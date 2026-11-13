# CHANGELOG — moteur XML LSP (`lspxml`)

> MIGRATION : ce module est repris INTÉGRALEMENT du dépôt `jjoblab/codeide-lsp`
> (décision propriétaire du 13/11/2026 — registre
> `lsp-xml/docs/PROVENANCE-REPRISE-XML.md`). Les versions antérieures à la
> reprise vivent dans l'historique de l'ancien dépôt (archivé et rendu
> privé). Format : **Ajouté / Modifié / Corrigé**.

## 1.15.0 — migration dans code-lsp, publication en pack langue (13/11/2026, PUBLIÉE v1.15.0)

- **Ajouté — MIGRATION (décision propriétaire)** : reprise intégrale du
  module `:lsp:xml` de codeide-lsp (parseur tolérant écrit main, DOM
  neutre, règles pures, complétion par contributeurs — zéro dépendance,
  258 tests dont corpus LayoutLib auto-skip). Publication en PACK LANGUE
  `jo.codeide.lsp.xml:jo-codeide-lsp-xml` sur le registre
  maven.pkg.github.com/jjoblab/code-lsp (ancienne coordonnée :
  `com.github.jjoblab.codeide-lsp:xml`) ; l'hôte migré (`lsp-host/`)
  consomme le SPI `jo.lspxml.api.MoteurLangageXml` par api.
- **Modifié — chaîne de build** : build.gradle.kts réécrit pour le DSL
  de code-lsp (JUnit 4 4.13.2, publication miroir :java/:kotlin) ;
  wrapper Gradle 9.7.1 (exigé par l'AAR hôte du même dépôt). Le code
  source est repris TEL QUEL — le descripteur de pack embarqué
  (`jo/lspxml/pack/version.properties`, version 0.134.20) est inchangé.
