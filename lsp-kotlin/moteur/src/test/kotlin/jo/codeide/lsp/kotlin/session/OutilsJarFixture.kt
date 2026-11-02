package jo.codeide.lsp.kotlin.session

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Le bâtisseur de JARS DE FIXTURE de la vague 2c (fiches 044/028/032/040) :
 * des class-files MINIMAUX mais RÉELS (JLS §4 : pool de constantes Utf8 +
 * Class, champs et méthodes avec leurs DESCRIPTEURS, aucun attribut), packagés
 * dans un jar physique posé comme ENTRÉE du classpath — AUCUNE vue factice :
 * la vue JVM du moteur lit ces entrées comme celles d'une bibliothèque
 * véritable (discipline pilotée par les entrées, miroir de la vague 2b).
 *
 * Le format est exactement ce que le lecteur maison
 * `resolve/VueJvmMembres.kt` attend : `0xCAFEBABE`, version 52, pool
 * dédupliqué, `this_class`/`super_class` par indirection Class (tag 7 → Utf8),
 * aucune interface, champs puis méthodes à `attributes_count = 0` (les accès
 * 0x0001 publics — jamais SYNTHETIC/BRIDGE ; 0x0009 publics STATIQUES depuis
 * la vague 2d : champ `INSTANCE` des objets, membres de compagnons, `const`).
 *
 * Étage 6 (fiche 018 — la fiche structurelle du survol) : le bâtisseur porte
 * les DRAPEAUX DE CLASSE (interface/enum/final), les INTERFACES DIRECTES,
 * les membres PRIVÉS/FINAUX/constantes d'enum et le mode HIÉRARCHIE BRISEÉ
 * (super pointé sur un Utf8 du pool — le lecteur tombe sur le genre et les
 * champs, l'héritage et les interfaces se taisent : dégradation D3).
 */
internal object OutilsJarFixture {

    /** Un membre de fixture (nom, descripteur JVMS §4.3, champ ou méthode, drapeaux). */
    internal class Membre(
        internal val nom: String,
        internal val descripteur: String,
        internal val estChamp: Boolean,
        internal val estStatique: Boolean = false,
        internal val estAbstrait: Boolean = false,
        internal val estPrive: Boolean = false,
        internal val estFinal: Boolean = false,
        internal val estConstanteEnum: Boolean = false,
    )

    /** Un bâtisseur accumulant les classes avant écriture. */
    internal class Batisseur {
        private val classes = LinkedHashMap<String, List<Membre>>()
        private val supers = HashMap<String, String?>()
        private val genres = HashMap<String, Int>()
        private val interfaces = HashMap<String, List<String>>()
        private val hierarchiesBrisees = HashSet<String>()

        /**
         * Ajoute une classe (super `null` = racine ; `java.lang.Object` et
         * `java.lang.Enum` restent des TERMINAUX CONNUS de la vue — jamais
         * requis dans les entrées).
         */
        internal fun classe(fqcn: String, superFqcn: String?, vararg membres: Membre): Batisseur {
            classes[fqcn] = membres.toList()
            supers[fqcn] = superFqcn
            return this
        }

        /**
         * Ajoute une classe GENRÉE (étage 6 — fiche 018) : les DRAPEAUX
         * d'accès de la classe (0x0201 public interface, 0x4001 public
         * enum, 0x0031 public final…) et les INTERFACES DIRECTES (FQCN).
         */
        internal fun classeGenree(
            fqcn: String,
            superFqcn: String?,
            accesClasse: Int,
            interfacesDirectes: List<String> = emptyList(),
            vararg membres: Membre,
        ): Batisseur {
            classes[fqcn] = membres.toList()
            supers[fqcn] = superFqcn
            genres[fqcn] = accesClasse
            interfaces[fqcn] = interfacesDirectes
            return this
        }

        /**
         * BRISE la hiérarchie d'une classe (étage 6 — fiche 018) : le
         * `super_class` pointe sur une constante Utf8 du pool, pas une
         * Class — le lecteur rend le genre et les champs, l'héritage se
         * tait (dégradation honnête D3).
         */
        internal fun briserHierarchie(fqcn: String): Batisseur {
            hierarchiesBrisees.add(fqcn)
            return this
        }

        /** Écrit le jar dans le dossier donné et rend le FICHIER (entrée de classpath). */
        internal fun ecrire(dossier: Path, nom: String): File {
            val fichier = File(dossier.toFile(), nom)
            ZipOutputStream(fichier.outputStream().buffered()).use { zip ->
                for ((fqcn, membres) in classes) {
                    zip.putNextEntry(ZipEntry(fqcn.replace('.', '/') + ".class"))
                    zip.write(
                        octetsClasse(
                            fqcn = fqcn,
                            superFqcn = supers[fqcn],
                            membres = membres,
                            accesClasse = genres[fqcn] ?: 0x0021,
                            interfacesDirectes = interfaces[fqcn].orEmpty(),
                            briserHierarchie = fqcn in hierarchiesBrisees,
                        ),
                    )
                    zip.closeEntry()
                }
            }
            return fichier
        }
    }

    /** Un champ de fixture (`champ("mFenetre", "Landroid/view/View;")`). */
    internal fun champ(nom: String, descripteur: String): Membre = Membre(nom, descripteur, true)

    /** Un champ PRIVÉ de fixture (étage 6 — exclu de la fiche structurelle). */
    internal fun champPrive(nom: String, descripteur: String): Membre =
        Membre(nom, descripteur, true, estPrive = true)

    /** Un champ STATIQUE FINAL de fixture (étage 6 — le marqueur `INSTANCE`, les constantes). */
    internal fun champStatiqueFinal(nom: String, descripteur: String): Membre =
        Membre(nom, descripteur, true, estStatique = true, estFinal = true)

    /** Une CONSTANTE D'ENUM de fixture (étage 6 — ACC_ENUM 0x4000 sur le champ). */
    internal fun constanteEnum(nom: String, descripteur: String): Membre =
        Membre(nom, descripteur, true, estStatique = true, estFinal = true, estConstanteEnum = true)

    /** Un champ STATIQUE de fixture (`champStatique("INSTANCE", "Lexo/theme/MaterialTheme;")`). */
    internal fun champStatique(nom: String, descripteur: String): Membre = Membre(nom, descripteur, true, estStatique = true)

    /** Une méthode de fixture (`methode("setContentView", "(Landroid/view/View;)V")`). */
    internal fun methode(nom: String, descripteur: String): Membre = Membre(nom, descripteur, false)

    /** Une méthode STATIQUE de fixture (vague 2d — le compagnon `@JvmStatic`). */
    internal fun methodeStatique(nom: String, descripteur: String): Membre = Membre(nom, descripteur, false, estStatique = true)

    /** Une méthode ABSTRAITE de fixture (vague 2f — l'interface binaire, ACC_ABSTRACT 0x0401). */
    internal fun methodeAbstraite(nom: String, descripteur: String): Membre = Membre(nom, descripteur, false, estAbstrait = true)

    /**
     * Les octets d'un class-file minimal (pool dédupliqué, membres sans
     * attributs) — étage 6 : drapeaux de classe, interfaces directes et
     * mode hiérarchie brisée.
     */
    private fun octetsClasse(
        fqcn: String,
        superFqcn: String?,
        membres: List<Membre>,
        accesClasse: Int = 0x0021,
        interfacesDirectes: List<String> = emptyList(),
        briserHierarchie: Boolean = false,
    ): ByteArray {
        val cheminThis = fqcn.replace('.', '/')
        val cheminSuper = superFqcn?.replace('.', '/')
        val cheminsInterfaces = interfacesDirectes.map { it.replace('.', '/') }

        // Le pool : les Utf8 dédupliqués (this, super, interfaces, noms,
        // descripteurs), puis les Class (this, super éventuel, interfaces).
        val utf8 = mutableListOf(cheminThis)
        if (cheminSuper != null && cheminSuper !in utf8) utf8.add(cheminSuper)
        for (chemin in cheminsInterfaces) {
            if (chemin !in utf8) utf8.add(chemin)
        }
        for (membre in membres) {
            if (membre.nom !in utf8) utf8.add(membre.nom)
            if (membre.descripteur !in utf8) utf8.add(membre.descripteur)
        }
        fun indexDe(texte: String): Int = utf8.indexOf(texte) + 1
        val classeThis = utf8.size + 1
        val classeSuper = if (cheminSuper != null) utf8.size + 2 else 0
        val premiereClasseInterface = utf8.size + 2 + (if (cheminSuper != null) 1 else 0)
        val classesInterfaces = cheminsInterfaces.mapIndexed { indice, _ -> premiereClasseInterface + indice }

        val sortie = ByteArrayOutputStream()
        DataOutputStream(sortie).use { flux ->
            flux.writeInt(0xCAFEBABE.toInt())
            flux.writeShort(0) // version mineure.
            flux.writeShort(52) // version majeure (lisible de JDK 8 à 21+).
            val taillePool = utf8.size + 1 + (if (cheminSuper != null) 1 else 0) + cheminsInterfaces.size
            flux.writeShort(taillePool + 1)
            for (texte in utf8) {
                flux.writeByte(1)
                val octets = texte.toByteArray(Charsets.UTF_8)
                flux.writeShort(octets.size)
                flux.write(octets)
            }
            flux.writeByte(7)
            flux.writeShort(indexDe(cheminThis))
            if (cheminSuper != null) {
                flux.writeByte(7)
                flux.writeShort(indexDe(cheminSuper))
            }
            for (chemin in cheminsInterfaces) {
                flux.writeByte(7)
                flux.writeShort(indexDe(chemin))
            }
            flux.writeShort(accesClasse)
            flux.writeShort(classeThis)
            // Hiérarchie brisée : le super pointe sur l'Utf8 DU NOM PROPRE
            // (une référence de classe invalide — le lecteur rend null).
            flux.writeShort(if (briserHierarchie) indexDe(cheminThis) else classeSuper)
            flux.writeShort(cheminsInterfaces.size)
            for (indice in cheminsInterfaces.indices) {
                flux.writeShort(classesInterfaces[indice])
            }
            for (estChamp in listOf(true, false)) {
                val duGenre = membres.filter { it.estChamp == estChamp }
                flux.writeShort(duGenre.size)
                for (membre in duGenre) {
                    // ACC_PUBLIC (jamais SYNTHETIC/BRIDGE) ; ACC_STATIC pour
                    // les membres statiques (vague 2d — INSTANCE des objets,
                    // compagnons, const) — 0x0009 ; ACC_ABSTRACT pour les
                    // méthodes d'interface (vague 2f) — 0x0401 ; étage 6 :
                    // ACC_PRIVATE 0x0002, ACC_FINAL 0x0010, ACC_ENUM 0x4000.
                    var acces = if (membre.estPrive) 0x0002 else 0x0001
                    if (membre.estStatique) acces = acces or 0x0008
                    if (membre.estAbstrait) acces = acces or 0x0400
                    if (membre.estFinal) acces = acces or 0x0010
                    if (membre.estConstanteEnum) acces = acces or 0x4000
                    flux.writeShort(acces)
                    flux.writeShort(indexDe(membre.nom))
                    flux.writeShort(indexDe(membre.descripteur))
                    flux.writeShort(0) // attributes_count.
                }
            }
        }
        return sortie.toByteArray()
    }
}
