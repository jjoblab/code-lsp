package jo.codeide.lsp.java.actionscode

import jo.codeide.lsp.java.outils.EditionTexte

/**
 * P4 (15/11/2026) — l'ENTOURAGE (surround with) : la sélection d'une
 * instruction devient le corps d'une construction de contrôle — if /
 * else, try / catch, for, while. Le sous-ensemble SENSÉ du catalogue
 * IntelliJ (Ctrl+Alt+T) : les quatre formes qui couvrent l'essentiel
 * des usages, sur une seule instruction terminée par `;` (la sélection
 * multi-instructions exige un analyseur de blocs complet — hors cadre,
 * l'honnêteté d'abord).
 *
 * La voie est TEXTUELLE (pas d'arbre) : [AnalyseTexte.debutInstruction]
 * et [AnalyseTexte.pointVirguleSuivant] bornent l'instruction ;
 * l'indentation de la ligne porteuse met en page le corps. Les éditions
 * sont DEUX : le texte d'en-tête avant l'instruction, la fermeture
 * après — l'instruction elle-même est INDENTÉE d'un cran par la
 * troisième édition.
 */
internal class ServiceEntourage(
    private val source: String,
) {

    /** L'instruction entourable au caret : la plage complète `… ;`. */
    internal data class Instruction(
        /** Le début de l'instruction (premier caractère). */
        val debut: Int,
        /** La fin (le point-virgule INCLUS). */
        val fin: Int,
        /** L'indentation de la ligne porteuse. */
        val indent: String,
    )

    /**
     * L'instruction ENTIÈRE contenant l'offset : bornée par le premier
     * point-virgule suivant (hors chaînes/commentaires — la voie de
     * [AnalyseTexte.pointVirguleSuivant]).
     *
     * @param offset une position dans l'instruction.
     * @return l'instruction, ou null (aucun `;` jusqu'à la fin —
     *   instruction incomplète, rien à entourer).
     */
    internal fun instructionAuCaret(offset: Int): Instruction? {
        val debut = AnalyseTexte.debutInstruction(source, offset)
        val fin = AnalyseTexte.pointVirguleSuivant(source, offset) ?: return null
        if (fin < debut) return null
        return Instruction(debut, fin, AnalyseTexte.indentation(source, offset))
    }

    /**
     * Les éditions de l'entourage `if / else`.
     *
     * @param instruction l'instruction cible.
     * @return les éditions (en-tête + indentation du corps + fermeture).
     */
    internal fun entourerSi(instruction: Instruction): List<EditionTexte> {
        val i = instruction.indent
        return listOf(
            EditionTexte(instruction.debut, 0, "if (condition) {\n$i    "),
            EditionTexte(instruction.fin + 1, 0, "\n$i} else {\n$i    \n$i}"),
        )
    }

    /** Les éditions de l'entourage `try / catch`. */
    internal fun entourerEssai(instruction: Instruction): List<EditionTexte> {
        val i = instruction.indent
        return listOf(
            EditionTexte(instruction.debut, 0, "try {\n$i    "),
            EditionTexte(instruction.fin + 1, 0, "\n$i} catch (Exception e) {\n$i    \n$i}"),
        )
    }

    /** Les éditions de l'entourage `for`. */
    internal fun entourerPour(instruction: Instruction): List<EditionTexte> {
        val i = instruction.indent
        return listOf(
            EditionTexte(instruction.debut, 0, "for (int i = 0; i < limite; i++) {\n$i    "),
            EditionTexte(instruction.fin + 1, 0, "\n$i}"),
        )
    }

    /** Les éditions de l'entourage `while`. */
    internal fun entourerTantQue(instruction: Instruction): List<EditionTexte> {
        val i = instruction.indent
        return listOf(
            EditionTexte(instruction.debut, 0, "while (condition) {\n$i    "),
            EditionTexte(instruction.fin + 1, 0, "\n$i}"),
        )
    }
}
