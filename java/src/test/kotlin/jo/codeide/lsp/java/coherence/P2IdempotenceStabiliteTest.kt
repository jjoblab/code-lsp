package jo.codeide.lsp.java.coherence

import jo.codeide.lsp.java.MoteurJava
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * P2 — Idempotence et stabilité des diagnostics (prompt §4.3, écart G-002).
 *
 * **Propriétés** :
 *  - (a) déterminisme : un moteur NEUF diagnostique deux fois la même source
 *    exactement de la même façon (froid/froid) ;
 *  - (b) chaud/froid : un moteur ayant un HISTORIQUE d'éditions rend, sur la
 *    source finale, les mêmes diagnostics qu'un moteur neuf (le mémo ne doit
 *    rien changer au fond) ;
 *  - (c) neutralité des blancs/commentaires : insérer un commentaire ou des
 *    espaces ne change PAS les diagnostics (hors décalage des plages).
 *
 * **Mode** : (a) est asserté dur (déterminisme fondamental) ; (b) est mesuré
 * (c'est la même propriété que P1 côté mémo) ; (c) est asserté dur sur
 * l'empreinte hors plages + le décalage exact des plages.
 */
class P2IdempotenceStabiliteTest {

    private val source = """
        package app;

        import java.util.ArrayList;
        import java.util.List;

        public class Carnet {
            private List<String> pages = new ArrayList<>();

            void ecrire(String ligne) {
                pages.add(ligne);
            }
        }
    """.trimIndent()

    @Test
    fun `determinisme a froid`() {
        val premier = SocleCoherence.temoinNeuf().diagnose("/app/Carnet.java", source)
        val second = SocleCoherence.temoinNeuf().diagnose("/app/Carnet.java", source)
        assertEquals(
            premier.map(SocleCoherence::empreinte),
            second.map(SocleCoherence::empreinte),
            "deux moteurs neufs doivent rendre exactement les mêmes diagnostics",
        )
    }

    @Test
    fun `chaud contre froid apres historique`() {
        val rapport = SocleCoherence.Rapport("P2", "stabilité chaud/froid après historique d'éditions")
        val moteur = MoteurJava()

        // Historique : quelques éditions confinées puis retour à la source d'origine.
        val variants = listOf(
            source.replace("pages.add(ligne);", "pages.add(ligne.trim());"),
            source.replace("void ecrire(String ligne) {", "void ecrire(final String ligne) {"),
            source,
        )
        variants.forEach { variante ->
            moteur.diagnose("/app/Carnet.java", variante)
            val temoin = SocleCoherence.temoinNeuf().diagnose("/app/Carnet.java", variante)
            val differences = SocleCoherence.differences(
                moteur.diagnose("/app/Carnet.java", variante),
                temoin,
            )
            if (differences.isEmpty()) {
                rapport.mesure()
            } else {
                differences.forEach {
                    rapport.violer(it, "mémo/état chaud divergent du froid (G-002 — même cause que P1)")
                }
            }
        }
        rapport.clore("3 éditions dont retour à l'original")
    }

    @Test
    fun `neutralite d un commentaire insere`() {
        val moteur = SocleCoherence.temoinNeuf()
        val avant = moteur.diagnose("/app/Carnet.java", source)

        // Insertion d'un commentaire neutre (sans task tag) au début de la classe.
        val ancre = source.indexOf("private List<String> pages")
        val insertion = "// note de lecture\n    "
        val apresSource = source.substring(0, ancre) + insertion + source.substring(ancre)
        val apres = SocleCoherence.temoinNeuf().diagnose("/app/Carnet.java", apresSource)

        // Même nombre, mêmes sévérités/codes/messages.
        assertEquals(
            avant.map { "${it.severite}|${it.code}|${it.message}" },
            apres.map { "${it.severite}|${it.code}|${it.message}" },
            "un commentaire neutre ne doit pas changer les diagnostics (hors plages)",
        )
        // Plages : décalées exactement du delta pour les diagnostics après l'ancre.
        val delta = insertion.length
        avant.zip(apres).forEach { (a, b) ->
            if (a.plage.debutOffset >= ancre) {
                assertEquals(
                    a.plage.debutOffset + delta,
                    b.plage.debutOffset,
                    "la plage après l'insertion doit être décalée du delta exact",
                )
            } else {
                assertEquals(a.plage.debutOffset, b.plage.debutOffset, "la plage avant l'insertion ne bouge pas")
            }
        }
    }

    @Test
    fun `neutralite d espaces inseres`() {
        val avant = SocleCoherence.temoinNeuf().diagnose("/app/Carnet.java", source)
        val ancre = source.indexOf("void ecrire")
        val apresSource = source.substring(0, ancre) + "    \n    " + source.substring(ancre)
        val apres = SocleCoherence.temoinNeuf().diagnose("/app/Carnet.java", apresSource)
        assertEquals(
            avant.map { "${it.severite}|${it.code}|${it.message}" },
            apres.map { "${it.severite}|${it.code}|${it.message}" },
            "des espaces ne doivent pas changer les diagnostics (hors plages)",
        )
    }
}
