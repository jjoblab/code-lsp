# ADR 0001 — Licence du nouveau dépôt et règles de reprise

- **Statut** : **Accepté** — décision du **propriétaire** reçue en séance le 2026-10-28 : **option 2 — réécriture intégrale du moteur**, motivation écrite : « **partir sur de nouvelles bases plus solides** ». L'option 1 (levée conditionnelle de l'exclusion du vendored) initialement proposée est **écartée** ; le présent ADR a été réécrit à la décision.
- **Date** : 2026-10-28 (rédaction initiale — option 1 proposée) / 2026-10-28 (acceptation-modification — option 2 ; réécriture du document)

## Contexte

Le nouveau dépôt `jjoblab/code-lsp` démarre sur des bases neuves : il ne reprend des anciens dépôts (`codeide-lsp` v1.16.0 @ `cb809e14`, `codeide-tooling` @ `b60f7f0`, `CodeIDE` @ `54e99c6`, `code-editor` @ `066b8c0` — tous en lecture seule) que le strict nécessaire, après contrôle de provenance (R9). Le choix de la licence et des règles de reprise conditionnait trois chaînes :

1. la **reprise du moteur vendored `lsp/java`** (≈ 25 839 lignes Java main, exclu par défaut R9.3 malgré la clôture ADR 0110/0200 dans l'ancien dépôt) ;
2. la **reprise des couches candidates** (`:lsp:index` CIDX, `:lsp:model`, `:lsp:testing` — manifeste `docs/PROVENANCE-REPRISE.md`) ;
3. les **dépendances d'artefacts** futures : ecj (EPL-2.0, modifié et redistribué — obligations de source et de mention des modifications), `org.jetbrains:java-syntax` (Apache-2.0), protocole tooling (**sans fichier LICENSE à ce jour** — vigilance), lsp4j (EPL-2.0), ASM (BSD-3).

Pièces à conviction disponibles pour la décision (inventaire Phase 0, `00b-inventaire-reprise.md` §0) : l'ADR 0110 de l'ancien dépôt est **clôturé** (niveau A réécrit depuis la spec JDK en v0.94.0 ; niveau B attesté travail original par le propriétaire les 16 et 19/10/2026 ; renommage intégral `Jdt*→Ecj*` v0.95.0 ; purge des désignations tiers ADR 0200). Ces pièces rendaient la levée d'exclusion **possible** (option 1). Le propriétaire a préféré ne pas bâtir le nouveau dépôt sur une base *attestée mais contestée dans son historique* : la réécriture élimine le risque à la racine au lieu de le gérer par des pièces datées à maintenir.

## Sources consultées

- `jjoblab/codeide-lsp` @ `cb809e1412fec429c9672da087244b3bd3176545` (v1.16.0), lecture seule : `docs/adr/` ADR 0108, 0109, 0110, 0111, 0200 ; `docs/THIRD_PARTY_NOTICES.md` ; `README.md` ; code de `lsp/{java,index,model,testing,ecj-art}` (comptages). Dépôt propriétaire jjoblab.
- `jjoblab/codeide-tooling` @ `b60f7f0`, lecture seule : absence constatée de fichier `LICENSE` (notice tierce de `codeide-lsp`, ligne du 2026-10-20).
- `JetBrains/intellij-community` @ `865067b0a7f1a73ee0fa8bce4f042013c8ac334f`, lecture seule : `LICENSE.txt` (« JetBrains Open-Source Build Terms v1.3 » du 15/06/2026, sources Apache-2.0). Aucun code copié (R6.4).
- Artefacts binaires consultés/mesurés : `org.jetbrains:java-syntax 0.3.381` (+ modules KMP), `com.jetbrains.intellij.{platform,java}:232.4652`, `org.eclipse.jdt:ecj 3.40.0` — dépôts cache-redirector JetBrains (intellij-dependencies) et Maven Central ; conditions d'utilisation à consigner (→ ADR 0017).
- Prompt de mission, règles R6/R9 ; inventaire Phase 0 (`00b-inventaire-reprise.md`, notamment §4 — conservation de la valeur des 449 tests sans reprendre le code).
- **Séance de décision du propriétaire du 2026-10-28** : instruction écrite « je veux une réécriture pour partir sur de nouvelles bases plus solides » — sélection de l'option 2.

## Options

**Option 1 — Licence permissive (Apache-2.0) + reprise conditionnelle du vendored.** Le dépôt prend Apache-2.0 ; le propriétaire lève l'exclusion R9.3 du moteur `lsp/java` par décision écrite adossée à la clôture ADR 0110 (attestation de paternité niveau B déjà produite ; réécriture niveau A déjà exécutée) ; le patcheur `ecj-art` est **réécrit** (468 lignes, dicées par l'API ASM et la javadoc JDK/JEP 322). **→ Écartée le 2026-10-28 : le propriétaire ne lève pas l'exclusion.**

**Option 2 — Licence permissive + réécriture intégrale du moteur.** Aucune reprise du vendored : repartir de zéro sur ecj avec un cahier de spécifications extrait des 449 tests (00b §4). Coût maximal (le moteur représente ~18 mois de durcissement), gain de pureté de provenance. **→ RETENUE le 2026-10-28** (motivation du propriétaire : « partir sur de nouvelles bases plus solides »).

**Option 3 — Licence copyleft (GPL/AGPL) pour éclairer la reprise du vendored.** Rejetée d'emblée : R6.2 interdit de consulter des sources GPL/AGPL pour cette étude, et une licence copyleft du nouveau dépôt contredirait la chaîne de dépendances permissives existantes (guava, ASM, java-syntax).

**Option 4 — Ne rien changer** (statu quo : le moteur vit dans `codeide-lsp`, pas de nouveau dépôt). Contredit la mission (§0 : « le propriétaire repart sur de nouvelles bases ») ; listée pour mémoire.

## Critères et mesures

- Périmètre et statuts de chaque module : `docs/PROVENANCE-REPRISE.md` (registres exclusions/candidats/dépendances) ; `00b-inventaire-reprise.md` (verdicts module par module).
- Empreinte de la reprise évitée : moteur 118 f / 42 068 l. main total dont 53 fichiers core ≈ 25 839 l. ; patcheur 2 f / 468 l. ; tests 65 f / 16 229 l. / 449 méthodes (dérive vs « 400 » du prompt — consignée `ALERTES.md`).
- Valeur conservée sans reprendre le code : cahier de spécifications extractible des **449 tests** (00b §4 : fiches de comportement, jeux de données, banc croisé de parité) — le procédé a déjà été exécuté une fois avec succès (niveau A, v0.94.0).
- Risque juridique de la chaîne protocole tooling (sans LICENSE) : bloquant pour la reprise de `:lsp:model` en l'état (00b §2) — inchangé par la présente décision.
- **Impact sur le plan de vagues** : la fourchette « vagues 0–4 : 75–130 j-h » (`00-synthese.md` §4) supposait la reprise du moteur comme substrat — **caduque sous l'option 2** ; remplacée le 28/10/2026 par la ré-estimation cahier en main : **`docs/PLAN-REECRITURE.md`** (fourchette 160–270 j-h, médiane 213, par étages N1–N8+T, soumise au propriétaire).

## Décision — ACCEPTÉE par le propriétaire le 2026-10-28 (option 2)

**1. Licence : Apache-2.0** (commune aux options 1 et 2 ; alignée sur l'écosystème consommé : java-syntax, guava, ASM). Le dépôt porte `LICENSE` + `NOTICE` (chaîne ecj EPL-2.0 + artefacts JetBrains + protocole tooling) dès la vague 0 ; contributions externes futures sous clause compatible Apache-2.0.

**2. Moteur : réécriture intégrale — l'exclusion du vendored devient définitive.** Aucune reprise, même partielle, du moteur `lsp/java` de `codeide-lsp` (ni code de production, ni code de test) : ni copie, ni adaptation, ni transcription consultée sur l'original. Le vendored **reste dans `codeide-lsp`** (production, oracle de parité, repli) et n'entre jamais dans `code-lsp` — aucun fichier, aucun chemin, aucune empreinte.

**3. Les nouvelles bases de la réécriture** (motivation du propriétaire : « partir sur de nouvelles bases plus solides ») :

- **cahier de spécifications** extrait des 449 tests (00b §4.1) : fiches de comportement (préconditions, entrée, attendu, bornes) rédigées **sans lire le code de production repris** ; le vendored reste utilisable comme **oracle en boîte noire** (l'exécuter et observer ses sorties), jamais comme source à transcrire ; jeux de données et golden files repris comme **données** au manifeste ;
- **architecture de référence** = étude D1–D12 + ADR 0004–0018 (GO PARTIEL : ecj + CIDX + java-syntax + designs D8–D12) — le code neuf intègre les designs **en naissant** (chaîne de weighers, ModCommand, rename par phases, verrous par périmètre, budgets octets) au lieu de les greffer après coup ;
- **Kotlin 100 %** (R2 ; aligne le moteur sur la règle « Kotlin 100 %, aucun Java écrit à la main » d'`AGENTS.md` de CodeIDE — le vendored Java était exempté par ADR 0108) ;
- **provenance propre par construction** : registre du manifeste vide pour le moteur + CI anti-fichier-hors-registre.

**4. Reprises maintenues** (provenance propre, hors périmètre de la contestation) : `:lsp:index` (CIDX — socle conservé par l'étude D7/ADR 0008), `:lsp:model`, `:lsp:testing`, jeux de données des tests — copies toujours conditionnées à l'inscription préalable au manifeste (R9.2) et, pour `:lsp:model`, à l'**action propriétaire P1** (ajouter une LICENSE au dépôt `jjoblab/codeide-tooling`, sinon réécrire les ~10 types consommés).

**5. Patcheur `ecj-art` : réécrit en Kotlin** (468 l., surface dicée par l'API ASM et la javadoc JDK/JEP 322) — inchangé par rapport à la proposition (voie unique toutes options).

**6. Obligations EPL-2.0** (ecj modifié et redistribué) : sources amont + description des modifications dans le `NOTICE` dès la première publication (inchangé).

## Conséquences

- **Positives** : le risque de contestation de paternité (R1 initial) est **éliminé par construction** — plus d'attestations à maintenir ni de dérive de clôture à suivre pour le moteur ; alignement Kotlin 100 % ; le moteur naît avec les designs de l'étude, sans la dette mesurée du vendored (verrou global 24+2 `synchronized`, score scalaire unique de complétion, `Files.walk` sur le chemin chaud, recalcul complet à chaque frappe — D8/D10/D11) ; le nouveau dépôt devient auditable en une page (manifeste + CI).
- **Négatives / coûts (assumés par le propriétaire)** :
  - **coût maximal** : le vendored représente ~18 mois de durcissement ; la réécriture est un chantier pluri-étapes dont l'estimation sérieuse exige le cahier de spécifications en main — la fourchette « vagues 0–4 : 75–130 j-h » de `00-synthese.md` §4 est **caduque** (R5 : pas de chiffre de remplacement sans données ; ré-estimation soumise au propriétaire à l'issue de la nouvelle vague 1) ;
  - **double existence prolongée** : le vendored continue de servir la production dans `codeide-lsp` tant que la parité du moteur neuf n'est pas mesurée (banc croisé ADR 0016 + appareil) ;
  - **savoir-faire implicite** : les corrections accumulées dont les tests ne portent pas la trace (76 tests ignorés, historique git) ne se transfèrent que par l'étude de l'historique en lecture seule et les fiches complétées pendant la parité ;
  - la charge des shims ecj/ART (4 motifs couverts + 2 à surveiller, spike §7) se re-porte dans le code neuf (elle restait de toute façon à maintenir).

## Plan de migration progressive

1. **Vague 0 (inchangée)** : `LICENSE` + `NOTICE` squelette + squelette Gradle Kotlin + CI (build, scan ART, dexage, seuils, bancs empreinte/latence, **contrôle anti-fichier-hors-registre** — garant automatique de l'exclusion définitive) + actions propriétaire (P1 LICENSE tooling ; conditions artefacts JetBrains).
2. **Nouvelle vague 1 (restructurée)** : (a) extraction du **cahier de spécifications** depuis les 449 tests (fiches versionnées, rédigées sans lire le code de production) ; (b) reprise contrôlée `index` → `model` → `testing` (inscription au manifeste avant chaque copie) ; (c) **réécriture du patcheur `ecj-art` en Kotlin** (liste de shims) ; (d) **ré-estimation du programme de réécriture du moteur** (j-h par étage, cahier en main) soumise au propriétaire avant l'étage noyau. **→ (a)(b)(c)(d) LIVRÉS le 28/10/2026** : cahier 65 fiches/449 comportements (1eadc96) ; `:model`+`:index` repris, 122 tests verts (04b51be) ; patcheur réécrit, 36 tests (d016030) ; ré-estimation **`docs/PLAN-REECRITURE.md`** (fourchette 160–270 j-h, médiane 213 — étages N1–N8+T, recalages prévus fin N1/fin N2).
3. **Étages de réécriture du moteur** (noyau env/ecj/parse/diagnostics → complétion → navigation/usages/rename), chacun borné par un critère de **parité mesurée** contre le vendored (banc croisé ADR 0016) avant de passer au suivant ; les designs des vagues 2–4 (`00-synthese.md` §4) sont intégrés en naissance, pas greffés.
4. **Bascule** : uniquement à parité du cahier atteinte sur appareil (gabarit `spike/appareil/`) ; le vendored demeure le repli permanent — aucune suppression dans l'ancien dépôt (lecture seule).

## Tests de non-régression et critères de sortie

- **Couverture du cahier** : 100 % des 449 comportements fichés (fiches versionnées, relues) avant le lancement de l'étage noyau.
- **Banc croisé de parité** : le même corpus rejoué contre le vendored (oracle, exécuté dans `codeide-lsp`) et le moteur neuf — critère de bascule : 0 comportement perdu (ADR 0016) et latences §4 tenues sur appareil.
- **CI anti-contamination** : zéro fichier hors manifeste ; contrôle d'empreinte sur les fichiers sensibles en complément du registre (garant de l'absence de copie déguisée).

## Risques

- **R1 — Contestation future de la paternité (niveau B)** : **éliminé par construction** (aucune reprise) ; reste le risque symétrique R5 ci-dessous.
- **R2 — Chaîne protocole tooling non éclairée** : inchangé — action propriétaire P1 ou réécriture des ~10 types (00b §2).
- **R3 — Dérive de la clôture ADR 0110 dans le temps** : **obsolète pour le moteur** (plus de reprise) ; reste pour les couches candidates (contrôle R9.4 à chaque copie).
- **R4 — Sous-estimation du coût de la réécriture** : mitigation = cahier d'abord, étages bornés à une parité mesurable, ré-estimation soumise au propriétaire avant l'étage noyau ; repli = le vendored reste en production dans `codeide-lsp`.
- **R5 — Copie déguisée** (une « réécriture de mémoire » qui recopie) : mitigation = fiches rédigées sans lecture du code de production, oracle vendored en boîte noire uniquement, revue par similarité (détection de clones) sur les étages sensibles, CI anti-fichier-hors-registre.
- **R6 — Perte de comportements non testés** (durcissement implicite) : mitigation = banc croisé systématique + consultation de l'historique git de l'ancien dépôt (lecture seule, entrées de mission) pour les incidents documentés.
