# Journal d'exécution — Vague V-B : Typage étendu, palier 2 (P1)

> Vague V-B du [plan](../04-plan-vagues.md) — exécutée le **12/11/2026**.
> Périmètre : V-B1 la REPRÉSENTATION (arguments de type écrits), V-B2 la
> SUBSTITUTION des paramètres de type des extensions (V-B3 — récepteurs
> implicites et smart casts étendus — a été livrée par la V-A). Écarts
> visés : la part typage de GK-001/GK-003 (ADR 0123). La frontière est
> versionnée : [`moteur/SPECIFICATION-TYPES.md`](../../../moteur/SPECIFICATION-TYPES.md).

## 1. Ce qui est livré

1. **V-B1 — les arguments de type écrits** : `TypeValeur.Jvm` porte ses
   arguments (`List<Client>` → `Jvm("kotlin.collections.List",
   ["Client"])`) ; `resoudreTypeEcrit` les analyse par profondeur de
   chevrons (imbrication tolérée, déséquilibre → rien) ; la LIAISON
   (`MarcheTypee.nomTypeEcritDeReference`) les CONSERVE désormais
   (`List<Int>?` rend `List<Int>?` — les consommateurs dépouillent).
2. **V-B2 — `HIERARCHIE_BUILTINS`** : les interfaces `kotlin.collections.*`
   sont des facades sans class-file (D4 — `kotlin.kotlin_builtins`,
   constaté : `kotlin/collections/List.class` absent du stdlib 2.2.10) :
   la chaîne de supertypes figée par le langage se déclare en table
   (miroir `BUILTINS_PROPRIETES`, fiche 038) — `List ⊂ Collection ⊂
   Iterable`, etc. La recherche d'extensions par récepteur
   (`recepteursExtension`) consulte désormais le nom MAPPÉ JVM, le nom
   KOTLIN ET la table — `Iterable<T>.map` atteint un récepteur `List<T>`.
3. **V-B2 — la substitution positionnelle** : la lambda d'une EXTENSION
   du classpath reçoit l'attente substituée — le paramètre fonctionnel
   décodé `Function1<T, R>` (les N premiers arguments = les paramètres,
   le dernier = le retour ; la forme écrite `(T) -> R` également) se
   substitue : chaque paramètre à UNE lettre majuscule ← l'argument
   écrit du récepteur au même rang. `clients.map { it.nom }` type `it`
   par l'ÉLÉMENT. Sans arguments écrits : silence D3 (jamais un faux
   positif — le comportement V-A demeure).
4. **Compléments de fiabilité** (les 3 FP du corpus élargi — le corpus
   gagne les fichiers neufs des vagues) : la branche ELSE d'une
   IF-EXPRESSION `if (x == null) a else …x…` voit `x` non nul ;
   `Character` entre au catalogue KP8.

## 2. Les preuves

| Preuve | Résultat |
|---|---|
| Tests moteur (`TypagePalier2KotlinTest`) | **6 verts** — `it.nom` résout ET `it.inconnu` se PROUVE absent ; forme parenthésée ; chaîne silencieuse ; récepteur sans arguments muet ; complétion membre sur `it.` propose l'élément ; monde réaliste sain |
| P1 auto-corpus (102 fichiers, corpus élargi) | **FP=0, FN=0 (0,000/1000 l.) — mode STRICT VERT** |
| Suite `:kotlin:test` | 556 verts (550 + 6) |
| SPECIFICATION-TYPES.md | version 1 (V-B) publiée — la frontière versionnée de l'ADR 0123 |

## 3. Non-dépassements (version 1, SPECIFICATION-TYPES.md §3)

Le RETOUR des extensions génériques n'est pas typé (la chaîne
`.map { }.filter { }` se tait) ; la substitution ne traverse pas les
arguments imbriqués ; les surcharges d'extension par arité de lambda ne
se départagent pas ; l'inférence locale des arguments (sans écriture)
relève du palier suivant.

## 4. Recalage

La V-B a été livrée en UNE séance (l'estimation du plan était 3–5) : la
matière de la V-A (gardes, typage borné, index) a porté — la
substitution n'a ajouté que la représentation des arguments et la table
de hiérarchie. La voie est ouverte pour V-C (diagnostics élargis — le
typage de l'élément permet les contraintes simples) et V-D (complétion
smart — le type attendu se dérive de la même attente).
