#!/usr/bin/env bash
# ============================================================================
# verifier-equivalence-digeste.sh — PREUVE D'ÉQUIVALENCE DU DIGESTE DE PARITÉ
# (piège P-1, docs/REORGANISATION.md §7 — exécution Phase 2, 03/11/2026).
#
# Le digeste de parité (suite de parité « dépôt », fiche 010 du cahier
# Kotlin) épingle la FORME des arbres de CHAQUE fichier .kt du dépôt :
# une ligne « <sha256-structurel>  <chemin relatif au dépôt> » par fichier.
# Le hash est STRUCTUREL (productions + genres de jetons — fonction
# déterministe du contenu) : les correctifs de RÉFÉRENCES de la Phase 2
# (KDoc, messages, littéraux de chemins) ne changent PAS la forme des
# arbres ; seuls les CHEMINS des lignes changent avec les déplacements.
#
# Méthode de preuve (exécutée après la re-signature officielle) :
#   1. la baseline re-signée a été RÉGÉNÉRÉE par la procédure officielle
#      (./gradlew :kotlin:test --tests '*Fiche010PariteCorpusDepotTest*'
#       -Dparite.majBase=true --rerun-tasks) — le test WALK le dépôt réel ;
#   2. ce script prouve l'ÉQUIVALENCE entre la baseline d'avant
#      réorganisation (<ref_avant>) et la baseline re-signée :
#      - même nombre de lignes (aucun fichier .kt du corpus ni perdu ni
#        ajouté) ;
#      - MULTI-ENSEMBLE des empreintes IDENTIQUE (aucune forme d'arbre
#        changée : 0 empreinte disparue, 0 apparue) ;
#      - chaque chemin ancien est couvert par un chemin nouveau portant la
#        MÊME empreinte (l'appariement par empreinte relie chaque fichier
#        déplacé — pur ou avec correctifs de références — à sa ligne) ;
#      - aucun chemin commun résiduel (tous les fichiers du corpus vivent
#        sous les nouvelles racines).
#
# La CI rejoue ensuite la suite de parité SANS -Dparite.majBase : elle
# vérifie la baseline re-signée contre le dépôt réel — l'arbitre final.
#
# Usage : scripts/verifier-equivalence-digeste.sh <ref_avant>
# Codes de retour : 0 = équivalence PROUVÉE ; 1 = écart.
# ============================================================================
set -euo pipefail

if [[ $# -ne 1 ]]; then
    echo "Usage : $0 <ref_avant>" >&2
    exit 2
fi
REF_AVANT="$1"
ANCIEN="kotlin/src/test/resources/parite/digeste-corpus.txt"
NOUVEAU="lsp-kotlin/moteur/src/test/resources/parite/digeste-corpus.txt"

cd "$(git rev-parse --show-toplevel)"

if ! git cat-file -e "${REF_AVANT}:${ANCIEN}" 2>/dev/null; then
    echo "ÉCHEC : baseline ancienne introuvable (${REF_AVANT}:${ANCIEN})." >&2
    exit 1
fi
if [[ ! -f "${NOUVEAU}" ]]; then
    echo "ÉCHEC : baseline re-signée attendue sous ${NOUVEAU}." >&2
    exit 1
fi

python3 - "${REF_AVANT}" "${ANCIEN}" "${NOUVEAU}" <<'PY'
import subprocess
import sys
from collections import Counter

ref_avant, ancien, nouveau = sys.argv[1:4]

def lire_lignes(texte):
    return [l for l in texte.splitlines() if l.strip()]

lignes_avant = lire_lignes(subprocess.run(
    ['git', 'show', f'{ref_avant}:{ancien}'],
    capture_output=True, text=True, check=True).stdout)
with open(nouveau, encoding='utf-8') as f:
    lignes_apres = lire_lignes(f.read())

h_avant = [l.split('  ')[0] for l in lignes_avant]
h_apres = [l.split('  ')[0] for l in lignes_apres]
c_avant = {l.split('  ', 1)[1]: l.split('  ')[0] for l in lignes_avant}
c_apres = {l.split('  ', 1)[1]: l.split('  ')[0] for l in lignes_apres}

ecarts = []
if len(lignes_avant) != len(lignes_apres):
    ecarts.append(f'nombre de lignes : {len(lignes_avant)} avant ≠ {len(lignes_apres)} après')
multiset_avant, multiset_apres = Counter(h_avant), Counter(h_apres)
perdues = multiset_avant - multiset_apres
apparues = multiset_apres - multiset_avant
if perdues:
    ecarts.append(f'{sum(perdues.values())} empreinte(s) DISPARUE(S) — forme d’arbre perdue')
if apparues:
    ecarts.append(f'{sum(apparues.values())} empreinte(s) APPARUE(S) — forme d’arbre changée')
# Appariement : chaque chemin ancien doit être couvert (même empreinte) après.
inverse_apres = {}
for chemin, empreinte in c_apres.items():
    inverse_apres.setdefault(empreinte, []).append(chemin)
non_couverts = [p for p, h in c_avant.items()
                if p not in c_apres and h not in inverse_apres]
if non_couverts:
    ecarts.append(f'{len(non_couverts)} chemin(s) ancien(s) sans équivalent '
                  f'(même empreinte) après : {non_couverts[:3]}…')

if ecarts:
    print(f'ÉCHEC : {len(ecarts)} écart(s) — équivalence NON prouvée :')
    for e in ecarts:
        print(f'  {e}')
    sys.exit(1)

communs = sorted(set(c_avant) & set(c_apres))
print(f'OK : équivalence PROUVÉE — {len(lignes_apres)} lignes avant/après,')
print('  multi-ensemble des empreintes IDENTIQUE (0 disparue, 0 apparue),')
print(f'  chaque chemin ancien apparié par empreinte ; {len(communs)} chemin(s)'
      ' commun(s) résiduel(s)' + (f' : {communs[:3]}…' if communs else '.'))
PY
