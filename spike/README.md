# spike/ — Module de mesure isolé (Phase 2 du prompt de mission)

> Étude « moteur Java PSI » pour CodeIDE sur Android/ART. **Module de mesure uniquement** : aucun code de production (§0 du prompt). Code neuf en Kotlin (règle R2 / ADR 0108 de l'ancien dépôt). Les jars mesurés sont **téléchargés, jamais commités** dans ce dépôt (R6.4, `docs/PROVENANCE-REPRISE.md`).

## Question de recherche (§6 du prompt)

PSI, ou la partie utile, tient-il sur ART avec une empreinte acceptable, et à quel prix d'intégration ? Trois voies mesurées sur **la même JVM, le même jour, les mêmes corpus** :

| Voie | Pile | Contenu | Taille jars | Méthodes déclarées |
|---|---|---|---|---|
| **V1** | `org.jetbrains:java-syntax 0.3.381` (+ syntax-api, lang-syntax, ij-multiplatform-util) | Parseur Java KMP autonome ( publié JetBrains ) | **798 Ko** | **2 965** |
| **V2** | `com.jetbrains.intellij.{platform,java}:232.4652` ( pile minimale à 23 jars, cf. `resultats/v2-pile-minimale.txt` ) | PSI headless complet avec résolution native | 37,4 Mo (min.) / 75,4 Mo (testée) | 124 513 (min.) / 168 448 (testée) |
| **V3** | `org.eclipse.jdt:ecj:3.40.0` | Référence : le moteur du statu quo ( **non patché ART** ) | 3,2 Mo | 11 990 |

Résultats : `resultats/` — synthèse et limites : `../docs/etudes/moteur-java-psi/02-spike-mesures.md`.

## Reproduction (R5)

Environnement des mesures consignées : OpenJDK 21.0.12.1 (Debian 13, amd64), JVM de bureau, `-Xmx2g`, machine de CI du bac à sable, le 2026-10-28 entre 10:58 et 12:40. **Toute mesure JVM est une limite basse** (facteur ART 3–5×, `docs/LSP_JAVA.md` §4 de l'ancien dépôt).

### 1. Télécharger les piles

```bash
bash spike/scripts/telecharger-piles.sh <repertoire-jars>   # ex. ../spike-jars
```

Crée `v1/`, `v2/` et le compilateur Kotlin 2.2.20 (`kotlinc-dist/`). Les coordonnées des artefacts fastutil/trove4j/automaton/asm-all ( hébergés intellij-dependencies ) sont dans les `.pom` téléchargés à côté.

### 2. Générer le corpus

```bash
# corpus synthétique déterministe (graine 42, ~500 lignes, 26 méthodes)
kotlinc-dist/kotlinc/bin/kotlinc -d build scripts/GenerateurCorpus.kt
java -cp build GenerateurCorpusKt corpus/Fichier500.java 500
```

Corpus réels ( parse uniquement, extraits du terrain d'étude `intellij-community` @ `865067b0` ) :
`java/java-psi-impl/src/com/intellij/psi/impl/source/tree/java/PsiMethodReferenceExpressionImpl.java` (494 l.) et `platform/util/src/com/intellij/openapi/util/io/FileUtil.java` (1 564 l.).

### 3. Compiler et lancer les bancs

```bash
KOTLINC=kotlinc-dist/kotlinc/bin/kotlinc
V1=<repertoire-jars>/v1 ; V2=<repertoire-jars>/v2 ; ECJ=<repertoire-jars>/ecj-3.40.0.jar
KS=<repertoire-jars>/kotlin-stdlib-2.2.20.jar

# V1 — parseur seul (lexing + arbre de production)
$KOTLINC -cp "$V1/java-syntax-jvm-0.3.381.jar:$V1/syntax-api-jvm-0.3.381.jar:$V1/lang-syntax-jvm-0.3.381.jar:$V1/ij-multiplatform-util-jvm-0.3.381.jar" \
    -d build scripts/BancV1.kt
java -Xmx2g -cp "build:$V1/*.jar:$KS" BancV1Kt corpus/Fichier500.java 200 50

# V2 — PSI headless complet (nécessite un « home » factice, cf. taxe d'assemblage)
$KOTLINC -cp "$V2/…23 jars…" -d build-v2 scripts/BancV2.kt
java -Xmx2g -Didea.home.path=$(pwd)/app-home -cp "build-v2:$V2/…" \
    BancV2Kt corpus/Fichier500.java $V2/guava-32.0.1-jre.jar 100

# V3 — référence ecj (parse diet/complet + compilation résolue)
$KOTLINC -cp "$ECJ" -d build-v3 scripts/BancV3.kt
java -Xmx2g -cp "build-v3:$ECJ:$KS" BancV3Kt corpus/Fichier500.java $V2/guava-32.0.1-jre.jar 200
```

La liste exhaustive des 23 jars de la pile V2 (classpath exact) figure en tête de `resultats/scan-art-v2-minimale.txt` et dans `02-spike-mesures.md` §2.2 — commandes complètes, sans joker.

### 4. Scanner de compatibilité ART

```bash
$KOTLINC -d build-scan scripts/ScanArt.kt
java -cp "build-scan:$KS" ScanArtKt <jar> [<jar>…]    # analyse des constant pools (JVMS §4)
```

Sorties consignées : `resultats/scan-art-{v1,v2,v2-minimale,v3-ecj}.txt`.

### 5. Sur appareil (Termux / AndroidIDE) — point d'entrée

Voir `appareil/` : procédure `banc-appareil.sh` + gabarit de résultats `GABARIT-RESULTATS.md` ( §6.c du prompt : le propriétaire exécute, aucun résultat appareil n'est prétendu sans lui ).

## Contenu

```
spike/
├── README.md                  (ce fichier)
├── app-home/                  « home » IntelliJ factice (bin/idea.properties vide) — taxe d'assemblage V2
├── appareil/                  point d'entrée benchmark appareil (procédure + gabarit)
├── corpus/                    corpus déterministe (Fichier500.java, graine 42)
├── resultats/                 sorties brutes des bancs et scans (consignées)
└── scripts/
    ├── BancV1.kt              banc V1 — parse java-syntax seul
    ├── BancV2.kt              banc V2 — PSI headless + résolution (EP enregistrés à la main)
    ├── BancV3.kt              banc V3 — référence ecj (diet/complet/compile)
    ├── ExplorateurV2.kt       exploration réflexive de l'API 232.4652 (écriture du banc)
    ├── GenerateurCorpus.kt    générateur de corpus déterministe
    ├── ScanArt.kt             scanner statique de compatibilité ART (constant pools)
    ├── SondeJdk.kt            sonde : câblage du JDK 21 dans le FileSystem batch ecj
    └── telecharger-piles.sh   téléchargement reproductible des piles
```

`build/`, `build-v2/`, `build-v3/`, `build-scan/`, `build-sonde/` : sorties de compilation (ignorées).
