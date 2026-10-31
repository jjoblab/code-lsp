# ADR 0029 — Performance, concurrence, mémoire sur ART

- **Statut** : Proposé
- **Date** : 2026-10-31
- **Écarts résolus** : G-029, G-030, G-031, G-033 (`01-audit-moteur.md` §1.13, §1.14)

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

## Tests de non-régression et critères de sortie

- Chaos tests verts ; 0 réponse périmée ; p95/p99 frappe ≤ baseline +10 % sous
  charge concurrente ; budgets mémoire tenus au banc ; appareil : gabarit 07 rempli
  par le propriétaire (jamais simulé).

## Risques

- **R1 — Interblocage** : ordre global + chaos tests + dump automatique.
- **R2 — ART monocœur** : workers plafonnés, bénéfice re-mesuré appareil.
- **R3 — Mémoire double-tampon** : budget + swap sous pression (onTrim).
