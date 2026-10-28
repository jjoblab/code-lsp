# Fiche 045 — CompatShimsTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/ecj/compat/CompatShimsTest.java`.
> **Domaine** : shims-art
> **Résumé** : Filet de non-régression des shims ART (`ArtCompatVersion`, `ArtCompatInputStream`, `ArtCompatStackWalker`) — cibles des réécritures bytecode du jar ecJ (`:lsp:ecj-art`, traitement D3 lot 1, ADR 0110 §5.3), réécrits depuis la spécification JDK. Valide : l'ordre des versions (avec la divergence assumée « 1.8 » < « 9 » par rapport à `Runtime.Version.parse` qui normaliserait 1.8→8), le `toString` brut, le rejet des formats malformés, les sémantiques `readAllBytes`/`readNBytes` (y compris flux plus grand que le tampon interne) et `getCallerClass` (saute le shim puis la méthode invoquante).

## Comportements

### parsesAndOrdersVersions — ordre des versions
- **Préconditions / entrée** : chaînes de version « 8 », « 17 », « 21 », « 1.8 », « 9 », « 17.0.1 », « 17.0.2 ».
- **Comportement attendu** : ordre strictement numérique : « 8 » < « 17 » = « 17 » < « 21 » ; « 1.8 » < « 9 » (divergence assumée avec `Runtime.Version.parse`, conservée — suffisante pour comparer la version `-source` à la dernière supportée) ; ordre JEP 322 : « 17.0.1 » > « 17 » (éléments manquants valant 0) et « 17.0.2 » > « 17.0.1 ».
- **Bornes** : —
- **Statut** : actif

### toStringReturnsRawString — rendu brut
- **Préconditions / entrée** : « 17.0.1+8 » et « 1.8 ».
- **Comportement attendu** : `toString` rend la chaîne BRUTE d'origine (« 17.0.1+8 », « 1.8 ») — pas de normalisation.
- **Bornes** : —
- **Statut** : actif

### rejectsMalformedVersions — formats malformés rejetés
- **Préconditions / entrée** : « » (vide), « a.b », « 1..2 », `null`.
- **Comportement attendu** : `IllegalArgumentException` pour les trois chaînes malformées ; `NullPointerException` pour `null`.
- **Bornes** : —
- **Statut** : actif

### readAllBytesRoundTrips — aller-retour octets
- **Préconditions / entrée** : flux d'entrée contenant un texte UTF-8 avec caractères accentués (« Hello, CodeIDE! — éàü »).
- **Comportement attendu** : `readAllBytes` rend exactement les octets d'origine.
- **Bornes** : —
- **Statut** : actif

### readAllBytesOnEmptyStream — flux vide
- **Préconditions / entrée** : flux sans octet.
- **Comportement attendu** : rend un tableau de longueur 0.
- **Bornes** : —
- **Statut** : actif

### readNBytesLimits — bornes de readNBytes
- **Préconditions / entrée** : flux de 8 octets (1..8) ; N = 3, 100, 0 puis -1.
- **Comportement attendu** : N=3 → les 3 premiers octets ; N=100 (> taille) → tout le flux (8 octets) ; N=0 → tableau vide ; N=-1 → `IllegalArgumentException`.
- **Bornes** : flux de 8 octets ; N ∈ {3, 100, 0, -1}.
- **Statut** : actif

### readAllBytesHandlesLargeStreams — flux multi-tampons
- **Préconditions / entrée** : flux de 50 000 octets (motif i & 0xFF), au-delà de la taille de tampon interne du shim.
- **Comportement attendu** : `readAllBytes` rend l'intégralité des 50 000 octets à l'identique (le doublement de tampon fonctionne).
- **Bornes** : 50 000 octets ; tampon interne de 16 384 octets.
- **Statut** : actif

### callerClassSkipsShimAndInvoker — classe appelante
- **Préconditions / entrée** : un « porte intermédiaire » (classe interne au test) invoque `getCallerClass` avec l'option RETAIN_CLASS_REFERENCE, lui-même appelé par le test.
- **Comportement attendu** : la classe rendue est celle du TEST (le saut ignore le shim PUIS la méthode invoquante — deux cran d'écart).
- **Bornes** : —
- **Statut** : actif

### optionConstantsMirrorJdk — constantes d'option miroir du JDK
- **Préconditions / entrée** : énumération des options du StackWalker.
- **Comportement attendu** : exactement 3 valeurs d'option (comme le JDK — le patcher déplace les références `StackWalker$Option` par préfixe) ; `getInstance(option)` rend toujours un marcheur non null, quelle que soit l'option.
- **Bornes** : 3 options exactement.
- **Statut** : actif

## Notes pour la réécriture
- Les surfaces de ces shims doivent matcher EXACTEMENT ce qu'ecJ appelle (ce sont des cibles de réécriture bytecode) : conserver signatures et sémantiques à l'octet près.
- Divergence assumée et délibérée : l'ordre « 1.8 » < « 9 » (strictement numérique, sans normalisation 1.8→8) — ne pas « corriger » ce comportement lors de la réécriture.
- `readNBytes` : sémantique « au plus N » (jamais d'exception de fin de flux), N négatif rejeté ; `readAllBytes` doit absorber des flux de taille arbitraire (croissance de tampon).
- `getCallerClass` compte le shim ET l'invoquant comme frames à sauter : la classe rendue est celle de l'appelant de l'appelant.
