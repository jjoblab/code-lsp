package jo.codeide.lsp.kotlin.session

import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import jo.codeide.lsp.kotlin.api.ClasspathKotlin
import jo.codeide.lsp.kotlin.api.EntreeClasspathKotlin
import jo.codeide.lsp.kotlin.api.KindEntreeKotlin
import jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin

/**
 * Fiche 018 — FicheClasspathTest : le chantier P1-D « documentation du
 * classpath au survol et à la signature » (ADR 0230) — par le SPI (chemin
 * de production D6) : la FICHE STRUCTURELLE du survol d'un type du
 * classpath (genre, héritage direct, interfaces, constantes d'enum, champs
 * publics) prouvée par la LECTURE BRUTE du `.class` (partie survol — 6
 * comportements sur 11 ; la partie SIGNATURE vit dans la vague 6d, mêmes
 * racines : les jars de fixture ci-dessous).

 * Les OCTETS sont des class-files de fixture RÉELS (miroir du montage
 * vague 2c : AUCUNE vue factice — la vue JVM du moteur lit les ENTRÉES du
 * classpath comme celles d'une bibliothèque véritable). Chaque test
 * éprouve AUSSI la borne conservatrice : un binaire à hiérarchie
 * illisible tombe sur le genre et les champs, un champ synthétique ou le
 * marqueur `INSTANCE` d'un objet ne se montrent jamais.
 *
 * Chaque comportement porte le nom exact du test source (identifiant de
 * traçabilité du cahier).
 */
class Fiche018FicheClasspathTest {

    /** Le dossier des jars de fixture. */
    @TempDir
    lateinit var dossier: Path

    /**
     * Le jar des types de la fiche : TextView (classe + héritage + champs
     * publics filtrés), Robot (interface à DEUX interfaces directes),
     * Couleur (enum trois constantes + $VALUES exclu), Outil (objet par
     * marqueur INSTANCE), Nombre (dix champs — le plafond 8), ArcEnCiel
     * (22 constantes — le plafond 20), SansBinaire (hiérarchie BRISEÉ).
     */
    private fun jarFiche(): java.io.File {
        val batisseur = OutilsJarFixture.Batisseur()
        // Classe publique : un champ public, un PRIVÉ (exclu), un
        // synthétique `$` public (exclu).
        batisseur.classeGenree(
            "android.widget.TextView", "android.view.View", 0x0021,
            membres = arrayOf(
                OutilsJarFixture.champ("texte", "Ljava/lang/CharSequence;"),
                OutilsJarFixture.champPrive("cache", "Ljava/lang/String;"),
                OutilsJarFixture.champ("\$observateur", "Ljava/lang/Object;"),
            ),
        )
        // Interface publique à DEUX interfaces directes.
        batisseur.classeGenree(
            "demo.Robot", "java.lang.Object", 0x0201,
            interfacesDirectes = listOf("java.lang.Runnable", "demo.Capable"),
            membres = arrayOf(OutilsJarFixture.methodeAbstraite("avance", "()V")),
        )
        // Enum : trois constantes (le super java.lang.Enum est MASQUÉ) + le
        // champ synthétique `$VALUES` (privé ET `$`).
        batisseur.classeGenree(
            "demo.Couleur", "java.lang.Enum", 0x4001,
            membres = arrayOf(
                OutilsJarFixture.constanteEnum("ROUGE", "Ldemo/Couleur;"),
                OutilsJarFixture.constanteEnum("VERT", "Ldemo/Couleur;"),
                OutilsJarFixture.constanteEnum("BLEU", "Ldemo/Couleur;"),
                OutilsJarFixture.champPrive("\$VALUES", "[Ldemo/Couleur;"),
            ),
        )
        // Objet Kotlin : marqueur INSTANCE (statique final) + un vrai champ
        // public.
        batisseur.classeGenree(
            "demo.Outil", "java.lang.Object", 0x0031,
            membres = arrayOf(
                OutilsJarFixture.champStatiqueFinal("INSTANCE", "Ldemo/Outil;"),
                OutilsJarFixture.champ("version", "Ljava/lang/String;"),
            ),
        )
        // Dix champs publics : le plafond 8 + « … ».
        batisseur.classeGenree(
            "demo.Nombre", "java.lang.Object", 0x0021,
            membres = ('a'..'j').map { lettre -> OutilsJarFixture.champ(lettre.toString(), "I") }.toTypedArray(),
        )
        // 22 constantes : le plafond 20 + « … ».
        batisseur.classeGenree(
            "demo.ArcEnCiel", "java.lang.Enum", 0x4001,
            membres = (1..22).map { indice -> OutilsJarFixture.constanteEnum("C$indice", "Ldemo/ArcEnCiel;") }.toTypedArray(),
        )
        // La hiérarchie BRISEÉ : le super pointe sur un Utf8 du pool — le
        // genre et les champs restent prouvés, l'héritage se tait (D3).
        batisseur.classeGenree(
            "demo.SansBinaire", "java.lang.Object", 0x0021,
            membres = arrayOf(OutilsJarFixture.champ("nom", "Ljava/lang/String;")),
        ).briserHierarchie("demo.SansBinaire")
        return batisseur.ecrire(dossier, "fiche-classpath.jar")
    }

    /** La session branchée sur le jar de la fiche (index attendu). */
    private fun session(): SessionLangageKotlinDocuments {
        val moteur = ServiceMoteurLangageKotlin.unique as MoteurLangageKotlinNoyau
        val classpath = ClasspathKotlin(
            entrees = listOf(
                EntreeClasspathKotlin(KindEntreeKotlin.JAR, jarFiche().path, "f018:fiche"),
            ),
            racinesSources = emptyList(),
        )
        moteur.definirClasspath(classpath)
        val session = moteur.creerSession() as SessionLangageKotlinDocuments
        session.definirClasspath(classpath)
        attendreCouvert(session)
        return session
    }

    /** Attend que l'index de la session soit couvert (polling, miroir fiche 032). */
    private fun attendreCouvert(session: SessionLangageKotlinDocuments, secondes: Long = 90) {
        val fin = System.nanoTime() + secondes * 1_000_000_000
        while (System.nanoTime() < fin) {
            if (session.indexSiPret() != null) return
            Thread.sleep(10)
        }
    }

    // ── Fiche de survol d'un type du classpath ─────────────────────────

    @Test
    fun `survol-type-classpath---classe,-heritage-direct-et-champs-publics`() {
        val s = session()
        try {
            val source =
                "import android.widget.TextView\n" +
                    "fun dessiner(vue: TextView) {}\n"
            val uri = "file:///dessin.kt"
            s.ouvrir(uri, source)
            val survol = assertNotNull(s.survol(uri, source.indexOf("TextView)")))
            assertEquals("public class android.widget.TextView extends android.view.View", survol.signature)
            assertEquals("Champs publics : CharSequence texte", survol.documentation)
        } finally {
            s.close()
        }
    }

    @Test
    fun `survol-type-classpath---interface,-section-Interfaces,-jamais-d-extends`() {
        val s = session()
        try {
            val source =
                "import demo.Robot\n" +
                    "fun piloter(robot: Robot) {}\n"
            val uri = "file:///pilotage.kt"
            s.ouvrir(uri, source)
            val survol = assertNotNull(s.survol(uri, source.indexOf("Robot)")))
            assertEquals("public interface demo.Robot", survol.signature)
            assertEquals("Interfaces : java.lang.Runnable, demo.Capable", survol.documentation)
        } finally {
            s.close()
        }
    }

    @Test
    fun `survol-type-classpath---enum,-constantes-servies,-super-Enum-masque`() {
        val s = session()
        try {
            val source =
                "import demo.Couleur\n" +
                    "fun peindre(c: Couleur) {}\n"
            val uri = "file:///peinture.kt"
            s.ouvrir(uri, source)
            val survol = assertNotNull(s.survol(uri, source.indexOf("Couleur)")))
            assertEquals("public enum demo.Couleur", survol.signature)
            assertEquals("Constantes : ROUGE, VERT, BLEU", survol.documentation)
        } finally {
            s.close()
        }
    }

    @Test
    fun `survol-type-classpath---objet-Kotlin-par-champ-INSTANCE,-marqueur-exclu-des-champs`() {
        val s = session()
        try {
            val source =
                "import demo.Outil\n" +
                    "fun verifier(o: Outil) {}\n"
            val uri = "file:///outils.kt"
            s.ouvrir(uri, source)
            val survol = assertNotNull(s.survol(uri, source.indexOf("Outil)")))
            assertEquals("public object demo.Outil", survol.signature)
            assertEquals("Champs publics : String version", survol.documentation)
        } finally {
            s.close()
        }
    }

    @Test
    fun `survol-type-classpath---plafonds-d-affichage,-champs-8-et-constantes-20`() {
        val s = session()
        try {
            val source =
                "import demo.Nombre\n" +
                    "import demo.ArcEnCiel\n" +
                    "fun mesurer(n: Nombre, a: ArcEnCiel) {}\n"
            val uri = "file:///mesures.kt"
            s.ouvrir(uri, source)
            val survolNombre = assertNotNull(s.survol(uri, source.indexOf("Nombre,")))
            assertEquals("public class demo.Nombre", survolNombre.signature)
            assertEquals(
                "Champs publics : Int a, Int b, Int c, Int d, Int e, Int f, Int g, Int h, …",
                survolNombre.documentation,
            )
            val survolArc = assertNotNull(s.survol(uri, source.indexOf("ArcEnCiel)")))
            assertEquals("public enum demo.ArcEnCiel", survolArc.signature)
            val attendu =
                "Constantes : " + (1..20).joinToString(", ") { indice -> "C$indice" } + ", …"
            assertEquals(attendu, survolArc.documentation)
        } finally {
            s.close()
        }
    }

    @Test
    fun `survol-type-classpath---binaire-illisible,-genre-et-champs-sans-heritage-ni-interfaces`() {
        val s = session()
        try {
            val source =
                "import demo.SansBinaire\n" +
                    "fun charger(s: SansBinaire) {}\n"
            val uri = "file:///chargement.kt"
            s.ouvrir(uri, source)
            val survol = assertNotNull(s.survol(uri, source.indexOf("SansBinaire)")))
            // Hiérarchie brisée dans le binaire : le genre (drapeaux de la
            // classe) et les champs restent prouvés, l'héritage et les
            // interfaces se taisent (D3 — jamais une ligne inventée).
            assertEquals("public class demo.SansBinaire", survol.signature)
            assertEquals("Champs publics : String nom", survol.documentation)
        } finally {
            s.close()
        }
    }
}
