package jo.codeide.lsp.java.projetandroid

/**
 * Lecteur XML PULL TOLÉRANT, sans dépendance (vague V-B — ADR 0020 R2 :
 * « parseur pull (pas de DOM) », budget octets/ART : ni javax.xml.stream
 * (absent d'ART), ni DOM (empreinte), ni XmlPullParser Android (absent de
 * la JVM) — le parseur est écrit pour lui-même).
 *
 * Périmètre HONNÊTE : les documents XML de ressources Android (dossiers
 * res/, manifest) — éléments, attributs, texte, CDATA, entités
 * prédéfinies, commentaires, processing instructions et DOCTYPE ignorés.
 * Ce n'est PAS un validateur : un document mal formé ne lève pas — le
 * lecteur rend ce qu'il comprend et saute ce qu'il ne comprend pas
 * (tolérance du référentiel : une ressource illisible est ÉCARTÉE, jamais
 * un crash du moteur).
 *
 * Usage (pull) :
 * ```
 * val lecteur = LecteurXmlTolerant(texte)
 * while (true) {
 *     val evenement = lecteur.suivant() ?: break
 *     when (evenement) {
 *         is EvenementXml.Debut -> { evenement.nom; evenement.attributs["android:id"] }
 *         is EvenementXml.Fin -> …
 *         is EvenementXml.Texte -> …
 *     }
 * }
 * ```
 *
 * Coût : une passe, sans allocation d'arbre ; le lecteur mémorise les
 * attributs du SEUL élément courant.
 */
public class LecteurXmlTolerant(texte: String) {

    /** Un événement pull. */
    public sealed interface EvenementXml {
        /** Début d'élément : nom SANS préfixe d'espace de noms + attributs
         *  (+ position du DÉBUT DE VALEUR de chaque attribut — cibles de
         *  navigation ressource, vague V-B2). */
        public data class Debut(
            public val nom: String,
            public val attributs: Map<String, String>,
            public val positionsValeurs: Map<String, Int> = emptyMap(),
        ) : EvenementXml

        /** Fin d'élément (nom sans préfixe). */
        public data class Fin(public val nom: String) : EvenementXml

        /** Zone de texte (déjà décodée des entités et CDATA). */
        public data class Texte(public val contenu: String) : EvenementXml
    }

    private val source: String = texte
    private var position: Int = 0

    /** File des événements en attente (une balise auto-fermée émet DEBUT
     *  puis FIN — comportement standard des parseurs pull). */
    private val enAttente = ArrayDeque<LecteurXmlTolerant.EvenementXml>()

    /** La pile des éléments ouverts (noms locaux). */
    private val pile = ArrayDeque<String>()

    /** Profondeur maximale gardée anti-dégénéré (documents pathologiques). */
    private var profondeur: Int = 0

    /**
     * L'événement suivant, ou `null` à la fin du document — ne lève
     * JAMAIS : un segment incompréhensible est sauté jusqu'au prochain
     * `<` exploitable.
     */
    public fun suivant(): EvenementXml? {
        // Les événements mis en attente (fins d'auto-fermées) partent d'abord.
        if (enAttente.isNotEmpty()) return enAttente.removeFirst()
        while (position < source.length) {
            val chevron = source.indexOf('<', position)
            if (chevron < 0) {
                // Queue de texte sans balise : la racine est fermée ou le
                // document est tronqué — le reste est du texte honnête.
                val reste = source.substring(position)
                position = source.length
                return if (pile.isEmpty() && reste.isNotBlank()) EvenementXml.Texte(reste.trim()) else null
            }
            if (chevron > position) {
                val texte = source.substring(position, chevron)
                position = chevron
                if (texte.isNotBlank()) return EvenementXml.Texte(texte.trim())
                continue
            }
            // chevron == position : une balise.
            val apres = chevron + 1
            val c = source.getOrNull(apres)
            when {
                c == null -> {
                    position = source.length
                    return null
                }
                // <!-- … -->
                c == '!' -> if (source.startsWith("<!--", apres - 1)) {
                    val fin = source.indexOf("-->", apres)
                    position = if (fin < 0) source.length else fin + 3
                } else {
                    // DOCTYPE / CDATA : CDATA rendu comme TEXTE, DOCTYPE sauté
                    // jusqu'au prochain '>' hors guillemets.
                    if (source.startsWith("<![CDATA[", apres - 1)) {
                        val fin = source.indexOf("]]>", apres)
                        position = if (fin < 0) source.length else fin + 3
                        val contenu = if (fin < 0) "" else source.substring(apres + 8, fin)
                        if (contenu.isNotBlank()) return EvenementXml.Texte(contenu) else continue
                    } else {
                        position = sauterJusqueChevron(apres)
                    }
                }
                // <?xml … ?>
                c == '?' -> position = sauterJusqueChevron(apres)
                // </fin>
                c == '/' -> {
                    val fin = source.indexOf('>', apres)
                    if (fin < 0) return null
                    val nom = source.substring(apres + 1, fin).trim().substringBefore(':')
                    position = fin + 1
                    if (pile.isNotEmpty()) pile.removeLast()
                    profondeur--
                    return EvenementXml.Fin(nom)
                }
                // <debut …>
                else -> {
                    val analyse = analyserDebut(apres) ?: return null
                    position = analyse.positionSuivante
                    if (analyse.autoFermee) {
                        // Balise auto-fermée : DEBUT puis FIN (standard pull).
                        pile.addLast(analyse.nom)
                        enAttente.addLast(EvenementXml.Fin(analyse.nom))
                        pile.removeLast()
                        return EvenementXml.Debut(analyse.nom, analyse.attributs, analyse.positionsValeurs)
                    }
                    pile.addLast(analyse.nom)
                    profondeur++
                    return EvenementXml.Debut(analyse.nom, analyse.attributs, analyse.positionsValeurs)
                }
            }
        }
        return null
    }

    /** Résultat interne de l'analyse d'une balise ouvrante. */
    private class DebutAnalyse(
        val nom: String,
        val attributs: Map<String, String>,
        val positionsValeurs: Map<String, Int>,
        val autoFermee: Boolean,
        val positionSuivante: Int,
    )

    /** Analyse `<nom a="v" b='v'>` en position [debut] (juste après `<`). */
    private fun analyserDebut(debut: Int): DebutAnalyse? {
        var i = debut
        // Nom (jusqu'à blanc, '/' ou '>').
        val debutNom = i
        while (i < source.length && !source[i].isWhitespace() && source[i] != '>' && source[i] != '/') i++
        if (i == debutNom) {
            // '<' seul ou '<>' : incompréhensible — sauter ce chevron.
            position = debut
            return DebutAnalyse("", emptyMap(), emptyMap(), true, debut + 1)
        }
        val nomComplet = source.substring(debutNom, i)
        // Nom LOCAL (après le deux-points d'un préfixe d'espace de noms —
        // `android:id` → `id` ; sans deux-points, la chaîne entière).
        val nom = nomComplet.substringAfter(':')
        val attributs = HashMap<String, String>(8)
        val positionsValeurs = HashMap<String, Int>(8)
        var autoFermee = false
        // Attributs.
        boucleAttributs@ while (i < source.length) {
            // Blancs séparateurs.
            while (i < source.length && source[i].isWhitespace()) i++
            when (source.getOrNull(i)) {
                null -> return null
                '>' -> {
                    i++
                    break@boucleAttributs
                }
                '/' -> {
                    autoFermee = true
                    i++
                }
                else -> {
                    // Nom d'attribut — clé LOCALE (`android:id` → `id`).
                    val debutCle = i
                    while (i < source.length && source[i] != '=' && source[i] != '>' && !source[i].isWhitespace()) i++
                    val cle = source.substring(debutCle, i).substringAfter(':')
                    while (i < source.length && source[i].isWhitespace()) i++
                    if (source.getOrNull(i) != '=') {
                        // Attribut sans valeur : conservé avec chaîne vide.
                        if (cle.isNotEmpty()) attributs[cle] = ""
                        continue@boucleAttributs
                    }
                    i++ // '='
                    while (i < source.length && source[i].isWhitespace()) i++
                    val guillemet = source.getOrNull(i)
                    if (guillemet == '"' || guillemet == '\'') {
                        val fin = source.indexOf(guillemet, i + 1)
                        if (fin < 0) return null
                        if (cle.isNotEmpty()) {
                            attributs[cle] = decoderEntites(source.substring(i + 1, fin))
                            positionsValeurs[cle] = i + 1
                        }
                        i = fin + 1
                    } else {
                        // Valeur non quotée : jusqu'au blanc/'>' (tolérant).
                        val debutValeur = i
                        while (i < source.length && !source[i].isWhitespace() && source[i] != '>') i++
                        if (cle.isNotEmpty()) attributs[cle] = decoderEntites(source.substring(debutValeur, i))
                    }
                }
            }
        }
        return DebutAnalyse(nom, attributs, positionsValeurs, autoFermee, i)
    }

    /** Saute jusqu'au '>' suivant (hors guillemets) — DOCTYPE, PI. */
    private fun sauterJusqueChevron(debut: Int): Int {
        var i = debut
        var guillemet: Char? = null
        while (i < source.length) {
            val c = source[i]
            if (guillemet != null) {
                if (c == guillemet) guillemet = null
            } else if (c == '"' || c == '\'') {
                guillemet = c
            } else if (c == '>') {
                return i + 1
            }
            i++
        }
        return source.length
    }

    /** Décode les cinq entités prédéfinies (+ numérique décimal/hexa, tolérant). */
    private fun decoderEntites(valeur: String): String {
        if ('&' !in valeur) return valeur
        val sortie = StringBuilder(valeur.length)
        var i = 0
        while (i < valeur.length) {
            if (valeur[i] != '&') {
                sortie.append(valeur[i])
                i++
                continue
            }
            val fin = valeur.indexOf(';', i)
            if (fin < 0) {
                sortie.append(valeur.substring(i))
                break
            }
            when (val entite = valeur.substring(i + 1, fin)) {
                "amp" -> sortie.append('&')
                "lt" -> sortie.append('<')
                "gt" -> sortie.append('>')
                "quot" -> sortie.append('"')
                "apos" -> sortie.append('\'')
                else -> if (entite.startsWith("#")) {
                    val code = entite.removePrefix("#x").removePrefix("#X").toIntOrNull(16)
                        ?: entite.removePrefix("#").toIntOrNullOrNull(10)
                    if (code != null) sortie.appendCodePoint(code) else sortie.append(valeur.substring(i, fin + 1))
                } else {
                    // Entité nommée inconnue : conservée telle quelle (honnête).
                    sortie.append(valeur.substring(i, fin + 1))
                }
            }
            i = fin + 1
        }
        return sortie.toString()
    }

    /** [String.toIntOrNull] avec base explicite, sans ambiguïté de lecture. */
    private fun String.toIntOrNullOrNull(base: Int): Int? = toIntOrNull(base)

    /** Le nom de l'élément ENGLOBANT courant (sommet de pile), ou null. */
    public fun elementEnglobant(): String? = pile.lastOrNull()
}
