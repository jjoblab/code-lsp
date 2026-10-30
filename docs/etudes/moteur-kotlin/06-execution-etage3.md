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

> **Mise à jour (31/10, vague 3b) — LA COMPLÉTION CONTEXTUELLE V1 EST
> LIVRÉE avec la fiche 043 ENTIÈRE (14 comportements) : les trois
> contextes repris en parité de l'ADR 0228 — ARGUMENTS NOMMÉS d'un appel
> résolu (les surcharges du projet du même nom, l'union des paramètres ;
> l'appel MEMBRE QUALIFIÉ lit les paramètres du MEMBRE typé ; les fournis
> en forme NOMMÉE exclus ; le filtre par préfixe ; insertion avec
> séparateur `nom = ` et signature écrite en détail), CONSTANTES D'ENUM
> des branches d'un `when` à sujet TYPÉ (projet : les entrées du modèle ;
> classpath : les champs STATIQUES typés par l'enum elle-même, la
> superclasse binaire `java.lang.Enum` exigée — l'INSTANCE d'un singleton
> n'y entre pas ; contexte EXCLUSIF, le corps d'une branche est muet),
> ATTRIBUTS d'annotation RÉSOLUE (les méthodes ABSTRAITES de la classe
> binaire, fournis exclus, insertion avec séparateur, FQCN en détail) —
> chaque contexte porte sa borne conservatrice D3 : la position de VALEUR
> (un littéral en cours de frappe couvre le caret, le slot après `=` d'un
> argument nommé TRONQUÉ — les ERROR_ELEMENT de plage nulle de la
> récupération d'erreur ne comptent pas comme valeur), l'appel inconnu,
> l'annotation non résolue et le sujet non enum retombent sur le RÉGIME DE
> NOM (mots-clés visibles — le témoin du repli honnête). Les sondes
> contextuelles courent AVANT le régime membre dans le pipeline : un caret
> dans la liste d'arguments d'un appel membre (`m.copier(|`) est une
> position d'ARGUMENT, pas de sélecteur.**

> **Mise à jour (31/10, vague 3c) — LA CHAÎNE DE CLASSEMENT EST EN
> NAISSANCE (ADR 0113 option 1, miroir de l'ADR 0012 Java) :
> `api/ClassementKotlin.kt` livre la correspondance GRADUÉE (six grades,
> bonus « plus petit = plus prioritaire » — le piège R1 documenté), les
> PESEURS comparés dimension par dimension PARESSEUSEMENT (PoidsPese
> mémoïsé, le miroir du `WeighingComparable`), la chaîne standard
> (correspondance → nature DÉPENDANTE du contexte → liftShorter →
> alphabétique → statistiques OPT-IN sur compteur d'usage local persistant
> purgé à la désactivation) et l'ARRANGEMENT (GEL des 8 têtes par clé de
> contexte document+genre+ancre STABLE pendant la frappe — contrat « zéro
> saut de liste » ; TRIM honnête : plafond 120 sur les espaces non bornés,
> 512 sur l'espace membre BORNÉ par la chaîne du récepteur — la frontière
> RPC du client y travaille sur l'espace complet, la fiche 045
> `requestLayout` en dépend) ; la chaîne est INJECTABLE au moteur (le banc
> comparera la livrée à la naïve alphabétique) ; 12 tests d'ingénierie
> verrouillent le mécanisme (§7).**

> **Mise à jour (31/10, vague 3d) — LES RÉCEPTEURS DE PORTÉE SONT LIVRÉS
> avec les fiches 038/039 ENTIÈRES (9 comportements — le dernier lot du
> domaine complétion, 44/44 au §2) : le paramètre `content:
> RowScope.() -> Unit` d'un appel prête RowScope comme this IMPLICITE du
> corps de la lambda — `TypageBorne.recepteurAttenduDeLambda` lit le
> candidat PROJET (le récepteur ÉCRIT du type fonctionnel du paramètre du
> même INDEX) puis le candidat CLASSPATH (la forme RENDUE `Function1<R,
> U>` du descripteur amont — `buildList { add(…) }` prête MutableList) ;
> la PILE des scopes emboîtés (Couches.aPosition et la passe sémantique
> empilent la couche PRÊTÉE par-dessus les couches englobantes — membres
> NUS, complétion et survol d'un SEUL mécanisme) ; les MEMBER-EXTENSIONS
> du scope (`fun Maison.orner()` déclarée DANS PorteeMaison — le
> sélecteur `Maison().orner()` résout par la pile) ; la DESTRUCTURATION
> (`(_, total) -> total` — chaque composant NOMMÉ lie, sans type écrit
> démontrable : la LIAISON seule résout) ; les PROPRIÉTÉS BUILTINS de Map
> (`entries`/`keys`/`values` → les accesseurs du maillon binaire, par
> CONVENTION close et documentée). La fiche 039 atteste le ViewBinding
> K5 SANS nouveau code moteur : le groupe synthétique livré en vague 1e
> offre root/textView/inflate/bind à la complétion membre de la propriété
> typée. Chaque famille porte son VRAI POSITIF — un membre inconnu DANS
> le scope reste signalé, le membre du scope HORS du lambda est signalé,
> un nom inconnu dans le corps d'une destructuration reste signalé : le
> silence n'est jamais de la complaisance (§8).**
>
> **Mise à jour (31/10, vague 3e — CLÔTURE DE L'ÉTAGE 3) — LE BANC COMMUN
> JAVA EST LIVRÉ ET L'ÉTAGE EST CLOS SUR PREUVE MÉCANIQUE : le corpus
> VERSIONNÉ `completion-kotlin v1` (16 positions étiquetées — les 7 genres
> du pipeline couverts, 5 divergences désignées, succession de frappe pour
> le contrat gel ; stdlib du runtime SEULE, projet corpus sur disque), le
> banc `:kotlin:benchQualite` (chaîne LIVRÉE vs NAÏVE alphabétique sur le
> MÊME corpus, métriques IDENTIQUES à `:java:benchQualite` : recall /
> top-1 / MRR / p95 / sauts), la baseline SIGNÉE
> `bancs/baselines/completion-kotlin.json` (livree 1.000/1.000/1.000,
> p95 7,53 ms ; naif 1.000/0.375/0.486 — **+62,5 pts top-1, +51,4 pts
> MRR**, la gate +5 points d'ADR 0113 dépassée ×12), la CI BLOQUANTE
> (job `kotlin-banc-qualite`, le 10e) — et le RECALAGE N° 2 de l'effort
> (§9.4) : les cinq critères de sortie §2 sont ATTESTÉS, dont le « zéro
> saut de liste » (0 saut sur la succession) et le p95 (7,53 ms ≪ borne
> 50 ms — la JVM reste la limite basse de la cible appareil < 100 ms).**

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
| 44 fiches vertes (fiches 038 + 039 + 043 + 045 du domaine complétion) | **44/44** (fiche 045 — §5 ; fiche 043 — §6 ; fiches 038/039 — §8) | `Fiche045SessionLangageKotlinK3Test` : 21 ; `Fiche043SessionLangageKotlinCompletionContextuelleTest` : 14 ; `Fiche038ReproductionVague5Test` : 8 ; `Fiche039ReproductionViewBindingTest` : 1 |
| Banc : recall ≥ baseline | **ATTEINT** (3e, §9) — livrée 1.000 = baseline signée 1.000 (= naïf 1.000 : l'espace candidat est le même, seule la CHAÎNE diffère) | `:kotlin:benchQualite` vert ; `bancs/baselines/completion-kotlin.json` |
| Banc : top-1/MRR +5 pts vs naïf | **ATTEINT (DÉPASSÉ ×12)** (3e, §9) — livrée 1.000/1.000 vs naïf 0.375/0.486 : **+62,5 pts top-1, +51,4 pts MRR** | `:kotlin:benchQualite` vert ; 5 divergences désignées + 5 positions membre (espace non filtré, frontière RPC) |
| Banc : p95 ≤ baseline (JVM = limite basse ; cible appareil < 100 ms) | **ATTEINT** (3e, §9) — 7,53 ms (première signature : borne absolue 50 ms, miroir Java ; marge ×6,6) | `:kotlin:benchQualite` vert (80 échantillons, 2 échauffements) |
| Zéro saut de liste (contrat gel) | **ATTEINT** (3e, §9) — 0 saut sur la succession (gel 3c tenu) | `:kotlin:benchQualite` vert (succession 4 frappes, clé de gel partagée) |

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

## 6. Vague 3b — la complétion contextuelle v1 (31/10, fiche 043 — 14 comportements)

### 6.1 Périmètre exécuté

| # | Item | État | Détail |
|---|---|---|---|
| 1 | Arguments nommés | **Fait** | `CompletionContextuelle.argumentsNommes` : la liste d'arguments d'APPEL couvrant le caret (la plus profonde — le PORTEUR voyage avec la liste, l'arbre n'a pas de pointeur parent) ; l'appel NU lit les surcharges du projet du même nom (`ModeleProjet.appelables`, union des paramètres) ; l'appel MEMBRE QUALIFIÉ type le récepteur et lit les paramètres du MEMBRE dans SES déclarations (les paramètres y vivent — un membre hérité du scan sans paramètres relus rend `null`, D3) ; les fournis en forme NOMMÉE exclus (noms lus des `VALUE_ARGUMENT_NAME`) ; insertion avec séparateur `nom = `, signature écrite en détail |
| 2 | Constantes d'enum | **Fait** | `CompletionContextuelle.constantesEnum` : le `when` couvrant le caret en ZONE DE CONDITION (après la fin du sujet, hors des CORPS de branches — après la flèche) ; le SUJET se type (première production enfant hors entrées) ; projet : les entrées du modèle ; classpath : les champs STATIQUES typés par l'enum ELLE-MÊME (`Ldemo/Couleur;`), la superclasse binaire `java.lang.Enum` EXIGÉE (l'INSTANCE d'un singleton typé par lui-même n'y entre pas) ; contexte EXCLUSIF |
| 3 | Attributs d'annotation | **Fait** | `CompletionContextuelle.attributsAnnotation` : la liste d'arguments d'ENTRÉE D'ANNOTATION couvrant le caret ; le nom écrit (CONSTRUCTOR_CALLEE → référence de type) se résout en classe du classpath ; les attributs sont les méthodes ABSTRAITES de la classe binaire (0x0401 — les accesseurs) ; fournis exclus, insertion avec séparateur, FQCN en détail ; contexte EXCLUSIF |
| 4 | Bornes D3 | **Fait** | `estPositionDeNom` : le caret dans le SLOT DE VALEUR d'un argument nommé (après son `=`), DANS une expression de valeur (un littéral en cours de frappe le couvre), ou dans le slot d'un nommé TRONQUÉ (nom écrit sans valeur — les `ERROR_ELEMENT` de plage nulle de la récupération d'erreur ne comptent pas) → repli sur le régime de NOM ; l'appel inconnu, l'annotation non résolue et le sujet non enum rendent `null` (repli honnête) |
| 5 | Ordre des sondes | **Fait** | Les sondes contextuelles courent AVANT le régime membre dans le pipeline : un caret dans la liste d'arguments d'un appel membre (`m.copier(|`) est une position d'ARGUMENT, pas de sélecteur — écart découvert par le comportement « appel membre qualifié » et corrigé |
| 6 | Tests neufs | **Fait** | 14 comportements aux noms exacts du cahier : `Fiche043SessionLangageKotlinCompletionContextuelleTest` — l'annotation `demo.Anno` et l'enum `demo.Couleur` du classpath vivent dans un JAR DE FIXTURE (superclasse `java.lang.Enum`, méthodes abstraites, champ d'instance `ordre` hors jeu) |

### 6.2 Décisions d'exécution consignées

1. **L'union des surcharges** : un appel nu résolu à plusieurs surcharges du
   même nom propose l'UNION de leurs paramètres — l'arbitrage par arité et
   type relève du typage complet (fiche 046, déjà livré) et du futur mode
   profond ; l'union est honnête (aucun paramètre réel manquant).
2. **Les seuls fournis NOMMÉS sont exclus** : les arguments positionnels
   fournis n'excluent RIEN (leur appariement aux paramètres exige
   l'arbitrage des surcharges — miroir du comportement source « déjà
   fourni en forme nommée exclu »).
3. **Le marqueur binaire d'enum** : la superclasse `java.lang.Enum` exigée
   avant de proposer des « constantes » d'un type du classpath — un
   singleton `object` JVM (champ `INSTANCE` typé par lui-même) n'entre
   jamais au contexte enum.

### 6.3 Preuves du bac à sable (avant poussée)

- **Suite** : **372 tests VERTS** sur la toolchain 17 (358 + 14 de la
  fiche 043) ; baseline de parité RE-SIGNÉE **376 → 378 fichiers** (les
  2 neufs : `CompletionContextuelle.kt` + le test de la fiche).
- **Jar moteur** : 719 857 o (+12 464 vs 3a).
- **Scan ART pile complète** : 6 × « AUCUNE », rc=0.
- **Dexage d8** (build-tools 34 local, min-api 26) : **25 529 références
  de méthodes (78 % du plafond) / 4 064 332 o = 3,88 Mo (65 % du budget
  6 Mo)**.
- **Banc JDK 17** : froid 246,1/25,5 ms, p50 6,7/26,9 ms, p95 35,1/57,1 ms,
  productions 5 448/31 368 et jetons 4 210/24 274 — déterminisme
  IDENTIQUE, marqueurs d'erreur 0, tas 3 845 Kio.
- **Provenance** : `verifier-provenance.sh` rc=0 (aucune reprise).

## 7. Vague 3c — la chaîne de classement en naissance (31/10, ADR 0113 option 1)

### 7.1 Périmètre exécuté

| # | Item | État | Détail |
|---|---|---|---|
| 1 | Correspondance graduée | **Fait** | `api/ClassementKotlin.kt` : `GradeCorrespondanceKotlin` (EXACT / PREFIXE / PREFIXE_INSENSIBLE / HUMP / SUBSTRING / HORS) et `CorrespondanceGradueeKotlin` (grade + bonus, la convention « plus petit = plus prioritaire » — le piège R1 de l'ADR 0012 documenté dans le KDoc) ; HUMP : chaque caractère du préfixe se pose sur une OUVERTURE de bossage (début, majuscule, après tiret bas) ; HORS ferme la marche — le régime membre ne FILTRE pas par préfixe (le re-filtrage local par frappe est la frontière RPC de l'hôte, ADR 0118) : les hors-préfixe se classent en fin, jamais écartés |
| 2 | Peseurs dimension par dimension | **Fait** | `PeseurKotlin` (interface publique) + `PoidsPeseKotlin` (le miroir du `WeighingComparable` : chaque dimension se calcule UNE fois, à la demande, mémoïsée) + `ChaineClassementKotlin` (comparateur dimension par dimension PARESSEUX — la dimension k ne se calcule qu'à égalité des précédentes) |
| 3 | La chaîne standard | **Fait** | Correspondance (dominante) → Nature (DÉPENDANTE du contexte : import = paquets dominants, membre = propriétés avant fonctions, nom = locales avant tout, mots-clés derniers) → Longueur (liftShorter) → Alphabetique (départage déterministe) → Statistiques (OPT-IN) |
| 4 | Compteur d'usage local | **Fait** | `CompteurUsageKotlin` : fichier de propriétés `nom=fréquence`, local au poste, OPT-IN, purgé à la désactivation (test de cycle de vie : incrément, relecture par une NOUVELLE instance, purge supprime le fichier) |
| 5 | Arrangement | **Fait** | `ArrangementListeKotlin` : classement + GEL des têtes (les [GEL_MAX] = 8 premiers libellés par clé de contexte ; les survivants reprennent LEUR rang exact — un nouveau venu ne déplace JAMAIS une tête affichée, contrat « zéro saut de liste ») + TRIM honnête (plafond + total réel + drapeau `tronquee`) |
| 6 | Clé de contexte du gel | **Fait** | Document + genre effectif + ANCRE : le DÉBUT (stable pendant la frappe) de la plus profonde production ANCRANTE couvrant le caret (sélecteur, appel, `when`, annotation, déclaration porteuse, directive d'import) — deux frappes successives de la MÊME expression partagent la clé |
| 7 | Plafond par régime | **Fait** | `LIMITE_AFFICHAGE` = 120 pour les espaces NON BORNÉS (noms, imports — le classpath est profond) ; `LIMITE_MEMBRE` = 512 pour l'espace membre BORNÉ par construction (les membres de la chaîne du récepteur, profondeur 7 — ADR 0219) : le re-filtrage local du client (frontière RPC) travaille sur l'espace complet — c'est ce qui tient la fiche 045 (`requestLayout` hérité profond) SANS sacrifier l'honnêteté du trim |
| 8 | Chaîne injectable | **Fait** | `MoteurLangageKotlinNoyau(chaineCompletion = …)` → chaque session porte SON arrangement (gels à session, états étanches) ; le banc de qualité comparera la chaîne livrée à la naïve alphabétique (`ChaineClassementKotlin(listOf(PeseurAlphabetiqueKotlin()))`) |
| 9 | Tests d'ingénierie | **Fait** | `ClassementCompletionTest` (12 tests — PAS une fiche du cahier : le MÉCANISME 3c, les fiches 043/045 tenant la parité) : les six grades, la dominance (EXACT → nature → liftShorter), la nature par contexte (membre, import), le départage alphabétique, le gel (affinement sans descente, nouveau venu immobile, clés indépendantes), le trim (plafond + total + drapeau ; membre servi entier ≤ 512), le compteur (cycle de vie), la chaîne naïve |

### 7.2 Décisions d'exécution consignées

1. **Le grade HORS** : le régime membre ne filtre pas par préfixe côté
   serveur (le split RPC envoie l'espace complet et son classement — le
   client re-filtre à chaque frappe) ; les propositions hors préfixe se
   classent donc en FIN (HORS) plutôt que d'être écartées — la chaîne
   reste utile aux DEUX mondes (serveur filtrant, client filtrant).
2. **Deux plafonds, un seul contrat** : le plafond n'a de sens que sur un
   espace non borné ; l'espace membre est borné par la chaîne du
   récepteur (profondeur 7) — le servir entier (≤ 512) préserve la
   frontière RPC SANS mentir sur le total (le drapeau `tronquee` existe
   et sert dès que l'espace déborde).
3. **L'ordre des dimensions reprend l'ADR 0012** (weighers paresseux,
   gel, liftShorter, trim honnête) — la transposition est STRUCTURELLE
   (les designs IntelliJ étudiés par l'étude Java), jamais textuelle :
   aucun code IntelliJ n'a été lu ni copié pour ce module (R6/R9).

### 7.3 Preuves du bac à sable (avant poussée)

- **Suite** : **384 tests VERTS** sur la toolchain 17 (372 + 12 du
  classement) ; baseline de parité RE-SIGNÉE **378 → 380 fichiers** (les
  2 neufs : `api/ClassementKotlin.kt` + `ClassementCompletionTest.kt`).
- **Jar moteur** : 748 226 o (+28 369 vs 3b).
- **Scan ART pile complète** : 6 × « AUCUNE », rc=0.
- **Dexage d8** (build-tools 34 local, min-api 26) : **25 621 références
  de méthodes (78 % du plafond) / 4 083 016 o = 3,89 Mo (65 % du budget
  6 Mo)**.
- **Banc JDK 17** : froid 192,9/51,4 ms, p50 7,1/24,6 ms, p95 30,3/51,8 ms,
  productions 5 448/31 368 et jetons 4 210/24 274 — déterminisme
  IDENTIQUE, marqueurs d'erreur 0, tas 3 845 Kio.
- **Provenance** : `verifier-provenance.sh` rc=0 (aucune reprise).

## 8. Vague 3d — les récepteurs de portée (31/10, fiches 038 + 039 — 9 comportements)

### 8.1 Périmètre exécuté

| # | Item | État | Détail |
|---|---|---|---|
| 1 | Récepteur attendu d'une lambda | **Fait** | `TypageBorne.recepteurAttenduDeLambda` : le FUNCTION_LITERAL englobant, sa LAMBDA_EXPRESSION, puis le parent LAMBDA_ARGUMENT (traînant) ou VALUE_ARGUMENT (parenthésé, indice compté) mène à l'APPEL ; `recepteurDeLappel` lit le candidat PROJET (`univers.modele.appelables` du même nom — le récepteur ÉCRIT du type fonctionnel du paramètre du même index) puis le candidat CLASSPATH visible (`univers.index.topParNom` — la forme RENDUE `Function1<R, U>` du paramètre, `premierArgumentFunction1` dépouille les génériques imbriqués : `MutableList<E>` → `MutableList`) ; `null` sans attente démontrable (lambda orpheline, appel inconnu, type sans récepteur — D3) |
| 2 | Résolution du récepteur ÉCRIT hors document | **Fait** | `resoudreRecepteurEcrit` : le document courant d'abord (types du projet, imports, défauts), PUIS le nom simple UNIQUE des ENTRÉES du classpath (`VueJvmMembres.fqcnParNomSimple`) — le récepteur d'un scope classpath (`Ecran(bloc: PorteeEtrangere.() -> Unit)`) déclaré dans un AUTRE fichier, sans import au point d'usage, s'y désigne sans équivoque ; un homonyme multiple rend `null` (jamais deviner) |
| 3 | Pile des scopes emboîtés | **Fait** | `Couches.aPosition` (branche LAMBDA_EXPRESSION : empile `deType(univers, recepteurAttendu)` avant de marcher le corps) et `DiagnosticsSemantiques.marcher` (miroir — la passe sémantique porte SA pile) : le corps voit la couche PRÊTÉE par-dessus les couches englobantes — `Exterieur { dansA(); Interieur { dansB(); dansA() } }` résout par les DEUX couches, la plus profonde d'abord ; membres NUS (`compter()`), complétion du régime NOM (`membresDetaillesDesCouches`) et member-extensions d'un SEUL mécanisme |
| 4 | Member-extensions du scope | **Fait** | `MembreProjet.recepteurNomme` (posé par `recepteurEcritMembre` : la référence de type portée AVANT la liste des paramètres du membre — `fun Maison.orner()` déclarée dans le corps de PorteeMaison) ; `verifierSelecteurProjet(…, pile)` : un membre DÉCLARÉ d'une couche PROJET de la pile dont le récepteur ÉCRIT résout au type du sélecteur → RESOLU ; la signature `verifierSelecteur` porte la pile (défaut VIDE — non-régression des appelants existants) |
| 5 | Destructuration de paramètre | **Fait** | `MarcheTypee` : les entrées `DESTRUCTURING_DECLARATION_ENTRY` du paramètre lient comme CANDIDATS (position de fin, type `null`) — `estLie(nom)` rend la LIAISON seule suffisante au verdict (`{ (_, total) -> total }`, `{ (cle, montant) -> … }`) ; le typage par `componentN` relève du modèle complet (bornologie ADR 0107 inchangée) ; le VRAI POSITIF tenu : `totalInconnu` dans le corps reste SIGNALÉ (le destructuring ne crée pas de nom) |
| 6 | Propriétés builtins de Map | **Fait** | `BUILTINS_PROPRIETES` (par maillon BINAIRE : `java.util.Map` → `entrySet`→`entries`, `keySet`→`keys`, `values`→`values`) consulté au VERDICT de sélecteur ET à la complétion membre DÉTAILLÉE (proposées sous le nom KOTLIN quand l'accesseur JVM vit dans le jeu) ; `MAPPES_JVM` étendu aux collections (`kotlin.collections.Iterable/Collection/List/MutableList/Set/MutableSet/Map/MutableMap` → les `java.*` encadrants — la vérité d'interop du compilateur) |
| 7 | ViewBinding K5 (fiche 039) | **Fait** | SANS nouveau code moteur : le groupe synthétique livré en vague 1e (reprise R1/R2 — `ClassesSynthetiques`) offre `root`/`textView`/`inflate`/`bind` à la complétion membre de la propriété typée (`binding: ActivityMainBinding`, sous-paquet `databinding` — convention AGP) ; la fiche de reproduction en est le PREMIER TÉMOIN cahier (aucun consommateur spéculatif n'avait été livré en 1e — la capacité attendait sa preuve) |
| 8 | Tests neufs | **Fait** | 9 comportements aux noms exacts du cahier : `Fiche038ReproductionVague5Test` (8 — scopes PROJET et CLASSPATH, pile, buildList, destructuration, builtins Map) et `Fiche039ReproductionViewBindingTest` (1 — projet Android factice sur disque : manifeste + res/layout `@+id/textView`) ; la vérité JVM des mondes classpath vit dans un JAR DE FIXTURE (`exo.cp.PorteeEtrangere`, `java.util.List`/`Collection`/`Iterable`, `java.util.Map` — discipline pilotée par les entrées) |

### 8.2 Décisions d'exécution consignées

1. **Le maillon `java.lang.Iterable` du jar de fixture** : la première
   rédaction déclarait `java.util.Collection` superclasse `Object` — les
   extensions `sortedByDescending`/`map` de l'index vivent sur
   `kotlin.collections.Iterable` (le maillon DISTANCIÉ du récepteur,
   miroir fiche 033) et restaient INATTEIGNABLES : le comportement
   « composants nommés en portée » les exigeait résolus. Le fixture
   modélise maintenant la chaîne JVM RÉELLE (`java.util.Collection`
   étend `java.lang.Iterable`, maillon posé sans membre) — la VÉRITÉ
   JVM que le stdlib seul ne porte pas, discipline pilotée par les
   entrées tenue de bout en bout.
2. **La liaison seule résout le composant destructuré** : `(_, total)`
   n'a pas de type écrit DÉMONTRABLE (le typage exige `componentN` du
   modèle complet) — la preuve de résolution tient par la seule
   EXISTENCE de la liaison (miroir du régime des paramètres non typés).
   Le vrai positif reste émis : un nom INCONNU dans le corps du lambda
   n'est pas couvert par la liaison — le destructuring ne crée pas de
   nom (comportement « nom inconnu dans le corps reste signalé »).
3. **Les builtins par CONVENTION, jamais par dérivation** :
   `entrySet()` → `entries` mais `keySet()` → `keys` (et non `keySet`)
   — la table est CLOSE et documentée dans le KDoc de
   `BUILTINS_PROPRIETES` : c'est la convention d'interop du compilateur
   Kotlin, pas une règle dérivable des accesseurs. Une dérivation
   générique (`get*`/`*Set` → propriété) inventerait des membres que le
   compilateur n'expose pas.
4. **Coquille 3a corrigée (§5.4)** : le « moteur 271 classes /
   6 798 méthodes déclarées » de la vague 3a était une erreur de
   transcription — 6 798 est l'ordre de grandeur du compte du STDLIB
   (6 612 méthodes, §8.3), pas du moteur. Les valeurs MESURÉES ce jour :
   moteur **268 classes / 296 méthodes déclarées** (borne basse dex),
   cohérentes avec la série de l'étage 2 (189/250 → 208/267 → 225/276).
   Aucun code n'est affecté — le chiffre erroné ne nourrissait aucun
   seuil (la gate CI compte les « AUCUNE », pas les classes).
5. **La fiche 039 sans nouveau code moteur** : le groupe synthétique
   était livré depuis la vague 1e mais AUCUNE fiche du cahier ne
   l'éprouvait (les fiches 040/045/047 touchaient Views/Compose/K5
   d'autres familles) — la reproduction ViewBinding EST cette preuve,
   et elle passe SANS adaptation : la capacité attendait son témoin,
   le témoin n'a exigé aucune retouche (la meilleure des non-régressions).

### 8.3 Preuves du bac à sable (avant poussée)

- **Suite** : **393 tests VERTS** sur la toolchain 17 (384 + 9 des
  fiches 038/039) ; baseline de parité RE-SIGNÉE **380 → 382 fichiers**
  (les 2 tests neufs).
- **Jar moteur** : 754 695 o (+6 469 vs 3c).
- **Scan ART pile complète** : 6 × « AUCUNE », rc=0 (moteur 268 classes /
  296 méthodes déclarées — borne basse ; coquille 3a corrigée, §8.2.4 ;
  ServiceLoader OK descripteur vérifié).
- **Dexage d8** (build-tools 34 local, min-api 26) : **25 636 références
  de méthodes (78 % du plafond 32 768) / 4 090 064 o = 3,90 Mo (65 % du
  budget 6 Mo)** — +15 méthodes / +7 048 o vs 3c ; seuils tenus (E-K8
  inchangée).
- **Banc JDK 17** (bac à sable, 2 cœurs) : froid 209,9/21,0 ms,
  p50 7,4/22,1 ms, p95 34,0/49,4 ms, productions 5 448/31 368 et jetons
  4 210/24 274 — déterminisme IDENTIQUE (la grammaire n'est pas
  touchée), marqueurs d'erreur 0, tas 3 845 Kio.
- **Provenance** : `verifier-provenance.sh` rc=0 (aucune reprise — code
  NEUF intégral, zones noyau anciennes jamais consultées R9.1).

## 9. Vague 3e — la clôture : banc commun Java, recalage n° 2 (31/10)

### 9.1 Périmètre exécuté

| # | Item | État | Détail |
|---|---|---|---|
| 1 | Corpus VERSIONNÉ | **Fait** | `banc/CorpusQualiteCompletion.kt` : `completion-kotlin v1` — 16 positions étiquetées couvrant les 7 genres du pipeline (MEMBRE ×5 — extension stdlib réelle, accès sûr `?.`, héritage projet par le scan, fonction propre, divergence de nature ; NOM ×4 — locale dominante, propriété implicite, type classpath par préfixe, mot-clé témoin du repli ; IMPORT ×3 — sous-paquet, paquet-dominant à préfixe vide, liftShorter ; ARGUMENTS_NOMMES ; CONSTANTES_ENUM ×2 — préfixé et vide ; ATTRIBUTS_ANNOTATION), 5 divergences désignées ★ (la naïve les perd par le MÉCANISME isolé), succession de frappe à 4 frappes (clé de gel partagée délibérément) ; stdlib du runtime SEULE (ancre de classe `Intrinsics`, épinglée 2.2.10 par la chaîne d'outils — le banc tourne partout, CI comprise, sans SDK), projet corpus Widget/Outil sur DISQUE (résolu par le scan des racines, comme une vraie frappe) ; **UNE URI PAR POSITION** (la clé de gel document+genre+ancre ne peut JAMAIS être partagée entre positions de mesure) |
| 2 | Banc de qualité | **Fait** | `banc/BancQualiteCompletion.kt` + tâche `:kotlin:benchQualite` : chaîne LIVRÉE (`MoteurLangageKotlinNoyau()` standard) vs NAÏVE (`ChaineClassementKotlin(listOf(PeseurAlphabetiqueKotlin()))`) sur le MÊME corpus, métriques IDENTIQUES à `:java:benchQualite` (recall / top-1 / MRR / p95 / sauts) ; montage par le CHEMIN DE PRODUCTION D6 (moteur nourricier par le constructeur public, session branchée, guichet attendu par sondage borné — l'association de compilations KGP `bench → main` donne au banc le sondeur `indexSiPret`, le miroir exact du monteur de la fiche 045 : JAMAIS un raccourci synchrone qui n'existerait pas côté hôte) ; p95 sur 2 échauffements + 5 passes mesurées (la mesure couvre l'ouverture ET l'arrangement — le coût d'une frappe) ; JVM bornée `-Xmx2g -XX:+UseSerialGC` sur la toolchain 17 (exigence appareil, miroir `:kotlin:bench`) |
| 3 | Baseline SIGNÉE | **Fait** | `bancs/baselines/completion-kotlin.json` — `livree` recall 1.000 / top1 1.000 / mrr 1.000 / p95 7,53 ms ; `naif` 1.000 / 0.375 / 0.486 ; tolérance latence p95 ≤ max(50 ms, baseline × 1,5) ; re-signature par PR dédiée (`-Presigner`) ; le commit qui la porte est la signature |
| 4 | CI BLOQUANTE | **Fait** | Job `kotlin-banc-qualite` (le 10ᵉ de `ci.yml`, miroir de `banc-qualite` Java) : la tâche ÉCHOUE sur régression de baseline, sur gate de valeur (+5 pts), sur saut de liste, sur dépassement p95 et sur les contrôles de santé |
| 5 | Contrôles de santé | **Fait** | (a) DÉTERMINISME des genres : les 16 verdicts doivent égaler les genres attendus (un verdict qui change d'un run à l'autre est un bug, pas une mesure) ; (b) **STABILITÉ SOUS GEL** (propre au Kotlin, motivée par la frontière RPC) : la chaîne livrée re-mesurée sur la MÊME session (gels constitués) doit rendre des rangs IDENTIQUES — un rang qui bouge dénonce une contamination de gel entre positions (clé d'ancre partagée) ; (c) preuve de valeur : livrée ≥ naïf sur recall, +5 pts top-1/MRR |
| 6 | Recalage n° 2 de l'effort | **Fait** | §9.4 — la première donnée de CLASSEMENT y est mesurée (la dominante inconnue du recalage n° 1) |

### 9.2 Décisions d'exécution consignées

1. **UNE URI PAR POSITION (la collision d'ancres est réelle, elle a été
   VÉCUE)** : la première rédaction du corpus mettait toutes les
   positions dans des variantes d'un MÊME document — deux positions
   membre dont les fonctions porteuses avaient des noms de MÊME
   LONGUEUR (`membreHeritage`/`membreFonction`, récepteurs `v`/`x`)
   partageaient l'OFFSET d'ancre du sélecteur, donc la CLÉ de gel : le
   gel constitué par la première (`v.ide¦` → têtes
   `[identifiant, zCount, …]`) gélait le gagnant de la seconde
   (`x.zAdd` au rang 3 au lieu de 0 — les survivants reprennent LEUR
   rang). L'écart était STABLE entre les passes, donc invisible au seul
   contrôle de stabilité : l'URI par position rend la collision
   structurellement IMPOSSIBLE (la clé intègre le document), la passe
   de stabilité reste en garde mécanique. La succession de frappe, elle,
   partage SA clé délibérément — c'est le contrat qu'elle mesure.
2. **La divergence IMPORT se joue à préfixe VIDE** : la collecte filtre
   par préfixe SENSIBLE À LA CASSE (`Annotation` n'est pas collecté au
   préfixe `annotation`) et les BUILTINS `Any`/`Annotation`/`String`
   n'existent pas comme ENTRÉES de classe du jar (ils vivent dans les
   `.kotlin_builtins` — vérifié : zéro `kotlin/Any.class`,
   `kotlin/Annotation.class` ; les types directs servis sont les ~96
   entrées `*.class`). Le niveau `kotlin` à préfixe vide (22 paquets +
   96 types) est donc le seul endroit où paquet-avant-type s'isole :
   livrée rend `io` (paquet, 2 lettres, liftShorter après la nature),
   naïf rend `ArrayIntrinsicsKt` et enterre les paquets (`io` au rang
   112).
3. **La naïve perd TOUTES les positions membre — c'est la MESURE, pas un
   artefact** : le régime membre ne FILTRE pas par préfixe côté serveur
   (frontière RPC ADR 0118 : le client re-filtre à chaque frappe), la
   chaîne naïve rend donc l'espace ENTIER en ordre alphabétique — `trim`
   au rang 106 derrière `byteInputStream` (l'espace des extensions
   stdlib sur String dépasse la centaine), `zCount` au rang 11 derrière
   `clone`. Le banc Java n'a pas ce phénomène (sa collecte filtre avant
   arrangement) : la différence est STRUCTURELLE et documentée dans le
   corpus, jamais contournée — elle mesure précisément la valeur de la
   correspondance graduée sur l'espace complet.
4. **La borne p95 absolue est le miroir exact du banc Java (50 ms)** :
   mesuré 7,53 ms (marge ×6,6) ; la cible §4 reste « < 100 ms sur
   APPAREIL », la JVM en est la limite basse — la mesure couvre
   l'ouverture du document ET l'arrangement (le coût d'une frappe), 2
   échauffements + 5 passes de 16 positions (80 échantillons).
5. **L'association de compilations `bench → main` est le seul moyen
   honnête d'attendre le nourrissage** : le SPI public n'expose pas de
   guichet d'attente (la publication du snapshot est asynchrone par
   design, ADR 0211 — le moteur rend AVANT la fin de la passe). Le banc
   mesure le chemin de production (sondage borné du guichet
   `indexSiPret`, miroir du monteur de la fiche 045), pas un montage
   synchrone qui n'existerait pas côté hôte ; l'association KGP donne au
   banc la visibilité `internal` du sondeur, SANS toucher au code du
   moteur (aucune ligne main modifiée — le jar reste au octet près
   celui de la 3d).
6. **La baseline de parité suit le corpus (382 → 384)** : les deux
   fichiers de banc entrent au corpus de parité de la fiche 010 —
   re-signature par le protocole habituel
   (`-Dparite.majBase=true --rerun-tasks`), aucune mesure de la baseline
   fidélité/complétion n'en dépend.

### 9.3 Preuves du bac à sable (avant poussée)

- **Banc de qualité** : **VERT** — livrée recall **1.000** / top-1
  **1.000** / MRR **1.000** ; naïf 1.000 / **0.375** / **0.486** →
  **+62,5 pts top-1, +51,4 pts MRR** (gate +5 points d'ADR 0113
  dépassée ×12) ; **p95 7,53 ms** (min 0,37 / max ~26, 80 échantillons,
  borne 50 ms) ; **sauts 0** (succession 4 frappes) ; genres 16/16
  DÉTERMINISTES ; stabilité sous gel IDENTIQUE (re-mesure sur la même
  session : rangs égaux).
- **Baseline signée** : `bancs/baselines/completion-kotlin.json`
  (corpus `completion-kotlin v1`, 16 positions ; le commit = signature).
- **Suite** : **393 tests VERTS** sur la toolchain 17 — INCHANGÉS (la
  3e n'ajoute AUCUN test : le banc est une `JavaExec` de vérification,
  pas une fiche) ; baseline de parité RE-SIGNÉE **382 → 384 fichiers**
  (les 2 fichiers de banc).
- **Jar moteur** : 754 695 o — **INCHANGÉ au octet près vs 3d** (la
  vague ne touche AUCUN code main : le banc vit dans le source set
  `bench`).
- **Scan ART pile complète** : 6 × « AUCUNE », rc=0 (moteur 268 classes /
  296 méthodes déclarées — borne basse ; ServiceLoader OK descripteur
  vérifié).
- **Dexage d8** (build-tools 34 local, min-api 26) : **25 636 références
  de méthodes (78 % du plafond 32 768) / 4 090 064 o = 3,90 Mo (65 % du
  budget 6 Mo)** — IDENTIQUES à la 3d (aucun code main touché ; E-K8
  inchangée).
- **Banc JDK 17** (bac à sable, 2 cœurs) : froid 190,6/22,5 ms,
  p50 4,8/23,0 ms, p95 40,0/53,1 ms, productions 5 448/31 368 et jetons
  4 210/24 274 — déterminisme IDENTIQUE, marqueurs d'erreur 0, tas
  3 694 Kio.
- **Provenance** : `verifier-provenance.sh` rc=0 (aucune reprise — code
  NEUF intégral, zones noyau anciennes jamais consultées R9.1).

### 9.4 Clôture de la vague 3e, de l'ÉTAGE 3 — et recalage n° 2 de l'effort

**CI VERTE 9/9 sur les QUATRE commits de livraison 3a→3d, PREMIÈRE
TENTATIVE** (runs `41127294348`, `41129037195`, `41130310022`,
`41136152557` — consultés le 31/10 via l'API GitHub Actions) — **et CI
VERTE 10/10 sur le commit de livraison 3e `cb562f2` (run `41142982589`,
PREMIÈRE TENTATIVE)** : le nouveau job `kotlin-banc-qualite` y est VERT
— la baseline SIGNÉE est tenue dans l'ENVIRONNEMENT CI (les nombres
déterministes du corpus y sont reproduits : recall/top-1/MRR au bit
près, p95 sous la borne), la gate de valeur, le contrat gel et les
contrôles de santé y passent à chaque build désormais.

**LES CINQ CRITÈRES DE SORTIE DE L'ÉTAGE 3 (§2) SONT ATTESTÉS** :

1. **44/44 comportements complétion verts** (fiches 045/043/038/039 —
   §5, §6, §8) ;
2. **Banc : recall ≥ baseline** — livrée 1.000, baseline signée 1.000 ;
3. **Banc : top-1/MRR +5 pts vs naïf** — +62,5 / +51,4 pts (ADR 0113
   VALIDÉ, la gate est dépassée ×12) ;
4. **Banc : p95 ≤ baseline** — 7,53 ms (première signature : borne
   absolue 50 ms, miroir Java ; JVM = limite basse de la cible appareil
   < 100 ms) ;
5. **Zéro saut de liste** — 0 saut sur la succession (le gel 3c tient le
   contrat pendant l'affinement du préfixe).

**L'ÉTAGE 3 EST CLOS sur preuve mécanique** : le pipeline de complétion
(classification du caret, régimes nom/membre/import, annuaire paresseux
des types du classpath, mots-clés toujours), la complétion
contextuelle v1 (arguments nommés, constantes d'enum, attributs
d'annotation — bornes D3 et replis honnêtes), la chaîne de classement
en naissance (correspondance graduée, peseurs dimension par dimension
paresseux, nature dépendante du contexte, liftShorter, gel des têtes,
trim honnête — injectable, comparée), les récepteurs de portée (pile
des scopes, member-extensions, destructuration, builtins Map,
ViewBinding K5) — et la QUALITÉ de tout cela est désormais une DONNÉE
CHIFFRÉE comparée à chaque build, sur banc COMMUN avec Java. L'étage 4
(navigation + rename — 38 fiches, rappel usages = oracle) est le
suivant.

#### Le recalage n° 2 de l'effort (miroir `RECALAGE-1.md`)

Promis par le recalage n° 1 (« recalage n° 2 à la clôture de l'étage 3
— la première donnée de CLASSEMENT y sera mesurée », `05-execution-
etage2.md` §11.4) — exécuté à SA porte cette fois.

**Ce que l'étage 3 a coûté** [mesuré — commandes : `git log
--oneline 8ff747c..HEAD`, `git diff --shortstat 8ff747c..HEAD`, compte
des tests des journaux §5–§9] :

| Grandeur | Valeur | Détail |
|---|---:|---|
| Commits de l'étage | 5 | 3a→3e : livraisons + clôture (borne : clôture étage 2 `8ff747c`) |
| Fichiers touchés | 32 | +6 113 / −238 lignes (main, tests, banc, CI, journaux) |
| `kotlin/` main | 19 fichiers, +2 742 / −223 | pipeline, contextuelle, classement, récepteurs |
| `kotlin/` test | 6 fichiers, +1 817 / −13 | fiches 045/043/038/039 + 12 tests du mécanisme 3c |
| `kotlin/` bench | 2 fichiers, +837 | corpus versionné (456 l.) + banc de qualité (381 l.) — la 3e |
| Tests du module | 337 → **393** | +56 pour 44 comportements et 12 tests d'ingénierie (≈ 1,1 test/comportement — 21 + 14 + 9 fiches + 12 mécanisme) |
| CI première tentative | **4/4** commits de livraison | runs `41127294348` / `41129037195` / `41130310022` / `41136152557` |
| Tempo [mesuré-agent] | 31/10 03:11 → ~08:00 (UTC+8) | 5 vagues en ~5 h de séance continue |

**Comparaison à l'estimation** : la fourchette de l'étage 3 était
**30–48 j-h [estimé]** (synthèse §4). Le rythme agent n'est PAS
transposable au jour-homme humain (miroir `RECALAGE-1.md` §3.4) :
l'étage a été couvert en ~5 h de séance continue, à ≈ **135 lignes par
comportement** (≈ 6 100 l. / 44 comport. — main, tests, banc ET journaux
compris). La donnée utile pour l'abaque : le coût marginal est resté
STABLE et la CI est passée en PREMIÈRE TENTATIVE sur les QUATRE
commits de livraison (l'étage 2 : 6/7) — aucune refonte, aucun retour
arrière, la clôture 3e elle-même n'a exigé AUCUNE retouche du code
livré en 3a–3d (le jar est inchangé au octet près).

**La première donnée de CLASSEMENT** [mesuré] — la dominante inconnue
du recalage n° 1 : la vague 3c (le design — chaîne, peseurs, gel) a
coûté **1 fichier main (581 l.) + 1 fichier de tests (326 l., 12 tests)**,
soit ~15 % du volume de l'étage ; les vagues de PARITÉ (3a/3b/3d —
fiches 045/043/038/039) en ont coûté ~70 %. La mesure du banc donne au
design sa preuve de valeur : **+62,5 pts top-1 / +51,4 pts MRR** contre
la naïve alphabétique, p95 7,53 ms, zéro saut — pour un coût qui
restera MARGINAL dans les étages suivants (la chaîne est LIVRÉE, les
étages 4–6 la CONSUMENT : les propositions de navigation et de confort
se classent par elle). Le « retard majeur » de complétion Kotlin est
désormais une donnée pilotable : corpus, baseline et CI sont en place,
toute dérive est un échec de build.

**Re-projection** [estimé] : les étages restants (4 navigation/rename,
5 confort, 6 serveur LSP, T transverse) restent à l'abaque de la
synthèse §4 — **68–108 j-h** cumulés. Aucune donnée ne motive une
révision : le signal du recalage n° 1 (stabilité du socle) est
CONFIRMÉ (aucune refonte en étage 3, CI 4/4 première tentative), et la
dominante classement s'est révélée MOINS COÛTEUSE que postulé (design
transposé de l'ADR 0012 Java déjà éprouvé — la vague 3c est la plus
petite de l'étage). La navigation (étage 4) reste la dernière dominante
inconnue (occurrences, usages inter-documents, rename borné) ;
**recommandation : recalage n° 3 à la clôture de l'étage 4** si son
exécution révèle un écart au rythme observé — sinon l'abaque tient
jusqu'à la clôture du programme (les 2 recalages promis au plan T sont
faits : n° 1 à l'étage 2 §11.4, n° 2 ici).
