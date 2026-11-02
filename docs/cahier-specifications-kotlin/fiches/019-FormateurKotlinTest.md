# Fiche 019 — FormateurKotlinTest

> **Source** : `codeide-lsp` @ `bc11253` — `lsp/kotlin/src/test/kotlin/jo/lspkotlin/session/FormateurKotlinTest.kt` (9 tests, 211 l.).
> **Domaine** : formatage
> **Résumé** : Tests du FORMATEUR BORNÉ Kotlin (L2 — plan 1.3.0, `textDocument formatting` et `rangeFormatting`) — par le SPI (chemin de production D6), moteur RÉEL. Le contrat éprouvé est celui de l'honnêteté D3 : ré-indentation canonique SEULE (le contenu des lignes n'est jamais réécrit), intouchables préservés (jeton multi-ligne : chaîne brute, commentaire de bloc, KDoc), refus sur arbre en erreur, idempotenc
> **Convention** : chaque comportement porte le **nom exact du test source** (identifiant de traçabilité) ; l'attendu le reformule en français lisible, enrichi du contrat D3 quand le nom le porte. Le résumé ci-dessus décrit le contrat de la classe de test.

## Comportements

### `document-sain---ré-indentation-canonique,-lignes-déjà-justes-intactes`

- **Attendu** : document sain : ré indentation canonique, lignes déjà justes intactes.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `idempotence---reformater-un-document-formaté-ne-change-rien`

- **Attendu** : idempotence : reformater un document formaté ne change rien.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `chaîne-brute-multi-lignes---le-contenu-entre-les-délimiteurs-est-inchangé`

- **Attendu** : chaîne brute multi lignes : le contenu entre les délimiteurs est inchangé.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `KDoc-et-commentaire-de-bloc---le-contenu-est-inchangé,-la-ligne-porteuse-s'indente`

- **Attendu** : KDoc et commentaire de bloc : le contenu est inchangé, la ligne porteuse s'indente.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `document-en-erreur-de-syntaxe---liste-d'éditions-VIDE`

- **Attendu** : document en erreur de syntaxe : liste d'éditions VIDE — réponse/effet vide, honnête..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `plage---seules-les-lignes-qui-intersectent-la-plage-bougent`

- **Attendu** : plage : seules les lignes qui intersectent la plage bougent.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `continuation-de-liste---éléments-un-niveau-sous-l'ouvrante,-fermeture-à-la-base`

- **Attendu** : continuation de liste : éléments un niveau sous l'ouvrante, fermeture à la base.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `taille-d'indentation---deux-espaces-par-niveau-sur-demande-du-client`

- **Attendu** : taille d'indentation : deux espaces par niveau sur demande du client.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `document-inconnu-ou-fermé---liste-VIDE,-jamais-d'exception`

- **Attendu** : document inconnu ou fermé : liste VIDE, jamais d'exception — réponse/effet vide, honnête..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)
