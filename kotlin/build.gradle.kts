import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion

// Module :kotlin — moteur Kotlin LSP de CodeIDE (voie C hybride, ADR 0100
// ACCEPTÉE le 2026-10-30 ; stratégie sémantique (i), ADR 0103 acceptée).
//
// ÉTAGE 0 — AMORÇAGE (plan §4 de la synthèse, docs/etudes/moteur-kotlin/) :
//   - grammaire officielle vendorée par RÉ-OBTENTION AMONT (tag v2.3.0,
//     R9.2 — jamais depuis codeide-lsp) dans `src/main/vendor/` ;
//   - suite de parité « grammaire » (fiches 010–012 du cahier
//     docs/cahier-specifications-kotlin/) : corpus du dépôt sans marqueur
//     d'erreur + digeste structurel + tolérance aux fautes + fixtures ;
//     la fiche 013 (façade `Kt*`) est l'étage 1 — périmètre documenté ;
//   - descripteur de pack langue (contrat D6, ADR 0101) généré ;
//   - banc d'empreinte/latence (nombres, aucun seuil — miroir :java:bench).
//
// Les étages 1+ (noyau parse/session/classpath, résolution, diagnostics,
// complétion, navigation, serveur LSP) naîtront du CAHIER — le code de
// l'ancien moteur (codeide-lsp:lsp/kotlin) n'est JAMAIS lu pour le noyau
// (clean-room, AGENTS.md) ; les modules différenciants repris (voie C) sont
// inscrits au préalable au manifeste docs/PROVENANCE-REPRISE-KOTLIN.md.
//
// Règles : Kotlin 100 %, KDoc français détaillé, explicitApi sur le code
// neuf (le vendoring en est EXEMPTÉ — compilation séparée ci-dessous,
// miroir ADR 0115.4 ancien consigné ADR 0104). Bytecode 17 (consommable
// par la chaîne Android/ART d8 — convention dépôt).
plugins {
    kotlin("jvm")
}

// ---------------------------------------------------------------------------
// Version de la grammaire vendorée — ÉPINGLAGE UNIQUE (ADR 0121).
// ---------------------------------------------------------------------------
// Règle d'épinglage : tag compilé par un bootstrap ≤ notre chaîne d'outils
// (v2.3.0 est compilé par le bootstrap 2.2.x ; notre chaîne est KGP 2.2.10).
// Toute montée passe par scripts/vendor-grammaire-kotlin.sh (remplacement
// intégral) + gates (suite de parité + re-signature digeste + revue du diff).
val VERSION_GRAMMAIRE = "v2.3.0"

// ---------------------------------------------------------------------------
// Version du JDK de la toolchain — ÉPINGLAGE UNIQUE (exigence appareil).
// ---------------------------------------------------------------------------
// L'appareil de mesure (banc-appareil, Termux) exécute openjdk 17 : depuis
// l'incident du 30/10 (java.util.List.removeLast JDK 21 dans le bytecode
// 61 — NoSuchMethodError appareil invisible en CI JDK 21), le module
// compile, TESTE et MESURE sous le MÊME JDK que l'appareil (17) : la
// compilation y rend invisibles les API JDK 21 (retombée sur le chemin
// sûr) et les erreurs de cette famille échouent la CI, plus l'appareil.
val VERSION_JDK = 17

// ---------------------------------------------------------------------------
// Grammaire vendorée : compilation SÉPARÉE (exemption explicitApi).
// ---------------------------------------------------------------------------
// Les sources amont (org.jetbrains.kotlin.kmp.*, Apache-2.0, octet pour
// octet — VENDOR.md) n'utilisent pas les modificateurs de visibilité
// explicites : les compiler avec `explicitApi()` échouerait. Le code neuf
// (src/main/kotlin) compile lui AVEC explicitApi Strict (tâche compileKotlin
// ci-dessous) — l'exemption est donc limitée au vendoring par construction.
val grammaireVendor = sourceSets.create("vendor") {
    kotlin.srcDir("src/main/vendor")
}

sourceSets.named("main") {
    // Le moteur voit la grammaire vendorée au classpath de compilation.
    compileClasspath += grammaireVendor.output
    runtimeClasspath += grammaireVendor.output
}

sourceSets.named("test") {
    compileClasspath += grammaireVendor.output
    runtimeClasspath += grammaireVendor.output
}

kotlin {
    // Toolchain 17 — EXIGENCE APPAREIL (banc-appareil du 30/10, moto g06 /
    // Termux openjdk 17.0.19) : la grammaire vendorée appelle
    // MutableList.removeLast() (gen/KotlinFlexLexer.kt:774) ; compilée sous
    // JDK 21, le MEMBRE java.util.List.removeLast (JDK 21 SEUL) prime sur
    // l'extension kotlin.collections et se retrouve DANS le bytecode 61
    // quel que soit jvmTarget (vérifié par expérience : -jvm-target 17 et 21
    // émettent l'un et l'autre l'InterfaceMethodref) — NoSuchMethodError
    // sur l'openjdk 17 de l'appareil, invisible en bac à sable/CI JDK 21
    // (la méthode y existe). La compilation SOUS JDK 17 rend le membre
    // INVISIBLE à la résolution (retombée sur le chemin sûr) ET fait
    // exécuter tests et bancs SUR 17 : cette classe d'erreur devient un
    // ÉCHEC CI, pas une panne appareil. Gardes complémentaires :
    // CompatibiliteJdk17Test (pool de constantes du code compilé) et
    // scan-art.py (MOTIFS_MEMBRES famille SequencedCollection — la CI
    // scanne le jar assemblé).
    jvmToolchain(VERSION_JDK)
    compilerOptions {
        // Même cible que les modules du dépôt (bytecode consommable par d8/ART).
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// explicitApi STRICT pour le code neuf UNIQUEMENT (la compilation « vendor »
// ci-dessus n'est pas touchée) — convention AGENTS.md, comme :java.
tasks.named<KotlinCompile>("compileKotlin") {
    compilerOptions {
        explicitApiMode.set(ExplicitApiMode.Strict)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// Le jar du module embarque la grammaire vendorée + le code neuf (le pack
// langue Kotlin est UN artefact — ADR 0101) ET les notices du vendoring
// sous META-INF/vendor-kotlin/ (Apache-2.0 : la licence accompagne la
// redistribution — miroir du registre META-INF/ECJ-ART-MODIFICATIONS.txt
// de :ecj-art, ADR 0120 ; l'ancien artefact publié n'embarquait RIEN,
// la présente voie corrige cela dès l'étage 0).
tasks.jar {
    from(grammaireVendor.output)
    from(layout.projectDirectory.dir("src/main/vendor")) {
        include("VENDOR.md", "LICENSE.txt", "COPYRIGHT.txt")
        into("META-INF/vendor-kotlin")
    }
}

tasks.test {
    useJUnitPlatform()
    // Convention CI du dépôt (tas borné).
    maxHeapSize = "640m"
    // Passage des propriétés de la suite de parité (fiche 010) vers la JVM
    // de test : -D est lu par la JVM de BUILD, il faut le transporter.
    //   -Dparite.majBase=true       → re-signature de la baseline digeste
    //     (avec --rerun-tasks OBLIGATOIRE — cf. KDoc de PariteCorpusDepotTest) ;
    //   -Dparite.racineCorpus=chemin → corpus externe (sweep d'un checkout).
    systemProperty("parite.majBase", providers.systemProperty("parite.majBase").getOrElse("false"))
    systemProperty("parite.racineCorpus", providers.systemProperty("parite.racineCorpus").getOrElse(""))
    testLogging {
        events("failed")
    }
}

// ---------------------------------------------------------------------------
// Descripteur de pack langue (contrat D6 ancien, ADR 0101) — GÉNÉRÉ.
// ---------------------------------------------------------------------------
// id/version/minApp/grammaire : valeurs RÉELLES (fiche 001 : « valeurs
// réelles pas des replis ») — la version vient du projet racine, la version
// de grammaire est ÉPINGLÉE par VERSION_GRAMMAIRE (unique point de vérité,
// miroir de genererVersionProps de :java). Les noms de clés seront
// verrouillés par le test du SPI (fiche 001, étage 6) ; minApp est la
// version d'app du premier consommateur potentiel du pack (bascule Java
// §8.5, 1.0.0).
val dossierPackGenere = layout.buildDirectory.dir("version-pack")

val genererDescripteurPack = tasks.register("genererDescripteurPack") {
    description = "Génère le descripteur de pack langue (version.properties, contrat D6) depuis la version du projet."
    inputs.property("version", project.version)
    inputs.property("versionGrammaire", VERSION_GRAMMAIRE)
    outputs.dir(dossierPackGenere)
    doLast {
        val cible = dossierPackGenere.get()
            .dir("jo/codeide/lsp/kotlin/pack").asFile
            .resolve("version.properties")
        cible.parentFile.mkdirs()
        cible.writeText(
            """
            |# Généré par :kotlin:genererDescripteurPack — NE PAS ÉDITER (contrat D6).
            |id=jo-codeide-lsp-kotlin
            |version=${project.version}
            |minApp=1.0.0
            |grammaire=${VERSION_GRAMMAIRE}
            """.trimMargin() + "\n",
        )
    }
}

sourceSets.main {
    resources.srcDir(dossierPackGenere)
}

tasks.processResources {
    dependsOn(genererDescripteurPack)
}

dependencies {
    // Pile S2 de l'étude (ADR 0103 accepté) : le parseur officiel —
    // syntax-api (intellij-dependencies, Apache-2.0 sources ; épinglée par
    // le tag amont v2.3.0 de la grammaire ; conditions des BINAIRES à
    // confirmer par le propriétaire — alerte P-K2, miroir AL1 Java, avant
    // toute publication du pack) + transitifs lang-syntax/annotations.
    // Résolution : cache-redirector (settings.gradle.kts, FAIL_ON_PROJECT_REPOS).
    // Déclarée DEUX fois : la compilation « vendor » a SON classpath propre
    // (elle ne voit pas les dépendances de main) — la grammaire amont est
    // écrite CONTRE syntax-api.
    implementation("org.jetbrains:syntax-api:0.3.340")
    "vendorImplementation"("org.jetbrains:syntax-api:0.3.340")
    // annotations en portée COMPILE pour le vendoring : la grammaire amont
    // importe org.jetbrains.annotations.ApiStatus (le POM de syntax-api ne
    // la porte qu'en runtime — insuffisant pour compiler les sources).
    "vendorImplementation"("org.jetbrains:annotations:26.0.2")

    // Décodage @Metadata (vague 1d — ADR 0108 : le décodeur vit côté :kotlin,
    // kotlinx-metadata-jvm en artefact ; Q-K2 documente l'alternative maison).
    // Épinglée à la chaîne d'outils (manifeste §4 — monte avec elle, ADR 0121).
    // Empreinte mesurée avant adoption (30/10, bac à sable) : 492 classes /
    // 1 141 méthodes / class-file 49:81+52:411 / AUCUNE référence sensible
    // ART (ServiceLoader OK — descripteur MetadataExtensions vérifié) ; dex
    // pile complète mesuré : 24 372 méthodes / 3 728 244 o (seuils tenus).
    implementation("org.jetbrains.kotlin:kotlin-metadata-jvm:2.2.10")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")

    // ── Corpus de test de la vague 1d (fiches 002–005/008/009/034) ──────
    // Tous localisés au runtime par leurs ANCRES de classe, jamais résolus
    // en code (miroir de l'ancien dépôt — jars RÉELS du classpath de test).
    testImplementation("com.google.android:android:4.1.1.4")
    testImplementation("org.jetbrains.kotlinx:kotlinx-datetime-jvm:0.6.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.9.0")

    // ── Corpus de test de la vague 2d (fiche 033 — coroutines RÉELLES) ──
    // `collect` suspend extension sur Flow/SharedFlow : le jar véritable du
    // classpath de test, ancré par classe (kotlinx.coroutines.flow.Flow) —
    // AUCUN impact dex (test seul, jamais la pile chaude du moteur).
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.9.0")
}

// ---------------------------------------------------------------------------
// Banc d'empreinte/latence de la grammaire (étage 0) — NOMBRES SEULEMENT.
// ---------------------------------------------------------------------------
// Miroir de :java:bench : aucune assertion de seuil (les seuils arrivent
// avec les bancs de parité ADR 0119) — le banc produit des nombres à
// comparer entre versions : démarrage à froid, parse p50/p95 (corpus 500 et
// 3 000 lignes), volumétrie d'arbre, tas au repos, taille du jar.
val bancSourceSet =
    sourceSets.create("bench") {
        java.srcDir("src/bench/kotlin")
        compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
        runtimeClasspath += sourceSets.main.get().output + sourceSets.main.get().runtimeClasspath
    }

tasks.register<JavaExec>("bench") {
    group = "verification"
    description = "Banc d'empreinte et de latence de la grammaire (étage 0 — p50/p95 + tas + taille jar)."
    mainClass.set("jo.codeide.lsp.kotlin.banc.BancGrammaireKt")
    classpath = bancSourceSet.runtimeClasspath
    // Le banc s'exécute SUR la toolchain du module (JDK 17 — même JVM que
    // l'appareil Termux) : un banc JDK 21 a MASQUÉ l'incident du 30/10
    // (List.removeLast y existe) — les nombres du bac à sable doivent être
    // mesurés dans les CONDITIONS de l'appareil, pas seulement compilés
    // pour lui.
    javaLauncher.set(
        javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(VERSION_JDK)) },
    )
    // JVM bornée comme les bancs du domaine (-Xmx2g, SerialGC — mesures
    // reproductibles, miroir :java:bench / :index:bench).
    jvmArgs("-Xmx2g", "-XX:+UseSerialGC")
}
