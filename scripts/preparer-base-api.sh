#!/usr/bin/env bash
# ============================================================================
# preparer-base-api.sh — prépare la BASE API du lint Android (V-C2, ADR 0024)
# : le `api-versions.xml` du SDK (≈ 5,5 Mo — JAMAIS committé, R6.4),
# éventuellement gzippé, posé à l'emplacement attendu par la propriété
# système `jo.codeide.lsp.java.lint.baseapi`.
#
# Sources acceptées (la première trouvée gagne) :
#   1. $ANDROID_HOME/platforms/android-*/data/api-versions.xml (la plus
#      récente gagne — miroir du choix de la plateforme de compilation) ;
#   2. $BASE_API_SOURCE — chemin direct d'un api-versions.xml.
#
# Usage :
#   bash scripts/preparer-base-api.sh [destination]
# Variables : ANDROID_HOME, BASE_API_SOURCE.
# Sortie : le chemin de la base préparée (à passer au moteur via
#   -Djo.codeide.lsp.java.lint.baseapi=<chemin>).
# ============================================================================
set -euo pipefail

DESTINATION="${1:-.base-api/api-versions.xml}"
SOURCE=""

if [[ -n "${BASE_API_SOURCE:-}" && -f "${BASE_API_SOURCE}" ]]; then
  SOURCE="${BASE_API_SOURCE}"
elif [[ -n "${ANDROID_HOME:-}" && -d "${ANDROID_HOME}/platforms" ]]; then
  SOURCE="$(ls -1d "${ANDROID_HOME}"/platforms/android-*/data/api-versions.xml 2>/dev/null | sort -V | tail -1 || true)"
fi

if [[ -z "${SOURCE}" || ! -f "${SOURCE}" ]]; then
  echo "ERREUR : aucun api-versions.xml trouvé (ANDROID_HOME ou BASE_API_SOURCE)." >&2
  echo "  Sans base, les contrôles de niveau d'API restent muets (repli honnête, ADR 0024 R1)." >&2
  exit 1
fi

mkdir -p "$(dirname "${DESTINATION}")"
cp "${SOURCE}" "${DESTINATION}"
echo "Base API préparée : ${DESTINATION} (depuis ${SOURCE})"
echo "Empreinte : $(sha256sum "${DESTINATION}" | cut -d' ' -f1)"
echo "Activer : -Djo.codeide.lsp.java.lint.baseapi=${DESTINATION}"
