package jo.codeide.lsp.java.renommage

import jo.codeide.lsp.java.MoteurJava
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Vague V-D TRANCHE 3 — RENOMMAGE DE RESSOURCE (écart G-028 : « pas de
 * rename de ressource (propagation code + XML) ») : `R.<type>.<nom>` au
 * caret renomme la ressource dans le CODE Java (bindings des classes
 * light) ET dans les XML (déclaration `name=`, références `@type/`,
 * `@+id/`, `?attr/`, expressions de data binding).
 *
 * Critères :
 *  - l'oracle « 0 occurrence manquée » : chaque occurrence Java et XML de
 *    la ressource est retouchée (aucune trace de l'ancien nom ne survit) ;
 *  - le résultat COMPILE (le nouveau `R.string.<nouveau>` résout sur un
 *    moteur NEUF reconstruit sur les sources renommées — le référentiel
 *    rescanné sert la classe light) ;
 *  - refus honnêtes (R1) : ressource-fichier (layout — le nom vit dans le
 *    nom du fichier), styleable, autre paquet (android.R) ;
 *  - collisions typées : identifiant invalide, nom déjà pris du même type.
 */
class RenommageRessourcesTest {

    /** Une racine de module Android minimal : sources + res/. */
    private data class Module(val racine: Path)

    private fun module(
        java: Map<String, String>,
        xml: Map<String, String>,
    ): Module {
        val racine = Files.createTempDirectory("vd3-ressources")
        Files.createDirectories(racine.resolve("demo"))
        for ((nom, texte) in java) {
            racine.resolve("demo").resolve(nom).writeText(texte)
        }
        for ((chemin, texte) in xml) {
            val fichier = racine.resolve(chemin)
            fichier.parent?.let { Files.createDirectories(it) }
            fichier.writeText(texte)
        }
        return Module(racine)
    }

    private fun moteurDe(module: Module): MoteurJava {
        val contrat = jo.codeide.lsp.java.projetandroid.ContratAndroidMoteur(
            namespace = "demo",
            dossiersRes = listOf(module.racine.resolve("res")),
        )
        return MoteurJava(listOf(module.racine), emptyList(), null, "17", null, null, contrat)
    }

    /** Applique les éditions par fichier (clé : chemin disque) aux fichiers du module. */
    private fun appliquer(module: Module, editions: Map<String, List<jo.codeide.lsp.java.outils.EditionTexte>>) {
        for ((fichier, liste) in editions) {
            val chemin = Path.of(fichier)
            val texte = chemin.toFile().readText()
            chemin.toFile().writeText(jo.codeide.lsp.java.outils.appliquerEditions(texte, liste))
        }
    }

    /** Vrai ssi le fichier du module contient le texte. */
    private fun contient(module: Module, cheminRelatif: String, aiguille: String): Boolean =
        module.racine.resolve(cheminRelatif).toFile().readText().contains(aiguille)

    // ------------------------------------------------------------------ //
    // La cible au caret
    // ------------------------------------------------------------------ //

    @Test
    fun `la ressource au caret est identifiee par bindings (type R et nom)`() {
        val source = """
            package demo;
            public class Main {
                int texte() { return R.string.bonjour; }
            }
        """.trimIndent()
        val module = module(
            java = mapOf("Main.java" to source),
            xml = mapOf(
                "res/values/strings.xml" to """
                    <resources>
                        <string name="bonjour">Salut</string>
                        <string name="au_revoir">Bye</string>
                    </resources>
                """.trimIndent(),
            ),
        )
        val moteur = moteurDe(module)
        val offset = source.indexOf("bonjour")
        val cible = moteur.cibleRessourceA("demo/Main.java", source, offset)
        assertNotNull(cible, "R.string.bonjour au caret EST une ressource")
        assertEquals("string", cible!!.type)
        assertEquals("bonjour", cible.nom)
        assertNull(cible.refus, "une ressource values est renommable")
        assertEquals("bonjour", source.substring(cible.jeton.debutOffset, cible.jeton.finOffset), "le jeton couvre exactement le nom")

        // Une autre position du même fichier n'est PAS une ressource.
        assertNull(moteur.cibleRessourceA("demo/Main.java", source, source.indexOf("texte()")), "hors R.<type>.<nom> : null")
    }

    @Test
    fun `les ressources fichier et styleable refusent honnetement`() {
        val source = """
            package demo;
            public class Main {
                int vue() { return R.layout.activity; }
                int attr() { return R.styleable.Exemple_libelle; }
            }
        """.trimIndent()
        val module = module(
            java = mapOf("Main.java" to source),
            xml = mapOf(
                "res/layout/activity.xml" to "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"/>",
                "res/values/attrs.xml" to """
                    <resources>
                        <declare-styleable name="Exemple">
                            <attr name="libelle" format="string"/>
                        </declare-styleable>
                    </resources>
                """.trimIndent(),
            ),
        )
        val moteur = moteurDe(module)
        val layout = moteur.cibleRessourceA("demo/Main.java", source, source.indexOf("activity"))
        assertNotNull(layout)
        assertEquals("fichier", layout!!.refus, "un layout vit dans le NOM DE FICHIER — le rename de fichier appartient au client")
        val styleable = moteur.cibleRessourceA("demo/Main.java", source, source.indexOf("libelle"))
        assertNotNull(styleable)
        assertEquals("styleable", styleable!!.refus, "un champ de declare-styleable appartient à la structure du styleable")
    }

    // ------------------------------------------------------------------ //
    // La propagation code + XML
    // ------------------------------------------------------------------ //

    @Test
    fun `renommer une ressource propage code java et xml (declaration et references)`() {
        val source = """
            package demo;
            public class Main {
                int texte() { return R.string.bonjour; }
            }
        """.trimIndent()
        val client = """
            package demo;
            public class Client {
                int texteDistant() { return demo.R.string.bonjour; }
            }
        """.trimIndent()
        val module = module(
            java = mapOf("Main.java" to source, "Client.java" to client),
            xml = mapOf(
                "res/values/strings.xml" to """
                    <resources>
                        <string name="bonjour">Salut</string>
                        <string name="autre">Autre</string>
                    </resources>
                """.trimIndent(),
                "res/values-fr/strings.xml" to """
                    <resources>
                        <string name="bonjour">Coucou</string>
                    </resources>
                """.trimIndent(),
                "res/layout/activity.xml" to """
                    <TextView xmlns:android="http://schemas.android.com/apk/res/android"
                        android:text="@string/bonjour"/>
                """.trimIndent(),
                "res/menu/main.xml" to """
                    <menu xmlns:android="http://schemas.android.com/apk/res/android">
                        <item android:title="@demo:string/bonjour"/>
                    </menu>
                """.trimIndent(),
            ),
        )
        val moteur = moteurDe(module)
        val offset = source.indexOf("bonjour")

        // prepareRename : le placeholder EXACT.
        val service = ServiceRenommage(moteur)
        val preparation = service.preparerRenommage("demo/Main.java", source, offset)
        assertNotNull(preparation)
        assertEquals("bonjour", preparation!!.placeholder)

        val editions = moteur.renommageRessource("demo/Main.java", source, offset, "salutations")
        assertTrue(editions.isNotEmpty(), "les éditions couvrent Java ET XML")
        appliquer(module, editions)

        // ORACLE « 0 occurrence manquée » : plus AUCUNE trace de l'ancien
        // nom nulle part (Java, values, values-fr, layout, menu).
        assertTrue(contient(module, "demo/Main.java", "R.string.salutations"), "le code Java courant est retouché")
        assertTrue(contient(module, "demo/Client.java", "demo.R.string.salutations"), "la référence QUALIFIÉE distante est retouchée")
        assertTrue(contient(module, "res/values/strings.xml", "name=\"salutations\""), "la déclaration values est retouchée")
        assertTrue(contient(module, "res/values-fr/strings.xml", "name=\"salutations\""), "la configuration values-fr est retouchée")
        assertTrue(contient(module, "res/layout/activity.xml", "@string/salutations"), "la référence @string/ du layout est retouchée")
        assertTrue(contient(module, "res/menu/main.xml", "@demo:string/salutations"), "la référence à paquet explicite est retouchée")
        for (chemin in listOf(
            "demo/Main.java", "demo/Client.java", "res/values/strings.xml",
            "res/values-fr/strings.xml", "res/layout/activity.xml", "res/menu/main.xml",
        )) {
            assertEquals(false, contient(module, chemin, "bonjour"), "aucune trace de l'ancien nom dans $chemin")
        }

        // Le RÉSULTAT COMPILE : un moteur NEUF sur les sources renommées
        // sert la classe light depuis le référentiel rescanné.
        val temoin = moteurDe(module)
        val sourceApres = module.racine.resolve("demo/Main.java").toFile().readText()
        val clientApres = module.racine.resolve("demo/Client.java").toFile().readText()
        assertEquals(
            0,
            temoin.diagnose("demo/Main.java", sourceApres).count { it.severite == jo.codeide.lsp.java.diagnostics.Severite.ERREUR },
            "R.string.salutations résout après renommage",
        )
        assertEquals(
            0,
            temoin.diagnose("demo/Client.java", clientApres).count { it.severite == jo.codeide.lsp.java.diagnostics.Severite.ERREUR },
        )
    }

    @Test
    fun `renommer un id propage les declarations inline et le data binding`() {
        val source = """
            package demo;
            public class Main {
                int vue() { return R.id.zone; }
            }
        """.trimIndent()
        val module = module(
            java = mapOf("Main.java" to source),
            xml = mapOf(
                "res/layout/activity.xml" to """
                    <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                        android:id="@+id/zone"
                        android:tag="@{String.valueOf(R.id.zone)}">
                        <TextView android:id="@id/zone"/>
                    </LinearLayout>
                """.trimIndent(),
                "res/menu/main.xml" to """
                    <menu xmlns:android="http://schemas.android.com/apk/res/android">
                        <item android:id="@+id/zone"/>
                    </menu>
                """.trimIndent(),
            ),
        )
        val moteur = moteurDe(module)
        val editions = moteur.renommageRessource("demo/Main.java", source, source.indexOf("zone"), "surface")
        assertTrue(editions.isNotEmpty())
        appliquer(module, editions)
        assertTrue(contient(module, "res/layout/activity.xml", "@+id/surface"), "la déclaration inline @+id est retouchée")
        assertTrue(contient(module, "res/layout/activity.xml", "@id/surface"), "la référence @id est retouchée")
        assertTrue(contient(module, "res/layout/activity.xml", "R.id.surface"), "l'expression de DATA BINDING est retouchée")
        assertTrue(contient(module, "res/menu/main.xml", "@+id/surface"), "le id de menu est retouché")
        assertEquals(false, contient(module, "res/layout/activity.xml", "zone"), "aucune trace de l'ancien id")
    }

    @Test
    fun `renommer refuse l homonyme d un autre type R (departage par bindings)`() {
        val source = """
            package demo;
            public class Main {
                int a() { return R.string.nom; }
                int b() { return R.layout.nom; }
            }
        """.trimIndent()
        val module = module(
            java = mapOf("Main.java" to source),
            xml = mapOf(
                "res/values/strings.xml" to "<resources><string name=\"nom\">X</string></resources>",
                "res/layout/nom.xml" to "<merge/>",
            ),
        )
        val moteur = moteurDe(module)
        val editions = moteur.renommageRessource("demo/Main.java", source, source.indexOf("nom"), "titre")
        appliquer(module, editions)
        assertTrue(contient(module, "demo/Main.java", "R.string.titre"), "R.string.nom est renommé")
        assertTrue(contient(module, "demo/Main.java", "R.layout.nom;"), "R.layout.nom n'est PAS touché (le type R départage)")
    }

    // ------------------------------------------------------------------ //
    // Collisions
    // ------------------------------------------------------------------ //

    @Test
    fun `les collisions d un renommage de ressource sont typées`() {
        val source = """
            package demo;
            public class Main {
                int texte() { return R.string.bonjour; }
            }
        """.trimIndent()
        val module = module(
            java = mapOf("Main.java" to source),
            xml = mapOf(
                "res/values/strings.xml" to "<resources><string name=\"bonjour\">Salut</string><string name=\"titre\">T</string></resources>",
            ),
        )
        val moteur = moteurDe(module)
        val offset = source.indexOf("bonjour")

        val invalides = moteur.conflitsRessource("demo/Main.java", source, offset, "3invalides")
        assertTrue(invalides.any { it.genre == GenreConflit.IDENTIFIANT_INVALIDE }, "un nom de ressource devient un champ Java : identifiant requis")

        val pris = moteur.conflitsRessource("demo/Main.java", source, offset, "titre")
        assertTrue(pris.any { it.genre == GenreConflit.NOM_PRIS }, "le référentiel est la source de vérité du nom pris")

        val libre = moteur.conflitsRessource("demo/Main.java", source, offset, "salutations")
        assertTrue(libre.isEmpty(), "un nom libre ne collisionne pas")
    }

    @Test
    fun `le service de renommage sert la forme negociee multi-fichiers`() {
        val source = """
            package demo;
            public class Main {
                int texte() { return R.string.bonjour; }
            }
        """.trimIndent()
        val module = module(
            java = mapOf("Main.java" to source),
            xml = mapOf(
                "res/values/strings.xml" to "<resources><string name=\"bonjour\">Salut</string></resources>",
            ),
        )
        val moteur = moteurDe(module)
        val service = ServiceRenommage(moteur)
        val edition = service.renommage(
            uri = module.racine.resolve("demo/Main.java").toUri().toString(),
            chemin = "demo/Main.java",
            source = source,
            offset = source.indexOf("bonjour"),
            nouveauNom = "salutations",
        )
        val uris = edition.changes?.keys ?: emptyList()
        assertTrue(uris.any { it.endsWith("demo/Main.java") }, "le document courant voyage sous SON URI")
        assertTrue(uris.any { it.endsWith("strings.xml") }, "le XML voyage sous file:// — $uris")
        assertEquals(2, edition.nombreEditions(), "une édition Java + une édition XML (name=)")
    }
}
