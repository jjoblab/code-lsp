package jo.codeide.lsp.java.actionscode

import jo.codeide.lsp.java.MoteurJava
import jo.codeide.lsp.java.diagnostics.Diagnostic
import jo.codeide.lsp.java.diagnostics.Plage
import jo.codeide.lsp.java.diagnostics.Severite
import jo.codeide.lsp.java.IndexNoms
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/**
 * Fiche 063 — CodeActionServiceTest : les quick-fixes à ÉDITS RÉELS
 * ([ServiceActionsCode]) — suppression d'import inutilisé (ligne entière),
 * insertion du point-virgule manquant, suppression prudente des
 * déclarations inutilisées, « Entourer avec try-catch » / « Déclarer
 * throws » sur exception non gérée, « Insérer un cast » vers le type
 * attendu, et « Importer X (fqn) » depuis l'index.
 *
 * Deux montages (fiche 063) :
 *  - **service TEXTE pur** (sans environnement — fixes sans index) : la
 *    note de réécriture de la fiche est appliquée — les fixes texte sont
 *    DÉTACHÉS de la dépendance SDK, le banc s'exécute partout ;
 *  - **service AVEC INDEX** : la réécriture requalifie le montage
 *    « jrt du JDK + android.jar » en **index factice PRÊT** (convention
 *    fiche 032 du même module : java.util.List côté plateforme,
 *    java.awt.List côté android.jar, java.lang.String côté java.lang) —
 *    les invariants du service (candidats, exclusions, insertion pure)
 *    sont indépendants de la plateforme ; le port contre le VRAI index
 *    disque relève de la couche d'intégration (:testing, ADR 0008).
 *
 * Diagnostics SYNTHÉTIQUES (montage du cahier) : plage + sévérité ERROR +
 * code « &lt;ID ecj&gt; » + arguments structurés — le dispatch ID-FIRST ne
 * lit JAMAIS le message.
 */
class Fiche063ServiceActionsCodeTest {

    private companion object {

        lateinit var SERVICE_TEXTE: ServiceActionsCode
        lateinit var SERVICE_INDEX: ServiceActionsCode

        /** Index factice PRÊT : plateforme (java.util), android.jar (java.awt), java.lang. */
        private class IndexPlateforme : IndexNoms {
            private val fqcnParNom = mapOf(
                "List" to listOf("java.util.List", "java.awt.List"),
                "String" to listOf("java.lang.String"),
            )

            override fun estPret(): Boolean = true

            override fun ajouterObservateurPret(observateur: () -> Unit) {
                observateur() // exécution immédiate (contrat des index factices).
            }

            override fun jarDe(fqcn: String): String? = null

            override fun nomsParPrefixe(prefixe: String, limite: Int): List<String> =
                fqcnParNom.keys.filter { it.startsWith(prefixe) }.take(limite)

            override fun fqnsForSimpleName(nom: String): List<String> = fqcnParNom[nom] ?: emptyList()

            override fun estPaquet(paquet: String): Boolean = false

            override fun sousPaquetsDirects(paquet: String): List<String> = emptyList()

            override fun typesDansPaquet(paquet: String): List<String> = emptyList()

            override fun membresStatiques(fqcn: String): List<String> = emptyList()
        }

        fun offsetDe(source: String, mot: String, occurrence: Int = 0): Int {
            var position = -1
            repeat(occurrence + 1) { position = source.indexOf(mot, position + 1) }
            return position
        }

        /** Un diagnostic synthétique du cahier : ERROR + code ID + plage + arguments structurés. */
        fun diagnostic(
            id: Long,
            arguments: List<String>,
            debut: Int,
            fin: Int,
            message: String = "synthétique",
        ): Diagnostic = Diagnostic(
            severite = Severite.ERREUR,
            message = message,
            plage = Plage(debut, fin),
            code = id.toString(),
            arguments = arguments,
        )

        @BeforeAll
        @JvmStatic
        fun avantTout() {
            SERVICE_TEXTE = ServiceActionsCode()
            SERVICE_INDEX = ServiceActionsCode(
                MoteurJava(emptyList(), emptyList(), null, "17", null, IndexPlateforme()),
            )
        }
    }

    // ---- 1. unusedImportDeletesWholeLine -------------------------------- //

    @Test
    fun `unusedImportDeletesWholeLine`() {
        val source = """
            package demo;

            import java.util.List;
            import java.util.ArrayList;

            class A { }
        """.trimIndent()
        val debut = offsetDe(source, "import java.util.List;")
        val diagnostic = diagnostic(268435844L, listOf("java.util.List"), debut, debut + 7)
        val actions = SERVICE_TEXTE.actions("A.java", source, diagnostic)
        val suppressions = actions.filter { it.kind == "quickfix.remove.import" }
        assertEquals(1, suppressions.size, "une action quickfix.remove.import")
        assertEquals(1, suppressions[0].editions.size, "exactement 1 édition (borne de la fiche)")
        val corrige = suppressions[0].apercu(source)
        assertTrue(!corrige.contains("import java.util.List;"), "la ligne d'import entière a disparu")
        assertTrue(corrige.contains("import java.util.ArrayList;"), "l'autre import survit")
    }

    // ---- 2. unusedImportNotOfferedOutsideImportLine --------------------- //

    @Test
    fun `unusedImportNotOfferedOutsideImportLine`() {
        val source = "class A { void m() { int List = 0; } }"
        val debut = offsetDe(source, "int List")
        val diagnostic = diagnostic(268435844L, listOf("List"), debut, debut + 8)
        val actions = SERVICE_TEXTE.actions("A.java", source, diagnostic)
        assertEquals(0, actions.count { it.kind == "quickfix.remove.import" }, "aucune suppression hors ligne d'import")
    }

    // ---- 3. missingSemiInsertedAtLineEnd -------------------------------- //

    @Test
    fun `missingSemiInsertedAtLineEnd`() {
        val source = "class A { void m() { int x = 5 } }"
        val debut = offsetDe(source, "5")
        val diagnostic = diagnostic(1610612960L, listOf("5"), debut, debut + 1)
        val actions = SERVICE_TEXTE.actions("A.java", source, diagnostic)
        val semicolons = actions.filter { it.kind == "quickfix.semicolon" }
        assertEquals(1, semicolons.size, "une action quickfix.semicolon")
        val corrige = semicolons[0].apercu(source)
        assertTrue(corrige.contains("int x = 5;"), "le « ; » se retrouve en fin d'instruction")
    }

    // ---- 4. unusedLocalDeclarationDeleted ------------------------------- //

    @Test
    fun `unusedLocalDeclarationDeleted`() {
        val source = """
            class A {
                void m() {
                    int unused = 5;
                    int suivante = 2;
                }
            }
        """.trimIndent()
        val debut = offsetDe(source, "unused")
        val diagnostic = diagnostic(536870973L, listOf("unused"), debut, debut + 6)
        val actions = SERVICE_TEXTE.actions("A.java", source, diagnostic)
        val suppressions = actions.filter { it.kind == "quickfix.remove.unused" }
        assertEquals(1, suppressions.size, "une action quickfix.remove.unused")
        val corrige = suppressions[0].apercu(source)
        assertTrue(!corrige.contains("unused"), "la déclaration disparaît")
        assertTrue(corrige.contains("int suivante = 2;"), "l'instruction suivante survit")
    }

    // ---- 5. multiFragmentDeclarationIsLeftAlone ------------------------- //

    @Test
    fun `multiFragmentDeclarationIsLeftAlone`() {
        val source = "class A { void m() { int a = 1, b = 2; System.out.println(a + b); } }"
        val debut = offsetDe(source, "b = 2")
        val diagnostic = diagnostic(536870973L, listOf("b"), debut, debut + 1)
        val actions = SERVICE_TEXTE.actions("A.java", source, diagnostic)
        assertEquals(0, actions.count { it.kind == "quickfix.remove.unused" }, "multi-fragments intact (variable sœur)")
    }

    // ---- 6. parameterIsLeftAlone ----------------------------------------- //

    @Test
    fun `parameterIsLeftAlone`() {
        val source = "class A { void m(int param) { } }"
        val debut = offsetDe(source, "param")
        val diagnostic = diagnostic(536870974L, listOf("param"), debut, debut + 5)
        val actions = SERVICE_TEXTE.actions("A.java", source, diagnostic)
        assertEquals(0, actions.count { it.kind == "quickfix.remove.unused" }, "un paramètre ne se supprime pas")
    }

    // ---- 7. tryCatchWrapsStatementWithExactException --------------------- //

    @Test
    fun `tryCatchWrapsStatementWithExactException`() {
        val source = """
            package demo;

            import java.util.List;

            class A {
                void m() throws Exception {
                    new java.io.FileInputStream(f);
                }
            }
        """.trimIndent()
        val debut = offsetDe(source, "new java.io.FileInputStream")
        val diagnostic = diagnostic(
            16777384L, listOf("java.io.FileNotFoundException"), debut, debut + 30,
        )
        val actions = SERVICE_TEXTE.actions("A.java", source, diagnostic)
        val entourages = actions.filter { it.kind == "quickfix.surround.trycatch" }
        assertEquals(1, entourages.size, "une action quickfix.surround.trycatch")
        val corrige = entourages[0].apercu(source)
        assertTrue(corrige.contains("try {"), "un bloc try { ouvre")
        assertTrue(
            corrige.contains("} catch (FileNotFoundException e) {"),
            "le catch cible le type EXACT de l'argument structuré",
        )
        assertTrue(corrige.contains("import java.io.FileNotFoundException;"), "l'import du type hors java.lang est AJOUTÉ")
        assertTrue(corrige.contains("new java.io.FileInputStream(f);"), "l'instruction initiale reste dans le try")
    }

    // ---- 8. throwsCreatedAfterParameterList ------------------------------ //

    @Test
    fun `throwsCreatedAfterParameterList`() {
        val source = "class A { void m(int a) { Thread.sleep(10); } }"
        val debut = offsetDe(source, "Thread.sleep")
        val diagnostic = diagnostic(16777384L, listOf("java.lang.InterruptedException"), debut, debut + 15)
        val actions = SERVICE_TEXTE.actions("A.java", source, diagnostic)
        val throws = actions.filter { it.kind == "quickfix.declare.throws" }
        assertEquals(1, throws.size, "une action quickfix.declare.throws")
        val corrige = throws[0].apercu(source)
        assertTrue(corrige.contains("void m(int a) throws InterruptedException"), "la clause suit directement la liste de paramètres")
    }

    // ---- 9. throwsAppendedToExistingClause ------------------------------- //

    @Test
    fun `throwsAppendedToExistingClause`() {
        val source = "class A { void m() throws java.io.IOException { Thread.sleep(10); } }"
        val debut = offsetDe(source, "Thread.sleep")
        val diagnostic = diagnostic(16777384L, listOf("java.lang.InterruptedException"), debut, debut + 15)
        val actions = SERVICE_TEXTE.actions("A.java", source, diagnostic)
        val throws = actions.filter { it.kind == "quickfix.declare.throws" }
        assertEquals(1, throws.size, "une action quickfix.declare.throws")
        val corrige = throws[0].apercu(source)
        assertTrue(
            corrige.contains("throws java.io.IOException, InterruptedException"),
            "la nouvelle exception s'AJOUTE à la clause existante (pas de remplacement)",
        )
    }

    // ---- 10. throwsFindsMethodInsideControlBlocks ------------------------ //

    @Test
    fun `throwsFindsMethodInsideControlBlocks`() {
        val source = "class A { void m() { if (true) { Thread.sleep(10); } } }"
        val debut = offsetDe(source, "Thread.sleep")
        val diagnostic = diagnostic(16777384L, listOf("java.lang.InterruptedException"), debut, debut + 15)
        val actions = SERVICE_TEXTE.actions("A.java", source, diagnostic)
        val throws = actions.filter { it.kind == "quickfix.declare.throws" }
        assertEquals(1, throws.size, "une action quickfix.declare.throws")
        val corrige = throws[0].apercu(source)
        assertTrue(corrige.contains("void m() throws InterruptedException"), "le throws atterrit sur void m()")
        assertTrue(!corrige.contains("if (true) throws"), "jamais sur le if")
    }

    // ---- 11. castUsesExpectedType ---------------------------------------- //

    @Test
    fun `castUsesExpectedType`() {
        val source = "class A { void m() { Object o = null; String s = o; } }"
        val debut = offsetDe(source, "s = o") + 4
        val diagnostic = diagnostic(
            16777233L, listOf("java.lang.Object", "java.lang.String"), debut, debut + 1,
        )
        val actions = SERVICE_TEXTE.actions("A.java", source, diagnostic)
        val casts = actions.filter { it.kind == "quickfix.cast" }
        assertEquals(1, casts.size, "une action quickfix.cast")
        val corrige = casts[0].apercu(source)
        assertTrue(corrige.contains("(String) o"), "le cast cible le type ATTENDU (deuxième argument)")
    }

    // ---- 12. unresolvedTypeOffersImportCandidates ------------------------ //

    @Test
    fun `unresolvedTypeOffersImportCandidates`() {
        val source = "class A { void m() { List list = null; } }"
        val debut = offsetDe(source, "List list")
        val diagnostic = diagnostic(16777218L, listOf("List"), debut, debut + 4)
        val actions = SERVICE_INDEX.actions("A.java", source, diagnostic)
        val imports = actions.filter { it.kind == "quickfix.import.fqn" }
        assertTrue(imports.isNotEmpty(), "au moins 1 action quickfix.import.fqn")
        assertTrue(
            imports.any { it.titre.contains("java.util.List") },
            "au moins un titre contient java.util.List",
        )
        for (action in imports) {
            assertEquals(1, action.editions.size, "1 édition par action (borne de la fiche)")
            val edition = action.editions[0]
            assertEquals(0, edition.longueur, "insertion PURE (début = fin)")
            assertTrue(edition.texte.startsWith("import"), "le texte commence par « import »")
            assertTrue(edition.texte.endsWith(";\n"), "le texte finit par « ;\\n »")
        }
    }

    // ---- 13. unresolvedLowercaseNameGetsNoImportFix ---------------------- //

    @Test
    fun `unresolvedLowercaseNameGetsNoImportFix`() {
        val source = "class A { void m() { foo(); } }"
        val debut = offsetDe(source, "foo")
        val diagnostic = diagnostic(67108964L, listOf("A", "foo", ""), debut, debut + 3)
        val actions = SERVICE_INDEX.actions("A.java", source, diagnostic)
        assertEquals(0, actions.count { it.kind == "quickfix.import.fqn" }, "seuls les TYPES reçoivent des candidats d'import")
    }

    // ---- 14. importFixSkipsAlreadyImportedAndJavaLang -------------------- //

    @Test
    fun `importFixSkipsAlreadyImportedAndJavaLang`() {
        val source = """
            package demo;

            import java.util.List;

            class A { void m() { List list = null; String s = "x"; } }
        """.trimIndent()

        // « List » : déjà importé — java.util.List n'est PAS reproposé ;
        // l'hOMONYME du classpath (java.awt.List) reste légitimement proposable.
        val debutList = offsetDe(source, "List list")
        val diagnosticList = diagnostic(16777218L, listOf("List"), debutList, debutList + 4)
        val actionsList = SERVICE_INDEX.actions("A.java", source, diagnosticList)
        val importsList = actionsList.filter { it.kind == "quickfix.import.fqn" }
        assertTrue(
            importsList.none { it.titre.contains("java.util.List") },
            "déjà importé : aucun candidat java.util.List",
        )
        assertTrue(
            importsList.any { it.titre.contains("java.awt.List") },
            "l'homonyme du classpath reste proposable",
        )

        // « String » : java.lang n'est JAMAIS proposé.
        val debutString = offsetDe(source, "String s")
        val diagnosticString = diagnostic(16777218L, listOf("String"), debutString, debutString + 6)
        val actionsString = SERVICE_INDEX.actions("A.java", source, diagnosticString)
        assertEquals(0, actionsString.count { it.kind == "quickfix.import.fqn" }, "java.lang n'est jamais proposé")
    }

    // ---- 15. noActionWithoutRealEdits ------------------------------------ //

    @Test
    fun `noActionWithoutRealEdits`() {
        val source = "class A { void m() { foo(); } }"
        val debut = offsetDe(source, "foo")
        val diagnostic = diagnostic(67108964L, listOf("A", "foo", ""), debut, debut + 3)
        val actions = SERVICE_TEXTE.actions("A.java", source, diagnostic)
        assertTrue(actions.isNotEmpty(), "au moins une action (création de méthode)")
        for (action in actions) {
            assertTrue(
                action.editions.isNotEmpty(),
                "contrat : aucune action « coquille vide » (${action.kind})",
            )
        }
    }
}
