# ADR 0115 — Formatage, imports, pliage, actions de code

- **Statut** : Proposé
- **Date** : 2026-10-30

## Contexte

K9/K10 : le confort éditeur. L'ancien moteur a livré : formateur BORNÉ
(ré-indentation seule, ADR 0202 — intouchables chaînes brutes/KDoc,
refus sur arbre en erreur, idempotence), organisation d'imports
(quick-fix par FQN triée), pliage (une plage par paire d'accolades),
correctifs d'expression (ADR 0232-like : `?.`/`!!`/entourage nullité/
changement de type — matrice fermée).

## Sources consultées

- Cahier : fiches 014 (pliage), 016 (formateur borné), 020
  (correctifs texte), 019 (membres manquants), 047 (importsCandidats).
- `code-lsp` : ADR 0013 Java (ModCommand — CodeActionService v2
  déclaratif sérialisable : le modèle direct d'un CodeAction LSP).
- `codeide-lsp` : ADR 0202/0229/0232.

## Options

**Option 1 — Parité des contrats bornés + ModCommand en cible pour
les actions de code.** Formatage borné et pliage repris tels quels ;
les quick-fixes migrent vers un modèle déclaratif sérialisable
(miroir ModCommand Java) au lieu d'édits impératifs ad hoc.
**Option 2 — Formateur complet** (espaces canoniques, alignements) :
exige des choix de style non prouvables — risque de « casser ce qu'on
ne comprend pas » (motif de l'ADR 0202) — écarté.
**Option 3 — Ne rien changer.**

## Critères et mesures

- Fiches 014/016/019/020 : les contrats bornés sontéprouvés
  (idempotence, plages, matrice fermée de conversions, re-preuve à la
  requête — jamais d'action périmée).

## Décision proposée

**Option 1** : formatage borné (ré-indentation seule) et pliage
conservés à l'identique (contrats ADR 0202) ; imports et membres
manquants repris en parité ; les correctifs évoluent vers le modèle
déclaratif (plan sérialisable calculé en lecture, preview, exécution
déléguée au client — miroir ModCommand) à l'étage actions de code.

## Conséquences

- Aucun risque de réécriture de contenu (le risque n° 1 d'un
  formateur) ; les actions deviennent prévisualisables et testables
  par cas dorés (WorkspaceEdit exacts).

## Plan de migration progressive

Parité confort éditeur (miroir N7 Java) ; ModCommand-like ensuite.

## Tests de non-régression et critères de sortie

- Fiches 014/016/019/020 vertes ; cas dorés des WorkspaceEdit exacts
  au banc.

## Risques

- R1 — Impatience « formateur complet » : mitigation = toute extension
  exige une décision dédiée (précédent ADR 0202 maintenu).
