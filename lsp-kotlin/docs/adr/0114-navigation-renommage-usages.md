# ADR 0114 — Navigation, renommage, usages, hiérarchies

- **Statut** : Proposé
- **Date** : 2026-10-30

## Contexte

K10 : navigation (définition/type/implémentations/super), références,
renommage, hiérarchies. L'ancien moteur a livré un ensemble conservateur
éprouvé : preuve FQCN par imports (typeDefinition/implementation),
occurrences et rename bornés (same-file + multi-fichiers prouvé),
super par phases (ADR 0210), objets du classpath (famille A des retours
utilisateurs).

## Sources consultées

- Cahier : fiches 050 (occurrences/rename — 21), 052 (Super — 10),
  053 (Types — navigation de types — 7), 039 (`ReproductionObjetsClasspathTest`).
- `code-lsp` : ADR 0014 Java (rename par phases — collisions typées,
  réparations, preview ; QueryExecutor sur CIDX).
- `codeide-lsp` : ADR 0204/0206/0210/0218.

## Options

**Option 1 — Parité conservatrice + designs Java en cible.** Reprendre
la navigation par preuve (FQCN, imports du fichier) et le rename borné ;
aligner ensuite le rename v2 par phases et l'usage search indexé sur
l'index des appelables (miroir CIDX Java). **Option 2 — Résolution
complète d'abord** (tout résoudre avant de naviguer) : contredit la
politique de preuve — écarté. **Option 3 — Ne rien changer.**

## Critères et mesures

- Fiches 050/052/053 = contrat de parité exact (homonymes sans preuve
  → vide ; ombrage exclu ; repli déclaration englobante au mot-clé ;
  expect d'abord ; supertype classpath sans source exclu).

## Décision proposée

**Option 1** : parité par preuve d'abord (fiches) ; puis extension
structurée : QueryExecutor sur l'index des appelables pour
`references`/`documentHighlight` exhaustifs (miroir ADR 0014 Java —
l'écart documenté fiche 054 Java trouve son équivalent Kotlin à
consigner), rename par phases avec collisions typées derrière drapeau.

## Conséquences

- La navigation reste prouvable (jamais d'homonymie résolue par
  devinette) ; les extensions sont des gains mesurés au banc commun.

## Plan de migration progressive

Étage navigation après complétion (miroir N4 Java) ; rename v2 en
étage suivant (miroir N5).

## Tests de non-régression et critères de sortie

- Fiches 050/052/053 vertes ; pour l'extension : rappel usages =
  oracle ; 0 conflit manqué ; p95 usages < 100 ms.

## Risques

- R1 — Rename multi-fichiers complexifié par les scopes : mitigation =
  phases + preview (design Java éprouvé).
