# ADR 0002 — Périmètre du dépôt et modules : reprendre, réécrire, abandonner

- **Statut** : Proposé
- **Date** : 2026-10-28

## Contexte

Le nouveau dépôt doit accueillir un **moteur Java de niveau IDE** pour CodeIDE (R8 : moteur Java uniquement — les moteurs Kotlin et XML restent dans `codeide-lsp`). La question du périmètre (moteur seul, ou avec hôte/index/modèle) est une conclusion à proposer, pas un présupposé (§4 bis du prompt). L'inventaire Phase 0 (`00b-inventaire-reprise.md`) établit rôle, taille, dépendances, tests et statut de provenance de chaque module des anciens dépôts ; le manifeste `docs/PROVENANCE-REPRISE.md` tient les registres.

Faits structurants :

- `:lsp:host` (51 f / 21 071 l.) orchestre **trois langues** (Java, Kotlin, XML) — il ne peut pas migrer sans casser R8.
- `:lsp:index` (CIDX, 33 f / 6 472 l.) est le socle que l'étude D7 recommande de **conserver** : il bat le design IntelliJ sur les axes mobiles mesurables (préfixe binaire p50/p95 0,16–0,76/1,1–7,1 ms ; +1 dépendance : seul le jar nouveau, 85/89/87 ms ; budget octets 4,2/2,9/29,7 Mo ; corruption : segment seul reconstruit).
- `:lsp:model` (11 f / 2 160 l.) consomme le protocole tooling (sans LICENSE — vigilance ADR 0001).
- Le moteur `lsp/java` (exclu par défaut R9.3) est conditionné à l'ADR 0001 ; l'étude recommande la voie hybride (ADR 0004) qui le **conserve comme socle**.

## Sources consultées

- `jjoblab/codeide-lsp` @ `cb809e14` (v1.16.0), lecture seule : README (module par module), `settings.gradle.kts`, code de `lsp/*` (comptages `rg --files | wc -l`, `xargs wc -l`), ADR 0108/0109/0110/0111/0200, `docs/LSP_JAVA.md`, `docs/LSP_MATRICE_CAPACITES.md`, `docs/THIRD_PARTY_NOTICES.md`, plan de stabilisation.
- `jjoblab/CodeIDE` @ `54e99c6` (v0.202.0), lecture seule : `settings.gradle.kts` (substitution composite des 8 artefacts `com.github.jjoblab.codeide-lsp:*`, lignes 226–251 ; « `feature:editor` ne voit que `:lsp:host` » :349).
- `jjoblab/code-editor` @ `066b8c0`, lecture seule : `cel-lsp` (Apache-2.0, LICENSE+NOTICE présents).
- Étude Phase 1 : D7 (index), D11 (hôte/scheduler), conclusions `01-architecture-psi.md`.

## Options

**Option 1 — Périmètre « domaine moteur Java »** : `model` + `index` + `testing` + moteur Java (selon ADR 0001), exposés par la façade API publique existante (`jo.lspjava.api.*`) ; l'hôte reste dans `codeide-lsp` en consommateur d'artefacts.

**Option 2 — Moteur Java seul** (sans index ni modèle) : le moteur consomme des interfaces abstraites ; l'index CIDX resterait dans l'ancien dépôt. Crée une frontière artificielle : le moteur et l'index sont co-designés (ADR 0111 ; recherche d'usages, complétion de types, +1 dépendance).

**Option 3 — Tout reprendre** (moteur + hôte + kotlin + xml) : contredit R8 et la logique de périmètre ; l'hôte tri-langue n'a pas de sens dans un dépôt « moteur Java ».

**Option 4 — Ne rien changer** : tout reste dans `codeide-lsp`, le nouveau dépôt ne contiendrait que l'étude. Rejette la mission (§0) ; listée pour mémoire.

## Critères et mesures

- Tableau module par module avec verdicts et justifications : `00b-inventaire-reprise.md` §1–§3.
- Valeur démontrée de CIDX face au design IntelliJ : `01-architecture-psi.md` §D7 (tableau comparatif) ; annexe `annexes/exp-D7D8.md` §D7.6.
- Couplage moteur↔index : `:lsp:testing` (4 f / 1 464 l., zéro dépendance externe publiée) existe précisément pour cette parité.
- Empreinte de l'option 1 : ~2 160 + 6 472 + 1 464 l. de couches candidates + moteur conditionné (≈25 839 l. core) ≈ **36 000 lignes** reprises contrôlées — à comparer aux ~75 000 lignes de l'hôte tri-langue laissées sur place.

## Décision proposée

**Option 1**. Le nouveau dépôt porte le **domaine moteur Java** : `:index` (CIDX), `:model`, `:testing`, le moteur Java (reprise conditionnelle ADR 0001 ou réécriture) et les modules d'étude (`docs/`, `spike/`). L'hôte, les moteurs Kotlin/XML et tout code CodeIDE/code-editor restent dans leurs dépôts (intégration par consommation d'artefats, comme aujourd'hui via GitHub Packages / substitution composite). Condition suspensive : éclaircissement de la licence du protocole tooling (ADR 0001/0017) pour la reprise de `:model`.

## Conséquences

- Le nouveau dépôt est **autonome au build et à l'exécution** (R3) : aucune dépendance de build vers les anciens dépôts.
- La façade publique du moteur devient le contrat de l'hôte : toute capacité LSP nouvelle se négocie d'abord dans la matrice de capacités (ADR 0015).
- `:lsp:testing` migre avec le couple moteur↔index (scénarios de parité).
- La publication d'artefacts (GitHub Packages) devra être mise en place dès la vague 0 (CI de publication).

## Plan de migration progressive

1. **Vague 0** : squelette Gradle minimal + CI + publication d'artefacts vides + branchement de substitution dans CodeIDE (l'hôte consomme le nouvel artefact en DEV).
2. **Vague 1** : reprise contrôlée `index` → `model` → `testing` (manifeste), moteur selon ADR 0001 ; double publication (ancien/nouveau) et bascule de la substitution composite CodeIDE derrière un drapeau.
3. **Retour arrière** : à chaque vague, la substitution composite de CodeIDE permet de revenir aux artefacts de `codeide-lsp` en inversant une ligne de configuration — aucun verrou technique avant la suppression de l'ancien dépôt de publication.

## Tests de non-régression et critères de sortie

- Critère de sortie vague 1 : les 449 tests du moteur + suites index/model/testing verts sur le nouveau dépôt (comportements conservés, ADR 0016) ; l'hôte consomme l'artefact neuf en DEV sans changement fonctionnel.
- Critère de sortie définitif : `codeide-lsp` ne publie plus `com.github.jjoblab.codeide-lsp:{java,index,model,testing}` (drapeau de bascule retiré).

## Risques

- **R1 — Double maintenance moteur pendant la migration** : mitigation = bascule à un seul artefact derrière drapeau, fenêtre courte (une vague).
- **R2 — Le protocole tooling reste sans licence** : mitigation = action propriétaire (ajout LICENSE) ou réécriture des types consommés avant la reprise de `:model`.
- **R3 — Perte de la vue tri-langue** : l'hôte continue de l'assumer dans l'ancien dépôt ; la façade moteur Java n'expose que Java (R8).
