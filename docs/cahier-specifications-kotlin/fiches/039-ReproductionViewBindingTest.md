# Fiche 039 — ReproductionViewBindingTest

> **Source** : `codeide-lsp` @ `bc11253` — `lsp/kotlin/src/test/kotlin/jo/lspkotlin/session/ReproductionViewBindingTest.kt` (1 tests, 175 l.).
> **Domaine** : completion
> **Résumé** : REPRODUCTION de la forme ViewBinding au niveau MOTEUR (ADR 0219) — le récepteur est une PROPRIÉTÉ typée (private lateinit var binding : ActivityMainBinding) : la complétion membre doit offrir root/textView/inflate/bind du groupe synthétique K5, TOUJOURS dans le sous-paquet `databinding` (convention AGP réelle — le template Android Studio « Empty Views Activity » importe com.ex.app.databinding.Acti
> **Convention** : chaque comportement porte le **nom exact du test source** (identifiant de traçabilité) ; l'attendu le reformule en français lisible, enrichi du contrat D3 quand le nom le porte. Le résumé ci-dessus décrit le contrat de la classe de test.

## Comportements

### `viewbinding---propriete-typée,-completion-membre-du-groupe-K5`

- **Attendu** : viewbinding : propriete typée, completion membre du groupe K5.
- **Classement** : heuristique (règle maison, sous-ensemble de la spécification)
