#!/bin/bash
# CodeIDE — étude moteur Java PSI : téléchargement des piles de jars du spike.
# R5 (honnêteté) : chaque mesure du rapport 02-spike-mesures.md est rejouable
# avec ces artefacts exacts. Les jars ne sont PAS commités dans le dépôt.
#
# Usage : bash spike/scripts/telecharger-piles.sh <repertoire-cible>
#   crée <cible>/v1/ et <cible>/v2/ et y télécharge les piles.
set -euo pipefail

CIBLE="${1:-../spike-jars}"
REDIRECT="https://cache-redirector.jetbrains.com"
DEPS="$REDIRECT/packages.jetbrains.team/maven/p/ij/intellij-dependencies"
LABS="$REDIRECT/repo.labs.intellij.net/maven"
CENTRAL="$REDIRECT/repo1.maven.org/maven2"

mkdir -p "$CIBLE/v1" "$CIBLE/v2"

telecharge () { # telecharge <url> <fichier-cible>
    local url="$1" f="$2"
    if [ -s "$f" ]; then echo "déjà présent : $f"; return 0; fi
    echo "télécharge : $(basename "$f")"
    curl -sL -f -o "$f" "$url"
}

echo "=== Pile V1 — org.jetbrains:java-syntax 0.3.381 (publié, autonome) ==="
# Variantes JVM des modules KMP : modules séparés liés par available-at
# (constat consigné dans l'ADR packaging 0005).
for a in java-syntax syntax-api lang-syntax ij-multiplatform-util; do
    telecharge "$LABS/org/jetbrains/$a/0.3.381/$a-jvm-0.3.381.jar" "$CIBLE/v1/$a-jvm-0.3.381.jar"
done

echo "=== Pile V2 — com.jetbrains.intellij.{platform,java}:232.4652 (intellij-dependencies) ==="
# Artefacts publiés par JetBrains pour l'amorçage headless (même repo que
# celui que consomme déjà :lsp:kotlin via cache-redirector, settings.gradle.kts
# de l'ancien dépôt). Conditions d'utilisation : à consigner en ADR 0017.
declare -A ARTEFACTS=(
    [core]=com.jetbrains.intellij.platform:core
    [util]=com.jetbrains.intellij.platform:util
    [util-base]=com.jetbrains.intellij.platform:util-base
    [util-rt]=com.jetbrains.intellij.platform:util-rt
    [util-class-loader]=com.jetbrains.intellij.platform:util-class-loader
    [util-jdom]=com.jetbrains.intellij.platform:util-jdom
    [util-xml-dom]=com.jetbrains.intellij.platform:util-xml-dom
    [extensions]=com.jetbrains.intellij.platform:extensions
    [core-impl]=com.jetbrains.intellij.platform:core-impl
    [java]=com.jetbrains.intellij.java:java
    [java-psi]=com.jetbrains.intellij.java:java-psi
    [java-psi-impl]=com.jetbrains.intellij.java:java-psi-impl
    [java-analysis-impl]=com.jetbrains.intellij.java:java-analysis-impl
    [java-impl]=com.jetbrains.intellij.java:java-impl
    [java-indexing-impl]=com.jetbrains.intellij.java:java-indexing-impl
)
for nom in "${!ARTEFACTS[@]}"; do
    groupe="${ARTEFACTS[$nom]%%:*}"
    arte="${ARTEFACTS[$nom]##*:}"
    chemin="${groupe//./\/}/$artefact"
    telecharge "$DEPS/$chemin/232.4652/$artefact-232.4652.jar" "$CIBLE/v2/$artefact-232.4652.jar"
done

echo "=== Dépendances runtime de la pile V2 ==="
# fastutil/trove4j/automaton : artefacts hébergés intellij-dependencies dont le
# nom exact est dans les .pom de core/util téléchargés ci-dessus. Le banc a
# utilisé les fichiers suivants (noms locaux) :
#   fastutil.jar (4,2 Mo), trove4j.jar (0,6 Mo), automaton-1.11-8.jar (0,2 Mo),
#   asm-all-9.5.jar (0,4 Mo — package ombré org.jetbrains.org.objectweb.asm)
telecharge "$CENTRAL/com/google/guava/guava/32.0.1-jre/guava-32.0.1-jre.jar" "$CIBLE/v2/guava-32.0.1-jre.jar"
telecharge "$CENTRAL/org/jetbrains/kotlin/kotlin-stdlib/1.8.20/kotlin-stdlib-1.8.20.jar" "$CIBLE/v2/kotlin-stdlib-1.8.20.jar"
telecharge "$CENTRAL/org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/1.6.4/kotlinx-coroutines-core-jvm-1.6.4.jar" "$CIBLE/v2/kotlinx-coroutines-core-jvm-1.6.4.jar"
telecharge "$CENTRAL/org/jetbrains/kotlinx/kotlinx-coroutines-jdk8/1.6.4/kotlinx-coroutines-jdk8-1.6.4.jar" "$CIBLE/v2/kotlinx-coroutines-jdk8-1.6.4.jar"
telecharge "$CENTRAL/org/jetbrains/annotations/24.0.1/annotations-24.0.1.jar" "$CIBLE/v2/annotations-24.0.1.jar"
telecharge "$CENTRAL/org/oneutil/streamex/0.8.1/streamex-0.8.1.jar" "$CIBLE/v2/streamex-0.8.1.jar" 2>/dev/null || \
    telecharge "$CENTRAL/one/util/streamex/0.8.1/streamex-0.8.1.jar" "$CIBLE/v2/streamex-0.8.1.jar"

echo "=== Pile V3 — référence ecj (moteur du statu quo, non patché) ==="
telecharge "$CENTRAL/org/eclipse/jdt/ecj/3.40.0/ecj-3.40.0.jar" "$CIBLE/ecj-3.40.0.jar"

echo "=== Kotlin (compilateur des bancs + stdlib d'exécution) ==="
telecharge "$CENTRAL/org/jetbrains/kotlin/kotlin-stdlib/2.2.20/kotlin-stdlib-2.2.20.jar" "$CIBLE/kotlin-stdlib-2.2.20.jar"
if [ ! -d "$CIBLE/kotlinc-dist" ]; then
    echo "télécharge : kotlin-compiler-2.2.20.zip (≈79 Mo)"
    curl -sL -f -o "$CIBLE/kotlin-compiler-2.2.20.zip" "$CENTRAL/org/jetbrains/kotlin/kotlin-compiler/2.2.20/kotlin-compiler-2.2.20.zip"
    unzip -q "$CIBLE/kotlin-compiler-2.2.20.zip" -d "$CIBLE/kotlinc-dist"
fi

echo
echo "TERMINÉ. Piles dans : $CIBLE"
echo "⚠ fastutil/trove4j/automaton/asm-all : coordonnées exactes dans les .pom de la pile V2 (hébergement intellij-dependencies) — à compléter manuellement si re-téléchargement."
