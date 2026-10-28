# GABARIT DE RÉSULTATS — benchmark sur appareil (rempli : passe du 2026-10-28, moto g06)

> Étude « moteur Java PSI » — Phase 2, §6.c du prompt de mission. Ce gabarit est la procédure de retour des mesures **sur appareil** : le propriétaire exécute `banc-appareil.sh` (Termux ou AndroidIDE), reporte les valeurs ci-dessous et joint le fichier au ticket d'étude. **Aucune valeur de ce document n'a été pré-remplie avant exécution** (R5 : jamais prétendre une mesure appareil sans résultats réels).
>
> **Passe reportée** : 2026-10-28 14:33:48 (heure appareil) — sortie brute transmise par le propriétaire, reportée ci-dessous **telle quelle** ; transcript intégral joint : [`resultats-appareil.txt`](resultats-appareil.txt). Une passe antérieure à 14:27:28 sur le même appareil avait échoué au déploiement (jars absents du classpath : `ClassNotFoundException`) ; ne produisant aucune valeur de mesure, elle n'est pas comptée comme passe.

## 1. Environnement déclaré (à remplir)

| Champ | Valeur |
|---|---|
| Appareil (modèle) | moto g06 ( bannière du banc : `aarch64 / 15 / moto g06` ) |
| SoC / RAM totale | non déclaré |
| Version d'Android (API) | Android 15 ( niveau API non déclaré ) |
| Runtime d'exécution (Termux openjdk-__ / AndroidIDE / dalvikvm…) | OpenJDK 17.0.19 ( 2026-04-21 ), aarch64 — origine exacte non déclarée ( la procédure vise Termux ) |
| Mode (chargeur branché ? économie d'énergie ? autres apps) | non déclaré |
| Date et heure de la passe | 2026-10-28 14:33:48 ( heure locale de l'appareil, fuseau non déclaré ) |
| `resultats-appareil.txt` brut joint ? | **oui** — `spike/appareil/resultats-appareil.txt` ( transcript exact de la passe ) |

Notes de passe ( depuis le transcript ) :
- **ScanArt** ( échantillon V1, `java-syntax-jvm-0.3.381.jar` seul ) : 95 classes, 777 méthodes déclarées, **0 motif sensible** — cohérent avec le scan de bureau ( `resultats/scan-art-v1.txt` : même jar → 95 classes / 777 méthodes / 0 motif ; le total 411 / 2 965 du bureau porte sur la pile V1 complète de 4 jars ).
- **Corpus** régénéré sur l'appareil ( déterministe, graine 42 ) : `Fichier500.java` — 498 lignes / 16 258 caractères / 26 méthodes, 5 653 jetons, 1 026 marqueurs d'arbre ( dont 26 collapses ) — identique au corpus de bureau.

## 2. Mesures V1 — java-syntax seul (copier depuis la sortie)

| Mesure | Valeur appareil | (limite basse JVM bureau, pour mémoire) |
|---|---|---|
| Démarrage à froid (1er parse) | **1393,342231 ms** | 99–179 ms |
| Parse p50 / p95 (Fichier500) | **6,548 / 13,44 ms** | 0,65 / 3,5 ms |
| Arbre retenu ≈ | **82 Ko** | 82 Ko |
| Tas au repos | **9 Mo** | 10 Mo |

Notes brutes : itérations 200 ( échauffement 50 ) ; parse min / max / moyen : 4,506 / 34,92 / 7,532 ms ; arbre = delta de tas, 100 arbres retenus après double GC.

## 3. Mesures V2 — PSI headless 232.4652

| Mesure | Valeur appareil | (limite basse JVM bureau) |
|---|---|---|
| Environnement app + projet (froid) | **2938,135154 + 902,127616 ms** | 205 + 48 ms ( pile minimale ) |
| Parse PSI p50 / p95 | **62,824 / 96,495 ms** | 4,8 / 27 ms |
| findClass froid / p50 | **981,681231 / 0,003 ms** | 96 / 0,0 ms |
| resolve() ref p50 / p95 | **6,518 / 879,736 ms** | 0,6 / 39 ms |
| Tas (env + parse) | **6 Mo** | 6 Mo |
| **La pile démarre-t-elle sur ART ? (shims requis : lesquels)** | **non testé dans cette passe** — le banc a tourné sur JVM ( OpenJDK 17 ) ; cf. §6 | n/a (JVM) |

Notes brutes : taxe d'assemblage 2 EP application + 1 EP projet enregistrés à la main ( psiAugmentProvider, javaModuleSystem, java.elementFinder ) ; jar classpath guava-32.0.1-jre.jar ( 2 Mo ) ; parse min / max : 51,458 / 135,881 ms ; findClass p95 : 0,078 ms ; première classe : Fichier500 ( 26 méthodes ).

## 4. Mesures V3 — référence ecj 3.40.0 (non patché)

| Mesure | Valeur appareil | (limite basse JVM bureau) |
|---|---|---|
| Démarrage à froid (1er diet parse) | **2030,760308 ms** | 238 ms |
| Parse diet p50 / p95 | **5,793 / 10,455 ms** | 0,32 / 1,1 ms |
| Parse complet p50 / p95 | **9,527 / 17,697 ms** | 0,92 / 2,0 ms |
| Compile Essai (env réutilisé) p50 / p95 | **30,922 / 40,182 ms** | 5,7 / 30 ms |
| Tas au repos | **5 Mo** | 6 Mo |

Notes brutes : itérations 200 ( échauffement 50+50 ) ; types parsés ( diet ) : 1 ; compile Essai à classpath neuf p50 / p95 : 50,185 / 70,156 ms ; compile Essai froide : 2269,031077 ms ( 1 unité, 0 erreur ).

## 5. Compléments demandés (si possible sur l'appareil)

1. **GC ART** : comportement des `SoftReference` — exécuter ~1 min de parse en boucle et observer la mémoire (`dumpsys meminfo <pid>`) : les arbres sont-ils évacués ? **non réalisé dans cette passe** ( banc exécuté sur JVM, pas sur ART )
2. **Répétition** : 3 passes du script complet, reporter min/médiane/max des p95 : **non réalisé** ( passe unique )
3. **Thermique** : la passe 3 est-elle plus lente que la passe 1 ( % d'écart ) ? **non réalisé** ( nécessite les 3 passes du point 2 )
4. **Termux vs app réelle** : si possible, exécuter le même banc embarqué dans une app de test ( instrumentation ) et comparer : **non réalisé**

## 6. Rappels d'interprétation (non modifiables)

- Les valeurs JVM du rapport `02-spike-mesures.md` sont des **limites basses** ; le facteur ART 3–5× ( `LSP_JAVA.md` §4 ) reste une **estimation** tant que ce gabarit n'est pas rempli.
- Le banc V2 sur Termux ( JVM complète ) mesure la voie « embarquée » **sur JVM Android**, pas sur ART : pour ART il faut dexer la pile ( CI : d8 + comptage de méthodes, §6.a du prompt ) et l'exécuter en app de test — c'est la vague « spike ART » du plan par vagues.
- Aucune extrapolation automatique ne sera faite à partir de ce gabarit : les chiffres appareil seront consignés tels quels dans la suite de l'étude.
