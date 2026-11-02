# spike-kotlin — Module de mesure isolé (Phase 2 du prompt de mission)

> Étude « moteur Kotlin » pour CodeIDE sur Android/ART. **Module de
> mesure uniquement** : aucun code de production (§0 du prompt). Code
> neuf en Kotlin (R2). Les jars mesurés sont **téléchargés hors dépôt,
> jamais committés** (R6.4 — `docs/PROVENANCE-REPRISE-KOTLIN.md`).
> Résultats et lecture : `../docs/etudes/moteur-kotlin/02-spike-mesures.md`.

## Question de recherche (§7 du prompt)

Quelle architecture sémantique tient sur ART avec une empreinte
acceptable, et à quel prix d'intégration ? Variantes mesurées :

| Voie | Pile | Contenu | Taille | Méthodes | Motifs ART |
|---|---|---|---|---|---|
| **S0** — oracle | `lsp-kotlin-1.22.1.jar` (GitHub Packages) + syntax-api | ancien moteur PUBLIÉ, exécuté en boîte noire par son SPI public | **0,86 Mo** | 355 | **0** |
| **S1** — K2 | `kotlin-compiler-embeddable 2.2.10` (+stdlib, coroutines, annotations) | frontend K2 complet (le seul vaisseau — l'Analysis API standalone n'est PAS publiée) | **53,6 Mo** | **64 507** | **~2 170** |
| **S2** — parseur officiel | grammaire vendorée DEPUIS L'AMONT (tag v2.3.0, `src/main/vendor/`) + syntax-api 0.3.340 | parseur officiel seul, piloté par `ParseurOfficiel` | 0,51 Mo | 514 | **0** |
| **S3** — hybride | S2 + coût S1 en différé | mesuré par composition (02-spike §5.5/§6.5) | — | — | — |

## Reproduction (R5)

```bash
# 1. Jars (répertoire hors dépôt ; GITHUB_TOKEN requis pour l'oracle S0) :
GITHUB_TOKEN=<jeton> bash spike-kotlin/scripts/telecharger-jars.sh [cible]
#    (défaut : <dépôt>/../spike-jars ; surcharge : -PspikeJars=<chemin>)

# 2. Bancs (module GARDÉ : sans -PspikeKotlin, toutes les tâches sont
#    désactivées — la CI du domaine Java n'est jamais affectée) :
./gradlew :spike-kotlin:test -PspikeKotlin

# 3. Scan ART statique :
python3 spike-kotlin/scripts/scan-art.py <jar>…    # → resultats/scan-art-*.txt
```

Environnement des mesures consignées : `resultats/environnement.txt`
(OpenJDK 21.0.13, 2 cœurs, bac à sable, 2026-10-30). **Toute mesure JVM
est une limite basse** (facteur ART 3–5× [estimé]). Aucun appareil
n'était disponible (E-K2) — le point d'entrée appareil reste à produire
sur le gabarit du domaine Java.

## Contenu

- `src/main/vendor/` — grammaire officielle **ré-obtenue de l'amont**
  (R9.2 : jamais depuis `codeide-lsp`), tag v2.3.0, Apache-2.0, notices
  copiées (`VENDOR.md`). 23 fichiers / 9 585 lignes — compile telle
  quelle avec la chaîne du dépôt [mesuré].
- `src/main/kotlin/spikekotlin/` — `GenerateurCorpus` (corpus
  déterministe 500/3 000 l.), `ParseurOfficiel` (pilotage de la
  grammaire — patron d'assemblage adapté de `NewTestParser.kt` amont,
  crédité).
- `src/test/kotlin/spikekotlin/` — `BancS0Test` (oracle en boîte noire
  par ServiceLoader — surface publique de l'artefact uniquement),
  `BancS1Test` (K2 en sous-processus), `BancS2Test` (parse officiel +
  tolérance), `VueVide` (implémentation minimale honnête de l'interface
  publique `VueClasspathKotlin`), `CheminJars` (localisation des piles).
- `resultats/` — mesures brutes et scans (rejouables).
- `scripts/` — `telecharger-jars.sh`, `scan-art.py` (pools de constantes
  — miroir de la méthode ScanArt.kt du spike Java, en Python).

## Clean-room (R9)

Le banc S0 n'utilise l'ancien moteur que comme **oracle en boîte
noire** : l'artefact PUBLIÉ est chargé par ServiceLoader et conduit par
sa surface publique (celle de tout consommateur) ; **aucune source de
`lsp/kotlin/src/main` n'est lue** (la clean-room de l'étude tient —
AGENTS.md). Le jar vit hors dépôt, jamais committé.
