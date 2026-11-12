# Journal d'exécution — Vague V-G : Gradle KTS & catalogue (P1, ADR 0130)

> Vague V-G du [plan](../04-plan-vagues.md) — exécutée le **12/11/2026**.
> Périmètre (ADR 0130 — Option 1 : le DISQUE est la source de vérité,
> JAMAIS la sync Gradle) : parseur TOML tolérant du catalogue
> `gradle/libs.versions.toml`, accesseurs `libs.…` en complétion (G4),
> navigation kts → entrée du catalogue (G5), diagnostic borné de clé
> inconnue (G6). Les clés AGP par déclarations disque (G3) restent à
> venir (bornologie §3).

## 1. Ce qui est livré

1. **Le parseur TOML tolérant** (`CatalogueGradle`) : les sections
   `[versions]`/`[libraries]`/`[bundles]`/`[plugins]` du catalogue lu du
   disque (`gradle/libs.versions.toml` du module, puis la racine du
   projet remontée — 8 niveaux) ; chaque entrée porte son ALIAS EXACT,
   sa LIGNE (navigation G5), son chemin NORMALISÉ (la convention des
   accesseurs Gradle : les tirets valent des points,
   `androidx-core-ktx` → `libs.androidx.core.ktx`, la section plugins
   porte son préfixe `libs.plugins.…`) et sa référence `version.ref`.
   Tolérant par construction : une ligne hostile est IGNORÉE, une
   section inconnue ABSORBE ses entrées (le parseur reprend à la
   section connue suivante), jamais une cascade.
2. **La complétion `libs.…`** (G4 — `AccesCatalogue.segmentsAuPoint`,
   branché en TÊTE du pipeline pour les `.kts`) : les SEGMENTS SUIVANTS
   du chemin normalisé par lecture TOML DIRECTE (PAS les accesseurs
   générés par Gradle) — `libs.|` propose `androidx`/`junit`/`plugins`,
   `libs.androidx.|` propose `core`/`appcompat` ; catalogue absent ou
   invalide → SILENCE (`libs.` non complété, G4 bornes).
3. **La navigation kts → TOML** (G5 — branchée AVANT la navigation
   standard dans `definition`) : la définition d'un accès `libs.…`
   complet saute à la LIGNE de l'entrée dans le catalogue ; une entrée
   de bibliothèque avec `version.ref` saute à l'entrée `[versions]`
   pointée (miroir exact de la fiche).
4. **Le diagnostic borné `kt.cleCatalogueInconnue`** (G6 — nouveau code
   du catalogue ADR 0125, branché dans `diagnostics` pour les `.kts`) :
   un accès `libs.…` COMPLET qui ne désigne AUCUNE entrée du catalogue
   PARSÉ (preuve d'absence par parse) ; l'accès EN FRAPPE (le caret
   dedans) reste muet ; catalogue absent → AUCUN diagnostic (le silence
   n'est jamais une preuve). JAMAIS de diagnostic de version ou de
   conflit (indémontrables sans sync, G7).
5. **Les accès imbriqués** : l'expression `libs.a.b.c` se parse en
   DOT_QUALIFIED imbriqués — seul le PORTANT EXTERNE est l'accès (les
   partiels internes ne sont ni complétés ni signalés).

## 2. Les preuves

| Preuve | Résultat |
|---|---|
| Tests moteur (`CatalogueGradleKotlinTest`) | **8 verts** — G4 (segments de premier niveau + imbrication par groupe + catalogue absent = silence), G5 (navigation vers la ligne `[versions]` référencée), G6 (clé inconnue signalée, résolues muettes, catalogue absent = aucun diagnostic), G7 (jamais de diagnostic de version), non-régression template (alias `libs.plugins.…` + accès résolus = zéro faux positif), TOML tolérant (entrées hostiles ignorées, section inconnue absorbée) |
| Suite `:kotlin:test` | 601 verts attendus (593 + 8), 3 ignorés (gates corpus) |
| Re-signature du digeste (E-K9) | par la COMMANDE OFFICIELLE fiche 010 — diff = `CatalogueGradle.kt` + `AccesCatalogue.kt` + `CatalogueGradleKotlinTest.kt` neufs + resynchronisés |
| Régression | aucune — fiche 027 (template Gradle sans faux positif) renforcée par le test dédié |

## 3. Bornologie (honnêteté D3)

- **G1/G2/G3 REPORTÉS** : plugins (`plugins { | }`), configurations du
  bloc `dependencies` et clés AGP par déclarations disque — le socle
  (lecture disque + catalogue) est livré, l'énumération des clés AGP
  relève d'un référentiel de déclarations (le repli honnête de G3
  exige la lecture des jars AGP du cache — dépendance hôte) ;
- la sync Gradle (variantes, types générés, contrôle de configuration)
  reste l'écart EXPLICITE G7/GK-018 — jamais comblé par imitation ;
- le diagnostic G6 ne porte QUE la clé inconnue (jamais la version,
  jamais le conflit) ;
- la navigation G5 rend la LIGNE (0-based) de l'entrée — la plage
  exacte du NOM dans la ligne relève d'un parseur TOML complet.
