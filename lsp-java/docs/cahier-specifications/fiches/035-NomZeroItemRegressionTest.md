# Fiche 035 — NomZeroItemRegressionTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/ecj/NomZeroItemRegressionTest.java`.
> **Domaine** : completion
> **Résumé** : Non-régression du symptôme « got 0 items » de la complétion NOM (étude §4.2/§4.3, lot F étape 3) : les contextes NON capturés par conception faisaient rendre `null` par `complete`, dont l'itération jetait une NPE avalée — le banc observait « 0 items ». Le contrat verrouillé : **jamais null** — ces positions rendent une liste VIDE honnête, tandis que les positions travaillées du même corpus continuent de rendre leurs items. Fixture : jar compilé au vol par `javax.tools` (aucune dépendance android.jar — les tests tournent partout) contenant `alpha.Alpha` (champ statique `COMPTEUR = 3` + champ d'instance), `alpha.AlphaVoisin`, `alpha.sous.Profond` et `beta.Beta` ; moteur compliance « 17 » avec racine `projet/exemple`.

## Comportements

### nomDeDeclarationRendVidePasNull — nom de paramètre non capturé
- **Préconditions / entrée** : source `Essai.java` avec le marqueur en position de NOM de paramètre : `void autre(int Bund` (caret en fin du mot tapé).
- **Comportement attendu** : `complete` rend une liste NON NULL (vide honnête — non capture par conception, parité projet tiers de référence « ContextAnalyzer ne capture pas les noms de déclarations ») ; ni erreur ni crash.
- **Bornes** : —
- **Statut** : actif (hypothèse `@BeforeAll` : JDK avec compilateur système, sinon classe ignorée)

### annotationEnRecoveryRendVidePasNull — annotation rejetée en recovery
- **Préconditions / entrée** : `@Overri` tapé dans un corps de méthode (annotation-statement : syntaxe Java invalide, jetée en recovery par l'analyseur).
- **Comportement attendu** : `complete` rend une liste NON NULL (vide honnête).
- **Bornes** : —
- **Statut** : actif (hypothèse `@BeforeAll` : JDK avec compilateur système)

### apresTableauRendVidePasNull — marqueur après crochets de tableau
- **Préconditions / entrée** : `Alp[]` — le marqueur est posé juste après les crochets du type tableau.
- **Comportement attendu** : `complete` rend une liste NON NULL (vide honnête).
- **Bornes** : —
- **Statut** : actif (hypothèse `@BeforeAll` : JDK avec compilateur système)

### typeEnParametrePropose — position TYPE de paramètre (travaillée)
- **Préconditions / entrée** : `void autre(Bund` — le marqueur est en position de TYPE de paramètre.
- **Comportement attendu** : `complete` rend des items NON VIDES.
- **Bornes** : —
- **Statut** : actif (hypothèse `@BeforeAll` : JDK avec compilateur système)

### nomEnInstructionProposeTypesDuJar — scénario NOM nominal
- **Préconditions / entrée** : `Alp` au début d'une instruction (dans le corps de `methode()`), jar fixture au classpath.
- **Comportement attendu** : les labels contiennent `Alpha` ET `AlphaVoisin` (types du jar du même paquet racine).
- **Bornes** : —
- **Statut** : actif (hypothèse `@BeforeAll` : JDK avec compilateur système)

### typeDansGeneriquePropose — type dans un générique
- **Préconditions / entrée** : `java.util.List<Alp` — marqueur en argument de type.
- **Comportement attendu** : les labels contiennent `Alpha`.
- **Bornes** : —
- **Statut** : actif (hypothèse `@BeforeAll` : JDK avec compilateur système)

## Notes pour la réécriture
- Le contrat central est « jamais null » : tout contexte non compris rend une liste vide — l'ancien comportement (null → NPE avalie côté appelant → « 0 items » observable) est le bug corrigé.
- Le harnais écrit le source sur disque puis calcule le caret comme longueur du préfixe + longueur du corps tapé : la position exacte du marqueur est contractuelle.
- La fixture jar est auto-contenue (compilation `javax.tools` + packaging `JarOutputStream` avec manifeste) : transposable telle quelle en Kotlin pour le banc de parité.
- Distinction clef du corpus : position de NOM (`void autre(int Bund|`) = vide honnête, position de TYPE (`void autre(Bund|`) = items — la frontière type/nom est le comportement verrouillé.
