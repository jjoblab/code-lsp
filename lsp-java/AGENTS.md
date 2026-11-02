# AGENTS.md — règles propres au LSP Java

> Règles **propres** au moteur Java (`lsp-java/`). Les règles communes
> (langue, KDoc, provenance, push, multi-agents) sont dans le
> [`AGENTS.md`](../AGENTS.md) racine — elles s'appliquent intégralement.

## Clean-room — le régime du moteur Java

- Le moteur Java est une **réécriture intégrale** (ADR 0001, option 2) :
  la lecture de `lsp/lsp-java/moteur/src/main` de l'ancien dépôt
  `codeide-lsp` (production vendored) est **interdite**. Le vendored sert
  uniquement d'**oracle en boîte noire** (exécution en sous-processus,
  comparaison des sorties — banc de parité croisée T).
- La base de spécification est le **cahier**
  ([`docs/cahier-specifications/`](docs/cahier-specifications/) — 66 fiches,
  449 comportements extraits des tests de l'ancien moteur) : c'est le
  contrat ; un comportement découvert « en route » qui n'y figure pas est
  **signalé**, pas implémenté en silence.
- Les reprises contrôlées (socle `:index`, `:model` — vague 1b) sont
  inscrites fichier par fichier au manifeste
  [`docs/PROVENANCE-REPRISE.md`](docs/PROVENANCE-REPRISE.md) ; `:java`,
  `:testing`, `:ecj-art` sont clean-room intégral (rien à inscrire).

## Nommons les classes

- Préfixes `Ecj*`/`Jdt*` **réservés** aux classes dérivant réellement
  d'ECJ/JDT (décision propriétaire du 28/10/2026) ; toute autre classe
  porte un nom neutre décrivant son rôle. Les `Ecj*Test` des fiches du
  cahier sont des identifiants historiques, pas des noms à imiter.

## Lectures préalables (LSP Java)

1. [`README.md`](README.md) — état, version, périmètre.
2. ADR [`0001`](docs/adr/0001-licence-et-regles-de-reprise.md)
   (réécriture intégrale), [`0002`](docs/adr/0002-perimetre-du-depot-et-modules.md)
   (périmètre et vision), [`0004`](docs/adr/0004-strategie-globale.md) (GO
   PARTIEL), puis les ADR du domaine concerné — index :
   [`docs/INDEX.md`](docs/INDEX.md).
3. Le cahier de spécifications (le contrat).
4. [`docs/etudes/moteur-java-psi/00-synthese.md`](docs/etudes/moteur-java-psi/00-synthese.md)
   (étude d'architecture D1–D12) et
   [`docs/etudes/niveau-android-studio/00-synthese.md`](docs/etudes/niveau-android-studio/00-synthese.md)
   (mission « niveau Android Studio », vagues V-A→V-H).
5. [`USAGE.md`](USAGE.md) — la surface LSP exacte, référence avant toute
   modification de comportement visible du client.

## Procédure de vérification (avant tout push du domaine Java)

```bash
./gradlew build                        # build strict P1–P8 + tests
./gradlew :java:benchQualite           # ÉCHOUE sur régression vs baseline signée
./gradlew :java:benchUsages :java:benchParite
scripts/verifier-provenance.sh         # aucun fichier hors registre
```

- **P1–P8** : propriétés de qualité du cahier (fidélité au compilateur,
  exhaustivité des usages, honnêteté des troncatures…) — le build strict
  les vérifie (règles attestées dans les PV de vagues).
- Les **baselines signées** (`bancs/baselines/*.json`) ne se re-signent
  JAMAIS à la légère : re-signature par étape dédiée avec preuve
  d'équivalence et accord du propriétaire (piège P-1 de la réorganisation,
  ADR 0016).
- Commits : préfixe **`lsp-java(scope):`**, en français, une décision ou
  capacité par commit autant que possible.

## Pièges connus (consignés — les lire avant d'y retomber)

- [`docs/ECARTS-SERVEUR.md`](docs/ECARTS-SERVEUR.md) — écarts reportés /
  non étudiés (inventaire demandé par le propriétaire, 31/10/2026).
- [`docs/etudes/niveau-android-studio/ALERTES.md`](docs/etudes/niveau-android-studio/ALERTES.md)
  et
  [`docs/etudes/moteur-java-psi/ALERTES.md`](docs/etudes/moteur-java-psi/ALERTES.md)
  — alertes des deux missions (classes de pannes, rate-limit Maven Central,
  faux positifs mesurés).
- En-têtes de version du `build.gradle.kts` racine (l. 17–408) : chaque
  version y consigne ses pièges ecj mesurés (drapeau varargs sur le
  binding, liaison unique du dernier segment `R.*`, constructeur par défaut
  sur le nom du type…).
- Sur ART : le fournisseur jrt du JDK externe lève un `Error`
  (`ServiceConfigurationError`) — toujours capturer `Throwable` (v1.1.2) ;
  ne jamais extrapoler une latence JVM vers l'appareil (facteur ART ≈ 4×,
  ADR 0029).
