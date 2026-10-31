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

