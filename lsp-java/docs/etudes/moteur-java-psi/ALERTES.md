# ALERTES — anomalies, risques de provenance, écarts avec le prompt de mission

> Étude « moteur Java PSI » ( dépôt `jjoblab/code-lsp`, branche `etude/moteur-java` ). Document vivant : toute nouvelle alerte s'ajoute ici ( R6.2 : signaler factuellement, sans corriger ni minimiser ).

## 1. Écarts entre les affirmations du prompt (§2 « État de l'existant, à VÉRIFIER ») et le code vérifié

| # | Affirmation du prompt | Vérification ( faite ) | Statut |
|---|---|---|---|
| A1 | « verrou `synchronized` global ≈ **20 méthodes** d'`EcjCompilerEngine` » | **24 méthodes synchronized + 2 blocs synchronisés** ( dont 17 public ) — comptage `rg` sur le code @ `cb809e14` ( D11 ) | **Écart affiné** ( sans gravité — l'ordre de grandeur tient ) |
| A2 | « **400 tests** dans `lsp/java` » | **449 méthodes @Test/@TestFactory** ( 65 fichiers, 16 229 l., 0 échec, 76 ignorés ) ; README ancien ( 21/10 ) dit « 400 », plan de stabilisation « 498 tests Java module+host » | **Dérive de comptage** — trois chiffres circulent ; le 449 est mesuré |
| A3 | « le patcheur réécrit `Runtime$Version`, `StackWalker`, `InputStream.readAllBytes` » | Scan du jar ecj 3.40.0 : `Runtime$Version` ( 1× ) et `readAllBytes` ( 3× ) confirmés ; **`StackWalker` ABSENT** des références statiques ET des chaînes ( balayage Utf8 ) ; en revanche **`InputStream.readNBytes` ( 1× ) présent et non listé** par le prompt | **Écart factuel** — la liste de shims doit être : `Runtime$Version`, `readAllBytes`, `readNBytes` ( + `StackWalker` à réévaluer aux montées de version ) — ADR 0018 |
| A4 | « ≈ 25 800 lignes Java dans `lsp/java` ( 53 fichiers ) » | 53 fichiers core ≈ 25 839 l. main Java ; module complet 118 f / 42 068 l. main total | Conforme ( précision ) |
| A5 | « pas de banc de qualité de classement de la complétion » | Confirmé : uniquement des bancs de latence ( `LatenceCompletionBenchmark` ) | Conforme — traité par ADR 0016 |

## 2. Risques de provenance ( R6.6 — signalés factuellement, non corrigés )

- **P1 — Protocole tooling sans licence** : le dépôt `jjoblab/codeide-tooling` @ `b60f7f0` n'a **pas de fichier LICENSE** ( constat de la notice tierce de `codeide-lsp`, re-vérifié ) ; `:lsp:model` ( candidat à reprise ) en dépend via `com.github.jjoblab.codeide-tooling:protocol 1.0.0`. Action propriétaire requise avant reprise ( ADR 0001/0017 ).
- **P2 — Moteur vendored exclu par défaut** ( R9.3 ) malgré la clôture ADR 0110/0200 : le présent dépôt ne préjuge pas de la levée ; pièces ( attestations 16–19/10/2026 ) citées pour la décision ADR 0001. Le patcheur `lsp/ecj-art` ( approche reprise d'un tiers selon ADR 0110 ) : **réécriture recommandée**, interdiction de copie en attendant.
- **P3 — En-têtes `com.siyeh.ig.*`** ( inspections InspectorGadgets, Apache-2.0, « Copyright 2003-2015 Dave Griffith, Bas Leijdekkers » ) constatés dans intellij-community ( D9 ) : toute réécriture future d'inspections exige ADR dédié + attribution — signalé pour mémoire ( R6.4/R9.5 ).
- **P4 — Conformité R6.2 de l'étude** : **aucune source GPL/AGPL ni de licence non vérifiée consultée** pendant la mission ( attesté : les seuls terrains sont intellij-community [Apache-2.0], codeide-lsp/tooling/CodeIDE/code-editor [proprios jjoblab], artefacts publics ecj/lsp4j/ASM/guava/JetBrains, specs JLS/JVM/LSP, doc publique Android/JetBrains/Eclipse ). Aucun projet concurrent d'IDE mobile consulté.

## 3. Anomalies et découvertes à caractère d'alerte ( issues de l'étude )

- **AL1 — Conditions des artefacts binaires JetBrains non consignées dans les jars** : les 4 jars V1 ( `org.jetbrains:*:0.3.381` ) n'embarquent **aucun `META-INF/LICENSE`** ( vérifié ) ; la licence des sources est Apache-2.0 ; l'hypothèse de travail pour les binaires ( Apache-2.0 ) **reste à confirmer** par le propriétaire avant la vague « parsing incrémental » ( ADR 0017 ).
- **AL2 — La frontière headless publiée d'IntelliJ bouge** : la pile 232.4652 exige 3 EP manuels + un home d'installation pour fonctionner ( spike §4.1 ) ; à HEAD, l'amorçage enregistre programmatiquement une partie de ces EP — **toute montée de version de la pile ( si un jour la voie A était reconsidérée ) re-diagnostic obligatoire**.
- **AL3 — `ReadMostlyRWLock` et `ClassCancellation` n'existent plus à HEAD** ( `865067b0` ) alors que la documentation publique les présente encore : écart doc/code à connaître avant toute inspiration concurrence ( D11 ).
- **AL4 — Pas de requête par préfixe dans le stockage IntelliJ** ( `AllClassesGetter.java:193-204` énumère toutes les clés ) : toute stratégie « embarquer l'index IntelliJ » hériterait de ce coût — CIDX l'évite ( D7 ).
- **AL5 — `java-impl.jar` ( 22,8 Mo ) et `java-analysis-impl.jar` ( 15,1 Mo ) retirables de la pile V2** sans casser parse/résolution ( vérifié au spike ) : tout chiffre d'empreinte « PSI headless » doit préciser sa pile exacte.
- **AL6 — Variabilité des p95 sur JVM de bureau** : ±×1,5 entre passes ( même hôte, même jour — spike §9.2 ) ; les p50 sont stables. Les comparaisons de p95 exigent des passes communes.

## 4. Écarts d'exécution de la mission ( transparence )

- **E1 — Réinitialisation du bac à sable le 28/10/2026 (~10h13)**, en cours de mission : l'environnement de travail ( clones, worklog, rapports d'agents sur disque, token GitHub ) a été perdu et **reconstruit le même jour** ( clones au mêmes commits — `reclone.log` ; worklog reconstruit ; jarres re-téléchargées, tailles conformes ). Les **annexes D7–D12 sont des reconstructions** depuis le contexte de mission — note de provenance en en-tête de chaque annexe ; les citations `fichier:ligne` proviennent des lectures réelles d'avant réinitialisation, et des vérifications ponctuelles ont été rejouées après ( p.ex. `JavaCoreApplicationEnvironment.java:117-134` ce jour ).
- **E2 — Le jeton GitHub fourni en début de mission a été perdu dans la réinitialisation** ( il n'est plus sur le disque ) : le dépôt `jjoblab/code-lsp` est donc **commité localement sur la branche `etude/moteur-java` mais non poussé** sur GitHub. Action propriétaire : fournir un nouveau jeton ( ou pousser soi-même ) — aucun accès distant n'a été tenté depuis la perte. **Recommandation : révoquer l'ancien jeton** par précaution.
- **E3 — Le scanner ART de l'étude** ( `spike/scripts/ScanArt.kt`, Kotlin ) analyse les constant pools : **usages réflexifs invisibles** ( documenté dans l'outil et le rapport ) ; le comptage de méthodes est une borne basse ( méthodes déclarées, pas référencées ).
- **E4 — Aucun appareil Android disponible** pendant la mission : conformément au §6 du prompt, seules (a) l'analyse statique ART, (b) les mesures JVM ( limites basses ) et (c) le point d'entrée appareil ont été produits. Aucune mesure appareil n'est prétendue ( `02-spike-mesures.md` §9–§10 ).

## 5. Points restés « à vérifier » ( non clos par l'étude )

- Comportement GC ART ( SoftReference/ReferenceQueue ) — appareil requis.
- Conditions de publication exactes des artefacts `platform/util/storages` et `com.jetbrains.intellij.platform` ( ADR 0017 ).
- Empreinte mémoire du PSI complet d'un fichier **dans les artefacts 232** ( pas de banc dans le sparse ; l'estimation V1 — 82–393 Ko/arbre — est celle du parseur seul ).
- Enregistrements XML des weighers Java et implémentation backend du RPC complétion ( hors sparse, signalés D8 ).
- Le statut de la passe `StackWalker` du patcheur pour des versions ecj antérieures à 3.40 ( A3/ADR 0018 ).
