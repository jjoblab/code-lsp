# ADR 0110 — Interop Java ↔ Kotlin avec le moteur Java

- **Statut** : Proposé
- **Date** : 2026-10-30

## Contexte

K7 du prompt : « Quel contrat avec `:java` et `:index` ? » Les projets
Android réels sont mixtes : l'ancien moteur extrait les types de premier
niveau des `.java` (`session/SourcesJava.kt` 715 l.) avec des gardes
d'honnêteté (arité Java jamais contrôlée, niveau 2 imbriqué extrait,
niveau 3 muet en type — caractérisé H3), tandis que le moteur Java
réécrit de code-lsp expose stubs/bindings ecj et le CIDX.

## Sources consultées

- Cahier : fiches 047 (multi-langue : complétion, arité, définition
  croisée, membres Java résolus, typage traversant un getter), 030
  (`ReproductionJavaImbriqueTest` — frontière mesurée H3).
- `code-lsp` : `:java` (façade publique, ADR 0002/0015), `:index`
  (analyseur `.class` partagé).
- `codeide-lsp` : ADR 0205 (alias JVM↔Kotlin), 0216 (membres hérités
  mixtes), 0222 (compagnons).

## Options

**Option 1 — Extraction syntaxique maison conservatrice** (statu quo
`SourcesJava` : lecture bornée des `.java`, top-niveau, gardes D3).
**Option 2 — Consommer le moteur Java réécrit** (API publique `:java`
pour les types/membres Java, navigation croisée par contrat) :
plus riche (bindings ecj), couplage inter-moteurs à spécifier.
**Option 3 — Light classes amont** : disqualifiées (poids plateforme,
étude §K7). **Option 4 — Ne rien changer.**

## Critères et mesures

- Fiches 047/030 : la frontière actuelle est CARACTÉRISÉE (niveau 3
  muet en type — H3 confirmé) : le contrat minimal est la non-régression
  de ce périmètre.
- Le moteur Java neuf est publié (v1.0.0) et son API est stable :
  l'option 2 est désormais réaliste (elle ne l'était pas pour
  l'ancien moteur vendored).

## Décision proposée

**Option 1 en parité, option 2 en cible** : la première vague reproduit
l'extraction conservatrice (cahier en main — y compris ses angles morts
documentés H3, améliorables ensuite par décision écrite) ; parallèlement,
une **interface publique d'interop** est négociée avec le domaine Java
(Q-K5, arbitrage propriétaire) : types de premier niveau, membres
résolus, navigation croisée `.kt` ↔ `.java`. Le contrat reste
INTERFACES PUBLIQUES documentées, jamais couplage interne.

## Conséquences

- La navigation croisée s'enrichit sans toucher au noyau conservateur ;
- l'arité Java reste hors contrôle jusqu'à l'option 2 (garde maintenue).

## Plan de migration progressive

Vague 1 : extraction conservatrice (parité). Vague 2+ : branchement de
l'interface d'interop derrière drapeau.

## Tests de non-régression et critères de sortie

- Fiches 047/030 vertes ; aucune régression Java (le domaine Java n'est
  pas touché — R3).

## Risques

- R1 — L'interface d'interop traîne (arbitrage) : le repli
  conservateur est autonome (aucune dépendance bloquante).
