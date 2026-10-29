# ADR 0105 — Modèle de symboles et déclarations

- **Statut** : Proposé
- **Date** : 2026-10-30

## Contexte

K2 du prompt : « Quelle représentation [des symboles], et quelle
frontière avec `:model` ? » L'amont offre la façade Ka* (98 269 l.,
15 % liés à IntelliJ [mesuré]) — non embarquable ; l'ancien moteur
maintient un modèle neutre conservateur (`symboles/` 149 l. +
`SymbolesProjet` 759 l., déclarations projet + vue JVM classpath).

## Sources consultées

- `JetBrains/kotlin` @ `e3806c8f` : `analysis/analysis-api`
  (KaSymbol/KaSession — vocabulaire), `compiler/fir/tree`+`cones`
  (représentation interne).
- `codeide-lsp` : fiches cahier 046/049 (`SymbolesProjet`, `PreuveAbsenceProjet`),
  `docs/LSP_KOTLIN.md` §4.1 (symboles neutres).
- `code-lsp` : `:model` (`ProjetLsp`, classpath v8 — descriptions de
  projet, pas de contenu).

## Options

**Option 1 — Modèle maison conservateur, vocabulaire inspiré Ka*.**
Symboles immuables (genre, nom, conteneur, portée, clause écrite),
déchets zéro, sous-rapport D3 sur les formes non modélisées.
**Option 2 — Reprise du modèle ancien** (voie B/C) : `symboles/` est un
candidat de reprise (149 l., borné). **Option 3 — Embarquer la façade
Ka*** : disqualifiée (poids + plateforme, ADR 0103).
**Option 4 — Ne rien changer.**

## Critères et mesures

- Le cahier verrouille les comportements attendus du modèle (genres
  déclarés, objets référençables, compagnons, expect/actual, typealias
  — fiches 046, 021, 025, 026, 039).
- Frontière `:model` : le modèle de PROJET (entrées, modules) ≠ le
  modèle de SYMBOLES (contenu des sources) — aucune évolution de
  `:model` n'est requise par le noyau (Q-K3 reste ouverte par prudence).

## Décision proposée

**Option 1 (+2)** : modèle de symboles maison immuable et conservateur,
vocabulaire inspiré de Ka* (genres, KaSymbol-like, session par module)
pour faciliter TOUTE future correspondance K2 (mode profond ADR 0103) ;
en voie C, les fichiers `symboles/` de l'ancien moteur sont un candidat
de reprise propre (registre préalable). Frontière : le modèle vit dans
`:kotlin`, consomme `:model` en lecture (classpath v8), ne l'étend pas.

## Conséquences

- Le vocabulaire commun Ka* réduit le coût d'un éventuel mode profond
  (traduction symbole maison → K2).
- La représentation reste bornée : ce qui n'est pas modélisé
  sous-rapporte (D3 inchangé).

## Plan de migration progressive

Le modèle naît avec l'étage parse/session ; les genres étendus
(compagnons, objets, typealias, expect/actual) suivent le cahier.

## Tests de non-régression et critères de sortie

- Fiches cahier correspondantes vertes (genres, portées, compagnons) ;
  zéro faux positif sur corpus quatre mondes.

## Risques

- R1 — Envy-featuritis (« modéliser presque tout ») : mitigation =
  chaque extension passe par le banc de fidélité (ADR 0119).
