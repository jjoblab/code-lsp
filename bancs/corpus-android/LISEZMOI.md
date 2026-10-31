# Corpus réel Android Java (banc P1–P8, vague 0 — ADR 0030)

> Répertoire **jamais committé** (`.gitignore` : `bancs/corpus-android/`) — le
> contenu arrive par `scripts/telecharger-corpus-android.sh`, épinglé commit par
> commit. Les projets sont des **données de corpus** (cibles de test) inscrites au
> registre `docs/PROVENANCE-REPRISE.md` §Corpus avec leur rôle.

## 1. Les 5 projets (épinglés le 31/10/2026 par `git ls-remote`)

| Clé | Dépôt | Licence | Rôle | Commit |
|---|---|---|---|---|
| `opentracks` | OpenTracksApp/OpenTracks | Apache-2.0 | corpus principal : 99,7 % Java, **viewBinding**, AndroidX/Material/ConstraintLayout | `3c23a9f5400c7b5b914a221469d76cc019a18296` |
| `markor` | gsantner/markor | Apache-2.0 | vues classiques sans binding, très actif | `7b15e66932f170c0b3f105c522111b0f32d6c73d` |
| `gh4a` | slapperwan/gh4a | Apache-2.0 | Retrofit 3 + OkHttp + RxJava2 + WorkManager | `39f03ffe122125e46a51de16d90881047ed4660e` |
| `anysoftkeyboard` | AnySoftKeyboard/AnySoftKeyboard | Apache-2.0 | multi-modules, base Java la plus large | `8cf81a3a2f73e9914c8613d56390a933486f0c6b` |
| `basicsample` | android/architecture-components-samples (module BasicSample) | Apache-2.0 | **DataBinding + Room** + annotationProcessor Java | `e849ce3004ccd1132a121cf513bbcb7996d95c30` |

Éliminés (vérifiés, raisons dans l'audit §5) : Thunderbird-android (82,8 % Kotlin),
AntennaPod / gpslogger / AmazeFileManager / LibreTorrent (GPL), Telecine (Gradle
obsolète), bitcoin-wallet (sans LICENSE). Secours : dmfs/opentasks
(`3ccb6f9d0195795bd461c3e634bbb8b22c72932c`, Apache-2.0, Java 100 %, inactif).

## 2. Préparation du classpath réel (manifestes)

Les propriétés P3/P4/P7 sur corpus réel exigent le **classpath compilé** de chaque
projet (sinon les `cannot be resolved` sur `androidx.*` seraient des faux positifs
de montage, pas du moteur). La préparation produit UN manifeste par projet :

```
bancs/corpus-android/prepares/<projet>.manifeste
racine:<chemin absolu d'une racine de sources>
racine:<...>                      # une ligne par racine (main, debug, générés…)
classpath:<chemin absolu d'un jar> # une ligne par entrée (android.jar, AAR, jars)
```

**Procédure (CI ou poste avec SDK Android)** — la même que les gates `ANDROID_JAR`
du dépôt (README §Construction) :

1. `scripts/telecharger-corpus-android.sh` (sources épinglées).
2. Pour chaque projet : exporter le classpath de compilation de la variante debug
   via Gradle (`./gradlew :app:dependencies` ou tâche d'export maison) — en
   extraire les jars et les racines de sources (y compris `build/generated/…`
   après un build).
3. Écrire le manifeste (chemins ABSOLUS).
4. `CORPUS_ANDROID=bancs/corpus-android ./gradlew :java:test --tests 'jo.codeide.lsp.java.coherence.*'`.

> **Note d'honnêteté (R5)** : le manifeste dépend du SDK/Gradle locaux ; il n'est
> pas committé. Tant qu'il n'existe pas, la gate corpus s'ignore **avec la raison
> affichée** (`assumeTrue`) — jamais un vert silencieux. La métrique FP/1 000 lignes
> produite ensuite est la **baseline d'écart** de l'écart G-034 (ADR 0030).

## 3. Ce que le corpus mesure

- **P3** : zéro faux positif d'erreur sur code qui compile (FP/1 000 lignes, par
  famille d'ID ecj — inclut la mesure des divergences ecj↔javac, les builds AGP
  compilant avec javac).
- **P4** : rappel de vrais positifs par mutation (sur les mêmes sources).
- **P7** : rename sûr sur du code réel (multi-fichiers).
- **P6/P1/P2/P8** : jouées sur corpus synthétique intégré (toujours actives).

Le split train/éval (anti-sur-apprentissage, ADR 0030) : les cas dorés de
développement vivent dans les tests ; les positions d'évaluation du corpus ne
servent QU'À mesurer — jamais à régler le moteur.
