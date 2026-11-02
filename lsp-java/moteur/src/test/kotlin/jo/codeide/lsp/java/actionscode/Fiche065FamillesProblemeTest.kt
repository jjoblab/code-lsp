package jo.codeide.lsp.java.actionscode

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

/**
 * Fiche 065 — JavaProblemFamilyTest : le mapping ID ecj → famille
 * sémantique ([FamilleProbleme]) verrouille le dispatch **ID-FIRST** des
 * correcteurs — chaque ID ecj testé mappe vers la même famille,
 * **indépendamment de la Locale** de l'utilisateur (condition pour que les
 * quick-fixes soient fiables sur une VM française/espagnole/etc.).
 *
 * Test unitaire PUR (fiche 065 : « aucun moteur, aucun android.jar, toujours
 * exécutable ») : couvre [FamilleProbleme.fromId] et
 * [FamilleProbleme.fromCode] (forme « ecj:&lt;ID&gt; » du `Diagnostic.code()`
 * LSP, ID numérique brut, entrées invalides).
 *
 * Les IDs numériques sont les constantes d'`IProblem` du jar ecj 3.40.0 —
 * identifiants historiques des tests vendored, extraits par lecture du
 * format de classe (aucune dépendance moteur).
 */
class Fiche065FamillesProblemeTest {

    /** UndefinedType, UndefinedName, UnresolvedVariable, UndefinedField, ImportNotFound. */
    private val IDS_NON_RESOLUS = listOf(16777218L, 570425394L, 33554515L, 33554502L, 268435846L)

    /** UnusedImport, LocalVariableIsNeverUsed, ArgumentIsNeverUsed, UnusedPrivateMethod, UnusedPrivateField, UnusedPrivateType. */
    private val IDS_INUTILISES = listOf(268435844L, 536870973L, 536870974L, 603979894L, 570425421L, 553648135L)

    /** UnhandledException + les trois variantes implicites/auto-close. */
    private val IDS_NON_GEREES = listOf(16777384L, 16777362L, 16777363L, 16778098L)

    /** TypeMismatch, ReturnTypeMismatch, IncompatibleReturnType. */
    private val IDS_MISMATCH = listOf(16777233L, 16777235L, 67109268L)

    @Test
    fun `undefinedTypesMapToUnresolvedReference`() {
        for (id in IDS_NON_RESOLUS) {
            assertEquals(
                FamilleProbleme.UNRESOLVED_REFERENCE,
                FamilleProbleme.fromId(id),
                "l'ID $id doit mapper vers UNRESOLVED_REFERENCE",
            )
        }
        assertEquals(5, IDS_NON_RESOLUS.size, "5 IDs couverts (borne de la fiche)")
    }

    @Test
    fun `undefinedMethodMapsToItsOwnFamily`() {
        assertEquals(
            FamilleProbleme.UNDEFINED_METHOD,
            FamilleProbleme.fromId(67108964L),
            "UndefinedMethod détaché de UNRESOLVED_REFERENCE — c'est ce qui aiguille vers la création de méthode",
        )
    }

    @Test
    fun `unusedWarningMapsToUnusedFamily`() {
        assertEquals(FamilleProbleme.UNUSED_IMPORT, FamilleProbleme.fromId(268435844L), "UnusedImport → famille propre")
        for (id in IDS_INUTILISES.drop(1)) {
            assertEquals(
                FamilleProbleme.UNUSED_DECLARATION,
                FamilleProbleme.fromId(id),
                "l'ID $id doit mapper vers UNUSED_DECLARATION",
            )
        }
        assertEquals(6, IDS_INUTILISES.size, "6 IDs couverts ; 1 + 5 familles (borne de la fiche)")
    }

    @Test
    fun `unhandledExceptionMapsToUncaughtExceptionFamily`() {
        for (id in IDS_NON_GEREES) {
            assertEquals(
                FamilleProbleme.UNCAUGHT_EXCEPTION,
                FamilleProbleme.fromId(id),
                "l'ID $id doit mapper vers UNCAUGHT_EXCEPTION",
            )
        }
        assertEquals(4, IDS_NON_GEREES.size, "4 IDs couverts (borne de la fiche)")
    }

    @Test
    fun `typeMismatchMapsToTypeMismatchFamily`() {
        for (id in IDS_MISMATCH) {
            assertEquals(
                FamilleProbleme.TYPE_MISMATCH,
                FamilleProbleme.fromId(id),
                "l'ID $id doit mapper vers TYPE_MISMATCH",
            )
        }
        assertEquals(3, IDS_MISMATCH.size, "3 IDs couverts (borne de la fiche)")
    }

    @Test
    fun `missingSemicolonMapsToMissingSemi`() {
        assertEquals(FamilleProbleme.MISSING_SEMI, FamilleProbleme.fromId(1610612960L), "MissingSemiColon → MISSING_SEMI")
        // Découverte N6 : l'ID RÉELLEMENT émis par ecj pour un point-virgule
        // manquant (« insert \";\" to complete BlockStatements »).
        assertEquals(
            FamilleProbleme.MISSING_SEMI,
            FamilleProbleme.fromId(1610612976L),
            "ParsingErrorInsertToComplete (ID émis) → MISSING_SEMI",
        )
    }

    @Test
    fun `unmappedIdsReturnUnknown`() {
        for (id in listOf(1610612940L, 1610612966L)) {
            assertEquals(
                FamilleProbleme.UNKNOWN,
                FamilleProbleme.fromId(id),
                "l'ID $id (parsing) reste UNKNOWN — aucun quick-fix ID-FIRST",
            )
        }
    }

    @Test
    fun `fromCodeParsesPrefixedCode`() {
        // Forme préfixée « ecj:<ID> » — le format du Diagnostic.code() LSP.
        assertEquals(
            FamilleProbleme.TYPE_MISMATCH,
            FamilleProbleme.fromCode("ecj:16777233"),
            "forme préfixée → TYPE_MISMATCH",
        )
        // ID numérique brut — le format du Diagnostic.code moteur.
        assertEquals(
            FamilleProbleme.TYPE_MISMATCH,
            FamilleProbleme.fromCode("16777233"),
            "ID brut → TYPE_MISMATCH",
        )
        // Entrées invalides : JAMAIS d'exception.
        for (code in listOf(null, "ecj:PasUnNombre", "garbage", "")) {
            assertEquals(
                FamilleProbleme.UNKNOWN,
                FamilleProbleme.fromCode(code),
                "le code « $code » doit rendre UNKNOWN sans lever",
            )
        }
    }
}
