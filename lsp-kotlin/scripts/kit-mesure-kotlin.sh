#!/usr/bin/env bash
# ============================================================================
# kit-mesure-kotlin.sh — assemble le KIT DE MESURE SUR APPAREIL du moteur
# Kotlin (gabarit 07 ENRICHI de la passe V-H — ADR 0124 option 2 : « passe
# gabarit 07 enrichi sur moto g06 » avant tout verdict).
#
# Produit build/kit-mesure-kotlin.zip (jamais committé — R6.4) :
#   banc-classes/   — le banc de mesure (BancMesureAppareil, compilé bench)
#   main-classes/   — le moteur :kotlin (compilé, grammaire vendorée incluse)
#   libs/           — syntax-api + kotlin-stdlib + kotlin-metadata-jvm +
#                     lsp4j (+ jsonrpc, gson) + kotlin-compiler-embeddable
#                     (le compilateur déporté de la section H)
#   banc-mesure.sh  — le point d'entrée appareil (Termux OpenJDK 17)
#
# Sur l'appareil (Termux, mode avion, aucune autre application) :
#   unzip -o kit-mesure-kotlin.zip -d mesure && cd mesure
#   bash banc-mesure.sh | tee resultats-mesure-passe1.txt
#   … 3 passes espacées de ≥ 30 s (gabarit 07 §2), la médiane retenue.
# Puis remplir docs/etudes/niveau-android-studio-kotlin/07-gabarit-mesure-appareil.md
# §4/§7 et le joindre à la décision V-H (ADR 0124).
# ============================================================================
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

echap() { printf '\033[1m== %s\033[0m\n' "$*"; }

# --- 1. Compiler le banc + le moteur (bytecode 17) -------------------------
echap "compilation :kotlin:compileBenchKotlin…"
./gradlew :kotlin:compileBenchKotlin --no-daemon -q

CLASSES_BANC="lsp-kotlin/moteur/build/classes/kotlin/bench"
CLASSES_MAIN="lsp-kotlin/moteur/build/classes/kotlin/main"
CLASSES_VENDOR="lsp-kotlin/moteur/build/classes/kotlin/vendor"

for chemin in "$CLASSES_BANC" "$CLASSES_MAIN" "$CLASSES_VENDOR"; do
    [ -e "$chemin" ] || { echo "ERREUR : $chemin absent (build incomplet)." >&2; exit 1; }
done

# --- 2. Localiser les jars du runtime (le classpath d'exécution du banc) ---
trouver() {
    find "$HOME"/.gradle/caches/modules-2/files-2.1/"$1" \
        -name "$2" ! -name '*sources*' 2>/dev/null | sort | tail -1
}

SYNTAX="$(trouver org.jetbrains/syntax-api-jvm 'syntax-api-jvm-*.jar')"
STDLIB="$(trouver org.jetbrains.kotlin/kotlin-stdlib 'kotlin-stdlib-*.jar')"
METADATA="$(trouver org.jetbrains.kotlin/kotlin-metadata-jvm 'kotlin-metadata-jvm-*.jar')"
LSP4J="$(trouver org.eclipse.lsp4j/org.eclipse.lsp4j 'org.eclipse.lsp4j-*.jar')"
JSONRPC="$(trouver org.eclipse.lsp4j/org.eclipse.lsp4j.jsonrpc 'org.eclipse.lsp4j.jsonrpc-*.jar')"
GSON="$(trouver com.google.code.gson/gson 'gson-*.jar')"
ANNOTATIONS="$(trouver org.jetbrains/annotations 'annotations-*.jar')"
# Le compilateur déporté de la section H (ADR 0124 : « l'oracle du banc EST
# le mode profond en germe ») — le banc le lance EN SOUS-PROCESSUS.
K2="$(trouver org.jetbrains.kotlin/kotlin-compiler-embeddable 'kotlin-compiler-embeddable-*.jar')"
# Le compilateur déporté exige les COROUTINES au classpath de lancement
# (NoClassDefFoundError kotlinx/coroutines/CoroutineScope sinon — vérifié
# sur le bac à sable : le sous-process échouait SILENCIEUSEMENT).
COROUTINES="$(trouver org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm 'kotlinx-coroutines-core-jvm-*.jar')"

for var in SYNTAX STDLIB METADATA LSP4J JSONRPC GSON ANNOTATIONS K2 COROUTINES; do
    [ -n "${!var}" ] || { echo "ERREUR : jar \$$var introuvable dans le cache Gradle." >&2; exit 1; }
done
echap "syntax-api: $(basename "$SYNTAX") · stdlib: $(basename "$STDLIB") · k2: $(basename "$K2")"

# --- 3. Assembler le kit ----------------------------------------------------
KIT="build/kit-mesure-kotlin"
rm -rf "$KIT" build/kit-mesure-kotlin.zip
mkdir -p "$KIT/libs"
cp -r "$CLASSES_BANC" "$KIT/banc-classes"
cp -r "$CLASSES_MAIN" "$KIT/main-classes"
cp -r "$CLASSES_VENDOR" "$KIT/vendor-classes"
cp "$SYNTAX" "$STDLIB" "$METADATA" "$LSP4J" "$JSONRPC" "$GSON" "$ANNOTATIONS" "$K2" "$COROUTINES" "$KIT/libs/"

cat > "$KIT/banc-mesure.sh" <<'FIN'
#!/bin/bash
# Banc de mesure sur appareil — gabarit 07 ENRICHI (V-H, ADR 0124).
# R5 : valeurs mesurées, jamais simulées. 3 passes espacées de ≥ 30 s,
# la médiane retenue (gabarit 07 §2). Chaque passe écrit SON transcript.
set -uo pipefail
cd "$(dirname "$0")"
echo "=============================================================="
echo " banc-mesure-kotlin — passe appareil ($(date '+%Y-%m-%d %H:%M:%S'))"
echo " hôte : $(uname -m) / $(getprop ro.build.version.release 2>/dev/null || echo 'non-Android') / $(getprop ro.product.model 2>/dev/null || echo '?')"
echo " java : $(java -version 2>&1 | head -1)"
echo "=============================================================="
java -Xmx2g -XX:+UseSerialGC \
    -cp "banc-classes:main-classes:vendor-classes:libs/*" \
    jo.codeide.lsp.kotlin.banc.BancMesureAppareilKt
FIN
chmod +x "$KIT/banc-mesure.sh"

# Le kit doit porter le CONSIGNES d'exécution (les mêmes consignes que le
# dépôt, repli honnête si l'archive voyage seule).
cp lsp-kotlin/docs/etudes/niveau-android-studio-kotlin/CONSIGNES-MESURE-APPAREIL.md "$KIT/" 2>/dev/null || true

# --- 4. Zip à PLAT (racine = banc-mesure.sh) -------------------------------
(cd "$KIT" && zip -qr ../kit-mesure-kotlin.zip .)
COMMIT="$(git rev-parse --short HEAD)"
echo "$COMMIT" > build/kit-mesure-kotlin.commit
echap "kit PRÊT : $(pwd)/build/kit-mesure-kotlin.zip ($(du -h build/kit-mesure-kotlin.zip | cut -f1)) — moteur $COMMIT"
echap "appareil : unzip -o kit-mesure-kotlin.zip -d mesure && cd mesure && bash banc-mesure.sh | tee resultats-mesure-passe1.txt"
