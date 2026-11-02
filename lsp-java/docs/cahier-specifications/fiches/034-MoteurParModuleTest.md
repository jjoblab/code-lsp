# Fiche 034 — MoteurParModuleTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/ecj/MoteurParModuleTest.java`.
> **Domaine** : caches-perf
> **Résumé** : Classe du LOT E (v0.97.0, docs/LSP_JAVA.md §15.7) verrouillant le moteur **par module** de `CompilationEnvironment` : chaque module (clé = chemin Gradle) dérive son propre moteur — SON verrou, SES caches. Éprouve l'isolation des verrous (le monitor du module A tenu ne bloque plus le diagnostic du module B), l'invalidation de périmètre module (une sync Gradle de A vide le cache de parse de A SEUL), les sémantiques « replaceAll » par module (no-op + zéro notification sur sync redondant, invalidation + notification unique sur remplacement effectif), la compliance Java par projet, l'invalidation globale par champ partagé (index disque), `dispose()` global, la redirection des clés vides/null vers le contexte défaut et la concurrence bi-module. Fixtures : `MAIN_OK` (zéro erreur) et `MAIN_ERR` (`String s = 42`, au moins 1 erreur), sources multi-niveaux `var`+record (Java 8/21) et record+switch à motifs (Java 21), plus un `FauxIndexNomsVide` (index jamais prêt, aucune entrée, observateur jamais notifié).

## Comportements

### leVerrouDuModuleANeBloquePlusLeModuleB — verrou PAR moteur
- **Préconditions / entrée** : un `CompilationEnvironment` ; moteurs de `:app` et `:lib` chauffés (un `diagnose` de `MAIN_OK` chacun). Un thread titulaire prend le monitor du moteur A et le tient pendant 3 s.
- **Comportement attendu** : `jdt(":app")` et `jdt(":lib")` rendent deux moteurs distincts ; pendant que le monitor de A est tenu, le `diagnose` du moteur B sur `MAIN_ERR` complète et rend au moins 1 erreur (le verrou est propre à chaque moteur — avec un moteur unique partagé, l'appel resterait bloqué et le délai expirerait).
- **Bornes** : monitor de A tenu 3 s ; diagnostic de B rendu en moins de 2 s (délai `Future.get`) ; attentes de sûreté de 5 s.
- **Statut** : actif

### invaliderAVideLeMoteurDeASeulement — invalidation de périmètre module
- **Préconditions / entrée** : contextes `:a` (racine src-a) et `:b` (racine src-b) posés ; moteurs chauffés (cache de parse rempli de chaque côté). Entrée : re-sync Gradle de `:a` SEUL avec une autre racine (src-a-2).
- **Comportement attendu** : le moteur de `:a` est reconstruit (instance différente) avec un cache de parse vide ; le moteur de `:b` RESTE la même instance et son cache de parse reste rempli.
- **Bornes** : `parseCacheSize() >= 1` pour A et B avant re-sync ; `== 0` pour le nouveau moteur de A ; `>= 1` conservé pour B.
- **Statut** : actif

### remplacementRedondantEstUnNoOpSysteme — sync redondant
- **Préconditions / entrée** : contexte `:app` posé (racine src-stable) ; auditeur de contexte enregistré. Entrée : second `remplacerContexteModule(":app", …)` avec les MÊMES listes.
- **Comportement attendu** : no-op système — même instance de moteur, zéro notification d'auditeur.
- **Bornes** : 0 notification.
- **Statut** : actif

### remplacementEffectifInvalideEtNotifie — remplacement effectif
- **Préconditions / entrée** : contexte `:app` posé (racine src-avant) ; auditeur enregistré. Entrée : remplacement avec une autre racine (src-apres).
- **Comportement attendu** : le moteur est reconstruit (instance différente) et les auditeurs sont notifiés exactement une fois.
- **Bornes** : 1 notification.
- **Statut** : actif

### laComplianceParModuleDiagnostiqueSelonLeNiveauDuProjet — compliance par projet (S1-a)
- **Préconditions / entrée** : deux contextes du MÊME environnement : `:vieux` avec niveau « 8 », `:moderne` avec niveau « 21 ». Sources : `VAR_ET_RECORD` (record + `var`), `RECORD_ET_SWITCH` (record + switch à motifs).
- **Comportement attendu** : le niveau posé est observable (`complianceModule` et `complianceDemandee()` rendent « 8 »/« 21 ») ; `var`+record sous « 8 » produit au moins 1 erreur ; record+switch à motifs sous « 21 » produit 0 erreur ; le MÊME source `var`+record sous « 21 » produit 0 erreur.
- **Bornes** : `>= 1` erreur sous compliance 8 ; `0` erreur sous compliance 21.
- **Statut** : actif

### semantiquesDuNiveauJavaParModule — sémantiques du niveau (S1-a)
- **Préconditions / entrée** : niveau « 8 » posé sur `:app` ; auditeur enregistré. Séquence : sync par la surcharge 3 arguments (ignorant le niveau), puis changement de niveau SEUL (« 21 », mêmes listes), puis `null` explicite.
- **Comportement attendu** : la surcharge ignorant le niveau le CONSERVE (« 8 », sync redondant → même moteur, 0 notification) ; un changement de niveau seul invalide le moteur, notifie une fois et le nouveau moteur porte « 21 » ; `null` explicite ramène au défaut de l'environnement (`complianceModule` rend null) et notifie (2 cumulées) ; le moteur reconstruit hérite du niveau par défaut.
- **Bornes** : notifications 0 puis 1 puis 2 cumulées ; niveau par défaut « 17 » (défaut constructeur, héritage et non absence).
- **Statut** : actif

### attacherLIndexInvalideDefautEtModules — champ partagé
- **Préconditions / entrée** : moteur défaut et moteur `:app` construits. Entrée : `setIndexNoms` avec un index factice vide, puis re-attachement de la MÊME instance d'index.
- **Comportement attendu** : l'attachement invalide le moteur défaut ET celui du module (instances reconstruites) ; ré-attacher la même instance ne re-invalide pas (idempotence conservée).
- **Bornes** : — (l'index factice n'est jamais prêt et rend zéro entrée pour toute requête).
- **Statut** : actif

### disposeLibereDefautEtModules — libération globale
- **Préconditions / entrée** : contexte `:app` posé ; moteurs défaut et `:app` construits.
- **Comportement attendu** : avant `dispose()`, `isEcjInitialized()` et `isEcjInitialized(":app")` sont vrais (et faux pour un module jamais construit) ; après, les deux sont faux et les prochaines accès construisent des moteurs distincts des précédents.
- **Bornes** : —
- **Statut** : actif

### cleVideOuNullViseLeContexteDefaut — redirection documentée
- **Préconditions / entrée** : un environnement. Entrées : `jdt(null)`, `jdt("")`, `remplacerContexteModule("", …)` et `remplacerContexteModule(null, …)`.
- **Comportement attendu** : `jdt(null)` et `jdt("")` rendent le moteur du contexte DÉFAUT (même instance que `jdt()`) ; la voie qualifiée vide/null applique les sémantiques replaceAll au contexte défaut (moteur reconstruit après remplacement effectif).
- **Bornes** : —
- **Statut** : actif

### mixConcurrentSurDeuxMoteursDistincts — concurrence bi-module
- **Préconditions / entrée** : moteurs défaut et `:app` chauffés ; 8 threads (moitié sur le défaut, moitié sur `:app`), 12 itérations chacun, mix simultané `diagnose`/`resolveUnit`/`hoverAt` (offset 60) sur `MAIN_OK`/`MAIN_ERR`.
- **Comportement attendu** : aucune exception ; résultats cohérents SUR CHAQUE moteur : `MAIN_OK` → 0 erreur, `MAIN_ERR` → au moins 1 erreur ; `resolveUnit` jamais null (cache non corrompu).
- **Bornes** : 8 threads × 12 itérations ; chaque `Future` récupérée en 30 s max ; `awaitTermination` 5 s.
- **Statut** : actif

## Notes pour la réécriture
- La construction des moteurs est paresseuse : « chauffer » (un premier `diagnose`) avant toute mesure, sinon la première requête paie la construction de l'environnement ECJ.
- L'invariant d'acceptation est l'isolation PAR MOTEUR du verrou et du cache — pas la technique du monitor Java : toute implémentation (verrou par instance, actor par module…) satisfait le test dès qu'un module bloqué n'arrête pas l'autre.
- Le niveau Java par module : posé → conservé par les appels qui l'ignorent ; `null` explicite → retour au défaut de l'environnement (« 17 » au constructeur, transmis comme héritage) ; tout changement effectif (listes OU niveau) invalide et notifie exactement une fois.
- L'index de noms est un champ PARTAGÉ : son remplacement invalide tous les moteurs (défaut + modules), idempotent pour la même instance.
- La surface observée du faux index (`estPret`, `ajouterObservateurPret`, `jarDe`, `nomsParPrefixe`, `estPaquet`, `sousPaquetsDirects`, `typesDansPaquet`, `membresStatiques`) décrit le contrat minimal de l'index vu par l'environnement.
