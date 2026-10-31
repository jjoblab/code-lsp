# ADR 0027 — Actions de code étendues (catalogue, source.\*, refactor.\*, codeAction/resolve)

- **Statut** : Proposé
- **Date** : 2026-10-31
- **Écarts résolus** : G-014, G-015, G-016, G-020 (`01-audit-moteur.md` §1.7)

## Contexte

Le mécanisme ModCommand est livré (N6, `actionscode/ModCommandes.kt`) — c'est le
**catalogle** qui est pauvre : 21 IDs ecj → 7 familles (`FamillesProbleme.kt:83–135`),
tout autre ID → `UNKNOWN` → aucune action ; `codeActionKinds` = QuickFix seul
(`CapacitesServeur.kt:229`) ; `organizeImports` existe en API moteur
(`MoteurJava.kt:1145`) mais n'est pas exposé (M3) ; aucune génération de code, aucune
intention sans diagnostic, aucun refactoring d'extraction. La référence : ampoules
jaune (intentions, sans diagnostic) + rouge (quick-fixes), Alt+Enter unique,
génération par templates (constructeur, getters/setters, equals/hashCode, toString,
override/implement, délégation), refactorings avec détection de conflits et preview
(`02-reference…` §A3, §A5).

## Sources consultées

- `02-reference-android-studio.md` §A3 (ModCommand — dont le verrou optimiste
  oldText/nouveauTexte, preview sur copie, ModShowConflicts/ModChooseAction pour les
  dialogues multi-étapes), §A5 (heuristiques extract method : une sortie, entrées
  bornées, pas de contrôle de flux non local ; safe delete = usages + conflits).
- `01-audit-moteur.md` §1.7, §3.6.
- ADR 0013/0014 (ModCommand, phases du rename — réutilisés tels quels), ADR 0028
  (usages différenciés = socle de safe delete/change signature).
- Fiches 022/063/065 du cahier (organize imports vendord, quick-fixes mono-fichier).

## Options

**Option 1 — Élargir le catalogue par vagues sur le mécanisme existant
(recommandée).**
(a) **Quick-fixes** : étendre la table ID→famille aux IDs ecj à fix réel (au moins
les familles « import rapide » (import manquant, cast, qualifiers), « supprimer »
(import/unused — existe), « créer » (méthode/champ/type — existe pour méthode),
« try/catch », « changer type », « ajouter override/@Override »…) — chaque famille =
un ModCommand + cas dorés.
(b) **Kinds** : `codeActionKinds += Source (organizeImports d'abord — M3), SourceFixAll
(marqueur), Refactor (extract/inline/rename)` ; exposer `organizeImports` immédiatement
(coût minuscule).
(c) **Intentions sans diagnostic** (ampoule jaune) : conversions
lambda↔classe anonyme, références de méthode↔lambda, `if`→ternaire, extraction
rapide de constante, `var` explicite↔implicite — calculées sur le contexte (bindings)
en lecture.
(d) **Génération de code** : getters/setters, constructeur, equals/hashCode,
toString, override/implement (stubs à valeurs par défaut + copie Javadoc) — par
**templates Kotlin** (pas Velocity) versionnés.
(e) **Refactorings** : extract variable/constant (v1) → extract method (heuristiques
référence) → inline variable/constant → safe delete (usages + conflits, réutilise
ADR 0014/0028) → change signature (graphe d'appelants CIDX, dernier).
(f) **`codeAction/resolve`** : calcul différé des gros WorkspaceEdit (multi-fichiers,
`documentChanges` versionné — modèle N5 réutilisé) + preview = diff sur copie (le
résultat N5 « preview = résultat » se généralise).
(g) **Verrou optimiste** : généraliser le rejet si le texte attendu a divergé
(mobile : l'utilisateur tape pendant le calcul).

**Option 2 — Refaire un service d'actions ad hoc.** Rejeté : ModCommand est précisément
le bon modèle (ADR 0013, déjà livré et testé).

**Option 3 — Tout le catalogue d'un coup.** Rejeté : non borné ; la priorisation
matrice (§03) impose la valeur d'abord.

## Critères et mesures

- **P5** (livré, gate) : chaque quick-fix appliqué → diagnostic cible disparu, zéro
  nouvelle erreur, code compilable — étendu aux nouvelles familles au fil de l'eau.
- Parité quick-fixes : cas dorés versionnés par famille (WorkspaceEdit exacts).
- Refactorings : oracle conflits/réparations sur cas dorés (méthode ADR 0014).
- Latence : `codeAction` (liste) < 30 ms p95 JVM ; resolve différé accepté.

## Décision proposée

**Option 1** avec cet ordre (valeur/valeur-cost) : (1) exposer `organizeImports`
(M3 — quasi gratuit), (2) génération de code (getters/setters/constructeur/
equals/hashCode/toString/override), (3) intentions sans diagnostic (conversions),
(4) étendue des familles ecj (import/try-cast/créer), (5) extract variable/constant
puis method, (6) safe delete, (7) inline, (8) change signature. M5 (mono-fichier des
quick-fixes) est levé naturellement par `documentChanges` (déjà négocié en N5).

## Conséquences

- La palette d'actions devient « niveau AS » sur les 20 % à 80 % de valeur ; le
  catalogue reste ouvert (famille = PR unitaire avec cas dorés).
- Le client doit consommer les kinds (K2 — liste séparée prompt client).
- Les intentions demandent un canal de déclenchement sans diagnostic : LSP
  `codeAction` les rend déjà (aucune extension protocole nécessaire).

## Plan de migration progressive

1. organizeImports + kinds Source/Refactor + `codeAction/resolve` (trimestre 1 de la
   vague V-D, cf. plan 04).
2. Génération de code (6 capacités) + intentions (4 conversions).
3. Familles ecj étendues (+8) avec cas dorés P5.
4. Extract/inline/safe-delete/change-signature par étapes.

## Tests de non-régression et critères de sortie

- P5 = 0 violation sur l'ensemble des familles exposées ; cas dorés par capacité ;
  fiches 063/065 vertes ; zéro régression rename N5.

## Risques

- **R1 — Extract method mal bornée** : heuristiques référence (une sortie, entrées
  bornées) + refus honnête (message) plutôt que transformation fausse.
- **R2 — Conflits multi-fichiers mal détectés** : réutiliser le moteur de collisions
  N5 (déjà éprouvé) ; safe delete gated par usages complets.
- **R3 — Poids du catalogue sur ART** : calcul paresseux (resolve), rien d'eager.
