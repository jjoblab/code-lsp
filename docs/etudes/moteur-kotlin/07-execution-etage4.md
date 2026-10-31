# 07 — Exécution étage 4 : navigation + rename

> **Décision du propriétaire reçue le 2026-10-31** dans le canal de mission :
> « Go étage 4 » — les chantiers de l'étage 4 (plan §4 de `00-synthese.md`)
> sont OUVERTS sur la branche `etude/moteur-kotlin`, à la suite de la
> clôture sur preuve mécanique de l'étage 3 (`06-execution-etage3.md` §9.4 :
> CI VERTE 10/10 sur le commit de livraison 3e `cb562f2` — run `41142982589`,
> première tentative —, 44/44 comportements complétion, banc commun Java
> livré et SIGNÉ : livrée 1.000/1.000/1.000 vs naïve +62,5 pts top-1 /
> +51,4 pts MRR, p95 7,53 ms, zéro saut ; recalage n° 2 exécuté). Le présent
> document est le journal d'exécution de l'étage (méthode : miroir des
> rapports précédents — chiffres étiquetés [mesuré] / [estimé], écarts
> signalés).
>
> Gouvernance inchangée : branche `etude/moteur-kotlin` seule ; zones NOYAU
> anciennes (`session/`, `resolve/` de production) en lecture interdite
> R9.1 — l'étage 4 est du code NEUF écrit cahier en main (le germe livré en
> 2e — `definition()`/`occurrences()`/`symboles()` périmètre document —
> reste couvert : la navigation PROUVÉE s'y greffe SANS régression). Les
> tests sources anciens restent consultables (R9.5) pour les FIXTURES,
> jamais copiés.
>
> **Mise à jour (31/10, vague 4a) — LA PREUVE DE LIAISON DES OCCURRENCES
> EST LIVRÉE avec la fiche 048 ENTIÈRE (21 comportements — le contrat S2
> « references / documentHighlight / rename borné » du plan de
> stabilisation LSP §3.2) : la CIBLE sous le point (site de déclaration,
> usage NU par la portée positionnelle — locale et paramètre ombrage
> exclu —, membre NU par la pile des this, appelable et type du MÊME
> FICHIER d'abord puis du paquet et des imports, membre QUALIFIÉ par le
> typage du récepteur et la clôture — `proprietaireFqcn` prouve le
> déclarant ; le repli ADR 0218 : le curseur sur le MOT-CLÉ `fun`/`class`
> vise la déclaration ENGLOBANTE, le CORPS ne vise rien) ; les USAGES
> LIÉS MULTI-FICHIERS (document courant, documents ouverts, fichiers
> FERMÉS du scan re-parsés paresseusement à la demande — pré-filtre par
> contenu, décision d'architecture consignée §5.3) ; la PREUVE DE LIAISON
> d'un usage dans un fichier : import EXACT (alias compris — le segment
> du chemin importé est une référence à éditer, miroir IntelliJ), ou MÊME
> PAQUET sans homonyme top-level local (l'homonyme local du fichier
> d'usage PRIME — la copie fantôme du scan est naturellement écartée),
> ou MÊME FICHIER déclarant (ADR 0204 : la garde d'homonymie ne confond
> plus la cible avec elle-même) ; `references` AU DRAPEAU
> `inclureDeclaration` ; `surlignagesDocument` (déclaration ECRITURE,
> usages LECTURE, ombrage exclu — documentHighlight LSP) ;
> `plageRenommable` (placeholder exact sur le nom déclaré, `null` hors
> identifiant — jamais deviné) ; `renommage` borné (déclaration + usages
> + imports multi-fichiers, liste VIDE sans cible prouvée — l'hôte en
> fait une erreur typée).**

## 1. Périmètre de l'étage (plan §4 — cahier en main)

| # | Item | Source du contrat |
|---|---|---|
| 1 | **Occurrences prouvées (S2)** : `references` multi-fichiers au drapeau de déclaration (locale du fichier déclarant ombrage exclu, paramètre confiné au corps, appelable top-level même paquet autre fichier fermé du scan, import explicite d'un autre paquet, homonyme sans preuve → vide D3, classe import + usage + import renommé, membre qualifié récepteur typé, appelable du même fichier déclarant ADR 0204, propriété de constructeur usage nu ADR 0206, repli mots-clés `fun`/`class` ADR 0218 — le corps ne vise rien) | fiche 048 (21 comportements) |
| 2 | **documentHighlight** : déclaration en ÉCRITURE, références en LECTURE, ombrage exclu, en ordre du document ; curseur sur référence = mêmes occurrences | fiche 048 (2 comportements) |
| 3 | **Rename borné** : `plageRenommable` (placeholder exact, `null` hors identifiant), `renommage` même fichier (ADR 0204), propriété de constructeur (ADR 0206), multi-fichiers déclaration + usages + import, sans cible prouvée → liste vide (l'hôte en fait une erreur typée) | fiche 048 (6 comportements) |
| 4 | **Navigation de types (L2)** : `typeDefinition` (référence de type, symbole typé par le type écrit, constructeur → la classe construite, incertain → vide), `implementations` (héritiers prouvés — document courant en tête, fermés du scan ensuite ; homonymie : le supertype résout par les IMPORTS du fichier, pas par nom simple ; incertain → vide) | fiche 053 (7 comportements) |
| 5 | **Go-to Super (ADR 0210)** : déclaration outrepassante → le même nommé du supertype DIRECT, clôture TRANSITIVE (deux niveaux), implémentation d'interface, usage AMBIGU d'un override → vide honnête (borne O3), usage UNIQUE → les supertypes du déclarant, propriété NON outrepassante → retombée classe englobante, référence de type → les supertypes du type visé, classe racine sans clause → aucune cible (D3), supertype classpath SANS source projet → aucune cible (D3) | fiche 052 (10 comportements) |
| 6 | **Tolérance** : document inconnu et session fermée — listes vides, sans exception, sur TOUTES les entrées de navigation | fiches 048/052/053 |

## 2. Critères de sortie de l'étage 4 (synthèse §4 — suivi)

| Critère | État | Preuve |
|---|---|---|
| 38 comportements navigation verts (fiches 048 + 052 + 053) | **21/38** (fiche 048 — §5 ; fiches 053/052 à venir) | `Fiche048SessionLangageKotlinOccurrencesTest` : 21 |
| Rappel usages = oracle (chaque usage réellement lié listé, ni plus ni moins — homonymes et ombrages écartés) | **ATTEINT sur la fiche 048** (§5 : les 9 familles de references éprouvent le rappel ET la précision — l'homonyme sans preuve rend vide, l'ombrage exclut, la copie fantôme du scan n'entre pas) | idem |
| 0 conflit manqué (le renommage couvre déclaration + usages + imports ; sans cible prouvée : liste vide, jamais un renommage partiel silencieux) | **ATTEINT sur la fiche 048** (§5 : renommage same-file / propriété de constructeur / multi-fichiers avec import / sans cible) | idem |

## 3. Plan de vagues (cahier en main — l'ordre suit la dépendance des mécanismes : la preuve de liaison d'abord, elle nourrit types et super ensuite)

| Vague | Périmètre | Fiches | Comportements |
|---|---|---|---:|
| **4a** | La PREUVE DE LIAISON des occurrences : cible sous le point (site, portée positionnelle, pile des this, typage du récepteur qualifié, repli mots-clés ADR 0218), usages liés multi-fichiers (documents ouverts + fermés du scan re-parsés à la demande, preuve par import/paquet/fichier, alias d'import, segment d'import éditable), `references` au drapeau, `surlignagesDocument` ECRITURE/LECTURE, `plageRenommable`, `renommage` borné | 048 | 21 |
| **4b** | Navigation de types : `typeDefinition` (référence de type, symbole typé, constructeur, incertain → vide) et `implementations` (héritiers prouvés, document en tête, homonymie par imports) — la clôture d'héritage livrée en 2c y est la matière | 053 | 7 |
| **4c** | Go-to Super (ADR 0210) : les DEUX chemins du picker (déclaration outrepassante → supertype direct, usage résolu → supertypes du déclarant), clôture transitive, retombées honnêtes (O3, D3) | 052 | 10 |
| **4d** | Clôture : preuves mécaniques complètes (suite, jar, scan ART pile, dexage d8, bancs JDK 17, provenance), journal, ALERTES (E-K8), recalage n° 3 de l'effort (recommandé par le recalage n° 2 §9.4) | — | — |

**Total : 38 comportements** (agrégat du cahier — INVENTAIRE.md, domaine
navigation). Chaque vague : code + tests NEUFS au nom exact du cahier +
CI 10 tâches verte + section du présent journal + commit/push.

## 4. Actions propriétaire ouvertes (reprises de l'étage 3 — §4 du journal précédent)

| # | Action | Référence | Bloque |
|---|---|---|---|
| 1 | Audit de similarité GPL (P-K1) — la copie R1/R2 est effectuée, l'audit recommandé (rôle distinct des implémenteurs) s'exerce a posteriori | manifeste §3/§6, ALERTES §2 | plus rien de dur — le propriétaire en reste juge |
| 2 | Arbitrages Q-K1 (CIDX vs index dédié) / Q-K2 (décodage @Metadata maison) — PARTIELLEMENT ARBITRÉ ; ouverts pour la structure | DEMANDES-INTERFACE, ADR 0108, ALERTES §4 | le câblage classpath final |
| 3 | Conditions binaires intellij-dependencies (P-K2) | ADR 0120, NOTICE | la PUBLICATION du pack langue |
| 4 | Lecture du second manifeste | synthèse §6.4 | rien |
| 5 | Passe appareil couvrant les étages 1-3 (kit enrichi) — recommandation consignée, OPTIONNELLE | `04-execution-etage1.md` §16 | rien (les latences S0 restent tenues en JVM) |
| 6 | Recommandation E-K6 (test flottant Java `IntegrationMoteurIndexTest` : tolérance de tempo) — 2 occurrences CI consignées, le flottement demeure | ALERTES §4 | rien (périmètre Java) |

## 5. Vague 4a — la preuve de liaison des occurrences (31/10, fiche 048 — 21 comportements)

### 5.1 Périmètre exécuté

| # | Item | État | Détail |
|---|---|---|---|
| 1 | Le MOTEUR `session/OccurrencesProuvees.kt` | **Fait** | La CIBLE sous le point (site de déclaration, usage NU par la cascade de portées — locale/paramètre lié POSITIONNELLEMENT avec l'ombre la plus récente avant l'usage, membre NU par la PILE des this, déclaration top-level du MÊME FICHIER d'abord ADR 0204, type du modèle, import exact, appelable du paquet —, sélecteur QUALIFIÉ par le TYPAGE BORNÉ du récepteur puis la CLÔTURE : `MembreHerite.proprietaireFqcn` désigne le déclarant ; le repli ADR 0218 : le curseur sur le MOT-CLÉ de déclaration (`fun`/`class`/`interface`/`object`/`val`/`var`/`typealias`) vise la déclaration ENGLOBANTE dont l'EN-TÊTE couvre le caret — le CORPS ne vise rien) ; les USAGES LIÉS MULTI-FICHIERS (document courant, documents ouverts, fichiers FERMÉS du scan re-parsés paresseusement) ; la PREUVE DE LIAISON par fichier : import EXACT (le SEGMENT du chemin importé est une référence à éditer, l'ALIAS rend l'usage sous son alias), MÊME PAQUET sans homonyme top-level local de la même FAMILLE (types et appelables ne se masquent pas entre eux), JOKER porteur UNIQUE (l'ambiguïté de jokers rend le silence), MÊME FICHIER déclarant ; les GARDES d'homonymie : la locale ombrageante, l'homonyme d'un autre paquet sans import, le fichier qui DÉCLARE lui-même le symbole (même fqcn déclarant pour un membre — la COPIE FANTÔME du scan d'un document ouvert est écartée par sa propre déclaration) restent HORS liste |
| 2 | Le SPI `api/` | **Fait** | `GenreSurlignageKotlin` (ECRITURE/LECTURE — l'hôte traduit en DocumentHighlightKind), `SurlignageDocumentKotlin` (plage + genre), `PlageRenommableKotlin` (plage + placeholder) ; le contrat [SessionLangageKotlin] gagne `references(uri, deplacement, inclureDeclaration)`, `surlignagesDocument`, `plageRenommable`, `renommage` — la classe d'erreurs de l'hôte reste la sienne (liste vide = erreur typée, jamais une exception du moteur) |
| 3 | Le CÂBLAGE session | **Fait** | `montageOccurrences()` (HORS verrou : documents ouverts copiés — arbres vifs immuables, contributions des ouvertes VIVES primant leurs doublons scannés, guichet de classpath, URIs des fichiers Kotlin du scan non ouverts) ; les 4 entrées du SPI rendent leur réponse par le moteur, la lecture disque et le re-parse des fichiers fermés ne tiennent JAMAIS le verrou (ADR 0212) |
| 4 | `references` AU DRAPEAU | **Fait** | `inclureDeclaration = true` met le site de déclaration en TÊTE (miroir Find Usages) ; `false` rend les seuls usages — l'ordre : le fichier déclarant, le document courant, les autres documents ouverts, puis les fichiers du scan en ordre de marche ; les usages de CHAQUE fichier en ordre du document |
| 5 | `surlignagesDocument` | **Fait** | documentHighlight : la déclaration ECRITURE (si le document la porte) puis chaque référence LECTURE, en ordre du document, ombrage exclu — le curseur sur un USAGE rend les MÊMES occurrences (la cible se ré-résout depuis l'usage) |
| 6 | `plageRenommable` | **Fait** | prepareRename : la plage de l'IDENTIFIANT sous le caret (usage ou déclaration) et son placeholder EXACT ; `null` hors identifiant ou sans cible prouvable — jamais deviné |
| 7 | `renommage` BORNÉ | **Fait** | rename : la déclaration + chaque usage lié (usages ET segments d'import) multi-fichiers — liste VIDE sans cible prouvée (l'hôte en fait une erreur typée) ; les usages d'un ALIAS d'import restent sous l'alias, seul le segment du chemin importé est édité (miroir IntelliJ) |
| 8 | La NON-RÉGRESSION | **Fait** | Le germe 2e demeure : `occurrences()` (périmètre document, sans preuve — fiche 025), `definition()`, `symboles()` inchangés ; la marche des SITES ne touche ni au survol ni aux diagnostics |
| 9 | Tests neufs | **Fait** | 21 comportements aux noms exacts du cahier : `Fiche048SessionLangageKotlinOccurrencesTest` — moteur RÉEL nourricier (chemin de production D6), projet multi-fichiers sur DISQUE (racines du scan), la convention `fixture://` DUPLIQUE le document ouvert dans le scan (la copie fantôme y est écartée par la garde d'homonymie — l'URI RÉELLE des comportements ADR 0218 garantit l'exclusion par identité, miroir de la production didOpen/scan) |

### 5.2 Trois écarts découverts et corrigés pendant la mise au vert (le cahier comme sonde)

1. **Le déclarant d'un MEMBRE est la CLASSE PORTEUSE, pas le membre** : la
   première rédaction de la cible d'un site membre concaténait le nom du
   membre au chemin (`outils.Boite.ouvrir`) — la preuve par
   `MembreHerite.proprietaireFqcn` (le FQCN de la classe qui DÉCLARE,
   `outils.Boite`) n'attrapait plus RIEN (membre qualifié et membre nu
   muets). Corrigé : le déclarant est le CHEMIN des types englobants SANS
   le nom du membre — miroir exact de la clôture.
2. **Le BLOC d'une fonction top-level n'est pas le NIVEAU DU FICHIER** : la
   première rédaction marquait TOP le bloc rencontré sous un conteneur TOP
   (règle pensée pour le bloc racine d'un SCRIPT) — une LOCALE de fonction
   top-level devenait un APPELABLE top-level, la garde d'homonymie ne
   distinguait plus l'ombrage (4 occurrences au lieu de 3 pour la locale
   ombragée). Corrigé : un BLOC rencontré en marche est TOUJOURS un corps
   (BLOC) ; le bloc racine d'un script vit à part — ses déclarations sont
   exposées au niveau TOP par les porteurs de la marche, jamais par elle.
3. **La COPIE FANTÔME du scan doit être écartée pour les MEMBRES aussi** :
   un document ouvert sous une URI distincte de son fichier (la convention
   `fixture://` des tests, miroir d'un didOpen sur fichier déplacé) vit
   DEUX fois dans la matière (l'arbre vif + le fichier scanné) ; pour les
   appelables et les types, la garde d'homonymie top-level écartait
   naturellement la copie (ses usages se lient à SA déclaration) ; pour un
   MEMBRE, la copie déclare le même membre du même FQCN — la garde
   d'HOMONYMIE DÉCLARANTE (même fqcn déclarant) l'écarte désormais
   pareillement (miroir de la garde top-level, jamais un doublon d'usages).

### 5.3 Décisions d'exécution consignées

1. **Le re-parse paresseux des fichiers FERMÉS est le prix honnête de la
   navigation multi-fichiers** : le scan ne garde NI texte NI arbre (budget
   mémoire appareil — la passe produit un modèle de SYMBOLES, pas des
   documents) ; une requête de navigation est PONCTUELLE (pas une frappe),
   chaque fichier candidat est lu (PRÉ-FILTRE par contenu : le nom ou son
   alias potentiel doit y vivre) puis parsé À LA DEMANDE, avec cache par
   requête (une seule lecture et un seul parse par fichier et par requête).
   Le coût est mesuré au banc (§5.4 : les latences de frappe ne bougent
   PAS — la navigation ne court pas sur le chemin de la frappe).
2. **La preuve d'un usage vit dans le FICHIER DE L'USAGE, pas dans celui de
   la cible** : un usage n'entre dans la liste que si le nom RÉSOUT dans
   SON fichier vers la cible (import exact, même paquet sans homonyme
   local, même fichier déclarant) — c'est la sémantique du compilateur
   (l'homonyme local PRIME), et c'est ce qui écarte mécaniquement la copie
   fantôme : sa déclaration homonyme détourne SES usages vers elle.
3. **Les FAMILLES ne se masquent pas entre elles** : la garde d'homonymie
   d'un TYPE ne regarde que les types top-level locaux, celle d'un
   APPELABLE que les appelables (`class X` et `fun X` coexistent en
   Kotlin) ; le JOKER d'import ne prouve que s'il est PORTEUR UNIQUE (deux
   jokers porteurs du même nom = ambiguïté, silence D3).
4. **Le repli mot-clé vise l'EN-TÊTE, jamais le corps** : la déclaration
   englobante n'est visée que si le caret vit AVANT le début de son corps
   (BLOCK d'une fonction, CLASS_BODY d'un type) — `fun saluer(): String =
   nom` (corps d'expression) est tout entier en-tête, `return` dans un
   bloc ne vise RIEN (le comportement ADR 0218 l'exige : le corps n'est
   pas l'en-tête).
5. **La déclaration d'un membre introuvable invalide la cible** : sans
   plage de déclaration (fichier déclarant illisible, membre synthétique),
   la cible n'EXISTE pas — un renommage sans déclaration serait un
   renommage partiel (0 conflit manqué exige mieux, D3).

### 5.4 Preuves du bac à sable (avant poussée)

- **Suite** : **414 tests VERTS** sur la toolchain 17 (393 + 21 de la
  fiche 048) ; baseline de parité RE-SIGNÉE **384 → 385 fichiers** (le
  fichier de test 048 entre au corpus, protocole habituel
  `-Dparite.majBase=true --rerun-tasks`).
- **Jar moteur** : 794 597 o (+39 902 vs 3e — le moteur d'occurrences :
  1 fichier main ~1 280 l. + le SPI).
- **Scan ART pile complète** : 6 × « AUCUNE », rc=0 (moteur 281 classes /
  302 méthodes déclarées — borne basse ; ServiceLoader OK descripteur
  vérifié).
- **Dexage d8** (build-tools 34 local, min-api 26) : **25 751 références
  de méthodes (79 % du plafond 32 768) / 4 124 124 o = 3,93 Mo (66 % du
  budget 6 Mo)** — +115 méthodes / +34 060 o vs 3e ; seuils tenus (E-K8 :
  surveillance maintenue, actualisation consignée dans ALERTES.md §4).
- **Banc JDK 17** (bac à sable, 2 cœurs) : froid 317,5/23,4 ms,
  p50 6,0/20,5 ms, p95 38,9/50,2 ms, productions 5 448/31 368 et jetons
  4 210/24 274 — déterminisme IDENTIQUE (la grammaire n'est pas
  touchée), marqueurs d'erreur 0, tas 3 014 Kio.
- **Banc de qualité** (`:kotlin:benchQualite`) : **VERT** — genres 16/16
  déterministes, sauts 0, p95 8,34 ms (baseline signée 7,53 ms, tolérance
  ×1,5 = 11,3 — tenue).
- **Compilation croisée** : `testClasses` de TOUS les modules du dépôt
  (aucun autre module n'implémente ni ne référence le SPI Kotlin —
  l'ajout des 4 méthodes d'interface ne casse rien).
- **Provenance** : `verifier-provenance.sh` rc=0 (aucune reprise — code
  NEUF intégral, zones noyau anciennes jamais consultées R9.1).
