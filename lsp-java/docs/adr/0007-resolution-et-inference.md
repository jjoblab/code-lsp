# ADR 0007 — Résolution et inférence : source de vérité des types, rôle d'ecj pour la compilation

- **Statut** : Proposé
- **Date** : 2026-10-28

## Contexte

§5-D5 pose la question : « quelle est la source de vérité des types, et la fidélité face au compilateur ? » Les faits :

- **IntelliJ** : `ResolveCache` ( 8 maps, clés faibles ) avec **vidage total** sur `ANY_PSI_CHANGE` ( `core-impl`, 343 l. ) ; invalidation par compteur global `PsiModificationTrackerImpl` ; inférence `graphInference` = 22 fichiers / 5 908 l. ( `InferenceSession` 2 114 l. ), réimplémentation JLS 18 calée sur javac ( clés Registry `javac.*` ) ; `findClass` par EP `PsiElementFinder` → `JavaFileManagerImpl`/index ( variante headless sans index : parcours VFS, mesuré ~100 ms/jar froid au spike V2 ).
- **Statu quo** : les **bindings ecj** sont la source de vérité ( `EcjCompilerEngine.resolveUnit` sous verrou, `DiagnosticService` → diagnostics natifs compilateur, correctifs de fidélité §17 de `LSP_JAVA.md` ) ; re-résolution fine par frappe ( plus fine que le vidage total IntelliJ ).
- **Spike** : V2 résolution native PSI fonctionne sur JVM ( resolve p50 0,6–1,5 ms, findClass chaud 0 ms ) **mais** la pile entière est disqualifiée ( ADR 0004 ) ; V3 compile résolue d'un snippet : 3,7–5,7 ms p50 à environnement chaud.
- **D9** : les erreurs « compilateur » d'IntelliJ sont une réimplémentation ( codeserver 49 f / 18 130 l. ) — la fidélité ecj est un **atout différenciant** du statu quo.

## Sources consultées

- `JetBrains/intellij-community` @ `865067b0` (lecture seule) : `platform/core-impl` `ResolveCache.java`, `PsiModificationTrackerImpl` ; `java/java-psi-impl` `graphInference/*`, `PsiResolveHelperImpl` ; `java/java-indexing-serializers` `CoreJavaFileManager`.
- Spike : `resultats/v2-*.txt` ( findClass/resolve ), `resultats/v3-reels.txt` ( compile résolue ).
- `jjoblab/codeide-lsp` @ `cb809e14` : `EcjCompilerEngine` ( 2 417 l., 24 méthodes synchronized ), `docs/LSP_JAVA.md` §17, `docs/LSP_MATRICE_CAPACITES.md`.

## Options

**Option 1 — ecj reste la source de vérité unique ; l'inférence IntelliJ n'est pas portée.** Le binding ecj sert types, diagnostics, complétion de membres, navigation. L'inférence générique d'IntelliJ ( JLS 18/javac ) n'apporterait de valeur que pour des diagnostics plus riches — au prix de 5 908 lignes à porter et d'une deuxième vérité.

**Option 2 — Double vérité ( ecj compilateur + inférence Kotlin maison « à la graphInference » ).** Deux moteurs d'inférence = deux ensembles de divergences à gouverner ; rejeté à ce stade ( réévaluable si des diagnostics d'inférence manquent dans la matrice ).

**Option 3 — Embarquer la résolution IntelliJ ( voie V2 ).** Disqualifiée par le spike/ADR 0004 ( empreinte, ART, index manquant ).

**Option 4 — Ne rien changer.** = Option 1 sans les consolidations ( verrous, invalidation ) — l'option 1 est le statu quo **consolidé** ( ADR 0010/0011 ).

## Critères et mesures

- Fidélité des diagnostics vs compilateur ( §4 ) : corpus faux positifs/négatifs — ecj natif vs codeserver réimplémenté ( D9 ) ; le statu quo a déjà les correctifs §17.
- Latence de résolution : compile résolue V3 3,7–5,7 ms p50 ( snippet, env chaud ) vs resolve V2 0,6–1,5 ms ( PSI + caches chauds ) — l'écart est dans l'ordre de grandeur du budget de frappe, sans changer de moteur.
- Coût du port de graphInference : 22 f / 5 908 l. + dépendance pile PSI — non mesuré précisément, « à vérifier ».

## Décision proposée

**Option 1** : les **bindings ecj demeurent l'unique source de vérité des types** ( compilation, diagnostics, membres, hover ) ; l'inférence IntelliJ n'est pas portée ( sa réévaluation passe par un besoin démontré dans la matrice de capacités ) ; les gains viennent des consolidations : invalidation ciblée ( époque D11 ), caches de résolution à dépendances ( D12 ), et **des verrous par périmètre** ( ADR 0010 ) plutôt que d'un changement de moteur.

## Conséquences

- La fidélité compilateur reste l'argument différenciant face à tout concurrent embarquant PSI ( D9 ).
- La dette « re-résolution par frappe » se traite par la gate incrémentale ( ADR 0006 ) et l'époque ( ADR 0010 ), pas par un nouveau résolveur.
- La montée JDT ( ADR 0018 ) devient le principal risque de fidélité — suivie par le banc de diagnostics ( ADR 0016 ).

## Plan de migration progressive

Aucune migration de moteur : consolidations par vagues ( verrous → époque → caches ) derrière drapeaux ; mesure de latence de frappe avant/après chaque vague ( banc ADR 0016 + appareil ).

## Tests de non-régression et critères de sortie

- Corpus de diagnostics ( faux +/− : zéro régression vs baseline versionnée ) ; tests de résolution existants ( spec extraite des 449 tests, 00b §4 ).

## Risques

- **R1 — Besoins d'inférence non couverts par ecj** ( p.ex. quick-fix proposant des types ) : y répondre par des requêtes bindings ciblées, pas par un inférenceur maison ; réévaluer option 2 sur preuve.
- **R2 — Verrou global = gouffre de latence multi-modules** : traité en ADR 0010 ( schéma B ).
