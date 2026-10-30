package jo.codeide.lsp.java.interne

import jo.codeide.lsp.java.outils.PlageCorps
import jo.codeide.lsp.java.outils.ScannerCorps

/**
 * Mécanique du diagnose rapide par corps de méthode (étage N1, cahier
 * fiche 014 — P6 v1.5.0, « skip the file parse entirely when a keystroke
 * stays inside one body », adapté ecj).
 *
 * Principe : quand une frappe reste CONFINÉE dans un seul corps de méthode,
 * seul CE corps est re-diagnostiqué — les autres corps sont remplacés dans
 * la source soumise à ecj par un **synthétique isomorphe** (même longueur :
 * ` throw new RuntimeException();` + bourrage d'espaces), et les problèmes
 * des corps masqués sont PORTÉS tels quels du diagnostic précédent (mêmes
 * offsets — l'isomorphie garantit l'alignement).
 *
 * La preuve de confinement casse → repli OBLIGATOIRE sur diagnose complet :
 *  - accolades déséquilibrées (le scanner rend null) ;
 *  - le texte HORS corps a changé (signature, initialiseur, import) ;
 *  - plus d'un corps a changé (région traversante) ;
 *  - aucun corps n'a changé mais la source diffère (memo géré en amont).
 *
 * Le synthétique `throw new RuntimeException();` est valide pour un corps de
 * méthode quel que soit le type de retour (pas de faux « must return ») et
 * ne référence rien du projet (pas de faux « unused ») — les pièges du
 * corpus du cahier (méthode non-void, import utilisé seulement par un corps
 * masqué) sont tenus par construction.
 */
public object DiagnoseRapide {

    /** Texte du synthétique (interne du corps, sans les accolades). */
    internal const val SYNTHETIQUE: String = " throw new RuntimeException();"

    /**
     * État rapide d'un document : dernière source diagnostiquée, son
     * résultat publié, ses plages de corps et son arbre focalisé.
     *
     * @property source la dernière source passée par diagnose (garde de
     *   contenu pour l'arbre focalisé).
     * @property diagnostics le résultat publié pour cette source.
     * @property corps les plages de corps détectées dans cette source.
     * @property arbreFocalise l'arbre de la source MASQUÉE (servi pour les
     *   features dans le corps hôte) ; null après un diagnose complet.
     * @property corpsHote le corps hôte de l'arbre focalisé (garde d'offset).
     */
    public class Etat(
        public val source: String,
        public val diagnostics: List<jo.codeide.lsp.java.diagnostics.Diagnostic>,
        public val corps: List<PlageCorps>,
        public val arbreFocalise: UniteResolue?,
        public val corpsHote: PlageCorps?,
    )

    /**
     * Tente d'établir la preuve de confinement entre l'ancienne source et
     * la nouvelle.
     *
     * La preuve compare les SEGMENTS hors corps (le texte entre les corps,
     * signatures comprises) : un corps qui grandit décale les positions
     * aval — les contenus restent identiques, la comparaison doit donc être
     * en contenu et non en longueur totale.
     *
     * @param ancienne l'état rapide du précédent diagnose (null = aucun).
     * @param nouvelle la nouvelle source.
     * @return la plage du CORPS HÔTE (le seul corps dont l'intérieur a
     *   changé) si la preuve est établie ; null sinon (repli complet).
     */
    public fun preuveDeConfinement(ancienne: Etat?, nouvelle: String): PlageCorps? {
        if (ancienne == null) return null
        val corpsNouveau = ScannerCorps.corpsDeMethodes(nouvelle) ?: return null
        if (ancienne.corps.size != corpsNouveau.size) return null
        // Le texte HORS corps doit être identique, segment par segment.
        if (segmentsHorsCorps(ancienne.source, ancienne.corps) != segmentsHorsCorps(nouvelle, corpsNouveau)) {
            return null
        }
        // Exactement UN corps dont l'intérieur diffère.
        var hote: PlageCorps? = null
        for ((index, plage) in corpsNouveau.withIndex()) {
            val anciennePlage = ancienne.corps[index]
            if (anciennePlage.longueur != plage.longueur ||
                ancienne.source.substring(anciennePlage.debutInterieur, anciennePlage.finInterieur) !=
                nouvelle.substring(plage.debutInterieur, plage.finInterieur)
            ) {
                if (hote != null) return null // deux corps touchés : structurel
                hote = plage
            }
        }
        return hote
    }

    /** Segments de texte hors corps (entre les corps, avant le premier, après le dernier). */
    private fun segmentsHorsCorps(source: String, corps: List<PlageCorps>): List<String> {
        val segments = mutableListOf<String>()
        var curseur = 0
        for (plage in corps) {
            if (plage.debutInterieur > curseur) {
                segments.add(source.substring(curseur, plage.debutInterieur))
            }
            if (plage.finInterieur > curseur) {
                curseur = plage.finInterieur
            }
        }
        segments.add(source.substring(curseur.coerceAtMost(source.length)))
        return segments
    }

    /**
     * Construit la source MASQUÉE pour le diagnose rapide : le corps hôte
     * intact, les autres corps remplacés par le synthétique isomorphe.
     *
     * Les corps trop courts pour contenir le synthétique, et les corps
     * vides, restent VERBATIM (contrat du cahier : « corps vide et corps
     * trop court laissés verbatim »).
     *
     * @param source la nouvelle source complète.
     * @param corps les plages de corps de la nouvelle source.
     * @param hote le corps hôte (intact).
     * @return la source masquée, de MÊME LONGUEUR que l'original.
     */
    public fun sourceMasquee(source: String, corps: List<PlageCorps>, hote: PlageCorps): String {
        val resultat = source.toCharArray()
        var frontierePrecedente = -1
        for (plage in corps) {
            // Égalité STRUCTURELLE (==), jamais l'identité (===) : l'hôte rendu par
            // [preuveDeConfinement] est une instance de SON scan interne, tandis que
            // cette liste vient du re-scan de diagnostiquerRapide — DEUX scans de la
            // même source produisent des instances DISTINCTES. Avec ===, l'hôte
            // n'était JAMAIS reconnu : il était masqué par le synthétique comme les
            // autres corps, la frappe de l'utilisateur était EFFACÉE du texte
            // diagnostiqué (0 diagnostic pour une erreur fraîche — v1.1.0, mesuré
            // sur JVM ET appareil : « l'éditeur n'affiche rien quand je tape »).
            // PlageCorps est une data class ; les plages d'une même liste étant
            // disjointes, l'égalité structurelle ne peut confondre deux corps.
            if (plage == hote) continue
            if (plage.debutInterieur < frontierePrecedente) continue // imbriqué : déjà couvert
            if (plage.longueur >= SYNTHETIQUE.length) {
                for (k in 0 until SYNTHETIQUE.length) {
                    resultat[plage.debutInterieur + k] = SYNTHETIQUE[k]
                }
                for (k in plage.debutInterieur + SYNTHETIQUE.length until plage.finInterieur) {
                    resultat[k] = ' '
                }
            }
            frontierePrecedente = plage.finInterieur
        }
        return String(resultat)
    }

    /**
     * Sépare les diagnostics PORTÉS (dans les corps masqués) des
     * diagnostics de structure (hors corps).
     *
     * Les corps masqués ont un intérieur IDENTIQUE entre l'ancienne et la
     * nouvelle source, mais leurs positions ABSOLUES peuvent être décalées
     * (le corps hôte a grandi ou rétréci) : les diagnostics portés sont
     * TRANSLATÉS du décalage de leur corps, de sorte que leurs offsets
     * correspondent EXACTEMENT à ceux du témoin (diagnose complet de la
     * nouvelle source).
     *
     * @param diagnostics le résultat précédent (offsets = ancienne source).
     * @param corpsAnciens les plages de corps de l'ancienne source.
     * @param corpsNouveaux les plages de corps de la nouvelle source (même
     *   cardinalité, index alignés).
     * @param indexHote l'index du corps hôte (exclu du portage).
     * @return les diagnostics portés et translatés ; le reste est recalculé
     *   frais sur la source masquée.
     */
    public fun portables(
        diagnostics: List<jo.codeide.lsp.java.diagnostics.Diagnostic>,
        corpsAnciens: List<PlageCorps>,
        corpsNouveaux: List<PlageCorps>,
        indexHote: Int,
    ): List<jo.codeide.lsp.java.diagnostics.Diagnostic> {
        val resultats = mutableListOf<jo.codeide.lsp.java.diagnostics.Diagnostic>()
        for (diagnostic in diagnostics) {
            val index = corpsAnciens.indices.firstOrNull { index ->
                index != indexHote &&
                    // Seuls les corps EFFECTIVEMENT masqués portent : les corps
                    // trop courts (laissés verbatim) et vides sont recalculés
                    // frais par la résolution de la source masquée — les porter
                    // les DUPLIQUERAIT.
                    corpsNouveaux[index].longueur >= SYNTHETIQUE.length &&
                    corpsAnciens[index].debutInterieur <= diagnostic.plage.debutOffset &&
                    diagnostic.plage.finOffset <= corpsAnciens[index].finInterieur
            } ?: continue // hors corps (ou verbatim) : recalculé frais
            val ancien = corpsAnciens[index]
            val nouveau = corpsNouveaux[index]
            val decalage = nouveau.debutInterieur - ancien.debutInterieur
            resultats.add(
                diagnostic.copy(
                    plage = jo.codeide.lsp.java.diagnostics.Plage(
                        diagnostic.plage.debutOffset + decalage,
                        diagnostic.plage.finOffset + decalage,
                    ),
                ),
            )
        }
        return resultats
    }
}
