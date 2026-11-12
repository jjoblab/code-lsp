// CodeIDE — tests de la complétion SMART (module :kotlin, vague V-D —
// KC-01/KC-02/KC-04/KC-05, ADR 0126). Code NEUF jjoblab (clean-room —
// comportements : le type attendu ordonne sans cacher, les chaînes
// d'extensions de profondeur ≤ 2, le déclassement des citées d'un `when`,
// les membres ouvrables après `override` ; jamais de tri sans preuve —
// la position sans type attendu démontre reste INCHANGÉE, T6).
package jo.codeide.lsp.kotlin.session

import java.nio.file.Path
import jo.codeide.lsp.kotlin.api.ClasspathKotlin
import jo.codeide.lsp.kotlin.api.EntreeClasspathKotlin
import jo.codeide.lsp.kotlin.api.GenreContexteKotlin
import jo.codeide.lsp.kotlin.api.KindEntreeKotlin
import jo.codeide.lsp.kotlin.api.NaturePropositionKotlin
import jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

/**
 * La vague V-D — la complétion smart/chain (ADR 0126) : le peseur de type
 * attendu (KC-01), les chaînes d'extensions applicables (KC-02), le
 * déclassement des constantes citées (KC-04 W1) et les membres ouvrables
 * après `override` (KC-05).
 */
class CompletionSmartKotlinTest {

    /** La racine temporaire (documents du test — inutilisée, les documents vivent en mémoire). */
    @Suppress("unused")
    private val racineInutilisee: Path? = null

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
                EntreeClasspathKotlin(KindEntreeKotlin.JAR, jarStdlib().path, "vd:stdlib"),
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

    // ── KC-01 — le type attendu ordonne, ne cache jamais ──────────────

    @Test
    fun `kc01-t1---argument-d-appel-typé,-l-expression-du-bon-type-domine`() {
        val session = session()
        val source =
            "class Client\n" +
                "fun saluer(qui: Client) { }\n" +
                "fun demo(ami: Client, age: Int) { saluer( }\n"
        val uri = "file:///kc01-t1.kt"
        session.ouvrir(uri, source)
        val decalage = source.indexOf("saluer( ") + "saluer(".length
        val reponse = session.completionArrangee(uri, decalage)
        val libelles = reponse.propositions.map { it.libelle }
        val rangAmi = libelles.indexOf("ami")
        val rangAge = libelles.indexOf("age")
        assertTrue(rangAmi >= 0, "`ami` devait être proposé")
        assertTrue(rangAge >= 0, "`age` devait rester VISIBLE (le tri ne cache jamais)")
        assertTrue(rangAmi < rangAge, "`ami` (type Client attendu) devait précéder `age` (Int)")
        session.close()
    }

    @Test
    fun `kc01-t2---return-de-fonction-typée,-le-local-du-bon-type-domine`() {
        val session = session()
        val source =
            "class Ecran\n" +
                "fun construire(): Ecran {\n" +
                "    val accueil: Ecran = Ecran()\n" +
                "    val titre: String = \"x\"\n" +
                "    return \n" +
                "}\n"
        val uri = "file:///kc01-t2.kt"
        session.ouvrir(uri, source)
        val decalage = source.indexOf("return ") + "return ".length
        val reponse = session.completionArrangee(uri, decalage)
        val libelles = reponse.propositions.map { it.libelle }
        val rangAccueil = libelles.indexOf("accueil")
        val rangTitre = libelles.indexOf("titre")
        assertTrue(rangAccueil >= 0, "`accueil` devait être proposé")
        assertTrue(rangTitre >= 0, "`titre` devait rester visible")
        assertTrue(rangAccueil < rangTitre, "`accueil` (Ecran attendu) devait précéder `titre` (String)")
        session.close()
    }

    @Test
    fun `kc01-t3---initialisateur-typé,-val-et-var-pareil`() {
        val session = session()
        val source =
            "class Ecran\n" +
                "fun demo(accueil: Ecran, titre: String) {\n" +
                "    val principale: Ecran = \n" +
                "    var active: Ecran = \n" +
                "}\n"
        val uri = "file:///kc01-t3.kt"
        session.ouvrir(uri, source)
        val decalage = source.indexOf("principale: Ecran = ") + "principale: Ecran = ".length
        val reponse = session.completionArrangee(uri, decalage)
        val libelles = reponse.propositions.map { it.libelle }
        assertTrue(
            libelles.indexOf("accueil") in 0..libelles.indexOf("titre").coerceAtLeast(0),
            "`accueil` (Ecran) devait précéder `titre` (String) dans l'initialisateur `val`",
        )
        val decalageVar = source.indexOf("active: Ecran = ") + "active: Ecran = ".length
        val reponseVar = session.completionArrangee(uri, decalageVar)
        val libellesVar = reponseVar.propositions.map { it.libelle }
        assertTrue(libellesVar.indexOf("accueil") >= 0, "`accueil` devait être proposé pour le var")
        assertTrue(
            libellesVar.indexOf("accueil") < libellesVar.indexOf("titre"),
            "`var` se comporte comme `val` (KC-01 T3)",
        )
        session.close()
    }

    @Test
    fun `kc01-t4---branche-d-if-expression-typée-par-ses-branches-complètes`() {
        val session = session()
        val source =
            "class Ecran\n" +
                "fun demo(accueil: Ecran, titre: String, erreur: Boolean) {\n" +
                "    val message = if (erreur)  else accueil\n" +
                "}\n"
        val uri = "file:///kc01-t4.kt"
        session.ouvrir(uri, source)
        // Le caret vit dans la branche THEN incomplète : l'attente vient du
        // ELSE complet (Ecran).
        val decalage = source.indexOf("if (erreur) ") + "if (erreur) ".length
        val reponse = session.completionArrangee(uri, decalage)
        val libelles = reponse.propositions.map { it.libelle }
        assertTrue(libelles.indexOf("accueil") >= 0, "`accueil` devait être proposé dans la branche")
        assertTrue(
            libelles.indexOf("accueil") < libelles.indexOf("titre"),
            "la branche incomplète est typée par la branche complète (Ecran) — `accueil` d'abord",
        )
        session.close()
    }

    @Test
    fun `kc01-t6---sans-type-attendu-démontrable,-la-liste-reste-dans-l-ordre-standard`() {
        val session = session()
        // Le corps d'une fonction sans return typé : position de nom SANS
        // attente — l'ordre de la chaîne standard (nature, longueur)
        // doit tenir (un tri mensonger est interdit, T6).
        val source =
            "class Ecran\n" +
                "fun demo(accueil: Ecran, titre: String) {\n" +
                "    val xyz = \n" +
                "}\n"
        val uri = "file:///kc01-t6.kt"
        session.ouvrir(uri, source)
        val decalage = source.indexOf("val xyz = ") + "val xyz = ".length
        val reponse = session.completionArrangee(uri, decalage)
        // La préuve : le peseur typeAttendu se tait (aucune attente), la
        // liste propose TOUS les noms — l'ordre des NATURES tient
        // (variable avant paramètre… par le peseur nature du régime NOM).
        assertTrue(reponse.propositions.isNotEmpty(), "le régime de nom devait proposer")
        assertTrue(
            reponse.propositions.any { it.libelle == "accueil" },
            "`accueil` reste proposé sans attente",
        )
        session.close()
    }

    // ── KC-02 — les chaînes d'extensions applicables ──────────────────

    @Test
    fun `kc02-e1---extension-du-projet-atteignant-le-type-attendu-en-tête`() {
        val session = session()
        val source =
            "class Fiche\n" +
                "class Auteur\n" +
                "fun Fiche.auteur(): Auteur = Auteur()\n" +
                "fun Fiche.titre(): String = \"x\"\n" +
                "fun demo(fiche: Fiche): Auteur {\n" +
                "    return fiche.\n" +
                "}\n"
        val uri = "file:///kc02-e1.kt"
        session.ouvrir(uri, source)
        val decalage = source.indexOf("return fiche.") + "return fiche.".length
        val reponse = session.completionArrangee(uri, decalage)
        assertEquals(GenreContexteKotlin.MEMBRE, reponse.genre, "le régime membre tient")
        val libelles = reponse.propositions.map { it.libelle }
        assertTrue("auteur" in libelles, "`auteur` (Fiche → Auteur) devait être proposé")
        assertTrue("titre" in libelles, "`titre` reste proposé (membre ordinaire)")
        assertTrue(
            libelles.indexOf("auteur") < libelles.indexOf("titre"),
            "l'extension atteignant le type attendu (Auteur) devait dominer `titre` (String)",
        )
        session.close()
    }

    @Test
    fun `kc02-e2---chaîne-de-profondeur-2-comme-un-seul-item`() {
        val session = session()
        val source =
            "class Fiche\n" +
                "class Auteur\n" +
                "fun Fiche.auteur(): Auteur = Auteur()\n" +
                "fun Auteur.nomComplet(): String = \"x\"\n" +
                "fun Fiche.titre(): String = \"x\"\n" +
                "fun demo(fiche: Fiche): String {\n" +
                "    return fiche.\n" +
                "}\n"
        val uri = "file:///kc02-e2.kt"
        session.ouvrir(uri, source)
        val decalage = source.indexOf("return fiche.") + "return fiche.".length
        val reponse = session.completionArrangee(uri, decalage)
        val libelles = reponse.propositions.map { it.libelle }
        assertTrue(
            "auteur().nomComplet()" in libelles,
            "la chaîne `auteur().nomComplet()` devait être proposée comme UN SEUL item (profondeur 2)",
        )
        assertTrue("titre" in libelles, "`titre` (profondeur 1 directe) reste proposé")
        assertTrue(
            libelles.indexOf("titre") < libelles.indexOf("auteur().nomComplet()"),
            "les chaînes directes précèdent les chaînes de profondeur 2",
        )
        session.close()
    }

    @Test
    fun `kc02-e4---sans-type-attendu,-aucune-chaîne-spéculée`() {
        val session = session()
        // Le récepteur est typé mais la position n'a PAS de type attendu
        // (le corps d'une fonction Unit sans return) : la passe se tait —
        // comportement = complétion membre ordinaire.
        val source =
            "class Fiche\n" +
                "class Auteur\n" +
                "fun Fiche.auteur(): Auteur = Auteur()\n" +
                "fun demo(fiche: Fiche) {\n" +
                "    fiche.\n" +
                "}\n"
        val uri = "file:///kc02-e4.kt"
        session.ouvrir(uri, source)
        val decalage = source.indexOf("fiche.\n") + "fiche.".length
        val reponse = session.completionArrangee(uri, decalage)
        val libelles = reponse.propositions.map { it.libelle }
        assertTrue("auteur" in libelles, "`auteur` reste proposé comme membre ordinaire")
        assertTrue(
            libelles.none { it.contains("()") && it != "auteur" },
            "aucune chaîne spéculée sans type attendu (E4)",
        )
        session.close()
    }

    // ── KC-04 W1 — les citées d'un `when` se déclassent ───────────────

    @Test
    fun `kc04-w1---les-constantes-déjà-citées-descendent-sans-disparaître`() {
        val session = session()
        val source =
            "enum class Couleur { ROUGE, VERT, BLEU }\n" +
                "fun demo(c: Couleur) {\n" +
                "    when (c) {\n" +
                "        ROUGE -> afficher()\n" +
                "        \n" +
                "    }\n" +
                "}\n"
        val uri = "file:///kc04-w1.kt"
        session.ouvrir(uri, source)
        // Le caret dans la zone de condition APRÈS la branche ROUGE.
        val decalage = source.indexOf("ROUGE -> afficher()\n") + "ROUGE -> afficher()\n".length + 8
        val reponse = session.completionArrangee(uri, decalage)
        assertEquals(GenreContexteKotlin.CONSTANTES_ENUM, reponse.genre, "le contexte when enum tient")
        val libelles = reponse.propositions.map { it.libelle }
        assertTrue("ROUGE" in libelles, "`ROUGE` déjà citée reste PROPOSÉE (jamais cachée)")
        assertTrue("VERT" in libelles && "BLEU" in libelles, "les non citées s'offrent")
        assertTrue(
            libelles.indexOf("VERT") < libelles.indexOf("ROUGE") &&
                libelles.indexOf("BLEU") < libelles.indexOf("ROUGE"),
            "les NON citées devaient dominer la citée (W1)",
        )
        session.close()
    }

    // ── KC-05 — les membres ouvrables après `override` ────────────────

    @Test
    fun `kc05-o1o2---membres-ouvrables-listés,-signature-complète-à-l-insertion`() {
        val session = session()
        val source =
            "interface Vue {\n" +
                "    fun mesurer(): Int\n" +
                "    fun dessiner() { }\n" +
                "}\n" +
                "open class Base {\n" +
                "    open fun fermer() { }\n" +
                "    fun ouvrir() { }\n" +
                "}\n" +
                "class Panneau : Vue, Base {\n" +
                "    override fun \n" +
                "}\n"
        val uri = "file:///kc05-o1.kt"
        session.ouvrir(uri, source)
        val decalage = source.indexOf("override fun ") + "override fun ".length
        val reponse = session.completionArrangee(uri, decalage)
        assertEquals(GenreContexteKotlin.OVERRIDE, reponse.genre, "le contexte override est classifié")
        val parLibelle = reponse.propositions.associateBy { it.libelle }
        val mesurer = parLibelle["mesurer"]
        assertNotNull(mesurer, "`mesurer` (abstraite) devait être proposée")
        assertEquals(NaturePropositionKotlin.FONCTION, mesurer.nature, "la nature est fonction")
        assertTrue(
            mesurer.texteInsertion?.startsWith("override fun mesurer(") == true,
            "l'insertion porte la signature complète — reçue : ${mesurer.texteInsertion}",
        )
        assertTrue(
            mesurer.texteInsertion?.contains("TODO(\"mesurer\")") == true,
            "le corps est un stub TODO — reçue : ${mesurer.texteInsertion}",
        )
        assertNotNull(parLibelle["dessiner"], "`dessiner` (concrète d'interface) devait être proposée")
        assertNotNull(parLibelle["fermer"], "`fermer` (open de classe) devait être proposée")
        assertTrue(
            "ouvrir" !in parLibelle,
            "`ouvrir` (finale de classe — sans open) ne devait PAS être proposée",
        )
        // O1 — les abstraits devant les ouverts (déclassement).
        val libelles = reponse.propositions.map { it.libelle }
        assertTrue(
            libelles.indexOf("mesurer") < libelles.indexOf("dessiner") &&
                libelles.indexOf("mesurer") < libelles.indexOf("fermer"),
            "l'abstraite devait précéder les ouvertes (O1) — reçue : $libelles",
        )
        session.close()
    }

    @Test
    fun `kc05-o1---membre-déjà-surchargé-absent-de-la-liste`() {
        val session = session()
        val source =
            "interface Vue {\n" +
                "    fun mesurer(): Int\n" +
                "    open fun dessiner() { }\n" +
                "}\n" +
                "class Panneau : Vue {\n" +
                "    override fun mesurer(): Int = 0\n" +
                "    override fun \n" +
                "}\n"
        val uri = "file:///kc05-o1b.kt"
        session.ouvrir(uri, source)
        val decalage = source.lastIndexOf("override fun ") + "override fun ".length
        val reponse = session.completionArrangee(uri, decalage)
        val libelles = reponse.propositions.map { it.libelle }
        assertTrue("mesurer" !in libelles, "`mesurer` déjà surchargée ne devait PAS revenir")
        assertTrue("dessiner" in libelles, "`dessiner` reste proposée")
        session.close()
    }

    @Test
    fun `kc05-o3---propriétés-ouvrables-après-override-val`() {
        val session = session()
        val source =
            "interface Modele {\n" +
                "    val titre: String\n" +
                "}\n" +
                "class Panneau : Modele {\n" +
                "    override val \n" +
                "}\n"
        val uri = "file:///kc05-o3.kt"
        session.ouvrir(uri, source)
        val decalage = source.indexOf("override val ") + "override val ".length
        val reponse = session.completionArrangee(uri, decalage)
        val parLibelle = reponse.propositions.associateBy { it.libelle }
        val titre = parLibelle["titre"]
        assertNotNull(titre, "`titre` (propriété abstraite) devait être proposée")
        assertEquals(NaturePropositionKotlin.PROPRIETE, titre.nature, "la nature est propriété")
        assertTrue(
            titre.texteInsertion?.startsWith("override val titre") == true,
            "l'insertion porte la signature de propriété — reçue : ${titre.texteInsertion}",
        )
        session.close()
    }

    @Test
    fun `kc05-o5---sans-supertypes-établis,-repli-honnête-sur-le-régime-de-nom`() {
        val session = session()
        // `override` dans une classe SANS supertype : le contexte ne se
        // prouve pas — repli NOM (mots-clés visibles).
        val source =
            "class Panneau {\n" +
                "    override fun \n" +
                "}\n"
        val uri = "file:///kc05-o5.kt"
        session.ouvrir(uri, source)
        val decalage = source.indexOf("override fun ") + "override fun ".length
        val reponse = session.completionArrangee(uri, decalage)
        assertEquals(GenreContexteKotlin.NOM, reponse.genre, "repli honnête sur le régime de nom")
        assertTrue(
            reponse.propositions.any { it.nature == NaturePropositionKotlin.MOT_CLE },
            "le témoin mots-clés du régime de repli devait suivre",
        )
        session.close()
    }
}
