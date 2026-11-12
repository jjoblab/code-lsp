# SPECIFICATION-TYPES — la frontière versionnée du système de types (palier 2)

> ADR 0123 (typage étendu, palier 2) — la frontière VERSIONNÉE de la
> représentation des types du moteur Kotlin. Toute extension de cette
> spécification est une NOUVELLE VERSION documentée ici (jamais un
> changement silencieux) ; les consommateurs (diagnostics, complétion,
> survol) dérivent TOUS de cette frontière. Vague **V-B** — 12/11/2026.
> Miroir de [`SPECIFICATION-RESOLUTION.md`](SPECIFICATION-RESOLUTION.md).

## 1. La représentation (`TypeValeur`)

```
TypeValeur ::= Projet(type: TypeProjet)                     — le modèle de symboles
            |  Jvm(fqcn: String,                            — le classpath, mappé Kotlin
                   arguments: List<String> = [])            — V-B : les arguments ÉCRITS
```

- **`Projet`** — un type du projet (classe, interface, enum, objet) par
  son modèle de symboles (fiche 013) ;
- **`Jvm`** — un type de bibliothèque par son FQCN Kotlin
  (`kotlin.collections.List`) ; depuis la vague V-B il porte ses
  **ARGUMENTS DE TYPE ÉCRITS** — des TEXTES, jamais des types récursifs :
  `List<Client>` → `Jvm("kotlin.collections.List", ["Client"])`,
  `Map<String, List<Int>>` → `Jvm("kotlin.collections.Map",
  ["String", "List<Int>"])` (l'imbrication reste un TEXTE — D3 aux
  frontières : les arguments imbriqués ne se substituent pas).

### 1.1 Ce que la représentation N'EST PAS (non-dépassements assumés)

- **Pas de variance** (`out`/`in` conservés en texte, jamais interprétés) ;
- **pas de nullabilité structurelle** — la nullabilité vit dans le TEXTE
  écrit (`String?`) consulté par la liaison (fiche 046), pas dans
  `TypeValeur` ;
- **pas de types plateformes** (interop Java — les types Java arrivent
  par la vue JVM comme `Jvm`, sans marque d'origine) ;
- **pas de paramètre de type comme valeur** — `T` nu reste non typé
  (D3) : la substitution V-B opère sur les TEXTES de l'attente, pas sur
  une représentation close.

## 2. Les règles de dérivation (V-B)

### 2.1 Les arguments écrits (`resoudreTypeEcrit`)

`List<Client>` se résout en `Jvm("kotlin.collections.List", ["Client"])`
— l'analyse par PROFONDEUR de chevrons tolère l'imbrication et rejette
les chevrons déséquilibrés (rien plutôt que mal). La LIAISON
(`MarcheTypee`) conserve désormais les arguments écrits
(`List<Int>?` rend `List<Int>?`) — les consommateurs qui n'en veulent
pas dépouillent par `substringBefore('<')`.

### 2.2 La hiérarchie des collections intégrées (`HIERARCHIE_BUILTINS`)

Les interfaces `kotlin.collections.*` sont des FACADES DE COMPILATION
sans class-file (D4 — `kotlin.kotlin_builtins` les porte) : leur chaîne
de supertypes se déclare dans la table `HIERARCHIE_BUILTINS`, figée par
le langage (`List ⊂ Collection ⊂ Iterable`, `MutableList ⊂ List ∩
MutableCollection`…, `Map` sans ancêtre utile). La recherche
d'extensions par récepteur la consulte AVANT de renoncer.

### 2.3 La substitution des paramètres de type (l'attente des extensions)

Une lambda passée à une EXTENSION du classpath dont le récepteur est
TYPÉ et porte des ARGUMENTS écrits reçoit l'attente SUBSTITUÉE :

```
clients: List<Client>          — le récepteur, arguments ["Client"]
clients.map { it.… }           — Iterable<T>.map(transform: (T) -> R)
                               — T ← Client (substitution positionnelle)
                               — attente = ["Client"] ⇒ it: Client
```

- la forme DÉCODÉE du paramètre fonctionnel est `Function1<T, R>` (le
  miroir JVM rendu par le décodeur @Metadata : les N premiers arguments
  sont les PARAMÈTRES, le dernier est le RETOUR — `Function2<K, V, Unit>`
  rend `["K", "V"]`) ; la forme ÉCRITE du projet est `(T) -> R` ;
- la substitution est **POSITIONNELLE et BORNÉE** : seuls les
  paramètres à UNE lettre majuscule se substituent (le N-ième ← le
  N-ième argument du récepteur) ; tout autre texte reste tel quel ;
- **sans arguments écrits** (récepteur brut `List<Any>`… ou récepteur
  non typé) : PAS d'attente — silence D3 (le comportement de la V-A
  demeure, jamais un faux positif).

### 2.4 Ce que la substitution débloque (mesuré)

- le diagnostic : `it.nom` RÉSOUT (l'élément porte le membre) et
  `it.inconnu` se PROUVE absent (le jeu de l'élément est clos) —
  tests `TypagePalier2KotlinTest` ;
- la complétion MEMBRE sur `it.` propose les membres de l'ÉLÉMENT ;
- la chaîne `.map { }.filter { }` reste SILENCIEUSE (le RETOUR de
  l'extension n'est pas typé à ce palier — non-dépassement, §3).

## 3. Les non-dépassements de la version V-B (le palier suivant)

- le **RETOUR** des extensions génériques (`map` rend `List<R>` — R
  substituable) n'est PAS typé : la chaîne se tait ;
- la substitution ne traverse PAS les arguments imbriqués
  (`List<List<Client>>` prête le texte `"List<Client>"`, pas Client) ;
- les surcharges d'extension par ARITÉ de lambda ne se départagent pas
  (la première candidate fonctionnelle gagne) ;
- l'inférence LOCALE des arguments (`listOf<Client>()` sans écriture)
  relève du palier suivant — seule l'ÉCRITURE compte ici.

## 4. Le registre des versions

| Version | Date | Contenu |
|---|---|---|
| 1 (V-B) | 12/11/2026 | arguments de type écrits sur `TypeValeur.Jvm` ; conservation des arguments par la liaison ; `HIERARCHIE_BUILTINS` ; substitution positionnelle des paramètres de type des extensions (attente `FunctionN` et forme écrite) |
