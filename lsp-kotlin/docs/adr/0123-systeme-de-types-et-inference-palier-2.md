# ADR 0123 — Système de types et inférence étendus (palier 2)

- **Statut** : Proposé
- **Date** : 2026-10-31
- **Amende** : ADR 0107 (périmètre de typage), ADR 0108 (décodage des
  récepteurs génériques)
- **Écarts** : GK-001 (KH1), GK-003, GK-012, GK-013

## Contexte

`TypageBorne.kt:19-26` ne sait représenter que `Projet | Jvm(fqcn)` : pas
d'arguments de type, de nullabilité, de types fonctionnels, de paramètres de
type, de variance (audit §1.1). Conséquence mesurée : 603 FP `it` (attente de
lambda indémontrable sur extensions génériques), 11 FP `T`, 38 FP
`argumentCount` et 23 FP `unsafeNullable` sur types perdus (audit §4.3), et
les constructions les plus fréquentes d'un projet Android (`list.map { }`,
`Flow<T>`, `State<T>`, scope functions, DSL) perdent leur type → complétion
muette, diagnostics muets, pas de smart/chain. Le palier 2 étend le typage
**sans devenir un compilateur** : ce qu'on ne sait pas prouver, on
sous-rapporte.

## Sources consultées

- Audit §1.1 (preuves fichier:ligne du périmètre actuel), §4.3 (FP mesurés).
- Référence §A1/A2 (phases de types et de résolution des corps côté K2 ;
  complétion par type attendu/récepteur).
- Spécification du langage Kotlin (types, variance, smart casts,
  inférence) — conformité exigée, oracle K2 en différentiel.
- Cahier fiches 046/049 (contrat actuel : smart casts positionnels,
  bornologie conservée).

## Options

| Option | Description | Critères |
|---|---|---|
| **1 — Typage étendu maison borné (recommandée)** | représentation à arguments + nullabilité + types fonctionnels + paramètres de type (avec bornes) + projections de variance ; résolution d'extensions par récepteur typé (y compris génériques) ; surcharges par arité + types (première candidate applicable, ambiguïté = silence) ; récepteurs implicites de portée ; smart casts étendus (`when` à sujet, elvis) ; inférence du paramètre de lambda par attente étendue au classpath | débloque smart/chain (KC-01/02/03), corrige les familles FP mesurées ; coût dex [estimé] +300–600 méthodes / +0,15–0,3 Mo — à mesurer à chaque sous-vague (garde E-K8) |
| **2 — Builder inference / inférence complète** | algorithme complet du compilateur | écarté : hors budget, faux positifs garantis — c'est le « presque-K2 » rejeté (ADR 0107 option 2 originale) |
| **3 — Statu quo** | 2 cas | FP mesurés 28,05/1 000 l. + complétion muette sur les constructions les plus fréquentes — inacceptable pour la cible |

## Critères et mesures

- **Critère d'arrêt écrit** (ce qu'on ne fait pas, on sous-rapporte) : builder
  inference, inférence de type de retour récursive, résolution d'ambiguïté
  par plus spécifique, variance en ÉCRITURE (projection star en lecture
  seulement), intersections non bornées ;
- **conformité à la spécification** vérifiée par **test différentiel contre
  l'oracle K2 déjà présent en CI** (extension du banc P1 : le type rendu par
  le palier 2 vs le type de l'oracle sur un corpus d'expressions étiquetées —
  cible ≥ 95 % d'unification correcte sur le sous-ensemble couvert) ;
- mesure KH1-proxy (P6) : taux de couverture typée des positions membres —
  baseline V0 à établir, cible ≥ 80 % sur corpus réel après V-B ;
- FP : la correction des familles `it`/`T`/`this` est livrée AVEC le palier 2
  (l'attente de lambda étendue au classpath supprime la cause racine).

## Décision proposée

**Option 1**, livrée par familles (V-B1 représentation + nullabilité ;
V-B2 arguments + extensions génériques ;V-B3 récepteurs implicites + smart
casts étendus + surcharges), chaque sous-vague fermée par le différentiel
oracle et la garde dex. La « frontière d'unification » devient versionnée
dans `SPECIFICATION-TYPES.md` (miroir de `SPECIFICATION-RESOLUTION.md`).

## Conséquences

- `TypeValeur` passe de 2 cas à une hiérarchie versionnée — le code existant
  qui pattern-match est migré mécaniquement (le compilateur liste les sites) ;
- le décodeur `@Metadata` apprend les récepteurs génériques (amende 0108 :
  la clé devient « récepteur erasé + contrainte substituée ») ;
- les inlay hints et semanticTokens deviennent fondables (0136).

## Plan de migration progressive

Sous-vagues V-B1→V-B3 ; le palier 1 reste servi pour tout ce que le palier 2
ne prouve pas (repli, ADR 0122).

## Tests de non-régression et critères de sortie

- Fiches 046/049 inchangées (les comportements actuels restent vrais) ;
- différentiel types ≥ 95 % sur le sous-ensemble couvert ; FP auto-corpus
  < 0,5/1 000 l. ; P6 couverture typée ≥ 80 % ; dex ≤ budget (0133) ;
- p95 frappe ≤ baseline + 10 %.

## Risques

- R1 — Explosion combinatoire des substitutions : mitigation = bornes de
  profondeur (2) et cache par déclaration (miroir K2 par phases) ;
- R2 — Faux positifs par sur-pondération : mitigation = l'ambiguïté est
  TOUJOURS silence ; R3 — dex : garde + packs.
