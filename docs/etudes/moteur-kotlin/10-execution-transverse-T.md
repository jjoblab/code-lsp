# 10 — Exécution transverse T : qualité transversale et clôture du programme

> **Décision du propriétaire reçue le 2026-10-31** dans le canal de mission :
> « Go transverse T » — le chantier transverse du plan §4 de `00-synthese.md`
> (« Bancs (fidélité K2 oracle, complétion, empreinte), corpus versionnés,
> recalages ») est OUVERT sur la branche `etude/moteur-kotlin`, à la suite de
> la clôture sur preuve mécanique de l'étage 6 (`09-execution-etage6.md`
> §9.4 : CI VERTE 10/10 sur le commit de clôture `f017a14` — run
> `41162022675` —, 38/38 comportements, suite 522 tests, dex d8 26 590
> méthodes / 4,10 Mo). Le présent document est le journal d'exécution du
> transverse T — le DERNIER chantier du plan §4 : il livre la fiche 021 (le
> DERNIER comportement du cahier), dresse l'état final des bancs et corpus,
> exécute le RELEVÉ FINAL du coût du programme et CLOTURE celui-ci. Méthode :
> miroir des rapports précédents — chiffres étiquetés [mesuré] / [estimé],
> écarts signalés.
>
> Gouvernance inchangée : branche `etude/moteur-kotlin` seule ; zones NOYAU
> anciennes (`parse/`, `resolve/`, `diagnostics/`, `completion/`, `session/`,
> `api/` de production de `codeide-lsp`) en lecture interdite R9.1. Le
> transverse n'ajoute AUCUNE reprise : le test de la fiche 021 est du code
> NEUF, la garde `it` du correctif est du code NEUF (manifeste inchangé).

## 1. Périmètre du transverse (plan §4 — cahier en main)

Le transverse T est le chantier qualité TRANSVERSE du plan §4 : « Bancs
(fidélité K2 oracle, complétion, empreinte), corpus versionnés, recalages » ;
critères de sortie : « CI bloquante verte ; 2 recalages » ; effort
**8–12 j-h [estimé]** ; il traverse les étages (aucune dépendance).

**État d'entrée** (journaux 05–09) : **468/469 comportements livrés** —
restait la fiche 021 (mesures de performance, domaine caches-perf), affectée
au transverse T dès la distribution de l'étage 6 (journal 09 §1 : « relève
des BANCS — mesure, pas capacité — journal T à venir »). Les bancs, corpus
versionnés et CI bloquante sont TOUS en place et VERTS depuis les clôtures
des étages (l'inventaire complet au §6) ; les 2 recalages promis au plan
sont FAITS (n° 1 à la clôture de l'étage 2 §11.4 du journal 05, n° 2 à la
clôture de l'étage 3 §9.4 du journal 06 — le n° 3, exécuté à la clôture de
l'étage 4 §8.4 du journal 07, a clos la série). Le solde du transverse T :
la fiche 021, le RELEVÉ FINAL du programme (§7) et sa clôture (§8).

| # | Item | Source du contrat |
|---|---|---|
| 1 | **Fiche 021 — mesures de performance** : gros fichier (≥ 1 500 lignes) GÉNÉRÉ DÉTERMINISTEMENT (~150 fonctions réelles : locales, appels stdlib, littéraux, chaînes), session au VRAI stdlib (index nourri, aucune racine de scan : l'univers des documents ouverts est complet par contrat), reparse complet et diagnostics à FROID puis 5 passes à CHAUD, chiffres IMPRIMÉS, document SAIN attendu (aucun non résolu), bornes de sanité volontairement larges (la régression pathologique, pas la latence) | fiche 021 (1 comportement) |
| 2 | **Inventaire final des bancs et corpus du programme** (fidélité K2 oracle, qualité complétion, empreinte/latence, corpus versionnés, parité, scan ART + dexage, CI bloquante) | plan §4 T ; ADR 0119 |
| 3 | **Relevé final du coût du programme** — les « recalages finaux » promis au journal 09 §9.4 : le programme COMPLET (étages 0–6 + T) face à l'abaque 174–282 j-h de la synthèse §4 | plan §4 T |
| 4 | **Clôture du programme** : preuves mécaniques complètes, journal, ALERTES (E-K8), push + attestation CI | miroir des clôtures d'étages |

## 2. Critères de sortie du transverse T (synthèse §4 — suivi)

| Critère | État | Preuve |
|---|---|---|
| **CI bloquante verte** (les 10 jobs, les 4 jobs Kotlin compris) | à suivre (§8) | attestation §8.4 |
| **2 recalages** | **FAITS avant le T** (n° 1 étage 2, n° 2 étage 3 ; le n° 3 a clos la série à l'étage 4) ; le T exécute le RELEVÉ FINAL du programme | journaux 05 §11.4, 06 §9.4, 07 §8.4 ; §7 ici |
| Fiche 021 verte — le 469e et DERNIER comportement du cahier | à suivre (§5) | `Fiche021MesuresPerfDiagnosticsTest` |
| Cahier complet 469/469 | à suivre (§8.1) | INVENTAIRE.md + journaux 03–10 |

## 3. Plan de vagues (la fiche d'abord, la clôture ensuite — il n'y a plus de dépendance à ordonner)

| Vague | Périmètre | Fiches | Comportements |
|---|---|---|---:|
| **T-a** | LA FICHE 021 : le test de mesures (document géant déterministe ~1 800 lignes, stdlib réel en COPIE, montage chemin de production D6, froid puis 5 passes chaudes, chiffres imprimés, bornes de sanité) + l'écart moteur découvert et corrigé à la mise au vert (le faux positif `it` implicite — §5.2) | 021 | 1 |
| **T-b** | Clôture : preuves mécaniques complètes (suite, jar, scan ART pile, dexage d8, bancs JDK 17, banc qualité, compilation croisée, provenance, baseline re-signée), inventaire des bancs (§6), relevé final (§7), ALERTES (E-K8), journal, push + attestation CI | — | — |

**Total : 1 comportement cahier** (l'agrégat du cahier — INVENTAIRE.md —
passe à 469/469). Chaque vague : code + tests NEUFS au nom exact du cahier +
suite verte + section du présent journal + commit ; le push et l'attestation
CI portent la clôture (miroir des étages 4–6 : le run de TÊTE éprouve
livraison et clôture ensemble).

## 4. Actions propriétaire ouvertes (reprises de l'étage 6 — journal 09 §4)

| # | Action | Référence | Bloque |
|---|---|---|---|
| 1 | Audit de similarité GPL (P-K1) — les copies R1–R8 sont effectuées, l'audit recommandé (rôle distinct des implémenteurs) s'exerce a posteriori | manifeste §3/§6, ALERTES §2 | plus rien de dur — le propriétaire en reste juge |
| 2 | Arbitrages Q-K1 (CIDX vs index dédié) / Q-K2 (décodage @Metadata maison) — PARTIELLEMENT ARBITRÉ par la livraison (index dédié `.kidx` dans `:kotlin`, ADR 0108) ; ouverts pour la structure future | DEMANDES-INTERFACE, ADR 0108, ALERTES §4 | le câblage classpath final (livré à l'usage — l'arbitrage structurel reste au propriétaire) |
| 3 | Conditions binaires intellij-dependencies (P-K2) — ÉTENDUES aux binaires lsp4j (EPL-2.0) de la pile chaude du serveur | ADR 0120, NOTICE, journal 09 §4 | la PUBLICATION du pack langue |
| 4 | Lecture du second manifeste | synthèse §6.4 | rien |
| 5 | Passe appareil couvrant les étages 1–6 (kit enrichi) — recommandation consignée, OPTIONNELLE | `04-execution-etage1.md` §16 | rien (les latences S0 restent tenues en JVM) |
| 6 | Recommandation E-K6 (test flottant Java `IntegrationMoteurIndexTest`) — 2 occurrences CI consignées, le flottement demeure | ALERTES §4 | rien (périmètre Java) |

## 5. Vague T-a — la fiche 021 : les mesures de performance (31/10)

### 5.1 Périmètre exécuté

| # | Item | État | Détail |
|---|---|---|---|
| 1 | Le test neuf | **Fait** | [Fiche021MesuresPerfDiagnosticsTest] (188 l., session/) au nom EXACT du cahier : `gros fichier - reparse complet et diagnostics mesures (froid puis 5 passes chaudes)` — le document géant DÉTERMINISTE (150 fonctions réelles de ~12 lignes : locales `montant`/`libelle`/`liste`/`trie`/`total`/`texte`/`profondeur`, appels stdlib `listOf` + chaîne `map`/`filter`/`sorted` + `sum` + `println`, littéraux et gabarits — **1 803 lignes** [mesuré], deux exécutions produisent le même texte au octet près) ; le montage est le CHEMIN DE PRODUCTION D6 (miroir du banc qualité 3e) : moteur nourricier NEUF + session BRANCHÉE + guichet attendu par sondage borné (90 s) — l'univers des preuves est COUVERT avant les mesures, sinon les `kt.unresolved` se tairaient et la preuve de santé serait creuse ; le stdlib réel en COPIE isolée (miroir fiche 042 — l'original jamais touché), AUCUNE racine de scan, `dossierIndex = null` (mémoire seule) |
| 2 | Le protocole de mesure | **Fait** | À FROID : `ouvrir` (premier parse intégral) puis `diagnostics` (première passe complète) ; À CHAUD : 5 passes — `modifier` (mutation INTÉGRALE du texte : un nom de fonction changé par passe, aucun cache par contenu ne survit) puis `diagnostics` recalculés intégralement ; les chiffres IMPRIMÉS pour le journal (relève [mesuré]) ; l'assertion de SANTÉ : AUCUN `kt.unresolved` (le document n'a que des preuves) |
| 3 | Les bornes de sanité | **Fait** | Volontairement LARGES (miroir du contrat de la fiche : la machine CI à 2 cœurs chasse la régression pathologique, pas la latence) : reparse à froid < 20 s, reprise à chaude < 10 s, diagnostics à chaud < 10 s — les chiffres réels vivent au §5.3 |
| 4 | La baseline de parité | **Fait** | Corpus 421 → **422 fichiers** [mesuré] (le fichier de test neuf — procédure documentée `-Dparite.majBase=true --rerun-tasks`) |

### 5.2 Un écart découvert et corrigé pendant la mise au vert (le cahier comme sonde — le DERNIER)

La première exécution a échoué sur l'assertion de santé : le moteur rendait
**300 `kt.unresolved` « Référence non résolue : `it` »** — un par occurrence
de l'`it` IMPLICITE des lambdas (`liste.map { it + 1 }`, `filter { it > n }`).

**Le diagnostic** : le TYPAGE connaissait l'`it` implicite
([TypageBorne.typeDeLexpression] : « l'attente à paramètre unique le type —
une locale nommée `it` prime », livré vague 2b, éprouvé par la fiche 046 en
COMPLÉTION), mais la passe DIAGNOSTICS ne le connaissait pas :
[verifierNomNu] consultait [resoudreNomNu] qui n'a AUCUNE garde `it` — un nom
nu introuvable de toutes les portées, l'univers des preuves étant couvert.
Toute lambda implicite d'un document sain était donc un FAUX POSITIF
potentiel : la famille était INVISIBLE du cahier jusqu'ici, car la fiche 021
est le SEUL comportement qui affirme un document SAIN portant des `it`
implicites d'extensions CLASSPATH (les lambdas des fiches 046/038 éprouvent
des attentes PROJET — `repeter(2) { it. }` — où le nom `it` nu… était
également un faux positif latent, jamais sonné par une assertion).

**Le correctif** (21 lignes, code NEUF — [DiagnosticsSemantiques.verifierNomNu]) :
la garde miroir EXACTE du typage — quand le nom est `it` et qu'aucun `it`
n'est déclaré au document, l'ATTE de la lambda englobante décide :
**démontrée à EXACTEMENT un paramètre** → l'`it` existe (silence) ;
**démontrée à une autre arité** → le nom ne peut pas exister (la preuve
tient, l'émission reste) ; **SANS attente démontrable** → la preuve manque
(silence D3 — jamais un faux positif sur la lambda d'une extension classpath
dont le récepteur n'est pas typable à cet étage : `liste.map { it + 1 }`).
La politique « preuve ou silence » (D1/D3) était violée par ÉMISSION sans
preuve ; elle est rétablie par la même attente qui fonde le typage.

**Non-régression** : la suite complète 523 tests VERTS après correctif —
le banc de fidélité K2 (baseline signée compte + empreinte) est INCHANGÉ,
les fiches diagnostics (017/022/046/047…) restent vertes : AUCUN test du
cahier n'exigeait l'émission sur `it` (vérifié par recherche — les `it` des
tests sont ceux du CODE de test Kotlin, jamais des documents sources).

### 5.3 Les mesures relevées [mesuré] (bac à sable, JVM toolchain 17, 2 cœurs)

Deux exécutions du même test, conditions différentes (les deux vertes) :

| Grandeur | Exécution isolée (JVM neuve) | Dans la suite (JVM échauffée) |
|---|---:|---:|
| Lignes / fonctions du document | 1 803 / 150 | 1 803 / 150 |
| Reparse À FROID (`ouvrir`) | **315 ms** (init de la grammaire comprise — première parse de la JVM) | **29 ms** |
| Reparse À CHAUD (`modifier`, 5 passes) | [124, 102, 47, 28, 78] ms — **min 28 ms** | [39, 21, 14, 15, 22] ms — **min 14 ms** |
| Diagnostics À FROID | **176 ms** | **135 ms** |
| Diagnostics À CHAUD (5 passes) | [69, 65, 55, 21, 20] ms — **min 20 ms** | [96, 71, 52, 26, 50] ms — **min 26 ms** |

**Lecture prudente** : ces chiffres sont des bornes basses JVM (machine de
bac à sable, pas un appareil — la synthèse §5 le consigne). Pour situer
SANS préjuger : l'ancien moteur publiait sur le même gabarit de document
(1 803 lignes, JVM de CI 2 cœurs — `docs/LSP_KOTLIN.md`) reparse 31 ms à
froid / 21 ms à chaud et diagnostics complets 469 ms à froid / 147 ms à
chaud [mesuré, machine différente]. Le moteur neuf tient le même ordre de
grandeur en reparse (14–28 ms à chaud) et un ordre de grandeur EN DEÇÀ en
diagnostics à chaud (20–26 ms) — la garde PAR DÉCLARATION (étage 2) ne
re-parcourt pas ce que la clôture a déjà prouvé. Aucune cible de latence
n'est engagée par ces chiffres : les cibles vivent dans la synthèse §4 et
restent à mesurer SUR APPAREIL (synthèse §5).

### 5.4 Preuves du bac à sable (avant commit)

- `Fiche021MesuresPerfDiagnosticsTest` **VERT** (première exécution :
  échec sur l'assertion de santé — l'écart §5.2 ; après correctif : vert,
  les deux exécutions) ;
- suite complète du module : **523 tests VERTS** toolchain 17
  (522 + 1 — le compte exact 1 test/comportement du cahier est maintenu
  jusqu'au bout) ;
- baseline de parité re-signée : corpus **421 → 422** fichiers (procédure
  documentée) ;
- jar du moteur : **991 021 o** (990 859 o à la clôture étage 6 — +162 o :
  la garde `it` seule) ;
- scan ART pile complète : **6 × AUCUNE** référence sensible (moteur
  **349 classes** — inchangé : le correctif n'ajoute aucune classe) ;
- dexage d8 (build-tools 34, min-api 26, pile CI inchangée) :
  **26 590 méthodes / 4 294 352 o (4,10 Mo)** — 81 % du plafond 32 768 /
  68 % du budget 6 Mo (**+0 méthode / +116 o** vs étage 6 : la garde est
  du pur logique, le câble lsp4j restant NON compté — §8.3) ;
- banc empreinte/latence JDK 17 : productions **5 448 / 31 368**, jetons
  **4 210 / 24 274**, marqueurs d'erreur **0** — déterminisme IDENTIQUE
  (le correctif ne touche pas la parse) ; tas au repos 3 724 Kio ;
- banc qualité complétion : **VERT** — p95 **7,71 ms** (SOUS la tolérance
  max(50 ms ; baseline × 1,5) de la baseline signée 7,53 ms), **0 saut**,
  livrée 1.000/1.000/1.000 vs naïve 0.375/0.486 ;
- compilation croisée : testClasses de TOUS les modules — BUILD SUCCESSFUL ;
- provenance : `verifier-provenance.sh` **rc=0** (aucun fichier hors
  registre — le T n'ajoute AUCUNE reprise).

## 6. Les bancs et corpus du programme — l'inventaire final (le cœur du transverse)

Ce que le plan §4 demandait au transverse (« bancs, corpus versionnés,
recalages ») est en place, VERT et SIGNÉ. L'inventaire à la clôture :

| Banc / corpus | Ce qu'il verrouille | Où il vit | CI (job) | État à la clôture T |
|---|---|---|---|---|
| **Fidélité K2 oracle** (banc de fidélité, vague 2g) | les diagnostics du moteur face au compilateur K2 sur les corpus quatre mondes : compte des faux négatifs ET empreinte SHA-256 du détail — toute variation, en hausse comme en baisse, est une divergence à expliquer | `BancFideliteK2Test` + `baseline-fidelite.txt` (signée) | suite du module (l'oracle s'exécute en CI, ~20 s) | VERT — inchangé par le correctif `it` (§5.2) |
| **Zéro faux positif** (passe corpus quatre mondes, vague 2g) | moteur 0 faux positif sur les mondes sains (Views, Compose, K/JVM, scripts) — l'invariant §4 du prompt | passe du banc de fidélité | suite du module | VERT |
| **Qualité complétion** (banc commun Java, vague 3e) | recall / top-1 / MRR sur corpus versionné 16 positions étiquetées (7 genres), chaîne livrée vs naïve (gate +5 pts), p95 ≤ tolérance, contrat zéro saut, déterminisme des genres, stabilité sous gel | `:kotlin:benchQualite` + `bancs/baselines/completion-kotlin.json` (SIGNÉE : livrée 1.000/1.000/1.000, p95 7,53 ms) | job dédié BLOQUANT (10e) | VERT — p95 7,71 ms sous tolérance, 0 saut |
| **Empreinte / latence** (banc de la grammaire, étage 0) | productions/jetons/marqueurs des corpus 500 et 3 000 lignes (déterminisme), p50/p95 parse, tas au repos — nombres, aucun seuil | `:kotlin:bench` | job dédié | VERT — déterminisme IDENTIQUE étages 2→T |
| **Parité grammaire** (suite de parité, étage 0) | le corpus du DÉPÔT (main + test, 422 fichiers) parse SANS marqueur d'erreur ; digeste SHA-256 par fichier (baseline signée) — une montée de grammaire ou un fichier neuf la déplace EXPLICITEMENT | fiches 010–012 + `digeste-corpus.txt` (signée, 422 lignes) | suite du module | VERT — re-signée 421→422 (le test T-a) |
| **Scan ART + dexage d8** (gates étage 0) | 0 motif sensible ART sur la pile spécifique (6 × AUCUNE) ; ≤ 32 768 méthodes / ≤ 6 Mo (budget arbitré E-K8) | `spike-kotlin/scripts/scan-art.py` + d8 build-tools 34 | job dédié BLOQUANT | VERT — 6 × AUCUNE ; 26 590 / 4,10 Mo |
| **Manifeste de provenance** | AUCUN fichier hors registre (reprises voie C contrôlées, vendoring amont noté) | `verifier-provenance.sh` + `PROVENANCE-REPRISE-KOTLIN.md` | job dédié BLOQUANT | VERT — rc=0, 8 reprises R1–R8 |
| **Compilation croisée** | le module compile et teste sous le JDK 17 de l'appareil (incident du 30/10 : API JDK 21 invisibles) | toolchain 17 du module | jobs Build + suite | VERT |

Les **corpus versionnés** : completion-kotlin v1 (16 positions, stdlib du
runtime seule — le job tourne sans SDK), corpus quatre mondes (17/27 jars,
`kotlin/mondes/`), digeste de parité (422 fichiers du dépôt), corpus du banc
d'empreinte (500/3 000 lignes générés déterministement) — plus le gabarit
appareil (`bancs/GABARIT-BASCULE-APPAREIL.md` + kit `scripts/kit-bascule.sh`,
mesures à retourner par le propriétaire — synthèse §5).

## 7. Le relevé final du coût du programme (les « recalages finaux » — journal 09 §9.4)

La série des recalages est CLOSE au n° 3 (étage 4) : ce relevé n'en ROUVRE
pas la méthode — il AGGRÈGE le programme complet et le confronte à l'abaque.
Commandes rejouables : `git log --format='%h %s' | grep lsp-kotlin`,
comptes des journaux 03–09, `wc -l` par arborescence.

### 7.1 Le compte par étage [mesuré]

| Étage | Commits | Tests (porte) | Volume livré (journaux) | Dominante | Tempo [mesuré-agent] |
|---|---:|---|---|---|---|
| Étude (phases 0–4) | 10 | — | inventaire + cahier 53 fiches/469 comport. + ADR 0100–0121 + spike S0–S3 | — | 29–30/10 |
| 0 — amorçage | 4 | 14 (parité) | grammaire vendorée (10 518 l. amont) + module + CI + provenance + descripteur | — | 30/10 |
| 1 — noyau | 16 | 14 → 132 | 19 fichiers main (4 421 l.) + tests ; reprises R1/R2/R4/R5 ; **gabarit appareil REMPLI** | — | 29–30/10 (clos 30/10 08:10 UTC) |
| 2 — résolution + diagnostics | 13 | 132 → 337 | 65 fichiers +17 373/−95 ; tableau A1–A31 ; banc fidélité | ≈ **85** l./comport. | ~2 jours calendaires (7 vagues) |
| 3 — complétion + classement | 6 | 337 → 393 | 32 fichiers +6 113/−238 ; banc commun + baseline | ≈ **135** l./comport. | ~5 h (séance continue) |
| 4 — navigation + rename | 4 | 393 → 431 | 12 fichiers +4 271/−6 | ≈ **112** l./comport. | ~40 min |
| 5 — confort | 6 | 431 → 477 | reprises R3/R6/R7/R8 + code neuf + 5 dorés WorkspaceEdit | ≈ **97** l./comport. | séance continue 31/10 |
| 6 — serveur LSP | 7 | 477 → 522 | 14 main + 6 test neufs + socle protocole 1 617 l. + harnais 449 l. + 7 dorés | ≈ **97** l./comport. | séance continue 31/10 |
| **T — transverse** | 3 | 522 → **523** | 1 test (188 l.) + garde `it` (21 l.) + baseline | — | séance continue 31/10 |

### 7.2 Le volume total du programme [mesuré]

| Arborescence | Lignes | Nature |
|---|---:|---|
| `kotlin/src/main/kotlin` | **25 250** | moteur NEUF (SPI, parse, resolve, classpath, session, protocole…) |
| `kotlin/src/main/vendor` | 10 518 | grammaire officielle amont (ré-obtention épinglée v2.3.0 — jamais « reprise ») |
| `kotlin/src/test` | **17 860** | 53 fiches du cahier + cas dorés + bancs + parité |
| `kotlin/src/bench` | 986 | bancs qualité + empreinte (source set dédié) |
| `docs/etudes/moteur-kotlin` | 5 223 | synthèse + inventaire + journaux 00–10 + ALERTES + DEMANDES |
| `docs/cahier-specifications-kotlin` | 2 952 | les 469 comportements extraits des tests sources |
| `docs/adr` (0100–0121) | 1 791 | les 21 ADR de la plage Kotlin |
| `spike-kotlin` | 11 608 | spike de mesure S0–S3 + scripts scan/dex (reproductible) |

**Le coût marginal global** : (25 250 + 17 860 + 986) l. de code livré pour
**469 comportements + 12 cas dorés + les socles** (protocole, harnais,
bancs, ingénierie) ≈ **92 l./comportement** [mesuré] (main ET tests compris,
journaux exclus) — la dominante s'est STABILISÉE à ~97 sur la deuxième
moitié du programme (recalages n° 2–3), l'écart global tient au socle de
l'étage 1 (noyau) porté par les premières vagues.

### 7.3 Le verdict face à l'abaque [estimé vs mesuré]

L'abaque de la synthèse §4 promettait **174–282 j-h** (médiane ≈ 225) pour
les étages 0–6 + T, voie C. Le rythme AGENT n'est PAS transposable au
jour-homme humain (consigné au recalage n° 1 : séances continues, 29/10 →
31/10) ; la donnée transposable est la STRUCTURE du coût, et elle est
TENUE : chaque étage a clos dans son enveloppe prévue (aucune refonte,
aucun retour arrière, aucune vague rouverte) ; le coût marginal par
comportement est resté dans le couloir 85–135 l. prédit par la méthode
socle + marginal (abaque Java transposée) ; les CI de clôture sont passées
en PREMIÈRE TENTATIVE à chaque étage (les seules reprises consignées sont
le flottant E-K6, périmètre Java). **Le programme EST complet : il ne
reste RIEN à l'abaque.** — les chantiers futurs (semanticTokens, frontière
RPC de classement, arbitrages Q-K1/Q-K2 structurels, montée de grammaire)
relèvent de NOUVELLES décisions du propriétaire, hors de ce plan.

## 8. Clôture du transverse T — et du PROGRAMME

### 8.1 Le compte du cahier

**469/469 comportements livrés** — 53 fiches, 0 reste. Le compte par
domaine (INVENTAIRE.md) : diagnostics 204, index-classpath 50, hover-inlay
44, completion 44, navigation 38, cycle-vie-lsp 29, parse 27, autre
(previews) 16, formatage 9, folding 7, caches-perf 1 (la fiche 021, livrée
ici). S'y ajoutent les **12 cas dorés** transversaux (5 WorkspaceEdit,
7 protocole) — hors cahier, verrouillés par leurs propres tests.

### 8.2 Ce que le programme a livré (synthèse en sept lignes)

1. **Noyau** (étage 1) : façade `Kt*`, session documents (verrou ADR 0212),
   scan paresseux, classpath + décodeur `@Metadata` + index appelables
   `.kidx`, synthétiques Android avant build (voie C R1–R8) ;
2. **Résolution + diagnostics** (étage 2) : hiérarchie de portées complète,
   catalogue `kt.*` conservateur, garde par déclaration, journal explain,
   tableau de spécification A1–A31, correctifs ;
3. **Complétion + classement** (étage 3) : pipeline 7 genres, chaîne
   weighers, banc commun Java — +62,5 pts top-1 / +51,4 pts MRR vs naïve,
   p95 sous la baseline ;
4. **Navigation + rename** (étage 4) : définition/type/impl/super,
   occurrences = oracle, 0 conflit manqué ;
5. **Confort** (étage 5) : formateur borné, pliage complet, moteur KDoc,
   previews Compose, actions déclaratives ModCommand-like ;
6. **Serveur LSP4J** (étage 6) : matrice paritaire verrouillée (19
   annoncées / 7 absentes honnêtes), codes exacts, époque copy-on-write,
   publication debouncée ;
7. **Qualité transversale** (T) : bancs et corpus signés, CI bloquante
   10 jobs, la fidélité K2 comme DONNÉE, le coût comme donnée — et le
   cahier COMPLET.

### 8.3 Les preuves de clôture (bac à sable, 31/10)

| Preuve | Résultat [mesuré] |
|---|---|
| Suite du module | **523 tests VERTS** (522 + fiche 021), toolchain 17 |
| Jar du moteur | **991 021 o** (+162 o vs étage 6 : la garde `it`) |
| Scan ART | **6 × AUCUNE** référence sensible (moteur 349 classes ; syntax-api 309 ; lang-syntax 3 ; annotations 61 ; stdlib 970 ; kotlin-metadata-jvm 492) — ServiceLoader OK |
| Dexage d8 (build-tools 34, min-api 26) | **26 590 méthodes / 4 294 352 o (4,10 Mo)** — 81 % du plafond / 68 % du budget 6 Mo (+0 méthode / +116 o vs étage 6 ; câble lsp4j NON compté — pour mémoire AVEC câble : 32 643 / 4,74 Mo) |
| Banc empreinte/latence JDK 17 | productions **5 448 / 31 368** ; jetons **4 210 / 24 274** ; marqueurs **0** ; tas au repos **3 724 Kio** — déterminisme IDENTIQUE |
| Banc qualité complétion | **VERT** — p95 **7,71 ms** (sous la tolérance de la baseline signée 7,53 ms), **0 saut**, livrée 1.000/1.000/1.000 |
| Compilation croisée | testClasses de TOUS les modules — BUILD SUCCESSFUL |
| Provenance | `verifier-provenance.sh` **rc=0** (aucune reprise ajoutée par le T) |
| Baseline de parité | re-signée **421 → 422** fichiers |

### 8.4 Clôture

**LE TRANSVERSE T EST CLOS sur preuve mécanique. LE PROGRAMME D'EXÉCUTION
DU PLAN §4 EST CLOS** (étages 0–6 + T : 469/469 comportements, 12 cas
dorés, bancs signés, CI bloquante). Ce qui reste appartient au
propriétaire : les actions §4 (P-K1/P-K2, arbitrages structurels Q-K1/Q-K2,
lecture du second manifeste), les mesures SUR APPAREIL (synthèse §5 — le
gabarit et le kit sont prêts), et les décisions d'OUVERTURE des chantiers
futurs. **STOP** : aucun chantier du plan §4 n'est ouvert ; ce journal est
le dernier de la série d'exécution. L'attestation CI du push de clôture
est consignée ci-dessous après consultation du run (miroir des étages 4–6).

**ATTESTATION CI (consignée après poussée)** : le push du transverse T
(`1878563` → `92315c5`, les deux commits vague T-a `164ace6` et clôture
T-b) a déclenché le run **`41165869078`** — consulté le 31/10 via l'API
GitHub Actions : **CI VERTE 10/10 sur le commit de clôture `92315c5`,
PREMIÈRE TENTATIVE** (le run porte le SHA de TÊTE du push : il éprouve la
livraison T-a ET la clôture T-b ensemble). Les dix jobs verts : Build et
tests JDK 21, banc parité croisée T (oracle vendored épinglé), **suite du
module Kotlin — les 523 tests, la fiche 021 comprise : cahier 469/469**
(le nom du job porte désormais « cahier COMPLET 469/469 »), banc qualité
complétion (baseline signée Java), banc empreinte/latence Kotlin, banc
qualité complétion Kotlin étage 3 (baseline signée — p95 sous tolérance),
gates Android SDK 34, banc empreinte/latence du noyau N1, manifeste de
provenance (anti-fichier-hors-registre), scan ART + dexage d8 sous seuils
étage 0. Le flottant E-K6 (périmètre Java) n'a pas frappé.

### 8.5 Post-scriptum d'exécution (le point d'honnêteté final)

Le correctif `it` (§5.2) est le 4e écart corrigé « pendant la mise au
vert » de l'histoire du programme (après 5a/5b/6e — journaux 08/09) et le
premier qui touche la POLITIQUE de silence (D3) plutôt que du câblage : la
fiche 021, dernière du cahier, était la seule à affirmer la santé d'un
document portant des `it` implicites d'extensions classpath — le cahier a
fait ce qu'on lui demande, révéler ce que l'architecture n'avait pas prouvé.
La leçon est consignée pour les chantiers futurs : toute famille de noms
qui NAÎT d'une attente (`it`, et demain les `componentN` de
destructuration, les récepteurs de DSL) doit vivre de la MÊME attente dans
toutes les passes — typage, diagnostics, complétion — sinon l'asymétrie
fabrique des faux positifs qu'aucun banc ne voit tant qu'aucune fiche
n'affirme le contraire.
