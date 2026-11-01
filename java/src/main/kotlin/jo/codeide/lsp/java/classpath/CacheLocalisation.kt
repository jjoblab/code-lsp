package jo.codeide.lsp.java.classpath

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Cache de localisation fqcn → jar (étage N1, cahier fiche 024 — P1 v1.3.0).
 *
 * Intégré à la recherche binaire de l'environnement de compilation : les
 * POSITIFS (fqcn trouvé dans un jar, avec le jar qui le porte) et les
 * NÉGATIFS (fqcn absent de tous les jars) sont mémorisés. C'est le négatif
 * qui porte le gain dominant : ecj résout des centaines de types absents par
 * frappe — sans mémoire, chaque absent re-sonde la table centrale de chaque
 * jar du classpath ; avec, le lookup est O(1).
 *
 * Contrats verrouillés (fiche 024) :
 *  - deux `find` successifs d'un FQCN présent répondent tous deux (positif
 *    mémorisé, même verdict) ;
 *  - un FQCN absent rend null DEUX FOIS sans re-sonder (négatif mémorisé) ;
 *  - les chemins `$` (types imbriqués) sondent l'entrée EXACTE ; un imbriqué
 *    sans entrée rend null SANS EXCEPTION, l'englobant existe ou non (le
 *    chemin `$` retombe sur l'englobant sans crash) ;
 *  - [dispose] vide TOUT : un négatif mémorisé ne survit JAMAIS à un
 *    changement de classpath.
 *
 * Les handles de jars ([LecteurZipBrut.Handle]) sont ouverts paresseusement,
 * un par jar, avec leur ensemble de paquets dérivé de la table centrale (un
 * seul passage par jar), et refermés au [dispose].
 *
 * **Budget OCTETS du mémo (vague V-F3 — ADR 0029 phase 3, exécution de
 * l'ADR 0011 point 2)** : les verdicts ne PÉRIMENT JAMAIS (ils vivent
 * tant que le moteur vit — contrairement au cache de présence, borné par
 * TTL) : le mémo est le seul cache de résolution SANS borne de l'audit.
 * Chaque verdict porte un COÛT ESTIMÉ ([coutEntree] : la clé UTF-16 +
 * l'enveloppe — estimation de gestion, pas une mesure, R5) et
 * l'éviction s'applique AU-DELÀ du budget : les NÉGATIFS partent
 * d'abord (un re-probe honnête re-sonde les tables centrales, le
 * verdict rendu reste EXACT), et si le budget reste dépassé le mémo
 * est vidé ENTIÈREMENT (robinet intégral — rare, documenté : les
 * positifs eux-mêmes sont re-calculables par définition). Les
 * contrats observables de la fiche 024 sont INCHANGÉS (deux find
 * successifs répondent le même verdict ; seul le COÛT du re-probe
 * revient). Kill-switch :
 * `jo.codeide.lsp.java.memoire.budgets.disabled=true` rétablit le mémo
 * non borné (comportement d'avant V-F3).
 *
 * @param jars les jars du classpath (ordre de déclaration préservé).
 * @param budgetOctets le budget d'octets ESTIMÉS du mémo (défaut
 *   [BUDGET_OCTETS_DEFAUT] ; le kill-switch global ne peut que le
 *   RELÂCHER — le minimum des deux gouverne).
 */
public class CacheLocalisation(
    jars: List<Path>,
    budgetOctets: Long = BUDGET_OCTETS_DEFAUT,
) {

    /**
     * Budget et coûts estimés (vague V-F3 — ADR 0011 point 2).
     */
    public companion object {

        /**
         * Le budget d'octets ESTIMÉS du mémo par défaut (2 Mio) : des
         * MILLIERS de verdicts (≈ 150–350 octets estimés chacun) — ne
         * mord qu'en session pathologique (sondages massifs de fqcn
         * distincts), jamais en régime de cahier.
         */
        public const val BUDGET_OCTETS_DEFAUT: Long = 2L * 1024 * 1024

        /**
         * Coût fixe ESTIMÉ d'un verdict : l'enveloppe [Verdict], le
         * nœud de la table concurrente, la référence de valeur —
         * estimation de gestion (R5), cohérente avec l'ordre de grandeur
         * des enveloppes JVM (16–24 o) + nœud CHM (~48 o).
         */
        internal const val COUT_VERDICT_ESTIME: Long = 64

        /** Budget infini : le kill-switch rétablit le mémo non borné. */
        private val BUDGET_GLOBAL: Long =
            if (System.getProperty("jo.codeide.lsp.java.memoire.budgets.disabled") == "true") {
                Long.MAX_VALUE
            } else {
                BUDGET_OCTETS_DEFAUT
            }

        /**
         * Le coût ESTIMÉ d'une entrée du mémo pour une clé donnée.
         *
         * @param cle le FQCN (ou `pkg:<paquet>`) mémorisé.
         * @return le coût en octets estimés (clé UTF-16 + enveloppe).
         */
        internal fun coutEntree(cle: String): Long =
            COUT_VERDICT_ESTIME + 2L * cle.length
    }

    /**
     * Verdict mémorisé : jar porteur (positif) ou null (négatif) — enveloppe
     * obligatoire, la table concurrente n'accepte pas les valeurs nulles.
     */
    private class Verdict(val jar: Path?)

    /** Verdicts mémorisés par clé (FQCN ou `pkg:<paquet>`). */
    private val memo = ConcurrentHashMap<String, Verdict>()

    /** Handles ouverts par jar (paresseux), avec leur ensemble de paquets. */
    private val handles = ConcurrentHashMap<Path, HandleOuvert>()

    /** Jars du classpath, dans l'ordre de déclaration. */
    private val jars: List<Path> = jars.distinct()

    /** Le budget effectif : le paramètre ne peut pas être plus permissif que le kill-switch global. */
    private val budgetEffectif: Long = minOf(budgetOctets, BUDGET_GLOBAL)

    /** Total des coûts estimés du mémo (approximé sous course, exact en régime sérialisé). */
    private val octetsEstimesMemo = AtomicLong(0)

    /** Verrou du périmètre FEUILLE éviction (V-F2/V-F3 — jamais imbriqué vers le haut). */
    private val verrouEviction = ReentrantLock()

    /** Un handle ouvert + l'ensemble des paquets dérivés de la table centrale. */
    private class HandleOuvert(val handle: LecteurZipBrut.Handle, val paquets: Set<String>)

    /**
     * Localise le jar qui contient la classe d'un FQCN.
     *
     * @param fqcn nom entièrement qualifié en points, chemin `$` accepté
     *   (`paquet.Englobant$Imbrique` — l'entrée exacte seule est sondée).
     * @return le chemin du jar porteur, ou null si absent de tous les jars
     *   (mémorisé : les appels suivants sont immédiats).
     */
    public fun jarPour(fqcn: String): Path? {
        memo[fqcn]?.let { return it.jar }
        val entree = fqcn.replace('.', '/') + ".class"
        for (jar in jars) {
            val ouvert = ouvrir(jar) ?: continue // jar illisible : écarté
            if (ouvert.handle.contains(entree)) {
                memo[fqcn] = Verdict(jar)
                octetsEstimesMemo.addAndGet(coutEntree(fqcn))
                evictSiDepasse()
                return jar
            }
        }
        memo[fqcn] = Verdict(null)
        octetsEstimesMemo.addAndGet(coutEntree(fqcn))
        evictSiDepasse()
        return null
    }

    /**
     * Lit les octets de la classe d'un FQCN depuis le jar qui la porte.
     *
     * @param fqcn nom entièrement qualifié en points.
     * @return les octets du `.class`, ou null si aucun jar ne le porte (ou
     *   si la lecture échoue — traité comme un absent propre).
     */
    public fun octetsPour(fqcn: String): ByteArray? {
        val jar = jarPour(fqcn) ?: return null
        val ouvert = ouvrir(jar) ?: return null
        return try {
            ouvert.handle.lire(fqcn.replace('.', '/') + ".class")
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Indique si un paquet existe dans au moins un jar du classpath.
     *
     * Le verdict est dérivé de l'ensemble des paquets de chaque jar (construit
     * en un seul passage sur la table centrale à l'ouverture) et mémorisé avec
     * la même politique que les classes.
     *
     * @param paquet nom du paquet en points.
     * @return vrai ssi au moins un jar contient des entrées sous ce paquet.
     */
    public fun estPaquet(paquet: String): Boolean {
        val cle = "pkg:$paquet"
        memo[cle]?.let { return it.jar != null }
        val chemin = paquet.replace('.', '/')
        for (jar in jars) {
            val ouvert = ouvrir(jar) ?: continue
            if (chemin in ouvert.paquets) {
                memo[cle] = Verdict(jar)
                octetsEstimesMemo.addAndGet(coutEntree(cle))
                evictSiDepasse()
                return true
            }
        }
        memo[cle] = Verdict(null)
        octetsEstimesMemo.addAndGet(coutEntree(cle))
        evictSiDepasse()
        return false
    }

    /**
     * Indique si au moins un jar du classpath est présent et lisible.
     *
     * Sonde observable du cahier (fiche 004 : index binaire rempli) pour le
     * stade N1 — le branchement de l'index disque CIDX arrive en N4.
     */
    public fun aJarsLisibles(): Boolean = jars.any { Files.isRegularFile(it) }

    /**
     * Vide le cache et referme tous les handles ouverts.
     *
     * Un négatif mémorisé ne doit jamais survivre à un changement de
     * classpath (contrat fiche 024) : la structure est intégralement vidée ;
     * les handles seront ré-ouverts paresseusement à la prochaine demande.
     */
    public fun dispose() {
        memo.clear()
        octetsEstimesMemo.set(0)
        for ((_, ouvert) in handles) {
            try {
                ouvert.handle.close()
            } catch (_: Exception) {
                // fermeture au mieux
            }
        }
        handles.clear()
    }

    /**
     * Évince TOUS les verdicts NÉGATIFS du mémo (pression mémoire V-F3 —
     * niveaux BACKGROUND/MODERATE : le process est parti en arrière-plan).
     *
     * Les POSITIFS restent (leurs handles ouverts restent lisibles sans
     * re-scan des tables centrales) ; les négatifs partis sont
     * re-probés honnêtement au prochain besoin (le verdict rendu reste
     * EXACT — seul le coût du re-probe revient).
     *
     * @return le nombre de négatifs évacués (compte du rapport de pression).
     */
    public fun evictNegatifs(): Int = verrouEviction.withLock {
        var liberes = 0
        val it = memo.entries.iterator()
        while (it.hasNext()) {
            val entree = it.next()
            if (entree.value.jar == null) {
                it.remove()
                liberes++
            }
        }
        octetsEstimesMemo.set(memo.keys.sumOf { coutEntree(it) })
        liberes
    }

    /**
     * L'éviction budgétaire (V-F3) : au-delà du budget, les NÉGATIFS
     * partent d'abord (re-probe honnête) ; si le budget reste dépassé, le
     * mémo est vidé ENTIÈREMENT (robinet intégral — les positifs sont
     * re-calculables par définition, les handles restent ouverts).
     *
     * Le compteur est RECALCULÉ depuis la table sous le verrou : en régime
     * sérialisé il est EXACT ; sous course, un put concurrent pendant la
     * fenêtre peut échapper au recompte (dérive bornée par les puts en
     * vol, auto-corrigée à la prochaine éviction — le budget est une
     * heuristique de gestion, pas une facturation).
     */
    private fun evictSiDepasse() {
        if (octetsEstimesMemo.get() <= budgetEffectif) return
        verrouEviction.withLock {
            if (octetsEstimesMemo.get() <= budgetEffectif) return // double contrôle
            memo.entries.removeIf { it.value.jar == null }
            var total = memo.keys.sumOf { coutEntree(it) }
            if (total > budgetEffectif) {
                memo.clear()
                total = 0
            }
            octetsEstimesMemo.set(total)
        }
    }

    /**
     * Le total des coûts ESTIMÉS du mémo (sonde V-F3 — tests et banc).
     */
    public fun octetsMemoises(): Long = octetsEstimesMemo.get()

    /** Le nombre de verdicts mémorisés, positifs et négatifs (sonde V-F3). */
    public fun nbVerdicts(): Int = memo.size

    /** Le nombre de verdicts NÉGATIFS mémorisés (sonde V-F3). */
    public fun nbNegatifs(): Int = memo.values.count { it.jar == null }

    /** Ouvre (une fois) le handle d'un jar et dérive ses paquets ; null si illisible. */
    private fun ouvrir(jar: Path): HandleOuvert? {
        if (handles.containsKey(jar)) return handles[jar]
        if (!Files.isRegularFile(jar)) return null
        return try {
            val handle = LecteurZipBrut.open(jar)
            // Ensemble des paquets = TOUS les ancêtres des répertoires
            // porteurs de `.class` (les intermédiaires comptent : `org` doit
            // exister pour que `org.junit` soit atteignable dans isPackage).
            // Les entrées à la racine du jar (sans barre) n'ouvrent aucun paquet.
            val paquets = mutableSetOf<String>()
            for (nom in handle.noms()) {
                if (!nom.endsWith(".class")) continue
                val derniereBarre = nom.lastIndexOf('/')
                if (derniereBarre <= 0) continue
                var segment = nom.substring(0, derniereBarre)
                while (segment.isNotEmpty()) {
                    paquets.add(segment)
                    val barre = segment.lastIndexOf('/')
                    segment = if (barre <= 0) "" else segment.substring(0, barre)
                }
            }
            HandleOuvert(handle, paquets).also { handles[jar] = it }
        } catch (_: Exception) {
            null
        }
    }
}
