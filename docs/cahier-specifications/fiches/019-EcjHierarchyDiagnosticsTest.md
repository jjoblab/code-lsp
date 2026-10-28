# Fiche 019 — EcjHierarchyDiagnosticsTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/ecj/EcjHierarchyDiagnosticsTest.java` (la classe interne se nomme `JdtHierarchyDiagnosticsTest` — portage de `HierarchyDiagnosticsTest.kt` du projet tiers de référence v3.20).
> **Domaine** : diagnostics
> **Résumé** : Tests P5 (v1.4.0) : enrichissement du diagnostic « La hiérarchie du type X est incohérente ». L'erreur ecj native ne nomme que le sous-type cassé, jamais l'ancêtre réellement absent du classpath ; quand l'ancêtre manquant est le super d'un supertype BINAIRE (classe présente dans un jar dont la propre superclasse a été retirée), le moteur doit replier le FQN manquant et son exigeant : « … — absent du classpath : lib.A (requis par lib.B) ». Fixture : `lib.A` et `lib.B` (`B extends A`) compilés par le javac du JVM de test, puis seuls les `.class` choisis sont placés dans le jar du classpath ; le test se skippe proprement sur un JRE sans compilateur système.

## Comportements

### ancetreBinaireManquantEstNomme — l'ancêtre binaire manquant est nommé dans le message
- **Préconditions / entrée** : jar de classpath contenant UNIQUEMENT `lib/B.class` (A absent) ; source « package app; class C extends lib.B {} » ; diagnose du moteur (racines sources + jar, « 17 »).
- **Comportement attendu** : un diagnostic porte un message contenant à la fois « lib.A » et « requis par lib.B » ; ce message enrichi mentionne « classpath » et conserve le message d'origine nommant « C » (préfixe préservé, indépendamment de la Locale du problème ecj).
- **Bornes** : —
- **Statut** : actif — skip conditionnel visible (hypothèse `Assumptions.assumeTrue(javac != null)` : JRE sans javac → ignoré)

### hierarchieCompleteAucunEnrichissement — hiérarchie saine : zéro enrichissement, zéro erreur
- **Préconditions / entrée** : jar COMPLET (`lib/A.class` + `lib/B.class`) ; même source « class C extends lib.B ».
- **Comportement attendu** : aucun message ne contient « classpath » (aucun enrichissement) et aucun diagnostic de sévérité ERROR n'est publié.
- **Bornes** : 0 erreur.
- **Statut** : actif — skip conditionnel visible (hypothèse javac présent)

### supertypeSourceInconnuNePlantePas — supertype source inconnu : best-effort sans crash
- **Préconditions / entrée** : classpath de jars vide ; source « package app; class C extends Inconnu {} » (supertype SOURCE inconnu).
- **Comportement attendu** : le diagnose retourne toujours un lot non null, non vide ; au moins une erreur de sévérité ERROR est publiée (« cannot be resolved » d'ecj) ; l'enrichissement peut être absent — jamais de crash ni de perte du lot.
- **Bornes** : —
- **Statut** : actif

## Notes pour la réécriture
- L'enrichissement ne s'applique qu'au cas « ancêtre BINAIRE manquant » : le message natif ecj (type cassé nommé) doit rester présent en préfixe, le suffixe français ajoute « absent du classpath : <FQN> (requis par <exigeant>) ».
- Garde anti-fuite essentielle : une hiérarchie saine ne doit produire AUCUN message contenant « classpath ».
- Le chemin « supertype source inconnu » est best-effort : l'erreur de résolution ecj doit rester publiée, l'enrichissement est optionnel.
- La fixture compile A/B avec le javac système puis fabrique des jars sélectifs (B seul / complet) — pattern utile pour le banc de parité.
- Anomalie de nommage : le fichier s'appelle `EcjHierarchyDiagnosticsTest.java` mais la classe déclarée est `JdtHierarchyDiagnosticsTest`.
