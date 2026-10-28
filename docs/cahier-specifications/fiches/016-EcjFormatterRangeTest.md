# Fiche 016 — EcjFormatterRangeTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/ecj/EcjFormatterRangeTest.java`.
> **Domaine** : formatage
> **Résumé** : Verrouille les éditions de formatage de PLAGE (`editionsRange`), substrat du handler LSP `textDocument/rangeFormatting`. Sémantique imposée : les éditions ne touchent QUE la plage demandée (l'unité entière est néanmoins parsée pour le contexte) ; elles voyagent en ordre CROISSANT d'offset et sont disjointes ; source imparsable → null ou no-op ; plage vide → liste vide ; plage pleine page équivaut au formatage intégral. Fixture unique : une classe `Ex` à deux méthodes volontairement mal formatées (`int x=1;` / `int y=2;` indentés à raz).

## Comportements

### editionsConfineesALaPlage — les éditions restent dans la plage demandée
- **Préconditions / entrée** : formateur niveau « 17 » ; source mal formatée ; plage = la méthode `b` entière (de « void b() » à son accolade fermante incluse), la méthode `a` restant hors plage.
- **Comportement attendu** : liste d'éditions non null et non vide ; chaque édition (offset + longueur) est entièrement contenue dans [début, fin] de la plage ; après application des éditions à la source, le code hors plage contient toujours « int x=1; » inchangé et le code dans la plage contient « int y = 2; » (formaté).
- **Bornes** : —
- **Statut** : actif

### editionsTrieesCroissantesEtDisjointes — offsets strictement croissants
- **Préconditions / entrée** : formateur « 17 » ; plage pleine page (offset 0, longueur = source entière).
- **Comportement attendu** : les offsets des éditions sont STRICTEMENT croissants (donc éditions disjointes), chaque longueur est ≥ 0, chaque offset ≤ longueur de la source.
- **Bornes** : —
- **Statut** : actif

### plageVideListeVide — plage vide → liste vide (jamais null)
- **Préconditions / entrée** : formateur « 17 » ; plage (offset 10, longueur 0).
- **Comportement attendu** : retour non null mais liste VIDE — une plage vide ne produit aucune édition.
- **Bornes** : —
- **Statut** : actif

### sourceImparsableNeMutilleJamais — imparsable → null ou aucune édition
- **Préconditions / entrée** : formateur « 17 » ; source « class {{{ » ; plage pleine page.
- **Comportement attendu** : `editionsRange` retourne null OU une liste vide ; `formatRange` retourne null OU la source strictement inchangée — jamais une version mutilée (contrat historique repris d'EcjFormatterTest).
- **Bornes** : —
- **Statut** : actif

### pleinePageEquivautAuFormatDocument — pleine page ≡ format intégral
- **Préconditions / entrée** : formateur « 17 » ; plage couvrant tout le document.
- **Comportement attendu** : appliquer les éditions de la plage pleine page produit EXACTEMENT le même texte que `format` sur la source entière.
- **Bornes** : —
- **Statut** : actif

### formatRangeResteCoherentAvecLesEditions — formatRange ≡ éditions appliquées
- **Préconditions / entrée** : formateur « 17 » ; plage = méthode `b` uniquement.
- **Comportement attendu** : `formatRange` rend le même texte que l'application (façon client LSP) des éditions de `editionsRange` sur la même plage ; le résultat contient « int x=1; » (hors plage intact) et « int y = 2; » (plage formatée).
- **Bornes** : —
- **Statut** : actif

## Notes pour la réécriture
- Le client LSP applique les éditions en ordre DÉCROISSANT d'offset (les offsets amont restent valides) ; le moteur émet en ordre croissant — la disjointness est ce qui rend ce protocole sûr.
- Le formateur parse l'unité ENTIÈRE pour disposer du contexte, mais ne doit émettre des éditions que dans la plage demandée.
- Contrat de sûreté transverse au formatage : une source irrécupérable ne produit jamais de texte déformé (null ou inchangé) — à reprendre tel quel en Kotlin.
- La distinction « liste vide » (plage vide, no-op explicite) vs « null » (imparsable, signal d'échec) est un invariant d'API à conserver.
