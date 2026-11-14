package jo.codeide.lsp.kotlin.protocole

import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DocumentFormattingParams
import org.eclipse.lsp4j.FormattingOptions
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Session 5 (15/11/2026) — le formatage Kotlin par STYLE : le formateur
 * borné ré-indente, l'unité d'indentation vient du style des options
 * LSP — OFFICIEL (kotlinlang, 4 espaces, le défaut historique), ANDROID
 * (Compose/AndroidX, 2 espaces), CUSTOM (indentSize libre, bornes
 * honnêtes 1..16). Contrats verrouillés par le protocole complet :
 *  - style officiel : un corps de classe à profondeur 1 vit à 4 espaces ;
 *  - style android : le même corps vit à 2 espaces ;
 *  - style custom indentSize=3 : le corps vit à 3 espaces ;
 *  - sans style : le défaut 4 (contrat fiche 019 inchangé) ;
 *  - une mesure hors bornes (0, 99) est IGNORÉE (défaut).
 */
class FormatageStylesKotlinTest {

    @Test
    fun `les styles officiel android custom reindentent par le protocole`() {
        val serveur = ServeurLangageKotlin(fabriqueSession = { _ ->
            jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin.unique.creerSession()
        })
        val boucle = BoucleLocale(serveur)
        boucle.demarrer()
        boucle.client.initialize(InitializeParams()).join()
        boucle.client.initialized(InitializedParams())
        val uri = "file:///styles.kt"
        // Le corps de la fonction est à profondeur 1 : la ligne « val
        // milieu = 2 » doit vivre à N espaces selon le style.
        val source = "fun calcule() {\nval milieu = 2\n}\n"
        boucle.client.textDocumentService.didOpen(
            DidOpenTextDocumentParams(TextDocumentItem(uri, "kotlin", 0, source)),
        )

        fun formaterAvec(options: FormattingOptions): String? {
            val editions = boucle.client.textDocumentService.formatting(
                DocumentFormattingParams(TextDocumentIdentifier(uri), options),
            ).join()
            // L'édition du CORPS : sa plage commence au début de la ligne
            // « val milieu = 2 » (ligne 1, colonne 0 — miroir fiche 019 ;
            // le newText est le BLANC cible — isNotBlank() serait FAUX,
            // une chaîne d'espaces est « blank » par définition).
            return editions.firstOrNull {
                it.range.start.line == 1 && it.range.start.character == 0
            }?.newText
        }

        fun options(vararg paires: Pair<String, Any>): FormattingOptions =
            FormattingOptions(4, true).apply {
                val map = mutableMapOf<String, String>()
                paires.forEach { (cle, valeur) -> map[cle] = valeur.toString() }
                properties = map
            }

        // 1. ANDROID : le corps vit à 2 espaces.
        val android = formaterAvec(options("style" to "android"))
        assertEquals("  ", android, "android : le blanc cible du corps est 2 espaces")

        // 2. CUSTOM indentSize=3.
        val custom = formaterAvec(options("style" to "custom", "indentSize" to 3))
        assertEquals("   ", custom, "custom : le blanc cible du corps est 3 espaces")

        // 3. OFFICIEL (nommé) : 4 espaces.
        val officiel = formaterAvec(options("style" to "officiel"))
        assertEquals("    ", officiel, "officiel : le blanc cible du corps est 4 espaces")

        // 4. SANS style : le défaut 4 (contrat fiche 019 inchangé).
        val defaut = formaterAvec(options())
        assertEquals("    ", defaut, "sans style : le tabSize du client (4) — contrat fiche 019")

        // 5. Mesure HORS BORNES : ignorée, défaut 4.
        val borne = formaterAvec(options("style" to "custom", "indentSize" to 99))
        assertEquals("    ", borne, "mesure hors bornes : ignorée (tabSize 4 du client)")
    }

    @TempDir
    lateinit var tmp: Path
}
