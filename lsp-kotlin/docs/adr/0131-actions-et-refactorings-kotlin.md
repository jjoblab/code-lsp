# ADR 0131 — Actions et refactorings Kotlin : catalogue progressif source → quickfix → refactor

- **Statut** : Proposé
- **Date** : 2026-10-31

## Contexte

L'audit (§1.7, KH7) : les actions de code se réduisent à trois familles
de quick-fixes ; le genre `source` n'est jamais produit ; les
refactorings sont absents (hors rename multi-fichiers `.kt`/`.kts`
ferroviaire, ADR 0114, occurrences par re-parse paresseux — 8,6 ms p50
sur 3 000 l. [mesuré]). La référence (`02-reference-android-studio.md`
§A4 intentions/quick-fixes, §A5 refactorings) sert un catalogue riche
derrière Alt+Entrée. Les fiches KC-10 (optimiseur d'imports) et KC-11
(intentions prioritaires) fixent les comportements ; LSP offre les
kinds `quickfix`/`source`/`refactor`, `codeAction/resolve` et les
`WorkspaceEdit` multi-fichiers. Écarts visés : GK-022 (organizeImports),
GK-023 (intentions sans diagnostic), GK-024 (refactorings). Cette ADR
**amende l'ADR 0115** (cible déclarative ModCommand-like) en la
séquençant par coûts de preuve croissants.

## Sources consultées

- Référence : `02-reference-android-studio.md` §A4 (intentions,
  prévisualisation, kinds), §A5 (refactorings et gardes), §A6.
- Fiches : `05-fiches-comportement/KC-10` (I1–I6), KC-11 (Q1–Q5),
  KC-04 (branches `when`), KC-09 (smart casts pour « supprimer !! »).
- Dépôt : ADR 0115 (amendée), 0114, 0123, 0125, 0136 ; mission jumelle
  Java : ADR 0027 (actions source./refactor. — miroir) ; audit §3.8.

## Options

**Option 1 — Progressif source → quickfix → refactor**, ordonné par
coût de PREUVE (jamais par attrait) : (1) organizeImports (kind
`source.organizeImports`, KC-10) — preuve lexicale sur document entier,
aucune dépendance au typage, la MOINS chère ; (2) intentions sans
diagnostic (KC-11) : expression↔block body, `if`↔`when`, gabarit de
chaîne (arbre seul) ; `?.let`, type de retour explicite, suppression de
`!!` (gardes de typage/smart cast, ADR 0107/0123) ; (3) quick-fixes
élargis : import, branches `when` manquantes (KC-04) ; (4) refactorings
bornés : extraire variable (intra-fonction), extraire fonction LOCALE
au document, inline simple, safe delete par usages prouvés. Mesuré :
organizeImports = un re-parse paresseux (8,6 ms p50/3 000 l. [mesuré
JVM] ; appareil à mesurer, cible < 300 ms D5) ; chaque action exige son
motif PROUVÉ (KC-11 Q2 : motif non prouvable → action absente).

**Option 2 — Tout le catalogue d'un coup.** Contre-mesuré : les
dépendances ne sont pas prouvées aujourd'hui — « type de retour
explicite » exige le palier 2 (ADR 0123), les extractions exigent la
capture des identifiants libres (non mesurée), safe delete exige des
usages prouvés multi-fichiers (coût à mesurer, gabarit 07) ; livrer
sans preuve viole KC-11 Q2 et fabrique des actions sur information
perdue (leçon V0 : 980 FP pour cause racine identique).

**Option 3 — Statu quo (quickfix seul).** Coût mesuré : GK-022/023/024
restent ouverts ; le genre `source` jamais produit reste un mensonge de
la matrice de capacités (annonces honnêtes, ADR 0118) ; le menu
Alt+Entrée de la référence (§A4) reste hors d'atteinte.

## Critères et mesures

| Vague | Action | Kind LSP | Preuve exigée | Coût |
|---|---|---|---|---|
| 1 | organizeImports (KC-10 I1–I6) | `source.organizeImports` | lexicale, document entier ; jamais de suppression sur doute | 8,6 ms p50/3 000 l. [mesuré] ; appareil à mesurer (gabarit 07) |
| 2 | expression↔block, `if`↔`when`, gabarit de chaîne | `refactor.rewrite` | arbre seul | idempotence testée |
| 2 | `?.let`, type explicite, supprimer `!!` | `refactor`/`quickfix` | typage/smart cast prouvé (ADR 0107/0123) | après palier 2 |
| 3 | quick-fix import, branches `when` | `quickfix` | candidats/couverture prouvés | après ADR 0123/0125 |
| 4 | extraire variable/fonction (locale document), inline | `refactor.extract` | identifiants libres bornés | à mesurer (V0) |
| 4 | safe delete | `refactor` | usages PROUVÉS (re-parse paresseux) | multi-fichiers à mesurer (gabarit 07) |

Mécanique transverse : `codeAction/resolve` (titre seul dans la liste,
édits résolus à la demande — latence/mémoire à mesurer, gabarit 07) ;
`WorkspaceEdit` multi-fichiers DÉCLARATIF (plan sérialisable calculé
en lecture, exécution déléguée au client — réutilisation directe de
l'ADR 0115) ; prévisualisation texte avant/après (KC-11 Q3), jamais
d'aperçu exécuté ; kinds honnêtes.

## Décision proposée

**Option 1.** Vague 1 : organizeImports (ferme GK-022 ; le genre
`source` devient enfin produit — matrice honnête). Vague 2 : intentions
arbre seul, puis intentions typées après ADR 0123. Vague 3 :
quick-fixes élargis coordonnés au catalogue ADR 0125. Vague 4 :
refactorings bornés document-locaux, puis safe delete par usages
prouvés uniquement. Tout passe par le modèle déclaratif de l'ADR 0115,
avec `codeAction/resolve` et WorkspaceEdit exacts.

## Conséquences

- La matrice de capacités gère une évolution ANNONCÉE : `source.*`
  servi dès la vague 1 (amende des annonces, ADR 0118/0136).
- Les refactorings multi-fichiers ne dépassent JAMAIS la preuve : hors
  usages prouvés → action absente (GK-024 se referme partiellement et
  honnêtement).
- Le rename ferroviaire existant (ADR 0114) reste le modèle de sûreté
  des éditions multi-fichiers.

## Plan de migration progressive

1. organizeImports + kind `source` + cas dorés WorkspaceEdit (ADR 0115).
2. Intentions palier 1 (arbre seul) + prévisualisation texte.
3. `codeAction/resolve` + intentions/quick-fixes palier 2 (ADR 0123).
4. Extractions document-locales, inline, puis safe delete prouvé.

## Tests de non-régression et critères de sortie

- Fiches KC-10 (I1–I6) et KC-11 (Q1–Q5) vertes ; fiches 014/016/019/020
  du cahier non régressées (ADR 0115).
- Idempotence organizeImports (re-exécution = zéro delta) ;
  conservation des imports anticipés (KC-10 I3) ; alias/jokers jamais
  réécrits silencieusement (I4/I5).
- Aucune action sur motif non prouvé (propriété testée) ; WorkspaceEdit
  multi-fichiers exacts sur cas dorés ; latences appareil dans les
  budgets D5 (gabarit 07).

## Risques

- R1 — Dérive vers du refactor non prouvé (pression « comme Android
  Studio ») : mitigation = vague 4 gardée par usages prouvés, extension
  par décision écrite (précédent ADR 0115 R1).
- R2 — `codeAction/resolve` non consommé par le client : mitigation =
  repli édits inline dans la réponse initiale + capability versionnée
  (ADR 0136).
- R3 — Coût des extractions sous-estimé (capture d'identifiants) :
  mitigation = mesure V0 avant la vague 4, abandon documenté si le
  coût dépasse le gain mesuré.
