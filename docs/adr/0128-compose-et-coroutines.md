# ADR 0128 — Compose et coroutines : décidabilité par paliers et décodage d'annotation

- **Statut** : Proposé
- **Date** : 2026-10-31

## Contexte

L'audit (§1.10, KH10) constate : Compose et coroutines ne sont servis que
par les previews (ADR 0116 — modèle neutre, preuve FQCN par imports,
budgets 500 nœuds/profondeur 6) ; aucun rouge, aucune complétion dédiée.
La référence (`02-reference-android-studio.md` §B10/§B11) fait de l'appel
composable hors contexte composable et de l'appel `suspend` hors contexte
suspendable des **rouges**, et sert `Modifier`, `remember`, `launch`/
`async`, `Flow` par typage. Obstacle mesuré : le rouge exige de connaître
les fonctions annotées du CLASSPATH — or `@Composable` n'est **pas décodée
aujourd'hui** (le décodeur de métadonnées ne lit pas les annotations ;
miroir Java : le LecteurDeclarationsJava saute aussi les annotations) ; le
modificateur `suspend` est en revanche lisible sur l'arbre des déclarations
projet. Écarts visés : GK-016 (Compose), GK-017 (coroutines). Cette ADR
**amende l'ADR 0116** (qui repoussait les analyses Compose à l'élargissement
du typage) en cadençant la décidabilité par paliers.

## Sources consultées

- Référence : `02-reference-android-studio.md` §B10 (Compose), §B11
  (coroutines et Flow), §B14 (annotations exploitées).
- Fiches : `05-fiches-comportement/KC-07` (C1–C5), KC-08 (S1–S5), KC-02
  (extensions `Modifier`), KC-03 (récepteurs implicites).
- Dépôt : ADR 0107, 0108, 0110, 0116 (amendée), 0123, 0125 ; audit
  `01-audit-moteur.md` §3.9, §4.3 (leçon V0 : décision sur information
  perdue = FP).

## Options

**Option 1 — Contexte par preuve d'import + décodage d'annotation.**
Palier 1 : l'import `androidx.compose.runtime.Composable` prouvé reconnaît
l'annotation dans le projet ; le modificateur `suspend` est lu sur l'arbre ;
un appel composable/suspend hors contexte n'est signalé QUE si l'annotation/
le modificateur de l'appelé est prouvé (déclaration projet, puis classpath
une fois le décodeur étendu). Palier 2 (ADR 0123) : `Modifier`, `remember`,
`mutableStateOf`, `launch`/`async` par récepteur, opérateurs `Flow` — par
typage. Mesuré : coût dex du décodage d'annotations à mesurer (V0 — marge
6 178 méthodes au-dessus de 26 590/32 768 mesurés) ; latence de premier
accès à mesurer (gabarit 07) ; FP = 0 exigé sur les rouges C1/S1 au banc
P1 (discipline ADR 0125).

**Option 2 — Traitement spécial dur** (FQCN Compose/coroutines codés en
dur). Contre-mesuré : signaler sur un nom plutôt que sur une preuve
fabrique des FP de la même famille que les segments racines mesurés en V0
(~60 FP : univers incomplet condamné à tort) ; couplage de maintenance à
chaque version des bibliothèques.

**Option 3 — Rien** (statu quo : previews seules, ADR 0116). Coût mesuré :
GK-016/GK-017 restent ouverts ; l'écart « niveau Android Studio » sur deux
domaines structurants d'un projet Android moderne reste publié dans la
matrice (`03-matrice-ecarts.md`).

## Critères et mesures

| Capacité | Palier 1 (imports/preuve) | Palier 2 (ADR 0123) |
|---|---|---|
| Contexte composable prouvé | annotation projet par import prouvé | héritage de contexte, lambdas composables |
| Rouge composable hors contexte (KC-07 C1) | appelé prouvé projet ; classpath après décodage | — |
| Contexte suspend (KC-08 S1) | modificateur arbre (projet) ; classpath après décodage | lambdas suspendables prouvés |
| Complétion corps composable (C2) | composables projet, marqués si prouvés | classpath marqué après décodage |
| `Modifier` (C3), `remember` (C4) | — | extensions/opérateurs par typage (KC-02) |
| `launch`/`async`/`Flow` (S2–S3) | builder reconnu par import prouvé | récepteur `CoroutineScope`, opérateurs par typage |
| Inspections dédiées prioritaires | fiches KC-07/KC-08 = gates | — |

Tout rouge nouveau entre au catalogue ADR 0125 (code, sévérité,
`@Suppress`) avec FP = 0 mesuré sur corpus Compose (auto-corpus P1, puis
manifestes du corpus externe) ; le silence est vérifié quand l'annotation
n'est pas prouvée (propriété testée — « preuve ou silence »).

## Décision proposée

**Option 1.** (1) Palier 1 : contexte par preuve d'import (miroir des
previews ADR 0116) + rouges bornés aux appelés PROUVÉS du projet ;
inspections dédiées des fiches KC-07/KC-08 en priorité. (2) Étendre le
décodeur de métadonnées aux annotations du classpath (projet mesuré :
coût dex et latence au banc, budgets 26 590/32 768 méthodes et 4,10/6 Mo
mesurés) — puis rouges C1/S1 complets et marquage de complétion.
(3) Palier 2 : Modifier/remember/Flow par typage après ADR 0123. L'option 2
est écartée, l'option 3 refusée (écrites noir sur blanc).

## Conséquences

- Les previews ADR 0116 ne bougent pas (budgets 500/6, modèle neutre).
- Le décodeur gagne une responsabilité mesurable : annotations par FQCN
  demandé, décodage paresseux (pas de surcoût au démarrage chaud — cible
  < 3 s, à mesurer gabarit 07).
- GK-016/GK-017 se ferment par étapes datées ; la borne honnête (pas de
  variance/génériques sur les types Flow) reste ADR 0107.

## Plan de migration progressive

1. Fiches KC-07/KC-08 palier 1 (projet seul) + codes au catalogue ADR 0125
   sous gate FP.
2. Extension du décodeur d'annotations (mesures dex/latence au banc,
   décision de poursuite sur chiffres).
3. Palier 2 : Modifier/remember/Flow par typage, après ADR 0123.

## Tests de non-régression et critères de sortie

- Fiches KC-07 (C1–C5) et KC-08 (S1–S5) vertes ; fiche 051 previews non
  régressée (16/16, ADR 0116).
- Banc P1 sur corpus Compose : FP = 0 sur les rouges C1/S1, FN documenté
  (sous-rapport publié) ; silence prouvé sans preuve d'annotation.
- Coût dex du décodeur étendu mesuré et publié (< marge 6 178 méthodes).

## Risques

- R1 — Le décodage d'annotation coûte trop : mitigation = décodage
  paresseux par FQCN + palier 1 livré indépendamment (projet seul).
- R2 — Évolution amont des annotations : mitigation = `@Composable` sans
  paramètre (stable), paramètres tranchés dans la source pour `@Preview`
  (précédent ADR 0116), veille ADR 0121.
- R3 — Rouge Compose faux positif sur code généré : coordination
  synthétiques Android (ADR 0109/0127) — coupure devant généré homonyme.
