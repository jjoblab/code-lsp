# :testing — suite d'intégration moteur ↔ index disque (étage N1)

> **Kotlin 100 %, clean-room** (AGENTS.md, ADR 0001) : ce module est une
> RÉÉCRITURE NEUVE — l'ancienne suite de `codeide-lsp:lsp/testing` (4
> fichiers Java, couplés aux membres package-private du moteur vendored
> `jo.lspjava.*`) est **mutée** faute de reprise possible ; décision
> consignée au manifeste (`docs/PROVENANCE-REPRISE.md` §3/§6).

Suite d'intégration du **branchement de l'index disque CIDX** sur le
moteur Java réécrit (`:java`, étage N1 de `docs/PLAN-REECRITURE.md`) :
le moteur consomme l'index à travers le **port SPI `IndexNoms`**
(`jo.codeide.lsp.java.IndexNoms`) dont l'implémentation de production
sera reprise par le futur hôte `:lsp:host`.

## Pourquoi un module dédié

Même motif que l'ancien dépôt (v0.96.3) : le moteur ne dépend d'AUCUN
module interne (`compileOnly` sur le jar ecj patché — extractibilité
bibliothèque indépendante) ; la suite vit AU-DESSUS des deux modules
qu'elle éprouve, sur le modèle du futur hôte.

## Contenu

- `AdaptateurIndexNoms` — adaptateur du port `IndexNoms` sur l'index réel
  (`ServiceIndexDisque` + `VueIndex`) : surveillance de la transition
  CONSTRUCTION → PRÊT par collecte du flux d'état, exécution des
  observateurs posés avant/après la transition, traduction des requêtes
  du port en requêtes de fusion LSM ;
- `IntegrationMoteurIndexTest` — 4 tests sur jar fabriqué de classes
  compilées-équivalentes (zéro dépendance à android.jar, au cache Gradle
  ou à javac — la suite tourne partout, CI comprise) :
  1. **parité stricte** des diagnostics avec et sans index attaché ;
  2. **requêtes du port à PRÊT** contre le vrai CIDX (localisateur, noms
     par préfixe, FQCN de nom exact, paquets, types) + transition observée
     (notifiée exactement une fois ; observateur tardif exécuté immédiatement) ;
  3. **filtre `$` en aval du moteur** contre l'index réel qui rend les
     imbriquées brutes (fiche 032 — invariant conservé même contre un
     index imparfait) ;
  4. **réouverture chaude** : deuxième session sur le même stockage —
     segments relus, mêmes verdicts, pas plus lente que le froid.

## Construction

```bash
./gradlew :testing:test    # 4 tests d'intégration (JDK 21, JVM 17)
```
