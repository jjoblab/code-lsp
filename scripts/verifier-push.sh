#!/usr/bin/env bash
# ============================================================================
# verifier-push.sh — VÉRIFICATION OBLIGATOIRE AVANT TOUT PUSH
# Décision propriétaire du 28/10/2026 (ADR 0002, consignée dans AGENTS.md) :
#   1. le dépôt distant doit être github.com/jjoblab/code-lsp ;
#   2. l'utilisateur GitHub authentifié par le jeton doit être « jjoblab » ;
#   3. l'auteur git configuré (user.name) doit être « jjoblab ».
# Un push effectué sans cette vérification est une faute de procédure.
#
# Usage :
#   GITHUB_TOKEN=<jeton> scripts/verifier-push.sh
#   scripts/verifier-push.sh <jeton>
#
# Codes de retour : 0 = push autorisé ; 1 = REFUS ; 2 = jeton absent/erreur.
# ============================================================================
set -euo pipefail

ATTENDU="jjoblab"
DEPOT_ATTENDU="github.com/jjoblab/code-lsp"
TOKEN="${GITHUB_TOKEN:-${1:-}}"

if [[ -z "$TOKEN" ]]; then
  echo "ERREUR : jeton GitHub absent (définissez GITHUB_TOKEN ou passez-le en argument)." >&2
  exit 2
fi

# --- 1. Distant -------------------------------------------------------------
REMOTE="$(git remote get-url --push origin 2>/dev/null || true)"
if [[ -z "$REMOTE" ]]; then
  echo "REFUS : aucun distant « origin » configuré." >&2
  exit 1
fi
if [[ "$REMOTE" != *"$DEPOT_ATTENDU"* ]]; then
  echo "REFUS : le distant origin (${REMOTE}) n'est pas ${DEPOT_ATTENDU}." >&2
  exit 1
fi

# --- 2. Utilisateur authentifié par le jeton --------------------------------
LOGIN="$(curl -fsS -m 30 \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Accept: application/vnd.github+json" \
  https://api.github.com/user \
  | python3 -c 'import json,sys; print(json.load(sys.stdin).get("login",""))' || true)"

if [[ "$LOGIN" != "$ATTENDU" ]]; then
  echo "REFUS : utilisateur authentifié = « ${LOGIN:-inconnu} » (attendu : ${ATTENDU}) — PUSH ANNULÉ." >&2
  echo "  Ne poussez pas avec ce jeton. Faites vérifier le jeton au propriétaire." >&2
  exit 1
fi

# --- 3. Auteur git ----------------------------------------------------------
GIT_USER="$(git config user.name || true)"
if [[ "$GIT_USER" != "$ATTENDU" ]]; then
  echo "REFUS : git user.name = « ${GIT_USER:-non configuré} » (attendu : ${ATTENDU})." >&2
  echo "  Corrigez : git config user.name jjoblab" >&2
  echo "             git config user.email 150948254+jjoblab@users.noreply.github.com" >&2
  exit 1
fi

echo "OK : push autorisé — distant ${DEPOT_ATTENDU} ; utilisateur authentifié « ${LOGIN} » ; auteur git « ${GIT_USER} »."
