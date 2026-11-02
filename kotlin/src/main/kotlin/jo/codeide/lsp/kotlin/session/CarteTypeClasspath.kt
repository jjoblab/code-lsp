// CodeIDE — fiche structurelle d'un type du classpath (module :kotlin, étage
// 6 vague 6c). Code NEUF jjoblab (contrat : fiche 018 du cahier
// docs/cahier-specifications-kotlin/ — chantier P1-D « documentation du
// classpath au survol » : genre, héritage direct, interfaces, constantes
// d'enum, champs publics — prouvé par la lecture brute du `.class`, les
// plafonds d'affichage et la dégradation honnête du binaire à hiérarchie
// illisible).
package jo.codeide.lsp.kotlin.session

import jo.codeide.lsp.kotlin.api.SurvolKotlin
import jo.codeide.lsp.kotlin.resolve.DescripteursJvm
import jo.codeide.lsp.kotlin.resolve.MembresDeclaresJvm
import jo.codeide.lsp.kotlin.resolve.VueJvmMembres

/**
 * La FICHE STRUCTURELLE d'un type du CLASSPATH au survol (fiche 018) : le
 * genre (`public class/interface/enum/object`), l'héritage direct
 * (`extends` — JAMAIS pour une interface, le `java.lang.Enum` des enums
 * MASQUÉ, `java.lang.Object` omis), la section `Interfaces :` (les
 * interfaces directes, FQCN écrits), les `Constantes :` d'un enum et les
 * `Champs publics :` (le marqueur `INSTANCE` d'un objet exclu — il est le
 * genre, pas un champ ; les synthétiques `$` et les privés exclus).
 *
 * ## Les plafonds d'affichage (fiche 018)
 *
 * Huit champs et vingt constantes au plus, l'ellipse « … » clôt la liste
 * tronquée — la fiche borne son rendu, jamais le lecteur ne scrolle une
 * table de membre.
 *
 * ## La dégradation honnête (D3)
 *
 * Un binaire à HIÉRARCHIE illisible (référence de superclasse cassée dans
 * le pool) tombe sur le genre et les champs : l'héritage et les interfaces
 * se taisent — JAMAIS une ligne inventée. Un type absent des entrées ne
 * rend RIEN (le survol reste muet, [MembresDeclaresJvm] absent).
 */
internal object CarteTypeClasspath {

    /** ACC_PUBLIC (JLS table 4.1-B). */
    private const val ACC_PUBLIC: Int = 0x0001

    /** ACC_FINAL — le marqueur des objets Kotlin et des constantes. */
    private const val ACC_FINAL: Int = 0x0010

    /** ACC_STATIC — les constantes d'enum et le marqueur INSTANCE. */
    private const val ACC_STATIC: Int = 0x0008

    /** ACC_INTERFACE (0x0200). */
    private const val ACC_INTERFACE: Int = 0x0200

    /** ACC_ENUM (0x4000). */
    private const val ACC_ENUM: Int = 0x4000

    /** Le plafond des CHAMPS rendus (fiche 018). */
    private const val PLAFOND_CHAMPS: Int = 8

    /** Le plafond des CONSTANTES rendues (fiche 018). */
    private const val PLAFOND_CONSTANTES: Int = 20

    /**
     * Rend la fiche structurelle du type du classpath.
     *
     * @param vue la vue JVM des membres (les entrées du classpath posé) ;
     * @param fqcn le nom pleinement qualifié du type visé ;
     * @return la fiche (signature = l'en-tête, documentation = les
     * sections) ; `null` si le type n'est pas servi par les entrées
     * (silence honnête D3).
     */
    internal fun carte(vue: VueJvmMembres, fqcn: String): SurvolKotlin? {
        val declares = vue.membresDeclares(fqcn) ?: return null
        val acces = declares.accesClasse
        val cheminThis = fqcn.replace('.', '/')
        val estInterface = acces and ACC_INTERFACE != 0
        val estEnum = acces and ACC_ENUM != 0
        // Un OBJET Kotlin : final + le marqueur `INSTANCE` statique final
        // de son PROPRE type (le marqueur EST le genre, jamais un champ).
        val estObjet = !estInterface && !estEnum && acces and ACC_FINAL != 0 &&
            declares.membres.any {
                it.estChamp && it.nom == "INSTANCE" && it.acces and (ACC_STATIC or ACC_FINAL) == (ACC_STATIC or ACC_FINAL) &&
                    it.descripteur == "L$cheminThis;"
            }
        val genre = when {
            estInterface -> "interface"
            estEnum -> "enum"
            estObjet -> "object"
            else -> "class"
        }
        val visibilite = if (acces and ACC_PUBLIC != 0) "public " else ""
        val entete = StringBuilder(visibilite).append(genre).append(' ').append(fqcn)
        if (genre == "class") {
            // L'héritage direct : JAMAIS pour une interface (fiche 018) ;
            // `java.lang.Object` est le silence par défaut, pas une ligne.
            val superFqcn = declares.superFqcn
            if (superFqcn != null && superFqcn != "java.lang.Object") {
                entete.append(" extends ").append(superFqcn)
            }
        }
        val sections = mutableListOf<String>()
        if (estEnum) {
            val constantes = declares.membres
                .filter {
                    it.estChamp &&
                        it.acces and (ACC_PUBLIC or ACC_STATIC or ACC_FINAL) == (ACC_PUBLIC or ACC_STATIC or ACC_FINAL) &&
                        !it.nom.startsWith("$") &&
                        it.descripteur == "L$cheminThis;"
                }
                .map { it.nom }
            if (constantes.isNotEmpty()) {
                sections.add(listeBornee("Constantes", constantes, PLAFOND_CONSTANTES))
            }
        }
        if (declares.interfaces.isNotEmpty()) {
            sections.add("Interfaces : " + declares.interfaces.joinToString(", "))
        }
        val champs = declares.membres
            .filter {
                it.estChamp &&
                    it.acces and ACC_PUBLIC != 0 &&
                    it.acces and ACC_STATIC == 0 &&
                    !it.nom.startsWith("$")
            }
            .mapNotNull { membre ->
                // Le type du champ au nom SIMPLE (fiche 018 :
                // `CharSequence texte`) ; un type illisible (tableau)
                // exclut le champ — jamais un type inventé (D3).
                DescripteursJvm.typeNu(membre.descripteur)?.let { type ->
                    "${nomSimple(type)} ${membre.nom}"
                }
            }
        if (champs.isNotEmpty()) {
            sections.add(listeBornee("Champs publics", champs, PLAFOND_CHAMPS))
        }
        return SurvolKotlin(
            signature = entete.toString(),
            documentation = sections.joinToString("\n").ifEmpty { null },
        )
    }

    /**
     * Le rendu borné d'une section : `Titre : a, b, c` plafonné, l'ellipse
     * « … » clôt la liste tronquée (fiche 018 : champs 8, constantes 20).
     */
    private fun listeBornee(titre: String, valeurs: List<String>, plafond: Int): String {
        val rendues = valeurs.take(plafond).joinToString(", ")
        val ellipse = if (valeurs.size > plafond) ", …" else ""
        return "$titre : $rendues$ellipse"
    }

    /** Le nom SIMPLE d'un FQCN (`java.lang.CharSequence` → `CharSequence`). */
    private fun nomSimple(fqcn: String): String =
        fqcn.substringAfterLast('.').substringAfterLast('$')
}
