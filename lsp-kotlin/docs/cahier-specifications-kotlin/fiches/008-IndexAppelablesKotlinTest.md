# Fiche 008 — IndexAppelablesKotlinTest

> **Source** : `codeide-lsp` @ `bc11253` — `lsp/kotlin/src/test/kotlin/jo/lspkotlin/index/IndexAppelablesKotlinTest.kt` (9 tests, 243 l.).
> **Domaine** : index-classpath
> **Résumé** : Tests de l'index des appelables (K3) — le CONTRAT DISQUE est le cœur du test : un segment écrit puis relu SANS le jar d'origine (supprimé) prouve la persistance ; un segment CORROMPU se re-décoderait (jar absent → partiel honnête, jamais d'exception).
> **Convention** : chaque comportement porte le **nom exact du test source** (identifiant de traçabilité) ; l'attendu le reformule en français lisible, enrichi du contrat D3 quand le nom le porte. Le résumé ci-dessus décrit le contrat de la classe de test.

## Comportements

### `segment-persiste---le-jar-supprime-est-encore-servi-par-le-cache`

- **Attendu** : segment persiste : le jar supprime est encore servi par le cache.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `memoire-seule---index-fonctionnel-sans-dossier`

- **Attendu** : memoire seule : index fonctionnel sans dossier.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `entree-inexistante---partiel-honnete,-jamais-d'exception`

- **Attendu** : entree inexistante : partiel honnete, jamais d'exception.
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `typealias---carte-dediee,-appelables-et-completion-non-pollues`

- **Attendu** : typealias : carte dediee, appelables et completion non pollues.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `recherche-par-prefixe---binaire-bornee-et-limite-respectee`

- **Attendu** : recherche par prefixe : binaire bornee et limite respectee.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `garde-d-empreintes---definir-deux-fois-est-un-no-op-chaud`

- **Attendu** : garde d empreintes : definir deux fois est un no op chaud.
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `cache-memoire---le-dossier-efface-ne-re-relit-rien`

- **Attendu** : cache memoire : le dossier efface ne re relit rien.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `echec-jamais-en-cache---la-reprise-retente-toujours-l-illisible`

- **Attendu** : echec jamais en cache : la reprise retente toujours l illisible.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `annulation---le-snapshot-partiel-est-la-verite`

- **Attendu** : annulation : le snapshot partiel est la verite.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)
