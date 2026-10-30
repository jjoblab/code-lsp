package jo.codeide.lsp.java.protocole

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue
import kotlin.test.fail
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder

/**
 * BANC PROJET ANDROID RÉEL (retours appareil du 31/10/2026 — « je veux que
 * tu crées un test projet android complet (avec de vrais jar, aar) et testé
 * les fonctionnalités ») : le moteur Java réécrit éprouvé contre un projet
 * Android COMPLET — vraie plateforme `android.jar` 34-ext12, vrais AARs
 * androidx/material (classes.jar extraits), data binding généré, régime
 * VIVANT multi-racines — exactement le montage de production de CodeIDE
 * (SessionLsp → EnvironnementCompilation → ServeurLangageJava).
 *
 * Le projet de test (`bancs/projet-android/` + sources générées de
 * `bancs/projet-android-genere/`) est la réplique du projet utilisateur
 * « monip » (com.example.monip, AppCompatActivity, ActivityMainBinding,
 * Greeter) ; les bibliothèques réelles sont téléchargées par
 * `scripts/preparer-projet-android.sh` dans `bancs/projet-android-libs/`
 * (R6.4 : jamais committées). Sans elles, la suite s'ignore avec un
 * message VISIBLE (gate environnementale, motif Fiche004).
 *
 * La TIMELINE reproduit les logs appareil du 30/10 12:44 — la cause racine
 * des « incohérences » y est littéralement rejouée :
 *
 *  1. didOpen AVANT toute sync (classpath vide — le cache démarré à froid) ;
 *  2. diagnostics « cannot be resolved » (le moteur vide, honnête) ;
 *  3. la sync NOURRIT le module :app (replaceAll à chaud) ;
 *  4. LIVE-REPUBLISH : les diagnostics doivent redevenir PROPRES sans
 *     re-initialize ni re-didOpen (le critère d'acceptation INTEGRATION) ;
 *  5. puis CHAQUE famille de complétion, une à une, avec la précision
 *     contextuelle d'Android Studio : import (paquets), classe (types +
 *     auto-import), membre (instance), statique (qualifier), locale,
 *     stub d'override, new, frappe continue (cohérence), latences.
 *
 * La précision attendue est celle demandée par le propriétaire : « les
 * suggestions doivent être précises dépendamment d'où je suis (import,
 * classe, membre, etc.), exactement comme Android Studio » — chaque test
 * S0x asserte la famille de contexte qui doit paraître ET celle qui ne
 * doit PAS fuider (p. ex. des méthodes dans un contexte d'import).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation::class)
class BancProjetAndroidReelTest {

    // ---------------------------------------------------------------- //
    // Gates environnementales — les bibliothèques réelles du banc
    // ---------------------------------------------------------------- //

    /** La racine des bibliothèques réelles (téléchargées hors dépôt). */
    private val libs: Path? = sequenceOf(
        System.getenv("BANC_ANDROID_LIBS")?.let { Path.of(it) },
        Path.of("../bancs/projet-android-libs"),
        Path.of("../../bancs/projet-android-libs"),
    ).firstOrNull { p -> p != null && Files.isDirectory(p) && Files.isRegularFile(p.resolve("android-34-ext12.jar")) }

    /** La racine des fixtures du projet (structure commise). */
    private val fixtures: Path? = sequenceOf(
        Path.of("../bancs/projet-android"),
        Path.of("../../bancs/projet-android"),
    ).firstOrNull { p -> Files.isDirectory(p) }

    /** La racine des sources générées simulées (structure commise). */
    private val generees: Path? = sequenceOf(
        Path.of("../bancs/projet-android-genere"),
        Path.of("../../bancs/projet-android-genere"),
    ).firstOrNull { p -> Files.isDirectory(p) }

    /** Le jar plateforme Android (la surface de compilation du module). */
    private fun androidJar(): Path? = libs?.resolve("android-34-ext12.jar")?.takeIf { Files.isRegularFile(it) }

    /** Les jars du classpath applicatif (classes.jar extraits des AARs).
     *
     * La FERMETURE transitive réelle d'un projet appcompat+material+data
     * binding (vérifiée classe par classe, hiérarchies complètes) :
     * sans activity/viewbinding/lifecycle-common, ecj rend « The hierarchy
     * of the type MainActivity is inconsistent » — le montage est celui
     * d'une VRAIE sync Gradle. */
    private fun jarsApplication(): List<Path> {
        val base = libs ?: return emptyList()
        return listOf(
            "appcompat-classes.jar",
            "core-classes.jar",
            "annotation-1.8.0.jar",
            "material-classes.jar",
            "constraintlayout-classes.jar",
            "lifecycle-runtime-classes.jar",
            "drawerlayout-classes.jar",
            "fragment-classes.jar",
            "activity-classes.jar",
            "lifecycle-viewmodel-classes.jar",
            "lifecycle-viewmodel-savedstate-classes.jar",
            "savedstate-classes.jar",
            "tracing-classes.jar",
            "emoji2-classes.jar",
            "core-viewtree-classes.jar",
            "lifecycle-common-2.6.2.jar",
            "databinding-runtime-classes.jar",
            "databinding-common-8.4.2.jar",
            "viewbinding-classes.jar",
        ).mapNotNull { nom ->
            base.resolve(nom).takeIf { Files.isRegularFile(it) }
        }
    }

    // ---------------------------------------------------------------- //
    // Montage
    // ---------------------------------------------------------------- //

    /** La racine du projet monté (copie temporaire du projet + du généré). */
    private lateinit var racine: Path

    /** La racine sources principales du module :app. */
    private val sourcesApp: Path get() = racine.resolve("app/src/main/java")

    /** La racine des sources GÉNÉRÉES (data binding + R — AGP les produit). */
    private val sourcesGenerees: Path get() = racine.resolve("app/build/generated/databinding")

    /** MainActivity — la source du projet. */
    private lateinit var sourceMain: String

    /** L'environnement détenu par le « hôte » (le banc joue SessionLsp). */
    private var environnement: jo.codeide.lsp.java.EnvironnementCompilation? = null

    /** Le serveur en régime vivant (la façade de production). */
    private var serveur: ServeurLangageJava? = null

    /** La boucle JSON-RPC (le chemin exact de l'éditeur). */
    private var boucle: BoucleLocale? = null

    /** Le document MainActivity ouvert au fil. */
    private var document: TextDocumentIdentifier? = null

    /** La version courante du document (moteur des didChange). */
    private var version: Int = 0

    @BeforeAll
    fun avantTout() {
        // Gates VISIBLES (motif Fiche004) : bibliothèques + fixtures.
        assumeTrue(
            libs != null && androidJar() != null && jarsApplication().size >= 5,
            "bibliothèques réelles absentes (bancs/projet-android-libs — " +
                "exécuter scripts/preparer-projet-android.sh) — banc ignoré (gate visible)",
        )
        assumeTrue(
            fixtures != null && generees != null,
            "fixtures du projet absentes (bancs/projet-android{,-genere}) — banc ignoré (gate visible)",
        )

        // Le projet monté : copie du projet réel + des sources générées à
        // leur emplacement AGP (app/build/generated/...) — le montage que
        // la sync Gradle décrit à l'orchestrateur.
        racine = Files.createTempDirectory("banc-android-reel")
        copierArbre(fixets()!!.resolve("app/src/main/java"), racine.resolve("app/src/main/java"))
        copierArbre(generees()!!, sourcesGenerees)
        sourceMain = Files.readString(sourcesApp.resolve("com/example/monip/MainActivity.java"))

        // L'HÔTE détient l'environnement (motif SessionLsp). jdkHome
        // INEXISTANT : pas d'image jrt — le régime APPAREIL où la
        // plateforme vit ENTIÈREMENT dans android.jar (v1.1.4 — sans
        // lib/modules ni fournisseur jrt, le régime reste plein).
        val env = jo.codeide.lsp.java.EnvironnementCompilation(
            jdkHome = Path.of("/jdk-inexistant-du-banc"),
        )
        env.routeurFichierModule = { chemin ->
            // Motif AssociationFichier : le fichier sous une racine du
            // module :app route vers :app ; sinon contexte défaut.
            val cheminApp = chemin.replace('\\', '/')
            if (cheminApp.contains("/app/src/main/java/") || cheminApp.contains("/app/build/generated/")) ":app" else null
        }

        // La TIMELINE APPAREIL : ouverture AVANT sync — l'état « cache »
        // du démarrage à froid porte la plateforme (android.jar) mais NI
        // les AARs NI les sources générées (les « cannot be resolved »
        // d'AppCompatActivity/ActivityMainBinding des logs, alors que
        // Bundle/String résolvent). La sync nourrira plus tard (test S02).
        env.remplacerContexteModule(":app", listOf(sourcesApp), listOf(androidJar()!!), "17")
        environnement = env

        val serveurVivant = ServeurLangageJava(env) { }
        serveur = serveurVivant
        boucle = BoucleLocale(serveurVivant).also { b ->
            b.demarrer()
            b.client.initialize(InitializeParams().apply { rootUri = racine.toUri().toString() }).get()
            b.client.initialized(InitializedParams())
        }
    }

    /** La racine des fixtures (non-null après les gates). */
    private fun fixets(): Path? = fixtures

    /** La racine des sources générées (non-null après les gates). */
    private fun generees(): Path? = generees

    /** Copie récursive d'un arbre de fichiers. */
    private fun copierArbre(source: Path, cible: Path) {
        Files.walk(source).forEach { entree ->
            val dest = cible.resolve(source.relativize(entree).toString())
            if (Files.isDirectory(entree)) {
                Files.createDirectories(dest)
            } else {
                Files.createDirectories(dest.parent)
                Files.copy(entree, dest, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    /** Ouvre (ou ré-ouvre) MainActivity avec un texte donné. */
    private fun ouvrirMain(texte: String): TextDocumentIdentifier {
        val b = boucle ?: fail("boucle absente")
        val uri = sourcesApp.resolve("com/example/monip/MainActivity.java").toUri().toString()
        b.client.textDocumentService.didOpen(
            DidOpenTextDocumentParams(TextDocumentItem(uri, "java", 0, texte)),
        )
        version = 0
        document = TextDocumentIdentifier(uri)
        return document!!
    }

    /** Frappe le document (sync plein texte — le mode annoncé du serveur). */
    private fun frapper(texte: String) {
        val b = boucle ?: fail("boucle absente")
        val doc = document ?: fail("document non ouvert")
        version += 1
        b.client.textDocumentService.didChange(
            DidChangeTextDocumentParams(
                VersionedTextDocumentIdentifier(doc.uri, version),
                listOf(TextDocumentContentChangeEvent(texte)),
            ),
        )
    }

    /**
     * La position juste APRÈS un texte cherché (caret de complétion) —
     * l'ancre peut être MULTI-LIGNE : la ligne/colonne se calcule à la
     * FIN de l'ancre (le caret), jamais au début.
     */
    private fun positionApres(source: String, cherche: String, occurrence: Int = 0): Position {
        var indice = -1
        repeat(occurrence + 1) { indice = source.indexOf(cherche, indice + 1) }
        if (indice < 0) fail("« $cherche » introuvable (occurrence $occurrence)")
        val fin = indice + cherche.length
        val ligne = source.substring(0, fin).count { it == '\n' }
        val debutLigne = source.lastIndexOf('\n', fin - 1) + 1
        return Position(ligne, fin - debutLigne)
    }

    /** Une complétion au caret après un texte cherché (items de la réponse). */
    private fun completionApres(source: String, apres: String, occurrence: Int = 0): List<org.eclipse.lsp4j.CompletionItem> {
        val doc = document ?: fail("document non ouvert")
        val params = CompletionParams()
        params.textDocument = doc
        params.position = positionApres(source, apres, occurrence)
        val reponse = boucle!!.client.textDocumentService.completion(params).get(60, TimeUnit.SECONDS)
        return (if (reponse.isLeft) reponse.left else reponse.right.items) ?: emptyList()
    }

    /** L'attente bornée d'une valeur (polling 50 ms). */
    private fun <T> attendre(delaiMs: Long = 20_000, fournisseur: () -> T?): T? {
        val fin = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(delaiMs)
        while (System.nanoTime() < fin) {
            val valeur = fournisseur()
            if (valeur != null) return valeur
            Thread.sleep(50)
        }
        return null
    }

    // ---------------------------------------------------------------- //
    // S01 — didOpen AVANT sync : honnêteté du moteur vide (timeline 1-2)
    // ---------------------------------------------------------------- //

    @Test
    @Order(1)
    fun S01_didOpen_avant_sync_le_moteur_du_cache_est_honnete() {
        ouvrirMain(sourceMain)
        val uri = document!!.uri
        // La publication initiale (état « cache ») : la plateforme résout
        // (android.jar — pas de « String cannot be resolved ») mais les
        // AARs manquent — « AppCompatActivity cannot be resolved », le
        // squiggle HONNÊTE des logs appareil.
        val publication = attendre {
            boucle!!.espion.publications.lastOrNull { it.uri == uri && it.diagnostics.isNotEmpty() }
        } ?: fail("aucune publication initiale (moteur du cache)")
        val messages = publication.diagnostics.map { it.message }
        println("[BANC] S01 didOpen avant sync → ${messages.size} diagnostic(s) : ${messages.take(6)}")
        assertTrue(
            messages.any { it.contains("AppCompatActivity cannot be resolved") },
            "l'état cache publie « AppCompatActivity cannot be resolved » (honnête) — reçu : $messages",
        )
        assertTrue(
            messages.none { it.contains("Bundle") && it.contains("cannot be resolved") },
            "la plateforme du cache résout Bundle (android.jar présent) — reçu : $messages",
        )
    }

    // ---------------------------------------------------------------- //
    // S02 — la sync nourrit :app : LIVE-REPUBLISH (timeline 3-4)
    // ---------------------------------------------------------------- //

    @Test
    @Order(2)
    fun S02_sync_nourrit_le_module_et_republish_propre() {
        val uri = document!!.uri
        // La sync (t+123,9 s des logs) : l'hôte remplace le contexte du
        // module :app — racines + classpath réel + niveau.
        environnement!!.remplacerContexteModule(
            ":app",
            listOf(sourcesApp, sourcesGenerees),
            jarsApplication() + listOf(androidJar()!!),
            "17",
        )

        // LIVE-REPUBLISH : SANS re-initialize ni re-didOpen, les
        // diagnostics du document OUVERT doivent redevenir PROPRES —
        // c'est le critère qui ÉCHOUAIT sur l'appareil (t+192 s : la
        // sync avait nourri, « cannot be resolved » persistait).
        val propre = attendre(delaiMs = 60_000) {
            boucle!!.espion.publications.lastOrNull { p ->
                p.uri == uri && p.diagnostics.none { d -> d.message.contains("cannot be resolved") }
            }
        }
        val derniere = boucle!!.espion.publications.lastOrNull { it.uri == uri }
        assertTrue(
            propre != null,
            "LIVE-REPUBLISH : les « cannot be resolved » s'effacent après la sync — " +
                "dernière publication : ${derniere?.diagnostics?.map { it.message }}",
        )
        println("[BANC] S02 après sync → diagnostics : ${derniere?.diagnostics?.map { it.message } ?: listOf()}")
    }

    // ---------------------------------------------------------------- //
    // S02b — la première complétion APRÈS sync : le coût du FROID
    // (l'appareil paie ici ~12 s — le « retard » des retours)
    // ---------------------------------------------------------------- //

    @Test
    @Order(3)
    fun S02b_premiere_completion_apres_sync_le_froid_est_borne() {
        // La PREMIÈRE requête après l'invalidation paie la reconstruction
        // du moteur : EnvironnementNoms neuf, lecture des 10 jars (~4 000
        // classes réelles), parse de l'unité — le moment exact où
        // l'appareil mesurait 11–12 s (cel-completion en attente).
        val doc = document ?: fail("document non ouvert")
        val params = CompletionParams()
        params.textDocument = doc
        params.position = positionApres(sourceMain, "binding.t")
        val debut = System.nanoTime()
        val reponse = boucle!!.client.textDocumentService.completion(params).get(120, TimeUnit.SECONDS)
        val froid = (System.nanoTime() - debut) / 1_000_000
        val nombre = (if (reponse.isLeft) reponse.left?.size else reponse.right?.items?.size) ?: 0
        println("[BANC] S02b première complétion après sync (froid complet) → ${froid}ms, $nombre items")
        assertTrue(
            froid < 60_000,
            "le froid complet reste borné (< 60 s) — reçu : $froid ms",
        )
    }

    // ---------------------------------------------------------------- //
    // S03 — import : les paquets de premier niveau (précision Android Studio)
    // ---------------------------------------------------------------- //

    @Test
    @Order(4)
    fun S03_import_racine_propose_les_paquets_de_premier_niveau() {
        val source = "package com.example.monip;\n\nimport a\n\n" +
            sourceMain.substringAfter("import android.os.Bundle;")
        frapper(source)
        val libelles = completionApres(source, "import a").map { it.label }
        println("[BANC] S03 import a| → ${libelles.size} items : ${libelles.take(24)}")
        for (attendu in listOf("android", "androidx")) {
            assertTrue(
                attendu in libelles,
                "« $attendu » est proposé après « import a » — reçu : ${libelles.take(24)}",
            )
        }
        // Précision Android Studio : le contexte IMPORT propose des
        // PAQUETS, pas des membres d'instance ni des mots-clés d'énoncé.
        assertTrue(
            libelles.none { it in listOf("if", "for", "while", "return", "new") },
            "aucun mot-clé d'énoncé ne fuit dans le contexte import — reçu : ${libelles.take(24)}",
        )
    }

    // ---------------------------------------------------------------- //
    // S04 — import androidx. : descendre l'arbre des paquets
    // ---------------------------------------------------------------- //

    @Test
    @Order(5)
    fun S04_import_androidx_descend_l_arbre() {
        val source = "package com.example.monip;\n\nimport androidx.a\n\n" +
            sourceMain.substringAfter("import android.os.Bundle;")
        frapper(source)
        val libelles = completionApres(source, "import androidx.a").map { it.label }
        println("[BANC] S04 import androidx.a| → ${libelles.size} items : ${libelles.take(24)}")
        assertTrue(
            "appcompat" in libelles,
            "« appcompat » est proposé après « import androidx.a » — reçu : ${libelles.take(24)}",
        )
    }

    // ---------------------------------------------------------------- //
    // S05 — import androidx.appcompat.app. : le TYPE direct (ni plus ni moins)
    // ---------------------------------------------------------------- //

    @Test
    @Order(6)
    fun S05_import_appcompat_app_propose_le_type_direct() {
        val source = "package com.example.monip;\n\nimport androidx.appcompat.app.AppCompatA\n\n" +
            sourceMain.substringAfter("import android.os.Bundle;")
        frapper(source)
        val libelles = completionApres(source, "import androidx.appcompat.app.AppCompatA").map { it.label }
        println("[BANC] S05 import …app.AppCompatA| → ${libelles.size} items : $libelles")
        assertTrue(
            "AppCompatActivity" in libelles,
            "« AppCompatActivity » est proposé (AAR appcompat réel) — reçu : $libelles",
        )
        // Règle d'or 4 (fiche 044) : le paquet app ne rend que ses types
        // DIRECTS — jamais les types du sous-paquet (AppCompatDelegate
        // vit dans …app, il EST direct ; rien de profond ne doit fuir).
        assertTrue(
            libelles.none { it.contains('.') },
            "aucun FQN profond ne fuit dans l'import (segments simples seuls) — reçu : $libelles",
        )
    }

    // ---------------------------------------------------------------- //
    // S06 — classe en position de type : auto-import à la Android Studio
    // ---------------------------------------------------------------- //

    @Test
    @Order(7)
    fun S06_type_avec_prefixe_propose_le_type_et_son_auto_import() {
        // Une frappe réaliste : le TYPE en cours de frappe — la déclaration
        // locale `MaterialAlertDialogB titre = …` (material-classes.jar,
        // SANS import préalable dans le fichier), caret juste après le
        // préfixe du type.
        val source = sourceMain.replace(
            "TextView titre = binding.textTitre;",
            "MaterialAlertDialogB titre = binding.textTitre;",
        )
        frapper(source)
        val items = completionApres(source, "MaterialAlertDialogB")
        val libelles = items.map { it.label }
        println("[BANC] S06 type MaterialAlertDialogB| → ${libelles.size} items : ${libelles.take(12)}")
        val cible = items.firstOrNull { it.label == "MaterialAlertDialogBuilder" }
        assertTrue(
            cible != null,
            "« MaterialAlertDialogBuilder » (AAR material réel) est proposé — reçu : ${libelles.take(12)}",
        )
        // Android Studio : l'auto-import voyage en additionalTextEdits.
        val editions = cible!!.additionalTextEdits ?: emptyList()
        println("[BANC] S06 auto-import → ${editions.size} édition(s) : ${editions.map { it.newText }}")
        assertTrue(
            editions.isNotEmpty() && editions.any { it.newText.contains("com.google.android.material.dialog.MaterialAlertDialogBuilder") },
            "l'auto-import de MaterialAlertDialogBuilder est proposé — reçu : ${editions.map { it.newText }}",
        )

        // Parité Android Studio (complément) : une fois le type posé, le
        // NOM de la locale en cours de frappe propose le nom dérivé du
        // type (`MaterialAlertDialogBuilder b|` → « builder »).
        val sourceNom = sourceMain.replace(
            "TextView titre = binding.textTitre;",
            "MaterialAlertDialogBuilder b = null;",
        )
        frapper(sourceNom)
        val itemsNom = completionApres(sourceNom, "MaterialAlertDialogBuilder b")
        println("[BANC] S06b nom dérivé après le type → ${itemsNom.map { it.label }}")
        assertTrue(
            itemsNom.any { it.label.startsWith("builder") },
            "le nom dérivé « builder » est proposé pour la locale — reçu : ${itemsNom.map { it.label }}",
        )
    }

    // ---------------------------------------------------------------- //
    // S07 — membre d'instance : les champs du binding (data binding réel)
    // ---------------------------------------------------------------- //

    @Test
    @Order(8)
    fun S07_membre_instance_propose_les_champs_du_binding() {
        val source = sourceMain.replace("titre.setText(liaison.saluer(\"monip\"));", "titre.setText(binding.t);")
        frapper(source)
        val libelles = completionApres(source, "binding.t").map { it.label }
        println("[BANC] S07 binding.t| → ${libelles.size} items : ${libelles.take(24)}")
        assertTrue(
            "textTitre" in libelles,
            "« textTitre » (champ du ActivityMainBinding généré) est proposé — reçu : ${libelles.take(24)}",
        )
        // Les champs de la classe générée, pas les paquets : la fuite
        // inverse (paquets dans un contexte de membre) serait une erreur.
        assertTrue(
            libelles.none { it in listOf("android", "androidx", "java") },
            "aucun PAQUET ne fuit dans le contexte de membre — reçu : ${libelles.take(24)}",
        )
    }

    // ---------------------------------------------------------------- //
    // S08 — membre d'instance projet : Greeter (source du module)
    // ---------------------------------------------------------------- //

    @Test
    @Order(9)
    fun S08_membre_instance_propose_les_methodes_du_greeter() {
        val source = sourceMain.replace("titre.setText(liaison.saluer(\"monip\"));", "titre.setText(liaison.s);")
        frapper(source)
        val libelles = completionApres(source, "liaison.s").map { it.label }
        println("[BANC] S08 liaison.s| → ${libelles.size} items : ${libelles.take(24)}")
        assertTrue(
            "saluer(String)" in libelles || libelles.any { it.startsWith("saluer") },
            "« saluer » (méthode du Greeter du projet) est proposée — reçu : ${libelles.take(24)}",
        )
        assertTrue(
            libelles.any { it.startsWith("setPolitesse") },
            "« setPolitesse » (setter du Greeter) est proposée — reçu : ${libelles.take(24)}",
        )
    }

    // ---------------------------------------------------------------- //
    // S09 — membre statique : R.layout (le R généré)
    // ---------------------------------------------------------------- //

    @Test
    @Order(10)
    fun S09_statique_propose_les_constantes_du_R() {
        val source = sourceMain.replace(
            "titre.setText(liaison.saluer(\"monip\"));",
            "titre.setText(String.valueOf(R.layout.a));",
        )
        frapper(source)
        val libelles = completionApres(source, "R.layout.a").map { it.label }
        println("[BANC] S09 R.layout.a| → ${libelles.size} items : $libelles")
        assertTrue(
            "activity_main" in libelles,
            "« activity_main » (constante du R généré) est proposée — reçu : $libelles",
        )
    }

    // ---------------------------------------------------------------- //
    // S10 — membre statique du binding généré : inflate/bind
    // ---------------------------------------------------------------- //

    @Test
    @Order(11)
    fun S10_statique_du_binding_propose_inflate_et_bind() {
        val source = sourceMain.replace(
            "binding = ActivityMainBinding.inflate(getLayoutInflater());",
            "binding = ActivityMainBinding.inf);",
        )
        frapper(source)
        val libelles = completionApres(source, "ActivityMainBinding.inf").map { it.label }
        println("[BANC] S10 ActivityMainBinding.inf| → ${libelles.size} items : ${libelles}")
        assertTrue(
            libelles.any { it.startsWith("inflate") },
            "« inflate » (statique du binding généré) est proposée — reçu : $libelles",
        )
    }

    // ---------------------------------------------------------------- //
    // S11 — locale + membres visibles dans le corps de méthode
    // ---------------------------------------------------------------- //

    @Test
    @Order(12)
    fun S11_corps_de_methode_propose_locale_et_membres() {
        // Une NOUVELLE ligne sous la déclaration de la locale — l'ancre
        // « textTitre;\n        ti » est UNIQUE (la déclaration précède le
        // caret), le préfixe tapé est « ti ».
        val source = sourceMain.replace(
            "TextView titre = binding.textTitre;",
            "TextView titre = binding.textTitre;\n        ti",
        )
        frapper(source)
        val libelles = completionApres(source, "textTitre;\n        ti").map { it.label }
        println("[BANC] S11 ti| (corps) → ${libelles.size} items : ${libelles.take(24)}")
        // Android Studio : la LOCALE correspondante au préfixe domine la
        // liste (les types Tile/Time/… suivent) — la localisation du
        // contexte EST la précision attendue.
        assertTrue(
            libelles.firstOrNull() == "titre",
            "« titre » (locale du corps) est proposée EN TÊTE — reçu : ${libelles.take(12)}",
        )
        // La précision du préfixe : les champs ne commençant PAS par « ti »
        // ne paraissent PAS (c'est le filtrage exact, pas une absence).
        assertTrue(
            "binding" !in libelles && "liaison" !in libelles,
            "les champs hors préfixe (« binding », « liaison ») ne fuient pas — reçu : ${libelles.take(12)}",
        )
    }

    // ---------------------------------------------------------------- //
    // S12 — new : les instanciables seuls (filtre grammatical)
    // ---------------------------------------------------------------- //

    @Test
    @Order(13)
    fun S12_new_propose_les_instanciables() {
        val source = sourceMain.replace(
            "titre.setText(liaison.saluer(\"monip\"));",
            "Object o = new Bund;",
        )
        frapper(source)
        val items = completionApres(source, "new Bund")
        val libelles = items.map { it.label }
        println("[BANC] S12 new Bund| → ${libelles.size} items : ${libelles.take(12)}")
        assertTrue(
            libelles.any { it == "Bundle" || it.startsWith("Bundle(") },
            "« Bundle » (android.jar réel) est proposé après new — reçu : ${libelles.take(12)}",
        )
    }

    // ---------------------------------------------------------------- //
    // S13 — corps de classe : le stub d'override (toStr → toString)
    // ---------------------------------------------------------------- //

    @Test
    @Order(14)
    fun S13_corps_de_classe_propose_le_stub_override() {
        val source = sourceMain.replace(
            "    @Override\n    protected void onCreate(Bundle savedInstanceState) {",
            "    toStr\n    @Override\n    protected void onCreate(Bundle savedInstanceState) {",
        )
        frapper(source)
        val items = completionApres(source, "toStr")
        val libelles = items.map { it.label }
        println("[BANC] S13 toStr| (corps de classe) → ${libelles.size} items : ${libelles.take(12)}")
        val stub = items.firstOrNull { it.label.startsWith("toString") }
        assertTrue(
            stub != null,
            "le stub « toString() » est proposé en corps de classe — reçu : ${libelles.take(12)}",
        )
        // Android Studio : le stub d'override INSÈRE le squelette.
        println("[BANC] S13 insertion du stub → «${stub!!.insertText?.take(120)}»")
    }

    // ---------------------------------------------------------------- //
    // S13b — corps de classe à préfixe VIDE : l'inventaire complet, sans
    // fuite du marqueur interne (le `__codeide_completion__()` du 1er run)
    // ---------------------------------------------------------------- //

    @Test
    @Order(15)
    fun S13b_corps_de_classe_prefixe_vide_sans_fuite_de_marqueur() {
        // Une ligne VIDE dans le corps de la classe (l'utilisateur vient
        // d'Entrée) : l'inventaire complet des stubs d'override doit
        // paraître — SANS le marqueur de splice interne.
        val source = sourceMain.replace(
            "    @Override\n    protected void onCreate(Bundle savedInstanceState) {",
            "    \n    @Override\n    protected void onCreate(Bundle savedInstanceState) {",
        )
        frapper(source)
        // L'ancre s'arrête à la FIN de la ligne vide (le caret), AVANT
        // le @Override — jamais au-delà.
        val items = completionApres(source, "new Greeter();\n\n    ")
        val libelles = items.map { it.label }
        println("[BANC] S13b préfixe vide (corps de classe) → ${libelles.size} items : ${libelles.take(12)}")
        assertTrue(
            libelles.none { it.contains("__codeide_completion__") },
            "le marqueur interne de splice ne fuit JAMAIS dans les items — reçu : ${libelles.take(20)}",
        )
        assertTrue(
            libelles.any { it.startsWith("toString") },
            "« toString() » figure dans l'inventaire à préfixe vide — reçu : ${libelles.take(20)}",
        )
    }

    // ---------------------------------------------------------------- //
    // S14 — hover : la documentation riche du type résolu (AAR réel)
    // ---------------------------------------------------------------- //

    @Test
    @Order(16)
    fun S14_hover_sur_appcompatactivity_est_riche() {
        frapper(sourceMain)
        val doc = document!!
        // Position MI-MOT (un clic réel tombe au milieu du mot) — l'ancre
        // « AppCompatActiv » laisse « ity » après le caret.
        val params = HoverParams(doc, positionApres(sourceMain, "extends AppCompatActiv"))
        val survol = boucle!!.client.textDocumentService.hover(params).get(30, TimeUnit.SECONDS)
        println("[BANC] S14 hover(AppCompatActivity) → ${survol?.contents?.toString()?.take(160)}")
        assertTrue(
            survol != null,
            "le survol d'AppCompatActivity (AAR réel) rend un contenu — reçu : null",
        )
    }

    // ---------------------------------------------------------------- //
    // S15 — definition : la cible binaire de l'AAR réel
    // ---------------------------------------------------------------- //

    @Test
    @Order(17)
    fun S15_definition_sur_appcompatactivity_rend_une_cible() {
        val doc = document!!
        // Position MI-MOT — le clic réel de l'utilisateur.
        val params = DefinitionParams(doc, positionApres(sourceMain, "extends AppCompatActiv"))
        val reponse = boucle!!.client.textDocumentService.definition(params).get(30, TimeUnit.SECONDS)
        val cibles = reponse?.left ?: emptyList()
        println("[BANC] S15 definition(AppCompatActivity) → ${cibles.size} cible(s) : ${cibles.take(2)}")
        assertTrue(
            cibles.isNotEmpty(),
            "la définition d'AppCompatActivity rend une cible (jar appcompat) — reçu : ${cibles.size}",
        )
    }

    // ---------------------------------------------------------------- //
    // S16 — frappe continue : la cohérence des préfixes croissants
    // (l'« incohérence » des retours : 0 items puis 108 au même endroit)
    // ---------------------------------------------------------------- //

    @Test
    @Order(18)
    fun S16_frappe_continue_coherence_de_prefixe() {
        // La séquence EXACTE des logs appareil (t+539–553 s) : le mot-clé
        // import se tape caractère par caractère, PUIS le paquet suit. La
        // ligne porte le TEXTE TAPÉ ; l'ancre « ;\n\n » + tapé désigne
        // sans ambiguïté la fin de la ligne en cours de frappe (le « ti »
        // de « monip » ne doit plus capturer l'ancre — le bug de position
        // du premier run).
        val suffixe = sourceMain.substringAfter("import android.os.Bundle;")
        var dernierNonVide: Int? = null
        var incoherences = 0
        val tape = listOf("i", "im", "imp", "impo", "import", "import ", "import a", "import an", "import andro")
        for (frappe in tape) {
            val source = "package com.example.monip;\n\n$frappe\n$suffixe"
            frapper(source)
            val libelles = completionApres(source, ";\n\n$frappe").map { it.label }
            println("[BANC] S16 « ${frappe.replace(" ", "␣")} » → ${libelles.size} items ${libelles.take(6)}")
            if (frappe == "import ") {
                // « import » + espace : les paquets de premier niveau
                // doivent paraître (c'était VIDE sur l'appareil).
                assertTrue(
                    "android" in libelles,
                    "« import ␣ » propose le paquet racine « android » — reçu : ${libelles.take(10)}",
                )
                dernierNonVide = libelles.size
            } else if (frappe.startsWith("import a")) {
                // Cohérence : la famille des paquets « a* » ne devient pas
                // VIDE en cours de frappe (l'incohérence 0 → 108 → 0 des
                // logs).
                if (libelles.isEmpty() && dernierNonVide != null) {
                    incoherences++
                    println("[BANC] S16 ⚠ frappe « $frappe » : 0 items après une réponse non vide ($dernierNonVide)")
                }
            }
        }
        assertTrue(
            incoherences == 0,
            "aucune incohérence de frappe (réponse vide après réponse non vide) — $incoherences observée(s)",
        )
    }

    // ---------------------------------------------------------------- //
    // S17 — latences : le froid vs le chaud (les « retards » des retours)
    // ---------------------------------------------------------------- //

    @Test
    @Order(19)
    fun S17_latences_chaud_et_frappe_continue() {
        // Le CHAUD (le régime de croisière de l'éditeur) : 20 répétitions
        // de la même position — le p95 doit rester réactif. Le FROID
        // complet (reconstruction après sync) est mesuré en S02b.
        frapper(sourceMain)
        val doc = document!!
        fun uneCompletion(source: String, ancre: String): Long {
            val params = CompletionParams()
            params.textDocument = doc
            params.position = positionApres(source, ancre)
            val debut = System.nanoTime()
            boucle!!.client.textDocumentService.completion(params).get(60, TimeUnit.SECONDS)
            return (System.nanoTime() - debut) / 1_000_000
        }
        val mesures = (1..20).map { uneCompletion(sourceMain, "binding.t") }.sorted()
        val p50 = mesures[10]
        val p95 = mesures[(mesures.size * 0.95).toInt().coerceAtMost(mesures.size - 1)]
        println("[BANC] S17 chaud : p50=${p50}ms p95=${p95}ms (min=${mesures.first()} max=${mesures.last()})")

        // La FRAPPE CONTINUE (le flux réel de l'éditeur) : didChange PUIS
        // complétion immédiate, préfixe croissant — chaque aller-retour
        // doit rester fluide (c'est le « retard dans les suggestions »).
        val suffixe = sourceMain.substringAfter("import android.os.Bundle;")
        val frappes = listOf("import a", "import an", "import andr", "import andro", "import androi", "import android")
        var pire = 0L
        for (frappe in frappes) {
            val source = "package com.example.monip;\n\n$frappe\n$suffixe"
            frapper(source)
            val duree = uneCompletion(source, ";\n\n$frappe")
            pire = maxOf(pire, duree)
            println("[BANC] S17 frappe « ${frappe} » → ${duree}ms")
        }
        println("[BANC] S17 frappe continue : pire=${pire}ms")

        // Bornes généreuses (JVM du banc — l'appareil ART est 3–5× plus
        // lent) : le CHAUD et la frappe doivent rester sous la seconde,
        // sinon c'est le « retard » du propriétaire.
        assertTrue(
            p95 < 1_000,
            "le complément CHAUD reste réactif (p95 < 1000 ms) — reçu : p95=$p95 ms",
        )
        assertTrue(
            pire < 1_000,
            "la complétion au fil de la frappe reste fluide (< 1000 ms) — reçu : pire=$pire ms",
        )
    }

    // ---------------------------------------------------------------- //
    // S18 — la frappe en cours (mot incomplet) : le repli honnête
    // ---------------------------------------------------------------- //

    @Test
    @Order(19)
    fun S18_frappe_en_cours_le_repli_reste_utile() {
        // La frappe réelle des logs : « liaiso » pas fini, syntaxe ouverte
        // — la complétion doit rester UTILE (locales, membres, mots-clés),
        // jamais silencieuse.
        val source = sourceMain.replace("private Greeter liaison = new Greeter();", "private Greeter liaiso = new Greeter();")
            .replace("titre.setText(liaison.saluer(\"monip\"));", "titre.setText(liaiso.s);")
        frapper(source)
        val libelles = completionApres(source, "liaiso.s").map { it.label }
        println("[BANC] S18 liaiso.s| (frappe en cours) → ${libelles.size} items : ${libelles.take(16)}")
        assertTrue(
            libelles.isNotEmpty(),
            "la frappe en cours rend des items (repli utile, jamais silencieux) — reçu : ${libelles.take(10)}",
        )
    }

    // ---------------------------------------------------------------- //
    // S19 — les MOTS-CLÉS de top level : `i|`, `im|` AVANT l'espace
    // (v1.1.6 — l'écart §13.4 : la frappe du mot-clé lui-même rendait
    // 0 item là où Android Studio propose import/interface)
    // ---------------------------------------------------------------- //

    @Test
    @Order(20)
    fun S19_motsClesTopLevelPendantLaFrappeDuMotCle() {
        val suffixe = sourceMain.substringAfter("import android.os.Bundle;")
        for (frappe in listOf("i", "im", "imp")) {
            val source = "package com.example.monip;\n\n$frappe\n$suffixe"
            frapper(source)
            val libelles = completionApres(source, ";\n\n$frappe").map { it.label }
            println("[BANC] S19 « $frappe » top level → ${libelles.size} items ${libelles.take(6)}")
            assertTrue(
                "import" in libelles,
                "« $frappe| » en top level propose « import » (le popup Android Studio) — reçu : ${libelles.take(10)}",
            )
        }
        // La complétion de NOM en corps de méthode n'est PAS devenue
        // mots-clés-seuls : la locale en tête (v1.1.5 tenu).
        val source = sourceMain.replace(
            "titre.setText(liaison.saluer(\"monip\"));",
            "titre.setText(liaison.saluer(\"monip\"));\n        int compteur = 0;\n        com¦",
        ).replace("¦", "")
        frapper(source)
        val libelles = completionApres(source, "compteur = 0;\n        com").map { it.label }
        println("[BANC] S19 corps de méthode com| → ${libelles.size} items ${libelles.take(6)}")
        assertTrue(
            "compteur" in libelles,
            "en corps de méthode, la locale reste en tête (pas de régression v1.1.5) — reçu : ${libelles.take(10)}",
        )
    }

    // ---------------------------------------------------------------- //
    // S20 — l'AIDE DE SIGNATURE sur une méthode du PROJET, arguments en
    // cours (v1.1.6 — la virgule traînante rendait RIEN : la fermeture
    // de la liste s'insérait après les accolades englobantes)
    // ---------------------------------------------------------------- //

    @Test
    @Order(21)
    fun S20_signatureMethodeProjetArgumentsEnCours() {
        // Deux arguments, le second en cours de frappe (virgule traînante)
        // — la méthode à DEUX paramètres du Greeter du PROJET.
        val source = sourceMain.replace(
            "titre.setText(liaison.saluer(\"monip\"));",
            "titre.setText(liaison.repeter(\"monip\", ",
        )
        frapper(source)
        val doc = document ?: fail("document non ouvert")
        val params = org.eclipse.lsp4j.SignatureHelpParams()
        params.textDocument = doc
        params.position = positionApres(source, "repeter(\"monip\", ")
        val aide = boucle!!.client.textDocumentService.signatureHelp(params).get(60, TimeUnit.SECONDS)
        val labels = aide?.signatures?.map { it.label } ?: emptyList()
        println("[BANC] S20 repeter(\"monip\", → ${labels.size} signature(s) : $labels (actif=${aide?.activeParameter})")
        assertTrue(
            labels.isNotEmpty(),
            "la virgule traînante rend l'aide de signature (niveau 4 réparé) — reçu : rien",
        )
        assertTrue(
            labels.any { it.contains("repeter") && it.contains("int") },
            "la signature du PROJET repeter(String, int) est servie — reçu : $labels",
        )
        assertTrue(
            labels.any { it.contains(" fois") },
            "le nom de paramètre de la SOURCE du projet paraît — reçu : $labels",
        )
    }
}
