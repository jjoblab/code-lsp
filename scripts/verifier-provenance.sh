#!/usr/bin/env bash
# ============================================================================
# verifier-provenance.sh — CONTRÔLE CI « ANTI-FICHIER-HORS-REGISTRE » (R9.2)
# Exécuté par la CI à chaque push (ADR 0001 : exclusion DÉFINITIVE du moteur
# vendored de codeide-lsp — ce script en est le garant automatique).
#
# Échoue si un fichier suivi par git sous index/ ou model/ n'est NI :
#   - un fichier de squelette autorisé (build.gradle.kts du vague 0),
#   - NI inscrit au manifeste de provenance docs/PROVENANCE-REPRISE.md
#     (une reprise contrôlée R9.2 exige une inscription PRÉALABLE).
#
# En vague 0, les modules sont vides : le contrôle passe trivialement.
# Dès la vague 1, TOUT fichier de code repris doit figurer au manifeste.
#
# Périmètre volontairement limité aux modules ACCUEILLANT DES REPRISES.
# :ecj-art (vague 1c), :java et :testing (étage N1) n'y figurent pas :
# réécritures intégrales clean-room (voie ① R9.3 — manifeste §2/§6),
# AUCUN de leurs fichiers n'est repris d'un ancien dépôt, ils n'ont donc
# rien à inscrire au registre.
#
# --- EXTENSION KOTLIN (vague 0, ADR 0100 acceptée le 2026-10-30) -----------
# Le module :kotlin est MIXTE (voie C — hybride) : trois régimes distincts.
#
#  1. VENDORING (kotlin/src/main/vendor/**) : grammaire officielle RÉ-OBTENUE
#     de l'amont JetBrains (R9.2 : jamais depuis codeide-lsp). Contrôles :
#     notices obligatoires (VENDOR.md, LICENSE.txt, COPYRIGHT.txt — Apache-2.0
#     accompagne la redistribution) + inscription du chemin du vendoring au
#     manifeste Kotlin docs/PROVENANCE-REPRISE-KOTLIN.md. La DÉRIVE de
#     contenu du vendoring (re-sync amont) est gardée par la SUITE DE PARITÉ
#     (digeste structurel, fiches 010/012) — pas par ce script.
#
#  2. ZONES DE REPRISE (voie C, inscription PRÉALABLE fichier par fichier) :
#     tout fichier suivi sous kotlin/src/main/kotlin/** dont le paquet est
#     une zone de reprise (synthetics, preview, classpath, format, plis,
#     documentation, symboles — frontières ADR 0100) DOIT être inscrit au
#     manifeste Kotlin AVANT d'exister dans le dépôt (R9.2). Une réécriture
#     clean-room d'une de ces zones passerait par une décision ADR/manifeste
#     (changement de statut de la zone) — le script applique la frontière
#     telle que décidée.
#
#  3. CODE NEUF (le reste : harnais, suite de parité, banc, futur noyau
#     parse/résolution/diagnostics/complétion/session, serveur LSP) : libre
#     par construction — clean-room depuis le cahier (AGENTS.md, R9.1 : le
#     code de l'ancien moteur n'y est jamais consulté). Rien à inscrire.
# ============================================================================
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

MANIFESTE="docs/PROVENANCE-REPRISE.md"
MANIFESTE_KOTLIN="docs/PROVENANCE-REPRISE-KOTLIN.md"
SQUELETTE_AUTORISE="build.gradle.kts"
CODE=0

while IFS= read -r f; do
  [[ -z "$f" ]] && continue
  base="$(basename "$f")"
  if [[ "$base" == "$SQUELETTE_AUTORISE" ]]; then
    continue
  fi
  if grep -qF -- "$f" "$MANIFESTE" 2>/dev/null; then
    continue
  fi
  echo "ERREUR : fichier hors registre détecté : $f"
  echo "  → inscrivez-le au manifeste $MANIFESTE (R9.2 : inscription PRÉALABLE à toute copie),"
  echo "    ou retirez-le du dépôt."
  CODE=1
done < <(git ls-files -- 'index/*' 'model/*')

if [[ "$CODE" -eq 0 ]]; then
  echo "OK : aucun fichier hors registre sous index/, model/ (manifeste respecté)."
fi

# --- Extension Kotlin : 1) vendoring (notices + inscription) ---------------
VENDOR_KOTLIN="kotlin/src/main/vendor"
if git ls-files -- "${VENDOR_KOTLIN}/*" | grep -q .; then
  for notice in VENDOR.md LICENSE.txt COPYRIGHT.txt; do
    if ! git ls-files -- "${VENDOR_KOTLIN}/${notice}" | grep -q .; then
      echo "ERREUR : notice ${notice} absente du vendoring ${VENDOR_KOTLIN}/ (Apache-2.0 :"
      echo "  la licence accompagne le code vendoré — procédure vendor-grammaire-kotlin.sh)."
      CODE=1
    fi
  done
  # Chemin ANCRÉ (espace/backtick/pipe/début avant, pour ne pas confondre
  # kotlin/src/main/vendor avec le vendoring DU SPIKE spike-kotlin/src/…).
  if ! grep -qE "(^|[[:space:]\`|])kotlin/src/main/vendor" "${MANIFESTE_KOTLIN}" 2>/dev/null; then
    echo "ERREUR : le vendoring ${VENDOR_KOTLIN}/ n'est pas inscrit au manifeste ${MANIFESTE_KOTLIN} (§4)."
    CODE=1
  fi
  if [[ "$CODE" -eq 0 ]]; then
    echo "OK : vendoring ${VENDOR_KOTLIN}/ conforme (notices présentes, inscrit au manifeste Kotlin)."
  fi
fi

# --- Extension Kotlin : 2) zones de reprise (inscription PRÉALABLE) --------
ZONES_REPRISE='(synthetics|preview|classpath|format|plis|documentation|symboles)'
while IFS= read -r f; do
  [[ -z "$f" ]] && continue
  if [[ "$f" =~ kotlin/src/main/kotlin/.*/${ZONES_REPRISE}/ ]]; then
    if grep -qF -- "$f" "${MANIFESTE_KOTLIN}" 2>/dev/null; then
      continue
    fi
    echo "ERREUR : fichier de ZONE DE REPRISE hors registre : $f"
    echo "  → voie C (ADR 0100) : le paquet ${ZONES_REPRISE} est une zone de reprise —"
    echo "    inscription PRÉALABLE au manifeste ${MANIFESTE_KOTLIN} obligatoire (R9.2, R9.4)."
    CODE=1
  fi
done < <(git ls-files -- 'kotlin/src/main/kotlin/*')

if [[ "$CODE" -eq 0 ]]; then
  echo "OK : aucune reprise hors registre sous kotlin/src/main/kotlin/ (zones voie C respectées)."
fi

exit "$CODE"
