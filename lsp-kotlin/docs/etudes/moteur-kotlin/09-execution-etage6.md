# 09 — Exécution étage 6 : serveur LSP + protocole riche

> **Décision du propriétaire reçue le 2026-10-31** dans le canal de mission :
> « Go étage 6 » — les chantiers de l'étage 6 (plan §4 de `00-synthese.md` :
> serveur LSP + protocole riche) sont OUVERTS sur la branche
> `etude/moteur-kotlin`, à la suite de la clôture sur preuve mécanique de
> l'étage 5 (`08-execution-etage5.md` §9.4 : CI VERTE 10/10 sur le commit
> de clôture `4119dd5` — run `41157236378`, première tentative —, 41/41
> comportements confort (fiches 014/019/007/050), 5/5 cas dorés
> WorkspaceEdit, E-K8 sous seuils (dex d8 26 040 méthodes / 4,00 Mo),
> suite 477 tests, baseline parité re-signée 390→401). Le présent document
> est le journal d'exécution de l'étage (méthode : miroir des rapports
> précédents — chiffres étiquetés [mesuré] / [estimé], écarts signalés).
>
> Gouvernance inchangée : branche `etude/moteur-kotlin` seule ; zones NOYAU
> anciennes (`parse/`, `resolve/`, `diagnostics/`, `completion/`, `session/`,
> `api/` de production) en lecture interdite R9.1 — les chantiers NEUFS de
> l'étage s'y écrivent cahier en main. Les reprises voie C sont TOUTES
> livrées (R1–R8, manifeste §3) : l'étage 6 n'en ajoute AUCUNE — la couche
> protocole est du code NEUF (miroir du `:java` N8, ADR 0118 : le modèle,
> jamais le code Java — `:java` est en LECTURE AUTORISÉE pour le miroir
> architectural, les fichiers copiés restent nuls). Les tests sources
> anciens restent consultables (R9.1/R9.5) pour les FIXTURES, jamais copiés.

## 1. Périmètre de l'étage (plan §4 — cahier en main)

L'étage 6 est le dernier étage moteur du plan §4 : **Serveur LSP + protocole
riche** — périmètre : « Serveur LSP4J du module (miroir N8), capabilities
paritaires, époque, drapeau pack » ; critères de sortie : « Matrice
reproduite ; codes exacts ; 0 réponse périmée » ; effort 16–26 j-h
[estimé] ; dépend de V5 (clos).

**Distribution des 39 comportements restants** (consignée ici comme
promis §9.4 du journal 08 — la répartition suit la nature des fiches) :

| Fiche | Domaine | Comportements | Étage 6 / T | Motif |
|---|---|---:|---|---|
| 001 — ChargeurMoteurKotlin | cycle-vie-lsp | 3 | **étage 6** | verrouille le DESCRIPTEUR DE PACK (drapeau pack — critère de sortie) |
| 015 — CapacitesL2 | hover-inlay | 12 | **étage 6** | capacités moteur exposées au SPI, servies par le serveur (matrice) |
| 018 — FicheClasspath | hover-inlay | 11 | **étage 6** | idem : fiche classpath du survol + KDoc des candidates de signature |
| 051 — SessionSignature | hover-inlay | 12 | **étage 6** | l'aide à la signature est une capability ANNOCÉE de la matrice |
| 021 — MesuresPerf | caches-perf | 1 | **T transverse** | relève des BANCS (mesure, pas capacité) — journal T à venir |
| | | **39** | 38 + 1 | |

Le compte du cahier : 469 comportements, 430 livrés aux étages 0–5 ;
l'étage 6 en livre **38** (le solde de 1 — fiche 021 — revient au
transverse T, qui clôturera le programme avec les recalages).

| # | Item | Source du contrat |
|---|---|---|
| 1 | **Chargeur + descripteur de pack (DRAPEAU)** : la résolution ServiceLoader du moteur UNIQUE vérifiée MÉCANIQUEMENT ; le descripteur `version.properties` complet avec VALEURS RÉELLES (jamais les replis « inconnue » — un descripteur absent est un contrat de pack CASSÉ, le test le DIT) ; l'analyse via le SPI (arbre non vide, zéro erreur) | fiche 001 (3 comportements) |
| 2 | **Capacités L2 moteur — INDICES EN LIGNE** : noms de paramètres aux appels positionnels PROUVÉS (ancrés sur l'argument, libellé AVANT), argument nommé sans indice, types CERTAINS des déclarations sans type écrit (littéraux, constructeurs projet — ancrés sur le NOM), incertain SANS indice (composé, référence), `it` implicite du littéral (indice LAMBDA au libellé complet `it: Type`, ancré sur l'accolade ouvrante), paramètres explicites sans type écrit (un TYPE par nom), arité divergente sans indice, paramètre déjà typé sans indice, `it` par propriété typée et SAM du projet, plage BORNÉE, document inconnu VIDE | fiche 015 (8 comportements indices) |
| 3 | **Capacités L2 moteur — SYMBOLES D'ESPACE** : requête CONTIENT insensible à la casse sur les déclarations de PREMIER NIVEAU des documents ouverts ET du scan (locales JAMAIS), ordre par nom, requête sans réponse VIDE | fiche 015 (2 comportements symbolesEspace) |
| 4 | **Capacités L2 moteur — CHAÎNES DE SÉLECTION** : chaîne STRICTEMENT croissante du maillon le plus profond au FICHIER ENTIER (dernier maillon), plusieurs positions dans l'ordre, hors arbre = fichier SEUL (rien n'est inventé), document inconnu VIDE | fiche 015 (2 comportements selections) |
| 5 | **FICHE CLASSPATH du survol** : la carte structurelle d'un type du classpath au survol — genre (`public class/interface/enum/object`), héritage direct (`extends` — JAMAIS pour une interface), section `Interfaces :` (interfaces directes), `Constantes :` des enums (super `Enum` MASQUÉ), `Champs publics :` (le marqueur `INSTANCE` d'un objet exclu, les synthétiques `$` exclus, les privés exclus), plafonds d'affichage (8 champs, 20 constantes, « … »), binaire illisible = genre et champs SANS héritage ni interfaces (D3) | fiche 018 (6 comportements survol) |
| 6 | **AIDE À LA SIGNATURE (S1)** : candidates du PROJET (fonctions, surcharges — la signature ACTIVE est l'arité compatible, paramètres par défaut élargissant l'arité légale, constructeur primaire, membre vif de la classe englobante), lambda FINAL compte pour l'argument suivant, candidates du CLASSPATH (appelables de premier niveau par l'index RÉEL ; méthode JVM du récepteur qualifié par la vue), appel NICHÉ (le plus profond gagne), curseur après l'appel FERMÉ = silence, hors appel / document inconnu / session fermée = `null` ; la KDoc accompagne la candidate (document courant, cross-fichier FERMÉ par lecture disque bornée, cross-fichier OUVERT par l'arbre VIF — jamais le disque périmé), la candidate du CLASSPATH reste MUETTE (bytecode sans doc, D3) | fiches 051 (12) + 018 (5) |
| 7 | **LE SERVEUR LSP4J du module** (miroir N8 Java, ADR 0118 option 1) : cycle de vie (UNINITIALIZED → INITIALIZING → INITIALIZED → SHUTDOWN → EXITED, `exit` in-process ne tue JAMAIS la JVM), gardes protocolaires (requête avant `initialized` → `-32002` SYNCHRONE ; après `shutdown` → `-32600` ; second `initialize` → `-32600` ; notifications hors fenêtre IGNORÉES), **ÉPOQUE copy-on-write** (« 0 réponse périmée » — relance bornée à 3), capabilities PARITAIRES de la colonne Kotlin de la matrice (annoncées ET servies ; non-annoncées honnêtes), `serverInfo` du descripteur de pack, publication de diagnostics (didChange/didSave), conversion offsets ↔ positions UTF-16 | ADR 0118 ; matrice `codeide-lsp` colonne Kotlin ; `:java` N8 (fiches 055/056/058/059 — le MODÈLE) |
| 8 | **CAS DORÉS protocole** : les critères de sortie de l'étage assertés par la boucle locale JSON-RPC (serveur réel + client espion, allers-retours lsp4j AUTHENTIQUES) : codes d'erreur EXACTS, matrice paritaire VERROUILLÉE par test (miroir W4), 0 réponse périmée, publication de diagnostics | critères de sortie §2 |

Périmètre DÉJÀ VERT livré aux étages précédents (non refait ici) : tout
le moteur SPI (complétion classée, diagnostics, navigation, rename,
confort, previews, actions déclaratives) — le serveur les ENROBE dans le
protocole sans retraiter leurs contrats. Les requêtes NON annoncées
(`semanticTokens`, `codeLens`, hiérarchies, `onTypeFormatting`,
`linkedEditingRange`, `documentColor`) restent TOLÉRANTES (réponse vide,
jamais d'exception — miroir I9 Java) mais ABSENTES des annonces.

## 2. Critères de sortie de l'étage 6 (synthèse §4 + ADR 0118 — suivi)

| Critère | État | Preuve |
|---|---|---|
| **Matrice reproduite** : capabilities annoncées = colonne Kotlin de la matrice (annoncées/servies, non-annoncées honnêtes) — verrouillées par test de parité | à suivre (§6) | `CasDoresProtocoleKotlinTest` (matrice) |
| **Codes exacts** : `-32002` avant `initialized`, `-32600` après `shutdown` et second `initialize`, notifications hors fenêtre ignorées | à suivre (§6) | `CasDoresProtocoleKotlinTest` (gardes) |
| **0 réponse périmée** : époque copy-on-write, relance bornée, jamais de réponse calculée contre un texte révolu | à suivre (§6) | `CasDoresProtocoleKotlinTest` (époque) |
| Fiches 001 + 015 + 018 + 051 vertes (38 comportements) | à suivre (§3–§5) | `Fiche001…` : 3 ; `Fiche015…` : 12 ; `Fiche018…` : 11 ; `Fiche051…` : 12 |
| E-K8 : dex d8 pile chaude sous seuils (≤ 32 768 méthodes / ≤ 6 Mo) — lsp4j rejoint la pile | à suivre (§7) | scan + dexage au bac à sable, ALERTES §4 actualisée |

## 3. Plan de vagues (cahier en main — l'ordre suit les dépendances : le drapeau pack d'abord (il verrouille le descripteur que le serveur servira), les capacités moteur ensuite (le serveur les enrobe), la couche protocole enfin (elle consomme TOUT))

| Vague | Périmètre | Fiches | Comportements |
|---|---|---|---:|
| **6a** | CHARGEUR + DESCRIPTEUR DE PACK : les accès SPI du descripteur (`identifiant`, `version`, `versionGrammaire`, `versionMinimaleApp` — lus de la ressource générée `version.properties`, repli « inconnue » JAMAIS silencieux : le test le dénonce) ; la résolution ServiceLoader éprouvée mécaniquement | 001 | 3 |
| **6b** | CAPACITÉS L2 MOTEUR : indices en ligne (PARAMETRE aux appels prouvés, TYPE des déclarations certaines, LAMBDA des `it` implicites), symboles d'espace (contient, premier niveau, ouverts + scan, ordre par nom), chaînes de sélection (strictement croissantes, fichier dernier maillon) — SPI `indices`/`symbolesEspace`/`selections` + moteurs neufs | 015 | 12 |
| **6c** | FICHE CLASSPATH DU SURVOL : la carte structurelle (genre, héritage, interfaces, constantes, champs publics, plafonds 8/20, binaire illisible borné) — extension du lecteur de membres (drapeaux de classe et de membres portés) + câblage au survol des types du classpath | 018 (survol) | 6 |
| **6d** | AIDE À LA SIGNATURE : le moteur d'aide (appel englobant le plus profond, candidates projet/surcharges/constructeurs/membres vifs, arité compatible, paramètre actif, lambda final, classpath par l'index et la vue JVM) + la KDoc des candidates (arbre vif, fichier fermé par lecture bornée) — SPI `signature` | 051 + 018 (signature) | 17 |
| **6e** | LE SERVEUR LSP4J : package `protocole/` neuf (CycleVie, EtatsDocuments époque, CapacitesServeur matrice, ConvertisseurLsp UTF-16, PublicateurDiagnostics, ServeurLangageKotlin) + dépendance lsp4j + CAS DORÉS protocole (cycle de vie, codes exacts, matrice paritaire, 0 réponse périmée, publication) | — (ADR 0118) | dorés |
| **6f** | Clôture : preuves mécaniques complètes (suite, jar, scan ART pile, dexage d8, bancs JDK 17, banc qualité, compilation croisée, provenance), journal, ALERTES (E-K8), push + attestation CI | — | — |

**Total : 38 comportements cahier + cas dorés protocole** (agrégat du
cahier — INVENTAIRE.md). Chaque vague : code + tests NEUFS au nom exact du
cahier + suite verte + section du présent journal + commit ; le push et
l'attestation CI portent la clôture (miroir étages 4/5 : le run de TÊTE
éprouve livraisons et clôture ensemble).

## 4. Actions propriétaire ouvertes (reprises de l'étage 5 — §4 du journal précédent)

| # | Action | Référence | Bloque |
|---|---|---|---|
| 1 | Audit de similarité GPL (P-K1) — les copies R1–R8 sont effectuées, l'audit recommandé (rôle distinct des implémenteurs) s'exerce a posteriori | manifeste §3/§6, ALERTES §2 | plus rien de dur — le propriétaire en reste juge |
| 2 | Arbitrages Q-K1 (CIDX vs index dédié) / Q-K2 (décodage @Metadata maison) — PARTIELLEMENT ARBITRÉ ; ouverts pour la structure | DEMANDES-INTERFACE, ADR 0108, ALERTES §4 | le câblage classpath final |
| 3 | Conditions binaires intellij-dependencies (P-K2) — désormais ÉTENDUES aux binaires **lsp4j** (EPL-2.0 côté Eclipse) de la pile chaude du serveur | ADR 0120, NOTICE, §6 | la PUBLICATION du pack langue |
| 4 | Lecture du second manifeste | synthèse §6.4 | rien |
| 5 | Passe appareil couvrant les étages 1–5 (kit enrichi) — recommandation consignée, OPTIONNELLE | `04-execution-etage1.md` §16 | rien (les latences S0 restent tenues en JVM) |
| 6 | Recommandation E-K6 (test flottant Java `IntegrationMoteurIndexTest` : tolérance de tempo) — 2 occurrences CI consignées, le flottement demeure | ALERTES §4 | rien (périmètre Java) |

## 5. Vagues 6a + 6b — le chargeur/descripteur puis les capacités L2 moteur (31/10 — fiches 001 puis 015)

### 5.1 Vague 6a — le chargeur et le descripteur de pack (fiche 001, 3 comportements)

| # | Item | État | Détail |
|---|---|---|---|
| 1 | Les accès SPI du descripteur | **Fait** | [ServiceMoteurLangageKotlin] gagne `identifiant`, `version`, `versionGrammaire`, `versionMinimaleApp` (contrat D6 — les quatre clés du `version.properties` GÉNÉRÉ par `:kotlin:genererDescripteurPack`) ; le lecteur neuf [DescripteurPackKotlin] (session) lit la ressource UNE fois (lazy, tolérante) et sert le repli « inconnue » JAMAIS silencieux : c'est le TEST du SPI qui dénonce le contrat cassé (fiche 001 : « le test doit le dire, pas l'absorber »), pas le moteur qui ment |
| 2 | Le moteur noyau | **Fait** | [MoteurLangageKotlinNoyau] implémente les quatre accès (délégation au lecteur) |
| 3 | Tests neufs | **Fait** | [Fiche001ChargeurMoteurKotlinTest] (3 noms exacts) : le ServiceLoader résout le fournisseur UNIQUE (descripteur META-INF/services embarqué — un fournisseur exactement, `unique` EST le fournisseur résolu, jamais le repli), le descripteur complet aux VALEURS RÉELLES (id exact `jo-codeide-lsp-kotlin`, version/grammaire ≠ inconnue, minApp au motif x.y.z) ET LES QUATRE CLÉS VERROUILLÉES (la forme de la génération ne change plus sans faire échouer le test — la promesse du commentaire de build tenue), l'analyse VIA LE SPI (session réelle : arbre non vide — la déclaration remonte au plan, zéro diagnostic) |

### 5.2 Vague 6b — les capacités L2 moteur (fiche 015, 12 comportements)

| # | Item | État | Détail |
|---|---|---|---|
| 1 | [IndicesDocuments] (code NEUF) | **Fait** | Les TROIS familles d'indices : **PARAMETRE** aux appels positionnels PROUVÉS (candidates du modèle par nom — l'indice n'est émis que si les surcharges s'ACCORDENT sur le nom à l'index ; un argument NOMMÉ ne reçoit JAMAIS d'indice ; ancre sur l'argument, libellé AVANT) ; **TYPE** des déclarations sans type écrit (littéraux modélisés et constructeurs du projet par le typage borné — les suffixes `u`/`U` non modélisés restent MUETS, les composés et références nues non plus ; ancre sur le NOM) ; **LAMBDA** des `it` implicites (l'attente à UN paramètre par [TypageBorne.attenteLambdaAuPoint], libellé COMPLET `it: Type`, ancre sur l'accolade ouvrante) + TYPE des paramètres explicites non typés à ARITÉ ÉGALE de l'attente (divergente : AUCUN indice du littéral) |
| 2 | [SymbolesEspaceDocuments] (code NEUF) | **Fait** | Le premier niveau des documents OUVERTS (arbres VIVANTS — jamais les doublons périmés du scan, miroir [contributionsAuConsult]) + les contributions du scan FILTRÉES des URI ouvertes ; requête CONTIENT insensible à la casse, ordre par nom (puis URI — stable) ; les LOCALES ne remontent JAMAIS (le premier niveau seul est parcouru) |
| 3 | [SelectionsDocuments] (code NEUF) | **Fait** | La chaîne du PLUS PROFOND au FICHIER ENTIER : descente de la racine par l'enfant contenant, déduplication des planges ÉGALES (le wrapper mono-enfant n'ouvre pas de maillon), le fichier est TOUJOURS le dernier maillon ; hors arbre = fichier SEUL (rien n'est inventé) |
| 4 | Le SPI | **Fait** | [indices(uri, debutPlage, finPlage)], [symbolesEspace(requete)], [selections(uri, deplacements)] + types neufs [IndiceKotlin]/[GenreIndiceKotlin]/[SymboleEspaceKotlin]/[ChaineSelectionKotlin] (neutres, zéro lsp4j — frontière D6) ; la session câble sous verrou de lecture, l'univers des preuves par le constructeur neuf [universDePreuves] qui N'EXIGE PAS l'index (les preuves LOCALES d'un document sans classpath ne se taisent plus — le test 8 : propriété typée + SAM projet sans aucune racine) |
| 5 | Tests neufs | **Fait** | [Fiche015CapacitesL2KotlinTest] (12 noms exacts) — VERTS dès la première exécution complète |

### 5.3 Un écart d'architecture consigné (pas de correction : le cahier prime)

Le test source 8 (`it implicite par propriété typée et SAM du projet`)
montait une **vue factice** de classpath dans l'ancien dépôt ; le moteur
neuf n'a PAS de vue hôte (D6 : « la vue de l'HÔTE n'est jamais consultée,
le moteur est autonome » — la vue JVM lit les ENTRÉES). Le montage
équivalent est le document SEUL (aucune racine) : la preuve de l'attente
vient du type ÉCRIT de la propriété et du SAM du PROJET — le contrat
(l'attendu reformulé) est tenu à l'identique, seul le chemin diffère.

## 6. Vagues 6c + 6d — la fiche classpath du survol puis l'aide à la signature (31/10 — fiches 018 puis 051)

### 6.1 Vague 6c — la fiche structurelle (fiche 018, partie survol : 6 comportements)

| # | Item | État | Détail |
|---|---|---|---|
| 1 | [CarteTypeClasspath] (code NEUF) | **Fait** | Le genre (`public class/interface/enum/object` — l'OBJET par son marqueur `INSTANCE` statique final de son PROPRE type : le marqueur EST le genre, jamais un champ), l'héritage direct (`extends` JAMAIS pour une interface, `java.lang.Object` omis, le super `java.lang.Enum` des enums MASQUÉ), la section `Interfaces :` (FQCN écrits), les `Constantes :` d'enum (public static final non-`$` du propre type) et les `Champs publics :` (non statiques non-`$`, privés exclus, type au nom SIMPLE, type illisible exclu D3) ; PLAFONDS 8 champs / 20 constantes avec ellipse « … » |
| 2 | Le lecteur de membres ÉTENDU | **Fait** | [LecteurMembresClasse] porte désormais les DRAPEAUX D'ACCÈS de la CLASSE (ACC_INTERFACE 0x0200, ACC_ENUM 0x4000, ACC_FINAL 0x0010 — [MembresDeclaresJvm.accesClasse]) ET des MEMBRES ([MembreJvm.acces]) — paramètres À DÉFAUT rétrocompatibles, zéro impact pour les consommateurs existants (survol membre, complétion, typage) |
| 3 | Le câblage | **Fait** | [SurvolDocuments] gagne l'étape 3-ter : le nom désigné résout en TYPE ÉCRIT de bibliothèque (import ou défaut — [resoudreTypeEcrit]) et la vue JVM sert la carte (l'en-tête en signature, les sections en documentation) |
| 4 | [OutilsJarFixture] ÉTENDU | **Fait** | `classeGenree` (drapeaux de classe + interfaces directes), `champPrive`/`champStatiqueFinal`/`constanteEnum` (drapeaux de membres), `briserHierarchie` (le super pointé sur un Utf8 du pool) — les montages vague 2c/2d/2f inchangés (paramètres à défaut) |
| 5 | Tests neufs | **Fait** | [Fiche018FicheClasspathTest] (6 noms exacts survol) |

### 6.2 La dégradation honnête du « binaire illisible » (écart d'architecture consigné)

Le test source montait une classe SANS OCTETS servis par la vue factice
(les membres venaient de la table, la hiérarchie des octets). Le moteur
neuf lit TOUT des octets des entrées : l'équivalent honnête est la
SECTION TOLÉRANTE — un class-file à HIÉRARCHIE brisée (référence de
superclasse invalide dans le pool, montage `briserHierarchie`) rend le
genre et les champs, l'héritage et les interfaces se taisent (le lecteur
rend `null` pour la super invalide SANS échouer la lecture des membres —
D3 : jamais une ligne inventée). Le contrat reformulé (« genre et champs
sans héritage ni interfaces ») est tenu à l'identique.

### 6.3 Vague 6d — l'aide à la signature (fiches 051 — 12 — puis 018 partie signature — 5)

| # | Item | État | Détail |
|---|---|---|---|
| 1 | [SignatureDocuments] (code NEUF) | **Fait** | **L'appel englobant le PLUS PROFOND** gagne (la chaîne des ancêtres du caret se parcourt du profond à l'externe, premier appel à candidates) ; **le paramètre ACTIF** (0 après l'ouverture, +1 par virgule — le compte des arguments écrits avant le caret ; le caret SUR la fermante désigne ENCORE la place, APRÈS l'appel fermé = silence D3) ; **le lambda FINAL compte pour l'argument suivant** (le compte des arguments de la liste) |
| 2 | Les candidates | **Fait** | Membre VIF de la classe englobante d'abord (le membre ombrage — la classe la plus proche par la chaîne des ancêtres), FONCTIONS du projet (surcharges TOUTES listées ; la signature ACTIVE = l'arité compatible : `parametres.size > actif`, les DÉFAUTS élargissent l'arité légale), CONSTRUCTEUR PRIMAIRE ([TypeProjet.parametresPrimaire]), APPELABLES de premier niveau du classpath (index réel — `listOf`), appel QUALIFIÉ par le type du RÉCEPTEUR (méthode JVM du PROPRIÉTAIRE DÉCLARANT rendue depuis les DESCRIPTEURS BRUTS — le `V` du void s'y rend `Unit` : `setText(CharSequence): Unit`, types SEULS car le bytecode ne nomme pas ; membre DÉCLARÉ du récepteur projet) |
| 3 | La KDoc des candidates (fiche 018) | **Fait** | Document courant par l'arbre VIF ([DocumentationKotlin.kdocDe] — la reprise R8 de l'étage 5) ; fichier tiers OUVERT par l'arbre VIVANT (le disque périmé JAMAIS consulté) ; fichier FERMÉ par le PORT (la KDoc du scan) ; la candidate du CLASSPATH reste MUETTE (bytecode sans doc, D3) |
| 4 | Le SPI | **Fait** | [signature(uri, deplacement)] + types neufs [AideSignatureKotlin]/[SignatureKotlin] (étiquette, paramètres rendus un à un, documentation) ; la session câble comme le survol (documents et scan hors verrou, univers tolérant l'index absent — les candidates PROJET ne consultent pas le classpath) |
| 5 | Tests neufs | **Fait** | [Fiche051SessionLangageKotlinSignatureTest] (12 noms exacts) + [Fiche018FicheClasspathTest] complétée (5 noms exacts signature — le jar de fixture porte désormais la méthode `setText`) |

## 7. Vague 6e — le serveur LSP4J du module (31/10 — ADR 0118 option 1, miroir :java N8)

| # | Item | État | Détail |
|---|---|---|---|
| 1 | [CycleVie] (code NEUF) | **Fait** | La machine à cinq états (UNINITIALIZED → INITIALIZING → INITIALIZED → SHUTDOWN → EXITED), miroir du MODÈLE Java — le mode in-process ne tue JAMAIS la JVM (`exit` en mode autonome seul) |
| 2 | [EtatsDocuments] (code NEUF) | **Fait** | L'ÉPOQUE COPY-ON-WRITE (« 0 réponse périmée ») : chaque mutation publie un instantané IMMABLE et incrémente l'époque ; [calculerFraiche] REJOUE le calcul borné à 3 relances ; `didSave` sans texte conserve l'époque (le texte n'a pas bougé) |
| 3 | [CapacitesServeur] (code NEUF) | **Fait** | La MATRICE PARITAIRE colonne Kotlin : ANNONCÉES et servies (sync Full, UTF-16, hover, completion `.`/`(`, signatureHelp `(`/`,`, definition, typeDefinition, implementation, references, documentHighlight, documentSymbol, workspaceSymbol, selectionRange, foldingRange, formatting, rangeFormatting, codeAction quickfix, inlayHint, rename+prepare) ; NON ANNONCÉES honnêtes (semanticTokens — dette D3 documentée, codeLens, callHierarchy, typeHierarchy, documentColor, onTypeFormatting, linkedEditingRange) ; serverInfo « lspkotlin » à la version RÉELLE du descripteur de pack (fiche 001) |
| 4 | [ConvertisseurLsp] (code NEUF) | **Fait** | Le repère offsets ↔ positions UTF-16 (dichotomie sur les débuts de ligne — un String JVM EST une suite d'unités UTF-16) + toutes les conversions (diagnostics `kt.*` avec tags et informations liées KP15, kinds de complétion et de symboles, éditions, emplacements) ; les paquets moteur restent LIBRES de lsp4j (convention N1 tenue) |
| 5 | [PublicateurDiagnostics] (code NEUF) | **Fait** | Debounce 50 ms par URI sur fil dédié (`lspkotlin-publications`), publication < 15 s PAR CONSTRUCTION, la version COURANTE du buffer, document fermé = liste VIDE |
| 6 | [ServeurLangageKotlin] (code NEUF) | **Fait** | L'implémentation complète LanguageServer + TextDocumentService + WorkspaceService : gardes I2/I3 (rejets SYNCHRONES -32002/-32600, second `initialize` refusé avec « initialize » dans le message, notifications hors fenêtre IGNORÉES), TOUTES les requêtes par [calculerFraiche] (l'époque protège chaque réponse), la session moteur en miroir EXACT des mutations de buffer, `didChangeWatchedFiles` par le fil dédié du scan (le fil RPC n'attend jamais), les requêtes non annoncées TOLÉRANTES (réponse vide — convention I9) |
| 7 | La dépendance | **Fait** | `org.eclipse.lsp4j:org.eclipse.lsp4j:0.21.2` en scope implementation (la MÊME version que :java — le câble cel-lsp étant commun) ; le jar du moteur NE L'EMBARQUE PAS (miroir :java — lsp4j est fourni par l'hôte ; E-K8 sans changement de pile, §9.3) |
| 8 | Le harnais + les cas dorés | **Fait** | [BoucleLocale] (deux lanceurs piped, client espion — les allers-retours lsp4j AUTHENTIQUES) + [CasDoresProtocoleKotlinTest] : **7 dorés** — cycle de vie complet (les cinq états dans l'ordre, la JVM survit à `exit`), CODES EXACTS (-32600 au second initialize avec « initialize » au message, -32002 avant `initialized`, -32600 après `shutdown`), notifications hors fenêtre ignorées, **MATRICE VERROUILLÉE** (les annoncées une à une ET les absentes : semanticTokens/codeLens/hiérarchies/documentColor/onTypeFormatting/linkedEditingRange), **ÉPOQUE COMPLÈTE** (ouverture, mutation, péremption, recalcul frais, fermeture), publication didOpen/didChange (`kt.unusedImport` publié puis EFFACÉ au changement), survol BOUT-EN-BOUT par le transport réel (markdown ```kotlin + KDoc) |

### 7.1 La capability maison n'est PAS annoncée (écart consigné, règle matrice)

L'ADR 0118 évoquait « capability `x-codeide.*` » en suivant les designs
Java : la capability `x-codeide.completionRanking` du serveur Java
 transporte un payload de classement construit par la frontière RPC de
l'étage N2 Java ([FrontiereRpc]). **Le moteur Kotlin n'a pas de frontière
RPC équivalente** (l'étage 3 a livré la chaîne MOTEUR + le banc de
qualité, pas le payload client) : la règle matrice « capacité testée =
annoncée ; sinon absente » s'applique — la capability n'est PAS annoncée,
et son ajout relève d'une extension dédiée si le propriétaire veut le
re-filtrage local côté client (demande à consigner). L'époque
copy-on-write, l'autre moitié du protocole riche, EST livrée (doré
époque).

## 8. Les écarts découverts et corrigés pendant la mise au vert

1. **Le descripteur du `V` (void) muet dans le dépouillement** :
   [DescripteursJvm] rend `null` pour `V` (tableau/void — conservateur
   pour le TYPAGE) ; la SIGNATURE ne peut pas se taire sur le retour
   d'une méthode JVM (`setText(CharSequence): Unit` — fiche 018). Le
   rendu JVM lit les DESCRIPTEURS BRUTS du propriétaire déclarant et
   rend `V` → `Unit` explicitement ([retourJvm]) — le typage garde son
   conservatisme, la signature son exhaustivité.
2. **`CompletionList.isIncomplete`** : le champ lsp4j porte le getter
   `isIncomplete`/setter `setIsIncomplete` — la syntaxe propriété
   Kotlin ne résout pas la paire ; l'appel SETTEUR explicite (miroir
   des setters surchargés des `ServerCapabilities`, qui exigent aussi
   la forme `setTextDocumentSync(...)` plutôt que l'affectation de
   propriété).

## 9. Clôture de l'étage 6

### 9.1 Le compte du cahier

469 comportements au total ; l'étage 6 livre **38** (fiche 001 : 3 ;
fiche 015 : 12 ; fiche 018 : 11 ; fiche 051 : 12) — le solde de 468
comportements livrés aux étages 0-6, **RESTE 1** : la fiche 021 (mesures
perf — 1 comportement), affectée au TRANSVERSE T (§1). Plus les 12 cas
dorés transversaux (5 WorkspaceEdit étage 5 + 7 protocole étage 6).

### 9.2 Les critères de sortie (§2) — ATTESTÉS

1. **Matrice reproduite** — le doré `matrice-paritaire` verrouille les
   capabilities EXACTEMENT (les 19 annoncées une à une, les 7 absentes
   une à une) : chaque case annoncée correspond à une capacité SERVIE
   par le SPI et TESTÉE par le cahier moteur (fiches 015/018/051
   comprises — les trois capacités L2 et la signature sont les
   dernières cases de la colonne) ;
2. **Codes exacts** — le doré `codes-exacts` (-32600/-32002/-32600,
   message « initialize » compris) + `notifications-hors-fenêtre` ;
3. **0 réponse périmée** — le doré `époque-copy-on-write` (la mécanique
   complète : péremption, recalcul frais borné) + TOUTES les requêtes
   du serveur passent par [calculerFraiche] ;
4. **Fiches vertes** — 38/38 (§5, §6) ;
5. **E-K8** — dex d8 sous les seuils arbitrés (§9.3).

**L'ÉTAGE 6 EST CLOS sur preuve mécanique.**

### 9.3 Les preuves de clôture (bac à sable, 31/10)

| Preuve | Résultat [mesuré] |
|---|---|
| Suite du module | **522 tests VERTS** (477 + 3 fiche 001 + 12 fiche 015 + 11 fiche 018 + 12 fiche 051 + 7 dorés protocole), toolchain 17 |
| Jar du moteur | **990 859 o** (892 147 o à la clôture étage 5 — +98 712 o : les moteurs L2/classpath/signature, le SPI et la couche `protocole/`) |
| Scan ART | **6 × AUCUNE** référence sensible (moteur **349 classes** — 321 à l'étage 5 ; syntax-api 309 ; lang-syntax 3 ; annotations 61 ; stdlib 970 pour mémoire ; kotlin-metadata-jvm 492) — ServiceLoader OK (descripteur du pack) |
| Dexage d8 (build-tools 34, min-api 26, pile CI inchangée) | **26 590 méthodes / 4 294 236 o (4,10 Mo)** — 81 % du plafond 32 768 / 68 % du budget 6 Mo (+550 méthodes / +97 512 o vs étage 5) ; **le câble lsp4j n'est PAS compté** (fourni par l'hôte — §7 item 7) ; POUR MÉMOIRE avec le câble entier : 32 643 / 4 974 608 o (4,74 Mo) |
| Banc empreinte/latence JDK 17 | productions **5 448** (corpus 500) / **31 368** (corpus 3 000) ; jetons **4 210 / 24 274** ; marqueurs d'erreur **0** ; tas au repos **3 506 Kio** — déterminisme IDENTIQUE à l'étage 5 (les mêmes corpus, les mêmes nombres de grammaire) |
| Banc qualité complétion | **VERT** — p95 **6,28 ms** (SOUS la baseline signée 7,53 ms de l'étage 5), **0 saut** de liste, les 16 positions du corpus gagnantes au rang 0 |
| Compilation croisée | testClasses de TOUS les modules (`:kotlin`, `:java`, `:index`, `:model`, `:testing`, `:ecj-art`) — BUILD SUCCESSFUL |
| Provenance | `verifier-provenance.sh` **rc=0** (aucun fichier hors registre ; les reprises R1-R8 inchangées — l'étage 6 n'en ajoute AUCUNE, la couche protocole est du code NEUF) |
| Baseline digeste | re-signée au fil des vagues (401 → **411** fichiers — les 7 neufs de 6a/6b + les 4 de 6c/6d + protocole et tests de 6e) |

### 9.4 Le coût de l'étage 6 (miroir des relevés des étages précédents — PAS un recalage : le recalage n° 3 a clos la série)

| Grandeur | Valeur | Détail |
|---|---:|---|
| Commits de l'étage | 6 | 6a (`832eb6b`), 6b (`e343ad2`), 6c (`8a003c8`), 6d (`1b50001`), 6e (`5d07f80`) + la clôture 6f (le présent commit) |
| Fichiers neufs (main) | 14 | SPI (IndicesKotlin, AideSignatureKotlin), moteurs (IndicesDocuments, SymbolesEspaceDocuments, SelectionsDocuments, CarteTypeClasspath, SignatureDocuments, DescripteurPackKotlin), protocole (CycleVie, EtatsDocuments, CapacitesServeur, ConvertisseurLsp, PublicateurDiagnostics, ServeurLangageKotlin) |
| Fichiers neufs (test) | 6 | Fiche001 (3), Fiche015 (12), Fiche018 (11), Fiche051 (12), CasDoresProtocole (7), BoucleLocale (harnais) |
| Fichiers modifiés | 10 | build.gradle.kts (lsp4j), SPI session + moteur noyau + survol + lecteur VueJvm (drapeaux), OutilsJarFixture, digeste-corpus (re-signé) |
| Tests du module | 477 → **522** | +38 comportements (EXACTEMENT 1 test/comportement) + 7 cas dorés protocole |
| Tempo [mesuré-agent] | 31/10 (séance continue) | 6 vagues — la dominante serveur est la PLUS COSTEUSE de la deuxième moitié du programme (la couche protocole neuve pèse 1 617 l. de code + le harnais 449 l.), mais l'abaque TIEN : ~97 l./comportement sur les fiches + le socle protocole |

**Comparaison à l'estimation** : la fourchette de l'étage 6 était
**16–26 j-h [estimé]** (synthèse §4). Les 38 comportements + le socle
serveur complet + 7 dorés s'inscrivent dans la dominante (miroir Java
N8 : 44 comportements + serveur pour 14–25 j-h) ; le relevé confirme
l'abaque SANS rouvrir le recalage (série close au n° 3). **Reste à
l'abaque : le transverse T (8–12 j-h — la fiche 021 et les recalages
finaux).**

**ATTESTATION CI (consignée après poussée)** : le push de l'étage
(`be35373` → `f017a14`, les six commits 6a/6b/6c/6d/6e/clôture) a
déclenché le run **`41162022675`** — consulté le 31/10 via l'API GitHub
Actions : **CI VERTE 10/10** sur le commit de clôture `f017a14` (le run
porte le SHA de TÊTE du push : il éprouve les LIVRAISONS 6a-6e ET la
clôture ensemble). Les dix jobs verts : Build et tests JDK 21, banc
parité croisée T (oracle vendored épinglé), **suite du module Kotlin**
(les 522 tests — fiches 001/015/018/051 et cas dorés protocole
compris), banc qualité complétion (baseline signée Java), banc
empreinte/latence Kotlin, **banc qualité complétion Kotlin étage 3**
(baseline signée — p95 sous la baseline), **gates Android SDK 34**,
banc empreinte/latence du noyau N1, **manifeste de provenance**
(anti-fichier-hors-registre — les 14 fichiers neufs de l'étage sont du
code NEUF, aucun hors registre), **scan ART + dexage d8** (6 × AUCUNE,
26 590 méthodes / 4,10 Mo sous les seuils étage 0). Le flottant E-K6
(périmètre Java) n'a pas frappé.

