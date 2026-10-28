#!/bin/bash
# CodeIDE — étude moteur Java PSI : point d'entrée de benchmark SUR APPAREIL.
# §6.c du prompt de mission : « fournir un point d'entrée de benchmark
# exécutable sur appareil par le propriétaire (Termux/AndroidIDE), avec
# procédure et gabarit de résultats à remplir ».
#
# ⚠ AUCUN résultat appareil n'est prétendu sans l'exécution de ce script par
# le propriétaire et le retour du gabarit rempli (R5). Les mesures JVM du
# rapport 02-spike-mesures.md sont des LIMITES BASSES.
#
# Préparation (une fois, sur l'appareil, Termux) :
#   pkg update && pkg install openjdk-17 git   # ou openjdk-21 si disponible
#   git clone --depth 1 -b etude/moteur-java https://github.com/jjoblab/code-lsp.git
#   cd code-lsp/spike
#   bash scripts/telecharger-piles.sh ../jarres
#   # + copier les jars fastutil/trove4j/automaton/asm-all (coordonnées dans
#   #   les .pom de la pile V2) vers ../jarres/v2/ sous les noms du présent script
#
# Compilation des bancs (sur bureau OU sur appareil si kotlinc disponible) :
#   sur bureau : compiler puis copier build/, build-v2/, build-v3/,
#   build-scan/ dans le spike/ de l'appareil (les .class sont portables).
#
# Exécution :
#   bash appareil/banc-appareil.sh ../jarres > resultats-appareil.txt
#
# Puis remplir appareil/GABARIT-RESULTATS.md et le joindre au ticket d'étude.
set -uo pipefail

JARS="${1:-../spike-jars}"
cd "$(dirname "$0")/.."
echo "=============================================================="
echo " banc-appareil — étude moteur Java PSI ($(date '+%Y-%m-%d %H:%M:%S'))"
echo " hôte : $(uname -m) / $(getprop ro.build.version.release 2>/dev/null || echo 'non-Android') / $(getprop ro.product.model 2>/dev/null || echo '?')"
echo " java : $(java -version 2>&1 | head -1)"
echo "=============================================================="

KS="$JARS/kotlin-stdlib-2.2.20.jar"
V1J="$JARS/v1/java-syntax-jvm-0.3.381.jar:$JARS/v1/syntax-api-jvm-0.3.381.jar:$JARS/v1/lang-syntax-jvm-0.3.381.jar:$JARS/v1/ij-multiplatform-util-jvm-0.3.381.jar:$KS"

# pile V2 minimale (23 jars) — même liste que resultats/scan-art-v2-minimale.txt
V2J="$JARS/v2/core-232.4652.jar:$JARS/v2/util-232.4652.jar:$JARS/v2/util-base-232.4652.jar:$JARS/v2/util-rt-232.4652.jar:$JARS/v2/util-class-loader-232.4652.jar:$JARS/v2/util-jdom-232.4652.jar:$JARS/v2/util-xml-dom-232.4652.jar:$JARS/v2/extensions-232.4652.jar:$JARS/v2/core-impl-232.4652.jar:$JARS/v2/java-232.4652.jar:$JARS/v2/java-psi-232.4652.jar:$JARS/v2/java-psi-impl-232.4652.jar:$JARS/v2/java-indexing-impl-232.4652.jar:$JARS/v2/guava-32.0.1-jre.jar:$JARS/v2/fastutil.jar:$JARS/v2/trove4j.jar:$JARS/v2/streamex-0.8.1.jar:$JARS/v2/automaton-1.11-8.jar:$JARS/v2/asm-all-9.5.jar:$JARS/v2/kotlin-stdlib-1.8.20.jar:$JARS/v2/kotlinx-coroutines-core-jvm-1.6.4.jar:$JARS/v2/kotlinx-coroutines-jdk8-1.6.4.jar:$JARS/v2/annotations-24.0.1.jar"

ECJ="$JARS/ecj-3.40.0.jar"

# 0. Scanner statique ART (vérifie que le jar est bien celui mesuré)
echo "--- ScanArt (échantillon V1)"
java -cp build-scan:"$KS" ScanArtKt "$JARS/v1/java-syntax-jvm-0.3.381.jar" | tail -2

# 1. Génération du corpus (déterministe, graine 42)
echo "--- corpus"
java -cp build GenerateurCorpusKt corpus/Fichier500.java 500

# 2. V1 — parseur java-syntax seul
echo "=== V1 (java-syntax seul)"
java -Xmx1g -cp "build:$V1J" BancV1Kt corpus/Fichier500.java 200 50

# 3. V2 — PSI headless complet (+ home factice)
echo "=== V2 (PSI headless 232.4652)"
java -Xmx1g -Didea.home.path="$(pwd)/app-home" -cp "build-v2:$V2J" \
    BancV2Kt corpus/Fichier500.java "$JARS/v2/guava-32.0.1-jre.jar" 100

# 4. V3 — référence ecj
echo "=== V3 (ecj 3.40.0 non patché)"
java -Xmx1g -cp "build-v3:$ECJ:$KS" BancV3Kt corpus/Fichier500.java "$JARS/v2/guava-32.0.1-jre.jar" 200

echo "=============================================================="
echo " Terminé. Reporter ces sorties dans appareil/GABARIT-RESULTATS.md"
echo "=============================================================="
