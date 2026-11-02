# ADR 0032 — Complément de catalogue : les restes des familles unitaires (ajout/réordonnancement de paramètres, rename depuis un XML, codeDescription.href)

- **Statut** : Accepté — **LIVRÉE le 01/11/2026 (v1.12.0 — PV
  `mesures/2026-11-01-complement-catalogue.txt`)** : les trois restes
  consignés du PV V-D3 et le solde (e) de V-G sont livrés (a–d) ;
  le catalogue des actions reste OUVERT (famille = PR unitaire).
- **Date** : 2026-11-01
- **Écarts résolus** : G-016 (solde « ajout/réordonnancement de
  paramètres »), G-028 (solde « rename DEPUIS un XML »), G-023 (solde
  `codeDescription.href` — l'écart passe à RÉSOLU ENTIÈREMENT)
  (`01-audit-moteur.md` §1.7, §1.15 ; `03-matrice-ecarts.md`).

## Contexte

La vague V-D (ADR 0027) a livré la famille change signature côté
SUPPRESSION de paramètre ; le PV V-D3 consignait noir sur blanc :
« Reste ouvert (catalogue ouvert, famille = PR unitaire) :
ajout/réordonnancement de paramètres, rename de ressource DEPUIS un
document XML ». La vague V-G (ADR 0031 (e)) livrait les diagnostics
enrichis SANS `codeDescription.href` (« aucun consommateur identifié » —
reste P3 de G-023). Le propriétaire a demandé la livraison de ces
restes (« Go pour les restes des familles unitaires »).

## Sources consultées

- `02-reference-android-studio.md` §A5 (catalogue des refactorings :
  change signature « propagation dans la hiérarchie d'appels », conflits
  et preview — dialogue ModShowConflicts) ; §A2 (lint : les inspections
  Android Studio exposent une EXPLICATION cliquable).
- Spécification LSP 3.17 (`Diagnostic.codeDescription {href}`, un URI à
  ouvrir pour plus d'information sur le diagnostic).
- Page officielle des contrôles lint — `https://developer.android.com/
  studio/write/lint` — VÉRIFIÉE le 01/11/2026 : répond 200, n'expose
  AUCUNE ancre par ID d'issue (`lint --list` reste la liste canonique).
- Jar ecj 3.40.0 : lecture du format de classe de `IProblem`
  (`ParameterMismatch` = 67108979, `UndefinedConstructor` = 134217858)
  et sonde sur le moteur (arguments structurés, bindings des sites
  fautifs) — tout MESURÉ, rien de mémoire (R5).

## Décision

**Option 1 — Livrer les trois restes comme PR unitaires d'une même
tranche (retenue).**

(a) **AJOUT DE PARAMÈTRES** — quickfix ID-FIRST sur l'appel fautif
    (`ParameterMismatch` 67108979 méthode / `UndefinedConstructor`
    134217858 constructeur, arguments MESURÉS : types existants et
    d'arguments JOINTS par « , »). La signature visée s'étend des types
    RÉSOLUS des arguments excédentaires (bindings du site — le site
    fautif méthode garde un `MethodBinding` lisible, `problemId()` 0) ;
    chaque AUTRE appelant de l'ancienne arité reçoit la valeur PAR
    DÉFAUT du type (`0`/`false`/`null` — miroir du préremplissage du
    dialogue Change Signature). Les sites dont le nombre d'arguments
    DÉPASSE l'ancienne arité ne se retouchent pas (l'appel fautif porte
    déjà les futurs paramètres — MESURÉ). Imports raccourcis dans le
    fichier DÉCLARANT (miroir fiche 064). Refus honnêtes motivés
    (oracle `MoteurJava.ajoutParametresSure`) : mismatch, typeIllisible
    (null littéral), binaire, surcharge, sansConstructeur, varargs,
    hierarchie, referenceMethode.
(b) **RÉORDONNANCEMENT** — deux actions au caret d'un paramètre
    (« vers la gauche/droite », kind `refactor.rewrite.signature`,
    différées clé + caret + sens ±1) : les fragments de la signature
    s'échangent et chaque appelant échange ses arguments aux MÊMES
    rangs — la liaison expression↔paramètre est PRÉSERVÉE (miroir du
    Change Signature de la référence : `m(a, b)` devient `m(b, a)`
    quand `(int a, String b)` devient `(String b, int a)`). Le
    paramètre PEUT être utilisé (il se déplace avec ses lectures —
    c'est la suppression qui refuse). Refus : borne, hierarchie,
    varargs frontal, surcharge (risque de bascule de résolution —
    sémantique silencieuse), referenceMethode, appelIncomplet.
(c) **RENAME DEPUIS UN XML** — `prepareRename`/`rename` acceptent un
    document XML du module : la DÉCLARATION `name="…"` des values ou
    le SEGMENT `<nom>` d'une référence `@<type>/…`, `@+<type>/…`,
    `?<type>/…`, `R.<type>.<nom>` (data binding). Même propagation que
    depuis le code Java ; le document XML courant est servi depuis son
    TAMPPOIN OUVERT (fait foi). Garde publicateur : un document XML ne
    publie JAMAIS de diagnostics Java (l'intention documentée de V-B
    devient effective — prouvé au protocole).
(d) **`codeDescription.href`** — pour la provenance `lint`
    uniquement : la page officielle des contrôles lint (VÉRIFIÉE —
    aucune ancre par ID n'existe, AUCUN URL par contrôle n'est
    fabriqué ; l'ID reste dans `data.code`). ecj/inspection : pas de
    href (aucune page officielle stable par erreur/inspection —
    honnête, documenté). Champ purement ADDITIF dans la publication —
    aucun retrait, aucun kill-switch nécessaire (miroir du champ `data`
    V-G).

**Option 2 — Attente d'un consommateur client pour href.** Rejetée :
l'ordre du travail vient du propriétaire ; le champ est additif et
coûte une constante vérifiée.

**Option 3 — Dialogue interactif pour l'ajout (type/nom/défaut
choisis).** Rejetée pour cette tranche : le modèle ModCommand est
serveur-calcule/client-applique (ADR 0013/0014) ; la saisie
interactive appartient au client (K2) et la VALEUR PAR DÉFAUT par type
est le préremplissage EXACT de la référence — les refus honnêtes
bornent le risque.

## Conséquences

- Le catalogue change signature couvre suppression/ajout/
  réordonnancement multi-fichiers ; reste OUVERT : introduce parameter
  object, extract/inline de champs, invert boolean, wrap return value,
  conversions lambda↔anonyme, stream↔loop (famille = PR unitaire).
- Le kind `refactor.rewrite.signature` porte désormais TROIS actions :
  les gardes des bancs se prouvent sur le TITRE (note de contrat posée
  dans RefactorisationsVd3Test).
- Le client (K2) peut rendre le lien de documentation lint ; le registre
  `docs/REGISTRE-CLIENT-K2.md` reste la liste de consommation.
- Coût liste mesuré : p95 410 µs (< 30 ms, ×73) — rien d'eager (ADR
  0027 (f)).

## Tests de non-régression et critères de sortie

- Cas dorés multi-fichiers re-diagnostiqués sur témoin NEUF (esprit P5,
  0 erreur) ; oracles de motifs par refus ; liste honnête (mismatch
  pur, null littéral) ; tampon XML ouvert en fait de foi ; AUCUN
  diagnostic Java pour un XML ouvert (client espion) ; href lint/ecj/
  inspection ; transcript de latence. 17 tests (900 au total, 0 échec).

## Risques

- **R1 — Ajout au choix d'ecj (surcharge)** : refus motivé dès qu'un
  autre sélecteur homonyme existe dans le type déclarant.
- **R2 — Défaut `null` injecté chez les appelants** : miroir du
  préremplissage de la référence ; le re-diagnose P5 des cas dorés
  prouve la compilabilité ; un appelant qui déréférencerait le défaut
  est un COMPORTEMENT à valider par le développeur (preview = résultat,
  ADR 0014).
- **R3 — Bascule de résolution au réordonnancement** : garde « surcharge »
  stricte (tout homonyme du type déclarant ferme la porte).
