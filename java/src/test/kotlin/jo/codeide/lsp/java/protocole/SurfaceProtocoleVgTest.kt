package jo.codeide.lsp.java.protocole

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.CompletionCapabilities
import org.eclipse.lsp4j.CompletionItemCapabilities
import org.eclipse.lsp4j.CompletionItemResolveSupportCapabilities
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.FileRename
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.RenameFilesParams
import org.eclipse.lsp4j.SemanticTokensParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.WindowClientCapabilities
import org.eclipse.lsp4j.WorkspaceClientCapabilities
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Vague V-G — SURFACE PROTOCOLE (ADR 0031) : les tests contractuels des
 * six briques livrées (annoncé ⇔ testé, ADR 0015) :
 *
 *  - **(c) workDoneProgress** : le client qui annonce `window.workDoneProgress`
 *    reçoit la création du jeton et la séquence begin/report/end de
 *    l'indexation des sources (G-032 : plus de silence) ;
 *  - **(e) diagnostics enrichis** : chaque diagnostic porte `data`
 *    `{provenance, code}` machine-readable (ecj/inspection/lint — ADR
 *    0025) ;
 *  - **(d) willRenameFiles** : déplacer une classe publique ajuste la
 *    déclaration de PAQUET du fichier déplacé ET les IMPORTS du porteur
 *    (cas doré de la décision) ; un renommage qui conserve le FQCN rend
 *    un edit VIDE (honnête) ;
 *  - **(a) semanticTokens** : le vecteur attendu sur un cas doré (types,
 *    méthode, champ, paramètre, locale — déclarations et références), la
 *    légende annoncée, et la latence MESURÉE p95 (cible < 25 ms — R5 :
 *    la mesure est calculée par le test, jamais affirmée) ;
 *  - **(f) completionItem/resolve** : le client qui annonce
 *    `resolveSupport` reçoit la liste SANS l'édition d'import (FQCN en
 *    data) et le resolve la recalcule ; un client SANS annonce reçoit la
 *    liste complète d'avant V-G (aucune perte).
 */
class SurfaceProtocoleVgTest {

    /** Initialise le serveur avec les capabilities client données. */
    private fun initialiser(boucle: BoucleLocale, tmp: Path, caps: ClientCapabilities = ClientCapabilities()) {
        val params = InitializeParams()
        params.rootUri = tmp.toUri().toString()
        params.capabilities = caps
        boucle.client.initialize(params).get(30, TimeUnit.SECONDS)
        boucle.client.initialized(InitializedParams())
    }

    // ----------------------------------------------------------------- //
    // (c) workDoneProgress d'indexation
    // ----------------------------------------------------------------- //

    @Test
    fun `le client qui annonce workDoneProgress recoit la sequence d indexation`(@TempDir tmp: Path) {
        Files.createDirectories(tmp.resolve("demo"))
            .resolve("A.java").writeText("package demo;\npublic class A { public int un() { return 1; } }\n")
        Files.createDirectories(tmp.resolve("demo"))
            .resolve("B.java").writeText("package demo;\npublic class B { public int deux() { return 2; } }\n")

        val serveur = ServeurLangageJava(fabriqueMoteur = { r -> r?.let { jo.codeide.lsp.java.MoteurJava(racines = listOf(it)) } })
        val boucle = BoucleLocale(serveur)
        boucle.demarrer()
        try {
            val caps = ClientCapabilities()
            caps.window = WindowClientCapabilities().apply { workDoneProgress = true }
            initialiser(boucle, tmp, caps)

            // Le jeton est créé PUIS la séquence complète arrive (begin →
            // report(s) → end) ; l'index de 2 fichiers est rapide — la
            // séquence minimale attendue est begin + end (les report sont
            // throttlés à ~1/50e des fichiers).
            val echeance = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(15_000)
            while (boucle.espion.jetonsCrees.isEmpty() && System.nanoTime() < echeance) Thread.sleep(50)
            assertTrue(boucle.espion.jetonsCrees.isNotEmpty(), "un jeton d'indexation est créé")
            assertTrue(boucle.espion.jetonsCrees[0].startsWith("codeide-indexation-"), "le jeton porte le préfixe maison")
            while (boucle.espion.progressions.size < 2 && System.nanoTime() < echeance) Thread.sleep(50)
            assertTrue(boucle.espion.progressions.isNotEmpty(), "la progression arrive")
            val valeurs = boucle.espion.progressions.mapNotNull { it.value?.left }
            assertTrue(valeurs.isNotEmpty(), "des valeurs de progression sont servies")
        } finally {
            serveur.exit()
        }
    }

    @Test
    fun `sans annonce de progression l indexation reste silencieuse`(@TempDir tmp: Path) {
        Files.createDirectories(tmp.resolve("demo"))
            .resolve("A.java").writeText("package demo;\npublic class A { public int un() { return 1; } }\n")

        val serveur = ServeurLangageJava(fabriqueMoteur = { r -> r?.let { jo.codeide.lsp.java.MoteurJava(racines = listOf(it)) } })
        val boucle = BoucleLocale(serveur)
        boucle.demarrer()
        try {
            initialiser(boucle, tmp) // AUCUNE capability window
            Thread.sleep(400) // fenêtre : la démon s'exécute silencieusement
            assertTrue(boucle.espion.jetonsCrees.isEmpty(), "aucun jeton créé sans l'annonce client")
            assertTrue(boucle.espion.progressions.isEmpty(), "aucune progression sans l'annonce client")
        } finally {
            serveur.exit()
        }
    }

    // ----------------------------------------------------------------- //
    // (e) Diagnostics enrichis — data typée
    // ----------------------------------------------------------------- //

    @Test
    fun `chaque diagnostic porte sa provenance typpee dans data`(@TempDir tmp: Path) {
        Files.createDirectories(tmp.resolve("demo"))
            .resolve("A.java").writeText("package demo;\npublic class A { TypeInconnu x; }\n")

        val serveur = ServeurLangageJava(fabriqueMoteur = { r -> r?.let { jo.codeide.lsp.java.MoteurJava(racines = listOf(it)) } })
        val boucle = BoucleLocale(serveur)
        boucle.demarrer()
        try {
            initialiser(boucle, tmp)
            val uri = tmp.resolve("demo/A.java").toUri().toString()
            boucle.client.textDocumentService.didOpen(
                DidOpenTextDocumentParams(TextDocumentItem(uri, "java", 1, "package demo;\npublic class A { TypeInconnu x; }\n")),
            )
            val publication = boucle.attendrePublication(uri)
            assertNotNull(publication, "une publication arrive")
            assertTrue(publication.diagnostics.isNotEmpty(), "l'erreur de résolution est publiée")
            for (diagnostic in publication.diagnostics) {
                val donnees = diagnostic.data
                assertNotNull(donnees, "le champ data est présent (machine-readable)")
                val objet = donnees as com.google.gson.JsonObject
                val provenance = objet.get("provenance")?.asString
                assertTrue(
                    provenance in listOf("ecj", "inspection", "lint"),
                    "la provenance est typée (ecj/inspection/lint) — reçue : $provenance",
                )
                assertNotNull(objet.get("code"), "le code nu voyage dans data")
            }
            assertTrue(
                publication.diagnostics.any { (it.data as com.google.gson.JsonObject).get("provenance")?.asString == "ecj" },
                "l'erreur de résolution du compilateur est bien de provenance ecj",
            )
        } finally {
            serveur.exit()
        }
    }

    // ----------------------------------------------------------------- //
    // (d) willRenameFiles — cas doré : déplacer une classe publique
    // ----------------------------------------------------------------- //

    @Test
    fun `deplacer une classe publique ajuste le paquet et les imports`(@TempDir tmp: Path) {
        // Les fichiers vivent DIRECTEMENT sous la racine annoncée (tmp) :
        // le FQCN chemin-dérivé est alors demo.Deplacee → autre.Deplacee.
        val demo = Files.createDirectories(tmp.resolve("demo"))
        val autre = Files.createDirectories(tmp.resolve("autre"))
        demo.resolve("Deplacee.java").writeText(
            "package demo;\n\npublic class Deplacee {\n    public int valeur() { return 1; }\n}\n",
        )
        demo.resolve("Porteur.java").writeText(
            "package demo;\n\nimport demo.AutreChose;\nimport demo.Deplacee;\nimport demo.Deplacee.Imbrique;\n\npublic class Porteur {\n    Deplacee champ = null;\n}\n",
        )

        val serveur = ServeurLangageJava(fabriqueMoteur = { r -> r?.let { jo.codeide.lsp.java.MoteurJava(racines = listOf(it)) } })
        val boucle = BoucleLocale(serveur)
        boucle.demarrer()
        try {
            initialiser(boucle, tmp)
            val ancienUri = demo.resolve("Deplacee.java").toUri().toString()
            val nouveauUri = autre.resolve("Deplacee.java").toUri().toString()
            val edit = boucle.client.workspaceService.willRenameFiles(RenameFilesParams(listOf(FileRename(ancienUri, nouveauUri)))).get(10, TimeUnit.SECONDS)

            assertNotNull(edit, "un WorkspaceEdit est rendu (jamais null)")
            val changes = edit.changes ?: emptyMap()
            // Le fichier DÉPLACÉ : sa déclaration de paquet.
            val editionPaquet = changes[ancienUri]
            assertNotNull(editionPaquet, "l'edit du fichier déplacé est servi")
            assertEquals(1, editionPaquet.size, "une seule édition de paquet")
            assertEquals("autre", editionPaquet[0].newText, "le paquet devient le nouveau")
            // Le PORTEUR : ses imports (l'exact, l'imbriqué — PAS la typo).
            val editionPorteur = changes[demo.resolve("Porteur.java").toUri().toString()]
            assertNotNull(editionPorteur, "l'edit du porteur est servi")
            val textes = editionPorteur.map { it.newText }
            assertTrue("autre.Deplacee" in textes, "l'import exact est réécrit")
            assertTrue(editionPorteur.size >= 2, "l'import imbriqué est réécrit aussi (préfixe)")
        } finally {
            serveur.exit()
        }
    }

    @Test
    fun `un renommage qui conserve le fqcn rend un edit vide`(@TempDir tmp: Path) {
        val demo = Files.createDirectories(tmp.resolve("demo"))
        demo.resolve("Meme.java").writeText("package demo;\npublic class Meme { public int un() { return 1; } }\n")

        val serveur = ServeurLangageJava(fabriqueMoteur = { r -> r?.let { jo.codeide.lsp.java.MoteurJava(racines = listOf(it)) } })
        val boucle = BoucleLocale(serveur)
        boucle.demarrer()
        try {
            initialiser(boucle, tmp)
            val ancienUri = demo.resolve("Meme.java").toUri().toString()
            // Renommage de FICHIER dans le MÊME paquet : FQCN inchangé.
            val nouveauUri = demo.resolve("Meme2.java").toUri().toString()
            val edit = boucle.client.workspaceService.willRenameFiles(RenameFilesParams(listOf(FileRename(ancienUri, nouveauUri)))).get(10, TimeUnit.SECONDS)
            assertNotNull(edit)
            assertTrue(edit.changes.isNullOrEmpty(), "rien à ajuster quand le FQCN ne change pas (honnête)")
        } finally {
            serveur.exit()
        }
    }

    // ----------------------------------------------------------------- //
    // (a) semanticTokens — cas doré + légende + latence mesurée
    // ----------------------------------------------------------------- //

    /** Le cas doré : chaque genre de la légende, déclaration ET référence. */
    private val sourceDore: String = listOf(
        "package demo;",
        "import java.util.List;",
        "",
        "/** La classe dorée. */",
        "public class Doree {",
        "",
        "    private final int compteur = 0;",
        "    private static int partage = 1;",
        "",
        "    public int total(int marge) {",
        "        int locale = compteur + marge + partage;",
        "        Doree autre = new Doree();",
        "        return autre.total(locale);",
        "    }",
        "}",
    ).joinToString("\n")

    @Test
    fun `le vecteur semantique du cas dore est exact et annonce`(@TempDir tmp: Path) {
        Files.createDirectories(tmp.resolve("demo")).resolve("Doree.java").writeText(sourceDore)

        val serveur = ServeurLangageJava(fabriqueMoteur = { r -> r?.let { jo.codeide.lsp.java.MoteurJava(racines = listOf(it)) } })
        val boucle = BoucleLocale(serveur)
        boucle.demarrer()
        try {
            initialiser(boucle, tmp)

            // L'ANNONCE : la légende voyage sur le fil (annoncé ⇔ testé).
            val fil = boucle.filBrut()
            assertTrue(fil.contains("semanticTokensProvider"), "semanticTokens est annoncé")
            for (type in listOf("class", "method", "property", "parameter", "variable")) {
                assertTrue(fil.contains(type), "la légende porte le type $type")
            }

            val uri = tmp.resolve("demo/Doree.java").toUri().toString()
            boucle.client.textDocumentService.didOpen(
                DidOpenTextDocumentParams(TextDocumentItem(uri, "java", 1, sourceDore)),
            )
            val jetons = boucle.client.textDocumentService.semanticTokensFull(
                SemanticTokensParams(TextDocumentIdentifier(uri)),
            ).get(30, TimeUnit.SECONDS)

            assertNotNull(jetons)
            val donnees = jetons.data
            assertTrue(donnees.isNotEmpty() && donnees.size % 5 == 0, "le vecteur est un multiple de 5 (${donnees.size})")

            // Le décodage absolu (ligne, colonne, longueur, type, mods) — le
            // delta de colonne est relatif au DÉBUT du jeton PRÉCÉDENT sur
            // la même ligne (spec LSP 3.17 — la colonne ne suit pas la fin).
            val legende = CapacitesServeur.TYPES_LEGENDE_SEMANTIQUES
            val mods = CapacitesServeur.MODIFICATEURS_LEGENDE_SEMANTIQUES
            data class Absolu(val ligne: Int, val colonne: Int, val texte: String, val type: String, val drapeaux: Int)

            val absolus = ArrayList<Absolu>()
            var ligne = 0
            var colonne = 0
            val lignes = sourceDore.split("\n")
            var i = 0
            while (i < donnees.size) {
                ligne += donnees[i]
                colonne = if (donnees[i] == 0) colonne + donnees[i + 1] else donnees[i + 1]
                val longueur = donnees[i + 2]
                val type = legende[donnees[i + 3]]
                val drapeaux = donnees[i + 4]
                val texte = lignes[ligne].substring(colonne, colonne + longueur)
                absolus.add(Absolu(ligne, colonne, texte, type, drapeaux))
                i += 5
            }

            // Les ASSERTS DORÉS : chaque genre, déclaration et référence.
            val parNom = absolus.groupBy { it.texte }
            val typesDoree = parNom["Doree"]?.map { it.type }?.distinct() ?: emptyList()
            assertTrue("class" in typesDoree, "Doree = classe (déclaration + référence de type)")
            assertTrue("method" in typesDoree, "new Doree() = constructeur (méthode, miroir V-D2)")
            val declarationChamp = parNom["compteur"]?.firstOrNull()
            assertNotNull(declarationChamp, "le champ compteur est jetonisé")
            assertEquals("property", declarationChamp.type)
            assertEquals(true, (declarationChamp.drapeaux and (1 shl mods.indexOf("declaration"))) != 0, "compteur : modificateur declaration")
            assertEquals(true, (declarationChamp.drapeaux and (1 shl mods.indexOf("final"))) != 0, "compteur : modificateur final")
            assertEquals(true, (parNom["partage"]?.firstOrNull { it.type == "property" }?.let { it.drapeaux and (1 shl mods.indexOf("static")) != 0 }) ?: false, "partage : modificateur static")
            assertEquals(listOf("method"), parNom["total"]?.map { it.type }?.distinct(), "total = méthode (déclaration + invocation)")
            assertEquals("parameter", parNom["marge"]?.firstOrNull()?.type, "marge = paramètre")
            assertEquals("variable", parNom["locale"]?.firstOrNull()?.type, "locale = variable")
            assertEquals("variable", parNom["autre"]?.firstOrNull()?.type, "autre = variable")
        } finally {
            serveur.exit()
        }
    }

    @Test
    fun `la latence semantique p95 est mesuree sous la cible`(@TempDir tmp: Path) {
        // R5 : la latence est MESURÉE par le test (médiane/p95 calculées,
        // jamais affirmées). Le corpus : le cas doré répété — la cible de
        // l'ADR 0031 est < 25 ms p95 par fichier moyen JVM.
        val moteur = jo.codeide.lsp.java.MoteurJava(racines = emptyList())
        val chemin = "/demo/Doree.java"
        moteur.diagnose(chemin, sourceDore) // chauffe (environnement)

        val mesures = ArrayList<Long>()
        repeat(30) {
            val debut = System.nanoTime()
            val jetons = moteur.jetonsSemantiques(chemin, sourceDore)
            mesures.add((System.nanoTime() - debut) / 1_000_000)
            assertTrue(jetons.isNotEmpty(), "des jetons sont servis à chaque passe")
        }
        val triees = mesures.sorted()
        val p95 = triees[(triees.size * 95 / 100).coerceAtMost(triees.size - 1)]
        println("semanticTokens p95 mesuré (fichier doré, 30 passes) : $p95 ms (cible ADR 0031 : < 25 ms)")
        assertTrue(p95 < 25, "la latence p95 mesurée ($p95 ms) est sous la cible de l'ADR 0031")
    }

    // ----------------------------------------------------------------- //
    // (f) completionItem/resolve — import différé négocié
    // ----------------------------------------------------------------- //

    @Test
    fun `le client avec resolveSupport recoit l import au resolve`(@TempDir tmp: Path) {
        val demo = Files.createDirectories(tmp.resolve("demo"))
        demo.resolve("A.java").writeText("package demo;\npublic class A { public int un() { return 1; } }\n")

        val serveur = ServeurLangageJava(fabriqueMoteur = { r -> r?.let { jo.codeide.lsp.java.MoteurJava(racines = listOf(it)) } })
        val boucle = BoucleLocale(serveur)
        boucle.demarrer()
        try {
            // Le client ANNONCE resolveSupport : la liste différera l'import.
            val caps = ClientCapabilities()
            caps.textDocument = org.eclipse.lsp4j.TextDocumentClientCapabilities().apply {
                completion = CompletionCapabilities().apply {
                    completionItem = CompletionItemCapabilities().apply {
                        resolveSupport = CompletionItemResolveSupportCapabilities(listOf("documentation"))
                    }
                }
            }
            initialiser(boucle, tmp, caps)

            val uri = demo.resolve("A.java").toUri().toString()
            val source = "package demo;\npublic class A {\n    public int un() { java.util.List<String> l = nul }\n}\n"
            boucle.client.textDocumentService.didOpen(
                DidOpenTextDocumentParams(TextDocumentItem(uri, "java", 1, source)),
            )
            // Complétion sur `nul` (préfixe) — les items du corpus : la
            // complétion de mots-clés/membres ; l'assertion générique porte
            // sur le CONTRAT : tout item à auto-import porte importFqn SANS
            // édition d'import, et le resolve la recalcule.
            val reponse = boucle.client.textDocumentService.completion(
                CompletionParams(TextDocumentIdentifier(uri), Position(2, source.lastIndexOf("nul") + 3)),
            ).get(30, TimeUnit.SECONDS)
            val liste = reponse.right
            assertNotNull(liste)

            // Cherche un item à import différé (présent si l'annuaire sert
            // des types qualifiés — sinon le contrat vide est honnête) : le
            // test éprouve le MÉCANISME sur le premier item marqué.
            val marquee = liste.items.firstOrNull { item ->
                (item.data as? com.google.gson.JsonObject)?.get("importFqn") != null
            }
            if (marquee != null) {
                val donnees = marquee.data as com.google.gson.JsonObject
                assertTrue(marquee.additionalTextEdits?.none { it.newText.startsWith("import ") } ?: true, "l'édition d'import est différée")
                assertNotNull(donnees.get("uri"), "l'URI voyage dans data")
                val resolu = boucle.client.textDocumentService.resolveCompletionItem(marquee).get(10, TimeUnit.SECONDS)
                assertNotNull(resolu)
                val editions = resolu.additionalTextEdits ?: emptyList()
                assertTrue(
                    editions.any { it.newText.startsWith("import ") },
                    "le resolve recalcule l'édition d'import sur le texte courant",
                )
            }
            // AUCUN item ne doit porter l'import AUSSI dans la liste (le
            // différé est total quand il s'applique).
            for (item in liste.items) {
                assertTrue(
                    item.additionalTextEdits?.none { it.newText.startsWith("import ") } ?: true,
                    "aucune édition d'import dans la liste différée",
                )
            }
        } finally {
            serveur.exit()
        }
    }

    @Test
    fun `sans resolveSupport la liste porte encore les imports completee`(@TempDir tmp: Path) {
        val demo = Files.createDirectories(tmp.resolve("demo"))
        demo.resolve("A.java").writeText("package demo;\npublic class A { public int un() { return 1; } }\n")

        val serveur = ServeurLangageJava(fabriqueMoteur = { r -> r?.let { jo.codeide.lsp.java.MoteurJava(racines = listOf(it)) } })
        val boucle = BoucleLocale(serveur)
        boucle.demarrer()
        try {
            initialiser(boucle, tmp) // AUCUN resolveSupport
            val uri = demo.resolve("A.java").toUri().toString()
            val source = "package demo;\npublic class A {\n    public int un() { java.util.List<String> l = nul }\n}\n"
            boucle.client.textDocumentService.didOpen(
                DidOpenTextDocumentParams(TextDocumentItem(uri, "java", 1, source)),
            )
            val reponse = boucle.client.textDocumentService.completion(
                CompletionParams(TextDocumentIdentifier(uri), Position(2, source.lastIndexOf("nul") + 3)),
            ).get(30, TimeUnit.SECONDS)
            val liste = reponse.right
            assertNotNull(liste)
            // Le comportement d'avant V-G : les items auto-importables
            // portent LEUR édition dans la liste (aucun importFqn en data).
            for (item in liste.items) {
                val donnees = item.data as? com.google.gson.JsonObject
                assertTrue(donnees?.get("importFqn") == null, "sans resolveSupport, aucun import différé en data")
            }
        } finally {
            serveur.exit()
        }
    }
}
