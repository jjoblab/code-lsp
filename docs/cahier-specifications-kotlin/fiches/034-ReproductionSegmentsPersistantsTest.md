# Fiche 034 — ReproductionSegmentsPersistantsTest

> **Source** : `codeide-lsp` @ `bc11253` — `lsp/kotlin/src/test/kotlin/jo/lspkotlin/session/ReproductionSegmentsPersistantsTest.kt` (3 tests, 193 l.).
> **Domaine** : index-classpath
> **Résumé** : REPRODUCTION H4 (mission fiabilisation, étape A.2 — hypothèse « segment d'index partiel / périmé » du prompt) : l'index des appelables persiste ses segments sous `dossierIndex` (`kotlin-appelables/<sha256>.kidx`), relus PAR EMPREINTE. Les scénarios suspectés — segment CORROMPU, segment de VERSION périmée (le format porte magie + version), jar DISPARU — sont mesurés ici en session complète. Comport
> **Convention** : chaque comportement porte le **nom exact du test source** (identifiant de traçabilité) ; l'attendu le reformule en français lisible, enrichi du contrat D3 quand le nom le porte. Le résumé ci-dessus décrit le contrat de la classe de test.

## Comportements

### `segment-corrompu---auto-guerison,-l-index-reste-portant`

- **Attendu** : segment corrompu : auto guerison, l index reste portant.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `segment-de-version-perimee---rejete-puis-re-decode,-pas-de-faux-positif`

- **Attendu** : segment de version perimee : rejete puis re decode, pas de faux positif.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `jar-absent---l-entree-est-ecartee-et-la-garde-tait-tout-kt-unresolved`

- **Attendu** : jar absent : l entree est ecartee et la garde tait tout kt unresolved.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)
