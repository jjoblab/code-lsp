# ADR 0021 — Classes « light » Android (R, BuildConfig, ViewBinding, DataBinding, Safe Args)

- **Statut** : Accepté (exécuté — vagues V-B1/V-B3, 31/10/2026)
- **Date** : 2026-10-31
- **Écarts résolus** : G-001, G-007, G-027 (partie navigation), G-028 (partie rename) — `01-audit-moteur.md` §1.1

## Contexte

Aujourd'hui, le moteur ne produit **aucune** classe générée : `R`, `BuildConfig`,
`ViewBinding`/`DataBinding`, Safe Args n'existent qu'après un build AGP (H1,
`DeclassementDatabinding.kt:36` ne fait que déclasser l'erreur en HINT). La référence
résout exactement ce problème par des **classes light** : le document de référence du
plugin Android (`02-reference…` §B9) pose les règles — API calculable par des règles
simples et stables ; l'IDE n'a besoin que de l'API (pas des corps) ; l'activation est
connue (buildFeatures) ; l'anti-doublon exclut les sources générées du modèle quand la
classe light existe. Détails vérifiés : nommage ViewBinding (PascalCase + « Binding »,
package `<module>.databinding`), **nullabilité par configuration** (champ `@Nullable`
si l'ID manque dans une configuration, type `View` si incohérent,
`tools:viewBindingType`), méthodes `inflate(...)`/`bind(...)`, `BR` (variables +
`@Bindable`), R non-transitif AGP 8 (`nonTransitiveRClass` par défaut, champs non-final
si `nonFinalResIds`), BuildConfig désactivé par défaut en AGP 8.

## Sources consultées

- `02-reference-android-studio.md` §B9 (document `android-light-classes.md` résumé
  fidèlement, règles de nommage/nullabilité vérifiées dans le code public du plugin).
- `01-audit-moteur.md` §1.1 (aucune synthèse — preuve `rg` citée).
- ADR 0020 (référentiel + buildFeatures = prérequis), ADR 0013 (le rouge reste ecj).
- `bancs/projet-android-genere/` (l'existant simule la sortie AGP — deviendra le
  banc d'équivalence light vs généré).

## Options

**Option 1 — Fabrique de déclarations synthétiques injectées dans la résolution
(recommandée).** Un module moteur `classeslegere/` fabrique, **en mémoire**, des
déclarations API-seule (pas de corps) : `<ns>.R` + classes internes par type (champs
`public static final int` — non-final selon flag), `<ns>.BuildConfig` (si activé :
`DEBUG`, `APPLICATION_ID`, `VERSION_CODE/NAME`, `FLAVOR`, `BUILD_TYPE` + champs
`buildConfigField`), `<ns>.databinding.*Binding` (règles B9), `BR`, Safe Args (si
graphe de navigation). L'injection se fait au niveau de l'**annuaire/stubs** (rôle du
`PsiElementFinder` de la référence) : quand ecj résout `ActivityMainBinding`,
l'annuaire répond synthétique avant jars/jrt ; la complétion les suggère avec
auto-import ; le hover affiche la signature. **Règle d'anti-doublon** : si la source
générée réelle existe (build passé), elle **prime** et le synthétique est masqué.

**Option 2 — Attendre les sources générées (statu quo amélioré).** Rejetée : c'est le
comportement actuel — faux positifs avant build, symptôme apparu dans tous les retours
appareil.

**Option 3 — Générer les fichiers .java sur disque.** Rejetée : écriture dans le
projet de l'utilisateur, conflits avec AGP, mauvaise citoyenneté LSP.

## Critères et mesures

- **Équivalence** : sur `bancs/projet-android-genere/` + corpus réel (BasicSample =
  DataBinding, OpenTracks = viewBinding), diagnostics light vs sources générées
  réelles : **zéro écart** sur les positions couvertes (cas dorés versionnés).
- Corpus P3 : les 5 projets **sans build** → zéro « cannot be resolved » sur `R.*`,
  `*Binding`, `BuildConfig` (la mesure exacte sera le premier résultat de la vague 0
  exécutée en CI).
- Latence : fabrication à froid du R d'un module (200 ressources) [cible < 30 ms JVM] ;
  empreinte mémoire du référentiel light [budget < 3 Mo/module].

## Décision proposée (exécutée)

> **EXÉCUTÉE en V-B1 (R, BuildConfig) puis V-B3 (Binding, BR)** (PV :
> `04-plan-vagues.md` §V-B) : ordre de repli sources → SYNTHÉTIQUES →
> jars → jrt → stubs (anti-doublon R3 : la source générée réelle PRIME —
> testée build présent/absent) ; octets v52 (ConstantValue + attribut
> InnerClasses) ; équivalence light ↔ simulacre AGP du banc : 0 écart sur
> l'API couverte ; `DeclassementDatabinding` RETIRÉ (décision D6 — fiche
> 051 amendée « couvert par résolution »). **Renvoyé documenté** : Safe
> Args (graphe de navigation absent du banc), annotations de nullabilité
> (inlay V-C — les TYPES par configuration sont déjà fusionnés :
> incohérent ⇒ `android.view.View`).

**Option 1**, dans cet ordre : R (le plus gros gain) → BuildConfig → ViewBinding →
DataBinding (`BR` inclus) → Safe Args (si navigation présente).
`DeclassementDatabinding` est **retiré** dès que la résolution réelle du binding est
livrée (son rôle disparaît : plus d'erreur à déclasser) — suppression propre avec le
commit de bascule, la fiche 051 du cahier passe en « couvert par résolution »
(amendement cahier à demander, décision D6).

## Conséquences

- Le moteur sait « ce que le build saura » avant le build — axe de dépassement §0.1-2
  le plus visible pour l'utilisateur.
- Le referentiel doit rester la **seule** source de vérité des noms (une seule
  fabrique, consommée par résolution + complétion + navigation + rename).
- Coût : maintenance des règles AGP (nonTransitive/nonFinal, nommage binding) —
  dette documentée dans le KDoc de la fabrique.

## Plan de migration progressive

1. Fabrique R + injection annuaire (drapeau `projetandroid.classes-legere`) ; banc
   d'équivalence sur `projet-android-genere`.
2. BuildConfig + ViewBinding (nullabilité par configuration) ; P3 sur corpus réel.
3. DataBinding (`BR`, variables) ; retrait de `DeclassementDatabinding` + amendement
   cahier fiche 051.
4. Safe Args (graphe de navigation).

## Tests de non-régression et critères de sortie

- Équivalence light ↔ généré : 0 écart sur cas dorés (chaque champ, chaque méthode).
- P3 corpus : zéro faux positif avant build ; **et** zéro régression après build
  (anti-doublon).
- Navigation `R.id.x` → XML (avec ADR 0028) : cible exacte dans le fichier layout.

## Risques

- **R1 — Divergence règles de nommage AGP** : cas dorés extraits des 5 projets réels ;
  la source générée réelle prime toujours (filet de sécurité).
- **R2 — Mémoire sur ART** : API-seule (pas de corps), budget mesuré par vague.
- **R3 — Doublons light/générés** : règle de priorité explicite + test dédié
  (build présent/absent).
