# Fiche 045 — SessionLangageKotlinK3Test

> **Source** : `codeide-lsp` @ `bc11253` — `lsp/kotlin/src/test/kotlin/jo/lspkotlin/session/SessionLangageKotlinK3Test.kt` (21 tests, 640 l.).
> **Domaine** : completion
> **Résumé** : Tests du contrat de session K3 (LSP_KOTLIN.md §5, K3 « symboles + complétion + survol + définition ») — par le SPI (chemin de production D6), avec : - une VUE factice (types/paquets/membres/octet de classes synthétiques — la couture neutre est éprouvée telle quelle) ; - le jar kotlin-stdlib RÉEL du classpath de test comme entrée d'appelables (extensions véritables) ; - des racines sources temporai
> **Convention** : chaque comportement porte le **nom exact du test source** (identifiant de traçabilité) ; l'attendu le reformule en français lisible, enrichi du contrat D3 quand le nom le porte. Le résumé ci-dessus décrit le contrat de la classe de test.

## Comportements

### `complétion-nom---locales,-membres,-mots-clés-par-préfixe`

- **Attendu** : complétion nom : locales, membres, mots clés par préfixe.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `complétion-nom---types-du-classpath-avec-auto-import`

- **Attendu** : complétion nom : types du classpath avec auto import.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `complétion-membre---champ-et-méthode-le-long-de-la-chaîne-de-supertypes`

- **Attendu** : complétion membre : champ et méthode le long de la chaîne de supertypes.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `complétion-membre---extensions-réelles-du-stdlib-sur-String`

- **Attendu** : complétion membre : extensions réelles du stdlib sur String.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `complétion-membre---récepteur-de-type-PROJET-(déclarations-de-la-classe)`

- **Attendu** : complétion membre : récepteur de type PROJET (déclarations de la classe).
- **Classement** : heuristique (règle maison, sous-ensemble de la spécification)

### `complétion-membre---récepteur-non-résolu,-aucune-proposition-(D3)`

- **Attendu** : complétion membre : récepteur non résolu, aucune proposition (D3) — borne conservatrice D3..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `complétion-import---sous-paquets-du-paquet-tapé`

- **Attendu** : complétion import : sous paquets du paquet tapé.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `survol---locale-avec-type-écrit`

- **Attendu** : survol : locale avec type écrit.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `survol---fonction-du-fichier-avec-signature-complète`

- **Attendu** : survol : fonction du fichier avec signature complète.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `survol---membre-qualifié-hérité-(propriétaire-classpath)`

- **Attendu** : survol : membre qualifié hérité (propriétaire classpath).
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `survol---membre-déclaré-sur-récepteur-kotlin-String-via-l'alias-JVM-(ADR-0205)`

- **Attendu** : survol : membre déclaré sur récepteur kotlin String via l'alias JVM (ADR 0205).
- **Classement** : heuristique (règle maison, sous-ensemble de la spécification)

### `survol-usage-document-courant---KDoc-de-la-déclaration-vive-(ADR-0207)`

- **Attendu** : survol usage document courant : KDoc de la déclaration vive (ADR 0207).
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `survol-usage-déclaration-du-SCAN---KDoc-cross-fichier-fermée-(ADR-0208)`

- **Attendu** : survol usage déclaration du SCAN : KDoc cross fichier fermée (ADR 0208).
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `survol-usage-déclaration-TIERCE-OUVERTE---KDoc-de-l'arbre-vif,-pas-du-disque-(ADR-0208)`

- **Attendu** : survol usage déclaration TIERCE OUVERTE : KDoc de l'arbre vif, pas du disque (ADR 0208).
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `survol-usage-déclaration-tierce-SANS-KDoc---signature-seule-(garde-ADR-0208)`

- **Attendu** : survol usage déclaration tierce SANS KDoc : signature seule (garde ADR 0208).
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `survol-usage-déclaration-tierce-ILLISIBLE---signature-seule-(garde-D3-ADR-0208)`

- **Attendu** : survol usage déclaration tierce ILLISIBLE : signature seule (garde D3 ADR 0208) — borne conservatrice D3..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `définition---locale-du-document-courant-(plage-du-nom)`

- **Attendu** : définition : locale du document courant (plage du nom).
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `définition---déclaration-d'un-AUTRE-fichier-des-racines-sources`

- **Attendu** : définition : déclaration d'un AUTRE fichier des racines sources.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `définition---cible-classpath,-liste-vide-(D3-documenté)`

- **Attendu** : définition : cible classpath, liste vide (D3 documenté) — réponse/effet vide, honnête..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `document-inconnu---requêtes-vides,-jamais-d'exception`

- **Attendu** : document inconnu : requêtes vides, jamais d'exception.
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `après-close---définirClasspath-et-requêtes-sont-des-no-ops`

- **Attendu** : après close : définirClasspath et requêtes sont des no ops.
- **Classement** : conforme (borne conservatrice « preuve ou silence »)
