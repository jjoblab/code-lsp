# 04-plan-vagues — Plan d'exécution par vagues (Phase 5)

> Mission « niveau Android Studio Kotlin » — date : 2026-10-31.
> **Principes** (prompt §8) : vagues **courtes, livrables, réversibles**
> (drapeau pack + repli sur l'état précédent à chaque vague) ; critères de
> sortie **mesurables** ; effort en **j-h agent [estimé]** (méthode « socle +
> marginal », vélocité observée 0,087–0,147 h agent par j-h postulé —
> RECALAGE-2) ; **STOP après la V0** : aucune vague n'est lancée sans
> décision du propriétaire (§10 du prompt). Les cibles de vitesse §0.1
> (< 100 ms p95 perçu complétion/hover/signature ; diagnostics ~300 ms ;
> p99 régulier ; aucune fonctionnalité indisponible pendant l'indexation)
> sont des **cibles à valider par la passe appareil** (gabarit
> [07](07-gabarit-mesure-appareil.md)) — le facteur ART observé côté Java
> est ≈ 4× [CONFIRMÉ Java, NON VÉRIFIÉ Kotlin].

## §0 La vague V0 — MESURE (LIVRÉE par cette mission)

**Périmètre livré** : banc de cohérence **P1–P9**
(`kotlin/src/test/kotlin/jo/codeide/lsp/kotlin/coherence/`), corpus réel
**6 projets Apache-2.0 épinglés** (`scripts/telecharger-corpus-kotlin.sh`
+ `bancs/corpus-android-kotlin/LISEZMOI.md`), protocole de captures lot E
(`06-…` + kit), gabarit de mesure appareil (`07-…`), fiches de comportement
KC-01…KC-12, et la **mesure inaugurale P1 auto-corpus** : 99 fichiers /
34 934 l. / **980 FP = 28,05 FP/1 000 l.** / FN = 0 / 36 s (audit §4.3).
**Critères de sortie atteints** : suite `:kotlin:test` verte (gates corpus
ignorées proprement), CI inchangée, baseline FP consignée. Ne dépend
d'aucune décision — c'est pourquoi elle est déjà livrée.

## §1 Séquence proposée (à arbitrer)

```
V0 mesure (LIVRÉE)
 └─ V-A fiabilité & bascule ── V-B typage palier 2 ── V-D complétion smart/chain
        │                            │
        │                            └─ V-C diagnostics élargis (gated FP)
        │
        └─ V-E Android réel (modèle partagé, généré, Compose) ── V-G Gradle KTS
                 │
                 └─ V-F actions & refactorings ── V-I protocole/usages à l'échelle
                                              └─ V-H palier profond (si arbitré)
```

Ordre motivé : V-A rend le moteur **vrai** (baseline FP) et **visible**
(bascule) — sans elle, tout le reste est invisible ; V-B débloque V-D et
une partie de V-C ; V-E suit la livraison du modèle partagé Java (D2) ;
V-H en dernier (arbitrage 0124).

## §2 Détail des vagues

### V-A — Fiabilité & bascule (P0) — 14–24 j-h agent, 2–3 séances
- **Périmètre** : (a) correction des familles FP mesurées (`it` 603,
  imports implicites `kotlin.*` ~100, `this` 48, segments racines ~60,
  `T` 11, `valReassign` indexé 36, `argumentCount` 38, `unsafeNullable` 23
  — audit §4.3) ; (b) budgets octets + observateur mémoire `onTrimMemory`
  (GK-031) ; (c) fraîcheur inter-fichiers par empreintes STAT si
  mutualisée avec Java (GK-038) ; (d) **bascule CodeIDE I0–I6 + publication
  du pack** (GK-027/GK-028).
- **ADR** : 0122 (palier 1 corrigé), 0134 (V-A), 0135 (escalade stricte),
  0136 (séquencement bascule).
- **Critères de sortie mesurables** : P1 auto-corpus **FP < 0,5/1 000 l.
  en mode strict** ; suite 523+ verte ; CI 10/10 ; pack publié et témoin
  `serverInfo` consigné côté client ; dex ≤ 26 590 méthodes (gel socle,
  ADR 0133) ; p95 JVM ≤ baseline + 10 %.
- **Dépendances** : hôte (`:lsp:host` adaptation I1, arbitrage A/B Java),
  client (bump catalogue, consommation des 19 capacités).
- **Risques** : calibrage du silence (ne pas régresser la détection utile) ;
  désalignement I1 Java.

### V-B — Typage étendu, palier 2 (P1) — 24–40 j-h, 3–5 séances
- **Périmètre** : V-B1 représentation (arguments, nullabilité, plateforme,
  types fonctionnels) ; V-B2 extensions génériques + surcharges ;
  V-B3 récepteurs implicites + smart casts étendus (`when` à sujet, elvis).
- **ADR** : 0123 (frontière versionnée `SPECIFICATION-TYPES.md`), 0122.
- **Critères de sortie** : différentiel types vs oracle ≥ 95 % sur le
  sous-ensemble couvert ; FP < 0,5/1 000 l. maintenu ; P6 couverture typée
  ≥ 80 % sur corpus réel ; dex ≤ budget packié (0133) ; fiches 046/049
  inchangées (non-régression).
- **Dépendances** : aucune externe ; garde dex interne.

### V-C — Diagnostics élargis (P2, gated) — 12–20 j-h, 2–3 séances
- **Périmètre** : extension du catalogue par décidabilité (ADR 0125) :
  d'abord les familles sans inférence (`when` non exhaustif enum/sealed,
  suspend hors contexte prouvé, contraintes simples), puis typage simple.
- **Critères de sortie** : chaque nouveau code entre avec FP/1 000 < 0,5
  mesuré sur corpus (P1(b) manifestes) + rappel mutants ≥ 0,95 sur sa
  famille (P3) ; `@Suppress` servi (GK-021).
- **Dépendances** : V-B pour la moitié du catalogue ; V-A pour la garde.

### V-D — Complétion smart/chain/DSL (P1) — 10–16 j-h, 1–2 séances
- **Périmètre** : smart par type attendu, chain par extensions applicables,
  récepteurs implicites, `when`/`override` (KC-01…KC-05), 50+ positions
  réelles étiquetées avec split train/éval (remplace le banc saturé),
  frontière RPC `x-codeide.completionRanking` à arbitrer.
- **Critères de sortie** : top-1 ≥ 0,80 / MRR publiés sur le split éval ;
  zéro suggestion insérable cassée (P6 ≥ 0,99) ; p95 complétion ≤ cible
  gabarit 07.
- **Dépendances** : V-B (type attendu/récepteur) ; client (frontière RPC).

### V-E — Android réel (P1) — 14–24 j-h, 2–3 séances
- **Périmètre** : V-E1 consommation `:model` v2 + référentiel de ressources
  partagé (D2) ; V-E2 racines `build/generated` (H2 levée, généré > light) ;
  V-E3 plugins de compilateur déclaratifs + Compose (0128) + interop
  arité/nullité (0129 V-E).
- **Critères de sortie** : équivalence light↔généré = 0 écart (miroir
  Java) ; corpus `nowinandroid` (Hilt/Room KSP) : FP < 0,5/1 000 l. avec
  manifestes ; contexte `@Composable`/`suspend` prouvé par annotation
  décodée.
- **Dépendances** : `:model` v2 livré par la mission Java (V-B Java) —
  sous réserve de son calendrier.

### V-F — Actions & refactorings (P2) — 12–20 j-h, 2–3 séances
- **Périmètre** : `source.organizeImports` (KC-10) d'abord, intentions
  (KC-11), refactorings bornés (extraction locale, inline simple, safe
  delete par usages prouvés), `codeAction/resolve`, prévisualisation.
- **Critères de sortie** : P5 cycle quick-fix vert (diagnostic cible
  disparaît, aucune nouvelle erreur, code compilable) sur chaque action ;
  WorkspaceEdit déclaratif (ADR 0115) réutilisé ; dex en pack.
- **Dépendances** : client (kinds, prévisualisation).

### V-G — Gradle KTS & catalogue (P1) — 8–14 j-h, 1–2 séances
- **Périmètre** : lecture disque `*.gradle.kts`/`libs.versions.toml`
  (ADR 0130), complétion/navigation/diagnostics bornés ; écriture du
  segment CIDX (0129 V-G).
- **Critères de sortie** : sur `nowinandroid` (40 `.kts` + catalogue) :
  zéro faux positif, rappel des accesseurs disque ≥ 90 %, navigation
  catalogue ↔ script fonctionnelle.
- **Dépendances** : V-E1 (modèle projet).

### V-H — Palier profond (P3, SI arbitré) — 8–16 j-h + décision
- **Périmètre** : service profond optionnel (processus déporté/démon,
  ADR 0124), UX du délai, provenance `palier 3`.
- **Critères de sortie** : premier diagnostic profond < 10 s, chaud < 2 s,
  mémoire additionnelle < 300 Mo [cibles à valider] ; aucune régression
  §0.1 ; CI inchangée service éteint.
- **Dépendances** : **propriétaire** (arbitrage après mesures gabarit 07 +
  confirmation hôte Q-K7).

### V-I — Protocole, sémantique & usages à l'échelle (P2/P3) — 10–18 j-h, 1–2 séances
- **Périmètre** : semanticTokens fondé palier 2, hiérarchies, documentColor
  (référentiel couleurs), diagnostics enrichis (provenance,
  relatedInformation), rename/navigation inter-langage (0129 V-I), index
  d'usages SI mesures le déclenchent (0132).
- **Critères de sortie** : catégories semanticTokens fiables listées et
  testées ; P7 rename sûr étendu aux projets mixtes ; `references` p95 ≤
  cible sur le plus gros module du corpus.
- **Dépendances** : V-B ; client (consommation) ; mission Java (phases
  partagées).

## §3 Synthèse d'effort

| Vague | Effort [estimé] j-h agent | Séances |
|---|---|---|
| V0 mesure | **livrée** | — |
| V-A fiabilité & bascule | 14–24 | 2–3 |
| V-B typage palier 2 | 24–40 | 3–5 |
| V-C diagnostics élargis | 12–20 | 2–3 |
| V-D complétion smart/chain | 10–16 | 1–2 |
| V-E Android réel | 14–24 | 2–3 |
| V-F actions & refactorings | 12–20 | 2–3 |
| V-G Gradle KTS | 8–14 | 1–2 |
| V-H palier profond (si arbitré) | 8–16 | 1–2 |
| V-I protocole & échelle | 10–18 | 1–2 |
| **Total** | **112–192** (+ V-H optionnel inclus) | **15–25** |

Recoupement matrice (§5 de la matrice : 149–264 j-h toutes options) :
l'enveloppe du plan est la part réaliste court terme (V-H chiffré à part
car conditionné). **Recalage obligatoire après V-A** (discipline des
recalages du dépôt — la vélocité remplace les postulats).

## §4 Ce qui revient à l'HÔTE (`codeide-lsp`), au CLIENT (`cel-lsp`/code-editor) et à la mission Java

**Hôte** : adaptation `:lsp:host` type I1 (bascule, arbitrage A/B Java) ;
SPI observateur mémoire (`onTrimMemory`) ; routage du classpath
(provisionnement Gradle des manifestes si la CI veut les gates corpus) ;
environnement déporté éventuel (Q-K7, V-H) ; confirmation Q-K4
(coordonnées de publication Kotlin).

**Client** : bump catalogue + consommation des 19 capacités ; insertion
de l'import auto (`data {import}`) ; kinds `source.*`/`refactor.*` et
prévisualisation (V-F) ; `x-codeide.*` versionnées (classement, usages
groupés miroir Java) ; coloration de repli tant que semanticTokens absent.

**Mission Java** : livraison `:model` v2 + référentiel de ressources
(ADR 0020/D2 — préalable V-E1) ; synchronisation des phases interop
(segment CIDX, rename croisé — ADR 0129) ; harmonisation des seuils
qualité (ADR 0135 ↔ 0030) ; demande formelle dans
[`DEMANDES-INTERFACE.md`](../moteur-kotlin/DEMANDES-INTERFACE.md).

**Propriétaire** : décisions §10 (recommandation unique, palier 2, profond,
enveloppe dex, séquencement bascule, Q-K1/K-K2/Q-K5/Q-K6, corpus MPL-2.0
éventuel) ; exécution des captures lot E et de la passe appareil ;
validation des re-signatures de baseline (E-K9).

## §5 Gouvernance

- Chaque vague : drapeau pack + repli documenté ; critères de sortie
  mesurés au banc (P1–P9) et consignés en PV (miroir des PV Java V-A/V-E) ;
- CI : mode rapport pendant V-A puis escalade stricte (`BANC_COHERENCE_
  STRICT=true`, ADR 0135) ; gates corpus `assumeTrue` avec raison ;
- recalage de vélocité après V-A ; toute re-signature de baseline par PR
  dédiée (R5) ; E-K8 sous surveillance à chaque vague (gel socle, 0133).
