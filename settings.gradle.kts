// code-lsp — dépôt officiel des LSP de CodeIDE (vision propriétaire, ADR 0002).
// Périmètre initial : domaine moteur Java — modules :java (moteur réécrit),
// :index (CIDX), :model, :testing (ADR 0001/0002).
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

include(":java")
include(":index")
include(":model")
include(":testing")
