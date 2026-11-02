# ADR 0125 — Catalogue des diagnostics : taxonomie par décidabilité et extension sous garde de faux positifs

- **Statut** : Proposé
- **Date** : 2026-10-31

## Contexte

L'audit (`../etudes/niveau-android-studio-kotlin/01-audit-moteur.md` §1.2,
§4.3) a déplacé le débat : le moteur émet 10 codes `kt.*` (syntax,
unusedImport, conflictingImport, unresolved, missingReturn, typeMismatch,
argumentCount, unsafeNullable, unreachable, valReassign) sous « preuve ou
silence » (ADR 0107/0112, verdicts RESOLU/MUET/SIGNALABLE) ; le « zéro faux
positif » tenait sur 86 lignes de corpus (KH3). La mesure V0 P1 du 31/10/2026
sur l'auto-corpus (99 fichiers, 34 934 lignes compilables) donne **980 FP =
28,05 FP/1 000 lignes, FN = 0** : `it` 603, imports implicites `kotlin.*`
~100, `this` 48, segments racines ~60, `T` 11, `kt.valReassign` 36
(affectation indexée), `kt.argumentCount` 38, `kt.unsafeNullable` 23.
Écarts visés : GK-002 (FP à l'échelle), GK-020 (catalogue éloigné de la
référence), GK-021 (`@Suppress`/tags/sévérités non servis). Cette ADR
**amende l'ADR 0112** : toute extension du catalogue est conditionnée à une
baseline mesurée.

## Sources consultées

- Audit : `01-audit-moteur.md` §1.2 (KH2/KH3), §4.3 (mesures P1), §5 ;
  référence : `02-reference-android-studio.md` §A3 (codes machine du
  compilateur Kotlin K2, sévérités).
- Fiches : `05-fiches-comportement/KC-09` (nullabilité/smart casts),
  KC-07/KC-08 (rouges Compose/suspend — renvoi ADR 0128).
- Dépôt : ADR 0107, 0112 (amendée), 0119, 0121 ; mission jumelle Java :
  ADR 0030 (seuil FP/1 000 lignes < 0,5 — miroir repris).

## Options

**Option 1 — Geler le catalogue tant que la baseline FP n'est pas
< 0,5/1 000 lignes.** Mesuré : 28,05/1 000 [V0] contre cible < 0,5 [seuil
D5 Java] ; chaque cause racine a un correctif borné et un effet chiffré
(−603 `it`, −100 imports implicites, −48 `this`, −60 segments racines,
−11 `T`, −97 typage). `@Suppress`, tags et `relatedInformation` entrent
AVEC la garde, jamais comme masque (la mesure P1 ignore les suppressions).

**Option 2 — Étendre immédiatement** (doublon d'import, `return` typé
manquant, branches `when`, const réassignée…). Mesuré contre : chaque
famille existante contribue déjà des FP quantifiés — étendre une baseline
cassée multiplie GK-002 ; re-mesure P1 = 36 287 ms/passe [mesuré V0].

**Option 3 — Inspections séparées** (avertissements hors « rouge », canal
SIGNALABLE/Warning). Coût de surface client et volume rendu à mesurer
(V0/gabarit 07) ; bénéfice = soupape pour familles non prouvées. Horizon,
pas première vague.

## Critères et mesures

Taxonomie par décidabilité (contrat de chaque code) :

| Strate | Décidabilité | Codes actuels | Extension éventuelle |
|---|---|---|---|
| A — syntaxe, imports, portée | arbre + disque | syntax, unusedImport, conflictingImport, missingReturn, unreachable, unresolved (preuve d'absence) | doublon d'import, collision de déclaration bornée |
| B — typage borné (ADR 0107) | `TypeValeur = Projet \| Jvm(fqcn)`, smart casts positionnels, `\|\|` annule | typeMismatch, argumentCount, unsafeNullable, valReassign (36 FP mesurés) | `return` typé manquant, branches `when` (après ADR 0123) |
| C — inférence/unification | compilateur Kotlin K2 seul | aucun (jamais en rouge maison) | variance, surcharge, génériques : mode profond différé (ADR 0112) |

Comparaison des options :

| Critère (mesuré) | Opt. 1 gel | Opt. 2 extension | Opt. 3 inspections |
|---|---|---|---|
| FP/1 000 l. [V0 : 28,05 ; cible < 0,5] | corrige d'abord | aggrave (P1 à refaire : 36 s/passe) | sans objet sur le rouge |
| FN [V0 : 0] | gardé (oracle K2 en banc) | non garanti | sans objet |
| Correspondance codes K2 (§A3) | différée au gel levé | immédiate mais fragile | partielle (Warning) |
| Coût dex [26 590/32 768 méthodes, mesuré] | nul | +codes mineurs | +canal à mesurer (V0) |

Correspondance K2 (alias de suppression acceptés) : kt.unresolved ↔
UNRESOLVED_REFERENCE, kt.typeMismatch ↔ TYPE_MISMATCH, kt.argumentCount ↔
ARGUMENT_TYPE_MISMATCH (arité), kt.valReassign ↔ VAL_REASSIGNMENT,
kt.unreachable ↔ UNREACHABLE_CODE. Sévérités : ERREUR en strates A/B
prouvées ; `relatedInformation` vers l'import en cause ou la déclaration ;
tag `Unnecessary` sur unusedImport.

## Décision proposée

**Option 1.** Catalogue gelé à 10 codes jusqu'à la gate **FP < 0,5/1 000
lignes mesuré sur corpus (auto-corpus P1, puis P1(b)/P1(c) corpus externe),
FN = 0 maintenu**. L'extension 10 → N vient ensuite strate A d'abord, chaque
nouveau code exigeant : (a) sa preuve de décidabilité écrite, (b) FP = 0 au
banc P1, (c) FN documenté (sous-rapport honnête), (d) son code K2 + entrée
`@Suppress`. Les suppressions ne comptent pas dans la mesure (garde de
silence = correctif ; suppression = masque).

## Conséquences

- GK-002 devient un chantier chiffré (vague V-A du `04-plan-vagues.md`) :
  huit gardes, effet cumulé attendu −980 FP.
- GK-020 assumé et daté : écart documenté, pas comblé avant la gate.
- GK-021 réglé à la levée du gel : `@Suppress("kt.x")` + alias K2, tags,
  sévérités, `relatedInformation`.
- Strate C interdite au rouge maison (ADR 0107).

## Plan de migration progressive

1. Vague V-A : gardes de silence par cause racine (`it` seulement si
   attente PROJET/SAM prouvée ; imports implicites du langage modélisés ;
   `this`/`T`/segments racines sous garde), re-mesure P1 à chaque garde.
2. Gate FP < 0,5/1 000 (auto-corpus + corpus externe avec manifestes).
3. Extension strate A puis B, code par code, banc obligatoire ; option 3
   réexaminée alors par décision dédiée.

## Tests de non-régression et critères de sortie

- P1 auto-corpus : FP < 0,5/1 000 lignes, FN = 0, suppressions ignorées ;
  P1(b)/P1(c) vertes sur corpus externe.
- Chaque code : fiche KC verte, `@Suppress` et alias K2 effectifs,
  `relatedInformation` exact ; 204 fiches du cahier non régressées (ADR
  0112).

## Risques

- R1 — La gate retarde des attentes : mitigation = option 3 en soupape
  documentée + sous-rapport publié (donnée du banc).
- R2 — Une garde de silence fabrique des FN : mitigation = oracle K2 en
  différentiel (FN = 0 est une gate, pas un vœu).
- R3 — Divergence des codes K2 entre versions : mitigation = veille ADR
  0121, alias tolérants, correspondance testée.
