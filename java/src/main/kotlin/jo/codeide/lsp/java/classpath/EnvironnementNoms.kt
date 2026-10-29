package jo.codeide.lsp.java.classpath

import org.eclipse.jdt.internal.compiler.classfmt.ClassFileReader
import org.eclipse.jdt.internal.compiler.env.ICompilationUnit
import org.eclipse.jdt.internal.compiler.env.INameEnvironment
import org.eclipse.jdt.internal.compiler.env.NameEnvironmentAnswer
import java.nio.file.Path

/**
 * Environnement de noms du moteur (étage N1) — concrétion ecj de
 * `INameEnvironment`, adaptateur DIRECT de la bibliothèque (le routage
 * alimente le résolveur ecj).
 *
 * Ordre de repli VERROUILLÉ par le cahier (fiche 042) :
 *
 * 1. **racines sources** — sondage par FQCN avec cache négatif de présence
 *    (fiche 006) ; un type imbriqué `X$Y` retombe sur le fichier de
 *    l'englobant `X.java` (le fichier source de A répond — convention
 *    `INameEnvironment`) ;
 * 2. **jars du classpath** — cache de localisation fqcn → jar, positifs ET
 *    négatifs mémorisés (fiche 024) ;
 * 3. **image jrt** — runtime courant ou JDK externe (fiche 031) ;
 * 4. **stubs des intrinsèques** — liste fermée de trois FQCN, dernier
 *    recours (fiche 042) : ils ne masquent JAMAIS une source, un jar ou un
 *    jrt qui porterait la classe.
 *
 * Le calcul du FQCN côté moteur suit le **plus long préfixe de paquet**
 * (fiche 010) : l'ordre des racines n'est jamais signifiant — voir
 * `MoteurJava.fqcnPour`.
 *
 * @param racines racines sources (répertoires existants recommandés).
 * @param jars jars du classpath (l'ordre de déclaration est conservé).
 * @param jdkHome home JDK explicite (null = runtime courant ; sans
 *   `lib/modules` = pas de jrt, régime instantané).
 */
public class EnvironnementNoms(
    racines: List<Path>,
    jars: List<Path>,
    jdkHome: Path?,
) : INameEnvironment {

    /** Cache négatif de présence source (TTL 30 s, invalidation Created). */
    public val cachePresence: CachePresenceSource = CachePresenceSource(racines)

    /** Cache de localisation fqcn → jar (positifs et négatifs, dispose). */
    public val cacheLocalisation: CacheLocalisation = CacheLocalisation(jars)

    /** Accès jrt (null si la plateforme est absente — régime instantané). */
    public val accesJrt: AccesJrt? = AccesJrt.ouvrir(jdkHome)

    /** Indique si une image jrt est chargée (sonde de plateforme). */
    public fun jrtDisponible(): Boolean = accesJrt != null

    /**
     * Indique si l'environnement dispose d'une source BINAIRE indexable.
     *
     * Sonde observable du cahier (fiche 004 : `hasBinarySource`) : vraie dès
     * qu'au moins un jar du classpath est présent et lisible — le
     * branchement de l'index disque CIDX arrive en N4 (ADR 0008).
     */
    public fun aSourceBinaire(): Boolean = cacheLocalisation.aJarsLisibles()

    /**
     * Sonde de résolution d'un FQCN sans passer par ecj (fiche 039 :
     * `engine.typeIndex().find(fqcn)`).
     *
     * @param fqcn nom entièrement qualifié en points.
     * @return la localisation trouvée (source, jar, jrt ou stub), ou null.
     */
    public fun trouverFqcn(fqcn: String): Localisation? {
        cachePresence.fichierPour(fqcn)?.let { return Localisation.Source(it) }
        if (cacheLocalisation.jarPour(fqcn) != null) return Localisation.Jar
        if (accesJrt?.octets(fqcn) != null) return Localisation.Jrt
        if (StubsIntrinseques.estIntrinseque(fqcn)) return Localisation.Stub
        return null
    }

    override fun findType(compoundTypeName: Array<CharArray>): NameEnvironmentAnswer? {
        val fqcn = compoundTypeName.joinToString(".") { String(it) }
        return trouver(fqcn)
    }

    override fun findType(typeName: CharArray, packageName: Array<CharArray>): NameEnvironmentAnswer? {
        val fqcn = buildString {
            for (segment in packageName) {
                if (isNotEmpty()) append('.')
                append(segment)
            }
            if (isNotEmpty()) append('.')
            append(typeName)
        }
        return trouver(fqcn)
    }

    override fun isPackage(parentPackageName: Array<CharArray>?, packageName: CharArray): Boolean {
        val parent = parentPackageName?.joinToString(".") { String(it) } ?: ""
        val paquet = if (parent.isEmpty()) String(packageName) else "$parent.${String(packageName)}"
        if (cachePresence.estPaquet(paquet)) return true
        if (cacheLocalisation.estPaquet(paquet)) return true
        if (accesJrt?.estPaquet(paquet) == true) return true
        return false
    }

    /**
     * Nettoyage ecj : libère handles et chargeurs, vide les caches.
     *
     * Passé ce point l'environnement n'est plus utilisable — [dispose].
     */
    override fun cleanup() {
        dispose()
    }

    /** Libère toutes les ressources (jars, jrt, caches). */
    public fun dispose() {
        cachePresence.invalider()
        cacheLocalisation.dispose()
        accesJrt?.close()
    }

    /** Routage complet d'un FQCN dans l'ordre de repli verrouillé. */
    private fun trouver(fqcn: String): NameEnvironmentAnswer? {
        // 1. Sources : fichier exact, sinon englobant d'un imbriqué `$`.
        cachePresence.fichierPour(fqcn)?.let { return reponseSource(it) }
        val positionDollar = fqcn.lastIndexOf('$')
        if (positionDollar > 0) {
            val englobant = fqcn.substring(0, positionDollar)
            if (englobant.contains('.')) {
                cachePresence.fichierPour(englobant)?.let { return reponseSource(it) }
            }
        }
        // 2. Jars (exact seulement — le chemin `$` retombe sans crash, fiche 024).
        cacheLocalisation.octetsPour(fqcn)?.let { octets ->
            return reponseBinaire(fqcn, octets)
        }
        // 3. Image jrt.
        accesJrt?.octets(fqcn)?.let { octets ->
            return reponseBinaire(fqcn, octets)
        }
        // 4. Stubs intrinsèques (liste fermée — dernier repli).
        StubsIntrinseques.octets(fqcn)?.let { octets ->
            return reponseBinaire(fqcn, octets)
        }
        return null
    }

    /** Enveloppe une réponse SOURCE (l'unité sera parsée par ecj à la demande). */
    private fun reponseSource(fichier: Path): NameEnvironmentAnswer {
        val unite: ICompilationUnit = jo.codeide.lsp.java.interne.UnitesSource.SurDisque(fichier)
        return NameEnvironmentAnswer(unite, null)
    }

    /** Enveloppe une réponse BINAIRE (octets déjà lus, lecteur de classe ecj). */
    private fun reponseBinaire(fqcn: String, octets: ByteArray): NameEnvironmentAnswer {
        val lecteur = ClassFileReader.read(octets, fqcn.replace('.', '/') + ".class")
        return NameEnvironmentAnswer(lecteur, null)
    }

    /** Localisation d'un FQCN (sonde [trouverFqcn]). */
    public sealed interface Localisation {
        /** La classe vit dans un fichier source du projet. */
        public data class Source(val fichier: Path) : Localisation

        /** La classe vit dans un jar du classpath. */
        public object Jar : Localisation

        /** La classe vit dans l'image jrt. */
        public object Jrt : Localisation

        /** La classe est un intrinsèque stubé (liste fermée). */
        public object Stub : Localisation
    }
}
