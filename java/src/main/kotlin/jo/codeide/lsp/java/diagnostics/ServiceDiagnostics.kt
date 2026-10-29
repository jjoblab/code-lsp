package jo.codeide.lsp.java.diagnostics

import jo.codeide.lsp.java.EnvironnementCompilation
import jo.codeide.lsp.java.MoteurJava

/**
 * Service de diagnostics (étage N1, cahier fiche 001) — couche de service
 * au-dessus du moteur.
 *
 * Deux comportements verrouillés par le cahier :
 *  - **diagnose nominal** : la liste rendue est NON NULLE, chaque
 *    diagnostic porte une sévérité, un message non vide et une plage
 *    cohérente (`startOffset >= 0`, `endOffset >= startOffset`) — les
 *    plages sont en offsets absolus dans le fichier (pas ligne/colonne) ;
 *  - **repli syntaxe** (fiche 028) : quand la plateforme manque
 *    (`isLikelyUsable()` faux — jdkHome sans image jrt), le service sert
 *    la syntaxe seule : une source cassée rend ses erreurs de SYNTAXE
 *    (garantie ①), une source saine non résolue rend une liste VIDE
 *    (garantie ② — jamais de « cannot be resolved » fabriqué).
 *
 * @param moteur le moteur qui exécute les diagnostics.
 */
public class ServiceDiagnostics(
    private val moteur: MoteurJava,
) {

    /**
     * Construit le service autour d'un ENVIRONNEMENT (fiche 001 : « le
     * service est instancié autour d'un CompilationEnvironment vierge —
     * aucun classpath n'est requis ») : le moteur du contexte défaut.
     *
     * @param environnement l'environnement de compilation.
     */
    public constructor(environnement: EnvironnementCompilation) : this(environnement.moteur())

    /**
     * Diagnostique une source : diagnose plein si la plateforme est
     * disponible, repli syntaxe sinon.
     *
     * @param nomFichier chemin logique du document (`Test.java`).
     * @param source le texte Java complet.
     * @return la liste de diagnostics (jamais nulle ; triée par offset).
     */
    public fun diagnose(nomFichier: String, source: String): List<Diagnostic> {
        return if (moteur.isLikelyUsable()) {
            moteur.diagnose(nomFichier, source)
        } else {
            moteur.diagnoseSyntax(nomFichier, source)
        }
    }
}
