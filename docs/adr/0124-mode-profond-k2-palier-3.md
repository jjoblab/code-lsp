# ADR 0124 — Mode profond K2 (palier 3) : options mesurées, verdict différé

- **Statut** : Proposé
- **Date** : 2026-10-31
- **Amende** : ADR 0112 (mode profond jamais construit), ADR 0103 (option iii)
- **Écarts** : GK-005 (KH5)

## Contexte

L'ADR 0112 a laissé le mode profond « extension à la demande (drapeau off) »
**jamais construit** ; il n'existe aucune soupape de fidélité compilateur.
Le prompt exige d'instruire trois options avec des chiffres avant de
recommander. Fait porteur : l'**oracle K2 tourne déjà en sous-processus dans
la CI du dépôt** (`BancFideliteK2Test`, `kotlin-compiler-embeddable` en
testImplementation) et le kit appareil exécute déjà une JVM dans Termux sur
moto g06 (`scripts/kit-bascule.sh`) — la voie « processus déporté » a donc un
précédent mécanique chez nous.

## Sources consultées

- Spike S1 [mesuré] : pile `kotlin-compiler-embeddable` 2.2.10 = **53,6 Mo /
  25 019 classes / 64 507 méthodes / ~2 170 motifs sensibles ART / amorçage
  3 503–3 802 ms** ; S3 : hybride composé, pas de prototype intégré.
- Journal 09 (kit appareil Termux, OpenJDK 17) ; doc 11 §2.2 (passe appareil
  optionnelle au kit enrichi).
- Référence §A1 (le mode K2 de l'IDE de bureau embarque le front-end — nous
  ne pouvons pas : §C de la référence, non-dépassement assumé).

## Options

| Option | Description | Critères mesurés / à mesurer |
|---|---|---|
| **1 — Pack dex chargé à la demande** | sous-ensemble K2 dexé, chargé à la demande | **rejetée par les chiffres** : même un sous-ensemble utile du front-end dépasse le budget (S1 : 64 507 méthodes pour la pile complète ; le mur ART est 65 536 par dex) — à re-réviser seulement si l'amont publie un front-end modulaire [NON VÉRIFIÉ] |
| **2 — Processus déporté (JVM Termux/PRoot) (à instruire)** | le compilateur VÉRITABLE tourne hors processus, sollicité à la demande (fichier + classpath → diagnostics/résolutions) | amorçage mesuré 3,5 s côté JVM bureau [CONFIRMÉ S1] ; latence d'aller-retour et mémoire sur appareil **[à mesurer, gabarit 07 enrichi]** ; prérequis : l'hôte CodeIDE dispose d'un environnement Termux/PRoot — **à confirmer côté hôte (Q-K7)** |
| **3 — Démon résident avec cycle de vie** | variante de 2 : le processus reste chaud | gagne l'amorçage après le 1er appel ; coût mémoire résident (tas JVM complet sur l'appareil) **[à mesurer]** ; UX du délai à concevoir (annonce honnête, annulation) |
| **4 — Rejet** | pas de palier 3 | le palier 2 borne le plafond de fidélité ; les frustrations résiduelles restent (doc 11 §2.5) |

## Critères et mesures

- Jamais sur le chemin de frappe ; activation **par l'utilisateur** ;
  délai annoncé, annulable, repli palier 1/2 immédiat ; provenance
  `palier 3` étiquetée (ADR 0122) ;
- seuils proposés pour RETENIR l'option 2/3 : premier diagnostic profond
  < 10 s après activation [cible, à valider], diagnostic chaud < 2 s,
  mémoire additionnelle < 300 Mo, aucune régression des cibles §0.1 ;
- le protocole est déjà écrit : l'oracle du banc (sous-processus K2) EST le
  mode profond en germe — la vague V-H le transforme en service optionnel
  derrière la même frontière.

## Décision proposée

**Instruire l'option 2 (avec sa variante démon 3) et rendre un verdict
chiffré après deux mesures** : (a) passe gabarit 07 enrichi sur moto g06
(amorçage, latence, mémoire), (b) confirmation hôte de l'environnement
déporté (Q-K7). L'option 1 est rejetée sur la mesure S1. Si (a) ou (b)
échoue : **rejet motivé** et le palier 2 reste le plafond de fidélité —
la décision finale appartient au propriétaire (§10).

## Conséquences

- Aucun travail d'implémentation avant l'arbitrage (STOP §10) ;
- le banc V0 P1(b) fournit déjà le protocole d'échange (fichiers + jars →
  erreurs) qui serait réutilisé tel quel.

## Plan de migration progressive

V-H (dernière vague, si arbitrée) : service profond optionnel + UX du délai
+ garde-fous mémoire + mesures gabarit 07.

## Tests de non-régression et critères de sortie

- Cibles de l'option (premier < 10 s, chaud < 2 s, mémoire < 300 Mo) ;
- zéro régression des cibles §0.1 du chemin de frappe ; CI inchangée quand
  le service est éteint.

## Risques

- R1 — L'hôte ne dispose pas d'environnement déporté : le verdict devient
  « rejet » — le palier 2 assume seul la fidélité ;
- R2 — Thermique/batterie sur appareil : démon à extinction différée ;
- R3 — Divergence oracle/moteur trompeuse : le palier 3 dit VÉRITÉ, la
  fusion des sources de diagnostics doit être explicitement étiquetée.
