# 01 — Responsabilités éditeur ↔ serveur de langage : rapport d'étude

> Étude demandée par le propriétaire le 30/10/2026 : « Avant toute décision de refonte
> je veux que tu consultes Android Studio pour voir comment ils ont fait, quelle est la
> responsabilité de chacun entre l'éditeur et les LSP ? Si l'éditeur doit seulement être
> générique, ne pas savoir quel LSP est connecté, etc. Avoir une seule source de vérité. »
>
> Branche `etude/moteur-java` du dépôt `jjoblab/code-lsp`. Document d'étude ; la
> décision qui en découle (correctif v1.1.4, pas de refonte de l'éditeur) est consignée
> dans `docs/INTEGRATION-CODEIDE.md` §12.

## Cadre et méthode

- **Terrain principal** : `JetBrains/intellij-community` @ `865067b0a7f` (clone sparse
  local de l'étude PSI — même terrain que `moteur-java-psi/01-architecture-psi.md`).
  Citations `fichier:ligne` vérifiables dans le working tree local.
- **Terrain documentaire** : « Language Server Protocol (LSP) », IntelliJ Platform
  Plugin SDK, <https://plugins.jetbrains.com/docs/intellij/language-server-protocol.html>
  (consulté le 30/10/2026) ; spécification LSP 3.17
  <https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification>.
- **Chaîne CodeIDE auditée** (lecture directe, commits épinglés) :
  `jjoblab/code-editor` @ `f310a39` (cel-lsp 4.14.x), `jjoblab/CodeIDE` @ `f6ebddd`
  (v0.204.3), `jjoblab/codeide-lsp` @ `2a99de8` (hôte 1.23.3), `jjoblab/code-lsp` @
  `7760450` (moteur 1.1.3). Logs appareil : `codeide-logs-2026-10-30-105910.zip`
  (moto g06, Android 15, CodeIDE 0.204.3 debug, 463 entrées dont 443 `LspEditeur`).
- **Méthode** : lecture du code avec citations `fichier:ligne` ; comportements
  observés EXTRAITS des logs appareil (aucune interprétation non étayée) ; ce qui
  n'a pas pu être vérifié est étiqueté « à vérifier ».

## Synthèse du verdict

| # | Question du propriétaire | Verdict |
|---|---|---|
| V1 | L'éditeur doit-il être générique, sans savoir quel LSP est connecté ? | **OUI** — c'est le modèle d'IntelliJ ET de VS Code ; cel-lsp l'est déjà |
| V2 | Qui est responsable de quoi ? | Table §3 : l'éditeur détient la vérité du TEXTE, le serveur celle du LANGAGE, l'adaptateur ne décide rien |
| V3 | « Une seule source de vérité » | Deux vérités DISJOINTES (texte / langage) qui ne se chevauchent jamais — règle d'or §4 |
| V4 | Faut-il refondre l'éditeur ? | **NON** — l'architecture cel-lsp/cel-lsp-api est déjà au modèle IntelliJ (§5). Les pannes du 30/10 viennent du SERVEUR (régime instantané sur ART), pas de la politique de l'éditeur |

---

## 1. Comment fait Android Studio (IntelliJ Platform)

Android Studio est IntelliJ IDEA Community + plugins Android : ses choix d'architecture
sont ceux de la plateforme IntelliJ.

### 1.1 L'éditeur est agnostique par construction

Le noyau d'édition — `Editor`, `Document`, `MarkupModel`, caret, undo, plis — ne
connaît AUCUNE langue. Le contrat du document est purement textuel :

> `platform/core-api/src/com/intellij/openapi/editor/Document.java:21` —
> « Represents the contents of a text file loaded into memory and possibly opened in
> an IDE text editor. Line breaks in the document text are always normalized as
> single `\n` characters […] »

Toute fonctionnalité DÉPENDANTE DE LA LANGUE est un **point d'extension** que le noyau
consomme sans connaître son producteur :

- diagnostics → `Annotator` : « Implemented by a custom language plugin to add
  annotations to files in the language »
  (`platform/analysis-api/src/com/intellij/lang/annotation/Annotator.java:14-15`) ;
  l'ÉDITEUR rend les annotations (soulignés, gouttière), le PLUGIN les calcule ;
- complétion → `CompletionContributor.fillCompletionVariants(CompletionParameters,
  CompletionResultSet)` (`platform/analysis-api/src/com/intellij/codeInsight/completion/
  CompletionContributor.java:166`) : la machinerie popup/classement appartient au noyau,
  les candidats viennent des contributeurs ;
- navigation → `GotoDeclarationHandler`
  (`platform/analysis-api/src/com/intellij/codeInsight/navigation/actions/GotoDeclarationHandler.java`).

L'éditeur ne sait **ni quelle langue** ni **quel plugin** alimente ces points
d'extension : il rend ce qu'ils produisent. C'est la réponse directe à la question V1.

### 1.2 Le Java d'Android Studio n'est PAS un LSP

Le support Java d'Android Studio est le plugin natif IntelliJ (`java/java-psi-api`,
`java/java-psi-impl`, `java/java-impl…` du clone local) : l'arbre PSI est la source de
vérité du langage, consommée par les points d'extension ci-dessus. La résolution
s'appuie sur le **SDK du module** : JDK (image jrt, sur machine de bureau) +
`android.jar` de la plateforme Android visée — la « plateforme » suit le module, pas
l'éditeur.

Point décisif pour nous : l'API LSP d'IntelliJ n'est **pas disponible dans Android
Studio** (module `com.intellij.modules.lsp`, IDEs commerciaux uniquement — doc SDK
citée). Android Studio n'utilise donc LSP pour RIEN : tout passe par les points
d'extension natifs. LSP n'intervient chez JetBrains que comme **porte d'entrée pour
les langes tiers** (§1.3).

### 1.3 L'API LSP d'IntelliJ : un ADAPTATEUR, jamais un refacto de l'éditeur

Depuis 2023.2, la plateforme offre une API client LSP (`LspServerDescriptor`, renommé
`LspClientDescriptor` en 2026.1.4 ; `LspServerSupportEntryPoint` →
`LspIntegrationProvider`). Le montage documenté :

1. le plugin déclare un `LspIntegrationProvider` (point d'extension) ;
2. `fileOpened()` démarre le serveur pour les fichiers que `isSupportedFile()` accepte ;
3. **le client LSP implémente les points d'extension standards** (complétion,
   navigation, annotations…) AU-DESSUS des requêtes LSP.

Autrement dit : JetBrains n'a PAS mis de LSP dans le noyau éditeur ; ils ont écrit un
ADAPTATEUR qui se branche sur les mêmes points d'extension qu'un plugin natif. Un
serveur de langage apparaît à l'éditeur comme « juste un autre plugin de langue ».

La liste des fonctionnalités LSP supportées, version par version (doc SDK) —
surlignage d'utilisations 2025.3, plis 2025.2.2, inlay hints 2025.2.2, aide de
signature 2025.3, formatage de plage 2026.1, organize imports 2026.1, renommage
2026.1.1 — montre la doctrine : **chaque fonctionnalité LSP est greffée UNE PAR UNE
sur UNE surface éditeur standard**, jamais l'inverse. La doctrine est énoncée noir sur
blanc : « LSP is not a replacement for the existing language API but rather an added
value » (blog JetBrains, LSP for plugin developers, 2023).

### 1.4 Ce qu'on retient d'Android Studio (règles R1–R6)

- **R1** — le noyau éditeur ne connaît aucune langue (Document = texte pur) ;
- **R2** — chaque fonctionnalité langue est un point d'extension ; l'éditeur rend,
  le plugin calcule ;
- **R3** — un client LSP est un producteur D'EXTENSION comme un autre : l'éditeur ne
  sait pas qu'un LSP est connecté ;
- **R4** — la plateforme de résolution suit le MODULE (JDK + android.jar), pas
  l'éditeur ;
- **R5** — les fonctionnalités sont greffées une par une, chacune sur une surface
  standard ;
- **R6** — LSP = valeur ajoutée pour les langues sans plugin natif, pas une
  réarchitecture de l'éditeur.

## 2. Comment fait VS Code (la référence LSP pure)

VS Code est l'éditeur canonique du LSP : son noyau est 100 % générique et TOUTE
l'intelligence de langue vient des serveurs.

- **Document** : l'éditeur est la source de vérité du texte ; chaque édition part en
  `textDocument/didChange` ; « Before requesting information from the server (e.g.
  textDocument/completion or textDocument/signatureHelp), the client must ensure that
  the document's state is synchronized with the server » (spec 3.17, § textDocument/
  didChange — la synchronisation est une obligation CLIENT).
- **Diagnostics** : calculés et POUSSÉS par le serveur (`textDocument/
  publishDiagnostics`) ; l'éditeur ne fait que rendre (squigglés + panneau
  Problèmes). Un éditeur n'invente jamais un diagnostic de langue.
- **Complétion** : le serveur propose, le client filtre/classe localement (fuzzy) et
  affiche ; les caractères déclencheurs sont négociés à l'initialize
  (`completionProvider.triggerCharacters`) ;
- **Capabilities** : contrat d'honnêteté — le client n'active une surface que si le
  serveur l'annonce ; un serveur n'annonce que ce qu'il peut servir.

VS Code possède aussi des couches LOCALES non-langue (coloration TextMate, surlignage
d'occurrences du mot sous le curseur) : c'est la « couche 1 », distincte des couches
LSP et légitime — à condition de ne jamais la confondre avec elles dans les retours
terrain (voir §5.3).

## 3. La table des responsabilités, fonctionnalité par fonctionnalité

| Fonctionnalité | Source de vérité | Qui calcule | Qui rend/interagit | Détail |
|---|---|---|---|---|
| Texte, undo, caret, sélection | **Éditeur** | éditeur | éditeur | le serveur reçoit des copies (didChange plein texte chez nous) |
| Coloration lexicale | éditeur (grammaire/TextMate) | éditeur | éditeur | couche 1, indépendante du LSP |
| Coloration sémantique | serveur | serveur (`semanticTokens`) | éditeur (rend les spans) | |
| Diagnostics | **Serveur** | serveur (publishDiagnostics) | éditeur (squigglés, gouttière, panneau) | l'éditeur n'invente RIEN |
| Complétion | serveur (candidats) | serveur (base, contexte) | éditeur (filtre flou local, classement, popup, déclencheurs) | la POLITIQUE d'affichage (préfixe vide sans déclencheur → pas de popup) est un choix d'UX éditeur, légitime |
| Aide de signature | serveur | serveur | éditeur (popup) | |
| Go to / définition / implémentations | **Serveur** | serveur (résolution) | éditeur (saut, ouverture de fichier) | |
| Références / surlignage de document | serveur | serveur (`documentHighlight`) | éditeur | distinct du surlignage lexical local (couche 1) |
| Survol (hover) | serveur | serveur | éditeur (popup) | |
| Inlay hints | serveur | serveur | éditeur (rendu inlay) | |
| Formatage, organise imports | serveur | serveur (TextEdit) | éditeur (applique) | |
| Code actions / quick fixes | serveur | serveur | éditeur (ampoule, applique l'édition) | |
| Renommage | serveur | serveur (WorkspaceEdit) | éditeur (applique, conflictualité) | |

## 4. La règle d'or — « une seule source de vérité »

Il n'existe pas UNE mais DEUX vérités, disjointes par construction :

> **L'éditeur est la source unique de vérité du TEXTE** (document, caret, undo,
> sélection, rendu). **Le serveur est la source unique de vérité du LANGAGE**
> (parse, résolution, diagnostics, complétion, navigation, sémantique). **L'adaptateur
> (client LSP) est un traducteur pur : il ne décide RIEN du langage et ne détient
> RIEN du texte.**

Toute « politique » se juge à cette aune : une politique d'AFFICHAGE (quand ouvrir le
popup, débouncer, filtrer localement) appartient à l'éditeur ; une politique de
LANGAGE (qu'est-ce qu'un diagnostic, quels candidats, quelle cible) appartient au
serveur. Une politique éditeur qui FABRIQUERAIT des données de langue (diagnostics
locaux, candidats synthétiques non documentés) violerait la règle ; ce n'est pas le
cas de cel-lsp (vérifié §5).

## 5. Audit de la chaîne CodeIDE à la lumière de la référence

Maillon par maillon, commits du 30/10/2026 :

### 5.1 cel-lsp-api (`jo.codeeditor.lang`) — l'équivalent de nos points d'extension ✓

`Language`, `Analyzer`, `provider/*` (`CompletionProvider`, `DiagnosticsProvider`…),
`model/*` (Diagnostic, CompletionItem…), `host/EditorHost` : c'est le SPI générique
que cel-core/cel-ui consomment — miroir exact des points d'extension IntelliJ (R2).
L'éditeur (EditorView/EditorRenderer) ne connaît que ce SPI.

### 5.2 cel-lsp (`jo.codeeditor.lsp`) — l'adaptateur ✓

`LspCompletionProvider` traduit `textDocument/completion` → `CompletionProvider` ;
`LspDiagnosticsManager` traduit `publishDiagnostics` → `session.setDiagnostics` +
`notifyDiagnosticsChanged` ; `LspDefinitionProvider`, `LspHoverProvider`,
`LspInlayHintProvider`… font de même. Montage identique à l'adaptateur LSP
d'IntelliJ (R3) : l'éditeur ne sait pas qu'un LSP est connecté. La traduction des
diagnostics est fidèle (sévérité LSP→interne 1↔3, tag Unnecessary transporté,
`LspDiagnosticsManager.java:62-82`).

### 5.3 Les couches locales de l'éditeur — légitimes mais à distinguer (écart e2)

L'éditeur rend UN surlignage d'occurrences du mot sous le curseur par lui-même
(couche 1, comme VS Code). Sur le moto g06, ce surlignage a fonctionné PENDANT que
`textDocument/documentHighlight` répondait vide (50 requêtes, 50 « no results » dans
les logs du 30/10 10:59) : l'utilisateur a cru la fonctionnalité LSP « fonctionner
bien ». La couche 1 est légitime ; il faut simplement la DISTINGUER dans les passes
terrain (le documentHighlight LSP se vérifie sur un symbole multi-mots ou une
liaison champ↔usage, pas sur un mot isolé).

### 5.4 CodeIDE (`CoordonnateurLsp`) — l'hôte ✓

Branchement par onglet d'un `LspEditor` cel-lsp sur la vue, définitions de serveurs
par langue, journal honnête « LSP X connecté — coloration seule » en cas d'échec :
c'est le rôle « hôte » d'IntelliJ (`fileOpened`/`isSupportedFile`), sans fuite de
langage dans l'éditeur.

### 5.5 Verdict

L'architecture CodeIDE est **déjà au modèle IntelliJ/VS Code** : éditeur générique,
SPI de fonctionnalités, adaptateur LSP pur, hôte applicatif. **Aucune refonte de
l'éditeur n'est nécessaire.** L'hypothèse « la politique cel-lsp interfère avec les
fonctionnalités LSP » est réfutée pour les pannes du 30/10 : la politique d'affichage
de cel-lsp (préfixe vide sans caractère déclencheur → pas de popup —
`EditorCompletionPopup`, même convention que VS Code) explique seulement l'absence de
popup après « `import ` » + espace, PAS les réponses vides. Les réponses vides
(diagnostics analytiques muets, go-to vide, complétion appauvrie en mots-clés,
documentHighlight/inlayHint vides sur fichier SAIN) viennent du **serveur en régime
instantané sur ART** : cause racine isolée et corrigée dans
`docs/INTEGRATION-CODEIDE.md` §12 (correctif v1.1.4).

Deux écarts véritables subsistent, tous deux côté SERVEUR (écart e1) ou à documenter
(e2) :

- **e1 — Capacités annoncées non servies en régime dégradé** : le serveur annonce
  `definitionProvider=YES` etc. même quand le moteur tourne en régime instantané et
  répond vide — violation du contrat d'honnêteté des capabilities (VS Code, §2). Le
  correctif v1.1.4 réduit la fenêtre d'occurrence (le régime plein s'active dès qu'un
  jar de plateforme est au classpath, cf. §12) ; la question « capabilities
  dynamiques » reste ouverte (LSP fige les capabilities à l'initialize ; la voie
  recommandée est le régime plein par défaut + repli documenté, pas un mensonge de
  capability).
- **e2 — Couche 1 non distinguée dans les retours terrain** (§5.3) : à consigner
  dans la méthode de passe appareil.

## 6. Recommandations

1. **Ne pas refondre l'éditeur** — il est au modèle (V4) ; l'effort va au serveur.
2. **Corriger le régime du moteur sur ART** (v1.1.4) : la plateforme est résoluble
   depuis le classpath (android.jar) sans image jrt — miroir de la règle R4
   d'Android Studio (« la plateforme suit le module », jrt SUR BUREAU, android.jar
   SUR APPAREIL). Détail et exécution : INTEGRATION-CODEIDE §12.
3. **Méthode « une fonctionnalité à la fois »** (demande du propriétaire) : pour
   CHAQUE fonctionnalité, vérifier bout-en-bout serveur → protocole → adaptateur →
   rendu, avec un témoin observable à chaque maillon (le tableau §12.1 est le gabarit).
4. **Documenter les politiques d'affichage de l'éditeur** (popup sur préfixe vide,
   hover au appui-long, débounces) côté documentation d'intégration — pour que les
   retours terrain ne soient plus attribués à des « interférences ».
5. **Décision ouverte au propriétaire** : capacités dynamiques honnêtes vs régime
   plein garanti par construction (recommandé : la seconde).
