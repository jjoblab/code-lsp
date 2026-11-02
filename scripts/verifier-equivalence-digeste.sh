#!/usr/bin/env bash
# ============================================================================
# verifier-equivalence-digeste.sh — PREUVE D'ÉQUIVALENCE DU DIGESTE DE PARITÉ
# (piège P-1, docs/REORGANISATION.md §7 — exécution Phase 2, 03/11/2026).
#
# Le digeste de parité (suite de parité « dépôt », fiche 010 du cahier
# Kotlin) épingle la FORME des arbres de CHAQUE fichier .kt du dépôt :
# une ligne « <sha256-structurel>  <chemin relatif au dépôt > » par fichier.
# Le hash est STRUCTUREL (fonction déterministe du contenu du fichier) : un
# déplacement PUR (git mv, contenu octet pour octet identique) conserve le
# hash — SEUL le chemin de la ligne change.
#
# Ce script PROUVE l'équivalence entre la baseline signée avant la
# réorganisation (<ref_avant>) et la baseline re-signée après :
#   1. AUCUN fichier .kt ajouté ni supprimé entre <ref_avant> et HEAD
#      (le corpus de la suite de parité a exactement les mêmes fichiers) ;
#   2. chaque ligne de l'ancienne baseline correspond à un déplacement PUR
#      (similarité 100 % : blob git identique) ou à un chemin inchangé ;
#   3. la baseline attendue après réorganisation (mêmes hashes, nouveaux
#      chemins, tri stable) est EXACTEMENT celle produite par la
#      re-signature officielle (-Dparite.majBase=true --rerun-tasks).
#
# Usages :
#   scripts/verifier-equivalence-digeste.sh <ref_avant>              # vérifie
#   scripts/verifier-equivalence-digeste.sh <ref_avant> --produire F # écrit F
#
# Codes de retour : 0 = équivalence PROUVÉE ; 1 = écart (ne pas re-signer) ;
# 2 = erreur d'usage.
# ============================================================================
set -euo pipefail

if [[ $# -lt 1 ]]; then
    echo "Usage : $0 <ref_avant> [--produire <fichier>]" >&2
    exit 2
fi
REF_AVANT="$1"
MODE="verifier"
SORTIE=""
if [[ "${2:-}" == "--produire" && -n "${3:-}" ]]; then
    MODE="produire"
    SORTIE="$3"
fi

ANCIEN="kotlin/src/test/resources/parite/digeste-corpus.txt"
NOUVEAU="lsp-kotlin/moteur/src/test/resources/parite/digeste-corpus.txt"

cd "$(git rev-parse --show-toplevel)"

# --- 1. Aucun .kt ajouté ni supprimé (corpus identique en nombre) -----------
if git diff --name-status -M100% "${REF_AVANT}" HEAD -- '*.kt' \
        | grep -Ev '^R100' | grep -q .; then
    echo "ÉCHEC : fichiers .kt ajoutés/supprimés/modifiés (hors déplacement" >&2
    echo "pur) entre ${REF_AVANT} et HEAD :" >&2
    git diff --name-status -M100% "${REF_AVANT}" HEAD -- '*.kt' \
        | grep -Ev '^R100' >&2
    exit 1
fi

# --- 2. Baseline ancienne lisible -------------------------------------------
if ! git cat-file -e "${REF_AVANT}:${ANCIEN}" 2>/dev/null; then
    echo "ÉCHEC : baseline ancienne introuvable (${REF_AVANT}:${ANCIEN})." >&2
    exit 1
fi
if [[ ! -f "${NOUVEAU}" ]]; then
    echo "ÉCHEC : baseline re-signée attendue sous ${NOUVEAU} (exécuter après" >&2
    echo "le déplacement du moteur Kotlin, commit code)." >&2
    exit 1
fi

# --- 3. Preuve : remap + comparaison (python3 pour le tri exact) ------------
# La carte des déplacements purs vient de git (R100 = blob identique) ; le
# blob de chaque fichier de l'ancienne baseline est REVÉRIFIÉ par empreeinte.
python3 - "${REF_AVANT}" "${ANCIEN}" "${NOUVEAU}" "${MODE}" "${SORTIE}" <<'PY'
import subprocess
import sys

ref_avant, ancien, nouveau, mode, sortie = sys.argv[1:6]

# Carte ancien → nouveau des déplacements purs (R100 : blob identique).
carte = {}
statut = subprocess.run(
    ['git', 'diff', '--name-status', '-M100%', ref_avant, 'HEAD'],
    capture_output=True, text=True, check=True).stdout
for ligne in statut.splitlines():
    champs = ligne.split('\t')
    if len(champs) == 3 and champs[0] == 'R100':
        carte[champs[1]] = champs[2]

def blob(ref, chemin):
    return subprocess.run(['git', 'rev-parse', f'{ref}:{chemin}'],
                          capture_output=True, text=True).stdout.strip()

ancienne_baseline = subprocess.run(
    ['git', 'show', f'{ref_avant}:{ancien}'],
    capture_output=True, text=True, check=True).stdout.splitlines()

lignes_attendues = []
ecarts = []
for ligne in ancienne_baseline:
    if not ligne.strip():
        continue
    empreinte, chemin = ligne.split('  ', 1)
    if chemin in carte:
        nouveau_chemin = carte[chemin]
        if blob(ref_avant, chemin) != blob('HEAD', nouveau_chemin):
            ecarts.append(f'{chemin} → {nouveau_chemin} : blob DIFFÉRENT')
    elif subprocess.run(['git', 'cat-file', '-e', f'HEAD:{chemin}'],
                         capture_output=True).returncode == 0:
        nouveau_chemin = chemin  # inchangé
        if blob(ref_avant, chemin) != blob('HEAD', chemin):
            ecarts.append(f'{chemin} : contenu MODIFIÉ')
    else:
        ecarts.append(f'{chemin} : introuvable après réorganisation')
    lignes_attendues.append(f'{empreinte}  {nouveau_chemin}')

# La baseline régénérée est TRIÉE (sorted() Kotlin = ordre lexicographique
# des lignes complètes « hash  chemin » — ASCII ici, identique au tri C).
attendu = '\n'.join(sorted(lignes_attendues)) + '\n'

if ecarts:
    print(f'ÉCHEC : {len(ecarts)} écart(s) — la re-signature est REFUSÉE :')
    for e in ecarts:
        print(f'  {e}')
    sys.exit(1)

if mode == 'produire':
    with open(sortie, 'w', encoding='utf-8') as f:
        f.write(attendu)
    print(f'OK : baseline attendue écrite ({len(lignes_attendues)} lignes) → {sortie}')
    print('  (mêmes empreintes structurelles, chemins réorganisés, tri stable)')
else:
    with open(nouveau, encoding='utf-8') as f:
        actuel = f.read()
    if actuel == attendu:
        print(f'OK : équivalence PROUVÉE — {len(lignes_attendues)} lignes, '
              'hashes identiques, chemins réorganisés, tri identique.')
        print('  La baseline re-signée est EXACTEMENT celle que produirait '
              '-Dparite.majBase=true.')
    else:
        print('ÉCHEC : la baseline re-signée diffère de la baseline attendue.')
        import difflib
        for d in list(difflib.unified_diff(
                attendu.splitlines(), actuel.splitlines(),
                'attendue', 'actuelle', lineterm=''))[:40]:
            print(f'  {d}')
        sys.exit(1)
PY
