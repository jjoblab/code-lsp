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
      "codeActionProvider": { "codeActionKinds": ["quickfix"] },
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

### 7.16 `textDocument/semanticTokens/full` — tolérant, non annoncé

Aucun service moteur ne sert les tokens sémantiques : la capability est
**absente** de l'annonce (règle matrice ADR 0015) mais la requête reste
**tolérante** — réponse à `data` vide (fiche 059 I9), jamais d'erreur :

```json
{"jsonrpc":"2.0","id":28,"result":{"data":[]}}
```

### 7.17 Organisation des imports (API moteur)

L'organisateur d'imports (étage N7, fiche 022) — identification des FQN
à importer depuis les **bindings**, règles d'exemption (`java.lang`,
même paquet, paquet couvert par un on-demand existant), remplacement du
bloc existant par un bloc **trié** — est servi par l'API moteur
(`MoteurJava.organiserImports`, §10) : la couche protocole ne l'expose
pas encore comme action `source.organizeImports` (amélioration
candidate). Exemple d'emploi en Kotlin :

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

## 8. Capability maison `x-codeide.completionRanking`

Le protocole riche de classement (ADR 0012/0015, design intégré N8) :
chaque item de complétion transporte dans son champ `data` un payload de
**classement** qui permet au client le **re-filtrage local** par frappe
sans aller-retour serveur (la réponse structurelle au facteur ART
3–5×).

Annonce (voyage via le sérialiseur dédié — §4.3) :

```json
"x-codeide.completionRanking": {
  "version": 1,
  "poidsItem": "x-codeide.poids",
  "correspondanceItem": "x-codeide.correspondance"
}
```

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

## 9. Extensions expérimentales

Deux méthodes JSON-RPC héritées du protocole maison du vendored
(fiche 054) — un client standard ne les appelle jamais :

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
| Diagnostics | `diagnose(chemin, source)`, `diagnoseSyntax(chemin, source)` (repli instantané) |
| Complétion | `completer(chemin, source, offset)` → `ReponseCompletion` (items + `totalDisponible` + genre), `complete(…)` |
| Survol | `hoverAt(chemin, source, offset)`, `survolRiche(…)` |
| Signatures / inlay | `signatureHelp(…)` → `AideSignature`, `inlayHints(chemin, source)` |
| Navigation | `definition`, `typeDefinition`, `implementation`, `superDefinition`, `fqcnAt`, `isBinaryType`, `decompiledSource` |
| Usages | `references`, `referencesParBalayageComplet` (l'oracle), `surbrillances` |
| Renommage | `cibleRenommageA`, `renommageProjet`, `conflitsRenommage`, `editionsRenommage`, `apercuRenommage` (preview sur copie) |
| Actions de code | `actionsCode(chemin, source, diagnostics)`, `actionsCodePour(…, diagnostic)` |
| Confort éditeur | `formater(source, style)`, `formaterPlage`, `editionsFormatagePlage`, `plis(source)`, `organiserImports`, `chainesSelection` |
| Hiérarchies | `typeAuCaret`, `supertypesDirects`, `sousTypes`, `methodeAuCaret`, `appelsEntrants`, `appelsSortants` |
| Index / types | `findTypesByPrefix`, `fqnsForSimpleName`, `typesDansPaquet`, `constructionsIndexMots` |

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
| `./gradlew :java:benchQualite` | banc qualité complétion (ADR 0016) — corpus versionné 18 positions, baseline signée `bancs/baselines/completion.json` : **recall 1,000 / top-1 1,000 / MRR 1,000** (naïf : 0,611/0,715), zéro saut de liste, p95 31 ms — **ÉCHOUE sur régression** |
| `./gradlew :java:benchUsages` | banc usages (ADR 0014) — rappel 1,000 (oracle), p95 ~62 ms < 100 ms, **CI-bloquant** |
| `bash scripts/preparer-oracle.sh` | construit l'**oracle vendored** du banc croisé (clone `codeide-lsp` épinglé cb809e14 + `:lsp:java:jar`, JVM pur — aucun SDK Android) → `.oracle/` (jamais committé, R6.4) |
| `bash scripts/preparer-projet-android.sh` | télécharge le **projet Android réel du banc** (android.jar 34-ext12 + 16 AARs/jars androidx/material, fermeture transitive vérifiée) → `bancs/projet-android-libs/` (jamais committé, R6.4) |
| `BANC_ANDROID_LIBS=… ./gradlew :java:test --tests jo.codeide.lsp.java.protocole.BancProjetAndroidReelTest` | **banc projet Android réel** (20 tests) : la timeline appareil rejouée au protocole (didOpen avant sync → sync → LIVE-REPUBLISH) puis CHAQUE famille de complétion (import, type + auto-import, membre, statique imbriqué, locale, stub, new, frappe continue, latences) contre les vraies bibliothèques — la sonde de parité Android Studio. Sans les bibliothèques, la suite s'ignore (gate visible) |
| `./gradlew :java:benchParite` | **banc de parité croisée** (étage T, ADR 0016) — le même corpus (18 positions + 4 frappes) rejoué contre le vendored (**boîte noire en sous-processus JVM isolé**, pilote compilé contre des stubs à alarme) et le moteur neuf (in-process) ; baseline signée `bancs/baselines/parite-completion.json` : couverture cahier **1,000 / 1,000**, 165 deltas bruts documentés — **ÉCHOUE** sur couverture en baisse, libellé perdu ou dérive de l'oracle ; oracle absent = banc ignoré avec message (exit 0), la CI le construit d'abord (job `banc-parite` **bloquant**) |
| `bash scripts/kit-bascule.sh` | assemble le **kit de passe bascule sur appareil** (§8.5) : classes du banc + du moteur, ecj patché, dépendances, `banc-bascule.sh` — protocole et tables dans `bancs/GABARIT-BASCULE-APPAREIL.md` (R5 : mesures réelles du propriétaire, jamais simulées) |
| `ANDROID_JAR=… ./gradlew :java:test` | ouvre les gates Android (fiches 033/004/036/043/044/050/053) — la CI télécharge android.jar API 34 (zip officiel) + AAR appcompat |
| `./gradlew :java:test --tests jo.codeide.lsp.java.BancDoubleTamponTest` | **banc V-F1** (R5) : latence nominale vs PENDANT reconstruction tenue (p50/p95 µs, budget ADR « p95 ≤ 2× » imprimé), reconstruction réelle, coût du rejeu (2 passes) — la suite `DoubleTamponTest` (8 tests) verrouille les invariants : 0 figage, 0 réponse périmée, coupures fines, syncs rapides, repli, republication au swap |

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
- **Pas de semanticTokens** : capability non annoncée (règle matrice
  ADR 0015), requête tolérante vide.
- **Quick-fixes mono-fichier** (fiche 063) : le multi-fichiers passe
  par le renommage (N5) et le protocole (N8), jamais par ces édits.
- **`decompiledSource` hors protocole d'époque** (V-F1, résiduel documenté) :
  le RPC sonde les moteurs VIVANTS sans chemin routable (le FQCN ne
  dit pas de quel module il vient — ADR 0215) ; une source décompilée
  pendant un swap peut donc refléter l'époque précédente — la requête
  suivante re-sonde (comportement d'aperçu, jamais un diagnostic).
- **Version 1.0.0 = bascule §8.5 SIGNÉE (30/10/2026)** ; **version 1.1.0 = intégration CodeIDE (§4.4 : régime vivant, option B arbitrée ; superDefinition aligné contrat vendord ; fiche 066)** ; **version 1.3.0 = vague V-E (références multi-fichiers D1, surbrillances composées typées, RPC usagesGroupes ; le 1.2.0 est SAUTÉ : valeur fantôme du vendord proscrite par la fiche 059 I7)** ; **version 1.4.0 = vague V-B (projet Android : modèle v11 + dérivateur disque, référentiel de ressources, classes light R/BuildConfig/Binding/BR, navigation ressource, interop Kotlin — décisions D2/D3/D6 ; `declasserDatabinding` RETIRÉ, rupture d'API consignée)** ; **version 1.5.0 = vague V-F1 (double-tampon + époques de l'environnement : 0 figage / 0 réponse périmée, rejeu des requêtes coupées, annulation fine complétion + usages, republication au swap d'époque, repli `jo.codeide.lsp.java.doubletampon.disabled` — ADR 0029 phase 1, §7.19/§11.1)** : checklist 6/6 du
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
- **Organisation des imports** : API moteur seulement (§7.17) — pas
  encore d'action `source.organizeImports` au fil LSP.
- Le serveur n'écrit **jamais** sur le disque : preview = résultat
  (renommage, actions de code — le client applique).
