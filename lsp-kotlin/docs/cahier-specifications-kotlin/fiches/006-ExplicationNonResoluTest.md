# Fiche 006 — ExplicationNonResoluTest

> **Source** : `codeide-lsp` @ `bc11253` — `lsp/kotlin/src/test/kotlin/jo/lspkotlin/diagnostics/ExplicationNonResoluTest.kt` (4 tests, 291 l.).
> **Domaine** : diagnostics
> **Résumé** : JOURNAL « EXPLAIN » DES `kt.unresolved` ÉMIS (mission fiabilisation, étape A.1 — instrumentation) : chaque émission est consignée en niveau FINE — forme syntaxique de la référence, issue de la sélection par types complets (ADR 0223, la « voie de preuve » qui a conclu à l'absence), récepteur TYPÉ et état de l'univers des preuves (scan projet, index portant, régime script). Ce test EST la « commande
> **Convention** : chaque comportement porte le **nom exact du test source** (identifiant de traçabilité) ; l'attendu le reformule en français lisible, enrichi du contrat D3 quand le nom le porte. Le résumé ci-dessus décrit le contrat de la classe de test.

## Comportements

### `nom-nu---le-journal-rend-la-forme,-l-issue-INCONNU-et-l-etat-des-preuves`

- **Attendu** : nom nu : le journal rend la forme, l issue INCONNU et l etat des preuves.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `selecteur-qualifie---le-journal-rend-l-ABSENCE_PROUVEE-et-le-recepteur-type`

- **Attendu** : selecteur qualifie : le journal rend l ABSENCE_PROUVEE et le recepteur type.
- **Classement** : heuristique (règle maison, sous-ensemble de la spécification)

### `references-resolues---le-journal-ne-dit-rien`

- **Attendu** : references resolues : le journal ne dit rien.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `journal-eteint-par-defaut---aucun-rendu-sans-ecouteur-FINE`

- **Attendu** : journal eteint par defaut : aucun rendu sans ecouteur FINE.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)
