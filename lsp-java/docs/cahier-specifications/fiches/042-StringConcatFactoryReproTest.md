# Fiche 042 — StringConcatFactoryReproTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/ecj/StringConcatFactoryReproTest.java`.
> **Domaine** : diagnostics
> **Résumé** : Repro du constat production v0.107.0 : « The type java.lang.invoke.StringConcatFactory cannot be resolved » (faux positif ERROR — la classe n'existe dans AUCUNE plateforme Android, vérifié API 31 et 36 ; D8 désugare toujours les concaténations invokedynamic). Le mécanisme est reproduit EXACTEMENT : un `.class` binaire de fixture (`batisse.Biblio`, fabriqué par ASM en bytecode Java 8) DÉCLARÉ `extends java.lang.invoke.StringConcatFactory`, référencé par la source éditée (`p.A extends batisse.Biblio`) — le binaire du classpath référence le type absent, d'où le message natif « indirectly referenced from required .class files ». Le correctif verrouillé : un stub EMBARQUÉ des intrinsèques du compilateur (StringConcatFactory, ObjectMethods, SwitchBootstraps), repli APRÈS sources/jars/jrt, uniquement pour ces FQCN — plateforme et jrt réels restent prioritaires.

## Comportements

### sansJrtLeFauxPositifNeReapparaitPas — garde de non-régression (le repro historique)
- **Préconditions / entrée** : android.jar API < 33 (SANS la classe — plateforme 31), jar de fixture biblio.jar, compliance « 17 », jdkHome factice (répertoire SANS `lib/modules` : pas d'image jrt) ; source éditée `p.A extends batisse.Biblio`.
- **Comportement attendu** : le diagnostic de `A.java` ne contient AUCUN message mentionnant « StringConcatFactory » (le faux positif ne doit JAMAIS réapparaître sans jrt — il était rouge avant v0.108.0, commit de référence du constat).
- **Bornes** : —
- **Statut** : actif (hypothèse : android.jar API < 33 introuvable → ignoré)

### contratDesStubsIntrinseques — liste fermée et octets valides
- **Préconditions / entrée** : FQCN sondés : `java.lang.String`, `java.util.List` (types de plateforme), `java.lang.invoke.StringConcatFactory`, `java.lang.runtime.ObjectMethods`, `java.lang.runtime.SwitchBootstraps` (intrinsèques).
- **Comportement attendu** : `estIntrinseque` est FAUX pour les types de plateforme et VRAI pour exactement les 3 intrinsèques ; `octets("java.lang.invoke.StringConcatFactory")` est non nul et démarre par l'en-tête de classe compilée magic exact, version Java 8 ; `octets("java.lang.String")` rend null (jamais de stub d'un type de plateforme).
- **Bornes** : magic `CA FE BA BE` (octets 0–3) ; versions mineure et majeure poids fort à 0 ; version majeure 52 (= Java 8).
- **Statut** : actif

### temoinAvecJrtLeDiagnosticDisparait — témoin jrt sain
- **Préconditions / entrée** : MÊME classpath (android.jar API < 33 + biblio.jar) mais jdkHome = JDK réel avec image jrt complète.
- **Comportement attendu** : aucun diagnostic « StringConcatFactory » — la classe résout via le jrt, la hiérarchie de `Biblio` se referme naturellement (preuve que le mécanisme jrt est sain : l'appareil qui voit l'erreur souffre du non-chargement de son jrt ; le stub est un filet structurel, pas un remplacement).
- **Bornes** : —
- **Statut** : actif (hypothèses : android.jar API < 33 ET JDK avec image jrt présents)

### stubEmbarqueLeFauxPositifDisparait — correctif par stub
- **Préconditions / entrée** : exactement le contexte du repro (android.jar API < 33, biblio.jar, compliance 17, jdkHome factice sans jrt).
- **Comportement attendu** : le stub embarqué referme la hiérarchie binaire : AUCUN diagnostic contenant « StringConcatFactory ».
- **Bornes** : —
- **Statut** : actif (hypothèse : android.jar API < 33 introuvable → ignoré)

### api36NonPlusSansJrt — plateforme récente aussi
- **Préconditions / entrée** : android.jar API 36 (ne contient PAS non plus la classe — seul `StringConcatException` y figure) + biblio.jar + jdkHome factice.
- **Comportement attendu** : aucun diagnostic « StringConcatFactory » — le correctif ferme le binding MÊME sur la plateforme la plus récente sans jrt : le stub est structurel, pas transitoire.
- **Bornes** : —
- **Statut** : actif (hypothèse : android.jar API 36 introuvable → ignoré)

## Notes pour la réécriture
- Ordre de repli observé : sources → jars → image jrt → stub des intrinsèques, le stub ne masquant JAMAIS un android.jar qui contiendrait la classe ni un jdkHome chargé.
- La fixture ASM inclut volontairement un constructeur public sans argument : sans lui, ecj produirait « Implicit super constructor Biblio() is undefined » — bruit de fixture sans rapport avec le mécanisme.
- « Faux JDK » = répertoire sans `lib/modules` (jrtAvailable faux, échec silencieux de jrt-fs) — conditions appareil lorsque l'image jrt du toolchain n'a pas chargé.
- Le javadoc documente le mécanisme production : sur l'appareil du propriétaire, la référence venait du code compilé par le serveur Gradle (javac target ≥ 9 : chaque concat laisse un `CONSTANT_MethodHandle` vers `makeConcatWithConstants` dans le pool) relu par le moteur pour binder les types du projet.
- Candidats android.jar : plateformes 31 (dossiers « android-31-bis »/« android-12 », bug d'étiquetage du zip Google) et 36 — chemins machine explicites, à paramétrer pour le banc de parité.
