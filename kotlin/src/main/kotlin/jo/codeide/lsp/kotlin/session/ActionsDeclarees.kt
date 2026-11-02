// CodeIDE — plans d'action déclaratifs d'un document Kotlin (module
// :kotlin, étage 5 vague 5d — ADR 0115 option 1 : miroir ModCommand,
// ADR 0013 Java — le modèle, pas le code). Code NEUF jjoblab
// (clean-room — contrat : fiches 016/020/047 du cahier pour les
// quick-fixes ENROBÉS, ADR 0115 pour le modèle déclaratif ; les plans
// sont calculés en LECTURE, re-prouvés à la requête, exécution déléguée
// au client).
package jo.codeide.lsp.kotlin.session

import jo.codeide.lsp.kotlin.api.EditionFichierKotlin
import jo.codeide.lsp.kotlin.api.EditionTexteKotlin
import jo.codeide.lsp.kotlin.api.GenreActionKotlin
import jo.codeide.lsp.kotlin.api.PlanActionKotlin
import jo.codeide.lsp.kotlin.parse.kt.PlageTexte

/**
 * Calcul des PLANS D'ACTION déclaratifs au point demandé (pur — aucun
 * état, appelé sous verrou de lecture).
 *
 * ## Les familles (chacune RE-PROUVÉE à la requête — jamais un plan périmé)
 *
 * - **IMPORT** (fiche 047 — `importsCandidats`) : le nom sous le point
 *   est NON RÉSOLU et le classpath porte des classes homonymes → un
 *   plan PAR FQCN candidate, l'édition INSÉRANT la directive après la
 *   dernière existante (ou après le package, ou en tête) ;
 * - **MEMBRES MANQUANTS** (fiche 020 — P1-C ADR 0229) : le point vit
 *   dans le nom d'une déclaration de type à supertypes abstraits → le
 *   plan d'insertion des stubs ;
 * - **CORRECTIFS DE TEXTE** (fiche 016) : le point vit dans la PLAGE
 *   d'un correctif prouvé (accès sûr, conversions fermées) → le plan de
 *   CE correctif.
 *
 * Le classement est STABLE (imports, puis membres manquants, puis
 * correctifs en ordre du document) — l'hôte affiche dans cet ordre.
 * Sans preuve au point : liste VIDE (D3).
 */
internal object ActionsDeclarees {

    /**
     * Calcule les plans d'action au point demandé.
     *
     * @param uri l'identifiant du document ;
     * @param deplacement l'offset du caret ;
     * @param importsCandidats les FQCN candidates du nom sous le point
     * (le moteur les a RE-PROUVÉES — liste vide si le nom résout déjà) ;
     * @param membresManquants les correctifs d'implémentation au point ;
     * @param correctifs les correctifs de texte du document (en ordre du
     * document — seuls ceux dont la plage COUVRE le point deviennent des
     * plans) ;
     * @param insertionImport la plage d'insertion d'une nouvelle
     * directive d'import du document (après la dernière directive, ou
     * après le package, ou en tête — calculée par la session) ;
     * @return les plans, en ordre stable (imports, membres, correctifs).
     */
    internal fun calculer(
        uri: String,
        deplacement: Int,
        importsCandidats: List<String>,
        membresManquants: List<jo.codeide.lsp.kotlin.api.CorrectifTexteKotlin>,
        correctifs: List<jo.codeide.lsp.kotlin.api.CorrectifTexteKotlin>,
        insertionImport: PlageTexte,
        prefixeImport: String,
        suffixeImport: String,
    ): List<PlanActionKotlin> {
        val plans = mutableListOf<PlanActionKotlin>()

        // 1. IMPORT — un plan PAR FQCN candidate (tri du moteur).
        for (fqcn in importsCandidats) {
            plans.add(
                PlanActionKotlin(
                    titre = "Importer $fqcn",
                    genre = GenreActionKotlin.RAPIDE,
                    editions = listOf(
                        EditionFichierKotlin(
                            uri = uri,
                            editions = listOf(
                                EditionTexteKotlin(
                                    plage = insertionImport,
                                    texte = "$prefixeImport$fqcn$suffixeImport",
                                ),
                            ),
                        ),
                    ),
                ),
            )
        }

        // 2. MEMBRES MANQUANTS — le plan d'insertion des stubs.
        for (correctif in membresManquants) {
            plans.add(
                PlanActionKotlin(
                    titre = correctif.titre,
                    genre = GenreActionKotlin.RAPIDE,
                    editions = listOf(
                        EditionFichierKotlin(
                            uri = uri,
                            editions = listOf(
                                EditionTexteKotlin(plage = correctif.plage, texte = correctif.texte),
                            ),
                        ),
                    ),
                ),
            )
        }

        // 3. CORRECTIFS DE TEXTE — ceux dont la plage COUVRE le point.
        for (correctif in correctifs) {
            if (deplacement < correctif.plage.debut || deplacement > correctif.plage.fin) continue
            plans.add(
                PlanActionKotlin(
                    titre = correctif.titre,
                    genre = GenreActionKotlin.RAPIDE,
                    editions = listOf(
                        EditionFichierKotlin(
                            uri = uri,
                            editions = listOf(
                                EditionTexteKotlin(plage = correctif.plage, texte = correctif.texte),
                            ),
                        ),
                    ),
                ),
            )
        }
        return plans
    }
}
