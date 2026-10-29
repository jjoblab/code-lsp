# Cahier de spécifications du moteur Kotlin — extraction des 469 comportements

> **Base de spécification pour la décision ADR 0100** (voie A réécriture
> intégrale ou voie C hybride — le cahier est « en main » dans les deux cas).
> Méthode : R9.5 du prompt de mission — extraction **des tests uniquement**,
> miroir de la méthode du cahier Java (`docs/cahier-specifications/`,
> 65 fiches / 449 comportements, exécutée avec succès en vague 1a Java).

## 1. Règle clean-room (ABSOLUE)

Les fiches sont rédigées **exclusivement** à partir de :

- ✅ les **fichiers de test** listés dans `INVENTAIRE.md`
  (`lsp/kotlin/src/test/**` de `codeide-lsp` @ `bc11253`, lecture seule —
  KDoc de classe + noms des méthodes `@Test`, extraits par script) ;
- ✅ la **documentation publique** de l'ancien dépôt : `docs/LSP_KOTLIN.md`,
  ADR 0094/0115/0119/0202–0233, `docs/RAPPORT-AUDIT-KOTLIN-LSP.md`,
  `docs/RAPPORT-FAUX-POSITIFS-2026-10-26.md`,
  `docs/INTEGRATION-CODEIDE-LSP-KOTLIN.md`,
  `docs/LSP_MATRICE_CAPACITES.md` ;
- ✅ le descripteur de vendoring (`src/main/vendor/VENDOR.md` — document).

Interdit pendant la rédaction des fiches :

- ❌ lire `lsp/kotlin/src/main/kotlin` (code de production — R9.1) ;
- ❌ consulter le moteur en boîte noire pour « compléter » une fiche : la
  fiche reflète **le test**, pas le comportement implémenté au-delà des
  tests (l'oracle boîte noire intervient plus tard, au banc de parité,
  ADR 0119 de l'étude) ;
- ❌ copier quelque extrait de code que ce soit (test ou production) —
  chaque comportement est spécifié par le **nom exact de son test source**
  (identifiant de traçabilité), un attendu reformulé et un classement.

## 2. Périmètre et chiffres [mesuré]

- **53 fichiers de test**, **469 méthodes `@Test`**, **15 853 lignes**
  (comptage par `rg --count-matches '@Test\b'` et `wc -l`, script
  `metriques-kotlin.sh` du bac à sable).
- Hors périmètre : les tests de l'**hôte** (`:lsp:host` de l'ancien dépôt —
  transport LSP4J, adaptateurs) — le cahier couvre le **module moteur**
  (`:lsp:kotlin`). La frontière transport sera spécifiée par l'ADR 0118
  de l'étude.
- 0 annotation `@Disabled`/`@Ignore` déclarée [mesuré] ; les tests
  conditionnels le sont par **hypothèses d'environnement**
  (`REPRO_COMPOSE_DIR`, `REPRO_VIEWS_DIR` — corpus E2E indisponibles en CI,
  cf. rapports) : les comportements correspondants restent fichés, leur
  gate de parité exigera le corpus au banc.

## 3. Domaines normalisés (colonne « Domaine » de l'INVENTAIRE)

`parse` · `diagnostics` · `completion` · `hover-inlay` · `navigation` ·
`formatage` · `folding` · `caches-perf` · `concurrence-annulation` (absent
des fichiers de test — couvert par `ScanProjetTest` accès concurrents et
`SessionLangageKotlinA211Test` verrou lecture/écriture, classés
`index-classpath`) · `cycle-vie-lsp` · `index-classpath` · `autre`
(previews Compose).

Agrégat [mesuré] : diagnostics 204 · index-classpath 50 · completion 44 ·
hover-inlay 44 · navigation 38 · cycle-vie-lsp 29 · parse 27 · autre
(previews) 16 · folding 7 · formatage 9 · caches-perf 1. **Total 469.**

## 4. Gabarit de fiche

```markdown
# Fiche <NNN> — <ClasseDeTest>

> **Source** : `codeide-lsp` @ `bc11253` — `<chemin complet du fichier de test>` (<n> tests, <l> lignes).
> **Domaine** : <domaine normalisé §3>
> **Résumé** : <KDoc de la classe de test, condensé — le contrat qu'elle verrouille.>
> **Convention** : chaque comportement porte le nom exact du test source…

## Comportements

### `<nomExactDuTestSource>`

- **Attendu** : <reformulation française du comportement, enrichie du contrat D3 si le nom le porte>.
- **Classement** : conforme à la spécification Kotlin / heuristique / contradictoire.
```

## 5. Classement (R9.5 — « conforme / heuristique / contradictoire »)

Règles appliquées (conservatrices, revues à la main pour les fiches
critiques) :

- **conforme (propriété de l'amont)** : comportements de parité de la
  grammaire vendorée (parse du corpus, digeste, tolérance aux fautes,
  déterminisme) — la spécification est l'amont JetBrains lui-même ;
- **conforme (borne conservatrice « preuve ou silence »)** : comportements
  de silence honnête (muet, vide, no-op, jamais d'exception) — la politique
  D3 est un **contrat de l'étude** (critère §4 : « zéro faux positif
  toléré »), non une règle du langage : le moteur neuf doit la reproduire ;
- **heuristique (règle maison)** : le reste — chaque règle de résolution/
  typage/diagnostic est un **sous-ensemble arbitraire** du comportement K2
  (smart casts positionnels, inférence par attente, surcharges par arité,
  récepteurs de portée, synthétiques…) : la **spécification Kotlin
  l'emporte sur l'ancien comportement** (R9.5), sauf décision écrite du
  propriétaire ;
- **contradictoire** : aucun comportement classé contradictoire n'a été
  détecté à l'extraction (l'ancien moteur ne contredit pas sciemment la
  spécification — il **sous-rapporte**) ; le classement restera ouvert à
  requalification au banc de parité face à K2 (ADR 0119).

## 6. Statut

Généré le 2026-10-30 (Phase 0) par script assisté + relecture — les fiches
sont **versionnées** ; toute correction se fait par commit dédié (la
méthode du cahier Java : « fiches versionnées, relues »).
