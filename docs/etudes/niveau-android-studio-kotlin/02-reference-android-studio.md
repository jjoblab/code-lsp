# Phase 2 — Étude de la référence : Android Studio, mode K2

> **Date :** 2026-10-31 · **Niveau :** Android Studio / Kotlin · **Document :** 02 — Référence.
>
> **Règle R5 — statut des faits.** Tout fait de comportement attribué à la référence porte exactement l'un de ces trois statuts : `[DÉRIVÉ DES SOURCES]` (lu dans les sources ou documentations publiques listées en §D — liste close), `[À CAPTURER]` (non couvert par ces sources ; à vérifier par le propriétaire sur une instance réelle d'Android Studio, selon le protocole de capture du document 06), `[NON VÉRIFIÉ]` (hypothèse d'ingénierie, ni sourcée ni simplement vérifiable). Aucune mesure n'est inventée : les chiffres internes au dépôt (journaux, banc de fidélité, ADR) sont cités comme mesures internes et ne portent pas R5 — ce ne sont pas des faits de la référence.
>
> **Avertissement E-K2 — la référence n'est pas exécutable ici.** Android Studio est un IDE de bureau (processus JVM, empreinte mémoire en gigaoctets, chaîne de build complète) ; l'environnement de rédaction de cette étude n'en contient aucune instance exécutable. En conséquence, **aucun comportement décrit ci-dessous n'a été observé directement** : la cartographie repose sur les cinq sources publiques vérifiées (§D) et sur des hypothèses d'ingénierie systématiquement marquées `[À CAPTURER]` ou `[NON VÉRIFIÉ]`. Tout point `[À CAPTURER]` doit être capturé avant d'être opposé au moteur dans un arbitrage.

---

## §0 Méthode — cartographier sans copier

- **Posture.** Décrire la référence *pour elle-même* — quels comportements, déclenchés quand, à quel coût conceptuel — puis traduire chaque comportement en opportunité ou en frontière pour le moteur. On n'imite rien : la chaîne de classement ADR 0113 tranche ce qui entre, palier par palier, sur preuve de coût et de valeur.
- **Liste blanche des projets nommables.** Peuvent être désignés par leur nom : la plateforme IntelliJ, l'IDE Android Studio, le plugin Kotlin de l'IDE (mode K2, Analysis API), le plugin Android de l'IDE, l'outil Android Lint, le compilateur Kotlin K2, la bibliothèque kotlin-metadata-jvm, la grammaire KMP vendorée dans le dépôt, la bibliothèque lsp4j. S'y ajoutent, parce que le mandat et le corpus du propriétaire les mobilisent (sujets à éditer, pas références de conception) : Room, Hilt, l'AGP et les plugins de compilateur Kotlin publics listés en B9 (Compose compiler, Parcelize, serialization, allopen/noarg). **Tout autre outil tiers est décrit « pour lui-même », sans être nommé.**
- **Statuts R5.** Les cinq sources web vérifiées (§D) fondent tous les `[DÉRIVÉ DES SOURCES]` ; les citations y sont données en traduction française. Le reste de la description comportementale est `[À CAPTURER]` (vérifiable dans l'IDE par le propriétaire) ou `[NON VÉRIFIÉ]` (raisonnement d'ingénierie).
- **Grille de lecture par paliers** (terminologie du dépôt, pas de la référence) : **palier 1** ≈ phases brutes + portées ; **palier 2** ≈ phases de types + sous-ensemble borné de la résolution des corps ; **palier 3** = K2 complet, hors chemin de frappe — c'est-à-dire l'oracle du banc de fidélité, jamais un composant embarqué.
- **Composants du moteur cités.** TypageBorne (`TypeValeur = Projet | Jvm(fqcn)` — pas d'arguments de type, pas de nullabilité, pas de variance ; smart casts positionnels, `||` annule le cast) ; CompletionPipeline (6 genres : MEMBRE, NOM, IMPORT, ARGUMENTS_NOMMES, CONSTANTES_ENUM, ATTRIBUTS_ANNOTATION ; INCONNU = liste vide) ; DiagnosticsSemantiques (10 codes kt.*, politique « preuve ou silence » : un diagnostic n'est émis que si l'absence se prouve sur l'arbre + résolution bornée) ; OccurrencesProuvees (re-parse paresseux, aucun index d'usages persistant) ; ClassesSynthetiques (R / ViewBinding / BuildConfig prouvés avant build par DOM res/+manifeste, effacés devant une classe générée réelle) ; LecteurDeclarationsJava (interop Java en déclarations bornées, arité non contrôlée) ; CapacitesServeur (LSP4J : 19 capacités servies / 7 absentes, dont semanticTokens, codeLens, hiérarchies) ; la garde par déclaration (fiche 017) ; la chaîne de classement (ADR 0113) ; l'oracle K2 du banc de fidélité.
- **Chiffres internes mobilisés** (mesures du dépôt, hors R5) : parse 1,8 ms p50 (500 lignes, JVM) ; re-parse de 3 000 lignes à 8,6 ms p50 ; K2 embarqué disqualifié par mesure : 53,6 Mo / 64 507 méthodes / ~2 170 motifs sensibles ART / 3 503 ms d'amorçage ; dex du moteur : 26 590 méthodes / 4,10 Mo, pour des plafonds de 32 768 méthodes / 6 Mo ; cibles de vitesse de la mission : complétion, survol et signature < 100 ms p95 perçue, diagnostics ~300 ms, p99 régulier.
- **Liens avec la série.** Les fiches de comportement KC-01 à KC-12 du document 05 traduisent déjà plusieurs points de cette étude en captures planifiées ; le document 06 fixe le protocole ; des captures sont engagées (dossier `captures/`) mais leurs résultats ne sont pas repris ici — ce document reste au niveau de la cartographie, et les statuts `[À CAPTURER]` désignent ce que le protocole doit trancher.

---

## §A Le mode K2 de l'IDE

Cadre posé par les sources vérifiées : depuis IntelliJ IDEA 2025.1 (avril 2025), le mode K2 est actif **par défaut** côté IDEA, porté par un moteur d'analyse « reconstruit, plus rapide et plus scalable » et par une Analysis API qui « fournit un moyen clair et stable d'accéder à l'information du code sans dépendre des internes du compilateur » `[DÉRIVÉ DES SOURCES]`. Le blog JetBrains annonçait alors que le mode K2 « serait activé par défaut dans Android Studio plus tard dans l'année » `[DÉRIVÉ DES SOURCES]` — c'est advenu avec Android Studio Narwhal Feature Drop 2025.1.2 (juillet 2025) : « Android Studio utilise désormais le compilateur Kotlin K2 par défaut » `[DÉRIVÉ DES SOURCES]`. Le guide de migration Kotlin confirme le rôle du compilateur dans l'analyse : « IntelliJ IDEA et Android Studio utilisent le compilateur K2 pour analyser votre code Kotlin » `[DÉRIVÉ DES SOURCES]`. Version stable courante au jour de l'étude : Android Studio Rabbit 2 (2026.2.2) `[DÉRIVÉ DES SOURCES]`. Enfin, les limites avouées à l'instauration du mode K2 — smart completion parfois défaillant, « problèmes occasionnels d'analyse de code et de coloration », « vitesse de complétion réduite sur certains projets spécifiques » — rappellent que la référence livre elle-même un moteur en cours d'achèvement `[DÉRIVÉ DES SOURCES]`.

### A1 Résolution paresseuse par phases

**Comportement de la référence**

- Le front-end du compilateur K2 (l'arbre FIR) est embarqué dans l'IDE : l'analyse IDE n'est pas une réécriture des règles du compilateur, c'est le compilateur lui-même, invoqué à la demande — c'est le sens de « l'IDE utilise le compilateur K2 pour analyser votre code » `[DÉRIVÉ DES SOURCES]`.
- L'arbre d'analyse est construit depuis le PSI : l'Analysis API est « implémentée au-dessus du Kotlin PSI », avec des passerelles telles que `ktExpression.expressionType` ou `ktCallExpression.resolveSuccessfulCall()` `[DÉRIVÉ DES SOURCES]`.
- La résolution est **par déclaration**, avec des phases ordonnées : déclarations brutes → statut → supertypes → types et signatures → contrats → inférence des types de retour implicites → résolution des corps `[À CAPTURER]` (schéma du front-end K2 non décrit dans les cinq sources ; à confirmer sur instance ou dans la doc publique de l'API).
- Chaque phase est calculée à la demande ; les dépendances sont tirées récursivement — demander les supertypes d'une classe déclenche les phases amont des fichiers concernés `[À CAPTURER]`.
- Les résultats sont mis en cache par déclaration, dans une session par module — « KaSession est le point d'entrée pour interagir avec l'Analysis API », et il existe une session par module `[DÉRIVÉ DES SOURCES]`.
- L'accès aux résultats se fait sous *read action* : `analyze(element) { ... }` ; les entités KaLifetimeOwner ne sont valides que dans cette read action ; pour conserver un résultat au-delà, il faut passer par KaSymbolPointer / KaTypePointer `[DÉRIVÉ DES SOURCES]`.
- L'invalidation repose sur des trackers de modification : une édition « hors bloc » (qui change la structure des portées ou des signatures) invalide plus largement qu'une édition dans un corps de fonction `[À CAPTURER]`.
- Chaque fonctionnalité ne paie que les phases dont elle a besoin : le survol exige les types des déclarations ; le rouge exige la résolution des corps ; la complétion travaille sur une copie du fichier au caret, avec un identifiant factice, et une re-résolution locale `[À CAPTURER]`. Fragment (écrit pour ce document) :

```kotlin
fun somme(a: Int, b: Int) = a + b
// survol de `somme` : phases de déclaration (signature) ;
// le corps n'est résolu que si le rouge l'exige.
```

**Implications pour code-lsp**

- Notre re-parse paresseux (3 000 lignes à 8,6 ms p50) est l'analogue appauvri de « ne payer que ce qui a changé ». La leçon à retenir n'est pas « construire un cache par déclaration » mais « morceler par déclaration » : la garde par déclaration (fiche 017) est déjà notre granularité d'invalidation et de disponibilité.
- La correspondance des paliers se lit à livre ouvert : palier 1 ≈ phases brutes + portées (déjà servi par notre résolution de portées sur l'arbre) ; palier 2 ≈ phases de types + résolution des corps bornée (TypageBorne y gagnerait les types de retour explicites et le type du récepteur, sans arguments génériques) ; palier 3 = K2 complet — disqualifié en embarqué par mesure (53,6 Mo / 64 507 méthodes / ~2 170 motifs ART / 3 503 ms), il reste l'oracle du banc de fidélité.
- Les phases « statut » et « contrats » n'ont pas d'équivalent utile à notre échelle (pas de compilation multi-modules à orchestrer) : les omettre est un choix de périmètre, pas un déficit.
- Le modèle « session par module + read action » se traduit chez nous en « session par fichier + génération d'analyse » : nos réponses sont annulables entre re-parses et rien ne survit à une invalidation non prouvée — cohérent avec CapacitesServeur (19/7) : on ne promet pas ce qu'on ne tient pas.
- Toute extension de TypageBorne (nullabilité, arguments de type) doit franchir la chaîne ADR 0113 avec preuve de coût en dex et en latence : la référence montre ce que coûte la phase complète ; nous n'embarquons que des tranches décidables.

**Ce qui ne se porte pas**

- Le cache de résolution par déclaration à l'échelle d'un grand projet (centaines de milliers de déclarations) : notre budget est compté en Mo de dex (4,10 Mo sur un plafond de 6 Mo).
- Les trackers de modification incrémentaux multi-fichiers, qui supposent une infrastructure d'index persistant que le moteur refuse par conception (OccurrencesProuvees : re-parse honnête, jamais de réponse périmée).

### A2 Complétion

**Comportement de la référence**

- La complétion travaille sur une copie « en l'air » du fichier au caret : un identifiant factice y est inséré, la copie est re-résolue localement, et le contexte au caret devient analysable sans toucher au document réel `[À CAPTURER]`.
- Des contributeurs distincts s'additionnent : mots-clés du langage, symboles de portée (locaux puis imports), membres du récepteur, extensions applicables au récepteur `[À CAPTURER]`.
- Le classement applique des pesons : correspondance de nom (préfixe, sous-séquence), type attendu au point d'insertion, genre de déclaration, position syntaxique, statistiques d'usage locales `[À CAPTURER]`.
- Variantes dédiées : complétion intelligente (filtrée par type attendu), complétion en chaîne (extensions applicables au type du récepteur), second appel élargi (membres inaccessibles ou statiques) `[À CAPTURER]`.
- Import automatique à la sélection : le symbole hors portée est inséré et l'import est ajouté dans le même geste `[À CAPTURER]`.
- Contextes spécialisés : lambdas (paramètres suggérés, récepteur implicite et `it`), `when` (branches restantes pour exhaustivité), `override` et implémentation de stubs (squelettes complets générés) `[À CAPTURER]`.
- Le mode K2 à son instauration dégrade certains de ces comportements : smart completion parfois défaillant, vitesse réduite sur certains projets `[DÉRIVÉ DES SOURCES]` — la complétion complète reste un chantier, y compris pour la référence.

**Implications pour code-lsp**

- CompletionPipeline (MEMBRE, NOM, IMPORT, ARGUMENTS_NOMMES, CONSTANTES_ENUM, ATTRIBUTS_ANNOTATION) couvre les contributeurs *structurels* ; notre manque nommé est double : **le type attendu et le type du récepteur** — tous deux palier 2 (TypageBorne suffit au `Jvm(fqcn)` du récepteur ; le type attendu exige les signatures des surcharges).
- La copie au caret avec identifiant factice est directement portable : à 8,6 ms p50 de re-parse pour 3 000 lignes, la copie + tampon paresseux tient sous la cible < 100 ms p95 perçue.
- Les pesons statistiques (fréquences d'usage) ne se portent pas : pas d'index d'usages persistant (OccurrencesProuvees), pas de télémétrie sur l'appareil. Nos pesons restent syntaxiques : préfixe, genre, portée, position.
- La complétion en chaîne (extensions applicables) exige types + corps → palier 2 ; à palier 1, une extension n'est pas « applicable », elle est « nommée » : le genre IMPORT le sert honnêtement.
- INCONNU = liste vide reste la bonne politique : la référence avoue elle-même des trous ; nous les déclarons au lieu de les remplir au hasard.
- Le second appel élargi (inaccessibles / statiques) est un simple drapeau de requête, compatible avec la capacité complétion déjà servie — à classer ADR 0113 comme extension à bas coût.

**Ce qui ne se porte pas**

- Les pesons appris et les statistiques d'usage locales (mémoire et historique persistants).
- La complétion sur l'ensemble des sources d'un projet non ouvert (workspace entier résolu) : elle exige l'index complet.

### A3 Diagnostics

**Comportement de la référence**

- En mode K2, le rouge Kotlin vient des diagnostics du compilateur K2 lui-même : fidélité au compilateur native, rafraîchie à la frappe — conséquence directe de « l'IDE utilise le compilateur K2 pour analyser votre code » `[DÉRIVÉ DES SOURCES]`. La précision des messages et des plages est celle du compilateur `[À CAPTURER]`.
- Les inspections IDE se superposent au rouge compilateur, avec sévérités configurables par inspection et par profil `[À CAPTURER]`.
- `@Suppress` agit des deux côtés : sur le diagnostic compilateur et sur l'inspection IDE `[À CAPTURER]`.
- Le mode K2 admet des « problèmes occasionnels d'analyse de code et de coloration » `[DÉRIVÉ DES SOURCES]` : la référence accepte du bruit transitoire ; notre politique « preuve ou silence » est l'opposé assumé (zéro bruit, trous déclarés).

**Tableau — top des diagnostics pour un développeur Android Kotlin.** Libellés en français descriptif de diagnostics réels du compilateur (identifiants internes non reproduits, clean-room) ; « besoin sémantique » = ce qu'il faut savoir pour décider ; « décidable » = décidable *sans* inférence complète ; « statut moteur » = code kt.* existant ou absent ; « palier » = palier cible.

| Famille | Libellé représentatif (descriptif) | Besoin sémantique | Décidable sans inférence complète ? | Statut moteur | Palier |
|---|---|---|---|---|---|
| Résolution de noms | Référence non résolue — nom absent des portées et imports visibles | imports/portée | Oui | `kt.unresolved` | 1 |
| Résolution de noms | Membre non résolu sur un récepteur typé | typage simple | Partiel (type du récepteur requis) | `kt.unresolved` | 2 |
| Résolution de noms | Extension applicable au récepteur mais non importée | imports + typage | Partiel | `kt.unresolved` (+ action d'import) | 2 |
| Résolution de noms | Ambiguïté — plusieurs candidates également applicables | inférence complète | Non | absent | 3 |
| Résolution de noms | Aucune candidate applicable pour l'appel | inférence complète | Non | absent | 3 |
| Résolution de noms | Référence à un membre protégé hors hiérarchie | portée/visibilité | Oui | absent | 2 |
| Résolution de noms | Membre d'instance atteint depuis le compagnon (ou l'inverse) | typage simple | Partiel | absent | 2 |
| Résolution de noms | Étiquette de saut/retour non résolue | syntaxe/portée | Oui | absent | 1 |
| Imports | Import inutilisé | syntaxe | Oui | `kt.unusedImport` | 1 |
| Imports | Imports en conflit — même nom simple depuis deux packages | syntaxe | Oui | `kt.conflictingImport` | 1 |
| Imports | Import non résolu — cible inexistante | imports/portée | Oui | `kt.unresolved` | 1 |
| Imports | Import redondant (déjà déclaré dans le fichier ou le package) | syntaxe | Oui | absent | 1 |
| Imports | Joker et import simple du même nom simultanés | syntaxe | Oui | absent | 1 |
| Nullabilité | Appel de membre non sûr sur un récepteur nullable | typage simple | Oui | `kt.unsafeNullable` | 1 |
| Nullabilité | Initialisation d'un type non nullable par une expression nullable | typage simple | Oui | `kt.typeMismatch` | 1 |
| Nullabilité | Argument nullable passé à un paramètre non nullable | typage simple | Partiel (signatures requises) | `kt.typeMismatch` | 2 |
| Nullabilité | Smart cast impossible — propriété mutable ou ouverte | typage simple | Partiel | absent | 2 |
| Nullabilité | Smart cast annulé — écriture possible entre la garde et l'usage | inférence complète | Non | absent | 3 |
| Nullabilité | `!!` redondant sur une valeur déjà non nullable | typage simple | Oui | absent | 2 |
| Nullabilité | Comparaison avec `null` inutile — type jamais nullable | typage simple | Oui | absent | 2 |
| Nullabilité | Valeur possiblement nulle rendue par une lambda vers un type non nullable | inférence complète | Non | absent | 3 |
| Types/arguments | Non-concordance de type en affectation ou initialisation | typage simple | Partiel (TypageBorne à deux cas) | `kt.typeMismatch` | 1 |
| Types/arguments | Nombre d'arguments d'appel incorrect (déclarations Kotlin) | typage simple | Oui (arité déclarée ; non contrôlée côté Java) | `kt.argumentCount` | 1 |
| Types/arguments | Argument nommé inconnu ou mal orthographié | typage simple | Oui (paramètres déclarés) | absent | 2 |
| Types/arguments | Type générique utilisé sans ses arguments requis | typage simple | Partiel | absent | 2 |
| Types/arguments | Trop ou trop peu d'arguments de type | typage simple | Oui (paramètres déclarés) | absent | 2 |
| Types/arguments | Argument de type hors borne supérieure | inférence complète | Non | absent | 3 |
| Types/arguments | Type de retour explicite contredit l'initialisateur | inférence complète | Non | absent | 3 |
| Types/arguments | Variable sans type écrit ni initialisateur inférable | inférence complète | Non | absent | 3 |
| Types/arguments | Littéral entier hors plage du type attendu | typage simple | Oui | absent (action de conversion de littéral) | 2 |
| Types/arguments | Projection de variance en position interdite (sortie consommée en entrée) | variance | Non | absent | 3 |
| Surcharges/candidates | Aucune surcharge ne convient aux types des arguments | inférence complète | Non (l'arité seule est couverte) | `kt.argumentCount` (partiel) | 3 |
| Surcharges/candidates | Ambiguïté entre surcharges après implicites par défaut | inférence complète | Non | absent | 3 |
| Surcharges/candidates | Ambiguïté entre extensions applicables | inférence complète | Non | absent | 3 |
| Surcharges/candidates | Extension nullable appelée sur un récepteur non nullable | typage simple | Partiel | absent | 2 |
| Surcharges/candidates | Repli vers une candidate `Any` moins spécifique | inférence complète | Non | absent | 3 |
| Déclarations/visibilité | Réaffectation d'un `val` | syntaxe/portée | Oui | `kt.valReassign` | 1 |
| Déclarations/visibilité | Déclaration en double dans la même portée ou le même fichier | syntaxe | Oui | absent | 1 |
| Déclarations/visibilité | Combinaison de modificateurs interdite | syntaxe | Oui | `kt.syntax` (partiel) | 1 |
| Déclarations/visibilité | Membre `internal` exposé par une API publique | typage simple | Partiel | absent | 2 |
| Déclarations/visibilité | Propriété `lateinit` lue sans garantie d'initialisation | typage simple | Partiel | absent | 2 |
| Déclarations/visibilité | Accès à un membre privé d'une autre classe | portée/visibilité | Oui | absent | 2 |
| Héritage/abstrait | Membre abstrait non implémenté | typage simple (supertypes) | Oui (phases brutes + supertypes) | absent (action « membres manquants » servie) | 2 |
| Héritage/abstrait | Retour manquant dans une fonction au type non `Unit` | typage simple | Partiel | `kt.missingReturn` | 1 |
| Héritage/abstrait | Redéfinition sans le modificateur `override` | typage simple | Partiel | absent | 2 |
| Héritage/abstrait | Redéfinition d'un membre final | typage simple | Partiel | absent | 2 |
| Héritage/abstrait | Implémentation multiple aux signatures incompatibles | inférence complète | Non | absent | 3 |
| Héritage/abstrait | Instanciation directe d'une classe abstraite | typage simple | Oui | absent | 2 |
| Contraintes/variance | Violation de la borne supérieure d'un paramètre de type | inférence complète | Non | absent | 3 |
| Contraintes/variance | Argument de type contredisant la variance déclarée | inférence complète | Non | absent | 3 |
| Contraintes/variance | Étoile-projection en position d'entrée interdite | variance | Non | absent | 3 |
| Contraintes/variance | Paramètre de type utilisé hors de ses bornes effectives | inférence complète | Non | absent | 3 |
| Contrôle/exhaustivité | `when` non exhaustif — sujet enum/scellé sans `else` | typage simple (types fermés) | Partiel | absent | 2 |
| Contrôle/exhaustivité | `when` avec `else` alors que toutes les branches sont couvertes | typage simple | Partiel | absent | 2 |
| Contrôle/exhaustivité | Code inatteignable | typage simple (flux positionnel) | Partiel | `kt.unreachable` | 1 |
| Contrôle/exhaustivité | `break`/`continue` hors boucle, `return` hors fonction | syntaxe | Oui | `kt.syntax` (partiel) | 1 |
| Coroutines/suspend | Appel suspendant hors contexte suspend | typage simple (modificateur de signature) | Partiel | absent | 2 |
| Coroutines/suspend | Fonction `suspend` sans point de suspension (avertissement) | typage simple | Partiel | absent | 2 |
| Coroutines/suspend | Appel bloquant repéré dans une coroutine | inférence complète | Non | absent | 3 |
| Compose | Appel de composable hors contexte composable | typage simple (annotation) | Partiel | absent | 2 |
| Compose | `@Preview` sur une fonction non composable | typage simple | Oui | absent | 2 |
| Compose | Paramètre `modifier` absent ou mal placé (inspection) | typage simple | Partiel | absent | 2 |
| Interop Java | Valeur Java annotée nullable passée à un paramètre non nullable | typage simple (annotations) | Partiel (si `@Nullable` lue) | absent | 2 |
| Interop Java | Membre statique Java atteint via l'instance | typage simple | Partiel | absent | 2 |
| Interop Java | Collision de signature Kotlin/Java (méthode masquée par une propriété) | inférence complète | Non | absent | 3 |

**Lecture du tableau.** Sur 65 lignes : 17 touchent déjà un code kt.* existant (parfois partiellement — c'est le palier 1 consolidé) ; 29 visent le palier 2, décidables sans inférence complète à partir de types simples, signatures déclarées et annotations ; 19 exigent l'inférence complète — palier 3, c'est-à-dire l'oracle du banc de fidélité, jamais embarqué. La frontière palier 2 / palier 3 est exactement la frontière « décidable sans inférence complète ».

**Implications pour code-lsp**

- Le tableau désigne le palier 2 comme le gisement principal : les lignes « besoin = typage simple » et « décidable = oui/partiel » y sont majoritaires sans exiger l'inférence complète. Chaque ligne est une entrée candidate pour la chaîne ADR 0113 : un nouveau code kt.* se justifie par sa **preuve de décidabilité**, pas par son existence chez la référence.
- La fidélité compilateur par frappe n'est pas un objectif : c'est la définition du palier 3, disqualifié par mesure (53,6 Mo / 64 507 méthodes / ~2 170 motifs ART / 3 503 ms). Notre rouge ne prétendra jamais à la complétude ; « preuve ou silence » est le contrat.
- `@Suppress` des deux côtés : côté moteur, l'honorer au niveau des codes kt.* est à la portée de DiagnosticsSemantiques (la lecture d'annotations existe déjà dans CompletionPipeline via ATTRIBUTS_ANNOTATION) — palier 1, à classer.
- L'oracle K2 du banc de fidélité transforme nos silences assumés en écarts *déclarés* : quand la référence rougit sur une ligne « absent, palier 3 » et que le moteur se tait, le banc comptabilise un écart prévu, pas un bug.

**Ce qui ne se porte pas**

- Le catalogue intégral des diagnostics du compilateur (des centaines de codes) : chacun coûte des phases que le moteur n'a pas ; seule la sélection par valeur (corpus du propriétaire) est réaliste.
- Les messages paramétrés du compilateur avec rendu enrichi : sur l'appareil, un libellé par code suffit — l'effort va à la preuve, pas au texte.

### A4 Inspections, intentions, quick-fixes

**Comportement de la référence** — catalogue priorisé par fréquence d'usage probable pour un développeur Android Kotlin (composition exacte du menu contextuel à capturer ; l'existence d'un catalogue d'intentions et quick-fixes dans l'IDE est de notoriété publique mais non couverte par les cinq sources `[À CAPTURER]`) :

1. Bascule corps d'expression ↔ corps de bloc (`fun f() = x` ↔ `fun f(): T { return x }`).
2. Ajout / retrait du type de retour explicite.
3. Bascule `if`/`else` ↔ `when`.
4. Encadrement d'un accès nullable par `?.let { ... }`.
5. Conversion concaténation → gabarit de chaîne.
6. Suppression de `!!` (remplacement par une garde sûre ou `?:`).
7. Nommage d'un argument positionnel.
8. Extraction de variable / propriété.
9. Extraction de fonction.
10. Extraction de paramètre.
11. Entourage par `run` / `with` / `let` / `apply`.
12. Conversion lambda → référence de fonction (`::`).
13. Conversion fonction → propriété (getter sans paramètre).
14. Suppression du constructeur primaire vide.
15. Génération de `hashCode` / `equals` / `toString`.
16. Implémentation / génération de stubs (membres abstraits d'une classe ou interface).
17. Qualification d'une référence ambiguë par import ou nom complet.
18. Ajout d'imports manquants à la volée (sur référence non résolue).

- Chaque intention n'est proposée que si la transformation compile dans le contexte : la disponibilité est elle-même décidée sur l'arbre d'analyse `[À CAPTURER]`.
- Prévisualisation du diff avant application `[À CAPTURER]`.
- Extinction locale par `@Suppress` et désactivation par inspection `[À CAPTURER]`.

**Implications pour code-lsp**

- Les familles d'actions du moteur (imports, accès non sûr, conversions de littéral, membres manquants) recouvrent le noyau de cette liste : import automatique ↔ 17-18 ; accès non sûr ↔ 4 et 6 ; membres manquants ↔ 16.
- Critère d'entrée en ADR 0113 : « décidable sur l'arbre + TypageBorne, sans inférence complète ». Les bascules purement structurelles (1, 3, 5, 11, 13, 14) sont palier 1 ; le nommage d'argument (7) exige les paramètres déclarés (palier 2 pour Java via LecteurDeclarationsJava) ; l'extraction (8-10) exige la résolution de portée (collisions de noms) mais pas l'inférence — palier 1 en omettant le type inféré.
- La disponibilité contextuelle *prouvée* rejoint la garde par déclaration (fiche 017) : une action n'est proposée que si sa précondition est prouvée, sinon silence.
- La prévisualisation de diff n'a pas d'équivalent LSP standard servi par CapacitesServeur — hors périmètre assumé.

**Ce qui ne se porte pas**

- Les intentions dont la validité exige l'inférence complète (p. ex. changement de signature en cascade typé) : palier 3.
- La prévisualisation riche multi-fichiers.

### A5 Refactorings

**Comportement de la référence**

- Extractions : variable, propriété, fonction, paramètre — avec choix de portée et gestion des collisions `[À CAPTURER]`.
- Inline : variable, fonction, propagation des usages multiples `[À CAPTURER]`.
- Changement de signature : paramètres, valeurs par défaut, propagation aux appelants `[À CAPTURER]`.
- Move : déplacement d'une déclaration vers un autre fichier ou package, avec mise à jour des imports `[À CAPTURER]`.
- Safe delete : recherche d'usages avant suppression, conflits signalés, annulation possible `[À CAPTURER]`.
- Rename : usages *typés* — lecture / écriture / implicite / texte (occurrences dans chaînes et commentaires, heuristiques) `[À CAPTURER]`.
- Le rename traverse les langages : Kotlin, Java, et jusqu'aux identifiants de ressources avec le plugin Android `[À CAPTURER]`.

**Implications pour code-lsp**

- Notre rename multi-fichiers .kt/.kts (OccurrencesProuvees, re-parse paresseux) est structurellement identique à un palier 1 près : la référence renomme au moyen de son index d'usages, nous au moyen de la re-vérification ; la différence de coût est cachée derrière la même capacité LSP (rename, servie).
- Les usages « texte » chez la référence sont heuristiques ; « preuve ou silence » interdit l'heuristique non prouvée : soit une recherche textuelle explicitement déclarée comme telle (non sémantique), soit rien. À trancher en ADR 0113.
- Safe delete = rename inversé + garde par déclaration : faisable palier 2 sur le corpus .kt du projet (déclarations Projet) ; pas à travers Java (LecteurDeclarationsJava est en lecture seule, pas de réécriture Java).
- Changement de signature et move exigent la mise à jour des appelants → résolution des corps complète → palier 3, sauf fonctions privées au fichier (portée prouvable, palier 1-2).
- Extract function à palier 1 est honnête si la signature générée omet le type de retour (inférence laissée au compilateur réel) : la référence sait, nous déléguons ce que nous ne prouvons pas.

**Ce qui ne se porte pas**

- Refactorings inter-langues complets (Kotlin + Java + XML simultanés) : réécriture multi-langages hors périmètre (LecteurDeclarationsJava en lecture seule).
- Détection de conflits fondée sur l'inférence complète (cassure de surcharges génériques).

### A6 Inlay hints, coloration sémantique, KDoc, pliage, optimiseur d'imports, code style

**Comportement de la référence**

- Inlay hints : types implicites (val/var/expressions sans type écrit), noms de paramètres aux sites d'appel, récepteurs implicites `this`/`it` étiquetés `[À CAPTURER]`.
- Coloration sémantique K2 : coloration par symbole résolu et pas seulement par jeton lexical ; le mode K2 a connu des « problèmes occasionnels d'analyse et de coloration » `[DÉRIVÉ DES SOURCES]` pour la limite avouée, `[À CAPTURER]` pour le mécanisme.
- Rendu KDoc au survol : signature + documentation formatée, liens suivables `[À CAPTURER]`.
- Pliage Kotlin : imports, corps, branches de `when`, lambdas, blocs KDoc `[À CAPTURER]`.
- Optimiseur d'imports : tri, suppression des inutilisés, expansion/résolution des jokers, gestion des alias `[À CAPTURER]`.
- Code style Kotlin configurable (ordre des imports, espacement) et formateur associé `[À CAPTURER]`.

**Implications pour code-lsp**

- CapacitesServeur : semanticTokens figure parmi les 7 absentes — ce paragraphe documente ce que son service coûterait : la coloration par symbole résolu exige les phases de types (palier 2). Une coloration purement lexicale (mots-clés, chaînes, commentaires) est déjà possible mais de faible valeur : à ne servir que si un client le demande.
- Hints de noms de paramètres : ARGUMENTS_NOMMES existe en complétion ; le hint au site d'appel passe par une capacité inlayHints exposée nativement par LSP4J — sa place dans les 19 capacités servies est à confirmer en interne ; le coût réel est la lecture des signatures : palier 1 pour les déclarations Kotlin du projet (Projet), palier 2 pour Java (LecteurDeclarationsJava).
- Hints de types implicites : afficher le type inféré suppose de l'inférer — palier 3, non.
- Optimiseur d'imports : la famille d'actions « imports » couvre l'ajout ; la suppression s'appuie sur `kt.unusedImport` ; le tri est trivial sur l'arbre. Le trio forme un palier 1 complet — candidat ADR 0113 à bas coût et forte valeur (rouge résiduel le plus fréquent).
- KDoc au survol : assembler signature déclarée (palier 1) + doc du commentaire adjacent est faisable sans analyse ; le rendu riche avec liens résolus est palier 2+.
- Pliage : le pliage purement syntaxique est honnête ; sa présence dans les capacités servies est à confirmer en interne.

**Ce qui ne se porte pas**

- Coloration par symbole résolu (semanticTokens) sans les phases de types.
- Hints de types inférés (inférence complète).
- Formateur complet de code style : espace de règles important, maintenance continue.

### A7 Index et dumb mode

**Comportement de la référence**

- L'IDE sépare ce qui fonctionne sans index (édition, fonctions purement syntaxiques : structure, pliage, coloration lexicale, complétion de mots-clés) de ce qui l'exige (navigation, recherches, complétion de symboles, refactorings) `[À CAPTURER]`.
- Pendant la (re)construction de l'index — dumb mode — les fonctions exigeantes sont indisponibles ou dégradées, et l'état est signalé `[À CAPTURER]`.
- Index inversés : héritage (qui étend quoi), extensions (qui étend quel type) — ils alimentent complétion, inspections et navigation `[À CAPTURER]`.
- Interop Kotlin↔Java en mode K2 : un modèle de déclarations unique — les sources Java sont exposées au front-end comme des déclarations de première classe via une couche d'adaptation, et la résolution est bidirectionnelle (le Kotlin voit le Java, le Java voit le Kotlin) `[À CAPTURER]`.
- La montée des index et caches est le coût de démarrage de l'IDE ; le mode K2 l'a réduite sans la supprimer `[À CAPTURER]`.

**Implications pour code-lsp**

- Notre équivalent du dumb mode serait l'état « avant première lecture » : il n'existe pas — la lecture disque (res/, manifeste, déclarations) est immédiate et ClassesSynthetiques ne dépend d'aucun index. L'appareil n'a jamais de dumb mode ; tout ce qui est servi l'est dès le lancement. C'est un argument produit (§C, axe 3).
- Les index inversés (héritage, extensions) nomment exactement ce que nous n'avons pas : « qui étend quel type » exige un balayage complet ; OccurrencesProuvees le fait à la demande par re-parse, jamais en tâche de fond persistante — coût mesuré acceptable (8,6 ms p50 / 3 000 lignes) tant que la requête est ciblée.
- Le modèle unique de déclarations Kotlin/Java est l'axe de LecteurDeclarationsJava — mais borné : déclarations de première classe en lecture seule, arité non contrôlée, pas de réécriture. La bidirectionnalité complète (Java voit Kotlin) est hors de portée : nous n'analysons pas Java, nous le lisons.
- L'absence d'index est une politique : jamais de réponse périmée, jamais de tâche de fond. À écrire noir sur blanc côté utilisateur : ce que l'IDE sert « instantanément » via l'index, nous le servons par re-parse à la demande.
- CapacitesServeur : la plupart des 7 absentes (semanticTokens, codeLens, hiérarchies…) sont des consommatrices d'index — le classement 19/7 traduit cette frontière, il n'est pas un accident.

**Ce qui ne se porte pas**

- Un index inversé persistant sur l'appareil : stockage + invalidation + mémoire au-delà du budget (dex 4,10 Mo / plafond 6 Mo).
- La bidirectionnalité Java→Kotlin complète (contrôle de cassure côté Java).

---

## §B Spécifique Android/Kotlin

Même discipline : comportements de la référence avec statuts R5, puis implications pour les composants du moteur, puis ce qui ne se porte pas sur ART. Les cinq sources vérifiées couvrent le socle K2 mais peu le détail Android : l'essentiel de ce qui suit est donc `[À CAPTURER]` — la liste de capture prioritaire se concentre ici, en lien avec les fiches KC du document 05 et le protocole du document 06.

### B8 Ressources et binding

**Comportement de la référence**

- La classe `R` est exposée à l'éditeur sous forme de classes légères construites depuis `res/`, sans attendre un build complet ; la navigation `R.string.accueil` → `strings.xml` en découle `[À CAPTURER]`.
- `R` est exposé par variante et par module : R du module courant, imports explicites pour les modules voisins `[À CAPTURER]`.
- ViewBinding : les classes de binding sont générées par l'AGP dans `build/generated` et ne deviennent visibles pour l'éditeur qu'après un build ou une sync réussie `[À CAPTURER]`.
- DataBinding suit le même chemin, avec en sus ses expressions dans les layouts `[À CAPTURER]`.
- Survol d'une référence de ressource : la valeur effective (chaîne par défaut, dimension résolue) est affichée dans la documentation rapide `[À CAPTURER]`.
- Navigation XML ↔ Kotlin : depuis un identifiant de vue vers ses usages Kotlin, depuis une classe de binding vers le layout source `[À CAPTURER]`.
- La délégation `by viewBinding` est reconnue et le type de la propriété déléguée est résolu depuis la classe générée `[À CAPTURER]`.
- Avant le premier build réussi, `R` peut être résolu (lecture de `res/`) mais pas les classes de binding : le code qui les référence reste rouge jusqu'à la sync/build — le fameux « faites d'abord un build » `[À CAPTURER]`.

**Implications pour code-lsp**

- ClassesSynthetiques fait mieux que la référence sur ce point précis : R, ViewBinding et BuildConfig sont prouvés AVANT tout build, par lecture DOM de `res/` et du manifeste, et effacés devant une classe générée réelle. Premier ouvrage du projet : zéro rouge transitoire côté moteur. C'est l'axe de surpassement n° 1 (§C).
- La politique d'effacement est notre équivalent de « la classe générée gagne » : la référence n'a pas besoin de cette garde (ses classes existent dès la sync) ; nous, oui — et la garde existe déjà.
- Le survol de ressource avec valeur est portable à palier 1 : la lecture DOM de `res/values` est déjà dans ClassesSynthetiques, il reste à la brancher sur le survol. Candidat ADR 0113 à bas coût.
- La navigation XML ↔ Kotlin s'appuie sur l'existant : côté .kt, OccurrencesProuvees (re-parse) sert la capacité references ; côté XML, lecture DOM des identifiants — exposable sans index persistant.
- `by viewBinding` : TypageBorne peut attribuer `Jvm(fqcn)` de la classe synthétique à la propriété déléguée — palier 2, à condition que la preuve (layout existant, `buildFeatures` actif) soit faite.

**Ce qui ne se porte pas**

- Le rendu par locale et par variante (values-fr, flavors) : lecture multi-variantes coûteuse ; le sous-ensemble « valeurs par défaut » suffit au corpus.
- DataBinding et ses expressions XML : un mini-langage dans les layouts — hors périmètre assumé.

### B9 Code généré (KSP/KAPT) et plugins de compilateur

**Comportement de la référence**

- Les racines de sources générées par KSP/KAPT deviennent des sources comme les autres après build/sync : navigation, complétion et rouge y fonctionnent `[À CAPTURER]`.
- Avant build, le code appelant le généré est rouge, sauf support IDE dédié : Room et Hilt (sujets du corpus du propriétaire) disposent d'un tel support, qui prédit les symboles générés avant la compilation `[À CAPTURER]`.
- Le support dédié fonctionne par connaissance injectée : l'extension IDE connaît le contrat du générateur (ce que Room produit à partir d'un `@Dao`, par exemple) et le matérialise pour l'analyse `[À CAPTURER]`.
- Les plugins de compilateur Kotlin publics (Compose compiler, Parcelize, serialization, allopen/noarg) reçoivent le même traitement : leur sémantique est injectée dans l'IDE par des points d'extension dédiés — l'IDE recode la connaissance du plugin, il ne l'exécute pas `[À CAPTURER]`.

**Implications pour code-lsp**

- Le point de douleur du propriétaire (rouge avant build sur les symboles Room/Hilt) est exactement là où la référence générale s'arrête : elle ne résout pas le générique, elle traite les cas un par un, par connaissance injectée. Notre équivalent structurel existe déjà : ClassesSynthetiques. La voie est d'y ajouter des **patrons nommés** — lire `@Dao`/`@Database`/`@Entity` (Room) et `@AndroidEntryPoint`/`@Module` (Hilt) pour prouver les symboles générés attendus (implémentations de DAO, fabriques de binding) avant build. Candidat majeur de la chaîne ADR 0113 : c'est le pendant exact des synthétiques Android, appliqué au corpus.
- Leçon d'architecture : ni la référence ni le moteur n'exécutent le processeur d'annotations ; les deux prédisent. La différence est le budget de prédiction — le nôtre reste en lecture déclarative (annotations + supertypes), sans réécrire la logique de génération.
- Version minimale pour les autres plugins : traiter leurs annotations comme des marqueurs (partiellement vrai déjà, via ATTRIBUTS_ANNOTATION en complétion), sans sémantique — et le déclarer dans la documentation utilisateur.
- La garde d'effacement s'applique : après un vrai build (sur machine de bureau), la classe générée réelle remplace la prédiction — même règle que pour R/ViewBinding.

**Ce qui ne se porte pas**

- Exécuter de vrais processeurs (KSP/KAPT) sur l'appareil : chaîne de build complète — hors budget dex/mémoire/CPU.
- La sémantique fine de chaque plugin (validation complète des schémas serialization, p. ex.).

### B10 Compose

**Comportement de la référence**

- Le contexte `@Composable` est une règle de compilation : un appel de composable hors contexte composable (fonction composable ou lambda composable englobant) est rouge dans l'éditeur `[À CAPTURER]`.
- La non-satisfaction des contraintes de stabilité est signalée plus faiblement (avertissements, inspections) que le rouge structurel `[À CAPTURER]`.
- Complétion : membres de `Modifier` en chaîne, paramètres nommés des composables, imports automatiques `[À CAPTURER]`.
- États : inspections dédiées aux états lus/écrits sans mémorisation (`remember`) dans la composition `[À CAPTURER]`.
- `@Preview` : exigences de signature vérifiées (fonction composable, paramètres sans valeur par défaut signalés), rendu et navigation dédiés `[À CAPTURER]`.
- Inspections de style Compose : nommage des composables (majuscule initiale), présence et position du paramètre `modifier` en tête `[À CAPTURER]`.

**Implications pour code-lsp**

- « Appel composable hors contexte » est un diagnostic de typage simple : le contexte est une propriété de l'enclos, décidable sur l'arbre + déclarations brutes, sans inférence complète — candidat palier 2 exact (tableau A3, famille Compose). Fragment (écrit pour ce document) :

```kotlin
@Composable fun Ecran() { Message() }  // dans un contexte composable : licite
fun utilitaire() { Message() }         // hors contexte : rouge dans la référence
```

- Complétion des paramètres nommés : ARGUMENTS_NOMMES (genre servi) l'offre déjà pour les composables déclarés dans le projet (Projet) — palier 1 sur le corpus.
- `Modifier` chaîné : MEMBRE sur récepteur `Jvm(fqcn)` — palier 2 (type du récepteur) ; le corpus du propriétaire y gagne directement.
- `@Preview` mal placé (fonction non composable) : typage simple, palier 2, même mécanisme que le contexte composable.
- Mémorisation, stabilité, nommage : non prouvables sans inférence — silence assumé et documenté.

**Ce qui ne se porte pas**

- Le rendu `@Preview` sur l'appareil : composant d'interface complet.
- L'analyse de stabilité des paramètres : inférence complète.

### B11 Coroutines et Flow

**Comportement de la référence**

- Le contexte suspend est une règle de compilation : un appel de fonction `suspend` hors coroutine (ni fonction `suspend`, ni englobant coroutine) est rouge `[À CAPTURER]`.
- Complétion dans une coroutine : builders (launch, async), fonctions suspend applicables, fonctions d'annulation `[À CAPTURER]`.
- Inspections de concurrence structurée : appels bloquants dans une coroutine, fuites de Job, usage de la portée globale `[À CAPTURER]`.
- Flow : complétion des opérateurs sur le récepteur typé, navigation vers les implémentations `[À CAPTURER]`.

**Implications pour code-lsp**

- « Appel suspendant hors contexte » est symétrique du cas Compose : modificateur `suspend` sur la signature + contexte de l'enclos — décidable par déclarations brutes + portées, sans inférence complète (tableau A3, famille coroutines, palier 2).
- CompletionPipeline peut appliquer un peson syntaxique — favoriser les symboles `suspend` dans un contexte suspend : coût nul, gain de classement, palier 1.
- Flow : les opérateurs sont des extensions génériques — leur complétion exige le type du récepteur (palier 2), leur typage complet l'inférence (palier 3). Le moteur sert le premier, jamais le second.
- Les inspections de concurrence fondées sur l'inférence (fuites, blocages) : silence — « preuve ou silence ».

**Ce qui ne se porte pas**

- Vérification de la coopération à l'annulation, analyse de fuite : inférence complète, voire exécution symbolique.

### B12 Gradle Kotlin DSL

**Comportement de la référence**

- Les accesseurs de script (`projects.*`, catalogue `libs.*`) sont générés lors de la sync Gradle ; sans sync, ils ne résolvent pas — rouge et complétion vide dans `*.gradle.kts` `[À CAPTURER]`.
- Après sync : complétion et navigation dans `*.gradle.kts` et dans `libs.versions.toml` (alias, versions) `[À CAPTURER]`.
- La sync fournit au modèle IDE : variantes, sourceSets, minSdk/namespace, processeurs (KSP/KAPT), dépendances — l'éditeur en tire les racines de sources, le SDK cible et les symboles disponibles `[À CAPTURER]`.
- Le rouge et la complétion DSL suivent la santé de la sync : un build cassé ou une sync échouée dégrade l'expérience dans les scripts `[À CAPTURER]`.

**Implications pour code-lsp**

- Notre avantage structurel : lecture disque directe de `build.gradle.kts`, `libs.versions.toml`, du manifeste et de `res/` — le modèle de projet partagé Java/Kotlin n'attend aucune sync. La référence ne peut pas l'offrir : sa connaissance vient de la génération.
- Champs utiles au modèle de projet partagé (cartographie) : `namespace` (DSL, manifeste en repli) ; `minSdk`/`targetSdk`/`compileSdk` (DSL, manifeste en repli) ; `res/` (ClassesSynthetiques) ; manifeste (composants, permissions) ; `buildFeatures` (viewBinding actif → synthétiques) ; processeurs déclarés (plugins KSP/KAPT → anticiper Room/Hilt, cf. B9) ; variantes/flavors (sous-ensemble par défaut) ; sourceSets additionnels (racines de lecture).
- Complétion dans `libs.versions.toml` : format TOML hors grammaire KMP vendorée — un petit lecteur dédié suffit (alias déclarés vs alias utilisés, versions) ; candidat ADR 0113 à bas coût, valeur immédiate pour le corpus.
- Les accesseurs `projects.*` sont dérivables sans sync : les modules sont des dossiers frères porteurs de fichiers de build — lecture disque, pas génération. Dépassement local assumé (la référence attend la sync).
- Garde : ces accesseurs dérivés sont des **prédictions** — à marquer comme ClassesSynthetiques (effaçables devant preuve contraire), pas comme des vérités.

**Ce qui ne se porte pas**

- Évaluer le DSL Gradle (exécuter le script de build) sur l'appareil : impossible et inutile — la lecture déclarative des champs suffit au corpus.
- La résolution complète des variantes (flavors × buildTypes) au-delà du défaut.

### B13 Android Lint sur Kotlin

**Comportement de la référence**

- Lint opère sur UAST, une couche d'abstraction au-dessus de l'analyse ; en mode K2, UAST est reconstruit sur la nouvelle analyse `[À CAPTURER]`.
- Contrôles à valeur pour un développeur Kotlin : NewApi (API requise au-dessus du minSdk), ResourceType (identifiants et types de ressources valides), WrongThread (API exigée sur un thread, appelée depuis un autre), Recycle (ressources à libérer explicitement) `[À CAPTURER]`.
- Besoin sémantique de chacun : NewApi = annotations `@RequiresApi` + niveau d'API du modèle de build ; ResourceType = validité de `R` (portée + synthétiques) ; WrongThread = annotations de thread + contexte de l'enclos (déclarations brutes + corps borné) ; Recycle = flot de données (inférence complète) `[À CAPTURER]`.
- Lint s'exécute à la demande (analyse dédiée), pas à la frappe ; ses résultats sont intégrés à l'éditeur `[À CAPTURER]`.
- `@SuppressLint` éteint localement un contrôle Lint, à côté de `@Suppress` pour le compilateur et les inspections `[À CAPTURER]`.

**Implications pour code-lsp**

- NewApi et ResourceType sont servables à palier 2 : lecture d'annotations (B14), minSdk du modèle projet (B12), `R` prouvé par ClassesSynthetiques. WrongThread : sous-ensemble borné (annotations de l'enclos). Recycle : non — inférence complète.
- Message structurel : la référence sépare le rouge à la frappe (compilateur, A3) des contrôles à valeur (Lint, à la demande). Notre équivalent : DiagnosticsSemantiques bornés à la frappe + contrôles étendus à la demande — modèle à définir en ADR 0113 (deux natures de diagnostics dans la même publication LSP, ou commande dédiée).
- Leçon UAST : une couche d'abstraction au-dessus de l'analyse pour écrire des contrôleurs indépendants du langage. Notre couche équivalente est l'arbre + TypageBorne ; y attacher des contrôleurs non prouvés est interdit par « preuve ou silence ».
- CapacitesServeur : les résultats Lint peuvent passer par la publication de diagnostics existante — les 19 capacités suffisent, pas de capacité dédiée requise.

**Ce qui ne se porte pas**

- Le catalogue Lint complet (des centaines de règles) : sélection par valeur pour le corpus uniquement.
- Recycle et toutes les analyses de flot de données.

### B14 Annotations exploitées

**Comportement de la référence**

- `@NonNull`/`@Nullable` (androidx) : raffinent la nullabilité des types plateformes Java et alimentent les diagnostics de nullabilité côté appelant `[À CAPTURER]`.
- `@RequiresApi` : fonde NewApi et propose l'encadrement par une garde de version (quick-fix) `[À CAPTURER]`.
- `@IntDef`/`@StringDef` : restreignent la complétion aux constantes nommées et contrôlent les valeurs passées `[À CAPTURER]`.
- `@MainThread`/`@WorkerThread` : fondent WrongThread et orientent la complétion contextuelle `[À CAPTURER]`.
- `@Composable` : définit le contexte de composition (B10) `[À CAPTURER]`.
- `@Suppress`/`@SuppressLint` : extinction locale des diagnostics, inspections et contrôles Lint, avec portées fines `[À CAPTURER]`.

**Implications pour code-lsp**

- `@IntDef`/`@StringDef` : l'exemple parfait d'une complétion bornée dérivable d'annotations — CONSTANTES_ENUM (genre servi) s'étend naturellement aux constantes listées dans la définition de l'annotation : lecture de déclarations brutes, palier 1, candidat ADR 0113.
- `@NonNull`/`@Nullable` : le rendement maximal par palier — à palier 2, LecteurDeclarationsJava lit ces annotations et TypageBorne en tire une nullabilité à deux états (nullable / non nullable), sans arguments génériques : une grande partie de la valeur de la nullabilité pour le prix d'une annotation lue. Les lignes nullabilité du tableau A3 se re-dérivent alors partiellement en palier 2.
- `@RequiresApi` + minSdk du modèle projet (B12) : diagnostic NewApi borné, palier 2.
- `@Suppress` : DiagnosticsSemantiques doit l'honorer (portée de l'annotation contre code du diagnostic) — palier 1, candidat ADR 0113.
- `@MainThread`/`@WorkerThread` : symétrique des contextes suspend/composable (contexte d'enclos) — sous-ensemble borné palier 2.

**Ce qui ne se porte pas**

- La nullabilité sensible au flot (inférence complète) et les contrats personnalisés.
- Les familles d'annotations au-delà de la liste ci-dessus : chaque ajout passe par ADR 0113.

---

## §C Où surpasser, où l'on ne prétend pas surpasser

### Les cinq axes réalistes de surpassement

**1. Lecture disque avant première sync, après build cassé.**

- La référence attend sync/build pour les classes de binding (B8), les accesseurs DSL (B12) et le code généré (B9) `[À CAPTURER]` ; le moteur lit `res/`, le manifeste, le DSL et le TOML directement.
- Synthétiques prouvés avant build (ClassesSynthetiques), effacés devant la classe réelle : la vérité ne se perd pas quand le build se casse — elle se dégrade honnêtement vers la prédiction déclarée.
- Valeur concrète : premier ouvrage du projet sans rouge transitoire, y compris sur une machine sans chaîne de build — l'appareil lui-même.

**2. Réponses annulables, jamais périmées, p99 régulier.**

- Un IDE à index peut servir des réponses fondées sur un cache en cours d'invalidation (dumb mode, A7) `[À CAPTURER]` ; le moteur recalcule ou se tait — OccurrencesProuvees ne répond jamais depuis une donnée périmée.
- Le re-parse à 8,6 ms p50 (3 000 lignes) rend le recalcul systématique abordable : cibles de la mission — complétion/survol/signature < 100 ms p95 perçue, diagnostics ~300 ms.
- La régularité du p99 est un attribut d'architecture (pas d'index, pas de pics d'invalidation, pas de tâche de fond) — à tenir par mesure au banc, pas par promesse.

**3. Démarrage et mémoire.**

- Dex du moteur : 4,10 Mo, 26 590 méthodes (plafonds 32 768 méthodes / 6 Mo), lancement quasi immédiat — contre un IDE de bureau en gigaoctets (Go) de mémoire et des sync en minutes `[À CAPTURER]` pour les ordres de grandeur côté IDE.
- La disqualification du K2 embarqué est mesurée (53,6 Mo / 64 507 méthodes / ~2 170 motifs sensibles ART / 3 503 ms d'amorçage) : l'IDE ne tient pas sur l'appareil, le moteur oui — c'est la raison d'être du projet.

**4. Le décidable Compose/DSL/coroutines servi instantanément, le reste différé honnêtement.**

- Servis dès qu'ils sont prouvables : synthétiques (R/ViewBinding/BuildConfig), contexte composable et suspend (palier 2), accesseurs DSL sans sync, paramètres nommés, constantes d'annotation, membres manquants.
- Le reste (lignes « palier 3 » du tableau A3) est absent *et déclaré* : INCONNU = liste vide, codes manquants documentés. La référence a des trous accidentels `[DÉRIVÉ DES SOURCES]` (limitations avouées du mode K2) ; nous avons des trous volontaires et listés.

**5. Cohérence Kotlin ↔ Java ↔ ressources sans build.**

- Un seul modèle de déclarations (Projet | Jvm(fqcn)) alimenté par l'arbre .kt/.kts, LecteurDeclarationsJava borné et le DOM res/+manifeste.
- La cohérence est celle de la lecture, pas celle d'un build réussi : elle ne se casse pas quand le build se casse — elle reflète le disque.

### Non-dépassements assumés (à écrire noir sur blanc)

- Le catalogue d'inspections/intentions/refactorings accumulé (A4, A5) : le moteur offre ses familles d'actions (imports, accès non sûr, conversions de littéral, membres manquants) ; l'écart est assumé, la sélection se fait par valeur corpus via ADR 0113.
- La fidélité compilateur complète par frappe : disqualifiée par mesure (S1 : 53,6 Mo / 64 507 méthodes / ~2 170 motifs sensibles ART / 3 503 ms) ; le palier 3 reste l'oracle du banc de fidélité, jamais embarqué. Notre rouge ne prétend pas à la complétude — « preuve ou silence » est le contrat.
- La complétion apprise : pesons statistiques et historique d'usage — absents et non prévus (pas d'index d'usages, pas de télémétrie).
- Le rendu riche : KDoc rendu avec liens suivables, previews, coloration par symbole — parmi les 7 capacités absentes de CapacitesServeur ; cela restera tant que les phases de types ne sont pas embarquées, et l'absence est documentée.

### Stratégie « 20 % des comportements qui font 80 % de la valeur »

- Le tableau A3 et les §B fournissent la liste des comportements ; le tri se fait sur deux critères : *décidable sans inférence complète* et *fréquence sur le corpus du propriétaire* (Room, Hilt, Compose, DSL).
- Le 20 % qui en sort : synthétiques avant build (R, ViewBinding, BuildConfig et patrons nommés Room/Hilt), imports (ajout/suppression/tri), contexte suspend et composable, nullabilité à deux états par annotations, `kt.argumentCount`/`kt.typeMismatch` bornés, membres manquants, ARGUMENTS_NOMMES, constantes d'annotation, rename .kt/.kts, occurrences prouvées.
- Tout le reste attend son ADR — ou n'arrivera pas, et c'est documenté : la frontière est un livrable, pas un échec.

---

## §D Sources consultées

| Source | Nature | Ce qui en est tiré |
|---|---|---|
| Blog JetBrains, « K2 Mode in IntelliJ IDEA 2025.1: Current State and FAQ », 15/04/2025 — https://blog.jetbrains.com/idea/2025/04/k2-mode-in-intellij-idea-2025-1-current-state-and-faq | Article de blog de l'éditeur | Mode K2 par défaut dans IDEA 2025.1 ; moteur d'analyse « reconstruit, plus rapide et plus scalable » ; Analysis API comme accès stable indépendant des internes du compilateur ; limitations avouées (smart completion, problèmes occasionnels d'analyse/coloration, vitesse de complétion réduite sur certains projets) ; annonce du défaut K2 dans Android Studio « plus tard dans l'année ». |
| Notes de version d'Android Studio Narwhal Feature Drop 2025.1.2, juillet 2025 — https://developer.android.com/studio/releases/past-releases/as-narwhal-feature-drop-release-notes | Release notes officielles | « K2 mode by default : Android Studio now uses the K2 Kotlin compiler by default » — borne la mise par défaut du mode K2 côté Android Studio. |
| Page des versions d'Android Studio (consultée octobre 2026) — https://developer.android.com/studio/releases | Documentation officielle | Version stable courante : Android Studio Rabbit 2 (2026.2.2). |
| Guide de migration vers le compilateur K2 — https://kotlinlang.org/docs/k2-compiler-migration-guide.html | Documentation officielle Kotlin | « IntelliJ IDEA et Android Studio utilisent le compilateur K2 pour analyser votre code Kotlin » — fondement du rouge par frappe (A3). |
| Analysis API, fondamentaux — https://kotlin.github.io/analysis-api/fundamentals.html | Documentation officielle de l'API | KaSession point d'entrée, une session par module ; `analyze(element)` sous read action ; entités KaLifetimeOwner valides seulement dans la read action ; KaSymbolPointer/KaTypePointer ; API « implémentée au-dessus du Kotlin PSI » (`ktExpression.expressionType`, `ktCallExpression.resolveSuccessfulCall()`). |
| ADR 0103 « Stratégie sémantique Kotlin » (dépôt) | Décision d'architecture interne | Cadre sémantique général du moteur mobilisé en §A (choix de la voie d'analyse, frontières). |
| ADR 0107 « Typage et inférence bornés » (dépôt) | Décision d'architecture interne | TypageBorne (`Projet | Jvm(fqcn)`), smart casts positionnels — référence des limites citées en §A1, A2, A3, B14. |
| ADR 0112 « Diagnostics, fidélité et mode profond » (dépôt) | Décision d'architecture interne | Politique « preuve ou silence », périmètre des 10 codes kt.* — fondement des colonnes « statut moteur » du tableau A3. |
| ADR 0113 « Complétion et classement » (dépôt) | Décision d'architecture interne | Chaîne de classement invoquée dans chaque section « Implications » : critère d'entrée des comportements, palier par palier. |
| Fiche 017 (cahier de spécifications Kotlin, dépôt) | Fiche de garde | Garde par déclaration — granularité d'invalidation et de disponibilité citée en A1, A4, B9. |
| Journaux de mesure (dépôt) | Mesures internes | Parse 1,8 ms p50 (500 lignes, JVM) ; re-parse 3 000 lignes à 8,6 ms p50 ; cibles < 100 ms p95 perçue / ~300 ms diagnostics / p99 régulier. |
| Banc de fidélité (dépôt) | Mesures internes + oracle | Disqualification du K2 embarqué : 53,6 Mo / 64 507 méthodes / ~2 170 motifs sensibles ART / 3 503 ms d'amorçage ; dex moteur 26 590 méthodes / 4,10 Mo (plafonds 32 768 / 6 Mo) ; rôle d'oracle du palier 3 (A3, §C). |
| Documents 05 (fiches KC-01 à KC-12) et 06 (protocole de captures) du présent niveau (dépôt) | Documents internes de la série | Destinataires des statuts `[À CAPTURER]` : fiches de comportement et protocole de vérification sur instance réelle. |

**Rappel clean-room.** Les sources publiques ci-dessus ont été consultées en lecture seule ; aucun code, testData, identifiant interne de diagnostic ni algorithme n'a été copié. Les libellés du tableau A3 sont des formulations françaises descriptives écrites pour ce document, pas des reprises. Les fragments Kotlin du document sont écrits pour lui. Aucune instance d'Android Studio n'ayant été exécutée (avertissement E-K2), tout comportement marqué `[À CAPTURER]` attend le protocole du document 06 avant d'être opposé au moteur dans un arbitrage.

