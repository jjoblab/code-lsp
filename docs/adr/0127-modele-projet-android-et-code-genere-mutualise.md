# ADR 0127 — Modèle de projet Android et code généré (mutualisé avec le moteur Java)

- **Statut** : Proposé
- **Date** : 2026-10-31
- **Amende** : ADR 0109 (extension H2 « à décider »), ADR 0101 (frontières)
- **Écarts** : GK-006, GK-007, GK-008 (KH8)
- **Alignement** : ADR 0020/0021/0022 de la mission jumelle Java
  (décisions D2/D3 du propriétaire, approuvées le 31/10/2026)

## Contexte

Le moteur sait prouver les synthétiques **avant build** (R/ViewBinding/
BuildConfig par DOM `res/` + manifeste, effacés devant le généré homonyme —
atout, audit §1.8), mais : le dossier `build` est **exclu du scan**
(`ScanProjetParesseux.kt:654`), donc le code **généré après build**
(KSP/KAPT : Room, Hilt, Safe Args, Moshi) est invisible, et les **plugins de
compilateur** (Compose, Parcelize, serialization, allopen/noarg) ne sont pas
modélisés. En parallèle, la mission Java a conçu **le** modèle de projet
Android partagé (ADR 0020 : champs `namespace`, `minSdk/targetSdk/compileSdk`,
`res/`, manifeste, `buildFeatures`, processeurs, variantes + référentiel de
ressources + dérivateur disque) et les classes light (0021) — le propriétaire
a arbitré (D2) : **un seul** modèle, produit par l'hôte, dérivable du disque.

## Sources consultées

- Audit §1.8, §3.9 ; référence §B8/B9 (ressources/binding et code généré
  côté Android Studio : racines générées = sources après build, plugins par
  points d'extension IDE).
- ADR Java 0020 (modèle + référentiel + dérivateur disque), 0021 (classes
  light, priorité généré réel), 0022 (segment CIDX).
- `docs/PROVENANCE-REPRISE-KOTLIN.md` (les synthétiques repris R1/R2).

## Options

| Option | Description | Critères |
|---|---|---|
| **1 — Consommer le socle partagé Java + lever H2 côté Kotlin (recommandée)** | `:model` v2 étendu (ADR Java 0020) consommé en lecture ; référentiel de ressources = UN composant partagé (pas deux) ; lecture des racines `build/generated` dans un scan DÉDIÉ post-build (priorité : généré réel > synthétique, miroir 0021 R1) ; plugins de compilateur modélisés DÉCLARATIVEMENT | zéro duplication (D2/D3) ; l'atout « avant build » est préservé (le synthétique ne meurt pas : il n'y a pas TOUJOURS un build) ; effort mutualisé |
| **2 — Modèle Android propre au Kotlin** | refaire côté Kotlin | rejeté : divergence garantie, coût doublé — la mission Java a déjà arbitré le contraire |
| **3 — Attendre la synchronisation Gradle de l'hôte** | ne rien faire sans sync | contredit l'axe de dépassement n° 1 (intelligence avant la première sync — §C de la référence) |

## Critères et mesures

- Levée de l'exclusion H2 : le scan **post-build** ajoute les racines
  `build/generated/**` DÉTROITÉES (KSP/KAPT identifiés par convention de
  répertoires AGP) au modèle projet — la priorité « généré réel > light »
  est la règle Java 0021 R1, testée par équivalence (banc P2/P6 sur corpus
  avec `prepares` incluant les générés) ;
- plugins de compilateur : **régime déclaratif borné** — Compose = contexte
  `@Composable` prouvé par annotation décodée (amende du décodeur, ADR
  0128) ; Parcelize = supertype `Parcelable` injecté ; serialization/
  allopen/noarg = **silence documenté** (on ne simule pas leur sémantique,
  on ne la contredit pas) ;
- cibles : dérivateur disque ≥ 95 % de champs corrects (cible Java 0020,
  reprise telle quelle) ; scan res/ p95 < 150 ms JVM.

## Décision proposée

**Option 1**. Le moteur Kotlin consomme `:model` v2 et le référentiel de
ressources partagés ; il lève l'extension H2 (racines générées) avec la
règle de priorité généré > light ; il ne duplique AUCUN composant de
ressources. Les désaccords éventuels avec la mission Java sont consignés
dans DEMANDES-INTERFACE, jamais divergés en silence.

## Conséquences

- Dépendance de séquencement : la V-E Kotlin suit la V-B Java (livraison du
  modèle v2) — le plan (§8) place la vague Android réel après la V-B
  typage, ce qui laisse au Java le temps de livrer ;
- `ClassesSynthetiques` garde son rôle AVANT build et délègue APRÈS build.

## Plan de migration progressive

V-E1 (modèle partagé consommé + champs Android) → V-E2 (racines générées,
H2 levée) → V-E3 (plugins de compilateur déclaratifs + Compose 0128).

## Tests de non-régression et critères de sortie

- Fiches 034/039/040 inchangées (avant build) ; nouveaux comportements :
  `build/generated` résolu (P6 corpus), équivalence light↔généré = 0 écart
  (miroir banc Java), FP < 0,5/1 000 l. maintenu.

## Risques

- R1 — AGP change les conventions de répertoires générés : conventions
  configurable + repli silence ;
- R2 — Couplage au rythme Java : la V-E est conditionnée (dépendance
  explicite dans le plan).
