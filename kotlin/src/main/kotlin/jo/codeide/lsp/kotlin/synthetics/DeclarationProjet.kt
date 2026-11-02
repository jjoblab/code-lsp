// CodeIDE — modèle local de sortie des classes synthétiques (module
// :kotlin, étage 1 vague 1e). Code NEUF jjoblab écrit pour code-lsp —
// fichier associé à la REPRISE VOIE C R2 (manifeste
// docs/PROVENANCE-REPRISE-KOTLIN.md §3 : la zone synthetics est une zone
// de reprise, ce compagnon neuf y est inscrit pour le contrôle
// verifier-provenance.sh ; il ne reprend AUCUNE ligne de l'ancien dépôt —
// sa forme est dictée par la SURFACE D'USAGE du module repris, la
// frontière C cartographiée par l'inventaire §3 : l'ancien
// jo.lspkotlin.resolve.DeclarationProjet, type du NOYAU jamais lu, n'a
// pas de survivant ici).
package jo.codeide.lsp.kotlin.synthetics

import jo.codeide.lsp.kotlin.index.ParametreAppelable

/**
 * Une déclaration rendue par les classes synthétiques (R, BuildConfig,
 * bindings) — la forme de sortie LOCALE de la zone synthetics.
 *
 * Ce type est le point d'adaptation de la frontière C : le module repris
 * [ClassesSynthetiques] construit ses déclarations avec ces champs précis
 * (noms portés par les appels nommés du corps repris) ; le modèle de
 * RÉSOLUTION de l'étage 2 le consommera ou y mappera sa propre forme au
 * moment de la fusion (coupure par classe générée réelle — l'URI vide et
 * les plages nulles distinguent une synthétique d'une déclaration scannée).
 *
 * @property genre la nature de la déclaration ;
 * @property nom le nom déclaré (`R`, `layout`, `textView`, `inflate`…) ;
 * @property uri l'URI du fichier PORTEUR le cas échéant (un layout pour
 * les membres d'un binding — clé du go-to-definition futur), chaîne vide
 * pour les déclarations sans fichier ;
 * @property debutNom toujours `0` pour une synthétique (aucune source ne
 * la porte — le champ existe pour la forme commune, l'étage 2 décide) ;
 * @property finNom toujours `0` (même remarque que [debutNom]) ;
 * @property paquet le paquet porteur (`com.ex.app`,
 * `com.ex.app.databinding`) ;
 * @property recepteur toujours `null` (aucune extension synthétique —
 * champ de forme commune) ;
 * @property typeRetour le type rendu (`Int` pour une entrée `R`, le FQCN
 * de la famille, le type du porteur d'un `@+id`), `null` pour une classe ;
 * @property parametres toujours vide (les arités vararg des méthodes de
 * binding sont portées par [aVararg], jamais modelées — D3) ;
 * @property conteneur le FQCN du conteneur (`com.ex.app.R.layout`),
 * `null` pour une classe de tête ;
 * @property aVararg `true` quand le contrôle d'arité est RELÂCHÉ (les
 * vraies signatures AGP ont des surcharges que le modèle n'invente pas —
 * sous-rapport honnête, jamais un faux positif).
 */
internal data class DeclarationProjet(
    internal val genre: GenreDeclaration,
    internal val nom: String,
    internal val uri: String,
    internal val debutNom: Int,
    internal val finNom: Int,
    internal val paquet: String,
    internal val recepteur: String?,
    internal val typeRetour: String?,
    internal val parametres: List<ParametreAppelable>,
    internal val conteneur: String?,
    internal val aVararg: Boolean = false,
)

/**
 * La nature d'une déclaration synthétique — les quatre genres que produit
 * la zone (une classe de tête, un sous-objet de famille `R`, une entrée
 * propriété, une méthode de binding).
 */
internal enum class GenreDeclaration {

    /** Classe de tête (`R`, `BuildConfig`, `ActivityMainBinding`). */
    CLASSE,

    /** Sous-objet d'une famille (`R.layout`). */
    OBJET,

    /** Propriété membre (entrée `R`, champ de binding, `root`). */
    PROPRIETE,

    /** Méthode de binding (`inflate`, `bind`). */
    FONCTION,
}
