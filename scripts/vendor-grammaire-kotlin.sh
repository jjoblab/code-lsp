#!/usr/bin/env bash
# ============================================================================
# vendor-grammaire-kotlin.sh — RE-VENDORING de la grammaire Kotlin (ADR 0121).
#
# La grammaire officielle du module :kotlin vit dans le module interne du
# compilateur (« not published on Maven » — constat d'étude) : elle s'obtient
# par `git archive` DEPUIS L'AMONT JetBrains, JAMAIS depuis l'ancien dépôt
# codeide-lsp (R9.2 du prompt de mission).
#
# Un re-sync est un REMPLACEMENT INTÉGRAL, jamais une fusion (miroir de
# l'ADR 0115 ancienne, consigné ADR 0104/0121 du présent dépôt) :
#   1. le tag est épinglé par la règle « compilé par un bootstrap ≤ notre
#      chaîne d'outils » (v2.3.0 est compilé par le bootstrap 2.2.x ; notre
#      chaîne est Kotlin Gradle plugin 2.2.10 — vérifié au spike §5.1) ;
#   2. `license/README.md` du tag ne doit lister AUCUNE exception de licence
#      sous `compiler/multiplatform-parsing` (sinon : ÉCHEC — décision
#      propriétaire requise) ;
#   3. le remplacement est octet pour octet (git archive), notices
#      LICENSE.txt/COPYRIGHT.txt copiées au même tag, VENDOR.md régénéré ;
#   4. GATES après remplacement (à exécuter et à joindre à la revue) :
#        ./gradlew :kotlin:test                      # suite de parité
#        ./gradlew :kotlin:test --tests '*PariteCorpusDepotTest*' \
#                   -Dparite.majBase=true --rerun-tasks   # re-signature digeste
#      Le DIFF de la baseline digeste fait partie de la revue du re-sync ;
#      `--rerun-tasks` est OBLIGATOIRE (la régénération est un effet de bord
#      d'une tâche de test — sans lui, Gradle peut déclarer la tâche
#      UP-TO-DATE et laisser une baseline périmée en silence).
#
# Usage :
#   bash scripts/vendor-grammaire-kotlin.sh <tag>          # ex. v2.3.0
#   bash scripts/vendor-grammaire-kotlin.sh <tag> <clone-amont>
#        (réutilise un clone existant de JetBrains/kotlin au lieu de cloner)
# ============================================================================
set -euo pipefail

TAG="${1:?usage : vendor-grammaire-kotlin.sh <tag> [clone-amont-existent]}"
CLONE="${2:-}"

cd "$(git rev-parse --show-toplevel)"
VENDOR="kotlin/src/main/vendor"
CHEMIN_AMONT="compiler/multiplatform-parsing/common/src"

# --- 1. Obtenir l'amont au tag (clone jetable ou clone fourni) -------------
if [[ -z "$CLONE" ]]; then
    CLONE="$(mktemp -d)/kotlin"
    echo "Clonage jetable de JetBrains/kotlin au tag ${TAG}…"
    git clone --quiet --depth 1 --branch "$TAG" https://github.com/JetBrains/kotlin.git "$CLONE"
fi
COMMIT="$(git -C "$CLONE" rev-parse HEAD^{commit})"
echo "Amont : ${TAG} (${COMMIT})"

# --- 2. Garde de licence : exceptions du chemin au tag ----------------------
# license/README.md liste les sections sous licence tierce ; AUCUNE ne doit
# toucher compiler/multiplatform-parsing (Apache-2.0 pur, R6.1).
if git -C "$CLONE" show "${TAG}:license/README.md" | grep -n "multiplatform-parsing"; then
    echo "ÉCHEC : exception de licence détectée sur ${CHEMIN_AMONT} au tag ${TAG}." >&2
    echo "  → STOP : décision propriétaire requise avant tout re-sync." >&2
    exit 1
fi
echo "Garde de licence : aucune exception sur ${CHEMIN_AMONT} [vérifié]."

# --- 3. Remplacement intégral (octet pour octet) ----------------------------
STAGE="$(mktemp -d)"
git -C "$CLONE" archive "$TAG" -- "$CHEMIN_AMONT" license/LICENSE.txt license/COPYRIGHT.txt | tar -x -C "$STAGE"
rm -rf "$VENDOR"
mkdir -p "$VENDOR"
mv "${STAGE}/${CHEMIN_AMONT}/." "$VENDOR/"
mv "${STAGE}/license/LICENSE.txt" "${STAGE}/license/COPYRIGHT.txt" "$VENDOR/"
NB_FICHIERS=$(find "$VENDOR" -name '*.kt' | wc -l)
NB_LIGNES=$(wc -l $(find "$VENDOR" -name '*.kt') | tail -1 | awk '{print $1}')
echo "Remplacé : ${NB_FICHIERS} fichiers Kotlin, ${NB_LIGNES} lignes."

# --- 4. Notice VENDOR.md régénérée (le tag y est consigné) -------------------
DATE="$(date +%Y-%m-%d)"
cat > "${VENDOR}/VENDOR.md" <<FIN
# Vendoring — la grammaire Kotlin multiplateforme du compilateur (module :kotlin)

> **Obtention R9.2** : ces fichiers sont obtenus **depuis l'amont JetBrains**
> (jamais depuis l'ancien dépôt \`codeide-lsp\`), à une version épinglée :
>
>     amont :   https://github.com/JetBrains/kotlin
>     chemin :  compiler/multiplatform-parsing/common/src
>     tag :     ${TAG} (commit ${COMMIT})
>     licence : Apache-2.0 (LICENSE.txt + COPYRIGHT.txt dans ce répertoire,
>               copiés depuis \`license/\` du dépôt amont au même tag)
>     vérification d'exception : \`license/README.md\` du tag ${TAG} ne liste
>               AUCUN fichier sous \`compiler/multiplatform-parsing\` — Apache-2.0
>               pur (R6.1 : vérifié par vendor-grammaire-kotlin.sh le ${DATE})
>
> Copie **octet pour octet** (\`git archive\` au tag) : ${NB_FICHIERS} fichiers
> Kotlin, ${NB_LIGNES} lignes. Un re-sync est un **remplacement**, jamais une
> fusion : il se fait par \`scripts/vendor-grammaire-kotlin.sh\` (miroir de la
> procédure de l'ADR 0121 — remplacement intégral + suite de parité + digeste
> comme gate). Règle d'épinglage : tag compilé par un bootstrap ≤ notre chaîne
> d'outils.
>
> **Rôle dans le module** : grammaire de production du moteur Kotlin LSP
> (voie C, ADR 0100 accepté ; stratégie (i), ADR 0103 accepté — vendoring
> amont épinglé, façade \`Kt*\` mince conservatrice, ADR 0104). Inscription au
> registre : \`docs/PROVENANCE-REPRISE-KOTLIN.md\` §4. La CI vérifie ce
> répertoire via \`scripts/verifier-provenance.sh\` (périmètre \`kotlin/\`).

## Dépendances amont (artefacts binaires, pas vendorés)

- \`org.jetbrains:syntax-api:0.3.340\` (variante \`-jvm\`, dépôt
  intellij-dependencies via cache-redirector) — l'arbre de construction
  syntaxique contre lequel la grammaire est écrite ; épinglée par le tag
  amont v2.3.0. Apache-2.0 (sources) — conditions des **binaires** à
  confirmer par le propriétaire (alerte P-K2 de l'étude, miroir AL1 Java :
  aucun \`META-INF/LICENSE\` dans les jars) avant toute publication du pack.
- \`org.jetbrains:annotations:26.0.2\` (Maven Central, Apache-2.0).

## Exemptions de qualité applicables au vendoring

Miroir de l'ADR 0115 de l'ancien dépôt (consigné dans l'ADR 0104 du
présent dépôt) : \`explicitApi()\` ne s'applique pas aux sources vendorées
(elles vivent dans une compilation séparée du module — voir
\`kotlin/build.gradle.kts\`) ; la couverture n'est pas exigée sur
\`org.jetbrains.kotlin.kmp.*\` : la grammaire est attestée par la suite de
parité du présent module (fiches 010–012 du cahier : corpus du dépôt sans
marqueur d'erreur, digeste structurel, tolérance aux fautes).
FIN
rm -rf "$STAGE"

# --- 5. Rappels de gates (à exécuter manuellement, à joindre à la revue) -----
cat <<FIN

Re-sync posé au tag ${TAG} (${COMMIT}) — GATES OBLIGATOIRES avant commit :

  ./gradlew :kotlin:test
  ./gradlew :kotlin:test --tests '*PariteCorpusDepotTest*' -Dparite.majBase=true --rerun-tasks

Le DIFF de la baseline digeste (kotlin/src/test/resources/parite/digeste-corpus.txt)
fait partie de la revue : toute dérive de forme d'arbre doit être EXPLIQUÉE
(changement de grammaire amont) avant signature. Mettre à jour le registre
(docs/PROVENANCE-REPRISE-KOTLIN.md §4/§6 : nouveau tag + commit) et le
descripteur de pack (version de grammaire, kotlin/build.gradle.kts).
FIN
