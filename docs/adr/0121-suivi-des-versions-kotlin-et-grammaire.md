# ADR 0121 — Suivi des versions de Kotlin et de la grammaire (vendoring ou artefact)

- **Statut** : Proposé
- **Date** : 2026-10-30

## Contexte

K1 : « Comment suivre les versions de Kotlin sans re-vendoring
douloureux ? » La grammaire vit dans un module interne du compilateur
(« not published on Maven » — VENDOR.md amont), 2×/an l'amont bouge ;
le bootstrap (version compilant le compilateur) contraint la chaîne :
v2.3.0 est compilé par 2.2.x = notre chaîne actuelle [vérifié — le
spike compile le vendoring tel quel].

## Sources consultées

- `JetBrains/kotlin` : tags et bootstrap (constat du VENDOR.md ancien,
  revérifié : v2.3.0 compile sous 2.2.10 [mesuré, spike §5.1]).
- ADR 0115 ancienne (procédure de re-sync), ADR 0119 ancienne
  (renonciation `languageVersion`), ADR 0018 Java (montée JDT —
  procédure de montée bornée, transposable d'esprit).
- Spike : S2 (tolérance, digeste possible).

## Options

**Option 1 — Vendoring épinglé + procédure de montée bornée.**
Re-sync scripté (remplacement intégral) ; gate = suite de parité +
digeste + bancs ; l'épinglage suit la règle « tag compilé par un
bootstrap ≤ notre chaîne ».
**Option 2 — Attendre un artefact publié** (multiplatform-parsing sur
Central) : n'existe pas [constaté] ; à surveiller, aucune dépendance de
plan possible.
**Option 3 — Vendoring à HEAD** (master) : instable, casse la chaîne
(bootstrap futur) — écarté. **Option 4 — Ne rien changer.**

## Critères et mesures

- Montée mesurée : le spike a « monté » à zéro coût (ré-obtention
  amont v2.3.0 compilée telle quelle) — la procédure est réelle, pas
  théorique [mesuré].
- syntax-api épinglé par le tag amont (0.3.340) : monte ENSEMBLE avec
  la grammaire (catalogue ancien — règle maintenue).

## Décision proposée

**Option 1** : le couple (grammaire vendorée au tag T, syntax-api à la
version épinglée par T) monte ensemble à chaque montée de chaîne
d'outils du dépôt, par procédure bornée : (1) re-sync scripté ;
(2) suite de parité + digeste (régénération délibérée, diff revu) ;
(3) bancs (fidélité/complétion — ADR 0119) ; (4) montée kotlin-metadata
en même temps ; (5) appareil (gabarit) avant release. L'option 2 reste
consignée : si l'amont publie un jour `multiplatform-parsing` en
artefact, la bascule artefact sera instruite (elle supprime le
vendoring).

## Conséquences

- La dérive amont devient un événement PLANIFIÉ (montée de chaîne), pas
  une urgence.
- `languageVersion` reste absent (miroir ADR 0119 ancienne) : le
  `kt.syntax` sur syntaxe future est honnête jusqu'à la montée.

## Plan de migration progressive

Procédure écrite dans le module (README de vendoring) dès la vague 0 ;
exécutée à chaque montée.

## Tests de non-régression et critères de sortie

- Suite de parité verte + digeste re-signé + bancs verts à chaque
  montée ; aucune montée sans ces gates.

## Risques

- R1 — Saut de bootstrap amont (2.4 exige 2.3+) : la montée devient
  une décision de chaîne d'outils du DÉPÔT (pas seulement du module) —
  tracée au plan.
- R2 — syntax-api indisponible à la version attendue : mitigation =
  cache-redirector + épinglage par le tag (l'ancien dépôt tient déjà
  ce canal).
