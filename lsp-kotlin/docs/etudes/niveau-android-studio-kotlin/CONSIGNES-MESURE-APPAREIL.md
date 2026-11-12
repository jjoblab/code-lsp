# CONSIGNES-MESURE-APPAREIL — passe gabarit 07 ENRICHIE (V-H, ADR 0124)

> Ces consignes déroulent la passe de mesure sur appareil que l'ADR 0124
> exige AVANT tout verdict sur le mode profond K2 (option 2 « processus
> déporté ») : **(a) passe gabarit 07 enrichi sur moto g06** (ce kit),
> **(b) confirmation hôte de l'environnement déporté (Q-K7)** — les deux
> mesures, puis la décision finale du propriétaire. R5 : **aucune valeur
> simulée** — tout chiffre de cette passe vient du banc, transcript joint
> brut.

## 1. Ce que contient l'archive

| Fichier | Rôle |
|---|---|
| `banc-mesure.sh` | le point d'entrée appareil (Termux OpenJDK 17) |
| `banc-classes/` | le banc `BancMesureAppareil` (sections S/F/C/Q/M/H/K7) |
| `main-classes/` + `vendor-classes/` | le moteur `:kotlin` 1.14.0 compilé (grammaire vendorée incluse) |
| `libs/` | syntax-api, kotlin-stdlib, kotlin-metadata-jvm, lsp4j (+jsonrpc, gson), annotations, **kotlin-compiler-embeddable** (le compilateur déporté de la section H) |
| `CONSIGNES-MESURE-APPAREIL.md` | ce document |
| `kit-mesure-kotlin.sha256` | l'empreinte de l'archive (vérifier après copie) |

Le commit du moteur testé est consigné dans le journal du dépôt au moment
de l'assemblage (vérifier par le sha256 de l'archive contre le dépôt).

## 2. Conditions de passe (gabarit 07 §2 — reprendre TELLES QUELLES)

- [ ] Appareil : **moto g06, Android 15, Termux + OpenJDK 17** ;
- [ ] **Mode avion**, aucune autre application active, chargeur branché
      (ou noter le niveau de batterie — l'économie d'énergie fausse les
      passes) ;
- [ ] **3 passes espacées de ≥ 30 s** (thermique), **médiane des 3
      retenue** ; chaque passe écrit son propre transcript ;
- [ ] Le transcript brut est **joint, jamais retouché**.

```bash
# Sur l'appareil (Termux) — vérifier d'abord l'empreinte :
sha256sum kit-mesure-kotlin.zip   # doit coller à kit-mesure-kotlin.sha256

unzip -o kit-mesure-kotlin.zip -d mesure && cd mesure
bash banc-mesure.sh | tee resultats-mesure-passe1.txt
sleep 30 && bash banc-mesure.sh | tee resultats-mesure-passe2.txt
sleep 30 && banc-mesure.sh | tee resultats-mesure-passe3.txt
# (la 3e passe : bash banc-mesure.sh — omettre le « sleep » devant si copié-collé)
```

Chaque passe dure ~2-4 min (les 110 itérations échauffées de F, le géant
de 1 800 lignes, la contention C, la section H qui lance le compilateur
K2 en sous-processus DEUX fois).

## 3. Lire le transcript (les sections et leurs cases gabarit)

| Section banc | Cases gabarit 07 | Seuils (CIBLES, à valider — pas des assertions) |
|---|---|---|
| `K7` | confirmation hôte Q-K7 (ADR 0124) | l'environnement Termux/PRoot existe, `java -version` = 17 |
| `S1/S2/S3` | §4 lignes S1-S3 | démarrage à chaud < 3 s (S1) |
| `F1-F6` | §4 lignes F1-F6 | complétion/hover/signature < 100 ms p95 ; diagnostics < 300 ms (F5) |
| `C1/C2` | §4 lignes C1-C2 | facteur de contention à consigner ; C2 : jamais d'interblocage |
| `Q1/Q2` | §4 lignes Q1-Q2 | 0 réponse périmée |
| `M1/M2` | §4 lignes M1-M2 | delta de fuite à consigner |
| **`H1/H2/H3`** | **§7 (V-H — le VERDICT)** | **1er diagnostic profond < 10 s ; chaud < 2 s ; mémoire additionnelle < 300 Mo** |

**La section H est celle qui débloque le verdict V-H** (ADR 0124 §Critères
de rétention de l'option 2/3). Si `H.ÉCART` s'affiche, le jar
`kotlin-compiler-embeddable` est absent du classpath : vérifier que
`libs/` le contient (l'assemblage du kit l'inclut systématiquement).

## 4. Après la passe : remplir le gabarit et trancher

1. Recopier les **médianes des 3 passes** dans le tableau §4 du gabarit
   `07-gabarit-mesure-appareil.md` (une colonne par passe, la médiane) ;
2. Remplir la section **§7 « passe V-H »** (H1/H2/H3 médians + les lignes
   K7 du transcript — c'est la case Q-K7) ;
3. Remplir §6 « Verdict et décisions » ;
4. **Téléverser les 3 transcripts bruts** (jamais retouchés) dans le dépôt
   sous `lsp-kotlin/docs/etudes/niveau-android-studio-kotlin/mesures/` ;
5. Le verdict V-H (ADR 0124) se prend alors avec les chiffres :
   - **H1 < 10 s ET H2 < 2 s ET H3 < 300 Mo** → l'option 2 est RETENUE en
     principe : la vague « branchement du service profond aux
     diagnostics » (UX du délai, annulation, provenance `palier 3` dans
     la réponse protocole) peut être planifiée ;
   - un seuil manqué → voir la variante **démon** (option 3 : H2 est le
     délai SANS démon ; un démon résident le rapprocherait de l'amorçage
     déjà payé — le delta H1−H2 du transcript en est le potentiel
     mesuré) ;
   - deux seuils manqués ou Q-K7 négatif → **rejet motivé**, le palier 2
     reste le plafond de fidélité (ADR 0124 : « la décision finale
     appartient au propriétaire §10 »).

## 5. Précautions d'honnêteté (R5)

- Les nombres de ce banc sont des **bornes basses du perçu** (in-process,
  sans debounce ni rendu client) — le gabarit 07 §2 le rappelle : le
  perçu de bout en bout se mesure par enregistrement d'écran à 60 i/s ;
- H3 mesure le **tas du banc** : le compilateur déporté a SON propre
  processus (hors ce tas) — pour la mémoire APPAREIL totale, relever
  `/proc/meminfo` (`MemAvailable`) avant/après la section H (consigné
  dans le transcript par la sonde K7 au début de la passe : refaire la
  lecture entre les passes si le chiffre mémoire est décisif) ;
- le facteur ≈ 4× observé côté Java JVM→ART ne se projette PAS sur le
  Kotlin (gabarit 07 §5, R5) : les seuils se lisent sur les chiffres
  appareil, tels quels.
