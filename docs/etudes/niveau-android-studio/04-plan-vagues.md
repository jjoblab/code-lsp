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

- **Périmètre** : époque copy-on-write de l'environnement de noms + construction en
  fond pendant que l'ancien sert (ADR 0029 phase 1) ; annulation fine de base.
- **Critères de sortie** : changement de classpath pendant rafale → 0 réponse périmée
  et 0 figage (latence pendant reconstruction ≤ 2× nominale) ; [appareil] gabarit 07
  rempli par le propriétaire — cible indicative : reconstruction non perceptible
  (< 300 ms de dégradation p95).
- **Effort [estimé]** : **8–14 j-h**. **Côté hôte** : signaler le moment de la sync
  (déjà le cas).

### V-C — Intelligence (inspections, lint, complétion smart)

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
