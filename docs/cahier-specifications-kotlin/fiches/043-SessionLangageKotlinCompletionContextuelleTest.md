# Fiche 043 — SessionLangageKotlinCompletionContextuelleTest

> **Source** : `codeide-lsp` @ `bc11253` — `lsp/kotlin/src/test/kotlin/jo/lspkotlin/session/SessionLangageKotlinCompletionContextuelleTest.kt` (14 tests, 308 l.).
> **Domaine** : completion
> **Résumé** : Tests de la complétion CONTEXTUELLE Kotlin (vague 1 du chantier du rapport d'analyse du 28/10/2026 — ADR 0228) : les trois contextes portés du moteur Java 1.16.0, par le SPI (chemin de production D6) — arguments NOMMÉS d'un appel, CONSTANTES d'enum des branches d'un `when`, ATTRIBUTS d'annotation. Chaque test éprouve AUSSI la borne conservatrice (D3) : position de valeur muette, candidat inconnu,
> **Convention** : chaque comportement porte le **nom exact du test source** (identifiant de traçabilité) ; l'attendu le reformule en français lisible, enrichi du contrat D3 quand le nom le porte. Le résumé ci-dessus décrit le contrat de la classe de test.

## Comportements

### `arguments-nommes---surcharge-du-projet,-insertion-avec-separateur`

- **Attendu** : arguments nommes : surcharge du projet, insertion avec separateur.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `arguments-nommes---argument-deja-fourni-en-forme-nommee-exclu`

- **Attendu** : arguments nommes : argument deja fourni en forme nommee exclu.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `arguments-nommes---position-de-valeur-muette-(litteral-chaine)`

- **Attendu** : arguments nommes : position de valeur muette (litteral chaine) — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `arguments-nommes---appel-membre-qualifie-du-projet`

- **Attendu** : arguments nommes : appel membre qualifie du projet.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `arguments-nommes---appel-inconnu,-aucune-invention-(D3)`

- **Attendu** : arguments nommes : appel inconnu, aucune invention (D3) — borne conservatrice D3..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `arguments-nommes---filtre-par-prefixe-du-nom-de-parametre`

- **Attendu** : arguments nommes : filtre par prefixe du nom de parametre.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `constantes-enum---when-a-sujet-enum-du-projet,-contexte-exclusif`

- **Attendu** : constantes enum : when a sujet enum du projet, contexte exclusif.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `constantes-enum---filtre-par-prefixe-et-sujet-enum-du-classpath`

- **Attendu** : constantes enum : filtre par prefixe et sujet enum du classpath.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `constantes-enum---sujet-non-enum,-repli-honnete-sur-le-regime-de-nom`

- **Attendu** : constantes enum : sujet non enum, repli honnete sur le regime de nom.
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `constantes-enum---corps-de-branche-muet`

- **Attendu** : constantes enum : corps de branche muet — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `attributs-annotation---non-fournis,-insertion-avec-separateur`

- **Attendu** : attributs annotation : non fournis, insertion avec separateur.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `attributs-annotation---attribut-deja-fourni-exclu`

- **Attendu** : attributs annotation : attribut deja fourni exclu.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `attributs-annotation---position-de-valeur-muette`

- **Attendu** : attributs annotation : position de valeur muette — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `attributs-annotation---annotation-non-resolue,-repli-honnete`

- **Attendu** : attributs annotation : annotation non resolue, repli honnete.
- **Classement** : conforme (borne conservatrice « preuve ou silence »)
