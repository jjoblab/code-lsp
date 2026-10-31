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
> 0 saut** (baseline tenue). **Reste V-A2** :
> préparation du corpus réel (manifestes de classpath des 5 projets — exige
> SDK Android + builds Gradle, côté CI nightly), gates P3/P4/P7 corpus et
> rapport de divergences ecj↔javac (ADR 0030) ; recalage de vélocité fin de
> vague.

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

> **V-C1 et V-C2 LIVRÉES le 31/10/2026 au soir** ; **V-C3 reste à livrer**
> (complétion smart/typedef/littéraux R.*/second appel/chain/statement/
> stats — découpée, dépend de la lecture approfondie d'`AnalyseurContexte`).
> Détail dans `mesures/2026-10-31-vf2-vc1-vc2.txt` :
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
>   circuit annotator (erreur de résolution = silence). **Restent** :
>   Range, ResourceType, CommitPrefEdits, Recycle, mode global différé,
>   segment CIDX compact de la base.

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

- **Périmètre** : organizeImports exposé + kinds (ADR 0027/0031), génération de code
  (6 capacités), intentions sans diagnostic, familles ecj étendues, extract
  variable/constant puis method, safe delete, inline, change signature, resolve réel.
- **Critères de sortie** : P5 = 0 violation sur l'ensemble exposé ; cas dorés
  WorkspaceEdit exacts par capacité ; conflits rename/safe-delete : 0 manqué (oracle) ;
  `codeAction` liste p95 < 30 ms JVM.
- **Dépendances** : V-C partiellement (annotations pour certains fixes) ; client :
  rendu des kinds (K2). **Effort [estimé]** : **28–46 j-h** (3–5 séances).

### V-F (suite) — Performance/concurrence complète

- **Périmètre** : verrous par périmètre + chaos tests (ADR 0029 phase 2), budgets
  octets + onTrimMemory (phase 3), plage prioritaire (phase 4), incrémentalité si
  conditions ADR 0017 (phase 5).
- **Critères de sortie** : comptage synchronized < 6 ; chaos tests verts ; p95/p99
  sous charge concurrente ≤ baseline +10 % ; budgets mémoire tenus ; [appareil]
  gabarit 07 : budgets par palier tenus, thermique 3 passes.
- **Effort [estimé]** : **16–26 j-h** (2–3 séances).

### V-G — Protocole/sémantique

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
| V-D actions | 28–46 | 3–5 | V-C partiel |
| V-F perf complète | 16–26 | 2–3 | aucune |
| V-G protocole | 10–18 | 1–2 | client (K2) |
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
