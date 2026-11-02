# Fiche 033 — LatenceCompletionBenchmark

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/ecj/LatenceCompletionBenchmark.java`.
> **Domaine** : caches-perf
> **Résumé** : BANC de mesure (pas un test d'assertion) : latence de complétion À CHAUD dans le scénario du retour propriétaire « les suggestions de complétion sont devenues lentes ». Corpus réaliste de module : DEUX racines sources (`app/src/main/java` et `app/src/generated/java`, paquet `com.example.monip`), 12 classes `Helper0..11` (chacune `valeurN()`/`nomN()`), un record `Modele(String titre, int poids)`, une classe générée `ActivityMainBinding` (inflate + champ `TextView titre`), la classe focale `MainActivity`, et un classpath android.jar + R.jar (helper `PopupSpontaneReproTestHelper`). Trois scénarios mesurés : complétion de MEMBRE (`binding.titre.|`), complétion de TYPE (`TextVi|`) et le pire cas du popup spontané (préfixe VIDE après espace). Protocole : 1 échauffement (index/caches) puis 10 mesures à chaud en nanoTime, converties en millisecondes et imprimées ; AUCUN seuil n'est asserté — le banc produit des nombres à comparer entre versions.

## Comportements

### latenceCompletionMembre — mesure : complétion de membres chaînée
- **Préconditions / entrée** : source de `MainActivity` avec imports `android.os.Bundle`/`android.widget.TextView`, binding inflate, `binding.titre.setText(modele.titre())`, sommes `h0.valeur0() + h1.valeur1()`, et caret juste après « binding.titre. » (accès membre sur champ d'une classe générée de l'AUTRE racine source). 1 appel d'échauffement puis 10 mesures à chaud.
- **Comportement attendu** : la complétion répond à chaque itération ; les 10 latences (ms) sont imprimées ainsi que le nombre d'items de la première mesure — AUCUNE assertion de seuil : le banc documente la latence, il ne la valide pas.
- **Bornes** : aucune borne assertée (10 mesures à chaud, échauffement 1 appel) ; protocole : latence en ms par `System.nanoTime`.
- **Statut** : actif — ignoré si android.jar absent (hypothèse visible `Assumptions.assumeTrue` dans le @BeforeAll)

### latenceCompletionType — mesure : complétion de type par préfixe
- **Préconditions / entrée** : source minimale avec « TextVi| » dans un corps de méthode (préfixe de type à résoudre contre android.jar). Même protocole : échauffement puis 10 mesures.
- **Comportement attendu** : 10 latences (ms) imprimées + nombre d'items de la première mesure ; aucune assertion.
- **Bornes** : aucune borne assertée (10 mesures à chaud).
- **Statut** : actif — ignoré si android.jar absent (hypothèse visible)

### latenceCompletionVide — mesure : préfixe vide (pire cas popup spontané)
- **Préconditions / entrée** : source avec « int x = 5; » puis un espace et le caret à préfixe VIDE — le pire cas du popup spontané (plus grand espace de candidats). Échauffement puis 10 mesures.
- **Comportement attendu** : 10 latences (ms) imprimées ; aucune assertion.
- **Bornes** : aucune borne assertée (10 mesures à chaud).
- **Statut** : actif — ignoré si android.jar absent (hypothèse visible)

## Notes pour la réécriture
- Le banc ne fixe AUCUN seuil : sa valeur est la comparaison avant/après (ancien moteur vs réécriture Kotlin) sur un corpus reproductible — le banc de parité (ADR 0016) devra définir les seuils manquants.
- Corpus à répliquer exactement pour la comparabilité : 2 racines sources (java + generated), 12 Helpers + 1 record + 1 binding généré + MainActivity, classpath android.jar + R.jar, conformité « 17 ».
- Trois profils à suivre : membre (navigation de type profonde), type (résolution android.jar), vide (espace de candidats maximal) — les latences à chaud, après un échauffement unique.
- Dépendance environnementale : android.jar à chemins fixes (`/home/z/android-sdk/platforms/android-37.2/android.jar` ou `/home/z/tools/…`) ; absent → banc ignoré (skip visible).
- Une NOUVELLE instance de `EcjCompletionEngine` est créée à CHAQUE mesure (le moteur/l'environnement reste chaud, pas le complétion-engine) — détail qui compte pour interpréter les nombres.
