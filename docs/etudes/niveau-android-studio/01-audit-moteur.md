# 01 — Audit du moteur actuel (Phase 1)

> Mission « Amener le LSP Java de `code-lsp` au niveau d'intelligence d'Android Studio ».
> Branche `etude/moteur-java`. Audit établi à l'état **v1.1.6** (bascule §8.5 signée le
> 30/10/2026, CI verte, 625 tests). Aucune implémentation au-delà de la vague 0 (§10 du prompt).
>
> **Convention de statut (R5)** : chaque affirmation porte `[CONFIRMÉ]` (preuve fichier:ligne
> ou commande reproductible), `[CONFIRMÉ, nuancé]`, `[INFIRMÉ]` ou `[NON VÉRIFIÉ]` (avec la
> raison et le protocole proposé). Les chiffres JVM de bureau sont des **bornes basses**
> (facteur ART 3–5×, `02-spike-mesures.md`).

## 0. Méthode et périmètre

- **Terrain** : le dépôt lui-même, à l'état livré (module `:java` : 19 répertoires,
  22 649 lignes Kotlin main — comptage `rg --files java/src/main | xargs wc -l`, 28/10…31/10).
- **Lectures** : ordre du prompt §2 (AGENTS.md, README.md, `docs/ECARTS-SERVEUR.md`,
  étude PSI D1–D12, ADR 0001–0018, cahier 65 fiches, USAGE.md, INTEGRATION-CODEIDE.md,
  PLAN-REECRITURE.md, RECALAGE-1/2.md), puis le code dans l'ordre recommandé.
- **Mesures dynamiques** : la CI GitHub (plus de RAM) porte la vérification complète
  (§1.5) ; en local seuls des tests ciblés ont été lancés, et le présent audit s'appuie
  d'abord sur la **lecture statique preuve à l'appui** + les chiffres hérités des bancs
  signés (marqués « hérité de »). Les bancs d'incohérences P1–P8 (§4.3 du prompt) sont
  **livrés comme outillage** (vague 0) — leurs résultats d'exécution sur corpus réel seront
  la matière première des vagues correctives.
- **Aucune baseline signée n'a été modifiée** ; aucune modification du comportement visible
  du moteur n'a été faite dans cette mission.

## 1. État des hypothèses H1–H15 du prompt

Résumé ; détail et preuves dans les sections suivantes.

| # | Hypothèse | Statut | Preuve principale |
|---|---|---|---|
| H1 | Code généré invisible tant que le build n'a pas tourné | **[CONFIRMÉ]** | §1.1 |
| H2 | Seules les sources `.java` existent pour le moteur | **[CONFIRMÉ]** | §1.2 |
| H3 | Modèle de projet trop pauvre pour l'intelligence Android | **[CONFIRMÉ]** | §1.3 |
| H4 | Pas d'invalidation inter-fichiers des diagnostics | **[CONFIRMÉ, nuancé]** | §1.4 |
| H5 | Le « rouge » = ecj seul, analyse IDE quasi absente | **[CONFIRMÉ]** | §1.5 |
| H6 | Diagnose rapide = source d'incohérences structurelles | **[CONFIRMÉ partiellement — risques réels, garde existante]** | §1.6 |
| H7 | 20 IDs ecj → 7 familles, quickfix seul | **[CONFIRMÉ, précisé : 21 IDs]** | §1.7 |
| H8 | `references` mono-fichier | **[CONFIRMÉ, nuancé — le confinement est purement protocolaire]** | §1.8 |
| H9 | Contexte de complétion par réparations textuelles | **[CONFIRMÉ]** | §1.9 |
| H10 | Couverture « intelligente » complétion à établir | **[CONFIRMÉ — audit position par position livré]** | §1.10 |
| H11 | Les bancs mesurent la parité avec l'ancien moteur, pas la qualité AS | **[CONFIRMÉ]** | §1.11 |
| H12 | Incohérences à chercher par propriétés | **[CONFIRMÉ — banc P1–P8 livré en vague 0]** | §1.12 |
| H13 | Un moteur = un verrou global, worker unique | **[CONFIRMÉ]** | §1.13 |
| H14 | Latence linéaire à la taille de l'unité, pas d'incrémentalité | **[CONFIRMÉ]** | §1.14 |
| H15 | Trous de surface protocole | **[CONFIRMÉ]** | §1.15 |

### 1.1 H1 — Code généré invisible avant build `[CONFIRMÉ]`

- Le seul traitement des classes générées est un **déclassement** :
  `actionscode/DeclassementDatabinding.kt:36` (`object DeclassementDatabinding`) passe en
  HINT les imports `…databinding cannot be resolved` pour un module **sans** classes
  générées détectées (coupure honnête dès la première classe générée présente, lignes
  42–56). Exposé par `MoteurJava.kt:890` (`declasserDatabinding`).
- **Aucune synthèse** de `R`, `BuildConfig`, `ViewBinding`/`DataBinding`, Safe Args :
  recherche `rg -ni 'BuildConfig|ViewBinding|DataBinding|SafeArgs|R\.layout'` sur
  `java/src/main` → uniquement `DeclassementDatabinding.kt` et son appelant (aucun
  référentiel de ressources, aucune classe synthétique).
- Le banc « projet Android réel » **simule** la sortie d'AGP : `bancs/projet-android-genere/`
  (arborescence versionnée de sources générées).
- Conséquence mesurée côté appareil : faux « cannot be resolved » sur `R.*` et binding
  avant build (E1–E4 de `INTEGRATION-CODEIDE.md` ; H1 hôte « classpath réel à t+123,9 s »).
- **Écart Android Studio** : référentiel de ressources + classes light
  (`docs/etudes/niveau-android-studio/02-reference-android-studio.md` §B8–B9).

### 1.2 H2 — Seules les sources `.java` `[CONFIRMÉ]`

- `classpath/AnnuaireClasspath.kt:385` :
  `flux.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".java") }`
  — l'annuaire des sources ne voit que `.java`.
- `navigation/IndexMotsSources.kt:128` :
  `flux.filter { it.fileName?.toString()?.endsWith(".java") == true }` — l'index de mots
  (usages, rename) ignore `.kt` (et tout XML).
- Recherche `.kt`/`kotlin` dans ces deux fichiers : **aucune occurrence**.
- Les déclarations Kotlin d'un projet Android moderne ne sont donc visibles du Java que
  via le **bytecode compilé** présent sur le classpath (après build). Le manifest, les
  layouts et `values/*.xml` sont inconnus du moteur.

### 1.3 H3 — Modèle de projet trop pauvre `[CONFIRMÉ]`

`model/src/main/kotlin/jo/codeide/lsp/model/ProjetLsp.kt:66–79` — `ModuleLsp` porte
exactement : `nom`, `type`, `variante`, `sources` (principal/test/générés, l. 93–96),
`classpath`, `dependancesModules`, `androidJar`, `ignore`, `raisonIgnore`,
`avertsements`, `compatibiliteSource`, `compatibiliteCible`, `empreinte`.
**Absents** (vérifié par lecture exhaustive de la classe) : `namespace`/`applicationId`,
`minSdk`/`targetSdk`/`compileSdk`, dossiers `res/`, chemin du manifest, `buildFeatures`
(viewBinding/dataBinding/buildConfig), processeurs d'annotations, flavors/variants
résolus, `compileOptions`/désucrage. Sans ces champs : aucun contrôle « API level »,
aucune activation conditionnelle du binding, aucune navigation ressource.

### 1.4 H4 — Pas d'invalidation inter-fichiers `[CONFIRMÉ, nuancé]`

- `protocole/PublicateurDiagnostics.kt` : publication asynchrone, **debounce 50 ms par
  document** (`DEBOUNCE_MS = 50`, l. 151–152) ; la républication ne concerne que le
  document modifié.
- `protocole/ServeurLangageJava.kt:304–323` : les autres documents **ouverts** ne sont
  republiés qu'au **remplacement de contexte** (`env.ajouterAuditeurContexte` —
  LIVE-REPUSH post-sync Gradle). **Aucun** mécanisme ne republie `B.java` quand l'API
  de `A.java` (ouvert tous deux) change : ni graphe de dépendances, ni invalidation
  transitif. À vérifier dynamiquement par la propriété P8 du banc livré (§1.12) —
  statique : `[CONFIRMÉ]`.
- La **nuance** : l'auditeur de contexte couvre bien le scénario hôte (sync Gradle) ;
  le trou est l'invalidation **intra-session, inter-fichiers**.

### 1.5 H5 — Rouge = ecj seul, analyse IDE quasi absente `[CONFIRMÉ]`

- `interne/PontEcj.kt:216–236` (`optionsEcj`) : **10 clés d'options** posées —
  compliance/source/codegen (3), familles « unused » (3 : `unusedImport`,
  `unusedLocal`, `unusedPrivateMember`), et 4 `ignore` (`undocumentedEmptyBlock`,
  `rawTypeReference`, `uncheckedTypeOperation`, `missingSerialVersion`). Le prompt
  annonçait « ~7 » : ordre de grandeur confirmé, compte exact 10.
- **Aucun profil d'inspections**, aucune analyse de **nullité** (les options ecj
  `org.eclipse.jdt.core.compiler.problem.nullAnnotation…`/`nonnull…` ne sont pas
  posées ; annotations `androidx.annotation`/`org.jetbrains.annotations` ignorées),
  pas d'analyse de **code mort/redondant**, pas d'inspections maison : l'option 1b
  « contrat d'inspection » de l'ADR 0013 est restée à l'état de proposition
  (l'ADR 0013 est toujours « Proposé », `docs/adr/0013` en-tête).
- Le `diagnostics/Diagnostic` moteur (`diagnostics/TypesDiagnostics.kt:66–72`) porte :
  sévérité, message, plage, code, arguments — pas de `relatedInformation`,
  pas de `codeDescription`, pas de source (« ecj » vs « inspection »).

### 1.6 H6 — Diagnose rapide : risques réels, garde existante `[CONFIRMÉ partiellement]`

- `interne/DiagnoseRapide.kt:34` : `SYNTHETIQUE = " throw new RuntimeException();"` —
  les autres corps de méthode sont remplacés par ce synthétique isomorphe, et les
  problèmes des corps masqués sont **portés** tels quels du diagnostic précédent
  (l. 15–16, `portablesHorsCorps` l. 21).
- Garde déjà en place (lecture l. 17–23) : repli sur le diagnose complet si le texte
  **hors corps** a changé, si plus d'un corps a changé, ou si la source diffère. La
  table rapide n'allume que `unusedLocal` (`PontEcj.kt` KDoc l. 59–66) précisément pour
  ne pas fabriquer de faux « unused » sur les corps masqués — le piège « import utilisé
  seulement par un corps masqué » est documenté et couvert par report.
- **Risques restants à mesurer** (banc P1) : (a) problème **périmé** porté après
  modification d'un champ/méthode utilisé ailleurs (le report n'est pas re-validé) ;
  (b) divergence rapide/complet sur des éditions confinées **réelles** — la fiche 014
  impose l'équivalence mais le cahier ne la teste que sur ses cas propres
  (`[NON VÉRIFIÉ sur corpus réel]` → propriété P1 du banc livré).

### 1.7 H7 — Quick-fixes : 21 IDs → 7 familles `[CONFIRMÉ, précisé]`

- `actionscode/FamillesProbleme.kt:83–135` : la table mappe **21 IDs numériques** ecj
  (le prompt disait 20) vers **7 familles** : `UNRESOLVED_REFERENCE` (5 IDs),
  `UNDEFINED_METHOD` (1), `UNUSED_IMPORT` (1), `UNUSED_DECLARATION` (5),
  `UNCAUGHT_EXCEPTION` (4), `TYPE_MISMATCH` (3), `MISSING_SEMI` (2). Tout autre ID →
  `UNKNOWN` → **aucune action** (`fromId` l. 139).
- `protocole/CapacitesServeur.kt:229` : `optionsActions.codeActionKinds =
  listOf(CodeActionKind.QuickFix)` — pas de `source.*` ni `refactor.*`.
  `organizeImports` existe en API moteur (`MoteurJava.kt:1145`) mais n'est pas exposé au
  fil LSP (écart M3 confirmé, `docs/ECARTS-SERVEUR.md` §2).
- Absents (vérifié `actionscode/` : 8 fichiers) : génération de code
  (getters/setters/constructeur/equals/hashCode/toString/override), extraction
  (méthode/variable/constante/paramètre), inline, change signature, conversions
  lambda/référence de méthode. Les correcteurs livrés sont textuels
  (`CorrecteursTexte.kt`), création de méthode (`CorrecteurCreationMethode.kt`) et le
  modèle ModCommand (`ModCommandes.kt`) — 1 seule capacité de création.

### 1.8 H8 — `references` mono-fichier : confinement purement protocolaire `[CONFIRMÉ, nuancé]`

Découverte importante de l'audit : **le moteur sait déjà faire du multi-fichiers**.

- `navigation/ServiceUsages.kt:60–100` : `references` est « EXHAUSTIF sur les racines du
  projet, par clé de binding — l'index de mots sert les candidats, le chemin chaud ne
  traverse JAMAIS l'arborescence ». L'oracle `referencesParBalayageComplet`
  (`MoteurJava.kt:685`) vérifie rappel = 100 %.
- `protocole/ServeurLangageJava.kt:666–685` : la couche protocole **confine** ensuite la
  réponse au document courant (`confine = usages.filter { … == cheminCourant }`),
  commentaires cités : « Parité stricte (§10.4, fiche 054) : le VENDORED ne rendait que
  les références du DOCUMENT COURANT… la décision du 28/10 impose au moteur NEUF de
  reproduire l'écart PENDANT le programme… la correction (rendre l'appel
  inter-fichiers) reste une amélioration candidate post-bascule ».
- **Conséquence** : lever l'écart M4 = retirer ce filtre (plus l'ADR dédié qui abandonne
  explicitement la parité stricte) — le coût réel est l'UX client, pas le moteur.
- `documentHighlight` (même fichier, l. 687+) : intra-document par construction LSP —
  pas de distinction lecture/écriture dans la réponse (`Surbrillance` non typée
  lecture/écriture — vérifié `MoteurJava.kt:702`).

### 1.9 H9 — Complétion par réparations textuelles `[CONFIRMÉ]`

- `completion/AnalyseurContexte.kt` (1 236 lignes) : « échelle de variantes du splice »
  (l. 75–133) — **six variantes** construites en cascade : remplacement (`""`), `()`
  (pour `new`), ` x;` (après modificateurs), ` x` (tête de liste), `() {}` (corps de
  classe), queue équilibrée (l. 747–780). Chaque variante n'est construite que dans sa
  grammaire ; le gating textuel écarte les variantes impossibles (l. 184).
- Le marqueur et l'échelle comparent favorablement au mécanisme de la référence
  (copie du fichier + identifiant factice — même invention, `ADR 0012` ;
  `02-reference…` §A2) mais restent **textuels** : le risque M6 (lambda dans la queue)
  est documenté (`docs/ECARTS-SERVEUR.md` §2 M6).
- `[NON VÉRIFIÉ]` : taux d'échec de classification sur frappe réelle → propriété P6 +
  protocole de captures (06-protocole-captures-android-studio.md).

### 1.10 H10 — Couverture complétion : audit par genre `[CONFIRMÉ]`

Le `GenreContexte` (`completion/ChaineClassement.kt:14–44`) compte **11 genres utiles +
INCONNU** ; dispatch `ServiceCompletion.kt:131–147`. Audit position par position :

| Genre / capacité | État moteur | Référence AS | Niveau (0–4) |
|---|---|---|---|
| MEMBRE (instance, hiérarchie complète jusqu'à Object) | livré (fiche 020) | basic | 3 |
| MEMBRE_STATIQUE | livré | basic | 3 |
| TYPE (types visibles + auto-import, règle sous-paquetage) | livré (fiche 044) | class name | 3 |
| NOM (locales/mots-clés) | livré | basic | 3 |
| IMPORT / REFERENCE_PAQUET (sous-paquetages) | livré | basic | 3 |
| CORPS_CLASSE (stubs d'override toute la hiérarchie) | livré | basic | 3 |
| NOM_DECLARATION (noms dérivés du type) | livré | suggestions de noms | 2 |
| CASE_LABEL / VALEUR_ANNOTATION (constantes d'enum) | livré | basic | 3 |
| TOP_NIVEAU (mots-clés, v1.1.6 C1) | livré | basic | 2 |
| **Smart** (filtrage par type attendu : argument, affectation, `return`, ternaire, lambda) | **absent** — `typeAttendu` ne sert qu'aux enums et à la déclaration (`ContexteCompletion.kt:55`, `ServiceCompletion.kt:145–146`) | smart | **0** |
| **Chain completion** (`a.getB().get\|`) | **absent** | 2e passe smart | **0** |
| **Statement completion** (compléter l'instruction courant) | **absent** | Ctrl+Shift+Enter | **0** |
| **Second appel** (membres inaccessibles) | **absent** | 2e invocation | **0** |
| Import `static` (membres statiques importables) | **absent** | basic | **0** |
| Littéraux de chaîne contextuels (`R.string.`, `getString(`) | **absent** (aucun XML connu) | plugin Android | **0** |
| `@IntDef`/`@StringDef` (constexes autorisées) | **absent** | plugin Android | **0** |
| `switch` sur enum → `sealed`/records, pattern matching Java 21 | **absent** | basic | **0** |
| Références de méthode / diamant en complétion | **absent** | basic | **0** |
| Classement : peseurs D8 + gel + plafond 120 | livré (fiche 050, ADR 0012) | weighers + gel | 3 |
| Re-filtrage local client (capability `x-codeide.completionRanking`) | livré (N8) | split RPC | 3 |
| Statistiques d'usage / classement ML | **absent** | stats + ML local | **0** |

Le classement (chaîne D8, `completion/ChaineClassement.kt`, 369 l.) et la frontière RPC
sont **au niveau** ; c'est la **couverture des contextes** qui est en retrait
(smart/chain/statement/second appel : 0).

### 1.11 H11 — Les bancs mesurent la parité, pas la qualité AS `[CONFIRMÉ]`

- `bancs/baselines/completion.json` : corpus « completion-n2 v1 (**18 positions**) »,
  d8 recall/top-1/MRR = **1,000/1,000/1,000** — un corpus minuscule donne des métriques
  saturées ; le risque n°5 de la synthèse PSI (sur-apprentissage) est **avéré par
  construction** (aucun split train/éval dans la baseline, lue intégralement).
- `bancs/baselines/parite-completion.json` : l'oracle de parité croisée est le
  **vendored** (165 deltas arbitrés) — les défauts de l'ancien moteur (fiche 054,
  `references` mono-doc) sont reproduits en parité stricte (§10.4).
- Le cahier (`docs/cahier-specifications/`, 65 fiches / 449 comportements) est extrait
  des tests de l'ancien moteur : c'est le **contrat de réécriture**, pas une vérité
  terrain « niveau Android Studio ». **Il manque** : corpus réel, métriques FP/FN,
  positions de complétion réelles étiquetées → c'est l'objet de l'ADR « Qualité mesurée »
  (0030) et de la vague 0 livrée.

### 1.12 H12 — Incohérences par propriétés `[CONFIRMÉ — banc livré ET exécuté]`

Aucun test de propriété/métamorphique n'existait (audit de `java/src/test` : tests de
parité cahier uniquement). Le banc P1–P8 est **livré** en vague 0
(`java/src/test/kotlin/jo/codeide/lsp/java/coherence/`) et a été **exécuté** (JVM
de bureau, 23 tests / 0 échec / 1 gate ignorée — corpus réel absent en local,
raison affichée). **Baseline d'écart mesurée** (violations classées par cause
racine, imprimées dans les logs de test) :

| Propriété | Mesure (31/10/2026, JVM bureau) | Lecture |
|---|---|---|
| P1 éditions confinées / usage tardif de champ / repli hors-corps | 0 violation (5 mesures) | la fiche 014 tient sur ces chemins |
| **P1 frappe lettre à lettre (27 éditions)** | **18 divergences rapide/complet** | **confirmé (H6)** : pendant la frappe, le chemin rapide diverge du complet — tailles différentes (ex. 5 contre 4), la matière première de l'ADR 0025 |
| P2 déterminisme / chaud-froid / neutralité blancs et commentaires | 0 violation | stable |
| P3 corpus synthétique (5 fichiers valides, 142 lignes) | **0 FP — FP/1 000 = 0,0** | pas de faux positif sur du code valide hors Android |
| P3 corpus réel (5 projets) | gate ignorée (`CORPUS_ANDROID` absent — préparation classpath requise, LISEZMOI) | à jouer en CI |
| P4 mutants (6 familles) | **6/6 détectés à la bonne plage** (codes : 16777218, 570425394, 16777233, 1610612976, 67108964, 33554515) | rappel intégral sur l'échantillon |
| P5 cas dorés (import, `;`, création en appel-instruction) | 0 violation | le cycle tient sur les contrats fiches 063/064 |
| **P5 balayage élargi (3 fixtures)** | **2 violations** | fix création non contextuel (retour `Object` laissé dans un contexte `String`) + fixture sans action — G-014 |
| P6 position NOM / MEMBRE | 0 violation (20 insertions) | suggestions résolubles |
| **P6 position TYPE (12 types)** | **4 violations** | **découverte** : la complétion propose des classes INTERNES non publiques du JDK (`Striped64`, `StreamShape`, `StreamOpFlag`, `Streams`) avec auto-import insoluble — G-004 (le filtre `com.sun.*`/`jdk.*` de N2 ne couvre pas les non-public de `java.util.*`) |
| P7 rename (mono + inter-fichiers) | 0 violation | le socle N5 tient |
| **P8 inter-fichiers** | **1 violation** | **confirmé (H4)** : après édition de l'API de `A`, `B` (ouvert, inchangé) reste sans erreur (mémo périmé) alors qu'un moteur NEUF voit « The method taille() is undefined » — G-003 |

### 1.13 H13 — Verrou global + worker unique `[CONFIRMÉ]`

- **82 occurrences** de `synchronized` dans `java/src/main` (comptage `rg -o
  'synchronized' --no-filename | wc -l`), dont **46** dans `MoteurJava.kt` seul :
  chaque API publique (`MoteurJava.kt:248` à `:1466`) est `synchronized(this)` — un
  **moniteur d'instance** par moteur.
- `concurrence/OrdonnanceurMoteur.kt:12–60` : ordonnanceur à **worker unique**
  (thread démon) avec supersession par fichier et jeton d'annulation coopératif —
  « une complétion peut attendre un diagnose » (l. 12–16 : le worker sérialise).
- L'ADR 0010 (schéma B « verrous par périmètre », ordre fichier → module → index) est
  **toujours au statut Proposé** : non implémenté. Les lectures concurrentes de deux
  fichiers distincts ne sont pas parallèles.

### 1.14 H14 — Latence linéaire à l'unité, pas d'incrémentalité `[CONFIRMÉ]`

- Chaque requête résout l'unité : toutes les API `MoteurJava` appellent
  `resolveUnit`/`diagnose` sur le texte complet (`MoteurJava.kt:437`).
- `outils/SourceFocuser.kt:105` : `SEUIL_REDUCTION = 2_000` caractères — au-delà, les
  corps des méthodes étrangères au caret sont effacés (v1.1.6 C3) : la **réduction**
  borne le coût mais reste une **re-résolution complète de l'unité réduite** à chaque
  frappe ; pas de résolution incrémentale, pas de graphe de dépendances de fichiers,
  pas de double-tampon d'environnement de noms (la reconstruction froide
  post-invalidation est mesurée à **11–12 s** côté appareil — hérité de
  `ECARTS-SERVEUR.md` §3 H2, la responsabilité du double-tampon moteur reste ouverte).
- Chiffres hérités (JVM bureau, bancs signés) : diagnose complet p50 11,7 ms / p95
  39,8 ms ; diagnose rapide p50 1,5 ms ; hover p50 0,12 ms ; complétion membres p50
  0,04 ms (README N1, corpus 20 classes) ; S17 chaud p95 < 1 000 ms asserté, ≈ 119 ms
  constatés (hérité du PV v1.1.6). **Toutes bornes basses JVM** — aucune mesure ART
  n'est prétendue ici.

### 1.15 H15 — Surface protocole `[CONFIRMÉ]`

- `semanticTokens` : **non annoncé** (`CapacitesServeur.kt:252` : « PAS de
  semanticTokens : aucun service moteur — règle matrice » ; requête tolérante vide,
  USAGE §7.16) — écart M2.
- `resolveCompletionItem` : **identité** (`ServeurLangageJava.kt:601–603` : rend
  l'item non résolu tel quel) — le resolve existe en forme mais ne fait rien.
- Pas de `workDoneProgress` pour l'indexation ; pas de `workspace/willRenameFiles` ;
  diagnostics sans `relatedInformation`/`codeDescription` (type moteur l. 66–72 ci-dessus).
- `codeLens/resolve` : paresseux et réel (l. 969–979). `codeActionKinds` : QuickFix
  seul (cf. H7).

## 2. Matrice capacité × moteur (§4.1 du prompt)

Niveau d'intelligence : 0 absent · 1 textuel/heuristique · 2 syntaxique fiable ·
3 sémantique (bindings ecj) · 4 sémantique + contexte projet/Android (niveau Android
Studio). « Moteurs internes » : ecj bindings (`interne/PontEcj.kt`), index de mots
sources (`navigation/IndexMotsSources.kt`), CIDX (`:index`), lexeur maison
(`outils/LexeurJava.kt`), formateur clean-room (`formatage/`), scanner de corps
(`outils/ScannerCorps.kt`).

| Capacité | Moteurs internes | État | Niveau | Preuve / écart |
|---|---|---|---|---|
| Diagnostics (rouge) | ecj complet/rapide/repli syntaxe | implémenté | 3 | 3 chemins (`MoteurJava.kt:248–333`) ; écart : périmètre = erreurs de compilation seules (H5) |
| Diagnostics (inspections IDE) | — | **absent** | 0 | ADR 0013 option 1b non exécutée (H5) |
| Complétion — membres/types/mots-clés/imports/stubs | ecj + annuaire classpath | implémenté | 3 | fiches 020/044/050 ; §1.10 |
| Complétion — smart/chain/statement/2e appel | — | **absent** | 0 | §1.10 H10 |
| Complétion — classement + RPC riche | peseurs D8 | implémenté | 3 | `ChaineClassement.kt` ; capability N8 |
| Aide de signature | ecj | implémenté | 3 | `signature/` 548 l., v1.1.6 C2 |
| Survol | ecj + Javadoc→Markdown | implémenté | 3 | `survol/` + `NettoyeurJavadocMarkdown.kt` (fiche 021) |
| Inlay hints | ecj | implémenté | 3 | `inlay/` : paramètres + types (2 passes) |
| Definition / typeDefinition | ecj + routeur binaire | implémenté | 3 | `MoteurJava.kt:505–540` ; `jdt://decompiled/` (N4) |
| superDefinition (extension maison) | ecj | implémenté | 3 | `MoteurJava.kt:558` |
| implementation | ecj croix-fichiers | implémenté | 3 | `MoteurJava.kt:541` |
| references | index de mots + bindings | implémenté **au moteur** | 3 | mais **confisée au document courant** par la couche protocole (H8) → niveau servi 2 |
| documentHighlight | bindings | implémenté | 3 | pas de distinction lecture/écriture |
| Rename | bindings + index de mots | implémenté (v2 par phases) | 3 | collisions typées, aperçu, opt-in overrides (N5) ; niveau 4 impossible sans ressources |
| Call hierarchy / type hierarchy | ecj + index | implémenté | 3 | `hierarchie/` 761 l. (N8) |
| Symboles document/espace | lexeur/racines | implémenté | 2–3 | plage sur le NOM déclarant (N8) |
| Code lens | index + balayage | implémenté | 3 | resolve paresseux « 0/1 référence » |
| Pliage | lexeur maison | implémenté | 2 | `pliage/` (syntaxique) |
| Sélection étendue | chaînes croissantes | implémenté | 3 | N4 |
| Formatage document/plage/frappe | formateur clean-room | implémenté | 2 | `formatage/` 790 l., style ECLIPSE |
| Organisation d'imports | moteur | implémenté **en API** | 3 | `MoteurJava.kt:1145` — **non exposé LSP** (M3) |
| Gabarits / postfix | gabarits maison | implémenté | 2 | `gabarits/` (4 f.) |
| Code actions (quick-fix) | familles + ModCommand | partiel | 2 | 21 IDs → 7 familles (H7) ; kinds QuickFix seul |
| Code actions (source.\*/refactor.\*) | — | **absent** | 0 | H7 |
| semanticTokens | — | **absent** | 0 | M2 (H15) |
| Décompilation | lecteur bytecode | implémenté | 2–3 | source attachée ou stub (N4) |
| Indexation | CIDX + index de mots | implémenté | 3 | pas de `workDoneProgress` (H15) |
| **Connaissance projet Android** (ressources, binding, manifest, minSdk) | — | **absent** | 0 | H1–H3 — **le déficit structurant** |
| Interop Kotlin→Java | — | **absent** | 0 | H2 (bytecode post-build seul) |

**Lecture** : le moteur est **solide niveau 3** (sémantique compilateur) sur tout ce qui
est du ressort d'un compilateur ; il est **niveau 0** sur la couche « projet Android »
(ressources/classes light/modèle) et sur les couches « IDE au-dessus du compilateur »
(inspections, smart completion, actions riches). C'est exactement l'inverse des forces
relatives annoncées par l'étude PSI (D9 : fidélité compilateur = atout différenciant).

## 3. Audits par domaine (§4.2 du prompt)

### 3.1 Modèle de code & parsing

- **Architecture** : arbre vivant ecj jamais nettoyé (`interne/PontEcj.kt`,
  `Compiler.resolve` patché `:ecj-art`), cache de parse LRU borné **8 entrées**
  (`interne/CacheParse.kt:63` `MAX_ENTREES = 8`, accès LinkedHashMap), chaîne de
  features à 3 niveaux (arbre focalisé / arbre réel / réduction `SourceFocuser`,
  fiches 015/041), repli syntaxe instantané sans plateforme (fiche 028,
  `MoteurJava.kt:285`).
- **Invariants** : les diagnostics du repli syntaxe ne mélangent jamais avec les
  bindings ; le cache de parse n'est pas rempli par un diagnose rapide
  (`MoteurJava.kt:333`).
- **Dette confirmée** : l'échelle de splice textuelle (H9) ; la vague « java-syntax » de
  l'ADR 0006 **non livrée** (validation d'accolades pré-ecj, plage commune) — l'ADR
  reste Proposé et la dépendance `java-syntax` n'est pas dans `build.gradle.kts` du
  module `:java` (lecture du fichier : aucune dépendance JetBrains).
- **Tests** : fiches 006/024/025/028/041 — garantissent le comportement cahier sur cas
  fixes ; ne garantissent **pas** : robustesse sur frappe réelle, taux d'échec de
  classification (P6 à jouer).

### 3.2 Résolution & environnement de noms

- **Architecture** : routage **sources → jars → jrt → stubs intrinsèques** (fiche 042),
  FQCN longest-match (fiche 010), jrt runtime ou externe via `jrt-fs.jar` (fiche 031),
  lecteur ZIP brut (fiche 038), caches présence/localisation/parse (fiches 006/024/025).
- **Dette confirmée** : reconstruction de l'environnement **globale** au changement de
  contexte (pas de double-tampon — H14) ; sélection de plateforme par `androidJar` du
  modèle seulement (H3 : pas de `compileSdk` explicite) ; AAR = jars internes uniquement,
  **ressources AAR ignorées** (H1).
- **Tests** : fiches 010/031/038/042 + gates `ANDROID_JAR` (033/004 requalifiées CI).

### 3.3 Diagnostics

- **Architecture** : 3 chemins (complet analytique — table `optionsAnalytiques` N8
  fiche 056 ; rapide — corps masqués + report `portablesHorsCorps` ; repli syntaxe),
  publication asynchrone débouncée 50 ms, LIVE-REPUBLISH au remplacement de contexte.
- **Dette confirmée** : H4 (pas d'inter-fichiers), H5 (pas d'inspections), H6 (report
  non re-validé), diagnostics sans `relatedInformation`/`codeDescription`/`source`
  (H15). Déduplication/stabilité des plages : garanties cahier (fiche 059) sur cas
  fixes seulement.
- **Tests** : fiches 014/043/056/059 + `RetoursAppareilV116Test` ; P1–P4 à jouer sur
  corpus réel.

### 3.4 Complétion

- **Architecture** : sonde à chaud sur l'arbre du cache → classification par source
  marquée + échelle de splice → collecte par genre → filtrage gradué → chaîne D8
  (peseurs, gel des têtes, plafond honnête 120) → frontière RPC sérialisable.
- **Dette confirmée** : couverture des genres « intelligents » (H10 : smart/chain/
  statement/2e appel/static import/chaînes de ressources = 0) ; `typeAttendu` limité aux
  enums/déclarations ; pas de statistiques d'usage.
- **Tests** : fiches 007/008/020/035/036/043/044/050/053 + banc qualité 18 positions
  (H11) ; P6 à jouer.

### 3.5 Navigation & usages

- **Architecture** : routeur GO TO complet (intra-fichier → projet → binaire
  `jdt://decompiled/`), câble binaire (`fqcnAt`/`isBinaryType`/`decompiledSource`),
  QueryExecutor des usages sur index de mots (`ServiceUsages.kt`), `Files.walk`
  retiré du chemin chaud, rappel = oracle (banc `:java:benchUsages` CI-bloquant,
  p95 ~62 ms).
- **Dette confirmée** : confinement protocolaire de `references` (H8 — écart M4) ;
  pas de navigation ressource ↔ code (H1) ; pas de distinction lecture/écriture dans
  `documentHighlight`.
- **Tests** : fiches N4 + banc usages ; P7/P8 à jouer.

### 3.6 Édition assistée

- **Architecture** : quick-fixes ModCommand (N6), rename v2 par phases (N5 :
  collisions typées, aperçu = résultat, opt-in overrides), organisateur d'imports
  (API), formateur clean-room, gabarits + postfix, confort v1.1.6 (C1–C3).
- **Dette confirmée** : H7 (21 IDs → 7 familles, kinds QuickFix seul, pas de
  génération de code ni extraction/inline/change signature/conversions) ; M3/M5.
- **Tests** : fiches 026/061/063/065 + N6 ; P5 à jouer.

### 3.7 Informations (hover, signature, inlay)

- **Architecture** : survol riche (signature + Javadoc→Markdown, priorités
  type/déclaration/appel/référence — `survol/`), aide de signature avec fermeture des
  listes avant l'accolade englobante (v1.1.6 C2), inlay 2 passes (paramètres + types).
- **Dette** : pas de valeurs de ressources dans le hover (H1) ; hover binaire limité au
  stub. Tests : fiches N3 (55 comportements).

### 3.8 Concurrence, mémoire, cycle de vie

- **Architecture** : ordonnanceur worker unique + supersession + jeton d'annulation
  coopératif (fiches 048/046), moniteur d'instance `MoteurJava` (46 synchronized),
  états copy-on-write à époques (« 0 réponse périmée », N8), caches LRU bornés
  entrées (parse 8), budgets octets CIDX (`:index`).
- **Dette confirmée** : H13 (verrou global — ADR 0010 schéma B non livré ; lectures
  non parallèles), H14 (pas de double-tampon), `onTrimMemory` non câblé côté moteur
  (ADR 0011 option 1 point 5 non exécuté — l'ADR est Proposé).
- **Tests** : fiches 046/048 ; banc empreinte/latence CI (`:java:bench`).

### 3.9 Intégration Android

- **État** : le moteur « sait » du projet : racines sources, classpath, `androidJar`,
  compatibilité source/cible (H3). **Tout le reste est absent** : ressources, manifest,
  namespace, minSdk, binding, Kotlin, processeurs d'annotations (H1–H3).
- **Conséquence mesurée** : les « urgences » appareil v1.1.6 (C1–C3) sont des symptômes
  de contexte, pas de sémantique ; le déficit « le moteur ne connaît pas le code pour
  lequel il publie » est **structurel et confirmé** sur les trois axes (généré,
  multi-langage, modèle).

## 4. Mesures exécutées / non exécutées

- **Exécutées (statique, reproductible)** : comptages (`synchronized` = 82 ; lignes du
  module = 22 649 ; tailles par répertoire), lectures fichier:ligne citées, épingle des
  commits du corpus (§5), lecture intégrale des baselines signées.
- **Exécutées (dynamique, 31/10/2026, JVM de bureau — borne basse)** : le banc
  P1–P8 livré a été joué (`./gradlew :java:test --tests
  'jo.codeide.lsp.java.coherence.*'` — 23 tests, 0 échec, 1 gate corpus ignorée) :
  résultats consolidés §1.12 — **26 violations mesurées**, toutes classées par cause
  racine (P1 frappe : 18 ; P5 balayage : 2 ; P6 types : 4 ; P8 : 1 ; les autres
  propriétés à 0).
- **Héritées (bancs signés, dates dans les sources)** : latences N1/S17/S20, 18/18
  bascule, 165 deltas parité.
- **[NON VÉRIFIÉ] (et protocole)** : toute mesure **appareil** (gabarit
  `07-gabarit-mesure-appareil.md`) ; divergences ecj ↔ javac **sur corpus réel** (la
  gate P3-corpus exige la préparation des classpaths — LISEZMOI du corpus) ; captures
  Android Studio (protocole `06-protocole-captures-android-studio.md`).

## 5. Corpus réel (§4.4 du prompt) — livré en vague 0

Cinq projets Android **Java**, licences permissives, épingle par `git ls-remote`
(exécuté le 31/10/2026 — SHA réels) ; rôles et licence inscrits au registre
(`docs/PROVENANCE-REPRISE.md` §Corpus) ; téléchargement par script
(`scripts/telecharger-corpus-android.sh`), **jamais de binaire commité** :

| Projet | Licence | Rôle corpus | Commit épinglé |
|---|---|---|---|
| OpenTracks (OpenTracksApp/OpenTracks) | Apache-2.0 | corpus principal : 99,7 % Java, viewBinding, AndroidX/Material/ConstraintLayout | `3c23a9f5400c7b5b914a221469d76cc019a18296` |
| Markor (gsantner/markor) | Apache-2.0 | vues classiques sans binding, très actif, Gradle 8.14 | `7b15e66932f170c0b3f105c522111b0f32d6c73d` |
| gh4a (slapperwan/gh4a) | Apache-2.0 | Retrofit 3 + OkHttp + RxJava2 + WorkManager (génériques/callbacks) | `39f03ffe122125e46a51de16d90881047ed4660e` |
| AnySoftKeyboard (AnySoftKeyboard/AnySoftKeyboard) | Apache-2.0 | multi-modules, base Java la plus large (~73,8 % Java) | `8cf81a3a2f73e9914c8613d56390a933486f0c6b` |
| BasicSample (android/architecture-components-samples) | Apache-2.0 | DataBinding + Room + annotationProcessor Java | `e849ce3004ccd1132a121cf513bbcb7996d95c30` |

Éliminés (vérifiés) : Thunderbird-android (82,8 % Kotlin), AntennaPod/gpslogger/
AmazeFileManager/LibreTorrent (GPL), Telecine (dormant, Gradle obsolète — gardé en
fumée optionnelle), bitcoin-wallet (sans LICENSE). Couverture obtenue : AndroidX/Material
(5/5), view binding (OpenTracks), data binding + Room (BasicSample), Retrofit/OkHttp
(gh4a), multi-modules (AnySoftKeyboard). Réserve : `dmfs/opentasks`
(`3ccb6f9d0195795bd461c3e634bbb8b22c72932c`, Apache-2.0, Java 100 %, inactif) en secours.

> Les noms de ces projets sont des **données de corpus** (cibles de test), pas des
> références de conception : ils vivent au registre de provenance avec leur rôle,
> conformément au §4.4 ; la liste blanche du §1.3 ne les concerne pas.

## 6. Conclusion de l'audit

1. **Les fondations compilateur sont saines et de niveau 3** (résolution, diagnostics
   natifs, usages indexés, rename sûr) — l'étude PSI l'avait prédit (D9), l'audit le
   confirme.
2. **Le déficit est en trois couches concentriques** : (a) **connaissance du projet
   Android** (niveau 0 — H1/H2/H3 : la cause racine des « il ne connaît pas bien le
   code ») ; (b) **couche IDE au-dessus du compilateur** (inspections, smart
   completion, actions riches — H5/H7/H10) ; (c) **cohérence/perf** (H4/H6/H13/H14).
3. **Un écart est quasi gratuit** : le multi-fichiers de `references` existe au moteur,
   seul le filtre protocolaire de parité le bloque (H8) — décision propriétaire
   demandée (D1 du compte-rendu).
4. **La vérité terrain manque** (H11) : le banc P1–P8 + le corpus 5 projets + les
   captures Android Studio forment la vague 0, **sans dépendance à aucune décision
   d'architecture**.
