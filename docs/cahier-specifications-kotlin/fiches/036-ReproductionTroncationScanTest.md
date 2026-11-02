# Fiche 036 — ReproductionTroncationScanTest

> **Source** : `codeide-lsp` @ `bc11253` — `lsp/kotlin/src/test/kotlin/jo/lspkotlin/session/ReproductionTroncationScanTest.kt` (3 tests, 161 l.).
> **Domaine** : diagnostics
> **Résumé** : H1 — TRONCATION DU SCAN (mission fiabilisation, étape A.2 → CORRECTIF étape B, rapport faux positifs 2026-10-26 §7.1) : la MARCHE du scan est bornée à [ScanProjet.BORN_FICHIERS] ; au-delà de la borne, les fichiers sont IGNORÉS du parse. L'étape B corrige les DEUX lacunes mesurées en A.2 : - la MARCHE COMPTE au-delà de la borne ([ScanProjet.statsDernierPassage] rend le total RÉEL des fichiers éligi
> **Convention** : chaque comportement porte le **nom exact du test source** (identifiant de traçabilité) ; l'attendu le reformule en français lisible, enrichi du contrat D3 quand le nom le porte. Le résumé ci-dessus décrit le contrat de la classe de test.

## Comportements

### `marche-bornee---le-fichier-au-dela-de-la-borne-est-compte-et-estUniversConnu-rend-faux`

- **Attendu** : marche bornee : le fichier au dela de la borne est compte et estUniversConnu rend faux.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `correction---la-reference-au-type-du-fichier-non-scanne-est-muette-(absence-non-prouvable)`

- **Attendu** : correction : la reference au type du fichier non scanne est muette (absence non prouvable) — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `controle---sans-troncation,-la-meme-reference-est-muette`

- **Attendu** : controle : sans troncation, la meme reference est muette — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)
