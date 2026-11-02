# ADR 0122 — Architecture sémantique à paliers (1/2/3) et provenance des réponses

- **Statut** : Proposé
- **Date** : 2026-10-31
- **Amende** : ADR 0103 (option iii), ADR 0107 (option 1), ADR 0112 (mode profond)
- **Écarts** : GK-001, GK-002, GK-003, GK-005 (matrice `03-matrice-ecarts.md`)

## Contexte

Les ADR 0103/0107/0112 ont fixé : parseur officiel + modèle conservateur +
typage **borné** (2 cas) + « preuve ou silence » + mode profond K2 **différé**
(« jamais construit »). Ce choix a livré le programme clos sur preuve
(469/469, CI 10/10), mais le banc V0 de cette mission mesure sur l'auto-corpus
(99 fichiers / 34 934 lignes de Kotlin compilable) **980 faux positifs =
28,05 FP/1 000 lignes** là où l'oracle K2 en trouve 1 — la « preuve ou
silence » tient sur le corpus de 86 lignes du dépôt, pas sur du code réel
(audit §4.3) : la frontière de preuve actuelle n'est pas la frontière du
langage (imports implicites `kotlin.*`, `it` sur extension générique, `this`
nu, paramètres de type). Atteindre le niveau Android Studio exige donc de
rouvrir la décision **avec des chiffres**, en combinant trois paliers.

## Sources consultées

- Audit `01-audit-moteur.md` §1.1, §1.2, §4.3 (mesure FP 28,05/1 000 l.).
- Référence `02-reference-android-studio.md` §A1 (résolution paresseuse par
  phases du mode K2 — le modèle à comprendre, pas à copier).
- Spike `02-spike-mesures.md` S0–S3 (K2 embarqué disqualifié : 53,6 Mo /
  64 507 méthodes / ~2 170 motifs ART / 3 503 ms d'amorçage).
- Mission jumelle Java : synthèse §8 (décisions D1–D9 approuvées).

## Options

| Option | Description | Critères |
|---|---|---|
| **1 — Statu quo palier 1 seul** | typage 2 cas, catalogue figé | FP mesuré 28,05/1 000 l. : le moteur est **faux à l'échelle** — écarte le statu quo comme état cible ; rester en palier 1 n'est acceptable que le temps de corriger la baseline |
| **2 — Paliers 1+2+3 combinés (recommandée)** | palier 1 = actuel (corrigé) ; palier 2 = typage étendu maison (ADR 0123) ; palier 3 = K2 profond à la demande (ADR 0124) | couverture visée sans faux positifs, chaque palier a SES critères de sortie mesurés ; « preuve ou silence » **conservée par palier** (chaque palier ne dit que ce qu'il prouve) |
| **3 — K2 embarqué par frappe** | compilateur dans le chemin de frappe | disqualifié par la mesure S1 (53,6 Mo / 3,5 s d'amorçage) et par §11 du prompt |
| **4 — « Presque-K2 » maison intégral** | inférence complète maison | écarté par ADR 0107 (« le pire des deux mondes ») — le palier 2 s'arrête à une frontière EXPLICITE (ADR 0123 §critère d'arrêt) |

## Critères et mesures

- Règle d'arbitrage inter-paliers : une requête est servie par le **palier le
  plus bas qui sait la prouver** ; si le palier N ne sait pas, il **sous-rapporte**
  (repli) et la réponse porte sa **provenance** (`palier 1/2/3`) — jamais une
  liste ou un diagnostic mensonger ;
- cible FP/1 000 lignes par palier : palier 1 **< 0,5** (mesuré 28,05 —
  correction V-A), palier 2 < 0,5 **maintenu** sur corpus étendu, palier 3 = 0
  par construction (vérité compilateur) ;
- budget dex par palier : à mesurer à chaque vague (garde E-K8, ADR 0133) ;
- latence : le chemin de frappe reste **intégralement palier 1/2** (< 100 ms
  p95 perçu, cible §0.1) ; le palier 3 n'est JAMAIS sur la frappe.

## Décision proposée

**Option 2** : architecture à trois paliers avec « preuve ou silence »
conservée à chaque niveau et **étiquetage de la provenance de chaque
réponse**. Corollaires : (a) la baseline FP du palier 1 est corrigée en
première vague (V-A) AVANT toute extension ; (b) l'ADR 0107 option 2
(« croître l'inférence maison ») est **rouverte au périmètre borné de
l'ADR 0123** — ce n'est pas le « presque-K2 » rejeté, c'est un palier
versionné avec test différentiel contre l'oracle K2 ; (c) le palier 3
(0124) n'est construit que si le propriétaire l'arbitre après lecture des
chiffres.

## Conséquences

- Le contrat « zéro FP » devient **mesuré à l'échelle** (banc V0), plus
  déclaré ;
- chaque capacité livrée cite son palier (KDoc + réponse protocole) ;
- la CI gère des gates par palier (ADR 0135).

## Plan de migration progressive

V-A (correction baseline FP palier 1) → V-B (palier 2 par familles, test
différentiel à chaque étape) → V-H (palier 3 si arbitré). Aucun big-bang ;
drapeau pack + repli documenté à chaque vague (§8 du plan).

## Tests de non-régression et critères de sortie

- P1 auto-corpus FP < 0,5/1 000 l. en mode strict ; P1(b) corpus externe
  idem (manifestes) ; FN baselinés par famille ; latence frappe p95 ≤
  baseline + 10 % (ADR 0135).

## Risques

- R1 — Le palier 2 dérape en « presque-K2 » : mitigation = frontière
  versionnée + critère d'arrêt écrit (0123) + différentiel oracle ;
- R2 — La provenance alourdit le protocole : mitigation = champ optionnel
  neutre, consommé à discrétion de l'hôte ;
- R3 — Dérive du budget dex : garde E-K8 inchangée + packs (0133).
