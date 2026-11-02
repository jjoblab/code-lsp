# 07 — Gabarit de mesure sur appareil (moteur Kotlin « niveau Android Studio »)

> Style `bancs/GABARIT-BASCULE-APPAREIL.md` (R5 : **valeurs réelles, jamais
> simulées** — le gabarit est rempli par le propriétaire sur l'appareil de
> référence). Appareil de référence : **moto g06, Android 15, OpenJDK 17
> aarch64 / ART sous Termux** — même appareil que la passe de bascule et que
> la passe du moteur Java.
>
> Objectif : établir les **budgets p50/p95/p99 appareil du MOTEUR KOTLIN** —
> cibles de vitesse de la mission, miroir du §0.1 de la mission Java
> (**cibles, à valider**) : complétion / hover / signature perçus **< 100 ms
> p95** ; diagnostics de frappe **< 300 ms** ; démarrage à chaud **< 3 s** ;
> régularité **p99** (aucune latence pathologique au 99e centile).

## 1. Rails existants (à réutiliser tels quels)

- [`bancs/GABARIT-BASCULE-APPAREIL.md`](../../../bancs/GABARIT-BASCULE-APPAREIL.md) —
  gabarit de passe appareil déjà éprouvé (passe du 29/10 consignée,
  décision de bascule signée le 30/10) : déclaration d'environnement,
  transcript brut joint, aucune valeur pré-remplie
  [CONFIRMÉ — dépôt, branche etude/moteur-kotlin].
- [`scripts/kit-bascule.sh`](../../../scripts/kit-bascule.sh) — la MÉCANIQUE
  d'assemblage : côté bureau, produit `build/kit-bascule.zip` (jamais
  committé) ; sur l'appareil (Termux, OpenJDK 17 — 17.0.19 constaté au
  29/10 [CONFIRMÉ — bancs/GABARIT-BASCULE-APPAREIL.md §1]) :
  `unzip -o kit-bascule.zip -d bascule && cd bascule && bash banc-bascule.sh > resultats-bascule.txt`.
- **Écart à couvrir pour la présente passe** : le kit actuel assemble la
  passe bascule du MOTEUR JAVA (`:java:benchClasses`) ; la passe Kotlin
  exige le **kit enrichi** du document 11 §2.2 — le moteur `:kotlin` et sa
  pile complète, `kotlin-metadata-jvm` comprise (recommandation consignée,
  mécanique prête) [CONFIRMÉ —
  docs/etudes/moteur-kotlin/11-decisions-proprio-post-cloture.md §2.2].
  Les sections ci-dessous en constituent le cahier des charges de mesure ;
  le présent document n'exige AUCUN changement de code pour exister.

## 2. Conditions de passe et méthode (règle de la mission Java, reprise)

- [ ] Appareil : **moto g06, Android 15, aarch64** — Termux + OpenJDK 17 ;
      noter chargeur / économie d'énergie / autres applications.
- [ ] Charge : mode avion, aucune autre application active.
- [ ] **Chronométrage** : mesures moteur in-process par le banc (des
      **bornes basses du perçu** — le perçu ajoute le debounce hôte et la
      publication) ; le perçu de bout en bout se mesure par enregistrement
      d'écran à 60 i/s lu image par image (≈ 16,7 ms/image).
- [ ] **3 passes espacées de ≥ 30 s** (thermique), **médiane des 3 passes
      retenue** ; chaque cellule de passe porte p50 / p95 intra-passe (p99
      pour la régularité) — la règle du gabarit 07 de la mission Java
      (méthode RECALAGE-2).
- [ ] **Corpus** : le document géant DÉTERMINISTE de la fiche 021 —
      **1 803 lignes, 150 fonctions**, reproductible à l'octet près, miroir
      du `Grand.java` (1 356 l. — gabarit 07 de la mission Java, cf. §5)
      [CONFIRMÉ —
      `kotlin/src/test/kotlin/jo/codeide/lsp/kotlin/session/Fiche021MesuresPerfDiagnosticsTest.kt`
      + document 11 §2.2] ; texte muté à chaque itération (sinon diagnose
      mémoïsé = no-op) ; + corpus de sonde du banc pour F1-F4.
- [ ] Transcript brut joint (jamais retouché) + commit du moteur testé
      (`git rev-parse HEAD`) consignés ci-dessous.

| Champ | Valeur |
|---|---|
| Commit du moteur Kotlin testé | |
| Kit (script, sha256, date d'assemblage) | |
| Transcript brut joint (chemin) | |
| Date/heure des passes (heure appareil) | |

## 3. Mesures exigées — protocole par section

### 3.1 S — Démarrage et indexation
- **S1** : démarrage à **chaud** (caches du moteur présents) jusqu'à la
  première complétion utile (la sonde rend des items exploitables) —
  cible < 3 s (cible, à valider).
- **S2** : démarrage à **froid** (caches vidés) → même point ; le moteur
  Kotlin embarque grammaire + index (ce que seul l'appareil prouve,
  document 11 §2.2).
- **S3** : PENDANT l'indexation : sonde `import ko¦` — nombre d'items
  rendus (dégradation honnête ; miroir du S3 Java).

### 3.2 F — Latences de frappe (le cœur)
- **F1** : complétion de membres (`truc.¦`) — cible < 100 ms p95 (cible, à
  valider).
- **F2** : complétion de noms (préfixe nu, hors point).
- **F3** : aide de signature (ouverture de parenthèse / séparateur) —
  cible < 100 ms p95 (cible, à valider).
- **F4** : hover — cible < 100 ms p95 (cible, à valider).
- **F5** : diagnostics d'édition du document 1 803 l. de la fiche 021,
  texte muté à chaque itération — cible < 300 ms (cible, à valider).
- **F6** : complétion au MILIEU du même document — **régularité p99**
  (figes constatées, latence max).

### 3.3 C — Contention
- **C1** : complétion du fichier A PENDANT le diagnose du gros fichier :
  p95 de A (ms) et facteur vs nominal.
- **C2** : interblocage constaté (jamais attendu — joindre le dump de
  threads si survient).

### 3.4 Q — Qualité inter-fichiers
- **Q1** : édition d'une API dans le fichier A → diagnostics du fichier B
  OUVERT en < 300 ms (cible, à valider) ; 0 diagnostic périmé servi.
- **Q2** : réponses périmées servies pendant reconstruction (0 attendu —
  contrat d'époques du serveur livré).

### 3.5 M — Mémoire et pression
- **M1** : tas moteur au repos après indexation (double `System.gc()`,
  JVM du banc −Xmx2g : composant moteur, pas le produit intégré).
- **M2** : tas après N éditions diagnostiquées (fuite ? delta consigné).
- **M3** : caches libérés sous pression (`onTrimMemory`) — côté HÔTE :
  case vide avec raison si le banc est autonome.

## 4. Tableau de relevé (vide — passe 1/2/3 + médiane)

| # | Mesure | Passe 1 | Passe 2 | Passe 3 | Médiane |
|---|---|---|---|---|---|
| S1 | Démarrage à chaud → 1re complétion utile (ms) | | | | |
| S2 | Démarrage à froid → 1re complétion utile (ms) | | | | |
| S3 | Pendant indexation : `import ko¦` (nb d'items) | | | | |
| F1 | Complétion de membres p50/p95 (ms) | | | | |
| F2 | Complétion de noms p50/p95 (ms) | | | | |
| F3 | Aide de signature p50/p95 (ms) | | | | |
| F4 | Hover p50/p95 (ms) | | | | |
| F5 | Diagnostics 1 803 l. p50/p95 (ms) | | | | |
| F6 | Complétion au milieu du gros fichier p50/p95/**p99** (ms) | | | | |
| C1 | Complétion pendant diagnose p95 (ms) + facteur × | | | | |
| C2 | Interblocage constaté (jamais / dump joint) | | | | |
| Q1 | Édition A → diagnostics de B ouverts (ms) + périmés | | | | |
| Q2 | Réponses périmées servies (0 attendu) | | | | |
| M1 | Tas au repos (Mo) | | | | |
| M2 | Tas après N éditions (Mo + delta) | | | | |
| M3 | `onTrimMemory` respecté (oui/non + niveau) | — (hôte) | | | |

## 5. Étalonnage connu (ordres de grandeur DÉJÀ mesurés)

> Aucun chiffre ci-dessous n'est une mesure du moteur Kotlin sur appareil :
> deux familles de repères attribués à leur source réelle ; ils fixent des
> ordres de grandeur pour la lecture de la passe, rien de plus.

**Moteur JAVA, moto g06, passe 1 du kit de mesure (jouée le 30/10/2026 au
soir, consignée le 31/10)** [CONFIRMÉ — mission Java, worktree
`code-lsp-java`, branche etude/moteur-java, docs/etudes/niveau-android-studio/
07-gabarit-mesure-appareil.md] :

- démarrage à froid → première complétion utile : **≈ 7,6 s** (passe 1) ;
- complétion de membres p95 : **≈ 400-460 ms** (≈ 4× le budget de 100 ms) ;
- diagnostics d'un fichier de 1 356 l. p95 : **≈ 1,15 s** ;
- hover p95 : **≈ 1,2 ms** ;
- facteur **≈ 4×** observé côté moteur Java entre les repères JVM et la
  passe ART moto g06.

**Repères JVM du MOTEUR KOTLIN (fiche 021 — bac à sable, document
déterministe de 1 803 l.)** [CONFIRMÉ —
docs/cahier-specifications-kotlin/fiches/021-MesuresPerfDiagnosticsTest.md +
docs/etudes/moteur-kotlin/11-decisions-proprio-post-cloture.md §2.2] :

- reparse complet du document 1 803 l. : **29 ms** échauffé ;
- diagnostics complets : **135-176 ms** à froid / **20-26 ms** à chaud.

> **Lecture interdite par honnêteté (R5)** : le facteur ≈ 4× observé côté
> Java ne se projette PAS — aucune valeur appareil du moteur Kotlin n'est
> déduite de ces repères ; jusqu'à la première passe réelle, tout chiffre
> appareil Kotlin est **[NON VÉRIFIÉ]**.

## 6. Verdict et décisions (à remplir après la passe)

- [ ] Complétion / hover / signature < 100 ms p95 (F1-F4) : atteint /
      partiel / non — chiffres : ________
- [ ] Diagnostics de frappe < 300 ms (F5) : atteint / partiel / non —
      chiffres : ________
- [ ] Démarrage à chaud < 3 s (S1) : atteint / partiel / non — chiffres : ___
- [ ] Régularité p99 (F6, C1) : figes constatées ? latence max : ________
- [ ] Budgets à recalibrer (nouveaux chiffres) : ________
- [ ] Décision de suite demandée au propriétaire : (i) figer la passe comme
      baseline appareil du moteur Kotlin ; (ii) re-passe après évolution du
      moteur ; (iii) extension éventuelle aux corpus quatre mondes sur
      stockage FUSE (document 11 §2.2).

> **Rappel d'honnêteté (R5)** : aucune valeur simulée — tout chiffre non
> mesuré est consigné **[NON VÉRIFIÉ]** ou laissé vide avec sa raison ;
> une case non mesurable reste vide avec sa raison.
