# ALERTES — mission « niveau Android Studio Kotlin »

> Anomalies, découvertes et écarts avec le prompt, signalés factuellement
> (R6.2/R5). Date : 2026-10-31. Ce document continue la numérotation des
> alertes du dossier [`../moteur-kotlin/ALERTES.md`](../moteur-kotlin/ALERTES.md)
> (E-K1…E-K8 y vivent) — les alertes ci-dessous sont **E-K9 et suivantes**,
> propres à la mission « niveau Android Studio ».

## §1 Découvertes majeures (mesurées)

- **E-K10 — La revendication « zéro faux positif » est INFIRMÉE à
  l'échelle** [CONFIRMÉ, banc V0 P1 auto-corpus, 31/10/2026] : 99 fichiers
  / 34 934 lignes de Kotlin compilable (le module lui-même, classpath de
  test complet) → **980 FP = 28,05 FP/1 000 lignes** (oracle K2 : 1 erreur)
  — familles : `it` 603, imports implicites `kotlin.*`/intrinsèques ~100,
  `this` nu 48, segments racines de paquets ~60, paramètres de type `T` 11,
  `kt.valReassign` sur affectation indexée 36, `kt.argumentCount` 38,
  `kt.unsafeNullable` 23. Le banc de fidélité du dépôt (86 lignes) et le
  cahier ne voyaient AUCUNE de ces familles. Conséquence : l'hypothèse
  KH3 du prompt est confirmée par la mesure ; la correction de cette
  baseline est la première vague (V-A) et le critère d'entrée de toute
  extension du catalogue (ADR 0125). Caveat : corpus JVM (le module), pas
  Android — la confirmation Android exige les manifestes (P1(b)).

- **E-K11 — Le banc de complétion est saturé** (confirmé côté Java par
  l'alerte AL1 de la mission jumelle) : 1,000/1,000/1,000 sur 16
  positions — il ne peut plus distingigner amélioration et régression.
  L'étiquetage de 50+ positions réelles avec split train/éval est le
  premier chantier qualité de V-A' (ADR 0135).

## §2 Écarts de procédure tracés (validation propriétaire demandée)

- **E-K9 — Re-signature de la baseline de parité structurale (fiche 010)** :
  l'ajout des fichiers du banc V0 (10 fichiers `.kt` sous
  `kotlin/src/test/…/coherence/`) déplace le digeste de parité qui couvre
  TOUT `.kt` du dépôt (422 → 432 lignes). La re-signature a été exécutée
  par la COMMANDE OFFICIELLE documentée dans la fiche 010
  (`-Dparite.majBase=true --rerun-tasks`) et le diff ne contient QUE des
  ajouts. **Ce geste contredit la lettre du §1.5 du prompt (« ne re-signe
  aucune baseline dans cette mission d'étude ») mais les livrables §9
  exigent le banc DANS `kotlin/src/test`** — écart tracé ici, chaque
  justification dans le commit dédié, validation propriétaire demandée.
  Aucune baseline de QUALITÉ (fidélité, complétion) n'a été touchée.

- **E-K12 — Élargissement du corpus d'exclusion de la fiche 010** : le
  set `EXCLUSIONS` gagne `"corpus-android-kotlin"` (2 lignes + KDoc) —
  le corpus téléchargé de vrais projets `.kt` ne fait pas partie du
  dépôt et ne doit pas entrer dans le digeste de parité. Modification
  d'un test existant, minimale et motivée — tracée ici pour revue.

- **E-K13 — Le kit de captures ne peut pas être un fichier `.kt`** : le
  kit du lot E (volontairement non compilable, positions `¦` en cours de
  frappe) vivrait dans le dépôt et casserait la parité « tout `.kt` du
  dépôt se parse sans marqueur ». Il est livré en
  `captures/kit/KitCapturesKotlin.kt.txt` — la convention Java (`.java`
  sans gate équivalente) n'était pas transposable.

## §3 Coordination avec la mission Java / contraintes externes

- **C-NK1 — V-E Kotlin dépend du calendrier Java** : la consommation de
  `:model` v2 (champs Android + référentiel de ressources, décision D2)
  suppose la livraison de la V-B Java — le plan place la V-E Kotlin après
  la V-B Kotlin pour absorber ce décalage, tout glissement Java se répercute.
- **C-NK2 — La bascule I0–I6 Kotlin s'appuie sur l'arbitrage I1 Java**
  (adaptation `:lsp:host`, fourchette 3–6 j-h, arbitrage A/B en attente
  côté Java) : un blocage Java bloque mécaniquement GK-027.
- **C-NK3 — Corpus : Thunderbird-android (MPL-2.0) écarté par défaut** —
  candidat secours du rôle « mixte » SI le propriétaire arbitre MPL-2.0
  acceptable pour des données de corpus jamais committées.
- **C-NK4 — Le facteur ART ≈ 4× est un chiffre JAVA** (passe 1 moto g06,
  alerte AL9 Java : complétion membres p95 404,87 ms ≈ 4× le budget) :
  il sert à CALIBRER les cibles, jamais à affirmer une latence Kotlin
  [NON VÉRIFIÉ côté Kotlin — passe gabarit 07].

## §4 Points restés « à vérifier » (protocoles prêts)

- Captures Android Studio lot E (protocole `06-…`, gabarits
  `captures/RESULTATS-LOT-E.md`) — propriétaire.
- Passe appareil gabarit 07 (kit existant `scripts/kit-bascule.sh`,
  enrichissement kotlin-metadata-jvm documenté doc 11 §2.2) — propriétaire.
- Manifestes du corpus externe (P1(b)/P2/P3/P7) — CI ou poste avec SDK
  Android (LISEZMOI du corpus §2).
- État du mode K2 dans la version courante : **K2 par défaut depuis
  Android Studio Narwhal Feature Drop 2025.1.2 (juillet 2025)**, version
  courante Rabbit 2 (2026.2.2) [CONFIRMÉ, developer.android.com,
  consulté le 31/10/2026] — les captures lot E doivent néanmoins
  consigner la version exacte utilisée et confirmer le mode K2 actif.
