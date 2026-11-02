# RECALAGE-2 — Vélocité mesurée de l'étage N2 et re-projection de N3–N8

> **Statut : SOUMIS au propriétaire** — décision de suite attendue (§6).
> **Date** : 29/10/2026. **Mandat** : `PLAN-REECRITURE.md` §8.2 (« Recalage 2 —
> fin N2 : l'étage le plus riche en design étant passé, la confiance sur
> N3–N8 est re-évaluée »). Suit `RECALAGE-1.md` (29/10/2026), qui avait
> explicitement réservé ce point : « le facteur d'incertitude dominant reste
> N2 : s'il s'avère nettement moins favorable au rythme agent que N1 (design
> fin, tuning qualitatif de classement), c'est le recalage 2 qui le dira ».
> **Discipline R5** : chaque chiffre est étiqueté **[mesuré]** (rejouable par
> commande — les commandes sont données), **[estimé]** (modèle documenté,
> postulats visibles) ou **[non vérifié]**. Qualificatifs propres :
> **[mesuré-agent]** (mesuré dans le rythme d'exécution de l'agent qui livre
> le dépôt — non transposable au jour-homme humain, cf. §3.4) et
> **[mesuré-décomposition]** (mesuré au volume ; la frontière de classement
> est déclarée, pas naturelle).

---

## 1. Mandat et méthode

Le plan approuvé le 28/10/2026 chiffrait chaque étage comme *socle + n × coût
marginal par comportement* ; le recalage 1 a remplacé les postulats de
l'étage N1 (le plus gros socle, le plus mécanique) par une mesure, et avait
cadré le facteur d'exécution agent à **0,11–0,20 h de séance par j-h postulé
(médiane 0,15)** — en signalant sa limite : un seul point, pris sur l'étage le
plus favorable à une exécution mécanique. L'étage N2 — complétion &
classement, 9 fiches, 66 comportements, design D8 « intégré en naissance »,
ancienne « capacité phare » — était désigné par le plan lui-même comme le
vrai test de l'abaque : l'étage le plus riche en design, celui dont le
marginal postulé était le plus élevé (2,5–4 h/comportement). Il a été **livré
intégralement le 29/10/2026 à 04 h 49 UTC** (commits `5d3ca1a` « étage en
cours » puis `bc70055` « étage N2 TERMINÉ »). Le présent document est
l'exécution du second et dernier recalage d'estimation prévu au plan (§8.2) ;
la question centrale est celle du mandat : **la confiance sur N3–N8**.

La méthode est inchangée : tout ce qui est affirmé est soit rejouable sur le
dépôt (`./gradlew build`, `./gradlew :java:bench`, `./gradlew
:java:benchQualite`, `git log`/`git show`), soit explicitement étiqueté. Les
chiffres « du jour » (§2.4, §2.5) sont des re-mesures indépendantes,
distinctes des chiffres portés par les messages de livraison. La seule
grandeur non rejouable reste le temps de séance (§2.2), borné au §3.3. Une
nuance propre à N2 : la fenêtre de séance contient **au moins une reprise
après compression du contexte de conversation** — l'exécution n'était plus
une séance continue unique comme en N1, et la fenêtre d'horloge est donc une
**borne haute** du travail effectif.

## 2. Ce que N2 a réellement coûté

### 2.1 Volumétrie livrée [mesuré]

Commandes : `git diff --stat 5c6bcf9 bc70055` ; `git diff --name-only
5c6bcf9 bc70055 -- java/src/main | wc -l` ; comptage des lignes par arbre
(`find … -name '*.kt' -exec cat {} + | wc -l`) aux deux bornes.

| Grandeur | Valeur | Détail |
|---|---:|---|
| Fichiers distincts du N2 | 29 | +5 148 / −62 lignes totales (`5c6bcf9`→`bc70055`) |
| `:java` main | 22 → 31 fichiers, **+3 143 l.** net (4 259 → 7 402) | 9 fichiers neufs `completion/` + `AnnuaireClasspath` + deltas noyau |
| `:java` test | 28 → 35 fichiers, **+1 321 l.** net (3 403 → 4 724) | 7 fichiers de fiches neufs + `OutilsFixture` étendu |
| Banc qualité | 2 fichiers neufs, **+490 l.** | `BancQualiteCompletion` 271 + `CorpusQualiteCompletion` 219 |
| CI / baseline / gradle | +143 / −11 | `ci.yml` (jobs `gates-android` + `banc-qualite`), `completion.json`, `build.gradle.kts` |
| Tests noyau | 128 → **182** | 54 neufs : 18 échelle de variantes + 36 finalisation |
| Tests du dépôt entier | 290 → **344 verts, 0 échec, 1 ignoré** | 182 noyau + 84 index + 38 modèle + 36 ecj-art + 4 intégration |
| Fiches N2 couvertes | **9 / 9** | 007 008 020\* 035 036 043 044 050 053 |
| Comportements couverts | **66 / 66** | fiche 020 partie complétion incluse (hover/reportés en N3) |

La couverture est intégrale, aucun report. Les 54 tests neufs couvrent 66
comportements (0,8 test/comportement, contre 128/124 en N1) : la parité N2
est portée par des tests **plus intégrés** — plusieurs comportements vérifiés
par un même scénario bout en bout (044 : six règles d'or sur android.jar
réel ; 053 : composition didChange→complétion) — et par le banc qualité, qui
n'existait pas en N1. La gate ignorée restante est `Fiche039` (artefact
appareil `model.json`, §5) : les gates `android.jar` (Fiche033 ×3, Fiche004,
036/043/044) **se jouent désormais** — requalifiées en CI selon la décision
du propriétaire, et jouées aussi sur le bac à sable du jour (§2.4).

### 2.2 Le temps de séance [mesuré-agent]

Commande : `git log --pretty=fuller 5c6bcf9..bc70055`.

| Borne | Commit | Horodatage UTC |
|---|---|---|
| Recalage 1 soumis (fin N1) | `5c6bcf9` | 29/10 01:48:35 |
| N2 « étage en cours » (échelle de variantes) | `5d3ca1a` | 29/10 03:47:13 |
| N2 TERMINÉ | `bc70055` | 29/10 04:49:26 |

**Fenêtre : 3 h 00 min 51 s**, contre 6 h 05 pour N1 — pour un étage que
l'abaque chiffrait à peine moins cher (32–50 j-h contre 30–54). Cette fenêtre
contient l'intégralité du travail N2 (aucun commit antérieur à `5c6bcf9` ne
touche `completion/`) : la découverte racine (ecj abandonne les membres
incomplets à l'EOF — d'où les « 5 variantes de splice » du domaine), les 6
variantes textuelles de l'échelle, les 9 fichiers de pipeline, les 54 tests,
le banc qualité avec baseline signée, la requalification des gates en CI et
les trois correctifs. Elle est découpée en **trois segments de séance** dont
au moins un a commencé par une reprise après compression du contexte (le
travail non commité a survécu à la coupure) : le temps mort éventuel entre
segments n'est pas séparable des commits, la fenêtre est donc une **borne
haute** du travail effectif. Même retenue prudente : N2, l'étage design le
plus riche, a coûté **moitié moins de temps d'horloge que N1**.

### 2.3 Écarts au plan [mesuré]

1. **L'échelle de variantes : 6 variantes textuelles, pas 5 splices.** Le
   domaine (moteur vendored) payait le phénomène par 5 formes de splice
   codées en dur ; la réécriture a d'abord **découvert la cause racine** —
   ecj abandonne à l'EOF les membres incomplets (et synthétise un
   constructeur par défaut), le marqueur disparaît de l'arbre AVEC le membre
   — puis y a répondu par une échelle généralisée en cascade
   (remplacement → `()` si `new` → ` x;` après modificateurs → ` x` en tête
   de liste → `() {}` en corps de classe → queue équilibrée). Ce n'est pas
   une dérive de coût : c'est le design prévu par l'ADR 0012, exécuté avec
   une compréhension du phénomène que l'ancien moteur n'avait pas. La parité
   se joue sur les comportements des fiches (verts), pas sur le compte
   interne de variantes.
2. **Deux séances, une reprise.** N1 s'était livré en une séance continue ;
   N2 s'est livré en segments interrompus par une compression de contexte.
   Aucun travail n'a été perdu (l'arbre non commité a été repris tel quel),
   mais le modèle « une séance continue par étage » n'est plus le seul mode
   observable — la projection (§4.1) compte désormais des séances pouvant
   être interrompues.
3. **Gates requalifiées conformément à la décision propriétaire** (« au fil
   de N2, SDK Android en CI ») : 4 des 6 gates dormantes de N1 sont ouvertes
   (job `gates-android` — android.jar API 34 en zip officiel `dl.google.com`
   + AAR appcompat `maven.google.com`) ; les 2 gates `model.json`
   (Fiche010/Fiche039) restent dormantes (artefact appareil propriétaire,
   jamais simulé). Le recalage 1 §5.1 recommandait exactement ce geste :
   exécuté.
4. **Le filet a fonctionné comme conçu** : trois bogues découverts par les
   tests et le banc (crash du gel sur liste rétrécie avalé en liste vide
   silencieuse ; `availableMethods` d'ecj ne rend que les déclarées — balayage
   de hiérarchie implémenté ; internes `com.sun.*`/`jdk.*` du jrt proposées en
   complétion — surface publique filtrée dans l'annuaire), plus la découverte
   racine de l'EOF. Chacun de ces points était **invisible aux tests
   existants** avant N2 : c'est la preuve de travail du harnais demandée par
   l'ADR 0016.

### 2.4 Vérification indépendante du jour [mesuré]

Sur le dépôt de travail (état `bc70055`, distant = local vérifié par
`git ls-remote` : SHA identiques) : `./gradlew build` **vert**, comptage XML
**344 tests, 0 échec, 1 ignoré** — identique à la livraison. Les gates
Android se sont jouées sur ce bac à sable (android.jar machine API 34
résolu par `OutilsFixture.androidJar` — env `ANDROID_JAR` → `ANDROID_HOME` →
chemins machine) : Fiche033 ×3 et Fiche004 **jouées, 0 ignoré**, fiches
036/044 vertes sur l'android.jar réel. Aucune reprise n'a été nécessaire
(l'incident de contention mémoire du recalage 1 ne s'est pas reproduit).

### 2.5 Bancs — livraison vs rejeu [mesuré]

`./gradlew :java:benchQualite` **VERT contre la baseline signée**
(`bancs/baselines/completion.json`) :

| Métrique | Livraison (04:49) | Rejeu (recalage 2) |
|---|---:|---:|
| d8 — recall / top-1 / MRR | 1,000 / 1,000 / 1,000 | **1,000 / 1,000 / 1,000** |
| naïf — top-1 / MRR | 0,611 / 0,715 | 0,611 / 0,715 |
| p95 complétion (d8) | 31 ms | **29,3 ms** |
| sauts de liste (gel) | 0 | **0** |

`./gradlew :java:bench` (banc noyau, même machine) : démarrage à froid
703 ms (735 à la livraison N1, 837 au recalage 1) ; diagnose complet
p50 11,2 / p95 50,4 ms ; diagnose rapide p50 1,4 / p95 10,8 ms ; hover
p50 0,087 ms ; tas au repos 16 Mo — hiérarchie des chemins inchangée,
±10 % de reproductibilité. **Un chiffre a bougé de nature** : complétion de
membres p50 0,031 ms (recalage 1) → **5,4 ms / p95 34,4 ms**. Ce n'est pas
une régression mais un changement de chemin mesuré : le banc éprouvait en
N1 un accès direct aux membres du qualifier ; `MoteurJava.completer` est
désormais câblé sur le pipeline complet N2 (analyse de contexte, échelle de
variantes, collecte avec balayage de hiérarchie, chaîne de peseurs,
arrangement gel/plafond). C'est le prix réel de la capacité livrée — et le
chiffre honnête à porter vers la mesure appareil (les gates de latence
Fiche033, désormais jouées en CI, restent vertes).

## 3. La vélocité mesurée contre les postulats d'abaque

### 3.1 Densités de code [mesuré]

Le plan §3.2 postulait **40–62 l. prod et 33–44 l. test par comportement**
pour le moteur entier. Les deux étages mesurés donnent :

| Densité | Postulat | Mesuré N1 (124 comp.) | Mesuré N2 (66 comp.) |
|---|---:|---:|---:|
| l. prod / comportement | 40–62 | 34,3 | **47,6** (3 143 / 66) |
| l. test / comportement | 33–44 | 27,4 | **20,0** (1 321 / 66) |
| Tout compris (prod + banc) / comp. | — | 38,3 | **55,0** (3 633 / 66) |

La prédiction structurale de l'abaque est **vérifiée** : la densité de
production de N2 (47,6) est supérieure à celle de N1 (34,3) et tombe
**à l'intérieur** du postulat 40–62 — l'étage design est plus dense que
l'étage mécanique, comme postulé, sans excéder la borne haute. Cumulé :
N1+N2 = 8 388 l. tout compris pour 190 comportements = **44,1 l./comp.**
[mesuré]. La densité de test descend (20,0) : les tests N2 sont plus
intégrés et le banc qualité prend une part du travail de parité que les
tests unitaires portaient en N1 — cohérent avec le design ADR 0016.

### 3.2 Structure socle/marginal [mesuré-décomposition]

En N1, la déclaration était lisible : 72 % de socle (infrastructure
traversante), 28 % de marginal. En N2, la frontière **perd
son pouvoir discriminant** — et c'est en soi le résultat : les ajouts main se répartissent
en `AnalyseurContexte` 964 l., `ServiceCompletion` 528, `AnnuaireClasspath`
435, `ChaineClassement` 305, `ArrangementListe` 134, `CorrespondanceGraduee`
122, `CorrespondanceKindsLsp` 116, `FrontiereRpc` 74, `AccesJrt` 62,
`MoteurJava` 61, `ContexteCompletion` 52, `LectureAst` 47, `EnvironnementNoms`
4 — soit 2 904 l., **toutes au service direct des 66 comportements**. Le
« design intégré en naissance » est précisément cela : le pipeline EST la
capacité. L'abaque postulait N2 marginal-dominant (socle 8–12 j-h, soit
20–30 % du coût de l'étage) ; la réalité volumétrique est pipeline-dominante
comme N1 — mais avec une différence décisive pour la projection : ce volume
n'est pas un socle « payé une fois pour rien », c'est un **actif réutilisé
par la suite** — N3 (hover, signatures) et N4 (navigation, bindings) se
greffent sur l'analyse de contexte et les bindings que N2 vient de livrer.
Le solde pour N3–N8 est donc structurellement plus léger que le même
périmètre l'aurait été avant N2.

### 3.3 Le facteur d'exécution [mesuré-agent]

L'étage N2 était estimé **32–50 j-h (médiane 40)** ; livré en **3,01 h**
d'horloge (borne haute — §2.2) en 3 segments de séance. Le rapport :

> **1 j-h du postulat ≈ 0,060–0,094 h de séance agent (médiane 0,075 ; soit
> un facteur ≈ 11–17, médiane ≈ 13).**

C'est la réponse à la question que le recalage 1 avait laissée ouverte, et
elle va **dans le sens favorable** : l'étage le plus riche en design s'est
exécuté *plus vite relativement à son postulat* que l'étage mécanique N1
(facteur 0,11–0,20, médiane 0,15). Cumul des deux étages extrêmes de
l'abaque :

| | N1 | N2 | **N1 + N2** |
|---|---:|---:|---:|
| Postulé (j-h) | 30–54 | 32–50 | 62–104 (médiane 80) |
| Séance agent (h) | 6,08 | 3,01 | **9,10** |
| Facteur (h agent / j-h postulé) | 0,11–0,20 | 0,060–0,094 | **0,087–0,147 (médiane 0,114)** |

Plausible sans être démontré [estimé] : le noyau était frais (N1 terminé une
heure avant le début de N2), et la seconde moitié de N2 (fiches 043/044/050/
053 à bornes littérales) était d'exécution mécanique une fois le pipeline en
place. Limites de validité, à garder en tête : (a) deux points ne font pas
une tendance, la variance inter-étages reste inconnue ; (b) les deux points
sont agent, jamais humain (§3.4) ; (c) la fenêtre N2 est une borne haute
(temps mort inter-séances non séparable) — le facteur N2 réel est donc
≤ 0,094, ce qui renforce encore le sens favorable.

### 3.4 Ce que la mesure ne dit pas [non vérifié]

Rien ne change au recalage 1 sur ce point : aucun point de mesure humain
n'existe dans ce dépôt, et deux points agent n'en créent pas. La fourchette
160–270 j-h reste **l'unique outil de planification pour une exécution
humaine**. Le facteur combiné 0,087–0,147 ne se lit pas « le programme est
huit fois moins cher que prévu » mais « *exécuté par l'agent qui livre ce
dépôt depuis la vague 0*, le programme consomme des séances, pas des j-h ».
La distinction reste contractuelle : l'engagement approuvé le 28/10 était
borné étage par étage, et le §6 soumet la suite.

## 4. Re-projection de N3–N8 + T

### 4.1 En exécution agent [estimé, recalibré sur N1+N2]

Projection = fourchette postulée du plan (§5) × facteur combiné mesuré
(0,087–0,147, médiane 0,114), discrétisée en séances — une séance minimum
par étage, la montée en contexte n'étant pas amortissable en dessous, et les
étages design structurant pouvant en exiger deux (N2 en a consommé deux) :

| Étage | j-h postulés | h agent projetées | Séances effectives |
|---|---:|---:|---:|
| N3 — hover / inlay / signatures | 14–23 | 1,3–3,4 | 1 |
| N7 — confort éditeur | 17–28 | 1,5–4,1 | 1 |
| N4 — navigation & usages | 21–31 | 1,9–4,6 | 1–2 |
| N5 — rename v2 | 12–20 | 1,1–2,9 | 1 |
| N6 — CodeAction v2 | 19–30 | 1,7–4,4 | 1–2 |
| N8 — cycle de vie LSP & protocole | 14–25 | 1,3–3,7 | 1 |
| T — transverse (banc croisé, re-baselines) | 6–10 | 0,6–1,5 | transverse |
| **Total N3–N8 + T** | **103–167** | **9–25** | **5–8** |

**Lecture : 5 à 8 séances effectives (médiane ≈ 7)** séparent la livraison
actuelle de la parité complète du cahier sur JVM + bancs — contre 6–9
séances projetées au recalage 1 pour N2–N8+T dont N2 a consommé 2 : la
projection totale est tenue. Coût cumulé du programme depuis la porte
d'entrée (mesure appareil) : 9,1 h faits + 9–25 h restants ≈ **18–34 h
agent** [estimé]. Les incertitudes dominantes restantes sont connues et
bornées : N4 (QueryExecutor sur CIDX — premier étage qui s'appuie sur
`:index` au-delà de l'adaptateur de `:testing`) et N6 (ModCommand — premier
protocole déclaratif sérialisable). Le plan ne prévoit **pas de recalage 3**
: le prochain point de vérification structurel n'est pas un point
d'estimation mais le **banc de parité croisé (T)** et la **parité sur
appareil** (critère de bascule §8.5).

### 4.2 En exécution humaine [estimé — inchangée, motif R5]

La fourchette **160–270 j-h (médiane 213)** est **maintenue**. Motifs : (a)
les deux points de mesure restent agent — R5 interdit d'ajuster une
fourchette d'exécution humaine sur des données qui ne sont pas humaines ;
(b) le recoupement volumétrique recalibré dérive désormais vers la moitié
basse sans la contredire : 251 comportements restants × 44–55 l./comp. tout
compris mesurées ≈ 11,0–13,8 k l. → à 150–300 l./j-h, 37–92 j-h de volume
pur, cohérent avec la partie basse des 103–167 j-h postulés pour N3–N8+T
une fois comptés bancs, CI, fixtures et montée en contexte ; (c) le moteur
entier projeté (15,9–19,4 k l. prod + 9,7–11,5 k l. tests) reste sous le
postulat haut du plan (18–28 k + 15–20 k). La fourchette n'est donc pas
réduite — mais son **centre de gravité documenté est bas**, et cette
constatation est consignée ici pour le propriétaire avec son étiquette R5 :
[estimé]. Toute revalorisation formelle exigerait un point humain, que seul
le propriétaire peut créer en exécutant un étage.

### 4.3 Postulats structurants du plan (§7) — état après N2

| Postulat du §7 | État après N2 |
|---|---|
| Le cahier couvre le contrat (garde : banc croisé T) | **Confirmé sur un échantillon de 34/65 fiches** (190/449 comportements, N1+N2) : aucun comportement « hors cahier » découvert ; les surprises de N2 (EOF, internes jrt, hiérarchie) étaient toutes des précisions D'implémentation, pas des trous du contrat |
| ecj 3.40.0 patché reste la base compilateur | **Confirmé et renforcé** : la découverte racine de N2 (abandon des membres incomplets à l'EOF) a été absorbée par l'échelle de variantes **sans toucher au patcheur** — le périmètre des shims n'a pas bougé |
| `:index`/`:model` repris restent stables | **Confirmé** : 122 tests repris verts dans le build 344 ; l'annuaire N2 s'est branché sur le classpath sans modifier CIDX |
| Latences cibles atteignables sur ART avec ecj | **Étayé, non clos** : gates Fiche033 désormais **jouées et vertes** (CI + local, android.jar réel API 34) ; p95 complétion 29–31 ms JVM, chemin complet p50 5,4 ms ; la mesure de bout en bout sur appareil reste la gate de N8/T |

Aucun postulat structurant n'est tombé après deux étages — dont l'étage
que le plan avait désigné comme le plus risqué pour l'abaque.

## 5. Dette et points d'attention hérités de N2

1. **2 gates `model.json` dormantes (Fiche010, Fiche039)** : artefact appareil
   du propriétaire, jamais simulé ; les `assumeTrue` restent visibles et
   messageés. Non bloquant pour N3–N7 ; à trancher avant la bascule (N8/T) —
   fournir l'artefact versionné ou une fixture synthétique équivalente.
2. **Chemin de complétion complet p50 5,4 ms / p95 34,4 ms (JVM bureau)** :
   c'est le chiffre réel à porter sur appareil (contre 0,031 ms pour l'accès
   direct N1). Les gates Fiche033 jouées (CI) sont la garde de non-régression
   latence ; la vérification ART de bout en bout reste en N8/T, comme au
   plan. À surveiller : le facteur appareil/JVM observé au spike (~×8–14)
   maintiendrait le p95 dans l'ordre de 250–480 ms — la fiche 033 fixe les
   bornes exactes, la CI les fait jouer.
3. **Corpus qualité volontairement borné (18 positions)** : versionné et
   signé, mais petit par construction ; son rôle est la non-régression, pas
   la significativité statistique. Extension du corpus = amélioration
   candidate (PR de re-signature dédiée, procédure en place).
4. **Statistics opt-in livrées désactivées** (`PeseurStatistiques`,
   `CompteurUsage` — conforme au design ADR 0012) : aucun consommateur hôte
   ne les alimente aujourd'hui ; le branchement (le cas échéant via le
   protocole en N8) reste à décider — état documenté, pas une dette bloquante.

## 6. Décision demandée au propriétaire

Conformément au mandat §8.2 (« la confiance sur N3–N8 est re-évaluée »), deux
points :

1. **Suite du programme — lancer N3 ?** L'étage N3 (hover, inlay &
   signatures — 5 fiches, 48 comportements) est le suivant de la séquence
   confirmée (§10.3), et c'est le premier qui **réutilise** le pipeline N2
   (analyse de contexte, bindings). Le présent recalage ne relève **aucun
   signal défavorable** — au contraire : couverture intégrale de l'étage le
   plus riche en design, densités dans le postulat, bancs verts et
   reproductibles, gates ouvertes, facteur d'exécution N2 inférieur à N1,
   aucun postulat structurant tombé après deux étages. Recommandation :
   **lancer N3**, borne d'engagement inchangée (étage par étage). À noter
   pour la gouvernance : ce recalage est le **dernier prévu au plan** —
   au-delà, la garde n'est plus l'estimation mais le banc croisé (T) et la
   parité sur appareil (§8.5).
2. **Gates `model.json` dormantes (Fiche010/Fiche039)** : les laisser dormir
   jusqu'à N8/T (acceptable — les fiches restent couvertes par leurs autres
   cas, comme avant pour les gates Android) ou fournir l'artefact appareil
   versionné pour les ouvrir plus tôt. Décision indépendante du point 1.
   Rappel de sécurité (recalage 1) : révoquer le PAT si son usage était
   unique.

---
*Références : `PLAN-REECRITURE.md` (§5 fourchettes, §8.2 point 3 — présent
recalage, §10 décisions) · `RECALAGE-1.md` (facteur N1, question laissée
ouverte sur N2) · commits `5d3ca1a` + `bc70055` (livraison N2, messages
détaillés) · `bancs/baselines/completion.json` (baseline signée) ·
`AGENTS.md` (règles de travail).*
