// Module :testing — suite d'intégration moteur ↔ index disque (ADR 0016,
// étage N1 de docs/PLAN-REECRITURE.md §4 : « reprise contrôlée de :testing —
// elle éprouve le noyau »).
//
// L'ANCIENNE suite de codeide-lsp:lsp/testing (4 fichiers Java, couplés aux
// membres package-private du moteur vendored) est MUTÉE en réécriture
// Kotlin : les MÊMES buts d'intégration (parité avec/sans index, autorité
// du localisateur à PRÊT, réouverture chaude, transition observée), sur
// l'API PUBLIQUE du moteur neuf — décision consignée au registre de
// provenance (docs/PROVENANCE-REPRISE.md). L'adaptateur vit AU-DESSUS des
// deux modules qu'il relie (même schéma que le futur hôte :lsp:host).
plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
    explicitApi()
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    // Le moteur neuf et l'index disque — dépendances de test uniquement :
    // le branchement de production (host) reprendra cet adaptateur.
    api(project(":java"))
    api(project(":index"))
    api(project(":model"))

    // Surveillance du flux d'état de la vue (transition CONSTRUCTION → PRÊT).
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")

    // Le moteur s'exécute contre le jar ecj PATCHÉ en test (compileOnly en
    // production — l'hôte fournit le jar, cf. :java/build.gradle.kts).
    testImplementation(project(mapOf("path" to ":ecj-art", "configuration" to "sortieEcjArt")))

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
}

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "640m"
}
