#!/usr/bin/env bash
# ============================================================================
# telecharger-corpus-android.sh — télécharge le corpus réel du banc
# d'incohérences P1–P8 (mission « niveau Android Studio », vague 0 — ADR 0030).
#
# Le corpus est constitué de 5 projets Android open source ÉCRITS EN JAVA,
# sous licence permissive (Apache-2.0), épinglés à des commits exacts
# (vérifiés par `git ls-remote` le 31/10/2026 — jamais « de mémoire »).
# Les projets sont des DONNÉES DE CORPUS (cibles de test), pas des références
# de conception : leur inscription au registre de provenance se fait avec
# leur rôle (docs/PROVENANCE-REPRISE.md §Corpus — liste blanche §1.3 d'
# AGENTS.md non concernée).
#
# Règles du dépôt respectées : AUCUN binaire committé (le répertoire cible
# est dans .gitignore) ; téléchargement idempotent ; empreinte vérifiée par
# commit épinglé (clone puis `git checkout <sha>`).
#
# Usage :
#   scripts/telecharger-corpus-android.sh            # tout le corpus
#   scripts/telecharger-corpus-android.sh opentracks # un seul projet
#
# Sortie : bancs/corpus-android/<projet>/ (sources seules, profondeur 1).
# ============================================================================
set -euo pipefail

CIBLE="bancs/corpus-android"

# <clé> <organisation/dépôt> <commit épinglé> [chemin du module utile]
declare -A PROJETS=(
  [opentracks]="OpenTracksApp/OpenTracks 3c23a9f5400c7b5b914a221469d76cc019a18296"
  [markor]="gsantner/markor 7b15e66932f170c0b3f105c522111b0f32d6c73d"
  [gh4a]="slapperwan/gh4a 39f03ffe122125e46a51de16d90881047ed4660e"
  [anysoftkeyboard]="AnySoftKeyboard/AnySoftKeyboard 8cf81a3a2f73e9914c8613d56390a933486f0c6b"
  [basicsample]="android/architecture-components-samples e849ce3004ccd1132a121cf513bbcb7996d95c30 BasicSample"
)

demandes=("$@")
if [[ ${#demandes[@]} -eq 0 ]]; then
  demandes=("${!PROJETS[@]}")
fi

mkdir -p "$CIBLE"
for cle in "${demandes[@]}"; do
  ligne="${PROJETS[$cle]:-}"
  if [[ -z "$ligne" ]]; then
    echo "ERREUR : projet inconnu « $cle » (connus : ${!PROJETS[*]})" >&2
    exit 1
  fi
  read -r depot commit module <<< "$ligne"
  destination="$CIBLE/$cle"
  if [[ -d "$destination/.git" ]]; then
    echo "— $cle : déjà présent, synchronisation au commit épinglé $commit"
    git -C "$destination" fetch --all --quiet
  else
    echo "— $cle : clonage de github.com/$depot (commit épinglé $commit)"
    git clone --quiet "https://github.com/$depot.git" "$destination"
  fi
  git -C "$destination" checkout --quiet --force "$commit"
  echo "  $cle @ $commit prêt (${destination})"
  if [[ -n "${module:-}" ]]; then
    echo "  (module utile du corpus : $module)"
  fi
done

cat <<'FIN'

Corpus prêt. Prochaine étape (préparation du classpath réel, voir
bancs/corpus-android/LISEZMOI.md) : produire un manifeste par projet sous
bancs/corpus-android/prepares/<projet>.manifeste (lignes « racine:<chemin> »
et « classpath:<chemin> »), puis :

  CORPUS_ANDROID=bancs/corpus-android ./gradlew :java:test --tests 'jo.codeide.lsp.java.coherence.*'

Les propriétés P3/P4/P7 sur corpus réel ne se jouent qu'en présence des
manifestes ; sans corpus, les gates s'ignorent PROPREMENT (raison affichée).
FIN
