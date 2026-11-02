# 02 — Spike de mesure (Phase 2) : PSI tient-il sur ART, et à quel prix ?

> Étude « moteur Java PSI » — branche `etude/moteur-java` du dépôt `jjoblab/code-lsp`.
> Module de mesure isolé `spike/` (aucune implémentation de production, §0 du prompt). Code neuf en Kotlin (R2). Mesures du **2026-10-28**, reproductibles via `spike/README.md`.

## 1. Question et protocole

**Question (§6 du prompt)** : PSI, ou la partie utile, tient-il sur ART avec une empreinte acceptable, et à quel prix d'intégration ?

**Sans appareil Android disponible**, le protocole appliqué est celui que le prompt autorise explicitement (§6) : (a) analyse statique de compatibilité ART des jars candidats (scanner maison sur les constant pools, §6), (b) mesures sur **JVM de bureau** présentées comme **limites basses**, (c) point d'entrée de benchmark appareil livré au propriétaire (`spike/appareil/`). **Aucune mesure appareil n'est prétendue.**

**Variantes mesurées** :

| Voie | Pile | Ce qu'elle teste |
|---|---|---|
| V1 | `org.jetbrains:java-syntax 0.3.381` (+ 3 modules KMP) — 798 Ko | La voie « réutiliser le parseur publié » (D3) |
| V2 | `com.jetbrains.intellij.{platform,java}:232.4652` — pile minimale 23 jars, 37,4 Mo | La voie « embarquer le PSI headless avec résolution native » (D1) |
| V3 | `org.eclipse.jdt:ecj 3.40.0` **non patché** — 3,2 Mo | La référence : le moteur du statu quo, re-mesuré **le même jour, même machine, mêmes corpus** (exigence §6) |

## 2. Environnement et corpus (R5)

- **JVM** : OpenJDK 21.0.12.1+1-1-deb13u1-Debian (amd64), `-Xmx2g`, hôte du bac à sable, le 2026-10-28 entre 10:58 et 12:40. V1, V2 et V3 ont été (re)mesurés **dans la même fenêtre** (une première passe V1 antérieure de la même journée donnait des p50 cohérents : 0,468–2,52 ms — la variabilité inter-passes est de l'ordre de ×1,5 sur les p95, cf. §9).
- **Corpus** (mêmes fichiers pour les trois voies) :
  - `corpus/Fichier500.java` — synthétique déterministe (générateur `scripts/GenerateurCorpus.kt`, graine 42) : 498 lignes, 16 258 caractères, 5 653 jetons, 26 méthodes, 1 classe ;
  - `PsiMethodReferenceExpressionImpl.java` (494 l., 18 701 car., 4 264 jetons) et `FileUtil.java` (1 564 l., 57 793 car., 14 513 jetons), extraits du terrain d'étude `intellij-community` @ `865067b0`.
- **Corpus de jars (20/73/250 de `LSP_JAVA.md` §4)** : non rejoués dans ce spike — ils mesurent le **moteur assemblé** (complétion, index) ; le spike mesure les briques élémentaires (parse/résolution). Les chiffres moteur font référence dans `LSP_JAVA.md` §4 (bureau : p50/p95 de frappe 30/64 ms à 20 jars → 128/150 ms à 250 jars). **À re-mesurer sur appareil** (§9).
- **Commandes exactes** : toutes dans `spike/README.md` (téléchargement des piles, génération du corpus, compilation, exécution) ; sorties brutes consignées dans `spike/resultats/`.

## 3. V1 — parseur `java-syntax` seul

**Méthode** (`scripts/BancV1.kt`) : lexing + construction de l'arbre de production complet (`JavaSyntaxDefinition.parse` sur `SyntaxTreeBuilder`) ; démarrage à froid = premier parse (chargement des classes inclus) ; 200 itérations après 50 d'échauffement ; empreinte de l'arbre = delta de tas après double GC avec 100 arbres retenus (approximation documentée).

**Résultats bruts** (`resultats/v1-reels.txt`, `resultats/v1-synthetique-500.txt`) :

| Fichier | Froid | p50 | p95 | min/max | Arbre ≈ | Tas repos |
|---|---|---|---|---|---|---|
| Fichier500 (498 l.) | 116,4 ms | **0,653 ms** | 3,478 ms | 0,4/25 ms | 82 Ko | 10 Mo |
| PsiMethodReference… (494 l.) | 121,9 ms | **0,616 ms** | 4,805 ms | 0,5/35 ms | 108 Ko | 12 Mo |
| FileUtil (1 564 l.) | 179,1 ms | **3,986 ms** | 23,999 ms | 2,5/58 ms | 393 Ko | 40 Mo |

**Lecture** : le parse complet d'un fichier de 500 lignes est **sub-milliseconde en p50** sur JVM de bureau ; l'arbre retenu pèse ~82–393 Ko (à rapporter aux 2,5 Mo/jar du snapshot index du statu quo — W2). Le tas « au repos » de FileUtil (40 Mo) reflète les 100 arbres retenus pour la mesure mémoire, pas un besoin du moteur. **Sur ART (facteur 3–5× estimé), p50 ≈ 2–3 ms et p95 ≈ 10–17 ms resteraient dans le budget de frappe** (< 100 ms §4) — estimation, à confirmer sur appareil.

## 4. V2 — PSI headless complet (artefacts 232.4652)

**Méthode** (`scripts/BancV2.kt`) : amorçage `JavaCoreApplicationEnvironment` → `JavaCoreProjectEnvironment` ; parse PSI complet par `PsiFileFactory.createFileFromText` ; résolution `findClass` (guava ajouté au classpath par `addJarToClassPath`, via `CoreJavaFileManager`, sans index) et résolution de référence intra-fichier (`resolve()` du type d'un champ, snippet `Essai.java` de 4 lignes).

### 4.1 La « taxe d'assemblage » — constat central du spike

L'amorçage headless publié **ne fonctionne pas tel quel**. Deux échecs documentés avant le premier résultat complet :

1. **`IllegalArgumentException: Missing extension point: com.intellij.javaModuleSystem`** à `PsiResolveHelperImpl.isAccessible` (résolution de référence) — l'artefact 232.4652 exige des EP que son propre amorçage headless n'enregistre pas. Corrigé par enregistrement manuel de 3 EP : `com.intellij.lang.psiAugmentProvider`, `com.intellij.javaModuleSystem`, `com.intellij.java.elementFinder` (ce dernier nécessite aussi un **adaptateur maison** `AdaptateurFinder` de 36 lignes exposant le service `JavaFileManager` comme `PsiElementFinder`).
2. **`Could not find installation home path`** (`PathManager.getHomePath`) — la pile exige un « home » d'installation contenant `bin/idea.properties` : contourné par un home factice (`spike/app-home/`, fichier vide) passé par `-Didea.home.path`.

À HEAD ( `865067b0` ) une partie de ces EP est enregistrée programmatiquement par `JavaCoreApplicationEnvironment` ( `java/java-indexing-serializers/src/com/intellij/core/JavaCoreApplicationEnvironment.java:117-121,160-161` ) — mais pas `javaModuleSystem`, devenu le service `JavaModuleGraphHelper` ( :134 ). **Conclusion : la frontière « headless » publiée bouge entre versions ; chaque montée de version de la pile = re-diagnostic de cette taxe.** Ce fait alimente directement l'ADR 0005 (packaging) et pèse contre l'embarquement.

### 4.2 Résultats bruts

Pile minimale vérifiée (23 jars, 37,4 Mo — `java-impl.jar` 22,8 Mo et `java-analysis-impl.jar` 15,1 Mo retirés après vérification que parse/findClass/resolve fonctionnent sans eux ; `resultats/v2-pile-minimale.txt`) :

| Mesure | Fichier500 (498 l.) |
|---|---|
| Environnement app froid | 200–205 ms |
| Environnement projet froid | 48–67 ms |
| **Parse PSI p50 / p95** | **4,75–4,89 / 27,1–29,1 ms** |
| findClass (guava) froid | 96–141 ms |
| findClass p50 / p95 | 0,0 / 0,02 ms |
| resolve() de référence p50 / p95 | 0,62–1,54 / 39–79 ms |
| Tas (env + parse) | 6 Mo |

Sur fichiers réels (pile complète, `resultats/v2-reels.txt`) : parse p50 3,7 ms (PsiMethodRef, 494 l.) et 11,9 ms (FileUtil, 1 564 l.), p95 30–37 ms ; resolve() p50 0,7–1,4 ms, p95 47–62 ms.

**Lecture** : le parse PSI complet est **~7× plus lent que V1** et **~5× plus lent que le parse complet ecj** (§5) sur le même fichier — le prix des objets PSI, des évènements et du câblage plateforme. Les p95 (27–37 ms parse, 39–79 ms resolve) sont dominés par des pauses GC (max observés 33–47 ms) : sur ART ( tas plus contraint, GC différents ) ces queues de distribution se dégraderaient. `findClass` froid à ~100 ms pour un seul jar confirme D1 ( `CoreJavaFileManager` parcourt le VFS sans index ) : ce chemin **exige l'index** pour être utilisable — l'embarquement de PSI sans l'index IntelliJ (incompatible, D7) ni CIDX n'a pas de complétion de types viable.

## 5. V3 — référence ecj 3.40.0 (non patché), même jour/mêmes corpus

**Méthode** (`scripts/BancV3.kt`) : parse **diet** (en-têtes, équivalent stub building) et **complet** (corps) par le `Parser` interne ecj ; **compilation complète** (parse + résolution + codegen en mémoire) du snippet `Essai.java` (4 lignes, guava au classpath) en deux variantes : classpath neuf à chaque essai, et `FileSystem` réutilisé (état stable type moteur en cache — le statu quo cache son `EcjCompilerEnvironment`). Résolution **vérifiée** : 0 erreur de compilation.

**Fait d'assemblage documenté** (`scripts/SondeJdk.kt`, 5 configurations testées) : le `FileSystem` batch d'ecj ne câble **pas** le JDK à partir du home nu — il faut passer `<java.home>/lib/jrt-fs.jar` comme entrée de classpath ( sinon `java.lang.Object cannot be resolved` ). Le moteur du statu quo résout ceci avec son propre `INameEnvironment` sur CIDX.

**Résultats bruts** (`resultats/v3-reels.txt`) :

| Mesure | Fichier500 | PsiMethodRef (494 l.) | FileUtil (1 564 l.) |
|---|---|---|---|
| Froid (1er diet parse) | 238 ms | 313 ms | 310 ms |
| Parse diet p50 / p95 | **0,315 / 1,054 ms** | 0,514 / 1,186 ms | 1,29 / 2,12 ms |
| Parse complet p50 / p95 | **0,923 / 1,992 ms** | 0,638 / 2,396 ms | 1,517 / 9,063 ms |
| Compile Essai froid | 348 ms | 379 ms | 329 ms |
| Compile Essai p50 / p95 (classpath neuf) | 6,7 / 35 ms | 10,9 / 45 ms | 10,1 / 37 ms |
| Compile Essai p50 / p95 (env réutilisé) | **5,7 / 30 ms** | 3,7 / 31 ms | 5,4 / 28 ms |
| Tas au repos | 6 Mo | 6 Mo | 6 Mo |

**Lecture** : ecj reste le **plus rapide en parse** (diet p50 0,32–1,3 ms, complet p50 0,6–1,5 ms) et le plus économe en code (3,2 Mo). La compilation résolue complète d'un fichier de 4 lignes coûte ~4–7 ms p50 à environnement chaud — cohérent avec les p50 de frappe 30/64 ms du moteur assemblé ( `LSP_JAVA.md` §4, qui inclut diagnostics + complétion).

## 6. Analyse statique de compatibilité ART (§6.a)

**Scanner** : `scripts/ScanArt.kt` (Kotlin, neuf) — analyse des constant pools (JVMS §4) de chaque `.class` : références de classes (CONSTANT_Class), références de méthodes ciblées (Methodref + NameAndType), méthodes déclarées, versions de format. Écrit à partir de la JVMS et de la documentation Android (android.jar API 34). **Limites** : bytecode non parcouru (réflexion et `ServiceLoader` dynamiques invisibles) ; les méthodes déclarées sont une **borne basse** du compte de méthodes dex (le dexage D8 compte aussi les référencées — à exécuter en CI, §6.a du prompt).

**Résultats** (sorties complètes : `resultats/scan-art-{v1,v2-minimale,v2,v3-ecj}.txt`) :

| Pile | Classes | Méthodes déclarées | Motifs sensibles distincts | Détail |
|---|---|---|---|---|
| **V1** (4 jars) | 411 | **2 965** | **0** | formats JVM 8/17 — aucune API absente d'ART référencée |
| **V2 minimale** (23 jars) | 12 693 | **124 513** | **108** | javax.swing/java.awt dans core (Icon, SwingUtilities, JComponent, JFrame…), `sun.misc.Unsafe` (13×, conteneurs concurrents), `com.sun.jna` (util), `java.lang.management` (LowMemoryWatcher), MethodHandles ~1 200 réf., MappedByteBuffer, ServiceLoader |
| **V2 complète testée** (25 jars) | 18 951 | 168 448 | 182 | idem + inspections/complétion |
| **V3 ecj** | 801 | 11 990 | **6** | `MethodHandles` (196×, présent API 26+), `ServiceLoader` (2×), **`Runtime$Version` (1× — shim du patcheur)**, **`InputStream.readAllBytes` (3×) + `readNBytes` (1×) — shims (API 33+)** |

**Faits saillants** :
- **V1 : pile ART-compatible en l'état** (0 référence sensible, JVM 8 majoritairement) — la voie la plus propre du point de vue du packaging.
- **V3 : 6 motifs, dont 4 absents d'ART** — exactement le périmètre du patcheur `ecj-art` du statu quo ( `Runtime$Version`, `readAllBytes` ; + `readNBytes` non listé par le prompt — **à ajouter à la liste des shims** ). **Écart constaté** : `StackWalker`, cité par le prompt §2 comme réécrit par le patcheur, est **absent** des références statiques ET des chaînes d'ecj 3.40.0 ( vérifié par balayage des constantes Utf8 ) — la passe StackWalker du patcheur est soit défensive, soit vestigiale à 3.40 ( à consigner dans `ALERTES.md` et l'ADR 0018 ).
- **V2 : incompatible ART en l'état** — swing/awt/Unsafe/management/jna exigeraient des shims massifs ( une centaine de classes stub ) **et** `sun.misc.Unsafe` + `MethodHandles.Lookup` usages fins ( `Lookup.findVirtual` etc. ) non vérifiables statiquement. **VarHandle : 0 référence dans 232.4652** ( l'usage est apparu plus tard — à HEAD, `TreeElement`/`CompositeElement` l'utilisent, D1 : un embarquement sur des artefacts récents exigerait minSdk 33+ ).
- **Nombre de shims requis** ( réponse à la « liste des shims nécessaires » §6 ) : V1 = **0** ; V3 = **4** ( `Runtime$Version`, `readAllBytes`, `readNBytes`, + `StackWalker` si le patcheur actuel l'exige pour des versions antérieures — à trancher en ADR 0018 ) ; V2 = **de l'ordre de la centaine** ( estimation par comptage de motifs, non exhaustive : réflexion invisible au scanner ).

## 7. Empreinte (taille d'APK et méthodes dex — §4 « à proposer »)

| Voie | Jars (avant dex) | Méthodes déclarées (borne basse dex) | Lecture |
|---|---|---|---|
| V1 | **798 Ko** | 2 965 | ~0,8 Mo d'APK en plus, dizaines de milliers de méthodes dex après références — **compatible avec un budget APK raisonnable** |
| V2 minimale | 37,4 Mo | 124 513 | dex multi-millions de méthodes potentiel (guava+fastutil seules = 4 300 classes) ; dépasse tout budget raisonnable pour un moteur embarqué ; à confirmer par dexage D8 en CI |
| V3 | 3,2 Mo | 11 990 | le statu quo actuel ( déjà dexé et patché dans l'APK ) |

**Cible proposée** ( critère §4 « empreinte : à proposer et justifier », à valider par le propriétaire ) : taille d'APK ajoutée **< 5 Mo**, méthodes dex ajoutées **< 30 000**, démarrage à froid du moteur **< 1,5 s** ( ouverture index chaud, cible existante ). Justification : l'APK actuel de CodeIDE embarque déjà ecj+CIDX dans cet ordre de grandeur ; V1 ( 798 Ko / 2 965 ) y entre facilement ; V2 ( 37,4 Mo / 124 513 ) ne peut pas y entrer. **Le dexage D8 des piles candidates reste à exécuter en CI** ( pas d'SDK Android dans le bac à sable — R4/R5 : non mesuré localement, §6.a du prompt le place en CI ).

## 8. Tableau comparatif (chiffres mesurés ce jour)

| Critère (§4/§6) | V1 java-syntax | V2 PSI headless | V3 ecj (statu quo) |
|---|---|---|---|
| Parse 500 lignes p50 / p95 | **0,65 / 3,5 ms** | 4,75–4,89 / 27–29 ms | 0,92 / 2,0 ms (complet) |
| Parse 1 564 lignes p50 | 3,99 ms | 11,9 ms | 1,52 ms |
| Résolution (froid / chaud p50) | n/a (pas de résolution) | findClass 96–141 ms / 0 ms ; resolve 0,6–1,5 ms | compile résolue 329–379 ms / 3,7–5,7 ms |
| Démarrage à froid | 116–179 ms | **205+48 ms (env) + taxe EP/home** | 238–313 ms |
| Arbre / tas | 82–393 Ko / 6–40 Mo | tas 6 Mo (env+parse) | tas 6 Mo |
| Jars / méthodes déclarées | **798 Ko / 2 965** | 37,4 Mo / 124 513 | 3,2 Mo / 11 990 |
| Références sensibles ART | **0** | 108 motifs ( swing/awt/Unsafe/mgmt/jna ) | 6 motifs ( 4 shims connus ) |
| Taxe d'intégration | nulle observée | **3 EP manuels + adaptateur + home factice + pile épurée à la main** | sonde jrt-fs (câblage du JDK) |
| Fidélité compilateur | n/a | checkers réimplémentés (D9) | **native** (D9) |

## 9. Limites et à mesurer sur appareil

1. **Tout est limite basse JVM** : facteur ART 3–5× **estimé** ( `LSP_JAVA.md` §4 ) ; aucune extrapolation automatique appliquée aux tableaux.
2. Variabilité inter-passes mesurée : p95 V1/FileUtil 7,2 ms (passe 10:58) → 24 ms (passe 12:3x) sur le même hôte (bruit de charge GC/CPU) ; les p50 sont stables à ±10 % près. Les p95 de ce rapport sont ceux de la passe commune V1/V2/V3.
3. La complétion (membres/types) et le tas « après indexation » ne sont pas mesurables dans ce spike (V1 sans sémantique, V2 sans index) — renvoi aux mesures moteur existantes + à re-mesurer sur appareil avec les corpus 20/73/250 jars ( `LSP_JAVA.md` §4 ).
4. **Dexage D8 + comptage de méthodes dex + APK réel : en CI** (§6.a), pas en local.
5. GC ART ( éviction des `SoftReference`, coût des `ReferenceQueue` — D12 ) : **à mesurer sur appareil**.
6. Le point d'entrée appareil ( `spike/appareil/banc-appareil.sh` + `GABARIT-RESULTATS.md` ) fournit la procédure et le gabarit ; **aucun résultat appareil n'existe à ce jour**.
7. La mesure d'arbre V1 ( delta de tas, 100 arbres ) est une approximation documentée ( R5 ) : ±10–20 % attendus.

## 10. Mesuré / Estimé / Non vérifié

- **Mesuré** (commandes dans `spike/README.md`, sorties brutes dans `spike/resultats/`) : tous les tableaux des §3–§8 ( V1/V2/V3 : froids, p50/p95, tas, arbres ; scans ART : classes, méthodes, motifs ; tailles de piles ; taxe d'assemblage V2 : les 2 échecs et leurs correctifs ; sonde jrt-fs : 5 configurations ).
- **Estimé** : projections ART ( ×3–5 ) ; « ~centaine de shims » pour V2 ( comptage de motifs, réflexion invisible ) ; méthodes dex ( borne basse = déclarées ).
- **Non vérifié / à faire sur appareil ou en CI** : dexage D8 et APK réel ; comportement GC ART ; latences V1/V2/V3 sur ART ; complétion et tas après indexation avec les corpus 20/73/250 ; conditions exactes de publication des artefacts `com.jetbrains.intellij.platform` ( ADR 0017 ) ; statut de la passe `StackWalker` du patcheur à ecj 3.40 ( ALERTES ).
