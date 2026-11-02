package jo.codeide.lsp.java.protocole

import jo.codeide.lsp.java.MoteurJava
import jo.codeide.lsp.java.diagnostics.Plage
import org.eclipse.jdt.internal.compiler.ast.AbstractMethodDeclaration
import org.eclipse.jdt.internal.compiler.ast.FieldDeclaration
import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration
import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.SymbolKind
import org.eclipse.lsp4j.Range

/**
 * Les symboles de document (étage N8 — cahier fiche 054 :
 * `documentSymbol`) : la structure hiérarchique d'une unité — types au
 * premier niveau, leurs membres (champs, méthodes, constructeurs) et les
 * types imbriqués en enfants.
 *
 * Le contrat de la fiche : « 1 symbole au premier niveau (la classe T) ;
 * au moins 2 enfants ; un enfant nommé “a” » — la FORME hiérarchique
 * `DocumentSymbol` (plage de la déclaration entière, plage de sélection
 * sur le JETON nom).
 *
 * La lecture est RÉSILIENTE par construction : l'arbre ecj en recovery
 * (source cassée) rend ce qui est parsable — chaque membre est extrait
 * indépendamment, un nœud fautif n'annule jamais ses frères (convention
 * de [jo.codeide.lsp.java.interne.LectureAst.VisiteurResilient],
 * appliquée ici par parcours direct et garde par membre).
 *
 * @param moteur le moteur hôte (arbre résolu du cache de parse).
 */
public class SymbolesDocument(private val moteur: MoteurJava) {

    /**
     * Les symboles hiérarchiques d'un document.
     *
     * @param chemin le chemin logique du document.
     * @param source le texte Java complet (tampon ouvert).
     * @return les symboles du premier niveau (les types de l'unité), les
     *   membres en enfants ; liste VIDE honnête si l'arbre est absent
     *   (convention I9 — jamais null, jamais d'exception).
     */
    public fun symboles(chemin: String, source: String): List<DocumentSymbol> {
        val arbre = moteur.resolveUnit(chemin, source, genererCode = false)?.arbre ?: return emptyList()
        val types = arbre.types ?: return emptyList()
        return types.mapNotNull { type -> symboleDe(type, source) }
    }

    /**
     * Le symbole d'une déclaration de type (récursif — les membres et les
     * types imbriqués deviennent les enfants).
     *
     * @param type la déclaration de type ecj.
     * @param source le texte source du document.
     * @return le symbole, ou null si le nom est introuvable dans le texte
     *   (nœud en recovery — la fiche « fuite des classes imbriquées » 032
     *   a verrouillé l'honnêteté : pas de symbole plutôt qu'un faux).
     */
    private fun symboleDe(type: TypeDeclaration, source: String): DocumentSymbol? {
        val nom = String(type.name)
        val plageNom = plageJeton(nom, type.declarationSourceStart, type.declarationSourceEnd, source)
            ?: return null
        val symbole = DocumentSymbol()
        symbole.name = nom
        symbole.kind = genreDe(nom, type, source)
        symbole.range = plage(
            type.declarationSourceStart,
            declarationSourceEnd(type, source),
            source,
        )
        symbole.selectionRange = plageNom

        val enfants = ArrayList<DocumentSymbol>()

        // Champs (fiche 054 : le champ « total » est un enfant de T).
        type.fields?.forEach { champ ->
            symboleDeChamp(champ, source)?.let { enfants.add(it) }
        }

        // Méthodes et constructeurs (fiche 054 : la méthode « a » est un
        // enfant de T).
        type.methods?.forEach { methode ->
            symboleDeMethode(methode, source)?.let { enfants.add(it) }
        }

        // Types imbriqués (récursion).
        type.memberTypes?.forEach { imbrique ->
            symboleDe(imbrique, source)?.let { enfants.add(it) }
        }

        symbole.children = enfants
        return symbole
    }

    /** Le symbole d'un champ. */
    private fun symboleDeChamp(champ: FieldDeclaration, source: String): DocumentSymbol? {
        val nom = String(champ.name)
        val fin = champ.declarationSourceEnd.takeIf { it > 0 } ?: (champ.declarationSourceStart + nom.length)
        val plageNom = plageJeton(nom, champ.declarationSourceStart, fin, source) ?: return null
        val symbole = DocumentSymbol()
        symbole.name = nom
        symbole.kind = SymbolKind.Field
        symbole.range = plage(champ.declarationSourceStart, fin, source)
        symbole.selectionRange = plageNom
        return symbole
    }

    /** Le symbole d'une méthode ou d'un constructeur. */
    private fun symboleDeMethode(methode: AbstractMethodDeclaration, source: String): DocumentSymbol? {
        val nom = if (methode.isConstructor) {
            // Le constructeur porte le nom de la classe (sélecteur vide
            // chez ecj pour les constructeurs).
            nomClasseDe(methode, source) ?: return null
        } else {
            String(methode.selector)
        }
        val fin = methode.declarationSourceEnd.takeIf { it > 0 }
            ?: (methode.declarationSourceStart + nom.length)
        val plageNom = plageJeton(nom, methode.declarationSourceStart, fin, source) ?: return null
        val symbole = DocumentSymbol()
        symbole.name = nom
        symbole.kind = if (methode.isConstructor) SymbolKind.Constructor else SymbolKind.Method
        symbole.range = plage(methode.declarationSourceStart, fin, source)
        symbole.selectionRange = plageNom
        symbole.children = emptyList()
        return symbole
    }

    /** Le nom de la classe propriétaire d'une méthode (constructeurs). */
    private fun nomClasseDe(methode: AbstractMethodDeclaration, source: String): String? {
        // La classe déclarante précède la déclaration dans le texte : le
        // premier identifiant avant « ( » sur la ligne du sélecteur. Le
        // parent est accessible par le scope — mais en recovery le scope
        // peut manquer : repli lexical sur le texte.
        val debut = methode.declarationSourceStart
        val limite = minOf(debut + 200, source.length)
        val fenetre = source.substring(debut, limite)
        val egal = fenetre.indexOf('(')
        if (egal <= 0) return null
        val tete = fenetre.substring(0, egal).trim()
        return tete.substringAfterLast(' ').takeIf { it.isNotBlank() && it.all { c -> c.isJavaIdentifierPart() } }
    }

    /**
     * Le genre LSP d'un type selon le MOT-CLÉ déclarant : le texte entre
     * le début de la déclaration et le jeton nom contient exactement l'un
     * des mots-clés de nature (les modificateurs précèdent, le mot-clé
     * précède immédiatement le nom). Le champ `kind` d'ecj n'est pas
     * visible hors de son paquet — la lecture lexicale est le contrat
     * HONNÊTE (et le même que [SymbolesEspace]).
     */
    private fun genreDe(nom: String, type: TypeDeclaration, source: String): SymbolKind {
        val debut = type.declarationSourceStart.coerceIn(0, source.length)
        val finNom = source.indexOf(nom, debut)
        if (finNom < 0) return SymbolKind.Class
        val tete = source.substring(debut, finNom)
        return when {
            tete.contains(Regex("""(\b|\s)interface\s*$""")) -> SymbolKind.Interface
            tete.contains(Regex("""@?interface\s*$""")) -> SymbolKind.Interface
            tete.contains(Regex("""\benum\s*$""")) -> SymbolKind.Enum
            tete.contains(Regex("""\brecord\s*$""")) -> SymbolKind.Struct
            else -> SymbolKind.Class
        }
    }

    /** La fin de déclaration BORNEE au texte (sourceEnd peut déborder en recovery). */
    private fun declarationSourceEnd(type: TypeDeclaration, source: String): Int =
        type.declarationSourceEnd.coerceAtMost(source.length).coerceAtLeast(type.declarationSourceStart)

    /** La plage LSP d'un couple d'offsets. */
    private fun plage(debut: Int, fin: Int, source: String): Range =
        ConvertisseurLsp.repere(source).plage(Plage(debut, fin))

    /**
     * La plage du JETON nom : recherche du premier identifiant ÉGAL au nom
     * dans la fenêtre de la déclaration (l'approche « plageJeton » de
     * [jo.codeide.lsp.java.hierarchie.ServiceHierarchies] — les
     * modificateurs et annotations précèdent le nom, on ne devine jamais
     * sa position).
     */
    private fun plageJeton(nom: String, debutFenetre: Int, finFenetre: Int, source: String): org.eclipse.lsp4j.Range? {
        val debut = debutFenetre.coerceIn(0, source.length)
        val fin = finFenetre.coerceIn(debut, source.length)
        val fenetre = source.substring(debut, fin)
        var indice = 0
        while (indice < fenetre.length) {
            val c = fenetre[indice]
            if (c.isJavaIdentifierStart()) {
                var j = indice + 1
                while (j < fenetre.length && fenetre[j].isJavaIdentifierPart()) j++
                if (fenetre.substring(indice, j) == nom) {
                    return plage(debut + indice, debut + j, source)
                }
                indice = j
            } else {
                indice++
            }
        }
        return null
    }
}
