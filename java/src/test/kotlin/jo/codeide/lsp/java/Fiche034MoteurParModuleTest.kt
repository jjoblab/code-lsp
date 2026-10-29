package jo.codeide.lsp.java

import jo.codeide.lsp.java.diagnostics.Severite
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Fiche 034 — MoteurParModuleTest : moteur PAR module de l'environnement de
 * compilation (lot E v0.97.0) — isolation des verrous et caches, sémantiques
 * replaceAll par module, compliance par projet, champ partagé index,
 * dispose global, redirection des clés vides et concurrence bi-module.
 */
class Fiche034MoteurParModuleTest {

    private fun dossier(vararg parties: String): Path {
        val dossier = Files.createTempDirectory("fiche034-" + parties.joinToString("-"))
        return dossier
    }

    @Test
    fun leVerrouDuModuleANeBloquePlusLeModuleB() {
        val environnement = EnvironnementCompilation()
        val moteurA = environnement.moteur(":app")
        val moteurB = environnement.moteur(":lib")
        // Chauffe : un diagnose de MAIN_OK chacun.
        moteurA.diagnose("/demo/Main.java", CorpusCommun.MAIN_OK)
        moteurB.diagnose("/demo/Main.java", CorpusCommun.MAIN_OK)
        assertNotSame(moteurA, moteurB, "deux moteurs distincts")

        val titulaire = CountDownLatch(1)
        val relache = CountDownLatch(1)
        val thread = Thread {
            synchronized(moteurA) { // le monitor du moteur A est TENU 3 s
                titulaire.countDown()
                try {
                    relache.await(5, TimeUnit.SECONDS)
                } catch (_: InterruptedException) {
                }
            }
        }
        thread.start()
        titulaire.await(5, TimeUnit.SECONDS)

        val erreursB = AtomicInteger(0)
        val executur = Executors.newSingleThreadExecutor()
        val future = executur.submit {
            val diags = moteurB.diagnose("/demo/Main.java", CorpusCommun.MAIN_ERR)
            erreursB.set(diags.count { it.severite == Severite.ERREUR })
        }
        future.get(2, TimeUnit.SECONDS)
        relache.countDown()
        thread.join(5_000)
        executur.shutdown()
        assertTrue(erreursB.get() >= 1, "le diagnostic du moteur B rend ≥ 1 erreur pendant que le monitor de A est tenu")
    }

    @Test
    fun invaliderAVideLeMoteurDeASeulement() {
        val environnement = EnvironnementCompilation()
        val srcA = dossier("src-a").also { it.resolve("X.java").writeText("class X { }\n") }
        val srcB = dossier("src-b").also { it.resolve("Y.java").writeText("class Y { }\n") }
        environnement.remplacerContexteModule(":a", listOf(srcA))
        environnement.remplacerContexteModule(":b", listOf(srcB))
        val moteurA1 = environnement.moteur(":a")
        val moteurB1 = environnement.moteur(":b")
        // Chauffe : cache de parse rempli de chaque côté.
        moteurA1.diagnose("/demo/A.java", CorpusCommun.MAIN_OK)
        moteurB1.diagnose("/demo/B.java", CorpusCommun.MAIN_OK)
        assertTrue(moteurA1.parseCacheSize() >= 1, "cache de A rempli")
        assertTrue(moteurB1.parseCacheSize() >= 1, "cache de B rempli")

        // Re-sync Gradle de :a SEUL avec une autre racine.
        val srcA2 = dossier("src-a-2").also { it.resolve("X.java").writeText("class X { }\n") }
        environnement.remplacerContexteModule(":a", listOf(srcA2))

        val moteurA2 = environnement.moteur(":a")
        val moteurB2 = environnement.moteur(":b")
        assertNotSame(moteurA1, moteurA2, "le moteur de :a est reconstruit")
        assertEquals(0, moteurA2.parseCacheSize(), "cache de parse vide pour le nouveau moteur de A")
        assertSame(moteurB1, moteurB2, "le moteur de :b RESTE la même instance")
        assertTrue(moteurB2.parseCacheSize() >= 1, "le cache de parse de B reste rempli")
    }

    @Test
    fun remplacementRedondantEstUnNoOpSysteme() {
        val environnement = EnvironnementCompilation()
        val srcStable = dossier("src-stable").also { it.resolve("X.java").writeText("class X { }\n") }
        environnement.remplacerContexteModule(":app", listOf(srcStable))
        val notifications = AtomicInteger(0)
        environnement.ajouterAuditeurContexte { notifications.incrementAndGet() }
        // Second remplacement avec les MÊMES listes.
        environnement.remplacerContexteModule(":app", listOf(srcStable))
        val moteur1 = environnement.moteur(":app")
        val moteur2 = environnement.moteur(":app")
        assertSame(moteur1, moteur2, "même instance de moteur")
        assertEquals(0, notifications.get(), "zéro notification d'auditeur")
    }

    @Test
    fun remplacementEffectifInvalideEtNotifie() {
        val environnement = EnvironnementCompilation()
        val srcAvant = dossier("src-avant").also { it.resolve("X.java").writeText("class X { }\n") }
        val srcApres = dossier("src-apres").also { it.resolve("X.java").writeText("class X { }\n") }
        environnement.remplacerContexteModule(":app", listOf(srcAvant))
        val notifications = AtomicInteger(0)
        environnement.ajouterAuditeurContexte { notifications.incrementAndGet() }
        val moteur1 = environnement.moteur(":app")
        environnement.remplacerContexteModule(":app", listOf(srcApres))
        val moteur2 = environnement.moteur(":app")
        assertNotSame(moteur1, moteur2, "le moteur est reconstruit")
        assertEquals(1, notifications.get(), "les auditeurs sont notifiés exactement une fois")
    }

    @Test
    fun laComplianceParModuleDiagnostiqueSelonLeNiveauDuProjet() {
        val environnement = EnvironnementCompilation()
        val srcVieux = dossier("src-vieux")
        val srcModerne = dossier("src-moderne")
        environnement.remplacerContexteModule(":vieux", listOf(srcVieux), niveau = "8")
        environnement.remplacerContexteModule(":moderne", listOf(srcModerne), niveau = "21")

        val moteurVieux = environnement.moteur(":vieux")
        val moteurModerne = environnement.moteur(":moderne")
        assertEquals("8", moteurVieux.complianceModule(), "niveau posé observable (:vieux)")
        assertEquals("21", moteurModerne.complianceModule(), "niveau posé observable (:moderne)")
        assertEquals("8", moteurVieux.complianceDemandee(), "compliance demandée (:vieux)")
        assertEquals("21", moteurModerne.complianceDemandee(), "compliance demandée (:moderne)")

        val varEtRecord = """
            public class Essai {
                record Point(int x, int y) { }
                public int somme(java.util.List<Integer> l) {
                    var total = 0;
                    for (var v : l) total += v;
                    return total;
                }
            }
        """.trimIndent()
        val recordEtSwitch = """
            public class Essai {
                record Forme(String genre) { }
                public String decrire(Object o) {
                    return switch (o) {
                        case Forme(String genre) -> "forme " + genre;
                        default -> "inconnu";
                    };
                }
            }
        """.trimIndent()
        val erreursVieux = moteurVieux.diagnose("/Essai.java", varEtRecord).count { it.severite == Severite.ERREUR }
        assertTrue(erreursVieux >= 1, "var+record sous « 8 » : au moins 1 erreur ($erreursVieux)")
        val erreursModerneRecord = moteurModerne.diagnose("/Essai.java", recordEtSwitch).count { it.severite == Severite.ERREUR }
        assertEquals(0, erreursModerneRecord, "record+switch à motifs sous « 21 » : 0 erreur")
        val erreursModerneVar = moteurModerne.diagnose("/Essai.java", varEtRecord).count { it.severite == Severite.ERREUR }
        assertEquals(0, erreursModerneVar, "le MÊME source var+record sous « 21 » : 0 erreur")
    }

    @Test
    fun semantiquesDuNiveauJavaParModule() {
        val environnement = EnvironnementCompilation()
        val src = dossier("src-niveau").also { it.resolve("X.java").writeText("class X { }\n") }
        environnement.remplacerContexteModule(":app", listOf(src), niveau = "8")
        val notifications = AtomicInteger(0)
        environnement.ajouterAuditeurContexte { notifications.incrementAndGet() }

        // La surcharge 3 arguments IGNORANT le niveau le CONSERVE.
        environnement.remplacerContexteModule(":app", listOf(src))
        assertEquals("8", environnement.moteur(":app").complianceModule(), "le niveau est conservé par la surcharge 3 arguments")
        assertEquals(0, notifications.get(), "sync redondant : 0 notification")

        // Un changement de niveau SEUL invalide le moteur et notifie une fois.
        environnement.remplacerContexteModule(":app", listOf(src), niveau = "21")
        assertEquals("21", environnement.moteur(":app").complianceModule())
        assertEquals(1, notifications.get(), "changement de niveau : une notification")

        // null explicite : retour au défaut de l'environnement (« 17 »).
        environnement.remplacerContexteModule(":app", listOf(src), niveau = null)
        assertNull(environnement.moteur(":app").complianceModule(), "null explicite : complianceModule rend null")
        assertEquals(2, notifications.get(), "2 notifications cumulées")
        assertEquals("17", environnement.moteur(":app").complianceDemandee(), "le moteur reconstruit hérite du niveau par défaut")
    }

    @Test
    fun attacherLIndexInvalideDefautEtModules() {
        val environnement = EnvironnementCompilation()
        val src = dossier("src-index").also { it.resolve("X.java").writeText("class X { }\n") }
        environnement.remplacerContexteModule(":app", listOf(src))
        val moteurDefaut1 = environnement.moteur()
        val moteurApp1 = environnement.moteur(":app")

        val fauxIndex = FauxIndexNomsVide()
        environnement.setIndexNoms(fauxIndex)
        val moteurDefaut2 = environnement.moteur()
        val moteurApp2 = environnement.moteur(":app")
        assertNotSame(moteurDefaut1, moteurDefaut2, "l'attachement invalide le moteur défaut")
        assertNotSame(moteurApp1, moteurApp2, "l'attachement invalide le moteur du module")
        // Ré-attacher la même instance : idempotence.
        environnement.setIndexNoms(fauxIndex)
        assertSame(moteurDefaut2, environnement.moteur(), "ré-attachement de la MÊME instance : pas de re-invalidation")
        assertSame(moteurApp2, environnement.moteur(":app"))
    }

    /** Index factice du cahier : jamais prêt, aucune entrée, observateur jamais notifié. */
    private class FauxIndexNomsVide : IndexNoms {
        override fun estPret(): Boolean = false
        override fun ajouterObservateurPret(observateur: () -> Unit) {
            // jamais notifié
        }

        override fun jarDe(fqcn: String): String? = null
        override fun nomsParPrefixe(prefixe: String, limite: Int): List<String> = emptyList()
        override fun fqnsForSimpleName(nom: String): List<String> = emptyList()
        override fun estPaquet(paquet: String): Boolean = false
        override fun sousPaquetsDirects(paquet: String): List<String> = emptyList()
        override fun typesDansPaquet(paquet: String): List<String> = emptyList()
        override fun membresStatiques(fqcn: String): List<String> = emptyList()
    }

    @Test
    fun disposeLibereDefautEtModules() {
        val environnement = EnvironnementCompilation()
        val src = dossier("src-dispose").also { it.resolve("X.java").writeText("class X { }\n") }
        environnement.remplacerContexteModule(":app", listOf(src))
        val moteurDefaut = environnement.moteur()
        val moteurApp = environnement.moteur(":app")

        assertTrue(environnement.moteurInitialise(null), "défaut initialisé")
        assertTrue(environnement.moteurInitialise(":app"), ":app initialisé")
        assertFalse(environnement.moteurInitialise(":jamaisConstruit"), "module jamais construit : faux")

        environnement.dispose()
        assertFalse(environnement.moteurInitialise(null), "défaut libéré")
        assertFalse(environnement.moteurInitialise(":app"), ":app libéré")
        val nouveauDefaut = environnement.moteur()
        val nouveauApp = environnement.moteur(":app")
        assertNotSame(moteurDefaut, nouveauDefaut, "les prochains accès construisent des moteurs distincts")
        assertNotSame(moteurApp, nouveauApp)
    }

    @Test
    fun cleVideOuNullViseLeContexteDefaut() {
        val environnement = EnvironnementCompilation()
        assertSame(environnement.moteur(null), environnement.moteur(), "jdt(null) = moteur du contexte défaut")
        assertSame(environnement.moteur(""), environnement.moteur(), "jdt(\"\") = moteur du contexte défaut")
        val src = dossier("src-defaut").also { it.resolve("X.java").writeText("class X { }\n") }
        // La voie qualifiée vide applique les sémantiques replaceAll au défaut.
        environnement.remplacerContexteModule("", listOf(src))
        val avant = environnement.moteur()
        environnement.remplacerContexteModule(null, listOf(src))
        assertSame(avant, environnement.moteur(), "remplacement redondant via clé null : no-op")
        val autre = dossier("src-defaut-2").also { it.resolve("X.java").writeText("class X { }\n") }
        environnement.remplacerContexteModule(null, listOf(autre))
        assertNotSame(avant, environnement.moteur(), "remplacement effectif via clé null : moteur reconstruit")
    }

    @Test
    fun mixConcurrentSurDeuxMoteursDistincts() {
        val environnement = EnvironnementCompilation()
        val src = dossier("src-mix").also { it.resolve("X.java").writeText("class X { }\n") }
        environnement.remplacerContexteModule(":app", listOf(src))
        val moteurDefaut = environnement.moteur()
        val moteurApp = environnement.moteur(":app")
        // Chauffe des deux moteurs.
        moteurDefaut.diagnose("/demo/Main.java", CorpusCommun.MAIN_OK)
        moteurApp.diagnose("/demo/Main.java", CorpusCommun.MAIN_OK)

        val defaillances = AtomicInteger(0)
        val executur = Executors.newFixedThreadPool(8)
        val futures = (0 until 8).map { i ->
            val moteur = if (i % 2 == 0) moteurDefaut else moteurApp
            executur.submit {
                repeat(12) { iteration ->
                    try {
                        val source = if ((i + iteration) % 2 == 0) CorpusCommun.MAIN_OK else CorpusCommun.MAIN_ERR
                        val diags = moteur.diagnose("/demo/Main.java", source)
                        val erreurs = diags.count { it.severite == Severite.ERREUR }
                        if (source == CorpusCommun.MAIN_OK && erreurs != 0) {
                            throw AssertionError("MAIN_OK doit rendre 0 erreur (cache corrompu ?)")
                        }
                        if (source == CorpusCommun.MAIN_ERR && erreurs < 1) {
                            throw AssertionError("MAIN_ERR doit rendre au moins 1 erreur")
                        }
                        if (moteur.resolveUnit("/demo/Main.java", source, false) == null) {
                            throw AssertionError("resolveUnit jamais null")
                        }
                        moteur.hoverAt("/demo/Main.java", source, 60)
                    } catch (t: Throwable) {
                        defaillances.incrementAndGet()
                    }
                }
            }
        }
        futures.forEach { it.get(30, TimeUnit.SECONDS) }
        executur.shutdown()
        executur.awaitTermination(5, TimeUnit.SECONDS)
        assertEquals(0, defaillances.get(), "aucune exception sur le mix concurrent")
    }
}
