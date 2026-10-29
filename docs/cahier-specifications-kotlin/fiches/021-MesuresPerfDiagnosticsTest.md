# Fiche 021 — MesuresPerfDiagnosticsTest

> **Source** : `codeide-lsp` @ `bc11253` — `lsp/kotlin/src/test/kotlin/jo/lspkotlin/session/MesuresPerfDiagnosticsTest.kt` (1 tests, 150 l.).
> **Domaine** : caches-perf
> **Résumé** : MESURES DE PERFORMANCE (mission fiabilisation, étape A.3) — temps de REPARSE COMPLET et de DIAGNOSTICS sur un GROS fichier (≥ 1 500 lignes), l'étape D du prompt s'appuyant sur ces chiffres pour décider du cache par déclaration. Le document est GÉNÉRÉ DÉTERMINISTEMENT (~1 680 lignes : 150 fonctions réelles — locales, appels stdlib, littéraux, chaînes) ; la session porte le VRAI stdlib (index nourri
> **Convention** : chaque comportement porte le **nom exact du test source** (identifiant de traçabilité) ; l'attendu le reformule en français lisible, enrichi du contrat D3 quand le nom le porte. Le résumé ci-dessus décrit le contrat de la classe de test.

## Comportements

### `gros-fichier---reparse-complet-et-diagnostics-mesures-(froid-puis-5-passes-chaudes)`

- **Attendu** : gros fichier : reparse complet et diagnostics mesures (froid puis 5 passes chaudes).
- **Classement** : heuristique (comportement éprouvé, à confronter à la spécification)
