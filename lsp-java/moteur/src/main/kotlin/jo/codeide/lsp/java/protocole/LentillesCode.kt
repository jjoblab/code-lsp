package jo.codeide.lsp.java.protocole

import java.nio.file.Files
import java.nio.file.Path
import jo.codeide.lsp.java.MoteurJava
import org.eclipse.lsp4j.CodeLens
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.Range

/**
 * Les lentilles de code (étage N8 — cahier fiche 058 : `codeLens` +
 * `resolveCodeLens`) : une lentille PAR DÉCLARATION (la classe et chacune
 * de ses méthodes), le RESOLVE paresseux portant la commande de comptage
 * de références.
 *
 * **Contrats verrouillés par la fiche** :
 *  - « exactement 2 lenses (une par déclaration : classe + méthode) » —
 *    les CHAMPS n'en portent PAS (le montage CLBase : classe + méthode
 *    `utilise` = 2) ;
 *  - « la lens brute n'a PAS encore de commande (resolve paresseux) » ;
 *  - la lens RÉSOLUE porte une commande dont le titre est EXACTEMENT « 0
 *    référence » / « 1 référence » (singulier/pluriel français) ;
 *  - le comptage EXCLUT la déclaration et BALAYE l'overlay des documents
 *    ouverts PUIS le disque (« l'appel `b.utilise(3)` du fichier non
 *    ouvert est compté ») — le balayage complet du moteur N4
 *    ([MoteurJava.referencesParBalayageComplet]) est ce chemin : il
 *    traverse les racines sources ET consulte les tampons vivants ;
 *  - les requêtes sont bornées (resolve en 10 s max — tenu par
 *    construction sur les corpus du cahier).
 *
 * Le comptage passe par le BALAYAGE COMPLET (l'oracle N4) et non par
 * l'index QueryExecutor : la fiche verrouille le décompte MULTI-FICHIERS
 * du fichier non ouvert, l'écart moteur de l'index (parité stricte
 * §10.4, fiche 054 : « références » rend 1) ne doit PAS atteindre le
 * compteur de lentilles — deux contrats différents, deux chemins
 * différents, assumés.
 *
 * @param moteur le moteur hôte.
 * @param etats le magasin des documents ouverts (overlay vivant).
 */
public class LentillesCode(
    private val moteur: MoteurJava,
    private val etats: EtatsDocuments,
) {

    /** Une lentille brute (plage + clé de comptage, SANS commande). */
    private data class LentilleInterne(
        val uri: String,
        val plage: Range,
        val offsetCible: Int,
    )

    /**
     * Les lentilles brutes d'un document (fiche 058 : « exactement 2 »
     * sur le montage CLBase — une par déclaration de TYPE ou de MÉTHODE ;
     * les champs n'en portent pas).
     *
     * @param uri l'URI du document.
     * @param chemin le chemin logique du document.
     * @param source le texte Java complet.
     * @return les lentilles SANS commande (resolve paresseux).
     */
    public fun lentilles(uri: String, chemin: String, source: String): List<CodeLens> {
        val internes = calculer(uri, chemin, source)
        return internes.map { interne ->
            CodeLens(interne.plage).also { lentille ->
                // Clé de résolution transportée dans data : le resolve
                // retrouve le document et l'offset de comptage sans
                // re-parser quoi que ce soit.
                lentille.data = mapOf(
                    "uri" to interne.uri,
                    "offset" to interne.offsetCible,
                )
            }
        }
    }

    /**
     * Résout une lentille : compte les références de la déclaration
     * (balayage complet — overlay ouvert PUIS disque, déclaration
     * EXCLUE) et pose la commande au titre contractuel.
     *
     * @param lentille la lentille brute rendue par [lentilles].
     * @return la lentille résolue (commande « N référence(s) »).
     */
    public fun resoudre(lentille: CodeLens): CodeLens {
        // Le data arrive en JsonObject (fil JSON-RPC) OU en Map (appel
        // direct in-process) — les helpers servent les deux.
        val uri = champDataChaine(lentille.data, "uri")
        val offset = champDataEntier(lentille.data, "offset")
        if (uri == null || offset == null) {
            lentille.command = Command(ConvertisseurLsp.titreCompteurReferences(0), "", emptyList())
            return lentille
        }
        val instantane = etats.instantane(uri)
        val texte = instantane?.texte
            ?: runCatching { Files.readString(Path.of(uri.removePrefix("file://"))) }.getOrNull()
            ?: ""
        val chemin = ConvertisseurLsp.cheminDeUri(uri) ?: uri
        val usages = moteur.referencesParBalayageComplet(chemin, texte, offset)
        val compteur = usages.count { !it.estDeclaration }
        lentille.command = Command(
            ConvertisseurLsp.titreCompteurReferences(compteur),
            "",
            emptyList(),
        )
        lentille.data = null
        return lentille
    }

    /** Le calcul des lentilles internes d'un document. */
    private fun calculer(uri: String, chemin: String, source: String): List<LentilleInterne> {
        val internes = ArrayList<LentilleInterne>()
        val arbre = moteur.resolveUnit(chemin, source, genererCode = false)?.arbre ?: return internes
        val types = arbre.types ?: return internes
        val repere = ConvertisseurLsp.repere(source)
        for (type in types) {
            val nomType = String(type.name)
            val plageNomType = plageJeton(nomType, type.declarationSourceStart, type.declarationSourceEnd, source)
            if (plageNomType != null) {
                internes.add(
                    LentilleInterne(
                        uri = uri,
                        plage = repere.plage(plageNomType),
                        offsetCible = plageNomType.debutOffset,
                    ),
                )
            }
            type.methods?.forEach { methode ->
                if (methode.isConstructor) return@forEach // pas de lentille sur les constructeurs
                val nom = String(methode.selector)
                val fin = methode.declarationSourceEnd.takeIf { it > 0 }
                    ?: (methode.declarationSourceStart + nom.length)
                val plageNom = plageJeton(nom, methode.declarationSourceStart, fin, source)
                if (plageNom != null) {
                    internes.add(
                        LentilleInterne(
                            uri = uri,
                            plage = repere.plage(plageNom),
                            offsetCible = plageNom.debutOffset,
                        ),
                    )
                }
            }
        }
        return internes
    }

    /**
     * La plage du JETON nom (même approche que [SymbolesDocument] : le
     * premier identifiant ÉGAL au nom dans la fenêtre de la déclaration).
     */
    private fun plageJeton(nom: String, debutFenetre: Int, finFenetre: Int, source: String): jo.codeide.lsp.java.diagnostics.Plage? {
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
                    return jo.codeide.lsp.java.diagnostics.Plage(debut + indice, debut + j)
                }
                indice = j
            } else {
                indice++
            }
        }
        return null
    }
}
