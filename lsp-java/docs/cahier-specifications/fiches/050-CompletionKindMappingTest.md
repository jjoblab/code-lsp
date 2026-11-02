# Fiche 050 — CompletionKindMappingTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/lsp/CompletionKindMappingTest.java`.
> **Domaine** : completion
> **Résumé** : Vérification BOUT-EN-BOUT des kinds LSP côté serveur : la chaîne complète `EcjCompletionEngine` → `api.CompletionItem.Kind` → `CompletionItemKind` LSP4J (+ champ `data` pour les annotations) — c'est ce que le badge de type du popup consomme côté client. Quatre contextes : membre (méthode/champ), paquet (import), annotation et type. Fixture : service réel `JavaTextDocumentService` sur un `CompilationEnvironment` (racine source + android.jar, compliance « 17 ») ; chaque document est ouvert par `didOpen` puis complété à un offset converti en line/character.

## Comportements

### memberCompletionCarriesMethodAndFieldKinds — kinds Method/Field
- **Préconditions / entrée** : classe avec champ `myField` et méthode `myMethod` ; complétion sur le préfixe « my » dans un corps de méthode.
- **Comportement attendu** : l'item « myMethod » porte kind `Method` ; l'item « myField » porte kind `Field`.
- **Bornes** : —
- **Statut** : actif (hypothèse `@BeforeAll` : android.jar absent → classe ignorée)

### packageCompletionCarriesModuleKind — kind Module pour sous-paquet
- **Préconditions / entrée** : complétion sur `import android.` (fin du préfixe tapé).
- **Comportement attendu** : le sous-paquet « content » porte kind `Module`.
- **Bornes** : —
- **Statut** : actif (hypothèse android.jar présent)

### annotationTypeCarriesDataTag — annotation : Class + data
- **Préconditions / entrée** : complétion TYPE_REFERENCE sur « Overrid » (préfixe de `java.lang.Override`) dans un corps de classe.
- **Comportement attendu** : l'item « Override » voyage avec kind `Class` (le protocole LSP n'a rien de mieux pour un type annotation) ET le champ `data` égal à « annotation » (badge « @ » côté client).
- **Bornes** : data = "annotation" exactement.
- **Statut** : actif (hypothèse android.jar présent)

### typeReferenceCarriesClassKind — kind Class pour un type
- **Préconditions / entrée** : complétion TYPE_REFERENCE sur « Strin » dans un corps de classe.
- **Comportement attendu** : l'item « String » porte kind `Class`.
- **Bornes** : —
- **Statut** : actif (hypothèse android.jar présent)

## Notes pour la réécriture
- La correspondance des kinds est le contrat visible par le client : Method/Field pour les membres, Module pour les sous-paquets, Class pour les types — et Class + `data="annotation"` pour les types annotation (seule distinction possible en LSP).
- Le harnais convertit l'offset en position line/character lui-même (comptage de `\n`) : la précision de la conversion est côté test, pas côté serveur.
- Attention : toute exception du serveur est transformée en liste VIDE par le harnais — un échec silencieux se manifesterait comme « item absent » (piège de diagnostic, pas un comportement à reproduire).
- Les documents sont ouverts par `didOpen` avant complétion (overlay tampons), URIs factices `file:///t1.java`…`t4.java`.
