// CodeIDE — vue JVM des MEMBRES du classpath (module :kotlin, étage 2 vague
// 2b — fiche 046 : sélecteurs sur récepteurs CLASSPATH typés, ADR 0223).
// Code NEUF jjoblab (clean-room — lecteur maison du format class-file JLS
// §4, miroir de la discipline de LecteurClassBrut R5 SANS la reprendre :
// celui-ci lit les CHAMPS et MÉTHODES déclarés, l'arbitrage Q-K1 « lecteur
// maison vs :index » est tranché À MINIMA pour la vague — aucun lien
// inter-modules, aucune dépendance neuve, le budget dex E-K8 est épargné).
package jo.codeide.lsp.kotlin.resolve

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.util.zip.ZipFile
import jo.codeide.lsp.kotlin.api.EntreeClasspathKotlin
import jo.codeide.lsp.kotlin.api.KindEntreeKotlin

/**
 * La vue JVM des membres du CLASSPATH : ce que les preuves ADR 0223
 * consultent quand le récepteur est un type DE BIBLIOTHÈQUE
 * (`android.widget.TextView`, `kotlin.String`…) — le jeu de membres COMPLET
 * (déclarés + chaîne de supertypes + propriétés dérivées des accesseurs +
 * implicites de Any/Object) d'un FQCN, lu des ENTRÉES posées.
 *
 * ## La discipline « pilotée par les entrées » (miroir de l'index, 1d)
 *
 * La vue ne lit QUE les jars/aars des entrées du classpath posé : un type
 * absent des entrées (le `java.lang.String` d'un android.jar de stub, le
 * `kotlin.String` mappé d'un stdlib seul) rend un jeu `null` — le récepteur
 * reste TYPABLE mais jamais ABSENCE-FIABLE (sélecteur muet, complétion
 * membre vide d'entrées : les EXTENSIONS de l'index se proposent encore).
 * La vue de l'HÔTE n'est jamais consultée (D6 : le moteur est
 * autonome) ; la ressource du RUNTIME n'est jamais lue (déterminisme
 * bac à sable = CI = appareil).
 *
 * ## Lecture paresseuse et tolérante
 *
 * Tout est PARESSEUX (rien n'est lu à la construction — le snapshot se
 * publie à vil coût) et TOUT est tolérant (D3) : une entrée absente, un
 * zip corrompu, un class-file hostile rendent `null`/vide, jamais
 * d'exception vers l'appelant. Les lectures sont SYNCHRONISÉES sur la vue
 * (les requêtes de session sont parallèles — ADR 0117).
 *
 * @property entrees les entrées du classpath couvert (ordre de préséance).
 */
internal class VueJvmMembres(private val entrees: List<EntreeClasspathKotlin>) {

    /** Les zip ouverts par entrée (paresseux — l'ordre des entrées fait foi). */
    private val zips = arrayOfNulls<ZipFile>(entrees.size)

    /** Le cache des membres DÉCLARÉS par FQCN (`null` = classe introuvable). */
    private val cacheDeclares = HashMap<String, MembresDeclaresJvm?>()

    /** Le cache des jeux COMPLETS par FQCN (`null` = chaîne non lisible). */
    private val cacheJeux = HashMap<String, JeuMembresJvm?>()

    /**
     * Les membres DÉCLARÉS d'un FQCN des entrées (sans héritage).
     *
     * @param fqcn le nom pleinement qualifié (`android.widget.TextView`) ;
     * @return les membres déclarés ; `null` si la classe n'est dans AUCUNE
     * entrée (ou est illisible — la preuve d'absence se tait, D3).
     */
    internal fun membresDeclares(fqcn: String): MembresDeclaresJvm? {
        synchronized(this) {
            if (cacheDeclares.containsKey(fqcn)) return cacheDeclares[fqcn]
            val lus = lireMembres(fqcn)
            cacheDeclares[fqcn] = lus
            return lus
        }
    }

    /**
     * Le jeu de membres COMPLET d'un FQCN : les DÉCLARÉS de toute la chaîne
     * de supertypes (superclasse et interfaces, en profondeur bornée et
     * gardée contre les cycles), les PROPRIÉTÉS DÉRIVÉES des accesseurs
     * Java (`getText` → `text`, ADR 0219) et les IMPLICITES de
     * Any/Object — la matière des preuves ADR 0223 et de la complétion
     * membre.
     *
     * `java.lang.Object` est un TERMINAL CONNU (ses membres publics et
     * protégés sont le socle commun — jamais requis lisible) ; un maillon
     * manquant des entrées rend le jeu `null` (chaîne non prouvable).
     *
     * @param fqcn le nom pleinement qualifié du récepteur ;
     * @return le jeu complet ; `null` si la chaîne n'est pas lisible.
     */
    internal fun jeuComplet(fqcn: String): JeuMembresJvm? {
        synchronized(this) {
            if (cacheJeux.containsKey(fqcn)) return cacheJeux[fqcn]
            val jeu = assemblerJeu(fqcn)
            cacheJeux[fqcn] = jeu
            return jeu
        }
    }

    /**
     * Les FQCN des classes des entrées portant EXACTEMENT ce nom simple
     * (quick-fix d'import `importsCandidats`, fiche 046) : les entrées sont
     * parcourues en ordre de préséance, les classes INTERNES (`$`) et les
     * fichiers spéciaux (`module-info`, `package-info`) sont ÉCARTÉS.
     *
     * @param nomSimple le nom cherché (`TextView`) ;
     * @return les FQCN distincts, TRIÉS (déterminisme) ; liste vide si
     * aucune entrée ne porte ce nom.
     */
    internal fun fqcnParNomSimple(nomSimple: String): List<String> {
        synchronized(this) {
            val trouves = sortedSetOf<String>()
            for (i in entrees.indices) {
                val zip = zipDe(i) ?: continue
                val prefixe = "$nomSimple.class"
                val entreesZip = try {
                    zip.entries()
                } catch (_: Exception) {
                    continue
                }
                for (entree in entreesZip) {
                    val nom = entree.name
                    if (!nom.endsWith(".class")) continue
                    if (nom.endsWith("/module-info.class") ||
                        nom.endsWith("/package-info.class")
                    ) {
                        continue
                    }
                    val base = nom.substringAfterLast('/')
                    if (base != prefixe) continue
                    if ('\$' in base) continue
                    trouves.add(nom.removeSuffix(".class").replace('/', '.'))
                }
            }
            return trouves.toList()
        }
    }

    // ------------------------------------------------------------------
    // Assemblage du jeu complet (la chaîne de supertypes).
    // ------------------------------------------------------------------

    /**
     * Assemble le jeu complet par marche en profondeur bornée de la chaîne
     * des supertypes (cycle-interrompue ; `java.lang.Object` terminal).
     *
     * @param fqcn le FQCN de départ ;
     * @return le jeu ; `null` si un maillon manque des entrées.
     */
    private fun assemblerJeu(fqcn: String): JeuMembresJvm? {
        val noms = LinkedHashSet<String>()
        val visites = HashSet<String>()
        val pile = ArrayDeque<String>()
        pile.addLast(fqcn)
        var profondeur = 0
        while (pile.isNotEmpty()) {
            if (++profondeur > PROFONDEUR_MAX) return null
            val courant = pile.removeLast()
            if (!visites.add(courant)) continue
            if (courant == "java.lang.Object" || courant == "kotlin.Any") {
                noms.addAll(IMPLICITS_ANY)
                continue
            }
            val declares = lireMembres(courant) ?: return null
            for (membre in declares.membres) noms.add(membre)
            for (membre in declares.proprietesDerivees) noms.add(membre)
            declares.superFqcn?.let { pile.addLast(it) }
            pile.addAll(declares.interfaces)
        }
        noms.addAll(IMPLICITS_ANY)
        return JeuMembresJvm(noms)
    }

    // ------------------------------------------------------------------
    // Lecture des entrées (le lecteur maison des membres).
    // ------------------------------------------------------------------

    /**
     * Lit les membres déclarés d'un FQCN dans la PREMIÈRE entrée qui le
     * porte (ordre de préséance du classpath).
     *
     * @param fqcn le nom pleinement qualifié ;
     * @return les membres déclarés avec la chaîne écrite ; `null` si
     * introuvable ou illisible (D3).
     */
    private fun lireMembres(fqcn: String): MembresDeclaresJvm? {
        val chemin = fqcn.replace('.', '/') + ".class"
        for (i in entrees.indices) {
            val zip = zipDe(i) ?: continue
            val octets = try {
                val entree = zip.getEntry(chemin) ?: continue
                zip.getInputStream(entree).use { it.readBytes() }
            } catch (_: Exception) {
                continue
            }
            return LecteurMembresClasse.lire(octets)
        }
        return null
    }

    /**
     * Le zip ouvert d'une entrée (paresseux, CACHÉ — la vue vit tant que le
     * snapshot la sert ; l'abandon du snapshot rend le zip unreachable et le
     * nettoyeur du JDK le referme ; une entrée JAR est lue telle quelle, une
     * AAR est lue dans son `classes.jar` imbriqué).
     *
     * @param index l'ordre de l'entrée dans le classpath ;
     * @return le zip ouvert ; `null` si l'entrée est absente/corrompue.
     */
    private fun zipDe(index: Int): ZipFile? {
        zips[index]?.let { return it }
        val entree = entrees[index]
        if (entree.kind != KindEntreeKotlin.JAR && entree.kind != KindEntreeKotlin.AAR) return null
        val fichier = File(entree.chemin)
        if (!fichier.isFile) return null
        val zip = try {
            if (entree.kind == KindEntreeKotlin.JAR) {
                ZipFile(fichier)
            } else {
                // AAR : les classes vivent dans classes.jar — un zip IMBRIQUÉ
                // est lu en mémoire (miroir minimal du décodage 1d).
                val externe = ZipFile(fichier)
                val octets = try {
                    val entreeInterne = externe.getEntry("classes.jar") ?: run {
                        externe.close()
                        return null
                    }
                    externe.getInputStream(entreeInterne).use { it.readBytes() }
                } finally {
                    externe.close()
                }
                ZipFile(
                    File.createTempFile("vuejvm-aar", ".jar").apply {
                        deleteOnExit()
                        writeBytes(octets)
                    },
                )
            }
        } catch (_: Exception) {
            return null
        }
        zips[index] = zip
        return zip
    }

    /** Le companion des constantes et implicites. */
    private companion object {
        /** La borne de profondeur de la chaîne de supertypes. */
        private const val PROFONDEUR_MAX: Int = 24

        /**
         * Les membres IMPLICITES de `java.lang.Object`/`kotlin.Any` — le
         * socle commun de tout récepteur (miroir de
         * `UniversPreuves.MEMBRES_IMPLICITS`, conservateur : les membres
         * protégés compris).
         */
        private val IMPLICITS_ANY: List<String> = listOf(
            "equals", "hashCode", "toString", "getClass", "wait", "notify",
            "notifyAll", "clone", "finalize",
        )
    }
}

/**
 * Les membres DÉCLARÉS d'une classe du classpath (sans héritage).
 *
 * @property membres les noms des champs et méthodes NON synthétiques NON
 * pont (les constructeurs `<init>`/`<clinit>` exclus) ;
 * @property proprietesDerivees les propriétés dérivées des accesseurs Java
 * (`getText` → `text`, `isEnabled` → `isEnabled` — ADR 0219 : la
 * sur-approximation est la direction SÛRE, elle MUEt des preuves au lieu
 * d'en forger de fausses) ;
 * @property superFqcn la superclasse ÉCRITE (`null` = racine) ;
 * @property interfaces les interfaces directes ÉCRITES.
 */
internal class MembresDeclaresJvm(
    internal val membres: List<String>,
    internal val proprietesDerivees: List<String>,
    internal val superFqcn: String?,
    internal val interfaces: List<String>,
)

/**
 * Le jeu de membres COMPLET d'un récepteur classpath (la matière des
 * preuves ADR 0223 — présence et absence) : déclarés de toute la chaîne,
 * propriétés dérivées et implicites de Any/Object.
 *
 * @property noms les noms appelables (dédupliqués, ordre de rencontre).
 */
internal class JeuMembresJvm(internal val noms: Set<String>) {

    /**
     * Le nom est-il un membre du jeu ?
     *
     * @param nom le nom du sélecteur ;
     * @return `true` si présent (la référence RÉSOUT).
     */
    internal fun contient(nom: String): Boolean = nom in noms
}

/**
 * Le lecteur maison des MEMBRES d'un class-file (JLS §4 : pool de
 * constantes, `fields_count`/`methods_count`, en-têtes u2×3 par membre) —
 * la même discipline octet par octet que `LecteurClassBrut` (R5), le
 * périmètre des membres EN PLUS ; les attributs sont sautés balancés.
 * Tolérant (D3) : tout écart rend `null`, jamais d'exception.
 */
private object LecteurMembresClasse {

    /** Magie d'un fichier de classe JVM. */
    private const val MAGIE_CLASSE: Int = 0xCAFEBABE.toInt()

    /** Accès SYNTHETIC (les membres générés ne sont pas appelables par nom). */
    private const val ACC_SYNTHETIC: Int = 0x1000

    /** Accès BRIDGE (les ponts génériques ne sont pas appelables par nom). */
    private const val ACC_BRIDGE: Int = 0x0040

    /**
     * Lit les membres déclarés d'un class-file.
     *
     * @param octets les octets du `.class` ;
     * @return les membres + la chaîne écrite ; `null` si illisible (D3 —
     * le consommateur sous-rapporte, jamais d'exception).
     */
    internal fun lire(octets: ByteArray): MembresDeclaresJvm? {
        return try {
            lireOuNull(octets)
        } catch (_: Exception) {
            null
        }
    }

    /** Le corps de lecture (toute exception devient `null` par [lire]). */
    private fun lireOuNull(octets: ByteArray): MembresDeclaresJvm? {
        val flux = DataInputStream(ByteArrayInputStream(octets))
        if (flux.readInt() != MAGIE_CLASSE) return null
        flux.readInt() // version mineure + majeure.
        val compte = flux.readUnsignedShort()
        val utf8 = arrayOfNulls<String>(compte)
        // L'indirection des constantes CLASS (tag 7 : index → name_index)
        // — sans elle, la superclasse et les interfaces restent muettes.
        val classes = IntArray(compte)
        var i = 1
        while (i < compte) {
            val tag = flux.readUnsignedByte()
            when (tag) {
                1 -> {
                    val longueur = flux.readUnsignedShort()
                    val texte = ByteArray(longueur)
                    flux.readFully(texte)
                    utf8[i] = texte.toString(Charsets.UTF_8)
                }
                3, 4 -> flux.skipBytes(4)
                5, 6 -> {
                    flux.skipBytes(8)
                    i++ // les longs/doubles occupent DEUX index du pool.
                }
                7 -> classes[i] = flux.readUnsignedShort()
                8, 16, 19, 20 -> flux.skipBytes(2)
                9, 10, 11, 12, 17, 18 -> flux.skipBytes(4)
                15 -> flux.skipBytes(3)
                else -> return null
            }
            i++
        }
        fun nomClasse(index: Int): String? {
            if (index <= 0 || index >= compte) return null
            return utf8[classes[index]]?.replace('/', '.')
        }

        flux.skipBytes(4) // access_flags + this_class.
        val superIndex = flux.readUnsignedShort()
        val superFqcn = if (superIndex == 0) null else nomClasse(superIndex)
        val nbInterfaces = flux.readUnsignedShort()
        val interfaces = mutableListOf<String>()
        repeat(nbInterfaces) {
            nomClasse(flux.readUnsignedShort())?.let { interfaces.add(it) }
        }

        val membres = mutableListOf<String>()
        val derivees = mutableListOf<String>()
        for (estChamp in listOf(true, false)) {
            val nombre = flux.readUnsignedShort()
            repeat(nombre) {
                val acces = flux.readUnsignedShort()
                val nom = utf8[flux.readUnsignedShort()] ?: return null
                flux.skipBytes(2) // descriptor.
                val nbAttributs = flux.readUnsignedShort()
                repeat(nbAttributs) {
                    flux.skipBytes(2)
                    val longueur = flux.readInt()
                    flux.skipBytes(longueur)
                }
                if (nom == "<init>" || nom == "<clinit>") return@repeat
                if (acces and (ACC_SYNTHETIC or ACC_BRIDGE) != 0) return@repeat
                membres.add(nom)
                if (!estChamp) {
                    // Accesseurs Java → propriétés dérivées (ADR 0219) :
                    // `getText` → `text` (décapitalisation conservatrice —
                    // la sur-approximation MUET, elle ne forge pas).
                    if (nom.length > 3 && nom.startsWith("get")) {
                        derivees.add(nom.substring(3).replaceFirstChar { it.lowercaseChar() })
                    } else if (nom.length > 2 && nom.startsWith("is")) {
                        derivees.add(nom)
                    }
                }
            }
        }
        return MembresDeclaresJvm(membres, derivees, superFqcn, interfaces)
    }
}
