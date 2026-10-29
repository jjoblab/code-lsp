# PROVENANCE-REPRISE-KOTLIN — Manifeste de reprise sélective (moteur Kotlin)

> Mission « étude moteur Kotlin » (dépôt `jjoblab/code-lsp`, branche
> `etude/moteur-kotlin`). **État : VIDE par construction.**
> Règle R9.3 du prompt de mission : *rien n'est copié depuis
> `codeide-lsp:lsp/kotlin` tant que le propriétaire n'a pas tranché la voie
> de gouvernance (ADR 0100 — voies B ou C uniquement)*. Ce manifeste est le
> registre où TOUTE reprise future devra être inscrite **avant** d'avoir
> lieu (R9.2 : dépôt d'origine, chemin, commit, auteur déclaré, licence,
> statut de provenance, décision, justification — jamais « en bloc »).
>
> Ancien dépôt (lecture seule, jamais modifié) : `jjoblab/codeide-lsp` @
> `bc11253b58a5eecd38194d46d5e862e1cf4185e3` (v1.22.1 ; le module
> `lsp/kotlin` est ACTIF — seul `:lsp:java` est gelé, ADR 0233 ancien).
> Sources tierces autorisées consultées durant l'étude : `JetBrains/kotlin`
> (commit consigné dans `01-architectures-analyse-kotlin.md` — Apache-2.0,
> consultation uniquement, R6.4) ; artefacts binaires `org.jetbrains:
> syntax-api`/`annotations` (intellij-dependencies) et
> `org.jetbrains.kotlin:kotlin-metadata-jvm` (Central) — téléchargés pour
> mesure dans `spike-kotlin/`, **jamais committés**.

## 1. Statuts utilisés

| Statut | Sens |
|---|---|
| **propre** | Origine entièrement maîtrisée (écrit à la main attesté, ou tierce sous licence permissive avec attributions conformes) |
| **à vérifier** | Doute documenté — exclu en attendant (R9.6) |
| **exclu** | Frappé d'exclusion par défaut (R9.3) ou hors périmètre (R8) |

## 2. Registre des exclusions et périmètres par défaut (R9.3 — verrouillées jusqu'à l'ADR 0100)

| Élément | Dépôt/chemin | Statut | Motif / voie de levée |
|---|---|---|---|
| Production du moteur Kotlin `lsp/kotlin/src/main/kotlin/**` | codeide-lsp:lsp/kotlin/src/main/kotlin | **exclu (lecture interdite R9.1)** | Lecture interdite pendant toute l'étude ; l'exécution en **boîte noire** (oracle) et les **métriques d'outil** sont autorisées. La reprise (voie B/C) ne peut être ouverte que par l'ADR 0100, fichier par fichier, inscrite ICI AVANT toute copie |
| Grammaire vendorée `lsp/kotlin/src/main/vendor/**` | codeide-lsp:lsp/kotlin/src/main/vendor | **exclu de la reprise « reprise »** (R9.2 du prompt) | La grammaire ne se « reprend » pas de l'ancien dépôt : elle est **réobtenue depuis l'amont JetBrains** à une version épinglée (`compiler/multiplatform-parsing`, Apache-2.0) avec notice et registre — voir §4 |
| Tests `lsp/kotlin/src/test/**` | codeide-lsp:lsp/kotlin/src/test | consultation autorisée (R9.1) | Base du cahier de spécifications (R9.5) ; **jamais copiés** — les fiches ne contiennent aucun extrait de code |
| Docs, ADR, rapports | codeide-lsp:docs/** | consultation autorisée | Matière d'étude uniquement |

## 3. Registre des candidats à reprise (voie B/C — AUCUN ne sera copié avant l'ADR 0100)

> L'évaluation « saine/fragile » de chaque zone provient de
> `00b-inventaire-existant.md` §3 (métriques + docs + tests, sans lecture
> du code). Le contrôle R9.4 complet (en-têtes, attributions) se mènera
> **à la copie**, si le propriétaire ouvre la voie B ou C.

| Élément | Dépôt/chemin @ commit | Taille [mesuré] | Auteur déclaré | Licence amont | Statut | Décision proposée | Justification |
|---|---|---|---|---|---|---|---|
| `synthetics/` (R, BuildConfig, ViewBinding, DataBinding) | codeide-lsp:lsp/kotlin/src/main/kotlin/jo/lspkotlin/synthetics @ bc11253 | 2 f / 667 l. | jjoblab | dépôt propriétaire | à vérifier (contrôle R9.4 à la copie) | **reprendre** (voie C) | Capacité différenciante, éprouvée par tests E2E réels (ViewBinding/DataBinding), demandée par D5 |
| `preview/` (previews Compose) | idem …/preview | 1 f / 522 l. | jjoblab | idem | à vérifier | **reprendre** (voie C) | SPI neutre, budgets, frontière rendu nette (C1 livré) |
| `classpath/` (lecteur brut + décodeur `@Metadata`) | idem …/classpath | 2 f / 664 l. | jjoblab | idem | à vérifier | **reprendre ou réécrire** | Différentiel 0 écart sur corpus réels (rapport A.3) ; à aligner sur `:index` CIDX (partage K5) |
| `format/` + `plis/` + `documentation/` + `symboles/` | idem | 4 f / 772 l. | jjoblab | idem | à vérifier | **reprendre** (voie C) | Petits, bornés (ADR 0202), testés |
| `parse/` (façade `Kt*`) | idem …/parse | 5 f / 1 240 l. | jjoblab | idem | à vérifier | **s'inspirer / reprendre** | Façade mince sur le vendor ; la question incrémentalité est ouverte (K1) |
| `resolve/` | idem …/resolve | 19 f / 7 966 l. | jjoblab | idem | à vérifier | **refonder** (voie A/C) | Périmètre arbitraire construit correctif par correctif ; à dériver de la spécification (K2–K4) |
| `diagnostics/` | idem …/diagnostics | 4 f / 2 431 l. | jjoblab | idem | à vérifier | **refonder** | Plus gros fichier (1 784 l.) ; conserver la politique « preuve ou silence » |
| `completion/` | idem …/completion | 2 f / 1 175 l. | jjoblab | idem | à vérifier | **refonder** | Classement structuré absent (chaîne weighers ADR 0012 Java) |
| `session/` + `api/` | idem | 15 f / 4 673 l. | jjoblab | idem | à vérifier | **refonder** | Couplages parse/scan/SPI ; alignement façade code-lsp |
| Jeux de données / digeste de parité | codeide-lsp:lsp/kotlin/src/test/resources | 1 f | jjoblab | idem | à vérifier | **reprendre comme données** | Conserver la valeur des 469 tests sans reprendre le code |
| Grammaire officielle | **JetBrains/kotlin**:compiler/multiplatform-parsing @ version à épingler | ~9,6 k l. | JetBrains | **Apache-2.0** | propre (licence vérifiée) | **réobtenir depuis l'amont** (jamais depuis l'ancien dépôt) | R9.2 du prompt ; notice + registre obligatoires |

## 4. Dépendances d'artefacts et vendoring du futur module (aucune copie de code ancien — chaîne à tenir à jour)

| Élément | Source @ version | Licence | Rôle prévu | Points d'attention |
|---|---|---|---|---|
| **Grammaire officielle vendorée (SPIKE — effectué)** | `JetBrains/kotlin`:compiler/multiplatform-parsing/common/src @ **tag v2.3.0** (`f95cb2f7`), extraction `git archive` du 2026-10-30 | **Apache-2.0** (LICENSE.txt + COPYRIGHT.txt copiés depuis `license/` du même tag ; `license/README.md` vérifié : AUCUNE exception sur ce chemin) | Base de mesure S2 du spike (et modèle du futur vendoring de production) | 23 f / 9 585 l. octet pour octet ; zéro modification locale ; re-sync = remplacement (ADR 0121) ; **jamais obtenue depuis `codeide-lsp`** (R9.2 du prompt) |
| `org.jetbrains:syntax-api` (+ `lang-syntax` transitif) | 0.3.340 (épinglée par le tag amont v2.3.0) | Apache-2.0 (sources) | Arbre de construction syntaxique | **Conditions des artefacts binaires intellij-dependencies à consigner** (miroir AL1 Java : aucun `META-INF/LICENSE` dans les jars — hypothèse Apache-2.0 à confirmer par le propriétaire) ; accès cache-redirector |
| `org.jetbrains:annotations` | 26.0.2 | Apache-2.0 | Annotations amont | Central |
| `org.jetbrains.kotlin:kotlin-metadata-jvm` | 2.2.10 | Apache-2.0 | Décodage `@Metadata` | Central ; monter avec la chaîne d'outils |
| `org.eclipse.lsp4j:lsp4j` | 0.21.2 | EPL-2.0 | Transport LSP (si le serveur vit dans le module, miroir `:java`) | — |
| (voies (ii)/(iii) uniquement) `kotlin-compiler-embeddable` | 2.2.10 | Apache-2.0 | Frontend K2 embarqué — **mesuré au spike, disqualifié pour l'APK** (53,6 Mo / 64 507 méthodes / ~2 170 motifs ART) | Jamais en compile-scope du module moteur (garde CI proposée, ADR 0120) ; pack dédié si mode profond |

## 5. Attributions d'origine (R9.5 — à conserver exactes lors de toute reprise)

- **Grammaire Kotlin multiplateforme** : `JetBrains/kotlin`
  `compiler/multiplatform-parsing`, Apache-2.0 — `LICENSE.txt` +
  `COPYRIGHT.txt` doivent accompagner tout vendoring ; l'algorithme de
  reconstruction d'arbre adapté de `NewTestParser.kt` amont (Apache-2.0,
  crédité dans la KDoc de l'ancien dépôt) devra être crédité à l'identique
  s'il est réadapté.
- **Étude du projet tiers de référence GPL-3.0** (R9.4 du prompt) : les
  décisions D1, D2, D5, D8 et le §2 « Enseignements » de
  `LSP_KOTLIN.md` de l'ancien dépôt déclarent s'appuyer sur l'étude d'un
  projet tiers sous **GPL-3.0** (« aucune ligne de code réutilisée » —
  affirmation **non vérifiable** sans lire du code GPL, hors périmètre).
  Ce manifeste **ne préjuge pas** de la licité de cette filiation
  documentaire ; la vérification objective de similarité d'expression est
  recommandée au propriétaire comme **tâche d'audit séparée**, confiée à
  un rôle distinct des implémenteurs (R9.4). Le présent registre n'enregistre
  **aucune** reprise touchant de près ou de loin ce projet.
- `codeide-lsp` : dépôt propriétaire jjoblab — les reprises éventuelles
  (voie B/C) porteront la mention d'origine et l'auteur déclarés.

## 6. Journal du manifeste

| Date | Événement |
|---|---|
| 2026-10-30 | Création du manifeste (Phase 0). Registres remplis : exclusions R9.3, 11 candidats évalués sans lecture du code, 5 dépendances d'artefacts. **Aucune copie effectuée.** |
| 2026-10-30 | Spike (`spike-kotlin/`) : l'ancien moteur **exécuté comme oracle en boîte noire** (S0, miroir du protocole `preparer-oracle.sh` de l'étude Java) — jar construit dans le clone local de `codeide-lsp`, posé hors dépôt, jamais commité ; aucun fichier de production lu. Artefacts JetBrains/Kotlin téléchargés pour mesure uniquement. |
| 2026-10-30 | **Vendoring amont EFFECTUÉ (spike uniquement)** : grammaire `compiler/multiplatform-parsing/common/src` @ tag v2.3.0 (`f95cb2f7`) obtenue par `git archive` depuis le clone amont (JAMAIS depuis codeide-lsp — R9.2 du prompt), posée dans `spike-kotlin/src/main/vendor/` avec LICENSE.txt/COPYRIGHT.txt du même tag et notice `VENDOR.md`. Vérification d'exception de licence : `license/README.md` @ v2.3.0 ne liste AUCUN fichier de ce chemin (grep vide). Usage : mesure S2 — ce n'est PAS la grammaire d'un moteur de production ; le vendoring de production suivra la même procédure à la vague 0 après décision ADR 0100. |
