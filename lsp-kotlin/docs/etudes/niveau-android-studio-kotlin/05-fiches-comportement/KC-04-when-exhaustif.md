# Fiche KC-04 — `when` exhaustif (enum et sealed)

> **Source** : `[DÉRIVÉ DES SOURCES]` — comportements d'Android Studio (plugin
> Kotlin, mode K2) : complétion des branches restantes, quick-fix « ajouter
> les branches manquantes », diagnostic d'inexhaustivité. Étude en lecture
> seule, clean-room.
> **Domaine** : complétion / diagnostics
> **Résumé** : sur un `when` dont le sujet est un enum ou une hiérarchie
> sealed, la complétion propose **les constantes/sous-classes non encore
> citées en tête** ; un quick-fix génère les branches manquantes (stubs) ;
> le diagnostic d'inexhaustivité est **cible** (pas jour 1), émis seulement
> si l'ensemble des cas est **prouvé**.

## Comportements

### W1 — complétion des branches restantes (enum)
- **Préconditions / entrée** : `enum class Couleur { ROUGE, VERT, BLEU }` ;
  `when (c) { ROUGE -> afficher(); | }`.
- **Comportement attendu** : `VERT` et `BLEU` (non citées) **en tête** ;
  `ROUGE` présente mais déclassée ; autres genres (NOM, membres) derrière.
- **Bornes** : genre CONSTANTES_ENUM ; l'enum vient du projet ou du classpath
  lu (FQCN) ; constantes non lisibles → non proposées.

### W2 — sealed
- **Entrée** : `sealed class Forme` avec sous-classes du **projet** ;
  `when (f) { | }`.
- **Attendu** : sous-classes non citées en tête, même classement que W1.
- **Bornes** : seules les sous-classes **lisibles** (projet, ou déclarations
  bornées du classpath) comptent ; ensemble incomplet → aucune promesse
  d'exhaustivité (cf. W4/W5).

### W3 — quick-fix « ajouter les branches manquantes »
- **Entrée** : `when` incomplet sur sujet enum/sealed prouvé ; action
  demandée sur le mot-clé `when` ou l'accolade fermante.
- **Attendu** : **une** action génère toutes les branches manquantes, chacune
  avec un corps stub (`TODO()` neutre) ; édition unique ; kind `quickfix`
  quand liée au diagnostic W4, `refactor` sinon.
- **Statut** : `[À CAPTURER]` — corps stub exact et position du curseur
  après génération dans la référence.

### W4 — diagnostic d'inexhaustivité (cible)
- **Entrée** : `when` utilisé **comme expression**, sans `else`, avec des
  branches manquantes sur sujet enum/sealed dont l'ensemble est prouvé.
- **Attendu** : diagnostic `kt.*` (code à arbitrer au catalogue) ; sévérité
  alignée sur la référence (erreur en expression).
- **Bornes** : **cible, pas jour 1** — livré quand la preuve d'ensemble est
  fiable ; `when` **instruction** : silence par défaut (preuve ou silence),
  à trancher au banc avec l'oracle K2.

### W5 — preuve ou silence
- **Entrée** : sujet non résolu, hiérarchie sealed partiellement lue, ou
  `else` présent.
- **Attendu** : **aucun** diagnostic d'inexhaustivité ni suggestion de
  branches (pas de fausse énumération, pas de tri sur ensemble incomplet).

## Notes pour la réécriture
- ADR 0125 (catalogue diagnostics — nouveau code `kt.*`, sévérités à
  arbitrer), ADR 0126 (complétion — genre CONSTANTES_ENUM, déclassement des
  citées), ADR 0123 (typage palier 2 du sujet).
- Oracle K2 (sous-processus) pour arbitrer instruction vs expression ;
  écarts GK-xxx (matrice, à venir).
