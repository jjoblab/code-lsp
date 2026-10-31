# ADR 0025 — Cohérence des diagnostics (pipeline unifié, inter-fichiers, provenance)

- **Statut** : Proposé
- **Date** : 2026-10-31
- **Écarts résolus** : G-002, G-003, G-023 (`01-audit-moteur.md` §1.4, §1.6, §1.15)

## Contexte

Trois incohérences structurelles : (a) l'équivalence rapide/complet n'est pas prouvée
sur du réel (H6 : le report des problèmes des corps masqués n'est pas re-validé —
un diagnostic périmé peut survivre à un changement d'API utilisé dans un corps) ;
(b) l'invalidation est **mono-fichier** : `B.java` ouvert garde ses diagnostics
périmés quand l'API de `A.java` change (H4 — la republication n'existe qu'au
remplacement de contexte, `ServeurLangageJava.kt:304–323`) ; (c) le type `Diagnostic`
moteur ne porte ni `relatedInformation`, ni `codeDescription`, ni **provenance**
(ecj / inspection / lint — prérequis des ADR 0023/0024). La référence tient ces trois
axes par : carte de régions sales par document (mise à jour locale), annulation/rejeu
des passes, et `HighlightInfo` typé par source (`02-reference…` §A1).

## Sources consultées

- `02-reference-android-studio.md` §A1 (FileStatusMap, dirty scope par bloc,
  annulation/rejeu, dumb-aware), §B14 (garde « ne pas tourner si erreurs bloquantes »).
- `01-audit-moteur.md` §1.4, §1.6, §3.3 (architecture des 3 chemins).
- ADR 0013 (le rouge n'a qu'une source), ADR 0010 (époques copy-on-write — socle
  existant N8 « 0 réponse périmée »), fiches 014/056/059 (contrats actuels).
- Banc P1/P2/P8 livré (les mesures font foi).

## Options

**Option 1 — Pipeline unifié à étages + graphe de dépendances de fichiers
(recommandée).** (a) **Pipeline** : une seule file de publication par document, à
étages ordonnés — repli syntaxe → diagnose (rapide si confinée, complet sinon) →
inspections (ADR 0023) → lint (ADR 0024, si pas d'erreurs bloquantes) — chaque étage
publie **dès qu'elle finit** (progressif, comme la référence), la dernière publication
porte la fusion avec provenance. (b) **Équivalence prouvée** : le chemin rapide
re-valide son report contre un diagnose complet **différé** (l'écart est mesuré par
P1 ; s'il est non nul, le report est corrigé par diff avant publication — le rapide
sert la latence, le complet sert la vérité, l'écart ne vit jamais plus d'un cycle).
(c) **Inter-fichiers** : un **graphe léger de dépendances de fichiers** (arêtes
« B.java référence l'API de A.java », construit sur les imports + résolutions
disponibles, persisté, borné mémoire) : quand l'API publique d'un fichier change
(diff des signatures déclarées), les documents **ouverts** dépendants sont
re-demandés en publication (debounce propre). (d) **Type enrichi** :
`relatedInformation` (ex. « requis par Y » — extension naturelle de l'enrichissement
de hiérarchie fiche 019), `codeDescription`, champ `source` (ecj/inspection/lint).

**Option 2 — Supprimer le chemin rapide.** Rejeté : la latence de frappe est un axe
de la mission (§0.1) ; le rapide est ~8× plus vite (p50 1,5 vs 11,7 ms hérités).

**Option 3 — Republier tout le projet à chaque changement.** Rejeté : coût ART
prohibitif, contraire au dirty scope.

## Critères et mesures

- **P1** : écart rapide/complet = 0 sur N éditions confinées réelles (rejeu
  d'historiques de frappe du corpus) — la propriété devient **CI-bloquante**.
- **P8** : édition d'API dans A → diagnostics de B (ouvert) corrects en < 300 ms
  [cible p95 JVM ; appareil : gabarit 07].
- **P2** : mêmes entrées → mêmes sorties (froid/chaud/rechargé) — CI-bloquante.
- Coût du graphe : construction incrémentale [à mesurer ; cible < 50 ms/100 fichiers],
  empreinte [budget < 5 Mo/1 000 fichiers].

## Décision proposée

**Option 1.** Le graphe de dépendances démarre **minimal** : arêtes par imports +
clés de bindings déjà résolues (matière existante : index de mots, `cleUsages`),
persistance optionnelle ; la re-publication ne vise que les documents **ouverts**
(honnêteté : les fermés sont diagnostiqués à l'ouverture — comportement LSP standard).
Le report périmé du rapide est **re-validé par diff** au cycle complet suivant (le
correctif est structurel, pas un cache-cache : toute divergence P1 devient un bug
ouvert).

## Conséquences

- « 0 diagnostic périmé » devient un invariant testable (P1/P2/P8) — réponse directe
  au « plein d'incohérences » du propriétaire.
- Le type `Diagnostic` s'enrichit : versionner la capability/le protocole maison
  (compatibilité client — ADR 0031).
- La publication progressive multi-étages prépare les inspections/lint sans nouveau
  chantier de publication.

## Plan de migration progressive

1. Type enrichi (provenance/relatedInformation) + étages du publicateur (syntaxe →
   diagnose) — drapeau `diagnostics.pipeline-unifie`.
2. Re-validation du report par diff (P1 passe de « mesure » à « gate »).
3. Graphe de dépendances minimal + re-publication des ouverts (P8).
4. Branchement inspections (0023) puis lint (0024) comme étages.

## Tests de non-régression et critères de sortie

- P1 = P2 = P8 : 0 violation en CI sur corpus réel ; fiches 014/056/059 toujours
  vertes ; latence de frappe p95 ≤ baseline + 10 %.

## Risques

- **R1 — Coût du graphe sur ART** : bornes mémoire + arêtes optionnelles + mesure.
- **R2 — Tempête de re-publications** (cascade) : debounce propre par document +
  profondeur de propagation bornée à 1 dans la v1 (les transitifs passent par le
  cycle suivant).
- **R3 — Régression de latence du rapide** : le diff de re-validation vit sur le
  chemin **différé**, jamais sur le chemin de frappe.
