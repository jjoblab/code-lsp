package jo.codeide.lsp.kotlin.session

import jo.codeide.lsp.kotlin.api.ClasspathKotlin
import jo.codeide.lsp.kotlin.api.CodeDiagnosticKotlin
import jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Fiche 017 — DiagnosticsParDeclarationTest : tests du chantier P1-A
 * « diagnostics par déclaration » (garde d'édition du texte vivant) — un
 * document portant des marqueurs d'erreur n'est plus écarté EN BLOC : seules
 * les DÉCLARATIONS dont le sous-arbre parse proprement sont diagnostiquées.
 *
 * Le câblage suit le chemin de production (D6) : le moteur nourricier pose
 * le classpath (UNE passe, ADR 0211), la session se branche, et le guichet
 * d'index est attendu par SONDAGE BORNÉ — c'est lui qui établit l'univers
 * des preuves (miroir du patron de la fiche 042 ; le classpath vide mais
 * DÉCLARÉ rend l'univers établi et VIDE : les preuves d'absence ne
 * dépendent que de la structure).
 *
 * - la RÉGION PROPRE garde ses preuves quand la frappe casse ailleurs ;
 * - un nom DÉCLARÉ n'importe où (déclaration en cours de frappe ou avalie
 *   par la récupération) n'est JAMAIS signalé ;
 * - une ZONE import brisée coupe la preuve d'absence sur TOUT le document ;
 * - un EN-TÊTE de classe brisée rend l'absence muette dans les membres ;
 * - une surcharge en cours de frappe n'est pas un témoin d'arité (la famille
 *   `kt.argumentCount` naît en vague 2b — le comportement reste vert par
 *   l'absence d'émission, et la propreté du témoin y sera verrouillée).
 *
 * Chaque comportement porte le nom exact du test source (identifiant de
 * traçabilité du cahier docs/cahier-specifications-kotlin/).
 */
class Fiche017DiagnosticsParDeclarationTest {

    /** Session branchée sur un classpath VIDE mais DÉCLARÉ (univers établi). */
    private fun session(): SessionLangageKotlinDocuments {
        val moteur = ServiceMoteurLangageKotlin.unique as MoteurLangageKotlinNoyau
        val classpath = ClasspathKotlin(entrees = emptyList(), racinesSources = emptyList())
        moteur.definirClasspath(classpath)
        val session = moteur.creerSession() as SessionLangageKotlinDocuments
        session.definirClasspath(classpath)
        attendreCouvert(session)
        return session
    }

    /** Attend (sondage borné) que le guichet serve le snapshot publié. */
    private fun attendreCouvert(
        session: SessionLangageKotlinDocuments,
        secondes: Long = 90,
    ) {
        val limite = System.currentTimeMillis() + secondes * 1000
        while (System.currentTimeMillis() < limite) {
            if (session.indexSiPret() != null) return
            Thread.sleep(10)
        }
        throw AssertionError("la passe du moteur n'a pas publié (guichet toujours muet)")
    }

    @Test
    fun `region-propre---la-frappe-au-bas-du-fichier-n'eteint-pas-les-preuves-du-dessus`() {
        val session = session()
        val source = "fun propre() {\n  introuvable\n}\n\nfun cassee( {\n"
        val uri = "file:///edition-bas.kt"
        session.ouvrir(uri, source)
        val diagnostics = session.diagnostics(uri)
        assertTrue(
            diagnostics.any { it.code == CodeDiagnosticKotlin.SYNTAXE },
            "le marqueur de syntaxe reste rendu",
        )
        val nonResolus = diagnostics.filter { it.code == CodeDiagnosticKotlin.REFERENCE_NON_RESOLUE }
        assertEquals(1, nonResolus.size, "la déclaration PROPRE garde son diagnostic")
        assertEquals(source.indexOf("introuvable"), nonResolus.single().plage.debut)
        session.close()
    }

    @Test
    fun `region-propre---preuve-locale-(retour-manquant)-pendant-l'edition-ailleurs`() {
        val session = session()
        val source = "fun retourne(): Int {\n  val a = 1\n}\n\nfun cassee( {\n"
        val uri = "file:///edition-retour.kt"
        session.ouvrir(uri, source)
        val diagnostics = session.diagnostics(uri)
        assertTrue(
            diagnostics.any { it.code == CodeDiagnosticKotlin.RETOUR_MANQUANT },
            "le retour manquant de la fonction PROPRE est signalé",
        )
        session.close()
    }

    @Test
    fun `classe---membre-propre-diagnostique,-membre-casse-muet`() {
        val session = session()
        val source =
            "class Maison {\n  fun propre() {\n    introuvable\n  }\n\n  fun cassee( {\n  }\n}\n"
        val uri = "file:///edition-classe.kt"
        session.ouvrir(uri, source)
        val diagnostics = session.diagnostics(uri)
        val nonResolus = diagnostics.filter { it.code == CodeDiagnosticKotlin.REFERENCE_NON_RESOLUE }
        assertEquals(1, nonResolus.size, "le membre PROPRE reste diagnostiqué")
        assertEquals(source.indexOf("introuvable"), nonResolus.single().plage.debut)
        session.close()
    }

    @Test
    fun `nom-declare-en-cours-de-frappe---jamais-signale-non-resolu`() {
        val session = session()
        val source = "fun appelant() {\n  encours()\n}\n\nfun encours( {\n"
        val uri = "file:///edition-declare.kt"
        session.ouvrir(uri, source)
        val diagnostics = session.diagnostics(uri)
        assertTrue(
            diagnostics.none { it.code == CodeDiagnosticKotlin.REFERENCE_NON_RESOLUE },
            "un nom DÉCLARÉ (déclaration en cours) n'est jamais non résolu",
        )
        assertTrue(
            diagnostics.none { it.code == CodeDiagnosticKotlin.NOMBRE_ARGUMENTS },
            "la surcharge en cours de frappe n'est pas un témoin d'arité",
        )
        session.close()
    }

    @Test
    fun `zone-import-brisee---preuve-d'absence-muette-sur-tout-le-document`() {
        val session = session()
        val source = "import android.\n\nfun propre() {\n  introuvable\n}\n"
        val uri = "file:///edition-import.kt"
        session.ouvrir(uri, source)
        val diagnostics = session.diagnostics(uri)
        assertTrue(
            diagnostics.any { it.code == CodeDiagnosticKotlin.SYNTAXE },
            "le marqueur de syntaxe de la zone import est rendu",
        )
        assertTrue(
            diagnostics.none { it.code == CodeDiagnosticKotlin.REFERENCE_NON_RESOLUE },
            "zone package/import brisée : aucun kt.unresolved sur le document",
        )
        session.close()
    }

    @Test
    fun `en-tete-de-classe-brise---absence-muette-dans-les-membres-propres`() {
        val session = session()
        val source = "class Maison : Base< {\n  fun membre() {\n    introuvable\n  }\n}\n"
        val uri = "file:///edition-entete.kt"
        session.ouvrir(uri, source)
        val diagnostics = session.diagnostics(uri)
        assertTrue(
            diagnostics.none { it.code == CodeDiagnosticKotlin.REFERENCE_NON_RESOLUE },
            "en-tête brisée : les membres hérités ne sont pas lisibles, absence muette",
        )
        session.close()
    }

    @Test
    fun `surcharge-en-cours-de-frappe---candidate-d'arite-ecartee`() {
        val session = session()
        val source = "fun consomme() {\n  canal(1, 2)\n}\n\nfun canal( {\n"
        val uri = "file:///edition-arite.kt"
        session.ouvrir(uri, source)
        val diagnostics = session.diagnostics(uri)
        assertTrue(
            diagnostics.none { it.code == CodeDiagnosticKotlin.NOMBRE_ARGUMENTS },
            "la surcharge cassée n'est pas un témoin d'arité fiable",
        )
        session.close()
    }

    @Test
    fun `document-propre---comportement-inchange`() {
        val session = session()
        val source = "fun demo() {\n  introuvable\n}\n"
        val uri = "file:///propre.kt"
        session.ouvrir(uri, source)
        val diagnostics = session.diagnostics(uri)
        assertEquals(
            1,
            diagnostics.filter { it.code == CodeDiagnosticKotlin.REFERENCE_NON_RESOLUE }.size,
            "sans marqueur d'erreur, le diagnostic historique est inchangé",
        )
        session.close()
    }
}
