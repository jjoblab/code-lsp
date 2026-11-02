# Fiche KC-03 — Récepteurs implicites et lambdas à récepteur (Kotlin)

> **Source** : `[DÉRIVÉ DES SOURCES]` — comportements d'Android Studio (plugin
> Kotlin, mode K2) : complétion dans les portées à récepteur implicite
> (`apply`/`with`/`run`/`buildString`, DSL Gradle) et lambdas à paramètre
> implicite `it`. Étude en lecture seule, clean-room.
> **Domaine** : complétion / typage
> **Résumé** : dans un lambda à récepteur (ou à paramètre implicite `it`),
> la complétion place **les membres du récepteur implicite en tête**, puis
> les déclarations de la portée englobante. Récepteur non démontrable →
> **repli honnête sur le genre NOM** ; jamais de membres inventés.

## Comportements

### R1 — `apply`/`with`/`run`/`buildString`
- **Préconditions / entrée** : `val texte = buildString { | }` — récepteur
  implicite `StringBuilder` (FQCN de bibliothèque).
- **Comportement attendu** : membres de `StringBuilder` (`append`,
  `appendLine`, …) en tête ; puis portée englobante ; `this` proposé.
- **Bornes** : récepteur prouvé par l'attente (projet ou FQCN) ; sans
  preuve → genre NOM seul (R5).

### R2 — paramètre implicite `it`
- **Entrée** : `val noms = clients.map { | }` — attente à paramètre unique,
  `it: Client`.
- **Attendu** : `it` et ses membres en tête ; sans attente démontrable à
  paramètre unique → `it` non typé, membres non proposés (silence).
- **Bornes** : lambda paramétré explicitement (`{ c -> | }`) → membres de
  `c`, même mécanisme.

### R3 — récepteurs imbriqués
- **Entrée** : `with(a) { b.apply { | } }` — deux récepteurs implicites
  actifs.
- **Attendu** : membres du récepteur **le plus proche** en tête, puis ceux du
  récepteur externe, puis portée englobante ; étiquettes `this@…` proposées
  en fin de liste.
- **Statut** : `[À CAPTURER]` — ordre exact récepteur externe / portée locale
  dans le classement de la référence.

### R4 — détection du contexte DSL
- **Entrée** : `build.gradle.kts`, bloc `dependencies { | }`.
- **Attendu** : le contexte DSL est détecté **en palier 1** (fichier `.kts`,
  imports et blocs connus lus sur disque) ; les candidats viennent de la
  lecture disque (renvoi KC-12), **pas d'une sync Gradle**.
- **Bornes** : bloc inconnu du disque → repli NOM ; aucune invention.

### R5 — repli honnête
- **Entrée** : lambda dont le récepteur/paramètre n'est pas démontrable
  (attente générique, appelable non résolu, récepteur non typé).
- **Attendu** : genre NOM (noms de la portée) **sans** propositions de
  membres ; genre INCONNU = liste vide (comportement moteur inchangé).

## Notes pour la réécriture
- ADR 0126 (complétion — genres MEMBRE/NOM, priorité au récepteur), ADR
  0123 (typage palier 2 des attentes de lambda), ADR 0130 (Gradle KTS —
  renvoi KC-12), ADR 0122 (paliers).
- Renvoi cahier : fiche 046 (lambda `it`, SAM, paramètre explicite déjà
  testés) — aligner les classements ; écarts GK-xxx (matrice, à venir).
