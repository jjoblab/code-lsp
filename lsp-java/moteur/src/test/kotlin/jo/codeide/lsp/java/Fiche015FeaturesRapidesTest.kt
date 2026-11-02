package jo.codeide.lsp.java

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Fiche 015 — EcjFastFeaturesTest : extension du fast-path aux features par
 * frappe (hover et signature help) servis par la chaîne à 3 niveaux —
 * arbre focalisé retenu → arbre réel du parseCache → réduction
 * SourceFocuser. Oracle d'équivalence stricte contre un moteur témoin NEUF.
 */
class Fiche015FeaturesRapidesTest {

    /** Fixture Main (paquet app, marqueur « ⌂ ») — méthodes add/join/edite/autre/bourre. */
    private val base = """
        package app;

        public class Main {
            private int total = 10;

            int add(int a, int b) {
                return a + b;
            }

            String join(String first, String second) {
                return first + second;
            }

            void edite() {⌂}

            void autre() {
                String mot = "mot";
                String r = mot.toUpperCase();
            }

            void bourre() {
                int b1 = 1;
                int b2 = 2;
            }
        }
    """.trimIndent()

    private val frappe = " int somme = add(total, 2);"

    private fun sourceAvec(contenu: String): String {
        val marqueur = base.indexOf('⌂')
        return base.substring(0, marqueur) + contenu + base.substring(marqueur + 1)
    }

    /** Le témoin neuf : moteur vierge, aucune réponse périmée possible. */
    private fun temoin(): MoteurJava = MoteurJava()

    @Test
    fun hoverDansCorpsEditeServiParArbreFocalise() {
        val moteur = MoteurJava()
        val chemin = "/app/Main.java"
        // didChange + diagnose initial, puis didChange + diagnose de la frappe.
        moteur.diagnose(chemin, sourceAvec(""))
        val sourceFrappe = sourceAvec(frappe)
        moteur.invalidateParseCache(chemin)
        moteur.diagnose(chemin, sourceFrappe)
        assertEquals(1, moteur.fastDiagnoseHits, "la frappe confinée est un diagnose rapide")

        val temoin = temoin()
        for (point in listOf("somme", "add(", "total")) {
            val offset = offsetDe(sourceFrappe, point)
            val reponse = moteur.hoverAt(chemin, sourceFrappe, offset)
            val attendu = temoin.hoverAt(chemin, sourceFrappe, offset)
            assertEquals(attendu, reponse, "hover « $point » identique au témoin")
            assertNotNull(reponse, "hover non null pour « $point »")
        }
        assertTrue(moteur.fastResolveHits >= 1, "fastResolveHits ≥ 1 : ${moteur.fastResolveHits}")
        assertEquals(0, moteur.cachedResolveHits, "le cache est vide après un diagnose RAPIDE")
    }

    @Test
    fun hoverHorsCorpsHoteRepliSansArbreFocalise() {
        val moteur = MoteurJava()
        val chemin = "/app/Main.java"
        moteur.diagnose(chemin, sourceAvec(""))
        val sourceFrappe = sourceAvec(frappe)
        moteur.diagnose(chemin, sourceFrappe)

        val temoin = temoin()
        // Premier hover : « mot.toUpperCase » dans le corps MASQUE autre.
        val offsetMot = offsetDe(sourceFrappe, "mot.toUpperCase")
        assertEquals(temoin.hoverAt(chemin, sourceFrappe, offsetMot), moteur.hoverAt(chemin, sourceFrappe, offsetMot), "hover mot.toUpperCase identique au témoin")
        assertEquals(0, moteur.fastResolveHits, "l'arbre focalisé ne sert JAMAIS un offset hors du corps hôte")
        assertEquals(0, moteur.cachedResolveHits, "le repli par chemin réduit REMPLIT le cache sans compter un hit")
        // Second hover : « first + second » — paramètres de join(), autre corps masqué.
        val offsetFirst = offsetApres(sourceFrappe, "return first", "first")
        assertEquals(temoin.hoverAt(chemin, sourceFrappe, offsetFirst), moteur.hoverAt(chemin, sourceFrappe, offsetFirst), "hover first identique au témoin")
        assertEquals(1, moteur.cachedResolveHits, "servi par l'arbre réel du parseCache")
        assertEquals(0, moteur.fastResolveHits)
    }

    @Test
    fun contenuDifferentArbrePerimeJamaisServi() {
        val moteur = MoteurJava()
        val chemin = "/app/Main.java"
        moteur.diagnose(chemin, sourceAvec(frappe))
        // Frappe suivante SANS diagnose (debounce non tiré) : changement de TYPE.
        val s2 = sourceAvec("String somme = \"a\";")
        val offset = offsetDe(s2, "somme")
        val temoin = temoin()
        val reponse = moteur.hoverAt(chemin, s2, offset)
        assertEquals(temoin.hoverAt(chemin, s2, offset), reponse, "réponse = témoin")
        assertNotNull(reponse)
        assertTrue(reponse.contains("String"), "le NOUVEAU type est rendu : $reponse")
        assertTrue(!reponse.contains("int somme"), "jamais ce qu'aurait répondu l'arbre périmé : $reponse")
        assertEquals(0, moteur.fastResolveHits, "la garde de contenu rejette avant tout comptage")
    }

    @Test
    fun signatureHelpDansCorpsEditeServiParArbreFocalise() {
        val moteur = MoteurJava()
        val chemin = "/app/Main.java"
        moteur.diagnose(chemin, sourceAvec(""))
        val sourceFrappe = sourceAvec(frappe)
        moteur.diagnose(chemin, sourceFrappe)
        // Caret juste après la virgule de « add(total, 2) ».
        val offset = sourceFrappe.indexOf("add(total, 2);") + "add(total,".length
        val temoin = temoin()
        val reponse = moteur.signatureHelp(chemin, sourceFrappe, offset)
        val attendu = temoin.signatureHelp(chemin, sourceFrappe, offset)
        assertNotNull(reponse)
        assertNotNull(attendu)
        assertEquals(attendu.parametreActif, reponse.parametreActif, "paramètre actif identique")
        assertEquals(attendu.signatures, reponse.signatures, "signatures identiques au témoin")
        assertEquals(1, reponse.parametreActif, "activeParameter = 1 (deuxième argument en saisie)")
        assertTrue(
            reponse.signatures[reponse.signatureActive].libelle.contains("add"),
            "le label de la signature active contient « add »",
        )
        assertTrue(moteur.fastResolveHits >= 1)
    }

    @Test
    fun signatureHelpHorsCorpsHoteRepli() {
        val moteur = MoteurJava()
        val chemin = "/app/Main.java"
        moteur.diagnose(chemin, sourceAvec(""))
        val sourceFrappe = sourceAvec(frappe)
        moteur.diagnose(chemin, sourceFrappe)
        // « add(total, 7) » vit dans le corps masqué autre().
        val avecSept = sourceFrappe.replace("String r = mot.toUpperCase();", "int r2 = add(total, 7);")
        val offset = avecSept.indexOf("add(total, 7);") + "add(total,".length
        val temoin = temoin()
        val reponse = moteur.signatureHelp(chemin, avecSept, offset)
        assertEquals(temoin.signatureHelp(chemin, avecSept, offset), reponse, "équivalence stricte hors corps hôte")
        assertEquals(0, moteur.fastResolveHits, "aucun service par l'arbre focalisé hors du corps hôte")
    }

    @Test
    fun apresDiagnoseCompletFeaturesParArbreReel() {
        val moteur = MoteurJava()
        val chemin = "/app/Main.java"
        // didChange + PREMIER diagnose (complet, jamais fast) de la source avec frappe.
        val sourceFrappe = sourceAvec(frappe)
        moteur.invalidateParseCache(chemin)
        moteur.diagnose(chemin, sourceFrappe)
        assertEquals(0, moteur.fastDiagnoseHits, "premier diagnose : complet")
        assertEquals(1, moteur.fullDiagnoseCount)

        val temoin = temoin()
        // Hover sur « somme » (corps édité) ET « mot.toUpperCase » (corps masqué),
        // plus signature help sur « add(total, ».
        val points = listOf(
            offsetDe(sourceFrappe, "somme"),
            offsetDe(sourceFrappe, "mot.toUpperCase"),
        )
        for (offset in points) {
            assertEquals(temoin.hoverAt(chemin, sourceFrappe, offset), moteur.hoverAt(chemin, sourceFrappe, offset), "hover identique au témoin")
        }
        val offsetSignature = sourceFrappe.indexOf("add(total, 2);") + "add(total,".length
        assertEquals(
            temoin.signatureHelp(chemin, sourceFrappe, offsetSignature),
            moteur.signatureHelp(chemin, sourceFrappe, offsetSignature),
            "signature help identique au témoin",
        )
        assertEquals(0, moteur.fastResolveHits)
        assertTrue(moteur.cachedResolveHits >= 3, "hover×2 + signature help servis par l'arbre réel : ${moteur.cachedResolveHits}")
    }

    @Test
    fun grosFichierArbreFocaliseContreSourceReduite() {
        // Fixture bigSrc : ≥ 2 000 caractères (bourre étendu — 70 lignes).
        val lignesBourre = (1..70).joinToString("\n") { "                int g$it = $it; // ligne de bourre numero $it" }
        val gros = base.replace("int b2 = 2;", "int b2 = 2;\n" + lignesBourre)
        assertTrue(gros.length >= 2_000, "précondition : bigSrc ≥ 2 000 caractères (${gros.length})")
        val marqueur = gros.indexOf('⌂')
        val grosFrappe = gros.substring(0, marqueur) + frappe + gros.substring(marqueur + 1)

        val moteur = MoteurJava()
        val chemin = "/app/Main.java"
        moteur.diagnose(chemin, gros)
        moteur.diagnose(chemin, grosFrappe)
        assertEquals(1, moteur.fastDiagnoseHits, "frappe confinée → diagnose rapide même sur gros fichier")

        val temoin = temoin()
        val offsetSomme = offsetDe(grosFrappe, "somme")
        assertEquals(
            temoin.hoverAt(chemin, grosFrappe, offsetSomme),
            moteur.hoverAt(chemin, grosFrappe, offsetSomme),
            "hover « somme » : arbre focalisé ISOMORPHE contre source RÉDUITE — équivalence",
        )
        val offsetSignature = grosFrappe.indexOf("add(total, 2);") + "add(total,".length
        assertEquals(
            temoin.signatureHelp(chemin, grosFrappe, offsetSignature),
            moteur.signatureHelp(chemin, grosFrappe, offsetSignature),
            "signature help : isomorphe contre réduit — équivalence",
        )
        assertTrue(moteur.fastResolveHits >= 2, "fastResolveHits ≥ 2 : ${moteur.fastResolveHits}")
    }

    @Test
    fun sessionMultiToursReponsesToujoursEquivalentes() {
        val moteur = MoteurJava()
        val chemin = "/app/Main.java"
        val temoin = temoin()

        // Tour 0 : diagnose initial.
        moteur.diagnose(chemin, sourceAvec(""))
        // Tour 1 : frappe « int somme = add(total, 2); ».
        val s1 = sourceAvec(frappe)
        moteur.invalidateParseCache(chemin)
        moteur.diagnose(chemin, s1)
        // Tour 2 : « add(total, 5) ».
        val s2 = sourceAvec(" int somme = add(total, 5);")
        moteur.invalidateParseCache(chemin)
        moteur.diagnose(chemin, s2)
        // Tour 3 : changement de TYPE + suppression de l'appel ; hover AVANT diagnose.
        val s3 = sourceAvec("long somme = 9L;")
        val offsetSomme = offsetDe(s3, "somme")
        val hoverAvant = moteur.hoverAt(chemin, s3, offsetSomme)
        assertEquals(temoin.hoverAt(chemin, s3, offsetSomme), hoverAvant, "hover avant diagnose = témoin")
        assertNotNull(hoverAvant)
        assertTrue(hoverAvant.contains("long"), "le tour 3 rend « long » : $hoverAvant")
        assertTrue(!hoverAvant.contains("int somme"), "l'arbre du tour 2 est évincé par la clé de contenu")
        moteur.invalidateParseCache(chemin)
        moteur.diagnose(chemin, s3)
        // Signature help au tour 3 : plus d'appel sous le caret — null des deux côtés.
        assertNull(moteur.signatureHelp(chemin, s3, offsetSomme), "plus d'appel sous le caret")
        assertNull(temoin.signatureHelp(chemin, s3, offsetSomme), "null des deux côtés")
        assertEquals(3, moteur.fastDiagnoseHits, "fastDiagnoseHits 1→2→3 : ${moteur.fastDiagnoseHits}")
    }

    /** Offset du point d'interrogation — recherche APRÈS la zone de frappe (usages, pas déclarations). */
    private fun offsetDe(source: String, morceau: String): Int {
        val zone = source.indexOf(frappe.substringBefore(';')).coerceAtLeast(0)
        val index = source.indexOf(morceau, zone)
        check(index >= 0) { "morceau introuvable après la frappe : $morceau" }
        return index + (morceau.length / 2)
    }

    /** Offset d'un morceau cherché après une amorce donnée (occurrences d'usage). */
    private fun offsetApres(source: String, amorce: String, morceau: String): Int {
        val zone = source.indexOf(amorce).coerceAtLeast(0)
        val index = source.indexOf(morceau, zone)
        check(index >= 0) { "morceau introuvable après « $amorce » : $morceau" }
        return index + (morceau.length / 2)
    }
}
