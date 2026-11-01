# REGISTRE CLIENT K2 — ce que le client (`cel-lsp`/`code-editor`) doit consommer

> Document VIVANT (vague V-G — ADR 0031 (g) : « le registre de compatibilité
> client devient un document vivant — fermeture K2 progressive »). Chaque
> vague qui SERV une nouvelle capacité l'y inscrit ; il est prêt à devenir
> le prompt d'exécution client (`cel-lsp`/`code-editor`).
>
> Règle de lecture : **annoncé ⇔ testé** côté serveur (ADR 0015) ; côté
> client, une capacité annoncée mais non consommée est un ÉCART K2 — elle
> est inoffensive (aucun retrait), mais la valeur n'arrive pas à
> l'utilisateur.

## 1. Capacités servies NON consommées (K2 ouvert — héritage)

| Capacité | Annonce | Valeur pour l'utilisateur | Statut client |
|---|---|---|---|
| `documentRangeFormatting` | provider booléen | formater la sélection | à consommer |
| `documentOnTypeFormatting` | déclencheurs `}` `;` | formatage à la frappe | à consommer |
| `workspace/symbol` | provider booléen | recherche de symbole projet | à consommer |
| `selectionRange` | provider booléen | expansion de sélection (Shift+Alt+↑) | à consommer |
| `codeLens` (+ resolve paresseux) | provider + resolveProvider | survol des références | à consommer |
| `callHierarchy` | provider booléen | hiérarchie d'appels | à consommer |
| `typeHierarchy` | provider booléen | hiérarchie de types | à consommer |
| `textDocument/superDefinition` (maison) | `experimental` | GO TO Super d'une redéfinition | à consommer |
| `textDocument/decompiledSource` (maison) | `experimental` | tampon lecture-seule des binaires | à consommer |
| `textDocument/usagesGroupes` (maison) | `experimental` `x-codeide.usages` v1 | vue Find Usages groupée par fichier | à consommer |
| `textDocument/statementCompletion` (maison) | — | Ctrl+Shift+Enter (équilibre l'instruction) | à consommer |
| kinds `source.*` / `refactor.*` | `codeActionKinds` | catégorisation du menu d'actions (V-D) | à consommer |

Gardes client existantes (K1/K3, à préserver) : politique préfixe vide ;
attendre la session avant `decompiledSource`.

## 2. Capacités V-G — à consommer (NOUVEAU)

| Capacité | Annonce | Contrat | Travail client |
|---|---|---|---|
| `workDoneProgress` d'indexation | négocié par `window.workDoneProgress` client | `begin` (« Indexation des sources ») → `report` (pourcentage) → `end`, jeton `codeide-indexation-*` | rendre la progression (barre/status) ; ne rien faire sinon — le préchauffage silencieux reste un gain |
| Diagnostics enrichis | champs standard | `data` = `{provenance: "ecj"\|"inspection"\|"lint", code}` ; `relatedInformation` (lien « requis par X ») | filtrer/regrouper par provenance ; rendre le lien cliquable |
| `workspace/willRenameFiles` | `workspace.fileOperations.willRename` (`**/*.java`) | éditions (package + imports) AVANT le renommage ; réponse toujours un `WorkspaceEdit` (vide honnête) | envoyer le geste de l'arbre éditeur (rename/move de `.java`) et appliquer l'edit pendant le geste ; envoyer `didRenameFiles` ensuite |
| `textDocument/semanticTokens` | légende + full (pas de delta) | vecteur deltas relatifs, 5 entiers/jeton ; types `class, interface, enum, decorator, method, property, parameter, variable` ; modificateurs `declaration, static, final, abstract` | coloration sémantique (mots-clés de type vs variables) ; re-demander le vecteur complet à chaque édition (pas de delta serveur) |
| `completionItem/resolve` (imports différés) | `resolveProvider: true` | SI le client annonce `textDocument.completion.completionItem.resolveSupport`, les items à auto-import portent `data.importFqn`/`importStatique`/`uri` SANS l'édition d'import — le resolve la calcule | annoncer `resolveSupport` et résoudre l'item CHOISI avant application (additionalTextEdits) ; sans annonce, la liste reste complète (aucun travail) |
| `$/codeide/plageVisible` (maison) | `experimental` `x-codeide.plageVisible` v1 | notification (uri, offsets absolus start/end) : la zone affichée | envoyer la zone visible (défilement + ouverture, throttlé côté client) ; bénéfice : diagnostics du visible d'abord sur les grands fichiers |

## 3. Protocole maison — versions

| Clé | Version | Dernière évolution |
|---|---|---|
| `x-codeide.completionRanking` | **2** | V-C3 (compteur d'invocations, ADR 0026 (a)). Les clés V-G (`importFqn`, `importStatique`, `uri` dans `data` des items) sont ADDITIVES et conditionnées à l'annonce client `resolveSupport` : un client ancien voit la liste complète d'avant V-G, AUCUN changement sémantique — pas de montée de version (règle ADR 0015 R1 : la version monte sur évolution SÉMANTIQUE, pas additive). |
| `x-codeide.usages` | 1 | V-E (multi-fichiers, groupes, plafond). |
| `x-codeide.plageVisible` | 1 | V-F4 (ADR 0029 phase 4 — posée à la livraison, v1.11.0). |

## 4. Ordre de consommation recommandé (valeur/coût)

1. `semanticTokens` + `plageVisible` (rendu visible immédiat, aucun risque) ;
2. `willRenameFiles` (geste arbre éditeur existant côté hôte) ;
3. kinds `source.*`/`refactor.*` + `usagesGroupes` (menus) ;
4. `workDoneProgress` (barre de progression) ;
5. `resolveSupport` complétion (import différé) ;
6. le reste de §1 (navigation riche).
