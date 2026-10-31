# ADR 0024 — Contrôles Android Lint à valeur Java

- **Statut** : Proposé
- **Date** : 2026-10-31
- **Écart résolu** : G-011 (`02-reference-android-studio.md` §B13–B14)

## Contexte

La référence embarque ~400 contrôles Android Lint, dont un noyau à **valeur Java
immédiate** : `NewApi`/`InlinedApi` (appel nécessitant un API level supérieur au
minSdk), `ResourceType` (`@…Res`), `MissingSuperCall` (`@CallSuper`), `WrongThread`,
`CheckResult`, `Range` (`@IntRange/@FloatRange/@Size`), `SetTextI18n`, `Recycle`,
`CommitPrefEdits`, `ObsoleteSdkInt`, `MissingPermission`. Faits vérifiés : la base
`api-versions.xml` vit dans le SDK (`platforms/android-35/data/api-versions.xml`,
**5 523 007 octets**, 6 480 classes / 48 666 méthodes / 31 098 champs avec `since`) ;
la référence la compile en cache binaire (4,7 Mo → 1,7 Mo en mémoire) ; l'intégration
on-the-fly est un **ExternalAnnotator** qui (a) choisit le scope selon le type de
fichier (Java → `JAVA_FILE_SCOPE`), (b) **abandonne si les index sont en cours de
construction**, (c) ne fait tourner à la frappe que les contrôles **mono-fichier** —
les globaux (UnusedResources) et l'interprocédural (WrongThread avancé) attendent le
batch. Notre moteur a : les bindings ecj (annotations résolues), CIDX, et bientôt
minSdk dérivé du disque (ADR 0020).

## Sources consultées

- `02-reference-android-studio.md` §B13 (mécanismes, base API vérifiée sur l'artefact
  SDK, échappatoires `@RequiresApi`/gardes `SDK_INT`/`values-vNN`), §B14 (circuit
  external annotator, gating par scope), §B12 (annotations).
- `01-audit-moteur.md` §1.5, §3.9.
- ADR 0020 (minSdk + référentiel = prérequis), ADR 0023 (contrat d'inspection — les
  contrôles lint sont des inspections spécialisées), ADR 0025 (provenance des
  diagnostics).
- Documentation Android officielle (lint, sévérités, `@SuppressLint`, baseline).

## Options

**Option 1 — Contrôles lint maison sur contrat d'inspection + base API compacte
(recommandée).** (a) La base : **téléchargée par script en CI** depuis un SDK local
ou l'artefact officiel (jamais commitée), compilée en **segment CIDX binaire compact**
(`owner+name+desc → since`, ~1,7 Mo cible — miroir des optimisations documentées de la
référence) ; lecture du SDK de l'appareil si présent. (b) Les contrôles : des
`InspectionMoteur` (ADR 0023) **mono-fichier** en première vague : NewApi (avec les
échappatoires : `@RequiresApi`/`@TargetApi`, gardes `SDK_INT`, dossiers `values-vNN`),
InlinedApi, ObsoleteSdkInt, MissingSuperCall (fix : insérer `super.x();`), CheckResult
(`suggest=`), Range, ResourceType (ADR 0021/0020), SetTextI18n, CommitPrefEdits,
Recycle simple (dataflow local `obtain→recycle`, try-with-resources exempté).
(c) La **suppression** : `@SuppressLint("id")` + `tools:ignore` hors champ XML.
(d) Les globaux (UnusedResources) et WrongThread interprocédural : **mode analyse
globale différé** (commande, pas à la frappe).

**Option 2 — Embarquer le moteur lint complet.** Rejeté : pile non ART-clean,
dépendances, taille — hors budget mobile.

**Option 3 — Rien (les contrôles IDE suffisent).** Rejeté : NewApi est l'un des
contrôles les plus utiles au développeur Android (valeur « niveau AS » manifeste).

## Critères et mesures

- Fidélité : cas dorés extraits de la sémantique documentée (chaque échappatoire) +
  comparaison qualitative sur le corpus (5 projets) — [captures Android Studio
  demandées au propriétaire pour 20 positions NewApi, protocole §06].
- Empreinte : segment CIDX ≤ 2 Mo ; latence de la passe lint p95 [cible < +20 ms/
  fichier JVM au-dessus de la diagnose].
- Base : parsée en [à mesurer, cible < 2 s JVM, une fois, en tâche de fond].

## Décision proposée

**Option 1.** Sévérités alignées sur la référence (NewApi = Error **mais** : règle
ADR 0013/0023 — un contrôle lint ne remplace pas ecj ; NewApi est un cas **à part**
documenté ici : il signale du code **valide** pour la compilation mais **invalide à
l'exécution** sur minSdk — il se publie donc en sévérité LSP Warning par défaut,
montable à Error par profil, et sa provenance est `lint`). Le pipeline ADR 0025 le
planifie en **seconde passe asynchrone** (après diagnose, seulement si pas d'erreurs
bloquantes — même garde que la référence).

## Conséquences

- Le moteur « comprend » le SDK Android au niveau des API levels — valeur différenciante
  visible immédiatement (souligne `TextView#setTextAlignment` sous minSdk 17, propose
  le fix « entourer d'une garde `SDK_INT` »).
- Dépendance à un artefact SDK téléchargé en CI (comme android.jar des gates
  existantes — même mécanique, `scripts/` + empreinte).
- Dette : suivre les évolutions de `api-versions.xml` (re-génération du segment par
  script versionné).

## Plan de migration progressive

1. Script de téléchargement/compilation de la base + segment CIDX (vague 0 prolongée,
   sans dépendance décisionnelle).
2. NewApi + InlinedApi + ObsoleteSdkInt (même détecteur) + fix « garde SDK_INT ».
3. MissingSuperCall, CheckResult, Range, ResourceType, SetTextI18n, CommitPrefEdits,
   Recycle simple.
4. Mode global différé : UnusedResources (index + référentiel).

## Tests de non-régression et critères de sortie

- Cas dorés par contrôle (détection, échappatoire, suppression, fix exact) ; P3
  corpus (FP = 0, tout écart arbitré) ; latence ≤ cible ; base re-compilable
  bit-à-bit (empreinte versionnée).

## Risques

- **R1 — Base indisponible hors ligne** : lecture SDK local sinon repli honnête
  (contrôles API désactivés, annoncé — jamais de faux silence).
- **R2 — FP NewApi** (chemins d'échappatoire mal couverts) : cas dorés + captures
  propriétaire + sévérité Warning par défaut.
- **R3 — Taille/latence ART** : segment compact + seconde passe asynchrone + mesure
  appareil (gabarit 07).
