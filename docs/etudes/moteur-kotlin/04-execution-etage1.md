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
> façade `Kt*`. **Vague 1b : session documents — fiches 049/042, SPI
> `ServiceMoteurLangageKotlin` en zone `api/`.**

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
| Fiches session vertes | **Atteint** | Fiches 049 (26/26) + 042 (4/4) — vague 1b, §7 |
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

- **Vague 1a — façade `Kt*`** : LIVRÉE (§1–§3, clôturée sur CI verte
  9/9 — commit `a1b29c8`, run `40988517140`).
- **Vague 1b — session documents** : LIVRÉE (§7–§9) — fiches 049 (26
  comportements : didOpen/didChange/didClose, diagnostics d'imports
  INUTILE/conflictuels + tags/informations liées KP15, plages pliables,
  survol S2, rafraichirSources) et 042 (4 : branchement A211, sous-lecture,
  verrou lecture/écriture). Le SPI `ServiceMoteurLangageKotlin` est NÉ en
  zone `api/` (chemin de production de l'hôte, D6 — le pack futur se
  branche là, et uniquement là).
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

## 7. Périmètre exécuté (vague 1b — session documents, fiches 049/042)

| # | Item | État | Détail |
|---|---|---|---|
| 1 | SPI zone `api/` | **Fait** | `ServiceMoteurLangageKotlin` (contrat + `unique` : ServiceLoader sur descripteur `META-INF/services` EMBARQUÉ dans le jar, repli moteur noyau — jamais d'exception au chargement) ; `SessionLangageKotlin` (ouvrir/modifier/fermer, diagnostics, plagesPliables, survol, rafraichirSources, definirClasspath, close — types 100 % neutres, aucune dépendance LSP4J/Android, D6) |
| 2 | Modèles neutres | **Fait** | `DiagnosticKotlin`/`SeveriteKotlin`/`CodeDiagnosticKotlin` (`kt.syntax`/`kt.unusedImport`/`kt.conflictingImport`)/`TagDiagnosticKotlin` (INUTILE)/`InformationLieeKotlin` (KP15) ; `PlagePliableKotlin`/`GenrePlagePliable` ; `SurvolKotlin` (signature + documentation) ; `ClasspathKotlin` (entrées ordonnées + racines) |
| 3 | Moteur noyau | **Fait** | `MoteurLangageKotlinNoyau` — NOURRICIER (ADR 0211) : `definirClasspath` rend `true` et publie un snapshot IMMUABLE (la lecture réelle des segments arrive vague 1d SUR cette passe) ; sessions câblées sur sa couture |
| 4 | Session documents | **Fait** | `SessionLangageKotlinDocuments` — verrou lecture-écriture (ADR 0117/0212 : écritures = mutations/définitions/publication du scan ; lectures PARALLÈLES = requêtes ; le scan L1 court HORS verrou), fermeture abandonnante (no-ops postérieurs), mode script par suffixe `.kts` |
| 5 | Diagnostics | **Fait** | `kt.syntax` (les DEUX formes de marqueurs d'erreur, plage et message amont repris) ; `kt.unusedImport` (nom effectif — alias sinon dernier segment — absent des IDENTIFIANTS hors préambule ; joker et directives en édition jamais signalés) ; `kt.conflictingImport` (signalé sur le plus tardif, message aux DEUX chemins, information liée sur la PREMIÈRE directive) |
| 6 | Confort S2/L1 | **Fait** | Plages pliables (imports/KDoc/corps de types/corps de fonctions — périmètre exact de la fiche 049, le reste est fiche 014) ; survol (signature écrite conservatrice + KDoc immédiatement précédente ; chaîne : document courant → documents ouverts → scan des racines) ; `rafraichirSources` (scan déterministe : racines en ordre posé, fichiers triés) |
| 7 | Branchement A211 | **Fait** | Guichet `indexSiPret()` consulté À CHAQUE REQUÊTE : branchement sans re-lecture quand le moteur est nourricier, nourrissage ICI synchrone en usage direct, sous-lecture honnête tant que rien n'est publié (guérison à la requête suivante, sans nouvel appel de définition) |
| 8 | Suite fiches 049/042 | **Fait** | `Fiche049SessionLangageKotlinParseTest` : 26/26 comportements PAR LE SPI ; `Fiche042SessionLangageKotlinA211Test` : 4/4 (câblage des coutures internes — l'usage direct documenté ADR 0211) |
| 9 | Baseline de parité | **Re-signée** | Corpus 287 → **301 fichiers** [mesuré] (14 fichiers Kotlin neufs : 12 main + 2 test) — procédure `-Dparite.majBase=true --rerun-tasks` |
| 10 | Manifeste de reprise | **Inchangé** | Aucune reprise voie C dans cette vague — code 100 % neuf (reprises R4/R5 programmées vague 1d) ; `verifier-provenance.sh` rc=0 |

## 8. Décisions d'exécution consignées (vague 1b — pas dans le plan initial)

1. **Trivia de marge dans les plages de productions (constat de sonde,
   à connaître)** : la politique de liaison de la grammaire attache les
   COMMENTAIRES PRÉCÉDENTS à la plage de la production qui suit (sonde :
   le KDoc « de la classe » fait partie de la plage du `CLASS` qui suit) —
   le KDoc du survol se cherche donc avant le PREMIER JETON PROPRE de la
   déclaration, jamais avant sa plage ; et les plages de diagnostics
   d'imports se rognent des blancs traînants (le `\n` d'une directive se
   lie à son dernier enfant — sonde). Les plages `ERROR_ELEMENT` restent
   fidèles au marqueur, vide compris (l'élargissement des plages vides est
   un soin d'hôte, K2 ancien).
2. **Commentaires du document portés par la racine** : les jetons de
   commentaire sont des trivia HORS productions — l'arbre reconstruit ne
   les portait pas (vague 1a). La racine `NoeudArbre` collecte désormais
   la liste des commentaires du document (additif, aucune rupture de la
   façade) : c'est la matière première du KDoc (survol S2) et du pliage
   KDoc. Le KDoc se reconnaît à son texte (`/**`), pas au genre du jeton —
   robuste au vocabulaire du lexeur.
3. **Sévérités du catalogue conservateur** : `kt.syntax` = ERREUR (fait du
   parseur) ; `kt.unusedImport` et `kt.conflictingImport` =
   AVERTISSEMENT (conseil d'éditeur — le build reste la source de vérité,
   philosophie D3). Choix de la maison, consigné ici pour la revue du
   banc de parité (ADR 0119).
4. **A211 à la vague 1b — périmètre du guichet** : la fiche 042 nomme
   `nourrissageParMoteur` et le snapshot publié ; le guichet `indexSiPret`
   est la couture exacte (interne au module). À la vague 1b il atteste
   l'état de couverture (branchement, sous-lecture, guérison — par
   IDENTITÉ du snapshot) ; les requêtes d'index proprement dites
   (appelables du classpath) et le silence `kt.unresolved` (preuve par
   l'absence) s'y branchent à la vague 1d / étage 2 sur la MÊME couture —
   la fiche 042 reste donc OUVERTE en profondeur (son verrou de structure
   est posé, son verrou sémantique viendra avec l'index). La passe moteur
   est immédiate à la vague 1b (le snapshot porte l'identité du classpath
   couvert) : le fil dédié et la lecture réelle des segments arrivent avec
   le contenu (vague 1d) — le CONTRAT (retour `true`, publication
   immuable, branchement, guérison à la requête suivante) est ce que cette
   vague verrouille.
5. **Scan L1 minimal et déterministe** : `rafraichirSources` scanne
   synchronement les racines posées (racines en ordre posé, fichiers triés
   par chemin, première déclaration par nom — les surcharges relèvent de
   la résolution, étage 2). Le scan paresseux borné annulable (fiche 041)
   est la vague 1c : il remplacera ce scan sans changer le contrat
   (publication immuable hors verrou).
6. **Tests de la fiche 042 par les coutures internes** : le contrat de
   SESSION (fiche 049) se teste PAR LE SPI (chemin de production de
   l'hôte, D6) ; le MÉCANISME de branchement (fiche 042) se teste câblé
   main sur les coutures internes du module (`moteur = null` pour l'usage
   direct, moteur noyau pour le branchement) — c'est l'usage direct
   documenté par l'ADR 0211 (« tests, packs anciens »), les deux fiches
   restant tracées par leurs noms exacts.
7. **Signature écrite conservatrice (S2)** : le rendu reconstruit l'en-tête
   depuis les enfants typés (récepteur `TYPE_REFERENCE` avant la liste de
   paramètres, retour après ; supertypes pour les types) — le corps et les
   initialiseurs ne voyagent jamais. Tronqué sans nom ⇒ pas de survol (la
   chaîne CONTINUE — une déclaration tronquée n'arrête pas la recherche).
8. **Première passe CI ROUGE puis guérie — scanner ART affiné sur ses
   propres règles (run `40999088759`, job `kotlin-art`)** : le scan a
   signalé `1 × ServiceLoader` dans le jar moteur (la gate étage 0 exige
   AUCUN motif). La catégorie disait « présent ART — vérifier
   META-INF/services » : la vérification est désormais MÉCANIQUE — une
   référence ServiceLoader AVEC descripteur META-INF/services valide
   (chaque ligne un nom binaire résoluble en .class DANS LE MÊME jar) est
   tracée « OK » et ne compte pas comme motif sensible (le SPI du pack
   D6 charge le moteur par ServiceLoader, miroir de l'ancien — le repli
   noyau reste si le ServiceLoader échoue au chargement) ; SANS descripteur
   valide la référence RESTE un motif sensible (chemin négatif testé sur
   un jar artificiel privé de son descripteur). Profité pour corriger une
   imprécision voisine : `readAllBytes`/`readNBytes` sont désormais des
   motifs MEMBRES CORRÉLÉS (Methodref → déclarant ↔ nom) — seul
   `java/io/InputStream.readAllBytes` (API 33+) se signale,
   `Files.readAllBytes` (Java 7, ART 26+) est légitime ; les 2 anciens
   signaux de kotlin-stdlib étaient des noms Utf8 non corrélés (elle
   scanne désormais AUCUNE). Au passage, le `ScanSources` de la session a
   été aligné min-api 26 (`Paths.get` + `Files.readAllBytes` explicites
   UTF-8 au lieu de `Path.of`/`Files.readString` Java 11) — le dexage du
   pack cible min-api 26. Baseline re-signée après le diff de code.

## 9. Mesures de la vague 1b (bac à sable — JVM de bureau conteneurisée,
2 cœurs, OpenJDK 21.0.13)

**Suite du module : 57 tests verts** [mesuré] (fiches 010 : 2, 011 : 3,
012 : 9, 013 : 13, **049 : 26**, **042 : 4** — le domaine `parse` + le
domaine `cycle-vie-lsp`/branchement de session du cahier sont couverts à
57/57 comportements sur leur périmètre livré).

**Banc `:kotlin:bench` (nombres, aucun seuil — grammaire inchangée,
bruit de mesure)** : parse froid 177,2 ms / 40,3 ms (500/3 000 l.),
parse chaud p50 6,4 ms / 20,1 ms, p95 37,9 ms / 43,5 ms, tolérance 101
troncatures hostiles 0 lever, tas au repos 4 356 Kio (inchangé — la
session ne vit pas dans le banc, banc grammaire).

**Jar moteur : 309 344 o (302,1 Kio)** [mesuré] — 251,5 Kio à la vague
1a ; **+50,5 Kio** pour le SPI + la session documents (117 classes dont
**51 code neuf** [mesuré par scan du jar] — 24 à la vague 1a ; 66 grammaire
vendorée inchangée). Le descripteur `META-INF/services` du SPI et les
notices `META-INF/vendor-kotlin` sont embarqués [mesuré]. Le dexage d8 et
les seuils sont vérifiés par la CI (`kotlin-art`) : **16 256 références de
méthodes / 2 806 956 o (2,68 Mo)** [mesurés par la CI — +175 méthodes /
+38 908 o vs vague 1a : la session et le SPI], seuils étage 0 tenus
(≤ 32 768 / ≤ 4 Mo, marge ×2 sur les méthodes).

**Code livré [mesuré]** : 12 fichiers main (1 598 l.) + 2 fichiers de test
(579 l.) — KDoc français détaillé partout, explicitApi Strict. Comptage
`synchronized` du code neuf : **1** [mesuré par `rg`] — la publication du
snapshot moteur (verrou dédié de la passe, hors chemin des requêtes) ; la
session vit sur le verrou lecture-écriture documenté (ADR 0117 : seuil CI
à documenter lorsque la métrique existera — miroir « < 6 » Java).

**Provenance : `verifier-provenance.sh` rc=0** [mesuré] — aucun fichier
hors registre (code neuf libre ; aucune reprise voie C cette vague).

**CI VERTE 9/9 sur le commit de guérison `4575a4f`** (run `41000084597`,
consulté le 30/10 à 03:58 UTC) — build et tests JDK 21, bancs
noyau/qualité/parité T, gates Android, provenance étendue kotlin/, et les
trois jobs Kotlin : suite du module (57 tests verts — fiches 010–013,
049, 042), scan ART **5 × « AUCUNE »** avec la trace `ServiceLoader : OK —
1 référence(s), descripteur(s) vérifié(s)` (§8.8 — le premier run
`40999088759` sur `b1832cd` avait signalé la référence avant le
raffinement du scanner), dexage d8 **16 256 méthodes / 2,68 Mo** (seuils
tenus), banc empreinte/latence. **La vague 1b est CLOSE sur preuve
mécanique.**
