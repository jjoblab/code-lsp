// CodeIDE — complétion de NOM au point (module :kotlin, étage 3 vague 3a —
// ADR 0113 : le régime de repli honnête du pipeline ; le germe de la vague
// 2e/2f s'étend en propositions RICHES — natures, détails, mots-clés,
// auto-import des types du classpath). Code NEUF jjoblab (clean-room).
package jo.codeide.lsp.kotlin.session

import jo.codeide.lsp.kotlin.api.NaturePropositionKotlin
import jo.codeide.lsp.kotlin.api.PropositionKotlin
import jo.codeide.lsp.kotlin.parse.NoeudArbre
import jo.codeide.lsp.kotlin.parse.kt.KtFichier
import jo.codeide.lsp.kotlin.resolve.GenreMembreHerite
import jo.codeide.lsp.kotlin.resolve.GenreTypeProjet
import jo.codeide.lsp.kotlin.resolve.MarcheTypee
import jo.codeide.lsp.kotlin.resolve.PileThis
import jo.codeide.lsp.kotlin.resolve.UniversPreuves
import jo.codeide.lsp.kotlin.resolve.VueJvmMembres
import jo.codeide.lsp.kotlin.index.VueAppelables
import org.jetbrains.kotlin.kmp.lexer.KtTokens
import org.jetbrains.kotlin.kmp.parser.KtNodeTypes

/**
 * Le régime de NOM (étage 3 — ADR 0113) : le caret vit dans un
 * identifiant SANS sélecteur qualifié — le régime de repli honnête du
 * pipeline. Les sources, dans l'ordre de rencontre :
 *
 *  1. les PARAMÈTRES et LOCALES de la région porteuse (portée de frappe) ;
 *  2. les MEMBRES du récepteur IMPLICITE (pile des this — `with`/`apply`,
 *     classes englobantes, objets anonymes) ;
 *  3. les TYPES du projet (nom simple) ;
 *  4. le catalogue KP8 (types bien connus des paquets par défaut) ;
 *  5. les APPELABLES de premier niveau de l'index (par préfixe, bornés) ;
 *  6. les TYPES du classpath par préfixe (auto-import : FQCN en détail et
 *     en import éditable) ;
 *  7. les MOTS-CLÉ du langage (liste finie — toujours proposés, même à
 *     préfixe vide : c'est le témoin visible du régime de repli, fiche
 *     043).
 *
 * Les sources INFINIES (index, classpath) ne se consultent qu'à préfixe
 * NON VIDE (le germe 2e est resté honnête : jamais l'avalanche du préfixe
 * vide — les mots-clés seuls y vivent).
 */
internal object CompletionNoms {

    /** La limite des propositions de l'index par préfixe (miroir du germe 2e). */
    private const val LIMITE_INDEX: Int = 24

    /**
     * Les MOTS-CLÉ proposés au régime de nom : le vocabulaire STATEMENT et
     * DÉCLARATION du langage (mots durs et modificateurs utiles à une
     * position d'identifiant — liste FINIE, jamais l'avalanche).
     */
    private val MOTS_CLES: List<String> = listOf(
        "as", "break", "by", "class", "companion", "const", "continue", "data",
        "do", "else", "enum", "expect", "actual", "false", "final", "for",
        "fun", "if", "import", "in", "init", "interface", "internal", "is",
        "lateinit", "null", "object", "open", "override", "package",
        "private", "protected", "public", "return", "sealed", "super",
        "suspend", "this", "throw", "true", "try", "typealias", "val",
        "var", "when", "while",
    )

    /**
     * Collecte les propositions du régime de NOM au point.
     *
     * @param arbre l'arbre du document ;
     * @param univers l'univers des preuves (document + scan + index) ;
     * @param liaison la liaison de la région porteuse ;
     * @param pile la pile des this au caret ;
     * @param deplacement l'offset du caret ;
     * @param index la vue des appelables (`null` = guichet muet) ;
     * @param vueJvm la vue JVM (`null` = pas d'annuaire de types) ;
     * @return les propositions (non filtrées par plafond — le pipeline
     * arrange) ; liste vide sans sources.
     */
    internal fun collecter(
        arbre: KtFichier,
        univers: UniversPreuves,
        liaison: MarcheTypee.Liaison,
        pile: PileThis,
        deplacement: Int,
        index: VueAppelables?,
        vueJvm: VueJvmMembres?,
    ): List<PropositionKotlin> {
        val prefixe = CompletionContextes.prefixeAuPoint(arbre.noeud, deplacement)
        val propositions = LinkedHashMap<String, PropositionKotlin>()

        fun poser(libelle: String, nature: NaturePropositionKotlin, detail: String? = null, fqcnImport: String? = null) {
            if (libelle.isEmpty() || libelle in propositions) return
            if (prefixe.isNotEmpty() && !libelle.startsWith(prefixe)) return
            propositions[libelle] = PropositionKotlin(
                libelle = libelle,
                nature = nature,
                detail = detail,
                fqcnImport = fqcnImport,
            )
        }

        // 1. Les paramètres et locales de la région porteuse.
        for ((libelle, nature, detail) in localesEtParametres(arbre.noeud, deplacement)) {
            poser(libelle, nature, detail)
        }

        // 2. Les membres du récepteur IMPLICITE (pile des this).
        for (membre in pile.membresDetaillesDesCouches) {
            when (membre.genre) {
                GenreMembreHerite.FONCTION -> poser(membre.nom, NaturePropositionKotlin.FONCTION)
                GenreMembreHerite.PROPRIETE ->
                    poser(membre.nom, NaturePropositionKotlin.PROPRIETE, membre.typeEcrit)
                GenreMembreHerite.TYPE_IMBRIQUE -> poser(membre.nom, NaturePropositionKotlin.CLASSE)
            }
        }

        // 3. Les types du projet (nom simple — la nature fine se lit du
        // modèle par consultation : un nom simple peut être interface,
        // classe, objet ou enum).
        for (nomSimple in univers.modele.typesParNomSimple()) {
            poser(nomSimple, natureDuTypeProjet(univers, nomSimple))
        }

        // 3-bis. Les APPELABLES de premier niveau du projet (fiche 025 : la
        // complétion de nom dans un corps propose les déclarés par préfixe
        // — `charg` propose `chargerDonnees`).
        for (nom in univers.modele.nomsAppelables()) {
            poser(nom, NaturePropositionKotlin.FONCTION)
        }

        // 4. Le catalogue KP8 (types bien connus des paquets par défaut).
        for (nom in UniversPreuves.CATALOGUE_TYPES_DEFAUT) {
            poser(nom, NaturePropositionKotlin.CLASSE)
        }

        // Les sources INFINIES ne se consultent qu'à préfixe NON VIDE
        // (jamais l'avalanche du préfixe vide — miroir du germe 2e).
        if (prefixe.isNotEmpty()) {
            // 5. Les appelables de premier niveau de l'index (bornés).
            if (index != null) {
                for (nom in index.nomsTopParPrefixe(prefixe, LIMITE_INDEX)) {
                    val appelables = index.topParNom(nom)
                    val nature = when (appelables.firstOrNull()?.genre) {
                        jo.codeide.lsp.kotlin.index.GenreAppelable.PROPRIETE ->
                            NaturePropositionKotlin.PROPRIETE
                        else -> NaturePropositionKotlin.FONCTION
                    }
                    poser(nom, nature, appelables.firstOrNull()?.typeRetour)
                }
            }

            // 6. Les types du classpath (auto-import : FQCN en détail ET en
            // import éditable — l'hôte arbitre par import).
            if (vueJvm != null) {
                for (fqcn in vueJvm.fqcnParPrefixe(prefixe, LIMITE_INDEX)) {
                    poser(fqcn.substringAfterLast('.'), NaturePropositionKotlin.CLASSE, fqcn, fqcn)
                }
            }
        }

        // 7. Les mots-clés (toujours proposés — le témoin du régime de
        // repli, fiche 043 : « le régime de nom reprend (mots-clés) »).
        MOTS_CLES.forEach { poser(it, NaturePropositionKotlin.MOT_CLE) }

        return propositions.values.toList()
    }

    /**
     * La NATURE d'un type du projet par nom simple (interface ou classe —
     * objet, enum et classe Java se proposent comme classes).
     */
    private fun natureDuTypeProjet(univers: UniversPreuves, nomSimple: String): NaturePropositionKotlin {
        val type = univers.typeProjetDuNomEcrit(nomSimple) ?: return NaturePropositionKotlin.CLASSE
        return if (type.genre == GenreTypeProjet.INTERFACE) {
            NaturePropositionKotlin.INTERFACE
        } else {
            NaturePropositionKotlin.CLASSE
        }
    }

    /**
     * Les LOCALES et PARAMÈTRES de la région porteuse du caret : les
     * paramètres de la fonction porteuse (toujours en portée) et les
     * propriétés LOCALES déclarées AVANT le caret (l'ombre interne gagne —
     * miroir de la liaison).
     *
     * @param racine la racine de l'arbre ;
     * @param deplacement l'offset du caret ;
     * @return les (libellé, nature, détail) en ordre du document.
     */
    private fun localesEtParametres(racine: NoeudArbre, deplacement: Int): List<Triple<String, NaturePropositionKotlin, String?>> {
        val region = regionContenant(racine, deplacement) ?: return emptyList()
        val trouvees = mutableListOf<Triple<String, NaturePropositionKotlin, String?>>()

        // Les paramètres de la fonction porteuse.
        val listeParametres = region.enfants.firstOrNull { it.genre == KtNodeTypes.VALUE_PARAMETER_LIST }
        for (parametre in listeParametres?.enfants.orEmpty()) {
            if (parametre.genre != KtNodeTypes.VALUE_PARAMETER) continue
            val nom = nomDeDeclaration(parametre) ?: continue
            trouvees.add(Triple(nom, NaturePropositionKotlin.PARAMETRE, typeEcritDe(parametre)))
        }

        // Les propriétés locales déclarées avant le caret.
        fun marcher(noeud: NoeudArbre) {
            if (noeud.genre == KtNodeTypes.PROPERTY && noeud.fin <= deplacement) {
                nomDeDeclaration(noeud)?.let { nom ->
                    trouvees.add(Triple(nom, NaturePropositionKotlin.VARIABLE, typeEcritDe(noeud)))
                }
            }
            noeud.enfants.forEach(::marcher)
        }
        marcher(region)
        return trouvees
    }

    /** Le nom déclaré d'un nœud (premier IDENTIFIER propre). */
    private fun nomDeDeclaration(noeud: NoeudArbre): String? =
        noeud.jetonsPropres.firstOrNull { it.genre == KtTokens.IDENTIFIER }?.texte

    /** Le type ÉCRIT d'une déclaration paramètre/propriété (`null` sinon). */
    private fun typeEcritDe(noeud: NoeudArbre): String? =
        noeud.enfants
            .firstOrNull { it.genre == KtNodeTypes.TYPE_REFERENCE }
            ?.texteCanonique
            ?.takeIf { it.isNotEmpty() }

    /**
     * La RÉGION contenant le point : la plus profonde déclaration de
     * fonction ou de propriété (miroir du germe 2a — les paramètres et les
     * locales y vivent).
     */
    private fun regionContenant(racine: NoeudArbre, deplacement: Int): NoeudArbre? {
        var trouve: NoeudArbre? = null
        fun marcher(noeud: NoeudArbre) {
            if (deplacement < noeud.debut || deplacement > noeud.fin) return
            if (noeud.genre == KtNodeTypes.FUN || noeud.genre == KtNodeTypes.PROPERTY) {
                trouve = noeud
            }
            noeud.enfants.forEach(::marcher)
        }
        marcher(racine)
        return trouve
    }
}
