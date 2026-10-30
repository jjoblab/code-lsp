# 04 — Exécution étage 1 : noyau parse/session/classpath

> **Décision du propriétaire reçue le 2026-10-30** dans le canal de mission :
> « Tous ce que je veux c'est une réécriture, pour recommencer sur de
> nouvelles bases plus solide, propre et professionnel comme je viens de
> faire pour lsp java. Donc oui, tu peux commencer les chantiers. Pour le
> banc-appareil.sh je te les donnerai plus tard » — les chantiers de
> l'étage 1 (plan §4 de `00-synthese.md`) sont OUVERTS sur la branche
> `etude/moteur-kotlin`. La **passe appareil** (porte d'entrée formelle de
> l'étage 1) est **DIFFÉRÉE par le propriétaire** : le `banc-appareil.sh`
> (et son exécution) sera fourni plus tard — le développement côté JVM
> démarre sans attendre, le critère de sortie « gabarit appareil rempli »
> reste OUVERT et sera attesté à réception. Le présent document est le
> journal d'exécution de l'étage (méthode : miroir des rapports précédents
> — chiffres étiquetés [mesuré] / [estimé], écarts signalés). Vague 1a :
> façade `Kt*`.

## 1. Périmètre exécuté (vague 1a — façade `Kt*`, fiche 013)

| # | Item | État | Détail |
|---|---|---|---|
| 1 | Reconstruction de l'arbre | **Fait** | `parse/ArbreSyntaxique.kt` (interne) : pile d'ouvertures/fermetures sur la liste plate des marqueurs, chemins défensifs sans exception ; nœuds immuables `NoeudArbre` avec jetons propres, texte canonique, message d'erreur |
| 2 | Façade `Kt*` (fiche 013) | **Fait** | Package `parse/kt/` : `KtNoeud` (socle générique + `PlageTexte`), `KtFichier`, `KtDirectivePackage`, `KtDirectiveImport`, hiérarchie fermée `KtDeclaration` (`KtClasse`/`KtFonction`/`KtPropriete`/`KtTypealias`/`KtObjet`/`KtEntreeEnum` + `KtParametreValeur`, `KtGenreClasse`), `KtAnnotationEntree`/`KtArgumentAnnotation` |
| 3 | Point d'entrée du harnais | **Fait** | `ParseurKotlin.arbre(texte, script)` : même patron d'assemblage que `analyse` (extraction `assembler`), rend la racine `KtFichier` — le contrat de tolérance (jamais d'exception) est inchangé |
| 4 | Suite fiche 013 | **Fait** | `parse/kt/Fiche013FacadeKtTest.kt` : 13/13 comportements verts (noms exacts du cahier, identifiants de traçabilité) |
| 5 | Baseline de parité | **Re-signée** | Corpus 280 → **287 fichiers** [mesuré] (7 fichiers Kotlin neufs : 6 main + 1 test) — procédure documentée `-Dparite.majBase=true --rerun-tasks` |
| 6 | Manifeste de reprise | **Inchangé** | Aucune reprise voie C dans cette vague — code 100 % neuf (R4/R5 programmées en vague 1d) ; `verifier-provenance.sh` rc=0 |

## 2. Décisions d'exécution consignées (pas dans le plan initial)

1. **Encodage de la liste des marqueurs établi par sondes** (fichiers de
   test temporaires, exécutés puis SUPPRIMÉS) : chaque production apparaît
   en paires ouverture/fermeture référençant le MÊME objet marqueur
   (identité vérifiée), en ordre strictement équilibré du document ; les
   marqueurs d'erreur posés par `error(message)` apparaissent en UNE seule
   entrée de plage nulle — des FEUILLES rattachées au nœud ouvert courant.
   La reconstruction suit la discipline de pile ; des chemins défensifs
   (jamais observés) garantissent qu'aucun texte hostile ne lève.
2. **Deux formes d'erreurs dans l'arbre** (constat de sonde, à connaître
   pour la fiche 049) : les nœuds `ERROR_ELEMENT` existent en forme PAIRE
   (message porté par `getErrorMessage`, ex. « Name expected » sur
   `class\n`) ET en forme mono-entrée (`error(message)`, ex. « Expecting an
   expression » sur `val x = `). L'arbre reconstruit porte les DEUX avec
   leur message — les diagnostics de syntaxe de la session liront les
   nœuds `ERROR_ELEMENT` de l'arbre (plage + message). NB : le compteur
   `ResultatAnalyse.nombreMarqueursErreur` de l'étage 0 ne compte que la
   forme mono-entrée — écart documenté, sans effet sur les fiches 010–012
   (leurs fixtures n'éproulent que la tolérance sans-lever et les sources
   valides).
3. **Noms qualifiés — deux encodages différents** (sonde) : les directives
   package/import portent un enfant `DOT_QUALIFIED_EXPRESSION` (chemin en
   référence pointée) ; les annotations portent la référence qualifiée en
   `USER_TYPE` IMBRIQUÉS (qualificateurs en cascade). Le « nom tel
   qu'écrit » se lit donc comme le texte canonique (jetons de la plage,
   trivia exclus) de l'expression de chemin pour les imports, et de la
   `TYPE_REFERENCE` du `CONSTRUCTOR_CALLEE` pour les annotations.
4. **Texte canonique par jetons** (pas par suppression des blancs du
   texte) : la concaténation des jetons significatifs de la plage exclut
   blancs ET commentaires par construction — les noms restent propres même
   avec des commentaires triviaux intercalés.
5. **Ordre du document garanti pour les enfants** : les fermetures de
   productions suivent l'ordre du document (imbriquées closes avant leur
   englobante, sœurs en ordre d'écriture) — les listes d'enfants
   reconstruites sont en ordre du document, propriété sur laquelle reposent
   les jetons propres ET les accès « ordre du document » de la fiche 013.
6. **Hiérarchie fermée volontairement incomplète** : `SECONDARY_CONSTRUCTOR`
   et `CLASS_INITIALIZER` ne sont pas typés à cet étage (sous-rapport D3
   honnête — jamais de typage approximatif) ; ils restent joignables par la
   navigation générique `enfants(genre)` et seront typés quand leur fiche
   le demandera (fiche 044 héritage, vague 2).
7. **Genre fin de classe** : `OBJECT_DECLARATION` → OBJET ; modificateur
   doux `enum` (nœud `ENUM_MODIFIER` dans `MODIFIER_LIST`) → ENUM ; jeton
   `interface` propre à la déclaration → INTERFACE ; sinon CLASSE.
8. **Joker d'import** : le jeton `*` (`MUL`) est un jeton PROPRE de la
   directive (hors expression de chemin) — `estJoker()` interroge les
   jetons propres ; le chemin rendu reste la partie qualifiée écrite
   (`java.util` pour `import java.util.*`).
9. **Alias conservateur** : l'identifiant d'alias est le premier jeton
   `IDENTIFIER` propre du nœud `IMPORT_ALIAS` — le mot-clé `as` (jeton
   `AS_KEYWORD`) ne concurrence jamais ; une troncature après `as` rend
   null sans lever (fiche 013).

## 3. Mesures de la vague 1a (bac à sable — JVM de bureau conteneurisée,
2 cœurs, OpenJDK 21.0.13)

**Suite du module : 27 tests verts** [mesuré] (fiches 010 : 2, 011 : 3,
012 : 9, **013 : 13** — le domaine `parse` du cahier est couvert à
27/27 comportements).

**Banc `:kotlin:bench` (nombres, aucun seuil) :**

| Mesure | 500 l. | 3 000 l. | Étage 0 (référence) |
|---|---:|---:|---|
| Parse froid | 221,2 ms | 21,4 ms | 274,5 ms / 46,4 ms |
| Parse chaud p50 | 5,8 ms | 18,9 ms | 6,4 ms / 18,6 ms |
| Parse chaud p95 | 40,8 ms | 48,6 ms | 37,0 ms / 51,9 ms |
| Productions / jetons | 5 448 / 4 210 | 31 368 / 24 274 | idem |
| Marqueurs d'erreur | 0 | 0 | 0 |

Tolérance : 101 troncatures hostiles, **0 lever** [mesuré]. Tas au repos :
4 356 Kio (3 698 Kio à l'étage 0 — +658 Kio, empreinte de la façade sur
les passes du banc ; à surveiller au banc appareil). Volumétrie d'arbre
inchangée (même grammaire, même corpus).

**Jar moteur : 257 572 o (251,5 Kio)** [mesuré] — 216 Kio à l'étage 0 ;
+36 Kio pour la façade (69 → **90 classes** [mesuré par scan du jar], dont
24 hors zone vendored — harnais + façade). Le dexage d8 et les seuils
sont vérifiés par la CI (`kotlin-art`) : **CI VERTE 9/9 sur le commit de
livraison `a1b29c8`** (run `40988517140`, consulté le 30/10 à 01:56 UTC) —
scan ART **4 × « AUCUNE » référence sensible** (moteur + pile spécifique :
la façade n'ajoute AUCUN motif), dexage d8 **16 081 références de méthodes
/ 2 768 048 o (2,64 Mo)** [mesuré par la CI — +117 méthodes / +25 380 o
vs étage 0 : la façade], seuils étage 0 tenus (≤ 32 768 / ≤ 4 Mo, marge
×2 sur les méthodes).

**Code livré [mesuré]** : 6 fichiers main (1 040 l.) + 1 fichier de test
(214 l.) — KDoc français détaillé partout, explicitApi Strict (le code
amont vendored reste exempté, compilation séparée inchangée).

**Provenance : `verifier-provenance.sh` rc=0** [mesuré] — aucun fichier
hors registre (code neuf libre ; aucune reprise voie C cette vague).

## 4. Critères de sortie de l'étage 1 (synthèse §4 — suivi)

| Critère | État | Preuve |
|---|---|---|
| Fiches parse vertes | **Atteint** | 010–013 : 27/27 comportements (domaine `parse` du cahier complet) |
| Fiches session vertes | Ouvert | Fiche 049 (+042) — vague 1b |
| Fiches index vertes | Ouvert | Fiches 008/009/034 — vague 1d |
| Fiches synthétiques vertes | **Bloqué propriétaire** | R1/R2 attendent l'audit P-K1 (recommandé avant l'étage 1 — toujours ouvert) |
| Latences S0 tenues (reparse < 50 ms JVM à 3 000 l.) | **Atteint** | parse chaud p50 18,9 ms / p95 48,6 ms à 3 000 l. [mesuré] |
| Gabarit appareil rempli (porte d'entrée) | **Différé** | Instruction propriétaire : `banc-appareil.sh` fourni plus tard — le critère reste OUVERT |

**CI VERTE 9/9 sur le commit de livraison** (run `40988517140`, commit
`a1b29c8`, consulté le 30/10 à 01:56 UTC) — build et tests JDK 21, bancs
noyau/qualité/parité T, gates Android, provenance étendue kotlin/, et les
trois jobs Kotlin : suite du module (fiches 010–013 — 27 tests verts),
scan ART + dexage d8 (4 × AUCUNE, 16 081 méthodes / 2,64 Mo, seuils
tenus), banc empreinte/latence. **La vague 1a est CLOSE sur preuve
mécanique.**

## 5. Suite de l'étage 1 (vagues suivantes — cahier en main)

- **Vague 1b — session documents** : fiches 049 (26 comportements :
  didOpen/didChange/didClose, diagnostics d'imports INUTILE/conflictuels,
  plages pliables, survol S2, rafraichirSources) et 042 (4 : verrou
  lecture/écriture A211). Le SPI `ServiceMoteurLangageKotlin` naît ici
  (zone `api/` réécrite clean-room — le chemin de production de l'hôte,
  D6).
- **Vague 1c — scan projet paresseux** : fiche 041 (15 comportements,
  accès concurrents) — sur la façade livrée.
- **Vague 1d — classpath + index appelables** : reprises R4/R5 au câblage
  (inscription PRÉALABLE déjà posée, re-contrôle R9.4 à la copie) +
  fiches 002/003/004/005 (11 comportements) ; index appelables fiches
  008/009/034 (18) sur l'hypothèse **index dédié** (ADR 0108 — Q-K1
  ouvert, l'arbitrage propriétaire peut redresser).
- **Synthétiques** : R1/R2 bloqués à l'audit P-K1 (§4).

## 6. Actions propriétaire ouvertes (mise à jour)

| # | Action | Référence | Bloque |
|---|---|---|---|
| 1 | Passe appareil (Termux) : `banc-appareil.sh` + gabarit — **le propriétaire a annoncé fournir le script plus tard** | GABARIT-RESULTATS.md, §4 | le critère de sortie « gabarit appareil rempli » de l'étage 1 |
| 2 | Audit de similarité GPL (P-K1) | manifeste §5 | la copie de synthetics R1/R2 (vague 1d/2) |
| 3 | Arbitrages Q-K1 (CIDX vs index dédié) / Q-K2 (décodage @Metadata) | DEMANDES-INTERFACE, ADR 0108 | le câblage classpath final (le développement suit l'hypothèse index dédié) |
| 4 | Conditions binaires intellij-dependencies (P-K2) | ADR 0120, NOTICE | la PUBLICATION du pack langue (pas le développement) |
| 5 | Lecture du second manifeste | synthèse §6.4 | rien |
