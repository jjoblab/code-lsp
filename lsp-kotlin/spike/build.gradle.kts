// spike-kotlin — module de mesure isolé (Phase 2 du prompt de mission).
//
// NON PUBLIÉ, NON COMPILÉ PAR DÉFAUT : ce module ne sert qu'aux mesures de
// l'étude « moteur Kotlin ». Ses dépendances sont des jars LOCAUX téléchargés
// hors dépôt (scripts/telecharger-jars.sh, R6.4 : jamais committés) — la CI
// du dépôt ne doit jamais tenter de le construire sans ces jars.
//
// Activation : ./gradlew :spike-kotlin:test -PspikeKotlin
// (sans -PspikeKotlin, TOUTES les tâches du module sont désactivées — la
// configuration reste valide, aucune dépendance n'est résolue.)
//
// Variantes mesurées (prompt §7) :
//   S0 — oracle : ancien moteur publié (lsp-kotlin-1.22.1.jar, GitHub
//        Packages), exécuté en boîte noire par le SPI public — aucune
//        source de l'ancien dépôt n'est lue (R9.1) ;
//   S1 — frontend K2 : kotlin-compiler-embeddable 2.2.10 (Analysis API
//        standalone N'EST PAS publiée sur Maven Central — constat de
//        l'étude, 01-architectures-analyse-kotlin.md §K2) ;
//   S2 — parseur officiel seul : grammaire vendorée DEPUIS L'AMONT
//        (src/main/vendor, R9.2 — jamais depuis codeide-lsp) + syntax-api ;
//   S3 — hybride : S2 en premier plan, coût d'accès K2 mesuré en S1.
plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(21)
    // Même cible que les modules du dépôt (bytecode consommable par d8/ART).
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
    // PAS d'explicitApi : module de mesure non publié. Le vendoring est
    // exempté par construction (voir src/main/vendor/VENDOR.md).
}

// Cohérence Java ↔ Kotlin : même cible de bytecode (17) que compileKotlin.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// ---------------------------------------------------------------------------
// Garde CI : sans -PspikeKotlin, aucune tâche de ce module ne s'exécute.
// ---------------------------------------------------------------------------
val spikeActif = project.hasProperty("spikeKotlin")
if (!spikeActif) {
    tasks.configureEach {
        onlyIf {
            logger.lifecycle("spike-kotlin : {} désactivée (module de mesure — activer avec -PspikeKotlin)", name)
            false
        }
    }
}

// ---------------------------------------------------------------------------
// Sources : production de mesure + vendoring amont (R9.2).
// ---------------------------------------------------------------------------
sourceSets {
    main {
        kotlin.srcDir("src/main/vendor")
    }
}

// Répertoire des jars téléchargés (HORS dépôt — R6.4). Par défaut : frère
// du dépôt (<dépôt>/../spike-jars) ; surchargeable : -PspikeJars=<chemin>.
val jars = rootProject.file(
    project.properties["spikeJars"] ?: "../spike-jars",
)
fun jarLocal(nom: String) = jars.resolve(nom)

dependencies {
    // Pile S2 — parseur officiel (syntax-api 0.3.340, épinglée par le tag
    // amont v2.3.0 de la grammaire vendorée ; ADR 0115 ancien).
    implementation(
        files(
            jarLocal("syntax-api-jvm-0.3.340.jar"),
            jarLocal("lang-syntax-jvm-0.3.340.jar"),
            jarLocal("annotations-26.0.2.jar"),
        ),
    )

    // Pile S0 — oracle en boîte noire (artefact PUBLIÉ de l'ancien moteur ;
    // usage de surface publique uniquement — miroir du rôle de consommateur
    // de l'hôte). Nécessaire aussi à l'exécution des tests S0.
    testImplementation(
        files(
            jarLocal("lsp-kotlin-1.22.1.jar"),
            jarLocal("kotlin-metadata-jvm-2.2.10.jar"),
        ),
    )

    // Pile S1 — frontend K2 embarqué (53,6 Mo mesurés au téléchargement).
    testImplementation(files(jarLocal("kotlin-compiler-embeddable-2.2.10.jar")))

    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    useJUnit()
    // Chemin des jars transmis aux bancs (résolu depuis la racine du dépôt).
    systemProperty("spike.jars", jars.absolutePath)
    // Les bancs S1 (K2) chargent des dizaines de Mo de classes : tas élargi.
    maxHeapSize = if (spikeActif) "2g" else "640m"
    // Les mesures s'impriment sur la console — montrer la sortie.
    testLogging {
        showStandardStreams = true
        events("passed", "failed", "skipped")
    }
    // Exclure rien par défaut ; les bancs détectent eux-mêmes la présence
    // des jars (assumeTrue) pour rester honnêtes quand une pile manque.
}
