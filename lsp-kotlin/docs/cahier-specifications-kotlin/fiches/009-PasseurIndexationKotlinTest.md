# Fiche 009 — PasseurIndexationKotlinTest

> **Source** : `codeide-lsp` @ `bc11253` — `lsp/kotlin/src/test/kotlin/jo/lspkotlin/index/PasseurIndexationKotlinTest.kt` (6 tests, 324 l.).
> **Domaine** : index-classpath
> **Résumé** : Tests du PILOTE de passages d'indexation Kotlin (ADR 0211/0213/0214 — demandes D1/D3/D4/D5 de CodeIDE) : SÉQUENCE d'événements (bibliothèques puis sources puis PRET/PARTIEL), ANNULATION coopérative (PARTIEL « annulé par l'utilisateur », l'acquis est publié), REPRISE ciblée (les entrées saines deviennent REPRIS du cache, seuls les échecs sont re-payés) et no-op chaud des syncs redondantes. Le stdli
> **Convention** : chaque comportement porte le **nom exact du test source** (identifiant de traçabilité) ; l'attendu le reformule en français lisible, enrichi du contrat D3 quand le nom le porte. Le résumé ci-dessus décrit le contrat de la classe de test.

## Comportements

### `sequence---bibliotheques-puis-sources-puis-PARTIEL-avec-ecart`

- **Attendu** : sequence : bibliotheques puis sources puis PARTIEL avec ecart.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `sequence-complete---liste-de-travail,-breakdown-et-bilan-paritaires`

- **Attendu** : sequence complete : liste de travail, breakdown et bilan paritaires.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `sync-redondante---tout-est-REPRIS,-aucune-relecture`

- **Attendu** : sync redondante : tout est REPRIS, aucune relecture.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `annulation---PARTIEL-annule-par-l-utilisateur,-l-acquis-est-publie`

- **Attendu** : annulation : PARTIEL annule par l utilisateur, l acquis est publie.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `echec-isole---reesayerEchecs-retente-l-ecart-et-le-guerit`

- **Attendu** : echec isole : reesayerEchecs retente l ecart et le guerit.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `hors-vol---annulerPassage-et-reesayerEchecs-refusent-honnetement`

- **Attendu** : hors vol : annulerPassage et reesayerEchecs refusent honnetement.
- **Classement** : conforme (borne conservatrice « preuve ou silence »)
