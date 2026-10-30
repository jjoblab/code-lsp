#!/bin/bash
# CodeIDE — moteur Kotlin (étage 0) : point d'entrée de benchmark SUR APPAREIL.
# Déclinaison de spike/appareil/banc-appareil.sh (étude Java — §6.c du prompt
# de mission) pour la porte d'entrée de l'étage 1 : « mesure sur appareil »
# (décision Java §10.2 transposée, synthèse Kotlin §6.3).
#
# ⚠ AUCUN résultat appareil n'est prétendu sans l'exécution de ce script par
# le propriétaire et le retour du gabarit rempli (R5). Les mesures JVM du bac
# à sable (:kotlin:bench, rapport d'étage 0) sont des LIMITES BASSES.
#
# Préparation (une fois, sur l'appareil, Termux) :
#   pkg update && pkg install openjdk-17 git unzip
#   git clone --depth 1 -b etude/moteur-kotlin https://github.com/jjoblab/code-lsp.git
#   cd code-lsp
#
#   # Kit d'exécution « kit-kotlin.zip » (jar du module + classes du banc +
#   # piles — octets identiques aux mesures de bac à sable) : l'assembler sur
#   # une machine de build puis le transférer :
#   #   ./gradlew :kotlin:jar :kotlin:compileBenchKotlin
#   #   mkdir -p kit-kotlin/{libs,banc}
#   #   cp kotlin/build/libs/kotlin-*.jar kit-kotlin/libs/
#   #   cp -r kotlin/build/classes/kotlin/bench/* kit-kotlin/banc/
#   #   find ~/.gradle/caches/modules-2 -name 'syntax-api-jvm-0.3.340.jar' -exec cp {} kit-kotlin/libs/ \;
#   #   find ~/.gradle/caches/modules-2 -name 'lang-syntax-jvm-0.3.340.jar' -exec cp {} kit-kotlin/libs/ \;
#   #   find ~/.gradle/caches/modules-2 -name 'annotations-26.0.2.jar' -exec cp {} kit-kotlin/libs/ \;
#   #   find ~/.gradle/caches/modules-2 -name 'kotlin-stdlib-2.2.10.jar' -exec cp {} kit-kotlin/libs/ \;
#   #   zip -r kit-kotlin.zip kit-kotlin
#   unzip -o kit-kotlin.zip -d kit    # à la racine du clone
#
# Exécution :
#   cd spike-kotlin
#   bash appareil/banc-appareil.sh ../kit/kit-kotlin > resultats-appareil.txt
#
# Puis remplir appareil/GABARIT-RESULTATS.md et le joindre au ticket de
# l'étage 1 (porte d'entrée : la mesure appareil conditionne l'ouverture).
set -uo pipefail

KIT="${1:-../kit/kit-kotlin}"
cd "$(dirname "$0")/.."
echo "=============================================================="
echo " banc-appareil — moteur Kotlin étage 0 ($(date '+%Y-%m-%d %H:%M:%S'))"
echo " hôte : $(uname -m) / $(getprop ro.build.version.release 2>/dev/null || echo 'non-Android') / $(getprop ro.product.model 2>/dev/null || echo '?')"
echo " java : $(java -version 2>&1 | head -1)"
echo "=============================================================="

BANC="$KIT/banc"
LIBS="$KIT/libs"
MOTEUR="$(ls "$LIBS"/kotlin-*.jar 2>/dev/null | head -1)"
SYNTAX="$(ls "$LIBS"/syntax-api-jvm-*.jar 2>/dev/null | head -1)"
LANGSYN="$(ls "$LIBS"/lang-syntax-jvm-*.jar 2>/dev/null | head -1)"
ANNOT="$(ls "$LIBS"/annotations-*.jar 2>/dev/null | head -1)"
STDLIB="$(ls "$LIBS"/kotlin-stdlib-*.jar 2>/dev/null | head -1)"

if [ -z "$MOTEUR" ] || [ -z "$SYNTAX" ] || [ -z "$STDLIB" ] || [ ! -d "$BANC" ]; then
  echo "ÉCHEC : kit incomplet ($KIT) — jar moteur, syntax-api, kotlin-stdlib et"
  echo " classes du banc attendus (cf. en-tête du script pour l'assemblage)."
  exit 1
fi

CP="$BANC:$MOTEUR:$SYNTAX"
[ -n "$LANGSYN" ] && CP="$CP:$LANGSYN"
[ -n "$ANNOT" ] && CP="$CP:$ANNOT"
CP="$CP:$STDLIB"

# Banc d'empreinte/latence de la grammaire (nombres — miroir :kotlin:bench).
# JVM bornée 2g / SerialGC : mêmes conditions que le banc de bac à sable.
java -Xmx2g -XX:+UseSerialGC -cp "$CP" jo.codeide.lsp.kotlin.banc.BancGrammaireKt

# Scan ART (échantillon — le jar moteur seul) : le scanner du spike est
# embarqué dans le kit par l'assembleur (copie de spike-kotlin/scripts/).
if [ -f "$KIT/scan-art.py" ] && command -v python3 >/dev/null 2>&1; then
  echo "---"
  python3 "$KIT/scan-art.py" "$MOTEUR"
fi

echo "=============================================================="
echo " Terminé — remplir appareil/GABARIT-RESULTATS.md et le joindre au"
echo " ticket de l'étage 1 (aucune mesure appareil n'est présumée, R5)."
echo "=============================================================="
