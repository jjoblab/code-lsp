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
 * COMPLÉMENT DE CATALOGUE — RENAME DEPUIS UN DOCUMENT XML (ADR 0032,
 * reste consigné du PV V-D3 / matrice G-028 : « rename DEPUIS un XML =
 * famille suivante ») : la ressource au caret d'un XML (DÉCLARATION
 * `name="…"` des values, RÉFÉRENCE `@type/…`, `@+type/…`, `?type/…`,
 * expression `R.type.…` du data binding) renomme la ressource PARTOUT —
 * code Java (bindings) ET XML du module — le document XML COURANT servi
 * depuis son TAMPON OUVERT (fait foi).
 *
 * Critères :
 *  - l'oracle « 0 occurrence manquée » étendu au départ XML ;
 *  - le TAMPON OUVERT fait foi (le disque peut être périmé) ;
 *  - refus honnêtes (R1) : ressource-fichier, inconnu ;
 *  - collisions typées : identifiant invalide, nom déjà pris ;
 *  - le service `ServiceRenommage` sert prepareRename/rename XML sous
 *    la forme NÉGOCIÉE (changes ou documentChanges versionnée).
 */
class RenommageRessourcesXmlTest {

    /** Une racine de module Android minimal : sources + res/. */
    private data class Module(val racine: Path)

    private fun module(
        java: Map<String, String>,
        xml: Map<String, String>,
    ): Module {
        val racine = Files.createTempDirectory("cat-xml")
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

    // ------------------------------------------------------------------ //
    // La cible au caret d'un XML
    // ------------------------------------------------------------------ //

    @Test
    fun `la reference string dans un layout identifie la ressource au caret`() {
        val layout = """
            <LinearLayout>
                <TextView android:text="@string/bonjour"/>
                <TextView android:text="@string/au_revoir"/>
            </LinearLayout>
        """.trimIndent()
        val module = module(
            java = mapOf(
                "Main.java" to """
                    package demo;
                    public class Main {
                        String texte() { return getString(); }
                        String getString() { return null; }
                    }
                """.trimIndent(),
            ),
            xml = mapOf(
                "res/values/strings.xml" to """
                    <resources>
                        <string name="bonjour">Salut</string>
                        <string name="au_revoir">Bye</string>
                    </resources>
                """.trimIndent(),
                "res/layout/activity.xml" to layout,
            ),
        )
        val moteur = moteurDe(module)
        val cheminLayout = module.racine.resolve("res/layout/activity.xml").toString()
        val offset = layout.indexOf("bonjour")

        val cible = moteur.cibleRessourceXmlA(cheminLayout, layout, offset)
        assertNotNull(cible, "@string/bonjour au caret EST une ressource")
        assertEquals("string", cible!!.type)
        assertEquals("bonjour", cible.nom)
        assertNull(cible.refus, "une ressource values référencée depuis un XML est renommable")
        assertEquals("bonjour", layout.substring(cible.jeton.debutOffset, cible.jeton.finOffset), "le jeton couvre exactement le segment nom")

        // Une position hors référence n'est PAS une ressource.
        assertNull(moteur.cibleRessourceXmlA(cheminLayout, layout, layout.indexOf("LinearLayout")), "hors référence : null")
        // La référence VOISINE ne vole pas le caret.
        assertEquals("au_revoir", moteur.cibleRessourceXmlA(cheminLayout, layout, layout.indexOf("au_revoir"))!!.nom)
    }

    @Test
    fun `la declaration name des values identifie la ressource au caret`() {
        val strings = """
            <resources>
                <string name="bonjour">Salut</string>
            </resources>
        """.trimIndent()
        val module = module(
            java = mapOf(
                "Main.java" to """
                    package demo;
                    public class Main {
                        int texte() { return R.string.bonjour; }
                    }
                """.trimIndent(),
            ),
            xml = mapOf("res/values/strings.xml" to strings),
        )
        val moteur = moteurDe(module)
        val chemin = module.racine.resolve("res/values/strings.xml").toString()
        val cible = moteur.cibleRessourceXmlA(chemin, strings, strings.indexOf("bonjour\""))
        assertNotNull(cible, "la déclaration name= porte la ressource")
        assertEquals("string", cible!!.type)
        assertEquals("bonjour", cible.nom)
        assertNull(cible.refus)
    }

    // ------------------------------------------------------------------ //
    // La propagation (tampon ouvert fait foi)
    // ------------------------------------------------------------------ //

    @Test
    fun `renommer depuis un layout propage code java et xml avec le tampon ouvert en fait de foi`() {
        val layoutDisque = """
            <LinearLayout>
                <TextView android:text="@string/bonjour"/>
            </LinearLayout>
        """.trimIndent()
        // Le TAMPOIN OUVERT diffère du disque : une référence de PLUS vit
        // dans le tampon (le client a édité sans sauvegarder).
        val layoutTampon = """
            <LinearLayout>
                <TextView android:text="@string/bonjour"/>
                <Button android:text="@string/bonjour"/>
            </LinearLayout>
        """.trimIndent()
        val menu = """
            <menu>
                <item android:title="@string/bonjour"/>
            </menu>
        """.trimIndent()
        val module = module(
            java = mapOf(
                "Main.java" to """
                    package demo;
                    public class Main {
                        String texte() { return String.valueOf(R.string.bonjour); }
                    }
                """.trimIndent(),
            ),
            xml = mapOf(
                "res/values/strings.xml" to """
                    <resources>
                        <string name="bonjour">Salut</string>
                        <string name="au_revoir">Bye</string>
                    </resources>
                """.trimIndent(),
                "res/layout/activity.xml" to layoutDisque,
                "res/menu/principal.xml" to menu,
            ),
        )
        val moteur = moteurDe(module)
        val cheminLayout = module.racine.resolve("res/layout/activity.xml").toString()
        val offset = layoutTampon.indexOf("bonjour")

        val editions = moteur.renommageRessourceXml(cheminLayout, layoutTampon, offset, "salutations")
        assertTrue(editions.isNotEmpty(), "le renommage propage")

        // Le CODE Java.
        val javaApplique = jo.codeide.lsp.java.outils.appliquerEditions(
            module.racine.resolve("demo").resolve("Main.java").toFile().readText(),
            editions[module.racine.resolve("demo").resolve("Main.java").toString()]!!,
        )
        assertTrue(javaApplique.contains("R.string.salutations"), "la référence Java est retouchée")
        assertTrue(!javaApplique.contains("R.string.bonjour"), "aucune trace de l'ancien nom côté Java")

        // La DÉCLARATION values.
        val stringsApplique = jo.codeide.lsp.java.outils.appliquerEditions(
            module.racine.resolve("res/values/strings.xml").toFile().readText(),
            editions[module.racine.resolve("res/values/strings.xml").toString()]!!,
        )
        assertTrue(stringsApplique.contains("<string name=\"salutations\">"), "la déclaration est retouchée")

        // Le document COURANT : les occurrences du TAMPON (pas du disque).
        val layoutApplique = jo.codeide.lsp.java.outils.appliquerEditions(layoutTampon, editions[cheminLayout]!!)
        assertEquals(2, Regex("@string/salutations").findAll(layoutApplique).count(), "les DEUX occurrences du tampon (TextView + Button) sont retouchées")

        // Le menu distant.
        val menuApplique = jo.codeide.lsp.java.outils.appliquerEditions(
            menu,
            editions[module.racine.resolve("res/menu/principal.xml").toString()]!!,
        )
        assertTrue(menuApplique.contains("@string/salutations"))
    }

    @Test
    fun `les refus honnetes depuis un xml portent leur motif`() {
        val layout = """
            <LinearLayout>
                <include layout="@layout/autre"/>
            </LinearLayout>
        """.trimIndent()
        val module = module(
            java = mapOf(
                "Main.java" to """
                    package demo;
                    public class Main {
                        int vue() { return R.layout.autre; }
                    }
                """.trimIndent(),
            ),
            xml = mapOf(
                "res/layout/activity.xml" to layout,
                "res/layout/autre.xml" to "<merge/>",
            ),
        )
        val moteur = moteurDe(module)
        val chemin = module.racine.resolve("res/layout/activity.xml").toString()
        val cible = moteur.cibleRessourceXmlA(chemin, layout, layout.indexOf("autre"))
        assertNotNull(cible, "la référence @layout/autre est localisée")
        assertEquals("fichier", cible!!.refus, "une ressource-fichier refuse (le nom vit dans le nom du fichier)")

        // Hors dossiers res du module : null.
        val horsModule = module.racine.resolve("demo").resolve("hors.xml").writeText("<x a=\"@string/bonjour\"/>")
        assertNull(moteur.cibleRessourceXmlA(horsModule.toString(), "<x a=\"@string/bonjour\"/>", 6), "un XML hors res/ n'est pas servi")

        // Une référence INCONNUE du référentiel refuse.
        val layoutInconnu = "<TextView android:text=\"@string/fantome\"/>"
        val moduleInconnu = module(
            java = mapOf("Main.java" to "package demo;\npublic class Main {}"),
            xml = mapOf("res/layout/activity.xml" to layoutInconnu),
        )
        val moteurInconnu = moteurDe(moduleInconnu)
        val cibleInconnue = moteurInconnu.cibleRessourceXmlA(
            moduleInconnu.racine.resolve("res/layout/activity.xml").toString(),
            layoutInconnu,
            layoutInconnu.indexOf("fantome"),
        )
        assertNotNull(cibleInconnue)
        assertEquals("inconnu", cibleInconnue!!.refus, "le référentiel ne connaît pas la ressource")
    }

    @Test
    fun `les collisions depuis un xml restent typées (référentiel source de vérité)`() {
        val layout = "<TextView android:text=\"@string/bonjour\"/>"
        val module = module(
            java = mapOf("Main.java" to "package demo;\npublic class Main {}"),
            xml = mapOf(
                "res/values/strings.xml" to """
                    <resources>
                        <string name="bonjour">Salut</string>
                        <string name="au_revoir">Bye</string>
                    </resources>
                """.trimIndent(),
                "res/layout/activity.xml" to layout,
            ),
        )
        val moteur = moteurDe(module)
        val chemin = module.racine.resolve("res/layout/activity.xml").toString()
        val offset = layout.indexOf("bonjour")

        val pris = moteur.conflitsRessourceXml(chemin, layout, offset, "au_revoir")
        assertTrue(pris.any { it.genre == GenreConflit.NOM_PRIS }, "le nom déjà pris du même type collisionne")

        val invalide = moteur.conflitsRessourceXml(chemin, layout, offset, "9fantome")
        assertTrue(invalide.any { it.genre == GenreConflit.IDENTIFIANT_INVALIDE }, "un identifiant invalide collisionne")

        assertTrue(moteur.conflitsRessourceXml(chemin, layout, offset, "salutations").isEmpty(), "un nom libre ne collisionne pas")
    }

    // ------------------------------------------------------------------ //
    // Le service (prepareRename / rename — forme négociée)
    // ------------------------------------------------------------------ //

    @Test
    fun `le service sert prepareRename et rename d un document xml sous la forme negociee`() {
        val layout = "<TextView android:text=\"@string/bonjour\"/>"
        val module = module(
            java = mapOf(
                "Main.java" to """
                    package demo;
                    public class Main {
                        int texte() { return R.string.bonjour; }
                    }
                """.trimIndent(),
            ),
            xml = mapOf(
                "res/values/strings.xml" to "<resources><string name=\"bonjour\">Salut</string></resources>",
                "res/layout/activity.xml" to layout,
            ),
        )
        val moteur = moteurDe(module)
        val cheminLayout = module.racine.resolve("res/layout/activity.xml").toString()
        val uriLayout = "file://$cheminLayout"
        val offset = layout.indexOf("bonjour")

        // Sans annonce documentChanges : la forme héritée `changes`.
        val service = ServiceRenommage(moteur, CapsEspaceDeTravailClient())
        val preparation = service.preparerRenommageXml(cheminLayout, layout, offset)
        assertNotNull(preparation, "prepareRename sert la ressource XML")
        assertEquals("bonjour", preparation!!.placeholder)
        assertEquals("bonjour", layout.substring(preparation.plage.debutOffset, preparation.plage.finOffset))

        val edition = service.renommageXml(uriLayout, cheminLayout, layout, offset, "salutations")
        assertNotNull(edition.changes, "sans annonce : la forme héritée changes")
        assertNull(edition.documentChanges, "jamais les deux formes")
        assertTrue(edition.changes!!.keys.any { it.endsWith("Main.java") }, "le code Java est retouché")
        assertTrue(edition.changes!!.keys.any { it.endsWith("values/strings.xml") }, "la déclaration est retouchée")
        assertTrue(edition.changes!!.containsKey(uriLayout), "le document XML courant voyage sous SON URI")

        // Le document FERMÉ sans ressource renommable : erreur typée.
        val surBlanc = service.preparerRenommageXml(cheminLayout, layout, layout.indexOf("TextView"))
        assertNull(surBlanc, "caret hors ressource : pas de préparation")
        try {
            service.renommageXml(uriLayout, cheminLayout, layout, layout.indexOf("TextView"), "x")
            throw AssertionError("un caret sans ressource doit REJETER (erreur typée)")
        } catch (e: RenommageSansCible) {
            assertNotNull(e.message)
        }
    }
}
