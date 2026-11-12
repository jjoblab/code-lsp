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

## 4. La passe appareil 1 — les mesures sont PRODUITES (12/11/2026)

> Suite de la même journée : la passe 1 du gabarit 07 enrichi a été jouée
> sur l'appareil de référence (kit du commit 10c2a6d, moteur b0bb9ce).
> Transcripts et lecture chiffrée : `../mesures/2026-11-12-vh-passe1-transcript.txt`
> (brut, jamais retouché) et `../mesures/2026-11-12-vh-passe1-lecture.md`
> (PV de lecture — tous les facteurs calculés par code). Le gabarit 07 §7
> porte le tableau de relevé.

| Mesure | Cible ADR 0124 | Passe 1 | Facteur |
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
  sonde à porter aux passes 2-3 ;
- **la mesure (a) ÉCHOUE sur la passe 1** (H1 ×2,7, H2 ×13,6 — marges
  d'inversion nulles, PV §5) : conformément à l'ADR 0124, **rejet motivé
  de l'option 2 sans démon** ; le palier 2 reste le plafond de fidélité ;
- **l'option 3 (démon résident) devient la seule voie crédible vers les
  seuils** : son potentiel est CHIFFRÉ (amorçage payé une fois) —
  l'instruire est un acte du propriétaire, ce journal ne la décide pas ;
- au passage, la même passe a produit la **baseline appareil du chemin
  de frappe** (F1-F4 p95 ≤ 13 ms, S1 212 ms, Q1 97 ms, zéro périmé —
  gabarit 07 §4/§6) et un écart propre au document géant (F5 : 1 531 ms
  p95, ×5,1 — recalibrage proposé au §6).

Le verdict final (rejet/instruction de l'option 3) reste au
propriétaire (ADR 0124 §10) ; les passes 2-3 (médiane du gabarit,
consigne de charge, relever H3 par /proc/meminfo) restent à jouer mais
ne peuvent renverser la lecture (PV §5).
