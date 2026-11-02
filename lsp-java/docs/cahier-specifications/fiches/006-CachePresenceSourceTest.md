# Fiche 006 — CachePresenceSourceTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/ecj/CachePresenceSourceTest.java`.
> **Domaine** : caches-perf
> **Résumé** : Cache négatif de présence source (v1.9.1) : la résolution ecj appelle `find(fqcn)` des CENTAINES de fois par passe, chaque appel sondant le disque (`Files.isRegularFile` par racine) — sur stockage FUSE Android, 0,5–5 ms par stat × centaines d'appels × 3–5 splices dominait la latence de complétion. Le négatif mémorise « ce FQCN n'est PAS une source du projet » pendant 30 s ; la création d'un `.java` via `didChangeWatchedFiles` (Created) invalide immédiatement. Deux comportements verrouillés : la fenêtre de masquage du négatif et la stabilité de lecture des sources existantes (positifs jamais cachés). Nécessite android.jar (plateformes android-37.2) sinon classe ignorée.

## Comportements

### classeCreeeApresNegatifDevientVisibleApresInvalidation — négatif TTL puis invalidation par événement Created
- **Préconditions / entrée** : moteur sur racine source `app/src/main/java` avec package `com.example.c` (hypothèse android.jar en @BeforeAll) ; (1) `find("com.example.c.Nouvelle")` sur classe absente → négatif mémorisé ; (2) écriture de `Nouvelle.java` sur disque (nouvelle classe hors éditeur) ; (3) `find` SANS invalidation ; (4) `engine.invalidateSourcePresence()` puis `find`.
- **Comportement attendu** : (1) null ; (3) null — le négatif TTL MASQUE encore la création dans sa fenêtre (comportement documenté et verrouillé) ; (4) non-null — la classe résout immédiatement après invalidation, sans attendre l'expiration du TTL.
- **Bornes** : TTL du négatif = 30 s (documenté en commentaire de classe ; non mesuré par le test).
- **Statut** : actif (ignoré si android.jar absent — hypothèse en @BeforeAll)

### sourceExistanteResteToujoursLisible — les positifs ne sont jamais cachés
- **Préconditions / entrée** : `Stable.java` écrit dans le package ; 3 appels successifs de `find("com.example.c.Stable")`.
- **Comportement attendu** : non-null à CHAQUE appel (le contenu suit le disque ; seuls les négatifs sont cachés).
- **Bornes** : 3 lectures successives.
- **Statut** : actif (ignoré si android.jar absent)

## Notes pour la réécriture
- Le test fige comme CONTRACTUELLE la fenêtre de masquage : une classe créée sur disque reste invisible à `find` tant que le négatif n'a pas expiré ou été invalidé — la réécriture doit assumer ce délai (ou l'amoindrir), mais l'invalidation par événement Created reste obligatoire.
- Seuls les négatifs sont cachés (TTL) ; toute lecture positive touche le disque — invariant implicite à préserver.
- La motivation chiffrée (0,5–5 ms par stat FUSE × centaines d'appels × 3–5 splices) est documentée dans le test mais non assertée : à garder comme cible de conception.
- android.jar est une dépendance d'environnement (candidats `/home/z/android-sdk/platforms/android-37.2/android.jar`, `/home/z/tools/android-sdk/...`) alors que le comportement du cache en est indépendant — la réécriture pourra tester ce cache sans SDK.
