# 01 — Étude des architectures d'analyse Kotlin (Phase 1)

> Étude « moteur Kotlin » pour CodeIDE sur Android/ART. Terrain amont :
> `JetBrains/kotlin` @ `e3806c8f7a9ed1b8506bdb710cb9e27ce50d0cd5`
> (HEAD au 30/10/2026, branche master) — clone partiel
> (`--depth 1 --filter=blob:none --sparse`) des chemins :
> `compiler/multiplatform-parsing`, `compiler/psi`, `compiler/fir/{tree,cones}`,
> `analysis/analysis-api`, `analysis/analysis-api-fir`,
> `analysis/analysis-api-standalone`, `analysis/low-level-api-fir`,
> `analysis/docs/design-documents`, `libraries/kotlinx-metadata`.
> Licence : Apache-2.0 (racine du dépôt) — **vérification d'exceptions
> R6.1** : `license/README.md` du tag v2.3.0 ne liste AUCUN fichier sous
> les chemins consultés (vérifié par grep, aucun hit
> « multiplatform-parsing », « analysis », « kotlinx-metadata »).
> Consultation uniquement, aucun code copié (R6.4) — la grammaire du spike
> est ré-obtenue amont au tag épinglé v2.3.0 (R9.2, `spike-kotlin/src/main/vendor/`).
>
> Chaque domaine K1–K12 tranche la question du prompt §6. Discipline R5 :
> tailles **[mesurées]** sur le clone (find/wc), le reste étiqueté
> [estimé]/[non vérifié].

## 0. Panorama des tailles amont [mesuré]

| Module amont | Fichiers Kotlin | Lignes | Rôle |
|---|---:|---:|---|
| `compiler/multiplatform-parsing` (HEAD) | 37 | 11 103 | la grammaire officielle (celle que l'ancien moteur vendore, 23 f / 9 585 l. au tag v2.3.0) |
| `compiler/fir/tree` | 658 | 52 127 | l'AST FIR (définitions générées + écrites) |
| `compiler/fir/cones` | 29 | 2 577 | les types FIR (ConeKotlinType) |
| `compiler/fir/resolve` | 151 f | n.m. | la résolution FIR (corps de la fidélité K2) |
| `compiler/fir/checkers` | 649 f | n.m. | les diagnostics FIR |
| `compiler/fir/raw-fir` | 908 f | n.m. | la construction de FIR brut depuis la syntaxe |
| `compiler/psi` | 3 854 f (tous fichiers) | n.m. | le PSI du compilateur |
| `analysis/analysis-api` | 5 537 | **98 269** | la façade publique Ka* (API + interfaces plateforme) |
| `analysis/analysis-api-fir` | 542 | 72 720 | l'implémentation de la façade sur FIR |
| `analysis/low-level-api-fir` | 2 536 | 67 384 | la machinerie de sessions/caches LL FIR |
| `analysis/analysis-api-standalone` | 142 | 9 051 | le mode autonome (hors IDE) — **NON PUBLIÉ sur Maven Central** [mesuré, `search.maven.org` numFound=0] |
| `libraries/kotlinx-metadata` | 64 | 9 074 | lecture/écriture `@Metadata` (bibliothèque autonome) |

L'ordre de grandeur structurant : **le seul étage « analyse » amont
(façade + impl + LL) ≈ 238 000 lignes**, sans compter FIR lui-même —
**×11 la production propre de tout l'ancien moteur Kotlin** (21 197 l.,
`00b-inventaire-existant.md` §1.1). Aucune « reprise » de cet étage n'est
envisageable : c'est une question d'architecture, pas de copie.

Faits de plateforme majeurs :
- **45 fichiers sur 306** de `analysis/analysis-api/src` importent
  `com.intellij.*` [mesuré, grep] — la façade « publique » est
  structurellement liée à la plateforme IntelliJ (scopes
  `GlobalSearchScope`, composants plateforme, dumb mode). Le standalone
  existe mais embarque la même dépendance de conception.
- Le document de conception `restricted-analysis.md` (l dumb mode,
  KT-74090) documente que **même JetBrains** n'a pas résolu la
  cohrence de l'Analysis API en mode indice partiel : « the main risk of
  naive dumb mode support is exceptions which are difficult to diagnose
  and (almost) impossible to reproduce » ; l'invalidation globale des
  caches à la sortie du mode restreint est la seule solution retenue.
  Notre contrat « index froid = silence » (ADR 0211 ancien) est
  **architecturalement plus simple et plus sûr** que ce que l'amont
  cherche encore à maîtriser.
- `Kotlin/kotlin-lsp` (incubateur JetBrains, licence Apache-2.0 vérifiée
  [mesuré, API GitHub]) est bâti sur **IntelliJ IDEA + le plugin Kotlin**
  (« The server is based on IntelliJ IDEA and the IntelliJ IDEA Kotlin
  Plugin implementation » — README) : c'est un serveur de bureau, pas un
  candidat embarqué ; sa consultation documente la cible « LSP complet »
  mais pas notre chemin (miroir du verdict V2 du spike Java).
- `syntax-api` 0.3.340 embarque `CancellationProvider` (annulation au
  lexing) et un `ThreadLocalRegistryHolderJvm` [mesuré, listing du jar] —
  le socle syntaxique est ART-compatible (0 motif sensible, spike §3).

## K1 — Syntaxe : grammaire, arbres, reparse, erreurs, KDoc, `.kts`

**Rôle** : produire un arbre tolérant depuis n'importe quel texte en
édition. **Clés amont** : `compiler/multiplatform-parsing/common/src`
(23 f / 9 585 l. @ v2.3.0 [mesuré]) — `KotlinParsing.kt` (2 866 l.),
`KotlinExpressionParsing.kt` (1 880 l.), `KDocParser`, lexers générés
(`gen/`, 2 776 l.) ; façade publique `KotlinParser(isScript, isLazy)` ;
l'assemblage builder vient de `SyntaxTreeBuilderFactory` (syntax-api).

**Détachable de la plateforme** : totalement — zéro `com.intellij.*`
hors syntax-api (qui est 0-motif ART, spike §3), zéro coroutines, zéro
réflexion [mesuré, scan ART].

**Exigences runtime sensibles ART** : aucune identifiée [mesuré] ;
`ThreadLocalRegistryHolderJvm` présent mais bénin (extensions de
registre).

**Incrémentalité** : l'API amont est **batch** (parse complet) — il
n'existe pas de re-parse incrémental public dans
multiplatform-parsing ; la mesure S2 (8,6 ms p50 à 3 000 l.) rend la
question non urgente à cette échelle [mesuré], l'ancien moteur a fait le
même choix (reparse complet, rapport faux positifs §5 : « l'étape D
n'a pas d'urgence mesurée »). Suivre les versions : le vendoring suit le
tag compilateur (bootstrap 2.2.x = notre chaîne 2.2.10, ADR 0115
ancien ; v2.3.0 vérifié compilable tel quel au spike [mesuré]) — la
dérive est maîtrisée par la suite de parité + digeste, pas par
`languageVersion` (renonciation ADR 0119 ancien, `kt.syntax` honnête sur
2.4+).

**Verdict : RÉUTILISER** (grammaire vendorée amont, octet pour octet,
remplacement scripté ; la façade maison mince par-dessus).

## K2 — Modèle de symboles et déclarations

**Rôle** : la représentation des déclarations (sources + classpath),
typealias, extensions, `expect`/`actual`. **Clés amont** :
`analysis/analysis-api` (KaSymbol/KaSession…, 98 269 l.) +
`analysis-api-platform-interface` (contrats de plateforme) ;
`compiler/fir/tree`+`cones` pour la représentation interne.

**Détachable** : la **forme** de l'API Ka* (symboles/visiteurs/kaSession
par module) est transposable comme vocabulaire ; l'**implémentation** ne
l'est pas (plateforme + FIR). **Frontière avec `:model`** : le modèle
existant (`ProjetLsp`, classpath v8) décrit le projet et ses entrées —
le modèle de symboles Kotlin décrit le CONTENU des sources : deux
couches distinctes, la seconde vivant dans `:kotlin` (Q-K3 de
`DEMANDES-INTERFACE.md`).

**ART** : n/a (couche de conception). **Taille** : l'ancien moteur
suffit à voir la taille du sous-ensemble réaliste : `symboles/` 149 l. +
`resolve/SymbolesProjet.kt` 759 l. [mesuré ancien] contre 98 269 l.
amont pour la façade complète.

**Verdict : S'INSPIRER** (vocabulaire Ka*, périmètre conservateur ;
représentation maison, jamais l'implémentation amont).

## K3 — Résolution de noms, portées, imports, récepteurs implicites

**Rôle** : lier les références aux déclarations. **Clés amont** :
`compiler/fir/resolve` (151 fichiers), design doc
`content-and-resolution-scope-architecture.md` (scopes de contenu vs
résolution, raffineurs, `KaResolutionScopeProvider`) — montre la
complexité amont : scopes par module, union/intersection de raffineurs,
extensions de résolution, fichiers générés.

**Règles de la spécification à couvrir exactement** [analyse depuis la
spec + cahier] : (a) hiérarchie de portées Kotlin (locals → membres →
englobantes → top-level paquet → imports explicites/joker/alias →
imports par défaut `kotlin.*`… `java.lang.*`) ; (b) récepteurs implicites
(récepteur de extension le plus proche, `with`/`apply` emboîtés,
DSL markers) ; (c) résolution en deux phases (spécification Kotlin
§imports/scopes) ; (d) `expect`/`actual` par source set. L'ancien moteur
couvre (a), (b) partiellement (K5 `with`, vague 5 scopes), (d) ; sa
sélection d'appelé reste **nom + arité** (jamais types complets en
routine — ADR 0223 l'a ajoutée par preuve bornée).

**Verdict : S'INSPIRER** (dérivation règle par règle depuis la
spécification, jamais correctif par correctif — la leçon des 24 classes
`Reproduction*` ; voir K9).

## K4 — Typage et inférence

**Rôle** : les types des expressions (nullabilité, génériques/variance,
lambdas à récepteur, SAM, surcharges par spécificité, opérateurs,
délégation, `suspend`, smart casts). **Clés amont** :
`compiler/fir/cones` (2 577 l. — les types), `compiler/fir/resolve`
(l'unification, la contrainte), `checkers` (la validation).

**Jusqu'où un modèle maison est fiable** : la ligne est mesurable —
l'ancien moteur démontre qu'un sous-ensemble **conservateur** (typage par
initialiseur, retours écrits, constructeurs résolus, littéraux,
smart casts POSITIONNELS, inférence de lambda par attente PROJET) tient à
zéro faux positif sur les quatre mondes Android [rapport d'audit
25/10]. Le domaine de K2 commence exactement là où la preuve exige
l'unification : variance, surcharges par spécificité, inférence de types
de lambda imbriqués, `suspend`, contraintes génériques. La politique
« preuve ou silence » est le garde-frontière : chaque règle non couverte
sous-rapporte au lieu de deviner.

**Verdict : S'INSPIRER avec périmètre assumé** (ADR 0107 : périmètre
borné + mode profond différé ; jamais « presque K2 »).

## K5 — Classpath, bytecode, métadonnées Kotlin

**Rôle** : lire les bibliothèques (`.class`, `@Metadata`, AAR,
propriétés vues en getters, `$default`, `@JvmName`). **Clés amont** :
`libraries/kotlinx-metadata` (64 f / 9 074 l., **bibliothèque autonome
Apache-2.0, publiée sur Central** [mesuré]) — `KmClass`,
`KotlinClassMetadata.read`… ; ASM pour le bytecode Java (BSD-3, déjà
dans la chaîne du dépôt via `:ecj-art`).

**Partage avec CIDX `:index`** : l'analyseur de `.class` existe déjà
(`AnalyseurClassFile`, repris vague 1b, 122 tests verts) ; la question
ouverte Q-K1/Q-K2 (`DEMANDES-INTERFACE.md`) est l'extension aux clés
taggées `top:`/`ext:` et au décodeur `@Metadata` — arbitrage
propriétaire, le spike n'y touchant pas.

**Verdict : RÉUTILISER** (kotlinx-metadata en artefact, analyseur CIDX
étendu ; le lecteur brut maison de l'ancien moteur est un candidat de
reprise voie C — différentiel 0 écart sur corpus réels [rapport A.3]).

## K6 — Synthétiques Android et code généré

**Rôle** : `R`, `BuildConfig`, ViewBinding, DataBinding, KSP/KAPT —
« preuve avant tout build ». **Clés amont** : **aucune** — l'amont ne
modélise pas les synthétiques Android avant build (l'IDE délègue à
Gradle) ; c'est une **invention maison** de l'ancien moteur (D5,
667 l., tests E2E réels).

**Règle de preuve** : les synthétiques résolvent AVANT tout build (DOM
borné sur `res/` + manifeste), et **s'effacent devant le généré réel**
(coupure dès qu'une classe homonyme existe dans le scan — la sortie du
build est la source de vérité). Les sources générées KSP/Hilt/Room sous
`build/` restent l'angle mort H2 (rapport faux positifs) : racines en
lecture seule à l'étude (ADR 0109).

**Verdict : RÉUTILISER** (capacité différenciante — reprise voie C
propre, ou réécriture cahier en main voie A ; les deux documentées).

## K7 — Interop Java ↔ Kotlin

**Rôle** : types/membres Java vus de Kotlin et navigation croisée.
**Clés** : l'ancien moteur `session/SourcesJava.kt` (715 l. — extraction
top-niveau des `.java`, garde « arité jamais contrôlée sur candidates
Java ») + les alias JVM↔Kotlin (`kotlin.String`↔`java.lang.String`,
ADR 0205) + compagnons (ADR 0222) + membres hérités mixtes (ADR 0216) ;
côté amont, les light classes (`analysis/light-classes-base`,
`symbol-light-classes`) sont la réponse d'IDE — hors de portée (poids).

**Contrat avec `:java`/`:index`** : le moteur Java réécrit de code-lsp
expose stubs/bindings ecj — le contrat inter-moteurs (Q-K5) est une
interface PUBLIQUE documentée, jamais un couplage interne. La vue
classpath partagée (`vueClasspathKotlin()`) reste le point d'attache.

**Verdict : S'INSPIRER** (le modèle SourcesJava est un candidat de
reprise voie C ; le contrat devient une interface publique).

## K8 — Scripts Gradle Kotlin DSL

**Rôle** : `.gradle.kts` éditables sans faux positif. **Clés** : mode
SCRIPT de la grammaire amont (`parseScript()` — présent [mesuré dans le
vendoring]) + la règle asymétrique ADR 0221 (nus muets — récepteur
implicite = contexte hôte non modélisable ; qualifiés faillibles) +
l'apport du classpath Gradle réel (INTEGRATION-CODEIDE §1 : trois jars +
AGP ; accesseurs générés `android { }` hors modèle — Gradle les génère
par projet).

**Verdict : RÉUTILISER** (le régime existe et est verrouillé ; la
stratégie réaliste = classpath par session scripts + nus muets, jamais
de modèle DSL complet).

## K9 — Diagnostics : fidélité, faux positifs, mode profond

**Rôle** : les `kt.*` — « preuve ou silence ». **Clés amont** :
`compiler/fir/checkers` (649 fichiers — le catalogue complet) contre le
catalogue conservateur maison (9 codes sémantiques + 3 syntaxiques,
fiches cahier). **Familles sûres sans K2** [démontré par le cahier +
audit] : structure (syntaxe, imports non conflictuels/inutilisés,
`valReassign`, `missingReturn`, `unreachable`, arité par candidates
prouvées, nullabilité à récepteur typé, garde de nullité) — toutes
décidables sur l'arbre + la résolution bornée. **Familles exigeant K2**
: type mismatch général (familles de littéraux ≠ unification),
surcharge par spécificité, inférence de lambda imbriquée, variance,
`suspend` hors contexte — le mode profond différé (S1 : 3,5 s d'amorçage
[mesuré]) ou le silence.

**Verdict : S'INSPIRER** (catalogue conservateur + banc de fidélité face
à K2 en oracle — ADR 0112/0119 ; jamais « presque compilateur »).

## K10 — Complétion et classement, navigation, refactorings

**Rôle** : la qualité perçue. **Clés** : côté Java, la chaîne weighers +
arrangement + split RPC (ADR 0012 du dépôt — designs IntelliJ étudiés) ;
côté Kotlin amont, la complétion vit dans le plugin IDE (hors portée).
L'ancien moteur offre la complétion **correcte** (contextuelle vague 1,
ADR 0228) sans le classement structuré.

**Mécanismes transposables** : toute la chaîne ADR 0012 (weighers
paresseux, gel, liftShorter, trim honnête, `RpcPrefixMatcher` →
re-filtrage local client) est **indépendante du langage** — le moteur
Kotlin neuf naît avec (voie A) ou la greffe (voie B/C). Navigation :
preuve FQCN par imports (typeDefinition/implementation), occurrences
conservatrices, rename borné même-fichier + multi-fichiers prouvé —
transposables du cahier tel quel.

**Verdict : S'INSPIRER fortement** (banc commun Java/Kotlin ADR 0016 —
c'est le critère §4 du prompt).

## K11 — Compose : previews et analyses spécifiques

**Rôle** : `@Preview` par SPI, frontière nette avec le rendu. **Clés** :
`preview/` ancien (522 l., C1 livré) : modèle NEUTRE (nom, plage,
paramètres tranchés dans la source, arbre d'appels borné 500
nœuds/profondeur 6), preuve FQCN par les imports, inlining des
composables du projet (cycles typés, budgets), échecs typés. Aucun
équivalent amont embarquble (le rendu ComposeUI est hors périmètre).

**Verdict : RÉUTILISER** (conception gardée telle quelle ; implémentation
reprise ou réécrite selon la voie).

## K12 — Concurrence, annulation, sessions, mémoire/GC ART

**Rôle** : ne jamais bloquer l'éditeur ; tas plat. **Clés** :
`CancellationProvider` de syntax-api (annulation au lexing [mesuré]) ;
l'ancien moteur : sessions étanches (`sessions distinctes — états
indépendants`, fiche cahier), verrou lecture/écriture de l'index (ADR
0211), observateurs neutres sans coroutines (ADR 0213 — condition pack
téléchargeable), index disque par empreinte (tas plat), scan paresseux
borné avec annulation coopérative (fiches `ScanProjetTest`).

**Comment éviter un verrou global et garder le tas plat** : (a) une
session = un verrou de document, jamais de verrou moteur global (le
comptage synchronized de l'ancien moteur reste à mesurer par outil sur
l'artefact — [non vérifié], ADR 0117) ; (b) l'index vit sur disque
(segments par empreinte, cache LRU borné en octets) ; (c) l'annulation
se propage par jeton aux passes longues (scan, indexation) ; (d) les
soft-références ART restent à mesurer sur appareil (miroir AL Java —
[gabarit appareil]).

**Verdict : S'INSPIRER** (schéma de concurrence par périmètre de
l'ADR 0010 Java transposé + le contrat d'indexation ADR 0211/0213 repris).

## Conclusion obligatoire (§6 du prompt)

**Ce qui fait la fidélité de K2 relève du compilateur (FIR), pas de
l'Analysis API ; ce qui fait sa vitesse relève de la plateforme autour.
L'Analysis API est une façade de 98 269 lignes dont 15 % importent la
plateforme IntelliJ, portée par LL FIR (67 384 l. de caches/sessions)
et FIR lui-même (cones + resolve + checkers). La fidélité sémantique
(cones, resolve, checkers) n'est PAS séparable du compilateur ; la
vitesse l'est encore moins (indices, caches, scopes — le document
`restricted-analysis.md` montre l'amont lui-même en difficulté sur les
modes à connaissance partielle, que notre contrat « index froid =
silence » résout par construction). Embarquer la fidélité K2 = embarquer
le compilateur : 53,6 Mo, 64 507 méthodes, ~2 170 motifs sensibles ART,
3,5 s d'amorçage [mesurés].**

**Familles de diagnostics et de complétions couvrables de façon
PROUVABLE par un modèle maison** : toutes celles que le cahier verrouille
à zéro faux positif — syntaxe tolérante, imports (inutilisé/
conflictuel), `valReassign`, `missingReturn`, `unreachable`, arité sur
candidates prouvées, nullabilité à récepteur typé, `kt.unresolved` par
preuve d'absence (univers complet), complétion membre/nom/type/contextuelle
vague 1, navigation par preuve FQCN, occurrences et rename conservateurs,
pliage/formatage borné, previews. La frontière est **la preuve** : une
règle est couvrable si son absence d'information se DECIDE (arité,
portée, littéraux) ; elle ne l'est pas si elle exige l'unification
(variance, spécificité, inférence imbriquée) — là, soit K2 en tâche
différée (3,5 s, annoncé), soit silence. La politique « preuve ou
silence » est donc le MÉTA-critère : elle transforme chaque lacune en
sous-rapport honnête au lieu de faux positif — la propriété que le
propriétaire a jugée non négociable (§4 : zéro faux positif toléré).
