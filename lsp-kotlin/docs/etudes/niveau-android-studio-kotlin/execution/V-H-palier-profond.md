# Journal d'exécution — Vague V-H : Palier profond K2 (P3, ADR 0124)

> Vague V-H du [plan](../04-plan-vagues.md) — exécutée le **12/11/2026**.
> Le plan conditionne la vague à **l'arbitrage du propriétaire** après
> les mesures (gabarit 07 sur moto g06) et la confirmation hôte de
> l'environnement déporté (Q-K7) — l'ADR 0124 instruit l'option 2
> (processus déporté, avec sa variante démon 3) et rend le verdict
> **après deux mesures**. Ce que cette session livre : le **GERME de
> l'option 2** — l'oracle du banc de fidélité (`BancFideliteK2Test`,
> le compilateur K2 VÉRITABLE en sous-processus — « l'oracle EST le
> mode profond en germe », ADR 0124 §Critères) transformé en **service
> optionnel derrière drapeau**, désactivé par défaut, conformément au
> protocole écrit. Le verdict final (retenir/rejeter) reste au
> propriétaire sur les mesures appareil.

## 1. Ce qui est livré

1. **`ModeProfondK2`** (moteur) : le service profond optionnel —
   **DÉSACTIVÉ par défaut** (jamais sur le chemin de frappe : le
   palier 2 reste le plafond de fidélité tant que l'arbitrage n'est
   pas rendu) ; `activer()`/`desactiver()` sont les actes de
   **l'utilisateur** (le moteur ne s'active JAMAIS de lui-même) ;
   `erreursProfondes()` rend `null` service éteint (**repli palier
   1/2 immédiat, CI inchangée service éteint** — critère de sortie du
   plan), et les erreurs du compilateur déporté sinon.
2. **La provenance `palier 3` étiquetée** (ADR 0122 : chaque réponse
   cite son palier) : `DiagnosticProfondKotlin.provenance` = toujours
   `"palier 3"` — jamais une réponse du palier 3 ne se fait passer
   pour une réponse du palier 1/2.
3. **L'oracle du banc en service** : le protocole du banc de fidélité
   repris tel quel (sous-processus `K2JVMCompiler`, `unresolved
   reference` relevés) — la MÊME frontière, en service optionnel borné ;
   l'échec du processus déporté est un **repli silencieux** (liste
   vide, jamais une exception vers l'appelant — D3 : le palier 1/2
   reprend).
4. **La garde CI** : le compilateur K2 vit en `testImplementation`
   (`kotlin-compiler-embeddable`, miroir du banc) — les tests de
   service se gardent sur sa présence au classpath : **sans la
   dépendance, les tests passent en repli honnête** (le service rend
   vide — la CI sans la dépendance reste verte).

## 2. Les preuves

| Preuve | Résultat |
|---|---|
| Tests moteur (`ModeProfondK2Test`) | **5 verts** — drapeau désactivé par défaut + repli `null` immédiat ; activation/désactivation par l'utilisateur ; l'oracle voit la référence introuvable avec la provenance `palier 3` ; un fichier sain ne produit rien ; un échec du processus déporté est un repli silencieux |
| Suite `:kotlin:test` | attendue 613 verts (608 + 5), 3 ignorés (gates corpus), 0 régression |
| Re-signature du digeste (E-K9) | par la COMMANDE OFFICIELLE fiche 010 — 2 fichiers neufs |
| CI inchangée service éteint | le drapeau est OFF par défaut : aucune requête du chemin de frappe ne sollicite le service (le branchement aux diagnostics attend l'arbitrage) |

## 3. Bornologie (honnêteté D3)

- **Le verdict final N'EST PAS RENDU** : l'ADR 0124 exige les mesures
  appareil (premier diagnostic profond < 10 s, chaud < 2 s, mémoire
  additionnelle < 300 Mo — gabarit 07 sur moto g06) et la confirmation
  hôte Q-K7 (l'environnement Termux/PRoot de l'hôte) : ni l'une ni
  l'autre ne sont produisibles dans cette session — le germe est livré,
  l'arbitrage reste au propriétaire ;
- le service n'est PAS branché au chemin des diagnostics : le
  branchement (UX du délai annoncé, annulation, provenance dans la
  réponse protocole) est CONDITIONNÉ à l'arbitrage — livrer le
  branchement maintenant anticiperait le verdict (le péché inverse de
  KH16 : « livré mais pas en service » serait ici « en service sans
  arbitrage ») ;
- la variante démon (option 3 : processus résident chaud) n'est pas
  prototypée — elle partage la même frontière, son coût mémoire résident
  est une donnée de l'arbitrage ;
- les cibles chiffrées (< 10 s / < 2 s / < 300 Mo) restent des CIBLES à
  valider (ADR 0124 : « cible, à valider »).

## 4. La passe appareil témoin — première mesure (12/11/2026, 16:59)

> La première passe jouée sur l'appareil de référence (kit du commit
> 10c2a6d, moteur b0bb9ce). Elle est conservée comme PASSE TÉMOIN
> (transcript : `../mesures/2026-11-12-vh-passe-temoin-1659-transcript.txt`,
> brut, jamais retouché ; lecture : `../mesures/2026-11-12-vh-passe-temoin-1659-lecture.md`)
> — la SÉRIE protocolaire des 3 passes (§5 ci-dessous) la remplace
> comme référence des chiffres officiels.

| Mesure | Cible ADR 0124 | Témoin 16:59 | Facteur |
|---|---|---|---|
| H1 premier diagnostic profond | < 10 000 ms | 27 163 ms | **×2,7 manqué** |
| H2 diagnostic chaud (option 2, sans démon) | < 2 000 ms | 27 129 ms | **×13,6 manqué** |
| H3 mémoire additionnelle | < 300 Mo | 0 Mo (tas banc ; appareil non concluant) | — |
| Q-K7 environnement déporté | confirmé | **OUI** — `com.acside`, OpenJDK 17.0.19 aarch64 | — |

- **la cause est mesurée, pas devinée** : H2 == H1 (delta 34 ms) —
  l'option 2 RELANCE le sous-processus K2 à chaque requête, l'amorçage
  (~27 s sur l'appareil) est RE-payé à chaque diagnostic : il n'existe
  PAS de « chaud » sans démon dans l'option 2. C'est la donnée
  quantitative qui manquait à l'arbitrage options 2/3 ;
- **Q-K7 ACQUIS** (l'environnement déporté de l'hôte a porté la passe) —
  avec une nuance de sonde : le banc cherchait `com.termux`, l'hôte
  porte `com.acside` (le bac déporté de l'application elle-même) ; la
  confirmation tient sur la preuve d'exécution (chemin du JDK,
  compilation réelle), pas sur la string de détection — correction de
  sonde à porter aux re-passes ;
- **la mesure (a) ÉCHOUE sur le témoin** (H1 ×2,7, H2 ×13,6) :
  conformément à l'ADR 0124, rejet motivé de l'option 2 sans démon ;
  le palier 2 reste le plafond de fidélité ;
- **l'option 3 (démon résident) devient la seule voie crédible vers les
  seuils** : son potentiel est CHIFFRÉ (amorçage payé une fois) —
  l'instruire est un acte du propriétaire, ce journal ne la décide pas ;
- au passage, le témoin a produit la première **baseline appareil du
  chemin de frappe** (F1-F4 p95 ≤ 13 ms, S1 212 ms, Q1 97 ms, zéro
  périmé — gabarit 07 §4/§6) et un écart propre au document géant
  (F5 : 1 531 ms p95, ×5,1 — recalibrage proposé au §6).

## 5. La série protocolaire des 3 passes — la mesure OFFICIELLE (12/11/2026, 17:07-17:25)

> Le protocole du gabarit 07 §2 (3 passes espacées de ≥ 30 s, médiane
> retenue) est maintenant COMPLET : passes jouées à 17:07, 17:11, 17:25
> heure appareil (espacements 3 min 37 s puis 14 min 03 s). Transcripts
> bruts : `../mesures/2026-11-12-vh-passe1-1707-transcript.txt`,
> `…passe2-1711-transcript.txt`, `…passe3-1725-transcript.txt` ;
> PV de lecture (médianes et facteurs calculés par code,
> `scripts/lecture-serie.py`) : `../mesures/2026-11-12-vh-serie-3passes-lecture.md`.

| Mesure | Cible | P1 17:07 | P2 17:11 | P3 17:25 | Médiane | Verdict |
|---|---|---|---|---|---|---|
| H1 1er profond (ms) | < 10 000 | 27 308 | 18 985 | 18 647 | **18 985** | **manqué ×1,9** |
| H2 profond chaud (ms) | < 2 000 | 24 809 | 18 956 | 18 736 | **18 956** | **manqué ×9,5** |
| H3 mémoire (Mo, tas banc) | < 300 | 0 | 0 | 0 | 0 | non concluant (hors process) |
| Q-K7 | confirmé | OUI | OUI | OUI | — | **ACQUIS (4 exécutions)** |

- **H2 ≈ H1 confirmé 3/3** (deltas −2 499 / −29 / +89 ms ; ratios
  0,908 / 0,998 / 1,005 ; témoin : −34 ms, 0,999) : 4 exécutions
  indépendantes reproduisent la même cause STRUCTURELLE — l'option 2
  re-paye l'amorçage du sous-process K2 à chaque requête. La lecture
  du témoin est CONFIRMÉE par la série ;
- **tendance thermique consignée** : H1 27 308 → 18 647 ms (−32 % ;
  S2 suit à −45 %, les mesures stables restent stables) — le premier
  « sursaut » de l'appareil est le cas PIRE ; l'appareil se stabilise
  vers ~18,6-19 s. La MEILLEURE passe reste ×1,86 / ×9,4 des cibles :
  aucune inversion possible (PV de série §4) ;
- **la mesure (a) ÉCHOUE sur la série protocolaire** : rejet motivé de
  l'option 2 sans démon, CONFIRMÉ PAR LA SÉRIE ; le palier 2 reste le
  plafond de fidélité ;
- **l'option 3 (démon résident) reste la seule voie crédible** — son
  potentiel est borné par la série : l'amorçage de ~18,6-27,3 s (selon
  thermique appareil) serait payé UNE fois par session ; le temps de
  compilation SANS amorçage (le « chaud » d'un démon) reste à isoler
  par une passe dédiée — candidate à la re-passe après décision du
  propriétaire ;
- **la baseline appareil du chemin de frappe est ACQUISE SUR MÉDIANE**
  (F1-F4 p95 ≤ 9 ms, S1 249 ms, F6 p99 28 ms, C1 ×1,00, Q1 72 ms,
  zéro périmé 3/3, mémoire 8→12 Mo — gabarit 07 §4/§6) ; F5 géant
  reste hors seuil (médiane p95 1 411 ms, ×4,7 — recalibrage ~1 500 ms
  ou recentrage du corpus proposé au gabarit §6).

Le verdict final (rejet/instruction de l'option 3) reste au
propriétaire (ADR 0124 §10) ; ce qui restait ouvert après le témoin —
médiane, consigne de charge (toujours non consignée dans les
transcripts, nuance portée au PV), relever H3 par `/proc/meminfo` —
se réduit aux deux dernières cases ; la médiane est produite et ne
change AUCUN verdict.
