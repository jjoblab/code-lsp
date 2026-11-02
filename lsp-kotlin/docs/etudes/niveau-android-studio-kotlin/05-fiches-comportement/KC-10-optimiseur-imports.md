# Fiche KC-10 — Optimiseur d'imports Kotlin

> **Source** : `[DÉRIVÉ DES SOURCES]` — comportements d'Android Studio (plugin
> Kotlin, mode K2) : « Optimize Imports » (tri, retrait des inutilisés,
> doublons, alias, jokers), exposé en LSP par l'action
> `source.organizeImports`. Étude en lecture seule, clean-room.
> **Domaine** : imports / actions
> **Résumé** : une action unique **organise** le bloc d'imports : tri stable,
> retrait des inutilisés **prouvés**, résolution des doublons, **alias
> conservés**, jokers jamais retirés par défaut. **Jamais de suppression si
> la preuve est insuffisante** — un import posé par la complétion pour un
> symbole pas encore inséré est conservé.

## Comportements

### I1 — organisation (tri)
- **Préconditions / entrée** : bloc d'imports en désordre ; action
  `source.organizeImports` (kind `source.organizeImports`).
- **Comportement attendu** : imports triés (ordre stable : préfixes
  regroupés, alphabétique) ; **idempotent** (re-exécution = zéro delta) ;
  édition unique, position du curseur préservée.
- **Statut** : `[À CAPTURER]` — règle de tri exacte de la référence
  (placement des alias et des jokers `*`).

### I2 — retrait des inutilisés
- **Entrée** : `import a.b.Outil` où le nom simple `Outil` n'apparaît
  **nulle part** dans le document (balayage complet du fichier).
- **Attendu** : l'import est retiré.
- **Bornes** : preuve de non-usage **lexicale, document entier** ; si le nom
  apparaît (même encore non résolu), l'import est **conservé** — jamais de
  suppression sur doute.

### I3 — import de complétion non inséré
- **Entrée** : la complétion a posé `import a.b.Outil` (insertion
  anticipée), l'appel correspondant n'a pas encore été inséré au corps.
- **Attendu** : l'optimiseur **conserve** l'import (pas de preuve
  d'inutilité) ; il ne rejoue pas la complétion.

### I4 — doublons
- **Entrée** : deux imports identiques ; deux FQCN différents portant le
  même nom simple.
- **Attendu** : doublon exact **fusionné** ; noms simples en conflit :
  **aucun retrait automatique** (alias à poser côté utilisateur ; jamais de
  réécriture silencieuse).

### I5 — alias et jokers
- **Entrée** : `import a.b.Outil as LegacyOutil` ; `import c.d.*`.
- **Attendu** : alias **conservé tel quel** (jamais développé ni retiré) ;
  joker **conservé** (l'inutilité d'un `*` est indémontrable à coût borné).

### I6 — imports par défaut
- **Entrée** : fichier sans imports `kotlin.*`/`kotlin.jvm.*`.
- **Attendu** : jamais ajoutés, jamais retirés (imports par défaut Kotlin/
  JVM — inutiles par définition).

## Notes pour la réécriture
- ADR 0131 (actions/refactorings — porteur), ADR 0136 (protocole — kind
  `source.organizeImports`), ADR 0126 (imports posés par la complétion).
- Renvoi cahier : fiches 015 (capacités L2) et 019 (formateur) à amender ;
  écarts GK-xxx (matrice, à venir).
