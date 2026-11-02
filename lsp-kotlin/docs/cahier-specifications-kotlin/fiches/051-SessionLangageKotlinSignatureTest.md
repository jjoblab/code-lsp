# Fiche 051 — SessionLangageKotlinSignatureTest

> **Source** : `codeide-lsp` @ `bc11253` — `lsp/kotlin/src/test/kotlin/jo/lspkotlin/session/SessionLangageKotlinSignatureTest.kt` (12 tests, 297 l.).
> **Domaine** : hover-inlay
> **Résumé** : Tests du contrat de session — AIDE À LA SIGNATURE (S1, plan de stabilisation LSP §3.2) : par le SPI (chemin de production D6), moteur RÉEL, candidates du PROJET (fonctions, constructeurs, membres vifs), candidates du CLASSPATH (index Kotlin — jar stdlib RÉEL ; vue factice — méthode JVM du récepteur, même montage que le survol qualifié K3).
> **Convention** : chaque comportement porte le **nom exact du test source** (identifiant de traçabilité) ; l'attendu le reformule en français lisible, enrichi du contrat D3 quand le nom le porte. Le résumé ci-dessus décrit le contrat de la classe de test.

## Comportements

### `signature---fonction-projet,-curseur-après-parenthèse`

- **Attendu** : signature : fonction projet, curseur après parenthèse.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `signature---paramètre-actif-après-la-virgule`

- **Attendu** : signature : paramètre actif après la virgule.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `signature---surcharges,-signature-active-=-arité-compatible`

- **Attendu** : signature : surcharges, signature active = arité compatible.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `signature---paramètres-par-défaut-élargissent-l'arité-légale`

- **Attendu** : signature : paramètres par défaut élargissent l'arité légale.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `signature---constructeur-primaire-de-classe-projet`

- **Attendu** : signature : constructeur primaire de classe projet.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `signature---membre-vif-de-la-classe-englobante`

- **Attendu** : signature : membre vif de la classe englobante.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `signature---lambda-final-compte-pour-l'argument-suivant`

- **Attendu** : signature : lambda final compte pour l'argument suivant.
- **Classement** : heuristique (règle maison, sous-ensemble de la spécification)

### `signature---appelable-de-premier-niveau-du-stdlib-(index-réel)`

- **Attendu** : signature : appelable de premier niveau du stdlib (index réel).
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `signature---méthode-JVM-du-récepteur-qualifié-(vue-factice)`

- **Attendu** : signature : méthode JVM du récepteur qualifié (vue factice).
- **Classement** : heuristique (règle maison, sous-ensemble de la spécification)

### `signature---appel-niché,-le-plus-profond-gagne`

- **Attendu** : signature : appel niché, le plus profond gagne.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `signature---curseur-après-l'appel-fermé,-silence`

- **Attendu** : signature : curseur après l'appel fermé, silence — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `signature---hors-appel,-document-inconnu,-session-fermée`

- **Attendu** : signature : hors appel, document inconnu, session fermée.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)
