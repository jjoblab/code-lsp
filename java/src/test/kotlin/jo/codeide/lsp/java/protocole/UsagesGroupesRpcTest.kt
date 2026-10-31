package jo.codeide.lsp.java.protocole

import jo.codeide.lsp.java.diagnostics.Plage
import jo.codeide.lsp.java.navigation.Usage
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Le regroupement d'usages par fichier + le plafond honnête (vague V-E —
 * ADR 0028 (e)/R1) : la fonction pure [grouperUsages] porte les
 * invariants du RPC `textDocument/usagesGroupes` SANS serveur :
 *
 *  - un groupe par fichier porteur, ordre de PREMIÈRE APPARITION de la
 *    liste triée (chemin, offset) — l'ordre du moteur ;
 *  - le TOTAL est EXACT même quand le plafond écarte des usages (jamais
 *    de troncature silencieuse : `rendus`, `plafond` et `tronque`
 *    l'annoncent) ;
 *  - plafond ≤ 0 : réponse vide mais total exact ;
 *  - la troncature garde les PREMIERS usages de l'ordre trié.
 */
class UsagesGroupesRpcTest {

    /** Un usage de test (chemin, offset, déclaration ?). */
    private fun usage(chemin: String, offset: Int, declaration: Boolean = false) =
        Usage(chemin, Plage(offset, offset + 1), declaration)

    /** regroupement_ordre — groupes par première apparition, offsets croissants. */
    @Test
    fun regroupementParFichierOrdrePremiereApparition() {
        val usages = listOf(
            usage("/demo/A.java", 10),
            usage("/demo/A.java", 5, declaration = true),
            usage("/demo/B.java", 3),
            usage("/demo/A.java", 20),
        )
        val reponse = grouperUsages(usages, plafond = 100)

        assertEquals(4, reponse.total, "le total exact")
        assertEquals(4, reponse.rendus, "tout est rendu sous le plafond")
        assertFalse(reponse.tronque, "aucune troncature")
        assertEquals(listOf("/demo/A.java", "/demo/B.java"), reponse.groupes.map { it.path }, "ordre de première apparition")

        val groupeA = reponse.groupes[0]
        assertEquals(3, groupeA.usages.size, "les 3 occurrences de A.java")
        assertEquals(listOf(10, 5, 20), groupeA.usages.map { it.start }, "l'ordre des offsets de l'entrée est conservé")
        assertTrue(groupeA.usages[1].estDeclaration, "le drapeau déclaration est reporté")
        assertEquals(1, reponse.groupes[1].usages.size, "l'occurrence de B.java")
    }

    /** plafond_tronque — les PREMIERS de l'ordre trié sont rendus, le total reste exact. */
    @Test
    fun plafondTronqueEtAnnonce() {
        val usages = listOf(
            usage("/demo/A.java", 1, declaration = true),
            usage("/demo/A.java", 2),
            usage("/demo/A.java", 3),
            usage("/demo/B.java", 4),
            usage("/demo/B.java", 5),
        )
        val reponse = grouperUsages(usages, plafond = 3)

        assertEquals(5, reponse.total, "le TOTAL EXACT, jamais plafonné")
        assertEquals(3, reponse.rendus, "seuls les 3 premiers sont rendus")
        assertEquals(3, reponse.plafond, "le plafond appliqué est annoncé")
        assertTrue(reponse.tronque, "la troncature est SIGNALÉE (jamais silencieuse)")
        assertEquals(1, reponse.groupes.size, "seul A.java est rendu (premier de l'ordre trié)")
        assertEquals(listOf(1, 2, 3), reponse.groupes[0].usages.map { it.start }, "les premiers offsets de l'ordre")
    }

    /** plafond_nul — réponse vide mais total exact (le plafond 0 ne ment pas). */
    @Test
    fun plafondNulVideMaisTotalExact() {
        val usages = listOf(usage("/demo/A.java", 1), usage("/demo/B.java", 2))
        val reponse = grouperUsages(usages, plafond = 0)

        assertEquals(2, reponse.total, "le total reste exact")
        assertEquals(0, reponse.rendus, "rien n'est rendu")
        assertTrue(reponse.tronque, "la troncature est signalée")
        assertTrue(reponse.groupes.isEmpty(), "aucun groupe")
    }

    /** entrée_vide — la vue vide honnête (convention I9). */
    @Test
    fun entreeVideVueVideHonnête() {
        val reponse = grouperUsages(emptyList(), plafond = PLAFOND_USAGES_RENDUS)
        assertEquals(0, reponse.total)
        assertEquals(0, reponse.rendus)
        assertFalse(reponse.tronque)
        assertTrue(reponse.groupes.isEmpty())
        assertEquals(PLAFOND_USAGES_RENDUS, reponse.plafond, "le plafond constant du serveur est annoncé tel quel")
    }
}
