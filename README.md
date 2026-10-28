# code-lsp

**Dépôt officiel des serveurs de langage (LSP) de CodeIDE.**

> Vision du propriétaire (28/10/2026) : un LSP Java « frais, plus solide, plus
> intelligent, plus robuste, plus propre et professionnel », grâce à
> l'architecture IntelliJ PSI — étudiée en profondeur, embarquée nulle part,
> greffée partout où elle prouve sa valeur.

## État — programme de réécriture approuvé — prochaine étape : mesure sur
appareil (porte d'entrée de N1)

Décisions actées par le propriétaire le 28/10/2026 :

| Décision | Référence |
|---|---|
| GO PARTIEL — stratégie hybride « s'inspirer » (ecj + CIDX + java-syntax + designs D8–D12) | ADR 0004 (Accepté) |
| Réécriture **intégrale** du moteur Java en Kotlin — exclusion du vendored définitive | ADR 0001 (Accepté, option 2) |
| Périmètre « domaine moteur Java » + vision « dépôt officiel des LSP » | ADR 0002 (Accepté) |
| Licence du dépôt : Apache-2.0 (`LICENSE`, `NOTICE`) | ADR 0001 |
| Convention de nommage : préfixes `Ecj*`/`Jdt*` réservés aux classes dérivant réellement d'ECJ/JDT | ADR 0002 (point 5) |

Décisions sur le programme de réécriture, actées par le propriétaire le
28/10/2026 en séance (`docs/PLAN-REECRITURE.md` §10) :

- **Fourchette 160–270 j-h (médiane 213) approuvée** comme enveloppe de
  planification — engagement contractuel **borné à N1 + banc de parité
  croisé**, décision de suite re-soumise au recalage de fin N1 ;
- **mesure sur appareil = porte d'entrée bloquante de N1** (`spike/appareil/`)
  : aucune implémentation du noyau avant retour du gabarit rempli ;
- **séquence confirmée** : N1 → (N2, N3, N7) → (N4, N5, N6) → N8 ;
- **écart moteur de la fiche 054 reproduit en parité stricte** — correction
  consignée comme amélioration candidate post-bascule.

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

**Vague 1c (28/10/2026) — patcheur `ecj-art` réécrit en Kotlin clean-room** :
remap de `Runtime$Version` + redirections `readAllBytes`/`readNBytes` vers des
shims embarqués (sémantique JEP 322 et javadoc JDK, éprouvée en différentiel
contre l'oracle JVM), pilotés par une **liste de shims versionnée**
(`liste-shims-3.40.0.txt`, ADR 0018) que la CI confronte au scan du jar
entrant. Le jar sortant est re-scanné : **aucun motif sensible résiduel**
toléré ; le registre EPL-2.0 des modifications est embarqué
(`META-INF/ECJ-ART-MODIFICATIONS.txt`). 36 tests (dont compilation de fumée
avec le jar ecj patché). Tâche Gradle : `./gradlew :ecj-art:patcherEcj`.

## Structure du dépôt

| Chemin | Rôle |
|---|---|
| `docs/etudes/moteur-java-psi/` | étude d'architecture IntelliJ PSI (D1–D12), synthèse, mesures du spike, alertes, annexes |
| `docs/adr/` | ADR 0001–0018 (décisions d'architecture, statuts à jour) |
| `docs/cahier-specifications/` | cahier des comportements attendus (extraction clean-room des 449 tests) — le contrat de la réécriture |
| `docs/PLAN-REECRITURE.md` | **programme de réécriture chiffré** (étages N1–N8+T, fourchette 160–270 j-h) — **approuvé le 28/10/2026** (engagement borné N1 + banc croisé) |
| `docs/PROVENANCE-REPRISE.md` | manifeste de reprise sélective (règle R9 — registre des copies autorisées) |
| `spike/` | bancs de mesure V1/V2/V3, scanner de compatibilité ART, gabarit appareil |
| `java/`, `testing/` | modules Gradle du domaine moteur Java (squelettes — moteur réécrit en étages) |
| `index/` | index disque CIDX — **repris contrôlé vague 1b** (17 f. main + 15 f. test + banc) |
| `model/` | modèle de projet LSP + types du protocole réécrits — **repris contrôlé vague 1b** (6 f. main + 5 f. test + code neuf) |
| `ecj-art/` | patcheur build-time d'ecj pour ART — **réécriture Kotlin clean-room vague 1c** (scanner, patcheur, shims, liste versionnée) |
| `scripts/` | vérifications obligatoires (`verifier-push.sh`, `verifier-provenance.sh`) |
| `.githooks/` | crochet `pre-push` d'identité jjoblab (activation : `git config core.hooksPath .githooks`) |
| `.github/workflows/` | CI (build + contrôles de provenance) |

## Règles de travail

Voir **`AGENTS.md`** — en résumé : Kotlin 100 %, français partout,
KDoc/Javadoc détaillé obligatoire, clean-room strict vis-à-vis de l'ancien
moteur, manifeste de provenance, et **vérification avant chaque push**
(l'utilisateur authentifié doit être `jjoblab`).

## Construction

Prérequis : JDK 21. Le wrapper Gradle 8.13 est inclus ; si la machine n'a
pas de JDK 21 complet, le plugin d'auto-provisionnement foojay le télécharge
automatiquement (paramétré dans `settings.gradle.kts`).

```bash
./gradlew build                    # build + tests (158 tests : 38 modèle + 84 index + 36 ecj-art)
./gradlew :ecj-art:patcherEcj      # produit ecj-art/build/ecj-patche/ecj-3.40.0-art.jar
```

La CI (`.github/workflows/ci.yml`) exécute le build et le contrôle
« anti-fichier-hors-registre » à chaque push.

## Feuille de route (résumé)

- **Vague 0 (terminée)** : squelette, licences, CI, vérifications.
- **Vague 1 (terminée)** : ~~cahier de spécifications~~ ✓ (1a — 65 fiches,
  449 comportements) ; ~~reprise contrôlée `index`/`model`~~ ✓ (1b —
  45 fichiers inscrits au manifeste, 122 tests verts ; `testing` reportée
  à l'étage N1) ; ~~réécriture Kotlin du patcheur `ecj-art`~~ ✓ (1c —
  12 fichiers neufs, 36 tests, jar ART re-scanné vierge) ;
  ~~ré-estimation du programme de réécriture cahier en main~~ ✓ (1d —
  **`docs/PLAN-REECRITURE.md`** : 160–270 j-h, médiane 213, étages
  N1–N8+T, **approuvée le 28/10/2026 en séance** — §10 du plan : enveloppe
  approuvée, engagement borné N1 + banc croisé).
- **Prochaine étape — mesure sur appareil (porte d'entrée de N1, décision
  §10.2)** : exécuter `spike/appareil/banc-appareil.sh` sur un appareil
  réel (Termux/AndroidIDE) et retourner
  `spike/appareil/GABARIT-RESULTATS.md` rempli — aucune implémentation du
  noyau avant le retour.
- **Étage N1 — noyau moteur** (25 fiches, 124 comportements, 30–54 j-h :
  env/ecj/parse/diagnostics + `:testing` + bancs) puis, séquence confirmée
  (§10.3) : complétion (weighers, D8) / hover-inlay / confort →
  navigation/usages/rename/code actions → cycle de vie LSP — chaque étage
  borné par la parité mesurée contre l'ancien moteur (banc croisé, ADR
  0016) ; recalages d'estimation fin N1 et fin N2.

Détail : `docs/PLAN-REECRITURE.md` (programme complet) et
`docs/etudes/moteur-java-psi/00-synthese.md` §4 (historique du plan).
