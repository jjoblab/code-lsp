// CodeIDE — index des appelables Kotlin : modèle (module :kotlin, étage 1
// vague 1d). Code NEUF jjoblab écrit pour code-lsp (contrat : fiches
// 002/003/008/009/034 du cahier docs/cahier-specifications-kotlin/ ; ADR
// 0108 — index DÉDIÉ au module, hypothèse de travail tant que
// l'arbitrage Q-K1 est ouvert). INTERNE au module : la frontière publique
// reste le SPI `api/` (D6) — l'hôte ne voit jamais les appelables bruts,
// seulement leurs effets (complétion, diagnostics — étages 2/3).
package jo.codeide.lsp.kotlin.index

/**
 * Un appelable de premier niveau d'une bibliothèque Kotlin du classpath
 * (K3) — fonction ou propriété, simple ou EXTENSION, décodé de
 * l'annotation `@Metadata` d'une façade de fichier (`XxxKt`).
 *
 * Le périmètre est VOLONTAIREMENT restreint aux appelables de premier
 * niveau : les MEMBRES de classes restent dans la vue JVM de l'hôte
 * (réutilisation D4), qui couvre Java et Android — l'index Kotlin ne
 * comble que le VIDE Kotlin (top-level + extensions, invisibles côté
 * Java).
 *
 * @property genre fonction, propriété ou alias ;
 * @property nom nom appelé (`creer`) ;
 * @property paquet paquet du fichier porteur (`androidx.core.widget`) ;
 * @property recepteur FQCN du récepteur pour une EXTENSION
 * (`android.widget.TextView`), `null` sinon — clé de la complétion membre ;
 * @property typeRetour type de retour rendu (nom court Kotlin-ish,
 * `String`, `List<View>`), `null` si inconnu ;
 * @property parametres paramètres (nom + type rendu) ;
 * @property conteneur FQCN de la façade/fichier porteur
 * (`androidx.core.widget.WidgetsKt`) — pour l'import auto-éditable côté
 * hôte.
 */
internal data class AppelableKotlin(
    internal val genre: GenreAppelable,
    internal val nom: String,
    internal val paquet: String,
    internal val recepteur: String?,
    internal val typeRetour: String?,
    internal val parametres: List<ParametreAppelable>,
    internal val conteneur: String,
)

/**
 * Le genre d'un appelable de premier niveau.
 */
internal enum class GenreAppelable {

    /** `fun` de premier niveau (extension comprise). */
    FONCTION,

    /** `val`/`var` de premier niveau (extension comprise). */
    PROPRIETE,

    /**
     * `typealias` PUBLIC de premier niveau : un alias de bibliothèque
     * n'a AUCUN miroir bytecode — il n'existe que dans les métadonnées de
     * la façade `*Kt` du jar. Le décodeur l'émet avec le type CIBLE rendu
     * en type de retour ; l'index le SÉPARE des appelables (complétion et
     * survol d'appelables non pollués) et le prête à la résolution de
     * NOMS (garde sur le FQCN complet qualifié).
     */
    ALIAS,
}

/**
 * Un paramètre d'appelable (nom conservateur : `null` si absent des
 * métadonnées — signatures de sources décompilées).
 *
 * @property nom nom du paramètre ;
 * @property type type rendu (nom court), `null` si non représentable.
 */
internal data class ParametreAppelable(
    internal val nom: String?,
    internal val type: String?,
)

/**
 * La vue de SERVICE de l'index des appelables — ce que les requêtes du
 * noyau (complétion, survol, futurs diagnostics `kt.unresolved` de
 * l'étage 2) consultent sur un snapshot PUBLIÉ (la vue est vivante :
 * elle délègue à l'état courant de l'index, la garde de couverture du
 * guichet fait le reste).
 */
internal interface VueAppelables {

    /** Appelables de premier niveau portant EXACTEMENT ce nom. */
    fun topParNom(nom: String): List<AppelableKotlin>

    /**
     * Noms de premier niveau portant un PRÉFIXE (complétion de noms) —
     * recherche binaire + marche bornée sur la liste triée.
     *
     * @param prefixe préfixe sensible à la casse ;
     * @param limite nombre maximum de noms rendus.
     */
    fun nomsTopParPrefixe(prefixe: String, limite: Int): List<String>

    /** Extensions dont le récepteur EST ce FQCN. */
    fun extensionsSur(recepteur: String): List<AppelableKotlin>

    /** TYPEALIAS publics de premier niveau portant EXACTEMENT ce nom. */
    fun aliasTopParNom(nom: String): List<AppelableKotlin>

    /**
     * Le FQCN complet désigne-t-il un TYPEALIAS public du classpath ?
     * (un type qualifié COMPLET d'un alias est un accès LÉGAL sans
     * import — garde honnête, jamais un faux positif).
     */
    fun estTypeAliasConnu(fqcn: String): Boolean
}
