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
> **Mise à jour (31/10, vague 4b) — LA NAVIGATION DE TYPES EST LIVRÉE
> avec la fiche 053 ENTIÈRE (7 comportements — `typeDefinition` et
> `implementations`, plan 1.3.0 L2) : la DÉFINITION DE TYPE sous le
> point (la référence de TYPE visée, le type ÉCRIT d'une propriété ou
> d'un paramètre dont le nom est sous le caret, la CLASSE CONSTRUITE
> d'un appel de constructeur — le type inféré d'un initialiseur n'est
> pas de cette vague, D3) et ses IMPLÉMENTATIONS (les héritiers
> PROUVÉS : le supertype ÉCRIT de chaque candidat résout PAR FQCN par
> les IMPORTS de SON PROPRE fichier — l'import PRIME le paquet courant,
> sémantique du compilateur — jamais par homonymie de nom simple : deux
> `Base` de paquets distincts ne se confondent pas ; document courant EN
> TÊTE, fermés du scan ensuite) — la PREUVE est PROJET : un type du
> CLASSPATH sans source projet n'est jamais servi, l'incertain rend
> VIDE.**
>
> **Mise à jour (31/10, vague 4c) — LE GO-TO SUPER EST LIVRÉ avec la
> fiche 052 ENTIÈRE (10 comportements — ADR 0210,
> `textDocument/superDefinition`, miroir des scénarios NavigationFacade
> Test du LSP Java) : les DEUX chemins du picker — (1) MEMBRE au caret
> (la déclaration OUTREPASSANTE, ou un usage RÉSOLU NON AMBIGU : le nom
> doit vivre dans EXACTEMENT UN type de la chaîne du récepteur — deux
> homonymes rendent le silence, borne O3, le moteur ne présage jamais)
> → le MÊME-NOMMÉ dans chaque supertype TRANSITIF projet du déclarant
> (`« run  ·  Base »`, triés par libellé) ; (2) la RETOMBÉE TYPE : le
> type en contexte (la référence de type sous le caret, à défaut la
> classe ENGLOBANTE) → ses supertypes DIRECTS ÉCRITS (`« Base  ·
> demo »`) — chaque supertype prouvé PAR FQCN dans le fichier de SON
> sous-type, un supertype classpath sans source projet est ABSENT,
> jamais un faux jalon.**
>
> **Mise à jour (31/10, vague 4d — CLÔTURE DE L'ÉTAGE 4) — L'ÉTAGE EST
> CLOS SUR PREUVE MÉCANIQUE : 38/38 comportements navigation (fiches
> 048/053/052 — §5, §6, §7), les DEUX critères de sortie §2 attestés
> (rappel usages = oracle : chaque usage réellement lié listé, ni plus
> ni moins — homonymes, ombrages et copies fantômes écartés ; 0 conflit
> manqué : le renommage couvre déclaration + usages + imports, liste
> VIDE sans cible prouvée) ; suite 431 tests VERTS (393 + 38 —
> exactement UN test par comportement, miroir du cahier), jar 821 750 o,
> scan ART 6 × AUCUNE (moteur 289 classes / 307 méthodes déclarées), dex
> d8 25 823 méthodes (79 % du plafond) / 3,95 Mo (66 % du budget 6 Mo),
> bancs verts (déterminisme IDENTIQUE ; complétion p95 5,07 ms, 0 saut),
> provenance rc=0 — CI VERTE 10/10 PREMIÈRE TENTATIVE sur le commit de
> clôture (run 41150305161) — et le RECALAGE N° 3 de l'effort (§8.4 : la
> dominante navigation s'est révélée MOINS COÛTEUSE que l'abaque —
> ~112 l. par comportement, l'étage 3 en coûtait ~135).**
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
| 38 comportements navigation verts (fiches 048 + 052 + 053) | **38/38** (fiche 048 — §5 ; fiche 053 — §6 ; fiche 052 — §7) | `Fiche048SessionLangageKotlinOccurrencesTest` : 21 ; `Fiche053TypesKotlinTest` : 7 ; `Fiche052SuperKotlinTest` : 10 |
| Rappel usages = oracle (chaque usage réellement lié listé, ni plus ni moins — homonymes et ombrages écartés) | **ATTEINT** (§5 : les 9 familles de references éprouvent le rappel ET la précision — l'homonyme sans preuve rend vide, l'ombrage exclut, la copie fantôme du scan n'entre pas ; §6 : l'homonymie de supertypes par imports, jamais par nom simple) | idem |
| 0 conflit manqué (le renommage couvre déclaration + usages + imports ; sans cible prouvée : liste vide, jamais un renommage partiel silencieux) | **ATTEINT** (§5 : renommage same-file / propriété de constructeur / multi-fichiers avec import / sans cible ; les cibles de types et super exigent une déclaration prouvée — §6/§7) | idem |

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
  fiche 048) ; baseline de parité RE-SIGNÉE **384 → 386 fichiers** (le
  moteur `OccurrencesProuvees.kt` et le fichier de test 048 entrent au
  corpus, les 3 fichiers main touchés re-hachés — protocole habituel
  `-Dparite.majBase=true --rerun-tasks` ; le message du commit de
  livraison 4a annonce « 385 » : COQUILLE corrigée ici, le compte réel
  est 386 — miroir des coquilles corrigées à la vague suivante).
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

## 6. Vague 4b — la navigation de types (31/10, fiche 053 — 7 comportements)

### 6.1 Périmètre exécuté

| # | Item | État | Détail |
|---|---|---|---|
| 1 | Le moteur `session/NavigationTypes.kt` | **Fait** | `typeDefinition` : la DÉFINITION DE TYPE sous le point — la référence de TYPE visée (TYPE_REFERENCE, clause de supertype, type de retour), le type ÉCRIT de la déclaration NOMMÉE (propriété, paramètre — le type inféré d'un initialiseur n'est pas de cette vague, D3), la CLASSE CONSTRUITE d'un appel de constructeur ; la cible est la DÉCLARATION du type du projet (uri + plage du nom, par FQCN) ; `implementations` : les héritiers PROUVÉS — le supertype ÉCRIT de chaque type candidat résout PAR FQCN vers la cible (document courant en tête en ordre du document, puis ouverts et fermés du scan en ordre de marche) |
| 2 | La RÉSOLUTION d'un nom écrit DANS UN FICHIER | **Fait** | Les imports EXACTS D'ABORD (l'import PRIME le paquet courant — sémantique du compilateur : `package z ; import o.Base` y désigne o.Base), puis le fichier LUI-MÊME (un type déclaré du même nom y prime), puis le paquet courant, puis l'unicité projet ; un nom QUALIFIÉ se résout par FQCN direct du modèle ; un nom du CLASSPATH ne résout pas (pas de source projet à servir — D3) |
| 3 | Le SPI | **Fait** | `typeDefinition(uri, deplacement)` et `implementations(uri, deplacement)` : `List<EmplacementKotlin>` — le montage des occurrences est PARTAGÉ (`fichiersParContenu` : l'ordre courant → ouverts → scan, pré-filtré par contenu du nom) |
| 4 | Tests neufs | **Fait** | 7 comportements aux noms exacts du cahier : `Fiche053TypesKotlinTest` — montage miroir de la fiche 048 (moteur réel, projet multi-fichiers sur disque, documents implementations sous URI DISQUE RÉELLE : le scan exclut les documents ouverts PAR URI, comme la production) |

### 6.2 Décisions d'exécution consignées

1. **L'IMPORT PRIME LE PAQUET COURANT** : la résolution d'un nom écrit
   teste les imports EXACTS AVANT le paquet du fichier — c'est la
   sémantique du compilateur (`package z ; import o.Base ; class H :
   Base()` désigne o.Base), et c'est ce qui porte la preuve d'homonymie
   du comportement `implementations-homonymie` : le `Base` de
   HeritierZ (paquet z, sans import) résout vers z.Base, PAS vers la
   cible o.Base — jamais d'homonymie acceptée.
2. **Les héritiers sont DIRECTS, documenté honnête** : un candidat entre
   s'il porte la cible dans SES supertypes ÉCRITS ; les héritiers
   INDIRECTS (petits-enfants par la clôture transitive) relèvent du
   suivi — la preuve par les imports ne s'appliquerait qu'aux clauses de
   chaque maillon de la chaîne, la fiche ne l'exige pas (D3 : mieux
   vaut sous-rapporter que deviner).
3. **La cible de typeDefinition exige sa DÉCLARATION** : sans plage du
   nom trouvée dans le fichier déclarant (type du modèle sans uri
   lisible), la cible n'est pas servie — jamais un emplacement deviné.

### 6.3 Preuves du bac à sable (avant poussée)

- **Fiche** : `Fiche053TypesKotlinTest` **7/7 VERTS à la PREMIÈRE
  exécution** (aucune retouche du moteur après son écriture).

## 7. Vague 4c — le go-to super (31/10, fiche 052 — 10 comportements)

### 7.1 Périmètre exécuté

| # | Item | État | Détail |
|---|---|---|---|
| 1 | Le moteur `session/NavigationSuper.kt` | **Fait** | Le CHEMIN MEMBRE : la déclaration OUTREPASSANTE (le site de déclaration dans une classe — corps, constructeur primaire) ou un usage RÉSOLU NON AMBIGU (sélecteur qualifié par le typage du récepteur, nom nu par la pile des this — le nom doit vivre dans EXACTEMENT UN type de la chaîne du récepteur) → le MÊME-NOMMÉ dans chaque supertype TRANSITIF projet du déclarant (`« run  ·  Base »`, TRIÉS PAR LIBELLÉ — l'ordre du picker) ; la RETOMBÉE TYPE : le type EN CONTEXTE (référence de type sous le caret, à défaut la classe ENGLOBANTE) → ses supertypes DIRECTS ÉCRITS (`« Base  ·  demo »`) |
| 2 | La CHAÎNE TRANSITIVE | **Fait** | La fermeture des supertypes ÉCRITS résolus PAR FQCN dans le fichier de CHAQUE sous-type (garde de cycle) — les maillons classpath sont ABSENTS, jamais devinés ; la même matière sert la garde d'ambiguïté (compter les porteurs du nom dans la chaîne du récepteur) et les cibles du même-nommé |
| 3 | Le SPI | **Fait** | `CibleSuperKotlin` (uri + libellé picker + plage du nom) et `superDefinition(uri, deplacement)` — les libellés sont NEUTRES (`« nom  ·  déclarant »`, `« Simple  ·  pkg »`), l'hôte les affiche tels quels (miroir des libellés ecj, jamais interprétés) |
| 4 | Tests neufs | **Fait** | 10 comportements aux noms exacts du cahier : `Fiche052SuperKotlinTest` — fixtures miroir de `NavigationFacadeTest` Java (Base/ImplA/ImplB, Greeter/EnGreeter, Main, Usage, MaVue : View du classpath sans source) |

### 7.2 Décisions d'exécution consignées

1. **La borne O3 est une GARDE D'AMBIGUÏTÉ, pas une absence de
   résolution** : le chemin membre sur un USAGE exige que le nom vive
   dans EXACTEMENT UN type de la chaîne du récepteur — deux homonymes
   (un override ET sa déclaration) rendent le silence : le moteur ne
   présage JAMAIS le membre d'un récepteur de type projet ; le chemin
   usuel (curseur sur la DÉCLARATION outrepassante) n'a pas cette garde
   — la cible y est la déclaration désignée, l'ambiguïté ne se pose
   pas.
2. **La retombée TYPE est le DEUXIÈME chemin, pas un repli de
   complaisance** : après un chemin membre sans même-nommé (une
   propriété NON outrepassante, un membre sans héritage), le picker
   montre les supertypes DIRECTS du type en contexte — la classe
   ENGLOBANTE du caret (miroir contextType ecj) ou la référence de type
   sous le caret ; les supertypes IMPLICITES (Any) ne sont jamais
   montrés (jamais écrits, jamais prouvables).
3. **Les PARTAGES internes sont consignés** : `OccurrencesProuvees.siteMembreDe` et
   `NavigationTypes.typeDuNomEcrit`/`plageNomDuType` passent `internal`
   — les trois moteurs de navigation vivent du MÊME montage (documents
   ouverts, scan re-parsé, modèle global), les partages sont nommés et
   documentés, jamais copiés.

### 7.3 Preuves du bac à sable (avant poussée)

- **Fiche** : `Fiche052SuperKotlinTest` **10/10 VERTS** (trois écarts de
  mise au vert, tous dans le FICHIER DE TEST : l'ordre des arguments
  `assertEquals(message, …)` de kotlin.test — le moteur n'a exigé
  AUCUNE retouche après sa première rédaction).

## 8. Vague 4d — la clôture de l'étage 4 (31/10)

### 8.1 Périmètre exécuté

| # | Item | État | Détail |
|---|---|---|---|
| 1 | Preuves mécaniques complètes | **Fait** | §8.3 (suite, jar, scan ART pile, dexage d8, bancs JDK 17, banc de qualité, compilation croisée, provenance) |
| 2 | Journal | **Fait** | Le présent document (en-tête des quatre vagues, §5–§7, la clôture §8) |
| 3 | ALERTES | **Fait** | E-K8 actualisée (§4 : 4b+4c portent le dex à 25 823 méthodes / 3,95 Mo — 79 % / 66 %) |
| 4 | Recalage n° 3 de l'effort | **Fait** | §8.4 (recommandé par le recalage n° 2 — la dominante navigation y est mesurée) |

### 8.2 Ce que l'étage 4 a livré (synthèse)

La NAVIGATION du moteur Kotlin est PROUVÉE : les occurrences
multi-fichiers par la preuve de liaison (references au drapeau,
documentHighlight ECRITURE/LECTURE, prepareRename, rename borné —
déclaration + usages + segments d'import, l'homonyme sans preuve rend
vide), la navigation de types (typeDefinition par les imports du
fichier du curseur, implementations par FQCN — jamais par homonymie de
nom simple), et le go-to super (les deux chemins du picker, la clôture
transitive, les bornes O3 et D3). Le germe de la vague 2e
(`definition`/`occurrences`/`symboles` périmètre document) demeure
couvert — la navigation prouvée vit à côté, le contrat de chaque
entrant est documenté au SPI.

### 8.3 Preuves du bac à sable (avant poussée)

- **Suite** : **431 tests VERTS** sur la toolchain 17 (393 + 21 + 7 + 10
  — exactement UN test par comportement du cahier, miroir de
  l'extraction) ; baseline de parité RE-SIGNÉE **386 → 390 fichiers**
  (les moteurs `NavigationTypes.kt` et `NavigationSuper.kt`, les tests
  052/053, et les 4 fichiers main re-hachés — 384 → 386 → 390 sur
  l'étage).
- **Jar moteur** : 821 750 o (+67 153 vs 3e : les trois moteurs de
  navigation + le SPI étendu).
- **Scan ART pile complète** : 6 × « AUCUNE », rc=0 (moteur 289 classes /
  307 méthodes déclarées — borne basse ; ServiceLoader OK descripteur
  vérifié).
- **Dexage d8** (build-tools 34 local, min-api 26) : **25 823 références
  de méthodes (79 % du plafond 32 768) / 4 146 236 o = 3,95 Mo (66 % du
  budget 6 Mo)** — +187 méthodes / +56 172 o sur l'étage vs la clôture
  3e ; seuils tenus (E-K8 : surveillance maintenue, ALERTES §4
  actualisée).
- **Banc JDK 17** (bac à sable, 2 cœurs) : froid 201,4/45,0 ms,
  p50 6,0/21,5 ms, p95 35,4/49,6 ms, productions 5 448/31 368 et jetons
  4 210/24 274 — déterminisme IDENTIQUE (la grammaire n'est pas
  touchée), marqueurs d'erreur 0, tas 3 489 Kio.
- **Banc de qualité** (`:kotlin:benchQualite`) : **VERT** — genres 16/16
  déterministes, sauts 0, p95 5,07 ms (baseline signée 7,53 ms — sous
  la baseline, tolérance ×1,5 tenue).
- **Compilation croisée** : `testClasses` de TOUS les modules (les 3
  méthodes d'interface ajoutées au SPI ne cassent rien — aucun autre
  module ne l'implémente).
- **Provenance** : `verifier-provenance.sh` rc=0 (aucune reprise — code
  NEUF intégral, zones noyau anciennes jamais consultées R9.1).

### 8.4 Clôture de la vague 4d, de l'ÉTAGE 4 — et recalage n° 3 de l'effort

**CI VERTE 10/10 sur le commit de clôture `89c2a7c`, PREMIÈRE TENTATIVE**
(run `41150305161`, consulté le 31/10 via l'API GitHub Actions — le run
porte le SHA de TÊTE du push : il éprouve le code de livraison `e9967c9`
ET la clôture ensemble, les dix jobs verts — suite du module, scan ART +
dexage d8 sous seuils, bancs empreinte/latence, banc qualité baseline
signée, parité croisée, build JDK 21, gates Android, provenance). Le
flottant E-K6 (périmètre Java) n'a pas frappé cette fois.

**LES TROIS CRITÈRES DE SORTIE DE L'ÉTAGE 4 (§2) SONT ATTESTÉS** :

1. **38/38 comportements navigation verts** (fiches 048/053/052 — §5,
   §6, §7) ;
2. **Rappel usages = oracle** — chaque usage réellement lié est listé,
   ni plus ni moins : l'homonyme sans preuve rend VIDE, la locale
   ombrageante reste HORS liste, la copie fantôme du scan écartée par
   sa propre déclaration, le supertype d'un héritier résout par les
   IMPORTS de son fichier, jamais par nom simple ;
3. **0 conflit manqué** — le renommage couvre déclaration + usages +
   segments d'import multi-fichiers, et rend une liste VIDE sans cible
   prouvée (l'hôte en fait une erreur typée) : jamais un renommage
   partiel silencieux.

**L'ÉTAGE 4 EST CLOS sur preuve mécanique.** L'étage 5 (confort
éditeur : formatage borné, pliage, imports, membres manquants, previews,
actions — 20–32 j-h [estimé]) est le suivant.

#### Le recalage n° 3 de l'effort (miroir des recalages n° 1 et 2)

**Ce que l'étage 4 a coûté** [mesuré — commandes : `git diff
--shortstat 782abd8..HEAD`, comptes des tests des journaux §5–§7] :

| Grandeur | Valeur | Détail |
|---|---:|---|
| Commits de l'étage | 3 | 4a (livraison poussée `9f95bf7`), 4b+4c (livraison conjointe — les deux vagues prêtes ensemble), 4d (clôture) — borne : clôture alertes étage 3 `782abd8` |
| Fichiers touchés | 12 | +4 271 / −6 lignes (main, tests, journaux, digeste) |
| `kotlin/` main | 6 fichiers, +2 602 / −3 | `OccurrencesProuvees.kt` (1 298 l.), `NavigationSuper.kt` (515 l.), `NavigationTypes.kt` (378 l.), SPI (`NavigationKotlin` +70, `SessionLangageKotlin` +169) et session (+172) |
| `kotlin/` test | 3 fichiers, +1 222 | fiches 048/052/053 (553 + 378 + 291 l. — 21 + 10 + 7 tests) |
| Tests du module | 393 → **431** | +38 pour 38 comportements — EXACTEMENT 1 test/comportement |
| Tempo [mesuré-agent] | 31/10 ~00:00 → ~00:40 (UTC+8) | 4 vagues en ~40 min de séance continue (l'étage 3 : ~5 h — la navigation est une dominante DOCUMENTÉE plus légère) |

**Comparaison à l'estimation** : la fourchette de l'étage 4 était
**24–38 j-h [estimé]** (synthèse §4). Le rythme agent n'est pas
transposable au jour-homme humain (miroir des recalages 1 et 2) : à
**~112 lignes par comportement** (≈ 4 271 l. / 38 comport. — main,
tests ET journaux compris) contre ~135 pour l'étage 3 et ~85 pour
l'étage 2, la dominante navigation s'est révélée MOINS COÛTEUSE que
l'abaque le postulait — le socle livré aux étages 1-3 (clôture
d'héritage 2c, typage borné, pile des this, scan multi-fichiers) a
porté les trois moteurs de navigation SANS aucun mécanisme neuf de
fond : les trois écarts corrigés en 4a (§5.2) étaient des erreurs de
rédaction, pas des découvertes d'architecture.

**Re-projection** [estimé] : les étages restants (5 confort, 6 serveur
LSP, T transverse) restent à l'abaque de la synthèse §4 — **44–70 j-h**
cumulés. Le recalage n° 2 conditionnait un recalage n° 3 « si écart au
rythme observé » : l'écart est À LA BAISSE, l'abaque tient (mieux :
le coût marginal baisse à mesure que le socle s'épaissit) — aucun
recalage supplémentaire n'est requis jusqu'à la clôture du programme.
Les 3 recalages du plan sont faits (n° 1 étage 2, n° 2 étage 3, n° 3
ici).
