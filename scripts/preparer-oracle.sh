#!/usr/bin/env bash
# ============================================================================
# preparer-oracle.sh — construit l'ORACLE du banc de parité croisée (étage T,
# ADR 0016) : le moteur vendored de codeide-lsp, au COMMIT ÉPINGLÉ, exécuté
# en boîte noire par :java:benchParite.
#
# Ce que produit ce script (tout dans .oracle/, JAMAIS committé — R6.4) :
#   .oracle/java-1.16.0.jar   — le moteur vendored (ecj patché graphé dedans)
#   .oracle/lsp4j-0.21.2.jar  — dépendance du vendored (Maven Central)
#   .oracle/gson-2.10.1.jar   — dépendance du vendored (Maven Central)
#   .oracle/travail/          — checkouts épinglés (codeide-lsp + tooling)
#   .oracle/ORACLE.sha256     — empreintes des trois jars (traçabilité locale)
#
# Épinglage (docs/PROVENANCE-REPRISE.md — anciens dépôts lecture seule) :
#   codeide-lsp    @ cb809e1412fec429c9672da087244b3bd3176545 (v1.16.0)
#   codeide-tooling @ b60f7f0 (build composite du dépôt frère)
# Toute montée de version de l'oracle = PR dédiée de re-pin + re-signature
# de la baseline du banc croisé (bancs/baselines/parite-completion.json).
#
# Usage :
#   bash scripts/preparer-oracle.sh
# Variables d'environnement :
#   CODEIDE_LSP_DIR   — checkout codeide-lsp EXISTANT (le commit épinglé est
#                       VÉRIFIÉ ; refus sinon) — évite le clonage réseau
#   ORACLE_DIR        — répertoire cible (défaut : .oracle à la racine)
#   ORACLE_JDK_HOME   — JDK 21 AVEC javac (défaut : JAVA_HOME, puis les JDK
#                       provisionnés par Gradle dans ~/.gradle/jdks)
# ============================================================================
set -euo pipefail

COMMIT_ORACLE="cb809e1412fec429c9672da087244b3bd3176545"
COMMIT_TOOLING="b60f7f0dc6c2ce75edd7febc44ff80e0db39f2b1"
LSP4J_VERSION="0.21.2"
GSON_VERSION="2.10.1"

cd "$(git rev-parse --show-toplevel)"
ORACLE_DIR="${ORACLE_DIR:-.oracle}"
TRAVAIL="$ORACLE_DIR/travail"
mkdir -p "$ORACLE_DIR" "$TRAVAIL"

echap() { printf '\033[1m== %s\033[0m\n' "$*"; }

# --- 1. Le JDK (javac OBLIGATOIRE : le vendored compile du Java) ----------
trouver_jdk() {
    for candidat in \
        "${ORACLE_JDK_HOME:-}" \
        "${JAVA_HOME:-}" \
        "$HOME"/.gradle/jdks/eclipse_adoptium-21-amd64-linux.* \
        /usr/lib/jvm/java-21-openjdk-amd64 ; do
        [ -n "$candidat" ] || continue
        if [ -x "$candidat/bin/javac" ]; then
            printf '%s' "$candidat"
            return 0
        fi
    done
    return 1
}
JDK="$(trouver_jdk)" || {
    echo "ERREUR : aucun JDK 21 avec javac (ORACLE_JDK_HOME, JAVA_HOME ou ~/.gradle/jdks)." >&2
    exit 1
}
echap "JDK : $JDK"

# --- 2. Les checkouts épinglés (clone si absent, VÉRIFICATION du pin) -----
obtenir_checkout() {
    local nom="$1" commit="$2" url="$3"
    local dossier="$TRAVAIL/$nom"
    if [ -n "${CODEIDE_LSP_DIR:-}" ] && [ "$nom" = "codeide-lsp" ]; then
        dossier="$CODEIDE_LSP_DIR"
    fi
    if [ ! -d "$dossier/.git" ]; then
        echap "clonage $nom ($url)…"
        git clone "$url" "$dossier"
    fi
    local actuel
    actuel="$(git -C "$dossier" rev-parse HEAD)"
    if [ "$actuel" != "$commit" ]; then
        # Tolérance de préfixe (pin court accepté s'il préfixe le SHA réel).
        case "$actuel" in
            "$commit"*) ;;
            *)
                echo "ERREUR : $nom est à $actuel, l'oracle est épinglé à $commit." >&2
                echo "  (monter l'oracle de version = PR dédiée : re-pin + re-signature baseline)" >&2
                exit 1
                ;;
        esac
    fi
    printf '%s' "$dossier"
}

LSP_DIR="$(obtenir_checkout codeide-lsp "$COMMIT_ORACLE" https://github.com/jjoblab/codeide-lsp.git)"
echap "codeide-lsp épinglé à $COMMIT_ORACLE"

# Le build composite du vendored résout son dépôt frère en ../codeide-tooling
# (settings.gradle.kts). Ordre de résolution : le FRÈRE RÉEL du checkout
# vendored (s'il est au bon commit — cas d'un checkout existant), sinon un
# clone épinglé dans .oracle/travail relié par lien symbolique.
FRERE="$(dirname "$LSP_DIR")/codeide-tooling"
if [ -d "$FRERE/.git" ] && [ "$(git -C "$FRERE" rev-parse HEAD)" = "$COMMIT_TOOLING" ]; then
    TOOLING_DIR="$FRERE"
else
    TOOLING_DIR="$(obtenir_checkout codeide-tooling "$COMMIT_TOOLING" https://github.com/jjoblab/codeide-tooling.git)"
    if [ ! -e "$FRERE" ]; then
        echap "lien symbolique du frère tooling vers $FRERE…"
        ln -sfn "$(cd "$TOOLING_DIR" && pwd)" "$FRERE"
    fi
fi

# --- 3. Le jar du moteur vendored (java-library JVM pur, aucun SDK Android)
JAR_CIBLE="$ORACLE_DIR/java-1.16.0.jar"
if [ ! -f "$JAR_CIBLE" ] || [ "${ORACLE_FORCER:-0}" = "1" ]; then
    echap "construction :lsp:java:jar (Gradle du vendored, JDK $JDK)…"
    (cd "$LSP_DIR" && JAVA_HOME="$JDK" ./gradlew :lsp:java:jar --no-daemon -q)
    JAR_PRODUIT="$(ls "$LSP_DIR"/lsp/java/build/libs/java-*.jar | head -1)"
    [ -n "$JAR_PRODUIT" ] || { echo "ERREUR : jar vendored introuvable après build." >&2; exit 1; }
    cp "$JAR_PRODUIT" "$JAR_CIBLE"
fi
echap "oracle : $JAR_CIBLE ($(du -h "$JAR_CIBLE" | cut -f1))"

# --- 4. Les dépendances du vendored (cache Gradle d'abord, Maven Central) -
prendre_dep() {
    local groupe="$1" artefact="$2" version="$3" fichier="$4" cible="$5"
    if [ -f "$cible" ]; then return 0; fi
    local trouve
    trouve="$(find "$HOME"/.gradle/caches/modules-2/files-2.1/"$groupe"/"$artefact"/"$version" \
        -name "$fichier" 2>/dev/null | head -1 || true)"
    if [ -z "$trouve" ]; then
        echap "téléchargement $fichier (Maven Central)…"
        local url="https://repo1.maven.org/maven2/$(echo "$groupe" | tr . /)/$artefact/$version/$fichier"
        curl -sSfL -o "$cible" "$url"
    else
        cp "$trouve" "$cible"
    fi
    [ "$(stat -c%s "$cible")" -gt 100000 ] || {
        echo "ERREUR : $cible anormalement petit — téléchargement corrompu ?" >&2
        exit 1
    }
}
prendre_dep org.eclipse.lsp4j org.eclipse.lsp4j "$LSP4J_VERSION" \
    "org.eclipse.lsp4j-$LSP4J_VERSION.jar" "$ORACLE_DIR/lsp4j-$LSP4J_VERSION.jar"
prendre_dep com.google.code.gson gson "$GSON_VERSION" \
    "gson-$GSON_VERSION.jar" "$ORACLE_DIR/gson-$GSON_VERSION.jar"

# --- 5. Empreintes locales (traçabilité ; le pin de VERSION reste le commit)
sha256sum "$JAR_CIBLE" "$ORACLE_DIR/lsp4j-$LSP4J_VERSION.jar" "$ORACLE_DIR/gson-$GSON_VERSION.jar" \
    > "$ORACLE_DIR/ORACLE.sha256"

echap "oracle PRÊT — lancer le banc : ./gradlew :java:benchParite"
echap "contenu de $ORACLE_DIR (jamais committé, R6.4) : $(ls "$ORACLE_DIR" | tr '\n' ' ')"
