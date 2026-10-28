package jo.codeide.lsp.ecjart.compat

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * Shim ART des méthodes d'`InputStream` introduites par Java 9+ —
 * `readAllBytes()` et `readNBytes(int)` sont API 33+ sur Android alors que
 * CodeIDE cible minSdk 26 : le patcheur redirige ces invocations vers les
 * équivalents statiques de cet objet (directive `rediriger-methode` de la
 * liste de shims versionnée — le récepteur de l'appel devient le premier
 * argument, forme de pile identique, frames inchangées).
 *
 * Sémantique implémentée depuis la javadoc publique de
 * `java.io.InputStream` (Java 21) — les comportements d'entrée-sortie
 * intermédiaires reposent sur `read(byte[], int, int)`, disponible depuis
 * l'API 1 : ces shims ne référencent donc AUCUNE API postérieure à minSdk 26
 * (le scanner ART du module le vérifie sur le jar patché).
 */
public object LectureFlux {

    /** Taille des blocs de lecture (compromis allocs/appels — 8 Kio). */
    private const val TAILLE_BLOC: Int = 8192

    /**
     * Équivalent d'`InputStream.readAllBytes()` : lit tous les octets
     * restants du flux jusqu'à la détection de fin de flux.
     *
     * Bloque jusqu'à ce que tous les octets restants aient été lus et que la
     * fin de flux soit détectée, ou qu'une exception soit levée. La
     * javadoc JDK précise que cette méthode n'agit pas sur la position d'un
     * flux repérable — comportement identique ici.
     *
     * @param flux flux à lire (non nul) — le récepteur de l'appel redirigé.
     * @return un tableau contenant les octets lus (vide si le flux était déjà
     *   en fin de lecture).
     * @throws IOException si une erreur d'entrée-sortie survient pendant la
     *   lecture (propagée telle quelle par `read`).
     */
    @JvmStatic
    public fun lireTout(flux: InputStream): ByteArray {
        val tampons = ByteArrayOutputStream()
        val bloc = ByteArray(TAILLE_BLOC)
        while (true) {
            val lus = flux.read(bloc, 0, bloc.size)
            if (lus < 0) break // fin de flux (javadoc : read renvoie -1)
            if (lus > 0) tampons.write(bloc, 0, lus)
        }
        return tampons.toByteArray()
    }

    /**
     * Équivalent d'`InputStream.readNBytes(int)` : lit AU PLUS [nombre]
     * octets, en bloquant jusqu'à en avoir lu exactement [nombre], ou
     * jusqu'à la fin de flux, ou jusqu'à une exception.
     *
     * @param flux flux à lire (non nul) — le récepteur de l'appel redirigé.
     * @param nombre nombre maximal d'octets à lire.
     * @return un tableau d'au plus [nombre] octets (vide si [nombre] vaut
     *   zéro ou si le flux est déjà en fin de lecture).
     * @throws IllegalArgumentException si [nombre] est négatif (parité avec
     *   la javadoc JDK).
     * @throws IOException si une erreur d'entrée-sortie survient.
     */
    @JvmStatic
    public fun lireN(flux: InputStream, nombre: Int): ByteArray {
        if (nombre < 0) throw IllegalArgumentException("nombre d'octets négatif : $nombre")
        if (nombre == 0) return ByteArray(0)
        val sortie = ByteArray(nombre)
        var position = 0
        while (position < nombre) {
            val lus = flux.read(sortie, position, nombre - position)
            if (lus < 0) break // fin de flux avant d'atteindre le nombre demandé
            position += lus
        }
        return if (position == nombre) sortie else sortie.copyOf(position)
    }
}
