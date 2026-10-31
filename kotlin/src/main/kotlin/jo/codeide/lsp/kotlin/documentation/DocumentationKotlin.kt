// CodeIDE — documentation KDoc d'une déclaration (module :kotlin, zone
// documentation — REPRISE VOIE C, manifeste
// docs/PROVENANCE-REPRISE-KOTLIN.md §3 R8). Origine : codeide-lsp
// lsp/kotlin/src/main/kotlin/jo/lspkotlin/documentation/DocumentationKotlin.kt
// @ bc11253 (auteur jjoblab, dépôt propriétaire) — contrôle R9.4 PASSÉ
// (en-têtes sans mention tierce ; re-contrôle au moment de la copie,
// vague 5b). Adaptations de câblage SEULEMENT, listées ici : paquet
// jo.codeide.lsp.kotlin.documentation ; la déclaration paramètre est la
// façade neuve `parse.kt.KtDeclaration` et les jetons de COMMENTAIRE du
// document vivent à la RACINE de l'arbre neuf (kdocDe les reçoit —
// l'ancien modèle attachait le KDoc comme jeton ENFANT de la
// déclaration, le neuf les collecte à la racine : le DOC_COMMENT
// « porté par la déclaration » s'y trouve par la règle de séparation
// blancs-seuls, l'ancre étant le premier jeton propre OU le premier
// enfant — une MODIFIER_LIST d'annotations précède le mot-clé) ; la
// reconstruction de l'arbre KDoc passe par `ArbreSyntaxique.construire`
// (le reconstructeur du modèle neuf, parser-agnostique) ; la marche des
// sections fusionne enfants-productions et jetons PROPRES triés par
// position (le modèle neuf sépare nœuds et jetons). Le cœur (lex +
// parse KDoc vendorés, rendu texte neutre : sections libres en
// paragraphes, tags en lignes `@param nom — texte` / `@return texte`,
// tolérance D3) est repris TEL QUEL.
package jo.codeide.lsp.kotlin.documentation

import com.intellij.platform.syntax.lexer.performLexing
import com.intellij.platform.syntax.parser.SyntaxTreeBuilderFactory
import jo.codeide.lsp.kotlin.parse.ArbreSyntaxique
import jo.codeide.lsp.kotlin.parse.JetonBrut
import jo.codeide.lsp.kotlin.parse.NoeudArbre
import jo.codeide.lsp.kotlin.parse.kt.KtDeclaration
import org.jetbrains.kotlin.kmp.lexer.KDocKnownTag
import org.jetbrains.kotlin.kmp.lexer.KDocLexer
import org.jetbrains.kotlin.kmp.lexer.KDocTokens
import org.jetbrains.kotlin.kmp.lexer.KtTokens
import org.jetbrains.kotlin.kmp.parser.AbstractParser
import org.jetbrains.kotlin.kmp.parser.KDocParseNodes
import org.jetbrains.kotlin.kmp.parser.KDocParser

/**
 * Extraction du KDoc d'une déclaration pour le survol riche (S2 — plan
 * de stabilisation LSP §3.2, « utiliser KDocParser déjà vendoré »).
 *
 * Chaîne d'extraction (miroir du parseur du document, même patron
 * documenté par l'amont) :
 * 1. le commentaire DOC_COMMENT PORTEUR PAR LA DÉCLARATION ELLE-MÊME —
 *    celui qui la précède à blancs-seuls (l'ancre : le premier jeton
 *    propre de la déclaration OU son premier enfant — les annotations
 *    d'une MODIFIER_LIST précèdent le mot-clé, le KDoc les précède
 *    toutes) ;
 * 2. lexification du texte du commentaire par le [KDocLexer] vendored
 *    (il découpe START/END/TEXT/TAG_NAME et STRIPPE les astérisques
 *    d'attente) ;
 * 3. parse par le [KDocParser] vendored (sections KDOC_SECTION,
 *    tags KDOC_TAG) ;
 * 4. reconstruction en arbre immuable ([ArbreSyntaxique.construire] du
 *    modèle neuf) puis rendu en TEXTE NEUTRE : sections libres en
 *    paragraphes, tags en lignes `@param nom — texte` / `@return texte`.
 *
 * Le rendu reste du TEXTE (pas du markdown) : la traduction hôte
 * décide de l'enveloppe (bloc de code Kotlin + documentation — même
 * patron que les serveurs tiers de référence), le moteur n'expose jamais LSP4J (D6).
 *
 * Tolérance (D3) : un commentaire vide, dégénéré ou incompris rend
 * `null` (survol signature seule) — JAMAIS d'exception, une doc
 * absente n'est pas une erreur.
 */
internal object DocumentationKotlin {
    /**
     * KDoc de la déclaration : le DOC_COMMENT qui la précède à
     * blancs-seuls (porté par elle), RENDU en texte lisible.
     * `null` sans commentaire KDoc (un BLOCK_COMMENT n'est PAS un
     * KDoc — il n'est pas rétro-analysé).
     *
     * @param declaration la déclaration documentée ;
     * @param commentaires les jetons de commentaire du document, en
     * ordre du document (la racine de l'arbre neuf les porte) ;
     * @return le KDoc rendu ; `null` sans KDoc ou si le rendu ne trouve
     * rien de lisible.
     */
    internal fun kdocDe(declaration: KtDeclaration, commentaires: List<JetonBrut>): String? =
        commentaireDeDeclaration(declaration, commentaires)?.let { rendre(it.texte) }

    /**
     * Le DOC_COMMENT de la déclaration : le commentaire qui la précède
     * IMMÉDIATEMENT (des blancs seuls séparent) — l'ancre de la
     * déclaration est son premier jeton PROPRE ou son PREMIER ENFANT
     * (le plus tôt des deux : une MODIFIER_LIST d'annotations précède
     * le mot-clé, `/** doc */ @Deprecated class Vieux` porte sa doc).
     */
    private fun commentaireDeDeclaration(
        declaration: KtDeclaration,
        commentaires: List<JetonBrut>,
    ): JetonBrut? {
        val ancre = minOf(
            declaration.noeud.jetonsPropres.firstOrNull()?.debut ?: Int.MAX_VALUE,
            declaration.noeud.enfants.firstOrNull()?.debut ?: Int.MAX_VALUE,
        ).let { if (it == Int.MAX_VALUE) declaration.noeud.debut else it }
        val candidat = commentaires.lastOrNull { it.fin <= ancre } ?: return null
        // Séparation : uniquement des blancs entre la fin du commentaire
        // et l'ancre de la déclaration (testé sur le SOURCE).
        for (indice in candidat.fin until ancre) {
            if (indice < declaration.source.length &&
                !declaration.source[indice].isWhitespace()
            ) {
                return null
            }
        }
        return if (candidat.genre == KtTokens.DOC_COMMENT) candidat else null
    }

    /**
     * Rendu du texte d'un KDoc (`/** … */`) : lex + parse vendorés puis
     * marche de rendu. `null` si rien de lisible (commentaire vide).
     */
    private fun rendre(texteKdoc: CharSequence): String? {
        val arbre = arbreKdoc(texteKdoc) ?: return null
        val lignes = mutableListOf<String>()
        arbre.enfants
            .filter { it.genre == KDocParseNodes.KDOC_SECTION }
            .forEach { section -> rendreSection(section, lignes) }
        return normaliser(lignes)
    }

    /**
     * Lex + parse du KDoc vendorés, reconstruction en arbre immuable
     * (le reconstructeur du modèle NEUF — parser-agnostique). Toute
     * exception du chemin vendoré se dégrade en `null` (D3 : une doc
     * qui résiste au parse n'est pas une erreur de survol).
     *
     * Adaptation de câblage : la reconstruction passe par un VUE du
     * parseur KDoc aux trivia VIDES ([KDocVuePleine]) — le modèle neuf
     * EXCLUT les blancs des jetons propres, or le rendu vit de la
     * STRUCTURE des blancs du KDoc (les retours à la ligne des
     * paragraphes, l'espace entre référence et description d'un tag) :
     * la vue les rend jetons plein, la marche les retrouve.
     */
    private fun arbreKdoc(texteKdoc: CharSequence): NoeudArbre? =
        try {
            val jetons = performLexing(texteKdoc, KDocLexer(), null, null)
            val builder =
                SyntaxTreeBuilderFactory
                    .builder(texteKdoc, jetons, KDocParser.whitespaces, KDocParser.comments)
                    .withWhitespaceOrCommentBindingPolicy(KDocParser.whitespaceOrCommentBindingPolicy)
                    .build()
            KDocParser.parse(builder)
            ArbreSyntaxique.construire(builder, KDocVuePleine)
        } catch (_: Throwable) {
            null
        }

    /**
     * La VUE du parseur KDoc aux ensembles de trivia VIDES : même parse
     * ([KDocParser.parse]), mais la reconstruction y garde les blancs
     * comme jetons pleins (le rendu du KDoc en dépend — adaptation de
     * câblage documentée ci-dessus).
     */
    private object KDocVuePleine : AbstractParser() {
        override val whitespaces: Set<com.intellij.platform.syntax.SyntaxElementType> = emptySet()

        override val comments: Set<com.intellij.platform.syntax.SyntaxElementType> = emptySet()

        override fun parse(builder: com.intellij.platform.syntax.parser.SyntaxTreeBuilder) {
            KDocParser.parse(builder)
        }
    }

    /** Rendu d'une section : texte libre en un paragraphe, tags en lignes. */
    private fun rendreSection(
        section: NoeudArbre,
        lignes: MutableList<String>,
    ) {
        val libre = StringBuilder()
        for (enfant in fusionner(section)) {
            accumulerOuTaguer(enfant, libre, lignes)
        }
        texteLibre(libre)?.let { lignes.add(it) }
    }

    /**
     * Les éléments DIRECTS d'une section en ordre du document : les
     * productions enfants (KDOC_TAG…) et les jetons PROPRES (texte
     * libre, blancs) fusionnés par position — le modèle neuf sépare
     * nœuds et jetons, la marche les réunit.
     */
    private fun fusionner(section: NoeudArbre): List<ElementSection> {
        val elements = mutableListOf<ElementSection>()
        section.enfants.forEach { elements.add(ElementSection.Noeud(it)) }
        section.jetonsPropres.forEach { elements.add(ElementSection.Jeton(it)) }
        return elements.sortedBy { element -> element.debut }
    }

    /** Un élément direct d'une section : production ou jeton. */
    private sealed class ElementSection {
        abstract val debut: Int

        class Noeud(val noeud: NoeudArbre) : ElementSection() {
            override val debut: Int get() = noeud.debut
        }

        class Jeton(val jeton: JetonBrut) : ElementSection() {
            override val debut: Int get() = jeton.debut
        }
    }

    /**
     * Un élément de section : soit un TAG rendu en ligne (le texte libre
     * accumulé est flushé avant), soit du contenu libre accumulé, soit
     * de la structure ignorée (astérisques d'attente, balises markdown,
     * sections imbriquées inattendues).
     */
    private fun accumulerOuTaguer(
        enfant: ElementSection,
        libre: StringBuilder,
        lignes: MutableList<String>,
    ) {
        val tag =
            if (enfant is ElementSection.Noeud && enfant.noeud.genre == KDocParseNodes.KDOC_TAG) {
                tagLigne(enfant.noeud)
            } else {
                null
            }
        if (tag != null) {
            texteLibre(libre)?.let { lignes.add(it) }
            lignes.add(tag)
            return
        }
        texteDeContenu(enfant)?.let { libre.append(it) }
    }

    /** Le texte du jeton de CONTENU, ou `null` (structure ignorée). */
    private fun texteDeContenu(enfant: ElementSection): String? =
        when {
            enfant !is ElementSection.Jeton -> null

            enfant.jeton.genre == KtTokens.WHITE_SPACE ||
                enfant.jeton.genre == KDocTokens.TEXT ||
                enfant.jeton.genre == KDocTokens.CODE_BLOCK_TEXT -> enfant.jeton.texte

            else -> null
        }

    /**
     * Ligne d'un tag : `@param nom — le texte` (tag à référence), ou
     * `@return le texte` (tag sans référence). Sans contenu : le tag
     * seul (`@return`). Les blancs du contenu sont ramenés à des
     * espaces (une ligne de tag reste UNE ligne de survol).
     */
    private fun tagLigne(tag: NoeudArbre): String? {
        val nomTag =
            jetonsDescendants(tag).firstOrNull { it.genre == KDocTokens.TAG_NAME } ?: return null
        // Le jeton TAG_NAME PORTE le `@` (« @param ») : rendu sans, la
        // recherche vendored (findByTagName) le tolère tel quel.
        val brut = nomTag.texte
        val nom = brut.removePrefix("@")
        val contenu =
            jetonsDescendants(tag)
                .filter { it.genre != KDocTokens.TAG_NAME && it.genre != KDocTokens.LEADING_ASTERISK }
                .joinToString("") { it.texte }
                .replace(Regex("\\s+"), " ")
                .trim()
        val referenceRequise = KDocKnownTag.findByTagName(brut)?.isReferenceRequired ?: false
        return when {
            contenu.isEmpty() -> "@$nom"

            referenceRequise -> {
                val espace = contenu.indexOf(' ')
                if (espace <= 0) "@$nom $contenu" else "@$nom ${contenu.take(espace)} — ${contenu.drop(espace + 1)}"
            }

            else -> "@$nom $contenu"
        }
    }

    /**
     * Les jetons du sous-arbre d'un tag en ordre du document (les
     * jetons propres du nœud vivent AUTOUR de ceux de ses enfants — la
     * fusion se trie par position).
     */
    private fun jetonsDescendants(noeud: NoeudArbre): List<JetonBrut> {
        val jetons = mutableListOf<JetonBrut>()
        jetons.addAll(noeud.jetonsPropres)
        for (enfant in noeud.enfants) {
            jetons.addAll(jetonsDescendants(enfant))
        }
        return jetons.sortedBy { it.debut }
    }

    /**
     * Normalise le texte libre accumulé : les blancs de l'arbre
     * reproduisent la source (retours à la ligne compris) — chaque
     * ligne est ramenée aux espaces utiles, les lignes vides de bords
     * disparaissent. `null` si rien de significatif.
     */
    private fun texteLibre(accummule: StringBuilder): String? {
        if (accummule.isBlank()) return null
        val rendu = accummule.lines().joinToString("\n") { ligne -> ligne.trim() }
        accummule.clear()
        return rendu.ifBlank { null }
    }

    /** Nettoie la liste de lignes (trim, bords vides) ; `null` si vide. */
    private fun normaliser(lignes: List<String>): String? {
        val propres = lignes.map { it.trim() }
        val debut = propres.indexOfFirst { it.isNotEmpty() }
        val fin = propres.indexOfLast { it.isNotEmpty() }
        if (debut < 0 || debut > fin) return null
        return propres.subList(debut, fin + 1).joinToString("\n").ifBlank { null }
    }
}
