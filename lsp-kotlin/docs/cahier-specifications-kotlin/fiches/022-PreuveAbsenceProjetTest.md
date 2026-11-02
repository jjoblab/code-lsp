# Fiche 022 — PreuveAbsenceProjetTest

> **Source** : `codeide-lsp` @ `bc11253` — `lsp/kotlin/src/test/kotlin/jo/lspkotlin/session/PreuveAbsenceProjetTest.kt` (8 tests, 388 l.).
> **Domaine** : diagnostics
> **Résumé** : PREUVE D'ABSENCE SUR LES TYPES DU PROJET (retour utilisateurs 25/10/2026, vague 3 — familles E U1 « collect » et faux négatifs U3 « CalculatriceBudget.consulterInconnu ») : un sélecteur dont le récepteur est un type DU PROJET sans héritage écrit porte un jeu de membres COMPLET — les DÉCLARÉS du scan + les IMPLICITES de Any/Object. L'absence y est PROUVÉE (miroir kotlinc : « Unresolved reference »
> **Convention** : chaque comportement porte le **nom exact du test source** (identifiant de traçabilité) ; l'attendu le reformule en français lisible, enrichi du contrat D3 quand le nom le porte. Le résumé ci-dessus décrit le contrat de la classe de test.

## Comportements

### `object-sans-heritage---le-membre-inexistant-est-signale,-les-declares-et-implicites-muets`

- **Attendu** : object sans heritage : le membre inexistant est signale, les declares et implicites muets — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `class-sans-heritage---la-propriete-inexistante-est-signalee,-membres-et-extensions-muets`

- **Attendu** : class sans heritage : la propriete inexistante est signalee, membres et extensions muets — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `class-avec-heritage-ecrit---aucun-membre-herite-n-est-prouvable-absent`

- **Attendu** : class avec heritage ecrit : aucun membre herite n est prouvable absent.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `data-class---copy-resolu-et-propose-(le-modificateur-data-enfin-lu-du-MODIFIER_LIST)`

- **Attendu** : data class : copy resolu et propose (le modificateur data enfin lu du MODIFIER_LIST).
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `enum---les-membres-implicites-ne-sont-jamais-prouvables-absents`

- **Attendu** : enum : les membres implicites ne sont jamais prouvables absents.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `java-sans-extends---le-membre-inexistant-est-signale,-getters-et-implicites-muets`

- **Attendu** : java sans extends : le membre inexistant est signale, getters et implicites muets — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `java-avec-extends---aucun-membre-herite-n-est-prouvable-absent`

- **Attendu** : java avec extends : aucun membre herite n est prouvable absent.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `java-generique---la-contrainte-du-parametre-n-est-pas-un-heritage`

- **Attendu** : java generique : la contrainte du parametre n est pas un heritage.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)
