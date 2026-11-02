# ADR 0104 — Syntaxe : grammaire officielle, incrémentalité, scripts, niveaux de langage

- **Statut** : Proposé
- **Date** : 2026-10-30

## Contexte

Le choix syntaxique conditionne tout le reste (miroir de l'ADR 0006
Java). L'ancien moteur vendore la grammaire officielle
(`compiler/multiplatform-parsing`, tag v2.3.0, Apache-2.0, octet pour
octet, ADR 0115 ancien) derrière une façade `Kt*` ; le spike l'a
ré-obtenue de l'amont et compilée telle quel avec la chaîne du dépôt
[mesuré].

## Sources consultées

- `JetBrains/kotlin` @ `e3806c8f` et tag `v2.3.0` : `compiler/multiplatform-parsing`
  (23 f / 9 585 l. au tag [mesuré]), `license/README.md` (aucune
  exception sur ce chemin [vérifié]), tests amont `NewTestParser.kt`
  (patron d'assemblage, crédité).
- Spike : `02-spike-mesures.md` §5.1 (S2 : 1,8 ms p50, 0 lever sur
  troncatures), `spike-kotlin/src/main/vendor/VENDOR.md`.
- `codeide-lsp` : ADR 0115 (vendoring), ADR 0119 (renonciation
  `languageVersion`), suite de parité (digeste, tolérance).

## Options

**Option 1 — Vendoring amont, octet pour octet, façade maison mince**
(statu quo de la D2 ancienne). **Option 2 — Parseur maison**
(ANTLR/tree-sitter) : une divergence apparaîtrait comme un faux
diagnostic sous du code correct (écartée par l'ADR 0115 ancien —
maintenu). **Option 3 — Parseur du compilateur via artefact** :
`multiplatform-parsing` n'est pas publié (module interne du build
Kotlin [constaté, VENDOR.md amont]) — pas de coordonnée à épingler.
**Option 4 — Ne rien changer** : la grammaire reste dans l'ancien dépôt
(voie D).

## Critères et mesures

- S2 [mesuré] : parse p50 1,8 ms (500 l.) / 8,6 ms (3 000 l.), p95
  33,6 ms ; 0 lever sur 100 troncatures hostiles ; 0 motif ART ;
  grammaire amont v2.3.0 compilée telle quelle (Kotlin 2.2.10/JVM 17).
- Incrémentalité : l'API amont est batch ; le reparse complet coûte
  8,6 ms p50 à 3 000 l. [mesuré] et l'oracle 37,7 ms à chaud
  (façade + session comprises) — l'incrémentalité n'a « pas d'urgence
  mesurée » (miroir du rapport faux positifs §5). La question reste
  ouverte au-delà de ~10 000 lignes [non vérifié].

## Décision proposée

**Option 1** : vendoring amont à un **tag épinglé** (règle : la version
la plus récente compilée par un bootstrap ≤ notre chaîne d'outils —
v2.3.0 aujourd'hui), zéro modification locale, re-sync = remplacement
scripté + suite de parité + digeste comme gate. Façade `Kt*` mince
conservatrice (vocabulaire du compilateur, introuvable = null).
Incrémentalité : **non construite à la vague initiale** — le reparse
complet est mesuré bon marché ; l'étage confort éditeur la ré-évaluera
sur corpus ≥ 10 000 l. Niveaux de langage : renonciation maintenue
(miroir ADR 0119 — le `kt.syntax` sur du 2.4+ est honnête ; la
grammaire monte avec la chaîne d'outils).

## Conséquences

- Re-vendoring 2×/an scripté (ADR 0121) ; la suite de parité est la
  gate de toute montée.
- Les exemptions de qualité du vendoring (explicitApi, couverture) sont
  documentées (miroir ADR 0115.4 ancien).

## Plan de migration progressive

La grammaire entre à la vague 0 par ré-obtention amont (jamais copie de
l'ancien dépôt — R9.2 du prompt) ; la suite de parité est portée du
cahier (fiches 010–013).

## Tests de non-régression et critères de sortie

- Parité : corpus du dépôt sans marqueur d'erreur + digeste SHA-256 +
  tolérance troncature/injection/déletion (fiches cahier 010/011) ;
  tolérance : 0 lever [mesuré au spike].
- Latence : parse p50 ≤ 10 ms à 3 000 lignes (JVM, limite basse).

## Risques

- R1 — Dérive amont (2×/an) : mitigation = tag épinglé + procédure
  ADR 0121.
- R2 — Gros fichiers (> 10 000 l.) : mitigation = mesure de corpus
  élargi au banc (ADR 0119) avant décision d'incrémentalité.
