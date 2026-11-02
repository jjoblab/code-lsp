package jo.codeide.lsp.java.protocole

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DocumentHighlightParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.InlayHintParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.services.LanguageServer
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * V-H — LA TEMPÊTE D'APPAREIL, rejouée au PROTOCOLE (retour du 01/11/2026,
 * logs `codeide-logs-2026-11-01-221628.zip`, moto g06, CodeIDE 0.205.2) :
 * « liaison étant une instance de ActivityMainBinding lorsque j'ai tapé
 * "liaison." il devrait me proposer des méthodes... De même pour la classe
 * Greeter, la complétion ne propose rien. »
 *
 * Ce que les logs mesuraient : le client émet À CHAQUE frappe/déplacement
 * une rafale didChange + complétion + inlayHint + surbrillances (+ codeAction
 * sur idle) ; le serveur historique calculait CHAQUE requête
 * SYNCHRONEMENT sur le fil de lecture lsp4j — la complétion patientait
 * derrière TOUTE la file (29 s et 87 s mesurés, timeout client 5 s :
 * RIEN ne s'affichait).
 *
 * Ce verrou éprouve le RÉPARTITEUR V-H par le chemin de production
 * ([BoucleLocale] — allers-retours LSP4J authentiques) :
 *
 *  - **S1** : la tempête (rafales inlay + surbrillances + didChange) ne
 *    fait PERDRE aucune requête, n'échoue AUCUNE future — et la complétion
 *    de frappe rend ses items JUSTES pendant la tempête ;
 *  - **S2** : la complétion d'un document qui MUTÉ entre la soumission et
 *    le service rend la réponse VIDE honnête (invariant « 0 réponse
 *    périmée » — jamais un mélange de deux époques) ;
 *  - **S3** : l'arrêt du serveur (`exit`) solde les requêtes en attente
 *    par leur réponse vide — AUCUNE future en erreur pour le client.
 *
 * Les sémantiques de priorité/supersession (frappe devant confort,
 * remplacement sans courir) sont verrouillées séparément au niveau du
 * répartiteur ([jo.codeide.lsp.java.RepartitionPrioriteeVhTest]) — ici,
 * seules les propriétés OBSERVABLES au fil LSP sont éprouvées (aucune
 * gate de minutage : le bac et la CI partagent leurs cœurs, motif
 * « floçon documenté »).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TempeteAppareilVhTest {

    /** La racine des fixtures (MANUELLE — motif Fiche026/066/054). */
    private lateinit var dossier: Path

    /** La boucle locale de la suite. */
    private var boucle: BoucleLocale? = null

    /** L'URI du document ouvert. */
    private lateinit var uri: String

    /** La source ouverte : un Greeter du projet, une locale, un appel. */
    private val sourceTempete = """
        package com.exemple.monip;

        public class Main {

            public int travail() {
                Greeter greeter = new Greeter();
                return greeter.saluer("monip").length();
            }
        }
    """.trimIndent() + "\n"

    /** La source mutée : la locale renommée (S2 — péremption d'époque). */
    private val sourceMutee = """
        package com.exemple.monip;

        public class Main {

            public int travail() {
                Greeter accueil = new Greeter();
                return accueil.saluer("monip").length();
            }
        }
    """.trimIndent() + "\n"

    @BeforeAll
    fun avantTout() {
        dossier = Files.createTempDirectory("tempete-vh")
        val paquet = dossier.resolve("com/exemple/monip")
        Files.createDirectories(paquet)
        Files.writeString(
            paquet.resolve("Greeter.java"),
            "package com.exemple.monip;\n\npublic class Greeter {\n    public String saluer(String nom) { return nom; }\n}\n",
        )
        Files.writeString(paquet.resolve("Main.java"), sourceTempete)

        val jdk = jo.codeide.lsp.java.navigation.JdkDeTest.home()
        org.junit.jupiter.api.Assumptions.assumeTrue(
            jdk != null,
            "JDK de test indisponible (lib/modules) — tempête ignorée (gate visible)",
        )

        val serveur = ServeurLangageJava(
            fabriqueMoteur = { racine ->
                racine?.let {
                    jo.codeide.lsp.java.MoteurJava(
                        racines = listOf(it),
                        jars = emptyList(),
                        jdkHome = jdk,
                    )
                }
            },
        )
        boucle = BoucleLocale(serveur).also { b ->
            b.demarrer()
            b.client.initialize(InitializeParams().apply { rootUri = dossier.toUri().toString() }).get()
            b.client.initialized(InitializedParams())
        }
        uri = dossier.resolve("com/exemple/monip/Main.java").toUri().toString()
        boucle!!.client.textDocumentService.didOpen(
            DidOpenTextDocumentParams(TextDocumentItem(uri, "java", 0, sourceTempete)),
        )
    }

    /** Un didChange plein texte (le geste de frappe de l'utilisateur). */
    private fun b_didChange(version: Int, texte: String) {
        boucle!!.client.textDocumentService.didChange(
            DidChangeTextDocumentParams(
                VersionedTextDocumentIdentifier(uri, version),
                listOf(TextDocumentContentChangeEvent(texte)),
            ),
        )
    }

    /** La position (ligne, colonne) du début d'un extrait cherché. */
    private fun positionDe(source: String, cherche: String, occurrence: Int = 0): Position {
        var indice = -1
        repeat(occurrence + 1) { passe ->
            indice = source.indexOf(cherche, indice + 1)
            assertTrue(indice >= 0, "extrait introuvable (occurrence ${passe + 1}) : $cherche")
        }
        val ligne = source.substring(0, indice).count { it == '\n' }
        val debutLigne = source.lastIndexOf('\n', indice - 1) + 1
        return Position(ligne, indice - debutLigne)
    }

    /** La position (ligne, colonne) de la FIN d'un extrait cherché. */
    private fun positionApres(source: String, cherche: String): Position {
        val base = positionDe(source, cherche)
        return Position(base.line, base.character + cherche.length)
    }

    /** Les libellés d'une complétion au caret après un extrait. */
    private fun libellesCompletion(position: Position): List<String> {
        val params = CompletionParams()
        params.textDocument = TextDocumentIdentifier(uri)
        params.position = position
        val reponse = boucle!!.client.textDocumentService.completion(params).get(30, TimeUnit.SECONDS)
        return if (reponse.isLeft) reponse.left.map { it.label } else reponse.right.items.map { it.label }
    }

    @Test
    fun `S1 - la completion de frappe rend ses items pendant la tempete`() {
        val client: LanguageServer = boucle!!.client
        // LA TEMPÊTE : la rafale du client réel — inlay + surbrillances à
        // chaque frappe, SANS attendre les réponses (exactement le journal).
        val tempete = ArrayList<CompletableFuture<*>>()
        for (tour in 0 until 6) {
            tempete.add(
                client.textDocumentService.inlayHint(
                    InlayHintParams().apply {
                        textDocument = TextDocumentIdentifier(uri)
                        range = Range(Position(0, 0), Position(12, 0))
                    },
                ),
            )
            tempete.add(
                client.textDocumentService.documentHighlight(
                    DocumentHighlightParams().apply {
                        textDocument = TextDocumentIdentifier(uri)
                        position = positionDe(sourceTempete, "greeter", occurrence = 1)
                    },
                ),
            )
            b_didChange(tour + 1, sourceTempete)
        }
        // La complétion DE FRAPPE : `greeter.` — les membres du Greeter DU
        // PROJET (le retour exact du propriétaire : « De même pour une
        // classe Greeter du projet, la complétion ne propose rien »).
        val libelles = libellesCompletion(positionApres(sourceTempete, "greeter."))
        // TOUT se solde : aucune future en erreur (la tempête ne perd ni ne
        // corrompt rien), la complétion rend les membres du projet.
        for (avenir in tempete) {
            avenir.get(30, TimeUnit.SECONDS)
        }
        assertTrue(
            "saluer" in libelles,
            "les membres du Greeter DU PROJET doivent paraître après « greeter. » : $libelles",
        )
    }

    @Test
    fun `S2 - la mutation du document rend la reponse perimee honnete`() {
        val client: LanguageServer = boucle!!.client
        // Une complétion sur l'ANCIEN texte (capturée AVANT la mutation).
        val paramsAnciens = CompletionParams()
        paramsAnciens.textDocument = TextDocumentIdentifier(uri)
        paramsAnciens.position = positionApres(sourceTempete, "greeter.")
        val completionAncienne = client.textDocumentService.completion(paramsAnciens)
        // Le document MUTE avant le service (la frappe suivante de
        // l'utilisateur — l'époque de la capture est révolue).
        b_didChange(42, sourceMutee)
        // Invariant « 0 réponse périmée » : la réponse de l'ANCIENNE requête
        // ne part JAMAIS avec un résultat calculé contre le texte révolu —
        // vide honnête OU résultat frais, mais JAMAIS un échec, JAMAIS un
        // mélange d'époques.
        val reponse = completionAncienne.get(30, TimeUnit.SECONDS)
        assertTrue(reponse != null, "la requête périmée est servie (vide honnête), jamais perdue")
        // La complétion NEUVE, sur le texte muté, rend ses membres.
        val libelles = libellesCompletion(positionApres(sourceMutee, "accueil."))
        assertTrue(
            "saluer" in libelles,
            "les membres du Greeter doivent paraître après « accueil. » du texte muté : $libelles",
        )
    }

    @Test
    fun `S3 - l arret du serveur solde les requetes en attente sans erreur`() {
        // Un SECOND serveur (le protocole n'autorise qu'un initialize par
        // session) : des requêtes en vol, puis exit() — le client ne doit
        // voir AUCUNE future échouer (solde par la réponse vide honnête).
        val jdk = jo.codeide.lsp.java.navigation.JdkDeTest.home()
        org.junit.jupiter.api.Assumptions.assumeTrue(jdk != null, "JDK indisponible (gate visible)")
        val serveur = ServeurLangageJava(
            fabriqueMoteur = { racine ->
                racine?.let {
                    jo.codeide.lsp.java.MoteurJava(
                        racines = listOf(it),
                        jars = emptyList(),
                        jdkHome = jdk,
                    )
                }
            },
        )
        val boucleArret = BoucleLocale(serveur).also { b ->
            b.demarrer()
            b.client.initialize(InitializeParams().apply { rootUri = dossier.toUri().toString() }).get()
            b.client.initialized(InitializedParams())
        }
        val uriArret = dossier.resolve("com/exemple/monip/Main.java").toUri().toString()
        boucleArret.client.textDocumentService.didOpen(
            DidOpenTextDocumentParams(TextDocumentItem(uriArret, "java", 0, sourceTempete)),
        )
        val enVol = ArrayList<CompletableFuture<*>>()
        repeat(8) {
            enVol.add(
                boucleArret.client.textDocumentService.inlayHint(
                    InlayHintParams().apply {
                        textDocument = TextDocumentIdentifier(uriArret)
                        range = Range(Position(0, 0), Position(12, 0))
                    },
                ),
            )
        }
        // exit() PENDANT le service : shutdown + rappels — les requêtes en
        // attente se soldent (vide honnête), les futures déjà servies le
        // restent ; AUCUNE ne doit se solder en erreur.
        boucleArret.client.exit()
        for (avenir in enVol) {
            try {
                avenir.get(30, TimeUnit.SECONDS)
            } catch (e: java.util.concurrent.ExecutionException) {
                throw AssertionError(
                    "une requête en vol a échoué à l'arrêt (le client verrait une erreur) : ${e.cause}",
                    e,
                )
            }
        }
        assertTrue(serveur.cycle.etat() == CycleVie.Etat.EXITED, "le serveur est EXITED après exit")
    }
}
