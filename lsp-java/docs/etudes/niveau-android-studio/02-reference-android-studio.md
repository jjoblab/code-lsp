# 02 — Étude de la référence : Android Studio (Phase 2)

> Android Studio = **IntelliJ Platform** (PSI, daemon, index, complétion, intentions,
> refactorings) + **plugin Android** (référentiel de ressources, classes « light »,
> modèle Gradle, intégration lint) + **Android Lint** (contrôles code/XML/manifest/
> Gradle). Sources publiques étudiées **en lecture seule** (IntelliJ Platform et plugin
> Android ; documentation JetBrains/Google officielle). Aucun code copié (R9) ; les
> comportements sont décrits pour eux-mêmes.
>
> **Méthode** : chaque fiche répond « comment fait Android Studio » (principes,
> structures, invariants, coûts), puis « implications pour nous » (ecj + CIDX + ART sur
> appareil). Marques : [doc] documentation officielle · [code] source public réellement
> ouvert · [inférence] déduction · NV non vérifié. Les URLs consultées sont listées par
> fiche ; les captures à faire confirmer par le propriétaire sont dans
> `06-protocole-captures-android-studio.md`.

## A. Plateforme IntelliJ

### A1. Passes d'analyse en arrière-plan (daemon)

**Comment fait Android Studio.**

- Une passe générale unique fait le gros du travail : `GeneralHighlightingPass`
  [code], construite avec une **plage prioritaire** (zone visible) et une plage de
  restriction — `Divider.divideInsideAndOutsideAllRoots` découpe les éléments « inside
  priority » vs « outside » : le highlight est **progressif**, zone visible d'abord
  [code].
- Ordre effectif dans cette passe : **erreurs de syntaxe/résolution d'abord**, puis
  annotateurs locaux asynchrones (`AnnotatorRunner.runAnnotatorsAsync`), puis
  **annotateurs externes en dernier** (plus basse priorité, après tout le traitement en
  arrière-plan) [code + doc SDK].
- L'ordonnancement des passes est fixé à l'**enregistrement des usines de passes**
  (priorités déclarées), pas à l'exécution [code : TextEditorBackgroundHighlighter].
- **Debounce à la frappe** : la première passe tourne à **délai zéro** après
  l'ouverture ; le délai (réglable) ne s'active qu'après qu'une passe d'un fichier
  ouvert a fini ou été annulée [code : DaemonCodeAnalyzerImpl.scheduleIfNotRunning].
- **Reprise/annulation** : chaque édition marque des régions sales dans une carte
  `FileStatusMap` (null = à jour, fichier entier, ou **plage locale** — souvent le bloc
  modifié) ; à la frappe, le runnable planifié est annulé/re-programmé et les passes en
  cours sont annulées avec motif [code].
- **Dumb mode (indexation)** : la passe générale est dumb-aware et continue, mais les
  contributions non dumb-aware sont filtrées ; l'accès aux index lève
  `IndexNotReadyException` ; l'utilisateur voit « Scanning files » (non annulable) puis
  « Analyzing project to enable smart features » (**pausable**) ; l'édition de base
  reste possible [doc].

**Implications pour nous (ecj + CIDX + ART).**

1. Pipeline à étages dans notre publicateur : repli syntaxe (instantané, sans
   plateforme) → diagnostics ecj (rapide/confine, complet différé) → inspections
   (vagues ultérieures) → lint-like en dernier. Notre repli syntaxe (fiche 028) est
   déjà le « dumb-aware » de ce modèle.
2. **Plage prioritaire** : diagnostiquer d'abord la zone visible — crucial sur ART où
   le CPU est compté ; notre `SourceFocuser` est un cousin (réduction par corps), la
   priorisation par visibilité l'étend.
3. **Dirty scope par bloc** (pas fichier entier) : carte document → plage sale à la
   manière de `FileStatusMap`, au lieu du memo source-entier actuel (fiche 014).
4. Première passe immédiate + debounce ensuite (nous : 50 ms partout — à comparer en
   P2), annulation coopérative déjà en place (jeton fiche 046).
5. Pendant l'indexation CIDX : servir syntaxe + navigation lexicale, différer la
   résolution — **dégradation honnête au lieu du blocage** (axe de dépassement §0.1-1).

### A2. Complétion

**Comment fait Android Studio.**

- **Copie du fichier + identifiant factice** : à l'invocation, un identifiant factice
  est inséré au caret **dans une copie du fichier** (traduction d'offsets
  original↔copie) ; Java ajuste ce factice (variante tronquée, ajout de `;` si
  nécessaire) [code : CompletionInitializationUtil, JavaCompletionContributor]. La doc
  SDK confirme le principe : les variantes sont servies par la référence au caret ou
  une référence factice [doc].
- **Contributeurs** (familles, toutes position-driven) : basic
  (`JavaCompletionContributor`), **smart/type-matching**
  (`JavaSmartCompletionContributor`), noms de classes, noms de membres, expressions de
  référence, mots-clés (dumb-aware possibles) [code + doc].
- **Type attendu** : `ExpectedTypesProvider.getExpectedTypes(...)` produit des
  `ExpectedTypeInfo` (TYPE_OR_SUBTYPE...) depuis l'expression englobante (assignation,
  argument, retour...) ; sert à filtrer (matchesExpectedType) et à ajouter les membres
  du type attendu [code]. [NV : détail interne du calcul.]
- **Chain completion** : si pas de suggestion type-matching (ou 2e invocation), des
  suggestions **chaînées** sont ajoutées (`a.getB().get|` — feature flag
  SECOND_SMART_COMPLETION_CHAIN) [code].
- **Invocations successives** : 2e appel → **classes et membres inaccessibles** ;
  3e appel → recherche dans **tout le projet** indépendamment des dépendances, avec
  import automatique [doc : auto-completing-code].
- **Classement** : tri par pertinence par défaut ; `CompletionSorterImpl` via
  `BaseCompletionLookupArranger` ; **gel des items déjà affichés en haut de liste**
  (myFrozenItems/freezeTopItems) [code]. **ML ranking local** : modèle entraîné sur
  logs d'usage (métrique QuerySoftMax, gain publié **+10 % de taux de sélection**),
  appliqué localement [blog officiel].
- **Auto-insert/auto-import** : parenthèses automatiques (InsertHandler), statement
  completion (Ctrl+Shift+Enter équilibre le construct courant), import automatique du
  3e appel, « Add unambiguous imports on the fly » (import dès la frappe si une seule
  source possible), suggestions de noms de variables/paramètres dérivées des types du
  projet [doc].

**Implications pour nous.**

1. Notre marqueur + échelle de splice est la même invention que l'identifiant factice
   (ADR 0012 l'avait noté) ; la différence : la référence insère le factice **dans une
   copie complète**, nous construisons des variantes textuelles. L'ADR 0019 propose la
   convergence (copie + factice + arbre tolérant).
2. **Smart completion sur ecj** : le type attendu est calculable par bindings (membre
   gauche d'affectation, paramètre attendu de l'appel, `return`, ternaire) — pas
   besoin de réimplémenter un inférenceur (cf. A7) : interroger ecj.
3. Chain completion = 2e passe sur les membres du type courant retournant le type
   attendu — équivalent « méthodes transitives » calculable par bindings, coût borné
   (profondeur 2).
4. Invocations successives : notre protocole LSP n'a pas de compteur d'invocation
   natif — l'état (compteur par document + ancre) se simule serveur ou client ;
   à spécifier en ADR 0026 (complétion).
5. Gel + plafond : déjà livrés (D8). Statistiques d'usage : compteur local persistant
   (opt-in) — le ML local est **rejeté en v1** (gain/cost défavorable sans logs
   massifs ; à réévaluer après télémétrie opt-in) — ADR 0026.

### A3. Intentions / quick-fixes / ModCommand

**Comment fait Android Studio.** (Sources : [doc] intention-actions, generating-code ;
[code] platform/analysis-api modcommand/*.)

- Deux ampoules : jaune (intention, **sans** diagnostic — transformations utiles) et
  rouge (quick-fix lié à un problème) ; ensemble = « context actions », un seul canal
  Alt+Enter [doc].
- **ModCommand** [code] : interface scellée de records — `ModChooseAction`,
  `ModCompositeCommand`, `ModCreateFile`, `ModDeleteFile`, `ModMoveFile`,
  `ModNavigate`, `ModShowConflicts`, `ModStartRename`, `ModStartTemplate`,
  `ModUpdateFileText` (oldText/newText + ranges, **abandon si oldText diverge**),
  `ModUpdateReferences`, etc. — « every ModCommand can be easily serialized and sent
  over the network » (javadoc) ; `ModCommandAction` remplace `IntentionAction` :
  `getPresentation` (pur), `perform` en **lecture en arrière-plan**, preview dérivée
  du ModCommand rejouée sur copie non physique [code].
- **Génération de code** (Alt+Insert) : constructeur (choix des champs),
  getters/setters, **equals/hashCode** (wizard, champs d'égalité/hash, `Double.compare`,
  `getClass() !=`), **toString()** (templates), override/implement (stubs avec valeurs
  par défaut, copie Javadoc), délégation [doc].
- **Postfix completion** (`expr.if` → `if (expr)`) et **live templates** (simples,
  paramétrées, surround) [doc].

**Implications pour nous.** ModCommand est **déjà** notre modèle (ADR 0013, N6 :
`actionscode/ModCommandes.kt`) ; l'écart est le **catalogue**, pas le mécanisme :
génération de code et intentions sans diagnostic sont des gains rapides (ADR 0027).
Le verrou optimiste `oldText` (abandon si divergence) est à généraliser — mobile :
l'utilisateur tape pendant le calcul.

### A4. Inspections Java

**Comment fait Android Studio.** (Sources : [doc] Inspectopedia + help ; [code]
LocalInspectionTool, JavaSuppressionUtil.)

- Contrat : `buildVisitor(holder, isOnTheFly)` — visitor **non récursif, thread-safe**,
  `holder.registerProblem(...)` → `ProblemDescriptor` + `LocalQuickFix` [code]. EP
  `localInspection` (fichier courant) vs `globalInspection` (multi-fichiers) [doc].
- Sévérités : Error / Warning / **Weak Warning** / Typo / « No highlighting (fix
  available) » + profils partageables [doc + code HighlightSeverity].
- Suppressions : `@SuppressWarnings(<id>)` (classe/méthode/champ) et `//noinspection
  <id>` (instruction) — les IDs d'inspection (souvent alignés sur les tokens javac :
  `unchecked`, `unused`...) ; détection des suppressions devenues redondantes
  (`RedundantSuppressionDetector`) [code + doc].
- **Nullité/DFA** : reconnaissance par défaut de ~20 paires d'annotations
  (`androidx.annotation` en tête), inspection `DataFlowIssue` « based on data flow
  analysis », pilotée par `@Contract`/`@Range`, options (dont `REPORT_UNSOUND_WARNINGS`
  off par défaut) ; avant 2022.3 elle formait « Constant Conditions & Exceptions » avec
  `ConstantValue` [doc + code]. Annotations **inférées du bytecode** et **externes**
  (annotations.xml) prises en compte [doc].
- **Code mort** : `unused` (usage global depuis entry points ; en éditeur, un membre
  non privé n'est signalé que si **son nom est rare dans le projet** — compromis
  perf/bruit documenté), `UnreachableCode`, `UnusedAssignment`, `UnusedReturnValue`
  (fix `MakeVoid` vérifié) [doc + code].
- Volumétrie : l'Inspectopedia 2026.2 liste **≈ 900 entrées Java** (~107 « probable
  bugs », ~50 threading, ~80 code style...) — le chiffre usuel « ~600 » est dépassé
  [doc, comptage sur pages ouvertes]. Profil par défaut d'Android Studio : **NV**
  (non documenté publiquement).

**Implications pour nous.** Un catalogue initial borné « 20 % des contrôles qui font
80 % de la valeur » est défini en ADR 0023 (table priorisée de 40 inspections, dont les
besoins sont marqués B=bindings / F=flux / I=index / L=local / A=annotations) :
tout ce qui est **B** est déjà couvert par ecj ; le vrai gap est **F** (mini-DFA de
nullité intra-méthode + contrats) puis **I** (usages/entry points — avec l'heuristique
du « nom rare » reprise telle quelle). Le contrat visitor/holder/descriptor est
transposable sur AST ecj (ADR 0013 option 1b, jamais exécutée — à livrer).

### A5. Refactorings

**Comment fait Android Studio.** (Sources : [doc] refactoring-source-code + pages
dédiées.)

- Catalogue : extract method/variable/constant/field/parameter, **introduce parameter
  object**, extract interface/superclass/delegate, inline, **change signature**
  (propagation dans la hiérarchie d'appels), move/copy, **safe delete**, invert
  boolean, wrap return value, convert lambda↔anonyme, stream↔loop, try-with-resources
  [doc].
- **Conflits et preview** : dialogue de preview (liste des usages à modifier,
  exclusion possible) ; en cas de conflit « Refactor Anyway » ou ouverture en fenêtre
  d'usages ; `ModShowConflicts` matérialise ce concept côté ModCommand [doc + code].
- **Find Usages** : résultats **groupés par fichiers/packages**, clustering par
  similarité, preview avec call hierarchy pour les méthodes / data flow pour les
  champs ; options méthodes : usages / méthode de base / surcharges / **occurrences
  texte** ; Code Vision : compteur d'usages inline [doc]. [NV : distinction
  lecture/écriture dans les dialogues courants.]
- **Safe delete** : recherche d'usages AVANT suppression, options « dans les
  commentaires et chaînes » et « occurrences texte » ; cas particuliers documentés
  (paramètre propagé en cascade, champ injecté par constructeur → paramètre aussi
  retiré) [doc].

**Implications pour nous.** Le rename v2 (N5) couvre déjà le noyau des phases ;
l'ordre de valeur est : (1) extract variable/constant (local, bindings), (2) extract
method (heuristiques documentées : une sortie, entrées bornées, pas de contrôle de
flux non local), (3) inline variable/constant, (4) safe delete = usages + conflits,
(5) change signature (exige un graphe d'appelants — requête CIDX à prévoir). La
navigation lecture/écriture passe par le typage des occurrences (déjà partiellement
présent : `estDeclaration` dans `CollecteurUsages`).

### A6. Index et dumb mode

**Comment fait Android Studio.** (Sources : [doc] SDK indexing/stubs/file-based ;
blog shared indexes.)

- Deux familles : **file-based** (contenu → fichiers ; ex. word index avec masque de
  contextes code/littéral/commentaire, FilenameIndex) et **stub indexes** (stub trees
  sérialisés → éléments PSI ; noms/modificateurs des déclarations visibles de
  l'extérieur, sérialisation avec déduplication des chaînes) [doc]. Exemples Java :
  `JavaAnnotationIndex` (annotations) ; index « héritage » historique [NV nom exact].
- **Dumb mode** : accès index restreint pendant l'indexation, `IndexNotReadyException`,
  API « exécuter quand smart » ; ce qui ne dépend pas des index reste disponible [doc].
- Index **persisté sur disque**, réutilisé au démarrage ; index partagés « jusqu'à
  **−75 %** de temps d'indexation Java » (plugin officiel 2020) ; scan (non annulable)
  puis analyse (pausable) [doc + blog]. Temps absolus sur projet Android : **NV**
  (non documenté — d'où le protocole de captures §06).

**Implications pour nous.** CIDX est notre stub index (et il gagne sur les axes
mobiles — D7, préfixe binaire) ; à ajouter : un **word index XML/manifest** pour la
navigation ressource ↔ code (ADR 0020/0028), la persistance est déjà native CIDX, et
les « index partagés » anticipés (AAR/JDK pré-indexés) sont un axe de dépassement
§0.1-3 (démarrage à chaud). Le contrat dumb (isDumb + exception + file d'attente) est
le modèle de notre dégradation honnête.

### A7. Inférence de types et résolution

**Comment fait Android Studio.** (Sources : [code] graphInference ; [doc]
Inspectopedia ; YouTrack.)

- L'inférence IntelliJ est une implémentation **JLS 18** (réduction/incorporation/
  résolution) : `InferenceSession` (2 114 l.) avec variables d'inférence, contraintes
  (`ExpressionCompatibilityConstraint`, `StrictSubtypingConstraint`...), incorporation,
  sessions imbriquées — pour lambdas, références de méthode, diamant ; des commutateurs
  Registry `javac.*` **imitent javac** [code].
- **Divergences documentées IDE ↔ javac** : inspection « Javac quirks » (« Reports
  known Javac issues and incompatibilities ») ; bugs YouTrack réels : « Ambiguous
  method call » à tort (IDEA-118527), références de méthode « cannot resolve » à tort
  (IDEA-163950), lambdas marqués rouges à tort (IDEA-211990), constructeurs
  (IDEA-148692) [doc + YouTrack].
- L'inspection « Unchecked warning » **émule** les warnings javac (options alignées :
  assignment, generics array, raw member call, cast, overriding) [doc + code].

**Implications pour nous.** L'expérience de la référence **valide notre choix** (rouges
= vrai compilateur) : ses faux positifs sont concentrés dans résolution/inférence —
exactement ce qu'ecj exécute nativement. Deux conséquences : (a) ne jamais
réimplémenter l'inférence — demander à ecj le type d'une expression quand une capacité
en a besoin ; (b) **mesurer honnêtement ecj ↔ javac** (le corpus compile avec javac
via AGP : tout écart est un FP/FN potentiel — P3 du banc), et garder une couche
« quirks » documentée.

## B. Plugin Android — le cœur de l'écart « il ne connaît pas le code »

### B8. Référentiel de ressources

**Comment fait Android Studio.** (Sources : [code] plugin Android
`com.android.tools.idea.res` ; [doc] providing-resources.)

- Hiérarchie de dépôts : `AppResourceRepository` (agrégat) = dépôts locaux
  (`ModuleResourceRepository`, `ProjectResourceRepository`) + **un dépôt par AAR**
  (`AarResourceRepository`, caché) + `resValue()` du build + sampledata ; les feuilles
  sont des `ResourceFolderRepository` (**un par dossier `res/`**) qui parcourent
  `res/<type>-<qualifiers>` et indexent : values (`<string>`, `<item type="id">`,
  `<declare-styleable>`), **ids `@+id/` des layouts** (dossiers « générant des IDs »)
  [code].
- Styleables : `R.attr.<name>`, `R.styleable.<Name>` (tableau d'IDs) et
  `R.styleable.<Name>_<attr>` (index) [code + doc].
- **Perf** : parseurs « plain file » (pas PSI) à l'init, bascule vers PSI pour les
  fichiers édités ; `PopulateCachesTask` parallélise ; **cache disque sérialisé**
  accélère les ouvertures suivantes [code].
- **MAJ incrémentale** : listener PSI par dépôt — ajout d'un `<item>`/`<string>` →
  insertion ciblée dans la map type→name→item + invalidation par type ;
  `ResourceNotificationManager` **coalescent** (une seule notification par rafale de
  frappes, après fin des mises à jour) [code].
- Alimentation de `R.*` : classes R light exposées par PsiElementFinder +
  ShortNamesCache + **ResolveScopeEnlarger** ; complétion décorée (icônes drawables,
  pastilles couleur + hex pour les couleurs, filtrage des ressources privées du module
  courant) [code]. R des AAR : construit depuis le **`R.txt`** de l'AAR [code].

**Implications pour nous.** Reproduisible **sans Gradle** : scan `src/*/res/**` par
conventions, parseur XML pull (léger ART), référentiel `type → name → (fichiers,
qualifiers, valeur)` ; stockage CIDX (segment `res:type/name`) + empreinte par
fichier ; MAJ ciblée sur didChange XML + invalidation par type ; `@+id` des layouts et
`<item type="id">` alimentent `R.id` ; AAR : lire `R.txt` si présent. Détail ADR 0020.

### B9. Classes « light »

**Comment fait Android Studio.** (Sources : [code] `android/docs/android-light-classes.md`
+ LightBindingClass/LightBrClass ; [doc] view-binding, AGP 8 release notes.)

- Le document de référence résume le pattern : l'IDE **simule les générateurs** (R,
  Data/View Binding, Safe Args) en maintenant le référentiel et en injectant des
  classes « fake » dans complétion et résolution. Trois prérequis : (1) l'API générée
  doit être **calculable par des règles simples et stables** ; (2) l'IDE n'a besoin
  que de l'**API**, pas des corps ; (3) l'IDE doit **savoir si la fonctionnalité est
  activée** (modèle Gradle). Composants : Model + implémentation de classe light +
  `PsiElementFinder` (les références ne sont pas rouges) + `ResolveScopeEnlarger` +
  `PsiShortNamesCache` (complétion/import) + navigation [code].
- **Anti-doublon** : les sources générées au build ne figurent **pas** dans les
  sourceSets du modèle (exclusion à la sync) [code].
- **R** : namespace AGP 8 obligatoire ; `nonTransitiveRClass=true` par défaut (R du
  module seul) ; `nonFinalResIds=true` (champs non-final) ; si FINAL, l'IDE cherche la
  classe R physique compilée [doc + code].
- **ViewBinding** : nom = fichier en PascalCase + « Binding » ; package
  `<module>.databinding` ; un champ par `android:id` (**`@Nullable` si l'ID est absent
  d'une des configurations** — ex. layout-land ; type `View` si incohérent ;
  `tools:viewBindingType` force le type) ; méthodes `inflate(LayoutInflater[, parent,
  attachToRoot])` (variante merge avec parent `@NonNull`), `bind(View)` ; exclusion par
  `tools:viewBindingIgnore` [doc + code].
- **DataBinding** : en plus — `BR` (champ `_all` + variables des layouts +
  `@Bindable` du code), getters/setters par variable, `*Impl` par configuration
  (suffixe du dossier) [code].
- **BuildConfig** : AGP 8 ne le génère plus par défaut (buildFeature) ; champs
  personnalisés par `buildConfigField` [doc]. [NV : liste exacte des champs standards
  sur la page consultée.]
- **Safe Args** : classes light in-memory « sinon le code resterait non résolu jusqu'au
  premier build » [code : README safeargs].

**Implications pour nous.** C'est le plan direct de l'ADR 0021 : fabrique de classes
synthétiques injectées dans **notre** annuaire/résolution (rôle du PsiElementFinder =
notre `AnnuaireClasspath`/stubs), règles de nommage/nullabilité reprises ci-dessus,
activation dérivée **de la lecture texte des build.gradle** (pas d'exécution Gradle),
priorité à la source réelle générée si présente (miroir de l'exclusion), suppression
propre de `DeclassementDatabinding` au profit de la résolution réelle.

### B10. Modèle Gradle

**Comment fait Android Studio.** (Sources : [doc] build-variants, dependencies,
java8-support, gradle-tips, AGP 8 notes.)

La sync fournit, par module/variante : variantes (flavors × dimensions + buildType,
`variantFilter`), sourceSets complets (java/kotlin/res/assets/manifest/aidl/rs/jni/
jniLibs/resources, par variante et personnalisés), dépendances par configuration
(implementation/api/compileOnly/runtimeOnly/**annotationProcessor/kapt/ksp**/lintChecks,
préfixées par variante), compileOptions (source/target compat, **core library
desugaring**), packaging, identité (namespace vs applicationId, suffixes par
flavor/buildType), SDK (compile/min/target, surcharges par flavor), `resValue`/
`buildConfigField`, buildFeatures, flags gradle.properties (`nonTransitiveRClass`,
`nonFinalResIds`, `defaults.buildfeatures.buildconfig`), catalogues de versions TOML,
lint (baseline, sévérités).

**Cartographie exacte pour `:model`** (champs à ajouter — ADR 0020) :
namespace/applicationId · compileSdk/minSdk/targetSdk · res/ et manifest par
sourceSet · buildFeatures (viewBinding/dataBinding/buildConfig) · processeurs
d'annotations (configuration séparée) · flavors/variants résolus (+ suffixes) ·
compileOptions/désucrage · `resValue`/`buildConfigField` par variante · dépendances
projet (résolution multi-modules) · packagingOptions (exclusions).

**Implications pour nous.** Le moteur doit être **dérivable depuis le disque** (parseur
Groovy/KTS tolérant + valeurs par défaut AGP 8 + settings.gradle pour les modules +
TOML pour les alias) : intelligent **avant la première sync** et après un build cassé
(axe de dépassement §0.1-2). L'hôte peut toujours surcharger par le modèle riche
(contrat à deux étages : dérivation disque par défaut, sync quand disponible).

### B11. Références ressources dans le code

**Comment fait Android Studio.** (Sources : [code] AndroidGotoDeclarationHandler,
RenameResourceReference, ResourceCompletionContributor, ResourceTypeDetector ;
[doc] annotations.)

- Navigation code→XML par goto declaration sur les champs R (module light ou `android.R`
  compilé) ; XML→code par find usages dédié [code].
- **Rename de ressource** : processeur de renommage qui **propage code + XML** avec
  détection de collisions ; famille inline/extract de styles et layouts [code].
- **Contrôles de type** : « Expected resource of type R.string » = contrôle lint
  `ResourceType` sur les annotations `@…Res` (liste complète des ~24 annotations +
  `@ColorInt`, `@Dimension`, `@Px`, `@HalfFloat`) [code] ; quick-fixes « créer la
  ressource » (value/file) [code].
- **Gouttière** : icônes couleur/drawable pour `R.color`/`R.drawable`/`R.mipmap` dans
  le code Java — via un **ExternalAnnotator** [code].
- Hover Java d'un champ R avec valeur résolue : **NV** (mécanisme précis non ouvert).

**Implications pour nous.** Navigation `R.string.x` → `strings.xml` (definition LSP) ;
rename de ressource multi-langages (code + XML) sur l'index étendu ; contrôle
`@…Res` = visiteur AST ecj lisant les annotations du callee (déjà résolues par ecj) ;
quick-fix « créer la ressource » ; couleur résolue en inlay/hover (ADR 0021/0028).

### B12. Annotations Android exploitées

**Comment fait Android Studio.** (Sources : [doc] annotations androidx ; [code]
AndroidAnnotationSupport, TypeDef contributors, détecteurs lint.)

- Nullité : préférence `androidx.annotation` > `android.support` > `android.annotation`
  > `com.android` > `Recently*` ; **annotations inférées du SDK** injectées [code].
- `@IntDef/@StringDef` : contrôle des valeurs autorisées **et complétion automatique
  des constantes** (priorisées, filtrage après « . », import automatique) ; `flag=true`
  autorise `|/&/^` [doc + code].
- `@IntRange/@FloatRange/@Size` : contrôle de bornes des arguments (lint `Range`) ;
  `@RequiresApi/@TargetApi` : contrainte locale de NewApi ; `@CallSuper` → MissingSuper
  Call (fix « ajouter super.x() ») ; `@CheckResult` (avec `suggest=`) ; thread
  (`@MainThread`↔`@UiThread` interchangeables) → WrongThread ; `@VisibleForTesting` ;
  `@SuppressLint` (suppression ciblée + quick-fix d'insertion) ; `@Dimension/@Px` :
  listés dans la référence, consommation exacte **NV** [doc + code].

**Implications pour nous.** Un **registre d'annotations** moteur : annotation connue →
diagnostic/complétion/fix. Ordre de valeur : `@IntDef/@StringDef` (effet
transformateur sur la complétion, purement local), `@…Res` (B11), `@RequiresApi`
(B13), `@CallSuper`/`@CheckResult`/`@IntRange` (visiteurs simples), thread (niveau 2).
Toutes lisibles dans android.jar/androidx (runtime visible en bytecode — notre lecteur
existants les expose).

### B13. Android Lint — contrôles à valeur Java

**Comment fait Android Studio.** (Sources : [code] lint-checks (ApiDetector,
ApiLookup, CallSuperDetector, ThreadDetector, SetTextDetector, CleanupDetector,
PermissionDetector, ResourceTypeDetector, UnusedResourceDetector, registry) ; [doc]
lint.)

- Chaque contrôle = `Issue` (id, sévérité, catégorie, priorité, `Implementation`
  avec **scopes**) du `BuiltinIssueRegistry` ; détecteurs UAST par noms de méthodes/
  annotations [code].
- **NewApi/InlinedApi** : base **`api-versions.xml`** — vérifiée sur l'artefact SDK
  `platform-35_r02.zip` : vit à `platforms/android-35/data/api-versions.xml`,
  **5 523 007 octets**, 6 480 `<class>` / 48 666 `<method>` / 31 098 `<field>` avec
  attribut `since` (absence = 1) ; compilée en **cache binaire** à côté (optimisations
  documentées : 4,7 Mo → 1,7 Mo en mémoire) [code + artefact]. Détection : niveau
  requis de l'appel vs **minSdk** du projet, échappatoires `@RequiresApi`/`@TargetApi`,
  gardes `SDK_INT`, dossiers `values-vNN`, `tools:targetApi` [code].
- `MissingPermission` (annotations `@RequiresPermission` + manifest) ;
  `ResourceType` (B11) ; `MissingSuperCall` (`@CallSuper`, collecte des `super.x()`)
  ; `WrongThread` (+ variante **interprocédurale** par lattice de contraintes) ;
  `UnusedResources` (**scope global uniquement** — manifest + toutes ressources + tout
  Java) ; `ObsoleteSdkInt` ; `CommitPrefEdits`/`Recycle` (dataflow : `obtain()` →
  `recycle()`/try-with-resources/chaînage returnsSelf) ; `SetTextI18n` (setText sur
  sous-classe de TextView, littéral ≥ 2 caractères-mot) [code].
- Le contrôle lint historique « Override » (annotation) est **supprimé** du registre
  (deprecated au profit de l'inspection standard) [code].
- **IDE vs Gradle** : lint intégré = pop-up éditeur + fenêtre Inspect Code ; tâche
  `lint<Variante>` en rapport XML/HTML ; « lint n'est PAS exécuté automatiquement
  pendant le build » ; baseline `lint-baseline.xml` [doc].

**Implications pour nous.** (1) Embarquer/consommer `api-versions.xml` (SAX simple ;
format binaire compact CIDX `owner+name+desc → niveau`) ou le lire du SDK local ;
(2) première vague = contrôles **mono-fichier** (NewApi, InlinedApi, ObsoleteSdkInt,
MissingSuperCall, WrongThread naïf, CheckResult, Range, ResourceType, SetTextI18n,
CommitPrefEdits, Recycle simple) : AST ecj + annotations résolues + minSdk dérivé (B10)
+ base API ; (3) UnusedResources et WrongThread interprocédural = mode global différé
(index). ADR 0024.

### B14. Intégration lint on-the-fly

**Comment fait Android Studio.** (Sources : [code] LintExternalAnnotator.kt,
lint-plugin.xml, ExternalAnnotator.java ; [doc] annotator SDK.)

- Le circuit : `collectInformation` (trouve le module, capture le profil d'inspections
  et le texte) → `doAnnotate` (**thread de fond**) : crée un client lint « editor »,
  **choisit le scope selon le type de fichier** (Java/Kotlin → `JAVA_FILE_SCOPE` ;
  manifest → `MANIFEST_SCOPE` ; XML → `RESOURCE_FILE_SCOPE`), **abandonne si les index
  sont en cours de construction**, requête **incrémentale sur le seul fichier ouvert**,
  lance le vrai moteur lint in-process → `apply` : mappe en `ProblemHighlightType`,
  tooltip HTML, attache les quick-fixes [code].
- **À la frappe tournent** les issues dont l'Implementation inclut le scope
  mono-fichier (NewApi, MissingSuperCall, CheckResult, Range, ResourceType,
  SetTextI18n, Recycle, CommitPrefEdits...) ; les globaux (UnusedResources) et
  l'interprocédural ne tournent **qu'en batch** [code]. Chaque contrôle lint est exposé
  comme inspection `AndroidLint<Id>` [code].

**Implications pour nous.** Modèle direct pour notre LSP : nos diagnostics ecj rapides
= annotateurs réguliers ; une **seconde passe asynchrone « lint-like »** par fichier
ouvert (après la diagnose ecj, seulement si pas d'erreurs bloquantes — même garde que
`collectInformation`), publiée dans le même `publishDiagnostics` avec **source/
provenance** marquée (ADR 0022) ; gating par scope (mono-fichier par défaut, global sur
commande) ; `@SuppressLint` reconnu par le pipeline de suppression.

## C. Ce qu'Android Studio fait mal ou lentement (cibles de dépassement)

- **Indexation bloquante des fonctions « smart »** (dumb mode pausable mais réel) ;
  mémoire en centaines de Mo à Go ; démarrage en minutes sur gros projets [doc].
- Faux positifs de résolution documentés (A7 : IDEA-118527/163950/211990) — ecj les
  évite par construction.
- Le lint on-the-fly n'est qu'un sous-ensemble du lint batch (gating par scope) —
  nous ferons pareil, honnêtement annoncé.

**Où l'on ne prétend pas surpasser** (consigné noir sur blanc, conformément au §0.1) :
le catalogue ~900 inspections + ~20 ans de refactorings, la complétion ML entraînée sur
des données d'usage massives (nous : statistiques locales opt-in, ML rejeté en v1), le
support multi-langages natif. Stratégie : **couvrir d'abord les 20 % de contrôles/
actions qui font 80 % de la valeur d'un développeur Android**, puis élargir par vagues
(matrice §03, plan §04).

## D. Sources consultées (liste consolidée)

- **Docs JetBrains/SDK** : help/idea (intention-actions, auto-completing-code,
  auto-import, indexing, project analysis, generating-code, refactoring-source-code,
  extract-method, change-signature, safe-delete, find-usages, inline, move, rename,
  postfix, live-templates, configuring-inspection-severities, code-inspection,
  nullable-and-notnull-annotations, supported-java-versions) ; plugins.jetbrains.com
  docs (code-completion, code-inspections, code-intentions, annotator,
  indexing-and-psi-stubs, file-based-indexes, stub-indexes) ; Inspectopedia
  (Java.html + ~25 catégories/pages).
- **Blogs JetBrains** : Code Completion Episode 4 (ML, QuerySoftMax +10 %), Shared
  Indexes (−75 %).
- **Code source public** (lecture seule) : JetBrains/intellij-community @ master —
  platform/analysis-api (modcommand/*, LocalInspectionTool, IntentionAction,
  HighlightSeverity), platform/analysis-impl (CompletionInitializationUtil,
  BaseCompletionLookupArranger, daemon/impl GeneralHighlightingPass,
  DaemonCodeAnalyzerImpl, FileStatusMap, TextEditorBackgroundHighlighter),
  platform/lang-impl, java/java-impl (JavaCompletionContributor,
  JavaSmartCompletionContributor, MakeVoidQuickFix), java/java-psi-impl
  (graphInference/InferenceSession), java/java-analysis-impl (JavaSuppressionUtil,
  UnusedSymbolLocalInspection) ; **plugin Android** (tools/adt/idea @
  mirror-goog-studio-main) — android/docs/android-light-classes.md,
  com.android.tools.idea.res/* (AppResourceRepository, ResourceFolderRepository,
  ResourceNotificationManager, AarRClasses, GradleModulePackageRClass,
  ResourceCompletionContributor), databinding/* (LightBindingClass, LightBrClass,
  BindingLayout), nav/safeargs, org.jetbrains.android (AndroidGotoDeclarationHandler,
  refactoring/renaming/RenameResourceReference, inspections), lint/* (
  LintExternalAnnotator, LintIdeSupport, LintGlobalInspectionContext, lint-plugin.xml) ;
  **lint-checks** (tools/base) — ApiDetector, ApiLookup, Api, BuiltinIssueRegistry,
  PermissionDetector, ResourceTypeDetector, ThreadDetector, ThreadConstraintDetector,
  CallSuperDetector, SetTextDetector, UnusedResourceDetector, CleanupDetector,
  CheckResultDetector, RangeDetector, OverrideConcreteDetector.
- **Docs Android** : developer.android.com (lint, annotations, view-binding,
  data-binding, build-variants, dependencies, java8-support, gradle-tips,
  configure-app-module, tool-attributes, providing-resources, more-resources, AGP 8.0
  release notes, androidx.annotation package-summary) ; artefact SDK
  platform-35_r02.zip (api-versions.xml inspecté).
- **YouTrack** : IDEA-118527, IDEA-163950, IDEA-211990 (+ résultats de recherche
  « compiles fine »).
- Les points restés **NV** sont listés dans chaque fiche et regroupés dans
  `06-protocole-captures-android-studio.md` (ce que le propriétaire doit capturer).
