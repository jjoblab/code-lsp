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
> 93 %) ; la vague 2b (familles sémantiques K4, fiche 046) est la
> suivante (§5.4).**

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
| 204 comportements diagnostics verts (23 fiches du domaine) | EN COURS — 20/204 | Vague 2a (§5) : fiches 017 (8/8) + 022 (8/8) + 006 (4/4) |
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
