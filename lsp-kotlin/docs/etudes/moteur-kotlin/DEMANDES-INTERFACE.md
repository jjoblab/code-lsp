# DEMANDES-INTERFACE — évolutions des modules partagés demandées par le moteur Kotlin

> R3 du prompt : le moteur Kotlin ne modifie **ni** `:model`, **ni**
> `:index`, **ni** `:java` (lecture seule). Toute évolution nécessaire de
> ces modules partagés est consignée ici ; **le propriétaire arbitre**.
> Ce document est vivant : chaque demande porte son statut
> (proposée / arbitrée / rejetée) et le renvoi vers l'ADR de l'étude qui
> la motive.

## Statut au 2026-10-30

| # | Demande | Module visé | Motivation (ADR) | Statut |
|---|---|---|---|---|
| D-K1 | **Aucune pour la Phase 0** — l'inventaire et le cahier n'exigent aucune évolution des modules partagés | — | — | — |

> **Post-clôture (31/10)** : le programme d'exécution du plan §4 est clos
> (journal 10) ; les actions de licence P-K1/P-K2 sont LEVÉES par décision
> du propriétaire. Q-K1–Q-K6 demeurent OUVERTES (Q-K1/Q-K2 partiellement
> arbitrées par la livraison — index dédié `.kidx`, ADR 0108). Le registre
> des restes au propriétaire vit dans
> [`11-decisions-proprio-post-cloture.md`](11-decisions-proprio-post-cloture.md) §2.

## Demandes anticipées (hors Phase 0 — à confirmer par les ADR de domaine)

> Ces points sont des **questions ouvertes** consignées pendant l'étude ;
> ils ne deviennent des demandes formelles que si l'ADR 0100 retient une
> voie qui les rend nécessaires.

| # | Sujet | Question ouverte | Module | ADR porteur |
|---|---|---|---|---|
| Q-K1 | Extension CIDX aux appelables Kotlin | L'index CIDX (`:index`, repris de l'ancien dépôt) décrit les membres des `.class` Java ; l'ancien moteur Kotlin maintient un **second** index disque (`.kidx`, clés `top:`/`ext:`) pour les appelables Kotlin. Faut-il (a) étendre CIDX aux clés taggées Kotlin, (b) garder un index dédié dans `:kotlin`, ou (c) fusionner les formats ? | `:index` | ADR 0108 (K5) |
| Q-K2 | Décodage `@Metadata` dans `:index` | Même question pour le décodeur `kotlin-metadata-jvm` : vit-il dans `:index` (partagé Java/Kotlin) ou dans `:kotlin` ? | `:index` | ADR 0108 |
| Q-K3 | Types du modèle de symboles | Le futur modèle Kotlin (K2) doit-il partager des types avec `:model` (`ProjetLsp`, classpath v8) ou rester autonome derrière le SPI ? | `:model` | ADR 0105 |
| Q-K4 | Contrat de pack langue | Le descripteur `version.properties` (pack Kotlin téléchargeable, D6 ancien) doit-il s'aligner sur le mécanisme de publication `:java` (GitHub Packages, façade unique) ? | `:java` (convention) | ADR 0118 |
| Q-K5 | Interop croisée | Le moteur Java expose-t-il ses stubs/bindings au moteur Kotlin (navigation croisée) par une interface publique documentée ? | `:java` | ADR 0110 |
| Q-K6 | Banc de qualité commun | Le banc ADR 0016 (recall/top-1/MRR, corpus fixe) doit être étendu au Kotlin « banc commun avec Java » (critère §4 du prompt) — le corpus et les métriques vivent-ils dans `:testing` ? | `:testing` | ADR 0119 |

## Mises à jour du 2026-10-31 — mission « niveau Android Studio »

> La mission d'étude « niveau Android Studio » (documents
> [`../niveau-android-studio-kotlin/`](../niveau-android-studio-kotlin/))
> a instruit les questions ouvertes et en a levé de nouvelles. ADR de
> renvoi : 0122–0136 (statut **Proposé** — rien n'est figé avant
> arbitrage du propriétaire).

| # | Sujet | État après la mission | ADR |
|---|---|---|---|
| Q-K1 | CIDX vs `.kidx` | **instruite** : l'index dédié `.kidx` (livré) suffit ; un éventuel index d'USAGES persistant ne se construit QUE si les mesures dépassent les seuils — la fusion CIDX globale reste une décision de dépôt | 0132 |
| Q-K2 | Décodage `@Metadata` | **demeure OUVERTE** — confirmée levier n° 1 du budget dex si la marge fond ; le palier 2 (récepteurs génériques) en augmente la valeur | 0123, 0133 |
| Q-K3 | Types du modèle de symboles | **inchangée** (autonomie derrière le SPI conservée) | — |
| Q-K4 | Contrat de pack langue | **DEVIENT PRÉALABLE P0** : la publication du pack Kotlin (coordonnées à ajouter à `publish.yml`) conditionne la bascule CodeIDE — mécanisme débloqué par la levée de P-K2 | 0136 |
| Q-K5 | Interop croisée | **instruite** : contrat segment CIDX partagé (le moteur Kotlin devient l'écrivain), arité + nullité androidx côté lecteur, rename croisé par phases partagées | 0129 |
| Q-K6 | Banc de qualité commun | **instruite** : le cadre P1–P9 (miroir ADR Java 0030) est livré dans `:kotlin` en V0 ; l'hébergement commun dans `:testing` reste une option de dépôt | 0135 |
| **Q-K7 (nouvelle)** | **Environnement déporté hôte** | Le palier 3 (mode profond K2, option processus déporté JVM Termux/PRoot) exige que l'hôte CodeIDE dispose d'un tel environnement — **confirmation hôte requise** avant tout arbitrage | 0124 |
| **Q-K8 (nouvelle)** | **SPI observateur mémoire** | `onTrimMemory` est absent du moteur ; la branche propre exige que l'HÔTE appelle un SPI neutre d'observation mémoire (vidange des caches bornés) — interface à convenir | 0134 |
| **Q-K9 (nouvelle)** | **Phases interop partagées** | Le rename/navigation inter-langage se livrent par phases synchronisées avec la mission Java (son ADR 0022 prévoit « il écrit, nous lisons ») — calendrier commun à convenir | 0129 |
