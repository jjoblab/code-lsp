# 08 — Exécution étage 5 : confort éditeur

> **Décision du propriétaire reçue le 2026-10-31** dans le canal de mission :
> « Go étage 5 » — les chantiers de l'étage 5 (plan §4 de `00-synthese.md` :
> confort éditeur) sont OUVERTS sur la branche `etude/moteur-kotlin`, à la
> suite de la clôture sur preuve mécanique de l'étage 4
> (`07-execution-etage4.md` §8.4 : CI VERTE 10/10 sur le commit de clôture
> `89c2a7c` — run `41150305161`, première tentative —, 38/38 comportements
> navigation (fiches 048/053/052), rappel usages = oracle, 0 conflit manqué,
> suite 431 tests, dex d8 25 823 méthodes / 3,95 Mo, recalage n° 3 exécuté :
> l'abaque tient, aucun recalage supplémentaire requis jusqu'à la clôture du
> programme). Le présent document est le journal d'exécution de l'étage
> (méthode : miroir des rapports précédents — chiffres étiquetés
> [mesuré] / [estimé], écarts signalés).
>
> Gouvernance inchangée : branche `etude/moteur-kotlin` seule ; zones NOYAU
> anciennes (`parse/`, `resolve/`, `diagnostics/`, `completion/`, `session/`,
> `api/` de production) en lecture interdite R9.1 — les chantiers NEUFS de
> l'étage s'y écrivent cahier en main. **L'étage 5 est l'étage des REPRISES
> CONFORT** (manifeste `docs/PROVENANCE-REPRISE-KOTLIN.md` §3 : copie prévue
> « étage 5 (confort) » pour R3 `preview/`, R6 `format/`, R7 `plis/`, R8
> `documentation/`) : ces zones DE REPRISE sont en lecture AUTORISÉE pour le
> contrôle et le câblage (ADR 0100 voie C) ; chaque fichier copié est
> re-contrôlé R9.4 au moment de la copie, inscrit au manifeste AVANT
> (déjà fait, §3), et ses adaptations de câblage sont documentées au registre
> (miroir des reprises R1/R2/R4/R5 des étages 1). **R9 `symboles/` n'est PAS
> copié** : la capacité symboles de document vit déjà dans le code NEUF de la
> vague 2e (`NavigationDocuments.symboles()`, fiche 025 verte) — le registre
> le consigne (§6 du manifeste, reprise caduque).
>
> Les tests sources anciens restent consultables (R9.1/R9.5) pour les
> FIXTURES, jamais copiés.

## 1. Périmètre de l'étage (plan §4 — cahier en main)

| # | Item | Source du contrat |
|---|---|---|
| 1 | **Pliage complet (S2)** : une plage par paire d'accolades (de la PREMIÈRE ouvrante à la DERNIÈRE fermante — jamais du début du nœud), le bloc d'imports ENTIER (premier → dernier), commentaires multi-lignes (KDoc ET bloc), lambda et `when` par accolades, déduplication BODY/BLOCK (une seule plage par paire), structures de contrôle sans doublon, édition en cours (sans paire : RIEN), document vide/mono-ligne (rendu honnête, filtre hôte) | fiche 014 (7 comportements) |
| 2 | **Formatage borné (L2, ADR 0202 miroir)** : ré-indentation canonique SEULE (le contenu des lignes n'est jamais réécrit), profondeur structurelle par jetons (accolades ouvertes, fermantes en tête, continuation de liste un niveau sous l'ouvrante, fermeture à la base), intouchables (chaîne brute entre délimiteurs, lignes internes KDoc/bloc, continuation d'expression libre, ligne vide), refus sur arbre en erreur (liste VIDE), idempotence, plage (rangeFormatting : seules les lignes qui intersectent bougent), taille d'indentation à la demande (2/4 espaces), document inconnu/fermé VIDE | fiche 019 (9 comportements) |
| 3 | **Documentation KDoc (S2)** : extraction du DOC_COMMENT porté par la déclaration, rendu en TEXTE NEUTRE par le KDocParser vendoré (sections libres en paragraphes, tags en lignes `@param nom — texte` / `@return texte`), tolérance D3 (KDoc vide/bloc non-KDoc → `null`, rien ne lève) | fiche 007 (9 comportements) |
| 4 | **Previews Compose (C1, PREVIEW.md §4.4)** : preuve FQCN des annotations PAR LES IMPORTS (exactes, jokers projet, formes qualifiées), paramètres `@Preview` tranchés dans la source, réduction du corps en ARBRE D'APPELS neutre (littéraux distingués, lambda final = contenu, expressions honnêtes), INLINING des composables du projet (cycles CYCLE, budgets BUDGET 500 nœuds/profondeur 6, paramétrés PARAMETRES, non-composables AUCUN), lignes d'annotation, ordre du document, fermeture de session | fiche 050 (16 comportements) |
| 5 | **Actions ModCommand-like (ADR 0115 option 1)** : les quick-fixes (import par FQN, membres manquants, correctifs de texte) migrent vers un modèle DÉCLARATIF séRIALISABLE — plan calculé en lecture (re-preuve à la requête), éditions par fichier neutres (l'équivalent WorkspaceEdit), exécution déléguée au client | ADR 0115 ; ADR 0013 Java (ModCommand — le modèle, pas le code) |
| 6 | **Cas dorés WorkspaceEdit** : les plans d'action assertés par CAS DORÉS exacts (titre, genre, éditions par fichier, plages et textes EXACTS) au banc | ADR 0115 §Tests (« cas dorés des WorkspaceEdit exacts ») |

Périmètre DÉJÀ VERT livré aux étages précédents (non refait ici) :
**membres manquants** (fiche 020, vague 2f), **correctifs de texte**
(fiche 016, vague 2f), **imports candidats** (fiche 047/`importsCandidats`,
vagues 2b/3) — l'étage 5 les ENROBE dans le modèle déclaratif (item 5)
sans retraiter leurs contrats. Les fiches hover-inlay restantes (007 exceptée
— item 3 ci-dessus : la fiche documentation est la gate de la reprise R8),
soient 015 (capacités L2), 018 (fiche classpath), 051 (signature), 001
(chargeur) et 021 (mesures perf), relèvent de l'étage 6 (serveur LSP +
protocole riche) et du transverse T — la distribution est consignée §9.

## 2. Critères de sortie de l'étage 5 (synthèse §4 + ADR 0115/0116 — suivi)

| Critère | État | Preuve |
|---|---|---|
| Fiches confort vertes (014 pliage + 019 formatage + 007 documentation + 050 previews — 41 comportements) | à suivre (§5, §6, §7) | `Fiche014PlisDocumentKotlinTest` : 7 ; `Fiche019FormateurKotlinTest` : 9 ; `Fiche007DocumentationKotlinTest` : 9 ; `Fiche050SessionLangageKotlinPreviewsTest` : 16 |
| Cas dorés WorkspaceEdit (ADR 0115 : plans d'action assertés par éditions EXACTES) | à suivre (§8) | `CasDoresActionsKotlinTest` |
| Fiche 016/020 restent vertes (non-régression de l'enrobage déclaratif) | à suivre (§8) | fiches 016/020 inchangées |
| E-K8 : dex d8 pile chaude sous seuils (≤ 32 768 méthodes / ≤ 6 Mo) | à suivre (§9.3) | scan + dexage au bac à sable, ALERTES §4 actualisée |

## 3. Plan de vagues (cahier en main — l'ordre suit les dépendances : les deux moteurs BORNÉS d'abord (aucune dépendance entre eux), la documentation ensuite (elle câble le survol), la preview enfin (elle consomme le modèle projet), les actions en dernier (elles enrobent les correctifs existants))

| Vague | Périmètre | Fiches | Comportements |
|---|---|---|---:|
| **5a** | REPRISES R7 + R6 : le moteur de pliage complet (accolades, déduplication, commentaires — remplace le calcul conservateur 1b, fiche 049 couverte par sur-ensemble) et le formateur borné (ré-indentation canonique, intouchables, refus sur erreur) ; SPI `formatage`/`formatagePlage` + `EditionTexteKotlin` + genre `BLOC` | 014 + 019 | 16 |
| **5b** | REPRISE R8 : le moteur de KDoc (KDocLexer/KDocParser vendorés, rendu texte neutre sections + tags), câblé AU SURVOL de session (la documentation in-vivo passe par lui) | 007 | 9 |
| **5c** | REPRISE R3 : le moteur de previews Compose (preuve FQCN par imports, arbre d'appels, inlining borné) + SPI `previews` et son modèle neutre (code NEUF — l'ancien `api/` est zone noyau jamais lue) + `AppelableProjet.debutNom` (extension du modèle neuf, frontière consignée au manifeste) | 050 | 16 |
| **5d** | Actions ModCommand-like : `PlanActionKotlin` déclaratif sérialisable (titre + genre + éditions par fichier), entrée `actions(uri, deplacement)` — plans d'import (par FQN), membres manquants, correctifs au caret ; CAS DORÉS WorkspaceEdit | — (ADR 0115) | 5 dorés |
| **5e** | Clôture : preuves mécaniques complètes (suite, jar, scan ART pile, dexage d8, bancs JDK 17, banc qualité, compilation croisée, provenance), journal, ALERTES (E-K8), manifeste (§3/§6) | — | — |

**Total : 41 comportements cahier + 5 cas dorés** (agrégat du cahier —
INVENTAIRE.md, domaines folding/formatage/hover-inlay-documentation/autre).
Chaque vague : code + tests NEUFS au nom exact du cahier + suite verte +
section du présent journal + commit ; le push et l'attestation CI portent la
clôture (miroir étage 4 : le run de TÊTE éprouve livraisons et clôture
ensemble).

## 4. Actions propriétaire ouvertes (reprises de l'étage 4 — §4 du journal précédent)

| # | Action | Référence | Bloque |
|---|---|---|---|
| 1 | Audit de similarité GPL (P-K1) — les copies R1–R8 sont effectuées, l'audit recommandé (rôle distinct des implémenteurs) s'exerce a posteriori | manifeste §3/§6, ALERTES §2 | plus rien de dur — le propriétaire en reste juge |
| 2 | Arbitrages Q-K1 (CIDX vs index dédié) / Q-K2 (décodage @Metadata maison) — PARTIELLEMENT ARBITRÉ ; ouverts pour la structure | DEMANDES-INTERFACE, ADR 0108, ALERTES §4 | le câblage classpath final |
| 3 | Conditions binaires intellij-dependencies (P-K2) | ADR 0120, NOTICE | la PUBLICATION du pack langue |
| 4 | Lecture du second manifeste | synthèse §6.4 | rien |
| 5 | Passe appareil couvrant les étages 1-4 (kit enrichi) — recommandation consignée, OPTIONNELLE | `04-execution-etage1.md` §16 | rien (les latences S0 restent tenues en JVM) |
| 6 | Recommandation E-K6 (test flottant Java `IntegrationMoteurIndexTest` : tolérance de tempo) — 2 occurrences CI consignées, le flottement demeure | ALERTES §4 | rien (périmètre Java) |

## 5. Vague 5a — les moteurs bornés : pliage complet + formateur (31/10, reprises R7/R6 — fiches 014/019, 16 comportements)

### 5.1 Périmètre exécuté

| # | Item | État | Détail |
|---|---|---|---|
| 1 | La REPRISE R7 `plis/PlisDocumentKotlin.kt` | **Fait** | Copiée depuis `codeide-lsp` @ bc11253 vers `kotlin/src/main/kotlin/jo/codeide/lsp/kotlin/plis/` (re-contrôle R9.4 : en-têtes/KDoc relus — AUCUNE mention tierce, AUCUN écart depuis le contrôle initial). Adaptations de câblage SEULEMENT (manifeste §3 R7) : paquet neuf, modèle `parse.NoeudArbre` (les commentaires vivent à la RACINE de l'arbre neuf — la marche les y lit), genre neutre `api.GenrePlagePliable` porté par chaque plage (BLOC de fonction → FONCTION, structures de contrôle/lambda/when/commentaire de bloc → BLOC — le SPI neuf distingue les genres, l'ancien n'en portait pas), rendu trié par début de plage. Le cœur est repris TEL QUEL : règle d'accolades (première ouvrante → dernière fermante, JAMAIS du début du nœud), déduplication par plage (BODY et son BLOCK = un pli), bloc d'imports ENTIER (premier → dernier), commentaires multi-lignes (KDoc ET bloc), tolérance D3 (sans paire : RIEN) |
| 2 | La RETRAITE du calcul neuf 1b | **Fait** | `session/PlagesPliablesDocuments.kt` SUPPRIMÉ : son périmètre (fiche 049 : imports/KDoc/CLASSE/BLOCK-de-fonction, plages aux bornes de nœuds) est un SOUS-ENSEMBLE EXACT de la reprise R7 — non-régression prouvée par la fiche 049 restée VERTE (26/26, §5.4) |
| 3 | La REPRISE R6 `format/FormateurKotlin.kt` | **Fait** | Copiée depuis `codeide-lsp` @ bc11253 vers `kotlin/src/main/kotlin/jo/codeide/lsp/kotlin/format/` (re-contrôle R9.4 : PASSÉ). Adaptations de câblage SEULEMENT (manifeste §3 R6) : paquet neuf, arbre paramètre = façade neuve `parse.kt.KtFichier` (le texte s'y lit sur `source`), REFUS sur arbre en erreur par la marche `messageErreur` du modèle neuf (l'ancien arbre portait un compteur), suite de jetons BLANCS COMPRIS re-lexifiée depuis le texte par `performLexing` + `KotlinLexer` (le modèle neuf ne retient que les jetons SIGNIFICATIFS — le curseur d'analyse exige les blancs fusionnés « \n    » du lexeur, positions identiques), édition rendue en SPI neuf `api.EditionTexteKotlin` (plage + texte). Le cœur est repris TEL QUEL : profondeur structurelle (accolades ouvertes − fermantes en tête + continuation de liste), intouchables (chaîne brute entre délimiteurs, lignes internes KDoc/bloc, continuation d'expression libre, ligne vide), idempotence (jamais d'édition à l'identique), refus honnête sur erreur |
| 4 | Le SPI | **Fait** | `api/ConfortKotlin.kt` : `GenrePlagePliable.BLOC` (corps à accolades sans genre dédié — l'hôte n'a pas de FoldingRangeKind pour eux) et `EditionTexteKotlin` (plage + texte, l'équivalent neutre d'un TextEdit) ; le contrat [SessionLangageKotlin] gagne `formatage(uri, tailleIndentation)` et `formatagePlage(uri, debutPlage, finPlage, tailleIndentation)` — document en erreur → liste VIDE, document inconnu/fermé → liste VIDE, jamais d'exception |
| 5 | Le CÂBLAGE session | **Fait** | `plagesPliables` délègue à la reprise R7 (sous verrou de lecture — calcul pur) ; `formatage`/`formatagePlage` délèguent à la reprise R6 (sous verrou de lecture — le moteur ne touche ni au disque ni à l'état) |
| 6 | Tests neufs | **Fait** | 16 comportements aux noms exacts du cahier : `Fiche014PlisDocumentKotlinTest` (7 — moteur R7 en chemin direct, miroir du test source) et `Fiche019FormateurKotlinTest` (9 — par le SPI, chemin de production D6, moteur réel) |

### 5.2 Un écart découvert et corrigé pendant la mise au vert (le cahier comme sonde)

1. **La fusion des jetons descendants doit se TRIER par position** : la
   première rédaction de `jetonsDescendants` (adaptation de câblage)
   concaténait les jetons PROPRES du nœud PUIS ceux de ses enfants — or
   les jetons propres d'un nœud vivent AUTOUR de ceux de ses enfants
   (l'accolade ouvrante avant, la fermante APRÈS) : la « dernière
   fermante » du corps de classe devenait celle d'un FILS (le pli de
   `class Rendu` finissait au `}` de `fun vide()` — 79 au lieu de 81,
   comportement `corps-de-classe-et-de-fonctions`). Corrigé : la fusion
   se trie par offset — miroir de la suite CONTIGUÈ de l'ancien modèle,
   où les jetons se suivaient dans l'ordre du document.

### 5.3 Décisions d'exécution consignées

1. **Le genre `BLOC` complète le vocabulaire neutre du pliage** : le
   SPI 1b distingue IMPORTS/KDOC/CLASSE/FONCTION ; la fiche 014 plie
   aussi les branches de contrôle, les lambda, les `when` et les
   commentaires de bloc — un genre BLOC neutre les porte (l'hôte n'a
   pas de `FoldingRangeKind` dédié, il filtre par genre comme il
   filtrait les mono-lignes). La distinction FONCTION (BLOC fils
   direct d'un FUN) est conservée : la fiche 049 compte « une plage par
   corps de bloc de fonction » par GENRE.
2. **Le mono-ligne reste ÉMIS par le moteur** (`fun f() {}` rend une
   plage) : le filtre des plis mono-lignes appartient à l'HÔTE, qui
   seul connaît les lignes (miroir du comportement source, fiche 014 —
   `document-vide-ou-mono-ligne`).
3. **Le formateur re-lexifie le texte** (blancs compris) au lieu de
   lire l'arbre : le modèle neuf ne retient que les jetons
   significatifs, et le CURSEUR d'analyse du formateur vit des blancs
   fusionnés « \n    » du lexeur (l'INVARIANT « un blanc qui chevauche
   le début de ligne est la fusion retour+indentation » en dépend).
   Le lexage est déterministe et sans état — même lexer, mêmes
   positions que le parse (la preuve : les 9 comportements verts,
   chaîne brute et KDoc compris).

### 5.4 Preuves du bac à sable (avant commit)

- **Fiches** : `Fiche014PlisDocumentKotlinTest` **7/7** (un écart de
  mise au vert corrigé — §5.2) ; `Fiche019FormateurKotlinTest` **9/9 à
  la PREMIÈRE exécution** (aucune retouche du moteur repris après sa
  rédaction) ; `Fiche049SessionLangageKotlinParseTest` **26/26** — la
  non-régression du pliage session (le retrait du calcul 1b est couvert
  par sur-ensemble).
- **Suite** : **447 tests VERTS** sur la toolchain 17 (431 + 16 —
  exactement UN test par comportement des fiches 014/019) ; baseline de
  parité RE-SIGNÉE **390 → 393 fichiers** (les reprises R6/R7 et les
  deux tests entrent au corpus, `PlagesPliablesDocuments.kt` en sort —
  protocole `-Dparite.majBase=true --rerun-tasks`, le fichier a changé,
  vérifié).
- Les preuves mécaniques COMPLÈTES (jar, scan ART pile, dexage d8,
  bancs JDK 17, banc qualité, compilation croisée, provenance) sont
  collectées à la CLÔTURE de l'étage (§9.3) — le push unique porte
  livraisons et clôture ensemble (miroir étage 4 : le run de TÊTE
  éprouve l'ensemble).
