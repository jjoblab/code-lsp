#!/usr/bin/env bash
# ============================================================================
# telecharger-corpus-kotlin.sh — télécharge le corpus réel Android KOTLIN du
# banc de cohérence P1–P9 (mission « niveau Android Studio Kotlin », vague 0 —
# banc de mesure du moteur Kotlin, miroir du corpus Java
# scripts/telecharger-corpus-android.sh du domaine :java).
#
# Le corpus est constitué de 6 dépôts Android open source à dominante KOTLIN,
# sous licence permissive (Apache-2.0), épinglés à des commits exacts
# (vérifiés par `git ls-remote` le 31/10/2026 — jamais « de mémoire »).
# Les dépôts sont des DONNÉES DE CORPUS (cibles de mesure), pas des références
# de conception : leur inscription au registre de provenance se fait avec leur
# rôle (liste blanche du corpus — seuls ces 6 dépôts sont nommés dans le code
# et la documentation du banc).
#
# Rôles (volumétrie .kt mesurée le 31/10/2026 au commit épinglé) :
#   nowinandroid  — Compose Material 3, Hilt/Room par KSP, Retrofit +
#                   kotlinx-serialization, WorkManager, coroutines/Flow ;
#                   40 fichiers .kts + catalogue libs.versions.toml ;
#                   310 .kt / 29 336 lignes.
#   sunflower     — Views + ViewBinding + Compose + Room ; 66 .kt / 4 759 l.
#   architecture  — module todoapp (chemin utile : todoapp/) ; Views +
#                   ViewBinding MVVM ; 55 .kt / 6 049 l.
#   compose       — modules utiles Jetcaster (Flow/coroutines) et Jetsnack
#                   (chemins : Jetcaster/ et Jetsnack/).
#   viewswidgets  — projet MIXTE Java (94 fichiers / 13 251 l.) + Kotlin
#                   (104 / 7 739 l.) — le seul rôle interop réel du corpus.
#   platform      — Kotlin moderne, APIs plateforme ; 336 .kt / 47 131 l.
#
# ÉLIMINÉS (raisons consignées au LISEZMOI du corpus) : camera-samples
# (0 fichier Java mesuré + licence non détectée par l'API au 31/10), gh4a
# (0 fichier .kt au commit épinglé), thunderbird-android (mixte réel mais
# MPL-2.0 — hors politique « licence permissive » du corpus ; candidat secours
# SI le propriétaire arbitre MPL-2.0 acceptable).
#
# Règles du dépôt respectées : AUCUN binaire ni source de tiers committé (le
# répertoire cible est dans .gitignore) ; téléchargement idempotent ;
# empreinte vérifiée par commit épinglé (clone puis `git checkout <sha>`).
#
# Usage :
#   scripts/telecharger-corpus-kotlin.sh              # tout le corpus
#   scripts/telecharger-corpus-kotlin.sh sunflower    # un seul projet
#
# Sortie : bancs/corpus-android-kotlin/<projet>/ (sources seules, profondeur 1).
# ============================================================================
set -euo pipefail

CIBLE="bancs/corpus-android-kotlin"

# <clé> <organisation/dépôt> <commit épinglé> [chemin du module utile]
declare -A PROJETS=(
  [nowinandroid]="android/nowinandroid 2982e63cb691ad49cd009aab43dc206fee6699c2"
  [sunflower]="android/sunflower 2a357a31551bb53f3fe80382a9ce6d30bcc8b960"
  [architecture]="android/architecture-samples ee66e1526b84c026615df032c705842b7d2a521f todoapp"
  [compose]="android/compose-samples 25631e8f544a9a1e3f01e296eaf1b4c28428196c Jetcaster"
  [viewswidgets]="android/views-widgets-samples 2238cc873501f9cda63605051de11832bb736a8a"
  [platform]="android/platform-samples 177c3b92c387894648d0a2d42d238e265b05bbc3"
)

demandes=("${@:-}")
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
bancs/corpus-android-kotlin/LISEZMOI.md) : produire un manifeste par projet
sous bancs/corpus-android-kotlin/prepares/<projet>.manifeste (lignes
« racine:<chemin> » et « classpath:<chemin> »), puis :

  CORPUS_KOTLIN=bancs/corpus-android-kotlin ./gradlew :kotlin:test \
    --tests 'jo.codeide.lsp.kotlin.coherence.*' --max-workers=1

Les propriétés P1(b)/P2 sur corpus réel ne se jouent qu'en présence des
manifestes ; sans corpus, les gates s'ignorent PROPREMENT (raison affichée).
FIN
