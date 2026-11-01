package jo.codeide.lsp.java

import java.nio.file.Files
import kotlin.io.path.writeText
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * COMPLÉMENT DE CATALOGUE — TRANSCRIPT DE LATENCE de la liste
 * `codeAction` AVEC les nouvelles familles (ADR 0032 : réordonnancement de
 * paramètres au caret, quickfix d'ajout de paramètres sur l'appel fautif —
 * critère du plan 04 §V-D : liste p95 < 30 ms JVM ; R5 : aucune valeur
 * fabriquée, les chiffres ci-dessous sont MESURÉS au banc du bac à sable).
 *
 * Le profil mesuré : la requête intentions COMPLÈTE (le déplacement du
 * paramètre au caret) ET la passe quickfix sur les diagnostics du document
 * (l'ajout de paramètres sur l'appel fautif) — les deux ne portent que des
 * clés + positions, les gardes multi-fichiers vivent au resolve (ADR
 * 0027 (f)).
 */
class LatenceComplementCatalogueTest {

    @Test
    fun `transcript de latence de la liste codeAction avec les familles du complement (p95 cible sous 30 ms JVM)`() {
        val source = """
            package demo;
            public class Main {
                String formater(int nombre, String texte) { return texte + nombre; }
                String appel() { return formater(3, "x"); }
                int total(int a) { return a; }
                int fautif() { return total(3, "x", 4L); }
            }
        """.trimIndent()
        val racine = Files.createTempDirectory("cat-latence")
        Files.createDirectories(racine.resolve("demo"))
        racine.resolve("demo").resolve("Main.java").writeText(source)
        val moteur = MoteurJava(racines = listOf(racine))
        val chemin = "demo/Main.java"

        // Les positions CIBLÉES : CARET sur chaque paramètre déplaçable
        // (réordonnancement) et le DIAGNOSTIC de l'appel fautif (ajout).
        val positionsParametre = listOf(
            source.indexOf("nombre"),
            source.indexOf("texte"),
        )
        val diagnostics = moteur.diagnose(chemin, source)

        // Échauffement (le cache de parse et le mémo se remplissent) — et
        // preuve que les familles du complément sont bien SERVIES.
        var deplacements = 0
        var ajouts = 0
        for (position in positionsParametre) {
            deplacements += moteur.intentions(chemin, source, position, position)
                .count { it.kind == "refactor.rewrite.signature" && it.titre.startsWith("Déplacer") }
        }
        ajouts = moteur.actionsCode(chemin, source, diagnostics)
            .count { (it.commande as? jo.codeide.lsp.java.actionscode.ModCommande.Differee)?.cle == jo.codeide.lsp.java.actionscode.CorrecteurAjoutParametres.CLE_AJOUTER_PARAMETRES }
        assertTrue(deplacements >= 2, "les déplacements sont servis sur les paramètres : $deplacements")
        assertEquals(1, ajouts, "l'ajout de paramètres est servi sur l'appel fautif")

        val durees = ArrayList<Long>()
        for (tour in 0 until 12) {
            for (position in positionsParametre) {
                val debut = System.nanoTime()
                moteur.intentions(chemin, source, position, position)
                durees.add((System.nanoTime() - debut) / 1_000)
            }
            val debutQuickfix = System.nanoTime()
            moteur.actionsCode(chemin, source, diagnostics)
            durees.add((System.nanoTime() - debutQuickfix) / 1_000)
        }
        val triees = durees.sorted()
        val p50 = triees[triees.size / 2]
        val p95 = triees[(triees.size * 95 / 100).coerceAtMost(triees.size - 1)]
        val max = triees.last()
        println("LATENCE-COMPLEMENT nb=${durees.size} p50=${p50}µs p95=${p95}µs max=${max}µs")
        assertTrue(p95 < 30_000, "liste p95 < 30 ms JVM (mesuré ${p95} µs)")
    }
}
