# GOUVERNANCE — clean-room, provenance, licences, publication

> Les règles de fond du dépôt, consolidées (Phase 3, 03/11/2026) depuis les
> décisions propriétaire du 28/10 au 03/11/2026 et les manifestes de
> provenance. Les règles opérationnelles (push, multi-agents) sont dans
> [`AGENTS.md`](../AGENTS.md) ; l'index transverse dans
> [`INDEX.md`](INDEX.md).

## 1. Clean-room et régimes de reprise (règle R9)

Trois régimes, appliqués par la CI
([`scripts/verifier-provenance.sh`](../scripts/verifier-provenance.sh)) :

| Régime | Règle | Périmètre mesuré |
|---|---|---|
| **Réécriture intégrale** (R9.3) | aucun code repris d'un ancien dépôt ; spécification = le **cahier** extrait des tests | moteur Java (`:java`, `:testing`, `:ecj-art`) ; noyau Kotlin (parse/résolution/session/diagnostics/complétion, serveur) |
| **Reprise contrôlée** (R9.2) | inscription **préalable** fichier par fichier au manifeste (dépôt, chemin, commit, licence, décision, justification) ; la CI refuse tout fichier hors registre | `socle/index/`, `socle/model/` (vague 1b) ; zones de reprise Kotlin (`synthetics/`, `preview/`, `classpath/`, `format/`, `plis/`, `documentation/`, `symboles/` — reprises R1–R8) |
| **Vendoring** (R9.2) | ré-obtention **de l'amont uniquement** (jamais de l'ancien dépôt) ; notices obligatoires (`VENDOR.md`, `LICENSE.txt`, `COPYRIGHT.txt`) ; dérive gardée par la suite de parité (digeste structurel) | grammaire KMP officielle du module `:kotlin` (tag amont v2.3.0, 422 fichiers signés) |

L'ancien moteur vendored de `codeide-lsp` sert **uniquement d'oracle en
boîte noire** (exécution en sous-processus, comparaison des sorties) — le
banc de parité croisé T (baseline
[`lsp-java/bancs/baselines/parite-completion.json`](../lsp-java/bancs/baselines/parite-completion.json),
oracle épinglé `codeide-lsp` @ `cb809e14`).

## 2. Licences (mesurées dans les registres et NOTICE)

| Composant | Licence | Source |
|---|---|---|
| Dépôt code-lsp (code et documentation) | **Apache-2.0** | [`LICENSE`](../LICENSE) · [`NOTICE`](../NOTICE) · ADR 0001 |
| Compagnon `ecj-3.40.0-art.jar` (ecj patché) | EPL-2.0 (écoulement ecj/JDT) | [`NOTICE`](../NOTICE) · registre Java |
| Grammaire KMP vendorée (`:kotlin`) | Apache-2.0 (notices accompagnent la redistribution) | `lsp-kotlin/moteur/src/main/vendor/` · VENDOR.md |
| Jars `syntax-api`/`lang-syntax` (intellij-dependencies, 0.3.340) | **Apache-2.0 acté par le propriétaire le 31/10/2026** (P-K2 levée) | `lsp-kotlin/docs/etudes/moteur-kotlin/11-decisions-proprio-post-cloture.md` §1 |
| Câble `lsp4j` 0.21.2 (+ jsonrpc, gson) | **EPL-2.0 — PORTÉ PAR L'HÔTE**, jamais embarqué dans le jar du moteur | décision P-K2, 31/10/2026 ; journal étage 6 §7 |

## 3. Honnêteté des chiffres (règle R5)

Chaque chiffre publié dans la documentation vient d'une **mesure ou d'un
fichier du dépôt** et porte sa source (fichier, run CI, PV) ; sinon il est
retiré. Les états qualitatifs portent les statuts
`[CONFIRMÉ] / [INFIRMÉ] / [NON VÉRIFIÉ]` et les estimations sont étiquetées
`[estimé]`. Une mesure sur appareil n'est **jamais simulée** — R5 consigné
dans tous les journaux d'étages (exemple : gabarit de bascule
[`GABARIT-BASCULE-APPAREIL.md`](GABARIT-BASCULE-APPAREIL.md) rempli sur
passe réelle).

## 4. Publication vers GitHub Packages

- **Déclencheur** : tag `v*` uniquement →
  [`.github/workflows/publish.yml`](../.github/workflows/publish.yml)
  (1 job « Vérification puis publication (JDK 21) » : build de vérification
  complet puis `publish`).
- **Coordonnées Java** (dépôt `maven.pkg.github.com/jjoblab/code-lsp`) :
  `jo.codeide.lsp.java:jo-codeide-lsp-java` et le compagnon
  `jo.codeide.lsp.java:jo-codeide-lsp-java-ecj-art` — **11 versions
  publiées** (1.0.0, 1.1.0–1.1.6, 1.12.0–1.12.2 ; dernière le 02/11/2026,
  run #12 id 41372554541, succès).
- **Pack Kotlin** (Q-K4 tranché le 03/11/2026 — arbitrage propriétaire :
  mécanisme miroir `:java`, façade unique) : coordonnées
  `jo.codeide.lsp.kotlin:jo-codeide-lsp-kotlin` — même dépôt, même
  déclencheur (tag `v*`), artefact = pack langue COMPLET (code neuf +
  grammaire vendorée + notices `META-INF/vendor-kotlin` + descripteur D6) ;
  les bloqueurs de licence P-K1/P-K2 étaient levés depuis le 31/10/2026.
  Première publication : **1.13.0** (tag `v1.13.0`, 03/11/2026 — même run
  que le moteur Java ; statut consigné dans
  [`lsp-kotlin/docs/INTEGRATION-CODEIDE.md`](../lsp-kotlin/docs/INTEGRATION-CODEIDE.md)).
- Les re-signatures de baselines signées passent par une **étape dédiée**
  avec preuve d'équivalence et accord du propriétaire (piège P-1 — cf.
  [`REORGANISATION-BILAN-PHASE2.md`](REORGANISATION-BILAN-PHASE2.md) §3).

## 5. La CI est l'arbitre

10 jobs à chaque push (noms exacts consignés dans
[`REORGANISATION-AVANT.md`](REORGANISATION-AVANT.md) §7) : build et tests,
bancs à baselines signées (Java et Kotlin), gates Android SDK 34,
provenance anti-fichier-hors-registre, scan ART + dexage d8 sous seuils.
`main` est protégée : les 10 jobs sont des **checks requis** (stricts) ;
force push et suppression interdits. Une tête n'est fusionnée que CI
verte ; les branches d'étude `etude/*` ne sont jamais rebasées ni
supprimées (tags `etude-java-final`, `etude-kotlin-final`).
