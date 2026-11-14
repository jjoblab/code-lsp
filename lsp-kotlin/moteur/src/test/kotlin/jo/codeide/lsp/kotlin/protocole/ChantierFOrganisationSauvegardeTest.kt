// CodeIDE — CHANTIER F (« Optimize imports on the fly », 14/11/2026) :
// à la sauvegarde (willSaveWaitUntil), le serveur rend les éditions
// d'ORGANISATION des imports — le MÊME organisateur que l'action de code
// source (KC-10). Un bloc PROPRE ne rend RIEN (idempotence I1 : une
// sauvegarde ne réécrit jamais un document propre).
package jo.codeide.lsp.kotlin.protocole

import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.TextDocumentSaveReason
import org.eclipse.lsp4j.WillSaveTextDocumentParams

/**
 * Le chantier F : l'organisation des imports À LA SAUVEGARDE.
 *
 * **Les cas verrouillés** :
 *  - un document avec imports EN DÉSORDRE reçoit les éditions
 *    d'organisation (tri + retrait des inutilisés prouvés) ;
 *  - un bloc DÉJÀ PROPRE reçoit une liste VIDE (idempotence I1) ;
 *  - les capacités annoncent willSaveWaitUntil + save.includeText.
 */
class ChantierFOrganisationSauvegardeTest {

    /** Un serveur SANS racine — la session PAR DÉFAUT porte l'organisateur (KC-10). */
    private fun serveur(): ServeurLangageKotlin = ServeurLangageKotlin()

    /** Le montage complet : boucle démarrée, initialisation faite. */
    private fun monte(): ServeurLangageKotlin {
        val serveur = serveur()
        val boucle = BoucleLocale(serveur)
        boucle.demarrer()
        serveur.initialize(InitializeParams()).get(15, TimeUnit.SECONDS)
        serveur.initialized(org.eclipse.lsp4j.InitializedParams())
        return serveur
    }

    @Test
    fun `chantierF---sauvegarde-organise-les-imports-en-desordre`() {
        val serveur = monte()
        val uri = "file:///cf1.kt"
        val source =
            "package demo\n" +
                "import java.util.List\n" +
                "import java.util.ArrayList\n" +
                "import kotlin.io.println\n" +
                "\n" +
                "fun demo(): List<String> = ArrayList()\n"
        serveur.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri, "kotlin", 1, source)))
        val reponse = serveur.willSaveWaitUntil(
            WillSaveTextDocumentParams(TextDocumentIdentifier(uri), TextDocumentSaveReason.Manual),
        ).get(15, TimeUnit.SECONDS)
        assertTrue(reponse.isNotEmpty(), "la sauvegarde devait rendre les éditions d'organisation : $reponse")
        // L'application des éditions rend le bloc ORGANISÉ (tri) et
        // RETIRE l'inutilisé prouvé.
        val texte = appliquer(source, reponse)
        assertTrue(
            texte.indexOf("import java.util.ArrayList") < texte.indexOf("import java.util.List"),
            "le bloc trié place ArrayList avant List : <<$texte>>",
        )
        assertTrue(
            "import kotlin.io.println" !in texte,
            "l'import INUTILISÉ devait être retiré par la sauvegarde : <<$texte>>",
        )
    }

    @Test
    fun `chantierF---bloc-propre-rend-rien`() {
        val serveur = monte()
        val uri = "file:///cf2.kt"
        val source =
            "package demo\n" +
                "import java.util.ArrayList\n" +
                "import java.util.List\n" +
                "\n" +
                "fun demo(): List<String> = ArrayList()\n"
        serveur.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri, "kotlin", 1, source)))
        val reponse = serveur.willSaveWaitUntil(
            WillSaveTextDocumentParams(TextDocumentIdentifier(uri), TextDocumentSaveReason.Manual),
        ).get(15, TimeUnit.SECONDS)
        assertTrue(reponse.isEmpty(), "un bloc PROPRE ne devait rien rendre (idempotence I1) : $reponse")
    }

    @Test
    fun `chantierF---capacites-annoncent-willSaveWaitUntil-et-includeText`() {
        val serveur = serveur()
        val boucle = BoucleLocale(serveur)
        boucle.demarrer()
        val init = serveur.initialize(InitializeParams()).get(15, TimeUnit.SECONDS)
        serveur.initialized(org.eclipse.lsp4j.InitializedParams())
        val sync = init.capabilities.textDocumentSync
        assertTrue(sync.isRight, "la synchronisation devait être en forme OPTIONS")
        val options = sync.right
        assertTrue(options.willSaveWaitUntil, "willSaveWaitUntil devait être annoncé")
        assertEquals(true, options.save?.right?.includeText, "save.includeText devait être annoncé")
    }

    /** L'application NAÏVE des éditions (une passe, plages disjointes, ordre inverse). */
    private fun appliquer(source: String, editions: List<org.eclipse.lsp4j.TextEdit>): String {
        var texte = source
        val trie = editions.sortedByDescending { it.range.start.line * 100000 + it.range.start.character }
        for (edition in trie) {
            // Positions UTF-16 → offsets (le texte du banc n'a pas de
            // surrogate pair — la conversion directe est exacte).
            fun offsetDe(p: org.eclipse.lsp4j.Position): Int {
                var ligne = 0
                var debut = 0
                for (i in 0 until texte.length) {
                    if (ligne == p.line) break
                    if (texte[i] == '\n') {
                        ligne++
                        debut = i + 1
                    }
                }
                return debut + p.character
            }
            val debut = offsetDe(edition.range.start)
            val fin = offsetDe(edition.range.end)
            texte = texte.substring(0, debut) + edition.newText + texte.substring(fin)
        }
        return texte
    }
}
