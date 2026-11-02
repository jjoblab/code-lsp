# ADR 0107 — Typage et inférence : périmètre assumé et politique « preuve ou silence »

- **Statut** : Proposé
- **Date** : 2026-10-30

## Contexte

K4 du prompt : « Jusqu'où un modèle maison est-il fiable, et où commence
le domaine de K2 ? » L'ancien moteur démontre un sous-ensemble
conservateur à zéro faux positif (typage par initialiseur, retours
écrits, smart casts POSITIONNELS, inférence de lambda par attente
PROJET/SAM) ; le spike mesure le coût du domaine complet (K2 :
3,5 s d'amorçage [mesuré]).

## Sources consultées

- Étude `01-architectures-analyse-kotlin.md` §K4 (ligne de partage
  décidable/unification).
- Cahier : fiches 046 (K4 — smart casts, lambdas, `kt.typeMismatch`,
  `kt.unsafeNullable`, `kt.argumentCount`), 049 (preuve d'absence sur
  types projet), 036/037 (vagues 4/5).
- `codeide-lsp` : ADR 0203/0223 (preuves de présence/absence).

## Options

**Option 1 — Périmètre borné déclaratif + preuve ou silence.** Le
typage maison couvre ce qui se DÉCIDE (littéraux et leurs familles,
types écrits, constructeurs résolus, retours écrits, nullabilité à
récepteur typé avec gardes, arité sur candidates prouvées) ; tout le
reste sous-rapporte ; le mode profond K2 (différé, ADR 0103) est la
seule voie d'extension. **Option 2 — Croître l'inférence maison vers
« presque K2 »** : le pire des deux mondes (complexité croissante,
faux positifs potentiels, maintenance) — écarté.
**Option 3 — K2 par frappe** : disqualifié (ADR 0103).
**Option 4 — Ne rien changer.**

## Critères et mesures

- Fiches K4/K5 vertes = le périmètre actuel est le contrat minimal ;
  la bornologie de l'ancien moteur (génériques rabattus sur le nom de
  base, `||` annule le smart cast, ombrage par locale postérieure,
  arité sans types complets en routine) est conservée TELLE QUELLE en
  première vague (parité), améliorable ensuite par décision écrite.

## Décision proposée

**Option 1** : périmètre de typage **borné et versionné** (document du
module : familles couvertes, familles sous-rapportées, frontière
d'unification) ; la politique « preuve ou silence » est le
méta-critère : un diagnostic de typage n'est émis que si l'absence
d'information se DÉCIDE sur l'arbre + la résolution bornée. Le mode
profond (K2) reste une extension à la demande (délai annoncé).

## Conséquences

- Aucun « presque-inference » : la frontière est honnête et testable.
- Le banc de fidélité mesure le sous-rapport par famille (taux de
  couverture documenté, pas niflé).

## Plan de migration progressive

Parité d'abord (fiches K4/K5 telles quelles), extensions ensuite par
decision écrite (chaque extension prouvée au banc avant fusion).

## Tests de non-régression et critères de sortie

- Fiches typage du cahier vertes ; zéro faux `kt.typeMismatch` sur
  corpus ; couverture de sous-rapport mesurée et publiée.

## Risques

- R1 — Pression fonctionnelle (utilisateurs voulant plus de rouges) :
  mitigation = mode profond différé comme soupape dédiée (ADR 0112).

---

## Amendement 2026-10-31 — mission « niveau Android Studio » (ADR 0123, statut Proposé)

L'option 2 (« croître l'inférence maison ») est **ROUVERTE au périmètre borné de l'ADR 0123** : représentation à arguments de type + nullabilité + types fonctionnels + paramètres de type + projections de variance, résolution d'extensions par récepteur typé, surcharges par arité + types (l'ambiguïté est TOUJOURS silence), récepteurs implicites, smart casts étendus — avec **critère d'arrêt écrit** (builder inference, inférence de retour récursive et variance en écriture restent sous-rapportés) et **test différentiel contre l'oracle K2 déjà présent en CI**. Ce n'est pas le « presque-K2 » rejeté ici : la frontière est versionnée (`SPECIFICATION-TYPES.md`) et la conformité à la spécification du langage est vérifiée, pas déclarée. La bornologie actuelle (génériques rabattus, `||` annule le smart cast) devient la V0 du périmètre, « améliorable ensuite par décision écrite » comme le prévoyait la décision d'origine. Motif de réouverture : la mesure V0 attribue des familles entières de faux positifs à la représentation à 2 cas (`it` 603, `T` 11, argumentCount 38, unsafeNullable 23 — audit §4.3).
