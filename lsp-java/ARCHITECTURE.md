# ARCHITECTURE — moteur Java LSP (`lspjava`)

> Composants, flux de données et décisions structurantes du moteur Java.
> Chaque décision renvoie à son ADR ([index complet](docs/INDEX.md)) ; les
> chiffres portent leur source (en-têtes de version du `build.gradle.kts`
> racine, PV de mesures, journaux d'études). La surface LSP exacte est dans
> [`USAGE.md`](USAGE.md).

## 1. Composants

| Composant | Rôle | Décision |
|---|---|---|
| Pont ecj patché (`:ecj-art`) | pilotage d'ecj 3.40.0 avec **bridge `Compiler.resolve`** — arbres vivants, jamais nettoyés ; shims et liste versionnée | ADR 0005/0018 |
| Routeur de classpath | résolution **sources → jars → jrt → stubs intrinsèques**, FQCN longest-match ; jrt runtime ou externe (`jrt-fs.jar` + URLClassLoader/ServiceLoader), repli ART honnête | fiches 010/031/042 |
| Moteur par module | un moteur par module de projet, routeur fichier→module **relu à chaque requête** | fiches 034/047 |
| Ordonnanceur 3 voies | FRAPPE (complétion/signature/statement) / NAVIGATION / CONFORT ; supersession **à valeur**, frappe-caducité, exclusion par fichier ; pool plafonné 2 par défaut (`jo.codeide.lsp.java.ordonnanceur.workers` 1..3) | ADR 0010, vague V-H |
| Environnement à époques | **double-tampon** de l'environnement de compilation (0 figage — l'ancien moteur sert pendant la construction du neuf, swap atomique) + **époques** (0 réponse périmée, rejeu borné 3/8) | ADR 0029 |
| Diagnose à deux régimes | **rapide par corps de méthode** (équivalence stricte contre témoin neuf) + complet analytique ; **repli syntaxe instantané** sans plateforme | fiches 014/015/028 |
| Chaîne de complétion | analyse de contexte par **échelle de variantes du splice**, collecte par genre (balayage de hiérarchie jusqu'à `Object`, traversée classpath, auto-import), filtrage gradué, chaîne de peseurs, **gel des têtes + plafond honnête 120** | ADR 0012/0026/0030 |
| Modèle de projet Android | référentiel de ressources XML (values/layouts, MAJ ciblée sur didChange), **classes light** R/BuildConfig/ViewBinding/DataBinding, interop Kotlin conservatrice | ADR 0020/0021/0022 |
| Inspections & lint | moteur d'inspections à contrat + profil + suppressions ; familles Android Lint (base api-versions en tâche de fond, segment CIDX compact) | ADR 0023/0024 |
| Navigation & usages | routeur GO TO complet (intra-fichier → projet → binaire `jdt://decompiled/`), `QueryExecutor` des usages sur index de mots — `Files.walk` retiré du chemin chaud | ADR 0014/0028 |
| Renommage sûr | cible par bindings, occurrences exhaustives multi-fichiers, **collisions typées AVANT écriture**, aperçu = résultat (jamais d'écriture disque) | fiche 026, ADR 0014 |
| Actions de code | modèle **ModCommand** (plan déclaratif sérialisable, calcul en lecture, exécution déléguée au client), familles ID-first, codeAction/resolve différé | ADR 0027/0032 |
| Protocole lsp4j | machine à états UNINITIALIZED→EXITED, gardes aux codes exacts -32002/-32600, sync plein texte copy-on-write, publication asynchrone débouncée 50 ms, capabilities maison versionnées | ADR 0031 |

## 2. Flux de données (une requête de complétion)

1. `didChange` → les **états de documents** publient un instantané immuable
   et incrémentent l'époque (copy-on-write).
2. La requête est **soumise à l'ordonnanceur** (voie FRAPPE) : le document
   est capturé à la soumission (cohérence coordonnées ↔ texte) ; une frappe
   rend caduques les requêtes inférieures en attente du même document
   (supersession à valeur — réponse vide honnête, jamais une annulation).
3. Le **routeur de module** désigne le moteur ; la chaîne de complétion
   interroge les caches (présence/localisation/parse), l'arbre ecj vivant
   ou le repli syntaxe.
4. La réponse est livrée après **vérification d'époque** (invariant
   « 0 réponse périmée » tenu sous concurrence) ; les diagnostics partent
   en publication asynchrone débouncée avec la version courante du buffer.

## 3. Contraintes Android / ART

- **Aucun interne JDK requis à l'exécution** : sur ART le fournisseur jrt du
  JDK externe lève `ServiceConfigurationError` (internes `jdk.internal.*`
  absents) — repli silencieux documenté vers le régime instantané, la
  plateforme restant couverte par les entrées réelles du classpath
  (correctif v1.1.2, régression verrouillée par `PlateformeClasspathARTTest`).
- **Bytecode 17**, toolchain JDK 21 (build) ; le jar du moteur et le
  compagnon ecj sont **re-scannés vierges** de références sensibles ART
  (scanner de compatibilité du spike, CI « Gates Android » SDK 34).
- **Mémoire sous pression** : budgets d'**octets** du cache de parse et du
  mémo de localisation, shim `onTrimMemory` (relais
  `EnvironnementCompilation.pressionMemoire`, rapport chiffré, kill-switches
  `memoire.budgets.disabled` / `memoire.ontrim.disabled`).
- **Facteur ART ≈ 4×** observé (ADR 0029) : les latences JVM ne
  s'extrapolent jamais sans mesure appareil.

## 4. Budgets et mesures de référence (avec sources)

| Grandeur | Valeur mesurée | Source |
|---|---|---|
| Démarrage à froid | 893 ms (v1.12.0) / 1 011 ms (v1.11.0) | en-têtes de version `build.gradle.kts` |
| Diagnose complet / rapide | p50 13,1 ms / 4,9 ms (v1.12.0) | idem |
| Survol (hover) | p50 0,11 ms (v1.12.0) | idem |
| Tas au repos | 35 Mo (v1.11.0/1.12.0) | idem |
| Complétion concurrente | p95 33 ms (vs 43 séquentiel), banc de charge | v1.6.0 |
| Frappe pendant reconstruction (double-tampon) | ×1,20 p50 / ×0,58 p95 du nominal (budget ≤ 2× tenu) | v1.5.0 |
| Latence liste codeAction | p95 410 µs (budget < 30 ms) | v1.12.0 |
| semanticTokens | p95 1 ms (cible < 25 ms) | v1.11.0, ADR 0031 |
| Budget d'horloge complétion | 3 s (expiration = partiel honnête) | v1.12.1 |
| Chien de garde lenteur | seuil 2 s, `window/logMessage` relayé | v1.12.1 |
| Peson du référentiel de ressources | 126 Ko max sur corpus (budget 2 Mo) | v1.4.0, ADR 0020 |
| Jar du moteur (tête 1.13.0) | 1 532 056 o | build local 03/11/2026 |
| Compagnon `ecj-3.40.0-art.jar` | 3 288 188 o | GitHub Packages (11 versions) |

Cibles « niveau Android Studio » arbitrées (ADR 0030 / doctrine D5) :
complétion < 100 ms p95 perçu, diagnostics < 300 ms, démarrage chaud
< 3 s — les bancs à baselines signées
([`lsp-java/bancs/baselines/`](bancs/baselines/)) échouent la CI sur régression
(ADR 0016).

## 5. Décisions structurantes (lecture rapide)

ADR [0004](docs/adr/0004-strategie-globale.md) GO PARTIEL (s'inspirer) ·
[0012](docs/adr/0012-completion-et-classement.md) classement de la
complétion · [0016](docs/adr/0016-banc-de-qualite-et-non-regression.md)
bancs bloquants · [0020](docs/adr/0020-modele-de-projet-android-et-referentiel-de-ressources.md)–[0022](docs/adr/0022-interoperabilite-kotlin-vers-java.md)
modèle Android · [0026](docs/adr/0026-completion-niveau-android-studio.md)
complétion enrichie · [0028](docs/adr/0028-recherche-et-navigation-exhaustives.md)
navigation exhaustive · [0029](docs/adr/0029-performance-concurrence-memoire-art.md)
performance/ART · [0031](docs/adr/0031-surface-protocole.md) surface
protocole · [0032](docs/adr/0032-complement-de-catalogue-familles-unitaires.md)
complément de catalogue — index complet : [`docs/INDEX.md`](docs/INDEX.md).
