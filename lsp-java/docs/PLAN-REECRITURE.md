# PLAN-REECRITURE — Ré-estimation du programme de réécriture du moteur Java

> **Statut : APPROUVÉ — les quatre décisions du §10 tranchées par le
> propriétaire le 28/10/2026, en séance, sur le soir de la remise du plan**
> (enveloppe approuvée, engagement borné N1 + banc croisé, mesure sur
> appareil = porte d'entrée de N1, séquence confirmée, écart fiche 054 en
> parité stricte). Livrable (d) de la vague 1 restructurée
> (plan de migration ADR 0001 : « ré-estimation du programme de réécriture du
> moteur, j-h par étage, cahier en main, soumise au propriétaire avant l'étage
> noyau »).
> **Recalage 1 (fin N1, §8.2) — EXÉCUTÉ le 29/10/2026 : voir
> `RECALAGE-1.md`** (N1 livré au commit `ce291dc` ; vélocité mesurée,
> re-projection N2–N8, décision de suite soumise).
> **Recalage 2 (fin N2, §8.2) — EXÉCUTÉ le 29/10/2026 : voir
> `RECALAGE-2.md`** (N2 livré aux commits `5d3ca1a`+`bc70055` ; confiance
> N3–N8 re-évaluée sur les deux étages extrêmes de l'abaque, décision de
> suite soumise).
> **Date** : 28/10/2026. **Base** : cahier de spécifications (`docs/cahier-specifications/`
> — 65 fiches, 449 comportements), étude PSI D1–D12, ADR 0001–0018.
> **Discipline R5** (étude, §méthode) : chaque chiffre de ce document est
> étiqueté **[mesuré]** (rejouable par script/commande), **[estimé]** (modèle
> documenté, postulats visibles) ou **[non vérifié]** (non convertible en
> chiffre honnêtement). Aucune moyenne non étiquetée.

---

## 1. Mandat et périmètre

La décision ADR 0001 (option 2 — réécriture intégrale) a rendu **caduque** la
fourchette « vagues 0–4 : 75–130 j-h » de `00-synthese.md` §4, qui supposait
le moteur vendored **repris comme substrat**. Le présent document la remplace :
il estime le coût de la réécriture **cahier en main**, par étage, pour le
périmètre du domaine moteur Java (ADR 0002 : modules `:java` noyau réécrit,
`:index`/`:model` repris, `:testing`, `:ecj-art` livré).

**Ce qui est déjà livré** (hors périmètre de l'estimation, listé pour situer
la base de départ) :

| Livrable | État | Réf. |
|---|---|---|
| Vague 0 — socle, licences, CI, contrôles | **livrée** | commit 9411b06 |
| Vague 1a — cahier (65 fiches, 449 comportements, 3 593 l.) | **livré** | commit 1eadc96 |
| Vague 1b — reprises `:model` + `:index` (45 f., 122 tests verts) | **livrée** | commit 04b51be |
| Vague 1c — patcheur `:ecj-art` réécrit (12 f., 1 521 l., 36 tests) | **livré** | commit d016030 |
| Vague 1d — le présent plan de ré-estimation | **soumis** | ce document |

**Ce que l'estimation couvre** : les étages de réécriture N1–N8 du moteur
(module `:java` et extensions), le transverse qualité T (banc de parité croisé
ADR 0016, reprise de `:testing`, CI dex/empreinte/latence), jusqu'à la parité
du cahier sur appareil (critère de bascule ADR 0001 §Tests de non-régression).

**Ce qu'elle exclut** (§7) : les vagues conditionnelles 5 (parsing incrémental
`java-syntax`, conditionné aux conditions artefacts JetBrains) et 6 (montée
JDT ≥ 3.43), les moteurs Kotlin/XML (hors périmètre ADR 0002), la maintenance
du vendored pendant la transition (dépôt `codeide-lsp`, côté propriétaire), et
l'intégration côté application CodeIDE (dépôt tiers — interface identifiée,
effort à estimer dans ce dépôt).

---

## 2. Données de base — agrégation du cahier [mesuré]

Agrégation exhaustive par script (`scripts/` de la mission, reproductible) des
65 fiches : domaine normalisé × nombre de comportements. Total : **450
sections « Comportements » = 449 méthodes de test + 1 service documenté**
(fiche 037, `buildRJar` — aide de fixture, pas un test). Quatre fiches
« mixtes » (020, 027, 028, 032) sont allouées à leur domaine dominant.

| Domaine | Fiches | Comport. | Fiches (N°) |
|---|---:|---:|---|
| caches-perf | 9 | 60 | 006 014 015 024 025 033 034 041 047 |
| diagnostics | 9+1 mixte | 66 | 001 013 019 028* 039 042 051 063 064 065 |
| completion | 8+1 mixte | 66 | 007 008 035 036 043 044 050 053 020* |
| hover-inlay | 5 | 48 | 002 003 021 023 030 |
| navigation | 5+1 mixte | 52 | 005 009 049 060 062 027* |
| cycle-vie-lsp | 3 | 29 | 055 058 059 |
| formatage | 3 | 20 | 016 017 057 |
| rename | 2 | 17 | 026 061 |
| autre (bout-en-bout, S1 riche, aide) | 3 | 16 | 037 054 056 |
| templates | 1 | 13 | 029 |
| index-classpath | 4+1 mixte | 16 | 004 010 031 038 032* |
| concurrence-annulation | 3 | 11 | 012 046 048 |
| folding | 2 | 10 | 011 052 |
| shims-art | 1 | 9 | 045 — **déjà couvert** (`:ecj-art` livré, 36 tests) |
| hierarchies | 1 | 8 | 018 |
| imports | 1 | 8 | 022 |
| parse | 1 | 1 | 040 |
| **Total** | **65** | **450** | (449 tests + 1 service) |

Bornes chiffrées notables fixées par les fiches (gates de parité par étage,
reprises en §4) : « 1 cible exactement » en navigation (049/060), « 1 hint » /
« 2 hints exactement » en inlay (003/023), « 120 items affichés max » (043),
« publication attendue sous 15 s » (056), « code d'erreur exact -32600 » en
protocole (059), « 0 erreur » du compilateur sur corpus (013),
« ≥ 5 signatures ; activeParameter = 0 » (023).

---

## 3. Méthodologie d'estimation

### 3.1 Modèle socle + marginal (bottom-up) [estimé]

Chaque étage est estimé comme :

> **coût(étage) = socle + n × coût marginal par comportement**

- le **socle** couvre ce qui n'est pas proportionnel au nombre de
  comportements : l'infrastructure propre à l'étage (pilote ecj, pipeline,
  providers, moteur de phases…), la mise en place du banc de qualité
  correspondant (ADR 0016) et le portage des fixtures/corpus des fiches ;
- le **coût marginal** couvre ce qui est proportionnel : satisfaire une fiche
  supplémentaire une fois l'infrastructure en place (implémentation + test de
  parité + KDoc détaillé obligatoire + passage du banc).

Les bornes de coût marginal par étage (h/comportement) et les socles (j-h)
sont des **postulats d'abaque** construits ainsi :

1. **nature du domaine** — délégation directe à ecj patché (faible) ;
   service avec état/caches (moyen) ; design D8–D12 intégré en naissance,
   multi-passes, contraintes de latence (élevé) ;
2. **densité des bornes chiffrées** des fiches de l'étage (une borne chiffrée
   = un critère mesurable à satisfaire, donc du travail de banc) ;
3. **recoupement avec l'ancienne estimation par vague** (§6) : les couches de
   design de l'ancien plan (vagues 2–4, 55–90 j-h sur substrat existant) sont
   retrouvées comme sous-ensembles des étages N2/N4/N5/N6/N8 — leur solde
   (« base reconstruite ») est la partie réellement nouvelle de la
   réécriture.

Aucun coût marginal ne dépasse 5 h/comportement (plancher de l'abaque :
1 h pour un invariant structurel simple), aucun socle ne dépasse 20 j-h.

### 3.2 Recoupement volumétrie (top-down) [estimé]

Le moteur vendored mesure **53 fichiers / 25 839 l. main Java + 16 229 l. de
tests** [mesuré, étude §2]. Le moteur neuf — Kotlin, designs D8–D12 intégrés
en naissance, KDoc détaillé obligatoire — est estimé à **18–28 k l. de
production + 15–20 k l. de tests** [estimé : Kotlin est plus dense, les
designs ajoutent du code, le cahier remplace une partie des tests vendored
par des tests de parité]. À une productivité soutenue de **150–300 l./j-h de
code qualifié** (testé, documenté, revu — borne basse prudente pour du
clean-room sous contraintes ART ; postulat industriel), le recoupement donne
**110–320 j-h**. La fourchette retenue (§5) est l'intersection pondérée avec
le bottom-up : elle est dominée par le bottom-up, jugé plus fin (granularité
fiche).

### 3.3 Ancrages mesurés et limites

- **[mesuré]** La vague 1c (`:ecj-art`) : réécriture complète d'un module
  borné (9 comportements shims de la fiche 045 + infrastructure scanner/
  patcheur/liste versionnée ; 1 521 l. Kotlin, 36 tests dont intégration au
  vrai jar) en **une session de travail continue**. Ancrage **relatif
  seulement** : la session n'est pas convertible en j-h honnêtement (rythme
  d'agent ≠ jour-homme humain) — elle prouve que la méthode « fiche →
  implémentation → tests de parité → banc » tient sur un module réel.
- **[mesuré, non transposable]** La vague 1b : 45 fichiers repris et 122
  tests verts — effort **mécanique de reprise**, sans valeur pour calibrer la
  réécriture (le substrat existait déjà).
- **[mesuré]** Chiffres de latence du spike (V3 ecj : parse p50 0,6–1,5 ms ;
  compile résolue p50 5,7–10,9 ms ; `02-spike-mesures.md` §8) : ils bornent
  la **faisabilité** des gates de latence, pas l'effort.
- **[non vérifié]** La productivité réelle de l'équipe qui exécutera : le
  premier recalage (fin N1, §8) remplacera les postulats d'abaque par une
  vélocité mesurée.

---

## 4. Étages N1–N8 + T

Chaque étage : contenus (fiches allouées, exactes), critère de sortie
(parité cahier + bancs ADR 0016 + latences des fiches), estimation
optimiste / médiane / pessimiste. Allocation vérifiée exhaustive : 65 fiches,
aucune double, aucune manquante (contrôle par script). Séquence :
**N1 → (N2, N3, N7) → (N4, N5, N6) → N8** ; T traverse tout.

### N1 — Noyau compilateur & environnement · 25 fiches, 124 comportements

Pilotage d'ecj patché (`:ecj-art`) : environnement de compilation, routeur de
classpaths (appareil/JRT/zips bruts/R.jar — fiche 037), ordonnanceur et
annulation (012/046/048), diagnostics de base et repli syntaxe instantané
(001/013/019/028/039/042), caches parse/locate/présence (006/014/015/024/025/
034/041/047), formes d'arbre (040), lecture zip/classpath (004/010/031/032/
038). **Inclut** : reprise contrôlée de `:testing` (4 f. — reportée ici depuis
la vague 1b, elle éprouve le noyau) et banc empreinte/latence en CI.
Fiches : 001 004 006 010 012 013 014 015 019 024 025 028 031 032 033 034
037 038 039 040 041 042 046 047 048 — 25 fiches, dont l'aide 037.
Sortie : comportements verts ; « 0 erreur » du compilateur sur corpus (013) ;
« ≥ 1 ERROR »/« 0 diagnostic » du repli syntaxe (028) ; latences de frappe
tenues (033) ; dex/empreinte sous budget ; `:testing` repris vert.
Estimation : **30–54 j-h** (médiane ≈ 40) — socle 12–18 j-h + 124 × 1–2 h.

### N2 — Complétion & classement (D8 en naissance) · 9 fiches, 66 comportements

Pipeline de complétion : intelligence de contexte (007), régressions
historiques (008/035), popup spontané (036), sous-paquetages et plafond
« 120 items » (043/044), mapping des kinds LSP (050), intégration imports
(053), bindings hover/définitions/complétion (020 mixte). **Design intégré en
naissance** (ancienne vague 2) : chaîne de weighers + `WeighingComparable` +
gel + liftShorter + trim honnête ; split RPC ; banc qualité
recall/top-1/MRR sur corpus fixe versionné (ADR 0012/0016) ; statistics
locales opt-in.
Sortie : comportements verts ; banc qualité : recall ≥ baseline, top-1/MRR
+5 pts vs baseline, p95 ≤ baseline, zéro « saut » de liste ; latence
benchmarks (033) tenue.
Estimation : **32–50 j-h** (médiane ≈ 40) — socle 8–12 j-h + 66 × 2,5–4 h.

### N3 — Hover, inlay & signatures · 5 fiches, 48 comportements

Hover javadoc/définition (021, 020), hints inlay : types var (030), lambdas
chaînées (002), comptage exact « 1 hint »/« 2 hints » (003), signatures +
`activeParameter` (023).
Sortie : comportements verts ; bornes exactes de comptage (003/023) ;
rendu javadoc fidèle (021).
Estimation : **14–23 j-h** (médiane ≈ 18) — socle 4–6 j-h + 48 × 1,5–2,5 h.

### N4 — Navigation & usages · 6 fiches, 52 comportements

Définitions et repli binaire (005), décompilation (009), audit navigation
(049), façade (060), typeDefinition (062), chaînes de sélection (027 mixte).
**Design intégré** (ancienne vague 3, moitié usages) : QueryExecutor sur
CIDX (`references`/`documentHighlight` exhaustifs), `Files.walk` retiré du
chemin chaud.
Sortie : comportements verts ; « 1 cible exactement » (049/060) ; rappel
usages = oracle ; p95 usages < 100 ms.
Estimation : **21–31 j-h** (médiane ≈ 26) — socle 6–9 j-h + 52 × 2–3 h.

### N5 — Rename v2 · 2 fiches, 17 comportements

Moteur de rename (026) et honnêteté des collisions (061). **Design intégré**
(ancienne vague 4, moitié rename) : rename par phases — collisions typées,
réparations, preview.
Sortie : comportements verts ; 0 conflit manqué (oracle) ; `WorkspaceEdit`
exacts (cas dorés, banc ADR 0016).
Estimation : **12–20 j-h** (médiane ≈ 16) — socle 5–8 j-h + 17 × 3–5 h.

### N6 — CodeAction v2 (ModCommand) & correcteurs · 4 fiches, 31 comportements

Service de code actions (063), création de méthode (064), familles de
problèmes (065), déclassement d'import databinding (051). **Design intégré**
(ancienne vague 3, moitié quick-fixes) : ModCommand déclaratif sérialisable,
migration des fixes par cas dorés.
Sortie : comportements verts ; parité quick-fixes 100 % (cas dorés) ;
« 1 édition par action ; insertion pure » (063) ; « 0 UndefinedMethod après
correction » (064).
Estimation : **19–30 j-h** (médiane ≈ 24) — socle 6–10 j-h + 31 × 3–4,5 h.

### N7 — Confort éditeur · 8 fiches, 59 comportements

Formatage et plages (016/017), confort S2 (057), organisation des imports
(022), pliage (011/052), templates (029), hiérarchies (018).
Sortie : comportements verts ; fidélité du formateur sur plages (016) ;
paires de pliage exactes (052).
Estimation : **17–28 j-h** (médiane ≈ 22) — socle 4–7 j-h + 59 × 1,5–2,5 h.

### N8 — Cycle de vie LSP, protocole riche & intégration · 5 fiches, 44 comportements

Cycle de vie du serveur (055), protocole S2b — codes d'erreur exacts
« -32600 » (058), hygiène de cycle de vie (059), bout-en-bout L4 (054),
S1 riche — « publication sous 15 s » (056). **Design intégré** (ancienne
vague 4, moitié protocole) : époque copy-on-write (0 réponse périmée) ;
capability `x-codeide.completionRanking`. **Interface identifiée** : le
branchement de substitution dans CodeIDE (dépôt tiers) est coordonné côté
propriétaire — effort estimé dans ce dépôt, hors du présent chiffrage.
Sortie : comportements verts ; publication < 15 s (056) ; 0 réponse périmée ;
codes d'erreur exacts (058/059).
Estimation : **14–25 j-h** (médiane ≈ 19) — socle 5–9 j-h + 44 × 1,5–2,5 h.

### T — Transverse qualité · traverse N1–N8

Banc de parité croisé ADR 0016 : le même corpus rejoué contre le vendored
(oracle en boîte noire, exécuté dans `codeide-lsp`) et le moteur neuf ;
baselines JSON signées, CI bloquante ; corpus fixe versionné. Deux points de
recalage d'estimation (§8).
Estimation : **6–10 j-h** (médiane ≈ 8) — forfait.

*(La fiche 045 — shims-art, 9 comportements — est déjà couverte par `:ecj-art`
livré en vague 1c : coût restant nul ; le re-scan/la révision de liste à
chaque montée d'ecj relèvent de la vague 6, exclue.)*

---

## 5. Synthèse et fourchette totale [estimé]

| Étage | Contenu dominant | Comport. | Optimiste | Médiane | Pessimiste |
|---|---|---:|---:|---:|---:|
| N1 | Noyau compilateur & environnement (+ `:testing`, bancs) | 124 | 30 | 40 | 54 |
| N2 | Complétion & classement (weighers D8) | 66 | 32 | 40 | 50 |
| N3 | Hover / inlay / signatures | 48 | 14 | 18 | 23 |
| N4 | Navigation & usages (QueryExecutor CIDX) | 52 | 21 | 26 | 31 |
| N5 | Rename v2 (phases, collisions) | 17 | 12 | 16 | 20 |
| N6 | CodeAction v2 (ModCommand) & correcteurs | 31 | 19 | 24 | 30 |
| N7 | Confort éditeur (formatage, imports, pliage, templates, hiérarchies) | 59 | 17 | 22 | 28 |
| N8 | Cycle de vie LSP & protocole riche | 44 | 14 | 19 | 25 |
| T | Transverse : banc de parité croisé, recalages | — | 6 | 8 | 10 |
| **Total restant** | | **441** | **165** | **213** | **271** |

**Fourchette retenue : 160–270 j-h, médiane 213 j-h** (bornes arrondies à la
dizaine — les faux entiers seraient un leurre de précision ; intersection
du bottom-up §3.1 [165–271] et du recoupement volumétrie §3.2 [110–320],
dominée par le bottom-up).

**Ce que ce chiffre dit, et ne dit pas** :
- 213 j-h ≈ **10 mois calendaires pour un développeur** (≈ 21 j/mois), ou
  **≈ 5 mois pour deux** — N3/N7 puis N5/N6 sont parallélisables après N1 ;
- c'est le coût de la **parité complète du cahier** (449 comportements) avec
  les designs D8–D12 intégrés en naissance, KDoc détaillé et bancs bloquants
  compris — pas celui d'un prototype ;
- la fourchette suppose l'exécution **par vagues successives bornées** (§8) ;
  elle ne suppose NI un big-bang, NI la présence simultanée d'une équipe
  nombreuse ;
- [non vérifié] la vélocité réelle de l'équipe exécutante : le premier
  recalage (fin N1) remplace les postulats d'abaque par une mesure.

Recoupement d'exactitude des totaux : N1 124 + N2 66 + N3 48 + N4 52 + N5 17
+ N6 31 + N7 59 + N8 44 = **441 comportements restants** ; + 9 (fiche 045,
déjà couverts par `:ecj-art`) = 450 = 449 tests + 1 service [mesuré, §2].

## 6. Comparaison avec l'estimation caduque (75–130 j-h)

L'ancienne fourchette « vagues 0–4 : 75–130 j-h » (`00-synthese.md` §4)
découpait ainsi :

| Ancien découpage | Ancienne estimation | Devenir dans ce plan |
|---|---:|---|
| Vague 0 — amorçage | 5–10 j-h | **livrée** (9411b06) — hors périmètre |
| Vague 1 — reprise contrôlée + cahier + patcheur | 15–30 j-h | **livrée** (1eadc96/04b51be/d016030) — hors périmètre |
| Vague 2 — classement de complétion | 15–25 j-h | retrouvée **dans N2** (partie design) |
| Vague 3 — CodeAction v2 + usages indexés | 20–35 j-h | retrouvée **dans N6 + N4** (parties design) |
| Vague 4 — rename v2 + époque + protocole | 20–30 j-h | retrouvée **dans N5 + N8** (parties design) |
| **Total vagues 0–4** | **75–130 j-h** | — |

La ré-estimation n'est donc **pas une inflation mystérieuse** : elle a une
cause structurelle unique. L'ancien plan greffait les couches de design
(vagues 2–4, 55–90 j-h) **sur un moteur qui existait déjà** — le vendored
repris comme substrat. L'option 2 supprime le substrat : chaque étage doit
**aussi reconstruire la base** que l'ancien plan réutilisait (pipeline de
complétion sous les weighers, services de navigation sous le QueryExecutor,
moteur de rename sous les phases, serveur LSP sous l'époque). Solde
structurel : base reconstruite ≈ 110–180 j-h + design ≈ 55–90 j-h (anciens
chiffres, toujours valables comme spécification) ≈ **165–270 j-h** — la
fourchette du §5 se retrouve par construction. Le poste « base » est
précisément ce que la réécriture intégrale achète : une base **neuve, Kotlin,
saine de provenance** (risque n° 1 de la stratégie éliminé par construction,
ADR 0004 amendement).

## 7. Exclusions et postulats

**Exclus de la fourchette** (à chiffrer séparément si décision) :
1. **Vague 5 — parsing incrémental `java-syntax`** (ancienne estimation
   15–25 j-h) : conditionnée aux conditions artefacts JetBrains (ADR 0017) —
   pas de version tant que P-JB n'est pas tranchée ;
2. **Vague 6 — montée JDT/ecj ≥ 3.43** (ancienne 10–15 j-h) : événementielle,
   procédure ADR 0018 (re-scan, révision de liste, appareil) ;
3. **Moteurs Kotlin/XML et hôte tri-langue** : hors périmètre ADR 0002 ;
4. **Intégration côté CodeIDE** (branchement de substitution, dépôt tiers) :
   interface identifiée en N8, effort à estimer dans ce dépôt ;
5. **Maintenance du vendored pendant la transition** : côté `codeide-lsp`
   (lecture seule pour ce dépôt) ;
6. **Actions propriétaire** : P1 (LICENSE tooling — contournée par la
   réécriture locale du protocole, vague 1b), conditions artefacts JetBrains
   (P-JB).

**Postulats structurants** (si l'un tombe, recalage) :
- le cahier couvre le contrat : R6 de l'ADR 0001 tient le risque « pertes de
  comportements non testés » par le banc croisé, mais un durcissement
  implicite du vendored découvert en cours de route s'ajouterait au coût ;
- ecj 3.40.0 patché reste la base compilateur (pas de montée en cours de
  programme) ;
- `:index`/`:model` repris (vague 1b) restent stables ; l'effort N4 suppose
  le CIDX opérationnel (122 tests verts aujourd'hui) ;
- les latences cibles des fiches sont atteignables sur ART avec ecj (les
  mesures du spike V3 le suggèrent sur JVM — `02-spike-mesures.md` §8 — mais
  **la mesure sur appareil reste à faire** [non vérifié], gabarit
  `spike/appareil/`).

## 8. Gouvernance et points de recalage (risque R4)

Le risque R4 (« sous-estimation ») se pilote par bornage, pas par promesse :

1. **Un étage = un contrat de sortie mesurable** (comportements verts + bancs
   ADR 0016 + latences des fiches + empreinte dex) — aucun étage suivant
   n'est lancé sans que le précédent soit au vert ;
2. **Recalage 1 — fin N1** : la vélocité réelle mesurée sur le premier étage
   (le plus gros socle) remplace les postulats d'abaque ; la fourchette
   N2–N8 est re-projetée et re-soumise au propriétaire —
   **exécuté le 29/10/2026 : `RECALAGE-1.md`** (soumis) ;
3. **Recalage 2 — fin N2** : l'étage le plus riche en design étant passé, la
   confiance sur N3–N8 est re-évaluée — **exécuté le 29/10/2026 :
   `RECALAGE-2.md`** (soumis ; aucun signal défavorable, facteur d'exécution
   N2 inférieur à N1, projection N3–N8+T : 5–8 séances effectives) ;
4. **Repli permanent** : le vendored demeure en production dans
   `codeide-lsp` jusqu'à la bascule (critère : parité du cahier **sur
   appareil**) — une pause ou un arrêt du programme ne dégrade rien ;
5. **Bascule uniquement à parité** : 0 comportement perdu contre l'oracle
   (banc croisé ADR 0016), latences §4 tenues sur appareil.

## 9. Risques d'estimation spécifiques

| Risque | Effet sur la fourchette | Mitigation |
|---|---|---|
| Comportements non testés du vendored (durcissement implicite) découverts au banc croisé | +0–40 j-h | banc croisé dès N1 (T) ; historique git de l'ancien dépôt consulté (lecture seule) pour les incidents documentés |
| Latences ART non tenues avec ecj (mesure appareil absente [non vérifié]) | ré-architecture partielle du repli syntaxe : +10–30 j-h | gabarit appareil fourni (`spike/appareil/`) — mesure propriétaire AVANT N1 |
| Tests conditionnels des fiches (hypothèses android.jar/JDK/model.json) traduits en gates trop faibles | sous-estimation douce | chaque fiche conditionnelle requalifiée en gate explicite à l'implémentation |
| Régression de santé `:index`/`:model` (base reprise) | +5–15 j-h | 122 tests repris verts = filet existant ; CI déjà en place |
| Parallélisation effective insuffisante (1 dev au lieu de 2) | délai ×2, coût stable | séquencement N1 → (N2,N3,N7) → (N4,N5,N6) → N8 documenté |

## 10. Décisions du propriétaire — tranchées le 28/10/2026

*(Chaque point rappelle la question posée, puis porte la tranche.)*

1. *Approuver la fourchette 160–270 j-h (médiane 213) comme base de
   planification du programme de réécriture — ou trancher un sous-ensemble
   (p. ex. « N1 + banc croisé d'abord, décision de suite après recalage 1 ») ?*

   **TRANCHÉ : approbation aux deux niveaux.** La fourchette est approuvée
   comme **enveloppe de planification** du programme (horizon §5 : ≈ 10 mois
   solo / ≈ 5 mois à deux) ; l'**engagement contractuel est borné à N1 + T**
   (banc de parité croisé). La décision de suite est re-soumise au
   propriétaire au **recalage 1 (fin N1, §8.2)**, quand la vélocité mesurée
   remplace les postulats d'abaque. Planifier sur l'enveloppe, s'engager
   étage par étage : les deux options offertes ne s'excluaient pas.

2. *Lancer la mesure sur appareil (gabarit `spike/appareil/`) AVANT N1 —
   c'est la seule donnée [non vérifiée] qui puisse bouger la structure du
   plan (§7 postulat, §9 risque 2) ?*

   **TRANCHÉ : OUI — porte d'entrée bloquante de N1.** Le propriétaire
   exécute `spike/appareil/banc-appareil.sh` (Termux ou AndroidIDE) et
   retourne `spike/appareil/GABARIT-RESULTATS.md` rempli ; les valeurs sont
   consignées telles quelles (R5 — aucune extrapolation automatique, §6 du
   gabarit). **Aucune implémentation de N1 ne démarre avant le retour du
   gabarit.** Si les latences ART ne sont pas tenues avec ecj, la
   ré-architecture partielle du repli syntaxe (§9 risque 2, +10–30 j-h)
   est tranchée AVANT d'écrire le noyau, pas après.

3. *Confirmer la séquence N1 → (N2, N3, N7) → (N4, N5, N6) → N8, ou en
   privilégier une autre (p. ex. maximiser tôt la valeur perceptible :
   N1 → N3 → N7 → N2…) ?*

   **TRANCHÉ : séquence confirmée telle quelle** — N1 → (N2, N3, N7) →
   (N4, N5, N6) → N8, T traversant. Motif : N2 (classement D8 — capacité
   phare et plus grosse dépendance au noyau) passe immédiatement après N1,
   pendant que le noyau est frais ; l'alternative « valeur perceptible
   tôt » a été examinée et écartée : elle repousse l'étage le plus riche en
   design et exposerait les lacunes du noyau tardivement (la complétion
   est le révélateur le plus exigeant du noyau). En exécution solo, l'ordre
   linéaire N1 → N2 → N3 → N7 → N4 → N5 → N6 → N8 parcourt les mêmes
   groupes.

4. *Arbitrer le point ouvert de la fiche 054 (écart moteur assumé :
   1 référence inter-fichiers seulement — consigné cahier §5) : le moteur
   neuf reproduit-il l'écart (parité stricte) ou le corrige-t-il ?*

   **TRANCHÉ : parité stricte pendant le programme.** Le moteur neuf
   **reproduit** l'écart — `references` sur la déclaration de `a()` rend
   exactement 1 référence (la déclaration) ; l'appel inter-fichiers de
   `U.java` n'est pas rendu ; la borne de la fiche (« taille attendue = 1
   exactement », vérifiée en N8 où la fiche est allouée) reste la gate.
   Motifs : (a) le banc croisé ADR 0016 rejoue le même corpus contre le
   vendored (oracle en boîte noire) — une divergence volontaire y serait
   un signal rouge permanent, indistinguable d'une régression réelle ;
   (b) le critère de bascule est « 0 comportement perdu contre l'oracle »
   (§8.5). La **correction** (rendre l'appel inter-fichiers —
   re-qualification de la fiche, borne « 2 exactement ») est consignée
   comme **amélioration candidate post-bascule** : décision dédiée à ce
   moment ; l'architecture N4 (QueryExecutor sur CIDX, références
   exhaustives) la rend alors structurellement peu coûteuse.

---
*Références : ADR 0001 (décision, plan de migration, R4/R5/R6) · ADR 0002
(périmètre) · ADR 0004 (stratégie GO PARTIEL, amendement option 2) · ADR
0012/0013/0014 (classement, code actions, rename) · ADR 0016 (bancs de
qualité) · ADR 0018 (liste de shims versionnée) · `00-synthese.md` §4 (plan
par vagues caduc) · cahier `docs/cahier-specifications/` (contrat) ·
`02-spike-mesures.md` (chiffres V3).*
