package jo.codeide.lsp.java.coherence

import jo.codeide.lsp.java.MoteurJava
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * P5 — Cycle quick-fix (prompt §4.3, écart G-004/H7 — « un quick-fix appliqué fait
 * disparaître son diagnostic sans en créer »).
 *
 * **Propriété** : pour toute action de code proposée sur un diagnostic : (a) après
 * application de ses éditions, le diagnostic CIBLE disparaît ; (b) AUCUNE nouvelle
 * erreur n'apparaît ; (c) le texte résultat reste compilable (zéro ERREUR sur des
 * fixtures par ailleurs valides).
 *
 * **Précision d'implémentation (vague V-A, alignée sur la propriété ci-dessus)** :
 * (b) compare les erreurs AVANT/APRÈS par SIGNATURE (sévérité + code + message,
 * plage ignorée — une erreur qui se DÉPLACE n'est pas nouvelle) ; (c) ne s'applique
 * qu'aux fixtures PAR AILLEURS VALIDES (une seule erreur avant : la cible) — une
 * fixture portant DEUX problèmes indépendants (frappe interrompue : point-virgule
 * + nom non résolu) ne peut être entièrement réparée par un fix qui n'en cible qu'un :
 * l'erreur sœur PRÉEXISTANTE n'est pas une « nouvelle erreur » (b), et la fixture
 * n'est pas « par ailleurs valide » (c). L'implémentation d'origine appliquait (c)
 * inconditionnellement — plus stricte que la propriété écrite, elle comptait des
 * violations hors de son périmètre (mesuré : Aventurier, correctif vague V-A).
 *
 * **Mode** : les trois fixtures couvertes par le cahier (import inutilisé,
 * point-virgule manquant, création de méthode — fiches 063/064) sont **assertées
 * dures** ; le balayage exhaustif (tous diagnostics × toutes actions d'un corpus
 * élargi) est **mesuré** et rapporté.
 */
class P5CycleQuickFixTest {

    /** Fixture 1 : import inutilisé (fiche 063 — suppression de ligne entière). */
    private val importInutilise = """
        package app;

        import java.util.List;

        public class Entrepot {
            private int casiers = 4;
        }
    """.trimIndent()

    /** Fixture 2 : point-virgule manquant (fiche 063 — insertion). */
    private val semiManquant = """
        package app;

        public class Compteur {
            private int valeur = 0;

            public void avancer() {
                valeur = valeur + 1
            }
        }
    """.trimIndent()

    /** Fixture 3 : méthode inexistante en APPEL-INSTRUCTION (fiche 064 —
     *  stub `private void` inséré, compilable par construction). */
    private val methodeInexistante = """
        package app;

        public class Rapport {
            public void titrer() {
                formater("activite");
            }
        }
    """.trimIndent()

    /**
     * La signature d'une erreur SANS plage — une erreur qui se DÉPLACE (édition
     * en amont) n'est pas une « nouvelle » erreur (propriété (b)).
     */
    private fun signatureErreur(d: jo.codeide.lsp.java.diagnostics.Diagnostic): String =
        "${d.severite}|${d.code}|${d.message}"

    /**
     * Applique le cycle complet sur une fixture : diagnostiquer → collecter les
     * actions → appliquer CHAQUE action sur la source → re-diagnostiquer.
     *
     * @return la liste de violations rencontrées (vide = propriété tenue).
     */
    private fun cycle(chemin: String, source: String, causeRacine: String): SocleCoherence.Rapport {
        val rapport = SocleCoherence.Rapport("P5", "cycle quick-fix (cible disparue, zéro nouvelle erreur, compilable)")
        val moteur = MoteurJava()
        val avant = moteur.diagnose(chemin, source)
        val actions = moteur.actionsCode(chemin, source, avant)
        val erreursAvant = SocleCoherence.erreurs(avant)
        val signaturesAvant = erreursAvant.groupingBy(::signatureErreur).eachCount()
        // « Fixture par ailleurs valide » : la SEULE erreur avant est la cible —
        // c'est le périmètre où la propriété exige un résultat COMPILABLE (c).
        val parAilleursValide = erreursAvant.size <= 1
        for (action in actions) {
            val cible = action.diagnostic
            val apresSource = jo.codeide.lsp.java.outils.appliquerEditions(source, action.editions)
            val apres = SocleCoherence.temoinNeuf().diagnose(chemin, apresSource)
            val erreursApres = SocleCoherence.erreurs(apres)

            // (c) compilable : zéro erreur — fixtures PAR AILLEURS VALIDES
            //     uniquement (propriété ci-dessus, précision vague V-A).
            if (parAilleursValide && erreursApres.isNotEmpty()) {
                rapport.violer(
                    "action « ${action.titre} » : ${erreursApres.size} erreur(s) après application " +
                        "(${erreursApres.joinToString { it.message }})",
                    causeRacine,
                )
                continue
            }

            // (b) AUCUNE erreur NOUVELLE : compte par signature, plage ignorée.
            val signaturesApres = erreursApres.groupingBy(::signatureErreur).eachCount()
            val nouvelles = signaturesApres.filter { (signature, nombre) ->
                (signaturesAvant[signature] ?: 0) < nombre
            }
            if (nouvelles.isNotEmpty()) {
                rapport.violer(
                    "action « ${action.titre} » : ${nouvelles.size} nouvelle(s) erreur(s) après application " +
                        "(${nouvelles.keys.joinToString()})",
                    causeRacine,
                )
                continue
            }

            // (a) la cible disparaît (même code d'ID, plus aucune occurrence).
            if (cible != null) {
                val residu = apres.filter {
                    it.code == cible.code && it.severite == cible.severite && it.message == cible.message
                }
                if (residu.isNotEmpty()) {
                    rapport.violer(
                        "action « ${action.titre} » : le diagnostic cible survit " +
                            "(${residu.joinToString { it.message }})",
                        causeRacine,
                    )
                    continue
                }
            }
            rapport.mesure()
        }
        if (actions.isEmpty()) {
            rapport.violer("aucune action proposée pour ${chemin.substringAfterLast('/')}", causeRacine)
        }
        return rapport
    }

    @Test
    fun `cycle sur import inutilise`() {
        val rapport = cycle("/app/Entrepot.java", importInutilise, "quick-fix import (fiche 063 — G-014)")
        // Dur (précédent fiche 063) : au moins une action, cycle propre — le
        // clore() AVANT l'assert imprime les violations si elles existent.
        rapport.clore("import inutilisé")
        assertTrue(rapport.nbViolations == 0, "le cycle quick-fix de l'import inutilisé doit être propre")
    }

    @Test
    fun `cycle sur point-virgule manquant`() {
        val rapport = cycle("/app/Compteur.java", semiManquant, "quick-fix point-virgule (fiche 063 — G-014)")
        rapport.clore("point-virgule manquant")
        assertTrue(rapport.nbViolations == 0, "le cycle quick-fix du point-virgule doit être propre")
    }

    @Test
    fun `cycle sur creation de methode`() {
        val rapport = cycle("/app/Rapport.java", methodeInexistante, "quick-fix création de méthode (fiche 064 — G-014)")
        rapport.clore("méthode inexistante (appel-instruction)")
        assertTrue(rapport.nbViolations == 0, "le cycle quick-fix de création de méthode doit être propre")
    }

    @Test
    fun `balayage des actions sur corpus elargi (mesure)`() {
        val rapport = SocleCoherence.Rapport("P5", "balayage élargi des cycles quick-fix")
        val corpus = listOf(
            "/app/Mutations.java" to """
                package app;

                import java.util.Map;

                public class Mutations {
                    private int cle = 1;

                    public int lire() {
                        introuvable();
                        return cle;
                    }
                }
            """.trimIndent(),
            "/app/Aventurier.java" to """
                package app;

                public class Aventurier {
                    private String nom = "Ada";

                    public String saluer() {
                        return inconnue
                    }
                }
            """.trimIndent(),
            // Appel-EXPRESSION dans un contexte typé : le correcteur de création
            // insère un stub de retour Object — l'erreur cible disparaît mais une
            // erreur de TYPE peut subsister (retour Object depuis une méthode
            // String). C'est une violation MESURÉE de la propriété « le texte
            // reste compilable » — baseline de l'écart G-014 (le fix ne tient pas
            // compte du type de retour attendu par le contexte).
            "/app/Redacteur.java" to """
                package app;

                public class Redacteur {
                    public String titrer() {
                        return formater("activite");
                    }
                }
            """.trimIndent(),
        )
        for ((chemin, source) in corpus) {
            val sousRapport = cycle(chemin, source, "action hors cas dorés (G-014 — catalogue 7 familles, fix non contextuel)")
            for (i in 0 until sousRapport.nbViolations) {
                rapport.violer("$chemin : violation #$i", "balayage élargi (G-014)")
            }
        }
        rapport.clore("${corpus.size} fixtures additionnelles")
    }
}
