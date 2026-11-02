# ADR 0132 — Usages et navigation à l'échelle : re-parse paresseux ou index persistant

- **Statut** : Proposé
- **Date** : 2026-10-31
- **Amende** : ADR 0114 (parité par preuve, extensions en cible)
- **Écarts** : GK-029, GK-030 (KH14)
- **Répond à** : Q-K1 (CIDX vs `.kidx` — partiellement arbitrée par la
  livraison : index dédié `.kidx`, ADR 0108)

## Contexte

`OccurrencesProuvees.kt` (1 298 l.) sert usages/rename/highlight par
**re-parse à la demande** : le scan ne garde ni texte ni arbre (budget
mémoire), pré-filtre par contenu, caches par requête, **aucune persistance**
(audit §1.14). Jamais mesuré sur un grand projet réel (E-K2). La question :
un index d'usages persistant (Q-K1) est-il nécessaire au niveau Android
Studio ? Le corpus V0 (jusqu'à 47 131 lignes/336 fichiers par projet) rend
enfin la mesure possible.

## Sources consultées

- Audit §1.14, §3.7 ; référence §A7 (index inversés côté référence, dumb
  mode).
- Q-K1 (DEMANDES-INTERFACE) ; ADR 0108 (`.kidx` : segments par empreinte,
  écriture atomique, corruption → re-décodage — le format à réutiliser).
- Mission Java : PV V-E (banc usages : rappel 1,000, p95 50,30 ms <
  100 ms sur index de mots).

## Options

| Option | Description | Critères |
|---|---|---|
| **1 — Mesurer avant de construire (recommandée)** | tenir le re-parse paresseux, mesurer P7 + gabarit 07 sur le corpus (jusqu'à ~47 000 l./module), ne construire un index que si une cible est dépassée | décision fondée sur des chiffres, pas sur une intuition ; le re-parse a fait ses preuves jusqu'ici (rappel = oracle, 0 conflit manqué) |
| **2 — Index d'usages persistant immédiat** | segments `.kidx` d'usages (clé : nom, empreinte par fichier), mise à jour incrémentale au scan | coût dex/mémoire/disque immédiat ; ne se justifie que si p95 > cible (à mesurer) |
| **3 — Fusion CIDX globale (Q-K1 c)** | un seul index pour tout | question d'architecture de dépôt — reste une demande d'interface, hors périmètre moteur seul |

## Critères et mesures

- Seuils déclencheurs (cibles, à valider au gabarit 07) : `references`
  p95 > 500 ms sur le plus gros module du corpus, ou mémoire du montage >
  plafond appareil, ou rappel < 1,000 ;
- P7 (rename sûr) exécuté sur les projets du corpus : le projet compile
  après rename (oracle) — c'est la gate qui décide ;
- références inter-modules : par le modèle de projet partagé (ADR 0127),
  pas par un balayage global.

## Décision proposée

**Option 1** : la V0 mesure (P7 + gabarit 07) ; l'index d'usages (option 2,
format `.kidx` étendu — pas un troisième format) n'est construit **que si**
un seuil est dépassé, avec la garde dex de l'ADR 0133. Q-K1 (fusion CIDX)
reste une demande d'interface au propriétaire.

## Conséquences

- Aucun travail d'index avant les mesures (STOP §10 respecté) ;
- le re-parse paresseux reste la référence d'exactitude (rappel = oracle).

## Plan de migration progressive

V0 (mesures) → V-I (index si déclenché, mise à jour incrémentale au scan,
corruption → re-décodage, miroir 0108).

## Tests de non-régression et critères de sortie

- Rappel `references` = oracle (invariant existant) ; P7 vert sur corpus ;
  si index : mêmes résultats que le re-parse (test d'équivalence dual).

## Risques

- R1 — Un grand projet réel appareil dépasse les cibles après la mission :
  l'option 2 est prête (format 0108) ;
- R2 — Indexation coûteuse sur ART : segments paresseux + annulation
  coopérative (existante).
