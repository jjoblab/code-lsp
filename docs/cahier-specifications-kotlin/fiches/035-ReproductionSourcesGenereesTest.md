# Fiche 035 — ReproductionSourcesGenereesTest

> **Source** : `codeide-lsp` @ `bc11253` — `lsp/kotlin/src/test/kotlin/jo/lspkotlin/session/ReproductionSourcesGenereesTest.kt` (2 tests, 160 l.).
> **Domaine** : diagnostics
> **Résumé** : REPRODUCTION H2 (mission fiabilisation, étape A.2 — hypothèse « sources générées invisibles » du prompt) : la MARCHE du scan EXCLUT le dossier `build` (exclusions ADR 0212 : sorties, caches — le scan est le SOURCE). Or les sources GÉNÉRÉES d'un projet Android réel vivent précisément dans l'arbre `build/generated` : KSP/Hilt (`Hilt_` préfixé), Room (`_Impl` suffixé), DataBinding (`BindingImpl` suff
> **Convention** : chaque comportement porte le **nom exact du test source** (identifiant de traçabilité) ; l'attendu le reformule en français lisible, enrichi du contrat D3 quand le nom le porte. Le résumé ci-dessus décrit le contrat de la classe de test.

## Comportements

### `source-generee-dans-build---le-parent-Hilt-est-muet-(convention-generee)`

- **Attendu** : source generee dans build : le parent Hilt est muet (convention generee) — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `controle---la-meme-classe-dans-une-racine-non-exclue-est-muette`

- **Attendu** : controle : la meme classe dans une racine non exclue est muette — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)
