# Corpus réel Android Kotlin (banc de cohérence P1–P9, vague 0)

> Répertoire **jamais committé** (`.gitignore` : `bancs/corpus-android-kotlin/`)
> — le contenu arrive par `scripts/telecharger-corpus-kotlin.sh`, épinglé commit
> par commit. Les dépôts sont des **données de corpus** (cibles de mesure) :
> ils sont les SEULS projets tiers nommés dans le code du banc (liste blanche
> du corpus, Apache-2.0).

## 1. Les 6 projets (épinglés le 31/10/2026 par `git ls-remote` — CONFIRMÉ)

| Clé | Dépôt | Licence | Rôle | Commit |
|---|---|---|---|---|
| `nowinandroid` | android/nowinandroid | Apache-2.0 | Compose Material 3, Hilt/Room par KSP, Retrofit + kotlinx-serialization, WorkManager, coroutines/Flow ; 40 fichiers `.kts` + catalogue `libs.versions.toml` (310 .kt / 29 336 l.) | `2982e63cb691ad49cd009aab43dc206fee6699c2` |
| `sunflower` | android/sunflower | Apache-2.0 | Views + ViewBinding + Compose + Room (66 .kt / 4 759 l.) | `2a357a31551bb53f3fe80382a9ce6d30bcc8b960` |
| `architecture` | android/architecture-samples (module `todoapp`) | Apache-2.0 | Views + ViewBinding MVVM (55 .kt / 6 049 l.) | `ee66e1526b84c026615df032c705842b7d2a521f` |
| `compose` | android/compose-samples (modules `Jetcaster`, `Jetsnack`) | Apache-2.0 | Jetcaster (Flow/coroutines) et Jetsnack (Compose pur) | `25631e8f544a9a1e3f01e296eaf1b4c28428196c` |
| `viewswidgets` | android/views-widgets-samples | Apache-2.0 | projet MIXTE Java (94 fichiers / 13 251 l.) + Kotlin (104 / 7 739 l.) — le seul rôle interop réel du corpus | `2238cc873501f9cda63605051de11832bb736a8a` |
| `platform` | android/platform-samples | Apache-2.0 | Kotlin moderne, APIs plateforme (336 .kt / 47 131 l.) | `177c3b92c387894648d0a2d42d238e265b05bbc3` |

Éliminés (vérifiés au 31/10/2026, raisons de l'audit du propriétaire) :
**camera-samples** (0 fichier Java mesuré + licence non détectée par l'API au
31/10), **gh4a** (0 fichier `.kt` au commit épinglé), **thunderbird-android**
(mixte réel mais MPL-2.0 — hors politique « licence permissive » du corpus ;
candidat secours SI le propriétaire arbitre MPL-2.0 acceptable).

## 2. Préparation du classpath réel (manifestes)

Les propriétés P1(b)/P2 sur corpus réel exigent le **classpath compilé** de
chaque projet (sinon les `kt.unresolved` sur `androidx.*` seraient des faux
positifs de montage, pas du moteur). La préparation produit UN manifeste par
projet :

```
bancs/corpus-android-kotlin/prepares/<projet>.manifeste
racine:<chemin absolu d'une racine de sources>
racine:<...>                      # une ligne par racine (main, debug, générés…)
classpath:<chemin absolu d'un jar> # une ligne par entrée (android.jar, kotlin-stdlib, AAR…)
```

**Procédure (CI ou poste avec SDK Android)** — la même que les gates corpus
du domaine Java (`bancs/corpus-android/LISEZMOI.md`) :

1. `scripts/telecharger-corpus-kotlin.sh` (sources épinglées).
2. Pour chaque projet : exporter le classpath de compilation de la variante
   debug via Gradle (`./gradlew :app:dependencies` ou tâche d'export maison)
   — en extraire les jars et les racines de sources (y compris
   `build/generated/…` après un build). Le manifeste DOIT inclure le
   `kotlin-stdlib` et l'`android.jar` (le plancher d'appelables du moteur).
3. Écrire le manifeste (chemins ABSOLUS).
4. `CORPUS_KOTLIN=bancs/corpus-android-kotlin ./gradlew :kotlin:test --tests 'jo.codeide.lsp.kotlin.coherence.*' --max-workers=1`.

> **Note d'honnêteté (R5)** : le manifeste dépend du SDK/Gradle locaux ; il
> n'est JAMAIS committé. Tant qu'il n'existe pas, les gates corpus s'ignorent
> **avec la raison affichée** (`assumeTrue`) — jamais un vert silencieux. La
> métrique FP/1 000 lignes qui en sortira est la **baseline d'écart** de la
> mesure P1(b) : un écart est un RÉSULTAT à imprimer, pas un silence.

## 3. Ce que le corpus mesure

- **P1(b)** : fidélité différentielle moteur vs oracle K2 (compilateur réel en
  sous-processus) sur les fichiers des racines — FP, FN, FP/1 000 lignes,
  répartition par code `kt.*`.
- **P2** : zéro faux positif d'erreur `kt.*` sur code qui compile
  (FP/1 000 lignes par famille).
- **P1(c)** (optionnel, `corpus.kotlin.sansClasspath=true`) : divergence de
  comportement D3 moteur/oracle sur du vrai code Android SANS classpath compilé
  (les deux voient le même univers vide) — mesurable sur `sunflower` seul.

Les autres propriétés (P3–P9) vivent sur l'**auto-corpus** (le module `:kotlin`
lui-même — du vrai Kotlin compilable) et des documents synthétiques : elles
tournent TOUJOURS en CI, sans corpus téléchargé.

Le split train/éval (anti-sur-apprentissage, miroir du corpus Java) : les cas
dorés de développement vivent dans les fiches du cahier ; les positions du
corpus ne servent QU'À mesurer — jamais à régler le moteur.
