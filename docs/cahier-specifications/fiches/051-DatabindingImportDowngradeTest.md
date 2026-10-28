# Fiche 051 — DatabindingImportDowngradeTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/lsp/DatabindingImportDowngradeTest.java`.
> **Domaine** : diagnostics
> **Résumé** : Repro du constat production v0.107.0 : « The import com.example.monip.databinding cannot be resolved » en ERROR sec sur un projet Data Binding sans build préalable. Les classes `XxxBinding` ne sont produites par AGP qu'AU BUILD (la sync ne les génère pas) : avant tout build, l'import est STRUCTURELLEMENT non résoluble — l'ERROR est exacte mais incurable côté utilisateur. Le correctif verrouillé : au package Data Binding, le MÊME downgrade honnête que la classe R — ERROR→HINT avec remède (« lance un Build ») — avec COUPURE honnête : dès que la première classe générée existe, une faute de frappe redevient une ERROR intégrale. Vrai flux LSP4J : serveur in-process + `LanguageClient` espion collectant les `publishDiagnostics` (le downgrade vit dans la PUBLICATION, pas dans le moteur).

## Comportements

### importDatabindingSansBuildEstHintAvecRemede — downgrade sans build
- **Préconditions / entrée** : serveur initialisé (rootUri + workspace folder) sur une racine SANS classes générées ; ouverture d'un `MainActivity.java` important `com.example.e.databinding.MainActivityBinding`.
- **Comportement attendu** : le diagnostic de l'import non résolu est déclassé en sévérité HINT ; son message EXPLIQUE le remède (contient « Data Binding ») et nomme l'action (contient « Build » — classes générées par AGP lors de la construction).
- **Bornes** : — (le diagnostic est attendu dans les 15 s de polling).
- **Statut** : actif (hypothèse `@BeforeEach` : android.jar absent — API 31/36 — → test ignoré)

### fauteDeFrappeDatabindingAvecGeneresResteError — coupure honnête
- **Préconditions / entrée** : dossier `com/example/e/databinding` PEUPLÉ (situation post-build : `MainActivityBinding.java` existant) ; import d'une classe databinding INEXISTANTE (`PasLaBinding`).
- **Comportement attendu** : le diagnostic garde la sévérité ERROR intégrale — le downgrade ne masque JAMAIS une vraie erreur.
- **Bornes** : —
- **Statut** : actif (hypothèse android.jar présent)

### paquetHomonymeNeDeclenchePasLeDowngrade — garde du segment
- **Préconditions / entrée** : import `com.example.e.mydatabinding.Machin` (paquet homonyme SANS être un segment `.databinding`).
- **Comportement attendu** : sévérité ERROR intacte — seul un VRAI segment `.databinding` (points des deux côtés) déclenche le downgrade.
- **Bornes** : —
- **Statut** : actif (hypothèse android.jar présent)

## Notes pour la réécriture
- La reconnaissance se fait par les ARGUMENTS STRUCTURÉS du problème ecj (arguments()[0] = nom importé, stable entre locales) — jamais par le message localisé : à préserver strictement en Kotlin.
- La coupure honnête sonde l'existence de classes générées (sources générées ou binaires du module) : présence → plus AUCUN downgrade sur ce paquet.
- Harnais : espion `LanguageClient` ne collectant que `publishDiagnostics` (le reste ignoré) ; polling 100 ms jusqu'à 15 s car le flux est debounced (50 ms) + asynchrone (scheduler) et plusieurs publications peuvent arriver.
- Cycle complet par test : initialize (rootUri, workspaceFolders, processId 0) → initialized → didOpen → observation → shutdown/exit (best-effort).
- Candidats android.jar : plateformes 31 (dossiers « android-31-bis »/« android-12 », bug d'étiquetage Google) et 36 — chemins machine explicites à paramétrer.
