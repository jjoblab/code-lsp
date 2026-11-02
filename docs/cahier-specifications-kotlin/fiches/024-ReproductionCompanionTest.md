# Fiche 024 — ReproductionCompanionTest

> **Source** : `codeide-lsp` @ `bc11253` — `lsp/kotlin/src/test/kotlin/jo/lspkotlin/session/ReproductionCompanionTest.kt` (7 tests, 348 l.).
> **Domaine** : diagnostics
> **Résumé** : REPRODUCTION des COMPANION OBJECTS (ADR 0222) — le quatrième monde Kotlin pour la parité Android Studio : `Boite.creer()` (membres du compagnon accessibles par le NOM de la classe), compagnons ANONYMES (« Companion » effectif — le repli générique nomJeton() lisait le PREMIER MEMBRE : le compagnon était enregistré sous « creer » et ses membres sous « pkg.Boite.creer »), compagnons NOMMÉS, this impl
> **Convention** : chaque comportement porte le **nom exact du test source** (identifiant de traçabilité) ; l'attendu le reformule en français lisible, enrichi du contrat D3 quand le nom le porte. Le résumé ci-dessus décrit le contrat de la classe de test.

## Comportements

### `companion-anonyme---Boite-point-creer(),-zero-faux-positif-et-capacites`

- **Attendu** : companion anonyme : Boite point creer(), zero faux positif et capacites.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `companion-nomme---membres-sous-la-classe-ET-sous-l-objet`

- **Attendu** : companion nomme : membres sous la classe ET sous l objet.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `corps-du-compagnon---this-implicite-des-membres`

- **Attendu** : corps du compagnon : this implicite des membres.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `objet-simple-non-regression---ADR-0220-inchangée`

- **Attendu** : objet simple non regression : ADR 0220 inchangée.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `companion-du-classpath---Regex-point-escape-resolve-et-s-offre`

- **Attendu** : companion du classpath : Regex point escape resolve et s offre.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `companion-du-classpath---UInt-point-MAX_VALUE-champ-typé`

- **Attendu** : companion du classpath : UInt point MAX_VALUE champ typé.
- **Classement** : heuristique (règle maison, sous-ensemble de la spécification)

### `garde-D3---membre-introuvable-du-compagnon-reste-signale`

- **Attendu** : garde D3 : membre introuvable du compagnon reste signale — l'émission est attendue (preuve d'absence/erreur établie)..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)
