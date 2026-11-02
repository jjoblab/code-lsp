# Fiche 024 — EcjLocateCacheTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/ecj/EcjLocateCacheTest.java`.
> **Domaine** : caches-perf
> **Résumé** : Tests du cache de localisation fqcn → jar (P1 v1.3.0, portage de `locateCache` de `JdtEnvironmentCache.kt`, projet tiers de référence v3.20) intégré à la recherche binaire de l'environnement de compilation : positif mémorisé (deux `find` successifs d'un FQCN présent dans un jar répondent tous deux), négatif mémorisé (un FQCN absent ne re-sonde pas les jars à chaque appel — lookup O(1), gain dominant sur les gros classpaths), types imbriqués via le chemin `$` sans exception, et invalidation par `dispose()`. Le jar de test est fabriqué à partir d'un vrai `.class` copié depuis les sorties de compilation du module (entrée ZIP valide pour la lecture de fichier de classe). Le test accède aux membres package-private `find`/`dispose` par RÉFLEXION.

## Comportements

### positifMemoriseDeuxFindSuccessifs — positif mémorisé
- **Préconditions / entrée** : environnement avec 1 jar contenant l'entrée réelle `jo/lspjava/ecj/EcjLiveTemplates.class` ; deux appels `find("jo.lspjava.ecj.EcjLiveTemplates")` successifs.
- **Comportement attendu** : les DEUX appels retournent une réponse non null (le second emprunte le cache, même verdict).
- **Bornes** : —
- **Statut** : actif

### negatifMemoriseAbsentDesJars — négatif mémorisé (O(1))
- **Préconditions / entrée** : FQCN « com.inexistant.Classe » absent des jars et du jrt de test ; deux appels successifs.
- **Comportement attendu** : les deux appels retournent null — après le premier échec, le négatif est mémorisé et le second lookup est immédiat.
- **Bornes** : —
- **Statut** : actif

### typeImbriqueLocaliseViaEnglobant — chemin `$` robuste
- **Préconditions / entrée** : `find("jo.lspjava.ecj.PasLa$Inner")` (englobant absent) puis `find("jo.lspjava.ecj.EcjLiveTemplates$Inner")` (englobant présent mais sans entrée imbriquée dans le jar témoin).
- **Comportement attendu** : AUCUNE exception ; verdict null cohérent dans les deux cas — le chemin `$` retombe sur l'englobant sans crash.
- **Bornes** : —
- **Statut** : actif

### disposeVideLeCache — dispose invalide le cache
- **Préconditions / entrée** : un négatif mémorisé (« com.inexistant.ApresDispose »), puis `dispose()`, puis re-`find`.
- **Comportement attendu** : après dispose, le `find` reste sûr et retourne null (les structures sont vides ; le négatif mémorisé ne survit pas au changement de classpath).
- **Bornes** : —
- **Statut** : actif

### encodageUtf8DuFichierSource — garde-fou d'encodage
- **Préconditions / entrée** : lecture du fichier du test lui-même en UTF-8.
- **Comportement attendu** : le contenu contient « localisation » (le fichier source est bien encodé UTF-8).
- **Bornes** : —
- **Statut** : actif

## Notes pour la réécriture
- Le cache doit mémoriser les positifs ET les négatifs : c'est le négatif qui porte le gain de perf (ecj résout des centaines de types absents par frappe).
- `dispose()` doit tout vider : un négatif mémorisé ne doit jamais survivre à un changement de classpath.
- Le test n'explore pas de cas POSITIF d'imbriqué réellement localisé (le jar témoin n'en contient pas) : seule la robustesse null-sans-exception du chemin `$` est verrouillée — le banc de parité pourra compléter.
- Couplage de fixture : le jar est construit depuis `build/classes/java/main` du module (avec repli depuis la racine du module) — à remplacer en Kotlin par n'importe quel `.class` réel.
- L'accès réflexif à `find`/`dispose` (membres package-private) trahit un contrat d'API non public : la réécriture devrait exposer un point d'entrée testable propre.
