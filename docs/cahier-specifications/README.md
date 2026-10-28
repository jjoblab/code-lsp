# Cahier de spécifications du moteur Java — extraction des 449 comportements

> **Base contractuelle de la réécriture intégrale** (ADR 0001, option 2 —
> décision du propriétaire du 28/10/2026 ; méthode définie dans
> `00b-inventaire-reprise.md` §4). Le moteur Java de code-lsp sera réécrit
> de zéro en Kotlin ; ce cahier dit **quoi** l'ancien moteur faisait, sans
> jamais dire **comment** son code le faisait.

## 1. Règle clean-room (ABSOLUE)

Les fiches sont rédigées **exclusivement** à partir de :

- ✅ les **fichiers de test** listés dans `INVENTAIRE.md`
  (`lsp/java/src/test/**` de `codeide-lsp` @ `cb809e14`, lecture seule) ;
- ✅ la **documentation publique** de l'ancien dépôt :
  `docs/LSP_JAVA.md`, `docs/adr/0110*`/`0111*`, `README.md`,
  `docs/LSP_MATRICE_CAPACITES.md` ;
- ✅ les données et golden files référencés par ces tests.

Interdit pendant la rédaction des fiches :

- ❌ lire `lsp/java/src/main` (code de production vendored) — ou tout autre
  code de production des anciens dépôts ;
- ❌ consulter le moteur en boîte noire pour « compléter » une fiche : la
  fiche reflète **le test**, pas le comportement implémenté au-delà des
  tests (l'oracle boîte noire intervient plus tard, au banc de parité,
  ADR 0016).

## 2. Périmètre et chiffres

- **65 fichiers de test** (dont 1 aide `PopupSpontaneReproTestHelper` et
  1 banc `LatenceCompletionBenchmark`), **16 229 lignes**,
  **449 méthodes de test** (@Test/@TestFactory/@ParameterizedTest).
- Scan annotation : 0 `@Disabled`/`@Ignore` déclarés ; les éventuels tests
  « ignorés » à l'exécution le sont par hypothèses/conditions — noter le
  statut `ignoré` seulement si le test l'exprime visiblement.
- Hors périmètre : les tests « host » (498 tests module+host du plan de
  stabilisation) — le cahier couvre le **module moteur** (`:lsp:java`).

## 3. Domaines normalisés (colonne « Domaine » des fiches)

`parse` · `diagnostics` · `completion` · `hover-inlay` · `rename` ·
`navigation` (définitions/usages/highlight/sélection) · `formatage` ·
`imports` · `folding` · `hierarchies` · `templates` · `caches-perf` ·
`concurrence-annulation` · `shims-art` · `cycle-vie-lsp` ·
`index-classpath` · `autre`

## 4. Gabarit de fiche (à suivre EXACTEMENT)

```markdown
# Fiche <NNN> — <ClasseDeTest>

> **Source** : `codeide-lsp` @ `cb809e14` — `<chemin complet du fichier de test>`.
> **Domaine** : <domaine normalisé §3>
> **Résumé** : <2 à 5 phrases — rôle de la classe, ce qu'elle verrouille,
> fixtures/corpus notables.>

## Comportements

### <nomDeMéthodeDeTest> — <titre court descriptif>
- **Préconditions / entrée** : …
- **Comportement attendu** : …
- **Bornes** : <seuil chiffré fixé par le test (latence, taille, nombre) ; sinon « — »>
- **Statut** : actif | ignoré (motif visible : @Disabled, @EnabledIf, hypothèse)

## Notes pour la réécriture
- <points d'attention transposables : dépendances simulées, corpus, pièges,
invariants implicites. Une à cinq puces.>
```

Règles de rédaction : **français** ; une section par méthode de test (les
`@TestFactory` listent chaque cas généré ou les familles de cas) ; rester au
niveau **comportement observable** (entrées → attendus), jamais au niveau de
l'implémentation ; citer les bornes chiffrées exactes du test.

## 5. Production et avancement

Extraction par 4 lots parallèles (agents subordonnés, règle clean-room §1
imposée) ; relecture par le planificateur ; commit après contrôle.

| Lot | Fiches | État |
|---|---|---|
| A | 001–015 | **terminé** (28/10/2026) — 15 fiches, 94 méthodes |
| B | 016–033 | **terminé** (28/10/2026) — 18 fiches, 149 méthodes |
| C | 034–052 | **terminé** (28/10/2026) — 19 fiches, 106 méthodes |
| D | 053–065 | **terminé** (28/10/2026) — 13 fiches, 100 méthodes |

**Bilan d'extraction (28/10/2026)** : 65 fiches · 449 méthodes de test
couvertes · clean-room attesté par chacun des 4 agents (« aucun fichier
sous src/main lu ») · conformité gabarit vérifiée (65/65 : Comportements,
Domaine, Statut, Notes) · 3 593 lignes de fiches.

Anomalies transverses consignées par les agents (à traiter pendant la
réécriture) : repro observationnels sans assertion (004/010/036/040/043) ;
tests conditionnels par hypothèses (android.jar, JDK, model.json absents) ;
classes internes `Jdt*` reliquaires du portage ; 054 verrouille un écart
moteur assumé (1 référence inter-fichiers seulement) — décision de
réécriture à trancher explicitement.

**Convention de nommage pour la réécriture (décision propriétaire,
28/10/2026 — ADR 0002 §Décision, point 5 ; AGENTS.md)** : les préfixes
`Ecj*`/`Jdt*` des noms de fiches sont les identifiants **historiques** des
tests vendored. Les classes neuves ne les reprennent **pas** : le préfixe
`Ecj*`/`Jdt*` est réservé aux classes qui dérivent réellement d'ECJ/JDT
(adaptateurs du compilateur, ponts AST) ; toute autre classe Kotlin porte un
nom neutre décrivant son rôle. L'ancien dépôt préfixait massivement `Ecj*`
des classes sans lien réel avec ecj ou jdt — pratique abandonnée.

Les fiches vivent dans `fiches/NNN-<ClasseDeTest>.md`. L'inventaire complet
(chemins, tailles, nombres de méthodes) : `INVENTAIRE.md`.
