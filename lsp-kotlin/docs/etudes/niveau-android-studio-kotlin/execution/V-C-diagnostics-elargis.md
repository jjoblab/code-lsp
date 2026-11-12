# Journal d'exécution — Vague V-C : Diagnostics élargis (P2, gated)

> Vague V-C du [plan](../04-plan-vagues.md) — exécutée le **12/11/2026**.
> Périmètre (ADR 0125 — l'ordre par décidabilité, les familles SANS
> inférence d'abord) : `kt.whenNonExhaustif`, `kt.suspendHorsContexte`,
> `@Suppress` servi (GK-021). La contrainte simple de types projet est
> reportée : le typage des INITIALISATEURS projet existe déjà (V-B) et la
> preuve de sous-typage exige le résolveur d'appel complet (non-dépassement
> documenté, SPECIFICATION-TYPES §3).

## 1. Ce qui est livré

1. **`kt.whenNonExhaustif`** (ERREUR) : un `when` en POSITION
   D'EXPRESSION (initialiseur, `return`, argument, corps d'expression
   `fun f(): T = when (e)`) sur un sujet ENUM TYPÉ — projet (genre ENUM,
   entrées du modèle) ou classpath (superclasse binaire `java.lang.Enum`,
   champs statiques typés par l'enum) — dont les entrées ne sont pas
   toutes couvertes, sans `else` (le jeton `ELSE_KEYWORD` de l'entrée).
   Un `when` INSTRUCTION n'est JAMAIS signalé (miroir K2).
   Appui : le typage d'une ENTRÉE D'ENUM qualifiée (`Couleur.ROUGE` type
   l'enum — le sujet des `when` sur valeur).
2. **`kt.suspendHorsContexte`** (ERREUR) : l'appel d'une fonction
   `suspend` PROUVÉE du projet (le modificateur est ÉCRIT — le modèle
   porte désormais `AppelableProjet.estSuspend`) hors d'une fonction
   `suspend` (englobante ou fonction LOCALE `suspend` — le contexte se
   suit dans la marche). Une surcharge NON suspend du même nom rend la
   preuve muette (conservateur).
3. **`@Suppress` servi** (GK-021) : les annotations `@Suppress("code")`
   des déclarations ENGLOBANTES éteignent leurs codes dans leurs régions
   (le joker `"*"`/`"all"` couvre tout — miroir kotlinc). Le filtre
   s'applique à TOUTE la passe sémantique et typée.

## 2. Les preuves

| Preuve | Résultat |
|---|---|
| Tests moteur (`DiagnosticsElargisKotlinTest`) | **8 verts** — l'enum projet en expression, complet/else muets, when instruction jamais signalé, suspend prouvé + contexte correct, surcharge non suspend muette, `@Suppress(code)` exact, joker `*`, rappel par mutants |
| Rappel par mutants (critère du plan ≥ 0,95/famille) | **when 4/4, suspend 3/3** (mesuré dans le test) |
| P1 auto-corpus (103 fichiers) | **FP=0, FN=0 (0,000/1000 l.) — STRICT VERT** (chaque nouveau code entre sous la garde FP/0,5 du plan) |
| Suite `:kotlin:test` | 564 verts (556 + 8) |

## 3. Bornologie (honnêteté D3)

- Le sujet doit être TYPER (un sujet non typable reste muet) ;
- les conditions couvertes se lisent par leur DERNIER segment
  (`Couleur.ROUGE`, `ROUGE`) — les conditions composées (`A, B ->`) se
  couvrent segment par segment ;
- l'appel suspend se prouve sur les appelables de PREMIER NIVEAU du
  projet (les membres `suspend` d'une classe exigent le contexte du
  récepteur — non-dépassement) ;
- `@Suppress` se lit sur les DÉCLARATIONS (le niveau FICHIER pour les
  imports inutilisés relève d'une fiche dédiée).
