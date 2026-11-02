# ADR 0003 — Index de l'étude : ordre de lecture et dépendances entre ADR

- **Statut** : Proposé
- **Date** : 2026-10-28

## Contexte

Le nouveau dépôt démarre avec sa propre numérotation (0001+) ; les ADR sont **ordonnés par dépendance** (§7 du prompt) et renvoient à trois rapports d'étude et deux registres :

- `docs/etudes/moteur-java-psi/01-architecture-psi.md` — étude des domaines D1–D12 (+ annexes `annexes/exp-{D7D8,D9D10,D11D12}.md`) ;
- `docs/etudes/moteur-java-psi/02-spike-mesures.md` — mesures V1/V2/V3 et compatibilité ART ;
- `docs/etudes/moteur-java-psi/00b-inventaire-reprise.md` — inventaire Phase 0 ;
- `docs/PROVENANCE-REPRISE.md` — manifeste de reprise (R9) ;
- `docs/etudes/moteur-java-psi/00-synthese.md` — recommandation go/no-go et plan par vagues.

## Sources consultées

- Prompt de mission §7 (numérotation, format des ADR) ; R7 (statut « Proposé »).
- Ancien dépôt `codeide-lsp` @ `cb809e14` : convention de numérotation du README (leçons : 120 ADR hérités gelés + 27 propres ; le nouveau dépôt repart à 0001 pour éviter toute collision).

## Ordre de lecture et graphe de dépendance

```
0001 Licence & règles de reprise  ─────────── VERROU : aucune reprise avant décision
  └─> 0002 Périmètre & modules ──> 0003 (présent ADR, index)
        └─> 0004 Stratégie globale ────────── synthèse D1–D12 + spike
              ├─> 0005 Packaging & compat. ART ── spike §6–§7, D1/D7
              ├─> 0006 Parsing & incrémentalité ── D3 + spike V1
              ├─> 0007 Résolution & inférence ─── D5 + spike V2/V3
              ├─> 0008 Index (stubs/disque) ──── D7 + ADR 0111 ancien
              ├─> 0009 Bytecode/AAR/android.jar ─ D6
              ├─> 0010 Concurrence & annulation ─ D11
              ├─> 0011 Mémoire & cycle de vie ─── D12
              ├─> 0012 Complétion & classement ── D8
              ├─> 0013 Diagnostics & inspections ─ D9 + fidélité ecj
              ├─> 0014 Navigation/refactorings ── D10
              └─> 0015 Intégration LSP progressive ── matrice de capacités
                    ├─> 0016 Banc de qualité & non-régression
                    └─> 0017 Licences & provenance de la solution ── ADR 0001 + artefacts
  (transversal) 0018 Montée JDT ≥ 3.43 & shims ── ADR 0005 + spike §6
```

Règles de lecture :

1. **0001 verrouille tout** : aucune reprise (copie) n'a lieu tant qu'il n'est pas accepté ; les ADR 0004+ peuvent être lus mais pas exécutés côté code repris.
2. Les ADR 0004–0014 se lisent après les rapports D-domaines correspondants (renvois systématiques dans « Critères et mesures »).
3. 0015/0016/0017 concluent (intégration, qualité, licences) ; 0018 est transversal à la voie ecj (toutes les stratégies sauf « embarquer tout PSI » le conservent).

## Décision proposée

Adopter le présent ordre comme table des matières officielle du dossier `docs/adr/` ; tout nouvel ADR (≥ 0019) s'insère au graphe avec ses dépendances déclarées dans son en-tête.

## Conséquences

- Le dossier reste navigable au fil des vagues d'implémentation (chaque vague référence ses ADR).
- Les renvois inter-ADR utilisent la numérotation du **nouveau** dépôt ; les renvois vers l'ancien dépôt portent toujours le préfixe « ancien » (ex. « ADR 0111 ancien ») pour éviter toute ambiguïté.

## Risques

- Faible (ADR méta) : veiller à mettre à jour le graphe à chaque ajout.
