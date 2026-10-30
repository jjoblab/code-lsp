# SPECIFICATION-RESOLUTION — tableau règles × spécification × statut

> Document de spécification INTERNE du module `:kotlin` (ADR 0106, option 1 :
> « la résolution neuve est SPÉCIFIÉE avant implémentée — un tableau règles ×
> références de spécification × statut (couvert / sous-rapport / hors
> périmètre) vit dans le module, versionné ; l'implémentation le remplit
> fiche par fiche »). Chaque ligne CITE le canon (la spécification Kotlin
> publique, le chapitre porteur de la règle) ; le banc de fidélité (ADR 0119,
> étage 2g) mesurera l'écart en oracle.
>
> **Légende des statuts** : ✅ couvert (éprouvé par les fiches du cahier) ·
> 🌙 sous-rapport (le silence est ASSUMÉ — preuve ou silence, D3) · ⛔ hors
> périmètre de l'étage (le mode profond K2, ADR 0103, est la seule voie
> d'extension).
>
> **Vague 2c (fiches 044/028/032/040/029 — 44 comportements)** : la
> CLÔTURE DES SUPERTYPES (mixte projet/classpath — la voie 2-ter d'ADR
> 0216), la PILE DES THIS (objets anonymes, `apply`/`also`/`run`), les
> types IMBRIQUÉS (projet niveau 2/3, classpath `$`, héritées par nom
> simple), les CHEMINS FQN (expression et type qualifié — E4/E4bis), les
> ZONES DE CONSTRUCTION (paramètres nus du primaire, `field`), la
> dérivation ADR 0219 CONSCIENTE des descripteurs, le TERMINAL CONNU
> `java.lang.Enum`, le SURVOL des membres hérités avec propriétaire et la
> complétion membre étendue à la clôture.
>
> **Vague 2b (fiche 046 — 39 comportements)** : la VUE JVM DES MEMBRES du
> classpath (lecteur maison, décision Q-K1 à minima), les smart casts
> POSITIONNELS, les lambdas par ATTENTE, `kt.typeMismatch`/
> `kt.argumentCount`/`kt.unsafeNullable`/`kt.unreachable`/`kt.valReassign`
> et `importsCandidats`.

## 1. Hiérarchie de portées (spécification Kotlin, « Scope and visibility » / imports)

| # | Règle (canon de spécification) | Statut | Où / quand |
|---|---|---|---|
| P1 | Les DÉCLARATIONS LOCALES (paramètres, `val`/`var` locaux) précèdent tout : la garde documentaire de la fiche 017 les rend résolues n'importe où dans le document (sous-rapport assumé : une locale postérieure résout aussi) | ✅ | `resolve/ResolutionReferences.resoudreNomNu` (garde) — vague 2a |
| P2 | Les MEMBRES de la classe englobante sont en portée sans sélecteur | ✅ (via la garde documentaire — les noms de membres sont des noms déclarés) | vague 2a |
| P3 | Les membres HÉRITÉS (clauses écrites) : résolus par la FERMETURE MIXTE projet/classpath — la clôture ferme la chaîne (maillons projet relus récursivement, maillons classpath par le jeu JVM de la chaîne binaire, IMBRIQUÉES `$` comprises) ; une chaîne NON FERMÉE (supertype introuvable, enum, maillon illisible) laisse l'univers OUVERT | ✅ 2c (fiches 044/028/040) ; 🌙 chaîne non fermée (muet, D3) | `Clotures` + `UniversPreuves.clotureDe` — vague 2c |
| P4 | Le PACKAGE courant : ses types et appelables de premier niveau sont en portée sans import | ✅ | `ModeleProjet.appelables(paquet)` — vague 2a |
| P5 | L'import EXACT (`import a.b.Nom`) met `Nom` en portée ; l'ALIAS (`as`) le remplace | ✅ | `resoudreNomNu`/`resoudreNomType` (nom effectif) — vague 2a |
| P6 | L'import JOKER (`import a.b.*`) met TOUS les membres du package en portée | 🌙 | seuls les packages ÉNUMÉRABLES (projet) et les appelables/alias connus de l'index résolvent ; un package classpath non énumérable MUET les preuves de noms nus (aucune vue JVM des APPELABLES de joker — la vue 2b couvre les MEMBRES, pas l'énumération des packages) |
| P7 | Les imports PAR DÉFAUT (`kotlin.*`, `kotlin.collections/text/io/ranges/sequences/annotation/comparisons`, `kotlin.jvm`, `java.lang`) : en portée sans import écrit | ✅ (types par le CATALOGUE KP8, appelables/alias par l'index — voie 6 ADR 0203) | `CATALOGUE_TYPES_DEFAUT`, `PAQUETS_DEFAUT` — vague 2a |
| P8 | Les récepteurs IMPLICITES : la PILE DES THIS (l'objet anonyme d'abord, puis les classes englobantes — les membres déclarés du corps et les clôtures des supertypes) et la lambda traînante d'un `apply`/`also`/`run` sur récepteur TYPÉ (le récepteur devient `this`) | ✅ 2c (fiches 028/040 — `Couches`/`PileThis`) ; 🌙 `with`/scopes de lambda à paramètre → 2f (fiche 047) | `Couches.aPosition`, marche sémantique |
| P9 | `expect`/`actual` : `expect` d'abord | ⛔→2e | fiches 030/031 |
| P10 | Les compagnons (anonymes « Companion », nommés) : membres accessibles par le nom de la classe | ⛔→2d | fiche 024 |
| P11 | Les TYPEALIAS : carte dédiée (projet et bibliothèque) | 🌙 projet ✅ (modèle), bibliothèque ✅ via `aliasTopParNom` (fiche 037 en 2d) | vague 2a |
| P12 | Les scripts (`.kts`) : récepteurs injectés par l'hôte (Gradle…) inconnus — NOMS NUS muets | ✅ (muets, ADR 0106 « scripts nus muets ») | `UniversPreuves.script` — vague 2a |
| P13 | L'ombrage : une locale postérieure OMBRE (le cast ne fuit pas — fiche 046) | ✅ | la liaison POSITIONNELLE (`MarcheTypee.Liaison.invoke(nom, position)` : le dernier candidat déclaré avant l'usage) — vague 2b |

## 2. Résolution d'appelés (sélection PAR PREUVE — ADR 0223 : nom + types complets)

| # | Règle | Statut | Où / quand |
|---|---|---|---|
| A1 | Un SÉLECTEUR sur récepteur typé DU PROJET sans héritage écrit porte un jeu de membres COMPLET (déclarés + implicites Any/Object + synthétisés data) : l'absence y est PROUVÉE | ✅ | `verifierSelecteur` — fiche 022, vague 2a |
| A2 | Les membres IMPLICITES de `Any`/`Object` (`equals`, `hashCode`, `toString`, `getClass`, `wait`, `notify`, `notifyAll`, `clone`, `finalize`) : jamais prouvables absents | ✅ | `MEMBRES_IMPLICITS` — vague 2a |
| A3 | Data class : `copy`/`componentN` synthétisés (lus du `MODIFIER_LIST` `data` et des paramètres `val`/`var`) | ✅ | `avecSynthesesData` — fiche 022 |
| A4 | Enum : membres implicites (`values`, `valueOf`, `entries`, `name`, `ordinal`, `Comparable`) — JAMAIS prouvables absents | ✅ (enum → non absence-fiable) | fiche 022 |
| A5 | Héritage ÉCRIT (classe ou interface, Kotlin ou Java `extends`/`implements`) : le jeu de membres complet vient de la CLÔTURE (2c) — l'absence se prouve quand la chaîne SE FERME ; une chaîne non fermée garde le silence | ✅ (2c — fiches 022/044/028) | `Clotures.clotureDuType` + `verifierSelecteurProjet` — vagues 2a/2c |
| A6 | La contrainte d'un PARAMÈTRE DE TYPE (`class B<T extends C>`) n'est PAS un supertype | ✅ | `LecteurDeclarationsJava` (génériques sautés balancés) — fiche 022 |
| A7 | Les EXTENSIONS du projet sur le récepteur résolvent (récepteur écrit résolu par package à l'assemblage) | ✅ | `ModeleProjet.Batisseur.figer` — fiche 022 |
| A8 | Les extensions du CLASSPATH sur le récepteur (FQCN) résolvent — l'index doit être PORTANT | ✅ | `VueAppelables.extensionsSur` — fiches 022/006 |
| A9 | Les extensions GÉNÉRIQUES (`fun <T> T.…`, récepteur paramètre de type) atteignent TOUT récepteur : leur présence dans l'index MUET la preuve | ✅ | `verifierSelecteur` (recepteur == null) — fiche 026 en 2d |
| A10 | Les extensions sur `kotlin.Any` atteignent tout récepteur : MUET la preuve | ✅ | `verifierSelecteur` — vague 2a |
| A11 | Une extension à récepteur NON RÉSOLU (alias/qualifié) ouvre l'univers : MUET toutes les preuves de sélecteurs | ✅ | `ModeleProjet.extensionsNonResolues` — vague 2a (garde de sonorité) |
| A12 | Sélecteurs sur récepteurs CLASSPATH (String, TextView…) : vue JVM des membres | ✅ | `VueJvmMembres.jeuComplet` (chaîne de supertypes des ENTRÉES, propriétés dérivées ADR 0219, implicites ; `java.lang.Object` terminal connu) — fiche 046, vague 2b | 
| A12b | Les types MAPPÉS numériques/booléens (`kotlin.Int`…) : leurs membres Kotlin (plus, inv…) ne vivent pas dans le bytecode des classes encadrantes | 🌙 (sélecteurs MUETS — jamais un faux positif ; `kotlin.String` et `kotlin.Any` restent absence-fiables, `get` couvert par la lecture directe des méthodes) | `MAPPES_NON_FIABLES` — vague 2b |
| A18 | Les CHEMINS DE CLASSE qualifiés (vague 2c — fiches 044 E4/E4bis, 028/029) : en TYPE, le chemin complet décide (`android.os.Bundle`, `Aquarium.Bac`, `Conteneur.Fabrique` → `Conteneur$Fabrique`) — UN SEUL segment faillible, le DERNIER ; en EXPRESSION, un chemin d'identifiants nus à racine non valeur qui RÉSOUT rend tous les segments muets, un chemin INTROUVABLE signale la RACINE et (chemin de classe ≥ 3 segments, feuille appelée) la FEUILLE — le MILIEU (paquet) toujours muet | ✅ 2c | `UniversPreuves.cheminClasse` + `marcherCheminFqn`/`verifierTypeQualifie` |
| A13 | La résolution par arité (surcharge) : nom + arité sur candidates prouvées | ✅ (appels NUS aux fonctions de premier niveau et constructeurs primaires du projet ; défauts/`vararg`/arguments nommés et sélecteurs qualifiés MUETS) | `DiagnosticsTypage.verifierArite` (témoins PROPRES, fiche 017) — fiche 046, vague 2b |
| A14 | Le typage borné : types ÉCRITS, initialiseurs typés (marche typée), retours écrits, constructeurs, LITTÉRAUX (familles) | ✅ (socle 2a + littéraux/`TypeValeur` 2b) | `resolve/MarcheTypee` + `resolve/TypageBorne` — vagues 2a/2b |
| A15 | Smart casts POSITIONNELS (if/garde/&&/when, ombrage, `||` annule) | ✅ | `TypageBorne.raffinementAuPoint` — fiche 046, vague 2b |
| A16 | Lambdas par ATTENTE (paramètre explicite, `it`, SAM projet, type de fonction écrit) | ✅ (046) / 🌙 récepteurs implicites `with` (2f, fiche 047) | `TypageBorne.attenteLambdaAuPoint` — vague 2b |
| A17 | La nullabilité écrite (`String?`) se prouve (`kt.unsafeNullable`), gardes `!= null`/`== null` et accès sûr `?.` éteignent | ✅ | `DiagnosticsTypage.verifierAccesNonSur` + raffinements non-nuls — fiche 046, vague 2b |

## 3. Garde d'édition (fiche 017 — P1-A)

| # | Règle | Statut |
|---|---|---|
| G1 | Seules les DÉCLARATIONS au sous-arbre propre sont diagnostiquées (« régions propres ») | ✅ vague 2a |
| G2 | Un nom DÉCLARÉ n'importe où (frappe en cours, récupération d'erreur) n'est JAMAIS signalé | ✅ vague 2a |
| G3 | Zone package/import brisée → preuve d'absence muette sur TOUT le document | ✅ vague 2a |
| G4 | En-tête de classe brisée → absence muette dans les membres propres (les preuves locales restent) | ✅ vague 2a |
| G5 | Une surcharge en cours de frappe n'est pas un témoin d'arité | ✅ verrouillé 2b (le témoin du document COURANT vérifie sa PROPRETÉ sur l'arbre frais ; les témoins du scan sont acceptés) | fiche 017 + `DiagnosticsTypage.temoinsArite` |
| G6 | Le guichet d'index muet (classpath non posé / passe non publiée) → AUCUNE preuve | ✅ vague 2a (fiche 046 garde) |
| G7 | Scan PARTIEL (annulé/tronqué) → preuves d'absence muettes | ✅ vague 2a (état réfléchi — fiche 036 affine en 2e) |

## 4. Sources du projet (le modèle de symboles)

| # | Règle | Statut |
|---|---|---|
| S1 | Les fichiers Kotlin des racines nourrissent le modèle (types, membres, appelables, extensions, supertypes écrits, data/enum) | ✅ vague 2a |
| S2 | Les fichiers JAVA des racines nourrissent le modèle (classes, `extends`/`implements` de NIVEAU CLASSE, méthodes, champs, propriétés synthétiques des accesseurs — ADR 0219) | ✅ vague 2a (fiche 022) |
| S3 | Les enum/record Java portent un supertype marqueur (`Enum`/`Record`) : jamais absence-fiables | ✅ vague 2a (garde de sonorité) |
| S4 | Les sources GÉNÉRÉES sous `build/` (KSP/Hilt/Room/DataBinding) | ⛔→2e | fiche 035 |
| S5 | La troncation du scan (borne de collecte) réfléchie dans l'état | ✅ (complet/partiel) — fiche 036 affine en 2e |

| S6 | Les types IMBRIQUÉS des sources (vague 2c — fiche 029) : niveau 2 et niveau 3 extraits en récursif BORNÉ — FQCN pointé `paquet.Conteneur.Imbriquee`, chaque imbriquée AUSSI membre TYPE_IMBRIQUE de son conteneur ; les marqueurs `Enum`/`Record` du lecteur Java laissent la clôture OUVERTE | ✅ 2c | `ModeleProjet` (chemin d'englobants + `imbriquesDu`), `LecteurDeclarationsJava` |
| S7 | Le FRÈRE OUVERT VIVANT prime son doublon du scan (vague 2c — fiche 044 : la clôture lit l'arbre VIF, jamais le disque périmé) ; le document courant primait déjà (2a) | ✅ 2c | `SessionLangageKotlinDocuments.contributionsAuConsult` |
## 5. Journal « explain » (fiche 006)

| # | Règle | Statut |
|---|---|---|
| E1 | Chaque ÉMISSION de `kt.unresolved` consignée en FINE, forme greppable `clé=valeur` (forme, sélection, récepteur typé, scan, index, script, position) | ✅ vague 2a |
| E2 | Les références RÉSOLUES ne journalisent rien | ✅ vague 2a |
| E3 | Éteint par défaut (niveau INFO : l'enregistrement paresseux ne construit RIEN) | ✅ vague 2a |

## 6. Frontières assumées (le périmètre borné et versionné — ADR 0107)

- **La vue JVM des membres est PILOTÉE PAR LES ENTRÉES** (vague 2b, décision
  Q-K1 à minima : LECTEUR MAISON — un fichier, aucune dépendance neuve,
  aucun lien inter-modules, le budget dex E-K8 est épargné ; l'arbitrage
  complet CIDX vs index dédié DEMEURE au propriétaire) : un type absent des
  entrées (le `java.lang.String` d'un jar de stub, le `kotlin.String` mappé
  d'un stdlib seul) rend un jeu `null` — le récepteur reste TYPABLE mais
  jamais ABSENCE-FIABLE (sélecteur muet, complétion membre limitée aux
  EXTENSIONS de l'index). La ressource du RUNTIME n'est JAMAIS lue
  (déterminisme bac à sable = CI = appareil), la vue de l'hôte n'est jamais
  consultée (D6 : le moteur est autonome).
- **Les types MAPPÉS numériques/booléens ne sont pas absence-fiables** : les
  membres Kotlin de `kotlin.Int` (plus, inv, not…) ne vivent pas dans le
  bytecode de `java.lang.Integer` — leurs sélecteurs restent muets
  (sous-rapport documenté, jamais un faux positif) ; `kotlin.String` et
  `kotlin.Any` le sont (le `get` de String vit dans les méthodes lues).
- **Les smart casts sont POSITIONNELS et bornés** : branche THEN, garde à
  sortie précoce (`!is`/`== null` + `return`/`throw`), opérande droit d'un
  `&&`, corps d'une entrée de `when` sans sujet ; l'ombrage par une locale
  postérieure coupe ; un `||` ANNULE (bornologie ADR 0107 conservée) ; les
  casts NÉGATIFS en branche (ELSE), les boucles et les sujets de `when`
  restent hors périmètre (mode profond K2).
- **Les témoins d'arité sont les déclarations PROPRES du projet** : appels
  NUS aux fonctions de premier niveau et constructeurs primaires ; les
  défauts, `vararg`, arguments NOMMÉS et sélecteurs qualifiés (membres)
  restent muets ; les témoins du document courant vérifient leur propreté
  sur l'arbre frais (fiche 017).
- **`kt.typeMismatch` décide sur les LITTÉRAUX seuls** : famille du littéral
  vs famille du type écrit (chaîne/numérique/booléen/caractère) ; les
  conversions numériques legitimes et la nullabilité ne comptent pas ; tout
  initialisateur non littéral est muet (D3).
- **`kt.unreachable` signale la PREMIÈRE déclaration après un terminal
  FRÈRE** (return/throw) ; un `return` imbriqué n'est pas un terminal —
  sous-rapport assumé.
- **`importsCandidats` propose les FQCN des ENTRÉES portant EXACTEMENT le nom
  simple** (classes de premier niveau, internes `$` écartées), triés ; un
  nom déjà importé (exact) ou couvert (joker) rend une liste vide ; le
  régime dégradé rend une liste vide honnête.
- **La complétion reste un GERME** (libellés nus, ordre de rencontre) : le
  pipeline complet (contextuelle, classement, multi-langue) est l'étage 3
  (ADR 0113).
- **La clôture d'héritage est FERMÉE ou MUETTE (vague 2c)** : les supertypes
  écrits se résolvent par le MODÈLE (package courant, nom unique), les
  IMPORTS EXACTS et le catalogue KP8 — JAMAIS par nom simple du classpath
  (ADR 0223 : aucune résolution devinée) ; un maillon introuvable, un enum,
  un marqueur `Enum`/`Record` Java ou un maillon JVM illisible des entrées
  laisse l'univers OUVERT (les preuves d'absence s'y taisent, jamais un
  faux positif). Les imbriquées des SUPERTYPES entrent à la clôture par
  leur nom simple (`Resultats` de `Base$Resultats`), miroir de la règle
  kotlinc ; `java.lang.Enum` est un TERMINAL CONNU (socle conservateur des
  récepteurs d'enum — ses membres implicits y sont sur-approximés).
- **La dérivation ADR 0219 lit les DESCRIPTEURS (vague 2c)** : un getter à
  PARAMÈTRE ne synthétise RIEN (`getDrawable(I)` n'est pas `drawable`) ; un
  getter nu dérive une propriété VAL, un setter à un paramètre une VAR ; le
  typage et le survol des membres hérités suivent le descripteur (retour du
  getter, paramètre du setter), les surcharges au retour DIVERGENT restent
  muettes (jamais faux).
- **La pile des this est bornée aux couches LISIBLES** : les lambdas
  traînantes d'`apply`/`also`/`run` sur récepteur typé portent le récepteur
  en `this` (heuristique documentée — la couche n'ajoute que du muet) ;
  `with` et les scopes à paramètre restent 2f ; les ZONES DE CONSTRUCTION
  (paramètres NUS du primaire, `field` des accesseurs) gardent leurs noms,
  les méthodes NON (miroir kotlinc — un paramètre nu y est SIGNALÉ).
- **Les chemins FQN sont conservateurs** : en expression, la racine non
  valeur et la feuille appelée (chemin ≥ 3 segments) seules sont faillibles ;
  en type, le DERNIER segment seul ; les milieux (paquets) jamais.
- **La fidélité compilateur par frappe est hors périmètre** (preuve au
  spike S1 — ADR 0103) ; le banc de fidélité (étage 2g) mesure et PUBLIE
  le sous-rapport par famille.
