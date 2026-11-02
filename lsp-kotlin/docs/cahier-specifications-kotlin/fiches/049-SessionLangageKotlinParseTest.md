# Fiche 049 — SessionLangageKotlinParseTest

> **Source** : `codeide-lsp` @ `bc11253` — `lsp/kotlin/src/test/kotlin/jo/lspkotlin/session/SessionLangageKotlinParseTest.kt` (26 tests, 381 l.).
> **Domaine** : cycle-vie-lsp
> **Résumé** : Tests du contrat de session K2 (LSP_KOTLIN.md §5, K2 « session LSP minimale ») — par le SPI ([ServiceMoteurLangageKotlin.unique]), PAS par le type concret : c'est le chemin de production de l'hôte (D6 : le pack futur se branche là, et uniquement là). Comportements éprouvés (fixtures calibrées sur le comportement RÉEL de la grammaire vendorée, sondé avant écriture) : - tolérance : aucun texte ne lè
> **Convention** : chaque comportement porte le **nom exact du test source** (identifiant de traçabilité) ; l'attendu le reformule en français lisible, enrichi du contrat D3 quand le nom le porte. Le résumé ci-dessus décrit le contrat de la classe de test.

## Comportements

### `didOpen-invalide---une-erreur-de-syntaxe,-plage-et-message-du-parseur`

- **Attendu** : didOpen invalide : une erreur de syntaxe, plage et message du parseur.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `didChange-corrige---les-diagnostics-disparaissent`

- **Attendu** : didChange corrige : les diagnostics disparaissent.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `didClose---le-document-est-oublie`

- **Attendu** : didClose : le document est oublie.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `document-inconnu---liste-vide,-jamais-d'exception`

- **Attendu** : document inconnu : liste vide, jamais d'exception — réponse/effet vide, honnête..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `import-inutilise---signale`

- **Attendu** : import inutilise : signale — l'émission est attendue (preuve d'absence/erreur établie)..
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `nom-present-en-commentaire-seulement---l'import-reste-inutilise`

- **Attendu** : nom present en commentaire seulement : l'import reste inutilise.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `import-utilise---aucun-diagnostic-(sous-rapport)`

- **Attendu** : import utilise : aucun diagnostic (sous rapport) — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `joker---jamais-signale`

- **Attendu** : joker : jamais signale — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `imports-conflictuels---signale-sur-le-plus-tardif-avec-les-deux-noms`

- **Attendu** : imports conflictuels : signale sur le plus tardif avec les deux noms — l'émission est attendue (preuve d'absence/erreur établie)..
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `meme-import-repete---pas-un-conflit-(sous-rapport)`

- **Attendu** : meme import repete : pas un conflit (sous rapport) — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `alias-inutilise---signale`

- **Attendu** : alias inutilise : signale — l'émission est attendue (preuve d'absence/erreur établie)..
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `alias-utilise---aucun-diagnostic`

- **Attendu** : alias utilise : aucun diagnostic.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `script-kts---parse-et-diagnostics-en-mode-script`

- **Attendu** : script kts : parse et diagnostics en mode script.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `didChange-sans-didOpen---tolerant,-vaut-une-ouverture`

- **Attendu** : didChange sans didOpen : tolerant, vaut une ouverture.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `fermeture---tout-abandonne,-mutations-posterieures-no-ops`

- **Attendu** : fermeture : tout abandonne, mutations posterieures no ops.
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `sessions-distinctes---etats-independants`

- **Attendu** : sessions distinctes : etats independants.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `import-inutilise---porte-le-tag-INUTILE-(KP15)`

- **Attendu** : import inutilise : porte le tag INUTILE (KP15).
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `erreur-de-syntaxe---aucun-tag-(KP15,-sous-rapport)`

- **Attendu** : erreur de syntaxe : aucun tag (KP15, sous rapport) — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `imports-conflictuels---information-liee-sur-la-PREMIERE-directive-(KP15)`

- **Attendu** : imports conflictuels : information liee sur la PREMIERE directive (KP15).
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `import-inutilise---aucune-information-liee-(KP15)`

- **Attendu** : import inutilise : aucune information liee (KP15).
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `plages-pliables---classe,-fonctions,-imports-et-KDoc`

- **Attendu** : plages pliables : classe, fonctions, imports et KDoc.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `plages-pliables---document-inconnu,-ferme-ou-vide,-jamais-d-exception`

- **Attendu** : plages pliables : document inconnu, ferme ou vide, jamais d exception — aucune exception levée (tolérance d'édition)..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `survol-riche---la-KDoc-du-document-courant-accompagne-la-signature-(S2)`

- **Attendu** : survol riche : la KDoc du document courant accompagne la signature (S2).
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `survol-sans-KDoc---documentation-absente,-signature-seule`

- **Attendu** : survol sans KDoc : documentation absente, signature seule.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `rafraichirSources---une-declaration-externe-devient-resolvable-(L1)`

- **Attendu** : rafraichirSources : une declaration externe devient resolvable (L1).
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `rafraichirSources---session-fermee,-no-op-honnete-(L1)`

- **Attendu** : rafraichirSources : session fermee, no op honnete (L1).
- **Classement** : conforme (borne conservatrice « preuve ou silence »)
