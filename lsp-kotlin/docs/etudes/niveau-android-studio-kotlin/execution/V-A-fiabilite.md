# Journal d'exécution — Vague V-A : Fiabilité (P0)

> Vague V-A du [plan](../04-plan-vagues.md) — exécutée le **12/11/2026**.
> Périmètre livré : (a) la correction des HUIT familles de faux positifs
> mesurées (GK-002), (b) les budgets d'octets + l'observateur mémoire
> `onTrimMemory` par SPI neutre (GK-031, ADR 0117/0134), (c) la fraîcheur
> inter-fichiers par empreintes STAT (GK-038, miroir du schéma Java ADR
> 0025). La partie (d) — bascule CodeIDE I0–I6 + publication du pack —
> était DÉJÀ FAITE (ADR 0136 étape A, `v1.13.0` du 03/11/2026).

## 1. La mesure AVANT (le point de départ, chiffres imprimés)

P1 auto-corpus (99 fichiers, 34 934 lignes) : **980 FP = 28,05 FP/1 000 l.**
(erreur oracle : 1 — un vrai positif), FN = 0. Familles mesurées (audit
§4.3 confirmé) :

| Famille | FP | Mécanisme racine |
|---|---:|---|
| `it` | 603 | `marcherCheminFqn` contournait la garde de l'implicite : `it.name` émettait la RACINE `it` en chemin FQN |
| imports implicites `kotlin.*` (IntArray…) | ~100 | types des paquets par défaut absents du catalogue KP8 |
| `this` nu | 48 | le parseur enveloppe `this` d'une référence — aucune garde (seul `super` en avait une) |
| segments racines (`jo`, `org`, `source`) | 60 | chemin FQN d'un TYPE DU PROJET + membre : la racine « inconnue » émise |
| `T` (paramètres de type) | 11 | `resoudreNomType` ne consultait pas les `TYPE_PARAMETER` englobants |
| `kt.valReassign` | 36 | la cible élue était la VALEUR DE DROITE (`cacheJeux[fqcn] = jeu` → FP sur `jeu`) ; définition garantie non modélisée |
| `kt.argumentCount` | 38 | les méthodes MEMBRES homonymes d'autres fichiers servaient de témoins ; constructeurs secondaires ignorés |
| `kt.unsafeNullable` | 23 | smart casts manquants (when à sujet, elvis terminal, disjonction, garde requireNotNull, boucle while) ; extensions à récepteur nullable signalées |

## 2. Les correctifs (a) — familles corrigées

Tous les diagnostics respectent « preuve ou silence » (ADR 0107) : chaque
garde ajoutée éteint une preuve qui n'en était pas une.

1. **Gardes partagées noms nus ↔ chemins FQN** (`DiagnosticsSemantiques`) :
   la nouvelle `nomNuSignalable` (this/super/`Companion` implicite,
   `it` par attente de lambda) s'applique AUSSI à la racine des chemins —
   le contournement par `it.name` était la source des 603+48 FP majeurs.
2. **Chemins amorce connus** (`cheminAmorceConnu`) : un PRÉFIXE du chemin
   qui est un paquet connu (projet/index) ou une classe résolue, ou l'amorce
   d'un import du document, rend la racine MUETTE ; la feuille reste
   faillible si le préfixe parent est un paquet du projet dont elle ne vit
   pas (miroir de la branche classpath existante).
3. **Catalogue KP8 élargi** (+25 types : `IntArray`…`BooleanArray`, les
   unsigned, `ArrayDeque`, `LazyThreadSafetyMode`, `Charsets`,
   `Comparator`, `Result`, `Runtime`, `AutoCloseable`…) + leur miroir typé
   `NOMS_FQCN_DEFAUT` — les imports IMPLICITES `kotlin.*`/`java.lang.*`.
4. **Paramètres de type** : `verifierNomType` consulte les
   `TYPE_PARAMETER_LIST` des déclarations englobantes (`T` nu en position
   de type résout).
5. **`X.Companion` en position de type** (récepteur d'extension
   `fun Char.Companion.…`) : muet — la présence du compagnon relève de la
   vue imbriquée.
6. **Zone de construction du super** : la clause `) : KtNoeud(noeud,
   source)` voit les paramètres NUS du primaire DE LA CLASSE (fiche 028 —
   l'ancienne visite passait la zone EXTERNE).
7. **Récepteur d'extension dans la pile** : `fun X.alea() { … }` empile le
   type de X (couche typée) — `alias()`/`nomQualifie()` nus résolvent ;
   récepteur non typable ⇒ couche OPAQUE (membres invisibles, jamais un
   faux positif — couvre `MIN_HIGH_SURROGATE` du compagnon de
   `Char.Companion`).
8. **Compagnons des maillons à la clôture** (`fermerType`) : la portée
   implicite d'une sous-classe voit les compagnons de ses supertypes
   (`errorIf`, `closeDeclarationWithCommentBinders`).
9. **Supertype délégué** (`: X by délégué`) : `supertypesEcrits` le lit
   désormais (DELEGATED_SUPER_TYPE_ENTRY) — `rawLookup`/`currentOffset` nu
   d'un bâtisseur délégué résolvent.
10. **`kt.valReassign`** : la cible est l'OPÉRANDE GAUCHE de `=`
    (`cible[clé] = valeur` et `p.x = v` ne sont pas des réaffectations de
    nom simple) ; un `val` SANS initialisateur (définition garantie) n'est
    jamais témoin.
11. **`kt.argumentCount`** : une fonction NICHEE (locale/membre) du nom
    prime les appelables globaux (silence) ; un constructeur SECONDAIRE du
    type éteint le témoin du primaire.
12. **Smart casts étendus** (`TypageBorne.raffinementAuPoint`, deux canaux
    — raffinements de condition annulables par `||` vs gardes STICKY) :
    `when` À SUJET (motif `is T` caste le sujet ; sujet `x?.y` ⇒ `x` non
    nul dans les corps), corps de `while (x != null && …)`, elvis TERMINAL
    (`val v = … ?: return/continue` ⇒ `v` non nul ; la base d'un
    `X?.f() ?: terminal` reste non nulle), `x == null || x.foo()`
    (droite de disjonction), `requireNotNull(x)`, affectation certaine
    précédente, lambda d'un accès sûr `X?.f { …X… }`.
13. **Extensions à récepteur nullable** (`orEmpty`, `let`, `run`, `also`,
    `apply`, `takeIf`, `takeUnless`, `isNullOrEmpty`, `isNullOrBlank`,
    `ifEmpty`, `ifBlank`) : jamais un accès non sûr prouvable.
14. **Références appelables** (`batisseur::ajouter`) : le MEMBRE se vérifie
    comme SÉLECTEUR du récepteur typé (l'ancienne marche le traitait en
    nom nu).
15. **Callee qualifiée** (`ModeleProjet.Batisseur()`) : le chemin résout
    l'imbriquée au typage.
16. **`apply` à récepteur non typable** : couche OPAQUE pour le corps
    (membres invisibles — couvre `deleteOnExit` d'un `File` JDK absent des
    jars de test).

### Complétion : la borne du catalogue à préfixe vide

L'élargissement du catalogue KP8 (25 types) repoussait les MOTS-CLÉS
(témoin du régime de repli, fiche 043) au-delà du plafond d'affichage 120 :
à préfixe VIDE, la source se BORNE désormais à 80 types (ordre
alphabétique déterministe — `LIMITE_CATALOGUE_VIDE`), à préfixe non vide
le catalogue reste intégral. Banc de qualité re-signé VERT.

## 3. Les budgets d'octets + l'observateur mémoire (b)

- **`api/MemoireKotlin.kt`** : l'enum NEUTRE `NiveauMemoireKotlin`
  (UI_MASQUEE, FAIBLE, CRITIQUE, URGENT) avec la correspondance conseillée
  aux rappels `onTrimMemory` Android — le moteur reste LIBRE de toute
  dépendance Android (l'hôte traduit).
- **`SessionLangageKotlin.reduireMemoire(niveau)`** : SPI de l'hôte. FAIBLE
  vidange les caches de lecture paresseuse de la vue JVM ; CRITIQUE vidange
  AUSSI l'annuaire des types et les gels de classement. Documents, époque
  et index persistants ne partent JAMAIS (justesse intacte).
- **`CacheBorneOctets`** (vue JVM) : les 7 caches (déclarés, jeux, jeux
  détaillés, membres de chaîne, imbriquées, compagnons, chaînes) déclarent
  chacun un PLAFOND D'OCTETS ESTIMÉS (512 KiB – 4 MiB, ~10 Mo cumulés) ;
  la vidange au plafond est TOTALE (tout se reconstruit). Estimation
  consciente : en-têtes d'objets + chars UTF-16. `occupationOctetsEstimee`
  expose l'observabilité.
- Tests : `MemoireSessionKotlinTest` (6 comportements — vidange au
  plafond, témoin null compté, vidange/reconstruction identique, session
  fermée no-op, échelle des niveaux, réécriture sans fuite).

## 4. La fraîcheur inter-fichiers STAT (c)

- **`ScanProjetParesseux.perimeAuStat()`** : la sonde compare le mtime de
  chaque fichier du CACHE au disque (miroir `empreinteFichier` Java ADR
  0025 : un stat coûte des microsecondes). Résidu documenté : même taille
  + même milliseconde échappe (granularité fs) ; les fichiers CRÉÉS hors
  éditeur relèvent de l'événement hôte ou de `rafraichirSources`.
- **La session sonde au CONSULT, throttlée** (`INTERVAL_SONDE_STAT_MS` =
  500 ms) : un disque qui a bougé déclenche la passe delta-only ASYNCHRONE
  — le consult courant sert l'état publié (honnête), le suivant voit
  l'état frais. Jamais de stat sur le chemin critique de chaque frappe.
- Tests : `FraicheurInterFichiersKotlinTest` (édition externe de A vue par
  les diagnostics de B ; sonde intacte/périmée).

## 5. Les preuves (chiffres imprimés, jamais inventés)

| Preuve | Avant | Après |
|---|---|---|
| P1 auto-corpus FP | 980 (28,05/1000 l.) | **0 (0,000/1000 l.)** — mode STRICT VERT |
| P1 auto-corpus FN | 0 | 0 |
| Suite `:kotlin:test` | 534 verts | **542 verts** (534 + 8 nouveaux : 6 mémoire + 2 fraîcheur) |
| Banc qualité complétion (baseline signée) | vert | **VERT** (p95 latence 8,17 ms, 0 saut de liste) |
| Banc grammaire | vert | vert |
| Digeste de parité | baseline d'origine | **re-signé** (voir §6) |

Le critère de sortie du plan (« P1 auto-corpus FP < 0,5/1 000 l. en mode
strict ») est DÉPASSÉ : FP = 0. La CI (10 jobs, dont dex d8 sous seuils)
reste l'arbitre de la tête poussée.

## 6. Écart de procédure E-K9 consigné (re-signature du digeste)

Le digeste de parité (fiche 010) signe la STRUCTURE du corpus — or le
corpus EST le module : les correctifs V-A modifient les sources mesurées.
Deux re-signatures (`-Dparite.majBase=true`) ont été exécutées au fil de
la vague, la preuve d'équivalence étant LE PRÉSENT JOURNAL : les
divergences du digeste correspondent EXACTEMENT aux fichiers modifiés par
les correctifs ci-dessus (aucune re-sync amont, aucun fichier ajouté ni
supprimé — 99 fichiers avant et après). L'incident E-K9 (re-signature sans
étape dédiée) a été évité : la re-signature EST l'étape dédiée, consignée
ici avec sa justification.

## 7. Ce qui reste hors V-A (honnêteté)

- La passe APPAREIL (gabarit 07) reste à exécuter par le propriétaire —
  les cibles D5 (complétion < 100 ms p95 perçu, diagnostics < 300 ms,
  démarrage chaud < 3 s) ne s'extrapolent pas de la JVM (p95 mesuré JVM :
  8,17 ms complétion).
- Les seuils de mémoire ART (GC, budgets par palier d'appareil) relèvent
  de V-F (ADR 0134 option 1 : V-A = budgets + observateur, V-F = GC ART +
  paliers).
- Le budget dex sera vérifié par le job CI `kotlin-art` (d8 ≤ 32 768
  méthodes / 6 Mo — les ajouts V-A sont de l'ordre du millier de méthodes
  au pire, aucun embarquement nouveau : le SPI mémoire est du code pur).
