// CodeIDE — SPI du moteur Kotlin LSP : classpath neutre (module :kotlin,
// étage 1 vague 1b — session documents ; vague 1d — vue riche des entrées
// + dossier d'index, annoncée par la vague 1b sur cette même frontière).
// Code NEUF jjoblab écrit pour code-lsp (contrat : fiches 042/049 puis
// 002–005/008/009/034 du cahier docs/cahier-specifications-kotlin/ ; les
// types du SPI sont NEUTRES, sans aucune dépendance LSP4J ni Android —
// frontière D6).
package jo.codeide.lsp.kotlin.api

/**
 * Le classpath d'une session de langage Kotlin, en formes neutres (chemins
 * et empreintes sous forme de chaînes).
 *
 * Les deux moitiés du classpath (contrat ADR 0211 ancien, repris) : les
 * [entrees] (jars/AAR des bibliothèques) nourrissent l'index des
 * appelables ; les [racinesSources] (racines des sources du projet)
 * nourrissent le scan paresseux et
 * [SessionLangageKotlin.rafraichirSources][jo.codeide.lsp.kotlin.api.SessionLangageKotlin.rafraichirSources].
 *
 * La liste des entrées est ORDONNÉE : l'ordre du classpath fait la préséance
 * des candidats — la garde de couverture de l'index compare la liste
 * ordonnée (jamais un ensemble).
 *
 * @property entrees les entrées du classpath (jars/AAR avec leur empreinte
 * d'identité), en ordre de préséance ; liste vide si aucun ;
 * @property racinesSources les racines des sources du projet (dossiers à
 * scanner), en ordre de préséance ; liste vide si aucune ;
 * @property dossierIndex le répertoire de l'index partagé où les segments
 * d'appelables persistent (`kotlin-appelables/<sha256>.kidx`) ; `null` =
 * index en mémoire seule (tests, environnements éphémères).
 */
public class ClasspathKotlin(
    public val entrees: List<EntreeClasspathKotlin>,
    public val racinesSources: List<String>,
    public val dossierIndex: String? = null,
)
