# Fiche C-05 — Contrôle NewApi : appel sous le minSdk

> **Source** : `[DÉRIVÉ DES SOURCES]` — `02-reference-android-studio.md` §B13
> (mécanisme du détecteur et base `api-versions.xml` vérifiés dans le code public
> et l'artefact SDK). Comportement décrit pour lui-même.
> **Domaine** : diagnostics / intégration Android
> **Résumé** : tout appel de méthode/constructeur/champ dont le niveau d'API requis
> (base `api-versions`) est supérieur au `minSdk` du module produit un
> avertissement NewApi — sauf échappatoires locales (`@RequiresApi`, garde
> `SDK_INT`, dossier `values-vNN` pour les ressources).

## Comportements

### N1 — appel simple sous minSdk
- **Préconditions / entrée** : module `minSdk 21` ; source
  `view.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);` (API 17+ — OK) puis
  `context.registerDeviceIdCertificate(...)` (API 33).
- **Comportement attendu** : le second appel produit un NewApi (sévérité LSP
  **Warning** par défaut — voir ADR 0024) à la plage de l'appel, provenance
  `lint` ; le premier n'en produit pas.
- **Bornes** : `compileSdk` implicite via android.jar du classpath ; `minSdk`
  dérivé du disque (ADR 0020) ou du modèle sync.

### N2 — échappatoire @RequiresApi
- **Entrée** : méthode annotée `@RequiresApi(33)` contenant l'appel API 33.
- **Attendu** : pas de NewApi **à l'intérieur** ; l'appelant non annoté de cette
  méthode reçoit le NewApi (propagation de contrainte locale, profondeur 1 en v1).

### N3 — échappatoire garde SDK_INT
- **Entrée** : `if (Build.VERSION.SDK_INT >= 33) { appel33(); }`.
- **Attendu** : pas de NewApi dans le bloc ; l'appel **hors** garde produit le
  diagnostic.

### N4 — InlinedApi
- **Entrée** : référence à une **constante inlinée** introduite après minSdk.
- **Attendu** : avertissement distinct (InlinedApi) — la constante existe à
  l'exécution mais sa valeur peut différer.

### N5 — fix proposé
- **Attendu** : quick-fix « entourer d'une garde `if (Build.VERSION.SDK_INT >= N)` »
  (ModCommand, multi-lignes à la plage de l'instruction englobante) et fix
  « ajouter `@RequiresApi(N)` » sur la méthode englobante.

## Notes pour la réécriture
- La base est **téléchargée/compilée par script** (segment CIDX ~1,7 Mo cible) ;
  si indisponible hors ligne : contrôles désactivés **annoncés** (jamais de silence
  faux).
- [Captures demandées au propriétaire] : 20 positions NewApi sur les corpus réels
  pour comparaison qualitative avec Android Studio (protocole 06).
