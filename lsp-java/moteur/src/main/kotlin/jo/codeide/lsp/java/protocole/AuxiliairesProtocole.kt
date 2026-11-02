package jo.codeide.lsp.java.protocole

import jo.codeide.lsp.java.navigation.ChaineSelection
import org.eclipse.lsp4j.ApplyWorkspaceEditParams
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse
import org.eclipse.lsp4j.ConfigurationParams
import org.eclipse.lsp4j.LogTraceParams
import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.ProgressParams
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.RegistrationParams
import org.eclipse.lsp4j.ShowDocumentParams
import org.eclipse.lsp4j.ShowDocumentResult
import org.eclipse.lsp4j.ShowMessageRequestParams
import org.eclipse.lsp4j.UnregistrationParams
import org.eclipse.lsp4j.WorkDoneProgressCreateParams
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.services.LanguageClient
import java.util.concurrent.CompletableFuture

/**
 * Un client LSP MUET (étage N8) : absorbe toute notification et répond
 * null à toute requête — le serveur construit hors lanceur (tests de
 * protocole pur, fiche 055 : « serveur in-process construit sans
 * environnement de compilation ») n'a pas encore de client branché.
 *
 * Le serveur ne DOIT jamais planter parce qu'aucun client n'a été
 * connecté : [PublicateurDiagnostics] publie vers ce muet jusqu'au
 * `connect` du lanceur (dans les tests, le client espion le remplace).
 */
public object ClientMuet : LanguageClient {

    override fun telemetryEvent(object_: Any?): Unit = Unit

    override fun publishDiagnostics(diagnostics: PublishDiagnosticsParams?): Unit = Unit

    override fun showMessage(messageParams: MessageParams?): Unit = Unit

    override fun showMessageRequest(requestParams: ShowMessageRequestParams?): CompletableFuture<MessageActionItem> {
        @Suppress("UNCHECKED_CAST")
        return CompletableFuture.completedFuture(null) as CompletableFuture<MessageActionItem>
    }

    override fun logMessage(message: MessageParams?): Unit = Unit

    override fun logTrace(params: LogTraceParams?): Unit = Unit
}

/**
 * La lecture d'un champ du `data` LSP (étage N8) : le champ `data` des
 * items (lentilles, éléments de hiérarchie) voyage comme objet LIBRE —
 * lsp4j le désérialise en [com.google.gson.JsonObject] (l'adaptateur
 * Object du Gson du protocole produit des JsonElement), jamais en Map ;
 * l'appel DIRECT (tests in-process, bancs) voit la Map Kotlin d'origine.
 *
 * Les deux formes sont servies : le harnais comme le client réel.
 *
 * @param donnees la valeur du champ data (JsonObject, Map, ou autre).
 * @param cle la clé cherchée.
 * @return la valeur brute (String, JsonPrimitive, Number…), ou null.
 */
public fun champData(donnees: Any?, cle: String): Any? = when (donnees) {
    is Map<*, *> -> donnees[cle]
    is com.google.gson.JsonObject -> donnees.get(cle)
    else -> null
}

/**
 * La chaîne d'un champ `data` (les deux formes, cf. [champData]).
 *
 * @param donnees la valeur du champ data.
 * @param cle la clé cherchée.
 * @return la chaîne, ou null.
 */
public fun champDataChaine(donnees: Any?, cle: String): String? = when (val brut = champData(donnees, cle)) {
    is String -> brut
    is com.google.gson.JsonPrimitive -> if (brut.isString) brut.asString else null
    else -> null
}

/**
 * L'entier d'un champ `data` (les deux formes, cf. [champData]).
 *
 * @param donnees la valeur du champ data.
 * @param cle la clé cherchée.
 * @return l'entier, ou null.
 */
public fun champDataEntier(donnees: Any?, cle: String): Int? = when (val brut = champData(donnees, cle)) {
    is Number -> brut.toInt()
    is com.google.gson.JsonPrimitive -> if (brut.isNumber) brut.asNumber.toInt() else null
    else -> null
}

/**
 * La conversion des chaînes de sélection (étage N8 — cahier fiche 058 :
 * `selectionRange`) : la chaîne STRICTEMENT croissante du moteur
 * ([ChaineSelection], du plus petit maillon au sommet) vers la forme
 * LSP `SelectionRange` (chaîne de PARENTS — la plus petite plage
 * d'abord, chaque niveau parent englobant STRICTEMENT le précédent).
 *
 * La profondeur exigée par la fiche (≥ 4 niveaux) vient du moteur : la
 * chaîne N4 couvre jeton → expression → déclaration → type → unité.
 */
public object SelectionRangeLsp {

    /**
     * Le `SelectionRange` LSP d'une chaîne moteur.
     *
     * @param chaine la chaîne moteur (maillons croissants).
     * @param repere le repère du document.
     * @return la tête de la chaîne LSP (plage la plus petite, parents
     *   enchaînés).
     */
    public fun deChaine(chaine: ChaineSelection, repere: ConvertisseurLsp.Repere): org.eclipse.lsp4j.SelectionRange {
        var precedent: org.eclipse.lsp4j.SelectionRange? = null
        // Le moteur itère du PLUS PETIT au PLUS GRAND ; la chaîne LSP se
        // construit du PLUS GRAND vers le plus petit (le parent du
        // niveau k est le niveau k+1).
        for (maillon in chaine.maillons.asReversed()) {
            val plage = Range(repere.position(maillon.plage.debutOffset), repere.position(maillon.plage.finOffset))
            precedent = org.eclipse.lsp4j.SelectionRange(plage, precedent)
        }
        return precedent ?: org.eclipse.lsp4j.SelectionRange(
            Range(Position(0, 0), Position(0, 0)),
            null,
        )
    }
}
