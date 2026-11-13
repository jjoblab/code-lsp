// Module :host — l'hôte LSP de CodeIDE (MIGRATION du module :lsp:host du
// dépôt jjoblab/codeide-lsp — décision propriétaire du 13/11/2026 : « migrer
// le lsp xml de codeide-lsp vers code-lsp ainsi que les modules qui lui
// sont nécessaires comme host » ; registre lsp-host/PROVENANCE.md).
//
// Point UNIQUE de changement de transport : SessionLsp isole les moteurs
// derrière des frontières nettes — démarrer/arrêter, requêtes, flux d'état.
// Le branchement LSP4J in-process (cel-lsp, InProcessStreamConnectionProvider)
// en est UNE implémentation ; un transport par socket vers un sous-processus
// JDK reste possible sans réécriture du moteur.
//
// RESPONSABILITÉS (reprises telles quelles) :
//  - OrchestrateurLsp : ClasspathResult (protocole tooling) → ProjetLsp
//    (:model) → moteurs PAR module ; vue d'index FUSION sur l'union des
//    classpaths ; BUDGET de cache dérivé du modèle.
//  - AdaptateurIndexNoms : l'adaptateur de PRODUCTION de la SPI.
//  - SessionLsp/SessionLspKotlin/SessionLspXml : façades de cycle de vie
//    des TROIS moteurs (packs jo.codeide.lsp.java/kotlin de CE dépôt + SPI
//    jo.lspxml.api du module :xml ci-inclus).
//
// BIBLIOTHÈQUE ANDROID (AAR) : la suite E2E (ReproductionViewsE2ETest et
// les scénarios harnais) tourne sous Robolectric — le module RESTE une
// bibliothèque Android (com.android.library), miroir de l'ancien dépôt.
// Kotlin intégré d'AGP 9 (ADR 0007 de codeide-lsp, repris) : AUCUN plugin
// kotlin-android à appliquer — la configuration passe par l'extension
// `kotlin` (KotlinAndroidProjectExtension). La convention
// `codeide.android.library` de codeide-lsp n'existe pas ici : sa
// configuration (namespace, SDK, Java 17, Robolectric, lint strict,
// avertissements Kotlin en erreur) est INLINE, miroir exact.
plugins {
    id("com.android.library") version "9.4.1"
    `maven-publish`
}

android {
    namespace = "jo.codeide.lsp.host"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        compileSdkMinor = 2
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            // Manifeste et ressources fusionnés pour Robolectric.
            isIncludeAndroidResources = true
        }
    }

    // Règle 9 (miroir convention) : lint strict, conseils de fraîcheur exclus.
    lint {
        warningsAsErrors = true
        abortOnError = true
        disable.add("NewerVersionAvailable")
        disable.add("GradleDependency")
        disable.add("AndroidGradlePluginVersion")
    }

    // Publication : variante unique release (AGP 9 — le composant « release »
    // de maven-publish n'existe qu'une fois la variante déclarée ici).
    publishing {
        singleVariant("release")
    }
}

// Kotlin intégré AGP 9 : avertissements en erreur, jvmTarget = 17 (valeur
// par défaut alignée sur compileOptions.targetCompatibility).
configure<org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension> {
    compilerOptions {
        allWarningsAsErrors.set(true)
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Robolectric doit refléter les internes du JDK récent (JPMS) ; miroir de
// la convention (docs/ENVIRONNEMENT.md de codeide-lsp,
// issue robolectric/robolectric#11434). Tas borné (machine CI 4 Go).
tasks.withType<Test>().configureEach {
    jvmArgs("--add-exports", "java.base/jdk.internal.access=ALL-UNNAMED")
    maxHeapSize = "640m"
    useJUnitPlatform()
    // Les sondes du moteur cherchent un JDK COMPLET (lib/src.zip,
    // lib/modules) : le JDK du démon Gradle l'est par construction.
    environment("JO_TEST_JDK_HOME", System.getProperty("java.home"))
}

dependencies {
    // Moteur Java RÉÉCRIT (pack jo.codeide.lsp.java de CE dépôt) : la façade
    // traverse la surface publique → api. Résolution LOCALE : la racine du
    // build SUBSTITUE la coordonnée par le project :java (resolutionStrategy
    // — le POM publié garde la coordonnée, consommateur : CodeIDE).
    api("jo.codeide.lsp.java:jo-codeide-lsp-java:${project.version}")

    // Moteur Kotlin RÉÉCRIT (pack jo.codeide.lsp.kotlin de CE dépôt) : le
    // serveur LSP4J et le SPI traversent la surface publique → api.
    // Résolution locale par substitution (racine).
    api("jo.codeide.lsp.kotlin:jo-codeide-lsp-kotlin:${project.version}")

    // Moteur XML (pack jo.codeide.lsp.xml de CE dépôt — migration) : le SPI
    // MoteurLangageXml traverse la surface publique (paramètre du
    // constructeur SessionLspXml, injectable pour les tests) → api.
    // Résolution locale par substitution (racine).
    api("jo.codeide.lsp.xml:jo-codeide-lsp-xml:${project.version}")

    // Modèle LSP (ProjetLsp/DepotModeleLsp) — surface publique → api.
    // Résolution locale par substitution (racine).
    api("jo.codeide.lsp.model:jo-codeide-lsp-model:${project.version}")

    // Index disque : détail interne (adaptateur SPI) — implementation.
    // Résolution locale par substitution (racine).
    implementation("jo.codeide.lsp.index:jo-codeide-lsp-index:${project.version}")

    // Protocole tooling (ClasspathResult traverse la surface publique de
    // l'OrchestrateurLsp) → api. Résolution : build composite du frère
    // codeide-tooling (settings.gradle.kts — substitution des coordonnées,
    // miroir du mécanisme de l'ancien dépôt).
    api("com.github.jjoblab.codeide-tooling:protocol:1.2.0")

    // lsp4j : le câble du serveur (le POM de cel-lsp l'impose en 0.22.0 ;
    // déclaré explicite — la surface publique de SessionLsp l'expose).
    api("org.eclipse.lsp4j:org.eclipse.lsp4j:0.22.0")
    api("org.eclipse.lsp4j:org.eclipse.lsp4j.jsonrpc:0.22.0")

    // cel-lsp (AAR LSP4J du dépôt code-editor) : DefinitionServeurLspJava
    // traverse la surface publique — JitPack (build autonome sans jeton).
    api("com.github.jjoblab.code-editor:cel-lsp:3.74.0")

    // Coroutines : impl du service d'index + collecte du StateFlow d'état.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")

    // NOTE compagnon ecj-art : le jar ecj patché (compagnon runtime du
    // moteur Java) voyage dans le POM en scope runtime (ajouté par withXml
    // de la publication ci-dessous) — PAS en dépendance résolue : :ecj-art
    // de CE dépôt n'expose pas le compagnon par sa coordonnée (son artefact
    // par défaut est le PATCHeur). Les TESTS consomment le jar patché par
    // la configuration dédiée (miroir exact de :java).
    testImplementation(project(mapOf("path" to ":ecj-art", "configuration" to "sortieEcjArt")))
    testImplementation(project(":index"))

    // Tests : Jupiter + coroutines-test + Robolectric (E2E harnais).
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("org.robolectric:robolectric:4.17")
}

// ---------------------------------------------------------------------------
// Publication GitHub Packages (miroir :java/:kotlin — même dépôt
// jjoblab/code-lsp) : l'AAR hôte remplace la coordonnée
// com.github.jjoblab.codeide-lsp:host de l'ancien dépôt — la bascule
// CodeIDE consomme jo.codeide.lsp.host:jo-codeide-lsp-host.
// AGP 9 : le composant « release » n'est créé qu'en afterEvaluate.
// ---------------------------------------------------------------------------
val depotGithubPackages = uri("https://maven.pkg.github.com/jjoblab/code-lsp")
val utilisateurPackages =
    providers.gradleProperty("github.packages.user")
        .orElse(providers.environmentVariable("GITHUB_ACTOR"))
val jetonPackages =
    providers.gradleProperty("github.packages.pat")
        .orElse(providers.environmentVariable("GITHUB_TOKEN"))

afterEvaluate {
    publishing {
        repositories {
            maven {
                name = "GitHubPackages"
                url = depotGithubPackages
                credentials {
                    username = utilisateurPackages.orNull ?: ""
                    password = jetonPackages.orNull ?: ""
                }
            }
        }
        publications {
            create<MavenPublication>("maven") {
                from(components["release"])
                groupId = "jo.codeide.lsp.host"
                artifactId = "jo-codeide-lsp-host"
                version = project.version.toString()

                pom {
                    name.set("Hôte LSP de CodeIDE (AAR)")
                    description.set(
                        "Hôte LSP de CodeIDE — orchestration des moteurs Java, " +
                            "Kotlin et XML (packs langue de jjoblab/code-lsp), " +
                            "branchement LSP4J in-process via cel-lsp.",
                    )

                    // Compagnon ecj-art (runtime scope — MÊME POM que l'ancien
                    // :lsp:host de codeide-lsp) : le jar ecj 3.40.0 patché ART
                    // voyage avec l'hôte, le moteur restant ecj-less par
                    // construction. Ajout manuel dans le nœud dependencies
                    // EXISTANT du POM (l'AAR en produit déjà un — un second
                    // nœud rendrait le POM invalide) : la dépendance n'est PAS
                    // résolue localement (cf. NOTE compagnon ci-dessus).
                    withXml {
                        val noeud = asNode()
                        // Le POM Maven est GÉNÉRÉ AVEC ESPACE DE NOMS (QName
                        // {http://maven.apache.org/POM/4.0.0}dependencies) — la
                        // comparaison porte sur la partie LOCALE du nom.
                        val deps = noeud.children().filterIsInstance<groovy.util.Node>()
                            .firstOrNull { it.name().toString().substringAfter('}') == "dependencies" }
                            ?: noeud.appendNode("dependencies") as groovy.util.Node
                        deps.appendNode("dependency").apply {
                            appendNode("groupId", "jo.codeide.lsp.java")
                            appendNode("artifactId", "jo-codeide-lsp-java-ecj-art")
                            appendNode("version", project.version.toString())
                            appendNode("scope", "runtime")
                        }
                    }
                }
            }
        }
    }
}
