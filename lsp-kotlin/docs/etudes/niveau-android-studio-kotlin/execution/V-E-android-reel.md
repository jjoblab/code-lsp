# Journal d'exécution — Vague V-E : Android réel (P1, ADR 0127/0128/0129)

> Vague V-E du [plan](../04-plan-vagues.md) — exécutée le **12/11/2026**.
> Dépendance levée : le plan conditionnait V-E à la livraison de `:model`
> v2 par la mission Java — l'exploration montre le socle Java **déjà
> livré** (`ContratAndroidMoteur`, `ModeleAndroidDerive`,
> `ReferentielRessources` dans `lsp-java/projetandroid/`), et le
> propriétaire a autorisé le franchissement des frontières de modules.
> Les trois étapes V-E1/V-E2/V-E3 sont livrées côté Kotlin.

## 1. Ce qui est livré

1. **V-E2 — le scan DÉDIÉ post-build des racines générées**
   (`ScanGenerees.kt`, ADR 0127 Option 1 : levée de l'exclusion H2,
   miroir Java 0021 R1 « généré réel > light ») : les racines
   `build/generated` (convention AGP — le `build/generated` du module et
   de ses parents, multi-module Gradle) se re-passen à
   `rafraichirSources` ; les classes KSP/KAPT (`MonDao_Impl` Room,
   `Hilt_*`, Safe Args) lues du disque — tolérant D3 (racine illisible,
   fichier non parsable = ignoré, partiel honnête), cache mtime, sans
   sync Gradle (le disque est la source de vérité). Les déclarations
   générées entrent au modèle de preuves AVANT les synthétiques ; la
   convention muette `estNomGenere` est **BORNÉE PAR PREUVE** : un nom
   généré dont la SOURCE est LUE résout (les deux sites de verdict —
   appelable et type) ; la coupure s'applique au FQCN (une classe R
   générée réelle EFFACE le groupe synthétique homonyme — testé).
2. **V-E3 — les plugins de compilateur DÉCLARATIFS** (ADR 0127/0128
   palier 1) : `annotationsFqcn` collecté sur `AppelableProjet` (les
   usages `@Nom` de la MODIFIER_LIST) ; `@Composable` et `suspend`
   portés par la SIGNATURE DU SURVOL des fonctions du document (le
   contexte du plugin prouvé par écrit — critère de sortie
   « @Composable/suspend prouvé ») ; **Parcelize** : le supertype
   `android.os.Parcelable` INJECTÉ dans la clause de la classe
   `@Parcelize` (modélisation déclarative — testé par la hiérarchie) ;
   `supertypeResolu` gère le nom écrit FQCN COMPLET. **Silence documenté**
   : serialization/allopen/noarg ne sont PAS simulés (on ne simule pas
   leur sémantique, on ne la contredit pas — ADR 0127).
3. **V-E1 — le namespace du build PRIME le paquet du manifest**
   (`ClassesSynthetiques.namespaceDuBuild`, miroir du champ namespace du
   dérivateur disque Java 0020) : le `build.gradle.kts` (ou
   `build.gradle`) FRÈRE du `src/main` porte `namespace = "…"` (AGP ≥ 8)
   ; sans namespace écrit, le paquet du manifest reprend (AGP < 8) —
   les deux branches testées.

## 2. Les preuves

| Preuve | Résultat |
|---|---|
| Tests moteur (`AndroidReelKotlinTest`) | **7 verts** — V-E2 : le nom généré lu du disque RÉSOUT (Room `_Impl` en usage), la priorité généré>light (le groupe synthétique R homonyme EFFACÉ, le membre de la générée servi), l'atout avant-build (sans build, le synthétique SURVIT) ; V-E3 : Parcelize injecté (et PAS d'injection sans l'annotation), @Composable + suspend dans le survol ; V-E1 : le namespace du build prime, le paquet du manifest reprend sans namespace |
| Suite `:kotlin:test` | **608 verts** (601 + 7), 3 ignorés (gates corpus), 0 régression |
| Re-signature du digeste (E-K9) | COMMANDE OFFICIELLE fiche 010 ; diff = `ScanGenerees.kt` + `AndroidReelKotlinTest.kt` neufs + 6 resynchronisés — aucune re-sync amont |
| Amélioration latérale | `supertypes` hiérarchie : un type PROJET résolu dont la déclaration vit HORS du document courant est désormais SERVI en item (uri vide — affichage honnête) au lieu d'être silencieusement sauté (bug émergent démasqué par le test Parcelize) |

## 3. Bornologie (honnêteté D3)

- **`nowinandroid` corpus FP < 0,5/1 000 l.** : REPORTÉ — le critère de
  sortie exige le corpus réel Hilt/Room KSP (dépôt externe volumineux) ;
  les preuves livrées sont les gabarits factices (`MonDao_Impl`,
  `Hilt_*`) ;
- l'interop arité/nullité 0129 : le versant ARITÉ vit des paramètres
  collectés (membres générés) ; le versant NULLITÉ (annotations
  `@Nullable` décodées du classpath) relève du décodeur de métadonnées
  (ADR 0128 palier 2, « étendre le décodeur aux annotations du
  classpath ») — palier 2 non livré ;
- l'équivalence light↔généré « 0 écart » : la coupure est testée
  (remplacement du groupe par la classe générée) ; le banc d'équivalence
  complet (toutes familles R) relève du corpus réel ;
- les plugins serialization/allopen/noarg : SILENCE documenté (ADR
  0127) — jamais simulés, jamais contredits.
