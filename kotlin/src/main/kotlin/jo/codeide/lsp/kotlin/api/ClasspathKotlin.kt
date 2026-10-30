// CodeIDE — SPI du moteur Kotlin LSP : classpath neutre (module :kotlin,
// étage 1 vague 1b — session documents). Code NEUF jjoblab écrit pour
// code-lsp (clean-room : le code de l'ancien moteur codeide-lsp n'est pas
// consulté — le contrat est la fiche 049 du cahier
// docs/cahier-specifications-kotlin/ ; les types du SPI sont NEUTRES, sans
// aucune dépendance LSP4J ni Android — frontière D6).
package jo.codeide.lsp.kotlin.api

/**
 * Le classpath d'une session de langage Kotlin, en formes neutres (chemins
 * sous forme de chaînes — la vue riche d'index arrive avec la vague 1d sur
 * cette même frontière).
 *
 * Les deux moitiés du classpath (ADR 0211 ancien, reprise du CONTRAT) :
 * les [entrees] (jars/dossiers des bibliothèques) nourrissent l'index des
 * appelables ; les [racinesSources] (racines des sources du projet) nourrissent
 * le scan paresseux et [SessionLangageKotlin.rafraichirSources].
 *
 * La liste des entrées est ORDONNÉE : l'ordre du classpath fait la préséance
 * des candidats — la garde de couverture de l'index compare la liste
 * ordonnée (jamais un ensemble).
 *
 * @property entrees les entrées du classpath (chemins de jars ou de
 * dossiers), en ordre de préséance ; liste vide si aucun ;
 * @property racinesSources les racines des sources du projet (dossiers à
 * scanner), en ordre de préséance ; liste vide si aucune.
 */
public class ClasspathKotlin(
    public val entrees: List<String>,
    public val racinesSources: List<String>,
)
