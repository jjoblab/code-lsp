# ADR 0118 — Intégration LSP/SPI et remplacement progressif dans CodeIDE

- **Statut** : Proposé
- **Date** : 2026-10-30

## Contexte

K10/intégration : comment le moteur neuf remplace l'ancien SANS rupture.
L'ancien moteur vit derrière un SPI (`MoteurLangageKotlin`,
ServiceLoader, descripteur `version.properties`, pack D6) avec
l'adaptateur LSP4J dans `:lsp:host` ; le moteur Java réécrit a montré
la voie (serveur intégré au module en N8, façade publique, protocole
riche, drapeaux).

## Sources consultées

- `code-lsp` : ADR 0015 Java (intégration LSP et remplacement
  progressif — drapeau, double moteur, retour arrière), `:java`
  N8 (cycle de vie LSP, codes d'erreur exacts, capability riche
  `x-codeide.completionRanking`), `USAGE.md`.
- Cahier : fiches 015 (cycle de vie session — tolérances post-close),
  021 (L2 capacités), 048 (previews par SPI).
- `codeide-lsp` : matrice de capacités (colonne Kotlin — capacités
  annoncées/servies/non annoncées), ADR 0213 (observateurs).

## Options

**Option 1 — Miroir Java N8 : serveur LSP4J dans le module + drapeau
pack ancien/nouveau.** Le descripteur de pack (id/version/min-app)
discrimine les deux moteurs côté hôte ; la substitution composite
permet le retour arrière en inversant une ligne.
**Option 2 — Transport conservé dans l'hôte ancien** : perpétue le
couplage — écarté (ADR 0101). **Option 3 — Big-bang** (remplacement
sans drapeau) : contredit toute la doctrine de migration du dépôt —
écarté. **Option 4 — Ne rien changer.**

## Critères et mesures

- Matrice des capacités ancienne (colonne Kotlin) = l'inventaire de
  parité à reproduire (annoncées et servies, non-annoncées honnêtes) ;
  l'écart A-K2 (super-définition livré mais matrice ❌) sera résolu au
  banc (les TESTS font foi).
- Tolérance de version du pack : contrats à défauts vides (fiches
  K3-like), panes absorbées (previews).

## Décision proposée

**Option 1** : le serveur LSP4K du module `:kotlin` (miroir `:java`)
porte les capacités de la matrice ; le remplacement est progressif :
(1) le pack neuf coexiste avec l'ancien (descripteurs distincts),
l'hôte routant derrière drapeau ; (2) deux versions de rodage sur
corpus réels avant retrait de l'ancien ; (3) retour arrière documenté
à chaque étage. Le protocole riche (capability `x-codeide.*`, époque
copy-on-write, re-filtrage local de complétion) suit les designs Java
(ADR 0012/0015) — le câble cel-lsp étant commun.

## Conséquences

- Aucune rupture utilisateur : l'ancien moteur sert la production
  jusqu'à la bascule (critère : parité du cahier MESURÉE sur appareil).
- L'hôte tri-langue ancien reste responsable du routage pendant la
  transition (son adaptation finale est le chaînon déjà identifié par
  la bascule Java §3.2.2).

## Plan de migration progressive

Étage LSP en fin de programme (miroir N8) ; drapeau dès la vague 0
(descripteur).

## Tests de non-régression et critères de sortie

- Matrice de capacités reproduite (annonces paritaires verrouillées
  par test W4-like) ; codes d'erreur exacts ; 0 réponse périmée ;
  bascule uniquement à parité cahier sur appareil.

## Risques

- R1 — Divergence des capabilities entre packs ancien/neuf pendant la
  transition : mitigation = table de parité verrouillée par test des
  deux côtés.
- R2 — Le chaînon hôte tarde (miroir Java §3.2.2) : le drapeau rend
  l'attente non bloquante.

---

## Amendement 2026-10-31 — mission « niveau Android Studio » (ADR 0136, statut Proposé)

L'option 1 (serveur LSP4J dans le module, drapeau pack, rodage, retour arrière) est livrée — mais **le serveur n'est PAS EN SERVICE** (le client consomme toujours l'ancien LSP Kotlin). L'**ADR 0136** recommande de **séquencer la bascule I0–I6 AVANT l'extension des capacités** : sans elle, toute capacité ajoutée est invisible (KH16) ; la publication du pack (Q-K4, `publish.yml` à étendre aux coordonnées Kotlin) en est le préalable mécanique, DÉBLOQUÉE depuis la levée de P-K2. Les 7 capacités absentes (dont semanticTokens) reçoivent une feuille de route fondée sur les paliers : semanticTokens devient FIABLE au palier 2, les hiérarchies suivent, `documentColor` s'appuie sur le référentiel de ressources partagé.
