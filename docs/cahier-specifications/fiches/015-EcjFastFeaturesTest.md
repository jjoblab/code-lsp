# Fiche 015 — EcjFastFeaturesTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/ecj/EcjFastFeaturesTest.java`.
> **Domaine** : caches-perf
> **Résumé** : Extension du fast-path P6 aux features par-frappe (v1.6.0) : **hover** et **signature help** servis sans re-parse depuis l'arbre focalisé que le diagnose rapide a déjà résolu — chaîne à 3 niveaux : arbre focalisé retenu → arbre réel du parseCache → réduction SourceFocuser (v1.5). Oracle d'équivalence stricte contre un moteur témoin NEUF (aucun mémo, aucun arbre retenu) : le fast-path ne peut que gagner du temps, jamais changer une réponse (y compris null). Compteurs : `fastResolveHits` prouve le service par l'arbre focalisé, `cachedResolveHits` par l'arbre réel du parseCache. Fixture `Main` (package `app`, marqueur « ⌂ ») avec méthodes `add`/`join`/`edite`/`autre`/`bourre` ; variante ≥ 2000 caractères (70 lignes de bourrage) activant la réduction SourceFocuser côté témoin. Le kill-switch P6 coupe aussi `resolveUnitFast`.

## Comportements

### hoverDansCorpsEdite_serviParArbreFocalise — hover dans le corps hôte
- **Préconditions / entrée** : didChange + diagnose initial ; didChange + diagnose de la frappe ` int somme = add(total, 2);` (frappe confinée → fast-path) ; hovers sur « somme », « add( » et « total » (argument) dans le corps édité.
- **Comportement attendu** : réponse identique au témoin aux 3 points ; fastDiagnoseHits = 1 ; fastResolveHits ≥ 1 ; cachedResolveHits = 0 (le cache est vide après un diagnose RAPIDE).
- **Bornes** : 3 points de hover ; fastResolveHits ≥ 1 ; cachedResolveHits = 0.
- **Statut** : actif

### hoverHorsCorpsHote_repliSansArbreFocalise — hover hors corps hôte : garde d'offset
- **Préconditions / entrée** : même scénario ; hover sur « mot.toUpperCase » (corps masqué `autre`), puis sur « first + second » (paramètre de `join()`, autre corps masqué).
- **Comportement attendu** : réponses identiques au témoin ; fastResolveHits = 0 (l'arbre focalisé ne doit JAMAIS servir un offset hors du corps hôte) ; premier hover : cachedResolveHits = 0 (repli par chemin réduit, qui REMPLIT le cache) ; second : cachedResolveHits = 1 (servi par l'arbre réel du parseCache).
- **Bornes** : fastResolveHits = 0 ; cachedResolveHits 0 puis 1.
- **Statut** : actif

### contenuDifferent_arbrePerimeJamaisServi — garde de contenu
- **Préconditions / entrée** : après le diagnose de s1 (`int somme`), frappe suivante s2 SANS diagnose (debounce non tiré) : `String somme = "a";` (changement volontaire de TYPE de la locale) ; hover sur « somme ».
- **Comportement attendu** : réponse = témoin, contient « String » (le NOUVEAU type) ; ne contient JAMAIS « int somme » (ce qu'aurait répondu l'arbre périmé) ; fastResolveHits = 0 (la garde de contenu rejette avant tout comptage).
- **Bornes** : —
- **Statut** : actif

### signatureHelpDansCorpsEdite_serviParArbreFocalise — signature help dans le corps hôte
- **Préconditions / entrée** : même scénario ; caret juste après la virgule de « add(total, 2) » dans le corps édité.
- **Comportement attendu** : équivalence stricte avec le témoin (labels, noms de paramètres, documentation, signature active, paramètre actif) ; fastResolveHits ≥ 1 ; activeParameter = 1 (deuxième argument en saisie) ; le label de la signature active contient « add ».
- **Bornes** : activeParameter = 1.
- **Statut** : actif

### signatureHelpHorsCorpsHote_repli — signature help hors corps hôte
- **Préconditions / entrée** : caret dans « add(total, 7) » vivant dans le corps masqué `autre`.
- **Comportement attendu** : équivalence stricte ; fastResolveHits = 0 (aucun service par l'arbre focalisé hors du corps hôte).
- **Bornes** : fastResolveHits = 0.
- **Statut** : actif

### apresDiagnoseComplet_featuresParArbreReel — après diagnose complet : arbre réel
- **Préconditions / entrée** : didChange + PREMIER diagnose (complet, jamais fast) de la source avec frappe ; hover sur « somme » et « mot.toUpperCase » (corps édité ET masqué), signature help sur « add(total, ».
- **Comportement attendu** : fastDiagnoseHits = 0, fullDiagnoseCount = 1 ; réponses identiques au témoin partout (l'arbre réel est fidèle sur tout le fichier) ; fastResolveHits = 0 ; cachedResolveHits ≥ 3 (hover×2 + signature help servis par l'arbre réel du parseCache — probe pure, zéro re-parse).
- **Bornes** : cachedResolveHits ≥ 3.
- **Statut** : actif

### grosFichier_arbreFocaliseContreSourceReduite — gros fichier : isomorphe contre réduit
- **Préconditions / entrée** : fixture bigSrc (≥ 2000 caractères, 70 lignes de bourrage) ; diagnose initial puis frappe (fast-path) ; hover « somme » + signature help.
- **Comportement attendu** : précondition : bigSrc.length() ≥ 2000 ; fastDiagnoseHits = 1 (frappe confinée → diagnose rapide même sur gros fichier) ; équivalence stricte entre le moteur (arbre focalisé ISOMORPHE, positions réelles) et le témoin (source RÉDUITE par splices SourceFocuser, offsets translatés) ; fastResolveHits ≥ 2.
- **Bornes** : fichier ≥ 2000 caractères (seuil d'activation de la réduction) ; fastResolveHits ≥ 2.
- **Statut** : actif

### sessionMultiTours_reponsesToujoursEquivalentes — session 4 tours sans fuite d'arbre périmé
- **Préconditions / entrée** : tour 0 : diagnose initial ; tour 1 : frappe ` int somme = add(total, 2);` ; tour 2 : `add(total, 5)` ; tour 3 : `long somme = 9L;` (changement de TYPE + suppression de l'appel). Chaque tour = didChange + diagnose + hover + signature help ; au tour 3, hover AVANT le diagnose.
- **Comportement attendu** : à chaque tour, équivalence stricte hover ET signature help ; tour 3 : hover AVANT diagnose = témoin (contient « long », jamais « int somme » — l'arbre du tour 2 évincé par la clé de contenu) ; signature help null des deux côtés au tour 3 (plus d'appel sous le caret) ; fastDiagnoseHits atteint 1 puis 2 puis 3.
- **Bornes** : 4 tours ; fastDiagnoseHits 1→2→3.
- **Statut** : actif

## Notes pour la réécriture
- Chaîne de service ordonnée à trois niveaux : (1) arbre focalisé — double garde : offset DANS le corps hôte + contenu identique ; (2) arbre réel du parseCache (fidèle partout, rempli par un diagnose complet ou par un repli) ; (3) réduction SourceFocuser par splices avec translation d'offsets.
- `didChange` est simulé par `engine.invalidateParseCache(chemin)` — le flux LSP invalide le cache à chaque édition ; la garde de contenu (clé = contenu source) protège contre les arbres périmés entre deux debounces.
- Compteurs `fastResolveHits`/`cachedResolveHits` indispensables : sans eux l'équivalence passerait « à vide » par simple repli — la réécriture doit exposer des équivalents.
- Seuil 2000 caractères : en dessous, la réduction SourceFocuser est l'identité (pas de réduction) — le test croise volontairement les deux transformations (isomorphe vs réduite) sur ce seuil.
- Anomalie de nommage : la classe interne s'appelle `JdtFastFeaturesTest` alors que le fichier est `EcjFastFeaturesTest.java` (reliquat du portage jdt→ecj).
- Le kill-switch P6 (`-Djo.lspjava.ecj.fastdiagnose.disabled`) coupe aussi `resolveUnitFast` — contrat couvert par `EcjFastDiagnoseTest` (fiche 014).
