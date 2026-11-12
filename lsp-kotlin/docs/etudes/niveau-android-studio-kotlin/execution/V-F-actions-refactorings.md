# Journal d'exécution — Vague V-F : Actions & refactorings (P2, ADR 0131)

> Vague V-F du [plan](../04-plan-vagues.md) — exécutée le **12/11/2026**.
> Périmètre : `source.organizeImports` (KC-10), extraction de VARIABLE
> bornée (KC-11 Q1/Q5), kinds LSP exacts, `codeAction/resolve`. Les
> refactorings BORNÉS restants (extraction de FONCTION, inline, safe
> delete, expression-body↔block, if↔when) sont REPORTÉS (bornologie §3).

## 1. Ce qui est livré

1. **`source.organizeImports`** (KC-10 — `ActionsSource.organiserImports`)
   : remplace le bloc d'imports par sa forme organisée — TRI stable
   (alphabétique par chemin qualifié, alias et jokers à leur place de
   tri), RETRAIT des inutilisés PROUVÉS LEXICALEMENT (le nom effectif
   absent des identifiants du document ENTIER, miroir de
   `DiagnosticsImports`), FUSION des doublons EXACTS (chemin+alias). Le
   plan N'EXISTE que si le texte CHANGE (idempotence I1 : la seconde
   passe ne produit RIEN).
2. **L'extraction de VARIABLE** (KC-11 Q1/Q5 —
   `ActionsSource.extraireVariable`, kind `refactor.extract`) :
   l'expression englobante du caret (appel, binaire, sélecteur — la plus
   profonde) devient `val extrait… = <expression>` insérée AVANT
   l'instruction porteuse, l'expression est REMPLACÉE par le nom LIBRE
   (premier `extrait`/`extrait2`… absent du document). Bornes Q5 :
   INTRA-FONCTION seul (l'instruction porteuse vit dans le BLOCK d'un
   FUN), PAS d'extraction d'une expression-instruction entière.
3. **Les kinds LSP exacts** (KC-11 Q4) : `quickfix` (correctifs),
   `source.organizeImports` (organisation), `refactor.extract`
   (extraction) — le moteur ne ment jamais sur le kind ; les kinds sont
   ANNONCÉS dans les options codeAction (avec `quickfix` historique).
4. **`codeAction/resolve`** (ADR 0131) : l'action rendue par `codeAction`
   est DÉJÀ COMPLÈTE (les plans se re-prouvent à la requête, ADR 0115 —
   un plan périmé n'existe pas) ; le resolve est l'IDENTITÉ (stable par
   contrat), le `resolveProvider` est annoncé.
5. **Le branchement session** : les deux familles s'ajoutent aux plans
   existants (imports, membres manquants, correctifs texte) — calcul PUR
   sous verrou de lecture, jamais une action inventée (D3).

## 2. Les preuves

| Preuve | Résultat |
|---|---|
| Tests moteur (`ActionsSourceKotlinTest`) | **7 verts** — I1/I2 (tri stable, inutilisé prouvé retiré, idempotence zéro-delta), I4/I5 (doublon fusionné, alias TEL QUEL, joker conservé, conflit conservé, inutilisé non aliasé retiré), I2 bornes (nom écrit non résolu = conservation), Q1/Q5 (val insérée + remplacement, hors-fonction = pas de plan), cycle P5 ×2 (aucune nouvelle erreur, `kt.unusedImport` disparaît) |
| Cas doré `matrice-paritaire` | kinds exacts QuickFix + SourceOrganizeImports + RefactorExtract + resolveProvider annoncé |
| Suite `:kotlin:test` | **593 verts** (586 + 7), 3 ignorés (gates corpus) |
| Re-signature du digeste (E-K9) | COMMANDE OFFICIELLE fiche 010 ; diff = `ActionsSource.kt` + `ActionsSourceKotlinTest.kt` neufs + 5 resynchronisés — aucune re-sync amont |
| Régression | aucune — ActionsDeclarees/fiches 016/020/047 inchangés |

## 3. Bornologie (honnêteté D3)

- **REPORTÉS** (périmètre borné de la session) : extraction de FONCTION
  (paramètres = identifiants libres — analyse de flux), inline, safe
  delete (par usages prouvés multi-fichiers), expression-body↔block,
  if↔when, `x?.let { }`, gabarit de chaîne, `!!`, nommage d'argument,
  `run`/`with` — le catalogue KC-11 Q1 reste la spécification ;
- le retrait d'import est LEXICAL (document entier — jamais la preuve
  de liaison multi-fichiers : un import posé par la complétion pour un
  symbole pas encore inséré est CONSERVÉ, I3) ;
- l'organisation ne normalise PAS les lignes vides intermédiaires du
  bloc (le bloc entier est réécrit d'un seul tenant — la règle de tri
  exacte de la référence reste `[À CAPTURER]`, I1) ;
- l'extraction ne capture PAS les identifiants libres (le corps extrait
  reste une expression PURE — l'analyse de flux relève de l'extraction
  de fonction).
