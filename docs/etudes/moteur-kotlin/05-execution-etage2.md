# 05 — Exécution étage 2 : résolution + diagnostics

> **Décision du propriétaire reçue le 2026-10-30** dans le canal de mission :
> « Go étage 2 » — les chantiers de l'étage 2 (plan §4 de `00-synthese.md`)
> sont OUVERTS sur la branche `etude/moteur-kotlin`, à la suite de la
> clôture sur preuve mécanique de l'étage 1 (CI verte 9/9 sur `d3486d7`,
> run `41024404548` — `04-execution-etage1.md` §17.4). Le présent document
> est le journal d'exécution de l'étage (méthode : miroir des rapports
> précédents — chiffres étiquetés [mesuré] / [estimé], écarts signalés).
> **Vague 2a : le socle de résolution — modèle de symboles projet (scan
> Kotlin ET Java), hiérarchie de portées, `kt.unresolved` avec la garde par
> déclaration (fiche 017), preuve d'absence sur les types du projet (fiche
> 022) et journal « explain » (fiche 006).**
>
> Gouvernance inchangée : branche `etude/moteur-kotlin` seule ; zones NOYAU
> anciennes (`resolve/`, `diagnostics/`) en lecture interdite R9.1 —
> l'étage 2 est du code NEUF écrit cahier en main (ADR 0106 : « le reste est
> réécrit depuis le tableau ») ; les tests sources anciens restent
> consultables (R9.5) pour les FIXTURES, jamais copiés.
>
> **Mise à jour (30/10, vague 2a) — LE SOCLE DE RÉSOLUTION EST LIVRÉ :
> modèle de symboles projet (scan Kotlin ET Java — lecteur Java maison),
> hiérarchie de portées (imports exact/joker/alias, paquets par défaut avec
> le catalogue KP8 et la voie 6 de l'index), `kt.unresolved` par preuve
> d'absence (noms nus et sélecteurs qualifiés sur récepteurs PROJET typés —
> ADR 0223), garde par déclaration (fiche 017), journal « explain » FINE
> (fiche 006), `kt.missingReturn` (cas positif requis par la fiche 017) et
> le GERME de complétion membre (requis par la fiche 022) ; 20
> comportements verts — fiches 017 (8/8), 022 (8/8), 006 (4/4) ; le tableau
> règles × spec exigé par l'ADR 0106 vit dans
> `kotlin/SPECIFICATION-RESOLUTION.md` (versionné, rempli fiche par fiche).**
> **Clôture (30/10) — VAGUE 2A CLOSE : CI VERTE 9/9 sur le commit de
> livraison `a07d381` (run `41054215181`, tentative 2 — le test flottant
> E-K6 du chantier Java a fait échouer la tentative 1, sans lien avec le
> diff) ; 152 tests verts toolchain 17, scan ART 6 × AUCUNE, dex 24 996
> méthodes / 3 882 064 o (identiques au bac à sable, seuils tenus — E-K8 à
> 93 %).**
>
> **Mise à jour (30/10, vague 2b) — LES FAMILLES SÉMANTIQUES K4 SONT
> LIVRÉES (fiche 046, 39 comportements) : la VUE JVM DES MEMBRES du
> classpath (lecteur MAISON — décision Q-K1 tranchée à minima, pilotée par
> les ENTRÉES, chaîne de supertypes + propriétés dérivées + implicites),
> les smart casts POSITIONNELS (THEN, garde `!is` à sortie précoce,
> opérande droit de `&&`, entrée de `when` sans sujet ; ombrage coupé,
> `||` annule), les lambdas par ATTENTE (paramètre explicite, `it`, SAM
> projet, type de fonction écrit), la liaison POSITIONNELLE (l'ombre
> interne gagne), `kt.typeMismatch`/`kt.argumentCount`/`kt.unsafeNullable`/
> `kt.unreachable`/`kt.valReassign` et le quick-fix `importsCandidats` ;
> 59/204 comportements diagnostics verts (fiches 017+022+006+046).**

## 1. Périmètre de l'étage (plan §4 — cahier en main)

| # | Item | Source du contrat |
|---|---|---|
| 1 | **Résolution spécifiée avant implémentée** : tableau règles × références de spécification × statut (couvert / sous-rapport / hors périmètre), document interne du module VERSIONNÉ — l'implémentation le remplit fiche par fiche | ADR 0106 (option 1) |
| 2 | **Hiérarchie de portées** : locales → membres → englobantes → paquet → imports exact/joker/alias → défauts `kotlin.*`/`java.lang.*` ; récepteurs implicites (`with`/`apply`), membres hérités (fermeture mixte projet/classpath), compagnons, typealias, expect/actual | ADR 0106 ; fiches 044/046/047/024/037/031 |
| 3 | **Catalogue `kt.*` conservateur** : `kt.unresolved`, `kt.typeMismatch`, `kt.argumentCount`, `kt.unsafeNullable`, `kt.missingReturn`, `kt.unreachable`, `kt.valReassign` — une famille n'entre au catalogue que si son absence se DÉCIDE sur l'arbre + la résolution bornée | ADR 0112 (option 1) ; fiche 046 |
| 4 | **Garde par déclaration** : une zone brisée ne coupe que sa preuve — seules les déclarations au sous-arbre propre sont diagnostiquées ; un nom déclaré n'importe où n'est jamais signalé ; zone import brisée = document muet ; en-tête de classe brisé = absence muette dans les membres | ADR 0112 ; fiche 017 |
| 5 | **Typage borné et versionné** : familles couvertes / sous-rapportées / frontière d'unification documentées dans le module ; « preuve ou silence » comme méta-critère | ADR 0107 (option 1) |
| 6 | **Journal « explain »** des `kt.unresolved` émis : niveau FINE, éteint par défaut, forme greppable `clé=valeur` (forme syntaxique, issue de la sélection, récepteur typé, état de l'univers des preuves) | ADR 0112 ; fiche 006 |
| 7 | **Consommation de l'étage 1** : l'index des appelables (guichet de sous-lecture fiche 042, `VueAppelables.extensionsSur`/`topParNom`) nourrit les preuves ; la fusion des groupes synthétiques AVEC COUPURE (une classe générée réelle efface le groupe homonyme) spécifiée par les fiches 040/045/047 | `04-execution-etage1.md` §16/§17 ; ADR 0109 |
| 8 | **Banc de fidélité** : oracle K2 en sous-processus sur corpus fixe, baseline signée, faux positifs = 0 (invariant §4) | ADR 0119 (option 1) |

## 2. Critères de sortie de l'étage 2 (synthèse §4 — suivi)

| Critère | État | Preuve |
|---|---|---|
| 204 comportements diagnostics verts (23 fiches du domaine) | EN COURS — 59/204 | Vague 2a (§5) : fiches 017 (8/8) + 022 (8/8) + 006 (4/4) ; vague 2b (§6) : fiche 046 (39/39) |
| Zéro faux positif corpus quatre mondes | OUVERT | — |
| Banc fidélité baseline signée | OUVERT | — |

## 3. Plan de vagues (cahier en main — l'ordre suit le plan de migration ADR 0106 : portées/imports d'abord, membres hérités ensuite, récepteurs de portée enfin)

| Vague | Périmètre | Fiches | Comportements |
|---|---|---|---:|
| **2a** | Socle de résolution : modèle de symboles projet (scan Kotlin ET Java), hiérarchie de portées + imports + défauts KP8, `kt.unresolved` (noms nus et sélecteurs qualifiés sur récepteurs PROJET typés), garde par déclaration, journal explain, `kt.missingReturn` (cas positif requis par la fiche 017), germe de complétion membre (requis par la fiche 022) | 017 + 022 + 006 | 20 |
| **2b** | Familles sémantiques K4 : typage borné (littéraux, types écrits, initialiseurs, retours), smart casts positionnels, lambdas par attente, `kt.typeMismatch`/`kt.argumentCount`/`kt.unsafeNullable`/`kt.unreachable`/`kt.valReassign`, gardes KP8/ADR 0203, `importsCandidats` ; vue JVM des membres du classpath (récepteurs classpath typés — ADR 0223) | 046 | 39 |
| **2c** | Membres hérités : clôture des supertypes (clauses écrites + port de fichiers, projet et classpath mixtes), chaîne profondeur 7 du monde Views, frère projet ouvert vivant, récepteur implicite hérité, objets anonymes (pile des this) | 044 + 028 + 032 + 040 + 029 | 44 |
| **2d** | Classpath côté Kotlin : compagnons (anonymes/nommés), objets du classpath (`INSTANCE`), typealias de bibliothèque, extensions à récepteur paramètre de type (`let`/`also`…), appelables builtin (`arrayOf`…) | 023 + 024 + 026 + 033 + 037 | 25 |
| **2e** | KMP (expect/actual, deux racines), sources générées (`build/generated`), troncation du scan réfléchie dans l'univers, motifs Compose (stdlib réel), scripts `.gradle.kts` (muets sans modèle Gradle) | 030 + 031 + 035 + 036 + 025 + 027 | 32 |
| **2f** | Correctifs texte (accès non sûr, conversions), membres manquants (quick-fix P1-C), consommation des synthétiques + fusion AVEC COUPURE, multi-langue Java↔Kotlin, récepteurs implicites `with` | 016 + 020 + 047 | 44 |
| **2g** | Clôture : banc fidélité K2 oracle (baseline signée), passe zéro faux positif corpus quatre mondes, recalage de l'effort, journal | — | — |

**Total fiches diagnostics : 204 comportements** (agrégat du cahier —
INVENTAIRE.md). Chaque vague : code + tests NEUFS au nom exact du cahier +
CI 9 tâques verte + section du présent journal + commit/push.

## 4. Actions propriétaire ouvertes (reprises de l'étage 1 — §6 du journal précédent)

| # | Action | Référence | Bloque |
|---|---|---|---|
| 1 | Audit de similarité GPL (P-K1) — la copie R1/R2 est effectuée (vague 1e), l'audit recommandé (rôle distinct des implémenteurs) s'exerce a posteriori | manifeste §3/§6, ALERTES §2 | plus rien de dur — le propriétaire en reste juge |
| 2 | Arbitrages Q-K1 (CIDX vs index dédié) / Q-K2 (décodage @Metadata maison) | DEMANDES-INTERFACE, ADR 0108 | le câblage classpath final ; la marge dex (E-K8 : 93 % du budget taille) |
| 3 | Conditions binaires intellij-dependencies (P-K2) | ADR 0120, NOTICE | la PUBLICATION du pack langue |
| 4 | Lecture du second manifeste | synthèse §6.4 | rien |
| 5 | Passe appareil couvrant l'étage 1 (kit enrichi de `kotlin-metadata-jvm`) — recommandation consignée, OPTIONNELLE | `04-execution-etage1.md` §16/§6 | rien (les latences S0 restent tenues en JVM) |

## 5. Vague 2a — le socle de résolution (30/10, fiches 017/022/006)

### 5.1 Périmètre exécuté

| # | Item | État | Détail |
|---|---|---|---|
| 1 | Tableau règles × spécification | **Fait** | `kotlin/SPECIFICATION-RESOLUTION.md` (ADR 0106 option 1) : portées (P1–P13), sélection par preuve (A1–A16), garde d'édition (G1–G7), sources du projet (S1–S5), journal explain (E1–E3), frontières assumées — chaque ligne cite le canon et le statut, rempli fiche par fiche |
| 2 | Modèle de symboles du projet | **Fait** | `resolve/ModeleProjet.kt` : types (Kotlin classes/interfaces/enums/OBJETS, Java), membres (fonctions, propriétés, paramètres `val`/`var` du constructeur), supertypes ÉCRITS, data (`copy`/`componentN` synthétisés), entrées d'enum, appelables et EXTENSIONS de premier niveau (récepteur résolu par FQCN en deuxième passe d'assemblage) |
| 3 | Lecteur Java maison | **Fait** | `resolve/LecteurDeclarationsJava.kt` : conservateur (jetons, accolades balancées, génériques SAUTÉS — la contrainte d'un paramètre de type n'est PAS un héritage, fiche 022), propriétés synthétiques des accesseurs (ADR 0219 : `getTitre()` → `titre`), enum/record Java marqués non absence-fiables, `@interface` lu comme type ; tolérant (jamais d'exception) |
| 4 | Univers des preuves + résolution | **Fait** | `resolve/ResolutionReferences.kt` : hiérarchie de portées (déclarés → liaisons locales → types projet → imports exacts/alias → package courant → voie 6 de l'index → catalogue KP8), preuve d'absence des sélecteurs (membres complets + implicites Any/Object + extensions projet/classpath + gardes de sonorité : extensions génériques, extensions de `Any`, récepteurs non résolus), régimes (guichet muet, scan partiel, joker non énumérable, script) |
| 5 | Marche typée | **Fait** | `resolve/MarcheTypee.kt` : liaison nom → type écrit PARESSEUSE (mémoïsée, gardée contre les cycles), typage par initialiseur (`val q = p.copy(x = 1)`), constructeurs, retours écrits |
| 6 | Journal « explain » | **Fait** | `resolve/ExplicationsNonResolus.kt` : JUL niveau FINE, éteint par défaut (garde `isLoggable` AVANT toute construction), forme greppable `clé=valeur` (forme, sélection, récepteur typé, scan, index, script, position `ligne:colonne`) |
| 7 | Passe sémantique de session | **Fait** | `session/DiagnosticsSemantiques.kt` : garde par déclaration (régions propres, noms déclarés documentaires avec data synthétisés, zone import brisée, en-tête de classe brisée, univers ouverts héritage/enum), marche des références (noms nus, sélecteurs, types `USER_TYPE` simples — les qualifiés restent muets, fiche 044), `kt.missingReturn` (corps de bloc sans `return` ni `throw`, type écrit ≠ `Unit`) |
| 8 | Germe de complétion membre | **Fait** | `session/CompletionMembres.kt` + SPI `PropositionKotlin` + `SessionLangageKotlin.completion()` : membres/implicites/extensions du récepteur typé du PROJET après le point (fiche 022 : `copy` proposé) — le pipeline complet est l'étage 3 |
| 9 | Scan étendu | **Fait** | La marche collecte AUSSI les `.java` (contribution au modèle SEULEMENT — la matière du survol reste Kotlin) ; le snapshot porte les contributions par fichier et l'ÉTAT de la passe (complète ou partielle) ; le consult REJOINT la passe en vol (miroir de la fiche 041) |
| 10 | Catalogue `kt.*` | **Fait** | `kt.unresolved` + `kt.missingReturn` émis ; `kt.argumentCount` RÉSERVÉ au vocabulaire (famille vague 2b — les comportements 017 de non-émission restent verts par absence d'émission, verrouillage substantiel en 2b) |
| 11 | Tests neufs | **Fait** | 20 comportements aux noms exacts du cahier : fiche 017 (8) + fiche 022 (8) + fiche 006 (4) — fixtures NEUVES (anciens tests consultés R9.1 pour les fixtures, jamais copiés) ; le câblage suit le chemin de production D6 (moteur nourricier + session branchée + guichet attendu par sondage borné — miroir du patron de la fiche 042) |

### 5.2 Décisions d'exécution consignées (pas dans le plan initial)

1. **Le guichet d'index EST la porte des preuves** : `kt.unresolved` ne
   prouve RIEN tant que le guichet de la fiche 042 est muet (classpath non
   posé OU passe non publiée) — la porte « sans classpath, jamais signalé »
   de la fiche 046 est tenue par construction, et le classpath VIDE mais
   DÉCLARÉ (passe aboutie) établit un univers VIDE où les preuves ne
   dépendent que de la structure (fiche 017).
2. **Le scan se consulte en MODE JOINTURE pour l'univers des preuves** :
   contrairement au survol (servi après `rafraichirSources` — contrat 049),
   la passe sémantique REJOINT la passe de scan en vol (jamais de scan sur
   le fil appelant, fiche 041) — les fiches 022/006 voient le scan sans
   rafraîchissement explicite, miroir de l'univers `estUniversConna()`
   ancien ; la consultation se fait HORS verrou de session (le verrou
   n'a rien à garder pendant l'attente).
3. **La garde de sonorité des extensions** : (a) les extensions GÉNÉRIQUES
   (`fun <T> T.…`, indexées sans récepteur de classe) atteignent tout
   récepteur — leur présence dans l'index MUET la preuve (fiche 026 en 2d) ;
   (b) les extensions de `kotlin.Any` pareillement ; (c) une extension du
   projet dont le récepteur ÉCRIT ne résout à AUCUN type (alias, qualifié)
   marque le modèle « extensions non résolues » et MUET toutes les preuves
   de sélecteurs — l'univers ouvert est toujours préféré au faux positif.
4. **Les jokers de packages classpath MUET les preuves de noms nus** : sans
   vue JVM (la question lecteur maison vs `:index` est la vague 2b, Q-K1 et
   budget dex E-K8 en jeu), un `import java.util.*` ne permet PAS de prouver
   qu'un nom n'y vit pas ; les jokers de packages PROJET (énumérables par
   le scan) laissent les preuves intactes.
5. **Le catalogue KP8 est un PLANCHER documenté** : les types bien connus
   des paquets par défaut résolvent sans classpath (garde KP8 de la fiche
   046 : un type hors catalogue et hors classpath reste SIGNALÉ) ; les
   APPELABLES des paquets par défaut relèvent de la voie 6 de l'index
   (ADR 0203) — `println` ne résout donc que lorsque le stdlib est dans les
   entrées (fidèle au compilateur : sans stdlib, `println` est réellement
   irrésolvable).
6. **La complétion est un germe ASSUMÉ** : la fiche 022 exige `copy` proposé
   après `p.` — le SPI porte `PropositionKotlin` (libellé nu) et la méthode
   `completion` du contrat de session, documentés comme croissant à l'étage
   3 (ADR 0113) ; le germe ne devine jamais : sans récepteur typé du
   projet, liste vide honnête.
7. **`kt.missingReturn` est livré au cas positif** (requis par la fiche 017
   « preuve locale ») avec la garde naturelle (corps d'expression, `Unit`,
   `throw`, `return` imbriqué éteignent) ; les gardes COMPLÈTES de la fiche
   046 (sous-rapports documentés) arrivent en vague 2b.
8. **Le document COURANT prime sur son doublon du scan** : sa contribution
   est assemblée EN PREMIER (les FQCN premiers gagnent) et le scan est
   filtré de son URI — l'arbre frais est la vérité, jamais contredit.

### 5.3 Mesures de la vague 2a (bac à sable — JVM de bureau conteneurisée, toolchain 17)

- **Suite du module : 152 tests VERTS** (132 + 20) sous la toolchain 17 —
  fiches 017 (8/8), 022 (8/8), 006 (4/4), l'intégralité de l'étage 1
  inchangée (010-013, 049/042/041, 002-005/008/009/034, synthétiques,
  garde JDK 17).
- **Baseline de parité re-signée** : corpus 324 → **335 fichiers**
  (+7 main : les cinq fichiers de la zone `resolve/`, la passe sémantique,
  le germe de complétion ; +3 test ; +1 document de spécification) —
  procédure documentée `-Dparite.majBase=true --rerun-tasks`.
- **Jar moteur : 499 730 o** (+81 025 vs vague 1e) — le socle de
  résolution.
- **Scan ART pile complète** (mêmes commandes que la CI) : **6 × AUCUNE
  référence sensible** — moteur **189 classes / 250 méthodes déclarées**
  (+27 classes / +23 méthodes vs vague 1e : `ModeleProjet`,
  `LecteurDeclarationsJava`, `ResolutionReferences`, `MarcheTypee`,
  `ExplicationsNonResolus`, `DiagnosticsSemantiques`, `CompletionMembres`
  et compagnons), class-file 61:189 (bytecode 17 intégral), ServiceLoader
  OK — descripteur vérifié (1 fournisseur).
- **Dexage d8** (build-tools 34, min-api 26, pile chaude complète) :
  **24 996 références de méthodes / 3 882 064 o (3,70 Mo)** — seuils étage
  0 tenus (marges 7 772 méthodes / 312 Kio) ; **alerte E-K8 actualisée :
  budget taille consommé à 93 %** (la vague ajoute 224 méthodes /
  66 336 o).
- **Banc JDK 17** (toolchain du module) : parse froid 220,7/26,8 ms, p50
  6,1/24,4 ms, p95 36,3/49,4 ms — reparse p50 24,4 ms à 3 000 l. (< 50 ms,
  latences S0 tenues), déterminisme **31 368 / 24 274 IDENTIQUE** (étage 0,
  1c–2a), tolérance 101 troncatures / 0 lever, tas au repos 3 845 Kio
  (+215 Kio vs §17 — le banc ne traverse pas la passe sémantique ; l'écart
  est le bruit entre passes déjà consigné §12/§14/§16/§17).
- **Provenance** : `verifier-provenance.sh` rc=0 (aucune reprise nouvelle —
  la vague est du code NEUF intégral, zones `resolve/` écrites cahier en
  main).

### 5.4 Clôture de la vague 2a

**CI VERTE 9/9 sur le commit de livraison `a07d381`** (run `41054215181`,
tentative 2 — consulté le 30/10) : build et tests JDK 21, bancs
noyau/qualité/parité T, gates Android, provenance (rc=0), et les trois jobs
Kotlin — suite du module **152 tests VERTS sur la toolchain 17** (fiches
010-013 + 049/042/041 + 002-005/008/009/034 + synthétiques R1/R2 +
**017/022/006**), scan ART **6 × « AUCUNE »** sur la pile complète (moteur
189 classes / 250 méthodes déclarées — ServiceLoader OK descripteur
vérifié), dexage d8 **24 996 références de méthodes / 3 882 064 o (3,70
Mo)** [mesurés par la CI — IDENTIQUES au bac à sable, seuils étage 0
tenus], banc empreinte/latence sous JDK 17.

**Écart d'exécution signalé (E-K6, miroir de la vague 1c)** : la tentative
1 du run a échoué sur le test flottant CONNU du chantier Java
(`testing/…/IntegrationMoteurIndexTest.reouvertureChaudeSansReconstruction`
— assertion de TEMPO sans tolérance, documenté ALERTES §4 E-K6 depuis la
vague 1c) ; le diff de la vague ne touche QUE `kotlin/` et `docs/` (aucun
lien avec le périmètre Java), la relance du job a rendu la tentative 2
VERTE (9/9). La recommandation d'assouplissement au propriétaire
(périmètre Java) DEMEURE.

**LA VAGUE 2A EST CLOSE sur preuve mécanique** : le socle de résolution
est livré — hiérarchie de portées, modèle de symboles projet (Kotlin ET
Java), preuve d'absence `kt.unresolved` (noms nus et sélecteurs sur
récepteurs projet typés), garde par déclaration, journal « explain »,
`kt.missingReturn` (cas positif), germe de complétion membre ; 20/20
comportements des fiches 017/022/006 verts. La vague 2b (familles
sémantiques K4 — fiche 046, 39 comportements : typage borné, smart casts,
lambdas par attente, `kt.typeMismatch`/`kt.argumentCount`/`kt.unsafeNullable`
/`kt.unreachable`/`kt.valReassign`, gardes KP8/ADR 0203, `importsCandidats`,
et la question de la vue JVM des membres du classpath) est la suivante.

## 6. Vague 2b — les familles sémantiques K4 (30/10, fiche 046)

### 6.1 Périmètre exécuté

| # | Item | État | Détail |
|---|---|---|---|
| 1 | Vue JVM des membres du classpath | **Fait** | `resolve/VueJvm.kt` : lecteur MAISON des class-files (pool de constantes avec indirection des constantes Class, champs et méthodes déclarés — synthétiques et ponts écartés, `java.lang.Object` terminal connu) + `VueJvmMembres` (jeu COMPLET par marche bornée de la chaîne de supertypes — profondeur 24, cycles coupés ; propriétés DÉRIVÉES des accesseurs `get`/`is` ADR 0219 ; `fqcnParNomSimple` pour les quick-fixes d'import) — PILOTÉE PAR LES ENTRÉES (miroir de la discipline de l'index 1d : jamais la ressource du runtime, jamais la vue de l'hôte), paresseuse et tolérante (D3), lecture synchronisée (requêtes parallèles ADR 0117), portée par le snapshot publié (`SnapshotClasspath.vueJvm` — même garde de couverture que l'index) |
| 2 | Typage borné étendu | **Fait** | `resolve/TypageBorne.kt` : `TypeValeur` (Projet \| Jvm), `typeDeLexpression` (littéraux par famille, types écrits via la résolution projet → import → catalogue KP8 `NOMS_FQCN_DEFAUT`, chaînes qualifiées par le retour écrit des membres), `raffinementAuPoint` (smart casts POSITIONNELS : branche THEN, garde `!is` à sortie PRÉCOCE — `estTerminal` déballant THEN/BLOCK, opérande droit d'un `&&`, corps d'entrée de `when` sans sujet ; l'OMBRE par une locale postérieure coupe — recherche dans le THEN ; `||` ANNULE tout), `attenteLambdaAuPoint` (fonction projet par INDEX d'argument, méthode unique d'une interface projet — SAM, type de fonction ÉCRIT d'une propriété), `familleLitteral` |
| 3 | Liaison POSITIONNELLE | **Fait** | `MarcheTypee.Liaison` refondue : les candidats d'un nom (paramètres, locales, propriétés du document) sont collectés en ordre de SOURCE et la consultation À POSITION rend le DERNIER déclaré avant l'usage — la locale ombreuse gagne (P13, fiche 046) ; la nullabilité écrite est CONSERVÉE (`String?` — c'est elle qui prouve `kt.unsafeNullable`) |
| 4 | Modèle étendu | **Fait** | `ModeleProjet` : `ParametreProjet` (nom, type écrit, type FONCTIONNEL écrit, défaut, `vararg`) sur les appelables et les méthodes, `parametresPrimaire` sur les types (témoins d'arité des constructeurs), `uri` sur les appelables (propreté des témoins du document courant), `typeFonctionEcritDe` partagé (modèle + attentes) |
| 5 | `kt.typeMismatch` | **Fait** | `DiagnosticsTypage.verifierTypeMismatch` : famille du LITTÉRAL vs famille du type ÉCRIT (chaîne/numérique/booléen/caractère) ; conversions numériques legitimes et nullabilité muettes ; plage sur le littéral |
| 6 | `kt.argumentCount` | **Fait** | `verifierArite` : appels NUS aux fonctions de premier niveau et constructeurs primaires du PROJET ; les témoins du document courant vérifient leur PROPRETÉ (fiche 017 — surcharge en frappe écartée) ; défauts, `vararg`, arguments NOMMÉS et sélecteurs qualifiés muets ; lambda en position comptée |
| 7 | `kt.unsafeNullable` | **Fait** | `verifierAccesNonSur` : point (jamais `?.`) sur récepteur NOMINAL au type écrit NULLABLE sans raffinement de non-nullité en position (gardes `!= null`, gardes précoces `== null`) |
| 8 | `kt.unreachable` | **Fait** | `verifierInatteignable` : la PREMIÈRE déclaration suivant un `return`/`throw` FRÈRE (un `return` imbriqué n'est pas un terminal — sous-rapport), AVERTISSEMENT |
| 9 | `kt.valReassign` | **Fait** | `verifierValReaffectation` : affectation `=` à cible NOMINALE liée à un `val` (locale, paramètre, propriété — `var` quelconque l'emporte, conservateur) ; cibles qualifiées et introuvables muettes |
| 10 | Sélecteurs sur récepteurs CLASSPATH | **Fait** | `UniversPreuves.verifierSelecteur` étendu à `TypeValeur` : le jeu JVM complet décide (résolu/absence prouvée) ; les types MAPPÉS numériques/booléens restent MUETS (`MAPPES_NON_FIABLES` — leurs membres Kotlin ne vivent pas dans le bytecode des classes encadrantes : jamais un faux positif) ; les gardes d'extensions (génériques, `Any`, récepteurs non résolus) et le régime dégradé tiennent |
| 11 | Complétion membre classpath | **Fait** | `propositionsMembres` étendu : jeu JVM + implicites + EXTENSIONS de l'index sur le FQCN Kotlin (une chaîne illisible rend la complétion VIDE d'entrées — les extensions se proposent encore, D3) ; `CompletionMembres` câblé sur le typage étendu (smart casts, attentes, littéraux) |
| 12 | `importsCandidats` | **Fait** | SPI `SessionLangageKotlin.importsCandidats(uri, deplacement)` : FQCN des ENTRÉES portant EXACTEMENT le nom simple (internes `$` et fichiers spéciaux écartés, tri déterministe) ; déjà importé (exact) ou couvert (joker) → vide ; document inconnu / guichet muet → vide honnête |
| 13 | Correctif de la vague 2a | **Fait** | ÉCART DÉCOUVERT et corrigé par les fixtures 046 : la marche des références re-marchait la RÉFÉRENCE d'un `USER_TYPE` en NOM NU après `verifierNomType` — émission DOUBLÉE d'un `kt.unresolved` sur un type introuvable (jamais exposé par les fiches 017/022/006, latente depuis 2a) ; la référence ne re-marche plus (position de TYPE, jamais un usage) |

### 6.2 Décisions d'exécution consignées (pas dans le plan initial)

1. **La vue JVM des membres est un LECTEUR MAISON** (Q-K1 tranchée À
   MINIMA) : un fichier (`resolve/VueJvm.kt`), aucune dépendance neuve,
   aucun lien inter-modules (`:kotlin` reste autonome pour le pack langue
   Android, ADR 0101), le budget dex E-K8 est épargné (+17 méthodes
   déclarées seulement) ; l'ARBITRAGE COMPLET du propriétaire (CIDX vs
   index dédié, action §4.2) DEMEURE OUVERT — la vue 2b ne le préjuge
   pas, elle couvre le besoin minimal des preuves ADR 0223.
2. **La vue est PILOTÉE PAR LES ENTRÉES** : un type absent des entrées
   (le `java.lang.String` d'un android.jar de STUB Maven — le jar de test
   4.1.1.4 n'embarque pas `java.*` ; le `kotlin.String` mappé d'un stdlib
   seul) rend un jeu `null` — le récepteur reste TYPABLE (complétion par
   les EXTENSIONS de l'index, sélecteur muet). La ressource du RUNTIME
   n'est jamais lue (déterminisme bac à sable = CI = appareil — la
   production pose un android.jar de SDK COMPLET qui porte `java.lang.*`,
   le stub de test honnêtement non).
3. **Les types MAPPÉS numériques/booléens ne sont pas absence-fiables** :
   `1.inv()`, `flag.not()` — ces membres Kotlin de `kotlin.Int`/`Boolean`
   ne vivent dans AUCUN class-file lisible (la boîte `java.lang.Integer`
   ne les porte pas) ; les muet (sous-rapport documenté, banc 2g) au
   lieu d'exposer un faux positif. `kotlin.String` et `kotlin.Any`
   restent absence-fiables (le `get` de String vit dans les méthodes
   lues ; Object est terminal connu).
4. **Les smart casts sont POSITIONNELS, jamais structurels** : la marche
   des ANCÊTRES couvrant la position applique THEN/garde précoce/`&&`/
   `when` SANS SUJET ; l'ombre par une locale postérieure coupe (recherche
   dans le THEN, la garde `P13` de la fiche 046) ; `||` annule (bornologie
   de l'ADR 0107 conservée TELLE QUELLE — parité avant extension) ; les
   ELSE, boucles et sujets de `when` restent au mode profond K2.
5. **Les témoins d'arité sont PROPRETÉ-VÉRIFIÉS pour le document
   courant** : la surcharge en cours de frappe (fiche 017) est écartée en
   cherchant la déclaration du nom dans l'arbre FRAIS ; les témoins du
   SCAN sont acceptés tels quels (un fichier non édité n'est pas en
   frappe). La propreté est vérifiée PAR NOM (une surcharge propre
   homonyme légitime le témoin — conservateur dans la direction sûre).
6. **`importsCandidats` exclut les classes INTERNES (`$`)** : l'import
   pointé d'une classe interne (`a.b.C.D`) est une raffinement possible
   mais pas un faux candidat — l'exclusion sous-rapporte (quick-fix moins
   riche, jamais faux).
7. **La double passe (références puis K4) par région propre** : la marche
   des familles K4 (`DiagnosticsTypage`) court APRÈS la marche des
   références sur la MÊME région propre (garde par déclaration fiche 017
   — « arbre en édition : tout le sémantique écarté » tient PAR
   CONSTRUCTION sur la déclaration brisée) ; le coût est un second passage
   par région, hors chemin de frappe du parseur (banc inchangé en
   latences).

### 6.3 Mesures de la vague 2b (bac à sable — JVM de bureau conteneurisée, toolchain 17)

- **Suite du module : 191 tests VERTS** (152 + 39) sous la toolchain 17 —
  fiche 046 (39/39), l'intégralité des vagues précédentes inchangée
  (010-013, 049/042/041, 002-005/008/009/034, synthétiques, garde JDK 17,
  017/022/006).
- **Baseline de parité re-signée** : corpus 335 → **340 fichiers**
  (+3 main : `VueJvm.kt`, `TypageBorne.kt`, `DiagnosticsTypage.kt` ;
  +1 test : fiche 046 ; +1 document de spécification recompté) —
  procédure documentée `-Dparite.majBase=true --rerun-tasks`.
- **Jar moteur : 565 228 o** (+65 498 vs vague 2a) — la vue JVM, le typage
  borné et les cinq familles.
- **Scan ART pile complète** (mêmes commandes que la CI) : **6 × AUCUNE
  référence sensible** — moteur **208 classes / 267 méthodes déclarées**
  (+19 classes / +17 méthodes vs vague 2a), class-file 61:208 intégral,
  ServiceLoader OK — descripteur vérifié (1 fournisseur).
- **Dexage d8** (build-tools 34, min-api 26, pile chaude complète) :
  **25 152 références de méthodes / 3 936 216 o (3,75 Mo)** — seuils étage
  0 tenus (marges 7 616 méthodes / 252 Kio) ; **alerte E-K8 actualisée :
  budget taille consommé à 94 %** (la vague ajoute 156 méthodes / 54 152 o).
- **Banc JDK 17** (toolchain du module) : parse froid 227,7/24,5 ms, p50
  4,4/25,8 ms, p95 40,2/50,4 ms — reparse p50 25,8 ms à 3 000 l. (< 50 ms,
  latences S0 tenues), déterminisme **31 368 / 24 274 IDENTIQUE** (étage 0,
  1c–2b), marqueurs d'erreur 0, tas au repos 3 700 Kio (−145 Kio vs §5.3 —
  le bruit entre passes déjà consigné §12/§14/§16/§17/§5.3).
- **Provenance** : `verifier-provenance.sh` rc=0 (aucune reprise nouvelle —
  la vague est du code NEUF intégral, `resolve/VueJvm.kt` est un lecteur
  maison écrit cahier en main).

### 6.4 Clôture de la vague 2b

**CI attendue sur le commit de livraison** (les 9 tâches : build/tests
JDK 21, bancs noyau/qualité/parité T, gates Android, provenance, et les
trois jobs Kotlin — suite du module toolchain 17 attendue à 191 tests,
scan ART 6 × AUCUNE, dexage d8 25 152 / 3 936 216 o, banc JDK 17). La
consultation du run et la clôture formelle suivent le push.

**LA VAGUE 2B EST LIVRÉE sur preuves bac à sable** : les familles
sémantiques K4 de la fiche 046 sont vertes (39/39 — typage borné, smart
casts positionnels, lambdas par attente, les cinq codes `kt.*`,
gardes KP8/ADR 0203, `importsCandidats`), la vue JVM des membres du
classpath est posée (lecteur maison piloté par les entrées) et le tableau
règles × spec est rempli (P13, A12-A17, G5 verrouillé, frontières 2b).
59/204 comportements diagnostics verts. La vague 2c (membres hérités :
clôture des supertypes, chaîne profondeur 7 du monde Views — fiches
044/028/032/040/029, 44 comportements) est la suivante.
