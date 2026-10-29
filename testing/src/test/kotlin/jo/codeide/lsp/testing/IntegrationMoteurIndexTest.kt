package jo.codeide.lsp.testing

import jo.codeide.lsp.index.PhaseIndex
import jo.codeide.lsp.index.ServiceIndexDisque
import jo.codeide.lsp.java.EnvironnementCompilation
import jo.codeide.lsp.java.MoteurJava
import jo.codeide.lsp.java.classpath.FabriqueClasseBinaire
import jo.codeide.lsp.model.ClasspathInstantane
import jo.codeide.lsp.model.EntreeClasspathInstantane
import jo.codeide.lsp.model.KindEntree
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Suite d'intégration moteur ↔ index disque (étage N1, ADR 0016) — la
 * réécriture Kotlin des 4 buts de l'ancienne suite `lsp/testing` : parité
 * avec/sans index, requêtes du port à PRÊT contre le VRAI CIDX, filtre `$`
 * en aval contre l'index réel, réouverture chaude et transition observée.
 *
 * Le jar de test est fabriqué de classes compilées-équivalentes
 * ([FabriqueClasseBinaire]) : zéro dépendance à android.jar, au cache
 * Gradle ou à javac — la suite tourne partout, CI comprise.
 */
class IntegrationMoteurIndexTest {

    /** Classe du jar de test : com.example.lib.Alpha (paquet + type). */
    private fun fabriquerJarClasse(): Path {
        val dossier = Files.createTempDirectory("fiche-testing")
        val jar = dossier.resolve("lib.jar")
        ZipOutputStream(Files.newOutputStream(jar)).use { sortie ->
            for (nom in listOf("Alpha", "Beta")) {
                sortie.putNextEntry(ZipEntry("com/example/lib/$nom.class"))
                sortie.write(FabriqueClasseBinaire.octets("com.example.lib.$nom", constructeurPublic = true))
                sortie.closeEntry()
            }
            // Classe imbriquée : le filtre « $ » du moteur s'éprouve contre l'index réel.
            sortie.putNextEntry(ZipEntry("com/example/lib/Alpha\$Inner.class"))
            sortie.write(FabriqueClasseBinaire.octets("com.example.lib.Alpha\$Inner", constructeurPublic = false))
            sortie.closeEntry()
        }
        return jar
    }

    private fun classpath(jar: Path): ClasspathInstantane = ClasspathInstantane(
        entrees = listOf(
            EntreeClasspathInstantane(
                kind = KindEntree.JAR,
                chemin = jar.toString(),
                portee = "compile",
                sources = null,
                aar = null,
                empreinte = jo.codeide.lsp.model.Empreintes.sha256(jar.toString()),
                existe = true,
                taille = Files.size(jar),
                mtime = Files.getLastModifiedTime(jar).toMillis(),
            ),
        ),
        empreinte = "projet-integration",
    )

    private fun service(stockage: File): Pair<ServiceIndexDisque, CoroutineScope> {
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val service = ServiceIndexDisque(
            repertoire = stockage,
            scope = scope,
            dispatcheurIo = Dispatchers.IO,
        )
        return service to scope
    }

    @Test
    fun pariteDiagnosticsAvecEtSansIndex() {
        val jar = fabriquerJarClasse()
        val source = """
            package app;

            public class C {
                public com.example.lib.Alpha champ = new com.example.lib.Alpha();
            }
        """.trimIndent()
        val sansIndex = MoteurJava(jars = listOf(jar)).diagnose("/app/C.java", source)

        val stockage = Files.createTempDirectory("fiche-testing-index").toFile()
        val (service, scope) = service(stockage)
        val adaptateur = AdaptateurIndexNoms(service, classpath(jar), scope)
        runBlocking { withTimeout(30_000) { service.construire(adaptateur.vue()).join() } }
        val environnement = EnvironnementCompilation()
        // Le contexte DÉFAUT porte le même classpath que le moteur seul.
        environnement.remplacerContexteModule("", emptyList(), listOf(jar))
        environnement.setIndexNoms(adaptateur)
        val avecIndex = environnement.moteur().diagnose("/app/C.java", source)

        assertEquals(
            sansIndex.map { listOf(it.severite, it.message, it.plage.debutOffset, it.plage.finOffset) },
            avecIndex.map { listOf(it.severite, it.message, it.plage.debutOffset, it.plage.finOffset) },
            "parité stricte des diagnostics avec et sans index",
        )
        adaptateur.fermer()
        scope.cancel()
    }

    @Test
    fun portContreLeVraiIndex() {
        val jar = fabriquerJarClasse()
        val stockage = Files.createTempDirectory("fiche-testing-index").toFile()
        val (service, scope) = service(stockage)
        val notifications = AtomicInteger(0)
        val adaptateur = AdaptateurIndexNoms(service, classpath(jar), scope)
        adaptateur.ajouterObservateurPret { notifications.incrementAndGet() }
        assertEquals(false, adaptateur.estPret(), "avant construction : pas prêt")
        runBlocking { withTimeout(30_000) { service.construire(adaptateur.vue()).join() } }

        // Transition observée : l'observateur posé AVANT a été exécuté.
        val attente = runBlocking {
            withTimeout(5_000) {
                while (!adaptateur.estPret()) {
                    kotlinx.coroutines.delay(50)
                }
                true
            }
        }
        assertTrue(attente, "la transition PRÊT a été observée")
        assertEquals(1, notifications.get(), "l'observateur est notifié exactement une fois")
        // Un observateur posé APRÈS la transition s'exécute immédiatement.
        val tardif = AtomicInteger(0)
        adaptateur.ajouterObservateurPret { tardif.incrementAndGet() }
        assertEquals(1, tardif.get(), "un index déjà prêt exécute l'observateur immédiatement")

        // Requêtes du port contre l'index réel.
        assertEquals(jar.toString(), adaptateur.jarDe("com.example.lib.Alpha"), "localisateur : jar porteur")
        assertTrue("Alpha" in adaptateur.nomsParPrefixe("Alph", 25), "noms par préfixe")
        assertTrue(
            "com.example.lib.Alpha" in adaptateur.fqnsForSimpleName("Alpha"),
            "FQCN du nom exact Alpha",
        )
        assertTrue(adaptateur.estPaquet("com.example.lib"), "paquet connu")
        assertTrue("com.example.lib" in adaptateur.sousPaquetsDirects("com.example"), "sous-paquet direct")
        assertTrue(
            "com.example.lib.Alpha" in adaptateur.typesDansPaquet("com.example.lib"),
            "types du paquet",
        )
        adaptateur.fermer()
        scope.cancel()
    }

    @Test
    fun moteurFiltreLesImbriqueesContreIndexReel() {
        val jar = fabriquerJarClasse()
        val stockage = Files.createTempDirectory("fiche-testing-index").toFile()
        val (service, scope) = service(stockage)
        val adaptateur = AdaptateurIndexNoms(service, classpath(jar), scope)
        runBlocking { withTimeout(30_000) { service.construire(adaptateur.vue()).join() } }
        runBlocking {
            withTimeout(5_000) {
                while (!adaptateur.estPret()) {
                    kotlinx.coroutines.delay(50)
                }
            }
        }
        val environnement = EnvironnementCompilation()
        environnement.remplacerContexteModule("", emptyList(), listOf(jar))
        environnement.setIndexNoms(adaptateur)
        val moteur = environnement.moteur()
        // L'index réel rend l'imbriquée BRUTE ; le moteur filtre en aval (fiche 032).
        assertTrue(
            "com.example.lib.Alpha\$Inner" in adaptateur.typesDansPaquet("com.example.lib"),
            "précondition : l'index rend l'entrée brute avec $",
        )
        assertTrue(
            moteur.typesDansPaquet("com.example.lib").none { it.contains("$") },
            "aucun FQN avec $ ne franchit le moteur",
        )
        assertTrue(
            "com.example.lib.Alpha" in moteur.findTypesByPrefix("Alph", 25),
            "le type de premier niveau reste proposé",
        )
        assertTrue(moteur.findTypesByPrefix("Alph", 25).none { it.contains("$") })
        adaptateur.fermer()
        scope.cancel()
    }

    @Test
    fun reouvertureChaudeSansReconstruction() {
        val jar = fabriquerJarClasse()
        val stockage = Files.createTempDirectory("fiche-testing-index").toFile()
        // PREMIÈRE session : construction à froid.
        val (service1, scope1) = service(stockage)
        val adaptateur1 = AdaptateurIndexNoms(service1, classpath(jar), scope1)
        val dureeFroid = System.nanoTime()
        runBlocking { withTimeout(30_000) { service1.construire(adaptateur1.vue()).join() } }
        val froidMs = (System.nanoTime() - dureeFroid) / 1_000_000
        val premiere = adaptateur1.jarDe("com.example.lib.Alpha")
        adaptateur1.fermer()
        scope1.cancel()

        // SECONDE session : réouverture chaude — les segments construits sont
        // RELUS (aucune reconstruction), les requêtes rendent les mêmes verdicts.
        val (service2, scope2) = service(stockage)
        val adaptateur2 = AdaptateurIndexNoms(service2, classpath(jar), scope2)
        val dureeChaud = System.nanoTime()
        runBlocking { withTimeout(30_000) { service2.construire(adaptateur2.vue()).join() } }
        val chaudMs = (System.nanoTime() - dureeChaud) / 1_000_000
        assertEquals(premiere, adaptateur2.jarDe("com.example.lib.Alpha"), "même verdict après réouverture")
        runBlocking {
            withTimeout(5_000) {
                while (adaptateur2.vue().etat.value.phase == PhaseIndex.CONSTRUCTION) {
                    kotlinx.coroutines.delay(50)
                }
            }
        }
        assertEquals(
            PhaseIndex.PRET,
            adaptateur2.vue().etat.value.phase,
            "la réouverture aboutit à PRÊT (état=${adaptateur2.vue().etat.value})",
        )
        assertTrue(chaudMs <= froidMs, "réouverture chaude ($chaudMs ms) pas plus lente que le froid ($froidMs ms)")
        adaptateur2.fermer()
        scope2.cancel()
    }
}
