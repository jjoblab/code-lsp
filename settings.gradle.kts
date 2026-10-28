// code-lsp — dépôt officiel des LSP de CodeIDE (vision propriétaire, ADR 0002).
// Périmètre initial : domaine moteur Java — modules :java (moteur réécrit),
// :index (CIDX), :model, :testing, :ecj-art (patcheur build-time, vague 1c)
// (ADR 0001/0002).
// Les moteurs Kotlin/XML migreront depuis codeide-lsp après décision dédiée.
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    // Les dépôts sont déclarés ICI uniquement (aucun bloc repositories() dans les modules).
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        // Vague 1 (décommenter à l'usage — URL de référence dans spike/scripts/telecharger-piles.sh) :
        // maven("https://cache-redirector.jetbrains.com/intellij-dependencies") // java-syntax (JetBrains)
        // maven("https://maven.pkg.github.com/jjoblab/codeide-tooling")          // protocole tooling (après action P1)
    }
}

rootProject.name = "code-lsp"

plugins {
    // Auto-provisionnement des toolchains : télécharge le JDK 21 (Temurin)
    // si la machine n'en dispose pas. La CI fournit déjà le JDK (setup-java) ;
    // ce plugin sécurise les builds locaux (poste de travail, bac à sable).
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":java")
include(":index")
include(":model")
include(":testing")
include(":ecj-art")
