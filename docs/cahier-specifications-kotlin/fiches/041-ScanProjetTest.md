# Fiche 041 — ScanProjetTest

> **Source** : `codeide-lsp` @ `bc11253` — `lsp/kotlin/src/test/kotlin/jo/lspkotlin/session/ScanProjetTest.kt` (15 tests, 277 l.).
> **Domaine** : index-classpath
> **Résumé** : Tests du scan des racines sources (KP6 — plan de stabilisation LSP §3.2 S0) : contrat fonctionnel (scan paresseux des `.kt`, exclusion du document courant, invalidation au changement de racines) et accès CONCURRENTS — deux sessions simultanées doivent voir un état cohérent : la paire (racines, scannees) est mutée sous le verrou du scan, la visibilité mémoire est garantie par les champs volatils (m
> **Convention** : chaque comportement porte le **nom exact du test source** (identifiant de traçabilité) ; l'attendu le reformule en français lisible, enrichi du contrat D3 quand le nom le porte. Le résumé ci-dessus décrit le contrat de la classe de test.

## Comportements

### `sans-racines---aucune-declaration`

- **Attendu** : sans racines : aucune declaration.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `scan-paresseux---les-kt-des-racines-nourrissent-le-scan`

- **Attendu** : scan paresseux : les kt des racines nourrissent le scan.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `document-courant-exclu---son-doublon-du-scan-ne-revient-pas`

- **Attendu** : document courant exclu : son doublon du scan ne revient pas.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `changement-de-racines---invalidation-puis-re-scan`

- **Attendu** : changement de racines : invalidation puis re scan.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `racines-identiques---no-op-chaud,-meme-resultat`

- **Attendu** : racines identiques : no op chaud, meme resultat.
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `amorce-warm---declarations-joint-le-scan-lancé-à-la-définition`

- **Attendu** : amorce warm : declarations joint le scan lancé à la définition.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `incremental-mtime---le-fichier-changé-re-parse,-les-autres-survivent`

- **Attendu** : incremental mtime : le fichier changé re parse, les autres survivent.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `rafraichissement-externe---un-fichier-cree-devient-visible-(L1)`

- **Attendu** : rafraichissement externe : un fichier cree devient visible (L1).
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `rafraichissement-externe---un-fichier-modifie-remplace-ses-declarations-(L1)`

- **Attendu** : rafraichissement externe : un fichier modifie remplace ses declarations (L1).
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `sans-racines---rafraichir-est-un-no-op-(L1)`

- **Attendu** : sans racines : rafraichir est un no op (L1).
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `acces-concurrents---chaque-session-voit-un-etat-coherent-(KP6)`

- **Attendu** : acces concurrents : chaque session voit un etat coherent (KP6).
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `progression---chaque-fichier-notifie,-le-total-est-connu-(marche-d-abord)`

- **Attendu** : progression : chaque fichier notifie, le total est connu (marche d abord).
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `annulation---l-acquis-est-publie,-le-passage-est-abouti-meme-en-partiel`

- **Attendu** : annulation : l acquis est publie, le passage est abouti meme en partiel.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `reprise-apres-annulation---rafraichir-complete-le-passage`

- **Attendu** : reprise apres annulation : rafraichir complete le passage.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `projet-sans-sources---passage-abouti,-pas-de-boucle-d-amorcage`

- **Attendu** : projet sans sources : passage abouti, pas de boucle d amorcage.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)
