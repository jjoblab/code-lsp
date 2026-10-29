# code-lsp

**Dépôt officiel des serveurs de langage (LSP) de CodeIDE.**

> Vision du propriétaire (28/10/2026) : un LSP Java « frais, plus solide, plus
> intelligent, plus robuste, plus propre et professionnel », grâce à
> l'architecture IntelliJ PSI — étudiée en profondeur, embarquée nulle part,
> greffée partout où elle prouve sa valeur.

## État — N1–N8 + T INTÉGRALEMENT LIVRÉS — BASCULE §8.5 SIGNÉE le 30/10/2026 (checklist 6/6, version 1.0.0)

Décisions actées par le propriétaire le 28/10/2026 :

| Décision | Référence |
|---|---|
| GO PARTIEL — stratégie hybride « s'inspirer » (ecj + CIDX + java-syntax + designs D8–D12) | ADR 0004 (Accepté) |
| Réécriture **intégrale** du moteur Java en Kotlin — exclusion du vendored définitive | ADR 0001 (Accepté, option 2) |
| Périmètre « domaine moteur Java » + vision « dépôt officiel des LSP » | ADR 0002 (Accepté) |
| Licence du dépôt : Apache-2.0 (`LICENSE`, `NOTICE`) | ADR 0001 |
| Convention de nommage : préfixes `Ecj*`/`Jdt*` réservés aux classes dérivant réellement d'ECJ/JDT | ADR 0002 (point 5) |

Décisions sur le programme de réécriture, actées par le propriétaire le
28/10/2026 en séance (`docs/PLAN-REECRITURE.md` §10) :

- **Fourchette 160–270 j-h (médiane 213) approuvée** comme enveloppe de
  planification — engagement contractuel **borné à N1 + banc de parité
  croisé**, décision de suite re-soumise au recalage de fin N1 ;
- **mesure sur appareil = porte d'entrée bloquante de N1** (`spike/appareil/`)
  : aucune implémentation du noyau avant retour du gabarit rempli ;
- **séquence confirmée** : N1 → (N2, N3, N7) → (N4, N5, N6) → N8 ;
- **écart moteur de la fiche 054 reproduit en parité stricte** — correction
  consignée comme amélioration candidate post-bascule.

Le moteur est réécrit de zéro : **aucun code** du moteur vendored de
`codeide-lsp` n'est repris (ni production, ni tests). La base contractuelle
de la réécriture est le **cahier de spécifications** extrait des 449 tests
de l'ancien moteur (`docs/cahier-specifications/` — **livré en vague 1a** :
65 fiches, 449 comportements).

**Vague 1b (28/10/2026) — premières reprises contrôlées effectuées** :
`:model` (11 fichiers) et `:index` (33 fichiers) repris de codeide-lsp @
cb809e14, inscrites fichier par fichier au manifeste (§7) ; les 4 types du
protocole tooling consommés par le modèle sont **réécrits en code neuf**
(`jo.codeide.lsp.model.protocole` — alternative consignée §4 du manifeste,
faute d'action P1). **122 tests repris verts** (38 modèle + 84 index).
`:testing` est reportée à l'étage noyau du moteur (elle éprouve le moteur
à réécrire).

**Étage N1 (29/10/2026) — noyau compilateur & environnement LIVRÉ** :
le module `:java` (moteur) est écrit **depuis le cahier de spécifications
uniquement** (clean-room intégral, 21 fichiers Kotlin neufs, KDoc français,
`explicitApi`, bytecode 17) : pilotage d'ecj 3.40.0 **patché** via `:ecj-art`
(bridge `Compiler.resolve` — arbres vivants, jamais nettoyés), routage de
classpath **sources → jars → jrt → stubs intrinsèques** (fiche 042) avec
FQCN **longest-match** (fiche 010), jrt runtime **ou externe** via
`jrt-fs.jar` + URLClassLoader/ServiceLoader (fiche 031), lecteur ZIP brut
propre (fiche 038 — parité octet à octet avec `ZipFile`), caches
présence/localisation/parse (fiches 006/024/025), **diagnose rapide par
corps de méthode** avec équivalence stricte contre témoin neuf (fiche 014),
chaîne de features à 3 niveaux — arbre focalisé / arbre réel / réduction
`SourceFocuser` (fiches 015/041), **repli syntaxe instantané** sans
plateforme (fiche 028), moteur **par module** avec routeur fichier→module
relu à chaque requête (fiches 034/047), ordonnanceur à worker unique avec
supersession par fichier + jeton d'annulation coopératif (fiches 048/046),
enrichissement de hiérarchie « absent du classpath : X (requis par Y) »
(fiche 019). **128 tests** (25 fiches N1 couvertes — les 6 dépendant de
l'environnement SDK/model.json s'ignorent proprement par hypothèses
visibles). Suite d'intégration `:testing` **réécrite en Kotlin neuf**
(mutation consignée au manifeste §3 : l'ancienne suite était couplée à
l'API vendored) : adaptateur `IndexNoms` sur le CIDX réel + 4 tests
(parité avec/sans index, autorité à PRÊT, filtre `$`, réouverture chaude).
**Banc empreinte/latence en CI** (`:java:bench`, corpus sans android.jar) :
diagnose complet p50 11,7 ms / p95 39,8 ms, **diagnose rapide p50 1,5 ms**
(frappe confinée — ~8× plus vite que le complet), hover p50 0,12 ms,
complétion de membres p50 0,04 ms, tas au repos 16 Mo (JDK 21 de bureau,
corpus 20 classes — nombres à comparer entre versions, aucun seuil
asserté avant le banc croisé ADR 0016).

**Étage N2 (29/10/2026) — complétion & classement LIVRÉ** : le pipeline
complet de la capacité phare — analyse de contexte (sonde à chaud sur
l'arbre du cache, puis classification par **source marquée** avec
l'**échelle de variantes du splice** : ecj abandonne les membres
incomplets à l'EOF, six variantes textuellement gérées en cascade
remplacement / `()` pour `new` / ` x;` après modificateurs / ` x` en tête
de liste / `() {}` en corps de classe / queue équilibrée), collecte par
genre (membres avec **balayage de hiérarchie complet jusqu'à Object**,
types visibles + traversée du classpath par préfixe avec auto-import,
règles d'or du filtre sous-paquetage « ni plus ni moins », stubs
d'override de toute la hiérarchie, constantes d'enum, mots-clés),
filtrage gradué (préfixe domine, bonus −200, sous-chaîne ≥ 3), chaîne de
peseurs D8 (correspondance → nature par contexte → liftShorter →
alphabetique), **gel des têtes + plafond honnête 120 items** (total réel
communiqué), frontière RPC sérialisable (re-filtrage local client),
annuaire du classpath ne fuit **que la surface publique du JDK**.
**36 tests neufs** (fiches 020 complétion / 036 popup spontané R$* / 043
diagnostic / 044 règles d'or sur android.jar réel / 050 kinds LSP / 053
composition didChange→complétion) — build complet **344 tests, 0 échec,
1 ignoré** (gate appareil). **Banc qualité en CI bloquante**
(`:java:benchQualite`, ADR 0016) : corpus versionné 18 positions, baseline
JSON signée `bancs/baselines/completion.json` — **recall 1,000, top-1
1,000, MRR 1,000** (chaîne naïve : 0,611 / 0,715 — **+38,9 pts top-1,
+28,5 pts MRR**, ADR 0012 validée), zéro saut de liste, p95 31 ms.
**Gates requalifiées en CI** (décision propriétaire) : android.jar API 34
téléchargé en zip officiel + AAR appcompat — Fiche033 ×3 et Fiche004 se
jouent ; les 2 gates `model.json` (Fiche010/Fiche039) restent dormantes
(artefact appareil). Trois correctifs découverts par les tests/banc :
crash du gel sur liste rétrécie (avalé en liste vide silencieuse),
hiérarchie non balayée par `availableMethods` d'ecj, internes
`com.sun.*`/`jdk.*` du jrt proposés en complétion.

## Structure du dépôt

| Chemin | Rôle |
|---|---|
| `docs/etudes/moteur-java-psi/` | étude d'architecture IntelliJ PSI (D1–D12), synthèse, mesures du spike, alertes, annexes |
| `docs/adr/` | ADR 0001–0018 (décisions d'architecture, statuts à jour) |
| `docs/cahier-specifications/` | cahier des comportements attendus (extraction clean-room des 449 tests) — le contrat de la réécriture |
| `docs/PLAN-REECRITURE.md` | **programme de réécriture chiffré** (étages N1–N8+T, fourchette 160–270 j-h) — **approuvé le 28/10/2026** (engagement borné N1 + banc croisé) ; recalages 1 et 2 exécutés le 29/10 |
| `docs/RECALAGE-1.md` | **recalage de fin N1 (§8.2)** — vélocité mesurée, re-projection N2–N8, décision de suite soumise au propriétaire |
| `docs/RECALAGE-2.md` | **recalage de fin N2 (§8.2 — dernier recalage prévu)** — confiance N3–N8 re-évaluée sur les deux étages extrêmes de l'abaque, re-projection 5–8 séances effectives, décision de suite soumise |
| `bancs/baselines/` | **baselines JSON signées des bancs qualité** (ADR 0016 — `completion.json` : complétion N2 ; `parite-completion.json` : parité croisée T ; re-baseline par PR dédiée) |
| `bancs/GABARIT-BASCULE-APPAREIL.md` | **gabarit de passe bascule sur appareil REMPLI ET SIGNÉ le 30/10/2026** (étage T §8.5 — passe moto g06 : 18/18, 0 saut ; R5 : valeurs réelles, jamais simulées) ; [`bancs/resultats-bascule.txt`](bancs/resultats-bascule.txt) : transcript brut ; [`bancs/ANALYSE-DELTAS-BASCULE.md`](bancs/ANALYSE-DELTAS-BASCULE.md) : arbitrage des 165 deltas (103 bruit / 62 assumées / 0 à rattraper) |
| `docs/BASCULE-CODEIDE.md` | **procédure de mise en œuvre de la bascule côté client CodeIDE** (substitution composite ADR 0015 — DEV composite / production GitHub Packages : publication **EFFECTUÉE le 30/10 au soir** — tag `v1.0.0`, workflow vert (run `40942576172`), `jo.codeide.lsp.java:jo-codeide-lsp-java:1.0.0` + compagnon `jo-codeide-lsp-java-ecj-art` vérifiés sur `maven.pkg.github.com/jjoblab/code-lsp` ; rails consommateurs poussés dans `CodeIDE` (`8450bb7`) ; chaînon hôte mesuré : 3–6 j-h, décision au propriétaire ; retour arrière 1 ligne) |
| `docs/INTEGRATION-CODEIDE.md` | **audit des logs appareil et plan du branchement complet du moteur neuf dans CodeIDE** (objectif propriétaire du 30/10 : « intégration complète, tout connecté, cohérent » — 4 écarts E1–E4 relevés sur CodeIDE 0.202.0/moto g06 : go-to vers sources générées, source décompilée avant session prête, churn de 10 sessions en 1,4 s + pression mémoire ; chaînon restant `:lsp:host` détaillé, arbitrage A/B au propriétaire, plan I0–I6 et critères d'acceptation) — le moteur vendored de `codeide-lsp` est archivé le même jour (ADR 0233, commit `bc11253`) |
| `docs/PROVENANCE-REPRISE.md` | manifeste de reprise sélective (règle R9 — registre des copies autorisées) |
| `spike/` | bancs de mesure V1/V2/V3, scanner de compatibilité ART, gabarit appareil |
| `java/` | **moteur Java LSP réécrit — étages N1–N8 INTÉGRALEMENT LIVRÉS** (noyau compilateur & environnement, complétion & classement avec échelle de variantes, hover/inlay/signatures, navigation & usages, rename v2, actions de code ModCommand, confort éditeur, cycle de vie LSP & protocole riche lsp4j ; bancs `:java:bench` / `:java:benchQualite` / `:java:benchUsages` / `:java:benchParite`) |
| `USAGE.md` | **guide d'utilisation du serveur `lspjava`** — démarrage LSP, cycle de vie, chaque fonctionnalité avec exemples de requêtes/réponses, capability maison, extensions expérimentales, API in-process du moteur |
| `testing/` | suite d'intégration moteur ↔ index CIDX — **réécrite en Kotlin neuf à l'étage N1** (adaptateur `IndexNoms` + 4 tests) |
| `index/` | index disque CIDX — **repris contrôlé vague 1b** (17 f. main + 15 f. test + banc) |
| `model/` | modèle de projet LSP + types du protocole réécrits — **repris contrôlé vague 1b** (6 f. main + 5 f. test + code neuf) |
| `ecj-art/` | patcheur build-time d'ecj pour ART — **réécriture Kotlin clean-room vague 1c** (scanner, patcheur, shims, liste versionnée) |
| `scripts/` | vérifications obligatoires (`verifier-push.sh`, `verifier-provenance.sh`) |
| `.githooks/` | crochet `pre-push` d'identité jjoblab (activation : `git config core.hooksPath .githooks`) |
| `.github/workflows/` | CI (build + contrôles de provenance) et publication (`publish.yml` : tag `v*`, build de vérification puis `publish` vers GitHub Packages) |

## Utilisation

Voir **`USAGE.md`** — le guide complet : intégration LSP (lanceur lsp4j,
négociation, cycle de vie, gardes d'erreur), chaque fonctionnalité avec
exemples de requêtes/réponses JSON-RPC (complétion, survol, diagnostics,
navigation, renommage, actions de code, formatage, pliage, symboles,
lentilles, hiérarchies…), la capability maison
`x-codeide.completionRanking`, les extensions expérimentales
(`superDefinition`, `decompiledSource`) et l'API in-process du moteur
(`MoteurJava`).

## Règles de travail

Voir **`AGENTS.md`** — en résumé : Kotlin 100 %, français partout,
KDoc/Javadoc détaillé obligatoire, clean-room strict vis-à-vis de l'ancien
moteur, manifeste de provenance, et **vérification avant chaque push**
(l'utilisateur authentifié doit être `jjoblab`).

## Construction

Prérequis : JDK 21. Le wrapper Gradle 8.13 est inclus ; si la machine n'a
pas de JDK 21 complet, le plugin d'auto-provisionnement foojay le télécharge
automatiquement (paramétré dans `settings.gradle.kts`).

```bash
./gradlew build                    # build + tests (625 tests : 38 modèle + 84 index + 36 ecj-art + 463 noyau :java + 4 intégration)
./gradlew :ecj-art:patcherEcj      # produit ecj-art/build/ecj-patche/ecj-3.40.0-art.jar
./gradlew :java:bench              # banc empreinte/latence du noyau N1 (JVM séparée)
./gradlew :java:benchQualite       # banc qualité complétion N2 — ÉCHOUE sur régression vs baseline signée
./gradlew :java:benchUsages        # banc usages N4 — rappel = oracle, p95 < 100 ms, CI-bloquant
ANDROID_JAR=… ./gradlew :java:test # ouvre les gates Android (036/043/044/050/053 + 033/004)
```

La CI (`.github/workflows/ci.yml`) exécute à chaque push : le build, le
banc du noyau, le **banc qualité bloquant**, les **gates Android**
(android.jar API 34 en zip officiel + AAR appcompat — Fiche033/Fiche004
dormantes ne le sont plus) et le contrôle « anti-fichier-hors-registre ».

## Feuille de route (résumé)

- **Vague 0 (terminée)** : squelette, licences, CI, vérifications.
- **Vague 1 (terminée)** : ~~cahier de spécifications~~ ✓ (1a — 65 fiches,
  449 comportements) ; ~~reprise contrôlée `index`/`model`~~ ✓ (1b —
  45 fichiers inscrits au manifeste, 122 tests verts ; `testing` reportée
  à l'étage N1) ; ~~réécriture Kotlin du patcheur `ecj-art`~~ ✓ (1c —
  12 fichiers neufs, 36 tests, jar ART re-scanné vierge) ;
  ~~ré-estimation du programme de réécriture cahier en main~~ ✓ (1d —
  **`docs/PLAN-REECRITURE.md`** : 160–270 j-h, médiane 213, étages
  N1–N8+T, **approuvée le 28/10/2026 en séance** — §10 du plan : enveloppe
  approuvée, engagement borné N1 + banc croisé).
- **Mesure sur appareil (porte d'entrée de N1, décision §10.2) — RETOURNÉE
  le 28/10/2026** : passe 14:33 (heure appareil) sur moto g06 / Android 15 /
  OpenJDK 17.0.19 (aarch64) — `spike/appareil/GABARIT-RESULTATS.md` rempli +
  transcript brut (commit 3c7158d), valeurs consignées telles quelles (R5).
  Passe sur JVM (Termux) — ART non testé (vague « spike ART ») ;
  compléments §5 non réalisés (optionnels).
- **Étage N1 (TERMINÉ le 29/10/2026)** : ~~noyau compilateur & environnement~~ ✓
  (25 fiches couvertes, 128 tests noyau + 4 intégration, `:testing` réécrite,
  banc empreinte/latence en CI — détail en tête de page).
- **Recalage 1 (fin N1, §8.2) — SOUMIS le 29/10/2026** : ~~vélocité mesurée,
  re-projection N2–N8~~ ✓ (`docs/RECALAGE-1.md`) — N1 livré en 1 séance
  continue (~6 h agent) ; densités mesurées sous le postulat (34,3 l. prod /
  comportement) ; projection N2–N8+T : **6–9 séances effectives** en
  exécution agent, fourchette humaine 160–270 j-h maintenue (R5) ;
  **décision reçue : N2 lancé, gates Android requalifiées au fil de N2**.
- **Étage N2 (TERMINÉ le 29/10/2026)** : ~~complétion & classement~~ ✓
  (9 fiches : 007/008/020/035/036/043/044/050/053 couvertes, échelle de
  variantes, chaîne D8 + gel + plafond honnête, split RPC, banc qualité
  CI-bloquante avec baseline signée — **critères de sortie tenus** :
  recall ≥ baseline, top-1/MRR +5 pts vs naïf (+38,9/+28,5), p95 ≤
  baseline, zéro saut ; gates Android requalifiées en CI — détail en tête
  de page).
- **Recalage 2 (fin N2, §8.2 — dernier recalage prévu) — SOUMIS le
  29/10/2026** : ~~confiance N3–N8 re-évaluée~~ ✓ (`docs/RECALAGE-2.md`) —
  N2 livré en 3 h 01 d'horloge (trois segments de séance, dont une reprise
  après compression de contexte — sans perte) contre 6 h 05 pour N1 ; densité prod **47,6 l./comp.,
  DANS le postulat 40–62** (l'étage design est plus dense, comme postulé) ;
  facteur d'exécution N2 **inférieur** à N1 (0,060–0,094 contre 0,11–0,20
  h/j-h) — la question laissée ouverte par le recalage 1 reçoit une réponse
  favorable ; re-projection N3–N8+T : **5–8 séances effectives** (9–25 h
  agent), fourchette humaine 160–270 j-h maintenue (R5, centre de gravité
  documenté bas) ; vérification indépendante du jour : build 344 tests /
  0 échec / 1 ignoré, bancs rejoués verts (d8 1,000/1,000/1,000, p95
  29,3 ms, 0 saut). **Décision de suite (lancer N3 ?) attendue du
  propriétaire.**
- **Étages N3–N8 (TERMINÉS les 29–30/10/2026)** : ~~hover/inlay/
  signatures~~ ✓ (N3) ; ~~confort éditeur~~ ✓ (N7) ; ~~navigation &
  usages~~ ✓ (N4) ; ~~rename v2 par phases~~ ✓ (N5) ; ~~CodeAction v2 &
  correcteurs~~ ✓ (N6) ; ~~cycle de vie LSP, protocole riche &
  intégration~~ ✓ (N8 — 5 fiches, 44 comportements verts, 625 tests /
  0 échec). **Le programme de réécriture N1–N8 est INTÉGRALEMENT livré.**
- **Étage T — banc de parité croisé LIVRÉ le 29/10/2026** : le même corpus
  (completion-n2 v1 — 18 positions + 4 frappes) est rejoué contre le
  **vendored en oracle boîte noire** (jar `codeide-lsp@cb809e14` construit
  épinglé par `scripts/preparer-oracle.sh`, exécuté en sous-processus JVM
  isolé via `DriverOracle`) et le moteur neuf (in-process) ; la baseline
  **`bancs/baselines/parite-completion.json`** est signée (couverture cahier
  1.000/1.000 ; 165 deltas bruts documentés pour l'arbitrage de bascule) ;
  la CI **échoue** sur régression (job `banc-parite` : couverture cahier en
  baisse, libellé perdu, dérive de l'oracle). Le **gabarit de passe bascule
  sur appareil** (§8.5 — `bancs/GABARIT-BASCULE-APPAREIL.md` + kit
  `scripts/kit-bascule.sh`) est fourni : la mesure sur appareil et la
  décision de bascule reviennent au propriétaire (R5 : jamais simulées).
- **Étage T — volet documentation LIVRÉ le 29/10/2026** : guide
  d'utilisation complet (**`USAGE.md`**) — démarrage du serveur (lanceur
  lsp4j stdio/embarqué, négociation, sérialiseur de la capability
  maison), cycle de vie et gardes d'erreur, synchronisation des
  documents, **chaque fonctionnalité avec exemples de requêtes/réponses
  JSON-RPC**, le protocole riche `x-codeide.completionRanking`, les
  extensions expérimentales, l'API in-process du moteur et les bancs.
  README et structure du dépôt alignés sur l'état N1–N8 (625 tests). Le
  **banc de parité croisé** (cœur historique de T) reste à livrer.

Détail : `docs/PLAN-REECRITURE.md` (programme complet) et
`docs/etudes/moteur-java-psi/00-synthese.md` §4 (historique du plan).

Historique des livraisons (séquence confirmée N1 → N2 → N3 → N7 → N4 →
N5 → N6 → N8, T traversant) :

- **N1 noyau compilateur & environnement** (livré) — pont ecj patché,
  routeur de classpaths, ordonnanceur, diagnostics, repli syntaxe,
  caches, patcheur ecj-art ; recalage 1 exécuté (`RECALAGE-1.md`).
- **N2 complétion & classement** (livré) — pipeline complet + échelle de
  variantes, banc qualité CI-bloquante ; recalage 2 exécuté
  (`RECALAGE-2.md`) — dernier recalage d'estimation prévu.
- **N3 hover riche, inlay hints & signatures niveau 4** (livré) — 6 fiches,
  55 comportements verts (399 tests au dépôt).
- **N7 confort éditeur** (livré) — formateur clean-room, pliage,
  organisateur d'imports, gabarits, hiérarchies (458 tests au dépôt).
- **N4 navigation & usages** (livré) — routeur GO TO complet
  (intra-fichier → projet → binaire `jdt://decompiled/`, `superDefinition`
  aux libellés picker exacts, `implementation` croix-fichiers), câble
  binaire (`fqcnAt`/`isBinaryType`/`decompiledSource` — source attachée ou
  stub), chaînes de sélection strictement croissantes + cible de formatage
  à la frappe, et le **QueryExecutor des usages** (ADR 0014) :
  `references`/`documentHighlight` exhaustifs par clé de binding sur index
  de mots des sources — `Files.walk` retiré du chemin chaud, rappel =
  oracle (banc `:java:benchUsages` CI-bloquant : rappel 1,000, p95 ~62 ms
  < 100 ms) — 520 tests / 0 échec au dépôt.
- **N5 rename v2 par phases** (livré) — le moteur de renommage sûr
  structurel (fiche 026) : identification de cible par bindings (clé
  structurelle `kind:paquet.Classe.membre` — homonymes départagés),
  occurrences EXHAUSTIVES multi-fichiers sur l'index de mots (imports,
  constructeurs et allocations compris pour un type — le constructeur
  vise le type, l'import est une occurrence comme les autres),
  court-circuit file-local des locales, rappel = oracle (balayage
  complet) ; **collisions typées** AVANT écriture (ADR 0014 — identifiant
  invalide, nom pris, masquage, override non suivi + re-résolution de
  l'aperçu par diagnose différentiel) et overrides **opt-in explicite** ;
  le service honnête (fiche 061) : `prepareRename` au placeholder et à
  la plage EXACTS du jeton (caret au milieu compris), erreur typoÉE
  `renommage.sansCible` (RequestFailed -32803, message FR, `data`
  structuré) — jamais d'édition vide silencieuse — et le
  `WorkspaceEdit` sous la forme NÉGOCIÉE (`changes` héritée ou
  `documentChanges` versionnée au didOpen, jamais les deux) ;
  preview = résultat (le moteur n'écrit jamais sur le disque) — 550
  tests / 0 échec au dépôt.
- **N6 CodeAction v2 (ModCommand) & correcteurs** (livré) — le modèle
  ModCommand d'IntelliJ greffé en esprit (plan déclaratif SÉRIALISABLE,
  calcul en lecture, exécution déléguée au client), familles de
  problèmes ID-first (dispatch ID ecj → famille, Locale-indépendant),
  correcteurs texte et création de méthode (581 tests / 0 échec).
- **N8 cycle de vie LSP, protocole riche & intégration** (livré) — 5
  fiches, 44 comportements verts (625 tests / 0 échec au dépôt ; 6
  ignorés gate fermée, 0 gate `ANDROID_JAR` ouverte) : la couche
  protocole lsp4j complète (`protocole/` — machine à états
  UNINITIALIZED→EXITED, gardes I2/I3 aux codes EXACTS -32002/-32600,
  erreur typée rename -32803, notifications hors fenêtre ignorées),
  synchronisation plein texte **copy-on-write à époques** (« 0 réponse
  périmée », design intégré vague 4), publication asynchrone débouncée
  (50 ms — version courante transportée), diagnose complet ANALYTIQUE
  (import inutilisé → tag `Unnecessary`, fiche 056 — le chemin rapide à
  corps masqués garde les familles éteintes, fiche 014), capabilities
  complètes + **capability maison `x-codeide.completionRanking` PROUVÉE
  sur le fil** (poids packé + matcher sérialisé par item — re-filtrage
  local client, ADR 0012), complétion `filterText` = label (I12),
  documentSymbol/workspace-symbol (plage sur le NOM déclarant),
  codeLens à resolve paresseux (« 0/1 référence », balayage overlay +
  disque, déclaration exclue), hiérarchies types/appels (englobant
  contextType, cibles localisées dans leur source de DÉCLARATION),
  formatage document/plage/frappe, références à la PARITÉ STRICTE
  §10.4 (écart du vendored verrouillé : document courant seul), et les
  méthodes expérimentales `superDefinition`/`decompiledSource` via
  l'interface distante combinée. Les étages N1–N8 du plan §4 sont
  TOUS livrés ; reste **T** (banc de parité croisé ADR 0016 — oracle
  vendored en boîte noire) et la bascule à parité sur appareil.
- **T transverse qualité — volet documentation (livré le 29/10/2026)** :
  ~~guide d'utilisation~~ ✓ (**`USAGE.md`** — 13 sections : démarrage LSP
  stdio/embarqué, négociation avec le sérialiseur de la capability
  maison, cycle de vie et codes d'erreur exacts, synchronisation
  copy-on-write, 17 fonctionnalités avec exemples de requêtes/réponses
  JSON-RPC, protocole riche de classement, extensions expérimentales,
  API in-process `MoteurJava`, environnement de compilation, bancs et
  gates, conventions d'honnêteté) ; ~~README aligné~~ ✓ (structure du
  dépôt N1–N8, construction 625 tests, section Utilisation, état T) ;
  ~~banc de parité croisé~~ ✓ (**livré le 29/10/2026** — oracle vendored
  épinglé cb809e14 en boîte noire exécuté en sous-processus via des STUBS
  de compilation à alarme, corpus de parité versionné, format d'échange
  JSON maison, baseline `parite-completion.json` signée, CI bloquante
  `banc-parite`, gardes falsifiées : libellé perdu / dérive oracle /
  couverture cahier) ; ~~gabarit bascule appareil~~ ✓ (fourni —
  `bancs/GABARIT-BASCULE-APPAREIL.md`, kit `scripts/kit-bascule.sh`, banc
  de passe `BancBasculeAppareil` : couverture cahier + latences p50/p95
  par genre + zéro saut, 18/18 constaté sur JVM de bureau). **Reste à T :
  la passe appareil et la décision de bascule du propriétaire** (§8.5 —
  checklist à 6 points dans le gabarit, dont l'arbitrage des 165 deltas
  bruts et les gates `model.json` dormantes).
