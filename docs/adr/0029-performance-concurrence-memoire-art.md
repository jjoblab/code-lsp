# ADR 0029 — Performance, concurrence, mémoire sur ART

- **Statut** : Accepté — **phase 1 (V-F1 : époque C + double-tampon +
  annulation fine de base) LIVRÉE le 31/10/2026 au soir** (PV dans
  `04-plan-vagues.md` §V-F1 et `mesures/2026-10-31-vf1-double-tampon.txt`) ;
  **phase 2 (V-F2 : verrous B par périmètre + chaos tests) LIVRÉE le
  31/10/2026 au soir** (PV dans `mesures/2026-10-31-vf2-vc1-vc2.txt` —
  comptage `synchronized(` 99 → 0 pour la cible < 6, métrique CI portée
  par `VerrousPerimetreTest` ; chaos 6 fils verts ; banc de charge
  « complétion A ∥ diagnose B » TENU) ; **phases 3–4 (V-F3 budgets
  octets + onTrimMemory, V-F4 plage prioritaire) LIVRÉES le 01/11/2026
  (v1.11.0 — PV `mesures/2026-11-01-vf-finale-vg-protocole.txt`)** :
  budgets d'octets ESTIMÉS du cache de parse (8 Mio, facteur arbre ×6 —
  garde anti-cyclage : la dernière entrée posée survit seule) et du mémo
  de localisation (2 Mio, négatifs évincés d'abord — re-probe honnête,
  contrats fiche 024 intacts), exécution de l'ADR 0011 point 2 ; shim
  `pressionMemoire(niveau)` miroir `ComponentCallbacks2` avec rapport
  CHIFFRÉ et relais multi-modules, exécution de l'ADR 0011 point 5
  (G-033 RÉSOLU) ; notification `$/codeide/plageVisible` v1 + double
  publication « visible d'abord » ≥ 16 000 caractères (passe prioritaire
  PURE, la complète fait foi, INERTE sans notification — kill-switches
  `memoire.budgets/ontrim/plageprioritaire.disabled`) ; **phase 5
  (incrémentalité) REPORTÉE motivée** : conditionnée à l'ADR 0017 (b)
  (artefacts JetBrains java-syntax, décision D4 non tranchée par le
  propriétaire) et le critère utile (« la frappe ne paie pas la
  re-résolution complète ») est déjà tenu par le diagnose rapide
  (fiche 014) — la vague V-F est COMPLÈTE au sens du plan 04.
- **Date** : 2026-10-31
- **Écarts résolus** : G-029 (partiel — annulation fine + époques), G-030
  (double-tampon), G-031, G-033 (`01-audit-moteur.md` §1.13, §1.14)

## Contexte

État mesuré de l'audit (H13/H14) : **82 occurrences** `synchronized` dans
`java/src/main`, dont 46 dans `MoteurJava.kt` — un **moniteur d'instance** par moteur ;
ordonnanceur à **worker unique** (`OrdonnanceurMoteur.kt:12–60`) : une complétion
peut attendre un diagnose, les lectures de fichiers distincts ne sont pas parallèles.
Chaque frappe re-résout l'unité réduite (`SourceFocuser` 2 000 caractères — amorti,
C3 v1.1.6) : pas d'incrémentalité, pas de graphe de dépendances, **pas de
double-tampon** de l'environnement de noms — la reconstruction froide post-sync est
mesurée à 11–12 s côté appareil (ECARTS-SERVEUR H2) et le classpath réel n'arrive
qu'à t+123,9 s (H1 hôte). L'ADR 0010 (schéma B « verrous par périmètre » + époque C)
et l'ADR 0011 (budgets octets, onTrimMemory) sont **toujours Proposés** — non livrés.

## Sources consultées

- `01-audit-moteur.md` §1.13, §1.14, §3.8 (comptages et preuves).
- ADR 0010 (schémas B/C — repris tels quels, enfin exécutés), ADR 0011 (mémoire),
  ADR 0006 (incrémentalité, ADR 0017 conditions), ADR 0015 (régimes).
- `02-reference-android-studio.md` §A1 (annulation/rejeu, passes priorisées),
  §C (coûts de la référence — nos cibles de dépassement).
- `spike/appareil/GABARIT-RESULTATS.md` (facteur ART 3–5× — borne d'estimation) ;
  `bancs/GABARIT-BASCULE-APPAREIL.md` (modèle de gabarit).
- Chiffres hérités des bancs signés (bornes basses JVM) : diagnose complet
  p50 11,7 ms / p95 39,8 ms ; rapide p50 1,5 ms ; S17 chaud ≈ 119 ms.

## Options

**Option 1 — Exécuter ADR 0010 (B puis C) + double-tampon + budgets, par vagues
mesurées (recommandée).**
(a) **Schéma B** : verrous par périmètre (fichier → module → index — ordre global
documenté), remplacement progressif du moniteur d'instance ; workers plafonnés 2–3
(ART) ; lectures parallèles de fichiers distincts. Comptage synchronized cible < 6
(critère ADR 0010).
(b) **Schéma C** : époque copy-on-write de l'environnement de noms — construction du
neuf **en tâche de fond pendant que l'ancien sert** (double-tampon), swap atomique,
rejeu des requêtes coupées (l'époque existe déjà pour les documents — N8 ; on
l'étend à l'environnement).
(c) **Annulation fine** : points de contrôle d'annulation dans la collecte de
complétion et le balayage d'usages (le jeton fiche 046 existe — généralisé).
(d) **Mémoire** : budgets octets étendus au parse-cache et aux caches de résolution
(ADR 0011 point 2), shim `onTrimMemory` (point 5 — l'hôte relaie le signal).
(e) **Résolution incrémentale** (G-031) : conditionnée à ADR 0017/0006 — cadre, pas
un prérequis.
(f) **Priorité visible** : l'équivalent « plage prioritaire » (zone visible
d'abord — §A1) sur le diagnose différé.

**Option 2 — RW multiple-lecteurs complet (schéma A).** Rejeté pour l'instant (ADR
0010) : exige des snapshots immuables partout ; à rouvrir sur mesure post-B si le
multi-fichiers concurrent devient dominant.

**Option 3 — Statu quo (worker unique).** Accepte : complétions bloquées par les
diagnoses, 11 s de figage post-sync — contraire aux cibles de vitesse (§0.1).

## Critères et mesures

- Test de charge déterministe (ADR 0010) : complétion fichier A + diagnose fichier B
  concurrents — p95 de chacun ≤ p95 séquentiel + 10 % ; **zéro interblocage**
  (timeout + dump en échec) ; test de chaos multi-threads en CI.
- Double-tampon : changement de classpath pendant rafale → **0 réponse périmée**
  (époques) et **0 figage** : requêtes servies par l'ancien environnement pendant la
  construction [cible : latence p95 de frappe pendant reconstruction ≤ 2× la latence
  nominale ; reconstruction mesurée appareil — gabarit 07].
- Comptage synchronized < 6 ; budgets octets respectés (banc mémoire).
- [Appareil exigé] : budgets p50/p95 par palier (moto g06 = référence), thermique
  3 passes — gabarit 07. **Cibles de vitesse proposées** (à valider par les mesures
  du propriétaire, §0.1 du prompt) : complétion/hover/signature perçus < 100 ms p95
  appareil ; diagnostics de frappe visibles < 300 ms ; aucune fonctionnalité de base
  indisponible pendant l'indexation (dégradation honnête) ; démarrage à chaud (double
  tampon + caches persistants) < 3 s avant première complétion utile.

## Décision proposée

**Option 1** dans cet ordre (chacune derrière drapeau, bancs avant/après) :
1. époque C + double-tampon (l'axe §0.1-3/5 le plus visible appareil — supprime le
   figage post-sync) ; 2. verrous B (ordre global, chaos tests) ; 3. budgets octets +
   onTrimMemory ; 4. annulation fine + plage prioritaire ; 5. incrémentalité
   (conditionnée). Toute vague d'intelligence (V-B/V-C) doit vivre **sans** dégrader
   ces budgets — « un gain d'intelligence qui double la latence de frappe n'est pas
   un gain » (§11).

## Conséquences

- La latence devient **régulière** (p99, pas seulement p95 — axe de dépassement
  §0.1-5) : les requêtes ne s'empilent plus derrière un worker unique.
- Discipline permanente : ordre de verrou documenté + revue ; comptage synchronized
  en métrique CI (rg -c).
- Mémoire ×2 transitoire pendant le swap du double-tampon — bornée par budget.

## Plan de migration progressive

Vagues F1 (époque/double-tampon), F2 (verrous B + chaos), F3 (budgets/onTrim), F4
(annulation fine/priorité visible), F5 (incrémentalité si conditions) — intégrées au
plan 04 comme vague **V-F**, avec dépendance inversée : V-F1 **avant** V-C
(intelligence) pour que la couche IDE n'hérite pas du verrou global.

## Suivi d'exécution

- **V-F1 (31/10/2026) — LIVRÉE.** Époque copy-on-write de l'ENVIRONNEMENT de
  noms (l'extension aux environnements du design N8 des documents) : chaque
  remplacement effectif de contexte incrémente la GÉNÉRATION du module ;
  `EnvironnementCompilation.calculerFraisse` capture atomiquement le couple
  (moteur, génération), calcule, et ne livre que si l'époque n'a pas tourné —
  sinon REJEU sur l'époque courante (bornes 3 résultats périmés / 8 coupures,
  clause d'honnêteté « dernier résultat » miroir des documents). Double-tampon :
  le moteur neuf est construit et RÉCHAUFFÉ (environnement + sonde de
  plateforme) sur un fil démon unique PENDANT QUE L'ANCIEN SERT ; swap atomique
  sous le verrou d'orchestration ; l'ancien est alors RETIRÉ (jeton d'époque
  annulé → coupure fine, puis dispose qui attend la drainage par le moniteur du
  moteur). Annulation fine DE BASE : points de contrôle dans la collecte de
  complétion (hiérarchies de membres, traversée de l'annuaire, filtres de
  paquetage) et le balayage d'usages (un par FICHIER) — l'exception
  `EpoqueRetiree` TRAVERSE la frontière « jamais null » de la complétion (elle
  n'est pas un échec interne mais un signal de rejeu). Le serveur route TOUS
  ses chemins de RÉPONSE par le protocole frais (`avecMoteurFrais`) et
  REPUBLIE les documents ouverts à chaque swap d'époque (auditeur d'époque —
  les diagnostics suivent l'époque sans attendre la frappe suivante). Repli
  documenté : `jo.codeide.lsp.java.doubletampon.disabled=true` rétablit le
  dispose immédiat synchrone (le protocole d'époque reste actif). Mesures JVM
  (banc `BancDoubleTamponTest`, R5) : frappe pendant reconstruction tenue
  p50 ×1,20 / p95 ×0,58 du nominal (budget ≤ 2× tenu) ; reconstruction réelle
  301/51 ms JVM (l'appareil paie 11–12 s — facteur 3–5× du spike) ; rejeu :
  2 passes, verdict livré par l'époque neuve. 730 tests tous modules / 0 échec
  (600 java, dont 8 V-F1 + 1 banc ; 41 gates environnementales ignorées avec
  raison) ; `clean build` strict (P1–P8) VERT ; d8 1,000/1,000/1,000 p95
  35,96 ms ; usages rappel 1,000 p95 40,62 ms ; parité verte ; banc N1 tenu
  (diagnose rapide p50 4,8 ms, tas 17 Mo).
- **V-F2 (31/10/2026) — LIVRÉE.** Schéma B de l'ADR 0010 enfin exécuté :
  verrous NOMMÉS par périmètre (orchestration → compilation → services →
  feuilles — le moniteur d'instance du moteur est retiré), comptage
  `synchronized(` 99 → 0 (cible < 6) ; lectures parallèles de fichiers
  distincts (mémo carte concurrente, complétion chaude sans verrou — les
  lambdas pont/environnement re-verrouillent seules, sonde de régime sur
  environnement VOLATILE, balayages hors verrou avec reprise par
  résolution) ; `PontEcj` sans AUCUN état mutable partagé (options et
  usine frais par passe) ; ordonnanceur en POOL plafonné (2 par défaut,
  propriété bornée 1..3 — fiche 048 amendée : jamais deux tâches du même
  fichier en vol). Chaos test multi-fils (dump des piles en échec) et
  banc de charge ADR 0010 (complétion A ∥ diagnose B : p95 33 ms
  concurrent vs 43 séquentiel — l'ancien moniteur échouait) — verts.
  Note honnête différée à V-C3 : la collecte de membres sur type BINAIRE
  jrt paie l'énumération à chaque appel (mesuré 15–26 ms).
- **V-F3 + V-F4 (01/11/2026, v1.11.0) — LIVRÉES (la vague V-F est
  COMPLÈTE).** **V-F3 — budgets d'octets (ADR 0011 point 2)** :
  `CacheParse` porte un budget d'octets ESTIMÉS (défaut 8 Mio — coût par
  entrée = source × facteur d'arbre ×6 + enveloppe, ESTIMATION de gestion
  R5, le tas réel reste l'affaire du banc mémoire) avec éviction LRU en
  PLUS de la borne d'entrées, et la garde ANTI-CYCLAGE (l'entrée la plus
  récente survit toujours — un fichier seul plus gros que le budget vit
  seul, jamais de put/éviction en boucle) ; le mémo de localisation
  (verdicts fqcn → jar, fiche 024) gagne 2 Mio estimés : les NÉGATIFS
  partent d'abord (re-probe honnête, le verdict rendu reste EXACT), le
  robinet intégral ne s'ouvre que si le budget reste dépassé. **V-F3 —
  shim `onTrimMemory` (ADR 0011 point 5, G-033)** :
  `MoteurJava.pressionMemoire(niveau)` reçoit l'ENTIER ComponentCallbacks2
  (miroir `NiveauxPressionMemoire` — identité, pas traduction) ;
  politique cumulative : < 20 → arbres résolus seuls (le mémo de diagnose
  reste, la frappe reste servie) ; 40/60 → + mémos + négatifs (les
  positifs gardent leurs handles) ; ≥ 80 → libération INTÉGRALE drainée
  par le verrou de compilation (le moteur SURVIT — tout se reconstruit
  paresseusement) ; le signal ne paie JAMAIS la construction de
  l'environnement (jrt/jars) ; rapport CHIFFRÉ `RapportPressionMemoire`
  (journalisable côté hôte — l'observable des logs appareil « caches
  d'arbre libérés » devient une donnée) et relais MULTI-MODULES
  `EnvironnementCompilation.pressionMemoire`. **V-F4 — plage prioritaire
  (phase 4, « zone visible d'abord » — transposition du §A1 de la
  référence)** : notification maison `$/codeide/plageVisible` v1 (offsets
  ABSOLUS, dernière gagnante, oubli au didClose, INERTE sans envoi —
  contrats du cahier intacts) ; sonde `serviraitPasseComplete` PUREMENT
  LEXICALE (mémo + preuve de confinement, jamais de compilation) ;
  `diagnosePrioritaire` masque le hors-visible (isomorphe fiche 014) et
  reste PURE (aucune écriture d'état — un partiel ne devient jamais
  source de portage) ; la publication COMPLÈTE suit dans le même tir et
  FAIT FOI (fil unique : ordre garanti). Seuil 16 000 caractères (~500
  lignes). Kill-switches : `memoire.budgets.disabled`,
  `memoire.ontrim.disabled`, `plageprioritaire.disabled`. **Phase 5
  (incrémentalité) : REPORTÉE motivée** — conditionnée à l'ADR 0017 (b)
  (artefacts JetBrains, décision D4 du propriétaire non tranchée) ; le
  critère utile est déjà tenu par le diagnose rapide (fiche 014).
  Validation (R5, PV `mesures/2026-11-01-vf-finale-vg-protocole.txt`) :
  23 tests V-F3/V-F4 verts (9 budgets + pressions, 5 plage prioritaire,
  9 V-G), suite complète verte, banc N1 tenu.
- **Phases 3–4 : LIVRÉES ci-dessus (01/11/2026). Phase 5 : reportée
  motivée (ADR 0017 (b), décision D4).**

## Tests de non-régression et critères de sortie

- Chaos tests verts ; 0 réponse périmée ; p95/p99 frappe ≤ baseline +10 % sous
  charge concurrente ; budgets mémoire tenus au banc ; appareil : gabarit 07 rempli
  par le propriétaire (jamais simulé).

## Risques

- **R1 — Interblocage** : ordre global + chaos tests + dump automatique.
- **R2 — ART monocœur** : workers plafonnés, bénéfice re-mesuré appareil.
- **R3 — Mémoire double-tampon** : budget + swap sous pression (onTrim).
