# 03 — Matrice d'écarts et priorisation (Phase 3)

> Une ligne par écart `G-###`. Colonnes : domaine · symptôme utilisateur · cause
> racine (renvoi H# du `01-audit-moteur.md`, avec preuve) · référence Android Studio
> (renvoi fiche du `02-reference-android-studio.md`) · impact (fréquence × gravité
> pour un dev Android) · effort (j-h agent, méthode du `PLAN-REECRITURE.md` §3 :
> socle + n × marginal, bornes 1–5 h/comportement, socles ≤ 20 j-h) · risque ·
> dépendances (hôte `codeide-lsp`, client `cel-lsp`/`code-editor`, `:model`) ·
> responsable · priorité.
>
> **Échelle de priorité** (règles du prompt §6) : **P0** = rend le moteur *faux*
> (faux positifs, incohérences, insertions cassées) · **P1** = rend le moteur
> *aveugle* (projet Android invisible) puis intelligence visible · **P2** = couverture
> et confort · **P3** = optionnel/perf pure. À l'intérieur de chaque priorité, tri par
> impact décroissant.

## 1. Tableau des écarts

| # | Domaine | Symptôme utilisateur | Cause racine | Réf. AS | Impact | Effort (j-h) | Risque | Dépendances | Resp. | Prio |
|---|---|---|---|---|---|---|---|---|---|---|
| G-001 | Projet Android | « cannot be resolved » sur `R.*`, binding, `BuildConfig` avant le premier build ; complétion muette sur `R.layout.`/`binding.` | Aucune synthèse de classes générées — seul un déclassement HINT (`DeclassementDatabinding.kt:36`) ; H1 | B9 (classes light) | Quotidien × bloquant = **maximal** | 12–20 (socle fabrique + R + BuildConfig) | Doublons light/générés (règle de priorité à spécifier) | `:model` (buildFeatures — ADR 0020) | moteur | **P0** |
| G-002 | Diagnostics | Diagnostics périmés ou divergents selon le chemin (rapide/complet/froid/chaud) | Équivalence rapide↔complet non prouvée sur corpus réel ; report non re-validé ; H6 | A1 (dirty scope par bloc) | Élevé (confiance dans le rouge) | 6–10 (P1 + correctifs) | Régression latence | aucun | moteur | **P0** |
| G-003 | Diagnostics | `B.java` ouvert garde des erreurs périmées après édition de l'API de `A.java` | Pas de graphe de dépendances de fichiers ni de republication des dépendants ; H4 | A1 (FileStatusMap + re-highlight) | Élevé | 8–12 | Coût du graphe mémoire | aucun | moteur | **P0** |
| G-004 | Complétion | Suggestions insérées qui cassent le code (imports manquants, syntaxe) | `additionalTextEdits` partiellement branché ; échec de classification non mesuré ; H9/M6 | A2 (insert handlers) | Moyen × confiance | 4–6 (P6 + correctifs) | — | aucun | moteur | **P0** |
| G-005 | Diagnostics | Rouge ecj sur du code qui compile avec javac (builds AGP), ou silence sur du code cassé | Divergences ecj↔javac jamais mesurées sur corpus réel ; H11 | A7 (Javac quirks) | Moyen (fidélité) | 3–5 (P3, réutilisation corpus G-036) | — | aucun | moteur | **P0** |
| G-006 | Projet Android | Ressources XML totalement inconnues : pas de navigation, hover, complétion `R.string.` | Aucun référentiel de ressources (`.java` seulement, H1/H2 : `AnnuaireClasspath.kt:385`, `IndexMotsSources.kt:128`) | B8 (resource repositories) | Quotidien × bloquant | 10–16 (parseur + référentiel + MAJ incrémentale) | Empreinte XML sur ART | `:model` (res/) | moteur | **P1** |
| G-007 | Projet Android | Idem G-001 côté binding : `ActivityMainBinding` rouge avant build | Couvert par G-001 (fabrique light) — ligne distincte pour le suivi | B9 (ViewBinding light : nullabilité par configuration) | Élevé | 6–10 (spécifique binding, après G-001) | Règles de nullabilité complexes | — | moteur | **P1** |
| G-008 | Projet Android | Aucun contrôle « API level » ; activation binding inconnue ; variante mal routée | `ModuleLsp` sans namespace/minSdk/buildFeatures/… ; H3 | B10 (modèle Gradle) | Structurant (alimente G-001/006/011) | 6–10 (champs + dérivation disque + contrat hôte) | Contrat hôte à versionner | hôte (producteur sync), `:model` | moteur + hôte | **P1** |
| G-009 | Multi-langage | Déclarations Kotlin invisibles du Java avant build | Filtre `.java` ; H2 | — (la référence lit les métadonnées Kotlin) | Élevé (projets modernes mixtes) | 8–14 (index léger de déclarations `.kt` publiques) | Périmètre moteur Kotlin hors champ (instruction 30/10) | index partagé | moteur | **P1** |
| G-010 | Inspections | Ni nullité, ni code mort, ni contrôles style/bogue au-dessus du compilateur | ADR 0013 option 1b jamais exécutée ; ~10 options ecj seulement ; H5 | A4 (contrat + profil + catalogue 40) | Élevé (qualité perçue AS) | 10–16 (contrat + 10 inspections pilotes) | FP si DFA mal bornée | aucun | moteur | **P1** |
| G-011 | Lint Android | `NewApi` absent : appel nécessitant un API level > minSdk non signalé | Pas de lint maison, pas de base `api-versions.xml` ; B13 | B13 (NewApi + base API) | Élevé (spécifique Android) | 8–12 (base + 5 contrôles mono-fichier) | Taille base (~5,3 Mo → compacter) ; minSdk (G-008) | `:model` | moteur | **P1** |
| G-012 | Complétion | Pas de filtrage par type attendu, pas de chain/statement/2e appel | `typeAttendu` limité aux enums/déclarations ; H10 | A2 (smart/chain/statement) | Élevé | 10–16 | Latence (régime à prévoir) | aucun | moteur | **P1** |
| G-013 | Navigation | ~~`references` rend le document courant seulement (usages incomplets)~~ | Confinement **protocolaire** de parité stricte §10.4 — le moteur est déjà multi-fichiers ; H8 | A5 (find usages groupés) | Élevé × correctif minuscule | **0,5–1** (retrait du filtre + ADR + tests) | Décision de rupture de parité (propriétaire) | client (présentation groupée) | moteur + décision | **P1 — RÉSOLU (V-E, 31/10)** |
| G-014 | Actions | Peu de quick-fixes (21 IDs → 7 familles), aucun `source.*`/`refactor.*`, pas d'intentions sans diagnostic | Catalogue borné N6 ; H7 | A3 (context actions) | Élevé | 10–18 (élargissement par familles + kinds) | — | client (présentation kinds) | moteur | **P1** |
| G-015 | Actions | Pas de génération de code (getters/setters, constructeur, equals/hashCode/toString, override) | Absent ; H7 | A3 (generate) | Élevé (usage quotidien) | 6–10 | — | aucun | moteur | P2 |
| G-016 | Refactorings | Pas d'extraction (méthode/variable/constante/paramètre), ni inline, ni change signature, ni safe delete | Absent ; H7 | A5 | Élevé mais secondaire après G-015 | 12–20 | Complexité conflits | aucun | moteur | P2 |
| G-017 | Complétion | `@IntDef`/`@StringDef`, littéraux `R.string.` dans les chaînes, valeurs d'annotation : muets | Absent ; H10 | B12 (typedef completion) | Moyen (effet démonstratif) | 3–5 | — | G-006 (ressources) | moteur | P2 |
| G-018 | Complétion | Pas de statistiques d'usage ; noms de variables peu pertinents | Absent ; H10 | A2 (stats + suggestions de noms) | Moyen | 4–6 (compteur local opt-in + heuristiques noms) | Vie privée (local uniquement) | aucun | moteur | P2 |
| G-019 | Protocole | Pas de coloration sémantique (`semanticTokens`) | Non annoncé (M2, H15) | A6 | Moyen (rendu) | 6–10 | Volume de calcul ; delta client | client (consommation) | moteur + client | P2 |
| G-020 | Actions | `source.organizeImports` non exposé au fil LSP | API moteur existante non branchée (M3, H7) | A3 | Petit coût × usage réel | **0,5** | — | client (palette) | moteur | P2 |
| G-021 | Complétion | `completionItem/resolve` ne résout rien (identité) | Stub ; H15 | A2 | Faible (charges la réponse) | 1–2 | — | client | moteur | P3 |
| G-022 | Protocole | Pas de `workspace/willRenameFiles` (répercussion des renames de fichiers sur imports) | Absent ; H15 | A5 | Moyen | 2–4 | — | client (geste rename fichier) | moteur + client | P2 |
| G-023 | Diagnostics | Pas de `relatedInformation`/`codeDescription`/provenance (ecj vs inspection vs lint) | Type moteur pauvre ; H5/H15 | A4/B14 | Moyen (UX, filtrage) | 2–3 | — | client (rendu) | moteur | P2 |
| G-024 | Complétion | `extends`/`implements` muets pendant leur frappe en tête de déclaration (M1) | Verrou fiche 007 | A2 | Petit | 1–2 | Arbitrage cahier | — | moteur + décision | P3 |
| G-025 | Complétion | Lambda ouvert après le caret : variante rejetée (M6) | Queue équilibrée heuristique ; H9 | A2 | Faible (rare) | 0,5–1 | — | — | moteur | P3 |
| G-026 | Navigation | ~~`documentHighlight` sans distinction lecture/écriture~~ | ~~Surbrillance non typée ; H8~~ (erreur d'audit : le typage existait depuis N4 ; le trou réel = composées/incréments hors pont `visit(Assignment)` — correction annotée audit §1.8) | A5 | Moyen | 1–2 | — | client (styles) | moteur | **P2 — RÉSOLU (V-E, 31/10)** |
| G-027 | Navigation | Pas de navigation `R.id.x` ↔ layout, `@string/x` ↔ code, manifest ↔ classe | Conséquence de G-006 | B11 | Élevé (après G-006) | 3–5 | — | G-006 | moteur | P2 |
| G-028 | Refactorings | Pas de rename de ressource (propagation code + XML) | Conséquence de G-006 | B11 | Moyen | 3–5 | Collisions | G-006 | moteur | P2 |
| G-029 | Concurrence | Une complétion attend un diagnose d'un autre fichier ; lectures non parallèles | Moniteur d'instance (46 synchronized dans `MoteurJava.kt`) + worker unique ; H13 | A1 (passes parallélisées) | Moyen (régularité p99) | 8–12 (ADR 0010 schéma B enfin livré) | Interblocage (ordre global à documenter + tests chaos) | aucun | moteur | P2 |
| G-030 | Performance | « Se fige » 11–12 s aux premières frappes post-sync (reconstruction froide) | Pas de double-tampon de l'environnement de noms ; H14 | — (axe de dépassement §0.1-3/5) | Élevé (perçu appareil) | 6–10 | Mémoire ×2 transitoire | hôte (moment de la sync) | moteur | P2 |
| G-031 | Performance | Chaque frappe re-résout l'unité (réduite) complète | Pas d'incrémentalité ; H14 ; ADR 0006 vague java-syntax non livrée | A1 (dirty scope) | Moyen (déjà amorti par SourceFocuser) | 8–12 (conditions artefacts JetBrains — ADR 0017) | Double parseur | — | moteur | P3 |
| G-032 | Protocole | Indexation invisible pour l'utilisateur (pas de `workDoneProgress`) | Absent ; H15 | A6 (« scanning/analyzing » visible) | Moyen (attente incomprise) | 1–2 | — | client (rendu progression) | moteur + client | P2 |
| G-033 | Mémoire | `onTrimMemory` non respecté côté moteur | ADR 0011 point 5 non exécuté | — | Moyen (pression appareil) | 1–2 | — | hôte (relayeur du signal) | moteur + hôte | P2 |
| G-034 | Qualité | Aucune vérité terrain « niveau AS » : bancs saturés (18 positions, 1,000/1,000), oracle = ancien moteur | H11 | A2/A4 (métriques FP/FN, corpus) | **Structurant** (conditionne P0/P1) | 5–8 (vague 0 — livrée dans cette mission) | — | aucun | moteur | **P0** (transverse) |
| G-035 | Hors moteur | Classpath réel à t+123,9 s ; churn de sessions ; databinding généré non indexé côté éditeur | Écarts hôte H1/H3/H4 + client K2/K3 de `ECARTS-SERVEUR.md` | — | Élevé appareil | (hors ce dépôt — prompts séparés) | — | hôte/client | hôte/client | suivi |

## 2. Lecture priorisée

- **P0 — rendre le moteur *vrai*** (vague V-A du plan §04) : G-002, G-003, G-004,
  G-005 s'appuient tous sur **G-034** (la vérité terrain, livrée en vague 0) ; G-001
  est le seul P0 « produit » à la fois P0 (faux positifs massifs avant build) et
  fondation de toute la couche Android (avec G-006/G-008).
- **P1 — rendre le moteur *voyant* puis intelligent** : le bloc Android
  (G-006/007/008/009, l'axe « il ne connaît pas le code ») puis la couche IDE
  (G-010/011/012/014). **G-013 est l'arbitrage le moins cher à plus gros gain
  immédiat** (retrait d'un filtre + ADR de rupture de parité) — décision demandée D1.
- **P2 — couverture et confort** : génération de code, extractions, protocole
  (semanticTokens, willRenameFiles, relatedInformation), navigation ressource (après
  G-006), perf structurelle (G-029/030).
- **P3** : incrémentalité parsing (conditionnée ADR 0017), petites réparations
  complétion (M1/M6), resolve identité.

## 3. Règles de traçabilité

Chaque `G-###` est relié à : sa preuve d'audit (`01-audit-moteur.md` §1/§3), sa
référence (`02-reference-android-studio.md`), et l'ADR qui le résout :

| ADR | Écarts couverts |
|---|---|
| 0019 — modèle de code tolérant & contexte de complétion | G-004, G-024, G-025, G-031 |
| 0020 — modèle de projet Android + référentiel de ressources | G-006, G-008 (+ alimente 001/007/011) |
| 0021 — classes « light » Android | G-001, G-007, G-027, G-028 |
| 0022 — interopérabilité Kotlin → Java | G-009 |
| 0023 — moteur d'inspections et profil | G-010 |
| 0024 — contrôles Android Lint à valeur Java | G-011 |
| 0025 — cohérence des diagnostics | G-002, G-003, G-023 |
| 0026 — complétion niveau Android Studio | G-012, G-017, G-018, G-024 |
| 0027 — actions de code étendues | G-014, G-015, G-016, G-020 |
| 0028 — recherche et navigation exhaustives | G-013, G-026, G-027, G-028 |
| 0029 — performance, concurrence, mémoire sur ART | G-029, G-030, G-031, G-033 |
| 0030 — qualité mesurée « niveau AS » | G-034, G-005 |
| 0031 — surface protocole | G-019, G-021, G-022, G-032 |
