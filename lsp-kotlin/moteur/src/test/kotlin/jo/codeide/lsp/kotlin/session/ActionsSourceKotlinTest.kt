// CodeIDE — tests des actions de SOURCE et REFACTORING (module :kotlin,
// vague V-F — KC-10/KC-11, ADR 0131) : l'organisateur d'imports (tri
// stable idempotent, retrait des inutilisés PROUVÉS, alias/jokers
// conservés, doublons fusionnés) et l'extraction de variable bornée
// (intra-fonction, insertion avant l'instruction, remplacement par le
// nom). Code NEUF jjoblab (clean-room — cycle P5 sur chaque action :
// le document appliqué ne porte AUCUNE nouvelle erreur).
package jo.codeide.lsp.kotlin.session

import jo.codeide.lsp.kotlin.api.ClasspathKotlin
import jo.codeide.lsp.kotlin.api.EntreeClasspathKotlin
import jo.codeide.lsp.kotlin.api.GenreActionKotlin
import jo.codeide.lsp.kotlin.api.KindEntreeKotlin
import jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin
import jo.codeide.lsp.kotlin.api.SeveriteKotlin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * La vague V-F — les actions de source et refactoring (ADR 0131) :
 * `source.organizeImports` (KC-10), `refactor.extract` (KC-11 Q1/Q5),
 * kinds exacts, `codeAction/resolve` identité.
 */
class ActionsSourceKotlinTest {

    /** Le jar kotlin-stdlib du classpath de test. */
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
                EntreeClasspathKotlin(KindEntreeKotlin.JAR, jarStdlib().path, "vf:stdlib"),
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

    /** Le texte du document APRÈS application des éditions d'un plan. */
    private fun appliquer(source: String, plan: jo.codeide.lsp.kotlin.api.PlanActionKotlin): String {
        var texte = source
        // Les éditions en ordre INVERSE des plages (les plages du plan sont
        // en ordre du document — appliquer de la fin au début garde les
        // offsets valides ; les plages vides d'insertion aussi).
        val editions = plan.editions.flatMap { it.editions }.sortedByDescending { it.plage.fin }
        for (edition in editions) {
            texte = texte.substring(0, edition.plage.debut) + edition.texte + texte.substring(edition.plage.fin)
        }
        return texte
    }

    // ── KC-10 — source.organizeImports ──────────────────────────────

    @Test
    fun `i1i2---tri-stable,-retrait-des-inutilisés-prouvés,-idempotent`() {
        val session = session()
        val source =
            "import zebra.Dernier\n" +
                "import alpha.Premier\n" +
                "import beta.Inutilise\n" +
                "\n" +
                "fun demo(): Int {\n" +
                "    Premier()\n" +
                "    return Dernier().total()\n" +
                "}\n"
        val uri = "file:///organise.kt"
        session.ouvrir(uri, source)
        val plans = session.actions(uri, source.indexOf("zebra"))
        val plan = plans.firstOrNull { it.genre == GenreActionKotlin.ORGANISER_IMPORTS }
        assertNotNull(plan, "le plan d'organisation devait exister (bloc désordonné + inutilisé)")
        assertEquals("Organiser les imports", plan!!.titre)
        // L'application : tri alphabétique, l'inutilisé RETIRÉ.
        val organise = appliquer(source, plan)
        val bloc = organise.substring(0, organise.indexOf("\n\n"))
        assertEquals("import alpha.Premier\nimport zebra.Dernier", bloc, "tri stable + inutilisé retiré")
        assertTrue("Inutilise" !in organise, "l'import non utilisé (preuve lexicale) est retiré")
        assertTrue("Premier" in organise && "Dernier" in organise, "les utilisés sont conservés")
        // IDEMPOTENT : une seconde passe sur le document organisé ne
        // propose PLUS rien (zéro delta — I1).
        session.ouvrir(uri, organise)
        assertNull(
            session.actions(uri, organise.indexOf("alpha")).firstOrNull { it.genre == GenreActionKotlin.ORGANISER_IMPORTS },
            "le bloc organisé ne produit PLUS de plan (idempotence)",
        )
        session.close()
    }

    @Test
    fun `i4i5---doublon-fusionné,-alias-et-joker-conservés,-conflit-conservé`() {
        val session = session()
        val source =
            "import alpha.Premier\n" +
                "import alpha.Premier\n" +
                "import gamma.Outil as LegacyOutil\n" +
                "import delta.Etoile\n" +
                "import epsilon.*\n" +
                "import zeta.Autre\n" +
                "\n" +
                "fun demo(): Int {\n" +
                "    Premier()\n" +
                "    val e = LegacyOutil()\n" +
                "    return Etoile().total()\n" +
                "}\n"
        val uri = "file:///conserve.kt"
        session.ouvrir(uri, source)
        val plan = session.actions(uri, source.indexOf("gamma"))
            .firstOrNull { it.genre == GenreActionKotlin.ORGANISER_IMPORTS }
        assertNotNull(plan, "le plan devait exister (doublon + inutilisé zeta)")
        val organise = appliquer(source, plan!!)
        assertTrue("import alpha.Premier\nimport alpha.Premier" !in organise, "le doublon EXACT est fusionné (I4)")
        assertEquals(1, organise.split("import alpha.Premier").size - 1, "une seule directive Premier")
        assertTrue("import gamma.Outil as LegacyOutil" in organise, "l'ALIAS est conservé TEL QUEL (I5)")
        assertTrue("import epsilon.*" in organise, "le JOKER est conservé (I5)")
        assertTrue("import delta.Etoile" in organise, "l'import utilisé est conservé")
        assertTrue("import zeta.Autre" !in organise, "l'inutilisé non aliasé est retiré")
        session.close()
    }

    @Test
    fun `i2---nom-encore-écrit-mais-non-résolu---import-conservé`() {
        val session = session()
        // `Mystere` est ÉCRIT dans le corps mais ne résout nulle part :
        // la preuve d'usage est LEXICALE — jamais de suppression sur
        // doute (I2 bornes).
        val source =
            "import omega.Mystere\n" +
                "\n" +
                "fun demo() { Mystere }\n"
        val uri = "file:///lexique.kt"
        session.ouvrir(uri, source)
        val plan = session.actions(uri, source.indexOf("omega"))
            .firstOrNull { it.genre == GenreActionKotlin.ORGANISER_IMPORTS }
        assertNull(plan, "le nom ÉCRIT (même non résolu) conserve son import — jamais de suppression sur doute")
        session.close()
    }

    // ── KC-11 — refactor.extract (extraction de variable) ───────────

    @Test
    fun `q1q5---extraction-intra-fonction---val-insérée,-expression-remplacée`() {
        val session = session()
        val source =
            "fun demo(): Int {\n" +
                "    val base = 10\n" +
                "    return base * 2 + 1\n" +
                "}\n"
        val uri = "file:///extrait.kt"
        session.ouvrir(uri, source)
        // Le caret DANS la multiplication.
        val caret = source.indexOf("base * 2") + 2
        val plan = session.actions(uri, caret)
            .firstOrNull { it.genre == GenreActionKotlin.EXTRAIRE_VARIABLE }
        assertNotNull(plan, "l'extraction devait être proposée sur l'expression")
        assertEquals(GenreActionKotlin.EXTRAIRE_VARIABLE, plan!!.genre, "le genre du plan est refactor.extract")
        val extrait = appliquer(source, plan)
        assertTrue(
            "val extrait = base * 2" in extrait,
            "la val est insérée avant l'instruction porteuse : « $extrait »",
        )
        assertTrue(
            "return extrait + 1" in extrait,
            "l'expression est remplacée par le nom : « $extrait »",
        )
        session.close()
    }

    @Test
    fun `q5---hors-fonction-ou-expression-instruction---pas-de-plan`() {
        val session = session()
        // Un appel de premier niveau (instruction du document, PAS dans
        // un corps de fonction) : l'extraction est INTRA-FONCTION seule.
        val source = "val temoin = calculGlobal()\n"
        val uri = "file:///hors.kt"
        session.ouvrir(uri, source)
        val caret = source.indexOf("calculGlobal") + 2
        assertNull(
            session.actions(uri, caret).firstOrNull { it.genre == GenreActionKotlin.EXTRAIRE_VARIABLE },
            "hors d'un corps de fonction : PAS de plan (bornage Q5)",
        )
        session.close()
    }

    // ── Le cycle P5 sur chaque action ───────────────────────────────

    @Test
    fun `cycle-p5---l'application-organisée-ne-porte-aucune-nouvelle-erreur`() {
        val session = session()
        val source =
            "import zebra.Dernier\n" +
                "import beta.Inutilise\n" +
                "\n" +
                "fun demo(): Int = Dernier().total()\n"
        val uri = "file:///cycle.kt"
        session.ouvrir(uri, source)
        val avant = session.diagnostics(uri).filter { it.severite == SeveriteKotlin.ERREUR }
        val plan = session.actions(uri, source.indexOf("zebra"))
            .firstOrNull { it.genre == GenreActionKotlin.ORGANISER_IMPORTS }
        assertNotNull(plan, "le plan devait exister")
        val organise = appliquer(source, plan!!)
        session.ouvrir(uri, organise)
        val apres = session.diagnostics(uri).filter { it.severite == SeveriteKotlin.ERREUR }
        assertTrue(
            apres.size <= avant.size,
            "l'organisation ne crée AUCUNE nouvelle erreur (avant ${avant.size}, après ${apres.size})",
        )
        // Le diagnostic kt.unusedImport DISPARAÎT (le retrait est le
        // CORRECTIF cible — cycle quick-fix vert).
        val avertissements = session.diagnostics(uri).filter { it.code == jo.codeide.lsp.kotlin.api.CodeDiagnosticKotlin.IMPORT_INUTILISE }
        assertTrue(avertissements.isEmpty(), "l'import inutilisé signalé n'existe plus : $avertissements")
        session.close()
    }

    @Test
    fun `cycle-p5---l'extraction-appliquée-ne-casse-pas-le-document`() {
        val session = session()
        val source =
            "fun prix(): Int {\n" +
                "    val unite = 3\n" +
                "    return unite * 100\n" +
                "}\n"
        val uri = "file:///cycle-extrait.kt"
        session.ouvrir(uri, source)
        val avant = session.diagnostics(uri).filter { it.severite == SeveriteKotlin.ERREUR }
        val plan = session.actions(uri, source.indexOf("unite * 100") + 2)
            .firstOrNull { it.genre == GenreActionKotlin.EXTRAIRE_VARIABLE }
        assertNotNull(plan, "l'extraction devait être proposée")
        val extrait = appliquer(source, plan!!)
        session.ouvrir(uri, extrait)
        val apres = session.diagnostics(uri).filter { it.severite == SeveriteKotlin.ERREUR }
        assertTrue(
            apres.size <= avant.size,
            "l'extraction ne crée AUCUNE nouvelle erreur (avant ${avant.size}, après ${apres.size}) : « $extrait »",
        )
        session.close()
    }
}
