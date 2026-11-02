// CodeIDE — banc de cohérence P7 : rename sûr (module :kotlin, vague 0).
// Code NEUF jjoblab (banc de MESURE).
package jo.codeide.lsp.kotlin.coherence

import java.nio.file.Files
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * P7 — Rename sûr (banc de cohérence, vague 0 — miroir de la P7 Java).
 *
 * ## Ce que la propriété mesure
 *
 * Un symbole top-level déclaré dans A et utilisé dans B (fichiers
 * DISTINCTS) : le renommage rend ses emplacements (déclaration + usages
 * + segments d'import), les édits appliqués, le projet re-diagnostiqué et
 * confronté à l'oracle — **le renommage est sûr si le code reste
 * compilable et qu'aucun usage n'est oublié**.
 *
 * ## Assertions vs mesures (R5)
 *
 * - **ASSERTÉ** (contrat du cahier, fiche 048) : le renommage est
 *   multi-fichiers (≥ 2 URI parmi les emplacements) et ferroviaire
 *   (liste vide SANS preuve, jamais un renommage partiel silencieux) ;
 * - **mesuré** (rapport ; strict à la demande) : après application des
 *   édits, diagnostics ERREUR du moteur sur les fichiers renommés et
 *   verdict de l'oracle K2 sur les mêmes fichiers.
 */
class P7RenameSurTest {

    @Test
    fun `le-renommage-multi-fichiers-est-complet-et-le-code-reste-sain`() {
        val racine = Files.createTempDirectory("p7-rename")
        val fichierA = racine.resolve("Source.kt")
        val fichierB = racine.resolve("Client.kt")
        Files.writeString(
            fichierA,
            "package p7\n\nfun panier(): Int = 3\n",
        )
        Files.writeString(
            fichierB,
            "package p7\n\nfun total(): Int = panier() + panier() * 2\n",
        )
        val jars = SocleCoherence.assurerPlancherStdlib(SocleCoherence.jarsAutoCorpus())
        val session = SocleCoherence.monterSession(listOf(racine), jars)
        try {
            session.rafraichirSources()
            val uriA = fichierA.toUri().toString()
            val texteA = Files.readString(fichierA)
            session.ouvrir(uriA, texteA)
            val pointDeclaration = texteA.indexOf("panier") + 1
            val emplacements = session.renommage(uriA, pointDeclaration)
            session.fermer(uriA)

            // Contrat du cahier : multi-fichiers, complet, ferroviaire.
            val uris = emplacements.map { it.uri }.distinct()
            println("P7: emplacements=${emplacements.size}, uris=$uris")
            assertTrue(emplacements.isNotEmpty(), "le renommage d'une déclaration prouvée rend des emplacements")
            assertTrue(uris.size >= 2, "le renommage est multi-fichiers (contrat fiche 048) — reçu : $uris")

            // Application des édits (offsets par URI, en ordre inverse du document).
            val textes = mapOf(fichierA.toUri().toString() to Files.readString(fichierA), fichierB.toUri().toString() to Files.readString(fichierB))
            val mutés = textes.toMutableMap()
            for ((uri, liste) in emplacements.groupBy { it.uri }) {
                val texte = mutés[uri] ?: continue
                var résultat = texte
                for (emplacement in liste.sortedByDescending { it.plage.debut }) {
                    résultat = résultat.substring(0, emplacement.plage.debut) + "panierRenomme" + résultat.substring(emplacement.plage.fin)
                }
                mutés[uri] = résultat
            }
            for ((uri, texte) in mutés) {
                Files.writeString(java.nio.file.Path.of(java.net.URI(uri)), texte)
            }
            println("P7: fichiers réécrits=${mutés.size}")
        } finally {
            session.close()
        }

        // Re-diagnostic sur projet renommé (session NEUVE) + verdict oracle.
        val sessionVérif = SocleCoherence.monterSession(listOf(racine), jars)
        try {
            sessionVérif.rafraichirSources()
            var erreursMoteur = 0
            for (fichier in listOf(fichierA, fichierB)) {
                val uri = fichier.toUri().toString()
                val texte = Files.readString(fichier)
                sessionVérif.ouvrir(uri, texte)
                erreursMoteur += sessionVérif.diagnostics(uri).count { it.severite == jo.codeide.lsp.kotlin.api.SeveriteKotlin.ERREUR }
                sessionVérif.fermer(uri)
            }
            val oracle = SocleCoherence.oracleK2(listOf(fichierA, fichierB), jars)
            val erreursOracle = oracle.values.sumOf { it.size }
            println("P7: après renommage — erreurs moteur=$erreursMoteur, erreurs oracle=$erreursOracle")
            if (SocleCoherence.STRICT) {
                assertTrue(erreursMoteur == 0, "P7 strict : erreurs moteur=$erreursMoteur après renommage")
                assertTrue(erreursOracle == 0, "P7 strict : erreurs oracle=$erreursOracle après renommage (usages oubliés ?)")
            }
        } finally {
            sessionVérif.close()
        }
    }
}
