# ADR 0005 — Packaging et compatibilité ART (jars, dexage, shims, version de plateforme, dépôts Maven)

- **Statut** : Proposé
- **Date** : 2026-10-28

## Contexte

La stratégie hybride (ADR 0004) consomme des artefacts binaires tiers qu'il faut dexer vers ART dans l'APK de CodeIDE (minSdk 26 d'après `AGENTS.md` de CodeIDE) : ecj 3.40.0 **patché** (statu quo, patcheur à réécrire — ADR 0001), `org.jetbrains:java-syntax 0.3.381` (nouveau, V1 du spike), et les dépendances existantes (lsp4j, gson, kotlinx…). Le spike a produit la matière factuelle du packaging :

- **V1 java-syntax : 0 référence sensible ART** (scanner `ScanArt.kt`, `resultats/scan-art-v1.txt`), formats JVM 8/17, 798 Ko, 2 965 méthodes déclarées. Les variantes JVM des modules KMP vivent dans des artefacts séparés (`java-syntax-jvm`, etc.) liés par `available-at` du `.module` Gradle — piège de résolution documenté.
- **V3 ecj 3.40.0 non patché : 6 motifs sensibles**, dont 4 exigent des shims : `java.lang.Runtime$Version` (1×), `InputStream.readAllBytes` (3×), `InputStream.readNBytes` (1×) — et `StackWalker` **absent des références** à 3.40 (écart vs prompt §2, `ALERTES.md`) ; `MethodHandles` (196×, présent sur ART API 26+) et `ServiceLoader` (2×) à vérifier à l'exécution.
- **V2 (232.4652) : 108 motifs sensibles** — disqualifié (ADR 0004).
- Le patcheur actuel `lsp/ecj-art` (exclu R9.3, approche reprise d'un tiers) réécrit `Runtime$Version`, `StackWalker`, `InputStream.readAllBytes` au build par ASM : **à réécrire** depuis la javadoc JDK/JEP 322 et la doc ASM (procédé déjà appliqué au « niveau A » v0.94.0).

## Sources consultées

- `JetBrains/intellij-community` @ `865067b0` (lecture seule) : `LICENSE.txt` (Apache-2.0) ; `java/java-syntax` (autonomie, D3).
- Artefacts mesurés (spike §6) : `java-syntax 0.3.381` et modules KMP ( intellij-dependencies via cache-redirector ) ; `ecj 3.40.0` ( Maven Central ) ; `com.jetbrains.intellij.{platform,java}:232.4652` ( packages.jetbrains.team/maven/p/ij/intellij-dependencies — vérifié accessible ) ; guava/streamex/kotlin/coroutines/annotations ( Central ).
- `jjoblab/codeide-lsp` @ `cb809e14` : `lsp/ecj-art` (2 f / 468 l., approche tierce — ADR 0110), `settings.gradle.kts` (canal cache-redirector déjà en place pour `:lsp:kotlin`), `docs/THIRD_PARTY_NOTICES.md`.
- `jjoblab/CodeIDE` @ `54e99c6` : `AGENTS.md` (minSdk 26, targetSdk 28), `docs/STRATEGIE_VERIFICATION.md` (CI porte le lourd).
- Documentation Android (android.jar API 34, comportement ART des API 26+/33+) ; JVMS §4 (format des constant pools, scanner maison).

## Options

**Option 1 — Statu quo packaging + java-syntax en ajout net.** Garder le mécanisme actuel (patcheur build-time réécrit + graphes ecj EPL-2.0), ajouter `java-syntax-jvm` comme dépendance directe (0 shim). Dexage vérifié en CI (d8 + comptage de méthodes, conformément au prompt §6.a qui place le dexage en CI).

**Option 2 — Conteneur « moteur » séparé (module Gradle dédié, artefact unique).** Empaqueter le moteur (ecj patché + java-syntax + CIDX) en un artefact `code-lsp-moteur` publié, dexé une fois en CI avec budget de méthodes suivi comme métrique de non-régression (ADR 0016).

**Option 3 — Runtimes émulés / substitution complète des API sensibles ( voile dexage complet ).** Réécrire toutes les API sensibles via des shims injectés au dexage ( style patcheur généralisé ). Rejetée : multiplier les shims multiplie les risques de divergence silencieuse ; le scanner prouve que V1 n'en a pas besoin et que V3 n'en exige que 4.

**Option 4 — Ne rien changer** (ne pas ajouter java-syntax). Compatible avec la stratégie B mais abandonne la vague « parsing incrémental » ; listée pour mémoire.

## Critères et mesures

- Scans ART : `resultats/scan-art-{v1,v2-minimale,v2,v3-ecj}.txt` ( scanner `ScanArt.kt`, méthodes et limites documentées ).
- Cibles d'empreinte proposées ( spike §7 ) : APK ajouté < 5 Mo, méthodes dex ajoutées < 30 000, démarrage à froid < 1,5 s.
- Fait KMP : les `.module` Gradle pointent les variants `-jvm` par `available-at` — un import Gradle metadata correct ramène automatiquement les 4 jars ; en résolution plate (comme le spike), les nommer explicitement.

## Décision proposée

**Option 1 (immédiat) évoluant vers Option 2 (vague 0/1)** : java-syntax entre comme dépendance nette ( zéro shim, ~0,8 Mo, ~3 000 méthodes déclarées ) ; le patcheur ecj-art est **réécrit en Kotlin** (R2) avec une liste de shims pilotée par un fichier de configuration versionné — liste initiale : `Runtime$Version`, `readAllBytes`, `readNBytes` (+ `StackWalker` retiré de la liste active, constat 3.40, à réévaluer à chaque montée — ADR 0018) ; le dexage D8 + comptage de méthodes dex devient une **tâche CI obligatoire** (seuils d'échec : +5 Mo APK, +30 000 méthodes) ; minSdk reste 26 (VarHandle et `java.lang.foreign` restent hors du périmètre retenu : V1 n'y touchent pas, V2 rejeté).

## Conséquences

- Le canal d'approvisionnement JetBrains (cache-redirector → intellij-dependencies) devient une dépendance de build officielle du nouveau dépôt — conditions d'utilisation consignées en ADR 0017 ; épinglez les versions ( pas de résolution dynamique ).
- La liste de shims versionnée crée un point de vérité unique, testable en CI ( présence/absence des motifs dans les jars entrants — le scanner `ScanArt.kt` du spike est la base de ce garde-fou ).
- L'ajout de `java-syntax` ne doit **pas** élever minSdk ni introduire `META-INF/services` dépendant de `ServiceLoader` (vérification CI).

## Plan de migration progressive

1. Vague 0 : module `:moteur-java` ( squelette ), `build-logic` minimal, CI dexage + scan ART + seuils.
2. Vague 1 : réécriture du patcheur ( 468 l. à reproduire depuis la doc, R9/ADR 0001 ) + reprise ecj ; java-syntax en dépendance ; drapeau « parsing incrémental » inactif.
3. Retour arrière : le drapeau désactive toute consommation de java-syntax ; l'artefact moteur sans java-syntax reste publiable.

## Tests de non-régression et critères de sortie

- CI : scan ART des jars entrants ( motifs attendus = liste de shims, tout écart = échec ) ; d8 dexage + comptage de méthodes ( seuils ) ; `CompatShimsTest` ( comportement des shims sur ART réel via instrumentation, à créer en vague 1 — l'ancien dépôt en a un, sa spécification est à reprendre ).
- Critère de sortie : APK additionnel mesuré < 5 Mo, méthodes < 30 000, zéro crash démarrage sur appareil de test minSdk 26.

## Risques

- **R1 — Montée JDT/ecj change les motifs sensibles** : mitigation = scan CI systématique + ADR 0018.
- **R2 — Disparition/déplacement du dépôt intellij-dependencies** : mitigation = épinner les versions + miroir interne des jars en cache CI.
- **R3 — R8/D8 ( dex 64k ) franchi par accumulation** : suivi de la métrique méthodes dex à chaque PR ( ADR 0016 ).
