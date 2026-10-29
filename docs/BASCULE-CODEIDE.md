# BASCULE CÔTÉ CODEIDE — procédure de mise en œuvre (§8.5, décision signée le 30/10/2026)

> **Rôle** : mise en œuvre **côté client** de la décision de bascule signée le
> 30/10/2026 ([`bancs/GABARIT-BASCULE-APPAREIL.md`](../bancs/GABARIT-BASCULE-APPAREIL.md)
> §6 — checklist 6/6 : banc croisé CI VERT, passe appareil 18/18 / 0 saut,
> latences consignées, deltas arbitrés 103/62/0, retour arrière 1 ligne).
> Ce document est la **procédure préparée** dans le dépôt `code-lsp` ; les
> modifications proprement dites ont lieu dans le dépôt tiers
> `jjoblab/CodeIDE` et restent à la main du propriétaire (ou de son agent,
> sur instruction explicite). Chaque fait est marqué **vérifié** (constaté
> dans ce dépôt au 30/10) ou **à faire** (action CodeIDE).

## 1. Faits vérifiés côté code-lsp (le fournisseur est prêt)

| Fait | Preuve |
|---|---|
| Version du dépôt = **1.0.0** (décision §8.5 signée) | `build.gradle.kts` — `serverInfo.version` générée de cette valeur (fiche 059 I7) |
| Bytecode **17**, consommable par la chaîne Android/ART (d8) | `java/build.gradle.kts` (`jvmTarget` 17, `sourceCompatibility` 17) |
| Façade publique LSP : `jo.codeide.lsp.java.protocole.ServeurLangageJava` (implémente `LanguageServer` + `TextDocumentService` + `WorkspaceService` + expérimentales) | `java/src/main/kotlin/.../protocole/ServeurLangageJava.kt` |
| Façade publique in-process : `jo.codeide.lsp.java.MoteurJava` (tests/bancs, §10 du `USAGE.md`) | `java/src/main/kotlin/.../MoteurJava.kt` |
| Mode autonome `setStandaloneMode(true)` + lanceur stdio lsp4j documentés (exemple complet) | `USAGE.md` §4.1 |
| CI **VERTE** sur le commit de la signature (6/6 jobs : banc parité croisée, banc qualité, empreinte/latence, gates Android, provenance, build/tests) | API GitHub Actions, commit `5b0db74` |
| **Non fait** : publication `maven-publish` vers GitHub Packages — aucun bloc `publishing` dans le dépôt | constat du 30/10 (prérequis §3.2) |

## 2. Point d'intégration (inchangé par construction)

CodeIDE consomme `:lsp:host`, qui orchestre les moteurs ; l'hôte consomme la
façade publique ci-dessus **par substitution composite** (ADR 0015 : lignes
226–251 de `CodeIDE/settings.gradle.kts`, citées à l'état `54e99c6` —
**à re-vérifier au HEAD courant** avant édition). Le client LSP in-process
(`cel-lsp`, code-editor, Apache-2.0) est consommé tel quel : aucune
modification du client n'est requise — la bascule ne touche que la source
de l'artefact moteur.

## 3. Étapes

### 3.1 DEV — substitution composite (sans publication)

1. Dans `CodeIDE/settings.gradle.kts` (bloc de substitution, l. 226–251 à
   l'état cité par l'ADR 0015) : substituer le module moteur consommé par
   `:lsp:host` vers le dépôt `code-lsp` local
   (`includeBuild("<chemin>/code-lsp")` + `dependencySubstitution` du module
   `jo.codeide.lsp.java` vers `:java` du build inclus).
2. Construire CodeIDE ; vérifier au premier lancement :
   - `initialize` rend `serverInfo = { "name": "lspjava", "version": "1.0.0" }`
     (le témoin direct que le moteur neuf est branché) ;
   - complétion, diagnostics, survol, renommage sur un projet Android réel
     (le scénario des fiches 004/010/039).
3. Toute anomalie ici n'est **pas** une régression de la bascule mais un
   écart d'intégration : la consigner et arbitrer (le moteur reste sous
   bancs bloquants dans `code-lsp`).

### 3.2 PRODUCTION — GitHub Packages (prérequis à créer dans code-lsp)

1. **À faire dans `code-lsp`** : ajouter le bloc `maven-publish` au module
   `:java` (publication `jo.codeide.lsp.java:jo-codeide-lsp-java:1.0.0`
   vers `maven.pkg.github.com/jjoblab/code-lsp`) — PR dédiée, la CI
   publie sur tag. La coordonnée Maven exacte est à fixer dans cette PR
   (rien ne l'épingle aujourd'hui).
2. Dans `CodeIDE` : déclarer le dépôt GitHub Packages (auth token
   `jjoblab`) et remplacer la dépendance moteur par la coordonnée publiée.
3. Incrément de version CodeIDE selon sa convention de release.

### 3.3 Retour arrière (critère 6 du gabarit — 1 ligne)

Inverser la substitution composite (ou retirer la dépendance GitHub
Packages) dans `CodeIDE/settings.gradle.kts` : `:lsp:host` re-pointe sur le
moteur vendored, l'état d'avant la bascule est reconstruit **sans
re-déploiement du client** (ADR 0015). Drapeaux serveur `off` si des vagues
sont actives.

## 4. Vérifications post-bascule (témoin et filet)

- `serverInfo.version` = **1.0.0** côté client (témoin du branchement) ;
- les 6 jobs CI de `code-lsp` restent verts sur les commits suivants
  (banc croisé bloquant — l'oracle épinglé `cb809e14` demeure la référence
  jusqu'au retrait du vendored, qui suit la règle « 2 versions sans
  régression » des ADR 0012–0014) ;
- suivi post-bascule borné (gabarit §6) : filtrage des membres
  package-private (`valueOfCodePoint`, `coder`, `value`, `hash`,
  `hashIsZero`, `isLatin1`), extension *optionnelle* de la collecte TYPE
  (hump/sous-chaîne), ouverture des gates `model.json` (Fiche010/Fiche039)
  sur export réel ou équivalent régénérable.
