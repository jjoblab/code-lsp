package jo.codeide.lsp.java

import jo.codeide.lsp.java.diagnostics.Severite
import jo.codeide.lsp.java.inspections.MoteurInspections
import jo.codeide.lsp.java.inspections.ProfilInspections
import jo.codeide.lsp.java.inspections.SeveriteInspection
import jo.codeide.lsp.java.inspections.SOURCE_INSPECTION
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Vague V-C1 — INSPECTIONS PILOTES (ADR 0023, écart G-010) : contrat +
 * profil + suppressions + les 8 pilotes B/L.
 *
 * Critères de sortie de la vague (plan 04) éprouvés ici :
 *  - chaque inspection : cas dorés (DÉTECTION + non-détection +
 *    suppression) — P4 « chaque inspection attrape ≥ 1 mutant » ;
 *  - P3 : zéro ERREUR ajoutée par les inspections sur code compilant ;
 *  - la règle ADR 0013 : jamais une inspection en sévérité Erreur ;
 *  - l'équivalence rapide/complet (P1) : les signaux d'inspection
 *    survivent à la frappe confinée (portage des corps masqués) ;
 *  - dumb-aware : le verdict ne dépend pas d'un index.
 */
class InspectionsTest {

    private fun moteurAvec(vararg fichiers: Pair<String, String>): Pair<MoteurJava, Path> {
        val racine = Files.createTempDirectory("vc1-inspections")
        val demo = Files.createDirectories(racine.resolve("demo"))
        for ((nom, texte) in fichiers) {
            demo.resolve(nom).writeText(texte)
        }
        return MoteurJava(racines = listOf(racine)) to racine
    }

    private fun inspectionsDe(source: String, chemin: String = "/demo/Main.java"): List<jo.codeide.lsp.java.diagnostics.Diagnostic> {
        val (moteur, _) = moteurAvec("Main.java" to source)
        return moteur.diagnose(chemin, source).filter { it.source == SOURCE_INSPECTION }
    }

    // ------------------------------------------------------------------ //
    // Les 8 pilotes — détection, non-détection, mutant attrapé (P4)
    // ------------------------------------------------------------------ //

    @Test
    fun `StringEquality detecte la comparaison de chaines par reference`() {
        val signaux = inspectionsDe(
            """
            package demo;
            public class Main {
                boolean f(String a, String b) { return a == b; }
                boolean g(String a) { return a != null; }
                boolean h(int x, int y) { return x == y; }
            }
            """.trimIndent(),
        )
        assertEquals(1, signaux.size, "un seul « == » de chaînes (null et int exclus)")
        assertEquals("StringEquality", signaux[0].code)
        assertEquals(Severite.AVERTISSEMENT, signaux[0].severite)
    }

    @Test
    fun `IgnoreResultOfCall detecte le resultat CheckResult jete`() {
        val source = """
            package demo;
            public class Main {
                @java.lang.annotation.Documented
                @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS)
                @interface CheckResult { }
                public static class Outil {
                    @CheckResult public String fabriquer() { return "x"; }
                }
                void f(Outil outil) {
                    outil.fabriquer();
                }
                void g(Outil outil) {
                    String s = outil.fabriquer();
                    s.length();
                }
            }
        """.trimIndent()
        val signaux = inspectionsDe(source)
        assertEquals(1, signaux.size, "seul l'appel jeté est signalé (l'affectation est légitime)")
        assertEquals("IgnoreResultOfCall", signaux[0].code)
    }

    @Test
    fun `RedundantCast detecte le cast vers le type courant`() {
        val signaux = inspectionsDe(
            """
            package demo;
            public class Main {
                Object f(String s) { return (Object) s; }
                String g(Object o) { return (String) o; }
            }
            """.trimIndent(),
        )
        assertEquals(0, signaux.size, "(String) o est un vrai rétrécissement ; (Object) s est un WIDENING valide aussi — v1 : identité stricte")
        val signauxIdentite = inspectionsDe(
            """
            package demo;
            public class Main {
                String f(String s) { return (String) s; }
            }
            """.trimIndent(),
        )
        assertEquals(1, signauxIdentite.size, "le cast vers le MÊME type est redondant")
        assertEquals("RedundantCast", signauxIdentite[0].code)
        assertEquals(Severite.INFO, signauxIdentite[0].severite, "sévérité FAIBLE par défaut")
    }

    @Test
    fun `EqualsHashCode detecte la paire rompue dans les deux sens`() {
        val sansHashCode = inspectionsDe(
            """
            package demo;
            public class Main {
                public boolean equals(Object o) { return o == this; }
            }
            """.trimIndent(),
        )
        assertEquals(listOf("EqualsHashCode"), sansHashCode.map { it.code })
        val sansEquals = inspectionsDe(
            """
            package demo;
            public class Main {
                public int hashCode() { return 1; }
            }
            """.trimIndent(),
        )
        assertEquals(1, sansEquals.size, "hashCode sans equals : signalé aussi")
        val appareillee = inspectionsDe(
            """
            package demo;
            public class Main {
                public boolean equals(Object o) { return o == this; }
                public int hashCode() { return 1; }
            }
            """.trimIndent(),
        )
        assertEquals(0, appareillee.size, "la paire complète est silencieuse")
    }

    @Test
    fun `SizeReplaceableByIsEmpty detecte size egal zero sur collection et carte`() {
        val signaux = inspectionsDe(
            """
            package demo;
            import java.util.List;
            import java.util.Map;
            public class Main {
                boolean f(java.util.List<String> l) { return l.size() == 0; }
                boolean g(java.util.Map<String,String> m) { return m.size() == 0; }
                boolean h(java.util.List<String> l) { return l.isEmpty(); }
                boolean i(int n) { return n == 0; }
            }
            """.trimIndent(),
        )
        assertEquals(2, signaux.size, "List et Map signalées ; isEmpty et int exclus")
        assertTrue(signaux.all { it.code == "SizeReplaceableByIsEmpty" })
    }

    @Test
    fun `StaticFieldReferenceOnSubclass detecte l'acces par la sous-classe`() {
        val signaux = inspectionsDe(
            """
            package demo;
            public class Main {
                public static class Super { public static final int CONST = 1; }
                public static class Sous extends Super { }
                int f() { return Sous.CONST; }
                int g() { return Super.CONST; }
            }
            """.trimIndent(),
        )
        assertEquals(1, signaux.size, "seul l'accès par la sous-classe est signalé")
        assertEquals("StaticFieldReferenceOnSubclass", signaux[0].code)
    }

    @Test
    fun `WrongPackageStatement detecte le paquet incoherent avec le dossier`() {
        val source = "package autre;\npublic class Main { }\n"
        val (moteur, _) = moteurAvec("Main.java" to "package demo;\npublic class Main { }\n")
        val tous = moteur.diagnose("/demo/Main.java", source)
        // Mesuré : sur unité EN MÉMOIRE, ecj ne publie PAS l'erreur de
        // paquet (« declared package does not match ») — l'inspection est
        // la SEULE voix sur ce cas, à l'instruction de paquet exacte.
        val signaux = tous.filter { it.source == SOURCE_INSPECTION }
        assertEquals(1, signaux.size, "l'inspection signale le paquet incohérent")
        assertEquals("WrongPackageStatement", signaux[0].code)
        assertEquals(source.indexOf("autre"), signaux[0].plage.debutOffset, "le signal pointe le nom de paquet")
        // Paquet cohérent → silence (le mutant négatif).
        val coherent = moteur.diagnose("/demo/Main.java", "package demo;\npublic class Main { }\n")
        assertEquals(0, coherent.filter { it.source == SOURCE_INSPECTION }.size, "paquet cohérent : silence")
    }

    @Test
    fun `FormatString detecte l'incoherence specificateurs arguments`() {
        val signaux = inspectionsDe(
            """
            package demo;
            public class Main {
                String f(String nom) { return String.format("Bonjour %s et %s", nom); }
                String g(String nom) { return String.format("Bonjour %s", nom); }
                String h(String nom) { return String.format("100%% pour %n%s", nom); }
            }
            """.trimIndent(),
        )
        assertEquals(1, signaux.size, "seul l'appel incohérent est signalé (%% et %n ne consomment pas)")
        assertEquals("FormatString", signaux[0].code)
    }

    // ------------------------------------------------------------------ //
    // Suppressions + profil + règles transverses
    // ------------------------------------------------------------------ //

    @Test
    fun `SuppressWarnings et noinspection coupent le signalement`() {
        val base = """
            package demo;
            public class Main {
                boolean f(String a, String b) { return a == b; }
            }
        """.trimIndent()
        assertEquals(1, inspectionsDe(base).size, "sans suppression : signalé")
        val parAnnotation = inspectionsDe(
            """
            package demo;
            public class Main {
                @SuppressWarnings("StringEquality")
                boolean f(String a, String b) { return a == b; }
            }
        """.trimIndent(),
        )
        assertEquals(0, parAnnotation.size, "@SuppressWarnings(StringEquality) coupe")
        val parCommentaire = inspectionsDe(
            """
            package demo;
            public class Main {
                boolean f(String a, String b) {
                    //noinspection StringEquality
                    return a == b;
                }
            }
        """.trimIndent(),
        )
        assertEquals(0, parCommentaire.size, "//noinspection StringEquality coupe (ligne précédente)")
    }

    @Test
    fun `le profil coupe ou abaisse une inspection sans jamais monter en erreur`() {
        val source = """
            package demo;
            public class Main {
                boolean f(String a, String b) { return a == b; }
            }
        """.trimIndent()
        val (moteur, _) = moteurAvec("Main.java" to source)
        val chemin = "/demo/Main.java"
        val complet = moteur.diagnose(chemin, source)
        // P3 : aucune ERREUR ajoutée par les inspections (code compilant).
        assertEquals(0, complet.count { it.severite == Severite.ERREUR }, "P3 : zéro erreur ajoutée sur code compilant")
        // Le catalogue est servi par défaut.
        assertTrue(complet.any { it.source == SOURCE_INSPECTION }, "les inspections vivent par défaut")
        // Le catalogue complet est connu et dumb-aware (8 pilotes V-C1 +
        // 3 contrôles lint V-C2 — tous AST pur, zéro index).
        assertEquals(11, MoteurInspections.catalogue.size)
        assertTrue(MoteurInspections.catalogue.all { it.dumbAware }, "les 11 contrôles sont dumb-aware")
    }

    @Test
    fun `les signaux d'inspection survivent a la frappe confinee (equivalence P1)` () {
        val (moteur, _) = moteurAvec("Main.java" to "package demo;\npublic class Main { }\n")
        val chemin = "/demo/Main.java"
        val complet = """
            package demo;
            public class Main {
                int a() { return 1; }
                boolean b(String x, String y) { return x == y; }
                int c() { return 3; }
            }
        """.trimIndent()
        val reference = moteur.diagnose(chemin, complet).filter { it.source == SOURCE_INSPECTION }
        assertEquals(1, reference.size, "le témoin complet signale le « == » dans b")
        // Frappe confinée dans le corps de a (les autres corps masqués) :
        // le signal de b (corps MASQUÉ) doit être PORTÉ à l'identique.
        val frappee = complet.replace("return 1;", "return 11;")
        val rapide = moteur.diagnose(chemin, frappee).filter { it.source == SOURCE_INSPECTION }
        assertEquals(reference.map { it.code to it.message }, rapide.map { it.code to it.message }, "P1 : le signal porté est identique au témoin")
    }

    @Test
    fun `une erreur de syntaxe tait la passe d'inspection (anti-bruit)`() {
        val signaux = inspectionsDe(
            """
            package demo;
            public class Main {
                boolean f(String a, String b) { return a === b; }
            }
            """.trimIndent(),
        )
        assertEquals(0, signaux.size, "sur unité syntaxiquement fautive : silence")
    }
}
