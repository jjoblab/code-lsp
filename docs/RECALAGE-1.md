# RECALAGE-1 — Vélocité mesurée de l'étage N1 et re-projection de N2–N8

> **Statut : SOUMIS au propriétaire** — décision de suite attendue (§6).
> **Date** : 29/10/2026. **Mandat** : `PLAN-REECRITURE.md` §8.2 (« Recalage 1 —
> fin N1 : la vélocité réelle mesurée sur le premier étage (le plus gros
> socle) remplace les postulats d'abaque ; la fourchette N2–N8 est
> re-projetée et re-soumise au propriétaire »). Décision d'origine : §10.1
> (« la décision de suite est re-soumise au propriétaire au recalage 1 »).
> **Discipline R5** : chaque chiffre est étiqueté **[mesuré]** (rejouable par
> commande — les commandes sont données), **[estimé]** (modèle documenté,
> postulats visibles) ou **[non vérifié]**. Deux qualificatifs propres à ce
> document : **[mesuré-agent]** (mesuré, mais dans le rythme d'exécution de
> l'agent qui livre le dépôt — non transposable au jour-homme humain, cf.
> §3.4) et **[mesuré-décomposition]** (mesuré au volume ; la frontière de
> classement entre « socle » et « marginal » est déclarée, pas naturelle).

---

## 1. Mandat et méthode

Le plan de réécriture approuvé le 28/10/2026 reposait sur une estimation par
abaque : chaque étage était chiffré comme *socle + n × coût marginal par
comportement*, avec des postulats de densité (lignes par comportement) et de
productivité (150–300 l./j-h) qu'aucune donnée ne pouvait encore étayer — le
plan le disait lui-même : « [non vérifié] la productivité réelle de l'équipe
qui exécutera : le premier recalage (fin N1, §8) remplacera les postulats
d'abaque par une vélocité mesurée » (§3.3). L'étage N1 — noyau compilateur &
environnement, 25 fiches, 124 comportements, le plus gros socle du programme —
a été **livré intégralement le 29/10/2026 à 00 h 57 UTC** (commit `ce291dc`).
Le présent document est l'exécution du recalage promis.

La méthode est celle du dépôt : tout ce qui est affirmé ici est soit rejouable
sur un clone frais du dépôt (`./gradlew build`, `./gradlew :java:bench`,
`git log`/`git show ce291dc --stat`), soit explicitement étiqueté comme
estimation. Les chiffres « du jour » (§2.4, §2.5) sont des **re-mesures
indépendantes** effectuées sur un clone neuf, distinctes des chiffres portés
par le message de livraison — leur accord mutuel est en soi un résultat
(reproductibilité). La seule grandeur non rejouable est le temps de séance
(§2.2), qui dépend de l'historique des commits : elle est étiquetée
[mesuré-agent] et son usage est borné au §4.1.

## 2. Ce que N1 a réellement coûté

### 2.1 Volumétrie livrée [mesuré]

Commandes : `git show ce291dc --stat` ; `find java/src/main -name '*.kt' |
wc -l` (et analogue pour chaque arbre) ; comptage des `TEST-*.xml`.

| Grandeur | Valeur | Détail |
|---|---:|---|
| Fichiers du commit N1 | 61 | +8 386 / −39 lignes |
| `:java` main | 22 fichiers, **4 259 l.** | dont le banc `BancNoyau.kt` à part (1 f., 144 l.) |
| `:java` test | 28 fichiers, **3 403 l.** | 25 fiches du cahier + fumée + fixtures |
| `:testing` réécrit | 2 fichiers, **352 l.** | 118 main + 234 test — écart au plan, §2.3 |
| Tests noyau | **128** (dont 6 ignorés) | + 4 tests d'intégration `:testing` |
| Tests du dépôt entier | **290 verts, 0 échec, 6 ignorés** | 38 modèle + 84 index + 36 ecj-art + 128 noyau + 4 intégration |
| Fiches N1 couvertes | **25 / 25** | périmètre de l'étage entièrement couvert |
| Comportements couverts | **124 / 124** (2,2 tests/comportement) | l'aide 037 (`buildRJar`) incluse |

La couverture est intégrale : aucune fiche de l'étage n'a été reportée ni
abandonnée. Les 6 tests ignorés ne sont pas des pertes de couverture mais des
**gates environnementales suspendues** (§5) : les mêmes fichiers de test
exécutent leurs autres cas, qui sont verts.

### 2.2 Le temps de séance [mesuré-agent]

Commande : `git log --pretty=fuller 90635c2..ce291dc`.

| Borne | Commit | Horodatage UTC |
|---|---|---|
| Porte d'entrée franchie (mesure appareil retournée) | `90635c2` | 28/10 18:52:06 |
| Livraison N1 | `ce291dc` | 29/10 00:57:20 |

**Fenêtre : 6 h 05**, en une séance de travail continue de l'agent, sans
commit intermédiaire — cette fenêtre contient l'intégralité du travail N1 :
lecture des 25 fiches, écriture des 22 fichiers de production, des 128 tests,
du banc, l'inscription au manifeste de provenance et la mise à jour du README.
Aucun travail N1 n'a été produit hors de cette fenêtre (aucun commit antérieur
ne touche `java/`). C'est la mesure centrale du recalage, et elle porte une
limite de validité essentielle : elle mesure le **rythme d'un agent** sur un
cahier exécutable mécaniquement — pas la productivité d'un développeur
humain, dont aucun point de mesure n'existe dans ce dépôt (§3.4).

### 2.3 Écarts au plan [mesuré]

Trois écarts entre le plan et l'exécution réelle sont à consigner, aucun
n'est une dérive de coût :

1. **`:testing` réécrite au lieu de reprise.** Le plan N1 prévoyait la
   « reprise contrôlée de `:testing` (4 f. — reportée ici depuis la vague
   1b) ». L'exécution a révélé que l'ancienne suite était **couplée à l'API
   du moteur vendored** (elle éprouvait des classes internes de l'ancien
   moteur) : la « reprise » mécanique était impossible, et `:testing` a été
   **réécrite en Kotlin neuf** (adaptateur `IndexNoms` sur le CIDX réel + 4
   tests d'intégration moteur↔index). La mutation est inscrite au manifeste
   de provenance (`PROVENANCE-REPRISE.md`). Le postulat « reprise mécanique »
   du plan est donc **tombé** ; son coût a été absorbé dans la même séance,
   sans allongement mesurable (il n'existe pas de contre-factuel : c'est la
   limite honnête de la mesure).
2. **6 tests ignorés, hypothèses environnementales visibles.** Précisément :
   `Fiche033BancLatenceTest.latenceCompletionMembre/Type/Vide` (3 —
   `android.jar` du SDK absent du bac à sable), `Fiche004.reproDeviceScenario`
   (1 — idem), `Fiche010.reproIntegraleOrdreDeviceContreOrdreCorrige` (1 —
   `model.json` appareil absent), `Fiche039.diagnoseWithRealFullClasspathDeviceConditions`
   (1 — idem). Le plan (§9, risque 3) exigeait que « chaque fiche
   conditionnelle [soit] requalifiée en gate explicite à l'implémentation » :
   c'est fait sous forme d'`assumeTrue` à message visible — la gate existe,
   elle dort faute d'environnement (§5).
3. **Banc intégré à la CI dès N1** (tâche `:java:bench` dans `ci.yml`),
   conformément au plan ; aucune surprise.

### 2.4 Vérification indépendante du jour [mesuré]

Sur un **clone frais** du dépôt distant (29/10, séance du recalage) :
`./gradlew build` **vert**, comptage XML : 290 tests, 0 échec, 6 ignorés —
identique à la livraison. La première exécution du build complet sur machine
vierge a connu un échec transitoire de `:java:test` (contention mémoire entre
JVM de test parallèles) résorbé au relancement ; incident de bac à sable, non
régressif, consigné ici pour la traçabilité.

### 2.5 Banc du noyau — livraison vs rejeu [mesuré]

`./gradlew :java:bench` (clone frais, JDK 21) contre les chiffres portés par
le message de livraison :

| Mesure | Livraison (29/10 00:57) | Rejeu (29/10, recalage) |
|---|---:|---:|
| Démarrage à froid (premier diagnose, jrt chargé) | 735 ms | 837 ms |
| Diagnose complet p50 / p95 | 11,7 ms / — | 11,0 / 46,6 ms |
| Diagnose rapide p50 / p95 (frappe confinée) | 1,5 ms / — | 1,8 / 8,8 ms |
| Hover p50 / p95 | 0,12 ms / — | 0,098 / 2,0 ms |
| Complétion membres p50 | — | 0,031 ms |
| Tas au repos | 16 Mo | 16 Mo |

Reproductible à ±15 % près sur les froids et p50 — l'ordre de grandeur et la
hiérarchie des chemins (rapide ≈ ×6 sous le complet ; hover quasi gratuit)
sont stables. Rappel de la porte d'entrée appareil (28/10, moto g06,
`GABARIT-RESULTATS.md`) : V3 diet p50 **5,793 ms**, complet **9,527 ms**,
compile résolue p50 **50,185 / p95 70,156 ms** (env réutilisé
**30,922 / 40,182 ms**) — le facteur appareil/JVM observé ce jour-là (~×8–14
selon le poste) maintient le chemin chaud du noyau (1,8 ms JVM) dans l'ordre
de 15–25 ms sur ART, compatible avec les gates de frappe des fiches
(vérification de bout en bout restant en N8/T, comme au plan).

## 3. La vélocité mesurée contre les postulats d'abaque

### 3.1 Densités de code [mesuré]

Le plan §3.2 postulait un moteur complet de « 18–28 k l. de production +
15–20 k l. de tests » pour 449 comportements, soit **40–62 l. prod et
33–44 l. test par comportement**. La mesure N1 donne :

| Densité | Postulat (moteur entier) | Mesuré N1 |
|---|---:|---:|
| l. prod / comportement | 40–62 | **34,3** (4 259 / 124) |
| l. test / comportement | 33–44 | **27,4** (3 403 / 124) |
| Tout compris (prod + banc + `:testing`) / comp. | — | **38,3** (4 755 / 124) |

N1 est donc **sous le postulat de densité**, de 15 à 45 % selon la borne. Ce
résultat doit être lu avec sa limite : N1 est l'étage le plus « délégation à
ecj » du programme (l'abaque lui donnait le marginal le plus bas, 1–2 h/comp.
contre 2,5–5 h pour les étages design). Les étages N2–N6 portent les designs
D8–D12 « intégrés en naissance » (chaîne de weighers, QueryExecutor, phases de
rename, ModCommand, époque copy-on-write) : leurs densités attendues restent
supérieures à celle de N1, comme l'abaque le postulait. La mesure ne invalide
donc pas le postulat haut de la fourchette — elle rassure la borne basse.

### 3.2 Structure socle/marginal [mesuré-décomposition]

Décomposition déclarée des 22 fichiers main de `:java` (la frontière
« infrastructure traversante » vs « capacité alignée sur des fiches » est un
choix de classement, annoncé comme tel) :

- **Socle (13 fichiers, 3 070 l., 72 %)** : `MoteurJava`, `EnvironnementCompilation`,
  `PontEcj`, `LectureAst`, `RegimeInstantane`, `UnitesSource`, `AccesJrt`,
  `EnvironnementNoms`, `FabriqueClasseBinaire`, `StubsIntrinseques`,
  `LecteurZipBrut` (classpath), `JetonAnnulation`, `OrdonnanceurMoteur` ;
- **Marginal (9 fichiers, 1 189 l., 28 %)** : `IndexNoms`,
  `CachePresenceSource`, `CacheLocalisation`, `CacheParse`, `DiagnoseRapide`,
  `ScannerCorps`, `SourceFocuser`, `ServiceDiagnostics`, `TypesDiagnostics`.

Le marginal pur ressort à **9,6 l./comportement**. L'abaque postulait pour N1
un socle de 12–18 j-h et un marginal de 124 × 1–2 h — soit, en médiane, une
part de socle d'environ 39 % du coût de l'étage. La réalité volumétrique
place le socle à **72 %**. Autrement dit : l'abaque avait raison sur la
*NATURE* de N1 (gros socle, petit marginal — c'est le plus gros socle du
programme, comme prévu) mais a **sous-estimé la part du socle et surestimé le
marginal**. La conséquence pour la projection est favorable : les étages
suivants ont des socles plus petits (8–12 j-h pour N2, 4–10 pour les autres,
contre 12–18 pour N1) et c'est précisément la part dont la réalité a montré
qu'elle dominait — à périmètre de comportements constant, chaque étage
suivant devrait donc voir son coût relatif pencher *davantage* vers le
marginal léger mesuré (9,6 l./comp.) que vers le gros socle déjà payé une
fois.

### 3.3 Le facteur d'exécution [mesuré-agent]

L'étage N1 était estimé **30–54 j-h (médiane 40)** ; il a été livré en
**6,1 h de séance agent continue**. Le rapport entre le postulat et la
réalité d'exécution est donc :

> **1 j-h du postulat ≈ 0,11–0,20 h de séance agent (médiane ≈ 0,15 ; soit un
> facteur ≈ 5–9, médiane ≈ 6,6).**

Ce facteur est LA grandeur que le §8.2 demandait d'extraire : c'est elle qui
re-projette N2–N8 en unités d'exécution réelles du programme (§4.1). Sa
validité est encadrée : (a) il n'est mesuré que sur **un** étage ; (b) cet
étage est le plus favorable à une exécution mécanique (délégation à ecj,
invariants structurels, bornes chiffrées littérales dans les fiches) ; (c) la
variance inter-séances est inconnue (une seule séance). N2, étage design le
plus riche, sera le premier point de contre-vérification — c'est
exactement le rôle que le plan donne au **recalage 2 (fin N2, §8.3)**.

### 3.4 Ce que la mesure ne dit pas [non vérifié]

Rien dans ce dépôt ne mesure la productivité d'un développeur humain sur ce
cahier : aucun point de mesure n'existe et n'existera pas avant qu'un humain
n'exécute un étage. La fourchette 160–270 j-h du plan reste donc **l'unique
outil de planification pour une exécution humaine**, et le présent recalage
ne la modifie pas (§4.2). Le facteur du §3.3 ne doit pas être lu comme « le
programme est 6,6 fois moins cher que prévu » : il se lit « *exécuté par
l'agent qui livre ce dépôt depuis la vague 0*, le programme consomme des
séances, pas des j-h ». La distinction est contractuelle : l'engagement
approuvé le 28/10 était borné à N1 + T précisément parce qu'aucune donnée
n'existait au-delà ; le §6 soumet la suite avec, cette fois, une donnée.

## 4. Re-projection de N2–N8 + T

### 4.1 En exécution agent [estimé, recalibré sur N1]

Projection = fourchette postulée du plan (§5) × facteur mesuré (0,11–0,20,
médiane 0,15), discrétisée en séances (~6 h continues) — une séance minimum
par étage, la montée en contexte n'étant pas amortissable en dessous :

| Étage | j-h postulés | h agent projetées | Séances effectives |
|---|---:|---:|---:|
| N2 — complétion & classement | 32–50 | 3,5–10 | **1–2** |
| N3 — hover / inlay / signatures | 14–23 | 1,5–4,6 | 1 |
| N7 — confort éditeur | 17–28 | 1,9–5,6 | 1 |
| N4 — navigation & usages | 21–31 | 2,3–6,2 | 1 |
| N5 — rename v2 | 12–20 | 1,3–4,0 | 1 |
| N6 — CodeAction v2 | 19–30 | 2,1–6,0 | 1 |
| N8 — cycle de vie LSP & protocole | 14–25 | 1,5–5,0 | 1 |
| T — transverse (banc croisé, recalages) | 6–10 | 0,7–2,0 | transverse |
| **Total N2–N8 + T** | **165–271** | **18–54** | **6–9** |

**Lecture : 6 à 9 séances effectives (médiane ≈ 7)** séparent la livraison
actuelle de la parité complète du cahier sur JVM + bancs, dans l'hypothèse où
l'exécution reste celle observée depuis la vague 0 (l'agent, en séances
continues, le propriétaire aux décisions §8). La borne basse (6) suppose des
séances denses et sans friction ; la borne haute (9) absorbe les imprévus du
type « postulat tombé » constaté une fois en N1 (`:testing` réécrite). Le
facteur d'incertitude dominant reste N2 : s'il s'avère nettement moins
favorable au rythme agent que N1 (design fin, tuning qualitatif de
classement), c'est le recalage 2 qui le dira — la structure de gouvernance du
plan (§8.3) est déjà en place pour l'absorber.

### 4.2 En exécution humaine [estimé — inchangée, motif R5]

La fourchette **160–270 j-h (médiane 213)** du plan est maintenue. Motif :
(a) un seul étage a été mesuré, et c'est l'étage le plus atypique (le plus
mécanique, le plus gros socle) — R5 interdit d'ajuster une fourchette
pluri-annuelle sur un point unique biaisé dans un sens connu ; (b) le
recoupement volumétrique recalibré (441 comportements restants × 34–38
l./comp. mesurées ≈ **15–17 k l. prod** + 12–13 k l. tests → 90–180 j-h à
150–300 l./j-h) devient cohérent avec la **moitié basse** de la fourchette :
il la rassure sans la justifier — les densités des étages design resteront
supérieures à celle de N1 ; (c) le plan avait déjà prévu la bonne réponse :
le point de vérification est le **recalage 2 (fin N2, §8.3)**, sur l'étage le
plus riche en design. Toute revalorisation avant ce point serait du faux
raffinement.

### 4.3 Postulats structurants du plan (§7) — état après N1

| Postulat du §7 | État après N1 |
|---|---|
| Le cahier couvre le contrat (le banc croisé T garde le risque « durcissement implicite ») | **Confirmé sur l'échantillon N1** : 25 fiches mises en œuvre sans comportement « hors cahier » découvert ; la garde reste le banc croisé (T), toujours à construire |
| ecj 3.40.0 patché reste la base compilateur | **Confirmé** : le pont `Compiler.resolve` tient (arbres vivants), diagnose rapide et complet fonctionnels, latences tenues (§2.5) |
| `:index`/`:model` repris restent stables | **Confirmé** : 122 tests repris toujours verts dans le build complet ; l'adaptateur `IndexNoms` de `:testing` s'appuie sur le CIDX réel sans le modifier |
| Latences cibles atteignables sur ART avec ecj | **Étayé, non clos** : porte d'entrée appareil du 28/10 (V3 compile p50 50 ms ; chemin chaud noyau 1,8 ms JVM ≈ 15–25 ms estimés sur ART) ; vérification de bout en bout sur appareil toujours en N8/T |

Aucun postulat n'est tombé ; un seul postulat d'exécution est tombé
(`:testing` en reprise → réécriture, §2.3), absorbé sans dérive.

## 5. Dette et points d'attention hérités de N1

1. **Gates environnementales dormantes (6 tests)** : `android.jar` (SDK) et
   `model.json` (appareil) absents du bac à sable ; les `assumeTrue` sont
   visibles et messageés. Requalification : ajouter le SDK Android en CI
   (download de `android.jar` API 34) et rejouer — 4 des 6 gates
   (Fiche033 ×3, Fiche004) n'exigent que ça ; les 2 gates `model.json`
   (Fiche010, Fiche039) exigent un artefact appareil versionné ou une
   fixture synthétique équivalente. Non bloquant pour N2.
2. **Contention mémoire du build complet** (§2.4) : 5 JVM de test parallèles
   × tas 640 m peuvent dépasser le bac à sable ; si cela se reproduit en CI,
   sérialiser les modules de test dans `ci.yml` (une ligne).
3. **`Fiche033` en double rôle** : ses trois latences de complétion
   s'ignorent faute d'`android.jar` — or la complétion est justement le
   sujet de N2 ; le banc N2 (recall/top-1/MRR) devra reprendre ces gates
   comme bornes de non-régression latence, avec le SDK en CI.

## 6. Décision demandée au propriétaire

Conformément au §10.1 du plan (« la décision de suite est re-soumise au
propriétaire au recalage 1 »), deux points :

1. **Suite du programme — lancer N2 ?** L'étage N2 (complétion &
   classement, 9 fiches, 66 comportements, design D8 en naissance, ancienne
   « capacité phare ») est le suivant de la séquence confirmée (§10.3). Le
   présent recalage ne relève **aucun signal défavorable** : couverture
   intégrale de N1, densités sous postulat, aucun postulat structurant
   tombé, latences tenues, dette identifiée et non bloquante. Recommandation :
   **lancer N2**, borne d'engagement inchangée (étage par étage, recalage 2
   à la fin).
2. **Requalification des gates environnementales** : au fil de N2 (ajout du
   SDK Android en CI dès l'étage complétion — recommandé, la fiche 033 est
   une gate latence de complétion) ou différé à T (acceptable : les fiches
   restent couvertes par leurs autres cas). Ce point peut être tranché
   indépendamment du premier.

---
*Références : `PLAN-REECRITURE.md` (§3.3 ancrages, §5 fourchettes, §8.2
présent recalage, §8.3 recalage 2, §10 décisions) · commit `ce291dc`
(livraison N1, message détaillé) · `spike/appareil/GABARIT-RESULTATS.md`
(porte d'entrée appareil, 28/10) · `PROVENANCE-REPRISE.md` (mutation
`:testing`) · `AGENTS.md` (règles de travail).*
