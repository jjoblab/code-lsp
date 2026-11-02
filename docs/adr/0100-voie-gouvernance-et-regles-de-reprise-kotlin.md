# ADR 0100 — Décision de fond : voie de gouvernance A/B/C/D et règles de reprise Kotlin

- **Statut** : **Accepté** — décision du **propriétaire** reçue dans le canal de mission le 2026-10-30 : « Je t'autorise à commencer les étapes, tu dois travailler uniquement sur ta branche » — **option C — hybride** (la seule voie GO proposée par la synthèse §1 ; le démarrage des étages du plan §4 vaut acceptation de la recommandation). L'exécution démarre par l'étage 0 (amorçage) sur la branche `etude/moteur-kotlin` uniquement. L'option D demeure le repli permanent.
- **Date** : 2026-10-30 (rédaction initiale — proposée) / 2026-10-30 (acceptation propriétaire — exécution ouverte)

## Contexte

`settings.gradle.kts` annonce que « les moteurs Kotlin/XML migreront
depuis `codeide-lsp` après décision dédiée » : la présente ADR porte les
éléments de cette décision. Le moteur Kotlin actuel
(`codeide-lsp:lsp/kotlin` @ `bc11253`, 21 197 l. propres / 57 f. +
grammaire vendorée 9 585 l. / 23 f., 469 tests) est **en production et
jugé « déjà pas mal »** (prompt §0) : audit 25/10 à zéro faux positif sur
les quatre mondes Android. Il n'est **pas frappé de l'exclusion R9.3**
(code propre jjoblab + vendoring Apache-2.0 attribué) — la situation
diffère structurellement du moteur Java vendored (contestation de
paternité, ADR 0001 Java). La question est donc architecturale, pas
juridique : comment donner à Kotlin les bases plus solides du dépôt
code-lsp (bancs de qualité, classement, CI dex, designs D8–D12) au
meilleur rapport coût/risque/valeur. Comparaison à armes égales :
`00b-inventaire-existant.md` §5 (matrice complète).

## Sources consultées

- `jjoblab/codeide-lsp` @ `bc11253` (lecture seule) : `docs/LSP_KOTLIN.md`, rapports d'audit et faux positifs, ADR 0094–0233, `lsp/kotlin/src/test/**` (53 fichiers — base du cahier), métriques d'outil (`00b-inventaire-existant.md` §1).
- `jjoblab/code-lsp` @ `etude/moteur-kotlin` : cahier `docs/cahier-specifications-kotlin/` (53 fiches, 469 comportements), étude amont `01-architectures-analyse-kotlin.md`, spike `02-spike-mesures.md`, manifeste `docs/PROVENANCE-REPRISE-KOTLIN.md`.
- `JetBrains/kotlin` @ `e3806c8f` (lecture seule, Apache-2.0) : tailles et conception (§0 de l'étude).
- Précédent Java : ADR 0001/0002/0004 du dépôt (réécriture intégrale, 160–270 j-h, `PLAN-REECRITURE.md`).

## Options (armes égales — coût [estimé], méthode §5 de l'inventaire)

**Option A — Réécriture intégrale clean-room** (miroir Java) : moteur
neuf dans `:kotlin` depuis le cahier (469 comportements), designs Java
intégrés en naissance (weighers, ModCommand, rename par phases, verrous
par périmètre), oracle = artefact publié 1.22.1 en boîte noire.
**+** base saine, alignement total (bancs/CI), aucune question de
provenance même future. **−** coût maximal (**170–290 j-h**), double
existence prolongée, durcissement implicite à re-découvrir (24 familles
Reproduction*). Provenance : propre par construction.

**Option B — Reprise contrôlée intégrale** : copie des 57 fichiers
(hors grammaire ré-obtenue amont) après contrôle R9.4 fichier par
fichier, inscription préalable au manifeste Kotlin, puis consolidation
(casse des gros fichiers, greffe des designs Java).
**+** valeur immédiate, coût minimal (**90–150 j-h**). **−**
l'architecture « correctif par correctif » migre avec le code ;
`DiagnosticsSemantiques` 1 784 l. et `SessionLangageKotlinParse`
1 241 l. restent à refondre ; l'alignement bancs/CI reste à construire.
Provenance : registre à tenir (R9.2), propre mais auditable.

**Option C — Hybride : noyau réécrit + modules sains repris** : noyau
(parse, résolution, session, diagnostics, complétion+classement) réécrit
depuis le cahier avec les designs Java en naissance ; modules
différenciants sains et bornés repris après contrôle :
`synthetics/` (667 l.), `preview/` (522 l.), `classpath/` (664 l.),
`format/`+`plis/`+`documentation/`+`symboles/` (772 l.).
**+** la valeur architecturale là où le gain est réel (le cœur
heuristique devient spécifié et bancé) ; la valeur immédiate là où elle
est prouvée (capacités différenciantes testées E2E) ; coût médian
(**110–190 j-h**). **−** deux régimes de provenance dans un module
(registre le plus fin) ; frontières à spécifier précisément (cette ADR).

**Option D — Statu quo amélioré** : le moteur reste dans `codeide-lsp`,
correctifs ciblés (H1 troncation, H2 sources générées, builtins,
`Enum.entries` — étape B du rapport faux positifs, **30–60 j-h**).
**+** zéro migration. **−** contredit la vision « code-lsp = dépôt
officiel des LSP » (ADR 0002) ; les gains d'architecture (bancs,
classement, parité mesurable K2) ne s'appliquent jamais ; l'hôte
tri-langue reste le patron.

## Critères et mesures

- Matrice coût/risque/provenance : `00b-inventaire-existant.md` §5.
- Valeur démontrée du cœur actuel : zéro faux positif quatre mondes [rapport d'audit] — mais couverture sémantique arbitraire (24 familles de reproduction = construction correctif par correctif) [cahier, §2.3 de l'inventaire].
- Faisabilité technique de toute voie : le socle partagé (`:index`, `:model`, CI) existe et est vert (122 tests) ; la grammaire amont compile tel quel [mesuré, spike §5.1].
- Empreintes : oracle 0,86 Mo / 0 motif ART / reparse 3,4 ms — les cibles §4 sont tenables dans toutes les voies [mesuré, spike §4].

## Décision proposée

**Option C — hybride**, avec frontières exactes : **réécriture**
(cahier en main, designs Java en naissance) : `parse/`, `resolve/`,
`diagnostics/`, `completion/`, `session/`, `api/` ; **reprise contrôlée
après R9.4** (inscription préalable au manifeste) : `synthetics/`,
`preview/`, `classpath/`, `format/`, `plis/`, `documentation/`,
`symboles/` ; **ré-obtention amont** (jamais reprise de l'ancien dépôt) :
la grammaire vendorée au tag épinglé. Motif : le risque juridique étant
nul, la réécriture complète n'achète que de la pureté de méthode ; la
valeur réelle est dans le noyau (spécification dérivée, bancs,
classement) — y concentrer l'investissement, reprendre le reste. **La
décision appartient au propriétaire** : toute autre voie est documentée
et exécutable (les livrables sont neutres : cahier, manifeste, spike).

## Conséquences

- Si C : deux régimes de provenance — le manifeste Kotlin devient le
  document d'audit central ; la CI anti-fichier-hors-registre devra être
  étendue à `kotlin/` (vague 0).
- Le cahier (469 comportements) est le contrat des voies A et C ;
  l'oracle boîte noire (artefact 1.22.1) reste la référence de parité
  pour toutes les voies.
- La voie D reste le repli permanent (aucune dégradation pendant
  l'étude — le moteur actuel sert la production).

## Plan de migration progressive

1. **Vague 0** (après décision) : amorçage `:kotlin`, extension du
   `verifier-provenance.sh`, CI (dex/scan ART/seuils), lecture du
   manifeste Kotlin, reprise des modules C (inscription préalable).
2. **Étages noyau** (cahier en main, séquence miroir Java N1→N2…) :
   parse/session → diagnostics → complétion+classement → navigation,
   chaque étage borné par le banc de parité croisé (oracle 1.22.1) et
   les latences §4.
3. **Drapeau de fonctionnalité** dans l'hôte (pack ancien/nouveau par
   descripteur `version.properties`) ; retour arrière documenté à chaque
   étage ; bascule uniquement à parité du cahier **mesurée sur appareil**.

## Tests de non-régression et critères de sortie

- Parité du cahier : 469/469 comportements verts contre l'oracle (banc
  croisé, ADR 0119) ; zéro faux positif sur les corpus quatre mondes
  (reprise des corpus E2E anciens).
- Cibles §4 : complétion < 100 ms (fichier ≤ 2 000 l., index chaud),
  premier diagnostic < 250 ms, tas plat, APK ≤ 4 Mo — sur appareil
  (gabarit), la mesure JVM étant limite basse.

## Risques

- **R1 — Frontière C mal spécifiée** (module repris qui depend du noyau
  réécrit) : mitigation = cartographie des dépendances AVANT copie
  (l'inventaire §3 la documente déjà par zone).
- **R2 — Sous-estimation du noyau** : mitigation = étages bornés,
  recalages (miroir §8 du plan Java).
- **R3 — Double existence prolongée** : mitigation = drapeau pack,
  repli D permanent.
- **R4 — Dérive de périmètre** (réécrire « presque tout ») : mitigation
  = le registre de reprise est le garde-fou contractuel de la voie C.
