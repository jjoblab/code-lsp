# code-lsp

**Dépôt officiel des serveurs de langage (LSP) de CodeIDE.**

> Vision du propriétaire (28/10/2026) : un LSP Java « frais, plus solide, plus
> intelligent, plus robuste, plus propre et professionnel », grâce à
> l'architecture IntelliJ PSI — étudiée en profondeur, embarquée nulle part,
> greffée partout où elle prouve sa valeur.

## État — étage N1 (noyau moteur) LIVRÉ — recalage 1 SOUMIS — décision de suite attendue avant N2

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

**Étage N1 (29/10/2026) — noyau compilateur & environnement LIVRÉ** :
le module `:java` (moteur) est écrit **depuis le cahier de spécifications
uniquement** (clean-room intégral, 21 fichiers Kotlin neufs, KDoc français,
`explicitApi`, bytecode 17) : pilotage d'ecj 3.40.0 **patché** via `:ecj-art`
(bridge `Compiler.resolve` — arbres vivants, jamais nettoyés), routage de
classpath **sources → jars → jrt → stubs intrinsèques** (fiche 042) avec
FQCN **longest-match** (fiche 010), jrt runtime **ou externe** via
`jrt-fs.jar` + URLClassLoader/ServiceLoader (fiche 031), lecteur ZIP brut
propre (fiche 038 — parité octet à octet avec `ZipFile`), caches
présence/localisation/parse (fiches 006/024/025), **diagnose rapide par
corps de méthode** avec équivalence stricte contre témoin neuf (fiche 014),
chaîne de features à 3 niveaux — arbre focalisé / arbre réel / réduction
`SourceFocuser` (fiches 015/041), **repli syntaxe instantané** sans
plateforme (fiche 028), moteur **par module** avec routeur fichier→module
relu à chaque requête (fiches 034/047), ordonnanceur à worker unique avec
supersession par fichier + jeton d'annulation coopératif (fiches 048/046),
enrichissement de hiérarchie « absent du classpath : X (requis par Y) »
(fiche 019). **128 tests** (25 fiches N1 couvertes — les 6 dépendant de
l'environnement SDK/model.json s'ignorent proprement par hypothèses
visibles). Suite d'intégration `:testing` **réécrite en Kotlin neuf**
(mutation consignée au manifeste §3 : l'ancienne suite était couplée à
l'API vendored) : adaptateur `IndexNoms` sur le CIDX réel + 4 tests
(parité avec/sans index, autorité à PRÊT, filtre `$`, réouverture chaude).
**Banc empreinte/latence en CI** (`:java:bench`, corpus sans android.jar) :
diagnose complet p50 11,7 ms / p95 39,8 ms, **diagnose rapide p50 1,5 ms**
(frappe confinée — ~8× plus vite que le complet), hover p50 0,12 ms,
complétion de membres p50 0,04 ms, tas au repos 16 Mo (JDK 21 de bureau,
corpus 20 classes — nombres à comparer entre versions, aucun seuil
asserté avant le banc croisé ADR 0016).

## Structure du dépôt

| Chemin | Rôle |
|---|---|
| `docs/etudes/moteur-java-psi/` | étude d'architecture IntelliJ PSI (D1–D12), synthèse, mesures du spike, alertes, annexes |
| `docs/adr/` | ADR 0001–0018 (décisions d'architecture, statuts à jour) |
| `docs/cahier-specifications/` | cahier des comportements attendus (extraction clean-room des 449 tests) — le contrat de la réécriture |
| `docs/PLAN-REECRITURE.md` | **programme de réécriture chiffré** (étages N1–N8+T, fourchette 160–270 j-h) — **approuvé le 28/10/2026** (engagement borné N1 + banc croisé) ; **recalage 1 exécuté le 29/10** |
| `docs/RECALAGE-1.md` | **recalage de fin N1 (§8.2)** — vélocité mesurée, re-projection N2–N8, décision de suite soumise au propriétaire |
| `docs/PROVENANCE-REPRISE.md` | manifeste de reprise sélective (règle R9 — registre des copies autorisées) |
| `spike/` | bancs de mesure V1/V2/V3, scanner de compatibilité ART, gabarit appareil |
| `java/` | **moteur Java LSP réécrit — étage N1 LIVRÉ** (noyau compilateur & environnement, 21 f. Kotlin clean-room, 128 tests, banc `:java:bench`) |
| `testing/` | suite d'intégration moteur ↔ index CIDX — **réécrite en Kotlin neuf à l'étage N1** (adaptateur `IndexNoms` + 4 tests) |
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
./gradlew build                    # build + tests (290 tests : 38 modèle + 84 index + 36 ecj-art + 128 noyau + 4 intégration)
./gradlew :ecj-art:patcherEcj      # produit ecj-art/build/ecj-patche/ecj-3.40.0-art.jar
./gradlew :java:bench              # banc empreinte/latence du noyau N1 (JVM séparée)
```

La CI (`.github/workflows/ci.yml`) exécute le build, le banc du noyau et le
contrôle « anti-fichier-hors-registre » à chaque push.

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
- **Mesure sur appareil (porte d'entrée de N1, décision §10.2) — RETOURNÉE
  le 28/10/2026** : passe 14:33 (heure appareil) sur moto g06 / Android 15 /
  OpenJDK 17.0.19 (aarch64) — `spike/appareil/GABARIT-RESULTATS.md` rempli +
  transcript brut (commit 3c7158d), valeurs consignées telles quelles (R5).
  Passe sur JVM (Termux) — ART non testé (vague « spike ART ») ;
  compléments §5 non réalisés (optionnels).
- **Étage N1 (TERMINÉ le 29/10/2026)** : ~~noyau compilateur & environnement~~ ✓
  (25 fiches couvertes, 128 tests noyau + 4 intégration, `:testing` réécrite,
  banc empreinte/latence en CI — détail en tête de page).
- **Recalage 1 (fin N1, §8.2) — SOUMIS le 29/10/2026** : ~~vélocité mesurée,
  re-projection N2–N8~~ ✓ (`docs/RECALAGE-1.md`) — N1 livré en 1 séance
  continue (~6 h agent) ; densités mesurées sous le postulat (34,3 l. prod /
  comportement) ; projection N2–N8+T : **6–9 séances effectives** en
  exécution agent, fourchette humaine 160–270 j-h maintenue (R5) ;
  **décision de suite (lancer N2 ?) attendue du propriétaire**.
- **Étape suivante — N2 complétion & classement** (9 fiches, 66
  comportements : chaîne de weighers D8, split RPC, banc qualité
  recall/top-1/MRR), au retour de la décision de suite ; puis, séquence
  confirmée (§10.3) : N3/N7 →
  N4–N6 → N8 — chaque étage borné par la parité mesurée contre l'ancien
  moteur (banc croisé, ADR 0016) ; recalage d'estimation fin N2.

Détail : `docs/PLAN-REECRITURE.md` (programme complet) et
`docs/etudes/moteur-java-psi/00-synthese.md` §4 (historique du plan).
