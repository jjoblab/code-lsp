# CHANGELOG — moteur Java LSP (`lspjava`)

> Reconstitué depuis les en-têtes de version du `build.gradle.kts` racine
> (l. 17–408), les messages de commits et le registre de publication
> (GitHub Packages, tailles mesurées le 03/11/2026). Le contenu complet de
> chaque version (validation, pièges, référentiels) vit dans l'en-tête de
> version cité. Format : **Ajouté / Modifié / Corrigé**.

## 1.14.0 — alignement de version avec le pack langue Kotlin (12/11/2026, PUBLIÉE v1.14.0)

> Entrée de rattrapage (13/11/2026) : la 1.14.0 a republié le moteur Java à
> l'identique — aucun diff Java entre v1.13.0 et v1.14.0 (vérifié au dépôt),
> l'incrément portait le pack langue Kotlin (`jo-codeide-lsp-kotlin` 1.14.0 :
> plan « niveau Android Studio » complet, vagues V-A → V-H). La 1.13.0
> ci-dessous, marquée « NON PUBLIÉE, tête » au moment de son écriture, A ÉTÉ
> PUBLIÉE par la suite (registre GitHub Packages : `jo-codeide-lsp-java`
> 1.13.0 présent, run Publication n° 13).

## 1.13.0 — V-H : répartiteur au protocole (02/11/2026, PUBLIÉE v1.13.0)

- **Ajouté** : `OrdonnanceurMoteur.soumettrePrioritaire` — 3 voies
  FRAPPE/NAVIGATION/CONFORT câblées sur 29 méthodes de document ; supersession
  **à valeur** (réponse vide honnête, jamais une annulation), frappe-caducité,
  exclusion par fichier ; capture du document À LA SOUMISSION + vérification
  d'époque à la livraison ; `exit()` solde les requêtes en attente.
- **Corrigé** : tempête de requêtes appareil (29 s / 87 s mesurés en file)
  — le chien de garde chronomètre désormais sur le worker du répartiteur.
- Validation : 959 tests / 0 échec (ADR 0010 à jour, PV
  `mesures/2026-11-02-vh-repartition.txt`).

## 1.12.2 — correctif interop Kotlin (01/11/2026, PUBLIÉE v1.12.2)

- **Corrigé** : une `open class` Kotlin était synthétisée FINALE —
  `DeclarationsKotlin` capte open/abstract/sealed/data/value (finale par
  défaut, interface jamais marquée) ; limite documentée (ADR 0022
  complément) : les membres d'object/class restent hors du scanner lexical.

## 1.12.1 — robustesse des retours appareil (01/11/2026, PUBLIÉE v1.12.1)

- **Ajouté** : pré-chauffe du classpath hors verrou (jouée par le
  réchauffeur du double-tampon et à la première pose asynchrone) ; budget
  d'horloge de complétion 3 s (`…completion.budgetms`) ; chien de garde des
  requêtes lentes (2 s, `…lenteur.seuilms`, journalisé côté hôte).
- **Corrigé** : serveur figé en pleine frappe (coûts froids de l'annuaire
  android.jar ~40 000 entrées et du LookupEnvironment payés sous le verrou).

## 1.12.0 — complément de catalogue (01/11/2026, PUBLIÉE v1.12.0)

- **Ajouté** : ajout et réordonnancement de paramètres au site d'appel
  fautif (quickfix ID-first, IDs mesurés) ; rename depuis un document XML
  (propagation code + XML, tampon ouvert en fait de foi) ;
  `codeDescription.href` (provenance lint → page officielle vérifiée).
- Validation : 900 tests ; latence liste p95 mesurée 410 µs.

## 1.11.0 — vagues V-F finale + V-G (31/10/2026, NON PUBLIÉE)

- **Ajouté** : budgets d'octets des caches (garde anti-cyclage) + shim
  `onTrimMemory` (rapport chiffré, kill-switches) ; plage prioritaire
  (`$/codeide.plageVisible` v1, double publication « visible d'abord ») ;
  workDoneProgress d'indexation ; diagnostics enrichis (data
  `{provenance, code}` + relatedInformation) ; `willRenameFiles` ;
  semanticTokens sur bindings (légende 8 types + 4 modificateurs) ;
  `completionItem/resolve` réel (auto-import différé négocié) ; registre
  client K2.
- Validation : 919 tests ; semanticTokens p95 1 ms.

## 1.10.0 — V-D tranche 3 (01/11/2026, NON PUBLIÉE)

- **Ajouté** : CHANGE SIGNATURE (ré-écriture du graphe des appelants) ;
  suppression hiérarchique (chaîne d'overrides entière) ; inline de
  constante multi-fichiers ; création de champ sur `UndefinedField` ;
  renommage de ressource (propagation code + XML) ; `resolveCodeAction`
  multi-URI.

## 1.9.0 — V-D tranche 2 : refactorings lourds (01/11/2026, NON PUBLIÉE)

- **Ajouté** : EXTRACT variable/constante/méthode sur sélection ; INLINE
  variable/constante ; SAFE DELETE par usages multi-fichiers exhaustifs
  (oracle « 0 manqué »).

## 1.8.0 — V-D tranche 1 + V-C2 catalogue lint (31/10/2026, NON PUBLIÉE)

- **Ajouté** : organizeImports au fil codeAction (filtre `only` LSP 3.17) ;
  codeAction/resolve différé ; génération de code (6 capacités) ;
  intentions sans diagnostic ; +7 familles ecj (stubs d'abstraites, corps
  manquant, retour en void, imports en conflit, cast inutile) ; contrôles
  Range/ResourceType/CommitPrefEdits/Recycle ; segment CIDX compact CLBA
  de la base api-versions.
- Validation : 850 tests ; latence liste p95 255 µs.

## 1.7.0 — V-C3 : complétion niveau Android Studio (31/10/2026, NON PUBLIÉE)

- **Ajouté** : SMART completion (type attendu par bindings, union des
  surcharges à l'arité) ; typedef `@IntDef`/`@StringDef` ; littéraux
  `R.*` ; second appel (membres marqués + statiques importables) ; chaîne
  à deux sauts ; statement completion lexicale + RPC ; stats locales
  opt-in à fenêtre glissante ; mémoïsation des membres binaires ;
  `extends/implements/permits` en tête de déclaration ; capability
  `x-codeide.completionRanking` **v2**.
- **Modifié** : banc qualité v2 (corpus 24 positions, split train/éval
  déterministe ADR 0030) — baseline re-signée (corpus v2).

## 1.6.0 — V-F2 + V-C1/V-C2 (31/10/2026, NON PUBLIÉE)

- **Modifié** : le moniteur d'instance du moteur remplacé par des verrous
  nommés (ordre global documenté, comptage synchronized 99 → 0, cible
  < 6) ; ordonnanceur en pool plafonné 2.
- **Ajouté** : moteur d'inspections (contrat + profil + suppressions, 8
  pilotes) ; familles Android Lint (NewApi, InlinedApi, ObsoleteSdkInt,
  MissingSuperCall, SetTextI18n, `@SuppressLint`).
- Validation : 792 tests ; complétion concurrente p95 33 ms (vs 43).

## 1.5.0 — V-F1 : double-tampon + époques (31/10/2026, NON PUBLIÉE)

- **Ajouté** : double-tampon de l'environnement (0 figé, swap atomique),
  époques (« 0 réponse périmée », rejeu borné 3/8), annulation fine,
  republication des documents ouverts à chaque swap, repli documenté
  `…doubletampon.disabled`.
- Validation : 730 tests ; frappe pendant reconstruction tenue ×1,20 p50 /
  ×0,58 p95 (budget ≤ 2×).

## 1.4.0 — V-B : projet Android (31/10/2026, NON PUBLIÉE)

- **Ajouté** : contrat `:model` v11 (ModuleAndroidLsp, dérivateur disque
  KTS/Groovy tolérant — la sync prime) ; référentiel de ressources XML
  (values/ids/styleables, MAJ ciblée, octets compacts) ; classes light
  (R, BuildConfig, ViewBinding, DataBinding) ; navigation ressource vers
  XML déclarant ; interop Kotlin conservatrice.
- **Modifié** : RETRAIT de `declasserDatabinding` (rupture d'API arbitrée
  D6 — l'import `.databinding` résout avant build).

## 1.3.0 — V-E : navigation/références (31/10/2026, NON PUBLIÉE)

- **Ajouté** : `references` multi-fichiers réels (levée du confinement,
  capability `x-codeide.usages` v1) ; typage lecture/écriture complet du
  documentHighlight (affectations composées, incréments) ; RPC maison
  `textDocument/usagesGroupes` (plafond honnête 2 000).
- Note : le **1.2.0 est sauté délibérément** (valeur fantôme du vendord,
  proscrite par la fiche 059 I7).

## 1.1.6 — vague corrective complétion (31/10/2026, PUBLIÉE v1.1.6)

- **Corrigé** : retours appareil du 31/10 — inventaire des écarts
  ([`lsp-java/docs/ECARTS-SERVEUR.md`](docs/ECARTS-SERVEUR.md)) + les trois
  urgences de la complétion (commit `f15b738`).

## 1.1.5 — banc projet Android réel (31/10/2026, PUBLIÉE v1.1.5)

- **Ajouté** : `lsp-java/bancs/projet-android/` (réplique monip + sources générées,
  16 AARs/jars réels) et `BancProjetAndroidReelTest` (20 tests — timeline
  appareil rejouée au protocole).
- **Corrigé** : qualifier imbriqué muet, auto-import muet (devenu
  `additionalTextEdits`), marqueur de splice en fuite, identifiant isolé
  en corps de méthode.

## 1.1.4 — passe appareil 0.204.3 (30/10/2026, PUBLIÉE v1.1.4)

- **Corrigé** : l'appareil vivait en régime instantané — la sonde
  `plateformeResolvable()` (java.lang.Object résoluble par jrt OU jar
  porteur) repasse les gardes/diagnose/complétion en régime plein ;
  régression ART simulée verrouillée (`PlateformeClasspathARTTest`, 8
  tests).

## 1.1.3 — retours appareil 0.204.2 (30/10/2026, PUBLIÉE v1.1.3)

- **Corrigé** : extensions maison sous leur nom COMPLET
  (`textDocument/superDefinition`…) ; familles analytiques élargies
  (locale inutilisée sur la table rapide, privé inutilisé + portage hors
  corps pendant la frappe) ; déclencheurs de complétion `.` ET `@`.

## 1.1.2 — incident ART (30/10/2026, PUBLIÉE v1.1.2)

- **Corrigé** : `ServiceConfigurationError` (un `Error`, pas une
  `Exception`) à l'instanciation du fournisseur jrt sur ART — capture
  `Throwable`, repli silencieux vers le régime instantané documenté.

## 1.1.1 — corps hôte du diagnose rapide (30/10/2026, PUBLIÉE v1.1.1)

- **Corrigé** : le corps hôte du diagnose rapide reconnu structurellement —
  la frappe de l'utilisateur n'est plus effacée du texte diagnostiqué
  (commit `91b5ce8`).

## 1.1.0 — intégration CodeIDE (30/10/2026, PUBLIÉE v1.1.0)

- **Ajouté** : constructeur VIVANT `ServeurLangageJava(environnement)` ;
  routeur multi-modules AU PROTOCOLE (résolution par requête, replaceAll à
  chaud, jamais de re-initialize) ; sérialisation `superDefinition` alignée
  sur le vendord (fiche 066).

## 1.0.0 — bascule (30/10/2026, PUBLIÉE v1.0.0 — run 40942576172)

- **Ajouté** : première version publiée du moteur réécrit — étages N1–N8+T
  intégralement livrés (noyau compilateur & environnement, complétion &
  classement, hover/inlay/signatures, confort éditeur, navigation & usages,
  rename v2, CodeAction ModCommand, cycle de vie LSP & protocole riche,
  banc de parité croisé) ; 625 tests / 0 échec.
- **Bascule §8.5 signée** : checklist 6/6, passe appareil moto g06 18/18 /
  0 saut, deltas arbitrés 103 bruit / 62 assumées / 0 à rattraper.

---

## Tailles des artefacts publiés (mesurées le 03/11/2026, GitHub Packages)

| Version | `jo-codeide-lsp-java` (octets) | Compagnon `ecj-art` (octets) |
|---|---:|---:|
| 1.0.0 | 780 607 | 3 288 188 |
| 1.1.0 | 785 870 | 3 288 188 |
| 1.1.1 | 785 868 | 3 288 188 |
| 1.1.2 | 786 963 | 3 288 188 |
| 1.1.3 | 787 983 | 3 288 188 |
| 1.1.4 | 788 974 | 3 288 188 |
| 1.1.5 | 790 879 | 3 288 188 |
| 1.1.6 | 793 678 | 3 288 188 |
| 1.12.0 | 1 517 604 | 3 288 188 |
| 1.12.1 | 1 522 136 | 3 288 188 |
| 1.12.2 | 1 523 043 | 3 288 188 |

Le compagnon ecj est constant (ecj 3.40.0 épinglé). Le jar de la tête
(1.13.0, non publiée) mesure **1 532 056 octets** (build local du
03/11/2026 — méthode vérifiée : le build au tag v1.12.2 reproduit la
taille publiée à l'octet près).
