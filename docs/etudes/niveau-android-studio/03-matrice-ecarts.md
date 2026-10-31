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
| G-001 | Projet Android | ~~« cannot be resolved » sur `R.*`, binding, `BuildConfig` avant le premier build ; complétion muette sur `R.layout.`/`binding.`~~ | Fabrique light (R + imbriquées InnerClasses, BuildConfig, `*Binding`, BR) servie ENTRE sources et jars — la source générée réelle PRIME (anti-doublon testé) ; `DeclassementDatabinding` RETIRÉ (D6) | B9 (classes light) | Quotidien × bloquant = **maximal** | livré (V-B1/V-B3) | Doublons light/générés : ORDRE DE REPLI verrouillé | `:model` (buildFeatures — ADR 0020) | moteur | **P0 — RÉSOLU (V-B, 31/10)** |
| G-002 | Diagnostics | Diagnostics périmés ou divergents selon le chemin (rapide/complet/froid/chaud) | Équivalence rapide↔complet non prouvée sur corpus réel ; report non re-validé ; H6 | A1 (dirty scope par bloc) | Élevé (confiance dans le rouge) | 6–10 (P1 + correctifs) | Régression latence | aucun | moteur | **P0** |
| G-003 | Diagnostics | `B.java` ouvert garde des erreurs périmées après édition de l'API de `A.java` | Pas de graphe de dépendances de fichiers ni de republication des dépendants ; H4 | A1 (FileStatusMap + re-highlight) | Élevé | 8–12 | Coût du graphe mémoire | aucun | moteur | **P0** |
| G-004 | Complétion | Suggestions insérées qui cassent le code (imports manquants, syntaxe) | `additionalTextEdits` partiellement branché ; échec de classification non mesuré ; H9/M6 | A2 (insert handlers) | Moyen × confiance | 4–6 (P6 + correctifs) | — | aucun | moteur | **P0** |
| G-005 | Diagnostics | Rouge ecj sur du code qui compile avec javac (builds AGP), ou silence sur du code cassé | Divergences ecj↔javac jamais mesurées sur corpus réel ; H11 | A7 (Javac quirks) | Moyen (fidélité) | 3–5 (P3, réutilisation corpus G-036) | — | aucun | moteur | **P0** |
| G-006 | Projet Android | ~~Ressources XML totalement inconnues : pas de navigation, hover, complétion `R.string.`~~ | Référentiel livré (V-B1/V-B2) : parseur pull sans dépendance, values/ids/styleables 3 familles/layout* par configuration, MAJ ciblée didChange XML ; scan p95 95 ms / peson 126 Ko (banc) | B8 (resource repositories) | Quotidien × bloquant | livré (V-B1/V-B2) | Empreinte mesurée : peson < 2 Mo tenu ×16 | `:model` (res/) | moteur | **P1 — RÉSOLU (V-B, 31/10)** |
| G-007 | Projet Android | ~~Idem G-001 côté binding : `ActivityMainBinding` rouge avant build~~ | Couvert par G-001 — livré V-B3 : nommage PascalCase+Binding, view/data binding, BR, supers résolus par sonde (repli Object) ; ÉQUIVALENCE vs simulacre AGP : champs/types/méthodes 0 écart | B9 (ViewBinding light : nullabilité par configuration) | Élevé | livré (V-B3) | Nullabilité par configuration : types fusionnés (View si incohérent) — annotations différées aux inlay (V-C) | — | moteur | **P1 — RÉSOLU (V-B, 31/10)** |
| G-008 | Projet Android | ~~Aucun contrôle « API level » ; activation binding inconnue ; variante mal routée~~ | `:model` v11 (ModuleAndroidLsp liste B10 + empreinte) + dérivateur disque KTS/Groovy tolérant (V-B1) + surcharge sync `remplacerContexteModule` (la sync PRIME, R3) | B10 (modèle Gradle) | Structurant (alimente G-001/006/011) | livré (V-B1) | Contrat hôte à versionner : champ `android` du protocole v11 | hôte (producteur sync), `:model` | moteur + hôte | **P1 — RÉSOLU moteur (V-B, 31/10) ; producteur hôte = prompt §4 du plan** |
| G-009 | Multi-langage | ~~Déclarations Kotlin invisibles du Java avant build~~ | Scanner lexical conservateur livré (V-B3) : types 1er niveau + facades `*Kt` (signatures CERTAINES seulement — le reste ÉCARTÉ, jamais de faux positif) ; drapeau de repli | — (la référence lit les métadonnées Kotlin) | Élevé (projets modernes mixtes) | livré (V-B3) | Périmètre moteur Kotlin hors champ (respecté) | index partagé | moteur | **P1 — RÉSOLU (V-B3, 31/10) ; rappel mesuré sur fixture oracle, complémentation corpus = V-B4** |
| G-010 | Inspections | ~~Ni nullité, ni code mort, ni contrôles style/bogue au-dessus du compilateur~~ | Livré (V-C1) : contrat + profil + suppressions + 8 inspections pilotes (StringEquality, IgnoreResultOfCall @CheckResult, RedundantCast, EqualsHashCode, SizeReplaceableByIsEmpty, StaticFieldReferenceOnSubclass, WrongPackageStatement, FormatString) — Warning/Info JAMAIS Error, provenance "inspection", survie à la frappe confinée (P1), repli documenté | A4 (contrat + profil) | Élevé (qualité perçue AS) | livré (V-C1) | DFA nullité et unused global : NON livrés (deuxième vague du catalogue, après V-C3) | aucun | moteur | **P1 — RÉSOLU (V-C1, 31/10)** |
| G-011 | Lint Android | ~~`NewApi` absent : appel nécessitant un API level > minSdk non signalé~~ | Livré (V-C2, première vague) : base `api-versions.xml` en tâche de fond (script + propriété, repli honnête muet) + NewApi (échappatoires @RequiresApi/@TargetApi/gardes SDK_INT), InlinedApi, ObsoleteSdkInt, MissingSuperCall, SetTextI18n ; @SuppressLint ; garde erreurs de résolution | B13 (NewApi + base API) | Élevé (spécifique Android) | livré (V-C2, 1re vague) | RESTENT : Range, ResourceType, CommitPrefEdits, Recycle, global différé, segment CIDX compact | `:model` | moteur | **P1 — RÉSOLU en 1re vague (V-C2, 31/10) ; reste catalogue** |
| G-012 | Complétion | Pas de filtrage par type attendu, pas de chain/statement/2e appel | `typeAttendu` limité aux enums/déclarations ; H10 | A2 (smart/chain/statement) | Élevé | 10–16 | Latence (régime à prévoir) | aucun | moteur | **RÉSOLU (V-C3 — ADR 0026 livrée : smart par bindings, union à l arité, chaîne 2 sauts, statement lexicale, 2e/3e appels)** |
| G-013 | Navigation | ~~`references` rend le document courant seulement (usages incomplets)~~ | Confinement **protocolaire** de parité stricte §10.4 — le moteur est déjà multi-fichiers ; H8 | A5 (find usages groupés) | Élevé × correctif minuscule | **0,5–1** (retrait du filtre + ADR + tests) | Décision de rupture de parité (propriétaire) | client (présentation groupée) | moteur + décision | **P1 — RÉSOLU (V-E, 31/10)** |
| G-014 | Actions | Peu de quick-fixes (21 IDs → 7 familles), aucun `source.*`/`refactor.*`, pas d'intentions sans diagnostic | Catalogue borné N6 ; H7 | A3 (context actions) | Élevé | 10–18 (élargissement par familles + kinds) | — | client (présentation kinds) | moteur | **P1** |
| G-015 | Actions | Pas de génération de code (getters/setters, constructeur, equals/hashCode/toString, override) | Absent ; H7 | A3 (generate) | Élevé (usage quotidien) | 6–10 | — | aucun | moteur | P2 |
| G-016 | Refactorings | Pas d'extraction (méthode/variable/constante/paramètre), ni inline, ni change signature, ni safe delete | Absent ; H7 | A5 | Élevé mais secondaire après G-015 | 12–20 | Complexité conflits | aucun | moteur | P2 |
| G-017 | Complétion | `@IntDef`/`@StringDef`, littéraux `R.string.` dans les chaînes, valeurs d'annotation : muets | Absent ; H10 | B12 (typedef completion) | Moyen (effet démonstratif) | 3–5 | — | G-006 (ressources) | moteur | **RÉSOLU (V-C3 — typedef T1/T2/T3 en tête + import statique ; littéraux R.* par @…Res + référentiel ; bornes : typedef binaires muets — noms absents du .class)** |
| G-018 | Complétion | Pas de statistiques d'usage ; noms de variables peu pertinents | Absent ; H10 | A2 (stats + suggestions de noms) | Moyen | 4–6 (compteur local opt-in + heuristiques noms) | Vie privée (local uniquement) | aucun | moteur | **RÉSOLU (V-C3 — CompteurUsage fenêtre glissante 30 j, peseur D9, opt-in à la construction + noterSelectionCompletion ; ML rejeté v1)** |
| G-019 | Protocole | Pas de coloration sémantique (`semanticTokens`) | Non annoncé (M2, H15) | A6 | Moyen (rendu) | 6–10 | Volume de calcul ; delta client | client (consommation) | moteur + client | P2 |
| G-020 | Actions | `source.organizeImports` non exposé au fil LSP | API moteur existante non branchée (M3, H7) | A3 | Petit coût × usage réel | **0,5** | — | client (palette) | moteur | P2 |
| G-021 | Complétion | `completionItem/resolve` ne résout rien (identité) | Stub ; H15 | A2 | Faible (charges la réponse) | 1–2 | — | client | moteur | P3 |
| G-022 | Protocole | Pas de `workspace/willRenameFiles` (répercussion des renames de fichiers sur imports) | Absent ; H15 | A5 | Moyen | 2–4 | — | client (geste rename fichier) | moteur + client | P2 |
| G-023 | Diagnostics | Pas de `relatedInformation`/`codeDescription`/provenance (ecj vs inspection vs lint) | Type moteur pauvre ; H5/H15 | A4/B14 | Moyen (UX, filtrage) | 2–3 | — | client (rendu) | moteur | P2 |
| G-024 | Complétion | `extends`/`implements` muets pendant leur frappe en tête de déclaration (M1) | Verrou fiche 007 | A2 | Petit | 1–2 | Arbitrage cahier | — | moteur + décision | **RÉSOLU (V-C3 — décision D7 : tête de déclaration TOP_NIVEAU, mots-clés fusionnés aux stubs ; fiche 007 + RetoursAppareilV116Test amendés, ECARTS-SERVEUR M1 fermé)** |
| G-025 | Complétion | Lambda ouvert après le caret : variante rejetée (M6) | Queue équilibrée heuristique ; H9 | A2 | Faible (rare) | 0,5–1 | — | — | moteur | P3 |
| G-026 | Navigation | ~~`documentHighlight` sans distinction lecture/écriture~~ | ~~Surbrillance non typée ; H8~~ (erreur d'audit : le typage existait depuis N4 ; le trou réel = composées/incréments hors pont `visit(Assignment)` — correction annotée audit §1.8) | A5 | Moyen | 1–2 | — | client (styles) | moteur | **P2 — RÉSOLU (V-E, 31/10)** |
| G-027 | Navigation | ~~Pas de navigation `R.id.x` ↔ layout, `@string/x` ↔ code, manifest ↔ classe~~ | Livré (V-B2) : `R.layout.x`/`R.id.x`/`R.string.x`/`R.styleable.X_a`/`binding.champ` → XML déclarant à la plage EXACTE du nom (ressource fichier = plage vide, ouverture) | B11 | Élevé (après G-006) | livré (V-B2) | — | G-006 | moteur | **P2 — RÉSOLU (V-B2, 31/10) ; manifest ↔ classe = V-G** |
| G-028 | Refactorings | Pas de rename de ressource (propagation code + XML) | Prérequis livré (référentiel V-B) ; le rename multi-langage reste à V-D | B11 | Moyen | 3–5 (V-D) | Collisions | G-006 ✅ | moteur | P2 — OUVERT (renvoyé à V-D) |
| G-029 | Concurrence | ~~Une complétion attend un diagnose d'un autre fichier ; lectures non parallèles~~ | **Livré (V-F2 — ADR 0029 phase 2, schéma B ADR 0010)** : verrous par périmètre (orchestration → compilation → services → feuilles), comptage `synchronized(` 99 → 0 (cible < 6, métrique CI), mémo/complétion chaude/sondes hors verrou, balayages libres, pool plafonné 2–3 (fiche 048 amendée) ; chaos test 6 fils + banc de charge ADR 0010 verts ; PontEcj sans état mutable partagé | A1 (passes parallélisées) | Moyen (régularité p99) | livré (V-F2) | Interblocement : ordre global documenté + chaos tests (dump en échec) | aucun | moteur | **P2 — RÉSOLU (V-F2, 31/10)** |
| G-030 | Performance | ~~« Se fige » 11–12 s aux premières frappes post-sync (reconstruction froide)~~ | ~~Pas de double-tampon de l'environnement de noms ; H14~~ **Livré (V-F1)** : double-tampon + époques — l'ancien moteur SERT pendant la construction/réchauffement du neuf (swap atomique, rejeu des requêtes coupées, republication au swap) ; mesuré JVM ×1,20 p50 / ×0,58 p95 pendant fenêtre tenue (budget ≤ 2× tenu) ; appareil = gabarit 07 (propriétaire) | — (axe de dépassement §0.1-3/5) | Élevé (perçu appareil) | livré (V-F1) | Mémoire ×2 transitoire (acceptée ADR 0029 R3 — budget V-F phase 3) | hôte (moment de la sync — déjà le cas) | moteur | **P2 — RÉSOLU (V-F1, 31/10) ; mesure appareil au gabarit 07** |
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
