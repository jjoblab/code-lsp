# GABARIT DE RÉSULTATS — benchmark sur appareil (à remplir par le propriétaire)

> Étude « moteur Java PSI » — Phase 2, §6.c du prompt de mission. Ce gabarit est la procédure de retour des mesures **sur appareil** : le propriétaire exécute `banc-appareil.sh` (Termux ou AndroidIDE), reporte les valeurs ci-dessous et joint le fichier au ticket d'étude. **Aucune valeur de ce document n'est pré-remplie** (R5 : jamais prétendre une mesure appareil sans résultats réels).

## 1. Environnement déclaré (à remplir)

| Champ | Valeur |
|---|---|
| Appareil (modèle) | ________ |
| SoC / RAM totale | ________ |
| Version d'Android (API) | ________ |
| Runtime d'exécution (Termux openjdk-__ / AndroidIDE / dalvikvm…) | ________ |
| Mode (chargeur branché ? économie d'énergie ? autres apps) | ________ |
| Date et heure de la passe | ________ |
| `resultats-appareil.txt` brut joint ? | oui / non |

## 2. Mesures V1 — java-syntax seul (copier depuis la sortie)

| Mesure | Valeur appareil | (limite basse JVM bureau, pour mémoire) |
|---|---|---|
| Démarrage à froid (1er parse) | ________ ms | 99–179 ms |
| Parse p50 / p95 (Fichier500) | ________ / ________ ms | 0,65 / 3,5 ms |
| Arbre retenu ≈ | ________ Ko | 82 Ko |
| Tas au repos | ________ Mo | 10 Mo |

## 3. Mesures V2 — PSI headless 232.4652

| Mesure | Valeur appareil | (limite basse JVM bureau) |
|---|---|---|
| Environnement app + projet (froid) | ________ ms | 205 + 48 ms ( pile minimale ) |
| Parse PSI p50 / p95 | ________ / ________ ms | 4,8 / 27 ms |
| findClass froid / p50 | ________ / ________ ms | 96 / 0,0 ms |
| resolve() ref p50 / p95 | ________ / ________ ms | 0,6 / 39 ms |
| Tas (env + parse) | ________ Mo | 6 Mo |
| **La pile démarre-t-elle sur ART ? (shims requis : lesquels)** | oui / non — ________ | n/a (JVM) |

## 4. Mesures V3 — référence ecj 3.40.0 (non patché)

| Mesure | Valeur appareil | (limite basse JVM bureau) |
|---|---|---|
| Démarrage à froid (1er diet parse) | ________ ms | 238 ms |
| Parse diet p50 / p95 | ________ / ________ ms | 0,32 / 1,1 ms |
| Parse complet p50 / p95 | ________ / ________ ms | 0,92 / 2,0 ms |
| Compile Essai (env réutilisé) p50 / p95 | ________ / ________ ms | 5,7 / 30 ms |
| Tas au repos | ________ Mo | 6 Mo |

## 5. Compléments demandés (si possible sur l'appareil)

1. **GC ART** : comportement des `SoftReference` — exécuter ~1 min de parse en boucle et observer la mémoire (`dumpsys meminfo <pid>`) : les arbres sont-ils évacués ? ________
2. **Répétition** : 3 passes du script complet, reporter min/médiane/max des p95 : ________
3. **Thermique** : la passe 3 est-elle plus lente que la passe 1 ( % d'écart ) ? ________
4. **Termux vs app réelle** : si possible, exécuter le même banc embarqué dans une app de test ( instrumentation ) et comparer : ________

## 6. Rappels d'interprétation (non modifiables)

- Les valeurs JVM du rapport `02-spike-mesures.md` sont des **limites basses** ; le facteur ART 3–5× ( `LSP_JAVA.md` §4 ) reste une **estimation** tant que ce gabarit n'est pas rempli.
- Le banc V2 sur Termux ( JVM complète ) mesure la voie « embarquée » **sur JVM Android**, pas sur ART : pour ART il faut dexer la pile ( CI : d8 + comptage de méthodes, §6.a du prompt ) et l'exécuter en app de test — c'est la vague « spike ART » du plan par vagues.
- Aucune extrapolation automatique ne sera faite à partir de ce gabarit : les chiffres appareil seront consignés tels quels dans la suite de l'étude.
