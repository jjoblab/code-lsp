# 11 — Décisions du propriétaire post-clôture : P-K1/P-K2 levées, registre des restes

> **Décisions reçues le 2026-10-31** dans le canal de mission, à la suite
> de la clôture sur preuve mécanique du programme (journal 10 §8.4 : CI
> VERTE 10/10 sur le commit de clôture `92315c5` — run `41165869078`,
> première tentative —, cahier **469/469** comportements + 12 cas dorés,
> bancs signés, dex d8 26 590 méthodes / 4,10 Mo). Après l'explication des
> « restantes » (actions P-K1/P-K2, arbitrages Q-K1/Q-K2, mesures sur
> appareil, chantiers futurs), le propriétaire a tranché les deux actions
> de licence et demandé la CONSIGNATION du reste dans le dépôt : le
> présent document est ce registre. Il ne ROUVRE AUCUN chantier (journal
> 10 §8.4 : STOP — aucun chantier du plan §4 n'est ouvert) et ne touche
> AUCUNE ligne de code (diff `docs/` seul).

## 1. Décisions du 31/10 — les actions P-K1 et P-K2 sont LEVÉES

| # | Décision | Motif déclaré (propriétaire, canal 31/10) | Conséquence |
|---|---|---|---|
| **P-K1** — audit de similarité GPL (ALERTES §2) | **JUGÉ NON NÉCESSAIRE — action CLOSE** | « aucune ligne de code de l'ancien projet n'a été copié » — appréciation du propriétaire (le projet tiers de référence GPL-3.0 n'a JAMAIS été consulté pendant la mission : R6.2/R9.4 tenus ; les reprises R1–R8 proviennent toutes du dépôt propriétaire `codeide-lsp`, contrôle R9.4 fichier par fichier à chaque copie — manifeste §3/§6) | plus AUCUNE action attendue ; les réserves historiques du manifeste et d'ALERTES §2 demeurent comme TRACE FACTUELLE inchangée |
| **P-K2** — conditions des artefacts binaires (ALERTES §2 ; étendues au câble lsp4j, journal 09 §4) | **JUGÉE NON NÉCESSAIRE — action CLOSE** (« de même ») | les conditions sont ACTÉES par le propriétaire : Apache-2.0 pour les jars `syntax-api` (intellij-dependencies — les sources sont Apache-2.0) ; EPL-2.0 pour le câble lsp4j 0.21.2 + jsonrpc + gson (PORTÉ PAR L'HÔTE en scope runtime, jamais embarqué dans le jar du moteur) | le DERNIER bloqueur de la PUBLICATION du pack langue est LEVÉ (voir §2.5) ; NOTICE et ADR 0120 demeurent la documentation d'attribution |

**Note E-K5** : la recommandation voisine d'ALERTES §4 (audit du motif
« scan paresseux » après l'écart de procédure de la vague 1c — même
principe que P-K1) n'a pas été rappelée par le propriétaire : elle reste
consignée SANS action attendue, sauf décision contraire de sa part (les
constats factuels E-K5 demeurent : aucune ligne copiée, code livré neuf,
écart tracé dans l'en-tête du fichier concerné).

## 2. Le registre des restes OUVERTS (consignation demandée)

> Rien de tout ceci n'est du code du plan §4 (clos sur preuve mécanique) ;
> rien ne bloque l'état livré. Chaque item porte son référentiel.

### 2.1 Arbitrages d'interface (`DEMANDES-INTERFACE.md` — le propriétaire arbitre, R3)

| # | Sujet | État | Ce que ça bloque |
|---|---|---|---|
| Q-K1 | Extension CIDX vs index dédié `.kidx` | OUVERT pour la structure future — PARTIELLEMENT ARBITRÉ par la livraison : l'index dédié `kotlin-appelables/*.kidx` de `:kotlin` est livré et éprouvé (ADR 0108 ; c'était le repli du risque n° 10 de la synthèse) | le câblage classpath FUTUR (unification éventuelle des deux index disque du dépôt — pas la livraison, qui fonctionne) |
| Q-K2 | Décodage `@Metadata` : `:index` ou `:kotlin` | OUVERT — livré dans `:kotlin` (reprises R4/R5 + `kotlin-metadata-jvm:2.2.10`, Apache-2.0, épinglée à la chaîne d'outils) ; double enjeu : partage Java/Kotlin et **LEVIER n° 1 du budget dex** (E-K8) | l'évolution future du module (si la marge dex fondait : décodeur maison sans la bibliothèque) |
| Q-K3–Q-K6 | modèle de symboles, contrat de pack, interop croisée, banc commun | OUVERTES (documentées dans `DEMANDES-INTERFACE.md`) | selon l'item — Q-K4 concerne précisément la publication du pack (§2.5) |

### 2.2 Mesures sur appareil (OPTIONNELLES — ne bloquent rien, les latences S0 sont tenues en JVM)

- **Passe couvrant les étages 1–6 avec le KIT ENRICHI** : l'assembleur du
  kit doit embarquer la pile complète, `kotlin-metadata-jvm` comprise (le
  kit v2 de la passe 3 de l'étage 1 n'exécutait que le banc grammaire —
  chargement paresseux, la pile classpath n'était jamais chargée sur son
  fil). Recommandation consignée (journal étage 1 §16 ; action n° 5 du
  journal 10 §4). Mécanique prête : gabarit de passe + kit assemblé côté
  bureau (`scripts/kit-bascule.sh`), exécution sur Termux, valeurs
  reportées au gabarit + transcript brut joint (R5 : jamais prétendre une
  mesure appareil sans résultats réels).
- **Ce que seul l'appareil prouve** (synthèse §5) : latences S0/S2 sous
  ART (reparse, diagnostics, complétion — le facteur ART 3–5× reste
  [estimé]) ; GC ART (éviction des soft-références, coût des
  `ReferenceQueue` — point resté « à vérifier », ALERTES §5) ; démarrage
  à froid du pack (grammaire + index) ; thermique sur passes répétées
  (min/médiane/max) ; corpus quatre mondes (17/27 jars) sur stockage FUSE
  réel. Le dexage réel est déjà mesuré par la CI à chaque push.
- **Repères JVM** (fiche 021, bornes basses bac à sable 2 cœurs,
  toolchain 17, document de 1 803 lignes / 150 fonctions) : reparse
  315 ms à froid (init de la grammaire comprise) / 14–28 ms à chaud ;
  diagnostics 135–176 ms à froid / 20–26 ms à chaud — aucun seuil
  appareil n'est engagé par ces chiffres.

### 2.3 Surveillances ouvertes (aucune action requise tant que la CI est verte)

- **E-K8 — budget dex** : OUVERTE en mode surveillance. À la clôture T :
  26 590 méthodes (**81 %** du plafond 32 768) / 4 294 352 o (**68 %** du
  budget 6 Mo arbitré le 31/10). Chaque évolution future du module
  actualise le pourcentage (le seuil CI échoue au-delà de 6 Mo — mesure à
  chaque push). Leviers consignés si la marge fond : Q-K2 (décodeur
  maison), Q-K1, réduction du câble lsp4j (gson en est la moitié — pour
  mémoire AVEC le câble entier : 32 643 méthodes / 4,74 Mo, 99,6 % du
  plafond).
- **E-K6 — test flottant Java** (périmètre Java, chantier clos) :
  `IntegrationMoteurIndexTest.reouvertureChaudeSansReconstruction` —
  2 occurrences CI consignées (runs `41004282232`, `41143338177`),
  relances vertes à chaque fois ; le flottement demeure. Recommandation
  au propriétaire : assouplir l'assertion temporelle (tolérance ×1,5 ou
  borne absolue) ou comparer des durées médiannées sur plusieurs passes.

### 2.4 Divers

- **Lecture du second manifeste** (synthèse §6.4) — action propriétaire,
  aucun bloqueur.
- **Suivi post-bascule Java borné** (pour mémoire, périmètre Java —
  `bancs/GABARIT-BASCULE-APPAREIL.md` §6) : filtrer les membres
  package-private lus au bytecode (`valueOfCodePoint`, `coder`, `value`,
  `hash`, `hashIsZero`, `isLatin1`) ; (optionnel) étendre la collecte
  TYPE aux grades hump/sous-chaîne ; ouvrir les gates `model.json`
  (Fiche010/Fiche039) sur export réel du propriétaire.

### 2.5 Chantiers futurs — NOUVELLES décisions du propriétaire (hors plan §4 clos)

| Chantier | État actuel (honnête) | Si ouvert |
|---|---|---|
| **semanticTokens** | NON annoncé (dette D3 documentée : les jetons sémantiques ne sont pas fiables au parseur seul ; requête tolérante — le client garde sa coloration de repli) | la résolution de l'étage 2 permet de dériver les tokens ; tenir la règle matrice « annoncée = servie ET testée » |
| **Frontière RPC de classement** | le serveur transporte déjà le POIDS par item (patron ADR 0118 ; plafonds 120/512, trim honnête avec total réel, drapeau `tronquee`), mais la capability maison `x-codeide.completionRanking` N'EST PAS annoncée (journal 09 §7.1 : le moteur Kotlin n'a pas de frontière RPC équivalente au `[FrontiereRpc]` Java) | extension dédiée : payload (matcher + poids + présentation) + re-filtrage local à chaque frappe côté client CodeIDE — à spécifier AVEC le client |
| **Montée de grammaire** | v2.3.0 figée (ADR 0119/0121) ; le `kt.syntax` sur du Kotlin 2.4+ est signalé honnêtement, fréquence croissante attendue | re-vendoring scripté mais manuel (2×/an, bootstrap 2.2.x) ; gates en place (parité, digeste 422 fichiers, bancs — une montée déplace le digeste EXPLICITEMENT) |
| **Profondeur K2** | extension (iii) différée — l'Analysis API/K2 embarquée est disqualifiée par la mesure (53,6 Mo / 64 507 méthodes / ~3,5 s d'amorçage [mesuré, spike]) | à rouvrir seulement si le besoin est démontré (coût S1 assumé et annoncé) |
| **Publication du pack langue Kotlin** | DÉBLOQUÉE par la levée de P-K2 (§1) — le pack n'est PAS publié à ce jour ; Q-K4 pose la question du mécanisme (miroir `:java` v1.0.0 : GitHub Packages, façade unique ?) | décision d'exécution : coordonnées, descripteur `version.properties` (le serverInfo « lspkotlin » porte déjà la version RÉELLE du descripteur — fiche 001), chaîne de publication (la workflow `publish.yml`, déclenchée par tags `v*`, ne couvre aujourd'hui que les coordonnées Java) |
| **Intégration CodeIDE du serveur Kotlin** | le client CodeIDE consomme TOUJOURS l'ancien LSP Kotlin servi par `codeide-lsp` (habitat unique limité au Java, instruction du 30/10 — `INTEGRATION-CODEIDE.md` §Périmètre) | le pendant Kotlin du plan I0–I6 Java : adaptation de l'hôte (l'actuel `KotlinLanguageServer.kt` de `:lsp:host`, 1 594 l.), publication, bascule avec retour arrière, passe appareil |

**Leçon consignée pour tout chantier futur** (journal 10 §8.5) : toute
famille de noms qui NAÎT d'une attente (`it`, et demain les `componentN`
de destructuration, les récepteurs de DSL) doit vivre de la MÊME attente
dans toutes les passes — typage, diagnostics, complétion — sinon
l'asymétrie fabrique des faux positifs qu'aucun banc ne voit tant
qu'aucune fiche n'affirme le contraire.

## 3. L'état du programme et du serveur au 31/10 (le point factuel)

### 3.1 Tous les chantiers du plan §4 sont TERMINÉS

Étages 0–6 + transverse T clos sur preuve mécanique : cahier **469/469**
(53 fiches, 0 reste) + 12 cas dorés transversaux ; bancs et corpus SIGNÉS
(fidélité K2 oracle avec baseline compte + empreinte, zéro faux positif
corpus quatre mondes, qualité complétion recall/top-1/MRR, empreinte et
latence, parité grammaire digeste 422 fichiers, scan ART + dexage d8,
manifeste de provenance, compilation croisée JDK 17) ; CI bloquante
**10/10 VERTE** sur le commit de clôture `92315c5` (run `41165869078`,
PREMIÈRE TENTATIVE). Le relevé final du coût (journal 10 §7) confronte
le programme à l'abaque 174–282 j-h : **il ne reste RIEN à l'abaque**.
Les seuls restes sont consignés au §2 du présent document — aucun n'est
du code du plan.

### 3.2 Le serveur LSP4J : LIVRÉ et PROUVÉ — pas encore EN SERVICE côté CodeIDE

**Livré et prouvé** (vague 6e — journal 09 §7 ; preuves §9.3) : la couche
`protocole/` NEUVE — `CycleVie` (machine à cinq états, le mode in-process
ne tue jamais la JVM), `EtatsDocuments` (époque copy-on-write,
« 0 réponse périmée », `calculerFraiche` borné à 3 relances),
`CapacitesServeur` (matrice paritaire : **19 capacités annoncées et
servies** — hover, complétion `.`/`(`, signatureHelp, définition,
typeDefinition, implémentations, références, surlignage, symboles
document/espace, sélections, pliage, formatage, formatage de plage,
codeAction quickfix, inlayHint, rename+prepare — et **7 absentes
honnêtes** : semanticTokens, codeLens, callHierarchy, typeHierarchy,
documentColor, onTypeFormatting, linkedEditingRange ; requêtes tolérantes,
jamais d'annonce fantôme), `ConvertisseurLsp` (repère offsets ↔ UTF-16),
`PublicateurDiagnostics` (debounce 50 ms par URI), `ServeurLangageKotlin`
(gardes I2/I3, codes -32600/-32002 exacts, notifications hors fenêtre
ignorées). Éprouvé par le harnais `BoucleLocale` (deux lanceurs piped —
les allers-retours lsp4j AUTHENTIQUES) et 7 cas dorés ; suite 523 tests
VERTS toolchain 17 ; jar 991 021 o ; dex 26 590 méthodes / 4,10 Mo sous
seuils ; serverInfo « lspkotlin » à la version réelle du descripteur.

**Pas encore en service** : (a) le pack langue n'est pas PUBLIÉ — le
bloqueur P-K2 est levé par §1, la publication reste à décider et exécuter
(§2.5) ; (b) le client CodeIDE consomme toujours l'ancien LSP Kotlin de
`codeide-lsp` — le branchement du serveur neuf (adaptation de l'hôte,
publication, bascule, passe appareil) est un chantier futur (§2.5).
NB câble : lsp4j est PORTÉ PAR L'HÔTE (scope runtime, jamais embarqué
dans le jar du moteur) — l'intégration côté CodeIDE doit le fournir,
comme pour `:java` (le client impose 0.22.0, le moteur est binaire sous
0.22.0 — même recette, filet `AlignementLsp4jTest`).

## 4. Attestation CI du push de consignation

> Consignée après consultation du run (miroir des clôtures 4–6 et du
> journal 10 §8.4) — le présent commit ne touche QUE des documents
> (`docs/` seul, aucune ligne de code).
