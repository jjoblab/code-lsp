# ADR 0103 — Stratégie sémantique : modèle maison / Analysis API K2 / hybride / externe

- **Statut** : **Accepté** — décision du **propriétaire** reçue dans le canal de mission le 2026-10-30 (même autorisation d'exécution que l'ADR 0100 : « Je t'autorise à commencer les étapes, tu dois travailler uniquement sur ta branche ») : **option (i) — parseur officiel + modèle de symboles conservateur**, avec **(iii) en extension différée** (profondeur K2 en tâche explicite, jamais sur une frappe). L'option (ii) reste archivée avec ses preuves de disqualification (S1 [mesuré]).
- **Date** : 2026-10-30 (rédaction initiale — proposée) / 2026-10-30 (acceptation propriétaire — exécution ouverte)

## Contexte

Quatre voies techniques pour le moteur sémantique (prompt §1) :
**(i)** parseur officiel + modèle de symboles maison (choix actuel de
l'ancien dépôt, D1) ; **(ii)** Analysis API / compilateur K2 embarqué ;
**(iii)** hybride : premier plan maison, profondeur K2 à la demande ;
**(iv)** serveur Kotlin externe (processus séparé). Le spike (Phase 2)
a produit les premières mesures armes égales sur ce terrain.

## Sources consultées

- Spike : `02-spike-mesures.md` §3–§5 (S0/S1/S2, scans ART, latences).
- Étude amont : `01-architectures-analyse-kotlin.md` §0 (tailles,
  non-publication de l'Analysis API standalone, dépendance
  IntelliJ de la façade, dumb mode) et conclusion obligatoire.
- `codeide-lsp` : `docs/LSP_KOTLIN.md` D1/D8 (décisions anciennes),
  ADR 0115 (vendoring), ADR 0119 (renonciation languageVersion).
- `Kotlin/kotlin-lsp` @ main (Apache-2.0 vérifié) : README — bâti sur
  IntelliJ IDEA + plugin Kotlin (serveur de bureau, pré-alpha).

## Options

**Option (i) — modèle maison sur parseur officiel.** Preuves : S2
= 0,51 Mo + grammaire, 0 motif ART, parse p50 1,8 ms (500 l.)/8,6 ms
(3 000 l.) [mesuré] ; l'oracle S0 démontre le plan édition complet à
zéro faux positif (quatre mondes). Limite : couverture sémantique
bornée (K4) — la fidélité « compilateur » n'est pas achetable par cette
voie.

**Option (ii) — Analysis API/K2 embarqué.** Disqualifié par la mesure :
pas de vaisseau autonome (Analysis API standalone non publiée
[mesuré]) ; le compilateur embarqué = 53,6 Mo / 25 019 classes /
64 507 méthodes / ~2 170 motifs sensibles ART (dont Unsafe ×2) /
3 503 ms d'amorçage / 6 231 ms d'analyse complète de 500 l.
[mesurés]. Contredit le budget T1 ≤ 4 Mo d'un facteur 13 avant shims ;
contredit « aucun compilateur sur le chemin d'édition ».

**Option (iii) — hybride : plan chaud maison + profondeur K2 différée.**
Le plan chaud = (i) ; la profondeur K2 = le compilateur embarqué en
**tâche explicite** (coût d'amorçage 3,5 s [mesuré], délai annoncé),
jamais sur une frappe. La frontière « jamais K2 sur une frappe » devient
une propriété vérifiable (pas d'artefact compilateur sur le classpath
du plan chaud).

**Option (iv) — serveur externe.** Hérite du coût (ii) + IPC/processus ;
pertinente seulement pour le tooling T2 (compilation), pas pour
l'édition. [non vérifié — aucune mesure IPC menée]

**Option 0 — ne rien changer** : le moteur actuel (qui est déjà (i))
reste dans l'ancien dépôt (voie D de l'ADR 0100).

## Critères et mesures

| Critère §4 | (i) [mesuré S2+S0] | (ii) [mesuré S1] | (iii) | (iv) |
|---|---|---|---|---|
| Empreinte APK | 0,86–2 Mo | 53,6 Mo+ (×13 budget) | (i) + éventuel pack compilateur différé | (i) côté app |
| Latence frappe | parse 1,8 ms p50 ; oracle reparse 3,4 ms | 3,5 s AMORÇAGE | = (i) | = (i) + IPC |
| Fidélité | bornée (preuve ou silence) | complète (FIR) | bornée + profondeur à la demande | complète côté serveur |
| ART | 0 motif | ~2 170 motifs | 0 motif (plan chaud) | dépend du serveur |
| Faux positifs | 0 (quatre mondes, audit) | 0 (compilateur) | 0 + diagnostics K2 en différé | 0 |

## Décision proposée

**(i), avec (iii) comme chemin d'extension différé.** Le moteur neuf
garde l'architecture « grammaire officielle + modèle de symboles
conservateur » (D1 maintenue, cette fois par la mesure et non par
transmission documentaire — R9.4) ; le mode profond K2 (iii) reste une
**extension possible à la demande** (commande utilisateur, délai
annoncé, éventuellement pack téléchargeable dédié — ADR 0112), à ne
construire que sur besoin démontré (matrice de capacités + retours
appareil). La voie (ii) est archivée avec ses preuves (spike §3/§5) —
elle ne se rouvrirait que sur un vaisseau Analysis API autonome,
publié et compact, à re-mesurer au spike avant toute décision.

## Conséquences

- Le cahier (469 comportements) reste le contrat du plan chaud ;
  le banc de fidélité face à K2 (compilateur en oracle de sous-processus,
  ADR 0119) mesure HONNÊTEMENT la couverture sans l'exiger complète.
- Aucune dépendance compile-scope au compilateur Kotlin dans le module
  `:kotlin` (vérifiable en CI — ADR 0120).
- L'effort d'inférence maison reste borné par l'ADR 0107 (périmètre
  assumé), pas par la course à K2.

## Plan de migration progressive

Drapeau « mode profond » (off par défaut) dès la conception du module ;
aucune implémentation avant besoin démontré (retours appareil, matrice).

## Tests de non-régression et critères de sortie

- CI : absence d'artefact compilateur dans le graphe du module (scan
  des dépendances) ; latences §4 tenues sur le plan chaud.
- Si (iii) activé un jour : diagnostics K2 différés étiquetés
  (source=profonde), jamais mélangés aux diagnostics de frappe.

## Risques

- R1 — Pression « pourquoi pas K2 ? » récurrente : mitigation = cette
  ADR chiffre l'écart (3,5 s/53,6 Mo) ; rouverte sur preuves nouvelles.
- R2 — Sous-estimation du modèle maison : mitigation = cahier + banc de
  fidélité (la couverture se MESURE, elle ne se déclare pas).

---

## Amendement 2026-10-31 — mission « niveau Android Studio » (ADR 0122, statut Proposé)

L'option (iii) « hybride en extension différée » se réalise comme **architecture à paliers 1/2/3** (ADR 0122) : le palier 1 (option (i) actuelle) reste le socle et le chemin de frappe ; le palier 2 (ADR 0123) est le typage étendu borné ; le palier 3 (ADR 0124) est le profond K2 à la demande. **Nouveau fait mesuré** (banc V0, audit §4.3) : la frontière de preuve du palier 1 n'est pas la frontière du langage — 980 FP / 34 934 l. = 28,05 FP/1 000 l. sur l'auto-corpus (imports implicites `kotlin.*`, `it` sur extension générique, `this` nu, paramètres de type). La correction de cette baseline PRÉCÈDE toute extension (vague V-A du plan).
