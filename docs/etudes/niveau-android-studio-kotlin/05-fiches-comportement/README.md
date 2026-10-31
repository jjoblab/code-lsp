# 05 — Fiches de comportement « niveau Android Studio » (Kotlin)

> Fiches de comportement **dérivées de la référence** (Android Studio — plugin
> Kotlin mode K2 / plugin Android ; sources listées par fiche). Étude en
> **lecture seule, clean-room** : aucune copie de code — les fragments Kotlin
> des fiches sont écrits par l'étude, neutres. Format du cahier
> (`docs/cahier-specifications-kotlin/`).
>
> Elles constituent la **vérité terrain qualitative « niveau Android
> Studio » qui manque au cahier actuel (KH13)** : chaque fiche est
> `[DÉRIVÉ DES SOURCES]` ; les comportements que seul le propriétaire pourra
> vérifier en capturant Android Studio sont marqués `[À CAPTURER]` (protocole
> de captures à venir).
>
> **Numérotation** : `KC-xx` (comportements candidats Kotlin).

## Index

| Fiche | Titre | Domaine | ADR porteur | Renvois |
|---|---|---|---|---|
| [KC-01](KC-01-completion-smart-type-attendu.md) | Complétion smart par type attendu | complétion | 0126 | 0123, 0122 · cahier 043 |
| [KC-02](KC-02-completion-chain-extensions.md) | Complétion chain / extensions applicables | complétion | 0126 | 0123, 0122 · Java C-03 · cahier 043 |
| [KC-03](KC-03-recepteurs-implicites.md) | Récepteurs implicites et lambdas à récepteur | complétion | 0126 | 0123, 0130 · cahier 046 |
| [KC-04](KC-04-when-exhaustif.md) | `when` exhaustif (enum et sealed) | complétion / diagnostics | 0125 | 0126, 0123 · oracle K2 |
| [KC-05](KC-05-completion-override.md) | Complétion `override` et stubs | complétion / génération | 0126 | 0131, 0129, 0123 · cahiers 044, 052 |
| [KC-06](KC-06-inlay-hints.md) | Inlay hints Kotlin | inlay hints | 0123 | 0126, 0136 |
| [KC-07](KC-07-contexte-composable.md) | Contexte `@Composable` | Compose | 0128 | 0127, 0125, 0123 · cahier 025 |
| [KC-08](KC-08-contexte-suspend.md) | Contexte `suspend` et coroutines | coroutines | 0128 | 0125, 0123, 0122 · oracle K2 |
| [KC-09](KC-09-nullabilite-smart-casts.md) | Nullabilité et smart casts | diagnostics / typage | 0123 | 0125, 0126 · cahier 046 |
| [KC-10](KC-10-optimiseur-imports.md) | Optimiseur d'imports Kotlin | imports / actions | 0131 | 0136, 0126 · cahiers 015, 019 |
| [KC-11](KC-11-intentions-quickfixes.md) | Intentions / quick-fixes prioritaires | actions | 0131 | 0136, 0125, 0123 · cahier 016 |
| [KC-12](KC-12-gradle-kts-catalogue.md) | Gradle KTS et catalogue de versions | Gradle KTS | 0130 | 0132, 0127, 0125 · cahier 027 |

## Méthode

- **Dérivé des sources** : chaque comportement décrit la référence (Android
  Studio, plugin Kotlin K2, plugin Android) observée en lecture seule, puis
  reformulé clean-room — jamais de copie de code ni de libellés.
- **Vérité terrain moteur** : les fiches bornent chaque comportement à ce
  que le moteur peut **prouver** — typage palier 2 (type du projet ou FQCN
  de bibliothèque ; pas de génériques, nullabilité inférée, variance),
  complétion à 6 genres (MEMBRE, NOM, IMPORT, ARGUMENTS_NOMMES,
  CONSTANTES_ENUM, ATTRIBUTS_ANNOTATION ; INCONNU = vide), politique
  « preuve ou silence », diagnostics `kt.*` bornés au catalogue, exécution
  sur appareil Android (ART, budget dex serré), oracle K2 en test
  (compilateur en sous-processus).
- **Statut** : `candidat — à intégrer au cahier par décision propriétaire
  avant implémentation` ; les amendements aux fiches existantes du cahier
  sont listés dans les notes de chaque fiche.
- **Renvois** : ADR 0122–0136 (à créer — numéros réservés : 0122
  architecture à paliers, 0123 typage palier 2, 0125 catalogue diagnostics,
  0126 complétion, 0127 modèle projet Android/code généré, 0128
  Compose/coroutines, 0129 interop, 0130 Gradle KTS, 0131
  actions/refactorings, 0132 usages/navigation, 0136 protocole) ; écarts
  GK-xxx (matrice, à venir) ; protocole de captures pour les
  `[À CAPTURER]`.

## Rappel KH13

Le cahier actuel décrit le moteur **tel qu'il est** ; il lui manque la
vérité terrain « niveau Android Studio » (KH13). Ces fiches comblent ce
manque : elles servent de **référence qualitative** aux vagues
d'implémentation futures — chaque vague y puise ses comportements cibles,
bornés par ce qui est prouvable ; l'oracle K2 tranche les cas douteux au
banc ; les `[À CAPTURER]` sont arbitrés par le propriétaire sur captures
réelles d'Android Studio.
