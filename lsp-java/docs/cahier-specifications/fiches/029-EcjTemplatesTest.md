# Fiche 029 — EcjTemplatesTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/ecj/EcjTemplatesTest.java` (la classe interne se nomme `JdtTemplatesTest` — portage de Live/PostfixTemplates.kt du projet tiers de référence v3.20).
> **Domaine** : templates
> **Résumé** : Tests des templates de complétion v1.3.0 : live templates (`sout`, `soutv`, `fori`, `try`…), templates postfixes (`expr.var`, `.sout`, `.opt`, `.if`, `.cast`…) et leur intégration dans le pipeline de complétion. Trois niveaux : unitaire (tiering de pertinence, scan du récepteur, prédicats sans binding = conscience de type), intégration moteur (item SNIPPET avec éditions additionnelles en offsets de la source originale), et API (isSnippet, insertText, relevance). Le moteur d'intégration tourne avec classpath VIDE (jrt seul) — les récepteurs `String` résolvent via l'image modules du JDK de test, aucun android.jar requis. Sémantique clé : les live templates sortent en position d'INSTRUCTION et jamais en position d'expression ; les postfixes respectent le type résolu du récepteur.

## Comportements

### liveTemplatesVidesAuCaretNu — caret nu : aucune live template
- **Préconditions / entrée** : `EcjLiveTemplates.itemsFor("")` et `itemsFor(null)`.
- **Comportement attendu** : liste vide dans les deux cas (pas d'inondation du popup à préfixe vide/null).
- **Bornes** : 0 item.
- **Statut** : actif

### liveTemplatesTiering — tiering de pertinence exact/partiel
- **Préconditions / entrée** : préfixe « sout ».
- **Comportement attendu** : 2 items : « sout » (clé exacte, relevance 2500 — en tête) et « soutv » (clé partielle, relevance 100 — discrète) ; le snippet de « sout » est « System.out.println($0); ».
- **Bornes** : 2 items ; relevance exacte = 2500 ; relevance partielle = 100.
- **Statut** : actif

### liveTemplatesTabStopsLies — tab stops liés et placeholders
- **Préconditions / entrée** : snippets des templates « fori » et « try ».
- **Comportement attendu** : « fori » contient « ${1:i} » (tab stop lié nommé) ; « try » contient « catch (${1:Exception} » (placeholder dans le catch).
- **Bornes** : —
- **Statut** : actif

### postfixScanDuRecepteur — scan du récepteur avant la clé
- **Préconditions / entrée** : textes « list.sout », « foo().var », « arr[i].for », « a.b().c.sout » (chaîne qualifiée), « "texte".len ».
- **Comportement attendu** : le récepteur scanné est l'expression entière AVANT le point porteur de la clé : « list », « foo() », « arr[i] », « a.b().c » (avant le DERNIER point), « "texte" ».
- **Bornes** : —
- **Statut** : actif

### postfixSansBindingConscienceDeType — postfixes sans binding : conscience de type
- **Préconditions / entrée** : `itemsFor("value.sout", …, "sout", null, false, 0)` et variantes « var », « opt », « cast », et « sout » sans point.
- **Comportement attendu** : « value.sout » → exactement 1 item, suppression [0, 6) (du récepteur jusqu'avant la clé), snippet « System.out.println(value);$0 » ; « value.var » → 1 item dont le snippet contient « ${1: » et « = value; » ; « opt » SANS type résolu → NON proposée (prédicat isReference) ; « cast » (ANY_VALUE) → proposée ; sans point → aucune postfix.
- **Bornes** : deleteStart = 0 ; deleteEnd = 6 ; 1 item par cas applicable.
- **Statut** : actif

### positionInstruction — garde textuelle position instruction/expression
- **Préconditions / entrée** : `isStatementPosition` sur : « void f() {\n    sout\n} » (tête de ligne), « a(); sout » (après ;), « if (x) { sout » (après {), « sout » (début de fichier), « x = sout » (après =), « foo(sout » (dans des arguments), « x.sout » (après un point).
- **Comportement attendu** : true pour les quatre premiers (position d'instruction) ; false pour les trois derniers (expression / arguments / accès membre).
- **Bornes** : —
- **Statut** : actif

### ancreImport — ancre d'insertion d'import
- **Préconditions / entrée** : `importAnchorOffset` sur trois sources : avec import, sans import, sans package.
- **Comportement attendu** : 35 (juste après le DERNIER import — « package a.b;\n » = 13 + « import java.util.Map;\n » = 22), 13 (juste après le package), 0 (début de fichier).
- **Bornes** : 35 / 13 / 0.
- **Statut** : actif

### liveTemplateEnPositionInstruction — live template servie en position d'instruction
- **Préconditions / entrée** : « sout » tapé en tête d'instruction dans un corps de méthode ; complétion à la fin du mot.
- **Comportement attendu** : item « sout » de kind SNIPPET avec isSnippet() vrai, insertText « System.out.println($0); », relevance 2500, ET en TÊTE de la liste de complétion.
- **Bornes** : relevance 2500 ; premier item de la liste.
- **Statut** : actif

### liveTemplateAbsenteEnPositionExpression — aucune template en position d'expression
- **Préconditions / entrée** : « int x = sout » (après « = »).
- **Comportement attendu** : aucune template d'instruction émise (la complétion ordinaire peut même être null — l'important est l'absence de snippet « sout »).
- **Bornes** : —
- **Statut** : actif

### postfixVarSurRecepteurResolu — `.var` conscient du type résolu
- **Préconditions / entrée** : « String name = "x"; » puis « name.var » ; complétion en fin de clé.
- **Comportement attendu** : item « var » SNIPPET avec insertText « String ${1:string} = name;$0 » (type du binding, nom dérivé du type, première lettre minuscule) ; 1 édition additionnelle : suppression du récepteur « name. » en offsets de la source ORIGINALE (texte de remplacement vide) ; l'édition ne chevauche jamais la plage principale.
- **Bornes** : 1 édition additionnelle ; suppression = [début de « name.var », début + 5).
- **Statut** : actif

### postfixOptImporteOptionalToutSeul — `.opt` importe Optional
- **Préconditions / entrée** : « String name = "x"; » puis « name.opt ».
- **Comportement attendu** : item « opt » avec insertText « Optional.ofNullable(name)$0 » ; 2 éditions additionnelles : suppression du récepteur + insertion de « import java.util.Optional;\n » à une insertion zéro-largeur juste après la ligne package.
- **Bornes** : 2 éditions additionnelles.
- **Statut** : actif

### postfixBoolFiltreesParType — postfixes booléennes filtrées par type
- **Préconditions / entrée** : « boolean ok = true; » puis « ok.if » ; puis la même source avec « String ok = "x"; ».
- **Comportement attendu** : sur booléen : item « if » avec insertText « if (ok) {\n    $0\n} » ; sur String : AUCUNE template postfixe proposée (prédicat isBoolean — conscience de type).
- **Bornes** : —
- **Statut** : actif

### lesTemplatesNePolluentPasLesContextesNonPrevus — anti-pollution des autres contextes
- **Préconditions / entrée** : caret nu (préfixe vide) en position d'instruction ; puis complétion dans « import java.util. » (contexte IMPORT_REFERENCE).
- **Comportement attendu** : AUCUNE template (aucun item isSnippet) dans les deux contextes.
- **Bornes** : —
- **Statut** : actif

## Notes pour la réécriture
- Hiérarchie de pertinence à respecter : clé exacte (2500) domine la liste, clé partielle (100) reste discrète — les templates ne doivent pas écraser les items ordinaires sauf correspondance exacte.
- Les éditions additionnelles (suppression du récepteur, insertion d'import) s'expriment en offsets de la source ORIGINALE et ne doivent JAMAIS chevaucher la plage principale de l'item (contrainte LSP).
- Garde conservatrice « position d'instruction » : après « = », dans des arguments, après un point → pas de live template d'instruction.
- Conscience de type des postfixes : `opt` exige une référence résolue, `if` un booléen — une template ne sort jamais là où elle ne compilerait pas ; sans binding, seules les postfixes ANY_VALUE (sout, var, cast) sortent.
- Ancre d'import : après le DERNIER import, sinon après le package, sinon 0 — réutilisable par quick-fix et postfixes importeuses.
- Anomalie de nommage : fichier `EcjTemplatesTest.java`, classe `JdtTemplatesTest`.
