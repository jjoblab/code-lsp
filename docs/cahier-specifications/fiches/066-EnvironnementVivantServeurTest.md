# Fiche 066 — EnvironnementVivantServeurTest

> **Source** : NÉO (30/10/2026) — née de l'intégration CodeIDE
> ([`INTEGRATION-CODEIDE.md`](../../INTEGRATION-CODEIDE.md) §3.3, option B :
> routeur multi-modules AU PROTOCOLE) ; le pendant vendord est
> `CompilationEnvironmentRouteurTest` (fiche 047, moteur seul) — cette fiche
> éprouve le même contrat à TRAVERS la façade `ServeurLangageJava` et la
> boucle JSON-RPC réelle.
> **Domaine** : protocole / intégration hôte
> **Résumé** : le RÉGIME VIVANT du serveur — `ServeurLangageJava(environnement)
> { rappel }` : UNE session LSP par projet, le moteur résolu PAR REQUÊTE par
> le routeur fichier → module (fiche 047), les reconfigurations de l'hôte
> remplacent les CONTEXTES à chaud (sémantiques replaceAll fiche 034), JAMAIS
> de re-initialize. Montage : le TEST joue l'hôte (il détient
> l'`EnvironnementCompilation` et le nourrit — motif `OrchestrateurLsp`),
> deux modules `:app`/`:core` aux racines canoniques réelles, la boucle
> locale des fiches 054/058/059. Gate visible : `isLikelyUsable` (image jrt
> du JDK de test — motif fiche 026). Fixtures en `Files.createTempDirectory`
> MANUEL (motif fiche 026 : le `@TempDir` de méthode disparaît en cours de
> suite ordonnée — la résolution des tests suivants mourrait silencieusement).

## Comportements

### leDocumentEstAnalyseParLeMoteurDeSonModule — routage fichier → module au protocole
- **Préconditions / entrée** : `:app` nourri avec [appmodule/src, coremodule/src] (le frère en entrée MODULE, motif OrchestrateurLsp) ; didOpen d'`app/Alpha.java` (import `coeur.Coeur`) ; complétion juste après « c. ».
- **Comportement attendu** : « battre » (membre du FRÈRE, racines de `:app`) est proposé — le document est analysé par le moteur de SON module.
- **Bornes** : —
- **Statut** : actif

### leDocumentHorsArbreTombeSurLeMoteurDefaut — repli défaut (contexte vide)
- **Préconditions / entrée** : même source ouverte sous `horsarbre/Perdu.java` (non routé) ; complétion après « c. ».
- **Comportement attendu** : « battre » N'est PAS proposé — le moteur DÉFAUT n'a pas les racines des modules.
- **Bornes** : —
- **Statut** : actif

### lesDiagnosticsSontRoutesVersLeMoteurDuModule — publicateur à fournisseur
- **Préconditions / entrée** : didOpen d'`app/Buggy.java` (« int x = ; ») ; attente de la publication.
- **Comportement attendu** : la publication porte l'ERREUR de syntaxe — le moteur du module est résolu AU TIR (fenêtre du debounce, fiche 047).
- **Bornes** : debounce 50 ms, drain 15 s.
- **Statut** : actif

### laReconfigurationAChaudEstVueSansReinitialisation — replaceAll fiche 034 au protocole
- **Préconditions / entrée** : après sollicitation du moteur `:app`, une SECONDE sync AJOUTE la racine `neufmodule` (remplacement EFFECTIF : moteur du module invalidé) ; rejet du second `initialize` (re-initialize interdit, -32600) ; didOpen du corps v1 (import `neuf.Neuf`) ; complétion après « n. ».
- **Comportement attendu** : « valeur » (membre de Neuf, racine AJOUTÉE) est proposé ; l'état du cycle est resté INITIALIZED ; le second initialize est rejeté.
- **Bornes** : —
- **Statut** : actif

### lesSyncsRedondantsEtEffectifsTraversentLaMemeSession — invariant ADR 0211
- **Préconditions / entrée** : cinq syncs mêlées (trois redondantes no-op, deux effectives par ordre des racines changé) ; didOpen v3 (texte original) ; complétion après « c. ».
- **Comportement attendu** : la résolution du frère SURVIT aux syncs (« battre » toujours proposé) ; le cycle est resté INITIALIZED à travers les cinq syncs — le nourrissage moteur unique par sync tient SANS reconstruction de session.
- **Bornes** : 5 syncs.
- **Statut** : actif

### decompiledSourceSondeLesMoteursVivants — motif ADR 0215 du vendored
- **Préconditions / entrée** : moteurs vivants construits par les requêtes précédentes ; `decompiledSource("java.lang.String")` puis un FQCN inconnu.
- **Comportement attendu** : la source (stub intrinsèque du régime instantané ou attachée) est servie par un moteur VIVANT (défaut d'abord, puis modules) ; le FQCN inconnu rend `source` null (honnêteté §9.2 du `USAGE.md`).
- **Bornes** : —
- **Statut** : actif

## Notes pour la réécriture
- Le constructeur `ServeurLangageJava(environnement, rappelArret)` est la façade de l'intégration CodeIDE : le serveur NE DISPOSE PAS l'environnement (l'hôte le possède — miroir exact du vendored `JavaLanguageServer`).
- Les services dérivés (publicateur, renommage, symboles d'espace) consomment des FOURNISSEURS `(chemin) -> MoteurJava?` — résolution au TIR / par document, JAMAIS de capture figée en régime vivant.
- `superDefinition` suit le CONTRAT DE SÉRIALISATION du vendord (`{path, start, end, label}` — miroir exact du client cel-lsp) : l'enrichissement de la fiche 054 le verrouille côté fil.
- Suites ordonnées (`@TestMethodOrder`) : l'état MUTABLE de l'environnement est le SUJET — chaque test doit DRAINER ses tirs retardés (publicateur) avant de rendre la main au suivant.
