# Fiche 033 — ReproductionObjetsClasspathTest

> **Source** : `codeide-lsp` @ `bc11253` — `lsp/kotlin/src/test/kotlin/jo/lspkotlin/session/ReproductionObjetsClasspathTest.kt` (9 tests, 460 l.).
> **Domaine** : diagnostics
> **Résumé** : REPRODUCTION des OBJETS DU CLASSPATH (retour utilisateurs 25/10/2026, familles A du programme U1-U4) : `MaterialTheme.typography`, `Arrangement.spacedBy`, `Icons.Filled` — un objet Kotlin compilé est une classe au champ STATIQUE `INSTANCE` dont les membres sont des membres d'INSTANCE. Le moteur n'offrait que « INSTANCE » en complétion après le nom de l'objet (Léa : `MaterialTheme.` → UNE propositi
> **Convention** : chaque comportement porte le **nom exact du test source** (identifiant de traçabilité) ; l'attendu le reformule en français lisible, enrichi du contrat D3 quand le nom le porte. Le résumé ci-dessus décrit le contrat de la classe de test.

## Comportements

### `objet-classpath---propriete-membre,-zero-faux-positif`

- **Attendu** : objet classpath : propriete membre, zero faux positif.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `objet-classpath---second-membre,-zero-faux-positif`

- **Attendu** : objet classpath : second membre, zero faux positif.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `objet-classpath---fonction-membre-appelee,-zero-faux-positif`

- **Attendu** : objet classpath : fonction membre appelee, zero faux positif.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `objet-classpath---membre-inexistant,-toujours-signale`

- **Attendu** : objet classpath : membre inexistant, toujours signale — l'émission est attendue (preuve d'absence/erreur établie)..
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `objet-classpath---survol-du-membre`

- **Attendu** : objet classpath : survol du membre.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `coroutines-reel---collect-suspend-extension,-zero-faux-positif`

- **Attendu** : coroutines reel : collect suspend extension, zero faux positif.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `coroutines-reel---collect-sur-membre-distancie-SharedFlow,-zero-faux-positif`

- **Attendu** : coroutines reel : collect sur membre distancie SharedFlow, zero faux positif.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `coroutines-reel---collect-sur-membre-dont-l-import-vit-ailleurs,-zero-faux-positif`

- **Attendu** : coroutines reel : collect sur membre dont l import vit ailleurs, zero faux positif.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `objectimporte---le-membre-inexistant-d-un-object-du-projet-est-signale`

- **Attendu** : objectimporte : le membre inexistant d un object du projet est signale — l'émission est attendue (preuve d'absence/erreur établie)..
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)
