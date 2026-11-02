# ADR 0112 — Diagnostics : fidélité, faux positifs, mode profond à la demande

- **Statut** : Proposé
- **Date** : 2026-10-30

## Contexte

K9 du prompt : « Quelles familles sont sûres sans K2, lesquelles
l'exigent ? » Le critère §4 du prompt est explicite : **zéro faux
positif toléré — preuve ou silence**. L'audit 25/10 démontre le contrat
tenable (quatre mondes à zéro faux positif) ; le spike mesure le coût
du complément K2 (3,5 s d'amorçage, 53,6 Mo [mesurés]).

## Sources consultées

- Cahier : fiches diagnostics (204 comportements — le catalogue
  exact des familles éprouvées), dont 046 (K4 sémantiques), 017/018
  (par déclaration), 019/023/024/026/027/029/031/033/036/037/038/040
  (reproductions), 006 (journal explain).
- Étude : `01-architectures-analyse-kotlin.md` §K9 + conclusion
  obligatoire (décidable vs unification).
- Spike : `02-spike-mesures.md` §5.2/§5.3.
- `code-lsp` : ADR 0013 Java (fidélité compilateur — transposition
  inversée : ici le compilateur est l'ORACLE, pas le moteur).

## Options

**Option 1 — Catalogue conservateur + banc de fidélité face à K2 +
mode profond différé.** Les familles décidables (structure, imports,
arité prouvée, nullabilité typée, absence prouvée) restent de frappe ;
le reste sous-rapporte ; un mode profond (K2 en tâche explicite,
3,5 s annoncés, éventuellement pack dédié) sert la « vérité
compilateur » à la demande. **Option 2 — Embarquer K2 pour les
rouges** : disqualifié (ADR 0103). **Option 3 — Sous-rapport global
sans mode profond** : perd la demande «.Android Studio sur appareil »
en profondeur — le mode différé reste la soupape. **Option 4 — Ne rien
changer.**

## Critères et mesures

- Familles sûres = celles du cahier (décidabilité démontrée par les
  fiches) ; familles K2 = unification (variance, spécificité,
  inférence imbriquée) — frontière de l'étude §K9.
- Le journal « explain » (fiche 006) reste l'instrument de diagnostic
  des émissions (FINE, jamais actif en production par défaut).

## Décision proposée

**Option 1** : catalogue conservateur (parité cahier), banc de fidélité
(ADR 0119) mesurant faux positifs/négatifs face au compilateur K2 en
oracle de sous-processus sur corpus fixe (le même banc sert le mode
profond s'il est activé) ; mode profond = extension à la demande
(drapeau off, pack éventuel, délai annoncé à l'utilisateur), jamais sur
le chemin de frappe. La garde « par déclaration » (fiches 017/018 :
une zone brisée ne coupe que sa preuve) est conservée comme invariant
d'édition.

## Conséquences

- La couverture de sous-rapport devient une donnée PUBLIÉE du banc
  (par famille), pas un ressenti.
- Le mode profond, s'il vient, vit derrière une frontière de pack
  (53,6 Mo hors APK principal — ADR 0120).

## Plan de migration progressive

Parité catalogue d'abord ; banc de fidélité dès l'étage diagnostics ;
mode profond seulement sur besoin démontré (retours appareil).

## Tests de non-régression et critères de sortie

- 204 fiches diagnostics vertes ; zéro faux positif corpus quatre
  mondes ; vrais négatifs toujours signalés ; couverture de
  sous-rapport publiée.

## Risques

- R1 — Mode profond jamais construit, frustrations résiduelles :
  mitigation = la couverture mesurée documente honnêtement l'attente.
- R2 — Dérive du catalogue vers des familles non décidables :
  mitigation = chaque famille passe la preuve de décidabilité (revue
  + banc).

---

## Amendement 2026-10-31 — mission « niveau Android Studio » (ADR 0124 et ADR 0125, statut Proposé)

Deux évolutions proposées. (1) **Le catalogue reste à 10 familles TANT QUE la baseline de faux positifs n'est pas sous garde** : la mesure V0 du 31/10/2026 (980 FP / 34 934 l. = 28,05 FP/1 000 l. sur l'auto-corpus, FN = 0 — le banc de fidélité de 86 lignes ne les voyait pas) devient le critère d'entrée de TOUTE extension : cible < 0,5 FP/1 000 l. avant d'ajouter un code (ADR 0125). (2) **Le mode profond est instruit** : l'option « pack dex chargé à la demande » est rejetée sur la mesure S1 (53,6 Mo / 64 507 méthodes) ; l'option « processus déporté (JVM Termux/PRoot) » est à instruire avec deux mesures (passe gabarit 07 enrichi + confirmation hôte Q-K7) — l'oracle du banc de fidélité, qui exécute déjà K2 en sous-processus, en est le germe (ADR 0124). Le verdict appartient au propriétaire.
