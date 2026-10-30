package jo.codeide.lsp.kotlin.classpath

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Fiche 004 — LecteurClassBrutTest : tests du lecteur de class-files BRUT
 * (K3) — deux vérités :
 *  - une classe SYNTHÉTIQUE fabriquée octet par octet (contrôle total du
 *    format : super, interfaces, absence de @Metadata) ;
 *  - une classe RÉELLE du kotlin-stdlib du classpath de test
 *    (`kotlin.text.StringsKt` — facade Kotlin avec @Metadata ;
 *    `kotlin.jvm.internal.Intrinsics` — classe Java, négatif honnête).
 *
 * Chaque comportement porte le nom exact du test source (identifiant de
 * traçabilité du cahier docs/cahier-specifications-kotlin/).
 */
class Fiche004LecteurClassBrutTest {

    /** Les octets d'une ressource du classpath de test (jar stdlib réel). */
    private fun octetsRessource(chemin: String): ByteArray =
        checkNotNull(
            java.lang.ClassLoader.getSystemResourceAsStream(chemin)?.use { flux -> flux.readBytes() },
        ) { "ressource introuvable : $chemin" }

    @Test
    fun `classe-synthetique---identite,-super-et-interfaces-lues`() {
        val octets = fabriqueClasse("a.b.C", "x.y.Super", listOf("i.Iface", "j.Autre"))
        val infos = assertNotNull(LecteurClassBrut.lire(octets), "classe synthétique lisible")
        assertEquals("a.b.C", infos.fqcn)
        assertEquals("x.y.Super", infos.superFqcn)
        assertEquals(listOf("i.Iface", "j.Autre"), infos.interfaces)
        assertNull(infos.metadonnees, "aucune @Metadata dans une classe synthétique")
    }

    @Test
    fun `classe-synthetique-sans-super---super-null-(java-lang-Object)`() {
        val infos = assertNotNull(LecteurClassBrut.lire(fabriqueClasse("a.Racine", null, emptyList())))
        assertNull(infos.superFqcn, "super_class = 0 se lit null (java.lang.Object)")
        assertTrue(infos.interfaces.isEmpty())
    }

    @Test
    fun `octets-invalides---null,-jamais-d'exception-(D3)`() {
        assertNull(LecteurClassBrut.lire(ByteArray(0)))
        assertNull(LecteurClassBrut.lire(byteArrayOf(1, 2, 3, 4)))
        assertNull(LecteurClassBrut.lire("du texte".toByteArray()))
    }

    @Test
    fun `classe-Kotlin-reelle-du-stdlib---identite-et-metadata-reconstruite`() {
        // StringsKt : facade multi-fichiers RÉELLE (k=4, @Metadata avec
        // mv/k/xi/d1) — le PROPOS du lecteur : l'annotation est reconstruite
        // en instance kotlin.Metadata exploitable. La superclasse est
        // RÉELLE (la facade multi-fichiers étend sa dernière partie).
        val infos =
            assertNotNull(
                LecteurClassBrut.lire(octetsRessource("kotlin/text/StringsKt.class")),
                "classe réelle lisible",
            )
        assertEquals("kotlin.text.StringsKt", infos.fqcn)
        assertNotNull(infos.metadonnees, "@Metadata reconstruite")
        assertEquals("kotlin.text.StringsKt___StringsKt", infos.superFqcn)
    }

    @Test
    fun `classe-JAVA-reelle-du-stdlib---pas-de-metadata-(negatif-honnete)`() {
        // Intrinsics est une classe JAVA du stdlib (« Compiled from
        // Intrinsics.java », aucun @Metadata) : le lecteur rend
        // metadonnees = null, sans erreur.
        val infos =
            assertNotNull(
                LecteurClassBrut.lire(octetsRessource("kotlin/jvm/internal/Intrinsics.class")),
                "classe réelle lisible",
            )
        assertEquals("kotlin.jvm.internal.Intrinsics", infos.fqcn)
        assertNull(infos.metadonnees, "négatif honnête : une classe Java ne porte pas @Metadata")
    }

    /**
     * Fabrique une classe minimale VALIDE octet par octet (JLS §4.1) :
     * magie, versions, pool réduit aux paires {Utf8, Class} dans l'ordre
     * (this, super, interfaces), AUCUN champ/méthode/attribut — le
     * contrôle total du format sans compilateur.
     *
     * @param fqcn le nom pleinement qualifié de la classe ;
     * @param superFqcn la superclasse (`null` = super_class 0 =
     *        java.lang.Object) ;
     * @param interfaces les interfaces directes ;
     * @return les octets du fichier de classe.
     */
    private fun fabriqueClasse(
        fqcn: String,
        superFqcn: String?,
        interfaces: List<String>,
    ): ByteArray {
        val sortie = ByteArrayOutputStream()
        DataOutputStream(sortie).use { flux ->
            flux.writeInt(0xCAFEBABE.toInt())
            flux.writeShort(0) // minor_version
            flux.writeShort(52) // major_version (Java 8)
            val nbPaires = 1 + (if (superFqcn != null) 1 else 0) + interfaces.size
            flux.writeShort(nbPaires * 2 + 1) // constant_pool_count (+1 : index 0 inutilisé)

            /** Une entrée Utf8 (l'index de pool suit l'ordre d'écriture). */
            fun utf8(texte: String) {
                flux.writeByte(1)
                val octets = texte.toByteArray(Charsets.UTF_8)
                flux.writeShort(octets.size)
                flux.write(octets)
            }
            fun classe(nomUtf8: Int) {
                flux.writeByte(7)
                flux.writeShort(nomUtf8)
            }

            // L'index de pool suit strictement l'ordre d'écriture : chaque
            // Utf8 occupe 1 index, chaque Class en occupe 1.
            var index = 1
            val thisUtf8 = index
            utf8(fqcn.replace('.', '/'))
            index++
            classe(thisUtf8)
            index++
            var superClasse = 0
            if (superFqcn != null) {
                val superUtf8 = index
                utf8(superFqcn.replace('.', '/'))
                index++
                classe(superUtf8)
                index++
                superClasse = superUtf8 + 1
            }
            val ifaceClasses = mutableListOf<Int>()
            for (iface in interfaces) {
                val ifaceUtf8 = index
                utf8(iface.replace('.', '/'))
                index++
                classe(ifaceUtf8)
                index++
                ifaceClasses.add(ifaceUtf8 + 1)
            }
            flux.writeShort(0x0021) // access_flags : public super
            flux.writeShort(thisUtf8 + 1) // this_class
            flux.writeShort(superClasse) // super_class (0 = java.lang.Object)
            flux.writeShort(ifaceClasses.size)
            ifaceClasses.forEach { flux.writeShort(it) }
            flux.writeShort(0) // fields_count
            flux.writeShort(0) // methods_count
            flux.writeShort(0) // attributes_count
        }
        return sortie.toByteArray()
    }
}
