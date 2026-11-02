# ADR 0026 — Complétion niveau Android Studio (smart, chain, statement, second appel, classement enrichi)

- **Statut** : **Accepté — LIVRÉ (vague V-C3, 31/10/2026)** — option 1
  complète : smart (affectation/initialiseur/argument-avec-union-à-l'arité/
  return/ternaire/lambda/allocation/cast, filtrage gradué JAMAIS caché),
  typedef (e), littéraux R.* (f), second appel + import static + 3e appel
  projet (d), chaîne à deux sauts (b), statement (c), stats opt-in à
  fenêtre glissante (g — ML rejeté v1), mémoïsation des membres binaires
  (reste PV 1.6.0). Amendement D7 (M1/G-024) exécuté : mots-clés de tête
  de déclaration fusionnés aux stubs. PV : `mesures/2026-10-31-vc3-completion.txt`
  (banc qualité v2 24 positions split train/éval — ÉVAL recall 1,000 /
  top-1 1,000 ; naïf ÉVAL top-1 0,000).
- **Date** : 2026-10-31
- **Écarts résolus** : G-012, G-017, G-018, G-024 (`01-audit-moteur.md` §1.10)

## Contexte

Le pipeline livré (collecte par genre, classement D8, gel, plafond, frontière RPC)
est **au niveau** ; la couverture des contextes est en retrait (audit §1.10) :
**smart** (filtrage par type attendu) absent — `typeAttendu` ne sert qu'aux
constantes d'enum et aux noms de déclaration (`ServiceCompletion.kt:145–146`) ;
**chain**, **statement**, **second appel** (membres inaccessibles), import `static`,
`@IntDef`/`@StringDef`, littéraux de ressources : absents. La référence calcule le
type attendu par bindings (`ExpectedTypesProvider`), propose le chain en seconde
passe, élargit le scope aux invocations successives (2e = inaccessibles, 3e = projet
entier), et pousse les constantes de typedef en tête (`02-reference…` §A2, §B12).
ecj expose nativement tout ce dont ces capacités ont besoin (types des expressions,
paramètres attendus, annotations résolues) — pas de réimplémentation d'inférence
(A7/A7-implication 2).

## Sources consultées

- `02-reference-android-studio.md` §A2 (smart/chain/statement/invocations, gel,
  stats/ML — gain publié +10 % du ML, statistiques locales), §B12 (typedef
  completion), §B11 (littéraux de ressources).
- `01-audit-moteur.md` §1.10 (matrice par genre), §1.9 (splice → ADR 0019).
- ADR 0012 (chaîne D8 — conservée), ADR 0019 (contexte par copie+factice),
  ADR 0020/0021 (référentiel/classes light = prérequis des littéraux `R.*`).
- Banc qualité (18 positions → à étendre, ADR 0030) + P6.

## Options

**Option 1 — Étendre par invocations et par bindings (recommandée).**
(a) **Smart** : calcul du type attendu par contexte (affectation, argument d'appel,
`return`, ternaire, lambda) **via les bindings ecj** (demander le type du membre
gauche / du paramètre formel) ; filtrage gradué : les éléments type-compatible
remontent, les autres restent visibles en bas (jamais cachés — dégradation honnête).
(b) **Chain** : seconde passe — sur le type courant, méthodes retournant le type
attendu (profondeur 2, budget d'items). (c) **Statement completion** : réutilise la
référence d'équilibrage d'`AideSignatures.refermer` (v1.1.6 C2) pour compléter
l'instruction courant. (d) **Second appel** : compteur d'invocation par (document,
ancre) côté serveur — 2e appel : membres inaccessibles (marqués), statiques
importables ; 3e : projet entier via CIDX + import auto. (e) **Typedef** :
`@IntDef/@StringDef` lus sur le paramètre/champ → constantes autorisées en tête.
(f) **Littéraux de ressources** : dans les chaînes/arguments annotés `@StringRes`,
proposer `R.string.*` (référentiel ADR 0020). (g) **Statistiques locales opt-in**
(compteur d'usage persistant, fenêtre glissante) branché comme peseur D9 ; **ML
rejeté en v1** (gain +10 % documenté mais exige des logs d'usage massifs que nous
n'avons pas ; à réévaluer après télémétrie opt-in — évaluation chiffrée exigée avant
toute ré-ouverture).

**Option 2 — Réimplémenter un inférenceur « expected type ».** Rejeté : ecj le fait
déjà (A7) ; la référence elle-même accumule ses faux positifs là.

**Option 3 — Améliorer le seul classement.** Déjà fait (D8 livré) ; le gain
restant est dans la couverture, pas le tri.

## Critères et mesures

- Banc qualité **étendu** (ADR 0030) : positions smart/chain étiquetées (type attendu
  connu) — [cible : rappel ≥ 0,95 sur positions smart, top-1 ≥ 0,80] ; à mesurer
  avant/après (baseline = état actuel, mesurée par la vague 0).
- P6 : 0 suggestion insérée qui casse le code (imports compris).
- Latence : smart/chain servis au régime différé si > budget [p95 ≤ 150 ms JVM
  membres ; la 2e passe chain peut être asynchrone — items ajoutés].
- [Appareil] : p95 perçu < 100 ms (§0.1) — gabarit 07.

## Décision proposée

**Option 1**, ordonnée par valeur : smart (argument/affectation/return) → typedef +
littéraux ressources (dépend ADR 0020/0021) → second appel + import static → chain →
statement → stats locales opt-in. M1 (`extends` en tête de déclaration) : fusion
mots-clés + stubs en tête de déclaration — **amendement cahier fiche 007 à demander**
(décision D7). La capability `x-codeide.completionRanking` est étendue
(versionnement) pour transporter le compteur d'invocation si le client gère le
double-appel localement.

## Conséquences

- La complétion cesse d'être « le problème le plus urgent » (demande propriétaire
  31/10) sur les axes intelligence ; la latence reste gouvernée par les régimes
  (instantané → différé).
- Le filtrage par type **ne cache jamais** : tri gradué — cohérent avec le plafond
  honnête existant.
- Stats locales : fichier d'état utilisateur local, purgable, documenté (vie privée).

## Plan de migration progressive

1. Type attendu (3 contextes) + filtrage gradué (drapeau `completion.smart`).
2. Typedef + `R.*` dans les chaînes (après 0020/0021).
3. Second appel + import static (compteur d'invocations).
4. Chain (asynchrone) ; statement completion ; stats opt-in.

## Tests de non-régression et critères de sortie

- Banc étendu : rappel/top-1 aux cibles ; P6 = 0 ; fiches 007/008/020/044/050
  toujours vertes (amendées si D7 acceptée) ; p95 ≤ baseline.

## Risques

- **R1 — Latence smart** : bindings déjà résolus par la sonde (réutilisation),
  régime différé en repli.
- **R2 — Bruit du filtrage** : gradué (jamais caché), mesuré au banc.
- **R3 — Compteur d'invocation client/serveur divergent** : contrat versionné +
  repli serveur.
