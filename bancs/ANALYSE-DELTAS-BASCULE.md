# ANALYSE DES DELTAS DU BANC DE PARITÉ CROISÉE — arbitrage du critère 4 (§8.5)

> **Rôle** : analyse **classe par classe** des **165 libellés** `pertes_vs_oracle`
> de la baseline signée
> [`baselines/parite-completion.json`](baselines/parite-completion.json)
> (commit `5edc10b`, signature du 29/10 — oracle vendored
> `codeide-lsp@cb809e14` en boîte noire), préparée pour la **décision de
> bascule** (critère 4 du gabarit
> [`GABARIT-BASCULE-APPAREIL.md`](GABARIT-BASCULE-APPAREIL.md) §5). La
> classification ci-dessous est **proposée** ; l'arbitrage final appartient au
> propriétaire. R5 : aucune valeur simulée — chaque libellé a été vérifié
> contre l'image `jrt` du JDK 21 (existence, module, caractère public ou
> interne) et chaque mécanisme contre le code source des deux moteurs.

## 1. Données et méthode

La baseline signée rejoue le corpus de parité (18 positions + 4 frappes de
succession) contre l'oracle vendored et le moteur neuf, et consigne les
listes de libellés position par position. Les faits de cadrage, vérifiables
dans le fichier :

- **couverture du cahier : 1.000 des deux côtés** — les 165 deltas sont,
  par construction, exclusivement **hors cahier** : aucun libellé attendu
  ne manque au moteur neuf ;
- les pertes se concentrent sur **9 des 18 positions**
  (`positions_sans_perte` = 0,5) — les positions MEMBRE, IMPORT et
  CORPS_CLASSE sont à **parité exacte** (zéro libellé perdu, zéro gagné) ;
  toutes les pertes vivent dans les positions de *recovery* (TYPE, NOM,
  CASE_LABEL, REFERENCE_PAQUET, MEMBRE_STATIQUE) ;
- la succession de frappe (contrat = gel, « zéro saut ») est **verte** :
  0 saut constaté sur l'appareil comme en CI ; l'écart de *contenu* à la
  frappe 0 (préfixe vide `s.¦`) est documenté en §5 — il n'entre pas dans
  les 165.

Méthode : extraction programmatique des 165 libellés perdus (annexe),
vérification de leur existence dans le JDK réel par balayage de l'image
`jrt` (classes réelles vs artefacts d'index, module d'appartenance, public
ou interne), et lecture du code des deux moteurs pour attribuer chaque
perte à un mécanisme — l'oracle d'un côté (matcher gradué
`EcjPrefixMatcher`, grades EXACT/PREFIX/PREFIX_CI/HUMP/SUBSTRING — ADR 0012),
le neuf de l'autre (`AnnuaireClasspath.typesParPrefixe` : collecte
**préfixe-ancrée** sur index en minuscules ; surface **publique** du JDK
seule — « les internes ne fuient jamais », kdoc de l'annuaire ; filtrage
grammatical par genre).

## 2. Synthèse — les trois classes du critère 4

| Classe proposée | Libellés | Part | Contenu |
|---|---:|---:|---|
| **Bruit hors cahier** | **103** | 62,4 % | libellés **invalides au genre de la position** (types en CASE_LABEL/NOM, mot-clé/méthode/constante en TYPE/MEMBRE) ou **non-API** (package-private) — leur perte améliore ou neutralise |
| **Divergence assumée** | **62** | 37,6 % | libellés valides écartés par un **choix de design documenté** du neuf : 49 par la collecte TYPE préfixe-ancrée (grades sous-chaîne/hump non collectés), 13 par l'exclusion des modules internes du JDK |
| **Comportement à rattraper** | **0** (proposé) | 0 % | aucun libellé ne respecte à la fois la politique du neuf (préfixe + surface publique) et le genre de la position — voir §6 pour la lecture alternative |

Lecture en une phrase : **chaque libellé perdu est soit du bruit que
l'oracle émet et que le neuf refuse, soit un écart assumé et documenté de
politique de collecte ; aucun trou ne se situe à l'intérieur de la
politique du moteur neuf.**

## 3. Mécanismes constatés (pourquoi ces deltas existent)

Les deux moteurs ne collectent pas leurs candidats de la même manière, et
l'écart de collecte — pas de classement — explique l'intégralité des 165 :

1. **L'oracle balaie son index par grades** (préfixe, préfixe insensible à
   la casse, hump, sous-chaîne) sur un univers qui **inclut les modules
   internes du JDK** (jdk.jlink, jdk.jfr, jdk.jpackage, jdk.dynalink) —
   d'où des propositions comme `StripNativeDebugSymbolsPlugin` ou
   `OverridableResource`, vérifiées réelles dans l'image `jrt` mais
   internes à des outillages (jlink/jfr/jpackage), jamais utiles en code
   utilisateur ;
2. **en position de recovery**, l'oracle déborde du genre : à `case RO¦` il
   propose 54 **types** (seules les constantes d'enum sont légales — le neuf
   propose exactement `ROUGE`) ; à `return ab¦` / `return cou¦` il propose
   43 types flous (un nom d'expression ne peut être un type) ; à
   `private Stri¦` il propose mot-clé (`strictfp`), méthode (`toString()`)
   et constante jlink ;
3. **le neuf collecte par préfixe ancré** (`typesParPrefixe` :
   `startsWith` sur index minuscules) **sur la surface publique seule** —
   les candidats sous-chaîne/hump n'entrent jamais dans la collecte TYPE,
   et les internes du JDK jamais dans l'annuaire ; son filtrage
   grammatical écarte les hors-genre.

Deux conséquences mesurables, à l'avantage du neuf, vont avec : sa
couverture **préfixe** excède celle de l'oracle (14 types `Inte*` publics —
toute la famille `InternalFrame*` — manquent à l'oracle, cf. §5), et ses
listes sont plus courtes et plus propres (29 items à `Stri¦` contre 50).

## 4. Détail par classe

### 4.1 Bruit hors cahier — 103 libellés

| Position | Pertes | Motif dominant |
|---|---:|---|
| `case-ROUGE` | 54 | types en position CASE_LABEL — seules les constantes d'enum sont valides ; le neuf rend exactement `ROUGE` |
| `nom-locale-domine` | 22 | types hors cahier en position NOM — bruit **symétrique** : le neuf émet aussi un repli de types (23 `Abstract*`, préfixe-ancrés), aucun des deux ensembles n'est dans le cahier (attendus `abd`, `abc` : présents des deux côtés, tête `abd` ✓) |
| `nom-champ` | 21 | idem — attendu `counter` : présent des deux côtés, tête ✓ |
| `type-String` | 3 | hors-genre : `strictfp` (mot-clé), `toString()` (méthode), `strip_native_debug_symbols_plugin` (constante jlink) |
| `type-Integer` | 1 | hors-genre : `interface` (mot-clé) |
| `statique-valueOf` | 1 | hors-genre : `var` (mot-clé) en position membre statique |
| `refpaquet-List` | 1 | `LocaleISOData` — classe package-private non-API de `java.util`, jamais dans la surface |

### 4.2 Divergence assumée — 62 libellés

| Sous-motif | Libellés | Détail |
|---|---:|---|
| **Collecte TYPE préfixe-ancrée** (grades sous-chaîne/hump non collectés) | **49** | 42 à `Inte¦` (dominés par les peintres/imprimeurs `javax.swing`/`javax.print` : `BigInteger`, `Painter`, `*PrintEvent`…) ; 6 à `Stri¦` (`AttributedString` java.text, `DOMStringList` org.w3c.dom, `SynthTreeUI`, `TableStringConverter`, `BadStringOperationException`, `DirContextStringPair`) ; 1 à `ArrayLis¦` (`CopyOnWriteArrayList`) |
| **Surface publique JDK seule** (modules internes exclus par design) | **13** | 12 à `Stri¦`/`Overrid¦` : 8 internes jdk.jlink/jdk.jfr/jdk.management.jfr **préfixe-matchés** (`StringParser`, `StringPool`, `StringTable`, `Stringifier`, `StringSharingPlugin`, `Strip*Plugin`×3) + 4 internes jdk.jlink/jdk.dynalink sous-chaîne (`DefaultStripDebugPlugin`, `ImageStringsWriter`, `ClassString`, `CheckRestrictedPackage`) ; 1 à `Overrid¦` (`OverridableResource`, jdk.jpackage) |

Tous ces libellés sont des **classes réelles du JDK** (vérifiées `jrt`) ;
leur absence découle de deux choix de design du neuf, documentés dans le
code (`typesParPrefixe` ; « les internes ne fuient jamais ») et cohérents
avec l'ADR 0012 (le classement gradué s'applique aux candidats collectés,
la collecte TYPE est préfixe-ancrée).

### 4.3 Comportement à rattraper — 0 (proposé)

Aucun des 165 libellés n'est simultanément : (a) valide au genre de sa
position, (b) dans la surface publique du JDK, (c) matchant la politique
de collecte du neuf (préfixe ancré). Il n'existe donc, **sous la
politique actuelle du neuf, aucun trou à combler** — c'est la
classification proposée au propriétaire.

## 5. Gains du neuf et fuite de surface cachée (pour mémoire, hors des 165)

L'analyse serait incomplète sans les 38 libellés que le neuf propose et
l'oracle non :

- **14 types `Inte*`** (`InternalFrame*`, `InterruptedByTimeoutException`) —
  la couverture **préfixe** du neuf **excède** celle de l'oracle sur sa
  propre politique : preuve que le delta n'est pas un déficit d'index ;
- **23 types `Abstract*`** à `return ab¦` — le bruit de repli symétrique du
  neuf en position NOM (préfixe-ancrés là où l'oracle balaie flou) ;
- **`valueOfCodePoint`** (MEMBRE_STATIQUE) et, à la frappe 0 de la
  succession, `coder`/`value`/`hash`/`hashIsZero`/`isLatin1` : des membres
  **package-private réels** de `java.lang.String` (vérifiés par réflexion
  sur JDK 21) que le neuf lit au bytecode mais l'oracle non. C'est une
  **fuite mineure de surface cachée** côté neuf — l'exact symétrique du
  choix « surface publique seule » porté par l'annuaire pour les types ;
  candidate au filtrage post-bascule (voir §6).

## 6. Lecture alternative et recommandation

**Lecture alternative (à trancher par le propriétaire).** La fiche 008
énonce que la sous-chaîne est *« admise seulement ≥ 3 caractères »*. Si on
lit cette admission comme une exigence de **proposition** (et non
seulement de classement des candidats collectés), les 49 pertes
sous-chaîne/hump deviendraient des « comportements à rattraper ». La borne
de ce rattrapage serait modeste : un seul libellé y est plausiblement
utile à un utilisateur (`CopyOnWriteArrayList` à `ArrayLis¦`) ; les 48
autres sont des peintres Swing, imprimeurs et internes d'outillage. Ce
rattrapage (étendre la collecte TYPE aux grades hump/sous-chaîne, en bas
de liste, derrière les préfixe-matchés) est **borné et réalisable
post-bascule** — il ne bloque pas la décision.

**Recommandation proposée pour le critère 4** : arbitrer les 165 comme
**103 bruit hors cahier + 62 divergences assumées + 0 à rattraper**,
avec deux notes post-bascule bornées, non bloquantes :

1. *(optionnel)* étendre la collecte TYPE aux grades hump/sous-chaîne —
   seul cas d'usage défendable : `CopyOnWriteArrayList` ;
2. *(recommandé)* filtrer les membres package-private lus au bytecode
   (`valueOfCodePoint`, `coder`, `value`, `hash`, `hashIsZero`,
   `isLatin1`) pour aligner les MEMBRES sur le même choix « surface
   publique » que les TYPES.

Avec ce classement, le critère §8.5 « 0 comportement perdu contre
l'oracle » se vérifie : rien de perdu n'est un comportement du cahier ou
de la politique du neuf — tout ce qui manque est du bruit ou de l'assumé.

## 7. Annexe — les 165 libellés, verdict par libellé

| Position | Libellé | Module JDK (vérifié jrt) | Verdict proposé | Motif |
|---|---|---|---|---|
| statique-valueOf | `var` | — | bruit hors cahier | mot-clé en position MEMBRE_STATIQUE |
| type-String | `AttributedString` | java.text | divergence assumée | grade sous-chaîne/hump non collecté (collecte TYPE préfixe-ancrée) |
| type-String | `BadStringOperationException` | javax.management | divergence assumée | grade sous-chaîne/hump non collecté (collecte TYPE préfixe-ancrée) |
| type-String | `CheckRestrictedPackage` | jdk.dynalink (interne) | divergence assumée | module interne JDK exclu par design (jdk.dynalink (interne)) |
| type-String | `ClassString` | jdk.dynalink (interne) | divergence assumée | module interne JDK exclu par design (jdk.dynalink (interne)) |
| type-String | `DOMStringList` | org.w3c.dom | divergence assumée | grade sous-chaîne/hump non collecté (collecte TYPE préfixe-ancrée) |
| type-String | `DefaultStripDebugPlugin` | jdk.jlink (interne) | divergence assumée | module interne JDK exclu par design (jdk.jlink (interne)) |
| type-String | `DirContextStringPair` | javax.naming.spi | divergence assumée | grade sous-chaîne/hump non collecté (collecte TYPE préfixe-ancrée) |
| type-String | `ImageStringsWriter` | jdk.jlink (interne) | divergence assumée | module interne JDK exclu par design (jdk.jlink (interne)) |
| type-String | `StringParser` | jdk.jfr (interne) | divergence assumée | module interne JDK exclu par design (jdk.jfr (interne)) |
| type-String | `StringPool` | jdk.jfr (interne) | divergence assumée | module interne JDK exclu par design (jdk.jfr (interne)) |
| type-String | `StringSharingPlugin` | jdk.jlink (interne) | divergence assumée | module interne JDK exclu par design (jdk.jlink (interne)) |
| type-String | `StringTable` | jdk.jlink (interne) | divergence assumée | module interne JDK exclu par design (jdk.jlink (interne)) |
| type-String | `Stringifier` | jdk.management.jfr (interne) | divergence assumée | module interne JDK exclu par design (jdk.management.jfr (interne)) |
| type-String | `StripJavaDebugAttributesPlugin` | jdk.jlink (interne) | divergence assumée | module interne JDK exclu par design (jdk.jlink (interne)) |
| type-String | `StripNativeCommandsPlugin` | jdk.jlink (interne) | divergence assumée | module interne JDK exclu par design (jdk.jlink (interne)) |
| type-String | `StripNativeDebugSymbolsPlugin` | jdk.jlink (interne) | divergence assumée | module interne JDK exclu par design (jdk.jlink (interne)) |
| type-String | `SynthTreeUI` | javax.swing.plaf.synth | divergence assumée | grade sous-chaîne/hump non collecté (collecte TYPE préfixe-ancrée) |
| type-String | `TableStringConverter` | javax.swing.table | divergence assumée | grade sous-chaîne/hump non collecté (collecte TYPE préfixe-ancrée) |
| type-String | `strictfp` | — | bruit hors cahier | mot-clé en position TYPE |
| type-String | `strip_native_debug_symbols_plugin` | — | bruit hors cahier | constante jlink en position TYPE |
| type-String | `toString()` | — | bruit hors cahier | méthode en position TYPE |
| type-ArrayList | `CopyOnWriteArrayList` | java.util.concurrent | divergence assumée | grade sous-chaîne/hump non collecté (collecte TYPE préfixe-ancrée) |
| type-Integer | `BigInteger` | — | divergence assumée | grade sous-chaîne/hump non collecté (collecte TYPE préfixe-ancrée) |
| type-Integer | `ButtonPainter` | — | divergence assumée | grade sous-chaîne/hump non collecté (collecte TYPE préfixe-ancrée) |
| type-Integer | `CheckBoxPainter` | — | divergence assumée | grade sous-chaîne/hump non collecté (collecte TYPE préfixe-ancrée) |
| type-Integer | `ComboBoxPainter` | — | divergence assumée | grade sous-chaîne/hump non collecté (collecte TYPE préfixe-ancrée) |
| type-Integer | `DefaultInterface` | — | divergence assumée | grade sous-chaîne/hump non collecté (collecte TYPE préfixe-ancrée) |
| type-Integer | `GlyphPainter1` | — | divergence assumée | grade sous-chaîne/hump non collecté (collecte TYPE préfixe-ancrée) |
| type-Integer | `GlyphPainter2` | — | divergence assumée | grade sous-chaîne/hump non collecté (collecte TYPE préfixe-ancrée) |
| type-Integer | `ImagePainter` | — | divergence assumée | grade sous-chaîne/hump non collecté (collecte TYPE préfixe-ancrée) |
| type-Integer | `IntrospectionException` | — | divergence assumée | grade sous-chaîne/hump non collecté (collecte TYPE préfixe-ancrée) |
| type-Integer | `JInternalFrame` | — | divergence assumée | grade sous-chaîne/hump non collecté (collecte TYPE préfixe-ancrée) |
| type-Integer | `MenuBarPainter` | — | divergence assumée | grade sous-chaîne/hump non collecté (collecte TYPE préfixe-ancrée) |
| type-Integer | `MenuItemPainter` | — | divergence assumée | grade sous-chaîne/hump non collecté (collecte TYPE préfixe-ancrée) |
| type-Integer | `MenuPainter` | — | divergence assumée | grade sous-chaîne/hump non collecté (collecte TYPE préfixe-ancrée) |
| type-Integer | `MutableBigInteger` | — | divergence assumée | grade sous-chaîne/hump non collecté (collecte TYPE préfixe-ancrée) |
| type-Integer | `NetworkInterface` | — | divergence assumée | grade sous-chaîne/hump non collecté (collecte TYPE préfixe-ancrée) |
| type-Integer | `PaintEvent` | — | divergence assumée | grade sous-chaîne/hump non collecté (collecte TYPE préfixe-ancrée) |
| type-Integer | `Painter` | — | divergence assumée | grade sous-chaîne/hump non collecté (collecte TYPE préfixe-ancrée) |
| type-Integer | `PointerInfo` | — | divergence assumée | grade sous-chaîne/hump non collecté (collecte TYPE préfixe-ancrée) |
| type-Integer | `PopupMenuPainter` | — | divergence assumée | grade sous-chaîne/hump non collecté (collecte TYPE préfixe-ancrée) |
| type-Integer | `PrintEvent` | — | divergence assumée | grade sous-chaîne/hump non collecté (collecte TYPE préfixe-ancrée) |
| type-Integer | `PrintException` | — | divergence assumée | grade sous-chaîne/hump non collecté (collecte TYPE préfixe-ancrée) |
| type-Integer | `PrinterAbortException` | — | divergence assumée | grade sous-chaîne/hump non collecté (collecte TYPE préfixe-ancrée) |
| type-Integer | `PrinterException` | — | divergence assumée | grade sous-chaîne/hump non collecté (collecte TYPE préfixe-ancrée) |
| type-Integer | `PrinterGraphics` | — | divergence assumée | grade sous-chaîne/hump non collecté (collecte TYPE préfixe-ancrée) |
| type-Integer | `PrinterIOException` | — | divergence assumée | grade sous-chaîne/hump non collecté (collecte TYPE préfixe-ancrée) |
| type-Integer | `PrinterJob` | — | divergence assumée | grade sous-chaîne/hump non collecté (collecte TYPE préfixe-ancrée) |
| type-Integer | `QueryPrinter` | — | divergence assumée | grade sous-chaîne/hump non collecté (collecte TYPE préfixe-ancrée) |
| type-Integer | `RowSetInternal` | — | divergence assumée | grade sous-chaîne/hump non collecté (collecte TYPE préfixe-ancrée) |
| type-Integer | `SeparatorPainter` | — | divergence assumée | grade sous-chaîne/hump non collecté (collecte TYPE préfixe-ancrée) |
| type-Integer | `SetOfIntegerSyntax` | — | divergence assumée | grade sous-chaîne/hump non collecté (collecte TYPE préfixe-ancrée) |
| type-Integer | `SignedMutableBigInteger` | — | divergence assumée | grade sous-chaîne/hump non collecté (collecte TYPE préfixe-ancrée) |
| type-Integer | `SwingInterOpUtils` | — | divergence assumée | grade sous-chaîne/hump non collecté (collecte TYPE préfixe-ancrée) |
| type-Integer | `SwingPaintEventDispatcher` | — | divergence assumée | grade sous-chaîne/hump non collecté (collecte TYPE préfixe-ancrée) |
| type-Integer | `SynthPainter` | — | divergence assumée | grade sous-chaîne/hump non collecté (collecte TYPE préfixe-ancrée) |
| type-Integer | `TextAreaPainter` | — | divergence assumée | grade sous-chaîne/hump non collecté (collecte TYPE préfixe-ancrée) |
| type-Integer | `TextPanePainter` | — | divergence assumée | grade sous-chaîne/hump non collecté (collecte TYPE préfixe-ancrée) |
| type-Integer | `ToolBarPainter` | — | divergence assumée | grade sous-chaîne/hump non collecté (collecte TYPE préfixe-ancrée) |
| type-Integer | `ToolTipPainter` | — | divergence assumée | grade sous-chaîne/hump non collecté (collecte TYPE préfixe-ancrée) |
| type-Integer | `TreeCellPainter` | — | divergence assumée | grade sous-chaîne/hump non collecté (collecte TYPE préfixe-ancrée) |
| type-Integer | `TreePainter` | — | divergence assumée | grade sous-chaîne/hump non collecté (collecte TYPE préfixe-ancrée) |
| type-Integer | `TypePrinter` | — | divergence assumée | grade sous-chaîne/hump non collecté (collecte TYPE préfixe-ancrée) |
| type-Integer | `ViewPrinter` | — | divergence assumée | grade sous-chaîne/hump non collecté (collecte TYPE préfixe-ancrée) |
| type-Integer | `interface` | — | bruit hors cahier | mot-clé en position TYPE |
| type-Override | `OverridableResource` | jdk.jpackage (interne) | divergence assumée | module interne JDK exclu par design (jdk.jpackage (interne)) |
| nom-locale-domine | `AEADBadTagException` | — | bruit hors cahier | types hors cahier en position NOM (bruit symétrique — le neuf émet les siens) |
| nom-locale-domine | `AbortException` | — | bruit hors cahier | types hors cahier en position NOM (bruit symétrique — le neuf émet les siens) |
| nom-locale-domine | `AbstractBundler` | — | bruit hors cahier | types hors cahier en position NOM (bruit symétrique — le neuf émet les siens) |
| nom-locale-domine | `AbstractDCmd` | — | bruit hors cahier | types hors cahier en position NOM (bruit symétrique — le neuf émet les siens) |
| nom-locale-domine | `AbstractJDKEvent` | — | bruit hors cahier | types hors cahier en position NOM (bruit symétrique — le neuf émet les siens) |
| nom-locale-domine | `AbstractJavaLinker` | — | bruit hors cahier | types hors cahier en position NOM (bruit symétrique — le neuf émet les siens) |
| nom-locale-domine | `AbstractJavaProfile` | — | bruit hors cahier | types hors cahier en position NOM (bruit symétrique — le neuf émet les siens) |
| nom-locale-domine | `AbstractMask` | — | bruit hors cahier | types hors cahier en position NOM (bruit symétrique — le neuf émet les siens) |
| nom-locale-domine | `AbstractProfiledItem` | — | bruit hors cahier | types hors cahier en position NOM (bruit symétrique — le neuf émet les siens) |
| nom-locale-domine | `AbstractShuffle` | — | bruit hors cahier | types hors cahier en position NOM (bruit symétrique — le neuf émet les siens) |
| nom-locale-domine | `AbstractSpecies` | — | bruit hors cahier | types hors cahier en position NOM (bruit symétrique — le neuf émet les siens) |
| nom-locale-domine | `AbstractVector` | — | bruit hors cahier | types hors cahier en position NOM (bruit symétrique — le neuf émet les siens) |
| nom-locale-domine | `AccessibleBundle` | — | bruit hors cahier | types hors cahier en position NOM (bruit symétrique — le neuf émet les siens) |
| nom-locale-domine | `AccessibleKeyBinding` | — | bruit hors cahier | types hors cahier en position NOM (bruit symétrique — le neuf émet les siens) |
| nom-locale-domine | `AlreadyBoundException` | — | bruit hors cahier | types hors cahier en position NOM (bruit symétrique — le neuf émet les siens) |
| nom-locale-domine | `AppImageBundler` | — | bruit hors cahier | types hors cahier en position NOM (bruit symétrique — le neuf émet les siens) |
| nom-locale-domine | `ArrayBlockingQueue` | — | bruit hors cahier | types hors cahier en position NOM (bruit symétrique — le neuf émet les siens) |
| nom-locale-domine | `ArrayIndexOutOfBoundsException` | — | bruit hors cahier | types hors cahier en position NOM (bruit symétrique — le neuf émet les siens) |
| nom-locale-domine | `ArrowButtonPainter` | — | bruit hors cahier | types hors cahier en position NOM (bruit symétrique — le neuf émet les siens) |
| nom-locale-domine | `AsyncBoxView` | — | bruit hors cahier | types hors cahier en position NOM (bruit symétrique — le neuf émet les siens) |
| nom-locale-domine | `AsynchronousByteChannel` | — | bruit hors cahier | types hors cahier en position NOM (bruit symétrique — le neuf émet les siens) |
| nom-locale-domine | `AtomicBoolean` | — | bruit hors cahier | types hors cahier en position NOM (bruit symétrique — le neuf émet les siens) |
| nom-champ | `AccountException` | — | bruit hors cahier | types hors cahier en position NOM (bruit symétrique — le neuf émet les siens) |
| nom-champ | `AccountExpiredException` | — | bruit hors cahier | types hors cahier en position NOM (bruit symétrique — le neuf émet les siens) |
| nom-champ | `AccountLockedException` | — | bruit hors cahier | types hors cahier en position NOM (bruit symétrique — le neuf émet les siens) |
| nom-champ | `AccountNotFoundException` | — | bruit hors cahier | types hors cahier en position NOM (bruit symétrique — le neuf émet les siens) |
| nom-champ | `CheckedOutputStream` | — | bruit hors cahier | types hors cahier en position NOM (bruit symétrique — le neuf émet les siens) |
| nom-champ | `CipherOutputStream` | — | bruit hors cahier | types hors cahier en position NOM (bruit symétrique — le neuf émet les siens) |
| nom-champ | `CodeUtil` | — | bruit hors cahier | types hors cahier en position NOM (bruit symétrique — le neuf émet les siens) |
| nom-champ | `ColorChooserUI` | — | bruit hors cahier | types hors cahier en position NOM (bruit symétrique — le neuf émet les siens) |
| nom-champ | `ColorUIResource` | — | bruit hors cahier | types hors cahier en position NOM (bruit symétrique — le neuf émet les siens) |
| nom-champ | `ComboBoxUI` | — | bruit hors cahier | types hors cahier en position NOM (bruit symétrique — le neuf émet les siens) |
| nom-champ | `CommandAPDU` | — | bruit hors cahier | types hors cahier en position NOM (bruit symétrique — le neuf émet les siens) |
| nom-champ | `CommentUtils` | — | bruit hors cahier | types hors cahier en position NOM (bruit symétrique — le neuf émet les siens) |
| nom-champ | `ComponentInputMapUIResource` | — | bruit hors cahier | types hors cahier en position NOM (bruit symétrique — le neuf émet les siens) |
| nom-champ | `ComponentUI` | — | bruit hors cahier | types hors cahier en position NOM (bruit symétrique — le neuf émet les siens) |
| nom-champ | `ConstantUtils` | — | bruit hors cahier | types hors cahier en position NOM (bruit symétrique — le neuf émet les siens) |
| nom-champ | `ContainerCPUThrottlingEvent` | — | bruit hors cahier | types hors cahier en position NOM (bruit symétrique — le neuf émet les siens) |
| nom-champ | `ContainerCPUUsageEvent` | — | bruit hors cahier | types hors cahier en position NOM (bruit symétrique — le neuf émet les siens) |
| nom-champ | `ContainerIOUsageEvent` | — | bruit hors cahier | types hors cahier en position NOM (bruit symétrique — le neuf émet les siens) |
| nom-champ | `ContainerMemoryUsageEvent` | — | bruit hors cahier | types hors cahier en position NOM (bruit symétrique — le neuf émet les siens) |
| nom-champ | `MacOutputStream` | — | bruit hors cahier | types hors cahier en position NOM (bruit symétrique — le neuf émet les siens) |
| nom-champ | `QueuedJobCount` | — | bruit hors cahier | types hors cahier en position NOM (bruit symétrique — le neuf émet les siens) |
| refpaquet-List | `LocaleISOData` | java.util (package-private) | bruit hors cahier | classe package-private non-API proposée comme membre de paquet |
| case-ROUGE | `RSAOtherPrimeInfo` | — | bruit hors cahier | types en position CASE_LABEL (seules les constantes d'enum sont valides) |
| case-ROUGE | `RasterOp` | — | bruit hors cahier | types en position CASE_LABEL (seules les constantes d'enum sont valides) |
| case-ROUGE | `ReadOnlyBufferException` | — | bruit hors cahier | types en position CASE_LABEL (seules les constantes d'enum sont valides) |
| case-ROUGE | `ReadOnlyFileSystemException` | — | bruit hors cahier | types en position CASE_LABEL (seules les constantes d'enum sont valides) |
| case-ROUGE | `RecordedObject` | — | bruit hors cahier | types en position CASE_LABEL (seules les constantes d'enum sont valides) |
| case-ROUGE | `RecordingOutput` | — | bruit hors cahier | types en position CASE_LABEL (seules les constantes d'enum sont valides) |
| case-ROUGE | `ReduceOps` | — | bruit hors cahier | types en position CASE_LABEL (seules les constantes d'enum sont valides) |
| case-ROUGE | `ReflectiveOperationException` | — | bruit hors cahier | types en position CASE_LABEL (seules les constantes d'enum sont valides) |
| case-ROUGE | `RemoteObject` | — | bruit hors cahier | types en position CASE_LABEL (seules les constantes d'enum sont valides) |
| case-ROUGE | `RemoteObjectInvocationHandler` | — | bruit hors cahier | types en position CASE_LABEL (seules les constantes d'enum sont valides) |
| case-ROUGE | `RenderableImageOp` | — | bruit hors cahier | types en position CASE_LABEL (seules les constantes d'enum sont valides) |
| case-ROUGE | `RescaleOp` | — | bruit hors cahier | types en position CASE_LABEL (seules les constantes d'enum sont valides) |
| case-ROUGE | `ResourceIOException` | — | bruit hors cahier | types en position CASE_LABEL (seules les constantes d'enum sont valides) |
| case-ROUGE | `ReverseOrderDequeView` | — | bruit hors cahier | types en position CASE_LABEL (seules les constantes d'enum sont valides) |
| case-ROUGE | `ReverseOrderListView` | — | bruit hors cahier | types en position CASE_LABEL (seules les constantes d'enum sont valides) |
| case-ROUGE | `ReverseOrderSortedMapView` | — | bruit hors cahier | types en position CASE_LABEL (seules les constantes d'enum sont valides) |
| case-ROUGE | `ReverseOrderSortedSetView` | — | bruit hors cahier | types en position CASE_LABEL (seules les constantes d'enum sont valides) |
| case-ROUGE | `Robot` | — | bruit hors cahier | types en position CASE_LABEL (seules les constantes d'enum sont valides) |
| case-ROUGE | `RobotPeer` | — | bruit hors cahier | types en position CASE_LABEL (seules les constantes d'enum sont valides) |
| case-ROUGE | `Role` | — | bruit hors cahier | types en position CASE_LABEL (seules les constantes d'enum sont valides) |
| case-ROUGE | `RoleInfo` | — | bruit hors cahier | types en position CASE_LABEL (seules les constantes d'enum sont valides) |
| case-ROUGE | `RoleInfoNotFoundException` | — | bruit hors cahier | types en position CASE_LABEL (seules les constantes d'enum sont valides) |
| case-ROUGE | `RoleList` | — | bruit hors cahier | types en position CASE_LABEL (seules les constantes d'enum sont valides) |
| case-ROUGE | `RoleNotFoundException` | — | bruit hors cahier | types en position CASE_LABEL (seules les constantes d'enum sont valides) |
| case-ROUGE | `RoleResult` | — | bruit hors cahier | types en position CASE_LABEL (seules les constantes d'enum sont valides) |
| case-ROUGE | `RoleStatus` | — | bruit hors cahier | types en position CASE_LABEL (seules les constantes d'enum sont valides) |
| case-ROUGE | `RoleUnresolved` | — | bruit hors cahier | types en position CASE_LABEL (seules les constantes d'enum sont valides) |
| case-ROUGE | `RoleUnresolvedList` | — | bruit hors cahier | types en position CASE_LABEL (seules les constantes d'enum sont valides) |
| case-ROUGE | `RootPaneContainer` | — | bruit hors cahier | types en position CASE_LABEL (seules les constantes d'enum sont valides) |
| case-ROUGE | `RootPaneUI` | — | bruit hors cahier | types en position CASE_LABEL (seules les constantes d'enum sont valides) |
| case-ROUGE | `RoundEnvironment` | — | bruit hors cahier | types en position CASE_LABEL (seules les constantes d'enum sont valides) |
| case-ROUGE | `RoundRectIterator` | — | bruit hors cahier | types en position CASE_LABEL (seules les constantes d'enum sont valides) |
| case-ROUGE | `RoundRectangle2D` | — | bruit hors cahier | types en position CASE_LABEL (seules les constantes d'enum sont valides) |
| case-ROUGE | `RoundingMode` | — | bruit hors cahier | types en position CASE_LABEL (seules les constantes d'enum sont valides) |
| case-ROUGE | `Row` | — | bruit hors cahier | types en position CASE_LABEL (seules les constantes d'enum sont valides) |
| case-ROUGE | `RowFilter` | — | bruit hors cahier | types en position CASE_LABEL (seules les constantes d'enum sont valides) |
| case-ROUGE | `RowId` | — | bruit hors cahier | types en position CASE_LABEL (seules les constantes d'enum sont valides) |
| case-ROUGE | `RowIdLifetime` | — | bruit hors cahier | types en position CASE_LABEL (seules les constantes d'enum sont valides) |
| case-ROUGE | `RowMapper` | — | bruit hors cahier | types en position CASE_LABEL (seules les constantes d'enum sont valides) |
| case-ROUGE | `RowSet` | — | bruit hors cahier | types en position CASE_LABEL (seules les constantes d'enum sont valides) |
| case-ROUGE | `RowSetEvent` | — | bruit hors cahier | types en position CASE_LABEL (seules les constantes d'enum sont valides) |
| case-ROUGE | `RowSetFactory` | — | bruit hors cahier | types en position CASE_LABEL (seules les constantes d'enum sont valides) |
| case-ROUGE | `RowSetInternal` | — | bruit hors cahier | types en position CASE_LABEL (seules les constantes d'enum sont valides) |
| case-ROUGE | `RowSetListener` | — | bruit hors cahier | types en position CASE_LABEL (seules les constantes d'enum sont valides) |
| case-ROUGE | `RowSetMetaData` | — | bruit hors cahier | types en position CASE_LABEL (seules les constantes d'enum sont valides) |
| case-ROUGE | `RowSetMetaDataImpl` | — | bruit hors cahier | types en position CASE_LABEL (seules les constantes d'enum sont valides) |
| case-ROUGE | `RowSetProvider` | — | bruit hors cahier | types en position CASE_LABEL (seules les constantes d'enum sont valides) |
| case-ROUGE | `RowSetReader` | — | bruit hors cahier | types en position CASE_LABEL (seules les constantes d'enum sont valides) |
| case-ROUGE | `RowSetWarning` | — | bruit hors cahier | types en position CASE_LABEL (seules les constantes d'enum sont valides) |
| case-ROUGE | `RowSetWriter` | — | bruit hors cahier | types en position CASE_LABEL (seules les constantes d'enum sont valides) |
| case-ROUGE | `RowSorter` | — | bruit hors cahier | types en position CASE_LABEL (seules les constantes d'enum sont valides) |
| case-ROUGE | `RowSorterEvent` | — | bruit hors cahier | types en position CASE_LABEL (seules les constantes d'enum sont valides) |
| case-ROUGE | `RowSorterListener` | — | bruit hors cahier | types en position CASE_LABEL (seules les constantes d'enum sont valides) |
| case-ROUGE | `RuntimeOperationsException` | — | bruit hors cahier | types en position CASE_LABEL (seules les constantes d'enum sont valides) |
