# Fiche 046 — SessionLangageKotlinK4Test

> **Source** : `codeide-lsp` @ `bc11253` — `lsp/kotlin/src/test/kotlin/jo/lspkotlin/session/SessionLangageKotlinK4Test.kt` (39 tests, 938 l.).
> **Domaine** : diagnostics
> **Résumé** : 
> **Convention** : chaque comportement porte le **nom exact du test source** (identifiant de traçabilité) ; l'attendu le reformule en français lisible, enrichi du contrat D3 quand le nom le porte. Le résumé ci-dessus décrit le contrat de la classe de test.

## Comportements

### `smart-cast---branche-THEN-d'un-if,-membres-du-type-casté`

- **Attendu** : smart cast : branche THEN d'un if, membres du type casté.
- **Classement** : heuristique (règle maison, sous-ensemble de la spécification)

### `smart-cast---garde-!is-a-sortie-precoce,-membres-apres-le-garde`

- **Attendu** : smart cast : garde !is a sortie precoce, membres apres le garde.
- **Classement** : heuristique (règle maison, sous-ensemble de la spécification)

### `smart-cast---operande-droit-d'un-&&`

- **Attendu** : smart cast : operande droit d'un &&.
- **Classement** : heuristique (règle maison, sous-ensemble de la spécification)

### `smart-cast---entree-d'un-when-sans-sujet`

- **Attendu** : smart cast : entree d'un when sans sujet.
- **Classement** : heuristique (règle maison, sous-ensemble de la spécification)

### `smart-cast---locale-OMBREE-apres-la-condition,-le-cast-ne-fuit-pas`

- **Attendu** : smart cast : locale OMBREE apres la condition, le cast ne fuit pas.
- **Classement** : heuristique (règle maison, sous-ensemble de la spécification)

### `smart-cast---garde-sans-sortie-precoce,-aucun-cast-(sous-rapport)`

- **Attendu** : smart cast : garde sans sortie precoce, aucun cast (sous rapport) — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `lambda---parametre-explicite-type-par-l'attente-de-la-fonction-projet`

- **Attendu** : lambda : parametre explicite type par l'attente de la fonction projet.
- **Classement** : heuristique (règle maison, sous-ensemble de la spécification)

### `lambda---it-implicite-type-par-l'attente-a-parametre-unique`

- **Attendu** : lambda : it implicite type par l'attente a parametre unique.
- **Classement** : heuristique (règle maison, sous-ensemble de la spécification)

### `lambda---SAM-d'une-interface-projet-a-methode-unique`

- **Attendu** : lambda : SAM d'une interface projet a methode unique.
- **Classement** : heuristique (règle maison, sous-ensemble de la spécification)

### `lambda---initialiseur-de-propriete-au-type-de-fonction-ecrit`

- **Attendu** : lambda : initialiseur de propriete au type de fonction ecrit.
- **Classement** : heuristique (règle maison, sous-ensemble de la spécification)

### `lambda---sans-attente-demonstrable,-aucun-membre-(sous-rapport)`

- **Attendu** : lambda : sans attente demonstrable, aucun membre (sous rapport) — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `kt-unresolved---reference-simple-introuvable,-signalee`

- **Attendu** : kt unresolved : reference simple introuvable, signalee — l'émission est attendue (preuve d'absence/erreur établie)..
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `kt-unresolved---sans-classpath,-jamais-signale-(sous-rapport)`

- **Attendu** : kt unresolved : sans classpath, jamais signale (sous rapport) — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `kt-unresolved---selecteur-sur-recepteur-TYPO,-signale-(ADR-0223)`

- **Attendu** : kt unresolved : selecteur sur recepteur TYPO, signale (ADR 0223) — l'émission est attendue (preuve d'absence/erreur établie)..
- **Classement** : heuristique (règle maison, sous-ensemble de la spécification)

### `kt-unresolved---selecteur-MEMBRE-sur-recepteur-typo,-signale-(ADR-0223)`

- **Attendu** : kt unresolved : selecteur MEMBRE sur recepteur typo, signale (ADR 0223) — l'émission est attendue (preuve d'absence/erreur établie)..
- **Classement** : heuristique (règle maison, sous-ensemble de la spécification)

### `kt-unresolved---callee-MEMBRE-d-un-recepteur-typé,-resolu-(ADR-0223)`

- **Attendu** : kt unresolved : callee MEMBRE d un recepteur typé, resolu (ADR 0223).
- **Classement** : heuristique (règle maison, sous-ensemble de la spécification)

### `kt-unresolved---recepteur-NON-TYPO,-selecteur-muet-(D3-inchangé)`

- **Attendu** : kt unresolved : recepteur NON TYPO, selecteur muet (D3 inchangé) — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `kt-unresolved---imports-par-defaut-kotlin-et-jvm,-jamais-signales-(KP8)`

- **Attendu** : kt unresolved : imports par defaut kotlin et jvm, jamais signales (KP8) — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `kt-unresolved---type-hors-imports-par-defaut,-toujours-signale-(garde-KP8)`

- **Attendu** : kt unresolved : type hors imports par defaut, toujours signale (garde KP8) — l'émission est attendue (preuve d'absence/erreur établie)..
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `kt-unresolved---appelable-du-classpath-sans-import,-signale-(ADR-0203)`

- **Attendu** : kt unresolved : appelable du classpath sans import, signale (ADR 0203) — l'émission est attendue (preuve d'absence/erreur établie)..
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `kt-unresolved---appelable-du-classpath-importe,-jamais-signale-(garde-ADR-0203)`

- **Attendu** : kt unresolved : appelable du classpath importe, jamais signale (garde ADR 0203) — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `kt-unresolved---appelables-des-paquets-par-defaut,-jamais-signales-(garde-ADR-0203)`

- **Attendu** : kt unresolved : appelables des paquets par defaut, jamais signales (garde ADR 0203) — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `kt-typeMismatch---litteral-chaine-affecte-a-Int,-signale`

- **Attendu** : kt typeMismatch : litteral chaine affecte a Int, signale — l'émission est attendue (preuve d'absence/erreur établie)..
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `kt-typeMismatch---conversions-legitales,-jamais-signalees-(sous-rapport)`

- **Attendu** : kt typeMismatch : conversions legitales, jamais signalees (sous rapport) — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `kt-argumentCount---appel-d'une-fonction-du-projet,-arite-fausse`

- **Attendu** : kt argumentCount : appel d'une fonction du projet, arite fausse.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `kt-argumentCount---constructeur-du-projet,-arite-fausse`

- **Attendu** : kt argumentCount : constructeur du projet, arite fausse.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `kt-argumentCount---defauts-et-varargs,-jamais-signales-(sous-rapport)`

- **Attendu** : kt argumentCount : defauts et varargs, jamais signales (sous rapport) — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `kt-unsafeNullable---point-sur-un-recepteur-nullable,-signale`

- **Attendu** : kt unsafeNullable : point sur un recepteur nullable, signale — l'émission est attendue (preuve d'absence/erreur établie)..
- **Classement** : heuristique (règle maison, sous-ensemble de la spécification)

### `kt-unsafeNullable---garde-de-nullite-et-acces-sur,-jamais-signales-(sous-rapport)`

- **Attendu** : kt unsafeNullable : garde de nullite et acces sur, jamais signales (sous rapport) — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `kt-missingReturn---corps-de-bloc-sans-return,-signale`

- **Attendu** : kt missingReturn : corps de bloc sans return, signale — l'émission est attendue (preuve d'absence/erreur établie)..
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `kt-missingReturn---Unit,-corps-d'expression-et-throw,-jamais-signales-(sous-rapport)`

- **Attendu** : kt missingReturn : Unit, corps d'expression et throw, jamais signales (sous rapport) — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `kt-unreachable---declaration-apres-un-return,-signalee`

- **Attendu** : kt unreachable : declaration apres un return, signalee — l'émission est attendue (preuve d'absence/erreur établie)..
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `kt-unreachable---apres-throw-aussi,-return-imbrique-non-frere-ignore-(sous-rapport)`

- **Attendu** : kt unreachable : apres throw aussi, return imbrique non frere ignore (sous rapport) — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `kt-valReassign---affectation-a-un-val,-signalee`

- **Attendu** : kt valReassign : affectation a un val, signalee — l'émission est attendue (preuve d'absence/erreur établie)..
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `kt-valReassign---var-et-cibles-introuvables,-jamais-signales-(sous-rapport)`

- **Attendu** : kt valReassign : var et cibles introuvables, jamais signales (sous rapport) — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `diagnostics-semantiques---arbre-en-edition,-tout-le-semantique-ecarte`

- **Attendu** : diagnostics semantiques : arbre en edition, tout le semantique ecarte.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `importsCandidats---FQCN-du-classpath-portant-exactement-le-nom`

- **Attendu** : importsCandidats : FQCN du classpath portant exactement le nom.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `importsCandidats---deja-importe,-aucun-candidat`

- **Attendu** : importsCandidats : deja importe, aucun candidat.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `importsCandidats---document-inconnu-et-sans-classpath,-vide`

- **Attendu** : importsCandidats : document inconnu et sans classpath, vide — réponse/effet vide, honnête..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)
