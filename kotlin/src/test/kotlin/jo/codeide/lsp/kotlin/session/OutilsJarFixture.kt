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
 * dédupliqué, `access_flags` 0x0021, `this_class`/`super_class` par
 * indirection Class (tag 7 → Utf8), aucune interface, champs puis méthodes à
 * `attributes_count = 0` (les accès 0x0001 publics — jamais SYNTHETIC/BRIDGE ;
 * 0x0009 publics STATIQUES depuis la vague 2d : champ `INSTANCE` des objets,
 * membres de compagnons, `const`).
 */
internal object OutilsJarFixture {

    /** Un membre de fixture (nom, descripteur JVMS §4.3, champ ou méthode, drapeaux). */
    internal class Membre(
        internal val nom: String,
        internal val descripteur: String,
        internal val estChamp: Boolean,
        internal val estStatique: Boolean = false,
        internal val estAbstrait: Boolean = false,
    )

    /** Un bâtisseur accumulant les classes avant écriture. */
    internal class Batisseur {
        private val classes = LinkedHashMap<String, List<Membre>>()
        private val supers = HashMap<String, String?>()

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

        /** Écrit le jar dans le dossier donné et rend le FICHIER (entrée de classpath). */
        internal fun ecrire(dossier: Path, nom: String): File {
            val fichier = File(dossier.toFile(), nom)
            ZipOutputStream(fichier.outputStream().buffered()).use { zip ->
                for ((fqcn, membres) in classes) {
                    zip.putNextEntry(ZipEntry(fqcn.replace('.', '/') + ".class"))
                    zip.write(octetsClasse(fqcn, supers[fqcn], membres))
                    zip.closeEntry()
                }
            }
            return fichier
        }
    }

    /** Un champ de fixture (`champ("mFenetre", "Landroid/view/View;")`). */
    internal fun champ(nom: String, descripteur: String): Membre = Membre(nom, descripteur, true)

    /** Un champ STATIQUE de fixture (`champStatique("INSTANCE", "Lexo/theme/MaterialTheme;")`). */
    internal fun champStatique(nom: String, descripteur: String): Membre = Membre(nom, descripteur, true, estStatique = true)

    /** Une méthode de fixture (`methode("setContentView", "(Landroid/view/View;)V")`). */
    internal fun methode(nom: String, descripteur: String): Membre = Membre(nom, descripteur, false)

    /** Une méthode STATIQUE de fixture (vague 2d — le compagnon `@JvmStatic`). */
    internal fun methodeStatique(nom: String, descripteur: String): Membre = Membre(nom, descripteur, false, estStatique = true)

    /** Une méthode ABSTRAITE de fixture (vague 2f — l'interface binaire, ACC_ABSTRACT 0x0401). */
    internal fun methodeAbstraite(nom: String, descripteur: String): Membre = Membre(nom, descripteur, false, estAbstrait = true)

    /** Les octets d'un class-file minimal (pool dédupliqué, membres sans attributs). */
    private fun octetsClasse(fqcn: String, superFqcn: String?, membres: List<Membre>): ByteArray {
        val cheminThis = fqcn.replace('.', '/')
        val cheminSuper = superFqcn?.replace('.', '/')

        // Le pool : les Utf8 dédupliqués (this, super, noms, descripteurs),
        // puis les Class (this, super éventuel).
        val utf8 = mutableListOf(cheminThis)
        if (cheminSuper != null && cheminSuper !in utf8) utf8.add(cheminSuper)
        for (membre in membres) {
            if (membre.nom !in utf8) utf8.add(membre.nom)
            if (membre.descripteur !in utf8) utf8.add(membre.descripteur)
        }
        fun indexDe(texte: String): Int = utf8.indexOf(texte) + 1
        val classeThis = utf8.size + 1
        val classeSuper = if (cheminSuper != null) utf8.size + 2 else 0

        val sortie = ByteArrayOutputStream()
        DataOutputStream(sortie).use { flux ->
            flux.writeInt(0xCAFEBABE.toInt())
            flux.writeShort(0) // version mineure.
            flux.writeShort(52) // version majeure (lisible de JDK 8 à 21+).
            flux.writeShort(if (cheminSuper != null) utf8.size + 3 else utf8.size + 2)
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
            flux.writeShort(0x0021) // ACC_PUBLIC | ACC_SUPER.
            flux.writeShort(classeThis)
            flux.writeShort(classeSuper)
            flux.writeShort(0) // interfaces_count.
            for (estChamp in listOf(true, false)) {
                val duGenre = membres.filter { it.estChamp == estChamp }
                flux.writeShort(duGenre.size)
                for (membre in duGenre) {
                    // ACC_PUBLIC (jamais SYNTHETIC/BRIDGE) ; ACC_STATIC pour
                    // les membres statiques (vague 2d — INSTANCE des objets,
                    // compagnons, const) — 0x0009 ; ACC_ABSTRACT pour les
                    // méthodes d'interface (vague 2f) — 0x0401.
                    var acces = 0x0001
                    if (membre.estStatique) acces = acces or 0x0008
                    if (membre.estAbstrait) acces = acces or 0x0400
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
