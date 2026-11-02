// CodeIDE — diagnostics TYPÉS de la famille K4 (module :kotlin, étage 2
// vague 2b — fiche 046 du cahier). Code NEUF jjoblab (clean-room — l'ancien
// `diagnostics/` est en lecture interdite R9.1 ; cette passe est écrite
// cahier en main : `kt.typeMismatch`, `kt.argumentCount`,
// `kt.unsafeNullable`, `kt.unreachable`, `kt.valReassign` — la politique
// « preuve ou silence » de l'ADR 0107/0112 : un diagnostic n'est émis que
// si l'erreur se DÉCIDE sur l'arbre + la résolution bornée).
package jo.codeide.lsp.kotlin.session

import jo.codeide.lsp.kotlin.api.CodeDiagnosticKotlin
import jo.codeide.lsp.kotlin.api.DiagnosticKotlin
import jo.codeide.lsp.kotlin.api.SeveriteKotlin
import jo.codeide.lsp.kotlin.parse.NoeudArbre
import jo.codeide.lsp.kotlin.parse.kt.PlageTexte
import jo.codeide.lsp.kotlin.resolve.MarcheTypee
import jo.codeide.lsp.kotlin.resolve.ParametreProjet
import jo.codeide.lsp.kotlin.resolve.TypageBorne
import jo.codeide.lsp.kotlin.resolve.UniversPreuves
import org.jetbrains.kotlin.kmp.lexer.KtTokens
import org.jetbrains.kotlin.kmp.parser.KtNodeTypes

/**
 * La passe TYPÉE des diagnostics d'une RÉGION PROPRE (pure — appelée sous
 * la garde par déclaration de la fiche 017, après la marche des références)
 * : les cinq familles sémantiques K4 de la fiche 046.
 *
 * ## Les familles (fiche 046 — bornologie conservatrice)
 *
 * - `kt.typeMismatch` : un LITTÉRAL de famille connue affecté à un type
 *   ÉCRIT d'une AUTRE famille (chaîne vers `Int`…) ; les conversions
 *   légitimes (numérique → numérique, nullabilité) et tout ce qui n'est
 *   pas un littéral restent muets ;
 * - `kt.argumentCount` : un appel NU dont l'arité ne cadre avec AUCUN
 *   témoin PROPRE (fonction de premier niveau ou constructeur primaire du
 *   projet — la surcharge en cours de frappe est ÉCARTÉE, fiche 017) ;
 *   défauts, `vararg` et arguments nommés éteignent le témoin ;
 * - `kt.unsafeNullable` : un POINT sur un récepteur au type ÉCRIT NULLABLE
 *   sans raffinement de non-nullité en position (l'accès sûr `?.` et les
 *   gardes `!= null` éteignent — les smart casts positionnels 2b) ;
 * - `kt.unreachable` : la DÉCLARATION qui suit directement un `return` ou
 *   un `throw` FRÈRE dans le même bloc (un `return` imbriqué n'est pas un
 *   terminal — sous-rapport assumé) ;
 * - `kt.valReassign` : une affectation (`=`) dont la cible est un nom lié
 *   à un `val` PROPRE (locale, paramètre, propriété) ; `var` et cibles
 *   introuvables restent muets.
 */
internal object DiagnosticsTypage {

    /**
     * Calcule les diagnostics typés d'une région propre.
     *
     * @param region la déclaration propre visitée (fonction, propriété) ;
     * @param univers l'univers des preuves ;
     * @param liaison la liaison nom → type écrit de la région ;
     * @param diagnostics l'accumulateur des émissions.
     */
    internal fun verifier(
        region: NoeudArbre,
        univers: UniversPreuves,
        liaison: MarcheTypee.Liaison,
        diagnostics: MutableList<DiagnosticKotlin>,
    ) {
        marcher(region, univers, liaison, region, diagnostics)
    }

    /** La marche typée : chaque famille y regarde ses nœuds. */
    private fun marcher(
        noeud: NoeudArbre,
        univers: UniversPreuves,
        liaison: MarcheTypee.Liaison,
        region: NoeudArbre,
        diagnostics: MutableList<DiagnosticKotlin>,
    ) {
        when (noeud.genre) {
            KtNodeTypes.PROPERTY -> verifierTypeMismatch(noeud, diagnostics)
            KtNodeTypes.BLOCK -> verifierInatteignable(noeud, diagnostics)
            KtNodeTypes.BINARY_EXPRESSION -> {
                verifierValReaffectation(noeud, univers, region, liaison, diagnostics)
                verifierAccesNonSur(noeud, univers, liaison, diagnostics)
            }
            KtNodeTypes.DOT_QUALIFIED_EXPRESSION, KtNodeTypes.SAFE_ACCESS_EXPRESSION -> {
                verifierAccesNonSur(noeud, univers, liaison, diagnostics)
                // Le RÉCEPTEUR marche seul ; le SÉLECTEUR qualifié n'est pas
                // un appel NU (ses arités relèvent des membres — sous-rapport)
                // mais ses ARGUMENTS marchent (les preuves y vivent).
                noeud.enfants.firstOrNull()?.let { marcher(it, univers, liaison, region, diagnostics) }
                val selecteur = noeud.enfants.lastOrNull()
                if (selecteur != null && selecteur.genre == KtNodeTypes.CALL_EXPRESSION) {
                    selecteur.enfants
                        .filter { it.genre != KtNodeTypes.REFERENCE_EXPRESSION }
                        .forEach { marcher(it, univers, liaison, region, diagnostics) }
                }
                return
            }
            KtNodeTypes.CALL_EXPRESSION -> verifierArite(noeud, univers, region, diagnostics)
            else -> Unit
        }
        for (enfant in noeud.enfants) {
            marcher(enfant, univers, liaison, region, diagnostics)
        }
    }

    // ------------------------------------------------------------------
    // kt.typeMismatch — le littéral hors famille du type écrit.
    // ------------------------------------------------------------------

    /**
     * La preuve locale du TYPE INCOMPATIBLE : une propriété (locale,
     * membre ou de premier niveau) au type ÉCRIT de famille connue dont
     * l'INITIALISEUR est un LITTÉRAL d'une autre famille. Les familles :
     * chaîne, numériques (inter-conversions légitimes — sous-rapport
     * assumé), booléen, caractère ; la nullabilité écrite ne compte pas.
     */
    private fun verifierTypeMismatch(noeud: NoeudArbre, diagnostics: MutableList<DiagnosticKotlin>) {
        val typeEcrit = noeud.enfants
            .firstOrNull { it.genre == KtNodeTypes.TYPE_REFERENCE }
            ?.let(::nomTypeEcritDeReference) ?: return
        val familleEcrite = familleDeType(typeEcrit) ?: return
        val initialiseur = noeud.enfants.lastOrNull {
            it.genre != KtNodeTypes.TYPE_REFERENCE &&
                it.genre != KtNodeTypes.MODIFIER_LIST &&
                it.genre != KtNodeTypes.PROPERTY_DELEGATE &&
                it.genre != KtNodeTypes.PROPERTY_ACCESSOR
        } ?: return
        val familleLitteral = TypageBorne.familleLitteral(initialiseur) ?: return
        if (familleDeType(familleLitteral) == familleEcrite) return
        if (familleEcrite == FamilleType.NUMERIQUE && familleDeType(familleLitteral) == FamilleType.NUMERIQUE) {
            return
        }
        diagnostics.add(
            DiagnosticKotlin(
                code = CodeDiagnosticKotlin.TYPE_INCOMPATIBLE,
                severite = SeveriteKotlin.ERREUR,
                plage = PlageTexte(initialiseur.debut, initialiseur.fin),
                message = "Type incompatible : le littéral est de la famille « $familleLitteral » " +
                    "mais le type écrit est « $typeEcrit »",
                tags = emptyList(),
                informationsLiees = emptyList(),
            ),
        )
    }

    /** La famille d'un nom de type écrit (`null` si inconnue — silence). */
    private fun familleDeType(nom: String): FamilleType? {
        return when (nom) {
            "String" -> FamilleType.CHAINE
            "Int", "Long", "Short", "Byte", "Double", "Float" -> FamilleType.NUMERIQUE
            "Boolean" -> FamilleType.BOOLEEN
            "Char" -> FamilleType.CARACTERE
            else -> null
        }
    }

    /** Les familles de types décides sur l'arbre (fiche 046). */
    private enum class FamilleType {
        CHAINE,
        NUMERIQUE,
        BOOLEEN,
        CARACTERE,
    }

    // ------------------------------------------------------------------
    // kt.argumentCount — l'arité sans témoin propre.
    // ------------------------------------------------------------------

    /**
     * La preuve d'ARITÉ : un appel NU (callee sans récepteur) dont le
     * compte d'arguments ne cadre avec AUCUN témoin PROPRE du projet —
     * fonction de premier niveau ou constructeur primaire (le sélecteur
     * qualifié relève des membres : sous-rapport assumé). Les arguments
     * NOMMÉS, les paramètres à DÉFAUT et les `vararg` éteignent le témoin.
     */
    private fun verifierArite(
        appel: NoeudArbre,
        univers: UniversPreuves,
        region: NoeudArbre,
        diagnostics: MutableList<DiagnosticKotlin>,
    ) {
        val nom = appel.enfants
            .firstOrNull { it.genre == KtNodeTypes.REFERENCE_EXPRESSION }
            ?.texteCanonique.orEmpty()
        if (nom.isEmpty()) return

        // Les témoins : fonction de premier niveau, constructeur primaire.
        val temoins = temoinsArite(nom, univers, region)
        if (temoins.isEmpty()) return

        // Le compte : arguments parenthésés + lambda en position.
        val liste = appel.enfants.firstOrNull { it.genre == KtNodeTypes.VALUE_ARGUMENT_LIST }
        val arguments = liste?.enfants?.filter { it.genre == KtNodeTypes.VALUE_ARGUMENT }.orEmpty()

        // Un argument NOMMÉ éteint le témoin (la correspondance par nom
        // relève de la résolution complète — D3).
        if (arguments.any { argument -> argument.enfants.any { it.genre == KtNodeTypes.VALUE_ARGUMENT_NAME } }) {
            return
        }
        val compte = arguments.size +
            appel.enfants.count { it.genre == KtNodeTypes.LAMBDA_ARGUMENT }

        // Un témoin doit être PROPRE : les surcharges en cours de frappe
        // du DOCUMENT COURANT sont écartées (fiche 017).
        val temoinsPropres = temoins.filter { temoin ->
            temoin.uri != "" || temoin.estPropre
        }
        if (temoinsPropres.isEmpty()) return

        // Un défaut ou un vararg éteint le témoin entier (toute arité
        // partielle y est légale).
        if (temoinsPropres.any { temoin -> temoin.parametres.any { it.aDefaut || it.estVararg } }) return

        val attends = temoinsPropres.map { it.parametres.size }.distinct()
        if (compte in attends) return

        val nomAffiche = attends.joinToString(" ou ")
        diagnostics.add(
            DiagnosticKotlin(
                code = CodeDiagnosticKotlin.NOMBRE_ARGUMENTS,
                severite = SeveriteKotlin.ERREUR,
                plage = PlageTexte(appel.debut, appel.fin),
                message = "Nombre d'arguments : « $nom » attend $nomAffiche argument(s), " +
                    "l'appel en porte $compte",
                tags = emptyList(),
                informationsLiees = emptyList(),
            ),
        )
    }

    /** Un témoin d'arité (paramètres + propreté du document courant). */
    private class TemoinArite(
        internal val parametres: List<ParametreProjet>,
        internal val uri: String,
        internal val estPropre: Boolean,
    )

    /**
     * Les témoins d'arité d'un nom : les fonctions de PREMIER NIVEAU du
     * projet (modèle) et le CONSTRUCTEUR PRIMAIRE du type du projet du
     * même nom — les témoins du document COURANT vérifient leur PROPRETÉ
     * sur l'arbre frais (fiche 017 : la surcharge cassée n'est pas un
     * témoin).
     */
    private fun temoinsArite(
        nom: String,
        univers: UniversPreuves,
        region: NoeudArbre,
    ): List<TemoinArite> {
        val temoins = mutableListOf<TemoinArite>()
        for (appelable in univers.modele.appelables(nom, univers.paquet)) {
            val propre = appelable.uri != "" ||
                declarationPropreDansDocument(univers, nom, region)
            temoins.add(TemoinArite(appelable.parametres, appelable.uri, propre))
        }
        univers.typeProjetEcrit(nom)?.let { type ->
            if (type.parametresPrimaire.isNotEmpty()) {
                val propre = type.uri != null && type.uri != "" ||
                    declarationPropreDansDocument(univers, nom, region)
                temoins.add(TemoinArite(type.parametresPrimaire, type.uri ?: "", propre))
            }
        }
        return temoins
    }

    /**
     * La déclaration du document COURANT portant ce nom de premier niveau
     * est-elle PROPRE (sous-arbre sans marqueur d'erreur — fiche 017) ?
     */
    private fun declarationPropreDansDocument(
        univers: UniversPreuves,
        nom: String,
        region: NoeudArbre,
    ): Boolean {
        var trouve = false
        fun marcher(noeud: NoeudArbre) {
            if (noeud.genre == KtNodeTypes.FUN || noeud.genre == KtNodeTypes.CLASS) {
                val n = noeud.jetonsPropres.firstOrNull { it.genre == KtTokens.IDENTIFIER }?.texte
                if (n == nom) {
                    trouve = sousArbrePropre(noeud)
                }
            }
            noeud.enfants.forEach(::marcher)
        }
        marcher(univers.arbre.noeud)
        return trouve
    }

    /** Le sous-arbre porte-t-il un nœud d'erreur ? */
    private fun sousArbrePropre(noeud: NoeudArbre): Boolean {
        if (noeud.messageErreur != null) return false
        return noeud.enfants.all(::sousArbrePropre)
    }

    // ------------------------------------------------------------------
    // kt.unsafeNullable — le point sur un récepteur nullable.
    // ------------------------------------------------------------------

    /**
     * La preuve d'ACCÈS NON SÛR : un POINT (jamais `?.`) sur un récepteur
     * NOMINAL dont le type écrit est NULLABLE et qu'aucun raffinement
     * positionnel de non-nullité ne couvre (garde `!= null`, garde à
     * sortie précoce `== null` — les smart casts 2b). Le récepteur non
     * nominal (appel, chaîne) ou au type non écrit reste muet (D3).
     */
    private fun verifierAccesNonSur(
        noeud: NoeudArbre,
        univers: UniversPreuves,
        liaison: MarcheTypee.Liaison,
        diagnostics: MutableList<DiagnosticKotlin>,
    ) {
        if (noeud.genre != KtNodeTypes.DOT_QUALIFIED_EXPRESSION) return
        val recepteur = noeud.enfants.firstOrNull() ?: return
        if (recepteur.genre != KtNodeTypes.REFERENCE_EXPRESSION) return
        val nom = recepteur.texteCanonique
        if (nom.isEmpty()) return

        // Le type ÉCRIT du récepteur : nullable au sens du texte (`?`
        // après dépouillement des génériques) — la consultation
        // POSITIONNELLE respecte les ombres internes.
        val typeEcrit = liaison(nom, recepteur.debut)?.trim() ?: return
        if (!typeEcrit.substringBefore('<').trimEnd().endsWith("?")) return

        // Un raffinement de NON-NULLITÉ en position éteint la preuve
        // (garde `!= null`, garde précoce `== null` — fiche 046).
        val raffinement = TypageBorne.raffinementAuPoint(univers.arbre, recepteur.debut, nom)
        if (raffinement != null && (raffinement.nonNul || raffinement.typeNom != null)) return

        diagnostics.add(
            DiagnosticKotlin(
                code = CodeDiagnosticKotlin.ACCES_NON_SUR,
                severite = SeveriteKotlin.ERREUR,
                plage = PlageTexte(recepteur.debut, recepteur.fin),
                message = "Accès non sûr : « $nom » est de type nullable « $typeEcrit » — " +
                    "utilisez l'accès sûr « ?. » ou une garde de nullité",
                tags = emptyList(),
                informationsLiees = emptyList(),
            ),
        )
    }

    // ------------------------------------------------------------------
    // kt.unreachable — la déclaration après un terminal.
    // ------------------------------------------------------------------

    /**
     * La preuve de CODE INATTEIGNABLE : la DÉCLARATION qui suit
     * DIRECTEMENT un `return` ou un `throw` FRÈRE dans le même bloc —
     * seule la PREMIÈRE est signalée (parité minimale) ; un `return`
     * imbriqué dans une sous-instruction n'est pas un terminal du bloc
     * (sous-rapport assumé, fiche 046).
     */
    private fun verifierInatteignable(bloc: NoeudArbre, diagnostics: MutableList<DiagnosticKotlin>) {
        val instructions = bloc.enfants
        for (i in 0 until instructions.size - 1) {
            val courante = instructions[i]
            if (courante.genre != KtNodeTypes.RETURN && courante.genre != KtNodeTypes.THROW) continue
            val suivante = instructions[i + 1]
            if (suivante.genre != KtNodeTypes.PROPERTY && suivante.genre != KtNodeTypes.FUN) continue
            diagnostics.add(
                DiagnosticKotlin(
                    code = CodeDiagnosticKotlin.CODE_INATTEIGNABLE,
                    severite = SeveriteKotlin.AVERTISSEMENT,
                    plage = PlageTexte(suivante.debut, suivante.fin),
                    message = "Code inatteignable : la déclaration suit un « " +
                        (if (courante.genre == KtNodeTypes.RETURN) "return" else "throw") +
                        " » terminal",
                    tags = emptyList(),
                    informationsLiees = emptyList(),
                ),
            )
        }
    }

    // ------------------------------------------------------------------
    // kt.valReassign — l'affectation à un val.
    // ------------------------------------------------------------------

    /**
     * La preuve de RÉAFFECTATION : une affectation (`=`) dont la cible est
     * un NOM SIMPLE lié à un `val` — la locale de la région, le paramètre
     * (toujours `val`) ou la propriété du document ; `var` et cibles
     * introuvables restent muets (fiche 046). Les cibles QUALIFIÉES
     * (`p.x = 1`) relèvent des membres : sous-rapport assumé.
     */
    private fun verifierValReaffectation(
        affectation: NoeudArbre,
        univers: UniversPreuves,
        region: NoeudArbre,
        liaison: MarcheTypee.Liaison,
        diagnostics: MutableList<DiagnosticKotlin>,
    ) {
        // L'opération est un `=` (les opérations composées et les arguments
        // nommés ne sont pas des réaffectations témoins).
        val operation = affectation.enfants
            .firstOrNull { it.genre == KtNodeTypes.OPERATION_REFERENCE } ?: return
        if (operation.jetonsPropres.none { it.genre == KtTokens.EQ }) return
        val cible = affectation.enfants.firstOrNull { it.genre == KtNodeTypes.REFERENCE_EXPRESSION }
            ?: return
        val nom = cible.texteCanonique
        if (nom.isEmpty()) return

        // La LIAISON tranche : un nom sans liaison dans la région n'est pas
        // une cible témoin (garde documentaire de la fiche 017 : un nom
        // déclaré n'importe où n'est jamais signalé).
        if (liaison(nom) == null && !univers.possedeNomDeclare(nom)) return

        when (mutabiliteDe(nom, univers, region)) {
            Mutabilite.VAL -> Unit
            Mutabilite.VAR, Mutabilite.INCONNUE -> return
        }

        diagnostics.add(
            DiagnosticKotlin(
                code = CodeDiagnosticKotlin.REAFFECTATION_VAL,
                severite = SeveriteKotlin.ERREUR,
                plage = PlageTexte(cible.debut, cible.fin),
                message = "Réaffectation impossible : « $nom » est un « val » (utilisez « var » " +
                    "pour le rendre réaffectable)",
                tags = emptyList(),
                informationsLiees = emptyList(),
            ),
        )
    }

    /** La mutabilité d'un nom lié (locale, paramètre, propriété). */
    private enum class Mutabilite {
        VAL,
        VAR,
        INCONNUE,
    }

    /**
     * La MUTABILITÉ du nom : la locale de la région d'abord (la plus
     * profonde — l'ordre de la marche), puis les paramètres, puis les
     * propriétés du document. Un `var` quelconque l'emporte (conservateur
     * — jamais un faux positif) ; INCONNUE pour une cible sans déclaration
     * trouvable (membre hérité, extension… : silence).
     */
    private fun mutabiliteDe(
        nom: String,
        univers: UniversPreuves,
        region: NoeudArbre,
    ): Mutabilite {
        var mutabilite: Mutabilite? = null
        fun noter(trouvee: Mutabilite) {
            mutabilite = when (mutabilite) {
                null -> trouvee
                Mutabilite.VAR -> Mutabilite.VAR
                Mutabilite.INCONNUE -> trouvee
                Mutabilite.VAL -> if (trouvee == Mutabilite.VAR) Mutabilite.VAR else Mutabilite.VAL
            }
        }
        fun marcher(noeud: NoeudArbre) {
            when (noeud.genre) {
                KtNodeTypes.PROPERTY -> {
                    val n = noeud.jetonsPropres.firstOrNull { it.genre == KtTokens.IDENTIFIER }?.texte
                    if (n == nom) {
                        val estVar = noeud.jetonsPropres.any { it.genre == KtTokens.VAR_KEYWORD }
                        noter(if (estVar) Mutabilite.VAR else Mutabilite.VAL)
                    }
                }
                KtNodeTypes.VALUE_PARAMETER -> {
                    val n = noeud.jetonsPropres.firstOrNull { it.genre == KtTokens.IDENTIFIER }?.texte
                    if (n == nom) noter(Mutabilite.VAL)
                }
                else -> Unit
            }
            noeud.enfants.forEach(::marcher)
        }
        // La RÉGION d'abord (locales et paramètres), puis le document.
        marcher(region)
        marcher(univers.arbre.noeud)
        return mutabilite ?: Mutabilite.INCONNUE
    }

    /** Le nom simple ÉCRIT d'une référence de type (nullable déballée). */
    private fun nomTypeEcritDeReference(reference: NoeudArbre): String? {
        var porteur: NoeudArbre =
            reference.enfants.firstOrNull { it.genre == KtNodeTypes.NULLABLE_TYPE } ?: reference
        if (porteur.genre == KtNodeTypes.NULLABLE_TYPE) {
            porteur = porteur.enfants.firstOrNull { it.genre == KtNodeTypes.USER_TYPE } ?: return null
        }
        val utilisateur = porteur.enfants.firstOrNull { it.genre == KtNodeTypes.USER_TYPE }
            ?: porteur.takeIf { it.genre == KtNodeTypes.USER_TYPE }
            ?: return null
        return utilisateur.texteCanonique.substringBefore('<').ifEmpty { null }
    }
}
