# Fiche 044 — SessionLangageKotlinHeritageTest

> **Source** : `codeide-lsp` @ `bc11253` — `lsp/kotlin/src/test/kotlin/jo/lspkotlin/session/SessionLangageKotlinHeritageTest.kt` (13 tests, 526 l.).
> **Domaine** : diagnostics
> **Résumé** : Tests ADR 0216 — la voie 2-ter « membres HÉRITÉS » de `ResolutionReferences.estResolu` (découverte E3 du rapport d'audit §14) : un appel NU à un membre d'un SUPERTYPE dans une classe du projet ne doit PLUS être un faux `kt.unresolved`. Cas du propriétaire : `setContentView(…)` dans un `class MainActivity : AppCompatActivity()` et `instantiate(…)` dans un `FragmentFactory` — le moteur lit la clause
> **Convention** : chaque comportement porte le **nom exact du test source** (identifiant de traçabilité) ; l'attendu le reformule en français lisible, enrichi du contrat D3 quand le nom le porte. Le résumé ci-dessus décrit le contrat de la classe de test.

## Comportements

### `methode-heritee-directe---setContentView-non-signalee`

- **Attendu** : methode heritee directe : setContentView non signalee — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `methode-heritee-transitives---findViewById-et-moveTaskToBack-non-signales`

- **Attendu** : methode heritee transitives : findViewById et moveTaskToBack non signales — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `champ-herite---mFenetre-non-signale`

- **Attendu** : champ herite : mFenetre non signale — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `instantiate-herite---FragmentFactory-non-signale`

- **Attendu** : instantiate herite : FragmentFactory non signale — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `frere-projet-entre-l-appel-et-le-classpath---setContentView-non-signalee`

- **Attendu** : frere projet entre l appel et le classpath : setContentView non signalee — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `membre-declare-du-frere-projet-herite---aCote-non-signale`

- **Attendu** : membre declare du frere projet herite : aCote non signale — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `frere-ouvert-vivant---setContentView-non-signalee-(arbre-vif)`

- **Attendu** : frere ouvert vivant : setContentView non signalee (arbre vif) — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `E4---type-qualifie-existant---segments-muets-(android-os-Bundle)`

- **Attendu** : E4 : type qualifie existant : segments muets (android os Bundle) — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `E4bis---type-qualifie-inexistant---un-seul-segment-faillible`

- **Attendu** : E4bis : type qualifie inexistant : un seul segment faillible.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `regression---nom-reellement-inconnu-reste-signale`

- **Attendu** : regression : nom reellement inconnu reste signale — l'émission est attendue (preuve d'absence/erreur établie)..
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `regression---methode-absente-du-supertype-reste-signalee`

- **Attendu** : regression : methode absente du supertype reste signalee — l'émission est attendue (preuve d'absence/erreur établie)..
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `regression---sans-supertype,-pas-de-voie-heritee-magique`

- **Attendu** : regression : sans supertype, pas de voie heritee magique.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `quick-fix-import---membre-herite-ne-propose-aucun-import`

- **Attendu** : quick fix import : membre herite ne propose aucun import.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)
