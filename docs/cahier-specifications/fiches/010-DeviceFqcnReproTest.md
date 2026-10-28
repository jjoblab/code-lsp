# Fiche 010 — DeviceFqcnReproTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/ecj/DeviceFqcnReproTest.java`.
> **Domaine** : index-classpath
> **Résumé** : Repro des conditions EXACTES du log device : sur mobile, l'environnement était construit avec sourceDirs = [workingDir (racine projet), app/src/main/java] — LA RACINE PROJET EN PREMIER. Or `fqcnFor()` itère les racines DANS L'ORDRE : la racine projet matche tout fichier du projet AVANT le vrai source set → FQCN segmenté par les slashes (`app.src.main.java.com.example.r.MainActivity` au lieu de `com.example.r.MainActivity`), d'où 31–32 diagnostics sur un MainActivity de 349 caractères (22 sur la ligne de déclaration) et « got 0 items » en complétion. Le test compare l'ordre device contre l'ordre corrigé et n'échoue que sur deux invariants forts.

## Comportements

### reproDeviceSourceRootOrdering — ordre des racines sources et FQCN longest-match
- **Préconditions / entrée** : hypothèses explicites : cache Gradle présent, android.jar présent (plateformes 34/35), AAR appcompat extractible. Projet factice `<tmp>/Rifk` avec `app/src/main/java/com/example/r/MainActivity.java` (SOURCE modèle device : 349 caractères, classe déclarée ligne 6) et `settings.gradle` à la racine (la racine « ressemble à un projet »). Deux moteurs : device (racines [projectRoot, srcMain]) et fixed (racines [srcMain]), même classpath [android.jar, appcompat(+core)]. Diagnostics des deux moteurs sur la même source ; complétions member `b.` et nom `alp` dans les deux configurations.
- **Comportement attendu** : (1) `fixedDiags.size() <= deviceDiags.size()` — l'ordre corrigé produit strictement moins ou autant de bruit ; (2) AUCUN diagnostic du moteur device ne contient « does not match the expected package » (le calcul FQCN longest-match ne doit plus régressé, même dans l'ordre historique racine-projet-d'abord) ; tout le reste (verdict d'écart, complétions) est imprimé sans assertion dure.
- **Bornes** : source de 349 caractères ; symptômes device de référence : 31–32 diagnostics (22 sur la ligne de déclaration de classe), complétion 0 item (valeurs observées sur device, NON assertées par le test).
- **Statut** : actif (ignoré si cache Gradle / android.jar / AAR appcompat absents — hypothèses `Assumptions` visibles)

## Notes pour la réécriture
- L'invariant central : le calcul du FQCN depuis les racines sources doit matcher le PLUS LONG préfixe de package (le vrai source set) — jamais la première racine par ordre d'itération ; l'ordre des racines ne doit plus être signifiant.
- Le bug historique : `createConnectionProvider` ajoutait la racine projet (workingDir) AVANT `app/src/main/java` — la réécriture doit soit trier les racines, soit faire un longest-match.
- Structure factice à répliquer : `settings.gradle` à la racine rend la racine projet « plausible » pour reproduire le mauvais match.
- Test majoritairement observationnel (stdout : diagnostics avec lignes, complétions avec trace) — sa valeur contractuelle est dans les deux invariants assertés ; utile comme oracle d'écart (device-order vs fixed-order) pour le banc de parité.
- Les complétions member/nom sont exécutées dans les DEUX configurations mais sans seuil — aucune exigence d'items ici.
