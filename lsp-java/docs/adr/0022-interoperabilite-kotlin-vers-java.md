# ADR 0022 — Interopérabilité Kotlin → Java

- **Statut** : Accepté (exécuté — vague V-B3, 31/10/2026 ; **amendé V-A2 le
  01/11/2026** : la light Kotlin CÈDE aux jars — cf. « Amendement V-A2 »)
- **Date** : 2026-10-31
- **Écart résolu** : G-009 (`01-audit-moteur.md` §1.2)

## Contexte

Les projets Android modernes sont majoritairement mixtes : les déclarations Kotlin
(extensions, data classes, fonctions top-level `@JvmStatic`, companion objects,
paramètres nommés…) sont **invisibles** du Java tant que le bytecode compilé n'est pas
sur le classpath — l'annuaire et l'index de mots filtrent `.java` uniquement
(`AnnuaireClasspath.kt:385`, `IndexMotsSources.kt:128`, preuve H2). Le moteur Kotlin
**ne migre pas** dans cette mission (instruction du 30/10/2026) ; seul l'**index
partagé** est dans le champ. La référence expose des déclarations cross-langage via
ses index de stubs ; côté ecj, un `.kt` compilé se lit par ses métadonnées de bytecode
(annotations `kotlin.Metadata`).

## Sources consultées

- `01-audit-moteur.md` §1.2 (preuves des filtres `.java`), §3.5 (usages).
- `lsp-java/docs/ECARTS-SERVEUR.md` §5 D3 (périmètre : habitat Java seulement, instruction du
  30/10/2026).
- ADR 0008 (index de stubs disque CIDX), ADR 0002 (périmètre moteur Java).
- Documentation publique du format `kotlin.Metadata` (spécification Kotlin) —
  comportement décrit pour lui-même.

## Options

**Option 1 — Index léger de déclarations publiques `.kt` (recommandé).** Un scanner
**lexical tolérant** des fichiers `.kt` (pas un front-end complet) extrait les
déclarations **publiques** : classes/interfaces/objets (FQCN), fonctions
top-level, propriétés/constantes, avec les indices de compatibilité Java
(`@JvmStatic`, `@JvmName`, `file facades `FileNameKt`). Ces déclarations entrent dans
(1) l'annuaire de noms (résolution et complétion de types, avec signature lisible),
(2) l'index de mots (usages/rename). Les **corps** ne sont jamais lus : hover = 
signature seule, honnête ; la navigation mène au `.kt` (texte).

**Option 2 — Lecture des métadonnées du bytecode compilé (post-build).** Complément
gratuit quand le build est passé (`.class` déjà sur le classpath) — mais **ne résout
pas** le cas avant-build, qui est le déficit visé.

**Option 3 — Front-end Kotlin embarqué.** Hors périmètre (instruction du 30/10) ;
coût ART prohibitif ; rejeté.

## Critères et mesures

- Rappel de résolution : sur un échantillon mixte du corpus (AnySoftKeyboard, Thunderbird
  éliminé pour Kotlin — remplacé par des paires Kotlin/Java des projets), taux de
  déclarations publiques Kotlin correctement exposées [cible ≥ 90 % des déclarations
  publiques des fichiers `.kt` scannés].
- Coût du scan : p95 par Ko de `.kt` [à mesurer ; cible < 1 ms/Ko JVM].
- Empreinte du segment CIDX [budget : < 1 Mo pour 3 500 fichiers `.kt`].

## Décision proposée (exécutée)

> **EXÉCUTÉE en V-B3** (PV : `04-plan-vagues.md` §V-B) : scanner lexical
> `DeclarationsKotlin` — types de premier niveau + facades `*Kt`
> (`@file:JvmName` primant), signatures CERTAINES seules (primitifs,
> `String`/collections standards, FQCN avec point — le reste ÉCARTÉ :
> jamais un type inventé) ; drapeau
> `jo.codeide.lsp.java.interoperabilite.kotlin.disabled`. Le rappel est
> vérifié contre un oracle compté à la main sur la fixture `mixte` ;
> la mesure sur l'échantillon du corpus (gate P3, manifestes requis) et
> le complément bytecode (point 3) restent à la gate corpus. Le segment
> CIDX partagé reste le CONTRAT documenté avec le futur moteur Kotlin.

**Option 1** (scanner lexical, déclarations publiques, index partagé CIDX — l'unique
point de contact avec le futur moteur Kotlin, qui pourra **écrire** le même segment
avec un front-end complet quand il existera) ; **option 2 en complément** quand le
bytecode est présent (priorité bytecode > scanner, miroir de la règle light/généré de
l'ADR 0021). Honnêteté : hover/complétion marqués « déclaration Kotlin (aperçu) »,
pas de faux corps.

## Conséquences

- `import a\|` et la complétion de types fonctionnent sur les projets mixtes **avant
  build** — ferme une partie du « il ne connaît pas bien le code ».
- Le scanner tolérant **sous-estime** (déclarations manquées sur syntaxes exotiques) :
  dégradation du rappel, jamais de faux positif (une déclaration scannée mais fausse
  serait un FP — les règles du scanner sont conservatrices : ne déclarer que ce qui
  est certain).

## Plan de migration progressive

1. Scanner classes/interfaces/objets + FQCN → annuaire (drapeau `interoperabilite.kotlin`).
2. Fonctions/propriétés top-level (facades `*Kt`, `@JvmStatic`/`@JvmName`) → index de
   mots + complétion.
3. Complément bytecode (priorité `.class` quand présent).

## Tests de non-régression et critères de sortie

- Cas dorés mixtes versionnés (classe Kotlin appelée du Java, avant/après build) ;
  P3 corpus inchangé (zéro FP ajouté) ; rappel mesuré = oracle (déclarations comptées
  à la main sur l'échantillon).

## Risques

- **R1 — Scanner trompé** (FQCN mal déduit, déclarations privées exposées) : règles
  conservatrices + cas dorés ; le bytecode prime.
- **R2 — Dérive avec le futur moteur Kotlin** : le segment CIDX est le contrat
  documenté ; il écrit, nous lisons.
- **R3 — Coût de scan sur gros projets Kotlin** : budgétaire (CIDX + empreinte
  par fichier).

## Amendement V-A2 (01/11/2026) — la light Kotlin cède aux jars

Mesuré sur le corpus réel (anysoftkeyboard, 27 358 lignes noyau dur) : dans un
projet mixte, javac compile le Java contre les classes Kotlin **compilées**
(`built_in_kotlinc` — l'ordre verrouillé V-B faisait PRIMER la light lexicale
sur les jars, y compris quand le montage fournit les vraies classes). La light
— API partielle, sans les membres `companion` — éclipsait alors la vraie
classe et fabriquait des faux positifs « méthode non définie » (18 FP mesurés
avant correctif, famille UndefinedMethod/UnresolvedVariable).

**Décision** : l'ordre de repli devient **sources → synthétiques PRIMANTS
(R, BuildConfig, Binding — ADR 0021, anti-doublon R3 inchangé) → JARS →
synthétiques CÉDANTS (déclarations Kotlin lexicales) → jrt → stubs**. Le
fournisseur `TypesSynthetiques` porte un `primeSurJars` (défaut `true`) ;
`DeclarationsKotlin` le pose à `false`. La light reste le REPLI pour un
projet Kotlin sans sortie compilée au classpath (le cas avant build de
V-B3 — comportement inchangé pour lui). Régression :
`DeclarationsKotlinTest` « la classe light kotlin cede aux jars du classpath ».
Détail chiffré : `mesures/2026-11-01-va2-corpus-reel.txt`, rapport
`08-rapport-divergences-ecj-javac.md` §5.

## Complément (02/11/2026) — `open class` sur le repli lexical (avant build)

L'amendement V-A2 règle l'APRÈS-build (la light cède aux jars) ; le REPLI
lexical lui-même était fautif sur la finalité : `DeclarationsKotlin`
synthétisait TOUTE classe finale, modificateurs captés mais jetés — une
classe Java héritant d'une `open class` Kotlin recevait « cannot subclass
the final class » (cas réel : `PackBroadcastReceiverBase`, addons/base du
corpus ; masqué par le montage V-A2 qui fournit `built_in_kotlinc`, exposé
par une passe de préparation sans classes compilées). La `Declaration`
porte désormais le mot-clé et les modificateurs : finale **par défaut**
(sémantique Kotlin), SAUF `open`/`abstract`/`sealed` (les membres d'un
`sealed` Java-side restent légaux tant que `PermittedSubclasses` n'est pas
émis) ; une `interface` n'est jamais marquée ; `object`/`enum class` et
facades restent finales. Deux régressions verrouillent les deux sens
(héritage Java depuis `open class` accepté / classe non ouverte refusée).

Limite inchangée, confirmée par la même passe : les MEMBRES d'`object`/
`class` Kotlin (cas réel : `ThemeAttributeKeyCodes.getKeyCodeForIconTheme-
Attribute`, `@JvmStatic`) restent hors du scanner lexical top-level —
l'extraction des corps indentés exigerait un mini-front-end ; jamais un
type inventé en attendant. Détail chiffré :
`mesures/2026-11-02-va2-complement-repli-lexical.txt`.
