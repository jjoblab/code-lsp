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
