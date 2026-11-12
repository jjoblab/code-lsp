# USAGE — serveur de langage Kotlin (`lspkotlin`)

> Guide d'utilisation du serveur Kotlin, construit (Phase 3, 03/11/2026)
> depuis le **journal d'exécution de l'étage 6** (serveur LSP + protocole
> riche — `lsp-kotlin/docs/etudes/moteur-kotlin/09-execution-etage6.md`) et la
> **matrice de capacités paritaire** qu'il verrouille. Tous les
> comportements décrits sont couverts par le cahier (469/469) et les 12 cas
> dorés. Le miroir architectural est le [`USAGE.md`](../lsp-java/USAGE.md)
> du serveur Java.

## 1. Identification et dépendances

- `serverInfo.name` = **`lspkotlin`** ; `serverInfo.version` = la version
  **réelle du descripteur de pack** (fiche 001 — jamais une valeur codée en
  dur).
- Le câble **lsp4j 0.21.2** (même version que `:java` — le câble
  `cel-lsp` étant commun) est **fourni par l'hôte** : le jar du moteur ne
  l'embarque PAS (miroir `:java` ; budget dex inchangé).
- La grammaire officielle vendorée est embarquée (notices Apache-2.0 dans
  `src/main/vendor/` — VENDOR.md).

## 2. Surface LSP exacte (matrice paritaire — verrouillée par le cas doré `matrice-paritaire`)

### 2.1 Capacités ANNONCÉES et servies (19)

| Famille | Capacités |
|---|---|
| Synchronisation | plein texte, positions UTF-16 |
| Survol | `hover` (markdown ```kotlin + KDoc) |
| Complétion | `completion` (déclencheurs `.` et `(`) |
| Signature | `signatureHelp` (déclencheurs `(` et `,`) |
| Navigation | `definition`, `typeDefinition`, `implementation`, `references` |
| Surlignage | `documentHighlight` |
| Symboles | `documentSymbol`, `workspaceSymbol` |
| Structure | `selectionRange`, `foldingRange` |
| Édition | `formatting`, `rangeFormatting` |
| Actions | `codeAction` (quickfix) |
| Confort | `inlayHint` |
| Renommage | `rename` + `prepareRename` |

### 2.2 Capacités NON annoncées (honnêtes — 7)

`semanticTokens` (dette D3 documentée), `codeLens`, `callHierarchy`,
`typeHierarchy`, `documentColor`, `onTypeFormatting`,
`linkedEditingRange`. Règle : **capacité testée = annoncée ; sinon
absente** — l'ajout relève d'une extension dédiée, après la bascule
(ADR 0136). Les requêtes non annoncées reçoivent une **réponse vide**
(convention I9), jamais une erreur.

### 2.3 Extensions maison

`x-codeide.completionRanking` n'est **pas annoncée** : le moteur Kotlin
n'a pas de frontière RPC de classement équivalente à celle du serveur Java
(journal étage 6 §7.1 — écart consigné ; ajout à demander au propriétaire
si le re-filtrage local client est voulu).

## 3. Cycle de vie et codes d'erreur (cas dorés `codes-exacts`, `notifications-hors-fenêtre`)

Machine à cinq états :
`UNINITIALIZED → INITIALIZING → INITIALIZED → SHUTDOWN → EXITED`.

| Situation | Comportement |
|---|---|
| Requête avant `initialized` | rejet **synchrone** `-32002` |
| Second `initialize` | rejet `-32600` avec « initialize » dans le message |
| Requête après `shutdown` | rejet `-32600` |
| Notifications hors fenêtre | **ignorées** (jamais d'erreur) |
| `exit` en mode in-process | la JVM n'est **jamais** tuée (mode autonome seul) |

## 4. Cohérence des réponses : l'époque copy-on-write (cas doré `époque-copy-on-write`)

Chaque mutation de document publie un **instantané immuable** et incrémente
l'époque ; TOUTES les requêtes passent par le recalcul frais
(`calculerFraiche` — rejeu borné à 3 relances) : **0 réponse périmée**, par
construction. `didSave` sans texte conserve l'époque (le texte n'a pas
bougé).

## 5. Publication des diagnostics

- Debounce **50 ms par URI** sur fil dédié (`lspkotlin-publications`) — le
  fil RPC n'attend jamais.
- Publication **< 15 s par construction**, toujours la version COURANTE du
  buffer ; document fermé = liste VIDE.
- Diagnostics `kt.*` avec tags et informations liées (KP15).
- `didChangeWatchedFiles` traité par le fil dédié du scan projet.

## 6. Intégration côté hôte

Deux modes, miroir du serveur Java (N8) :

1. **In-process (embarqué)** : construire le serveur et le brancher sur le
   câble lsp4j de l'hôte — c'est l'assemblage éprouvé par le harnais
   `BoucleLocale` du cahier (deux lanceurs piped, allers-retours lsp4j
   authentiques). Le SPI du moteur vit en zone `api/`
   (`ServiceMoteurLangageKotlin`, fiche 042).
2. **Pack langue** : le **descripteur de pack** (fiche 001) porte la
   version réelle et le drapeau pack ; le chargement passe par le
   `ChargeurMoteurKotlin` (fiche 001 — 3 comportements verrouillés par le
   cahier). Le pack est **publié** à partir de la version 1.13.0 (tag
   `v1.13.0`, 03/11/2026) : coordonnées
   `jo.codeide.lsp.kotlin:jo-codeide-lsp-kotlin` sur
   `maven.pkg.github.com/jjoblab/code-lsp` (Q-K4 tranché le 03/11/2026 —
   mécanisme miroir `:java`, dépôt et déclencheur partagés).

Configuration serveur : le comportement est piloté par le descripteur de
pack ; aucune propriété système n'est requise en service.

### 6.1 Pression mémoire — le SPI neutre (vague V-A, ADR 0117/0134)

L'hôte qui reçoit les rappels mémoire de SA plateforme (`onTrimMemory`
côté Android) les TRADUIT en niveaux neutres et appelle
`session.reduireMemoire(niveau)` :

| Rappel plateforme (Android) | Niveau neutre |
|---|---|
| `TRIM_MEMORY_UI_HIDDEN` | `NiveauMemoireKotlin.UI_MASQUEE` |
| `TRIM_MEMORY_RUNNING_MODERATE/LOW`, `TRIM_MEMORY_BACKGROUND` | `NiveauMemoireKotlin.FAIBLE` |
| `TRIM_MEMORY_RUNNING_CRITICAL`, `TRIM_MEMORY_MODERATE` | `NiveauMemoireKotlin.CRITIQUE` |
| `TRIM_MEMORY_COMPLETE`, `onLowMemory` | `NiveauMemoireKotlin.URGENT` |

La réponse est TOUJOURS sûre : les caches de lecture paresseuse du
classpath se vidangent dès `FAIBLE` (chaque cache porte son plafond
d'octets estimés — vidange totale au plafond), les annuaires de
complétion et les gels de classement dès `CRITIQUE` ; les documents
ouverts, l'époque copy-on-write et les index persistants ne partent
JAMAIS — la justesse est intacte, seul le coût de relecture revient. Une
session fermée ignore l'appel (no-op silencieux).

## 7. Procédure de bascule CodeIDE (ADR 0136 — AVANT toute extension)

1. **Publier le pack** (Q-K4 — bloqueurs de licence P-K1/P-K2 levés le
   31/10/2026).
2. **Achever I0–I6** (miroir Java figé à I1, chiffré 3–6 j-h [estimé]) et
   brancher le serveur dans CodeIDE.
3. **Mesurer sur appareil** (gabarit `07-gabarit-mesure-appareil.md`,
   passes 1/2/3 + médiane) — cibles D5 : complétion < 100 ms p95 perçu,
   diagnostics < 300 ms, démarrage chaud < 3 s (facteur ART ≈ 4× observé
   côté Java : aucune extrapolation JVM).
4. **Seulement ensuite** étendre les 7 capacités absentes (écarts GK-025/
   GK-026) et arbitrer la capability de classement.

Sans bascule, toute capacité est **invisible et non mesurable** (KH16) —
c'est la raison d'être du séquencement.

## 8. Diagnostics des problèmes courants

| Symptôme | Cause probable / conduite |
|---|---|
| « Unsupported request method » sur une extension maison | la capacité n'est pas annoncée (§2.2/§2.3) — réponse vide attendue pour les requêtes LSP standard ; une extension maison non annoncée doit être demandée au propriétaire |
| Diagnostics absents | document non ouvert (`didOpen` requis) ou publication débouncée (50 ms) ; document fermé = liste vide par contrat |
| Réponse vide sur une requête récente | supersession/péremption honnête : la frappe a rendu la requête caduque, le client re-émet (époques — §4) |
| Classe introuvable côté hôte (lsp4j) | le câble lsp4j est fourni par l'hôte (§1) — vérifier le classpath hôte, jamais le jar moteur |
| Échec de build Maven Central 429 | panne transitoire déjà consignée (AL4 / E-K10) — relancer, jamais de reprise de code |
| Parité du digeste en échec après déplacement de fichiers | procédure P-1 : preuve d'équivalence puis re-signature dédiée (E-K9 consigné) |

Les alertes complètes : [`lsp-kotlin/docs/etudes/moteur-kotlin/ALERTES.md`](docs/etudes/moteur-kotlin/ALERTES.md).
