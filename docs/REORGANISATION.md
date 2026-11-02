# REORGANISATION — plan de réorganisation du dépôt code-lsp (Phase 1)

> **Statut : VALIDÉ puis EXÉCUTÉ le 03/11/2026 — branche `refonte/organisation`**
> (PV d'exécution : [`REORGANISATION-BILAN-PHASE2.md`](REORGANISATION-BILAN-PHASE2.md) ;
> ce plan décrit l'avant et reste la référence des décisions).
> Base du plan : branche `integration/fusion` (fusion sans perte de
> `etude/moteur-java` et `etude/moteur-kotlin`, CI verte). Photographie des
> invariants : [`REORGANISATION-AVANT.md`](REORGANISATION-AVANT.md). Table
> exhaustive ancien → nouveau (920 fichiers) :
> [`MIGRATION-CHEMINS.md`](MIGRATION-CHEMINS.md), générée par
> `scripts/generer-migration-chemins.py` et relue.
> Mission : §4.3 du prompt du 02/11/2026 — le plan est commité, l'agent
> **s'arrête** ; **aucun déplacement avant l'accord du propriétaire**.

## 1. Objectif et principes non négociables

Un dépôt où **chaque LSP vit dans sa propre section** (Java, Kotlin,
emplacement réservé XML) avec README général + par LSP :
`README.md` · `USAGE.md` · `ARCHITECTURE.md` · `CHANGELOG.md` · `AGENTS.md` ·
index de documentation.

Sans casser : le build, la CI, les baselines signées, l'historique git, ni
les contrats consommés par l'hôte CodeIDE. **Zéro changement fonctionnel** :
aucun renommage de paquet (`jo.codeide.lsp.java`, `jo.codeide.lsp.kotlin`),
aucune capacité LSP (`x-codeide.*`), aucun format de sérialisation, aucune
coordonnée publiée (GitHub Packages). Un changement de comportement découvert
en route est **signalé, pas corrigé**.

Déplacements par `git mv` (similarité 100 %, `git log --follow` préservé),
**un type de déplacement par commit**, corrections de références dans des
commits **séparés**.

## 2. Arborescence cible

```
code-lsp/
├─ README.md                  # GÉNÉRAL (réécrit Phase 3 : vision, carte des LSP, démarrage)
├─ AGENTS.md                  # règles communes + travail multi-agents (§7.4, Phase 3)
├─ LICENSE · NOTICE
├─ settings.gradle.kts · build.gradle.kts · gradle.properties · gradle/ · gradlew*
├─ .github/ · .githooks/ · .gitignore
├─ scripts/                   # COMMUNS : verifier-push.sh, verifier-provenance.sh,
│                             #   outillage de contrôle Phase 2 (liens, arborescence, digestes)
├─ docs/                      # TRANSVERSE uniquement
│   ├─ INDEX.md · GOUVERNANCE.md · REORGANISATION*.md · MIGRATION-CHEMINS.md
│   ├─ GABARIT-BASCULE-APPAREIL.md        # (D-2 — gabarit commun)
│   └─ etudes/editeur-lsp/                # (D-4 — étude valable pour tous les LSP)
├─ socle/                     # code partagé : index/ · model/ · testing/ · ecj-art/
├─ lsp-java/
│   ├─ README.md · USAGE.md (D-6) · ARCHITECTURE.md · CHANGELOG.md · AGENTS.md
│   ├─ moteur/                # ex java/ (module Gradle :java)
│   ├─ docs/                  # adr/ (0001–0032) · cahier-specifications/ · etudes/
│   │                         # (moteur-java-psi, niveau-android-studio) ·
│   │                         # PROVENANCE-REPRISE.md · documents de pilotage (D-1) ·
│   │                         # INDEX.md (Phase 3)
│   ├─ bancs/                 # projet-android/ · projet-android-genere/ · corpus-android/
│   │                         # baselines/ (completion.json, parite-completion.json) ·
│   │                         # ANALYSE-DELTAS-BASCULE.md · resultats-bascule.txt (D-3)
│   ├─ scripts/               # (D-5) kit-bascule, kit-mesure, preparer-oracle,
│   │                         # preparer-base-api, preparer-manifeste-ask,
│   │                         # preparer-manifestes-corpus, telecharger-corpus-android,
│   │                         # preparer-projet-android, ask-export.init.gradle,
│   │                         # manifeste-corpus.init.gradle
│   └─ spike/                 # ex spike/
├─ lsp-kotlin/
│   ├─ README.md · USAGE.md · ARCHITECTURE.md · CHANGELOG.md · AGENTS.md (Phase 3)
│   ├─ moteur/                # ex kotlin/ (module Gradle :kotlin, grammaire vendorée intacte)
│   ├─ docs/                  # adr/ (0100–0136) · cahier-specifications-kotlin/ ·
│   │                         # etudes/ (moteur-kotlin, niveau-android-studio-kotlin) ·
│   │                         # PROVENANCE-REPRISE-KOTLIN.md · INDEX.md (Phase 3)
│   ├─ bancs/                 # baselines/completion-kotlin.json · corpus-android-kotlin/
│   ├─ scripts/               # telecharger-corpus-kotlin.sh · vendor-grammaire-kotlin.sh
│   └─ spike/                 # ex spike-kotlin/
└─ lsp-xml/                   # EMPLACEMENT RÉSERVÉ — README.md + docs/PLAN-MIGRATION.md
                              # (aucun code, aucun module déclaré ; ligne commentée
                              #  dans settings.gradle.kts)
```

## 3. Principe clé — les chemins de projets Gradle ne changent pas

Dans `settings.gradle.kts`, les modules restent `:java`, `:kotlin`, `:index`,
`:model`, `:testing`, `:ecj-art` et sont remappés par `projectDir` :

```kotlin
include(":java");     project(":java").projectDir     = file("lsp-java/moteur")
include(":kotlin");   project(":kotlin").projectDir   = file("lsp-kotlin/moteur")
include(":index");    project(":index").projectDir    = file("socle/index")
include(":model");    project(":model").projectDir    = file("socle/model")
include(":testing");  project(":testing").projectDir  = file("socle/testing")
include(":ecj-art");  project(":ecj-art").projectDir  = file("socle/ecj-art")
// Réservation LSP XML (aucun code — cf. lsp-xml/docs/PLAN-MIGRATION.md) :
// include(":xml");   project(":xml").projectDir       = file("lsp-xml/moteur")
```

Les coordonnées publiées, dépendances inter-modules, tâches
(`benchQualite`, `genererDescripteurPack`, …) et artefacts restent
**identiques** pour l'hôte. Les 3 chemins de baselines codés en dur
(`java/build.gradle.kts` l.220 et l.264, `kotlin/build.gradle.kts` l.314 —
`rootProject.file("bancs/baselines/…")`) sont mis à jour **dans le même
commit** que le déplacement des baselines (piège P-3).

## 4. Classification (résumé)

Chaque fichier suivi est classé **une seule** fois (détail exhaustif par
fichier dans `MIGRATION-CHEMINS.md`, généré depuis `git ls-files` de
`integration/fusion`) :

| Classe | Fichiers | Contenu |
|---|---:|---|
| `java` | 463 | `java/` (257) · `spike/` (24) · ADR 0001–0032 · cahier-specifications · études moteur-java-psi + niveau-android-studio · PROVENANCE-REPRISE.md · bancs Java (17) · scripts Java (D-5, 10) · documents de pilotage (D-1, 7) · USAGE.md (D-6) |
| `kotlin` | 374 | `kotlin/` (191) · `spike-kotlin/` (48) · ADR 0100–0136 · cahier-specifications-kotlin · études moteur-kotlin + niveau-android-studio-kotlin · PROVENANCE-REPRISE-KOTLIN.md · completion-kotlin.json · corpus-android-kotlin · scripts Kotlin (2) |
| `socle` | 63 | `index/` (34) · `model/` (13) · `testing/` (4) · `ecj-art/` (12) |
| `transverse` | 23 | racine (10 fichiers + `gradle/` 2 + `.github/` 2 + `.githooks/` 1) · scripts communs (3, dont le générateur de la table) · docs transverses (REORGANISATION-AVANT.md, GABARIT D-2, étude editeur-lsp D-4, REORGANISATION.md, MIGRATION-CHEMINS.md) |
| `xml` | 0 | aucun fichier — `lsp-xml/` est un emplacement réservé créé vide |

## 5. Décisions ouvertes pour le propriétaire (avec recommandation)

Chaque recommandation s'appuie sur l'en-tête du fichier concerné (mesuré) :

- **D-1 — Documents de pilotage du moteur Java** :
  `docs/INTEGRATION-CODEIDE.md` (« intégration complète du nouveau
  lsp-java »), `docs/BASCULE-CODEIDE.md` (§8.5, bascule du moteur Java),
  `docs/PLAN-REECRITURE.md` (« réécriture du moteur Java »),
  `docs/RECALAGE-1.md`/`RECALAGE-2.md` (étapes N1/N2 du même plan),
  `docs/ECARTS-SERVEUR.md` (écarts du serveur Java),
  `docs/REGISTRE-CLIENT-K2.md` (vague V-G, ADR 0031(g) — LSP Java).
  **Recommandation : `lsp-java/docs/`** — tous documentent le moteur Java ;
  un document d'intégration COMMUN pourra naître dans `docs/` transverse
  lorsque d'autres LSP s'y rattacheront.
- **D-2 — `bancs/GABARIT-BASCULE-APPAREIL.md`** : gabarit de passe bascule
  sur appareil, cité par le prompt comme « gabarit commun » (§4.2) — les
  standards d'entrée (§7.3) l'imposent aussi au LSP XML.
  **Recommandation : `docs/GABARIT-BASCULE-APPAREIL.md`** (transverse).
- **D-3 — `bancs/ANALYSE-DELTAS-BASCULE.md` et `bancs/resultats-bascule.txt`**
  : analyse des deltas et résultats de la passe bascule §8.5 du moteur Java
  (moto g06, 2026-10-29). **Recommandation : `lsp-java/bancs/`**.
- **D-4 — `docs/etudes/editeur-lsp/`** : étude « responsabilités éditeur ↔
  serveur de langage » demandée par le propriétaire (consultation Android
  Studio) — valable pour **tous** les LSP.
  **Recommandation : rester en `docs/etudes/editeur-lsp/`** (transverse).
- **D-5 — Scripts de bancs/bascule du moteur Java** (10 fichiers :
  `kit-bascule.sh`, `kit-mesure.sh`, `preparer-oracle.sh`,
  `preparer-base-api.sh`, `preparer-manifeste-ask.sh`,
  `preparer-manifestes-corpus.sh`, `telecharger-corpus-android.sh`,
  `preparer-projet-android.sh`, `ask-export.init.gradle`,
  `manifeste-corpus.init.gradle`) : tous référencent les bancs du moteur
  Java (ADR 0016/0024/0030, §8.5). **Recommandation : `lsp-java/scripts/`**
  — `preparer-oracle.sh` (oracle du banc de parité croisée T) pourrait à
  terme devenir commun si le banc devient multi-LSP : decision à confirmer.
- **D-6 — `USAGE.md` (racine)** : documente le serveur Java (surface LSP du
  moteur Java). **Recommandation : `lsp-java/USAGE.md`** ; le README général
  (Phase 3) pointera vers les USAGE par LSP ; le Kotlin construit le sien
  en Phase 3 (§7.2).

## 6. Ordre des commits — Phase 2 (après validation)

Sur une branche de travail `refonte/organisation` (non fusionnée tant que
tout n'est pas vert) ; chaque commit reste **vert** en CI ou documenté
comme intermédiaire :

1. **Outillage de contrôle** (`scripts/`, communs) : vérificateur de liens
   (liens Markdown **et** références textuelles de chemins — mesuré :
   63 liens + 882 références à traiter), comparateur d'arborescence
   (nombre + contenu des fichiers avant/après, face à la photographie),
   `verifier-equivalence-digeste.sh` (piège P-1). Le vérificateur de liens
   doit passer **avant** les déplacements (zéro lien mort constaté) et
   **après**.
2. **Squelette** : dossiers cibles vides (`.gitkeep` si nécessaire) +
   `lsp-xml/README.md` + `lsp-xml/docs/PLAN-MIGRATION.md` + ligne commentée
   dans `settings.gradle.kts`.
3. **Documentation** : `git mv` des docs par LSP (D-1 à D-4 tranchées), un
   commit par LSP ; **puis** correction des liens dans des commits séparés
   (`refonte(liens): …`).
4. **Code** : `java/` → `lsp-java/moteur/`, `kotlin/` → `lsp-kotlin/moteur/`,
   `index|model|testing|ecj-art` → `socle/…` ; **même commit** :
   `settings.gradle.kts` (`projectDir`) + les 3 chemins de baselines codés
   en dur (P-3) + chemins des tâches Gradle. Un commit par famille.
5. **Bancs et baselines** par LSP ; **provenance** :
   `scripts/verifier-provenance.sh` paramétré par module + les 2 registres
   (9 lignes java + 26 lignes kotlin référencent des chemins — P-2) mis à
   jour **dans le même commit** que chaque déplacement concerné ;
   **digestes** (P-1) : preuve d'équivalence par script (l'ensemble des
   empreintes STRUCTURELLES est identique, seuls les préfixes de chemin
   changent), puis re-signature **dans un commit dédié et annoncé** ;
   `spike/` et `spike-kotlin/` déplacés avec leurs chemins de scripts (P-8) ;
   le contenu de `kotlin/src/main/vendor/**` (grammaire vendorée) reste
   **octet pour octet identique**.
6. **CI** (P-4) : workflows séparés par LSP (`ci-java.yml`, `ci-kotlin.yml`,
   `ci-socle.yml`) **uniquement si** un job de garde globale exécute toujours
   l'ensemble sur `main` ; **les noms de jobs sont conservés à l'identique**
   (ce sont des checks potentiels de protection de branche). Puis
   **nettoyage** : dossiers vides, doublons, `.gitignore` (nouveaux chemins).

## 7. Pièges connus et traitement (mesurés sur `integration/fusion`)

| Piège | Traitement prévu |
|---|---|
| **P-1** Empreintes signées contenant des chemins : `kotlin/src/test/resources/parite/digeste-corpus.txt` (521 entrées — re-signé en Phase 0 pour l'évolution du corpus Java, preuve au commit `16f06c5`) | Phase 2 : `scripts/verifier-equivalence-digeste.sh` prouve l'égalité de l'ensemble des empreintes (seuls les préfixes changent) → re-signature dans un commit dédié et annoncé ; étude d'une indépendance au préfixe (chemin relatif au module) **par ADR**. Jamais de modification de `vendor/**` (422 fichiers signés). |
| **P-2** `verifier-provenance.sh` + registres citent des chemins en dur (`kotlin/src/main/vendor`, `kotlin/src/main/kotlin/**`, manifestes ; 9 + 26 lignes de registres) | Paramétrage par module + mise à jour **dans le même commit** que le déplacement — « zéro fichier hors registre » vrai à chaque commit. |
| **P-3** Chemins en dur dans les modules (3 occurrences mesurées : baselines dans `java/build.gradle.kts` l.220/l.264 et `kotlin/build.gradle.kts` l.314) | Recensement exhaustif (`rg`) refait au moment du commit 4 ; correction + test dans le même commit. |
| **P-4** Noms de jobs CI = checks requis éventuels | **Noms conservés à l'identique** (10 jobs `ci.yml` mesurés) ; séparation des workflows par LSP avec filtres de chemins seulement si garde globale sur `main`. |
| **P-5** Scripts relatifs (10 scripts référencent des chemins du dépôt) | Corrections dans les commits de déplacement concernés ; tout doit fonctionner **depuis la racine** comme avant (testé par la CI et le comparateur). |
| **P-6** Liens relatifs et références croisées : 63 liens Markdown + 882 références textuelles de chemins dans les `.md` | Vérificateur de liens (commit 1) couvrant les deux ; **zéro lien mort avant et après** ; corrections dans des commits séparés. |
| **P-7** `.gitignore`, `.githooks/`, `LICENSE`/`NOTICE`, `gradle/wrapper` | Restent à la racine ; `.gitignore` mis à jour avec les nouveaux préfixes au moment des déplacements (blocs par LSP conservés). |
| **P-8** Spikes (`spike/`, `spike-kotlin/`) : scripts et résultats référencent des chemins, vendoring du spike Kotlin | Déplacés avec leurs chemins corrigés ; reproductibilité des mesures vérifiée (scripts exécutables depuis la racine). |

## 8. Invariants de sortie (comparés à `REORGANISATION-AVANT.md`)

1. Annotations de tests par module **identiques** (731 / 84 / 42 / 4 / 36 /
   534 / 8) et CI **verte** sur tous les jobs (mêmes noms).
2. Cahiers : 66 fiches / 449 comportements Java ; 53 fiches / 469
   comportements Kotlin.
3. Baselines **identiques octet pour octet** (SHA-256 consignés) — hors
   digeste re-signé avec preuve d'équivalence (P-1).
4. Chiffres dex Kotlin (method_ids 26 590 ; classes.dex 4 294 352 octets) et
   compagnon ecj (3 211 Ko) identiques.
5. Zéro lien mort (vérificateur) ; `git log --follow` retrouve l'historique
   des fichiers déplacés ; build, bancs et scripts exécutables **depuis la
   racine** comme avant ; chemins Gradle, coordonnées et capacités LSP
   inchangés.
6. Taille des jars moteurs : à compléter par le propriétaire dans la
   photographie (`[À COMPLÉTER PAR LE PROPRIÉTAIRE]`) puis comparée.

## 9. Plan de retour arrière

- **Tags de sauvegarde** : `pre-fusion/etude-moteur-java` et
  `pre-fusion/etude-moteur-kotlin` (poussés, jamais supprimés) — état exact
  avant la fusion. `integration/fusion` est réinitialisable à tout moment
  vers ces tags.
- Phase 2 sur la branche `refonte/organisation` : **non fusionnée** tant que
  tous les invariants ne sont pas verts ; chaque commit reste annulable
  (`git revert`) individuellement ; `git mv` preserve l'historique — un
  retour arrière est un `git mv` inverse + correction des références.
- Les branches `etude/*` et les tags ne sont **jamais** modifiés, ni
  supprimés, ni rebasés (interdits §9 du prompt).
- Décision §3.2.4 du prompt (après CI verte, à trancher par le propriétaire) :
  branche cible (**`main` proposée** — la tête distante actuelle est
  `etude/moteur-java`), protection de branche, devenir des branches
  `etude/*` (proposition : les conserver telles quelles, tagguées
  `etude-java-final` / `etude-kotlin-final`).

## 10. Ce qui N'EST PAS fait avant validation

Aucun déplacement de fichier (§4.3 du prompt). Les livrables présents sont
des **documents** uniquement : `docs/REORGANISATION-AVANT.md` (photographie),
`docs/REORGANISATION.md` (ce plan), `docs/MIGRATION-CHEMINS.md` (table) et
`scripts/generer-migration-chemins.py` (générateur de la table, sans effet
sur le dépôt). La Phase 3 (documentation par LSP) et la Phase 2 (exécution)
attendent l'accord du propriétaire sur ce plan et les décisions D-1 à D-6.
