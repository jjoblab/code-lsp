// CodeIDE — lecteur conservateur de DÉCLARATIONS Java (module :kotlin, étage
// 2 vague 2a — socle de résolution). Code NEUF jjoblab (clean-room — contrat :
// fiche 022 du cahier : « les racines sources nourrissent le scan projet
// (Kotlin ET Java — le volet Java couvre les POJO) » ; le lecteur Java
// maison de l'ancien dépôt vit en zone NOYAU, lecture interdite R9.1 —
// celui-ci est écrit neuf, borné au besoin des preuves).
package jo.codeide.lsp.kotlin.resolve

/**
 * Lecteur CONSERVATEUR de déclarations Java pour le modèle du projet : il
 * extrait d'une source Java les classes (avec leur clause `extends`/
 * `implements` de NIVEAU CLASSE), leurs méthodes, leurs champs et les
 * PROPRIÉTÉS SYNTHÉTIQUES des accesseurs (ADR 0219 : `getTitre()` rend la
 * propriété `titre` accessible depuis Kotlin).
 *
 * ## Bornes assumées (sous-rapport D3, jamais faux)
 *
 * - les ANNOTATIONS et les génériques sont sautés balancés : la contrainte
 *   d'un paramètre de type (`class B<T extends Comparable<T>>`) n'est PAS
 *   un héritage — seule la clause de NIVEAU CLASSE compte (fiche 022) ;
 * - les énumérations et records Java portent un supertype IMPLICITE
 *   (`Enum`/`Record`) : leurs membres implicites ne sont jamais prouvables
 *   absents — le type entre au modèle NON absence-fiable ;
 * - les membres de CORPS de méthode ne sont jamais collectés (suivi de
 *   profondeur d'accolades) ;
 * - tout token inattendu est IGNORÉ : le lecteur avance, jamais ne lève —
 *   une source hostile rend une contribution VIDE ou partielle, honnête.
 */
internal object LecteurDeclarationsJava {

    /** Les modificateurs Java sautés avant un membre ou un type. */
    private val MODIFICATEURS: Set<String> = setOf(
        "public", "private", "protected", "static", "final", "abstract",
        "synchronized", "native", "transient", "volatile", "strictfp",
        "default", "sealed",
    )

    /** Les mots-clés d'ouverture d'une déclaration de type. */
    private val MOTS_CLE_TYPE: Set<String> = setOf("class", "interface", "enum", "record")

    /**
     * Lit la contribution d'une source Java.
     *
     * @param texte le contenu complet du fichier `.java` ;
     * @param uri l'identifiant du fichier ;
     * @return la contribution (package, types) — vide si rien de lisible ;
     * JAMAIS d'exception.
     */
    internal fun lire(texte: String, uri: String): ContributionFichier {
        val jetons = jetonner(nettoyer(texte))
        val curseur = Curseur(jetons)

        // Le package du fichier (avant la première déclaration de type).
        var paquet = ""
        while (curseur.aUnSuivant()) {
            val jeton = curseur.courant()
            if (jeton == "package") {
                curseur.avancer()
                val nom = StringBuilder()
                while (curseur.aUnSuivant() && curseur.courant() != ";") {
                    nom.append(curseur.courant())
                    curseur.avancer()
                }
                paquet = nom.toString()
                continue
            }
            if (jeton == "@" && curseur.suitMotCleType()) {
                // @interface (annotation) : lu comme un type ci-dessous.
                break
            }
            if (jeton in MOTS_CLE_TYPE) break
            curseur.avancer()
        }

        val types = mutableListOf<TypeProjet>()
        lireCorps(curseur, paquet, emptyList(), uri, types)
        return ContributionFichier(uri, paquet, types, emptyList(), emptyList())
    }

    /**
     * Lit le corps d'un contexte (fichier ou classe) jusqu'à sa fermeture :
     * types déclarés (récursifs) et membres solitaires consommés.
     *
     * @param curseur les jetons ;
     * @param paquet le package du fichier ;
     * @param chemin les noms des types englobants (vide au fichier) ;
     * @param uri l'identifiant du fichier ;
     * @param types l'accumulateur des types lus.
     */
    private fun lireCorps(
        curseur: Curseur,
        paquet: String,
        chemin: List<String>,
        uri: String,
        types: MutableList<TypeProjet>,
    ) {
        while (curseur.aUnSuivant()) {
            val jeton = curseur.courant()
            when {
                jeton == "}" && chemin.isNotEmpty() -> return

                jeton == ";" || jeton == "," || jeton == "." -> curseur.avancer()

                jeton == "{" -> curseur.sauterBalances("{", "}") // bloc statique ou d'instance

                jeton == "@" -> {
                    if (curseur.suitMotCleType()) {
                        lireType(curseur, paquet, chemin, uri, types)?.let(types::add)
                    } else {
                        curseur.avancer()
                        curseur.sauterAnnotations()
                    }
                }

                jeton in MOTS_CLE_TYPE -> lireType(curseur, paquet, chemin, uri, types)?.let(types::add)

                else -> lireMembre(curseur, membres = null, nomClasse = "")
            }
        }
    }

    /**
     * Lit une déclaration de type Java : nom, clause `extends`/`implements`
     * de NIVEAU CLASSE, puis son corps (récursif — les types imbriqués
     * entrent au modèle sur leur nom simple).
     *
     * @return le type lu ; `null` si la déclaration est illisible (le
     * curseur a alors avancé au mieux — tolérant).
     */
    private fun lireType(
        curseur: Curseur,
        paquet: String,
        chemin: List<String>,
        uri: String,
        types: MutableList<TypeProjet>,
    ): TypeProjet? {
        val genreEcrit = curseur.courant()
        curseur.avancer()
        if (!curseur.aUnSuivant() || !estIdentifiant(curseur.courant())) return null
        val nom = curseur.courant()
        curseur.avancer()

        // Génériques de la déclaration SAUTÉS balancés : la contrainte d'un
        // paramètre de type n'est PAS un héritage (fiche 022).
        curseur.sauterGeneriques()

        val supertypes = lireClauseSupertypes(curseur, genreEcrit)

        // Les énumérations et records Java portent des membres IMPLICITS
        // (values/valueOf/name/ordinal, accesseurs de composants) : le
        // supertype marqueur rend le type non absence-fiable (jamais faux).
        if (genreEcrit == "enum") supertypes.add("Enum")
        if (genreEcrit == "record") supertypes.add("Record")

        if (!curseur.aUnSuivant() || curseur.courant() != "{") {
            // Source tronquée avant le corps : type déclaré, sans membres.
            return TypeProjet(
                uri = uri,
                fqcn = fqcn(paquet, chemin, nom),
                nomSimple = nom,
                genre = GenreTypeProjet.JAVA,
                supertypesEcrits = supertypes,
                membres = emptyList(),
                estData = false,
                entreesEnum = emptyList(),
            )
        }

        curseur.avancer()
        val membres = mutableListOf<MembreProjet>()
        lireMembresClasse(curseur, paquet, chemin + nom, uri, types, membres)
        return TypeProjet(
            uri = uri,
            fqcn = fqcn(paquet, chemin, nom),
            nomSimple = nom,
            genre = GenreTypeProjet.JAVA,
            supertypesEcrits = supertypes,
            membres = membres,
            estData = false,
            entreesEnum = emptyList(),
        )
    }

    /**
     * Lit la clause `extends`/`implements` de niveau classe (jusqu'à
     * l'accolade ouvrante).
     */
    private fun lireClauseSupertypes(curseur: Curseur, genreEcrit: String): MutableList<String> {
        val supertypes = mutableListOf<String>()
        while (curseur.aUnSuivant()) {
            when (curseur.courant()) {
                "extends", "implements" -> {
                    val mot = curseur.courant()
                    curseur.avancer()
                    while (curseur.aUnSuivant()) {
                        if (estIdentifiant(curseur.courant())) {
                            val nomLu = lireNomType(curseur)
                            if (nomLu != null) supertypes.add(nomLu)
                            if (mot == "extends" && genreEcrit != "interface") break
                        } else if (curseur.courant() == ",") {
                            curseur.avancer()
                        } else {
                            break
                        }
                    }
                }
                "permits" -> {
                    curseur.avancer()
                    while (curseur.aUnSuivant() && curseur.courant() != "{") curseur.avancer()
                }
                "{" -> return supertypes
                else -> curseur.avancer()
            }
        }
        return supertypes
    }

    /**
     * Lit les membres d'un corps de classe : méthodes (avec les propriétés
     * synthétiques de leurs accesseurs), champs, types imbriqués (récursifs).
     */
    private fun lireMembresClasse(
        curseur: Curseur,
        paquet: String,
        chemin: List<String>,
        uri: String,
        types: MutableList<TypeProjet>,
        membres: MutableList<MembreProjet>,
    ) {
        val nomClasse = chemin.lastOrNull().orEmpty()
        while (curseur.aUnSuivant()) {
            val jeton = curseur.courant()
            when {
                jeton == "}" -> {
                    curseur.avancer()
                    return
                }

                jeton == ";" || jeton == "," || jeton == "." -> curseur.avancer()

                jeton == "{" -> curseur.sauterBalances("{", "}")

                jeton == "@" -> {
                    if (curseur.suitMotCleType()) {
                        lireType(curseur, paquet, chemin, uri, types)?.let { type ->
                            types.add(type)
                            // L'imbriquée est AUSSI une DÉCLARATION MEMBRE du
                            // conteneur (vague 2c — fiche 029 : la voie du
                            // sélecteur `Aquarium.Bac` résout par `Bac`).
                            membres.add(MembreProjet(type.nomSimple, GenreMembreProjet.TYPE_IMBRIQUE, null))
                        }
                    } else {
                        curseur.avancer()
                        curseur.sauterAnnotations()
                    }
                }

                jeton in MOTS_CLE_TYPE -> lireType(curseur, paquet, chemin, uri, types)?.let { type ->
                    types.add(type)
                    membres.add(MembreProjet(type.nomSimple, GenreMembreProjet.TYPE_IMBRIQUE, null))
                }

                jeton in MODIFICATEURS -> curseur.avancer()

                else -> lireMembre(curseur, membres, nomClasse)
            }
        }
    }

    /**
     * Lit un MEMBRE : méthode (accesseurs compris) ou champ — les corps de
     * méthode sont consommés balancés (leurs variables ne comptent pas).
     *
     * @param curseur les jetons ;
     * @param membres la collecte (`null` = juste consommer) ;
     * @param nomClasse le nom de la classe englobante (constructeurs écartés).
     */
    private fun lireMembre(curseur: Curseur, membres: MutableList<MembreProjet>?, nomClasse: String) {
        if (!curseur.aUnSuivant() || !estIdentifiant(curseur.courant())) {
            if (curseur.aUnSuivant()) curseur.avancer()
            return
        }

        // Le TYPE du membre (éventuellement qualifié/générique/tableau) —
        // le nom SIMPLE tête est retenu comme type écrit (vague 2c : le
        // typage d'un sélecteur sur récepteur Java projet s'en sert,
        // `bac.capacite` type `Int`).
        val typeEcrit = nomKotlinDeTypeJava(lireNomType(curseur))
        while (curseur.aUnSuivant() && (curseur.courant() == "." || curseur.courant() == "<" || curseur.courant() == "[")) {
            when (curseur.courant()) {
                "." -> {
                    curseur.avancer()
                    if (curseur.aUnSuivant()) lireNomType(curseur)
                }
                "<" -> curseur.sauterBalances("<", ">")
                else -> {
                    curseur.avancer()
                    if (curseur.aUnSuivant() && curseur.courant() == "]") curseur.avancer()
                }
            }
        }

        // Le NOM du membre.
        if (!curseur.aUnSuivant() || !estIdentifiant(curseur.courant())) return
        val nom = curseur.courant()
        curseur.avancer()
        if (!curseur.aUnSuivant()) return

        when (curseur.courant()) {
            "(" -> {
                // Méthode (ou constructeur — écarté) : paramètres, `throws`,
                // puis corps OU point-virgule.
                curseur.sauterBalances("(", ")")
                while (curseur.aUnSuivant() && curseur.courant() != "{" && curseur.courant() != ";") {
                    curseur.avancer()
                }
                if (curseur.aUnSuivant() && curseur.courant() == "{") {
                    curseur.sauterBalances("{", "}")
                } else if (curseur.aUnSuivant() && curseur.courant() == ";") {
                    curseur.avancer()
                }
                if (membres != null && nom != nomClasse) {
                    membres.add(MembreProjet(nom, GenreMembreProjet.FONCTION, typeEcrit))
                    // Propriété synthétique d'accesseur (ADR 0219) :
                    // `getTitre()` → `titre` ; `estValide()` → `valide` ;
                    // `setStyle(...)` → `style` (vague 2c — le SETTER
                    // aussi, l'affectation `style = 1` résout par lui).
                    val propriete = proprieteSynthetique(nom)
                    if (propriete != null && membres.none { it.nom == propriete }) {
                        membres.add(MembreProjet(propriete, GenreMembreProjet.PROPRIETE, typeEcrit))
                    }
                }
            }
            "=", ";" -> {
                // Champ : une propriété Kotlin voit le champ Java.
                if (curseur.courant() == "=") {
                    while (curseur.aUnSuivant() && curseur.courant() != ";") {
                        if (curseur.courant() == "{") {
                            curseur.sauterBalances("{", "}")
                        } else {
                            curseur.avancer()
                        }
                    }
                }
                if (curseur.aUnSuivant() && curseur.courant() == ";") curseur.avancer()
                if (membres != null && nom != nomClasse) {
                    membres.add(MembreProjet(nom, GenreMembreProjet.PROPRIETE, typeEcrit))
                }
            }
            else -> Unit // forme inattendue : le tour suivant avance
        }
    }

    /**
     * Le nom de type KOTLIN d'un type Java écrit (vague 2c — le typage des
     * membres Java : `int` → `Int`, `String` → `String` ; `null` si absent).
     */
    private fun nomKotlinDeTypeJava(nomJava: String?): String? {
        if (nomJava == null) return null
        return when (nomJava) {
            "int" -> "Int"; "long" -> "Long"; "short" -> "Short"; "byte" -> "Byte"
            "double" -> "Double"; "float" -> "Float"; "char" -> "Char"; "boolean" -> "Boolean"
            "void" -> "Unit"
            else -> nomJava
        }
    }

    /** Le nom de propriété synthétique d'un accesseur (`getTitre` → `titre`). */
    private fun proprieteSynthetique(nomMethode: String): String? {
        when {
            nomMethode.length > 3 && nomMethode.startsWith("get") -> {
                val reste = nomMethode.substring(3)
                return if (reste.length > 1 && reste[1].isUpperCase()) reste
                else reste.replaceFirstChar { it.lowercaseChar() }
            }
            nomMethode.length > 2 && nomMethode.startsWith("is") -> {
                val reste = nomMethode.substring(2)
                return if (reste.length > 1 && reste[0].isUpperCase() && reste[1].isUpperCase()) reste
                else reste.replaceFirstChar { it.lowercaseChar() }
            }
            nomMethode.length > 3 && nomMethode.startsWith("set") -> {
                // Vague 2c : le SETTER synthétise AUSSI sa propriété
                // (`setTitle` → `title`) — l'AFFECTATION nue y résout
                // (ADR 0219, miroir de la vue JVM).
                val reste = nomMethode.substring(3)
                return if (reste.length > 1 && reste[1].isUpperCase()) reste
                else reste.replaceFirstChar { it.lowercaseChar() }
            }
            else -> return null
        }
    }

    /** Lit un nom de type (identifiant simple, génériques exclus). */
    private fun lireNomType(curseur: Curseur): String? {
        if (!curseur.aUnSuivant() || !estIdentifiant(curseur.courant())) return null
        val nom = curseur.courant()
        curseur.avancer()
        curseur.sauterGeneriques()
        return nom
    }

    /** Le FQCN d'un type Java (package + chemin d'englobants + nom — le SÉPARATEUR compte, fiche 029 : `Aquarium.Bac`). */
    private fun fqcn(paquet: String, chemin: List<String>, nom: String): String {
        val base = if (paquet.isEmpty()) "" else "$paquet."
        val englobants = if (chemin.isEmpty()) "" else chemin.joinToString(".") + "."
        return base + englobants + nom
    }

    /** Un identifiant Java : lettres, chiffres, `_`, `$` (ni mot-clé). */
    private fun estIdentifiant(jeton: String): Boolean {
        if (jeton.isEmpty()) return false
        if (jeton[0].isDigit()) return false
        if (jeton in MOTS_CLE_TYPE) return false
        return jeton.all { it.isLetterOrDigit() || it == '_' || it == '$' }
    }

    /**
     * Nettoie la source : commentaires et contenu des chaînes/caractères
     * remplacés par des espaces (les fins de ligne restent) — le lecteur ne
     * se laisse pas piéger par un `class` écrit dans un texte.
     */
    private fun nettoyer(texte: String): String {
        val caracteres = texte.toCharArray()
        var i = 0
        while (i < caracteres.size) {
            val c = caracteres[i]
            when {
                c == '/' && i + 1 < caracteres.size && caracteres[i + 1] == '/' -> {
                    while (i < caracteres.size && caracteres[i] != '\n') {
                        caracteres[i] = ' '
                        i++
                    }
                }
                c == '/' && i + 1 < caracteres.size && caracteres[i + 1] == '*' -> {
                    caracteres[i] = ' '
                    caracteres[i + 1] = ' '
                    i += 2
                    while (i < caracteres.size &&
                        !(caracteres[i] == '*' && i + 1 < caracteres.size && caracteres[i + 1] == '/')
                    ) {
                        if (caracteres[i] != '\n') caracteres[i] = ' '
                        i++
                    }
                    if (i < caracteres.size) {
                        caracteres[i] = ' '
                        if (i + 1 < caracteres.size) caracteres[i + 1] = ' '
                        i += 2
                    }
                }
                c == '"' -> {
                    caracteres[i] = ' '
                    i++
                    while (i < caracteres.size && caracteres[i] != '"') {
                        if (caracteres[i] != '\n') caracteres[i] = ' '
                        i++
                    }
                    if (i < caracteres.size) {
                        caracteres[i] = ' '
                        i++
                    }
                }
                c == '\'' -> {
                    caracteres[i] = ' '
                    i++
                    while (i < caracteres.size && caracteres[i] != '\'') {
                        caracteres[i] = ' '
                        i++
                    }
                    if (i < caracteres.size) {
                        caracteres[i] = ' '
                        i++
                    }
                }
                else -> i++
            }
        }
        return String(caracteres)
    }

    /**
     * Jetonne la source nettoyée : les identifiants/mots en un jeton, TOUT
     * autre caractère significatif (symboles `{`, `;`, `(`, `=`, `<`…) en un
     * jeton à lui seul — les blancs séparent.
     */
    private fun jetonner(texte: String): List<String> {
        val jetons = mutableListOf<String>()
        val courant = StringBuilder()
        fun pousser() {
            if (courant.isNotEmpty()) {
                jetons.add(courant.toString())
                courant.setLength(0)
            }
        }
        for (c in texte) {
            when {
                c.isLetterOrDigit() || c == '_' || c == '$' -> courant.append(c)
                c.isWhitespace() -> pousser()
                else -> {
                    pousser()
                    jetons.add(c.toString())
                }
            }
        }
        pousser()
        return jetons
    }

    /** Le curseur de jetons (avance-only, tolérant). */
    private class Curseur(private val jetons: List<String>) {
        private var position: Int = 0

        /** Y a-t-il un jeton courant ? */
        fun aUnSuivant(): Boolean = position < jetons.size

        /** Le jeton courant. */
        fun courant(): String = jetons[position]

        /** Avance d'un jeton. */
        fun avancer() {
            position++
        }

        /** Le jeton courant est-il `@` suivi d'un mot-clé de type (`@interface`) ? */
        fun suitMotCleType(): Boolean {
            if (courant() != "@") return false
            val suivant = jetons.getOrNull(position + 1) ?: return false
            return suivant == "interface"
        }

        /** Saute une séquence équilibrée ouvrante/fermante déjà OUVERTE. */
        fun sauterBalances(ouvrant: String, fermant: String) {
            var profondeur = 0
            while (aUnSuivant()) {
                val jeton = courant()
                avancer()
                if (jeton == ouvrant) profondeur++
                if (jeton == fermant) {
                    profondeur--
                    if (profondeur <= 0) return
                }
            }
        }

        /** Saute les génériques `<...>` s'ouvrant au jeton courant. */
        fun sauterGeneriques() {
            if (aUnSuivant() && courant() == "<") sauterBalances("<", ">")
        }

        /** Saute une annotation (`@Nom(...)` ou `@paquet.Nom`) — le `@` est consommé. */
        fun sauterAnnotations() {
            while (aUnSuivant() && courant() == "@") avancer()
            if (aUnSuivant() && estIdentifiant(courant())) {
                avancer()
                if (aUnSuivant() && courant() == "(") sauterBalances("(", ")")
                if (aUnSuivant() && courant() == ".") {
                    avancer()
                    if (aUnSuivant() && estIdentifiant(courant())) {
                        avancer()
                        if (aUnSuivant() && courant() == "(") sauterBalances("(", ")")
                    }
                }
            }
        }
    }
}
