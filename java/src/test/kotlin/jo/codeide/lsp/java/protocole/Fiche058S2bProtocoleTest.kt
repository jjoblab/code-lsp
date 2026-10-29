package jo.codeide.lsp.java.protocole

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ExecutionException
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail
import org.eclipse.lsp4j.CallHierarchyIncomingCallsParams
import org.eclipse.lsp4j.CallHierarchyItem
import org.eclipse.lsp4j.CallHierarchyOutgoingCallsParams
import org.eclipse.lsp4j.CallHierarchyPrepareParams
import org.eclipse.lsp4j.CodeLensParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DocumentOnTypeFormattingParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.SelectionRangeParams
import org.eclipse.lsp4j.SemanticTokensParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.TypeHierarchyPrepareParams
import org.eclipse.lsp4j.TypeHierarchySubtypesParams
import org.eclipse.lsp4j.TypeHierarchySupertypesParams
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Fiche 058 — JavaS2bProtocoleTest : lot S2-b « confort structurel
 * restant » : protocole bout-en-bout à travers le serveur réel —
 * `selectionRange` (chaîne strictement croissante), `onTypeFormatting`
 * (« } » reformate le bloc fermé seul), `codeLens` (une lens par
 * déclaration + `resolve` avec comptage de références), hiérarchies de
 * types et d'appels, `filterText` de complétion (I12) et garde lifecycle
 * I2 (rejets avant initialize).
 *
 * Montage SANS classpath (fichiers sources écrits sur disque avant
 * création du serveur) — aucun android.jar requis. La position du
 * déclencheur onTypeFormatting pointe SUR le caractère tapé (le contrat
 * moteur N4 verrouille `source[offset] == déclencheur` ; le caractère
 * étranger → no-op).
 */
class Fiche058S2bProtocoleTest {

    @TempDir
    lateinit var dossier: Path

    /** La classe T mal indentée (deux méthodes, corps non indentés). */
    private val sourceT = """
        package demo;

        public class T {
        void a() {
        int x=1;
        }
        void b() {
        int y=2;
        }
        }
    """.trimIndent() + "\n"

    /** CLBase isolée (classe + méthode utilise — ligne ≥ 3, cahier). */
    private val sourceClBase = """
        package demo;

        public class CLBase {

            // La méthode ciblée par la lentille.
            public void utilise(int n) {
                return n;
            }
        }
    """.trimIndent() + "\n"

    /** Base (déclare utilise — ligne ≥ 3 pour la lentille). */
    private val sourceBase = """
        package demo;

        public class Base {

            // La méthode comptée par la lentille.
            public void utilise(int n) {
            }
        }
    """.trimIndent() + "\n"

    /** Appelant (appelle b.utilise(3)). */
    private val sourceAppelant = """
        package demo;

        public class Appelant {
            public void declenche() {
                Base b = new Base();
                b.utilise(3);
            }
        }
    """.trimIndent() + "\n"

    /** Base2 / Enfant2 (héritage). */
    private val sourceBase2 = """
        package demo;

        public class Base2 {
        }
    """.trimIndent() + "\n"

    private val sourceEnfant2 = """
        package demo;

        public class Enfant2 extends Base2 {
        }
    """.trimIndent() + "\n"

    /** CHA (l'appelant de la hiérarchie d'appels). */
    private val sourceCha = """
        package demo;

        public class CHA {
            public void declenche() {
                Base b = new Base();
                b.utilise(3);
            }
        }
    """.trimIndent() + "\n"

    /** FT (source de l'appelant — complétion juste après « b. »). */
    private val sourceFt = """
        package demo;

        public class FT {
            public void declenche() {
                Base b = new Base();
                b.
            }
        }
    """.trimIndent() + "\n"

    /** Un serveur réel sur le dossier (moteur nu, sans classpath). */
    private fun serveurSurDossier(): ServeurLangageJava = ServeurLangageJava(
        fabriqueMoteur = { racine -> racine?.let { jo.codeide.lsp.java.MoteurJava(racines = listOf(it)) } },
    )

    /** Monte la boucle + initialise le serveur sur le dossier. */
    private fun bouclePrete(): BoucleLocale {
        val boucle = BoucleLocale(serveurSurDossier())
        boucle.demarrer()
        boucle.client.initialize(
            InitializeParams().apply { rootUri = dossier.toUri().toString() },
        ).get()
        boucle.client.initialized(InitializedParams())
        return boucle
    }

    /** Écrit un fichier sous le dossier (noms canoniques). */
    private fun ecrire(nom: String, texte: String): Path {
        val fichier = dossier.resolve(nom)
        Files.createDirectories(fichier.parent)
        Files.writeString(fichier, texte)
        return fichier
    }

    /** Ouvre un document du dossier (didOpen version 0). */
    private fun ouvrir(boucle: BoucleLocale, fichier: Path, texte: String) {
        boucle.client.textDocumentService.didOpen(
            DidOpenTextDocumentParams(TextDocumentItem(fichier.toUri().toString(), "java", 0, texte)),
        )
    }

    /** La position LSP d'un texte cherché dans une source. */
    private fun positionDe(source: String, cherche: String, occurrence: Int = 0): Position {
        var indice = -1
        repeat(occurrence + 1) { indice = source.indexOf(cherche, indice + 1) }
        val ligne = source.substring(0, indice).count { it == '\n' }
        val debutLigne = source.lastIndexOf('\n', indice - 1) + 1
        return Position(ligne, indice - debutLigne)
    }

    /** ---------------------------------------------------------------- */

    /** capabilitiesS2bDeclarees — capabilities S2-b annoncées. */
    @Test
    fun capabilitiesS2bDeclarees() {
        val boucle = BoucleLocale(serveurSurDossier())
        boucle.demarrer()
        val capacites = boucle.client.initialize(
            InitializeParams().apply { rootUri = dossier.toUri().toString() },
        ).get().capabilities

        assertEquals(true, capacites.selectionRangeProvider.left, "selectionRangeProvider booléen TRUE (gauche)")

        val frappe = capacites.documentOnTypeFormattingProvider
        assertNotNull(frappe, "documentOnTypeFormattingProvider non-null")
        assertEquals("}", frappe.firstTriggerCharacter, "premier déclencheur « } »")
        assertTrue(
            frappe.moreTriggerCharacter.contains(";"),
            "« ; » présent dans moreTriggerCharacter — trouvé : ${frappe.moreTriggerCharacter}",
        )

        val lentilles = capacites.codeLensProvider
        assertNotNull(lentilles, "codeLensProvider non-null")
        assertEquals(true, lentilles.resolveProvider, "resolveProvider TRUE")

        assertEquals(true, capacites.callHierarchyProvider.left, "callHierarchyProvider booléen TRUE (gauche)")
        assertEquals(true, capacites.typeHierarchyProvider.left, "typeHierarchyProvider booléen TRUE (gauche)")
    }

    /** selectionRangeChaineCroissanteBoutEnBout — chaîne de plages strictement croissante. */
    @Test
    fun selectionRangeChaineCroissanteBoutEnBout() {
        val fichier = ecrire("demo/T.java", sourceT)
        val boucle = bouclePrete()
        ouvrir(boucle, fichier, sourceT)

        val params = SelectionRangeParams()
        params.textDocument = TextDocumentIdentifier(fichier.toUri().toString())
        params.positions = listOf(positionDe(sourceT, "int y=2;"))
        val chaines = boucle.client.textDocumentService.selectionRange(params).get()
        assertEquals(1, chaines.size, "exactement 1 réponse (autant que de positions demandées)")

        // En descendant la chaîne parent : plages non vides, longueur
        // STRICTEMENT croissante, profondeur ≥ 4.
        var niveau = chaines[0]
        var profondeur = 0
        var longueurPrecedente = -1
        while (true) {
            val plage = niveau.range
            val longueur = longueurDe(plage)
            assertTrue(longueur > 0, "chaque plage est non vide (niveau $profondeur)")
            assertTrue(
                longueur > longueurPrecedente,
                "longueur STRICTEMENT croissante au niveau $profondeur : $longueur après $longueurPrecedente",
            )
            longueurPrecedente = longueur
            profondeur++
            niveau = niveau.parent ?: break
        }
        assertTrue(profondeur >= 4, "la profondeur atteinte est ≥ 4 niveaux — trouvée : $profondeur")
    }

    /** La longueur (en caractères) d'une plage LSP. */
    private fun longueurDe(plage: Range): Int {
        val debut = plage.start.line * 100_000 + plage.start.character
        val fin = plage.end.line * 100_000 + plage.end.character
        return fin - debut
    }

    /** onTypeFormattingAccoladeReformateLeBlocSeulement — « } » reformate le bloc seul. */
    @Test
    fun onTypeFormattingAccoladeReformateLeBlocSeulement() {
        val fichier = ecrire("demo/TF.java", sourceT)
        val boucle = bouclePrete()
        ouvrir(boucle, fichier, sourceT)

        // La position DU « } » fermant la méthode b() (le contrat moteur
        // verrouille le caractère À la position ; le caret de l'éditeur
        // se retrouve juste après la frappe).
        val positionFermetureB = positionDe(sourceT, "int y=2;")
            .let { Position(it.line + 1, 0) } // la ligne après le corps de b
        val params = DocumentOnTypeFormattingParams(
            TextDocumentIdentifier(fichier.toUri().toString()),
            org.eclipse.lsp4j.FormattingOptions(4, false),
            positionFermetureB,
            "}",
        )
        val editions = boucle.client.textDocumentService.onTypeFormatting(params).get()
        assertNotNull(editions, "la liste d'éditions est non-null")
        assertTrue(editions.isNotEmpty(), "éditions non vides")

        // Le texte reconstruit : « int y = 2; » formaté, « int x=1; » intact.
        val reconstruit = appliquer(sourceT, editions)
        assertTrue(
            "int y = 2;" in reconstruit,
            "« int y = 2; » (bloc b) est formaté — reconstruit : $reconstruit",
        )
        assertTrue(
            "int x=1;" in reconstruit,
            "« int x=1; » (méthode a, hors bloc) reste intact",
        )
    }

    /** Application d'éditions TextEdit LSP à une source. */
    private fun appliquer(source: String, editions: List<org.eclipse.lsp4j.TextEdit>): String {
        val lignes = source.split("\n")
        fun offset(position: Position): Int =
            lignes.subList(0, position.line).sumOf { it.length + 1 } + position.character
        data class Edition(val debut: Int, val fin: Int, val texte: String)
        val converties = editions.map { Edition(offset(it.range.start), offset(it.range.end), it.newText) }
            .sortedByDescending { it.debut }
        val texte = StringBuilder(source)
        for (e in converties) {
            texte.replace(e.debut, e.fin, e.texte)
        }
        return texte.toString()
    }

    /** onTypeFormattingCaractereEtrangerAucuneEdition — caractère inattendu → no-op. */
    @Test
    fun onTypeFormattingCaractereEtrangerAucuneEdition() {
        val fichier = ecrire("demo/TF.java", sourceT)
        val boucle = bouclePrete()
        ouvrir(boucle, fichier, sourceT)
        // Position 0 : le caractère à cet offset (« p » de public) n'est
        // pas « } ».
        val params = DocumentOnTypeFormattingParams(
            TextDocumentIdentifier(fichier.toUri().toString()),
            org.eclipse.lsp4j.FormattingOptions(4, false),
            Position(0, 0),
            "}",
        )
        val editions = boucle.client.textDocumentService.onTypeFormatting(params).get()
        assertNotNull(editions, "liste non-null")
        assertTrue(editions.isEmpty(), "liste d'éditions VIDE (aucune action)")
    }

    /** codeLensParDeclarationResolveCompte — lens par déclaration, resolve paresseux. */
    @Test
    fun codeLensParDeclarationResolveCompte() {
        // Serveur ISOLÉ : SEUL CLBase.java sous la racine.
        val fichier = ecrire("demo/CLBase.java", sourceClBase)
        val boucle = bouclePrete()
        ouvrir(boucle, fichier, sourceClBase)

        val lentilles = boucle.client.textDocumentService.codeLens(
            CodeLensParams(TextDocumentIdentifier(fichier.toUri().toString())),
        ).get()
        assertEquals(2, lentilles.size, "exactement 2 lenses (une par déclaration : classe + méthode)")

        // La lens brute n'a PAS encore de commande (resolve paresseux).
        assertTrue(
            lentilles.all { it.command == null },
            "la lens brute n'a PAS encore de commande — trouvé : ${lentilles.map { it.command }}",
        )

        // La lens de la MÉTHODE (ligne ≥ 3) résolue : « 0 référence ».
        val lensMethode = lentilles.first { it.range.start.line >= 3 }
        val resolue = boucle.client.textDocumentService.resolveCodeLens(lensMethode).get()
        assertNotNull(resolue.command, "la lens résolue porte une commande")
        assertEquals("0 référence", resolue.command.title, "titre exact « 0 référence » (projet isolé)")
    }

    /** codeLensCompteLesReferencesMultiFichiers — comptage multi-fichiers, déclaration exclue. */
    @Test
    fun codeLensCompteLesReferencesMultiFichiers() {
        // Serveur isolé : Base + Appelant AUSSI sur disque (noms canoniques).
        val base = ecrire("demo/Base.java", sourceBase)
        ecrire("demo/Appelant.java", sourceAppelant)
        val boucle = bouclePrete()
        // Base OUVERT, Appelant seulement sur disque.
        ouvrir(boucle, base, sourceBase)

        val lentilles = boucle.client.textDocumentService.codeLens(
            CodeLensParams(TextDocumentIdentifier(base.toUri().toString())),
        ).get()
        val lensMethode = lentilles.first { it.range.start.line >= 3 }
        val resolue = boucle.client.textDocumentService.resolveCodeLens(lensMethode).get()
        assertEquals(
            "1 référence",
            resolue.command.title,
            "titre exactement « 1 référence » — l'appel b.utilise(3) du fichier non ouvert est compté (overlay PUIS disque), la déclaration est exclue",
        )
    }

    /** typeHierarchyPrepareSupertypesSubtypes — prepare/supertypes/subtypes. */
    @Test
    fun typeHierarchyPrepareSupertypesSubtypes() {
        val base2 = ecrire("demo/Base2.java", sourceBase2)
        val enfant2 = ecrire("demo/Enfant2.java", sourceEnfant2)
        val boucle = bouclePrete()
        ouvrir(boucle, base2, sourceBase2)
        ouvrir(boucle, enfant2, sourceEnfant2)

        // prepare sur la référence « extends Base2 » (fichier Enfant2).
        val prepareReference = TypeHierarchyPrepareParams(
            TextDocumentIdentifier(enfant2.toUri().toString()),
            positionDe(sourceEnfant2, "Base2"),
        )
        val itemsReference = boucle.client.textDocumentService.prepareTypeHierarchy(prepareReference).get()
        assertEquals(1, itemsReference.size, "1 élément (la référence « extends Base2 »)")
        assertEquals("Base2", itemsReference[0].name, "l'élément rendu est « Base2 »")
        assertTrue(
            itemsReference[0].uri.endsWith("Base2.java"),
            "localisé dans SA source (Base2.java) — trouvé : ${itemsReference[0].uri}",
        )

        // prepare sur « Enfant2 » (sa propre déclaration) → supertypes.
        val prepareEnfant = TypeHierarchyPrepareParams(
            TextDocumentIdentifier(enfant2.toUri().toString()),
            positionDe(sourceEnfant2, "Enfant2"),
        )
        val enfant = boucle.client.textDocumentService.prepareTypeHierarchy(prepareEnfant).get()
        assertEquals(1, enfant.size, "prepare sur Enfant2 : 1 élément")
        val supertypes = boucle.client.textDocumentService.typeHierarchySupertypes(
            TypeHierarchySupertypesParams(enfant[0]),
        ).get()
        assertEquals(1, supertypes.size, "supertypes de Enfant2 = exactement 1")
        assertEquals("Base2", supertypes[0].name, "le supertype est « Base2 »")

        // subtypes de l'élément Base2 préparé.
        val subtypes = boucle.client.textDocumentService.typeHierarchySubtypes(
            TypeHierarchySubtypesParams(itemsReference[0]),
        ).get()
        assertEquals(1, subtypes.size, "subtypes de Base2 = exactement 1 (balayage des racines sources)")
        assertEquals("Enfant2", subtypes[0].name, "le subtype est « Enfant2 »")
    }

    /** typeHierarchyPasDeTypeAuCaretListeVide — caret sans type : englobant ou vide. */
    @Test
    fun typeHierarchyPasDeTypeAuCaretListeVide() {
        val base2 = ecrire("demo/Base2.java", sourceBase2)
        val enfant2 = ecrire("demo/Enfant2.java", sourceEnfant2)
        val boucle = bouclePrete()
        ouvrir(boucle, enfant2, sourceEnfant2)

        // Caret dans le corps VIDE de la classe (juste après « { ») : le
        // type ENGLOBANT compte — 1 élément « Enfant2 ».
        val corpsVide = boucle.client.textDocumentService.prepareTypeHierarchy(
            TypeHierarchyPrepareParams(
                TextDocumentIdentifier(enfant2.toUri().toString()),
                positionDe(sourceEnfant2, "Base2 {").let { Position(it.line, it.character + "Base2 {".length) },
            ),
        ).get()
        assertEquals(1, corpsVide.size, "corps vide → 1 élément (le type ENGLOBANT compte)")
        assertEquals("Enfant2", corpsVide[0].name, "l'englobant est « Enfant2 »")

        // Caret sur « package » (avant toute classe) : liste VIDE.
        val avantTout = boucle.client.textDocumentService.prepareTypeHierarchy(
            TypeHierarchyPrepareParams(
                TextDocumentIdentifier(enfant2.toUri().toString()),
                Position(0, 2),
            ),
        ).get()
        assertEquals(0, avantTout.size, "sur « package » → liste VIDE (aucun type référencé ni englobant)")
    }

    /** callHierarchyPrepareIncomingOutgoing — hiérarchie d'appels complète. */
    @Test
    fun callHierarchyPrepareIncomingOutgoing() {
        val base = ecrire("demo/Base.java", sourceBase)
        val cha = ecrire("demo/CHA.java", sourceCha)
        val boucle = bouclePrete()
        ouvrir(boucle, cha, sourceCha)

        // prepare sur l'APPEL « b.utilise(3) » : rend la DÉCLARATION.
        val prepareAppel = CallHierarchyPrepareParams(
            TextDocumentIdentifier(cha.toUri().toString()),
            positionDe(sourceCha, "utilise"),
        )
        val cibles = boucle.client.textDocumentService.prepareCallHierarchy(prepareAppel).get()
        assertEquals(1, cibles.size, "1 élément")
        assertEquals("utilise", cibles[0].name, "le prepare sur l'APPEL rend « utilise » (la DÉCLARATION)")
        assertTrue(
            cibles[0].uri.endsWith("Base.java"),
            "localisé dans Base.java — trouvé : ${cibles[0].uri}",
        )

        // incoming : exactement 1 appelant « declenche » avec 1 site.
        val entrants = boucle.client.textDocumentService.callHierarchyIncomingCalls(
            CallHierarchyIncomingCallsParams(cibles[0]),
        ).get()
        assertEquals(1, entrants.size, "incoming = exactement 1 appelant")
        assertEquals("declenche", entrants[0].from.name, "l'appelant est « declenche »")
        assertEquals(1, entrants[0].fromRanges.size, "exactement 1 site d'appel (fromRanges)")

        // prepare sur « declenche() » puis outgoing : 1 cible source.
        val prepareDeclenche = CallHierarchyPrepareParams(
            TextDocumentIdentifier(cha.toUri().toString()),
            positionDe(sourceCha, "declenche"),
        )
        val declenche = boucle.client.textDocumentService.prepareCallHierarchy(prepareDeclenche).get()
        assertEquals(1, declenche.size, "prepare sur declenche : 1 élément")
        val sortants = boucle.client.textDocumentService.callHierarchyOutgoingCalls(
            CallHierarchyOutgoingCallsParams(declenche[0]),
        ).get()
        assertEquals(1, sortants.size, "outgoing de declenche = exactement 1 cible (les binaires sont écartées)")
        assertEquals("utilise", sortants[0].to.name, "la cible source est « utilise »")
        assertTrue(sortants[0].to.uri.endsWith("Base.java"), "la cible est localisée dans SA déclaration")
    }

    /** completionPorteFilterTextAligneSurLeLabel — filterText = label (I12). */
    @Test
    fun completionPorteFilterTextAligneSurLeLabel() {
        val base = ecrire("demo/Base.java", sourceBase)
        val ft = ecrire("demo/FT.java", sourceFt)
        val boucle = bouclePrete()
        ouvrir(boucle, ft, sourceFt)

        // Requête juste après « b. » (membres de Base).
        val positionApresPoint = positionDe(sourceFt, "b.")
            .let { Position(it.line, it.character + 2) }
        val params = org.eclipse.lsp4j.CompletionParams()
        params.textDocument = TextDocumentIdentifier(ft.toUri().toString())
        params.position = positionApresPoint
        val reponse = boucle.client.textDocumentService.completion(params).get()
        val liste = reponse.right
        assertNotNull(liste, "une CompletionList (forme droite)")
        assertTrue(liste.items.isNotEmpty(), "liste non vide (membres de Base)")

        // CHAQUE item porte un filterText non-null strictement ÉGAL à son label.
        for (item in liste.items) {
            assertNotNull(item.filterText, "filterText non-null pour « ${item.label} »")
            assertEquals(
                item.label,
                item.filterText,
                "filterText strictement ÉGAL au label pour « ${item.label} »",
            )
        }
    }

    /** requetesAvantInitialisationRejetees — garde I2 sur les 5 requêtes S2-b. */
    @Test
    fun requetesAvantInitialisationRejetees() {
        val serveur = serveurSurDossier()
        val boucle = BoucleLocale(serveur)
        boucle.demarrer()
        // Serveur NON initialisé : cinq requêtes successives.
        val fictif = dossier.resolve("demo/Fictif.java").toUri().toString()
        val service = boucle.client.textDocumentService

        val requetes: List<() -> java.util.concurrent.CompletableFuture<*>> = listOf(
            {
                val p = SelectionRangeParams()
                p.textDocument = TextDocumentIdentifier(fictif)
                p.positions = listOf(Position(0, 0))
                service.selectionRange(p)
            },
            {
                val p = DocumentOnTypeFormattingParams(
                    TextDocumentIdentifier(fictif),
                    org.eclipse.lsp4j.FormattingOptions(4, false),
                    Position(0, 0),
                    "}",
                )
                service.onTypeFormatting(p)
            },
            { service.codeLens(CodeLensParams(TextDocumentIdentifier(fictif))) },
            {
                service.prepareTypeHierarchy(
                    TypeHierarchyPrepareParams(TextDocumentIdentifier(fictif), Position(0, 0)),
                )
            },
            {
                service.prepareCallHierarchy(
                    CallHierarchyPrepareParams(TextDocumentIdentifier(fictif), Position(0, 0)),
                )
            },
        )
        for ((indice, requete) in requetes.withIndex()) {
            try {
                requete().get()
                fail("la requête $indice devait être rejetée (garde lifecycle I2)")
            } catch (e: ExecutionException) {
                val reponse = e.cause as? ResponseErrorException
                    ?: fail("requête $indice : la cause devait être ResponseErrorException — trouvé : ${e.cause}")
                assertEquals(
                    -32002,
                    reponse.responseError?.code,
                    "requête $indice : ServerNotInitialized (-32002)",
                )
            }
        }
    }
}
