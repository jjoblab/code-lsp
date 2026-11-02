# ADR 0111 — Gradle Kotlin DSL (`.gradle.kts`)

- **Statut** : Proposé
- **Date** : 2026-10-30

## Contexte

K8 du prompt : « Quelle stratégie réaliste (accesseurs générés,
classpath du script) ? » L'ADR 0221 ancienne a réglé les faux positifs
(nus muets — récepteur implicite = contexte hôte ; qualifiés
faillibles) ; l'apport du classpath Gradle réel (3 jars + AGP) est
documenté côté CodeIDE (INTEGRATION-CODEIDE §1) ; les accesseurs générés
(`android { }`) restent hors modèle (Gradle les génère par projet —
aucune classe lisible dans un jar).

## Sources consultées

- Cahier : fiche 032 (`ReproductionGradleKtsTest` — 5 comportements
  dont les gardes D3).
- `codeide-lsp` : ADR 0221, `docs/INTEGRATION-CODEIDE-LSP-KOTLIN.md` §1
  (routage session scripts, provisionnement des jars).
- Grammaire amont : mode SCRIPT présent (`parseScript()` [mesuré dans
  le vendoring]).

## Options

**Option 1 — Régime asymétrique + session scripts avec classpath
Gradle** (statu quo documenté : nus muets, qualifiés prouvés par le
classpath, Kotlin standard du script servi). **Option 2 — Modèle DSL
Gradle complet** (typage des récepteurs injectés, accesseurs générés
par projet) : exige un modèle Gradle exécuté — hors de portée in
process (miroir T2, coûts compilateur) — écarté.
**Option 3 — Taire tout diagnostic dans les scripts** : perd la garde
qualifiée — écarté (option 3 de l'ADR 0221). **Option 4 — Ne rien
changer.**

## Critères et mesures

- Fiche 032 : 0 faux positif sur les deux scripts canoniques du
  template ; typo qualifiée toujours signalée ; Kotlin standard du
  script résolu.

## Décision proposée

**Option 1** : régime asymétrique conservé à l'identique ; le
provisionnement du classpath Gradle par session dédiée (routage côté
hôte — document INTEGRATION repris) reste la seule extension retenue ;
les accesseurs générés restent HORS modèle (documenté comme borne
honnête). Les `.main.kts` partagent le régime (noms nus muets).

## Conséquences

- Zéro faux positif maintenu ; la complétion dans les blocs DSL reste
  sous-rapportée (documentée).
- Le compromis D3 (typo nue non signalée dans un script) est assumé
  et documenté (fiche).

## Plan de migration progressive

Le mode script entre avec l'étage parse (fiches 012/015) ; le régime
asymétrique avec les diagnostics.

## Tests de non-régression et critères de sortie

- Fiche 032 verte ; corpus scripts du template sans faux positif.

## Risques

- R1 — Attente utilisateur de complétion DSL : mitigation = demande
  d'interface pour le routage classpath (document INTEGRATION §1) ;
  la complétion dans les blocs reste un horizon (modèle Gradle T2).

---

## Amendement 2026-10-31 — mission « niveau Android Studio » (ADR 0130, statut Proposé)

L'option 1 (régime asymétrique, accesseurs générés HORS modèle, provisionnement = routage hôte) est **RÉALISÉE par l'ADR 0130**, étendue d'un volet que cette ADR ne couvrait pas : la **lecture DISQUE** de `libs.versions.toml` et de `build.gradle.kts`/`settings.gradle.kts` (parseurs tolérants, SANS synchronisation Gradle) pour la complétion, la navigation et des diagnostics bornés (clé inconnue du catalogue, version en doublon). C'est l'axe de dépassement n° 1 de la mission (intelligence avant la première sync) : la source de vérité est le disque, ce que la sync ajoute (variantes, accesseurs) reste hors périmètre moteur.
