# Fiche 041 — SourceFocuserTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/ecj/SourceFocuserTest.java`.
> **Domaine** : caches-perf
> **Résumé** : Valide `SourceFocuser` (technique de focalisation du source, à la java-language-server) : réduire un gros source en effaçant les CORPS des méthodes hors du caret (remplacés par un bloc vide `{}`) pour accélérer les opérations de binding, tout en préservant intégralement la méthode contenant l'offset et les SIGNATURES de toutes les méthodes (résolution de surcharges/héritage). Éprouve aussi la robustesse de l'appariement d'accolades face aux strings/commentaires, le non-focal hors méthode, et la traduction inverse `mapBack` (positions réduites → originales), y compris à l'intérieur des jetons insérés. Fixtures : classes de 3 méthodes de 200–400 lignes générées (sources > 2 000 caractères).

## Comportements

### shortSourceIsReturnedUnchanged — sources courtes intactes
- **Préconditions / entrée** : un source court (classe à une méthode, une ligne).
- **Comportement attendu** : `focus` rend le source INCHANGÉ (aucune réduction sous le seuil).
- **Bornes** : seuil de réduction : 2 000 caractères.
- **Statut** : actif

### methodBodiesOutsideOffsetAreErased — corps hors portée effacés
- **Préconditions / entrée** : classe de 3 méthodes de 200 lignes chacune (source > 2 000 caractères) ; offset dans la 2e méthode, sur le littéral `"here"`.
- **Comportement attendu** : la méthode focalisée (m2) reste INTÉGRALE (on y retrouve `"here"`) ; les corps de m1 et m3 sont effacés (plus de `a150 = 150` ni `c150 = 150`) ; les SIGNATURES `void m1()`, `void m2()`, `void m3()` restent présentes.
- **Bornes** : source > 2 000 caractères ; 200 lignes par méthode.
- **Statut** : actif

### stringsAndCommentsDoNotConfuseMatcher — accolades en string/commentaire
- **Préconditions / entrée** : m1 contient un string avec un `{` échappé et un commentaire contenant un `{` ; méthodes de 300 lignes ; offset dans m2 (`"target"`).
- **Comportement attendu** : m2 préservée ; m1 effacée malgré les accolades parasites (l'appariement ignore strings et commentaires).
- **Bornes** : source > 2 000 caractères ; 300 lignes par méthode.
- **Statut** : actif

### offsetOutsideMethodDoesNotReduce — offset hors méthode
- **Préconditions / entrée** : offset au niveau CLASSE (sur un champ), source long (méthode de 400 lignes).
- **Comportement attendu** : la source est rendue INCHANGÉE (pas de méthode focalisable → pas de réduction).
- **Bornes** : —
- **Statut** : actif

### mapBackUnreducedSourceIsIdentity — identité sans réduction
- **Préconditions / entrée** : source court (non réduite) via `focusAndMap`.
- **Comportement attendu** : `mapBack` est l'identité : position 10 → 10 ; fin de source → fin de source.
- **Bornes** : —
- **Statut** : actif

### mapBackTranslatesPositionsAfterErasedBodies — traduction après effacements
- **Préconditions / entrée** : fixture réduite (m2 focalisée, m1/m3 effacées).
- **Comportement attendu** : une position AVANT la première insertion est identique ; la position de `void m3()` dans la source réduite (décalée vers la gauche) est restituée à sa position ORIGINALE ; la fin de la source réduite correspond à la fin originale.
- **Bornes** : —
- **Statut** : actif

### mapBackInsideInsertedTokenMapsToResumePosition — position dans le jeton inséré
- **Préconditions / entrée** : le corps de m1 est remplacé par le jeton inséré `{}` ; la position du `}` de ce jeton est soumise à `mapBack`.
- **Comportement attendu** : elle mappe sur la position de REPRISE — juste après le `}` fermant du corps original de m1 (fermeture + 1).
- **Bornes** : —
- **Statut** : actif

### mapBackRoundTripsTheCaretOffset — aller-retour du caret
- **Préconditions / entrée** : même fixture réduite ; l'offset focalisé conservé par l'objet `Focused`.
- **Comportement attendu** : `mapBack(offset())` restitue EXACTEMENT l'offset original.
- **Bornes** : —
- **Statut** : actif

## Notes pour la réécriture
- Le couple `focus`/`focusAndMap` définit une surface nette : `Focused { source(), offset(), mapBack(int) }` — la réécriture doit garantir l'aller-retour du caret ET la cohérence des positions après effacements.
- L'insertion est un bloc vide `{}` (pas une suppression pure) : une position dans ce jeton a une image définie (reprise après le corps original), pas un trou.
- L'appariement d'accolades doit être insensible aux accolades dans strings et commentaires — cas à conserver dans le banc de parité.
- Les signatures préservées servent la résolution de surcharges et d'héritage : ne pas effacer les listes de paramètres.
- Seuil observable : 2 000 caractères (en-deçà : source rendue telle quelle) ; offset hors de toute méthode : aucune réduction.
