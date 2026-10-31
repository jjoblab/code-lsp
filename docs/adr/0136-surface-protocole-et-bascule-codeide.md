# ADR 0136 — Surface de protocole, capacités absentes et bascule CodeIDE avant extension

- **Statut** : Proposé
- **Date** : 2026-10-31

## Contexte

L'audit (§1.15/§1.16, KH15/KH16) dresse le constat : 19 capacités
annoncées par le serveur LSP4J (lsp4j en runtime-only, porté par
l'hôte), 7 absentes (semanticTokens, codeLens, callHierarchy,
typeHierarchy, documentColor, onTypeFormatting, linkedEditingRange),
`x-codeide.completionRanking` non annoncée ; surtout, le moteur est
livré mais **PAS EN SERVICE** — aucune mesure appareil n'existe. La
mission jumelle Java a figé la doctrine (ADR 0031 surface protocole ;
bascule CodeIDE I0–I6, arrêtée à I1 pour 3–6 j-h ; cibles D5 :
complétion < 100 ms p95 perçu, diagnostics < 300 ms, démarrage chaud
< 3 s, facteur ART ≈ 4× observé côté Java). Écarts visés : GK-025
(semanticTokens), GK-026 (hiérarchies/codeLens), GK-027 (bascule
KH16), GK-028 (publication du pack). Cette ADR **amende l'ADR 0118**
(séquencement : la bascule passe AVANT toute extension de capacité).

## Sources consultées

- Référence : `02-reference-android-studio.md` §A6 (coloration
  sémantique, inlay hints), §A7 (index/dumb mode), §C (non-
  dépassements assumés).
- Audit : `01-audit-moteur.md` §3.12, §4.4 (aucune mesure appareil),
  §1.15/§1.16 ; gabarit `07-gabarit-mesure-appareil.md` (Q-K4
  publication, passes 1/2/3 + médiane).
- Dépôt : ADR 0118 (amendée), 0115, 0119, 0123, 0125/0126/0131 ;
  mission jumelle Java : ADR 0028 (`x-codeide.usages` v1), 0029
  (facteur ART), 0030 (seuils), 0031.

## Options

**Option 1 — Bascule d'abord, capacités ensuite.** Achever I0–I6 (le
miroir Java est figé à I1, 3–6 j-h) et publier le pack AVANT d'étendre
les 7 capacités absentes. Mesuré : une capacité non servie en
production est INVISIBLE et NON MESURABLE (KH16 : aucune mesure
appareil, donc aucun chiffre p95 possible au gabarit 07) ; le facteur
ART ≈ 4× [observé côté Java] interdit d'extrapoler les latences JVM
(parse 1,8 ms p50/500 l., re-parse 8,6 ms p50/3 000 l. [mesurés JVM])
sans appareil en service ; la bascule coûte 3–6 j-h [estimé miroir
Java] contre des étages multi-vagues pour les capacités.

**Option 2 — Capacités d'abord, bascule ensuite.** Contre-mesuré :
travail invisible (personne ne voit semanticTokens sans la bascule) ;
risque de divergence des capabilities entre packs ancien/neuf (R1 de
l'ADR 0118) ; chaque capacité devrait être validée au gabarit 07 —
impossible sans service réel ; KH16 se prolonge.

**Option 3 — Parallèle.** Contre-mesuré : contention d'équipe (3–6 j-h
de bascule contre des étages entiers) et risque que la bascule glisse
— précisément le péché originel de KH16 (livré mais pas en service) ;
aucune mesure d'accueil avant la fin des deux chantiers.

## Critères et mesures

| Capacité (absente) | Fondement | Bornes fiables | Mesure |
|---|---|---|---|
| semanticTokens (GK-025) | palier 2 (ADR 0123) | types DÉCLARÉS, fonctions/paramètres, récepteurs — **PAS la variance/nullabilité inférées tant que non prouvées** (KH1 : `TypeValeur = Projet \| Jvm(fqcn)`, pas de génériques) | tableaux plein document + latence (V0/gabarit 07) |
| codeLens (GK-026) | occurrences par re-parse paresseux (8,6 ms p50/3 000 l. [mesuré]) | résolution paresseuse, périmètre borné | coût multi-fichiers à mesurer |
| callHierarchy/typeHierarchy (GK-026) | types Projet/Jvm sans variance | affichage borné honnête (pas de génériques) | à mesurer (V0) |
| documentColor | lexical (couleurs Android en Kotlin) | littéraux couleur hexadécimaux | candidat précoce, à mesurer |
| onTypeFormatting | formateur borné ADR 0115 | ré-indentation seule | miroir des tests 0115 |
| linkedEditingRange | arbre | paires `()`/`{}`/`<>` | candidat précoce, à mesurer |
| diagnostics enrichis | ADR 0125 | `relatedInformation` (import/déclaration en cause), provenance palier 1/2/3 en donnée custom | à mesurer (V0) |

Séquencement (comparaison) :

| Critère | Opt. 1 bascule d'abord | Opt. 2 capacités d'abord | Opt. 3 parallèle |
|---|---|---|---|
| Visibilité (KH16) | immédiate | nulle jusqu'à la bascule | différée |
| Mesures appareil (D5) | possibles dès I6 | impossibles | tardives |
| Coût d'entrée [estimé] | 3–6 j-h [miroir Java I1] | étages multi-vagues | les deux en contention |
| Divergence capabilities | table de parité verrouillée (ADR 0118) | maximale | élevée |

## Décision proposée

**Option 1.** Étape A — bascule : achever I0–I6, publier le pack (Q-K4 :
descripteur id/version/min-app, data import auto, association des
extensions `.kt`/`.kts`, capabilities `x-codeide.*` versionnées) et
publier la liste de consommation : **l'HÔTE consomme** le descripteur
de pack, le drapeau de routage ancien/nouveau (ADR 0118), le runtime
lsp4j (inchangé), l'import auto des data (classpath/corpus) ; **le
CLIENT consomme** les capabilities existantes puis les nouvelles au fil
des vagues, `x-codeide.completionRanking` v1 (ADR 0126),
`x-codeide.usages` v1 (miroir ADR 0028 Java), `codeAction/resolve` et
WorkspaceEdit multi-fichiers (ADR 0131). Étape B — capacités par
vagues : d'abord les décidables sur l'arbre (linkedEditingRange,
documentColor, onTypeFormatting miroir 0115), puis palier 2
(semanticTokens borné, hiérarchies, codeLens paresseux), diagnostics
enrichis avec l'ADR 0125. Chaque vague : mesure gabarit 07 contre les
cibles D5, matrice de capacités honnête (annoncée/servie/non annoncée,
verrouillée par test W4-like).

## Conséquences

- GK-027/KH16 se ferment d'abord : le moteur entre en service, les
  chiffres D5 existent, toute capacité devient visible ET mesurable.
- semanticTokens ne survend jamais la variance (borne écrite, miroir
  des non-dépassements §C de la référence).
- La bascule reste réversible (drapeau, packs coexistants, ADR 0118).

## Plan de migration progressive

1. Bascule I0–I6 (3–6 j-h estimés) + publication du pack (Q-K4).
2. Vague capacités arbre : linkedEditingRange, documentColor,
   onTypeFormatting.
3. Vague palier 2 : semanticTokens borné, hiérarchies, codeLens
   paresseux, diagnostics enrichis (provenance 1/2/3).

## Tests de non-régression et critères de sortie

- Matrice de capacités reproduite puis étendue (les 7 absentes passent
  « annoncée+servie » une à une, jamais annoncée sans être servie) ;
  codes d'erreur exacts ; zéro réponse périmée.
- Cibles D5 mesurées sur appareil (gabarit 07) : complétion < 100 ms
  p95 perçu, diagnostics < 300 ms, démarrage chaud < 3 s.
- Retour arrière testé (drapeau inversé = ancien moteur servi).

## Risques

- R1 — Le chaînon hôte tarde (miroir Java §3.2.2) : mitigation =
  drapeau et packs coexistants (l'attente n'est pas bloquante).
- R2 — semanticTokens survend la variance : mitigation = catégories
  bornées aux preuves (KH1), pas de variance tant que non prouvée,
  test dédié.
- R3 — Charge codeLens multi-fichiers : mitigation = résolution
  paresseuse + périmètre borné, mesure gabarit 07 avant annonce.
