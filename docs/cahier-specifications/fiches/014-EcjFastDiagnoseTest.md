# Fiche 014 — EcjFastDiagnoseTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/ecj/EcjFastDiagnoseTest.java`.
> **Domaine** : caches-perf
> **Résumé** : Diagnostics incrémentaux par corps de méthode (P6, v1.5.0 — portage du commit 4b9b880 du projet tiers de référence « skip the file parse entirely when a keystroke stays inside one body », adapté ecj : re-diagnostic du SEUL corps édité, autres corps remplacés par un synthétique isomorphe, problèmes portés du diagnostic précédent). Oracle d'équivalence stricte : chaque tour de frappe est joué sur le moteur sous test ET sur un moteur témoin NEUF (mémo vide) — les listes de diagnostics doivent être STRICTEMENT identiques (taille, offsets début/fin, sévérité, message, code) : le fast-path ne peut que gagner du temps, jamais perdre ni inventer un diagnostic. Fixture riche (classe `Main` du package `app`, marqueur « ⌂ » pour les frappes) : corps vide, corps court, méthode non-void avec return, corps édité, corps utilisant List+champ, corps avec erreur, signature cassée.

## Comportements

### frappesDansUnCorps_equivalenceEtFastPath — 4 tours de frappe dans le même corps
- **Préconditions / entrée** : moteur sous test + moteur témoin neuf par tour ; tours : s0 = « » (initial), s1 = ` int localeMorte2 = 2;`, s2 = ` int x = ; ` (syntaxe cassée volontaire), s3 = ` int x = 3; System.out.println(x);` — tous insérés au marqueur du corps `edite()`.
- **Comportement attendu** : à chaque tour, liste du moteur = liste du témoin (comparaison stricte) ; compteurs : tour 0 → fullDiagnoseCount=1, fastDiagnoseHits=0 ; tour 1 → fastDiagnoseHits=1 et fullDiagnoseCount=1 ; tour 2 → fastDiagnoseHits=2 ; tour 3 → fastDiagnoseHits=3, fullDiagnoseCount=1 (aucun diagnose complet supplémentaire sur toute la session).
- **Bornes** : compteurs exacts : fullDiagnoseCount = 1 sur les 4 tours ; fastDiagnoseHits 0→1→2→3.
- **Statut** : actif

### editionPuisSautDansUnAutreCorps_equivalence — chaînage inter-corps
- **Préconditions / entrée** : diagnose initial ; frappe dans `edite()` (` int a = 1;`) ; saut dans `jamaisAppelee()` (`localeMorte` 42→43 — corps masqué au tour précédent) ; retour dans `signatureCasse()` (`mauvaisNom2`→`mauvaisNom3`).
- **Comportement attendu** : équivalence stricte à chaque tour (problèmes du corps quitté PORTÉS, corps d'arrivée recalculé frais) ; fastDiagnoseHits = 2 après le saut, 3 après le retour.
- **Bornes** : fastDiagnoseHits = 2 puis 3.
- **Statut** : actif

### memeSource_memoSansReparse — mémo exact (republish post-debounce)
- **Préconditions / entrée** : la MÊME source (` int x = 1;`) diagnostiquée deux fois de suite.
- **Comportement attendu** : mêmes taille, messages et offsets de début ; fastDiagnoseHits = 1 et fullDiagnoseCount = 1 (rien de re-parsé — le résultat précédent est rendu tel quel).
- **Bornes** : 1/1.
- **Statut** : actif

### editionsStructurelles_repliSurDiagnoseComplet — repli obligatoire sur diagnose complet
- **Préconditions / entrée** : après le diagnose initial, 4 éditions cassant la preuve de confinement : (1) suppression de l'accolade fermante du corps édité, (2) renommage d'un paramètre de signature (`mavaisNom`→`meilleurNom`), (3) édition d'un initialiseur de champ (`champPrive` 1→2, hors corps), (4) remplacement à cheval sur deux corps (fusion `edite`/`utiliseListEtChamp`).
- **Comportement attendu** : équivalence stricte dans les 4 cas ; fullDiagnoseCount avance 1→2→3→4→5 ; fastDiagnoseHits = 0 (AUCUN fast-path ne s'applique aux éditions structurelles).
- **Bornes** : fullDiagnoseCount 1→5 ; fastDiagnoseHits = 0.
- **Statut** : actif

### corpsMasques_pasDeUnusedFallacieux — pas de faux « unused »
- **Préconditions / entrée** : fixture de base (l'import `java.util.List` et `champPrive` ne sont utilisés QUE par `utiliseListEtChamp`, corps masqué lors d'une frappe dans `edite()`) ; frappe ` int y = 2;` dans le corps édité.
- **Comportement attendu** : précondition vérifiée — la base ne signale pas List/champ comme « never used » ; après la frappe : AUCUN diagnostic « unused » (never used / is not used) mentionnant List ou champPrive ; équivalence stricte avec la référence.
- **Bornes** : —
- **Statut** : actif

### killSwitch_declareDansLaClasse — kill-switch du fast-path
- **Préconditions / entrée** : réflexion sur le champ `FAST_DIAGNOSE_DISABLED` de `EcjCompilerEngine`.
- **Comportement attendu** : le champ existe, son type est `boolean` (statique final), sa valeur est lisible non nulle. La désactivation FONCTIONNELLE par `-Djo.lspjava.ecj.fastdiagnose.disabled` n'est pas testable en même JVM (propriété lue en statique) — le test ne verrouille que le contrat de présence du flag.
- **Bornes** : —
- **Statut** : actif

### aucunProblemeFraisDansUnCorpsMasque — le synthétique ne fuit pas
- **Préconditions / entrée** : diagnose initial puis frappe ` methodeInexistante2();` dans le corps édité (tandis que `methodeInexistante()` vit dans un corps masqué).
- **Comportement attendu** : fastDiagnoseHits = 1 ; l'erreur fraîche « methodeInexistante2 » EST publiée (le corps édité est bien parsé frais) ; équivalence stricte (l'erreur de `methodeInexistante`, corps masqué, est PORTÉE avec les mêmes offsets — aucun problème frais n'est inventé dans un corps masqué).
- **Bornes** : fastDiagnoseHits = 1.
- **Statut** : actif

## Notes pour la réécriture
- L'oracle « moteur témoin NEUF par tour » est le pattern à répliquer à l'identique : équivalence bit-à-bit des listes (offsets, sévérité, message, code), tri par offset des deux côtés.
- Compteurs observables indispensables (`fullDiagnoseCount`, `fastDiagnoseHits`) : la réécriture Kotlin devra exposer des équivalents pour que ces tests restent exécutables.
- Pièges de la fixture à préserver : méthode non-void avec return (pas de « must return » fallacieux quand son corps est remplacé par le synthétique), import utilisé uniquement par un corps masqué (pas d'« unused import » fallacieux), corps vide et corps trop court laissés verbatim.
- Le repli complet est OBLIGATOIRE dès que la preuve de confinement casse : structure (accolade), signature, initialiseur de champ, région traversante.
- Kill-switch par propriété système `-Djo.lspjava.ecj.fastdiagnose.disabled` — seule la présence du flag est contractuelle (test réflexif).
