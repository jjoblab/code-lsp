package jo.codeide.lsp.kotlin.format

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin

/**
 * Fiche 019 — FormateurKotlinTest : le FORMATEUR BORNÉ du cahier
 * (formatage, L2 — plan 1.3.0, `textDocument/formating` et
 * `rangeFormatting`, ADR 0202 miroir) — 9 comportements du cahier
 * docs/cahier-specifications-kotlin/, par le SPI (chemin de production
 * D6), moteur RÉEL (la REPRISE voie C R6, manifeste
 * docs/PROVENANCE-REPRISE-KOTLIN.md §3).
 *
 * Le contrat éprouvé est celui de l'honnêteté D3 : ré-indentation
 * canonique SEULE (le contenu des lignes n'est jamais réécrit),
 * intouchables préservés (jeton multi-ligne : chaîne brute,
 * commentaire de bloc, KDoc), refus sur arbre en erreur, idempotence,
 * et le formatage de plage ne touche QUE la plage demandée.
 *
 * Chaque comportement porte le nom exact du test source (identifiant
 * de traçabilité du cahier).
 */
class Fiche019FormateurKotlinTest {

    /** Une session NEUVE par comportement (moteur réel, chemin D6). */
    private fun sessionNeuve() = ServiceMoteurLangageKotlin.unique.creerSession()

    /** Décalage du PREMIER caractère de [occurrence] dans [texte]. */
    private fun decalageDe(
        texte: String,
        occurrence: String,
        numero: Int = 0,
    ): Int {
        var index = -1
        repeat(numero + 1) {
            index = texte.indexOf(occurrence, index + 1)
        }
        return index
    }

    @Test
    fun `document-sain---ré-indentation-canonique,-lignes-déjà-justes-intactes`() {
        val texte =
            "fun a() {\n" +
                "val x = 1\n" +
                "    }\n"
        val session = sessionNeuve()
        session.ouvrir("fixture://a.kt", texte)
        val editions = session.formatage("fixture://a.kt", 4)
        // Deux éditions : +4 espaces pour le corps, retrait du blanc
        // excédentaire de l'accolade fermante (le fermant vit à la
        // profondeur de son bloc). Le contenu des lignes ne bouge pas.
        assertEquals(2, editions.size)
        val editionCorps = editions.single { it.texte == "    " }
        assertEquals(decalageDe(texte, "val x = 1"), editionCorps.plage.debut)
        assertEquals(decalageDe(texte, "val x = 1"), editionCorps.plage.fin)
        val editionFermante = editions.single { it.texte.isEmpty() }
        assertEquals(decalageDe(texte, "    }"), editionFermante.plage.debut)
        assertEquals(decalageDe(texte, "    }") + 4, editionFermante.plage.fin)
        session.close()
    }

    @Test
    fun `idempotence---reformater-un-document-formaté-ne-change-rien`() {
        val texte =
            "fun a() {\n" +
                "    val x = 1\n" +
                "}\n"
        val session = sessionNeuve()
        session.ouvrir("fixture://a.kt", texte)
        assertTrue(session.formatage("fixture://a.kt", 4).isEmpty())
        session.close()
    }

    @Test
    fun `chaîne-brute-multi-lignes---le-contenu-entre-les-délimiteurs-est-inchangé`() {
        val texte =
            "fun a() {\n" +
                "val s = \"\"\"\n" +
                "      contenu  mal   indenté\n" +
                "  \"\"\"\n" +
                "}\n"
        val session = sessionNeuve()
        session.ouvrir("fixture://a.kt", texte)
        val editions = session.formatage("fixture://a.kt", 4)
        // La chaîne brute ENTRE SES DÉLIMITEURS (inclus) est du
        // CONTENU : AUCUNE édition ne la chevauche — le blanc de tête
        // des lignes internes (y compris celui devant le délimiteur
        // fermant) est du contenu de la chaîne, jamais réécrit.
        val debutChaine = decalageDe(texte, "\"\"\"")
        val finChaine = decalageDe(texte, "\"\"\"", 1) + "\"\"\"".length
        assertTrue(
            editions.none { it.plage.debut < finChaine && it.plage.fin > debutChaine },
            "une édition traverse la chaîne brute : $editions",
        )
        // La ligne PORTEUSE (hors chaîne) est ré-indentée.
        assertTrue(editions.any { it.plage.debut == decalageDe(texte, "val s") && it.texte == "    " })
        session.close()
    }

    @Test
    fun `KDoc-et-commentaire-de-bloc---le-contenu-est-inchangé,-la-ligne-porteuse-s'indente`() {
        val texte =
            "/**\n" +
                "   * doc mal indentée\n" +
                "*/\n" +
                "fun a() {\n" +
                "/* bloc\n" +
                "     mal indenté */\n" +
                "val x = 1\n" +
                "}\n"
        val session = sessionNeuve()
        session.ouvrir("fixture://a.kt", texte)
        val editions = session.formatage("fixture://a.kt", 4)
        // Les lignes INTERNES des jetons multi-lignes (KDoc, bloc)
        // gardent leur blanc de tête ; la ligne PORTEUSE d'un bloc
        // (le commentaire s'ouvre dessus) est du CODE — elle
        // s'indente avec son bloc. Le CONTENU des commentaires n'est
        // JAMAIS chevauché par une édition.
        val kdoc = decalageDe(texte, "/**")
        val finKdoc = decalageDe(texte, "*/") + 2
        val bloc = decalageDe(texte, "/* bloc")
        val finBloc = decalageDe(texte, "*/", 1) + 2
        assertTrue(
            editions.none { it.plage.debut < finKdoc && it.plage.fin > kdoc },
            "une édition traverse le KDoc : $editions",
        )
        assertTrue(
            editions.none { it.plage.debut < finBloc && it.plage.fin > bloc },
            "une édition traverse le commentaire de bloc : $editions",
        )
        assertEquals(
            listOf(decalageDe(texte, "/* bloc"), decalageDe(texte, "val x = 1")),
            editions.map { it.plage.debut },
        )
        assertTrue(editions.all { it.texte == "    " })
        session.close()
    }

    @Test
    fun `document-en-erreur-de-syntaxe---liste-d'éditions-VIDE`() {
        val session = sessionNeuve()
        session.ouvrir("fixture://a.kt", "fun a() {\nval x = (\n}\n")
        assertTrue(session.diagnostics("fixture://a.kt").isNotEmpty())
        assertTrue(session.formatage("fixture://a.kt", 4).isEmpty())
        session.close()
    }

    @Test
    fun `plage---seules-les-lignes-qui-intersectent-la-plage-bougent`() {
        val texte =
            "fun un() {\n" +
                "val a = 1\n" +
                "}\n" +
                "\n" +
                "fun deux() {\n" +
                "val b = 2\n" +
                "}\n"
        val debutPlage = decalageDe(texte, "fun deux")
        val session = sessionNeuve()
        session.ouvrir("fixture://a.kt", texte)
        val editions = session.formatagePlage("fixture://a.kt", debutPlage, texte.length, 4)
        // La ligne mal indentée de la première fonction (hors plage) ne
        // reçoit AUCUNE édition ; celles de la plage restent DANS la
        // plage (une édition hors plage décalerait le buffer client).
        assertTrue(editions.isNotEmpty())
        assertTrue(editions.all { it.plage.debut >= debutPlage && it.plage.fin <= texte.length })
        assertEquals(
            listOf(decalageDe(texte, "val b = 2")),
            editions.map { it.plage.debut },
        )
        session.close()
    }

    @Test
    fun `continuation-de-liste---éléments-un-niveau-sous-l'ouvrante,-fermeture-à-la-base`() {
        val texte =
            "fun somme(\n" +
                "a: Int,\n" +
                "b: Int,\n" +
                "): Int {\n" +
                "return a + b\n" +
                "}\n"
        val session = sessionNeuve()
        session.ouvrir("fixture://a.kt", texte)
        val editions = session.formatage("fixture://a.kt", 4)
        // Les éléments de la liste vivent UN niveau sous l'ouvrante ;
        // la ligne qui FERME la liste revient à la base (0) — déjà
        // juste, aucune édition ; le corps de la fonction est à 1.
        assertEquals(
            listOf(decalageDe(texte, "a: Int"), decalageDe(texte, "b: Int"), decalageDe(texte, "return a + b")),
            editions.map { it.plage.debut },
        )
        assertTrue(editions.all { it.texte == "    " })
        session.close()
    }

    @Test
    fun `taille-d'indentation---deux-espaces-par-niveau-sur-demande-du-client`() {
        val texte = "fun a() {\nval x = 1\n}\n"
        val session = sessionNeuve()
        session.ouvrir("fixture://a.kt", texte)
        val editions = session.formatage("fixture://a.kt", 2)
        assertEquals(listOf("  "), editions.map { it.texte })
        session.close()
    }

    @Test
    fun `document-inconnu-ou-fermé---liste-VIDE,-jamais-d'exception`() {
        val session = sessionNeuve()
        assertTrue(session.formatage("fixture://inconnu.kt", 4).isEmpty())
        session.ouvrir("fixture://a.kt", "fun a() {\nval x = 1\n}\n")
        session.fermer("fixture://a.kt")
        assertTrue(session.formatage("fixture://a.kt", 4).isEmpty())
        session.close()
    }
}
