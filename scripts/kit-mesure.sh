#!/usr/bin/env bash
# ============================================================================
# kit-mesure.sh — assemble le KIT DE MESURE APPAREIL « niveau Android
# Studio » (vague 0 — gabarit docs/etudes/niveau-android-studio/
# 07-gabarit-mesure-appareil.md, mesures à retourner par le propriétaire,
# JAMAIS simulées — R5).
#
# Produit build/kit-mesure.zip (jamais committé — R6.4) :
#   banc-classes/   — le banc de mesure + le corpus (compilés)
#   main-classes/   — le moteur (compilé)
#   libs/           — ecj patché (ecj-3.40.0-art.jar) + kotlin-stdlib + lsp4j
#   banc-mesure.sh  — le point d'entrée appareil (Termux OpenJDK 17)
#
# Sur l'appareil (Termux) :
#   unzip -o kit-mesure.zip -d mesure && cd mesure
#   bash banc-mesure.sh > resultats-mesure.txt
# Puis reporter les valeurs dans docs/etudes/niveau-android-studio/
# 07-gabarit-mesure-appareil.md et joindre le transcript brut.
#
# Options appareil :
#   - poser android.jar à côté de banc-mesure.sh (optionnel) : la plateforme
#     vient du jar (plus représentatif d'un projet Android réel) au lieu du
#     jrt du runtime ;
#   - PASSES=1 bash banc-mesure.sh : une seule passe (passe rapide) au lieu
#     des 3 passes espaciées de 30 s exigées par le gabarit §1.
# ============================================================================
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

echap() { printf '\033[1m== %s\033[0m\n' "$*"; }

# --- 1. Compiler le banc + le moteur (bytecode 17, octets déterministes) ---
echap "compilation :java:benchClasses…"
./gradlew :java:benchClasses --no-daemon -q

CLASSES_BANC="java/build/classes/kotlin/bench"
CLASSES_MAIN="java/build/classes/kotlin/main"
JAR_ECJ="ecj-art/build/ecj-patche/ecj-3.40.0-art.jar"

for chemin in "$CLASSES_BANC" "$CLASSES_MAIN" "$JAR_ECJ"; do
    [ -e "$chemin" ] || { echo "ERREUR : $chemin absent (build incomplet)." >&2; exit 1; }
done

# --- 2. Localiser kotlin-stdlib et lsp4j (le classpath d'exécution du banc) --
STDLIB="$(find "$HOME"/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib \
    -name 'kotlin-stdlib-*.jar' ! -name '*sources*' 2>/dev/null | sort | tail -1)"
LSP4J="$(find "$HOME"/.gradle/caches/modules-2/files-2.1/org.eclipse.lsp4j/org.eclipse.lsp4j \
    -name 'org.eclipse.lsp4j-*.jar' ! -name '*sources*' 2>/dev/null | sort | tail -1)"
JSONRPC="$(find "$HOME"/.gradle/caches/modules-2/files-2.1/org.eclipse.lsp4j/org.eclipse.lsp4j.jsonrpc \
    -name 'org.eclipse.lsp4j.jsonrpc-*.jar' ! -name '*sources*' 2>/dev/null | sort | tail -1)"
for var in STDLIB LSP4J JSONRPC; do
    [ -n "${!var}" ] || { echo "ERREUR : jar \$$var introuvable dans le cache Gradle." >&2; exit 1; }
done
echap "stdlib: $(basename "$STDLIB") · lsp4j: $(basename "$LSP4J")"

# --- 3. Assembler le kit ---------------------------------------------------
KIT="build/kit-mesure"
rm -rf "$KIT" build/kit-mesure.zip
mkdir -p "$KIT/libs"
cp -r "$CLASSES_BANC" "$KIT/banc-classes"
cp -r "$CLASSES_MAIN" "$KIT/main-classes"
cp "$JAR_ECJ" "$KIT/libs/"
cp "$STDLIB" "$LSP4J" "$JSONRPC" "$KIT/libs/"

cat > "$KIT/banc-mesure.sh" <<'FIN'
#!/bin/bash
# Banc de mesure appareil « niveau Android Studio » — gabarit 07, R5 : mesure réelle.
# Sortie : S2/S3 (froid + sonde import), F1/F3/F4/F5/F6 (latences moteur),
# C1 (contention), Q3 (inter-fichiers), M1/M2 (tas) — 3 passes espacées de 30 s.
# À reporter dans docs/etudes/niveau-android-studio/07-gabarit-mesure-appareil.md.
set -uo pipefail
cd "$(dirname "$0")"
echo "=============================================================="
echo " banc-mesure — gabarit 07 première passe ($(date '+%Y-%m-%d %H:%M:%S'))"
echo " hôte : $(uname -m) / $(getprop ro.build.version.release 2>/dev/null || echo 'non-Android') / $(getprop ro.product.model 2>/dev/null || echo '?')"
echo " java : $(java -version 2>&1 | head -1)"
echo "=============================================================="
ARGS=""
if [ -f android.jar ]; then
    ARGS="$(pwd)/android.jar"
    echo "android.jar détecté : OUI ($(du -h android.jar | cut -f1)) — plateforme jar"
else
    echo "android.jar détecté : non — plateforme jrt du runtime"
fi
PASSES="${PASSES:-3}"
echo "passes : $PASSES (gabarit §1 : 3 espacées de 30 s ; PASSES=1 pour une passe rapide)"
for ((P = 1; P <= PASSES; P++)); do
    echo ""
    echo "########## PASSE $P/$PASSES ##########"
    java -Xmx2g -cp "banc-classes:main-classes:libs/*" \
        jo.codeide.lsp.java.banc.BancMesureAppareilKt $ARGS
    if [ "$P" -lt "$PASSES" ]; then
        echo "(pause 30 s — thermique, gabarit 07 §1)"
        sleep 30
    fi
done
echo ""
echo "banc-mesure terminé — reporter les valeurs dans le gabarit 07 (R5 : aucune déduction)"
FIN
chmod +x "$KIT/banc-mesure.sh"

# Zip à PLAT (racine = banc-mesure.sh) : les commandes documentées ci-dessus
# doivent être exactes telles quelles sur l'appareil.
(cd "$KIT" && zip -qr ../kit-mesure.zip .)
echap "kit PRÊT : $(pwd)/build/kit-mesure.zip ($(du -h build/kit-mesure.zip | cut -f1))"
echap "appareil : unzip -o kit-mesure.zip -d mesure && cd mesure && bash banc-mesure.sh"
echap "optionnel : poser android.jar dans le dossier avant la passe (plateforme jar)"
