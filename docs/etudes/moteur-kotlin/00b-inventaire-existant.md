# 00b — Inventaire de l'existant (Phase 0)

> Étude « moteur Kotlin » pour CodeIDE sur Android/ART — inventaire de
> l'ancien moteur **sans lire sa production** (R9.1). Toutes les mesures de
> taille de code sont des **métriques d'outil** (`find`/`wc -l`/`rg`
> — script `scripts/metriques-kotlin.sh` du bac à sable, rejouable) ;
> le rôle des zones provient des **documents publics** de l'ancien dépôt
> (`docs/LSP_KOTLIN.md`, ADR, rapports) et des **tests** (autorisés R9.1) ;
> aucun fichier de `lsp/kotlin/src/main` n'a été ouvert pour la rédaction
> de ce document.
>
> Discipline R5 : chaque chiffre est étiqueté **[mesuré]** (commande
> rejouable), **[estimé]** (modèle documenté) ou **[non vérifié]**.

## 0. Méthode et terrain

- **Terrain** : `jjoblab/codeide-lsp` @ `bc11253b58a5eecd38194d46d5e862e1cf4185e3`
  (commit HEAD au 30/10/2026 — « moteur Java ARCHIVE, code-lsp devient
  l'habitat unique du LSP Java », v1.22.1 ; module `lsp/kotlin` **actif**,
  gel non applicable — seul `:lsp:java`/`:lsp:ecj-art` sont gelés).
  Clone en **lecture seule**, jamais modifié (R9).
- **Sources autorisées utilisées** : `docs/LSP_KOTLIN.md` (264 l.),
  `docs/RAPPORT-AUDIT-KOTLIN-LSP.md` (188 l.),
  `docs/RAPPORT-FAUX-POSITIFS-2026-10-26.md` (196 l.),
  `docs/INTEGRATION-CODEIDE-LSP-KOTLIN.md` (123 l.),
  `docs/LSP_MATRICE_CAPACITES.md` (114 l.), ADR 0094/0115/0119/0202/0203/
  0205/0210/0213/0216/0221/0228 (et ADR citées par renvoi : 0211/0212/0214/
  0217–0220/0222/0223/0226/0227/0229–0232/0233), `lsp/kotlin/src/test/**`
  (53 fichiers, lecture intégrale autorisée), `lsp/kotlin/build.gradle.kts`,
  `lsp/kotlin/src/main/vendor/VENDOR.md` (notice de vendoring — document,
  pas du code), `docs/THIRD_PARTY_NOTICES.md`.
- **Interdits respectés** : `lsp/kotlin/src/main` (hors `VENDOR.md` et
  métriques `wc`) — aucune ligne de code de production lue ; aucun projet
  GPL/AGPL consulté (R6.2) ; le « projet tiers de référence » GPL-3.0 cité
  par `LSP_KOTLIN.md` §2 n'a **pas** été consulté (R9.4).

## 1. Le module en chiffres [mesuré]

### 1.1 Volumétrie

| Zone | Fichiers | Lignes | Rôle (docs/ADR) |
|---|---:|---:|---|
| `api/` | 12 | 2 165 | SPI `MoteurLangageKotlin`, contrat `SessionLangageKotlin`, types neutres (pack langue D6) |
| `classpath/` | 2 | 664 | décodeurs `kotlin-metadata-jvm` (libs Kotlin) + lecteur de class-files brut (sans ASM) |
| `completion/` | 2 | 1 175 | complétion membre/nom/type/contextuelle (ADR 0228) |
| `diagnostics/` | 4 | 2 431 | catalogue `kt.*` conservateur, « explain », sous-rapport D3 |
| `documentation/` | 1 | 199 | KDoc (survol riche S2) |
| `format/` | 1 | 289 | formateur borné — ré-indentation seule (ADR 0202) |
| `index/` | 3 | 1 087 | index disque des appelables (`.kidx`, clés `top:`/`ext:`), pilote de passages (ADR 0211/0213/0214) |
| `parse/` | 5 | 1 240 | façade `Kt*` sur la grammaire vendorée, arbre immuable tolérant |
| `plis/` | 1 | 135 | pliage (`foldingRange`) |
| `preview/` | 1 | 522 | previews Compose `@Preview` par SPI (arbres d'appels, budgets) |
| `resolve/` | **19** | **7 966** | résolution déclarative, portées, typage borné, occurrences, membres manquants, sources Java, `with`, interrogations symboles |
| `session/` | 3 | 2 508 | session de parse/documents, scan projet, sources Java mixtes |
| `symboles/` | 1 | 149 | modèle de symboles neutre |
| `synthetics/` | 2 | 667 | R, BuildConfig, ViewBinding, DataBinding (D5) |
| **Total main hors vendor** | **57** | **21 197** | |
| `src/main/vendor/` | 23 | 9 585 | grammaire officielle v2.3.0 (voir §1.3) |
| **Total `src/main`** | **80** | **30 782** | |

Commande : `bash scripts/metriques-kotlin.sh` (rejouable). **Écart avec le
prompt §2** [consigné `ALERTES.md` A-K1] : le prompt annonce « ≈ 30 800
lignes (80 fichiers) **+** grammaire vendorée ≈ 9 900 lignes », suggérant
un total ≈ 40 700 ; la mesure donne 30 782 l. / 80 f. **incluant** le
vendor (9 585 l. / 23 f.). Le « 30 800 / 80 fichiers » est exact, mais il
contient déjà le vendor — la production propre est **21 197 l. / 57 f.**
Le vendor mesuré (9 585) est proche du « ≈ 9 900 » annoncé (écart 3 %,
fichiers non-Kotlin exclus du comptage).

### 1.2 Tests [mesuré]

| Mesure | Valeur | Commande |
|---|---|---|
| Fichiers de test | **53** | `find lsp/kotlin/src/test -name '*Test.kt' \| wc -l` |
| Lignes de test | **15 853** | `find … -exec cat {} + \| wc -l` |
| Méthodes `@Test` | **469** | `rg -t kotlin --count-matches '@Test\b'` |
| `@Disabled`/`@Ignore` déclarés | 0 (annotation) | scan — tests « ignorés » via hypothèses/environnement (`REPRO_COMPOSE_DIR`…), cf. rapports |

Le « ≈ 469 tests » du prompt §2 est **confirmé** [mesuré]. Répartition par
zone de test : `session/` 38 fichiers (dont 24 « Reproduction*» —
l'historique de correction vit dans les tests), `classpath/` 4,
`index/` 2, `parite/` 2, `parse/` 2 (+1 `parse/kt/`), `api/` 1,
`diagnostics/` 1, `documentation/` 1, `plis/` 1.

### 1.3 Grammaire vendorée [mesuré + notice]

- Source amont : `JetBrains/kotlin` tag **v2.3.0**, commit
  `f95cb2f7693a15966fd8fd9b1d78d440545e7b66`, chemin
  `compiler/multiplatform-parsing` (common + gen), Apache-2.0,
  `LICENSE.txt`/`COPYRIGHT.txt` présents dans le répertoire vendor
  (`VENDOR.md` de l'ancien dépôt — notice lue).
- 23 fichiers / 9 585 l. : `KotlinParsing.kt` (2 866 l.),
  `KotlinExpressionParsing.kt` (1 880 l.), `KotlinFlexLexer.kt` généré
  (1 724 l.), `KDocFlexLexer.kt` (1 052 l.)… [mesuré]
- **Zéro modification locale** (ADR 0115 : « octet pour octet ») ; le
  re-sync est un remplacement scripté
  (`scripts/vendor-grammaire-kotlin.sh` de l'ancien dépôt).
- Dépendances binaires amont : `org.jetbrains:syntax-api:0.3.340` +
  `org.jetbrains:annotations:26.0.2` (intellij-dependencies, Apache-2.0)
  [notice tierce de l'ancien dépôt].

### 1.4 Dépendances du module [mesuré — `lsp/kotlin/build.gradle.kts`]

| Artefact | Portée | Licence | Rôle |
|---|---|---|---|
| `org.jetbrains:syntax-api` | `api` | Apache-2.0 | arbre de construction syntaxique (base de la grammaire vendorée) |
| `org.jetbrains:annotations` | `implementation` | Apache-2.0 | annotations amont |
| `org.jetbrains.kotlin:kotlin-metadata-jvm` 2.2.10 | `implementation` | Apache-2.0 | décodage `@Metadata` du classpath |
| JUnit 4, `com.google.android:android` 4.1.1.4 (jar stubs AOSP API 16), kotlinx-datetime/serialization-core | `testImplementation` | divers | tests uniquement |

**Zéro dépendance de runtime lourde** : pas d'ASM côté moteur (le lecteur
de class-files est maison — `classpath/` 664 l.), pas de compilateur, pas
de kotlinx-coroutines dans le module (ADR 0213 option 1 : observateurs
neutres pour garder le pack téléchargeable). L'APK embarque la grammaire
compilée + syntax-api + kotlin-metadata-jvm : **jar moteur mesuré 257 Ko
à K1** [LSP_KOTLIN.md §5-K1], dépendances non comprises ; estimation T1
totale « ~2–4 Mo » [estimé — LSP_KOTLIN.md §6].

## 2. Architecture actuelle (évaluée depuis docs, ADR, tests et métriques)

### 2.1 Le choix de fond D1 (LSP_KOTLIN.md)

**Grammaire officielle du compilateur + modèle de symboles maison, aucun
compilateur ni FIR sur le chemin d'édition.** L'Analysis API (K2) y est
« réduite à un éventuel mode diagnostic profond à la demande ». Le motif
documenté : le frontend FIR à chaque frappe est trop lourd pour un
éditeur sur appareil (leçon rapportée du projet tiers de référence —
affirmation **non vérifiable** ici, R9.4 : ce projet est sous GPL-3.0 et
ne doit pas être consulté ; la citation provient du document interne).

Conséquences structurelles observables dans les métriques :
- la **résolution est le cœur du moteur** : 19 fichiers / 7 966 l.
  (37 % de la production propre) — résolution déclarative, typage borné,
  occurrences, membres manquants, sources Java, récepteurs `with`,
  interrogations symboles ;
- les **diagnostics** (2 431 l.) et la **complétion** (1 175 l.)
  consomment la résolution ; la politique « preuve ou silence » (D3)
  traverse tout : chaque voie de résolution ne conclut à l'absence que si
  elle est **prouvée** (univers complet, index portant, récepteur typé) ;
- le **parse est mince** (1 240 l. + 149 l. symboles) : la complexité
  syntaxique vit dans le vendor (9 585 l.) et dans syntax-api (artefact).

### 2.2 Ce que l'architecture obtient (points à préserver)

1. **Parité syntaxique par construction** : être la grammaire du
   compilateur est la propriété centrale (ADR 0115) ; suite de parité sur
   le corpus du dépôt (818→873 fichiers, 0 marqueur d'erreur, digeste
   SHA-256, tolérance troncature/injection) — les tests `parite/`
   verrouillent.
2. **Honnêteté « preuve ou silence » (D3)** : aucune absence affirmée
   hors du modèle ; les sous-rapports sont documentés (définitions
   classpath vides sans source, sélection par types complets limitée,
   jetons sémantiques non annoncés). L'audit du 25/10 rapporte **zéro
   faux positif sur les quatre mondes Android** (Compose, Views, KMP,
   scripts Gradle) — preuve de la méthode.
3. **Tas plat par conception** : index disque par empreinte de jar
   (`.kidx`, clés taggées `top:`/`ext:`), scan paresseux des racines
   sources, aucune structure de classes résidente ; re-mesuré le
   26/10 : reparse 31 ms froid / 21 ms chaud, diagnostics complets
   469 ms froid / 147 ms chaud (1 803 l., JVM CI 2 cœurs) [mesuré —
   RAPPORT-FAUX-POSITIFS §5].
4. **Découplage « pack langue » dès K1 (D6)** : SPI ServiceLoader +
   descripteur `version.properties`, aucune dépendance de module interne,
   types neutres sans coroutines (ADR 0213) — le pack téléchargeable
   différé reste peu coûteux à activer.
5. **Synthétiques Android avant tout build (D5)** : R, BuildConfig,
   ViewBinding, DataBinding modélisés depuis `res/` + manifeste (667 l.),
   coupés dès qu'une classe générée réelle existe — élimine le délai
   « databinding/R invisibles avant build ».
6. **Interop Java↔Kotlin pragmatique** : `session/SourcesJava.kt`
   (715 l.) — extraction top-niveau des `.java` pour complétion/
   résolution/définition croisées, avec garde « arité non extraite →
   `kt.argumentCount` ignore » (jamais de faux « 0 attendu »).
7. **Previews Compose par SPI** (`preview/` 522 l.) : modèle neutre
   (nom, plage, paramètres tranchés dans la source, arbre d'appels),
   preuve FQCN par les imports, inlining des composables du projet
   borné (500 nœuds / profondeur 6) — frontière nette avec le rendu.
8. **Interopérabilité avec l'infra Java existante** : classpath modèle v8,
   index ADR 0111 partagé, vue fusionnée par la session Java
   (`vueClasspathKotlin()`), LSP4J côté hôte — D4 : « le LSP Kotlin se
   branche au lieu de re-dériver ».

### 2.3 Ce que l'architecture subit (points de fragilité)

1. **Résolution heuristique, non fondée sur la spécification** : le
   prompt la dit « heuristique : à évaluer ». Les ADR la décrivent
   précisément : surcharges par **nom + arité** (jamais par types
   complets en routine), typage borné (locaux par initialiseur, retours
   écrits), smart casts **positionnels** (formes `if`/`when`/`&&`,
   annulés par `||`), inférence de lambda par l'attente du consommateur
   PROJET. Chaque garde D3 masque l'absence de preuve, mais la
   **couverture sémantique** reste un sous-ensemble arbitraire du
   comportement K2 — non mesurée face au compilateur.
2. **Deux très gros fichiers concentrent les risques** :
   `DiagnosticsSemantiques.kt` (1 784 l.) et `SessionLangageKotlinParse.kt`
   (1 241 l.) + `ResolutionKotlin.kt` (990 l.) — 4 015 l. dans 3 fichiers,
   19 % de la production propre [mesuré]. La doctrine maison du dépôt
   code-lsp (AGENTS.md, étude Java) penne contre les moteurs monolithiques.
3. **L'historique de correction vit dans les tests** : 24 fichiers
   `Reproduction*` sur 38 tests `session/` [mesuré] — chaque faux positif
   réel (Compose E1–E4/C1–C3, Views V1–V5, KMP K1–K3, Gradle G1,
   typealias, troncation, sources générées, companions, `Enum.entries`,
   builtins tableaux…) a produit un correctif **et** un test. C'est une
   richesse (cahier extractible) et un signal : la résolution a été
   construite correctif par correctif, pas par dérivation de la
   spécification Kotlin.
4. **Gardes d'univers incomplètes historiquement** : la troncation du
   scan (`BORN_FICHIERS = 2000`) ne se reflétait pas dans
   `estUniversConna()` (H1 du rapport faux positifs — confirmé, correctif
   recommandé en étape B) ; les **sources générées KSP/Hilt/Room** sous
   `build/` restent invisibles (H2 — confirmé, non corrigé à la date du
   rapport) [RAPPORT-FAUX-POSITIFS §1]. État exact à HEAD : à vérifier
   sur les tests (le cahier verrouille ce qui est couvert).
5. **Grammaire figée à v2.3.0** : renonciation `languageVersion`
   (ADR 0119) — le `kt.syntax` sur du Kotlin 2.4+ est « honnête » mais
   fréquent à mesure que l'écosystème monte ; le re-vendoring est
   scripté mais **manuel**, 2×/an, et chaque montée impose de
   réévaluer syntax-api + la chaîne d'outils (bootstrap).
6. **Complétion sans classement structuré** : l'ADR 0228 (contextuelle
   vague 1) et la matrice montrent une complétion **correcte** mais dont
   le classement n'a pas la chaîne weighers/arrangement étudiée côté Java
   (ADR 0012 du dépôt code-lsp) — pas de stats d'usage, pas de gel, pas
   de tri par pertinence prouvé. Le « retard majeur » est documenté côté
   ancien dépôt (rapport d'analyse comparative du 28/10, cité par ADR
   0228) [non vérifié — document interne non relu].
7. **Dépendance à l'hôte tri-langue** : l'adaptateur LSP4J vit dans
   `:lsp:host` (pas dans le module) — la migration vers code-lsp devra
   trancher où vit le transport (le module `:java` de code-lsp a
   réintégré le serveur LSP4J en N8 ; même question Kotlin).

### 2.4 Capacités exposées (matrice, colonne Kotlin) [mesuré — doc]

Annoncées et servies : sync complète, publishDiagnostics,
didChangeWatchedFiles, definition, typeDefinition, implementation,
references, documentHighlight, rename + prepareRename, documentSymbol,
workspace/symbol ⚠, selectionRange ⚠, completion (contextuelle v1),
hover, signatureHelp, codeAction (imports, membres manquants,
correctifs d'expression), formatting + rangeFormatting (ré-indentation
seule), foldingRange, inlayHint ⚠ (types certains + indices lambda).
**Non annoncées (honnêteté D3)** : semanticTokens (distinction non
fiable au parseur), onTypeFormatting, linkedEditingRange, codeLens,
callHierarchy, typeHierarchy, documentColor. Expérimentales : aucune
(super-définition et décompilation sont Java uniquement à ce jour —
l'ADR 0210 Kotlin « superDefinition » a été livré par le moteur Kotlin,
vérifier la matrice : ligne « Super-définition ✅ ⚠ Java / ❌ Kotlin » —
**écart interne entre ADR 0210 (livré, moteur+hôte+E2E) et la matrice**
(❌) [consigné `ALERTES.md` A-K2]).

## 3. Zones fonctionnelles — verdicts d'inventaire

| Zone | Lignes | Conserver / refonder / réécrire — avis d'étude (décision ADR 0100) |
|---|---:|---|
| `parse/` + vendor | 1 240 + 9 585 | **Conserver le principe** (grammaire officielle vendorée, façade mince) — c'est la propriété centrale ; la question est l'incrémentalité (reparse complet mesuré 21 ms/1 803 l. — l'étape D « diagnostics incrémentaux » n'a « pas d'urgence mesurée ») |
| `resolve/` | 7 966 | **À évaluer domaine par domaine** (K2–K4) : le périmètre couvert est arbitraire (correctif par correctif) ; une re-fondation sur la spécification exigerait de dériver les règles, pas de les accumuler |
| `diagnostics/` | 2 431 | Refonte candidate : le plus gros fichier du moteur ; la garde « preuve ou silence » doit survivre, l'implémentation peut être restructurée |
| `completion/` | 1 175 | Refonte candidate : classement structuré absent (chaîne weighers ADR 0012 Java) |
| `session/` | 2 508 | Refonte probable : couplage parse/documents/scan ; `SessionLangageKotlinParse.kt` 1 241 l. |
| `classpath/` | 664 | Conserver le principe (lecteur maison sans ASM, décodeur `@Metadata`) — différentiel 0 écart sur 1 007 classes android + 678 kotlin [mesuré — rapport A.3] |
| `index/` | 1 087 | Conserver le principe (`.kidx` par empreinte, clés taggées) — à aligner sur CIDX `:index` de code-lsp (K5 de l'étude) |
| `synthetics/` | 667 | **Conserver** (capacité différenciante, éprouvée par tests E2E ViewBinding/DataBinding) |
| `preview/` | 522 | **Conserver** (SPI neutre, budgets, frontière rendu) |
| `api/` | 2 165 | Refonte probable : le SPI doit s'aligner sur la façade `jo.codeide.lsp.java` de code-lsp (contrat de pack commun ?) |
| `format/`, `plis/`, `documentation/`, `symboles/` | 772 | Conserver (petits, bornés, testés) |

## 4. Cahier de spécifications (renvoi)

Extrait **des tests uniquement** (R9.5) : `docs/cahier-specifications-kotlin/`
— 53 fiches (une par classe de test), 469 comportements, chacun classé
**conforme à la spécification Kotlin / heuristique / contradictoire**.
C'est le contrat de toute voie A (réécriture) ou C (hybride).

## 5. Matrice de décision des voies de gouvernance (A/B/C/D)

> Comparaison à armes égales — **aucune conclusion pré-écrite** (§1 du
> prompt). La décision appartient au propriétaire (ADR 0100). Coûts en
> j-h **[estimé]** — méthode : comparaison aux fourchettes du plan de
> réécriture Java (160–270 j-h pour 449 comportements, 25 839 l.
> vendored), ajustée à la taille du moteur Kotlin (21 197 l. propres,
> 469 comportements) et au fait que le socle partagé (`:index`, `:model`,
> le protocole) **existe déjà** dans code-lsp.

| Voie | Contenu | Avantages | Coûts/risques | Provenance | J-h [estimé] |
|---|---|---|---|---|---|
| **A — Réécriture intégrale clean-room** | Même méthode que Java : cahier (469 comportements) + oracle boîte noire ; moteur neuf Kotlin dans `:kotlin` de code-lsp, designs Java transposés (weighers, ModCommand, phases rename, verrous par périmètre) | Base saine par construction ; alignement architectural total avec `:java` (bancs, CI, ADR) ; risque de contestation éliminé (le moteur actuel n'est pas frappé d'exclusion R9.3 — son statut est documenté, mais la réécriture rend la question vide) | Coût maximal ; double existence prolongée ; durcissement implicite (24 familles de reproduction) à re-découvrir | Propre par construction | **170–290** (miroir Java 160–270, +8 % volumétrie comportements, socle déjà là) |
| **B — Reprise contrôlée module par module** | Copie des zones saines après contrôle R9 (inscription préalable au manifeste Kotlin), refonte des zones fragiles | Valeur immédiate (synthétiques, preview, classpath) ; coût réduit | Registre fichier par fichier obligatoire ; l'architecture « correctif par correctif » migre avec le code ; tests de reprise ; dépendance au format de l'hôte ancien | Registre à tenir (R9.2/9.3) | **90–150** (contrôles + refonte ciblée) |
| **C — Hybride** : noyau réécrit + modules sains repris | Noyau (parse/résolution/session/diagnostics) réécrit depuis le cahier ; synthétiques, preview, classpath, format repris après contrôle | Équilibre coût/risque ; les modules « différenciants » à valeur immédiate sont repris ; le cœur heuristique est refondé | Deux régimes de provenance dans un même module ; frontières à définir précisément | Mixte — registre le plus fin | **110–190** |
| **D — Statu quo amélioré** | Le moteur reste dans `codeide-lsp`, correctifs continus (H1/H2, étape B du rapport) | Zéro migration ; l'équipe continue sur le code connu | Contredit la vision « code-lsp = dépôt officiel des LSP » (ADR 0002) ; les gains d'architecture Java (bancs, classement, CI dex) ne s'appliquent pas ; l'hôte tri-langue reste le patron | Inchangé | **30–60** de correctifs ciblés (H1+H2+builtins+`Enum.entries` ≈ étape B) |

**Fait structurant pour la décision** : le socle que le moteur Java de
code-lsp a payé en vagues 0/1 (`:index`, `:model`, protocole réécrit,
CI, bancs) **existe déjà et est vert** (122 tests repris + CI) — le coût
d'entrée d'une voie A Kotlin est structurellement inférieur à ce qu'il
a été pour Java, où il a fallu créer le dépôt et le socle avant le
moteur. À l'inverse, le moteur Kotlin actuel est **en production et
jugé « déjà pas mal »** (prompt §0) : la valeur d'une réécriture intégrale
est dans l'alignement architectural (bancs de qualité, classement,
parité mesurable face à K2) — pas dans la correction de défauts massifs
(les quatre mondes Android sont à zéro faux positif).

## 6. Demandes d'interface (renvoi)

Toute évolution nécessaire de `:model`/`:index` (partagés avec Java) est
consignée dans `docs/etudes/moteur-kotlin/DEMANDES-INTERFACE.md` —
arbitrage propriétaire (R3).

## 7. Alertes (renvoi)

Écarts entre le prompt et la réalité vérifiée : `docs/etudes/moteur-kotlin/ALERTES.md`.
