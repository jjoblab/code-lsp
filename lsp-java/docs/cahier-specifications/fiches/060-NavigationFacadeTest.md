# Fiche 060 — NavigationFacadeTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/lsp/NavigationFacadeTest.java`.
> **Domaine** : navigation
> **Résumé** : Tests E2E à travers la façade LSP réelle : `textDocument/implementation` (GO TO « Implementations » — les héritiers DIRECTS du type en contexte, multi-fichiers par balayage des racines sources) et `textDocument/superDefinition` (GO TO « Super » — le membre outrepassé dans chaque supertype, sinon les supertypes DIRECTS du type en contexte). Fixtures multi-fichiers sous `tmp/src` : `Base`/`ImplA`/`ImplB` (héritage de classe), `Greeter`/`EnGreeter` (implémentation d'interface), `Nest` (sous-classe imbriquée dans le même fichier), `Solo` (sans descendance), `MyThread` (supertype BINAIRE java.lang.Thread — aucune source navigable). Hypothèse visible dans `@BeforeAll` : `android.jar` présent (ANDROID_HOME/ANDROID_SDK_ROOT/`/home/z/tools/android-sdk`), sinon toute la classe est ignorée ; environnement racine src + android.jar, Java 17, façade construite directement.

## Comportements

### implementation_typeReference_findsCrossFileSubtypes — référence de type → héritiers inter-fichiers
- **Préconditions / entrée** : 9 fichiers ouverts (Base, ImplA, ImplB, Main, Greeter, EnGreeter, Nest, Solo, MyThread) ; caret sur « Base » (référence de type) dans `Main.use(Base b)`.
- **Comportement attendu** : exactement 2 cibles — `ImplA` et `ImplB` — chacue dans SON fichier, positionnée sur le NOM de classe (ligne et colonne exactes de « class ImplA » / « class ImplB »).
- **Bornes** : 2 cibles exactement ; positions du nom vérifiées ligne + colonne.
- **Statut** : actif

### implementation_insideClass_findsSubtypes — caret dans la classe → classe englobante
- **Préconditions / entrée** : fichiers ouverts ; caret sur le champ « counter » DANS `Base` (pas sur une référence de type).
- **Comportement attendu** : le contexte retombe sur la classe ENGLOBANTE (`Base`) → exactement 2 cibles (ImplA, ImplB).
- **Bornes** : 2 cibles exactement.
- **Statut** : actif

### implementation_noSubclass_yieldsEmpty — sans descendance → vide
- **Préconditions / entrée** : `Solo.java` ouvert ; caret sur « tag » (contexte Solo, aucune sous-classe).
- **Comportement attendu** : liste VIDE.
- **Bornes** : —
- **Statut** : actif

### implementation_interfaceImplementors — implémenteurs d'une interface
- **Préconditions / entrée** : `Greeter.java` et `EnGreeter.java` ouverts ; caret sur « Greeter » dans la clause « implements Greeter » de `EnGreeter`.
- **Comportement attendu** : exactement 1 cible : `EnGreeter.java`, sur le nom de la classe implémentante (colonne exacte de « class EnGreeter »).
- **Bornes** : 1 cible exactement.
- **Statut** : actif

### implementation_nestedSubclass_sameFile — sous-classe imbriquée même fichier
- **Préconditions / entrée** : `Nest.java` ouvert (contient `class Inner extends Nest`) ; caret sur « tag » (contexte Nest).
- **Comportement attendu** : exactement 1 cible : la sous-classe imbriquée `Inner`, dans le MÊME fichier, sur le nom « class Inner » (colonne exacte).
- **Bornes** : 1 cible exactement.
- **Statut** : actif

### super_onOverridingMethod_findsBaseMethod — super sur une redéfinition → méthode de base
- **Préconditions / entrée** : `Base.java` et `ImplA.java` ouverts ; caret sur « run » (la déclaration OUTREPASSANTE dans ImplA, marquée @Override).
- **Comportement attendu** : exactement 1 cible dans `Base.java`, dont la plage désigne le NOM « run » dans la source de Base ; libellé exact « run  ·  Base » (format du picker).
- **Bornes** : 1 cible exactement ; libellé exact « run  ·  Base ».
- **Statut** : actif

### super_onTypeInsideImpl_findsBaseClass — super hors membre outrépassant → supertype direct
- **Préconditions / entrée** : `Base.java` et `ImplA.java` ouverts ; caret sur le champ « tag » de `ImplA` (pas un membre outrepassant).
- **Comportement attendu** : exactement 1 cible : le supertype DIRECT de `ImplA` — `Base.java`, plage sur le NOM « Base » ; libellé exact « Base  ·  demo ».
- **Bornes** : 1 cible exactement ; libellé exact « Base  ·  demo ».
- **Statut** : actif

### super_binarySupertype_yieldsEmpty — supertype binaire → vide
- **Préconditions / entrée** : `MyThread.java` ouvert (extends Thread) ; caret sur « tag » — le supertype direct est `java.lang.Thread`, BINAIRE (classpath).
- **Comportement attendu** : liste VIDE (un supertype binaire n'a pas de source projet).
- **Bornes** : —
- **Statut** : actif

### super_overriddenViaInvocation_findsBaseMethod — super via une invocation → vide
- **Préconditions / entrée** : 4 fichiers ouverts ; caret sur « run » dans l'INVOCATION `b.run()` (dans `Main`).
- **Comportement attendu** : liste VIDE — la méthode liée à l'appel est la plus spécifique (héritée de Base, rien au-dessus), et la retombée sur les supertypes du type EN CONTEXTE (la classe englobante `Main` → Object) ne donne aucune cible source.
- **Bornes** : —
- **Statut** : actif

### implementation_interfaceMethodReference_findsImplementor — sur la propre déclaration de l'interface
- **Préconditions / entrée** : `Greeter.java` et `EnGreeter.java` ouverts ; caret sur « Greeter » dans SA PROPRE déclaration (« interface Greeter »).
- **Comportement attendu** : exactement 1 cible : `EnGreeter.java` (les implémentations de l'interface).
- **Bornes** : 1 cible exactement.
- **Statut** : actif

## Notes pour la réécriture
- Convention de contexte : un caret sans référence de type utilise la classe ENGLOBANTE comme type en contexte (champ, corps vide, etc.).
- `implementation` ne rend que les héritiers/implémenteurs DIRECTS (pas la descendance transitive) et fonctionne inter-fichiers par balayage des racines sources, y compris pour une sous-classe imbriquée du même fichier.
- Types de retour distincts : `implementation` rend des `Location` LSP (URI + plage du nom déclarant) ; `superDefinition` rend des cibles internes (chemin + plage + libellé picker « nom  ·  conteneur »).
- Libellés picker exacts verrouillés : « run  ·  Base », « Base  ·  demo » (deux espaces autour du séparateur « · »).
- Hypothèse d'environnement android.jar (ANDROID_HOME/ANDROID_SDK_ROOT/`/home/z/tools/android-sdk`, plateformes balayées) — sinon classe ignorée à l'exécution.
