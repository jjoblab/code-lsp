# Fiche 023 — EcjInlayAndSignatureTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/ecj/EcjInlayAndSignatureTest.java` (la classe interne se nomme `JdtInlayAndSignatureTest`).
> **Domaine** : hover-inlay
> **Résumé** : Tests des inlay hints de PARAMÈTRES (`EcjInlayHints`) et de l'aide de signature (`EcjSignatureHelp`) sur bindings ecj. Inlays : un hint par argument (« name: ») ancré avant l'argument, noms issus de la déclaration — même fichier ou autre fichier du projet, résolution d' surcharge, et silence total pour les méthodes binaires (jamais de faux noms). Signature help : énumération des surcharges (binaires comprises), paramètre actif compté, noms de paramètres source, et « niveau 4 » — le texte vivant CASSÉ (parenthèse ouverte, virgule de fin, argument à moitié tapé, chaîne non refermée, constructeur ouvert, booléen) est quand même servi via variantes réparées, tandis que hors appel la réponse reste null.

## Comportements

### hintsForLocalMethodCall — hints sur un appel local
- **Préconditions / entrée** : `greet(String name, int times)` déclarée ; appel « greet("world", 3) ».
- **Comportement attendu** : 2 hints : « name: » (ancré juste avant l'argument « "world" ») et « times: ».
- **Bornes** : 2 hints exactement.
- **Statut** : actif

### hintsForMethodDeclaredInAnotherFile — noms depuis un autre fichier
- **Préconditions / entrée** : `Helper.add(int first, int second)` dans `demo/Helper.java` ; appel « h.add(10, 20) » dans `demo/Main.java` (racine source déclarée au moteur).
- **Comportement attendu** : 2 hints « first: » et « second: » — les noms viennent de la déclaration de l'AUTRE fichier.
- **Bornes** : 2 hints exactement.
- **Statut** : actif

### noHintsForBinaryMethod — méthode binaire : aucun hint
- **Préconditions / entrée** : appel « s.substring(1, 4) » (méthode du jrt, binaire).
- **Comportement attendu** : AUCUN hint (pas de noms de paramètres disponibles → jamais de faux noms).
- **Bornes** : 0 hint.
- **Statut** : actif

### overloadResolutionPicksRightParameterNames — résolution d'overcharge
- **Préconditions / entrée** : `area(int size)` et `area(int width, int height)` ; appel « area(2, 3) ».
- **Comportement attendu** : 2 hints « width: » et « height: » — l'appel à 2 arguments résout vers `area(int, int)`.
- **Bornes** : 2 hints exactement.
- **Statut** : actif

### signatureHelpListsBinaryOverloads — surcharges binaires énumérées
- **Préconditions / entrée** : « sb.append("hello") », caret dans le premier argument.
- **Comportement attendu** : résultat non null ; ≥ 5 signatures (les surcharges de `StringBuilder.append`), labels contenant « append( » ; paramètre actif = 0.
- **Bornes** : ≥ 5 signatures ; activeParameter = 0.
- **Statut** : actif

### signatureHelpCountsActiveParameter — paramètre actif dans un appel chaîné
- **Préconditions / entrée** : « sb.append("a").append('b') », caret avant l'argument « 'b' » du second appel.
- **Comportement attendu** : résultat non null ; activeParameter = 0.
- **Bornes** : activeParameter = 0.
- **Statut** : actif

### signatureHelpSourceParameterNames — noms de paramètres de la déclaration source
- **Préconditions / entrée** : `Helper.add(int first, int second)` (source) ; appel « h.add(1, 2) », caret sur « 1, 2 ».
- **Comportement attendu** : 1 signature ; le label contient « first » et « second » ; la liste des noms de paramètres est exactement [« first », « second »].
- **Bornes** : 1 signature exactement.
- **Statut** : actif

### signatureHelpNullOutsideCall — hors appel : null
- **Préconditions / entrée** : caret sur « int x » (déclaration locale, pas dans un appel).
- **Comportement attendu** : null.
- **Bornes** : —
- **Statut** : actif

### activeParameterCountsCommasNotInStrings — virgules dans les chaînes ignorées
- **Préconditions / entrée** : texte « run(a, "x,y", c); », caret après « "x,y" » (3e argument en cours).
- **Comportement attendu** : `countActiveParameter` retourne 1 (la virgule DANS la chaîne littérale n'est pas un séparateur — index d'argument 1).
- **Bornes** : 1.
- **Statut** : actif

### signatureHelpSurvivesOpenParen — appel non refermé (niveau 4)
- **Préconditions / entrée** : « sb.append( » sans fermeture, caret juste après la parenthèse ouverte.
- **Comportement attendu** : le résultat est servi (variante réparée qui referme l'appel) : ≥ 5 signatures, activeParameter = 0.
- **Bornes** : ≥ 5 signatures ; activeParameter = 0.
- **Statut** : actif

### signatureHelpSurvivesTrailingComma — virgule de fin (variante B)
- **Préconditions / entrée** : « h.add(1, » non refermé (virgule traînante), caret après la virgule ; `add` déclaré en source dans un autre fichier.
- **Comportement attendu** : résultat servi via apposition d'un littéral de secours : activeParameter = 1 ; au moins une signature portant le sélecteur « add( ».
- **Bornes** : activeParameter = 1.
- **Statut** : actif

### signatureHelpSurvivesMidWordArgument — argument à moitié tapé (variante D)
- **Préconditions / entrée** : « sb.append("a").append(fi » (identifiant non résoluble en cours de frappe), caret dans « fi ».
- **Comportement attendu** : résultat servi par substitution de l'argument : activeParameter = 0 ; sélecteur « append( » présent.
- **Bornes** : activeParameter = 0.
- **Statut** : actif

### signatureHelpSurvivesUnterminatedString — chaîne non refermée (variante A)
- **Préconditions / entrée** : « sb.append("abc » (littéral ouvert), caret dans la chaîne.
- **Comportement attendu** : résultat servi (refermeture du littéral puis de l'appel) : activeParameter = 0.
- **Bornes** : activeParameter = 0.
- **Statut** : actif

### signatureHelpSurvivesOpenConstructor — constructeur non refermé
- **Préconditions / entrée** : « new StringBuilder( » sans fermeture, caret après la parenthèse.
- **Comportement attendu** : résultat servi ; ≥ 3 constructeurs de `StringBuilder` énumérés.
- **Bornes** : ≥ 3 signatures.
- **Statut** : actif

### signatureHelpSurvivesBooleanParameter — paramètre booléen (variante F)
- **Préconditions / entrée** : « toggle( » ouvert, `toggle(boolean flag)` étant l'unique surcharge (0 argument impossible).
- **Comportement attendu** : résultat servi (substitution `true`) : 1 signature contenant « boolean ».
- **Bornes** : 1 signature exactement.
- **Statut** : actif

### signatureHelpStillNullOutsideCall — garde du niveau 4 hors appel
- **Préconditions / entrée** : « int x = 1 + » (expression incomplète, AUCUNE parenthèse d'appel ouverte), caret après « 1 + ».
- **Comportement attendu** : null — le niveau 4 ne doit RIEN inventer hors d'une liste d'arguments.
- **Bornes** : —
- **Statut** : actif

## Notes pour la réécriture
- Inlays : label « <nom>: » ancré à l'offset de CHAQUE argument ; source des noms = déclaration (projet multi-fichiers), résolution d'overcharge obligatoire ; binaire → silence (jamais de noms devinés).
- Signature help : le comptage du paramètre actif doit ignorer les virgules à l'intérieur des chaînes littérales ; surcharges binaires énumérées depuis les bindings.
- Le « niveau 4 » (texte cassé) est une famille de variantes de réparation : A (chaîne refermée), B (littéral de secours après virgule traînante), D (identifiant à moitié tapé substitué), F (`true` pour booléen) — la réponse hors appel reste null (garde absolue).
- Les seuils « ≥ 5 » / « ≥ 3 » traduisent le nombre réel de surcharges JDK : le cahier exige l'énumération COMPLETE des surcharges, pas un minimum arbitraire.
- Anomalie de nommage : fichier `EcjInlayAndSignatureTest.java`, classe `JdtInlayAndSignatureTest`.
