package jo.codeide.lsp.kotlin.parse.kt

import jo.codeide.lsp.kotlin.parse.ParseurKotlin
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Fiche 013 — FacadeKtTest : tests de la façade `Kt*` au-dessus du harnais
 * (l'autre moitié du noyau parse de l'étage 1). Le contrat de la fiche :
 * « les accesseurs portent le vocabulaire du compilateur et RESTENT
 * conservateurs (D3) — introuvable = null/vide ».
 *
 * Les fixtures sont calibrées sur le comportement RÉEL de la grammaire
 * vendorée (sondé avant écriture, miroir de la méthode de la fiche 049) :
 * chaque comportement porte le nom exact du test source (identifiant de
 * traçabilité du cahier).
 */
class Fiche013FacadeKtTest {

    private val parseur = ParseurKotlin()

    @Test
    fun `directive-package---nom-qualifie`() {
        val fichier = parseur.arbre("package demo.parse\n\nval x = 1\n")
        assertEquals("demo.parse", fichier.directivePackage()?.nomQualifie())
    }

    @Test
    fun `fichier-sans-package---nom-qualifie-null-(directive-vide-presente)`() {
        val fichier = parseur.arbre("val x = 1\n")
        val directive = fichier.directivePackage()
        assertNotNull(directive, "la directive vide est présente dans l'arbre (plage nulle)")
        assertNull(directive.nomQualifie(), "sans package écrit, le nom qualifié est null — pas une exception")
    }

    @Test
    fun `imports-simple-alias-et-wildcard`() {
        val source = (
            "import java.util.ArrayList\n" +
                "import kotlin.math.PI as constante\n" +
                "import java.io.*\n"
            )
        val imports = parseur.arbre(source).imports()
        assertEquals(3, imports.size, "trois directives d'import en ordre du document")

        assertEquals("java.util.ArrayList", imports[0].nomQualifie())
        assertNull(imports[0].alias(), "un import simple n'a pas d'alias")
        assertFalse(imports[0].estJoker(), "un import simple n'est pas un joker")

        assertEquals("kotlin.math.PI", imports[1].nomQualifie())
        assertEquals("constante", imports[1].alias())
        assertFalse(imports[1].estJoker())

        assertEquals("java.io", imports[2].nomQualifie(), "le joker rend la partie qualifiée écrite")
        assertNull(imports[2].alias())
        assertTrue(imports[2].estJoker(), "le jeton * marque le joker")
    }

    @Test
    fun `alias-sans-identifiant-derriere-as---null-conservateur`() {
        val fichier = parseur.arbre("import java.util.ArrayList as\n")
        // La directive est aliasée mais tronquée avant l'identifiant : la
        // réponse est null, AUCUNE exception n'est levée (D3).
        assertNull(fichier.imports().single().alias())
    }

    @Test
    fun `declarations-de-premier-niveau---noms-et-genres`() {
        val source = (
            "class Premiere\n" +
                "interface Ile\n" +
                "object Unique\n" +
                "typealias Alias = Int\n" +
                "fun racine(): Int = 0\n" +
                "val compteur = 1\n"
            )
        val declarations = parseur.arbre(source).declarations()
        assertEquals(
            listOf("Premiere", "Ile", "Unique", "Alias", "racine", "compteur"),
            declarations.mapNotNull { it.nom() },
            "les noms se lisent en ordre du document",
        )
        assertTrue(declarations[0] is KtClasse)
        assertEquals(KtGenreClasse.CLASSE, (declarations[0] as KtClasse).genreClasse)
        assertTrue(declarations[1] is KtClasse)
        assertEquals(KtGenreClasse.INTERFACE, (declarations[1] as KtClasse).genreClasse)
        assertTrue(declarations[2] is KtObjet)
        assertTrue(declarations[3] is KtTypealias)
        assertTrue(declarations[4] is KtFonction)
        assertTrue(declarations[5] is KtPropriete)
    }

    @Test
    fun `classe---membres,-interface,-enum`() {
        val source = (
            "class Serviteur {\n" +
                "    val nom: String = \"\"\n" +
                "    fun saluer(): String = nom\n" +
                "}\n" +
                "interface Service {\n" +
                "    fun executer()\n" +
                "}\n" +
                "enum class Etat { ACTIF, VEILLE }\n"
            )
        val declarations = parseur.arbre(source).declarations()
        assertEquals(3, declarations.size)

        val classe = declarations[0] as KtClasse
        assertEquals(KtGenreClasse.CLASSE, classe.genreClasse)
        assertEquals(listOf("nom", "saluer"), classe.membres().mapNotNull { it.nom() })
        assertTrue(classe.membres()[0] is KtPropriete)
        assertTrue(classe.membres()[1] is KtFonction)

        val iface = declarations[1] as KtClasse
        assertEquals(KtGenreClasse.INTERFACE, iface.genreClasse)
        assertEquals(listOf("executer"), iface.membres().mapNotNull { it.nom() })

        val enum = declarations[2] as KtClasse
        assertEquals(KtGenreClasse.ENUM, enum.genreClasse)
        assertEquals(
            listOf("ACTIF", "VEILLE"),
            enum.membres().filterIsInstance<KtEntreeEnum>().mapNotNull { it.nom() },
            "les entrées d'enum sont des membres",
        )
    }

    @Test
    fun `fonction---parametres-nommes`() {
        val fichier = parseur.arbre("fun additionne(a: Int, b: Int = 1): Int = a + b\n")
        val fonction = fichier.declarations().single() as KtFonction
        assertEquals(listOf("a", "b"), fonction.parametres().mapNotNull { it.nom() })
    }

    @Test
    fun `propriete-avec-objet-anonyme---nom-de-propriete-conservateur`() {
        val source = "val pont: Runnable = object : Runnable {\n    override fun run() {}\n}\n"
        val propriete = parseur.arbre(source).declarations().single() as KtPropriete
        assertEquals("pont", propriete.nom(), "le nom reste celui de la propriété, pas celui de l'objet anonyme")
    }

    @Test
    fun `declaration-tronquee-sans-identifiant---nom-null-sans-lever`() {
        val fichier = parseur.arbre("class\n")
        val declarations = fichier.declarations()
        assertEquals(1, declarations.size, "la déclaration tronquée produit quand même un nœud")
        assertNull(declarations[0].nom(), "sans identifiant écrit, le nom est null — AUCUNE exception")
    }

    @Test
    fun `navigation-generique---enfants-par-type`() {
        val source = "class A {\n    fun f() {}\n    fun g() {}\n    val x = 1\n}\n"
        val fichier = parseur.arbre(source)

        val classes = fichier.enfants("CLASS")
        assertEquals(1, classes.size)
        assertEquals("CLASS", classes[0].genre)

        val corps = classes[0].enfants("CLASS_BODY")
        assertEquals(1, corps.size)

        assertEquals(2, corps[0].enfants("FUN").size, "deux fonctions par navigation générique")
        assertEquals(1, corps[0].enfants("PROPERTY").size, "une propriété par navigation générique")
        assertEquals(0, fichier.enfants("FUN").size, "rien de tout cela au premier niveau")
    }

    @Test
    fun `annotations---noms-ecrits-et-ordre-du-document`() {
        val source = "@Alpha\n@kotlin.Beta\nclass Annotee\n"
        val classe = parseur.arbre(source).declarations().single() as KtClasse
        assertEquals(
            listOf("Alpha", "kotlin.Beta"),
            classe.annotations().mapNotNull { it.nom() },
            "les noms sont rendus TELS QU'ÉCRITS, en ordre du document",
        )
    }

    @Test
    fun `annotations---arguments-nommes-avec-plages-de-valeurs`() {
        val source = "@Deprecated(message = \"obsolète\", level = DeprecationLevel.WARNING)\nfun ancien() = 1\n"
        val fonction = parseur.arbre(source).declarations().single() as KtFonction
        val annotation = fonction.annotations().single()
        assertEquals("Deprecated", annotation.nom())

        val arguments = annotation.arguments()
        assertEquals(listOf("message", "level"), arguments.mapNotNull { it.nom() }, "arguments nommés en ordre d'écriture")

        // Plages de valeurs : offsets EXACTS de l'expression écrite après « = ».
        val debutMessage = source.indexOf("\"obsolète\"")
        val plageMessage = arguments[0].plageValeur()
        assertNotNull(plageMessage)
        assertEquals(debutMessage, plageMessage.debut)
        assertEquals(debutMessage + "\"obsolète\"".length, plageMessage.fin)

        val debutNiveau = source.indexOf("DeprecationLevel.WARNING")
        val plageNiveau = arguments[1].plageValeur()
        assertNotNull(plageNiveau)
        assertEquals(debutNiveau, plageNiveau.debut)
        assertEquals(debutNiveau + "DeprecationLevel.WARNING".length, plageNiveau.fin)
    }

    @Test
    fun `annotations---fonction-sans-annotations-et-fonction-avec-corps`() {
        val source = "fun nue(): Int = 0\n\nfun avecCorps() {\n    val interne = 1\n}\n"
        val fonctions = parseur.arbre(source).declarations().filterIsInstance<KtFonction>()
        assertEquals(2, fonctions.size)
        assertTrue(fonctions[0].annotations().isEmpty(), "une fonction sans annotations rend une liste vide")
        assertFalse(fonctions[0].possedeCorpsBloc(), "corps d'expression : pas de bloc")
        assertTrue(fonctions[1].possedeCorpsBloc(), "corps de bloc présent")
    }
}
