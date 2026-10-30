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

## 1. Hiérarchie de portées (spécification Kotlin, « Scope and visibility » / imports)

| # | Règle (canon de spécification) | Statut | Où / quand |
|---|---|---|---|
| P1 | Les DÉCLARATIONS LOCALES (paramètres, `val`/`var` locaux) précèdent tout : la garde documentaire de la fiche 017 les rend résolues n'importe où dans le document (sous-rapport assumé : une locale postérieure résout aussi) | ✅ | `resolve/ResolutionReferences.resoudreNomNu` (garde) — vague 2a |
| P2 | Les MEMBRES de la classe englobante sont en portée sans sélecteur | ✅ (via la garde documentaire — les noms de membres sont des noms déclarés) | vague 2a |
| P3 | Les membres HÉRITÉS (clauses écrites) : résolus par la fermeture mixte projet/classpath | 🌙 à la vague 2a (muets : l'univers est ouvert), ✅ en 2c | fiches 044/028/040 |
| P4 | Le PACKAGE courant : ses types et appelables de premier niveau sont en portée sans import | ✅ | `ModeleProjet.appelables(paquet)` — vague 2a |
| P5 | L'import EXACT (`import a.b.Nom`) met `Nom` en portée ; l'ALIAS (`as`) le remplace | ✅ | `resoudreNomNu`/`resoudreNomType` (nom effectif) — vague 2a |
| P6 | L'import JOKER (`import a.b.*`) met TOUS les membres du package en portée | 🌙 | seuls les packages ÉNUMÉRABLES (projet) et les appelables/alias connus de l'index résolvent ; un package classpath non énumérable MUET les preuves (aucune vue JVM à la vague 2a — la vue arrive en 2b) |
| P7 | Les imports PAR DÉFAUT (`kotlin.*`, `kotlin.collections/text/io/ranges/sequences/annotation/comparisons`, `kotlin.jvm`, `java.lang`) : en portée sans import écrit | ✅ (types par le CATALOGUE KP8, appelables/alias par l'index — voie 6 ADR 0203) | `CATALOGUE_TYPES_DEFAUT`, `PAQUETS_DEFAUT` — vague 2a |
| P8 | Les récepteurs IMPLICITES (`with`/`apply` emboîtés, scopes de lambda, pile des `this` des objets anonymes) | 🌙 en 2a, ✅ en 2f (fiche 047) / 2c (vague 4) | — |
| P9 | `expect`/`actual` : `expect` d'abord | ⛔→2e | fiches 030/031 |
| P10 | Les compagnons (anonymes « Companion », nommés) : membres accessibles par le nom de la classe | ⛔→2d | fiche 024 |
| P11 | Les TYPEALIAS : carte dédiée (projet et bibliothèque) | 🌙 projet ✅ (modèle), bibliothèque ✅ via `aliasTopParNom` (fiche 037 en 2d) | vague 2a |
| P12 | Les scripts (`.kts`) : récepteurs injectés par l'hôte (Gradle…) inconnus — NOMS NUS muets | ✅ (muets, ADR 0106 « scripts nus muets ») | `UniversPreuves.script` — vague 2a |
| P13 | L'ombrage : une locale postérieure OMBRE (le cast ne fuit pas — fiche 046) | 🌙 (la garde documentaire sur-résout : jamais faux) | 2b |

## 2. Résolution d'appelés (sélection PAR PREUVE — ADR 0223 : nom + types complets)

| # | Règle | Statut | Où / quand |
|---|---|---|---|
| A1 | Un SÉLECTEUR sur récepteur typé DU PROJET sans héritage écrit porte un jeu de membres COMPLET (déclarés + implicites Any/Object + synthétisés data) : l'absence y est PROUVÉE | ✅ | `verifierSelecteur` — fiche 022, vague 2a |
| A2 | Les membres IMPLICITES de `Any`/`Object` (`equals`, `hashCode`, `toString`, `getClass`, `wait`, `notify`, `notifyAll`, `clone`, `finalize`) : jamais prouvables absents | ✅ | `MEMBRES_IMPLICITS` — vague 2a |
| A3 | Data class : `copy`/`componentN` synthétisés (lus du `MODIFIER_LIST` `data` et des paramètres `val`/`var`) | ✅ | `avecSynthesesData` — fiche 022 |
| A4 | Enum : membres implicites (`values`, `valueOf`, `entries`, `name`, `ordinal`, `Comparable`) — JAMAIS prouvables absents | ✅ (enum → non absence-fiable) | fiche 022 |
| A5 | Héritage ÉCRIT (classe ou interface, Kotlin ou Java `extends`/`implements`) : aucun membre hérité n'est prouvable absent | ✅ | `TypeProjet.absenceFiable` — fiches 022 |
| A6 | La contrainte d'un PARAMÈTRE DE TYPE (`class B<T extends C>`) n'est PAS un supertype | ✅ | `LecteurDeclarationsJava` (génériques sautés balancés) — fiche 022 |
| A7 | Les EXTENSIONS du projet sur le récepteur résolvent (récepteur écrit résolu par package à l'assemblage) | ✅ | `ModeleProjet.Batisseur.figer` — fiche 022 |
| A8 | Les extensions du CLASSPATH sur le récepteur (FQCN) résolvent — l'index doit être PORTANT | ✅ | `VueAppelables.extensionsSur` — fiches 022/006 |
| A9 | Les extensions GÉNÉRIQUES (`fun <T> T.…`, récepteur paramètre de type) atteignent TOUT récepteur : leur présence dans l'index MUET la preuve | ✅ | `verifierSelecteur` (recepteur == null) — fiche 026 en 2d |
| A10 | Les extensions sur `kotlin.Any` atteignent tout récepteur : MUET la preuve | ✅ | `verifierSelecteur` — vague 2a |
| A11 | Une extension à récepteur NON RÉSOLU (alias/qualifié) ouvre l'univers : MUET toutes les preuves de sélecteurs | ✅ | `ModeleProjet.extensionsNonResolues` — vague 2a (garde de sonorité) |
| A12 | Sélecteurs sur récepteurs CLASSPATH (String, TextView…) : vue JVM des membres | ⛔→2b | fiches 046/032/033/040 |
| A13 | La résolution par arité (surcharge) : nom + arité sur candidates prouvées | ⛔→2b | fiche 046 (`kt.argumentCount`) |
| A14 | Le typage borné : types ÉCRITS, initialiseurs typés (marche typée), retours écrits, constructeurs | ✅ (socle) | `resolve/MarcheTypee` — vague 2a |
| A15 | Smart casts POSITIONNELS (if/garde/&&/when, ombrage) | ⛔→2b | fiche 046 |
| A16 | Lambdas par ATTENTE (paramètre explicite, `it`, SAM projet) | ⛔→2b/2f | fiches 046/047 |

## 3. Garde d'édition (fiche 017 — P1-A)

| # | Règle | Statut |
|---|---|---|
| G1 | Seules les DÉCLARATIONS au sous-arbre propre sont diagnostiquées (« régions propres ») | ✅ vague 2a |
| G2 | Un nom DÉCLARÉ n'importe où (frappe en cours, récupération d'erreur) n'est JAMAIS signalé | ✅ vague 2a |
| G3 | Zone package/import brisée → preuve d'absence muette sur TOUT le document | ✅ vague 2a |
| G4 | En-tête de classe brisée → absence muette dans les membres propres (les preuves locales restent) | ✅ vague 2a |
| G5 | Une surcharge en cours de frappe n'est pas un témoin d'arité | ✅ (trivialement — la famille naît en 2b, verrouillage substantiel alors) |
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

## 5. Journal « explain » (fiche 006)

| # | Règle | Statut |
|---|---|---|
| E1 | Chaque ÉMISSION de `kt.unresolved` consignée en FINE, forme greppable `clé=valeur` (forme, sélection, récepteur typé, scan, index, script, position) | ✅ vague 2a |
| E2 | Les références RÉSOLUES ne journalisent rien | ✅ vague 2a |
| E3 | Éteint par défaut (niveau INFO : l'enregistrement paresseux ne construit RIEN) | ✅ vague 2a |

## 6. Frontières assumées (le périmètre borné et versionné — ADR 0107)

- **Aucune vue JVM à la vague 2a** : les récepteurs classpath ne sont pas
  typables, leurs sélecteurs sont muets ; les jokers de packages classpath
  muet les preuves de noms nus (la vue JVM arrive en 2b, la question du
  lecteur maison vs `:index` y sera tranchée — Q-K1 et budget dex E-K8).
- **La complétion est un GERME** (membres des récepteurs projet, libellés
  nus) : le pipeline complet (contextuelle, classement, multi-langue) est
  l'étage 3 (ADR 0113).
- **Le catalogue KP8 est un PLANCHER conservateur** : un type par défaut
  absent du catalogue et du classpath est SIGNALÉ (garde KP8) — la
  complétude du catalogue est la surface d'honnêteté du régime sans JDK ;
  l'index réel affine dès que les entrées portent les types.
- **La fidélité compilateur par frappe est hors périmètre** (preuve au
  spike S1 — ADR 0103) ; le banc de fidélité (étage 2g) mesure et PUBLIE
  le sous-rapport par famille.
