package jo.codeide.lsp.java.interne

import jo.codeide.lsp.java.diagnostics.Diagnostic
import jo.codeide.lsp.java.diagnostics.Plage
import jo.codeide.lsp.java.diagnostics.Severite
import org.eclipse.jdt.core.compiler.IProblem
import org.eclipse.jdt.internal.compiler.env.ICompilationUnit

/**
 * Régime instantané (étage N1, cahier fiche 028 — lot h4 « LSP instantané »).
 *
 * Le régime de repli quand la plateforme manque (classpath vide AVANT la
 * première sync, jdkHome sans `lib/modules`) : l'état de l'appareil entre
 * le didOpen et la première synchronisation utile. Deux garanties
 * fondamentales verrouillées par le cahier :
 *
 * ① **la syntaxe cassée rend SES erreurs de syntaxe** — le parse SANS
 *    résolution publie les problèmes de parse (le silence d'avant h4 était
 *    le défaut) ;
 * ② **une source saine mais pleine de types non résolus rend une liste
 *    VIDE** — le repli ne fabrique JAMAIS de « cannot be resolved » (aucune
 *    résolution n'est tentée, donc aucun faux positif n'existe).
 *
 * La complétion instantanée propose champs, locales, paramètres et méthodes
 * du FICHIER COURANT + mots-clés du langage — sans jamais deviner de types
 * du JDK (le régime plein les apportera). Elle ne rend jamais null.
 */
public object RegimeInstantane {

    /**
     * Diagnostics de SYNTAXE seuls : parse complet sans résolution.
     *
     * @param pont le pont ecj (le parse pur ne consulte pas la plateforme).
     * @param unite l'unité à parser.
     * @return les diagnostics de syntaxe, TRIÉS par offset croissant
     *   (contrat de sortie du cahier).
     */
    public fun diagnosticsSyntaxe(pont: PontEcj, unite: ICompilationUnit): List<Diagnostic> {
        val reponse = pont.parserComplet(unite)
        return reponse.problemes()
            .map { convertir(it) }
            .sortedWith(compareBy({ it.plage.debutOffset }, { it.plage.finOffset }))
    }

    /**
     * Complétion instantanée du fichier courant.
     *
     * @param pont le pont ecj (parse sans résolution).
     * @param unite l'unité à parser.
     * @param source le texte source (peut être null ou vide — jamais de
     *   levée : une liste, éventuellement vide, est toujours rendue).
     * @param offset la position du caret.
     * @return les items du fichier (champ/méthode/type/locales et paramètre
     *   de la méthode englobante) + mots-clés, filtrés par le préfixe
     *   identifiant précédant le caret.
     */
    public fun completionInstantanee(
        pont: PontEcj,
        unite: ICompilationUnit,
        source: String?,
        offset: Int,
    ): List<ItemCompletion> {
        if (source.isNullOrEmpty()) return emptyList()
        val prefixe = prefixeIdentifiant(source, offset)
        val reponse = pont.parserComplet(unite)
        val items = LectureAst.inventaireFichier(reponse.arbre, offset).toMutableList()
        for (mot in LectureAst.MOTS_CLES) {
            items.add(ItemCompletion(mot, NatureItem.MOT_CLE))
        }
        return items
            .filter { it.libelle.startsWith(prefixe) }
            .distinctBy { it.libelle to it.nature }
            .sortedWith(compareBy({ it.nature }, { it.libelle }))
    }

    /**
     * Extrait le préfixe identifiant juste avant le caret (vide si le
     * caractère précédent n'est pas un caractère d'identifiant).
     *
     * @param source le texte source.
     * @param offset la position du caret.
     * @return le préfixe (éventuellement vide).
     */
    public fun prefixeIdentifiant(source: String, offset: Int): String {
        var debut = offset.coerceAtMost(source.length)
        while (debut > 0 && source[debut - 1].isJavaIdentifierPart()) {
            debut--
        }
        return source.substring(debut, offset.coerceAtMost(source.length))
    }

    /** Conversion d'un problème ecj en diagnostic du moteur. */
    internal fun convertir(probleme: IProblem): Diagnostic {
        val severite = when {
            probleme.isError -> Severite.ERREUR
            probleme.isWarning -> Severite.AVERTISSEMENT
            else -> Severite.INFO
        }
        // Plage NORMALISÉE (contrat fiche 013 : jamais plus dégénérée que
        // 1 caractère). Découverte figée N6 (sonde sur le jar ecj 3.40) :
        // IProblem.getSourceEnd() est une borne INCLUSIVE — la plage du
        // moteur étant EXCLUSIVE (TypesDiagnostics), la fin publiée vaut
        // sourceEnd + 1. N1 posait sourceEnd TEL QUEL : toute plage
        // multi-caractères était raccourcie d'un jeton (invisible aux
        // contrats faibles des fiches 001/013 — listes auto-cohérentes de
        // la fiche 014 — mais létale pour les correcteurs N6, qui insèrent
        // au bord EXACT du jeton). Les bornes inconnues d'ecj (-1) et les
        // plages vides restent une plage d'un caractère.
        val debut = probleme.getSourceStart().coerceAtLeast(0)
        val finBrut = probleme.getSourceEnd().coerceAtLeast(debut)
        val fin = finBrut + 1
        return Diagnostic(
            severite = severite,
            message = probleme.getMessage(),
            plage = Plage(debut, fin),
            code = probleme.getID().toString(),
            // Arguments STRUCTURÉS (étage N6) : les pièces {0}/{1}… AVANT
            // interpolation — stables entre locales, contrat du dispatch
            // ID-FIRST des correcteurs (fiches 063/064/065/051 : jamais de
            // parsing du message localisé).
            arguments = probleme.getArguments()?.toList() ?: emptyList(),
        )
    }
}
