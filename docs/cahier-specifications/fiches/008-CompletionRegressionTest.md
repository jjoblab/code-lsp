# Fiche 008 — CompletionRegressionTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/ecj/CompletionRegressionTest.java`.
> **Domaine** : completion
> **Résumé** : Non-régression des six symptômes exacts du rapport device : (1) « dès que je tape un point "import android." plus aucune suggestion » — la complétion d'import après le point doit proposer les sous-paquets ; (2) « cela devrait être fluide incrémental » — chaque frappe affine avec les matches par préfixe dominants ; (3) « private String... rien du tout » — le marqueur en position de TYPE doit être accepté par le recovery ; (4) taper « imp » propose « import » ; (5) référence de paquet hors import (contexte PACKAGE_REFERENCE) ; (6) la tempête de retries du scan jrt ne doit pas revenir (cache négatif après un seul essai). Classpath = android.jar seul (pas de cache Gradle).

## Comportements

### importCompletionAfterDotProposesSubPackages — sous-paquets après `import android.os.`
- **Préconditions / entrée** : source d'un `MainActivity` avec `import android.os.` (Bundle retiré) ; caret juste après le dernier point.
- **Comportement attendu** : items non null ; « Bundle » (type direct du paquet) ET « Build » (sous-paquet `android.os.Build`) présents ; au moins un item parmi Bundle/Build/StrictMode — plus d'un item (l'ancien crash de subPackages ne laissait qu'un seul item).
- **Bornes** : > 1 item.
- **Statut** : actif (ignoré si android.jar absent — hypothèse en @BeforeAll)

### importCompletionAfterAndroidDot — avalanche de sous-paquets après `import android.`
- **Préconditions / entrée** : `import android.|` (caret après le point).
- **Comportement attendu** : sous-paquets « app », « widget », « view » présents (dominants sur les seuls types directs R/Manifest) ; plus de 10 items au total.
- **Bornes** : > 10 items.
- **Statut** : actif (hypothèse android.jar)

### importCompletionRefinesIncrementally — affinage incrémental du préfixe
- **Préconditions / entrée** : préfixes successifs `""`, `Te`, `Tex`, `Text` après `import android.widget.` (4 complétions successives).
- **Comportement attendu** : préfixe vide → liste non vide ; sinon le PREMIER item matche le préfixe tapé PAR PRÉFIXE (insensible à la casse) et chaque item matche par préfixe OU par sous-chaîne ; pour « Tex », « TextView » présent. Matching gradué (parité PrefixMatcher du projet tiers de référence) : prefix / prefix-insensible-casse / camel-hump / substring ≥ 3 caractères — les matches par préfixe dominent TOUJOURS le classement.
- **Bornes** : substring autorisée seulement ≥ 3 caractères ; bonus de classement −200 pour un match par préfixe (vs 0) — le premier item matche toujours par préfixe.
- **Statut** : actif (hypothèse android.jar)

### typeCompletionOnFieldDeclaration — type sur déclaration de champ dans une classe existante
- **Préconditions / entrée** : `private Str|` tapé au milieu d'une classe existante (champ `existing` plus haut, `onCreate` plus bas — le cas device).
- **Comportement attendu** : items non null ; « String » et « StringBuilder » présents.
- **Bornes** : —
- **Statut** : actif (hypothèse android.jar)

### typeCompletionOnFieldDeclarationBareClass — recovery sur classe non terminée
- **Préconditions / entrée** : source `public class MainActivity {\n    private Str` (classe NON fermée, caret en fin de fichier).
- **Comportement attendu** : items non null ; « String » présent (pire cas de recovery).
- **Bornes** : —
- **Statut** : actif (hypothèse android.jar)

### keywordCompletionByPrefix — mots-clés rendus à préfixe non vide
- **Préconditions / entrée** : `imp|` tapé dans un corps de méthode.
- **Comportement attendu** : « import » présent dans les propositions.
- **Bornes** : —
- **Statut** : actif (hypothèse android.jar)

### packageCompletionOutsideImport — référence de paquet hors import
- **Préconditions / entrée** : `Object o = java.util.Array|` dans un corps de méthode (qualifier non-type hors import).
- **Comportement attendu** : items non null ; « ArrayList » présent ; au moins un item parmi Arrays/ArrayList (types du paquet `java.util`).
- **Bornes** : —
- **Statut** : actif (hypothèse android.jar)

### jrtScanDoesNotRetryOnFailure — cache négatif du scan jrt
- **Préconditions / entrée** : JDK factice avec `lib/modules` BIDON (3 octets) et `jrt-fs.jar` VALIDE copié du runtime (le provider charge mais le montage échoue) ; 500 appels `isPackage("com.nonexistent.z"+i)` sur le typeIndex du moteur (via réflexion).
- **Comportement attendu** : durée totale des 500 appels < 1500 ms — un seul essai payé (~100 ms de chargement du provider) puis cache négatif O(1) pour les 499 suivants ; sans cache, chaque appel re-tenterait (URLClassLoader + ServiceLoader).
- **Bornes** : 500 appels ; < 1500 ms ; premier essai ~100 ms.
- **Statut** : actif (ignoré si `jrt-fs.jar` absent du runtime — hypothèse `Assumptions`)

## Notes pour la réécriture
- Le contrat de CLASSEMENT (préfixe avant sous-chaîne, bonus −200) est aussi important que la présence des items — la réécriture doit garantir un ordre où le premier item matche toujours par préfixe.
- La borne < 1500 ms pour 500 `isPackage` verrouille l'absence de re-tentatives après échec du montage jrt — mémoriser l'échec après UN seul essai.
- La complétion s'appelle `complete(chemin, source, caret[, trace])` sur `EcjCompletionEngine` adossé au moteur ; chaque cas réécrit `Main.java` sur disque avant l'appel.
- L'ensemble de la classe (hors test jrt) est ignoré sans android.jar — hypothèse en @BeforeAll ; le test jrt est indépendant du SDK mais exige `jrt-fs.jar` dans le runtime.
- Les sources de contrôle « S1-like » (`s.le`) montrent que le moteur vide suffit pour la complétion member-access simple — utile pour les tests unitaires de la réécriture.
