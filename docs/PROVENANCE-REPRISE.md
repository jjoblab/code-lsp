# PROVENANCE-REPRISE — Manifeste de reprise sélective

> Mission « étude moteur Java PSI » (dépôt `jjoblab/code-lsp`, branche `etude/moteur-java`).
> **État : VIDE par construction.** Règle R9.1 du prompt de mission : *rien n'est copié depuis un ancien dépôt tant que le propriétaire n'a pas tranché la licence du nouveau dépôt (ADR 0001)*. Ce manifeste est le registre où TOUTE reprise future devra être inscrite avant d'avoir lieu (R9.2 : dépôt d'origine, chemin, commit, auteur déclaré, licence, statut de provenance, décision, justification — aucune reprise « en bloc »).
> **Décision ADR 0001 rendue le 2026-10-28** (Accepté modifié — option 2 : **réécriture intégrale du moteur**, licence Apache-2.0) : l'exclusion du moteur vendored devient **définitive** (aucune levée) ; les reprises candidates du §3 demeurent conditionnées à l'inscription préalable au manifeste (R9.2) et, pour `:lsp:model`, à l'action P1 (LICENSE du protocole tooling).
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
| Patcheur `lsp/ecj-art` (2 f / 468 l.) | codeide-lsp:lsp/ecj-art | R9.3 : ADR 0110 indique l'approche « reprise d'un projet tiers » (passes `RelocateTypesInJar.kt`/`EclipseStreamArtPass.kt` du build-logic tiers) | Mêmes voies ; recommandation de l'étude : **réécrire** (surface réduite, dicée par l'API ASM et la javadoc JDK/JEP 322 — procédé déjà appliqué avec succès au niveau A en v0.94.0) |
| Fichiers niveau A (3 shims `compat/`) | codeide-lsp:lsp/java/src/main/java/jo/lspjava/ecj/compat/ | R9.3 : « tout fichier ou module que l'ADR 0110 classe en niveau A ou B » | Déjà réécrits depuis la spec JDK (v0.94.0) — le statut « propre » peut être reconnu par décision ADR 0001 sur la base de cette exécution |
| Fichiers niveau B (~31 fichiers « portage » déclarés) | codeide-lsp:lsp/java (divers) | R9.3 (idem) | Attestation de paternité du propriétaire (16/10, reconfirmée 19/10/2026) — à acter pour ce dépôt en ADR 0001 |
| Fichiers homonymes du projet tiers | historique : `JdtCodeFolder`, `JdtSemanticHighlighter`, `JdtSourceUnit`, `RuntimeVersion`, `StackWalker`, `InputStreamCompat` | R9.3 : « les fichiers dont il [l'ADR 0110] signale un nom identique à celui d'un projet tiers » | Renommage intégral `Jdt*→Ecj*` exécuté (v0.95.0) — le renommage seul ne suffit pas selon l'ADR 0110 ; traitement du contenu déjà acté |
| `lsp/kotlin`, `lsp/xml`, hôte tri-langue | codeide-lsp:lsp/{kotlin,xml,host} | R8 : moteur Java uniquement | Hors périmètre — pas une exclusion de provenance |

## 3. Registre des candidats à reprise (aucun ne sera copié avant l'ADR 0001)

| Élément | Dépôt/chemin @ commit | Taille | Auteur déclaré | Licence amont | Statut | Décision proposée | Justification |
|---|---|---|---|---|---|---|---|
| `:lsp:index` (index CIDX) | codeide-lsp:lsp/index/src @ cb809e14 | 33 f / 6 472 l. main | jjoblab (couche neuve, post-ADR 0110) | dépôt propriétaire | à vérifier (contrôle R9.4 à mener à la copie) | **reprendre tel quel** | Socle conservé par l'étude D7 (bat le design IntelliJ sur préfixe/+1 dép/mémoire/corruption) |
| `:lsp:model` | codeide-lsp:lsp/model/src @ cb809e14 | 11 f / 2 160 l. | jjoblab (idem) | idem + dépend protocole tooling **sans LICENSE** | à vérifier | **reprendre** | Modèle immuable consommé par l'index et le moteur ; condition : éclairer le protocole (§4) |
| `:lsp:testing` | codeide-lsp:lsp/testing/src @ cb809e14 | 4 f / 1 464 l. | jjoblab | idem | à vérifier | **reprendre** | Parité moteur↔index, zéro dépendance externe publiée |
| Jeux de données / golden files des tests `lsp/java` | codeide-lsp:lsp/java/src/test (entrées-sorties attendues) | à inventorier à la copie | jjoblab | idem | à vérifier | **reprendre comme données** | Conserver la valeur des 449 tests sans reprendre le code (00b §4) |
| Squelette Gradle / conventions qualité | codeide-lsp:build-logic | ~7 plugins | jjoblab | idem | à vérifier | **s'inspirer** (réécrire le minimum) | R3 n'exige que le squelette du spike |

## 4. Dépendances d'artefacts du nouveau dépôt (aucune copie de code — chaîne à tenir à jour)

| Artefact | Version | Licence | Rôle prévu | Points d'attention |
|---|---|---|---|---|
| `org.eclipse.jdt:org.eclipse.jdt.core` (+ graphe) | 3.40.0 | EPL-2.0 | Compilateur/moteur si voie statu quo ou hybride | Code MODIFIÉ (patch ART) et REDISTRIBUTÉ → obligations EPL-2.0 (sources amont disponibles, description des modifications) — reprise du patcheur conditionnée à sa réécriture (§2) |
| `org.eclipse.lsp4j:lsp4j` | 0.21.2 | EPL-2.0 | Protocole LSP (façade) | — |
| `com.github.jjoblab.codeide-tooling:protocol` | 1.0.0 | **aucun fichier LICENSE sur le dépôt à ce jour** | Types `ClasspathResult` (modèle) | **Action propriétaire** : ajouter une LICENSE au dépôt frère, sinon réécrire les ~10 types consommés |
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
