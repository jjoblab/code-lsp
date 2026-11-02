#!/usr/bin/env bash
# ============================================================================
# telecharger-jars.sh — télécharge les piles de mesure du spike Kotlin.
# Les jars vivent HORS du dépôt code-lsp (R6.4 : jamais committés).
# Usage : bash telecharger-jars.sh [repertoire-cible]
# ============================================================================
set -euo pipefail
CIBLE="${1:-/home/z/my-project/spike-jars}"
mkdir -p "$CIBLE"
UA="User-Agent: Mozilla/5.0"
ok=0; echec=0

telecharge () { # url dest
  if [ -s "$CIBLE/$2" ]; then echo "OK (déjà présent) : $2"; ok=$((ok+1)); return 0; fi
  code=$(curl -s -L -m 300 -H "$UA" -w "%{http_code}" -o "$CIBLE/$2.tmp" "$1" || echo "000")
  if [ "$code" = "200" ] && [ -s "$CIBLE/$2.tmp" ]; then
    mv "$CIBLE/$2.tmp" "$CIBLE/$2"; echo "OK : $2 ($(du -h "$CIBLE/$2" | cut -f1))"; ok=$((ok+1))
  else
    rm -f "$CIBLE/$2.tmp"; echo "ÉCHEC ($code) : $1"; echec=$((echec+1))
  fi
}

CENTRAL="https://repo1.maven.org/maven2"
JB="https://cache-redirector.jetbrains.com/intellij-dependencies"

# Pile S2 — parseur officiel seul (syntax-api + lang-syntax + ij-multiplatform-util)
telecharge "$JB/org/jetbrains/syntax-api-jvm/0.3.340/syntax-api-jvm-0.3.340.jar" syntax-api-jvm-0.3.340.jar
telecharge "$JB/org/jetbrains/lang-syntax-jvm/0.3.340/lang-syntax-jvm-0.3.340.jar" lang-syntax-jvm-0.3.340.jar
telecharge "$CENTRAL/org/jetbrains/annotations/26.0.2/annotations-26.0.2.jar" annotations-26.0.2.jar

# Pile S0 — oracle ancien moteur (GitHub Packages, jeton requis)
if [ -n "${GITHUB_TOKEN:-}" ]; then
  telecharge "https://maven.pkg.github.com/jjoblab/codeide-lsp/com/github/jjoblab/codeide-lsp/kotlin/1.22.1/kotlin-1.22.1.jar" lsp-kotlin-1.22.1.jar
else
  echo "INFO : GITHUB_TOKEN absent — oracle S0 (lsp-kotlin-1.22.1.jar) non re-téléchargé."
fi

# Pile S1 — frontend K2 (compilateur embarqué)
telecharge "$CENTRAL/org/jetbrains/kotlin/kotlin-compiler-embeddable/2.2.10/kotlin-compiler-embeddable-2.2.10.jar" kotlin-compiler-embeddable-2.2.10.jar

# Métadonnées Kotlin (K5 — décodage @Metadata)
telecharge "$CENTRAL/org/jetbrains/kotlin/kotlin-metadata-jvm/2.2.10/kotlin-metadata-jvm-2.2.10.jar" kotlin-metadata-jvm-2.2.10.jar

# Dépendances d'exécution du compilateur embarqué (S1) — stdlib et coroutines
telecharge "$CENTRAL/org/jetbrains/kotlin/kotlin-stdlib/2.2.10/kotlin-stdlib-2.2.10.jar" kotlin-stdlib-2.2.10.jar
telecharge "$CENTRAL/org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/1.8.0/kotlinx-coroutines-core-jvm-1.8.0.jar" kotlinx-coroutines-core-jvm-1.8.0.jar

echo "---"
echo "téléchargés : $ok ; échecs : $echec"
ls -la "$CIBLE"
