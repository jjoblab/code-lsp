# 06 — Protocole de captures Android Studio

> Ce que le **propriétaire** doit capturer/confirmer sur Android Studio (l'environnement
> d'étude ne peut pas l'exécuter — R5 : aucune mesure simulée). Modèle : les captures
> servent de vérité terrain qualitative aux fiches C-xx quand la documentation ne
> suffit pas (points marqués NV dans `02-reference-android-studio.md`).
>
> **Matériel** : Android Studio courant (Ladybug ou plus récent), un projet Android
> Java de test (utiliser `bancs/projet-android/` du dépôt, ou n'importe quel corpus
> du §5 de l'audit), enregistrement d'écran ou captures d'écran + texte copié.

## 1. Protocole général

Pour chaque capture : (a) fichier + position exacte (ligne:colonne), (b) geste
(caret, frappe, Alt+Enter…), (c) **liste attendue top-10 copiée en texte** (pas
seulement l'image — l'ordre compte), (d) temps perçu (immédiat / visible / lent).
Ranger dans `docs/etudes/niveau-android-studio/captures/` (le propriétaire fournit,
l'agent consigne).

## 2. Captures demandées (priorisées)

### Lot A — Complétion (le sujet le plus urgent, demande du 31/10)

| # | Position | Ce qu'on veut apprendre |
|---|---|---|
| A1 | `String s = |` (locales `String nom`, `int age` en portée) | Ordre exact du filtrage par type attendu (smart) : positions relatives de `nom`/`age`, présence des non-compatibles |
| A2 | `list.add(|)` sur `List<String>` | Même chose en argument + comportement sur surcharges |
| A3 | `a.getB().get|` (types en chaîne) | Comportement chain : que propose la 1re invocation ; que la 2e |
| A4 | 2e puis 3e invocation à la même position | Membres inaccessibles (marqués comment ?), import statique, projet entier |
| A5 | `setMode(|)` avec `@IntDef` sur le paramètre | Constantes typedef : ordre, filtrage, import auto |
| A6 | `R.string.|` et `getString(R.string.|)` | Complétion des ressources : ordre, décoration (icônes ?), ressources privées filtrées ? |
| A7 | `Toast.makeText(c, "|` (chaîne ouvrante) | Littéraux contextuels dans les chaînes : proposés ou pas |
| A8 | `class Principal e|` (tête de déclaration) | Mots-clés `extends`/`implements` proposés en même temps que les stubs ? (amendement fiche 007 — D7) |
| A9 | Frappe `import ja|`, `imp|`, `i|` | Rappel du comportement top-level (comparaison v1.1.6 C1) |
| A10 | Noms de variables : `Button | = findViewById(...)` | Suggestions de noms dérivées du type (ordre exact) |

### Lot B — Diagnostics et inspections

| # | Scénario | Ce qu'on veut apprendre |
|---|---|---|
| B1 | `a == b` sur String + Alt+Enter | Message exact du quick-fix « equals » + présence de l'option « unsafe » |
| B2 | Méthode `@NonNull` retournant champ `@Nullable` | Sévérité affichée (Warning/Weak), message exact de DataFlowIssue |
| B3 | `@SuppressWarnings("StringEquality")` | L'ID accepté (ou token alternatif) — confirme W1 |
| B4 | Appel API 33 sous minSdk 21 (avec et sans garde `SDK_INT`) | NewApi : sévérité, message, les fixes proposés (garde/@RequiresApi) — confirme N1/N3/N5 |
| B5 | `@CallSuper` non appelé | Message + fix MissingSuperCall |
| B6 | Projet **sans build** : `R.string.x` et `ActivityMainBinding` | Confirmations C-01/C-02 : zéro rouge avant build ? hover exact ? |
| B7 | Supprimer une méthode utilisée dans `B.java` (les deux ouverts) | Délai et comportement de re-highlight de B — confirme l'attente P8 (< 300 ms) |

### Lot C — Performance perçue (chiffres)

| # | Scénario | Mesure |
|---|---|---|
| C1 | Ouverture du même corpus (5 projets) sur Android Studio | Temps d'indexation affiché (« scanning/analyzing »), mémoire du processus, temps avant première complétion utile |
| C2 | Frappe normale pendant 30 s dans un fichier 500 lignes | Régularité perçue (figes ?), latence approximative d'apparition du popup |
| C3 | Renommage d'une méthode très utilisée | Temps total du dialogue usages |
| C4 | Pendant l'indexation : complétion `R.string.` | Ce qui est servi en dumb mode (dégradation annoncée comment ?) — alimente l'axe de dépassement §0.1-1 |

### Lot D — Points NV de l'étude

| # | Point NV | Capture |
|---|---|---|
| D1 | Hover Java d'un champ `R.string.x` | Contenu exact (valeur résolue ? fichier ?) |
| D2 | Champs standards de `BuildConfig` | Liste réelle générée (debug/release) |
| D3 | Distinction lecture/écriture dans find usages | Options affichées dans le dialogue courant |
| D4 | `@Dimension/@Px` | Y a-t-il un diagnostic/une complétion associée |
| D5 | Profil d'inspections par défaut d'Android Studio | Export du profil (Settings > Editor > Inspections > export) — même partiel |

## 3. Format de restitution

Un fichier Markdown par lot, avec pour chaque item : capture (image), transcription
texte des listes, remarques libres. Ces captures sont **des données de spécification**
(usage interne, clean-room respecté : descriptions de comportement, aucune copie de
code).
