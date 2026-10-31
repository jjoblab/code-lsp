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

## 6. Vague 5b — le moteur de KDoc (31/10, reprise R8 — fiche 007, 9 comportements)

### 6.1 Périmètre exécuté

| # | Item | État | Détail |
|---|---|---|---|
| 1 | La REPRISE R8 `documentation/DocumentationKotlin.kt` | **Fait** | Copiée depuis `codeide-lsp` @ bc11253 vers `kotlin/src/main/kotlin/jo/codeide/lsp/kotlin/documentation/` (re-contrôle R9.4 : PASSÉ). Adaptations de câblage SEULEMENT (manifeste §3 R8) : paquet neuf, déclaration = façade neuve `KtDeclaration` + jetons de commentaire REÇUS (le modèle neuf les porte à la racine — l'ancien attachait le KDoc comme jeton enfant de la déclaration, le neuf le retrouve par la règle blancs-seuls avec l'ANCRE premier jeton propre OU premier enfant — une MODIFIER_LIST d'annotations précède le mot-clé, le KDoc les précède toutes, comportement `kdoc-avant-annotations`), reconstruction par `ArbreSyntaxique.construire` avec une VUE du parseur KDoc aux trivia vides (§6.3), marche des sections fusionnant enfants et jetons propres triés par position. Le cœur est repris TEL QUEL : lex + parse KDocLexer/KDocParser vendorés, rendu texte neutre (sections libres en paragraphes, tags `@param nom — texte` / `@return texte`), tolérance D3 (vide/bloc → `null`, rien ne lève) |
| 2 | Le CÂBLAGE session + scan | **Fait** | Le survol IN-VIVO (`SurvolDocuments.rendu`) et le SCAN des sources (`ScanProjetParesseux` — la documentation des déclarations externes) passent par la reprise : le rendu KDoc est UNIFIÉ (l'ancienne chaîne 1b — retrait des marqueurs et astérisques à la main — est RETIRÉE : `kdocPrecedente`/`corpsKDoc` supprimés, non-régression prouvée par les fiches 049/045 restées vertes) |
| 3 | Tests neufs | **Fait** | 9 comportements aux noms exacts du cahier : `Fiche007DocumentationKotlinTest` — moteur R8 en chemin direct (miroir du test source), la façade `KtClasse.membres()` pour le KDoc d'un membre |

### 6.2 Un écart découvert et corrigé pendant la mise au vert

1. **Le rendu du KDoc vit de la STRUCTURE des blancs, que le modèle neuf
   EXCLUT des jetons propres** : la première rédaction reconstruisait
   l'arbre KDoc avec le `KDocParser` tel quel — ses `WHITE_SPACE` étant
   des trivia, la reconstruction les écartait des jetons propres : les
   paragraphes fusionnaient en une ligne (`La vue de rendu. Un
   paragraphe de plus.` sans retour) et les tags perdaient l'espace
   entre référence et description (`@param xle — nombre de colonnes`).
   Corrigé : la reconstruction passe par une VUE du parseur aux ensembles
   de trivia VIDES (`KDocVuePleine` — même `parse`, blancs rendus jetons
   pleins) ; le rendu retrouve la structure (comportements
   `kdoc-simple` et `kdoc-avec-tags` verts).

### 6.3 Décisions d'exécution consignées

1. **L'ancre du KDoc « porté par la déclaration » est le premier jeton
   propre OU le premier enfant, au plus tôt des deux** : `/** doc */
   @Deprecated class Vieux` — la MODIFIER_LIST (enfant) précède le
   mot-clé (jeton propre) ; ancrer sur le seul jeton propre laisserait
   les annotations entre le commentaire et l'ancre, la séparation
   blancs-seuls échouerait et la doc serait perdue (le comportement
   `kdoc-avant-annotations` l'exige).
2. **Le rendu KDoc est UNIFIÉ entre survol in-vivo et scan** : les
   déclarations externes du scan reçoivent le MÊME rendu que les
   déclarations ouvertes (sections + tags) — l'ancienne chaîne 1b
   (retrait naïf des marqueurs) ne rendait pas les tags ; la fiche 045
   (KDoc tierce au survol qualifié) et la fiche 049 (KDoc externe au
   rafraîchissement) restent vertes (leurs KDoc sont simples, rendus
   identiques).

### 6.4 Preuves du bac à sable (avant commit)

- **Fiche** : `Fiche007DocumentationKotlinTest` **9/9** (un écart de
  mise au vert corrigé — §6.2, aucune retouche du cœur repris) ;
  `Fiche049SessionLangageKotlinParseTest` **26/26** et
  `Fiche045SessionLangageKotlinK3Test` **21/21** — non-régression du
  survol (in-vivo et KDoc tierce).
- **Suite** : **456 tests VERTS** sur la toolchain 17 (447 + 9) ;
  baseline de parité RE-SIGNÉE **393 → 395 fichiers** (la reprise R8 et
  le test 007 entrent au corpus).

## 7. Vague 5c — les previews Compose (31/10, reprise R3 — fiche 050, 16 comportements)

### 7.1 Périmètre exécuté

| # | Item | État | Détail |
|---|---|---|---|
| 1 | Le modèle SPI NEUF `api/PreviewsKotlin.kt` | **Fait** | Code NEUF jjoblab écrit fiche 050 en main (l'ancien `api/` est zone NOYAU en lecture interdite R9.1) : `PreviewKotlin` (nom, paquet, plageNom, paramètres tranchés, corps, coupureBudget, ligneAnnotation), l'arbre d'appels `NoeudAppelKotlin` scellé (`AppelFonctionKotlin` / `ExpressionNonInterpreteeKotlin`), `GenreInlineKotlin` (6 genres — AUCUN, PROJET_INLINE, PROJET_CYCLE, PROJET_BUDGET, PROJET_PARAMETRES, PROJET_ILLISIBLE), `GenreArgumentAppelKotlin` (LITTÉRAL/REFERENCE/AUTRE), `ParametrePreviewKotlin` |
| 2 | L'extension du modèle neuf | **Fait** | `AppelableProjet` gagne `estFonction` et `debutNom` (remplis à la collecte) — la candidate d'inline est une FONCTION top-level ouverte à sa fonction PAR LA POSITION du nom, pas par homonymie (frontière consignée au manifeste §3 R3) |
| 3 | La REPRISE R3 `preview/PreviewsDocumentKotlin.kt` | **Fait** | Copiée depuis `codeide-lsp` @ bc11253 vers `kotlin/src/main/kotlin/jo/codeide/lsp/kotlin/preview/` (re-contrôle R9.4 : PASSÉ). Adaptations de câblage SEULEMENT (manifeste §3 R3) : paquet neuf, SPI neuf, façade `parse.kt.*` neuve, extensions d'expression de l'ancienne façade (nomAppele, genreLitteral, recepteurQualifie, selecteurQualifie — zone noyau interdite) réécrites en helpers PRIVÉS depuis le contrat de la fiche, preuve FQCN d'un nom d'annotation (l'ancienne `fqcnDuNomEcrit` vivait dans le résolveur noyau, jamais lu) réécrite depuis sa sémantique documentée : imports EXACTS (alias compris), jokers de paquets PROJET (le nom doit être un type du modèle sous le paquet importé — un joker hors projet ne prouve RIEN), unicité projet. Le cœur est repris TEL QUEL : preuve FQCN des DEUX annotations, réduction du corps en arbre d'appels (littéraux distingués, lambda final = contenu, expressions honnêtes), INLINING borné (budget 500 nœuds PARTAGÉ par document, profondeur 6, cycles CYCLE, paramétrés PARAMETRES, non-composables AUCUN, illisibles ILLISIBLE), lignes d'annotation |
| 4 | Le CÂBLAGE session | **Fait** | `previews(uri)` au contrat [SessionLangageKotlin] : le document, le MODÈLE du projet (contribution du document à uri RÉELLE + contributions du scan — `contributionsAuConsult`) et le PORT (documents ouverts d'abord — l'arbre vif prime le disque ; fichier du disque re-parsé ensuite, lecture et parse TOUJOURS hors verrou, ADR 0212) nourrissent la reprise |
| 5 | R9 `symboles/` : reprise CADUQUE | **Fait** | NON copiée : la capacité symboles de document vit déjà dans le code NEUF de la vague 2e (`NavigationDocuments.symboles()`, fiche 025 verte) — consigné au journal du manifeste §6 |
| 6 | Tests neufs | **Fait** | 16 comportements aux noms exacts du cahier : `Fiche050SessionLangageKotlinPreviewsTest` — par le SPI, moteur réel, projet multi-fichiers sur disque pour l'inline (fichiers écrits AVANT definirClasspath : la course avec le scan WARM est fermée par construction, ADR 0212) |

### 7.2 Décisions d'exécution consignées

1. **La preuve FQCN d'un nom d'annotation vit DANS la reprise, sur le
   modèle neuf** : l'ancienne vivait dans le résolveur noyau (lecture
   interdite) — sa sémantique documentée (imports exacts, jokers,
   unicité projet) est réécrite sur `ModeleProjet` : un JOKER ne prouve
   que si le nom est un TYPE DU PROJET sous le paquet importé (le joker
   `androidx.compose.runtime.*` d'un paquet hors projet ne prouve
   jamais `Composable` — comportement `import-joker-et-forme-qualifiee`
   : JokerRuntime SOUS-RAPPORTÉ, la forme qualifiée TOUJOURS prouvée).
2. **La candidate d'inline s'ouvre PAR LA POSITION du nom** (`debutNom`
   porté par `AppelableProjet`) : deux `fun Titre` dans un même fichier
   ne se confondent pas — miroir de l'ancien, la position remplace
   l'identité d'objet de l'ancien modèle.
3. **Le port sert l'arbre VIF d'abord** : un composable d'un document
   OUVERT s'inline depuis son arbre frais (jamais le disque périmé) ;
   un fichier fermé du scan se lit et se RE-PARSE à la demande, hors
   verrou — le même patron que la navigation multi-fichiers de la vague
   4a (§5.3 du journal 07).

### 7.3 Preuves du bac à sable (avant commit)

- **Fiche** : `Fiche050SessionLangageKotlinPreviewsTest` **16/16 VERTS
  à la PREMIÈRE exécution** (aucune retouche du moteur repris après sa
  rédaction — les 16 comportements, inline disque, cycle, budget et
  lignes d'annotation compris).
- **Suite** : **472 tests VERTS** sur la toolchain 17 (456 + 16) ;
  baseline de parité RE-SIGNÉE **395 → 398 fichiers** (la reprise R3,
  le SPI neuf et le test 050 entrent au corpus).

## 8. Vague 5d — les actions déclaratives ModCommand-like (31/10, ADR 0115 — 5 cas dorés)

### 8.1 Périmètre exécuté

| # | Item | État | Détail |
|---|---|---|---|
| 1 | Le modèle SPI `api/ActionsKotlin.kt` | **Fait** | Code NEUF jjoblab (ADR 0115 option 1 — miroir ModCommand, ADR 0013 Java : LE MODÈLE, PAS LE CODE) : `PlanActionKotlin` (titre, genre, éditions PAR FICHIER), `EditionFichierKotlin` (uri + éditions), `GenreActionKotlin` (RAPIDE/SOURCE — l'hôte traduit en CodeActionKind). Un plan est SÉRIALISABLE, calculé en LECTURE (re-prouvé à la requête : un plan périmé n'existe pas), prévisualisable (le client applique les éditions sur une copie) et son EXÉCUTION est déléguée au client (le moteur n'écrit jamais) |
| 2 | Le moteur `session/ActionsDeclarees.kt` | **Fait** | Trois familles, chacune RE-PROUVÉE : **IMPORT** (fiche 047 — `importsCandidats` : un plan PAR FQCN candidate, l'édition insérant la directive `import <fqcn>` après la DERNIÈRE existante, sinon après le package `\n\n`, sinon en tête) ; **MEMBRES MANQUANTS** (fiche 020 — le plan d'insertion des stubs de la déclaration visée) ; **CORRECTIFS DE TEXTE** (fiche 016 — les correctifs dont la plage COUVRE le caret). Ordre STABLE : imports, membres, correctifs en ordre du document |
| 3 | Le CÂBLAGE session | **Fait** | `actions(uri, deplacement)` au contrat [SessionLangageKotlin] : le point d'insertion d'une directive d'import se calcule sous verrou de LECTURE (pur, arbre immuable) ; les preuves passent par les entrées EXISTANTES du contrat (`importsCandidats`, `membresManquants`, `correctifsTexte`) — chacune gère SON verrou et ses consultations HORS verrou (ADR 0212 : jamais de scan sous le verrou — la première rédaction appelait les entrées SOUS le verrou de lecture, restructurée avant commit) |
| 4 | Les CAS DORÉS WorkspaceEdit | **Fait** | `CasDoresActionsKotlinTest` (5 cas, ADR 0115 : « cas dorés des WorkspaceEdit exacts au banc ») : (1) plan d'IMPORT — édition EXACTE (plage vide à la fin de la dernière directive, texte `\nkotlin.coroutines.Continuation`) ; (2) plan MEMBRES MANQUANTS — l'insertion des stubs avec la signature substituée ; (3) plan CORRECTIF au caret — accès sûr, la plage du POINT remplacée par `?.` ; (4) SANS PREUVE : liste VIDE (nom résolu par import exact, caret dans un blanc, document inconnu) ; (5) IDEMPOTENCE — deux calculs, mêmes plans (mêmes titres, mêmes éditions) |

### 8.2 Décisions d'exécution consignées

1. **Le plan d'action ne RE-PROUVE pas lui-même : il ENROBE les preuves
   existantes** (`importsCandidats` vague 2b, `membresManquants` et
   `correctifsTexte` vague 2f) — les contrats des fiches 016/020/047
   sont inchangés et restent verts (non-régression : la suite entière,
   477 tests) ; l'apport de la vague est le MODÈLE déclaratif
   (sérialisable, prévisualisable, exécution déléguée), pas de
   nouvelles preuves.
2. **L'ancrage du caret pour un correctif est la PLAGE du correctif** :
   le plan « Accès sûr » vit sur le POINT du sélecteur (la plage
   remplacée), pas sur l'identifiant du récepteur — le cas doré 3
   l'épingle ; un caret hors de toute plage prouvée ne rend RIEN (D3).
3. **`GenreActionKotlin.SOURCE` est réservé** (aucun plan ne l'emploie
   à cette vague) : l'action de source « organiser les imports »
   (retraits + insertions) exigera de TRANCHER entre candidates
   ambiguës — décision à prendre quand une fiche la demandera, pas
   avant (l'invention d'action n'est pas de ce dépôt).

### 8.3 Preuves du bac à sable (avant commit)

- **Cas dorés** : `CasDoresActionsKotlinTest` **5/5** (deux écarts de
  rédaction du CAS DORÉ corrigés — le nom du cas 1 devait être NON
  résolu au sens du moteur : `Regex` vit dans le paquet par défaut
  `kotlin.text` (KP8), remplacé par `Continuation` de
  `kotlin.coroutines` ; le caret du cas 3 devait vivre sur le POINT,
  la plage du correctif).
- **Suite** : **477 tests VERTS** sur la toolchain 17 (472 + 5) ;
  baseline de parité RE-SIGNÉE **398 → 401 fichiers** (le SPI, le
  moteur d'actions et les cas dorés entrent au corpus).

## 9. Vague 5e — la clôture de l'étage 5 (31/10)

### 9.1 Périmètre exécuté

| # | Item | État | Détail |
|---|---|---|---|
| 1 | Preuves mécaniques complètes | **Fait** | §9.3 (suite, jar, scan ART pile, dexage d8, bancs JDK 17, banc qualité, compilation croisée, provenance) |
| 2 | Journal | **Fait** | Le présent document (en-tête des cinq vagues, §5–§8, la clôture §9) |
| 3 | ALERTES | **Fait** | E-K8 actualisée (§4 : les vagues 5a-5d portent le dex à 26 040 méthodes / 4,00 Mo — 79 % / 66 %) |
| 4 | Manifeste | **Fait** | §3 (R3/R6/R7/R8 copiées avec leurs adaptations) + §6 (journal des copies, R9 caduque) |
| 5 | CI | **Fait** | `kotlin-parite` actualisé aux fiches de l'étage (014/019/007/050 + cas dorés) |

### 9.2 Ce que l'étage 5 a livré (synthèse)

Le CONFORT ÉDITEUR du moteur Kotlin est livré : le PLIAGE complet
(reprise R7 — une plage par paire d'accolades, dédupliquée, commentaires
multi-lignes), le FORMATAGE BORNÉ (reprise R6 — ré-indentation canonique
seule, intouchables préservés, refus sur arbre en erreur, idempotent),
la DOCUMENTATION KDoc (reprise R8 — KDocParser vendoré, rendu texte
neutre sections + tags, câblée au survol ET au scan), les PREVIEWS
Compose (reprise R3 — preuve FQCN par les imports, arbre d'appels,
inlining borné cycles/budgets) et les ACTIONS DÉCLARATIVES
ModCommand-like (ADR 0115 — plans sérialisables re-prouvés à la
requête, éditions par fichier, exécution déléguée au client, cas dorés
WorkspaceEdit exacts). Les quatre reprises confort prévues au manifeste
sont EFFECTUÉES (R3/R6/R7/R8 — re-contrôles R9.4 passés, adaptations de
câblage documentées) ; R9 est caduque (couvert par le code neuf 2e).

### 9.3 Preuves du bac à sable (avant poussée)

- **Suite** : **477 tests VERTS** sur la toolchain 17 (431 + 46 —
  exactement UN test par comportement des fiches 014/019/007/050, plus
  les 5 cas dorés ADR 0115) ; baseline de parité RE-SIGNÉE au fil des
  vagues **390 → 393 → 395 → 398 → 401 fichiers** (les reprises, le
  SPI neuf, les moteurs d'actions et les tests entrent au corpus ;
  `PlagesPliablesDocuments.kt` en sort).
- **Jar moteur** : 892 147 o (+70 397 vs la clôture étage 4 : les
  quatre reprises + le SPI previews/actions + les actions déclaratives).
- **Scan ART pile complète** : 6 × « AUCUNE », rc=0 (moteur 321 classes
  / 327 méthodes déclarées — borne basse ; bytecode 61:321, la chaîne
  toolchain 17 tenue ; ServiceLoader OK descripteur vérifié).
- **Dexage d8** (build-tools 34 local, min-api 26) : **26 040
  références de méthodes (79 % du plafond 32 768) / 4 196 724 o =
  4,00 Mo (66 % du budget 6 Mo)** — +217 méthodes / +50 488 o vs la
  clôture étage 4 ; seuils tenus (E-K8 : surveillance maintenue,
  ALERTES §4 actualisée).
- **Banc JDK 17** (bac à sable, 2 cœurs) : froid 237,7/47,2 ms,
  p50 5,4/22,7 ms, p95 33,0/51,7 ms, productions 5 448/31 368 et jetons
  4 210/24 274 — déterminisme IDENTIQUE (la grammaire n'est pas
  touchée), marqueurs d'erreur 0, tas 3 694 Kio.
- **Banc de qualité** (`:kotlin:benchQualite`) : **VERT** — genres 16/16
  déterministes, chaîne livrée 1.000/1.000/1.000 vs naïve 0.375 top-1 /
  0.486 MRR, p95 6,80 ms (baseline signée 7,53 ms — sous la baseline,
  tolérance ×1,5 tenue), sauts 0.
- **Compilation croisée** : `testClasses` de TOUS les modules du dépôt
  (les nouvelles entrées du SPI Kotlin — `formatage`/`formatagePlage`/
  `previews`/`actions` — ne cassent rien : aucun autre module
  n'implémente le contrat).
- **Provenance** : `verifier-provenance.sh` rc=0 (les quatre reprises
  confort sont inscrites au manifeste avec leurs chemins neufs ; les
  zones noyau anciennes restent en lecture interdite R9.1 — jamais
  consultées).

### 9.4 Clôture de la vague 5e et de l'ÉTAGE 5

**LES CRITÈRES DE SORTIE DE L'ÉTAGE 5 (§2) SONT ATTESTÉS** :

1. **Fiches confort vertes** — 41/41 comportements (fiche 014 — §5 :
   7/7 ; fiche 019 — §5 : 9/9 ; fiche 007 — §6 : 9/9 ; fiche 050 — §7 :
   16/16), les fiches 016/020 restant vertes (non-régression de
   l'enrobage déclaratif) ;
2. **Cas dorés WorkspaceEdit** (ADR 0115) — 5/5 (§8 : éditions exactes
   des trois familles, sans-preuve vide, idempotence) ;
3. **E-K8** — dex d8 sous les seuils arbitrés (26 040 / 4,00 Mo — 79 %
   méthodes / 66 % taille), ALERTES §4 actualisée.

**L'ÉTAGE 5 EST CLOS sur preuve mécanique.** Le compte du cahier :
469 comportements, dont **430 livrés** (389 aux étages 0-4 + 41 ici) —
**RESTENT 39** pour l'étage 6 et le transverse : fiches 001
(chargeur — 3), 015 (capacités L2 — 12), 018 (fiche classpath — 11),
051 (signature — 12) et 021 (mesures perf — 1). L'étage 6 (serveur LSP +
protocole riche : LSP4J, capabilities paritaires, époque, drapeau pack —
16–26 j-h [estimé]) est le suivant ; la distribution exacte de ces 39
comportements entre l'étage 6 et le transverse T sera tranchée à son
ouverture (les fiches 015/018/051 sont des capacités moteur exposées au
SPI, la 001 verrouille le descripteur de pack, la 021 relève des
bancs).

#### Le coût de l'étage 5 (miroir des relevés des étages précédents — PAS un recalage : le recalage n° 3 a clos la série)

| Grandeur | Valeur | Détail |
|---|---:|---|
| Commits de l'étage | 4 | 5a (livraison `2add68e`), 5b (`976baa6`), 5c (`b9c7734`), 5d (`b6d8337`) + la clôture 5e (le présent commit) |
| Fichiers touchés (5a-5d) | 18 | +4 482 / −368 lignes (main, tests, journaux, manifeste, digeste) |
| `kotlin/` main | 9 fichiers, +2 657 / −305 | les 4 reprises (PlisDocumentKotlin 187 l., FormateurKotlin 296 l., DocumentationKotlin 287 l., PreviewsDocumentKotlin 668 l.), le SPI (ConfortKotlin +59, PreviewsKotlin +194, ActionsKotlin +82, SessionLangageKotlin +131), la session (+172), ActionsDeclarees (+148), ModeleProjet (+18), SurvolDocuments (−45), ScanProjetParesseux (+2), PlagesPliablesDocuments supprimé (−72) |
| `kotlin/` test | 5 fichiers, +1 366 | fiches 014/019/007/050 (159 + 219 + 172 + 338 l.) + cas dorés (278 l. — 41 + 5 tests) |
| Tests du module | 431 → **477** | +41 pour 41 comportements (EXACTEMENT 1 test/comportement) + 5 cas dorés ADR 0115 |
| Tempo [mesuré-agent] | 31/10 (séance continue) | 5 vagues — les reprises confort sont une dominante DOCUMENTÉE légère (miroir étage 4 : le socle porte les moteurs SANS mécanisme neuf de fond — les trois écarts corrigés (§5.2, §6.2, §8.3) étaient des erreurs de CÂBLAGE, pas des découvertes d'architecture) |

**Comparaison à l'estimation** : la fourchette de l'étage 5 était
**20–32 j-h [estimé]** (synthèse §4). À ~97 lignes par comportement
(≈ 4 482 l. / 46 comportements + dorés — main, tests, journaux et
manifeste compris) contre ~112 pour l'étage 4 et ~135 pour l'étage 3 :
la dominante confort (reprises bornées + enrobage déclaratif) est la
MOINS COÛTEUSE des dominantes rencontrées — l'abaque de la synthèse
tenait déjà (recalage n° 3 : « aucun recalage supplémentaire n'est
requis jusqu'à la clôture du programme ») ; le présent relevé le
confirme sans le rouvrir. Restent à l'abaque : étage 6 (16–26 j-h) et T
transverse (8–12 j-h).
