# ADR 0023 — Moteur d'inspections et profil de sévérités

- **Statut** : Accepté — **LIVRÉ en première vague le 31/10/2026** (V-C1 :
  contrat + profil + suppressions + les 8 inspections pilotes ; PV dans
  `mesures/2026-10-31-vf2-vc1-vc2.txt` ; reste du catalogue : DFA nullité
  intra-méthode, unused global — après V-C3)
- **Date** : 2026-10-31
- **Écart résolu** : G-010 (`01-audit-moteur.md` §1.5) — exécution de l'option 1b restée en proposition de l'ADR 0013

## Contexte

Le « rouge » vient d'ecj seul et le moteur ne pose que ~10 options compilateur
(`PontEcj.kt:216–236`, preuve H5) : **aucune** inspection IDE — pas de nullité, pas de
code mort, pas de contrôles de style/bogue. La référence expose ≈ 900 inspections Java
(Inspectopedia 2026.2, comptage §A4) sur un contrat simple : visitor non récursif
thread-safe + holder + descriptor + sévérités profil + suppressions
(`@SuppressWarnings(id)` / `//noinspection id`, IDs souvent alignés javac). Le prompt
§7.5 reconduit la règle de l'ADR 0013 : **le rouge n'a qu'une source (ecj)** ; les
inspections ajoutent avertissements/hints, ne remplacent rien. L'analyse de flux de
nullité de la référence est pilotée par les annotations (dont `androidx.annotation` en
tête de sa liste de ~20 paires) et **sépare** les valeurs constantes (positifs sûrs)
du « possible NPE » (option « unsound » off par défaut) — discipline anti-faux-positifs
à copier.

## Sources consultées

- `02-reference-android-studio.md` §A4 (contrat, sévérités, suppressions, DFA de
  nullité, code mort, catalogue priorisé 40 inspections avec besoins marqués
  B/F/I/L/A), §A1 (ordonnancement : inspections après les erreurs, dumb-aware).
- `01-audit-moteur.md` §1.5 (état exact des options ecj), §3.3.
- ADR 0013 (règle « une seule source du rouge » — réaffirmée), ADR 0008 (dumb-aware),
  ADR 0025 (pipeline unifié où les inspections s'insèrent).
- Corpus P3/P4 livrés (vérité terrain des FP/FN).

## Options

**Option 1 — Contrat d'inspection maison + profil + première vague (recommandée).**
(a) **Contrat** : `InspectionMoteur` (id, libellé, groupe, sévérité par défaut,
`dumbAware`) + `visiter(arbre, source, contexte, rapporteur)` — visitor sur AST ecj ;
`rapporteur.signaler(plage, message, gravité, fix?)` ; exécution **après** la diagnose
ecj du fichier (même passe de fond, cf. ADR 0025). (b) **Profil** : sévérités par
défaut modifiables + `@SuppressWarnings`/`//noinspection` avec **nos** IDs alignés sur
les tokens javac quand ils existent (`unchecked`, `deprecation`, `unused`,
`rawtypes`, `serial`). (c) **Catalogue initial** : les inspections marquées **B**
(bindings) et **L** (locales) du tableau §A4 — ecj fournit déjà la matière
(`StringEquality`, `SuspiciousMethodCalls`, `IgnoreResultOfCall` via annotations,
`RedundantCast`…) ; puis **F** : mini-DFA de nullité **intra-méthode** (annotations
`androidx`/`org.jetbrains`/`javax` + `@Contract` simplifié), `UnusedAssignment`
élémentaire ; puis **I** : `unused` global avec l'heuristique documentée de la
référence (membre non privé signalé seulement si son nom est rare dans le projet).

**Option 2 — Porter des inspections de la référence.** Interdit sans décision
propriétaire (R9 — réécriture depuis spécification seulement, avec attributions le
cas échéant) ; coûteux ; rejeté en v1.

**Option 3 — Tout ecj (options compilateur uniquement).** Plafonné : ecj n'a pas
d'équivalent aux inspections de flux/style ; rejeté comme stratégie unique (resterait
en repli partiel).

## Critères et mesures

- Anti-FP : P3 corpus (zéro erreur **ajoutée** par les inspections sur code compilant)
  + option « unsound » off par défaut (discipline référence).
- Valeur : P4 (mutants) — chaque inspection pilote doit attraper au moins un mutant
  de sa famille.
- Coût : latence de la passe d'inspections p95 [cible < +15 ms/fichier JVM au-dessus
  de la diagnose complète] ; dumb-aware vérifié (index simulé en cours).

## Décision proposée

**Option 1.** Le contrat vit dans un module `inspections/` neuf ; la règle ADR 0013
est **conservée mot pour mot** : les inspections ne posent **jamais** de sévérité
Error-sans-ecj — elles ajoutent Warning/Weak Warning/Hint (typés LSP Warning/
Information), marquées `source: "inspection"` dans le pipeline ADR 0025. Ordre du
catalogue : (1) 8 inspections B/L pilotes (String equality, résultat ignoré
`@CheckResult`-like, cast redondant, `equals`/`hashCode` appareillés, taille→isEmpty,
`StaticFieldReferenceOnSubclass`, `WrongPackageStatement`, format string), (2) DFA
nullité intra-méthode + `@Contract`, (3) unused global (heuristique du nom rare).

## Conséquences

- Le moteur gagne la couche « IDE au-dessus du compilateur » sans jamais risquer le
  rouge — la confiance dans ecj est préservée.
- Les quick-fixes d'inspections s'appuient sur ModCommand existant (ADR 0013/N6).
- Dette : le mini-DFA doit rester borné (intra-méthode) — toute extension
  interprocédurale passe par un amendement.

## Plan de migration progressive

1. Contrat + profil + suppressions + 3 inspections pilotes (drapeau
   `inspections.active`).
2. Vague B/L complète (8) + fixes ModCommand.
3. DFA nullité intra-méthode ; puis unused global (index).

## Tests de non-régression et critères de sortie

- Chaque inspection : cas dorés (détection + non-détection + suppression) + P3/P4.
- Zéro inspection en Error ; zéro variation des rouges ecj (banc fidélité).
- Critère de sortie : 8 inspections livrées, FP = 0 sur corpus, latence ≤ cible.

## Risques

- **R1 — FP de nullité** : option unsound off, périmètre intra-méthode, P3 garde-fou.
- **R2 — Bruit (trop d'avertissements)** : profil par défaut conservateur (Weak
  Warning pour le style), réglable.
- **R3 — Latence** : inspections après diagnose, dumb-aware, mesure par vague.
