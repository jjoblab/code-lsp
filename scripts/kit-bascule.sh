#!/usr/bin/env bash
# ============================================================================
# kit-bascule.sh — assemble le KIT DE PASSE BASCULE SUR APPAREIL (étage T,
# §8.5 : « latences tenues sur appareil » — protocole du gabarit
# bancs/GABARIT-BASCULE-APPAREIL.md, mesures à retourner par le propriétaire,
# JAMAIS simulées — R5).
#
# Produit build/kit-bascule.zip (jamais committé — R6.4) :
#   banc-classes/   — le banc de passe bascule + le corpus (compilés)
#   main-classes/   — le moteur neuf (compilé)
#   libs/           — ecj patché (ecj-3.40.0-art.jar) + kotlin-stdlib + lsp4j
#   banc-bascule.sh — le point d'entrée appareil (Termux OpenJDK 17)
#
# Sur l'appareil (Termux) :
#   unzip -o kit-bascule.zip -d bascule && cd bascule
#   bash banc-bascule.sh > resultats-bascule.txt
# Puis remplir bancs/GABARIT-BASCULE-APPAREIL.md et le joindre à la décision.
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

# --- 2. Localiser kotlin-stdlib et lsp4j (le classpath d'exécution du banc) ---
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
KIT="build/kit-bascule"
rm -rf "$KIT" build/kit-bascule.zip
mkdir -p "$KIT/libs"
cp -r "$CLASSES_BANC" "$KIT/banc-classes"
cp -r "$CLASSES_MAIN" "$KIT/main-classes"
cp "$JAR_ECJ" "$KIT/libs/"
cp "$STDLIB" "$LSP4J" "$JSONRPC" "$KIT/libs/"

cat > "$KIT/banc-bascule.sh" <<'FIN'
#!/bin/bash
# Banc de passe bascule sur appareil — étage T (§8.5), R5 : mesure réelle.
# Sortie : couverture du cahier (18 positions) + latences p50/p95 par genre
# + verdict zéro-saut. À reporter dans bancs/GABARIT-BASCULE-APPAREIL.md.
set -uo pipefail
cd "$(dirname "$0")"
echo "=============================================================="
echo " banc-bascule — parité & latences du moteur neuf ($(date '+%Y-%m-%d %H:%M:%S'))"
echo " hôte : $(uname -m) / $(getprop ro.build.version.release 2>/dev/null || echo 'non-Android') / $(getprop ro.product.model 2>/dev/null || echo '?')"
echo " java : $(java -version 2>&1 | head -1)"
echo "=============================================================="
java -Xmx2g -cp "banc-classes:main-classes:libs/*" \
    jo.codeide.lsp.java.banc.BancBasculeAppareilKt
FIN
chmod +x "$KIT/banc-bascule.sh"

# Zip à PLAT (racine = banc-bascule.sh) : les commandes documentées ci-dessus
# et au gabarit §2 (« unzip -o kit-bascule.zip -d bascule && cd bascule »)
# doivent être exactes telles quelles sur l'appareil.
(cd "$KIT" && zip -qr ../kit-bascule.zip .)
echap "kit PRÊT : $(pwd)/build/kit-bascule.zip ($(du -h build/kit-bascule.zip | cut -f1))"
echap "appareil : unzip -o kit-bascule.zip -d bascule && cd bascule && bash banc-bascule.sh"
