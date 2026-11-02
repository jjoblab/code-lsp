// CodeIDE — SPI du moteur Kotlin LSP : aide à la signature (module :kotlin,
// étage 6 vague 6d). Code NEUF jjoblab (contrat : fiches 051 et 018 du
// cahier docs/cahier-specifications-kotlin/ — l'aide à la signature S1 du
// plan de stabilisation LSP §3.2, par le SPI (chemin de production D6),
// moteur réel). Types NEUTRES : aucune dépendance LSP4J ni Android — D6.
package jo.codeide.lsp.kotlin.api

/**
 * Une CANDIDATE de l'aide à la signature : l'étiquette rendue (la signature
 * écrite conservatrice), ses paramètres et la documentation KDoc qui
 * l'accompagne.
 *
 * @property etiquette la signature rendue (par ex.
 * `creer(nom: String, taille: Int): Int`, `Point(x: Int, y: Int)` pour un
 * constructeur, `setText(CharSequence): Unit` pour une méthode JVM) ;
 * @property parametres les paramètres rendus UN À UN (`nom: String`,
 * `CharSequence` pour la JVM — le bytecode ne nomme pas) ;
 * @property documentation la KDoc qui précède la déclaration (document
 * courant, fichier tiers ouvert par l'arbre VIF, fichier fermé par le
 * port) ; `null` si la candidate n'en porte pas — la candidate du
 * CLASSPATH reste MUETTE (le bytecode ne porte pas de documentation, D3).
 */
public class SignatureKotlin(
    public val etiquette: String,
    public val parametres: List<String>,
    public val documentation: String?,
)

/**
 * L'AIDE À LA SIGNATURE au point demandé (fiche 051) : les candidates de
 * l'appel englobant le plus profond (surcharges comprises), la signature
 * ACTIVE (l'arité compatible avec la position) et le paramètre ACTIF (la
 * place du caret dans la liste d'arguments — un lambda final compte pour
 * l'argument suivant).
 *
 * Le silence est conservateur (D3) : un curseur hors appel, après un appel
 * fermé, sur un document inconnu ou dans une session fermée rend `null` —
 * jamais une candidate inventée.
 *
 * @property signatures les candidates (toutes les surcharges du nom) ;
 * @property signatureActive l'index de la candidate ACTIVE dans
 * [signatures] (l'arité compatible : assez de paramètres pour servir la
 * place du caret) ;
 * @property parametreActif l'index du paramètre ACTIF (la place du caret
 * dans la liste — 0 après la parenthèse ouvrante, +1 après chaque
 * virgule, le compte des arguments du listes pour un lambda final).
 */
public class AideSignatureKotlin(
    public val signatures: List<SignatureKotlin>,
    public val signatureActive: Int,
    public val parametreActif: Int,
)
