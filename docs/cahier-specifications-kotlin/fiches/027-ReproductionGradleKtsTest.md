# Fiche 027 — ReproductionGradleKtsTest

> **Source** : `codeide-lsp` @ `bc11253` — `lsp/kotlin/src/test/kotlin/jo/lspkotlin/session/ReproductionGradleKtsTest.kt` (5 tests, 236 l.).
> **Domaine** : diagnostics
> **Résumé** : REPRODUCTION des fichiers GRADLE KOTLIN DSL (.gradle.kts) — le build.gradle.kts et settings.gradle.kts du template « Empty Views Activity » d'Android Studio (version-catalog `libs`, blocs plugins/android/dependencies, receivers implicites injectés par le DSL Gradle). Miroir Android Studio : le DSL Gradle est INJECTÉ par le modèle Gradle — sans lui (pas de classpath Gradle), IntelliJ ne signale PAS
> **Convention** : chaque comportement porte le **nom exact du test source** (identifiant de traçabilité) ; l'attendu le reformule en français lisible, enrichi du contrat D3 quand le nom le porte. Le résumé ci-dessus décrit le contrat de la classe de test.

## Comportements

### `gradle-kts---build-gradle-kts-du-template,-aucun-faux-positif`

- **Attendu** : gradle kts : build gradle kts du template, aucun faux positif.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `gradle-kts---settings-gradle-kts-du-template,-aucun-faux-positif`

- **Attendu** : gradle kts : settings gradle kts du template, aucun faux positif.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `gradle-kts---le-Kotlin-du-script-resout-quand-meme-(stdlib,-locales)`

- **Attendu** : gradle kts : le Kotlin du script resout quand meme (stdlib, locales).
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `gradle-kts---garde-D3---le-segment-qualifie-inconnu-reste-signale`

- **Attendu** : gradle kts : garde D3 : le segment qualifie inconnu reste signale — l'émission est attendue (preuve d'absence/erreur établie)..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `gradle-kts---survol-du-Kotlin-standard-du-script`

- **Attendu** : gradle kts : survol du Kotlin standard du script.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)
