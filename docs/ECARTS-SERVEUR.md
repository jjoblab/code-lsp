# Écarts du serveur — inventaire des reportés et non étudiés

> **Demande du propriétaire (31/10/2026, retour appareil sur CodeIDE
> 0.204.5)** : « Listé les écarts du serveur qui avaient été mis pour plus
> tard ou pas encore étudié. Le problème le plus urgent c'est la
> complétion. » Ce document est l'inventaire demandé — établi à l'état
> **v1.1.5**, avant l'exécution de la vague corrective v1.1.6 (§1), puis
> mis à jour des suites de celle-ci.
>
> **Lecture** : chaque écart porte son **RESPONSABLE** (moteur = ce dépôt ;
> hôte = `codeide-lsp` ; client = `cel-lsp`/`code-editor` ; décision =
> arbitrage propriétaire) — le moteur neuf est éprouvé par le banc projet
> Android réel (`BancProjetAndroidReelTest`, 22 tests, vraies
> bibliothèques) : les écarts appareil restants se lisent presque tous
> HORS moteur (§3–§4), c'est la conclusion du PV §13.5.

## 1. Corrigés dans la vague v1.1.6 (les « plus urgents »)

| # | Écart (constaté) | Responsable | Correctif v1.1.6 | Preuve |
|---|---|---|---|---|
| C1 | **Mots-clés de top level muets** (consigné §13.4) : `i\|`, `im\|`, `import\|` AVANT l'espace rendaient **0 item** là où Android Studio propose `import`/`interface` | moteur | genre `TOP_NIVEAU` + repli de la cascade (profondeur d'accolades zéro, gardes tête de déclaration/littéraux/point) + collecte des mots-clés légaux hors corps de type (`extends`/`implements`/`permits` compris : la tête de déclaration vit à profondeur zéro) | `RetoursAppareilV116Test` (7 tests) + banc S19 |
| C2 | **Aide de signature muette sur virgule traînante** : `repeter("monip", ` (méthode DU PROJET, second argument en cours) rendait **null** — la fermeture de la liste d'arguments était concaténée APRÈS les accolades englobantes (`f(a, 0\n})` : syntaxe cassée, appel perdu) | moteur | `AideSignatures.refermer` : les listes ouvertes avant le caret se referment juste AVANT l'accolade du bloc englobant (`f(a, 0);` puis le `}` du corps) ; sans telle accolade (caret en fin de texte), l'ancienne queue complète tient | `RetoursAppareilV116Test` (3 tests) + banc S20 (noms de paramètres `nom`/`fois` lus dans la SOURCE du projet) |
| C3 | **Latence de frappe linéaire à la taille du FICHIER** : chaque frappe résolvait l'UNITÉ ENTIÈRE marquée (une classe réelle de plusieurs milliers de caractères paie la résolution intégrale à chaque touche) | moteur | réduction `SourceFocuser` appliquée à la résolution marquée (corps des méthodes étrangères effacés, seuil 2 000 caractères : identité pour les petits fichiers, signatures intactes pour l'héritage) — même medicine que le niveau 3 de `resoudrePourOffset` depuis N4 | banc S17 (chaud p95 < 1 000 ms asserté) ; banc qualité p95 ≈ 37 ms inchangé |

## 2. Moteur (`code-lsp`) — écarts OUVERTS, non étudiés

| # | Écart | Statut / effort estimé | Note |
|---|---|---|---|
| M1 | **Mot-clé `extends` pendant sa frappe en tête de déclaration** : `class Principal e\|` est capturé par la fiche 007 comme sélecteur de méthode (CORPS_CLASSE : stubs d'override + types) — Android Studio y propose aussi le mot-clé `extends` LUI-MÊME | ouvert, petit | verrouillé par contrat fiche 007 ; le changer demande un arbitrage cahier (fusion mots-clés + stubs en tête de déclaration) |
| M2 | **`semanticTokens` non annoncé** (règle matrice ADR 0015) : requête tolérante vide, le client garde sa coloration de repli | assumé (USAGE §13) | à réouvrir si la coloration sémantique devient un sujet utilisateur |
| M3 | **`source.organizeImports` pas au fil LSP** : l'API moteur existe (USAGE §7.17), l'action de code n'est pas exposée | ouvert, petit | l'action existe côté vendord (fiche 022) — portage à étudier |
| M4 | **`references` confiné au document courant** (parité stricte §10.4 avec le vendord) | assumé | multi-fichiers passerait par l'index de mots (N4) — coût réel à mesurer |
| M5 | **Quick-fixes mono-fichier** (fiche 063) : le multi-fichiers passe par renommage/protocole seulement | assumé | — |
| M6 | **Réparation niveau 4 avec lambda dans la queue** : une expression lambda ouverte APRÈS le caret peut refermer ses listes dans un ordre syntaxiquement invalide — la variante est alors rejetée (best effort, les autres littéraux/l'arbre direct compensent) | ouvert, très petit | cas rare ; documenté dans `AideSignatures.refermer` |

## 3. Hôte (`codeide-lsp`) — écarts OUVERTS (la cause racine des « retards » appareil)

> PV §13.5 : « les écarts restants appareil se lisent donc côté HÔTE
> (nourrissage du cache à l'ouverture, délai de la première sync) : le
> banc S01/S02 en est la sonde de parité exacte. »

| # | Écart (mesuré dans les logs appareil, §13.1) | Effet utilisateur | Piste |
|---|---|---|---|
| H1 | **Le classpath réel n'arrive qu'à t+123,9 s** (« session LSP nourrie (1 module(s) actif(s), 47 entrées d'index) ») — la session démarre NOURRIE DU CACHE (t+8,5 s) | pendant DEUX MINUTES : `import a\|` rend 0 item, « AppCompatActivity cannot be resolved » squiggle — l'utilisateur voit « rien ne marche » | lancer la sync à l'ouverture AVANT le cache, ou servir le cache en signalant l'état |
| H2 | **Complétions 11–12 s après invalidation du moteur par la sync** (t+339 s, t+625 s) : reconstruction froide de l'environnement de noms sur classpath réel | l'appareil « se fige » sur les premières frappes post-sync | double-tampon : l'ancien environnement SERVANT pendant la construction du neuf |
| H3 | **Churn de sessions** (E4) : 10 × « LspProject configuré » + 10 × « LSP Java connecté » en ~1,4 s (jeu de langues final : 6 reconfigurations), 3 × libération de caches sous pression mémoire | mémoire + latence sur moto g06 | invariant « une session vivante par projet » (critère 6 du plan d'intégration §5) |
| H4 | **Databinding généré non indexé par l'arbre éditeur** (E1) : la cible de go-to vit sous `app/build/generated/…` | go-to databinding → WARN « cible introuvable dans l'arbre » côté éditeur | indexer `build/generated` côté CodeIDE |

## 4. Client (`cel-lsp` / `code-editor`) — écarts OUVERTS

| # | Écart | Effet utilisateur | Note |
|---|---|---|---|
| K1 | **Politique préfixe vide** : pas de popup avant la première LETTRE ou un caractère déclencheur (comportement identique au vendord, §11.1) | « j'ai tapé "import ", il n'y a pas de suggestions » — les items ARRIVENT à la première lettre | politique cel-lsp (80 ms debounce) ; le serveur répond dès le préfixe vide |
| K2 | **Capacités servies non consommées** (liste `docs/LSP_ECARTS_CLIENT.md` du dépôt codeide-lsp, à inverser post-bascule) : `rangeFormatting`, `onTypeFormatting`, palette `workspace/symbol`, `selectionRange`, `codeLens`, `callHierarchy`, `typeHierarchy`, `documentColor`, `linkedEditingRange`, `superDefinition`, `decompiledSource` | des gestes entiers manquent à l'éditeur alors que le serveur les sert | nourrit un futur prompt `code-editor` |
| K3 | **`decompiledSource` demandé AVANT la configuration du projet LSP** (E2/E3) | « source décompilée impossible : pas de projet LSP » | garde client à poser (attendre la session) |

## 5. Décisions au propriétaire (différées)

| # | Sujet | Référence |
|---|---|---|
| D1 | **Passe appareil moto g06 attendue sur 0.204.5** (le banc garantit le comportement MOTEUR sur classpath réel — les écarts restants se lisent côté hôte, §3) | PV §13.5 |
| D2 | **Retrait du vendord** : `:lsp:java` vendord reste au build de codeide-lsp, gelé, publiable jusqu'au retrait (retour arrière = substitution inverse en 1 ligne) | ADR 0233 de codeide-lsp ; §6 du plan d'intégration |
| D3 | **Périmètre Kotlin/XML** : les moteurs Kotlin et XML ne migrent PAS dans ce plan — habitat unique Java seulement | instruction du 30/10/2026 |
| D4 | **Génération data binding post-sync** (le HINT diagnostic reste jusqu'au premier build) | vendord §17.3 — à re-évaluer côté hôte |

---
*Inventaire établi le 31/10/2026 (état v1.1.5 + vague v1.1.6 §1). Toute
reprise d'un écart M/H/K doit citer son identifiant dans le PV
d'intégration.*
