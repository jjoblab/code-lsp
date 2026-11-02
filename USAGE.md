# USAGE — Guide d'utilisation du serveur de langage Java `lspjava`

> Ce guide décrit l'utilisation du serveur de langage Java de CodeIDE
> livré par le programme de réécriture N1–N8 (`docs/PLAN-REECRITURE.md` §4).
> Il complète le [`README.md`](README.md) (état du programme, décisions) et
> les ADR (`docs/adr/`) — en cas de divergence, le code et le cahier de
> spécifications (`docs/cahier-specifications/`) font foi.

**Sommaire**

1. [Présentation](#1-présentation)
2. [Prérequis et construction](#2-prérequis-et-construction)
3. [Architecture et modules](#3-architecture-et-modules)
4. [Démarrer le serveur — intégration LSP](#4-démarrer-le-serveur--intégration-lsp)
5. [Cycle de vie et gardes protocolaires](#5-cycle-de-vie-et-gardes-protocolaires)
6. [Synchronisation des documents](#6-synchronisation-des-documents)
7. [Fonctionnalités, avec exemples](#7-fonctionnalités-avec-exemples)
8. [Capability maison `x-codeide.completionRanking`](#8-capability-maison-x-codeidecompletionranking)
9. [Extensions expérimentales](#9-extensions-expérimentales)
10. [Utilisation in-process du moteur](#10-utilisation-in-process-du-moteur)
11. [Environnement de compilation](#11-environnement-de-compilation)
12. [Bancs et gates de CI](#12-bancs-et-gates-de-ci)
13. [Conventions d'honnêteté et limites connues](#13-conventions-dhonnêteté-et-limites-connues)

---

## 1. Présentation

`lspjava` est le serveur de langage Java de CodeIDE, réécrit intégralement
en Kotlin (ADR 0001, option 2) à partir d'un cahier de spécifications
extrait de 449 tests de l'ancien moteur (65 fiches, 449 comportements —
`docs/cahier-specifications/`). Il pilote le compilateur **Eclipse ecj
3.40.0 patché** (module `:ecj-art`) pour servir un protocole LSP 3.17
complet : complétion classée, survol riche, diagnostics, navigation,
renommage sûr multi-fichiers, actions correctrices, formatage, pliage,
hiérarchies, lentilles…

Le serveur se branche de deux manières :

- **intégration LSP standard** (JSON-RPC sur tuyaux — stdio, sockets ou
  tuyaux en mémoire) via la couche `org.eclipse.lsp4j` : c'est le mode
  d'intégration de l'éditeur CodeIDE (§4) ;
- **utilisation in-process** du moteur (`MoteurJava`) : c'est le mode des
  tests et des bancs — toutes les fonctionnalités sont appelables
  directement en Kotlin, sans boucle JSON-RPC (§10).

Caractéristiques saillantes, verrouillées par le cahier :

- **encodage des positions UTF-16** (valeur exacte annoncée à
  `initialize` — fiche 055) ;
- **synchronisation plein texte** copy-on-write « 0 réponse périmée » :
  une réponse calculée contre un texte révolu n'est jamais livrée ;
- **diagnostics publiés en asynchrone** (debounce 50 ms par URI,
  publication sous 15 s par construction) ;
- **règle matrice ADR 0015** : « capacité testée = annoncée ; sinon
  absente » — aucune capability fantôme ;
- **capacité maison `x-codeide.completionRanking`** : chaque item de
  complétion transporte son payload de classement pour le re-filtrage
  local du client (§8) ;
- **erreurs typées exactes** : `-32002` avant `initialized`, `-32600`
  après `shutdown` et sur `initialize` réentrant, `-32803` pour un
  renommage sans cible (§5).

## 2. Prérequis et construction

| Prérequis | Détail |
|---|---|
| JDK | 21 (le wrapper Gradle 8.13 est inclus ; le plugin foojay télécharge un JDK 21 Temurin si la machine n'en dispose pas) |
| Système | JVM de bureau ou Termux/OpenJDK 17+ selon la cible ; bytecode **17** produit (consommable par d8/ART) |
| Réseau | Maven Central au premier build (ecj, lsp4j, kotlin-stdlib…) |

```bash
./gradlew build                    # build + tests (625 tests : 38 modèle + 84 index
                                    #   + 36 ecj-art + 463 noyau :java + 4 intégration)
./gradlew :ecj-art:patcherEcj      # produit ecj-art/build/ecj-patche/ecj-3.40.0-art.jar
./gradlew :java:bench              # banc empreinte/latence du noyau (JVM séparée)
./gradlew :java:benchQualite       # banc qualité complétion — ÉCHOUE sur régression
                                    #   vs baseline signée bancs/baselines/completion.json
./gradlew :java:benchUsages        # banc usages — rappel 1,000, p95 < 100 ms, CI-bloquant
./gradlew :java:benchParite        # banc de parité croisée T — oracle vendored vs moteur neuf
                                    #   (prérequis : bash scripts/preparer-oracle.sh → .oracle/)
ANDROID_JAR=… ./gradlew :java:test # ouvre les gates Android (fiches 033/004/036/043/044/050/053)
```

Six tests s'ignorent proprement quand les gates d'environnement sont
fermées (SDK Android, `model.json` d'appareil) — l'ignorance est un
comportement explicite, pas un silence.

## 3. Architecture et modules

| Module | Rôle | Publication |
|---|---|---|
| `:java` | **le moteur LSP Java** : noyau compilateur, complétion & classement, survol, inlay, signatures, navigation & usages, renommage, actions de code, confort éditeur (formatage, pliage, imports, gabarits, hiérarchies) et la **couche protocole lsp4j** (`protocole/`) | étages N1–N8 |
| `:index` | index disque CIDX (mots des sources — l'oracle des usages) | reprise contrôlée vague 1b |
| `:model` | modèle de projet LSP + types du protocole tooling | reprise contrôlée vague 1b |
| `:testing` | suite d'intégration moteur ↔ index CIDX | réécrite à l'étage N1 |
| `:ecj-art` | patcheur build-time d'ecj 3.40.0 pour ART (shims de compatibilité) | vague 1c |

La classe d'intégration est `ServeurLangageJava`
(`jo.codeide.lsp.java.protocole.ServeurLangageJava`) : elle implémente les
trois interfaces lsp4j `LanguageServer`, `TextDocumentService`,
`WorkspaceService` **plus** les méthodes expérimentales
(`ServiceExperimental` — §9). Le moteur pur est
`jo.codeide.lsp.java.MoteurJava` (§10).

## 4. Démarrer le serveur — intégration LSP

Le serveur n'impose pas de binaire autonome : le branchement de
substitution dans CodeIDE (dépôt tiers) est coordonné côté propriétaire
(plan §4, note N8) — **procédure préparée : `docs/BASCULE-CODEIDE.md`**
(décision de bascule §8.5 signée le 30/10/2026). L'intégrateur câble un
lanceur lsp4j autour de
`ServeurLangageJava` — sur stdio pour un processus séparé, sur tuyaux en
mémoire pour un serveur embarqué.

### 4.1 Serveur sur stdio (processus séparé)

```kotlin
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.services.LanguageClient
import jo.codeide.lsp.java.protocole.CapacitesServeur
import jo.codeide.lsp.java.protocole.ServeurLangageJava

fun main() {
    val serveur = ServeurLangageJava().apply { setStandaloneMode(true) }

    val lanceur = Launcher.Builder<LanguageClient>()
        .setLocalService(serveur)
        .setRemoteInterface(LanguageClient::class.java)
        .setSystemStreams()                      // stdin / stdout
        .configureGson { gson ->
            // OBLIGATOIRE pour émettre la capability maison (§8) :
            gson.registerTypeAdapter(
                ServerCapabilities::class.java,
                CapacitesServeur.serialisateurCapacites(),
            )
        }
        .create()

    serveur.connect(lanceur.remoteProxy)
    lanceur.startListening()
    // exit() tue le processus : setStandaloneMode(true) (fiche 055).
}
```

### 4.2 Serveur embarqué (tuyaux en mémoire)

Le montage de référence est celui du harnais des fiches 054/056/058/059
(`java/src/test/…/protocole/BoucleLocale.kt`) : deux `PipedInputStream` /
`PipedOutputStream` croisés, un lanceur par extrémité, le client espion
branché par `serveur.connect(lanceur.remoteProxy)`. Les requêtes et
publications traversent la sérialisation Gson et les en-têtes
`Content-Length` — exactement le chemin de production.

### 4.3 Négociation

- Le **client** qui connaît les extensions maison construit son proxy sur
  l'interface combinée `LangageServeurDistant`
  (`LanguageServer` + `ServiceExperimental`) ; un client standard utilise
  `LanguageServer` et ignore simplement les méthodes inconnues.
- Le **serveur** rend sa réponse `initialize` complète via
  `CapacitesServeur.reponseInitialize()` : `positionEncoding` UTF-16,
  `textDocumentSync` Full, `completionProvider` (déclencheur `.`),
  `hoverProvider`, `signatureHelpProvider` (déclencheurs `(` et `,`),
  `definitionProvider` / `typeDefinitionProvider` /
  `implementationProvider` / `referencesProvider` /
  `documentHighlightProvider` / `documentSymbolProvider`,
  `documentFormattingProvider` / `documentRangeFormattingProvider` /
  `documentOnTypeFormattingProvider` (`}` puis `;`),
  `selectionRangeProvider`, `foldingRangeProvider`, `codeActionProvider`
  (forme détaillée, kind `quickfix`), `codeLensProvider`
  (`resolveProvider: true`), `renameProvider` (forme options,
  `prepareProvider: true`), `callHierarchyProvider`,
  `typeHierarchyProvider`, `workspaceSymbolProvider`,
  `inlayHintProvider`, la capability maison
  `x-codeide.completionRanking` (§8), et — depuis la vague V-E (ADR 0028,
  décision D1) — le **contrat usages versionné** `x-codeide.usages` dans le
  champ `experimental` : `multiFichiers` (la réponse `references` rend les
  usages multi-fichiers réels — la parité stricte §10.4 est abandonnée),
  `groupes` (le RPC `textDocument/usagesGroupes`, §9.3) et `plafondRendu`
  (2 000 — le plafond honnête du RPC groupé).
- `serverInfo` : nom **`lspjava`**, version **1.4.0** — lue de la
  ressource embarquée `version.properties`, générée à la construction
  depuis la version du projet (fiche 059 I7 : jamais de version fantôme).

```json
{
  "jsonrpc": "2.0", "id": 1, "method": "initialize", "params": {
    "processId": null,
    "rootUri": "file:///projet/demo",
    "capabilities": {},
    "workspaceFolders": [{ "uri": "file:///projet/demo", "name": "demo" }]
  }
}
```

Extrait de réponse (seule la capability maison est tronquée ici, voir §8) :

```json
{
  "jsonrpc": "2.0", "id": 1, "result": {
    "capabilities": {
      "positionEncoding": "utf-16",
      "textDocumentSync": 1,
      "completionProvider": { "triggerCharacters": [".", "@"], "resolveProvider": false },
      "signatureHelpProvider": { "triggerCharacters": ["(", ","] },
      "hoverProvider": true,
      "definitionProvider": true,
      "typeDefinitionProvider": true,
      "implementationProvider": true,
      "referencesProvider": true,
      "documentHighlightProvider": true,
      "documentSymbolProvider": true,
      "foldingRangeProvider": true,
      "selectionRangeProvider": true,
      "documentFormattingProvider": true,
      "documentRangeFormattingProvider": true,
      "documentOnTypeFormattingProvider": { "firstTriggerCharacter": "}", "moreTriggerCharacter": [";"] },
      "codeActionProvider": {
        "codeActionKinds": ["quickfix", "source", "source.organizeImports", "source.generate", "refactor", "refactor.extract", "refactor.rewrite"],
        "resolveProvider": true
      },
      "codeLensProvider": { "resolveProvider": true },
      "renameProvider": { "prepareProvider": true },
      "callHierarchyProvider": true,
      "typeHierarchyProvider": true,
      "workspaceSymbolProvider": true,
      "inlayHintProvider": true,
      "experimental": {
        "superDefinitionProvider": true,
        "decompiledSourceProvider": true,
        "x-codeide.usages": { "version": 1, "multiFichiers": true, "groupes": true, "plafondRendu": 2000 }
      }
    },
    "serverInfo": { "name": "lspjava", "version": "1.4.0" }
  }
}
```

> **Attention** : sans l'enregistrement du sérialiseur
> `CapacitesServeur.serialisateurCapacites()` (crochet `configureGson`),
> le champ maison `x-codeide.completionRanking` **n'apparaît pas sur le
> fil** — Gson sérialise les champs du type déclaré et la sous-classe
> perdrait son champ propre (mesuré sur le fil — §8).

### 4.4 Le régime vivant — intégration hôte (multi-modules à chaud)

Pour l'intégration CodeIDE (`:lsp:host`, docs/`INTEGRATION-CODEIDE.md`
§3.3 option B), le serveur se construit sur un **environnement vivant**
possédé par l'HÔTE au lieu de la fabrique figée :

```kotlin
import jo.codeide.lsp.java.EnvironnementCompilation
import jo.codeide.lsp.java.protocole.ServeurLangageJava

val environnement = EnvironnementCompilation(jdkHome = jdkDuProjet)
environnement.routeurFichierModule = { chemin ->
    // l'instantané COURANT du modèle (relu à CHAQUE requête, fiche 047)
    modele?.associer(chemin)?.nom
}
environnement.remplacerContexteModule(":app", racinesDeApp, jarsDeApp)
environnement.setIndexNoms(adaptateurIndex)

// UNE session LSP Java par projet — le moteur naît PAR REQUÊTE
// (routage fichier → module), les syncs remplacent les CONTEXTES à chaud.
val serveur = ServeurLangageJava(environnement) { /* rappel d'arrêt (registre des serveurs vivants) */ }
```

Deux régimes, deux constructeurs :

| | régime FIGÉ (fabrique, §4.1) | régime VIVANT (environnement) |
|---|---|---|
| moteur | naît de la racine à `initialize`, ne bouge plus | résolu **par requête** par le routeur fichier → module (fiche 047, relu à chaque requête) |
| reconfiguration | — (reconnexion requise) | `remplacerContexteModule` **à chaud** (sémantiques replaceAll fiche 034 : effectif = moteur du module reconstruit, redondant = no-op) — jamais de re-initialize |
| diagnostics | moteur figé | moteur du module résolu **au tir** (fenêtre du debounce) |
| renommage | moteur figé | moteur du document renommé (résolution par requête) |
| `decompiledSource` | moteur figé | sonde des moteurs **vivants** : défaut d'abord, puis modules (motif ADR 0215 du vendored) |
| libération | — | le serveur **ne dispose jamais** l'environnement — l'hôte le possède et le libère à la fermeture de la session (miroir exact du vendored `JavaLanguageServer`) |

L'invariant ADR 0211 (« nourrissage moteur unique par sync ») tient par
construction : l'hôte nourrit l'environnement, le serveur route — la
garde d'acceptation est la fiche 066 du cahier (routage, reconfiguration
à chaud, session unique à travers N syncs, diagnostics routés, sonde des
moteurs vivants).

### 4.4 Le régime vivant — intégration hôte (multi-modules à chaud)

Pour l'intégration CodeIDE (`:lsp:host`, docs/`INTEGRATION-CODEIDE.md`
§3.3 option B), le serveur se construit sur un **environnement vivant**
possédé par l'HÔTE au lieu de la fabrique figée :

```kotlin
import jo.codeide.lsp.java.EnvironnementCompilation
import jo.codeide.lsp.java.protocole.ServeurLangageJava

val environnement = EnvironnementCompilation(jdkHome = jdkDuProjet)
environnement.routeurFichierModule = { chemin ->
    // l'instantané COURANT du modèle (relu à CHAQUE requête, fiche 047)
    modele?.associer(chemin)?.nom
}
environnement.remplacerContexteModule(":app", racinesDeApp, jarsDeApp)
environnement.setIndexNoms(adaptateurIndex)

// UNE session LSP Java par projet — le moteur naît PAR REQUÊTE
// (routage fichier → module), les syncs remplacent les CONTEXTES à chaud.
val serveur = ServeurLangageJava(environnement) { /* rappel d'arrêt */ }
```

Deux régimes, deux constructeurs :

| | régime FIGÉ (fabrique, §4.1) | régime VIVANT (environnement) |
|---|---|---|
| moteur | naît de la racine à `initialize`, ne bouge plus | résolu **par requête** par le routeur fichier → module (fiche 047, relu à chaque requête) |
| reconfiguration | — (reconnexion requise) | `remplacerContexteModule` **à chaud** (sémantiques replaceAll fiche 034 : effectif = moteur du module reconstruit, redondant = no-op) — jamais de re-initialize |
| diagnostics | moteur figé | moteur du module résolu **au tir** (fenêtre du debounce) |
| renommage | moteur figé | moteur du document renommé (résolution par requête) |
| `decompiledSource` | moteur figé | sonde des moteurs **vivants** : défaut d'abord, puis modules (motif ADR 0215 du vendored) |
| libération | — | le serveur **ne dispose jamais** l'environnement — l'hôte le possède et le libère à la fermeture de la session (miroir exact du vendored `JavaLanguageServer`) |

L'invariant ADR 0211 (« nourrissage moteur unique par sync ») tient par
construction : l'hôte nourrit l'environnement, le serveur route — la
garde d'acceptation est la fiche 066 du cahier (routage par module,
reconfiguration à chaud sans re-initialize, session unique à travers N
syncs, diagnostics routés, sonde des moteurs vivants).

## 5. Cycle de vie et gardes protocolaires

La machine à états (fiche 055) :

```
UNINITIALIZED ──initialize──> INITIALIZING ──initialized──> INITIALIZED
INITIALIZED ──shutdown──> SHUTDOWN ──exit──> EXITED
```

- `INITIALIZING` est franchi **après la réponse** de `initialize`
  (le calcul de la réponse se fait encore dans l'état précédent) ;
- le mode par défaut est **in-process** : `exit()` ne tue JAMAIS la JVM —
  l'intégration Android et les tests l'exigent ; `setStandaloneMode(true)`
  restaure le comportement processus (`System.exit`) ;
- **I2** — toute requête AVANT `initialized` est rejetée
  **synchrone** : `ServerNotInitialized` (**-32002**) ;
- **I2** — toute requête APRÈS `shutdown` : `InvalidRequest`
  (**-32600**) ;
- **I3** — un second `initialize` : `InvalidRequest` (**-32600**), le
  message contient « initialize » ;
- les **notifications hors fenêtre** (avant `initialize` comme après
  `shutdown`) sont **ignorées** silencieusement — jamais rejetées (une
  notification n'a pas de réponse d'erreur : la spec demande d'ignorer) ;
- **I8** — `textDocument/rename` sans symbole renommable : erreur typée
  `RequestFailed` (**-32803**), message français « Aucun symbole
  renommable », `data` structuré (`renommage.sansCible`) — jamais un
  `WorkspaceEdit` vide silencieux ;
- un **rappel d'arrêt** optionnel (`rappelArret` du constructeur) est
  déclenché à `shutdown` PUIS confirmé à `exit` — 0 appel après
  initialize, 1 après shutdown, 2 après le cycle complet (registre des
  serveurs vivants, fiche 056 S1-d).

Exemple d'une session complète au fil :

```json
{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"rootUri":"file:///projet/demo","capabilities":{}}}
{"jsonrpc":"2.0","id":1,"result":{"capabilities":{ … },"serverInfo":{"name":"lspjava","version":"1.1.4"}}}
{"jsonrpc":"2.0","method":"initialized","params":{}}
{"jsonrpc":"2.0","id":2,"method":"textDocument/hover","params":{ … }}
{"jsonrpc":"2.0","id":2,"result":{ … }}
{"jsonrpc":"2.0","id":3,"method":"shutdown"}
{"jsonrpc":"2.0","id":3,"result":null}
{"jsonrpc":"2.0","method":"exit"}
```

## 6. Synchronisation des documents

- **Plein texte** (`TextDocumentSyncKind.Full`) : chaque `didChange`
  transporte le texte COMPLET du tampon — le serveur maintient un magasin
  d'états par URI (`EtatsDocuments`).
- **Époques copy-on-write** : chaque mutation incrément une époque et
  publie un instantané **immuable** ; les requêtes vérifient la fraîcheur
  de l'instantané qu'elles ont lu et **recalculent sur le neuf** (borné) —
  « 0 réponse périmée » : une réponse calculée contre un texte révolu
  n'est jamais livrée.
- **`didSave` sans `includeText`** : la publication repart du **buffer
  vivant** version courante (I5) — pas d'un fichier disque potentiellement
  divergent.
- **`didClose`** ferme le document ; les requêtes suivantes sur cette URI
  reçoivent la convention honnête (listes vides / null — §13).

Exemple :

```json
{"jsonrpc":"2.0","method":"textDocument/didOpen","params":{
  "textDocument":{"uri":"file:///projet/demo/src/demo/Demo.java",
                  "languageId":"java","version":1,
                  "text":"package demo;\n\npublic class Demo {\n    int compteur = 0;\n}\n"}}}
{"jsonrpc":"2.0","method":"textDocument/didChange","params":{
  "textDocument":{"uri":"file:///projet/demo/src/demo/Demo.java","version":2},
  "contentChanges":[{"text":"package demo;\n\npublic class Demo {\n    int compteur = 1;\n}\n"}]}}
```

## 7. Fonctionnalités, avec exemples

Chaque sous-section : le comportement verrouillé par le cahier, un
exemple de requête JSON-RPC et un exemple de réponse réaliste (les
positions sont en encodage **UTF-16**, lignes/caractères zéro-basés).
Sauf mention contraire, le document ouvert est — l'import `List` y est
délibérément inutilisé (il sert l'exemple de diagnostic §7.5) et la
locale `message` est déclarée `var` (elle sert l'exemple inlay §7.4) :

```java
package demo;

import java.util.List;

public class Demo {
    private int compteur = 0;

    /** Salue quelqu'un. */
    public String greet(String who) {
        return "Bonjour " + who;
    }

    public void run() {
        var message = greet("monde");
        compteur = compteur + 1;
    }
}
```

Correspondance lignes/colonnes utile aux exemples : `import` en ligne 2
(`java.util.List` aux colonnes 7–20) ; `Demo` en ligne 4 (colonnes
13–17) ; `compteur` déclaré ligne 5 (colonnes 16–24) ; `greet` déclaré
ligne 8 (colonnes 18–23) ; `run` ligne 12 (colonnes 16–19) ; ligne 13 :
`var message = greet("monde");` — `message` aux colonnes 12–19, `greet`
aux colonnes 22–27, `"monde"` aux colonnes 28–34 ; ligne 14 :
`compteur = compteur + 1;` — occurrences aux colonnes 8–16 et 19–27.

### 7.1 Complétion & classement — `textDocument/completion`

Le pipeline complet (étage N2) : analyse de contexte, collecte par genre
(membres de toute la hiérarchie jusqu'à `Object`, types visibles +
classpath par préfixe avec auto-import, stubs d'override, constantes
d'enum, mots-clés), filtrage gradué (le préfixe domine, bonus −200 ;
sous-chaîne admise à partir de 3 caractères), chaîne de peseurs D8
(correspondance → nature par contexte → longueur), **gel des têtes** et
**plafond honnête de 120 items** (le total réel disponible reste
communicable par l'API moteur — `ReponseCompletion.totalDisponible`).
Le serveur ne rend **jamais null** et ne lève jamais : un contexte non
compris rend une liste vide honnête.

Requête — l'utilisateur vient de taper `message.` sur une nouvelle ligne
sous `compteur = compteur + 1;` (tampon en cours d'édition, ligne 15 :
`        message.`, le caret juste après le point) :

```json
{"jsonrpc":"2.0","id":10,"method":"textDocument/completion","params":{
  "textDocument":{"uri":"file:///projet/demo/src/demo/Demo.java"},
  "position":{"line":15,"character":8},
  "context":{"triggerKind":2,"triggerCharacter":"."}}}
```

Réponse (extrait — `filterText` = `label` sur CHAQUE item, I12 ;
`data` = payload de classement §8) :

```json
{"jsonrpc":"2.0","id":10,"result":{
  "isIncomplete":false,
  "items":[
    {"label":"length","kind":5,"detail":"int","filterText":"length",
     "insertText":"length",
     "data":{"x-codeide.poids":-198,"x-codeide.correspondance":{"prefixe":"","longueurMinSousChaine":3}}},
    {"label":"isEmpty","kind":2,"detail":"boolean","filterText":"isEmpty",
     "insertText":"isEmpty()",
     "data":{"x-codeide.poids":-6,"x-codeide.correspondance":{"prefixe":"","longueurMinSousChaine":3}}},
    {"label":"chars","kind":2,"detail":"java.util.stream.IntStream","filterText":"chars",
     "insertText":"chars()",
     "data":{"x-codeide.poids":-4,"x-codeide.correspondance":{"prefixe":"","longueurMinSousChaine":3}}}
  ]}}
```

Particularités verrouillées :

- le déclencheur configuré est **`.`** (`triggerCharacters`) ;
- les **kinds LSP** respectent la nature (méthode = 2, champ = 5, type =
  7, mot-clé = 14…) — fiche 050 ;
- un item d'**auto-import** porte `additionalTextEdits` (l'insertion
  d'import) et son `detail` est le FQCN — fiche 053 ;
- les **gabarits vivants** (`sout`, `soutv`, `fori`, `try`…) ne sont
  proposés qu'en position d'**instruction** (jamais en expression) et
  voyagent en `insertTextFormat: "snippet"` avec pertinence 2500 (clé
  exacte) ou 100 (clé partielle) — fiche 029 ;
- les items de type annotation portent le marqueur `annotation`
  (badge « @ » côté client, fiche 050) ;
- `resolveCompletionItem` est un passe-plat (l'item est déjà complet).

### 7.2 Survol — `textDocument/hover`

Hover riche (étage N3) : signature + documentation Javadoc rendue
fidèlement (fiche 021), sur déclarations COMME sur références (fiche
020). Convention I9 : **hover `null`** hors de tout nœud résolu (jamais
un contenu vide).

Requête — caret sur `greet` (la référence, ligne 13) :

```json
{"jsonrpc":"2.0","id":11,"method":"textDocument/hover","params":{
  "textDocument":{"uri":"file:///projet/demo/src/demo/Demo.java"},
  "position":{"line":13,"character":24}}}
```

Réponse (conventions fiche 057 : MARKDOWN `« ```java\n » + signature +
javadoc »` quand la DÉCLARATION a une javadoc — seules les déclarations
de la même unité l'apportent ; PLAINTEXT signature seule sinon ; **pas de
plage** rendue) :

```json
{"jsonrpc":"2.0","id":11,"result":{
  "contents":{"kind":"markdown","value":"```java\nString greet(String)\n```\nSalue quelqu'un."}}}
```

### 7.3 Aide de signature — `textDocument/signatureHelp`

Déclencheurs `(` et `,` (étage N3/N1) : candidats de signature avec le
résolu en tête, `activeSignature` et `activeParameter` exacts (fiche
023). Caret entre les parenthèses de `greet("monde")` (ligne 13) :

```json
{"jsonrpc":"2.0","id":12,"method":"textDocument/signatureHelp","params":{
  "textDocument":{"uri":"file:///projet/demo/src/demo/Demo.java"},
  "position":{"line":13,"character":28}}}
```

Réponse :

```json
{"jsonrpc":"2.0","id":12,"result":{
  "signatures":[
    {"label":"String greet(String who)","parameters":[{"label":"who"}]}
  ],
  "activeSignature":0,
  "activeParameter":0}}
```

### 7.4 Indices inlay — `textDocument/inlayHint`

Indices inlay (étage N3) : « : Type » collé en fin de nom des
déclarations `var` (fiche 030 — le type RÉEL lié, nom court ; un `var`
textuel dans une chaîne littérale ne porte rien, l'analyse est
structurelle), « nom: » au début de chaque argument d'appel à une
méthode déclarée en SOURCE (fiche 003 — les BINAIRES restent muettes,
jamais de faux noms), types des paramètres lambda implicites et
frontières de chaînages multi-lignes (fiche 002). La liste est
DELIBÉRÉMENT non triée (paramètres avant types — le tri par colonne
relève du client). Convention honnête : liste VIDE quand le document
n'appelle aucun indice (fiche 059 I9).

Requête sur la plage du document :

```json
{"jsonrpc":"2.0","id":13,"method":"textDocument/inlayHint","params":{
  "textDocument":{"uri":"file:///projet/demo/src/demo/Demo.java"},
  "range":{"start":{"line":0,"character":0},"end":{"line":16,"character":1}}}}
```

Réponse (le paramètre `who:` ancré au début de l'argument, le type
`: String` collé à la fin du nom `message`) :

```json
{"jsonrpc":"2.0","id":13,"result":[
  {"position":{"line":13,"character":28},"label":"who:","kind":2},
  {"position":{"line":13,"character":19},"label":": String","kind":1}
]}
```

### 7.5 Diagnostics — `textDocument/publishDiagnostics` (publication)

Les diagnostics sont **poussés** par le serveur (jamais tirés).
Publication **asynchrone débouncée** : 50 ms par URI, fil unique, contenu
calculé à l'instant du tir sur l'instantané vivant — publication sous
15 s par construction (fiche 056, S1 riche). Le diagnose complet passe
**analytique** (PontEcj) : l'import inutilisé est un `warning` avec le
tag `Unnecessary` et le code ecj exact `ecj:<IProblem.UnusedImport>`
(fiche 056). Le **chemin rapide** à corps masqués (frappe confinée)
garde ses familles éteintes (fiche 014 — zéro régression) ; ses signaux
analytiques hors corps (import/membre privé inutilisé) sont **portés** du
dernier diagnose complet et **gabarités par le témoin** (vague V-A,
ADR 0025 — règles mesurées sur ecj 3.40) : pendant une frappe
syntaxiquement incomplète, l'import inutilisé n'est publié que si l'unité
est sans AUCUNE erreur, le membre privé inutilisé que si elle est sans
erreur de SYNTAXE — exactement ce qu'un diagnose complet de la même
source publierait (propriété P1 : équivalence rapide/complet). Le
**mémo** de diagnose (republish post-debounce) n'est servi que si les
fichiers sources du module lus pendant la passe sont **inchangés sur
disque** (empreintes STAT mtime+taille — P8/ADR 0025) : une édition de
l'API d'un fichier voisin invalide le résultat périmé au prochain
diagnostic demandé.

Les imports `.databinding` non résolus d'un module sans classes générées
sont **déclassés en HINT** par la couche de publication (« le remède est
un Build », fiche 051) — avec coupure honnête dès qu'une classe générée
existe.

Exemple de publication reçue par le client (l'import `java.util.List`
est inutilisé — l'ID ecj exact d'`IProblem.UnusedImport` est transporté,
le tag `Unnecessary` est posé) :

```json
{"jsonrpc":"2.0","method":"textDocument/publishDiagnostics","params":{
  "uri":"file:///projet/demo/src/demo/Demo.java",
  "diagnostics":[
    {"range":{"start":{"line":2,"character":7},"end":{"line":2,"character":20}},
     "severity":2,"code":"ecj:268435844",
     "source":"ecj","tags":[1],
     "message":"The value of the import java.util.List is not used"}
  ]}}
```

> Après `didSave` **sans** `includeText`, la publication repart du buffer
> vivant (I5) — cf. §6.

### 7.6 Navigation — définition, type de définition, implémentations

Le routeur GO TO complet (étage N4) : intra-fichier → projet → binaire
`jdt://decompiled/<fqcn>` (fiche 009), avec cible **exactement 1** par
le verdict du cahier (fiches 049/060 : « 1 cible exactement »).

- `textDocument/definition` — caret sur `greet` (la référence, ligne 13) :

```json
{"jsonrpc":"2.0","id":14,"method":"textDocument/definition","params":{
  "textDocument":{"uri":"file:///projet/demo/src/demo/Demo.java"},
  "position":{"line":13,"character":24}}}
```

```json
{"jsonrpc":"2.0","id":14,"result":[
  {"uri":"file:///projet/demo/src/demo/Demo.java",
   "range":{"start":{"line":8,"character":18},"end":{"line":8,"character":23}}}
]}
```

- `textDocument/typeDefinition` (fiche 062) — caret sur `message` rend la
  définition de `java.lang.String` (binaire → `jdt://decompiled/`).
- `textDocument/implementation` (croix-fichiers) — caret sur le nom d'une
  interface rend chaque implémentation du projet.
- `superDefinition` (expérimental, §9) — le « GO TO Super » d'une
  redéfinition, cibles à champ `path`, libellés picker
  « nom  ·  conteneur ».
- `decompiledSource` (expérimental, §9) — le tampon lecture-seule d'un
  FQCN binaire (source attachée ou stub).

### 7.7 Références & surbrillance

- `textDocument/references` (fiche 054, **amendée D1** — vague V-E, ADR
  0028) : usages **MULTI-FICHIERS réels** — exhaustifs sur les racines du
  projet par clé de binding (homonymes départagés par propriétaire et
  arité, jamais par le texte), ordre trié (chemin, offset). La parité
  stricte §10.4 (réponse confinée au document courant, écart du vendored)
  est **formellement abandonnée** — décision D1 du propriétaire du
  31/10/2026 ; la rupture est annoncée par la capability versionnée
  `x-codeide.usages`. La conversion ligne/colonne utilise le repère du
  FICHIER PORTEUR de chaque usage (texte disque — celui que le moteur a
  analysé — puis overlay ouvert en repli). La vue groupée par fichier
  (rendu arborescent « Find Usages ») est servie par le RPC maison
  `textDocument/usagesGroupes` (§9.3).

```json
{"jsonrpc":"2.0","id":15,"method":"textDocument/references","params":{
  "textDocument":{"uri":"file:///projet/demo/src/demo/Demo.java"},
  "position":{"line":8,"character":20},
  "context":{"includeDeclaration":false}}}
```

```json
{"jsonrpc":"2.0","id":15,"result":[
  {"uri":"file:///projet/demo/src/demo/Demo.java",
   "range":{"start":{"line":13,"character":22},"end":{"line":13,"character":27}}},
  {"uri":"file:///projet/demo/src/demo/Consommateur.java",
   "range":{"start":{"line":41,"character":14},"end":{"line":41,"character":19}}}
]}
```

- `textDocument/documentHighlight` : occurrences exhaustives **dans le
  document courant** par clé de binding (homonymes départagés), typées
  lecture/écriture (vague V-E, G-026) : méthode et type → `Text` (1) ;
  variable → `Write` (3) pour la DÉCLARATION, la cible d'une affectation
  — SIMPLE (`=`) ou COMPOSÉE (`+=`, `-=`, …) — et les incréments
  (`x++`/`++x`) ; `Read` (2) pour le reste (le genre LSP est unique par
  plage : l'écriture domine, la lecture implicite d'une composée relève
  du data flow).

### 7.8 Renommage — `textDocument/prepareRename` + `textDocument/rename`

Rename v2 par phases (étage N5) : identification de cible par bindings
(clé structurelle `kind:paquet.Classe.membre`), occurrences EXHAUSTIVES
multi-fichiers sur l'index de mots (imports, constructeurs et allocations
compris pour un type), court-circuit file-local des locales, collisions
typées AVANT écriture, overrides **opt-in** explicites, preview =
résultat (le serveur n'écrit JAMAIS sur le disque).

`prepareRename` rend le placeholder et la plage EXACTE du jeton (caret au
milieu compris — fiche 061) — ici sur la DÉCLARATION de `compteur`
(ligne 5) :

```json
{"jsonrpc":"2.0","id":16,"method":"textDocument/prepareRename","params":{
  "textDocument":{"uri":"file:///projet/demo/src/demo/Demo.java"},
  "position":{"line":5,"character":20}}}
```

```json
{"jsonrpc":"2.0","id":16,"result":{
  "range":{"start":{"line":5,"character":16},"end":{"line":5,"character":24}},
  "placeholder":"compteur"}}
```

`rename` rend le `WorkspaceEdit` sous la forme **NÉGOCIÉE** : `changes`
(héritée) OU `documentChanges` versionnée au `didOpen` — jamais les
deux (selon `workspace.workspaceEdit` du client) :

```json
{"jsonrpc":"2.0","id":17,"method":"textDocument/rename","params":{
  "textDocument":{"uri":"file:///projet/demo/src/demo/Demo.java"},
  "position":{"line":5,"character":20},
  "newName":"occurrences"}}
```

```json
{"jsonrpc":"2.0","id":17,"result":{
  "documentChanges":[
    {"textDocument":{"uri":"file:///projet/demo/src/demo/Demo.java","version":2},
     "edits":[
       {"range":{"start":{"line":5,"character":16},"end":{"line":5,"character":24}},
        "newText":"occurrences"},
       {"range":{"start":{"line":14,"character":8},"end":{"line":14,"character":16}},
        "newText":"occurrences"},
       {"range":{"start":{"line":14,"character":19},"end":{"line":14,"character":27}},
        "newText":"occurrences"}
     ]}
  ]}}
```

Sur une position sans symbole renommable : erreur **-32803**
`RequestFailed`, message « Aucun symbole renommable », `data`
`{"erreur":"renommage.sansCible", …}` (I8 — §5).

Les **collisions typées** (identifiant invalide, nom pris, masquage,
override non suivi) remontent comme erreurs typées AVANT toute écriture —
l'aperçu est re-résolu par **diagnose différentiel** (ADR 0014).

### 7.9 Actions de code — `textDocument/codeAction`

CodeAction v2 (étage N6) : le modèle **ModCommand** d'IntelliJ greffé en
esprit — un plan déclaratif **sérialisable**, calculé en lecture,
exécuté côté client ; familles de problèmes **ID-first** (dispatch ID ecj
→ famille, indépendant de la `Locale`) ; **1 édition par action,
insertion pure** (fiche 063) ; les quick-fixes de l'étage sont
mono-fichier (le multi-fichiers passe par N5/N8).

Kinds verrouillés (fiche 063) : `quickfix.remove.import`,
`quickfix.semicolon`, `quickfix.remove.unused`,
`quickfix.surround.trycatch`, `quickfix.declare.throws`,
`quickfix.cast`, `quickfix.import.fqn`, `quickfix.create.method`.

Requête — sur le diagnostic d'import inutilisé (§7.5) :

```json
{"jsonrpc":"2.0","id":18,"method":"textDocument/codeAction","params":{
  "textDocument":{"uri":"file:///projet/demo/src/demo/Demo.java"},
  "range":{"start":{"line":2,"character":7},"end":{"line":2,"character":20}},
  "context":{"diagnostics":[
    {"range":{"start":{"line":2,"character":7},"end":{"line":2,"character":20}},
     "severity":2,"code":"ecj:268435844","source":"ecj",
     "message":"The value of the import java.util.List is not used"}]}}}
```

Réponse — l'action « Supprimer l'import inutilisé » :

```json
{"jsonrpc":"2.0","id":18,"result":[
  {"title":"Supprimer l'import inutilisé","kind":"quickfix",
   "diagnostics":[
     {"range":{"start":{"line":2,"character":7},"end":{"line":2,"character":20}},
      "severity":2,"code":"ecj:268435844","source":"ecj",
      "message":"The value of the import java.util.List is not used"}],
   "edit":{"changes":{
     "file:///projet/demo/src/demo/Demo.java":[
       {"range":{"start":{"line":2,"character":0},"end":{"line":3,"character":0}},
        "newText":""}
     ]}}}
]}
```

Sur un appel non résolu, le client reçoit deux familles ensemble
(`quickfix.surround.trycatch` ET `quickfix.declare.throws`), et sur un
appel de méthode inconnu, `quickfix.create.method` propose la création
de méthode (« 0 UndefinedMethod après correction », fiche 064).

### 7.10 Formatage — document, plage, à la frappe

Formateur clean-room (étage N7, fiches 016/017) : fidélité sur plages,
styles `ECLIPSE` (4 espaces, repli 120 colonnes) et `GOOGLE` (2 espaces),
cible de formatage à la frappe calculée par chaînes de sélection
(fiche 027 mixte). Trois requêtes :

- `textDocument/formatting` — le document ENTIER (fiche 054) :

```json
{"jsonrpc":"2.0","id":19,"method":"textDocument/formatting","params":{
  "textDocument":{"uri":"file:///projet/demo/src/demo/Demo.java"},
  "options":{"tabSize":4,"insertSpaces":true}}}
```

- `textDocument/rangeFormatting` — une plage ;
- `textDocument/onTypeFormatting` — déclenché par `}` PUIS `;`
  (déclencheurs exacts annoncés, fiche 058) — l'utilisateur vient de
taper le `}` fermant `run()` (ligne 15) :

```json
{"jsonrpc":"2.0","id":20,"method":"textDocument/onTypeFormatting","params":{
  "textDocument":{"uri":"file:///projet/demo/src/demo/Demo.java"},
  "position":{"line":15,"character":4},
  "ch":"}",
  "options":{"tabSize":4,"insertSpaces":true}}}
```

La réponse est une liste de `TextEdit` d'indentation/plages — le
formateur ne réécrit jamais hors de la cible calculée.

### 7.11 Pliage — `textDocument/foldingRange`

Paires de pliage exactes (étage N7, fiches 011/052) : classes, méthodes,
import, commentaires… Chaque pli rend une `FoldingRange` ligne début/fin :

```json
{"jsonrpc":"2.0","id":21,"method":"textDocument/foldingRange","params":{
  "textDocument":{"uri":"file:///projet/demo/src/demo/Demo.java"}}}
```

```json
{"jsonrpc":"2.0","id":21,"result":[
  {"startLine":2,"endLine":2,"kind":"imports"},
  {"startLine":7,"endLine":7,"kind":"comment"},
  {"startLine":4,"endLine":16,"kind":"class"},
  {"startLine":8,"endLine":10,"kind":"method"},
  {"startLine":12,"endLine":15,"kind":"method"}
]}
```

### 7.12 Symboles — document & espace de travail

- `textDocument/documentSymbol` (étage N8) : l'arbre des symboles du
  document, la plage portée sur le **NOM déclarant** (pas toute la
  déclaration) :

```json
{"jsonrpc":"2.0","id":22,"method":"textDocument/documentSymbol","params":{
  "textDocument":{"uri":"file:///projet/demo/src/demo/Demo.java"}}}
```

```json
{"jsonrpc":"2.0","id":22,"result":[
  {"name":"Demo","detail":"demo.Demo","kind":5,
   "range":{"start":{"line":4,"character":0},"end":{"line":16,"character":1}},
   "selectionRange":{"start":{"line":4,"character":13},"end":{"line":4,"character":17}},
   "children":[
     {"name":"compteur","kind":8,"range":{"start":{"line":5,"character":4},"end":{"line":5,"character":29}},
      "selectionRange":{"start":{"line":5,"character":16},"end":{"line":5,"character":24}}},
     {"name":"greet(String)","detail":"String","kind":6,"range":{"start":{"line":8,"character":4},"end":{"line":10,"character":5}},
      "selectionRange":{"start":{"line":8,"character":18},"end":{"line":8,"character":23}}},
     {"name":"run()","kind":6,"range":{"start":{"line":12,"character":4},"end":{"line":15,"character":5}},
      "selectionRange":{"start":{"line":12,"character":16},"end":{"line":12,"character":19}}}
   ]}
]}
```

- `workspace/symbol` (fiche 056) : recherche par préfixe sur les symboles
  des sources ouvertes + racines serveur — la forme booléenne TRUE est
  annoncée :

```json
{"jsonrpc":"2.0","id":23,"method":"workspace/symbol","params":{"query":"Gre"}}
```

### 7.13 Lentilles de code — `textDocument/codeLens` + `codeLens/resolve`

Lentilles à **resolve paresseux** (fiche 058) : la requête initiale rend
des lentilles brutes, `codeLens/resolve` achève le comptage. Le comptage
« 0/1 référence » utilise le balayage **overlay + disque** (index de
mots), déclaration EXCLUE (fiche 058).

```json
{"jsonrpc":"2.0","id":24,"method":"textDocument/codeLens","params":{
  "textDocument":{"uri":"file:///projet/demo/src/demo/Demo.java"}}}
```

```json
{"jsonrpc":"2.0","id":24,"result":[
  {"range":{"start":{"line":8,"character":18},"end":{"line":8,"character":23}}},
  {"range":{"start":{"line":12,"character":16},"end":{"line":12,"character":19}}}
]}
```

Puis :

```json
{"jsonrpc":"2.0","id":25,"method":"codeLens/resolve","params":{
  "range":{"start":{"line":8,"character":18},"end":{"line":8,"character":23}}}}
```

```json
{"jsonrpc":"2.0","id":25,"result":{
  "range":{"start":{"line":8,"character":18},"end":{"line":8,"character":23}},
  "command":{"title":"1 référence","command":""}}}
```

### 7.14 Hiérarchies de types et d'appels (LSP 3.17)

Quatre temps par hiérarchie (étage N7, fiche 018 ; étage N8 pour le
branchement protocole) : `prepare` au caret, puis supertypes/subtypes
(types) ou incoming/outgoing (appels). L'englobant des appels porte son
`contextType`, les cibles sont **localisées dans leur source de
DÉCLARATION**.

```json
{"jsonrpc":"2.0","id":26,"method":"textDocument/prepareTypeHierarchy","params":{
  "textDocument":{"uri":"file:///projet/demo/src/demo/Demo.java"},
  "position":{"line":4,"character":13}}}
```

```json
{"jsonrpc":"2.0","id":26,"result":[
  {"name":"Demo","kind":5,"uri":"file:///projet/demo/src/demo/Demo.java",
   "range":{"start":{"line":4,"character":0},"end":{"line":16,"character":1}},
   "selectionRange":{"start":{"line":4,"character":13},"end":{"line":4,"character":17}}}
]}
```

Puis `typeHierarchy/supertypes` (rend `java.lang.Object` pour une classe
sans superclasse) / `typeHierarchy/subtypes`, et côté appels :
`prepareCallHierarchy` sur `greet`, puis `callHierarchy/incomingCalls`
(les appelants — `run` dans notre exemple) / `callHierarchy/outgoingCalls`.

### 7.15 Chaînes de sélection — `textDocument/selectionRange`

Chaînes strictement croissantes (fiche 027/058) : chaque maillon englobe
le précédent — du token vers l'expression, le bloc, la méthode, le type.
C'est aussi la base de la **cible de formatage à la frappe** (§7.10).

```json
{"jsonrpc":"2.0","id":27,"method":"textDocument/selectionRange","params":{
  "textDocument":{"uri":"file:///projet/demo/src/demo/Demo.java"},
  "positions":[{"line":13,"character":24}]}}
```

```json
{"jsonrpc":"2.0","id":27,"result":[
  {"range":{"start":{"line":13,"character":22},"end":{"line":13,"character":27}},
   "parent":{"range":{"start":{"line":13,"character":22},"end":{"line":13,"character":35}},
     "parent":{"range":{"start":{"line":13,"character":8},"end":{"line":13,"character":35}}}}}
]}
```

(du jeton `greet` vers l'appel `greet("monde")` vers la déclaration
`var message = greet("monde");` — chaque maillon englobe strictement le
précédent.)

### 7.16 `textDocument/semanticTokens/full` — coloration sémantique (vague V-G)

Annoncé depuis la vague V-G (ADR 0031 (a) — la capability voyage avec sa
**légende**, annoncée ⇔ testée) : le calcul parcourt l'arbre **RÉSOLU**
du document (résolution complète, jamais masquée — la coloration porte
sur TOUT le fichier) et rend le vecteur **encodé en deltas relatifs**
(spécification LSP 3.17 : 5 entiers par jeton — delta de ligne, delta de
colonne, longueur, index du type dans la légende, bitfield des
modificateurs). Légende des types : `class`, `interface`, `enum`,
`decorator` (annotations), `method` (déclarations, invocations et
**constructeurs** — miroir V-D2), `property` (champs), `parameter`,
`variable` (locales) ; modificateurs : `declaration`, `static`, `final`,
`abstract`.

```json
{"jsonrpc":"2.0","id":28,"result":{"data":[0,6,5,0,1,0,8,7,4,0,…]}}
```

**Pas de delta serveur** (borne honnête documentée) : `range` n'est pas
servi, le client re-demande le vecteur complet à chaque édition — jamais
un delta faux. Document absent ou moteur introuvable : `data` vide
(I9 — jamais null, jamais d'exception). Latence mesurée au banc : p95
**1 ms** sur le cas doré JVM (cible ADR 0031 : < 25 ms — §12).

### 7.17 Organisation des imports (API moteur)

L'organisateur d'imports (étage N7, fiche 022) — identification des FQN
à importer depuis les **bindings**, règles d'exemption (`java.lang`,
même paquet, paquet couvert par un on-demand existant), remplacement du
bloc existant par un bloc **trié** — est servi par l'API moteur
(`MoteurJava.organiserImports`, §10) ET, depuis la vague V-D tranche 1,
comme action `source.organizeImports` au fil LSP (§7.22). Exemple
d'emploi en Kotlin :

```kotlin
val proposition = moteur.organiserImports(chemin, source)
proposition?.let {
    // it.fqcn : les imports triés à écrire
    // it.bloc : le bloc « import x.y.Z;\n… » PRÊT À ÉCRIRE
    // it.debutRemplacement / it.finRemplacement : les offsets du bloc
    // existant à remplacer dans la source
}
```

### 7.18 Projet Android — classes light, référentiel, interop Kotlin (vague V-B)

Le moteur connaît le projet Android **sans Gradle ni build** (ADR
0020/0021/0022, décisions D2/D3/D6) :

- **Contrat Android** (`ContratAndroidMoteur`) : deux producteurs, un
  contrat. L'hôte pose celui de la sync (surcharge
  `EnvironnementCompilation.remplacerContexteModule(…, projetAndroid)` —
  la sync PRIME) ; sinon le moteur le **dérive du disque**
  (`build.gradle(.kts)` KTS/Groovy tolérant + manifest + `res/` par
  convention). Correspondance des champs avec `ModuleLsp.android` du
  modèle v11 : `namespace`, `applicationId`, `minSdk`, `targetSdk`,
  `compileSdk`, `dossiersRes`, `manifest`, `viewBinding`, `dataBinding`,
  `buildConfig`, `nonTransitiveRClass`, `nonFinalResIds`, `resValues`,
  `buildConfigFields`.
- **Classes light** (ordre de repli : sources → synthétiques → jars →
  jrt → stubs ; la source générée réelle PRIME toujours) : `R` +
  imbriquées, `BuildConfig` (si `buildFeatures.buildConfig`),
  `<ns>.databinding.*Binding` + `BR` — résolus, complétés, décompilables
  (aperçu API) et navigables vers le XML déclarant (`R.layout.x`,
  `R.id.x`, `R.string.x`, `R.styleable.X_a`, `binding.textTitre` →
  `@+id/text_titre`).
- **MAJ ciblée des ressources** : un `didChange` d'un document `.xml`
  rafraîchit le référentiel du module (re-scan du SEUL fichier) et
  invalide les diagnostics mis en mémoire — les champs de `R` suivent
  l'édition du layout.
- **Interop Kotlin** : les déclarations publiques `.kt` de premier
  niveau (classes/objets/énum) et les facades `*Kt` (fonctions et
  propriétés top-level à signature certaine) résolvent et se complètent
  depuis Java — aperçu honnête (API seule), navigation vers le `.kt`.
- **Drapeaux de repli** (propriétés système) :
  `jo.codeide.lsp.java.projetandroid.derivation.disabled`,
  `jo.codeide.lsp.java.projetandroid.classelegere.disabled`,
  `jo.codeide.lsp.java.interoperabilite.kotlin.disabled`.
- Sondes moteur : `contratAndroid()`, `aClassesLegereAndroid()`,
  `classesLegereAndroid()`, `declarationsKotlin()`,
  `generationRessources()`, `rafraichirFichierXml(chemin)`.
- **Retrait (v1.4.0, décision D6)** : `MoteurJava.declasserDatabinding`
  n'existe plus — l'import `.databinding` RÉSOUT avant build (fiche 051
  amendée « couvert par résolution »).

### 7.19 Double-tampon et époques de l'environnement (vague V-F1)

**Ce que voit le client pendant et après une sync Gradle** (remplacement
effectif de contexte — racines, classpath, niveau ou contrat Android) :

1. **Pendant la reconstruction** (l'appareil y paie 11–12 s, la JVM des
   tests ~0,3 s) : **rien ne se fige**. L'ancien moteur CONTINUE de servir
   toutes les requêtes — c'est l'époque VIVANTE jusqu'au swap ; les
   diagnostics de cette fenêtre reflètent légitimement l'ANCIEN classpath
   (le même comportement que la fenêtre de sync d'Android Studio, sans le
   figage).
2. **Au swap** (atomique, dès que le moteur neuf est construit et
   réchauffé) : les documents OUVERTS du module sont **REPUBLIÉS** sur
   l'époque neuve — les faux « cannot be resolved » s'effacent SANS
   attendre la frappe suivante (le client peut donc recevoir plusieurs
   `publishDiagnostics` par document autour d'une sync : comportement LSP
   standard, la dernière publication fait foi).
3. **Requêtes en vol au moment du swap** : elles sont **REJOUÉES** sur
   l'époque courante (« 0 réponse périmée ») — la réponse livrée reflète
   toujours l'époque vivante au moment de la livraison ; les boucles
   longues (collecte de complétion, balayage d'usages) sont COUPÉES puis
   rejouées plutôt que solvées pour rien (annulation fine coopérative).

**Repli documenté** : la propriété système
`jo.codeide.lsp.java.doubletampon.disabled=true` rétablit le comportement
d'avant V-F1 — dispose immédiat et synchrone du moteur à chaque sync (la
requête suivante paie la reconstruction froide, le figage revient) ; le
protocole d'époque (rejeu des réponses périmées) reste actif dans les DEUX
régimes. Bornes d'honnêteté du rejeu : 3 résultats périmés puis livraison
du dernier (clause miroir des documents), 8 coupures puis erreur franche
sous churn de syncs soutenu.

### 7.20 Concurrence, inspections et lint Android (vagues V-F2/V-C1/V-C2)

**Verrous par périmètre (V-F2 — ADR 0029 phase 2)** : le moniteur
d'instance historique du moteur est remplacé par des verrous NOMMÉS, avec
un ordre global documenté (orchestration → compilation → services →
feuilles). Ce que voit le client :

- la **frappe ne se met plus derrière le travail des autres fichiers** :
  un hit de mémo de diagnose, une complétion CHAUDE (sonde sur l'arbre du
  cache) ou un hover servi depuis le cache répondent PENDANT la diagnose
  d'un autre fichier du module (banc de charge : complétion p95 33 ms
  concurrent vs 43 ms séquentiel) ;
- les **balayages longs** (usages, renommage projet) ne tiennent plus le
  périmètre compilation pendant leurs lectures disque — chaque résolution
  de candidat reprend le verrou individuellement ;
- **deux modules vivent en parallèle complet** (isolation lot E intacte).

Replis documentés : l'ordonnanceur (fiche 048 amendée) est un POOL
plafonné — `jo.codeide.lsp.java.ordonnanceur.workers=1` rétablit le
worker unique d'avant V-F2 (jamais deux tâches du même fichier en vol
dans tous les régimes) ; les verrous par périmètre sont un refactor
conservant la sémantique (aucun drapeau : le repli est le revert du
commit). La métrique CI « comptage `synchronized(` < 6 » est portée par
`VerrousPerimetreTest` (valeur mesurée : 0).

**Inspections (V-C1 — ADR 0023)** : la couche IDE au-dessus du
compilateur — 8 inspections pilotes (StringEquality, IgnoreResultOfCall
`@CheckResult`, RedundantCast, EqualsHashCode appareillés,
SizeReplaceableByIsEmpty, StaticFieldReferenceOnSubclass,
WrongPackageStatement, FormatString). Règle ADR 0013 inchangée : **le
rouge n'a qu'une source (ecj)** — les inspections ajoutent des
Warning/Info, JAMAIS une erreur ; leur provenance est transportée par le
champ `source` du diagnostic (`"inspection"` / `"lint"` — filtrage et
rendu côté hôte, relatedInformation en V-G). Suppressions :
`@SuppressWarnings("id")` (portée = déclaration entière), `//noinspection
id` (ligne courante ou précédente), tokens javac alignés quand ils
existent. Les signaux survivent à la frappe confinée (portage des corps
masqués — équivalence P1 éprouvée). Repli :
`jo.codeide.lsp.java.inspections.disabled=true` (retour au rouge ecj
seul).

**Lint Android (V-C2 — ADR 0024)** : contrôles à valeur Java,
mono-fichier, provenance `"lint"`, Warning LSP par défaut — NewApi
signale du code VALIDE en compilation mais cassé à l'exécution sur
minSdk. Livrés : **NewApi** (avec les échappatoires `@RequiresApi(n)` /
`@TargetApi(n)` et gardes `Build.VERSION.SDK_INT >= n`),
**InlinedApi**, **ObsoleteSdkInt**, **MissingSuperCall** (fix
`super.x();` suggéré dans le message), **SetTextI18n**. Suppression :
`@SuppressLint("NewApi")` (valeurs scalaires ou tableaux). La base
`api-versions.xml` du SDK (≈ 5,5 Mo, jamais committée) se prépare par
`bash scripts/preparer-base-api.sh` et se pose via
`-Djo.codeide.lsp.java.lint.baseapi=<chemin>` ; **repli honnête** : sans
base ou sans contrat Android (minSdk inconnu), les contrôles de niveau
d'API restent MUETS — jamais un faux silence non plus : la présence de
la base est observable (`BaseApiAndroid.disponible()`), et la passe lint
se tait sur une unité portant une erreur de résolution (garde du circuit
annotator de la référence).

### 7.21 Complétion smart, typedef, R.*, second appel, chaîne, statement (vague V-C3)

**Smart — filtrage gradué par type attendu** (ADR 0026 (a), fiches C-03) :
aux positions où un type attendu est calculable par les **bindings ecj**
(affectation, initialiseur, argument d'appel — union des surcharges **à
l'arité de l'appel**, `return`, ternaire, lambda, allocation, cast), les
candidats **type-compatibles remontent**, les autres **descendent** —
**jamais cachés** (la liste plafonnée à 120 et le filtrage par préfixe
restent intacts, le total réel voyage toujours). La dimension rejoint la
chaîne D8 (`PeseurCompatibilite`, 2ᵉ dimension) et le poids RPC packé —
capability **`x-codeide.completionRanking` v2** (§8).

**Typedef `@IntDef`/`@StringDef`** (fiche C-04) : les constantes
AUTORISÉES sont proposées **en tête** aux positions argument annoté,
affectation de champ annoté et label de `switch` — même non importées :
l'élément simple porte une édition `import static …;`, l'élément qualifié
s'insère sous sa forme qualifiée (insertion **compilable**, P6). Les noms
sont lus dans la **source déclarante** : un typedef déclaré dans un type
**binaire** (androidx en AAR) reste muet — les noms ne vivent pas dans le
`.class` (même contrainte que la référence).

**Littéraux `R.*`** (B11) : le caret **dans un littéral de chaîne**
occupant un argument annoté `@…Res` (21 annotations mappées) propose les
**noms de ressources** du référentiel (ADR 0020) — genre
`LITTÉRAL_RESSOURCE`, insertion du nom, détail `R.<type>.<nom>`. Sans
annotation ou sans référentiel : liste vide honnête (comme avant V-C3).

**Second appel** (fiche C-08) : compteur d'invocation par (document,
genre, ancre, **préfixe**) côté serveur — un changement de préfixe **rouvre
le contexte**. **2ᵉ appel** : les membres **inaccessibles** (JLS 6.6 —
éprouvés dès le 1ᵉʳ appel, ils n'y figurent plus) s'ajoutent **marqués**
(`data.marqueur = "inaccessible"`) et **derrière** les accessibles ;
les membres **statiques importables** (imports explicits + paquet courant +
panneau java.lang borné, énumération mémoïsée) portent une édition
`import static …;`. **3ᵉ appel** : types du **projet entier** par l'index
de noms partagé (CIDX), import automatique — moteur sans index : rien
d'ajouté (honnête).

**Chaîne** (ADR 0026 (b)) : quand AUCUN candidat direct type-compatible ne
survit au préfixe, les méthodes du récepteur **retournant le type attendu
en deux sauts** sont proposées (`w.builder().text()`) — budgets bornés
(40 méthodes au premier niveau, 12 items).

**Statement completion** (ADR 0026 (c) — le geste Ctrl+Shift+Enter) :
`MoteurJava.completerInstruction` et l'extension expérimentale
`textDocument/statementCompletion` (§9.4) équilibrent l'instruction au
caret — littéral ouvert refermé, listes refermées, argument de secours
après virgule traînante, `;` final (ou bloc ` {` d'un mot-clé de
contrôle). **Purement lexicale** : servie dans tous les régimes, aucun
verrou ; rien à compléter → null (jamais d'édition vide).

**Stats locales opt-in** (G-018) : `CompteurUsage` à **fenêtre
glissante** (30 jours, 16 horodatages par libellé, horloge injectée,
fichier local purgable — l'ancien format `label=fréquence` est ignoré au
chargement), branché en peseur D9 de la chaîne standard : passer le
compteur à la construction du moteur (`null` par défaut — vie privée) et
noter les sélections par `MoteurJava.noterSelectionCompletion`. Le ML
local reste **rejeté** en v1 (ADR 0026 : gain/cost défavorable sans logs
massifs).

**Mémoïsation des membres binaires** (reste consigné du PV 1.6.0) : les
membres des types **jrt/jar** se mémoïsent par identité de binding (les
sources bougent à l'édition, elles ne s'y mémoïsent pas) ; les niveaux
d'accès sont consignés dans le mémo — l'épreuve d'accessibilité reste par
contexte. Verrou d'état **nommé** (le comptage `synchronized(` du module
reste 0, cible ADR 0010 < 6).

**Mots-clés de tête de déclaration** (décision D7 — G-024/M1, l'écart
consigné ECARTS-SERVEUR M1) : `extends`/`implements`/`permits` sont
proposés **pendant leur frappe** en tête de déclaration, **fusionnés**
aux stubs d'override de la hiérarchie du type en cours (fiche 007
amendée). Borne : une tête **locale** (classe déclarée dans un corps de
méthode) reste un artefact de recovery d'ecj (mots-clés non proposés).

### 7.22 Actions de code étendues et complément lint (vagues V-D/V-C2 suite)

**`source.organizeImports` exposé** (M3 — G-020, ADR 0027 (b)) : le fil
`codeAction` sert l'action « Organiser les imports » quand le `only` de
la requête demande `source` ou `source.organizeImports` (égalité ou
**préfixe hiérarchique** LSP 3.17 — `source.generate` ne répond PAS à un
`only` = `source.organizeImports`). L'édition unique remplace la plage
du bloc d'imports par le bloc **trié** de l'API moteur existante (fiche
022) ; `codeActionKinds` annonce désormais `quickfix`, `source`,
`source.organizeImports`, `source.generate`, `refactor`,
`refactor.extract`, `refactor.rewrite`.

**Génération de code — 6 capacités** (G-015, ADR 0027 (d)) : au caret
**sur un champ** → getter (préfixe `is` pour `boolean`), setter (jamais
sur `final`), ou le couple ; au caret **sur le nom d'un type** →
constructeur assignant les champs d'instance, `equals()`/`hashCode()`
(`java.util.Objects` ; `==` sur primitifs, `deepEquals` sur tableaux ;
non servis si déjà déclarés), `toString()` ; dans un type **incomplet**
→ un stub « Implémenter « x(…) » » par méthode abstraite manquante
(`@Override`, valeur de retour par défaut, `// TODO : implémenter`,
liste bornée à 8). Kinds `source.generate.*` ; déclenchement **ciblé**
(miroir de la référence : l'ampoule ne s'allume que là où le geste a du
sens).

**`codeAction/resolve`** (ADR 0027 (f)) : `resolveProvider` annoncé ;
les **générateurs voyagent différés** — la liste ne porte que `data`
(`cle` + `arguments` + `uri`), les éditions se recalculent
**déterministement** au resolve (même source → mêmes éditions). Les
intentions légères restent EAGER.

**Intentions sans diagnostic** (G-014, ADR 0027 (c) — ampoule jaune) :
`if/else` **symétrique** (deux `return`) → ternaire ; littéral au caret
→ **extraction de constante** (`private static final`, nom
`CONSTANTE_42`/`CHAINE_<préfixe>`, refus honnête des noms trop pauvres) ;
lambda à récepteur identitaire (`s -> s.length()`) → **référence de
méthode** (`String::length` — nom simple pour `java.lang`, sinon qualifié
compilable) ; déclaration locale → **`var` ↔ type explicite** (uniquement
si le type déclaré ÉGALE le type résolu de l'initialisateur : un
`Object x = "a"` ne change jamais de sémantique). Kinds
`refactor.rewrite` / `refactor.extract`.

**Familles ecj étendues** (G-014, ADR 0027 (a)) : la table ID→famille
couvre **+7 IDs** (extraits du jar ecj 3.40.0, jamais codés de mémoire) :
`AbstractMethodMustBeImplemented` (et ses deux variantes) → **stubs**
(génération par bindings, arguments structurés du diagnostic) ;
`MethodRequiresBody` → insertion d'un corps qui **jette** (compilable
pour tout type de retour) ; `VoidMethodReturnsValue` → suppression de
l'instruction de retour **entière** ; `ConflictingImport`/`DuplicateImport`
(collision d'imports) → suppression de la ligne ; **cast redondant** → le
signal de l'inspection V-C1 « RedundantCast » (code nommé — alias
`fromCode`, l'ID numérique garde la priorité) porte le retrait du cast.
Le balayage P5 couvre l'ensemble exposé (7 fixtures, 0 violation).

**Complément de catalogue lint** (ADR 0024, G-011) : quatre contrôles de
plus, mono-fichier, Warning LSP, `@SuppressLint`, garde « erreur de
résolution = silence » — **Range** : `@IntRange`/`@FloatRange`/`@Size`
éprouvés sur les arguments **constants** et les retours annotés (la
taille des littéraux de chaîne et le cardinal des initialisateurs pour
`@Size`) ; **ResourceType** : famille réelle `R.<famille>.x` ou nom
annoté `@…Res` contre la famille attendue du paramètre (l'entier
ordinaire n'est jamais signalé) ; **CommitPrefEdits** : `edit()` sans
`commit()`/`apply()` (locales et chaînes nues, évasions silencieuses) ;
**Recycle** : `TypedArray`/`VelocityTracker` obtenus sans `recycle()`
(try-with-resources exempté). Le catalogue passe à **15 contrôles**
(8 inspections + 7 lint).

**Segment compact de la base API** (ADR 0024) : `api-versions.xml` se
compile en segment binaire **CLBA v1** (gzip) —
`scripts/preparer-base-api.sh` appelle `OutilSegmentBaseApi` quand le jar
du moteur est disponible (`java/build/libs/` ou `JAR_BASE_API`) ; le
chargeur lit `<base>.seg` **en priorité** sur le xml (ou la propriété
désigne le `.seg` directement), un segment corrompu **retombe** sur le
xml. Re-compilable **bit-à-bit** (empreintes SHA-256 consignées par
l'outil). Restes : mode global différé (UnusedResources), mesure de la
taille sur la vraie base en CI/appareil.

### 7.23 Refactorings lourds : extractions, inline, safe delete (vague V-D tranche 2)

**Extractions sur SÉLECTION** (ADR 0027 (e) — kinds `refactor.extract.variable`,
`refactor.extract.constant`, `refactor.extract.function`) : une sélection qui
couvre une EXPRESSION sert l'extraction de variable (`Type nom = <expr>;`
insérée au **début de ligne** de l'instruction de bloc porteuse, sélection
remacée par le nom — heuristique de nommage par type : `nombre`, `texte`,
`drapeau`…, suffixe anti-collision contre les locales, les champs et tout
identifiant lu de la méthode), de constante (`private static final` en tête
des membres du type, nom `CONSTANTE_…`/`CHAINE_…`, littéraux et binaires de
littéraux seulement) et de méthode (**l'appel remplace l'expression SUR
PLACE** — l'ordre d'évaluation est préservé, les locales lues deviennent des
paramètres dans l'ordre de première lecture). Une sélection qui couvre
**exactement une plage contiguë d'instructions d'un même bloc** sert
l'extraction de méthode « instructions » (heuristiques §A5 de la référence :
**une sortie au plus**, les locales lues avant toute écriture deviennent des
paramètres, celles écrites avant toute lecture sont **déclarées dans la
méthode extraite**, `return`/`break`/`continue` dans la sélection = refus
honnête). Une sélection paresseuse — opérande de `&&`/`||`, branche de
ternaire, condition de boucle, `assert`, corps de lambda — ne propose PAS
l'extraction de variable (R1 : refus honnête plutôt que transformation
fausse). Tous DIFFÉRÉS : la liste ne porte que clé + positions.

**Inline** (kinds `refactor.inline.variable`, `refactor.inline.constant`) :
au caret sur une locale initialisée — chaque LECTURE est remplacée par
l'initialisateur (parenthésé pour les binaires/ternaires/unaires, lignes
repliées), la déclaration est supprimée ; une locale **réaffectée** ne
s'inline jamais, un initialisateur à effets ne s'inline qu'à usage **unique**
(une lambda qui l'utilise compte comme différée). Au caret sur un champ
`static final` à initialisateur **constant** — les usages du **fichier
courant** sont remplacés par la valeur (référence **ENTIÈRE** pour les
qualifiées : `demo.Main.MAX` devient `42` ; valeur négative parenthésée) ; un
usage hors du fichier ferme la porte honnêtement (le multi-fichiers suit la
tranche 3 avec change signature).

**Safe delete** (kind `refactor.rewrite.delete`) : au caret sur une
déclaration (locale, champ, méthode, constructeur, type). La DÉTECTION des
usages est **multi-fichiers** (socle ADR 0028, exhaustive par clé de
binding) ; la **SUPPRESSION** est mono-fichier (la déclaration vit toujours
dans le fichier courant). L'API moteur `suppressionSure(chemin, source,
offset)` rend le verdict complet — `usagesBloquants` (l'oracle : un usage
ferme la porte), `editions` (null ssi bloqué), `motifRefus` (`override` :
la clé d'usages ne suit pas la hiérarchie — tranche 3 ; `paramètre` :
change signature — tranche 3). Le client LSP voit l'action au caret, la
garde se paie au **resolve** (ADR 0027 (f) : rien d'eager) — un usage
bloquant laisse l'action sans édition (refus honnête), `references` montre
les coupables.

**Socle usages étendu** (V-D2, mesuré) : les sites `new X(…)` comptent pour
la **méthode constructeur** (le binding d'une allocation EST le
`MethodBinding` du constructeur — son sélecteur LISIBLE est le nom du type,
le sélecteur brut `<init>` n'existe pas dans le texte source), les
références de méthode `X::m` comptent pour la **méthode visée**
(`getExactMethod` — les non exactes restent hors correspondance). Le
symbole au caret identifie désormais les **constructeurs** (clé MÉTHODE au
nom du type) et l'égalité d'étendue de la fenêtre « +120 » d'une tête de
méthode laisse passer le **dernier** candidat (mesuré : des méthodes
rapprochées volaient le caret de la suivante). `codeActionKinds` annonce
les formes précises `refactor.extract.variable/constant/function`,
`refactor.inline.variable/constant` et `refactor.rewrite.delete`
(consommables par préfixe hiérarchique).

Restent (tranche 3) : change signature (graphe d'appelants CIDX), rename de
ressource (G-028), création de champ sur UndefinedField, suppression
hiérarchique (override), inline multi-fichiers — **TOUT LIVRÉ en §7.24**.

### 7.24 Change signature, suppression hiérarchique, inline multi-fichiers, création de champ, rename de ressource (vague V-D tranche 3)

**Change signature — suppression de paramètre** (kind
`refactor.rewrite.signature`, DIFFÉRÉ) : au caret sur le nom d'un paramètre
NON utilisé, NON varargs, d'une méthode HORS hiérarchie d'overrides (les
gardes locales, peu coûteuses, se paient à la liste) ; au **resolve**, la
signature perd le paramètre ET le **graphe des appelants** multi-fichiers
perd l'argument au rang — les envois `m(…)`, les sites `new X(…)` et les
appels explicites `this(…)`/`super(…)` (socle ADR 0028, candidats de
l'index de mots). Le re-partage des virgules est exact : le paramètre de
TÊTE emmène sa virgule suivante, le TERMINAL part depuis la fin du
précédent (`m(a, b)` retire `, b`, jamais un `a,` orphelin), l'argument
SEUL part sans virgule (`m()`). L'oracle moteur `changementSignatureSure`
porte le `motifRefus` — `utilise` (lu dans le corps : sa suppression
casserait la compilation), `varargs` (le rang d'un argument ne se sait pas
à l'appel), `hierarchie` (override : la famille hiérarchique suit),
`referenceMethode` (un `X::m` fixe la forme) — le nombre d'appelants et
les éditions PAR FICHIER. Le resolve sert la forme **multi-URI** (map
fichier → éditions, convention `file://` du renommage N5).

**Suppression hiérarchique** (kind `refactor.rewrite.delete`, titre
« Supprimer … et sa hiérarchie », servie au caret d'une méthode qui
OUTREPASSE un supertype) : la chaîne d'overrides **ENTIÈRE** est calculée
au resolve — la outrépassée par **bindings**, les outrépassantes par
**balayage des sources** (fermeture transitive des sous-types). La garde
d'usages est par **maillon** (chacun porte sa clé) : un site d'appel hors
chaîne FERME la porte, les appels INTERNES à la chaîne disparaissent avec
elle. L'oracle `suppressionHierarchiqueSure` rend la chaîne
(`membres` : chemin + bornes), les `bloquants` et les éditions par
fichier. Corollaire (garde V-D3) : le safe delete SIMPLE refuse désormais
`hierarchie` quand un **sous-type** outrepasse la cible (la clé d'usages
ne suit pas la hiérarchie descendante — un maillon orphelin ne compile
pas) ; au caret d'une méthode simplement OUTREPASSÉE, l'action simple est
servie mais son resolve refuse avec le motif, la variante hiérarchique
reste accessible à l'hôte par l'oracle.

**Inline de constante MULTI-FICHIERS** (kind `refactor.inline.constant`,
clé `refactor.mettreEnLigneConstanteProjet`) : la garde mono-fichier de la
tranche 2 est LEVÉE — chaque lecture du champ `static final` à
initialisateur constant, dans **tous les fichiers du projet** (clé de
champ, candidats de l'index de mots), reçoit la valeur (référence
qualifiée remplacée ENTIÈRE, valeur négative parenthésée) ; la
DÉCLARATION quitte son fichier.

**Création de champ sur UndefinedField** (kind `quickfix.create.field`,
DIFFÉRÉ) : au caret d'un diagnostic « The field T.foo is undefined »
(`this.foo = …`, `x.foo` où le type de `x` est résolu), la déclaration
`<visibilité> Type foo = <défaut>;` naît en tête des membres du type
DÉCLARANT — le récepteur peut mener dans un **AUTRE fichier** (le
WorkspaceEdit multi-URI du resolve) ; le TYPE est inféré par **bindings**
de la valeur affectée (`this.foo = 3` → `int`), la VISIBILITÉ suit la
portée (`private` dans le type courant, package/public sinon — un champ
`private` écrit depuis un autre type ne compile pas), la valeur par
défaut est compilable (`false`/`0`/`null`). Récepteur de type BINAIRE
(`android.jar`, jrt) : refus honnête — jamais de champ dans `java.lang`.

**Renommage de ressource** (G-028, `textDocument/rename`) : au caret sur
le segment `<nom>` d'une référence `R.<type>.<nom>` (bindings des classes
light — le type R départage les homonymes : `R.string.x` n'est jamais
`R.layout.x`), le renommage propage dans le **code Java** (chaque
référence, même qualifiée `demo.R.string.x`) et dans les **XML du
module** : la DÉCLARATION (`name="…"` des `values*`, y compris les
configurations `values-fr`…), les RÉFÉRENCES `@<type>/…`,
`@+<type>/…` (déclaration inline des ids), `?<type>/…` (attributs de
thème) et les expressions `R.<type>.<nom>` du **data binding**. Refus
honnêtes : ressource-**FICHIER** (`layout`, `drawable`… — le nom vit dans
le nom du fichier, le rename de fichier appartient au client LSP
`RenameFile`), **styleable** (l'attribut appartient à la structure du
`declare-styleable`). Collisions typées (`conflitsRessource`) :
identifiant invalide (le nom devient un champ Java), nom déjà pris du
MÊME type (le référentiel est la source de vérité). `prepareRename`
sert le placeholder exact ; les formes négociées `changes`/
`documentChanges` couvrent les fichiers Java ET XML.

**Kinds et protocole** : `codeActionKinds` ajoute
`refactor.rewrite.signature` (prolongement hiérarchique de
`refactor.rewrite`, consommable K2 par préfixe) ; `codeAction/resolve`
sert la forme multi-URI — les éditions par fichier deviennent une map
URI → éditions (le document courant sous SON URI, les autres sous
`file://<chemin>`, convention du renommage N5).

### 7.25 V-F finale — budgets d'octets, onTrimMemory, plage prioritaire (vagues V-F3/V-F4)

**Budgets d'octets (V-F3 — ADR 0029 phase 3, exécution de l'ADR 0011
point 2)**. Deux caches gagnent une borne en **octets estimés** (le tas
réel reste l'affaire du banc mémoire, R5) :

- le **cache de parse** (arbres résolus) : coût estimé par entrée =
  source retenue × facteur d'arbre **estimé** (×6, gestion) + enveloppe ;
  budget par défaut **8 Mio** (généreux devant le régime typique 8
  entrées ≈ 800 Ko de l'audit ADR 0011 — il ne mord que sur les fichiers
  GÉANTS) ; éviction **LRU** au-delà du budget, EN PLUS de la borne en
  entrées. **Garde anti-cyclage** : l'entrée la plus récente est
  TOUJOURS retenue — un fichier unique plus gros que le budget vit seul
  jusqu'à la suivante, jamais de put/éviction en boucle ;
- le **mémo de localisation** (verdicts fqcn → jar) : les verdicts
  ne périment jamais par conception (fiche 024) ; budget par défaut
  **2 Mio**, les **NÉGATIFS sont évincés d'abord** (un re-probe honnête
  re-sonde les tables centrales, le verdict rendu reste EXACT), et si le
  budget reste dépassé le mémo est vidé ENTIÈREMENT (rare, documenté).
  Les contrats observables de la fiche 024 sont INCHANGÉS — seul le
  COÛT du re-probe revient.

Kill-switch commun :
`jo.codeide.lsp.java.memoire.budgets.disabled=true` rétablit les bornes
d'avant V-F3 (budget infini).

**Shim `onTrimMemory` (V-F3 — ADR 0029 phase 3, exécution de l'ADR 0011
point 5)**. L'hôte Android relaie
`ComponentCallbacks2.onTrimMemory(level)` vers
`MoteurJava.pressionMemoire(niveau)` **avec le même entier** (les
constants `NiveauxPressionMemoire` sont le miroir exact de la
plateforme — identité, pas traduction) ; le relais multi-modules
`EnvironnementCompilation.pressionMemoire(niveau)` touche TOUS les
moteurs vivants et rend un **rapport chiffré** (`RapportPressionMemoire`
: entrées de parse libérées, mémos de diagnose, négatifs de
localisation, libération intégrale oui/non — journalisable côté hôte).
Politique cumulative :

| Niveaux reçus | Ce qui est libéré |
|---|---|
| `RUNNING_MODERATE/LOW/CRITICAL` (5/10/15), `UI_HIDDEN` (20) | les arbres RÉSOLUS du cache de parse seuls (le mémo de diagnose reste — la frappe reste servie au chemin chaud) |
| `BACKGROUND` (40), `MODERATE` (60) | + les MÉMOS de diagnose et les NÉGATIFS de localisation (les positifs gardent leurs handles) |
| `COMPLETE` (80) | libération INTÉGRALE (`dispose` drainé par le verrou de compilation — le moteur reste utilisable, tout se reconstruit paresseusement) |

Un niveau INCONNU reste servi sans exception (< 20 → traitement doux,
≥ 80 → libération intégrale). Le signal ne paie JAMAIS la construction
de l'environnement de noms (jrt/jars). Kill-switch :
`jo.codeide.lsp.java.memoire.ontrim.disabled=true` rend le shim
inopérant (rapport « rien libéré »).

**Plage prioritaire (V-F4 — ADR 0029 phase 4, « zone visible d'abord »)**.
La notification maison `$/codeide/plageVisible` (§9.5) déclare la zone
de CHAQUE document réellement affichée ; quand une passe à venir serait
COMPLÈTE (sonde `serviraitPasseComplete` — ouverture de grand fichier,
édition structurelle) et que le document dépasse
`MoteurJava.SEUIL_PRIORITAIRE` (16 000 caractères ≈ 500 lignes), le
publicateur publie DEUX fois dans le MÊME tir : la passe **PRIORITAIRE**
d'abord (le corps VISIBLE intact + tout le hors-corps — masquage
isomorphe au diagnose rapide, fiche 014 ; partielle ASSUMÉE), la passe
COMPLÈTE ensuite — **elle fait foi** (fil unique : l'ordre d'arrivée au
client est garanti, le client rend la dernière reçue). La passe
prioritaire est **PURE** : elle n'écrit NI mémo NI cache de parse (un
diagnostic partiel ne devient jamais la source de portage d'une passe
rapide suivante). Refus honnêtes (null → publication complète
directement) : fichier sous le seuil, accolades déséquilibrées, offset
visible hors de tout corps, moins de deux corps, régime non plein. Un
client SANS notification garde le comportement d'avant V-F4 **à
l'identique** (une salve = une publication, contrats du cahier intacts).
Kill-switch : `jo.codeide.lsp.java.plageprioritaire.disabled=true`.

**Incrémentalité (V-F5 — report honnête)** : non livrée — conditionnée à
l'ADR 0017 (b) (artefacts JetBrains java-syntax, décision D4 non
tranchée par le propriétaire) ; le critère utile (« la frappe ne paie
pas la re-résolution complète ») est déjà tenu par le diagnose rapide
(fiche 014 — frappe confinée, p50 mesuré au banc). La V-F est COMPLÈTE
au sens du plan 04 (phases 1–4 livrées, phase 5 reportée motivée).

### 7.26 V-G — surface protocole : progression, diagnostics enrichis, willRenameFiles, resolve réel (vague V-G)

**Progression d'indexation (ADR 0031 (c), écart G-032)**. Juste après
`initialized`, un fil de démon (`lspjava-indexation`)
**préchauffe** l'index de mots du moteur par défaut — la construction
froide est payée au moment le plus creux de la session (aucune requête
en vol ; valeur PROPRE : la première requête d'usages/renommage ne paie
plus le froid). Si le client a annoncé `window.workDoneProgress`, le
serveur ouvre un jeton `codeide-indexation-*` et envoie
`begin`/`report`/`end` — titre « Indexation des sources », pourcentage
RÉEL (les `report` suivent les fichiers effectivement lexés, throttlés
à ~50), `end` TOUJOURS envoyé (le jeton ne fuit jamais, même sur
exception). Sans la capability : préchauffage silencieux (aucun bruit).
Kill-switch : `jo.codeide.lsp.java.progression.disabled=true` coupe le
fil entier.

**Diagnostics enrichis (ADR 0031 (e))**. Chaque diagnostic publié porte
désormais un champ `data` **machine-readable** :
`{"provenance": "ecj" | "inspection" | "lint", "code": "<ID nu>"}` — un
client filtre ou groupe SANS parser le code préfixé humain (miroir du
champ source moteur, ADR 0025). `relatedInformation` : le lien
« requis par X » de l'enrichissement hiérarchique (fiche 019 — la clause
`extends` exigeante d'un supertype absent du classpath) devient une
localisation CLIQUABLE dans le document porteur. `codeDescription.href`
n'est PAS livré (aucun consommateur identifié — écart documenté, la
matrice suit).

**Renommage de fichier (ADR 0031 (d), écart G-022)**.
`workspace/willRenameFiles` (annoncé via
`workspace.fileOperations.willRename` sur `**/*.java`) : le geste
« renommer / déplacer un `.java` » de l'arbre éditeur reçoit, AVANT le
renommage effectif, les éditions qui gardent le projet cohérent — la
**déclaration de paquet** du fichier déplacé (le FQCN change) et les
**imports** de ses porteurs (`import demo.A;`, `import demo.A.Imbrique;`,
`import static demo.A.CHAMP;` — le segment FQCN seul est réécrit). La
source fraîche de chaque porteur : le TAMPON OUVERT si le document
l'est, le DISQUE sinon. Réponse TOUJOURS un `WorkspaceEdit` (vide
honnête si rien à ajuster — renommage conservant le FQCN, fichier hors
racines). Périmètre honnête : les usages qualifiés complets SANS import
(`demo.A a = …`) ne sont PAS réécrits (le cas doré de la décision est
« package + imports »). `didRenameFiles` (annoncé aussi) invalide la
présence source et l'index de mots — le FQCN a changé.

**`completionItem/resolve` réel (ADR 0031 (f), écart G-021)**. Le serveur
annonce `resolveProvider: true`. La négociation décide : SI le client
annonce `textDocument.completion.completionItem.resolveSupport`, les
items à auto-import perdent leur édition d'import à la liste (les
snippets restent) et portent `data.importFqn`/`importStatique`/`uri` —
le **resolve de l'item choisi** recalcule l'insertion sur le TEXTE
COURANT (miroir exact des règles fiche 029 : déjà importé exact ou
`.*`, `java.lang`, paquet courant, type imbriqué `$` → AUCUNE édition ;
sinon insertion après le DERNIER import, sinon après `package`, sinon au
début) — un seul calcul au lieu de N, jamais derrière une compilation en
vol (balayage purement lexical). SANS l'annonce : la liste reste
complète d'avant V-G, AUCUN changement. Le resolve d'un item sans FQCN
différé le rend TEL QUEL (jamais d'édition fabriquée).

**Registre client K2 (ADR 0031 (g))** : `docs/REGISTRE-CLIENT-K2.md` —
le document VIVANT des capacités servies non consommées (l'héritage) et
des nouvelles par vague ; prêt à devenir le prompt d'exécution
`cel-lsp`/`code-editor`.

### 7.27 Complément de catalogue : ajout/réordonnancement de paramètres, rename depuis un XML, lien de documentation lint (v1.12.0)

Le reste consigné du PV V-D3 et le solde (e) de V-G, en PR unitaires
(ADR 0032).

**Ajouter les paramètres manquants (quickfix, ID-FIRST)** — sur un
appel qui passe PLUS d'arguments que la signature n'en prend
(`calcul(3, "x", 4L)` sur `calcul(int)` ; `new Main(1, "y")` sur
`Main(int)`) :

```java
// AVANT — l'appel fautif :
int calcul(int a) { return a * 2; }
int appel() { return calcul(3, "x", 4L); }        // ← le quickfix ici

// APRÈS l'application (action différée, éditions au resolve) :
int calcul(int a, String texte, long nombre) { return a * 2; }
int appel() { return calcul(3, "x", 4L); }        // inchangé (il porte déjà les futurs paramètres)
// chaque AUTRE appelant de l'ancienne arité reçoit la valeur PAR DÉFAUT :
// m.calcul(4)  →  m.calcul(4, null, 0)
```

Les paramètres ajoutés sont typés par les types RÉSOLUS des arguments
excédentaires (FQN raccourcis + import dans le fichier déclarant),
nommés par l'heuristique par type (`texte`, `nombre`…) avec
anti-collision (params, locales du corps, champs). Refus honnêtes
motivés : pur mismatch de types, littéral `null`, déclarant binaire
(SDK), surcharge (plusieurs méthodes du même sélecteur), constructeur
synthétique, varargs, hiérarchie d'overrides, référence `X::m`.

**Déplacer un paramètre (refactor.rewrite.signature)** — au caret d'un
paramètre, « Déplacer le paramètre « x » vers la gauche/droite » : les
fragments de la signature s'échangent et CHAQUE appelant échange ses
arguments aux mêmes rangs (la liaison expression↔paramètre est
préservée — `(int a, String b)` → `(String b, int a)` et
`m(1, "x")` → `m("x", 1)`). Le paramètre peut être UTILISÉ (il se
déplace avec ses lectures). Refus : borne, hiérarchie, frontière
varargs, surcharge (risque de bascule de résolution), `X::m`,
appelant incomplet.

**Renommer une ressource DEPUIS un XML** — `prepareRename`/`rename`
fonctionnent sur un document XML du module : le caret sur
`@string/bonjour` (ou `@+id/…`, `?attr/…`, `R.string.…` du data
binding, ou la valeur `name="bonjour"` d'une déclaration values) rend
le placeholder exact et propage le renommage au code Java ET aux XML du
module — le document XML courant est servi depuis son tampon ouvert
(fait foi, même non sauvegardé). Refus et collisions identiques au
rename depuis le code Java (ressource-fichier, styleable, inconnu ;
identifiant invalide, nom déjà pris). Un document XML ouvert ne publie
JAMAIS de diagnostics Java.

**Lien de documentation des contrôles lint (codeDescription.href)** —
chaque diagnostic de provenance `lint` porte
`codeDescription.href = https://developer.android.com/studio/write/lint`
(la page officielle — VÉRIFIÉE : elle n'a PAS d'ancre par ID ; l'ID
reste dans `data.code` pour un client qui veut naviguer par
identifiant). Les diagnostics ecj/inspection n'ont pas de href : aucune
page officielle stable par erreur/inspection (documenté).

Latence liste mesurée : p95 = 410 µs (budget 30 ms). Validation :
17 tests (cas dorés multi-fichiers re-diagnostiqués — 0 erreur ;
oracles de motifs ; tampon XML en fait de foi ; client espion — aucun
diagnostic Java publié ; href), cf.
`docs/etudes/niveau-android-studio/mesures/2026-11-01-complement-catalogue.txt`.

### 7.28 V-A3 — robustesse des retours appareil : pré-chauffe du classpath, budget de complétion, chien de garde (v1.12.1)

Les retours appareil du 01/11/2026 (CodeIDE 0.205.0, logs
`codeide-logs-2026-11-01-144401.zip`) montraient un serveur FIGÉ en
pleine frappe : complétions et diagnostics en timeout en cascade
pendant des minutes, après une seconde invocation de complétion à la
même ancre. Cause racine : les coûts FROIDS du chemin de complétion
(annuaire du classpath — le `android.jar` seul porte ~40 000 entrées —
et LookupEnvironment ecj) étaient payés PAR LA PREMIÈRE REQUÊTE qui
les touche, sous le verrou de compilation : la diagnose et les
requêtes voisines s'empilaient derrière, la borne client de 5 s
explosait. Trois garde-fous posés :

1. **Pré-chauffe du classpath** (`MoteurJava.prechaufferClasspath()`) :
   plateforme + annuaire (phases 1-3) + une sonde ecj chaude. Jouée
   par le réchauffeur du double-tampon (fil de reconstruction, l'ancien
   moteur sert pendant la chauffe) ET à la première pose de chaque
   moteur (asynchrone) — plus JAMAIS sur le fil de requête. La
   construction paresseuse d'un moteur se joue désormais HORS du
   verrou global de l'environnement (les requêtes des autres modules
   ne se bloquent plus derrière). En repli documenté
   (`doubletampon.disabled=true`), la pré-chauffe ne part PAS (le
   repli garantit « aucun travail de fond »).
2. **Budget d'horloge de complétion** : 3 s par défaut, SOUS la borne
   client de 5 s. À l'expiration, les boucles d'élargissement
   (imports statiques de la 2e invocation, types du projet de la 3e)
   COUPENT et livrent le PARTIEL honnête déjà collecté. Propriété
   `jo.codeide.lsp.java.completion.budgetms` (bornée 200..60 000 ;
   `0` ramène à 200 ms — le budget ne se désactive pas).
3. **Chien de garde des requêtes lentes** : toute requête serveur
   (complétion, surbrillance, inlay, survol, signature, diagnose) qui
   dépasse 2 s publie une ligne `window/logMessage` (Warning) — cel-lsp
   la relaie dans le journal applicatif de CodeIDE
   (« server/logMessage: lspjava lenteur : … ») : le point de lenteur
   devient VISIBLE sur l'appareil, sans deviner. Propriété
   `jo.codeide.lsp.java.lenteur.seuilms` (0 = désactivé).

Latence sonde mesurée (banc JVM, android.jar réel 43 Mo) :
invocations 1-3 à 92/118/67 ms — la marge sous la borne vient du
budget (3 s) et de la pré-chauffe. Validation : 4 tests de
reproduction appareil (rafale didChange + complétions ×3 à la même
ancre + diagnose concurrente + jar dupliqué ; génériques hérités
propres — les faux positifs « I/O cannot be resolved » des logs
viennent du FICHIER utilisateur qui copie la signature générique avec
ses variables de type, javac dirait pareil), cf.
`java/src/test/kotlin/jo/codeide/lsp/java/Repro*20261101*`.

### 7.29 V-H — répartition priorisée des requêtes : la complétion pendant la tempête (v1.13.0)

Le serveur répartit ses requêtes de document sur un pool priorisé
(ADR 0010 — « le scheduler 3 lanes est conservé », transposé à la
couche protocole). Avant v1.13.0, chaque requête était calculée
SYNCHRONEMENT sur le fil de lecture lsp4j : un client qui émet à chaque
frappe une rafale complétion + inlayHint + surbrillances (+ actions sur
idle) plaçait la complétion derrière TOUTE la file (29–87 s mesurés sur
appareil, timeout client 5 s — la complétion ne s'affichait jamais, alors
qu'elle était calculée juste : 120 items rendus deux fois dans les logs).
Complémentaire de la §7.28 : la pré-chauffe et le budget (V-A3)
attaquent le COÛT de chaque calcul, la répartition (V-H) attaque la
FILE — le chien de garde y chronomètre désormais le calcul SUR LE
WORKER du répartiteur.

**Trois voies** — la prise d'un worker privilégie la voie la plus basse,
puis l'ordre de soumission :

| Voie | Requêtes |
|---|---|
| 0 — FRAPPE | `completion`, `signatureHelp`, `statementCompletion` |
| 1 — NAVIGATION | `hover`, `definition`, `typeDefinition`, `implementation`, `references`, `documentHighlight`, `prepareRename`, `rename`, hiérarchies de types et d'appels, `superDefinition`, `usagesGroupes` |
| 2 — CONFORT | `documentSymbol`, formatage (3 formes), `selectionRange`, `foldingRange`, `inlayHint`, `semanticTokensFull`, `codeAction`, `codeLens` |

**Supersession à valeur (jamais d'annulation)** — une requête EN ATTENTE
remplacée (même document, même voie — ou voie inférieure lors d'une
soumission FRAPPE) est soldée par la **réponse vide honnête** du type de
la requête : liste vide, `null` pour le survol (convention I9), jamais
une exception — le client attend une réponse pour l'identifiant qu'il a
émis. Une frappe rend caduques les voies inférieures en attente du même
document (le texte a bougé, leurs coordonnées sont périmées).

**Capture à la soumission, époque à la livraison** — la requête porte des
positions relatives au texte au moment de son émission : le couple
(chemin, texte) est figé à la soumission, et si le document mute pendant
le calcul, la réponse calculée ne part JAMAIS — réponse vide honnête
(invariant « 0 réponse périmée » conservé sous le nouveau parallélisme ;
elle remplace `calculerFraiche` pour la complétion — recalculer contre
un texte muté y appliquerait une position révolue).

**Exclusion par fichier** — jamais deux requêtes du MÊME document en vol,
toutes voies confondues ; les requêtes d'espace de travail sont durables
(jamais supersedées). `exit()` solde les requêtes en attente par leur
réponse vide : aucune future en erreur côté client lors d'un arrêt.

Les verrous : 8 tests (5 sémantiques du répartiteur + 3 au protocole par
boucle locale LSP4J authentique — la tempête du journal rejouée ne perd
ni n'échoue aucune future, la complétion rend les membres d'une classe
du projet PENDANT la tempête), cf.
`docs/etudes/niveau-android-studio/mesures/2026-11-02-vh-repartition.txt`.

## 8. Capability maison `x-codeide.completionRanking`

Le protocole riche de classement (ADR 0012/0015, design intégré N8) :
chaque item de complétion transporte dans son champ `data` un payload de
**classement** qui permet au client le **re-filtrage local** par frappe
sans aller-retour serveur (la réponse structurelle au facteur ART
3–5×).

Annonce (voyage via le sérialiseur dédié — §4.3) :

```json
"x-codeide.completionRanking": {
  "version": 2,
  "poidsItem": "x-codeide.poids",
  "correspondanceItem": "x-codeide.correspondance"
}
```

**v2 = vague V-C3** (ADR 0026 (a), jamais de mutation silencieuse) : le
poids packé gagne la dimension **compatibilité** au type attendu, entre la
correspondance et la nature — le re-filtrage local du client reproduit
l'ordre **smart** du serveur (compatible en tête, jamais caché). Les clés
du payload sont inchangées ; un client v1 qui trie par le poids packé
reste cohérent (l'ordre du poids EST l'ordre serveur).

Payload par item (clés stables, versionnées par la capability) :

| Champ | Rôle |
|---|---|
| `x-codeide.poids` | le poids packé (plus petit = plus prioritaire — contrat verrouillé ADR 0012 R1) |
| `x-codeide.correspondance.prefixe` | le préfixe tapé au moment de l'envoi |
| `x-codeide.correspondance.longueurMinSousChaine` | la règle « substring ≥ 3 » (fiche 008) |

Un client qui ignore la capability perd silencieusement ces champs
(comportement spec LSP pour les champs inconnus) et retombe sur le
filtrage standard par `filterText` — **repli automatique**, aucune
dégradation de service. Un futur protocole v2 se portera en valeur
`version: 2` (ou capability nouvelle), jamais par mutation silencieuse.

**Clés additives V-G (ADR 0031 (f) — la version RESTE 2)** : quand le
client annonce `completionItem.resolveSupport`, le `data` des items à
auto-import gagne `importFqn`, `importStatique` et `uri` (l'édition
d'import est alors différée au resolve, §7.26) — clés ADDITIVES et
conditionnées à l'annonce client : un client ancien voit la liste
complète d'avant V-G, AUCUN changement sémantique (règle ADR 0015 R1 :
la version monte sur évolution SÉMANTIQUE, pas additive).

**Capability `x-codeide.plageVisible` v1 (V-F4 — posée à la livraison,
ADR 0029 phase 4)** : le contrat de la notification
`$/codeide/plageVisible` (§9.5) — offsets ABSOLUS, dernière notification
gagnante, oubli au didClose ; toute évolution sémantique montera la
version.

## 9. Extensions expérimentales

Les extensions MAISON du protocole (héritées du vendored, fiche 054, et
posées par les vagues — chaque annonce est versionnée `x-codeide.*`) —
un client standard ne les appelle jamais :

### 9.1 `superDefinition` — le « GO TO Super »

La méthode de BASE d'une redéfinition au caret. Réponse : liste de
cibles à champ `path` (la forme du vendored), **contrat de sérialisation
exact du vendord `NavigationTarget` — miroir du client cel-lsp, aucun
champ renommé** : `path`, `start`, `end` (offsets du JETON nom déclaré
dans la source cible) et `label` (libellé picker « nom  ·  conteneur »,
deux espaces autour du séparateur). Liste VIDE
honnête si le caret ne porte pas de redéfinition (I9). Exemple — dans un
projet où `Demo.greet` REDÉFINIT `Base.greet` :

```json
{"jsonrpc":"2.0","id":30,"method":"superDefinition","params":{
  "textDocument":{"uri":"file:///projet/demo/src/demo/Demo.java"},
  "position":{"line":8,"character":20}}}
```

```json
{"jsonrpc":"2.0","id":30,"result":[
  {"path":"/projet/demo/src/base/Base.java","start":76,"end":81,"label":"greet  ·  Base"}
]}
```

### 9.2 `decompiledSource` — la source d'un FQCN binaire

Le scénario de production : `definition` a répondu
`jdt://decompiled/<fqcn>` — le client demande ici le tampon
lecture-seule. Le champ `source` est null si le FQCN est introuvable.

```json
{"jsonrpc":"2.0","id":31,"method":"decompiledSource","params":{
  "fqcn":"java.lang.String"}}
```

```json
{"jsonrpc":"2.0","id":31,"result":{
  "source":"/* décompilé — public final class String … */ …"}}
```

Le câble binaire côté moteur : `fqcnAt` / `isBinaryType` /
`decompiledSource` (source attachée ou stub, §10).

### 9.3 `usagesGroupes` — les usages groupés par fichier (vague V-E)

Le rendu arborescent « Find Usages » de la référence (résultats groupés
par fichiers, ADR 0028 (e)) : MÊME requête moteur que `references`
(usages multi-fichiers exhaustifs, déclaration **toujours incluse** —
elle est la racine de la vue), servie groupée. Un groupe par fichier
porteur (ordre de première apparition de la liste triée chemin, offset),
occurrences par offset croissant, offsets **ABSOLUS** dans le texte du
porteur (même convention que `superDefinition` : jamais ligne/colonne).

Le **plafond honnête** (ADR 0028 R1 : jamais de troncature silencieuse) :
au-delà de 2 000 usages rendus, la réponse est tronquée dans l'ordre
trié et L'ANNONCE TOUJOURS — `total` (le compte exact, jamais plafonné),
`rendus`, `plafond` et le drapeau `tronque` ; la liste plate standard
`references` reste EXHAUSTIVE (aucune troncature). Le plafond est
annoncé dans la capability `x-codeide.usages` (`plafondRendu`).

```json
{"jsonrpc":"2.0","id":32,"method":"textDocument/usagesGroupes","params":{
  "textDocument":{"uri":"file:///projet/demo/src/demo/Demo.java"},
  "position":{"line":8,"character":20}}}
```

```json
{"jsonrpc":"2.0","id":32,"result":{
  "total":2,"rendus":2,"plafond":2000,"tronque":false,
  "groupes":[
    {"path":"/projet/demo/src/demo/Demo.java",
     "usages":[{"start":146,"end":151,"estDeclaration":true}]},
    {"path":"/projet/demo/src/demo/Consommateur.java",
     "usages":[{"start":1024,"end":1029,"estDeclaration":false}]}
  ]}}
```

Vue VIDE honnête si le caret ne porte pas de symbole (I9).

### 9.4 `statementCompletion` — compléter l'instruction (V-C3)

Le geste Ctrl+Shift+Enter de la référence (ADR 0026 (c)) : équilibre
l'instruction au caret — referme le littéral ouvert, referme les listes
ouvertes, pose un argument de secours après une virgule traînante, termine
par `;` (ou ouvre le bloc ` {` d'un mot-clé de contrôle). Calcul
**purement lexical** (aucune résolution — servi dans tous les régimes).
Rien à compléter → `edition: false` (jamais d'édition vide).

```json
{"jsonrpc":"2.0","id":41,"method":"textDocument/statementCompletion","params":{
  "textDocument":{"uri":"file:///projet/demo/src/demo/Main.java"},
  "position":{"line":8,"character":24}}}
```

```json
{"jsonrpc":"2.0","id":41,"result":{
  "offset":214,"longueur":0,"texte":"0);","caret":217,"edition":true}}
```

Les offsets sont **absolus** (même convention que `superDefinition` : jamais
de conversion ligne/colonne). Le client applique l'édition puis pose le
caret à `caret`. Le texte tapé APRÈS le caret sur la même instruction reste
à la charge de la frappe (borne honnête). Consommation client à venir
(registre K2) — l'appel par un client standard qui ne connaît pas la
méthode est simplement absent.

### 9.5 `$/codeide/plageVisible` — la zone affichée (V-F4)

La notification MAISON (announce `x-codeide.plageVisible` **v1** —
ADR 0029 phase 4) par laquelle le client déclare, pour CHAQUE document,
la zone qu'il affiche réellement — le publicateur s'en sert pour la
double publication « visible d'abord » sur les grands fichiers (§7.25).

```json
{"jsonrpc":"2.0","method":"$/codeide/plageVisible","params":{
  "uri":"file:///projet/demo/src/demo/Doree.java",
  "start":4210,"end":9877}}
```

Convention : offsets **ABSOLUS** dans le texte du document (jamais de
conversion ligne/colonne côté serveur — même convention que
`superDefinition`). Sémantique de fraîcheur : la dernière notification
gagne (la zone défile) ; un document fermé est OUBLIÉ ; l'ABSENCE de
notification = comportement d'avant V-F4, à l'identique. Le client
throttle l'envoi (défilement + ouverture) ; hors fenêtre de vie
(avant initialize, après shutdown) la notification est ignorée
silencieusement (règle maison des notifications).

## 10. Utilisation in-process du moteur

Le moteur pur (`jo.codeide.lsp.java.MoteurJava`) s'utilise sans boucle
JSON-RPC — c'est le mode des tests et des bancs. Construction sur les
racines du projet :

```kotlin
import java.nio.file.Path
import jo.codeide.lsp.java.MoteurJava

val moteur = MoteurJava(racines = listOf(Path.of("/projet/demo")))

val chemin = "src/demo/Demo.java"
val source = Files.readString(Path.of("/projet/demo/src/demo/Demo.java"))
```

Surface principale (extraits représentatifs — KDoc complète dans le
code) :

| Domaine | Méthodes |
|---|---|
| Diagnostics | `diagnose(chemin, source)`, `diagnoseSyntax(chemin, source)` (repli instantané), `serviraitPasseComplete(…)` + `diagnosePrioritaire(…)` (V-F4 — sonde et passe « visible d'abord », §7.25) |
| Complétion | `completer(chemin, source, offset)` → `ReponseCompletion` (items + `totalDisponible` + genre), `complete(…)` |
| Survol | `hoverAt(chemin, source, offset)`, `survolRiche(…)` |
| Signatures / inlay | `signatureHelp(…)` → `AideSignature`, `inlayHints(chemin, source)` |
| Navigation | `definition`, `typeDefinition`, `implementation`, `superDefinition`, `fqcnAt`, `isBinaryType`, `decompiledSource` |
| Usages | `references`, `referencesParBalayageComplet` (l'oracle), `surbrillances` |
| Renommage | `cibleRenommageA`, `renommageProjet`, `conflitsRenommage`, `editionsRenommage`, `apercuRenommage` (preview sur copie), `editionsRenommageFichier(ancien, nouveau, texteDe)` (V-G — `willRenameFiles`, §7.26) |
| Actions de code | `actionsCode(chemin, source, diagnostics)`, `actionsCodePour(…, diagnostic)` |
| Confort éditeur | `formater(source, style)`, `formaterPlage`, `editionsFormatagePlage`, `plis(source)`, `organiserImports`, `chainesSelection` |
| Hiérarchies | `typeAuCaret`, `supertypesDirects`, `sousTypes`, `methodeAuCaret`, `appelsEntrants`, `appelsSortants` |
| Coloration | `jetonsSemantiques(chemin, source)` (V-G — `semanticTokens`, §7.16) |
| Mémoire | `pressionMemoire(niveau)` → `RapportPressionMemoire` (V-F3 — shim `onTrimMemory`, §7.25 ; relais multi-modules `EnvironnementCompilation.pressionMemoire`), `parseCacheOctetsEstimes()` (sonde budget) |
| Index / types | `findTypesByPrefix`, `fqnsForSimpleName`, `typesDansPaquet`, `constructionsIndexMots`, `prechaufferIndexMots(signalement)` (V-G — progression d'indexation, §7.26) |

Exemple — survol + complétion + renommage :

```kotlin
// Survol : nom + types, sur déclaration COMME sur référence (fiche 020)
val offset = source.indexOf("compteur + 1")
val hover = moteur.hoverAt(chemin, source, offset)
checkNotNull(hover)                 // « int compteur »

// Complétion de membres : après le point de « message. »
val reponse = moteur.completer(chemin, source, source.indexOf("greet(") - 1)
println(reponse.items.take(3).map { it.libelle })
println("total disponible : ${reponse.totalDisponible}")

// Renommage sûr : collisions AVANT écriture (fiche 061/026)
val cible = moteur.cibleRenommageA(chemin, source, offset)
val conflits = moteur.conflitsRenommage(chemin, source, offset, "occurrences")
val occurrences = moteur.renommageProjet(chemin, source, offset, "occurrences")
val editions = moteur.editionsRenommage("occurrences", occurrences)
val apercu = moteur.apercuRenommage(source, editions)   // preview = résultat
```

L'ordonnanceur du moteur est à **worker unique** avec supersession par
fichier et jeton d'annulation coopératif (fiches 048/046) — les requêtes
sont sérialisées par fichier, la dernière frappe gagne.

## 11. Environnement de compilation

Le routeur de classpaths (étage N1, fiches 037/010/042) résout l'ordre :
**sources ouvertes → jars du classpath → jrt (runtime ou externe) →
stubs intrinsèques**, avec FQCN **longest-match** (fiche 010). Le JRT
peut être celui du runtime OU un externe via `jrt-fs.jar` +
`URLClassLoader`/`ServiceLoader` (fiche 031). Un lecteur ZIP brut
maison lit les jars parité octet à octet avec `ZipFile` (fiche 038).

Pour un projet Android, le jar `android.jar` (API 34) et l'AAR
appcompat s'ajoutent au classpath — c'est ce que fait la CI pour les
gates (§12) ; la conformité source/cible se règle à la construction du
moteur.

### 11.1 Double-tampon, époques et surface hôte (vague V-F1)

`EnvironnementCompilation` (§4.4 — régime vivant) expose depuis V-F1 la
surface de concurrence suivante à l'hôte :

| Membre | Rôle |
|---|---|
| `calculerFraisse(chemin) { moteur -> … }` | le protocole d'époque : capture atomique (moteur, génération), calcule, ne LIVRE que si l'époque n'a pas tourné — sinon rejeu (bornes §7.19). Le serveur route déjà TOUS ses chemins de réponse par là ; l'hôte in-process qui calcique contre un moteur routé devrait en faire autant |
| `attendreReconstructions(dureeMaxMs)` | borne le moment où les reconstructions de double-tampon en cours sont drainées (après une sync, le moteur neuf est vivant) — sonde d'attente pour l'orchestrateur et les bancs |
| `ajouterAuditeurEpoque { cle -> … }` | notifié APRÈS chaque swap EFFECTIF (le moteur neuf sert déjà) — l'auditeur type redemande les calculs des documents ouverts du module (le serveur y republie ses diagnostics) |
| `generationDe(module)` / `reconstructionsEnCours()` | sondes d'état (bancs, orchestrateur) |

Comportement de `moteur(module)` sous double-tampon : pendant la fenêtre
de reconstruction, il rend l'ANCIEN moteur (l'époque vivante — par
conception) ; le neuf n'y devient observable qu'après le swap
(`attendreReconstructions` borne l'attente). Les sémantiques fiche 034
sont INTACTES (no-op redondant, notification EXACTEMENT une fois par
remplacement effectif, isolation par module) — seul l'observable « moteur
reconstruit » est devenu ASYNCHRONE.

## 12. Bancs et gates de CI

| Commande | Rôle |
|---|---|
| `./gradlew :java:bench` | banc empreinte/latence du noyau (JVM séparée) : diagnose complet p50 11,7 ms / p95 39,8 ms, **diagnose rapide p50 1,5 ms**, hover p50 0,12 ms, complétion membres p50 0,04 ms, tas au repos 16 Mo |
| `./gradlew :java:benchQualite` | banc qualité complétion (ADR 0016/0030) — corpus versionné **v2 : 24 positions** (dont 6 V-C3 smart/typedef/chaîne/second appel, split **train/éval** déterministe), baseline signée `bancs/baselines/completion.json` : **recall 1,000 / top-1 1,000 / MRR 1,000**, sous-ensemble **ÉVAL : 1,000/1,000/1,000** (cibles V-C3 ≥ 0,95/≥ 0,80), naïf 0,917/0,458/0,611 (ÉVAL top-1 0,000 — la preuve de valeur de la vague), zéro saut de liste, p95 ~38 ms — **ÉCHOUE sur régression** |
| `./gradlew :java:benchUsages` | banc usages (ADR 0014) — rappel 1,000 (oracle), p95 ~62 ms < 100 ms, **CI-bloquant** |
| `bash scripts/preparer-oracle.sh` | construit l'**oracle vendored** du banc croisé (clone `codeide-lsp` épinglé cb809e14 + `:lsp:java:jar`, JVM pur — aucun SDK Android) → `.oracle/` (jamais committé, R6.4) |
| `bash scripts/preparer-projet-android.sh` | télécharge le **projet Android réel du banc** (android.jar 34-ext12 + 16 AARs/jars androidx/material, fermeture transitive vérifiée) → `bancs/projet-android-libs/` (jamais committé, R6.4) |
| `BANC_ANDROID_LIBS=… ./gradlew :java:test --tests jo.codeide.lsp.java.protocole.BancProjetAndroidReelTest` | **banc projet Android réel** (20 tests) : la timeline appareil rejouée au protocole (didOpen avant sync → sync → LIVE-REPUBLISH) puis CHAQUE famille de complétion (import, type + auto-import, membre, statique imbriqué, locale, stub, new, frappe continue, latences) contre les vraies bibliothèques — la sonde de parité Android Studio. Sans les bibliothèques, la suite s'ignore (gate visible) |
| `./gradlew :java:benchParite` | **banc de parité croisée** (étage T, ADR 0016) — le même corpus (**v2 : 24 positions** + 4 frappes) rejoué contre le vendored (**boîte noire en sous-processus JVM isolé**, pilote compilé contre des stubs à alarme) et le moteur neuf (in-process) ; baseline signée `bancs/baselines/parite-completion.json` : couverture cahier **1,000**, **314 deltas bruts documentés** (re-signés V-C3 : accessibilité JLS 6.6 au 1er appel, positions smart, gabarits de l'oracle — arbitrage consigné dans la baseline) — **ÉCHOUE** sur couverture en baisse, libellé perdu ou dérive de l'oracle ; oracle absent = banc ignoré avec message (exit 0), la CI le construit d'abord (job `banc-parite` **bloquant**) |
| `bash scripts/kit-bascule.sh` | assemble le **kit de passe bascule sur appareil** (§8.5) : classes du banc + du moteur, ecj patché, dépendances, `banc-bascule.sh` — protocole et tables dans `bancs/GABARIT-BASCULE-APPAREIL.md` (R5 : mesures réelles du propriétaire, jamais simulées) |
| `ANDROID_JAR=… ./gradlew :java:test` | ouvre les gates Android (fiches 033/004/036/043/044/050/053) — la CI télécharge android.jar API 34 (zip officiel) + AAR appcompat |
| `./gradlew :java:test --tests jo.codeide.lsp.java.BancDoubleTamponTest` | **banc V-F1** (R5) : latence nominale vs PENDANT reconstruction tenue (p50/p95 µs, budget ADR « p95 ≤ 2× » imprimé), reconstruction réelle, coût du rejeu (2 passes) — la suite `DoubleTamponTest` (8 tests) verrouille les invariants : 0 figage, 0 réponse périmée, coupures fines, syncs rapides, repli, republication au swap |
| `./gradlew :java:test --tests jo.codeide.lsp.java.VerrousPerimetreTest` | **banc V-F2** : la MÉTRIQUE CI `synchronized(` < 6 dans `java/src/main` (mesuré : 0) + les chemins sans ecj répondent pendant qu'un fil détient le verrou de compilation (et un chemin ecj BLOQUE — la sémantique une-résolution-à-la-fois est réelle) |
| `./gradlew :java:test --tests jo.codeide.lsp.java.ChaosConcurrenceTest` | **chaos test V-F2** (ADR 0010/0029) : 6 fils × 90 opérations mêlées (diagnoses, complétions, syncs/époques, XML, invalidations) — 0 interblocement (dump des piles en échec), 0 exception non attendue, verdict fiche 012 préservé |
| `./gradlew :java:test --tests jo.codeide.lsp.java.ChargeConcurrenteTest` | **banc de charge V-F2** (critère ADR 0010) : complétion fichier A ∥ diagnose fichier B — p95 de chacun ≤ séquentiel + 10 % + 10 ms (l'ancien moniteur échouait : concurrent ≈ somme des deux) |
| `./gradlew :java:test --tests 'jo.codeide.lsp.java.InspectionsTest'` | **banc V-C1** : 12 cas dorés des inspections (détection, non-détection, suppression, mutants P4, P3 zéro-erreur, P1 frappe confinée, dumb-aware) |
| `./gradlew :java:test --tests 'jo.codeide.lsp.java.ControlesLintTest'` | **banc V-C2** : 8 cas dorés du lint (échappatoires NewApi une à une, @SuppressLint, base synthétique, repli honnête, garde erreurs) — gate android.jar du banc |
| `./gradlew :java:test --tests 'jo.codeide.lsp.java.completion.CompletionVagueC3Test'` | **banc V-C3** : 26 cas dorés (smart S1–S5, typedef T1–T3, second appel D1–D4 avec imports statiques et index projet, chaîne, statement completion, stats à fenêtre glissante, mémo binaire avec transcript de latence, littéral `R.string.*` sur la fixture mini, mots-clés de tête de déclaration D7) |
| `./gradlew :java:test --tests 'jo.codeide.lsp.java.RefactorisationsVd2Test'` | **banc V-D2** : 22 cas dorés des refactorings lourds (extractions dorées + compilables, refus honnêtes — paresseuse, condition de boucle, return, deux sorties, réaffectée, effets multi-usages, hors-fichier ; oracle safe-delete — usage même fichier, autre fichier, sites `new`, références `X::m`, override, paramètre, champ/locale inutilisés) + `LatenceRefactorissementsVd2Test` : transcript de latence de la liste avec refactorings (48 requêtes, p95 mesuré < 30 ms) |
| `./gradlew :java:test --tests 'jo.codeide.lsp.java.memoire.MemoireBudgetsOctetsTest'` | **banc V-F3** : budgets d'octets (éviction LRU du cache de parse au-delà du budget, garde anti-cyclage — un fichier seul plus gros que le budget vit seul, cumul borne d'entrées + budget, invalidation suit les octets ; mémo de localisation — négatifs évincés d'abord, verdict rendu EXACT après re-probe, `evictNegatifs` garde les positifs) + pressions `onTrimMemory` (RUNNING → parse seul et moteur utilisable, BACKGROUND → + mémos + négatifs, COMPLETE → libération intégrale et le moteur SURVIT, relais environnement → tous les moteurs vivants) |
| `./gradlew :java:test --tests 'jo.codeide.lsp.java.protocole.PlagePrioritaireTest'` | **banc V-F4** : la passe prioritaire publie le corps visible (pas les masques), les refus honnêtes rendent null, la sonde distingue mémo/confinement SANS compiler, la notification `$/codeide/plageVisible` déclenche la double publication « visible d'abord » (l'ordre et la version finale font foi), SANS notification le comportement d'avant V-F4 est identique |
| `./gradlew :java:test --tests 'jo.codeide.lsp.java.protocole.SurfaceProtocoleVgTest'` | **banc V-G** (ADR 0031) : progression `workDoneProgress` (séquence begin/report/end si annoncée, silence sinon), `data.provenance` typée par diagnostic, `willRenameFiles` (déplacer une classe publique → paquet + imports ; FQCN conservé → edit vide honnête), vecteur sémantique du cas doré EXACT (genres, déclaration, static/final) + **latence p95 mesurée** (cible < 25 ms), `completionItem/resolve` (import différé avec `resolveSupport`, liste complète sinon) |

Re-baseline : par **PR dédiée** uniquement (ADR 0016 — jamais en passant).
Re-pin de l'oracle (montée de version du vendored) : PR dédiée de re-pin +
re-signature de `parite-completion.json` (les stubs de façade suivent).

## 13. Conventions d'honnêteté et limites connues

- **Convention I9** (fiche 059) : hover **null**, structures **VIDES**
  ailleurs — jamais de contenus factices ; une liste vide EST une
  réponse.
- **Usages multi-fichiers depuis V-E (1.3.0)** : `references` rend les
  usages de TOUT le projet (décision D1 — ADR 0028 ; la parité stricte
  §10.4 est abandonnée, la rupture est annoncée par `x-codeide.usages`) ;
  le RPC groupé `usagesGroupes` borne à 2 000 rendus AVEC total exact et
  drapeau `tronque` (§9.3).
- **Coloration sémantique (V-G)** : `semanticTokens` ANNONCÉ avec légende,
  vecteur **full uniquement** — PAS de delta serveur (`range` non servi : le
  client re-demande le vecteur complet à chaque édition, jamais un delta
  faux) ; `codeDescription.href` NON livré (aucun consommateur identifié).
  Cf. §7.16.
- **Quick-fixes mono-fichier** (fiche 063) : le multi-fichiers passe
  par le renommage (N5) et le protocole (N8), jamais par ces édits.
- **`decompiledSource` hors protocole d'époque** (V-F1, résiduel documenté) :
  le RPC sonde les moteurs VIVANTS sans chemin routable (le FQCN ne
  dit pas de quel module il vient — ADR 0215) ; une source décompilée
  pendant un swap peut donc refléter l'époque précédente — la requête
  suivante re-sonde (comportement d'aperçu, jamais un diagnostic).
- **Version 1.11.0 = vagues V-F finale + V-G (ADR 0029 phases 3–4 +
  ADR 0031 Accepté-livré : budgets d'OCTETS du cache de parse — 8 Mio,
  coût ESTIMÉ source×6, garde anti-cyclage — et du mémo de localisation
  — 2 Mio, négatifs évincés d'abord, re-probe honnête, contrats fiche 024
  intacts ; shim `onTrimMemory` par niveaux miroirs ComponentCallbacks2
  avec rapport CHIFFRÉ et relais multi-modules, jamais de construction
  d'environnement sur signal mémoire ; plage prioritaire `$/codeide/plageVisible`
  v1 — double publication « visible d'abord » ≥ 16 000 caractères, passe
  prioritaire PURE (aucune écriture d'état), la complète fait foi, INERTE
  sans notification ; V-F5 incrémentalité REPORTÉE motivée — conditionnée
  à ADR 0017 (b)/décision D4, critère utile déjà tenu par le diagnose
  rapide fiche 014 ; workDoneProgress d'indexation G-032 — préchauffage
  au creux initialized, jeton begin/report/end throttlé si et seulement si
  annoncé ; diagnostics enrichis — `data {provenance, code}` machine-readable
  + relatedInformation « requis par X » fiche 019 ; willRenameFiles G-022
  — package + imports du cas doré, usages QUALIFIÉS sans import non
  réécrits (périmètre honnête) ; semanticTokens G-019 — légende 8 types +
  4 modificateurs, full sans delta, p95 mesuré 1 ms < 25 ms ;
  completionItem/resolve RÉEL G-021 — import différé négocié par
  resolveSupport, sans annonce la liste reste complète ; registre client
  K2 vivant `docs/REGISTRE-CLIENT-K2.md` G-023 partiel — §7.25/§7.26/§9.5)** ;
  - **Version 1.9.0 = vague V-D tranche 2 (refactorings lourds — ADR 0027 Accepté-tranche 2 : extract variable/constante/méthode sur SÉLECTION avec heuristiques §A5 — une sortie au plus, paramètres par première lecture, locales écrites-d'abord déclarées dedans, refus honnête des positions paresseuses et du contrôle de flux non local ; inline variable — réaffectations et effets multi-usages fermés — et inline constante mono-fichier ; safe delete par usages multi-fichiers exhaustifs avec oracle « 0 manqué » — refus motivés override/paramètre ; socle usages étendu aux sites new X(…) et références X::m, constructeurs identifiés au caret, égalité d'étendue de la fenêtre +120 corrigée ; kinds refactor.extract.*/inline.*/rewrite.delete annoncés ; cas dorés compilables par capacité, latence liste p95 mesurée 3 881 µs < 30 ms — §7.23)** ; - **Version 1.8.0 = vague V-D tranche 1 + V-C2 complément de catalogue (ADR 0027 Accepté-tranche 1 + ADR 0024 : organizeImports exposé M3/G-020 avec kinds source.*/refactor.* et codeAction/resolve DIFFÉRÉ ; génération de code — getter/setter/constructeur/equals+hashCode/toString/stubs d'outrepassement ; intentions sans diagnostic — ternaire, constante, référence de méthode, var↔explicite ; familles ecj étendues +7 IDs avec P5 étendu 0 violation ; latence liste p95 mesurée 255 µs < 30 ms ; lint Range/ResourceType/CommitPrefEdits/Recycle — catalogue 15 contrôles ; segment compact CLBA de la base api-versions, re-compilable bit-à-bit — §7.22)** ; - **Version 1.7.0 = vague V-C3 (complétion niveau Android Studio — ADR 0026 Accepté-livré : smart par bindings avec filtrage gradué jamais caché, typedef @IntDef/@StringDef en tête avec import statique, littéraux R.* par @…Res + référentiel, second appel — inaccessibles marqués + imports statiques — et 3ᵉ appel projet par CIDX, chaîne à deux sauts, statement completion lexicale moteur + RPC textDocument/statementCompletion, stats locales opt-in à fenêtre glissante, mémoïsation des membres binaires, mots-clés de tête de déclaration D7/G-024 avec fusion stubs ; capability completionRanking **v2** ; corpus qualité **v2 24 positions split train/éval** — ÉVAL 1,000/1,000 ; fiches 066 et 007 amendées, ECARTS-SERVEUR M1 résolu — §7.21/§8/§9.4)** ; **version 1.0.0 = bascule §8.5 SIGNÉE (30/10/2026)** ; **version 1.1.0 = intégration CodeIDE (§4.4 : régime vivant, option B arbitrée ; superDefinition aligné contrat vendord ; fiche 066)** ; **version 1.3.0 = vague V-E (références multi-fichiers D1, surbrillances composées typées, RPC usagesGroupes ; le 1.2.0 est SAUTÉ : valeur fantôme du vendord proscrite par la fiche 059 I7)** ; **version 1.4.0 = vague V-B (projet Android : modèle v11 + dérivateur disque, référentiel de ressources, classes light R/BuildConfig/Binding/BR, navigation ressource, interop Kotlin — décisions D2/D3/D6 ; `declasserDatabinding` RETIRÉ, rupture d'API consignée)** ; **version 1.5.0 = vague V-F1 (double-tampon + époques de l'environnement : 0 figage / 0 réponse périmée, rejeu des requêtes coupées, annulation fine complétion + usages, republication au swap d'époque, repli `jo.codeide.lsp.java.doubletampon.disabled` — ADR 0029 phase 1, §7.19/§11.1)** ; **version 1.6.0 = vagues V-F2 + V-C1/V-C2 (verrous par périmètre + chaos tests + banc de charge ADR 0010, comptage synchronized 99 → 0 ; inspections — contrat, profil, suppressions, 8 pilotes, provenance "inspection" ; lint Android — base api-versions en tâche de fond, NewApi/InlinedApi/ObsoleteSdkInt/MissingSuperCall/SetTextI18n, @SuppressLint, provenance "lint" — §7.20)** : checklist 6/6 du
  gabarit `bancs/GABARIT-BASCULE-APPAREIL.md` (passe appareil moto g06 —
  18/18, 0 saut, p50/p95 GLOBAL 42,93/73,01 ms ; deltas 165 arbitrés
  103/62/0 — `ANALYSE-DELTAS-BASCULE.md`) ; `serverInfo.version` rend
  désormais **générée du dépôt** (fiche 059 I7 — la 1.0.0 était
  celle de la signature de bascule, la 1.1.0 celle de l'intégration, la
  1.1.1 celle du correctif du diagnose rapide — le corps hôte masqué par
  erreur d'identité, §9 — la **1.1.2 celle du correctif ART** : fournisseur
  jrt du JDK externe ininstanciable sur appareil (ServiceConfigurationError,
  un Error invisible à `catch (Exception)`), repli régime instantané au
  lieu de « Internal error. » sur toute requête, §10) ;
  mise en œuvre client : `docs/BASCULE-CODEIDE.md` +
  `docs/INTEGRATION-CODEIDE.md` ; suivi post-bascule borné au §6 du
  gabarit.
- **Organisation des imports** : exposée comme action
  `source.organizeImports` au fil LSP depuis la vague V-D tranche 1
  (§7.22) ET par l'API moteur (§7.17).
- Le serveur n'écrit **jamais** sur le disque : preview = résultat
  (renommage, actions de code — le client applique).
