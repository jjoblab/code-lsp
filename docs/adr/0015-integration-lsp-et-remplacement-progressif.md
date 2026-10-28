# ADR 0015 — Intégration LSP et remplacement progressif dans CodeIDE

- **Statut** : Proposé
- **Date** : 2026-10-28

## Contexte

La stratégie hybride ( ADR 0004 ) modifie le moteur **par vagues**, derrière des drapeaux, sans casser la production. Le point d'intégration est inchangé par construction : CodeIDE consomme `:lsp:host` ( « `feature:editor` ne voit que `:lsp:host` », `CodeIDE/settings.gradle.kts:349` ), qui orchestre les moteurs ; l'hôte consommera la façade publique du nouveau dépôt ( `jo.lspjava.api.*` existante, ADR 0002 ) par substitution composite ( lignes 226–251 du même fichier ) en DEV et par GitHub Packages en production. Le client LSP in-process (`cel-lsp`, code-editor, Apache-2.0) est consommé tel quel.

La **matrice de capacités** ( `LSP_MATRICE_CAPACITES.md` ancien ) est la référence de parité : chaque greffe doit la maintenir ou l'améliorer, jamais la régresser ( ADR 0016 pour les mesures ). Capacités LSP concernées par les vagues : `completion` ( classement + protocole riche `x-codeide.completionRanking`, ADR 0012 ), `codeAction` ( ModCommand v2, ADR 0013 ), `references`/`documentHighlight`/`rename` ( ADR 0014 ), diagnostics ( inchangés — ecj ).

## Sources consultées

- `jjoblab/CodeIDE` @ `54e99c6` ( v0.202.0, lecture seule ) : `settings.gradle.kts` ( substitution composite 226–251, `feature:editor` :349 ), `AGENTS.md`, `docs/STRATEGIE_VERIFICATION.md`.
- `jjoblab/code-editor` @ `066b8c0` ( lecture seule ) : `cel-lsp` ( 306 f / 80 993 l., Apache-2.0, LICENSE+NOTICE ; dépendance LSP EPL-2.0 notée ).
- `jjoblab/codeide-lsp` @ `cb809e14` : `docs/LSP_MATRICE_CAPACITES.md`, `docs/LSP_JAVA.md` ( §19 diagnostics riches ), API `jo.lspjava.api`.
- Spécification LSP ( document public ) ; étude D8/D9 ( frontières calcul/présentation ).

## Options

**Option 1 — Vagues derrière drapeaux + capabilities LSP de négociation ( recommandé ).** Chaque greffe s'active : côté serveur par drapeau de configuration ( persistant, versionné ), côté client par **capability LSP** optionnelle ( absence = repli automatique sur le comportement actuel ). Le remplacement du moteur ( reprise ADR 0001/0002 ) passe par la substitution composite de CodeIDE — une ligne de configuration, inversible.

**Option 2 — Bascule big-bang à la fin des vagues.** Concentre le risque, interdit la mesure en production progressive — rejeté.

**Option 3 — Double déploiement parallèle ( ancien hôte + nouveau moteur en side-load ).** Utile seulement pour les mesures A/B sur appareil réels ; à utiliser **pendant** les vagues ( rail de mesure ), pas comme architecture cible.

**Option « ne rien changer »** : l'intégration actuelle ( artefacts GitHub Packages ) — c'est précisément le mécanisme conservé ; seule la source de l'artefact change.

## Critères et mesures

- Matrice de capacités : niveau actuel = plancher de chaque vague ( suivi dans ce document à chaque livraison ).
- Retour arrière mesuré : inverser la substitution composite = 1 ligne ; drapeau = 1 clé de configuration ; capability absente = repli client automatique. Aucun retrait de code avant 2 versions sans régression ( règle générale des ADR 0012–0014 ).

## Décision proposée

**Option 1** ( avec l'option 3 comme **rail de mesure** pendant les vagues 1–2 ) : drapeaux serveur + capabilities LSP pour toutes les greffes des ADR 0012/0013/0014 ; substitution composite pour l'artefact moteur ; suppression du code ancien seulement après double validation ( banc ADR 0016 + 2 versions en production sans régression ). Les capacités **annoncées** au client restent strictement alignées sur la matrice ( pas d'annonce de capacité non testée — règle matrice ).

## Conséquences

- Le client CodeIDE doit apprendre : (a) la capability de classement riche ( re-filtrage local, ADR 0012 ) ; (b) la preview de rename ( dialogue de conflits projeté, ADR 0014 ). Ces développements côté CodeIDE sont planifiés dans les vagues correspondantes ( hors périmètre moteur — dépôt CodeIDE ).
- Le protocole des capabilities maison est préfixé `x-codeide.` et versionné ( `x-codeide.completionRanking.v1` ) ; toute évolution = capability nouvelle, pas de mutation silencieuse.
- L'hôte ( ancien dépôt ) reste l'orchestrateur tri-langue : le nouveau dépôt n'expose que la façade Java ( R8, ADR 0002 ).

## Plan de migration progressive

1. **Vague 0** : squelette + CI + publication d'artefacts vides + substitution branchée en DEV ( CodeIDE consomme le dépôt neuf sans changement fonctionnel ) — critère de sortie : build CodeIDE vert avec l'artefact neuf.
2. **Vagues 1+** ( ordre consolidé dans `00-synthese.md` §4 ) : chaque vague = drapeau + capability + banc + 2 versions de rodage ; puis retrait du code ancien.
3. **Retour arrière global** : substitution composite inversée + drapeaux off — l'état d'avant la stratégie est reconstruit sans re-déploiement du client.

## Tests de non-régression et critères de sortie

- Par LSP capability : tests contractuels client-serveur ( capability présente/absente ) ; matrice de capacités régénérée à chaque vague et jointe au release note.
- Intégration : tests d'hôte existants ( 141 tests ) rejoués contre la façade neuve ; zéro comportement perdu.

## Risques

- **R1 — Divergence client/serveur sur les capabilities maison** : contrat typé versionné + tests contractuels dans les deux dépôts.
- **R2 — Fenêtre de double maintenance CodeIDE** : les développements client sont minimes ( re-filtrage, dialogue ) mais à planifier ; rail A/B pour valider.
- **R3 — La matrice annonce plus que le livré** : règle stricte — capacité testée = annoncée ; sinon absente.
