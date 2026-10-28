# Fiche 056 — JavaS1RicheTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/lsp/JavaS1RicheTest.java`.
> **Domaine** : autre
> **Résumé** : Lot S1 « parité utile JDT.LS » (plan de stabilisation §3.1), volontairement transversal : tags de diagnostics (S1-b — `Unnecessary` sur un import inutilisé), `workspace/symbol` (S1-c — recherche de types par préfixe sur les sources du projet, requête vide et moteur non prêt → liste vide), registre des serveurs vivants (S1-d — rappel d'arrêt à la façade) et événements de fichiers surveillés. Domaine « autre » car mixte (diagnostics / navigation de symboles / cycle de vie) — chaque comportement porte son périmètre. Montage : serveur réel + client espion (enregistre les `publishDiagnostics`), SANS classpath pour les cas « moteur non prêt ». Fixtures : classe avec import inutilisé (`T1.java`), affectation erronée (`T2.java`), `com/ex/MaVue.java`, `com/ex/Vue.java`.

## Comportements

### importInutiliseVoyageAvecTagUnnecessary — import inutilisé taggé Unnecessary
- **Préconditions / entrée** : serveur initialisé avec le dossier comme racine source, client espion connecté ; `didOpen` de `T1.java` : « import java.util.ArrayList;\n\nclass T1 {\n}\n ».
- **Comportement attendu** : une publication de diagnostics arrive (asynchrone) ; au moins un diagnostic porte le tag `DiagnosticTag.Unnecessary` et son code est exactement « ecj:<IProblem.UnusedImport> ».
- **Bornes** : publication attendue sous 15 s (polling de 100 ms).
- **Statut** : actif

### diagnosticSansCodeEcnConnuSansTag — pas de tag inventé hors famille
- **Préconditions / entrée** : `didOpen` de `T2.java` : « class T2 {\n    int x = "texte";\n}\n » (erreur de type).
- **Comportement attendu** : une publication arrive ; au moins un diagnostic (l'erreur de type) a des tags null OU vides — aucun tag n'est inventé sur un problème sans famille connue.
- **Bornes** : publication attendue sous 15 s (polling de 100 ms).
- **Statut** : actif

### capaciteWorkspaceSymbolDeclaree — capacité workspaceSymbolProvider déclarée
- **Préconditions / entrée** : création des capabilities du serveur.
- **Comportement attendu** : `workspaceSymbolProvider` est annoncé en forme booléenne `TRUE` (côté gauche du Either).
- **Bornes** : —
- **Statut** : actif

### requeteVideListeVideConventionI9 — requête vide → liste vide (I9)
- **Préconditions / entrée** : serveur initialisé sur une racine source vide ; requête `workspace/symbol` avec requête « » (chaîne vide).
- **Comportement attendu** : la réponse (forme liste, gauche) est une liste VIDE — jamais d'exception (convention I9).
- **Bornes** : —
- **Statut** : actif

### rechercheTypeSourceLocalisationReelleEtPlage — type source trouvé, localisation réelle
- **Préconditions / entrée** : fichier `com/ex/MaVue.java` sur disque (« package com.ex;\n\npublic class MaVue {\n}\n ») ; serveur initialisé sur le dossier ; notification `didChangeWatchedFiles` avec liste VIDE (force la construction de l'index) ; requête `workspace/symbol` « MaVue ».
- **Comportement attendu** : liste non vide ; l'entrée « MaVue » a `containerName` = « com.ex », une URI égale à la forme `Path.toUri()` du fichier, et une plage de début sur le NOM déclarant : ligne 2, caractère 13.
- **Bornes** : plage exacte (2, 13) verrouillée.
- **Statut** : actif

### moteurNonPretListeVideSansException — moteur non prêt → liste vide sans exception
- **Préconditions / entrée** : serveur construit SANS environnement (aucune racine source/classpath) ; requête `workspace/symbol` « Rien ».
- **Comportement attendu** : liste vide ; aucune exception (jamais d'erreur, toujours une réponse).
- **Bornes** : —
- **Statut** : actif

### aLArretEstAppeleAuShutdownEtALexit — rappel d'arrêt : shutdown puis exit
- **Préconditions / entrée** : serveur créé avec un rappel d'arrêt (compteur) ; cycle initialize → shutdown → exit.
- **Comportement attendu** : 0 appel du rappel après initialize ; 1 appel après shutdown (la façade est prévenue) ; 2 appels après exit (confirmation — le rappel est idempotent côté façade).
- **Bornes** : compteurs exacts 0 / 1 / 2.
- **Statut** : actif

### evenementsFichiersInvalidentLeCacheSansErreur — didChangeWatchedFiles tolérant
- **Préconditions / entrée** : fichier `com/ex/Vue.java` sur disque ; trois notifications `didChangeWatchedFiles` successives : fichier .java EXISTANT (Changed), fichier .java ABSENT (`com/ex/Absente.java`, Deleted — suppression externe), fichier NON-.java (`notes.txt`, Changed).
- **Comportement attendu** : aucune exception dans les trois cas (fichier absent toléré, non-.java filtré, existant invalide le cache).
- **Bornes** : —
- **Statut** : actif

## Notes pour la réécriture
- Les diagnostics voyagent en publication asynchrone (debounce + scheduler) : les tests attestent la publication sous 15 s par polling de 100 ms — la réécriture doit publier sans réponse bloquante côté client.
- Code de diagnostic d'import inutilisé : « ecj: » + `IProblem.UnusedImport` (dispatch ID-first, cf. JavaProblemFamilyTest) ; le tag `Unnecessary` sert au rendu estompé côté éditeur.
- `workspace/symbol` : plage = nom déclarant (pas la déclaration entière), URI = forme `Path.toUri()` ; requête vide ou moteur non prêt → liste vide, jamais d'exception.
- Le rappel d'arrêt (aLArret) est déclenché par shutdown ET exit — le registre des serveurs vivants repose dessus.
- Les tests « moteur non prêt » tournent SANS classpath : verrouiller « liste vide, jamais d'exception » plutôt qu'un échec.
