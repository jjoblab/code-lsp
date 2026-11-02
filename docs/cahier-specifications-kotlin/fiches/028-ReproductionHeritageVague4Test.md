# Fiche 028 — ReproductionHeritageVague4Test

> **Source** : `codeide-lsp` @ `bc11253` — `lsp/kotlin/src/test/kotlin/jo/lspkotlin/session/ReproductionHeritageVague4Test.kt` (12 tests, 605 l.).
> **Domaine** : diagnostics
> **Résumé** : REPRODUCTION des familles de la VAGUE 4 (retour utilisateurs 25/10/2026 — backlog RETOURS §4, items 1 à 4) : 1. MEMBRES HÉRITÉS du classpath par le RÉCEPTEUR IMPLICITE — `soumettre(…)`/`compte` nus dans une classe projet qui hérite d'une superclasse BINAIRE (le chaînon projet→classpath), Y COMPRIS depuis un OBJET ANONYME (la pile des this : l'objet d'abord, puis les classes englobantes — `submitLi
> **Convention** : chaque comportement porte le **nom exact du test source** (identifiant de traçabilité) ; l'attendu le reformule en français lisible, enrichi du contrat D3 quand le nom le porte. Le résumé ci-dessus décrit le contrat de la classe de test.

## Comportements

### `heritage---membres-herites-du-classpath-par-this-implicite`

- **Attendu** : heritage : membres herites du classpath par this implicite.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `heritage---pile-des-this-dans-un-objet-anonyme`

- **Attendu** : heritage : pile des this dans un objet anonyme.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `imbriques---herites-du-supertype-par-nom-simple`

- **Attendu** : imbriques : herites du supertype par nom simple.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `imbriques---parametre-typé-dans-un-objet-anonyme-heritant`

- **Attendu** : imbriques : parametre typé dans un objet anonyme heritant.
- **Classement** : heuristique (règle maison, sous-ensemble de la spécification)

### `primaire---parametres-en-zone-de-construction`

- **Attendu** : primaire : parametres en zone de construction.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `primaire---hors-portee-dans-une-methode,-vrai-positif-rattrape`

- **Attendu** : primaire : hors portee dans une methode, vrai positif rattrape.
- **Classement** : heuristique (règle maison, sous-ensemble de la spécification)

### `setter---field-et-parametre-en-portee`

- **Attendu** : setter : field et parametre en portee.
- **Classement** : heuristique (règle maison, sous-ensemble de la spécification)

### `fqn---expression-completee-sans-import`

- **Attendu** : fqn : expression completee sans import.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `fqn---introuvable,-premier-et-dernier-signales,-milieu-muet`

- **Attendu** : fqn : introuvable, premier et dernier signales, milieu muet — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `imbriques---qualifies-en-position-de-type`

- **Attendu** : imbriques : qualifies en position de type.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `apply---propriete-synthetique-java-du-recepteur-implicite`

- **Attendu** : apply : propriete synthetique java du recepteur implicite.
- **Classement** : heuristique (règle maison, sous-ensemble de la spécification)

### `imbriques---chaine-en-expression`

- **Attendu** : imbriques : chaine en expression.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)
