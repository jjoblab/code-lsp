# Fiche C-10 — `references` multi-fichiers (levée de la parité stricte)

> **Source** : `[DÉRIVÉ DES SOURCES]` — `02-reference-android-studio.md` §A5
> (find usages project-wide) ; état moteur `01-audit-moteur.md` §1.8 (le moteur est
> déjà exhaustif, seul le filtre protocolaire confine — preuve citée).
> **Domaine** : navigation / usages
> **Résumé** : `textDocument/references` rend **tous** les usages du symbole sur
> les racines du projet (index de mots + clé de binding), pas seulement ceux du
> document courant. `documentHighlight` reste intra-document (LSP) mais **type**
> ses occurrences (lecture/écriture/déclaration).

## Comportements

### M1 — usages inter-fichiers
- **Préconditions / entrée** : `Utilitaire.java` déclare `static void repeter(...)`,
  appelé depuis `Main.java` et `Autre.java` (tous deux **fermés**) ; requête
  `references` sur la déclaration.
- **Comportement attendu** : les usages des **trois** fichiers sont rendus, triés
  (chemin, offset) ; `includeDeclaration=false` exclut la déclaration.
- **Bornes** : rappel = oracle (`referencesParBalayageComplet`) — gate CI
  existante.

### M2 — plafond honnête
- **Entrée** : symbole très utilisé (> seuil).
- **Attendu** : liste complète quand même (le banc mesure la latence) ; si un
  plafond s'avère nécessaire sur ART, il est **annoncé** dans la réponse
  (métadonnée de total) — jamais de troncature silencieuse.

### M3 — documentHighlight typé
- **Entrée** : variable locale lue puis affectée.
- **Attendu** : occurrences typées Lecture / Écriture / Déclaration (le client
  applique des styles distincts).

### M4 — groupement
- **Attendu** : les usages portent une métadonnée de groupement par fichier
  (le rendu arborescent est client).

## Notes pour la réécriture
- **Amendement cahier fiche 054** (l'écart du vendord n'est plus reproduit) et
  inversion de la gate « taille attendue = 1 » — décision D1 demandée au
  propriétaire ; coût moteur ~0,5 j-h (ADR 0028).
