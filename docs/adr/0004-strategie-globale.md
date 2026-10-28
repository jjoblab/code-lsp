# ADR 0004 — Stratégie globale : embarquer / s'inspirer / hybride / statu quo

- **Statut** : Proposé
- **Date** : 2026-10-28

## Contexte

Le prompt (§1) exige d'établir, preuves à l'appui, comment doter CodeIDE d'un moteur Java de niveau IDE sur Android/ART en s'appuyant sur l'architecture IntelliJ, avec quatre options **à armes égales** : embarquer les artefacts IntelliJ, s'en inspirer (réécriture des mécanismes sur la base ecj/JDT), combiner, ou conclure que le statu quo amélioré est préférable. Aucune conclusion n'est pré-écrite. Les preuves disponibles :

- **Étude D1–D12** (`01-architecture-psi.md`) : aucun domaine ne débouche sur « embarquer tel quel » ; aucun sur « ignorer ». 12 verdicts : réutiliser partiellement (D1), émuler (D2), réutiliser java-syntax (D3), s'inspirer (D4–D12, dont 4 « fortement »).
- **Spike** (`02-spike-mesures.md`) : V1 (java-syntax) = 798 Ko / 2 965 méthodes / **0 référence sensible ART** / parse p50 0,65 ms — transposable en l'état. V2 (PSI headless 232.4652) = fonctionne sur JVM **après** une taxe d'assemblage (3 EP manuels + adaptateur + home factice), mais 37,4 Mo minimum / 124 513 méthodes déclarées / **108 motifs sensibles ART** (swing, awt, `sun.misc.Unsafe`, `java.lang.management`, jna) / parse p50 ~5 ms (×7 vs V1, ×5 vs ecj) — **disqualifié pour l'embarquement direct**, précieux comme référence de protocoles. V3 (ecj) = le plus rapide en parse (diet p50 0,32–1,3 ms), 6 motifs sensibles dont 4 déjà couverts par le patcheur du statu quo.
- **Faits d'architecture majeurs** : pas de requête par préfixe dans le stockage IntelliJ (D7 — CIDX l'a et gagne) ; les deux piles de persistance IntelliJ sont incompatibles ART (cleaner `sun.nio.ch.DirectBuffer` / `java.lang.foreign`) ; les diagnostics rouges d'IntelliJ sont une réimplémentation (49 f / 18 130 l., D9) alors qu'ecj donne la fidélité compilateur native ; le classement de complétion (weighers + arrangement + split RPC, D8) et ModCommand (D9) sont les apports les plus transposables.

## Sources consultées

- `JetBrains/intellij-community` @ `865067b0` (lecture seule, licence Apache-2.0 — « JetBrains Open-Source Build Terms v1.3 ») : modules et fichiers cités dans `01-architecture-psi.md` §D1–D12 et annexes.
- Artefacts mesurés : `org.jetbrains:java-syntax 0.3.381` ; `com.jetbrains.intellij.{platform,java}:232.4652` ; `org.eclipse.jdt:ecj 3.40.0` (dépôts et conditions : ADR 0017).
- `jjoblab/codeide-lsp` @ `cb809e14` (v1.16.0), lecture seule : `docs/LSP_JAVA.md` (§4 mesures, W1–W10), ADR 0111 (CIDX), matrice de capacités.
- Rapports internes : `01-architecture-psi.md`, `02-spike-mesures.md`, `00b-inventaire-reprise.md`.

## Options

**Option A — Embarquer la plateforme IntelliJ (artefacts).** PSI headless + résolution native sur artefacts publiés. Preuves contre : 37,4 Mo minimum / 124 513 méthodes (dépasse tout budget APK, §7 du spike) ; 108 motifs sensibles ART dont `Unsafe`/swing/awt → shims massifs non vérifiables statiquement ; taxe d'assemblage à re-diagnostiquer à chaque version (§4.1 du spike) ; parse ×5–7 vs ecj ; conclut D7 : l'index ne vient pas avec (incompatible ART) donc pas de complétion de types viable non plus ; D9 : on perd la fidélité compilateur. **Preuves pour** : résolution native démontrée (findClass/resolve fonctionnels), navigation riche à terme.

**Option B — S'inspirer : statu quo ecj/CIDX consolidé + greffe des protocoles et designs IntelliJ.** Conserver ecj (fidélité compilateur, D9) et CIDX (supérieur sur les axes mobiles, D7) ; greffer en Kotlin (R2) : (1) `java-syntax` V1 **réutilisé tel quel** comme parseur d'appoint (reparse incrémental, validation d'accolades, éventuel arbre léger) ; (2) la chaîne de classement de complétion (weighers/`WeighingComparable` paresseux + arrangement : gel, liftShorter, trim honnête) et la frontière RPC client-serveur (D8) ; (3) ModCommand comme modèle du CodeActionService v2 (D9) ; (4) le protocole de phases du rename + QueryExecutor sur CIDX (D10) ; (5) le schéma de concurrence B (+C) (D11) ; (6) la discipline mémoire (soft-réfs, budgets octets, invalidation par dépendances, shim `onTrimMemory`) (D12).

**Option C — Combinaison « double moteur »** : ecj pour diagnostics/compilation + PSI embarqué pour navigation/refactorings. Combine les coûts d'A et de B (APK de 40+ Mo) pour un gain couvert par B à moyen terme — rejetée sauf preuve contraire sur appareil.

**Option D — Statu quo amélioré (sans apport IntelliJ).** Corriger W1–W10 du moteur actuel sans greffer les designs étudiés. Abandonne les gains de qualité perçue identifiés (classement ~90 % de la valeur perçue de la complétion, D8 ; rename sûr, D10) ; maintenue comme **repli** de B pendant la migration (le moteur actuel reste la production).

## Critères et mesures

- Tableau comparatif mesuré ( spike §8 ) : parse p50/p95, résolution, empreinte, sensibilité ART, taxe d'intégration — V1/V2/V3.
- Verdicts D1–D12 avec citations `fichier:ligne` : `01-architecture-psi.md` et annexes.
- Conclusion obligatoire §5 (puissance de PSI vs plateforme autour) : la qualité perçue réside dans **cinq mécanismes autour du PSI** (index multi-usage+forward, usage search indexée, ~660 inspections, classement, concurrence/mémoire) — tous non portables en code, tous repris en designs dans B.

## Décision proposée

**Option B — hybride « s'inspirer »**, avec réemploi effectif d'exactement un artefact IntelliJ : `java-syntax` (V1). Le moteur ecj reste la source de vérité des types et diagnostics (ADR 0007/0013) ; CIDX reste l'index (ADR 0008) ; les designs IntelliJ sont greffés vague par vague (ADR 0015). L'option D demeure le repli permanent (retour arrière à chaque vague) ; l'option A est archivée avec ses preuves (spike §4/§6/§7) — elle ne se rouvrirait que sur des artefacts JetBrains futurs radicalement plus compacts et ART-clean, à re-tester au spike avant toute décision.

## Conséquences

- **Positives** : fidélité compilateur conservée (atout différenciant, D9) ; empreinte APK quasi inchangée (+798 Ko max pour java-syntax) ; pas de shim nouveau (V1 : 0 référence sensible) ; gains de qualité perçue actionnables à court terme (classement, CodeAction, rename) ; risque juridique limité (Apache-2.0 permissif, ADR 0017).
- **Négatives** : le travail de greffe est du **code neuf Kotlin** à écrire et maintenir (chaîne de weighers, ModCommand, QueryExecutor…) — estimation par vagues dans `00-synthese.md` ; la résolution « native PSI » (navigation riche multi-fichiers sans ecj) n'est pas achetée — elle se reconstruit sur ecj/CIDX (D5/D10).
- La montée de version JDT (≥ 3.43) devient un axe de maintenance structurant (ADR 0018).

## Plan de migration progressive

Vagues détaillées dans `00-synthese.md` §4 (amorçage → consolidation verrous/mémoire → classement → CodeAction/rename → parsing incrémental V1) ; chaque vague : drapeau de fonctionnalité, double moteur (ancien comportement conservé derrière le drapeau), retour arrière documenté, critères de sortie mesurables (ADR 0016).

## Tests de non-régression et critères de sortie

- Chaque greffe doit passer le banc de qualité ADR 0016 ( recall/top-1/MRR sur corpus fixe pour le classement ; corpus de renommages pour le rename ; corpus de diagnostics pour la fidélité ) **sans régression** du comportement existant.
- Critère de sortie global de la stratégie : latences §4 tenues sur appareil ( gabarit `spike/appareil/` ) et matrice de capacités au niveau actuel ou supérieur.

## Risques

- **R1 — Sous-estimation du coût des greffes Kotlin** : mitigation = vagues bornées, chacune livrant une capacité mesurable seule ; repli D permanent.
- **R2 — Dérive « réinventer IntelliJ »** : mitigation = ne greffer que les designs dont la valeur est prouvée dans les rapports (D8/D9/D10/D11/D12) ; refuser les greffes sans critère §4 mesurable.
- **R3 — La voie A se referme définitivement** : non — le spike est rejouable ( `spike/README.md` ) ; re-tester si JetBrains publie une pile headless ART-clean.
- **R4 — Montée JDT 3.43+ casse les shims** : ADR 0018 ( réévaluation systématique à chaque montée ).
