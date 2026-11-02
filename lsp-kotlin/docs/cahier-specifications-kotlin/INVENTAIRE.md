# INVENTAIRE — fichiers de test sources du cahier Kotlin

> Base d'extraction (R9.1/R9.5) : `codeide-lsp` @ `bc11253` — `lsp/kotlin/src/test/**`, lecture seule. 53 fichiers, 469 méthodes `@Test`, 15 853 lignes [mesuré]. Le cahier ne contient AUCUN extrait de code : chaque comportement est spécifié par son nom de test source, son attendu reformulé et son classement.

| Fiche | Fichier | Domaine | Tests | Lignes |
|---|---|---|---:|---:|
| 001 | `ChargeurMoteurKotlinTest.kt` | cycle-vie-lsp | 3 | 44 |
| 002 | `DecodeurMetadonneesTest.kt` | index-classpath | 3 | 49 |
| 003 | `LecteurClassBrutDifferentielTest.kt` | index-classpath | 3 | 212 |
| 004 | `LecteurClassBrutTest.kt` | index-classpath | 5 | 135 |
| 005 | `SondeTypealiasKotlinxTest.kt` | index-classpath | 2 | 145 |
| 006 | `ExplicationNonResoluTest.kt` | diagnostics | 4 | 291 |
| 007 | `DocumentationKotlinTest.kt` | hover-inlay | 9 | 129 |
| 008 | `IndexAppelablesKotlinTest.kt` | index-classpath | 9 | 243 |
| 009 | `PasseurIndexationKotlinTest.kt` | index-classpath | 6 | 324 |
| 010 | `PariteCorpusDepotTest.kt` | parse | 2 | 149 |
| 011 | `ToleranceErreursTest.kt` | parse | 3 | 87 |
| 012 | `ParseurKotlinTest.kt` | parse | 9 | 128 |
| 013 | `FacadeKtTest.kt` | parse | 13 | 240 |
| 014 | `PlisDocumentKotlinTest.kt` | folding | 7 | 153 |
| 015 | `CapacitesL2KotlinTest.kt` | hover-inlay | 12 | 380 |
| 016 | `CorrectifsTexteTest.kt` | diagnostics | 13 | 325 |
| 017 | `DiagnosticsParDeclarationTest.kt` | diagnostics | 8 | 190 |
| 018 | `FicheClasspathTest.kt` | hover-inlay | 11 | 459 |
| 019 | `FormateurKotlinTest.kt` | formatage | 9 | 211 |
| 020 | `MembresManquantsTest.kt` | diagnostics | 10 | 319 |
| 021 | `MesuresPerfDiagnosticsTest.kt` | caches-perf | 1 | 150 |
| 022 | `PreuveAbsenceProjetTest.kt` | diagnostics | 8 | 388 |
| 023 | `ReproductionAppelablesBuiltinTest.kt` | diagnostics | 2 | 141 |
| 024 | `ReproductionCompanionTest.kt` | diagnostics | 7 | 348 |
| 025 | `ReproductionComposeTest.kt` | diagnostics | 12 | 300 |
| 026 | `ReproductionExtensionsRecepteurNulTest.kt` | diagnostics | 2 | 150 |
| 027 | `ReproductionGradleKtsTest.kt` | diagnostics | 5 | 236 |
| 028 | `ReproductionHeritageVague4Test.kt` | diagnostics | 12 | 605 |
| 029 | `ReproductionJavaImbriqueTest.kt` | diagnostics | 5 | 256 |
| 030 | `ReproductionKmpAvanceTest.kt` | diagnostics | 5 | 236 |
| 031 | `ReproductionKmpTest.kt` | diagnostics | 5 | 226 |
| 032 | `ReproductionMembresClasspathTest.kt` | diagnostics | 3 | 211 |
| 033 | `ReproductionObjetsClasspathTest.kt` | diagnostics | 9 | 460 |
| 034 | `ReproductionSegmentsPersistantsTest.kt` | index-classpath | 3 | 193 |
| 035 | `ReproductionSourcesGenereesTest.kt` | diagnostics | 2 | 160 |
| 036 | `ReproductionTroncationScanTest.kt` | diagnostics | 3 | 161 |
| 037 | `ReproductionTypealiasClasspathTest.kt` | diagnostics | 5 | 251 |
| 038 | `ReproductionVague5Test.kt` | completion | 8 | 511 |
| 039 | `ReproductionViewBindingTest.kt` | completion | 1 | 175 |
| 040 | `ReproductionViewsTest.kt` | diagnostics | 11 | 490 |
| 041 | `ScanProjetTest.kt` | index-classpath | 15 | 277 |
| 042 | `SessionLangageKotlinA211Test.kt` | index-classpath | 4 | 163 |
| 043 | `SessionLangageKotlinCompletionContextuelleTest.kt` | completion | 14 | 308 |
| 044 | `SessionLangageKotlinHeritageTest.kt` | diagnostics | 13 | 526 |
| 045 | `SessionLangageKotlinK3Test.kt` | completion | 21 | 640 |
| 046 | `SessionLangageKotlinK4Test.kt` | diagnostics | 39 | 938 |
| 047 | `SessionLangageKotlinK5Test.kt` | diagnostics | 21 | 781 |
| 048 | `SessionLangageKotlinOccurrencesTest.kt` | navigation | 21 | 519 |
| 049 | `SessionLangageKotlinParseTest.kt` | cycle-vie-lsp | 26 | 381 |
| 050 | `SessionLangageKotlinPreviewsTest.kt` | autre | 16 | 527 |
| 051 | `SessionLangageKotlinSignatureTest.kt` | hover-inlay | 12 | 297 |
| 052 | `SuperKotlinTest.kt` | navigation | 10 | 357 |
| 053 | `TypesKotlinTest.kt` | navigation | 7 | 278 |
| **Total** | **53 fichiers** | — | **469** | **15853** |

## Agrégat par domaine (comportements)

| Domaine | Comportements |
|---|---:|
| diagnostics | 204 |
| index-classpath | 50 |
| hover-inlay | 44 |
| completion | 44 |
| navigation | 38 |
| cycle-vie-lsp | 29 |
| parse | 27 |
| autre | 16 |
| formatage | 9 |
| folding | 7 |
| caches-perf | 1 |
| **Total** | **469** |