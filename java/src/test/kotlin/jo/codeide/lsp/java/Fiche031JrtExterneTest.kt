package jo.codeide.lsp.java

import jo.codeide.lsp.java.diagnostics.Severite
import jo.codeide.lsp.java.interne.ItemCompletion
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Fiche 031 — ExternalJrtTest : le jrt EXTERNE — résolution des classes
 * java.* depuis un JDK autre que le runtime courant via son `lib/jrt-fs.jar`
 * (URLClassLoader + ServiceLoader du fournisseur jrt).
 */
class Fiche031JrtExterneTest {

    /** Le JDK externe à chemin fixe (contrat du cahier). */
    private val jdkExterne: Path = Path.of("/usr/lib/jvm/java-21-openjdk-amd64")

    /** Le JDK externe est présent (motif visible `@EnabledIf`). */
    private fun aJdkExterne(): Boolean = Files.isRegularFile(jdkExterne.resolve("lib/modules"))

    @Test
    fun resolvesJavaLangFromExternalJdkViaJrtFsJar() {
        assumeTrue(aJdkExterne(), "JDK 21 externe absent — test ignoré (skip visible)")
        val moteur = MoteurJava(jdkHome = jdkExterne)
        assertTrue(moteur.isLikelyUsable(), "le jrt externe rend le moteur utilisable")
        val source = """
            public class Essai {
                public String salut() {
                    return new java.lang.String("bonjour");
                }
            }
        """.trimIndent()
        val diagnostics = moteur.diagnose("/Essai.java", source)
        assertTrue(
            diagnostics.none { it.severite == Severite.ERREUR && it.message.contains("cannot be resolved") },
            "aucun message « cannot be resolved » : ${diagnostics.map { it.message }}",
        )
    }

    @Test
    fun externalJdkCompletesStringMembers() {
        assumeTrue(aJdkExterne(), "JDK 21 externe absent — test ignoré (skip visible)")
        val moteur = MoteurJava(jdkHome = jdkExterne)
        val source = """
            public class Essai {
                public int taille(String s) {
                    return s.le;
                }
            }
        """.trimIndent()
        val items: List<ItemCompletion> = moteur.complete("/Essai.java", source, source.indexOf("s.le") + 4)
        assertTrue(items.isNotEmpty(), "complétion non null")
        assertTrue(
            items.any { it.libelle == "length" },
            "« length » proposé — les membres de String résolvent depuis l'image externe : ${items.map { it.libelle }}",
        )
    }

    @Test
    fun nullJdkHomeFallsBackToRuntime() {
        val moteur = MoteurJava()
        assertTrue(moteur.isLikelyUsable(), "jdkHome null : le jrt du runtime courant est utilisé par défaut")
    }

    // ----------------------------------------------------------------- //
    // v1.1.2 — incident appareil du 30/10 au soir (CodeIDE 0.204.1,
    // moto g06) : sur ART, le fournisseur jrt du lib/jrt-fs.jar du JDK
    // externe ne peut PAS s'instancier (il référence des internes JDK
    // absents du runtime — jdk.internal.jimage notamment) ; ServiceLoader
    // le signale par ServiceConfigurationError / NoClassDefFoundError —
    // des ERROR, pas des Exception. L'ancien catch (Exception) laissait
    // l'Error s'échapper : la construction paresseuse d'EnvironnementNoms
    // explosait à la PREMIÈRE requête, et le serveur répondait
    // « Internal error. » à TOUT (complétion, surlignage, inlay — plus
    // AUCUN diagnostic).
    //
    // SIMULATION ART, vérifiée expérimentalement : sur JVM, le service
    // FileSystemProvider est TOUJOURS fourni par le module java.base
    // (couche boot) — le ServiceLoader itère les fournisseurs des modules
    // AVANT ceux du classpath du chargeur, même SANS parent : le
    // fournisseur système précède et MASQUE le cassé (c'est pourquoi la CI
    // JVM est aveugle à ce défaut — le test de masquage ci-dessous le
    // documente). La condition ART — le fournisseur cassé est le SEUL
    // itéré — exige donc un service SANS AUCUN module fournisseur :
    // [SondeFactice], déclarée uniquement dans un jar cassé.
    // ----------------------------------------------------------------- //

    /** Service factice de la régression — AUCUN module JVM ne le fournit. */
    interface SondeFactice {
        fun sonde(): String
    }

    /**
     * Fabrique un jar dont `META-INF/services` déclare [nomService] avec
     * un fournisseur inexistant — l'itération du ServiceLoader lève
     * ServiceConfigurationError dès l'instanciation, l'exacte signature
     * ART (provider présent dans le descripteur, classe introuvable ou
     * ininstanciable).
     */
    private fun jarFournisseurCasse(dossier: Path, nomService: String): Path {
        Files.createDirectories(dossier)
        val jarCasse = dossier.resolve("fournisseur-casse.jar")
        java.util.zip.ZipOutputStream(
            Files.newOutputStream(jarCasse),
        ).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("META-INF/services/$nomService"))
            zip.write("jo.factice.FournisseurInexistant".toByteArray())
            zip.closeEntry()
        }
        return jarCasse
    }

    /**
     * Fabrique un `lib/jrt-fs.jar` cassé (fournisseur FileSystemProvider
     * inexistant) — l'ossature du home JDK factice des tests de contrat.
     */
    private fun jarJrtFsCasse(dossier: Path): Path {
        val source = jarFournisseurCasse(dossier, "java.nio.file.spi.FileSystemProvider")
        val cible = dossier.resolve("jrt-fs.jar")
        Files.move(source, cible)
        return cible
    }

    @Test
    fun fournisseurCasseeContenuCommeSurArt() {
        // ART reproduit EXACTEMENT : un service sans AUCUN module
        // fournisseur (contrairement à FileSystemProvider, fourni par
        // java.base sur JVM) dont le seul fournisseur déclaré est cassé.
        // Son instanciation lève ServiceConfigurationError — un Error.
        val jar = jarFournisseurCasse(Files.createTempDirectory("svc-art"), SondeFactice::class.java.name)
        val chargeurArt = java.net.URLClassLoader(
            arrayOf(jar.toUri().toURL()),
            null, // pas de parent : le chargeur ne voit QUE le jar cassé
        )
        chargeurArt.use { chargeur ->
            // AVANT v1.1.2 : ServiceConfigurationError s'échappait d'ici
            // (un Error, invisible à catch (Exception)) — c'est LE défaut
            // appareil. APRÈS : null, repli contenu.
            val sonde =
                jo.codeide.lsp.java.classpath.AccesJrt.premierFournisseur(
                    SondeFactice::class.java,
                    chargeur,
                ) { true }
            kotlin.test.assertNull(
                sonde,
                "fournisseur cassé : l'Error d'instanciation est CONTENUE (null), jamais propagée",
            )
        }
    }

    @Test
    fun surJvmLeFournisseurSystemeMasqueLeDefaut() {
        // DOCUMENTATION du masquage JVM (pourquoi la CI est aveugle à ce
        // défaut) : même avec un chargeur SANS parent et un jrt-fs.jar
        // cassé, l'itération du ServiceLoader rend le fournisseur jrt de
        // java.base D'ABORD (modules avant classpath) — le défaut n'est
        // jamais atteint sur JVM. Vérifié expérimentalement : l'itération
        // COMPLÈTE finit par lever, mais firstOrNull(schéma jrt) s'arrête
        // au fournisseur système.
        val jar = jarJrtFsCasse(Files.createTempDirectory("jrtfs-masque"))
        val chargeur = java.net.URLClassLoader(arrayOf(jar.toUri().toURL()), null)
        chargeur.use { c ->
            val fournisseur = jo.codeide.lsp.java.classpath.AccesJrt.fournisseurJrtDe(c)
            kotlin.test.assertNotNull(
                fournisseur,
                "sur JVM, le fournisseur jrt de java.base précède le cassé (masquage documenté)",
            )
        }
    }

    @Test
    fun ouvrirSurJdkFacticeNeLeveJamais() {
        // Contrat de surface (fiche 031) : quel que soit l'état du home
        // JDK, ouvrir ne LÈVE JAMAIS — l'échec (provider cassé, image
        // illisible) se traduit par null ou un accès utilisable. Sur JVM
        // le fournisseur système peut masquer le défaut (comportement
        // accidentel mais stable) : la garantie testée est le NON-JET.
        val dossier = Files.createTempDirectory("jdk-art-factice")
        val lib = dossier.resolve("lib")
        Files.createDirectories(lib)
        Files.writeString(lib.resolve("modules"), "image factice")
        jarJrtFsCasse(lib)
        val acces = jo.codeide.lsp.java.classpath.AccesJrt.ouvrir(dossier)
        acces?.close()
    }

    @Test
    fun moteurSurJdkCasseeRepondAuxRequetes() {
        // La signature EXACTE du défaut appareil : toute requête moteur
        // (complétion ici) répond SANS lever — même quand la plateforme
        // jrt est indisponible, le fichier courant reste servi (fiche 028).
        val dossier = Files.createTempDirectory("jdk-art-factice-moteur")
        val lib = dossier.resolve("lib")
        Files.createDirectories(lib)
        Files.writeString(lib.resolve("modules"), "image factice")
        jarJrtFsCasse(lib)
        val moteur = MoteurJava(jdkHome = dossier)
        val source = """
            public class Main {
                private int compteurTotal = 0;

                public void premiere() {
                    compteurTotal = compteurTo;
                }
            }
        """.trimIndent()
        val items = moteur.complete("/Main.java", source, source.indexOf("compteurTo;") + 10)
        assertTrue(
            items.any { it.libelle == "compteurTotal" },
            "la complétion répond même sans plateforme jrt (champ du fichier courant servi) : ${items.map { it.libelle }}",
        )
    }
}
