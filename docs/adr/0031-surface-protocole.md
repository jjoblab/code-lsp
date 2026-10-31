# ADR 0031 — Surface protocole (semanticTokens, kinds, progression, willRenameFiles, diagnostics enrichis, capabilities maison versionnées)

- **Statut** : Proposé
- **Date** : 2026-10-31
- **Écarts résolus** : G-019, G-021, G-022, G-023 (partie protocole), G-032 (`01-audit-moteur.md` §1.15)

## Contexte

Trous de surface constatés (H15, preuves §1.15) : `semanticTokens` non annoncé
(M2 — requête tolérante vide) ; `codeActionKinds` = QuickFix seul ; pas de
`workDoneProgress` d'indexation ; pas de `workspace/willRenameFiles` ;
`resolveCompletionItem` = identité (`ServeurLangageJava.kt:601–603`) ; diagnostics
sans `relatedInformation`/`codeDescription`. La règle du dépôt reste : « capacité
testée = annoncée ; sinon absente » (ADR 0015) et la compatibilité du client
(`cel-lsp`/`code-editor`, écarts K1–K3) est une contrainte de premier ordre — plusieurs
capacités servies existent déjà que le client ne consomme pas (K2).

## Sources consultées

- `01-audit-moteur.md` §1.15, §1.7 (preuves).
- `02-reference-android-studio.md` §A1 (progression visible « scanning/analyzing »),
  §A6 (semantic tokens = rendu, dumb-aware), §A3 (kinds d'actions).
- Spécification LSP 3.17 (textDocument/semanticTokens, workDoneProgress,
  willRenameFiles, codeAction/resolve, Diagnostic.relatedInformation/codeDescription/
  data) — comportements décrits pour eux-mêmes.
- `docs/ECARTS-SERVEUR.md` §4 (K1–K3 client), `USAGE.md` §7.16/§13 (état actuel des
  conventions d'honnêteté).

## Options

**Option 1 — Extension progressive, versionnée, consommable (recommandée).**
(a) **`semanticTokens`** : calcul sur bindings (types/paramètres/méthodes/champs/
    annotations + modificateurs static/final/abstract) ; delta serveur si le client
    le demande ; annoncé seulement quand le banc de rendu est vert. Valeur : coloration
    sémantique (mots-clés de type vs variables), pas critique mais visible.
(b) **Kinds d'actions** : + `Source`, `SourceFixAll`, `Refactor` (ADR 0027) —
    annoncés au fil des vagues.
(c) **`workDoneProgress`** : jeton de progression pour l'indexation CIDX et la
    première analyse (« Indexation des sources… n/m ») — remplace le silence actuel
    (G-032).
(d) **`willRenameFiles`** : ajuster les imports lors d'un rename de fichier dans
    l'arbre éditeur (le client doit envoyer le geste).
(e) **Diagnostics enrichis** : `relatedInformation` (« requis par Y », fiche 019),
    `codeDescription.href` (doc de l'ID ecj), champ `data` typé (provenance
    ecj/inspection/lint — ADR 0025).
(f) **`codeAction/resolve`** et **`completionItem/resolve`** réels (calcul différé
    des gros edits / des métadonnées).
(g) **Capabilities maison versionnées** : `x-codeide.completionRanking` v2
    (compteur d'invocations, ADR 0026), registre de compatibilité client
    (liste de ce que le client doit consommer — K2).

**Option 2 — Tout exposer maintenant.** Rejeté : viole « testé = annoncé » et noie le
client (K2 déjà ouvert).

**Option 3 — Statu quo.** Rejeté : la coloration sémantique et la progression sont
des demandes utilisateur plausibles (M2 « à réouvrir si la coloration devient un
sujet utilisateur » — l'audit la classe P2, la progression P2).

## Critères et mesures

- semanticTokens : banc de rendu (fichier de référence → vecteur attendu, cas dorés)
  + latence p95 [cible < 25 ms/fichier moyen JVM] ; delta correct sur édition
  partielle (P2-étendu).
- willRenameFiles : imports ajustés exacts sur cas dorés (déplacer une classe publique
  → package + imports).
- Progression : token créé/fermé proprement, annulation respectée.
- Compatibilité client : chaque extension consommée listée dans le registre K2 (le
  prompt client en découle) ; les capacités non consommées restent annoncées
  (inoffensives) ou masquées par négociation.

## Décision proposée

**Option 1**, ordonnée par valeur/coût : (1) `workDoneProgress` indexation + kinds
Source (avec organizeImports, ADR 0027) ; (2) diagnostics enrichis (relatedInformation
+ provenance — roule avec ADR 0025) ; (3) `willRenameFiles` ; (4) `codeAction/resolve`
réel ; (5) `semanticTokens` (avec banc de rendu) ; (6) `completionItem/resolve` ;
(7) capability v2. L'ordre suit le plan 04 (vague V-G) et **chaque brique n'est
annoncée que consommable** — la liste « ce que le client doit consommer » est un
livrable séparé de chaque vague (prêt à devenir le prompt `cel-lsp`/`code-editor`).

## Conséquences

- Le protocole rattrape la référence sur les axes visibles (progression, coloration,
  actions catégorisées) sans casser les clients existants (négociation par
  capabilities).
- Le registre de compatibilité client devient un document vivant (fermeture K2
  progressive).
- Maintenance : chaque brique = tests contractuels presence/absence (ADR 0015).

## Plan de migration progressive

Sept briques = sept PR (une capacité par commit) ; V-G du plan 04 ; aucune
suppression de capability existante (ajouts uniquement).

## Tests de non-régression et critères de sortie

- Tests contractuels par capability (annoncée ⇔ testée) ; cas dorés semanticTokens/
  willRenameFiles/resolve ; USAGE.md tenu à jour par brique (une section vivante) ;
  matrice de capacités régénérée (ADR 0015).

## Risques

- **R1 — Client non prêt (K2)** : négociation par capabilities ; rien n'est retiré ;
  le registre pilote le travail client.
- **R2 — Coût semanticTokens sur ART** : calcul sur l'arbre déjà résolu + cache par
  époque ; mesuré avant annonce.
- **R3 — Dérive de la spec LSP** : épinglage 3.17, extensions maison versionnées
  séparément.
