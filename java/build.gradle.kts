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
    `maven-publish`
}

// ---------------------------------------------------------------------------
// Publication Maven — bascule §8.5 (décision signée le 30/10/2026), voie
// PRODUCTION GitHub Packages (docs/BASCULE-CODEIDE.md §3.2.1). Coordonnées
// FIXÉES ici (rien ne les épingle ailleurs) :
//   jo.codeide.lsp.java:jo-codeide-lsp-java:<version du dépôt>
//   → https://maven.pkg.github.com/jjoblab/code-lsp (workflow publish.yml,
//     déclenché sur tag v*).
// Le COMPAGNON RUNTIME (jar ecj patché de :ecj-art) est publié SOUS sa
// propre coordonnée par le module :ecj-art — le moteur reste ecj-less par
// construction (compileOnly : « l'hôte fournit le jar », ADR 0005) et la
// séparation de licences Apache-2.0 / EPL-2.0 est préservée (ADR 0017 :
// jamais d'ecj embarqué dans l'artefact moteur).
// Jetons : propriétés Gradle `githubPackagesUtilisateur` /
// `githubPackagesJeton`, sinon variables d'environnement GITHUB_ACTOR /
// GITHUB_TOKEN (fournies par Actions au publish) — JAMAIS codées en dur.
// publishToMavenLocal fonctionne sans jeton (dépôt local, aucun contact
// réseau) : c'est le chemin de test documenté (BASCULE-CODEIDE §3.2).
// ---------------------------------------------------------------------------
val depotGithubPackages = uri("https://maven.pkg.github.com/jjoblab/code-lsp")
val utilisateurPackages = providers.gradleProperty("githubPackagesUtilisateur")
    .orElse(providers.environmentVariable("GITHUB_ACTOR"))
val jetonPackages = providers.gradleProperty("githubPackagesJeton")
    .orElse(providers.environmentVariable("GITHUB_TOKEN"))

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
    // Sources publiées avec l'artefact (navigation IDE du consommateur —
    // la KDoc française du moteur y est lisible ; pas de jar javadoc :
    // dokka hors périmètre, GitHub Packages ne l'exige pas).
    withSourcesJar()
}

// Le jar sources voit le répertoire GENERE version-n8 (srcDir de resources
// du source set main) : dépendance explicite pour l'ordre (validation
// Gradle des sorties implicites) + exclusion de la ressource générée —
// version.properties est un artefact de construction (fiche 059 I7 :
// « NE PAS ÉDITER »), pas une source.
tasks.named<Jar>("sourcesJar") {
    dependsOn(genererVersionProps)
    exclude("**/version.properties")
}

tasks.test {
    useJUnitPlatform()
    // Tas borné comme les autres modules (convention CI 4 Go).
    maxHeapSize = "640m"
}

// ---------------------------------------------------------------------------
// Étage N8 — ressource de version du serveur (fiche 059, comportement I7 :
// serverInfo.version EXACTEMENT égale à la clé `version` de la ressource
// `.../protocole/version.properties` embarquée). La ressource est GÉNÉRÉE
// depuis la version du projet (racine) : toute dérive dépôt↔serveur est
// impossible par construction. La valeur fantôme « 1.2.0 » du vendored est
// proscrite par le cahier — jamais codée en dur ici.
// ---------------------------------------------------------------------------
val dossierVersionGeneree = layout.buildDirectory.dir("version-n8")

val genererVersionProps = tasks.register("genererVersionProps") {
    description = "Génère version.properties du serveur (fiche 059 I7) depuis la version du projet."
    // La version est une ENTRÉE : changer la version du projet régénère la
    // ressource (jamais de ressource périmée dans l'artefact).
    inputs.property("version", project.version)
    outputs.dir(dossierVersionGeneree)
    doLast {
        val cible = dossierVersionGeneree.get()
            .dir("jo/codeide/lsp/java/protocole").asFile
            .resolve("version.properties")
        cible.parentFile.mkdirs()
        cible.writeText(
            """
            |# Généré par :java:genererVersionProps — NE PAS ÉDITER (fiche 059 I7).
            |version=${project.version}
            """.trimMargin() + "\n",
        )
    }
}

sourceSets.main {
    resources.srcDir(dossierVersionGeneree)
}

tasks.processResources {
    dependsOn(genererVersionProps)
}

dependencies {
    // Le moteur pilote le jar ecj PATCHÉ produit par :ecj-art (build-time,
    // ADR 0005/0018) : classes ecj + shims ART embarqués. compileOnly en
    // production (l'hôte fournit le jar) ; testImplementation pour la suite
    // (les tests du cahier consomment le jar patché — fiche 013).
    compileOnly(project(mapOf("path" to ":ecj-art", "configuration" to "sortieEcjArt")))
    testImplementation(project(mapOf("path" to ":ecj-art", "configuration" to "sortieEcjArt")))

    // Étage N8 — couche protocole (cycle de vie LSP, protocole riche) :
    // liaisons Java publiques du protocole LSP (EPL-2.0, Maven Central —
    // NOTICE n° 3, ADR 0015). Bibliothèque TIERS comme ecj ou JUnit : le
    // clean-room du moteur (AGENTS.md) interdit de reprendre le CODE du
    // vendored, pas d'utiliser les mêmes bibliothèques publiques. Le
    // paquet `protocole` est le SEUL consommateur ; les paquets moteur
    // restent libres de lsp4j (convention « la conversion vers le protocole
    // est le travail de la couche hôte », TypesDiagnostics).
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.21.2")

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
        // Format d'échange partagé avec le pilote oracle (source of truth
        // unique du JSON corpus/résultats, sans dépendance).
        java.srcDir("src/formatBanc/kotlin")
        compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
        runtimeClasspath += sourceSets.main.get().output + sourceSets.main.get().runtimeClasspath
    }

// ---------------------------------------------------------------------------
// Étage T — banc de PARITÉ CROISÉE (ADR 0016) : le même corpus rejoué contre
// le vendored (oracle en boîte noire, SOUS-PROCESSUS) et le moteur neuf.
// Trois source sets dédiés :
//  - `bancStubs`     : STUBS des signatures publiques du vendored — servent
//                      UNIQUEMENT à compiler le pilote ; JAMAIS sur un
//                      classpath d'exécution (alarme au chargement) ;
//  - `piloteOracle`  : le pilote du sous-processus oracle — SANS AUCUNE
//                      dépendance du dépôt (classpath runtime = jar vendored
//                      + lsp4j/gson du vendored + kotlin-stdlib) ;
//  - `formatBanc`    : le format d'échange JSON maison, partagé par le pilote
//                      et l'orchestrateur (et par les tests du cahier).
// ---------------------------------------------------------------------------
val bancStubsSourceSet = sourceSets.create("bancStubs") {
    java.srcDir("src/bancStubs/kotlin")
}

val piloteOracleSourceSet = sourceSets.create("piloteOracle") {
    java.srcDir("src/piloteOracle/kotlin")
    java.srcDir("src/formatBanc/kotlin")
    compileClasspath += bancStubsSourceSet.output
}

// Le format d'échange est testé depuis la suite standard (FormatEchangeTest).
sourceSets.named("test") {
    java.srcDir("src/formatBanc/kotlin")
}

dependencies {
    // Le banc exécute le moteur CONTRE le jar ecj patché (comme les tests —
    // main est compileOnly, le runtime du banc doit le porter).
    "benchImplementation"(project(mapOf("path" to ":ecj-art", "configuration" to "sortieEcjArt")))

    // Compilations des source sets du banc croisé : kotlin-stdlib seul
    // (le pilote ne doit porter AUCUNE dépendance du dépôt — isolation du
    // sous-processus oracle, cf. bancStubs/piloteOracle ci-dessus).
    "bancStubsImplementation"(kotlin("stdlib"))
    "piloteOracleImplementation"(kotlin("stdlib"))
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

// Banc des USAGES de l'étage N4 (critère de sortie — ADR 0014 : QueryExecutor
// sur index de mots, rappel = oracle, p95 < 100 ms, Files.walk hors chemin
// chaud) — la tâche ÉCHOUE si une gate n'est pas tenue (CI bloquante).
tasks.register<JavaExec>("benchUsages") {
    group = "verification"
    description = "Banc des usages N4 (rappel vs oracle, p95 < 100 ms, index froid unique) — échoue sur régression."
    mainClass.set("jo.codeide.lsp.java.banc.BancUsagesKt")
    classpath = bancSourceSet.runtimeClasspath
    jvmArgs("-Xmx2g", "-XX:+UseSerialGC")
}

// Banc des RESSOURCES de la vague V-B (ADR 0020/0021) : scan res/ p95 <
// 150 ms, MAJ ciblée < 5 ms, peson compact < 2 Mo/projet — mesures sur la
// fixture mini, le banc monip et le corpus réel (présent = mesuré, absent
// = ignoré avec raison affichée) ; ÉCHOUE si un budget ADR est dépassé.
tasks.register<JavaExec>("benchRessources") {
    group = "verification"
    description = "Banc des ressources V-B (scan p95 < 150 ms, MAJ ciblée < 5 ms, peson < 2 Mo/projet)."
    mainClass.set("jo.codeide.lsp.java.banc.BancRessourcesKt")
    classpath = bancSourceSet.runtimeClasspath
    jvmArgs("-Xmx2g", "-XX:+UseSerialGC")
}

// Banc de PARITÉ CROISÉE (étage T — cœur historique, ADR 0016) : le même
// corpus rejoué contre le vendored (oracle en boîte noire, sous-processus
// JVM isolé piloté par DriverOracle) et le moteur neuf (in-process) ;
// baselines JSON signées (bancs/baselines/parite-completion.json) ; la
// tâche ÉCHOUE sur régression (CI bloquante). Prérequis (gate, motif
// ANDROID_JAR) : .oracle/ construit par scripts/preparer-oracle.sh —
// absent localement = banc IGNORÉ (exit 0) ; la CI construit l'oracle
// d'abord, le banc y est donc bloquant. Re-signature : PR dédiée avec
// -Presigner.
tasks.register<JavaExec>("benchParite") {
    group = "verification"
    description = "Banc de parité croisé T (oracle vendored vs moteur neuf, ADR 0016) — échoue sur régression."
    mainClass.set("jo.codeide.lsp.java.banc.BancPariteCroiseKt")
    classpath = bancSourceSet.runtimeClasspath
    jvmArgs("-Xmx2g", "-XX:+UseSerialGC")
    systemProperty("banc.baseline", rootProject.file("bancs/baselines/parite-completion.json").absolutePath)
    systemProperty("banc.oracle", rootProject.file(".oracle").absolutePath)
    systemProperty(
        "banc.piloteClasses",
        piloteOracleSourceSet.output.joinToString(File.pathSeparator),
    )
    dependsOn(piloteOracleSourceSet.output)
    if (project.hasProperty("resigner")) {
        args("--resigner")
    }
}

// ---------------------------------------------------------------------------
// Publication Maven du moteur (bascule §8.5 — cf. préambule en tête de
// fichier pour les coordonnées et le contrat de jetons).
//
// Invariants vérifiés par la publication elle-même :
//  - `version.properties` (serverInfo.version = 1.0.0, fiche 059 I7) est
//    dans le jar : la ressource générée passe par processResources, donc
//    par la sortie du jar publié — jamais de version fantôme ;
//  - le POM ne porte PAS ecj (compileOnly : « l'hôte fournit le jar ») —
//    le compagnon `jo-codeide-lsp-java-ecj-art` est publié par :ecj-art ;
//  - lsp4j 0.21.2 est la seule dépendance runtime déclarée (scope runtime
//    dans le POM, consommable par une chaîne Gradle Android).
// ---------------------------------------------------------------------------
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
        create<MavenPublication>("moteur") {
            from(components["java"])
            groupId = "jo.codeide.lsp.java"
            artifactId = "jo-codeide-lsp-java"
            version = project.version.toString()

            pom {
                name.set("Moteur Java LSP de CodeIDE (réécriture intégrale)")
                description.set(
                    "Moteur d'analyse Java LSP de CodeIDE — réécriture intégrale en Kotlin " +
                        "(bascule §8.5 signée le 30/10/2026). Façades publiques : " +
                        "jo.codeide.lsp.java.protocole.ServeurLangageJava (LSP) et " +
                        "jo.codeide.lsp.java.MoteurJava (in-process). Bytecode 17 " +
                        "(chaîne Android/ART d8). Le jar ecj patché (compagnon runtime, " +
                        "compileOnly) est publié séparément : jo-codeide-lsp-java-ecj-art.",
                )
                url.set("https://github.com/jjoblab/code-lsp")
                licenses {
                    license {
                        name.set("Apache License 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("jjoblab")
                        name.set("jjoblab")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/jjoblab/code-lsp.git")
                    developerConnection.set("scm:git:https://github.com/jjoblab/code-lsp.git")
                    url.set("https://github.com/jjoblab/code-lsp")
                }
            }
        }
    }
}
