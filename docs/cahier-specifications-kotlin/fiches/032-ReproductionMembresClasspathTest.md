# Fiche 032 — ReproductionMembresClasspathTest

> **Source** : `codeide-lsp` @ `bc11253` — `lsp/kotlin/src/test/kotlin/jo/lspkotlin/session/ReproductionMembresClasspathTest.kt` (3 tests, 211 l.).
> **Domaine** : diagnostics
> **Résumé** : REPRODUCTION H5 (mission fiabilisation, étape A.2 — hypothèses « entrées d'enum (`entries`, `values()`), membres statiques de bibliothèques » du prompt) : le moteur résout les sélecteurs d'un récepteur CLASSPATH via la vue JVM (champs + méthodes du bytecode, [VueClasspathKotlin.membres]). La vue factice modélise le contenu RÉEL d'un `.class` d'enum Kotlin 1.9+ (miroir de `kotlin.io.FileWalkDirecti
> **Convention** : chaque comportement porte le **nom exact du test source** (identifiant de traçabilité) ; l'attendu le reformule en français lisible, enrichi du contrat D3 quand le nom le porte. Le résumé ci-dessus décrit le contrat de la classe de test.

## Comportements

### `enum-classpath---champ-statique,-values-et-valueOf-sont-muets`

- **Attendu** : enum classpath : champ statique, values et valueOf sont muets — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `enum-classpath---entries-(getter-STATIQUE)-est-muet-(miroir-ADR-0222)`

- **Attendu** : enum classpath : entries (getter STATIQUE) est muet (miroir ADR 0222) — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `controle---un-membre-reellement-absent-reste-signale`

- **Attendu** : controle : un membre reellement absent reste signale — l'émission est attendue (preuve d'absence/erreur établie)..
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)
