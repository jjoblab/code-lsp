// CodeIDE — quick-fix « IMPLÉMENTER LES MEMBRES MANQUANTS » (module
// :kotlin, étage 2 vague 2f — fiche 020 du cahier, chantier P1-C ADR
// 0229). Code NEUF jjoblab (clean-room) : la clôture des supertypes
// (PROJET par le modèle — membres ABSTRAITS seuls —, CLASSPATH par la vue
// JVM — getters/setters appariés en propriétés), la substitution des
// paramètres de type par la clause ÉCRITE, l'insertion après le dernier
// membre, le corps créé en fin d'en-tête, le corps vide remplacé.
package jo.codeide.lsp.kotlin.session

import jo.codeide.lsp.kotlin.api.CorrectifTexteKotlin
import jo.codeide.lsp.kotlin.parse.NoeudArbre
import jo.codeide.lsp.kotlin.parse.kt.KtFichier
import jo.codeide.lsp.kotlin.parse.kt.PlageTexte
import jo.codeide.lsp.kotlin.resolve.GenreMembreProjet
import jo.codeide.lsp.kotlin.resolve.GenreTypeProjet
import jo.codeide.lsp.kotlin.resolve.MarcheTypee
import jo.codeide.lsp.kotlin.resolve.MembreProjet
import jo.codeide.lsp.kotlin.resolve.TypeProjet
import jo.codeide.lsp.kotlin.resolve.TypeValeur
import jo.codeide.lsp.kotlin.resolve.UniversPreuves
import org.jetbrains.kotlin.kmp.lexer.KtTokens
import org.jetbrains.kotlin.kmp.parser.KtNodeTypes

/**
 * Le générateur du correctif « implémenter les membres manquants » (pur —
 * aucun état, appelé sous verrou de lecture).
 *
 * ## Le contrat (fiche 020)
 *
 * - la CIBLE est une classe ou un objet du document (l'caret dans son
 *   nom) qui DÉCLARE des supertypes — une cible interface ou abstraite
 *   ELLE-MÊME ne reçoit RIEN (D3) ;
 * - les membres manquants viennent des supertypes PROJET (membres
 *   ABSTRAITS seuls — un membre concret du supertype n'est JAMAIS
 *   collecté) et des supertypes CLASSPATH (les méthodes de l'interface
 *   binaire, getters/setters APPARIÉS en propriétés) ;
 * - les paramètres de TYPE du supertype sont SUBSTITUÉS par la clause
 *   écrite (`: Depot<String>` → `T` devient `String`) ; une clause NUE
 *   sur un supertype GÉNÉRIQUE laisse le membre muet (D3) ;
 * - l'INSERTION se fait après le DERNIER membre du corps, dans un corps
 *   CRÉÉ en fin d'en-tête s'il est absent, ou REMPLACE un corps vide.
 */
internal object MembresManquants {

    /**
     * Calcule le correctif de la déclaration sous le point.
     *
     * @param document le document ouvert ;
     * @param univers l'univers des preuves (modèle + vue JVM) ;
     * @param deplacement l'offset du caret DANS le nom de la déclaration ;
     * @return le correctif ; liste vide sans cible, sans supertype ou sous
     * garde D3.
     */
    internal fun calculer(
        document: DocumentOuvert,
        univers: UniversPreuves,
        deplacement: Int,
    ): List<CorrectifTexteKotlin> {
        val cible = declarationVisee(document.arbre, deplacement) ?: return emptyList()
        val nom = cible.jetonsPropres.firstOrNull { it.genre == KtTokens.IDENTIFIER }?.texte ?: return emptyList()

        // La cible : classe ou objet CONCRET (une interface ou une classe
        // abstraite ne reçoit rien — le correctif vise l'IMPLÉMENTATEUR).
        val typeCible = univers.modele.typeParNomSimple(nom, univers.paquet) ?: return emptyList()
        if (typeCible.genre == GenreTypeProjet.INTERFACE || typeCible.genre == GenreTypeProjet.ENUM) {
            return emptyList()
        }
        if (estAbstraite(cible)) return emptyList()

        // Les supertypes ÉCRITS avec leurs clauses (nom + arguments).
        val clauses = clausesSupertypes(cible)
        if (clauses.isEmpty()) return emptyList()

        // Les membres DÉJÀ déclarés (par nom) — l'appariement par nom
        // suffit au correctif (les surcharges relèvent de la résolution).
        val deja = cible.membresDeclares().toSet()

        val stubs = mutableListOf<String>()
        for (clause in clauses) {
            val supertype = univers.supertypeResolu(clause.nom) ?: continue
            when (supertype) {
                is TypeValeur.Projet -> {
                    val substitution = substitutionDe(clause, supertype.type) ?: return emptyList()
                    for (membre in supertype.type.membres) {
                        if (membre.genre == GenreMembreProjet.TYPE_IMBRIQUE) continue
                        if (!membre.estAbstrait) continue
                        if (membre.nom in deja) continue
                        stubs.add(stubProjet(membre, substitution))
                    }
                }
                is TypeValeur.Jvm -> {
                    val vue = univers.vueJvm ?: return emptyList()
                    val fqcn = supertype.fqcn
                    if (!vue.possedeClasse(fqcn)) continue
                    for (stub in stubsClasspath(vue, fqcn, deja)) stubs.add(stub)
                }
            }
        }
        if (stubs.isEmpty()) return emptyList()

        // Le rendu du correctif selon la forme du corps.
        val corps = cible.enfants.firstOrNull { it.genre == KtNodeTypes.CLASS_BODY }
        val indentation = indentationDe(document.texte, cible.debut)
        val corpsVide = corps != null && corps.enfants.none {
            it.genre == KtNodeTypes.FUN || it.genre == KtNodeTypes.PROPERTY ||
                it.genre == KtNodeTypes.CLASS || it.genre == KtNodeTypes.OBJECT_DECLARATION ||
                it.genre == KtNodeTypes.SECONDARY_CONSTRUCTOR || it.genre == KtNodeTypes.ENUM_ENTRY ||
                it.genre == KtNodeTypes.CLASS_INITIALIZER
        }
        return when {
            corpsVide -> listOf(
                CorrectifTexteKotlin(
                    titre = "Implémenter les membres manquants (${stubs.size})",
                    plage = PlageTexte(corps!!.debut, corps.fin),
                    texte = "{\n" + stubs.joinToString("\n") { "$indentation  $it" } + "\n$indentation}",
                ),
            )
            corps == null -> listOf(
                CorrectifTexteKotlin(
                    titre = "Implémenter les membres manquants (${stubs.size})",
                    plage = PlageTexte(cible.fin, cible.fin),
                    texte = " {\n" + stubs.joinToString("\n") { "$indentation  $it" } + "\n$indentation}",
                ),
            )
            else -> {
                val dernier = corps.enfants.lastOrNull { it.genre == KtNodeTypes.FUN || it.genre == KtNodeTypes.PROPERTY }
                    ?: corps.enfants.lastOrNull()
                val point = (dernier?.fin ?: corps.debut + 1).coerceAtMost(corps.fin - 1)
                listOf(
                    CorrectifTexteKotlin(
                        titre = "Implémenter les membres manquants (${stubs.size})",
                        plage = PlageTexte(point, point),
                        texte = "\n" + stubs.joinToString("\n") { "$indentation  $it" },
                    ),
                )
            }
        }
    }

    /** La déclaration de type visée par le caret (son NOM la couvre). */
    private fun declarationVisee(arbre: KtFichier, deplacement: Int): NoeudArbre? {
        var trouve: NoeudArbre? = null
        fun marcher(noeud: NoeudArbre) {
            if (noeud.genre == KtNodeTypes.CLASS || noeud.genre == KtNodeTypes.OBJECT_DECLARATION) {
                val nom = noeud.jetonsPropres.firstOrNull { it.genre == KtTokens.IDENTIFIER }
                if (nom != null && nom.debut <= deplacement && deplacement <= nom.fin) trouve = noeud
            }
            noeud.enfants.forEach(::marcher)
        }
        marcher(arbre.noeud)
        return trouve
    }

    /** La cible porte-t-elle le modificateur `abstract` ? */
    private fun estAbstraite(cible: NoeudArbre): Boolean =
        cible.enfants
            .filter { it.genre == KtNodeTypes.MODIFIER_LIST }
            .any { liste -> liste.enfants.any { it.genre == KtTokens.ABSTRACT_MODIFIER } }

    /** Les noms des membres FUN/PROPERTY déjà déclarés dans le corps. */
    private fun NoeudArbre.membresDeclares(): List<String> {
        val corps = enfants.firstOrNull { it.genre == KtNodeTypes.CLASS_BODY } ?: return emptyList()
        return corps.enfants.mapNotNull { membre ->
            when (membre.genre) {
                KtNodeTypes.FUN, KtNodeTypes.PROPERTY ->
                    membre.jetonsPropres.firstOrNull { it.genre == KtTokens.IDENTIFIER }?.texte
                else -> null
            }
        }
    }

    /** Une clause de supertype écrite (nom + arguments de type écrits). */
    private class Clause(val nom: String, val arguments: List<String>)

    /** Les clauses de supertypes écrites de la cible (avec arguments). */
    private fun clausesSupertypes(cible: NoeudArbre): List<Clause> {
        val clause = cible.enfants.firstOrNull { it.genre == KtNodeTypes.SUPER_TYPE_LIST } ?: return emptyList()
        val clauses = mutableListOf<Clause>()
        for (entree in clause.enfants) {
            if (entree.genre != KtNodeTypes.SUPER_TYPE_ENTRY &&
                entree.genre != KtNodeTypes.SUPER_TYPE_CALL_ENTRY
            ) {
                continue
            }
            val reference = entree.enfants
                .firstOrNull { it.genre == KtNodeTypes.CONSTRUCTOR_CALLEE }
                ?.enfants?.firstOrNull { it.genre == KtNodeTypes.TYPE_REFERENCE }
                ?: entree.enfants.firstOrNull { it.genre == KtNodeTypes.TYPE_REFERENCE }
                ?: continue
            val utilisateur = reference.enfants.firstOrNull { it.genre == KtNodeTypes.USER_TYPE } ?: continue
            val nom = utilisateur.texteCanonique.substringBefore('<').trim()
            if (nom.isEmpty()) continue
            val arguments = utilisateur.enfants
                .firstOrNull { it.genre == KtNodeTypes.TYPE_ARGUMENT_LIST }
                ?.enfants?.filter { it.genre == KtNodeTypes.TYPE_PROJECTION }
                ?.mapNotNull { it.texteCanonique.trim().removePrefix("*").ifEmpty { null } }
                .orEmpty()
            clauses.add(Clause(nom, arguments))
        }
        return clauses
    }

    /**
     * La substitution des paramètres de type du supertype par la clause
     * ÉCRITE (`T` → `String`) ; `null` quand la clause est NUE sur un
     * supertype GÉNÉRIQUE (le membre reste muet, D3).
     */
    private fun substitutionDe(clause: Clause, supertype: TypeProjet): Map<String, String>? {
        if (supertype.parametresType.isEmpty()) return emptyMap()
        if (clause.arguments.isEmpty()) return null
        return supertype.parametresType.zip(clause.arguments).toMap()
    }

    /** Le stub d'un membre ABSTAIT du projet (substitution appliquée). */
    private fun stubProjet(membre: MembreProjet, substitution: Map<String, String>): String {
        val substitue = { nom: String? -> nom?.let { substitution[it] ?: it } }
        return if (membre.genre == GenreMembreProjet.FONCTION) {
            val parametres = membre.parametres.joinToString(", ") { parametre ->
                val type = substitue(parametre.typeEcrit) ?: "?"
                if (parametre.nom == null) type else "${parametre.nom}: $type"
            }
            val retour = substitue(membre.typeRetourNomme)
            if (retour == null || retour == "Unit") {
                "override fun ${membre.nom}($parametres) = TODO(\"${membre.nom}\")"
            } else {
                "override fun ${membre.nom}($parametres): $retour = TODO(\"${membre.nom}\")"
            }
        } else {
            "override val ${membre.nom}: ${substitue(membre.typeRetourNomme) ?: "?"} get() = TODO(\"${membre.nom}\")"
        }
    }

    /** Les stubs des membres d'une interface du CLASSPATH (vue JVM). */
    private fun stubsClasspath(
        vue: jo.codeide.lsp.kotlin.resolve.VueJvmMembres,
        fqcn: String,
        deja: Set<String>,
    ): List<String> {
        val declares = vue.membresDeclares(fqcn) ?: return emptyList()
        val stubs = mutableListOf<String>()
        val apparies = mutableSetOf<String>()
        for (membre in declares.membres) {
            if (membre.nom in deja) continue
            if (!membre.estChamp && membre.estAbstrait) {
                // Getters/setters APPARIÉS en propriété (ADR 0219).
                if (membre.nom.startsWith("get") && membre.nom.length > 3) {
                    val propriete = membre.nom.substring(3).replaceFirstChar { it.lowercaseChar() }
                    val type = membre.retour() ?: continue
                    val aSetteur = declares.membres.any {
                        it.nom == "set" + membre.nom.substring(3) && !it.estChamp && it.estAbstrait
                    }
                    if (propriete in deja) continue
                    apparies.add("set" + membre.nom.substring(3))
                    val genre = if (aSetteur) "var" else "val"
                    stubs.add(
                        "override $genre $propriete: ${type.substringAfterLast('.')}" +
                            " get() = TODO(\"$propriete\")",
                    )
                    continue
                }
                if (membre.nom in apparies) continue
                if (membre.nom.startsWith("set") && membre.nom.length > 3) continue
                val parametres = membre.parametres()?.joinToString(", ") { it.substringAfterLast('.') }.orEmpty()
                val retour = membre.retour()
                val retourSimple = retour?.let { ": " + it.substringAfterLast('.') }.orEmpty()
                val corps = if (retour == null || retour == "kotlin.Unit") "" else retourSimple
                stubs.add(
                    "override fun ${membre.nom}($parametres)$corps = TODO(\"${membre.nom}\")",
                )
            }
        }
        return stubs
    }

    /** L'indentation (préfixe blanc) de la ligne du décalage donné. */
    private fun indentationDe(texte: CharSequence, decalage: Int): String {
        val debutLigne = (0 until decalage).lastOrNull { texte[it] == '\n' }?.plus(1) ?: 0
        val ligne = texte.substring(debutLigne, decalage)
        return ligne.takeWhile { it == ' ' || it == '\t' }
    }
}
