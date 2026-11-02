# 02 — Spike de mesure (`spike-kotlin/`, Phase 2)

> Étude « moteur Kotlin » pour CodeIDE sur Android/ART — mesures des
> variantes S0–S3. **Question de recherche (prompt §7)** : quelle
> architecture sémantique tient sur ART avec une empreinte acceptable,
> et à quel prix d'intégration ?
>
> Discipline R5 : chaque chiffre est étiqueté **[mesuré]** (commande
> rejouable ci-dessous), **[estimé]** (modèle documenté) ou
> **[non vérifié]**. Toute mesure JVM de bureau est une **limite basse**
> (facteur ART 3–5× [estimé — retour d'expérience de l'étude Java, non
> vérifié sur appareil]). Aucune mesure appareil n'est prétendue (E-K2).

## 1. Environnement des mesures [mesuré]

- Bac à sable conteneurisé, **2 cœurs** effectifs, 3,9 Gio de mémoire,
  OpenJDK 21.0.13 (Temurin auto-provisionné par foojay pour la
  compilation), le **2026-10-30** (`resultats/environnement.txt`).
- Corpus : générateur déterministe (`GenerateurCorpus.kt`, graine = index
  de bloc) — fichiers Kotlin réalistes de 500 et 3 000 lignes
  (classes, constructeurs, membres, fonctions top-level, lambdas,
  imports stdlib) — reproductibles à l'octet près.
- Mêmes corpus, même jour, même JVM pour S0/S1/S2 (exigence du prompt).

## 2. Reproduction (R5 — commandes exactes)

```bash
# 1. Télécharger les piles (jars HORS dépôt, jamais committés — R6.4) ;
#    le jeton GitHub est requis pour l'oracle S0 :
GITHUB_TOKEN=<jeton> bash spike-kotlin/scripts/telecharger-jars.sh [cible]

# 2. Lancer les bancs (module non publié, tâches gardées par -PspikeKotlin) :
./gradlew :spike-kotlin:test -PspikeKotlin
#    (surcharge du répertoire des jars : -PspikeJars=<chemin>)

# 3. Scan ART statique des piles :
python3 spike-kotlin/scripts/scan-art.py <jar>…   # résultat : resultats/scan-art-*.txt
```

R4 respecté : seuls les tests de `:spike-kotlin` sont exécutés en local ;
aucun build complet du dépôt ; la CI du domaine Java n'est pas affectée
(garde `-PspikeKotlin`, voir `spike-kotlin/build.gradle.kts`).

## 3. Les piles mesurées

| Voie | Pile | Taille [mesuré] | Classes | Méthodes déclarées | Motifs sensibles ART |
|---|---|---|---:|---:|---:|
| **S0** — oracle (ancien moteur publié `:lsp:kotlin` 1.22.1, GitHub Packages) | `lsp-kotlin-1.22.1.jar` + syntax-api 0.3.340 + annotations + kotlin-metadata-jvm | **0,86 Mo** (moteur seul) | 287 | 355 | **0** (1 référence `ServiceLoader` = le SPI, présent sur ART) |
| **S1** — frontend K2 embarqué | `kotlin-compiler-embeddable` 2.2.10 + stdlib + coroutines + annotations | **53,6 Mo** (compilateur seul) | **25 019** | **64 507** | **~2 170** (MethodHandle ×1 050, `MethodHandles$Lookup` ×1 034, swing ×33, awt ×13, management ×13, `readAllBytes` ×7, `ClassValue` ×5, `Introspector` ×3, **`sun.misc.Unsafe` ×2**, `readNBytes` ×1) |
| **S2** — parseur officiel seul (grammaire amont v2.3.0, R9.2) | vendoring `compiler/multiplatform-parsing/common/src` (23 f / 9 585 l.) + syntax-api 0.3.340 | **0,51 Mo** (syntax-api) + grammaire compilée | 309 (syntax-api) | 514 (syntax-api) | **0** |

Détail des scans : `resultats/scan-art-s0-s2.txt`, `resultats/scan-art-s1.txt`.
L'Analysis API standalone **n'est pas publiée sur Maven Central**
[mesuré — requête `search.maven.org`, numFound: 0] : la voie (ii) du
prompt n'a pas de vaisseau artefact autonome — soit le compilateur
embarqué (S1), soit les artefacts de plateforme IntelliJ (piles du spike
Java V2 : 37,4 Mo min. / 124 513 méthodes / 108 motifs — ADR 0004 Java).

## 4. Mesures de latence [mesuré — limites basses JVM]

| Opération | S0 oracle | S1 K2 (sous-processus) | S2 parseur officiel |
|---|---|---|---|
| Démarrage à froid (trivial) | — | **3 503 / 3 802 ms** (2 passes) | — |
| Parse/reparse 500 l. — froid | 4,0 ms (`ouvrirDocument`) | (6 231 ms compilation complète) | 10,0 ms (première passe) |
| Parse/reparse 500 l. — chaud | **3,4 ms** (min de 5) | — | **1,8 ms** (p50), 5,9 ms (p95) |
| Diagnostics 500 l. — froid | 46,2 ms | — | — |
| Diagnostics 500 l. — chaud | **16,5 ms** (min de 5) | — | — |
| Parse/reparse 3 000 l. — chaud | **37,7 ms** | — | **8,6 ms** (p50), 33,6 ms (p95) |
| Diagnostics 3 000 l. — froid/chaud | 1 059 / 364 ms | — | — |
| Compilation K2 500 l. | — | **6 231 ms** | — |
| Compilation K2 3 000 l. | — | **13 931 ms** | — |
| Volumétrie d'arbre (3 000 l.) | — | — | 31 368 productions / 24 274 jetons |

Sorties brutes : `resultats/mesures-brutes.txt` (une ligne greppable par
opération, rejouable par la commande §2).

**Recoupement avec les chiffres publiés de l'ancien dépôt** [mesuré par
le rapport faux positifs 2026-10-26, document 1 803 l., stdlib nourrie] :
reparse 31 ms froid / 21 ms chaud ; diagnostics 469 ms froid / 147 ms
chaud. Notre S0 (vue VIDE, plan édition seul) à 3 000 l. : reparse chaud
37,7 ms — **même ordre de grandeur** (l'échelle ×1,66 de corpus et le
plans classpath absent se compensent approximativement) ; diagnostics
chaud 364 ms (vs 147 ms à 1 803 l. — croissance super-linéaire avec la
taille du document, à documenter au banc de qualité). La cohérence
inter-bancs valide le pilote.

## 5. Lecture des résultats

### 5.1 Le parseur officiel est un plancher excellent (S2)

1,8 ms p50 pour 500 lignes, 8,6 ms p50 pour 3 000 lignes, p95 33,6 ms,
**zéro lever sur 100 troncatures hostiles** [mesuré]. L'empreinte
(syntax-api 0,51 Mo + grammaire) et la sensibilité ART (0 motif)
autorisaient déjà l'ancien moteur à respecter la cible T1 ≤ 4 Mo. La
grammaire **ré-obtenue de l'amont au tag v2.3.0 compile tel quel** avec
la chaîne du dépôt (Kotlin 2.2.10, JVM 17, toolchain 21) [mesuré —
`compileKotlin` vert] : le re-vendoring est un remplacement scripté, pas
un portage.

### 5.2 La « profondeur K2 à la demande » coûte des secondes (S1)

Le seul vaisseau K2 disponible (compilateur embarqué, 53,6 Mo) demande
**3,5 s de démarrage à froid** et **6,2 s pour analyser entièrement 500
lignes** en sous-processus [mesuré]. Même en gardant le compilateur chaud
en processus dédié (l'amortissement soustrait ~3,5 s de démarrage : ~2,7 s
restants pour 500 l. [estimé par différence]), la profondeur K2 reste
**3 ordres de grandeur au-dessus** du budget de frappe (< 100 ms). La
voie (ii) « Analysis API/K2 par frappe » est donc **structurellement
écartée pour l'édition** — ce qui confirme numériquement la décision D1
de l'ancien dépôt, cette fois par la mesure et non par transmission
documentaire (R9.4). Un « mode diagnostic profond à la demande »
(voie (iii) hybride) reste plausible **en tâche explicite** (commande
utilisateur, délai annoncé), jamais sur le chemin de frappe.

### 5.3 L'empreinte S1 disqualifie l'embarquement direct dans l'APK

53,6 Mo de jars (avant dexage), 25 019 classes, 64 507 méthodes
déclarées [mesuré — borne basse], ~2 170 références sensibles ART dont
2 × `sun.misc.Unsafe` et 33 × swing. La cible T1 est ≤ 4 Mo : **rapport
×13 en taille seule**, avant de compter les shims. Miroir exact du
verdict V2 du spike Java (37,4 Mo / 124 513 méthodes / 108 motifs —
disqualifié). Toute variante K2 passera donc par : (a) processus séparé
(voie (iv)), (b) pack langue téléchargeable (D6 ancien), ou (c) mode
profond différé au tooling — les trois sont des décisions ADR 0103.

### 5.4 Le coût d'intégration vécu du compilateur embarqué [mesuré]

Pour ne serait-ce que COMPILER un fichier trivial en sous-processus, il a
fallu : le jar compilateur + `kotlin-stdlib` (classpath JVM **et**
classpath de bibliothèque) + `kotlinx-coroutines-core` + `org.jetbrains:
annotations` — quatre artefacts, deux `-cp` distincts, sinon
`NoClassDefFoundError` (stdlib, coroutines) puis exception de codegen
(annotations). C'est un échantillon du « prix d'intégration » que le
prompt demande de mesurer : l'ancien dépôt documente en plus (non vérifié
ici) 9 passes ASM au dexage, `kotlinc-resources.zip` extrait au runtime,
stdlib en ressource et fork de VM persistante (LSP_KOTLIN §2).

### 5.5 L'oracle S0 tient les cibles publiées sur le plan édition

Reparse 3,4 ms (500 l.) et 37,7 ms (3 000 l.) à chaud [mesuré] : la
cible « complétion à chaud < 100 ms, fichier ≤ 2 000 lignes » est tenue
avec une marge ×3 sur le plan édition. Le point de vigilance est le
**diagnostic complet chaud à grande échelle** (364 ms à 3 000 l. — le
rapport faux positifs recommandait déjà de mesurer à cette échelle avant
d'investir dans l'incrémentalité). Le moteur neuf devra viser la même
frontière : reparse complet bon marché, diagnostics complets sous la
demi-seconde à 3 000 lignes sur JVM — à re-mesurer sur appareil.

## 6. Ce que le spike ne mesure pas (bornes honnêtes)

1. **ART** : aucune exécution sur appareil (E-K2) — le facteur 3–5× est
   [estimé] ; le point d'entrée appareil reste à produire (gabarit
   `spike/appareil/` du domaine Java à décliner).
2. **Dexage réel** : d8 non disponible dans le bac à sable — comptage de
   méthodes déclaré (borne basse) et scan statique seulement ; la CI du
   domaine Java (scan ART + dexage + seuils) devra être étendue au module
   Kotlin (vague 0, ADR 0119).
3. **Le plan classpath** de S0 (vue vide) : index, décodage `@Metadata`,
   complétion classpath ne sont pas bancés ici — l'ancien dépôt les
   mesure (rejeu des corpus E2E Compose/Views) ; le banc de qualité
   Kotlin (ADR 0119) les reprendra.
4. **Mémoire** : le tas des variantes n'est pas profilé (conteneur 3,9
   Gio) — S1 consomme ~2 Gio de tas JVM pour compiler [observé via -Xmx2g
   sans OOM, non profilé] ; à mesurer proprement sur appareil.
5. **S3 (hybride)** : mesuré par composition — S2 en plan chaud + coût
   d'accès S1 (3,5 s froid) en profondeur différée ; aucun prototype S3
   intégré n'a été écrit (hors périmètre de mesure : c'est une
   architecture, pas une pile).

## 7. Verdict du spike (entrée de la Phase 3)

- La **voie technique (i)** (parseur officiel + modèle maison) tient
  numériquement le plan édition : c'est la seule pile à la fois sous le
  budget d'empreinte (×8 de marge), ART-propre (0 motif) et dans le
  budget de latence (1,8 ms p50).
- La **voie (ii)** (Analysis API/K2 par frappe) est disqualifiée par la
  mesure : pas de vaisseau autonome publié, 3,5 s de coût d'amorçage,
  53,6 Mo, 2 170 motifs sensibles.
- La **voie (iii)** (hybride) est viable comme ARCHITECTURE : plan chaud
  maison (S2), profondeur K2 en tâche explicite différée (coût S1
  assumé et annoncé) — la frontière « jamais K2 sur une frappe » devient
  une propriété vérifiable.
- La **voie (iv)** (serveur externe) hérite du coût S1 + un coût de
  processus/IPC — non mesurée ici [non vérifié], à ne rouvrir que si le
  tooling (T2) en a besoin.

Ces verdicts alimentent l'ADR 0103 (stratégie sémantique) ; la décision
de gouvernance (A/B/C/D) reste au propriétaire (ADR 0100).
