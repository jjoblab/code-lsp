# 00 — Synthèse et décision (Phase 4)

> Étude « moteur Java PSI » pour CodeIDE sur Android/ART — synthèse finale de la mission (§8 du prompt).
> Documents de base : `01-architecture-psi.md` (étude D1–D12 + annexes), `02-spike-mesures.md` (mesures V1/V2/V3 + compatibilité ART), `00b-inventaire-reprise.md` + `docs/PROVENANCE-REPRISE.md` (Phase 0), `docs/adr/0001–0018` (décisions proposées).
> **La mission s'arrête ici** : aucune implémentation n'est démarrée ; tout attend la décision du propriétaire (§8 STOP).

## 1. Recommandation unique

### **GO PARTIEL — stratégie hybride « s'inspirer »** : conserver le socle ecj/CIDX, greffer les protocoles et designs IntelliJ, réutiliser un seul artefact IntelliJ (`java-syntax`)

**Raisonnement (en cinq temps, chacun prouvé dans les rapports) :**

1. **Embarquer la plateforme IntelliJ est disqualifié par les chiffres.** La pile PSI headless minimale (232.4652) pèse **37,4 Mo / 124 513 méthodes déclarées** (75,4 Mo / 168 448 en pile complète) contre un budget APK raisonnable de ~5 Mo ; elle expose **108 motifs sensibles ART** (swing, awt, `sun.misc.Unsafe`, `java.lang.management`, jna) exigeant une centaine de shims dont certains usages (`Unsafe`, `MethodHandles.Lookup`) sont invérifiables statiquement ; elle ne démarre même pas sur JVM sans une **taxe d'assemblage** documentée (3 EP manuels + adaptateur + home factice, `02-spike-mesures.md` §4.1) ; et son parse est ~5–7× plus lent qu'ecj sur les mêmes corpus (§8). En prime, l'index — la vraie puissance de la plateforme (D7) — ne vient pas avec (ses deux piles de persistance sont incompatibles ART), et les diagnostics rouges d'IntelliJ sont une réimplémentation de 18 130 lignes, pas un compilateur (D9).
2. **Le statu quo détient déjà ce que PSI ne peut pas apporter.** La fidélité compilateur (bindings ecj natifs) est l'atout différenciant (D9) ; CIDX bat le design d'index IntelliJ sur chaque axe mobile mesurable : préfixe par recherche binaire (p50/p95 0,16–0,76/1,1–7,1 ms) là où IntelliJ **énumère toutes les clés** ( `AllClassesGetter.java:193-204` — fait critique contre-intuitif, D7), +1 dépendance = seul le jar nouveau (85/89/87 ms), budget octets 4,2–29,7 Mo contre 600 Mo de page cache, corruption = segment seul contre rebuild global.
3. **La qualité perçue « IDE complet » réside dans cinq mécanismes autour du PSI, pas dans le PSI** (conclusion obligatoire §5 de `01-architecture-psi.md`) : index multi-usage + forward index, usage search indexée, ~660 inspections, classement de complétion, modèle concurrence/mémoire. Aucun n'est portable en code ; **tous sont repris comme designs** dans les ADR 0008/0012/0013/0014/0010/0011.
4. **Le seul artefact qui passe les critères mobiles tel quel est `java-syntax`** (V1 du spike) : 798 Ko, 2 965 méthodes, **0 référence sensible ART**, parse p50 0,65 ms (498 lignes), publié et autonome (D3). Il entre comme moteur de l'incrémentalité (ADR 0006).
5. **Les gains actionnables à court terme sont identifiés et bornés** : classement de complétion (weighers + gel + trim + frontière RPC — le plus gros levier de qualité perçue, D8), CodeAction v2 sur modèle ModCommand (D9), rename sûr par phases + usage search indexé sur CIDX (D10), verrous par périmètre + époque (D11), discipline mémoire (D12). Chacun = code neuf Kotlin borné, derrière drapeau, avec banc de non-régression (ADR 0016).

**Ce que le GO PARTIEL n'achète pas** (assumé) : la résolution « native PSI » multi-fichiers sans ecj, la navigation dans les sources décompilées, l'écosystème des 660 inspections IntelliJ (hors réécriture depuis spécification avec décision propriétaire), le support du langage Java 25 (ecj 3.40 = Java 21 — montée en vague optionnelle, ADR 0018).

## 2. Tableau comparatif des options (chiffres mesurés)

| Option | Parse 500 l. p50/p95 | Résolution | Empreinte | Sensibilité ART | Verdict |
|---|---|---|---|---|---|
| **A — Embarquer PSI** (pile V2, spike) | 4,75–4,89 / 27–29 ms | findClass 96–141 ms froid / resolve p50 0,6–1,5 ms | **37,4 Mo min. / 124 513 méth. déclarées** | **108 motifs** + taxe d'assemblage ( 3 EP + home ) | **Rejetée** ( `02-spike` §4/§6/§7 ) |
| **B — Hybride s'inspirer** (recommandée) | ecj : diet p50 0,32–1,3 ms / complet 0,6–1,5 ms ; java-syntax p50 0,65 ms en appoint | ecj bindings ( compile résolue snippet 3,7–5,7 ms p50 env chaud ) | ecj 3,2 Mo + java-syntax 0,8 Mo + CIDX ( budget 4,2–29,7 Mo/100 jars ) | ecj 6 motifs dont 4 shims connus ; java-syntax **0** | **GO PARTIEL** |
| **C — Combinaison double moteur** | cumul A+B | cumul | **> 40 Mo** | cumul ( 108+6 motifs ) | Rejetée ( coûts cumulés, gain couvert par B ) |
| **D — Statu quo amélioré** | = B ( même moteur ) | = B | = B moins 0,8 Mo ( sans java-syntax ) | = B | **Repli permanent** de B ( drapeaux off ) ; abandonne les gains D8/D9/D10/D11/D12 |

Détail complet (mesures brutes, commandes, incertitudes) : `02-spike-mesures.md` §3–§8. Rappel : tout est **limite basse JVM** ; facteur ART 3–5× estimé.

## 3. Les 10 risques principaux et leurs mitigations

| # | Risque | Gravité | Mitigation ( renvoi ) |
|---|---|---|---|
| 1 | **Contestation de provenance du moteur repris** ( niveau B ADR 0110 ) | Haute | Décision écrite propriétaire ADR 0001 + attestations datées conservées + registre `PROVENANCE-REPRISE.md` + CI anti-fichier-hors-registre |
| 2 | **Chaîne licences incomplète** ( protocole tooling sans LICENSE ; conditions artefacts JetBrains à confirmer ) | Haute | Actions propriétaire tracées ( ADR 0017 ) ; `NOTICE` + registre dès la vague 0 ; réécriture des ~10 types en repli |
| 3 | **Sous-estimation de l'effort des greffes Kotlin** ( classement, ModCommand, rename v2 ) | Haute | Vagues bornées à une capacité mesurable ; banc ADR 0016 avec baseline signée avant chaque vague ; repli D permanent |
| 4 | **Régression de qualité perçue pendant la migration** | Haute | Double moteur derrière drapeau ( 2 versions de rodage avant retrait ) ; CI bloquante sur recall/top-1/MRR/fidélité |
| 5 | **Dérive du banc de qualité** ( sur-apprentissage, bruit ) | Moyenne | Corpus versionné, split train/éval, seuils intégrant l'incertitude mesurée ( spike §9 ) |
| 6 | **Interblocage après passage aux verrous par périmètre** | Moyenne | Ordre global fichier→module→index documenté + tests de chaos CI ( ADR 0010 ) |
| 7 | **GC ART incompatible avec les soft-références** ( éviction trop tôt/tard ) | Moyenne | Mesure sur appareil ( gabarit spike ) ; repli bornes dures en octets ( ADR 0011 ) |
| 8 | **Montée JDT silencieuse** ( API JDK nouvelles, réflexive ) | Moyenne | Scanner CI = liste de shims versionnée + tests de fumée appareil à chaque montée ( ADR 0018/0005 ) |
| 9 | **Disparition/déplacement des dépôts d'artefacts JetBrains** | Basse | Versions épinglées + miroir CI ( ADR 0005 ) |
| 10 | **Besoin Java 25 non couvert** ( ecj 3.40 = Java 21 ) | Basse | Procédure de montée bornée prête ( ADR 0018 ), déclenchée par besoin démontré ( matrice de capacités ) |

## 4. Plan par vagues

> Fourchettes d'effort en **jours-homme** ( estimation, méthode : comparaison aux tailles de code mesurées dans les rapports — ex. noyau classement ≈ 10 f / 1 500 l. — et à l'historique de l'ancien dépôt ; à affiner par le propriétaire ). Chaque vague : périmètre, critères de sortie mesurables, dépendances. **Le prompt d'exécution de chaque vague sera produit séparément, après décision du propriétaire** (§8 : « le prompt d'exécution à produire ensuite (séparément) »).

| Vague | Périmètre | Critères de sortie | Dépend de | Effort ( est. ) |
|---|---|---|---|---|
| **0 — Amorçage du nouveau dépôt** | Squelette Gradle, `LICENSE`/`NOTICE`, CI ( build + scan ART + dexage + seuils + bancs empreinte/latence avec baseline initiale ), publication d'artefacts vides, branchement substitution composite dans CodeIDE ( DEV ), actions propriétaire ( LICENSE tooling ; conditions artefacts JetBrains ) | Build CodeIDE vert avec l'artefact neuf ; baseline empreinte signée | ADR 0001 accepté | **5–10 j-h** |
| **1 — Reprise contrôlée + consolidation** | Reprise `index`→`model`→`testing`→moteur ( manifeste ) ; patcheur ecj-art réécrit en Kotlin piloté par liste de shims ; verrous par périmètre ( schéma B ) ; budgets octets partout ; `onTrimMemory` | 449 comportements verts ( spec extraite ) ; comptage synchronized < 6 ; tas ≤ budget par palier ; zéro fichier hors registre | V0 | **15–30 j-h** |
| **2 — Classement de complétion** | Chaîne weighers + `WeighingComparable` + gel + liftShorter + trim honnête ; banc qualité ( recall/top-1/MRR ) ; statistics locales opt-in | Recall ≥ baseline ; top-1/MRR +5 pts ; p95 ≤ baseline ; zéro « saut » de liste | V1 | **15–25 j-h** |
| **3 — CodeAction v2 ( ModCommand ) + usages indexés** | `CodeActionService` v2 déclaratif sérialisable ; migration des fixes par cas dorés ; QueryExecutor sur CIDX ( `references`/`documentHighlight` exhaustifs ) | Parité quick-fixes 100 % ( cas dorés ) ; rappel usages = oracle ; `Files.walk` retiré du chemin chaud ; p95 usages < 100 ms | V1, ( V2 utile ) | **20–35 j-h** |
| **4 — Rename v2 + époque + frontière RPC** | Rename par phases ( collisions typées + réparations + preview ) ; époque copy-on-write ( schéma C ) ; protocole riche de complétion ( capability `x-codeide.completionRanking` ) | 0 conflit manqué ( oracle ) ; 0 réponse périmée ( époque ) ; re-filtrage local actif côté client | V2, V3 | **20–30 j-h** |
| **5 — Parsing incrémental ( java-syntax )** *( conditionnée : conditions artefacts JetBrains confirmées )* | Validation d'accolades pré-ecj ; plage commune → garde de re-résolution ; éventuel arbre léger | > 50 % de re-résolutions évitées sur corpus de frappe ; diagnostics identiques au re-parse complet ; APK +0,8 Mo vérifié | V0, ADR 0017 action (b) | **15–25 j-h** |
| **6 ( optionnelle ) — Montée JDT ≥ 3.43** | Procédure ADR 0018 ( re-scan, shims, fidélité, appareil ) | Critères ADR 0018 | Déclenchement par besoin | **10–15 j-h** |

**Total GO PARTIEL ( vagues 0–4 ) : 75–130 j-h** ( estimation à affiner par vague avant lancement ; la vague 5 et 6 s'ajoutent selon conditions/déclencheurs ).

## 5. Ce qui reste à mesurer sur appareil (rien n'est prétendu sans retour réel)

1. **Latences V1/V2/V3 sur ART** ( parse, résolution, frappe ) — point d'entrée : `spike/appareil/banc-appareil.sh` + gabarit `GABARIT-RESULTATS.md` ; le facteur 3–5× reste une estimation tant que le gabarit n'est pas rempli.
2. **GC ART** : éviction des `SoftReference`, coût des `ReferenceQueue` ( D12/ADR 0011 ) — protocole au gabarit §5.
3. **Dexage réel** ( d8 en CI, conformément au prompt §6.a ) : méthodes dex et taille d'APK des piles candidates ( borne basse = méthodes déclarées, spike §7 ).
4. **Latences du moteur assemblé** avec les corpus 20/73/250 jars de `LSP_JAVA.md` §4, avant/après chaque vague ( banc ADR 0016 + appareil ).
5. **Thermique et répétition** ( 3 passes min/médiane/max ) — gabarit §5.
6. **Conditions d'utilisation des artefacts binaires JetBrains** ( intellij-dependencies ) — action propriétaire ( ADR 0017 ) avant la vague 5.

## 6. Décision demandée au propriétaire

1. **Trancher le GO PARTIEL** ( ou corriger le cap : D pur, ou A sur preuves nouvelles ) — le présent document + `02-spike-mesures.md` en portent la démonstration.
2. **Accepter l'ADR 0001** ( licence + levée d'exclusion du moteur ) — verrou de toutes les reprises.
3. **Confirmer le périmètre** ( ADR 0002, proposition §5 du `00b-inventaire-reprise.md` ).
4. **Exécuter les 2 actions licence** ( `codeide-tooling` ; conditions artefacts JetBrains ).
5. À réception : les **prompts d'exécution vague par vague** seront produits séparément (§8 du prompt), en commençant par la vague 0.
