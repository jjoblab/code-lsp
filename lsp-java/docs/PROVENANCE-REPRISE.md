# PROVENANCE-REPRISE — Manifeste de reprise sélective

> Mission « étude moteur Java PSI » (dépôt `jjoblab/code-lsp`, branche `etude/moteur-java`).
> **État : VIDE par construction.** Règle R9.1 du prompt de mission : *rien n'est copié depuis un ancien dépôt tant que le propriétaire n'a pas tranché la licence du nouveau dépôt (ADR 0001)*. Ce manifeste est le registre où TOUTE reprise future devra être inscrite avant d'avoir lieu (R9.2 : dépôt d'origine, chemin, commit, auteur déclaré, licence, statut de provenance, décision, justification — aucune reprise « en bloc »).
> **Décision ADR 0001 rendue le 2026-10-28** (Accepté modifié — option 2 : **réécriture intégrale du moteur**, licence Apache-2.0) : l'exclusion du moteur vendored devient **définitive** (aucune levée) ; les reprises candidates du §3 demeurent conditionnées à l'inscription préalable au manifeste (R9.2) et, pour `:lsp:model`, à l'action P1 (LICENSE du protocole tooling).
> **Vague 1b (28/10/2026) : premières reprises EFFECTUÉES** — `:model` (11 fichiers) et `:index` (33 fichiers) depuis codeide-lsp @ cb809e14, inscrites fichier par fichier au §7 ; l'alternative §4 (réécriture locale des types du protocole consommés) est appliquée pour débloquer `:model` sans attendre P1. Le manifeste n'est plus vide.
> **Vague 1c (28/10/2026) : réécriture du patcheur EFFECTUÉE** — `:ecj-art` est né en Kotlin clean-room (voie ① du §2 — aucune ligne de l'ancien patcheur n'a été lue ni copiée) : module neuf, HORS registre de reprise par construction. Consignée au journal (§6).
>
> Anciens dépôts (lecture seule) : `jjoblab/codeide-lsp` @ `cb809e1412fec429c9672da087244b3bd3176545` (v1.16.0), `jjoblab/codeide-tooling` @ `b60f7f0`, `jjoblab/CodeIDE` @ `54e99c6` (v0.202.0), `jjoblab/code-editor` @ `066b8c0`.
> Sources tierces autorisées consultées durant l'étude : `JetBrains/intellij-community` @ `865067b0a7f1a73ee0fa8bce4f042013c8ac334f` (Apache-2.0, « JetBrains Open-Source Build Terms v1.3 » du 15/06/2026) — **consultation uniquement, aucun code copié** (R6.4) ; artefacts binaires `org.jetbrains:java-syntax` 0.3.381 et dépendants (intellij-dependencies, cache-redirector) — **téléchargés pour mesure dans `spike/`, jamais commités dans ce dépôt**.

## 1. Statuts utilisés

| Statut | Sens |
|---|---|
| **propre** | Origine entièrement maîtrisée (écrit à la main attesté, ou tierce sous licence permissive avec attributions conformes) |
| **à vérifier** | Doute documenté — exclu en attendant (R9.6) |
| **exclu** | Frappé d'exclusion par défaut (R9.3) ou hors périmètre (R8) |

## 2. Registre des exclusions par défaut (R9.3 — verrouillées jusqu'à décision écrite du propriétaire)

| Élément | Dépôt/chemin | Motif de l'exclusion | Voies de levée (ADR 0110) |
|---|---|---|---|
| Moteur vendored `lsp/java` (≈25 839 l. Java main, 53 fichiers + tests) | codeide-lsp:lsp/java/src | R9.3 : « le moteur vendored lsp/java dans son ensemble » ; ADR 0110 : niveaux A/B audités, dérivation de référence tierce GPL documentée pour A (réécrit v0.94.0) et B (attesté original) | ① réécriture cloisonnée depuis spécifications écrites sans lire le code tiers ② autorisation écrite de l'auteur tiers ③ licence compatible assumée. **→ Voies CLOSES le 28/10/2026 : décision ADR 0001 (option 2 — réécriture intégrale) — aucune levée, exclusion DÉFINITIVE pour ce dépôt** ; le vendored reste dans `codeide-lsp` comme oracle de parité et repli, jamais copié |
| Patcheur `lsp/ecj-art` (2 f / 468 l.) | codeide-lsp:lsp/ecj-art | R9.3 : ADR 0110 indique l'approche « reprise d'un projet tiers » (passes `RelocateTypesInJar.kt`/`EclipseStreamArtPass.kt` du build-logic tiers) | Mêmes voies ; recommandation de l'étude : **réécrire** (surface réduite, dicée par l'API ASM et la javadoc JDK/JEP 322 — procédé déjà appliqué avec succès au niveau A en v0.94.0). **→ Voie ① EXÉCUTÉE le 28/10/2026 (vague 1c) : RÉÉCRIT en Kotlin clean-room (`ecj-art/`, 12 fichiers neufs) — aucune reprise, la question est close pour ce dépôt** |
| Fichiers niveau A (3 shims `compat/`) | codeide-lsp:lsp/java/src/main/java/jo/lspjava/ecj/compat/ | R9.3 : « tout fichier ou module que l'ADR 0110 classe en niveau A ou B » | Déjà réécrits depuis la spec JDK (v0.94.0) — le statut « propre » peut être reconnu par décision ADR 0001 sur la base de cette exécution |
| Fichiers niveau B (~31 fichiers « portage » déclarés) | codeide-lsp:lsp/java (divers) | R9.3 (idem) | Attestation de paternité du propriétaire (16/10, reconfirmée 19/10/2026) — à acter pour ce dépôt en ADR 0001 |
| Fichiers homonymes du projet tiers | historique : `JdtCodeFolder`, `JdtSemanticHighlighter`, `JdtSourceUnit`, `RuntimeVersion`, `StackWalker`, `InputStreamCompat` | R9.3 : « les fichiers dont il [l'ADR 0110] signale un nom identique à celui d'un projet tiers » | Renommage intégral `Jdt*→Ecj*` exécuté (v0.95.0) — le renommage seul ne suffit pas selon l'ADR 0110 ; traitement du contenu déjà acté |
| `lsp/kotlin`, `lsp/xml`, hôte tri-langue | codeide-lsp:lsp/{kotlin,xml,host} | R8 : moteur Java uniquement | Hors périmètre — pas une exclusion de provenance |

## 3. Registre des candidats à reprise (aucun ne sera copié avant l'ADR 0001)

| Élément | Dépôt/chemin @ commit | Taille | Auteur déclaré | Licence amont | Statut | Décision proposée | Justification |
|---|---|---|---|---|---|---|---|
| `:lsp:index` (index CIDX) | codeide-lsp:lsp/index/src @ cb809e14 | 33 f / 6 472 l. main | jjoblab (couche neuve, post-ADR 0110) | dépôt propriétaire | à vérifier (contrôle R9.4 à mener à la copie) | **reprendre tel quel** | Socle conservé par l'étude D7 (bat le design IntelliJ sur préfixe/+1 dép/mémoire/corruption) — **REPRIS le 28/10/2026 (vague 1b), voir §7** |
| `:lsp:model` | codeide-lsp:lsp/model/src @ cb809e14 | 11 f / 2 160 l. | jjoblab (idem) | idem + dépend protocole tooling **sans LICENSE** | à vérifier | **reprendre** | Modèle immuable consommé par l'index et le moteur ; condition : éclairer le protocole (§4) — **REPRIS le 28/10/2026 (vague 1b) via l'alternative §4 (types du protocole REWRITS en local), voir §7** |
| `:lsp:testing` | codeide-lsp:lsp/testing/src @ cb809e14 | 4 f / 1 464 l. | jjoblab | idem | à vérifier | **muter en réécriture** | Parité moteur↔index, zéro dépendance externe publiée — **MUTÉE le 29/10/2026 (étage N1)** : la suite vendored est couplée aux membres package-private du moteur vendored (`jo.lspjava.*`, split package de test) et ne compile pas contre le moteur neuf ; la reprise « telle quelle » est IMPOSSIBLE. Décision : réécriture Kotlin NEUVE des mêmes buts d'intégration (parité avec/sans index, autorité du localisateur à PRÊT, réouverture chaude, transition observée) contre l'API PUBLIQUE du moteur — `testing/src/**` étage N1, voie ① R9.3 (aucun fichier repris). Le contrat comportemental de référence reste le cahier (fiches 032/034/024) ; les 4 fichiers vendored n'ont PAS été relus pour la réécriture (seul le README du module, matière de spécification, l'a été) |
| Jeux de données / golden files des tests `lsp/java` | codeide-lsp:lsp/java/src/test (entrées-sorties attendues) | à inventorier à la copie | jjoblab | idem | à vérifier | **reprendre comme données** | Conserver la valeur des 449 tests sans reprendre le code (00b §4) |
| Squelette Gradle / conventions qualité | codeide-lsp:build-logic | ~7 plugins | jjoblab | idem | à vérifier | **s'inspirer** (réécrire le minimum) | R3 n'exige que le squelette du spike |

## 4. Dépendances d'artefacts du nouveau dépôt (aucune copie de code — chaîne à tenir à jour)

| Artefact | Version | Licence | Rôle prévu | Points d'attention |
|---|---|---|---|---|
| `org.eclipse.jdt:org.eclipse.jdt.core` (+ graphe) | 3.40.0 | EPL-2.0 | Compilateur/moteur si voie statu quo ou hybride | Code MODIFIÉ (patch ART) et REDISTRIBUTÉ → obligations EPL-2.0 (sources amont disponibles, description des modifications) — **patcheur RÉÉCRIT en Kotlin clean-room le 28/10/2026 (vague 1c, §2/§6) ; registre des modifications embarqué dans le jar sortant (META-INF/ECJ-ART-MODIFICATIONS.txt)** |
| `org.eclipse.lsp4j:lsp4j` | 0.21.2 | EPL-2.0 | Protocole LSP (façade) | — |
| `com.github.jjoblab.codeide-tooling:protocol` | 1.0.0 | **aucun fichier LICENSE sur le dépôt à ce jour** | Types `ClasspathResult` (modèle) | **Action propriétaire** : ajouter une LICENSE au dépôt frère, sinon réécrire les ~10 types consommés — **alternative APPLIQUÉE le 28/10/2026 (vague 1b)** : les 4 types consommés sont réécrits en code neuf local (`jo.codeide.lsp.model.protocole`, clean-room depuis la surface observable — §7) ; retour à l'artefact officiel à l'étude quand P1 sera résolu |
| `org.jetbrains:java-syntax` (+ syntax-api, lang-syntax, ij-multiplatform-util) | 0.3.381 | Apache-2.0 (intellij-dependencies) | Parseur Java (voie V1 du spike) | Conditions d'utilisation des artefacts binaires JetBrains à consigner en ADR 0017 ; dépôt de publication = packages.jetbrains.team (ij/intellij-dependencies), accès via cache-redirector |
| `org.ow2.asm:asm` (+ commons) | 9.7 | BSD-3-Clause | Patchage build-time (réécriture d'ecj-art) | — |

## 5. Attributions d'origine (R9.5 — à conserver exactes lors de toute reprise)

- Grammaire Kotlin vendorée (si un jour reprise) : `JetBrains/kotlin` v2.3.0 @ `f95cb2f7693a15966fd8fd9b1d78d440545e7b66`, Apache-2.0, `LICENSE.txt`/`COPYRIGHT.txt` présents dans `lsp/kotlin/src/main/vendor/` — hors périmètre R8, citée pour mémoire.
- En-têtes `com.siyeh.ig.*` (si un jour reprise d'inspections, interdite sans décision) : « Copyright 2003-2015 Dave Griffith, Bas Leijdekkers », Apache-2.0 (constaté D9 dans intellij-community).
- ecj/JDT : Eclipse Public License 2.0 — mention des modifications ART obligatoire (déjà documentée dans `THIRD_PARTY_NOTICES.md` de l'ancien dépôt, note 1).
- **Aucune attribution ne sera supprimée** lors d'une reprise ; le nouveau dépôt ne sert pas à effacer la trace d'une origine (R9.5).

## 6. Journal du manifeste

| Date | Événement |
|---|---|
| 2026-10-28 | Création du manifeste (Phase 0). Registres remplis : 6 exclusions R9.3, 5 candidats, 5 dépendances d'artefacts. Aucune copie effectuée. |
| 2026-10-28 | Décision propriétaire **ADR 0001 accepté modifié — option 2 : réécriture intégrale du moteur** (« partir sur de nouvelles bases plus solides »). Exclusion R9.3 du vendored `lsp/java` rendue **définitive** (aucune levée, aucune copie, même partielle) ; le vendored reste l'oracle de parité et le repli dans `codeide-lsp`. Licence du dépôt : **Apache-2.0**. Couches candidates (§3) maintenues — copies toujours subordonnées à l'inscription au manifeste (R9.2) et à l'action P1 pour `:lsp:model`. Aucune copie effectuée à ce jour. |
| 2026-10-29 | **Étage N1 — :java livré en clean-room intégral ; :lsp:testing MUTÉE en réécriture** : le module `:java` (moteur, 21 fichiers Kotlin neufs) est écrit depuis le cahier de spécifications uniquement (voie ① R9.3 — AUCUN fichier repris, rien à inscrire). Le contrôle `verifier-provenance.sh` restreint son périmètre aux modules accueillant des reprises (`index/`, `model/`) : `java/` et `testing/` sont des réécritures clean-room au même titre que `ecj-art/` (vague 1c). La suite d'intégration `:testing` est réécrite en Kotlin neuf (adaptateur `IndexNoms` + 4 tests) — la reprise littérale des 4 fichiers vendored était impossible (couplage API vendored) ; motif consigné §3. |
| 2026-10-28 | **Vague 1b — premières reprises EFFECTUÉES** : `:model` (11 fichiers) et `:index` (33 fichiers) copiés depuis codeide-lsp @ cb809e14, inscrits fichier par fichier au §7 (contrôle R9.4 mené à la copie : statut propre, aucune attribution tierce, aucune effacée). Adaptations minimales consignées : redirection des imports du protocole vers les 4 types REWRITS en code neuf (`jo.codeide.lsp.model.protocole` — alternative §4 appliquée faute de P1) ; fichiers de build réécrits pour le squelette code-lsp (Kotlin 2.2.10, explicitApi, JUnit 4, JVM 17). `:lsp:testing` REPORTÉ à l'étage noyau du moteur (dépend du moteur à réécrire). Vérification : 122 tests repris verts (38 modèle + 84 index), banc compilé. |
| 2026-10-28 | **Vague 1c — patcheur `ecj-art` RÉÉCRIT en Kotlin clean-room** (voie ① du §2, recommandation de l'étude suivie) : module `ecj-art/` neuf (12 fichiers, 1 521 lignes Kotlin) — scanner de constant pools (`ScanneurArt`), patcheur ASM (`PatcheurJar`), shims embarqués (`compat/VersionJava` JEP 322, `compat/LectureFlux` javadoc JDK), liste de shims versionnée (`liste-shims-3.40.0.txt`, ADR 0018). Aucun fichier de l'ancien `lsp/ecj-art` lu ni copié (l'ancien patcheur de codeide-lsp n'était accessible qu'en lecture seule de toute façon, et ne l'a pas été). Garanties : motifs entrants = liste versionnée exacte ; jar sortant re-scanné **vierge de motif bloquant** ; registre EPL-2.0 des modifications embarqué ; signatures amont retirées. 36 tests verts (dont compilation de fumée avec le jar ecj patché). |
| 2026-10-29 | **Étage T — banc de parité croisée livré : le vendored EXÉCUTÉ comme oracle, aucun code repris**. `scripts/preparer-oracle.sh` clone `codeide-lsp` @ `cb809e1412fec429c9672da087244b3bd3176545` (et le frère composite `codeide-tooling` @ `b60f7f0dc6c2ce75edd7febc44ff80e0db39f2b1`), construit `:lsp:java:jar` (JVM pur) et pose les jars dans `.oracle/` — **jamais committés** (R6.4, hors dépôt). Le pilote (`java/src/piloteOracle/`) est du Kotlin neuf qui ne touche que les points d'entrée publics documentés du vendored (`EcjCompilerEngine`/`EcjCompletionEngine`/`jo.lspjava.api` — la surface même des tests du vendored, source du cahier) ; il est compilé contre des **stubs de signatures** (`java/src/bancStubs/` — code neuf, alarme explicite si un stub atteint l'exécution) et s'exécute en sous-processus JVM isolé. La clean-room est tenue : `lsp/java/src/main` du vendored n'est pas lu pour la spécification (le cahier et les tests en sont la source, comme depuis la vague 1a) ; l'usage est strictement « exécuter et comparer les sorties » (AGENTS.md). |

## 7. Registre des fichiers repris — vague 1b (28/10/2026) : `:model` + `:index`

> Reprise contrôlée exécutée le 28/10/2026 (vague 1b du plan de migration ADR
> 0001). **Fait commun à toutes les entrées ci-dessous** (R9.2) : dépôt
> d'origine `jjoblab/codeide-lsp` @ `cb809e1412fec429c9672da087244b3bd3176545`
> (v1.16.0, lecture seule) ; auteur déclaré **jjoblab** (couche neuve Kotlin
> post-ADR 0110, attesté original) ; licence amont **dépôt propriétaire** ;
> statut de provenance **propre** (contrôle R9.4 mené à la copie : aucun
> en-tête tiers, aucune attribution effacée — les fichiers d'origine ne
> portent pas d'en-tête de licence) ; décision ADR 0001 (reprises autorisées
> hors moteur) + inscription préalable au présent registre. Chaque fichier est
> inscrit individuellement — jamais « en bloc ».

### Adaptations apportées à la copie (consignées — seule dérive par rapport à « tel quel »)

1. **Redirection du protocole** (`:model` uniquement) : les imports
   `jo.codeide.tooling.protocol.*` sont redirigés vers
   `jo.codeide.lsp.model.protocole.*` (4 types REWRITS en code neuf —
   alternative §4) dans 5 fichiers : `ConvertisseurModeleLsp.kt`,
   `ProjetLsp.kt` (référence KDoc), `FixturesClasspath.kt`,
   `AssociationFichierTest.kt`, `ConvertisseurModeleLspTest.kt`.
   Aucune autre modification de code — `:index` est repris TEL QUEL
   (aucun import du protocole).
2. **Fichiers de build réécrits** pour le squelette code-lsp (non repris du
   build-logic) : conventions d'origine portées (explicitApi, JUnit 4, cible
   JVM 17, tas de test borné 640 m) ; Kotlin 2.2.10 (aligné sur l'origine) ;
   plugin maven-publish/kover NON portés (publication et seuils de couverture
   = décisions de vague ultérieure).

Vérification à la reprise : build complet vert ; **122 tests repris verts**
(38 `:model` + 84 `:index`, zéro échec, zéro ignoré) ; banc compilé
(`:index:compileBenchKotlin`).

### Fichiers repris — `model/src/main` (6)

| Chemin dans code-lsp | Rôle |
|---|---|
| `model/src/main/kotlin/jo/codeide/lsp/model/AssociationFichier.kt` | association fichier → module (racine de sourceset la plus profonde) |
| `model/src/main/kotlin/jo/codeide/lsp/model/ConvertisseurModeleLsp.kt` | conversion PURE ClasspathResult → ProjetLsp (W5/W6, doublons, cycles) |
| `model/src/main/kotlin/jo/codeide/lsp/model/DepotModeleLsp.kt` | dépôt observable du modèle (publication par instantanés) |
| `model/src/main/kotlin/jo/codeide/lsp/model/Empreintes.kt` | empreintes W6 par contenu + [LecteurStatistiquesFichier]/[LecteurStatistiquesSysteme] |
| `model/src/main/kotlin/jo/codeide/lsp/model/PersistanceModeleLsp.kt` | persistance versionnée validée (lsp-modele.json, corrige W7) |
| `model/src/main/kotlin/jo/codeide/lsp/model/ProjetLsp.kt` | types publics du modèle (ProjetLsp, ModuleLsp, ClasspathInstantane, KindEntree…) |

### Fichiers repris — `model/src/test` (5)

| Chemin dans code-lsp | Rôle |
|---|---|
| `model/src/test/kotlin/jo/codeide/lsp/model/AssociationFichierTest.kt` | tests de l'association fichier → module |
| `model/src/test/kotlin/jo/codeide/lsp/model/ConvertisseurModeleLspTest.kt` | tests de la conversion (doublons, cycles, empreintes, normalisation) |
| `model/src/test/kotlin/jo/codeide/lsp/model/EmpreintesTest.kt` | tests des empreintes W6 |
| `model/src/test/kotlin/jo/codeide/lsp/model/FixturesClasspath.kt` | bâtisseurs de fixtures du protocole v8 (+ FauxStats) |
| `model/src/test/kotlin/jo/codeide/lsp/model/PersistanceModeleLspTest.kt` | tests de la persistance versionnée |

### Code NEUF enregistré — `model/…/protocole` (1)

| Chemin dans code-lsp | Rôle |
|---|---|
| `model/src/main/kotlin/jo/codeide/lsp/model/protocole/ProtocoleClasspath.kt` | CODE NEUF code-lsp (alternative §4 faute de P1) : ClasspathResult, ClasspathModule, ClasspathEntry, ClasspathKind — clean-room depuis la surface observable (fixtures + conversion), jamais lu du source du protocole |

### Fichiers repris — `index/src/main` (17)

| Chemin dans code-lsp | Rôle |
|---|---|
| `index/src/main/kotlin/jo/codeide/lsp/index/AnalyseurClassFile.kt` | analyseur minimal de fichiers .class (membres, sans dépendance) |
| `index/src/main/kotlin/jo/codeide/lsp/index/AnalyseurClasspathSysteme.kt` | analyse du classpath du système hôte (rt/JDK) |
| `index/src/main/kotlin/jo/codeide/lsp/index/AssembleurSegment.kt` | assemblage des données d'un segment (logique pure) |
| `index/src/main/kotlin/jo/codeide/lsp/index/CacheSegments.kt` | cache LRU des segments décodés (budget tas explicite) |
| `index/src/main/kotlin/jo/codeide/lsp/index/ClesSegments.kt` | clés des segments — noms de fichiers depuis empreintes W6 |
| `index/src/main/kotlin/jo/codeide/lsp/index/DonneesSegment.kt` | structures de données d'un segment (paquets internes) |
| `index/src/main/kotlin/jo/codeide/lsp/index/EcrivainSegment.kt` | format binaire des segments (magique + version + crc32) |
| `index/src/main/kotlin/jo/codeide/lsp/index/EntrepotSegments.kt` | stockage PARTAGÉ des segments entre projets |
| `index/src/main/kotlin/jo/codeide/lsp/index/EtatIndex.kt` | phase de construction d'un index (états/progression) |
| `index/src/main/kotlin/jo/codeide/lsp/index/LecteurSegment.kt` | segment décodé en mémoire, interrogable par préfixe |
| `index/src/main/kotlin/jo/codeide/lsp/index/LecteurZipBrut.kt` | lecteur ZIP minimaliste sans java.util.zip (sensibilité ART) |
| `index/src/main/kotlin/jo/codeide/lsp/index/NettoyageSegments.kt` | nettoyage des segments orphelins |
| `index/src/main/kotlin/jo/codeide/lsp/index/OctetsIndex.kt` | primitives de lecture d'entiers encodés (gros-boutien) |
| `index/src/main/kotlin/jo/codeide/lsp/index/ServiceIndexDisque.kt` | service principal : construction bornée/annulable, fusion LSM |
| `index/src/main/kotlin/jo/codeide/lsp/index/TypesEtatIndexEtendus.kt` | phase détaillée de construction (affinage des états) |
| `index/src/main/kotlin/jo/codeide/lsp/index/TypesIndex.kt` | conteneur d'origine des classes d'un segment (localisation) |
| `index/src/main/kotlin/jo/codeide/lsp/index/VueIndex.kt` | vue ordonnée d'un classpath sur les segments (union LSM) |

### Fichiers repris — `index/src/test` (15)

| Chemin dans code-lsp | Rôle |
|---|---|
| `index/src/test/kotlin/jo/codeide/lsp/index/AnalyseurClassFileDifferentielTest.kt` | test différentiel du lecteur de membres (android.jar réel API 16) |
| `index/src/test/kotlin/jo/codeide/lsp/index/AnalyseurClassFileTest.kt` | tests de l'analyseur de .class |
| `index/src/test/kotlin/jo/codeide/lsp/index/AnalyseurClasspathSystemeTest.kt` | tests de l'analyse du classpath système |
| `index/src/test/kotlin/jo/codeide/lsp/index/AssembleurSegmentTest.kt` | tests de l'assemblage de segment |
| `index/src/test/kotlin/jo/codeide/lsp/index/CacheSegmentsTest.kt` | tests du cache LRU (budget, éviction) |
| `index/src/test/kotlin/jo/codeide/lsp/index/ClesSegmentsTest.kt` | tests des clés de segments |
| `index/src/test/kotlin/jo/codeide/lsp/index/EcrivainSegmentTest.kt` | tests du format binaire (corruption, version) |
| `index/src/test/kotlin/jo/codeide/lsp/index/FixturesIndex.kt` | bâtisseurs de fixtures de l'index (jars de test forgés) |
| `index/src/test/kotlin/jo/codeide/lsp/index/FormatSegmentTest.kt` | tests du format de segment (aller-retour complet) |
| `index/src/test/kotlin/jo/codeide/lsp/index/LecteurZipBrutTest.kt` | tests du lecteur ZIP brut |
| `index/src/test/kotlin/jo/codeide/lsp/index/NettoyageSegmentsTest.kt` | tests du nettoyage des orphelins |
| `index/src/test/kotlin/jo/codeide/lsp/index/ServiceIndexDisqueTest.kt` | tests du service (construction, annulation, progression) |
| `index/src/test/kotlin/jo/codeide/lsp/index/ServiceIndexDisqueVidageTest.kt` | tests du vidage de l'index |
| `index/src/test/kotlin/jo/codeide/lsp/index/TypesEtatIndexEtendusTest.kt` | tests des phases détaillées |
| `index/src/test/kotlin/jo/codeide/lsp/index/VueIndexTest.kt` | tests de la vue (fusion LSM, ordre) |

### Fichiers repris — `index/src/bench` (1)

| Chemin dans code-lsp | Rôle |
|---|---|
| `index/src/bench/kotlin/jo/codeide/lsp/index/banc/BancIndex.kt` | banc de mesure ADR 0111 (jeux petit/moyen/lourd ; jamais dans la chaîne de vérification) |

---

## Corpus de test — banc d'incohérences P1–P8 (mission « niveau Android Studio », 31/10/2026)

> Enregistrement **R9** des cibles de test du corpus réel (mission §4.4). Ces
> dépôts sont des **DONNÉES DE CORPUS** (code Java réel à diagnostiquer), pas des
> reprises de code ni des références de conception : aucun fichier n'est copié
> dans code-lsp — le contenu est **téléchargé à l'exécution** par
> `scripts/telecharger-corpus-android.sh` (répertoire gitignoré
> `bancs/corpus-android/`, cf. `bancs/corpus-android/LISEZMOI.md`). Commits
> épinglés par `git ls-remote` le 31/10/2026.

| Dépôt | Licence | Commit épinglé | Rôle corpus |
|---|---|---|---|
| `github.com/OpenTracksApp/OpenTracks` | Apache-2.0 | `3c23a9f5400c7b5b914a221469d76cc019a18296` | corpus principal : application Java (99,7 %), viewBinding, AndroidX/Material/ConstraintLayout — P3 (zéro FP), P4 (rappel), P7 (rename) |
| `github.com/gsantner/markor` | Apache-2.0 | `7b15e66932f170c0b3f105c522111b0f32d6c73d` | application Java (99,7 %), vues classiques sans binding — diversité UI |
| `github.com/slapperwan/gh4a` | Apache-2.0 | `39f03ffe122125e46a51de16d90881047ed4660e` | application Java (93,4 %), Retrofit/OkHttp/RxJava2/WorkManager — stress générics/callbacks |
| `github.com/AnySoftKeyboard/AnySoftKeyboard` | Apache-2.0 | `8cf81a3a2f73e9914c8613d56390a933486f0c6b` | application Java multi-modules (~73,8 %) — résolution inter-modules |
| `github.com/android/architecture-components-samples` (module `BasicSample`) | Apache-2.0 | `e849ce3004ccd1132a121cf513bbcb7996d95c30` | module Java 100 % : DataBinding + Room + annotationProcessor — couverture binding/AGP |
| `github.com/dmfs/opentasks` (secours, non téléchargé par défaut) | Apache-2.0 | `3ccb6f9d0195795bd461c3e634bbb8b22c72932c` | réserve Java 100 % si un projet principal devient inutilisable |

> **Consultations en lecture seule de la mission** (aucune reprise de code —
> comportements décrits pour eux-mêmes, conformément à la liste blanche du
> §Références externes d'`AGENTS.md`) : documentation officielle JetBrains
> (help/idea, Inspectopedia, SDK docs), blog JetBrains, code public d'
> `JetBrains/intellij-community` (Apache-2.0), code public du plugin Android
> (dépôt des outils Android, Apache-2.0), documentation developer.android.com,
> artefact SDK `platform-35_r02.zip` (`api-versions.xml` inspecté), YouTrack
> JetBrains. Aucun autre projet tiers n'est nommé dans les livrables de la
> mission (les projets de corpus ci-dessus sont des données de test).
