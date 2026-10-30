# PROVENANCE-REPRISE-KOTLIN — Manifeste de reprise sélective (moteur Kotlin)

> Mission « moteur Kotlin » (dépôt `jjoblab/code-lsp`, branche
> `etude/moteur-kotlin`). **ADR 0100 ACCEPTÉE le 2026-10-30** (voie C —
> hybride : noyau réécrit depuis le cahier, modules différenciants repris
> après contrôle, grammaire ré-obtenue de l'amont) : **la reprise voie C
> est OUVERTE** pour les zones §3, fichier par fichier, inscription
> PRÉALABLE ici (R9.2). Les zones NOYAU (`parse/`, `resolve/`,
> `diagnostics/`, `completion/`, `session/`, `api/` de l'ancien dépôt)
> restent en lecture interdite : elles se RÉÉCRIVENT du cahier
> (clean-room, AGENTS.md — R9.1 maintenu pour le noyau).
>
> Ancien dépôt (lecture seule, jamais modifié) : `jjoblab/codeide-lsp` @
> `bc11253b58a5eecd38194d46d5e862e1cf4185e3` (v1.22.1 ; le module
> `lsp/kotlin` est ACTIF — seul `:lsp:java` est gelé, ADR 0233 ancien).
> Sources tierces autorisées consultées : `JetBrains/kotlin` @ tag
> `v2.3.0` (`f95cb2f7…`, Apache-2.0) ; artefacts binaires `org.jetbrains:
> syntax-api`/`annotations` (intellij-dependencies) et
> `org.jetbrains.kotlin:kotlin-metadata-jvm` (Central) — voir §4.

## 1. Statuts utilisés

| Statut | Sens |
|---|---|
| **propre** | Origine entièrement maîtrisée (écrit à la main attesté, ou tierce sous licence permissive avec attributions conformes) |
| **à vérifier** | Doute documenté — exclu en attendant (R9.6) |
| **exclu** | Frappé d'exclusion par défaut (R9.3) ou hors périmètre (R8) |

## 2. Registre des exclusions et périmètres (R9.3 — cadrés par l'ADR 0100 ACCEPTÉE)

| Élément | Dépôt/chemin | Statut | Motif / voie de levée |
|---|---|---|---|
| Zones NOYAU `lsp/kotlin/src/main/kotlin/jo/lspkotlin/{parse,resolve,diagnostics,completion,session,api}/**` | codeide-lsp:lsp/kotlin/src/main/kotlin | **exclu (lecture interdite R9.1 — MAINTENUE)** | Voie C acceptée : ces zones se RÉÉCRIVENT depuis le cahier (469 comportements) avec les designs Java en naissance — l'exécution clean-room exige de ne PAS lire le code ancien. L'exécution en boîte noire (oracle) et les métriques d'outil restent autorisées |
| Zones DE REPRISE `lsp/kotlin/src/main/kotlin/jo/lspkotlin/{synthetics,preview,classpath,format,plis,documentation,symboles}/**` | idem | **reprise OUVERTE (ADR 0100, voie C)** — inscription préalable §3, contrôle R9.4 à la copie | La lecture y est autorisée POUR LE CONTRÔLE et le câblage ; chaque fichier copié doit être inscrit ICI AVANT (R9.2) et passer le contrôle R9.4 (en-têtes, attributions) |
| Grammaire vendorée `lsp/kotlin/src/main/vendor/**` | codeide-lsp:lsp/kotlin/src/main/vendor | **exclu de la reprise « reprise »** (R9.2 du prompt) | La grammaire ne se « reprend » pas de l'ancien dépôt : elle est **réobtenue depuis l'amont JetBrains** à une version épinglée (`compiler/multiplatform-parsing`, Apache-2.0) avec notice et registre — voir §4 |
| Tests `lsp/kotlin/src/test/**` | codeide-lsp:lsp/kotlin/src/test | consultation autorisée (R9.1) | Base du cahier de spécifications (R9.5) ; **jamais copiés** — les fiches ne contiennent aucun extrait de code |
| Docs, ADR, rapports | codeide-lsp:docs/** | consultation autorisée | Matière d'étude uniquement |

## 3. Registre des reprises voie C — INSCRIPTIONS PRÉALABLES (contrôle R9.4 MENÉ le 2026-10-30)

> L'évaluation « saine/fragile » provient de `00b-inventaire-existant.md` §3
> (sans lecture du code, pendant l'étude). **Le contrôle R9.4 (en-têtes,
> attributions) a été mené le 2026-10-30** après ouverture de la voie C :
> lecture des 9 en-têtes/KDoc, recherche de mentions tierces (JetBrains,
> Apache, GPL, crédits, @author) — verdict : AUCUN en-tête tiers, AUCUNE
> mention GPL, AUCUN crédit amont dans les 9 fichiers ; seules références
> externes = imports de la grammaire vendorée (Apache-2.0) et de
> `kotlin-metadata-jvm` (Apache-2.0). Réserve P-K1 : `synthetics/` cite la
> décision D5 dont la filiation documentaire remonte au projet tiers GPL-3.0
> (étude déclarée, code non consulté) — l'audit de similarité d'expression
> recommandé au propriétaire (rôle distinct des implémenteurs) reste
> l'action de levée.
>
> **COPIE DIFFÉRÉE AUX ÉTAGES DE CÂBLAGE** : ces fichiers dépendent du noyau
> (`jo.lspkotlin.parse.*`, `api.*`, `index.*`) qui n'existe pas à l'étage 0 —
> les copier maintenant casserait « build vert ». L'inscription est
> PRÉALABLE (R9.2 respecté) ; chaque fichier entre dans `kotlin/src/main/kotlin/`
> À SON ÉTAGE (ci-dessous), avec re-contrôle R9.4 au moment de la copie
> (écart éventuel entre bc11253 et le commit ancien alors courant).

| # | Fichier (zone) | Source @ commit | Taille [mesuré] | Auteur déclaré | Licence amont | Statut R9.4 (2026-10-30) | Décision (ADR 0100) | Copie prévue (étage) | Justification |
|---|---|---|---|---|---|---|---|---|---|
| R1 | `synthetics/AnalyseRessourcesAndroid.kt` | codeide-lsp:lsp/kotlin/src/main/kotlin/jo/lspkotlin/synthetics/AnalyseRessourcesAndroid.kt @ bc11253 | 284 l. | jjoblab | dépôt propriétaire | **passé** (re-contrôle à la copie vague 1e : PASSÉ ; réserve P-K1/D5 — copie effectuée sur ordre de poursuite de l'étage 1 reçu du propriétaire, audit recommandé DEMEURE OUVERT a posteriori) | reprendre (voie C) — **COPIÉE vague 1e** vers `kotlin/src/main/kotlin/jo/codeide/lsp/kotlin/synthetics/AnalyseRessourcesAndroid.kt` (adaptations de câblage : paquet jo.codeide.lsp.kotlin.synthetics, en-tête d'origine posé, deux références KDoc d'entités de l'ancien dépôt retéléphonées vers ScanProjetParesseux — corps repris tel quel, diff dans le commit) | étage 1 (noyau) | Capacité différenciante (entrées XML des synthétiques), éprouvée E2E (D5) |
| R2 | `synthetics/ClassesSynthetiques.kt` | idem …/synthetics/ClassesSynthetiques.kt @ bc11253 | 383 l. | jjoblab | idem | **passé** (re-contrôle à la copie vague 1e : PASSÉ ; réserve P-K1/D5 — copie effectuée sur ordre de poursuite de l'étage 1 reçu du propriétaire, audit recommandé DEMEURE OUVERT a posteriori) | reprendre (voie C) — **COPIÉE vague 1e** vers `kotlin/src/main/kotlin/jo/codeide/lsp/kotlin/synthetics/ClassesSynthetiques.kt` (adaptations de câblage : paquet jo.codeide.lsp.kotlin.synthetics, imports du noyau ancien RETIRÉS — jo.lspkotlin.resolve.DeclarationProjet/GenreDeclaration remplacés par le modèle local NEUF `DeclarationProjet`/`GenreDeclaration` du même paquet, parametres → index.ParametreAppelable ; en-tête d'origine posé, quatre références KDoc d'entités de l'ancien dépôt retéléphonées vers ScanProjetParesseux — corps repris tel quel, diff dans le commit). Fichier neuf ASSOCIÉ inscrit ici pour le contrôle du registre : `kotlin/src/main/kotlin/jo/codeide/lsp/kotlin/synthetics/DeclarationProjet.kt` — code NEUF jjoblab (modèle local de sortie dicté par la surface d'usage du module repris, frontière C de l'inventaire §3 ; l'ancien jo.lspkotlin.resolve.DeclarationProjet, type du NOYAU jamais lu, n'a pas de survivant ici) | étage 1 (noyau) | R/BuildConfig/ViewBinding/DataBinding avant build — capacité que l'amont n'a pas (D5) |
| R3 | `preview/PreviewsDocumentKotlin.kt` | idem …/preview/PreviewsDocumentKotlin.kt @ bc11253 | 522 l. | jjoblab | idem | **passé** | reprendre (voie C) | étage 5 (confort) | SPI neutre, budgets, frontière rendu nette (C1 livré) |
| R4 | `classpath/DecodeurMetadonnees.kt` | idem …/classpath/DecodeurMetadonnees.kt @ bc11253 | 284 l. | jjoblab | idem | **passé** (re-contrôle à la copie vague 1d : PASSÉ) | reprendre (voie C) — **COPIÉE vague 1d** vers `kotlin/src/main/kotlin/jo/codeide/lsp/kotlin/classpath/DecodeurMetadonnees.kt` (adaptations de câblage : paquet, imports retéléphonés SPI/modèle neuf, en-tête d'origine — corps repris tel quel) | étage 1 (noyau) | Décodeur `@Metadata` — différentiel 0 écart sur corpus réels (rapport A.3) ; alignement `:index` CIDX à l'étage 1 (Q-K1/Q-K2) |
| R5 | `classpath/LecteurClassBrut.kt` | idem …/classpath/LecteurClassBrut.kt @ bc11253 | 380 l. | jjoblab | idem | **passé** (re-contrôle à la copie vague 1d : PASSÉ) | reprendre (voie C) — **COPIÉE vague 1d** vers `kotlin/src/main/kotlin/jo/codeide/lsp/kotlin/classpath/LecteurClassBrut.kt` (adaptations de câblage : paquet, deux références KDoc au nouveau module, en-tête d'origine — corps repris tel quel) | étage 1 (noyau) | Lecteur de class-files maison sans ASM — même preuve A.3 |
| R6 | `format/FormateurKotlin.kt` | idem …/format/FormateurKotlin.kt @ bc11253 | 289 l. | jjoblab | idem | **passé** | reprendre (voie C) | étage 5 (confort) | Borné (ré-indentation canonique, ADR 0202), testé |
| R7 | `plis/PlisDocumentKotlin.kt` | idem …/plis/PlisDocumentKotlin.kt @ bc11253 | 135 l. | jjoblab | idem | **passé** | reprendre (voie C) | étage 5 (confort) | Petit, borné, testé (foldingRange) |
| R8 | `documentation/DocumentationKotlin.kt` | idem …/documentation/DocumentationKotlin.kt @ bc11253 | 199 l. | jjoblab | idem | **passé** | reprendre (voie C) | étage 5 (confort) | Hover KDoc sur le vendoring — petit, testé |
| R9 | `symboles/SymbolesDocumentKotlin.kt` | idem …/symboles/SymbolesDocumentKotlin.kt @ bc11253 | 149 l. | jjoblab | idem | **passé** | reprendre (voie C) | étage 5 (confort) | Symboles de document — petit, testé |

> Zones NON reprises (confirmées par l'ADR 0100) : `parse/`, `resolve/`,
> `diagnostics/`, `completion/`, `session/`, `api/` — **refonte** depuis le
> cahier (lecture interdite maintenue). Le digeste de parité ancien
> (`src/test/resources/parite/digeste-corpus.txt`) n'est PAS repris : le
> corpus du NOUVEAU module est différent (le dépôt code-lsp) — une baseline
> NEUVE a été signée à l'étage 0
> (`kotlin/src/test/resources/parite/digeste-corpus.txt`, 280 fichiers).

## 4. Dépendances d'artefacts et vendoring du module (aucune copie de code ancien — chaîne à tenir à jour)

| Élément | Source @ version | Licence | Rôle prévu | Points d'attention |
|---|---|---|---|---|
| **Grammaire officielle vendorée (PRODUCTION — étage 0, effectué)** | `JetBrains/kotlin`:compiler/multiplatform-parsing/common/src @ **tag v2.3.0** (`f95cb2f7693a15966fd8fd9b1d78d440545e7b66`), ré-obtention `git archive` du 2026-10-30 | **Apache-2.0** (LICENSE.txt + COPYRIGHT.txt copiés depuis `license/` du même tag ; `license/README.md` du tag revérifié : AUCUNE exception sur ce chemin) | Grammaire de production du module `kotlin/src/main/vendor/` — compilation séparée (exemption explicitApi), notices embarquées au jar (META-INF/vendor-kotlin/) | 23 f / 9 585 l. octet pour octet (**identité vérifiée par `diff -r` contre le vendoring du spike**, même tag même procédure) ; zéro modification locale ; re-sync = remplacement par `scripts/vendor-grammaire-kotlin.sh` (ADR 0121) ; **jamais obtenue depuis `codeide-lsp`** (R9.2 du prompt) ; la dérive de contenu est gardée par la SUITE DE PARITÉ (digeste structurel, fiches 010/012) |
| **Grammaire officielle vendorée (SPIKE — historique d'étude)** | `JetBrains/kotlin`:compiler/multiplatform-parsing/common/src @ **tag v2.3.0** (`f95cb2f7`), extraction `git archive` du 2026-10-30 | **Apache-2.0** (idem) | Base de mesure S2 du spike `spike-kotlin/src/main/vendor/` (module de mesure non publié) | 23 f / 9 585 l. octet pour octet ; conservé pour la reproductibilité des mesures d'étude (R5) |
| `org.jetbrains:syntax-api` (+ `lang-syntax` transitif) | 0.3.340 (épinglée par le tag amont v2.3.0) | Apache-2.0 (sources) | Arbre de construction syntaxique | **Conditions des artefacts binaires intellij-dependencies à consigner** (miroir AL1 Java : aucun `META-INF/LICENSE` dans les jars — hypothèse Apache-2.0 à confirmer par le propriétaire) ; accès cache-redirector |
| `org.jetbrains:annotations` | 26.0.2 | Apache-2.0 | Annotations amont | Central |
| `org.jetbrains.kotlin:kotlin-metadata-jvm` | 2.2.10 | Apache-2.0 | Décodage `@Metadata` | Central ; monter avec la chaîne d'outils |
| `org.eclipse.lsp4j:lsp4j` | 0.21.2 | EPL-2.0 | Transport LSP (si le serveur vit dans le module, miroir `:java`) | — |
| (voies (ii)/(iii) uniquement) `kotlin-compiler-embeddable` | 2.2.10 | Apache-2.0 | Frontend K2 embarqué — **mesuré au spike, disqualifié pour l'APK** (53,6 Mo / 64 507 méthodes / ~2 170 motifs ART) | Jamais en compile-scope du module moteur (garde CI proposée, ADR 0120) ; pack dédié si mode profond |

## 5. Attributions d'origine (R9.5 — à conserver exactes lors de toute reprise)

- **Grammaire Kotlin multiplateforme** : `JetBrains/kotlin`
  `compiler/multiplatform-parsing`, Apache-2.0 — `LICENSE.txt` +
  `COPYRIGHT.txt` doivent accompagner tout vendoring ; l'algorithme de
  reconstruction d'arbre adapté de `NewTestParser.kt` amont (Apache-2.0,
  crédité dans la KDoc de l'ancien dépôt) devra être crédité à l'identique
  s'il est réadapté.
- **Étude du projet tiers de référence GPL-3.0** (R9.4 du prompt) : les
  décisions D1, D2, D5, D8 et le §2 « Enseignements » de
  `LSP_KOTLIN.md` de l'ancien dépôt déclarent s'appuyer sur l'étude d'un
  projet tiers sous **GPL-3.0** (« aucune ligne de code réutilisée » —
  affirmation **non vérifiable** sans lire du code GPL, hors périmètre).
  Ce manifeste **ne préjuge pas** de la licité de cette filiation
  documentaire ; la vérification objective de similarité d'expression est
  recommandée au propriétaire comme **tâche d'audit séparée**, confiée à
  un rôle distinct des implémenteurs (R9.4). Le présent registre n'enregistre
  **aucune** reprise touchant de près ou de loin ce projet.
- `codeide-lsp` : dépôt propriétaire jjoblab — les reprises éventuelles
  (voie B/C) porteront la mention d'origine et l'auteur déclarés.

## 6. Journal du manifeste

| Date | Événement |
|---|---|
| 2026-10-30 | Création du manifeste (Phase 0). Registres remplis : exclusions R9.3, 11 candidats évalués sans lecture du code, 5 dépendances d'artefacts. **Aucune copie effectuée.** |
| 2026-10-30 | Spike (`spike-kotlin/`) : l'ancien moteur **exécuté comme oracle en boîte noire** (S0, miroir du protocole `preparer-oracle.sh` de l'étude Java) — jar construit dans le clone local de `codeide-lsp`, posé hors dépôt, jamais commité ; aucun fichier de production lu. Artefacts JetBrains/Kotlin téléchargés pour mesure uniquement. |
| 2026-10-30 | **Vendoring amont EFFECTUÉ (spike uniquement)** : grammaire `compiler/multiplatform-parsing/common/src` @ tag v2.3.0 (`f95cb2f7`) obtenue par `git archive` depuis le clone amont (JAMAIS depuis codeide-lsp — R9.2 du prompt), posée dans `spike-kotlin/src/main/vendor/` avec LICENSE.txt/COPYRIGHT.txt du même tag et notice `VENDOR.md`. Vérification d'exception de licence : `license/README.md` @ v2.3.0 ne liste AUCUN fichier de ce chemin (grep vide). Usage : mesure S2 — ce n'est PAS la grammaire d'un moteur de production ; le vendoring de production suivra la même procédure à la vague 0 après décision ADR 0100. |
| 2026-10-30 | **Décision du propriétaire REÇUE** (canal de mission : « Je t'autorise à commencer les étapes, tu dois travailler uniquement sur ta branche ») — ADR 0100 ACCEPTÉE (voie C), ADR 0103 ACCEPTÉE (voie (i)). La reprise voie C est OUVERTE ; les zones noyau restent en lecture interdite (clean-room maintenu). |
| 2026-10-30 | **Vendoring de production EFFECTUÉ (étage 0)** : grammaire ré-obtenue par `git archive` @ tag v2.3.0 (`f95cb2f7693a15966fd8fd9b1d78d440545e7b66`) depuis le clone amont, posée dans `kotlin/src/main/vendor/` avec LICENSE.txt/COPYRIGHT.txt et notice VENDOR.md — **identité octet pour octet vérifiée par `diff -r` contre le vendoring du spike** (même tag, même procédure). Exception de licence revérifiée au tag. Notices embarquées au jar (META-INF/vendor-kotlin/). La dérive de contenu est gardée par la suite de parité (digeste structurel). |
| 2026-10-30 | **Contrôle R9.4 MENÉ sur les 9 fichiers des zones C** (lecture des en-têtes/KDoc, recherche de mentions tierces) : AUCUN en-tête tiers, AUCUNE mention GPL, AUCUN crédit amont ; seules références externes = grammaire vendorée + kotlin-metadata-jvm (Apache-2.0). Réserve P-K1 (D5 → synthetics) consignée §3. **Inscriptions préalables R1–R9 posées §3 ; AUCUNE copie effectuée à ce jour** (copie différée aux étages de câblage : étage 1 pour classpath/synthetics, étage 5 pour le confort). |
| 2026-10-30 | **COPIES R4/R5 EFFECTUÉES (vague 1d — classpath + index appelables)** : re-contrôle R9.4 au moment de la copie (en-têtes/KDoc des 2 fichiers relus sur le clone à bc11253 — AUCUNE mention tierce, AUCUN écart depuis le contrôle initial). Destinations : `kotlin/src/main/kotlin/jo/codeide/lsp/kotlin/classpath/DecodeurMetadonnees.kt` et `…/classpath/LecteurClassBrut.kt` (§3 mis à jour avec les chemins). Adaptations de câblage SEULEMENT, listées §3 : paquet jo.codeide.lsp.kotlin.classpath, imports retéléphonés vers le SPI `api/` et le modèle neuf `index/`, en-tête d'origine posé, références KDoc d'entités de l'ancien dépôt retirées/retéléphonées — le corps est repris tel quel (diff documenté dans le commit). Les tests des fiches 002–005 sont NEUFS (jamais copiés — consultation seule, R9.1). Dépendance `org.jetbrains.kotlin:kotlin-metadata-jvm:2.2.10` activée (§4 — empreinte mesurée AVANT adoption : AUCUNE référence sensible ART, ServiceLoader OK descripteur vérifié, dex pile complète 24 372 méthodes / 3,56 Mo — seuils tenus). |
| 2026-10-30 | **COPIES R1/R2 EFFECTUÉES (vague 1e — synthétiques, clôture du périmètre étage 1)** : re-contrôle R9.4 au moment de la copie (en-têtes/KDoc des 2 fichiers relus sur le clone à bc11253 — AUCUNE mention tierce, AUCUN écart depuis le contrôle initial). Destinations : `kotlin/src/main/kotlin/jo/codeide/lsp/kotlin/synthetics/AnalyseRessourcesAndroid.kt` et `…/synthetics/ClassesSynthetiques.kt` (§3 mis à jour avec les chemins). Adaptations de câblage SEULEMENT, listées §3 : paquet jo.codeide.lsp.kotlin.synthetics, imports du noyau ancien retirés au profit du modèle local NEUF `DeclarationProjet`/`GenreDeclaration` (fichier associé inscrit §3 R2 — la frontière C de l'inventaire §3 identifiait cette dépendance : jo.lspkotlin.resolve.*, zone noyau en lecture interdite, jamais lue), en-têtes d'origine posés, références KDoc d'entités de l'ancien dépôt retéléphonées vers ScanProjetParesseux — les corps sont repris tels quels (diffs documentés dans le commit). Tests NEUFS (26 comportements — AUCUN test source dédié n'existe dans le cahier pour la zone synthetics/, la capacité y est éprouvée par les fiches 039/040/045/047 des étages 2–3). **P-K1 (R6.5 — signalé factuellement)** : la recommandation d'audit de similarité (rôle distinct des implémenteurs) était posée AVANT la reprise de la zone ; le propriétaire a ordonné la poursuite de l'étage 1 (canal, 30/10) sans audit préalable — la copie est effectuée SOUS RÉSERVE, la recommandation DEMEURE OUVERTE et s'exerce désormais a posteriori (les diffs de câblage documentés au commit facilitent cet audit). |
| 2026-10-30 | **ÉTAGE 2 OUVERT — vague 2a (socle de résolution), AUCUNE reprise** : l'ordre de poursuite reçu du propriétaire (« Go étage 2 », 30/10) ouvre l'étage résolution + diagnostics. La vague 2a est du code NEUF intégral (zone `resolve/` neuve : modèle de symboles projet, lecteur Java maison, univers des preuves, marche typée, journal explain ; passe sémantique et germe de complétion en `session/`) — les zones NOYAU anciennes `resolve/` et `diagnostics/` restent en lecture interdite R9.1 (jamais consultées pour cette vague ; les TESTS sources des fiches 017/022/006 ont été consultés R9.1/R9.5 pour les FIXTURES, jamais copiés). `verifier-provenance.sh` rc=0. |
