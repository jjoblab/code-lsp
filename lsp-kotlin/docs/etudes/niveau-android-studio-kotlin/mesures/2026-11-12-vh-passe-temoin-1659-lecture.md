PV DE LECTURE — PASSE APPAREIL 1 DU GABARIT 07 ENRICHI (V-H)
(moto g06, 12/11/2026 16:59:24 heure appareil)
====================================================================

R5 : AUCUNE valeur fabriquée. Tous les chiffres ci-dessous viennent du
transcript brut joint (mesures/2026-11-12-vh-passe1-transcript.txt,
jamais retouché) — reçu de l'appareil de référence. Les facteurs et
ratios sont calculés par code (scripts/lecture-passe1.py), jamais de
tête. La passe est la N°1 sur 3 (gabarit 07 §2 : médiane des 3 passes
espacées de ≥ 30 s) ; la lecture des seuils est assortie de la marge
d'inversion — nulle partout ici (voir §5).

--- Le contexte de la passe -----------------------------------------

Appareil : moto g06, Android 15, aarch64 — bac déporté `com.acside`
(OpenJDK 17.0.19, 8 cœurs, 3,8 Go total / 567 Mo libres au moment de
la passe). Kit : kit-mesure-kotlin.zip (sha256
ac10835c…9749a8f5, assemblé le 12/11/2026), moteur b0bb9ce. Charge
(mode avion, autres applications) : NON consignée dans le transcript —
à consigner aux passes 2-3.

--- 1. Le chemin de frappe : VERT partout ---------------------------

| Mesure | p95 appareil | Seuil §0.1 | Marge |
|---|---|---|---|
| F1 complétion membres | 9 ms | < 100 ms | ×11 |
| F2 complétion noms | 13 ms | < 100 ms | ×7,7 |
| F3 aide signature | 4 ms | < 100 ms | ×25 |
| F4 hover | 2 ms | < 100 ms | ×50 |
| F6 régularité (p99, milieu du géant) | 34 ms | régularité | latence max 34 ms, aucune fige |
| C1 contention | 6 ms (×1,00 du nominal 3 ms) | — | aucune dégradation |
| S1 démarrage à chaud | 212 ms | < 3 000 ms | ×14 |
| Q1 édition A → rouge B | 97 ms | < 300 ms | ×3,1 (0 périmé servi) |

Le moteur Kotlin tient les cibles de vitesse §0.1 sur l'appareil de
référence avec des marges de ×3 à ×50 : la mission « niveau Android
Studio » a sa baseline appareil. La comparaison avec la passe moteur
JAVA du 30/10 (mêmes seuils, complétion membres ≈ 400-460 ms p95) est
à l'avantage du moteur Kotlin d'un facteur ≈ 40× — sans commentaire de
cause au-delà de la mesure (architectures distinctes, la comparaison
n'est pas le sujet de ce PV).

--- 2. F5 — l'écart du document géant (mesuré, borné) ----------------

Diagnostics du document 1 803 l. (150 fonctions, miroir fiche 021),
texte muté à chaque itération : **1 346 / 1 531 ms p50/p95** — 5,1× le
seuil de 300 ms. Lectures :

- l'écart est PROPRE au document géant : le diagnostic des documents
  de travail (Q1 : rouge inter-fichiers servi en 97 ms) est tenu ;
- le facteur bac à sable → appareil sur F5 p95 est ×6,05 (253 ms →
  1 531 ms, JDK 21 bureau → moto g06) : l'écart existait déjà au bac
  (253 ms > 300 ms ? non — 253 ms < 300 ms : le bac le tenait, l'appareil
  ne le tient plus ; le seuil de 300 ms était calibré sur les repères
  bac, pas sur une passe appareil réelle — c'est exactement ce que la
  passe devait révéler, gabarit 07 §5 : « le facteur ≈ 4× ne se
  projette pas ») ;
- proposition de recalibrage (à trancher avec les passes 2-3) :
  soit le budget F5 géant passe à ~1 600 ms, soit le corpus de la
  cible se recentre sur les documents de travail (le géant 1 803 l.
  est un cas d'extrême volontaire, pas la frappe courante).

--- 3. La passe V-H (la mesure qui manquait au verdict) --------------

| Mesure | Cible ADR 0124 | Passe 1 | Facteur |
|---|---|---|---|
| H1 premier diagnostic profond | < 10 000 ms | 27 163 ms | ×2,7 manqué |
| H2 diagnostic chaud (option 2, sans démon) | < 2 000 ms | 27 129 ms | ×13,6 manqué |
| H3 mémoire additionnelle | < 300 Mo | 0 Mo (tas banc) ; appareil : non concluante | — |

La cause est MESURÉE, pas devinée : **H2 == H1 à 34 ms près** —
l'option 2 relance le sous-processus K2 à CHAQUE requête, l'amorçage
du compilateur (~27 s sur l'appareil) est RE-payé à chaque diagnostic.
Il n'existe PAS de diagnostic « chaud » sans démon dans l'option 2 :
c'est la donnée quantitative qui manquait à l'arbitrage entre les
options 2 (processus déporté à la demande) et 3 (démon résident chaud)
de l'ADR 0124 — la passe la produit.

Facteur bac → appareil sur H : ×12,4/×11,8 (2 197/2 297 ms au bac
JDK 21 → 27,2 s sur moto g06) : l'amorçage du compilateur K2 est le
poste massivement dominant sur mobile — là encore, le facteur du
chemin de frappe (×1,3 à ×1,7) ne se projette pas sur le compilateur.

H3 : le banc mesure son PROPRE tas (0 Mo de delta — le sous-processus
K2 vit hors process). L'empreinte appareil du mode profond (via
/proc/meminfo autour d'une section H) reste à relever aux passes
suivantes — MAIS ce relever ne peut plus sauver les seuils H1/H2.

--- 4. Q-K7 : confirmé, avec la nuance qui change la sonde ----------

L'environnement déporté existe et a PORTÉ la passe : le JDK 17.0.19
aarch64 a tourné, le compilateur K2 a compilé (27 s de compilation
réelle, erreur détectée, provenance « palier 3 »). Nuance : le bac
est `com.acside` — le bac déporté de l'HÔTE lui-même (préfixe
/data/data/com.acside/files/usr/), pas le Termux du commerce. La sonde
K7 du banc cherchait com.termux : elle a imprimé « NON détecté » alors
que le chemin du JDK faisait foi. Correction à porter au banc pour les
passes 2-3 (détection par le préfixe de chemin du java exécuté, pas
par le paquet) — la confirmation Q-K7 elle-même est ACQUISE sur la
preuve d'exécution, pas sur la string de détection.

--- 5. Marges d'inversion (pourquoi la lecture tient sans passes 2-3)

- seuils MANQUÉS par ×2,7 (H1), ×13,6 (H2), ×5,1 (F5) — aucun écart
  de mesure entre passes (même thermique : facteur ×1,3-1,7 observé
  entre bac et appareil sur les mesures STABLES) ne peut combler un
  facteur ×2,7 ;
- seuils TENUS par ×3,1 (Q1) à ×50 (F4) — aucune dégradation de passe
  ne les renverse ;
- la cause H2 == H1 est STRUCTURELLE (relance du sous-processus à
  chaque requête), pas environnementale : elle ne variera pas entre
  les passes.

Les passes 2-3 restent à jouer pour la MÉDIANE du gabarit (§2) et la
consigne de charge ; elles ne sont pas nécessaires à la lecture des
seuils ci-dessus.

--- 6. Verdict proposé à l'arbitrage du propriétaire -----------------

Sur la passe 1, la mesure (a) de l'ADR 0124 ÉCHOUE :

- **option 2 (sans démon) : REJET MOTIVÉ** — H1 ×2,7 et H2 ×13,6 hors
  seuils, cause structurelle mesurée (amorçage re-payé à chaque
  requête). Conformément à l'ADR : le palier 2 reste le plafond de
  fidélité ;
- **l'option 3 (démon résident) devient la seule voie crédible vers
  les seuils** — son potentiel est maintenant CHIFFRÉ par cette passe :
  l'amorçage de ~27 s serait payé UNE fois, le diagnostic chaud
  deviendrait le temps de compilation SEUL (l'ordre de grandeur bac
  de la compilation sans amorçage reste à isoler : candidat pour la
  re-passe). Instruction de l'option 3 = décision du propriétaire ;
- **Q-K7 : ACQUIS** (l'environnement déporté a porté la passe) ;
- **la baseline appareil du chemin de frappe : ACQUISE** (F1-F4, S1,
  F6, C1, Q1, Q2, M1, M2 — proposition : figer la passe 1 comme
  baseline, gabarit §6 (i)).

La décision finale appartient au propriétaire (ADR 0124 §10).
