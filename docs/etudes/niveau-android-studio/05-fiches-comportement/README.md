# 05 — Fiches de comportement « niveau Android Studio »

> Fiches de comportement **dérivées de la référence** (IntelliJ Platform / plugin
> Android / Android Lint — étude `02-reference-android-studio.md`, sources listées
> par fiche). Format du cahier (`docs/cahier-specifications/`). Elles constituent la
> **vérité terrain qualitative** qui manque au contrat actuel (H11) : chaque fiche
> est `[DÉRIVÉ DES SOURCES]` (documentation et code public de la référence, lecture
> seule) ; les points non confirmés sont marqués et renvoient au protocole de
> captures (`06-protocole-captures-android-studio.md`).
>
> **Numérotation** : `C-xx` (comportements candidats) — à intégrer au cahier par
> décision propriétaire avant implémentation (amendements fiches existantes listés
> dans les notes).

## Index

| Fiche | Capacité | ADR | Réf. |
|---|---|---|---|
| [C-01](C-01-classes-light-R.md) | Classe `R` light : résolution et complétion avant build | 0021 | B8/B9 |
| [C-02](C-02-viewbinding-light.md) | ViewBinding light : nommage, champs, nullabilité par configuration | 0021 | B9 |
| [C-03](C-03-completion-smart.md) | Complétion smart : filtrage par type attendu | 0026 | A2 |
| [C-04](C-04-completion-typedef.md) | Complétion des typedefs `@IntDef`/`@StringDef` | 0026 | B12 |
| [C-05](C-05-newapi.md) | Contrôle NewApi : appel sous le minSdk | 0024 | B13 |
| [C-06](C-06-navigation-ressource.md) | Navigation `R.string.x` ↔ `strings.xml` | 0028 | B11 |
| [C-07](C-07-suppressions-inspections.md) | Suppressions d'inspections (`@SuppressWarnings`/`//noinspection`) | 0023 | A4 |
| [C-08](C-08-second-appel.md) | Second appel de complétion : membres inaccessibles et statiques importables | 0026 | A2 |
| [C-09](C-09-nullite-intra-methode.md) | Analyse de nullité intra-méthode (annotations + contrats) | 0023 | A4 |
| [C-10](C-10-references-multifichiers.md) | `references` multi-fichiers (levée de la parité stricte) | 0028 | A5 |
