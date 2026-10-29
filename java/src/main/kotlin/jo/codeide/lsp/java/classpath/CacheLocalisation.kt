package jo.codeide.lsp.java.classpath

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

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
 * @param jars les jars du classpath (ordre de déclaration préservé).
 */
public class CacheLocalisation(
    jars: List<Path>,
) {

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
                return jar
            }
        }
        memo[fqcn] = Verdict(null)
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
                return true
            }
        }
        memo[cle] = Verdict(null)
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
        for ((_, ouvert) in handles) {
            try {
                ouvert.handle.close()
            } catch (_: Exception) {
                // fermeture au mieux
            }
        }
        handles.clear()
    }

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
