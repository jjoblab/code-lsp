# MIGRATION-CHEMINS — table ancien → nouveau (exécutée le 03/11/2026)

> Générée par `scripts/generer-migration-chemins.py` (relue à la main) à partir de
> `git ls-files` de la branche `integration/fusion`. **Aucun déplacement effectué**
> — la table devient exécutoire après validation du plan par le propriétaire
> (`docs/REORGANISATION.md`). Les chemins marqués D-x portent une décision ouverte
> (recommandation de l’agent) ; les autres sont directs.

Fichiers classés : 923 — java : 463 · kotlin : 374 · socle : 63 · transverse : 23.

## 1. Table racine (une ligne par dossier/fichier racine)

| Ancien | Nouveau | Classe(s) | Fichiers | Décision |
|---|---|---|---:|---|
| `.githooks/` | `.githooks` | transverse | 1 |  |
| `.github/` | `.github` | transverse | 2 |  |
| `.gitignore` | `.gitignore` | transverse | 1 |  |
| `AGENTS.md` | `AGENTS.md` | transverse | 1 |  |
| `bancs/` | `ÉCLATÉ (voir §2–§4)` | java + kotlin + transverse | 19 | D-3 |
| `build.gradle.kts` | `build.gradle.kts` | transverse | 1 |  |
| `docs/` | `ÉCLATÉ (voir §2–§4)` | java + kotlin + transverse | 290 | D-4 |
| `ecj-art/` | `socle/ecj-art` | socle | 12 |  |
| `gradle/` | `gradle` | transverse | 2 |  |
| `gradle.properties` | `gradle.properties` | transverse | 1 |  |
| `gradlew` | `gradlew` | transverse | 1 |  |
| `gradlew.bat` | `gradlew.bat` | transverse | 1 |  |
| `index/` | `socle/index` | socle | 34 |  |
| `java/` | `lsp-java/moteur/` | java | 257 |  |
| `kotlin/` | `lsp-kotlin/moteur/` | kotlin | 191 |  |
| `LICENSE` | `LICENSE` | transverse | 1 |  |
| `model/` | `socle/model` | socle | 13 |  |
| `NOTICE` | `NOTICE` | transverse | 1 |  |
| `README.md` | `README.md` | transverse | 1 |  |
| `scripts/` | `ÉCLATÉ (voir §2–§4)` | java + kotlin + transverse | 15 | D-5 |
| `settings.gradle.kts` | `settings.gradle.kts` | transverse | 1 |  |
| `spike/` | `lsp-java/spike/` | java | 24 |  |
| `spike-kotlin/` | `lsp-kotlin/spike/` | kotlin | 48 |  |
| `testing/` | `socle/testing` | socle | 4 |  |
| `USAGE.md` | `lsp-java/USAGE.md` | java | 1 | D-6 |

Sections 2–4 : détail par fichier pour les répertoires éclatés (`docs/`, `bancs/`, `scripts/`, fichiers racine).

## 2. Détail — documents (`docs/`)

| Ancien | Nouveau | Classe | Décision |
|---|---|---|---|
| `docs/BASCULE-CODEIDE.md` | `lsp-java/docs/BASCULE-CODEIDE.md` | java | D-1 |
| `docs/ECARTS-SERVEUR.md` | `lsp-java/docs/ECARTS-SERVEUR.md` | java | D-1 |
| `docs/INTEGRATION-CODEIDE.md` | `lsp-java/docs/INTEGRATION-CODEIDE.md` | java | D-1 |
| `docs/MIGRATION-CHEMINS.md` | `docs/MIGRATION-CHEMINS.md` | transverse |  |
| `docs/PLAN-REECRITURE.md` | `lsp-java/docs/PLAN-REECRITURE.md` | java | D-1 |
| `docs/PROVENANCE-REPRISE-KOTLIN.md` | `lsp-kotlin/docs/PROVENANCE-REPRISE-KOTLIN.md` | kotlin |  |
| `docs/PROVENANCE-REPRISE.md` | `lsp-java/docs/PROVENANCE-REPRISE.md` | java |  |
| `docs/RECALAGE-1.md` | `lsp-java/docs/RECALAGE-1.md` | java | D-1 |
| `docs/RECALAGE-2.md` | `lsp-java/docs/RECALAGE-2.md` | java | D-1 |
| `docs/REGISTRE-CLIENT-K2.md` | `lsp-java/docs/REGISTRE-CLIENT-K2.md` | java | D-1 |
| `docs/REORGANISATION-AVANT.md` | `docs/REORGANISATION-AVANT.md` | transverse |  |
| `docs/REORGANISATION.md` | `docs/REORGANISATION.md` | transverse |  |
| `docs/adr/0001-licence-et-regles-de-reprise.md` | `lsp-java/docs/adr/0001-licence-et-regles-de-reprise.md` | java |  |
| `docs/adr/0002-perimetre-du-depot-et-modules.md` | `lsp-java/docs/adr/0002-perimetre-du-depot-et-modules.md` | java |  |
| `docs/adr/0003-index-de-l-etude.md` | `lsp-java/docs/adr/0003-index-de-l-etude.md` | java |  |
| `docs/adr/0004-strategie-globale.md` | `lsp-java/docs/adr/0004-strategie-globale.md` | java |  |
| `docs/adr/0005-packaging-et-compatibilite-art.md` | `lsp-java/docs/adr/0005-packaging-et-compatibilite-art.md` | java |  |
| `docs/adr/0006-parsing-et-incrementalite.md` | `lsp-java/docs/adr/0006-parsing-et-incrementalite.md` | java |  |
| `docs/adr/0007-resolution-et-inference.md` | `lsp-java/docs/adr/0007-resolution-et-inference.md` | java |  |
| `docs/adr/0008-index-stubs-disque.md` | `lsp-java/docs/adr/0008-index-stubs-disque.md` | java |  |
| `docs/adr/0009-lecture-du-bytecode.md` | `lsp-java/docs/adr/0009-lecture-du-bytecode.md` | java |  |
| `docs/adr/0010-concurrence-annulation-priorites.md` | `lsp-java/docs/adr/0010-concurrence-annulation-priorites.md` | java |  |
| `docs/adr/0011-memoire-et-cycle-de-vie.md` | `lsp-java/docs/adr/0011-memoire-et-cycle-de-vie.md` | java |  |
| `docs/adr/0012-completion-et-classement.md` | `lsp-java/docs/adr/0012-completion-et-classement.md` | java |  |
| `docs/adr/0013-diagnostics-inspections-fidelite.md` | `lsp-java/docs/adr/0013-diagnostics-inspections-fidelite.md` | java |  |
| `docs/adr/0014-navigation-refactorings-usages.md` | `lsp-java/docs/adr/0014-navigation-refactorings-usages.md` | java |  |
| `docs/adr/0015-integration-lsp-et-remplacement-progressif.md` | `lsp-java/docs/adr/0015-integration-lsp-et-remplacement-progressif.md` | java |  |
| `docs/adr/0016-banc-de-qualite-et-non-regression.md` | `lsp-java/docs/adr/0016-banc-de-qualite-et-non-regression.md` | java |  |
| `docs/adr/0017-licences-et-provenance-de-la-solution.md` | `lsp-java/docs/adr/0017-licences-et-provenance-de-la-solution.md` | java |  |
| `docs/adr/0018-version-jdt-et-shims.md` | `lsp-java/docs/adr/0018-version-jdt-et-shims.md` | java |  |
| `docs/adr/0019-modele-de-code-tolerant-et-contexte-de-completion.md` | `lsp-java/docs/adr/0019-modele-de-code-tolerant-et-contexte-de-completion.md` | java |  |
| `docs/adr/0020-modele-de-projet-android-et-referentiel-de-ressources.md` | `lsp-java/docs/adr/0020-modele-de-projet-android-et-referentiel-de-ressources.md` | java |  |
| `docs/adr/0021-classes-light-android.md` | `lsp-java/docs/adr/0021-classes-light-android.md` | java |  |
| `docs/adr/0022-interoperabilite-kotlin-vers-java.md` | `lsp-java/docs/adr/0022-interoperabilite-kotlin-vers-java.md` | java |  |
| `docs/adr/0023-moteur-d-inspections-et-profil.md` | `lsp-java/docs/adr/0023-moteur-d-inspections-et-profil.md` | java |  |
| `docs/adr/0024-controles-android-lint-a-valeur-java.md` | `lsp-java/docs/adr/0024-controles-android-lint-a-valeur-java.md` | java |  |
| `docs/adr/0025-coherence-des-diagnostics.md` | `lsp-java/docs/adr/0025-coherence-des-diagnostics.md` | java |  |
| `docs/adr/0026-completion-niveau-android-studio.md` | `lsp-java/docs/adr/0026-completion-niveau-android-studio.md` | java |  |
| `docs/adr/0027-actions-de-code-etendues.md` | `lsp-java/docs/adr/0027-actions-de-code-etendues.md` | java |  |
| `docs/adr/0028-recherche-et-navigation-exhaustives.md` | `lsp-java/docs/adr/0028-recherche-et-navigation-exhaustives.md` | java |  |
| `docs/adr/0029-performance-concurrence-memoire-art.md` | `lsp-java/docs/adr/0029-performance-concurrence-memoire-art.md` | java |  |
| `docs/adr/0030-qualite-mesuree-niveau-android-studio.md` | `lsp-java/docs/adr/0030-qualite-mesuree-niveau-android-studio.md` | java |  |
| `docs/adr/0031-surface-protocole.md` | `lsp-java/docs/adr/0031-surface-protocole.md` | java |  |
| `docs/adr/0032-complement-de-catalogue-familles-unitaires.md` | `lsp-java/docs/adr/0032-complement-de-catalogue-familles-unitaires.md` | java |  |
| `docs/adr/0100-voie-gouvernance-et-regles-de-reprise-kotlin.md` | `lsp-kotlin/docs/adr/0100-voie-gouvernance-et-regles-de-reprise-kotlin.md` | kotlin |  |
| `docs/adr/0101-perimetre-et-frontieres-kotlin.md` | `lsp-kotlin/docs/adr/0101-perimetre-et-frontieres-kotlin.md` | kotlin |  |
| `docs/adr/0102-index-de-l-etude-kotlin.md` | `lsp-kotlin/docs/adr/0102-index-de-l-etude-kotlin.md` | kotlin |  |
| `docs/adr/0103-strategie-semantique-kotlin.md` | `lsp-kotlin/docs/adr/0103-strategie-semantique-kotlin.md` | kotlin |  |
| `docs/adr/0104-syntaxe-grammaire-officielle-et-incrementalite.md` | `lsp-kotlin/docs/adr/0104-syntaxe-grammaire-officielle-et-incrementalite.md` | kotlin |  |
| `docs/adr/0105-modele-de-symboles-kotlin.md` | `lsp-kotlin/docs/adr/0105-modele-de-symboles-kotlin.md` | kotlin |  |
| `docs/adr/0106-resolution-portees-imports.md` | `lsp-kotlin/docs/adr/0106-resolution-portees-imports.md` | kotlin |  |
| `docs/adr/0107-typage-et-inference-bornes.md` | `lsp-kotlin/docs/adr/0107-typage-et-inference-bornes.md` | kotlin |  |
| `docs/adr/0108-classpath-metadonnees-et-index.md` | `lsp-kotlin/docs/adr/0108-classpath-metadonnees-et-index.md` | kotlin |  |
| `docs/adr/0109-synthetiques-android-et-genere.md` | `lsp-kotlin/docs/adr/0109-synthetiques-android-et-genere.md` | kotlin |  |
| `docs/adr/0110-interop-java-kotlin.md` | `lsp-kotlin/docs/adr/0110-interop-java-kotlin.md` | kotlin |  |
| `docs/adr/0111-gradle-kotlin-dsl.md` | `lsp-kotlin/docs/adr/0111-gradle-kotlin-dsl.md` | kotlin |  |
| `docs/adr/0112-diagnostics-fidelite-et-mode-profond.md` | `lsp-kotlin/docs/adr/0112-diagnostics-fidelite-et-mode-profond.md` | kotlin |  |
| `docs/adr/0113-completion-et-classement.md` | `lsp-kotlin/docs/adr/0113-completion-et-classement.md` | kotlin |  |
| `docs/adr/0114-navigation-renommage-usages.md` | `lsp-kotlin/docs/adr/0114-navigation-renommage-usages.md` | kotlin |  |
| `docs/adr/0115-formatage-imports-pliage-actions.md` | `lsp-kotlin/docs/adr/0115-formatage-imports-pliage-actions.md` | kotlin |  |
| `docs/adr/0116-compose-previews.md` | `lsp-kotlin/docs/adr/0116-compose-previews.md` | kotlin |  |
| `docs/adr/0117-concurrence-annulation-sessions-memoire.md` | `lsp-kotlin/docs/adr/0117-concurrence-annulation-sessions-memoire.md` | kotlin |  |
| `docs/adr/0118-integration-lsp-et-remplacement-progressif.md` | `lsp-kotlin/docs/adr/0118-integration-lsp-et-remplacement-progressif.md` | kotlin |  |
| `docs/adr/0119-banc-de-qualite-et-non-regression.md` | `lsp-kotlin/docs/adr/0119-banc-de-qualite-et-non-regression.md` | kotlin |  |
| `docs/adr/0120-licences-et-provenance-kotlin.md` | `lsp-kotlin/docs/adr/0120-licences-et-provenance-kotlin.md` | kotlin |  |
| `docs/adr/0121-suivi-des-versions-kotlin-et-grammaire.md` | `lsp-kotlin/docs/adr/0121-suivi-des-versions-kotlin-et-grammaire.md` | kotlin |  |
| `docs/adr/0122-architecture-semantique-a-paliers.md` | `lsp-kotlin/docs/adr/0122-architecture-semantique-a-paliers.md` | kotlin |  |
| `docs/adr/0123-systeme-de-types-et-inference-palier-2.md` | `lsp-kotlin/docs/adr/0123-systeme-de-types-et-inference-palier-2.md` | kotlin |  |
| `docs/adr/0124-mode-profond-k2-palier-3.md` | `lsp-kotlin/docs/adr/0124-mode-profond-k2-palier-3.md` | kotlin |  |
| `docs/adr/0125-catalogue-diagnostics-par-decidabilite.md` | `lsp-kotlin/docs/adr/0125-catalogue-diagnostics-par-decidabilite.md` | kotlin |  |
| `docs/adr/0126-completion-niveau-android-studio.md` | `lsp-kotlin/docs/adr/0126-completion-niveau-android-studio.md` | kotlin |  |
| `docs/adr/0127-modele-projet-android-et-code-genere-mutualise.md` | `lsp-kotlin/docs/adr/0127-modele-projet-android-et-code-genere-mutualise.md` | kotlin |  |
| `docs/adr/0128-compose-et-coroutines.md` | `lsp-kotlin/docs/adr/0128-compose-et-coroutines.md` | kotlin |  |
| `docs/adr/0129-interop-java-kotlin-bidirectionnelle.md` | `lsp-kotlin/docs/adr/0129-interop-java-kotlin-bidirectionnelle.md` | kotlin |  |
| `docs/adr/0130-gradle-kotlin-dsl-et-catalogues.md` | `lsp-kotlin/docs/adr/0130-gradle-kotlin-dsl-et-catalogues.md` | kotlin |  |
| `docs/adr/0131-actions-et-refactorings-kotlin.md` | `lsp-kotlin/docs/adr/0131-actions-et-refactorings-kotlin.md` | kotlin |  |
| `docs/adr/0132-usages-et-navigation-a-l-echelle.md` | `lsp-kotlin/docs/adr/0132-usages-et-navigation-a-l-echelle.md` | kotlin |  |
| `docs/adr/0133-budget-dex-taille-et-packaging.md` | `lsp-kotlin/docs/adr/0133-budget-dex-taille-et-packaging.md` | kotlin |  |
| `docs/adr/0134-performance-concurrence-memoire-art.md` | `lsp-kotlin/docs/adr/0134-performance-concurrence-memoire-art.md` | kotlin |  |
| `docs/adr/0135-qualite-mesuree-niveau-android-studio.md` | `lsp-kotlin/docs/adr/0135-qualite-mesuree-niveau-android-studio.md` | kotlin |  |
| `docs/adr/0136-surface-protocole-et-bascule-codeide.md` | `lsp-kotlin/docs/adr/0136-surface-protocole-et-bascule-codeide.md` | kotlin |  |
| `docs/cahier-specifications-kotlin/INVENTAIRE.md` | `lsp-kotlin/docs/cahier-specifications-kotlin/INVENTAIRE.md` | kotlin |  |
| `docs/cahier-specifications-kotlin/README.md` | `lsp-kotlin/docs/cahier-specifications-kotlin/README.md` | kotlin |  |
| `docs/cahier-specifications-kotlin/fiches/001-ChargeurMoteurKotlinTest.md` | `lsp-kotlin/docs/cahier-specifications-kotlin/fiches/001-ChargeurMoteurKotlinTest.md` | kotlin |  |
| `docs/cahier-specifications-kotlin/fiches/002-DecodeurMetadonneesTest.md` | `lsp-kotlin/docs/cahier-specifications-kotlin/fiches/002-DecodeurMetadonneesTest.md` | kotlin |  |
| `docs/cahier-specifications-kotlin/fiches/003-LecteurClassBrutDifferentielTest.md` | `lsp-kotlin/docs/cahier-specifications-kotlin/fiches/003-LecteurClassBrutDifferentielTest.md` | kotlin |  |
| `docs/cahier-specifications-kotlin/fiches/004-LecteurClassBrutTest.md` | `lsp-kotlin/docs/cahier-specifications-kotlin/fiches/004-LecteurClassBrutTest.md` | kotlin |  |
| `docs/cahier-specifications-kotlin/fiches/005-SondeTypealiasKotlinxTest.md` | `lsp-kotlin/docs/cahier-specifications-kotlin/fiches/005-SondeTypealiasKotlinxTest.md` | kotlin |  |
| `docs/cahier-specifications-kotlin/fiches/006-ExplicationNonResoluTest.md` | `lsp-kotlin/docs/cahier-specifications-kotlin/fiches/006-ExplicationNonResoluTest.md` | kotlin |  |
| `docs/cahier-specifications-kotlin/fiches/007-DocumentationKotlinTest.md` | `lsp-kotlin/docs/cahier-specifications-kotlin/fiches/007-DocumentationKotlinTest.md` | kotlin |  |
| `docs/cahier-specifications-kotlin/fiches/008-IndexAppelablesKotlinTest.md` | `lsp-kotlin/docs/cahier-specifications-kotlin/fiches/008-IndexAppelablesKotlinTest.md` | kotlin |  |
| `docs/cahier-specifications-kotlin/fiches/009-PasseurIndexationKotlinTest.md` | `lsp-kotlin/docs/cahier-specifications-kotlin/fiches/009-PasseurIndexationKotlinTest.md` | kotlin |  |
| `docs/cahier-specifications-kotlin/fiches/010-PariteCorpusDepotTest.md` | `lsp-kotlin/docs/cahier-specifications-kotlin/fiches/010-PariteCorpusDepotTest.md` | kotlin |  |
| `docs/cahier-specifications-kotlin/fiches/011-ToleranceErreursTest.md` | `lsp-kotlin/docs/cahier-specifications-kotlin/fiches/011-ToleranceErreursTest.md` | kotlin |  |
| `docs/cahier-specifications-kotlin/fiches/012-ParseurKotlinTest.md` | `lsp-kotlin/docs/cahier-specifications-kotlin/fiches/012-ParseurKotlinTest.md` | kotlin |  |
| `docs/cahier-specifications-kotlin/fiches/013-FacadeKtTest.md` | `lsp-kotlin/docs/cahier-specifications-kotlin/fiches/013-FacadeKtTest.md` | kotlin |  |
| `docs/cahier-specifications-kotlin/fiches/014-PlisDocumentKotlinTest.md` | `lsp-kotlin/docs/cahier-specifications-kotlin/fiches/014-PlisDocumentKotlinTest.md` | kotlin |  |
| `docs/cahier-specifications-kotlin/fiches/015-CapacitesL2KotlinTest.md` | `lsp-kotlin/docs/cahier-specifications-kotlin/fiches/015-CapacitesL2KotlinTest.md` | kotlin |  |
| `docs/cahier-specifications-kotlin/fiches/016-CorrectifsTexteTest.md` | `lsp-kotlin/docs/cahier-specifications-kotlin/fiches/016-CorrectifsTexteTest.md` | kotlin |  |
| `docs/cahier-specifications-kotlin/fiches/017-DiagnosticsParDeclarationTest.md` | `lsp-kotlin/docs/cahier-specifications-kotlin/fiches/017-DiagnosticsParDeclarationTest.md` | kotlin |  |
| `docs/cahier-specifications-kotlin/fiches/018-FicheClasspathTest.md` | `lsp-kotlin/docs/cahier-specifications-kotlin/fiches/018-FicheClasspathTest.md` | kotlin |  |
| `docs/cahier-specifications-kotlin/fiches/019-FormateurKotlinTest.md` | `lsp-kotlin/docs/cahier-specifications-kotlin/fiches/019-FormateurKotlinTest.md` | kotlin |  |
| `docs/cahier-specifications-kotlin/fiches/020-MembresManquantsTest.md` | `lsp-kotlin/docs/cahier-specifications-kotlin/fiches/020-MembresManquantsTest.md` | kotlin |  |
| `docs/cahier-specifications-kotlin/fiches/021-MesuresPerfDiagnosticsTest.md` | `lsp-kotlin/docs/cahier-specifications-kotlin/fiches/021-MesuresPerfDiagnosticsTest.md` | kotlin |  |
| `docs/cahier-specifications-kotlin/fiches/022-PreuveAbsenceProjetTest.md` | `lsp-kotlin/docs/cahier-specifications-kotlin/fiches/022-PreuveAbsenceProjetTest.md` | kotlin |  |
| `docs/cahier-specifications-kotlin/fiches/023-ReproductionAppelablesBuiltinTest.md` | `lsp-kotlin/docs/cahier-specifications-kotlin/fiches/023-ReproductionAppelablesBuiltinTest.md` | kotlin |  |
| `docs/cahier-specifications-kotlin/fiches/024-ReproductionCompanionTest.md` | `lsp-kotlin/docs/cahier-specifications-kotlin/fiches/024-ReproductionCompanionTest.md` | kotlin |  |
| `docs/cahier-specifications-kotlin/fiches/025-ReproductionComposeTest.md` | `lsp-kotlin/docs/cahier-specifications-kotlin/fiches/025-ReproductionComposeTest.md` | kotlin |  |
| `docs/cahier-specifications-kotlin/fiches/026-ReproductionExtensionsRecepteurNulTest.md` | `lsp-kotlin/docs/cahier-specifications-kotlin/fiches/026-ReproductionExtensionsRecepteurNulTest.md` | kotlin |  |
| `docs/cahier-specifications-kotlin/fiches/027-ReproductionGradleKtsTest.md` | `lsp-kotlin/docs/cahier-specifications-kotlin/fiches/027-ReproductionGradleKtsTest.md` | kotlin |  |
| `docs/cahier-specifications-kotlin/fiches/028-ReproductionHeritageVague4Test.md` | `lsp-kotlin/docs/cahier-specifications-kotlin/fiches/028-ReproductionHeritageVague4Test.md` | kotlin |  |
| `docs/cahier-specifications-kotlin/fiches/029-ReproductionJavaImbriqueTest.md` | `lsp-kotlin/docs/cahier-specifications-kotlin/fiches/029-ReproductionJavaImbriqueTest.md` | kotlin |  |
| `docs/cahier-specifications-kotlin/fiches/030-ReproductionKmpAvanceTest.md` | `lsp-kotlin/docs/cahier-specifications-kotlin/fiches/030-ReproductionKmpAvanceTest.md` | kotlin |  |
| `docs/cahier-specifications-kotlin/fiches/031-ReproductionKmpTest.md` | `lsp-kotlin/docs/cahier-specifications-kotlin/fiches/031-ReproductionKmpTest.md` | kotlin |  |
| `docs/cahier-specifications-kotlin/fiches/032-ReproductionMembresClasspathTest.md` | `lsp-kotlin/docs/cahier-specifications-kotlin/fiches/032-ReproductionMembresClasspathTest.md` | kotlin |  |
| `docs/cahier-specifications-kotlin/fiches/033-ReproductionObjetsClasspathTest.md` | `lsp-kotlin/docs/cahier-specifications-kotlin/fiches/033-ReproductionObjetsClasspathTest.md` | kotlin |  |
| `docs/cahier-specifications-kotlin/fiches/034-ReproductionSegmentsPersistantsTest.md` | `lsp-kotlin/docs/cahier-specifications-kotlin/fiches/034-ReproductionSegmentsPersistantsTest.md` | kotlin |  |
| `docs/cahier-specifications-kotlin/fiches/035-ReproductionSourcesGenereesTest.md` | `lsp-kotlin/docs/cahier-specifications-kotlin/fiches/035-ReproductionSourcesGenereesTest.md` | kotlin |  |
| `docs/cahier-specifications-kotlin/fiches/036-ReproductionTroncationScanTest.md` | `lsp-kotlin/docs/cahier-specifications-kotlin/fiches/036-ReproductionTroncationScanTest.md` | kotlin |  |
| `docs/cahier-specifications-kotlin/fiches/037-ReproductionTypealiasClasspathTest.md` | `lsp-kotlin/docs/cahier-specifications-kotlin/fiches/037-ReproductionTypealiasClasspathTest.md` | kotlin |  |
| `docs/cahier-specifications-kotlin/fiches/038-ReproductionVague5Test.md` | `lsp-kotlin/docs/cahier-specifications-kotlin/fiches/038-ReproductionVague5Test.md` | kotlin |  |
| `docs/cahier-specifications-kotlin/fiches/039-ReproductionViewBindingTest.md` | `lsp-kotlin/docs/cahier-specifications-kotlin/fiches/039-ReproductionViewBindingTest.md` | kotlin |  |
| `docs/cahier-specifications-kotlin/fiches/040-ReproductionViewsTest.md` | `lsp-kotlin/docs/cahier-specifications-kotlin/fiches/040-ReproductionViewsTest.md` | kotlin |  |
| `docs/cahier-specifications-kotlin/fiches/041-ScanProjetTest.md` | `lsp-kotlin/docs/cahier-specifications-kotlin/fiches/041-ScanProjetTest.md` | kotlin |  |
| `docs/cahier-specifications-kotlin/fiches/042-SessionLangageKotlinA211Test.md` | `lsp-kotlin/docs/cahier-specifications-kotlin/fiches/042-SessionLangageKotlinA211Test.md` | kotlin |  |
| `docs/cahier-specifications-kotlin/fiches/043-SessionLangageKotlinCompletionContextuelleTest.md` | `lsp-kotlin/docs/cahier-specifications-kotlin/fiches/043-SessionLangageKotlinCompletionContextuelleTest.md` | kotlin |  |
| `docs/cahier-specifications-kotlin/fiches/044-SessionLangageKotlinHeritageTest.md` | `lsp-kotlin/docs/cahier-specifications-kotlin/fiches/044-SessionLangageKotlinHeritageTest.md` | kotlin |  |
| `docs/cahier-specifications-kotlin/fiches/045-SessionLangageKotlinK3Test.md` | `lsp-kotlin/docs/cahier-specifications-kotlin/fiches/045-SessionLangageKotlinK3Test.md` | kotlin |  |
| `docs/cahier-specifications-kotlin/fiches/046-SessionLangageKotlinK4Test.md` | `lsp-kotlin/docs/cahier-specifications-kotlin/fiches/046-SessionLangageKotlinK4Test.md` | kotlin |  |
| `docs/cahier-specifications-kotlin/fiches/047-SessionLangageKotlinK5Test.md` | `lsp-kotlin/docs/cahier-specifications-kotlin/fiches/047-SessionLangageKotlinK5Test.md` | kotlin |  |
| `docs/cahier-specifications-kotlin/fiches/048-SessionLangageKotlinOccurrencesTest.md` | `lsp-kotlin/docs/cahier-specifications-kotlin/fiches/048-SessionLangageKotlinOccurrencesTest.md` | kotlin |  |
| `docs/cahier-specifications-kotlin/fiches/049-SessionLangageKotlinParseTest.md` | `lsp-kotlin/docs/cahier-specifications-kotlin/fiches/049-SessionLangageKotlinParseTest.md` | kotlin |  |
| `docs/cahier-specifications-kotlin/fiches/050-SessionLangageKotlinPreviewsTest.md` | `lsp-kotlin/docs/cahier-specifications-kotlin/fiches/050-SessionLangageKotlinPreviewsTest.md` | kotlin |  |
| `docs/cahier-specifications-kotlin/fiches/051-SessionLangageKotlinSignatureTest.md` | `lsp-kotlin/docs/cahier-specifications-kotlin/fiches/051-SessionLangageKotlinSignatureTest.md` | kotlin |  |
| `docs/cahier-specifications-kotlin/fiches/052-SuperKotlinTest.md` | `lsp-kotlin/docs/cahier-specifications-kotlin/fiches/052-SuperKotlinTest.md` | kotlin |  |
| `docs/cahier-specifications-kotlin/fiches/053-TypesKotlinTest.md` | `lsp-kotlin/docs/cahier-specifications-kotlin/fiches/053-TypesKotlinTest.md` | kotlin |  |
| `docs/cahier-specifications/INVENTAIRE.md` | `lsp-java/docs/cahier-specifications/INVENTAIRE.md` | java |  |
| `docs/cahier-specifications/README.md` | `lsp-java/docs/cahier-specifications/README.md` | java |  |
| `docs/cahier-specifications/fiches/001-DiagnosticServiceTest.md` | `lsp-java/docs/cahier-specifications/fiches/001-DiagnosticServiceTest.md` | java |  |
| `docs/cahier-specifications/fiches/002-EcjChainedLambdaHintsTest.md` | `lsp-java/docs/cahier-specifications/fiches/002-EcjChainedLambdaHintsTest.md` | java |  |
| `docs/cahier-specifications/fiches/003-InlayHintTest.md` | `lsp-java/docs/cahier-specifications/fiches/003-InlayHintTest.md` | java |  |
| `docs/cahier-specifications/fiches/004-AndroidClasspathReproTest.md` | `lsp-java/docs/cahier-specifications/fiches/004-AndroidClasspathReproTest.md` | java |  |
| `docs/cahier-specifications/fiches/005-BinaryDefinitionFallbackTest.md` | `lsp-java/docs/cahier-specifications/fiches/005-BinaryDefinitionFallbackTest.md` | java |  |
| `docs/cahier-specifications/fiches/006-CachePresenceSourceTest.md` | `lsp-java/docs/cahier-specifications/fiches/006-CachePresenceSourceTest.md` | java |  |
| `docs/cahier-specifications/fiches/007-CompletionContextIntelligenceTest.md` | `lsp-java/docs/cahier-specifications/fiches/007-CompletionContextIntelligenceTest.md` | java |  |
| `docs/cahier-specifications/fiches/008-CompletionRegressionTest.md` | `lsp-java/docs/cahier-specifications/fiches/008-CompletionRegressionTest.md` | java |  |
| `docs/cahier-specifications/fiches/009-DecompiledSourceTest.md` | `lsp-java/docs/cahier-specifications/fiches/009-DecompiledSourceTest.md` | java |  |
| `docs/cahier-specifications/fiches/010-DeviceFqcnReproTest.md` | `lsp-java/docs/cahier-specifications/fiches/010-DeviceFqcnReproTest.md` | java |  |
| `docs/cahier-specifications/fiches/011-EcjCodeFolderTest.md` | `lsp-java/docs/cahier-specifications/fiches/011-EcjCodeFolderTest.md` | java |  |
| `docs/cahier-specifications/fiches/012-EcjCompilerEngineConcurrencyTest.md` | `lsp-java/docs/cahier-specifications/fiches/012-EcjCompilerEngineConcurrencyTest.md` | java |  |
| `docs/cahier-specifications/fiches/013-EcjCompilerEngineTest.md` | `lsp-java/docs/cahier-specifications/fiches/013-EcjCompilerEngineTest.md` | java |  |
| `docs/cahier-specifications/fiches/014-EcjFastDiagnoseTest.md` | `lsp-java/docs/cahier-specifications/fiches/014-EcjFastDiagnoseTest.md` | java |  |
| `docs/cahier-specifications/fiches/015-EcjFastFeaturesTest.md` | `lsp-java/docs/cahier-specifications/fiches/015-EcjFastFeaturesTest.md` | java |  |
| `docs/cahier-specifications/fiches/016-EcjFormatterRangeTest.md` | `lsp-java/docs/cahier-specifications/fiches/016-EcjFormatterRangeTest.md` | java |  |
| `docs/cahier-specifications/fiches/017-EcjFormatterTest.md` | `lsp-java/docs/cahier-specifications/fiches/017-EcjFormatterTest.md` | java |  |
| `docs/cahier-specifications/fiches/018-EcjHierarchiesTest.md` | `lsp-java/docs/cahier-specifications/fiches/018-EcjHierarchiesTest.md` | java |  |
| `docs/cahier-specifications/fiches/019-EcjHierarchyDiagnosticsTest.md` | `lsp-java/docs/cahier-specifications/fiches/019-EcjHierarchyDiagnosticsTest.md` | java |  |
| `docs/cahier-specifications/fiches/020-EcjHoverDefinitionCompletionTest.md` | `lsp-java/docs/cahier-specifications/fiches/020-EcjHoverDefinitionCompletionTest.md` | java |  |
| `docs/cahier-specifications/fiches/021-EcjHoverJavadocTest.md` | `lsp-java/docs/cahier-specifications/fiches/021-EcjHoverJavadocTest.md` | java |  |
| `docs/cahier-specifications/fiches/022-EcjImportOrganizerTest.md` | `lsp-java/docs/cahier-specifications/fiches/022-EcjImportOrganizerTest.md` | java |  |
| `docs/cahier-specifications/fiches/023-EcjInlayAndSignatureTest.md` | `lsp-java/docs/cahier-specifications/fiches/023-EcjInlayAndSignatureTest.md` | java |  |
| `docs/cahier-specifications/fiches/024-EcjLocateCacheTest.md` | `lsp-java/docs/cahier-specifications/fiches/024-EcjLocateCacheTest.md` | java |  |
| `docs/cahier-specifications/fiches/025-EcjParseCacheTest.md` | `lsp-java/docs/cahier-specifications/fiches/025-EcjParseCacheTest.md` | java |  |
| `docs/cahier-specifications/fiches/026-EcjRenameEngineTest.md` | `lsp-java/docs/cahier-specifications/fiches/026-EcjRenameEngineTest.md` | java |  |
| `docs/cahier-specifications/fiches/027-EcjSelectionRangesTest.md` | `lsp-java/docs/cahier-specifications/fiches/027-EcjSelectionRangesTest.md` | java |  |
| `docs/cahier-specifications/fiches/028-EcjSyntaxInstantTest.md` | `lsp-java/docs/cahier-specifications/fiches/028-EcjSyntaxInstantTest.md` | java |  |
| `docs/cahier-specifications/fiches/029-EcjTemplatesTest.md` | `lsp-java/docs/cahier-specifications/fiches/029-EcjTemplatesTest.md` | java |  |
| `docs/cahier-specifications/fiches/030-EcjVarTypeHintsTest.md` | `lsp-java/docs/cahier-specifications/fiches/030-EcjVarTypeHintsTest.md` | java |  |
| `docs/cahier-specifications/fiches/031-ExternalJrtTest.md` | `lsp-java/docs/cahier-specifications/fiches/031-ExternalJrtTest.md` | java |  |
| `docs/cahier-specifications/fiches/032-FuiteClassesImbriqueesTest.md` | `lsp-java/docs/cahier-specifications/fiches/032-FuiteClassesImbriqueesTest.md` | java |  |
| `docs/cahier-specifications/fiches/033-LatenceCompletionBenchmark.md` | `lsp-java/docs/cahier-specifications/fiches/033-LatenceCompletionBenchmark.md` | java |  |
| `docs/cahier-specifications/fiches/034-MoteurParModuleTest.md` | `lsp-java/docs/cahier-specifications/fiches/034-MoteurParModuleTest.md` | java |  |
| `docs/cahier-specifications/fiches/035-NomZeroItemRegressionTest.md` | `lsp-java/docs/cahier-specifications/fiches/035-NomZeroItemRegressionTest.md` | java |  |
| `docs/cahier-specifications/fiches/036-PopupSpontaneReproTest.md` | `lsp-java/docs/cahier-specifications/fiches/036-PopupSpontaneReproTest.md` | java |  |
| `docs/cahier-specifications/fiches/037-PopupSpontaneReproTestHelper.md` | `lsp-java/docs/cahier-specifications/fiches/037-PopupSpontaneReproTestHelper.md` | java |  |
| `docs/cahier-specifications/fiches/038-RawZipReaderTest.md` | `lsp-java/docs/cahier-specifications/fiches/038-RawZipReaderTest.md` | java |  |
| `docs/cahier-specifications/fiches/039-RealClasspathDeviceReproTest.md` | `lsp-java/docs/cahier-specifications/fiches/039-RealClasspathDeviceReproTest.md` | java |  |
| `docs/cahier-specifications/fiches/040-ScratchAstShapesTest.md` | `lsp-java/docs/cahier-specifications/fiches/040-ScratchAstShapesTest.md` | java |  |
| `docs/cahier-specifications/fiches/041-SourceFocuserTest.md` | `lsp-java/docs/cahier-specifications/fiches/041-SourceFocuserTest.md` | java |  |
| `docs/cahier-specifications/fiches/042-StringConcatFactoryReproTest.md` | `lsp-java/docs/cahier-specifications/fiches/042-StringConcatFactoryReproTest.md` | java |  |
| `docs/cahier-specifications/fiches/043-SubpackageDiagnosticTest.md` | `lsp-java/docs/cahier-specifications/fiches/043-SubpackageDiagnosticTest.md` | java |  |
| `docs/cahier-specifications/fiches/044-SubpackageGoldenRulesTest.md` | `lsp-java/docs/cahier-specifications/fiches/044-SubpackageGoldenRulesTest.md` | java |  |
| `docs/cahier-specifications/fiches/045-CompatShimsTest.md` | `lsp-java/docs/cahier-specifications/fiches/045-CompatShimsTest.md` | java |  |
| `docs/cahier-specifications/fiches/046-CancelTokenTest.md` | `lsp-java/docs/cahier-specifications/fiches/046-CancelTokenTest.md` | java |  |
| `docs/cahier-specifications/fiches/047-CompilationEnvironmentRouteurTest.md` | `lsp-java/docs/cahier-specifications/fiches/047-CompilationEnvironmentRouteurTest.md` | java |  |
| `docs/cahier-specifications/fiches/048-EcjEngineSchedulerTest.md` | `lsp-java/docs/cahier-specifications/fiches/048-EcjEngineSchedulerTest.md` | java |  |
| `docs/cahier-specifications/fiches/049-AuditNavigationJavaTest.md` | `lsp-java/docs/cahier-specifications/fiches/049-AuditNavigationJavaTest.md` | java |  |
| `docs/cahier-specifications/fiches/050-CompletionKindMappingTest.md` | `lsp-java/docs/cahier-specifications/fiches/050-CompletionKindMappingTest.md` | java |  |
| `docs/cahier-specifications/fiches/051-DatabindingImportDowngradeTest.md` | `lsp-java/docs/cahier-specifications/fiches/051-DatabindingImportDowngradeTest.md` | java |  |
| `docs/cahier-specifications/fiches/052-FoldingRangeFacadeTest.md` | `lsp-java/docs/cahier-specifications/fiches/052-FoldingRangeFacadeTest.md` | java |  |
| `docs/cahier-specifications/fiches/053-ImportCompletionIntegrationTest.md` | `lsp-java/docs/cahier-specifications/fiches/053-ImportCompletionIntegrationTest.md` | java |  |
| `docs/cahier-specifications/fiches/054-JavaL4BoutEnBoutTest.md` | `lsp-java/docs/cahier-specifications/fiches/054-JavaL4BoutEnBoutTest.md` | java |  |
| `docs/cahier-specifications/fiches/055-JavaLanguageServerLifecycleTest.md` | `lsp-java/docs/cahier-specifications/fiches/055-JavaLanguageServerLifecycleTest.md` | java |  |
| `docs/cahier-specifications/fiches/056-JavaS1RicheTest.md` | `lsp-java/docs/cahier-specifications/fiches/056-JavaS1RicheTest.md` | java |  |
| `docs/cahier-specifications/fiches/057-JavaS2ConfortTest.md` | `lsp-java/docs/cahier-specifications/fiches/057-JavaS2ConfortTest.md` | java |  |
| `docs/cahier-specifications/fiches/058-JavaS2bProtocoleTest.md` | `lsp-java/docs/cahier-specifications/fiches/058-JavaS2bProtocoleTest.md` | java |  |
| `docs/cahier-specifications/fiches/059-LifecycleHygieneTest.md` | `lsp-java/docs/cahier-specifications/fiches/059-LifecycleHygieneTest.md` | java |  |
| `docs/cahier-specifications/fiches/060-NavigationFacadeTest.md` | `lsp-java/docs/cahier-specifications/fiches/060-NavigationFacadeTest.md` | java |  |
| `docs/cahier-specifications/fiches/061-RenameHonneteTest.md` | `lsp-java/docs/cahier-specifications/fiches/061-RenameHonneteTest.md` | java |  |
| `docs/cahier-specifications/fiches/062-TypeDefinitionFacadeTest.md` | `lsp-java/docs/cahier-specifications/fiches/062-TypeDefinitionFacadeTest.md` | java |  |
| `docs/cahier-specifications/fiches/063-CodeActionServiceTest.md` | `lsp-java/docs/cahier-specifications/fiches/063-CodeActionServiceTest.md` | java |  |
| `docs/cahier-specifications/fiches/064-CreateMethodFixTest.md` | `lsp-java/docs/cahier-specifications/fiches/064-CreateMethodFixTest.md` | java |  |
| `docs/cahier-specifications/fiches/065-JavaProblemFamilyTest.md` | `lsp-java/docs/cahier-specifications/fiches/065-JavaProblemFamilyTest.md` | java |  |
| `docs/cahier-specifications/fiches/066-EnvironnementVivantServeurTest.md` | `lsp-java/docs/cahier-specifications/fiches/066-EnvironnementVivantServeurTest.md` | java |  |
| `docs/etudes/editeur-lsp/01-responsabilites-editeur-lsp.md` | `docs/etudes/editeur-lsp/01-responsabilites-editeur-lsp.md` | transverse | D-4 |
| `docs/etudes/moteur-java-psi/00-synthese.md` | `lsp-java/docs/etudes/moteur-java-psi/00-synthese.md` | java |  |
| `docs/etudes/moteur-java-psi/00b-inventaire-reprise.md` | `lsp-java/docs/etudes/moteur-java-psi/00b-inventaire-reprise.md` | java |  |
| `docs/etudes/moteur-java-psi/01-architecture-psi.md` | `lsp-java/docs/etudes/moteur-java-psi/01-architecture-psi.md` | java |  |
| `docs/etudes/moteur-java-psi/02-spike-mesures.md` | `lsp-java/docs/etudes/moteur-java-psi/02-spike-mesures.md` | java |  |
| `docs/etudes/moteur-java-psi/ALERTES.md` | `lsp-java/docs/etudes/moteur-java-psi/ALERTES.md` | java |  |
| `docs/etudes/moteur-java-psi/annexes/exp-D11D12.md` | `lsp-java/docs/etudes/moteur-java-psi/annexes/exp-D11D12.md` | java |  |
| `docs/etudes/moteur-java-psi/annexes/exp-D7D8.md` | `lsp-java/docs/etudes/moteur-java-psi/annexes/exp-D7D8.md` | java |  |
| `docs/etudes/moteur-java-psi/annexes/exp-D9D10.md` | `lsp-java/docs/etudes/moteur-java-psi/annexes/exp-D9D10.md` | java |  |
| `docs/etudes/moteur-kotlin/00-synthese.md` | `lsp-kotlin/docs/etudes/moteur-kotlin/00-synthese.md` | kotlin |  |
| `docs/etudes/moteur-kotlin/00b-inventaire-existant.md` | `lsp-kotlin/docs/etudes/moteur-kotlin/00b-inventaire-existant.md` | kotlin |  |
| `docs/etudes/moteur-kotlin/01-architectures-analyse-kotlin.md` | `lsp-kotlin/docs/etudes/moteur-kotlin/01-architectures-analyse-kotlin.md` | kotlin |  |
| `docs/etudes/moteur-kotlin/02-spike-mesures.md` | `lsp-kotlin/docs/etudes/moteur-kotlin/02-spike-mesures.md` | kotlin |  |
| `docs/etudes/moteur-kotlin/03-execution-etage0.md` | `lsp-kotlin/docs/etudes/moteur-kotlin/03-execution-etage0.md` | kotlin |  |
| `docs/etudes/moteur-kotlin/04-execution-etage1.md` | `lsp-kotlin/docs/etudes/moteur-kotlin/04-execution-etage1.md` | kotlin |  |
| `docs/etudes/moteur-kotlin/05-execution-etage2.md` | `lsp-kotlin/docs/etudes/moteur-kotlin/05-execution-etage2.md` | kotlin |  |
| `docs/etudes/moteur-kotlin/06-execution-etage3.md` | `lsp-kotlin/docs/etudes/moteur-kotlin/06-execution-etage3.md` | kotlin |  |
| `docs/etudes/moteur-kotlin/07-execution-etage4.md` | `lsp-kotlin/docs/etudes/moteur-kotlin/07-execution-etage4.md` | kotlin |  |
| `docs/etudes/moteur-kotlin/08-execution-etage5.md` | `lsp-kotlin/docs/etudes/moteur-kotlin/08-execution-etage5.md` | kotlin |  |
| `docs/etudes/moteur-kotlin/09-execution-etage6.md` | `lsp-kotlin/docs/etudes/moteur-kotlin/09-execution-etage6.md` | kotlin |  |
| `docs/etudes/moteur-kotlin/10-execution-transverse-T.md` | `lsp-kotlin/docs/etudes/moteur-kotlin/10-execution-transverse-T.md` | kotlin |  |
| `docs/etudes/moteur-kotlin/11-decisions-proprio-post-cloture.md` | `lsp-kotlin/docs/etudes/moteur-kotlin/11-decisions-proprio-post-cloture.md` | kotlin |  |
| `docs/etudes/moteur-kotlin/ALERTES.md` | `lsp-kotlin/docs/etudes/moteur-kotlin/ALERTES.md` | kotlin |  |
| `docs/etudes/moteur-kotlin/DEMANDES-INTERFACE.md` | `lsp-kotlin/docs/etudes/moteur-kotlin/DEMANDES-INTERFACE.md` | kotlin |  |
| `docs/etudes/niveau-android-studio-kotlin/00-synthese.md` | `lsp-kotlin/docs/etudes/niveau-android-studio-kotlin/00-synthese.md` | kotlin |  |
| `docs/etudes/niveau-android-studio-kotlin/01-audit-moteur.md` | `lsp-kotlin/docs/etudes/niveau-android-studio-kotlin/01-audit-moteur.md` | kotlin |  |
| `docs/etudes/niveau-android-studio-kotlin/02-reference-android-studio.md` | `lsp-kotlin/docs/etudes/niveau-android-studio-kotlin/02-reference-android-studio.md` | kotlin |  |
| `docs/etudes/niveau-android-studio-kotlin/03-matrice-ecarts.md` | `lsp-kotlin/docs/etudes/niveau-android-studio-kotlin/03-matrice-ecarts.md` | kotlin |  |
| `docs/etudes/niveau-android-studio-kotlin/04-plan-vagues.md` | `lsp-kotlin/docs/etudes/niveau-android-studio-kotlin/04-plan-vagues.md` | kotlin |  |
| `docs/etudes/niveau-android-studio-kotlin/05-fiches-comportement/KC-01-completion-smart-type-attendu.md` | `lsp-kotlin/docs/etudes/niveau-android-studio-kotlin/05-fiches-comportement/KC-01-completion-smart-type-attendu.md` | kotlin |  |
| `docs/etudes/niveau-android-studio-kotlin/05-fiches-comportement/KC-02-completion-chain-extensions.md` | `lsp-kotlin/docs/etudes/niveau-android-studio-kotlin/05-fiches-comportement/KC-02-completion-chain-extensions.md` | kotlin |  |
| `docs/etudes/niveau-android-studio-kotlin/05-fiches-comportement/KC-03-recepteurs-implicites.md` | `lsp-kotlin/docs/etudes/niveau-android-studio-kotlin/05-fiches-comportement/KC-03-recepteurs-implicites.md` | kotlin |  |
| `docs/etudes/niveau-android-studio-kotlin/05-fiches-comportement/KC-04-when-exhaustif.md` | `lsp-kotlin/docs/etudes/niveau-android-studio-kotlin/05-fiches-comportement/KC-04-when-exhaustif.md` | kotlin |  |
| `docs/etudes/niveau-android-studio-kotlin/05-fiches-comportement/KC-05-completion-override.md` | `lsp-kotlin/docs/etudes/niveau-android-studio-kotlin/05-fiches-comportement/KC-05-completion-override.md` | kotlin |  |
| `docs/etudes/niveau-android-studio-kotlin/05-fiches-comportement/KC-06-inlay-hints.md` | `lsp-kotlin/docs/etudes/niveau-android-studio-kotlin/05-fiches-comportement/KC-06-inlay-hints.md` | kotlin |  |
| `docs/etudes/niveau-android-studio-kotlin/05-fiches-comportement/KC-07-contexte-composable.md` | `lsp-kotlin/docs/etudes/niveau-android-studio-kotlin/05-fiches-comportement/KC-07-contexte-composable.md` | kotlin |  |
| `docs/etudes/niveau-android-studio-kotlin/05-fiches-comportement/KC-08-contexte-suspend.md` | `lsp-kotlin/docs/etudes/niveau-android-studio-kotlin/05-fiches-comportement/KC-08-contexte-suspend.md` | kotlin |  |
| `docs/etudes/niveau-android-studio-kotlin/05-fiches-comportement/KC-09-nullabilite-smart-casts.md` | `lsp-kotlin/docs/etudes/niveau-android-studio-kotlin/05-fiches-comportement/KC-09-nullabilite-smart-casts.md` | kotlin |  |
| `docs/etudes/niveau-android-studio-kotlin/05-fiches-comportement/KC-10-optimiseur-imports.md` | `lsp-kotlin/docs/etudes/niveau-android-studio-kotlin/05-fiches-comportement/KC-10-optimiseur-imports.md` | kotlin |  |
| `docs/etudes/niveau-android-studio-kotlin/05-fiches-comportement/KC-11-intentions-quickfixes.md` | `lsp-kotlin/docs/etudes/niveau-android-studio-kotlin/05-fiches-comportement/KC-11-intentions-quickfixes.md` | kotlin |  |
| `docs/etudes/niveau-android-studio-kotlin/05-fiches-comportement/KC-12-gradle-kts-catalogue.md` | `lsp-kotlin/docs/etudes/niveau-android-studio-kotlin/05-fiches-comportement/KC-12-gradle-kts-catalogue.md` | kotlin |  |
| `docs/etudes/niveau-android-studio-kotlin/05-fiches-comportement/README.md` | `lsp-kotlin/docs/etudes/niveau-android-studio-kotlin/05-fiches-comportement/README.md` | kotlin |  |
| `docs/etudes/niveau-android-studio-kotlin/06-protocole-captures-android-studio.md` | `lsp-kotlin/docs/etudes/niveau-android-studio-kotlin/06-protocole-captures-android-studio.md` | kotlin |  |
| `docs/etudes/niveau-android-studio-kotlin/07-gabarit-mesure-appareil.md` | `lsp-kotlin/docs/etudes/niveau-android-studio-kotlin/07-gabarit-mesure-appareil.md` | kotlin |  |
| `docs/etudes/niveau-android-studio-kotlin/ALERTES.md` | `lsp-kotlin/docs/etudes/niveau-android-studio-kotlin/ALERTES.md` | kotlin |  |
| `docs/etudes/niveau-android-studio-kotlin/captures/RESULTATS-LOT-E.md` | `lsp-kotlin/docs/etudes/niveau-android-studio-kotlin/captures/RESULTATS-LOT-E.md` | kotlin |  |
| `docs/etudes/niveau-android-studio-kotlin/captures/kit/KitCapturesKotlin.kt.txt` | `lsp-kotlin/docs/etudes/niveau-android-studio-kotlin/captures/kit/KitCapturesKotlin.kt.txt` | kotlin |  |
| `docs/etudes/niveau-android-studio/00-synthese.md` | `lsp-java/docs/etudes/niveau-android-studio/00-synthese.md` | java |  |
| `docs/etudes/niveau-android-studio/01-audit-moteur.md` | `lsp-java/docs/etudes/niveau-android-studio/01-audit-moteur.md` | java |  |
| `docs/etudes/niveau-android-studio/02-reference-android-studio.md` | `lsp-java/docs/etudes/niveau-android-studio/02-reference-android-studio.md` | java |  |
| `docs/etudes/niveau-android-studio/03-matrice-ecarts.md` | `lsp-java/docs/etudes/niveau-android-studio/03-matrice-ecarts.md` | java |  |
| `docs/etudes/niveau-android-studio/04-plan-vagues.md` | `lsp-java/docs/etudes/niveau-android-studio/04-plan-vagues.md` | java |  |
| `docs/etudes/niveau-android-studio/05-fiches-comportement/C-01-classes-light-R.md` | `lsp-java/docs/etudes/niveau-android-studio/05-fiches-comportement/C-01-classes-light-R.md` | java |  |
| `docs/etudes/niveau-android-studio/05-fiches-comportement/C-02-viewbinding-light.md` | `lsp-java/docs/etudes/niveau-android-studio/05-fiches-comportement/C-02-viewbinding-light.md` | java |  |
| `docs/etudes/niveau-android-studio/05-fiches-comportement/C-03-completion-smart.md` | `lsp-java/docs/etudes/niveau-android-studio/05-fiches-comportement/C-03-completion-smart.md` | java |  |
| `docs/etudes/niveau-android-studio/05-fiches-comportement/C-04-completion-typedef.md` | `lsp-java/docs/etudes/niveau-android-studio/05-fiches-comportement/C-04-completion-typedef.md` | java |  |
| `docs/etudes/niveau-android-studio/05-fiches-comportement/C-05-newapi.md` | `lsp-java/docs/etudes/niveau-android-studio/05-fiches-comportement/C-05-newapi.md` | java |  |
| `docs/etudes/niveau-android-studio/05-fiches-comportement/C-06-navigation-ressource.md` | `lsp-java/docs/etudes/niveau-android-studio/05-fiches-comportement/C-06-navigation-ressource.md` | java |  |
| `docs/etudes/niveau-android-studio/05-fiches-comportement/C-07-suppressions-inspections.md` | `lsp-java/docs/etudes/niveau-android-studio/05-fiches-comportement/C-07-suppressions-inspections.md` | java |  |
| `docs/etudes/niveau-android-studio/05-fiches-comportement/C-08-second-appel.md` | `lsp-java/docs/etudes/niveau-android-studio/05-fiches-comportement/C-08-second-appel.md` | java |  |
| `docs/etudes/niveau-android-studio/05-fiches-comportement/C-09-nullite-intra-methode.md` | `lsp-java/docs/etudes/niveau-android-studio/05-fiches-comportement/C-09-nullite-intra-methode.md` | java |  |
| `docs/etudes/niveau-android-studio/05-fiches-comportement/C-10-references-multifichiers.md` | `lsp-java/docs/etudes/niveau-android-studio/05-fiches-comportement/C-10-references-multifichiers.md` | java |  |
| `docs/etudes/niveau-android-studio/05-fiches-comportement/README.md` | `lsp-java/docs/etudes/niveau-android-studio/05-fiches-comportement/README.md` | java |  |
| `docs/etudes/niveau-android-studio/06-protocole-captures-android-studio.md` | `lsp-java/docs/etudes/niveau-android-studio/06-protocole-captures-android-studio.md` | java |  |
| `docs/etudes/niveau-android-studio/07-gabarit-mesure-appareil.md` | `lsp-java/docs/etudes/niveau-android-studio/07-gabarit-mesure-appareil.md` | java |  |
| `docs/etudes/niveau-android-studio/08-rapport-divergences-ecj-javac.md` | `lsp-java/docs/etudes/niveau-android-studio/08-rapport-divergences-ecj-javac.md` | java |  |
| `docs/etudes/niveau-android-studio/ALERTES.md` | `lsp-java/docs/etudes/niveau-android-studio/ALERTES.md` | java |  |
| `docs/etudes/niveau-android-studio/captures/README.md` | `lsp-java/docs/etudes/niveau-android-studio/captures/README.md` | java |  |
| `docs/etudes/niveau-android-studio/captures/RESULTATS-LOT-A.md` | `lsp-java/docs/etudes/niveau-android-studio/captures/RESULTATS-LOT-A.md` | java |  |
| `docs/etudes/niveau-android-studio/captures/RESULTATS-LOT-B.md` | `lsp-java/docs/etudes/niveau-android-studio/captures/RESULTATS-LOT-B.md` | java |  |
| `docs/etudes/niveau-android-studio/captures/RESULTATS-LOT-C.md` | `lsp-java/docs/etudes/niveau-android-studio/captures/RESULTATS-LOT-C.md` | java |  |
| `docs/etudes/niveau-android-studio/captures/RESULTATS-LOT-D.md` | `lsp-java/docs/etudes/niveau-android-studio/captures/RESULTATS-LOT-D.md` | java |  |
| `docs/etudes/niveau-android-studio/captures/kit/KitCapturesAs.java` | `lsp-java/docs/etudes/niveau-android-studio/captures/kit/KitCapturesAs.java` | java |  |
| `docs/etudes/niveau-android-studio/captures/kit/res-values-strings.xml` | `lsp-java/docs/etudes/niveau-android-studio/captures/kit/res-values-strings.xml` | java |  |
| `docs/etudes/niveau-android-studio/mesures/2026-10-30-passe1-moto-g06-jrt.txt` | `lsp-java/docs/etudes/niveau-android-studio/mesures/2026-10-30-passe1-moto-g06-jrt.txt` | java |  |
| `docs/etudes/niveau-android-studio/mesures/2026-10-31-vc3-completion.txt` | `lsp-java/docs/etudes/niveau-android-studio/mesures/2026-10-31-vc3-completion.txt` | java |  |
| `docs/etudes/niveau-android-studio/mesures/2026-10-31-vd-vc2-catalogue.txt` | `lsp-java/docs/etudes/niveau-android-studio/mesures/2026-10-31-vd-vc2-catalogue.txt` | java |  |
| `docs/etudes/niveau-android-studio/mesures/2026-10-31-vf1-double-tampon.txt` | `lsp-java/docs/etudes/niveau-android-studio/mesures/2026-10-31-vf1-double-tampon.txt` | java |  |
| `docs/etudes/niveau-android-studio/mesures/2026-10-31-vf2-vc1-vc2.txt` | `lsp-java/docs/etudes/niveau-android-studio/mesures/2026-10-31-vf2-vc1-vc2.txt` | java |  |
| `docs/etudes/niveau-android-studio/mesures/2026-11-01-complement-catalogue.txt` | `lsp-java/docs/etudes/niveau-android-studio/mesures/2026-11-01-complement-catalogue.txt` | java |  |
| `docs/etudes/niveau-android-studio/mesures/2026-11-01-va2-corpus-reel.txt` | `lsp-java/docs/etudes/niveau-android-studio/mesures/2026-11-01-va2-corpus-reel.txt` | java |  |
| `docs/etudes/niveau-android-studio/mesures/2026-11-01-vd2-refactorings.txt` | `lsp-java/docs/etudes/niveau-android-studio/mesures/2026-11-01-vd2-refactorings.txt` | java |  |
| `docs/etudes/niveau-android-studio/mesures/2026-11-01-vd3-refactorings.txt` | `lsp-java/docs/etudes/niveau-android-studio/mesures/2026-11-01-vd3-refactorings.txt` | java |  |
| `docs/etudes/niveau-android-studio/mesures/2026-11-01-vf-finale-vg-protocole.txt` | `lsp-java/docs/etudes/niveau-android-studio/mesures/2026-11-01-vf-finale-vg-protocole.txt` | java |  |
| `docs/etudes/niveau-android-studio/mesures/2026-11-02-va2-complement-repli-lexical.txt` | `lsp-java/docs/etudes/niveau-android-studio/mesures/2026-11-02-va2-complement-repli-lexical.txt` | java |  |
| `docs/etudes/niveau-android-studio/mesures/2026-11-02-vh-repartition.txt` | `lsp-java/docs/etudes/niveau-android-studio/mesures/2026-11-02-vh-repartition.txt` | java |  |

## 3. Détail — bancs (`bancs/`)

| Ancien | Nouveau | Classe | Décision |
|---|---|---|---|
| `bancs/ANALYSE-DELTAS-BASCULE.md` | `lsp-java/bancs/ANALYSE-DELTAS-BASCULE.md` | java | D-3 |
| `bancs/GABARIT-BASCULE-APPAREIL.md` | `docs/GABARIT-BASCULE-APPAREIL.md` | transverse | D-2 |
| `bancs/baselines/completion-kotlin.json` | `lsp-kotlin/bancs/baselines/completion-kotlin.json` | kotlin |  |
| `bancs/baselines/completion.json` | `lsp-java/bancs/baselines/completion.json` | java |  |
| `bancs/baselines/parite-completion.json` | `lsp-java/bancs/baselines/parite-completion.json` | java |  |
| `bancs/corpus-android-kotlin/LISEZMOI.md` | `lsp-kotlin/bancs/corpus-android-kotlin/LISEZMOI.md` | kotlin |  |
| `bancs/corpus-android/LISEZMOI.md` | `lsp-java/bancs/corpus-android/LISEZMOI.md` | java |  |
| `bancs/projet-android-genere/com/example/monip/R.java` | `lsp-java/bancs/projet-android-genere/com/example/monip/R.java` | java |  |
| `bancs/projet-android-genere/com/example/monip/databinding/ActivityMainBinding.java` | `lsp-java/bancs/projet-android-genere/com/example/monip/databinding/ActivityMainBinding.java` | java |  |
| `bancs/projet-android/README.md` | `lsp-java/bancs/projet-android/README.md` | java |  |
| `bancs/projet-android/app/build.gradle.kts` | `lsp-java/bancs/projet-android/app/build.gradle.kts` | java |  |
| `bancs/projet-android/app/src/main/AndroidManifest.xml` | `lsp-java/bancs/projet-android/app/src/main/AndroidManifest.xml` | java |  |
| `bancs/projet-android/app/src/main/java/com/example/monip/Greeter.java` | `lsp-java/bancs/projet-android/app/src/main/java/com/example/monip/Greeter.java` | java |  |
| `bancs/projet-android/app/src/main/java/com/example/monip/MainActivity.java` | `lsp-java/bancs/projet-android/app/src/main/java/com/example/monip/MainActivity.java` | java |  |
| `bancs/projet-android/app/src/main/res/layout/activity_main.xml` | `lsp-java/bancs/projet-android/app/src/main/res/layout/activity_main.xml` | java |  |
| `bancs/projet-android/build.gradle.kts` | `lsp-java/bancs/projet-android/build.gradle.kts` | java |  |
| `bancs/projet-android/gradle/libs.versions.toml` | `lsp-java/bancs/projet-android/gradle/libs.versions.toml` | java |  |
| `bancs/projet-android/settings.gradle.kts` | `lsp-java/bancs/projet-android/settings.gradle.kts` | java |  |
| `bancs/resultats-bascule.txt` | `lsp-java/bancs/resultats-bascule.txt` | java | D-3 |

## 4. Détail — scripts et fichiers racine

| Ancien | Nouveau | Classe | Décision |
|---|---|---|---|
| `.gitignore` | `.gitignore` | transverse |  |
| `AGENTS.md` | `AGENTS.md` | transverse |  |
| `LICENSE` | `LICENSE` | transverse |  |
| `NOTICE` | `NOTICE` | transverse |  |
| `README.md` | `README.md` | transverse |  |
| `USAGE.md` | `lsp-java/USAGE.md` | java | D-6 |
| `build.gradle.kts` | `build.gradle.kts` | transverse |  |
| `gradle.properties` | `gradle.properties` | transverse |  |
| `gradlew` | `gradlew` | transverse |  |
| `gradlew.bat` | `gradlew.bat` | transverse |  |
| `scripts/ask-export.init.gradle` | `lsp-java/scripts/ask-export.init.gradle` | java | D-5 |
| `scripts/generer-migration-chemins.py` | `scripts/generer-migration-chemins.py` | transverse |  |
| `scripts/kit-bascule.sh` | `lsp-java/scripts/kit-bascule.sh` | java | D-5 |
| `scripts/kit-mesure.sh` | `lsp-java/scripts/kit-mesure.sh` | java | D-5 |
| `scripts/manifeste-corpus.init.gradle` | `lsp-java/scripts/manifeste-corpus.init.gradle` | java | D-5 |
| `scripts/preparer-base-api.sh` | `lsp-java/scripts/preparer-base-api.sh` | java | D-5 |
| `scripts/preparer-manifeste-ask.sh` | `lsp-java/scripts/preparer-manifeste-ask.sh` | java | D-5 |
| `scripts/preparer-manifestes-corpus.sh` | `lsp-java/scripts/preparer-manifestes-corpus.sh` | java | D-5 |
| `scripts/preparer-oracle.sh` | `lsp-java/scripts/preparer-oracle.sh` | java | D-5 |
| `scripts/preparer-projet-android.sh` | `lsp-java/scripts/preparer-projet-android.sh` | java | D-5 |
| `scripts/telecharger-corpus-android.sh` | `lsp-java/scripts/telecharger-corpus-android.sh` | java | D-5 |
| `scripts/telecharger-corpus-kotlin.sh` | `lsp-kotlin/scripts/telecharger-corpus-kotlin.sh` | kotlin |  |
| `scripts/vendor-grammaire-kotlin.sh` | `lsp-kotlin/scripts/vendor-grammaire-kotlin.sh` | kotlin |  |
| `scripts/verifier-provenance.sh` | `scripts/verifier-provenance.sh` | transverse |  |
| `scripts/verifier-push.sh` | `scripts/verifier-push.sh` | transverse |  |
| `settings.gradle.kts` | `settings.gradle.kts` | transverse |  |

