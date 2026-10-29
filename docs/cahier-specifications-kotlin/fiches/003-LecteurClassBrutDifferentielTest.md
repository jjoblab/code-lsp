# Fiche 003 — LecteurClassBrutDifferentielTest

> **Source** : `codeide-lsp` @ `bc11253` — `lsp/kotlin/src/test/kotlin/jo/lspkotlin/classpath/LecteurClassBrutDifferentielTest.kt` (3 tests, 212 l.).
> **Domaine** : index-classpath
> **Résumé** : TEST DIFFÉRENTIEL DU LECTEUR DE CLASSES (mission fiabilisation, étape A.3) — [LecteurClassBrut] contre la RÉFLEXION JDK sur DEUX corpus réels : android.jar (AOSP API 16, Maven Central — le SDK Android est absent des environnements de test JVM) et le jar kotlin-stdlib du classpath de test. Le prompt demande « réflexion JDK pour les classes du JDK » : la réflexion sur classes CHARGÉES (jamais initia
> **Convention** : chaque comportement porte le **nom exact du test source** (identifiant de traçabilité) ; l'attendu le reformule en français lisible, enrichi du contrat D3 quand le nom le porte. Le résumé ci-dessus décrit le contrat de la classe de test.

## Comportements

### `android-jar---structure-identique-a-la-reflexion,-zero-classe-illisible`

- **Attendu** : android jar : structure identique a la reflexion, zero classe illisible.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `kotlin-stdlib---structure-identique-a-la-reflexion,-zero-classe-illisible`

- **Attendu** : kotlin stdlib : structure identique a la reflexion, zero classe illisible.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `metadonnees-stdlib---appelables-usuels-presents,-extensions-reputees,-volume-rendu`

- **Attendu** : metadonnees stdlib : appelables usuels presents, extensions reputees, volume rendu.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)
