# Fiche 028 — EcjSyntaxInstantTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/ecj/EcjSyntaxInstantTest.java`.
> **Domaine** : diagnostics (fiche mixte : repli diagnostics de syntaxe + complétion instantanée)
> **Résumé** : Tests du lot h4 « LSP instantané » (v0.101.0) — le régime de repli quand la plateforme manque (classpath vide AVANT la première sync, jdkHome sans `lib/modules` : JDK du terminal non installé). Deux garanties fondamentales : ① une source syntaxiquement cassée rend SES erreurs de syntaxe (le silence d'avant h4 était le défaut) ; ② une source saine mais pleine de types non résolus rend une liste VIDE — le repli ne fabrique JAMAIS de « cannot be resolved ». La complétion instantanée propose champs/locales/paramètres du fichier + mots-clés du langage, sans jamais deviner de types du JDK. Marqueur de caret : la séquence `/*CAR*/` dans la source témoin, remplacée par le préfixe inséré, caret juste après lui. Le repli est aussi vérifié au niveau service (`DiagnosticService`).

## Comportements

### preconditionLeMoteurSansPlateformeEstInutilisable — pré-condition du régime
- **Préconditions / entrée** : moteur construit avec classpath vide et jdkHome = répertoire temporaire SANS `lib/modules` (image jrt neutralisée), conformité « 17 ».
- **Comportement attendu** : `isLikelyUsable()` retourne false — le régime plein est fermé, c'est le mode instantané.
- **Bornes** : —
- **Statut** : actif

### laSyntaxeCasseeRendSesErreursSansPlateforme — garantie ① : la syntaxe cassée parle
- **Préconditions / entrée** : `diagnoseSyntax` sur une source avec point-virgule manquant (« int x = 42 ») et parenthèse non fermée (« System.out.println(x; »), sans plateforme.
- **Comportement attendu** : au moins un diagnostic de sévérité ERROR ; chaque diagnostic porte une plage valide (startOffset ≥ 0, endOffset > startOffset).
- **Bornes** : ≥ 1 ERROR.
- **Statut** : actif

### laSourceSaineNonResolueResteSilencieuse — garantie ② : zéro faux positif
- **Préconditions / entrée** : source syntaxiquement saine utilisant `String`/`System` (non résolus sans plateforme).
- **Comportement attendu** : liste de diagnostics VIDE — aucun « cannot be resolved » fabriqué par le repli.
- **Bornes** : 0 diagnostic.
- **Statut** : actif

### diagnoseSyntaxTriParOffset — tri par offset croissant
- **Préconditions / entrée** : `diagnoseSyntax` sur la source cassée (2 fautes).
- **Comportement attendu** : les diagnostics sont triés par startOffset croissant.
- **Bornes** : —
- **Statut** : actif

### leServiceRendLaSyntaxeQuandLaPlateformeManque — le repli est branché dans le service
- **Préconditions / entrée** : `DiagnosticService` sur un `CompilationEnvironment` dont jdkHome pointe vers le dossier temporaire (jrt neutralisé) ; diagnose de la source cassée.
- **Comportement attendu** : le service sert la syntaxe en repli (≥ 1 ERROR).
- **Bornes** : ≥ 1 ERROR.
- **Statut** : actif

### leServiceResteSilencieuxSurDuSainNonResolu — service : silence sur sain non résolu
- **Préconditions / entrée** : même service en repli ; source saine non résolue.
- **Comportement attendu** : liste VIDE (le repli du service ne fabrique pas de « cannot be resolved »).
- **Bornes** : 0 diagnostic.
- **Statut** : actif

### laCompletionInstantaneeProposeLeChampFiltreParPrefixe — champ du fichier, filtré
- **Préconditions / entrée** : complétion instantanée, préfixe « com » (champ `compteurTotal`, méthodes `premiere`/`seconde`).
- **Comportement attendu** : item « compteurTotal » de kind FIELD proposé ; « premiere » NON proposé (le préfixe filtre).
- **Bornes** : —
- **Statut** : actif

### laCompletionInstantaneeProposeLesLocalesDeLaMethodeEnglobante — portée = méthode englobante
- **Préconditions / entrée** : préfixe « localeS » dans `seconde()` (locales `localePremiere` dans `premiere()`, `localeSeconde` dans `seconde()`).
- **Comportement attendu** : « localeSeconde » (VARIABLE) proposé ; « localePremiere » de l'AUTRE méthode NON proposé.
- **Bornes** : —
- **Statut** : actif

### laCompletionInstantaneeProposeLesMotsClesDuLangage — mots-clés en repli
- **Préconditions / entrée** : préfixe « wh ».
- **Comportement attendu** : « while » de kind KEYWORD proposé.
- **Bornes** : —
- **Statut** : actif

### laCompletionInstantaneeNeDevineAucunTypeDuJdk — zéro devinette de type JDK
- **Préconditions / entrée** : préfixe « Str ».
- **Comportement attendu** : NI « String » NI « StringBuilder » proposés — le repli ne propose aucun type du JDK (le régime plein les apportera).
- **Bornes** : —
- **Statut** : actif

### laCompletionInstantaneeProposeTypesEtMethodesDuFichier — à préfixe vide : tout le fichier
- **Préconditions / entrée** : préfixe vide (caret à blanc dans `seconde(String parametre)`).
- **Comportement attendu** : items « Main » (CLASS), « seconde » (METHOD), « premiere » (METHOD), « compteurTotal » (FIELD) et « parametre » (PARAMETER — paramètre de la méthode englobante) tous présents.
- **Bornes** : —
- **Statut** : actif

### laCompletionInstantaneeNeRendJamaisNull — jamais null
- **Préconditions / entrée** : `complete(path, "", 0)` et `complete(path, null, 0)`.
- **Comportement attendu** : une LISTE (éventuellement vide) est toujours retournée — jamais null, même sur source vide ou null.
- **Bornes** : —
- **Statut** : actif

## Notes pour la réécriture
- Le régime instantané est défini par `isLikelyUsable() == false` (classpath vide + jdkHome sans `lib/modules`) : c'est l'état de l'appareil entre le didOpen et la première sync utile — le comportement de repli doit être natif, pas une exception.
- Symétrie des garanties : syntaxe cassée → erreurs de syntaxe publiées ; sain non résolu → silence absolu. Le tri par offset des diagnostics de syntaxe est un contrat de sortie.
- La complétion instantanée ne connaît que le fichier courant : champs, méthodes, type de l'unité, locales et paramètre de la méthode englobante, mots-clés du langage — avec kinds distincts (FIELD/VARIABLE/KEYWORD/CLASS/METHOD/PARAMETER).
- Source null ou vide ne doit jamais faire lever la complétion (liste, pas null).
- Le marqueur `/*CAR*/` + remplacement par le préfixe est la convention de positionnement du caret des corpus — à réutiliser dans le banc de parité.
