# ADR 0028 — Recherche et navigation exhaustives (levée de la parité stricte, lecture/écriture, ressources)

- **Statut** : Proposé
- **Date** : 2026-10-31
- **Écarts résolus** : G-013, G-026, G-027, G-028 (`01-audit-moteur.md` §1.8, §3.5)

## Contexte

Découverte décisive de l'audit (H8) : le **moteur** calcule déjà des usages
multi-fichiers exhaustifs (`ServiceUsages.kt:60–100` — index de mots + clé de
binding, rappel = oracle au banc `:java:benchUsages`, p95 ~62 ms hérités) ; c'est la
**couche protocole** qui confine la réponse au document courant
(`ServeurLangageJava.kt:666–685`) pour *parité stricte* avec le vendord (§10.4,
fiche 054) — décision du 28/10 explicite : « la correction (rendre l'appel
inter-fichiers) reste une amélioration candidate post-bascule ». La bascule est
signée (30/10, v1.0.0) : la parité n'a plus d'objet. En revanche :
`documentHighlight` ne distingue pas lecture/écriture (H8), et la navigation
ressource ↔ code / manifest ↔ classe n'existe pas (H1 — dépend ADR 0020/0021).

## Sources consultées

- `02-reference-android-studio.md` §A5 (find usages groupés par
  fichiers/packages, clustering, options « occurrences texte », data flow pour
  champs, safe delete), §B11 (navigation `R.*` ↔ XML bidirectionnelle, rename de
  ressource avec propagation code+XML et collisions).
- `01-audit-moteur.md` §1.8, §3.5 (preuves).
- ADR 0014 (QueryExecutor sur index — socle livré), `docs/ECARTS-SERVEUR.md` §2 M4
  (écart assumé, coût réel « à mesurer » — le banc le mesure déjà).
- Banc `:java:benchUsages` (rappel = oracle, CI-bloquant) + P7.

## Options

**Option 1 — Lever le confinement + typage lecture/écriture + navigation ressource
(recommandée).** (a) Retirer le filtre de confinement (l'API `references` rend les
usages multi-fichiers réels, `includeDeclaration` inchangé) ; **ADR de rupture
explicite de la parité stricte** (la décision 28/10 est amendée par le
propriétaire — décision D1 demandée). (b) Typer `Surbrillance` (documentHighlight)
en Lecture/Écriture/Déclaration (la matière existe : `CollecteurUsages` marque
déclaration ; l'écriture = affectation/argument d'appel mutateur — bindings ecj).
(c) Navigation ressource : `R.string.x`/`R.id.x` → déclaration XML (`definition`),
`@string/x` → usages code (l'index de mots étendu aux XML — ADR 0020), manifest
↔ classes déclarées (`android:name`). (d) Rename de ressource : occurrences code +
XML, collisions sur le référentiel (moteur de collisions N5 réutilisé). (e) Usages
**groupés** par fichier (métadonnées de regroupement dans la réponse — le rendu
arborescent revient au client).

**Option 2 — Conserver le confinement.** Rejetée : la bascule est faite, la
fonctionnalité est prête, l'utilisateur perd des usages réels — contre la cible
« niveau AS ».

**Option 3 — Multi-fichiers seulement sur commande.** Sous-ensemble déroutant (deux
comportements selon le geste) ; rejeté au profit du comportement unique honnête.

## Critères et mesures

- Rappel = oracle (banc existant) inchangé ; p95 multi-fichiers réel [hérité ~62 ms
  JVM lourd — à re-mesurer sur corpus 5 projets, cible < 100 ms].
- P7 (rename sûr, corpus) : inchangé — la levée n'ajoute pas d'occurrences fausses
  (même index).
- Navigation ressource : cible exacte (fichier + plage de l'élément XML) sur cas
  dorés des 5 projets.
- [Client] : rendu groupé + styles lecture/écriture — à consommer (K2).

## Décision proposée

**Option 1.** La levée du confinement se fait **dans un commit unique** avec : tests
de la gate « taille attendue = 1 » inversés (nouveau contrat : usages multi-fichiers),
mise à jour de la fiche 054 (amendement cahier — décision D1), note dans
`ECARTS-SERVEUR.md` (M4 → corrigé). L'implémentation protocole (repli si un client
ancien exige le confinement) : capability maison versionnée — refus de complexifier :
le client consomme ce qu'il veut de la liste.

## Conséquences

- `references`, `documentHighlight` (typés), la navigation ressource et le rename de
  ressource atteignent le niveau de la référence sur les axes couverts.
- Le client doit suivre (styles, groupement) — liste séparée prête pour le prompt
  `cel-lsp`/`code-editor`.
- La parité stricte §10.4 est **formellement abandonnée** pour `references` (le
  programme de réécriture est terminé — la bascule a eu lieu).

## Plan de migration progressive

1. Levée du confinement + amendement fiche 054 + tests inversés (vague V-E, tôt —
   gain immédiat, coût ~0,5 j-h).
2. Typage lecture/écriture de `documentHighlight` (+ styles client).
3. Navigation ressource ↔ code (après ADR 0020/0021) + manifest ↔ classe.
4. Rename de ressource (collisions référentiel).

## Tests de non-régression et critères de sortie

- Banc usages vert (rappel 1,000, p95 < 100 ms) sur corpus étendu ; P7 = 0
  violation ; navigation ressource : 100 % cibles exactes sur cas dorés ; zéro
  régression des fiches N4/N5.

## Risques

- **R1 — Volume de réponses multi-fichiers sur gros projets** : ordre + plafond
  honnête documenté (jamais de troncature silencieuse — annoncer le total).
- **R2 — Client non prêt** : la liste reste exploitable à plat ; le groupement est
  une métadonnée.
- **R3 — Rename de ressource XML multi-qualifiers** : collisions vérifiées sur tous
  les fichiers du référentiel (matière ADR 0020).
