# ADR 0001 — Licence du nouveau dépôt et règles de reprise

- **Statut** : Proposé (décision du **propriétaire** — aucune copie n'a lieu avant sa décision, R9.1)
- **Date** : 2026-10-28

## Contexte

Le nouveau dépôt `jjoblab/code-lsp` démarre sur des bases neuves : il ne reprend des anciens dépôts (`codeide-lsp` v1.16.0 @ `cb809e14`, `codeide-tooling` @ `b60f7f0`, `CodeIDE` @ `54e99c6`, `code-editor` @ `066b8c0` — tous en lecture seule) que le strict nécessaire, après contrôle de provenance (R9). Le choix de la licence du nouveau dépôt conditionne trois chaînes :

1. la **reprise du moteur vendored `lsp/java`** (≈ 25 839 lignes Java main, exclu par défaut R9.3 malgré la clôture ADR 0110/0200 dans l'ancien dépôt) ;
2. la **reprise des couches candidates** (`:lsp:index` CIDX, `:lsp:model`, `:lsp:testing` — manifeste `docs/PROVENANCE-REPRISE.md`) ;
3. les **dépendances d'artefacts** futures : ecj (EPL-2.0, modifié et redistribué — obligations de source et de mention des modifications), `org.jetbrains:java-syntax` (Apache-2.0), protocole tooling (**sans fichier LICENSE à ce jour** — vigilance), lsp4j (EPL-2.0), ASM (BSD-3).

Pièces à conviction disponibles pour la décision (inventaire Phase 0, `00b-inventaire-reprise.md` §0) : l'ADR 0110 de l'ancien dépôt est **clôturé** (niveau A réécrit depuis la spec JDK en v0.94.0 ; niveau B attesté travail original par le propriétaire les 16 et 19/10/2026 ; renommage intégral `Jdt*→Ecj*` v0.95.0 ; purge des désignations tiers ADR 0200). Ce fait n'annule pas l'exclusion par défaut posée par le prompt de mission, mais démontre que la voie « réécriture cloisonnée depuis spécifications » a déjà été exécutée une fois avec succès.

## Sources consultées

- `jjoblab/codeide-lsp` @ `cb809e1412fec429c9672da087244b3bd3176545` (v1.16.0), lecture seule : `docs/adr/` ADR 0108, 0109, 0110, 0111, 0200 ; `docs/THIRD_PARTY_NOTICES.md` ; `README.md` ; code de `lsp/{java,index,model,testing,ecj-art}` (comptages). Dépôt propriétaire jjoblab.
- `jjoblab/codeide-tooling` @ `b60f7f0`, lecture seule : absence constatée de fichier `LICENSE` (notice tierce de `codeide-lsp`, ligne du 2026-10-20).
- `JetBrains/intellij-community` @ `865067b0a7f1a73ee0fa8bce4f042013c8ac334f`, lecture seule : `LICENSE.txt` (« JetBrains Open-Source Build Terms v1.3 » du 15/06/2026, sources Apache-2.0). Aucun code copié (R6.4).
- Artefacts binaires consultés/mesurés : `org.jetbrains:java-syntax 0.3.381` (+ modules KMP), `com.jetbrains.intellij.{platform,java}:232.4652`, `org.eclipse.jdt:ecj 3.40.0` — dépôts cache-redirector JetBrains (intellij-dependencies) et Maven Central ; conditions d'utilisation à consigner (→ ADR 0017).
- Prompt de mission, règles R6/R9 ; inventaire Phase 0.

## Options

**Option 1 — Licence permissive (Apache-2.0) + reprise conditionnelle du vendored.** Le dépôt prend Apache-2.0 ; le propriétaire lève l'exclusion R9.3 du moteur `lsp/java` par décision écrite adossée à la clôture ADR 0110 (attestation de paternité niveau B déjà produite ; réécriture niveau A déjà exécutée) ; le patcheur `ecj-art` est **réécrit** (468 lignes, dicées par l'API ASM et la javadoc JDK/JEP 322).

**Option 2 — Licence permissive + réécriture intégrale du moteur.** Aucune reprise du vendored : repartir de zéro sur ecj avec un cahier de spécifications extrait des 449 tests (00b §4). Coût maximal (le moteur représente ~18 mois de durcissement), gain de pureté de provenance.

**Option 3 — Licence copyleft (GPL/AGPL) pour éclairer la reprise du vendored.** Rejetée d'emblée : R6.2 interdit de consulter des sources GPL/AGPL pour cette étude, et une licence copyleft du nouveau dépôt contredirait la chaîne de dépendances permissives existantes (guava, ASM, java-syntax).

**Option 4 — Ne rien changer** (statu quo : le moteur vit dans `codeide-lsp`, pas de nouveau dépôt). Contredit la mission (§0 : « le propriétaire repart sur de nouvelles bases ») ; listée pour mémoire.

## Critères et mesures

- Périmètre et statuts de chaque module : `docs/PROVENANCE-REPRISE.md` (registres exclusions/candidats/dépendances) ; `00b-inventaire-reprise.md` (verdicts module par module).
- Empreinte de la reprise : moteur 118 f / 42 068 l. main total dont 53 fichiers core ≈ 25 839 l. ; patcheur 2 f / 468 l. ; tests 65 f / 16 229 l. / 449 méthodes (dérive vs « 400 » du prompt — consignée `ALERTES.md`).
- Risque juridique de la chaîne protocole tooling (sans LICENSE) : bloquant pour la reprise de `:lsp:model` en l'état (00b §2).

## Décision proposée

**Option 1** : dépôt sous **Apache-2.0** (aligné sur l'écosystème consommé : java-syntax, guava, ASM) ; levée conditionnelle de l'exclusion du moteur `lsp/java` **par décision écrite du propriétaire** dans le présent ADR (les attestations ADR 0110/0200 de l'ancien dépôt servent de pièces justificatives) ; **réécriture** obligatoire du patcheur `ecj-art` ; obligations EPL-2.0 pour ecj modifié (sources amont + description des modifications) reportées dans un `NOTICE` dès la première publication ; **action propriétaire préalable** : ajouter un fichier `LICENSE` au dépôt `jjoblab/codeide-tooling` (sinon réécrire les ~10 types du protocole consommés).

## Conséquences

- La reprise du moteur devient possible immédiatement après acceptation : aucune autre voie ne livre un moteur opérationnel à budget raisonnable.
- Le dépôt devra porter `LICENSE`, `NOTICE` (chaîne ecj EPL-2.0 + artefacts JetBrains + protocole tooling) et maintenir `docs/PROVENANCE-REPRISE.md` à chaque reprise (R9.2).
- Toute future contribution externe devra passer par une clause de contribution compatible Apache-2.0.

## Plan de migration progressive

1. Propriétaire tranche la licence et la levée d'exclusion (acceptation du présent ADR) → consignation dans `PROVENANCE-REPRISE.md` §6 (journal).
2. Vague 0 (amorçage) : `LICENSE` + `NOTICE` squelette + vérification CI du manifeste (aucun fichier repris hors registre).
3. Vague 1 : reprise contrôlée module par module (index → model → testing → moteur), chaque copie inscrite au manifeste avant d'avoir lieu.
4. Retour arrière : tant qu'aucune copie n'a eu lieu, les options 2/4 restent ouvertes sans coût.

## Tests de non-régression et critères de sortie

- Critère de sortie de la vague de reprise : chaque fichier repris apparaît dans le manifeste avec statut « propre » ou « à vérifier » (jamais vide) ; la CI refuse tout fichier hors registre.
- Non-régression comportementale : le corpus des 449 tests rejoué contre le moteur repris (fiches de spécification, 00b §4) — 0 comportement perdu (critère ADR 0016).

## Risques

- **R1 — Contestation future de la paternité du niveau B** : mitigation = conserver les attestations datées de l'ancien dépôt et les référencer dans le `NOTICE` ; ne jamais supprimer d'attribution (R9.5).
- **R2 — Chaîne protocole tooling non éclairée** : mitigation = action propriétaire préalable ou réécriture des ~10 types (00b §2).
- **R3 — Dérive de la clôture ADR 0110 dans le temps** : le moteur a évolué depuis le 19/10 (v0.97.0+ par module) — chaque nouvelle montée devra re-passer le contrôle de provenance pour les fichiers touchés.
