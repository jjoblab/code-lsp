# Fiche 039 — RealClasspathDeviceReproTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/ecj/RealClasspathDeviceReproTest.java`.
> **Domaine** : diagnostics
> **Résumé** : Variante HAUTE FIDÉLITÉ du repro device : le VRAI classpath complet (export `model.json` réel du projet CodeIDE — 91 jars + boot + sourceSets) avec les conditions EXACTES du device, dont l'ordre des racines sources (racine projet EN PREMIER, puis `app/src/main/java`). Verrouille la régression off-by-one d'indexation (un type du android.jar du boot, placé en fin de liste après 2 jars écartés, doit résoudre) et l'effondrement du bruit diagnostique : un MainActivity propre avec classpath complet ne doit plus produire de mur de ~31 diagnostics. Fixture : source `MainActivity` (paquet `com.example.r`, `extends AppCompatActivity`, `setContentView(R.layout.activity_main)`) ; parsing ad hoc du model.json (regex sur `compileClasspath`, `bootClasspath`, `javaDirs`) car org.json indisponible.

## Comportements

### diagnoseWithRealFullClasspathDeviceConditions — classpath réel, ordre device
- **Préconditions / entrée** : `model.json` réel présent sur la machine (sinon test ignoré) ; parsing minimal extrait les jars existants (`.jar` de `compileClasspath` + chaînes de `bootClasspath`) et les `javaDirs` existants des sourceSets ; moteur construit avec sourceRoots = `[racine projet, app/src/main/java]` (racine projet d'abord), le classpath réel et un jdkHome JDK 17 explicite.
- **Comportement attendu** : (1) le classpath parsé est massif ; (2) sondes de résolution non nulles : `android.os.Bundle` (épreuve off-by-one — le classpath contient 93 jars dont 2 écartés à l'indexation car sans `.class`, lifecycle-viewmodel et kotlin-stdlib-common, AVANT l'android.jar du boot), `androidx.appcompat.app.AppCompatActivity` (appcompat indexé), `java.lang.Object` (via l'image jrt du jdkHome) ; (3) diagnostics de `MainActivity` : aucun message « android.os.Bundle cannot be resolved », aucun « androidx.appcompat.app.AppCompatActivity cannot be resolved », aucun « does not match the expected package » (régression FQCN avec racine projet en premier) ; (4) le bruit total s'effondre — seuls le `R` d'avant premier build (et sa cascade directe) restent tolérés.
- **Bornes** : `>= 50` jars réels filtrés (attendu ~93) ; `<= 2` diagnostics au total (le symptôme du bug était ~31) ; jdkHome `/home/z/my-project/opt/jdk-17`.
- **Statut** : actif (hypothèse visible : `model.json` réel absent — repro ignoré)

## Notes pour la réécriture
- Le test dépend de chemins machine absolus (model.json exporté, jdk-17) : prévoir un équivalent régénérable (export de modèle reconstitué) pour le banc de parité.
- L'ordre device (racine projet avant les dirs du modèle) est délibéré : le moteur DOIT rester correct dans cet ordre — la résolution FQCN par plus long préfixe est éprouvée telle quelle.
- Le reliquat autorisé est explicitement le downgrade honnête du `R` avant premier build ; tout « cannot be resolved » sur android.*/appcompat est une régression dure.
- L'index des types est interrogé via `engine.typeIndex().find(fqcn)` : sondes de non-régression utilisables telles quelles côté réécriture.
- Le parsing du modèle est tolérant (sections optionnelles, dédoublonnage des jars, filtrage des existants) — miroir du comportement attendu de la construction du classpath.
