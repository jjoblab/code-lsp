// Module :index — index disque CIDX en segments par jar (mission « LSP Java »,
// étape 2 — ADR 0111 de l'ancien dépôt ; reprise conservée par l'étude D7 :
// préfixe binaire, budget octets, corruption par segment — supérieur au design
// IntelliJ sur les axes mobiles).
//
// REPRISE CONTRÔLÉE vague 1b (28/10/2026) depuis codeide-lsp:lsp/index @ cb809e14
// — registre : docs/PROVENANCE-REPRISE.md §7 (R9.2). Fichiers de code repris
// TELS QUELS (aucune adaptation d'import — l'index ne touche pas au protocole).
//
// Principes portés tels quels (en-tête d'origine) : UN segment PAR JAR, clé =
// identité du contenu (empreintes W6 de :model), stockage partagé entre
// projets, fusion LSM à la requête, format binaire versionné (magique +
// version + crc32, corruption d'un segment ⇒ reconstruction de CE segment),
// construction en arrière-plan bornée et annulable, lecture par cache LRU de
// budget tas explicite, nettoyage des orphelins.
plugins {
    kotlin("jvm")
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
    // Entrées empreintées (ClasspathInstantane) — les clés de segments
    // dérivent des empreintes W6 de :model.
    api(project(":model"))

    // États/progression en flux (construction → prêt/partiel/erreur).
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")

    // Tests : JUnit 4 (cadre d'origine des tests repris — conservés tels quels).
    // Test différentiel du lecteur de membres : android.jar RÉEL (AOSP API 16,
    // Maven Central — le SDK Android est absent des environnements JVM).
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("com.google.android:android:4.1.1.4")
}

tasks.test {
    useJUnit()
    // Tas borné comme à l'origine (machine CI 4 Go — convention d'origine).
    maxHeapSize = "640m"
}

// Banc de mesure (ADR 0111 : « banc avant/après sur les trois jeux ») —
// source set exécuté par la tâche :index:bench (JavaExec, JVM séparée),
// jamais dans la chaîne de vérification. Recette de reconstitution des jeux :
// scripts/lsp-index/construire-jeux.sh DU DÉPÔT D'ORIGINE (codeide-lsp — non
// repris ; les jeux sont régénérables hors dépôt).
val benchSourceSet =
    sourceSets.create("bench") {
        java.srcDir("src/bench/kotlin")
        compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
        runtimeClasspath += sourceSets.main.get().output + sourceSets.main.get().runtimeClasspath
    }

tasks.register<JavaExec>("bench") {
    group = "verification"
    description = "Banc de mesure de l'index disque (ADR 0111) — jeux petit/moyen/lourd."
    mainClass.set("jo.codeide.lsp.index.banc.BancIndexKt")
    workingDir = rootProject.projectDir
    classpath = benchSourceSet.runtimeClasspath
    // La JVM du banc est bornée comme au banc d'étude (-Xmx2g, SerialGC —
    // mesures comparables d'une étape à l'autre).
    jvmArgs("-Xmx2g", "-XX:+UseSerialGC")
}
