#!/usr/bin/env bash
# preparer-projet-android.sh — télécharge les bibliothèques RÉELLES du banc
# projet Android (bancs/projet-android) : plateforme android.jar 34-ext12,
# AARs androidx/material et leurs classes.jar extraits.
#
# Usage : bash scripts/preparer-projet-android.sh
# Cible : bancs/projet-android-libs/ (git-ignoré, R6.4 — jamais committé)
#
# Sources (mêmes URL que la CI, .github/workflows/ci.yml) :
#   - plateforme : dl.google.com/android/repository/platform-34-ext12_r01.zip
#   - AARs/jars  : maven.google.com (coordonnées Gradle de
#                  bancs/projet-android/gradle/libs.versions.toml)
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
LIBS="$REPO/bancs/projet-android-libs"
mkdir -p "$LIBS"
cd "$LIBS"

telecharger() {  # telecharger <fichier> <url>
    if [ -s "$1" ]; then
        echo "· $1 déjà présent ($(stat -c%s "$1") octets) — conservé"
    else
        echo "· téléchargement de $1…"
        curl -sSfL --retry 3 -o "$1" "$2"
    fi
}

# 1. Plateforme Android 34 ext12 (android.jar — la surface de compilation).
if [ ! -s android-34-ext12.jar ]; then
    curl -sSfL --retry 3 -o p34.zip \
        "https://dl.google.com/android/repository/platform-34-ext12_r01.zip"
    unzip -o -q p34.zip 'android-34-ext12/android.jar' -d .
    mv android-34-ext12/android.jar android-34-ext12.jar
    rm -rf android-34-ext12 p34.zip
fi

# 2. AARs et jars des coordonnées Gradle du projet (versions de
#    libs.versions.toml — la fermeture utile pour la résolution ecj).
telecharger appcompat-1.7.0.aar \
    "https://maven.google.com/androidx/appcompat/appcompat/1.7.0/appcompat-1.7.0.aar"
telecharger material-1.12.0.aar \
    "https://maven.google.com/com/google/android/material/material/1.12.0/material-1.12.0.aar"
telecharger constraintlayout-2.1.4.aar \
    "https://maven.google.com/androidx/constraintlayout/constraintlayout/2.1.4/constraintlayout-2.1.4.aar"
telecharger core-1.13.1.aar \
    "https://maven.google.com/androidx/core/core/1.13.1/core-1.13.1.aar"
telecharger annotation-1.8.0.jar \
    "https://maven.google.com/androidx/annotation/annotation-jvm/1.8.0/annotation-jvm-1.8.0.jar"
telecharger lifecycle-runtime-2.6.2.aar \
    "https://maven.google.com/androidx/lifecycle/lifecycle-runtime/2.6.2/lifecycle-runtime-2.6.2.aar"
telecharger drawerlayout-1.2.0.aar \
    "https://maven.google.com/androidx/drawerlayout/drawerlayout/1.2.0/drawerlayout-1.2.0.aar"
telecharger fragment-1.7.1.aar \
    "https://maven.google.com/androidx/fragment/fragment/1.7.1/fragment-1.7.1.aar"
telecharger databinding-runtime-8.4.2.aar \
    "https://maven.google.com/androidx/databinding/databinding-runtime/8.4.2/databinding-runtime-8.4.2.aar"
# Fermeture transitive RÉELLE (banc du 31/10 : AppCompatActivity →
# FragmentActivity → ComponentActivity, ViewDataBinding → ViewBinding…) —
# la hiérarchie doit être COMPLÈTE pour la résolution ecj, sinon
# « The hierarchy of the type MainActivity is inconsistent ».
telecharger activity-1.8.2.aar \
    "https://maven.google.com/androidx/activity/activity/1.8.2/activity-1.8.2.aar"
telecharger lifecycle-viewmodel-2.6.2.aar \
    "https://maven.google.com/androidx/lifecycle/lifecycle-viewmodel/2.6.2/lifecycle-viewmodel-2.6.2.aar"
telecharger lifecycle-viewmodel-savedstate-2.6.2.aar \
    "https://maven.google.com/androidx/lifecycle/lifecycle-viewmodel-savedstate/2.6.2/lifecycle-viewmodel-savedstate-2.6.2.aar"
telecharger savedstate-1.2.1.aar \
    "https://maven.google.com/androidx/savedstate/savedstate/1.2.1/savedstate-1.2.1.aar"
telecharger tracing-1.0.0.aar \
    "https://maven.google.com/androidx/tracing/tracing/1.0.0/tracing-1.0.0.aar"
telecharger emoji2-1.3.0.aar \
    "https://maven.google.com/androidx/emoji2/emoji2/1.3.0/emoji2-1.3.0.aar"
telecharger core-viewtree-1.0.0.aar \
    "https://maven.google.com/androidx/core/core-viewtree/1.0.0/core-viewtree-1.0.0.aar"
telecharger viewbinding-8.4.2.aar \
    "https://dl.google.com/dl/android/maven2/androidx/databinding/viewbinding/8.4.2/viewbinding-8.4.2.aar"
telecharger lifecycle-common-2.6.2.jar \
    "https://dl.google.com/dl/android/maven2/androidx/lifecycle/lifecycle-common/2.6.2/lifecycle-common-2.6.2.jar"
telecharger databinding-common-8.4.2.jar \
    "https://dl.google.com/dl/android/maven2/androidx/databinding/databinding-common/8.4.2/databinding-common-8.4.2.jar"

# 3. Extraction des classes.jar (le classpath réel qu'assemble Gradle).
extraire() {  # extraire <aar> <dest>
    if [ -s "$2" ]; then
        echo "· $2 déjà extrait — conservé"
    else
        unzip -p "$1" classes.jar > "$2"
        echo "· $2 extrait ($(stat -c%s "$2") octets)"
    fi
}
extraire appcompat-1.7.0.aar appcompat-classes.jar
extraire material-1.12.0.aar material-classes.jar
extraire constraintlayout-2.1.4.aar constraintlayout-classes.jar
extraire core-1.13.1.aar core-classes.jar
extraire lifecycle-runtime-2.6.2.aar lifecycle-runtime-classes.jar
extraire drawerlayout-1.2.0.aar drawerlayout-classes.jar
extraire fragment-1.7.1.aar fragment-classes.jar
extraire databinding-runtime-8.4.2.aar databinding-runtime-classes.jar
extraire activity-1.8.2.aar activity-classes.jar
extraire lifecycle-viewmodel-2.6.2.aar lifecycle-viewmodel-classes.jar
extraire lifecycle-viewmodel-savedstate-2.6.2.aar lifecycle-viewmodel-savedstate-classes.jar
extraire savedstate-1.2.1.aar savedstate-classes.jar
extraire tracing-1.0.0.aar tracing-classes.jar
extraire emoji2-1.3.0.aar emoji2-classes.jar
extraire core-viewtree-1.0.0.aar core-viewtree-classes.jar
extraire viewbinding-8.4.2.aar viewbinding-classes.jar

echo "OK — bibliothèques réelles prêtes dans $LIBS"
