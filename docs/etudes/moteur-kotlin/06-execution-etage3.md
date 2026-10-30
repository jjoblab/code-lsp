# 06 — Exécution étage 3 : complétion + classement

> **Décision du propriétaire reçue le 2026-10-31** dans le canal de mission :
> « Go etage 3 » — les chantiers de l'étage 3 (plan §4 de `00-synthese.md`)
> sont OUVERTS sur la branche `etude/moteur-kotlin`, à la suite de la
> clôture sur preuve mécanique de l'étage 2 (CI verte 9/9 sur `8ff747c`,
> run `41112383347` — `05-execution-etage2.md` §11.4 : 204/204
> comportements diagnostics, zéro faux positif corpus quatre mondes,
> banc fidélité baseline signée, premier recalage d'effort exécuté).
> Le présent document est le journal d'exécution de l'étage (méthode :
> miroir des rapports précédents — chiffres étiquetés [mesuré] /
> [estimé], écarts signalés).
>
> Gouvernance inchangée : branche `etude/moteur-kotlin` seule ; zones NOYAU
> anciennes (`completion/`, `session/` de production) en lecture interdite
> R9.1 — l'étage 3 est du code NEUF écrit cahier en main (ADR 0113 : « la
> chaîne de classement de l'ADR 0012 est la spécification du pipeline de
> complétion Kotlin neuf ») ; les tests sources anciens restent
> consultables (R9.5) pour les FIXTURES, jamais copiés. Le germe membre
> livré en 2a (`CompletionMembres` + `PropositionKotlin.libelle`) est le
> point de départ : l'étage l'étend en pipeline complet SANS régression des
> comportements déjà verts (fiches 022/046/044/047 — suite 337 tests).
>
> **Mise à jour (31/10, vague 3a) — LE PIPELINE DE COMPLÉTION EST LIVRÉ
> avec la fiche 045 ENTIÈRE (21 comportements — le contrat K3) : la
> CLASSIFICATION DU CARET (import → membre → nom, l'ordre de décision
> documenté ; les contextes v1 de la fiche 043 s'insèrent en 3b), le
> modèle RICHE `PropositionKotlin` (nature, détail, texte d'insertion,
> FQCN d'import auto — les libellés restent NUS, non-régression du germe),
> le régime NOM (paramètres et locales de la région avec leur type écrit,
> membres du récepteur implicite par la pile des this, types du projet,
> appelables déclarés, catalogue KP8, appelables de l'index par préfixe
> BORNÉ, TYPES DU CLASSPATH PAR PRÉFIXE avec FQCN en détail et en import
> auto-éditable — l'ANNUAIRE paresseux de la vue JVM, une seule marche des
> entrées, aucun class-file ouvert ; MOTS-CLÉS toujours proposés, même à
> préfixe vide — le témoin du repli honnête), le régime MEMBRE détaillé
> (jeu DÉTAILLÉ de la chaîne : nature et type de présentation par membre,
> compagnon et extensions compris), le régime IMPORT (sous-paquets
> DIRECTS du paquet tapé dominants sur les types directs), l'ARRANGEMENT
> honnête (genre du contexte + total réel ; le plafond d'affichage
> s'appliquera AVEC le classement en 3c, sur la liste ordonnée — jamais
> sur l'ordre de rencontre), le nouveau contrat SPI
> `completionArrangee()` ; TROIS correctifs de fond découverts par la
> fiche (voir §5.2) : les LOCALES se survolent et se définissent (les
> déclarations descendent dans les BLOCS), les TERMINAUX CONNUS
> n'éteignent plus la recherche d'un membre hérité (Object exploré avant
> View par le DFS des interfaces), la KDOC TIERCE vérifie l'existence du
> fichier déclarant (garde D3 ADR 0208).**

## 1. Périmètre de l'étage (plan §4 — cahier en main)

| # | Item | Source du contrat |
|---|---|---|
| 1 | **Pipeline de complétion** : classification du caret (membre, nom, import), modèle riche de proposition (libellé, nature, détail, texte d'insertion, FQCN d'import auto), mots-clés du langage, plafond d'affichage honnête | ADR 0113 ; fiche 045 (7 comportements complétion) |
| 2 | **Complétion contextuelle v1** (reprise en parité, ADR 0228) : arguments NOMMÉS d'un appel (insertion avec séparateur, fournis exclus, filtre par préfixe), CONSTANTES d'enum des branches d'un `when` (contexte exclusif, sujet projet et classpath), ATTRIBUTS d'annotation — chaque contexte avec sa borne conservatrice D3 (position de valeur muette, candidat inconnu, repli honnête sur le régime de nom) | ADR 0113 ; fiche 043 (14 comportements) |
| 3 | **Chaîne de classement en naissance** (ADR 0113 option 1, miroir de l'ADR 0012 Java) : peseurs comparés dimension par dimension PARESSEUSEMENT (correspondance graduée EXACT/PREFIXE/…, nature DÉPENDANTE du contexte, longueur liftShorter, alphabétique déterministe, statistiques d'usage OPT-IN), arrangement (GEL des têtes — contrat « zéro saut de liste », trim honnête avec total réel), frontière RPC (poids entier transporté — patron d'intégration LSP ADR 0118) | ADR 0113 ; critère §4 |
| 4 | **Récepteurs de portée** : le paramètre `content: RowScope.() -> Unit` prête RowScope comme this IMPLICITE — membre NU et MEMBRE-EXTENSION du scope, PILE des scopes emboîtés, `buildList` du classpath prête MutableList, DESTRUCTURATION (`(_, total) ->`), PROPRIÉTÉS BUILTINS de Map (`entries`/`keys`/`values`) — chaque famille avec son vrai positif (le silence n'est jamais de la complaisance) | fiche 038 (8 comportements) |
| 5 | **ViewBinding K5** : la complétion membre d'une PROPRIÉTÉ TYPAÉE (`binding: ActivityMainBinding`) offre le groupe synthétique (`root`, `textView`, `inflate`, `bind`) | fiche 039 (1 comportement) |
| 6 | **Banc commun Java** (ADR 0119 option 1) : corpus de positions de complétion VERSIONNÉ, métriques IDENTIQUES à `:java:benchQualite` (recall / top-1 / MRR / p95 / sauts), baseline par langue SIGNÉE (`bancs/baselines/`), CI BLOQUANTE ; comparaison « chaîne livrée » vs « naïf alphabétique » — le design doit le battre de ≥ 5 points top-1/MRR (miroir ADR 0012/0016) | ADR 0113 §Critères ; ADR 0119 ; Q-K6 |

## 2. Critères de sortie de l'étage 3 (synthèse §4 — suivi)

| Critère | État | Preuve |
|---|---|---|
| 44 fiches vertes (fiches 038 + 039 + 043 + 045 du domaine complétion) | **21/44** (fiche 045 — vague 3a, §5) | `Fiche045SessionLangageKotlinK3Test` : 21 comportements |
| Banc : recall ≥ baseline | À atteindre (banc livré en 3e) | — |
| Banc : top-1/MRR +5 pts vs naïf | À atteindre (classement 3c, banc 3e) | — |
| Banc : p95 ≤ baseline (JVM = limite basse ; cible appareil < 100 ms) | À atteindre (banc livré en 3e) | — |
| Zéro saut de liste (contrat gel) | À atteindre (gel 3c, banc 3e) | — |

## 3. Plan de vagues (cahier en main — l'ordre suit le plan de migration ADR 0113 : parité d'abord, classement ensuite, banc à la clôture)

| Vague | Périmètre | Fiches | Comportements |
|---|---|---|---:|
| **3a** | Pipeline de complétion : contexte au caret (membre/nom/import), modèle riche `PropositionKotlin` (nature, détail, texte d'insertion, FQCN import auto), mots-clés, auto-import des types du classpath, sous-paquets du paquet tapé ; fiche 045 ENTIÈRE (complétion + survol + définition + tolérances — le contrat K3) | 045 | 21 |
| **3b** | Complétion contextuelle v1 : arguments nommés, constantes d'enum `when`, attributs d'annotation — insertion avec séparateur, exclusions, replis honnêtes D3 | 043 | 14 |
| **3c** | Chaîne de classement (ADR 0113) : correspondance graduée, peseurs dimension par dimension paresseux, nature dépendante du contexte, arrangement gel + trim honnête, statistiques opt-in, frontière RPC — greffée sur le pipeline 3a SANS toucher à la collecte | — | — |
| **3d** | Récepteurs de portée : type fonction ÉCRIT avec récepteur (`A.() -> B`), member-extensions du scope, pile des scopes, `buildList`, destructuration, builtins Map ; ViewBinding K5 | 038 + 039 | 9 |
| **3e** | Clôture : banc commun Java (corpus versionné + baseline signée + CI bloquante), recalage n° 2 de l'effort (miroir `RECALAGE-1.md` — la première donnée de CLASSEMENT y est mesurée), journal | — | — |

**Total : 44 comportements** (agrégat du cahier — INVENTAIRE.md). Chaque
vague : code + tests NEUFS au nom exact du cahier + CI 9 tâches verte +
section du présent journal + commit/push.

## 4. Actions propriétaire ouvertes (reprises de l'étage 2 — §4 du journal précédent)

| # | Action | Référence | Bloque |
|---|---|---|---|
| 1 | Audit de similarité GPL (P-K1) — la copie R1/R2 est effectuée, l'audit recommandé (rôle distinct des implémenteurs) s'exerce a posteriori | manifeste §3/§6, ALERTES §2 | plus rien de dur — le propriétaire en reste juge |
| 2 | Arbitrages Q-K1 (CIDX vs index dédié) / Q-K2 (décodage @Metadata maison) — PARTIELLEMENT ARBITRÉ (budget T1 élevé 6 Mo le 31/10) ; ouverts pour la structure | DEMANDES-INTERFACE, ADR 0108, ALERTES §4 | le câblage classpath final |
| 3 | Conditions binaires intellij-dependencies (P-K2) | ADR 0120, NOTICE | la PUBLICATION du pack langue |
| 4 | Lecture du second manifeste | synthèse §6.4 | rien |
| 5 | Passe appareil couvrant les étages 1-2 (kit enrichi) — recommandation consignée, OPTIONNELLE | `04-execution-etage1.md` §16/§6 | rien (les latences S0 restent tenues en JVM) |

## 5. Vague 3a — le pipeline de complétion (31/10, fiche 045 — 21 comportements)

### 5.1 Périmètre exécuté

| # | Item | État | Détail |
|---|---|---|---|
| 1 | Modèle riche de proposition | **Fait** | `api/CompletionKotlin.kt` : `NaturePropositionKotlin` (10 natures — la présentation hôte ET le futur peseur de nature), `PropositionKotlin` ÉTENDUE (libellé, nature, détail, `texteInsertion`, `fqcnImport` — défauts `null`, les libellés restent NUS : non-régression du germe 2a), `GenreContexteKotlin` (7 genres), `ReponseCompletionKotlin` (genre + propositions + `totalDisponible` — l'honnêteté du trim) |
| 2 | Classification du caret | **Fait** | `session/CompletionContextes.kt` : IMPORT (le chemin tapé se coupe au caret, texte brut de la directive) → MEMBRE (la règle du GERME 2a à l'identique — fiche 047 : `R|` dans `R.layout` complète le récepteur) → NOM (le repli honnête) ; les genres contextuels v1 (arguments nommés, constantes d'enum, attributs d'annotation) s'insèrent entre MEMBRE et NOM en 3b |
| 3 | Régime NOM | **Fait** | `session/CompletionNoms.kt` : paramètres (nature PARAMETRE) et locales (VARIABLE, type écrit en détail) de la région, membres du récepteur IMPLICITE (`PileThis.membresDetaillesDesCouches`), types du projet, APPELABLES DÉCLARÉS (`ModeleProjet.nomsAppelables` — fiche 025), catalogue KP8, appelables de l'index par préfixe borné (24), types du classpath par préfixe (FQCN en détail ET en import auto-éditable), mots-clés TOUJOURS (même à préfixe vide) ; les sources INFINIES ne se consultent qu'à préfixe NON VIDE |
| 4 | Annuaire de types de la vue JVM | **Fait** | `VueJvmMembres` : l'ANNUAIRE PARESSEUX (une seule marche des entrées — noms d'entrées du zip central, AUCUN class-file ouvert) sert `fqcnParPrefixe` (binaire bornée), `sousPaquetsDirects` (intermédiaires compris : `android` de `android.widget.TextView`), `typesDansPaquet` |
| 5 | Régime MEMBRE détaillé | **Fait** | `UniversPreuves.propositionsMembresDetaillees` : les MÊMES sources que `propositionsMembres` (le germe), le MÊME ordre — chaque nom emporte nature (fonction/propriété/constante/imbriquée) et type de présentation ; `VueJvmMembres.jeuDetaille` (la même marche bornée que `jeuComplet`, le plus dérivé gagne par nom) |
| 6 | Régime IMPORT | **Fait** | `session/CompletionImports.kt` : sous-paquets DIRECTS du parent (nature PAQUET, FQCN en détail) DOMINANTS sur les types directs du paquet (import auto-éditable) — miroir de la fiche 008 Java |
| 7 | Arrangement honnête | **Fait** | `session/CompletionPipeline.kt` : genre + total réel ; le PLAFOND d'affichage (120) s'appliquera en 3c AVEC le classement (sur la liste ORDONNÉE — appliqué à l'ordre de rencontre il tronquait arbitrairement : écart découvert par la fiche 046, corrigé en conserve) |
| 8 | Contrat SPI | **Fait** | `SessionLangageKotlin.completion()` (non cassé : rend les propositions arrangées) + `completionArrangee()` (genre + propositions + total réel) — câblés sur le pipeline, miroir D6 |
| 9 | Correctifs de fond | **Fait** | Voir §5.2 — trois écarts découverts par les comportements survol/définition de la fiche |
| 10 | Tests neufs | **Fait** | 21 comportements aux noms exacts du cahier : `Fiche045SessionLangageKotlinK3Test` — jars RÉELS du classpath de test (android.jar AOSP + stdlib + jar de FIXTURE pour le maillon binaire `java.lang.String` de l'alias ADR 0205), racines temporaires pour les définitions inter-fichiers |

### 5.2 Trois correctifs de fond découverts par la fiche (le cahier comme sonde)

1. **Les LOCALES se survolent et se définissent** : `declarationsRecursives`
   ne descendait que dans les CORPS DE CLASSE — une propriété LOCALE
   (`val titre: String` dans un corps de fonction) était INVISIBLE du
   survol et de la définition (comportements « survol : locale avec type
   écrit » et « définition : locale du document courant »). La collecte
   descend maintenant aussi dans les BLOCS ; la chaîne conservatrice
   (première déclaration du même nom, ordre du document) est inchangée.
2. **Les TERMINAUX CONNUS n'éteignent plus la recherche d'un membre
   hérité** : `chercherMembreChaine` rendait `null` en ATTEIGNANT
   `java.lang.Object` — or le DFS des interfaces (poussées APRÈS la
   superclasse, dépilées AVANT elle) explore Object AVANT `View` :
   `v.requestLayout()` sur un TextView réel ne se survolait JAMAIS
   (comportement « survol : membre qualifié hérité »). Les terminaux
   passent maintenant à `continue` (miroir de `assemblerJeu` — le bug
   était latent : tous les survols hérités livrés en 2c portaient des
   membres PROPRES, trouvés avant toute interface).
3. **La KDOC TIERCE vérifie le fichier déclarant** (garde D3 ADR 0208) :
   la KDoc voyage avec le SNAPSHOT du scan — un fichier déclarant DISPARU
   depuis le scan la servait encore (comportement « déclaration tierce
   ILLISIBLE : signature seule »). L'existence du fichier `file:` se
   vérifie maintenant au survol (une stat par requête) ; une URI non
   vérifiable (fixture) garde sa documentation (conservateur honnête).

### 5.3 Décisions d'exécution consignées

1. **Le plafond d'affichage attend le classement** : la fiche 046 exige
   `requestLayout` (membre hérité profond dans l'ordre de rencontre) — un
   plafond à 120 appliqué AVANT classement tronquait arbitrairement. Le
   `totalDisponible` est déjà honnête ; le trim arrive en 3c SUR LA LISTE
   ORDONNÉE (miroir exact du `trimToHalfLimit` d'IntelliJ : on trim ce
   qui est classé, jamais ce qui arrive).
2. **L'alias JVM ADR 0205 s'éprouve par les ENTRÉES** : `android-4.1.1.4`
   ne contient AUCUNE classe `java.lang.*` (artefact Maven android, pas
   un android.jar de plateforme — vérifié : 7 264 entrées, zéro
   `java/lang/`). Le maillon binaire `java.lang.String` du comportement
   « survol via l'alias JVM » vit donc dans un JAR DE FIXTURE
   (`OutilsJarFixture`, discipline pilotée par les entrées — miroir de la
   vue factice de l'ancien test, sans jamais contourner la vue JVM).
3. **Les mots-clés à préfixe vide sont le témoin du repli** : le germe
   2e rendait une liste VIDE à préfixe vide ; le pipeline propose
   désormais les mots-clés (liste FINIE) même à préfixe vide — c'est le
   contrat observable du repli honnête sur le régime de nom (fiche 043 :
   « le régime de nom reprend (mots-clés) »), les sources infinies
   restent muettes à préfixe vide.

### 5.4 Preuves du bac à sable (avant poussée)

- **Suite** : **358 tests VERTS** sur la toolchain 17 (337 + 21 de la
  fiche 045) ; baseline de parité RE-SIGNÉE **371 → 376 fichiers** (les
  5 neufs : 4 sources du pipeline + le test de la fiche).
- **Jar moteur** : 707 393 o (+31 321 vs clôture 2g).
- **Scan ART pile complète** : 6 × « AUCUNE » (moteur 271 classes /
  6 798 méthodes déclarées), rc=0.
- **Dexage d8** (build-tools 34 local, min-api 26) : **25 511 références
  de méthodes / 4 054 660 o (3,87 Mo)** — +91 méthodes / +23 860 o vs
  clôture 2g ; seuils tenus : 78 % du plafond 32 768, **64 % du budget
  6 Mo** (E-K8 inchangée).
- **Banc JDK 17** (bac à sable, 2 cœurs) : froid 197,2/20,8 ms,
  p50 7,0/20,5 ms, p95 36,1/54,4 ms, productions 5 448/31 368 et jetons
  4 210/24 274 — déterminisme IDENTIQUE (la grammaire n'est pas touchée),
  marqueurs d'erreur 0, tas 3 079 Kio.
- **Provenance** : `verifier-provenance.sh` rc=0 (aucune reprise — code
  NEUF intégral).
