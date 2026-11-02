# Fiche 055 — JavaLanguageServerLifecycleTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/lsp/JavaLanguageServerLifecycleTest.java`.
> **Domaine** : cycle-vie-lsp
> **Résumé** : Valide la machine à états du cycle de vie LSP du serveur (UNINITIALIZED → INITIALIZING → INITIALIZED → SHUTDOWN → EXITED) et le contenu de la réponse `initialize` (capabilities, serverInfo). Serveur in-process construit sans environnement de compilation : protocole pur, aucune dépendance à android.jar ou au JDK du projet. Corpus minimal : paramètres d'initialize avec rootUri `file:///test` et processId 12345.

## Comportements

### initialStateIsUninitialized — état initial
- **Préconditions / entrée** : un serveur fraîchement construit, aucune requête.
- **Comportement attendu** : l'état exposé est `UNINITIALIZED`.
- **Bornes** : —
- **Statut** : actif

### initializeTransitionsToInitializing — initialize répond et passe en INITIALIZING
- **Préconditions / entrée** : serveur à l'état initial ; `initialize` avec rootUri `file:///test` et processId 12345.
- **Comportement attendu** : `InitializeResult` non-null avec capabilities non-null et serverInfo non-null (name exact « lspjava », version non-null) ; après la requête, l'état est `INITIALIZING` (et non INITIALIZED — la transition finale attend `initialized`).
- **Bornes** : serverInfo.name = « lspjava » exactement.
- **Statut** : actif

### initializedTransitionsToInitialized — initialized finalise l'initialisation
- **Préconditions / entrée** : serveur initialisé (initialize) ; notification `initialized`.
- **Comportement attendu** : l'état passe à `INITIALIZED`.
- **Bornes** : —
- **Statut** : actif

### shutdownTransitionsToShutdown — shutdown
- **Préconditions / entrée** : serveur à l'état INITIALIZED ; requête `shutdown`.
- **Comportement attendu** : l'état passe à `SHUTDOWN`.
- **Bornes** : —
- **Statut** : actif

### exitTransitionsToExited — exit
- **Préconditions / entrée** : serveur à l'état SHUTDOWN ; notification `exit`.
- **Comportement attendu** : l'état passe à `EXITED`.
- **Bornes** : —
- **Statut** : actif

### standaloneModeDoesNotExitProcess — mode in-process : exit ne tue pas le processus
- **Préconditions / entrée** : serveur avec `setStandaloneMode(false)` (mode in-process, défaut des tests) ; cycle complet initialize → initialized → shutdown → exit.
- **Comportement attendu** : l'état atteint `EXITED` sans arrêter la JVM hôte (pas de `System.exit` en mode in-process) — le test ne fait qu'observer l'état sans crash.
- **Bornes** : —
- **Statut** : actif

### capabilitiesIncludeCodeActionKinds — codeActionProvider annoncé avec quickfix
- **Préconditions / entrée** : réponse `initialize` d'un serveur neuf.
- **Comportement attendu** : `codeActionProvider` annoncé ; si la forme détaillée `CodeActionOptions` est utilisée, `codeActionKinds` est non-null, non vide et contient « quickfix ».
- **Bornes** : —
- **Statut** : actif

### capabilitiesIncludePositionEncoding — positionEncoding UTF-16
- **Préconditions / entrée** : réponse `initialize` d'un serveur neuf.
- **Comportement attendu** : `positionEncoding` = `PositionEncodingKind.UTF16`.
- **Bornes** : valeur exacte UTF16.
- **Statut** : actif

## Notes pour la réécriture
- L'ordre des transitions est verrouillé : initialize → INITIALIZING, initialized → INITIALIZED, shutdown → SHUTDOWN, exit → EXITED (exit après shutdown).
- serverInfo.name exact « lspjava » ; la valeur exacte de la version est verrouillée par ailleurs (LifecycleHygieneTest, I7).
- Mode in-process (standaloneMode false) : `exit` change l'état sans tuer le processus — indispensable pour l'intégration Android et les tests.
- La déclaration de `positionEncoding` (UTF-16) doit être explicite dans les capabilities.
