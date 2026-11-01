# ADR 0031 — Surface protocole (semanticTokens, kinds, progression, willRenameFiles, diagnostics enrichis, capabilities maison versionnées)

- **Statut** : Accepté — **LIVRÉE le 01/11/2026 (v1.11.0 — PV
  `mesures/2026-11-01-vf-finale-vg-protocole.txt`)** : les sept briques
  de l'option 1 sont livrées (a–g), les écarts honnêtes sont consignés
  dans le suivi d'exécution ci-dessous (pas de delta serveur
  semanticTokens ; `codeDescription.href` non livré ; périmètre
  `willRenameFiles` = package + imports du cas doré).
- **Date** : 2026-10-31
- **Écarts résolus** : G-019, G-021, G-022, G-023 (partiel — provenance
  `data` + relatedInformation ; `codeDescription.href` non livré), G-032
  (`01-audit-moteur.md` §1.15)

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

## Suivi d'exécution

- **V-G (01/11/2026, v1.11.0) — LIVRÉE.** Les sept briques :
  **(a) `semanticTokens`** — calcul sur l'arbre RÉSOLU (résolution
  complète, jamais masquée), légende 8 types STANDARD LSP (`class`,
  `interface`, `enum`, `decorator`/annotations, `method` — y compris
  constructeurs, miroir V-D2 —, `property`, `parameter`, `variable`) +
  4 modificateurs (`declaration`, `static`, `final`, `abstract`),
  encodage deltas relatifs 5 entiers/jeton (spec 3.17 — la colonne
  relative suit le DÉBUT du jeton précédent, décodage vérifié au cas
  doré) ; annoncé ⇔ testé (banc de rendu `SurfaceProtocoleVgTest` —
  chaque genre, déclaration + référence, modificateurs) ; latence p95
  MESURÉE 1 ms (cas doré, 30 passes — cible < 25 ms). **Écart honnête** :
  PAS de delta serveur — `range` non servi, le client re-demande le
  vecteur complet (jamais un delta faux) ; l'option (a) « delta si le
  client le demande » est différée jusqu'à demande client réelle.
  **(c) `workDoneProgress` d'indexation (G-032)** — fil démon
  `lspjava-indexation` juste après `initialized` : PRÉCHAUFFAGE de
  l'index de mots (valeur propre — la première requête
  usages/renommage ne paie plus le froid) + jeton `codeide-indexation-*`
  SI ET SEULEMENT SI le client a annoncé `window.workDoneProgress` ;
  `report` après CHAQUE fichier lexé (pourcentage réel, throttlé ~50),
  `end` TOUJOURS (jeton jamais fuité, `finally`) ; kill-switch
  `progression.disabled`. **(e) diagnostics enrichis** — champ `data`
  machine-readable `{provenance: ecj|inspection|lint, code}` (miroir
  ADR 0025) sur CHAQUE diagnostic publié ; `relatedInformation` « requis
  par X » (fiche 019) : la PLAGE de la clause `extends` exigeante voyage
  depuis les positions sources de la référence (nulle si synthétique —
  liens omis honnêtement). **Écart honnête** :
  `codeDescription.href` NON livré (aucun consommateur identifié).
  **(d) `workspace/willRenameFiles` (G-022)** — `fileOperations`
  willRename + didRename annoncés sur `**/*.java` ; le geste reçoit les
  éditions AVANT renommage : déclaration de PAQUET du fichier déplacé
  (plage du NOM seul) + IMPORTS des porteurs (`import`, `import static`,
  segment FQCN seul réécrit — types imbriqués suivent) ; source fraîche
  = tampon ouvert sinon disque ; réponse TOUJOURS un `WorkspaceEdit`
  (vide honnête si FQCN conservé ou hors racines) ; `didRenameFiles`
  invalide présence source + index. **Périmètre honnête** : usages
  QUALIFIÉS complets sans import (`demo.A a = …`) non réécrits — le cas
  doré de la décision est « package + imports » (documenté, USAGE §7.26).
  **(f) `completionItem/resolve` RÉEL (G-021)** — `resolveProvider:
  true` annoncé ; négociation : SI le client annonce `resolveSupport`,
  les items à auto-import perdent l'édition d'import à la liste et
  portent `data.importFqn`/`importStatique`/`uri` — le resolve de
  l'item CHOISI recalcule sur le texte COURANT (`ImportsDifferes`,
  miroir EXACT des règles fiche 029 : déjà importé/`.*`, `java.lang`,
  paquet courant, imbriqué `$` → rien ; ancre après dernier import sinon
  `package` sinon début) ; balayage purement LEXICAL (jamais derrière
  une compilation en vol). SANS annonce : liste complète d'avant V-G à
  l'identique. **(b) kinds Source/Refactor** — livrés depuis V-D (ADR
  0027). **(g) capabilities maison versionnées + registre K2** —
  `x-codeide.plageVisible` **v1** posée à la livraison (V-F4) ; les clés
  V-G de `data` complétion sont ADDITIVES et conditionnées à l'annonce
  client → la version de `x-codeide.completionRanking` RESTE 2 (règle
  R1 : additive ≠ sémantique) ; `docs/REGISTRE-CLIENT-K2.md` posé — le
  document VIVANT des capacités non consommées (l'héritage) et des
  nouvelles par vague, prêt à devenir le prompt `cel-lsp`/
  `code-editor`. Validation (R5) : 9 tests V-G (progression annoncée/
  silencieuse, provenance, willRenameFiles doré + FQCN conservé,
  vecteur exact, latence p95 mesurée, resolve négocié des deux côtés),
  suite complète verte, banc N1 tenu.

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
