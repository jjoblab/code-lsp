# Fiche 057 — JavaS2ConfortTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/lsp/JavaS2ConfortTest.java`.
> **Domaine** : formatage
> **Résumé** : Lot S2-a « confort structurel » : formatage de SÉLECTION (`rangeFormatting` — capacité déclarée, éditions bornées à la plage demandée, code hors plage jamais touché, garde lifecycle) et survol riche (`hover` — signature seule → PLAINTEXT ; signature + javadoc → MARKDOWN ; la forme MarkedString-string dépréciée est proscrite). Domaine formatage car majoritaire (3 tests sur 5) ; les 2 tests de survol relèvent de hover-inlay. Fixtures : classe `T` à deux méthodes mal indentées (`a()` hors sélection, `b()` ciblée) et classe `H` avec javadoc (`somme` documentée, `doubleSomme` qui l'appelle, `sansDoc` sans doc). Serveur réel SANS classpath — aucun android.jar requis.

## Comportements

### capaciteRangeFormattingDeclaree — capacité rangeFormatting annoncée
- **Préconditions / entrée** : création des capabilities du serveur.
- **Comportement attendu** : `documentRangeFormattingProvider` annoncé en forme booléenne `TRUE` (côté gauche du Either).
- **Bornes** : —
- **Statut** : actif

### formatageDeSelectionBornesALaPlage — formatage de sélection borné à la plage
- **Préconditions / entrée** : `didOpen` de `T.java` (deux méthodes mal indentées) ; requête `rangeFormatting` sur la plage couvrant `void b()` jusqu'à l'accolade fermante incluse, avec `FormattingOptions` par défaut.
- **Comportement attendu** : éditions non-null et non vides ; AUCUNE édition ne démarre avant la ligne de `b()` ; après application des éditions sur la source originale : « int y = 2; » (méthode `b`, dans la plage) est formatée et « int x=1; » (méthode `a`, hors plage) reste inchangée.
- **Bornes** : réponse en 5 s max ; toutes les éditions commencent à une ligne ≥ ligne de `b()`.
- **Statut** : actif

### rangeFormattingAvantInitialisationRejetee — garde lifecycle sur rangeFormatting
- **Préconditions / entrée** : serveur NON initialisé (jamais de `initialized`) ; requête `rangeFormatting` sur un document fictif (plage 0:0–0:5).
- **Comportement attendu** : rejet par `ResponseErrorException` (garde I2 — le service refuse toute requête avant initialized).
- **Bornes** : —
- **Statut** : actif

### hoverSignatureSeuleEnPlaintext — hover signature seule en PLAINTEXT
- **Préconditions / entrée** : `didOpen` de `H1.java` (source avec javadoc) ; requête `hover` sur « sansDoc » (méthode sans javadoc).
- **Comportement attendu** : hover non-null ; le contenu est une `MarkupContent` (jamais la forme MarkedString-string dépréciée) de kind `PLAINTEXT` ; la valeur contient « sansDoc » (la signature nomme la méthode) et ne contient PAS de clôture « ``` ».
- **Bornes** : réponse en 5 s max.
- **Statut** : actif

### hoverAvecJavadocEnMarkdown — hover signature + javadoc en MARKDOWN
- **Préconditions / entrée** : `didOpen` de `H2.java` ; requête `hover` sur l'appel « somme(1, 2) » (la déclaration de `somme` porte une javadoc ailleurs dans le fichier).
- **Comportement attendu** : hover non-null ; `MarkupContent` de kind `MARKDOWN` ; la valeur COMMENCE par « ```java\n » (bloc de code pour la signature) et contient le texte de la javadoc « Additionne deux valeurs ».
- **Bornes** : réponse en 5 s max ; préfixe exact « ```java\n ».
- **Statut** : actif

## Notes pour la réécriture
- Règle hover : PLAINTEXT quand seule la signature est disponible ; MARKDOWN dès qu'une javadoc existe, avec la signature en bloc de code « ```java » puis la javadoc.
- `rangeFormatting` : les éditions portent des positions de la source ORIGINALE ; le code hors plage ne doit jamais être modifié — le test le vérifie par reconstruction du texte complet.
- La garde lifecycle I2 s'applique à `rangeFormatting` comme aux autres requêtes : rejet avant `initialized`.
- La capacité `documentRangeFormattingProvider` est annoncée en forme booléenne (pas en options détaillées).
- Les deux survols utilisent la même fixture javadoc : `somme(int a, int b)` documentée « Additionne deux valeurs. », appelée depuis `doubleSomme()`.
