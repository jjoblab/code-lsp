package jo.codeide.lsp.kotlin.session

import java.nio.file.Path
import jo.codeide.lsp.kotlin.api.ClasspathKotlin
import jo.codeide.lsp.kotlin.api.EntreeClasspathKotlin
import jo.codeide.lsp.kotlin.api.GenreContexteKotlin
import jo.codeide.lsp.kotlin.api.KindEntreeKotlin
import jo.codeide.lsp.kotlin.api.NaturePropositionKotlin
import jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Fiche 043 — SessionLangageKotlinCompletionContextuelleTest : la
 * complétion CONTEXTUELLE Kotlin (vague 1 du chantier du rapport d'analyse
 * du 28/10/2026 — ADR 0228 ancien, repris en parité par l'ADR 0113) — 14
 * comportements du cahier docs/cahier-specifications-kotlin/ : les trois
 * contextes portés du moteur Java 1.16.0, par le SPI (chemin de production
 * D6) — arguments NOMMÉS d'un appel, CONSTANTES d'enum des branches d'un
 * `when`, ATTRIBUTS d'annotation. Chaque comportement éprouve AUSSI la
 * borne conservatrice (D3) : position de valeur muette, candidat inconnu,
 * repli honnête sur le régime de nom.
 *
 * L'enum et l'annotation du CLASSPATH vivent dans un JAR DE FIXTURE
 * (discipline pilotée par les entrées — la vue JVM lit les class-files
 * réels : superclasse `java.lang.Enum` de l'enum, méthodes ABSTRAITES de
 * l'annotation).
 *
 * Chaque comportement porte le nom exact du test source (identifiant de
 * traçabilité du cahier).
 */
@Suppress("LargeClass")
class Fiche043SessionLangageKotlinCompletionContextuelleTest {

    /** La racine temporaire des jars de fixture. */
    @TempDir
    lateinit var racine: Path

    /** Le jar kotlin-stdlib du classpath de test. */
    private fun jarStdlib(): java.io.File =
        java.io.File(
            kotlin.jvm.internal.Intrinsics::class.java.protectionDomain
                .codeSource.location.toURI(),
        )

    /**
     * Le jar de fixture du CLASSPATH démo : l'ANNOTATION `demo.Anno`
     * (attributs `level`/`nom` abstraits, `helper` concret) et l'ENUM
     * `demo.Couleur` (ROUGE/VERT/BLEU statiques typés par elle-même,
     * `ordre` champ d'instance Int hors jeu).
     */
    private fun jarDemo(): java.io.File =
        OutilsJarFixture.Batisseur()
            .classe(
                "demo.Anno",
                "java.lang.Object",
                OutilsJarFixture.methodeAbstraite("level", "()Ldemo/Level;"),
                OutilsJarFixture.methodeAbstraite("nom", "()Ljava/lang/String;"),
                OutilsJarFixture.methode("helper", "()V"),
            )
            .classe(
                "demo.Couleur",
                "java.lang.Enum",
                OutilsJarFixture.champStatique("ROUGE", "Ldemo/Couleur;"),
                OutilsJarFixture.champStatique("VERT", "Ldemo/Couleur;"),
                OutilsJarFixture.champStatique("BLEU", "Ldemo/Couleur;"),
                OutilsJarFixture.champ("ordre", "I"),
            )
            .ecrire(racine, "demo-contextuel.jar")

    /** Session branchée sur les jars donnés (guichet attendu par sondage borné). */
    private fun session(jars: List<java.io.File>): SessionLangageKotlinDocuments {
        val moteur = ServiceMoteurLangageKotlin.unique as MoteurLangageKotlinNoyau
        val classpath = ClasspathKotlin(
            entrees = jars.mapIndexed { i, jar ->
                EntreeClasspathKotlin(KindEntreeKotlin.JAR, jar.path, "f043:$i:${jar.name}")
            },
            racinesSources = emptyList(),
        )
        moteur.definirClasspath(classpath)
        val session = moteur.creerSession() as SessionLangageKotlinDocuments
        session.definirClasspath(classpath)
        attendreCouvert(session)
        return session
    }

    /** Attend (sondage borné) que le guichet serve le snapshot publié. */
    private fun attendreCouvert(session: SessionLangageKotlinDocuments, secondes: Long = 120) {
        val limite = System.currentTimeMillis() + secondes * 1000
        while (System.currentTimeMillis() < limite) {
            if (session.indexSiPret() != null) return
            Thread.sleep(10)
        }
        throw AssertionError("la passe du moteur n'a pas publié (guichet toujours muet)")
    }

    // ── Arguments nommés ───────────────────────────────────────────────

    @Test
    fun `arguments-nommes---surcharge-du-projet,-insertion-avec-separateur`() {
        val session = session(listOf(jarStdlib()))
        val source = "fun creer(nom: String, age: Int) { }\nfun demo() { creer( }\n"
        val uri = "file:///nommes.kt"
        session.ouvrir(uri, source)
        val decalage = source.indexOf("creer( ") + "creer(".length
        val reponse = session.completionArrangee(uri, decalage)
        assertEquals(GenreContexteKotlin.ARGUMENTS_NOMMES, reponse.genre, "le contexte est classifié")
        val parLibelle = reponse.propositions.associateBy { it.libelle }
        val nom = parLibelle["nom ="]
        assertEquals(NaturePropositionKotlin.PARAMETRE, nom?.nature, "la nature est paramètre")
        assertEquals("nom = ", nom?.texteInsertion, "l'insertion porte le séparateur")
        assertEquals("creer(nom: String, age: Int)", nom?.detail, "le détail porte la signature écrite")
        assertNotNull(parLibelle["age ="], "l'autre paramètre s'offre")
        session.close()
    }

    @Test
    fun `arguments-nommes---argument-deja-fourni-en-forme-nommee-exclu`() {
        val session = session(listOf(jarStdlib()))
        val source = "fun creer(nom: String, age: Int) { }\nfun demo() { creer(nom = \"x\", }\n"
        val uri = "file:///exclus.kt"
        session.ouvrir(uri, source)
        val decalage = source.indexOf("\"x\", ") + 5
        val libelles = session.completion(uri, decalage).map { it.libelle }
        assertTrue("age =" in libelles, "`age =` devait être offert")
        assertTrue("nom =" !in libelles, "`nom =` déjà fourni ne devait pas revenir")
        session.close()
    }

    @Test
    fun `arguments-nommes---position-de-valeur-muette-(litteral-chaine)`() {
        val session = session(listOf(jarStdlib()))
        val source = "fun creer(nom: String) { }\nfun demo() { creer(\" }\n"
        val uri = "file:///valeur.kt"
        session.ouvrir(uri, source)
        val decalage = source.indexOf("(\" ") + 2
        val reponse = session.completionArrangee(uri, decalage)
        // Une valeur en cours de frappe n'offre AUCUN argument nommé (le
        // régime de nom reprend — mots-clés visibles, préfixe vide).
        assertTrue(
            reponse.propositions.none { it.texteInsertion != null },
            "aucun argument nommé ne s'offre en position de valeur",
        )
        assertTrue(
            reponse.propositions.any { it.libelle == "class" },
            "le régime de nom reprend (mots-clés visibles) : ${reponse.propositions.take(5).map { it.libelle }}",
        )
        session.close()
    }

    @Test
    fun `arguments-nommes---appel-membre-qualifie-du-projet`() {
        val session = session(listOf(jarStdlib()))
        val source =
            "class Modele {\n  fun copier(titre: String): Modele = this\n}\n" +
                "fun demo() { val m = Modele(); m.copier( }\n"
        val uri = "file:///qualifie.kt"
        session.ouvrir(uri, source)
        val decalage = source.indexOf("copier( ") + "copier(".length
        val reponse = session.completionArrangee(uri, decalage)
        assertEquals(GenreContexteKotlin.ARGUMENTS_NOMMES, reponse.genre, "le contexte est classifié")
        assertEquals(
            "titre = ",
            reponse.propositions.firstOrNull { it.libelle == "titre =" }?.texteInsertion,
            "le paramètre du MEMBRE s'offre avec séparateur",
        )
        session.close()
    }

    @Test
    fun `arguments-nommes---appel-inconnu,-aucune-invention-(D3)`() {
        val session = session(listOf(jarStdlib()))
        val source = "fun demo() { inconnu( }\n"
        val uri = "file:///inconnu.kt"
        session.ouvrir(uri, source)
        val decalage = source.indexOf("inconnu( ") + "inconnu(".length
        val reponse = session.completionArrangee(uri, decalage)
        assertTrue(
            reponse.propositions.none { it.texteInsertion != null },
            "un appel inconnu n'invente AUCUN argument nommé",
        )
        assertTrue(
            reponse.propositions.any { it.libelle == "class" },
            "le régime de nom reprend honnêtement",
        )
        session.close()
    }

    @Test
    fun `arguments-nommes---filtre-par-prefixe-du-nom-de-parametre`() {
        val session = session(listOf(jarStdlib()))
        val source = "fun creer(nom: String, age: Int) { }\nfun demo() { creer(ag }\n"
        val uri = "file:///prefixe.kt"
        session.ouvrir(uri, source)
        val decalage = source.indexOf("ag ") + 2
        val libelles = session.completion(uri, decalage).map { it.libelle }
        assertTrue("age =" in libelles, "age = passe le préfixe")
        assertTrue("nom =" !in libelles, "nom = ne porte pas le préfixe")
        session.close()
    }

    // ── Constantes d'enum (branches d'un when) ─────────────────────────

    @Test
    fun `constantes-enum---when-a-sujet-enum-du-projet,-contexte-exclusif`() {
        val session = session(listOf(jarStdlib()))
        val source =
            "enum class Couleur { ROUGE, VERT, BLEU }\n" +
                "fun demo(c: Couleur) {\n  when (c) {\n  \n}\n}\n"
        val uri = "file:///enum-projet.kt"
        session.ouvrir(uri, source)
        // Préfixe vide, juste après l'accolade du when : position de
        // condition — les constantes ET RIEN D'AUTRE.
        val decalage = source.indexOf("when (c) {\n") + "when (c) {\n".length
        val reponse = session.completionArrangee(uri, decalage)
        assertEquals(GenreContexteKotlin.CONSTANTES_ENUM, reponse.genre, "le contexte est classifié")
        val parLibelle = reponse.propositions.associateBy { it.libelle }
        assertEquals(NaturePropositionKotlin.ENUM, parLibelle["ROUGE"]?.nature, "la nature est constante d'enum")
        assertEquals("Couleur", parLibelle["ROUGE"]?.detail, "le détail porte l'enum porteuse")
        assertNotNull(parLibelle["VERT"], "VERT s'offre")
        assertNotNull(parLibelle["BLEU"], "BLEU s'offre")
        assertTrue(
            reponse.propositions.none { it.nature == NaturePropositionKotlin.MOT_CLE },
            "le contexte est exclusif — aucun mot-clé ne devait suivre",
        )
        session.close()
    }

    @Test
    fun `constantes-enum---filtre-par-prefixe-et-sujet-enum-du-classpath`() {
        val session = session(listOf(jarDemo(), jarStdlib()))
        val source = "import demo.Couleur\nfun demo(c: Couleur) {\n  when (c) {\n    V\n}\n}\n"
        val uri = "file:///enum-classpath.kt"
        session.ouvrir(uri, source)
        val decalage = source.indexOf("V\n}") + 1
        val reponse = session.completionArrangee(uri, decalage)
        val parLibelle = reponse.propositions.associateBy { it.libelle }
        assertEquals(NaturePropositionKotlin.ENUM, parLibelle["VERT"]?.nature, "VERT constante d'enum du classpath")
        assertEquals("Couleur", parLibelle["VERT"]?.detail, "le détail porte l'enum porteuse")
        assertTrue(
            parLibelle.keys.none { it == "ROUGE" },
            "ROUGE ne devait pas passer le préfixe",
        )
        assertTrue(
            parLibelle.keys.none { it == "ordre" },
            "ordre (champ d'instance) ne devait pas s'offrir",
        )
        session.close()
    }

    @Test
    fun `constantes-enum---sujet-non-enum,-repli-honnete-sur-le-regime-de-nom`() {
        val session = session(listOf(jarStdlib()))
        val source = "fun demo(n: Int) {\n  when (n) {\n    r\n}\n}\n"
        val uri = "file:///pas-enum.kt"
        session.ouvrir(uri, source)
        val decalage = source.indexOf("r\n}") + 1
        val reponse = session.completionArrangee(uri, decalage)
        assertTrue(
            reponse.propositions.none { it.nature == NaturePropositionKotlin.ENUM },
            "un sujet non enum ne propose AUCUNE constante",
        )
        assertTrue(
            reponse.propositions.any { it.libelle == "return" },
            "le régime de nom reprend (mots-clés)",
        )
        session.close()
    }

    @Test
    fun `constantes-enum---corps-de-branche-muet`() {
        val session = session(listOf(jarStdlib()))
        val source =
            "enum class Couleur { ROUGE }\n" +
                "fun demo(c: Couleur) {\n  when (c) {\n    ROUGE -> 1 + r\n  }\n}\n"
        val uri = "file:///corps.kt"
        session.ouvrir(uri, source)
        val decalage = source.indexOf("1 + r") + 5
        val reponse = session.completionArrangee(uri, decalage)
        assertTrue(
            reponse.propositions.none { it.nature == NaturePropositionKotlin.ENUM },
            "le corps d'une branche n'est pas une position de condition",
        )
        assertTrue(reponse.propositions.any { it.libelle == "return" }, "le régime de nom reprend")
        session.close()
    }

    // ── Attributs d'annotation ─────────────────────────────────────────

    @Test
    fun `attributs-annotation---non-fournis,-insertion-avec-separateur`() {
        val session = session(listOf(jarDemo(), jarStdlib()))
        val source = "import demo.Anno\n@Anno(\nclass A\n"
        val uri = "file:///anno.kt"
        session.ouvrir(uri, source)
        val decalage = source.indexOf("(\nclass") + 1
        val reponse = session.completionArrangee(uri, decalage)
        assertEquals(GenreContexteKotlin.ATTRIBUTS_ANNOTATION, reponse.genre, "le contexte est classifié")
        val parLibelle = reponse.propositions.associateBy { it.libelle }
        val level = parLibelle["level ="]
        assertEquals(NaturePropositionKotlin.PROPRIETE, level?.nature, "la nature est propriété")
        assertEquals("level = ", level?.texteInsertion, "l'insertion porte le séparateur")
        assertEquals("demo.Anno", level?.detail, "le détail porte le FQCN de l'annotation")
        assertNotNull(parLibelle["nom ="], "l'autre attribut s'offre")
        assertTrue(
            reponse.propositions.none { it.nature == NaturePropositionKotlin.MOT_CLE },
            "le contexte est exclusif — aucun mot-clé ne devait suivre",
        )
        session.close()
    }

    @Test
    fun `attributs-annotation---attribut-deja-fourni-exclu`() {
        val session = session(listOf(jarDemo(), jarStdlib()))
        val source = "import demo.Anno\n@Anno(level = 1, no\nclass A\n"
        val uri = "file:///anno-exclus.kt"
        session.ouvrir(uri, source)
        val decalage = source.indexOf("no\nclass") + 2
        val libelles = session.completion(uri, decalage).map { it.libelle }
        assertTrue(
            "nom =" in libelles,
            "`nom =` devait être offert",
        )
        assertTrue(
            "level =" !in libelles,
            "`level =` déjà fourni ne devait pas revenir",
        )
        assertTrue(
            "helper =" !in libelles,
            "`helper` (méthode concrète) n'est pas un attribut",
        )
        session.close()
    }

    @Test
    fun `attributs-annotation---position-de-valeur-muette`() {
        val session = session(listOf(jarDemo(), jarStdlib()))
        val source = "import demo.Anno\n@Anno(level = \nclass A\n"
        val uri = "file:///anno-valeur.kt"
        session.ouvrir(uri, source)
        val decalage = source.indexOf("= \nclass") + 2
        val reponse = session.completionArrangee(uri, decalage)
        assertTrue(
            reponse.propositions.none { it.texteInsertion != null },
            "une valeur en cours de frappe n'offre AUCUN attribut",
        )
        assertTrue(
            reponse.propositions.any { it.libelle == "class" },
            "le régime de nom reprend (mots-clés)",
        )
        session.close()
    }

    @Test
    fun `attributs-annotation---annotation-non-resolue,-repli-honnete`() {
        val session = session(listOf(jarStdlib()))
        val source = "@Inconnu(c\nclass A\n"
        val uri = "file:///anno-inconnu.kt"
        session.ouvrir(uri, source)
        val decalage = source.indexOf("c\nclass") + 1
        val reponse = session.completionArrangee(uri, decalage)
        assertTrue(
            reponse.propositions.none { it.texteInsertion != null },
            "une annotation non résolue n'invente AUCUN attribut",
        )
        assertTrue(
            reponse.propositions.any { it.libelle == "class" },
            "le régime de nom reprend (mots-clés)",
        )
        session.close()
    }
}
