// CodeIDE — CHANTIER J (parité Android Studio « Go to Class/Symbol
// incluant bibliothèques », 14/11/2026) : workspace/symbol propose AUSSI
// les classes du CLASSPATH (l'index nourri) — sans location (le jar n'a
// pas d'offsets), le FQCN en data — et workspaceSymbol/resolve remplit la
// location quand la source vit dans un document OUVERT.
package jo.codeide.lsp.kotlin.session

import jo.codeide.lsp.kotlin.api.ClasspathKotlin
import jo.codeide.lsp.kotlin.api.EntreeClasspathKotlin
import jo.codeide.lsp.kotlin.api.KindEntreeKotlin
import jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Le chantier J : les symboles BIBLIOTHÈQUE dans workspace/symbol.
 *
 * **Les cas verrouillés** :
 *  - un préfixe de classe du stdlib (ex. `StringBu`) propose la classe
 *    `StringBuilder` avec son FQCN (`kotlin.text.StringBuilder`) ;
 *  - requête VIDE : les symboles bibliothèque ne débordent PAS (borne
 *    anti-noyade) ;
 *  - pas de DOUBLON : un nom du projet ne reçoit pas l'ombre
 *    bibliothèque ;
 *  - un symbole de PROJET (document ouvert) garde sa forme complète
 *    (uri + plageNom).
 */
class ChantierJSymbolesBibliothequeTest {

    /** Le jar kotlin-stdlib du RUNTIME (ancre de classe, miroir banc). */
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
                EntreeClasspathKotlin(KindEntreeKotlin.JAR, jarStdlib().path, "cj:stdlib"),
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
        return session
    }

    @Test
    fun `chantierJ---le-prefixe-propose-les-classes-du-classpath`() {
        val session = session()
        // NOTE : kotlin.text.StringBuilder n'est PAS un class-file du jar
        // (expect/actual mappé sur java.lang) — la preuve cible des
        // classes RÉELLES du stdlib (kotlin.collections.Abstract*).
        val symboles = session.symbolesEspace("AbstractCol")
        val bibli = symboles.filter { it.fqcn != null }
        assertTrue(
            bibli.isNotEmpty(),
            "`AbstractCol` devait proposer les classes du classpath ; rendu : ${symboles.map { it.nom }}",
        )
        val ac = bibli.firstOrNull { it.nom == "AbstractCollection" }
        assertNotNull(ac, "AbstractCollection devait être proposé ; rendu : ${bibli.map { it.nom }}")
        assertEquals("kotlin.collections.AbstractCollection", ac.fqcn)
        assertEquals(null, ac.plageNom, "un symbole bibliothèque n'a PAS de plage (jar sans offsets)")
        session.close()
    }

    @Test
    fun `chantierJ---requete-vide-ne-debordent-pas-la-bibliotheque`() {
        val session = session()
        val symboles = session.symbolesEspace("")
        assertTrue(
            symboles.none { it.fqcn != null },
            "une requête VIDE ne doit proposer AUCUN symbole bibliothèque (anti-noyade) ; rendus : " +
                symboles.filter { it.fqcn != null }.map { it.fqcn }.take(5),
        )
        session.close()
    }

    @Test
    fun `chantierJ---pas-de-doublon-projet-vers-bibliotheque`() {
        val session = session()
        // Le document OUVERT déclare `Ecran` ; la requête `Ecr` ne doit
        // rendre le symbole bibliothèque QUE si le projet n'a pas déjà
        // le même nom simple.
        val uri = "file:///cj1.kt"
        session.ouvrir(uri, "package demo\n\nclass Ecran {\n    fun afficher() = 1\n}\n")
        val symboles = session.symbolesEspace("Ecran")
        assertTrue(symboles.any { it.nom == "Ecran" && it.fqcn == null }, "le symbole du PROJET est rendu")
        assertTrue(
            symboles.none { it.fqcn != null && it.nom == "Ecran" },
            "un nom du projet ne reçoit PAS l'ombre bibliothèque ; rendu : ${symboles.map { it.nom to it.fqcn }}",
        )
        session.close()
    }

    @Test
    fun `chantierJ---le-symbole-projet-garde-uri-et-plage`() {
        val session = session()
        val uri = "file:///cj2.kt"
        val source = "package demo\n\nclass NoteRepository {\n    fun charger() = 1\n}\n"
        session.ouvrir(uri, source)
        val symboles = session.symbolesEspace("NoteRepository")
        val note = symboles.firstOrNull { it.nom == "NoteRepository" }
        assertNotNull(note, "le symbole du document ouvert est rendu")
        assertEquals(uri, note.uri)
        assertEquals(null, note.fqcn, "un symbole de projet n'a PAS de fqcn")
        assertEquals("NoteRepository", source.substring(note.plageNom!!.debut, note.plageNom!!.fin))
        session.close()
    }
}
