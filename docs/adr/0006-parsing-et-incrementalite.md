# ADR 0006 — Parsing et incrémentalité

- **Statut** : Proposé
- **Date** : 2026-10-28

## Contexte

Le moteur actuel re-diagnostique le corps modifié à chaque frappe (`EcjFastDiagnose`) mais **re-parse et re-résout l'unité complète** ( re-résolution par frappe — point faible W- connu, `LSP_JAVA.md` ) ; l'incrémentalité utile ( §5-D3 : « quel niveau d'incrémentalité est réellement atteignable et utile ? » ) se joue sur trois mécanismes IntelliJ :

1. **Parse paresseux des corps** : chameleon `LazyParseableElement` + `marker.collapse(CODE_BLOCK)` ( `java/java-syntax/src/com/intellij/java/syntax/parser/StatementParser.kt:44` ) — les corps ne sont parsés qu'à la demande.
2. **Reparse incrémental** : `BlockSupportImpl` — plage commune préfixe/suffixe ( `ChangedPsiRangeUtil` ), remontée au plus profond nœud `IReparseableElementType`, **validation par équilibre d'accolades** ( `getErrorsCount` ), reparse du chameleon, diff/graft (`DiffLog`), repli full-parse.
3. **Commit Document→PSI incrémental** ( `DocumentCommitProcessor` ) — trou fonctionnel en headless (mock no-op, D2), couvert côté statu quo par le re-diagnostic de corps.

**`java-syntax` est autonome et publié** (D3) : module KMP sur `platform/syntax`, 0 `PsiBuilder`/`Project`/awt ; mesuré au spike V1 : parse p50 0,65 ms / p95 3,5 ms (498 l.), 82 Ko/arbre, **0 référence sensible ART**, 798 Ko.

## Sources consultées

- `JetBrains/intellij-community` @ `865067b0` (lecture seule, Apache-2.0) : `java/java-syntax` (parser Kotlin, `SyntaxBuilderUtil.kt:92-123`, `JavaElementType.java:337`), `platform/core-impl` `psi/impl/BlockSupportImpl.java`, `LazyParseableElement` ( `:274-323`, `:39-40` VarHandle ), `ChangedPsiRangeUtil` [hors sparse].
- Artefact : `org.jetbrains:java-syntax 0.3.381` — mesuré `spike/resultats/v1-*.txt`.
- `jjoblab/codeide-lsp` @ `cb809e14` : `EcjFastDiagnose`, `EcjParseCache` (LRU 8), `docs/LSP_JAVA.md` §4.

## Options

**Option 1 — Réutiliser `java-syntax` comme moteur de reparse incrémental + validation, derrière un drapeau.** Le moteur ecj reste souverain (unité compilée) ; java-syntax fournit : (a) l'arbre léger tolérant aux erreurs pour les réponses « syntaxe pure » ( plages de re-parse, équilibre d'accolades avant de payer une re-résolution ecj, pliages ) ; (b) à terme le stub building côté source ( D4 ). Pont : traduction événement → validation accolades → décision « re-diagnostic de corps » vs « re-résolution complète ».

**Option 2 — Réécrire un parseur maison incrémental.** Coût élevé, zéro valeur ajoutée vs java-syntax publié ( même grammaire, même tolérance ) ; rejeté.

**Option 3 — Portage du pipeline `BlockSupportImpl`/`PsiBuilderImpl`.** Couplé Project/PsiFile/Document/DummyHolder/événements PSI ( D3 ) ; VarHandle dans les nœuds ( minSdk 33 ) ; rejeté — on n'embarque pas l'AST IntelliJ.

**Option 4 — Ne rien changer** : re-parse complet ecj par frappe. C'est le comportement actuel ( p50 de frappe 30/64 ms à 20 jars, bureau ) — reste le repli derrière drapeau.

## Critères et mesures

- Spike V1 ( §3 ) : p50 0,65 ms / p95 3,5 ms ( 498 l. ) ; arbre 82 Ko ; 0 motif ART.
- D3 : mécanique chameleon/collapse + validation accolades décrites avec `fichier:ligne`.
- Critère d'utilité : ne payer une re-résolution ecj que si la modification sort du corps ( signature/imports ), sinon re-diagnostic de corps seul — comportement déjà partiellement implémenté ( `EcjFastDiagnose` ) à systématiser.

## Décision proposée

**Option 1** : dépendance `java-syntax` ( déjà validée packaging ADR 0005 ), drapeau « incrémentalité parsing » ; vague dédiée ( après classement/CodeAction, cf. `00-synthese.md` ) qui branche : validation d'accolades pré-ecj, calcul de plage commune préfixe/suffixe ( algorithme décrit D3, réécrit en Kotlin ), decision gate re-parse. Le parseur ecj reste source de vérité compilable.

## Conséquences

- Nouvelle dépendance Apache-2.0 de 798 Ko ( budget §7 du spike respecté ).
- Deux parseurs coexistent ( ecj souverain + java-syntax léger ) : le contrat entre eux doit être spécifié ( quelles questions syntaxiques java-syntax a le droit de répondre seul ).
- La grammaire java-syntax suit les niveaux de langage JetBrains ( `LanguageLevel.HIGHEST` ) : aligner avec le niveau ecj du projet.

## Plan de migration progressive

1. Vague ( ordre dans `00-synthese.md` ) : (a) validation d'accolades seule ( peu de risque ) ; (b) plage commune → garde de re-résolution ; (c) éventuel arbre léger pour pliage/navigation syntaxique.
2. Drapeau à chaque étape ; retour arrière = re-parse complet ( option 4 ) sans changement d'API.

## Tests de non-régression et critères de sortie

- Corpus de frappe ( scénarios déterministes d'édition : insertion/suppression dans corps, signature, imports ) : latence p95 sans régression vs statu quo ; taux de re-résolutions évitées mesuré ( > 50 % des frappes dans les corps ).
- Fidélité : les diagnostics produits après garde incrémentale sont identiques ( corpus ADR 0016 ) à ceux du re-parse complet.

## Risques

- **R1 — Divergence de grammaire java-syntax vs ecj** ( niveaux de langage ) : mitigation = tests croisés sur corpus de sources réelles, épinnage de version.
- **R2 — Double coût mémoire des deux parseurs** : l'arbre java-syntax est jetable ( 82 Ko, non retenu ) ; mesuré au tas repos 10 Mo.
- **R3 — Complexité de la frontière** : la gate de décision doit rester un module pur, testable unitairement ( pas d'État diffus ).
