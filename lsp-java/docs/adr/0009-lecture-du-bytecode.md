# ADR 0009 — Lecture du bytecode, AAR, android.jar

- **Statut** : Proposé
- **Date** : 2026-10-28

## Contexte

§5-D6 compare la lecture IntelliJ du bytecode ( Cls/stubs `.class`/décompilation ) avec `AnalyseurClassFile` / `:lsp:index` du statu quo. Faits :

- **IntelliJ** : `.class` → stubs via **ASM** ( `org.jetbrains.intellij.deps:asm-all:9.6.1`, package ombré ) — `StubBuildingVisitor` 898 l. + `FirstPassData` + `SignatureParsing`, `SKIP_CODE`/`SKIP_FRAMES` ; classes internes lues dans les fichiers frères `Outer$Inner.class` ( hack documenté `ClassFileStubBuilder:58-69` ) ; paquet `compiled/` = 56 f / 9 694 l. ; 6 fichiers importent `javax.swing` ( Icon ) ; **pas de support AAR/android.jar** ( plugin Android hors périmètre ) ; décompilation = rendu texte approximatif + miroir PSI re-parsé ( navigation/quick-doc, pas fidélité ).
- **Statu quo** : `ClassFileStubBuilder.java` maison + `RawZipReader` + CIDX par jar — le pipeline existe déjà, sans swing, en Kotlin.
- Le moteur actuel consomme android.jar/AAR via le protocole tooling ( `ClasspathResult` ) — chaîne maison éprouvée.

## Sources consultées

- `JetBrains/intellij-community` @ `865067b0` (lecture seule) : `java/java-psi-impl` `psi/impl/compiled/*` ( comptages 56 f / 9 694 l. ), `ClassFileStubBuilder.java`, `StubBuildingVisitor` [hors sparse, lu via git cat-file].
- `jjoblab/codeide-lsp` @ `cb809e14` : `lsp/index` ( `ClassFileStubBuilder`, `RawZipReader` ), `lsp/java` ( `AnalyseurClassFile` équivalent ), ADR 0111.
- Documentation ASM ( BSD-3, javadoc ) ; spécification JVM ( format class ).

## Options

**Option 1 — Statu quo maison ( réutiliser le pipeline actuel ), en s'inspirant de deux détails IntelliJ.** (a) le hack documenté des classes internes frères ( `Outer$Inner.class` ) si le statu quo ne le couvre pas déjà — **à vérifier** dans le code repris ; (b) `SKIP_CODE`/`SKIP_FRAMES` comme rappel de performance si l'analyse actuelle lit plus que nécessaire.

**Option 2 — Remplacer par ASM ( dépendance `asm-all` ombré JetBrains ).** ASM est robuste et éprouvé ; mais le lecteur maison fonctionne, est déjà ART-clean et évite 372 Ko + un shading ; rejeté sauf preuve d'un écart de fidélité ( signatures génériques, records, annotations ) — le banc de fidélité ( ADR 0016 ) tranche.

**Option 3 — Porter le miroir décompilé-source IntelliJ ( navigation « voir la source des libs » ).** Rendu approximatif par construction ( bannière « Implementation of methods is not available » ) + re-parse PSI — superflu sur mobile : la quick-doc par signature ( statu quo ) couvre le besoin à coût minimal. Rejeté ( D6 ).

**Option 4 — Ne rien changer.** = Option 1 sans les deux emprunts ; acceptable si le code repris couvre déjà les classes internes.

## Critères et mesures

- D6 : pipeline ASM→stub décrit avec tailles ; décompilation qualifiée ( « usage navigation/quick-doc, pas fidélité » ).
- Matrice de capacités : quick-doc par signature = couvert ( statu quo ).
- Fidélité des stubs .class : corpus de jars réels ( records, sealed, enums complexes, nested ) — banc ADR 0016.

## Décision proposée

**Option 1** : conserver le lecteur de bytecode maison ( reprise contrôlée ADR 0001/0002 ) ; vérifier/ajouter la couverture des classes internes frères ( seul emprunt de design retenu, avec doc ) ; pas d'ASM en dépendance ; pas de décompilateur. AAR et android.jar restent consommés via le protocole tooling existant ( `ClasspathResult` ) — aucune brique IntelliJ ne les couvre ( D6 ).

## Conséquences

- Zéro dépendance nouvelle ; zéro swing ( contrairement au paquet `compiled/` IntelliJ ) ; la dette éventuelle se concentre sur la fidélité des signatures ( à surveiller par le banc ).
- La quick-doc des bibliothèques reste « par signature » — position assumée ( écart vs IntelliJ, documenté dans la matrice ).

## Plan de migration progressive

1. Reprise du module index ( comprend le lecteur ) — ADR 0002.
2. Vague « fidélité stubs » : corpus de classes modernes, corrections ciblées si écart, banc de non-régression.
3. Retour arrière : n/a ( pas de changement de moteur ).

## Tests de non-régression et critères de sortie

- Corpus stubs : 100 % des classes publiques de jars témoins ( guava, fastutil, ecj lui-même ) résolues avec membres/signatures exacts ; classes internes `Outer$Inner` couvertes.
- Temps d'indexation par jar ≤ baseline ( 85–89 ms/jar mesuré ADR 0111 ancien ).

## Risques

- **R1 — Écarts de fidélité sur les constructs récents** ( records, sealed, patterns ) : banc de fidélité + montée ASM seulement si écart démontré.
- **R2 — Classes internes non couvertes** : à vérifier dès la reprise ( spécification extraite des tests existants ).
