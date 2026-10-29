package jo.codeide.lsp.java.completion

/**
 * Grade de correspondance d'un préfixe (étage N2 — cahier fiche 008,
 * « matching gradué », parité PrefixMatcher du projet tiers de référence).
 *
 * L'ordre des valeurs est l'ordre de PRIORITÉ décroissante : EXACT précède
 * PREFIXE qui précède PREFIXE_CI, etc. La sous-chaîne n'est admise qu'à
 * partir de 3 caractères de préfixe ; les matches PAR PRÉFIXE dominent
 * TOUJOURS le classement (contrat de la fiche : le premier item matche
 * toujours par préfixe).
 */
public enum class GradeCorrespondance {
    /** Le nom EST le préfixe (même casse). */
    EXACT,

    /** Le nom démarre par le préfixe (même casse). */
    PREFIXE,

    /** Le nom démarre par le préfixe (casse ignorée) — fiche 044 : « Con » matche `content`. */
    PREFIXE_CI,

    /** Camel-hump : les majuscules du préfixe correspondent aux majuscules du nom (`IAE` → `IllegalArgumentException`). */
    HUMP,

    /** Sous-chaîne : le nom CONTIENT le préfixe — admise seulement ≥ 3 caractères. */
    SOUS_CHAINE,

    /** Aucune correspondance : le candidat est écarté. */
    AUCUN,
}

/**
 * Correspondance graduée par préfixe (étage N2, fiche 008).
 *
 * Le BONUS de classement suit la convention documentée du domaine
 * (ADR 0012, risque R1) : **plus petit = plus prioritaire**. Un match PAR
 * PRÉFIXE (exact, préfixe ou préfixe insensible à la casse) porte le bonus
 * [BONUS_PREFIXE] de −200 contre 0 pour les autres grades admis — l'écart
 * garantit qu'un match par préfixe précède TOUJOURS un match par
 * sous-chaîne, quelles que soient les dimensions suivantes de la chaîne de
 * classement.
 */
public object CorrespondanceGraduee {

    /**
     * Bonus de classement d'un match PAR PRÉFIXE (fiche 008 : « bonus de
     * classement −200 pour un match par préfixe (vs 0) »).
     */
    public const val BONUS_PREFIXE: Int = -200

    /**
     * Longueur minimale du préfixe pour qu'un match par SOUS-CHAÎNE soit
     * admis (fiche 008 : « substring autorisée seulement ≥ 3 caractères »).
     */
    public const val LONGUEUR_MIN_SOUS_CHAINE: Int = 3

    /**
     * Grade de correspondance d'un nom pour un préfixe.
     *
     * @param prefixe le préfixe tapé (éventuellement vide — tout grade alors
     *   EXACT : un préfixe vide matche tout nom par préfixe, règle d'or 3,
     *   fiche 044).
     * @param nom le nom candidat.
     * @return le grade ([GradeCorrespondance.AUCUN] si aucun grade ne
     *   s'applique).
     */
    public fun grade(prefixe: String, nom: String): GradeCorrespondance {
        if (prefixe.isEmpty()) return GradeCorrespondance.EXACT
        if (nom == prefixe) return GradeCorrespondance.EXACT
        if (nom.startsWith(prefixe)) return GradeCorrespondance.PREFIXE
        if (nom.startsWith(prefixe, ignoreCase = true)) return GradeCorrespondance.PREFIXE_CI
        if (estHump(prefixe, nom)) return GradeCorrespondance.HUMP
        if (prefixe.length >= LONGUEUR_MIN_SOUS_CHAINE &&
            nom.contains(prefixe, ignoreCase = true)
        ) {
            return GradeCorrespondance.SOUS_CHAINE
        }
        return GradeCorrespondance.AUCUN
    }

    /**
     * Bonus de classement du grade (plus petit = plus prioritaire).
     *
     * Les trois grades PAR PRÉFIXE portent [BONUS_PREFIXE] ; HUMP et
     * SOUS_CHAINE portent 0 ; AUCUN n'a pas de bonus (le candidat est écarté
     * avant le classement).
     *
     * @param grade le grade évalué.
     * @return le bonus de classement.
     */
    public fun bonus(grade: GradeCorrespondance): Int = when (grade) {
        GradeCorrespondance.EXACT -> BONUS_PREFIXE
        GradeCorrespondance.PREFIXE -> BONUS_PREFIXE
        GradeCorrespondance.PREFIXE_CI -> BONUS_PREFIXE
        GradeCorrespondance.HUMP -> 0
        GradeCorrespondance.SOUS_CHAINE -> 0
        GradeCorrespondance.AUCUN -> 0
    }

    /**
     * Épreuve camel-hump : chaque caractère du préfixe correspond, dans
     * l'ordre, à un point de casse du nom (`IAE` → `IllegalArgumentException`).
     *
     * Un préfixe tout-minuscule n'est pas un hump (ce serait une érosion du
     * grade préfixe) : le hump exige au moins une majuscule DANS le préfixe.
     *
     * @param prefixe le préfixe tapé.
     * @param nom le nom candidat.
     * @return vrai ssi le préfixe est un préfixe hump du nom.
     */
    private fun estHump(prefixe: String, nom: String): Boolean {
        if (prefixe.isEmpty() || nom.isEmpty()) return false
        if (prefixe.none { it.isUpperCase() }) return false
        var indiceNom = 0
        for (caractere in prefixe) {
            val cible = caractere.uppercaseChar()
            var trouve = false
            while (indiceNom < nom.length) {
                val courant = nom[indiceNom]
                val courantCible = courant.uppercaseChar()
                val pointDeCasse = indiceNom == 0 || courant.isUpperCase()
                indiceNom++
                if (pointDeCasse && courantCible == cible) {
                    trouve = true
                    break
                }
                if (caractere.isLowerCase()) return false
            }
            if (!trouve) return false
        }
        return true
    }
}
