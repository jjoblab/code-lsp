# Résultats — Lot E (positions Kotlin)

> À remplir par le propriétaire. Protocole §2 : position, geste, **top-10
> copié en texte** (l'ordre compte), temps perçu (immédiat / visible / lent).
> Images dans `images/Ex-*.png`. Support : [`kit/KitCapturesKotlin.kt.txt`](kit/KitCapturesKotlin.kt.txt).
> Le texte prime sur l'image ; E6 exige l'écran ET la transcription.

## Contexte de la capture

| Champ | Valeur |
|---|---|
| Version Android Studio exacte (Help > About) | |
| Mode K2 confirmé (Settings > Languages & Frameworks > Kotlin) | oui / non — détail : |
| Machine (modèle, RAM) | |
| Projet de test | modèle « Empty Activity » / autre : |
| Indexation terminée avant captures | oui / non |
| Réglages complétion / inlay hints modifiés | non / préciser : |

## E1 — Smart par type attendu (`val s: String = |`)

| Position | Geste | Top-10 attendu (ordre exact) | Temps perçu | Remarques |
|---|---|---|---|---|
| `e1Smart` l.__:__ | Ctrl+Shift+Espace | 1. 2. 3. 4. 5. 6. 7. 8. 9. 10. | | position de `libelle` ; `compte`/`villes` (non compatibles) : absents / présents pos. __ |
| idem | Ctrl+Espace (2e liste) | 1. 2. 3. 4. 5. 6. 7. 8. 9. 10. | | |

## E2 — Chaîne sur collection (`listOf(1, 2).fil|`)

| Position | Geste | Top-10 attendu (ordre exact) | Temps perçu | Remarques |
|---|---|---|---|---|
| `e2Chaine` l.__:__ | frappe `fil` (spontané) | 1. 2. 3. 4. 5. 6. 7. 8. 9. 10. | | |
| idem | Ctrl+Espace | 1. 2. 3. 4. 5. 6. 7. 8. 9. 10. | | |

## E3 — Récepteurs implicites de DSL

| Position | Geste | Top-10 attendu (ordre exact) | Temps perçu | Remarques |
|---|---|---|---|---|
| `e3Recepteurs` `buildString { appe¦` | frappe `appe` | 1. 2. 3. 4. 5. 6. 7. 8. 9. 10. | | |
| `e3Recepteurs` `with(context) { \| }` | Ctrl+Espace | 1. 2. 3. 4. 5. 6. 7. 8. 9. 10. | | |

## E4 — when exhaustif (enum puis sealed)

| Position | Geste | Liste attendue / constat | Temps perçu | Remarques |
|---|---|---|---|---|
| `e4When` when VIDE sur enum | Ctrl+Espace | 1. 2. 3. 4. 5. 6. 7. 8. 9. 10. | | |
| `e4When` branche `Vide` manquante | Alt+Enter | message exact : ________ ; correctifs (ordre) : 1. 2. 3. | | |

## E5 — Stubs d'override (`class EcranArticles : Ecran { o|`)

| Position | Geste | Top-10 attendu (ordre exact) | Temps perçu | Remarques |
|---|---|---|---|---|
| corps de `EcranArticles` l.__:__ | frappe `o` | 1. 2. 3. 4. 5. 6. 7. 8. 9. 10. | | forme du stub inséré : ______ |

## E6 — Inlay hints (types, paramètres, récepteurs)

| Position | Geste | Hints observés (transcription texte) | Temps d'apparition | Remarques |
|---|---|---|---|---|
| `e6Inlays` + `totalLigne` — écrans `E6-1.png`/`E6-2.png` | ouvrir, attendre | | | |

## E7 — Appel suspend hors coroutine

| Position | Geste | Message exact + correctifs (ordre) | Temps perçu | Remarques |
|---|---|---|---|---|
| `e7HorsCoroutine` l.__:__ | Alt+Enter | message : ________ ; correctifs : 1. 2. 3. | | |

## E8 — Compose (hors contexte + complétion Modifier)

| Position | Geste | Message / Top-10 attendu | Temps perçu | Remarques |
|---|---|---|---|---|
| `e8ComposeHorsContexte` | Alt+Enter | message : ________ ; correctifs : 1. 2. 3. | | |
| `ListeArticles` `modificateur.p¦` | frappe `p` | 1. 2. 3. 4. 5. 6. 7. 8. 9. 10. | | |

## E9 — Nullabilité et smart cast (`var pseudo: String?`)

| Position | Geste | Message / Top-10 attendu | Temps perçu | Remarques |
|---|---|---|---|---|
| `pseudo.length` SANS garde | Alt+Enter | message : ________ ; correctifs : 1. 2. 3. | | |
| idem APRÈS `if (pseudo != null)` | constater | erreur disparue ? oui / non — hint : ________ | | |
| en aval de `pseudo ?: "invité"` | Ctrl+Espace | 1. 2. 3. 4. 5. | | |

## E10 — Complétion dans le KDoc (`@param`)

| Position | Geste | Liste attendue (ordre exact) | Temps perçu | Remarques |
|---|---|---|---|---|
| KDoc de `totalLigne`, `* @p¦` | frappe `@p` | 1. 2. 3. 4. 5. | | |
| idem, `* @param \|` | Ctrl+Espace | 1. 2. 3. | | |
