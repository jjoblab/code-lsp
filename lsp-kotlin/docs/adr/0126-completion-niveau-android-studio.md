# ADR 0126 — Complétion « niveau Android Studio » : positions, frontière RPC de classement et pesée

- **Statut** : Proposé
- **Date** : 2026-10-31

## Contexte

L'audit (§1.6, KH6) décrit la complétion actuelle : 6 genres servis
(MEMBRE, NOM, IMPORT, ARGUMENTS_NOMMES, CONSTANTES_ENUM,
ATTRIBUTS_ANNOTATION ; INCONNU = vide), plafonds 120/512, chaîne de
classement Java (ADR 0012/0113) non branchée, aucune complétion smart
ni chain. La référence (`02-reference-android-studio.md` §A2) sert des
positions riches : smart par type attendu, chain, récepteurs implicites/
DSL, branches `when` manquantes, override/stubs, KDoc, mots-clés
contextuels, postfix. Dépendance structurante : type attendu et
récepteur attendu exigent le palier 2 (ADR 0123) — sans lui, seules les
positions décidables sur l'arbre/disque se livrent. Écarts visés :
GK-012 (smart/chain), GK-013 (récepteurs DSL), GK-014 (when/override),
GK-015 (frontière RPC `x-codeide.completionRanking` non annoncée).
Cette ADR **amende l'ADR 0113** (positions au-delà de la parité v1).

## Sources consultées

- Référence : `02-reference-android-studio.md` §A2 (complétion K2 :
  pesée par type attendu, classement statistique, postfix).
- Fiches : `05-fiches-comportement/KC-01`→`KC-05` (smart, chain,
  récepteurs implicites, when, override), KC-10 I3 (import anticipé).
- Dépôt : ADR 0012/0113, 0118, 0119, 0123, 0131 ; mission jumelle
  Java : ADR 0026 (smart/chain — miroir), 0030 (top-1 ≥ 0,80), 0031.

## Options

**Option 1 — Catalogue de positions cadencé par paliers + frontière RPC
miroir Java.** Palier 1 (arbre + disque, sans ADR 0123) : mots-clés
contextuels, KDoc (noms de paramètres lus), imports additionnels avec
`textEdit`. Palier 2 : smart par type attendu (KC-01), chain (KC-02),
récepteurs DSL (KC-03), branches `when` (KC-04), override/stubs (KC-05),
postfix. Mesuré : KH1/KH4 (TypeValeur = Projet | Jvm(fqcn), ni surcharges
ni inférence) rendent smart/chain vides ou faux avant le palier 2 — les
61 FP typage de la V0 (argumentCount 38 + unsafeNullable 23 sur types
perdus) chifrent le coût d'une décision sur type non prouvé.

**Option 2 — Tout le catalogue immédiatement.** Contre-mesuré : chaque
position palier 2 dépend d'une information absente (type attendu,
récepteur, sujet de `when` typé) ; livrer « quand même » produit listes
vides ou candidats non prouvés — même cause racine que les FP mesurés ;
coût d'accueil à mesurer (V0/gabarit 07).

**Option 3 — Statu quo** (6 genres, tri scalaire). Écarté : l'ADR 0113 a
déjà tranché (le classement est le levier n° 1 de qualité perçue) ;
GK-012/013/014/015 resteraient tous ouverts.

**Sous-décision — classement : statistiques locales vs petit modèle hors
ligne.** (a) Statistiques locales (fréquences projet/session) : gain à
mesurer (V0/gabarit 07 — top-1 au banc commun ADR 0119, cible ≥ 0,80
miroir Java). (b) Modèle hors ligne : évaluation gain/coût obligatoire —
adoption si Δtop-1 ≥ +0,05 mesuré ET tête de budget dex (26 590/32 768
méthodes, 4,10/6 Mo mesurés — marge 6 178 méthodes/1,9 Mo), sinon rejet
écrit. Échelle : le compilateur Kotlin K2 embarqué est disqualifié
(53,6 Mo, 64 507 méthodes, ~2 170 motifs ART, 3 503 ms).

## Critères et mesures

| Position | Genre | Palier | Preuve exigée |
|---|---|---|---|
| Mots-clés contextuels | NOM | 1 | arbre |
| KDoc `@param`/`@return` | NOM | 1 | noms de paramètres lus |
| Imports additionnels (`textEdit`) | IMPORT | 1 | disque/index ; conservation par l'optimiseur (KC-10 I3) |
| Smart par type attendu | genre dédié | 2 (ADR 0123) | type attendu PROUVÉ (KC-01) |
| Chain d'extensions | MEMBRE étendu | 2 | type du récepteur prouvé (KC-02) |
| Récepteurs implicites/DSL | MEMBRE | 2 | récepteur attendu prouvé (KC-03) |
| Branches `when` manquantes | NOM | 2 | sujet typé + exhaustivité (KC-04) |
| Override/stubs | NOM | 2 | membre à surcharger prouvé (KC-05) |
| Postfix (`.let`, `.if`…) | NOM | 2 | type de l'expression prouvé |

Frontière RPC (GK-015) : capability `x-codeide.completionRanking`
versionnée, miroir du FrontiereRpc Java (ADR 0012/0118) — serveur =
candidats + classement, client = re-filtrage local par préfixe (split
RPC) ; plafonds 120/512 conservés (mesurés au banc) ; latence perçue
cible < 100 ms p95 (D5, à mesurer au gabarit 07 ; facteur ART ≈ 4×
observé côté Java, à confirmer sur appareil).

## Décision proposée

**Option 1.** Livrer d'abord positions palier 1 + chaîne de classement
(ADR 0113) + frontière RPC (GK-015 fermé dès le câblage, capability v1) ;
les positions palier 2 entrent chacune avec sa fiche KC comme gate et sa
mesure au banc commun (recall/top-1/MRR, p95). Classement : statistiques
locales d'abord ; modèle hors ligne seulement après évaluation gain/coût
mesurée — sinon rejet écrit dans cette ADR.

## Conséquences

- Sans ADR 0123, l'offre reste honnête : mots-clés, KDoc, imports,
  statistiques — jamais de smart/chain sur type non prouvé (GK-012/013
  restent ouverts et datés, pas masqués).
- Les imports posés par la complétion sont contractualisés avec
  l'optimiseur (KC-10 I3, ADR 0131) : jamais retirés sans preuve
  d'inutilité.
- Le budget dex est une donnée d'entrée de toute évaluation de modèle
  (marge mesurée 6 178 méthodes).

## Plan de migration progressive

1. Chaîne de classement + banc baseline (ADR 0113/0119).
2. Positions palier 1 + capability `x-codeide.completionRanking` v1.
3. Statistiques locales, mesure top-1 (gabarit 07).
4. Positions palier 2 après ADR 0123, une fiche KC par vague.

## Tests de non-régression et critères de sortie

- Fiches KC-01→KC-05 vertes au fil des vagues ; 44 comportements
  complétion du cahier non régressés.
- Banc commun : recall ≥ baseline, top-1 ≥ 0,80 (cible miroir Java),
  p95 < 100 ms perçu, zéro « saut » de liste ; aucune position palier 2
  servie sans sa preuve (propriété testée).

## Risques

- R1 — Le palier 2 tarde, l'écart catalogue reste visible : mitigation =
  GK-012/013/014 datés et publiés, pas niffés.
- R2 — Le modèle hors ligne grignote le budget dex : mitigation =
  évaluation chiffrée obligatoire (marge 6 178 méthodes mesurée).
- R3 — Dérive du contrat RPC côté client : mitigation = capability
  versionnée + tests de parité (miroir W4 Java, ADR 0118).
