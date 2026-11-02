# Fiche 020 — EcjHoverDefinitionCompletionTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/ecj/EcjHoverDefinitionCompletionTest.java` (la classe interne se nomme `JdtHoverDefinitionCompletionTest`).
> **Domaine** : completion (fiche mixte : hover, définitions et complétion sur les bindings ecj)
> **Résumé** : Verrouille les trois fonctionnalités de base pilotées par bindings sur une unité isolée : hover (texte contenant nom + types), définition (plage de la DÉCLARATION visée par une référence) et complétion (membres sur instance, membres statiques sur type, locales et membres englobants, filtrage par préfixe, robustesse au code cassé). Le moteur est construit avec racines sources et classpath de jars VIDES, conformité « 17 » — la plateforme (java.lang, java.util…) vient de l'image jrt du JVM de test (chemin desktop). Fixture principale : `demo.Main` avec champ `counter`, méthodes `greet(String)`, `getCounter()`.

## Comportements

### hoverOnMethodDeclaration — hover sur une déclaration de méthode
- **Préconditions / entrée** : caret sur « greet » (déclaration) dans `demo/Main.java`.
- **Comportement attendu** : hover non null contenant « greet » (nom de la méthode) et « String » (type de retour).
- **Bornes** : —
- **Statut** : actif

### hoverOnFieldReference — hover sur une référence de champ
- **Préconditions / entrée** : caret sur « counter » dans l'incrémentation « counter + 1 » (référence, pas déclaration).
- **Comportement attendu** : hover non null contenant « int » (type) et « counter » (nom du champ).
- **Bornes** : —
- **Statut** : actif

### hoverOnStringMethodCall — hover sur un appel de méthode binaire
- **Préconditions / entrée** : source avec « return s.length(); », caret sur « length ».
- **Comportement attendu** : hover non null contenant « length » et « int » (type de retour de `String.length()`).
- **Bornes** : —
- **Statut** : actif

### hoverOutsideAnyNode — pas de nœud hoverable → null
- **Préconditions / entrée** : offset 0 (tout début du `package`).
- **Comportement attendu** : hover NULL (aucun binding hoverable à cet offset).
- **Bornes** : —
- **Statut** : actif

### definitionOfLocalVariable — définition d'une variable locale
- **Préconditions / entrée** : caret sur « message » dans « return message ».
- **Comportement attendu** : plage de définition non null couvrant le « message » de la DÉCLARATION (« String message »).
- **Bornes** : start de la plage dans [début de « String message », début + 50).
- **Statut** : actif

### definitionOfField — définition d'un champ
- **Préconditions / entrée** : caret sur « counter » dans « counter + 1 ».
- **Comportement attendu** : plage visant le champ déclaré (« private int counter »).
- **Bornes** : start de la plage dans [début de « private int counter », début + 25).
- **Statut** : actif

### definitionOfMethodCall — définition d'une méthode appelée
- **Préconditions / entrée** : source avec « return decorate(name); », caret sur « decorate(name) ».
- **Comportement attendu** : plage visant la méthode déclarée (« private String decorate »).
- **Bornes** : start de la plage dans [début de « private String decorate », début + 30).
- **Statut** : actif

### memberCompletionOnStringVariable — complétion de membres sur un String
- **Préconditions / entrée** : source « return s.le| » (préfixe « le » après le point), caret en fin de préfixe.
- **Comportement attendu** : liste non null contenant un item de label « length ».
- **Bornes** : —
- **Statut** : actif

### memberCompletionIncludesInheritedMembers — membres hérités proposés
- **Préconditions / entrée** : source « return s.| » (préfixe vide juste après le point).
- **Comportement attendu** : items « isEmpty », « charAt » (String) ET « equals » (hérité de Object) présents — la hiérarchie complète (CharSequence/Comparable/Object) est balayée.
- **Bornes** : —
- **Statut** : actif

### memberCompletionOnOwnField — membres sur un champ propre
- **Préconditions / entrée** : champ « private String name = "x"; » puis « return name.le| ».
- **Comportement attendu** : « length » proposé sur le champ String.
- **Bornes** : —
- **Statut** : actif

### memberCompletionStaticQualifier — statique sur un qualifier de type
- **Préconditions / entrée** : « return String.va| » (qualifier = le type String lui-même).
- **Comportement attendu** : « valueOf » (membre statique) proposé ; « charAt » (méthode d'instance) NON proposé sur un qualifier de type.
- **Bornes** : —
- **Statut** : actif

### nameCompletionProposesLocals — locales proposées en complétion de nom
- **Préconditions / entrée** : « int total = 42; » puis « return tot| ».
- **Comportement attendu** : la locale « total » est proposée.
- **Bornes** : —
- **Statut** : actif

### nameCompletionProposesEnclosingMembers — membres de la classe englobante proposés
- **Préconditions / entrée** : champ « private int counter » puis « return cou| » dans une méthode.
- **Comportement attendu** : le champ « counter » est proposé.
- **Bornes** : —
- **Statut** : actif

### memberCompletionFiltersByPrefix — filtrage strict par préfixe
- **Préconditions / entrée** : « return s.is| ».
- **Comportement attendu** : TOUS les items proposés commencent par « is ».
- **Bornes** : —
- **Statut** : actif

### completionOnBrokenCodeStillWorks — complétion robuste au code cassé
- **Préconditions / entrée** : une ligne cassée au-dessus du caret (« int broken = undefinedThing; ») puis « return s.le| ».
- **Comportement attendu** : la récupération ecj résout malgré tout le contexte : « length » est proposé.
- **Bornes** : —
- **Statut** : actif

## Notes pour la réécriture
- La complétion distingue membre-sur-instance vs membre-statique-sur-type : jamais de méthode d'instance sur un qualifier de type.
- La définition retourne la plage de la DÉCLARATION (pas de la référence) — les bornes vérifiées sont tolérantes (fenêtres de 25–50 caractères), le cahier ne fixe pas la longueur exacte de la plage.
- Robustesse exigée : la complétion fonctionne sur du code syntaxiquement/résolutivement cassé autour du caret.
- Hover « null » hors symbole (offset 0) : l'absence de réponse est une réponse valide.
- Anomalie de nommage : fichier `EcjHoverDefinitionCompletionTest.java`, classe `JdtHoverDefinitionCompletionTest`.
