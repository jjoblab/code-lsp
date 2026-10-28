# Fiche 038 — RawZipReaderTest

> **Source** : `codeide-lsp` @ `cb809e14` — `lsp/java/src/test/java/jo/lspjava/ecj/RawZipReaderTest.java`.
> **Domaine** : index-classpath
> **Résumé** : Tests du lecteur ZIP minimaliste `RawZipReader`, chaque propriété étant vérifiée PAR RAPPORT à `java.util.zip.ZipFile` (vérité terrain JDK). Corpus : un zip de synthèse couvrant les cas réels des jars — entrée DEFLATED (`com/example/Alpha.class`), entrée imbriquée avec `$` (`com/example/R$string.class`, cas AndroidX), entrée STORED (`META-INF/MANIFEST.MF` avec CRC/taille), nom d'entrée UTF-8 accentué (`déjà/ClassÉ.class`) et grande entrée compressée au maximum (`big/Data.class`, 100 000 octets de motif périodique).

## Comportements

### scanNamesMatchesZipFileGroundTruth — inventaire identique au JDK
- **Préconditions / entrée** : le zip de synthèse (5 entrées couvrant DEFLATED/STORED/imbriqué/UTF-8/grande entrée).
- **Comportement attendu** : `scanNames` rend exactement l'ENSEMBLE des noms d'entrées qu'énumère `ZipFile` sur le même fichier.
- **Bornes** : —
- **Statut** : actif

### readSingleDeflatedEqualsZipFile — lecture d'une entrée DEFLATED
- **Préconditions / entrée** : entrée `com/example/Alpha.class` (compression DEFLATE classique).
- **Comportement attendu** : `readSingle` rend exactement les mêmes octets que le flux de `ZipFile` pour cette entrée (comparaison byte à byte).
- **Bornes** : —
- **Statut** : actif

### readSingleStoredEqualsZipFile — lecture d'une entrée STORED
- **Préconditions / entrée** : entrée `META-INF/MANIFEST.MF` posée en méthode STORED (non compressée, CRC et taille déclarés).
- **Comportement attendu** : `readSingle` rend exactement les octets vus par `ZipFile` pour cette entrée.
- **Bornes** : —
- **Statut** : actif

### readLargeMultiBufferEntry — payload multi-tampons
- **Préconditions / entrée** : entrée `big/Data.class` (BEST_COMPRESSION) dont le contenu décompressé est le motif « x<i mod 10> » répété 50 000 fois.
- **Comportement attendu** : `readSingle` rend le contenu complet : longueur exacte, début et fin conformes au motif (l'inflation traverse plusieurs tampons).
- **Bornes** : longueur exactement 100 000 octets ; préfixe « x0x1x2 » ; suffixe « x8x9 ».
- **Statut** : actif

### utf8EntriesReadable — noms d'entrées UTF-8
- **Préconditions / entrée** : entrée nommée `déjà/ClassÉ.class` (accents), contenu « utf8 ».
- **Comportement attendu** : `scanNames` contient le nom accentué EXACT ; `readSingle` sur ce nom rend le contenu décodé « utf8 ».
- **Bornes** : —
- **Statut** : actif

### persistentHandleLifecycle — handle persistant réutilisable
- **Préconditions / entrée** : un handle ouvert sur le zip (`RawZipReader.open`).
- **Comportement attendu** : `contains` répond vrai pour une entrée existante et faux pour une absente ; deux lectures successives via le MÊME handle rendent les bons contenus ; la lecture par handle est identique à la lecture en un coup (`readSingle`).
- **Bornes** : —
- **Statut** : actif

### missingOrTruncatedFilesAreRejectedCleanly — fichiers absents/tronqués
- **Préconditions / entrée** : un chemin de jar inexistant ; un jar valide amputé de sa seconde moitié d'octets (EOCD supprimé).
- **Comportement attendu** : `open` lève `IOException` dans les deux cas (rejet propre, pas d'erreur arbitraire ni de retour silencieux).
- **Bornes** : troncature à 50 % de la longueur du fichier.
- **Statut** : actif

## Notes pour la réécriture
- L'oracle systématique est le JDK : toute implémentation Kotlin doit matcher `ZipFile` byte à byte (noms et contenus) — garder les mêmes corpus de synthèse comme golden comparatif.
- La fabrication du zip STORED exige CRC32 et taille exacts ; le zip de test est reconstruit à chaque test (noms distincts) pour éviter tout couplage d'état.
- L'entrée de 100 000 octets vise le franchissement d'un unique tampon d'inflation : à conserver pour piéger les lectures tronquées.
- Surface observable : `scanNames(Path)`, `readSingle(Path, String)`, `open(Path)` → handle `contains(String)`/`read(String)` fermable et réutilisable ; les fichiers corrompus/absents sont rejetés par `IOException`.
