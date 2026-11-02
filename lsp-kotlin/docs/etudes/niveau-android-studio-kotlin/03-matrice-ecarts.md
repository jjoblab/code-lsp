# 03-matrice-ecarts — Écarts avec Android Studio (mode K2), priorisés

> Mission « niveau Android Studio Kotlin », Phase 3 — date : 2026-10-31.
> Une ligne par écart `GK-###`. Colonnes (miroir du format de la mission
> jumelle Java `03-matrice-ecarts.md`) : domaine · symptôme utilisateur ·
> cause racine (renvoi KH/mesure de l'[audit](01-audit-moteur.md)) ·
> référence Android Studio ([02-reference](02-reference-android-studio.md)) ·
> impact (fréquence × gravité pour un développeur Android Kotlin) ·
> effort **[estimé]** en j-h agent (méthode « socle + marginal » du
> `PLAN-REECRITURE.md` §3, bornes 1–5 h/comportement, socles ≤ 20 j-h ;
> vélocité observée N1–N2 : 0,087–0,147 h agent/j-h postulé) · risque ·
> dépendances (hôte `codeide-lsp`, client `cel-lsp`/code-editor, `:model`,
> moteur Java, propriétaire) · responsable · priorité.
>
> **Légende de priorisation** : **P0** = rend le moteur *faux* (faux
> positifs, incohérences) ou *invisible* (bascule non faite) ;
> **P1** = intelligence visible (typage, Android réel, smart/chain,
> interop, Compose, Gradle) ; **P2** = couverture et confort (catalogue,
> actions, protocole) ; **P3** = option/perf pure. Tri interne par impact
> décroissant.

## §1 P0 — ce qui rend le moteur faux ou invisible

| # | Domaine | Symptôme utilisateur | Cause racine | Réf. AS | Impact | Effort [estimé] | Risque | Dépendances | Resp. | Prio |
|---|---|---|---|---|---|---|---|---|---|---|
| GK-002 | Fiabilité | 981 erreurs rouges **fausses** sur le propre code compilable du moteur : `it` 603, imports implicites `kotlin.*` ~100, `this` 48, segments racines ~60, `T` 11, `valReassign` 36 (affectation indexée `cible[clé] = valeur`), `argumentCount` 38, `unsafeNullable` 23 | KH3 CONFIRMÉ PAR LA MESURE : P1 auto-corpus 28,05 FP/1 000 l. (audit §4.3) ; frontière de preuve ≠ frontière du langage | §A1/A3 | Quotidien × bloquant = **maximal** — un faux rouge détruit la confiance dans TOUT le moteur | 8–14 (V-A : familles it/imports implicites/this/racines/T, valReassign indexé) | Régression du silence utile (l'excès inverse) | aucune | moteur | **P0** |
| GK-027 | Intégration | Aucune capacité du moteur n'est visible : le client CodeIDE consomme toujours l'ancien LSP Kotlin | KH16 : serveur livré NON EN SERVICE, bascule I0–I6 non commencée, pack non publié | §D | Toutes × invisible = **structurant** | 3–6 (miroir I1 Java) + publication | Dépend à l'arbitrage A/B hôte (I1 Java) | hôte (`:lsp:host`), client, propriétaire | moteur + hôte | **P0** |
| GK-034 | Qualité | Aucune vérité terrain : bancs saturés (complétion 1,000/16 positions), fidélité sur 86 lignes | KH3/KH13 — corrigé par le V0 livré (banc P1–P9 + corpus + mesure 28,05) | §A3 | Transverse (conditionne le pilotage de TOUS les écarts) | livré (V0) ; +4–7 (étiquetage 50+ positions, split train/éval, escalade stricte) | Dérive du banc (leçon AL1 Java) | CI | moteur | **P0** |
| GK-020 | Diagnostics | Rouge pauvre : 10 familles `kt.*` là où la référence en rend des centaines (surcharges, contraintes, exhaustivité `when`, suspend hors contexte…) | KH2 ; catalogue figé par parité ancien moteur | §A3 (tableau ~65) | Quotidien × moyen | 12–20 (par vagues APRÈS la baseline FP < 0,5 — ADR 0125) | Faux positifs : chaque code entre sous garde FP/1 000 | palier 2 pour une partie (ADR 0123) | moteur | P0→P2 (gated) |
| GK-028 | Publication | Le pack Kotlin n'est pas publié (`publish.yml` = coordonnées Java seulement) | Q-K4 ouverte ; DÉBLOQUÉ depuis la levée de P-K2 | §D | Condition mécanique de la bascule | 1–2 | aucune (mécanisme existant à étendre) | CI | moteur | **P0** (préalable GK-027) |

## §2 P1 — intelligence visible

| # | Domaine | Symptôme utilisateur | Cause racine | Réf. AS | Impact | Effort [estimé] | Risque | Dépendances | Resp. | Prio |
|---|---|---|---|---|---|---|---|---|---|---|
| GK-001 | Typage | `list.map { it.nom }`, `Flow<T>`, `State<T>`, `Map<K,V>`, chaînes génériques perdent leur type | KH1 : `TypeValeur` à 2 cas (TypageBorne.kt:19-26) | §A1 | Quotidien × majeur (toue la couche IDE en dépend) | 24–40 (V-B1→B3, ADR 0123) | Faux positifs par sur-inférence → différentiel oracle + ambiguïté = silence | garde dex (0133) | moteur | **P1** |
| GK-003 | Typage | Constructions fréquentes muettes : scope functions, DSL, lambdas à récepteur | KH4 : ni surcharges, ni inférence, ni récepteurs implicites | §A2 | Quotidien × majeur | inclus dans GK-001 (V-B3) | idem | idem | moteur | **P1** |
| GK-012 | Complétion | Pas de smart (type attendu), pas de chain (extensions applicables) | KH6 : 6 genres, INCONNU = vide ; manque type attendu/récepteur | §A2, KC-01/02 | Quotidien × majeur | 10–16 (V-D, APRÈS GK-001) | classement mensonger si type faux | palier 2 | moteur | **P1** |
| GK-013 | Complétion | Muet dans les DSL (Gradle, Compose) : récepteurs implicites non typés | KH4/KH9 | §A2/B12, KC-03 | Quotidien × majeur | inclus GK-001 + GK-018 | idem | palier 2 | moteur | **P1** |
| GK-006 | Android | Code généré invisible après build (Room `*_Impl`, Hilt `Hilt_*`, Safe Args) : complétion/navigation muettes, `build` exclu du scan | KH8 : ScanProjetParesseux.kt:654 | §B9 | Quotidien × majeur (projets KSP) | 6–10 (V-E2, ADR 0127) | conventions AGP mouvantes | `:model` v2 Java (ADR 0020/D2) | moteur | **P1** |
| GK-007 | Android | Plugins de compilateur non modélisés (Compose, Parcelize, serialization, allopen) : ni support ni contradiction | KH8 | §B9 | Hebdo × moyen | 4–8 (V-E3, déclaratif borné) | sur-promesse sémantique → silence documenté | palier 2 (Compose) | moteur | **P1** |
| GK-008 | Android | Modèle projet sans champs Android (namespace, minSdk, res/, buildFeatures, processeurs) | mutualisation D2 non livrée | §B12 | Structurant (ressources, lint NewApi…) | 4–7 (V-E1, consommation `:model` v2) | dépendance au rythme Java | `:model` (ADR 0020) | moteur + mission Java | **P1** |
| GK-016 | Compose | Aucune intelligence : contexte `@Composable` muet, `Modifier`, `remember` absents | KH10 | §B10, KC-07 | Quotidien (projets Compose) × majeur | 6–10 (V-E3) | annotation du classpath à décoder | palier 2, décodeur | moteur | **P1** |
| GK-017 | Coroutines | Contexte `suspend` muet (appel hors coroutine non signalé), `Flow` non typé | KH10 + KH1 | §B11, KC-08 | Quotidien × moyen | 3–5 (avec V-B) | faux positifs hors preuve | palier 2 | moteur | **P1** |
| GK-009 | Interop | Arité Java jamais contrôlée, nullabilité androidx invisible, types plateformes absents | KH11 : LecteurDeclarationsJava.kt:313, :106-113 | §A7/B14, KC-09 | Quotidien (projets mixtes) × moyen | 5–8 (V-E interop) | faux positifs sur code Java réel | — | moteur | **P1** |
| GK-010 | Interop | Sens Kotlin→Java absent : le moteur Java lit un scanner lexical, le moteur Kotlin n'écrit pas le segment CIDX | Q-K5/ADR Java 0022 | §A7 | Moyen × moyen | 6–10 (V-G) | contrat partagé à synchroniser | moteur Java (ADR 0022) | moteur + mission Java | **P1** |
| GK-011 | Interop | Rename/navigation inter-langage inexistants (`.java` exclus du rename) | KH11 : OccurrencesProuvees.kt:144-147 | §A5/A7 | Moyen × moyen | 4–8 (V-I, phases partagées) | renommage partiel interdit (ferroviaire) | GK-010 | moteur + mission Java | **P1** |
| GK-018 | Gradle | `build.gradle.kts`/`libs.versions.toml` : noms nus muets, aucune complétion/navigation | KH9 | §B12, KC-12 | Quotidien × moyen | 8–14 (V-G, ADR 0130) | faux positifs DSL → garde nus muets tant que non prouvés | — | moteur | **P1** |
| GK-019 | Gradle | Catalogue de versions invisible (versions dupliquées, clés inconnues non signalées) | KH9 | §B12 | Hebdo × moyen | inclus GK-018 | idem | — | moteur | **P1** |

## §3 P2 — couverture et confort

| # | Domaine | Symptôme | Cause racine | Réf. AS | Impact | Effort [estimé] | Risque | Dépendances | Resp. | Prio |
|---|---|---|---|---|---|---|---|---|---|---|
| GK-014 | Complétion | `when` exhaustif (enum/sealed), `override`/stubs absents | KH6 | §A2, KC-04/05 | Quotidien × confort | 4–7 | exhaustivité exige clôture (sealed) | palier 2 partiel | moteur | P2 |
| GK-015 | Complétion | Pas de frontière RPC de classement : `x-codeide.completionRanking` non annoncée (le serveur sert l'espace entier, non filtré) | KH15/journal 09 §7.1 | §A2, ADR 0126 | Moyen × confort (UX client) | 1–3 | contrat client à versionner | client | moteur + client | P2 |
| GK-021 | Diagnostics | `@Suppress`/tags/sévérités non servis, pas de relatedInformation | KH2 | §A3/A4, KC-07 Java C-07 | Moyen × moyen | 3–6 | grammar de suppression à borner | — | moteur | P2 |
| GK-022 | Actions | `source.organizeImports` inexistant (la plus demandée des actions `source`) | KH7 : genre SOURCE jamais produit | §A4, KC-10 | Quotidien × confort | 2–4 | suppression d'import utile (garde : import de complétion conservé) | — | moteur | P2 |
| GK-023 | Actions | Aucune intention sans diagnostic (expression↔block, `?.let`, if↔when, gabarit…) | KH7 | §A4, KC-11 | Quotidien × confort | 8–14 (V-F) | prévisualisation client | client (kinds) | moteur | P2 |
| GK-024 | Actions | Aucun refactoring (extraction, inline, safe delete) | KH7 | §A5 | Moyen × moyen | 10–16 (V-F) | conflits multi-fichiers | WorkspaceEdit déclaratif (ADR 0115) | moteur | P2 |
| GK-035 | Inlay | Hints sans récepteurs implicites `this`/`it`, sans chaînes | KH15-adjacent | §A6, KC-06 | Quotidien × confort | 2–4 | hints jamais faux (effacement sans preuve) | palier 2 | moteur | P2 |
| GK-025 | Protocole | semanticTokens non annoncé (couleur de repli client) | dette D3 | §A6, ADR 0136 | Moyen × confort | 4–8 | fiabilité au parseur seul — fondé sur palier 2 | palier 2, client | moteur + client | P2 |
| GK-026 | Protocole | Hiérarchies, codeLens, documentColor, onTypeFormatting, linkedEditingRange absents | KH15 | §A6 | Moyen × confort | 6–12 (V-I) | coût protocole | client | moteur + client | P2 |
| GK-036 | Protocole | Diagnostics non enrichis (relatedInformation, provenance palier) | KH15-adjacent | §A3 | Moyen × faible | 1–3 | — | client | moteur + client | P2 |
| GK-038 | Fiabilité | Fraîcheur inter-fichiers sans empreintes (édition de A → diagnostics de B recalculés au guichet seulement) | schéma Java ADR 0025 non mutua- lisé | §A7 | Moyen × moyen | 3–6 (V-A2) | coût STAT par édition | mission Java (0025) | moteur | P2 |
| GK-037 | Mesures | Aucune mesure appareil ART (latences perçues, GC, thermique) | E-K2 | §D | Transverse | livré gabarit 07 + kit existant ; exécution propriétaire | aucune (mesure) | propriétaire (appareil) | propriétaire | P2 (transverse) |

## §4 P3 — option / performance pure

| # | Domaine | Symptème | Cause racine | Réf. AS | Impact | Effort [estimé] | Risque | Dépendances | Resp. | Prio |
|---|---|---|---|---|---|---|---|---|---|---|
| GK-004 | Budget | 81 % du plafond méthodes / 68 % du budget Mo : l'ambition consomme la marge | KH12/E-K8 | §C | Transverse | décision (ADR 0133) + packs 6–12 | lever le plafond sans mesure ART = imprudent | propriétaire | moteur + propriétaire | P3 (gouvernance) |
| GK-005 | Profondeur | Aucune soupape de fidélité compilateur (mode profond jamais construit) | KH5 | §A1 | Faible fréquence × fort (cas limites) | instruit (ADR 0124) ; 8–16 SI arbitré | hôte (Q-K7 : environnement déporté) | propriétaire | moteur + hôte + propriétaire | P3 |
| GK-029 | Navigation | Usages par re-parse : jamais mesurés à l'échelle (47 000 l.) | KH14 | §A7 | Moyen × faible si cibles tenues | mesure V0 (P7) puis 8–14 si index requis | coût mémoire/disque | gabarit 07 | moteur | P3 |
| GK-030 | Navigation | Pas d'index d'usages persistant (Q-K1) | KH14 | §A7 | Faible si GK-029 tient | inclus GK-029 | — | `:index` | moteur + propriétaire | P3 |
| GK-031 | Perf | Caches bornés en unités (pas en octets), `onTrimMemory` absent | audit §3.11 | §D | Appareil × moyen | 3–5 (V-A) | — | hôte (SPI mémoire) | moteur | P3 |
| GK-032 | Perf | GC ART, dumb mode garanti non mesurés | E-K2 | §D | Appareil × moyen | 3–6 (V-F) | — | gabarit 07 | moteur | P3 |
| GK-033 | Perf | Budgets par palier d'appareil non définis | ADR 0117 partiel | §D | Appareil × faible | 2–4 (V-F) | — | gabarit 07 | moteur | P3 |

## §5 Table de traçabilité ADR ↔ écarts

| ADR | Écarts couverts |
|---|---|
| 0122 (paliers) | GK-001, GK-002, GK-003, GK-005 |
| 0123 (typage palier 2) | GK-001, GK-003, GK-012, GK-013 |
| 0124 (profond) | GK-005 |
| 0125 (catalogue diagnostics) | GK-002, GK-020, GK-021 |
| 0126 (complétion) | GK-012, GK-013, GK-014, GK-015 |
| 0127 (modèle Android/généré) | GK-006, GK-007, GK-008 |
| 0128 (Compose/coroutines) | GK-016, GK-017 |
| 0129 (interop) | GK-009, GK-010, GK-011 |
| 0130 (Gradle KTS) | GK-018, GK-019 |
| 0131 (actions/refactorings) | GK-022, GK-023, GK-024 |
| 0132 (usages/échelle) | GK-029, GK-030 |
| 0133 (budget/packaging) | GK-004 |
| 0134 (perf ART) | GK-031, GK-032, GK-033 |
| 0135 (qualité mesurée) | GK-002, GK-034 |
| 0136 (protocole/bascule) | GK-025, GK-026, GK-027, GK-028, GK-036 |

**Total : 38 écarts** (P0 : 5 — dont 1 livré V0 et 1 préalable mécanique ;
P1 : 14 ; P2 : 11 ; P3 : 8). Effort cumulé estimé des vagues (hors V0
livré et décisions) : **149–264 j-h agent** — enveloppe cohérente avec la
mission Java (145–241), majorée par le palier 2 typage qui n'a pas
d'équivalent Java. À recaler après la première vague (discipline des
recalages).
