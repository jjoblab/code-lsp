package jo.codeide.lsp.kotlin.session

import java.nio.file.Files
import java.nio.file.Path
import jo.codeide.lsp.kotlin.api.ClasspathKotlin
import jo.codeide.lsp.kotlin.api.CodeDiagnosticKotlin
import jo.codeide.lsp.kotlin.api.EntreeClasspathKotlin
import jo.codeide.lsp.kotlin.api.KindEntreeKotlin
import jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Fiche 016 — CorrectifsTexteTest : les correctifs de texte du cahier
 * (fiches 016 — accès non sûr `?.`/`!!`/entourage, conversions de
 * littéraux fermées par famille) — 13 comportements du cahier
 * docs/cahier-specifications-kotlin/.
 *
 * Le moteur RE-PROUVE le document à la requête : un correctif existe si
 * et seulement si le diagnostic correspondant existe (alignement par
 * construction — les familles dérivent des MÊMES prédicats que la passe
 * typée).
 *
 * Chaque comportement porte le nom exact du test source (identifiant de
 * traçabilité du cahier).
 */
class Fiche016CorrectifsTexteTest {

    /** La racine temporaire de la copie du jar. */
    @TempDir
    lateinit var racine: Path

    /** Le jar kotlin-stdlib du classpath de test (jar physique). */
    private fun jarStdlib(): java.io.File =
        java.io.File(
            kotlin.jvm.internal.Intrinsics::class.java.protectionDomain
                .codeSource.location.toURI(),
        )

    /** Session branchée sur le stdlib (guichet attendu par sondage borné). */
    private fun session(): SessionLangageKotlinDocuments {
        val moteur = ServiceMoteurLangageKotlin.unique as MoteurLangageKotlinNoyau
        val copie = Files.createTempFile(racine, "f016-stdlib", ".jar").toFile()
        copie.writeBytes(jarStdlib().readBytes())
        val classpath = ClasspathKotlin(
            entrees = listOf(EntreeClasspathKotlin(KindEntreeKotlin.JAR, copie.path, "f016:stdlib")),
            racinesSources = emptyList(),
        )
        moteur.definirClasspath(classpath)
        val session = moteur.creerSession() as SessionLangageKotlinDocuments
        session.definirClasspath(classpath)
        attendreCouvert(session)
        return session
    }

    /** Attend (sondage borné) que le guichet serve le snapshot publié. */
    private fun attendreCouvert(session: SessionLangageKotlinDocuments, secondes: Long = 90) {
        val limite = System.currentTimeMillis() + secondes * 1000
        while (System.currentTimeMillis() < limite) {
            if (session.indexSiPret() != null) return
            Thread.sleep(10)
        }
        throw AssertionError("la passe du moteur n'a pas publié (guichet toujours muet)")
    }

    /** Les codes des diagnostics courants du document. */
    private fun codes(session: SessionLangageKotlinDocuments, uri: String): List<jo.codeide.lsp.kotlin.api.DiagnosticKotlin> =
        session.diagnostics(uri)

    @Test
    fun `acces-non-sur---appel-sur,-assertion-non-nulle-et-entourage-multi-ligne`() {
        val session = session()
        val source =
            """
            fun demo(nom: String?) {
              nom.length
            }
            """.trimIndent()
        val uri = "file:///acces-multiligne.kt"
        session.ouvrir(uri, source)
        val correctifs = session.correctifsTexte(uri)
        val titres = correctifs.map { it.titre }
        // Les TROIS formes : l'appel sûr (le point devient ?.), l'assertion
        // non nulle (!! après le récepteur) et l'entourage multi-ligne de
        // l'instruction ENTIERE (l'instruction s'étend sur des lignes).
        assertTrue(titres.any { it.startsWith("Accès sûr") }, "l'appel sûr est proposé : $titres")
        assertTrue(titres.any { it.startsWith("Assertion non nulle") }, "l'assertion est proposée : $titres")
        val entourage = correctifs.first { it.titre.startsWith("Entourer") }
        assertTrue(entourage.texte.startsWith("if (nom != null) {\n"), "l'entourage multi-ligne : ${entourage.texte}")
        assertTrue(entourage.texte.endsWith("\n}"), "l'entourage multi-ligne ferme : ${entourage.texte}")
        session.close()
    }

    @Test
    fun `acces-non-sur---instruction-partageant-sa-ligne,-entourage-en-ligne`() {
        val session = session()
        val source =
            """
            fun demo(nom: String?) {
              val l = nom.length; nom.hashCode()
            }
            """.trimIndent()
        val uri = "file:///acces-enligne.kt"
        session.ouvrir(uri, source)
        val correctifs = session.correctifsTexte(uri)
        val entourage = correctifs.first { it.titre.startsWith("Entourer") }
        // L'instruction PARTAGE sa ligne : l'entourage est EN LIGNE.
        assertTrue(
            entourage.texte.startsWith("if (nom != null) { ") && entourage.texte.endsWith(" }"),
            "l'entourage en ligne : ${entourage.texte}",
        )
        session.close()
    }

    @Test
    fun `acces-non-sur---propriete-locale-entouree,-l'instruction-entiere`() {
        val session = session()
        val source =
            """
            fun demo() {
              val nom: String? = lire()
              nom.length
            }

            fun lire(): String? = null
            """.trimIndent()
        val uri = "file:///acces-locale.kt"
        session.ouvrir(uri, source)
        val correctifs = session.correctifsTexte(uri)
        val entourage = correctifs.first { it.titre.startsWith("Entourer") }
        // La propriété LOCALE entourée : l'INSTRUCTION ENTIÈRE (nom.length)
        // est remplacée — jamais la propriété seule.
        assertTrue(entourage.texte.contains("nom.length"), "l'instruction entière : ${entourage.texte}")
        assertTrue(!entourage.texte.contains("val nom"), "jamais la propriété : ${entourage.texte}")
        session.close()
    }

    @Test
    fun `acces-non-sur---premier-niveau-sans-bloc,-pas-d'entourage-(D3)`() {
        val session = session()
        val source =
            """
            val nom: String? = null

            val longueur = nom.length
            """.trimIndent()
        val uri = "file:///acces-premierniveau.kt"
        session.ouvrir(uri, source)
        val correctifs = session.correctifsTexte(uri)
        val titres = correctifs.map { it.titre }
        // Le PREMIER NIVEAU sans bloc : pas d'entourage (D3) — l'appel sûr
        // et l'assertion restent proposés.
        assertTrue(titres.none { it.startsWith("Entourer") }, "pas d'entourage au premier niveau : $titres")
        assertTrue(titres.any { it.startsWith("Accès sûr") }, "l'appel sûr reste proposé : $titres")
        session.close()
    }

    @Test
    fun `acces-non-sur---segment-imbrique,-le-point-du-segment-le-plus-profond`() {
        val session = session()
        val source =
            """
            fun demo(adresse: String?) {
              val s = adresse.length.toString()
            }
            """.trimIndent()
        val uri = "file:///acces-imbrique.kt"
        session.ouvrir(uri, source)
        val correctifs = session.correctifsTexte(uri)
        val appelSur = correctifs.first { it.titre.startsWith("Accès sûr") }
        // Le POINT du segment le plus profond porté par le récepteur :
        // le point ENTRE `adresse` et `length` devient `?.`.
        val point = source.indexOf("adresse.length") + "adresse".length
        assertEquals(point, appelSur.plage.debut, "le point après le récepteur : ${appelSur.plage}")
        assertEquals("?.", appelSur.texte)
        session.close()
    }

    @Test
    fun `acces-sur-ou-garde-de-nullite---aucun-correctif-(D3)`() {
        val session = session()
        val source =
            """
            fun demo(nom: String?) {
              val a = nom?.length
              if (nom != null) {
                nom.hashCode()
              }
            }
            """.trimIndent()
        val uri = "file:///acces-garde.kt"
        session.ouvrir(uri, source)
        // L'accès SÛR et la GARDE de nullité : la passe n'émet pas, le
        // correctif non plus (D3 — liste vide honnête).
        assertTrue(session.correctifsTexte(uri).isEmpty(), "aucun correctif sur accès sûr ou gardé")
        session.close()
    }

    @Test
    fun `diagnostic-perime---le-moteur-re-prouve,-rend-vide-(honnête)`() {
        val session = session()
        val source =
            """
            fun demo(nom: String?) {
              nom.length
            }
            """.trimIndent()
        val uri = "file:///perime.kt"
        session.ouvrir(uri, source)
        // Le document ÉVOLUE : l'accès devient sûr — le moteur re-prouve,
        // le correctif de l'ANCIEN diagnostic ne revient pas (réponse
        // vide honnête).
        session.modifier(
            uri,
            """
            fun demo(nom: String?) {
              nom?.length
            }
            """.trimIndent(),
        )
        assertTrue(session.correctifsTexte(uri).isEmpty(), "le moteur re-prouve : vide honnête")
        session.close()
    }

    @Test
    fun `litteral-chaine-vers-Int---changement-de-type-et-conversion-toInt`() {
        val session = session()
        val source = """val age: Int = "5"""" + "\n"
        val uri = "file:///conversion-int.kt"
        session.ouvrir(uri, source)
        val correctifs = session.correctifsTexte(uri)
        val titres = correctifs.map { it.titre }
        // Le changement de TYPE (Int → String) ET la CONVERSION (.toInt()
        // après le littéral).
        assertTrue(titres.any { it.startsWith("Changer le type") }, "le changement de type : $titres")
        val conversion = correctifs.first { it.titre.startsWith("Convertir") }
        assertEquals(".toInt()", conversion.texte, "la conversion toInt : ${conversion.texte}")
        assertEquals(source.indexOf("\"5\"") + 3, conversion.plage.debut, "après le littéral")
        session.close()
    }

    @Test
    fun `cible-non-primitive---changement-de-type-seul-(D3)`() {
        val session = session()
        val source =
            """
            class Boite

            val b: Boite = "x"
            """.trimIndent()
        val uri = "file:///conversion-nonprimitif.kt"
        session.ouvrir(uri, source)
        val correctifs = session.correctifsTexte(uri)
        val titres = correctifs.map { it.titre }
        // La cible NON PRIMITIVE : le changement de type SEUL (la matrice
        // de conversion ne couvre pas Boite, D3).
        assertTrue(titres.any { it.startsWith("Changer le type") }, "le changement de type : $titres")
        assertTrue(titres.none { it.startsWith("Convertir") }, "aucune conversion vers Boite : $titres")
        session.close()
    }

    @Test
    fun `matrice-de-conversion---fermee-par-famille-(D3)`() {
        val session = session()
        val source = """val c: Boolean = "x"""" + "\n"
        val uri = "file:///conversion-matrice.kt"
        session.ouvrir(uri, source)
        val correctifs = session.correctifsTexte(uri)
        // La matrice est FERMÉE : String → Boolean n'y vit pas — seul le
        // changement de type est proposé (jamais un toBoolean inventé).
        assertTrue(
            correctifs.none { it.titre.startsWith("Convertir") },
            "la matrice fermée n'invente rien : ${correctifs.map { it.titre }}",
        )
        assertTrue(correctifs.any { it.titre.startsWith("Changer le type") })
        session.close()
    }

    @Test
    fun `type-nullable-ou-conversion-legale---aucun-correctif-(D3)`() {
        val session = session()
        val source =
            """
            fun demo() {
              val a: Int? = 5
              val b: Double = 7
            }
            """.trimIndent()
        val uri = "file:///conversion-legale.kt"
        session.ouvrir(uri, source)
        // Le type NULLABLE et la conversion numérique LÉGITIME : la passe
        // n'émet pas, le correctif non plus.
        assertTrue(session.correctifsTexte(uri).isEmpty(), "aucun correctif sur nullable ou légal")
        session.close()
    }

    @Test
    fun `alignement---un-correctif-si-et-seulement-si-le-diagnostic-correspondant`() {
        val session = session()
        val source =
            """
            fun demo(nom: String?) {
              nom.length
              nom.hashCode()
            }
            """.trimIndent()
        val uri = "file:///alignement.kt"
        session.ouvrir(uri, source)
        val diags = codes(session, uri).filter { it.code == CodeDiagnosticKotlin.ACCES_NON_SUR }
        val entourages = session.correctifsTexte(uri).filter { it.titre.startsWith("Entourer") }
        // ALIGNEMENT : DEUX diagnostics d'accès non sûr → DEUX
        // entourages (un correctif si et seulement si le diagnostic).
        assertEquals(2, diags.size, "deux diagnostics : $diags")
        assertEquals(2, entourages.size, "deux entourages alignés : $entourages")
        session.close()
    }

    @Test
    fun `document-inconnu-ou-ferme---liste-vide-honnete`() {
        val session = session()
        // Document INCONNU : liste vide honnête.
        assertTrue(session.correctifsTexte("file:///inconnu.kt").isEmpty(), "document inconnu : vide")
        val uri = "file:///ferme.kt"
        session.ouvrir(uri, "fun demo() {}")
        session.close()
        // Session FERMÉE : liste vide honnête.
        assertTrue(session.correctifsTexte(uri).isEmpty(), "session fermée : vide")
    }
}
