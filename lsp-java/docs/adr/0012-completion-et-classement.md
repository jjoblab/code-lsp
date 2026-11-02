# ADR 0012 — Complétion et classement

- **Statut** : Proposé
- **Date** : 2026-10-28

## Contexte

§5-D8 : « quels mécanismes de classement sont transposables ? ». C'est **le domaine le plus transposable de la pile** ( verdict D8 : « s'inspirer fortement » ) :

- **Chaîne IntelliJ** : classifiers/weighers composés — `defaultSorter` ( `BaseCompletionService.java:204-238` ) : `PreferStartMatching` + weighers EP ( prefix/stats/priority/grouping ) + `LiftShorterItemsClassifier`, cache des poids ( `CompletionSorterImpl:112-124` ) ; comparaison **paresseuse poids par poids** ( `WeighingComparable:12-103` ). **Arrangement** ( `BaseCompletionLookupArranger` ~734 l. ) : gel des items affichés ( `myFrozenItems:71`, `freezeTopItems:522-531` ), `trimToHalfLimit` avec publicité honnête, batchUpdate.
- **Split RPC frontend/backend** ( `platform/completion/common` : 57 f / 2 340 l. kotlinx.serialization ) : `RpcCompletionItemWeight` ( classement backend transporté comme entier ), **`RpcPrefixMatcher` sérialisé côté client = re-filtrage local par frappe sans RPC** — exactement la frontière qu'impose un LSP sur Android, réponse structurelle au facteur ART 3–5×.
- **Chemin de classement pur = 0 swing/awt** ( vérifié ) ; `StatisticsWeigher` dépend de la pile statistics ( absente du sparse — à réécrire sur un simple compteur d'usage local ).
- **Statu quo** : `EcjCompletionEngine` ( 3 491 l. ) — pipeline éprouvé ( 5 variantes de splice autour du marqueur `__codeide_completion__`, même invention que `insertDummyIdentifier` d'IntelliJ : `CompletionInitializationUtil.java:123-149` ) ; mais classement = **un seul score scalaire** `rankScore` ( :3228-3258 : base 1000 − bonus grade − `BONUS_ASSIGNABLE` + proximité − pénalités ), tri pertinence → longueur → alphabétique ; recalcul complet à chaque frappe ; pas de stats d'usage, pas de gel, pas de liftShorter, pas de trim honnête.

## Sources consultées

- `JetBrains/intellij-community` @ `865067b0` (lecture seule) : `platform/analysis-{api,impl}` ( weighers, `Weigher.java:17-29` ), `platform/completion/{common,frontend,backend}` ( protocole RPC — `RpcCompletionItemWeight.kt:12-19`, `RpcPrefixMatcher` ), `java/java-impl` `JavaCompletionContributor` ( 1 380 l. ), `AllClassesGetter.java`.
- `jjoblab/codeide-lsp` @ `cb809e14` : `EcjCompletionEngine` ( 3 491 l. ), `EcjPrefixMatcher` ( grades EXACT/PREFIX/PREFIX_CI/HUMP/SUBSTRING :34-36 ), `EcjSyntaxCompletion`, `LatenceCompletionBenchmark`.
- Spike ( contexte ART ) : `02-spike-mesures.md` §4 ( V2 resolve/findClass = ordres de grandeur disponibles pour le temps serveur ).

## Options

**Option 1 — Réécrire le classement en Kotlin par-dessus l'API `CompletionItem` actuelle ( recommandé ).** Chaîne classifiers/weighers ordonnés + `WeighingComparable` paresseux ( ~10 fichiers / ~1 500 lignes de noyau, D8 ) ; y brancher les grades + proximité existants, puis `liftShorter`, gel des items affichés, `trimToHalfLimit` honnête ; **adopter la frontière RPC** : le serveur envoie ( items + poids entier + matcher sérialisé + présentation ) et le client re-filtre localement à chaque frappe ; `StatisticsWeigher` réécrit sur compteur d'usage local persistant.

**Option 2 — Embarquer la complétion IntelliJ.** Exige la pile V2 ( rejetée, ADR 0004 ) + l'index ( D7 ) + contributors ( D8 tailles : 316 f / 33 550 l. ) — rejeté.

**Option 3 — Améliorer le score scalaire uniquement** ( coefficients ). Gain limité : un seul axe de tri ne peut pas tout encoder ; ne règle ni le recalcul par frappe ni le gel.

**Option « ne rien changer »** : rankScore actuel — repli permanent.

## Critères et mesures

- Écarts structurels documentés ( D8 ) : stats/gel/liftShorter/groupes/trim/recalcul/un-axe.
- **Banc de qualité à créer** ( §4 : recall / top-1 / MRR sur corpus fixe — ADR 0016 ) : cibles proposées ≥ rankScore actuel sur recall, +5 points de top-1/MRR pour valider la greffe.
- Latence : p95 complétion < 100 ms types / < 150 ms membres ( cibles §4 ) ; le re-filtrage local supprime le aller-retour serveur des frappes suivantes.

## Décision proposée

**Option 1**, en deux vagues : (a) chaîne de classement + gel + trim ( API serveur inchangée ) ; (b) frontière RPC ( extension LSP maison ou usage du filtrage client existant de CodeIDE — à spécifier avec l'ADR 0015 ) + statistiques d'usage locales. Le pipeline ecj de collecte ( splice/marqueur ) est **conservé tel quel** ( testé, même invention qu'IntelliJ ).

## Conséquences

- La qualité perçue de la complétion ( ~90 % côté arrangement, D8 ) s'améliore sans toucher à la collecte ni à la fidélité ecj.
- Le contrat client/serveur s'enrichit ( poids + matcher transportés ) : versionner le protocole ( capability LSP ) et prévoir la dégradation gracieuse ( client ancien = tri serveur ).
- Les statistiques d'usage introduisent un fichier d'état utilisateur ( vie privée : local uniquement, documenté ).

## Plan de migration progressive

1. Vague (a) : classeur Kotlin + drapeau `completion.classement-v2` ( double moteur : rankScore conservé pour comparaison au banc ) ; baselines figées avant migration.
2. Vague (b) : protocole riche ( capability `x-codeide.completionRanking` ) + stats locales opt-in ; retour arrière = capability absente ( client retombe sur tri serveur ).
3. Critère de retrait du rankScore : deux versions sans régression au banc.

## Tests de non-régression et critères de sortie

- Banc qualité ( corpus fixe, ADR 0016 ) : recall ≥ baseline ; top-1/MRR en hausse ; latence p95 ≤ baseline ; gel : zéro « saut » de liste sur scénario de frappe enregistré ( test visuel rejouable ).
- Statistiques : désactivables, purgées à la désinstallation ( test de cycle de vie ).

## Risques

- **R1 — Poids entiers transportés mal interprétés par le client** ( « lower = higher priority », piège documenté `RpcCompletionItemWeight.kt:12-19` ) : contrat typé + tests contractuels.
- **R2 — Stats d'usage biaisées par l'historique** : pondération à fenêtre glissante ; reset explicite.
- **R3 — Dérive du banc** ( sur-apprentissage sur le corpus ) : corpus versionné, split train/test.
