// CodeIDE — coloration SÉMANTIQUE bornée (module :kotlin, vague V-I2 —
// ADR 0136 étape B.3 : semanticTokens fondé palier 2, ADR 0123/KH1 : le
// jeton ne porte JAMAIS la variance ni la nullabilité inférées — la
// légende est l'ensemble des catégories PROUVÉES sur l'arbre + la
// résolution bornée). Code NEUF jjoblab (clean-room — miroir de la
// coloration Java V-G ADR 0031 (a) : les identifiants PORTANTS se
// classent par leur PREUVE de résolution ; un nœud sans preuve est
// IGNORÉ — silence honnête D3, jamais une couleur fausse).
package jo.codeide.lsp.kotlin.session

import jo.codeide.lsp.kotlin.api.GenreJetonSemantiqueKotlin
import jo.codeide.lsp.kotlin.api.JetonSemantiqueKotlin
import jo.codeide.lsp.kotlin.parse.NoeudArbre
import jo.codeide.lsp.kotlin.parse.kt.KtFichier
import jo.codeide.lsp.kotlin.resolve.MarcheTypee
import jo.codeide.lsp.kotlin.resolve.TypeValeur
import jo.codeide.lsp.kotlin.resolve.TypageBorne
import jo.codeide.lsp.kotlin.resolve.UniversPreuves
import org.jetbrains.kotlin.kmp.lexer.KtTokens
import org.jetbrains.kotlin.kmp.parser.KtNodeTypes

/**
 * La coloration sémantique BORNÉE du document Kotlin (V-I2) : la marche
 * de l'arbre classe chaque identifiant PORTANT par sa preuve —
 *
 * - une DÉCLARATION de type porte le genre du modèle (`class`, `interface`,
 *   `enum class` — annotation si son nom est utilisé en position
 *   `@Anno`) ;
 * - une référence en position de TYPE résout vers le modèle (genre du
 *   type projet) ou vers l'index du classpath (CLASSE — le genre binaire
 *   exact relève de la lecture des descripteurs hors périmètre borné :
 *   une classe serve en repli honnête, jamais une interface devinée) ;
 * - une DÉCLARATION `fun`/`val`/`var` de membre porte METHODE/PROPRIÉTÉ ;
 * - un appel NU résolu vers un appelable du projet porte METHODE ;
 * - un PARAMÈTRE déclaré porte PARAMETRE ; une LOCALE déclarée porte
 *   VARIABLE.
 *
 * **Honnêtetés** (miroir Java) : les IMPORTS ne sont pas jetonisés (la
 * coloration d'import relève du langage) ; les MOTS-CLÉS, littéraux et
 * commentaires non plus (la coloration syntaxique du client les couvre) ;
 * un nom SANS preuve exploitable est IGNORÉ (une tokenisation partielle
 * vaut mieux qu'une couleur fausse — D3).
 */
internal object ColorationSemantique {

    /**
     * Calcule les jetons sémantiques du document.
     *
     * @param arbre l'arbre du document ;
     * @param univers l'univers des preuves ;
     * @return les jetons TRIÉS par offset croissant, DÉDOUUBLONNÉS (un
     * même identifiant ne produit qu'un jeton — la déclaration, plus
     * riche, gagne la fusion).
     */
    internal fun jetons(arbre: KtFichier, univers: UniversPreuves): List<JetonSemantiqueKotlin> {
        val collecteur = Collecteur()
        marcher(arbre.noeud, arbre, univers, collecteur)
        return collecteur.rassembler()
    }

    /** La marche de collecte : visite les nœuds porteurs d'identifiants. */
    private fun marcher(
        noeud: NoeudArbre,
        arbre: KtFichier,
        univers: UniversPreuves,
        collecteur: Collecteur,
    ) {
        when (noeud.genre) {
            KtNodeTypes.CLASS -> {
                val nom = nomDeDeclaration(noeud) ?: return
                val genre = univers.modele.typeParNomSimple(nom, univers.paquet)?.genre
                collecteur.poser(
                    nom, noeud, genreDeclaration = genre?.let(::genreDeTypeProjet),
                    declaration = true,
                    statique = false,
                    final = porteurFinalDeclaration(noeud, univers, nom),
                    abstrait = porteurAbstraiteDeclaration(noeud),
                )
            }
            KtNodeTypes.OBJECT_DECLARATION -> {
                val nom = nomDeDeclaration(noeud) ?: return
                collecteur.poser(
                    nom, noeud, genreDeclaration = GenreJetonSemantiqueKotlin.CLASSE,
                    declaration = true,
                    statique = porteurCompagnon(noeud),
                    final = true,
                    abstrait = false,
                )
            }
            KtNodeTypes.FUN -> {
                val nom = nomDeDeclaration(noeud) ?: return
                val estMembre = estDansCorpsDeClasse(noeud, arbre)
                if (estMembre) {
                    collecteur.poser(
                        nom, noeud, genreDeclaration = GenreJetonSemantiqueKotlin.METHODE,
                        declaration = true,
                        statique = estDansCompagnon(noeud, arbre),
                        final = false,
                        abstrait = noeud.enfants.none { it.genre == KtNodeTypes.BLOCK },
                    )
                } else {
                    // Une fonction de premier niveau : METHODE aussi (la
                    // légende Kotlin/IntelliJ classe `fun` en fonction).
                    collecteur.poser(
                        nom, noeud, genreDeclaration = GenreJetonSemantiqueKotlin.METHODE,
                        declaration = true,
                        statique = true,
                        final = false,
                        abstrait = noeud.enfants.none { it.genre == KtNodeTypes.BLOCK },
                    )
                }
            }
            KtNodeTypes.PROPERTY -> {
                val nom = nomDeDeclaration(noeud) ?: return
                val estMembre = estDansCorpsDeClasse(noeud, arbre)
                val genre = if (estMembre) {
                    GenreJetonSemantiqueKotlin.PROPRIETE
                } else {
                    GenreJetonSemantiqueKotlin.VARIABLE
                }
                collecteur.poser(
                    nom, noeud, genreDeclaration = genre,
                    declaration = true,
                    statique = estDansCompagnon(noeud, arbre),
                    final = noeud.jetonsPropres.any { it.genre == KtTokens.VAL_KEYWORD },
                    abstrait = noeud.jetonsPropres.none { it.genre == KtTokens.EQ },
                )
            }
            KtNodeTypes.VALUE_PARAMETER -> {
                val nom = nomDeDeclaration(noeud) ?: return
                collecteur.poser(
                    nom, noeud, genreDeclaration = GenreJetonSemantiqueKotlin.PARAMETRE,
                    declaration = true,
                    statique = false,
                    final = noeud.jetonsPropres.any { it.genre == KtTokens.VAL_KEYWORD },
                    abstrait = false,
                )
            }
            KtNodeTypes.REFERENCE_EXPRESSION -> {
                // Une référence : sa preuve se lit en contexte (le parent
                // décide — une référence de TYPE vs un appel vs un
                // sélecteur). La collecte se fait au parent (CALL ou
                // sélecteur) ; ici : le nom NU dont la liaison type ou
                // dont l'appelable du projet est CONNU.
                val nom = noeud.texteCanonique
                if (nom.isNotEmpty() && !nom.contains('.')) {
                    collecteur.poserReference(nom, noeud, arbre, univers)
                }
            }
            else -> Unit
        }
        for (enfant in noeud.enfants) {
            marcher(enfant, arbre, univers, collecteur)
        }
    }

    // ── Aides de preuve ─────────────────────────────────────────────

    /** Le nom déclaré d'un nœud (premier IDENTIFIER propre). */
    private fun nomDeDeclaration(noeud: NoeudArbre): String? =
        noeud.jetonsPropres.firstOrNull { it.genre == KtTokens.IDENTIFIER }?.texte

    /** Le nœud vit-il dans le CORPS d'une classe (membre) ? */
    private fun estDansCorpsDeClasse(noeud: NoeudArbre, arbre: KtFichier): Boolean =
        ancetreCorpsDeClasse(noeud, arbre.noeud) != null

    /** Le nœud vit-il dans le COMPAGNON d'une classe (portée statique) ? */
    private fun estDansCompagnon(noeud: NoeudArbre, arbre: KtFichier): Boolean {
        val ancetre = ancetreCorpsDeClasse(noeud, arbre.noeud) ?: return false
        return ancetre.enfants.any {
            it.genre == KtNodeTypes.OBJECT_DECLARATION &&
                it.enfants.any { enfant -> enfant.genre == KtNodeTypes.MODIFIER_LIST } &&
                porteurCompagnon(it)
        }
    }

    /** La déclaration porte-t-elle `companion` ? */
    private fun porteurCompagnon(noeud: NoeudArbre): Boolean =
        noeud.enfants
            .filter { it.genre == KtNodeTypes.MODIFIER_LIST }
            .any { liste -> liste.enfants.any { it.genre == KtTokens.COMPANION_MODIFIER } }

    /** Le premier ancêtre CLASS_BODY du nœud (la classe porteuse) ; `null` sinon. */
    private fun ancetreCorpsDeClasse(noeud: NoeudArbre, racine: NoeudArbre): NoeudArbre? {
        var porteuse: NoeudArbre? = null
        fun marcher(ancetre: NoeudArbre) {
            if (noeud.debut >= ancetre.debut && noeud.fin <= ancetre.fin) {
                if (ancetre.genre == KtNodeTypes.CLASS_BODY) porteuse = ancetre
                ancetre.enfants.forEach(::marcher)
            }
        }
        marcher(racine)
        return porteuse
    }

    /** Une déclaration de TYPE est-elle `final` (classe sans `open`) ? */
    private fun porteurFinalDeclaration(noeud: NoeudArbre, univers: UniversPreuves, nom: String): Boolean {
        val genre = univers.modele.typeParNomSimple(nom, univers.paquet)?.genre ?: return true
        if (genre == jo.codeide.lsp.kotlin.resolve.GenreTypeProjet.INTERFACE) return true
        return noeud.enfants
            .filter { it.genre == KtNodeTypes.MODIFIER_LIST }
            .none { liste -> liste.enfants.any { it.genre == KtTokens.OPEN_MODIFIER } }
    }

    /** Une déclaration de TYPE est-elle abstraite (`abstract`/`interface`/`sealed`) ? */
    private fun porteurAbstraiteDeclaration(noeud: NoeudArbre): Boolean =
        noeud.enfants
            .filter { it.genre == KtNodeTypes.MODIFIER_LIST }
            .any { liste ->
                liste.enfants.any {
                    it.genre == KtTokens.ABSTRACT_MODIFIER || it.genre == KtTokens.SEALED_MODIFIER
                }
            }

    /**
     * Le collecteur : accumule par offset, la DÉCLARATION (plus riche)
     * gagne la fusion d'une référence concurrente du même identifiant.
     */
    private class Collecteur {

        /** Les jetons par (offset, nom) — la fusion se fait au rassemblement. */
        private val jetons = mutableListOf<JetonSemantiqueKotlin>()

        /**
         * Pose un jeton de DÉCLARATION (le nom introduit par le nœud).
         *
         * @param nom le nom déclaré ;
         * @param noeud le nœud déclarant (le jeton couvre l'IDENTIFIER) ;
         * @param genreDeclaration le genre PROUVÉ (`null` = inconnu — le
         * jeton ne se pose PAS : une couleur fausse est pire qu'une
         * absence) ;
         */
        fun poser(
            nom: String,
            noeud: NoeudArbre,
            genreDeclaration: GenreJetonSemantiqueKotlin?,
            declaration: Boolean,
            statique: Boolean,
            final: Boolean,
            abstrait: Boolean,
        ) {
            if (genreDeclaration == null) return
            val identifiant = noeud.jetonsPropres.firstOrNull {
                it.genre == KtTokens.IDENTIFIER && it.texte == nom
            } ?: return
            jetons.add(
                JetonSemantiqueKotlin(
                    offset = identifiant.debut,
                    longueur = identifiant.fin - identifiant.debut,
                    genre = genreDeclaration,
                    declaration = declaration,
                    statique = statique,
                    final = final,
                    abstrait = abstrait,
                ),
            )
        }

        /**
         * Pose la référence d'un nom NU : sa preuve — l'appelable du projet
         * (METHODE), la liaison typée de la portée (VARIABLE/PARAMETRE), la
         * résolution de type (genre du type projet), l'entrée CLASSPATH
         * (CLASSE en repli — jamais une interface devinée) ; sans preuve,
         * RIEN (D3).
         */
        fun poserReference(
            nom: String,
            noeud: NoeudArbre,
            arbre: KtFichier,
            univers: UniversPreuves,
        ) {
            // 1. La liaison typée de la portée : une LOCALE ou un
            //    PARAMÈTRE — la VARIABLE/PARAMETRE du palier 1.
            //    (La preuve se lit par le modèle de la région — trop lourd
            //    ici : la référence LOCALE se prouve par les portées de la
            //    liaison de la région couvrante.)
            // 2. La résolution de TYPE : le genre du type projet.
            univers.typeProjetEcrit(nom)?.let { type ->
                poser(nom, noeud, genreDeclaration = genreDeTypeProjet(type.genre), declaration = false, statique = false, final = false, abstrait = false)
                return
            }
            // 3. L'appelable du projet : METHODE (le nom d'une fonction
            //    de premier niveau référencée).
            if (univers.modele.appelables(nom, univers.paquet).isNotEmpty() ||
                univers.modele.nomsAppelables().contains(nom)
            ) {
                poser(nom, noeud, genreDeclaration = GenreJetonSemantiqueKotlin.METHODE, declaration = false, statique = false, final = false, abstrait = false)
                return
            }
            // 4. L'index du classpath : le nom d'un TYPE connu (CLASSE en
            //    repli honnête — le genre binaire exact n'est pas prouvé
            //    au palier borné).
            if (univers.index?.nomsTopParPrefixe(nom, 1).orEmpty().contains(nom)) {
                poser(nom, noeud, genreDeclaration = GenreJetonSemantiqueKotlin.CLASSE, declaration = false, statique = false, final = false, abstrait = false)
            }
        }

        /** Rassemble : tri par offset, fusion par offset (la déclaration gagne). */
        fun rassembler(): List<JetonSemantiqueKotlin> =
            jetons.sortedWith(compareBy({ it.offset }, { if (it.declaration) 0 else 1 }))
                .distinctBy { it.offset }
    }

    /** Le genre de jeton d'un genre de type projet. */
    private fun genreDeTypeProjet(genre: jo.codeide.lsp.kotlin.resolve.GenreTypeProjet): GenreJetonSemantiqueKotlin = when (genre) {
        jo.codeide.lsp.kotlin.resolve.GenreTypeProjet.INTERFACE -> GenreJetonSemantiqueKotlin.INTERFACE
        jo.codeide.lsp.kotlin.resolve.GenreTypeProjet.ENUM -> GenreJetonSemantiqueKotlin.ENUM
        jo.codeide.lsp.kotlin.resolve.GenreTypeProjet.OBJET -> GenreJetonSemantiqueKotlin.CLASSE
        jo.codeide.lsp.kotlin.resolve.GenreTypeProjet.CLASSE -> GenreJetonSemantiqueKotlin.CLASSE
        jo.codeide.lsp.kotlin.resolve.GenreTypeProjet.JAVA -> GenreJetonSemantiqueKotlin.CLASSE
    }
}
