package jo.codeide.lsp.java.navigation

import jo.codeide.lsp.java.outils.LexeurJava
import jo.codeide.lsp.java.outils.NatureJeton
import java.nio.file.Files
import java.nio.file.Path
import java.util.regex.Pattern

/**
 * Index de MOTS des sources du projet (étage N4 — ADR 0014/0008) : le
 * socle du [ServiceUsages] (QueryExecutor). L'index de mots est la
 * traduction clean-room du `searchWord` de l'étude D10 : les candidats
 * d'une recherche d'usages sont les fichiers qui CONTIENNENT le mot (le
 * sélecteur, le nom du champ ou le nom simple du type) — la
 * correspondance EXACTE se fait ensuite par clé de binding sur l'arbre
 * résolu de chaque candidat.
 *
 * Critère de sortie de la vague « usages indexés » (ADR 0014) :
 * **`Files.walk` retiré du chemin chaud** — l'énumération et le lexage
 * des racines se produisent UNE fois (construction FROIDE à la première
 * requête), jamais par requête ; les requêtes suivantes ne touchent le
 * disque que pour la FRAÎCHEUR des seuls candidats rendus (stat mtime +
 * taille, re-lexage d'un fichier changé).
 *
 * Invalidation : [invalider] reconstruit à la prochaine requête (le
 * branchement didSave/watcher arrive avec la couche LSP de l'étage N8) ;
 * un fichier supprimé ou modifié est rattrapé paresseusement au fil des
 * requêtes qui le rendent candidat.
 *
 * @param racines les racines sources du projet (l'ordre n'est jamais
 *   signifiant — l'énumération est triée pour la reproductibilité).
 */
public class IndexMotsSources(private val racines: List<Path>) {

    /** Verrou de construction (une seule construction à la fois). */
    private val verrou = Any()

    /** Vrai ssi l'index est construit (lecture sans verrou — indicatif). */
    @Volatile
    private var construit: Boolean = false

    /** Mot → fichiers porteurs (identifiants lexicaux uniquement). */
    private var motsVersFichiers: Map<String, Set<Path>> = emptyMap()

    /** Fichier → mots (pour le re-lexage différentiel d'un fichier changé). */
    private var motsParFichier: Map<Path, Set<String>> = emptyMap()

    /** Fichier → empreinte (mtime, taille) au moment du lexage. */
    private var empreintes: Map<Path, Pair<Long, Long>> = emptyMap()

    /** L'énumération triée des sources (partagée avec les hiérarchies N7). */
    private var fichiersConnus: List<Path> = emptyList()

    /** Compteur observable des constructions (le test du retrait de `Files.walk`). */
    private var nbConstructions: Int = 0

    /**
     * Le nombre de CONSTRUCTIONS de l'index (énumération + lexage des
     * racines) — l'observable du critère « `Files.walk` retiré du chemin
     * chaud » : il vaut 1 après N requêtes si aucune invalidation.
     *
     * @return le nombre de constructions froides effectuées.
     */
    public fun constructions(): Int = synchronized(verrou) { nbConstructions }

    /**
     * Les fichiers sources des racines (énumération de la construction
     * froide) — partagée avec le service de hiérarchies (N7) pour que le
     * balayage froid ne traverse JAMAIS deux fois l'arborescence.
     *
     * @return les fichiers `.java` triés (vide si aucune racine lisible).
     */
    public fun fichiers(): List<Path> {
        assurerConstruit()
        return synchronized(verrou) { fichiersConnus }
    }

    /**
     * Les fichiers CANDIDATS contenant le mot (fraîcheur vérifiée : un
     * candidat changé est re-lexé, un candidat disparu est écarté).
     *
     * @param mot l'identifiant cherché (sélecteur, nom de champ, nom simple).
     * @return les fichiers porteurs du mot, triés.
     */
    public fun fichiersPour(mot: String): List<Path> {
        assurerConstruit()
        synchronized(verrou) {
            val candidats = motsVersFichiers[mot]?.toList() ?: return emptyList()
            val resultat = ArrayList<Path>(candidats.size)
            for (fichier in candidats) {
                when (fraicheurDe(fichier)) {
                    Fraicheur.INCHANGE -> resultat.add(fichier)
                    Fraicheur.CHANGE -> {
                        relexer(fichier)
                        if (mot in (motsParFichier[fichier] ?: emptySet())) resultat.add(fichier)
                    }
                    Fraicheur.DISPARU -> retirer(fichier)
                }
            }
            return resultat.sorted()
        }
    }

    /**
     * Invalide l'index : la prochaine requête reconstruit (fichiers créés
     * découverts, fichiers disparus oubliés).
     */
    public fun invalider() {
        synchronized(verrou) {
            construit = false
            motsVersFichiers = emptyMap()
            motsParFichier = emptyMap()
            empreintes = emptyMap()
            fichiersConnus = emptyList()
        }
    }

    /** Construit l'index si nécessaire (l'appelant NE détient PAS le verrou). */
    private fun assurerConstruit() {
        if (construit) return
        synchronized(verrou) {
            if (construit) return
            val fichiers = ArrayList<Path>()
            for (racine in racines) {
                if (!Files.isDirectory(racine)) continue
                try {
                    Files.walk(racine).use { flux ->
                        flux.filter { it.fileName?.toString()?.endsWith(".java") == true }.forEach { fichiers.add(it) }
                    }
                } catch (_: Exception) {
                    // Une racine illisible est écartée — résilience.
                }
            }
            fichiers.sort()
            val motsVers = HashMap<String, MutableSet<Path>>()
            val motsPar = HashMap<Path, Set<String>>()
            val empreintesLocales = HashMap<Path, Pair<Long, Long>>()
            for (fichier in fichiers) {
                val mots = lexMotsDe(fichier)
                motsPar[fichier] = mots
                for (mot in mots) {
                    motsVers.getOrPut(mot) { LinkedHashSet() }.add(fichier)
                }
                empreintesLocales[fichier] = empreinteDe(fichier) ?: Pair(0L, 0L)
            }
            motsVersFichiers = motsVers
            motsParFichier = motsPar
            empreintes = empreintesLocales
            fichiersConnus = fichiers.toList()
            nbConstructions++
            construit = true
        }
    }

    /** L'empreinte (mtime, taille) d'un fichier, ou null si illisible. */
    private fun empreinteDe(fichier: Path): Pair<Long, Long>? = try {
        val attributs = Files.readAttributes(fichier, java.nio.file.attribute.BasicFileAttributes::class.java)
        Pair(attributs.lastModifiedTime().toMillis(), attributs.size())
    } catch (_: Exception) {
        null
    }

    /** La fraîcheur d'un fichier indexé (stat disque — candidats seulement). */
    private fun fraicheurDe(fichier: Path): Fraicheur {
        if (!Files.isRegularFile(fichier)) return Fraicheur.DISPARU
        val courante = empreinteDe(fichier) ?: return Fraicheur.DISPARU
        val indexee = empreintes[fichier] ?: return Fraicheur.CHANGE
        return if (courante == indexee) Fraicheur.INCHANGE else Fraicheur.CHANGE
    }

    /** Re-lexe un fichier changé : l'ancien lexique est retiré, le nouveau posé. */
    private fun relexer(fichier: Path) {
        retirer(fichier)
        val mots = lexMotsDe(fichier)
        motsParFichier = motsParFichier + (fichier to mots)
        empreintes = empreintes + (fichier to (empreinteDe(fichier) ?: Pair(0L, 0L)))
        val motsVers = HashMap<String, MutableSet<Path>>()
        for ((mot, ensemble) in motsVersFichiers) motsVers[mot] = LinkedHashSet(ensemble)
        for (mot in mots) {
            motsVers.getOrPut(mot) { LinkedHashSet() }.add(fichier)
        }
        motsVersFichiers = motsVers
    }

    /** Retire un fichier de toutes les structures (disparu ou changé). */
    private fun retirer(fichier: Path) {
        val anciens = motsParFichier[fichier] ?: emptySet()
        val motsVers = HashMap(motsVersFichiers)
        for (mot in anciens) {
            val ensemble = motsVers[mot] ?: continue
            val reste = ensemble - fichier
            if (reste.isEmpty()) motsVers.remove(mot) else motsVers[mot] = reste
        }
        motsVersFichiers = motsVers
        motsParFichier = motsParFichier - fichier
        empreintes = empreintes - fichier
        fichiersConnus = fichiersConnus - fichier
    }

    /** Les identifiants lexicaux d'un fichier (repli regex si le lexeur échoue). */
    private fun lexMotsDe(fichier: Path): Set<String> {
        val texte = try {
            String(Files.readAllBytes(fichier), Charsets.UTF_8)
        } catch (_: Exception) {
            return emptySet()
        }
        val jetons = LexeurJava.lexe(texte)
        if (jetons != null) {
            return jetons.asSequence()
                .filter { it.nature == NatureJeton.IDENTIFIANT }
                .map { it.texte }
                .toSet()
        }
        // Repli total (littéral inachevé — fichier en cours d'édition) : le
        // scan brut des identifiants garde le fichier candidat.
        val resultat = LinkedHashSet<String>()
        val scanner = MOTS.matcher(texte)
        while (scanner.find()) resultat.add(scanner.group())
        return resultat
    }

    /** Motif brut de repli (identifiants Java). */
    private companion object {
        val MOTS: Pattern = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*")
    }

    /** Fraîcheur d'un fichier indexé par rapport au disque. */
    private enum class Fraicheur {
        /** Le fichier est inchangé depuis le lexage. */
        INCHANGE,

        /** Le fichier a changé (mtime/taille) — re-lexage. */
        CHANGE,

        /** Le fichier a disparu — retrait. */
        DISPARU,
    }
}
