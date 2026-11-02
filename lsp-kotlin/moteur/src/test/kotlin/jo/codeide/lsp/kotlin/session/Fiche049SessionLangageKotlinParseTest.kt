package jo.codeide.lsp.kotlin.session

import java.nio.file.Files
import java.nio.file.Path
import jo.codeide.lsp.kotlin.api.ClasspathKotlin
import jo.codeide.lsp.kotlin.api.CodeDiagnosticKotlin
import jo.codeide.lsp.kotlin.api.GenrePlagePliable
import jo.codeide.lsp.kotlin.api.SeveriteKotlin
import jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin
import jo.codeide.lsp.kotlin.api.TagDiagnosticKotlin
import jo.codeide.lsp.kotlin.parse.kt.PlageTexte
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Fiche 049 — SessionLangageKotlinParseTest : tests du contrat de session K2
 * (« session LSP minimale », LSP_KOTLIN.md §5) — PAR LE SPI
 * ([ServiceMoteurLangageKotlin.unique]), PAS par le type concret : c'est le
 * chemin de production de l'hôte (D6 — le pack futur se branche là, et
 * uniquement là).
 *
 * Comportements éprouvés (fixtures calibrées sur le comportement RÉEL de la
 * grammaire vendorée, sondé avant écriture — miroir de la méthode vague 1a) :
 * tolérance (aucun texte ne lève), cycle de vie didOpen/didChange/didClose,
 * diagnostics d'imports INUTILE/conflictuels (tags et informations liées
 * KP15), plages pliables, survol S2 et rafraichirSources (L1). Chaque
 * comportement porte le nom exact du test source (identifiant de
 * traçabilité du cahier).
 */
class Fiche049SessionLangageKotlinParseTest {

    /** Le moteur chargé par le SPI (chemin de production de l'hôte, D6). */
    private val moteur = ServiceMoteurLangageKotlin.unique

    /** La racine temporaire des sources externes (comportement L1). */
    @TempDir
    lateinit var racine: Path

    /** Une session NEUVE par test (états indépendants — comportement dédié ci-dessous). */
    private fun session() = moteur.creerSession()

    // ------------------------------------------------------------------
    // Cycle de vie et tolérance.
    // ------------------------------------------------------------------

    @Test
    fun `didOpen-invalide---une-erreur-de-syntaxe,-plage-et-message-du-parseur`() {
        val s = session()
        // Sonde : « val x = » rend UN marqueur mono-entrée (7..7) portant le
        // message amont « Expecting an expression ».
        s.ouvrir("file:///a.kt", "val x = ")
        val diagnostics = s.diagnostics("file:///a.kt")
        assertEquals(1, diagnostics.size, "une seule erreur de syntaxe")
        val diagnostic = diagnostics.single()
        assertEquals(CodeDiagnosticKotlin.SYNTAXE, diagnostic.code)
        assertEquals(SeveriteKotlin.ERREUR, diagnostic.severite)
        assertEquals("Expecting an expression", diagnostic.message, "message du parseur repris tel quel")
        assertEquals(PlageTexte(7, 7), diagnostic.plage, "plage du marqueur du parseur (fidèle, vide)")
    }

    @Test
    fun `didChange-corrige---les-diagnostics-disparaissent`() {
        val s = session()
        s.ouvrir("file:///a.kt", "val x = ")
        assertEquals(1, s.diagnostics("file:///a.kt").size, "l'ouverture invalide signale")
        s.modifier("file:///a.kt", "val x = 1\n")
        assertEquals(0, s.diagnostics("file:///a.kt").size, "la correction efface les diagnostics")
    }

    @Test
    fun `didClose---le-document-est-oublie`() {
        val s = session()
        s.ouvrir("file:///a.kt", "val x = ")
        assertEquals(1, s.diagnostics("file:///a.kt").size)
        s.fermer("file:///a.kt")
        assertEquals(0, s.diagnostics("file:///a.kt").size, "le document fermé est un inconnu : liste vide")
    }

    @Test
    fun `document-inconnu---liste-vide,-jamais-d'exception`() {
        val s = session()
        assertEquals(0, s.diagnostics("file:///jamais-ouvert.kt").size, "réponse vide honnête, pas d'exception")
    }

    // ------------------------------------------------------------------
    // Diagnostics d'imports (K2) : INUTILISE, joker, conflits, alias.
    // ------------------------------------------------------------------

    @Test
    fun `import-inutilise---signale`() {
        val s = session()
        s.ouvrir("file:///a.kt", "import java.util.ArrayList\n\nval exemple = 1\n")
        val diagnostics = s.diagnostics("file:///a.kt")
        assertEquals(1, diagnostics.size)
        val diagnostic = diagnostics.single()
        assertEquals(CodeDiagnosticKotlin.IMPORT_INUTILISE, diagnostic.code)
        assertEquals(PlageTexte(0, 26), diagnostic.plage, "plage de la directive entière")
        assertTrue(diagnostic.message.contains("java.util.ArrayList"), "le message porte le chemin importé")
    }

    @Test
    fun `nom-present-en-commentaire-seulement---l'import-reste-inutilise`() {
        val s = session()
        s.ouvrir("file:///a.kt", "import java.util.ArrayList\n// ArrayList en commentaire\nval exemple = 1\n")
        val diagnostics = s.diagnostics("file:///a.kt")
        assertEquals(1, diagnostics.size, "un nom en COMMENTAIRE ne compte pas — seuls les identifiants de code comptent")
        assertEquals(CodeDiagnosticKotlin.IMPORT_INUTILISE, diagnostics.single().code)
    }

    @Test
    fun `import-utilise---aucun-diagnostic-(sous-rapport)`() {
        val s = session()
        s.ouvrir("file:///a.kt", "import java.util.ArrayList\nval liste = ArrayList()\n")
        assertEquals(0, s.diagnostics("file:///a.kt").size, "silence conservateur D3 : rien à signaler")
    }

    @Test
    fun `joker---jamais-signale`() {
        val s = session()
        s.ouvrir("file:///a.kt", "import java.io.*\nval exemple = 1\n")
        assertEquals(0, s.diagnostics("file:///a.kt").size, "le joker n'est jamais signalé")
    }

    @Test
    fun `imports-conflictuels---signale-sur-le-plus-tardif-avec-les-deux-noms`() {
        val s = session()
        val source = (
            "import java.util.LinkedList\n" +
                "import java.awt.LinkedList\n" +
                "\n" +
                "val fil: LinkedList? = null\n"
            )
        s.ouvrir("file:///a.kt", source)
        val diagnostics = s.diagnostics("file:///a.kt")
        assertEquals(1, diagnostics.size, "un seul diagnostic : le conflit (le nom est utilisé, pas d'inutilisé)")
        val diagnostic = diagnostics.single()
        assertEquals(CodeDiagnosticKotlin.IMPORT_CONFLICTUEL, diagnostic.code)
        val debutTardif = source.indexOf("import java.awt.LinkedList")
        assertEquals(
            PlageTexte(debutTardif, debutTardif + "import java.awt.LinkedList".length),
            diagnostic.plage,
            "signalé sur le PLUS TARDIF des deux directives",
        )
        assertTrue(diagnostic.message.contains("java.util.LinkedList"), "le message porte le premier nom")
        assertTrue(diagnostic.message.contains("java.awt.LinkedList"), "le message porte le second nom")
    }

    @Test
    fun `meme-import-repete---pas-un-conflit-(sous-rapport)`() {
        val s = session()
        s.ouvrir(
            "file:///a.kt",
            "import java.util.LinkedList\nimport java.util.LinkedList\n\nval fil: LinkedList? = null\n",
        )
        assertEquals(0, s.diagnostics("file:///a.kt").size, "même chemin répété : silence conservateur D3")
    }

    @Test
    fun `alias-inutilise---signale`() {
        val s = session()
        s.ouvrir("file:///a.kt", "import kotlin.math.PI as constante\n\nval exemple = 1\n")
        val diagnostics = s.diagnostics("file:///a.kt")
        assertEquals(1, diagnostics.size, "l'alias est le nom effectif : absent du code, il se signale")
        val diagnostic = diagnostics.single()
        assertEquals(CodeDiagnosticKotlin.IMPORT_INUTILISE, diagnostic.code)
        assertTrue(diagnostic.message.contains("constante"), "le message porte l'alias effectif")
    }

    @Test
    fun `alias-utilise---aucun-diagnostic`() {
        val s = session()
        s.ouvrir("file:///a.kt", "import kotlin.math.PI as constante\n\nval rayon = constante\n")
        assertEquals(0, s.diagnostics("file:///a.kt").size)
    }

    @Test
    fun `script-kts---parse-et-diagnostics-en-mode-script`() {
        val s = session()
        // Sonde : en mode fichier, ces instructions de premier niveau lèvent
        // « Expecting a top level declaration » ; en mode script (.kts),
        // AUCUNE erreur — le parse suit l'extension de l'URI.
        s.ouvrir("file:///build.kts", "import java.util.ArrayList\nprintln(\"bonjour\")\n")
        val diagnostics = s.diagnostics("file:///build.kts")
        assertEquals(1, diagnostics.size, "parse script SAIN (aucune erreur de syntaxe) + l'import inutilisé se signale")
        assertEquals(CodeDiagnosticKotlin.IMPORT_INUTILISE, diagnostics.single().code)
    }

    @Test
    fun `didChange-sans-didOpen---tolerant,-vaut-une-ouverture`() {
        val s = session()
        s.modifier("file:///a.kt", "val x = ")
        assertEquals(1, s.diagnostics("file:///a.kt").size, "la modification sans ouverture vaut une ouverture")
    }

    @Test
    fun `fermeture---tout-abandonne,-mutations-posterieures-no-ops`() {
        val s = session()
        s.ouvrir("file:///a.kt", "val x = ")
        assertEquals(1, s.diagnostics("file:///a.kt").size)
        s.close()
        // TOUTES les mutations postérieures sont des no-ops silencieux.
        s.ouvrir("file:///b.kt", "val y = ")
        s.modifier("file:///a.kt", "val y = 2\n")
        s.fermer("file:///a.kt")
        s.definirClasspath(ClasspathKotlin(entrees = emptyList(), racinesSources = emptyList()))
        s.rafraichirSources()
        assertEquals(0, s.diagnostics("file:///a.kt").size, "le document d'avant la fermeture est oublié")
        assertEquals(0, s.diagnostics("file:///b.kt").size, "aucun nouveau document n'est admis après fermeture")
        assertEquals(0, s.plagesPliables("file:///a.kt").size)
        assertNull(s.survol("file:///a.kt", 0), "aucune requête ne rend plus rien après fermeture")
    }

    @Test
    fun `sessions-distinctes---etats-independants`() {
        val a = moteur.creerSession()
        val b = moteur.creerSession()
        a.ouvrir("file:///a.kt", "val x = ")
        assertEquals(1, a.diagnostics("file:///a.kt").size, "la session A voit son document")
        assertEquals(0, b.diagnostics("file:///a.kt").size, "la session B est étanche : elle ne connaît PAS le document de A")
        a.fermer("file:///a.kt")
        assertEquals(0, a.diagnostics("file:///a.kt").size)
    }

    // ------------------------------------------------------------------
    // Tags et informations liées (KP15).
    // ------------------------------------------------------------------

    @Test
    fun `import-inutilise---porte-le-tag-INUTILE-(KP15)`() {
        val s = session()
        s.ouvrir("file:///a.kt", "import java.util.ArrayList\n\nval exemple = 1\n")
        val diagnostic = s.diagnostics("file:///a.kt").single()
        assertEquals(listOf(TagDiagnosticKotlin.INUTILE), diagnostic.tags, "l'import inutilisé porte le tag INUTILE")
    }

    @Test
    fun `erreur-de-syntaxe---aucun-tag-(KP15,-sous-rapport)`() {
        val s = session()
        s.ouvrir("file:///a.kt", "val x = ")
        val diagnostic = s.diagnostics("file:///a.kt").single()
        assertEquals(0, diagnostic.tags.size, "silence conservateur : l'erreur de syntaxe ne porte AUCUN tag")
    }

    @Test
    fun `imports-conflictuels---information-liee-sur-la-PREMIERE-directive-(KP15)`() {
        val s = session()
        val source = (
            "import java.util.LinkedList\n" +
                "import java.awt.LinkedList\n" +
                "\n" +
                "val fil: LinkedList? = null\n"
            )
        s.ouvrir("file:///a.kt", source)
        val diagnostic = s.diagnostics("file:///a.kt").single()
        assertEquals(1, diagnostic.informationsLiees.size, "le conflit porte UNE information liée")
        val information = diagnostic.informationsLiees.single()
        assertEquals(PlageTexte(0, "import java.util.LinkedList".length), information.plage, "l'information liée pointe la PREMIÈRE directive")
        assertTrue(information.message.isNotBlank(), "le message de l'information liée est lisible")
    }

    @Test
    fun `import-inutilise---aucune-information-liee-(KP15)`() {
        val s = session()
        s.ouvrir("file:///a.kt", "import java.util.ArrayList\n\nval exemple = 1\n")
        val diagnostic = s.diagnostics("file:///a.kt").single()
        assertEquals(0, diagnostic.informationsLiees.size, "l'import inutilisé ne porte AUCUNE information liée")
    }

    // ------------------------------------------------------------------
    // Plages pliables (classe, fonctions, imports, KDoc).
    // ------------------------------------------------------------------

    @Test
    fun `plages-pliables---classe,-fonctions,-imports-et-KDoc`() {
        val s = session()
        val texteKdoc = "/** KDoc de la classe. */"
        val source = (
            "import java.util.ArrayList\n" +
                "\n" +
                "$texteKdoc\n" +
                "class Serviteur {\n" +
                "    val nom: String = \"a\"\n" +
                "\n" +
                "    fun saluer(): String {\n" +
                "        return nom\n" +
                "    }\n" +
                "}\n" +
                "\n" +
                "fun racine(): Int {\n" +
                "    return 0\n" +
                "}\n"
            )
        s.ouvrir("file:///a.kt", source)
        val plages = s.plagesPliables("file:///a.kt")

        val imports = plages.filter { it.genre == GenrePlagePliable.IMPORTS }
        val kdoc = plages.filter { it.genre == GenrePlagePliable.KDOC }
        val classes = plages.filter { it.genre == GenrePlagePliable.CLASSE }
        val fonctions = plages.filter { it.genre == GenrePlagePliable.FONCTION }

        assertEquals(1, imports.size, "une plage pour le bloc des imports")
        assertEquals(PlageTexte(0, 26), imports.single().plage)

        assertEquals(1, kdoc.size, "une plage pour le KDoc")
        assertEquals(
            PlageTexte(source.indexOf(texteKdoc), source.indexOf(texteKdoc) + texteKdoc.length),
            kdoc.single().plage,
        )

        assertEquals(1, classes.size, "une plage pour le corps de la classe")
        assertTrue(classes.single().plage.debut < classes.single().plage.fin)

        assertEquals(2, fonctions.size, "une plage par corps de bloc de fonction (saluer, racine)")
        fonctions.forEach { assertTrue(it.plage.debut < it.plage.fin) }
    }

    @Test
    fun `plages-pliables---document-inconnu,-ferme-ou-vide,-jamais-d-exception`() {
        val s = session()
        assertEquals(0, s.plagesPliables("file:///inconnu.kt").size, "document inconnu : liste vide")
        s.ouvrir("file:///vide.kt", "")
        assertEquals(0, s.plagesPliables("file:///vide.kt").size, "document vide : liste vide")
        s.ouvrir("file:///ferme.kt", "class A {\n    fun f() {}\n}\n")
        s.fermer("file:///ferme.kt")
        assertEquals(0, s.plagesPliables("file:///ferme.kt").size, "document fermé : liste vide")
    }

    // ------------------------------------------------------------------
    // Survol (S2) et rafraichirSources (L1).
    // ------------------------------------------------------------------

    @Test
    fun `survol-riche---la-KDoc-du-document-courant-accompagne-la-signature-(S2)`() {
        val s = session()
        val source = (
            "/** Salue le destinataire reçu. */\n" +
                "fun saluer(destinataire: String): String {\n" +
                "    return \"Bonjour\"\n" +
                "}\n" +
                "\n" +
                "fun appelant(): String = saluer(\"monde\")\n"
            )
        s.ouvrir("file:///a.kt", source)
        val deplacement = source.indexOf("= saluer") + 2
        val survol = s.survol("file:///a.kt", deplacement)
        assertNotNull(survol, "la référence au nom déclaré se résout dans le document courant")
        assertTrue(survol.signature.contains("saluer"), "la signature de la déclaration visée")
        assertTrue(survol.signature.contains("fun"), "la signature rend la forme écrite de la fonction")
        assertEquals("Salue le destinataire reçu.", survol.documentation, "la KDoc du document courant accompagne la signature")
    }

    @Test
    fun `survol-sans-KDoc---documentation-absente,-signature-seule`() {
        val s = session()
        val source = "fun calculer(): Int = 1\nval resultat = calculer()\n"
        s.ouvrir("file:///a.kt", source)
        val deplacement = source.indexOf("= calculer") + 2
        val survol = s.survol("file:///a.kt", deplacement)
        assertNotNull(survol)
        assertTrue(survol.signature.contains("calculer"))
        assertNull(survol.documentation, "sans KDoc : documentation absente, signature seule")
    }

    @Test
    fun `rafraichirSources---une-declaration-externe-devient-resolvable-(L1)`() {
        val externe = racine.resolve("Fabrique.kt")
        Files.writeString(
            externe,
            "/** Fabrique des instances. */\nclass Fabrique {\n    fun creer(): Int = 1\n}\n",
        )
        val s = session()
        s.definirClasspath(
            ClasspathKotlin(entrees = emptyList(), racinesSources = listOf(racine.toAbsolutePath().toString())),
        )
        val uri = "file:///principal.kt"
        val texte = "val usine = Fabrique()\n"
        s.ouvrir(uri, texte)
        val deplacement = texte.indexOf("Fabrique")
        assertNull(s.survol(uri, deplacement), "AVANT le rafraîchissement : la déclaration externe est hors de portée")
        s.rafraichirSources()
        val survol = s.survol(uri, deplacement)
        assertNotNull(survol, "APRÈS le rafraîchissement : la déclaration externe devient resolvable")
        assertTrue(survol.signature.contains("Fabrique"))
        assertEquals("Fabrique des instances.", survol.documentation, "la KDoc de la source externe voyage avec la signature")
    }

    @Test
    fun `rafraichirSources---session-fermee,-no-op-honnete-(L1)`() {
        val s = session()
        s.definirClasspath(
            ClasspathKotlin(entrees = emptyList(), racinesSources = listOf(racine.toAbsolutePath().toString())),
        )
        s.close()
        s.rafraichirSources() // no-op honnête : aucune exception, aucun effet
        assertNull(s.survol("file:///a.kt", 0), "la session fermée ne rend plus rien")
    }
}
