# Fiche 027 — EcjSelectionRangesTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/ecj/EcjSelectionRangesTest.java`.
> **Domaine** : navigation (fiche mixte : chaîne de sélection + cible de formatage à la frappe)
> **Résumé** : Tests du lot S2-b (moteur) : la chaîne de sélection (`selectionRange`) et la cible de formatage à la frappe (`onTypeFormatting`). La chaîne doit être STRICTEMENT croissante en longueur (chaque niveau contient strictement le précédent — contrat LSP 3.17 pour `parent`) et couvrir l'escalade complète jeton → expression → instruction → bloc → déclaration → type → unité. La cible de formatage doit tomber sur la construction qui SE TERMINE au caractère tapé (`}` → bloc/méthode, `;` → instruction). Fixture : `demo.Main` avec champ `total`, méthode `somme(int, int)` et méthode `malIndente()` au corps mal indenté.

## Comportements

### chaineStrictementCroissanteDepuisUneExpression — croissance stricte, sommet = unité
- **Préconditions / entrée** : caret au milieu du « + » de « a + b » (dans `return a + b;`).
- **Comportement attendu** : chaîne non vide ; chaque niveau est STRICTEMENT plus long que le précédent ET le contient ; le sommet démarre à 0 et couvre toute la source.
- **Bornes** : sommet = [0, longueur de la source].
- **Statut** : actif

### escaladeCompleteJusquaLaDeclaration — l'escalade traverse les niveaux attendus
- **Préconditions / entrée** : caret sur « a + b ».
- **Comportement attendu** : la chaîne contient un niveau fragment commençant par « return » (instruction), un niveau contenant « somme » (méthode/déclaration) et un niveau contenant « class Main » (type).
- **Bornes** : —
- **Statut** : actif

### jetonNomDuChampEtDeclarationComplete — jeton puis déclaration complète
- **Préconditions / entrée** : caret sur « total » dans « private int total = 0; ».
- **Comportement attendu** : le premier niveau est le jeton « total » EXACTEMENT ; le second niveau est la déclaration complète du champ (contient « private int total »).
- **Bornes** : —
- **Statut** : actif

### offsetHorsBornesOuSourceVideChaineVide — entrées invalides : chaîne vide
- **Préconditions / entrée** : offset −1 ; offset = longueur de la source ; source vide avec offset 0.
- **Comportement attendu** : chaîne VIDE dans les trois cas (jamais d'exception, jamais de plage bidon).
- **Bornes** : —
- **Statut** : actif

### sourceImparsableNeProvoquePasDException — imparsable : plages bornées, pas de crash
- **Préconditions / entrée** : source « class {{{{ », offset 3.
- **Comportement attendu** : aucune exception ; toutes les plages rendues restent dans les bornes de la source (début ≥ 0, fin ≤ 11, début < fin).
- **Bornes** : plages ⊆ [0, 11] pour cette source de 11 caractères.
- **Statut** : actif

### accoladeFermanteCibleLeBlocDeLaMethode — `}` cible le bloc de la méthode
- **Préconditions / entrée** : caractère tapé `}` à l'offset de l'accolade fermant `malIndente()`.
- **Comportement attendu** : cible non null dont le texte contient « int y=2; » (le corps mal indenté) et « malIndente » (la méthode entière).
- **Bornes** : —
- **Statut** : actif

### pointVirguleCibleLinstruction — `;` cible l'instruction
- **Préconditions / entrée** : caractère tapé `;` à la fin de « int y=2; ».
- **Comportement attendu** : cible non null contenant « int y=2; » et NE contenant PAS « return » (ne déborde pas sur l'instruction suivante).
- **Bornes** : —
- **Statut** : actif

### caractereInattenduAucuneCible — caractère inattendu : null
- **Préconditions / entrée** : un « ; » à l'offset avec déclencheur demandé `}` ; puis déclencheur hors liste (`x`).
- **Comportement attendu** : null dans les deux cas — le caractère à l'offset doit ÊTRE le déclencheur, et le déclencheur doit appartenir à la liste supportée.
- **Bornes** : —
- **Statut** : actif

## Notes pour la réécriture
- Contrat LSP 3.17 explicite : chaque plage parente contient strictement la précédente (croissance stricte de la LONGUEUR + inclusion) ; le sommet est l'unité entière.
- L'escalade passe par tous les niveaux structurels : jeton, expression, instruction, bloc, déclaration, type, unité — la fiche 027 est le contrat observable, pas la taxonomie interne.
- `plageFormattable` : le déclencheur (`}`, `;`) doit correspondre au caractère réellement présent à l'offset ; cibles = construction se terminant à ce caractère (bloc de méthode pour `}`, instruction pour `;`).
- Tolérance : source imparsable → chaîne partielle mais bornée ; offsets hors bornes / source vide → liste vide silencieuse.
