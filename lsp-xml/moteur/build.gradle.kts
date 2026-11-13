// Module :xml — moteur d'analyse XML Android de CodeIDE (MIGRATION du
// module :lsp:xml du dépôt jjoblab/codeide-lsp — décision propriétaire du
// 13/11/2026 : « migrer le lsp xml de codeide-lsp vers code-lsp ainsi que
// les modules qui lui sont nécessaires comme host », code-lsp devient le
// dépôt officiel des LSP, codeide-lsp est archivé et rendu privé).
//
// Le code est repris TEL QUEL de codeide-lsp (lsp/xml, XE1) : parseur
// tolérant écrit main, DOM neutre, règles pures, complétion par
// contributeurs — PAS de Lemminx, PAS de XSD. Registre de provenance :
// lsp-xml/docs/PROVENANCE-REPRISE-XML.md (R9.2 — inscription préalable).
//
// ENTITÉ INDÉPENDANTE (miroir :kotlin) : aucune dépendance vers un module
// CodeIDE — le module reste découplable en « pack langue » téléchargeable ;
// l'app ne voit que le SPI mince jo.lspxml.api.MoteurLangageXml derrière
// ServiceLoader + le descripteur de pack jo/lspxml/pack/version.properties.
//
// Dépendances : RIEN (Kotlin JVM pur, zéro bibliothèque — le parseur est
// écrit main précisément pour ça : aucun XmlPullParser/DOM tiers sur le
// chemin critique de l'édition, comportement identique JVM/ART).
//
// Tests : JUnit 4 (suite reprise telle quelle — 63 tests unitaires +
// corpus LayoutLib auto-skip honnête si le dépôt frère est absent).
plugins {
    kotlin("jvm")
    `maven-publish`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
}

tasks.withType<Test>().configureEach {
    useJUnit()
    // Corpus LayoutLib (dépôt frère, LSP_XML §7) : les fixtures de
    // validation deviennent corpus du parseur — absent (CI sans frère)
    // : la suite S'AUTO-SKIP honnêtement (patron PariteCorpusDepotTest).
    System.getProperty("corpus.racine")
        ?.let { systemProperty("corpus.racine", it) }
}

// ---------------------------------------------------------------------------
// Publication GitHub Packages (miroir :java/:kotlin — même dépôt
// jjoblab/code-lsp, coordonnées jo.codeide.lsp.*) : le pack langue XML
// remplace la coordonnée com.github.jjoblab.codeide-lsp:xml de l'ancien
// dépôt — la bascule CodeIDE consomme jo.codeide.lsp.xml:jo-codeide-lsp-xml.
// ---------------------------------------------------------------------------
val depotGithubPackages = uri("https://maven.pkg.github.com/jjoblab/code-lsp")
val utilisateurPackages =
    providers.gradleProperty("github.packages.user")
        .orElse(providers.environmentVariable("GITHUB_ACTOR"))
val jetonPackages =
    providers.gradleProperty("github.packages.pat")
        .orElse(providers.environmentVariable("GITHUB_TOKEN"))

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
        create<MavenPublication>("pack") {
            from(components["java"])
            groupId = "jo.codeide.lsp.xml"
            artifactId = "jo-codeide-lsp-xml"
            version = project.version.toString()

            pom {
                name.set("Moteur XML LSP de CodeIDE (pack langue)")
                description.set(
                    "Moteur d'analyse XML Android de CodeIDE — parseur tolérant écrit " +
                        "main, DOM neutre, règles pures, complétion par contributeurs " +
                        "(catalogue plateforme + index ressources). Aucune dépendance.",
                )
            }
        }
    }
}
