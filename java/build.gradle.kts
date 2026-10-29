// Module :java — moteur Java LSP de CodeIDE (RÉÉCRITURE INTÉGRALE en Kotlin, ADR 0001).
// Étage N1 — noyau compilateur & environnement (docs/PLAN-REECRITURE.md §4) :
// pilotage d'ecj PATCHÉ via :ecj-art, routeur de classpaths, ordonnanceur et
// annulation, diagnostics de base et repli syntaxe instantané, caches
// parse/locate/présence, lecture zip/classpath. Les sources naissent du cahier
// de spécifications (docs/cahier-specifications/) — jamais du vendored.
//
// Règles : Kotlin 100 %, KDoc français détaillé, explicitApi (AGENTS.md).
// Bytecode 17 (consommable par la chaîne Android/ART d8 — convention dépôt).
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

tasks.test {
    useJUnitPlatform()
    // Tas borné comme les autres modules (convention CI 4 Go).
    maxHeapSize = "640m"
}

dependencies {
    // Le moteur pilote le jar ecj PATCHÉ produit par :ecj-art (build-time,
    // ADR 0005/0018) : classes ecj + shims ART embarqués. compileOnly en
    // production (l'hôte fournit le jar) ; testImplementation pour la suite
    // (les tests du cahier consomment le jar patché — fiche 013).
    compileOnly(project(mapOf("path" to ":ecj-art", "configuration" to "sortieEcjArt")))
    testImplementation(project(mapOf("path" to ":ecj-art", "configuration" to "sortieEcjArt")))

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}

// Banc d'empreinte et de latence du noyau N1 (critère de sortie de l'étage,
// PLAN-REECRITURE §4 : « banc empreinte/latence en CI »). Source set séparé :
// JAMAIS dans la chaîne de vérification (la tâche :java:bench l'exécute en
// JVM séparée, même convention que :index:bench).
val bancSourceSet =
    sourceSets.create("bench") {
        java.srcDir("src/bench/kotlin")
        compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
        runtimeClasspath += sourceSets.main.get().output + sourceSets.main.get().runtimeClasspath
    }

dependencies {
    // Le banc exécute le moteur CONTRE le jar ecj patché (comme les tests —
    // main est compileOnly, le runtime du banc doit le porter).
    "benchImplementation"(project(mapOf("path" to ":ecj-art", "configuration" to "sortieEcjArt")))
}

tasks.register<JavaExec>("bench") {
    group = "verification"
    description = "Banc d'empreinte et de latence du noyau N1 (p50/p95 + tas) — nombres à comparer entre versions."
    mainClass.set("jo.codeide.lsp.java.banc.BancNoyauKt")
    classpath = bancSourceSet.runtimeClasspath
    // JVM bornée comme les bancs du domaine (-Xmx2g, SerialGC — mesures
    // comparables d'une étape à l'autre).
    jvmArgs("-Xmx2g", "-XX:+UseSerialGC")
}

// Banc de QUALITÉ de complétion (étage N2 — critère de sortie, ADR 0016) :
// recall/top-1/MRR + p95 + zéro saut sur corpus versionné, comparés à la
// baseline JSON signée (bancs/baselines/completion.json) — la tâche ÉCHOUE
// sur régression (CI bloquante, ADR 0016 option 1). Re-signature : PR
// dédiée avec -Presigner.
tasks.register<JavaExec>("benchQualite") {
    group = "verification"
    description = "Banc de qualité de complétion N2 (recall/top-1/MRR vs baseline signée) — échoue sur régression."
    mainClass.set("jo.codeide.lsp.java.banc.BancQualiteCompletionKt")
    classpath = bancSourceSet.runtimeClasspath
    jvmArgs("-Xmx2g", "-XX:+UseSerialGC")
    systemProperty("banc.baseline", rootProject.file("bancs/baselines/completion.json").absolutePath)
    if (project.hasProperty("resigner")) {
        args("--resigner")
    }
}
