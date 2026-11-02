package jo.codeide.lsp.java.navigation

import jo.codeide.lsp.java.classpath.EnvironnementNoms
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Service de décompilation (étage N4 — cahier fiches 005/009) : le câble
 * « GO TO Definition sur cible binaire » côté SOURCE.
 *
 * Deux représentations acceptées pour un même FQCN (fiche 009) :
 *  1. la **source attachée** du JDK (`lib/src.zip`) — lue par le lecteur
 *     zip brut du classpath (aucune dépendance à `java.util.zip.ZipFile`) ;
 *  2. un **stub décompilé** minimal, dérivé des OCTETS de la classe (jar
 *     du classpath, image jrt, stub intrinsèque) par le lecteur de
 *     fichiers de classe d'ecj : en-tête « Decompiled from … », clause
 *     package, en-tête de type, et les méthodes publiques avec leurs
 *     paramètres désérialisés du descripteur JVM — le critère de qualité
 *     du cahier (« le stub doit au moins mentionner les méthodes
 *     publiques — put/get/size pour HashMap »).
 *
 * La sonde [estTypeBinaire] (fiche 005 : `isBinaryType`) identifie les
 * types BINAIRES : tout FQCN résolu par l'annuaire hors des SOURCES du
 * projet (jar, jrt, stub intrinsèque, classe LIGHT synthétique — V-B :
 * `R`/`BuildConfig`/`Binding` fabriqués) ; null, vide, inconnu ou source
 * projet → faux.
 *
 * Sortie honnête : null pour un FQCN null/vide (sortie anticipée, AUCUN
 * JDK requis — fiche 009) ou inconnu de l'environnement.
 *
 * @param environnement fournisseur de l'environnement de noms du moteur
 *   (localisation + octets des classes).
 * @param jdkHomeExplicite home JDK explicite du moteur (null = runtime
 *   courant — la recherche de `lib/src.zip` remonte alors `java.home`).
 */
public class ServiceDecompilation(
    private val environnement: () -> EnvironnementNoms,
    private val jdkHomeExplicite: Path?,
) {

    /**
     * La source d'un type binaire (fiche 009 : `decompiledSource`) —
     * attachée si le JDK porte `lib/src.zip`, stub décompilé sinon.
     *
     * @param fqcn nom entièrement qualifié en points (null/vide → null,
     *   sans toucher à l'environnement).
     * @return le texte Java du type, ou null (FQCN inconnu).
     */
    public fun sourceDecompilee(fqcn: String?): String? {
        if (fqcn.isNullOrEmpty()) return null
        sourceAttachee(fqcn)?.let { return it }
        return stubDecompile(fqcn)
    }

    /**
     * Sonde de type binaire (fiche 005 : `isBinaryType`).
     *
     * @param fqcn le FQCN testé (null/vide → faux).
     * @return vrai ssi le type existe et vit hors des sources du projet.
     */
    public fun estTypeBinaire(fqcn: String?): Boolean {
        if (fqcn.isNullOrEmpty()) return false
        return when (localiser(fqcn)) {
            EnvironnementNoms.Localisation.Jar,
            EnvironnementNoms.Localisation.Jrt,
            EnvironnementNoms.Localisation.Stub,
            EnvironnementNoms.Localisation.Synthetique,
            -> true
            else -> false
        }
    }

    /** Localisation défensive d'un FQCN par l'annuaire du moteur. */
    private fun localiser(fqcn: String): EnvironnementNoms.Localisation? = try {
        environnement().trouverFqcn(fqcn)
    } catch (_: Exception) {
        null
    }

    // ------------------------------------------------------------------ //
    // Source attachée (lib/src.zip du JDK)
    // ------------------------------------------------------------------ //

    /** Le home JDK effectif (explicite, sinon `java.home`). */
    private fun homeEffectif(): Path? = jdkHomeExplicite ?: runCatching {
        Paths.get(System.getProperty("java.home"))
    }.getOrNull()

    /** Le chemin de `lib/src.zip` si présent (null sinon — pas d'exception). */
    private fun srcZip(): Path? {
        val home = homeEffectif() ?: return null
        val candidat = home.resolve("lib").resolve("src.zip")
        return if (Files.isRegularFile(candidat)) candidat else null
    }

    /** La source attachée d'un FQCN depuis `lib/src.zip` (null si absente). */
    private fun sourceAttachee(fqcn: String): String? {
        val zip = srcZip() ?: return null
        val entree = fqcn.replace('.', '/') + ".java"
        return try {
            String(jo.codeide.lsp.java.classpath.LecteurZipBrut.readSingle(zip, entree), Charsets.UTF_8)
        } catch (_: Exception) {
            null // zip absent/tronqué ou entrée inconnue : stub ensuite
        }
    }

    // ------------------------------------------------------------------ //
    // Stub décompilé (octets de la classe)
    // ------------------------------------------------------------------ //

    /** Les octets de la classe (jar, jrt, stub intrinsèque, light V-B — null sinon). */
    private fun octetsDe(fqcn: String): ByteArray? = try {
        val env = environnement()
        env.cacheLocalisation.octetsPour(fqcn)
            ?: env.accesJrt?.octets(fqcn)
            ?: env.synthetiques.firstNotNullOfOrNull { fournisseur ->
                runCatching { fournisseur.octetsPour(fqcn) }.getOrNull()
            }
    } catch (_: Exception) {
        null
    }

    /** Le stub décompilé d'un FQCN (null si les octets sont introuvables). */
    private fun stubDecompile(fqcn: String): String? {
        val octets = octetsDe(fqcn) ?: return null
        val lecteur = try {
            org.eclipse.jdt.internal.compiler.classfmt.ClassFileReader.read(octets, fqcn)
        } catch (_: Exception) {
            return null
        }
        return FormatterStub.formatter(fqcn, lecteur)
    }
}

/**
 * Formateur du stub décompilé (étage N4) : la mise en page MINIMALE du
 * cahier — en-tête « Decompiled from … », clause package, en-tête de
 * type (modificateurs + nom simple + super + interfaces), puis une
 * méthode publique par ligne avec les paramètres désérialisés du
 * descripteur JVM. Le corps de chaque méthode est un `throw` unique :
 * le stub sert la NAVIGATION (noms et signatures), jamais l'exécution.
 */
internal object FormatterStub {

    /** Drapeaux d'accès JVM retenus pour l'en-tête de type. */
    private const val ACC_PUBLIC: Int = 0x0001
    private const val ACC_FINAL: Int = 0x0010
    private const val ACC_INTERFACE: Int = 0x0200
    private const val ACC_ABSTRACT: Int = 0x0400

    /**
     * Met en page le stub d'un type lu dans son fichier de classe.
     *
     * @param fqcn le FQN du type (en points).
     * @param lecteur le lecteur de classe rempli.
     * @return le texte Java du stub.
     */
    fun formatter(fqcn: String, lecteur: org.eclipse.jdt.internal.compiler.classfmt.ClassFileReader): String {
        val nomSimple = fqcn.substringAfterLast('.')
        val paquet = fqcn.substringBeforeLast('.', "")
        val sb = StringBuilder()
        sb.append("/*\n * Decompiled from ").append(fqcn).append("\n */\n")
        if (paquet.isNotEmpty()) sb.append("package ").append(paquet).append(";\n\n")

        // En-tête de type : modificateurs, nom, super, interfaces.
        val drapeaux = runCatching { lecteur.getModifiers() }.getOrDefault(ACC_PUBLIC)
        val motsCles = ArrayList<String>()
        if (drapeaux and ACC_PUBLIC != 0) motsCles.add("public")
        if (drapeaux and ACC_ABSTRACT != 0 && drapeaux and ACC_INTERFACE == 0) motsCles.add("abstract")
        if (drapeaux and ACC_FINAL != 0) motsCles.add("final")
        val motType = if (drapeaux and ACC_INTERFACE != 0) "interface" else "class"
        sb.append(motsCles.joinToString(" ")).append(if (motsCles.isEmpty()) "" else " ")
        sb.append(motType).append(' ').append(nomSimple)

        val superNom = runCatching { lecteur.getSuperclassName() }.getOrNull()
        val interfaces = runCatching { lecteur.getInterfaceNames() }.getOrNull()
        if (superNom != null && String(superNom) != "java/lang/Object") {
            sb.append(" extends ").append(DescripteurJvm.lisible(String(superNom)))
        }
        if (!interfaces.isNullOrEmpty()) {
            sb.append(" implements ").append(interfaces.joinToString(", ") { DescripteurJvm.lisible(String(it)) })
        }
        sb.append(" {\n")

        // Méthodes publiques (le critère du cahier : put/get/size cités).
        val methodes = runCatching { lecteur.methods }.getOrNull() ?: emptyArray()
        var indice = 0
        for (methode in methodes) {
            if (methode == null) continue
            val drapeauxM = runCatching { methode.getModifiers() }.getOrDefault(0)
            if (drapeauxM and ACC_PUBLIC == 0) continue
            if (runCatching { methode.isClinit() }.getOrDefault(false)) continue
            val selecteur = runCatching { String(methode.getSelector()) }.getOrNull() ?: continue
            val descripteur = runCatching { String(methode.getMethodDescriptor()) }.getOrNull() ?: continue
            val estConstructeur = runCatching { methode.isConstructor() }.getOrDefault(false)
            val nomAffiche = if (estConstructeur) nomSimple else selecteur
            val retour = if (estConstructeur) "" else DescripteurJvm.retour(descripteur) + " "
            val parametres = DescripteurJvm.parametres(descripteur)
                .mapIndexed { i, type -> "$type p$i" }
                .joinToString(", ")
            val modificateurs = buildString {
                append("    public")
                if (drapeauxM and 0x0008 != 0) append(" static")
                if (drapeauxM and 0x0400 != 0 && drapeaux and ACC_INTERFACE == 0) append(" abstract")
            }
            if (drapeauxM and 0x0400 != 0) {
                sb.append(modificateurs).append(' ').append(retour).append(nomAffiche)
                    .append('(').append(parametres).append(");\n")
            } else {
                sb.append(modificateurs).append(' ').append(retour).append(nomAffiche)
                    .append('(').append(parametres).append(") {\n")
                    .append("        throw new UnsupportedOperationException(\"stub décompilé — navigation seulement\");\n")
                    .append("    }\n")
            }
            indice++
        }
        if (indice == 0) {
            sb.append("    // aucune méthode publique lue dans le fichier de classe\n")
        }
        sb.append("}\n")
        return sb.toString()
    }
}

/**
 * Désérialiseur minimal de descripteurs JVM (étage N4) : `(Ljava/lang/Object;I)V`
 * → paramètres `[java.lang.Object, int]`, retour `void`. Les tableaux
 * rendent leur forme Java (`int[]`) ; les classes leur FQN en points.
 */
internal object DescripteurJvm {

    /** Les types des paramètres d'un descripteur de méthode. */
    fun parametres(descripteur: String): List<String> {
        val ouverture = descripteur.indexOf('(')
        val fermeture = descripteur.indexOf(')')
        if (ouverture < 0 || fermeture < ouverture) return emptyList()
        var i = ouverture + 1
        val resultat = ArrayList<String>()
        while (i < fermeture) {
            val lecture = lireType(descripteur, i) ?: return emptyList()
            resultat.add(lecture.first)
            i = lecture.second
        }
        return resultat
    }

    /** Le type de retour d'un descripteur de méthode. */
    fun retour(descripteur: String): String {
        val fermeture = descripteur.indexOf(')')
        if (fermeture < 0) return "void"
        return lireType(descripteur, fermeture + 1)?.first ?: "void"
    }

    /** Nom Java lisible d'un nom interne de classe (`java/lang/String` → `java.lang.String`). */
    fun lisible(nomInterne: String): String = nomInterne.replace('/', '.')

    /** Lit un type à la position [debut] : (texte, position suivante) ou null. */
    private fun lireType(descripteur: String, debut: Int): Pair<String, Int>? {
        var i = debut
        val profondeur = StringBuilder()
        while (i < descripteur.length && descripteur[i] == '[') {
            profondeur.append("[]")
            i++
        }
        if (i >= descripteur.length) return null
        val base = when (descripteur[i]) {
            'B' -> "byte"
            'C' -> "char"
            'D' -> "double"
            'F' -> "float"
            'I' -> "int"
            'J' -> "long"
            'S' -> "short"
            'Z' -> "boolean"
            'V' -> "void"
            'L' -> {
                val fin = descripteur.indexOf(';', i)
                if (fin < 0) return null
                val fqn = descripteur.substring(i + 1, fin).replace('/', '.')
                i = fin
                fqn
            }
            else -> return null
        }
        return base + profondeur.toString() to i + 1
    }
}
