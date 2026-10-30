# 03 — Exécution étage 0 : amorçage (vague 0)

> **Décision du propriétaire reçue le 2026-10-30** dans le canal de mission :
> « Je t'autorise à commencer les étapes, tu dois travailler uniquement sur
> ta branche » — ADR 0100 ACCEPTÉE (voie C — hybride), ADR 0103 ACCEPTÉE
> (voie (i) — parseur officiel + modèle conservateur, (iii) différée).
> L'exécution démarre à l'étage 0 du plan §4 de `00-synthese.md`, sur la
> branche `etude/moteur-kotlin` uniquement. Le présent document est le
> journal d'exécution de cet étage (méthode : miroir des rapports Java —
> chiffres étiquetés [mesuré] / [estimé], écarts signalés).

## 1. Périmètre exécuté (vague 0 — 7 items de la synthèse §4)

| # | Item | État | Détail |
|---|---|---|---|
| 1 | Module `:kotlin` squelette | **Fait** | `kotlin/build.gradle.kts` : toolchain 21, bytecode 17 (convention d8/ART), Kotlin 2.2.10 (chaîne racine) ; `settings.gradle.kts` : `include(":kotlin")` + dépôt intellij-dependencies activé (cache-redirector) |
| 2 | Grammaire vendorée (ré-obtention amont) + suite de parité + VENDOR/NOTICE | **Fait** | Ré-obtention `git archive` @ tag v2.3.0 (`f95cb2f7…`) — identité octet pour octet vérifiée `diff -r` contre le vendoring du spike ; suite de parité fiches 010–012 (14 tests) ; notice `VENDOR.md` + LICENSE/COPYRIGHT embarqués au jar (META-INF/vendor-kotlin/) ; script de re-sync `scripts/vendor-grammaire-kotlin.sh` (ADR 0121) |
| 3 | Extension `verifier-provenance.sh` au périmètre `kotlin/` | **Fait** | Trois régimes : vendoring (notices + inscription), zones de reprise voie C (inscription PRÉALABLE fichier par fichier), code neuf libre. Testé positif ET négatif (fichier de zone non inscrit → ÉCHEC) |
| 4 | Reprises contrôlées des modules C | **Fait (inscription + contrôle, copie différée)** | Contrôle R9.4 MENÉ sur les 9 fichiers (en-têtes/KDoc : aucun tiers, aucune mention GPL — réserve P-K1/D5 sur synthetics) ; inscriptions préalables R1–R9 au manifeste §3 ; **aucune copie à ce jour** — copie aux étages de câblage (1 : classpath/synthetics ; 5 : confort) |
| 5 | CI : build + scan ART + dexage + seuils + bancs | **Fait** | 3 jobs neufs (`kotlin-parite`, `kotlin-art`, `kotlin-banc`) + job `provenance` étendu — 9 jobs au total. Scan : 0 motif sur la pile spécifique (tolérance documentée : 2 readAllBytes dans kotlin-stdlib, fondation partagée `:java`) ; dexage d8 build-tools 34 + seuils |
| 6 | Actions propriétaire | **Tracées** | P-K2 (binaires intellij-dependencies), second manifeste, Q-K1–Q-K6, audit GPL — §5 ci-dessous ; aucune n'est bloquante pour l'étage 0, P-K2 bloque la PUBLICATION du pack |
| 7 | Gabarit de mesure appareil | **Fait** | `spike-kotlin/appareil/{banc-appareil.sh, GABARIT-RESULTATS.md}` — déclinaison du patron Java ; aucune valeur pré-remplie (R5) |

## 2. Décisions d'exécution consignées (pas dans le plan initial)

1. **Compilation séparée du vendoring** (`vendor` source set, exemptée
   d'`explicitApi`) : les sources amont n'ont pas de modificateurs de
   visibilité explicites — l'exemption ADR 0104/0115.4 devient STRUCTURELLE
   (le code neuf compile lui en explicitApi Strict, tâche `compileKotlin`).
2. **syntax-api déclarée sur les DEUX compilations** (`main` + `vendor`) :
   la compilation vendor a son propre classpath ; et `annotations:26.0.2`
   en portée compile (le POM de syntax-api ne la porte qu'en runtime, la
   grammaire importe `ApiStatus` à la compilation).
3. **Copie des reprises DIFFÉRÉE aux étages de câblage** : les 9 fichiers
   dépendent du noyau (`jo.lspkotlin.parse.*`, `api.*`) inexistant à
   l'étage 0 — les copier maintenant casserait « build vert ». L'inscription
   préalable (R9.2) est posée ; la copie se fera à l'étage 1
   (classpath/synthetics) et 5 (confort), avec re-contrôle R9.4.
4. **Baseline digeste NEUVE** : le corpus de parité du nouveau module est le
   dépôt code-lsp (280 fichiers [mesuré] — l'ancien digeste n'est pas
   repris, corpus différent). Signature documentée : `-Dparite.majBase=true
   --rerun-tasks` (leçon v1.22.0 de l'ancien dépôt, reprise au KDoc).
5. **Vocabulaire amont fidèle** : le nœud racine s'appelle `kotlin.FILE`
   (enregistrement KMP) — la fiche 012 porte le nom historique `KT_FILE`
   du monde PSI ; les tests épinglent le comportement (racine fichier, sans
   erreur) sous le nom amont, commentaire de vocabulaire dans le test.
6. **Constat de tolérance amont** : la grammaire KMP ne marque NI les
   instructions de premier niveau en mode fichier, NI la chaîne non fermée
   (le jeton est émis, l'arbre construit) — le rejet relève de la
   sémantique compilateur, pas de la couche grammaticale. Les tests
   n'épinglent que ce que le cahier exige (acceptation silencieuse,
   sans-lever).

## 3. Mesures de l'étage 0 (bac à sable — JVM de bureau conteneurisée,
2 cœurs, OpenJDK 21.0.13, limites basses R5)

**Banc `:kotlin:bench` (nombres, aucun seuil) :**

| Mesure | 500 l. | 3 000 l. |
|---|---:|---:|
| Parse froid | 274,5 ms | 46,4 ms |
| Parse chaud p50 | 6,4 ms | 18,6 ms |
| Parse chaud p95 | 37,0 ms | 51,9 ms |
| Productions / jetons | 5 448 / 4 210 | 31 368 / 24 274 |
| Marqueurs d'erreur | 0 | 0 |

Tolérance : 101 troncatures hostiles, **0 lever**. Tas au repos : 3 698 Kio.
Les volumes d'arbre (5 448 / 31 368 productions) sont IDENTIQUES aux
mesures S2 du spike (même générateur, même grammaire) — l'écart de latence
(p50 6,4 ms vs 1,8 ms au spike) est l'empreinte du harnais (digeste SHA-256
+ matérialisation des jetons par analyse) et de la machine ; les deux
instrumentations vivent en parallèle, la comparaison entre versions du
MODULE se fait au `:kotlin:bench` (constantes de méthode inchangées).

**Scan ART (moteur + pile) :**

| Jar | Classes | Méthodes déclarées | Motifs sensibles |
|---|---:|---:|---|
| kotlin-1.0.0.jar (moteur : grammaire + harnais) | 69 | 150 | **AUCUNE** |
| syntax-api-jvm-0.3.340.jar | 309 | 514 | **AUCUNE** |
| lang-syntax-jvm-0.3.340.jar | 3 | 71 | **AUCUNE** |
| annotations-26.0.2.jar | 61 | 33 | **AUCUNE** |
| kotlin-stdlib-2.2.10.jar (fondation partagée `:java`) | 970 | 6 612 | 2 × readAllBytes (API 33+, désugarable — tolérance documentée) |

**Dexage d8 (build-tools r34, `--release --min-api 26`, pile chaude
complète : moteur + syntax-api + lang-syntax + annotations + stdlib)
[mesuré au bac à sable — E-K2 levé de fait : d8 téléchargé et exécuté] :**

| Mesure | Valeur | Seuil étage 0 | Verdict |
|---|---:|---:|---|
| Références de méthodes (method_ids) | **15 964** | ≤ 32 768 (moitié du mur 64K) | **tenu** |
| Taille classes.dex | **2 742 668 o (2,62 Mo)** | ≤ 4 Mo (budget T1 APK) | **tenu** |

**Jar moteur : 216 Ko [mesuré]** (69 classes — grammaire + harnais ; pour
mémoire : l'oracle ancien complet = 876 998 o / 287 classes [mesuré spike]).

## 4. Critères de sortie de l'étage 0 (synthèse §4)

| Critère | État | Preuve |
|---|---|---|
| Build vert | **Atteint** | `./gradlew :kotlin:build :kotlin:bench` — SUCCÈS (tests, jar, descripteur de pack) |
| Parité grammaire verte | **Atteinte** | 14/14 tests fiches 010–012 : corpus 280 fichiers SANS marqueur d'erreur + digeste baseline signée (280 lignes) + tolérance (0 lever) + fixtures |
| Zéro fichier hors registre | **Atteint** | `scripts/verifier-provenance.sh` rc=0 (trois régimes OK) ; test négatif validé (rc=1 sur fichier de zone non inscrit) |
| 0 motif ART | **Atteint** | Moteur + pile spécifique : AUCUNE référence sensible (tableau §3) |

**L'étage 0 est CLOS sur ses quatre critères.** La suite (étage 1) attend :
le gabarit appareil rempli (porte d'entrée, §5 du gabarit) et les arbitrages
Q-K1/Q-K2 (index CIDX) qui conditionnent le câblage classpath.

## 5. Actions propriétaire ouvertes (aucune ne bloque l'étage 0)

| # | Action | Référence | Bloque |
|---|---|---|---|
| 1 | Conditions des artefacts BINAIRES intellij-dependencies (syntax-api & co) — hypothèse Apache-2.0 à confirmer | P-K2 (ALERTES §2), ADR 0120, NOTICE n° 7 | la **publication** du pack langue (pas le développement) |
| 2 | Audit de similarité d'expression (filiation documentaire GPL-3.0 des décisions anciennes D1/D2/D5/D8) — rôle DISTINCT des implémenteurs | P-K1, manifeste §5 | la copie de synthetics (R1/R2) si le doute n'est pas levé — recommandé AVANT l'étage 1 |
| 3 | Arbitrage Q-K1 (CIDX étendu vs index dédié) et Q-K2 (décodage @Metadata dans :index ou :kotlin) | DEMANDES-INTERFACE, ADR 0108 | le câblage classpath de l'étage 1 (le développement peut démarrer sur l'hypothèse index dédié — ADR 0108) |
| 4 | Lecture du second manifeste (propriétaire) | synthèse §6.4 | rien (action d'information) |
| 5 | Passe appareil (Termux) : exécuter `spike-kotlin/appareil/banc-appareil.sh`, remplir le gabarit | GABARIT-RESULTATS.md | l'OUVERTURE de l'étage 1 (porte d'entrée) |

## 6. Prochaine étape — étage 1 (noyau parse/session/classpath, 30–52 j-h [estimé])

Périmètre (synthèse §4) : façade `Kt*` (fiche 013 — au-dessus du harnais),
session documents, scan projet paresseux, lecteur classpath (reprises R4/R5
+ contrat ADR 0211/0213 ancien), index appelables, synthétiques (reprises
R1/R2). Critères de sortie : fiches parse/session/index/synthétiques
vertes ; latences S0 tenues ; **gabarit appareil rempli (porte d'entrée)**.
