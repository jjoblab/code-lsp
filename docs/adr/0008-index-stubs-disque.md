# ADR 0008 — Index (stubs / disque)

- **Statut** : Proposé
- **Date** : 2026-10-28

## Contexte

§5-D7 demande la comparaison entre l'index IntelliJ ( stub index + file-based ) et l'index par segments CIDX ( ADR 0111 ancien ). Faits décisifs de l'étude :

- **Il n'existe pas de requête par préfixe au niveau du stockage IntelliJ** : la complétion de types énumère toutes les clés de `CLASS_SHORT_NAMES` puis filtre camel-hump en mémoire ( `AllClassesGetter.java:193-204`, `PsiShortNamesCacheImpl.java:117-123` ) — le jeu lourd équivalent fait ~23 300 noms simples.
- **Deux piles de persistance incompatibles ART** : pile par défaut `PersistentHashMap`/`PagedFileStorage` ( page cache 600 Mo par défaut, libération par cleaner `sun.nio.ch.DirectBuffer` via MethodHandles — absent d'ART ) ; pile « durable » opt-in sur `java.lang.foreign` ( JDK 19+ ) + VarHandle.
- **Ce que l'index IntelliJ a en plus** : invalidation par fichier source ( IndexingStamp ), **forward index** ( updates différentielles ADDED/UPDATED/REMOVED — réponse directe au point faible W4 du statu quo ), généricité multi-index ( 19 index Java sur un moteur ), remontée PSI complète.
- **CIDX ( ADR 0111 ancien ) garde la victoire sur les axes mobiles mesurés** : préfixe par recherche binaire dans segments triés ( p50/p95 0,16–0,76/1,1–7,1 ms petit→lourd ), +1 dépendance = seul le jar nouveau réindexé ( 85/89/87 ms ), budget octets explicite ( 4,2/2,9/29,7 Mo, cible < 30 Mo/100 jars tenue ), corruption = seul segment refusé et reconstruit paresseusement ( vs rebuild de l'index entier côté IntelliJ ).

## Sources consultées

- `JetBrains/intellij-community` @ `865067b0` (lecture seule) : `platform/indexing-{api,impl}`, `platform/lang-impl/backend`, `platform/util/{io,storages}` — chaînes citées `01-architecture-psi.md` §D7 ; annexe `annexes/exp-D7D8.md` §D7 ( tableaux D7.6 ).
- `jjoblab/codeide-lsp` @ `cb809e14` : `lsp/index` ( CIDX, 33 f / 6 472 l. ), ADR 0111, `docs/LSP_JAVA.md` §4/§12.

## Options

**Option 1 — Conserver CIDX comme socle, greffer quatre designs IntelliJ.** (a) la forme canonique **ValueContainer** clé→( unité→valeur ) pour unifier multi-usages ; (b) le **forward index / snapshot de clés par unité** pour updates différentielles des sources ( répond W4 ) ; (c) l'idiome **IndexingStamp** ( unité × index × version ) pour l'invalidation fine des fichiers sources ; (d) le **dumb mode à deux niveaux** ( `RELIABLE_DATA_ONLY` / `RAW_INDEX_DATA_ACCEPTABLE`, `DumbModeAccessType.java:18-61` ) pour servir des données partielles pendant indexation.

**Option 2 — Remplacer CIDX par un portage de StubIndex/FileBasedIndex.** Rejeté : deux piles de persistance incompatibles ART, 600 Mo de page cache, fileId-VFS verrouille sur le VFS persistant, rebuild global sur corruption ( D7 ) ; CIDX gagne sur chaque axe mobile mesurable.

**Option 3 — Ajouter un index « texte de mots » à la IntelliJ ( searchWord ).** Cas d'usage : recherche d'usages non résolue ( D10 ). À évaluer **dans** CIDX ( segments de mots ) plutôt qu'en index séparé — sous-option de l'option 1, portée par l'ADR 0014.

**Option 4 — Ne rien changer.** CIDX seul, sans forward index ni dumb mode : conserve W4 ( ré-indexation en masse sur changement de dépendance ) et le blocage des requêtes pendant indexation.

## Critères et mesures

- Tableau comparatif factuel : `01-architecture-psi.md` §D7 + annexe D7.6 ( préfixe, +1 dép, mémoire, corruption, atouts IntelliJ ).
- Cibles §4 : complétion types p95 < 100 ms jusqu'à 250 jars ; tas index < 30 Mo/100 jars ; +1 dépendance = seul nouveau jar.

## Décision proposée

**Option 1** : CIDX conservé ( reprise contrôlée ADR 0001/0002 ) ; greffes ordonnées par valeur : (1) **IndexingStamp** ( invalidation fine — petite surface, gros gain ) ; (2) **dumb mode deux niveaux** ( disponibilité pendant indexation — expérience utilisateur ) ; (3) **forward index par unité** pour les sources ( W4 ) ; (4) ValueContainer comme refactor interne de consolidation. L'éventuel index de mots ( option 3 ) est étudié avec l'ADR 0014.

## Conséquences

- CIDX devient plus « IntelliJ-esprit » ( invalidation fine, updates différentielles ) sans hériter de ses poids ( mmap, page cache, fileId-VFS ).
- Chaque greffe est une modification du module repris — passe le contrôle de provenance au manifeste.
- Le budget octets reste la loi ( étendre aux caches du moteur, ADR 0011 ).

## Plan de migration progressive

Greffe par greffe derrière drapeaux ( `index.stamps`, `index.dumb-deux-niveaux`, `index.forward` ) ; à chaque étape : banc d'index ( +1 dépendance, latence préfixe, mémoire ) sans régression ; retour arrière = drapeau off ( l'ancien chemin de ré-indexation reste vivant pendant une version ).

## Tests de non-régression et critères de sortie

- +1 dépendance : seul le jar nouveau réindexé ( temps ≤ baseline ) ; préfixe p95 ≤ 7,1 ms ( lourd, bureau ) ; tas ≤ 30 Mo/100 jars ; corruption segment : reconstruction paresseuse constatée.
- Dumb mode : requêtes servies ( données marquées brutes ) pendant une indexation simulée.

## Risques

- **R1 — Complexité des stamps mal invalidés ( trop ou trop peu )** : tests de chaos ( toucher des fichiers, changer des jars, corrompre des segments ) en CI.
- **R2 — Forward index des sources coûte en disque** : budget octets étendu, mesure à ajouter au banc.
