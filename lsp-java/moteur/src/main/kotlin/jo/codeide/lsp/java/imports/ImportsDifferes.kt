package jo.codeide.lsp.java.imports

import jo.codeide.lsp.java.outils.EditionTexte

/**
 * Les éditions d'import DIFFÉRÉES (vague V-G — ADR 0031 (f),
 * `completionItem/resolve` réel : « calcul différé des gros edits ») :
 * quand le client sait RÉSOUDRE un item de complétion (capability
 * `resolveSupport`), l'insertion d'auto-import n'est plus calculée pour
 * CHAQUE item de la liste — elle l'est pour l'item CHOISI, au moment du
 * resolve, sur le texte COURANT.
 *
 * Les règles sont le MIROIR EXACT du calcul au moment de la liste
 * (`ServiceCompletion`, insertion d'import à l'ancre — fiche 029) :
 *  - déjà importé (exact ou `.*` du porteur) → AUCUNE édition ;
 *  - `java.lang` ou paquet COURANT du fichier → AUCUNE édition ;
 *  - type imbriqué (`$`) → AUCUNE édition (jamais importé directement) ;
 *  - sinon : insertion `import <fqn>;` (ou `import static <fqn>;`) au
 *    point d'ancrage — après le DERNIER import existant, sinon après la
 *    déclaration de paquet, sinon au début.
 *
 * Le balayage est PUREMENT LEXICAL (lignes `package`/`import`) : aucune
 * résolution, aucun verrou — le resolve d'un item ne se met jamais
 * derrière une compilation en vol.
 */
public object ImportsDifferes {

    /**
     * L'édition d'import d'un FQCN dans le texte COURANT du document.
     *
     * @param source le texte Java complet du document (au moment du resolve).
     * @param fqn le FQCN à importer (points ; type imbriqué `$` → null).
     * @param statique vrai pour un import STATIQUE (membre importé).
     * @return l'édition d'insertion, ou null si l'import est inutile
     *   (déjà présent, `java.lang`, paquet courant, imbriqué).
     */
    public fun edition(source: String, fqn: String, statique: Boolean): EditionTexte? {
        if (fqn.contains('$')) return null // imbriqué : jamais importé directement
        val paquetCourant = paquetCourant(source)
        val imports = importsPresents(source, statique)
        if (statique) {
            for (importe in imports) {
                if (importe == fqn) return null
                if (importe.endsWith(".*") && fqn.startsWith(importe.removeSuffix(".*") + ".")) return null
            }
        } else {
            val paquetType = fqn.substringBeforeLast('.')
            if (paquetType == paquetCourant) return null
            if (paquetType == "java.lang") return null
            for (importe in imports) {
                if (importe == fqn) return null
                if (importe.endsWith(".*") && fqn.startsWith(importe.removeSuffix(".*") + ".")) return null
            }
        }
        val texte = if (statique) "import static $fqn;\n" else "import $fqn;\n"
        return EditionTexte(offset = pointInsertion(source), longueur = 0, texte = texte)
    }

    /** Le paquet déclaré du fichier (balayage lexical de la ligne `package`). */
    private fun paquetCourant(source: String): String? {
        for (ligne in source.lineSequence()) {
            val amorce = ligne.trimStart()
            if (amorce.startsWith("package ") && amorce.contains(';')) {
                return amorce.removePrefix("package ").substringBefore(';').trim().takeIf { it.isNotEmpty() }
            }
            if (amorce.startsWith("import ") || amorce.startsWith("class ") || amorce.startsWith("public ")) break
        }
        return null
    }

    /** Les imports présents (statiques ou simples selon [statique]). */
    private fun importsPresents(source: String, statique: Boolean): List<String> {
        val resultats = mutableListOf<String>()
        for (ligne in source.lineSequence()) {
            val amorce = ligne.trimStart()
            if (amorce.startsWith("import ")) {
                val reste = amorce.removePrefix("import ").trimStart()
                val estStatique = reste.startsWith("static ")
                if (estStatique == statique) {
                    val cible = if (estStatique) reste.removePrefix("static ").trimStart() else reste
                    if (cible.contains(';')) {
                        resultats.add(cible.substringBefore(';').trim())
                    }
                }
            }
        }
        return resultats
    }

    /**
     * Le point d'insertion d'un import : après le DERNIER import existant
     * (complets ou en frappe), sinon après la déclaration de paquet, sinon
     * au début du fichier (même règle que l'ancre de la fiche 029).
     */
    private fun pointInsertion(source: String): Int {
        var position = 0
        var point = 0
        for (ligne in source.lineSequence()) {
            val amorce = ligne.trimStart()
            if (amorce.startsWith("package ")) {
                position += ligne.length + 1
                if (amorce.contains(';')) point = position
                continue
            }
            if (amorce.startsWith("import ") || amorce.startsWith("import\t")) {
                position += ligne.length + 1
                point = position
                continue
            }
            break
        }
        return point
    }
}
