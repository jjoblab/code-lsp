// Module :ecj-art — patcheur build-time d'ecj pour ART (réécriture intégrale
// vague 1c, ADR 0001 §2/0005/0018).
//
// RÉÉCRITURE EN CLEAN-ROOM (R9.3 voie ①) : l'ancien patcheur de
// codeide-lsp:lsp/ecj-art (2 f / 468 l., approche tierse — ADR 0110) N'A PAS
// été lu. Cette implémentation est dictée par :
//   - la javadoc JDK / JEP 322 (sémantique de Runtime.Version) ;
//   - la javadoc JDK d'InputStream (readAllBytes / readNBytes) ;
//   - l'API publique ASM 9.7 (ClassReader / ClassWriter / ClassRemapper) ;
//   - les constats factuels du scanner du spike (resultats/scan-art-v3-ecj.txt).
//
// Le patcheur tourne sur la MACHINE DE BUILD (JVM) : il peut utiliser toute
// API JVM. Seules les classes du paquet `jo.codeide.lsp.ecjart.compat`
// (shims) sont embarquées dans le jar patché et s'exécutent sur ART.
//
// Liste de shims VERSIONNÉE (ADR 0018) : src/main/resources/…/liste-shims-3.40.0.txt
// — toute montée de version d'ecj exige un re-scan + révision de la liste.
import org.gradle.process.CommandLineArgumentProvider

plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(21)
    // Le patcheur s'exécute en JVM ; bytecode 17 (convention du dépôt).
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
    explicitApi()
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// Artefact ecj à patcher (EPL-2.0 — obligations de modification consignées
// dans le jar sortant via META-INF/ECJ-ART-MODIFICATIONS.txt + NOTICE du dépôt).
val configurationEcj: Configuration by configurations.creating

dependencies {
    // Transformation bytecode build-time (BSD-3-Clause — manifeste §4).
    implementation("org.ow2.asm:asm:9.7")
    implementation("org.ow2.asm:asm-commons:9.7")

    // Jar ecj 3.40.0 — cible du patchage ET oracle des tests d'intégration
    // (patch réel + re-scan + compilation de fumée avec le jar patché).
    configurationEcj("org.eclipse.jdt:ecj:3.40.0")
    testImplementation("org.eclipse.jdt:ecj:3.40.0")

    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    useJUnit()
    maxHeapSize = "640m"
}

// Tâche de production du jar patché : ecj-3.40.0-art.jar (classes ecj
// patchées + shims embarqués + registre des modifications, signatures
// amont retirées). Entrées : jar ecj, classes compilées (patcheur + shims),
// liste versionnée. La racine des classes shims n'est PAS passée en argument :
// le main() du patcheur la localise via son propre code source (classpath).
tasks.register<JavaExec>("patcherEcj") {
    group = "build"
    description = "Produit build/ecj-patche/ecj-3.40.0-art.jar (ecj patché pour ART + shims embarqués)."
    val jarEcj = configurationEcj.incoming.artifacts.resolvedArtifacts.map { it.single().file }
    val liste = sourceSets.main.get().resources.srcDirs.first()
        .resolve("jo/codeide/lsp/ecjart/liste-shims-3.40.0.txt")
    val sortie = layout.buildDirectory.file("ecj-patche/ecj-3.40.0-art.jar")

    dependsOn(tasks.compileKotlin, tasks.processResources)
    inputs.file(jarEcj)
    // ClassesDirs (kotlin/main + java/main) : couvre patcheur ET shims — le
    // .singleFile est proscrit (le plugin Kotlin crée plusieurs répertoires),
    // et inputs.dir() exige un répertoire unique : inputs.files() accepte la
    // collection entière.
    inputs.files(sourceSets.main.get().output.classesDirs)
    inputs.file(liste)
    outputs.file(sortie)

    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("jo.codeide.lsp.ecjart.PatcheurJarKt")
    // Arguments résolus à l'EXÉCUTION (résolution paresseuse de l'artefact ecj,
    // aucune résolution réseau pendant la phase de configuration du build).
    argumentProviders.add(
        CommandLineArgumentProvider {
            listOf(
                jarEcj.get().absolutePath,
                sortie.get().asFile.absolutePath,
                liste.absolutePath,
            )
        },
    )
}

// Sortie consommable : le jar ecj patché (classes ecj + shims embarqués) mis à
// disposition du module :java (étage N1 — le moteur pilote ecj PATCHÉ, ADR 0001
// §2/0005) via une configuration sortante. Le motif task de la notation
// d'artefact exploite la sortie unique déclarée ci-dessus (outputs.file).
val sortieEcjArt: Configuration by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}

artifacts {
    add("sortieEcjArt", tasks.named("patcherEcj"))
}
