package jo.codeide.lsp.kotlin.session

import java.nio.file.Files
import java.nio.file.Path
import jo.codeide.lsp.kotlin.api.ClasspathKotlin
import jo.codeide.lsp.kotlin.api.GenreSurlignageKotlin
import jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Fiche 048 — SessionLangageKotlinOccurrencesTest : le contrat de session
 * OCCURRENCES (S2, plan de stabilisation LSP §3.2 : `references`,
 * `documentHighlight`, rename borné miroir Java) — 21 comportements du
 * cahier docs/cahier-specifications-kotlin/.
 *
 * Le montage éprouve le CHEMIN DE PRODUCTION D6 (moteur nourricier réel
 * par le SPI + session branchée + guichet attendu par sondage borné) : un
 * projet multi-fichiers sur DISQUE (racines sources du scan) porte les
 * usages des fichiers FERMÉS — la preuve de liaison multi-fichiers s'y
 * joue pour de vrai.
 *
 * Le cœur éprouvé : le CONSERVATISME. Chaque référence NON prouvée
 * (homonyme d'un autre paquet sans import, locale ombrageante, sélecteur
 * sans récepteur démontrable) reste HORS liste — jamais de faux usage,
 * jamais de faux renommage.
 *
 * Chaque comportement porte le nom exact du test source (identifiant de
 * traçabilité du cahier).
 */
@Suppress("LargeClass")
class Fiche048SessionLangageKotlinOccurrencesTest {

    /** La racine temporaire des sources (projet multi-fichiers du scan). */
    @TempDir
    lateinit var dossier: Path

    /**
     * Session branchée sur un projet multi-fichiers : chaque fichier est
     * écrit sous la racine AVANT la pose du classpath (la passe du scan
     * les voit), le moteur nourricier est RÉEL (chemin de production D6).
     */
    private fun sessionAvecRacines(vararg fichiers: Pair<String, String>): SessionLangageKotlinDocuments {
        val racine = Files.createDirectories(dossier.resolve("src"))
        for ((chemin, contenu) in fichiers) {
            val fichier = racine.resolve(chemin)
            Files.createDirectories(fichier.parent)
            Files.writeString(fichier, contenu)
        }
        val moteur = ServiceMoteurLangageKotlin.unique as MoteurLangageKotlinNoyau
        val classpath = ClasspathKotlin(
            entrees = emptyList(),
            racinesSources = listOf(racine.toAbsolutePath().toString()),
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

    /** Décalage du PREMIER caractère de [occurrence] dans [texte]. */
    private fun decalageDe(texte: String, occurrence: String, numero: Int = 0): Int {
        var index = -1
        repeat(numero + 1) {
            index = texte.indexOf(occurrence, index + 1)
        }
        return index
    }

    /**
     * L'URI DISQUE RÉELLE d'un fichier du projet — la convention
     * `fixture://` des autres tests crée une COPIE fantôme au balayage
     * (le document ouvert et le fichier du scan portent des URI
     * DISTINCTES) ; l'URI réelle garantit l'exclusion du balayage, miroir
     * de la production (didOpen et scan partagent le même file:// URI).
     */
    private fun uriReelle(chemin: String): String =
        dossier.resolve("src/$chemin").toUri().toString()

    // ── Références : locale file-local ────────────────────────────────

    @Test
    fun `references-locale---usages-du-fichier-déclarant-seul,-ombrage-exclu`() {
        val texte =
            """
            fun principale() {
                val compteur = 0
                val somme = compteur + compteur
            }

            fun autre() {
                val compteur = 9
                val copie = compteur
            }
            """.trimIndent()
        val session = sessionAvecRacines("Main.kt" to texte)
        session.ouvrir("fixture://Main.kt", texte)
        // Sur la DÉCLARATION de compteur (1re occurrence) — usages dans
        // principale() SEULEMENT (la locale de autre() est un homonyme).
        val decalage = decalageDe(texte, "compteur")
        val references = session.references("fixture://Main.kt", decalage, inclureDeclaration = true)
        assertEquals(3, references.size)
        assertTrue(references.all { it.uri == "fixture://Main.kt" })
        // Sans la déclaration : les deux usages seulement.
        val usages = session.references("fixture://Main.kt", decalageDe(texte, "compteur"), false)
        assertEquals(2, usages.size)
        session.close()
    }

    @Test
    fun `references-paramètre---confiné-au-fichier,-usages-du-corps`() {
        val texte =
            """
            fun saluer(nom: String): String = "Bonjour " + nom
            """.trimIndent()
        val session = sessionAvecRacines("Main.kt" to texte)
        session.ouvrir("fixture://Main.kt", texte)
        val usages = session.references("fixture://Main.kt", decalageDe(texte, "nom"), false)
        assertEquals(1, usages.size)
        session.close()
    }

    // ── Références : fonction top-level multi-fichiers ────────────────

    @Test
    fun `references-fonction-top-level---même-paquet,-autre-fichier-fermé-du-scan`() {
        val bibli =
            """
            package outils

            fun triple(x: Int): Int = x * 3
            """.trimIndent()
        val appelant =
            """
            package outils

            fun essai(): Int = triple(2) + triple(3)
            """.trimIndent()
        val session = sessionAvecRacines("Biblio.kt" to bibli, "Appelant.kt" to appelant)
        session.ouvrir("fixture://Biblio.kt", bibli)
        // Sur la DÉCLARATION de triple dans Biblio.kt OUVERT — les usages
        // vivent dans Appelant.kt, fichier FERMÉ du scan (même paquet,
        // preuve par paquet sans homonyme local).
        val references = session.references("fixture://Biblio.kt", decalageDe(bibli, "triple"), false)
        assertEquals(2, references.size)
        assertTrue(references.all { it.uri.endsWith("Appelant.kt") })
        session.close()
    }

    @Test
    fun `references-fonction-top-level---import-explicite-d'un-autre-paquet`() {
        val bibli =
            """
            package outils

            fun triple(x: Int): Int = x * 3
            """.trimIndent()
        val appelant =
            """
            package principal

            import outils.triple

            fun essai(): Int = triple(2)
            """.trimIndent()
        val session = sessionAvecRacines("Biblio.kt" to bibli, "Appelant.kt" to appelant)
        session.ouvrir("fixture://Biblio.kt", bibli)
        val references = session.references("fixture://Biblio.kt", decalageDe(bibli, "triple"), false)
        // L'USAGE + le segment d'IMPORT (miroir IntelliJ : les usages
        // listent aussi l'import à renommer).
        assertEquals(2, references.size)
        assertTrue(references.all { it.uri.endsWith("Appelant.kt") })
        session.close()
    }

    @Test
    fun `references-homonyme-sans-preuve---vide-(conservatisme-D3)`() {
        val bibli =
            """
            package outils

            fun triple(x: Int): Int = x * 3
            """.trimIndent()
        // Même paquet principal, AUTRE fonction homonyme — l'usage
        // principal() désigne SA fonction, jamais celle d'outils.
        val autre =
            """
            package principal

            fun triple(x: Int): Int = x * 30

            fun essai(): Int = triple(2)
            """.trimIndent()
        val session = sessionAvecRacines("Biblio.kt" to bibli, "Autre.kt" to autre)
        session.ouvrir("fixture://Biblio.kt", bibli)
        val references = session.references("fixture://Biblio.kt", decalageDe(bibli, "triple"), false)
        assertTrue(references.isEmpty(), "l'homonyme sans preuve rend VIDE (D3)")
        session.close()
    }

    // ── Références : type via import + appel de constructeur ──────────

    @Test
    fun `references-classe---import,-usage,-et-import-renommé-aussi`() {
        val bibli =
            """
            package outils

            class Outil(val nom: String)
            """.trimIndent()
        val appelant =
            """
            package principal

            import outils.Outil

            fun fabrique(): Outil = Outil("clef")
            """.trimIndent()
        val session = sessionAvecRacines("Biblio.kt" to bibli, "Appelant.kt" to appelant)
        session.ouvrir("fixture://Biblio.kt", bibli)
        val references = session.references("fixture://Biblio.kt", decalageDe(bibli, "Outil"), false)
        // L'IMPORT, le TYPE DE RETOUR et le CONSTRUCTEUR sont prouvés
        // (FQCN résolu dans le fichier appelant).
        assertEquals(3, references.size)
        assertTrue(references.all { it.uri.endsWith("Appelant.kt") })
        session.close()
    }

    @Test
    fun `references-membre-qualifié---récepteur-typé-prouve-la-cible`() {
        val bibli =
            """
            package outils

            class Boite {
                fun ouvrir(): Int = 1
            }
            """.trimIndent()
        val appelant =
            """
            package principal

            import outils.Boite

            fun essai(b: Boite): Int = b.ouvrir() + b.ouvrir()
            """.trimIndent()
        val session = sessionAvecRacines("Biblio.kt" to bibli, "Appelant.kt" to appelant)
        session.ouvrir("fixture://Biblio.kt", bibli)
        // Sur la DÉCLARATION de ouvrir (membre) — les sélecteurs
        // qualifiés b.ouvrir() sont prouvés par le typage du récepteur.
        val references = session.references("fixture://Biblio.kt", decalageDe(bibli, "ouvrir"), false)
        assertEquals(2, references.size)
        assertTrue(references.all { it.uri.endsWith("Appelant.kt") })
        session.close()
    }

    // ── documentHighlight ─────────────────────────────────────────────

    @Test
    fun `documentHighlight---déclaration-en-écriture,-références-en-lecture,-ombrage-exclu`() {
        val texte =
            """
            fun principale() {
                val total = 1
                val doubling = total + total
            }
            """.trimIndent()
        val session = sessionAvecRacines("Main.kt" to texte)
        session.ouvrir("fixture://Main.kt", texte)
        val surlignages = session.surlignagesDocument("fixture://Main.kt", decalageDe(texte, "total"))
        assertEquals(3, surlignages.size)
        assertEquals(GenreSurlignageKotlin.ECRITURE, surlignages.first().genre)
        assertTrue(surlignages.drop(1).all { it.genre == GenreSurlignageKotlin.LECTURE })
        // En ordre du document.
        assertTrue(surlignages.zipWithNext().all { (a, b) -> a.plage.debut < b.plage.debut })
        session.close()
    }

    @Test
    fun `documentHighlight---curseur-sur-référence,-pas-sur-la-déclaration`() {
        val texte =
            """
            fun principale() {
                val total = 1
                val copie = total
            }
            """.trimIndent()
        val session = sessionAvecRacines("Main.kt" to texte)
        session.ouvrir("fixture://Main.kt", texte)
        // Curseur sur l'USAGE (2e occurrence de total).
        val surlignages =
            session.surlignagesDocument("fixture://Main.kt", decalageDe(texte, "total", numero = 1))
        assertEquals(2, surlignages.size)
        session.close()
    }

    // ── Références : appelables top-level du MÊME fichier (ADR 0204) ───

    @Test
    fun `references-fonction-top-level---usages-du-MEME-fichier-déclarant-(ADR-0204)`() {
        val texte =
            """
            fun triple(x: Int): Int = x * 3

            fun essai(): Int = triple(2) + triple(3)
            """.trimIndent()
        val session = sessionAvecRacines("Main.kt" to texte)
        session.ouvrir("fixture://Main.kt", texte)
        // Sur la DÉCLARATION : la déclaration + les DEUX usages du même
        // fichier — le garde d'homonymie ne confond plus la cible avec
        // un homonyme (elle-même), et la COPIE FANTÔME du scan (URI
        // distincte) est écartée par sa propre déclaration homonyme.
        val references = session.references("fixture://Main.kt", decalageDe(texte, "triple"), true)
        assertEquals(3, references.size)
        assertTrue(references.all { it.uri == "fixture://Main.kt" })
        // En ordre du document : déclaration, puis les deux usages.
        assertTrue(references.zipWithNext().all { (a, b) -> a.plage.debut < b.plage.debut })
        // Curseur sur un USAGE : mêmes occurrences.
        val depuisUsage =
            session.references("fixture://Main.kt", decalageDe(texte, "triple", numero = 1), false)
        assertEquals(2, depuisUsage.size)
        // documentHighlight : le surlignage couvre déclaration + usages.
        val surlignages = session.surlignagesDocument("fixture://Main.kt", decalageDe(texte, "triple"))
        assertEquals(3, surlignages.size)
        assertEquals(GenreSurlignageKotlin.ECRITURE, surlignages.first().genre)
        assertTrue(surlignages.drop(1).all { it.genre == GenreSurlignageKotlin.LECTURE })
        session.close()
    }

    @Test
    fun `renommage-same-file---déclaration-ET-usages-du-fichier-déclarant-édités-(ADR-0204)`() {
        val texte =
            """
            fun triple(x: Int): Int = x * 3

            fun essai(): Int = triple(2) + triple(3)
            """.trimIndent()
        val session = sessionAvecRacines("Main.kt" to texte)
        session.ouvrir("fixture://Main.kt", texte)
        // Le renommage couvre déclaration + usages — le correctif ADR
        // 0204 : AVANT, la déclaration seule était éditée (code cassé).
        val occurrences = session.renommage("fixture://Main.kt", decalageDe(texte, "triple"))
        assertEquals(3, occurrences.size, "le renommage doit couvrir déclaration + usages")
        assertTrue(occurrences.all { it.uri == "fixture://Main.kt" })
        session.close()
    }

    @Test
    fun `references-propriété-de-constructeur---usage-non-qualifié-prouvé-(ADR-0206)`() {
        val texte =
            """
            class Boite(private val contenu: Int) {
                fun lire(): Int = contenu
            }
            """.trimIndent()
        val session = sessionAvecRacines("Main.kt" to texte)
        session.ouvrir("fixture://Main.kt", texte)
        // Le paramètre-propriété est DEUX choses (paramètre + PROPRIÉTÉ
        // membre) : l'usage non qualifié dans lire() prouve la cible.
        val references = session.references("fixture://Main.kt", decalageDe(texte, "contenu"), true)
        assertEquals(2, references.size)
        assertTrue(references.all { it.uri == "fixture://Main.kt" })
        assertTrue(references.zipWithNext().all { (a, b) -> a.plage.debut < b.plage.debut })
        // Même preuve depuis l'USAGE.
        val depuisUsage =
            session.references("fixture://Main.kt", decalageDe(texte, "contenu", numero = 1), false)
        assertEquals(1, depuisUsage.size)
        // Et le surlignage document (miroir S8 : propriété de corps).
        val surlignages = session.surlignagesDocument("fixture://Main.kt", decalageDe(texte, "contenu"))
        assertEquals(2, surlignages.size)
        assertEquals(GenreSurlignageKotlin.ECRITURE, surlignages.first().genre)
        session.close()
    }

    @Test
    fun `renommage-propriété-de-constructeur---déclaration-ET-usage-édités-(ADR-0206)`() {
        val texte =
            """
            class Boite(private val contenu: Int) {
                fun lire(): Int = contenu
            }
            """.trimIndent()
        val session = sessionAvecRacines("Main.kt" to texte)
        session.ouvrir("fixture://Main.kt", texte)
        val occurrences = session.renommage("fixture://Main.kt", decalageDe(texte, "contenu"))
        assertEquals(2, occurrences.size, "renommage complet du paramètre-propriété")
        assertTrue(occurrences.all { it.uri == "fixture://Main.kt" })
        session.close()
    }

    // ── prepareRename / renommage ─────────────────────────────────────

    @Test
    fun `plageRenommable---placeholder-exact-sur-le-nom-déclaré`() {
        val texte =
            """
            class Pompe {
                fun amorcer(): Int = 1
            }
            """.trimIndent()
        val session = sessionAvecRacines("Main.kt" to texte)
        session.ouvrir("fixture://Main.kt", texte)
        val plage = session.plageRenommable("fixture://Main.kt", decalageDe(texte, "amorcer"))
        assertNotNull(plage)
        assertEquals("amorcer", plage!!.placeholder)
        assertEquals(decalageDe(texte, "amorcer"), plage.plage.debut)
        session.close()
    }

    @Test
    fun `plageRenommable---hors-identifiant,-null-(jamais-deviné)`() {
        val texte =
            """
            val x = "litteral"
            """.trimIndent()
        val session = sessionAvecRacines("Main.kt" to texte)
        session.ouvrir("fixture://Main.kt", texte)
        // Curseur DANS le littéral chaîne.
        assertNull(session.plageRenommable("fixture://Main.kt", decalageDe(texte, "litteral") + 2))
        session.close()
    }

    @Test
    fun `renommage---déclaration,-usages-et-import-multi-fichiers`() {
        val bibli =
            """
            package outils

            fun triple(x: Int): Int = x * 3
            """.trimIndent()
        val appelant =
            """
            package principal

            import outils.triple

            fun essai(): Int = triple(2)
            """.trimIndent()
        val session = sessionAvecRacines("Biblio.kt" to bibli, "Appelant.kt" to appelant)
        session.ouvrir("fixture://Biblio.kt", bibli)
        val occurrences = session.renommage("fixture://Biblio.kt", decalageDe(bibli, "triple"))
        // Déclaration (Biblio) + usage + import (Appelant).
        assertEquals(3, occurrences.size)
        assertEquals(1, occurrences.count { it.uri.endsWith("Biblio.kt") })
        assertEquals(2, occurrences.count { it.uri.endsWith("Appelant.kt") })
        session.close()
    }

    @Test
    fun `renommage---sans-cible-prouvée,-liste-vide-(l'hôte-en-fait-une-erreur-typée)`() {
        val texte =
            """
            val x = 1
            """.trimIndent()
        val session = sessionAvecRacines("Main.kt" to texte)
        session.ouvrir("fixture://Main.kt", texte)
        // Curseur sur le littéral : rien à renommer.
        assertTrue(session.renommage("fixture://Main.kt", decalageDe(texte, "1")).isEmpty())
        session.close()
    }

    // ── ADR 0218 : repli Android Studio, curseur sur le MOT-CLÉ ────────

    @Test
    fun `references---curseur-sur-le-mot-cle-fun,-la-declaration-englobante-est-visee-(ADR-0218)`() {
        val texte =
            """
            fun saluer(nom: String): String = nom

            fun appel(): String = saluer("monde")
            """.trimIndent()
        val session = sessionAvecRacines("Main.kt" to texte)
        val uri = uriReelle("Main.kt")
        session.ouvrir(uri, texte)
        // Curseur sur le MOT-CLÉ `fun` de la DÉCLARATION (aucun
        // identifiant sous le curseur) — Android Studio y répond
        // Find Usages : la déclaration englobante est la cible.
        val references = session.references(uri, decalageDe(texte, "fun saluer"), true)
        assertEquals(2, references.size, "déclaration + usage de saluer trouvés depuis le mot-clé fun")
        session.close()
    }

    @Test
    fun `references---curseur-sur-le-mot-cle-class,-la-declaration-englobante-est-visee-(ADR-0218)`() {
        val texte =
            """
            class Pompe {
                fun amorcer(): Int = 1
            }

            fun usage(): Pompe = Pompe()
            """.trimIndent()
        val session = sessionAvecRacines("Main.kt" to texte)
        val uri = uriReelle("Main.kt")
        session.ouvrir(uri, texte)
        val references = session.references(uri, decalageDe(texte, "class Pompe"), true)
        assertEquals(
            3,
            references.size,
            "déclaration + type écrit + construction de Pompe trouvés depuis le mot-clé class",
        )
        session.close()
    }

    @Test
    fun `references---curseur-dans-le-CORPS-d'une-fonction,-AUCUNE-cible-(le-corps-n'est-pas-l'en-tête)`() {
        val texte =
            """
            fun saluer(): String {
                return "bonjour"
            }
            """.trimIndent()
        val session = sessionAvecRacines("Main.kt" to texte)
        val uri = uriReelle("Main.kt")
        session.ouvrir(uri, texte)
        // Curseur sur `return` — DANS le corps : le repli ne vise PAS la
        // fonction englobante (le corps appartient aux expressions).
        assertTrue(session.references(uri, decalageDe(texte, "return"), false).isEmpty())
        session.close()
    }

    // ── Tolérance ─────────────────────────────────────────────────────

    @Test
    fun `document-inconnu-et-session-fermée---listes-vides,-sans-exception`() {
        val session = sessionAvecRacines("Main.kt" to "val x = 1")
        assertTrue(session.references("fixture://Inconnu.kt", 0, false).isEmpty())
        assertTrue(session.surlignagesDocument("fixture://Inconnu.kt", 0).isEmpty())
        assertNull(session.plageRenommable("fixture://Inconnu.kt", 0))
        assertTrue(session.renommage("fixture://Inconnu.kt", 0).isEmpty())
        session.ouvrir("fixture://Main.kt", "val x = 1")
        session.close()
        assertTrue(session.references("fixture://Main.kt", 0, false).isEmpty())
        assertTrue(session.surlignagesDocument("fixture://Main.kt", 0).isEmpty())
        assertNull(session.plageRenommable("fixture://Main.kt", 0))
        assertTrue(session.renommage("fixture://Main.kt", 0).isEmpty())
    }
}
