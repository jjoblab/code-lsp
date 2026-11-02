# PLAN-MIGRATION — arrivée du futur LSP XML dans le dépôt code-lsp

> Emplacement réservé créé en Phase 2 de réorganisation
> (docs/REORGANISATION.md §2 et §6.2 — aucun code, aucun module déclaré).
> Ce document fixe le cadre d'accueil ; il ne préjuge d'aucune décision
> d'architecture qui appartiendra à ses propres ADR.

## 1. Ce qui existe déjà

- La **section** `lsp-xml/` dans l'arborescence cible (ce répertoire).
- Une **réservation commentée** dans `settings.gradle.kts` :

  ```kotlin
  // Réservation LSP XML (aucun code — cf. lsp-xml/docs/PLAN-MIGRATION.md) :
  // include(":xml"); project(":xml").projectDir = file("lsp-xml/moteur")
  ```

- Le **socle partagé** (`socle/index`, `socle/model`, `socle/testing`,
  `socle/ecj-art`) et les deux moteurs aînés (`lsp-java/`, `lsp-kotlin/`)
  comme références de structure et de méthode.

## 2. Standards d'entrée (hérités du prompt de mission, §7.3)

1. **Registre de provenance dédié** (`lsp-xml/docs/PROVENANCE-REPRISE-XML.md`)
   ouvert AVANT toute reprise de fichier d'un ancien dépôt (R9.2 —
   inscription préalable, fichier par fichier, jamais « en bloc »).
2. **Cahier de spécifications** (`lsp-xml/docs/cahier-specifications-xml/`)
   extrait des tests de l'existant avant toute réécriture — le contrat
   prime sur le code (cf. AGENTS.md).
3. **ADR fondatrices** : périmètre, voie de réécriture (clean-room intégrale
   ou voie hybride), gouvernance — miroir des ADR 0001–0002 (Java) et
   0100–0101 (Kotlin).
4. **Gabarit de passe bascule appareil** commun
   (`docs/GABARIT-BASCULE-APPAREIL.md`) — toute passe sur appareil suit le
   gabarit et joint ses mesures à la décision.
5. **Bancs et baselines signées** dès la première capacité mesurable,
   sur le modèle des bancs Java/Kotlin (baseline JSON signée + digeste).

## 3. Étapes d'arrivée (ordre indicatif)

1. Décommenter la réservation `:xml` dans `settings.gradle.kts` —
   uniquement quand `lsp-xml/moteur/` contient un `build.gradle.kts` réel
   (un `include` sans module casse la configuration Gradle).
2. Ouvrir les ADR fondatrices + le registre de provenance (fichiers vides
   acceptés, l'inscription prévaut sur la copie).
3. Extraire le cahier de spécifications depuis l'existant (oracle en boîte
   noire, lecture seule).
4. Premier socle de tests de parité sur corpus XML réel (Android layouts,
   manifests — le corpus existe déjà dans les bancs Android des aînés).
5. Premier bump de version et publication selon la voie choisie.

## 4. Ce qui ne changera pas

- Les chemins des autres LSP, les coordonnées publiées, les capacités LSP
  existantes (contractuel pour l'hôte CodeIDE — docs/REORGANISATION.md §1).
- La CI existante : le job global reste l'arbitre ; des jobs dédiés XML
  suivront le même modèle que les jobs Kotlin (noms stables, §P-4).
