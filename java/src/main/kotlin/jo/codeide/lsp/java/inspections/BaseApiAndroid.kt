package jo.codeide.lsp.java.inspections

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import javax.xml.stream.XMLInputFactory

/**
 * La BASE API Android (vague V-C2 — ADR 0024, écart G-011) : les niveaux
 * `since`/`inlined` des classes, méthodes et champs du SDK, lus du
 * `api-versions.xml` du SDK (5,5 Mo — JAMAIS committé, R6.4).
 *
 * **Chargement** : en TÂCHE DE FOND au premier usage (cible ADR : < 2 s
 * JVM, une fois) depuis le chemin posé par la propriété système
 * `jo.codeide.lsp.java.lint.baseapi` (le script
 * `scripts/preparer-base-api.sh` la construit depuis un SDK local ou
 * l'artefact officiel). **Repli honnête (R1)** : base absente ou illisible
 * → [disponible] faux — les contrôles de niveau d'API restent MUETS
 * (jamais un faux silence : la présence de la base est observable par
 * l'hôte via [disponible]).
 *
 * **Format de clé** (miroir du xml : `name="setText(CharSequence)"`) :
 * classe en nom INTERNE (`android/widget/TextView`), méthodes par
 * `nom(paramètres simples)` — la correspondance se fait par nom + arité +
 * noms simples de paramètres ; une collision d'homonymes non résolue rend
 * `null` (silence honnête, jamais un since inventé).
 */
public object BaseApiAndroid {

    /** Une entrée de niveau : `since`, et `inlined` éventuel (champs). */
    public class Niveau(public val since: Int, public val inlined: Int?)

    /** Les membres d'une classe du SDK. */
    private class ClasseApi(
        val since: Int,
        val methodes: MutableMap<String, Int> = HashMap(),
        val champs: MutableMap<String, Niveau> = HashMap(),
    )

    /** La base chargée (null tant que non chargée / introuvable). */
    private val chargee = AtomicReference<Map<String, ClasseApi>?>(null)

    /** Le fil unique de chargement (démarré au premier besoin). */
    private val fil = AtomicReference<Thread?>(null)

    /**
     * Le chemin de la base : propriété système
     * `jo.codeide.lsp.java.lint.baseapi` (fichier `api-versions.xml` ou
     * sa forme gzippée `api-versions.xml.gz`).
     */
    private fun chemin(): Path? {
        val brut = System.getProperty("jo.codeide.lsp.java.lint.baseapi") ?: return null
        return runCatching { Path.of(brut) }.getOrNull()
    }

    /**
     * Vrai ssi la base est chargée et disponible — la sonde de l'hôte
     * (le repli honnête s'annonce, il ne se tait pas).
     */
    public fun disponible(): Boolean = chargee.get() != null

    /**
     * Lance le chargement en tâche de fond si nécessaire et ATTEND sa fin
     * (bornée) — appelé par les contrôles de niveau d'API : le premier
     * appel paie le parse (< 2 s cible), les suivants lisent la carte.
     *
     * @return la base, ou null (absente/illisible — contrôles muets).
     */
    private fun base(): Map<String, ClasseApi>? {
        chargee.get()?.let { return it }
        val demarreur = fil.get() ?: run {
            val nouveau = Thread({ charger() }, "lspjava-base-api").apply {
                isDaemon = true
                start()
            }
            fil.compareAndSet(null, nouveau)
            fil.get() ?: nouveau
        }
        demarreur.join(30_000)
        return chargee.get()
    }

    /** Le parse réel (fil de fond). */
    private fun charger() {
        val chemin = chemin() ?: return
        val carte = runCatching { lire(chemin) }.getOrNull() ?: return
        chargee.set(carte)
    }

    /** Ouvre le flux (gzip transparent). */
    private fun ouvrir(chemin: Path): InputStream {
        val brut = Files.newInputStream(chemin)
        return if (chemin.fileName.toString().endsWith(".gz")) {
            java.util.zip.GZIPInputStream(brut)
        } else {
            brut
        }
    }

    /** Parse l'api-versions.xml en carte interne. */
    private fun lire(chemin: Path): Map<String, ClasseApi> {
        val carte = HashMap<String, ClasseApi>()
        val fabrique = XMLInputFactory.newFactory()
        fabrique.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
        fabrique.setProperty(XMLInputFactory.SUPPORT_DTD, false)
        ouvrir(chemin).use { flux ->
            val lecteur = fabrique.createXMLStreamReader(flux)
            var classeCourante: ClasseApi? = null
            while (lecteur.hasNext()) {
                when (lecteur.next()) {
                    javax.xml.stream.XMLStreamConstants.START_ELEMENT -> when (lecteur.localName) {
                        "class" -> {
                            val nom = lecteur.getAttributeValue(null, "name") ?: continue
                            val since = lecteur.getAttributeValue(null, "since")?.toIntOrNull() ?: 1
                            val nouvelle = carte.getOrPut(nom) { ClasseApi(since) }
                            classeCourante = nouvelle
                        }
                        "method" -> {
                            val nom = lecteur.getAttributeValue(null, "name") ?: continue
                            val since = lecteur.getAttributeValue(null, "since")?.toIntOrNull() ?: continue
                            classeCourante?.methodes?.putIfAbsent(nom, since)
                        }
                        "field" -> {
                            val nom = lecteur.getAttributeValue(null, "name") ?: continue
                            val since = lecteur.getAttributeValue(null, "since")?.toIntOrNull() ?: continue
                            val inlined = lecteur.getAttributeValue(null, "inlined")?.toIntOrNull()
                            classeCourante?.champs?.putIfAbsent(nom, Niveau(since, inlined))
                        }
                        else -> {}
                    }
                    else -> {}
                }
            }
        }
        return carte
    }

    /**
     * Le niveau d'une MÉTHODE du SDK.
     *
     * @param classe le nom interne du propriétaire (« android/widget/TextView »).
     * @param selecteur le nom de la méthode.
     * @param parametres les noms SIMPLES des types de paramètres (« CharSequence »).
     * @return le niveau since, ou null (inconnu ou collision non résolue).
     */
    public fun sinceMethode(classe: String, selecteur: String, parametres: List<String>): Int? {
        val c = base()?.get(classe) ?: return null
        val cles = c.methodes.keys.filter { cle ->
            val parenthese = cle.indexOf('(')
            parenthese > 0 && cle.substring(0, parenthese) == selecteur &&
                cle.substring(parenthese + 1, cle.length - 1)
                    .split(',').map { it.trim().substringAfterLast('.') } == parametres
        }
        return cles.singleOrNull()?.let { c.methodes[it] }
    }

    /** Le niveau d'une CLASSE du SDK (nom interne). */
    public fun sinceClasse(classe: String): Int? = base()?.get(classe)?.since

    /**
     * Le niveau d'un CHAMP du SDK (with son `inlined` éventuel).
     *
     * @param classe le nom interne du propriétaire.
     * @param champ le nom du champ.
     */
    public fun niveauChamp(classe: String, champ: String): Niveau? = base()?.get(classe)?.champs?.get(champ)

    /** Le nom interne d'un binding (« android.widget.TextView » → « android/widget/TextView »). */
    public fun nomInterneDe(lisible: String): String = lisible.replace('.', '/')
}
