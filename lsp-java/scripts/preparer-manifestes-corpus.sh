#!/usr/bin/env bash
# ============================================================================
# preparer-manifestes-corpus.sh — V-A2 (ADR 0030) : prépare les manifestes de
# classpath du corpus réel (procédure de lsp-java/bancs/corpus-android/LISEZMOI.md §2).
#
# Pour chaque projet demandé :
#   1. compile la variante DEBUG avec le Gradle ÉPINGLÉ du projet
#      (compileDebugJavaWithJavac — génère R/BuildConfig/binding/Room) ;
#   2. exporte le classpath réellement résolu (lsp-java/scripts/manifeste-corpus.init.gradle :
#      artefacts EXTERNES de debugCompileClasspath, post-transformations AGP,
#      dépendances de PROJET) ;
#   3. écrit lsp-java/bancs/corpus-android/prepares/<projet>.manifeste (chemins ABSOLUS) :
#        racine:<répertoire de sources Java (main/debug du module app + des
#                modules locaux dépendants + répertoires générés par le build)>
#        racine-generee:<répertoire de sources GÉNÉRÉES (R, BuildConfig, binding…)>
#        classpath:<jar (android.jar de la plateforme + artefacts résolus ;
#                les AAR voient classes.jar et libs/*.jar extraites sous
#                prepares/<projet>-jars/)>
#
# Le manifeste N'EST JAMAIS COMMITTÉ (git-ignoré) : il dépend du SDK/Gradle
# locaux (note d'honnêteté R5 du LISEZMOI). Un projet dont la préparation
# échoue ne produit PAS de manifeste : la gate corpus correspondante
# s'ignore avec la raison affichée (jamais un vert silencieux).
#
# Usage :
#   ANDROID_HOME=<sdk> [JAVA21_HOME=<jdk>] [JAVA17_HOME=<jdk>] \
#     bash lsp-java/scripts/preparer-manifestes-corpus.sh [projet…]
#
#   JAVA21_HOME (défaut : JAVA_HOME) — projets Gradle ≥ 8 ;
#   JAVA17_HOME — requis pour basicsample (Gradle 7.5, cf. ALERTES AL3) ;
#     absent ⇒ manifeste basicsample DIFFÉRÉ avec raison imprimée.
#
# Code de sortie : nombre de projets en échec de préparation (0 = tout prêt).
# ============================================================================
set -euo pipefail

REPO=$(cd "$(dirname "$0")/../.." && pwd)
CORPUS="$REPO/lsp-java/bancs/corpus-android"
PREPARES="$CORPUS/prepares"
INIT="$REPO/lsp-java/scripts/manifeste-corpus.init.gradle"

ANDROID_HOME_RESOLU=${ANDROID_HOME:-}
if [[ -z "$ANDROID_HOME_RESOLU" ]]; then
  echo "ERREUR : ANDROID_HOME requis (SDK avec platforms;android-33/35/36/37…)." >&2
  exit 1
fi
JAVA21_HOME_RESOLU=${JAVA21_HOME:-${JAVA_HOME:-}}
JAVA17_HOME_RESOLU=${JAVA17_HOME:-}

# <clé> : répertoire du build Gradle · module app (chemin relatif, "" = racine)
#         · compileSdk épinglé par le projet · JDK exigé par le Gradle épinglé.
# (Valeurs vérifiées sur les commits épinglés du corpus, 31/10/2026 — jamais
# de mémoire : opentracks racine/src, markor+gh4a :app, anysoftkeyboard
# :ime/app multi-modules, basicsample projet interne BasicSample.)
declare -A REPERTOIRE=(
  [opentracks]="."
  [markor]="."
  [gh4a]="."
  [anysoftkeyboard]="."
  [basicsample]="BasicSample"
)
declare -A MODULE=(
  [opentracks]=""
  [markor]="app"
  [gh4a]="app"
  [anysoftkeyboard]="ime/app"
  [basicsample]="app"
)
declare -A COMPILE_SDK=(
  [opentracks]=36
  [markor]=35
  [gh4a]=36
  [anysoftkeyboard]=37
  [basicsample]=33
)
declare -A JDK_REQUIS=(
  [opentracks]=21
  [markor]=21
  [gh4a]=21
  [anysoftkeyboard]=21
  [basicsample]=17
)
# Variante mesurée : debug partout, SAUF projets à flavors — la tâche
# compileDebugJavaWithJavac y est ambiguë. OpenTracks : nightly/irreproducible/
# reproducible → irreproducible = le build communautaire F-Droid, sans
# signature. Markor : flavorAtest/flavorDefault/flavorGplay → flavorDefault =
# le flavor FOSS (sans services Google Play, celui des builds F-Droid).
declare -A VARIANTE=(
  [opentracks]="irreproducibleDebug"
  [markor]="flavorDefaultDebug"
  [gh4a]="debug"
  [anysoftkeyboard]="debug"
  [basicsample]="debug"
)

demandes=("$@")
if [[ ${#demandes[@]} -eq 0 ]]; then
  demandes=(opentracks markor gh4a anysoftkeyboard basicsample)
fi

echecs=0

# ---------------------------------------------------------------------------
# extrait_classes_aar <clé> <notation g:a:v> <fichier.aar>
# → imprime les jars EXTRAITS (classes.jar + libs/*.jar) sur stdout.
# ---------------------------------------------------------------------------
extrait_classes_aar() {
  local cle="$1" notation="$2" aar="$3"
  local sortie="$PREPARES/$cle-jars"
  mkdir -p "$sortie"
  local base="${notation//:/-}"
  local cible="$sortie/${base}-classes.jar"
  if [[ ! -s "$cible" ]]; then
    unzip -p "$aar" classes.jar > "$cible"
  fi
  echo "$cible"
  local libs nom ciblelib
  libs=$(unzip -Z1 "$aar" 'libs/*.jar' 2>/dev/null || true)
  while IFS= read -r lib; do
    [[ -z "$lib" ]] && continue
    nom=$(basename "$lib")
    ciblelib="$sortie/${base}-lib-${nom}"
    if [[ ! -s "$ciblelib" ]]; then
      unzip -p "$aar" "$lib" > "$ciblelib"
    fi
    echo "$ciblelib"
  done <<< "$libs"
}

# ---------------------------------------------------------------------------
# racines_generees <répertoire du module> <variante>
# → imprime les RACINES DE SOURCES (parent du paquet) des .java générés POUR
# LA VARIANTE mesurée (AGP y écrit R, BuildConfig, viewBinding/dataBinding,
# safe-args… — layout interne non stable, la découverte par système de
# fichiers est la plus honnête).
#
# DEUX formes de chemins coexistent selon le générateur (vérifié OpenTracks,
# AGP 8.x) : dataBinding écrit generated/<kind>/<variante>/… (variante EN UN
# SEGMENT), BuildConfig écrit generated/source/buildConfig/<flavor>/<buildType>/
# (flavor et buildType SÉPARÉS). Le filtre EXCLUT les R/classes des AUTRES
# variantes, qui dupliqueraient les mêmes FQCN.
#
# RACINE = répertoire du fichier MOINS le chemin de son PAQUET (le root d'un
# source set est le PARENT du paquet, pas le répertoire du fichier :
# …/debug/com/gh4a/BuildConfig.java de paquet com.gh4a → racine …/debug —
# sinon le moteur indexe le fichier sous un paquet erroné et le type reste
# non résolu ; vérifié sur les premières mesures V-A2 : 51 « BuildConfig » +
# ~350 « *Binding » non résolus venaient de là).
# ---------------------------------------------------------------------------
racines_generees() {
  local repertoire="$1" variante="$2"
  # debug → « debug » ; irreproducibleDebug → ajoute « irreproducible/debug ».
  # Segment TROISIÈME forme (vérifié anysoftkeyboard, safeargs 2.x/AGP 9) :
  # generated/java/generateSafeArgsDebug/<paquet> — la variante est SUFFIXE
  # camelCase du segment, d'où le [^/]* du premier motif.
  local motif="/build/generated/([^/]+/)*[^/]*$variante(/|$)"
  # camelCase : generateSafeArgs<Debug> (D majuscule).
  motif="$motif|/build/generated/([^/]+/)*[^/]*${variante^}(/|$)"
  if [[ "$variante" =~ ^(.*[a-z])Debug$ ]]; then
    motif="$motif|/build/generated/([^/]+/)*${BASH_REMATCH[1]}/debug(/|$)"
  fi
  # `|| true` : sous pipefail, grep sans correspondance (module SANS source
  # générée — fréquent en multi-modules, p. ex. les bibliothèques du corpus
  # anysoftkeyboard) ou find sur un répertoire absent sortent 1 et tueraient
  # le script (set -e) — l'absence de racine générée est une issue NORMALE.
  find "$repertoire/build/generated" -name '*.java' 2>/dev/null \
    | grep -E "$motif" | sort -u | while IFS= read -r fichier; do
      local dir pkg chemin_pkg racine
      dir=$(dirname "$fichier")
      pkg=$(grep -m1 -oE '^[[:space:]]*package[[:space:]]+[a-zA-Z0-9_.]+' "$fichier" 2>/dev/null \
        | sed -E 's/^[[:space:]]*package[[:space:]]+//')
      if [[ -n "$pkg" && "$pkg" != *[!a-zA-Z0-9_.]* ]]; then
        chemin_pkg=${pkg//./\/}
        racine=${dir%/$chemin_pkg}
        if [[ "$racine" != "$dir" ]]; then
          echo "$racine"
          continue
        fi
      fi
      echo "$dir"
    done | sort -u || true
}

# ---------------------------------------------------------------------------
# preparer_projet <clé>
# ---------------------------------------------------------------------------
preparer_projet() {
  local cle="$1"
  local repertoire="$CORPUS/$cle/${REPERTOIRE[$cle]}"
  local module="${MODULE[$cle]}"
  local sdk="${COMPILE_SDK[$cle]}"
  local variante="${VARIANTE[$cle]}"
  # Chemin Gradle du module app ("ime/app" → ":ime:app" ; racine → "").
  local module_path=""
  [[ -n "$module" ]] && module_path=":${module//\//:}"
  # Le chemin imprimé par le script d'init pour le projet racine est ":".
  local app_awk=":${module//\//:}"; [[ -z "$module" ]] && app_awk=":"

  if [[ ! -d "$repertoire" ]]; then
    echo "— $cle : corpus absent ($repertoire) — lancez lsp-java/scripts/telecharger-corpus-android.sh $cle"
    echecs=$((echecs + 1))
    return
  fi

  local jdk
  if [[ "${JDK_REQUIS[$cle]}" == "17" ]]; then
    jdk="$JAVA17_HOME_RESOLU"
    if [[ -z "$jdk" || ! -d "$jdk" ]]; then
      echo "— $cle : JDK 17 requis (Gradle épinglé 7.5, ALERTES AL3) et JAVA17_HOME vide — manifeste DIFFÉRÉ (raison consignée)"
      echecs=$((echecs + 1))
      return
    fi
  else
    jdk="$JAVA21_HOME_RESOLU"
    if [[ -z "$jdk" || ! -d "$jdk" ]]; then
      echo "— $cle : JDK 21 requis (JAVA_HOME/JAVA21_HOME) — manifeste DIFFÉRÉ (raison consignée)"
      echecs=$((echecs + 1))
      return
    fi
  fi

  # Plateforme : nommage direct (android-35) OU versionné mineur (android-36.1,
  # android-37.0 — officiel depuis 2025 : l'api-level peut porter une décimale).
  local android_jar=""
  local candidat
  for candidat in "$ANDROID_HOME_RESOLU/platforms/android-$sdk"/android.jar \
    "$ANDROID_HOME_RESOLU/platforms/android-$sdk."*/android.jar; do
    if [[ -s "$candidat" ]]; then
      android_jar="$candidat"
      break
    fi
  done
  if [[ -z "$android_jar" ]]; then
    echo "— $cle : plateforme android-$sdk absente du SDK ($ANDROID_HOME_RESOLU/platforms/) — manifeste DIFFÉRÉ (raison consignée ; sdkmanager \"platforms;android-$sdk\")"
    echecs=$((echecs + 1))
    return
  fi

  # Adaptation documentée (ALERTES AL10) : le miroir GitHub d'OpenTracks au
  # commit épinglé 3c23a9f5 n'a PAS de fichier settings — sans lui, Gradle
  # remonte l'arbre et résout le settings.gradle.kts de CODE-LSP (le corpus
  # vit dans lsp-java/bancs/ du dépôt). Création d'un settings minimal : AUCUNE
  # source du corpus n'est touchée, l'adaptation est imprimée et consignée.
  if [[ ! -f "$repertoire/settings.gradle" && ! -f "$repertoire/settings.gradle.kts" ]]; then
    echo "  AVERTISSEMENT : aucun fichier settings dans $repertoire — création d'un settings.gradle minimal (adaptation documentée, aucune source touchée)"
    echo "rootProject.name = '$cle'" > "$repertoire/settings.gradle"
  fi

  # Adaptation documentée : gh4a exige client.properties (OAuth GitHub, lu par
  # app/build.gradle pour BuildConfig.CLIENT_ID/SECRET) — le README demande de
  # vrais credentials pour l'APK signé ; pour la COMPILATION du corpus, des
  # valeurs factices suffisent (aucun appel réseau à la compilation). Le
  # fichier est dans le .gitignore de gh4a : aucune source du corpus n'est
  # touchée. NB vérifié : `new File('client.properties')` dans app/build.gradle
  # se résout contre le RÉPERTOIRE D'INVOCATION du daemon (racine du projet),
  # pas le projectDir de :app — d'où l'emplacement racine.
  if [[ "$cle" == "gh4a" && ! -s "$repertoire/client.properties" ]]; then
    echo "  AVERTISSEMENT : client.properties absent à la racine (OAuth GitHub du README gh4a) — création de valeurs FACTICES (compilation seule, git-ignoré par gh4a)"
    printf 'ClientId="corpus-placeholder"\nClientSecret="corpus-placeholder"\n' > "$repertoire/client.properties"
  fi

  echo "— $cle : build debug (Gradle épinglé, JDK ${JDK_REQUIS[$cle]}) + export du classpath…"
  local log="$PREPARES/$cle.build.log"
  mkdir -p "$PREPARES"
  local taches=("${module_path}:compile${variante^}JavaWithJavac" "manifesteCorpusTous")
  # --max-workers=1 + -Dorg.gradle.internal.http.maxConnections=1 : mitigation
  # du rate-limit Maven Central (ALERTES AL4, rafales parallèles → 429) — le
  # build reprend sur cache à chaque tentative (rejouer le script suffit).
  # -Dorg.gradle.jvmargs (précédence MAXIMALE, > gradle.properties du projet) :
  # les corpus demandent jusqu'à -Xmx8000M (anysoftkeyboard) — sur un poste
  # de 4 Go le daemon serait tué par l'OOM-killer. Plafond 1400m + Méta-
  # espace 512m : assez pour la CONFIGURATION AGP + compileJava (le build ne
  # compile PAS de Kotlin — cible Java uniquement).
  if ! (
    cd "$repertoire" &&
    JAVA_HOME="$jdk" ANDROID_HOME="$ANDROID_HOME_RESOLU" \
    GRADLE_OPTS="-Dorg.gradle.internal.http.maxConnections=1" \
      ./gradlew "${taches[@]}" --console=plain --no-daemon --no-configuration-cache \
      --max-workers=1 -Pcodeide.variante="$variante" --stacktrace --init-script "$INIT" \
      "-Dorg.gradle.jvmargs=-Xmx1400m -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8"
  ) > "$log" 2>&1; then
    echo "  ÉCHEC du build $cle — log : $log (20 dernières lignes :)"
    tail -20 "$log" | sed 's/^/    /'
    echecs=$((echecs + 1))
    return
  fi

  # ---- Honnêteté (jamais un vert silencieux) : le script d'init imprime des
  # ---- replis ARTIFACT-ERREUR/ARTIFACT-ABSENT/SRCROOT-ERREUR quand une
  # ---- résolution échoue PARTIELLEMENT (le build peut réussir sur cache) —
  # ---- un manifeste écrit dans cet état serait INCOMPLET (classpath tronqué)
  # ---- sans que rien n'échoue. On refuse : rejouer le script reprend sur le
  # ---- cache Gradle et complète la résolution.
  local artefacts_en_echec
  artefacts_en_echec=$(grep -cE "^ARTIFACT-ERREUR |^ARTIFACT-ABSENT |^SRCROOT-ERREUR " "$log" || true)
  if [[ "$artefacts_en_echec" -gt 0 ]]; then
    echo "  ÉCHEC d'export $cle : $artefacts_en_echec repli(s) ARTIFACT-*/SRCROOT-* dans le log (résolution incomplète — manifeste REFUSÉ ; rejouer le script reprend sur le cache Gradle) :"
    grep -E "^ARTIFACT-ERREUR |^ARTIFACT-ABSENT |^SRCROOT-ERREUR " "$log" | head -3 | sed 's/^/    /'
    echecs=$((echecs + 1))
    return
  fi

  # ---- Analyse du log : module app (ARTIFACT/PROJET-DEP), racines de tous
  # ---- les modules concernés (SRCROOT du module app + des modules dépendants).

  # Chemins des modules dépendants (":ime:base" → "$repertoire/ime/base").
  local deps
  deps=$(awk -v app="$app_awk" '
    /^MODULE / { line=$0; sub(/^MODULE /, "", line); split(line, p, /\|\|/); module=p[2]; module_dir[p[2]]=p[1]; courant=module }
    /^PROJET-DEP / && courant==app { print $2 }
  ' "$log")

  # Racines SRCROOT du module app + des modules dépendants (dédupliquées).
  local racines
  racines=$(awk -v app="$app_awk" -v deps="$(echo "$deps" | tr '\n' ' ')" '
    BEGIN { n=split(deps, d, / +/); cible[app]=1; for (i=1;i<=n;i++) if (d[i]!="") cible[d[i]]=1 }
    /^MODULE / { line=$0; sub(/^MODULE /, "", line); split(line, p, /\|\|/); courant=p[2] }
    /^SRCROOT / { if (courant in cible) print $2 }
  ' "$log" | sort -u)

  # Répertoires générés du module app + des modules dépendants.
  local module_dirs
  module_dirs=$(awk -v app="$app_awk" -v deps="$(echo "$deps" | tr '\n' ' ')" '
    BEGIN { n=split(deps, d, / +/); cible[app]=1; for (i=1;i<=n;i++) if (d[i]!="") cible[d[i]]=1 }
    /^MODULE / { line=$0; sub(/^MODULE /, "", line); split(line, p, /\|\|/); if (p[2] in cible) print p[1] }
  ' "$log" | sort -u)
  local generees=""
  local dir sortie
  while IFS= read -r dir; do
    [[ -z "$dir" ]] && continue
    sortie=$(racines_generees "$dir" "$variante")
    # +$'\n' explicite : $() AVALE les retours à la ligne finaux — sans lui,
    # les lots successifs se COLLERAIENT en un chemin unique invalide (bug
    # vérifié anysoftkeyboard : 3 racines buildConfig fondues en une).
    [[ -n "$sortie" ]] && generees+="$sortie"$'\n'
  done <<< "$module_dirs"

  # ---- Classpath : android.jar + artefacts externes (AAR → extraction) +
  # ---- R.jar d'AGP (les symboles R ne sont JAMAIS des sources en AGP 8 :
  # ---- ils sont compilés en jar sous build/intermediates —
  # ---- compile_and_runtime_not_namespaced_r_class_jar/<variante>/…/R.jar
  # ---- pour le module app, compile_r_class_jar/<variante>/…/R.jar pour les
  # ---- bibliothèques ; vérifié sur les premières mesures V-A2 : ~3 800
  # ---- « R cannot be resolved » venaient de cette absence).
  local classpath="$android_jar"
  local rjar
  while IFS= read -r rjar; do
    [[ -s "$rjar" ]] && classpath+=$'\n'"$rjar"
  done < <(find $module_dirs -maxdepth 7 \( \
      -path "*compile_and_runtime_not_namespaced_r_class_jar*" -o \
      -path "*compile_r_class_jar*" \) -name "R.jar" 2>/dev/null | sort -u)

  # ---- Classes KOTLIN compilées (AGP ≥ 8 « built-in kotlinc », vérifié
  # ---- anysoftkeyboard/AGP 9.4) : les projets mixtes référencent les classes
  # ---- Kotlin depuis le Java — javac compile contre
  # ---- build/intermediates/built_in_kotlinc/<variante>/compile<Variante>Kotlin/classes ;
  # ---- le moteur (ecj, Java seul) ne lit ni les .kt ni ce répertoire →
  # ---- zippés en jar sous prepares/<clé>-kotlin/ (mesures V-A2 : 18 FP
  # ---- « cannot subclass the final class » [quirk ecj : supertype non
  # ---- résolu] et « MSG_* cannot be resolved » venaient de là).
  local kotlinc_dir module_rel cible_kt
  while IFS= read -r kotlinc_dir; do
    [[ -d "$kotlinc_dir" ]] || continue
    module_rel=${kotlinc_dir#"$CORPUS/$cle/"}
    module_rel=${module_rel%%/build/intermediates/*}
    cible_kt="$PREPARES/$cle-kotlin/${module_rel//\//__}.jar"
    mkdir -p "$PREPARES/$cle-kotlin"
    if [[ ! -s "$cible_kt" ]] || [[ -n "$(find "$kotlinc_dir" -name '*.class' -newer "$cible_kt" -print -quit 2>/dev/null)" ]]; then
      (cd "$kotlinc_dir" && "$jdk/bin/jar" cf "$cible_kt" .)
    fi
    classpath+=$'\n'"$cible_kt"
  done < <(find $module_dirs -maxdepth 6 -path "*built_in_kotlinc*" -type d -name classes 2>/dev/null \
      | grep "/$variante/" | sort -u)
  local notation fichier
  while IFS= read -r ligne; do
    [[ -z "$ligne" ]] && continue
    notation="${ligne%% *}"
    fichier="${ligne#* }"
    if [[ "$fichier" == *.aar ]]; then
      classpath+=$'\n'"$(extrait_classes_aar "$cle" "$notation" "$fichier")"
    elif [[ -d "$fichier" ]]; then
      # Artefact répertoire (AAR explosé par transformation) : classes.jar + libs.
      local jar
      while IFS= read -r jar; do
        [[ -s "$jar" ]] && classpath+=$'\n'"$jar"
      done < <(find "$fichier" -maxdepth 3 \( -name 'classes.jar' -o -path '*/libs/*.jar' \) -type f 2>/dev/null | sort -u)
    elif [[ -s "$fichier" ]]; then
      classpath+=$'\n'"$fichier"
    else
      echo "  AVERTISSEMENT : artefant introuvable ignoré ($notation : $fichier)"
    fi
  done < <(awk -v app="$app_awk" '
      /^MODULE / { line=$0; sub(/^MODULE /, "", line); split(line, p, /\|\|/); courant=p[2] }
      /^ARTIFACT / && courant==app { ligne=$0; sub(/^ARTIFACT /, "", ligne); print ligne }
    ' "$log")
  classpath=$(echo "$classpath" | sort -u | grep -v '^$')

  # ---- Écriture du manifeste (chemins ABSOLUS, cf. LISEZMOI §2).
  local manifeste="$PREPARES/$cle.manifeste"
  local commit
  commit=$(git -C "$CORPUS/$cle" rev-parse HEAD 2>/dev/null || echo "commit inconnu")
  {
    echo "# Manifeste de classpath du corpus — $cle (V-A2, ADR 0030)"
    echo "# Généré par lsp-java/scripts/preparer-manifestes-corpus.sh le $(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo "# Corpus : $cle @ $commit (lsp-java/scripts/telecharger-corpus-android.sh)"
    echo "# Plateforme : android-$sdk (compileSdk épinglé par le projet) ; variante $variante,"
    echo "# module ${module_path:-racine}, dépendances de projet : ${deps//[[:space:]]+/ }"
    echo "# Jamais committé (git-ignoré) — dépend du SDK/Gradle locaux (R5)."
    local r
    while IFS= read -r r; do
      [[ -z "$r" ]] && continue
      echo "racine:$r"
    done <<< "$racines"
    while IFS= read -r r; do
      [[ -z "$r" ]] && continue
      echo "racine-generee:$r"
    done <<< "$generees"
    local c
    while IFS= read -r c; do
      [[ -z "$c" ]] && continue
      echo "classpath:$c"
    done <<< "$classpath"
  } > "$manifeste"

  echo "  $cle : manifeste prêt ($manifeste) — racines $(echo "$racines$generees" | grep -c '^/' ), classpath $(echo "$classpath" | grep -c '^/')"
}

for cle in "${demandes[@]}"; do
  if [[ -z "${REPERTOIRE[$cle]:-}" ]]; then
    echo "ERREUR : projet inconnu « $cle » (connus : ${!REPERTOIRE[*]})" >&2
    echecs=$((echecs + 1))
    continue
  fi
  preparer_projet "$cle"
done

echo ""
if [[ $echecs -eq 0 ]]; then
  echo "Préparation terminée : ${#demandes[@]} manifeste(s). Gates corpus :"
  echo "  CORPUS_ANDROID=$CORPUS ./gradlew :java:test --tests 'jo.codeide.lsp.java.coherence.*'"
else
  echo "Préparation PARTIELLE : $echecs échec(s) — les gates des projets sans manifeste"
  echo "s'ignorent avec la raison affichée (jamais un vert silencieux)."
fi
exit "$echecs"
