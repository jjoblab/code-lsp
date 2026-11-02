# CHANGELOG — moteur Kotlin LSP (`lspkotlin`)

> Le pack langue est publié sur GitHub Packages à partir de la 1.13.0
> (coordonnées `jo.codeide.lsp.kotlin:jo-codeide-lsp-kotlin`, Q-K4 tranché le
> 03/11/2026 — mécanisme miroir `:java`). Les versions antérieures sont
> reconstituées depuis les journaux d'étages
> (`lsp-kotlin/docs/etudes/moteur-kotlin/`) et l'en-tête de version du module.
> Format : **Ajouté / Modifié / Corrigé**.

## 1.13.0 — version unifiée par la fusion + PREMIÈRE PUBLICATION (03/11/2026, PUBLIÉE v1.13.0)

- **Modifié** : la version du dépôt est unifiée à **1.13.0** par la fusion
  des branches d'étude (la version vient du projet racine) — seul
  `version.properties` change dans le jar (991 021 → 991 023 o, +2 o,
  385/386 entrées identiques — preuve par CRC, mesure du 03/11/2026) ;
  aucun `1.0.0` codé en dur dans les tests, `minApp` du descripteur de
  pack indépendant.
- **Ajouté** : **publication Maven du pack langue** (Q-K4 — ADR 0136,
  GK-028) — coordonnées `jo.codeide.lsp.kotlin:jo-codeide-lsp-kotlin`,
  dépôt `maven.pkg.github.com/jjoblab/code-lsp`, déclencheur tag `v*`
  partagé avec le moteur Java (workflow `publish.yml`) ; l'artefact
  embarque code neuf + grammaire vendorée (notices
  `META-INF/vendor-kotlin`) + descripteur D6 généré (`id=jo-codeide-lsp-kotlin`,
  `version=1.13.0`, `minApp=1.0.0`, `grammaire=v2.3.0` — vérifié dans le
  jar `publishToMavenLocal` du 03/11/2026) + jar sources (descripteur
  exclu) ; POM : syntax-api 0.3.340, kotlin-metadata-jvm 2.2.10 et
  lsp4j 0.21.2 en scope runtime (le câble 0.22.0 reste porté par l'hôte).

## 1.0.0 — programme des étages 0–6 + transverse T (30–31/10/2026, NON PUBLIÉE)

Clos **sur preuve mécanique** le 31/10/2026 (CI verte 10/10, run 41165869078 :
cahier **469/469** comportements + 12 cas dorés, bancs signés, dex d8
26 590 méthodes / 4,10 Mo sous seuils).

- **Ajouté — étage 0 (amorçage, 30/10)** : module `:kotlin`, grammaire
  officielle vendorée v2.3.0 (ré-obtention amont), suite de parité +
  baseline, CI dex/scan/seuils, gabarit appareil.
- **Ajouté — étage 1 (30/10)** : façade `Kt*` (fiche 013) ; session
  documents (fiches 049/042 — SPI `api/`) ; scan projet paresseux (fiche
  041) — suite 72 tests verts.
- **Ajouté — étage 2 (31/10)** : classpath et index (fiches 002–005,
  008/009, 034) — reprises contrôlées R4/R5, index dédié
  `kotlin-appelables/*.kidx`.
- **Ajouté — étage 3 (31/10)** : complétion et classement avec banc de
  qualité à **baseline signée** (étage 3).
- **Ajouté — étage 4 (31/10)** : confort (formatage, imports, pliage —
  reprises R6/R7/R8), diagnostics `kt.*`.
- **Ajouté — étage 5 (31/10)** : renommage sûr / WorkspaceEdit — 5 cas dorés
  (41/41 comportements confort, E-K8 sous seuils : 26 040 méthodes /
  4,00 Mo).
- **Ajouté — étage 6 (31/10)** : **serveur LSP4J + protocole riche** —
  cycle de vie cinq états, époque copy-on-write (0 réponse périmée),
  **matrice paritaire** (19 annoncées / 7 absentes honnêtes), convertisseur
  UTF-16, publicateur débouncé 50 ms, descripteur de pack (fiche 001) ;
  38 comportements + 7 cas dorés protocole (suite 522 tests).
- **Ajouté — transverse T (31/10)** : fiche 021 (mesures perf), 12 cas
  dorés, digeste de parité re-signé au fil des vagues (401 → 411 → 432
  fichiers).

## Étude post-clôture « niveau Android Studio » (31/10/2026 — diff DOCUMENT SEUL)

- **Ajouté (documentation)** : audit du moteur (KH1–KH16 confirmés avec
  preuves), référence Android Studio, matrice d'écarts GK-001…GK-038
  priorisée P0–P3, plan par vagues V-A…V-I (112–192 j-h [estimé]), ADR
  0131–0136 (toutes Proposées — amendements datés aux 8 ADR impactées),
  synthèse et recommandation (paliers 1/2/3 chiffrés, décisions demandées).
- Décisions propriétaire reçues le 31/10 : P-K1 (audit similarité GPL) et
  P-K2 (conditions des artefacts binaires) **levées** — le dernier
  bloqueur de la publication du pack est levé (Q-K4 reste à exécuter).

---

## Tailles de l'artefact (mesurées)

| Date / état | Jar du moteur | Source |
|---|---:|---|
| Clôture étage 5 (31/10) | 892 147 o | journal étage 6 §9.3 |
| Clôture étage 6 (31/10) | 990 859 o | journal étage 6 §9.3 |
| Tête d'étude (8856b95, 31/10) | 991 021 o (`kotlin-1.0.0.jar`) | build local 03/11/2026 |
| Tête fusionnée 1.13.0 (03/11) | **991 023 o** (`kotlin-1.13.0.jar`) | build local 03/11/2026 — 385/386 entrées identiques par CRC, seule `version.properties` change |

Budget dex (pile chaude, CI) : **26 590 method_ids / classes.dex
4 294 352 o (4,10 Mo)** — identique avant/après réorganisation (runs 41196064353
et 41511834656) ; câble lsp4j non compté (porté par l'hôte).
