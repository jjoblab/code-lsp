// CodeIDE — correctifs de TEXTE d'un document (module :kotlin, étage 2
// vague 2f — fiche 016 du cahier). Code NEUF jjoblab (clean-room) : les
// familles « accès non sûr » (`?.`, `!!`, entourage `if (x != null)`) et
// « conversion de littéral » (changement de type + conversion fermée par
// famille). Le moteur RE-PROUVE le document — un correctif existe si et
// seulement si le diagnostic correspondant existe (alignement par
// construction, jamais un diagnostic périmé).
package jo.codeide.lsp.kotlin.session

import jo.codeide.lsp.kotlin.api.CorrectifTexteKotlin
import jo.codeide.lsp.kotlin.parse.NoeudArbre
import jo.codeide.lsp.kotlin.parse.kt.KtFichier
import jo.codeide.lsp.kotlin.parse.kt.PlageTexte
import jo.codeide.lsp.kotlin.resolve.MarcheTypee
import jo.codeide.lsp.kotlin.resolve.TypageBorne
import jo.codeide.lsp.kotlin.resolve.UniversPreuves
import org.jetbrains.kotlin.kmp.lexer.KtTokens
import org.jetbrains.kotlin.kmp.parser.KtNodeTypes

/**
 * Les correctifs de texte (pur — aucun état, appelé sous verrou de
 * lecture) : chaque famille dérive SES diagnostics par les MÊMES
 * prédicats que la passe typée ([DiagnosticsTypage]) — l'alignement
 * « un correctif si et seulement si le diagnostic correspondant » tient
 * par construction (les deux marchent le même arbre avec les mêmes
 * gardes).
 *
 * ## Les familles (fiche 016)
 *
 * - **Accès non sûr** (`x.foo` à récepteur nominal nullable, hors garde)
 *   : « Accès sûr » remplace le POINT du segment par `?.` ; « Assertion
 *   non nulle » INSÈRE `!!` après le récepteur ; « Entourer » remplace
 *   l'INSTRUCTION entière par `if (x != null) { … }` (multi-ligne quand
 *   l'instruction s'étend sur plusieurs lignes, EN LIGNE quand elle
 *   partage sa ligne) — l'entourage n'est pas proposé au PREMIER NIVEAU
 *   sans bloc (D3).
 * - **Conversion de littéral** (propriété à type écrit d'une autre
 *   famille que son littéral) : « Changer le type » remplace le type
 *   écrit par la famille du littéral ; « Convertir » insère la
 *   conversion FERMÉE par famille (`.toInt()`, `.toString()`…) après le
 *   littéral — une cible NON PRIMITIVE ne reçoit que le changement de
 *   type, un type NULLABLE ou une conversion numérique légitime ne
 *   reçoivent RIEN (la passe n'émet pas, le correctif non plus).
 */
internal object CorrectifsTexte {

    /** Les conversions FERMÉES par famille (fiche 016 — matrice close). */
    private val CONVERSIONS: Map<String, String> = mapOf(
        "String to Int" to "toInt",
        "String to Long" to "toLong",
        "String to Double" to "toDouble",
        "Int to String" to "toString",
        "Long to String" to "toString",
        "Double to String" to "toString",
        "Boolean to String" to "toString",
        "Char to String" to "toString",
    )

    /** Les types PRIMITIFS (les autres cibles : changement de type seul). */
    private val PRIMITIFS: Set<String> = setOf(
        "String", "Int", "Long", "Double", "Float", "Boolean", "Char", "Short", "Byte",
    )

    /**
     * Calcule les correctifs du document.
     *
     * @param document le document ouvert (texte + arbre) ;
     * @param uri l'identifiant du document ;
     * @param univers l'univers des preuves (la même matière que la passe) ;
     * @return les correctifs en ordre du document ; liste vide sans
     * diagnostic corrigeable.
     */
    internal fun calculer(
        document: DocumentOuvert,
        uri: String,
        univers: UniversPreuves,
    ): List<CorrectifTexteKotlin> {
        val correctifs = mutableListOf<CorrectifTexteKotlin>()
        val liaisonDocument = MarcheTypee.Liaison(arbre = univers.arbre, univers = univers, region = null)
        marcher(document.arbre.noeud, univers, liaisonDocument, document.texte, document.arbre.noeud, null, correctifs)
        return correctifs
    }

    /**
     * La marche des nœuds (les deux familles se rencontrent en profondeur).
     * La RÉGION suivie est la PREMIÈRE fonction/propriété ENGLOBANTE
     * (l'outermost — miroir de la passe typée : la liaison d'une fonction
     * couvre ses propriétés locales, les paramètres y vivent).
     */
    private fun marcher(
        noeud: NoeudArbre,
        univers: UniversPreuves,
        liaison: MarcheTypee.Liaison,
        texte: CharSequence,
        racine: NoeudArbre,
        region: NoeudArbre?,
        correctifs: MutableList<CorrectifTexteKotlin>,
    ) {
        val regionCourante = region ?: noeud.takeIf {
            it.genre == KtNodeTypes.FUN || it.genre == KtNodeTypes.PROPERTY
        }
        val liaisonCourante = if (regionCourante != null && regionCourante !== region) {
            MarcheTypee.Liaison(arbre = univers.arbre, univers = univers, region = regionCourante)
        } else {
            liaison
        }
        if (noeud.genre == KtNodeTypes.DOT_QUALIFIED_EXPRESSION) {
            correctifsAccesNonSur(noeud, univers, liaisonCourante, texte, racine, correctifs)
        }
        if (noeud.genre == KtNodeTypes.PROPERTY) {
            correctifsConversion(noeud, texte, correctifs)
        }
        noeud.enfants.forEach { marcher(it, univers, liaisonCourante, texte, racine, regionCourante, correctifs) }
    }

    // ------------------------------------------------------------------
    // Famille 1 : l'accès non sûr.
    // ------------------------------------------------------------------

    /** Les correctifs d'un accès non sûr (les MÊMES gardes que la passe). */
    private fun correctifsAccesNonSur(
        noeud: NoeudArbre,
        univers: UniversPreuves,
        liaison: MarcheTypee.Liaison,
        texte: CharSequence,
        racine: NoeudArbre,
        correctifs: MutableList<CorrectifTexteKotlin>,
    ) {
        val recepteur = noeud.enfants.firstOrNull() ?: return
        if (recepteur.genre != KtNodeTypes.REFERENCE_EXPRESSION) return
        val nom = recepteur.texteCanonique
        if (nom.isEmpty()) return

        // Le type ÉCRIT nullable (miroir de [DiagnosticsTypage]) et les
        // gardes de non-nullité (un accès sûr ou une garde ne reçoit
        // RIEN, D3 — la passe n'émet pas, le correctif non plus).
        val typeEcrit = liaison(nom, recepteur.debut)?.trim() ?: return
        if (!typeEcrit.substringBefore('<').trimEnd().endsWith("?")) return
        val raffinement = TypageBorne.raffinementAuPoint(univers.arbre, recepteur.debut, nom)
        if (raffinement != null && (raffinement.nonNul || raffinement.typeNom != null)) return

        // 1. « Accès sûr » : le POINT du segment (le plus profond porté
        //    par ce récepteur) devient `?.`.
        if (recepteur.fin < noeud.fin) {
            correctifs.add(
                CorrectifTexteKotlin(
                    titre = "Accès sûr : $nom?.",
                    plage = PlageTexte(recepteur.fin, recepteur.fin + 1),
                    texte = "?.",
                ),
            )
        }

        // 2. « Assertion non nulle » : `!!` inséré après le récepteur.
        correctifs.add(
            CorrectifTexteKotlin(
                titre = "Assertion non nulle : $nom!!",
                plage = PlageTexte(recepteur.fin, recepteur.fin),
                texte = "!!",
            ),
        )

        // 3. « Entourer » : l'INSTRUCTION entière — seulement dans un
        //    BLOC (le premier niveau sans bloc n'est pas entourable, D3).
        instructionContenant(racine, noeud)?.let { instruction ->
            // L'entourage MULTI-LIGNE est la FORME PAR DÉFAUT ; il est EN
            // LIGNE quand l'instruction PARTAGE SA LIGNE avec du contenu
            // voisin (frères sur la même ligne — le `;` du test de la
            // fiche) : le bloc généré épargne alors la ligne.
            val partageSaLigne = partageSaLigne(texte, instruction)
            val entoure = if (partageSaLigne) {
                "if ($nom != null) { " + extrait(texte, instruction) + " }"
            } else {
                "if ($nom != null) {\n" + extrait(texte, instruction) + "\n}"
            }
            correctifs.add(
                CorrectifTexteKotlin(
                    titre = "Entourer d'une garde de nullité : if ($nom != null)",
                    plage = PlageTexte(instruction.debut, instruction.fin),
                    texte = entoure,
                ),
            )
        }
    }

    /**
     * L'INSTRUCTION (l'enfant direct d'un BLOC) contenant le nœud ; `null`
     * au premier niveau sans bloc (D3 — pas d'entourage) : la marche
     * descend depuis la racine en gardant le DERNIER enfant de BLOC qui
     * couvre la cible.
     */
    private fun instructionContenant(racine: NoeudArbre, cible: NoeudArbre): NoeudArbre? {
        var resultat: NoeudArbre? = null
        fun descendre(noeud: NoeudArbre) {
            if (cible.debut < noeud.debut || cible.fin > noeud.fin) return
            if (noeud.genre == KtNodeTypes.BLOCK) {
                for (enfant in noeud.enfants) {
                    if (cible.debut >= enfant.debut && cible.fin <= enfant.fin) {
                        resultat = enfant
                        descendre(enfant)
                    }
                }
            } else {
                noeud.enfants.forEach(::descendre)
            }
        }
        descendre(racine)
        return resultat
    }

    // ------------------------------------------------------------------
    // Famille 2 : la conversion de littéral.
    // ------------------------------------------------------------------

    /** Les correctifs d'une propriété à littéral hors famille (fiche 016). */
    private fun correctifsConversion(
        propriete: NoeudArbre,
        texte: CharSequence,
        correctifs: MutableList<CorrectifTexteKotlin>,
    ) {
        val reference = propriete.enfants.firstOrNull { it.genre == KtNodeTypes.TYPE_REFERENCE } ?: return
        val typeEcrit = nomTypeEcritDe(reference) ?: return
        val initialiseur = propriete.enfants.lastOrNull {
            it.genre != KtNodeTypes.TYPE_REFERENCE &&
                it.genre != KtNodeTypes.MODIFIER_LIST &&
                it.genre != KtNodeTypes.PROPERTY_DELEGATE &&
                it.genre != KtNodeTypes.PROPERTY_ACCESSOR
        } ?: return
        val familleLitteral = TypageBorne.familleLitteral(initialiseur) ?: return
        if (familleLitteral == familleDeTypeEcrit(typeEcrit)) return
        if (familleDeTypeEcrit(typeEcrit) == "NUMERIQUE" && familleDeTypeEcrit(familleLitteral) == "NUMERIQUE") return

        // 1. « Changer le type » : le type écrit devient la famille du
        //    littéral (toujours proposé — cible primitive ou non).
        correctifs.add(
            CorrectifTexteKotlin(
                titre = "Changer le type en $familleLitteral",
                plage = PlageTexte(reference.debut, reference.fin),
                texte = familleLitteral,
            ),
        )

        // 2. « Convertir » : la conversion FERMÉE par famille — cible
        //    PRIMITIVE seule (une cible non primitive reçoit le
        //    changement de type seul, D3).
        if (typeEcrit in PRIMITIFS) {
            CONVERSIONS["$familleLitteral to $typeEcrit"]?.let { conversion ->
                correctifs.add(
                    CorrectifTexteKotlin(
                        titre = "Convertir le littéral : .$conversion()",
                        plage = PlageTexte(initialiseur.fin, initialiseur.fin),
                        texte = ".$conversion()",
                    ),
                )
            }
        }
    }

    /** Le nom simple du type écrit d'une référence (nullable déballée). */
    private fun nomTypeEcritDe(reference: NoeudArbre): String? {
        var porteur: NoeudArbre = reference.enfants.firstOrNull { it.genre == KtNodeTypes.NULLABLE_TYPE } ?: reference
        if (porteur.genre == KtNodeTypes.NULLABLE_TYPE) {
            porteur = porteur.enfants.firstOrNull { it.genre == KtNodeTypes.USER_TYPE } ?: return null
        }
        val utilisateur = porteur.enfants.firstOrNull { it.genre == KtNodeTypes.USER_TYPE } ?: porteur
        return utilisateur.texteCanonique.substringBefore('<').substringBefore('?').trim().ifEmpty { null }
    }

    /** La famille d'un type écrit (miroir borné de la passe typée). */
    private fun familleDeTypeEcrit(nom: String): String? = when (nom) {
        "String" -> "String"
        "Int", "Long", "Short", "Byte", "Double", "Float" -> "NUMERIQUE"
        "Boolean" -> "Boolean"
        "Char" -> "Char"
        else -> null
    }

    /**
     * L'instruction PARTAGE-T-ELLE SA LIGNE : du contenu non blanc avant
     * elle sur sa ligne (après le retour précédent) ou après elle (avant
     * le retour suivant) — un point-virgule voisin, une accolade.
     */
    private fun partageSaLigne(texte: CharSequence, instruction: NoeudArbre): Boolean {
        val borne = texte.length
        var debutLigne = instruction.debut
        while (debutLigne > 0 && texte[debutLigne - 1] != '\n') debutLigne--
        if (texte.substring(debutLigne, instruction.debut).any { !it.isWhitespace() }) return true
        var finLigne = instruction.fin.coerceAtMost(borne)
        while (finLigne < borne && texte[finLigne] != '\n') finLigne++
        if (texte.substring(instruction.fin.coerceAtMost(borne), finLigne).any { !it.isWhitespace() }) return true
        return false
    }

    /** L'extrait texte d'un nœud. */
    private fun extrait(texte: CharSequence, noeud: NoeudArbre): String =
        texte.substring(noeud.debut.coerceIn(0, texte.length), noeud.fin.coerceIn(0, texte.length))
}
