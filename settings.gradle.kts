// code-lsp — dépôt officiel des LSP de CodeIDE (vision propriétaire, ADR 0002).
// Périmètre : domaine moteur Java — modules :java (moteur réécrit), :index
// (CIDX), :model, :testing, :ecj-art (patcheur build-time, vague 1c)
// (ADR 0001/0002) ; domaine moteur Kotlin — module :kotlin (voie C hybride,
// ADR 0100 acceptée le 2026-10-30, étage 0 amorcé).
// Les autres moteurs (XML…) migreront après décision dédiée.
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
        // ACTIVÉ au domaine Kotlin (vague 0, ADR 0100 accepté) : syntax-api
        // 0.3.340 (intellij-dependencies — épinglé par le tag amont v2.3.0 de
        // la grammaire vendorée, VENDOR.md). Conditions des BINAIRES à
        // confirmer par le propriétaire (P-K2) avant publication du pack.
        maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
        // À décommenter à l'usage (après action P1) :
        // maven("https://maven.pkg.github.com/jjoblab/codeide-tooling")          // protocole tooling
    }
}

rootProject.name = "code-lsp"

plugins {
    // Auto-provisionnement des toolchains : télécharge le JDK 21 (Temurin)
    // si la machine n'en dispose pas. La CI fournit déjà le JDK (setup-java) ;
    // ce plugin sécurise les builds locaux (poste de travail, bac à sable).
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// Socle partagé (réorganisation Phase 2 — docs/REORGANISATION.md §3) :
// les chemins de projets Gradle (:java, :kotlin, :index, :model, :testing,
// :ecj-art) NE CHANGENT PAS — seuls les répertoires sont remappés.
include(":java")
include(":index");     project(":index").projectDir     = file("socle/index")
include(":model");     project(":model").projectDir     = file("socle/model")
include(":testing");   project(":testing").projectDir   = file("socle/testing")
include(":ecj-art");   project(":ecj-art").projectDir   = file("socle/ecj-art")
// Moteur Kotlin LSP (voie C, ADR 0100 — étage 0 : grammaire vendorée par
// ré-obtention amont + suite de parité fiches 010–012 + descripteur de pack).
include(":kotlin")
// Module de mesure isolé de l'étude « moteur Kotlin » (Phase 2 du prompt de
// mission) — NON PUBLIÉ, tâches désactivées sans -PspikeKotlin (voir
// spike-kotlin/build.gradle.kts) : la CI du domaine Java n'est pas affectée.
include(":spike-kotlin")
// Réservation LSP XML (aucun code — cf. lsp-xml/docs/PLAN-MIGRATION.md) :
// include(":xml"); project(":xml").projectDir = file("lsp-xml/moteur")
