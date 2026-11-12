// CodeIDE — tests des capacités ARBRE (module :kotlin, vague V-I1 — ADR
// 0136 étape B.2 : linkedEditingRange, documentColor, onTypeFormatting).
// Code NEUF jjoblab (clean-room — comportements : la paire appariée
// couvre le caret ; les couleurs hexadécimales Android 6/8 chiffres ; la
// ré-indentation bornée à la ligne ; documents inconnus/fermés = vides).
package jo.codeide.lsp.kotlin.session

import jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * La vague V-I1 — les TROIS capacités arbre (ADR 0136 étape B.2, avant le
 * palier 2) : les plages liées des délimiteurs appariés, les couleurs
 * littérales, la ré-indentation à la frappe.
 */
class CapacitesArbreKotlinTest {

    /** Une session directe (sans classpath — les capacités arbre n'en dépendent pas). */
    private fun session(): SessionLangageKotlinDocuments =
        (ServiceMoteurLangageKotlin.unique as MoteurLangageKotlinNoyau).creerSession()
            as SessionLangageKotlinDocuments

    // ── linkedEditingRange ──────────────────────────────────────────────

    /** La paire `(`…`)` couvrant le caret se rend, l'ouvrant comme le fermant. */
    @Test
    fun `plage-liee---paire-de-parentheses-appariees`() {
        val session = session()
        try {
            val texte = "fun demo() {\n  calculer(1, 2)\n}\n"
            session.ouvrir("file:///lie.kt", texte)
            val ouvrante = texte.indexOf('(')
            val fermante = texte.indexOf(')')
            val surOuvrante = session.plagesLiees("file:///lie.kt", ouvrante + 1)
            assertNotNull(surOuvrante, "le caret sur l'ouvrant rend la paire")
            assertEquals(ouvrante, surOuvrante.plageOuvrante.debut, "la plage ouvrante est exacte")
            assertEquals(fermante, surOuvrante.plageFermante.debut, "la plage fermante est appariée")
            val surFermante = session.plagesLiees("file:///lie.kt", fermante)
            assertNotNull(surFermante, "le caret sur le fermant rend la paire")
            assertEquals(ouvrante, surFermante.plageOuvrante.debut, "le caret sur le fermant rend la MÊME paire")
        } finally {
            session.close()
        }
    }

    /** Les chevrons ne s'apparient QUE dans une liste d'arguments de TYPE. */
    @Test
    fun `plage-liee---chevrons-des-arguments-de-type-()-jamais-les-comparaisons`() {
        val session = session()
        try {
            val texte = "val xs: List<Int> = emptyList()\nval b = 1 < 2\n"
            session.ouvrir("file:///chevrons.kt", texte)
            val lt = texte.indexOf('<')
            val gt = texte.indexOf('>')
            val paire = session.plagesLiees("file:///chevrons.kt", lt)
            assertNotNull(paire, "le chevron OUVRANT d'une liste de type rend la paire")
            assertEquals(gt, paire.plageFermante.debut, "le chevron fermant apparié")
            // Le `<` de la COMPARAISON n'apparie rien (jamais de paire inventée).
            val comparaison = texte.lastIndexOf('<')
            assertNull(
                session.plagesLiees("file:///chevrons.kt", comparaison),
                "un chevron de comparaison n'apparie RIEN",
            )
        } finally {
            session.close()
        }
    }

    /** Hors délimiteur : `null` honnête (un identifiant n'est pas un délimiteur). */
    @Test
    fun `plage-liee---hors-delimiteur-null-honnete`() {
        val session = session()
        try {
            val texte = "fun demo() { calculer(1) }\n"
            session.ouvrir("file:///hors.kt", texte)
            assertNull(
                session.plagesLiees("file:///hors.kt", texte.indexOf("demo")),
                "un caret sur un identifiant ne rend aucune paire",
            )
            assertNull(session.plagesLiees("file:///inconnu.kt", 0), "un document inconnu rend null")
        } finally {
            session.close()
        }
    }

    // ── documentColor ───────────────────────────────────────────────────

    /** Les littéraux hexadécimaux Android 8 chiffres rendent RGBA exact. */
    @Test
    fun `couleurs---hexadecimal-8-chiffres-argb-exact`() {
        val session = session()
        try {
            val texte = "val primaire = 0xFF1A73E8\nval secondaire = 0x1A73E8\nval pasCouleur = 0xFFF\n"
            session.ouvrir("file:///couleurs.kt", texte)
            val couleurs = session.couleursDocument("file:///couleurs.kt")
            assertEquals(2, couleurs.size, "les littéraux 8 et 6 chiffres seulement (0xFFF exclu)")
            val argb = couleurs[0]
            assertEquals(texte.indexOf("0xFF1A73E8"), argb.plage.debut, "la plage du littéral 8 chiffres")
            assertEquals(1.0, argb.alpha, 1e-9, "alpha FF = 1.0")
            assertEquals(0x1A / 255.0, argb.rouge, 1e-9, "rouge 1A")
            assertEquals(0x73 / 255.0, argb.vert, 1e-9, "vert 73")
            assertEquals(0xE8 / 255.0, argb.bleu, 1e-9, "bleu E8")
            val rgb = couleurs[1]
            assertEquals(1.0, rgb.alpha, 1e-9, "6 chiffres = OPAQUE (alpha FF implicite)")
            assertEquals(0x1A / 255.0, rgb.rouge, 1e-9, "le premier canal est le ROUGE en 6 chiffres")
        } finally {
            session.close()
        }
    }

    /** Sans littéral de la bonne largeur : liste vide (jamais une couleur inventée). */
    @Test
    fun `couleurs---sans-hexadecimal-liste-vide`() {
        val session = session()
        try {
            session.ouvrir("file:///sans.kt", "val x = 42\nval y = \"0xFF1A73E8\"\n")
            assertTrue(session.couleursDocument("file:///sans.kt").isEmpty(), "un décimal et une CHAÎNE ne sont pas des couleurs")
            assertTrue(session.couleursDocument("file:///inconnu.kt").isEmpty(), "document inconnu = vide")
        } finally {
            session.close()
        }
    }

    // ── onTypeFormatting ────────────────────────────────────────────────

    /** La frappe `\n` après une ouverture nette indente la nouvelle ligne. */
    @Test
    fun `frappe-retour-ligne---indent-de-l-ouverture-nette`() {
        val session = session()
        try {
            val texte = "fun demo() {\n"
            session.ouvrir("file:///frappe.kt", texte)
            val editions = session.formatageFrappé("file:///frappe.kt", texte.length, '\n', 2)
            assertEquals(1, editions.size, "une seule édition (le blanc de tête)")
            assertEquals("  ", editions.single().texte, "deux espaces par ouverture nette de la ligne précédente")
            assertEquals(texte.length, editions.single().plage.debut, "l'édition vise la NOUVELLE ligne (vide)")
        } finally {
            session.close()
        }
    }

    /** La frappe `}` aligne l'accolade sur la ligne de son OUVRANT apparié. */
    @Test
    fun `frappe-accolade-fermante---alignement-sur-l-ouvrant`() {
        val session = session()
        try {
            val texte = "fun demo() {\n    calculer(1)\n}\n"
            session.ouvrir("file:///aligne.kt", texte)
            // Le caret sur l'accolade fermante de la 3e ligne (indent 0 déjà juste).
            val fermente = texte.lastIndexOf('}')
            val juste = session.formatageFrappé("file:///aligne.kt", fermente + 1, '}', 2)
            assertTrue(juste.isEmpty(), "une ligne déjà alignée ne reçoit AUCUNE édition")
            // Ligne trop indentée : l'édition ramène le blanc de tête à 0.
            val mauvais = "fun demo() {\n    calculer(1)\n        }\n"
            session.modifier("file:///aligne.kt", mauvais)
            val fermenteMauvais = mauvais.lastIndexOf('}')
            val editions = session.formatageFrappé("file:///aligne.kt", fermenteMauvais + 1, '}', 2)
            assertEquals(1, editions.size, "une édition du blanc de tête")
            assertEquals("", editions.single().texte, "l'accolade s'aligne sur la colonne 0 de son ouvrant")
        } finally {
            session.close()
        }
    }

    /** Une frappe non gérée (autre caractère) : liste vide honnête. */
    @Test
    fun `frappe-autre-caractere---liste-vide`() {
        val session = session()
        try {
            session.ouvrir("file:///autre.kt", "val x = 1;")
            assertTrue(
                session.formatageFrappé("file:///autre.kt", 10, ';', 2).isEmpty(),
                "un point-virgule ne déclenche RIEN",
            )
            assertTrue(
                session.formatageFrappé("file:///inconnu.kt", 0, '\n', 2).isEmpty(),
                "document inconnu = vide",
            )
        } finally {
            session.close()
        }
    }
}
