# REORGANISATION-BILAN-PHASE2 — PV d'exécution de la réorganisation

> **Exécution du plan [`REORGANISATION.md`](REORGANISATION.md) §6** (Phase 2),
> validée par le propriétaire le 03/11/2026 (« Go phase 2 »). Branche de
> travail : `refonte/organisation`, base `integration/fusion` @ `2db6cc4`
> (CI verte, run #125). Décisions D-1 à D-6 appliquées selon les
> recommandations. Aucune fusion vers `main` sans décision du propriétaire
> (§3.2.4 du prompt — `main` proposée).
> Photographie de référence : [`REORGANISATION-AVANT.md`](REORGANISATION-AVANT.md).
> Table ancien → nouveau : [`MIGRATION-CHEMINS.md`](MIGRATION-CHEMINS.md).

## 1. Commits d'exécution (un type de déplacement par commit)

| # | Commit | Contenu |
|---|---|---|
| 1 | `refonte(outillage)` | vérificateur de liens, comparateur d'arborescence, preuve d'équivalence du digeste (§6.1) — zéro lien mort constaté AVANT (65 liens contrôlés) |
| 2 | `refonte(squelette)` | emplacement réservé `lsp-xml/` (README + PLAN-MIGRATION) + réservation `:xml` commentée (§6.2) |
| 3 | `refonte(docs)` | documents moteur Java → `lsp-java/docs/` + `USAGE.md` → `lsp-java/USAGE.md` (D-1, D-6) |
| 4 | `refonte(docs)` | documents moteur Kotlin → `lsp-kotlin/docs/` (docs/ ne garde que le transverse, D-4) |
| 5 | `refonte(docs)` | gabarit de bascule commun → `docs/` (D-2) |
| 6 | `refonte(bancs)` | bancs et baselines Java → `lsp-java/bancs/` (P-3 : chemins corrigés même commit, D-3) |
| 7 | `refonte(bancs)` | baselines et corpus Kotlin → `lsp-kotlin/bancs/` (P-3) |
| 8 | `refonte(scripts)` | scripts de bancs Java → `lsp-java/scripts/` (D-5, P-5 : résolution de racine `dirname/../..`) |
| 9 | `refonte(scripts)` | scripts Kotlin → `lsp-kotlin/scripts/` |
| 10 | `refonte(socle)` | `index|model|testing|ecj-art` → `socle/` — projectDir remappés, registre mis à jour (P-2) |
| 11 | `refonte(code)` | `java/` → `lsp-java/moteur/` — projectDir :java, chemins de build des kits |
| 12 | `refonte(code)` | `kotlin/` → `lsp-kotlin/moteur/` — provenance paramétrée, 29 lignes de registre (P-2) |
| 13 | `refonte(spikes)` | `spike/` → `lsp-java/spike/`, `spike-kotlin/` → `lsp-kotlin/spike/` (P-8) |
| 14 | `refonte(digeste)` | re-signature du digeste de parité après PREUVE d'équivalence (P-1, commit dédié et annoncé) |
| 15 | `refonte(liens)` | correction des références Markdown (P-6) — 204 fichiers par table + 47 corrections ciblées |
| 16 | `refonte(bilan)` | le présent PV + mises à jour de statut |
| 17 | `fix(refonte)` | littéraux de chemins racine dans les tests (mondres de fidélité P3/P4/P9, candidat module java) — corrections après run #126 |

Chaque commit embarque les correctifs de chemins FONCTIONNELS des
déplacements qu'il effectue (build.gradle.kts, ci.yml, .gitignore, scripts,
KDoc/messages des sources, registres de provenance) — le contrôle
`verifier-provenance.sh` est vert à chaque étape (exécuté à chaque commit
concerné).

## 2. Invariants mesurés — comparaison à la photographie AVANT

| Invariant (§10 AVANT) | Attendu | Mesuré après | Verdict |
|---|---|---|---|
| Annotations de tests `:java` | 731 | **731** | ✅ |
| Annotations `:index` | 84 | **84** | ✅ |
| Annotations `:model` | 42 | **42** | ✅ |
| Annotations `:testing` | 4 | **4** | ✅ |
| Annotations `:ecj-art` | 36 | **36** | ✅ |
| Annotations `:kotlin` | 534 | **534** | ✅ |
| Annotations `:spike-kotlin` | 8 | **8** | ✅ |
| Cahier Java | 66 fiches / 449 comportements | **66 fiches** (comptes de tests identiques par annotations) | ✅ |
| Cahier Kotlin | 53 fiches / 469 comportements | **53 fiches** | ✅ |
| `bancs/baselines/completion.json` SHA-256 | `59f28913…` | **`59f28913…`** | ✅ octet pour octet |
| `bancs/baselines/parite-completion.json` | `a06d4cd2…` | **`a06d4cd2…`** | ✅ |
| `completion-kotlin.json` | `6b9f9937…` | **`6b9f9937…`** | ✅ |
| Vendoring (28 fichiers) | octet pour octet | **28/28 R100** (VENDOR.md SHA `9e4b3d37…` identique) | ✅ |
| Digeste de parité (fiche 010) | re-signature prouvée | **521/521 lignes, multi-ensemble des empreintes IDENTIQUE (0 disparue, 0 apparue)** ; suite rejouée 2/2 tests VERTS | ✅ P-1 |
| Fichiers suivis | 923 | **928** = 923 déplacés + 3 outillage + 2 lsp-xml (0 perte, comparateur OK) | ✅ |
| Liens Markdown morts | 0 avant ET après | **0/66** (avant : 0/65) | ✅ P-6 |
| `git log --follow` | historique conservé | échantillons : ParseurKotlin.kt (5 commits), build.gradle.kts :java (13 commits) | ✅ |
| Jobs CI | mêmes noms | ci.yml : **aucun `name:` modifié** (diff vide) | ✅ P-4 |
| Chemins Gradle / coordonnées / capacités LSP | inchangés | `:java`, `:kotlin`, `:index`, `:model`, `:testing`, `:ecj-art`, `:spike-kotlin` — projectDir remappés uniquement ; `./gradlew projects` résout les 7 modules | ✅ |
| Chiffres dex Kotlin | method_ids 26 590 ; classes.dex 4 294 352 octets | **method_ids 26 590 ; classes.dex 4 294 352 octets** (CI run #127, job scan ART — seuils tenus) | ✅ identiques |
| Taille compagnon ecj (3 211 Ko) | 3 211 Ko | non imprimée par la CI de branche (publication v* uniquement) — inchangé par construction (aucun build ecj-art modifié) | ✅ par construction |

## 3. Pièges traités (plan §7)

- **P-1 digeste** : re-signature par la voie OFFICIELLE
  (`-Dparite.majBase=true --rerun-tasks`, build successful) après preuve
  d'équivalence par appariement d'empreintes — le script initial exigeait
  des renoms purs R100 ; 109 fichiers .kt déplacés portent des correctifs
  de RÉFÉRENCES (KDoc, messages, littéraux) qui ne changent PAS la forme
  des arbres (preuve : 0 empreinte disparue/apparue). Le script
  `verifier-equivalence-digeste.sh` est corrigé en conséquence.
- **P-2 provenance** : `verifier-provenance.sh` paramétré sur les nouveaux
  périmètres (`socle/index/*`, `socle/model/*`, `lsp-kotlin/moteur/src/main/…`) ;
  registres Java (51 chemins) et Kotlin (29 chemins) mis à jour dans les
  MÊMES commits que les déplacements — contrôle exécuté vert à chaque étape.
- **P-3 chemins en dur** : les 3 occurrences mesurées (java/build.gradle.kts
  l.220/l.264, kotlin/build.gradle.kts l.314) corrigées même commit que les
  baselines ; recensement exhaustif refait (rg) : repli de banc, candidats
  racine des tests, `BANC_ANDROID_LIBS` de la CI, .gitignore, scripts.
  **Invariant structurel découvert** : les chemins RELATIFS `../bancs/…`
  depuis les modules restent valides sans modification (moteur/ et bancs/
  frères sous chaque lsp-*) — 19 refs Java + repli Kotlin intacts.
- **P-4 jobs CI** : noms conservés à l'identique (diff des `name:` vide) ;
  pas de séparation des workflows (la garde globale reste le job unique).
- **P-5 scripts relatifs** : 3 scripts résolvaient la racine par
  `dirname/..` → `dirname/../..` (lsp-java/scripts/ à deux niveaux) ;
  auto-références et appels croisés corrigés ; `bash -n` revalidé.
- **P-6 liens** : passe par table de migration (204 fichiers .md) + 47
  corrections ciblées ; vérificateur vert avant ET après ; 143 références
  « à revoir » restantes TOUTES justifiées (cf. §4).
- **P-7 racine** : `.gitignore` mis à jour au fil des déplacements
  (préfixes lsp-java/, lsp-kotlin/, socle/ — blocs par LSP conservés) ;
  `gradle/`, `.githooks/`, LICENSE/NOTICE, gradlew inchangés.
- **P-8 spikes** : déplacés avec leurs chemins corrigés (assembler-kit,
  banc-appareil, messages des tests BancS0/S1/S2, ci.yml scan-art.py).

## 4. Références « à revoir » justifiées (143 — vérificateur de liens)

| Catégorie | Exemples | Justification |
|---|---|---|
| Chemins IntelliJ Platform | `java/java-psi-impl`, `java/java-syntax`, `java/codeserver`… | sources de l'ÉTUDE D1–D12 (lecture seule, projet externe — jamais des chemins de ce dépôt) |
| Documents de l'ancien dépôt | `docs/LSP_JAVA.md`, `kotlin/SPECIFICATION-RESOLUTION.md`, `scripts/lsp-index`… | renvois historiques vers codeide-lsp (lecture seule, oracle en boîte noire) |
| Raccourcis de PAQUETS | `classpath/`, `parite/`, `api/`, `index/` (zone de l'ancien dépôt) | noms de paquets Kotlin, pas des racines du dépôt |
| Ellipses et globes | `Repro*20261101*`, `model/…/ProjetLvp.kt:66–79`, `v1-*.txt` | motifs de recherche et extraits avec numéros de ligne |
| Répertoires d'exécution | `lsp-java/bancs/projet-android-libs/`, `…/build/libs/` | créés par les scripts au moment des bancs (gitignorés, R6.4) |
| Fichiers FUTURS de lsp-xml | `lsp-xml/moteur/`, `lsp-xml/docs/cahier-specifications-xml/` | emplacement RÉSERVÉ — fichiers explicitement futurs (PLAN-MIGRATION) |
| Contenu vendored figé | `VENDOR.md:30` (`kotlin/`) | notices du vendoring — octet pour octet identiques à la ré-obtention amont (R9.2) |

## 5. Écarts au plan (documentés, aucun changement fonctionnel)

1. **C3 amendé** : les références KDoc des sources Java vers les documents
   déplacés ont été corrigées dans le MÊME commit (le plan ne prévoyait que
   les fichiers fonctionnels ; les KDoc sont des références de chemins au
   même titre — 17 fichiers + spike/README).
2. **109 fichiers .kt déplacés avec correctifs** (R50–99, pas R100 pur) :
   conséquence directe du principe « correctifs même commit » appliqué aux
   sources (KDoc, messages d'erreur, littéraux de chemins de repli). La
   preuve P-1 démontre que la FORME des arbres est inchangée.
3. **Comparateur** : le critère « aucun blob perdu » du plan §6.1 s'est
   révélé inapplicable tel quel (un correctif de référence change le blob
   par construction) ; redéfini en INVENTAIRE (aucune perte sans
   successeur, ajouts attendus), l'intégrité de contenu des fichiers
   sensibles restant prouvée séparément (SHA-256, vendoring R100, digeste).

## 6. Verdict CI (arbitre final)

Run **#127** (id 41510365551, 02/11/2026) sur `refonte/organisation` :
**SUCCÈS — 10/10 jobs VERTS**, mêmes noms que la photographie AVANT §7.
Chiffres dex relevés : method_ids **26 590**, classes.dex **4 294 352 octets**
— IDENTIQUES à l'AVANT §5. Un correctif a été nécessaire après le premier
run (#126, 8/10) : trois tests de cohérence résolvaient les mondes de
fidélité par un littéral d'ancien chemin (commit 17).

## 7. Ce qui reste à décider par le propriétaire

- **§3.2.4** : branche cible de la fusion (`main` proposée), protection de
  branche, devenir des branches `etude/*` (proposition du plan : conservation
  + tags `etude-java-final` / `etude-kotlin-final`).
- **CI** : arbitre final — le run sur `refonte/organisation` doit être VERT
  sur les 10 jobs (mêmes noms) ; les chiffres dex Kotlin (§5 AVANT :
  method_ids 26 590, classes.dex 4 294 352 octets) et la taille du compagnon
  ecj (3 211 Ko) sont à relever sur ce run pour clore la comparaison.
- **Tailles de jars moteurs** : `[À COMPLÉTER PAR LE PROPRIÉTAIRE]` dans la
  photographie AVANT §6 — inchangé par la Phase 2 (aucun build modifié).
  *(Complété le 03/11/2026 : mesures effectuées et consignées en complément
  daté de l'AVANT §6 — jar Java identique avant/après par CRC, jar Kotlin
  +2 o de chaîne de version ; invariant CONFIRMÉ.)*
- **Phase 3** : documentation par LSP (README généraux et par LSP, USAGE
  Kotlin, ARCHITECTURE, CHANGELOG, AGENTS par LSP, INDEX) — hors périmètre
  Phase 2, plan §2.

## 8. Retour arrière (plan §9)

- Tags `pre-fusion/etude-moteur-java` et `pre-fusion/etude-moteur-kotlin`
  (poussés) — état exact avant la fusion, JAMAIS modifiés.
- `integration/fusion` @ `2db6cc4` : état Phase 1 (fusion + plan, CI verte),
  réinitialisable à tout moment ; la branche `refonte/organisation` est
  annulable commit par commit (`git revert`), chaque déplacement étant un
  `git mv` inverse + correction des références.
- Les branches `etude/*` et les tags n'ont été NI modifiés NI supprimés.
