# Journal d'exécution — Vague V-I1 : Capacités arbre (ADR 0136 étape B.2)

> Sous-vague V-I1 du [plan](../04-plan-vagues.md) (pilote la vague V-I) —
> exécutée le **12/11/2026**. Périmètre : les trois capacités décidables
> sur l'ARBRE SEUL, AVANT le palier 2 (séquencement de l'ADR 0136
> acceptée — étape B.2) : `linkedEditingRange`, `documentColor`,
> `onTypeFormatting`. Écarts visés : la part « arbre » de GK-026.

## 1. Ce qui est livré

1. **`linkedEditingRange`** (`session/CapacitesArbre.plagesLiees`) : la
   paire de délimiteurs APPARIÉS couvrant le caret — `()`/`{}`/`[]` par
   empilement lexical (LIFO par genre, un fermant orphelin est ignoré,
   jamais une paire inventée), `<`…`>` UNIQUEMENT dans les listes
   d'arguments de type (les chevrons de comparaison n'apparient jamais).
   SPI : `SessionLangageKotlin.plagesLiees(uri, deplacement)`.
2. **`documentColor`** (`CapacitesArbre.couleurs`) : les littéraux
   entiers HEXADÉCIMAUX Android — 8 chiffres `0xAARRGGBB` (alpha en tête)
   et 6 chiffres `0xRRGGBB` (opaque) — avec canaux normalisés [0, 1] ;
   les autres largeurs et les chaînes ne sont pas des couleurs. SPI :
   `SessionLangageKotlin.couleursDocument(uri)`.
3. **`onTypeFormatting`** (`CapacitesArbre.formatageFrappé`) : la
   RÉ-INDENTATION bornée à la frappe (miroir ADR 0115 — jamais une
   re-formatisation du document) : `\\n` indente la nouvelle ligne de
   l'ouverture nette de la précédente (ouvrants − fermants) ; `}`
   aligne l'accolade sur la ligne de son OUVRANT apparié (empilement en
   arrière) ; l'édition ne touche QUE le blanc de tête de la ligne.
   Déclencheurs annoncés : `\\n` et `}`. SPI :
   `SessionLangageKotlin.formatageFrappé(uri, deplacement, caractere,
   tailleIndentation)`.

La matrice paritaire passe de **19 annoncées / 7 absentes** à
**22 annoncées / 4 absentes** (semanticTokens, codeLens, callHierarchy,
typeHierarchy — la vague V-I2, palier 2). Le cas doré
`matrice-paritaire` verrouille la nouvelle colonne : chaque capacité
annoncée est SERVIE et testée (règle « annoncée = servie ET testée »).

## 2. Les preuves

| Preuve | Résultat |
|---|---|
| Tests moteur (`CapacitesArbreKotlinTest`) | **8 verts** (paires parenthèses/chevrons, hors délimiteur null, ARGB 8 chiffres exact, 6 chiffres opaque, sans hexadécimal vide, frappe `\\n`, alignement `}`, autre caractère vide) |
| Cas dorés protocole (`CasDoresProtocoleKotlinTest`) | 7 verts — matrice 22/4 verrouillée, déclencheurs exacts |
| Suite `:kotlin:test` complète | 550 verts (542 + 8) |
| CapacitesServeur | les 3 capabilities annoncées + `DocumentOnTypeFormattingOptions(\\n, })` |

## 3. Bornologie (honnêteté D3, documentée)

- Les chevrons de COMPARAISON ne s'apparient pas (seuls ceux des
  `TYPE_ARGUMENT_LIST`) ;
- une couleur est un LITTÉRAL de 6 ou 8 chiffres hexadécimaux — jamais
  une inférence depuis un nom (`Color.RED` relève du palier 2) ;
- la ré-indentation ne touche qu'une ligne — les continuations
  d'expression et le contenu des lignes ne se réécrivent JAMAIS (le
  formateur complet reste `textDocument/formatting`).
