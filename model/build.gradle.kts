// Module :model — modèle de projet LSP (snapshots immuables + empreintes W6).
//
// REPRISE CONTRÔLÉE vague 1b (28/10/2026) depuis codeide-lsp:lsp/model @ cb809e14
// — registre : docs/PROVENANCE-REPRISE.md §7 (R9.2). Aucun fichier de code
// modifié hormis les imports du protocole (voir manifeste).
//
// La reprise était CONDITIONNÉE à l'action propriétaire P1 (LICENSE du dépôt
// jjoblab/codeide-tooling — ADR 0001/0017). L'alternative consignée au registre
// §4 est appliquée : les ~4 types du protocole consommés sont REWRITS localement
// (jo.codeide.lsp.model.protocole, clean-room depuis la surface observable),
// ce qui débloque la reprise sans attendre P1. Retour à l'artefact officiel
// envisagé quand P1 sera résolu.
plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

kotlin {
    jvmToolchain(21)
    // Cible d'origine conservée (codeide.kotlin.library : JVM_17) — bytecode
    // consommable par la chaîne Android/ART du host (d8/desugaring).
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
    // Convention d'origine : API publique explicite (visibilité + KDoc).
    explicitApi()
}

// Cohérence Java ↔ Kotlin : même cible de bytecode (17) que le compilateur
// Kotlin ci-dessus — le toolchain 21 exécute la compilation (JDK 21, release 17).
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    // Persistance versionnée du modèle (lsp-modele.json) — api car les types
    // publics du modèle sont @Serializable.
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    // Flux d'état (publication par instantanés successifs).
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")

    // Tests : JUnit 4 (cadre d'origine des tests repris — conservés tels quels)
    // + coroutines-test pour runTest.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
}

tasks.test {
    useJUnit()
    // Tas borné comme à l'origine (machine CI 4 Go — convention d'origine).
    maxHeapSize = "640m"
}
