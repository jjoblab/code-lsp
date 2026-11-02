# Corpus réel Android Java (banc P1–P8, vague 0 — ADR 0030)

> Répertoire **jamais committé** (`.gitignore` : `lsp-java/bancs/corpus-android/`) — le
> contenu arrive par `lsp-java/scripts/telecharger-corpus-android.sh`, épinglé commit par
> commit. Les projets sont des **données de corpus** (cibles de test) inscrites au
> registre `lsp-java/docs/PROVENANCE-REPRISE.md` §Corpus avec leur rôle.

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
lsp-java/bancs/corpus-android/prepares/<projet>.manifeste
racine:<chemin absolu d'une racine de sources écrites à la main>
racine-generee:<chemin absolu du PARENT DU PAQUET des sources générées>
classpath:<chemin absolu d'un jar (plateforme, artefact, R.jar, classes Kotlin)>
```

**Procédure** — tout est outillé (V-A2, 01/11/2026) :

1. `lsp-java/scripts/telecharger-corpus-android.sh` (sources épinglées).
2. `ANDROID_HOME=<sdk> JAVA21_HOME=<jdk> [JAVA17_HOME=<jdk>] bash
   lsp-java/scripts/preparer-manifestes-corpus.sh [projet…]` — pour chaque projet :
   compile la variante mesurée avec le Gradle ÉPINGLÉ du projet
   (`compile<Variante>JavaWithJavac` : génère R, BuildConfig, binding, safeargs,
   Room ET les classes Kotlin `built_in_kotlinc`), exporte le classpath RÉELLEMENT
   résolu par l'init script (`manifeste-corpus.init.gradle` : vue d'artefats
   `android-classes-jar` — la vue EXACTE de javac, **jetifier** compris, AAR
   extraits), ajoute les **R.jar** d'AGP par module, zippé les classes **Kotlin**
   compilées par module, et écrit le manifeste (racines générées au PARENT DU
   PAQUET). Un projet dont la résolution est incomplète (replis
   `ARTIFACT-ERREUR`/`ARTIFACT-ABSENT`) ne produit PAS de manifeste : rejouer le
   script reprend sur le cache Gradle (429 Maven Central : cf. ALERTES AL4).
3. `CORPUS_ANDROID=lsp-java/bancs/corpus-android ./gradlew :java:test --tests 'jo.codeide.lsp.java.coherence.*'`
   — le tas du JVM de test passe alors de 640 Mio à 2 048 Mio (conditionné à
   `CORPUS_ANDROID`, `lsp-java/moteur/build.gradle.kts`).

> **Passe complémentaire SANS build (02/11) — diagnostic du repli lexical
> avant build** : `lsp-java/scripts/ask-export.init.gradle` (tâche `exportClasspathAsk
> -PsortieAsk=<dir>`, générique) résout la copie `debugCompileClasspath` de
> chaque module avec les dépendances projet SUBSTITUÉES par un module « void »
> local — AUCUN build de projet (ni javac ni CMake), donc AUCUNE classe
> `built_in_kotlinc` : les sources fraternelles couvrent les dépendances projet
> via les `racine:`, et le REPLI lexical Kotlin (ADR 0022) est exercé TEL QUEL
> — c'est cette passe qui a exposé la `open class` synthétisée finale
> (correctif v1.12.2), invisible du montage canonique qui fournit les classes
> compilées. `lsp-java/scripts/preparer-manifeste-ask.sh` (déclinaison AnySoftKeyboard)
> assemble le manifeste : android.jar, R.jar debug (`generateDebugRFile` +
> `:ime:app:processDebugResources`, plus `:ime:app:generateDebugBuildConfig`
> après coup), AAR réduits à leur classes.jar sous `prepares/ask-libs/`, et
> racines générées remontées au répertoire CONTENANT l'arbre de paquets (la
> déclaration `package` du fichier fait foi). PV :
> mesures/2026-11-02-va2-complement-repli-lexical.txt.

> **Note d'honnêteté (R5)** : le manifeste dépend du SDK/Gradle locaux ; il n'est
> pas committé. Tant qu'il n'existe pas, la gate corpus s'ignore **avec la raison
> affichée** (`assumeTrue`) — jamais un vert silencieux. La métrique FP/1 000 lignes
> produite ensuite est la **baseline d'écart** de l'écart G-034 (ADR 0030). Les
> leçons de montage (R.jar, jetifier, classes Kotlin, parents de paquet) sont
> consignées dans `lsp-java/docs/etudes/niveau-android-studio/08-rapport-divergences-ecj-javac.md` §3.

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
