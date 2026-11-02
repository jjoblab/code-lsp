# ADR 0120 — Licences et provenance de la solution retenue (artefacts JetBrains/Kotlin)

- **Statut** : Proposé
- **Date** : 2026-10-30

## Contexte

R6 du prompt : chaque condition d'usage doit être consignée. La chaîne
Kotlin diffère de la chaîne Java (pas d'EPL-2.0 modifié en cœur) :
grammaire vendorée Apache-2.0 (amont JetBrains), syntax-api
(intellij-dependencies), kotlin-metadata-jvm (Central), lsp4j (EPL-2.0,
transport), et le point de vigilance transversal : la filiation
documentaire GPL-3.0 des décisions anciennes (R9.4 — non consultée,
non vérifiable).

## Sources consultées

- `JetBrains/kotlin` @ `e3806c8f` et tag v2.3.0 : `license/README.md`
  (Apache-2.0 pour le code JetBrains, exceptions par fichier —
  AUCUNE sur les chemins consultés [vérifié]), `license/LICENSE.txt`,
  `license/COPYRIGHT.txt` (copiés au vendoring du spike).
- Artefacts mesurés : `org.jetbrains:syntax-api:0.3.340` (+
  lang-syntax) via intellij-dependencies (cache-redirector) ;
  `org.jetbrains:annotations:26.0.2` ;
  `org.jetbrains.kotlin:kotlin-metadata-jvm:2.2.10` ;
  `kotlin-compiler-embeddable:2.2.10` (mesures S1).
- `Kotlin/kotlin-lsp` : Apache-2.0 [vérifié, API GitHub] — consulté
  (README) comme référence de design, aucun code.
- Manifeste : `docs/PROVENANCE-REPRISE-KOTLIN.md` (registres).
- `code-lsp` : ADR 0017 Java (conditions artefacts JetBrains — alerte
  AL1 : aucun `META-INF/LICENSE` dans les jars intellij-dependencies).

## Options

**Option 1 — Chaîne Apache-2.0 assumée + registre + vérifications
mécaniques.** Grammaire vendorée avec notices ; artefacts binaires
épinglés ; le compilateur embarqué (si mode profond un jour) en pack
téléchargeable séparé ; interdiction de dépendance compile-scope au
compilateur dans le module moteur (vérifiable en CI).
**Option 2 — Consommer la filiation documentaire de l'ancien dépôt**
(enseignements du projet tiers GPL-3.0) : NON — R6.2/R9.4 interdisent
la consultation ; les décisions anciennes D1/D2/D5/D8 restent des
affirmations non vérifiables ; recommandation d'audit séparé au
propriétaire (confié à un rôle distinct des implémenteurs).
**Option 3 — Ne rien consigner** : contredit R6.3 — écarté.

## Critères et mesures

- Scan ART [mesuré] : syntax-api 0 motif ; kotlin-metadata 1
  ServiceLoader ; compilateur ~2 170 motifs (dont Unsafe) — les
  conditions d'usage suivent la pile retenue (ADR 0103 : plan chaud
  sans compilateur).
- Notices embarquées : LICENSE/COPYRIGHT amont copiés au vendoring
  [fait, spike] ; THIRD_PARTY_NOTICES à étendre en vague 0.

## Décision proposée

**Option 1** : (a) le vendoring de grammaire porte notices amont et
registre (manifeste Kotlin — fait pour le spike, à reproduire au
module) ; (b) les conditions des artefacts binaires intellij-dependencies
restent **à confirmer par le propriétaire** (miroir AL1 Java — aucune
licence embarquée dans les jars ; hypothèse de travail Apache-2.0,
sources Apache-2.0) — action propriétaire avant publication ; (c) le
compilateur embarqué n'entre JAMAIS dans le graphe compile-scope du
module moteur (garde CI) — un éventuel mode profond vivrait en pack
téléchargeable séparé ; (d) l'audit de similarité d'expression
(filiation GPL-3.0 documentaire) est recommandé comme tâche séparée au
propriétaire, distincte des implémenteurs.

## Conséquences

- Chaîne de licences courte et permissive (Apache-2.0 dominante) ;
  NOTICE et registre tenus dès la vague 0 (extension du
  `verifier-provenance.sh` — plan vague 0 de la synthèse).

## Plan de migration progressive

Vague 0 : notices + registre + garde CI « pas de compilateur en
compile-scope » + action propriétaire (conditions binaires).

## Tests de non-régression et critères de sortie

- CI verte : aucun fichier hors registre sous `kotlin/` (extension du
  script) ; aucune dépendance compile-scope au compilateur ;
  notices présentes au vendoring.

## Risques

- R1 — Conditions binaires intellij-dependencies non confirmées :
  blocante pour la PUBLICATION (pas pour l'étude) — action
  propriétaire tracée.
- R2 — Oubli d'attribution lors d'une reprise (voie B/C) :
  mitigation = registre préalable obligatoire (R9.2) + CI.
