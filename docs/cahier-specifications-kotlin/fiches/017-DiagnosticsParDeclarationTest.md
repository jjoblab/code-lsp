# Fiche 017 — DiagnosticsParDeclarationTest

> **Source** : `codeide-lsp` @ `bc11253` — `lsp/kotlin/src/test/kotlin/jo/lspkotlin/session/DiagnosticsParDeclarationTest.kt` (8 tests, 190 l.).
> **Domaine** : diagnostics
> **Résumé** : Tests du chantier P1-A « diagnostics par déclaration » (garde d'édition du texte vivant) — un document portant des marqueurs d'erreur n'est plus écarté EN BLOC : seules les DÉCLARATIONS dont le sous-arbre parse proprement sont diagnostiquées, et `kt.unresolved` (preuve d'ABSENCE) porte des gardes dédiées : - un nom DÉCLARÉ n'importe où dans le document (déclaration en cours de frappe ou avalée par
> **Convention** : chaque comportement porte le **nom exact du test source** (identifiant de traçabilité) ; l'attendu le reformule en français lisible, enrichi du contrat D3 quand le nom le porte. Le résumé ci-dessus décrit le contrat de la classe de test.

## Comportements

### `region-propre---la-frappe-au-bas-du-fichier-n'eteint-pas-les-preuves-du-dessus`

- **Attendu** : region propre : la frappe au bas du fichier n'eteint pas les preuves du dessus.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `region-propre---preuve-locale-(retour-manquant)-pendant-l'edition-ailleurs`

- **Attendu** : region propre : preuve locale (retour manquant) pendant l'edition ailleurs.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `classe---membre-propre-diagnostique,-membre-casse-muet`

- **Attendu** : classe : membre propre diagnostique, membre casse muet — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `nom-declare-en-cours-de-frappe---jamais-signale-non-resolu`

- **Attendu** : nom declare en cours de frappe : jamais signale non resolu — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `zone-import-brisee---preuve-d'absence-muette-sur-tout-le-document`

- **Attendu** : zone import brisee : preuve d'absence muette sur tout le document — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `en-tete-de-classe-brise---absence-muette-dans-les-membres-propres`

- **Attendu** : en tete de classe brise : absence muette dans les membres propres — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `surcharge-en-cours-de-frappe---candidate-d'arite-ecartee`

- **Attendu** : surcharge en cours de frappe : candidate d'arite ecartee.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `document-propre---comportement-inchange`

- **Attendu** : document propre : comportement inchange.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)
