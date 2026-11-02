# ADR 0020 — Modèle de projet Android et référentiel de ressources

- **Statut** : Accepté (exécuté — vague V-B1/V-B2, 31/10/2026)
- **Date** : 2026-10-31
- **Écarts résolus** : G-006, G-008 (fondation de G-001/G-007/G-011 ; `01-audit-moteur.md` §1.1–§1.3, §3.9)

## Contexte

`ModuleLsp` (`model/…/ProjetLsp.kt:66–79`) ne porte ni `namespace`/`applicationId`, ni
`minSdk`/`targetSdk`/`compileSdk`, ni dossiers `res/`, ni manifest, ni `buildFeatures`,
ni processeurs d'annotations, ni flavors résolus, ni `compileOptions` (H3, preuve
§1.3). Le moteur ne connaît des ressources **rien** (filtres `.java`, H2). Or la
référence fonde toute son intelligence Android sur : (a) un **référentiel de
ressources** incrémental (un dépôt par dossier `res/`, ids `@+id` des layouts,
styleables à trois familles de champs, `R.txt` des AAR, cache disque) et (b) le
**modèle Gradle** sync (sourceSets, variantes, buildFeatures, minSdk — cartographie
exhaustive §B10). L'instruction du 30/10 maintient les moteurs Kotlin/XML hors champ ;
le propriétaire veut un moteur intelligent **avant la première sync** et après un build
cassé (§0.1-2).

## Sources consultées

- `02-reference-android-studio.md` §B8 (référentiel), §B10 (modèle Gradle, champs
  exhaustifs), §B9 (anti-doublon light/générés) — code public du plugin Android et
  documentation developer.android.com.
- `01-audit-moteur.md` §1.3 (champs réels de `ModuleLsp`), §3.9 (intégration Android).
- `docs/ECARTS-SERVEUR.md` §3 (contraintes hôte : sync tardive, H1/H2).
- `docs/INTEGRATION-CODEIDE.md` (modèle riche produit par l'hôte — contrat à étendre).

## Options

**Option 1 — Extension de `:model` + référentiel moteur, dérivation disque par
défaut, sync en surcharge (recommandée).** Deux étages : (a) **champs additionnels**
dans `ModuleLsp` (liste B10 : identité, SDK, res/, manifest, buildFeatures,
processeurs, flavors, compileOptions, resValue/buildConfigField, packaging) —
tous optionnels avec valeurs par défaut AGP 8 ; (b) un **référentiel de ressources
moteur** (`res/**` par conventions de sourceSets, parseur XML pull, map
`type → name → (fichiers, qualifiers, valeur)`, ids `@+id`, styleables, `R.txt` AAR),
persisté en segment CIDX, MAJ ciblée sur didChange XML. Quand l'hôte fournit le modèle
sync complet, il **prime** ; sinon un **dérivateur disque** (parseur Groovy/KTS
tolérant + settings.gradle + TOML + manifest + valeurs par défaut AGP 8) produit le
même contrat — l'appareil est intelligent sans Gradle.

**Option 2 — Tout côté hôte (l'hôte produit déjà tout).** Rejetée : dépend de la sync
(t+123,9 s mesuré, ECARTS-SERVEUR H1) et d'un build sain — exactement ce que le
propriétaire veut éviter ; le moteur resterait « bête » seul.

**Option 3 — Moteur seul sans contrat hôte.** Rejetée : le modèle riche sync
(variantes résolues, classpaths exacts) reste supérieur au parseur texte ; il faut les
deux avec priorité sync.

## Critères et mesures

- Référentiel : temps de scan d'un `res/` de 200 fichiers [à mesurer : cible p95
  < 150 ms JVM, ×5 ART] ; MAJ ciblée d'un `<string>` ajouté [cible < 5 ms] ; empreinte
  du segment CIDX [budget : < 2 Mo pour le corpus 5 projets].
- Dérivateur disque : taux de champs corrects sur les 5 projets du corpus
  (namespace/minSdk/buildFeatures vs valeurs réelles des build.gradle) [cible ≥ 95 %].
- [Non vérifiable en local] : comportement sur appareil sans sync — gabarit 07.

## Décision proposée (exécutée)

> **EXÉCUTÉE en V-B1/V-B2** (PV : `04-plan-vagues.md` §V-B) : contrat
> `:model` v11 (`ModuleAndroidLsp`, champ `android` optionnel — rétro-
> compatible), dérivateur disque `ModeleAndroidDerive` (KTS/Groovy
> tolérant, repli par défauts AGP 8, drapeau
> `jo.codeide.lsp.java.projetandroid.derivation.disabled`), référentiel
> `ReferentielRessources` (parseur pull sans dépendance, MAJ ciblée,
> `octetsCompacts()` — peson mesuré 126 Ko max < 2 Mo sur le corpus),
> surcharge sync `remplacerContexteModule(…, projetAndroid)` (la sync
> PRIME, R3). **Reste du plan de migration** : point 3 (R.txt AAR +
> resValue/flavors) et le câblage du segment CIDX côté index partagé
> (format binaire prêt, budget prouvé) — PR dédiée.

**Option 1.** Le contrat `:model` est étendu en **v2 rétro-compatible** (nouveaux
champs optionnels, `empreinte` étendue) ; le dérivateur disque vit dans le moteur
(`interne/` ou `projetandroid/`), le référentiel de ressources est un service moteur
alimenté par CIDX ; l'auditeur de contexte existant (LIVE-REPUBLISH) relie l'arrivée du
modèle sync à la re-résolution. **Ce que l'hôte doit produire** (liste séparée, prête
à devenir un prompt `codeide-lsp`) : les champs B10 lors de la sync + les `res/`
srcDirs + le moment de bascule dérivation→sync.

## Conséquences

- Le moteur devient autonome vis-à-vis de Gradle (axe de dépassement §0.1-2) au prix
  d'un parseur texte à maintenir (dette bornée : grammaire des fichiers build AGP
  courants).
- Les classes light (ADR 0021), le lint NewApi (ADR 0024) et la navigation ressource
  (ADR 0028) consomment ce référentiel — cet ADR est leur prérequis.

## Plan de migration progressive

1. Champs `:model` v2 + dérivateur disque (namespace, minSdk, buildFeatures,
   res/ srcDirs) — derrière drapeau, mesuré sur corpus.
2. Référentiel de ressources (values + ids + styleables) + MAJ incrémentale + segment
   CIDX.
3. `R.txt` AAR + resValue + flavors ; synchro avec l'auditeur de contexte.

## Tests de non-régression et critères de sortie

- Tests du dérivateur sur les 5 projets du corpus (champs attendus = valeurs lues des
  build.gradle réels, cas dorés versionnés).
- Référentiel : idempotence (P2 étendu aux XML), MAJ ciblée = rescan complet sur
  valeurs.
- Critère de sortie : corpus P3 (zéro faux positif) **avec** classes light actives
  (ADR 0021) sur les 5 projets.

## Risques

- **R1 — Parseur texte cassé par des DSL exotiques** : tolérance + repli valeurs par
  défaut + contrat sync prioritaire.
- **R2 — Empreinte XML sur ART** : budget octets CIDX + parseur pull (pas de DOM).
- **R3 — Divergence dérivation/sync** : la sync prime par contrat ; divergences
  consignées au PV d'intégration.
