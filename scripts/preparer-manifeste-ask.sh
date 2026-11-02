#!/usr/bin/env bash
# ============================================================================
# preparer-manifeste-ask.sh — assemble le manifeste de classpath du corpus
# AnySoftKeyboard (V-A2, ADR 0030 — banc P1–P8 sur corpus réel).
#
# ENTRÉES (toutes locales, jamais committées) :
#   1. lsp-java/bancs/corpus-android/anysoftkeyboard — clone épinglé
#      (scripts/telecharger-corpus-android.sh anysoftkeyboard) APRÈS :
#        ./gradlew exportClasspathAsk --init-script scripts/ask-export.init.gradle \
#                  -PsortieAsk=<export>          (artefacts externes + racines)
#        ./gradlew generateDebugSources generateDebugRFile :ime:app:processDebugResources
#                                          (R.jar + sources générées)
#   2. <export> — répertoire de sortie de l'export (défaut /tmp/ask-export)
#   3. android.jar — première plateforme trouvée sous $ANDROID_SDK (défaut
#      /home/z/my-project/android-sdk).
#
# SORTIE : lsp-java/bancs/corpus-android/prepares/anysoftkeyboard.manifeste
#          + prepares/ask-libs/<artefact>-classes.jar (AAR réduits)
#
# Format (consommé par jo.codeide.lsp.java.coherence.* via CORPUS_ANDROID) :
#   racine:<chemin absolu d'une racine de sources>
#   classpath:<chemin absolu d'un jar>
# Les dépendances PROJET sont couvertes par les racines (sources fraternelles),
# PAS par des jars — l'export les a substituées par un module void (R5).
# ============================================================================
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
ASK="$REPO/lsp-java/bancs/corpus-android/anysoftkeyboard"
EXPORT="${1:-/tmp/ask-export}"
SDK="${ANDROID_SDK:-/home/z/my-project/android-sdk}"
PREPARES="$REPO/lsp-java/bancs/corpus-android/prepares"
LIBS="$PREPARES/ask-libs"

[ -d "$ASK" ] || { echo "ERREUR : clone ASK absent ($ASK)"; exit 1; }
[ -s "$EXPORT/artefacts.txt" ] || { echo "ERREUR : $EXPORT/artefacts.txt absent — lancer ask-export.init.gradle d'abord"; exit 1; }
[ -s "$EXPORT/racines.txt" ] || { echo "ERREUR : $EXPORT/racines.txt absent"; exit 1; }

ANDROID_JAR=$(ls "$SDK"/platforms/android-*/android.jar 2>/dev/null | sort -V | tail -1)
[ -s "$ANDROID_JAR" ] || { echo "ERREUR : aucun android.jar sous $SDK/platforms"; exit 1; }

mkdir -p "$LIBS"
MANIFESTE="$PREPARES/anysoftkeyboard.manifeste"

# ---------------------------------------------------------------------------
# 1. Classpath — android.jar, R.jar debug de chaque module, artefacts externes
#    (jars directs, AAR réduits à leur classes.jar).
# ---------------------------------------------------------------------------
{
  echo "# Manifeste corpus AnySoftKeyboard — généré par scripts/preparer-manifeste-ask.sh"
  echo "# clone épinglé : $(git -C "$ASK" rev-parse HEAD 2>/dev/null || echo '?')"
  echo "# android.jar : $ANDROID_JAR"
  echo "# export : $EXPORT ($(wc -l < "$EXPORT/artefacts.txt") artefacts externes)"
  echo
  echo "classpath:$ANDROID_JAR"
} > "$MANIFESTE"

nb_rjar=0
declare -A vus_rjar
while IFS= read -r rjar; do
  cle=$(echo "$rjar" | sed "s|$ASK/||; s|/build/.*||")
  [ -n "${vus_rjar[$cle]:-}" ] && continue
  vus_rjar[$cle]=1
  echo "classpath:$rjar" >> "$MANIFESTE"
  nb_rjar=$((nb_rjar + 1))
done < <(find "$ASK" -path "*/compile_r_class_jar/debug/*/R.jar" -o -path "*/compile_and_runtime_r_class_jar/debug/*/R.jar" | sort)

nb_aar=0
nb_jar=0
while IFS= read -r art; do
  case "$art" in
    *.aar)
      dest="$LIBS/$(basename "$art" .aar)-classes.jar"
      if unzip -p "$art" classes.jar > "$dest" 2>/dev/null && [ -s "$dest" ]; then
        echo "classpath:$dest" >> "$MANIFESTE"
        nb_aar=$((nb_aar + 1))
      else
        rm -f "$dest"
        echo "— AAR sans classes.jar (ressources seules) : $(basename "$art")" >&2
      fi
      ;;
    *.jar)
      echo "classpath:$art" >> "$MANIFESTE"
      nb_jar=$((nb_jar + 1))
      ;;
    *)
      echo "— artefact ignoré (ni jar ni aar) : $art" >&2
      ;;
  esac
done < "$EXPORT/artefacts.txt"

# ---------------------------------------------------------------------------
# 2. Racines — sources principales (cœur IME d'abord pour la fenêtre de 150
#    fichiers de la gate P3), puis sources générées du module correspondant.
# ---------------------------------------------------------------------------
priorite() {
  case "$1" in
    ":ime:app") echo 90 ;;
    ":ime:base-test"|":junit-sharding") echo 80 ;;
    :ime:*) echo 10 ;;
    ":api"|":addons:base") echo 20 ;;
    :addons:*) echo 30 ;;
    *) echo 40 ;;
  esac
}
echo >> "$MANIFESTE"
nb_racines=0
while IFS=$'\t' read -r mod chemin; do
  p=$(priorite "$mod")
  echo -e "$p\t$mod\t$chemin"
done < "$EXPORT/racines.txt" | sort -t$'\t' -k1,1n -k2,2 | cut -f2,3 | while IFS=$'\t' read -r mod chemin; do
  echo "racine:$chemin" >> "$MANIFESTE"
  # Sources générées du module (safe-args, buildConfig…) — modules non-Android
  # (junit-sharding) sans build/ : find gardé, échec neutralisé (set -e).
  # NB : la RACINE SOURCE est le répertoire CONTENANT l'arbre de paquets —
  # on remonte d'autant de segments que la déclaration `package` du fichier
  # en exige (le parent direct du .java est le répertoire PAQUET, pas la
  # racine : un BuildConfig y serait vu en package par défaut).
  genroot=$(realpath -m "$(dirname "$chemin")/../../build/generated")
  if [ -d "$genroot" ]; then
    find "$genroot" -name "*.java" 2>/dev/null | while IFS= read -r f; do
      pkg=$(sed -n 's/^package[[:space:]]\([A-Za-z_][A-Za-z0-9_.]*\);.*/\1/p' "$f" | head -1)
      d=$(dirname "$f")
      if [ -n "$pkg" ]; then
        n=$(printf '%s\n' "$pkg" | tr '.' '\n' | wc -l)
        for _ in $(seq 1 "$n"); do d=$(dirname "$d"); done
      fi
      printf '%s\n' "$d"
    done | sort -u | while IFS= read -r gen; do
      echo "racine:$gen" >> "$MANIFESTE"
    done
  fi
done || true
nb_racines=$(grep -c "^racine:" "$MANIFESTE")

echo "OK — $MANIFESTE"
echo "  racines : $nb_racines   | classpath : android.jar + $nb_rjar R.jar + $nb_jar jars + $nb_aar AAR réduits"
