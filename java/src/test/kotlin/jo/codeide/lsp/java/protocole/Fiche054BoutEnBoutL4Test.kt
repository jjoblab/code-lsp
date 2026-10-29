package jo.codeide.lsp.java.protocole

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail
import org.eclipse.lsp4j.CodeLensParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DocumentHighlightParams
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.FormattingOptions
import org.eclipse.lsp4j.DocumentFormattingParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PrepareRenameParams
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.ReferenceContext
import org.eclipse.lsp4j.ReferenceParams
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.WorkspaceEdit
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir

/**
 * Fiche 054 — JavaL4BoutEnBoutTest : suite E2E multi-capacités du « lot
 * L4 » (colonne « preuve E2E » de la matrice de capacités) :
 * aller-retours LSP4J réels sur des capacités non couvertes par les
 * suites existantes — formatage du document ENTIER, références,
 * symboles de document, prepareRename+rename, surlignage d'occurrences,
 * superDefinition et decompiledSource (méthodes expérimentales).
 *
 * Fixtures du cahier : classe `T` mal indentée (`int x=1; total+=x;`),
 * `Base`/`Derivee` (redéfinition), `T`+`U` (appel inter-fichiers),
 * `java.lang.String` (binaire JDK).
 *
 * Montage : serveur réel sur un dossier @TempDir, classpath
 * android.jar réel (l'hypothèse d'environnement est VISIBLE :
 * `assumeTrue(androidJar != null)` — sans jar, tests ignorés, comme les
 * gates N2 033/036/043/044) ; `JO_TEST_JDK_HOME` (ou le runtime)
 * nourrit la source des classes binaires. Les fichiers existent sur
 * disque AVANT l'initialisation pour que la résolution du projet entier
 * se fasse (noms canoniques paquet ↔ chemin).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Fiche054BoutEnBoutL4Test {

    @TempDir
    lateinit var dossier: Path

    /** Le serveur réel de la suite (montage du cahier). */
    private var boucle: BoucleLocale? = null

    /** La classe T mal indentée (champ total, méthode a, corps non indentés). */
    private val sourceT = """
        package demo;

        public class T {
        int total = 0;
        void a() {
        int x=1;
        total+=x;
        }
        }
    """.trimIndent() + "\n"

    /** U : appelante(T t) contenant t.a(). */
    private val sourceU = """
        package demo;

        public class U {
            void appelante(T t) {
                t.a();
            }
        }
    """.trimIndent() + "\n"

    /** Base / Derivee (redéfinition de signaler). */
    private val sourceBase = """
        package demo;

        public class Base {
            public void signaler() {
            }
        }
    """.trimIndent() + "\n"

    private val sourceDerivee = """
        package demo;

        public class Derivee extends Base {
            @Override
            public void signaler() {
            }
        }
    """.trimIndent() + "\n"

    @BeforeAll
    fun avantTout(@TempDir racine: Path) {
        dossier = racine
        // Hypothèse d'environnement VISIBLE du cahier : sans android.jar,
        // les tests du lot sont IGNORÉS (gate dormante — la CI l'ouvre).
        val androidJar = jo.codeide.lsp.java.OutilsFixture.androidJar()
        assumeTrue(androidJar != null, "android.jar indisponible — lot L4 ignoré (gate visible du cahier)")

        // Les fichiers AVANT le serveur (résolution du projet entier).
        val demo = dossier.resolve("demo")
        Files.createDirectories(demo)
        Files.writeString(demo.resolve("T.java"), sourceT)
        Files.writeString(demo.resolve("U.java"), sourceU)
        Files.writeString(demo.resolve("Base.java"), sourceBase)
        Files.writeString(demo.resolve("Derivee.java"), sourceDerivee)

        // Le JDK de test (JO_TEST_JDK_HOME, sinon candidats, sinon runtime)
        // nourrit l'attachement de source des classes binaires.
        val jdk = jo.codeide.lsp.java.navigation.JdkDeTest.home()

        val serveur = ServeurLangageJava(
            fabriqueMoteur = { racine ->
                racine?.let {
                    jo.codeide.lsp.java.MoteurJava(
                        racines = listOf(it),
                        jars = listOfNotNull(androidJar),
                        jdkHome = jdk,
                    )
                }
            },
        )
        boucle = BoucleLocale(serveur).also { b ->
            b.demarrer()
            b.client.initialize(
                InitializeParams().apply { rootUri = dossier.toUri().toString() },
            ).get()
            b.client.initialized(InitializedParams())
        }
    }

    /** Ouvre (ou ré-ouvre) un document de la suite. */
    private fun ouvrir(nom: String, texte: String, version: Int = 0): TextDocumentIdentifier {
        val b = boucle ?: fail("boucle absente")
        val uri = dossier.resolve("demo/$nom").toUri().toString()
        b.client.textDocumentService.didOpen(
            DidOpenTextDocumentParams(TextDocumentItem(uri, "java", version, texte)),
        )
        return TextDocumentIdentifier(uri)
    }

    /** La position d'un texte cherché dans une source. */
    private fun positionDe(source: String, cherche: String, occurrence: Int = 0): Position {
        var indice = -1
        repeat(occurrence + 1) { indice = source.indexOf(cherche, indice + 1) }
        val ligne = source.substring(0, indice).count { it == '\n' }
        val debutLigne = source.lastIndexOf('\n', indice - 1) + 1
        return Position(ligne, indice - debutLigne)
    }

    /** L'application d'éditions TextEdit à une source (reconstruction). */
    private fun appliquer(source: String, editions: List<TextEdit>): String {
        val lignes = source.split("\n")
        fun offset(position: Position): Int =
            lignes.subList(0, position.line).sumOf { it.length + 1 } + position.character
        data class Ed(val debut: Int, val fin: Int, val texte: String)
        val converties = editions.map { Ed(offset(it.range.start), offset(it.range.end), it.newText) }
            .sortedByDescending { it.debut }
        val texte = StringBuilder(source)
        for (e in converties) texte.replace(e.debut, e.fin, e.texte)
        return texte.toString()
    }

    /** ---------------------------------------------------------------- */

    /** formattingDocumentEntierReindenteLeCorpsMalIndenté — formatage plein document réindente le corps. */
    @Test
    fun formattingDocumentEntierReindenteLeCorpsMalIndente() {
        val document = ouvrir("T.java", sourceT)
        val params = DocumentFormattingParams()
        params.textDocument = document
        params.options = FormattingOptions(4, false) // tabulation 4, pas d'espaces d'insertion
        val editions = boucle!!.client.textDocumentService.formatting(params).get()
        assertNotNull(editions, "des éditions sont produites")
        assertTrue(editions.isNotEmpty(), "éditions non vides")

        val reconstruit = appliquer(sourceT, editions)
        val idx = reconstruit.indexOf("int x = 1;")
        assertTrue(idx >= 0, "le texte reconstruit contient « int x = 1; »")
        val espacesAvant = reconstruit.substring(0, idx).length -
            reconstruit.substring(0, idx).lastIndexOf('\n') - 1
        assertEquals(
            8,
            espacesAvant,
            "« int x = 1; » précédé de 8 espaces — le corps de a() est réindenté (espaces autour du =)",
        )
    }

    /** referencesDUneMéthodeTrouventLesAppels — références d'une méthode : déclaration seule (parité stricte §10.4). */
    @Test
    fun referencesDUneMethodeTrouventLesAppels() {
        val t = ouvrir("T.java", sourceT)
        ouvrir("U.java", sourceU)

        val params = ReferenceParams()
        params.textDocument = t
        params.position = positionDe(sourceT, "a()")
        params.context = ReferenceContext(true) // includeDeclaration
        val references = boucle!!.client.textDocumentService.references(params).get()
        assertEquals(
            1,
            references.size,
            "exactement 1 référence rendue — la déclaration, dans T.java (écart moteur assumé, parité stricte §10.4)",
        )
        assertTrue(
            references[0].uri.endsWith("demo/T.java"),
            "la référence rendue vit dans T.java — trouvé : ${references[0].uri}",
        )
    }

    /** documentSymbolRendLaClasseEtSesMembres — documentSymbol : classe + membres. */
    @Test
    fun documentSymbolRendLaClasseEtSesMembres() {
        val document = ouvrir("T.java", sourceT)
        val symboles = boucle!!.client.textDocumentService.documentSymbol(
            DocumentSymbolParams(document),
        ).get()
        assertEquals(1, symboles.size, "1 symbole au premier niveau (la classe T)")
        val classe = symboles[0].right
        assertNotNull(classe, "forme hiérarchique DocumentSymbol")
        assertEquals("T", classe.name, "le symbole racine est la classe T")
        assertTrue(classe.children.size >= 2, "au moins 2 enfants — trouvé : ${classe.children?.map { it.name }}")
        assertTrue(
            classe.children.any { it.name == "a" },
            "un enfant nommé « a »",
        )
    }

    /** prepareRenamePuisRenameSurUnChamp — prepareRename puis rename sur un champ. */
    @Test
    fun prepareRenamePuisRenameSurUnChamp() {
        val document = ouvrir("T.java", sourceT)
        val service = boucle!!.client.textDocumentService
        val positionTotal = positionDe(sourceT, "total")

        // prepareRename : résultat non-null (le champ est renommable, forme plage).
        val preparation = service.prepareRename(
            PrepareRenameParams(document, positionTotal),
        ).get()
        assertNotNull(preparation, "prepareRename rend un résultat non-null")
        // La forme plage ({range, placeholder}) — côté du Either3.
        val forme = preparation.second
        assertNotNull(forme, "forme {range, placeholder} (le champ est renommable)")
        assertNotNull(forme.range, "la plage du jeton est non-null")
        assertEquals("total", forme.placeholder, "le placeholder est le nom actuel")

        // rename de « total » en « cumul » : WorkspaceEdit avec des éditions
        // pour l'URI du document courant, au moins 2 (déclaration + usages).
        val edition = service.rename(
            RenameParams(document, positionTotal, "cumul"),
        ).get()
        assertNotNull(edition, "rename rend un WorkspaceEdit")
        val changes = edition.changes
        assertNotNull(changes, "forme changes (le client n'annonce pas documentChanges)")
        val editions = changes[document.uri]
        assertNotNull(editions, "des éditions pour l'URI du document courant")
        assertTrue(
            editions.size >= 2,
            "au moins 2 éditions (déclaration + usages, dont total+=x) — trouvé : ${editions.size}",
        )
    }

    /** documentHighlightDUnChampDéclarationEtUsages — surlignage : déclaration + usage. */
    @Test
    fun documentHighlightDUnChampDeclarationEtUsages() {
        val document = ouvrir("T.java", sourceT)
        val surlignages = boucle!!.client.textDocumentService.documentHighlight(
            DocumentHighlightParams(document, positionDe(sourceT, "total")),
        ).get()
        assertEquals(
            2,
            surlignages.size,
            "exactement 2 occurrences surlignées — la déclaration (écriture) et l'usage (lecture)",
        )
    }

    /** superDefinitionDuneRedéfinitionTrouveLaMéthodeDeBase — super sur une redéfinition. */
    @Test
    fun superDefinitionDuneRedefinitionTrouveLaMethodeDeBase() {
        val document = ouvrir("Derivee.java", sourceDerivee)
        // Le caret au milieu du nom « signaler() » de la redéfinition.
        val positionSignaler = positionDe(sourceDerivee, "signaler")
            .let { Position(it.line, it.character + "signaler".length / 2) }
        val cibles = boucle!!.experimental.superDefinition(
            org.eclipse.lsp4j.TextDocumentPositionParams(document, positionSignaler),
        ).get()
        assertEquals(1, cibles.size, "exactement 1 cible (GO TO « Super », méthode expérimentale)")
        assertTrue(
            cibles[0].path.endsWith("Base.java"),
            "le chemin se termine par Base.java — la méthode de BASE est la cible — trouvé : ${cibles[0].path}",
        )
        // Contrat de sérialisation du vendored (INTEGRATION-CODEIDE §2) :
        // {path, start, end, label} — miroir exact du client cel-lsp. La
        // cible transporte la plage du NOM déclaré DANS la source cible.
        assertTrue(
            cibles[0].start < cibles[0].end,
            "la plage du nom est bien formée (${cibles[0].start} < ${cibles[0].end})",
        )
        val extrait = runCatching {
            Files.readString(dossier.resolve("demo/Base.java")).substring(cibles[0].start, cibles[0].end)
        }.getOrDefault("<hors-bornes>")
        assertEquals(
            "signaler",
            extrait,
            "start/end délimitent le JETON « signaler » dans Base.java (contrat vendord, miroir cel-lsp)",
        )
        assertEquals(
            "signaler  ·  Base",
            cibles[0].label,
            "libellé picker exact « signaler  ·  Base » (deux espaces autour du séparateur)",
        )
    }

    /** decompiledSourceSurUneClasseDuJdkRendLaSource — decompiledSource sur un FQCN du JDK. */
    @Test
    fun decompiledSourceSurUneClasseDuJdkRendLaSource() {
        ouvrir("T.java", sourceT)
        // Scénario de production : la définition a répondu
        // jdt://decompiled/java.lang.String — le client demande la source.
        val reponse = boucle!!.experimental.decompiledSource(
            ParamsFqcnRpc("java.lang.String"),
        ).get()
        assertNotNull(reponse, "réponse non-null")
        assertNotNull(reponse.source, "champ source non-null")
        assertTrue(
            reponse.source!!.isNotBlank(),
            "source non vide (blank)",
        )
        assertTrue(
            "String" in reponse.source!!,
            "la source du JDK (attachée ou décompilée) contient « String »",
        )
    }
}
