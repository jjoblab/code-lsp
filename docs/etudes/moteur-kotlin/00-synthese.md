# 00 — Synthèse et décision (Phase 4)

> Étude « moteur Kotlin » pour CodeIDE sur Android/ART — synthèse finale
> de la mission (§9 du prompt). Documents de base :
> `00b-inventaire-existant.md` (Phase 0) ·
> `01-architectures-analyse-kotlin.md` (Phase 1, K1–K12) ·
> `02-spike-mesures.md` (Phase 2, S0–S3) · cahier
> `docs/cahier-specifications-kotlin/` (53 fiches, 469 comportements) ·
> manifeste `docs/PROVENANCE-REPRISE-KOTLIN.md` · ADR `0100–0121`
> (**0100 et 0103 Acceptés** le 2026-10-30 — les autres restent Proposées
> jusqu'à leur étage, R7).
> **Décision du propriétaire reçue le 2026-10-30** (« commencer les
> étapes », branche `etude/moteur-kotlin` seule) : l'étude est CLOSE,
> l'EXÉCUTION est ouverte — étage 0 (amorçage) engagé, rapport dans
> `03-execution-etage0.md`.

## 1. Recommandation unique

### Gouvernance : **voie C — hybride (noyau réécrit, modules sains repris)**, décision ADR 0100 au propriétaire

### Technique : **voie (i) — parseur officiel + modèle de symboles conservateur**, avec (iii) en extension différée (profondeur K2 à la demande)

**Raisonnement (cinq temps, chacun prouvé dans les rapports) :**

1. **Le moteur actuel n'a pas le problème qui a justifié la réécriture
   Java.** Le moteur Java vendored était frappé d'une contestation de
   paternité (ADR 0110/0001 Java) ; le moteur Kotlin est du code propre
   jjoblab sur une grammaire Apache-2.0 correctement attribuée —
   aucune exclusion R9.3 ne le frappe. La réécriture intégrale (voie A,
   170–290 j-h) n'achèterait ici que de la pureté de méthode.
2. **La vraie valeur d'une reprise sur « des bases plus solides » est
   architecturale, et elle est concentrée dans le noyau.** Ce que le
   moteur actuel n'a pas et ne peut pas avoir seul : les bancs de
   qualité (fidélité mesurée face à K2, recall/top-1/MRR — il n'a que
   des latences), la chaîne de classement (le levier n° 1 de qualité
   perçue, ADR 0012 Java), la CI dex/empreinte, les designs de
   concurrence/mémoire (ADR 0010/0011 Java), le renommage sûr. Ces
   bases s'installent en RÉÉCRIVANT le cœur (parse/résolution/session/
   diagnostics/complétion) depuis le **cahier** (469 comportements
   extraits des tests) avec les designs intégrés en naissance.
3. **Les modules différenciants sont sains, bornés et éprouvés — les
   reprendre est licite et rapide.** `synthetics/` (667 l., zéro faux
   positif E2E, capacité que l'amont n'a pas), `preview/` (522 l.,
   16 tests), `classpath/` (664 l., différentiel 0 écart sur corpus
   réels), `format/`/`plis/`/`documentation/`/`symboles/` (772 l.) :
   ~2 600 lignes de valeur immédiate, reprise contrôlée après R9.4
   (registre préalable — manifeste déjà prêt).
4. **La grammaire ne se « reprend » jamais de l'ancien dépôt : elle se
   ré-obtient de l'amont** (R9.2) — fait, pas théorie : le spike
   l'a ré-obtenue au tag v2.3.0 et elle compile telle quelle avec la
   chaîne du dépôt [mesuré].
5. **La stratégie sémantique est tranchée par la mesure, cette fois.**
   L'Analysis API/K2 embarqué est disqualifié par les chiffres
   (53,6 Mo / 64 507 méthodes / ~2 170 motifs sensibles ART / 3 503 ms
   d'amorçage [mesurés] — et l'Analysis API standalone n'est même pas
   publiée [mesuré]) ; le parseur officiel seul tient tout le budget
   (0 motif, 1,8 ms p50 [mesuré]). La « preuve ou silence » (zéro faux
   positif — critère §4 du prompt) reste le méta-contrat ; la
   profondeur K2 devient une tâche explicite différée (3,5 s annoncées)
   si le besoin est démontré.

**Ce que la recommandation n'achète pas (assumé)** : la fidélité
compilateur par frappe (impossible dans le budget — preuve au spike) ;
la complétion DSL Gradle dans les blocs (hors modèle tant que T2) ;
les jetons sémantiques (non fiables au parseur — dette documentée) ;
le formateur complet (borné par prudence — ADR 0202/0115).

## 2. Tableau comparatif des voies (chiffres étiquetés)

| Voie | Coût [estimé, §5 inventaire] | Délai de valeur | Empreinte/latences (toutes voies, [mesuré]) | Provenance | Verdict |
|---|---|---|---|---|---|
| **A — Réécriture intégrale** | 170–290 j-h | Étages (miroir Java : ~10 mois solo) | idem C (mêmes piles) | propre par construction | Sous-option de C si le propriétaire préfère la pureté de méthode |
| **B — Reprise contrôlée intégrale** | 90–150 j-h | Immédiat puis consolidation | idem | registre à tenir | L'architecture « correctif par correctif » migre — le gain architectural reste à construire |
| **C — Hybride (recommandée)** | **110–190 j-h** | Modules différenciants immédiats ; noyau par étages | moteur 0,86 Mo / 0 motif ART / reparse 3,4 ms (500 l.) / parse 1,8 ms p50 | deux régimes, registre fin (manifeste prêt) | **GO proposé** (ADR 0100) |
| **D — Statu quo amélioré** | 30–60 j-h (étape B du rapport faux positifs) | Immédiat | inchangé | inchangé | **Repli permanent** (le moteur actuel sert la production) |

Voies techniques : (i) retenue (S2+S0 [mesurés]) ; (ii) disqualifiée
(S1 [mesuré]) ; (iii) extension différée (coût S1 assumé et annoncé) ;
(iv) non mesurée [non vérifié] — à ne rouvrir que pour le tooling T2.

## 3. Les 10 risques principaux et leurs mitigations

| # | Risque | Gravité | Mitigation (renvoi) |
|---|---|---|---|
| 1 | **Frontière C mal spécifiée** (module repris qui dépend du noyau réécrit) | Haute | Cartographie de dépendances AVANT copie (inventaire §3) + registre préalable (R9.2) |
| 2 | **Sous-estimation du noyau** (résolution spécifiée = du travail neuf) | Haute | Étages bornés à une parité mesurable ; recalages (miroir §8 plan Java) |
| 3 | **Perte de durcissement implicite** (24 familles Reproduction*) | Haute | Toutes fichées au cahier (469 comportements) + banc croisé oracle 1.22.1 |
| 4 | **Conditions des artefacts binaires intellij-dependencies non confirmées** | Haute (publication) | Action propriétaire tracée (ADR 0120, miroir AL1 Java) |
| 5 | **Filiation documentaire GPL-3.0 des décisions anciennes** (R9.4) | Moyenne | Non consultée (R6.2) ; audit de similarité recommandé au propriétaire, rôle distinct des implémenteurs |
| 6 | **Régression de qualité perçue pendant la migration** | Haute | Drapeau pack ancien/neuf + 2 versions de rodage + CI bloquante sur les bancs (ADR 0118/0119) |
| 7 | **GC ART incompatible soft-références** | Moyenne | Bornes dures en octets + mesure appareil (ADR 0117) |
| 8 | **Montée de grammaire douloureuse** (2×/an, bootstrap) | Moyenne | Procédure bornée + gates (parité, digeste, bancs — ADR 0121) ; ré-obtention amont démontrée [mesuré] |
| 9 | **Interblocage après transposition des verrous par périmètre** | Moyenne | Ordre global documenté + tests de chaos CI (ADR 0117) |
| 10 | **Extension `:index` refusée** (Q-K1) | Basse | Repli autonome : index dédié éprouvé (ADR 0108) |

## 4. Plan par étages (sur le modèle `PLAN-REECRITURE.md`)

> Fourchettes en jours-homme **[estimé]** — méthode : miroir du plan
> Java (socle + marginal par comportement, bornes d'abaque), ajusté :
> socle partagé (`:index`/`:model`/CI) **déjà là et vert** ; volumétrie
> cahier 469 comportements (+8 % vs Java 449) ; modules différenciants
> repris (voie C). Chaque étage : périmètre, critères de sortie
> mesurables, dépendances. **Cahier en main** (voie C) ; **mesure sur
> appareil = porte d'entrée du premier étage** (décision Java §10.2 —
> transposée).

| Étage | Périmètre | Critères de sortie | Dépend de | Effort [estimé] |
|---|---|---|---|---|
| **0 — Amorçage** | Module `:kotlin` squelette + descripteur de pack + grammaire vendorée (ré-obtention amont) + suite de parité + CI (dex/scan ART/seuils) + extension `verifier-provenance.sh` + reprises C contrôlées (registre préalable) + notices | Build vert ; parité grammaire verte ; zéro fichier hors registre ; 0 motif ART | ADR 0100 accepté | **8–14 j-h** |
| **1 — Noyau parse/session/classpath** | Façade `Kt*`, session documents, scan projet paresseux, lecteur classpath + décodeur `@Metadata` + index appelables (reprend le contrat ADR 0211/0213), synthétiques repris | Fiches parse/session/index/synthétiques vertes ; latences S0 tenues (reparse < 50 ms JVM à 3 000 l.) ; **gabarit appareil rempli (porte d'entrée)** | V0 | **30–52 j-h** |
| **2 — Résolution + diagnostics** | Résolution spécifiée (tableau règles×spec), catalogue `kt.*` conservateur, garde par déclaration, journal explain | 204 fiches diagnostics vertes ; zéro faux positif corpus quatre mondes ; banc fidélité baseline signée | V1 | **38–60 j-h** |
| **3 — Complétion + classement** | Pipeline complétion, contextuelle v1, **chaîne weighers en naissance**, banc commun Java (recall/top-1/MRR) | 44 fiches vertes ; banc : recall ≥ baseline, top-1/MRR +5 pts, p95 ≤ baseline | V2 | **30–48 j-h** |
| **4 — Navigation + rename** | Définition/type/impl/super, occurrences, rename borné puis phases | 38 fiches vertes ; rappel usages = oracle ; 0 conflit manqué | V3 | **24–38 j-h** |
| **5 — Confort éditeur** | Formatage borné, pliage, imports, membres manquants, previews (reprise), actions ModCommand-like | Fiches confort vertes ; cas dorés WorkspaceEdit | V4 | **20–32 j-h** |
| **6 — Serveur LSP + protocole riche** | Serveur LSP4J du module (miroir N8), capabilities paritaires, époque, drapeau pack | Matrice reproduite ; codes exacts ; 0 réponse périmée | V5 | **16–26 j-h** |
| **T — Transverse qualité** | Bancs (fidélité K2 oracle, complétion, empreinte), corpus versionnés, recalages | CI bloquante verte ; 2 recalages | traverse | **8–12 j-h** |

**Total voie C [estimé] : 174–282 j-h** (médiane ≈ 225) — cohérent avec
la fourchette 110–190 de l'inventaire §5 (qui ne comptait pas T ni
l'étage LSP complets) ; à affiner cahier en main à la fin de l'étage 1
(méthode des recalages Java). En voie A (tout réécrit) : +40–70 j-h.

**Vague 0 (amorçage) — détaillée :**
1. module `:kotlin` (squelette Gradle, conventions du dépôt, toolchain) ;
2. grammaire vendorée par ré-obtention amont + suite de parité + VENDOR/NOTICE ;
3. extension de `scripts/verifier-provenance.sh` au périmètre `kotlin/`
   (registre Kotlin comme manifeste de contrôle — proposition, le
   script n'est pas modifié pendant l'étude, R3) ;
4. reprises contrôlées des modules C (inscription PRÉALABLE au
   manifeste, contrôle R9.4 fichier par fichier) ;
5. CI : build + scan ART + dexage + seuils + bancs empreinte/latence
   (extension des workflows Java) ;
6. actions propriétaire : conditions artefacts intellij-dependencies
   (P-K2) ; lecture du second manifeste ;
7. gabarit de mesure appareil (déclinaison de `spike/appareil/` Java).

## 5. Ce qui reste à mesurer sur appareil (rien n'est prétendu sans retour réel)

1. **Latences S0/S2 sur ART** (reparse, diagnostics, complétion) — le
   facteur 3–5× reste [estimé] tant que le gabarit n'est pas rempli.
2. **GC ART** : éviction des soft-références, coût des
   `ReferenceQueue` (ADR 0117).
3. **Dexage réel** (d8) du moteur + dépendances : méthodes dex et
   taille APK (la CI le fera — le bac à sable n'a pas d8, E-K2).
4. **Démarrage à froid du pack** sur appareil (chargement grammaire +
   index) ; thermique et répétition (3 passes min/médiane/max).
5. **Corpus réels appareil** : les corpus E2E quatre mondes (17/27
   jars) sur stockage FUSE réel.

## 6. Décision demandée au propriétaire (STOP — §8 du prompt)

> **Décision REÇUE le 2026-10-30** dans le canal de mission : « Je t'autorise à
> commencer les étapes, tu dois travailler uniquement sur ta branche » — les
> étages du plan §4 sont ouverts sur la branche `etude/moteur-kotlin`
> (ADR 0100 : voie C acceptée ; ADR 0103 : voie (i) acceptée). Le rapport
> d'exécution de l'étage 0 vit dans
> `03-execution-etage0.md` ; les points 4 ci-dessous restent des actions
> propriétaire ouvertes (licences P-K2, arbitrage Q-K1–Q-K6, lecture du
> second manifeste).

1. **ADR 0100 — voie de gouvernance** : A / B / C (proposée) / D
   (repli). Les quatre sont documentées à armes égales ; aucun fichier
   n'a été copié (manifeste vierge de reprise — seul le vendoring amont
   du SPIKE est inscrit, à des fins de mesure).
2. **ADR 0103 — stratégie sémantique** : (i) proposée (+ (iii) en
   extension différée) ; (ii) archivée avec ses preuves.
3. **Mesure sur appareil comme porte d'entrée de l'étage 1**
   (transposition de la décision Java §10.2) — le gabarit sera produit
   à la vague 0.
4. **Actions licences** : conditions des artefacts binaires
   intellij-dependencies (P-K2) ; arbitrage des demandes d'interface
   Q-K1–Q-K6 (`DEMANDES-INTERFACE.md`).
5. À réception : les **prompts d'exécution étage par étage** seront
   produits séparément (§8), en commençant par la vague 0.
