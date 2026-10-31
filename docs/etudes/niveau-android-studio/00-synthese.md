# 00 — Synthèse et décision (mission « niveau Android Studio »)

> Mission : amener le LSP Java de `code-lsp` au niveau d'intelligence d'Android
> Studio — audit chiffré (Phase 1), étude de la référence (Phase 2), matrice
> d'écarts (Phase 3), ADR (Phase 4), plan par vagues (Phase 5), puis **STOP**
> (§10 du prompt) : aucune vague d'implémentation au-delà de la vague 0 (outillage
> de mesure) n'est démarrée. Documents de base : `01-audit-moteur.md`,
> `02-reference-android-studio.md`, `03-matrice-ecarts.md`, ADR 0019–0031,
> `04-plan-vagues.md`, `ALERTES.md`.
>
> **Convention R5** : chaque affirmation renvoie à sa preuve ; rien n'est simulé ;
> les mesures appareil attendent les gabarits 06/07 remplis par le propriétaire.

## 1. Recommandation unique (5 lignes)

**GO — exécuter le plan par vagues V-A→V-G sur le socle actuel (ecj + CIDX),
sans rouvrir ADR 0001/0004** : l'audit établit que le moteur est **solide niveau 3**
(sémantique compilateur) sur tout son périmètre, et que le déficit « niveau Android
Studio » est concentré en trois couches concentriques — (1) **connaissance du projet
Android** (niveau 0 : ressources, classes générées, Kotlin, modèle Gradle — H1/H2/H3,
cause racine des « il ne connaît pas bien le code »), (2) **couche IDE au-dessus du
compilateur** (inspections, smart completion, actions riches — H5/H7/H10), (3)
**cohérence et régularité** (inter-fichiers, verrous, double-tampon — H4/H13/H14).
Aucune de ces couches n'exige de changer de moteur : toutes se greffent sur ecj/CIDX
exactement comme le prévoyait le GO PARTIEL — et **un écart (M4/references) est
purement protocolaire et quasi gratuit à lever** (H8). La stratégie « couvrir les
20 % qui font 80 % de la valeur puis élargir par vagues » est le cadre retenu.

## 2. Tableau d'options chiffré

| Option | Intelligence visée | Empreinte/risque ART | Effort [estimé] | Verdict |
|---|---|---|---|---|
| **A — Statu quo amélioré** (correctifs C1–C3 seulement) | niveau 3 actuel, « urgences » traitées | nul | ~5 j-h | **Rejetée** : laisse intacts les trois déficits (G-001…) — la demande du propriétaire reste insatisfaite |
| **B — GO plan par vagues sur ecj+CIDX** (recommandée) | niveau 4 sur projet Android : classes light + inspections + lint Java + smart completion + actions riches ; dépassements §0.1 (1–3, 5) | +~2 Mo (ressources/base API compacte, budget §11) ; aucune dépendance nouvelle en v1 (java-syntax resté conditionné ADR 0017) | **145–241 j-h agent** (16–29 séances ; méthode PLAN-REECRITURE, recoupement volumétrie 90–250 j-h) | **GO** |
| **C — Rouvrir l'architecture** (PSI/embarquer, ou remplacer ecj) | — | disqualifié par les mesures de l'étude PSI (37,4 Mo / 108 motifs ART) | — | **Rejetée** — aucun chiffre de l'audit ne la justifie (aucun blocage constaté) |
| **D — Ne faire que la vague 0** (mesurer, ne pas corriger) | — | nul | livrée | Sous-ensemble de B : la mesure sans la correction ne ferme aucun écart |

Chiffres clés de l'audit qui fondent l'arbitrage : 22 649 l. Kotlin / 625 tests /
82 `synchronized` (46 dans `MoteurJava.kt`) / 21 IDs ecj → 7 familles / 11 genres de
complétion (smart/chain/statement/2e appel absents) / `ModuleLsp` sans aucun champ
Android / banc qualité saturé à 1,000 sur 18 positions. Preuves : `01-audit-moteur.md`
§1–§3.

## 3. Top 10 des écarts P0/P1 (preuves dans l'audit)

| # | Écart | Priorité | Preuve |
|---|---|---|---|
| 1 | G-034 — aucune vérité terrain « niveau AS » (bancs = parité ancien moteur) | P0 | audit §1.11 (completion.json 18 positions 1,000/1,000) |
| 2 | G-001 — classes générées invisibles avant build (`R`, binding) → faux « cannot be resolved » | P0 | audit §1.1 (`DeclassementDatabinding.kt:36`, rg sans synthèse) |
| 3 | G-002 — équivalence rapide/complet non prouvée sur réel (report périmé) | P0 | audit §1.6 (`DiagnoseRapide.kt:34`, portage non re-validé) |
| 4 | G-003 — pas d'invalidation inter-fichiers (B ouvert périmé après édition de A) | P0 | audit §1.4 (`ServeurLangageJava.kt:304–323` : republication au seul remplacement de contexte) |
| 5 | G-004 — insertions de complétion non mesurées (résolvabilité) | P0 | audit §1.9 (échelle textuelle, M6) |
| 6 | G-005 — divergences ecj↔javac jamais mesurées | P0 | audit §4 (corpus compile avec javac) |
| 7 | G-006/G-008 — ressources XML et modèle Gradle inconnus | P1 | audit §1.1/§1.3 (filtres `.java`, champs `ModuleLsp`) |
| 8 | G-009 — déclarations Kotlin invisibles avant build | P1 | audit §1.2 (`AnnuaireClasspath.kt:385`) |
| 9 | G-010/G-011 — aucune inspection ni lint Android (NewApi…) | P1 | audit §1.5 (10 options ecj seulement) |
| 10 | G-013 — `references` confiné au document courant **par un simple filtre protocolaire** | P1 | audit §1.8 (moteur déjà exhaustif — levée ≈ 0,5 j-h) |

## 4. État des hypothèses H1–H15

**Toutes confirmées**, avec trois nuances factuelles (audit §1) : H5 précisée (10
options ecj, pas ~7) ; H7 précisée (21 IDs, pas 20) ; **H8 fortement nuancée** — le
moteur est déjà multi-fichiers exhaustif, le confinement est protocolaire (parité
stricte §10.4) ; H6 confirmée avec garde existante (le risque est le report périmé,
mesurable par P1/P8). Aucune hypothèse infirmée. Les mesures dynamiques restant
`[NON VÉRIFIÉ]` (appareil, captures AS, exécution CI du banc) ont leur protocole :
gabarits 06/07 + run CI du push.

## 5. Les 10 risques principaux et leurs mitigations

| # | Risque | Gravité | Mitigation (renvoi) |
|---|---|---|---|
| 1 | **Régression du rouge ecj** pendant V-A/V-B (pipeline unifié, classes light) | Haute | le rouge n'a qu'une source (ADR 0013/0023) ; banc fidélité + P3 corpus en gate CI (ADR 0030) |
| 2 | **Règles AGP divergentes** (nommage binding, R transitif, flags) | Haute | cas dorés extraits des 5 projets réels ; priorité systématique à la source générée réelle (ADR 0021 R1) |
| 3 | **Faux positifs des inspections/lint** (nullité, NewApi) | Haute | option « unsound » off par défaut (discipline de la référence) ; sévérité Warning ; P3 garde-fou (ADR 0023/0024) |
| 4 | **Latence dégradée par la couche IDE** (§11 : pas un gain si la frappe double) | Haute | V-F1 (double-tampon) AVANT V-C ; régimes instantané→différé ; budgets p95/p99 (ADR 0029) |
| 5 | **Sous-estimation de l'effort des vagues** (fourchettes 145–241 j-h agent) | Moyenne | vagues courtes livrables ; recalages fin V-A et fin V-B (méthode RECALAGE) |
| 6 | **Interblocage après verrous par périmètre** | Moyenne | ordre global fichier→module→index + chaos tests CI (ADR 0029, hérité de 0010) |
| 7 | **Empreinte ART** (référentiel XML, base api-versions ~5,3 Mo) | Moyenne | budgets octets CIDX (segment compact ~1,7 Mo cible) ; mesure appareil gabarit 07 |
| 8 | **Dérive du banc** (sur-apprentissage, bruit) | Moyenne | corpus réel + split train/éval + enrichissement par PR (ADR 0030, hérité de 0016) |
| 9 | **Dépendances contractuelles hôte/client** (modèle v2, kinds, progression) | Moyenne | contrats versionnés ; listes séparées prêtes en prompts (`04-plan-vagues.md` §4) |
| 10 | **Corpus indisponible/dormant en CI** (réseau, projets amont) | Basse | gates `assumeTrue` avec raison affichée ; secours dmfs/opentasks épinglé (LISEZMOI corpus) |

## 6. Plan résumé (détail dans `04-plan-vagues.md`)

**V-0 mesure (LIVRÉE)** : banc P1–P8 exécutable en CI + corpus 5 projets épinglés +
protocoles de captures AS (06) et de mesure appareil (07). **Aucune décision ne la
conditionne — elle peut s'exécuter dès maintenant.**

**V-A fiabilité** (18–30 j-h) → **V-E navigation** (1–3 j-h, décision D1) →
**V-B projet Android** (36–60 j-h, en 3 sous-vagues) → **V-F1 double-tampon**
(8–14 j-h, AVANT V-C) → **V-C intelligence** (28–44 j-h) → **V-D actions**
(28–46 j-h) → **V-F perf complète** (16–26 j-h) → **V-G protocole** (10–18 j-h).
**Total : 145–241 j-h agent, 16–29 séances effectives.**

## 7. Ce qui n'a pas pu être vérifié (et protocole)

1. **Mesures appareil** (latences §0.1, mémoire, thermique) → **passe 1
   consignée le 31/10** (moto g06, moteur in-process, plateforme jrt :
   S2/S3, F1/F3-F6, C1/C2, Q3, M1/M2 — verdict §8 : cibles §0.1
   **partiellement atteintes**, F1 ≈ 4× le budget, cf. ALERTES AL9) ; restent
   le perçu écran 60 i/s, la passe android.jar, S1/M3/M4 (hôte) — gabarit
   `07-gabarit-mesure-appareil.md` + transcript `mesures/`.
2. **Captures Android Studio** (comportements NV de la référence) → protocole
   `06-protocole-captures-android-studio.md` (lots A–D).
3. **Exécution du banc sur corpus réel** → le banc P1–P8 a été **joué en local**
   (23 tests / 0 échec / 1 gate ignorée ; 26 violations mesurées — audit §1.12) ;
   la **gate corpus réel** (P3/P4/P7 sur les 5 projets) exige la préparation des
   manifestes de classpath (`bancs/corpus-android/LISEZMOI.md`) puis
   `CORPUS_ANDROID=… :java:test` — à jouer en CI.
4. **Profil d'inspections par défaut d'Android Studio** (non documenté
   publiquement) → capture D5 du protocole 06.

## 8. Décisions demandées au propriétaire (numérotées, avec recommandation)

> **APPROUVÉES le 31/10/2026 — GO du propriétaire** (« Je suis d'accord
> avec tes recommandations pour les décisions D1–D9. C'est un GO, tu peux
> commencer les vagues ») : les neuf recommandations ci-dessous sont
> entérinées telles quelles ; la **passe 1 du gabarit 07** (consignée le
> 31/10) arbitre D5 — cibles §0.1 maintenues, la passe jrt devient la
> baseline vague 0. L'exécution des vagues suit (V-A1 livrée le 31/10,
> **V-E livrée le 31/10 au soir** — D1 exécutée : references
> multi-fichiers, surbrillances composées typées, RPC usagesGroupes,
> cf. `04-plan-vagues.md` §V-E ; **V-B livrée le 31/10 au soir (v1.4.0)** —
> D2/D3/D6 exécutées : modèle v11 + dérivateur disque, référentiel de
> ressources, classes light R/BuildConfig/Binding/BR, navigation
> ressource exacte, interop Kotlin conservatrice, retrait
> `DeclassementDatabinding`, cf. `04-plan-vagues.md` §V-B).

| # | Décision | Recommandation |
|---|---|---|
| **D1** | **Abandonner la parité stricte sur `references`** (M4/fiche 054/§10.4) : le moteur est déjà multi-fichiers ; la levée = retirer le filtre + inverser la gate « taille = 1 » | **OUI** — gain immédiat maximal pour ~0,5 j-h (ADR 0028, fiche C-10) |
| **D2** | **Étendre le contrat `:model`** (champs B10) **et le producteur côté hôte**, avec dérivation disque par défaut dans le moteur | **OUI** — l'axe « intelligent avant la première sync » (§0.1-2) l'exige (ADR 0020) |
| **D3** | **Périmètre Kotlin→Java** : indexer les déclarations publiques `.kt` (scanner lexical, index partagé) sans toucher au moteur Kotlin | **OUI, borné ainsi** — instruction du 30/10 respectée (ADR 0022) |
| **D4** | **Conditions d'usage des artefacts JetBrains** (java-syntax, ADR 0017) : confirmer avant la vague incrémentalité | **confirmer quand possible** — ne conditionne QUE l'ADR 0019 option 2/G-031, aucune vague P0/P1 |
| **D5** | **Seuils des métriques « niveau AS »** (FP/1 000 < 0,5 ; top-1 ≥ 0,80 ; P6 ≥ 99 %…) et **cibles de vitesse §0.1** (< 100 ms p95 perçu ; < 300 ms diagnostics ; démarrage chaud < 3 s) | **valider ou corriger après la première mesure** (V-0 CI + gabarit 07) — **passe 1 reçue** (gabarit 07 §8 : partiellement atteintes ; F1 ≈ 4× le budget — re-passe android.jar conseillée avant figement, AL9) — ADR 0030/0029 |
| **D6** | **Retirer `DeclassementDatabinding`** au profit de la résolution réelle du binding (amende la fiche 051) | **OUI à V-B3** (ADR 0021) |
| **D7** | **Amender la fiche 007** (mots-clés `extends`/`implements` en tête de déclaration avec les stubs — M1) | **OUI à V-C** (ADR 0026) |
| **D8** | **Enveloppe/ordre des vagues** (145–241 j-h agent, séquence V-A→V-G avec V-F1 remontée) | **approuver l'enveloppe et la séquence**, recalages fin V-A/fin V-B |
| **D9** | **Axes de dépassement §0.1 (points 1–6)** : les retenir comme objectifs affichés (pas de dumb mode bloquant, indépendance Gradle, budgets Mo/démarrage, fidélité ecj mesurée, régularité p99, cohérence Java↔XML) | **OUI** — écrits dans la référence §C et les ADR ; « où l'on ne prétend pas surpasser » consigné noir sur blanc |

## 9. État de la CI et des commits

- Branche `etude/moteur-java`, commits poussés après `scripts/verifier-push.sh`
  concluant (identité jjoblab vérifiée) — SHA et état du run CI consignés dans le
  compte-rendu final de la mission (le push inclut : études, ADR 0019–0031, banc
  P1–P8, script corpus, mises à jour AGENTS/ECARTS/PROVENANCE).
- Le banc P1–P8 s'exécute dans le job de tests existant (package
  `jo.codeide.lsp.java.coherence`) ; les gates corpus s'ignorent proprement tant
  que `CORPUS_ANDROID` n'est pas préparé (raison affichée).

---

**STOP (§10)** : la mission s'arrête ici. Les prompts d'exécution **vague par
vague** seront produits séparément après décision du propriétaire — la vague 0
(outillage de mesure) est **livrée et exécutable immédiatement**.
