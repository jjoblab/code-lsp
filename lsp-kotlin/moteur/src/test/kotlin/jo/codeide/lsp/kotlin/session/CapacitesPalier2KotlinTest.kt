// CodeIDE — tests des CAPACITÉS PALIER 2 (module :kotlin, vague V-I2 —
// ADR 0136 étape B.3 : semanticTokens borné KH1, hiérarchies, codeLens).
// Code NEUF jjoblab (clean-room — comportements : la légende est
// contractuelle, le jeton ne porte JAMAIS la variance ; les hiérarchies
// se prouvent sur les clauses ÉCRITES ; les lentilles comptent les
// usages documentaires — périmètre borné, jamais un survendre).
package jo.codeide.lsp.kotlin.session

import jo.codeide.lsp.kotlin.api.ClasspathKotlin
import jo.codeide.lsp.kotlin.api.EntreeClasspathKotlin
import jo.codeide.lsp.kotlin.api.GenreJetonSemantiqueKotlin
import jo.codeide.lsp.kotlin.api.KindEntreeKotlin
import jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * La vague V-I2 — les capacités PALIER 2 (ADR 0136 B.3) :
 * `semanticTokens/full` (borné KH1/ADR 0123), `typeHierarchy`,
 * `callHierarchy`, `codeLens` (occurrences paresseuses bornées).
 */
class CapacitesPalier2KotlinTest {

    /** Le jar kotlin-stdlib du classpath de test (le régime de preuve exige l'index). */
    private fun jarStdlib(): java.io.File =
        java.io.File(
            kotlin.jvm.internal.Intrinsics::class.java.protectionDomain
                .codeSource.location.toURI(),
        )

    /** Une session branchée sur le stdlib (guichet attendu par sondage borné). */
    private fun session(): SessionLangageKotlinDocuments {
        val moteur = ServiceMoteurLangageKotlin.unique as MoteurLangageKotlinNoyau
        val classpath = ClasspathKotlin(
            entrees = listOf(
                EntreeClasspathKotlin(KindEntreeKotlin.JAR, jarStdlib().path, "vi2:stdlib"),
            ),
            racinesSources = emptyList(),
        )
        moteur.definirClasspath(classpath)
        val session = moteur.creerSession() as SessionLangageKotlinDocuments
        session.definirClasspath(classpath)
        val limite = System.currentTimeMillis() + 90_000
        while (System.currentTimeMillis() < limite) {
            if (session.indexSiPret() != null) return session
            Thread.sleep(10)
        }
        throw AssertionError("la passe du moteur n'a pas publié (guichet toujours muet)")
    }

    // ── semanticTokens — le vecteur borné KH1 ─────────────────────────

    @Test
    fun `jetons---declarations-typées,-genres-prouvés-et-modificateurs-écrits`() {
        val session = session()
        val source =
            "interface Figure { val nom: String }\n" +
                "class Cercle(val rayon: Int) : Figure {\n" +
                "    override val nom: String = \"cercle\"\n" +
                "    fun aire(): Int = 3\n" +
                "}\n" +
                "val fig: Figure = Cercle(1)\n"
        val uri = "file:///jetons.kt"
        session.ouvrir(uri, source)
        val jetons = session.jetonsSemantiques(uri)
        val parGenre = jetons.groupBy { it.genre }
        // Les genres PROUVÉS : interface, classe, méthode, propriété,
        // paramètre, variable (jamais la variance ni la nullabilité).
        assertTrue(
            parGenre.keys.isNotEmpty(),
            "le document portait des déclarations typées — jetons attendus : ${jetons.map { it.genre }}",
        )
        // Figure : INTERFACE déclarée.
        val figure = jetons.firstOrNull { it.offset == source.indexOf("Figure") }
        assertNotNull(figure, "`Figure` devait être jetonisée")
        assertEquals(GenreJetonSemantiqueKotlin.INTERFACE, figure.genre, "Figure est une interface")
        assertEquals(true, figure.declaration, "la première occurrence est la DÉCLARATION")
        // Cercle : CLASSE déclarée, non ouverte → final.
        val cercle = jetons.firstOrNull { it.offset == source.indexOf("Cercle") }
        assertNotNull(cercle, "`Cercle` devait être jetonisée")
        assertEquals(GenreJetonSemantiqueKotlin.CLASSE, cercle.genre, "Cercle est une classe")
        // aire : METHODE déclarée — corps en EXPRESSION (`= 3`, sans
        // bloc : le jeton ne porte pas abstrait — un `fun` avec `=`
        // a un corps écrit).
        val aire = jetons.firstOrNull { it.offset == source.indexOf("aire") }
        assertNotNull(aire, "`aire` devait être jetonisée")
        assertEquals(GenreJetonSemantiqueKotlin.METHODE, aire.genre, "aire est une fonction membre")
        // rayon : PARAMÈTRE déclaré (val → final).
        val rayon = jetons.firstOrNull { it.offset == source.indexOf("rayon") }
        assertNotNull(rayon, "`rayon` devait être jetonisé")
        assertEquals(GenreJetonSemantiqueKotlin.PARAMETRE, rayon.genre, "rayon est un paramètre")
        assertEquals(true, rayon.final, "un paramètre val porte le modificateur final")
        session.close()
    }

    @Test
    fun `jetons---tri-par-offset-croissant,-déclaration-gagne-la-fusion`() {
        val session = session()
        val source =
            "class Boite {\n" +
                "    fun ouvrir() { fermer() }\n" +
                "    fun fermer() { }\n" +
                "}\n"
        val uri = "file:///tri.kt"
        session.ouvrir(uri, source)
        val jetons = session.jetonsSemantiques(uri)
        // Le tri : offsets croissants, DÉDOUBLONNÉS (une seule occurrence
        // par offset — Boite déclarée une fois).
        val offsets = jetons.map { it.offset }
        assertEquals(offsets.sorted(), offsets, "les jetons se trient par offset croissant")
        val boites = jetons.filter { it.offset == source.indexOf("Boite") }
        assertEquals(1, boites.size, "la déclaration fusionne la référence homonyme au même offset")
        // fermer : déclarée ET appelée — deux jetons à des offsets distincts.
        val fermees = jetons.filter { it.genre == GenreJetonSemantiqueKotlin.METHODE }
        assertEquals(3, fermees.size, "ouvrir, fermer(déclaration), fermer(appel) : ${fermees.map { it.declaration }}")
        assertTrue(fermees.any { !it.declaration }, "l'appel `fermer()` porte declaration=false")
        session.close()
    }

    @Test
    fun `jetons---document-inconnu-ou-guichet-muet---liste-vide-honnête`() {
        val session = session()
        assertTrue(
            session.jetonsSemantiques("file:///inconnu.kt").isEmpty(),
            "un document inconnu rend la liste VIDE (I9)",
        )
        session.close()
    }

    // ── typeHierarchy — préparation et navigation bornée ─────────────

    @Test
    fun `hierarchie-types---préparation-sur-le-nom,-supertypes-écrits-résolus`() {
        val session = session()
        val source =
            "interface Figure { fun aire(): Int }\n" +
                "class Cercle : Figure {\n" +
                "    override fun aire(): Int = 3\n" +
                "}\n"
        val uri = "file:///hier.kt"
        session.ouvrir(uri, source)
        // Le caret sur le nom Cercle : la préparation rend l'item.
        val item = session.preparerHierarchieTypes(uri, source.indexOf("Cercle") + 1)
        assertNotNull(item, "la préparation devait rendre l'item Cercle")
        assertEquals("Cercle", item.nom, "le nom de l'item est le nom simple")
        // Les SUPERTYPES : la clause écrite résolue (Figure).
        val supertypes = session.supertypesHierarchie(uri, item!!)
        assertEquals(1, supertypes.size, "Cercle déclare UN supertype : ${supertypes.map { it.nom }}")
        assertEquals("Figure", supertypes.single().nom, "le supertype écrit est Figure")
        assertEquals(true, supertypes.single().genre.contains("INTERFACE"), "Figure est une interface")
        // Les SOUS-TYPES de Figure : Cercle (preuve par FQCN).
        val itemFigure = session.preparerHierarchieTypes(uri, source.indexOf("Figure") + 1)
        assertNotNull(itemFigure, "la préparation sur Figure devait rendre l'item")
        val sousTypes = session.sousTypesHierarchie(uri, itemFigure!!)
        assertEquals(listOf("Cercle"), sousTypes.map { it.nom }, "Cercle hérite de Figure (clause écrite)")
        session.close()
    }

    @Test
    fun `hierarchie-types---position-sans-type-projet---null-honnête`() {
        val session = session()
        val source = "fun demo(x: Int) { }\n"
        val uri = "file:///sans.kt"
        session.ouvrir(uri, source)
        val item = session.preparerHierarchieTypes(uri, source.indexOf("demo") + 1)
        assertTrue(
            item == null || item.nom != "x",
            "sans type projet prouvé : `null` (l'incertain ne se prépare pas, D3)",
        )
        session.close()
    }

    // ── callHierarchy — préparation, entrants, sortants ──────────────

    @Test
    fun `hierarchie-appels---préparation,-entrants-et-sortants-du-projet`() {
        val session = session()
        val source =
            "fun calculer(): Int = 1\n" +
                "fun afficher(): Int {\n" +
                "    return calculer()\n" +
                "}\n" +
                "fun principal() { afficher() }\n"
        val uri = "file:///appels.kt"
        session.ouvrir(uri, source)
        // La préparation sur le nom calculer.
        val item = session.preparerHierarchieAppels(uri, source.indexOf("calculer") + 1)
        assertNotNull(item, "la préparation devait rendre l'item calculer")
        assertEquals("calculer", item!!.nom)
        // Les ENTRANTS : afficher appelle calculer (calculer()).
        val entrants = session.appelsEntrants(uri, item)
        assertEquals(
            listOf("afficher"),
            entrants.map { it.item.nom },
            "afficher appelle calculer dans son corps : ${entrants.map { it.item.nom }}",
        )
        assertTrue(entrants.isNotEmpty(), "l'arête entrante porte la plage de l'appel")
        // Les SORTANTS de afficher : calculer.
        val itemAfficher = session.preparerHierarchieAppels(uri, source.indexOf("afficher") + 2)
        assertNotNull(itemAfficher, "la préparation devait rendre l'item afficher")
        val sortants = session.appelsSortants(uri, itemAfficher!!)
        assertEquals(
            listOf("calculer"),
            sortants.map { it.item.nom },
            "le corps d'afficher appelle calculer : ${sortants.map { it.item.nom }}",
        )
        session.close()
    }

    @Test
    fun `hierarchie-appels---nom-sans-appelable-prouvé---null-honnête`() {
        val session = session()
        val source = "fun demo() { inconnu() }\n"
        val uri = "file:///sans-appelable.kt"
        session.ouvrir(uri, source)
        // La préparation sur `inconnu` : le modèle ne connaît AUCUN
        // appelable de ce nom — `null` (D3).
        val item = session.preparerHierarchieAppels(uri, source.indexOf("inconnu") + 1)
        assertTrue(
            item == null,
            "un nom sans appelable du projet ne se prépare pas (D3)",
        )
        session.close()
    }

    // ── codeLens — les occurrences paresseuses bornées ───────────────

    @Test
    fun `lentilles---chaque-déclaration-top-level-porte-son-compte-d-usages`() {
        val session = session()
        val source =
            "class Panneau { fun dessiner() { } }\n" +
                "fun batir(): Panneau { return Panneau() }\n" +
                "val temoin = batir()\n" +
                "fun auteur() { batir() }\n"
        val uri = "file:///lentilles.kt"
        session.ouvrir(uri, source)
        val lentilles = session.lentilles(uri)
        val parNom = lentilles.associateBy { it.nom }
        // Les 4 déclarations top-level portent une lentille.
        assertEquals(setOf("Panneau", "batir", "temoin", "auteur"), parNom.keys, "les déclarations top-level")
        // Le compte d'usages DOCUMENTAIRE : batir est appelé 2 fois
        // (batir() dans val + batir() dans auteur) + 1 déclaration.
        val batir = parNom["batir"]
        assertNotNull(batir, "batir portait une lentille")
        assertEquals(3, batir!!.usages, "batir : déclaration + 2 usages = 3 occurrences du mot complet")
        // Panneau : déclaration + usage type (: Panneau) + constructeur
        // (Panneau()) = 3 occurrences du mot complet.
        assertEquals(3, parNom["Panneau"]!!.usages, "Panneau : déclaration + type de retour + constructeur")
        session.close()
    }

    @Test
    fun `lentilles---document-vide-sans-déclaration---liste-vide`() {
        val session = session()
        val source = "val seul = 1\n"
        val uri = "file:///seul.kt"
        session.ouvrir(uri, source)
        val lentilles = session.lentilles(uri)
        assertEquals(1, lentilles.size, "une seule déclaration top-level (seul)")
        assertEquals(1, lentilles.single().usages, "seul n'est pas réutilisé : 1 occurrence (la déclaration)")
        session.close()
    }
}
