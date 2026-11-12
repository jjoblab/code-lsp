# Journal d'exécution — Vague V-I2 : Palier 2 du protocole (ADR 0136 B.3)

> Vague V-I2 du [plan](../04-plan-vagues.md) — exécutée le **12/11/2026**.
> Périmètre (ADR 0136 étape B.3) : `semanticTokens` BORNÉ (KH1/ADR 0123),
> `typeHierarchy`, `callHierarchy`, `codeLens` (occurrences paresseuses
> bornées). Les 4 capacités absentes de la matrice passent
> ANNONCÉES+SERVIES une à une — « jamais annoncée sans être servie ».

## 1. Ce qui est livré

1. **`semanticTokens/full`** (GK-025) : la coloration SÉMANTIQUE bornée
   — `ColorationSemantique.kt` (moteur) classe chaque identifiant
   PORTANT par sa PREUVE (déclarations typées : genre du modèle ;
   références : résolution type/appelable/index), le jeton porte les
   modificateurs ÉCRITS (`declaration`, `static` [compagnon], `final`
   [`val`], `abstract`) et JAMAIS la variance ni la nullabilité inférées
   (KH1 — risque R2 contré par la borne). La LÉGENDE est contractuelle
   (miroir exact de la légende Java V-G : `class`, `interface`, `enum`,
   `decorator`, `method`, `property`, `parameter`, `variable`) ; la
   réponse est le VECTEUR EN DELTAS RELATIFS (5 entiers/jeton — miroir
   du vecteur Java). Les IMPORTS, mots-clés et littéraux ne sont pas
   jetonisés (la coloration syntaxique du client les couvre) ; un nom
   SANS preuve est IGNORÉ (une tokenisation partielle vaut mieux qu'une
   couleur fausse — D3).
2. **`typeHierarchy`** (GK-026) : `prepareTypeHierarchy` rend l'item du
   type PROJET sous le point (la préparation se fait sur l'USAGE ou la
   DÉCLARATION) ; `supertypes` = la clause ÉCRITE résolue (maillons
   projet navigables, maillons classpath rendus au FQCN SANS uri —
   l'affichage borné honnête) ; `subtypes` = les types du modèle dont la
   clause résout vers l'item (preuve par FQCN, miroir
   d'`implementations`).
3. **`callHierarchy`** (GK-026) : `prepareCallHierarchy` sur la
   déclaration OU l'appel d'un appelable de premier niveau ;
   `incomingCalls` = les appelants du document dont le corps appelle le
   nom NU (arêtes avec plages FROM) ; `outgoingCalls` = les appelés
   NU du CORPS de la déclaration (arêtes avec plages TO).
4. **`codeLens`** (GK-026) : chaque déclaration de premier niveau porte
   une lentille du COMPTE de ses usages documentaires (mot complet —
   périmètre BORNÉ au document, la preuve de LIAISON complète relève du
   moteur d'occurrences) ; la DONNÉE de la lentille (nom, usages)
   voyage, le command reste à la charge du client (contrat LSP) ; PAS
   de resolveProvider (la lentille est complète).
5. **La matrice de capacités passe 26 annoncées / 0 absentes** : les
   `assertNull` du cas doré `matrice-paritaire` deviennent des
   `assertNotNull` + légendes exactes (USAGE.md §2.2 mis à jour).

## 2. Les preuves

| Preuve | Résultat |
|---|---|
| Tests moteur (`CapacitesPalier2KotlinTest`) | **8 verts** — genres prouvés + modificateurs écrits (interface/classe/méthode/paramètre), tri par offset + fusion déclaration-gagne, liste vide honnête (I9), préparation types + supertypes écrits + sous-types par FQCN, préparation appels + entrants/sortants du corps FUN, lentilles avec comptes exacts, comptes documentaires honnêtes |
| Cas doré `matrice-paritaire` | légendes exactes + callHierarchy/typeHierarchy annoncées (26/0) |
| Suite `:kotlin:test` | **586 verts** (577 + 8 + CasDores), 3 ignorés (gates corpus) |
| Re-signature du digeste (E-K9) | COMMANDE OFFICIELLE fiche 010 ; diff = 2 fichiers neufs (`ColorationSemantique.kt`, `HierarchiesEtLens.kt` + test) + 5 resynchronisés — aucune re-sync amont |
| Régression | aucune — fiches 043/045/046/047/049/052/053 et bancs P1–P9 inchangés |

## 3. Bornologie (honnêteté D3)

- Le genre d'une référence de type CLASSPATH est CLASSE en repli (le
  genre binaire exact — interface vs classe — exige la lecture des
  descripteurs hors périmètre borné : jamais une interface devinée) ;
- les ITEMS de maillons classpath de `supertypes` portent le nom simple
  SANS uri navigable (le décompilateur relève de l'hôte) ;
- `subtypes` se prouve sur les types du MODÈLE du document (le scan
  des racines alimente le même modèle — les types hors document se
  voient au scan ; l'indexation exhaustive relève de 0132 si mesuré) ;
- les plages des items hiérarchies rendues au round-trip LSP sont
  bornées (0,0) pour la navigation côté item : la PRÉPARATION porte la
  vraie plage de déclaration dans le document de départ (le protocole
  LSP ne transporte l'uri et la plage que pour l'item préparée) ;
- le delta `semanticTokens/full/delta` n'est pas annoncé (la
  coloration se calcule entière — le delta relève d'une mesure de
  latence au gabarit 07, pas d'un survendre) ;
- les requêtes `semanticTokens/range` ne sont pas annoncées (le client
  demande le document entier — convention full).
