# ADR 0018 — Version de JDT ≥ 3.43 (Java 25) et API JDK à shimmer

- **Statut** : Proposé
- **Date** : 2026-10-28

## Contexte

La stratégie retenue conserve **ecj comme source de vérité** ( ADR 0004/0007 ) : sa montée de version devient un axe de maintenance structurant. L'ancien dépôt est à **ecj 3.40.0** patché pour ART par `:lsp:ecj-art` ( patcheur à réécrire, ADR 0001/0005 ). Le prompt ( §7 ) demande de trancher si **JDT ≥ 3.43 ( support Java 25 )** est nécessaire, et quelles API JDK shimmer.

Constats factuels du spike ( scanner `ScanArt.kt`, `resultats/scan-art-v3-ecj.txt` ) sur ecj 3.40.0 :
- 6 motifs sensibles : `MethodHandles` ( 196×, présent sur ART API 26+, usages Lookup à vérifier ), `ServiceLoader` ( 2× ), **`java.lang.Runtime$Version` ( 1× — absent d'ART, shim )**, **`InputStream.readAllBytes` ( 3× ) et `readNBytes` ( 1× ) — API 33+ ( minSdk 26 ⇒ shims )** ;
- **`StackWalker` : ABSENT des références statiques ET des chaînes** ( vérifié par balayage des constantes Utf8 ) alors que le prompt §2 le cite comme réécrit par le patcheur — la passe est soit défensive, soit vestigiale à 3.40 ( écart consigné `ALERTES.md` ).

La question « Java 25 » se pose à deux niveaux : (a) **compiler/exécuter du code Java 25 côté CodeIDE** ( l'utilisateur écrit du Java récent sur Android — ecj doit le parser/le résoudre ) ; (b) faire **tourner** ecj lui-même ( il utilise des API JDK récentes que ART n'a pas → shims ). À 3.40, ecj supporte jusqu'à Java 21 ( compliance mesurée au spike : compilation 21 OK ) ; Java 25 exige 3.39+…3.43+ ( la numérotation JDT suit les releases Eclipse ).

## Sources consultées

- Artefact mesuré : `org.eclipse.jdt:ecj 3.40.0` ( Maven Central ) — scans ART ci-dessus, banc V3 ( `resultats/v3-reels.txt` ).
- `jjoblab/codeide-lsp` @ `cb809e14` : `lsp/ecj-art` ( 2 f / 468 l. — approche tierse, ADR 0110 : réécrire ), tests `CompatShimsTest`, `docs/LSP_JAVA.md` ( §2 contraintes ART : patcheur réécrit `Runtime$Version`, `StackWalker`, `readAllBytes` ).
- Documentation Android ( niveaux d'API : `readAllBytes`/`readNBytes`/`transferTo` = 33+ ; `MethodHandles` = 26+ ; `VarHandle` = 33+ ) ; JEP 322 ( version-string ) ; javadoc JDK ( `Runtime.Version`, `StackWalker` ).
- Étude D1 ( sensibilité ART de la plateforme ) et spike §6 ( liste noire de référence du scanner ).

## Options

**Option 1 — Rester à ecj 3.40 ( Java 21 ) et piloter les shims par liste versionnée ( recommandé à court terme ).** La liste de shims devient un fichier de configuration versionné ( initiale : `Runtime$Version`, `readAllBytes`, `readNBytes` ; `StackWalker` retiré — constat 3.40 ) ; le scanner CI ( ADR 0005 ) vérifie à chaque PR que les motifs du jar entrant = la liste ( tout écart = échec ). Montée de version = PR dédiée avec re-scan + réévaluation de la liste + tests `CompatShims`.

**Option 2 — Monter à JDT 3.43+ ( Java 25 ) dès maintenant.** Gain : support du langage Java 25 ( records patterns, modules, etc. jusqu'à 25 ) pour les sources de l'utilisateur. Coûts : re-scan complet ( l'historique JDT montre des usages JDK qui évoluent — p.ex. `StackWalker` réapparaissait selon versions ), réécriture/extension des shims, re-validation fidélité ( corpus diagnostics, ADR 0016 ). **Aucun besoin utilisateur démontré à ce jour** pour Java > 21 dans CodeIDE ( la matrice de capacités ne liste pas de manque ) — la montée est prématurée sans demande.

**Option 3 — Monter à une version intermédiaire ( 3.41/3.42 ) au fil des besoins de sécurité.** Sous-cas de 2, même procédure.

**Option « ne rien changer »** : 3.40 + patcheur actuel ( à réécrire de toute façon, ADR 0001 ) — l'option 1 est le statu quo consolidé avec la liste pilotée.

## Critères et mesures

- Scans ART ( mesures ) : 3.40 = 6 motifs dont 4 shims ; liste de référence `resultats/scan-art-v3-ecj.txt`.
- Critère de besoin Java 25 : demande utilisateur / entrée matrice de capacités ( absent aujourd'hui ) ; le niveau de langage projet suit `LanguageLevel` configurable côté moteur.
- Fidélité : corpus diagnostics ( ADR 0016 ) avant/après toute montée.

## Décision proposée

**Option 1** : rester à **ecj 3.40.0** ( Java 21 ) ; liste de shims versionnée = `{ Runtime$Version, InputStream.readAllBytes, InputStream.readNBytes }` ( `StackWalker` retiré, constat factuel — **à réévaluer à chaque montée** ) ; scanner CI en garde-fou ; la montée vers 3.43+ ( Java 25 ) est **planifiée comme vague optionnelle déclenchée par besoin démontré** ( demande utilisateur, faille de sécurité, ou alignement niveau de langage d'un outil tiers ) avec procédure fixe : re-scan → liste → shims → tests fidélité/compat → appareil ( gabarit spike ).

## Conséquences

- Le support « Java 25 » n'est pas acheté aujourd'hui — position assumée et documentée dans la matrice de capacités ( niveau 21 ) ; l'utilisateur sur Android compile rarement du 25 aujourd'hui ( à vérifier par télémétrie opt-in éventuelle ).
- La réécriture du patcheur ( ADR 0001/0005 ) se fait directement en **Kotlin piloté par la liste** — plus petit, testable, diffable.
- Toute montée JDT future a un coût borné et connu ( procédure ci-dessus ) — plus de surprise de type « StackWalker inattendu ».

## Plan de migration progressive

1. Vague 1 ( avec la reprise moteur ) : patcheur Kotlin + liste versionnée + scanner CI + `CompatShimsTest` réécrit ( spec de l'ancien ).
2. Vague optionnelle ( déclenchée ) : montée JDT par étapes de version mineure, procédure fixe, drapeau `ecj.version` ( double version en cache CI le temps de la validation ).
3. Retour arrière : re-pinner l'artefact précédent ( la liste de shims versionnée suit le jar ).

## Tests de non-régression et critères de sortie

- CI : scan du jar ecj = liste ( sinon échec ) ; `CompatShimsTest` sur instrumentation ( appareil ) pour chaque shim ; corpus diagnostics : zéro variation à chaque montée.
- Critère de sortie d'une montée : fidélité identique + zéro crash ART sur appareil de test minSdk 26 + latences ≤ baseline.

## Risques

- **R1 — Faille de sécurité dans ecj 3.40 non patchée** : suivi des advisories Eclipse ; la montée ( option 2 ) est alors obligatoire — procédure prête.
- **R2 — APIs JDK nouvelles silencieuses à une montée** ( usage réflexif invisible au scanner ) : tests de fumée sur appareil ( démarrage + diagnostic + complétion ) à chaque montée, en plus du scan.
- **R3 — Besoin Java 25 urgent non anticipé** : la procédure de montée est bornée ( une vague courte ) — délai maîtrisé.
