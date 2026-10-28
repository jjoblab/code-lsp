# code-lsp

**Dépôt officiel des serveurs de langage (LSP) de CodeIDE.**

> Vision du propriétaire (28/10/2026) : un LSP Java « frais, plus solide, plus
> intelligent, plus robuste, plus propre et professionnel », grâce à
> l'architecture IntelliJ PSI — étudiée en profondeur, embarquée nulle part,
> greffée partout où elle prouve sa valeur.

## État — vague 1 (en cours)

Décisions actées par le propriétaire le 28/10/2026 :

| Décision | Référence |
|---|---|
| GO PARTIEL — stratégie hybride « s'inspirer » (ecj + CIDX + java-syntax + designs D8–D12) | ADR 0004 (Accepté) |
| Réécriture **intégrale** du moteur Java en Kotlin — exclusion du vendored définitive | ADR 0001 (Accepté, option 2) |
| Périmètre « domaine moteur Java » + vision « dépôt officiel des LSP » | ADR 0002 (Accepté) |
| Licence du dépôt : Apache-2.0 (`LICENSE`, `NOTICE`) | ADR 0001 |
| Convention de nommage : préfixes `Ecj*`/`Jdt*` réservés aux classes dérivant réellement d'ECJ/JDT | ADR 0002 (point 5) |

Le moteur est réécrit de zéro : **aucun code** du moteur vendored de
`codeide-lsp` n'est repris (ni production, ni tests). La base contractuelle
de la réécriture est le **cahier de spécifications** extrait des 449 tests
de l'ancien moteur (`docs/cahier-specifications/` — **livré en vague 1a** :
65 fiches, 449 comportements).

**Vague 1b (28/10/2026) — premières reprises contrôlées effectuées** :
`:model` (11 fichiers) et `:index` (33 fichiers) repris de codeide-lsp @
cb809e14, inscrites fichier par fichier au manifeste (§7) ; les 4 types du
protocole tooling consommés par le modèle sont **réécrits en code neuf**
(`jo.codeide.lsp.model.protocole` — alternative consignée §4 du manifeste,
faute d'action P1). **122 tests repris verts** (38 modèle + 84 index).
`:testing` est reportée à l'étage noyau du moteur (elle éprouve le moteur
à réécrire).

## Structure du dépôt

| Chemin | Rôle |
|---|---|
| `docs/etudes/moteur-java-psi/` | étude d'architecture IntelliJ PSI (D1–D12), synthèse, mesures du spike, alertes, annexes |
| `docs/adr/` | ADR 0001–0018 (décisions d'architecture, statuts à jour) |
| `docs/cahier-specifications/` | cahier des comportements attendus (extraction clean-room des 449 tests) — le contrat de la réécriture |
| `docs/PROVENANCE-REPRISE.md` | manifeste de reprise sélective (règle R9 — registre des copies autorisées) |
| `spike/` | bancs de mesure V1/V2/V3, scanner de compatibilité ART, gabarit appareil |
| `java/`, `testing/` | modules Gradle du domaine moteur Java (squelettes — moteur réécrit en étages) |
| `index/` | index disque CIDX — **repris contrôlé vague 1b** (17 f. main + 15 f. test + banc) |
| `model/` | modèle de projet LSP + types du protocole réécrits — **repris contrôlé vague 1b** (6 f. main + 5 f. test + code neuf) |
| `scripts/` | vérifications obligatoires (`verifier-push.sh`, `verifier-provenance.sh`) |
| `.github/workflows/` | CI (build + contrôles de provenance) |

## Règles de travail

Voir **`AGENTS.md`** — en résumé : Kotlin 100 %, français partout,
KDoc/Javadoc détaillé obligatoire, clean-room strict vis-à-vis de l'ancien
moteur, manifeste de provenance, et **vérification avant chaque push**
(l'utilisateur authentifié doit être `jjoblab`).

## Construction

Prérequis : JDK 21 (le wrapper Gradle 8.13 est inclus — `JAVA_HOME` doit
pointer vers un JDK complet, pas un JRE).

```bash
./gradlew build       # build + tests (122 tests repris : 38 modèle + 84 index)
```

La CI (`.github/workflows/ci.yml`) exécute le build et le contrôle
« anti-fichier-hors-registre » à chaque push.

## Feuille de route (résumé)

- **Vague 0 (terminée)** : squelette, licences, CI, vérifications.
- **Vague 1 (en cours)** : ~~cahier de spécifications~~ ✓ (1a — 65 fiches,
  449 comportements) ; ~~reprise contrôlée `index`/`model`~~ ✓ (1b —
  45 fichiers inscrits au manifeste, 122 tests verts ; `testing` reportée
  à l'étage noyau) ; réécriture Kotlin du patcheur `ecj-art` (à faire) ;
  ré-estimation du programme de réécriture cahier en main (à faire).
- **Étages suivants** : noyau moteur (env/ecj/parse/diagnostics) →
  complétion (weighers, D8) → navigation/usages/rename (D10) — chaque étage
  borné par la parité mesurée contre l'ancien moteur (banc croisé, ADR 0016).

Détail : `docs/etudes/moteur-java-psi/00-synthese.md` §4 et ADR 0001.
