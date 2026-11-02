# ADR 0101 — Périmètre du moteur Kotlin et frontières

- **Statut** : Proposé
- **Date** : 2026-10-30

## Contexte

L'ADR 0002 (domaine Java) a fixé le périmètre du dépôt à son lancement ;
la vision « dépôt officiel des LSP pour CodeIDE » (séance 28/10) implique
l'accueil du moteur Kotlin après décision dédiée (ADR 0100). Le moteur
Kotlin de l'ancien dépôt vit aujourd'hui couplé à `:lsp:host`
tri-langue (adaptateur LSP4J côté hôte, verrou partagé avec Java, vue
classpath partagée par la session Java) : la migration doit trancher où
vivent le transport, le pack langue et les frontières avec les modules
partagés.

## Sources consultées

- `codeide-lsp` @ `bc11253` : `docs/LSP_KOTLIN.md` §4/§6 (architecture
  T1, pack langue D6, SPI `MoteurLangageKotlin`), `docs/INTEGRATION-CODEIDE-LSP-KOTLIN.md`
  (routage sessions scripts/source sets), ADR 0213 (observateurs neutres).
- `code-lsp` : ADR 0002 (périmètre Java), module `:java` (façade
  `jo.codeide.lsp.java`, serveur LSP4J intégré N8), `:index`/`:model`
  repris, `USAGE.md`.
- Étude : `00b-inventaire-existant.md` §2.3 (dépendance hôte),
  `01-architectures-analyse-kotlin.md` §K2/K7.

## Options

**Option 1 — Module `:kotlin` autonome avec serveur LSP intégré**
(miroir `:java` N8) : le module porte le moteur ET le serveur LSP4J ET
le descripteur de pack ; l'app ne voit que le SPI/les artefacts.
**Option 2 — Moteur seul, transport dans l'hôte ancien** (statu quo de
l'architecture actuelle) : l'adaptateur LSP4J reste dans
`codeide-lsp:host` — perpétue la dépendance à l'hôte tri-langue,
contredit l'autonomie du dépôt.
**Option 3 — Ne rien changer** : pas de module `:kotlin` (voie D de
l'ADR 0100) — listée pour mémoire.

## Critères et mesures

- Précédent `:java` : le serveur LSP4J vit DANS le module (N8, cycle de
  vie LSP, protocole riche) — l'hôte consomme la façade.
- Condition pack téléchargeable (D6 ancien, ADR 0213) : le module ne
  doit exposer AUCUNE dépendance aux coroutines ni aux modules internes
  — le transport LSP4J est une dépendance de bibliothèque (EPL-2.0,
  déjà dans la chaîne), pas un couplage de module.
- Frontières partagées : `:model` (classpath v8 — déjà consommé par
  l'index), `:index` (CIDX — extension à arbitrer, Q-K1/K2).

## Décision proposée

**Option 1** : le module `:kotlin` de code-lsp porte le moteur, le
serveur LSP4J (miroir `:java`), la façade publique et le descripteur de
pack langue (`version.properties`, id/version/min-app/version de
grammaire — contrat D6 conservé). Frontières : `:model` consommé tel
quel (aucune évolution requise par le noyau — Q-K3 reste ouverte pour
le modèle de symboles) ; `:index` consommé via son API publique, toute
extension (clés taggées Kotlin, décodage `@Metadata`) passant par
`DEMANDES-INTERFACE.md` (arbitrage propriétaire) ; interop Java par une
interface publique documentée entre `:java` et `:kotlin` (Q-K5), jamais
par couplage interne.

## Conséquences

- Le module est autonome au build et à l'exécution ; l'hôte ancien
  continue de servir la production pendant la migration (repli D).
- La duplication éventuelle de l'adaptateur LSP4J (ancien hôte/nouveau
  module) est temporaire et bornée par la bascule.
- Le pack téléchargeable futur reste activable à coût quasi nul
  (descripteur dès la vague 0).

## Plan de migration progressive

Vague 0 : squelette `:kotlin` + descripteur de pack + CI ; le serveur
LSP4J entre au dernier étage (miroir N8 Java) une fois le noyau éprouvé
par le SPI.

## Tests de non-régression et critères de sortie

- `checkModuleDependencies` équivalent : aucune dépendance de module
  interne hors `:model`/`:index` (API publiques) ; LSP4J en
  bibliothèque.
- Parité des capacités annoncées avec la matrice ancienne (colonne
  Kotlin) — aucune capacité perdue (banc ADR 0119).

## Risques

- R1 — Divergence de façade avec `:java` (deux contrats de pack) :
  mitigation = gabarit commun de descripteur documenté en vague 0
  (Q-K4).
- R2 — Extension `:index` refusée par le propriétaire : repli = index
  dédié dans `:kotlin` (le format `.kidx` actuel est un candidat de
  reprise voie C, différentiel éprouvé).
