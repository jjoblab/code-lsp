package jo.codeide.lsp.java.protocole

import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DocumentFormattingParams
import org.eclipse.lsp4j.FormattingOptions
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Session 5 (15/11/2026) — le formatage par STYLE : les options LSP
 * portent le style (« google », « android », « eclipse », « custom » +
 * mesures indentSize/lineWidth) ; le serveur les lit et le formateur
 * s'y plie. Contrats verrouillés :
 *  - style GOOGLE : 2 espaces par niveau (le nom de la classe imbriquée
 *    à profondeur 1 précède de 2 espaces) ;
 *  - style ANDROID (AOSP) : 8 espaces par niveau ;
 *  - style CUSTOM : mesures libres honorées (indentation 3) ;
 *  - sans style : le défaut Eclipse (4 espaces) reste — contrat du
 *    cahier inchangé.
 */
class FormatageStylesTest {

    @Test
    fun `les quatre styles formatent par le protocole`() {
        val dossier = tmp.resolve("src").also(Files::createDirectories)
        // Une classe imbriquée à profondeur 1 : son nom porte
        // l'indentation du style (2 google / 4 eclipse / 8 android / 3 custom).
        val source = """
            package demo;

            public class Externe {
            class Interne {
            int x = 1;
            }
            }
        """.trimIndent()
        val fichier = dossier.resolve("Externe.java")
        Files.writeString(fichier, source)
        val serveur = ServeurLangageJava(
            fabriqueMoteur = { racine -> racine?.let { jo.codeide.lsp.java.MoteurJava(racines = listOf(it)) } },
        )
        val boucle = BoucleLocale(serveur)
        boucle.demarrer()
        boucle.client.initialize(InitializeParams().apply { rootUri = dossier.toUri().toString() }).get()
        boucle.client.initialized(InitializedParams())
        val uri = fichier.toUri().toString()
        boucle.client.textDocumentService.didOpen(
            DidOpenTextDocumentParams(TextDocumentItem(uri, "java", 1, source)),
        )

        fun formaterAvec(options: FormattingOptions): String {
            val editions = boucle.client.textDocumentService.formatting(
                DocumentFormattingParams(TextDocumentIdentifier(uri), options),
            ).get()
            assertTrue(editions.isNotEmpty(), "le formateur rend des éditions")
            return editions.joinToString("") { it.newText }
        }

        fun options(vararg paires: Pair<String, Any>): FormattingOptions =
            FormattingOptions(4, true).apply {
                // NB : la map par défaut de lsp4j est NON modifiable — on
                // la REMPLACE (jamais de put dessus).
                val map = mutableMapOf<String, String>()
                paires.forEach { (cle, valeur) -> map[cle] = valeur.toString() }
                properties = map
            }

        // 1. GOOGLE : la classe interne à 2 espaces.
        val google = formaterAvec(options("style" to "google"))
        assertTrue(
            "\n  class Interne" in google,
            "google : la classe interne à 2 espaces — reçu :\n$google",
        )

        // 2. ANDROID (AOSP) : 8 espaces.
        val android = formaterAvec(options("style" to "android"))
        assertTrue(
            "\n        class Interne" in android,
            "android : la classe interne à 8 espaces — reçu :\n$android",
        )

        // 3. CUSTOM : indentation libre de 3 espaces.
        val custom = formaterAvec(options("style" to "custom", "indentSize" to 3, "lineWidth" to 90))
        assertTrue(
            "\n   class Interne" in custom,
            "custom : la classe interne à 3 espaces — reçu :\n$custom",
        )

        // 4. Sans style : le défaut Eclipse (4 espaces) — contrat inchangé.
        val eclipse = formaterAvec(options())
        assertTrue(
            "\n    class Interne" in eclipse,
            "défaut : la classe interne à 4 espaces (Eclipse) — reçu :\n$eclipse",
        )
    }

    @Test
    fun `mesures custom hors bornes - ignorees honnetement`() {
        // L'API publique du style : depuisOptions — les mesures invalides
        // (indentation 0, 99 ; largeur 5) retombent sur le défaut.
        val style = jo.codeide.lsp.java.formatage.StyleFormattage.depuisOptions(
            "custom",
            indentCustom = 0,
            largeurCustom = 5,
        )
        assertEquals(4, style.indentation, "mesure invalide ⇒ défaut Eclipse (4)")
        assertEquals(120, style.largeurLigne, "largeur invalide ⇒ défaut Eclipse (120)")

        val valide = jo.codeide.lsp.java.formatage.StyleFormattage.depuisOptions(
            "custom",
            indentCustom = 3,
            largeurCustom = 90,
        )
        assertEquals(3, valide.indentation, "mesure valide honorée")
        assertEquals(90, valide.largeurLigne, "largeur valide honorée")
    }

    @TempDir
    lateinit var tmp: Path
}
