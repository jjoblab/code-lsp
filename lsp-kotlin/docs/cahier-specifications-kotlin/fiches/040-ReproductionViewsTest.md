# Fiche 040 — ReproductionViewsTest

> **Source** : `codeide-lsp` @ `bc11253` — `lsp/kotlin/src/test/kotlin/jo/lspkotlin/session/ReproductionViewsTest.kt` (11 tests, 490 l.).
> **Domaine** : diagnostics
> **Résumé** : REPRODUCTION du monde NON-Compose (vues XML / AppCompat) au niveau MOTEUR — ADR 0219 : deux découvertes du template « Empty Views Activity » (rapport d'audit §14) : - **chaîne COUPÉE** : la borne de profondeur 6 de `ResolutionKotlin.chaineSupertypes` s'arrêtait AVANT `android.content.Context` — la hiérarchie Android réelle (AppCompatActivity → … → ContextWrapper → Context) exige PROFONDEUR 7 : `ge
> **Convention** : chaque comportement porte le **nom exact du test source** (identifiant de traçabilité) ; l'attendu le reformule en français lisible, enrichi du contrat D3 quand le nom le porte. Le résumé ci-dessus décrit le contrat de la classe de test.

## Comportements

### `getString-herite-du-maillon-7---Context---non-signale`

- **Attendu** : getString herite du maillon 7 : Context : non signale — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `layoutInflater-propriete-synthetique---non-signale`

- **Attendu** : layoutInflater propriete synthetique : non signale — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `title-affectation-nue-via-setter-synthetique---non-signale`

- **Attendu** : title affectation nue via setter synthetique : non signale — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `packageManager-var---getter-ET-setter---non-signale`

- **Attendu** : packageManager var : getter ET setter : non signale — silence conservateur D3 (aucune émission hors preuve)..
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `typo-d-une-propriete-synthetique-reste-signalee`

- **Attendu** : typo d une propriete synthetique reste signalee — l'émission est attendue (preuve d'absence/erreur établie)..
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `getter-A-PARAMETRE-ne-synthetise-pas-de-propriete`

- **Attendu** : getter A PARAMETRE ne synthetise pas de propriete.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `survol-implicite-de-layoutInflater---val-avec-proprietaire`

- **Attendu** : survol implicite de layoutInflater : val avec proprietaire.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `survol-implicite-de-getString---fun-avec-proprietaire-Context`

- **Attendu** : survol implicite de getString : fun avec proprietaire Context.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `survol-qualifie-d-une-propriete-synthetique---this-layoutInflater`

- **Attendu** : survol qualifie d une propriete synthetique : this layoutInflater.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)

### `typage---layoutInflater-inflate-chaine-membre-apres-propriete-synthetique`

- **Attendu** : typage : layoutInflater inflate chaine membre apres propriete synthetique.
- **Classement** : heuristique (règle maison, sous-ensemble de la spécification)

### `completion-membre-apres-this-point---proprietes-synthetiques-offertes`

- **Attendu** : completion membre apres this point : proprietes synthetiques offertes.
- **Classement** : heuristique (règle maison — à confronter à K2 au banc)
