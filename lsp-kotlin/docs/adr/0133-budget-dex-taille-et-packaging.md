# ADR 0133 — Budget dex, taille et packaging : enveloppe et modularisation en packs

- **Statut** : Proposé
- **Date** : 2026-10-31
- **Amende** : alerte E-K8 (surveillance) ; ADR 0101 (descripteur de pack D6)
- **Écarts** : GK-004 (KH12)

## Contexte

Pile chaude : **26 590 méthodes / 4 294 352 o = 4,10 Mo** — 81 % du plafond
auto-imposé de 32 768 méthodes et 68 % du budget de 6 Mo (arbitré
propriétaire le 31/10). L'ambition « niveau Android Studio » (palier 2,
catalogue étendu, actions/refactorings, Gradle KTS, Compose) **consomme**
ce budget : chaque vague doit savoir où elle peut aller. Leviers consignés
(doc 11 §2.3) : Q-K2 (décodeur `@Metadata` maison), Q-K1 (index partagé),
réduction du câble lsp4j (déjà résolu : runtime-only porté par l'hôte).

## Sources consultées

- Alertes E-K8 du dépôt (historique chiffré vague par vague : 16 306 →
  26 590 méthodes) ; CI `kotlin-art` (gates : 5 × AUCUNE scan ART, d8
  ≤ 32 768 / ≤ 6 Mo).
- Mission Java : contrainte ~5 Mo (plan 04, en-tête §contraintes ART) ;
  ADR 0030 (seuils qualité).
- Référence §C (non-dépassements assumés — l'IDE de bureau pèse des Go).

## Options

| Option | Description | Critères |
|---|---|---|
| **1 — Gel du socle + packs à chargement à la demande (recommandée)** | le socle (noyau + session + protocole + paliers 1/2) est GELÉ à son niveau actuel (toute régression = CI rouge) ; les capacités lourdes vivent en **packs optionnels** (actions/refactorings, Gradle KTS, Compose étendu, profond 0124) chargés à la demande via le descripteur D6 ; enveloppe proposée : socle ≤ 26 590 méthodes + 4,10 Mo [gel], chaque pack ≤ +2 000 méthodes / +0,4 Mo, total packs ≤ +4 000 méthodes / +1,9 Mo (plafond packié implicite ≤ 49 152 / 6,0 Mo) | la garde CI existante est INCHANGÉE (le plafond 32 768 continue de s'appliquer au socle chargé par défaut) ; les packs sont opt-in utilisateur — l'empreinte par défaut n'augmente pas |
| **2 — Lever le plafond socle à 49 152** | tout dans le socle | le plafond 32 768 (« moitié du mur 64K ») est une marge de sécurité ART assumée du dépôt — le lever sans mesure appareil [NON VÉRIFIÉ] est imprudent |
| **3 — Statu quo sans packs** | tout dans le socle sous 32 768 | l'arbitrage précédent laisse ~6 178 méthodes de marge — insuffisant pour palier 2 + actions + Gradle KTS simultanés (estimation à confirmer V-B) |

## Critères et mesures

- Toute vague cite son coût dex **avant fusion** (miroir des journaux du
  programme initial : « +X méthodes / +Y o » consignés) ;
- le plafond 32 768 méthodes reste **non négociable pour le socle tant
  qu'aucune mesure appareil** (gabarit 07) ne documente le comportement
  au-delà — la question est RÉOUVERTE par cette ADR avec un protocole :
  si le propriétaire veut monter, la passe appareil le mesure d'abord ;
- leviers de financement : Q-K2 (décodeur maison, levier n° 1 documenté),
  R8 vendoring (exceptions de qualité), dead-code du câble.

## Décision proposée

**Option 1** : gel du socle + packs optionnels décrits au descripteur D6
(versionnés, chargeables à la demande, déchargeables à `onTrimMemory`),
garde CI inchangée par défaut. La décision de lever le plafond socle est
explicitement remise au propriétaire AVEC le protocole de mesure qui
l'éclairera (gabarit 07).

## Conséquences

- Le plan par vagues (§8) borne chaque vague par le budget du pack visé ;
- `DescripteurPackKotlin` (D6) apprend la liste des packs.

## Plan de migration progressive

V-B (mesure du coût réel du palier 2 sur le socle) → décision pack par
capacité aux vagues V-F/V-G/V-H.

## Tests de non-régression et critères de sortie

- Gates `kotlin-art` vertes à chaque vague (socle inchangé) ; chaque pack a
  sa propre empreinte mesurée et publiée ; aucun pack chargé par défaut.

## Risques

- R1 — Complexité de chargement à la demande sur ART : descripteur D6 déjà
  conçu pour ; mesure au gabarit 07 ;
- R2 — Sous-estimation du coût du palier 2 : la V-B mesure AVANT
  d'étendre (critère de sortie dex).
