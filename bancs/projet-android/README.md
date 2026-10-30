# bancs/projet-android/ — le projet Android RÉEL du banc (INTEGRATION-CODEIDE §13)

> Réplique fidèle du projet utilisateur « monip » (retours appareil du
> 31/10/2026) : `com.example.monip`, `MainActivity` extends
> `AppCompatActivity`, data binding `ActivityMainBinding`, `Greeter`,
> layout `<layout>` avec variable `liaison`.

Ce dossier est la STRUCTURE commise (sources, fichiers Gradle, manifest,
layout). Les BIBLIOTHÈQUES RÉELLES (android.jar 34-ext12, 16 AARs/jars
androidx/material — la fermeture transitive vérifiée classe par classe)
sont téléchargées par `scripts/preparer-projet-android.sh` dans
`bancs/projet-android-libs/` (git-ignoré, R6.4 — jamais committées).

Les sources GÉNÉRÉES par AGP (R.java, ActivityMainBinding.java) vivent dans
`bancs/projet-android-genere/` — le simulacre déterministe de ce que la
sync Gradle nourrit au LSP comme racine additionnelle.

Le banc lui-même : `java/src/test/kotlin/jo/codeide/lsp/java/protocole/
BancProjetAndroidReelTest.kt` (20 tests ordonnés — la timeline appareil
rejouée au protocole, puis chaque famille de complétion avec la précision
Android Studio).

```bash
bash scripts/preparer-projet-android.sh
BANC_ANDROID_LIBS=../bancs/projet-android-libs ./gradlew :java:test \
    --tests "jo.codeide.lsp.java.protocole.BancProjetAndroidReelTest"
```
