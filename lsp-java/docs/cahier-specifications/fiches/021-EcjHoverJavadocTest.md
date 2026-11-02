# Fiche 021 — EcjHoverJavadocTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/ecj/EcjHoverJavadocTest.java`.
> **Domaine** : hover-inlay
> **Résumé** : Tests du survol RICHE (lot S2-a) : le hover porte la signature du symbole au caret PLUS la javadoc de sa déclaration quand elle vit dans la MÊME unité (une référence vue avant ou après sa déclaration la retrouve via le registre des déclarations du scan). Les symboles binaires (classpath, jrt) n'ont pas de javadoc ici — documentation nulle, signature seule (périmètre « honnête »). Verrouille aussi le nettoyeur javadoc→markdown (`EcjJavadocMarkdown`) en unitaire. Fixture : `demo.Main` avec javadoc sur la classe (« Un compteur d'essais. »), le champ `total` (« Le total courant. ») et la méthode `somme(int, int)` (javadoc complète avec `@param`, `@return`, `{@code}`, `<p>`).

## Comportements

### referenceMethodeAvantArriereRetrouveLeJavadoc — référence retrouve la javadoc de la déclaration
- **Préconditions / entrée** : caret sur « somme(total » (appel DANS un autre corps, déclaration ailleurs dans le même fichier).
- **Comportement attendu** : survol riche non null ; la signature contient « somme » ; la documentation contient « Additionne deux valeurs » (première ligne), « `a` et `b` » (`{@code}` converti en code span markdown), « @param a la première valeur » (balises de bloc conservées verbatim), et « valeurs.\n\nLes deux » (`<p>` devient un saut de paragraphe markdown, i.e. ligne vide).
- **Bornes** : —
- **Statut** : actif

### referenceChampRetrouveSonJavadoc — référence de champ retrouve sa javadoc
- **Préconditions / entrée** : caret sur « total » dans « somme(total, total) ».
- **Comportement attendu** : signature contenant « total » ; documentation contenant « Le total courant ».
- **Bornes** : —
- **Statut** : actif

### declarationClasseRetrouveSonJavadoc — déclaration de type retrouve sa javadoc
- **Préconditions / entrée** : caret sur le nom « Main » de la déclaration de classe.
- **Comportement attendu** : signature contenant « Main » ; documentation contenant « Un compteur d'essais ».
- **Bornes** : —
- **Statut** : actif

### methodeSansJavadocDocumentationNulle — sans javadoc : signature seule
- **Préconditions / entrée** : caret sur « pasDeDoc » (méthode sans javadoc).
- **Comportement attendu** : survol riche servi avec signature non null mais documentation NULL.
- **Bornes** : —
- **Statut** : actif

### referenceBinaireSansDocumentation — symbole binaire : pas de javadoc
- **Préconditions / entrée** : caret sur « length » dans « "abc".length() ».
- **Comportement attendu** : survol servi, signature contenant « length » ; documentation NULL (périmètre limité au même fichier — les binaires n'apportent pas de javadoc ici).
- **Bornes** : —
- **Statut** : actif

### hoverAtResteSousCloturesSansDocumentation — hoverAt garde son contrat historique
- **Préconditions / entrée** : caret sur « somme(total » ; appel de `hoverAt` (API historique).
- **Comportement attendu** : le texte commence par « ```java\n » et se termine par « \n``` » (clôtures markdown) ; il ne contient PAS la documentation (« Additionne » absent) — `hoverAt` ne porte jamais la javadoc.
- **Bornes** : —
- **Statut** : actif

### nettoyeurCasLimites — cas limites du nettoyeur javadoc
- **Préconditions / entrée** : `EcjJavadocMarkdown.nettoyer` sur : null, «    » (blancs), « /***/ », « /**x*/ », « /** x */ ».
- **Comportement attendu** : null pour null/blancs/commentaire vide (survol signature seule) ; « x » pour les deux derniers.
- **Bornes** : —
- **Statut** : actif

### nettoyeurAsterisquesDeMarge — astérisques de marge retirés
- **Préconditions / entrée** : javadoc multi-lignes classique (« /**\n * Ligne une.\n * Ligne deux.\n */ »).
- **Comportement attendu** : « Ligne une.\nLigne deux. » (les astérisques de marge et les marqueurs /** */ disparaissent).
- **Bornes** : —
- **Statut** : actif

### nettoyeurBalisesInline — conversion des balises inline et de saut
- **Préconditions / entrée** : javadocs avec `{@code Foo}`, `{@link Foo#bar()}`, `{@linkplain Foo}`, `{@literal abc}`, `A<p>B`, `A<br>B`, `A<br/>B`.
- **Comportement attendu** : `{@code}`/`{@link}`/`{@linkplain}` → code span markdown (« Voir `Foo`. », « Voir `Foo.bar()`. », « Voir `Foo`. ») ; `{@literal abc}` → « abc » (verbatim) ; `<p>` → « A\n\nB » (paragraphe) ; `<br>` et `<br/>` → « A\nB » (saut de ligne).
- **Bornes** : —
- **Statut** : actif

## Notes pour la réécriture
- Deux API distinctes à prévoir : survol riche (signature + documentation structurées) et hover texte (clôtures ```java, jamais de documentation) — compatibilité ascendante verrouillée.
- Le registre des déclarations du scan rend l'ordre fichier indifférent (référence avant/après déclaration) ; périmètre = même unité de compilation uniquement.
- Le nettoyeur javadoc a une sémantique de conversion précise : inline → code span, `<p>` → ligne vide, `<br>`/`<br/>` → saut de ligne simple, balises de bloc (`@param`…) conservées verbatim.
- Null vs chaîne : documentation null (pas de javadoc) est différent d'une documentation vide — le test le distingue explicitement.
