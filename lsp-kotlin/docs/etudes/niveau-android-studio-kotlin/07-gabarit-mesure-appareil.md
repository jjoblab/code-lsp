# 07 — Gabarit de mesure sur appareil (moteur Kotlin « niveau Android Studio »)

> Style `docs/GABARIT-BASCULE-APPAREIL.md` (R5 : **valeurs réelles, jamais
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

- [`docs/GABARIT-BASCULE-APPAREIL.md`](../../../../docs/GABARIT-BASCULE-APPAREIL.md) —
  gabarit de passe appareil déjà éprouvé (passe du 29/10 consignée,
  décision de bascule signée le 30/10) : déclaration d'environnement,
  transcript brut joint, aucune valeur pré-remplie
  [CONFIRMÉ — dépôt, branche etude/moteur-kotlin].
- [`lsp-java/scripts/kit-bascule.sh`](../../../../lsp-java/scripts/kit-bascule.sh) — la MÉCANIQUE
  d'assemblage : côté bureau, produit `build/kit-bascule.zip` (jamais
  committé) ; sur l'appareil (Termux, OpenJDK 17 — 17.0.19 constaté au
  29/10 [CONFIRMÉ — docs/GABARIT-BASCULE-APPAREIL.md §1]) :
  `unzip -o kit-bascule.zip -d bascule && cd bascule && bash banc-bascule.sh > resultats-bascule.txt`.
- **Écart à couvrir pour la présente passe** : le kit actuel assemble la
  passe bascule du MOTEUR JAVA (`:java:benchClasses`) ; la passe Kotlin
  exige le **kit enrichi** du document 11 §2.2 — le moteur `:kotlin` et sa
  pile complète, `kotlin-metadata-jvm` comprise (recommandation consignée,
  mécanique prête) [CONFIRMÉ —
  lsp-kotlin/docs/etudes/moteur-kotlin/11-decisions-proprio-post-cloture.md §2.2].
  Les sections ci-dessous en constituent le cahier des charges de mesure ;
  le présent document n'exige AUCUN changement de code pour exister.

## 2. Conditions de passe et méthode (règle de la mission Java, reprise)

- [ ] Appareil : **moto g06, Android 15, aarch64** — Termux + OpenJDK 17 ;
      noter chargeur / économie d'énergie / autres applications.
      **[PASSÉ 1/3 le 12/11/2026 21:59 heure bac — confirmé par le
      transcript : `moto g06`, Android 15, OpenJDK 17.0.19 aarch64.]**
- [ ] Charge : mode avion, aucune autre application active.
      **[Passe 1 : non confirmé par le transcript — à consigner par
      l'opérateur aux passes suivantes.]**
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
      `lsp-kotlin/moteur/src/test/kotlin/jo/codeide/lsp/kotlin/session/Fiche021MesuresPerfDiagnosticsTest.kt`
      + document 11 §2.2] ; texte muté à chaque itération (sinon diagnose
      mémoïsé = no-op) ; + corpus de sonde du banc pour F1-F4.
- [x] Transcript brut joint (jamais retouché) + commit du moteur testé
      (`git rev-parse HEAD`) consignés ci-dessous.
      **[Passe 1 : transcript joint — `mesures/2026-11-12-vh-passe1-transcript.txt`,
      reçu brut de l'appareil, aucune retouche.]**

| Champ | Valeur |
|---|---|
| Commit du moteur Kotlin testé | **b0bb9ce** (consigné par le kit à l'assemblage — `build/kit-mesure-kotlin.commit`, commit pushé le 12/11/2026) |
| Kit (script, sha256, date d'assemblage) | `lsp-kotlin/scripts/kit-mesure-kotlin.sh` · archive `ac10835c9d216b75a8d6d5065680041be46f5b1d482681e4d1f69a8c2749a8f5` (`kit-mesure-kotlin.zip`, 57 Mo) · assemblée le 12/11/2026 |
| Transcript brut joint (chemin) | `mesures/2026-11-12-vh-passe1-transcript.txt` (passe 1) |
| Date/heure des passes (heure appareil) | **Passe 1 : 12/11/2026 16:59:24** (heure appareil — en-tête du script ; l'horodatage interne du banc dit 21:59:27, écart d'échelle d'horloge bac/appareil consigné tel quel) |

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

## 4. Tableau de relevé (passe 1/2/3 + médiane)

| # | Mesure | Passe 1 | Passe 2 | Passe 3 | Médiane |
|---|---|---|---|---|---|
| S1 | Démarrage à chaud → 1re complétion utile (ms) | **212** | | | |
| S2 | Démarrage à froid → 1re complétion utile (ms) | **4 891** | | | |
| S3 | Pendant indexation : `import ko¦` (nb d'items) | **5 / 5** (pendant / après) | | | |
| F1 | Complétion de membres p50/p95 (ms) | **3 / 9** | | | |
| F2 | Complétion de noms p50/p95 (ms) | **4 / 13** | | | |
| F3 | Aide de signature p50/p95 (ms) | **1 / 4** | | | |
| F4 | Hover p50/p95 (ms) | **0 / 2** | | | |
| F5 | Diagnostics 1 803 l. p50/p95 (ms) | **1 346 / 1 531** | | | |
| F6 | Complétion au milieu du gros fichier p50/p95/**p99** (ms) | **22 / 32 / 34** | | | |
| C1 | Complétion pendant diagnose p95 (ms) + facteur × | **6 + ×1,00** (nominal p50 3 ms) | | | |
| C2 | Interblocage constaté (jamais / dump joint) | **non constaté** (1 diagnostic rendu) | | | |
| Q1 | Édition A → diagnostics de B ouverts (ms) + périmés | **97 + 0 périmé** (rouge sur « cible » : OUI) | | | |
| Q2 | Réponses périmées servies (0 attendu) | **0** | | | |
| M1 | Tas au repos (Mo) | **8** | | | |
| M2 | Tas après N éditions (Mo + delta) | **12 + 4** (50 éditions) | | | |
| M3 | `onTrimMemory` respecté (oui/non + niveau) | — (hôte) | | | |

> **Lecture de la passe 1** (détail chiffré : `mesures/2026-11-12-vh-passe1-lecture.md`) :
> chemin de frappe **VERT** — F1-F4 tenus avec 7 à 50× de marge (p95 ≤ 13 ms
> contre 100), S1 tenu ×14 (212 ms contre 3 000), régularité saine (p99 34 ms,
> aucune fige), contention nulle (×1,00), zéro périmé ; **deux écarts** :
> F5 (diagnostics du géant 1 531 ms p95 = 5,1× le seuil de 300 ms) et la passe
> V-H §7 (H1/H2 manqués — voir §7). Les passes 2-3 restent à jouer pour la
> médiane ; la lecture des seuils n'est pas susceptible d'inversion par la
> médiane (les écarts sont ×5 à ×13,6, les marges ×7 à ×50).

## 5. Étalonnage connu (ordres de grandeur DÉJÀ mesurés)

> Aucun chiffre ci-dessous n'est une mesure du moteur Kotlin sur appareil :
> deux familles de repères attribués à leur source réelle ; ils fixent des
> ordres de grandeur pour la lecture de la passe, rien de plus.

**Moteur JAVA, moto g06, passe 1 du kit de mesure (jouée le 30/10/2026 au
soir, consignée le 31/10)** [CONFIRMÉ — mission Java, worktree
`code-lsp-java`, branche etude/moteur-java, lsp-java/docs/etudes/niveau-android-studio/
07-gabarit-mesure-appareil.md] :

- démarrage à froid → première complétion utile : **≈ 7,6 s** (passe 1) ;
- complétion de membres p95 : **≈ 400-460 ms** (≈ 4× le budget de 100 ms) ;
- diagnostics d'un fichier de 1 356 l. p95 : **≈ 1,15 s** ;
- hover p95 : **≈ 1,2 ms** ;
- facteur **≈ 4×** observé côté moteur Java entre les repères JVM et la
  passe ART moto g06.

**Repères JVM du MOTEUR KOTLIN (fiche 021 — bac à sable, document
déterministe de 1 803 l.)** [CONFIRMÉ —
lsp-kotlin/docs/cahier-specifications-kotlin/fiches/021-MesuresPerfDiagnosticsTest.md +
lsp-kotlin/docs/etudes/moteur-kotlin/11-decisions-proprio-post-cloture.md §2.2] :

- reparse complet du document 1 803 l. : **29 ms** échauffé ;
- diagnostics complets : **135-176 ms** à froid / **20-26 ms** à chaud.

> **Lecture interdite par honnêteté (R5)** : le facteur ≈ 4× observé côté
> Java ne se projette PAS — aucune valeur appareil du moteur Kotlin n'est
> déduite de ces repères ; jusqu'à la première passe réelle, tout chiffre
> appareil Kotlin est **[NON VÉRIFIÉ]**.

## 6. Verdict et décisions (à remplir après la passe)

> **Passe 1 du 12/11/2026 cochée ci-dessous** — les cases restent
> ouvertes aux passes 2-3 (médiane), mais la lecture n'est pas
> susceptible d'inversion (les écarts sont ×5,1 à ×13,6 ; les marges
> ×7 à ×50).

- [x] Complétion / hover / signature < 100 ms p95 (F1-F4) : **atteint**
      (passe 1) — chiffres : F1 9 · F2 13 · F3 4 · F4 2 ms p95
      (marges ×7 à ×50).
- [x] Diagnostics de frappe < 300 ms (F5) : **non** (passe 1) —
      chiffres : 1 346 / 1 531 ms p50/p95 sur le document 1 803 l.
      (5,1× le seuil ; le diagnostic des documents de TRAVAIL — Q1 à
      97 ms — est tenu : l'écart est propre au document géant).
- [x] Démarrage à chaud < 3 s (S1) : **atteint** (passe 1) — chiffres :
      212 ms (×14 de marge ; à froid : 4 891 ms).
- [x] Régularité p99 (F6, C1) : figes constatées ? **aucune** — latence
      max : 34 ms (F6 p99 34 ms ; C1 facteur ×1,00, nominal 3 ms) ;
- [ ] Budgets à recalibrer (nouveaux chiffres) : **proposition passe 1**
      (à trancher avec les passes 2-3) : F5 géant 300 ms → **~1 600 ms**
      (ou recalibrer le CORPUS : le géant 1 803 l. est un cas d'extrême,
      pas la frappe courante) ;
- [ ] Décision de suite demandée au propriétaire : (i) figer la passe comme
      baseline appareil du moteur Kotlin ; (ii) re-passe après évolution du
      moteur ; (iii) extension éventuelle aux corpus quatre mondes sur
      stockage FUSE (document 11 §2.2).
      **[Proposition passe 1 : (i) FIGER cette passe comme baseline
      appareil — le chemin de frappe est vert, la baseline existe ; la
      décision V-H (§7) se prend séparément.]**

> **Rappel d'honnêteté (R5)** : aucune valeur simulée — tout chiffre non
> mesuré est consigné **[NON VÉRIFIÉ]** ou laissé vide avec sa raison ;
> une case non mesurable reste vide avec sa raison.

## 7. Passe V-H — mode profond K2 (ADR 0124, mesure (a) de la décision)

> Les seuils de RÉTENTION de l'option 2 (ADR 0124 §Critères) : premier
> diagnostic profond **< 10 s**, diagnostic chaud **< 2 s**, mémoire
> additionnelle **< 300 Mo** — « seuils proposés pour RETENIR l'option
> 2/3 ». La passe (a) et la confirmation hôte (b) = Q-K7 sont les DEUX
> mesures du verdict ; si (a) OU (b) échoue : **rejet motivé**, le palier
> 2 reste le plafond de fidélité (la décision finale appartient au
> propriétaire §10).

| # | Mesure | Cible | Passe 1 (12/11/2026) | Passe 2 | Passe 3 | Médiane |
|---|---|---|---|---|---|---|
| H1 | Premier diagnostic profond (amorçage compris) | < 10 000 ms | **27 163 ms** (2,7× la cible) | | | |
| H2 | Diagnostic profond CHAUD (option 2, sans démon) | < 2 000 ms | **27 129 ms** (13,6× la cible) | | | |
| H3 | Mémoire additionnelle du mode profond | < 300 Mo | **0 Mo au tas du banc** (le sous-process K2 est HORS process ; l'empreinte totale appareil reste à relever par `/proc/meminfo` — passe 1 non concluante, voir lecture) | | | |
| K7 | Environnement déporté confirmé (Q-K7) | l'hôte dispose de Termux/PRoot + JDK 17 | **OUI** — `com.acside` (le bac déporté de l'hôte lui-même) : OpenJDK **17.0.19** aarch64, 8 cœurs, 3,8 Go total / 567 Mo libres | | | |

**Lecture de la passe 1** (chiffres exacts et facteurs :
`mesures/2026-11-12-vh-passe1-lecture.md`) :

- **H1 manqué ×2,7** (27,2 s contre 10 s) et **H2 manqué ×13,6** (27,1 s
  contre 2 s) — la cause est STRUCTURELLE, pas thermique : H2 == H1
  (delta 34 ms) parce que l'option 2 RELANCE le sous-processus K2 à
  chaque requête — l'amorçage du compilateur est RE-payé à chaque
  diagnostic, il n'existe PAS de « chaud » sans démon (c'est la donnée
  qui manquait à l'arbitrage entre options 2 et 3) ;
- **Q-K7 confirmé** avec une nuance : l'environnement déporté est le bac
  `com.acside` de l'hôte (préfixe `/data/data/com.acside/files/usr/`),
  pas un Termux du commerce — la sonde du banc (qui cherchait
  `com.termux`) l'a raté, le chemin du JDK fait foi ; le compilateur K2
  y a TOURNÉ (les 27 s sont des secondes de compilation réelle, erreur
  détectée, provenance « palier 3 ») ;
- **H3 non concluant en passe 1** : le banc mesure son propre tas
  (0 Mo de delta — le sous-process est hors process) ; l'empreinte
  APPAREIL du mode profond reste à relever par `/proc/meminfo` autour
  d'une section H — MAIS ce relever ne peut plus sauver les seuils H1/H2.

**Verdict proposé à l'arbitrage du propriétaire (sur la passe 1)** :
**(a) échoue** — H1 et H2 manqués par des facteurs structurels (×2,7 et
×13,6, non rattrapables par la médiane ni par la mémoire). Suivant
l'ADR 0124 : **rejet motivé de l'option 2 (sans démon)** ; l'instruction
de l'option 3 (démon résident chaud) devient la SEULE voie crédible vers
les seuils — le potentiel mesuré (amorçage ≈ 27 s payé UNE fois au lieu
de chaque requête) est la donnée quantitative de cette instruction, pas
une décision prise ici. Le palier 2 reste le plafond de fidélité tant
que l'arbitrage n'est pas rendu. **La décision finale appartient au
propriétaire (§10).**
