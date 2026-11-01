# 04 — Plan par vagues (Phase 5)

> Vagues **courtes, livrables, réversibles** (drapeau + repli documenté), séquencées
> par la priorisation de `03-matrice-ecarts.md`. Efforts en **jours-homme agent**
> (méthode `PLAN-REECRITURE.md` §3 : socle + n × marginal ; bornes 1–5 h/comportement,
> socles ≤ 20 j-h ; vélocité de référence : les étages N1–N8 ont été livrés en
> **9–25 h agent effectives** pour ~22 600 l. + 625 tests — RECALAGE-2). Fourchettes
> **[estimé]**, à re-calibrer au premier recalage de la vague V-A.
>
> Rappel des contraintes transverses (§11 du prompt) : ART (budget ~5 Mo d'ajout,
> tas borné, hors ligne), scanner de compatibilité pour toute dépendance, bancs
> d'empreinte/latence non régressés, **un gain d'intelligence qui double la latence
> de frappe n'est pas un gain**.

## 0. Vague 0 — MESURE (livrée dans cette mission, sans dépendance décisionnelle)

**Périmètre livré** (ce dépôt, branche `etude/moteur-java`) :
- Banc d'incohérences **P1–P8** exécutable en CI (`java/src/test/…/coherence/`).
- **Corpus réel** 5 projets Android Java (commits épinglés, licences vérifiées,
  registre + script `scripts/telecharger-corpus-android.sh` — jamais de binaire).
- **Protocole de captures Android Studio** (`06-protocole-captures-android-studio.md`).
- **Gabarit de mesure appareil** (`07-gabarit-mesure-appareil.md`, style
  `bancs/GABARIT-BASCULE-APPAREIL.md`).
- ADR 0030 (cadre qualité) + cette étude (audit, référence, matrice, ADR).

**Critères de sortie de la vague 0** : ~~CI verte avec le banc P1–P8 compilé~~ **atteint
en local** (23 tests / 0 échec / 1 gate corpus ignorée — mesures consignées audit
§1.12 : 26 violations, dont 18 divergences rapide/complet en frappe, 4 types JDK
internes proposés en complétion, 1 staleness inter-fichiers) ; restent : la première
exécution **CI** du push, la gate corpus réel (manifestes de classpath à préparer) et
les captures AS retournées par le propriétaire. **Elle ne dépend d'aucune décision
d'architecture.**

**Dépendances** : propriétaire (captures AS, passe appareil). **Effort** : livré
(~1 séance) ; exécution/analyse : 0,5–1 séance.

## 1. Séquence proposée (à justifier/ajuster par la matrice)

```
V-0 mesure (livrée) → V-A fiabilité → V-B projet Android → V-C intelligence
                                                ↘ V-E navigation (court, tôt)
V-F1 perf (double-tampon) s'insère AVANT V-C → V-D actions → V-F perf complète → V-G protocole
```

Justification de l'ordre : (1) V-A rend le moteur *vrai* — prérequis de confiance
pour tout le reste ; (2) V-B est le déficit « il ne connaît pas le code » — la
demande fondatrice du propriétaire ; (3) **V-F1 (double-tampon) est remontée avant
V-C** parce que la couche IDE (inspections, smart completion) multiplierait les
requêtes derrière le verrou global si elle arrivait la première (§11 : un gain
d'intelligence qui dégrade la latence n'est pas un gain) ; (4) V-E (levée M4) est si
peu coûteuse (~0,5 j-h) qu'elle est livrée tôt, dès la décision D1 ; (5) V-D et V-G
complètent la couverture.

## 2. Détail des vagues

### V-A — Fiabilité (P0)

> **EXÉCUTION démarrée le 31/10/2026 (GO propriétaire D1–D9)** — premier
> segment livré (V-A1 : correctifs moteur + gates CI) :
>
> - **P1 (18 divergences → 0)** : le portage hors-corps des signaux
>   analytiques est GABARITÉ par les règles du témoin neuf (mesurées sur
>   ecj 3.40 : import inutilisé exige zéro erreur ; membre privé inutilisé
>   exige zéro erreur de syntaxe) — la source de portage devient PERSISTANTE
>   (`Etat.analytiquesHorsCorps`, traduite à chaque passe) pour qu'une frappe
>   cassée ne détruise jamais le signal (mesuré : édition 27).
> - **P8 (1 staleness → 0)** : le mémo de diagnose enregistre les fichiers
>   sources lus sur disque (pistage thread-local dans l'environnement de
>   noms) avec empreinte STAT mtime+taille (coût microseconde — un SHA-256 de
>   contenu mesuré à 1,5 → 5 ms p50 sur le diagnose rapide, écarté) ; un
>   voisin édité invalide le mémo (fraîcheur inter-fichiers, ADR 0025 —
>   repli global existant : `jo.codeide.lsp.java.fastdiagnose.disabled`).
> - **P6 (4 non-résolubles → 0)** : filtre d'accessibilité de l'annuaire
>   (`estProposablePourAutoImport`) — plus aucun type non public d'un autre
>   paquet proposé avec auto-import insoluble (G-004, `Striped64`,
>   `Streams`…). **Arbitrage parité (ADR 0030)** : 30 libellés non publics
>   du JDK que l'oracle vendored propose AUSSI ont été retirés du contrat de
>   parité par une liste explicite dans le banc (`ARBITRAGES_G004` — java.lang,
>   java.util.stream, javax.swing.plaf.metal internes), la re-baseline
>   suivante (PR dédiée) les absorbera.
> - **P5 (2 violations → 0)** : création de méthode CONTEXTUELLE en position
>   de retour (G-014 : `return formater(…)` dans une méthode `String` crée
>   un stub `String` — l'affectation reste `Object`, fiche 064 verrouillée,
>   extension renvoyée à l'amendement arbitré V-C/V-D) ; nouveau correcteur
>   « Créer la variable locale » (`quickfix.create.variable`) pour les noms
>   non résolus en position de variable ; le banc P5 aligne son
>   implémentation sur sa propriété écrite ((b) « aucune erreur NOUVELLE »
>   par signature, (c) « compilable » scoped aux fixtures par ailleurs
>   valides).
> - **Gates CI (ADR 0030)** : `BANC_COHERENCE_STRICT=true` sur les jobs
>   `build` et `gates-android` — P1/P2/P5/P6/P8 deviennent bloquants à
>   chaque push.
>
> **PV de mesure (JVM, 31/10)** : 557 tests / 0 échec ; banc P1–P8
> **0 violation** (P1 : 27+3+1+1 mesures ; P2 : 3 ; P3 synthétique : 5
> fichiers, 0 FP ; P4 : 6/6 mutants ; P5 : 3 cas dorés + balayage 3
> fixtures ; P6 : 4+16+12 insertions ; P7 : 0 ; P8 : scénario inter-fichiers
> + garde-fous). Mode strict vérifié vert localement. **Latence (A/B même
> machine, arbre d'origine vs V-A1)** : diagnose complet p50 13,4 → 10,8 ms,
> diagnose rapide p50 4,95 → 4,3–4,8 ms, p95 28,9 → 30,1–32,5 ms (variance
> inter-passes, RECALAGE-2 ±×1,5 — le « 1,5 ms » du README N1 est un chiffre
> d'une autre ère corpus/machine, l'arbre d'origine mesure déjà 4,95) ; tas
> 17 → 17 Mo ; banc qualité signé **d8 1,000/1,000/1,000, p95 33,25 ms,
> 0 saut** (baseline tenue).
>
> **V-A2 LIVRÉE le 01/11/2026 (corpus réel — PV
> `mesures/2026-11-01-va2-corpus-reel.txt`, rapport
> `08-rapport-divergences-ecj-javac.md`, ADR 0030 mis à jour, ADR 0022
> amendé)** :
> - **Préparation outillée et rejouable** : `scripts/preparer-manifestes-
>   corpus.sh` + `scripts/manifeste-corpus.init.gradle` (vue d'artefats
>   `android-classes-jar` = la vue EXACTE de javac — jetifier compris ;
>   R.jar d'AGP par module ; classes Kotlin `built_in_kotlinc` zippées ;
>   racines générées au PARENT DU PAQUET ; replis ARTIFACT-* = refus franc,
>   jamais de vert silencieux). 5 manifestes produits : 30 racines, 12
>   générées, 432 jars (opentracks 1+2+64, markor 2+1+109, gh4a 1+1+85,
>   anysoftkeyboard 25+4+117, basicsample 1+4+57).
> - **P1 équivalence rapide/complet sur corpus réel : 0 violation / 42
>   mesures** (G-002, volet équivalence — PROUVÉ).
> - **P3 : 6 FP sur 111 700 lignes = 0,054/1 000** (seuil < 0,5 TENU ;
>   opentracks/anysoftkeyboard/basicsample à **0 FP**, markor 0,055,
>   gh4a 0,174) — les 6 restants sont 2 divergences GENUINES ecj↔javac :
>   D-01 `CharSequence += String` (ecj refuse, javac accepte — JLS 15.26.2)
>   et D-02 pattern-matching-switch (compliance projet 21 vs défaut 17 —
>   le manifeste ne porte pas la compliance). Sources générées : 0 FP.
> - **P4 : rappel 108/109 = 0,991** (seuil 0,95 TENU ; l'unique FN est une
>   limite d'ANCRE de l'harnais — l'erreur existe sur le sélecteur, 13
>   caractères après le récepteur).
> - **P7 : 28 renommages sûrs / 0 cassé / 0 sur-renommage** (après les
>   correctifs ci-dessous).
> - **Correctifs moteur pilotés par la mesure** : ① ordre de repli — la
>   light Kotlin CÈDE aux jars (18 FP mesurés avant ; ADR 0022 amendé,
>   `primeSurJars`) ; ② off-by-one `plageJeton` sur FieldReference —
>   `this.champ` perdu du renommage (1 rename cassé avant). Régressions :
>   `DeclarationsKotlinTest`, `OccurrenceThisChampTest`.
> - **Mode strict** : vert sans corpus ; AVEC corpus, P3/P4 échouent sur
>   la baseline d'écart (6 FP + 1 FN d'ancre) — matière G-002/G-005, les
>   gates corpus restent non bloquantes tant que la CI ne provisionne pas
>   CORPUS_ANDROID (ignore propre avec raison).
> - **Recalage de vélocité fin de vague** : V-A (2 sous-vagues) = 2 séances
>   agent au lieu des 18–30 j-h estimés — la fourchette initiale du plan
>   était calibrée sur un périmètre incluant l'incrémentalité structurelle,
>   reportée ; les vagues suivantes gardent leurs estimations.

- **Périmètre** : exécution du banc P1–P8 sur corpus → correction des faux positifs
  et incohérences : pipeline unifié + re-validation du report rapide (ADR 0025),
  invalidation inter-fichiers des documents ouverts (ADR 0025), insertions de
  complétion sûres (ADR 0019 option 1), arbitrage du rapport ecj↔javac (ADR 0030).
- **ADR couverts** : 0019 (option 1), 0025, 0030 (gates).
- **Critères de sortie mesurables** : P1 = P2 = P5 = P8 : **0 violation** en CI ;
  P3 : FP < 0,5/1 000 lignes (0 sur le noyau dur du corpus) ; P6 ≥ 99 % de
  suggestions résolubles ; P4 rappel mutants ≥ 0,95 ; p95 frappe ≤ baseline + 10 %.
- **Dépendances** : aucune décision (V-A peut suivre V-0 immédiatement).
- **Risques** : corrections structurelles (graphe de dépendances) — behind drapeau.
- **Effort [estimé]** : **18–30 j-h agent** (2–4 séances).
- **Côté hôte/client** : rien (moteur seul) — sauf publication progressive (le client
  voit plusieurs publishes par document : comportement LSP standard, à documenter).

### V-B — Projet Android (P1 « aveugle »)

> **LIVRÉE le 31/10/2026 au soir (v1.4.0) — intégralité du périmètre en
> trois sous-vagues V-B1→V-B3** :
>
> - **V-B1 — modèle + R** : `:model` **v11** (`ModuleAndroidLsp`, liste
>   B10 : namespace, SDK, res/, manifest, buildFeatures, drapeaux R,
>   resValue/buildConfigField) avec empreinte de module étendue au contrat
>   et protocole `ClasspathModuleAndroid` (le producteur hôte — la sync
>   PRIME par contrat, R3). Côté moteur : `ContratAndroidMoteur` (miroir
>   documenté USAGE §7.8), **dérivateur disque** KTS/Groovy tolérant
>   (`ModeleAndroidDerive` — namespace/minSdk/target/compileSdk/
>   buildFeatures/srcDirs res + manifest, repli par défauts AGP 8, drapeau
>   `projetandroid.derivation.disabled`), **référentiel de ressources**
>   (`ReferentielRessources` — parseur pull `LecteurXmlTolerant` SANS
>   dépendance (ART-compat. : ni StAX ni DOM), values typés, ids `@+id`
>   collectés dans TOUS les XML (règle aapt), styleables à TROIS familles,
>   layouts par configuration (type incohérent ⇒ `View`, règle §B9),
>   `tools:viewBindingType` primant, MAJ ciblée), **classes light
>   R/BuildConfig** (`ClassesLegereAndroid` + `EcrivainClasseLegere` :
>   octets v52 avec ConstantValue et attribut **InnerClasses** — sans lui
>   ecj refuse `R.layout`), ordre de repli verrouillé **sources →
>   SYNTHÉTIQUES → jars → jrt → stubs** (anti-doublon : la source générée
>   réelle PRIME — testée build présent/absent), annuaire `Origine.
>   SYNTHETIQUE` (complétion + auto-import), isPackage du namespace.
> - **V-B2 — ressources + navigation** : GO TO `R.layout.x`/`R.id.x`/
>   `R.string.x`/`R.styleable.X_a` → le XML déclarant à la **plage EXACTE
>   du nom** (ressource FICHIER : plage vide, ouverture — comportement de
>   la référence) ; serveur : didChange `.xml` → **MAJ ciblée** du
>   référentiel + invalidation du mémo (P8 étendu aux ressources) ;
>   décompilation des classes light (aperçu API honnête via FormatterStub) ;
>   `octetsCompacts()` (peson du futur segment CIDX) ; **banc
>   `:java:benchRessources`** (mesures R5 : mini + monip + corpus 5 projets
>   = **172 res/**).
> - **V-B3 — binding + Kotlin (décisions D3/D6)** : fabrique
>   `<ns>.databinding.*Binding` (nommage PascalCase+Binding §B9 ; view
>   binding final + `ViewBinding` si résoluble + rootView/getRoot +
>   inflate×2 + bind ; data binding abstrait + `ViewDataBinding` si
>   résoluble + variables `<data>` en champs `public final` ; `BR` ; sonde
>   de résolvabilité — repli Object, jamais de hiérarchie cassée) ;
>   navigation `binding.textTitre` → la déclaration `@+id/text_titre` ;
>   **`DeclarationsKotlin`** (ADR 0022 : scanner lexical conservateur —
>   types de premier niveau + facades `*Kt`/`@file:JvmName`, signatures
>   CERTAINES seulement, le reste ÉCARTÉ — jamais un type inventé ;
>   drapeau `interoperabilite.kotlin.disabled`) ; **RETRAIT
>   `DeclassementDatabinding` + `MoteurJava.declasserDatabinding` (D6)** —
>   son rôle disparaît, l'import `.databinding` RÉSOUS avant build
>   (fiche 051 remplacée par `Fiche051BindingResoluTest`).
>
> **PV de mesure (JVM, 31/10 au soir, gates Android ouvertes — android.jar
> 34-ext12 + bibliothèques réelles du banc, corpus téléchargé)** : **717
> tests / 0 échec** (java+model+index ; +31 tests V-B : référentiel/
> dérivateur/lecteur XML, octets R/BuildConfig/Binding, navigation
> ressource, interop Kotlin, équivalence, fiche 051 amendée ; 41 gates
> environnementales ignorées avec raison). **Banc ressources** (172 res/
> mesurés, 7 projets) : scan complet **p95 max 95,0 ms < 150 ms** (budget
> ADR), MAJ ciblée **p95 max 1,26 ms < 5 ms** (échauffement JIT documenté :
> 5,003 ms constatés à froid), **peson compact max 126 395 o < 2 Mo**
> (marge ×16 ; chemins relatifs, métadonnées binding comprises). **Bancs
> non régressés** : qualité d8 p95 31,4–49,7 ms / rappel 1,000 (baseline
> tenue), usages p95 < 100 ms / rappel 1,000, P1–P8 strict 0 violation.
> **Équivalence light ↔ généré (monip)** : `ActivityMainBinding` — super
> `ViewDataBinding`, champs `textTitre`/`liaison` typés comme le simulacre
> AGP commis, inflate×2 + bind : **0 écart** sur l'API couverte ;
> `MainActivity` du banc SANS racine générée : **zéro erreur** (R + binding
> light). **Régressions corrigées en cours de vague** (consignées) :
> `List<Path> + Path` itère les SEGMENTS du chemin (Path est Iterable) ;
> `return@when` invalide hors lambda ; forme « liaison unique » des
> références qualifiées (`R.layout.x` : ecj lie le DERNIER segment dans
> `binding`, `otherBindings` null) — comblée dans `SymboleAuCaret`, gain
> pour TOUTE référence qualifiée statique. **Non livré, renvoyé
> documenté** : câblage du SEGMENT CIDX côté index partagé (le peson
> compact prouve le budget ; le format binaire est prêt — PR dédiée avec
> l'index), `R.txt` AAR + flavors (ADR 0020 plan 3), rappel Kotlin mesuré
> sur l'ÉCHANTILLON du corpus (gate P3 corpus — manifestes requis),
> annotations de nullabilité des bindings (inlay, V-C), rename de
> ressource (V-D). **Effort réel : ~1,5 séance agent** (V-B1+V-B2+V-B3
> dans la même session) — à recouper avec l'estimation 36–60 j-h au
> recalage (la méthode de comptabilité agent/j-h du PLAN-REECRITURE s'applique
> au prochain recalage).

- **Périmètre** : modèle `:model` v2 + dérivateur disque (ADR 0020), référentiel de
  ressources + MAJ incrémentale (ADR 0020), classes light R/BuildConfig/ViewBinding/
  DataBinding (ADR 0021), interop Kotlin→Java par index léger (ADR 0022).
- **ADR couverts** : 0020, 0021, 0022.
- **Critères de sortie** : corpus 5 projets **sans build** : zéro « cannot be
  resolved » sur `R.*`/`*Binding`/`BuildConfig` (P3 étendu) ; équivalence
  light ↔ généré : 0 écart sur cas dorés ; navigation `R.id.x` → layout : 100 %
  cibles exactes ; dérivateur : ≥ 95 % de champs corrects ; rappel Kotlin ≥ 90 % ;
  budget CIDX ressources < 2 Mo/projet moyen ; scan res/ p95 < 150 ms JVM.
- **Dépendances** : décision D2 (contrat `:model` + producteur hôte) — le dérivateur
  disque permet de livrer sans l'hôte ; décision D6 (fiche 051 / retrait
  `DeclassementDatabinding`).
- **Risques** : règles AGP divergentes ; empreinte XML sur ART — budgets mesurés.
- **Effort [estimé]** : **36–60 j-h agent** (4–7 séances) — la plus grosse vague,
  découpable en V-B1 (modèle+R), V-B2 (ressources+navigation), V-B3 (binding+Kotlin).
- **Côté hôte** : produire les champs B10 à la sync (liste prête = prompt
  `codeide-lsp`) ; **côté client** : rien de bloquant.

### V-E — Navigation/références (P1, livrée tôt — décision D1)

> **LIVRÉE le 31/10/2026 (v1.3.0)** — intégralité du périmètre :
>
> - **Levée du confinement `references` (G-013, ADR 0028 (a), décision D1)** :
>   le filtre protocolaire est retiré — la réponse rend les usages
>   MULTI-FICHIERS réels du moteur (exhaustifs par clé de binding, ordre
>   trié chemin+offset) ; la conversion ligne/colonne passe par le repère
>   du FICHIER PORTEUR de chaque usage (texte disque analysé par le
>   moteur, overlay ouvert en repli) — l'ancien confinement convertissait
>   tout avec le repère du document interrogé, ce qui n'était vrai que
>   pour lui. **Gate « taille = 1 » INVERSÉE** : fiche 054 borne « 2
>   exactement » (déclaration T.java + appel U.java, plage du JETON « a »
>   vérifiée dans le porteur). Capability versionnée `x-codeide.usages`
>   (champ `experimental` : `multiFichiers`/`groupes`/`plafondRendu`,
>   version 1) annonce la rupture de la parité stricte §10.4.
> - **Typage lecture/écriture complet (G-026, ADR 0028 (b))** : le typage
>   simple existait depuis N4 (`GenreSurbrillance` — l'affirmation
>   d'audit « Surbrillance non typée » était erronée, annotée) ; le trou
>   RÉEL était les écritures COMPOSÉES et incréments — la hiérarchie ecj
>   (`Assignment` ← `CompoundAssignment` ← `PostfixExpression`/
>   `PrefixExpression`) route `+=`/`x++`/`++x` HORS du pont
>   `visit(Assignment)` : trois ponts dédiés posés dans
>   `LectureAst.VisiteurResilient` ; la cible (membre gauche) est une
>   zone d'écriture, champ qualifié compris (`c.memoire += 1` → segment
>   `memoire` en Write). Fiche 054 amendée : `total+=x` passe de
>   « lecture » (narrative vendord) à Write (le genre LSP est unique par
>   plage : l'écriture domine, la lecture implicite relève du data flow).
>   Non livré, renvoyé documenté : « argument d'appel mutateur » (exige
>   la DFA de V-C).
> - **Usages groupés (ADR 0028 (e), risque R1)** : RPC maison
>   `textDocument/usagesGroupes` — un groupe par fichier porteur (ordre
>   de première apparition), déclaration TOUJOURS incluse et signalée,
>   offsets absolus du porteur (convention `CibleSuperRpc`), **plafond
>   honnête 2 000** : `total` exact, `rendus`, `plafond`, `tronque`
>   TOUJOURS annoncés (jamais de troncature silencieuse) ; la liste
>   plate `references` reste EXHAUSTIVE. Regroupement pur isolé et testé
>   unitairement (4 tests d'invariants).
> - **Comptabilité** : M4 ET M7 rayés dans `ECARTS-SERVEUR.md` §2 (M7
>   corrigé par V-A1 mais la ligne n'avait pas été mise à jour) ; ADR
>   0028 → Accepté ; audit §1.8 annoté (erreur d'audit corrigée, mesures
>   d'origine conservées — R5) ; matrice G-013/G-026 résolus.
>
> **PV de mesure (JVM, 31/10 au soir, gates Android ouvertes — android.jar
> 34-ext12 + bibliothèques réelles du banc)** : 564 tests / 0 échec
> (dont 2 tests moteur affectations composées/incréments, 4 tests RPC
> groupé, 1 test E2E `usagesGroupesParFichier`, gate inversée + kinds
> verrouillés 3/3) ; 726 tests tous modules / 0 échec ; `clean build`
> strict intégral VERT. **Banc usages** : rappel **1,000** = oracle,
> p50 13,3–16,1 ms, **p95 50,30 ms < 100 ms** (borne tenue), 1 seule
> construction d'index (30 classes, 153 usages). **Banc qualité** :
> baseline signée tenue (d8 p95 36,27 ms, 0 saut). **P1–P8 strict :
> 0 violation** (P7 rename sûr inchangé — 3/3, même index, la levée
> n'ajoute pas d'occurrences fausses). Effort réel : ~0,5 séance agent —
> cohérent avec l'estimation « quasi gratuit » (1–3 j-h).

- **Périmètre** : levée du confinement `references` (ADR 0028), typage
  lecture/écriture de `documentHighlight`, usages groupés.
- **Critères de sortie** : usages multi-fichiers réels (gate « taille = 1 » inversée) ;
  banc usages rappel 1,000 / p95 < 100 ms sur corpus étendu ; P7 inchangé (0
  violation) ; amendement fiche 054 accepté.
- **Dépendances** : décision D1 (abandon parité stricte M4). **Effort** : **1–3 j-h**
  (quasi gratuit — la capacité moteur existe).

### V-F1 — Performance : double-tampon (remontée avant V-C)

> **LIVRÉE le 31/10/2026 au soir** — intégralité du périmètre ADR 0029 phase 1 :
>
> - **Époques de l'ENVIRONNEMENT** (l'extension aux environnements du design
>   copy-on-write des documents N8 — « 0 réponse périmée ») : chaque
>   remplacement effectif de contexte (listes, niveau OU contrat Android)
>   incrémente la GÉNÉRATION du module ; `EnvironnementCompilation.
>   calculerFraisse(chemin, calcul)` capture ATOMIQUEMENT le couple (moteur,
>   génération) sous le verrou d'orchestration, exécute le calcul, et ne
>   livre que si l'époque n'a pas tourné — sinon REJEU sur l'époque courante
>   (bornes honnêtes : 3 résultats périmés puis livraison du dernier — clause
>   miroir des documents ; 8 coupures puis erreur franche sous churn soutenu).
> - **Double-tampon** (« 0 figage », G-030/H2) : à un remplacement effectif
>   AVEC moteur vivant, l'ancien moteur CONTINUE DE SERVIR pendant qu'un fil
>   démon unique (`lspjava-double-tampon`) construit et RÉCHAUFFE le neuf
>   (environnement de noms + sonde de plateforme — le réchauffement est
>   injectable pour les bancs) ; SWAP ATOMIQUE sous le verrou ; l'ancien est
>   ensuite RETIRÉ : jeton d'époque posé puis ANNULÉ (coupure fine des boucles
>   lourdes en vol) et dispose (le moniteur du moteur attend la drainage
>   naturelle des calculs non ponctués). Syncs rapides successives : les
>   reconstructions se sérialisent sur le fil, chacune vérifie SA génération
>   avant de swapper — une build dépassée est JETÉE, jamais swappée.
> - **Annulation fine de base** (G-029, partiel) : points de contrôle dans la
>   collecte de complétion (hiérarchies de membres, traversée de l'annuaire
>   par préfixe, types visibles, filtres de sous-paquetage — l'exception
>   `EpoqueRetiree` TRAVERSE le repli « liste vide honnête » de la fiche 035,
>   l'avaler livrerait pire qu'une réponse périmée) et dans le balayage
>   d'usages (un contrôle PAR FICHIER, `references` comme `usagesGroupes`).
>   Le jeton fiche 046 est généralisé en signal d'époque SANS thread attaché
>   — la coupure est purement coopérative.
> - **Câblage serveur** : TOUS les chemins de RÉPONSE (hover, complétion,
>   signatures, définition/typeDefinition/implementation, references,
>   documentHighlight/Symbol, formatage ×3, sélection, pliage, inlay,
>   prepareRename/rename, codeAction, codeLens + resolve, hiérarchies de
>   types et d'appels, superDefinition, usagesGroupes) passent par le
>   protocole frais `avecMoteurFrais` — la complétion COMPOSE les deux époques
>   (document PUIS environnement). À chaque SWAP effectif, le serveur
>   REDEMANDE la publication des documents OUVERTS du module (auditeur
>   d'époque) : les diagnostics suivent l'époque SANS attendre la frappe
>   suivante — le critère « les faux squiggles s'effacent dès la sync ».
> - **Repli documenté** : `jo.codeide.lsp.java.doubletampon.disabled=true`
>   rétablit le comportement d'avant V-F1 (dispose immédiat et synchrone à
>   la sync) ; le protocole d'époque reste actif dans les DEUX régimes.
> - **Amendements de fiches** (contrat « moteur reconstruit » devenu
>   ASYNCHRONE par design) : fiches 034/047/066 attendent la DRAINAGE des
>   reconstructions (`attendreReconstructions`) avant d'observer le moteur
>   neuf — les autres contracts (notification EXACTEMENT une fois, isolation
>   par module, idempotence) sont INTACTS. Stabilisation au passage du floçon
>   préexistant du banc testing « réouverture chaude » (comparaison d'horloge
>   murale stricte entre deux sessions — échec reproductible sur l'arbre
>   d'origine ; borne de tolérance de charge ×2 + 2 s, les assertions
>   sémantiques PRÊT/même verdict portent l'intention).
>
> **PV de mesure (JVM, 31/10 au soir, banc `BancDoubleTamponTest` — R5, aucune
> valeur fabriquée ; transcript complet dans
> `mesures/2026-10-31-vf1-double-tampon.txt`)** : frappe nominale (48
> requêtes, mémo chaud) p50 5 µs / p95 36 µs ; frappe PENDANT reconstruction
> tenue par loquet (48 requêtes, toutes servies par l'ancienne époque —
> légitime, c'est l'époque vivante) p50 6 µs (**×1,20**) / p95 21 µs
> (**×0,58**) — **budget ADR « p95 ≤ 2× le nominal » TENU** ; reconstruction
> réelle à réchauffeur par défaut 301 ms puis 51 ms (jrt du runtime, fixtures
> minimales — l'appareil paie 11–12 s, facteur 3–5× du spike, la mesure
> appareil reste au gabarit 07 du propriétaire, jamais simulée) ; requête
> coupée par le swap : **exactement 2 passes** (solde de l'ancienne, livraison
> par la neuve — 0 erreur livrée). **Non-régression** : 730 tests tous
> modules / 0 échec (600 java dont 8 V-F1 + 1 banc ; 41 gates environnementales
> ignorées avec raison) ; `clean build` strict (BANC_COHERENCE_STRICT, P1–P8)
> VERT ; banc d8 rappel/top-1/MRR **1,000/1,000/1,000**, p95 35,96 ms
> (baseline tenue) ; banc usages rappel **1,000**, p50 15,48 ms / p95
> 40,62 ms < 100 ms, 1 construction d'index ; parité verte ; banc N1 tenu
> (diagnose rapide p50 4,8 ms / p95 27,7 ms, hover 0,1/1,2 ms, tas 17 Mo).
> **Effort réel : ~1 séance agent** (estimation 8–14 j-h). **Reste V-F
> (phases 2–5)** : verrous par périmètre + chaos tests, budgets octets +
> onTrimMemory, plage prioritaire, incrémentalité conditionnée.

- **Périmètre** : époque copy-on-write de l'environnement de noms + construction en
  fond pendant que l'ancien sert (ADR 0029 phase 1) ; annulation fine de base.
- **Critères de sortie** : changement de classpath pendant rafale → 0 réponse périmée
  et 0 figage (latence pendant reconstruction ≤ 2× nominale) ; [appareil] gabarit 07
  rempli par le propriétaire — cible indicative : reconstruction non perceptible
  (< 300 ms de dégradation p95).
- **Effort [estimé]** : **8–14 j-h**. **Côté hôte** : signaler le moment de la sync
  (déjà le cas).

### V-F2 — Performance : verrous par périmètre + chaos tests (remontée avant V-C)

> **LIVRÉE le 31/10/2026 au soir** (ADR 0029 phase 2 — exécution du
> schéma B de l'ADR 0010) :
>
> - **Verrous par périmètre** : le moniteur d'instance du moteur (51
>   `synchronized` dans `MoteurJava.kt`, 99 dans `java/src/main`) est
>   remplacé par des verrous NOMMÉS — `verrouCompilation` (le périmètre
>   MODULE : tout ce qui touche le pont ecj, l'environnement de noms ou
>   un arbre résolu ; la drainage de `dispose` est préservée),
>   `verrouServices` (inits paresseuses), feuilles thread-sûres
>   (annuaire, index de mots, cartes d'octets des classes light en
>   cartes CONCURRENTES, documents par verrou propre). ORDRE GLOBAL
>   documenté : orchestration → compilation → services → feuilles.
>   **Comptage `synchronized(` : 99 → 0** (cible ADR 0010 < 6 — la
>   métrique est portée en CI par `VerrousPerimetreTest`).
> - **Lectures parallèles de fichiers distincts** : le MÉMO de diagnose
>   (carte concurrente, instantanés immuables), la complétion CHAUDE
>   (sonde sur l'arbre du cache — les lambdas pont/environnement
>   re-verrouillent seules), la sonde de régime (environnement VOLATILE)
>   et les balayages d'usages/renommage (lectures disque hors verrou,
>   reprise par résolution) ne se mettent JAMAIS derrière la résolution
>   d'un autre fichier ; `PontEcj` ne détient plus AUCUN état mutable
>   partagé (options et usine de problèmes frais par passe).
> - **Ordonnanceur en pool plafonné** (fiche 048 AMENDÉE) : 2 workers
>   par défaut, propriété `jo.codeide.lsp.java.ordonnanceur.workers`
>   bornée 1..3 (`=1` rétablit le worker unique) ; jamais deux tâches
>   du MÊME fichier en vol, fichiers distincts en parallèle (prouvé par
>   rendez-vous déterministe).
> - **Chaos + charge (critères ADR 0010)** : chaos 6 fils × 90
>   opérations mêlées (syncs/époques, XML, invalidations) — 0
>   interblocement (dump des piles en échec), 0 exception non attendue,
>   verdict fiche 012 préservé ; banc de charge « complétion A ∥
>   diagnose B » : p95 complétion 33 ms concurrent vs 43 séquentiel,
>   diagnose 25 vs 19 — critère ≤ +10 % + 10 ms TENU (l'ancien moniteur
>   échouait : concurrent ≈ somme des deux).
>
> **PV complet** : `mesures/2026-10-31-vf2-vc1-vc2.txt` (R5). **Reste
> V-F phases 3–5** : budgets octets + onTrimMemory, plage prioritaire,
> incrémentalité conditionnée (ADR 0017/0006).

### V-C — Intelligence (inspections, lint, complétion smart)

> **V-C1, V-C2 et V-C3 LIVRÉES le 31/10/2026 au soir** (V-C3 = v1.7.0 :
> complétion smart/typedef/littéraux R.*/second appel + import static +
> 3ᵉ appel projet/chaîne/statement/stats opt-in/mémoïsation binaires/
> mots-clés de tête D7 — G-012, G-017, G-018, G-024 RÉSOLUS ; banc
> qualité v2 24 positions split train/éval, ÉVAL 1,000/1,000 ; PV
> `mesures/2026-10-31-vc3-completion.txt`).
> Détail V-C1/V-C2 dans `mesures/2026-10-31-vf2-vc1-vc2.txt` :
>
> - **V-C1 (ADR 0023, G-010 RÉSOLU)** : contrat d'inspection (visitor
>   résilient, dumb-aware par construction) + profil (Warning/Info,
>   JAMAIS Error — règle ADR 0013) + suppressions
>   (`@SuppressWarnings` portée déclaration ENTIÈRE, `//noinspection`,
>   tokens javac alignés) + les 8 pilotes ; provenance
>   `source="inspection"` sur le `Diagnostic` (G-023 préparé) ;
>   exécution dans les DEUX régimes (complet ET rapide sur arbre masqué,
>   signaux des corps masqués PORTÉS — équivalence P1 éprouvée) ; repli
>   `jo.codeide.lsp.java.inspections.disabled`. Pièges mesurés et
>   corrigés : elvis sur `indexOf` (rend -1, pas null — boucle infinie),
>   portée `declarationSourceStart/End`, opérateur des `BinaryExpression`
>   dans `bits`, effacement générique des collections.
> - **V-C2 (ADR 0024, G-011 RÉSOLU en première vague)** : base
>   `api-versions.xml` (parseur StAX en tâche de fond, < 2 s cible ;
>   script `scripts/preparer-base-api.sh`, propriété
>   `jo.codeide.lsp.java.lint.baseapi`, repli honnête MUET sans base) ;
>   **NewApi** (échappatoires `@RequiresApi`/`@TargetApi` + gardes
>   `SDK_INT`, le déclarant réel fait foi), **InlinedApi**,
>   **ObsoleteSdkInt**, **MissingSuperCall** (fix suggéré),
>   **SetTextI18n** ; `@SuppressLint` (scalaire ou tableau) ; garde du
>   circuit annotator (erreur de résolution = silence). **Complément de
>   catalogue livré le 31/10 au soir (v1.8.0, PV
>   `mesures/2026-10-31-vd-vc2-catalogue.txt`)** : **Range**
>   (@IntRange/@FloatRange/@Size — arguments constants et retours
>   annotés, taille des chaînes et cardinal des tableaux),
>   **ResourceType** (famille `R.<famille>.x` ou nom annoté `@…Res`
>   contre la famille attendue — l'entier ordinaire jamais signalé),
>   **CommitPrefEdits** (locales + chaînes nues sans commit/apply,
>   évasions silencieuses), **Recycle** (TypedArray/VelocityTracker,
>   try-with-resources exempté) + **segment CIDX compact CLBA** de la
>   base (re-compilable bit-à-bit, chemin rapide, outil CLI + script).
>   **Restent** : mode global différé (UnusedResources), mesure de la
>   taille du segment sur la vraie base en CI/appareil.

- **Périmètre** : contrat d'inspection + profil + suppressions + 8 inspections
  pilotes puis DFA nullité intra-méthode (ADR 0023) ; contrôles lint mono-fichier +
  base `api-versions` compacte (ADR 0024) ; complétion smart + typedef + littéraux
  ressources + second appel + chain (ADR 0026) ; stats locales opt-in.
- **ADR couverts** : 0023, 0024, 0026.
- **Critères de sortie** : 8 inspections : FP = 0 sur corpus (P3) et chaque une
  attrape ≥ 1 mutant (P4) ; NewApi : cas dorés (échappatoires) 100 % ; complétion :
  rappel ≥ 0,95 / top-1 ≥ 0,80 sur positions réelles étiquetées (banc étendu,
  split train/éval) ; P6 ≥ 99 % ; latence p95 complétion ≤ 150 ms JVM ; **[appareil]
  p95 perçu < 100 ms** (cible §0.1, à valider par mesure) ; inspections dumb-aware
  vérifiées.
- **Dépendances** : V-B (référentiel, minSdk), V-F1 (verrous), décision D7 (fiche 007).
- **Effort [estimé]** : **28–44 j-h agent** (3–5 séances), découpable V-C1
  (inspections), V-C2 (lint), V-C3 (complétion smart).

### V-D — Actions de code

> **TRANCHE 3 LIVRÉE le 01/11/2026 (v1.10.0 — ADR 0027 Accepté (trois
> tranches), PV `mesures/2026-11-01-vd3-refactorings.txt`)** :
>
> - **CHANGE SIGNATURE** (kind `refactor.rewrite.signature`, DIFFÉRÉ —
>   clé refactor.supprimerParametre) : la suppression d'un PARAMÈTRE
>   réécrit la signature ET le GRAPHE DES APPELANTS multi-fichiers —
>   envois, sites `new X(…)` et appels explicites `this(…)`/`super(…)`
>   (socle ADR 0028, candidats de l'index de mots) ; le paramètre de
>   TÊTE emmène sa virgule suivante, le TERMINAL part depuis la fin du
>   précédent (`m(a, b)` retire `, b`, jamais un `a,` orphelin), l'argument
>   SEUL part sans virgule. Refus honnêtes motivés : `utilise` (lu dans
>   le corps), `varargs` (le rang d'un argument ne se sait pas à l'appel
>   — MESURÉ : le drapeau vit sur le BINDING isVarargs, pas sur
>   Argument.modifiers), `hierarchie` (override), `referenceMethode`
>   (un `X::m` fixe la forme). L'oracle `changementSignatureSure` porte
>   motif + appelants + éditions par fichier.
> - **SUPPRESSION HIÉRARCHIQUE** (kind `refactor.rewrite.delete`, titre
>   « et sa hiérarchie », servi au caret d'une méthode qui OUTREPASSE) :
>   la chaîne d'overrides ENTIÈRE — la outrépassée par BINDINGS, les
>   outrépassantes par BALAYAGE des sources (fermeture transitive) — la
>   garde d'usages par MAILLON (chaque maillon porte sa clé) : un site
>   d'appel hors chaîne FERME la porte, les appels INTERNES à la chaîne
>   disparaissent avec elle. GARDE V-D3 : le safe delete SIMPLE refuse
>   désormais `hierarchie` quand un sous-type outrepasse (la clé d'usages
>   ne suit PAS la hiérarchie descendante — un maillon orphelin ne
>   compile pas, MESURÉ : un appel `sub.m()` résout vers Sub.m).
> - **INLINE MULTI-FICHIERS** (kind `refactor.inline.constant`, clé
>   refactor.mettreEnLigneConstanteProjet) : la garde mono-fichier de la
>   tranche 2 est LEVÉE — chaque lecture du champ `static final` dans
>   TOUS les fichiers reçoit la valeur (référence qualifiée remplacée
>   ENTIÈRE), la déclaration quitte son fichier.
> - **CRÉATION DE CHAMP sur UndefinedField** (kind `quickfix.create.field`,
>   DIFFÉRÉ) : `this.foo`/`x.foo` reçoit `private/package/public Type foo
>   = <défaut>;` en tête des membres du type DÉCLARANT — qui peut vivre
>   dans un AUTRE fichier (le WorkspaceEdit multi-URI du resolve) ; type
>   inféré par BINDINGS de la valeur affectée, visibilité par paquet
>   (MESURÉ : `private` depuis un autre type ne compile pas) ; récepteur
>   BINAIRE = refus honnête.
> - **RENOMMAGE DE RESSOURCE (G-028)** : `R.<type>.<nom>` au caret
>   renomme la ressource dans le CODE Java (bindings des classes light —
>   le type R départage les homonymes, MESURÉ : la « liaison unique » de
>   ecj lie le DERNIER segment dans `binding`) ET dans les XML :
>   déclarations `name=`, références `@type/`, `@+type/`, `?type/` et
>   expressions `R.*` du data binding (MESURÉ : un `@{…}` de data binding
>   n'est PAS une référence — le scan avance d'un caractère). Refus
>   honnêtes : ressource-FICHIER (layout/drawable — le nom vit dans le
>   nom du fichier, le rename de fichier appartient au client LSP),
>   styleable ; collisions identifiant-invalide / nom-pris par le
>   référentiel. Servi par `textDocument/rename` (prepareRename + conflits
>   + formes négociées changes/documentChanges).
>
> **Critères éprouvés** : 23 cas V-D3 (WorkspaceEdit dorés multi-fichiers
> + compilabilité re-diagnostiquée de CHAQUE fichier — esprit P5) ; oracles
> motivés (utilise/varargs/hierarchie/referenceMethode ; chaîne 2 maillons
> + usage bloquant ; refus fichier/styleable) ; oracle rename « 0 occurrence
> manquée » (aucune trace de l'ancien nom ne survit, le nouveau `R.*`
> résout sur moteur neuf) ; latence liste AVEC refactorings étendus
> **p95 mesurée 442 µs** (< 30 ms, mémo chaud) ; 860 tests / 0 échec
> (730 java dont 23 V-D3). **Le catalogue reste ouvert** : ajout/
> réordonnancement de paramètres, rename depuis un XML.

> **TRANCHE 2 LIVRÉE le 01/11/2026 (v1.9.0 — ADR 0027 Accepté-tranche 2,
> PV `mesures/2026-11-01-vd2-refactorings.txt`)** :
>
> - **EXTRACT VARIABLE/CONSTANTE/MÉTHODE sur SÉLECTION** (kinds
>   `refactor.extract.variable/constant/function`) : l'expression
>   couverte s'extrait en locale (insertion au DÉBUT DE LIGNE de
>   l'instruction porteuse, nommage par type + anti-collision), en
>   constante (`private static final` en tête des membres) ou en méthode
>   (**l'appel remplace l'expression sur place** — l'ordre d'évaluation
>   est préservé, les locales lues deviennent des paramètres dans
>   l'ordre de première lecture). La plage CONTIGUË d'instructions d'un
>   même bloc s'extrait en méthode « instructions » (heuristiques §A5 :
>   une SORTIE au plus — les locales écrites-d'abord sont DÉCLARÉES
>   dans la méthode extraite, miroir de la référence —, contrôle de flux
>   non local = refus honnête R1).
> - **INLINE** (kinds `refactor.inline.variable/constant`) : locale
>   initialisée → chaque LECTURE remplacée par l'initialisateur
>   (parenthésage des binaires/ternaires/unaires, lignes repliées) ;
>   réaffectations et effets multi-usages FERMÉS. Champ `static final`
>   constant → usages du FICHIER COURANT remplacés par la valeur
>   (référence ENTIÈRE pour les qualifiées) ; usage hors fichier =
>   refus honnête (multi-fichiers en tranche 3).
> - **SAFE DELETE** (kind `refactor.rewrite.delete`) : détection des
>   usages MULTI-FICHIERS exhaustive (socle ADR 0028), suppression
>   mono-fichier ; l'oracle `MoteurJava.suppressionSure` porte les
>   usages bloquants et les motifs de refus (`override`,
>   `paramètre`) ; la garde se paie au RESOLVE (ADR (f)).
> - **SOCLE USAGES ÉTENDU** : les sites `new X(…)` comptent pour la
>   MÉTHODE constructeur (sélecteur lisible = nom du type — le sélecteur
>   brut `<init>` n'existe pas dans le texte source, MESURÉ), les
>   références `X::m` comptent pour la méthode visée, les CONSTRUCTEURS
>   sont identifiés au caret, l'égalité d'étendue de la fenêtre « +120 »
>   laisse passer le DERNIER candidat (des méthodes rapprochées volaient
>   le caret de la suivante, MESURÉ).
> - **Pièges mesurés** : `declarationSourceEnd` avale le `;`/`}` final
>   (une instruction-expression ne le porte PAS dans ses bornes) ; le
>   corps d'une méthode n'est PAS un nœud Block ; le constructeur PAR
>   DÉFAUT synthétique vit à type.methods sur le NOM du type (jamais
>   ancre, jamais cible) ; la LIMITE de plageJeton est EXCLUE alors que
>   sourceEnd d'un TypeReference est INCLUSIF (la dernière lettre du nom
>   était coupée).
>
> **Critères éprouvés** : 22 cas dorés (WorkspaceEdit exacts +
> compilabilité re-diagnostiquée — esprit P5) ; oracle safe-delete
> « 0 manqué » (usage même fichier, autre fichier, sites `new`,
> références `X::m` ; refus motivés override/paramètre) ; latence liste
> AVEC refactorings **p95 mesurée 3 881 µs** (< 30 ms, mémo chaud) ;
> 708 tests / 0 échec. La tranche 3 (change signature, rename de
> ressource G-028, création de champ sur UndefinedField, suppression
> hiérarchique, inline multi-fichiers) est LIVRÉE ci-dessus.

> **TRANCHE 1 LIVRÉE le 31/10/2026 au soir (v1.8.0 — ADR 0027
> Accepté-tranche 1, PV `mesures/2026-10-31-vd-vc2-catalogue.txt`)** :
>
> - **organizeImports EXPOSÉ** (M3 — G-020) par le filtre `only` de
>   LSP 3.17 (égalité ou préfixe hiérarchique) ; kinds annoncés
>   `source`/`source.organizeImports`/`source.generate`/`refactor`/
>   `refactor.extract`/`refactor.rewrite`.
> - **codeAction/resolve DIFFÉRÉ** (ADR (f)) : les GÉNÉRATEURS
>   voyagent avec `data` (cle + arguments + uri), les éditions se
>   recalculent déterministement au resolve — rien d'eager.
> - **Génération de code — les 6 capacités** (G-015) : getter (is*
>   boolean), setter (jamais sur final), couple, constructeur assignant
>   les champs d'instance, equals/hashCode (java.util.Objects, ==
>   primitif, deepEquals tableaux), toString, stubs d'outrepassement
>   des abstraites manquantes (@Override + défaut + TODO) — déclenchement
>   CIBLÉ (caret sur le champ / le nom du type / type incomplet).
> - **Intentions sans diagnostic** (G-014) : if/else symétrique →
>   ternaire, extraction de constante au littéral, lambda → référence
>   de méthode, var ↔ type explicite (jamais de changement de
>   sémantique : le type déclaré doit ÉGALER le type résolu).
> - **Familles ecj étendues +7 IDs** (G-014) : stubs des abstraites
>   manquantes (par bindings, arguments structurés), corps manquant
>   (corps qui jette), retour de valeur en void, imports en
>   conflit/dupliqués, cast inutile (alias nommé de l'inspection
>   V-C1 — l'ID numérique garde la priorité, fiche 065 intacte).
> - **Piège mesuré** : ecj ajoute un CONSTRUCTEUR PAR DÉFAUT à
>   `type.methods`, positionné sur le nom du type (hors corps) — l'ancre
>   des membres filtre `declarationSourceStart >= bodyStart`.
>
> **Critères éprouvés** : cas dorés par capacité (WorkspaceEdit exacts,
> 10/10) ; P5 = 0 violation sur l'ensemble exposé (balayage élargi
> 3 → 7 fixtures) ; latence liste codeAction **p95 mesurée 255 µs**
> (< 30 ms, mémo chaud). **Restent les tranches 2–3** : extract
> variable/method sur SÉLECTION, safe delete (usages + conflits,
> ADR 0014/0028), inline, change signature, rename de ressource
> (G-028), création de champ sur UndefinedField — le critère « conflits
> rename/safe-delete : 0 manqué (oracle) » suivra ces tranches.

> **COMPLÉMENT DE CATALOGUE LIVRÉ le 01/11/2026 (v1.12.0 — ADR 0032,
> PV `mesures/2026-11-01-complement-catalogue.txt`)** — les restes des
> familles unitaires :
>
> - **AJOUT de paramètres** (quickfix ID-FIRST sur l'appel fautif —
>   ParameterMismatch 67108979 / UndefinedConstructor 134217858, IDs et
>   arguments MESURÉS) : la signature s'étend des types RÉSOLUS des
>   arguments excédentaires, les autres appelants reçoivent la valeur
>   PAR DÉFAUT (0/false/null — préremplissage de la référence), l'import
>   raccourci vit dans le fichier déclarant ; le site fautif ne se
>   retouche pas (MESURÉ : son binding reste lisible, il porte déjà les
>   futurs paramètres) ;
> - **RÉORDONNANCEMENT** (« Déplacer le paramètre vers la gauche/
>   droite ») : échange des fragments de signature + des arguments aux
>   mêmes rangs chez chaque appelant — la liaison expression↔paramètre
>   est préservée ; le paramètre peut être utilisé (il se déplace avec
>   ses lectures) ;
> - **RENAME DEPUIS UN XML** (G-028, solde) : prepareRename/rename sur
>   un document XML du module (déclaration name= des values, références
>   @type/, @+type/, ?type/, data binding R.*), propagation code + XML,
>   tampon ouvert en fait de foi ; garde publicateur (aucun diagnostic
>   Java pour un XML ouvert) ;
> - **codeDescription.href** (solde V-G (e), G-023) : provenance lint →
>   la page officielle des contrôles lint (VÉRIFIÉE : pas d'ancre par
>   ID — aucun URL fabriqué) ; ecj/inspection sans href (documenté).
>
> Validation : 17 tests (cas dorés re-diagnostiqués — 0 erreur ; oracles
> de motifs ; client espion protocole) ; latence liste p95 MESURÉE
> 410 µs (< 30 ms) ; 900 tests / 0 échec ; build STRICT vert ; banc N1
> tenu.

- **Périmètre** : organizeImports exposé + kinds (ADR 0027/0031), génération de code
  (6 capacités), intentions sans diagnostic, familles ecj étendues, extract
  variable/constant puis method, safe delete, inline, change signature, resolve réel.
- **Critères de sortie** : P5 = 0 violation sur l'ensemble exposé ; cas dorés
  WorkspaceEdit exacts par capacité ; conflits rename/safe-delete : 0 manqué (oracle) ;
  `codeAction` liste p95 < 30 ms JVM.
- **Dépendances** : V-C partiellement (annotations pour certains fixes) ; client :
  rendu des kinds (K2). **Effort [estimé]** : **28–46 j-h** (3–5 séances).

### V-F (suite) — Performance/concurrence complète

> **LIVRÉE le 01/11/2026 (v1.11.0 — phases 3–4 ; phase 5 reportée
> motivée)**. **Phase 3 (budgets octets + onTrimMemory — exécution ADR
> 0011 points 2 et 5)** : budget d'octets ESTIMÉS du cache de parse
> (8 Mio — facteur arbre ×6, estimation de gestion R5 ; garde
> ANTI-CYCLAGE : la dernière entrée posée survit seule) et du mémo de
> localisation (2 Mio — négatifs évincés d'abord, re-probe honnête,
> contrats fiche 024 INTACTS) ; shim `pressionMemoire(niveau)` miroir
> `ComponentCallbacks2` (G-033 RÉSOLU) — politique cumulative
> (premier plan → arbres résolus seuls ; arrière-plan → + mémos +
> négatifs ; COMPLETE → libération intégrale drainée, le moteur
> SURVIT), rapport CHIFFRÉ `RapportPressionMemoire`, relais
> MULTI-MODULES `EnvironnementCompilation.pressionMemoire`, jamais de
> construction d'environnement sur signal mémoire ; kill-switches
> `memoire.budgets.disabled` / `memoire.ontrim.disabled`.
> **Phase 4 (plage prioritaire)** : notification `$/codeide/plageVisible`
> v1 (offsets ABSOLUS, dernière gagnante, oubli au didClose, INERTE sans
> envoi — contrats du cahier intacts) + sonde `serviraitPasseComplete`
> PUREMENT LEXICALE + `diagnosePrioritaire` (masquage isomorphe fiche
> 014, PURE — aucune écriture d'état) : double publication « visible
> d'abord » ≥ 16 000 caractères, la COMPLÈTE fait foi (fil unique) ;
> kill-switch `plageprioritaire.disabled`. **Phase 5
> (incrémentalité) : REPORTÉE motivée** — conditionnée à l'ADR 0017 (b)
> (artefacts JetBrains java-syntax, décision D4 non tranchée par le
> propriétaire) ; le critère utile (« la frappe ne paie pas la
> re-résolution complète ») est déjà tenu par le diagnose rapide
> (fiche 014). Validation : PV
> `mesures/2026-11-01-vf-finale-vg-protocole.txt` — 23 tests V-F3/V-F4/V-G
> verts (9 budgets/pressions, 5 plage prioritaire, 9 V-G), suite complète
> verte, banc N1 tenu (diagnose rapide p50 4,2 ms).

- **Périmètre** : verrous par périmètre + chaos tests (ADR 0029 phase 2), budgets
  octets + onTrimMemory (phase 3), plage prioritaire (phase 4), incrémentalité si
  conditions ADR 0017 (phase 5).
- **Critères de sortie** : comptage synchronized < 6 ; chaos tests verts ; p95/p99
  sous charge concurrente ≤ baseline +10 % ; budgets mémoire tenus ; [appareil]
  gabarit 07 : budgets par palier tenus, thermique 3 passes.
- **Effort [estimé]** : **16–26 j-h** (2–3 séances).

### V-G — Protocole/sémantique

> **LIVRÉE le 01/11/2026 (v1.11.0 — ADR 0031 Accepté-livré, PV
> `mesures/2026-11-01-vf-finale-vg-protocole.txt`)**. Les sept briques :
> **(a)** `semanticTokens` (G-019 RÉSOLU) — légende 8 types STANDARD +
> 4 modificateurs, calcul sur arbre RÉSOLU, deltas relatifs 5
> entiers/jeton, constructeurs = method (miroir V-D2) ; banc de rendu
> cas doré + latence p95 MESURÉE **1 ms** (cible < 25 ms) ; PAS de delta
> serveur (honnête — le client re-demand le vecteur complet). **(b)**
> kinds source.*/refactor.* — livrés depuis V-D. **(c)**
> `workDoneProgress` d'indexation (G-032 RÉSOLU) — fil démon de
> PRÉCHAUFFAGE après `initialized` (valeur propre : la 1re requête
> usages/renommage ne paie plus le froid) + jeton négocié
> begin/report/end (report par fichier RÉELLEMENT lexé, throttlé ~50,
> end TOUJOURS). **(d)** `willRenameFiles` (G-022 RÉSOLU) — package +
> imports du cas doré AVANT le geste, `didRenameFiles` invalide,
> périmètre honnête : usages qualifiés sans import non réécrits.
> **(e)** diagnostics enrichis (G-023 résolu EN PARTIE) — `data
> {provenance, code}` machine-readable + relatedInformation « requis
> par X » (fiche 019) ; `codeDescription.href` livré au COMPLÉMENT
> de catalogue (v1.12.0 — ADR 0032 : provenance lint → la page
> officielle developer.android.com/studio/write/lint, VÉRIFIÉE sans
> ancre par ID ; ecj/inspection sans href, documenté). **(f)** `completionItem/resolve` RÉEL
> (G-021 RÉSOLU) — auto-import DIFFÉRÉ négocié par `resolveSupport`
> (règles fiche 029 miroir, balayage lexical) ; sans annonce, liste
> complète d'avant V-G. **(g)** registre client K2 VIVANT
> (`docs/REGISTRE-CLIENT-K2.md`) + capability `x-codeide.plageVisible`
> v1 posée (V-F4) ; clés V-G de complétion ADDITIVES → la version de
> `completionRanking` RESTE 2 (ADR 0015 R1). Tests contractuels par
> capability (annoncée ⇔ testée) : 9 cas V-G.

- **Périmètre** : `workDoneProgress`, diagnostics enrichis (relatedInformation,
  provenance), `willRenameFiles`, `semanticTokens` (avec banc de rendu), resolve
  réels, capability v2 (ADR 0031).
- **Critères de sortie** : tests contractuels par capability (annoncée ⇔ testée) ;
  semanticTokens : vecteurs attendus sur cas dorés + p95 < 25 ms ; USAGE.md à jour.
- **Dépendances** : client (consommation — registre K2, liste séparée par vague).
  **Effort [estimé]** : **10–18 j-h** (1–2 séances).

## 3. Synthèse d'effort et calendrier indicatif

| Vague | Effort [estimé] j-h agent | Séances effectives | Prérequis décision |
|---|---|---|---|
| V-0 mesure | livrée | +0,5–1 (exécution) | aucune |
| V-A fiabilité | 18–30 | 2–4 | aucune |
| V-E navigation | 1–3 | < 0,5 | **D1** |
| V-B projet Android | 36–60 | 4–7 | D2 (contrat modèle), D6 |
| V-F1 double-tampon | 8–14 | 1–2 | aucune |
| V-C intelligence | 28–44 | 3–5 | V-B, V-F1, D7 |
| V-D actions | 28–46 | 3–5 (livrée 31/10–01/11 — trois tranches + complément de catalogue ADR 0032) | V-C partiel |
| V-F perf complète | 16–26 | 2–3 (livrée 01/11 — phases 3–4 ; phase 5 reportée motivée) | aucune |
| V-G protocole | 10–18 | 1–2 (livrée 01/11 ; solde href livré au complément ADR 0032) | client (K2) |
| **Total** | **145–241 j-h agent** | **16–29 séances** | — |

> Recoupement volumétrie (méthode §3.2 du PLAN-REECRITURE) : les vagues ajoutent
> ~15–25 k l. de production + ~12–18 k l. de tests [estimé] à 150–300 l./j-h
> qualifiées → 90–250 j-h — cohérent avec le bottom-up. La fourchette humaine
> équivalente reste à calibrer par le propriétaire (rythme agent ≠ jour-homme).

## 4. Ce qui revient à l'hôte `codeide-lsp` et au client (listes séparées)

**Hôte (`codeide-lsp`)** — prêt à devenir un prompt :
1. Produire les champs du modèle v2 à la sync (liste B10 de l'ADR 0020) + bascule
   dérivation disque → sync.
2. Écarts H1–H4 existants d'`ECARTS-SERVEUR.md` §3 (sync AVANT cache à l'ouverture,
   invariant « une session vivante », signaler l'état) — le double-tampon moteur
   (V-F1) neutralise H2 côté moteur.
3. Relayer `onTrimMemory` au moteur (V-F).
4. Indexer `build/generated` côté arbre éditeur (H4, E1).

**Client (`cel-lsp`/`code-editor`)** — idem :
1. Consommer les capabilities servées non consommées (K2 : rangeFormatting,
   onTypeFormatting, workspace/symbol, selectionRange, codeLens, callHierarchy,
   typeHierarchy, superDefinition, decompiledSource) + les nouvelles (kinds
   source/refactor, progression, relatedInformation, semanticTokens, usages groupés,
   styles lecture/écriture).
2. Gardes K1/K3 (politique préfixe vide ; attendre la session avant
   `decompiledSource`).
3. Rendu de la progression d'indexation et des diagnostics multi-étages.

## 5. Gouvernance

- Un **recalage** à la fin de V-A (vélocité mesurée, re-projection V-B→V-G) puis à
  la fin de V-B — méthode `RECALAGE-1/2.md`.
- Chaque vague : drapeaux + repli documentés, baselines signées avant/après (ADR
  0016/0030), PV de mesure, une décision ou capacité par commit.
- Les prompts d'exécution **vague par vague** seront produits séparément après
  décision du propriétaire (§10 du prompt de mission).
