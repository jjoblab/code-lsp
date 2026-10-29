package jo.codeide.lsp.java.renommage

import jo.codeide.lsp.java.MoteurJava
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * Fiche 061 — RenameHonneteTest : le lot S0-b « rename honnête » (I8) —
 * le miroir moteur de `textDocument/prepareRename` et
 * `textDocument/rename` :
 *
 *  - `prepareRename` rend le placeholder EXACT et la plage EXACTE du
 *    jeton (caret au milieu du nom compris) ;
 *  - `rename` sans cible REJETTE par l'erreur TYPOÉE
 *    [RenommageSansCible] (RequestFailed -32803, message FR, `data`
 *    structuré « renommage.sansCible ») — jamais d'édition vide
 *    silencieuse ;
 *  - le renommage nominal sous les DEUX formes négociées du
 *    `WorkspaceEdit` — `changes` (héritée) quand le client n'annonce
 *    pas `workspace.workspaceEdit.documentChanges`,
 *    `documentChanges` versionnée quand il l'annonce.
 *
 * Fixture du cahier : classe `Panier` (champ `total` affecté/retourné,
 * méthodes `ajouter`/`somme`).
 */
class Fiche061RenommageHonneteTest {

    private companion object {

        lateinit var MOTEUR: MoteurJava
        lateinit var PROJET: Path
        lateinit var URI_PANIER: String

        /** Le serveur LEGACY : le client cel-lsp n'annonce pas documentChanges. */
        lateinit var SERVEUR_LEGACY: ServiceRenommage

        /** Le second serveur : caps `documentChanges` négociées. */
        lateinit var SERVEUR_NEGOCIE: ServiceRenommage

        val SOURCE_PANIER = """
            package demo;

            public class Panier {
                int total;

                public void ajouter(int montant) {
                    total = total + montant;
                }

                public int somme() {
                    return total;
                }
            }
        """.trimIndent()

        fun offsetDe(source: String, mot: String, occurrence: Int = 0): Int {
            var position = -1
            repeat(occurrence + 1) { position = source.indexOf(mot, position + 1) }
            return position
        }

        @BeforeAll
        @JvmStatic
        fun avantTout() {
            PROJET = Files.createTempDirectory("renommage-061")
            val dossier = Files.createDirectories(PROJET.resolve("demo"))
            dossier.resolve("Panier.java").writeText(SOURCE_PANIER)
            URI_PANIER = "file://" + dossier.resolve("Panier.java").toAbsolutePath()
            MOTEUR = MoteurJava(racines = listOf(PROJET), jars = emptyList(), compliance = "17")
            assumeTrue(MOTEUR.isLikelyUsable(), "image jrt indisponible — renommage ignoré")
            SERVEUR_LEGACY = ServiceRenommage(MOTEUR, CapsEspaceDeTravailClient())
            SERVEUR_NEGOCIE = ServiceRenommage(MOTEUR, CapsEspaceDeTravailClient(documentChanges = true))
            // didOpen de Panier.java (version 0) sur les deux serveurs.
            SERVEUR_LEGACY.ouvrirDocument(URI_PANIER, 0)
            SERVEUR_NEGOCIE.ouvrirDocument(URI_PANIER, 0)
        }

        @AfterAll
        @JvmStatic
        fun apresTout() {
            MOTEUR.dispose()
        }
    }

    /** prepareRenamePlaceholderExactCaretMilieuDuNom — placeholder et plage exacts du jeton. */
    @Test
    fun prepareRenamePlaceholderExactCaretMilieuDuNom() {
        val source = SOURCE_PANIER
        val nom = offsetDe(source, "ajouter")
        val caret = nom + 3 // AU MILIEU du nom
        val preparation = SERVEUR_LEGACY.preparerRenommage("demo/Panier.java", source, caret)
        assertNotNull(preparation, "la préparation est servie en forme { range, placeholder }")
        assertEquals("ajouter", preparation.placeholder, "placeholder EXACT « ajouter »")
        assertEquals(nom, preparation.plage.debutOffset, "début de plage = DÉBUT du nom")
        assertEquals(nom + "ajouter".length, preparation.plage.finOffset, "fin de plage = FIN du nom")
        assertEquals("ajouter", source.substring(preparation.plage.debutOffset, preparation.plage.finOffset))
    }

    /** prepareRenameHorsNomRendNull — hors jeton renommable : null. */
    @Test
    fun prepareRenameHorsNomRendNull() {
        val source = SOURCE_PANIER
        val caret = offsetDe(source, "package demo;")
        assertNull(
            SERVEUR_LEGACY.preparerRenommage("demo/Panier.java", source, caret),
            "un mot-clé n'est pas un jeton renommable",
        )
    }

    /** renameSansCibleErreurTypeeRequestFailed — sans cible : erreur typée (I8). */
    @Test
    fun renameSansCibleErreurTypeeRequestFailed() {
        val source = SOURCE_PANIER
        val caret = offsetDe(source, "public class Panier")
        val erreur = assertFailsWith<RenommageSansCible> {
            SERVEUR_LEGACY.renommage(URI_PANIER, "demo/Panier.java", source, caret, "autre")
        }
        assertEquals(RenommageSansCible.CODE_REQUEST_FAILED, erreur.codeLsp, "code d'erreur exact -32803")
        assertTrue(
            erreur.message!!.contains("Aucun symbole renommable"),
            "message FR contenant « Aucun symbole renommable » : ${erreur.message}",
        )
        assertNotNull(erreur.donnees, "data structuré non-null")
        assertEquals("renommage.sansCible", erreur.donnees.code, "code machine exact")
        assertNotNull(erreur.donnees.position, "la position est transportée")
    }

    /** renommageNominalFormeChangesQuandClientNeDeclarePas — forme changes (caps legacy). */
    @Test
    fun renommageNominalFormeChangesQuandClientNeDeclarePas() {
        val source = SOURCE_PANIER
        val caret = offsetDe(source, "int total;") + "int ".length
        val edition = SERVEUR_LEGACY.renommage(URI_PANIER, "demo/Panier.java", source, caret, "solde")
        assertNotNull(edition.changes, "changes non-null (forme héritée)")
        assertNull(edition.documentChanges, "documentChanges NULL (les deux formes ne coexistent jamais)")
        val editions = edition.changes!![URI_PANIER]
        assertNotNull(editions, "l'URI ouvert y figure")
        assertEquals(4, editions.size, "exactement 4 éditions (déclaration + 2 usages + return)")
        for (e in editions) {
            assertEquals("total", source.substring(e.offset, e.offset + e.longueur), "plage = exactement « total »")
            assertEquals("solde", e.texte, "remplacement par « solde »")
        }
    }

    /** renommageNominalFormeDocumentChangesVersionneeQuandNegociee — forme versionnée négociée. */
    @Test
    fun renommageNominalFormeDocumentChangesVersionneeQuandNegociee() {
        val source = SOURCE_PANIER
        val caret = offsetDe(source, "public int somme()") + "public int ".length
        val edition = SERVEUR_NEGOCIE.renommage(URI_PANIER, "demo/Panier.java", source, caret, "cumul")
        assertNotNull(edition.documentChanges, "documentChanges non-null (forme négociée)")
        assertTrue(edition.changes == null || edition.changes.isEmpty(), "jamais les deux formes")
        assertEquals(1, edition.documentChanges.size, "exactement 1 TextDocumentEdit")
        val entree = edition.documentChanges.first()
        assertEquals(URI_PANIER, entree.uri, "pour l'URI ouvert")
        assertEquals(0L, entree.version, "la version COURANTE du document (0 — posée par le didOpen)")
        assertEquals(1, entree.editions.size, "exactement 1 édition (la déclaration seule de « somme »)")
        val e = entree.editions.first()
        assertEquals("somme", source.substring(e.offset, e.offset + e.longueur))
        assertEquals("cumul", e.texte)
    }
}
