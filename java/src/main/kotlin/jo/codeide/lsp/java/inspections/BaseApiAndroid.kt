package jo.codeide.lsp.java.inspections

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
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
     * sa forme gzippée `api-versions.xml.gz`), OU le **segment compact**
     * `.seg` — posé à côté du xml (chemin `<xml>.seg`, le chemin rapide)
     * ou désigné directement par la propriété (ADR 0024 : compilation
     * binaire de la base, cible ~1,7 Mo, re-compilable bit-à-bit).
     */
    private fun chemin(): Path? {
        val brut = System.getProperty("jo.codeide.lsp.java.lint.baseapi") ?: return null
        return runCatching { Path.of(brut) }.getOrNull()
    }

    /** Le chemin du SEGMENT COMPACT associé à un chemin de base (null sinon). */
    private fun segmentDe(chemin: Path): Path? {
        if (chemin.fileName.toString().endsWith("." + EXTENSION_SEGMENT)) return chemin
        val voisin = chemin.resolveSibling(chemin.fileName.toString() + "." + EXTENSION_SEGMENT)
        return if (Files.isRegularFile(voisin)) voisin else null
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

    /** Le parse réel (fil de fond) : segment compact d'abord, xml ensuite. */
    private fun charger() {
        val chemin = chemin() ?: return
        val carte = runCatching {
            val segment = segmentDe(chemin)
            when {
                segment == null -> lire(chemin)
                // Un segment illisible/corrompu ne tue JAMAIS la base :
                // repli honnête sur le xml (jamais un silence fabriqué).
                else -> runCatching { lireSegment(segment) }.getOrNull() ?: lire(chemin)
            }
        }.getOrNull() ?: return
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

    /**
     * Réinitialise l'état chargé (usage BANC documenté : recharger une
     * autre base sous la même propriété — la production ne la touche
     * jamais, la base vit pour toute la session).
     */
    public fun reinitialiser() {
        chargee.set(null)
        fil.set(null)
    }

    // ------------------------------------------------------------------ //
    // Segment compact CIDX (ADR 0024 : compilation binaire de la base) //
    // ------------------------------------------------------------------ //

    /**
     * Compile la base `api-versions.xml` (ou `.xml.gz`) en SEGMENT
     * COMPACT binaire : classes par ordre lexicographique, méthodes et
     * champs triés — le résultat est RE-COMPILABLE BIT-À-BIT (même xml →
     * mêmes octets, empreinte versionnée par [empreinte]).
     *
     * Format (`CLBA`, v1) : magic 4 octets, version 1 octet, puis par
     * classe (UTF nom interne, Int since) ses méthodes (UTF clé, Int
     * since) et ses champs (UTF nom, Int since, Int inlined ; -1 =
     * absent) — lecture [DataInputStream], écriture [DataOutputStream].
     *
     * @param source le xml de la base (gzip transparent).
     * @param cible le fichier segment à écrire (créé/écrasé).
     * @return le nombre d'octets écrits.
     */
    public fun compilerSegment(source: Path, cible: Path): Long {
        val carte = lire(source)
        Files.createDirectories(cible.toAbsolutePath().parent)
        DataOutputStream(
            java.util.zip.GZIPOutputStream(
                java.io.BufferedOutputStream(Files.newOutputStream(cible)),
            ),
        ).use { flux ->
            flux.write(MAGIE_SEGMENT.toByteArray(Charsets.US_ASCII))
            flux.writeByte(VERSION_SEGMENT)
            flux.writeInt(carte.size)
            for (nomClasse in carte.keys.sorted()) {
                val classe = carte.getValue(nomClasse)
                flux.writeUTF(nomClasse)
                flux.writeInt(classe.since)
                val methodes = classe.methodes.entries.sortedBy { it.key }
                flux.writeInt(methodes.size)
                for ((cle, since) in methodes) {
                    flux.writeUTF(cle)
                    flux.writeInt(since)
                }
                val champs = classe.champs.entries.sortedBy { it.key }
                flux.writeInt(champs.size)
                for ((nom, niveau) in champs) {
                    flux.writeUTF(nom)
                    flux.writeInt(niveau.since)
                    flux.writeInt(niveau.inlined ?: -1)
                }
            }
        }
        return Files.size(cible)
    }

    /** La lecture d'un segment compact (v1) — muette sur format inconnu. */
    private fun lireSegment(chemin: Path): Map<String, ClasseApi>? {
        DataInputStream(
            java.util.zip.GZIPInputStream(
                java.io.BufferedInputStream(Files.newInputStream(chemin)),
            ),
        ).use { flux ->
            val magie = ByteArray(MAGIE_SEGMENT.length)
            flux.readFully(magie)
            if (String(magie, Charsets.US_ASCII) != MAGIE_SEGMENT) return null
            if (flux.readByte().toInt() != VERSION_SEGMENT) return null
            val nombreClasses = flux.readInt()
            val carte = HashMap<String, ClasseApi>(nombreClasses * 2)
            repeat(nombreClasses) {
                val nomClasse = flux.readUTF()
                val classe = ClasseApi(flux.readInt())
                repeat(flux.readInt()) {
                    classe.methodes[flux.readUTF()] = flux.readInt()
                }
                repeat(flux.readInt()) {
                    val nom = flux.readUTF()
                    val since = flux.readInt()
                    val inlined = flux.readInt()
                    classe.champs[nom] = Niveau(since, if (inlined < 0) null else inlined)
                }
                carte[nomClasse] = classe
            }
            return carte
        }
    }

    /**
     * L'empreinte SHA-256 d'un fichier de base (xml OU segment) — la
     * signature versionnée du déploiement (ADR 0024 : la base
     * re-compilable bit-à-bit se PRÉUVE par son empreinte).
     *
     * @param chemin le fichier à signer.
     * @return l'empreinte hexadécimale minuscule, ou null (illisible).
     */
    public fun empreinte(chemin: Path): String? = runCatching {
        val condensat = MessageDigest.getInstance("SHA-256")
        java.io.BufferedInputStream(Files.newInputStream(chemin)).use { flux ->
            val tampon = ByteArray(64 * 1024)
            while (true) {
                val lus = flux.read(tampon)
                if (lus < 0) break
                condensat.update(tampon, 0, lus)
            }
        }
        condensat.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

    /** L'extension du segment compact. */
    public const val EXTENSION_SEGMENT: String = "seg"

    /** La magie du format segment. */
    private const val MAGIE_SEGMENT: String = "CLBA"

    /** La version courante du format segment. */
    private const val VERSION_SEGMENT: Int = 1
}
