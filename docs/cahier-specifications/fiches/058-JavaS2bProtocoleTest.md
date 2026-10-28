# Fiche 058 — JavaS2bProtocoleTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/lsp/JavaS2bProtocoleTest.java`.
> **Domaine** : cycle-vie-lsp
> **Résumé** : Lot S2-b « confort structurel restant » : protocole bout-en-bout à travers le serveur réel — `selectionRange` (chaîne strictement croissante), `onTypeFormatting` (« } » reformate le bloc fermé seul), `codeLens` (une lens par déclaration + `resolve` avec comptage de références), hiérarchies de types et d'appels (`prepareTypeHierarchy`/supertypes/subtypes, `prepareCallHierarchy`/incoming/outgoing, localisation SOURCE), `filterText` de complétion (I12) et garde lifecycle I2 (rejets avant initialize) — c'est cette exécution via le cycle de vie réel du serveur qui motive le domaine. Fixtures : classe `T` mal indentée, `Base`/`Appelant` (`b.utilise(3)`), `Base2`/`Enfant2` (héritage). Montage SANS classpath (fichiers sources écrits sur disque avant création du serveur) — aucun android.jar requis.

## Comportements

### capabilitiesS2bDeclarees — capabilities S2-b annoncées
- **Préconditions / entrée** : création des capabilities du serveur.
- **Comportement attendu** : `selectionRangeProvider` booléen TRUE (gauche) ; `documentOnTypeFormattingProvider` non-null avec premier déclencheur « } » et « ; » présent dans `moreTriggerCharacter` ; `codeLensProvider` non-null avec `resolveProvider` TRUE ; `callHierarchyProvider` booléen TRUE (gauche) ; `typeHierarchyProvider` booléen TRUE (gauche).
- **Bornes** : déclencheurs exacts « } » (premier) et « ; » (second).
- **Statut** : actif

### selectionRangeChaineCroissanteBoutEnBout — chaîne de plages strictement croissante
- **Préconditions / entrée** : `didOpen` de `demo/T.java` (source mal indentée) ; requête `selectionRange` avec UNE position (caret à l'intérieur de « int y=2; »).
- **Comportement attendu** : exactement 1 réponse (autant que de positions demandées) ; en descendant la chaîne `parent` : chaque plage est non vide et de longueur STRICTEMENT croissante ; la profondeur atteinte est ≥ 4 niveaux.
- **Bornes** : réponse en 5 s max ; ≥ 4 niveaux ; croissance stricte vérifiée niveau par niveau.
- **Statut** : actif

### onTypeFormattingAccoladeReformateLeBlocSeulement — « } » reformate le bloc seul
- **Préconditions / entrée** : `didOpen` de `demo/TF.java` ; requête `onTypeFormatting` avec ch « } » à la position juste après le « } » fermant la méthode `b()`.
- **Comportement attendu** : éditions non vides ; après application : « int y = 2; » (bloc `b`) est formaté et « int x=1; » (méthode `a`, hors bloc) reste intact.
- **Bornes** : réponse en 5 s max.
- **Statut** : actif

### onTypeFormattingCaractereEtrangerAucuneEdition — caractère inattendu → no-op
- **Préconditions / entrée** : requête `onTypeFormatting` avec ch « } » mais position 0 (le caractère à cet offset n'est pas « } »).
- **Comportement attendu** : liste d'éditions non-null mais VIDE (aucune action).
- **Bornes** : —
- **Statut** : actif

### codeLensParDeclarationResolveCompte — lens par déclaration, resolve paresseux
- **Préconditions / entrée** : serveur ISOLÉ (aucun autre fichier du projet) ; `didOpen` de `demo/CLBase.java` (classe + méthode `utilise`) ; requête `codeLens` puis `resolveCodeLens` sur la lens de la méthode (ligne ≥ 3).
- **Comportement attendu** : exactement 2 lenses (une par déclaration : classe + méthode) ; la lens brute n'a PAS encore de commande (resolve paresseux) ; la lens résolue porte une commande dont le titre est exactement « 0 référence » (aucune référence dans le projet isolé).
- **Bornes** : 2 lenses exactement ; titre exact « 0 référence » ; requêtes en 5 s max.
- **Statut** : actif

### codeLensCompteLesReferencesMultiFichiers — comptage multi-fichiers, déclaration exclue
- **Préconditions / entrée** : serveur isolé ; `demo/Base.java` et `demo/Appelant.java` AUSSI sur disque (noms canoniques) ; `Base` OUVERT, `Appelant` seulement sur disque ; lens de `utilise` (ligne ≥ 3) résolue.
- **Comportement attendu** : titre exactement « 1 référence » — l'appel `b.utilise(3)` du fichier non ouvert est compté (le balayage consulte l'overlay ouvert PUIS le disque), la déclaration est exclue du compte.
- **Bornes** : titre exact « 1 référence » ; resolve en 10 s max.
- **Statut** : actif

### typeHierarchyPrepareSupertypesSubtypes — prepare/supertypes/subtypes
- **Préconditions / entrée** : fichiers `Base2.java` et `Enfant2.java` écrits et ouverts ; `prepareTypeHierarchy` sur « Base2 » dans la clause « extends Base2 » (fichier Enfant2) ; puis prepare sur « Enfant2 » (sa propre déclaration) → `typeHierarchySupertypes` ; et `typeHierarchySubtypes` de l'élément Base2 préparé.
- **Comportement attendu** : le prepare sur la référence « extends Base2 » rend 1 élément « Base2 » localisé dans SA source (`Base2.java`) ; supertypes de Enfant2 = exactement 1 (« Base2 ») ; subtypes de Base2 = exactement 1 (« Enfant2 », par balayage des racines sources).
- **Bornes** : 1 élément / 1 supertype / 1 subtype exactement ; prepare et supertypes en 5 s, subtypes en 10 s.
- **Statut** : actif

### typeHierarchyPasDeTypeAuCaretListeVide — caret sans type : englobant ou vide
- **Préconditions / entrée** : `didOpen` de `demo/Enfant2.java` ; `prepareTypeHierarchy` au caret dans le corps VIDE de la classe (juste après « { »), puis au caret sur « package » (avant toute classe).
- **Comportement attendu** : corps vide → 1 élément « Enfant2 » (le type ENGLOBANT compte — convention contextType) ; sur « package » → liste VIDE (aucun type référencé ni englobant).
- **Bornes** : 1 puis 0 éléments exactement ; réponses en 5 s max.
- **Statut** : actif

### callHierarchyPrepareIncomingOutgoing — hiérarchie d'appels complète
- **Préconditions / entrée** : `didOpen` de `demo/CHA.java` (l'appelant) ; `prepareCallHierarchy` sur l'appel « b.utilise(3) » ; `callHierarchyIncomingCalls` de l'élément ; prepare sur « declenche() » puis `callHierarchyOutgoingCalls`.
- **Comportement attendu** : le prepare sur l'APPEL rend 1 élément « utilise » localisé dans `Base.java` (la DÉCLARATION) ; incoming = exactement 1 appelant « declenche » avec exactement 1 site d'appel (fromRanges) ; outgoing de `declenche` = exactement 1 cible source « utilise » (les cibles binaires sont écartées).
- **Bornes** : 1 élément / 1 appelant / 1 site d'appel / 1 cible exactement ; prepare en 5 s, incoming/outgoing en 10 s.
- **Statut** : actif

### completionPorteFilterTextAligneSurLeLabel — filterText = label (I12)
- **Préconditions / entrée** : `didOpen` de `demo/FT.java` (source de l'appelant) ; requête `completion` juste après « b. » (membres de Base).
- **Comportement attendu** : une `CompletionList` (forme droite) non vide ; CHAQUE item porte un `filterText` non-null strictement ÉGAL à son label.
- **Bornes** : réponse en 5 s max ; égalité filterText/label pour tous les items.
- **Statut** : actif

### requetesAvantInitialisationRejetees — garde I2 sur les 5 requêtes S2-b
- **Préconditions / entrée** : serveur NON initialisé ; cinq requêtes successives : `selectionRange`, `onTypeFormatting`, `codeLens`, `prepareTypeHierarchy`, `prepareCallHierarchy` (positions/plages fictives).
- **Comportement attendu** : chacune est rejetée par `ResponseErrorException` (garde lifecycle I2 — ServerNotInitialized).
- **Bornes** : 5 requêtes couvertes.
- **Statut** : actif

## Notes pour la réécriture
- La garde lifecycle I2 doit couvrir TOUTES les requêtes, y compris les nouvelles capacités S2-b (5 rejets verrouillés ici).
- Comptage codeLens : la déclaration est EXCLUE ; le balayage consulte l'overlay des documents ouverts PUIS le disque — les fichiers frères doivent exister avec des noms CANONIQUES (paquet ↔ chemin) pour être lus.
- Les éléments de hiérarchie (types et appels) sont localisés dans leur source de DÉCLARATION, jamais à l'emplacement du caret ; le type ENGLOBANT sert d'élément quand le caret ne porte pas de référence.
- Titres de lens exacts en français : « 0 référence » / « 1 référence » (singulier/pluriel).
- `onTypeFormatting` : no-op si le caractère à la position ne correspond pas au caractère tapé ; déclencheurs annoncés « } » puis « ; ».
- `filterText` (I12) : posé sur chaque item et égal au label — condition de filtrage côté client.
