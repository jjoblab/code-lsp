#!/usr/bin/env bash
# ============================================================================
# assembler-kit.sh — assemble le kit d'exécution appareil (kit-kotlin.zip)
# du banc-appareil.sh (moteur Kotlin, étage 0 puis étage 1).
#
# Implémente MÉCANIQUEMENT la procédure documentée en en-tête de
# appareil/banc-appareil.sh :
#   ./gradlew :kotlin:jar :kotlin:compileBenchKotlin
#   kit-kotlin/{libs,banc} + scan-art.py  ->  kit-kotlin.zip
#
# Le kit doit être « octets identiques aux mesures de bac à sable » : chaque
# pile de dépendances est donc GARDÉE par sa somme sha1 PUBLIÉE (épinglage —
# miroir du discipline d'épinglage du dépôt : syntax-api 0.3.340 vient de
# cache-redirector/intellij-dependencies, annotations/kotlin-stdlib de Maven
# Central ; les coordonnées Maven sont immuables, le sha1 prouve l'identité).
# Une pile non conforme est REFUSÉE (échec sec).
#
# Sources acceptées pour chaque pile (dans l'ordre) :
#   1. le cache Gradle (${GRADLE_USER_HOME:-~/.gradle}/caches/modules-2) —
#      voie de l'en-tête du banc (résolution par le build) ;
#   2. le répertoire des jars du spike (JARS_SPIKE, défaut
#      /home/z/my-project/spike-jars — téléchargés des MÊMES dépôts par
#      scripts/telecharger-jars.sh).
#
# Usage :
#   bash spike-kotlin/scripts/assembler-kit.sh [repertoire-cible]
#     répertoire-cible (défaut ../kit-assemble) : reçoit kit-kotlin.zip —
#     HORS du dépôt (R6.4 : les jars ne sont jamais committés).
#   ASSEMBLER_SAUTER_BUILD=1 bash spike-kotlin/scripts/assembler-kit.sh ...
#     re-assemblage d'un arbre DÉJÀ bâti : saute ./gradlew et réutilise les
#     artefacts de kotlin/build (utile en bac à sable quand la résolution
#     réseau est indisponible — les artefacts existants restent gardés par
#     les asserts ci-dessous et par la passe du banc sur le kit assemblé).
# ============================================================================
set -euo pipefail
cd "$(dirname "$0")/../.."   # racine du dépôt (code-lsp)

CIBLE="${1:-../kit-assemble}"
JARS_SPIKE="${JARS_SPIKE:-/home/z/my-project/spike-jars}"
CACHE_GRADLE="${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2"

# --- Épinglage sha1 des piles (versions du build — ADR 0103/0121). -------
# Toute montée de version d'une pile = mettre à jour la constante ici (et
# seulement ici) ; le refus est automatique tant que ce n'est pas fait.
SHA1_SYNTAX_API="f7109308b8078569b06e5494d4dfbee88bec4a50"   # syntax-api-jvm-0.3.340.jar
SHA1_LANG_SYNTAX="e66421c8a0020f390260be1745b54fccdb279647"   # lang-syntax-jvm-0.3.340.jar
SHA1_ANNOTATIONS="c7ce3cdeda3d18909368dfe5977332dfad326c6d"   # annotations-26.0.2.jar
SHA1_STDLIB="30de6faa127a4a012db8e71bf1b9c0a99b1402b2"        # kotlin-stdlib-2.2.10.jar

echouer () { echo "ÉCHEC : $*" >&2; exit 1; }

# --- 1. Build du module (sauf re-assemblage explicite) --------------------
if [ "${ASSEMBLER_SAUTER_BUILD:-0}" = "1" ]; then
  echo "NOTE : build sauté (ASSEMBLER_SAUTER_BUILD=1) — artefacts existants réutilisés."
else
  ./gradlew :kotlin:jar :kotlin:compileBenchKotlin
fi

# --- 2. Artefacts du module (asserts — le jar moteur et les classes banc) --
MOTEUR="$(ls kotlin/build/libs/kotlin-*.jar 2>/dev/null | head -1 || true)"
[ -n "$MOTEUR" ] || echouer "jar moteur absent (kotlin/build/libs/kotlin-*.jar) — lancer le build."
CLASSE_BANC="kotlin/build/classes/kotlin/bench/jo/codeide/lsp/kotlin/banc/BancGrammaireKt.class"
[ -s "$CLASSE_BANC" ] || echouer "classes du banc absentes (:kotlin:compileBenchKotlin)."
[ -s "spike-kotlin/scripts/scan-art.py" ] || echouer "scan-art.py absent (spike-kotlin/scripts/)."

# --- 3. Assemblage ---------------------------------------------------------
STAGING="$(mktemp -d)"
trap 'rm -rf "$STAGING"' EXIT
mkdir -p "$STAGING/kit-kotlin/libs" "$STAGING/kit-kotlin/banc"

cp "$MOTEUR" "$STAGING/kit-kotlin/libs/$(basename "$MOTEUR")"
echo "OK : $(basename "$MOTEUR") ($(du -h "$MOTEUR" | cut -f1)) — jar moteur (mesures bac à sable)."

cp -r kotlin/build/classes/kotlin/bench/* "$STAGING/kit-kotlin/banc/"
echo "OK : banc/ — $(find "$STAGING/kit-kotlin/banc" -name '*.class' | wc -l) classe(s) du banc."

cp spike-kotlin/scripts/scan-art.py "$STAGING/kit-kotlin/scan-art.py"
echo "OK : scan-art.py (scan ART de l'appareil, si python3 y est présent)."

# pile <nom-de-fichier> <sha1-attendu> : cache Gradle puis jars du spike,
# TOUJOURS gardée par le sha1 publié.
prendre_pile () {
  local nom="$1" attendu="$2" source=""
  source="$(find "$CACHE_GRADLE" -name "$nom" -print 2>/dev/null | head -1 || true)"
  if [ -z "$source" ] && [ -s "$JARS_SPIKE/$nom" ]; then source="$JARS_SPIKE/$nom"; fi
  [ -n "$source" ] || echouer "pile $nom introuvable (ni cache Gradle, ni $JARS_SPIKE)."
  local lue; lue="$(sha1sum "$source" | awk '{print $1}')"
  [ "$lue" = "$attendu" ] || echouer "pile $nom : sha1 $lue ≠ publié $attendu — REFUS (octets non identiques)."
  cp "$source" "$STAGING/kit-kotlin/libs/$nom"
  echo "OK : $nom (sha1 publié vérifié — $(du -h "$source" | cut -f1))."
}

prendre_pile "syntax-api-jvm-0.3.340.jar"    "$SHA1_SYNTAX_API"
prendre_pile "lang-syntax-jvm-0.3.340.jar"   "$SHA1_LANG_SYNTAX"
prendre_pile "annotations-26.0.2.jar"        "$SHA1_ANNOTATIONS"
prendre_pile "kotlin-stdlib-2.2.10.jar"      "$SHA1_STDLIB"

# --- 3.bis. Garde de compatibilité JDK 17 AVANT expédition ------------------
# Incident appareil du 30/10 : java.util.List.removeLast (JDK 21 seul) dans
# le bytecode 61 du jar moteur — NoSuchMethodError sur l'openjdk 17 de
# Termux. Le kit doit être EXÉCUTABLE par le JDK de l'appareil : on refuse
# d'expédier tout artefact qui référence la famille SequencedCollection sur
# java.util.List (miroir du garde CompatibiliteJdk17Test du module et du
# motif MOTIFS_MEMBRES de scan-art.py en CI).
if command -v python3 >/dev/null 2>&1; then
  python3 spike-kotlin/scripts/scan-jdk21.py \
    "$STAGING/kit-kotlin/libs/$(basename "$MOTEUR")" "$STAGING/kit-kotlin/banc" \
    || echouer "kit non conforme JDK 17 (références JDK 21+ détectées) — expédition REFUSÉE."
else
  echo "AVERTISSEMENT : python3 absent — garde JDK 17 non exécutée (le garde CI scan-art.py reste actif)."
fi

# --- 4. Sommes de contrôle du kit (vérification du transfert appareil) -----
( cd "$STAGING/kit-kotlin" \
  && find . -type f ! -name SHA256SUMS.txt -print0 | sort -z \
  | xargs -0 sha256sum > SHA256SUMS.txt )
echo "OK : SHA256SUMS.txt — vérifiable sur l'appareil : sha256sum -c SHA256SUMS.txt"

# --- 5. Zip ----------------------------------------------------------------
CIBLE_ABS="$(mkdir -p "$CIBLE" && cd "$CIBLE" && pwd)"
rm -f "$CIBLE_ABS/kit-kotlin.zip"
( cd "$STAGING" && zip -qr "$CIBLE_ABS/kit-kotlin.zip" kit-kotlin )

echo "---"
echo "Kit assemblé : $CIBLE_ABS/kit-kotlin.zip ($(du -h "$CIBLE_ABS/kit-kotlin.zip" | cut -f1), sha256 $(sha256sum "$CIBLE_ABS/kit-kotlin.zip" | awk '{print $1}'))"
echo "Transfert appareil (racine du clone) :  unzip -o kit-kotlin.zip -d kit"
echo "Exécution (depuis spike-kotlin/) :       bash appareil/banc-appareil.sh ../kit/kit-kotlin"
