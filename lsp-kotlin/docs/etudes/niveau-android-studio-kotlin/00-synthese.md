# 00-synthese — Synthèse et recommandation unique

> Mission « Amener le LSP Kotlin de `code-lsp` au niveau d'intelligence
> d'Android Studio (mode K2) » — audit + étude de référence + ADR + plan
> par vagues, **puis STOP** (§10 du prompt). Date : 2026-10-31, branche
> `etude/moteur-kotlin`, état audité `df3cf85` (CI 10/10 verte, run
> `41170597847`).

## 1. Recommandation unique (5 lignes)

**Exécuter le plan par vagues V-A→V-I sur le socle actuel, avec l'architecture
à paliers 1/2/3 (ADR 0122) : d'abord rendre le moteur VRAI et VISIBLE
(V-A : corriger les 980 faux positifs mesurés + bascule CodeIDE), puis
étendre le typage maison au palier 2 borné (V-B, ADR 0123 — la réouverture
de l'ADR 0107 option 2, mais versionnée et vérifiée en différentiel contre
l'oracle K2), puis l'intelligence visible (smart/chain, Android réel,
Compose, Gradle, interop).** Le palier profond (V-H) n'est construit que si
le propriétaire l'arbitre après deux mesures (gabarit 07 + confirmation
hôte) ; l'option « pack dex K2 » est rejetée sur les chiffres. Le socle
(parseur officiel, preuve ou silence, époque copy-on-write, empreinte
4,10 Mo) est sain — rien à reprendre, tout à étendre.

## 2. Tableau des paliers chiffré

| Palier | Contenu | Coût/empreinte | Latence | Fidélité visée |
|---|---|---|---|---|
| **1 (existe, à corriger)** | parseur + modèle + typage 2 cas + 10 codes + 6 genres | livré : dex 26 590 méthodes / 4,10 Mo (81 %/68 % des plafonds) | parse 1,8 ms p50 (500 l., JVM) ; reparse 3 000 l. 8,6 ms p50 [mesuré, JVM] | **FP mesuré 28,05/1 000 l. (V0) → cible < 0,5 après V-A** ; FN baselinés |
| **2 (à construire, V-B)** | arguments de type, nullabilité, types fonctionnels, paramètres de type, variance en lecture, extensions génériques, surcharges, récepteurs implicites, smart casts étendus | [estimé] +300–600 méthodes / +0,15–0,3 Mo — à mesurer à chaque sous-vague (garde E-K8) | même chemin de frappe (cible §0.1 inchangée) | différentiel types vs oracle K2 ≥ 95 % sur le sous-ensemble couvert ; ambiguïté = silence |
| **3 (à instruire, V-H)** | vérité compilateur K2 HORS frappe : processus déporté (JVM Termux/PRoot — à confirmer hôte Q-K7) ou démon ; pack dex REJETÉ (S1 : 53,6 Mo / 64 507 méthodes) | amortçage K2 mesuré 3 503 ms JVM ; mémoire/latence appareil [à mesurer, gabarit 07] | jamais sur la frappe ; premier diagnostic < 10 s [cible] ; chaud < 2 s [cible] | fidélité compilateur par construction |

Cibles de vitesse §0.1 (complétion/hover/signature < 100 ms p95 perçu ;
diagnostics ~300 ms ; p99 régulier ; rien d'indisponible pendant
l'indexation) : maintenues comme **cibles à valider par la passe appareil**
(gabarit 07) — calibrées par le facteur ART **observé ≈ 4× côté Java**
(passe 1 moto g06 : complétion membres p95 404,87 ms ≈ 4× le budget —
chiffre JAVA, pas Kotlin [NON VÉRIFIÉ côté Kotlin]).

## 3. Top 10 des écarts P0/P1 (preuves à l'audit)

| # | Écart | Preuve |
|---|---|---|
| 1 | **980 faux positifs sur code compilable** (28,05/1 000 l.) — `it` 603, imports implicites `kotlin.*` ~100, `this` 48, racines ~60, `T` 11, valReassign indexé 36… | mesure V0 P1, audit §4.3 |
| 2 | **Moteur livré mais PAS EN SERVICE** (bascule I0–I6 non commencée, pack non publié) | KH16, doc 11 §3 |
| 3 | Typage à 2 cas : génériques/nullabilité/variance non représentés — toute la couche IDE en dépend | KH1, TypageBorne.kt:19-26 |
| 4 | Complétion sans smart/chain ni récepteurs DSL (6 genres, INCONNU = vide) | KH6 |
| 5 | Code généré KSP/KAPT invisible après build (`build` exclu du scan) | KH8 |
| 6 | Compose/coroutines sans intelligence (previews seulement) | KH10 |
| 7 | Interop unilatérale bornée (arité Java jamais contrôlée, rename inter-langage absent) | KH11 |
| 8 | Gradle KTS/catalogue sans intelligence (silence D3) | KH9 |
| 9 | Catalogue de diagnostics 10 familles (la référence en rend des centaines) — extension GATED par la baseline FP | KH2 + ADR 0125 |
| 10 | Bancs saturés (complétion 1,000/16 positions ; fidélité 86 lignes) — corrigé par le V0 livré | KH3/KH13 |

## 4. État des hypothèses KH1–KH16

**Les seize hypothèses sont CONFIRMÉES** — tableau récapitulatif et preuves
fichier:ligne à l'[audit](01-audit-moteur.md) §1. Trois précisions :
KH3 est confirmé **par la mesure** (28,05 FP/1 000 l. — plus fort que
l'hypothèse : le zéro FP n'était pas seulement peu prouvé, il était faux à
l'échelle) ; KH8 est confirmé **nuancé** (l'« avant build » est un ATOUT,
le déficit est l'« après build ») ; KH16 est confirmé et devient le
**chemin critique de valeur** (bascule avant extension).

## 5. Les 10 risques et mitigations

1. **Régression du silence utile en corrigeant les FP** (V-A) → chaque
   famille corrigée passe le banc P3 mutants (rappel ≥ 0,95 avant fusion).
2. **Le palier 2 dérive en « presque-K2 »** → frontière versionnée +
   critère d'arrêt écrit (0123) + différentiel oracle + ambiguïté = silence.
3. **Budget dex** (81 % méthodes) → gel du socle + packs à la demande +
   garde CI inchangée (0133) ; mesure dex à chaque vague.
4. **Bascule bloquée par l'arbitrage I1 Java** (C-NK2) → la V-A Kotlin
   prépare tout ce qui est de son côté (publication, témoin, capacités) ;
   demande d'interface explicite.
5. **Dépendance `:model` v2 au calendrier Java** (C-NK1) → V-E placée
   après V-B Kotlin ; repli : dérivateur disque propre (lecture directe).
6. **Cibles §0.1 inatteignables sur ART** (miroir AL9 Java) → discipline
   D5 Java : ne pas recalibrer avant la passe android.jar + mesure réelle ;
   baseline consignée.
7. **Faux positifs des nouvelles familles de diagnostics** → catalogue
   GATED par FP/1 000 < 0,5 mesuré (0125) ; sévérité avertissement d'abord.
8. **Sur-apprentissage du nouveau banc** → split train/éval dès V-A'
   (0135, leçon AL1 Java).
9. **Divergence silencieuse avec la mission Java** (mutualisation) → tout
   désaccord consigné dans DEMANDES-INTERFACE, jamais divergé en silence.
10. **Ré-exécution du programme au-delà du STOP** → le plan est arrêté à
    la V0 livrée ; chaque vague attend son prompt d'exécution validé.

## 6. Plan résumé

V0 **LIVRÉE** (banc P1–P9 + corpus 6 projets + mesures + fiches KC-01…12 +
protocoles) → **V-A** fiabilité & bascule (14–24 j-h) → **V-B** typage
palier 2 (24–40) → **V-C** diagnostics gated (12–20) → **V-D** smart/chain
(10–16) → **V-E** Android réel (14–24) → **V-F** actions (12–20) → **V-G**
Gradle KTS (8–14) → **V-I** protocole & échelle (10–18) → **V-H** profond
si arbitré (8–16). **Total 112–192 j-h agent, 15–25 séances** (recalage
obligatoire après V-A). Détail, critères de sortie mesurables et listes
hôte/client/Java au [plan](04-plan-vagues.md).

## 7. Ce qui n'a pas pu être vérifié (protocoles prêts)

- **Mesures appareil ART** (latences perçues, GC, thermique, dex en charge)
  — gabarit [07](07-gabarit-mesure-appareil.md) (rails existants :
  `scripts/kit-bascule.sh`, kit enrichi doc 11 §2.2) ;
- **Captures Android Studio** (comportements de la référence) — protocole
  [06](06-protocole-captures-android-studio.md), lot E + kit Kotlin, les
  lots A–D Java restent valables côté Java ;
- **P1(b)/P2/P3/P7 sur corpus Android avec classpath compilé** — manifestes
  (LISEZMOI du corpus §2), exige un SDK Android ;
- **Coût dex précis du palier 2** — mesuré à V-B1 (garde E-K8).

## 8. Décisions demandées au propriétaire (avec recommandation)

1. **Réouverture de l'ADR 0107 option 2** (typage étendu maison borné =
   palier 2, ADR 0123) — **RECOMMANDÉ : OUI** (la mesure V0 attribue des
   familles entières de FP à la représentation à 2 cas ; sans palier 2, pas
   de smart honnête ni de couverture des constructions fréquentes).
2. **Construction du palier profond** (ADR 0124) — **RECOMMANDÉ : décider
   APRÈS les deux mesures** (passe gabarit 07 enrichi + confirmation hôte
   Q-K7) ; l'option pack dex est rejetée sur les chiffres S1.
3. **Nouvelle enveloppe dex/taille** (ADR 0133) — **RECOMMANDÉ : gel du
   socle (≤ 26 590 / 4,10 Mo) + packs optionnels** ; le plafond 32 768
   méthodes reste non négociable pour le socle tant qu'aucune mesure
   appareil ne documente le comportement au-delà.
4. **Séquencement de la bascule CodeIDE** — **RECOMMANDÉ : bascule AVANT
   l'extension des capacités** (V-A), publication du pack comprise (Q-K4).
5. **Arbitrages Q-K1/Q-K2/Q-K5/Q-K6** — propositions instruites aux ADR
   0132/0123/0129/0135 ; Q-K2 (décodeur maison) demeure levier n° 1 du
   budget, non urgent tant que le gel tient.
6. **Modèle de projet partagé avec la mission Java** — **RECOMMANDÉ :
   OUI** (consommation `:model` v2 + référentiel de ressources partagé,
   D2/D3, jamais de double composant — ADR 0127).
7. **Seuils de qualité et cibles de vitesse** — FP/1 000 < 0,5, top-1 ≥
   0,80 (50+ positions split train/éval), P6 ≥ 0,99, rappel mutants ≥
   0,95 ; cibles §0.1 maintenues jusqu'à la passe appareil (miroir D5
   Java) — ADR 0135.
8. **Axes de dépassement retenus** — les 5 axes §C de la référence
   (lecture disque avant sync, réponses annulables + p99, empreinte,
   décidables Compose/DSL instantanés, cohérence inter-langages) ; les
   non-dépassements écrits noir sur blanc (catalogue d'inspections,
   fidélité compilateur par frappe, complétion apprise).
9. **Enveloppe/ordre des vagues** — 112–192 j-h agent, séquence §6,
   recalage après V-A.
10. **Points de procédure tracés à valider** : re-signature du digeste de
    parité (E-K9), exclusion du corpus de la fiche 010 (E-K12), kit de
    captures en `.kt.txt` (E-K13) ; corpus : arbitrer MPL-2.0
    (thunderbird-android) pour le rôle mixte si souhaité.

## 9. État de la CI et des livrables

- CI de l'état audité : **VERTE 10/10** (run `41170597847` sur `7b2f972`,
  attestée au doc 11 §4).
- **Attestation CI de la mission (31/10/2026)** : run **`41195132011`** sur
  le commit de clôture **`c650186b3d553dee77b8daf0f52713c162f5180b`** —
  **CI VERTE 10/10** (les dix jobs verts : Build et tests JDK 21, banc
  empreinte/latence noyau, banc qualité complétion Java baseline signée,
  gates Android SDK 34, banc parité croisée T, suite du module Kotlin
  — 523 tests du cahier + 11 tests du banc P1–P9 —, scan ART + dexage d8
  sous les seuils étage 0, manifeste de provenance anti-fichier-hors-
  registre, bancs empreinte/latence et qualité complétion Kotlin baseline
  signée). Les deux premiers jobs (`kotlin-parite`, `build`) avaient
  échoué sur **rate-limit Maven Central (429)** — même classe de panne
  transitoire que l'alerte AL4 de la mission Java (artefact
  `annotations:26.0.2` résolu localement le même jour) — relancés une
  fois, **verts à la première relance, aucune reprise de code**.
- Vérifications locales préalables (R5) : `./gradlew :kotlin:test` complet
  **BUILD SUCCESSFUL en 5 min 45 s** (534 tests) ; banc P1–P9
  **BUILD SUCCESSFUL en 5 min 02 s** (11 tests, mesures consignées à
  l'audit §4.3) ; `verifier-push.sh` concluant avant chaque push.
- Livrables : documents `00` à `07` + `ALERTES` + 12 fiches `KC-01…12` +
  kit/gabarits de captures + ADR `0122`–`0136` (Proposé) + 8 amendements
  datés + banc P1–P9 + corpus épinglé + DEMANDES-INTERFACE/PROVENANCE/
  AGENTS mis à jour — 32 commits `lsp-kotlin(…)` sur `df3cf85..c650186`.

**STOP** — conformément au §10 du prompt : aucune vague d'implémentation
n'est lancée au-delà de la V0 (outillage de mesure). Les prompts
d'exécution vague par vague attendent la décision du propriétaire.
