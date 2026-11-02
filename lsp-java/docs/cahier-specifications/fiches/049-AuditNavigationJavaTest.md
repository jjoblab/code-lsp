# Fiche 049 — AuditNavigationJavaTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/lsp/AuditNavigationJavaTest.java`.
> **Domaine** : navigation
> **Résumé** : Audit du panneau GO TO du LSP Java (ADR 0209) — caractérisation BOUT-EN-BOUT à travers la façade réelle `JavaTextDocumentService` : `definition` et `typeDefinition` CROSS-FICHIERS (chemin intra-fichier → PROJET → binaire décompilé), `superDefinition` (garde de parité), discipline des trois chemins et vide honnête sur document inconnu. Une cible PROJET n'atterrit JAMAIS dans le stub décompilé `jdt://decompiled/…` ; une cible BINAIRE n'atterrit jamais dans les sources. Corpus : classes du paquet `demo` — `Base` (champ `compteur`, `run()`, `doubler(int)`, statique `creer()`), `ImplA extends Base` (champ `tag`, `@Override run()`), `Consommateur` (usages croisés), `Utilitaire` + import statique `triple`, `PorteurUn`/`PorteurDeux` (méthodes homonymes `run()`/`run(int)`), `AppeleurDeuxRun`, usages binaires `HashMap`.

## Comportements

### definition_methodeInterFichiers_viseLaClasseDeclarante — méthode projet cross-fichiers
- **Préconditions / entrée** : trois fichiers ouverts (Base, ImplA, Consommateur) ; caret sur le nom `run` de `b.run()`.
- **Comportement attendu** : exactement 1 cible, dans `Base.java` (fichier non ouvert au moment de la requête), avec une plage qui démarre sur le NOM `run` de `void run()` (après le mot-clé `void`).
- **Bornes** : 1 cible exactement.
- **Statut** : actif (hypothèse `@BeforeAll` : android.jar absent → classe ignorée)

### definition_methodeHeritee_viseLeFichierDuDeclarant — méthode héritée
- **Préconditions / entrée** : caret sur `doubler` de `b.doubler(21)` (récepteur `Base`).
- **Comportement attendu** : 1 cible dans `Base.java` — le DÉCLARANT porte la cible ; plage sur le nom `doubler`.
- **Bornes** : 1 cible.
- **Statut** : actif (hypothèse android.jar présent)

### definition_champInterFichiers_viseSaDeclaration — champ cross-fichiers
- **Préconditions / entrée** : caret SUR le segment `tag` de `a.tag` (le récepteur `a` donnerait le paramètre, le point le dernier chaînon).
- **Comportement attendu** : 1 cible dans `ImplA.java`, plage sur le nom du champ `tag`.
- **Bornes** : 1 cible.
- **Statut** : actif (hypothèse android.jar présent)

### definition_referenceDeType_viseLaClasse — référence de type
- **Préconditions / entrée** : caret sur `Base` dans la signature `use(Base b, ImplA a)`.
- **Comportement attendu** : 1 cible dans `Base.java`, plage sur le nom de la CLASSE `Base` (après `class`).
- **Bornes** : 1 cible.
- **Statut** : actif (hypothèse android.jar présent)

### definition_constructeurProjet_viseLeNomDeLaClasse — invocation statique projet
- **Préconditions / entrée** : caret sur `creer` de `Base.creer()` (méthode statique projet — factory rendant un `Base`).
- **Comportement attendu** : 1 cible dans `Base.java`, plage sur le nom `creer` de sa déclaration `static Base creer()`.
- **Bornes** : 1 cible.
- **Statut** : actif (hypothèse android.jar présent)

### definition_importStatique_viseLaMethodeImportee — import statique
- **Préconditions / entrée** : `Statique.java` avec `import static demo.Utilitaire.triple;` ; caret sur `triple` de l'appel non qualifié `triple(2)`.
- **Comportement attendu** : 1 cible dans `Utilitaire.java`, plage sur le nom `triple` (le binding résout via l'import statique).
- **Bornes** : 1 cible.
- **Statut** : actif (hypothèse android.jar présent)

### definition_homonymes_deuxRun_differentParBindings — homonymes départagés
- **Préconditions / entrée** : DEUX méthodes `run` déclarées dans des fichiers distincts (`PorteurUn.run()` sans argument, `PorteurDeux.run(int)`) ; deux invocations dans le même fichier.
- **Comportement attendu** : chaque invocation vise SON déclarant avec la plage du nom dans le bon fichier — le BINDING (ici l'arité) départage les homonymes ; 1 cible par requête.
- **Bornes** : 1 cible par requête (deux requêtes).
- **Statut** : actif (hypothèse android.jar présent)

### definition_locale_viseLeFichierCourant — variable locale
- **Préconditions / entrée** : caret sur l'usage d'une variable locale `locale + 1`.
- **Comportement attendu** : 1 cible dans le FICHIER COURANT, plage sur le nom de la locale — jamais de saut cross-fichier hasardeux.
- **Bornes** : 1 cible.
- **Statut** : actif (hypothèse android.jar présent)

### definition_cibleBinaire_resteDecompilee — cible binaire
- **Préconditions / entrée** : caret sur `put` de `carte.put("a","b")` (méthode de `java.util.HashMap`, binaire).
- **Comportement attendu** : 1 cible dont l'URI démarre par `jdt://decompiled/` — le fallback décompilé sert, jamais le chemin projet.
- **Bornes** : 1 cible.
- **Statut** : actif (hypothèse android.jar présent)

### typeDefinition_variableProjet_viseLaClasse — type d'une variable projet
- **Préconditions / entrée** : caret SUR le paramètre `a` (de type `ImplA`) dans `a.tag`.
- **Comportement attendu** : 1 cible dans `ImplA.java`, plage sur le nom de la classe `ImplA` (le TYPE de la variable, pas le champ).
- **Bornes** : 1 cible.
- **Statut** : actif (hypothèse android.jar présent)

### typeDefinition_typeBinaire_resteDecompile — type binaire
- **Préconditions / entrée** : caret sur `String` (type de retour binaire `java.lang`).
- **Comportement attendu** : 1 cible dont l'URI démarre par `jdt://decompiled/java/lang/String` — jamais le chemin projet.
- **Bornes** : 1 cible.
- **Statut** : actif (hypothèse android.jar présent)

### super_surOverride_viseLaMethodeDuSupertype — super sur override
- **Préconditions / entrée** : caret dans le `run()` outrépassé de `ImplA`.
- **Comportement attendu** : 1 cible de libellé « run  ·  Base » (méthode du supertype).
- **Bornes** : 1 cible.
- **Statut** : actif (hypothèse android.jar présent)

### super_surChampDeClasseSansSuperProjet_vide — super sur champ
- **Préconditions / entrée** : caret sur le champ `tag` de `ImplA` (pas un membre outrepassant).
- **Comportement attendu** : 1 cible de libellé « Base  ·  demo » — retombée sur les supertypes DIRECTS du type en contexte (ImplA → Base).
- **Bornes** : 1 cible.
- **Statut** : actif (hypothèse android.jar présent)

### ordreDesChemins_projetAvantBinaire_binaireQuandPasProjet — discipline des chemins
- **Préconditions / entrée** : dans le MÊME flux serveur, une requête projet (`b.run()`) puis une requête binaire (`.size()` sur un `HashMap` fraîchement alloué).
- **Comportement attendu** : chaque requête rend SON régime — `b.run()` → `Base.java` (projet) ; `.size()` → URI `jdt://decompiled/…` (binaire) ; aucune fuite entre les chemins.
- **Bornes** : 1 cible par requête.
- **Statut** : actif (hypothèse android.jar présent)

### definition_documentInconnu_ouCurseurHorsSymbole_vide — vide honnête
- **Préconditions / entrée** : requête `definition` sur un document JAMAIS ouvert (URI `file:///introuvable.java`, position 0:0).
- **Comportement attendu** : résultat NON NULL avec une liste VIDE — aucune exception, jamais de cible fantôme.
- **Bornes** : —
- **Statut** : actif (hypothèse android.jar présent)

## Notes pour la réécriture
- La chaîne éprouvée : binding au caret → localisation de la classe DÉCLARANTE par convention de chemin + parse (l'overlay des tampons OUVERTS prime sur le disque) → nom borné au JETON. La cible est la plage du NOM, pas de la signature entière.
- La position EXACTE du caret est contractuelle : sur le récepteur → le paramètre ; sur le point → le dernier chaînon ; sur le segment → le membre visé.
- `superDefinition` rend des `NavigationTarget` avec libellé picker « nom  ·  propriétaire » (méthode du supertype si override, sinon le supertype direct du type en contexte).
- Les documents sont posés sur disque ET ouverts par `didOpen` : la réécriture doit reproduire l'overlay tampons>disque.
- Harnais : offsets convertis line/character côté test ; résultats récupérés de façon asynchrone (`.get()`/`.join()`).
