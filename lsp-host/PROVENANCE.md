# PROVENANCE — module :host (hôte LSP)

> Registre de reprise (R9.2 — miroir de
> `lsp-xml/docs/PROVENANCE-REPRISE-XML.md`). Décision propriétaire du
> 13/11/2026 : migration de l'hôte LSP du dépôt `jjoblab/codeide-lsp`
> vers `jjoblab/code-lsp`, avec le moteur XML — codeide-lsp est archivé
> et rendu privé après la bascule CodeIDE.

## Entrée du 2026-11-13 — reprise intégrale du module `lsp/host`

- **Source** : `jjoblab/codeide-lsp` @ `01e7dd2` (main, dernière version
  publiée 1.26.0) — répertoire `lsp/host/`.
- **Destination** : `lsp-host/` (module Gradle `:host`).
- **Contenu repris** : `src/main/kotlin/jo/codeide/lsp/host/**` et
  `src/test/kotlin/**` TELS QUELS (45 fichiers — SessionLsp,
  SessionLspKotlin, SessionLspXml, OrchestrateurLsp, AdaptateurIndexNoms,
  XmlLanguageServer, harnais E2E, CapacitesLspPariteTest avec les tables
  L0 TABLE_JAVA/TABLE_KOTLIN/TABLE_XML, scénarios Java/Kotlin harnais).
- **Adaptations** (aucune de code) :
  - `build.gradle.kts` RÉÉCRIT pour le DSL de code-lsp : plugin
    `com.android.library` + `kotlin("android")` INLINE (la convention
    `codeide.android.library` de codeide-lsp n'existe pas ici — namespace
    `jo.codeide.lsp.host`, compileSdk 37(.2), minSdk 26, Java 17,
    Robolectric isIncludeAndroidResources repris à l'identique) ;
  - dépendances : `:lsp:model`/`:lsp:index` → `:model`/`:index` du socle
    de code-lsp (sur-ensemble vérifié — `ModuleAndroidLsp` additionnel,
    signatures identiques) ; `:lsp:xml` → `:xml` (migration jumelle) ;
    packs `jo.codeide.lsp.java`/`jo.codeide.lsp.kotlin` résolus par les
    coordonnées du DÉPÔT code-lsp (même registre, version du projet) ;
    cel-lsp 3.74.0 (JitPack) et tooling protocol (composite frère
    `../codeide-tooling`, `settings.gradle.kts`) inchangés ;
  - publication : `jo.codeide.lsp.host:jo-codeide-lsp-host` (ancienne
    coordonnée : `com.github.jjoblab.codeide-lsp:host`).
- **Non repris** : rien — le module est complet.
