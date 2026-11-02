package jo.codeide.lsp.java.classpath

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.Inflater

/**
 * Lecteur ZIP minimaliste « brut » pour les jars du classpath (étage N1,
 * cahier fiche 038).
 *
 * Le moteur ne dépend PAS de `java.util.zip.ZipFile` pour son chemin chaud :
 * ce lecteur sait inventorier un zip depuis sa **table centrale** (fin du
 * fichier, remontée depuis l'EOCD) et lire une entrée unique en un seul coup,
 * sans instancier un `ZipFile` complet ni verrouiller le fichier. La vérité
 * terrain reste `ZipFile` : chaque propriété du contrat est vérifiée par le
 * test du cahier **par rapport au JDK**, octet pour octet (noms et contenus).
 *
 * Cas couverts par le contrat : entrées DEFLATED et STORED, noms UTF-8
 * accentués, entrées imbriquées `$` (AndroidX), grandes entrées multi-tampons
 * (inflation traversant plusieurs tampons), handle persistant réutilisable,
 * et rejet propre (`IOException`) des fichiers absents ou tronqués.
 *
 * Empreinte : le [Handle] ne retient en mémoire que la **table centrale**
 * (métadonnées, petite devant les données) ; les contenus d'entrées sont lus
 * depuis le disque à la demande, en flux, à l'offset calculé.
 *
 * Méthodes de classe :
 *  - [scanNames] : inventaire exact des noms d'entrées (table centrale) ;
 *  - [readSingle] : lecture d'une entrée en un coup ;
 *  - [open] : handle persistant ([Handle]) — `contains` + lectures répétées.
 */
public object LecteurZipBrut {

    /** Taille du tampon de lecture/inflation (le contrat éprouve le franchissement multi-tampons). */
    private const val TAILLE_TAMPON: Int = 8 * 1024

    /** Nombre maximal d'entrées acceptées dans la table centrale (garde anti-corruption). */
    private const val MAX_ENTREES: Int = 1 shl 20

    /** Taille maximale de la queue explorée pour trouver l'EOCD (22 + commentaire 65 535). */
    private const val QUEUE_MAX: Int = 22 + 65_536

    /** Amorce de la signature d'un enregistrement de table centrale. */
    private val AMORCE_CENTRALE = intArrayOf(0x50, 0x4b, 0x01, 0x02)

    /** Signature EOCD (fin de table centrale). */
    private val AMORCE_EOCD = intArrayOf(0x50, 0x4b, 0x05, 0x06)

    /**
     * Inventorie les noms d'entrées d'un zip depuis sa table centrale.
     *
     * La table centrale est l'autorité de l'inventaire (c'est ce qu'énumère
     * `ZipFile.entries()`) : les entrées locales non référencées sont ignorées.
     *
     * @param chemin chemin du fichier zip ; doit exister et être un zip complet.
     * @return l'ensemble exact des noms d'entrées décodés en UTF-8.
     * @throws IOException si le fichier est absent, tronqué (EOCD introuvable)
     *   ou si la table centrale est incohérente (compteur invalide).
     */
    @JvmStatic
    public fun scanNames(chemin: Path): Set<String> = open(chemin).use { it.noms() }

    /**
     * Lit une entrée unique d'un zip, en un coup.
     *
     * @param chemin chemin du fichier zip.
     * @param nom nom exact de l'entrée (séparateurs `/`, décodage UTF-8).
     * @return les octets décompressés de l'entrée.
     * @throws IOException si le zip est invalide, si l'entrée est absente ou
     *   si les données ne se décompressent pas à la taille déclarée.
     */
    @JvmStatic
    public fun readSingle(chemin: Path, nom: String): ByteArray = open(chemin).use { it.lire(nom) }

    /**
     * Ouvre un handle persistant sur un zip.
     *
     * Le handle met en cache la table centrale (une seule lecture des
     * métadonnées) et permet des lectures répétées par entrée — c'est la
     * forme utilisée par le cache de localisation du moteur (fiche 024) :
     * ouvrir chaque jar du classpath une fois, interroger des centaines de
     * FQCN sans re-balayer la table centrale.
     *
     * @param chemin chemin du fichier zip.
     * @return un handle ouvert (auto-fermable — [Handle.close] invalide l'état).
     * @throws IOException si le fichier est absent ou tronqué.
     */
    @JvmStatic
    public fun open(chemin: Path): Handle {
        val taille = Files.size(chemin)
        if (taille < 22L) throw IOException("zip trop court (${taille} octets) : $chemin")
        // Seule la queue est chargée pour l'EOCD : le jar reste sur le disque.
        // Chaque plage est lue depuis un flux NEUF (un InputStream ne revient
        // pas en arrière ; la table centrale est AVANT la queue).
        val queue = Files.newInputStream(chemin).use { flux ->
            lireOctets(flux, (taille - QUEUE_MAX).coerceAtLeast(0L), taille)
        }
        val indexEocd = trouverEocd(queue)
        val nbEntrees = le16(queue, indexEocd + 10)
        if (nbEntrees <= 0 || nbEntrees > MAX_ENTREES) {
            throw IOException("compteur d'entrées suspect : $nbEntrees")
        }
        val debutTable = le32(queue, indexEocd + 16)
        val finTable = taille - (queue.size - indexEocd.toLong())
        if (debutTable < 0 || finTable < debutTable) {
            throw IOException("bornes de table centrale incohérentes : $debutTable..$finTable")
        }
        val tableOctets = Files.newInputStream(chemin).use { flux ->
            lireOctets(flux, debutTable, finTable)
        }
        return Handle(chemin, taille, lireTableCentrale(tableOctets, nbEntrees))
    }

    /** Charge exactement la plage [debut, fin) depuis un flux positionné au début du fichier. */
    private fun lireOctets(flux: InputStream, debut: Long, fin: Long): ByteArray {
        ignorer(flux, debut)
        val resultat = ByteArray((fin - debut).toInt())
        var lu = 0
        while (lu < resultat.size) {
            val n = flux.read(resultat, lu, resultat.size - lu)
            if (n < 0) throw EOFException("lecture tronquée (${lu}/${resultat.size} octets)")
            lu += n
        }
        return resultat
    }

    /** Avance un flux de [nb] octets (skip par petits paquets — contrat InputStream). */
    private fun ignorer(flux: InputStream, nb: Long) {
        var reste = nb
        while (reste > 0) {
            val n = flux.skip(reste)
            if (n <= 0) {
                if (flux.read() < 0) throw EOFException("skip tronqué")
                reste -= 1
            } else {
                reste -= n
            }
        }
    }

    /** Localise l'enregistrement EOCD en remontant depuis la fin (commentaire terminal possible). */
    private fun trouverEocd(queue: ByteArray): Int {
        var i = queue.size - 22
        while (i >= 0) {
            if (amorcePresente(queue, i, AMORCE_EOCD)) return i
            i--
        }
        throw EOFException("EOCD introuvable (zip tronqué ou non-zip)")
    }

    /** Vérifie une amorce de 4 octets à l'offset donné. */
    private fun amorcePresente(octets: ByteArray, offset: Int, amorce: IntArray): Boolean =
        (0..3).all { k -> (octets[offset + k].toInt() and 0xff) == amorce[k] }

    /** Parse la table centrale ; rend les métadonnées par nom d'entrée. */
    private fun lireTableCentrale(octets: ByteArray, nbEntrees: Int): Map<String, EntreeZip> {
        val table = HashMap<String, EntreeZip>(nbEntrees * 2)
        var p = 0
        repeat(nbEntrees) {
            if (p + 46 > octets.size || !amorcePresente(octets, p, AMORCE_CENTRALE)) {
                throw IOException("enregistrement de table centrale invalide à $p")
            }
            val methode = le16(octets, p + 10)
            val tailleCompressee = le32(octets, p + 20)
            val tailleDeCompressee = le32(octets, p + 24)
            val longueurNom = le16(octets, p + 28)
            val longueurExtra = le16(octets, p + 30)
            val longueurCommentaire = le16(octets, p + 32)
            val offsetLocal = le32(octets, p + 42)
            if (p + 46 + longueurNom > octets.size) {
                throw IOException("nom d'entrée hors bornes à $p")
            }
            val nom = String(octets, p + 46, longueurNom, Charsets.UTF_8)
            table[nom] = EntreeZip(methode, tailleCompressee, tailleDeCompressee, offsetLocal)
            p += 46 + longueurNom + longueurExtra + longueurCommentaire
        }
        return table
    }

    /** Lit un petit entier non signé 16 bits petit-boutiste. */
    internal fun le16(octets: ByteArray, offset: Int): Int =
        (octets[offset].toInt() and 0xff) or ((octets[offset + 1].toInt() and 0xff) shl 8)

    /** Lit un entier non signé 32 bits petit-boutiste (rendu en Long pour éviter le signe). */
    internal fun le32(octets: ByteArray, offset: Int): Long =
        (octets[offset].toLong() and 0xff) or
            ((octets[offset + 1].toLong() and 0xff) shl 8) or
            ((octets[offset + 2].toLong() and 0xff) shl 16) or
            ((octets[offset + 3].toLong() and 0xff) shl 24)

    /**
     * Handle persistant sur un zip ouvert (fiche 038 : cycle de vie
     * `contains`/lectures répétées/fermeture).
     *
     * Ne retient que la table centrale en mémoire ; [contains] est un O(1)
     * après l'ouverture — c'est le gain que consomme le cache de
     * localisation (fiche 024).
     */
    public class Handle internal constructor(
        private val chemin: Path,
        private val tailleFichier: Long,
        private val table: Map<String, EntreeZip>,
    ) : AutoCloseable {

        private var ferme: Boolean = false

        /** Inventaire des noms d'entrées du handle (même ensemble que [scanNames]). */
        public fun noms(): Set<String> {
            verifierOuvert()
            return table.keys
        }

        /**
         * Indique si le zip contient une entrée du nom exact donné.
         *
         * @param nom nom d'entrée (avec séparateurs `/`).
         * @return vrai ssi l'entrée existe dans la table centrale.
         */
        public fun contains(nom: String): Boolean {
            verifierOuvert()
            return table.containsKey(nom)
        }

        /**
         * Lit et décompresse une entrée par son nom exact.
         *
         * @param nom nom d'entrée (avec séparateurs `/`).
         * @return les octets décompressés ; identiques à [readSingle] pour la
         *   même entrée (contrat de parité avec `ZipFile`).
         * @throws IOException si l'entrée est absente, la méthode inconnue ou
         *   la taille décompressée ne concorde pas avec la table centrale.
         */
        public fun lire(nom: String): ByteArray {
            verifierOuvert()
            val entree = table[nom] ?: throw IOException("entrée absente du zip $chemin : $nom")
            Files.newInputStream(chemin).use { flux ->
                // En-tête local : signature (4) + champs fixes (26) incluant
                // namelen (26) et extralen (28), PUIS nom + extra, PUIS données.
                // L'extra LOCAL peut différer de l'extra central : sa longueur
                // doit être relue depuis l'en-tête local, jamais réutilisée.
                ignorer(flux, entree.offsetLocal)
                val entete = lireN(flux, 30)
                val longueurNomLocale = le16(entete, 26)
                val longueurExtraLocale = le16(entete, 28)
                ignorer(flux, (longueurNomLocale + longueurExtraLocale).toLong())
                val compressees = lireN(flux, entree.tailleCompressee.toInt())
                val donnees = when (entree.methode) {
                    0 -> compressees
                    8 -> inflater(compressees)
                    else -> throw IOException("méthode de compression non supportée : ${entree.methode}")
                }
                if (donnees.size.toLong() != entree.tailleDeCompressee) {
                    throw IOException(
                        "taille décompressée incohérente pour $nom : ${donnees.size} != ${entree.tailleDeCompressee}",
                    )
                }
                return donnees
            }
        }

        /** Décompresse un flux DEFLATE brut jusqu'à épuisement (multi-tampons). */
        private fun inflater(compressees: ByteArray): ByteArray {
            val inflater = Inflater(true)
            // Inflater « nowrap » (DEFLATE brut) : la bibliothèque native
            // exige un octet supplémentaire DERRIÈRE le flux — un octet nul
            // d'amorce est ajouté à l'entrée (jamais consommé par les données).
            val entree = compressees.copyOf(compressees.size + 1)
            inflater.setInput(entree)
            val sortie = ByteArrayOutputStream()
            val tampon = ByteArray(TAILLE_TAMPON)
            try {
                while (!inflater.finished()) {
                    val n = inflater.inflate(tampon)
                    if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                        throw IOException("flux DEFLATE incomplet")
                    }
                    if (n > 0) sortie.write(tampon, 0, n)
                }
                return sortie.toByteArray()
            } finally {
                inflater.end()
            }
        }

        /** Lit exactement [n] octets du flux. */
        private fun lireN(flux: InputStream, n: Int): ByteArray {
            val resultat = ByteArray(n)
            var lu = 0
            while (lu < n) {
                val k = flux.read(resultat, lu, n - lu)
                if (k < 0) throw EOFException("lecture tronquée (${lu}/${n} octets) : $chemin")
                lu += k
            }
            return resultat
        }

        /** Rejette tout usage après [close] (rejet propre, pas d'état silencieux). */
        private fun verifierOuvert() {
            check(!ferme) { "handle zip fermé : $chemin" }
        }

        override fun close() {
            ferme = true
        }
    }

    /** Métadonnées d'une entrée de la table centrale (offsets 32 bits sans signe). */
    internal class EntreeZip(
        val methode: Int,
        val tailleCompressee: Long,
        val tailleDeCompressee: Long,
        val offsetLocal: Long,
    )
}
