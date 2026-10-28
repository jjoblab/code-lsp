# ADR 0016 — Banc de qualité et non-régression (baselines versionnées, échec de CI)

- **Statut** : Proposé
- **Date** : 2026-10-28

## Contexte

§4 exige des critères mesurables dont plusieurs sont « à créer » : qualité de complétion ( recall / top-1 / MRR sur corpus fixe ), fidélité des diagnostics ( faux positifs / faux négatifs sur corpus ), empreinte ( APK, méthodes dex, démarrage ). L'ancien dépôt a des latences mesurées ( `LSP_JAVA.md` §4, banc `scripts/lsp-index`, `LatenceCompletionBenchmark` ) mais **pas de banc de qualité de classement** ( constat du prompt §2 ). La stratégie hybride ( ADR 0004 ) multiplie les greffes à comportement perceptible ( classement ADR 0012, code actions 0013, rename 0014 ) : sans baseline versionnée et CI qui **échoue** en cas de régression, chaque vague jouerait la qualité perçue aux dés. R4 : le local attrape le déterministe, la CI porte le lourd ( `STRATEGIE_VERIFICATION.md` de CodeIDE — règle reprise ).

## Sources consultées

- `jjoblab/codeide-lsp` @ `cb809e14` : `docs/LSP_JAVA.md` ( §4 cibles et mesures, W1–W10 ), `docs/LSP_MATRICE_CAPACITES.md`, bancs existants ( `scripts/lsp-index`, `LatenceCompletionBenchmark` ), 449 tests ( 0 échec, 76 ignorés ).
- `jjoblab/CodeIDE` @ `54e99c6` : `docs/STRATEGIE_VERIFICATION.md` ( partage local/CI ).
- Prompt de mission §4 ( critères « à créer » ) et §10 ( acceptation ).
- Étude : `01-architecture-psi.md` ( constats de qualité perçue D8 ), `02-spike-mesures.md` ( méthodologie de mesure et limites — modèle à suivre : mesuré/estimé/non vérifié ).

## Options

**Option 1 — Bancs de qualité versionnés + CI bloquante ( recommandé ).** Quatre bancs, chacun avec corpus fixe versionné dans le dépôt et baseline JSON signée ( commit ) :
- **Qualité de complétion** : corpus de positions de complétion ( types + membres ) sur sources réelles étiquetées ; métriques recall / top-1 / MRR ; cible : recall ≥ baseline, top-1/MRR en hausse pour valider ADR 0012 ( +5 points proposés ).
- **Fidélité des diagnostics** : corpus faux positifs/négatifs ( sources avec erreurs semées, oracle = ecj batch ) ; zéro variation vs baseline pour les rouges ( ADR 0007/0013 ).
- **Comportements des greffes** : quick-fixes ( `WorkspaceEdit` exacts — cas dorés v1 vs v2, ADR 0013 ), rename ( oracle conflits/réparations, ADR 0014 ), latence de frappe ( scénarios rejoués ).
- **Empreinte** : dexage CI + méthodes dex + taille APK ( seuils ADR 0005 : +5 Mo / +30 000 ) + tas par palier de jars ( budgets ADR 0011 ).
Échec de CI = régression ( seuils documentés par banc ) ; toute amélioration = re-baseline par PR dédiée ( diff visible, review obligatoire ).

**Option 2 — Mesures ponctuelles à chaque vague ( manuelles ).** Réintroduit la dérive silencieuse entre vagues — rejeté ( c'est précisément le constat qui fonde cet ADR ).

**Option 3 — Banc tiers ( benchmarks académiques de complétion ).** Corpus non représentatifs du contexte Android/ecj ; licences à vérifier ( R6.2 ) — rejeté au profit d'un corpus maison déterministe.

**Option « ne rien changer »** : latences seules, sans qualité — insuffisant pour arbitrager ADR 0012.

## Critères et mesures

- Méthodologie de mesure honnête ( modèle du spike ) : commande exacte, environnement, brut + incertitude, mesuré/estimé séparés.
- Corpus : sources réelles ( terrain intellij-community filtré par licence Apache-2.0 — données de test, pas code copié ) + fichiers synthétiques déterministes ( générateur à graine, modèle `GenerateurCorpus.kt` du spike ).

## Décision proposée

**Option 1** : les quatre bancs entrent au fur et à mesure des vagues ( ordre : empreinte+latence en vague 0 — CI existe déjà côté CodeIDE à compléter ; complétion avant la vague ADR 0012 ; diagnostics avant ADR 0013 ; greffes avant 0013/0014 ) ; toute baseline est versionnée dans `bancs/baselines/` ( JSON signé du commit ) ; la CI **échoue** sur régression ; l'appareil reste le juge final ( gabarit `spike/appareil/` — mesures à retourner par le propriétaire, jamais simulées ).

## Conséquences

- Chaque PR de greffe porte ses chiffres ; les décisions ADR 0012–0014 s'appuient sur des deltas mesurés, pas sur des impressions.
- Coût de maintenance : corpus à enrichir à chaque nouveau comportement ( discipline de PR ).
- Les 76 tests ignorés de l'ancien moteur sont un signal : la migration des comportements ( 00b §4 ) priorise leur réactivation en spécifications exécutables.

## Plan de migration progressive

1. Vague 0 : infrastructure de bancs ( harnais, format baseline, tâche CI ) + banc empreinte actif sur le statu quo ( chiffres actuels = baseline initiale ).
2. Avant chaque vague de greffe : le banc correspondant existe et la baseline du statu quo est signée ; la greffe doit la battre ou l'égaler.
3. Retour arrière : une régression non explicable = drapeau off + investigation ( le code ancien est toujours vivant ).

## Tests de non-régression et critères de sortie

- Par banc : seuils explicites ( ex. recall ≥ baseline − 0 % ; MRR ≥ baseline + 5 pts pour valider 0012 ; faux +/− : 0 variation ; méthodes dex ≤ +30 000 ; tas ≤ budget par palier ).
- Critère de sortie de la stratégie ( ADR 0004 ) : toutes les vapes passées avec zéro baseline cassée durablement.

## Risques

- **R1 — Sur-apprentissage sur le corpus** : split déterministe train/éval, corpus versionné, enrichissement par PR.
- **R2 — Bruit de mesure ( JVM, GC )** : p50/p95 sur N itérations, échauffement, passes répétées ( modèle spike §9 : variabilité documentée ) ; les seuils intègrent l'incertitude.
- **R3 — Coût CI** : les bancs lourds ( dexage, corpus larges ) en CI nightly, les déterministes à chaque PR.
