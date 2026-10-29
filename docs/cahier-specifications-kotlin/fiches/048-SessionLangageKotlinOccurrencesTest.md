# Fiche 048 — SessionLangageKotlinOccurrencesTest

> **Source** : `codeide-lsp` @ `bc11253` — `lsp/kotlin/src/test/kotlin/jo/lspkotlin/session/SessionLangageKotlinOccurrencesTest.kt` (21 tests, 519 l.).
> **Domaine** : navigation
> **Résumé** : Tests du contrat de session — OCCURRENCES (S2, plan de stabilisation LSP §3.2 : `references`, `documentHighlight`, rename borné miroir Java) — par le SPI (chemin de production D6), moteur RÉEL, projet multi-fichiers sur disque (racines sources du scan). Le cœur éprouvé : le conservatisme. Chaque référence NON prouvée (homonyme d'un autre paquet sans import, locale ombrageante, sélecteur qualifié s
> **Convention** : chaque comportement porte le **nom exact du test source** (identifiant de traçabilité) ; l'attendu le reformule en français lisible, enrichi du contrat D3 quand le nom le porte. Le résumé ci-dessus décrit le contrat de la classe de test.

## Comportements

### `references-locale---usages-du-fichier-déclarant-seul,-ombrage-exclu`

- **Attendu** : references locale : usages du fichier déclarant seul, ombrage exclu.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `references-paramètre---confiné-au-fichier,-usages-du-corps`

- **Attendu** : references paramètre : confiné au fichier, usages du corps.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `references-fonction-top-level---même-paquet,-autre-fichier-fermé-du-scan`

- **Attendu** : references fonction top level : même paquet, autre fichier fermé du scan.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `references-fonction-top-level---import-explicite-d'un-autre-paquet`

- **Attendu** : references fonction top level : import explicite d'un autre paquet.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `references-homonyme-sans-preuve---vide-(conservatisme-D3)`

- **Attendu** : references homonyme sans preuve : vide (conservatisme D3).
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `references-classe---import,-usage,-et-import-renommé-aussi`

- **Attendu** : references classe : import, usage, et import renommé aussi.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `references-membre-qualifié---récepteur-typé-prouve-la-cible`

- **Attendu** : references membre qualifié : récepteur typé prouve la cible.
- **Classement** : heuristique (règle maison, sous-ensemble de la spécification)

### `documentHighlight---déclaration-en-écriture,-références-en-lecture,-ombrage-exclu`

- **Attendu** : documentHighlight : déclaration en écriture, références en lecture, ombrage exclu.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `documentHighlight---curseur-sur-référence,-pas-sur-la-déclaration`

- **Attendu** : documentHighlight : curseur sur référence, pas sur la déclaration.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `references-fonction-top-level---usages-du-MEME-fichier-déclarant-(ADR-0204)`

- **Attendu** : references fonction top level : usages du MEME fichier déclarant (ADR 0204).
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `renommage-same-file---déclaration-ET-usages-du-fichier-déclarant-édités-(ADR-0204)`

- **Attendu** : renommage same file : déclaration ET usages du fichier déclarant édités (ADR 0204).
- **Classement** : heuristique (règle maison, sous-ensemble de la spécification)

### `references-propriété-de-constructeur---usage-non-qualifié-prouvé-(ADR-0206)`

- **Attendu** : references propriété de constructeur : usage non qualifié prouvé (ADR 0206).
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `renommage-propriété-de-constructeur---déclaration-ET-usage-édités-(ADR-0206)`

- **Attendu** : renommage propriété de constructeur : déclaration ET usage édités (ADR 0206).
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `plageRenommable---placeholder-exact-sur-le-nom-déclaré`

- **Attendu** : plageRenommable : placeholder exact sur le nom déclaré.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `plageRenommable---hors-identifiant,-null-(jamais-deviné)`

- **Attendu** : plageRenommable : hors identifiant, null (jamais deviné).
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `renommage---déclaration,-usages-et-import-multi-fichiers`

- **Attendu** : renommage : déclaration, usages et import multi fichiers.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `renommage---sans-cible-prouvée,-liste-vide-(l'hôte-en-fait-une-erreur-typée)`

- **Attendu** : renommage : sans cible prouvée, liste vide (l'hôte en fait une erreur typée) — réponse/effet vide, honnête..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `references---curseur-sur-le-mot-cle-fun,-la-declaration-englobante-est-visee-(ADR-0218)`

- **Attendu** : references : curseur sur le mot cle fun, la declaration englobante est visee (ADR 0218).
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `references---curseur-sur-le-mot-cle-class,-la-declaration-englobante-est-visee-(ADR-0218)`

- **Attendu** : references : curseur sur le mot cle class, la declaration englobante est visee (ADR 0218).
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `references---curseur-dans-le-CORPS-d'une-fonction,-AUCUNE-cible-(le-corps-n'est-pas-l'en-tête)`

- **Attendu** : references : curseur dans le CORPS d'une fonction, AUCUNE cible (le corps n'est pas l'en tête).
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `document-inconnu-et-session-fermée---listes-vides,-sans-exception`

- **Attendu** : document inconnu et session fermée : listes vides, sans exception.
- **Classement** : conforme (borne conservatrice « preuve ou silence »)
