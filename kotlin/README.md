# :kotlin — moteur Kotlin LSP de CodeIDE (voie C, étage 0)

> Moteur Kotlin LSP de CodeIDE sur Android/ART. **Voie C — hybride** (ADR
> 0100, acceptée par le propriétaire le 2026-10-30) : noyau (parse,
> résolution, session, diagnostics, complétion) **réécrit depuis le cahier**
> `docs/cahier-specifications-kotlin/` (53 fiches, 469 comportements) ;
> modules différenciants sains **repris après contrôle R9.4** (registre
> `docs/PROVENANCE-REPRISE-KOTLIN.md`) ; grammaire officielle **vendorée
> par ré-obtention amont** (jamais depuis `codeide-lsp` — R9.2).
> Stratégie sémantique : **voie (i)** parseur officiel + modèle de symboles
> conservateur (ADR 0103, acceptée) — **aucun compilateur sur le chemin
> d'édition**.

## État — étage 0 (amorçage)

| Élément | État |
|---|---|
| Grammaire vendorée `src/main/vendor/` | tag amont v2.3.0 (`f95cb2f7…`), Apache-2.0, octet pour octet — notice `VENDOR.md`, re-sync par `scripts/vendor-grammaire-kotlin.sh` (ADR 0121) |
| Harnais `ParseurKotlin` | minimal (fiches 010–012) : genre racine, marqueurs d'erreur, jetons non blancs, digeste structurel — la façade `Kt*` (fiche 013) est l'étage 1 |
| Suite de parité | fiches 010 (corpus du dépôt + digeste baseline), 011 (tolérance aux fautes), 012 (fixtures) — `./gradlew :kotlin:test` |
| Descripteur de pack | `version.properties` généré (contrat D6 : id/version/minApp/grammaire) |
| Banc | `./gradlew :kotlin:bench` — empreinte/latence, nombres seuls (miroir `:java:bench`) |
| CI | jobs `kotlin-parite`, `kotlin-art` (scan ART + dexage d8 + seuils), `kotlin-banc` + `provenance` étendu au périmètre `kotlin/` |

Critères de sortie de l'étage 0 : **build vert ; parité grammaire verte ;
zéro fichier hors registre ; 0 motif ART** (rapport :
`docs/etudes/moteur-kotlin/03-execution-etage0.md`).

## Règles propres au module

- **Clean-room pour le noyau** : le code de `codeide-lsp:lsp/kotlin/src/main`
  n'est pas consulté pour les zones réécrites (parse, resolve, diagnostics,
  completion, session, api) — le cahier est le contrat. Les zones de
  reprise (voie C) sont ouvertes fichier par fichier, APRÈS inscription au
  manifeste et contrôle R9.4.
- **explicitApi Strict** sur le code neuf (`compileKotlin`) ; le vendoring
  vit dans une compilation séparée (`vendor`) exemptée (ADR 0104, miroir
  ADR 0115.4 ancien) — l'exemption est limitée à `src/main/vendor/` par
  construction.
- **Bytecode 17** (consommable par d8/ART — convention dépôt), toolchain
  JDK 21, Kotlin 2.2.10 (chaîne racine du dépôt).
- La baseline digeste (`src/test/resources/parite/digeste-corpus.txt`) se
  re-signe DÉLIBÉRÉMENT : `-Dparite.majBase=true --rerun-tasks` (le diff
  fait partie de la revue — cf. KDoc de `Fiche010PariteCorpusDepotTest`).

## Dépendances (chaîne Apache-2.0 — ADR 0120)

- Grammaire vendorée (amont, Apache-2.0, notices embarquées dans le jar).
- `org.jetbrains:syntax-api:0.3.340` (+ lang-syntax, annotations transitifs)
  — intellij-dependencies via cache-redirector ; **conditions des binaires
  à confirmer par le propriétaire (P-K2) avant publication du pack**.
- `kotlin-compiler-embeddable` est **interdit de fait** dans le graphe
  compile-scope du module (garde CI ADR 0120 — voie (ii) disqualifiée au
  spike : 53,6 Mo / 64 507 méthodes / ~2 170 motifs ART [mesuré]).

## État — étage 1 (noyau parse/session/classpath) — vagues 1a/1b

| Élément | État |
|---|---|
| Façade `Kt*` (vague 1a, fiche 013) | Livrée : reconstruction d'arbre immuable + accesseurs conservateurs (introuvable = null/vide, jamais d'exception) |
| SPI zone `api/` (vague 1b) | **Né** : `ServiceMoteurLangageKotlin` (`unique` : ServiceLoader sur descripteur embarqué + repli moteur noyau) et `SessionLangageKotlin` — types 100 % neutres (D6 : le pack futur se branche là, et uniquement là) |
| Session documents (vague 1b, fiches 049/042) | **Livrée** : didOpen/didChange/didClose + diagnostics `kt.syntax`/`kt.unusedImport`/`kt.conflictingImport` (tags INUTILE + informations liées KP15), plages pliables, survol S2 (signature + KDoc), `rafraichirSources` (L1), branchement A211 (moteur nourricier, sous-lecture honnête, guérison à la requête suivante) |
| Concurrence | Verrou lecture-écriture (ADR 0117/0212) : écritures = mutations/définitions/publication du scan ; requêtes en lectures PARALLÈLES ; le scan L1 court hors verrou |
| Suite du module | 57 tests verts (fiches 010–013, 049, 042) — `./gradlew :kotlin:test` |
| Jar moteur | 302,1 Kio / 117 classes (51 code neuf) — descripteur `META-INF/services` + notices vendor embarqués |

Journal d'exécution de l'étage : `docs/etudes/moteur-kotlin/04-execution-etage1.md`
(vagues suivantes : 1c scan projet paresseux — fiche 041 ; 1d classpath + index
appelables — fiches 002–005/008/009/034, reprises R4/R5).
