# GABARIT DE RÉSULTATS — moteur Kotlin étage 0, benchmark sur appareil

> Moteur Kotlin (`:kotlin`, voie C — ADR 0100 acceptée). Ce gabarit est la
> procédure de retour des mesures **sur appareil** : le propriétaire exécute
> `banc-appareil.sh` (Termux), reporte les valeurs ci-dessous et joint le
> fichier au ticket de l'étage 1. **Aucune valeur de ce document n'a été
> pré-remplie avant la passe du 30/10 02:14:51** (R5 : jamais prétendre une
> mesure appareil sans résultats
> réels) — les chiffres entre parenthèses sont les LIMITES BASSES de bac
> à sable, désormais mesurées **sous JDK 17** (toolchain du module depuis
> le correctif du 30/10 — MÊME JVM major que l'appareil ; journal §14).
>
> **Statut : GABARIT REMPLI — PREMIÈRE PASSE APPAREIL COMPLÈTE ET VERTE
> (30/10 02:14:51, kit v2)**. La passe du 30/10 01:33 (kit v1) a levé
> `NoSuchMethodError : java.util.List.removeLast()` au premier parse : une
> référence **JDK 21 seule** vivait dans le bytecode 17 de la grammaire
> vendorée (invisible en bac à sable/CI JDK 21, où la méthode existe).
> Corrigé sur la branche (toolchain JDK 17 + gardes — journal §14, alerte
> E-K7). La passe du 30/10 **02:14:51 avec le kit v2** (sha256 `1352072e…`)
> a exécuté le banc grammaire, la tolérance, le tas ET le scan ART jusqu'au
> bout sur l'openjdk 17.0.19 de l'appareil — **« OK : banc terminé »**.
> Les valeurs ci-dessous sont reportées TELLES QUELLES de cette sortie
> brute (consignée en dépôt : `appareil/resultats-appareil-20261030-0214.txt`)
> — premières mesures appareil réelles, R5 tenu de bout en bout.
> L'exécution complète sous 17.0.19 n'était possible qu'avec le jar corrigé
> (le kit v1 lève l'erreur au premier parse — §14) : elle atteste le kit v2
> en place sur l'appareil.

## 1. Environnement déclaré (rempli — passe du 30/10 02:14:51)

| Champ | Valeur |
|---|---|
| Appareil (modèle) | moto g06 [déclaré propriétaire, passes du 30/10] |
| SoC / RAM totale | non déclaré par la passe |
| Version d'Android (API) | Android 15 [en-tête du banc : `aarch64 / 15 / moto g06`] |
| Runtime d'exécution (Termux openjdk-__ / AndroidIDE / dalvikvm…) | Termux openjdk **17.0.19** (2026-04-21) [en-tête du banc, passe 02:14:51] |
| Cœurs visibles de la JVM | 8 [mesuré par l'en-tête du banc sur l'appareil] |
| Mode (chargeur branché ? économie d'énergie ? autres apps) | non déclaré par la passe (passe unique) |
| Date et heure de la passe | 2026-10-30 **02:14:51** (heure appareil) |
| `resultats-appareil.txt` brut joint ? | **oui** — sortie brute reçue dans le canal de mission, consignée en dépôt : `appareil/resultats-appareil-20261030-0214.txt` |

NB : champs environnementaux pré-remplis des FAITS déclarés par les en-têtes
des passes du 30/10 ; les mesures ci-dessous sont reportées de la sortie
brute de la passe 02:14:51 (kit v2) — aucune mesure appareil n'a été
pré-sumée avant ce retour (R5).

## 2. Mesures grammaire — pile chaude voie (i) (copier depuis la sortie)

| Mesure | Valeur appareil | (limite basse bac à sable — JDK 17) |
|---|---|---|
| Démarrage à froid (1er parse, 500 l.) | **1 692,8 ms** | 273,8 ms |
| Parse p50 / p95 (500 l.) | **58,6 / 149,7 ms** | 6,1 / 32,1 ms |
| Parse p50 / p95 (3 000 l.) | **247,6 / 371,5 ms** | 25,0 / 58,1 ms |
| Productions / jetons (3 000 l.) — déterminisme | **31 368 / 24 274** | 31 368 / 24 274 (IDENTIQUE) |
| Tolérance : troncatures / levers | **101 / 0** | 101 / 0 (IDENTIQUE) |
| Tas au repos | **3 800 Kio** | 3 781 Kio |

Notes brutes (passe unique du 30/10 02:14:51 — sortie brute) : corpus 500 :
productions **5 448** / jetons **4 210**, passes 50, marqueurs d'erreur **0**
(compteurs identiques au relevé brut étage 0 — `resultats/mesures-brutes.txt`) ;
corpus 3 000 : passes 20, marqueurs d'erreur **0** ; parse_froid 3 000 l. =
**252,1 ms** (JVM déjà chauffée par les 50 passes du corpus 500 — le « froid »
du second corpus n'est pas un démarrage à froid JVM). Thermique et
min/médiane/max sur passes répétées : non observés (passe unique — 3 passes
minimum recommandées, miroir §5.4 de la synthèse ; passes ultérieures
bienvenues pour le suivi longitudinal, le déterminisme étant déjà attesté
par les compteurs).

## 3. Scan ART (échantillon jar moteur, si python3 présent sur l'appareil)

| Mesure | Valeur appareil | (bac à sable) |
|---|---|---|
| Classes / méthodes déclarées (jar moteur) | **122 / 205** | 122 / 205 (IDENTIQUE) |
| Références sensibles ART | **AUCUNE** | AUCUNE (IDENTIQUE) |

Compléments de la même section de sortie brute : versions de class-file
**61:122** (bytecode 17 consommé par l'openjdk 17.0.19 de l'appareil —
exécution complète en preuve) ; ServiceLoader **OK** — 1 référence,
descripteur vérifié `META-INF/services/jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin`,
1 fournisseur. Le motif `List.<SequencedCollection>` (correctif §14) est
inclus dans ce scan : AUCUNE référence sensible, garde silencieuse —
cohérent avec le jar kit v2 (le v1 aurait levé au premier parse).

## 4. Compléments demandés (si possible sur l'appareil)

- Facteur appareil, rapport p50 appareil / p50 bac à sable **reporté tel
  quel** : 58,6 / 6,1 ≈ **×9,6** (500 l.) et 247,6 / 25,0 ≈ **×9,9**
  (3 000 l.) contre les limites basses JDK 17 ci-dessus ; contre la replay
  kit v2 de la machine de build (§14 : p50 8,6 / 28,8 ms — même script,
  même kit, même JVM majeure) : ≈ **×6,8 / ×8,6**. NB : la passe exécute
  l'**openjdk 17 (HotSpot) de Termux** — ce rapport mesure l'écart de CPU
  entre l'appareil d'entrée de gamme et la machine de build À runtime
  identique ; le facteur **ART 3–5× (E-K4) reste à mesurer sous
  dalvikvm/ART** aux étapes ultérieures, la présente passe ne l'éprouve pas.
- Dexage réel : déjà couvert par la CI (`kotlin-art`, build-tools 34) —
  **16 306 références de méthodes / 2 821 016 o (2,69 Mo)** [mesurés par la
  CI du commit correctif JDK 17 — run 41011935692 : −1 méthode / −40 o vs
  vague 1c, l'échange removeLast → removeAt] ; l'appareil n'a PAS à le
  rejouer.
- GC : rien d'observable dans la sortie brute (aucun journal GC activé, la
  passe s'est déroulée sans incident sur les 50+20 passes ; JVM bornée 2g /
  SerialGC — mêmes conditions que le banc de bac à sable) — note libre à
  compléter aux étapes suivantes (ADR 0117 : les soft-références et
  ReferenceQueue se testeront à l'étage 1 avec la session).

## 5. Décision que ce gabarit alimente

- **Ouverture de l'étage 1** (noyau parse/session/classpath) : la synthèse
  §4 fixe « gabarit appareil rempli (porte d'entrée) » comme critère de
  sortie de l'étage 1 — **la présente passe EST ce gabarit, et il est
  REMPLI** (30/10 02:14:51, kit v2 — critère ATTESTÉ, journal §15). Les
  critères restants de l'étage 1 : fiches index (vague 1d — classpath +
  index appelables, reprises R4/R5) et synthétiques (audit P-K1).
- Les latences cibles §4 du prompt (complétion < 100 ms, premier
  diagnostic < 250 ms, APK ≤ 4 Mo) se mesureront aux étages 2/3 avec le
  moteur réel — l'étage 0 ne mesure que la couche grammaticale.
