// Build racine de code-lsp (vague 0 — squelette ; vague 1b — outillage repris).
// Les versions des plugins Kotlin sont déclarées ICI uniquement ; les modules
// les appliquent sans version (plugins { kotlin("jvm") }).
//
// Kotlin 2.2.10 (vague 1b) : aligné sur la chaîne d'origine des modules repris
// de codeide-lsp — kotlinx-serialization 1.9.0 exige un lecteur de métadonnées
// Kotlin 2.2 (1.10+ exigerait Kotlin 2.3) ; 2.1.20 ne sait pas la lire.
plugins {
    kotlin("jvm") version "2.2.10" apply false
    kotlin("plugin.serialization") version "2.2.10" apply false
}

// Version du dépôt (ADR 0002 : réécriture intégrale — nouvelles bases).
// Étage N8 : la ressource `version.properties` embarquée dans le serveur
// (fiche 059, I7) est GÉNÉRÉE depuis cette valeur — serverInfo.version du
// protocole et version du dépôt restent synchronisées par construction.
// 1.0.0 : BASCULE §8.5 SIGNÉE le 30/10/2026 (gabarit
// docs/GABARIT-BASCULE-APPAREIL.md — checklist 6/6, passe appareil moto g06
// 18/18 / 0 saut, deltas arbitrés 103/62/0).
// 1.1.0 : INTÉGRATION CODEIDE (lsp-java/docs/INTEGRATION-CODEIDE.md §3.3, option B
// arbitrée) — constructeur VIVANT ServeurLangageJava(environnement) :
// routeur multi-modules AU PROTOCOLE (résolution par requête, replaceAll
// à chaud, jamais de re-initialize, invariant ADR 0211) ; contrat de
// sérialisation superDefinition ALIGNÉ sur le vendord {path, start, end,
// label} (miroir cel-lsp — aucune adaptation client requise) ; fiche 066.
// 1.1.2 : incident ART du 30/10 au soir (CodeIDE 0.204.1, moto g06) —
// AccesJrt.ouvrirDepuisJarExterne capture Throwable, PAS seulement
// Exception : sur ART, le fournisseur jrt du lib/jrt-fs.jar du JDK
// externe lève ServiceConfigurationError (un Error) à l'instanciation
// (internes JDK absents d'ART — jdk.internal.jimage) ; l'ancienne garde
// laissait l'Error fuir, la construction paresseuse d'EnvironnementNoms
// explosait à la PREMIÈRE requête et le serveur répondait « Internal
// error. » à TOUT (complétion, surlignage, inlay, diagnostics muets).
// Désormais repli silencieux vers le régime instantané documenté (null),
// la plateforme restant couverte par les entrées réelles du classpath
// (android.jar du modèle). §10 d'INTEGRATION-CODEIDE.
// 1.1.3 : retours appareil du 30/10 au soir (logs codeide-logs-2026-10-30-
// 025629.zip, CodeIDE 0.204.2) — la chaîne est FRANCHIE mais trois familles
// restaient muettes : (1) les extensions maison voyagent sous leur nom
// COMPLET « textDocument/superDefinition » / « textDocument/decompiled-
// Source » (le @JsonRequest nu sur ServiceExperimental enregistrait le nom
// nu — le client cel-lsp appelle le nom complet, la requête était rejetée
// « Unsupported request method ») ; (2) familles analytiques ÉLARGIES —
// locale inutilisée allumée sur la table RAPIDE (corps hôte intact : les
// usages d'une locale vivent dans SA méthode, aucun fallacieux) et champ/
// méthode/type privé inutilisé sur la table ANALYTIQUE, avec PORTAGE HORS
// CORPS pendant la frappe (DiagnoseRapide.portablesHorsCorps : l'import et
// le membre privé inutilisés RESTENT affichés — la passe rapide ne peut pas
// les recalculer honnêtement, le masquage casserait leur analyse d'usages) ;
// (3) déclencheurs de complétion « . » ET « @ » (miroir EXACT du vendord —
// la complétion d'annotation s'arme à la frappe).
// 1.1.4 : passe appareil 0.204.3 (logs codeide-logs-2026-10-30-105910.zip)
// — l'appareil vivait en RÉGIME INSTANTANÉ : sur ART le jrt du JDK externe
// est ininstanciable (v1.1.2) ET l'ancienne sonde du régime plein ne voyait
// QUE le jrt, alors que le classpath porte android.jar (plateforme java.*
// + android.*). La sonde devient EnvironnementNoms.plateformeResolvable()
// — java.lang.Object résoluble par le jrt OU par un jar porteur : les ~30
// gardes, diagnose (tables analytiques « unused ») et completer repassent
// en régime plein sur l'appareil. Régression ART simulée verrouillée :
// PlateformeClasspathARTTest (8 tests, faux jdk + jar de plateforme extrait
// du jrt — les TROIS signaux « unused » publiés, définition/documentHighlight
// non vides, paquets proposés à l'import, sur le fil LSP4J). Étude
// éditeur↔LSP (mandat propriétaire) : docs/etudes/editeur-lsp/. §12
// d'INTEGRATION-CODEIDE.
// 1.1.5 : banc PROJET ANDROID RÉEL (retours appareil du 31/10, logs
// codeide-logs-2026-10-30-124450.zip — « je propose que tu crées un test
// projet android complet (avec de vrais jar, aar) et testé les
// fonctionnalités ») : cinq correctifs moteur issus du premier run —
// qualifier imbriqué (`R.layout.a|` proposait 0 item : chaînage `$` +
// sonde pointée), auto-import MUET devenu additionalTextEdits (l'insertion
// au point d'imports, jamais si inutile), marqueur de splice en fuite
// (`__codeide_completion__()` dans l'inventaire à préfixe vide), identifiant
// isolé en corps de méthode (SingleTypeReference de recovery ECJ → le
// contexte NOM : la locale en tête, le popup Android Studio). Montage :
// lsp-java/bancs/projet-android/ (réplique monip) + sources générées +
// scripts/preparer-projet-android.sh (android.jar 34-ext12 + 16 AARs/jars
// réels, fermeture transitive vérifiée classe par classe) +
// BancProjetAndroidReelTest (20 tests : timeline appareil rejouée au
// protocole — didOpen avant sync, diagnostics du cache, sync,
// LIVE-REPUBLISH — puis chaque famille de complétion). Validation : 20/20,
// :java:test 521/0/0 gates ouvertes, benchQualite baseline SIGNÉE intacte
// (1,000/1,000/1,000, p95 36,7 ms), benchUsages 1,000/43 ms. §13
// d'INTEGRATION-CODEIDE.
// 1.3.0 : VAGUE V-E — NAVIGATION/RÉFÉRENCES (GO propriétaire D1–D9 du
// 31/10 ; ADR 0028, décision D1 : abandon de la parité stricte §10.4).
// (1) LEVÉE DU CONFINEMENT `references` : la réponse rend les usages
// MULTI-FICHIERS réels du moteur (rappel = oracle au banc), conversion
// ligne/colonne par le repère du FICHIER PORTEUR (disque puis overlay) —
// la gate « taille = 1 » de la fiche 054 est INVERSÉE (borne 2 : la
// déclaration T.java ET l'appel U.java) ; capability maison versionnée
// `x-codeide.usages` (experimental) annonce la rupture. (2) TYPOAGE
// lecture/écriture COMPLET du documentHighlight : les affectations
// COMPOSÉES (`+=`) et les incréments (`x++`/`++x`) sont des ÉCRITURES
// (la hiérarchie ecj Assignment ← CompoundAssignment ← Postfix/Prefix
// route ces nœuds hors du pont visit(Assignment) — trois ponts dédiés
// posés dans LectureAst). (3) RPC maison `textDocument/usagesGroupes` :
// usages groupés par fichier (ADR 0028 (e)) avec plafond honnête 2 000
// — total exact, rendus, tronque TOUJOURS annoncés (R1 : jamais de
// troncature silencieuse). Validation : 564 tests / 0 échec (gates
// Android ouvertes), banc usages rappel 1,000 / p95 50,3 ms, banc
// qualité tenu (d8 p95 36,3 ms, 0 saut), P1–P8 strict 0 violation.
// (Le 1.2.0 est SAUTÉ délibérément : valeur fantôme du vendord,
// proscrite par la fiche 059 I7 — test serverInfoVersionEstCelleDuDepot.)
// 1.4.0 : VAGUE V-B — PROJET ANDROID (ADR 0020/0021/0022, décisions
// D2/D3/D6 du 31/10). (1) CONTRAT :model v11 (ModuleAndroidLsp, liste
// B10, empreinte étendue, protocole ClasspathModuleAndroid producteur
// hôte) + DÉRIVATEUR DISQUE KTS/Groovy tolérant (la sync PRIME, R3).
// (2) RÉFÉRENTIEL DE RESSOURCES : parseur XML pull sans dépendance
// (ART-compatible), values/ids @+id/styleables 3 familles/layout* par
// configuration, MAJ CIBLÉE sur didChange .xml (mémo invalidé — P8
// étendu aux ressources), octetsCompacts (peson CIDX : 126 Ko max sur
// le corpus, budget 2 Mo tenu ×16). (3) CLASSES LIGHT : R + imbriquées
// (attribut InnerClasses), BuildConfig, databinding.*Binding + BR —
// ordre de repli sources → SYNTHÉTIQUES → jars → jrt → stubs (la source
// générée réelle PRIME, anti-doublon R3 testé) ; équivalence light ↔
// simulacre AGP du banc : 0 écart sur l'API ; MainActivity SANS racine
// générée : zéro erreur. (4) NAVIGATION RESSOURCE : R.layout.x / R.id.x
// / R.string.x / R.styleable.X_a / binding.champ → XML déclarant à la
// plage EXACTE ; forme « liaison unique » des références qualifiées
// couverte dans SymboleAuCaret (gain pour TOUTE réf. qualifiée statique).
// (5) INTEROP KOTLIN (D3) : scanner lexical conservateur — types de
// premier niveau + facades *Kt, signatures CERTAINES seulement. (6) D6 :
// RETRAIT de DeclassementDatabinding + MoteurJava.declasserDatabinding
// (RUPTURE d'API — l'import .databinding RÉSOUT avant build ; fiche 051
// amendée « couvert par résolution »). Validation : 717 tests / 0 échec
// (java+model+index, gates Android ouvertes), banc ressources 172 res/ :
// scan p95 95 ms / MAJ ciblée 1,26 ms / peson 126 Ko (budgets ADR
// tenus), bancs qualité (d8 p95 31–50 ms, 1,000) et usages (1,000,
// < 100 ms) non régressés, P1–P8 strict 0 violation.
// 1.6.0 (31/10/2026, vagues V-F2 + V-C1/V-C2) : VERROUS PAR PÉRIMÈTRE
// (ADR 0029 phase 2 / ADR 0010 schéma B — le moniteur d'instance du moteur
// est remplacé par des verrous nommés, ordre global documenté, comptage
// synchronized 99 → 0 pour la cible < 6 ; ordonnanceur en POOL plafonné
// 2 par défaut, propriété jo.codeide.lsp.java.ordonnanceur.workers 1..3 ;
// chaos test multi-fils + banc de charge ADR 0010 : complétion A p95
// 33 ms concurrent vs 43 séquentiel, diagnose B 25 vs 19 — TENU) ;
// INSPECTIONS (ADR 0023 / G-010 : contrat + profil + suppressions + 8
// pilotes, provenance "inspection", jamais Error — la règle ADR 0013
// tient) ; LINT ANDROID (ADR 0024 / G-011 : base api-versions en tâche de
// fond, NewApi + InlinedApi + ObsoleteSdkInt + MissingSuperCall +
// SetTextI18n, @SuppressLint, provenance "lint", repli honnête sans
// base/contrat). Validation : 792 tests / 0 échec (tous modules ; 626
// java dont 41 gates ignorées avec raison), build strict P1–P8 VERT,
// bancs d8 (1,000 — p95 34,52 ms), usages (1,000 — p95 47,08 ms),
// qualité VERT.
// 1.7.0 (31/10/2026, vague V-C3 — ADR 0026 Accepté-livrée) : COMPLÉTION
// NIVEAU ANDROID STUDIO. (1) SMART — type attendu par bindings ecj
// (affectation, initialiseur, argument avec UNION DES SURCHARGES À L'ARITÉ
// de l'appel, return, ternaire transparent, lambda fonctionnalisée,
// allocation, cast) ; filtrage GRADUÉ par PeseurCompatibilite (compatible
// en tête / inconnu neutre / incompatible en bas / typedef domine /
// inaccessibles du 2e appel ferment la marche) — JAMAIS de cachage, le
// plafond honnête 120 et le total réel voyagent toujours. (2) TYPEDEF
// @IntDef/@StringDef (fiche C-04 T1/T2/T3) : constantes en tête, lues dans
// la SOURCE déclarante (bornes : typedef binaires muets), éléments simples
// avec import STATIQUE en édition additionnelle, éléments qualifiés
// insérés qualifiés (P6). (3) LITTÉRAUX R.* (B11/G-017) : genre
// LITTÉRAL_RESSOURCE par variante dédiée (littéral remplacé par le
// marqueur), 21 annotations @…Res mappées, noms du référentiel ADR 0020.
// (4) SECOND APPEL (fiche C-08 D1-D4) : compteur par (document, genre,
// ancre, PRÉFIXE) — 2e appel : membres inaccessibles MARQUÉS
// (data.marqueur) et DERRIÈRE (l'accessibilité JLS 6.6 est éprouvée dès le
// 1er appel — fiche 066 amendée) + membres STATIQUES IMPORTABLES (panneau
// java.lang borné, énumération mémoïsée, édition import static, P6) ;
// 3e appel : types du PROJET par l'index de noms partagé. (5) CHAÎNE à
// deux sauts (budgets 40/12). (6) STATEMENT completion lexicale (référence
// d'équilibrage v1.1.6 C2 réutilisée) : MoteurJava.completerInstruction +
// RPC textDocument/statementCompletion. (7) STATS LOCALES opt-in à FENÊTRE
// GLISSANTE (30 j, horloge injectée — format de fichier v2, l'ancien
// compteur est ignoré) branchées en peseur D9 + noterSelectionCompletion.
// (8) MÉMOÏSATION des membres de types BINAIRES (reste PV 1.6.0 mesuré
// 15-26 ms/appel) par identité de binding, niveaux d'accès consignés,
// verrou d'ÉTAT nommé (comptage synchronized( = 0 tenu). (9) D7/M1/G-024 :
// mots-clés extends/implements/permits proposés en TÊTE DE DÉCLARATION,
// fusionnés aux stubs (fiche 007 + RetoursAppareilV116Test amendés,
// ECARTS-SERVEUR M1 fermé). Capability x-codeide.completionRanking v2 (le
// poids packé gagne la dimension compatibilité — jamais de mutation
// silencieuse). Banc qualité v2 : corpus 24 positions (6 V-C3) avec SPLIT
// TRAIN/ÉVAL déterministe (ADR 0030) — d8 1,000/1,000/1,000, ÉVAL
// 1,000/1,000 (cibles ≥ 0,95/≥ 0,80 dépassées), naïf ÉVAL top-1 0,000 ;
// baseline re-signée (corpus v2) ; gel réinitialisé par position côté banc
// (scénarios indépendants). Validation : 818 tests / 0 échec (652 java
// dont 26 V-C3 ; 73 gates ignorées avec raison), build strict P1-P8 VERT,
// usages 1,000 / p95 51 ms, sauts 0.
// 1.8.0 (31/10/2026, vague V-D tranche 1 + V-C2 complément de catalogue —
// ADR 0027 Accepté-tranche 1 + ADR 0024) : ACTIONS DE CODE ET CATALOGUE
// LINT. V-D : (1) organizeImports EXPOSÉ au fil codeAction (M3/G-020) par
// le filtre `only` de LSP 3.17 (égalité ou préfixe hiérarchique) ; kinds
// annoncés source/source.organizeImports/source.generate/refactor.*/.
// (2) codeAction/resolve DIFFÉRÉ (resolveProvider, ModCommande.Differee :
// cle + arguments + uri dans data, éditions recalculées au resolve).
// (3) GÉNÉRATION DE CODE — les 6 capacités (G-015) : getter (is* boolean),
// setter (jamais sur final), couple, constructeur assignant les champs
// d'instance, equals/hashCode (java.util.Objects, == primitif, deepEquals
// tableaux, non servis si déjà déclarés), toString, stubs d'outrepassement
// des abstraites manquantes (@Override + valeur par défaut + TODO) —
// déclenchement ciblé, ancre des membres filtrée par bodyStart (piège
// mesuré : le constructeur PAR DÉFAUT d'ecj vit sur le nom du type).
// (4) INTENTIONS sans diagnostic (G-014) : if/else symétrique → ternaire
// (déballage des blocs mono-instruction), extraction de constante au
// littéral, lambda → référence de méthode (java.lang simple, sinon
// qualifié compilable), var ↔ type explicite (le type déclaré doit ÉGALER
// le type résolu — jamais de changement de sémantique). (5) FAMILLES ECJ
// ÉTENDUES +7 IDs (extraits du jar par javap) : stubs des abstraites
// manquantes (par bindings, arguments structurés), corps manquant (corps
// qui jette), retour de valeur en void (instruction entière supprimée),
// imports en conflit/dupliqués (ligne supprimée), cast inutile (alias
// nommé de l'inspection V-C1 « RedundantCast » — l'ID numérique garde la
// priorité, fiche 065 intacte) ; P5 étendu 7 fixtures 0 violation.
// V-C2 suite : Range (@IntRange/@FloatRange/@Size — arguments constants +
// retours annotés, taille des chaînes, cardinal des tableaux),
// ResourceType (famille R.<famille>.x vs @…Res — l'entier ordinaire jamais
// signalé), CommitPrefEdits (locales + chaînes nues sans commit/apply),
// Recycle (TypedArray/VelocityTracker, try-with-resources exempté) —
// socle PisteurLocales à piles vivantes (ecj ne pose pas de lien parent) ;
// catalogue 11 → 15 contrôles. SEGMENT CIDX compact CLBA de la base
// api-versions (re-compilable bit-à-bit, empreintes SHA-256, chemin rapide
// sur le .seg voisin, repli honnête si corrompu, outil CLI + script).
// Validation : 850 tests / 0 échec (684 java dont 15 V-C2 + 15 V-D ; 73
// gates ignorées avec raison), build strict P1–P8 VERT ; latence liste
// codeAction p95 MESURÉE 255 µs (budget < 30 ms TENU ×100).
// 1.9.0 (01/11/2026, vague V-D tranche 2 — ADR 0027 Accepté-tranche 2) :
// REFACTORINGS LOURDS — EXTRACT VARIABLE/CONSTANTE/MÉTHODE sur SÉLECTION
// (kinds refactor.extract.*, tous différés), INLINE variable/constante,
// SAFE DELETE par usages multi-fichiers exhaustifs (socle ADR 0028 étendu
// aux sites new X(…) et X::m). NOTE D'HONNÊTETÉ : la livraison b0bbcaf a
// documenté « version 1.9.0 » (PV, ADR, plan) SANS écrire la version ici —
// l'omission est réparée à la présente livraison (les deux tranches
// portent chacune leur entrée, la version courante saute à 1.10.0).
// Validation tranche 2 : 22 cas dorés, oracle safe-delete « 0 manqué »,
// latence liste p95 3 881 µs, 708 tests java / 0 échec.
// 1.10.0 (01/11/2026, vague V-D tranche 3 — ADR 0027 Accepté (trois
// tranches)) : CHANGE SIGNATURE (suppression de paramètre + re-écriture
// du GRAPHE DES APPELANTS multi-fichiers : envois, sites new X(…), appels
// explicites this(…)/super(…) ; refus motivés utilise/varargs/hierarchie/
// referenceMethode), SUPPRESSION HIÉRARCHIQUE (chaîne d'overrides ENTIERE
// — la outrépassée par bindings, les outrépassantes par balayage, garde
// d'usages par maillon, les appels internes à la chaîne disparaissent
// avec elle), INLINE DE CONSTANTE MULTI-FICHIERS (la garde mono-fichier
// de la tranche 2 est levée), CRÉATION DE CHAMP sur UndefinedField
// (`this.foo`/`x.foo` — type inféré par bindings de la valeur affectée,
// visibilité private/package/public selon le type déclarant, le fichier
// déclarant peut être DISTANT), RENOMMAGE DE RESSOURCE (G-028 :
// propagation code Java `R.<type>.<nom>` + XML — déclarations name=,
// références @type/, @+type/, ?type/, expressions R.* du data binding ;
// refus honnêtes fichier/styleable, collisions identifiant/nom-pris par
// le référentiel). resolveCodeAction sert la forme MULTI-URI (map
// fichier → éditions, convention file:// du renommage N5) ; garde V-D3 :
// le safe delete simple refuse désormais « hierarchie » quand un
// sous-type OUTREPASSE (la clé d'usages ne suit pas la hiérarchie
// descendante — un maillon orphelin ne compile pas, MESURÉ). Pièges ecj
// consignés : le drapeau varargs vit sur le BINDING (isVarargs), pas sur
// Argument.modifiers ; la « liaison unique » de R.<type>.<nom> lie le
// DERNIER segment dans `binding` (otherBindings NULL) ; un '@' de data
// binding n'est pas une référence (le scan XML avance d'un caractère).
// Validation : 860 tests / 0 échec (730 java dont 23 V-D3 ; 73 gates
// ignorées avec raison), build strict P1–P8 VERT ; latence liste AVEC
// refactorings étendus p95 MESURÉE 442 µs (budget < 30 ms TENU ×67).
// ---------------------------------------------------------------------------
// VERSION DU DÉPÔT (fiche 059 I7 — serverInfo.version générée d'ICI).
// 1.5.0 (31/10/2026, vague V-F1 — ADR 0029 phase 1) : DOUBLE-TAMPON +
// ÉPOQUES de l'environnement de compilation — 0 figage (l'ancien moteur
// sert pendant la construction/réchauffement du neuf, swap atomique),
// 0 réponse périmée (rejeu des requêtes coupées, bornes 3/8), annulation
// fine de base (points de contrôle complétion + balayage d'usages,
// EpoqueRetiree rejetée), republication des documents ouverts à chaque
// swap d'époque, repli documenté
// `jo.codeide.lsp.java.doubletampon.disabled`. Validation : 730 tests /
// 0 échec (tous modules ; 600 java dont 8 V-F1 + 1 banc ; 41 gates
// environnementales ignorées avec raison), `clean build` strict
// (P1–P8) VERT ; banc V-F1 : frappe pendant reconstruction tenue
// ×1,20 p50 / ×0,58 p95 du nominal (budget ADR ≤ 2× TENU), rejeu
// 2 passes (livraison par l'époque neuve) ; bancs d8 (1,000/1,000/1,000,
// p95 35,96 ms), usages (1,000, p95 40,62 ms), parité et N1
// (diagnose rapide p50 4,8 ms, tas 17 Mo) non régressés.
// 1.11.0 : VAGUES V-F FINALE + V-G (ADR 0029 phases 3–4 + ADR 0031 — la
// vague V-F est COMPLÈTE, la vague V-G est LIVRÉE) : V-F3 budgets d'OCTETS
// du cache de parse (coût estimé par fichier, garde anti-cyclage : la
// dernière entrée posée survit seule) et du mémo de localisation (négatifs
// évincés d'abord — re-probe honnête, contrats fiche 024 intacts) + shim
// onTrimMemory (pressionMemoire par niveau ComponentCallbacks2 miroir,
// relais multi-modules EnvironnementCompilation.pressionMemoire, rapport
// CHIFFRÉ, kill-switches memoire.budgets/ontrim.disabled) ; V-F4 PLAGE
// PRIORITAIRE (notification $/codeide.plageVisible v1 + double publication
// « visible d'abord » sur les grands fichiers ≥ SEUIL_PRIORITAIRE — passe
// prioritaire PURE (aucune écriture d'état), la complète fait foi, inert
// sans notification : contrats du cahier intacts, kill-switch
// plageprioritaire.disabled) ; V-F5 incrémentalité : report honnête
// conditionné (ADR 0017 (b) non clos, critère utile déjà tenu par le
// diagnose rapide fiche 014) ; V-G workDoneProgress d'indexation (jeton
// begin/report/end throttlé, préchauffage au creux initialized, G-032),
// diagnostics enrichis (data {provenance, code} machine-readable +
// relatedInformation « requis par X » sur la clause extends — fiche 019),
// willRenameFiles (package + imports du cas doré, fileOperations annoncé,
// didRenameFiles invalide), semanticTokens sur bindings (légende 8 types
// standard + 4 modificateurs, full sans delta, cas doré + latence p95
// mesurée < 25 ms), completionItem/resolve RÉEL (auto-import différé
// négocié par resolveSupport client — sans annonce, liste complète
// d'avant V-G), registre client K2 (lsp-java/docs/REGISTRE-CLIENT-K2.md).
// Validation : 919 tests / 0 échec (753 java dont 23 V-F3/V-F4/V-G ;
// 73 gates environnementales ignorées avec raison), `clean build` strict
// (P1–P8) VERT ; banc N1 tenu (démarrage à froid 1 011 ms, diagnose
// complet p50 13,8 ms, rapide p50 5,3 ms, hover p50 0,089 ms, complétion
// membres p50 6,8 ms, tas au repos 35 Mo) ; semanticTokens p95 mesuré
// 1 ms (cible ADR 0031 < 25 ms). §7.16/§7.25/§7.26/§9.5, ADR
// 0029/0031/0011 à jour.
// 1.12.0 : COMPLÉMENT DE CATALOGUE — les restes des familles unitaires
// (ADR 0032) : AJOUT de paramètres au site d'appel fautif (quickfix
// ID-FIRST ParameterMismatch 67108979 / UndefinedConstructor 134217858,
// IDs et arguments MESURÉS — types résolus des arguments excédentaires,
// valeur PAR DÉFAUT 0/false/null chez les autres appelants, import
// raccourci dans le fichier déclarant, le site fautif ne se retouche
// pas) ; RÉORDONNANCEMENT de paramètres (échange des fragments de
// signature + des arguments aux mêmes rangs chez chaque appelant —
// liaison expression↔paramètre préservée, le paramètre peut être
// utilisé) ; RENAME DEPUIS UN DOCUMENT XML (G-028, solde :
// prepareRename/rename sur déclaration name= des values et références
// @type/, @+type/, ?type/, data binding R.* — propagation code + XML,
// tampon ouvert en fait de foi, garde publicateur : aucun diagnostic
// Java pour un XML ouvert) ; codeDescription.href (G-023 ENTIÈREMENT
// résolu : provenance lint → page officielle VÉRIFIÉE sans ancre par
// ID, ecj/inspection sans href documenté). Validation : 900 tests /
// 0 échec (770 java dont 17 du complément ; 73 gates environnementales
// ignorées avec raison), `build` strict (P1–P8) VERT ; banc N1 tenu
// (démarrage à froid 893 ms, diagnose complet p50 13,1 ms, rapide
// p50 4,9 ms, hover p50 0,11 ms, tas au repos 35 Mo) ; latence liste
// p95 mesurée 410 µs (< 30 ms). §7.27, ADR 0032, PV
// mesures/2026-11-01-complement-catalogue.txt à jour.
// 1.12.1 : ROBUSTESSE DES RETOURS APPAREIL (V-A3 — logs CodeIDE
// 0.205.0 du 01/11/2026, codeide-logs-2026-11-01-144401.zip : serveur
// figé en pleine frappe — 38 complétions, 47 surbrillances et 29 inlay
// en timeout, plus AUCUN publishDiagnostics après une 2e invocation à
// la même ancre). Cause racine : les coûts FROIDS du chemin de
// complétion (annuaire du classpath — android.jar ~40 000 entrées — et
// LookupEnvironment ecj) payés PAR LA PREMIÈRE REQUÊTE qui les touche,
// SOUS le verrou de compilation : la diagnose s'empile derrière, la
// borne client de 5 s explose. Trois garde-fous : (1) PRÉ-CHAUFFE du
// classpath (MoteurJava.prechaufferClasspath — plateforme + annuaire
// phases 1-3 + sonde ecj) jouée par le réchauffeur du double-tampon ET
// à la première pose asynchrone de chaque moteur, construction
// paresseuse HORS du verrou global (les autres modules ne se bloquent
// plus) ; (2) BUDGET D'HORLOGE de complétion (3 s, sous la borne
// client ; expiration = PARTIEL honnête déjà collecté ; propriété
// jo.codeide.lsp.java.completion.budgetms) ; (3) CHIEN DE GARDE des
// requêtes lentes (window/logMessage Warning relayé par cel-lsp dans
// le journal applicatif — le point de lenteur devient VISIBLE sur
// l'appareil ; propriété jo.codeide.lsp.java.lenteur.seuilms, défaut
// 2 s). Consigné au passage : les faux positifs « I/O cannot be
// resolved » des mêmes logs viennent du FICHIER utilisateur (copie de
// la signature générique héritée avec ses variables de type — javac
// dirait pareil ; banc ReproGeneriquesHeritesTest). Validation : 783
// tests java / 0 échec dont 4 repros appareil (rafale + invocations ×3
// à la même ancre + diagnose concurrente + jar dupliqué + android.jar
// réel 43 Mo — invocations 1-3 à 92/118/67 ms), §7.28 USAGE à jour.
// 1.12.2 : CORRECTIF INTEROP KOTLIN, REPLI LEXICAL AVANT BUILD
// (complément V-A2 — primeSurJars règle l'APRÈS-build, pas le repli) —
// une `open class` Kotlin était synthétisée FINALE : toute classe Java
// l'étendant recevait « cannot subclass the final class » (cas réel du
// corpus AnySoftKeyboard : PackBroadcastReceiverBase, addons/base —
// masqué par le montage V-A2 qui fournit built_in_kotlinc, exposé par
// une passe de préparation SANS classes compilées). DeclarationsKotlin
// capte désormais le mot-clé et les modificateurs (open/abstract/
// sealed/data/value) : finale PAR DÉFAUT (sémantique Kotlin), SAUF
// open/abstract/sealed pour une class ; une interface n'est jamais
// marquée (ACC_FINAL y est sans objet) ; object/enum class et facades
// restent finales. Limite documentée (ADR 0022 complément 02/11) : les
// MEMBRES d'object/class Kotlin restent hors du scanner lexical
// top-level (jamais un type inventé). Validation : DeclarationsKotlin-
// Test au vert dont 2 régressions nouvelles (héritage Java depuis open
// class accepté / classe non ouverte refusée — fidélité conservée),
// régressions jar-prime de V-A2 inchangées.
// 1.13.0 : V-H — BRANCHEMENT DU RÉPARTISSEUR AU PROTOCOLE (ADR 0010 :
// « le scheduler 3 lanes est conservé », le chaînon que la réécriture
// avait laissé au sol). Retour appareil du 01/11/2026 (CodeIDE 0.205.2,
// moto g06, logs codeide-logs-2026-11-01-221628.zip) : « liaison. » et
// « greeter. » ne proposaient RIEN — la cause mesurée n'est PAS la
// résolution (la complétion liaison. A rendu 120 items par deux fois)
// mais la TEMPÊTE : le client émet complétion + inlayHint +
// documentHighlight (+ codeAction sur idle) à CHAQUE frappe, et CHAQUE
// requête était calculée SYNCHRONEMENT sur le fil de lecture lsp4j —
// la complétion patientait derrière TOUTE la file (29 s et 87 s
// mesurés côté serveur, timeout client 5 s : rien ne s'affichait). Le
// correctif : OrdonnanceurMoteur.soumettrePrioritaire (voies 0 FRAPPE
// complétion/signatureHelp/statementCompletion, 1 NAVIGATION, 2
// CONFORT ; supersession à VALEUR — la réponse VIDE honnête du type,
// jamais une annulation : le client attend une réponse ; la FRAPPE
// rend caduques les voies inférieures EN ATTENTE du même document ;
// exclusion par fichier toutes voies — contrat V-F2 inchangé) câblé
// sur 29 méthodes de document de ServeurLangageJava ; CAPTURE du
// document À LA SOUMISSION (cohérence coordonnées ↔ texte sous le
// nouveau parallélisme) + vérification d'époque à la livraison
// (invariant « 0 réponse périmée » tenu sous concurrence) ; exit()
// solde les requêtes en attente par la réponse vide. Validation :
// 959 tests / 0 échec (793 java dont 8 V-H ; 45 gates environnementales
// ignorées avec raison — corpus CORPUS_ANDROID absent, racines SDK
// réelles ; le banc projet RÉEL, lui, a tourné : libs Android
// téléchargées sans 429, liaison.s| → 2 items, préfixe vide → 120
// items) ; après rebase sur V-A2/V-A3 du distant, les repros appareil
// v1.12.1 (rafale, invocations ×3, freeze) passent AVEC le
// répartiteur V-H — pré-chauffe/budget/chien de garde attaquent le
// COÛT de chaque calcul, la répartition attaque la FILE : le chien
// de garde chronomètre désormais sur le WORKER du répartiteur ;
// verrous RepartitionPrioriteeVhTest (voies,
// supersession à valeur, frappe-caducité, exclusion par fichier, arrêt)
// + TempeteAppareilVhTest au PROTOCOLE (BoucleLocale : la tempête ne
// perd ni n'échoue aucune future, la complétion rend ses membres PENDANT
// la tempête, péremption honnête, arrêt propre). §7.29, ADR 0010 à jour,
// PV mesures/2026-11-02-vh-repartition.txt.
allprojects {
    version = "1.13.0"
}
