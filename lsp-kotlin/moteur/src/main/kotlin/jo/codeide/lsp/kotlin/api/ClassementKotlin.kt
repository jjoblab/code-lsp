// CodeIDE — SPI du moteur Kotlin LSP : chaîne de CLASSEMENT (module
// :kotlin, étage 3 vague 3c — ADR 0113 option 1 : la chaîne de l'ADR 0012
// Java est « la spécification du pipeline de complétion Kotlin neuf » —
// weighers comparés dimension par dimension PARESSEUSEMENT, arrangement
// avec GEL des têtes et TRIM honnête). Code NEUF jjoblab (clean-room —
// la transposition des designs IntelliJ étudiés par l'ADR 0012, jamais
// leur code).
package jo.codeide.lsp.kotlin.api

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Le GRADE de correspondance d'une proposition au préfixe frappé (étage 3
 * — ADR 0113, miroir des grades de l'ADR 0012 Java) : la dimension
 * DOMINANTE du classement.
 *
 * L'ordre va du meilleur au pire ; [HORS] ferme la marche — le régime
 * membre ne filtre PAS par préfixe (le re-filtrage local par frappe est la
 * frontière RPC de l'hôte, ADR 0118) : les propositions hors préfixe se
 * classent en fin de liste, jamais écartées.
 */
public enum class GradeCorrespondanceKotlin {

    /** Le libellé EST le préfixe frappé (frappe complète). */
    EXACT,

    /** Le libellé COMMENCE par le préfixe (sensible à la casse). */
    PREFIXE,

    /** Le libellé commence par le préfixe SANS la casse. */
    PREFIXE_INSENSIBLE,

    /** Les caractères du préfixe épousent les BOSSAGES du libellé (`TV` → `TextView`). */
    HUMP,

    /** Le préfixe vit À L'INTÉRIEUR du libellé. */
    SUBSTRING,

    /** AUCUNE correspondance (le régime membre propose tout — le client filtre). */
    HORS,
}

/**
 * La correspondance GRADUÉE (étage 3 — ADR 0113) : le grade d'un libellé
 * face à un préfixe, et le BONUS de classement qui en dérive.
 */
public object CorrespondanceGradueeKotlin {

    /**
     * Le grade d'un libellé face au préfixe frappé.
     *
     * @param libelle le libellé de la proposition ;
     * @param prefixe le préfixe frappé (vide = neutre : PREFIXE) ;
     * @return le grade (du meilleur au pire).
     */
    public fun grade(libelle: String, prefixe: String): GradeCorrespondanceKotlin {
        if (prefixe.isEmpty()) return GradeCorrespondanceKotlin.PREFIXE
        if (libelle == prefixe) return GradeCorrespondanceKotlin.EXACT
        if (libelle.startsWith(prefixe)) return GradeCorrespondanceKotlin.PREFIXE
        if (libelle.startsWith(prefixe, ignoreCase = true)) {
            return GradeCorrespondanceKotlin.PREFIXE_INSENSIBLE
        }
        if (estHump(libelle, prefixe)) return GradeCorrespondanceKotlin.HUMP
        if (libelle.contains(prefixe, ignoreCase = true)) {
            return GradeCorrespondanceKotlin.SUBSTRING
        }
        return GradeCorrespondanceKotlin.HORS
    }

    /**
     * Le BONUS de classement d'un grade (plus petit = plus prioritaire —
     * la convention du domaine, ADR 0113/0012 risque R1 : « lower = higher
     * priority », le piège documenté du transport).
     */
    public fun bonus(grade: GradeCorrespondanceKotlin): Int = when (grade) {
        GradeCorrespondanceKotlin.EXACT -> 0
        GradeCorrespondanceKotlin.PREFIXE -> 1
        GradeCorrespondanceKotlin.PREFIXE_INSENSIBLE -> 2
        GradeCorrespondanceKotlin.HUMP -> 3
        GradeCorrespondanceKotlin.SUBSTRING -> 4
        GradeCorrespondanceKotlin.HORS -> 5
    }

    /**
     * Les caractères du préfixe épousent-ils les BOSSAGES du libellé
     * (`TV` → `TextView`, `vA` → `viewModel`) : chaque caractère du préfixe
     * se pose sur une OUVERTURE de bossage (début, majuscule, après tiret
     * bas), dans l'ordre.
     */
    private fun estHump(libelle: String, prefixe: String): Boolean {
        if (prefixe.isEmpty() || prefixe.length > libelle.length) return false
        var indice = 0
        for (caractere in prefixe) {
            var pose = false
            while (indice < libelle.length) {
                val ouverture = indice == 0 ||
                    libelle[indice].isUpperCase() ||
                    libelle[indice - 1] == '_'
                if (ouverture && libelle[indice].equals(caractere, ignoreCase = true)) {
                    pose = true
                    indice++
                    break
                }
                indice++
            }
            if (!pose) return false
        }
        return true
    }
}

/**
 * Le contexte de CLASSEMENT (étage 3 — ADR 0113) : les données dont les
 * peseurs ont besoin — le genre du contexte au caret et le préfixe
 * frappé.
 *
 * @property genre le genre du contexte (les priorités de nature en
 * dépendent) ;
 * @property prefixe le préfixe frappé (éventuellement vide).
 */
public class ContexteClassementKotlin(
    public val genre: GenreContexteKotlin,
    public val prefixe: String,
)

/**
 * Un PESEUR de la chaîne de classement (étage 3 — ADR 0113 : le miroir
 * Kotlin des weighers IntelliJ étudiés par l'ADR 0012) : produit le poids
 * d'une proposition sur UNE dimension.
 *
 * La convention du domaine (ADR 0012, risque R1 — « lower = higher
 * priority ») : **plus petit poids = plus prioritaire**.
 */
public interface PeseurKotlin {

    /** Le nom du peseur (diagnostique, banc de qualité). */
    public val nom: String

    /**
     * Le poids d'une proposition sur la dimension de ce peseur.
     *
     * @param proposition la proposition évaluée ;
     * @param contexte le contexte de classement (genre + préfixe) ;
     * @return le poids (plus petit = plus prioritaire).
     */
    public fun poids(proposition: PropositionKotlin, contexte: ContexteClassementKotlin): Comparable<*>
}

/**
 * La chaîne de classement (étage 3 — ADR 0113, miroir de l'ADR 0012) :
 * une liste ORDONNÉE de [PeseurKotlin] comparés dimension par dimension,
 * PARESSEUSEMENT — la dimension k d'une proposition ne se calcule QUE si
 * les dimensions 0..k-1 des deux propositions comparées sont égales ;
 * chaque dimension calculée est MÉMORISÉE (une seule évaluation par
 * proposition, par dimension et par arrangement).
 *
 * La chaîne standard de l'étage 3, dans l'ordre :
 *  1. [PeseurCorrespondanceKotlin] — la dominance du préfixe ;
 *  2. [PeseurNatureKotlin] — la priorité par nature, dépendante du
 *     contexte ;
 *  3. [PeseurLongueurKotlin] — liftShorter : les libellés courts
 *     remontent parmi les égaux ;
 *  4. [PeseurAlphabetiqueKotlin] — départage déterministe (jamais de
 *     classement instable) ;
 *  5. [PeseurStatistiquesKotlin] — compteur d'usage local, OPT-IN
 *     (désactivé par défaut : vie privée — local uniquement, documenté).
 *
 * @property peseurs les peseurs, dans l'ordre des dimensions.
 */
public class ChaineClassementKotlin(
    public val peseurs: List<PeseurKotlin>,
) {

    /**
     * La chaîne STANDARD de l'étage 3.
     *
     * @param statistiques le compteur d'usage local (`null` = statistiques
     *   désactivées — le défaut, opt-in côté hôte).
     */
    public constructor(statistiques: CompteurUsageKotlin? = null) : this(
        listOfNotNull(
            PeseurCorrespondanceKotlin(),
            PeseurNatureKotlin(),
            PeseurLongueurKotlin(),
            PeseurAlphabetiqueKotlin(),
            statistiques?.let { PeseurStatistiquesKotlin(it) },
        ),
    )

    /**
     * Prépare les poids PARESSEUX d'une liste de candidates.
     *
     * @param candidates les propositions à classer ;
     * @return les enveloppes de poids (une par proposition).
     */
    internal fun preparer(candidates: List<PropositionKotlin>): List<PoidsPeseKotlin> =
        candidates.map { PoidsPeseKotlin(it, this) }

    /**
     * Le comparateur de la chaîne : dimension par dimension,
     * paresseusement (chaque dimension ne se calcule qu'à égalité des
     * précédentes).
     *
     * @param contexte le contexte de classement ;
     * @return le comparateur (ordre total déterministe).
     */
    public fun comparateur(contexte: ContexteClassementKotlin): Comparator<PoidsPeseKotlin> =
        Comparator { a, b ->
            for (dimension in peseurs.indices) {
                val resultat = comparerDimensions(a, b, dimension, contexte)
                if (resultat != 0) return@Comparator resultat
            }
            0
        }

    /** La comparaison générique de deux poids de même dimension. */
    @Suppress("UNCHECKED_CAST")
    private fun comparerDimensions(
        a: PoidsPeseKotlin,
        b: PoidsPeseKotlin,
        dimension: Int,
        contexte: ContexteClassementKotlin,
    ): Int {
        val poidsA = a.dimension(dimension, contexte) as Comparable<Any>
        val poidsB = b.dimension(dimension, contexte) as Comparable<Any>
        return poidsA.compareTo(poidsB)
    }
}

/**
 * L'enveloppe de poids d'une proposition (le miroir Kotlin du
 * `WeighingComparable` étudié par l'ADR 0012) : chaque dimension se
 * calcule UNE fois, à la demande, et se mémorise.
 */
public class PoidsPeseKotlin internal constructor(
    private val proposition: PropositionKotlin,
    private val chaine: ChaineClassementKotlin,
) {

    /** Les mémoires de dimensions (`null` = non calculée). */
    private val memoires = arrayOfNulls<Comparable<*>?>(chaine.peseurs.size)

    /** La proposition porteuse (diagnostique, arrangement). */
    public val porte: PropositionKotlin
        get() = proposition

    /**
     * Le poids de la dimension k — calculé au premier appel seulement.
     *
     * @param dimension l'indice de la dimension ;
     * @param contexte le contexte de classement ;
     * @return le poids mémorisé ou fraîchement calculé.
     */
    internal fun dimension(dimension: Int, contexte: ContexteClassementKotlin): Comparable<*> {
        memoires[dimension]?.let { return it }
        val poids = chaine.peseurs[dimension].poids(proposition, contexte)
        memoires[dimension] = poids
        return poids
    }
}

/** Le peseur de CORRESPONDANCE (dimension dominante) : le bonus du grade du libellé face au préfixe. */
public class PeseurCorrespondanceKotlin : PeseurKotlin {
    override val nom: String = "correspondance"

    override fun poids(proposition: PropositionKotlin, contexte: ContexteClassementKotlin): Comparable<*> =
        CorrespondanceGradueeKotlin.bonus(
            CorrespondanceGradueeKotlin.grade(proposition.libelle, contexte.prefixe),
        )
}

/**
 * Le peseur de NATURE : la priorité par nature, DÉPENDANTE du contexte
 * (en contexte d'import les paquets dominent les types ; en membre les
 * propriétés dominent les fonctions ; en nom les locales dominent tout).
 */
public class PeseurNatureKotlin : PeseurKotlin {
    override val nom: String = "nature"

    override fun poids(proposition: PropositionKotlin, contexte: ContexteClassementKotlin): Comparable<*> =
        priorite(proposition.nature, contexte.genre)

    /** La priorité d'une nature dans un genre de contexte (plus petite = d'abord). */
    private fun priorite(nature: NaturePropositionKotlin, genre: GenreContexteKotlin): Int = when (genre) {
        GenreContexteKotlin.IMPORT ->
            when (nature) {
                NaturePropositionKotlin.PAQUET -> 0
                NaturePropositionKotlin.CLASSE,
                NaturePropositionKotlin.INTERFACE,
                NaturePropositionKotlin.ANNOTATION,
                NaturePropositionKotlin.ENUM,
                -> 1
                else -> 2
            }

        GenreContexteKotlin.MEMBRE ->
            when (nature) {
                NaturePropositionKotlin.PROPRIETE -> 0
                NaturePropositionKotlin.FONCTION -> 1
                else -> 2
            }

        GenreContexteKotlin.ARGUMENTS_NOMMES ->
            when (nature) {
                NaturePropositionKotlin.PARAMETRE -> 0
                else -> 1
            }

        GenreContexteKotlin.CONSTANTES_ENUM ->
            when (nature) {
                NaturePropositionKotlin.ENUM -> 0
                else -> 1
            }

        GenreContexteKotlin.ATTRIBUTS_ANNOTATION ->
            when (nature) {
                NaturePropositionKotlin.PROPRIETE -> 0
                else -> 1
            }

        else -> // NOM, INCONNU
            when (nature) {
                NaturePropositionKotlin.VARIABLE -> 0
                NaturePropositionKotlin.PARAMETRE -> 1
                NaturePropositionKotlin.PROPRIETE -> 2
                NaturePropositionKotlin.FONCTION, NaturePropositionKotlin.ENUM -> 3
                NaturePropositionKotlin.CLASSE, NaturePropositionKotlin.INTERFACE, NaturePropositionKotlin.ANNOTATION -> 4
                NaturePropositionKotlin.PAQUET -> 5
                NaturePropositionKotlin.MOT_CLE -> 6
            }
    }
}

/** Le peseur de LONGUEUR (liftShorter) : les libellés COURTS remontent parmi les égaux. */
public class PeseurLongueurKotlin : PeseurKotlin {
    override val nom: String = "longueur"

    override fun poids(proposition: PropositionKotlin, contexte: ContexteClassementKotlin): Comparable<*> =
        proposition.libelle.length
}

/** Le peseur ALPHABÉTIQUE : départage déterministe (jamais de classement instable). */
public class PeseurAlphabetiqueKotlin : PeseurKotlin {
    override val nom: String = "alphabetique"

    override fun poids(proposition: PropositionKotlin, contexte: ContexteClassementKotlin): Comparable<*> =
        proposition.libelle
}

/**
 * Le compteur d'usage LOCAL (ADR 0113 : le `StatisticsWeigher` réécrit sur
 * un simple compteur d'usage local persistant — miroir de l'ADR 0012) : un
 * fichier de propriétés `nom=fréquence`, local au poste, OPT-IN.
 * Désactivé par défaut ; purgé à la désactivation.
 *
 * @param fichier le fichier d'état (créé paresseusement à la première écriture).
 */
public class CompteurUsageKotlin(
    private val fichier: Path,
) {

    /** Les comptes en mémoire (chargés une fois, écrits à chaque incrément). */
    private val comptes = ConcurrentHashMap<String, Int>()

    /** Vrai ssi le fichier d'état existe (chargé alors paresseusement). */
    private val charge = AtomicBoolean(false)

    /**
     * Incrémente le compte d'usage d'un libellé.
     *
     * @param libelle le libellé choisi par l'utilisateur.
     */
    public fun incrementer(libelle: String) {
        charger()
        comptes.merge(libelle, 1, Int::plus)
        ecrire()
    }

    /**
     * Le compte d'usage d'un libellé.
     *
     * @param libelle le libellé interrogé ;
     * @return le compte (0 si inconnu).
     */
    public fun compter(libelle: String): Int {
        charger()
        return comptes[libelle] ?: 0
    }

    /** Purge complète : comptes vidés, fichier supprimé (cycle de vie). */
    public fun purger() {
        comptes.clear()
        try {
            Files.deleteIfExists(fichier)
        } catch (_: Exception) {
            // purge au mieux
        }
    }

    /** Charge le fichier d'état une fois (paresseux). */
    private fun charger() {
        if (!charge.compareAndSet(false, true)) return
        if (!Files.isRegularFile(fichier)) return
        try {
            for (ligne in Files.readAllLines(fichier)) {
                val egale = ligne.indexOf('=')
                if (egale <= 0) continue
                val cle = ligne.substring(0, egale)
                val valeur = ligne.substring(egale + 1).toIntOrNull() ?: continue
                if (cle.isNotEmpty()) comptes[cle] = valeur
            }
        } catch (_: Exception) {
            // état illisible : repart à vide (honnête)
        }
    }

    /** Écrit le fichier d'état (à chaque incrément — fichier petit). */
    private fun ecrire() {
        try {
            Files.createDirectories(fichier.parent)
            Files.writeString(fichier, comptes.entries.joinToString("\n") { "${it.key}=${it.value}" })
        } catch (_: Exception) {
            // écriture impossible : l'état reste en mémoire
        }
    }
}

/** Le peseur de STATISTIQUES d'usage (opt-in) : plus utilisé = plus prioritaire. */
public class PeseurStatistiquesKotlin(
    private val compteur: CompteurUsageKotlin,
) : PeseurKotlin {
    override val nom: String = "statistiques"

    override fun poids(proposition: PropositionKotlin, contexte: ContexteClassementKotlin): Comparable<*> =
        -compteur.compter(proposition.libelle)
}

/**
 * La liste ARRANGÉE (étage 3 — ADR 0113) : la liste ordonnée, plafonnée à
 * l'affichage, AVEC le total réel de l'espace de candidats (le plafond ne
 * ment jamais).
 *
 * @property propositions les propositions ordonnées (≤ plafond) ;
 * @property totalDisponible le nombre RÉEL de candidates avant plafond.
 */
public class ListeArrangeeKotlin(
    public val propositions: List<PropositionKotlin>,
    public val totalDisponible: Int,
) {

    /** Vrai ssi la liste a été raccourcie par le plafond d'affichage. */
    public val tronquee: Boolean
        get() = totalDisponible > propositions.size
}

/**
 * L'arrangement de liste de complétion (étage 3 — ADR 0113 : le miroir
 * Kotlin de l'arrangement IntelliJ étudié par l'ADR 0012) : CLASSEMENT
 * par la chaîne, GEL des têtes affichées, TRIM honnête.
 *
 * Le GEL (`freezeTopItems`) : après chaque arrangement sous une même clé
 * de contexte (même document, même genre, même ancre d'expression — le
 * récepteur ou la déclaration porteuse), les [GEL_MAX] premiers libellés
 * sont mémorisés ; à l'arrangement SUIVANT sous la même clé, chaque
 * survivant du gel OCCUPE EXACTEMENT son rang précédent — un nouveau venu
 * ne déplace JAMAIS un item déjà affiché en tête (contrat « zéro saut de
 * liste » du banc de qualité).
 *
 * Le TRIM : la liste rendue est plafonnée ; le total réel voyage avec la
 * réponse — le client SAIT que la liste est tronquée.
 *
 * L'instance est À SESSION (états étanches) : deux sessions ne partagent
 * jamais leurs gels.
 *
 * @param chaine la chaîne de classement (peseurs ordonnés).
 */
public class ArrangementListeKotlin(
    private val chaine: ChaineClassementKotlin,
) {

    /** Les items gelés par clé de contexte (libellé → rang). */
    private val gels = HashMap<String, List<String>>()

    /**
     * Arrange les candidates : classement par la chaîne, gel des têtes,
     * plafond honnête.
     *
     * @param cleContexte la clé de stabilité (document + genre + ancre) —
     *   deux arrangements successifs de frappes successives partagent la
     *   clé ;
     * @param candidates les propositions collectées, non ordonnées ;
     * @param contexte le contexte de classement ;
     * @param limite le plafond d'affichage de CE régime ;
     * @return la liste arrangée (propositions ≤ limite + total réel).
     */
    public fun arranger(
        cleContexte: String,
        candidates: List<PropositionKotlin>,
        contexte: ContexteClassementKotlin,
        limite: Int = LIMITE_AFFICHAGE,
    ): ListeArrangeeKotlin {
        val total = candidates.size
        val poids = chaine.preparer(candidates)
        val ordonnee = poids.sortedWith(chaine.comparateur(contexte)).map { it.porte }
        val gelees = appliquerGel(cleContexte, ordonnee)
        memoriserGel(cleContexte, gelees)
        return ListeArrangeeKotlin(gelees.take(limite.coerceAtLeast(0)), total)
    }

    /**
     * Applique le gel aux propositions ordonnées : les survivants de la
     * tête gelée précédente reprennent LEUR rang exact, les autres
     * comblent les trous dans l'ordre de la chaîne. Les places non
     * comblables (liste rétrécie) restent vides — jamais d'exception.
     */
    private fun appliquerGel(cleContexte: String, ordonnee: List<PropositionKotlin>): List<PropositionKotlin> {
        val gelees = gels[cleContexte] ?: return ordonnee
        if (gelees.isEmpty()) return ordonnee
        val rangs = HashMap<String, Int>()
        gelees.forEachIndexed { rang, libelle -> rangs[libelle] = rang }
        val places = arrayOfNulls<PropositionKotlin>(ordonnee.size.coerceAtLeast(gelees.size))
        val epargnees = mutableListOf<PropositionKotlin>()
        for (proposition in ordonnee) {
            val rang = rangs[proposition.libelle]
            if (rang != null && rang < places.size && places[rang] == null) {
                places[rang] = proposition
            } else {
                epargnees.add(proposition)
            }
        }
        var curseur = 0
        val resultat = mutableListOf<PropositionKotlin>()
        for (place in places) {
            if (place != null) {
                resultat.add(place)
                continue
            }
            val epargnee = epargnees.getOrNull(curseur)
            if (epargnee != null) {
                resultat.add(epargnee)
                curseur++
            }
        }
        while (curseur < epargnees.size) resultat.add(epargnees[curseur++])
        return resultat.filterNotNull()
    }

    /** Mémorise la nouvelle tête gelée (les [GEL_MAX] premiers libellés). */
    private fun memoriserGel(cleContexte: String, ordonnee: List<PropositionKotlin>) {
        gels[cleContexte] = ordonnee.take(GEL_MAX).map { it.libelle }
    }

    /** Oublie tout gel (test, nouvelle session). */
    public fun reinitialiser() {
        gels.clear()
    }

    public companion object {

        /**
         * Le plafond d'affichage standard (les espaces NON BORNÉS —
         * régime de nom, imports : le classpath est profond).
         */
        public const val LIMITE_AFFICHAGE: Int = 120

        /**
         * Le plafond d'affichage du régime MEMBRE : l'espace est BORNÉ par
         * construction (les membres de la chaîne du récepteur, profondeur
         * 7 — ADR 0219) — le plafond couvre la chaîne entière pour que le
         * re-filtrage local du client (frontière RPC, ADR 0118) travaille
         * sur l'espace complet.
         */
        public const val LIMITE_MEMBRE: Int = 512

        /** Le nombre d'items gelés en tête par clé de contexte. */
        public const val GEL_MAX: Int = 8
    }
}
