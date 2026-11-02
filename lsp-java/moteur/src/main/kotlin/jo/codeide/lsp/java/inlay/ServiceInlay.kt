package jo.codeide.lsp.java.inlay

import jo.codeide.lsp.java.interne.LectureAst
import jo.codeide.lsp.java.interne.LectureAst.VisiteurResilient
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration
import org.eclipse.jdt.internal.compiler.ast.LambdaExpression
import org.eclipse.jdt.internal.compiler.ast.LocalDeclaration
import org.eclipse.jdt.internal.compiler.ast.MessageSend
import org.eclipse.jdt.internal.compiler.ast.SingleTypeReference
import org.eclipse.jdt.internal.compiler.lookup.MethodBinding
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding

/**
 * Genre d'un indice inlay — miroir des valeurs numériques du protocole LSP
 * 3.17 (`InlayHintKind`, 1 = Type, 2 = Parameter) : le serveur LSP complet
 * arrive à l'étage N8 ; l'étage N3 verrouille le genre côté moteur, c'est
 * le contrat visible par le client.
 */
public enum class GenreIndice(
    /** Valeur numérique du kind dans le protocole LSP. */
    public val codeLsp: Int,
) {
    /** Indice de type (fin de nom d'une déclaration, frontière de chaînage). */
    TYPE(1),

    /** Indice de nom de paramètre (avant un argument d'appel). */
    PARAMETRE(2),
}

/**
 * Un indice inlay servi par le moteur (étage N3 — cahier fiches 002/003/
 * 030) : position OFFSET (le client convertit en ligne/colonne par son
 * repère de document — jamais côté serveur, le repère client fait foi).
 *
 * @property offset l'ancre de l'indice dans le système du document.
 * @property libelle le texte affiché (conventions de la fiche : « : Type »
 *   collé pour les types, « nom: » pour les paramètres, nom court nu pour
 *   les frontières de chaînage).
 * @property genre le genre LSP (Type ou Parameter).
 * @property paddingGauche espacement demandé à gauche du libellé.
 * @property paddingDroit espacement demandé à droite du libellé.
 */
public data class IndiceInlay(
    public val offset: Int,
    public val libelle: String,
    public val genre: GenreIndice,
    public val paddingGauche: Boolean = false,
    public val paddingDroit: Boolean = false,
)

/**
 * Fournisseur d'indices inlay (étage N3 — cahier fiches 002, 003, 030) :
 * quatre familles, servies en DEUX passes contractuelles.
 *
 * Passe 1 — PARAMÈTRES (kind Parameter) : un indice « nom: » par argument
 * d'appel de méthode déclarée en SOURCE (même unité ou autre fichier du
 * projet — le binding est un type source) ; la surcharge exacte est celle
 * résolue par ecj. Les méthodes BINAIRES (jrt, jars) restent muettes —
 * jamais de faux noms ; les appels sans argument ne portent rien.
 *
 * Passe 2 — TYPES (kind Type) :
 *  - « : Type » en fin de nom des déclarations `var` (locale ou élément de
 *    boucle for-each) — le type RÉEL lié, généricité comprise, nom court ;
 *    les déclarations typées explicitement et les « var » textuels vivant
 *    dans une chaîne littérale ne portent RIEN (l'analyse est structurelle) ;
 *  - « : Type » en fin de nom des paramètres lambda IMPLICITES ; les
 *    paramètres typés explicitement ne portent rien ;
 *  - frontière de chaînage : sur un chaînage fluent MULTI-LIGNE, le type
 *    (nom court, sans deux-points, padding gauche) du récepteur est ancré
 *    à la fin de SA ligne ; le premier maillon (récepteur constructeur) et
 *    les chaînages monolignes restent muets.
 *
 * La liste résultante est DÉLIBÉRÉMENT non triée (paramètres avant types,
 * quelles que soient les colonnes — contrat documenté fiche 003) : le tri
 * par colonne est la responsabilité du client avant le tissage.
 *
 * Aucun filtrage par plage : la requête LSP porte une range, le serveur
 * parcourt toute l'unité et rend tous ses indices — l'éditeur filtre.
 */
public object ServiceInlay {

    /**
     * Indices inlay de l'unité.
     *
     * @param arbre l'arbre RÉSOLU (bindings vivants).
     * @param source le texte source dans le système de l'arbre.
     * @return les indices (non triés — contrat de désordre documenté).
     */
    public fun indices(arbre: CompilationUnitDeclaration?, source: String): List<IndiceInlay> {
        if (arbre == null) return emptyList()
        val parametres = CollecteurParametres()
        LectureAst.traverserResilient(arbre, parametres)
        val types = CollecteurTypes(source)
        LectureAst.traverserResilient(arbre, types)
        return parametres.indices + types.indices
    }

    /** Nom court d'un type résolu (les libellés inlay sont des noms courts). */
    private fun court(type: org.eclipse.jdt.internal.compiler.lookup.TypeBinding?): String? = try {
        type?.shortReadableName()?.let(::String)
    } catch (_: Exception) {
        null
    }

    /**
     * Passe 1 : indices de noms de paramètres aux sites d'appel — un
     * « nom: » par argument, ancré au DÉBUT de l'argument.
     */
    private class CollecteurParametres : VisiteurResilient() {
        val indices = mutableListOf<IndiceInlay>()

        override fun visiteEnvoi(node: MessageSend, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            val binding = node.binding as? MethodBinding
            if (binding == null || !binding.isValidBinding) return true
            val classe = binding.declaringClass as? ReferenceBinding ?: return true
            // Binaires : silence — jamais de faux noms (fiche 023).
            if (classe.isBinaryBinding()) return true
            val noms = binding.parameterNames ?: return true
            val arguments = node.arguments ?: return true
            if (noms.size != arguments.size) return true
            for (i in arguments.indices) {
                indices.add(
                    IndiceInlay(
                        offset = arguments[i].sourceStart,
                        libelle = "${String(noms[i])}:",
                        genre = GenreIndice.PARAMETRE,
                        paddingDroit = true,
                    ),
                )
            }
            return true
        }
    }

    /**
     * Passe 2 : indices de TYPE — var, paramètres lambda implicites,
     * frontières de chaînage.
     */
    private class CollecteurTypes(private val source: String) : VisiteurResilient() {
        val indices = mutableListOf<IndiceInlay>()

        override fun visiteLocale(node: LocalDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            // Seules les déclarations « var » sont annotées (fiche 030) —
            // l'anti-piège « var dans une chaîne littérale » est couvert
            // par construction : ce visiteur ne voit que des nœuds AST.
            val typeRef = node.type as? SingleTypeReference ?: return true
            if (String(typeRef.token) != "var") return true
            val typeReel = court(node.binding?.type) ?: return true
            val finNom = finDeNomDe(node, source) ?: return true
            indices.add(IndiceInlay(finNom, ": $typeReel", GenreIndice.TYPE))
            return true
        }

        override fun visiteLambda(node: LambdaExpression, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            if (!node.argumentsTypeElided()) return true
            for (parametre in node.arguments ?: emptyArray()) {
                val type = court(parametre.binding?.type) ?: continue
                indices.add(IndiceInlay(parametre.sourceEnd + 1, ": $type", GenreIndice.TYPE))
            }
            return true
        }

        override fun visiteEnvoi(node: MessageSend, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            // Frontière de chaînage : le récepteur doit être un APPEL (le
            // premier maillon — récepteur constructeur — ne porte rien) et
            // la frontière doit être multi-lignes (un chaînage monoligne
            // ne serait que du bruit visuel — fiche 002).
            val recepteur = node.receiver as? MessageSend ?: return true
            val debutSel = (node.nameSourcePosition ushr 32).toInt()
            if (debutSel <= recepteur.sourceEnd + 1) return true
            if (!source.substring(recepteur.sourceEnd + 1, debutSel).contains('\n')) return true
            val typeRecepteur = court(recepteur.resolvedType) ?: court(node.actualReceiverType) ?: return true
            indices.add(
                IndiceInlay(
                    offset = recepteur.sourceEnd + 1,
                    libelle = typeRecepteur,
                    genre = GenreIndice.TYPE,
                    paddingGauche = true,
                ),
            )
            return true
        }
    }

    /**
     * Fin du NOM déclaré d'une déclaration de variable : le nom suit le
     * type (blancs franchis) — l'ancre de l'indice de type est collée à la
     * fin du nom, AVANT le « = » (fiche 030).
     *
     * @return l'offset exclusif de fin du nom, ou null (texte incohérent).
     */
    private fun finDeNomDe(node: LocalDeclaration, source: String): Int? {
        val finType = node.type?.sourceEnd ?: return null
        var i = finType + 1
        while (i < source.length && source[i].isWhitespace()) i++
        val debutNom = i
        while (i < source.length && source[i].isJavaIdentifierPart()) i++
        if (i == debutNom) return null
        return i
    }
}
