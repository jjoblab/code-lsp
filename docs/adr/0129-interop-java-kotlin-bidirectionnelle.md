# ADR 0129 — Interopérabilité Java↔Kotlin bidirectionnelle

- **Statut** : Proposé
- **Date** : 2026-10-31
- **Amende** : ADR 0110 (option 2 « interface publique d'interop négociée »)
- **Écarts** : GK-009, GK-010, GK-011 (KH11)
- **Répond à** : Q-K5 (DEMANDES-INTERFACE) ; **aligne** ADR Java 0022
  (décision D3 : scanner lexical « sans toucher au moteur Kotlin », segment
  CIDX partagé « il écrit, nous lisons »)

## Contexte

L'interop actuelle est unilatérale et bornée : `LecteurDeclarationsJava.kt`
lit les `.java` (déclarations, supertypes de niveau classe, accesseurs
synthétiques) mais **l'arité est sautée balancée** (:313), **toutes les
annotations sont sautées** (:106-113 — ni `@Nullable/@NonNull` androidx, ni
`@Jvm*`, ni types plateformes `T!`), et le **renommage exclut les `.java`**
(`OccurrencesProuvees.kt:144-147`). Le sens manquant est Kotlin→Java : le
moteur Java a livré un scanner lexical des `.kt` écrivant un **segment CIDX
partagé** et attend que le moteur Kotlin devienne « l'écrivain complet ».

## Sources consultées

- Audit §1.11, §3.10 ; référence §A7 (en mode K2, UN modèle de déclarations
  — le Java est exposé au front-end par adaptation), §B14 (annotations de
  nullité).
- ADR Java 0022 (contrat du segment CIDX, « il écrit, nous lisons »),
  0025 (fraîcheur inter-fichiers par empreintes STAT).
- Cahier fiches 028/029/029 (Java imbriqué, héritage mixte).

## Options

| Option | Description | Critères |
|---|---|---|
| **1 — Contrat d'échange par segment CIDX + interface publique (recommandée)** | (a) le moteur Kotlin **écrit** le segment CIDX des déclarations publiques `.kt` (FQCN, fonctions/propriétés top-level, `@JvmStatic`/`@JvmName`, facades `*Kt`) avec un front-end complet — il remplace le scanner lexical Java ; (b) `LecteurDeclarationsJava` apprend l'**arité** (lecture balancée des paramètres), les **annotations de nullité androidx** et `@Jvm*` (annotations de préférence aux accesseurs) ; (c) navigation/rename inter-langage par phases partagées avec la mission Java | cible de rappel du scanner Java ≥ 90 % reprise telle quelle ; le contrat CIDX est déjà documenté côté Java ; l'arité manquante est une cause racine de FP potentiels (audit §1.11) |
| **2 — Passerelle ad hoc par FQCN croisés** | chaque moteur expose sa propre API, pontage double | deux contrats à maintenir, divergence garantie — rejeté |
| **3 — Statu quo borné** | lecture muette, pas d'arité | l'interop est la 5e couche du déficit (audit §6) et un axe de dépassement affiché (§C.5) |

## Critères et mesures

- Types plateformes : un type Java non annoté est **plateforme** (nullabilité
  inconnue) — le palier 2 le représente (`T!` = nullabilité plateforme) et
  la politique D3 ne signale une nullabilité qu'annotée ;
- rename inter-langage : **ferroviaire** comme le rename Kotlin (vide sans
  preuve, jamais partiel silencieux) — livré en phases partagées (V-E
  mutuelle des deux missions, séquencement dans DEMANDES-INTERFACE) ;
- arité Java : `kt.argumentCount` acquiert des témoins Java (décodeur de
  listes de paramètres balancées — pas de réutilisation du compilateur).

## Décision proposée

**Option 1** : interface publique d'interop versionnée entre `:java` et
`:kotlin` (Q-K5), matérialisée par le segment CIDX partagé pour les
déclarations et par des requêtes croisées bornées pour navigation/rename ;
le moteur Kotlin devient l'écrivain du segment (D3 Java l'a prévu).

## Conséquences

- Le scanner lexical Java devient une **bascule de repli** (le bytecode
  compilé prime, miroir 0022) ;
- la cohérence des diagnostics inter-langage suit le schéma de fraîcheur
  de l'ADR Java 0025 (empreintes STAT — candidat de mutualisation, GK-038).

## Plan de migration progressive

V-E (arité + nullité androidx côté lecteur) → V-G (écriture du segment CIDX)
→ V-I (rename/navigation croisés, phases partagées avec Java).

## Tests de non-régression et critères de sortie

- Fiches 028/029 inchangées ; banc : rappel du segment CIDX ≥ 90 % (cible
  Java 0022 reprise) ; P7 rename sûr étendu aux projets mixtes du corpus
  (`viewswidgets`) ; FP < 0,5/1 000 l. maintenu.

## Risques

- R1 — Dépendance au rythme de la mission Java : les phases sont
  partagées, un désalignement est consigné dans DEMANDES-INTERFACE ;
- R2 — Coût du décodage d'annotations : borné aux seules annotations utiles
  (nullité androidx + `@JvmStatic`/`@JvmName`), liste close.
