# Fiche 016 — CorrectifsTexteTest

> **Source** : `codeide-lsp` @ `bc11253` — `lsp/kotlin/src/test/kotlin/jo/lspkotlin/session/CorrectifsTexteTest.kt` (13 tests, 325 l.).
> **Domaine** : diagnostics
> **Résumé** : 
> **Convention** : chaque comportement porte le **nom exact du test source** (identifiant de traçabilité) ; l'attendu le reformule en français lisible, enrichi du contrat D3 quand le nom le porte. Le résumé ci-dessus décrit le contrat de la classe de test.

## Comportements

### `acces-non-sur---appel-sur,-assertion-non-nulle-et-entourage-multi-ligne`

- **Attendu** : acces non sur : appel sur, assertion non nulle et entourage multi ligne.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `acces-non-sur---instruction-partageant-sa-ligne,-entourage-en-ligne`

- **Attendu** : acces non sur : instruction partageant sa ligne, entourage en ligne.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `acces-non-sur---propriete-locale-entouree,-l'instruction-entiere`

- **Attendu** : acces non sur : propriete locale entouree, l'instruction entiere.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `acces-non-sur---premier-niveau-sans-bloc,-pas-d'entourage-(D3)`

- **Attendu** : acces non sur : premier niveau sans bloc, pas d'entourage (D3) — borne conservatrice D3..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `acces-non-sur---segment-imbrique,-le-point-du-segment-le-plus-profond`

- **Attendu** : acces non sur : segment imbrique, le point du segment le plus profond.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `acces-sur-ou-garde-de-nullite---aucun-correctif-(D3)`

- **Attendu** : acces sur ou garde de nullite : aucun correctif (D3) — borne conservatrice D3..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `diagnostic-perime---le-moteur-re-prouve,-rend-vide-(honnête)`

- **Attendu** : diagnostic perime : le moteur re prouve, rend vide (honnête) — réponse/effet vide, honnête..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `litteral-chaine-vers-Int---changement-de-type-et-conversion-toInt`

- **Attendu** : litteral chaine vers Int : changement de type et conversion toInt.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `cible-non-primitive---changement-de-type-seul-(D3)`

- **Attendu** : cible non primitive : changement de type seul (D3) — borne conservatrice D3..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `matrice-de-conversion---fermee-par-famille-(D3)`

- **Attendu** : matrice de conversion : fermee par famille (D3) — borne conservatrice D3..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `type-nullable-ou-conversion-legale---aucun-correctif-(D3)`

- **Attendu** : type nullable ou conversion legale : aucun correctif (D3) — borne conservatrice D3..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `alignement---un-correctif-si-et-seulement-si-le-diagnostic-correspondant`

- **Attendu** : alignement : un correctif si et seulement si le diagnostic correspondant.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `document-inconnu-ou-ferme---liste-vide-honnete`

- **Attendu** : document inconnu ou ferme : liste vide honnete — réponse/effet vide, honnête..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)
