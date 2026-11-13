# PROVENANCE-REPRISE-XML — registre des fichiers repris de codeide-lsp

> R9.2 (inscription PRÉALABLE, fichier par fichier). Décision propriétaire
> du 13/11/2026 : migration du moteur XML (et de l'hôte) du dépôt
> `jjoblab/codeide-lsp` vers `jjoblab/code-lsp` — code-lsp devient le dépôt
> officiel des LSP ; codeide-lsp est archivé et rendu privé après bascule.
> Exception aux règles clean-room : la reprise est INTÉGRALE et décidée par
> le propriétaire (pas une réécriture — le plan
> `lsp-xml/docs/PLAN-MIGRATION.md` d'origine prévoyait une réécriture ;
> la consigne du 13/11 la remplace par la migration directe).

## Entrée du 2026-11-13 — reprise intégrale du module `lsp/xml`

- **Source** : `jjoblab/codeide-lsp` @ `01e7dd2` (main, dernière version
  publiée 1.26.0) — répertoire `lsp/xml/`.
- **Destination** : `lsp-xml/moteur/` (module Gradle `:xml`).
- **Contenu repris** : `src/main/kotlin/jo/lspxml/**` (69 fichiers —
  parse, dom, rules, completion, resolution, session, api…),
  `src/main/resources/META-INF/services/jo.lspxml.api.MoteurLangageXml`
  (SPI ServiceLoader → `jo.lspxml.session.MoteurXmlMaison`),
  `src/main/resources/jo/lspxml/pack/version.properties` (descripteur
  D6, version 0.134.20 — inchangé),
  `src/test/kotlin/jo/lspxml/**` (63 tests JUnit 4 + corpus LayoutLib
  auto-skip), `README.md`.
- **Adaptations** (aucune de code) :
  - `build.gradle.kts` RÉÉCRIT pour le DSL de code-lsp : pas de catalogue
    `libs`, pas de detekt/spotless (convention dépôt), publication
    `jo.codeide.lsp.xml:jo-codeide-lsp-xml` (ancienne coordonnée :
    `com.github.jjoblab.codeide-lsp:xml`) — miroir `:java`/`:kotlin` ;
  - JUnit 4 pin 4.13.2 explicite (résolu depuis Maven Central).
- **Non repris** : le module `:lsp:java` et `:lsp:kotlin` vendored de
  codeide-lsp (GELÉS depuis ADR 0233/0136, plus consommés par l'hôte —
  les packs `jo.codeide.lsp.java`/`jo.codeide.lsp.kotlin` de CE dépôt
  les remplacent) ; `:lsp:testing` (banc Java de l'ancien dépôt — le
  socle de code-lsp a le sien, `socle/testing`).

## Entrée du 2026-11-13 — reprise intégrale du module `lsp/host`

- **Source** : `jjoblab/codeide-lsp` @ `01e7dd2` — répertoire `lsp/host/`.
- **Destination** : `lsp-host/` (module Gradle `:host`).
- Voir `lsp-host/PROVENANCE.md` (même registre, adapté au module).
