# 01-audit-moteur — Audit du moteur Kotlin actuel (Phase 1)

> **Mission** : amener le LSP Kotlin de `code-lsp` au niveau d'intelligence
> d'Android Studio (mode K2) — audit, étude de référence, matrice d'écarts,
> ADR, plan par vagues, puis STOP.
> **Branche** : `etude/moteur-kotlin` — état audité : commit `df3cf85`
> (« attestation CI consignée au document 11 §4 »), CI **VERTE 10/10** sur le
> run GitHub Actions `41170597847` [CONFIRMÉ, document
> `docs/etudes/moteur-kotlin/11-decisions-proprio-post-cloture.md` §4].
> **Date** : 2026-10-31. **Langue** : français (règle du dépôt).

## Convention R5 — honnêteté des preuves

Chaque affirmation porte l'un des statuts suivants :

- **[CONFIRMÉ]** — preuve `fichier:ligne` (chemin relatif au dépôt) ou
  commande reproductible avec sa sortie ; les mesures du dépôt sont **JVM de
  bureau** (E-K4) et aucune mesure appareil du moteur Kotlin n'existe (E-K2) ;
- **[INFIRMÉ]** — l'hypothèse de départ du prompt est contredite par la mesure
  ou la lecture ;
- **[NON VÉRIFIÉ]** — non mesurable dans l'environnement de la mission, avec
  la raison et le protocole proposé (gabarit appareil `07-`, captures `06-`).

Aucune baseline signée n'a été re-signée hors la baseline de **parité
structurale** (fiche 010), rendue nécessaire par l'ajout des fichiers du banc
V0 — écart au §1.5 du prompt tracé dans [`ALERTES.md`](ALERTES.md) (E-K9),
validation propriétaire demandée.

---

## 0. Méthode et périmètre

**Terrain** : bac à sable Linux 2 cœurs / ~3,9 Gio / OpenJDK 21.0.13 (le
module force toolchain 17, auto-provisionné). Android Studio **n'est pas
exécutables** ici ; l'appareil de référence (moto g06) **n'est pas
disponible** (E-K2). Le build a été vérifié localement :
`./gradlew :kotlin:compileTestKotlin` **BUILD SUCCESSFUL en 2 min 46 s**
[CONFIRMÉ, exécuté le 31/10/2026 dans l'environnement de la mission].

**Ordre de lecture** (§2 du prompt) : `AGENTS.md`, `README.md`, `USAGE.md`,
documents d'étude `00` à `11` de `docs/etudes/moteur-kotlin/`, ADR
`0100`–`0121`, cahier `docs/cahier-specifications-kotlin/` (53 fiches /
469 comportements), journaux `03`–`10`, `docs/INTEGRATION-CODEIDE.md`,
`docs/BASCULE-CODEIDE.md`, `bancs/GABARIT-BASCULE-APPAREIL.md`, code du
module `kotlin/` (lecture exhaustive des en-têtes + des fichiers porteurs,
`rg` systématique), mission jumelle Java (`etude/moteur-java` en worktree
lecture seule : synthèse, ADR 0019–0031, banc P1–P8, kit de mesure, passe 1
appareil moto g06).

**Mesures exécutées pendant l'audit** :

- statique : `rg` sur `kotlin/src/main` (diagnostics, genres, actions,
  verrous, Gradle, `generated`, `onTrimMemory`, `x-codeide`) — preuves
  citées ci-dessous ;
- dynamique locale : build + compilation des tests (ci-dessus), puis
  **banc V0 P1–P9** (livré par cette mission, `kotlin/src/test/kotlin/jo/
  codeide/lsp/kotlin/coherence/`) : fidélité différentielle moteur vs oracle
  K2 sur l'auto-corpus (les sources du module lui-même, ~25 250 l. de vrai
  Kotlin compilable) — résultats au §4.3 ;
- héritées du dépôt (reprises telles quelles, avec leur statut d'origine) :
  spike S0–S3 (`02-spike-mesures.md`), fiche 021 (reparse/diagnostics JVM),
  banc de complétion signé, passe 1 appareil **du moteur Java** (facteur
  ART observé ≈ 4×, jamais mesuré côté Kotlin).

**Périmètre respecté** : aucune modification du comportement visible du
moteur ; aucune baseline de qualité re-signée ; le module `:java` et les
baselines Java n'ont pas été touchés.

---

## 1. État des hypothèses de départ KH1–KH16

| # | Hypothèse (prompt §3) | Statut | Preuve principale |
|---|---|---|---|
| KH1 | Typage à deux cas seulement, pas de génériques/nullabilité/variance | **[CONFIRMÉ]** | `TypageBorne.kt:19-26` |
| KH2 | « Preuve ou silence » : 10 familles `kt.*`, FN structurels | **[CONFIRMÉ]** | `api/DiagnosticsKotlin.kt:37-100` ; `baseline-fidelite.txt` |
| KH3 | Évidence « zéro FP » bâtie sur un corpus minuscule | **[CONFIRMÉ]** | `src/test/resources/fidelite/` : 86 l. ; banc complétion : 16 positions |
| KH4 | Ni surcharges, ni inférence, ni récepteurs implicites de DSL | **[CONFIRMÉ]** | `TypageBorne.kt` (attentes PROJET/SAM) ; §1.4 |
| KH5 | Le mode profond K2 n'existe pas | **[CONFIRMÉ]** | ADR 0112 (statut) ; doc 11 §2.5 |
| KH6 | Complétion : 6 genres de contexte, INCONNU = vide | **[CONFIRMÉ]** (7 valeurs dont INCONNU) | `api/CompletionKotlin.kt:86-117` |
| KH7 | Actions de code : 3 familles, quickfix seul | **[CONFIRMÉ]** | `CorrectifsTexte.kt:28-44` ; `CapacitesServeur.kt:110` |
| KH8 | Code généré réel (KSP/KAPT) non lu, plugins ignorés | **[CONFIRMÉ, nuancé]** | `ScanProjetParesseux.kt:654` ; `ResolutionReferences.kt:217-223` ; synthétiques = atout |
| KH9 | Aucune intelligence Gradle Kotlin DSL | **[CONFIRMÉ]** | `rg -i gradle` → 1 commentaire ; ADR 0111 non réalisé |
| KH10 | Compose/coroutines : previews seulement | **[CONFIRMÉ]** | `preview/` (648+156 l.) ; absence dans diagnostics/complétion |
| KH11 | Interop Java unilatérale et bornée | **[CONFIRMÉ]** | `LecteurDeclarationsJava.kt:313` (arité sautée) ; `OccurrencesProuvees.kt:144-147` |
| KH12 | Budget dex serré (81 % méthodes / 68 % taille) | **[CONFIRMÉ]** | E-K8 : 26 590 / 4 294 352 o ; plafonds 32 768 / 6 Mo |
| KH13 | Le contrat mesure la parité avec l'ancien moteur, pas Android Studio | **[CONFIRMÉ]** | ADR 0107 « bornologie conservée TELLE QUELLE » ; bancs auto-référents |
| KH14 | Usages multi-fichiers par re-parse, jamais mesuré à l'échelle | **[CONFIRMÉ]** | `OccurrencesProuvees.kt:73-77, 169-178` |
| KH15 | 7 capacités absentes, `x-codeide.completionRanking` non annoncée | **[CONFIRMÉ]** | `CapacitesServeur.kt:38-44` ; `rg 'x-codeide'` → néant |
| KH16 | Aucune mesure appareil, moteur livré mais PAS EN SERVICE | **[CONFIRMÉ]** | doc 11 §3 ; `INTEGRATION-CODEIDE.md` (I0–I6 non démarrés côté Kotlin) |

### 1.1 KH1 — Le typage ne sait représenter que « type du projet » ou « FQCN de bibliothèque » — [CONFIRMÉ]

La hiérarchie des types tient en deux cas (`resolve/TypageBorne.kt:19-26`) :

```kotlin
internal sealed class TypeValeur {
    internal class Projet(internal val type: TypeProjet) : TypeValeur()   // :22
    internal class Jvm(internal val fqcn: String) : TypeValeur()          // :25
}
```

- **Arguments de type : NON représentés.** Un `TypeValeur.Jvm` ne porte qu'un
  FQCN nu ; la résolution **dépouille** `?` et `<…>` avant de nommer le type
  (`resolve/ResolutionReferences.kt:259-264` — « nullabilité et arguments
  sont DÉPOUILLÉS », `removeSuffix("?")` :262) ; `nomTypeEcrit` fait
  `substringBefore('<')` (`TypageBorne.kt:833`) ; l'attente de lambda
  `premierArgumentFunction1` rend le premier segment **en dépouillant les
  génériques imbriqués** (`MutableList<E>` → `MutableList`,
  `TypageBorne.kt:711-730`). Conséquence directe : `list.map { it.nom }`,
  `Flow<T>`, `State<T>`, `Result<T>`, `Map<K,V>` et les chaînes génériques
  perdent leur type après la première opération générique.
- **Nullabilité : NON représentée.** Le `?` est supprimé avant résolution ;
  la non-nullabilité n'existe que comme raffinement positionnel
  (`Raffinement(nonNul)`, `TypageBorne.kt:256`).
- **Types fonctionnels : pas en tant que `TypeValeur`** — ils vivent comme
  types *écrits* (`ParametreProjet.typeFonction`, `ModeleProjet.kt:188-199`)
  ou comme forme RENDUE du classpath (`Function1<R, U>`,
  `TypageBorne.kt:614-616, 691-706`).
- **Paramètres de type : par NOM seulement.** Le classpath rend un
  classificateur `TypeParameter` par son nom (« T »,
  `classpath/DecodeurMetadonnees.kt:247-257`) ; côté projet, la substitution
  n'existe que pour le correctif « membres manquants »
  (`ModeleProjet.kt:84-90`).
- **Variance : ABSENTE.** `rg -i 'variance|projection' resolve/ classpath/`
  → aucune occurrence fonctionnelle (seul le rendu d'arguments à
  `DecodeurMetadonnees.kt:264-265`).
- **Smart casts : positionnels et conservateurs.** `raffinementAuPoint`
  (`TypageBorne.kt:281-340`) couvre : THEN d'un `if (x is T)` (:285-293),
  opérande droit d'un `&&` (:294-302), corps d'entrée d'un `when` sans
  sujet (:307-319), garde à sortie précoce `if (x !is T) return/throw`
  (`analyserGarde` :414-429, `estTerminal` :432-442). **Un `||` ANNULE tout
  raffinement** (:303-305, :386-387 — « bornologie ADR 0107 : jamais un cast
  hors preuve ») ; une locale postérieure ombrage le nom et coupe le cast
  (:347-364).
- **« Frontière d'unification »** : le terme n'existe pas dans le code
  (`rg 'unification' kotlin/src/main` → néant) ; le concept effectif est le
  **rabattement documenté** ci-dessus (arguments dépouillés, nom de base).

**Conséquence mesurable** : la couverture typée des expressions est le
défaut n° 1 du moteur (c'est la mesure KH1-proxy du banc V0 P6, et la
motivation de l'ADR 0123 — typage étendu, palier 2).

### 1.2 KH2 — « Preuve ou silence » : 10 familles, zéro FP revendiqué, FN structurels — [CONFIRMÉ]

Catalogue **exact** des codes émis (`api/DiagnosticsKotlin.kt:37-100`) :
`kt.syntax` (:39), `kt.unusedImport` (:42), `kt.conflictingImport` (:45),
`kt.unresolved` (:55), `kt.missingReturn` (:61), `kt.typeMismatch` (:69),
`kt.argumentCount` (:77), `kt.unsafeNullable` (:84), `kt.unreachable` (:91),
`kt.valReassign` (:99) — **10 familles**. Points d'émission :
`DiagnosticsImports.kt:80/201/236`, `DiagnosticsSemantiques.kt:335/1042`,
`DiagnosticsTypage.kt:132/217/328/359/412` [CONFIRMÉ, `rg '"kt\.'`].

Le mécanisme « preuve ou silence » repose sur :

1. le **verdict trichotomique** (`ResolutionReferences.kt:120-129`) :
   `RESOLU` (preuve de présence), `MUET` (« la preuve d'absence est
   IMPOSSIBLE (univers ouvert — silence D3) »), `SIGNALABLE` (« l'absence est
   PROUVÉE ») — un diagnostic n'est émis que si `verdict == SIGNALABLE` ;
2. la **garde globale** `nomsNusProuvables` (index posé + scan non
   `PARTIEL` + aucun joker non énumérable, `ResolutionReferences.kt:137-138`) :
   en régime dégradé la passe rend vide (`DiagnosticsSemantiques.kt:99-101`) ;
3. les **univers ouverts** : héritage écrit/enum/data class → membres muets
   (`DiagnosticsSemantiques.kt:61-65`, `ModeleProjet.kt:110-111`) ; noms
   générés KSP/Hilt/Room/DataBinding → `MUET`
   (`ResolutionReferences.kt:217-223`, listes closes :1085-1092) ;
4. la **garde `it`** (`DiagnosticsSemantiques.kt:893-947`, cœur :915-935) :
   sans attente démontrable de la lambda englobante, ou attente à
   EXACTEMENT 1 paramètre → silence ; une attente à une autre arité condamne
   le nom (correctif de la vague T, miroir exact du typage).

Le banc de fidélité enregistre déjà un faux négatif assumé sur son propre
témoin positif (`src/test/resources/fidelite/baseline-fidelite.txt` :
`temoin-positif.kt oracle=3 moteur=2 fp=0 fn=1 — FN 10:zzMembreInconnu`)
[CONFIRMÉ, contenu du fichier]. Le moteur Java de référence (mission
jumelle) a montré la même structure avant son banc P1–P8 (26 violations
initiales, corrigées en V-A1) — l'équivalent Kotlin est livré par cette
mission (banc V0, §4.3).

### 1.3 KH3 — L'évidence « zéro faux positif » repose sur un corpus minuscule — [CONFIRMÉ]

- Banc de fidélité K2 : **quatre fichiers « mondes » + un témoin = 86
  lignes** (`src/test/resources/fidelite/` : `monde-compose.kt` 12 l.,
  `monde-coroutines.kt` 15 l., `monde-stdlib.kt` 28 l., `monde-views.kt`
  20 l., `temoin-positif.kt` 11 l.) [CONFIRMÉ, `wc -l`].
- Banc de complétion : **16 positions**, recall/top-1/MRR = 1,000/1,000/1,000
  (baseline signée `bancs/baselines/completion-kotlin.json`, naïve 0,375/0,486)
  [CONFIRMÉ, journal 06 §banc commun] — un banc **saturé** ne distingue plus
  amélioration et régression (le même constat a été fait côté Java :
  alerte AL1 de la mission jumelle).
- **Aucune mesure FP/FN n'existait sur du code Android Kotlin réel** avant
  cette mission. Le banc V0 (auto-corpus ~25 250 l. + corpus réel
  `bancs/corpus-android-kotlin/`, 6 projets Apache-2.0 épinglés, §5) comble
  ce trou ; les premières mesures sont au §4.3.

### 1.4 KH4 — Ni résolution de surcharges, ni inférence, ni récepteurs implicites de DSL — [CONFIRMÉ]

- **Surcharges** : la sélection se fait par preuve « nom + arité »
  (ADR 0106, `SPECIFICATION-RESOLUTION.md` règles A1–A31) ; il n'existe
  aucune désambiguïsation par types d'arguments — les appels polymorphes
  réels (surcharges de la stdlib, surcharges Android) ne sont tranchés que
  par le nom, et l'arité seule décide `kt.argumentCount`
  (`DiagnosticsTypage.kt:217`).
- **Inférence** : l'attente de lambda est PROJET/SAM seulement
  (`TypageBorne` `attenteLambdaAuPoint`) ; pas d'inférence de type de
  retour, pas d'inférence de variables locales au-delà de l'initialiseur.
- **Récepteurs implicites** : les scope functions (`apply`, `with`, `run`,
  `buildString`…), les DSL Compose/Gradle et les lambdas à récepteur
  génériques ne peuvent pas être typés (KH1) ; en mode script `.kts`, les
  noms nus sont **muets par construction**
  (`ResolutionReferences.kt:230` — jamais de faux positif sur les
  récepteurs implicites du DSL Gradle).
- La mission Java a mesuré l'équivalent : le même mur sépare son moteur du
  niveau Android Studio (rapport §1.3 de son audit, hypothèse H « couche
  IDE au-dessus du compilateur »).

### 1.5 KH5 — Le « mode profond K2 » n'existe pas — [CONFIRMÉ]

ADR 0112 (statut Proposé) : le banc de fidélité face à K2 en **oracle
sous-processus** a été livré, mais le « mode profond = extension à la
demande (drapeau off) » **n'a jamais été construit** — le registre des
restes le consigne comme chantier futur (« profondeur K2 différée »,
doc 11 §2.5) et les frustrations résiduelles sont documentées. Aucune
soupe de fidélité compilateur n'existe donc sur le chemin de frappe ni
hors de lui. L'ADR 0124 (cette mission) instruit les options du palier 3.

### 1.6 KH6 — La complétion ne connaît que 6 genres de contexte utiles (+ INCONNU = vide) — [CONFIRMÉ]

`api/CompletionKotlin.kt:86-117` — énumération **exacte** :
`MEMBRE` (:89), `NOM` (:92), `IMPORT` (:95), `ARGUMENTS_NOMMES` (:101,
fiche 043), `CONSTANTES_ENUM` (:107, contexte exclusif),
`ATTRIBUTS_ANNOTATION` (:113, exclusif), `INCONNU` (:116, « repli honnête :
liste vide »). Le pipeline classifie sur l'arbre seul
(`CompletionContextes.auPoint`, ordre IMPORT → MEMBRE → NOM), sonde les
contextuels **avant** le régime membre (« un caret dans la liste d'arguments
d'un appel membre est une position d'ARGUMENT », `CompletionPipeline.kt:93-108`),
collecte par régime puis arrange (chaîne de classement + gel des têtes +
plafond honnête, :129-149). Un document inconnu/fermé rend
`INCONNU + emptyList()` (`SessionLangageKotlinDocuments.kt:270`) ; un
contexte non prouvable retombe sur `NOM` (`api/CompletionKotlin.kt:82-84`).

**Absents** (vérifiés) : complétion smart par type attendu, chain
(extensions applicables), membres de lambda à récepteur, `when` exhaustif,
`override`/stubs, en-têtes de classe, gabarits `${}`, labels, KDoc,
destructuration, mots-clés contextuels positionnels, postfix/templates.
Snippets : `insertTextFormat = Snippet` seulement si le texte contient `$`
(`ServeurLangageKotlin.kt:453-455`). `textEdit`/`additionalTextEdits` :
ABSENTS — le protocole sert `insertText` + `filterText` et une `data
= {import: fqcn}` pour l'import auto côté hôte (:447-456). Plafonds :
`LIMITE_AFFICHAGE = 120`, `LIMITE_MEMBRE = 512` (`api/ClassementKotlin.kt:567,
576`) avec `totalDisponible` et `tronquee` honnêtes
(`api/CompletionKotlin.kt:128-140`).

### 1.7 KH7 — Actions de code : 3 familles, `codeAction` = quickfix seul — [CONFIRMÉ]

- `CorrectifsTexte.kt` (:28-44) : **accès non sûr** (3 correctifs : `?.`,
  `!!`, entourer `if (x != null)`, :144-183) et **conversion de littéral**
  (matrice FERMÉE `CONVERSIONS` :48-62, :235-241) — chaque famille
  re-dérive SES diagnostics avec les MÊMES prédicats que la passe.
- `MembresManquants.kt` (:24-42) : stubs des membres abstraits des
  supertypes PROJET + méthodes/getters-setters CLASSPATH appariés en
  propriétés, substitution des paramètres de type par la clause écrite.
- Imports : un plan PAR FQCN candidate (`ActionsDeclarees.kt:68-86`).
- Annonce : `codeActionKinds = [QuickFix]` SEUL (`CapacitesServeur.kt:110`) ;
  mapping `RAPIDE → QuickFix, sinon Source` (`ServeurLangageKotlin.kt:784`).
- `source.*` : la branche existe (`GenreActionKotlin.SOURCE`,
  `api/ActionsKotlin.kt:19`) mais **AUCUN plan SOURCE n'est jamais produit**
  (`rg 'GenreActionKotlin\.SOURCE' kotlin/src/main` → néant) ; pas de
  `organizeImports`. `refactor.*` : ABSENT (`rg 'refactor\.'` → néant).
- Aucune intention sans diagnostic (expression-body ↔ block-body, `?.let`,
  `if`↔`when`, gabarit de chaîne, extraction, inline, change signature,
  move, safe delete) — le catalogue complet visé est dans
  [`02-reference-android-studio.md`](02-reference-android-studio.md) §A4
  et la fiche KC-11.

### 1.8 KH8 — Code généré réel non lu ; synthétiques = atout — [CONFIRMÉ, nuancé]

L'hypothèse est confirmée sur les deux branches qu'elle contient :

- **Atout confirmé** : les synthétiques R/BuildConfig/bindings sont prouvés
  **avant build** par lecture du DOM `res/` + du manifeste
  (`ClassesSynthetiques.kt:140-157`, paquet du manifeste :47-48, familles R
  démontrées :184-249, borne 300 entrées/famille :393, BuildConfig « la
  classe SEULE » :251-260, bindings `inflate`/`bind` :275-336) et
  **s'effacent devant une classe générée réelle homonyme** (:36-41, :117-120 ;
  fusion en session `SessionLangageKotlinDocuments.kt:1105-1114`).
- **Déficit confirmé** : la marche du scan **exclut le dossier `build`**
  (`ScanProjetParesseux.kt:654`, `DOSSIER_EXCLU = "build"`) — les racines
  `build/generated` (KSP/KAPT : Room `*_Impl`, Hilt `Hilt_*`, Safe Args,
  Moshi) ne sont JAMAIS lues ; leurs noms conventionnels sont rendus `MUET`
  (« leurs sources sont invisibles PAR CONCEPTION, leur absence n'est
  JAMAIS prouvable », `ResolutionReferences.kt:217-223`, :1077-1092).
  Conséquence : ni complétion, ni navigation, ni typage sur le code généré
  après build (fiche 034 « parent muet », extension H2 « à décider » de
  l'ADR 0109). Les **plugins de compilateur** (Compose, Parcelize,
  serialization, allopen/noarg) ne sont pas modélisés : Compose n'est vu
  que par ses previews (imports prouvés), Parcelize/serialization/allopen
  ne changent rien aux dires du moteur (ni faux positif documenté, ni
  support) — à instruire en V-E (ADR 0127).

### 1.9 KH9 — Aucune intelligence Gradle Kotlin DSL — [CONFIRMÉ]

`rg -i gradle kotlin/src/main` → **une seule occurrence** hors vendoring :
un commentaire (« l'indicateur `viewBinding` du build.gradle n'est PAS
lisible depuis les sources », `ClassesSynthetiques.kt:54`). Il n'existe
AUCUN modèle du DSL : pas de lecture de `build.gradle.kts`, de
`settings.gradle.kts` ni de `libs.versions.toml`. Le régime « script
`.kts` » de la grammaire (`ParseurKotlin.kt:52-54`, :141-142 ; racine
`SCRIPT`, `parse/kt/KtFichier.kt:33`) rend les **noms nus muets**
(`ResolutionReferences.kt:230`) — le contrat de parité de l'ancien moteur
(fiche 027, 5 comportements : « aucun faux positif sur un
`build.gradle.kts` de template ») est tenu, mais c'est un contrat de
SILENCE, pas d'intelligence. L'ADR 0111 (option 1 : régime asymétrique,
accesseurs hors modèle, provisionnement = routage hôte) est resté au
statut de design — l'ADR 0130 propose sa réalisation par lecture disque.

### 1.10 KH10 — Compose et coroutines : previews seulement — [CONFIRMÉ]

Le module `preview/` (648 l. + SPI `api/PreviewsKotlin.kt` 156 l.) collecte
les fonctions **`@Preview @Composable` prouvées par les FQCN des IMPORTS**
(`FQCN_PREVIEW`/`FQCN_COMPOSABLE`, `PreviewsDocumentKotlin.kt:66-69` ;
« annotation non résolue par les imports = pas de preview » :60-62), tranche
les paramètres `@Preview` **dans la source** (`ParametrePreviewKotlin`,
`api/PreviewsKotlin.kt:116-127`), réduit le corps en **arbre d'appels
neutre** (`NoeudAppelKotlin`, :60-99) sous budgets explicites (500 nœuds,
profondeur 6, :72-75), troncature **signalée** (`api/PreviewsKotlin.kt:141-153`),
échecs d'inline typés (`GenreInlineKotlin`, :17-41) — jamais un faux rendu.

**Aucune intelligence propre** au-delà : pas de contexte `@Composable`
(appel composable hors contexte = muet), pas de complétion de `Modifier`,
pas de `remember`/états, pas de contrôle `suspend` (appel hors coroutine =
muet), pas de `Flow` (KH1 : le type est perdu). La référence fournit le
catalogue correspondant (`02-reference-android-studio.md` §B10/B11) ; la
fiche KC-07/KC-08 spécifient ce qui est décidable par palier.

### 1.11 KH11 — Interop Java↔Kotlin unilatérale et bornée — [CONFIRMÉ]

`resolve/LecteurDeclarationsJava.kt` (565 l.) lit des `.java` :
`package` (:55-73), types imbriqués récursivement (:130-182, :222-268),
`extends`/`implements` **de niveau classe uniquement** (:188-216), méthodes
(nom + type de retour simple mappé Kotlin, `int→Int`/`void→Unit`,
`nomKotlinDeTypeJava` :358-366), champs, **propriétés synthétiques des
accesseurs** (`getTitre→titre`, :324-331, :369-391). Limites vérifiées :
**l'arité Java n'est jamais contrôlée** (« la liste de paramètres est
sautée balancée », `:313` — un `MembreProjet` Java ne porte que
nom/genre/typeRetour, donc `kt.argumentCount` n'a jamais de témoin Java) ;
**génériques sautés** (:143-145) ; **toutes annotations sautées**
(:106-113, `sauterAnnotations` :549-563) → ni `@Nullable/@NonNull`, ni
`@Jvm*`, ni types plateformes `T!`. Le renommage **exclut explicitement
les `.java`** (« le moteur Kotlin ne renomme pas le Java »,
`OccurrencesProuvees.kt:144-147`) ; la navigation Kotlin→Java passe par les
déclarations lues, l'inverse n'existe pas (le moteur Java, lui, a livré un
scanner lexical des `.kt` par segment CIDX partagé — ADR 0022 de la
mission jumelle, décision D3 : « sans toucher au moteur Kotlin »).
L'interface publique d'interop (Q-K5) n'est toujours pas arbitrée — l'ADR
0129 propose le contrat.

### 1.12 KH12 — Budget dex serré pour l'ambition — [CONFIRMÉ]

E-K8 (alerte OUVERTE du dépôt, dernière mise à jour vague T) : pile chaude
**26 590 méthodes / 4 294 352 octets = 4,10 Mo** = **81 %** du plafond
auto-imposé de 32 768 méthodes (« moitié du mur 64K ») et **68 %** du
budget de **6 Mo** (arbitré par le propriétaire le 31/10/2026) ; pour
mémoire, avec le câble lsp4j ENTIER : 32 643 méthodes / 4,74 Mo = 99,6 %
— c'est le chiffre qui a motivé le choix « lsp4j porté par l'hôte ».
Leviers consignés au doc 11 §2.3 : Q-K2 (décodeur `@Metadata` maison),
Q-K1 (index partagé CIDX), réduction du câble lsp4j. Toute extension du
typage (palier 2), du catalogue de diagnostics, des actions ou des
refactorings consommera ce budget — c'est l'objet de l'ADR 0133
(nouvelle enveloppe proposée + modularisation en packs chargés à la
demande + garde CI inchangée par défaut).

### 1.13 KH13 — Le contrat mesure la parité avec l'ancien moteur, pas Android Studio — [CONFIRMÉ]

Le cahier (53 fiches / 469 comportements) est extrait des tests de
l'ancien moteur `codeide-lsp` @ `bc11253` ; l'ADR 0107 conserve
explicitement la « bornologie » ancienne « TELLE QUELLE en première vague
(parité), améliorable ensuite par décision écrite ». Les bancs comparent à
une **baseline signée issue du moteur lui-même** (fidélité : compte +
empreinte de ses propres 4 mondes ; complétion : 16 positions du corpus
`completion-kotlin v1`) ou à la chaîne « naïve » — jamais à K2/Android
Studio sur du code réel. Il manque une **vérité terrain « niveau Android
Studio »** : elle est livrée sous deux formes par cette mission — (a) les
fiches `[DÉRIVÉ DES SOURCES]` du dossier `05-fiches-comportement/` (à
intégrer au cahier par décision propriétaire), (b) le banc V0 + corpus
réel (§4.3/§5). La mise en garde du prompt §3 KH13 (« le cahier, y compris
ses limites ») est donc confirmée et traitée.

### 1.14 KH14 — Usages multi-fichiers par re-parse paresseux, jamais mesurés à l'échelle — [CONFIRMÉ]

`session/OccurrencesProuvees.kt` (1 298 l., plus gros fichier du module) :
objet **sans état statique**, tout vit dans un `Montage` construit **par
requête** (:155-320) ; le scan ne garde NI texte NI arbre (« budget
mémoire appareil », :73-77) ; chaque fichier candidat est **pré-filtré par
contenu** (le nom ou son alias potentiel doit apparaître dans le texte,
:280-310) puis **re-parsé à la demande** (:198-205) ; caches par requête
seulement (`arbresFermes`, `textesFermes`, `universParUri`,
`liaisonsParRegion`, :169-178 — jetés après la requête) ; les entrées
appelées sont **hors verrou** (:322-325). Renommage = `references` +
déclaration en tête (:419-426). **Aucune mesure de latence/mémoire sur un
grand projet réel n'existe** (E-K2 : pas d'appareil ; le corpus V0 la
rend possible — P7 + kit appareil `07-`). La question Q-K1 (index d'usages
persistant partagé) reste ouverte — ADR 0132.

### 1.15 KH15 — 7 capacités absentes ; `x-codeide.completionRanking` non annoncée — [CONFIRMÉ]

Matrice `protocole/CapacitesServeur.kt` (règle : « capacité testée =
annoncée ; sinon absente », :21-24). **Servies (19)** (:78-104) :
positionEncoding UTF-16, textDocumentSync Full, hover, definition,
typeDefinition, implementation, references, documentHighlight,
documentSymbol, workspaceSymbol, selectionRange, completion (`.`, `(`),
signatureHelp (`(`, `,`), codeAction (**QuickFix seul** :110),
documentFormatting, documentRangeFormatting, foldingRange, inlayHint,
rename **+ prepare** (:114-118). **Absentes honnêtes (7)** (:38-44) :
jetons sémantiques (semanticTokens — « dette D3 » : non fiables au
parseur seul, requête tolérante `ServeurLangageKotlin.kt:710-715`),
codeLens, hiérarchie d'appels, hiérarchie de types, documentColor,
onTypeFormatting, linkedEditingRange. `x-codeide.completionRanking` :
NON annoncée — `rg 'x-codeide|completionRanking' kotlin/src` → néant
(faute de frontière RPC équivalente au `FrontiereRpc` Java, journal 09
§7.1). Publication diagnostics : debounce 50 ms par URI, annule-et-remplace
(`PublicateurDiagnostics.kt:109, :44-63`). Cycle de vie 5 états
(`CycleVie.kt:27-45`) ; **époque copy-on-write** (« 0 réponse périmée »,
`EtatsDocuments.kt:13-27`, :151-163 ; `ServeurLangageKotlin.kt:122`).

### 1.16 KH16 — Aucune mesure appareil ; moteur livré mais PAS EN SERVICE — [CONFIRMÉ]

- **Mesures appareil** : aucune pour le moteur Kotlin (E-K2) ; la passe
  étages 1–6 au kit enrichi est documentée comme OPTIONNELLE (doc 11 §2.2,
  mécanique prête : `scripts/kit-bascule.sh` + exécution Termux). Le
  facteur ART « 3–5× estimé » du prompt est remplacé, pour calibrer les
  budgets, par le facteur **observé ≈ 4× du moteur Java** (moto g06,
  passe 1 du 30/10 : démarrage à froid → 1re complétion ≈ 7,6 s ;
  complétion de membres p95 ≈ 400–460 ms ≈ 4× le budget de 100 ms ;
  diagnostics 1 356 l. p95 ≈ 1,15 s) — ordres de grandeur Java, **pas des
  mesures Kotlin** [CONFIRMÉ côté Java, NON VÉRIFIÉ côté Kotlin]. Le
  gabarit `07-gabarit-mesure-appareil.md` (cette mission) fixe le protocole
  de la passe Kotlin.
- **Bascule** : le serveur LSP4J Kotlin est **livré et prouvé** (19/7,
  gardes I2/I3, harnais BoucleLocale, 523 tests) mais le client CodeIDE
  consomme toujours l'ancien LSP Kotlin de `codeide-lsp` ; le pack n'est
  pas publié (DÉBLOQUÉ depuis la levée de P-K2 le 31/10) ; la publication
  (`publish.yml`) ne couvre que les coordonnées Java (Q-K4) ; le
  branchement type I0–I6 n'a pas commencé (doc 11 §3, §2.5). Toute
  capacité ajoutée au moteur est donc **invisible pour l'utilisateur**
  tant que la bascule n'a pas eu lieu — c'est l'arbitrage n° 4 demandé au
  propriétaire (§10 du compte-rendu, ADR 0136).

---

## 2. Matrice fonctionnalité × moteur (couverture réelle)

Convention des niveaux (miroir de l'échelle de la mission jumelle Java,
`01-audit-moteur.md` §2) : **0** absent · **1** textuel/heuristique ·
**2** syntaxique fiable · **3** sémantique bornée (preuve ou silence) ·
**4** sémantique complète type K2 + contexte projet Android (niveau
Android Studio).

| Capacité | Composants internes | État | Niveau | Preuve / écart type Android Studio |
|---|---|---|---|---|
| Diagnostics « rouge » | DiagnosticsSemantiques/Typage/Imports, garde par déclaration | implémenté | **3** | 10 familles `kt.*` (KH2) ; la référence en rend des centaines par frappe (§A3 de la référence) |
| Diagnostics inspections IDE | — | **absent** | **0** | inspections/sévérités configurables côté référence |
| Complétion MEMBRE / NOM / IMPORT | CompletionPipeline, CompletionMembres/Noms/Imports, chaîne de classement | implémenté | **3** | classement livré (+62,5 pts top-1 vs naïve) mais pas de type attendu/récepteur (KH1/KH6) |
| Complétion contextuelle (arguments nommés, enum, annotations) | CompletionContextuelle | implémenté | **3** | 3 contextuels prouvés seulement |
| Complétion smart / chain / 2e appel | — | **absent** | **0** | fiches KC-01/KC-02 |
| Complétion lambdas à récepteur / DSL | — | **absent** (muet en `.kts`) | **0–1** | KH4 ; fiche KC-03 |
| `when` exhaustif, `override`/stubs | — | **absent** | **0** | fiches KC-04/KC-05 |
| Aide à la signature | SignatureDocuments | implémenté | **3** | candidates projet+classpath, paramètre actif, KDoc |
| Survol / KDoc | SurvolDocuments, DocumentationKotlin (R8) | implémenté | **3** | chaîne conservatrice document→ouverts→scan |
| Inlay hints | IndicesDocuments | implémenté | **2–3** | PARAMETRE/TYPE/LAMBDA ; pas de récepteurs implicites `this`/`it` en tête de lambda |
| definition / typeDefinition / implementation | NavigationDocuments, NavigationTypes | implémenté | **2–3** | par imports/FQCN prouvés, jamais par homonymie |
| references / documentHighlight | OccurrencesProuvees | implémenté | **3** | multi-fichiers par preuve de liaison, re-parse à la demande (KH14) |
| rename | OccurrencesProuvees.renommage | implémenté (borné) | **3** | `.kt`/`.kts` + imports ; inter-langage exclu (KH11) |
| symboles document/espace | SymbolesEspaceDocuments | implémenté | **2–3** | — |
| Pliage | PlisDocumentKotlin (R7) | implémenté | **2** | — |
| Sélection étendue | SelectionsDocuments | implémenté | **2** | — |
| Formatage | FormateurKotlin (R6) | implémenté (borné) | **2** | ré-indentation seule (ADR 0115, choix assumé) |
| Imports (organiser/optimiser) | CorrectifsTexte (inutilisé), imports de complétion | **partiel** | **2** | `source.organizeImports` jamais produit (KH7) ; fiche KC-10 |
| Actions de code | CorrectifsTexte, MembresManquants, ActionsDeclarees | partiel (3 familles) | **2** | quickfix seul ; intentions/refactorings absents (KH7) |
| semanticTokens | — | **absent** (tolérant) | **0** | dette D3 ; ADR 0136 (fondé sur le palier 2) |
| Hiérarchies appel/types, codeLens, documentColor, onTypeFormatting, linkedEditingRange | — | **absent** | **0** | KH15 |
| Previews Compose | PreviewsDocumentKotlin (R3) | implémenté | **2** | arbre d'appels NEUTRE (ADR 0116, budgets 500/6) |
| Intelligence Compose (contexte, Modifier, remember) | — | **absent** | **0** | KH10 ; fiches KC-07 |
| Intelligence coroutines/Flow (contexte suspend) | — | **absent** | **0** | KH10 ; fiche KC-08 |
| Gradle Kotlin DSL / catalogue de versions | — | **absent** (silence D3 en `.kts`) | **1** | KH9 ; ADR 0130, fiche KC-12 |
| Synthétiques Android avant build (R/binding/BuildConfig) | ClassesSynthetiques (R1/R2), AnalyseRessourcesAndroid | implémenté | **3** | **atout** : prouvé avant build, effacé devant le généré (KH8) |
| Code généré KSP/KAPT après build | — | **absent** (muet) | **0** | `build` exclu du scan (KH8) ; ADR 0127 |
| Interop Java→Kotlin (lecture) | LecteurDeclarationsJava | implémenté (borné) | **2** | arité/nullabilité/`@Jvm*` absents (KH11) |
| Interop Kotlin→Java (écriture croisée, rename) | — | **absent** | **0** | ADR 0129 (segment CIDX de la mission Java : « il écrit, nous lisons ») |
| Index classpath Kotlin | IndexAppelablesKotlin (`.kidx`), DecodeurMetadonnees (R4), LecteurClassBrut (R5) | implémenté | **3** | facades/top-level seulement ; `suspend`/`inline`/`reified` non décodés |
| Index d'usages persistant | — | **absent** | **0** | re-parse à la demande (KH14) ; Q-K1/ADR 0132 |
| Mesures appareil (ART) | — | **absent** | **0** | E-K2 ; gabarit `07-` |

**Lecture** : le moteur est **solide au niveau 3 borné** sur tout ce qui
relève du noyau (parse, portées, preuve d'absence, liaison, plafonds
honnêtes, époque copy-on-write) ; il est **niveau 0** sur quatre couches
concentriques : (1) le typage riche (génériques/nullabilité/inférence),
(2) la couche IDE au-dessus (smart/chain, inspections, intentions,
refactorings, semanticTokens), (3) la connaissance du projet Android réel
(généré KSP, Gradle KTS, Compose/coroutines), (4) l'interop
bidirectionnelle et l'échelle (index d'usages). La mission Java a dressé
le même diagnostic structurel pour son moteur — les deux moteurs
partagent le socle, les déficits se traitent en partie en commun (ADR
0127/0129/0135).

---

## 3. Audits par domaine

### 3.1 Syntaxe & session

**Architecture** : parseur officiel vendoré (grammaire KMP v2.3.0, 10 251
l. dans `src/main/vendor`, source set exempté d'explicitApi), façade
conservatrice `Kt*` (`parse/kt/`, 5 fichiers) au-dessus du harnais
(`ParseurKotlin.kt:41-43, :133-149`), mode script racine `SCRIPT`
(`KtFichier.kt:33`). **Reparse COMPLET** du document ouvert à chaque
mutation (`SessionLangageKotlinDocuments.kt:174-178, :1265-1267`) —
l'incrémental mtime n'existe que pour le SCAN des racines
(`ScanProjetParesseux.kt:4, :398, :542`). Le reparse complet est mesuré
bon marché (500 l. : 1,8 ms p50 JVM ; 3 000 l. : 8,6 ms p50 / 33,6 ms
p95, spike S2 [CONFIRMÉ, doc 02 §4]).

**Invariants** : tolérance aux erreurs (« l'éditeur appelle le parseur à
CHAQUE frappe… ne lève JAMAIS d'exception », `ParseurKotlin.kt:32-37`) ;
**garde par déclaration** (fiche 017) : seules les déclarations au
sous-arbre propre sont diagnostiquées (`sousArbrePropre`
`DiagnosticsSemantiques.kt:363-366`), l'en-tête d'une classe est séparé
de son corps (`entetePropre` :373-381), une zone package/import brisée
coupe la preuve pour tout le document (`directivesPortentErreur`
:396-408), et « un nom DÉCLARÉ n'importe où n'est JAMAIS signalé »
(`ResolutionReferences.kt:176-178`).

**Dette confirmée** : aucun parse incrémental (choix assumé ADR 0104) ;
édition d'un gros document = reparse intégral (mesuré acceptable jusqu'à
3 000 l., à surveiller sur documents géants appareil).

**Tests** : fiches 011/012/013/049 + parité dépôt entier (fiche 010,
422 fichiers, digeste signé) + tolérance par troncature (banc
`BancGrammaire.kt:130-145`, 100 troncatures hostiles, 0 lever).

### 3.2 Modèle de symboles & résolution

**Architecture** : modèle immuable conservateur
(`resolve/ModeleProjet.kt`, 988 l.) — `TypeProjet` (fqcn, genre
CLASSE/INTERFACE/ENUM/OBJET/**JAVA**, supertypes écrits, membres,
compagnon, paramètres de type, `absenceFiable` :110-111), `AppelableProjet`
(:248), `MembreProjet` (:158-176, `recepteurNomme`, `estAbstrait`),
`ParametreProjet` (:178-199, `typeFonction`, `aDefaut`, `estVararg`) ;
le Java nourrit le MÊME modèle (`GenreTypeProjet.JAVA` :31-32). La
résolution est **spécifiée avant d'être implémentée** :
`kotlin/SPECIFICATION-RESOLUTION.md`, règles A1–A31 versionnées (ADR
0106), sélection par preuve « nom + arité », verdict trichotomique
(`ResolutionReferences.kt:120-129`).

**Invariants** : imports (alias, jokers — un joker non énumérable coupe
la preuve `nomsNusProuvables` :137-138) ; univers ouverts muets
(héritage écrit, enum/data, noms générés KSP/Hilt/Room/DataBinding,
`ResolutionReferences.kt:217-223, :1085-1092`).

**Dette confirmée** : pas de résolution de surcharges par types, pas
d'extensions candidates par récepteur typé (KH4) ; les membres hérités
via le classpath dépendent de la clôture d'héritage bornée
(`ClotureHeritage`) et de la vue JVM (membres de `java.lang.String` d'un
`android.jar` posé — sinon jeu `null`, `VueJvm.kt:31-38`).

**Tests** : fiches 044/045/046/047/048 + journal 05 (7 vagues, 204
comportements diagnostics) ; le banc V0 P1 étend la preuve à l'échelle
(§4.3).

### 3.3 Typage & inférence

Couvert par KH1/KH4 (§1.1, §1.4) — le périmètre exact est
**versionné** : familles couvertes (littéraux et leurs familles, types
écrits, constructeurs résolus, retours écrits, nullabilité à récepteur
typé avec gardes, arité sur candidates prouvées, attente de lambda
PROJET/SAM), familles sous-rapportées (tout le reste), « frontière » =
rabattement des génériques sur le nom de base. Le banc de fidélité
mesure le sous-rapport (FN baselinés). **Ce que la mission ajoute** :
la mesure KH1-proxy (P6, taux de couverture typée des positions membres)
et le plan de conformité « palier 2 » (ADR 0123 : représentation à
arguments de type + nullabilité + types fonctionnels + variance,
conformité à la spécification du langage **vérifiée en différentiel
contre l'oracle K2 déjà présent en CI**).

### 3.4 Classpath & métadonnées

**Architecture** : lecteur de `.class` **octet par octet sans ASM**
(`LecteurClassBrut.kt`, 390 l. — identité, superclasse, interfaces,
annotation `kotlin.Metadata` reconstruite :308-330) ; décodeur
`kotlin-metadata-jvm` 2.2.10 (R4) : **porte Kotlin** = `.kotlin_module`
sinon le jar saute ENTIER (`DecodeurMetadonnees.kt:46-47, :125-127`) ;
déchiffre les FACADES (fonctions/propriétés top-level, typealias
publics, récepteur d'extension si classe simple :226-233) ; AAR :
`classes.jar` + `libs/*.jar` parcourus en mémoire (:94-123). Index
`.kidx` par segment sha256 (`IndexAppelablesKotlin.kt:25, :488`),
format binaire symétrique lecture/écriture (:356-363, :402-456),
**écriture atomique tmp+rename**, corruption → re-décodage (« l'index
est un CACHE, jamais une autorité », :369-399), cache mémoire borné
(256 segments, :295, :497).

**Dette confirmée** : `suspend`/`inline`/`reified` et les valeurs par
défaut ne sont PAS décodés (aucune lecture — `rg` infructueux) ; les
récepteurs d'extension GÉNÉRIQUES sont ignorés (« pas une clé stable »,
:228-230) — c'est une perte directe pour la complétion chain cible ;
les membres de classes ne sont décodés que via la vue JVM (noms JVM),
pas via `@Metadata` (pas d'arité Kotlin des méthodes de classes) —
d'où l'arité Java « jamais contrôlée » (KH11).

**Tests** : fiches 002/003/004/005/008/009/037 ; corpus de jars réels
(kotlinx-coroutines/serialization/datetime, android 4.1.1.4).

### 3.5 Diagnostics

Couvert par KH2 (§1.2). **Compléments d'audit** : sévérités —
`kt.syntax` et les 9 familles sémantiques sont des ERREURS
(`SeveriteKotlin.ERREUR`), pas de niveau AVERTISSEMENT au-delà des
imports inutilisés (HINT) ; `@Suppress` n'est pas géré (aucune
occurrence dans le catalogue — l'ancien moteur non plus ; l'inspection
de suppression est un chantier de la référence §A4/C-07 Java) ; le
journal « explain » existe (`ExplicationsNonResolus.kt`, why-unresolved
documentaire). La cohérence inter-fichiers repose sur l'époque + le
sondage du guichet (pas de graphe de dépendances de documents — la
mission Java a livré la « fraîcheur inter-fichiers » par empreintes
STAT mtime+taille, ADR 0025 : candidat de mutualisation, GK-038).

### 3.6 Complétion

Couvert par KH6 (§1.6). **Compléments** : le classement est la chaîne
Java (ADR 0012/0113 — correspondance graduée, peseurs, gel des têtes,
`liftShorter`, trim honnête) ; le banc commun est **saturé**
(1,000/1,000/1,000 sur 16 positions, KH3) — le V0 ajoute des positions
réelles (P6) et la frontière RPC de classement reste à arbitrer
(`x-codeide.completionRanking`, Q-…, ADR 0126/0136). Plafonds 120/512
avec `totalDisponible` honnête. L'auto-import passe par `data
{import: fqcn}` côté hôte (le client doit insérer l'import — point
d'intégration à vérifier côté CodeIDE, GK-…).

### 3.7 Navigation & usages

Couvert par KH14 (§1.14) + matrice §2. **Compléments** : le rappel
d'usages est prouvé égal à l'oracle interne (journal 07 : « rappel
usages = oracle »), le rename est **ferroviaire** (vide sans preuve,
jamais un renommage partiel silencieux, `ServeurLangageKotlin.kt:752-755`)
; l'édition est construite par l'hôte (`edit.changes = parUri`
:757-766). Échelle jamais mesurée (P7 V0 + gabarit appareil).

### 3.8 Édition assistée

Formateur borné (ré-indentation seule, R6, ADR 0115 — choix assumé « un
formateur complet exige des choix de style non prouvables ») ; pliage
(R7) ; KDoc (R8 : documentation au survol ET au scan) ; actions
déclaratives ModCommand-like (ADR 0115, 5 cas dorés WorkspaceEdit).
`source.*` jamais produit, `refactor.*` absent (KH7). L'organiseur
d'imports Kotlin (KC-10) est l'action `source` la moins chère et la
plus visible — candidate n° 1 de V-F.

### 3.9 Android / Compose / Gradle

Couvert par KH8/KH9/KH10 (§1.8–§1.10). **Synthèse de domaine** :
l'Android « avant build » est le point FORT du moteur (synthétiques
prouvés par disque, atout de différenciation affiché §C de la
référence) ; l'Android « après build » (généré KSP/KAPT) et l'Android
« outillage » (Gradle KTS, catalogue, Compose, coroutines) sont au
niveau 0–1. La mission Java a construit le socle mutuel : ADR 0020
(modèle de projet Android + référentiel de ressources + dérivateur
disque), 0021 (classes light), 0022 (scanner lexical Kotlin→Java +
segment CIDX partagé) — l'ADR 0127 les aligne au lieu de les dupliquer.

### 3.10 Interop Java↔Kotlin

Couvert par KH11 (§1.11). **Le sens manquant est Kotlin→Java** : le
moteur Java lit les `.kt` par scanner lexical (ADR 0022, décision D3 :
« sans toucher au moteur Kotlin ») et attend que le moteur Kotlin
**écrive** le segment CIDX partagé (« il écrit, nous lisons ») —
l'ADR 0129 propose ce contrat (réponse à Q-K5) et le séquençage avec
la mission Java (V-E mutuelle).

### 3.11 Concurrence, mémoire, budget

**Architecture** : verrou lecture-écriture de session
(`SessionLangageKotlinDocuments.kt:109`, écritures :163-190, univers
consulté HORS verrou :221-225), `verrouScan`, `verrouCalcul`
(synthétiques), `synchronized` de la vue JVM ; **annulation**
coopérative par génération (`PasseurIndexationKotlin.kt:86-108`,
`ObservationPassage`) ; **bornes** : cache segments 256, scan 2 000
fichiers, 300 entrées/famille, LIMITE_INDEX 24 ; gels à session ;
l'époque copy-on-write côté protocole. **`onTrimMemory` : ABSENT**
(`rg -i 'trimemory|onTrim|lowmemory'` → néant) — la mémoire repose sur
les bornes + l'abandon à `close()` ; l'ADR 0117 (caches bornés en
octets, observateurs, budgets) n'est réalisé que partiellement →
ADR 0134. Budget dex : KH12 (§1.12).

### 3.12 Protocole & intégration

Couvert par KH15/KH16 (§1.15, §1.16). **Compléments** : lsp4j 0.21.2 en
runtime SEUL (« les paquets moteur restent LIBRES de lsp4j », câble
porté par l'hôte — le client impose 0.22.0, moteur binaire dessous,
journal 09) ; le descripteur de pack D6 est généré
(`version.properties`, id `jo-codeide-lsp-kotlin`) ; la publication
n'existe pas encore (Q-K4, `publish.yml` = coordonnées Java
seulement) ; la CI bloque sur 10 jobs dont `kotlin-parite` (523
tests), `kotlin-art` (scan ART 5 × AUCUNE + d8 ≤ 32 768/6 Mo),
`kotlin-banc`, `kotlin-banc-qualite`.

---

## 4. Mesures exécutées / non exécutées

### 4.1 Statiques [CONFIRMÉ] (extraits — détail au §1)

Volumétrie (`wc -l` sur `*.kt`) : main **25 250** l. (+ vendor 10 251),
test **17 430** l., bench **986** l. ; répartition main : session 10 126
(28 f.), resolve 5 647 (8 f.), api 2 504 (15 f.), protocole 1 617 (6 f.),
index 1 096 (3 f.), preview 648+156, parse 1 311, classpath 683,
synthetics 784 (3 f.), format/plis/documentation 834. Fichiers > 800 l. :
`OccurrencesProuvees` 1 298, `SessionLangageKotlinDocuments` 1 269,
`ResolutionReferences` 1 144, `VueJvm` 1 142, `DiagnosticsSemantiques`
1 091, `ModeleProjet` 988, `TypageBorne` 849, `ServeurLangageKotlin`
843. Catalogues : 10 codes `kt.*`, 6+1 genres de complétion, 3 familles
d'actions, 19/7 capacités.

### 4.2 Dynamiques locales [CONFIRMÉ, JVM bac à sable 2 cœurs]

- `./gradlew :kotlin:compileTestKotlin` : **BUILD SUCCESSFUL en 2 min 46 s**
  (JDK 21 / toolchain 17 auto-provisionné), puis 20 s à chaud — le
  dépôt se build intégralement dans l'environnement de la mission.
- Suite `:kotlin:test` complète : exécutée dans l'environnement de la
  mission **après livraison du banc V0** — voir §4.3 et le compte-rendu
  final pour le verdict chiffré.

### 4.3 Banc V0 (livré par cette mission) — mesures P1

Le banc de cohérence P1–P9 (`kotlin/src/test/kotlin/jo/codeide/lsp/
kotlin/coherence/`) étend la preuve du dépôt à l'échelle : **P1
fidélité différentielle** moteur vs oracle K2 sur l'auto-corpus (les
~99 fichiers `.kt` de `kotlin/src/main/kotlin` + grammaire vendorée —
du vrai Kotlin compilable, ~35 500 lignes), avec l'oracle en
sous-processus multi-fichiers en UNE invocation (leçon mesurée en bac
à sable : des passes séparées fabriquent 462 « unresolved reference »
croisés fantômes sur 40 fichiers — le module de compilation est
l'univers de la mesure).

**Résultats [CONFIRMÉ, exécutés le 31/10/2026, JVM bac à sable 2 cœurs,
commande `./gradlew :kotlin:test --tests 'jo.codeide.lsp.kotlin.coherence.P1FideliteDifferentielleTest'`]**
— 3 tests, 2 gates corpus ignorées avec raison affichée, 0 échec :

| Mesure P1 auto-corpus | Valeur |
|---|---|
| fichiers / lignes | **99 / 34 934** |
| diagnostics moteur (ERREUR) | **981** — `kt.unresolved` 884, `kt.argumentCount` 38, `kt.valReassign` 36, `kt.unsafeNullable` 23 |
| erreurs oracle K2 (même classpath) | **1** |
| **faux positifs (FP)** | **980** |
| **FP / 1 000 lignes** | **28,05** |
| faux négatifs (FN) | **0** (le moteur couvre l'unique erreur oracle) |
| temps | montage 5 372 ms · moteur 2 983 ms · oracle 27 931 ms · total 36 287 ms |

**Lecture (la découverte n° 1 de l'audit)** : sur du vrai Kotlin compilable
en volume (34 934 l.), la revendication « zéro faux positif » du dépôt est
**INFIRMÉE à l'échelle** — elle tenait sur le corpus de 86 lignes (KH3).
Causes racine des 980 FP, par famille (détail imprimé par le banc) :

1. **`it` implicite : 603 FP** — la garde `it` (vague T) ne silence que
   lorsque l'attente de la lambda est *démontrable* (PROJET/SAM) ; sur code
   réel, les lambes alimentent des extensions génériques stdlib
   (`joinToString`, `forEach`…) que le décodeur ignore par construction
   (`DecodeurMetadonnees.kt:228-230` « pas une clé stable ») → l'attente
   n'est pas démontrable mais le nom `it` est néanmoins condamné.
2. **Types intrinsèques et imports implicites `kotlin.*`/`kotlin.collections.*`/
   `kotlin.text.*` non modélisés : ~100 FP** — `IntArray` 71, `ByteArray` 7,
   `ArrayDeque` 7, `LazyThreadSafetyMode` 7, `Charsets` 6, `Comparator`,
   `AutoCloseable`, extensions `startsWith`/`endsWith`… : l'oracle résout
   ces noms par les importations implicites du langage, le moteur exige un
   import explicite ou un index — l'univers du moteur n'est PAS l'univers
   du langage.
3. **`this` nu : 48 FP** — récepteurs nus (classes internes, lambdas à
   récepteur, expressions qualifiées `this@…` tronquées au nom nu).
4. **Segments racines de paquets qualifiés : ~60 FP** — `jo` 36, `org` 19,
   `source` 5 : le premier segment d'usages complètement qualifiés est
   signalé quand la racine de paquet n'est pas dans l'univers.
5. **Paramètres de type nus : 11 FP** (`T`) — conséquence directe de KH1.
6. **`kt.valReassign` : 36 FP** — motif `cible[clé] = valeur` (affectation
   INDICÉE mal attribuée à la valeur de droite, ex.
   `api/ClassementKotlin.kt:260` : `val poids = …` puis
   `memoires[dimension] = poids` → « Réaffectation impossible : poids »).
7. **`kt.argumentCount` 38 / `kt.unsafeNullable` 23 FP** — arité et
   nullabilité émises sur des récepteurs/candidates dont le type a été
   perdu (KH1) ou sur des valeurs par défaut/varargs non décodées
   (`DecodeurMetadonnees` : défauts non lus).

Cette mesure devient la **baseline d'écart P0** de la vague V-A (cible :
FP < 0,5/1 000 lignes, miroir du seuil D5 de la mission Java), reprise dans
l'ADR 0135 et l'alerte E-K10. Caveat d'honnêteté : l'auto-corpus est un
corpus **JVM** (le module lui-même) avec le classpath de test posé — les
causes racine ci-dessus sont structurelles (langage), pas des artefacts de
montage ; la confirmation sur code Android réel viendra des manifestes du
corpus externe (P1(b)/P2).

Les gates P1(b) (corpus externe avec manifestes) et P1(c) (divergence
D3 sans classpath) s'exécutent avec `CORPUS_KOTLIN=…` — protocole au
LISEZMOI du corpus (§5). **Ces chiffres n'existaient nulle part avant
cette mission** (KH3).

### 4.3 bis — Banc V0 : mesures P2–P9 (exécutées le 31/10/2026, mêmes conditions)

Commande : `./gradlew :kotlin:test --tests 'jo.codeide.lsp.kotlin.coherence.*'`
— **BUILD SUCCESSFUL en 5 min 02 s, 11 tests verts, 0 échec** (gates
corpus ignorées avec raison affichée) [CONFIRMÉ] :

| Propriété | Résultat mesuré |
|---|---|
| P2 zéro FP corpus externe | gate ignorée (manifestes absents) — protocole LISEZMOI §2 |
| **P3 rappel par mutations** | **taux de silence 0,0 % sur les 3 familles** (import supprimé 8/8, nom inconnu 14/14, type erroné 14/14 — 36 mutants détectés à la bonne ligne) |
| **P4 équivalence des chemins** | **0 différence** (froid/chaud/index disque/ouvert-fermé ; blancs/commentaires neutres, +3 lignes) |
| **P5 cycle quick-fix** | assertion TENUE (la garde `if (t != null)` fait disparaître `kt.unsafeNullable` sans nouvelle erreur) ; **écart mesuré** : les plans « Importer … » proposés à une position de diagnostic MEMBRE insèrent à une position qui casse la syntaxe (18 `kt.syntax` après application) et proposent des candidates impertinentes — à instruire en V-A (GK-022 adjacent) |
| **P6 complétion résoluble / couverture typée (KH1-proxy)** | classification **100 %** (membre/nom/import) ; **couverture typée membre 50 % (1/2)** : `etiquettes.` (type ÉCRIT `ArrayList<String>`) → 10 propositions, mais `nombres.` (initialiseur GÉNÉRIQUE `listOf`) → **0** — KH1 confirmé par la mesure ; résoluble 50 % (1/2 — l'insertion naïve au caret du document sonde est un artefact de protocole documenté, à affiner en V-A' : insérer en début d'identifiant) |
| **P7 rename sûr** | renommage multi-fichiers **3 emplacements / 2 URI** (contrat fiche 048 tenu) ; après application : **erreurs moteur 0, erreurs oracle 0** |
| **P8 cohérence inter-fichiers** | B sain (0) → suppression de l'API de A → **recalculé SIGNALABLE** (l'usage devient `kt.unresolved`) — la fraîcheur inter-fichiers tient en univers fermé |
| **P9 résistance à la frappe** | 40 troncatures hostiles (5 mondes) : **0 exception**, positions membres 34/34 classées (**0 % INCONNU**) |

### 4.4 Non exécutées — [NON VÉRIFIÉ] avec protocole

- **Toute mesure appareil ART** (latences perçues, GC soft-références,
  démarrage à froid, thermique, dex en charge) — raison : pas d'appareil
  (E-K2) ; protocole : `07-gabarit-mesure-appareil.md` (kit
  `scripts/kit-bascule.sh` existant + passe étages 1–6 documentée au
  doc 11 §2.2).
- **Captures Android Studio** (comportements de la référence) — raison :
  Android Studio non exécutable ici (AL6 miroir Java) ; protocole :
  `06-protocole-captures-android-studio.md` (lots A–D Java déjà
  spécifiés + **lot E Kotlin** livré).
- **P1(b)/P2 sur corpus réel avec classpath compilé** — raison : la
  préparation des manifestes exige un SDK Android/Gradle par projet ;
  protocole : `bancs/corpus-android-kotlin/LISEZMOI.md` §2 (la gate
  s'ignore avec raison, jamais un vert silencieux).

---

## 5. Corpus réel (livré — `bancs/corpus-android-kotlin/`)

Corpus **versionné par script** (`scripts/telecharger-corpus-kotlin.sh`,
miroir du corpus Java), jamais de binaire committé (`.gitignore`),
**6 projets Apache-2.0 épinglés par `git ls-remote` le 31/10/2026**
[CONFIRMÉ] — rôles couvrant les exigences du prompt §4.4 :

| Clé | Dépôt | Rôle | Commit épinglé | Volumétrie .kt |
|---|---|---|---|---|
| `nowinandroid` | android/nowinandroid | Compose M3, Hilt/Room KSP, Retrofit + kotlinx-serialization, WorkManager, Flow, 40 `.kts` + catalogue | `2982e63cb691ad49cd009aab43dc206fee6699c2` | 310 f. / 29 336 l. |
| `sunflower` | android/sunflower | Views + ViewBinding + Compose + Room | `2a357a31551bb53f3fe80382a9ce6d30bcc8b960` | 66 f. / 4 759 l. |
| `architecture` | android/architecture-samples (`todoapp`) | Views + ViewBinding MVVM | `ee66e1526b84c026615df032c705842b7d2a521f` | 55 f. / 6 049 l. |
| `compose` | android/compose-samples (`Jetcaster`, `Jetsnack`) | Compose pur + Flow/coroutines | `25631e8f544a9a1e3f01e296eaf1b4c28428196c` | (multi-modules) |
| `viewswidgets` | android/views-widgets-samples | **mixte Java (94 f./13 251 l.) + Kotlin (104 f./7 739 l.)** — interop réel | `2238cc873501f9cda63605051de11832bb736a8a` | 104 f. / 7 739 l. |
| `platform` | android/platform-samples | Kotlin moderne, APIs plateforme | `177c3b92c387894648d0a2d42d238e265b05bbc3` | 336 f. / 47 131 l. |

Licences vérifiées par l'API GitHub (spdx Apache-2.0) sauf mention ;
volumétries mesurées par clonage superficiel au commit épinglé
[CONFIRMÉ, `find`/`wc -l`]. Éliminés (raisons) : `camera-samples`
(0 fichier Java mesuré — pas mixte — et licence non détectée par l'API
au 31/10), `gh4a` (0 fichier `.kt` au commit épinglé), 
`thunderbird-android` (mixte réel mais **MPL-2.0** — hors politique
« licence permissive » du corpus ; **candidat secours si le
propriétaire arbitre MPL-2.0 acceptable**). Les projets sont des
**données de corpus** (cibles de mesure) : inscrits au registre de
provenance avec leur rôle (`docs/PROVENANCE-REPRISE-KOTLIN.md`
§Corpus), hors liste blanche des références de conception (§1.3).

---

## 6. Conclusion d'audit

1. **Le noyau est sain et prouvé** : parseur officiel, modèle conservateur,
   preuve ou silence, plafonds honnêtes, époque copy-on-write, 523 tests
   verts, CI 10/10 — le programme d'origine est clos sur preuve mécanique
   et rien n'est à reprendre dans le socle.
2. **Le déficit est structurel et concentrique** (KH1–KH16 confirmés) :
   typage à 2 cas → diagnostics 10 familles → complétion 6 genres →
   Android muet après build → interop unilatérale → protocole 7 absentes.
   Aucun de ces déficits ne se corrige par « plus de la même chose » :
   ils sont la conséquence DIRECTE de la décision ADR 0103/0107
   (bornologie conservée), assumée pour la parité avec l'ancien moteur.
3. **L'évidence de qualité était proportionnée au contrat de l'époque**
   (parité ancien moteur) mais **insuffisante pour le contrat nouveau**
   (niveau Android Studio) — le banc V0 + corpus réel + fiches de la
   référence comblent ce trou (KH13).
4. **Deux atouts de différenciation confirmés** : les synthétiques avant
   build (lecteur disque) et l'empreinte (~4,10 Mo de dex, aucune
   dépendance réseau au runtime) — à préserver pendant toute montée en
   intelligence (l'ADR 0133 donne l'enveloppe et les garde-fous).
5. **Le levier n° 1 est le palier 2 (typage étendu)** : il débloque à lui
   seul smart/chain (KC-01/02/03), une partie des diagnostics (§A3 de la
   référence), les inlay hints riches, semanticTokens fiables — c'est la
   recommandation centrale de la synthèse (`00-synthese.md`) et la
   réouverture demandée de l'ADR 0107 option 2.
6. **La bascule CodeIDE est le chemin critique de valeur** (KH16) : sans
   elle, aucune capacité n'est visible ; elle est courte (miroir Java
   I1 : 3–6 j-h) et réversible (drapeau pack) — l'arbitrage n° 4 du
   compte-rendu propose de la séquencer AVANT l'extension de capacités.
