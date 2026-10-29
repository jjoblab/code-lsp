package jo.codeide.lsp.java.completion

import jo.codeide.lsp.java.interne.ItemCompletion
import jo.codeide.lsp.java.interne.NatureItem
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * Genre de contexte de complétion (étage N2) — la classification du caret
 * produite par l'analyseur de contexte, consommée par la collecte ET par la
 * chaîne de classement (les priorités de nature dépendent du contexte).
 */
public enum class GenreContexte {
    /** Complétion de MEMBRES : `recepteur.prefixe|` (récepteur instance). */
    MEMBRE,

    /** Complétion de MEMBRES STATIQUES : `Type.prefixe|` (qualifier de type). */
    MEMBRE_STATIQUE,

    /** Complétion de TYPE : position de type (déclaration, paramètre, clause). */
    TYPE,

    /** Complétion de NOM : instruction/expression (locales, membres, mots-clés). */
    NOM,

    /** Contexte d'IMPORT : `import paquet.tape.prefixe|`. */
    IMPORT,

    /** Référence de PAQUET hors import : `java.util.prefixe|` en expression. */
    REFERENCE_PAQUET,

    /** Position de MEMBRE de classe (corps de type, hors méthode) : stubs d'override. */
    CORPS_CLASSE,

    /** Position de NOM de déclaration : suggestion de noms dérivés du type. */
    NOM_DECLARATION,

    /** Label de `switch` sur enum : constantes de l'enum sélecteur. */
    CASE_LABEL,

    /** Valeur d'attribut d'annotation de type enum : constantes de l'enum. */
    VALEUR_ANNOTATION,

    /** Contexte non classé (repli honnête : liste vide ou inventaire). */
    INCONNU,
}

/**
 * Contexte de classement (étage N2) : les données dont les peseurs ont
 * besoin — le genre du contexte et le préfixe tapé.
 *
 * @property genre le genre du contexte au caret.
 * @property prefixe le préfixe tapé (éventuellement vide).
 */
public class ContexteClassement(
    public val genre: GenreContexte,
    public val prefixe: String,
)

/**
 * Peseur de la chaîne de classement (design D8 « en naissance », ADR 0012 —
 * miroir Kotlin des weighers IntelliJ) : produit le poids d'un item sur UNE
 * dimension.
 *
 * La convention du domaine (ADR 0012, risque R1 — « lower = higher
 * priority ») : **plus petit poids = plus prioritaire**.
 */
public interface Peseur {
    /** Nom du peseur (diagnostic, banc de qualité). */
    public val nom: String

    /**
     * Poids d'un item sur la dimension de ce peseur.
     *
     * @param item l'item évalué.
     * @param contexte le contexte de classement (genre + préfixe).
     * @return le poids (plus petit = plus prioritaire).
     */
    public fun poids(item: ItemCompletion, contexte: ContexteClassement): Comparable<*>
}

/**
 * Chaîne de classement (design D8, ADR 0012) : une liste ORDONNÉE de
 * [Peseur] comparés dimension par dimension, paresseusement — le miroir
 * Kotlin du `WeighingComparable` d'IntelliJ : la dimension k d'un item n'est
 * calculée QUE si les dimensions 0..k-1 des deux items comparés sont
 * égales ; chaque dimension calculée est mémorisée par item (une seule
 * évaluation par item et par dimension et par arrangement).
 *
 * La chaîne standard de l'étage N2, dans l'ordre :
 *  1. [PeseurCorrespondance] — la dominance du préfixe (fiche 008) ;
 *  2. [PeseurNature] — la priorité par nature, dépendante du contexte ;
 *  3. [PeseurLongueur] — liftShorter : les items courts remontent ;
 *  4. [PeseurAlphabetique] — départage déterministe ;
 *  5. [PeseurStatistiques] — compteur d'usage local, OPT-IN (désactivé par
 *     défaut : vie privée — local uniquement, documenté ADR 0012).
 */
public class ChaineClassement(
    private val peseurs: List<Peseur>,
) {

    /**
     * Construit la chaîne standard de l'étage N2.
     *
     * @param statistiques le compteur d'usage local (null = statistiques
     *   désactivées — défaut, opt-in côté hôte).
     * @return la chaîne ordonnée.
     */
    public constructor(statistiques: CompteurUsage? = null) : this(
        listOfNotNull(
            PeseurCorrespondance(),
            PeseurNature(),
            PeseurLongueur(),
            PeseurAlphabetique(),
            statistiques?.let { PeseurStatistiques(it) },
        ),
    )

    /** Les peseurs de la chaîne, dans l'ordre (diagnostic/banc). */
    public val dimensions: List<Peseur>
        get() = peseurs

    /**
     * Compareur de la chaîne : dimension par dimension, paresseusement.
     *
     * @param contexte le contexte de classement.
     * @return le comparateur (ordre total déterministe).
     */
    public fun comparateur(contexte: ContexteClassement): Comparator<PoidsPese> =
        Comparator { a, b ->
            for (dimension in peseurs.indices) {
                val resultat = comparerDimensions(a, b, dimension, contexte)
                if (resultat != 0) return@Comparator resultat
            }
            0
        }

    /** Comparaison générique de deux poids de même dimension (cast contrôlé). */
    @Suppress("UNCHECKED_CAST")
    private fun comparerDimensions(
        a: PoidsPese,
        b: PoidsPese,
        dimension: Int,
        contexte: ContexteClassement,
    ): Int {
        val poidsA = a.dimension(dimension, contexte) as Comparable<Any>
        val poidsB = b.dimension(dimension, contexte) as Comparable<Any>
        return poidsA.compareTo(poidsB)
    }

    /**
     * Prépare les poids paresseux d'une liste de candidats.
     *
     * @param candidats les items à classer.
     * @return les enveloppes de poids (une par item).
     */
    public fun preparer(candidats: List<ItemCompletion>): List<PoidsPese> =
        candidats.map { PoidsPese(it, this) }

    /** Poids d'une dimension précise (interne — voir [PoidsPese.dimension]). */
    internal fun poidsDe(item: ItemCompletion, dimension: Int, contexte: ContexteClassement): Comparable<*> =
        peseurs[dimension].poids(item, contexte)
}

/**
 * Enveloppe de poids d'un item (le miroir Kotlin du `WeighingComparable`)
 * : chaque dimension est calculée UNE fois, à la demande, et mémorisée.
 */
public class PoidsPese internal constructor(
    private val item: ItemCompletion,
    private val chaine: ChaineClassement,
) {
    /** Mémoires de dimensions (null = non calculée). */
    private val memoires = arrayOfNulls<Comparable<*>?>(chaine.dimensions.size)

    /** L'item porteur (diagnostic). */
    public val porte: ItemCompletion
        get() = item

    /**
     * Poids de la dimension k — calculé au premier appel seulement.
     *
     * @param dimension l'indice de la dimension.
     * @param contexte le contexte de classement.
     * @return le poids mémorisé ou fraîchement calculé.
     */
    internal fun dimension(dimension: Int, contexte: ContexteClassement): Comparable<*> {
        memoires[dimension]?.let { return it }
        val poids = chaine.poidsDe(item, dimension, contexte)
        memoires[dimension] = poids
        return poids
    }
}

/** Peseur de correspondance (dimension dominante) : le bonus du grade de matching (fiche 008). */
public class PeseurCorrespondance : Peseur {
    override val nom: String = "correspondance"

    override fun poids(item: ItemCompletion, contexte: ContexteClassement): Comparable<*> =
        CorrespondanceGraduee.bonus(item.correspondance ?: GradeCorrespondance.PREFIXE)
}

/**
 * Peseur de nature : priorité par nature, DÉPENDANTE du contexte (p. ex. en
 * contexte d'import, les sous-paquets dominent les types — fiche 008 :
 * « dominants sur les seuls types directs »).
 */
public class PeseurNature : Peseur {
    override val nom: String = "nature"

    override fun poids(item: ItemCompletion, contexte: ContexteClassement): Comparable<*> =
        priorite(item.nature, contexte.genre)

    /** Priorité d'une nature dans un genre de contexte (plus petite = d'abord). */
    private fun priorite(nature: NatureItem, genre: GenreContexte): Int = when (genre) {
        GenreContexte.IMPORT, GenreContexte.REFERENCE_PAQUET ->
            when (nature) {
                NatureItem.PAQUET -> 0
                NatureItem.CLASSE, NatureItem.INTERFACE, NatureItem.ANNOTATION, NatureItem.CONSTANTE_ENUM -> 1
                else -> 2
            }

        GenreContexte.MEMBRE, GenreContexte.MEMBRE_STATIQUE ->
            when (nature) {
                NatureItem.CHAMP -> 0
                NatureItem.METHODE -> 1
                else -> 2
            }

        GenreContexte.CASE_LABEL, GenreContexte.VALEUR_ANNOTATION ->
            when (nature) {
                NatureItem.CONSTANTE_ENUM -> 0
                else -> 1
            }

        else -> // NOM, TYPE, CORPS_CLASSE, NOM_DECLARATION, INCONNU
            when (nature) {
                NatureItem.VARIABLE -> 0
                NatureItem.PARAMETRE -> 1
                NatureItem.CHAMP -> 2
                NatureItem.METHODE, NatureItem.EXTRAIT, NatureItem.CONSTANTE_ENUM -> 3
                NatureItem.CLASSE, NatureItem.INTERFACE, NatureItem.ANNOTATION -> 4
                NatureItem.PAQUET -> 5
                NatureItem.MOT_CLE -> 6
            }
    }
}

/** Peseur de longueur (liftShorter) : les items COURTS remontent parmi les égaux. */
public class PeseurLongueur : Peseur {
    override val nom: String = "longueur"

    override fun poids(item: ItemCompletion, contexte: ContexteClassement): Comparable<*> =
        item.libelle.length
}

/** Peseur alphabétique : départage déterministe (jamais de classement instable). */
public class PeseurAlphabetique : Peseur {
    override val nom: String = "alphabetique"

    override fun poids(item: ItemCompletion, contexte: ContexteClassement): Comparable<*> =
        item.libelle
}

/**
 * Compteur d'usage local (ADR 0012 — `StatisticsWeigher` réécrit sur un
 * simple compteur d'usage local persistant) : un fichier de propriétés
 * `nom=fréquence`, local au poste, OPT-IN. Désactivé par défaut ; purgé à
 * la désactivation (cycle de vie vérifié par test).
 *
 * @param fichier le fichier d'état (créé paresseusement à la première écriture).
 */
public class CompteurUsage(
    private val fichier: Path,
) {
    /** Comptes en mémoire (chargés une fois, écrits à chaque incrément). */
    private val comptes = ConcurrentHashMap<String, Int>()

    /** Vrai ssi le fichier d'état existe (chargé alors paresseusement). */
    private val charge = java.util.concurrent.atomic.AtomicBoolean(false)

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
     * Compte d'usage d'un libellé.
     *
     * @param libelle le libellé interrogé.
     * @return le compte (0 si inconnu).
     */
    public fun compter(libelle: String): Int {
        charger()
        return comptes[libelle] ?: 0
    }

    /** Purge complète : comptes vidés, fichier supprimé (test de cycle de vie). */
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

/** Peseur de statistiques d'usage (opt-in) : plus utilisé = plus prioritaire. */
public class PeseurStatistiques(
    private val compteur: CompteurUsage,
) : Peseur {
    override val nom: String = "statistiques"

    override fun poids(item: ItemCompletion, contexte: ContexteClassement): Comparable<*> =
        -compteur.compter(item.libelle)
}
