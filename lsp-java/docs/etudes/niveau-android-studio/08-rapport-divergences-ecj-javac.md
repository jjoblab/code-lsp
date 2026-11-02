# Rapport de divergences ecj↔javac — corpus réel (V-A2, ADR 0030)

> Mesuré le 01/11/2026 sur le corpus des 5 projets épinglés
> (LISEZMOI §1), montage exhaustif du classpath réel (manifestes du
> préparateur — la vue `android-classes-jar` de javac, jetifier et
> classes Kotlin compilées compris). Transcripts chiffrés :
> `mesures/2026-11-01-va2-corpus-reel.txt` (R5 : aucune valeur
> fabriquée). Ce rapport classe chaque divergence constatée entre le
> moteur (ecj 3.40 patché) et javac (le compilateur des builds AGP du
> corpus) par cause racine et statue le traitement.

## 1. Le périmètre de comparaison

Le banc P3 diagnostique des sources qui COMPILENT avec javac (variante
debug de chaque projet, Gradle épinglé) : toute ERREUR émise par le
moteur sur ces sources est une divergence. Le banc P4 casse le code
(mutations) et vérifie le rappel des erreurs ; le banc P7 renomme et
vérifie la compilabilité du résultat. Sur **111 700 lignes mesurées**
(105 051 noyau dur + 6 649 générées, 615 fichiers), le moteur émet
**6 erreurs divergentes** (0,054/1 000 lignes) et attrape **108/109
mutants** (0,991). L'équivalence rapide/complet (P1) est **prouvée**
sur 42 mesures corpus (0 divergence).

## 2. Divergences GÉNUINES ecj↔javac (à arbitrer)

### D-01 — `+=` sur `CharSequence` (markor ×2, ecj 536871072)

`MarkorDialogFactory.java` :

```java
CharSequence titre = ...;
titre += getString(R.string.x);   // javac : OK ; ecj 3.40 : erreur
```

- **javac** accepte : JLS 15.26.2, l'affectation composée équivaut à
  `titre = (CharSequence)(String) titre + rhs` — le cast implicite de
  la conversion d'assignation composée rend le programme légal.
- **ecj 3.40** rejette : « The operator += is undefined for the
  argument type(s) CharSequence, String ».
- **Impact mesuré** : 2 FP sur 36 547 lignes markor (0,055/1 000).
- **Traitement proposé** (carte G-005) : post-traitement ciblé du
  diagnostic — éteindre l'erreur 536871072 quand le type statique de
  l'opérande gauche est `CharSequence` (et le droit `String`), en
  DOCUMENTANT la règle (référence JLS 15.26.2) ; alternative : quirk
  configurable. Arbitrage propriétaire requis (la parité « ce que
  javac accepte » prime sur la sévérité ecj pour un IDE).

### D-02 — niveau de source 21 vs défaut 17 (gh4a ×4, ecj 4195411)

`EventViewHolder.java` utilise le *pattern matching in switch*
(Java 21) ; le projet compile en `sourceCompatibility 21`, le moteur
diagnostique en compliance **17** (défaut du constructeur) :

- « The Java feature 'Pattern Matching in Switch' is only available
  with source level 21 and above » ×4 sur 23 010 lignes (0,174/1 000).
- **Cause racine** : le MANIFESTE de montage ne porte pas la
  compliance du projet — le moteur ne peut pas la deviner.
- **Traitement proposé** (carte G-002) : étendre le format du
  manifeste (`compliance:<17|21|…>` extraite de `compileOptions` par
  l'init script) + l'API du moteur l'accepte déjà (`compliance` du
  constructeur) ; côté hôte, le contrat de module la transportera
  (R3 — la sync prime). Petit, à faire en première vague de report.

## 3. Divergences de MONTAGE (résolues — lessons)

Ces écarts n'étaient PAS des défauts du moteur : le classpath d'un
build AGP est PLUS RICHE que « android.jar + AAR bruts ». Le
préparateur de manifestes les couvre désormais tous, avec refus
franc sur repli (jamais de vert silencieux).

| # | Symptôme mesuré (1re passe) | Cause | Correction |
|---|---|---|---|
| M-1 | ~3 800 « R cannot be resolved » | les symboles R sont un **JAR** d'AGP (`compile_and_runtime_not_namespaced_r_class_jar`, `compile_r_class_jar`), jamais une source en AGP 8+ | R.jar par module au classpath |
| M-2 | 51 « BuildConfig » + ~350 « *Binding » | racines générées posées au RÉPERTOIRE du fichier au lieu du PARENT DU PAQUET | calcul racine = répertoire − chemin de paquet |
| M-3 | 3 « LoaderManager » (gh4a) | `android.enableJetifier=true` : javac voit les artefacts RÉÉCRITS (rxloader référence l'ancien espace de noms) | vue d'artefacts `android-classes-jar` (jetifier appliqué) |
| M-4 | 18 FP « méthode non définie » (anysoftkeyboard) | classes **Kotlin** référencées par le Java : javac compile contre `built_in_kotlinc` ; la light lexicale du moteur primait les jars | classes Kotlin zippées au montage + **correctif moteur (A)** : la light cède aux jars |
| M-5 | formes de chemins générés | AGP écrit `<variante>/`, `<flavor>/<buildType>/` (séparés) ET `<suffixe>Variante` camelCase (safeargs) | trois motifs dans `racines_generees` |

**Quirk ecj documentée** (sans impact après correction) : sur
supertype NON RÉSOLU, ecj peut émettre « cannot subclass the final
class X » alors que X n'existe pas dans l'environnement (recovery
d'ecj — la classe Kotlin `open` réelle n'est pas finale). Piège de
diagnostic : le message oriente à tort vers un problème de finalité.

## 4. Limites de l'HARNAIS de mesure (corrections proposées)

| # | Constat | Cause | Traitement |
|---|---|---|---|
| H-1 | 1 faux négatif P4 (AddOnsFactory) : l'erreur existe mais sur le SÉLECTEUR (`unmodifiableList`), 13 caractères après l'ancre posée sur le récepteur (`Collections.`) | tolérance de recouvrement d'ancre fixée à +12 | élargir l'ancre au JETON QUALIFIÉ complet (`Récepteur.sélecteur`) — arbitrage mesure, pas moteur |
| H-2 | 10 « rappel de renommage nul » fantômes (1re passe P7) | l'ancre P7 tombait sur le MOT-CLÉ `final`/`static` (regex de ciblage défaillante) — le moteur a raison de ne rien renommer sur un mot-clé | corrigé : capture du dernier identifiant avant `=`, `(`, `;` |

## 5. Corrections moteur livrées avec la mesure

1. **Ordre de repli** (`EnvironnementNoms`, `TypesSynthetiques.primeSurJars`)
   : les déclarations Kotlin lexicales (API partielle) cèdent désormais
   aux jars du classpath — les classes light Android (R, BuildConfig,
   Binding) continuent de PRIMER les jars (anti-doublon ADR 0021 R3 :
   un R de build périmé ne masque jamais le référentiel frais).
   Régression : `DeclarationsKotlinTest`.
2. **Renommage** (`CollecteursRenommage.visiteReferenceChamp`) : off-by-one
   `plageJeton` (limite exclue vs `sourceEnd` inclus d'ecj) — les
   occurrences `this.champ` étaient perdues, cassant le renommage
   (mesuré basicsample/AppExecutors). Régression :
   `OccurrenceThisChampTest`.

## 6. État des critères de sortie V-A (ADR 0030 (c))

| Critère | Cible | Mesuré | Statut |
|---|---|---|---|
| P3 FP/1 000 lignes | < 0,5 | **0,054** | TENU |
| P3 noyau dur | 0 | 6 (D-01 ×2, D-02 ×4) | NON TENU — 2 cartes à arbitrer |
| P4 rappel mutants | ≥ 0,95 | **0,991** | TENU |
| P1 équivalence corpus | 0 divergence | **0/42** | TENU |
| P7 rename sûr corpus | compilable | **28/28** | TENU |
| p95 frappe | ≤ baseline +10 % | banc qualité inchangé (CI) | à re-vérifier CI |

La sortie de vague V-A est donc acquise sur 4 critères sur 6 ; les
deux restants sont des DIVERGENCES DOCUMENTÉES (D-01, D-02) et non
des défauts non-diagnostiqués — la suite (report honnête ADR 0029
phase 5) les porte aux cartes G-002/G-005 de la matrice.
