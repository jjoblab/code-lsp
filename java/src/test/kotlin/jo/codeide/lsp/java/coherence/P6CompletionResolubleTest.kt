package jo.codeide.lsp.java.coherence

import jo.codeide.lsp.java.MoteurJava
import jo.codeide.lsp.java.completion.GenreContexte
import jo.codeide.lsp.java.interne.NatureItem
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * P6 — Complétion « résoluble » (prompt §4.3, écart G-004/H9).
 *
 * **Propriété** : toute suggestion insérée à une position (via son texte
 * d'insertion + ses éditions additionnelles, comme un client LSP standard)
 * produit un code **sans erreur de résolution nouvelle** — une suggestion
 * inexistante ou inaccessible dans le contexte est une violation.
 *
 * **Périmètre d'honnêteté** : la propriété porte sur la **résolvabilité
 * sémantique** des suggestions ; la PONCTUATION (parenthèses des méthodes,
 * point-virgule) est une politique client hors contrat moteur (fiche 020 :
 * libellé = nom simple). Les positions testées terminent donc par un `;`
 * déjà présent (l'insertion complète l'instruction), et :
 *  - les natures sémantiques sans ponctuation (CHAMP, VARIABLE, PARAMETRE,
 *    CONSTANTE_ENUM, CLASSE, INTERFACE) sont insérées **telles quelles** —
 *    asserté dur ;
 *  - les méthodes sont insérées avec la convention standard `label()` et les
 *    erreurs résultantes sont **classées** (résolution = violation ; type/
 *    syntaxe = information) — mesuré ;
 *  - MOT_CLE/PAQUET/EXTRAIT sont hors périmètre (syntaxe/structure).
 */
class P6CompletionResolubleTest {

    /** Position NOM : locals, champ et paramètre en portée, `;` présent. */
    private val sourceNom = """
        package app;

        public class Salle {
            private String prenom = "Ada";

            void saluer(String titre) {
                int age = 36;
                var x = ;
            }
        }
    """.trimIndent()

    /** Position MEMBRE : champ public de String en réception, `;` présent. */
    private val sourceMembre = """
        package app;

        public class SalleMembre {
            private String prenom = "Ada";

            void saluer() {
                var y = prenom.;
            }
        }
    """.trimIndent()

    /** Position TYPE : déclaration de champ en cours (préfixe tapé). */
    private val sourceType = """
        package app;

        public class SalleType {
            private Str reserve;
        }
    """.trimIndent()

    private fun classification(moteur: MoteurJava, chemin: String, source: String, caret: Int) =
        moteur.completer(chemin, source, caret)

    @Test
    fun `suggestions semantiques inserables sans erreur de resolution (position NOM)`() {
        val rapport = SocleCoherence.Rapport("P6", "suggestions résolubles — position NOM (`var x = |;`)")
        val moteur = MoteurJava()
        val caret = sourceNom.indexOf("var x = ") + "var x = ".length
        val reponse = classification(moteur, "/app/Salle.java", sourceNom, caret)
        assertTrue(
            reponse.items.isNotEmpty(),
            "la position NOM doit classer et proposer des items (genre ${reponse.genre})",
        )
        println("[P6] position NOM : genre=${reponse.genre}, ${reponse.items.size} items, total=${reponse.totalDisponible}")

        val examinables = reponse.items.filter {
            it.nature in setOf(NatureItem.CHAMP, NatureItem.VARIABLE, NatureItem.PARAMETRE, NatureItem.CONSTANTE_ENUM)
        }
        for (item in examinables) {
            val texte = item.texteInsertion ?: item.libelle
            val apresSource = SocleCoherence.insererSuggestion(
                sourceNom,
                caret,
                SocleCoherence.longueurPrefixeAuCaret(sourceNom, caret),
                texte,
                item.editionsAdditionnelles,
            )
            val erreurs = SocleCoherence.erreurs(SocleCoherence.temoinNeuf().diagnose("/app/Salle.java", apresSource))
            val deResolution = erreurs.filter(SocleCoherence::estErreurDeResolution)
            if (deResolution.isEmpty()) {
                rapport.mesure()
            } else {
                deResolution.forEach {
                    rapport.violer(
                        "item « ${item.libelle} » (${item.nature}) : erreur de résolution après insertion — ${it.message}",
                        "suggestion inaccessible/inexistante dans le contexte (G-004)",
                    )
                }
            }
        }
        assertTrue(examinables.isNotEmpty(), "la position NOM doit proposer au moins une variable/champ/paramètre")
        rapport.clore("${examinables.size} items sémantiques examinés")
    }

    @Test
    fun `suggestions de membres inserables (position MEMBRE, mesure)`() {
        val rapport = SocleCoherence.Rapport("P6", "suggestions résolubles — position MEMBRE (`prenom.|;`)")
        val moteur = MoteurJava()
        val caret = sourceMembre.indexOf("prenom.") + "prenom.".length
        val reponse = classification(moteur, "/app/SalleMembre.java", sourceMembre, caret)
        if (reponse.items.isEmpty()) {
            // Mesure (pas un échec dur) : la position membre à préfixe VIDE peut
            // encore manquer de couverture — c'est une donnée de baseline, pas un
            // crash.
            rapport.violer(
                "la position MEMBRE à préfixe vide ne rend aucun item (genre ${reponse.genre})",
                "classification de contexte à préfixe vide (H9/G-004 — échelle de splice)",
            )
            rapport.clore("position MEMBRE — aucun item (baseline)")
            return
        }
        println("[P6] position MEMBRE : genre=${reponse.genre}, ${reponse.items.size} items")

        val champValeur = reponse.items.filter { it.nature == NatureItem.CHAMP }
        for (item in champValeur) {
            val apresSource = SocleCoherence.insererSuggestion(
                sourceMembre,
                caret,
                SocleCoherence.longueurPrefixeAuCaret(sourceMembre, caret),
                item.texteInsertion ?: item.libelle,
                item.editionsAdditionnelles,
            )
            val erreurs = SocleCoherence.erreurs(SocleCoherence.temoinNeuf().diagnose("/app/SalleMembre.java", apresSource))
            val deResolution = erreurs.filter(SocleCoherence::estErreurDeResolution)
            if (deResolution.isEmpty()) rapport.mesure() else {
                deResolution.forEach {
                    rapport.violer("champ « ${item.libelle} » : ${it.message}", "suggestion inaccessible (G-004)")
                }
            }
        }

        // Les méthodes : convention client standard `label()` — classification
        // des erreurs (résolution = violation ; type/syntaxe = information).
        val methodes = reponse.items.filter { it.nature == NatureItem.METHODE }.take(12)
        for (item in methodes) {
            val apresSource = SocleCoherence.insererSuggestion(
                sourceMembre,
                caret,
                SocleCoherence.longueurPrefixeAuCaret(sourceMembre, caret),
                (item.texteInsertion ?: item.libelle) + "()",
                item.editionsAdditionnelles,
            )
            val erreurs = SocleCoherence.erreurs(SocleCoherence.temoinNeuf().diagnose("/app/SalleMembre.java", apresSource))
            val deResolution = erreurs.filter(SocleCoherence::estErreurDeResolution)
            if (deResolution.isEmpty()) {
                rapport.mesure()
            } else {
                deResolution.forEach {
                    rapport.violer("méthode « ${item.libelle} » : ${it.message}", "suggestion inaccessible (G-004)")
                }
            }
        }
        rapport.clore("${champValeur.size} champs + ${methodes.size} méthodes examinés")
    }

    @Test
    fun `types inserables avec auto-import (position TYPE, mesure)`() {
        val rapport = SocleCoherence.Rapport("P6", "types résolubles — position TYPE (`private Str| reserve;`)")
        val moteur = MoteurJava()
        val caret = sourceType.indexOf("Str") + "Str".length
        val reponse = classification(moteur, "/app/SalleType.java", sourceType, caret)
        assertTrue(reponse.items.isNotEmpty(), "la position TYPE doit proposer des types")
        println("[P6] position TYPE : genre=${reponse.genre}, ${reponse.items.size} items")

        val types = reponse.items.filter { it.nature in setOf(NatureItem.CLASSE, NatureItem.INTERFACE) }.take(12)
        for (item in types) {
            val apresSource = SocleCoherence.insererSuggestion(
                sourceType,
                caret,
                SocleCoherence.longueurPrefixeAuCaret(sourceType, caret),
                item.texteInsertion ?: item.libelle,
                item.editionsAdditionnelles,
            )
            val erreurs = SocleCoherence.erreurs(SocleCoherence.temoinNeuf().diagnose("/app/SalleType.java", apresSource))
            val deResolution = erreurs.filter(SocleCoherence::estErreurDeResolution)
            if (deResolution.isEmpty()) {
                rapport.mesure()
            } else {
                deResolution.forEach {
                    rapport.violer(
                        "type « ${item.libelle} » : ${it.message} (auto-import=${item.autoImportFqn})",
                        "suggestion de type non résoluble malgré l'auto-import (G-004)",
                    )
                }
            }
        }
        assertTrue(types.isNotEmpty(), "la position TYPE doit proposer au moins un type simple")
        rapport.clore("${types.size} types examinés")
    }

    /** Garde de compilation : le genre INCONNU existe (référence de l'énum). */
    @Test
    fun `genres de contexte referables`() {
        assertTrue(GenreContexte.INCONNU.name.isNotEmpty())
    }
}
