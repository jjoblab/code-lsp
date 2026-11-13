// code-lsp — dépôt officiel des LSP de CodeIDE (vision propriétaire, ADR 0002).
// Périmètre : domaine moteur Java — modules :java (moteur réécrit), :index
// (CIDX), :model, :testing, :ecj-art (patcheur build-time, vague 1c)
// (ADR 0001/0002) ; domaine moteur Kotlin — module :kotlin (voie C hybride,
// ADR 0100 acceptée le 2026-10-30, étage 0 amorcé).
// MIGRATION du 13/11/2026 (décision propriétaire — codeide-lsp est archivé
// et rendu privé) : domaine moteur XML — module :xml (reprise intégrale de
// :lsp:xml) + domaine hôte — module :host (reprise intégrale de :lsp:host,
// AAR Android, orchestre les trois moteurs).
pluginManagement {
    repositories {
        gradlePluginPortal()
        // AGP (module :host — bibliothèque Android AAR, migration codeide-lsp).
        google()
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
        // Module :host (migration codeide-lsp) : dépendances Android.
        google()
        // cel-lsp (l'AAR LSP4J du dépôt code-editor, consommé par :host) :
        // JitPack pour un build autonome sans jeton — même mécanique que
        // l'ancien dépôt codeide-lsp (settings.gradle.kts).
        maven { url = uri("https://jitpack.io") }
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
include(":java");     project(":java").projectDir     = file("lsp-java/moteur")
include(":index");     project(":index").projectDir     = file("socle/index")
include(":model");     project(":model").projectDir     = file("socle/model")
include(":testing");   project(":testing").projectDir   = file("socle/testing")
include(":ecj-art");   project(":ecj-art").projectDir   = file("socle/ecj-art")
// Moteur Kotlin LSP (voie C, ADR 0100 — étage 0 : grammaire vendorée par
// ré-obtention amont + suite de parité fiches 010–012 + descripteur de pack).
include(":kotlin");   project(":kotlin").projectDir   = file("lsp-kotlin/moteur")
// Module de mesure isolé de l'étude « moteur Kotlin » (Phase 2 du prompt de
// mission) — NON PUBLIÉ, tâches désactivées sans -PspikeKotlin (voir
// lsp-kotlin/spike/build.gradle.kts) : la CI du domaine Java n'est pas affectée.
include(":spike-kotlin"); project(":spike-kotlin").projectDir = file("lsp-kotlin/spike")
// Moteur XML LSP (MIGRATION du 13/11/2026 — décision propriétaire : reprise
// intégrale du module :lsp:xml de codeide-lsp, registre
// lsp-xml/docs/PROVENANCE-REPRISE-XML.md ; le plan de réécriture d'origine
// est remplacé par la migration directe, codeide-lsp étant archivé).
include(":xml"); project(":xml").projectDir = file("lsp-xml/moteur")
// Hôte LSP (MIGRATION du 13/11/2026 — reprise intégrale du module :lsp:host
// de codeide-lsp, registre lsp-host/PROVENANCE.md) : AAR Android, orchestre
// les moteurs Java/Kotlin (packs de CE dépôt) et XML (module :xml ci-dessus),
// branche le câble cel-lsp ; consommateur direct : CodeIDE (après bascule).
include(":host"); project(":host").projectDir = file("lsp-host")

// com.github.jjoblab.codeide-tooling:protocol (protocole tooling v8, ADR
// 0113 — MIGRATION du composite de codeide-lsp) : en DEV et CI, le dépôt
// frère est intégré en SOURCE par build composite — substitution des
// coordonnées, miroir exact du mécanisme de l'ancien dépôt. Le dossier est
// résolu dans cet ordre : propriété Gradle `tooling.dir`, variable
// d'environnement CODEIDE_TOOLING_DIR, puis défaut `../codeide-tooling`
// (checkout frère — convention de la CI et du bac à sable).
// OPTIONNEL (≠ codeide-lsp) : absent, le build CONFIGURE quand même — seuls
// les modules qui RÉSOLVENT le protocole (:host) exigent le frère ; les
// autres jobs de la CI (bancs, parité, Kotlin) n'ont pas besoin de le
// cloner. La SUBSTITUTION remplace les coordonnées par les projets du
// build inclus : l'épinglage de VERSION se fait par le ref git check out
// (la substitution ignore la version déclarée).
val dossierTooling: File =
    providers.gradleProperty("tooling.dir")
        .orElse(providers.environmentVariable("CODEIDE_TOOLING_DIR"))
        .map(rootDir::resolve)
        .orElse(rootDir.resolve("../codeide-tooling"))
        .get()
if (dossierTooling.resolve("settings.gradle.kts").isFile) {
    includeBuild(dossierTooling) {
        dependencySubstitution {
            substitute(module("com.github.jjoblab.codeide-tooling:protocol")).using(project(":tooling:protocol"))
            substitute(module("com.github.jjoblab.codeide-tooling:api")).using(project(":tooling:api"))
            substitute(module("com.github.jjoblab.codeide-tooling:testing")).using(project(":tooling:testing"))
        }
    }
}
