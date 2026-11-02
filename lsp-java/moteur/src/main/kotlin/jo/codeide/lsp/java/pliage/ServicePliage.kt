package jo.codeide.lsp.java.pliage

import jo.codeide.lsp.java.outils.Jeton
import jo.codeide.lsp.java.outils.LexeurJava
import jo.codeide.lsp.java.outils.NatureJeton

/**
 * Genre d'une région de pliage (étage N7 — cahier fiches 011 et 052) :
 * les kinds contractuels du protocole LSP.
 *
 * @property libelleLsp la valeur `kind` du protocole (« imports »,
 *   « block », « comment »).
 */
public enum class GenrePli(public val libelleLsp: String) {
    /** Groupe d'imports — replié par défaut, texte replié « import ... ». */
    IMPORTS("imports"),

    /** Corps d'accolades (classe, méthode, contrôle, lambda). */
    BLOC("block"),

    /** Commentaire de bloc ou javadoc multi-lignes. */
    COMMENTAIRE("comment"),
}

/**
 * Une région de pliage, en OFFSETS caractères (fiche 011) : `Fold { kind,
 * placeholder, start, end, collapsedByDefault }`.
 *
 * Contrat de placement (fiche 011, notes) : les régions d'accolades
 * couvrent l'INTÉRIEUR — `start` = accolade ouvrante + 1, `end` = index de
 * la fermante (EXCLUE : les accolades restent visibles après repli) ; seuls
 * les commentaires s'étendent JUSQU'AU-DELÀ de leur marqueur de fin
 * (`end` = index du « étoile-slash » + 2, borne incluse).
 *
 * @property genre le genre (kind LSP).
 * @property texteReplie le texte replié (placeholder) — « import ... »,
 *   « slash-étoile … étoile-slash » ou « double-slash-étoile … étoile-slash » pour les commentaires ; null pour un bloc.
 * @property debut l'offset de début (inclus).
 * @property fin l'offset de fin (exclusive).
 * @property replieParDefaut vrai ssi la région est replée par défaut
 *   (seule la région d'imports l'est).
 */
public data class Pli(
    public val genre: GenrePli,
    public val texteReplie: String?,
    public val debut: Int,
    public val fin: Int,
    public val replieParDefaut: Boolean,
)

/**
 * Une région de pliage en COORDONNÉES LSP (fiche 052) : la précision
 * CARACTÈRE du dépliage est contractuelle — le dépliage démarre juste
 * APRÈS l'accolade ouvrante et se termine SUR la fermante.
 *
 * @property genre le genre (kind LSP).
 * @property texteReplie le texte replié (null pour un bloc).
 * @property ligneDebut la ligne de début (0-based).
 * @property colonneDebut la colonne de début.
 * @property ligneFin la ligne de fin.
 * @property colonneFin la colonne de fin.
 * @property replieParDefaut vrai ssi repli par défaut.
 */
public data class PliLsp(
    public val genre: GenrePli,
    public val texteReplie: String?,
    public val ligneDebut: Int,
    public val colonneDebut: Int,
    public val ligneFin: Int,
    public val colonneFin: Int,
    public val replieParDefaut: Boolean,
)

/**
 * Service du pliage (étage N7 — cahier fiches 011 et 052) : calcul des
 * régions de pliage d'une unité, FONCTION PURE du texte — le pliage ne
 * dépend d'aucune résolution de type (fiche 011 : « Moteur construit
 * vide (aucune racine, aucun classpath) »).
 *
 * Règles verrouillées par le cahier :
 *  - groupe d'imports replié PAR DÉFAUT, du « import » du PREMIER au
 *    « ; » du DERNIER (juste après) ;
 *  - paires d'accolades : INTÉRIEUR seulement, aucune paire monoligne,
 *    aucune paquet née d'une accolade de chaîne/commentaire/bloc-texte ;
 *  - commentaires de bloc et javadoc MULTI-LIGNES pliables (bornes
 *    incluses), commentaires de ligne JAMAIS ;
 *  - blocs de contrôle et corps de lambda pliables comme tout bloc.
 */
public object ServicePliage {

    /** Texte replié contractuel du groupe d'imports (littéral exact côté client). */
    public const val TEXTE_IMPORTS: String = "import ..."

    /** Texte replié d'un commentaire de bloc. */
    public const val TEXTE_COMMENTAIRE: String = "/*…*/"

    /** Texte replié d'une javadoc. */
    public const val TEXTE_JAVADOC: String = "/**…*/"

    /**
     * Régions de pliage d'une source, en offsets (fiche 011).
     *
     * @param source le texte Java complet.
     * @return les régions (ordre du document), vide si le lexage échoue —
     *   une source imparsable ne plie rien, sans jamais lever.
     */
    public fun plis(source: String): List<Pli> {
        val jetons = LexeurJava.lexe(source) ?: return emptyList()
        val resultat = ArrayList<Pli>()

        // 1. Groupe d'imports : du premier `import` au « ; » du dernier (inclus).
        var premierImport: Jeton? = null
        var finDernierImport = -1
        for (jeton in jetons) {
            if (jeton.texte == "import" && jeton.nature == NatureJeton.MOT_CLE) {
                if (premierImport == null) premierImport = jeton
                // Une instruction d'import ne contient ni chaîne ni groupement :
                // son « ; » est le PREMIER qui suit le mot-clé.
                val pointVirgule = jetons.firstOrNull { it.debut > jeton.debut && it.texte == ";" }
                if (pointVirgule != null) finDernierImport = pointVirgule.fin
            }
        }
        if (premierImport != null && finDernierImport > 0) {
            resultat.add(Pli(GenrePli.IMPORTS, TEXTE_IMPORTS, premierImport.debut, finDernierImport, replieParDefaut = true))
        }

        // 2. Accolades : intérieur seulement, hors chaînes/commentaires (lexeur).
        val pile = ArrayDeque<Jeton>()
        for (jeton in jetons) {
            when (jeton.texte) {
                "{" -> pile.addLast(jeton)
                "}" -> {
                    val ouvrante = pile.removeLastOrNull() ?: continue
                    if (pluriLigne(source, ouvrante.debut, jeton.debut)) {
                        resultat.add(Pli(GenrePli.BLOC, null, ouvrante.fin, jeton.debut, replieParDefaut = false))
                    }
                }
            }
        }

        // 3. Commentaires de bloc / javadoc MULTI-LIGNES (bornes incluses).
        for (jeton in jetons) {
            if (jeton.nature != NatureJeton.COMMENTAIRE_BLOC && jeton.nature != NatureJeton.JAVADOC) continue
            if (!pluriLigne(source, jeton.debut, jeton.fin)) continue
            val texte = if (jeton.nature == NatureJeton.JAVADOC) TEXTE_JAVADOC else TEXTE_COMMENTAIRE
            resultat.add(Pli(GenrePli.COMMENTAIRE, texte, jeton.debut, jeton.fin, replieParDefaut = false))
        }

        return resultat.sortedBy { it.debut }
    }

    /**
     * Régions de pliage en coordonnées LSP lignes/caractères (fiche 052) :
     * le dépliage démarre juste après l'accolade ouvrante et se termine SUR
     * la fermante — la précision caractère est contractuelle.
     *
     * @param source le texte Java complet.
     * @return les régions en coordonnées LSP (0-based).
     */
    public fun plisLsp(source: String): List<PliLsp> {
        val debutsLignes = ArrayList<Int>()
        debutsLignes.add(0)
        for (i in source.indices) if (source[i] == '\n') debutsLignes.add(i + 1)
        fun ligne(offset: Int): Int {
            var bas = 0
            var haut = debutsLignes.size - 1
            while (bas < haut) {
                val milieu = (bas + haut + 1) / 2
                if (debutsLignes[milieu] <= offset) bas = milieu else haut = milieu - 1
            }
            return bas
        }
        fun colonne(offset: Int): Int = offset - debutsLignes[ligne(offset)]
        return plis(source).map { pli ->
            PliLsp(
                pli.genre,
                pli.texteReplie,
                ligne(pli.debut),
                colonne(pli.debut),
                ligne(pli.fin),
                colonne(pli.fin),
                pli.replieParDefaut,
            )
        }
    }

    /** Vrai ssi la plage [debut, fin) chevauche au moins deux lignes. */
    private fun pluriLigne(source: String, debut: Int, fin: Int): Boolean =
        source.indexOf('\n', debut) in debut until fin
}
