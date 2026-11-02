package jo.codeide.lsp.kotlin.session

import java.nio.file.Files
import java.nio.file.Path
import jo.codeide.lsp.kotlin.api.ClasspathKotlin
import jo.codeide.lsp.kotlin.api.EntreeClasspathKotlin
import jo.codeide.lsp.kotlin.api.GenreContexteKotlin
import jo.codeide.lsp.kotlin.api.KindEntreeKotlin
import jo.codeide.lsp.kotlin.api.NaturePropositionKotlin
import jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Fiche 045 — SessionLangageKotlinK3Test : le contrat de session K3
 * (LSP_KOTLIN.md §5, K3 « symboles + complétion + survol + définition »)
 * — 21 comportements du cahier docs/cahier-specifications-kotlin/.
 *
 * Le montage éprouve le CHEMIN DE PRODUCTION D6 (moteur nourricier par le
 * SPI + session branchée + guichet attendu par sondage borné) : le jar
 * kotlin-stdlib RÉEL et le jar android AOSP RÉEL du classpath de test
 * servent d'entrées d'appelables et de types (extensions véritables,
 * chaîne TextView→View véritable) ; des racines sources temporaires
 * portent les définitions inter-fichiers.
 *
 * Chaque comportement porte le nom exact du test source (identifiant de
 * traçabilité du cahier).
 */
@Suppress("LargeClass")
class Fiche045SessionLangageKotlinK3Test {

    /** La racine temporaire des sources externes (définitions inter-fichiers). */
    @TempDir
    lateinit var racine: Path

    /** Le jar kotlin-stdlib du classpath de test. */
    private fun jarStdlib(): java.io.File =
        java.io.File(
            kotlin.jvm.internal.Intrinsics::class.java.protectionDomain
                .codeSource.location.toURI(),
        )

    /** Le jar android.jar AOSP du classpath de test. */
    private fun jarAndroid(): java.io.File =
        java.io.File(
            android.util.Log::class.java.protectionDomain
                .codeSource.location.toURI(),
        )

    /** Session branchée sur les jars et racines donnés (guichet attendu). */
    private fun session(
        jars: List<java.io.File> = listOf(jarStdlib()),
        racines: List<Path> = emptyList(),
    ): SessionLangageKotlinDocuments {
        val moteur = ServiceMoteurLangageKotlin.unique as MoteurLangageKotlinNoyau
        val classpath = ClasspathKotlin(
            entrees = jars.mapIndexed { i, jar ->
                EntreeClasspathKotlin(KindEntreeKotlin.JAR, jar.path, "f045:$i:${jar.name}")
            },
            racinesSources = racines.map { it.toAbsolutePath().toString() },
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

    // ── Complétion de noms ─────────────────────────────────────────────

    @Test
    fun `complétion-nom---locales,-membres,-mots-clés-par-préfixe`() {
        val session = session()
        val source =
            """
            class Vue {
              val titre: String = "a"
              fun demo(nom: String) {
                val compte = 1
                co
              }
            }
            """.trimIndent()
        val uri = "file:///vue.kt"
        session.ouvrir(uri, source)
        val point = source.indexOf("co\n") + 2
        val propositions = session.completionArrangee(uri, point)
        assertEquals(GenreContexteKotlin.NOM, propositions.genre, "le contexte est le régime de nom")
        val parLibelle = propositions.propositions.associateBy { it.libelle }
        // Locale en portée (nature VARIABLE, type écrit en détail).
        assertEquals(NaturePropositionKotlin.VARIABLE, parLibelle["compte"]?.nature, "la locale en portée")
        // Mot-clé du langage.
        assertNotNull(parLibelle["companion"], "le mot-clé companion se propose au préfixe co")
        // Le membre `titre` de l'englobante n'apparaît pas sous « co »
        // (filtré par le préfixe).
        assertTrue(parLibelle.keys.none { it == "titre" }, "titre ne porte pas le préfixe co")
        session.close()
    }

    @Test
    fun `complétion-nom---types-du-classpath-avec-auto-import`() {
        val session = session(jars = listOf(jarAndroid(), jarStdlib()))
        val source = "fun main() { val v: TextV }\n"
        val uri = "file:///main.kt"
        session.ouvrir(uri, source)
        val point = source.indexOf("TextV") + 5
        val propositions = session.completionArrangee(uri, point)
        val textView = propositions.propositions.firstOrNull { it.libelle == "TextView" }
        assertNotNull(textView, "TextView (android.jar réel) se propose au préfixe TextV")
        // Le FQCN voyage en DÉTAIL et en IMPORT AUTO-ÉDITABLE (l'hôte
        // arbitre par import).
        assertEquals("android.widget.TextView", textView.detail, "le détail porte le FQCN")
        assertEquals("android.widget.TextView", textView.fqcnImport, "l'import auto-éditable porte le FQCN")
        assertEquals(NaturePropositionKotlin.CLASSE, textView.nature, "la nature est classe")
        session.close()
    }

    // ── Complétion membre ──────────────────────────────────────────────

    @Test
    fun `complétion-membre---champ-et-méthode-le-long-de-la-chaîne-de-supertypes`() {
        val session = session(jars = listOf(jarAndroid(), jarStdlib()))
        val source =
            """
            import android.widget.TextView
            fun demo() {
              val v: TextView = creer()
              v.
            }
            fun creer(): TextView = creer()
            """.trimIndent()
        val uri = "file:///demo.kt"
        session.ouvrir(uri, source)
        val point = source.indexOf("v.") + 2
        val propositions = session.completionArrangee(uri, point)
        assertEquals(GenreContexteKotlin.MEMBRE, propositions.genre, "le contexte est le régime membre")
        val parLibelle = propositions.propositions.associateBy { it.libelle }
        // Champ DÉCLARÉ de TextView (propriété dérivée du getter Android
        // réel — `text` de getText(): CharSequence).
        val texte = parLibelle["text"]
        assertNotNull(texte, "text (dérivée du getter réel) se propose")
        assertEquals(NaturePropositionKotlin.PROPRIETE, texte.nature, "la nature est propriété")
        assertEquals("CharSequence", texte.detail, "le détail porte le type du getter")
        // Méthode HÉRITÉE de View (chaîne véritable d'android.jar).
        val requestLayout = parLibelle["requestLayout"]
        assertNotNull(requestLayout, "requestLayout (héritée de View) se propose")
        assertEquals(NaturePropositionKotlin.FONCTION, requestLayout.nature, "la nature est fonction")
        session.close()
    }

    @Test
    fun `complétion-membre---extensions-réelles-du-stdlib-sur-String`() {
        // stdlib SEULE : java.lang.String n'y vit pas (la vue JVM n'y
        // propose RIEN) — seules les EXTENSIONS DÉCODÉES du stdlib réel
        // s'offrent : la matière de ce comportement.
        val session = session(jars = listOf(jarStdlib()))
        val source = "fun demo() { val s = \"x\"; s. }\n"
        val uri = "file:///chaine.kt"
        session.ouvrir(uri, source)
        val point = source.indexOf("s.") + 2
        val propositions = session.completionArrangee(uri, point)
        val trim = propositions.propositions.firstOrNull { it.libelle == "trim" }
        assertNotNull(trim, "une extension RÉELLE du stdlib (trim) se propose sur String")
        assertEquals(NaturePropositionKotlin.FONCTION, trim.nature, "l'extension est une fonction")
        session.close()
    }

    @Test
    fun `complétion-membre---récepteur-de-type-PROJET-(déclarations-de-la-classe)`() {
        val session = session()
        val source =
            """
            class Modele {
              val nom: String = "a"
              fun afficher() { }
              fun demo() {
                val m = Modele()
                m.
              }
            }
            """.trimIndent()
        val uri = "file:///modele.kt"
        session.ouvrir(uri, source)
        val point = source.indexOf("m.") + 2
        val propositions = session.completionArrangee(uri, point)
        val parLibelle = propositions.propositions.associateBy { it.libelle }
        assertEquals(NaturePropositionKotlin.PROPRIETE, parLibelle["nom"]?.nature, "la propriété déclarée")
        assertEquals("String", parLibelle["nom"]?.detail, "le type écrit voyage en détail")
        assertEquals(NaturePropositionKotlin.FONCTION, parLibelle["afficher"]?.nature, "la méthode déclarée")
        session.close()
    }

    @Test
    fun `complétion-membre---récepteur-non-résolu,-aucune-proposition-(D3)`() {
        val session = session()
        val source = "fun demo(x: Inconnu) { x. }\n"
        val uri = "file:///inconnu.kt"
        session.ouvrir(uri, source)
        val point = source.indexOf("x.") + 2
        val propositions = session.completionArrangee(uri, point)
        assertTrue(
            propositions.propositions.isEmpty(),
            "un récepteur non résolu ne propose RIEN (D3) : ${propositions.propositions.map { it.libelle }}",
        )
        session.close()
    }

    @Test
    fun `complétion-import---sous-paquets-du-paquet-tapé`() {
        val session = session(jars = listOf(jarAndroid(), jarStdlib()))
        val source = "import android.wid\n"
        val uri = "file:///imports.kt"
        session.ouvrir(uri, source)
        val point = source.indexOf("wid") + 3
        val propositions = session.completionArrangee(uri, point)
        assertEquals(GenreContexteKotlin.IMPORT, propositions.genre, "le contexte est le régime d'import")
        val parLibelle = propositions.propositions.associateBy { it.libelle }
        val widget = parLibelle["widget"]
        assertNotNull(widget, "le sous-paquet widget du paquet tapé android se propose")
        assertEquals(NaturePropositionKotlin.PAQUET, widget.nature, "la nature est paquet")
        assertEquals("android.widget", widget.detail, "le détail porte le FQCN du paquet")
        session.close()
    }

    // ── Survol ─────────────────────────────────────────────────────────

    @Test
    fun `survol---locale-avec-type-écrit`() {
        val session = session()
        val source = "fun demo() { val titre: String = \"a\" }\n"
        val uri = "file:///survol.kt"
        session.ouvrir(uri, source)
        val survol = session.survol(uri, source.indexOf("titre") + 1)
        assertEquals("val titre: String", survol?.signature)
        session.close()
    }

    @Test
    fun `survol---fonction-du-fichier-avec-signature-complète`() {
        val session = session()
        val source = "fun creer(nom: String): Int = 1\n"
        val uri = "file:///fonction.kt"
        session.ouvrir(uri, source)
        val survol = session.survol(uri, source.indexOf("creer") + 1)
        assertEquals("fun creer(nom: String): Int", survol?.signature)
        session.close()
    }

    @Test
    fun `survol---membre-qualifié-hérité-(propriétaire-classpath)`() {
        val session = session(jars = listOf(jarAndroid(), jarStdlib()))
        val source =
            """
            import android.widget.TextView
            fun demo() {
              val v: TextView = creer()
              v.requestLayout()
            }
            fun creer(): TextView = creer()
            """.trimIndent()
        val uri = "file:///herit.kt"
        session.ouvrir(uri, source)
        val deplacement = source.indexOf("requestLayout") + 3
        val survol = session.survol(uri, deplacement)
        assertNotNull(survol, "le membre hérité de la chaîne véritable se survole")
        assertTrue(survol.signature.contains("requestLayout"), "la signature du membre")
        assertTrue(
            survol.signature.contains("android.view.View"),
            "le propriétaire déclarant (View, maillon de la chaîne réelle) accompagne : ${survol.signature}",
        )
        session.close()
    }

    @Test
    fun `survol---membre-déclaré-sur-récepteur-kotlin-String-via-l'alias-JVM-(ADR-0205)`() {
        // La VÉRITÉ JVM modélisée par les ENTRÉES (discipline pilotée par
        // les données, miroir du classpath réel stdlib + android.jar) :
        // AUCUN jar ne porte `kotlin.String` — un jar de FIXTURE porte le
        // maillon BINAIRE java.lang.String (méthode length) que le moteur
        // n'atteint que par l'ALIAS ADR 0205.
        val jarAlias = OutilsJarFixture.Batisseur()
            .classe("java.lang.String", "java.lang.Object", OutilsJarFixture.methode("length", "()I"))
            .ecrire(racine, "alias-jvm-string.jar")
        val session = session(jars = listOf(jarAlias, jarStdlib()))
        val source =
            """
            fun demo(s: String): Int {
              return s.length
            }
            """.trimIndent()
        val uri = "file:///alias-jvm.kt"
        session.ouvrir(uri, source)
        val deplacement = source.indexOf("length") + 2
        val survol = session.survol(uri, deplacement)
        assertNotNull(survol, "le membre de String se survole par l'alias JVM java.lang.String")
        assertTrue(survol.signature.contains("length"), "la signature du membre")
        assertTrue(survol.signature.contains("Int"), "le type de retour Int")
        session.close()
    }

    @Test
    fun `survol-usage-document-courant---KDoc-de-la-déclaration-vive-(ADR-0207)`() {
        val session = session()
        val source =
            """
            /**
             * Calcule le total pondéré.
             */
            fun total(x: Int): Int = x * 2

            fun appel(): Int = total(21)
            """.trimIndent()
        val uri = "file:///kdoc-usage.kt"
        session.ouvrir(uri, source)
        // Curseur sur l'USAGE de total dans appel() — la KDoc de la
        // déclaration VIVE du même document accompagne la signature.
        val survol = session.survol(uri, source.lastIndexOf("total") + 2)
        assertEquals("fun total(x: Int): Int", survol?.signature)
        assertNotNull(survol?.documentation, "la KDoc accompagne l'usage dans le document courant")
        assertTrue(
            survol?.documentation?.contains("Calcule le total pondéré") == true,
            "la KDoc rendue porte le texte du commentaire",
        )
        session.close()
    }

    @Test
    fun `survol-usage-déclaration-du-SCAN---KDoc-cross-fichier-fermée-(ADR-0208)`() {
        val bibli =
            """
            package outils

            /**
             * Triple la valeur.
             */
            fun triple(x: Int): Int = x * 3
            """.trimIndent()
        Files.createDirectories(racine.resolve("outils"))
        Files.writeString(racine.resolve("outils/Biblio.kt"), bibli)
        val appelant =
            """
            package outils

            fun essai(): Int = triple(2)
            """.trimIndent()
        val session = session(racines = listOf(racine))
        session.ouvrir("fixture://Essai.kt", appelant)
        session.rafraichirSources()
        val survol = session.survol("fixture://Essai.kt", appelant.indexOf("triple") + 2)
        assertNotNull(survol, "la déclaration du SCAN (fichier fermé) se survole")
        assertTrue(survol.signature.contains("triple"), "la signature de la déclaration scannée")
        assertNotNull(survol.documentation, "la KDoc tierce accompagne l'usage (lecture des racines)")
        assertTrue(
            survol.documentation.contains("Triple la valeur"),
            "la KDoc rendue porte le texte du fichier déclarant",
        )
        session.close()
    }

    @Test
    fun `survol-usage-déclaration-TIERCE-OUVERTE---KDoc-de-l'arbre-vif,-pas-du-disque-(ADR-0208)`() {
        // Disque : KDoc périmée — le document OUVERT porte la vérité.
        val bibliDisque =
            """
            package outils

            /**
             * Triple la valeur (version disque).
             */
            fun triple(x: Int): Int = x * 3
            """.trimIndent()
        val bibliVive =
            """
            package outils

            /**
             * Triple la valeur (version vivante).
             */
            fun triple(x: Int): Int = x * 3
            """.trimIndent()
        val appelant =
            """
            package outils

            fun essai(): Int = triple(2)
            """.trimIndent()
        Files.createDirectories(racine.resolve("outils"))
        val uriBibli = racine.resolve("outils/Biblio.kt")
        Files.writeString(uriBibli, bibliDisque)
        val session = session(racines = listOf(racine))
        session.ouvrir(uriBibli.toUri().toString(), bibliVive)
        session.ouvrir("fixture://Essai.kt", appelant)
        session.rafraichirSources()
        val survol = session.survol("fixture://Essai.kt", appelant.indexOf("triple") + 2)
        assertNotNull(survol, "la déclaration tierce ouverte se survole")
        assertTrue(
            survol.documentation?.contains("version vivante") == true,
            "la KDoc vient de l'arbre VIF du document tiers (jamais du disque périmé) : ${survol.documentation}",
        )
        session.close()
    }

    @Test
    fun `survol-usage-déclaration-tierce-SANS-KDoc---signature-seule-(garde-ADR-0208)`() {
        val bibli =
            """
            package outils

            fun triple(x: Int): Int = x * 3
            """.trimIndent()
        val appelant =
            """
            package outils

            fun essai(): Int = triple(2)
            """.trimIndent()
        Files.createDirectories(racine.resolve("outils"))
        Files.writeString(racine.resolve("outils/Biblio.kt"), bibli)
        val session = session(racines = listOf(racine))
        session.ouvrir("fixture://Essai.kt", appelant)
        session.rafraichirSources()
        val survol = session.survol("fixture://Essai.kt", appelant.indexOf("triple") + 2)
        assertNotNull(survol, "la déclaration tierce sans KDoc se survole")
        assertTrue(survol.signature.contains("triple"), "la signature seule")
        assertNull(survol.documentation, "sans KDoc tierce, pas de documentation")
        session.close()
    }

    @Test
    fun `survol-usage-déclaration-tierce-ILLISIBLE---signature-seule-(garde-D3-ADR-0208)`() {
        val bibli =
            """
            package outils

            /**
             * Triple la valeur.
             */
            fun triple(x: Int): Int = x * 3
            """.trimIndent()
        val appelant =
            """
            package outils

            fun essai(): Int = triple(2)
            """.trimIndent()
        Files.createDirectories(racine.resolve("outils"))
        val uriBibli = racine.resolve("outils/Biblio.kt")
        Files.writeString(uriBibli, bibli)
        val session = session(racines = listOf(racine))
        session.ouvrir("fixture://Essai.kt", appelant)
        session.rafraichirSources()
        // Premier survol : la KDoc tierce est servie (le fichier existe).
        val avant = session.survol("fixture://Essai.kt", appelant.indexOf("triple") + 2)
        assertNotNull(avant?.documentation, "KDoc tierce servie avant disparition")
        // Le fichier déclarant DISPARAÎT : la re-lecture échoue
        // honnêtement — signature seule, jamais d'exception.
        Files.delete(uriBibli)
        val survol = session.survol("fixture://Essai.kt", appelant.indexOf("triple") + 2)
        assertNotNull(survol, "la déclaration survit au snapshot du scan")
        assertNull(survol.documentation, "fichier déclarant illisible : signature seule")
        session.close()
    }

    // ── Définition ─────────────────────────────────────────────────────

    @Test
    fun `définition---locale-du-document-courant-(plage-du-nom)`() {
        val session = session()
        val source = "fun demo() { val titre = 1 }\n"
        val uri = "file:///def.kt"
        session.ouvrir(uri, source)
        val definitions = session.definition(uri, source.indexOf("titre") + 1)
        assertEquals(1, definitions.size, "un emplacement : $definitions")
        assertEquals(uri, definitions.single().uri)
        assertEquals(source.indexOf("titre"), definitions.single().plage.debut)
        assertEquals(source.indexOf("titre") + "titre".length, definitions.single().plage.fin)
        session.close()
    }

    @Test
    fun `définition---déclaration-d'un-AUTRE-fichier-des-racines-sources`() {
        Files.createDirectories(racine.resolve("demo"))
        val fichierAutre = racine.resolve("demo/Autre.kt")
        Files.writeString(fichierAutre, "package demo\n\nclass Autre { fun biz() { } }\n")
        val session = session(racines = listOf(racine))
        val source = "import demo.Autre\n\nfun main() { val a = Autre() }\n"
        val uri = "file:///principal.kt"
        session.ouvrir(uri, source)
        session.rafraichirSources()
        val definitions = session.definition(uri, source.indexOf("Autre()") + 1)
        assertEquals(1, definitions.size, "un emplacement : $definitions")
        assertEquals(fichierAutre.toUri().toString(), definitions.single().uri)
        val contenu = Files.readString(fichierAutre)
        // Plage = le NOM déclaré dans l'AUTRE fichier (« class Autre »).
        assertEquals(contenu.indexOf("Autre"), definitions.single().plage.debut)
        assertEquals(contenu.indexOf("Autre") + "Autre".length, definitions.single().plage.fin)
        session.close()
    }

    @Test
    fun `définition---cible-classpath,-liste-vide-(D3-documenté)`() {
        val session = session(jars = listOf(jarAndroid(), jarStdlib()))
        val source =
            """
            import android.widget.TextView
            fun creer(): TextView = creer()
            fun demo() { creer() }
            """.trimIndent()
        val uri = "file:///classpath.kt"
        session.ouvrir(uri, source)
        // `creer` est une fonction du PROJET → définition locale ; la
        // cible TYPE (TextView) elle-même rend vide : pas d'URI de
        // bibliothèque (D3 documenté).
        val definitions = session.definition(uri, source.indexOf("creer()") + 1)
        assertEquals(uri, definitions.single().uri)
        val surType = session.definition(uri, source.indexOf("TextView") + 1)
        assertTrue(surType.isEmpty(), "une cible classpath rend une liste vide (D3)")
        session.close()
    }

    // ── Tolérances ─────────────────────────────────────────────────────

    @Test
    fun `document-inconnu---requêtes-vides,-jamais-d'exception`() {
        val session = session()
        assertTrue(session.completion("file:///x.kt", 0).isEmpty())
        assertNull(session.survol("file:///x.kt", 0))
        assertTrue(session.definition("file:///x.kt", 0).isEmpty())
        session.close()
    }

    @Test
    fun `après-close---définirClasspath-et-requêtes-sont-des-no-ops`() {
        val session = session()
        session.close()
        session.definirClasspath(ClasspathKotlin(entrees = emptyList(), racinesSources = emptyList()))
        assertTrue(session.completion("file:///x.kt", 0).isEmpty())
        assertNull(session.survol("file:///x.kt", 0))
        assertTrue(session.definition("file:///x.kt", 0).isEmpty())
        session.close()
    }
}
