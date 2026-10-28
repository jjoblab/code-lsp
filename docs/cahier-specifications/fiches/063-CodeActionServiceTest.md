# Fiche 063 — CodeActionServiceTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/services/CodeActionServiceTest.java`.
> **Domaine** : diagnostics
> **Résumé** : Quick-fixes à ÉDITS RÉELS (CodeActionService) — portages des quick-fixes du projet tiers de référence (JavaActions.kt / JavaCompilerFixes.kt) : suppression d'import inutilisé (ligne entière), insertion du point-virgule manquant, suppression prudente des déclarations inutilisées, « Entourer avec try-catch » / « Déclarer throws » sur exception non gérée, « Insérer un cast » vers le type attendu, et « Importer X (fqn) » depuis l'index. Deux montages : service TEXTE pur (sans environnement — fixes sans index) et service AVEC INDEX réel (jrt du JDK + android.jar — fixes d'import) ; hypothèse visible dans `@BeforeAll` : android.jar présent, sinon TOUTE la classe est ignorée (y compris les tests textuels). Diagnostics synthétiques : plage + sévérité ERROR + code « ecj:<ID> » + arguments structurés.

## Comportements

### unusedImportDeletesWholeLine — import inutilisé : ligne entière supprimée
- **Préconditions / entrée** : service texte pur ; source avec deux imports (`java.util.List`, `java.util.ArrayList`) ; diagnostic UnusedImport sur « java.util.List ».
- **Comportement attendu** : une action « quickfix.remove.import » avec exactement 1 édition ; après application, la LIGNE d'import entière a disparu et l'autre import survit.
- **Bornes** : 1 édition exactement.
- **Statut** : actif

### unusedImportNotOfferedOutsideImportLine — pas de suppression hors ligne d'import
- **Préconditions / entrée** : diagnostic UnusedImport dont la plage est sur « int List » (une déclaration, pas une ligne import).
- **Comportement attendu** : AUCUNE action « quickfix.remove.import ».
- **Bornes** : —
- **Statut** : actif

### missingSemiInsertedAtLineEnd — point-virgule en fin d'instruction
- **Préconditions / entrée** : source « int x = 5 » sans point-virgule ; diagnostic MissingSemiColon sur « 5 ».
- **Comportement attendu** : une action « quickfix.semicolon » ; après application, « int x = 5; » — le « ; » se retrouve en fin d'instruction.
- **Bornes** : —
- **Statut** : actif

### unusedLocalDeclarationDeleted — déclaration locale inutilisée supprimée
- **Préconditions / entrée** : « int unused = 5; » suivie d'une instruction ; diagnostic LocalVariableIsNeverUsed sur « unused ».
- **Comportement attendu** : une action « quickfix.remove.unused » ; la déclaration disparaît ; l'instruction suivante survit.
- **Bornes** : —
- **Statut** : actif

### multiFragmentDeclarationIsLeftAlone — déclaration multi-fragments intacte
- **Préconditions / entrée** : « int a = 1, b = 2; » ; diagnostic LocalVariableIsNeverUsed sur « b ».
- **Comportement attendu** : AUCUNE action « quickfix.remove.unused » (risque de supprimer la variable sœur).
- **Bornes** : —
- **Statut** : actif

### parameterIsLeftAlone — paramètre jamais supprimé
- **Préconditions / entrée** : « void m(int param) » ; diagnostic ArgumentIsNeverUsed sur « param ».
- **Comportement attendu** : AUCUNE action « quickfix.remove.unused » (un paramètre de méthode ne se supprime pas).
- **Bornes** : —
- **Statut** : actif

### tryCatchWrapsStatementWithExactException — try-catch avec le type exact
- **Préconditions / entrée** : « new java.io.FileInputStream(f); » dans une méthode ; diagnostic UnhandledException avec argument « java.io.FileNotFoundException ».
- **Comportement attendu** : une action « quickfix.surround.trycatch » ; après application : un bloc « try { » … « } catch (FileNotFoundException e) { » ciblant le type EXACT de l'argument structuré ; l'import « import java.io.FileNotFoundException; » est AJOUTÉ (type hors java.lang) ; l'instruction initiale reste dans le try.
- **Bornes** : —
- **Statut** : actif

### throwsCreatedAfterParameterList — throws créé après la liste de paramètres
- **Préconditions / entrée** : « void m(int a) » contenant `Thread.sleep(10)` ; diagnostic UnhandledException avec argument « java.lang.InterruptedException ».
- **Comportement attendu** : une action « quickfix.declare.throws » ; après application : « void m(int a) throws InterruptedException » — la clause suit directement la liste de paramètres.
- **Bornes** : —
- **Statut** : actif

### throwsAppendedToExistingClause — throws ajouté à la clause existante
- **Préconditions / entrée** : « void m() throws java.io.IOException { » avec `Thread.sleep(10)` ; même diagnostic (java.lang.InterruptedException).
- **Comportement attendu** : après application : « throws java.io.IOException, InterruptedException » — la nouvelle exception s'AJOUTE à la clause existante (pas de remplacement).
- **Bornes** : —
- **Statut** : actif

### throwsFindsMethodInsideControlBlocks — méthode trouvée à travers un bloc de contrôle
- **Préconditions / entrée** : `Thread.sleep(10)` placé DANS un `if (true) { … }` à l'intérieur de `m()` ; même diagnostic.
- **Comportement attendu** : le scan d'accolades traverse le bloc de contrôle : le throws atterrit sur « void m() throws InterruptedException », jamais sur le if.
- **Bornes** : —
- **Statut** : actif

### castUsesExpectedType — cast vers le type ATTENDU
- **Préconditions / entrée** : « String s = o; » ; diagnostic TypeMismatch avec arguments (« java.lang.Object », « java.lang.String ») — message ecj « cannot convert from {0} to {1} ».
- **Comportement attendu** : une action « quickfix.cast » ; après application : « (String) o » — le cast cible le type ATTENDU (deuxième argument du diagnostic), pas le type source.
- **Bornes** : —
- **Statut** : actif

### unresolvedTypeOffersImportCandidates — candidats d'import depuis l'index
- **Préconditions / entrée** : service AVEC index ; « List list = null; » ; diagnostic UndefinedType avec argument « List ».
- **Comportement attendu** : au moins 1 action « quickfix.import.fqn » ; au moins un titre contenant « java.util.List » ; CHAQUE action de ce kind porte exactement 1 édition — insertion PURE (début = fin) d'un texte commençant par « import » et finissant par « ;\n ».
- **Bornes** : 1 édition par action ; insertion pure (start == end).
- **Statut** : actif

### unresolvedLowercaseNameGetsNoImportFix — nom minuscule : pas d'import
- **Préconditions / entrée** : service avec index ; « foo(); » ; diagnostic UndefinedMethod avec argument « foo » (initiale minuscule).
- **Comportement attendu** : AUCUNE action « quickfix.import.fqn » — seuls les noms ressemblant à des TYPES (initiale majuscule) reçoivent des candidats d'import.
- **Bornes** : —
- **Statut** : actif

### importFixSkipsAlreadyImportedAndJavaLang — déjà importé et java.lang exclus
- **Préconditions / entrée** : service avec index ; source contenant déjà « import java.util.List; » ; diagnostics UndefinedType sur « List » et sur « String ».
- **Comportement attendu** : pour « List » : aucun candidat dont le titre contient « java.util.List » (déjà importé — un autre « List » du classpath, p.ex. java.awt.List, reste légitimement proposable) ; pour « String » : AUCUNE action « quickfix.import.fqn » (java.lang n'est jamais proposé).
- **Bornes** : —
- **Statut** : actif

### noActionWithoutRealEdits — contrat : aucune action sans édits
- **Préconditions / entrée** : service TEXTE pur ; « foo(); » ; diagnostic UndefinedMethod avec argument « foo ».
- **Comportement attendu** : TOUTES les actions rendues portent des édits non-null et non vides (aucune action « coquille vide »).
- **Bornes** : —
- **Statut** : actif

## Notes pour la réécriture
- Piège de montage : l'hypothèse android.jar est dans `@BeforeAll` — sans SDK, même les tests du service TEXTE pur sont ignorés ; en réécriture, détacher les fixes texte de la dépendance SDK pour rendre le banc exécutable partout.
- Kinds verrouillés : quickfix.remove.import, quickfix.semicolon, quickfix.remove.unused, quickfix.surround.trycatch, quickfix.declare.throws, quickfix.cast, quickfix.import.fqn, quickfix.create.method (ce dernier par CreateMethodFixTest).
- Diagnostics structurés attendus : code « ecj:<ID> » + arguments — type exact de l'exception (Uncaught), types source/attendu du mismatch (args[0]/args[1] du message « cannot convert from {0} to {1} »), nom non résolu.
- Règles de prudence à conserver : jamais de suppression hors ligne d'import, sur multi-fragments, ou sur un paramètre ; try-catch cible le type exact ; throws traverse les blocs de contrôle.
- Le fix d'import est une insertion pure (début = fin) en « import …;\n » ; java.lang et les types déjà importés sont exclus des candidats.
