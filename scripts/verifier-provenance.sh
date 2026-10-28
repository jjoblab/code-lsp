#!/usr/bin/env bash
# ============================================================================
# verifier-provenance.sh — CONTRÔLE CI « ANTI-FICHIER-HORS-REGISTRE » (R9.2)
# Exécuté par la CI à chaque push (ADR 0001 : exclusion DÉFINITIVE du moteur
# vendored de codeide-lsp — ce script en est le garant automatique).
#
# Échoue si un fichier suivi par git sous java/, index/, model/ ou testing/
# n'est NI :
#   - un fichier de squelette autorisé (build.gradle.kts du vague 0),
#   - NI inscrit au manifeste de provenance docs/PROVENANCE-REPRISE.md
#     (une reprise contrôlée R9.2 exige une inscription PRÉALABLE).
#
# En vague 0, les modules sont vides : le contrôle passe trivialement.
# Dès la vague 1, TOUT fichier de code repris doit figurer au manifeste.
#
# Périmètre volontairement limité aux modules ACCUEILLANT DES REPRISES.
# :ecj-art (vague 1c) n'y figure pas : c'est une réécriture intégrale
# clean-room (voie ① R9.3 — manifeste §2/§6), AUCUN de ses fichiers n'est
# repris d'un ancien dépôt, il n'a donc rien à inscrire au registre.
# ============================================================================
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

MANIFESTE="docs/PROVENANCE-REPRISE.md"
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
done < <(git ls-files -- 'java/*' 'index/*' 'model/*' 'testing/*')

if [[ "$CODE" -eq 0 ]]; then
  echo "OK : aucun fichier hors registre sous java/, index/, model/, testing/ (manifeste respecté)."
fi
exit "$CODE"
