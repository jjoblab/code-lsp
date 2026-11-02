package jo.codeide.lsp.java.projetandroid

import java.io.ByteArrayOutputStream

/**
 * Écrivain de fichiers de classe BINAIRES « light » (vague V-B — ADR 0021
 * option 1 : « fabrique de déclarations synthétiques », API-SEULE, pas de
 * corps). Généralisation de la fabrique d'intrinsèques (fiche 042) aux
 * champs et méthodes : R et ses imbriquées, BuildConfig, les classes
 * `*Binding`, les facades Kotlin.
 *
 * Les octets alimentent UNIQUEMENT le lecteur de classes d'ecj
 * (`ClassFileReader.read(byte[], String)`) — jamais la JVM : seule la
 * validité du FORMAT importe (JVM Spec §4), version 52 (Java 8, miroir de
 * la fiche 042). Les corps émis sont des REPLI honnêtes (renvoi de
 * défauts) : l'API suffit à la résolution, aux diagnostics et à la
 * complétion — un appel à `inflate(...)` d'une classe light est un
 * comportement d'exécution que seul le build réel sait produire.
 *
 * Ce que l'écrivain sait émettre :
 *  - drapeaux `public`, `final`, `abstract` ;
 *  - super-classe et interfaces explicites ;
 *  - champs statiques (finaux ou non) avec `ConstantValue` (int, boolean,
 *    String) ou descripteur de référence ;
 *  - méthodes statiques ou d'instance, avec ou sans corps de repli ;
 *  - constructeurs (déléguant au super sans argument).
 *
 * Chaque classe imbriquée est un FICHIER SÉPARÉ (`R$layout`) — la
 * convention du format de classe, pas un choix.
 *
 * La table de constantes est allouée SÉQUENTIELLEMENT (chaque entrée
 * réserve son index à l'ajout, les dépendances d'abord) et dédupliquée par
 * clé — l'écriture finale est une simple itération dans l'ordre des index.
 */
internal class EcrivainClasseLegere private constructor(
    private val fqcn: String,
) {

    /** Une entrée de la table de constantes. */
    private sealed interface Entree {
        val cle: String

        data class Utf8(override val cle: String, val valeur: String) : Entree

        data class Entier(override val cle: String, val valeur: Int) : Entree

        data class Chaine(override val cle: String, val idxUtf8: Int) : Entree

        data class Classe(override val cle: String, val idxUtf8: Int) : Entree

        data class NameAndType(
            override val cle: String,
            val idxNom: Int,
            val idxDesc: Int,
        ) : Entree

        data class Methodref(
            override val cle: String,
            val idxClasse: Int,
            val idxNat: Int,
        ) : Entree
    }

    /** Constantes du format de classe utilisées ici. */
    private object Cst {
        const val UTF8 = 1
        const val INTEGER = 3
        const val STRING = 8
        const val CLASS = 7
        const val METHODREF = 10
        const val NAME_AND_TYPE = 12
        const val ACC_PUBLIC = 0x0001
        const val ACC_PRIVATE = 0x0002
        const val ACC_FINAL = 0x0010
        const val ACC_SUPER = 0x0020
        const val ACC_STATIC = 0x0008
        const val ACC_ABSTRACT = 0x0400
        const val ACC_SYNTHETIC = 0x1000
    }

    /** Un champ à émettre. */
    internal data class Champ(
        val nom: String,
        val descripteur: String,
        val statique: Boolean,
        val final: Boolean,
        val visibilite: Int,
        /** Valeur de constante (Int/Boolean/String) — null : pas d'attribut. */
        val constante: Any?,
    )

    /** Une méthode à émettre. */
    internal data class Methode(
        val nom: String,
        val descripteur: String,
        val statique: Boolean,
        val abstraite: Boolean,
        val visibilite: Int,
        /** Constructeur déléguant au super () — émet un vrai code. */
        val constructeur: Boolean,
    )

    /** La table de constantes ordonnée par index. */
    private val entrees = ArrayList<Entree>()

    /** Déduplication par clé. */
    private val parCle = HashMap<String, Int>()

    private val champs = mutableListOf<Champ>()
    private val methodes = mutableListOf<Methode>()
    private val interfaces = mutableListOf<String>()

    /** Entrées InnerClasses à émettre (l'imbriquée ET l'englobante portent
     *  l'attribut — convention javac, lue par le ClassFileReader d'ecj). */
    private val imbriquees = mutableListOf<Imbriquee>()

    /** Une classe interne déclarée par l'attribut InnerClasses. */
    private class Imbriquee(
        val fqcnInterne: String,
        val fqcnExterne: String,
        val nomSimple: String,
        val drapeaux: Int,
    )

    private var superFqcn: String = "java.lang.Object"
    private var final = false
    private var abstraite = false

    /** Fabrique un écrivain pour un FQCN (points). */
    internal companion object {
        fun pour(fqcn: String): EcrivainClasseLegere = EcrivainClasseLegere(fqcn)

        /** Descripteur d'un type par FQCN/notation (`int` → `I`). */
        fun descripteurDe(fqcn: String): String = when (fqcn) {
            "int", "I" -> "I"
            "boolean", "Z" -> "Z"
            "long", "J" -> "J"
            "double", "D" -> "D"
            "float", "F" -> "F"
            "short", "S" -> "S"
            "byte", "B" -> "B"
            "char", "C" -> "C"
            "void", "V" -> "V"
            "java.lang.String", "String" -> "Ljava/lang/String;"
            else -> "L" + fqcn.replace('.', '/') + ";"
        }
    }

    /** Pose la super-classe (FQCN en points). */
    fun superClasse(fqcn: String) {
        superFqcn = fqcn
    }

    /** Ajoute une interface implémentée (FQCN en points). */
    fun interfaceImplantee(fqcn: String) {
        interfaces += fqcn
    }

    /** Marque la classe finale. */
    fun finale() {
        final = true
    }

    /** Marque la classe abstraite (les DataBinding le sont). */
    fun abstraite() {
        abstraite = true
    }

    /** Ajoute un champ (les constantes int/String/boolean sont émises). */
    fun champ(
        nom: String,
        descripteur: String,
        statique: Boolean = true,
        finalChamp: Boolean = true,
        visibilite: Int = Cst.ACC_PUBLIC,
        constante: Any? = null,
    ) {
        champs += Champ(nom, descripteur, statique, finalChamp, visibilite, constante)
    }

    /** Ajoute une méthode. */
    fun methode(
        nom: String,
        descripteur: String,
        statique: Boolean = true,
        abstraiteMethode: Boolean = false,
        visibilite: Int = Cst.ACC_PUBLIC,
        constructeur: Boolean = false,
    ) {
        methodes += Methode(nom, descripteur, statique, abstraiteMethode, visibilite, constructeur)
    }

    /** Constructeur privé sans argument (R, R$layout, *Binding). */
    fun constructeurPrive() {
        methodes += Methode("<init>", "()V", statique = false, abstraite = false, visibilite = Cst.ACC_PRIVATE, constructeur = true)
    }

    /**
     * Déclare une classe INTERNE (attribut `InnerClasses` — JVM Spec §4.7.6)
     * : SANS lui, ecj ignore que `R$layout` est un membre de `R` et refuse
     * `R.layout` (« cannot be resolved or is not a field »). L'entrée se
     * pose sur l'ENGLOBANTE et sur l'IMBRIQUÉE elle-même (convention javac).
     *
     * @param nomSimple le nom de l'imbriquée (`layout`).
     * @param fqcnExterne le FQCN de l'englobante (`ns.R`).
     * @param drapeaux les drapeaux de l'imbriquée (public static final…).
     */
    fun imbriquee(
        nomSimple: String,
        fqcnExterne: String,
        drapeaux: Int = Cst.ACC_PUBLIC or Cst.ACC_STATIC or Cst.ACC_FINAL,
    ) {
        imbriquees +=
            Imbriquee(
                fqcnInterne = "$fqcnExterne\$$nomSimple",
                fqcnExterne = fqcnExterne,
                nomSimple = nomSimple,
                drapeaux = drapeaux,
            )
    }

    // ------------------------------------------------------------------ //
    // Table de constantes (allocation séquentielle dédupliquée)
    // ------------------------------------------------------------------ //

    private fun reserver(entree: Entree): Int {
        parCle[entree.cle]?.let { return it }
        entrees += entree
        val index = entrees.size // la table commence à 1
        parCle[entree.cle] = index
        return index
    }

    private fun indexUtf8(valeur: String): Int =
        reserver(Entree.Utf8("u:$valeur", valeur))

    private fun indexClasse(fqcn: String): Int {
        val interne = fqcn.replace('.', '/')
        val idxNom = indexUtf8(interne)
        return reserver(Entree.Classe("c:$interne", idxNom))
    }

    private fun indexNameAndType(nom: String, descripteur: String): Int {
        val idxNom = indexUtf8(nom)
        val idxDesc = indexUtf8(descripteur)
        return reserver(Entree.NameAndType("n:$nom:$descripteur", idxNom, idxDesc))
    }

    private fun indexMethodref(classe: String, nom: String, descripteur: String): Int {
        val idxClasse = indexClasse(classe)
        val idxNat = indexNameAndType(nom, descripteur)
        return reserver(Entree.Methodref("m:$classe:$nom:$descripteur", idxClasse, idxNat))
    }

    private fun indexEntier(valeur: Int): Int =
        reserver(Entree.Entier("i:$valeur", valeur))

    private fun indexChaine(valeur: String): Int {
        val idxUtf8 = indexUtf8(valeur)
        return reserver(Entree.Chaine("s:$valeur", idxUtf8))
    }

    // ------------------------------------------------------------------ //
    // Émission
    // ------------------------------------------------------------------ //

    /** Émet les octets du fichier de classe. */
    fun octets(): ByteArray {
        // Résolution de TOUTE la table AVANT l'écriture : les indices
        // référencés par les attributs ConstantValue doivent exister au
        // moment où le compteur de pool est écrit (aucune allocation
        // pendant l'émission).
        val idxThis = indexClasse(fqcn)
        val idxSuper = indexClasse(superFqcn)
        val idxInterfaces = interfaces.map { indexClasse(it) }
        for (champ in champs) {
            indexUtf8(champ.nom)
            indexUtf8(champ.descripteur)
            if (champ.constante != null) indexConstante(champ.constante)
        }
        for (methode in methodes) {
            indexUtf8(methode.nom)
            indexUtf8(methode.descripteur)
        }
        val codes = methodes.filter { !it.abstraite }.associateWith { corpsDeRepli(it) }
        val idxCode = indexUtf8("Code")
        val idxConstantValue = indexUtf8("ConstantValue")
        val idxInnerClasses = indexUtf8("InnerClasses")
        val entreesImbriquees =
            imbriquees.map { imbriquee ->
                Quad(
                    indexClasse(imbriquee.fqcnInterne),
                    indexClasse(imbriquee.fqcnExterne),
                    indexUtf8(imbriquee.nomSimple),
                    imbriquee.drapeaux,
                )
            }

        val sortie = ByteArrayOutputStream(512)
        val w = SortieBigEndian(sortie)

        // En-tête.
        w.u4(0xCAFEBABE)
        w.u2(0)
        w.u2(52)
        w.u2(entrees.size + 1)

        // Table de constantes, dans l'ordre des index.
        for (entree in entrees) {
            when (entree) {
                is Entree.Utf8 -> {
                    w.u1(Cst.UTF8)
                    val octets = entree.valeur.toByteArray(Charsets.UTF_8)
                    w.u2(octets.size)
                    w.octets(octets)
                }
                is Entree.Entier -> {
                    w.u1(Cst.INTEGER)
                    w.u4(entree.valeur.toLong())
                }
                is Entree.Chaine -> {
                    w.u1(Cst.STRING)
                    w.u2(entree.idxUtf8)
                }
                is Entree.Classe -> {
                    w.u1(Cst.CLASS)
                    w.u2(entree.idxUtf8)
                }
                is Entree.NameAndType -> {
                    w.u1(Cst.NAME_AND_TYPE)
                    w.u2(entree.idxNom)
                    w.u2(entree.idxDesc)
                }
                is Entree.Methodref -> {
                    w.u1(Cst.METHODREF)
                    w.u2(entree.idxClasse)
                    w.u2(entree.idxNat)
                }
            }
        }

        // Corps de la classe.
        var drapeaux = Cst.ACC_PUBLIC or Cst.ACC_SUPER or Cst.ACC_SYNTHETIC
        if (final) drapeaux = drapeaux or Cst.ACC_FINAL
        if (abstraite) drapeaux = drapeaux or Cst.ACC_ABSTRACT
        w.u2(drapeaux)
        w.u2(idxThis)
        w.u2(idxSuper)
        w.u2(interfaces.size)
        for (index in idxInterfaces) w.u2(index)

        // Champs.
        w.u2(champs.size)
        for (champ in champs) {
            var acces = champ.visibilite
            if (champ.statique) acces = acces or Cst.ACC_STATIC
            if (champ.final) acces = acces or Cst.ACC_FINAL
            w.u2(acces)
            w.u2(indexUtf8(champ.nom))
            w.u2(indexUtf8(champ.descripteur))
            if (champ.constante != null) {
                val idxConstante = indexConstante(champ.constante)
                w.u2(1)
                w.u2(idxConstantValue)
                w.u4(2L)
                w.u2(idxConstante)
            } else {
                w.u2(0)
            }
        }

        // Méthodes.
        w.u2(methodes.size)
        for (methode in methodes) {
            var acces = methode.visibilite
            if (methode.statique) acces = acces or Cst.ACC_STATIC
            if (methode.abstraite) acces = acces or Cst.ACC_ABSTRACT
            w.u2(acces)
            w.u2(indexUtf8(methode.nom))
            w.u2(indexUtf8(methode.descripteur))
            if (methode.abstraite) {
                w.u2(0)
                continue
            }
            val code = codes[methode] ?: octetsDe(0xb1)
            w.u2(1)
            w.u2(idxCode)
            w.u4((2 + 2 + 4 + code.size + 2 + 2).toLong())
            w.u2(1) // max_stack (aload_0/invokespecial ou aconst_null/areturn)
            w.u2(1) // max_locals
            w.u4(code.size.toLong())
            w.octets(code)
            w.u2(0) // exceptions
            w.u2(0) // attributs de Code
        }

        // Attributs de classe : InnerClasses quand des imbriquées sont posées.
        if (entreesImbriquees.isEmpty()) {
            w.u2(0)
        } else {
            w.u2(1)
            w.u2(idxInnerClasses)
            w.u4((2 + 8L * entreesImbriquees.size))
            w.u2(entreesImbriquees.size)
            for (entree in entreesImbriquees) {
                w.u2(entree.premier)
                w.u2(entree.second)
                w.u2(entree.troisieme)
                w.u2(entree.quatrieme)
            }
        }
        return sortie.toByteArray()
    }

    /** Quadruplet d'index (InnerClasses). */
    private class Quad(
        val premier: Int,
        val second: Int,
        val troisieme: Int,
        val quatrieme: Int,
    )

    /** L'index de constante d'une valeur de champ. */
    private fun indexConstante(valeur: Any): Int = when (valeur) {
        is Int -> indexEntier(valeur)
        is Boolean -> indexEntier(if (valeur) 1 else 0)
        is String -> indexChaine(valeur)
        else -> throw IllegalArgumentException("constante non émissible : $valeur")
    }

    /** Corps de repli d'une méthode (JVM valide, valeur par défaut). */
    private fun corpsDeRepli(methode: Methode): ByteArray {
        if (methode.constructeur) {
            // aload_0 ; invokespecial super.<init>()V ; return
            val idxMethodref = indexMethodref(superFqcn, "<init>", "()V")
            return octetsDe(0x2a, 0xb7, (idxMethodref ushr 8) and 0xff, idxMethodref and 0xff, 0xb1)
        }
        val retour = methode.descripteur.substringAfterLast(')')
        return when (retour) {
            "V" -> octetsDe(0xb1) // return
            "I", "Z", "B", "S", "C" -> octetsDe(0x03, 0xac) // iconst_0 ; ireturn
            "J" -> octetsDe(0x0a, 0xad) // lconst_0 ; lreturn
            "F" -> octetsDe(0x0a, 0xae) // fconst_0 ; freturn
            "D" -> octetsDe(0x0a, 0xaf) // dconst_0 ; dreturn
            else -> octetsDe(0x01, 0xb0) // aconst_null ; areturn
        }
    }

    /** [ByteArray] depuis des octets donnés en Int (littéraux 0x… lisibles). */
    private fun octetsDe(vararg valeurs: Int): ByteArray = ByteArray(valeurs.size) { valeurs[it].toByte() }

    /** Petit écrivain gros-boutiste (miroir de la fiche 042). */
    private class SortieBigEndian(private val sortie: ByteArrayOutputStream) {
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
