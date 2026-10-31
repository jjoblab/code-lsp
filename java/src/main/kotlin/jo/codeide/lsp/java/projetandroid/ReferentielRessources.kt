package jo.codeide.lsp.java.projetandroid

import java.nio.file.Files
import java.nio.file.Path
import java.util.TreeMap

/**
 * Référentiel de RESSOURCES d'un module Android (vague V-B — ADR 0020) :
 * la source de vérité UNIQUE des noms de ressources, consommée par la
 * fabrique des classes light R/binding (ADR 0021), la navigation
 * `R.id.x` → XML et le futur lint NewApi (ADR 0024).
 *
 * Miroir moteur du « référentiel incrémental » de la référence (§B8) : un
 * dépôt par dossier `res/`, ids `@+id` collectés dans TOUS les XML (layouts,
 * menus…), styleables à trois familles de champs, valeurs typées des
 * `<values>` — SANS `R.txt` AAR (vague suivante, ADR 0020 plan 3).
 *
 * **Règles de construction** :
 *  - le type d'une resriture vient du NOM DE DOSSIER avant le premier
 *    tiret (`values-fr`, `layout-land` → `values`, `layout`) ;
 *  - les dossiers `values*` livrent leurs entrées TYPÉES par l'élément
 *    (`<string>`, `<color>`, `<dimen>`, `<bool>`, `<integer>`, `<array>`,
 *    `<string-array>`, `<integer-array>`, `<plurals>`, `<style>`,
 *    `<declare-styleable>`, `<item type="id">`) ;
 *  - les autres dossiers livrent UNE ressource par fichier (nom sans
 *    extension) — `layout` EN PLUS collecte les vues `@+id` et les
 *    métadonnées de binding ;
 *  - un fichier XML hors `values*`/`layout*` (menu, xml, drawable…) qui
 *    déclare `@+id/…` alimente AUSSI `R.id` (règle aapt) ;
 *  - un nom déclaré DEUX fois dans la même configuration est une erreur
 *    de ressource — la PREMIÈRE occurrence gagne (aapt échoue ; le
 *    référentiel choisit la stabilité).
 *
 * **MAJ ciblée** : [rafraichirFichier] retire les entrées d'UN fichier et
 * le re-scanne — les `didChange` XML de l'hôte ne paient jamais un rescan
 * complet (ADR 0020 : « MAJ ciblée < 5 ms »).
 *
 * Tolérance : un fichier illisible/tronqué est ÉCARTÉ (parseur pull
 * tolérant) — jamais un crash du moteur ; un dossier absent est ignoré.
 *
 * @param dossiers dossiers `res/` (le premier est le principal — les
 *   qualifiers y sont résolus en priorité pour les types de vues).
 */
public class ReferentielRessources(dossiers: List<Path>) {

    /** Une ressource déclarée (une ou plusieurs configurations/fichiers). */
    public data class Ressource(
        /** Type R (`layout`, `id`, `string`…). */
        public val type: String,
        /** Nom de la ressource (champ Java correspondant). */
        public val nom: String,
        /** Les fichiers qui la déclarent (1+ pour un id multi-layouts). */
        public val fichiers: List<Path>,
        /** Offset du NOM dans le fichier le plus tôt (navigation exacte). */
        public val offsetNom: Int,
    ) {
        /** Le fichier de déclaration principal (le plus tôt parcouru). */
        public val fichierPrincipal: Path get() = fichiers.first()
    }

    /** Métadonnées de binding d'un layout (ADR 0021 — règles §B9). */
    public class LayoutBinding(
        /** Nom du layout SANS extension (`activity_main`). */
        public val nom: String,
        /** Vrai si la racine est `<layout>` (data binding) — faux : view binding. */
        public val estDataBinding: Boolean,
        /** id de vue → type Java (règle B9 : type incohérent entre
         *  configurations ⇒ `android.view.View` ; `tools:viewBindingType`
         *  prime). */
        public val vues: Map<String, String>,
        /** Variables `<data>` : nom → type Java (data binding seul). */
        public val variables: List<Pair<String, String>>,
        /** Fichiers (configurations) qui définissent ce layout. */
        public val fichiers: List<Path>,
    )

    /** Les types R connus du scan (triés). */
    private val types = TreeMap<String, TreeMap<String, Ressource>>()

    /** Layouts par nom (pour la fabrique binding et la navigation). */
    private val layouts = HashMap<String, LayoutBinding>()

    /** Index fichier → types touchés (pour la MAJ ciblée). */
    private val parFichier = HashMap<Path, MutableSet<String>>()

    /** Les dossiers scannés (l'ordre : le premier est le principal). */
    private val dossiers: List<Path> = dossiers.filter { Files.isDirectory(it) }

    init {
        for (dossier in this.dossiers) {
            scannerDossier(dossier)
        }
    }

    // ------------------------------------------------------------------ //
    // Lecture
    // ------------------------------------------------------------------ //

    /**
     * Les noms d'un type R (`R.layout` → noms des layouts).
     *
     * @param type le type (ex. `layout`, `id`, `string`).
     * @return l'ensemble des noms (vide si le type est inconnu).
     */
    public fun noms(type: String): Set<String> = types[type]?.keys?.toSet() ?: emptySet()

    /** La ressource (type, nom) — null si inconnue. */
    public fun ressource(type: String, nom: String): Ressource? = types[type]?.get(nom)

    /** Les types R connus (ensemble trié — `layout`, `id`, `string`…). */
    public fun typesConnus(): Set<String> = types.keys.toSet()

    /**
     * Les métadonnées de binding d'un layout.
     *
     * @param nomLayout le nom SANS extension (`activity_main`).
     * @return les métadonnées, ou null si le layout n'existe pas.
     */
    public fun binding(nomLayout: String): LayoutBinding? = layouts[nomLayout]

    /** Tous les layouts scannés (nom → métadonnées). */
    public fun bindings(): Map<String, LayoutBinding> = layouts.toMap()

    /**
     * La cible de NAVIGATION d'une référence `R.<type>.<nom>` : le fichier
     * déclarant + l'offset du nom (valeur de l'attribut `name` pour les
     * values, position de l'id pour les layouts/menus, début du nom de
     * fichier sinon).
     *
     * @param type le type R (`layout`, `id`, `string`…).
     * @param nom le nom de la ressource.
     * @return (fichier, offset du jeton cible) ou null si inconnu.
     */
    public fun cible(type: String, nom: String): Pair<Path, Int>? {
        val ressource = ressource(type, nom) ?: return null
        return ressource.fichierPrincipal to ressource.offsetNom
    }

    /**
     * La cible de navigation d'un CHAMP de styleable
     * (`R.styleable.X_a`) : l'attribut `<attr>` dans le `<declare-styleable>`.
     *
     * @param styleable le nom du styleable (`X`).
     * @param champ l'attribut (`a`).
     * @return (fichier, offset du nom) ou null.
     */
    public fun cibleStyleable(styleable: String, champ: String): Pair<Path, Int>? {
        val ressource = ressource("styleable", "${styleable}_$champ") ?: return null
        return ressource.fichierPrincipal to ressource.offsetNom
    }

    /**
     * MAJ CIBLÉE : retire les entrées d'UN fichier et le re-scanne
     * (didChange/didSave XML — jamais de rescan complet).
     *
     * @param fichier le fichier XML modifié (sous un dossier `res/`).
     */
    public fun rafraichirFichier(fichier: Path) {
        val typesTouches = parFichier.remove(fichier) ?: mutableSetOf()
        // Retrait : les entrées de ce fichier dans les types touchés ;
        // les layouts ENTIEREMENT issus de ce fichier disparaissent.
        for (type in typesTouches) {
            val table = types[type] ?: continue
            val nomsASupprimer = table.values.filter { fichier in it.fichiers }
            for (entree in nomsASupprimer) table.remove(entree.nom)
        }
        val aSupprimer = layouts.values.filter { fichier in it.fichiers && it.fichiers.size == 1 }
        for (layout in aSupprimer) layouts.remove(layout.nom)
        scannerFichier(fichier)
    }

    // ------------------------------------------------------------------ //
    // Scan
    // ------------------------------------------------------------------ //

    /** Scan complet d'un dossier `res/` — les dossiers NON qualifiés
     *  d'abord (`layout` avant `layout-land`) : le principal gagne les
     *  conflits de fichiers principaux. */
    private fun scannerDossier(dossier: Path) {
        try {
            // Les dossiers NON qualifiés d'abord (`layout` avant `layout-land`).
            val ordre = java.util.Comparator
                .comparing { fichier: Path -> estDossierQualifie(fichier.parent) }
                .thenComparing { fichier: Path -> fichier.toString() }
            Files.walk(dossier).use { flux ->
                flux.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".xml") }
                    .sorted(ordre)
                    .forEach { scannerFichier(it) }
            }
        } catch (_: Exception) {
            // Dossier illisible : écarté (tolérance).
        }
    }

    /** Vrai ssi le dossier parent porte un qualifier (`layout-land`). */
    private fun estDossierQualifie(dossier: Path?): Boolean {
        val nom = dossier?.fileName?.toString() ?: return false
        return nom.substringBefore('-').length != nom.length
    }

    /** Le type de ressource d'un chemin de dossier (`layout-land` → `layout`). */
    private fun typeDuDossier(dossier: Path): String = dossier.fileName.toString().substringBefore('-')

    /** Scan d'UN fichier XML de ressource. */
    private fun scannerFichier(fichier: Path) {
        val dossierParent = fichier.parent ?: return
        val typeDossier = typeDuDossier(dossierParent)
        val texte =
            try {
                Files.readString(fichier, Charsets.UTF_8)
            } catch (_: Exception) {
                return // illisible : écarté
            }
        when (typeDossier) {
            "values" -> scannerValues(fichier, texte)
            "layout" -> scannerLayout(fichier, texte, estMenu = false)
            else -> {
                // Fichier-ressource du type du dossier + ids @+id éventuels.
                enregistrer(typeDossier, nomFichier(fichier), fichier, 0)
                if (estXmlAvecIds(typeDossier)) scannerIds(fichier, texte)
            }
        }
    }

    /** Les dossiers XML où aapt collecte aussi les `@+id` (hors values/layout). */
    private fun estXmlAvecIds(typeDossier: String): Boolean =
        typeDossier in ENSEMBLE_TYPES_AVEC_IDS || typeDossier.startsWith("xml")

    /** `<values>` : entrées typées par élément. */
    private fun scannerValues(fichier: Path, texte: String) {
        val lecteur = LecteurXmlTolerant(texte)
        var styleableCourant: String? = null
        while (true) {
            val evenement = lecteur.suivant() ?: break
            if (evenement !is LecteurXmlTolerant.EvenementXml.Debut) {
                if (evenement is LecteurXmlTolerant.EvenementXml.Fin && evenement.nom == "declare-styleable") {
                    styleableCourant = null
                }
                continue
            }
            val nom = evenement.nom
            val attributs = evenement.attributs
            val positionNom = evenement.positionsValeurs["name"] ?: 0
            val nomDeclare = attributs["name"]
            when (nom) {
                "string", "color", "dimen", "bool", "integer", "array", "string-array", "integer-array", "plurals", "fraction" -> {
                    val type = if (nom == "string-array" || nom == "integer-array") "array" else nom
                    if (nomDeclare != null) enregistrer(type, nomDeclare, fichier, positionNom)
                }
                "style" -> if (nomDeclare != null) enregistrer("style", nomDeclare, fichier, positionNom)
                "declare-styleable" -> if (nomDeclare != null) {
                    styleableCourant = nomDeclare
                    enregistrer("styleable", nomDeclare, fichier, positionNom)
                }
                "attr" -> if (nomDeclare != null) {
                    // Trois familles (§B8) : X, X_a, X_a (index).
                    val styleable = styleableCourant
                    if (styleable != null) {
                        enregistrer("styleable", "${styleable}_$nomDeclare", fichier, positionNom)
                    } else {
                        // Un <attr> isolé (hors styleable) déclare R.attr.
                        enregistrer("attr", nomDeclare, fichier, positionNom)
                    }
                }
                "item" -> {
                    // <item type="id" name="x"/> — ids sans layout.
                    if (attributs["type"] == "id" && nomDeclare != null) {
                        enregistrer("id", nomDeclare, fichier, positionNom)
                    }
                }
                else -> {}
            }
        }
    }

    /** Dossiers layout (et variantes qualifiées) : ressource layout + vues
     *  @+id + métadonnées binding. */
    private fun scannerLayout(
        fichier: Path,
        texte: String,
        estMenu: Boolean,
    ) {
        val nomLayout = nomFichier(fichier)
        enregistrer("layout", nomLayout, fichier, 0)
        if (estMenu) return

        val lecteur = LecteurXmlTolerant(texte)
        var racineVue = true
        var estDataBinding = false
        var dansData = false
        val vues = LinkedHashMap<String, String>()
        val variables = mutableListOf<Pair<String, String>>()
        val typeParId = HashMap<String, MutableSet<String>>()
        while (true) {
            val evenement = lecteur.suivant() ?: break
            if (evenement !is LecteurXmlTolerant.EvenementXml.Debut) {
                if (evenement is LecteurXmlTolerant.EvenementXml.Fin && evenement.nom == "data") dansData = false
                continue
            }
            when (evenement.nom) {
                "layout" -> if (racineVue) estDataBinding = true
                "data" -> dansData = true
                "variable" -> {
                    val nom = evenement.attributs["name"]
                    val type = evenement.attributs["type"]
                    if (nom != null && type != null) variables.add(nom to type)
                }
                else -> if (!dansData && evenement.nom != "import") {
                    val id = evenement.attributs["id"]
                    if (id != null && (id.startsWith("@+id/") || id.startsWith("@id/"))) {
                        val nomId = id.substringAfterLast('/')
                        val typeForce = evenement.attributs["viewBindingType"]
                        val type = typeForce ?: fqcnDeBalise(evenement.nom)
                        if (type != null) {
                            vues[nomId] = type
                            typeParId.getOrPut(nomId) { mutableSetOf() }.add(type)
                        }
                        enregistrer("id", nomId, fichier, evenement.positionsValeurs["id"] ?: 0)
                    }
                }
            }
            racineVue = false
        }
        // Règle B9 : type incohérent entre configurations ⇒ View (le champ
        // EXISTE, le type est dégradé honnêtement).
        for ((nomId, ensemble) in typeParId) {
            if (ensemble.size > 1) vues[nomId] = "android.view.View"
        }
        val existant = layouts[nomLayout]
        if (existant == null) {
            layouts[nomLayout] = LayoutBinding(nomLayout, estDataBinding, vues, variables, listOf(fichier))
        } else {
            // Autre configuration du même layout : fusion des vues/variables.
            val fusionVues = LinkedHashMap(existant.vues)
            for ((id, type) in vues) {
                val ancien = fusionVues[id]
                if (ancien != null && ancien != type) fusionVues[id] = "android.view.View"
                else if (ancien == null) fusionVues[id] = type
            }
            val fusionVariables = (existant.variables + variables).distinct()
            layouts[nomLayout] =
                LayoutBinding(nomLayout, existant.estDataBinding || estDataBinding, fusionVues, fusionVariables, existant.fichiers + listOf(fichier))
        }
    }

    /** Scan des ids d'un XML hors layout (menus, xml, préférences…). */
    private fun scannerIds(fichier: Path, texte: String) {
        val lecteur = LecteurXmlTolerant(texte)
        while (true) {
            val evenement = lecteur.suivant() ?: break
            if (evenement !is LecteurXmlTolerant.EvenementXml.Debut) continue
            val id = evenement.attributs["id"] ?: continue
            if (!id.startsWith("@+id/")) continue
            val nomId = id.substringAfterLast('/')
            enregistrer("id", nomId, fichier, evenement.positionsValeurs["id"] ?: 0)
        }
    }

    /** Enregistre une ressource (la première occurrence gagne en doublon).
     *
     * Règle aapt du nom de champ : les POINTS deviennent des underscores
     * (`Theme.Mini` → `R.style.Theme_Mini`) — les autres caractères non
     * Java écartent la ressource (jamais un champ illégal). */
    private fun enregistrer(
        type: String,
        nom: String,
        fichier: Path,
        offsetNom: Int,
    ) {
        val nomPropre = nom.replace('.', '_')
        if (nomPropre.isEmpty() || !nomPropre.get(0).isJavaIdentifierStart()) return
        if (!nomPropre.all { it.isJavaIdentifierPart() }) return
        val table = types.getOrPut(type) { TreeMap() }
        val existant = table[nomPropre]
        if (existant == null) {
            table[nomPropre] = Ressource(type, nomPropre, listOf(fichier), offsetNom)
        } else if (fichier !in existant.fichiers) {
            table[nomPropre] = existant.copy(fichiers = existant.fichiers + listOf(fichier))
        }
        parFichier.getOrPut(fichier) { mutableSetOf() }.add(type)
    }

    /** Nom de ressource d'un fichier (sans extension). */
    private fun nomFichier(fichier: Path): String = fichier.fileName.toString().substringBefore('.')

    /**
     * Le FQCN Java d'une balise de vue : `TextView` → `android.widget.TextView`
     * (mapping des balises Android), une balise avec point → FQCN de vue
     * personnalisée, sinon null (balise non-vue : `<merge>`, `<include>`…).
     */
    private fun fqcnDeBalise(balise: String): String? = when {
        balise in BALISES_ANDROID -> "android.widget.${balise}"
        balise == "View" || balise == "ViewGroup" || balise == "ViewStub" -> "android.view.$balise"
        balise == "fragment" || balise == "merge" || balise == "include" || balise == "requestFocus" ||
            balise == "data" || balise == "variable" || balise == "import" -> null
        balise.contains('.') -> balise // vue personnalisée : le tag EST le FQCN
        balise.get(0).isUpperCase() -> balise // <MyView> sans paquet : non résolvable honnêtement
        else -> null
    }

    private companion object {
        /** Les dossiers XML dont aapt collecte les `@+id` (hors layout). */
        private val ENSEMBLE_TYPES_AVEC_IDS: Set<String> = setOf("menu", "xml", "preference")

        /** Les balises de vues Android mappées sur `android.widget.*`. */
        private val BALISES_ANDROID: Set<String> =
            setOf(
                "TextView", "EditText", "Button", "ImageView", "ImageButton", "CheckBox",
                "RadioButton", "RadioGroup", "Switch", "ToggleButton", "SeekBar", "ProgressBar",
                "RatingBar", "Spinner", "AutoCompleteTextView", "MultiAutoCompleteTextView",
                "CheckedTextView", "Chronometer", "DatePicker", "TimePicker", "NumberPicker",
                "AnalogClock", "DigitalClock", "HorizontalScrollView", "ScrollView", "ListView",
                "GridView", "ExpandableListView", "Gallery", "StackView", "TextSwitcher",
                "ImageSwitcher", "ViewAnimator", "ViewFlipper", "ViewSwitcher", "AdapterViewFlipper",
                "AbsListView", "AbsSpinner", "FrameLayout", "LinearLayout", "RelativeLayout",
                "TableLayout", "TableRow", "GridLayout", "AppBarLayout",
            )
    }
}
