# Fiche 017 — EcjFormatterTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/ecj/EcjFormatterTest.java`.
> **Domaine** : formatage
> **Résumé** : Tests du formateur Eclipse JDT in-process (`EcjFormatter`) : style Eclipse par défaut (indentation 4 espaces, espacement des opérateurs, repli à 120 colonnes) et style Google (2 espaces), idempotence, syntaxe Java 17 (record, bloc texte), formatage de plage, reformatage des commentaires/javadoc, et contrats de sûreté (imparsable, source vide). Tourne sur JVM desktop avec les jars Eclipse PATCHÉS (tâche `patchEclipseForArt`) : il valide donc AUSSI que le formateur charge et s'exécute au travers des shims. Fixture principale : classe `demo.Main` volontairement mal formatée (`MESSY`).

## Comportements

### formatsIndentationAndSpacing — style Eclipse : espacement et indentation 4
- **Préconditions / entrée** : formateur « 17 », style par défaut (Eclipse) ; source `MESSY` (classe publique tassée, affectations sans espaces).
- **Comportement attendu** : le résultat contient « public class Main { » (accolade de classe espacée), « private int counter = 0; » (espaces autour de `=`), « public String greet(String name) { » (signature espacée), « "Hello, " + name » (espaces autour de `+`) ; corps de classe indenté de 4 espaces (« \n    private int counter ») et corps de méthode de 8 (« \n        String message »).
- **Bornes** : indentation 4 (niveau classe) / 8 (niveau corps de méthode).
- **Statut** : actif

### googleStyleUsesTwoSpaces — style Google : indentation 2
- **Préconditions / entrée** : formateur « 17 », style `GOOGLE` ; même source `MESSY`.
- **Comportement attendu** : corps de classe indenté de 2 espaces (« \n  private int counter ») et corps de méthode de 4 (« \n    String message »).
- **Bornes** : indentation 2 (classe) / 4 (méthode).
- **Statut** : actif

### formattedOutputIsStable — idempotence du formatage
- **Préconditions / entrée** : formateur « 17 » (Eclipse).
- **Comportement attendu** : formater deux fois la source donne le même texte que de la formater une fois (`format(format(s)) == format(s)`).
- **Bornes** : —
- **Statut** : actif

### formatsModernSyntax — records Java 17 formatables
- **Préconditions / entrée** : source avec un `record` compact (« public record Point(int x,int y){…} »).
- **Comportement attendu** : résultat non null ; l'en-tête du record est espacé : « public record Point(int x, int y) { ».
- **Bornes** : —
- **Statut** : actif

### formatRangeOnlyTouchesRange — le formatage de plage n'atteint que la plage
- **Préconditions / entrée** : classe avec « int a = 1; » (déjà indenté) et « int b = 2; » (non indenté) ; plage = uniquement « int b = 2; ».
- **Comportement attendu** : la ligne hors plage reste intacte («     int a = 1; ») ; la ligne dans la plage est indentée («     int b = 2; »).
- **Bornes** : —
- **Statut** : actif

### unparseableSourceIsNeverMangled — imparsable : null ou inchangé
- **Préconditions / entrée** : source « class {{{{ nope » (irrécupérable).
- **Comportement attendu** : `format` retourne null OU la source exactement inchangée — jamais une version déformée.
- **Bornes** : —
- **Statut** : actif

### emptySourceIsReturnedAsIs — source vide retournée telle quelle
- **Préconditions / entrée** : source « » (chaîne vide).
- **Comportement attendu** : `format("")` retourne exactement « ».
- **Bornes** : —
- **Statut** : actif

### formatsCommentsAndJavadoc — la javadoc est reformattée
- **Préconditions / entrée** : source contenant « /**Javadoc    mal   espacée.*/ » et un bloc de commentaire.
- **Comportement attendu** : la javadoc mal espacée n'apparaît PLUS telle quelle dans le résultat (les commentaires sont inclus dans le formatage — comportement « F_INCLUDE_COMMENTS »).
- **Bornes** : —
- **Statut** : actif

### lineWrappingRespectsWidth — repli de ligne à la largeur configurée
- **Préconditions / entrée** : une déclaration très longue (appel `java.util.Arrays.asList` avec 14 chaînes), mise en forme initiale correcte mais sur une seule ligne.
- **Comportement attendu** : après formatage (style Eclipse, largeur 120), AUCUNE ligne du résultat ne dépasse la limite vérifiée par le test.
- **Bornes** : chaque ligne ≤ 130 caractères (largeur cible Eclipse 120, marge de vérification 130).
- **Statut** : actif

## Notes pour la réécriture
- Deux styles exposés au minimum : Eclipse (4 espaces, largeur 120) et Google (2 espaces) — le style est un paramètre du formateur.
- Les tests s'exécutent avec les jars Eclipse patchés par la tâche `patchEclipseForArt` : le formateur s'exécute in-process via des shims (`ToolFactory.createCodeFormatter` → `DefaultCodeFormatter` → Scanner ecJ réécrit) ; la réécriture Kotlin devra offrir un équivalent exécutable sur Android/ART.
- Invariants de sûreté à reprendre tels quels : imparsable → null ou inchangé ; vide → vide ; idempotence.
- Les assertions sont substring-based (robustes à la mise en page exacte) : le cahier ne fixe PAS le texte intégral de sortie, seulement des invariants d'espacement/indentation.
