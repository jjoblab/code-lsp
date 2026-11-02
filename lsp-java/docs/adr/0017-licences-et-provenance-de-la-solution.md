# ADR 0017 — Licences et provenance de la solution retenue (dont conditions des artefacts JetBrains)

- **Statut** : Proposé
- **Date** : 2026-10-28

## Contexte

La stratégie hybride ( ADR 0004 ) repose sur : code propriétaire repris/reécrit + **artefacts binaires tiers** ( ecj EPL-2.0 modifié et redistribué ; `org.jetbrains:java-syntax` Apache-2.0 ; lsp4j EPL-2.0 ; protocole tooling jjoblab sans LICENSE à ce jour ; guava/ASM Apache-2.0/BSD-3 ) + **consultation des sources IntelliJ** ( Apache-2.0, « JetBrains Open-Source Build Terms v1.3 » du 15/06/2026 — aucune copie, R6.4 ). Le prompt ( R6.5 ) exige de **vérifier explicitement les conditions d'utilisation des artefacts binaires publiés par JetBrains** et de les consigner ici. Constats disponibles :

- **`java-syntax` / modules KMP `org.jetbrains:*:0.3.381`** : publiés sur le dépôt `intellij-dependencies` ( `packages.jetbrains.team/maven/p/ij/intellij-dependencies`, accès via cache-redirector — **vérifié accessible le 2026-10-28**, URLs dans `spike/scripts/telecharger-piles.sh` ). Les conditions applicables aux artefacts binaires de ce dépôt ne sont **pas** résumées dans un fichier LICENSE embarqué dans les jars ( vérifié : aucun `META-INF/LICENSE` dans les 4 jars V1 ) ; la licence des **sources** correspondantes est Apache-2.0 ( `intellij-community/LICENSE.txt` ). **À faire ( action avant la vague parsing ) : obtenir/consigner la déclaration de conditions des artefacts binaires ( page du dépôt / terms JetBrains ) — en l'absence de déclaration contraire et pour des artefacts compilés depuis des sources Apache-2.0, l'hypothèse de travail est Apache-2.0, à confirmer par le propriétaire juridiquement.**
- **ecj** ( `org.eclipse.jdt:ecj` ) : EPL-2.0 — le statu quo le **modifie** ( patch ART ) et le **redistribue** ( graphé dans l'APK ) : obligations = mention des modifications + disponibilité des sources amont ( déjà documentées dans `THIRD_PARTY_NOTICES.md` ancien, note 1 — à recopier dans le `NOTICE` du nouveau dépôt ).
- **Protocole tooling** : dépôt frère **sans fichier LICENSE** — action propriétaire ( ADR 0001 ) ; sinon réécriture des ~10 types consommés.
- **Consultations** : intellij-community ( lecture seule, aucune copie ) ; ADR 0110/0200 anciens ( entrées de mission ) ; **aucune source GPL/AGPL ni licence non vérifiée consultée** ( R6.2 — attesté pour toute l'étude ).
- Attributions à conserver si reprise ( R9.5 ) : grammaire Kotlin vendorée ( hors périmètre R8, citée ) ; en-têtes `com.siyeh.ig.*` ( si un jour réécriture d'inspections — interdite sans décision ) ; ecj modifications.

## Sources consultées

- `JetBrains/intellij-community` @ `865067b0` : `LICENSE.txt` ( « JetBrains Open-Source Build Terms v1.3 » du 15/06/2026 — sources Apache-2.0 ). Vérification embarquée : absence de `META-INF/LICENSE` dans les jars V1 ( inspection zip, spike ).
- Dépôt `packages.jetbrains.team/maven/p/ij/intellij-dependencies` : métadonnées Maven consultées ( accessible ; pas de fichier de conditions dédié constaté dans les jars ).
- `jjoblab/codeide-lsp` @ `cb809e14` : `docs/THIRD_PARTY_NOTICES.md` ( chaîne ecj, note protocole tooling ), ADR 0110/0200.
- `jjoblab/codeide-tooling` @ `b60f7f0` : absence de `LICENSE` constatée.
- Licences publiques : EPL-2.0 ( texte ), Apache-2.0, BSD-3 ( ASM ) — textes de licence lus via leurs canons publics.
- Registre : `docs/PROVENANCE-REPRISE.md` ( exclusions, candidats, dépendances d'artefacts ).

## Options

**Option 1 — Chaîne de licences explicite et auditable ( recommandé ).** `LICENSE` ( Apache-2.0, ADR 0001 ) + `NOTICE` exhaustif ( ecj modifié + artefacts JetBrains + protocole tooling + guava/ASM/lsp4j + attributions reprises ) + registre `PROVENANCE-REPRISE.md` maintenu à chaque reprise + vérification CI ( fichier repris hors registre = échec ) + **action propriétaire** sur le protocole tooling et la confirmation des conditions artefacts JetBrains.

**Option 2 — NOTICES minimales ( ne citer qu'ecj ).** Insuffisant pour la chaîne réelle ( protocole, artefacts ) — risque de non-conformité silencieuse ; rejeté.

**Option 3 — Tout réécrire pour éviter les obligations.** Coût démesuré ( ADR 0001 option 2 déjà écartée ) ; les obligations ( NOTICE, sources ) sont légères et documentées.

**Option « ne rien changer »** : les notices vivent dans l'ancien dépôt — insuffisant dès que le nouveau dépôt publie ses propres artefacts.

## Critères et mesures

- Auditabilité : chaque ligne de `NOTICE` renvoie à une entrée du registre `PROVENANCE-REPRISE.md` ( dépôt, chemin, commit, licence, statut, décision ).
- Vérification R6.2 de l'étude : aucune source interdite consultée — attesté dans `ALERTES.md` et la conclusion de `01-architecture-psi.md` §Sources.

## Décision proposée

**Option 1** : `NOTICE` créé dès la vague 0 ( contenu initial : ecj EPL-2.0 modifié — reprise de la note ancienne ; java-syntax + dépendances KMP — conditions binaires **à confirmer** ( hypothèse Apache-2.0, action propriétaire ) ; lsp4j EPL-2.0 ; protocole tooling ( licence en attente ) ; guava Apache-2.0 ; ASM BSD-3 ) ; registre CI-vérifié ; deux **actions propriétaire** tracées : (a) LICENSE sur `codeide-tooling`, (b) confirmation des conditions des artefacts binaires JetBrains ( intellij-dependencies ) avant la vague « parsing incrémental ». Toute future dépendance passe par une entrée `NOTICE` + registre ( revue de PR obligatoire ).

## Conséquences

- La chaîne de provenance du nouveau dépôt est reconstruisable de bout en bout ( registre ↔ NOTICE ↔ code ) — exigence R9/R10 du prompt.
- Le blocage éventuel sur les conditions JetBrains artefacts retarde la seule vague qui consomme `java-syntax` ( ADR 0006 ) — pas les vagues classement/CodeAction/rename ( aucun artefact nouveau ).
- Publication d'artefacts ( GitHub Packages, ADR 0002 ) : le `NOTICE` voyage avec les POM ( obligation de redistribution ).

## Plan de migration progressive

1. Vague 0 : `LICENSE` + `NOTICE` squelette + vérification CI registre.
2. Avant chaque reprise/vague : entrée registre + ligne `NOTICE` dans la même PR ( règle « pas de reprise sans manifeste » ).
3. Actions propriétaire ( a/b ) suivies dans `ALERTES.md` jusqu'à clôture.

## Tests de non-régression et critères de sortie

- CI : (i) tout fichier source du dépôt dont le chemin correspond à une reprise doit avoir son entrée registre ; (ii) `NOTICE` mentionne chaque dépendance du graphe résolu ( diff du graphe = échec ) ; (iii) scan R6.2 des sources tierces du dossier vendor éventuel.
- Critère de sortie : audit de licence factice passé par un tiers ( revue du registre ) sans écart.

## Risques

- **R1 — Conditions artefacts JetBrains non confirmées** : la vague parsing est conditionnée ; repli = réécrire le parseur ( coûteux ) ou ne pas faire la vague ( perte limitée aux gains ADR 0006 ).
- **R2 — Protocole tooling sans licence** : réécriture des ~10 types ( modeste ) si l'action propriétaire échoue.
- **R3 — Oubli de mention de modification ecj** : vérification CI du `NOTICE` contre le graphe réel ( diff automatique ).
