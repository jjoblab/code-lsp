# CHANGELOG — hôte LSP (`jo.codeide.lsp.host`)

> MIGRATION : ce module est repris INTÉGRALEMENT du dépôt
> `jjoblab/codeide-lsp` (décision propriétaire du 13/11/2026 — registre
> `lsp-host/PROVENANCE.md`). Les versions antérieures à la reprise vivent
> dans l'historique de l'ancien dépôt (archivé et rendu privé).
> Format : **Ajouté / Modifié / Corrigé**.

## 1.15.0 — migration dans code-lsp, AAR jo.codeide.lsp.host (13/11/2026, PUBLIÉE v1.15.0)

- **Ajouté — MIGRATION (décision propriétaire)** : reprise intégrale du
  module `:lsp:host` de codeide-lsp (OrchestrateurLsp, SessionLsp/
  SessionLspKotlin/SessionLspXml, AdaptateurIndexNoms, XmlLanguageServer,
  121 tests dont la garde L0 de parité CapacitesLspPariteTest et le
  harnais E2E Robolectric). Publication `jo.codeide.lsp.host:
  jo-codeide-lsp-host` sur maven.pkg.github.com/jjoblab/code-lsp
  (ancienne coordonnée : `com.github.jjoblab.codeide-lsp:host`).
- **Ajouté — AdaptateurProtocoleClasspath** : la surface publique garde
  le type du protocole tooling RÉEL (`jo.codeide.tooling.protocol.
  ClasspathResult`, consommé par CodeIDE) ; l'adaptation vers le contrat
  intégré du socle :model (`jo.codeide.lsp.model.protocole` — réécriture
  locale motivée par l'action P1) est INTERNE, champ pour champ.
- **Modifié — chaîne de build** : plugin AGP 9.4.1 INLINE (convention
  codeide.android.library inexistante ici — namespace, compileSdk 37.2,
  minSdk 26, Kotlin intégré AGP 9, lint strict repris) ; les packs
  java/kotlin/xml et le socle model/index sont consommés par leurs
  COORDONNÉES publiées (substitution en-build par la racine — le POM
  reste exact pour CodeIDE) ; le compagnon ecj-art voyage en scope
  runtime dans le POM (withXml) ; protocole tooling par build composite
  du frère codeide-tooling (optionnel — cf. settings.gradle.kts).
