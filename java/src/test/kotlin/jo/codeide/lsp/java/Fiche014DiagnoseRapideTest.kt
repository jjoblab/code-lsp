package jo.codeide.lsp.java

import jo.codeide.lsp.java.diagnostics.Diagnostic
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Fiche 014 — EcjFastDiagnoseTest : diagnostics incrémentaux par corps de
 * méthode (P6). Oracle d'équivalence stricte : chaque tour de frappe est
 * joué sur le moteur sous test ET sur un moteur témoin NEUF (mémo vide) —
 * les listes doivent être STRICTEMENT identiques (taille, offsets, sévérité,
 * message, code) : le fast-path ne peut que gagner du temps, jamais perdre
 * ni inventer un diagnostic.
 */
class Fiche014DiagnoseRapideTest {

    /** Fixture riche du cahier : classe Main du paquet app, marqueur « ⌂ ». */
    private val base = """
        package app;

        import java.util.List;

        public class Main {
            private int champPrive = 1;

            void corpsVide() { }

            void court() { int x = 1; }

            int nonVoid() { return 42; }

            void edite() {⌂}

            void utiliseListEtChamp() { List<Object> l = null; champPrive = l.size(); }

            void avecErreur() { methodeInexistante(); }

            void jamaisAppelee() { int localeMorte = 42; }

            void signatureCasse(TypeInconnu mauvaisNom) { int essai = mauvaisNom2; }
        }
    """.trimIndent()

    /** Témoin neuf par tour : moteur vierge, diagnose complet. */
    private fun temoin(source: String): List<Diagnostic> = MoteurJava().diagnose("/app/Main.java", source)

    @Test
    fun frappesDansUnCorpsEquivalenceEtFastPath() {
        val moteur = MoteurJava()
        val marqueur = base.indexOf('⌂')
        fun frappe(contenu: String): String =
            base.substring(0, marqueur) + contenu + base.substring(marqueur + 1)

        val tours = listOf("", " int localeMorte2 = 2;", " int x = ; ", " int x = 3; System.out.println(x);")
        var attenduFull = 0
        var attenduFast = 0
        for (tour in tours) {
            val source = frappe(tour)
            val reference = temoin(source)
            val resultats = moteur.diagnose("/app/Main.java", source)
            assertEquals(reference.size, resultats.size, "taille identique au témoin (tour « $tour »)")
            assertEquals(
                reference.map { listOf(it.plage.debutOffset, it.plage.finOffset, it.severite, it.message, it.code) },
                resultats.map { listOf(it.plage.debutOffset, it.plage.finOffset, it.severite, it.message, it.code) },
                "équivalence stricte (offsets/sévérité/message/code) au tour « $tour »",
            )
            if (tour.isEmpty()) attenduFull++ else attenduFast++
            assertEquals(attenduFull, moteur.fullDiagnoseCount, "fullDiagnoseCount au tour « $tour »")
            assertEquals(attenduFast, moteur.fastDiagnoseHits, "fastDiagnoseHits au tour « $tour »")
        }
        assertEquals(1, moteur.fullDiagnoseCount, "aucun diagnose complet supplémentaire sur toute la session")
    }

    @Test
    fun editionPuisSautDansUnAutreCorpsEquivalence() {
        val moteur = MoteurJava()
        val marqueur = base.indexOf('⌂')
        fun frappe(contenu: String): String =
            base.substring(0, marqueur) + contenu + base.substring(marqueur + 1)

        // Diagnose initial.
        moteur.diagnose("/app/Main.java", base)
        // Frappe dans edite().
        val s1 = frappe(" int a = 1;")
        val r1 = moteur.diagnose("/app/Main.java", s1)
        assertEquals(r1, temoin(s1), "équivalence tour 1")
        assertEquals(1, moteur.fastDiagnoseHits)
        // Saut dans jamaisAppelee() : localeMorte 42→43 (corps masqué au tour précédent).
        val s2 = s1.replace("int localeMorte = 42;", "int localeMorte = 43;")
        val r2 = moteur.diagnose("/app/Main.java", s2)
        assertEquals(r2, temoin(s2), "équivalence tour 2 (corps d'arrivée recalculé frais)")
        assertEquals(2, moteur.fastDiagnoseHits)
        // Retour dans signatureCasse() : renommage du corps (mauvaisNom2→mauvaisNom3).
        val s3 = s2.replace("int essai = mauvaisNom2;", "int essai = mauvaisNom3;")
        val r3 = moteur.diagnose("/app/Main.java", s3)
        assertEquals(r3, temoin(s3), "équivalence tour 3 (problèmes du corps quitté PORTÉS)")
        assertEquals(3, moteur.fastDiagnoseHits, "fastDiagnoseHits = 3 après le retour")
    }

    @Test
    fun memeSourceMemoSansReparse() {
        val moteur = MoteurJava()
        val marqueur = base.indexOf('⌂')
        val source = base.substring(0, marqueur) + " int x = 1;" + base.substring(marqueur + 1)
        val premier = moteur.diagnose("/app/Main.java", source)
        val second = moteur.diagnose("/app/Main.java", source)
        assertEquals(premier.size, second.size, "mêmes tailles")
        assertEquals(
            premier.map { it.message to it.plage.debutOffset },
            second.map { it.message to it.plage.debutOffset },
            "mêmes messages et offsets de début",
        )
        assertEquals(1, moteur.fastDiagnoseHits, "mémo : un fast hit")
        assertEquals(1, moteur.fullDiagnoseCount, "rien de re-parsé")
    }

    @Test
    fun editionsStructurellesRepliSurDiagnoseComplet() {
        val moteur = MoteurJava()
        val marqueur = base.indexOf('⌂')
        fun frappe(contenu: String): String =
            base.substring(0, marqueur) + contenu + base.substring(marqueur + 1)

        moteur.diagnose("/app/Main.java", base)
        val editions = listOf(
            // (1) suppression de l'accolade fermante du corps édité.
            frappe(" int a = 1;").replaceFirst("void edite() { int a = 1;}", "void edite() { int a = 1;"),
            // (2) renommage d'un paramètre de SIGNATURE.
            base.replace("TypeInconnu mauvaisNom)", "TypeInconnu meilleurNom)"),
            // (3) édition d'un initialiseur de champ (hors corps).
            base.replace("private int champPrive = 1;", "private int champPrive = 2;"),
            // (4) remplacement à cheval sur deux corps (fusion edite/utiliseListEtChamp).
            frappe(" int b = 2;").replace(
                "void edite() { int b = 2; }\n\n            void utiliseListEtChamp() { List<Object> l = null; champPrive = l.size(); }",
                "void edite() { int b = 2; void utiliseListEtChamp() { List<Object> l = null; champPrive = l.size(); }",
            ),
        )
        var attenduFull = 1
        for (edition in editions) {
            val resultats = moteur.diagnose("/app/Main.java", edition)
            assertEquals(temoin(edition), resultats, "équivalence stricte sur édition structurelle")
            attenduFull++
            assertEquals(attenduFull, moteur.fullDiagnoseCount, "repli complet obligatoire : $attenduFull")
        }
        assertEquals(0, moteur.fastDiagnoseHits, "AUCUN fast-path sur les éditions structurelles")
    }

    @Test
    fun corpsMasquesPasDeUnusedFallacieux() {
        // Précondition : la base ne signale pas List/champPrive comme « never used ».
        val baseDiagnostics = temoin(base)
        assertTrue(
            baseDiagnostics.none { it.message.contains("never used") || it.message.contains("is not used") },
            "précondition : la base ne signale pas List/champ comme jamais utilisés",
        )
        val moteur = MoteurJava()
        moteur.diagnose("/app/Main.java", base)
        val marqueur = base.indexOf('⌂')
        val frappee = base.substring(0, marqueur) + " int y = 2;" + base.substring(marqueur + 1)
        val resultats = moteur.diagnose("/app/Main.java", frappee)
        assertTrue(
            resultats.none {
                (it.message.contains("never used") || it.message.contains("is not used")) &&
                    (it.message.contains("List") || it.message.contains("champPrive"))
            },
            "AUCUN diagnostic « unused » fallacieux sur List/champPrive",
        )
        assertEquals(temoin(frappee), resultats, "équivalence stricte avec la référence")
    }

    @Test
    fun killSwitchDeclareDansLaClasse() {
        // Contrat de présence du flag (test réflexif, fiche 014) : le champ
        // existe, statique final de type boolean, lisible et non nul.
        val champ = MoteurJava::class.java.getDeclaredField("FAST_DIAGNOSE_DISABLED")
        assertEquals(java.lang.Boolean.TYPE, champ.type, "le type du champ est boolean")
        kotlin.test.assertNotNull(champ.get(null), "valeur lisible non nulle")
    }

    @Test
    fun aucunProblemeFraisDansUnCorpsMasque() {
        val moteur = MoteurJava()
        moteur.diagnose("/app/Main.java", base)
        val marqueur = base.indexOf('⌂')
        val frappee = base.substring(0, marqueur) + " methodeInexistante2();" + base.substring(marqueur + 1)
        val resultats = moteur.diagnose("/app/Main.java", frappee)
        assertEquals(1, moteur.fastDiagnoseHits, "fastDiagnoseHits = 1")
        assertTrue(
            resultats.any { it.message.contains("methodeInexistante2") },
            "l'erreur fraîche « methodeInexistante2 » EST publiée (corps édité parsé frais)",
        )
        // Équivalence stricte : l'erreur de « methodeInexistante » (corps masqué)
        // est PORTÉE avec les mêmes offsets — aucun problème frais inventé.
        val reference = temoin(frappee)
        assertEquals(
            reference.map { listOf(it.plage.debutOffset, it.plage.finOffset, it.severite, it.message, it.code) },
            resultats.map { listOf(it.plage.debutOffset, it.plage.finOffset, it.severite, it.message, it.code) },
            "équivalence stricte (portage à offsets identiques)",
        )
    }

    @Test
    fun corpsHoteLongIdentifieStructurellementPasParIdentite() {
        // Régression v1.1.1 (retour appareil du 30/10 : « l'éditeur n'affiche rien
        // quand je tape, aucun signe des fonctionnalités du serveur ») : quand le
        // corps hôte est ASSEZ LONG pour être masquable (>= SYNTHETIQUE.length),
        // sourceMasquee comparait l'hôte par IDENTITÉ (===) — or l'hôte rendu par
        // preuveDeConfinement est une instance de SON scan interne, et
        // diagnostiquerRapide re-scanne la source : DEUX scans distincts produisent
        // des instances DISTINCTES. L'hôte n'était jamais reconnu : il était masqué
        // par le synthétique « throw new RuntimeException(); » comme les autres
        // corps — la frappe de l'utilisateur était EFFACÉE du texte diagnostiqué,
        // et l'erreur fraîche du corps édité n'apparaissait JAMAIS (0 diagnostic).
        // Les corps de la fixture historique sont tous plus courts que le
        // synthétique (laissés verbatim) : c'est pourquoi ce défaut a traversé la
        // CI de la v1.1.0. Ici, le corps hôte dépasse volontairement la longueur
        // du synthétique — la voie masquable est exercée.
        val sourceValide = """
            package app;

            public class LongCorps {
                void edite() { System.out.println("une ligne assez longue pour etre masquee"); }
            }
        """.trimIndent()
        val sourceCassee = sourceValide.replace("System.out.println", "Systeme.out.printl")
        val moteur = MoteurJava()
        moteur.diagnose("/app/LongCorps.java", sourceValide) // état initial (diagnose complet)
        val resultats = moteur.diagnose("/app/LongCorps.java", sourceCassee) // frappe confinée, corps masquable
        assertEquals(1, moteur.fastDiagnoseHits, "le fast-path est bien emprunté (corps confiné)")
        // Témoin AU MÊME CHEMIN que le moteur sous test (le helper [temoin] diagnostique
        // à « /app/Main.java » : le nom de fichier du témoin doit correspondre à la
        // classe de CETTE fixture — sinon un « must be defined in its own file » parasite
        // la référence).
        val reference = MoteurJava().diagnose("/app/LongCorps.java", sourceCassee)
        assertEquals(reference.size, resultats.size, "l'erreur fraîche « Systeme » doit PARAÎTRE (taille du témoin)")
        assertEquals(
            reference.map { listOf(it.plage.debutOffset, it.plage.finOffset, it.severite, it.message, it.code) },
            resultats.map { listOf(it.plage.debutOffset, it.plage.finOffset, it.severite, it.message, it.code) },
            "équivalence stricte au témoin neuf (l'hôte diagnostiqué INTACT)",
        )
    }
}
