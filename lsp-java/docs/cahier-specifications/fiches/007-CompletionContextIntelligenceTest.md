# Fiche 007 — CompletionContextIntelligenceTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/ecj/CompletionContextIntelligenceTest.java`.
> **Domaine** : completion
> **Résumé** : Intelligence contextuelle de la complétion (v1.16.0) : la position grammaticale filtre le légal (`extends`/`implements`/`throws`/`catch`/`new`/annotation), la position de membre propose des stubs d'override (snippets), la position de nom de déclaration suggère des noms dérivés du type, les labels d'un `switch` sur enum proposent les constantes (kind ENUM), les valeurs d'attribut d'annotation de type enum proposent les constantes (forme nommée et mono-valeur). Moteur sur racine source temporaire + android.jar (sinon classe ignorée) ; le caret est simulé par une frappe partielle dans `Main.java` réécrit sur disque.

## Comportements

### extendsOfAClassExcludesInterfaces — `extends` d'une classe refuse les interfaces
- **Préconditions / entrée** : `public class Main extends Runn {|}` — caret après « Runn ».
- **Comportement attendu** : « Runnable » ABSENT des propositions (une interface est illégale après `extends` d'une classe).
- **Bornes** : —
- **Statut** : actif (ignoré si android.jar absent — hypothèse en @BeforeAll)

### implementsProposesInterfacesOnly — `implements` accepte les interfaces
- **Préconditions / entrée** : `public class Main implements Runn {|}`.
- **Comportement attendu** : « Runnable » présent.
- **Bornes** : —
- **Statut** : actif (hypothèse android.jar)

### throwsClauseProposesThrowablesOnly — clause `throws` filtre les Throwables
- **Préconditions / entrée** : `void m() throws Ex|`.
- **Comportement attendu** : « Exception » présent ; « Executor » absent (pas un Throwable).
- **Bornes** : —
- **Statut** : actif (hypothèse android.jar)

### catchClauseProposesThrowablesOnly — clause `catch` filtre les Throwables
- **Préconditions / entrée** : `catch (Ex| e)`.
- **Comportement attendu** : « Exception » présent ; « Executor » absent (même filtre que `throws`).
- **Bornes** : —
- **Statut** : actif (hypothèse android.jar)

### newExcludesInterfacesAndAbstractClasses — `new` ne propose que l'instanciable
- **Préconditions / entrée** : `Object x = new ArrayLi|` puis variante `new Runn|`.
- **Comportement attendu** : « ArrayList » présent ; « AbstractList » absent (classe abstraite) ; « Runnable » absent (interface) — seuls les types concrets instanciables sont proposés après `new`.
- **Bornes** : —
- **Statut** : actif (hypothèse android.jar)

### annotationPositionProposesAnnotationsOnly — position d'annotation filtre les @interface
- **Préconditions / entrée** : `@Func|` au-dessus de la déclaration de classe.
- **Comportement attendu** : « FunctionalInterface » présent ; « Function » absent (interface, pas une annotation).
- **Bornes** : —
- **Statut** : actif (hypothèse android.jar)

### memberPositionProposesOverrideStubs — stub d'override en position de membre
- **Préconditions / entrée** : `toStr|` tapé dans le corps d'une classe.
- **Comportement attendu** : un item dont le label commence par « toString( » ; `isSnippet()` = true ; l'`insertText` contient « @Override », « public String toString() » (signature complète), « super.toString(); » (corps par défaut) et « $0 » (position d'arrêt final du snippet).
- **Bornes** : —
- **Statut** : actif (hypothèse android.jar)

### overrideStubOfInterfaceMethodThrowsInsteadOfSuper — stub d'interface lève au lieu de super
- **Préconditions / entrée** : `public class Main implements Runnable { ru| }`.
- **Comportement attendu** : stub « run( » présent ; son `insertText` contient « throw new UnsupportedOperationException(); » (l'appel à `super` serait illégal pour une méthode d'interface).
- **Bornes** : —
- **Statut** : actif (hypothèse android.jar)

### declarationNameSuggestsNamesFromType — suggestions de nom pour une locale
- **Préconditions / entrée** : `HttpClient |` (déclaration de locale, nom vide, caret juste après l'espace final — chaîne construite explicitement pour maîtriser l'espace).
- **Comportement attendu** : « httpClient » (type en lowerFirst complet) ET « client » (suffixe court) présents dans les propositions.
- **Bornes** : —
- **Statut** : actif (hypothèse android.jar)

### fieldDeclarationNameSuggestsNamesFromType — suggestions de nom pour un champ
- **Préconditions / entrée** : `private Button |` au niveau de la classe (chaîne explicite, espace final).
- **Comportement attendu** : « button » présent.
- **Bornes** : —
- **Statut** : actif (hypothèse android.jar)

### declarationHeadKeywordsProposedWhileTyping — mots-clés de tête de déclaration (AMENDEMENT D7, V-C3/G-024)
- **Préconditions / entrée** : `public class Principal e|` — caret après le
  préfixe `e`, la déclaration n'a pas encore ouvert son accolade (tête
  TOP LEVEL).
- **Comportement attendu** : la liste propose le MOT-CLÉ `extends`
  (pareillement `implements`/`permits` pendant leur frappe), **fusionnés**
  aux stubs d'override de la hiérarchie du type en cours de déclaration
  (genre `TOP_NIVEAU`). La clause APRÈS le mot-clé (`implements Ru|`) reste
  une position de TYPE (interfaces seules). AVANT l'amendement : la
  position capturait le sélecteur de méthode (CORPS_CLASSE) et les
  mots-clés étaient muets — l'écart ECARTS-SERVEUR M1, résolu par la
  décision D7 (31/10/2026).
- **Bornes** : une tête LOCALE (classe déclarée dans un corps de méthode)
  reste un artefact de recovery d'ecj — mots-clés non proposés (borne
  documentée USAGE §7.21).
- **Statut** : actif (amendement D7 — le verrou « muets » est LEVÉ : le
  comportement livré est le comportement attendu ; test amendé
  `RetoursAppareilV116Test.motCleExtendsDansLaTeteDeDeclaration`)

### caseLabelProposesEnumConstants — labels de switch sur enum
- **Préconditions / entrée** : `switch (c)` sur `enum Color { RED, GREEN, BLUE }`, label partiel `case RE|:`.
- **Comportement attendu** : « RED » présent ET l'item « RED » voyage avec le kind `ENUM` (`CompletionItem.Kind.ENUM`).
- **Bornes** : —
- **Statut** : actif (hypothèse android.jar)

### annotationEnumValueProposesConstants — valeurs d'enum dans les annotations
- **Préconditions / entrée** : `@Check(severity = |)` où `severity()` est de type `Level` (enum LOW/MEDIUM/HIGH), puis forme mono-valeur `@Single(LO|)` où l'attribut implicite `value` est de type `Level` (JLS 9.7.3).
- **Comportement attendu** : forme nommée : « LOW » et « MEDIUM » présents ; forme mono-valeur : « LOW » présent.
- **Bornes** : —
- **Statut** : actif (hypothèse android.jar)

## Notes pour la réécriture
- Les cas « nom vide » utilisent des chaînes construites à la main (pas de text-block) pour éliminer les espaces de fin de ligne et placer le caret exactement en position de NOM — piège à conserver tel quel dans les tests.
- Deux corps de stub d'override : `super.x();` pour les méthodes de classe, `throw new UnsupportedOperationException();` pour les méthodes d'interface ; le snippet se termine par `$0`.
- Le filtrage grammatical s'appuie sur la position dans l'AST de recovery (pas d'heuristique lexicale) — comportement observable : présence/absence d'items selon la position.
- Les suggestions de noms suivent au minimum : lowerFirst du type complet + suffixe court (les tests n'exigent que « httpClient »/« client » et « button »).
- android.jar (plateformes 34/35 ou ANDROID_HOME) sert de classpath binaire — l'ensemble de la classe est ignoré sans lui.
