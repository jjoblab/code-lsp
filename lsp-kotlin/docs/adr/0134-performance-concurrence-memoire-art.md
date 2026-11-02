# ADR 0134 — Performance, concurrence et mémoire sur ART

- **Statut** : Proposé
- **Date** : 2026-10-31
- **Amende** : ADR 0117 (réalisation complète), ADR 0119 (cibles de latence)
- **Écarts** : GK-031, GK-032, GK-033

## Contexte

L'ADR 0117 a posé le schéma (verrous par périmètre + ordre global,
annulation par jeton, budgets octets, observateurs neutres, SANS
coroutines). Réalisé : verrous (session RW, scan, calcul, vue JVM),
annulation coopérative par génération, bornes EN UNITÉS (256 segments,
2 000 fichiers, 300 entrées), époque copy-on-write. **Non réalisé** :
caches bornés en **octets**, `onTrimMemory` (absent — `rg` néant, audit
§3.11), GC ART (soft-références/ReferenceQueue), budgets par palier
d'appareil, dumb mode garanti. Aucune mesure appareil n'existe (E-K2) ; le
facteur ART observé ≈ 4× côté Java (passe 1 moto g06 : complétion membres
p95 404,87 ms ≈ 4× le budget de 100 ms — alerte AL9 Java) remplace
l'hypothèse « 3–5× ».

## Sources consultées

- Audit §3.11 ; fiche 021 (repères JVM : reparse 1 803 l. 29 ms chaud,
  diagnostics 20–26 ms chauds).
- Mission Java : gabarit 07 passe 1 (S2 démarrage 7,6 s ; F1 complétion
  p95 404,87 ms ; F6 diagnostics 1 356 l. 1 154 ms ; C1 contention ×1,8 ;
  M1/M2 tas 15,4 Mo), ADR 0029 (5 phases), alerte AL9.
- Cibles §0.1 du prompt (à valider ou corriger par la mesure).

## Options

| Option | Description | Critères |
|---|---|---|
| **1 — Réaliser 0117 en entier + mesures d'abord (recommandée)** | caches bornés en octets (budget par cache, vidange au plafond), `onTrimMemory` branché par l'hôte (SPI d'observation neutre), GC ART instruit au gabarit 07 (soft-références vs époque), budgets par palier d'appareil, dumb mode garanti (le guichet sert le disponible, jamais bloquant) | achève une décision déjà prise ; les mesures Java (AL9 : 4× le budget) montrent que SANS mesure on ne délivre pas les cibles §0.1 |
| **2 — Coroutines pour la concurrence** | réécriture async | rejeté par 0117 (choix assumé : pas de coroutines dans le moteur) |
| **3 — Verrou global (statu quo Java d'origine)** | un verrou, tout séquentiel | la contention C1 Java mesure ×1,8–2,8 — le moteur Kotlin a déjà les verrous par périmètre, l'écart est derrière nous |

## Critères et mesures

- Cibles §0.1 reprises comme **cibles à valider par la passe gabarit 07**
  (complétion/hover/signature < 100 ms p95 perçu ; diagnostics ~300 ms ;
  p99 régulier ; aucune fonctionnalité indisponible pendant l'indexation —
  le S3 Java a montré l'inverse côté Java : à garantir côté Kotlin) ;
- budgets octets : chaque cache déclare un plafond en octets estimés
  (pas seulement en nombre d'entrées) — le coût par entrée est mesuré une
  fois puis borné ;
- `onTrimMemory` : SPI `ObservateurMemoire` neutre que l'hôte appelle —
  le moteur vide ses caches bornés et relâche le montage d'occurrences.

## Décision proposée

**Option 1**, séquencée par le plan : V-A (budgets octets + observateur
mémoire, conditions de la bascule), V-F (GC ART + budgets par palier
d'appareil + dumb mode, après la passe appareil réelle).

## Conséquences

- Aucun chiffre ART affirmé avant la passe gabarit 07 (R5) ;
- les cibles §0.1 restent des CIBLES, pas des constats.

## Plan de migration progressive

V-A (budgets/octets, onTrimMemory par SPI) → passe appareil (gabarit 07) →
V-F (le reste, éclairé par les chiffres).

## Tests de non-régression et critères de sortie

- P4 (équivalence des chemins) et P9 (résistance) verts ; p95 JVM ≤
  baseline + 10 % à chaque vague ; passe appareil consignée au gabarit 07.

## Risques

- R1 — Les cibles §0.1 s'avèrent inatteignables sur ART (miroir AL9 Java) :
  re-passe avec android.jar puis recalibrage PROPRIÉTAIRE (D5 Java : « ne
  pas recalibrer, consigner la baseline » — même discipline) ;
- R2 — Fuite par le montage d'occurrences : caches par requête déjà jetés ;
  l'observateur mémoire est la ceinture.
