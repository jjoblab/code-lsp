package jo.codeide.lsp.java.gabarits

/**
 * Ancre d'insertion d'import (étage N7 — cahier fiche 029) : le point
 * d'insertion d'un import additionnel, réutilisable par les postfixes
 * importeuses et, à terme, par les correcteurs.
 *
 * Règle contractuelle (ancre exacte du cahier) : JUSTE APRÈS le DERNIER
 * import (le début de la ligne qui le suit — « package a.b;\n » = 13 +
 * « import java.util.Map;\n » = 22 → 35), sinon juste après la ligne du
 * `package` (13), sinon le début de fichier (0).
 */
public object AncreImport {

    /**
     * Calcule l'ancre d'insertion d'import d'une source.
     *
     * @param source le texte Java complet.
     * @return l'offset d'insertion (insertion zéro-largeur).
     */
    public fun ancre(source: String): Int {
        var ancrePaquet = -1
        var ancreImport = -1
        var debutLigne = 0
        for (ligne in source.lineSequence()) {
            val pliee = ligne.trimStart()
            val finLigne = debutLigne + ligne.length
            if (pliee.startsWith("package ") && ancrePaquet < 0) {
                ancrePaquet = if (finLigne < source.length) finLigne + 1 else source.length
            }
            if (pliee.startsWith("import ") || pliee.startsWith("import\t")) {
                ancreImport = if (finLigne < source.length) finLigne + 1 else source.length
            }
            debutLigne = if (finLigne < source.length) finLigne + 1 else source.length
        }
        return when {
            ancreImport >= 0 -> ancreImport
            ancrePaquet >= 0 -> ancrePaquet
            else -> 0
        }
    }
}
