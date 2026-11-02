# Fiche 020 — MembresManquantsTest

> **Source** : `codeide-lsp` @ `bc11253` — `lsp/kotlin/src/test/kotlin/jo/lspkotlin/session/MembresManquantsTest.kt` (10 tests, 319 l.).
> **Domaine** : diagnostics
> **Résumé** : Tests du quick-fix « IMPLÉMENTER LES MEMBRES MANQUANTS » (chantier P1-C, ADR 0229) — par le SPI (chemin de production D6) : la clôture des supertypes (PROJET via le port, CLASSPATH via la vue JVM), la substitution des paramètres de type, l'appariement getters/setters en propriétés, la garde générique (attribut `Signature` du bytecode) et les exclusions du corps — chaque test éprouve AUSSI la borne
> **Convention** : chaque comportement porte le **nom exact du test source** (identifiant de traçabilité) ; l'attendu le reformule en français lisible, enrichi du contrat D3 quand le nom le porte. Le résumé ci-dessus décrit le contrat de la classe de test.

## Comportements

### `membres-manquants---interface-du-projet,-corps-vide-remplace`

- **Attendu** : membres manquants : interface du projet, corps vide remplace.
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `membres-manquants---tout-implemente,-correctif-absent-(D3)`

- **Attendu** : membres manquants : tout implemente, correctif absent (D3) — borne conservatrice D3..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `membres-manquants---implementation-partielle,-insertion-apres-le-dernier-membre`

- **Attendu** : membres manquants : implementation partielle, insertion apres le dernier membre.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `membres-manquants---parametres-de-type-substitues-par-la-clause-ecrite`

- **Attendu** : membres manquants : parametres de type substitues par la clause ecrite.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `membres-manquants---clause-nue-sur-type-generique,-membre-muet-(D3)`

- **Attendu** : membres manquants : clause nue sur type generique, membre muet (D3) — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `membres-manquants---corps-absent,-corps-cree-en-fin-d'en-tete`

- **Attendu** : membres manquants : corps absent, corps cree en fin d'en tete.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `membres-manquants---objet-implementant-l'interface`

- **Attendu** : membres manquants : objet implementant l'interface.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `membres-manquants---cible-interface-ou-classe-abstraite,-action-absente-(D3)`

- **Attendu** : membres manquants : cible interface ou classe abstraite, action absente (D3) — borne conservatrice D3..
- **Classement** : conforme (borne conservatrice « preuve ou silence »)

### `membres-manquants---membre-concret-du-supertype,-jamais-collecte`

- **Attendu** : membres manquants : membre concret du supertype, jamais collecte.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `membres-manquants---interface-du-classpath,-methode-et-proprietes-appariees`

- **Attendu** : membres manquants : interface du classpath, methode et proprietes appariees.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)
