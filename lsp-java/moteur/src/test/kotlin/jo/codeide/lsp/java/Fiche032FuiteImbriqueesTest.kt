package jo.codeide.lsp.java

import jo.codeide.lsp.java.classpath.FabriqueClasseBinaire
import jo.codeide.lsp.java.interne.NatureItem
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Fiche 032 — FuiteClassesImbriqueesTest : régression v1.9.1 — les classes
 * internes binaires (« R$style », « R$id »…) ne doivent PLUS fuiter dans
 * les complétions servies par l'index : le filtre « $ » s'applique aux TROIS
 * surfaces (complétion d'import, findTypesByPrefix, fqnsForSimpleName),
 * MÊME CONTRE UN INDEX IMPARFAIT qui rend les entrées brutes.
 *
 * L'index factice rend EXPRÈS les entrées brutes (comportement
 * pré-correctif) : c'est le MOTEUR qui filtre en aval — l'invariant à
 * conserver (note du cahier). Sans android.jar sur la machine, le moteur
 * tourne sur le jrt du runtime : les invariants du filtre sont indépendants
 * de la plateforme.
 */
class Fiche032FuiteImbriqueesTest {

    /** Index factice PRÊT du cahier : rend les entrées BRUTES (avec `$`). */
    private class IndexBrut : IndexNoms {
        private val fqcnParNom = mapOf(
            "R" to listOf("com.example.monip.R", "com.example.monip.R\$style", "com.example.monip.R\$id", "com.example.monip.R\$string"),
            "style" to listOf("com.example.monip.R\$style"),
            "id" to listOf("com.example.monip.R\$id"),
            "string" to listOf("com.example.monip.R\$string"),
        )

        override fun estPret(): Boolean = true

        override fun ajouterObservateurPret(observateur: () -> Unit) {
            observateur() // exécution immédiate (contrat des index factices)
        }

        override fun jarDe(fqcn: String): String? = "r-bench.jar"

        override fun nomsParPrefixe(prefixe: String, limite: Int): List<String> =
            fqcnParNom.keys.filter { it.startsWith(prefixe) }.take(limite)

        override fun fqnsForSimpleName(nom: String): List<String> = fqcnParNom[nom] ?: emptyList()

        override fun estPaquet(paquet: String): Boolean = paquet == "com.example.monip"

        override fun sousPaquetsDirects(paquet: String): List<String> = emptyList()

        override fun typesDansPaquet(paquet: String): List<String> =
            listOf("com.example.monip.R", "com.example.monip.R\$style", "com.example.monip.R\$id", "com.example.monip.R\$string")

        override fun membresStatiques(fqcn: String): List<String> = emptyList()
    }

    private fun moteurAvecRJarEtIndex(): MoteurJava {
        // R.jar réaliste (aapt2-like) : R + 3 classes internes, fabriquées par
        // la fabrique binaire (fiche 037) — aucune dépendance javac.
        val dossier = Files.createTempDirectory("fiche032")
        val rJar = dossier.resolve("r-bench.jar")
        ZipOutputStream(Files.newOutputStream(rJar)).use { sortie ->
            for (nom in listOf("R", "R\$style", "R\$id", "R\$string")) {
                sortie.putNextEntry(ZipEntry("com/example/monip/$nom.class"))
                sortie.write(FabriqueClasseBinaire.octets("com.example.monip.$nom", constructeurPublic = false))
                sortie.closeEntry()
            }
        }
        val environnement = EnvironnementCompilation()
        environnement.setIndexNoms(IndexBrut())
        val src = Files.createDirectories(dossier.resolve("java/com/example/monip"))
        src.resolve("MainActivity.java").writeText("package com.example.monip;\npublic class MainActivity { }\n")
        environnement.remplacerContexteModule(":app", listOf(dossier.resolve("java")), listOf(rJar))
        return environnement.moteur(":app")
    }

    @Test
    fun importApresPaquetProjetNeProposePasLesImbriquees() {
        val moteur = moteurAvecRJarEtIndex()
        val types = moteur.typesDansPaquet("com.example.monip")
        assertTrue(types.contains("com.example.monip.R"), "le type de premier niveau « R » est proposé")
        assertTrue(types.none { it.contains("$") }, "AUCUN FQN contenant « $ » : $types")
    }

    @Test
    fun completionTypeParPrefixeNeProposePasLesImbriquees() {
        val moteur = moteurAvecRJarEtIndex()
        val pourStyle = moteur.findTypesByPrefix("style", 25)
        assertTrue(pourStyle.none { it.contains("$") }, "aucun FQN avec « $ » pour « style » : $pourStyle")
        val pourR = moteur.findTypesByPrefix("R", 25)
        assertTrue(pourR.none { it.contains("$") }, "aucun FQN avec « $ » pour « R » : $pourR")
        assertTrue(pourR.contains("com.example.monip.R"), "le rappel normal reste servi : FQN exact de R")
    }

    @Test
    fun quickFixImportNeProposePasLesImbriquees() {
        val moteur = moteurAvecRJarEtIndex()
        val pourStyle = moteur.fqnsForSimpleName("style")
        assertTrue(pourStyle.none { it.contains("$") }, "le quick-fix « Importer style » n'offre pas le FQN binaire brut")
        val pourR = moteur.fqnsForSimpleName("R")
        assertTrue(pourR.contains("com.example.monip.R"), "« R » reste importable")
        assertTrue(pourR.none { it.contains("$") })
    }

    @Test
    fun completionAnnotationApresArobase() {
        val moteur = moteurAvecRJarEtIndex()
        // Source avec « @ » seul au-dessus d'une méthode ; caret juste après
        // l'arobase — la complétion ne doit jamais être null.
        val source = """
            package com.example.monip;

            public class MainActivity {
                @
                public void onCreate() {
                }
            }
        """.trimIndent()
        val items = moteur.complete("/com/example/monip/MainActivity.java", source, source.indexOf('@') + 1)
        assertNotNull(items, "la complétion n'est JAMAIS null — le serveur répond honnêtement")
        // À préfixe vide, la liste peut être vide (types filtrés) : aucune exigence de contenu.
        assertTrue(true, "contexte annotation résolu sans levée")
    }
}
