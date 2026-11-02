# ADR 0108 — Classpath, bytecode et métadonnées Kotlin ; partage avec l'index CIDX

- **Statut** : Proposé
- **Date** : 2026-10-30

## Contexte

K5 du prompt : « Comment partager le travail avec l'index CIDX de
`:index` ? » L'ancien moteur maintient DEUX mécanismes : le lecteur brut
maison (664 l., différentiel 0 écart sur corpus réels [rapport A.3]) +
le décodeur `@Metadata` (kotlin-metadata-jvm 2.2.10, Apache-2.0) et un
index disque dédié `.kidx` (clés taggées `top:`/`ext:`, 1 087 l.) ;
tandis que `:index` de code-lsp a repris CIDX (segments, fusion LSM,
analyseur `.class`).

## Sources consultées

- `JetBrains/kotlin` @ `e3806c8f` : `libraries/kotlinx-metadata`
  (64 f / 9 074 l. [mesuré] — bibliothèque AUTONOME, Apache-2.0,
  publiée sur Central [vérifié]).
- `code-lsp` : `:index` (AnalyseurClassFile etc., 122 tests verts),
  `:model` (classpath v8).
- `codeide-lsp` : cahier fiches 002–005, 008–009, 022, 028, 035
  (décodeur, lecteur brut, index disque, segments persistants,
  typealias, objets classpath) ; ADR 0111/0211/0213/0219/0226.
- Étude : `01-architectures-analyse-kotlin.md` §K5.

## Options

**Option 1 — Étendre CIDX aux appelables Kotlin** (clés taggées +
décodage `@Metadata` dans `:index`) : un seul index, mais `:index`
devient bilingue (arbitrage propriétaire requis — Q-K1/Q-K2).
**Option 2 — Index dédié dans `:kotlin`** (statu quo de forme : le
format `.kidx` éprouvé migre) : autonomie du pack, duplication
d'infrastructure. **Option 3 — Vue mémoire seule sans index disque** :
rejette le contrat « tas plat » (re-décodage des jars à chaque
session) — écarté. **Option 4 — Ne rien changer.**

## Critères et mesures

- Différentiel du lecteur : 0 classe illisible, 0 écart de
  superclasse/interfaces/champs/méthodes sur android.jar (1 007) +
  stdlib (678/681) [mesuré — rapports A.3].
- Contrat disque du `.kidx` : persistance sans jar d'origine,
  auto-guérison des segments corrompus/périmés, garde d'empreintes
  no-op chaud (fiches 008/022).
- kotlinx-metadata-jvm : 492 classes / 1 141 méthodes / 1 référence
  ServiceLoader / 0 autre motif sensible ART [mesuré, scan].

## Décision proposée

**Option 2 par défaut, option 1 ouverte** : le module `:kotlin` porte
son index des appelables (format éprouvé — reprise voie C ou
réécriture cahier en main selon ADR 0100) CONSOMMANT l'analyseur
`.class` de `:index` via son API publique pour la structure Java ; toute
fusion des formats (option 1) est une demande d'interface (Q-K1)
soumise au propriétaire — elle ne sera instruite qu'avec des mesures de
coût (ré-écriture du format, double indexation transitoire). Le décodeur
`@Metadata` vit côté `:kotlin` (kotlinx-metadata en artefact ;
Q-K2 documente l'alternative).

## Conséquences

- Deux index disque coexistent pendant la migration (CIDX Java +
  appelables Kotlin) — comme aujourd'hui dans l'ancien dépôt ;
  l'entrepôt de segments partage les mêmes conventions d'empreintes W6.
- Le contrat ADR 0211/0213 (état d'indexation observable, garde de
  couverture) est repris tel quel (fiches 009/022).

## Plan de migration progressive

Étage classpath avec le noyau (miroir N1 Java) ; l'index suit le
contrat de session (défauts vides, tolérance de version du pack).

## Tests de non-régression et critères de sortie

- Fiches 002–005/008/009/022/028/035 vertes ; auto-guérison et garde
  de couverture éprouvées ; différentiel du lecteur re-joué au banc.

## Risques

- R1 — Double maintenance d'infrastructure d'index : mitigation =
  partage de l'analyseur `.class` (API publique) + demande Q-K1 ouverte.
- R2 — Montée de kotlinx-metadata avec la chaîne : suivie par l'ADR
  0121.
