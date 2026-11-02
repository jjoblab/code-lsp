package jo.codeide.lsp.java.protocole

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.TextDocumentItem
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Vague V-F4 — PLAGE PRIORITAIRE (ADR 0029 phase 4 : « zone visible
 * d'abord », transposition du §A1 de la référence) :
 *
 *  - **moteur** : la passe prioritaire d'un GRAND fichier publie le corps
 *    VISIBLE + tout le hors-corps, et PAS les corps masqués (partialité
 *    ASSUMÉE) ; elle n'ÉCRIT RIEN (ni mémo ni cache de parse) — le
 *    diagnose complet qui suit reste la source de vérité ; les refus
 *    honnêtes (fichier sous le seuil, offset hors corps) rendent null ;
 *  - **sonde** : [MoteurJava.serviraitPasseComplete] voit, sans compiler,
 *    les cas où la priorité apporte quelque chose (mémo manquant /
 *    confinement non prouvable) et les cas où elle ne sert à rien ;
 *  - **contrat protocole** : la notification `$/codeide/plageVisible`
 *    (offsets absolus) déclenche la DOUBLE publication sur un grand
 *    fichier — la partielle d'abord, la complète ensuite (même version,
 *    la complète fait foi) ; SANS notification, une seule publication
 *    (comportement d'avant V-F4) ; la capability `x-codeide.plageVisible`
 *    v1 est annoncée sur le fil (annoncé ⇔ testé, ADR 0015).
 */
class PlagePrioritaireTest {

    /** Le grand fichier de fixture : seuil dépassé, deux corps, une erreur dans CHACUN. */
    private fun grandFichier(): Triple<String, Int, Int> {
        val sb = StringBuilder()
        sb.append("package demo;\n")
        sb.append("import java.util.List;\n")
        sb.append("public class Grand {\n")
        sb.append("    private int champInutile = 0;\n")
        sb.append("    public int methodeVisible(int a) {\n")
        sb.append("        int x = a + 1;\n")
        while (sb.length < 8_000) {
            sb.append("        x = x + 1; // remplissage methode visible jusqu a la taille cible\n")
        }
        sb.append("        return TypeInconnuVisible.trouver(x);\n")
        sb.append("    }\n")
        sb.append("    public int methodeMasquee(int b) {\n")
        sb.append("        int y = b + 2;\n")
        while (sb.length < 16_500) {
            sb.append("        y = y + 2; // remplissage methode masquee jusqu a la taille cible\n")
        }
        sb.append("        return TypeInconnuMasque.trouver(y);\n")
        sb.append("    }\n")
        sb.append("}\n")
        val source = sb.toString()
        assertTrue(source.length >= MoteurJavaSeuil.seuil, "fixture au-dessus du seuil (${source.length})")
        val visible = source.indexOf("methodeVisible")
        val masquee = source.indexOf("methodeMasquee")
        return Triple(source, visible, masquee)
    }

    /** Le seuil de la vague V-F4 (miroir testable de la constante moteur). */
    private object MoteurJavaSeuil {
        val seuil: Int = jo.codeide.lsp.java.MoteurJava.SEUIL_PRIORITAIRE
    }

    @Test
    fun `la passe prioritaire publie le corps visible et pas les masques`() {
        val (source, visible, _) = grandFichier()
        val moteur = jo.codeide.lsp.java.MoteurJava(racines = emptyList())
        val chemin = "/demo/Grand.java"

        // La sonde : source fraîche sans mémo → la passe complète serait payée.
        assertTrue(moteur.serviraitPasseComplete(chemin, source), "mémo absent : priorité utile")

        val prioritaire = moteur.diagnosePrioritaire(chemin, source, visible + 30)
        assertNotNull(prioritaire, "grand fichier, offset dans un corps : la priorité s'applique")
        assertTrue(
            prioritaire.any { it.message.contains("TypeInconnuVisible") },
            "l'erreur du corps VISIBLE est publiée",
        )
        assertTrue(
            prioritaire.none { it.message.contains("TypeInconnuMasque") },
            "l'erreur du corps MASQUÉ n'est pas publiée (partialité assumée)",
        )

        // Pureté d'état : la passe prioritaire n'a RIEN écrit — le diagnose
        // complet d'après reste la source de vérité (compteur complet
        // incrémenté, pas de mémo servi par la passe partielle).
        val completsAvant = moteur.fullDiagnoseCount
        val complet = moteur.diagnose(chemin, source)
        assertEquals(completsAvant + 1, moteur.fullDiagnoseCount, "le diagnose d'après paie la passe complète")
        assertTrue(complet.any { it.message.contains("TypeInconnuVisible") })
        assertTrue(complet.any { it.message.contains("TypeInconnuMasque") }, "la passe complète publie TOUT")
    }

    @Test
    fun `les refus honnetes de la priorite rendent null`() {
        val moteur = jo.codeide.lsp.java.MoteurJava(racines = emptyList())
        // Petit fichier : sous le seuil.
        val petit = "package demo;\npublic class Petit {\n    public int un() { return 1; }\n    public int deux() { return 2; }\n}\n"
        assertNull(moteur.diagnosePrioritaire("/demo/Petit.java", petit, petit.indexOf("un()")), "sous le seuil : null")

        // Grand fichier mais offset HORS de tout corps (la zone montre la
        // structure : la passe complète EST la bonne réponse).
        val (source, _, _) = grandFichier()
        assertNull(
            moteur.diagnosePrioritaire("/demo/Grand.java", source, source.indexOf("class Grand")),
            "offset hors corps : null",
        )
    }

    @Test
    fun `la sonde distingue memo et confinement sans compiler`() {
        val moteur = jo.codeide.lsp.java.MoteurJava(racines = emptyList())
        val chemin = "/demo/A.java"
        val source = "package demo;\npublic class A {\n    public int un() { return 1; }\n}\n"
        assertTrue(moteur.serviraitPasseComplete(chemin, source), "première passe : complète")
        assertTrue(moteur.diagnose(chemin, source).isEmpty())
        // Mémo vif : plus rien à prioriser.
        assertTrue(!moteur.serviraitPasseComplete(chemin, source), "mémo : la priorité ne s'applique pas")
        // Frappe confinée dans le corps : la passe RAPIDE sert.
        val frappe = source.replace("return 1;", "return 2;")
        assertTrue(!moteur.serviraitPasseComplete(chemin, frappe), "confinement prouvable : passe rapide")
    }

    @Test
    fun `la notification declenche la double publication visible d abord`(@TempDir tmp: Path) {
        val racine = Files.createDirectories(tmp.resolve("demo"))
        val (source, visible, _) = grandFichier()
        racine.resolve("Grand.java").writeText(source)

        val serveur = ServeurLangageJava(fabriqueMoteur = { r -> r?.let { jo.codeide.lsp.java.MoteurJava(racines = listOf(it)) } })
        val boucle = BoucleLocale(serveur)
        boucle.demarrer()
        try {
            val params = InitializeParams()
            params.rootUri = tmp.toUri().toString()
            boucle.client.initialize(params).get()
            boucle.client.initialized(InitializedParams())

            // Le client déclare SA zone visible (le corps visible).
            boucle.experimental.plageVisible(ParamsPlageVisibleRpc(tmp.resolve("Grand.java").toUri().toString(), visible, visible + 400))

            val uri = tmp.resolve("Grand.java").toUri().toString()
            boucle.client.textDocumentService.didOpen(
                DidOpenTextDocumentParams(TextDocumentItem(uri, "java", 1, source)),
            )

            // La DOUBLE publication : la partielle (corps visible seul)
            // puis la complète (elle fait foi) — même version.
            assertTrue(boucle.attendreCumul(2, 3_000), "deux publications arrivent (prioritaire + complète)")
            val publications = boucle.espion.publications.filter { it.uri == uri }
            assertEquals(2, publications.size, "exactement deux publications pour ce didOpen")
            val premiere = publications[0]
            val deuxieme = publications[1]
            assertEquals(1, premiere.version)
            assertEquals(1, deuxieme.version)
            assertTrue(
                premiere.diagnostics.none { it.message.contains("TypeInconnuMasque") },
                "la première publication est PARTIELLE (corps visible seul)",
            )
            assertTrue(
                premiere.diagnostics.any { it.message.contains("TypeInconnuVisible") },
                "la première publication porte l'erreur VISIBLE",
            )
            assertTrue(
                deuxieme.diagnostics.any { it.message.contains("TypeInconnuMasque") },
                "la deuxième publication est COMPLÈTE (elle fait foi)",
            )

            // L'annonce sur le fil : x-codeide.plageVisible v1 (annoncé ⇔ testé).
            assertTrue(
                boucle.filBrut().contains("x-codeide.plageVisible"),
                "la capability x-codeide.plageVisible est annoncée sur le fil",
            )
        } finally {
            serveur.exit()
        }
    }

    @Test
    fun `sans notification le comportement d avant V-F4 est identique`(@TempDir tmp: Path) {
        val racine = Files.createDirectories(tmp.resolve("demo"))
        val (source, _, _) = grandFichier()
        racine.resolve("Grand.java").writeText(source)

        val serveur = ServeurLangageJava(fabriqueMoteur = { r -> r?.let { jo.codeide.lsp.java.MoteurJava(racines = listOf(it)) } })
        val boucle = BoucleLocale(serveur)
        boucle.demarrer()
        try {
            val params = InitializeParams()
            params.rootUri = tmp.toUri().toString()
            boucle.client.initialize(params).get()
            boucle.client.initialized(InitializedParams())

            // AUCUNE notification de plage visible.
            val uri = tmp.resolve("Grand.java").toUri().toString()
            boucle.client.textDocumentService.didOpen(
                DidOpenTextDocumentParams(TextDocumentItem(uri, "java", 1, source)),
            )

            assertTrue(boucle.attendreCumul(1, 3_000), "une publication arrive")
            Thread.sleep(300) // fenêtre : une seconde publication ne doit PAS suivre
            val publications = boucle.espion.publications.filter { it.uri == uri }
            assertEquals(1, publications.size, "une seule publication sans notification (contrat du cahier intact)")
            assertTrue(
                publications[0].diagnostics.any { it.message.contains("TypeInconnuMasque") },
                "l'unique publication est complète",
            )
        } finally {
            serveur.exit()
        }
    }
}
