# ADR 0013 — Diagnostics, inspections, fidélité au compilateur

- **Statut** : Proposé
- **Date** : 2026-10-28

## Contexte

§5-D9 : « quelle part [des inspections/quick fixes] dépend de la plateforme IDE ? ». Réponse de l'étude : **la part plateforme est majoritaire en code, minoritaire en valeur** — le contrat d'inspection ( ~1 500 lignes utiles : visitor/holder/descriptor/quickfix ) est pur PSI et 0-UI ; l'exécution on-the-fly ( ~14 k lignes : pass + HighlightInfo + daemon + EDT ) est du pur rendu ; la valeur sémantique des ~660 inspections ( `java-impl-inspections` 332 f / 78 317 l. + `java-analysis-impl/codeInspection` 328 f / 68 011 l. ) dépend des services de calcul ( D5, D7 ), pas de l'UI.

Fait décisif pour la fidélité ( §4 ) : les erreurs « compilateur » d'IntelliJ sont une **réimplémentation** — `java/codeserver` [hors sparse] 49 f / 18 130 l. de checkers sur PSI/résolution ( `JavaErrorKinds` 1 912 l. ), aucun ecj. Le statu quo ( `DiagnosticService` → bindings ecj natifs, correctifs §17 de `LSP_JAVA.md`, diagnostics riches §19.1 ) détient la fidélité compilateur native.

**Découverte clé — ModCommand** ( `platform/analysis-api/src/com/intellij/modcommand/`, 37 f / 2 975 l. ) : interface scellée de records sérialisables ( javadoc : « every ModCommand can be easily serialized and sent e.g., over the network » — `ModCommand.java:42-43` ) ; le fix est **calculé en read action comme un plan déclaratif** ( textes ancien/nouveau par fichier ), prévisualisable, sérialisable ; `ModCommandAction` est le remplacement déclaré de `IntentionAction`. C'est **le modèle direct d'un CodeAction LSP** ( `WorkspaceEdit` ) — grille de conception pour un `CodeActionService` v2 ( 919 lignes d'édits impératifs ad hoc aujourd'hui côté statu quo ).

## Sources consultées

- `JetBrains/intellij-community` @ `865067b0` (lecture seule) : `platform/analysis-api` `LocalInspectionTool.java:138-140`, `modcommand/*` ( 37 f ), `platform/analysis-impl` `InspectionEngine.java:328-432,435-438` ; `java/java-analysis-impl` `HighlightVisitorImpl.java:62-68` ; `java/java-impl-inspections` ( inventaire 332 f / 78 317 l., 124 `BaseInspection`, 147 ModCommand, **1 seul fichier swing** ) ; en-têtes `com.siyeh.ig.*` ( Apache-2.0, Griffith/Leijdekkers ) — cités pour mémoire ( R6/R9 ).
- `jjoblab/codeide-lsp` @ `cb809e14` : `DiagnosticService`, `JavaProblemFamily`, `CodeActionService` ( 919 l. ), `EcjFastDiagnose` ; `docs/LSP_JAVA.md` §17/§19.1 ; matrice de capacités.

## Options

**Option 1 — ecj garde les « rouges » ; greffer le protocole ModCommand + un contrat d'inspection maison ( recommandé ).** (a) `CodeActionService` v2 réécrit sur le modèle ModCommand : quick-fix = plan déclaratif sérialisable ( texte/fichier/navigate/rename/conflict ), calcul en lecture, preview sur copie, exécution déléguée au client LSP ; (b) contrat visitor/holder/descriptor ( ~1 500 lignes ) pour des inspections **lint** ( non-compilateur ) par-dessus bindings ecj + CIDX ( ex. imports non utilisés — déjà couvert — puis symboles non lus, égal/distance suspectes… ) ; (c) séparation dumb-aware ( inspections index-dépendantes marquées, servies hors indexation — ADR 0008 ).

**Option 2 — Porter des inspections IntelliJ.** Interdit sans décision propriétaire ( R6.4/R9 — code Apache-2.0, obligations NOTICE/modifications ) et coûteux ( chacune consomme PSI/résolution IntelliJ ). Seule une sélection ciblée pourrait un jour être **réécrite depuis leur spécification publique** — hors périmètre de la vague initiale.

**Option 3 — Adopter le codeserver comme substitut d'ecj.** Perdrait la fidélité compilateur ( l'atout différenciant, D9 ) — rejeté.

**Option « ne rien changer »** : diagnostics ecj + CodeActionService actuel ( 919 l. ad hoc ) — repli.

## Critères et mesures

- Fidélité ( §4 ) : corpus faux positifs/négatifs — ecj natif ; baseline à figer ( ADR 0016 ).
- Périmètre ModCommand : 37 f / 2 975 l. de protocole — le modèle, pas le code ( R6.4 ).
- Matrice de capacités : diagnostics riches §19.1, quick-fixes — suivie en ADR 0015.

## Décision proposée

**Option 1** : les diagnostics **rouges restent ecj** ( source de vérité ADR 0007 ) ; le `CodeActionService` v2 adopte le **modèle ModCommand** ( réécriture Kotlin, sérialisation LSP `WorkspaceEdit`/`CodeAction` ) ; un contrat d'inspection léger ouvre la voie aux inspections lint maison ( dumb-aware, ADR 0008 ) ; aucun code d'inspection IntelliJ n'est copié ( reprise éventuelle = réécriture depuis spécification + ADR dédié + attributions `com.siyeh.ig` si style InspectorGadgets — R6.4 ).

## Conséquences

- Les quick-fixes deviennent déclaratifs, sérialisables, prévisualisables : testables unitairement ( plan attendu, pas effet de bord ), rejouables, transportables ( frontière serveur/client nette ).
- La 919-ligne ad hoc se retire progressivement ( double moteur derrière drapeau le temps du banc ).
- Le corpus d'inspections maison grandit à coût marginal ( contrat léger ) mais **ne remplace jamais** ecj sur le rouge.

## Plan de migration progressive

1. Vague « ModCommand v2 » : nouveau service + drapeau `codeactions.v2` ; les fixes existants migrent un par un ( chaque migration validée par test doré de comportement ) ; ancien service vivant pour retour arrière.
2. Vague « inspections lint » : contrat + 2 inspections pilotes ( dumb-aware ) ; extension par PR.
3. Critère de retrait de l'ancien service : parité de comportement sur le corpus de quick-fixes ( 0 régression ).

## Tests de non-régression et critères de sortie

- Corpus quick-fixes : chaque fix v2 produit le même `WorkspaceEdit` que la version v1 sur les cas dorés ; preview exacte ( snapshot ) ; idempotence.
- Corpus diagnostics : zéro variation vs baseline ecj ( les inspections lint s'ajoutent, ne remplacent rien ).
- Dumb : inspections marquées index-dépendantes servies seulement hors dumb ( test avec indexation simulée ).

## Risques

- **R1 — Divergence v1/v2 pendant la migration** : tests dorés versionnés ( baseline ADR 0016 ).
- **R2 — Tentation d'étendre le rouge maison** : garde-fou architectural — le rouge n'a qu'une source ( ecj ), revue de code systématique.
- **R3 — ModCommand incomplet pour nos cas** ( refactoring multi-fichiers complexes ) : passer par l'ADR 0014 ( protocoles rename/usages ), pas par des édits impératifs.
