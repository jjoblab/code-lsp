# Fiche 050 — SessionLangageKotlinPreviewsTest

> **Source** : `codeide-lsp` @ `bc11253` — `lsp/kotlin/src/test/kotlin/jo/lspkotlin/session/SessionLangageKotlinPreviewsTest.kt` (16 tests, 527 l.).
> **Domaine** : autre
> **Résumé** : Tests du contrat de session C1 (preview Compose — `previews(uri)`, PREVIEW.md §4.4) — par le SPI (chemin de production D6) : la preuve FQCN des annotations PAR LES IMPORTS, les paramètres `@Preview` tranchés dans la source, la réduction du corps en ARBRE D'APPELS (littéraux distingués, lambda final = contenu, expressions honnêtes), l'INLINING des composables du projet (cycles, budgets, paramétrés,
> **Convention** : chaque comportement porte le **nom exact du test source** (identifiant de traçabilité) ; l'attendu le reformule en français lisible, enrichi du contrat D3 quand le nom le porte. Le résumé ci-dessus décrit le contrat de la classe de test.

## Comportements

### `preview-simple---corps-reduit-en-arbre-d'appels`

- **Attendu** : preview simple : corps reduit en arbre d'appels.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `parametres-d'annotation---valeurs-tranchees-dans-la-source`

- **Attendu** : parametres d'annotation : valeurs tranchees dans la source.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `sans-Composable,-sans-Preview,-homonyme-d'un-autre-paquet---absents`

- **Attendu** : sans Composable, sans Preview, homonyme d'un autre paquet : absents.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `import-joker-et-forme-qualifiee---preuve-FQCN-tenue`

- **Attendu** : import joker et forme qualifiee : preuve FQCN tenue.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `plusieurs-previews-et-membre-de-classe---ordre-du-document`

- **Attendu** : plusieurs previews et membre de classe : ordre du document.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `document-inconnu---liste-vide`

- **Attendu** : document inconnu : liste vide — réponse/effet vide, honnête..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `lambda-final---contenu-imbrique,-arguments-distingués`

- **Attendu** : lambda final : contenu imbrique, arguments distingués.
- **Classement** : heuristique (règle maison, sous-ensemble de la spécification)

### `composable-du-projet---corps-inline-depuis-le-disque`

- **Attendu** : composable du projet : corps inline depuis le disque.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `cycle-de-composables---genre-CYCLE,-jamais-de-recursivite`

- **Attendu** : cycle de composables : genre CYCLE, jamais de recursivite.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `composable-parametre---genre-PARAMETRES-(C1-n'inlinie-pas)`

- **Attendu** : composable parametre : genre PARAMETRES (C1 n'inlinie pas).
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `chaine-d'inline-profonde---genre-BUDGET-au-dela-de-la-borne`

- **Attendu** : chaine d'inline profonde : genre BUDGET au dela de la borne.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `appel-a-une-fonction-du-projet-NON-composable---pas-d'inline`

- **Attendu** : appel a une fonction du projet NON composable : pas d'inline.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `ligne-annotation---la-preview-porte-la-ligne-de-son-arobase-Preview`

- **Attendu** : ligne annotation : la preview porte la ligne de son arobase Preview.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `ligne-annotation-multi-lignes---la-ligne-reste-celle-du-arobase`

- **Attendu** : ligne annotation multi lignes : la ligne reste celle du arobase.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `plusieurs-previews---lignes-distinctes-dans-l'ordre-du-document`

- **Attendu** : plusieurs previews : lignes distinctes dans l'ordre du document.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `fermeture-de-session---previews-vides-sans-jamais-lever`

- **Attendu** : fermeture de session : previews vides sans jamais lever — aucune exception levée (tolérance d'édition)..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)
