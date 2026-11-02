# 06 — Protocole de captures Android Studio (moteur Kotlin — lot E)

> Ce que le **propriétaire** doit capturer/confirmer sur Android Studio pour
> le MOTEUR KOTLIN (l'environnement d'étude ne peut pas l'exécuter — R5 :
> aucune mesure simulée). Les captures servent de vérité terrain qualitative
> aux documents de `niveau-android-studio-kotlin/` quand la documentation ne
> suffit pas (points marqués NV). Miroir du protocole de la mission Java.
>
> **Matériel** : Android Studio courant, un projet Android **Kotlin** de
> test — modèle « Empty Activity » (Compose et coroutines inclus), copie
> locale jamais committée — le kit
> [`captures/kit/KitCapturesKotlin.kt.txt`](captures/kit/KitCapturesKotlin.kt.txt)
> posé dedans, enregistrement d'écran ou captures d'écran + texte copié.

## 1. Déjà spécifié côté Java — ne pas rejouer

Le protocole des lots A-D existe et couvre des positions **JAVA** :
**branche etude/moteur-java, docs/etudes/niveau-android-studio/
06-protocole-captures-android-studio.md** (lisible dans le worktree
`/home/z/my-project/code-lsp-java`). En résumé :

- **Lot A — complétion** : A1 smart (`String s = |`), A2 argument typé +
  surcharges, A3/A4 chaîne de getters et invocations répétées, A5
  `@IntDef`, A6 `R.string.`, A7 littéral de chaîne, A8 mots-clés de tête
  de déclaration, A9 imports top-level, A10 noms de variables ;
- **Lot B — diagnostics/inspections** : equals sur String, nullité
  JetBrains, NewApi avec/sans garde, `@CallSuper`, projet sans build,
  re-highlight inter-fichiers ;
- **Lot C — performance perçue** : indexation des corpus, frappe 30 s,
  renommage, dumb mode ;
- **Lot D — points NV** : hover `R.string`, `BuildConfig`, find usages
  lecture/écriture, `@Dimension`, profil d'inspections.

Rien de tout cela n'est à rejouer ici. Le **lot E** ci-dessous ne porte que
les positions propres au langage Kotlin ; les mécanismes hôte communs sont
récapitulés au §3 (« à ne PAS redemander »).

## 2. Lot E — positions Kotlin

Protocole par capture (repris tel quel de la mission Java, §1) : (a) fichier
+ position exacte (ligne:colonne), (b) geste (caret, frappe, Ctrl+Espace,
Ctrl+Shift+Espace, Alt+Enter…), (c) **liste attendue top-10 copiée en
TEXTE** — l'ordre compte, l'image ne suffit jamais seule, (d) temps perçu
(immédiat / visible / lent ; les millisecondes sont réservées au gabarit
`07-gabarit-mesure-appareil.md`). Support : le kit
[`captures/kit/KitCapturesKotlin.kt.txt`](captures/kit/KitCapturesKotlin.kt.txt) —
une fonction par scénario, marqueur `¦` = position du caret, commentaire
`// CAPTURE Ex` = geste attendu.

| # | Position (dans le kit) | Geste | Ce qu'on veut apprendre |
|---|---|---|---|
| E1 | `val s: String = \|` (locales `libelle: String`, `compte: Int`, `villes: List<String>` en portée) | Ctrl+Shift+Espace puis Ctrl+Espace (2 listes) | Filtrage par type attendu : position de `libelle` (compatible), présence de `compte`/`villes` (non compatibles), littéraux, mots-clés |
| E2 | `listOf(1, 2).fil¦` | frappe `fil` (liste spontanée) puis Ctrl+Espace | Chaîne sur collection : ordre des `filter*`/`find`/`first`, la lambda `it` est-elle insérée à l'insertion ? |
| E3a | `buildString { appe¦` | frappe `appe` | Récepteur implicite : membres de `StringBuilder` sans préfixe (`append`, `appendLine`…), extensions proposées ? |
| E3b | `with(context) { \| }` | Ctrl+Espace | Idem sur `Context` : membres sans préfixe, extensions à récepteur `Context` ? |
| E4a | `when (categorie) { \| }` (enum, when VIDE) | Ctrl+Espace | Sujets de branche proposés (constantes de `Categorie`), place de « Add remaining branches » |
| E4b | `when (etat) { … }` (sealed, branche manquante) | Alt+Enter sur le soulignement | Message du diagnostic d'exhaustivité, intitulé et effet du correctif « Add remaining branches » |
| E5 | corps de `class EcranArticles : Ecran { o¦` | frappe `o` (ou `ov`) | Stubs d'override : membres proposés, priorité du mot-clé `override`, forme du stub inséré |
| E6 | `e6Inlays` / `totalLigne` ouvertes | aucun — lire l'éditeur | **Inlay hints** : types inférés, noms de paramètres aux appels, récepteurs/`it` — capture écran OBLIGATOIRE + transcription texte de chaque hint |
| E7 | `depot.chargerTous()` appelé hors coroutine | diagnostic puis Alt+Enter | Message EXACT (appel suspend hors coroutine) + liste des correctifs (`runBlocking`, rendre `suspend`…) |
| E8a | `TitreEcran(…)` appelé hors composable | diagnostic puis Alt+Enter | Message EXACT (composable hors contexte) + correctifs proposés |
| E8b | `modificateur.p¦` dans un composable | frappe `p` | Complétion `Modifier` : membres + extensions (`padding`…), import automatique proposé ? |
| E9a | `pseudo.length` (`var pseudo: String?`, SANS garde) | diagnostic puis Alt+Enter | Message EXACT (appel non sûr sur nullable) + correctifs (`?.`, `!!`, garde `if`) |
| E9b | même accès APRÈS `if (pseudo != null)` | constater | Smart cast : l'erreur disparaît-elle ? un hint explique-t-il le cast ? |
| E9c | en aval de `pseudo ?: "invité"` | Ctrl+Espace | Type redevenu non nullable : que propose la complétion sur le résultat |
| E10a | KDoc : ligne `* @p¦` | frappe `@p` | Balises KDoc proposées (`@param`, `@property`, `@return`…) et leur ordre |
| E10b | KDoc : `* @param \|` | Ctrl+Espace | Noms des paramètres de la fonction proposés (ordre exact) |

**Ce que chaque position alimente** (valeur pour le moteur, lecture
qualitative) : E1/E2 la chaîne de classement de la complétion contextuelle ;
E3 les récepteurs implicites de DSL — la famille dont la leçon « it » du
journal d'exécution 10 §8.5 dit qu'elle doit vivre de la même attente dans
toutes les passes (typage, diagnostics, complétion) ; E4 le contrat
d'exhaustivité `when` ; E5 les stubs de membres manquants ; E6 les inlay
hints (types, paramètres, récepteurs) ; E7/E9 les diagnostics de contexte
(suspend, nullabilité/smart cast) ; E8 le périmètre Compose (appels
composables, `Modifier`) ; E10 la documentation KDoc. Une position non
capturée reste une attente non tranchée — le gabarit
[`captures/RESULTATS-LOT-E.md`](captures/RESULTATS-LOT-E.md) garde la case
vide avec sa raison (R5).

## 3. Questions à ne PAS redemander (couvertes côté Java)

Les mécanismes ci-dessous sont des comportements de l'HÔTE Android Studio,
indépendants du langage des sources — les lots A-D Java y répondent déjà
(fichier du §1) :

- 2e puis 3e invocation de Ctrl+Espace à la même position (membres
  inaccessibles, import statique, bascule projet entier) — A4 ;
- complétion des imports top-level sur préfixes partiels — A9 ;
- indexation des corpus, régularité de frappe perçue, renommage — C1/C2/C3 ;
- dégradation en dumb mode pendant l'indexation — C4 ;
- mécanismes Android communs : `R.string.` (A6), `BuildConfig` (D2), NewApi
  sous garde (B4), zéro rouge avant build (B6).

Si le propriétaire n'a pas encore joué ces lots côté Java : les jouer UNE
fois sur le projet de test (la langue des sources n'importe pas pour ces
questions-là) et les consigner dans les gabarits de la mission Java. En cas
de doute sur un comportement KOTLIN spécifique de ces mécanismes (ex. ordre
des propositions `R.string.` dans un fichier `.kt`), le noter en remarque
libre du lot E — sans rouvrir un lot entier.

## 4. Règles d'hygiène

1. **Le texte prime sur l'image** : toute liste est transcrite en texte
   ordonné ; l'image ne sert jamais seule (E6 est la seule position à
   EXIGER une capture d'écran, toujours accompagnée de sa transcription).
2. **Version notée** : la version d'Android Studio courante au 31/10/2026
   est **Rabbit 2 / 2026.2.2**, et **K2 est le mode par défaut depuis
   Narwhal Feature Drop 2025.1.2** [CONFIRMÉ — developer.android.com,
   consulté le 31/10/2026]. Consigner néanmoins la version EXACTE
   (Help > About) et le mode K2 effectif du projet dans
   `captures/RESULTATS-LOT-E.md` : le comportement de complétion et les
   inlay hints dépendent du mode.
3. **Indexation terminée** avant toute capture du lot E (aucune position ne
   vise le dumb mode — §3).
4. **Réglages par défaut** : aucune modification des paramètres de
   complétion / inlay hints / inspections ; noter tout écart inévitable.
5. **Clean-room** : les captures sont des données de spécification — on
   décrit des comportements (ordres de listes, messages, sévérités), on ne
   copie aucun fichier source d'IntelliJ/Android Studio ; les extraits du
   kit sont nôtres (écrits pour la mission, aucun code copié).
6. **Temps perçu** en trois classes (immédiat / visible / lent) — aucune
   estimation en ms ici.

## 5. Ce que le propriétaire doit en faire

1. Poser le kit dans le projet de test (mode d'emploi complet en tête du
   fichier), laisser Gradle resynchroniser, attendre la fin de l'indexation.
2. Jouer E1→E10 dans l'ordre (E1/E2 d'abord — la complétion smart et la
   chaîne alimentent les priorités les plus chaudes), et remplir
   [`captures/RESULTATS-LOT-E.md`](captures/RESULTATS-LOT-E.md) — une
   section par position, gabarit vierge fourni.
3. Ranger les images dans `captures/images/` (à créer), nommées `E1-1.png`,
   `E1-2.png`…
4. Restitution : commit direct sur `etude/moteur-kotlin` (après
   `scripts/verifier-push.sh`) ou envoi des fichiers remplis + images à
   l'agent, qui consigne, committe et pousse — miroir de la mission Java.
