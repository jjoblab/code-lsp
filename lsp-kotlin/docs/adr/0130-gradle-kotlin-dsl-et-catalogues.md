# ADR 0130 — Gradle Kotlin DSL et catalogue de versions : le disque comme source de vérité

- **Statut** : Proposé
- **Date** : 2026-10-31

## Contexte

L'audit (§1.9, KH9) confirme : aucune intelligence Gradle Kotlin DSL —
noms nus muets, qualifiés faillibles, accesseurs générés hors modèle
(ADR 0111). La référence (`02-reference-android-studio.md` §B12) sert
pourtant `build.gradle.kts` (plugins, dépendances, clés AGP) et
`gradle/libs.versions.toml` par l'index issu de la sync. Notre
différenciateur assumé : **lire le disque, sans sync Gradle** —
`build.gradle.kts`, `settings.gradle.kts`, `libs.versions.toml` parsés
par des parseurs TOLÉRANTS (erreur de syntaxe → repli silencieux,
jamais de cascade). La fiche KC-12 (G1–G7) fixe les comportements :
plugins/dépendances/clés par lecture disque, accesseurs `libs.…` par
lecture TOML directe (PAS via les accesseurs générés), navigation kts →
entrée du catalogue, diagnostics bornés. Écarts visés : GK-018 (Gradle
KTS), GK-019 (catalogue de versions). Cette ADR **réalise l'ADR 0111**
(la couche catalogue est cohérente avec sa doctrine ; le
provisionnement de classpath y restait la seule extension retenue).

## Sources consultées

- Référence : `02-reference-android-studio.md` §B12 (Gradle Kotlin DSL,
  ce que la sync ajoute et que nous n'aurons pas).
- Fiches : `05-fiches-comportement/KC-12` (G1–G7), KC-03 (configurations
  du bloc `dependencies`).
- Dépôt : ADR 0111 (amont), 0104 (mode SCRIPT du parseur vendoré),
  0109/0127 (modèle projet Android partagé), 0125, 0136 ; audit
  `01-audit-moteur.md` §3.9, §4.2 (mesures parse/reparse).

## Options

**Option 1 — Lecture disque seule (sans sync).** Plugins par ids lus sur
le disque (déjà déclarés, catalogue, `pluginManagement`) ; dépendances
par le modèle de projet partagé (ADR 0127) + coordonnées du catalogue ;
clés AGP par déclarations trouvées sur disque, repli honnête sinon ;
accesseurs `libs.…` par parseur TOML dédié (normalisation tirets/points,
imbrication par groupe, alias exacts) ; navigation vers la ligne du
TOML ; diagnostics bornés à la clé inconnue du catalogue (preuve
d'absence par parse) et à la version en doublon (décidable dans le
fichier). Mesuré : parse 1,8 ms p50 (500 l., JVM) et re-parse 3 000 l.
à 8,6 ms p50 [mesurés] — budgets transposables aux `.kts` ; TOML :
taille et latence de re-parse à mesurer (V0/gabarit 07, facteur
ART ≈ 4× à confirmer) ; zéro appel réseau (propriété testable).

**Option 2 — Attendre la sync hôte.** Contre-mesuré : la sync exige
d'exécuter Gradle sur JVM — hors de portée in process (l'ADR 0111
écartait déjà le modèle DSL complet, miroir T2) ; la latence de sync
n'est pas maîtrisable par nous (à mesurer côté hôte, mais le
différenciateur « sans sync » serait perdu, cf. §B12).

**Option 3 — DSL complet** (accesseurs générés par projet, contrôle de
configuration, variantes). Écarté par héritage : les accesseurs sont
générés par Gradle par projet — aucune classe lisible dans un jar
[constaté en 0111] ; le budget dex mesuré (26 590/32 768 méthodes,
4,10/6 Mo) n'accueille pas un modèle Gradle exécuté (leçon d'échelle
K2 : 64 507 méthodes).

## Critères et mesures

| Capacité | Preuve (disque) | Service | Mesure |
|---|---|---|---|
| `plugins { }` (G1) | ids déclarés + catalogue + `pluginManagement` | genre NOM, jamais inventé | à mesurer (V0) |
| `dependencies` (G2) | modèle projet partagé (ADR 0127) + catalogue + cache lisible | genre NOM borné | à mesurer (V0) |
| `android { }` (G3) | déclarations AGP trouvées sur disque | repli honnête (liste vide/partielle) | à mesurer (V0) |
| `libs.…` (G4) | parse TOML direct, alias exacts | silence si catalogue absent/invalide | re-parse TOML à mesurer (gabarit 07) |
| Navigation kts→toml (G5) | ligne de l'entrée ; `version.ref` → `[versions]` | extension protocole (ADR 0136) | latence à mesurer |
| Diagnostics (G6) | clé inconnue = preuve d'absence ; doublon `[versions]` | codes bornés au catalogue ADR 0125 | FP = 0 exigé (banc P1) |

Gardes héritées : nus toujours muets dans les scripts (régime
asymétrique 0111) ; aucun diagnostic de version/conflit (indémontrables
sans sync, G7) ; dossier `build/` exclu du scan des synthétiques (ADR
0109) vaut aussi pour les fichiers Gradle générés.

## Décision proposée

**Option 1.** Le disque est la source de vérité : parseurs tolérants
`.kts` (mode SCRIPT vendoré) et TOML dédié ; complétion/navigation/
diagnostics bornés comme au tableau ; plugins et dépendances passent par
la lecture du modèle de projet partagé (ADR 0127) ; les accesseurs
générés restent HORS périmètre (borne honnête héritée de 0111,
documentée GK-018) ; la navigation inter-fichiers kts→toml est déclarée
à la surface protocole (ADR 0136) ; les diagnostics bornés entrent au
catalogue ADR 0125 sous gate FP < 0,5/1 000 lignes.

## Conséquences

- Le moteur sert Gradle KTS sans infrastructure hôte supplémentaire :
  différenciateur mesurable (aucune sync, aucun réseau) face à la
  référence (§B12).
- Ce que la sync ajoute (variantes, types générés, contrôle de
  configuration) reste un écart EXPLICITE (G7, GK-018), jamais comblé
  par imitation.
- La lecture TOML directe contourne honnêtement la borne des accesseurs
  générés (GK-019) sans la nier.

## Plan de migration progressive

1. Parseur TOML tolérant + garde « catalogue absent → silence ».
2. Accesseurs `libs.…` en complétion (genre NOM) + navigation kts→toml
   (extension protocole, ADR 0136).
3. Diagnostics bornés (clé inconnue, doublon) sous gate ADR 0125 ;
   clés AGP par déclarations disque en dernier (repli honnête).

## Tests de non-régression et critères de sortie

- Fiche KC-12 (G1–G7) verte ; fiche 032 du cahier non régressée
  (scripts du template sans FP, typo qualifiée toujours signalée).
- Idempotence du re-parse TOML ; clé inconnue toujours signalée ; zéro
  diagnostic hors G6 ; zéro appel réseau (test dédié).
- Latences : re-parse `.kts`/TOML dans le budget diagnostics < 300 ms
  (D5), à mesurer au gabarit 07 sur appareil.

## Risques

- R1 — Évolution du format des catalogues : mitigation = parseur
  tolérant, repli silence, veille ADR 0121.
- R2 — Attente utilisateur des accesseurs générés : mitigation = borne
  documentée + demande d'interface (précédent ADR 0111).
- R3 — Collision avec le régime asymétrique : mitigation = nus maintenus
  muets, seuls les accès qualifiés `libs.*` deviennent servis (test de
  non-régression dédié).
