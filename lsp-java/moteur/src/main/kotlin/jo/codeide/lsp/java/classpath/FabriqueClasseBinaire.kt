package jo.codeide.lsp.java.classpath

import java.io.ByteArrayOutputStream

/**
 * Fabrique de fichiers de classe binaires minimaux, émise octet par octet
 * (étage N1, cahier fiche 042).
 *
 * Le moteur embarque des **stubs des intrinsèques du compilateur**
 * (`java.lang.invoke.StringConcatFactory`, `java.lang.runtime.ObjectMethods`,
 * `java.lang.runtime.SwitchBootstraps`) pour refermer les hiérarchies
 * binaires des builds Gradle relus sans image jrt. Ces octets alimentent le
 * lecteur de classes d'ecj (`ClassFileReader.read(byte[], String)`) : ils ne
 * sont **jamais chargés dans la JVM**, seule la validité du format importe.
 *
 * Le format émis est le format de classe minimal documenté (JVM Spec §4) :
 * `CA FE BA BE`, version **52** (Java 8 — conforme au contrat de la fiche :
 * versions mineure et majeure haute à 0, majeure 52), une classe `public`
 * étendant `java.lang.Object` ou un super explicite, sans champs, avec un
 * constructeur public sans argument optionnel (`aload_0 ; invokespecial
 * java/lang/Object.<init>()V ; return`).
 *
 * La fabrique sert aussi de **fabricant de fixtures** côté tests (jar
 * `batisse.Biblio` étendant un intrinsèque, fiche 042) : un compilateur
 * système (javac) n'est pas requis — les octets sont produits par le code,
 * déterministes et reproductibles sur toute machine.
 */
public object FabriqueClasseBinaire {

    private const val CONSTANT_UTF8: Int = 1
    private const val CONSTANT_CLASS: Int = 7
    private const val CONSTANT_METHODREF: Int = 10
    private const val CONSTANT_NAME_AND_TYPE: Int = 12

    private const val ACC_PUBLIC: Int = 0x0001
    private const val ACC_SUPER: Int = 0x0020

    /**
     * Émet les octets d'une classe publique vide.
     *
     * @param fqcn nom entièrement qualifié de la classe (points) ; le
     *   descripteur interne est converti en slashes.
     * @param superFqcn super-classe déclarée (défaut `java.lang.Object`).
     * @param constructeurPublic si vrai, émet un constructeur public sans
     *   argument déléguant au super (nécessaire pour éviter le diagnostic
     *   « Implicit super constructor is undefined » quand la fixture est
     *   étendue par une source, cf. fiche 042).
     * @return les octets du fichier de classe, version 52.
     */
    @JvmStatic
    public fun octets(fqcn: String, superFqcn: String = "java.lang.Object", constructeurPublic: Boolean = true): ByteArray {
        val sortie = ByteArrayOutputStream(160)
        val tampon = DataOutputStreamFrancais(sortie)

        // --- en-tête ---
        tampon.u4(0xCAFEBABE)
        tampon.u2(0) // version mineure
        tampon.u2(52) // version majeure = Java 8 (contrat fiche 042)

        // --- table de constantes ---
        val utf8s = ArrayList<String>()
        fun indexUtf8(valeur: String): Int {
            utf8s.add(valeur)
            return utf8s.size // indices commencent à 1
        }
        val nomInterne = fqcn.replace('.', '/')
        val superInterne = superFqcn.replace('.', '/')

        val idxClasse = indexUtf8(nomInterne) // → CONSTANT_Class
        val idxSuper = indexUtf8(superInterne) // → CONSTANT_Class
        val idxInit = indexUtf8("<init>")
        val idxDescVide = indexUtf8("()V")
        val idxObject = indexUtf8("java/lang/Object")
        val idxCode = indexUtf8("Code")

        // La table est écrite en une passe : UTF8 (dans l'ordre d'enregistrement,
        // indices 1..utf8s.size), puis Class×2, Methodref, NameAndType.
        val nbConstantes = utf8s.size + 4
        tampon.u2(nbConstantes + 1)
        for (valeur in utf8s) {
            val octets = valeur.toByteArray(Charsets.UTF_8)
            tampon.u1(CONSTANT_UTF8)
            tampon.u2(octets.size)
            tampon.octets(octets)
        }
        val base = utf8s.size
        val idxClassCetteClasse = base + 1
        val idxClassSuper = base + 2
        val idxMethodref = base + 3
        val idxNameAndType = base + 4
        tampon.u1(CONSTANT_CLASS)
        tampon.u2(idxClasse)
        tampon.u1(CONSTANT_CLASS)
        tampon.u2(idxSuper)
        tampon.u1(CONSTANT_METHODREF)
        tampon.u2(idxObject)
        tampon.u2(idxNameAndType)
        tampon.u1(CONSTANT_NAME_AND_TYPE)
        tampon.u2(idxInit)
        tampon.u2(idxDescVide)

        // --- corps de la classe ---
        tampon.u2(ACC_PUBLIC or ACC_SUPER)
        tampon.u2(idxClassCetteClasse)
        tampon.u2(idxClassSuper)
        tampon.u2(0) // interfaces
        tampon.u2(0) // champs
        if (constructeurPublic) {
            tampon.u2(1) // une méthode
            tampon.u2(ACC_PUBLIC)
            tampon.u2(idxInit)
            tampon.u2(idxDescVide)
            // attribut Code : max_stack 1, max_locals 1, aload_0, invokespecial, return
            val longueurCode = 2 + 2 + 4 + 5 + 2 + 2 // 17
            tampon.u2(1) // un attribut
            tampon.u2(idxCode)
            tampon.u4(longueurCode.toLong())
            tampon.u2(1) // max_stack
            tampon.u2(1) // max_locals
            tampon.u4(5) // longueur du code
            tampon.u1(0x2a) // aload_0
            tampon.u1(0xb7) // invokespecial
            tampon.u2(idxMethodref)
            tampon.u1(0xb1) // return
            tampon.u2(0) // table d'exceptions
            tampon.u2(0) // attributs de Code
        } else {
            tampon.u2(0) // aucune méthode
        }
        tampon.u2(0) // attributs de classe
        return sortie.toByteArray()
    }

    /** Petit écrivain gros-boutiste sans dépendance (DataOutputStream a une API throwing). */
    private class DataOutputStreamFrancais(private val sortie: ByteArrayOutputStream) {
        fun u1(valeur: Int) {
            sortie.write(valeur and 0xff)
        }

        fun u2(valeur: Int) {
            sortie.write((valeur ushr 8) and 0xff)
            sortie.write(valeur and 0xff)
        }

        fun u4(valeur: Long) {
            sortie.write(((valeur ushr 24) and 0xff).toInt())
            sortie.write(((valeur ushr 16) and 0xff).toInt())
            sortie.write(((valeur ushr 8) and 0xff).toInt())
            sortie.write((valeur and 0xff).toInt())
        }

        fun octets(octets: ByteArray) {
            sortie.write(octets, 0, octets.size)
        }
    }
}
