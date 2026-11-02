# Fiche 047 — SessionLangageKotlinK5Test

> **Source** : `codeide-lsp` @ `bc11253` — `lsp/kotlin/src/test/kotlin/jo/lspkotlin/session/SessionLangageKotlinK5Test.kt` (21 tests, 781 l.).
> **Domaine** : diagnostics
> **Résumé** : Tests du contrat de session K5 (LSP_KOTLIN.md §5, K5 « classes synthétiques R/ViewBinding/DataBinding, intégration éditeur complète — complétion multi-langue, bascule Java↔Kotlin ») — par le SPI (chemin de production D6), avec un PROJET ANDROID FACTICE sur disque (manifeste + res/layout + res/values + res/drawable) et la MÊME vue factice que la suite K4 (étendue : `setOnClickListener` et son SAM `
> **Convention** : chaque comportement porte le **nom exact du test source** (identifiant de traçabilité) ; l'attendu le reformule en français lisible, enrichi du contrat D3 quand le nom le porte. Le résumé ci-dessus décrit le contrat de la classe de test.

## Comportements

### `R---familles-proposees-apres-le-point,-layout-string-id-drawable`

- **Attendu** : R : familles proposees apres le point, layout string id drawable.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `R---constantes-des-familles-(layout,-string,-id)-proposees`

- **Attendu** : R : constantes des familles (layout, string, id) proposees.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `R---reference-nue-resolue,-aucun-kt-unresolved`

- **Attendu** : R : reference nue resolue, aucun kt unresolved.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `R---sans-import,-meme-paquet-que-le-manifeste-(retour-U2---MainActivity)`

- **Attendu** : R : sans import, meme paquet que le manifeste (retour U2 : MainActivity).
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `ViewBinding---classe-synthetique-et-champs-des-ids-proposes`

- **Attendu** : ViewBinding : classe synthetique et champs des ids proposes.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `ViewBinding---le-nom-de-classe-propose-en-complétion-de-nom`

- **Attendu** : ViewBinding : le nom de classe propose en complétion de nom.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `DataBinding---classe-du-sous-paquet-databinding-et-variable`

- **Attendu** : DataBinding : classe du sous paquet databinding et variable.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `ViewBinding---la-definition-ouvre-le-fichier-de-layout`

- **Attendu** : ViewBinding : la definition ouvre le fichier de layout.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `BuildConfig---type-resolu,-membres-sous-rapportes`

- **Attendu** : BuildConfig : type resolu, membres sous rapportes — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `coupure---une-classe-R-reelle-du-scan-efface-le-synthetique`

- **Attendu** : coupure : une classe R reelle du scan efface le synthetique.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `sans-manifeste---aucun-synthetique,-rien-de-faux`

- **Attendu** : sans manifeste : aucun synthetique, rien de faux.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `multi-langue---la-classe-Java-est-proposee-et-resolue-depuis-Kotlin`

- **Attendu** : multi langue : la classe Java est proposee et resolue depuis Kotlin.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `multi-langue---l-arite-d-un-constructeur-Java-n-est-jamais-controlee`

- **Attendu** : multi langue : l arite d un constructeur Java n est jamais controlee.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `multi-langue---la-definition-d-une-reference-Kotlin-ouvre-le-java`

- **Attendu** : multi langue : la definition d une reference Kotlin ouvre le java.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `multi-langue---membres-Java-resolus-depuis-Kotlin-(getters,-statiques,-propriete-synthetique)`

- **Attendu** : multi langue : membres Java resolus depuis Kotlin (getters, statiques, propriete synthetique).
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `multi-langue---membres-Java-proposes-en-completion-apres-le-point`

- **Attendu** : multi langue : membres Java proposes en completion apres le point.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `multi-langue---le-typage-traverse-un-getter-Java-(chaine-continue)`

- **Attendu** : multi langue : le typage traverse un getter Java (chaine continue).
- **Classement** : heuristique (règle maison, sous-ensemble de la spécification)

### `with---membres-du-recepteur-implicite-proposes-sans-selecteur`

- **Attendu** : with : membres du recepteur implicite proposes sans selecteur.
- **Classement** : heuristique (règle maison, sous-ensemble de la spécification)

### `with---un-membre-nu-du-recepteur-n-est-pas-non-resolu`

- **Attendu** : with : un membre nu du recepteur n est pas non resolu.
- **Classement** : heuristique (règle maison, sous-ensemble de la spécification)

### `with---le-recepteur-implicite-traverse-les-literaux-emboites`

- **Attendu** : with : le recepteur implicite traverse les literaux emboites.
- **Classement** : heuristique (règle maison, sous-ensemble de la spécification)

### `SAM-classpath---le-parametre-du-lambda-est-type-par-l-attente`

- **Attendu** : SAM classpath : le parametre du lambda est type par l attente.
- **Classement** : heuristique (règle maison, sous-ensemble de la spécification)
