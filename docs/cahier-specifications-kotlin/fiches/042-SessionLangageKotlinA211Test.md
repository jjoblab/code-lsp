# Fiche 042 — SessionLangageKotlinA211Test

> **Source** : `codeide-lsp` @ `bc11253` — `lsp/kotlin/src/test/kotlin/jo/lspkotlin/session/SessionLangageKotlinA211Test.kt` (4 tests, 163 l.).
> **Domaine** : index-classpath
> **Résumé** : Tests ADR 0211/0212 (demandes D1/D2 de CodeIDE) — BRANCHEMENT et SOUS-LECTURE de la session : - quand le MOTEUR possède le nourrissage (`nourrissageParMoteur`), `definirClasspath` NE RE-LECT RIEN : l'index reste tel quel et les requêtes qui prouvent par l'ABSENCE (diagnostics `kt.unresolved`) se TAISENT (index non couvert ⇒ AUCUN faux négatif) ; - hors moteur nourricier, le contrat SYNCHRONE histo
> **Convention** : chaque comportement porte le **nom exact du test source** (identifiant de traçabilité) ; l'attendu le reformule en français lisible, enrichi du contrat D3 quand le nom le porte. Le résumé ci-dessus décrit le contrat de la classe de test.

## Comportements

### `contrat-synchrone---hors-moteur-nourricier,-definirClasspath-nourrit-ici`

- **Attendu** : contrat synchrone : hors moteur nourricier, definirClasspath nourrit ici.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `branchement---moteur-nourricier,-definirClasspath-ne-re-lect-rien`

- **Attendu** : branchement : moteur nourricier, definirClasspath ne re lect rien.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `sous-lecture-guerit---le-snapshot-publie-rend-l-index-a-la-requete-suivante`

- **Attendu** : sous lecture guerit : le snapshot publie rend l index a la requete suivante.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)

### `verrou-lecture-ecriture---requetes-paralleles-sans-interblocage`

- **Attendu** : verrou lecture ecriture : requetes paralleles sans interblocage.
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)
