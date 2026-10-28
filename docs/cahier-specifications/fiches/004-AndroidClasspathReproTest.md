# Fiche 004 — AndroidClasspathReproTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/ecj/AndroidClasspathReproTest.java`.
> **Domaine** : index-classpath
> **Résumé** : Reproduit la condition mobile observée sur device (log device) : un `MainActivity` de 349 caractères, classpath = android.jar + classes.jar d'appcompat extrait d'un AAR (comme `AndroidProjectClasspathResolver`), PAS de R.jar (pas de build préalable). Test de diagnostic à vocation observationnelle : il imprime tout ce qu'il observe (stdout) et n'échoue que sur les invariants forts — index binaire rempli et résolvant `AppCompatActivity` et `Bundle`, plus une sonde de complétion sur 4 positions réalistes et un discriminateur sur 3 configurations de moteur.

## Comportements

### reproDeviceScenario — repro intégrale du scénario device (index, diagnostics, complétions)
- **Préconditions / entrée** : hypothèses explicites (`Assumptions`) : cache Gradle `~/.gradle/caches/modules-2/files-2.1` présent, `android.jar` trouvé (plateformes android-34/35), AAR appcompat extractible (classes.jar). Source `SOURCE` : `MainActivity` de 349 caractères (package `com.example.d`, extends `AppCompatActivity`, `setContentView(R.layout.activity_main)`), écrite sous `<tmp>/java/com/example/d/`. Moteur construit avec sourceRoot = `<tmp>/java` et classpath = [android.jar, appcompat-classes.jar, core?, annotation?]. Sondes de complétion : A) import après le dernier point de `androidx.appcompat.app.`, B) member access `b.` sur locale `Bundle`, C) mid-word `activity_mai|x`, D) nom simple `alp` ; discriminateurs : moteur plain-vide / +jars / +sourceRoots sur un source de contrôle `s.le`.
- **Comportement attendu** : (1) l'index binaire est non vide (`hasBinarySource()`) ; (2) `find("androidx.appcompat.app.AppCompatActivity")` et `find("android.os.Bundle")` retournent non-null ; (3) `diagnose` de MainActivity retourne une liste (taille seulement imprimée — ~10 diagnostics attendus sur device, essentiellement `R` non résolu avant 1er build) ; (4) les complétions A–D et les 3 configurations discriminantes s'exécutent et s'impriment (items, trace, étapes de splicing) sans assertion dure — exceptions de complétion tolérées et affichées.
- **Bornes** : source de 349 caractères ; ~10 diagnostics attendus sur device (valeur de référence observée, NON assertée) ; aucun seuil chiffré strict.
- **Statut** : actif (ignoré si cache Gradle, android.jar ou AAR appcompat absents — hypothèses `Assumptions` visibles)

## Notes pour la réécriture
- La valeur contractuelle du test tient aux 3 invariants assertés (index non vide, `AppCompatActivity` résolu, `Bundle` résolu) ; le reste est un journal de diagnostic à valeur d'oracle pour le banc de parité.
- Le test documente la construction des splices de complétion « identiques au code de production » : marqueur `__codeide_completion__` substitué au préfixe identifiant, variantes avec `;` et, après un point, `()` — plus consommation du suffixe identifiant (`replaceEnd`).
- Positions de complétion réalistes à réutiliser : après le dernier point d'un import, member access sur locale `Bundle`, milieu d'identifiant (`activity_mai|x`), préfixe de nom simple (`alp`).
- Dépendances environnementales fortes (SDK Android + cache Gradle avec AAR androidx) — à répliquer uniquement en CI dédiée ; sinon ignoré.
- Le diagnostic d'étape (`stageDiagnosis`) vérifie que le CUD splicé contient encore des types et visite le type (compteurs cud/type par `ASTVisitor`) — diagnostic de santé interne, sans assertion.
