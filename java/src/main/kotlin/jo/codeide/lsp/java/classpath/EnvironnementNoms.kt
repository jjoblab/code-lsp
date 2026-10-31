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
 * Ordre de repli VERROUILLÉ par le cahier (fiche 042, étendu V-B/ADR 0021) :
 *
 * 1. **racines sources** — sondage par FQCN avec cache négatif de présence
 *    (fiche 006) ; un type imbriqué `X$Y` retombe sur le fichier de
 *    l'englobant `X.java` (le fichier source de A répond — convention
 *    `INameEnvironment`) ;
 * 2. **types synthétiques light** (V-B — ADR 0021/0022) — R, BuildConfig,
 *    `*Binding`, déclarations Kotlin : APRÈS les sources (la source
 *    générée réelle PRIME — anti-doublon R3), AVANT les jars (un R de
 *    build périmé ne masque jamais un référentiel frais) ;
 * 3. **jars du classpath** — cache de localisation fqcn → jar, positifs ET
 *    négatifs mémorisés (fiche 024) ;
 * 4. **image jrt** — runtime courant ou JDK externe (fiche 031) ;
 * 5. **stubs des intrinsèques** — liste fermée de trois FQCN, dernier
 *    recours (fiche 042) : ils ne masquent JAMAIS une source, un
 *    synthétique, un jar ou un jrt qui porterait la classe.
 *
 * Le calcul du FQCN côté moteur suit le **plus long préfixe de paquet**
 * (fiche 010) : l'ordre des racines n'est jamais signifiant — voir
 * `MoteurJava.fqcnPour`.
 *
 * **Régime plein** (v1.1.4) : la plateforme est déclarée résoluble par
 * [plateformeResolvable] dès que `java.lang.Object` est atteignable — par
 * le jrt OU par un jar du classpath (sur ART, le fournisseur jrt du JDK
 * externe est ininstanciable et le `android.jar` du modèle porte la
 * plateforme). Le régime instantané (fiche 028) ne survit que lorsque
 * NI le jrt NI un jar porteur n'existent.
 *
 * @param racines racines sources (répertoires existants recommandés).
 * @param jars jars du classpath (l'ordre de déclaration est conservé) —
 *   un jar portant `java.lang.Object` (ex. `android.jar`) vaut plateforme.
 * @param jdkHome home JDK explicite (null = runtime courant ; sans
 *   `lib/modules` ou fournisseur jrt ininstanciable = pas de jrt — le
 *   régime reste plein si un jar du classpath porte la plateforme).
 * @param synthetiques fournisseurs de types light (V-B — ADR 0021/0022) ;
 *   liste vide = comportement d'avant V-B (aucune classe light).
 */
public class EnvironnementNoms(
    racines: List<Path>,
    jars: List<Path>,
    jdkHome: Path?,
    synthetiques: List<TypesSynthetiques> = emptyList(),
) : INameEnvironment {

    /** Fournisseurs de types synthétiques (ordre de la liste conservé). */
    public val synthetiques: List<TypesSynthetiques> = synthetiques.toList()

    /** Cache négatif de présence source (TTL 30 s, invalidation Created). */
    public val cachePresence: CachePresenceSource = CachePresenceSource(racines)

    /** Cache de localisation fqcn → jar (positifs et négatifs, dispose). */
    public val cacheLocalisation: CacheLocalisation = CacheLocalisation(jars)

    /** Accès jrt (null si la plateforme est absente — régime instantané). */
    public val accesJrt: AccesJrt? = AccesJrt.ouvrir(jdkHome)

    /** Annuaire du classpath (étage N2 — complétion), paresseux et unique. */
    public val annuaire: AnnuaireClasspath by lazy {
        AnnuaireClasspath(racines, jars, accesJrt, synthetiques)
    }

    /**
     * Piste des fichiers sources servis pendant la passe courante (P8 —
     * fraîcheur inter-fichiers, ADR 0025) : un ensemble propre au THREAD
     * appelant, ouvert par [ouvrirPiste] et refermé par [fermerPiste] par
     * le moteur autour d'une résolution. Chaque [reponseSource] servi
     * pendant la fenêtre y est consigné — le moteur en dérive les
     * DÉPENDANCES DISQUE du diagnostic produit (chemin → empreinte), et le
     * mémo n'est plus servi si l'une d'elles change.
     *
     * Pourquoi un ThreadLocal : les API du moteur sont sérialisées par son
     * moniteur, mais la piste ne doit pas fuiter entre threads hôtes ni
     * entre passes imbriquées d'un même appel ( résolution → diagnostic
     * enrichi ) — la fenêtre explicite ouvrir/fermer est la seule frontière.
     */
    private val piste = ThreadLocal<MutableSet<Path>?>()

    /**
     * Ouvre le pistage des lectures disque pour le thread appelant — la
     * fenêtre vit jusqu'à [fermerPiste].
     *
     * @return l'ensemble récepteur des fichiers consignés.
     */
    public fun ouvrirPiste(): MutableSet<Path> {
        val ensemble = mutableSetOf<Path>()
        piste.set(ensemble)
        return ensemble
    }

    /** Referme le pistage du thread appelant (toute lecture suivante est invisible). */
    public fun fermerPiste() {
        piste.set(null)
    }

    /** Indique si une image jrt est chargée (sonde de plateforme). */
    public fun jrtDisponible(): Boolean = accesJrt != null

    /**
     * La plateforme est-elle RÉSOLUBLE par cet environnement — la sonde du
     * régime plein (retours appareil du 30/10 10:59, correctif v1.1.4) :
     * vrai dès que la classe racine `java.lang.Object` est atteignable,
     * soit par l'image jrt ( JVM de bureau, JDK Termux utilisable ), soit
     * par un JAR DU CLASSPATH porteur ( ART : le `android.jar` du modèle —
     * le boot classpath Android embarque `java.*` ET `android.*`, miroir
     * exact de la règle d'Android Studio « la plateforme suit le module » ).
     *
     * Pourquoi cette sonde : sur ART, le fournisseur `jrt` du
     * `lib/jrt-fs.jar` du JDK externe NE PEUT PAS s'instancier ( internes
     * JDK absents du runtime — incident contenu en v1.1.2 ) : l'ancienne
     * définition du régime plein — `jrtDisponible()` seul — dégradait donc
     * l'appareil en régime instantané ALORS QUE le classpath portait la
     * plateforme complète, coupant diagnostics analytiques, navigation,
     * survol, inlay et complétion de classpath ( INTEGRATION-CODEIDE §12 ).
     *
     * La sonde n'est PAS `aSourceBinaire()` : n'importe quel jar
     * applicatif rendrait « plateforme présente » à tort — il faut un jar
     * portant `java.lang.Object`, la classe que TOUTE résolution ecj
     * réclame. Le verdict est mémoïsé par [CacheLocalisation.jarPour]
     * ( positif et négatif ) : la sonde ne paie qu'UN balayage de table
     * centrale, jamais un parcours répété.
     *
     * @return vrai ssi la plateforme est résoluble ( jrt OU jar porteur ).
     */
    public fun plateformeResolvable(): Boolean =
        accesJrt != null || cacheLocalisation.jarPour(FQCN_SONDE_PLATEFORME) != null

    /** La classe racine qui définit la plateforme comme résoluble. */
    private companion object {
        const val FQCN_SONDE_PLATEFORME = "java.lang.Object"
    }

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
        if (octetsSynthetiques(fqcn) != null) return Localisation.Synthetique
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
        if (synthetiques.any { it.estPaquet(paquet) }) return true
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
        // 2. Types synthétiques light (V-B — après les sources : la source
        // générée réelle PRIME, anti-doublon ADR 0021 R3).
        octetsSynthetiques(fqcn)?.let { octets ->
            return reponseBinaire(fqcn, octets)
        }
        // 3. Jars (exact seulement — le chemin `$` retombe sans crash, fiche 024).
        cacheLocalisation.octetsPour(fqcn)?.let { octets ->
            return reponseBinaire(fqcn, octets)
        }
        // 4. Image jrt.
        accesJrt?.octets(fqcn)?.let { octets ->
            return reponseBinaire(fqcn, octets)
        }
        // 5. Stubs intrinsèques (liste fermée — dernier repli).
        StubsIntrinseques.octets(fqcn)?.let { octets ->
            return reponseBinaire(fqcn, octets)
        }
        return null
    }

    /** Les octets synthétiques d'un FQCN (premier fournisseur qui répond). */
    private fun octetsSynthetiques(fqcn: String): ByteArray? {
        for (fournisseur in synthetiques) {
            val octets = fournisseur.octetsPour(fqcn) ?: continue
            return octets
        }
        return null
    }

    /** Enveloppe une réponse SOURCE (l'unité sera parsée par ecj à la demande). */
    private fun reponseSource(fichier: Path): NameEnvironmentAnswer {
        piste.get()?.add(fichier)
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

        /** La classe est un type light fabriqué (R, Binding, Kotlin — V-B). */
        public object Synthetique : Localisation

        /** La classe vit dans un jar du classpath. */
        public object Jar : Localisation

        /** La classe vit dans l'image jrt. */
        public object Jrt : Localisation

        /** La classe est un intrinsèque stubé (liste fermée). */
        public object Stub : Localisation
    }
}
