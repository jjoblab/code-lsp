package jo.codeide.lsp.java.renommage

import jo.codeide.lsp.java.diagnostics.Plage
import jo.codeide.lsp.java.interne.LectureAst
import jo.codeide.lsp.java.outils.EditionTexte
import jo.codeide.lsp.java.projetandroid.LecteurXmlTolerant
import jo.codeide.lsp.java.projetandroid.ReferentielRessources
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration
import org.eclipse.jdt.internal.compiler.ast.QualifiedNameReference
import org.eclipse.jdt.internal.compiler.lookup.FieldBinding
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * RENOMMAGE DE RESSOURCE (vague V-D TRANCHE 3 — écart G-028 : « pas de
 * rename de ressource (propagation code + XML) ») : `R.<type>.<nom>` au
 * caret renomme la ressource PARTOUT —
 *
 *  - dans le CODE Java : chaque référence `R.<type>.<nom>` (par BINDINGS —
 *    le champ light de la classe `ns.R$<type>`, candidats de l'index de
 *    mots, homonymes d'un autre type R jamais retouchés) ;
 *  - dans les XML du module : la DÉCLARATION (`name="<nom>"` des values,
 *    `<item type="…">`), les RÉFÉRENCES `@<type>/<nom>`, `@+<type>/<nom>`
 *    (déclaration-inline des ids), `?<type>/<nom>` (attributs de thème) et
 *    les expressions `R.<type>.<nom>` du data binding (balayage TEXTE des
 *    valeurs d'attributs — le nom y vit littéralement).
 *
 * **Refus honnêtes (R1)** :
 *  - les ressources FICHIER (`layout`, `drawable`, `raw`…) : le nom VIT
 *    dans le nom du fichier — le renommage de fichier appartient au
 *    client (LSP `RenameFile`), pas au moteur de renommage Java ;
 *  - les champs de `declare-styleable` (`R.styleable.X_a`) : l'attribut
 *    appartient à la structure du styleable, sa restructuration suit ;
 *  - les ressources d'un autre paquet (`android.R.…`) : hors module.
 *
 * Les collisions (identifiant invalide, nom déjà pris du même type) se
 * servent par [conflits] (ADR 0014 : le moteur signale, le client décide).
 *
 * @param racines les racines sources du module.
 * @param indexMots l'index de mots des sources (candidats Java).
 * @param resoudre résout une unité (chemin logique, texte) en arbre bindé.
 * @param referentiel le référentiel de ressources (V-B — source de vérité).
 * @param namespace le namespace du module (`ns.R$<type>`).
 * @param dossiersRes les dossiers `res/` (balayage XML complet).
 */
public class RenommageRessources(
    private val racines: List<Path>,
    private val indexMots: jo.codeide.lsp.java.navigation.IndexMotsSources,
    private val resoudre: (String, String) -> CompilationUnitDeclaration?,
    private val referentiel: ReferentielRessources,
    private val namespace: String,
    private val dossiersRes: List<Path>,
) {

    /**
     * La cible de renommage de ressource au caret : le type R, le nom, le
     * jeton et le motif de refus honnête le cas échéant.
     *
     * @property type le type R (`string`, `id`, `layout`…).
     * @property nom le nom de la ressource.
     * @property jeton la plage du JETON `nom` dans la source Java.
     * @property refus null = renommable ; « fichier » (ressource-fichier —
     *   le nom vit dans le nom du fichier) ; « styleable » (champ X_a) ;
     *   « inconnu » (le référentiel ne connaît pas la ressource).
     */
    public data class CibleRessource(
        public val type: String,
        public val nom: String,
        public val jeton: Plage,
        public val refus: String?,
    )

    // ------------------------------------------------------------------ //
    // Cible au caret
    // ------------------------------------------------------------------ //

    /**
     * La ressource au caret (un champ de classe light `R$<type>` — la
     * référence la plus profonde couvrant l'offset, par bindings).
     *
     * @param chemin le chemin logique du document Java.
     * @param source la source Java complète (tampon ouvert).
     * @param offset le caret (sur le segment `<nom>` de `R.<type>.<nom>`).
     * @return la cible, ou null (caret hors référence de ressource).
     */
    public fun cible(chemin: String, source: String, offset: Int): CibleRessource? {
        val arbre = runCatching { resoudre(chemin, source) }.getOrNull() ?: return null
        val collecteur = CollecteurCibleRessource(source, offset, namespace)
        LectureAst.traverserResilient(arbre, collecteur)
        val (type, nom, jeton) = collecteur.cible ?: return null
        val refus = when {
            type == "styleable" -> "styleable"
            referentiel.ressource(type, nom) == null -> "inconnu"
            estRessourceFichier(type, nom) -> "fichier"
            else -> null
        }
        return CibleRessource(type, nom, jeton, refus)
    }

    // ------------------------------------------------------------------ //
    // Éditions
    // ------------------------------------------------------------------ //

    /**
     * Les éditions du renommage de ressource, par fichier (Java et XML) :
     * chaque occurrence du nom (code et XML) remplacée par le nouveau.
     *
     * @param chemin le chemin logique du document Java interrogé.
     * @param source la source Java complète (tampon ouvert — overlay).
     * @param offset le caret (sur le nom de la ressource).
     * @param nouveauNom le nouveau nom de la ressource.
     * @return les éditions par fichier DISQUE (vide si refus/inconnu).
     */
    public fun editions(
        chemin: String,
        source: String,
        offset: Int,
        nouveauNom: String,
    ): Map<String, List<EditionTexte>> {
        val cible = cible(chemin, source, offset)
        if (cible == null || cible.refus != null || nouveauNom == cible.nom) return emptyMap()
        val resultat = LinkedHashMap<String, MutableList<EditionTexte>>()

        // Le CODE Java : document courant (overlay) puis candidats de
        // l'index (texte disque).
        val arbre = runCatching { resoudre(chemin, source) }.getOrNull() ?: return emptyMap()
        val cheminCourant = fichierCourant(chemin)?.toString() ?: chemin
        run {
            val collecteur = CollecteurOccurrencesR(source, namespace, cible.type, cible.nom)
            LectureAst.traverserResilient(arbre, collecteur)
            for (plage in collecteur.plages) {
                resultat.getOrPut(cheminCourant) { mutableListOf() }
                    .add(EditionTexte(plage.debutOffset, plage.finOffset - plage.debutOffset, nouveauNom))
            }
        }
        val disqueCourant = fichierCourant(chemin)
        accumuler(resultat, propagerJavaDisque(cible, nouveauNom, disqueCourant))

        // Les XML du module : déclarations, références @type/, @+type/,
        // ?type/ et expressions R.type.nom (data binding).
        accumuler(resultat, propagerXml(cible, nouveauNom, null, null))
        val triees = LinkedHashMap<String, List<EditionTexte>>()
        for ((fichier, liste) in resultat) triees[fichier] = liste.sortedBy { it.offset }
        return triees
    }

    /**
     * La ressource au caret DANS UN DOCUMENT XML (complément de catalogue
     * — ADR 0032, « rename depuis un XML ») : la DÉCLARATION
     * (`name="&lt;nom&gt;"` d’un élément typé des values) ou le SEGMENT
     * `&lt;nom&gt;` d’une RÉFÉRENCE (`@&lt;type&gt;/&lt;nom&gt;`,
     * `@+&lt;type&gt;/&lt;nom&gt;`, `?&lt;type&gt;/&lt;nom&gt;`, expression
     * `R.&lt;type&gt;.&lt;nom&gt;` du data binding) — les MÊMES refus honnêtes
     * que le renommage depuis le code Java.
     *
     * @param fichier le fichier XML DU MODULE (path disque).
     * @param texte le texte XML complet (tampon OUVERT — fait foi).
     * @param offset le caret.
     * @return la cible, ou null (caret hors ressource / fichier hors
     *   dossiers res du module).
     */
    public fun cibleXml(fichier: Path, texte: String, offset: Int): CibleRessource? {
        if (!dossiersRes.any { dossier -> fichier.startsWith(dossier) }) return null
        val trouvee = ressourceAuCaretXml(fichier, texte, offset) ?: return null
        val refus = when {
            trouvee.type == "styleable" -> "styleable"
            referentiel.ressource(trouvee.type, trouvee.nom) == null -> "inconnu"
            estRessourceFichier(trouvee.type, trouvee.nom) -> "fichier"
            else -> null
        }
        return CibleRessource(trouvee.type, trouvee.nom, trouvee.jeton, refus)
    }

    /** Une ressource localisée dans un document XML (interne). */
    private data class RessourceXml(
        val type: String,
        val nom: String,
        val jeton: Plage,
    )

    /** La ressource couvrant le caret dans UN fichier XML (déclaration ou référence). */
    private fun ressourceAuCaretXml(fichier: Path, texte: String, offset: Int): RessourceXml? {
        val dossierParent = fichier.parent ?: return null
        val typeDossier = dossierParent.fileName.toString().substringBefore('-')
        val estValues = typeDossier == "values"
        val lecteur = LecteurXmlTolerant(texte)
        while (true) {
            val evenement = lecteur.suivant() ?: break
            if (evenement !is LecteurXmlTolerant.EvenementXml.Debut) continue

            // La DÉCLARATION : le caret porte la VALEUR de name="…" d’un
            // élément typé des values.
            if (estValues) {
                val typeDeclare = typeDeclareDe(evenement.nom, evenement.attributs)
                if (typeDeclare != null) {
                    val nom = evenement.attributs["name"]
                    val position = evenement.positionsValeurs["name"]
                    if (nom != null && position != null && nom.isNotEmpty() &&
                        offset >= position && offset < position + nom.length &&
                        position + nom.length <= texte.length
                    ) {
                        return RessourceXml(typeDeclare, nom, Plage(position, position + nom.length))
                    }
                }
            }

            // Les RÉFÉRENCEs dans chaque valeur d’attribut : le segment
            // `&lt;nom&gt;` couvrant le caret.
            for ((cle, position) in evenement.positionsValeurs) {
                if (cle == "xmlns") continue
                val fin = finValeurBrute(texte, position) ?: continue
                if (fin <= position) continue
                for (reference in referencesDansValeur(texte, position, fin)) {
                    if (offset >= reference.plage.debutOffset && offset <= reference.plage.finOffset) {
                        return RessourceXml(reference.type, reference.nom, reference.plage)
                    }
                }
            }
        }
        return null
    }

    /**
     * Les éditions du renommage de ressource DEPUIS UN DOCUMENT XML
     * (complément de catalogue — ADR 0032) : la MÊME propagation que
     * depuis le code Java (code Java par bindings + XML du module), le
     * document XML COURANT servi depuis son TAMPON OUVERT (fait foi —
     * les autres fichiers depuis le disque).
     *
     * @param fichier le fichier XML du module (path disque).
     * @param texte le texte XML complet (tampon ouvert).
     * @param offset le caret (sur le nom de la ressource).
     * @param nouveauNom le nouveau nom.
     * @return les éditions par fichier DISQUE (vide si refus/inconnu).
     */
    public fun editionsXml(
        fichier: Path,
        texte: String,
        offset: Int,
        nouveauNom: String,
    ): Map<String, List<EditionTexte>> {
        val cible = cibleXml(fichier, texte, offset)
        if (cible == null || cible.refus != null || nouveauNom == cible.nom) return emptyMap()
        val resultat = LinkedHashMap<String, MutableList<EditionTexte>>()
        accumuler(resultat, propagerJavaDisque(cible, nouveauNom, null))
        accumuler(resultat, propagerXml(cible, nouveauNom, fichier, texte))
        val triees = LinkedHashMap<String, List<EditionTexte>>()
        for ((chemin, liste) in resultat) triees[chemin] = liste.sortedBy { it.offset }
        return triees
    }

    /**
     * Les COLLISIONS typées du renommage DEPUIS UN DOCUMENT XML (le
     * référentiel reste la source de vérité).
     *
     * @param fichier le fichier XML du module.
     * @param texte le texte XML complet (tampon ouvert).
     * @param offset le caret.
     * @param nouveauNom le nouveau nom proposé.
     * @return les collisions (vide = renommage sûr).
     */
    public fun conflitsXml(
        fichier: Path,
        texte: String,
        offset: Int,
        nouveauNom: String,
    ): List<ConflitRenommage> {
        val cible = cibleXml(fichier, texte, offset) ?: return emptyList()
        if (cible.refus != null) return emptyList() // le refus n’est pas une collision
        return conflitsDeCible(cible, nouveauNom)
    }

    /** Les collisions typées d’une cible de ressource (commun Java/XML). */
    private fun conflitsDeCible(cible: CibleRessource, nouveauNom: String): List<ConflitRenommage> {
        val collisions = ArrayList<ConflitRenommage>()
        if (!estIdentifiantValide(nouveauNom)) {
            collisions.add(
                ConflitRenommage(
                    GenreConflit.IDENTIFIANT_INVALIDE,
                    "« $nouveauNom » n’est pas un identifiant Java valide (un nom de ressource devient un champ R).",
                ),
            )
        }
        if (nouveauNom != cible.nom && referentiel.ressource(cible.type, nouveauNom) != null) {
            collisions.add(
                ConflitRenommage(
                    GenreConflit.NOM_PRIS,
                    "Une ressource ${cible.type} porte déjà le nom « $nouveauNom ».",
                ),
            )
        }
        return collisions
    }

    /** La propagation COMMUNE — le CODE JAVA disque (candidats de l'index de mots, l'exclu déjà servi en overlay). */
    private fun propagerJavaDisque(
        cible: CibleRessource,
        nouveauNom: String,
        exclu: Path?,
    ): Map<String, List<EditionTexte>> {
        val resultat = LinkedHashMap<String, MutableList<EditionTexte>>()
        for (fichier in indexMots.fichiersPour(cible.nom)) {
            if (exclu != null && fichier == exclu) continue // déjà servi (overlay)
            val texte = try {
                fichier.readText()
            } catch (_: Exception) {
                continue
            }
            val arbreFichier = runCatching { resoudre(fichier.toString(), texte) }.getOrNull() ?: continue
            val collecteur = CollecteurOccurrencesR(texte, namespace, cible.type, cible.nom)
            LectureAst.traverserResilient(arbreFichier, collecteur)
            for (plage in collecteur.plages) {
                resultat.getOrPut(fichier.toString()) { mutableListOf() }
                    .add(EditionTexte(plage.debutOffset, plage.finOffset - plage.debutOffset, nouveauNom))
            }
        }
        return resultat
    }

    /** La propagation COMMUNE — les XML du module (le document courant en tampon ouvert, fait foi). */
    private fun propagerXml(
        cible: CibleRessource,
        nouveauNom: String,
        cheminXmlCourant: Path?,
        texteXmlCourant: String?,
    ): Map<String, List<EditionTexte>> {
        val resultat = LinkedHashMap<String, MutableList<EditionTexte>>()
        for (fichier in fichiersXml()) {
            val estCourant = cheminXmlCourant != null && fichier == cheminXmlCourant
            val texte = if (estCourant && texteXmlCourant != null) {
                texteXmlCourant
            } else {
                try {
                    fichier.readText()
                } catch (_: Exception) {
                    continue
                }
            }
            for (plage in occurrencesXml(fichier, texte, cible.type, cible.nom)) {
                resultat.getOrPut(fichier.toString()) { mutableListOf() }
                    .add(EditionTexte(plage.debutOffset, plage.finOffset - plage.debutOffset, nouveauNom))
            }
        }
        return resultat
    }

    /** Accumule une map d'éditions dans une autre (éditions par fichier concaténées). */
    private fun accumuler(cible: LinkedHashMap<String, MutableList<EditionTexte>>, source: Map<String, List<EditionTexte>>) {
        for ((chemin, liste) in source) {
            cible.getOrPut(chemin) { mutableListOf() }.addAll(liste)
        }
    }

    // ------------------------------------------------------------------ //
    // Conflits
    // ------------------------------------------------------------------ //

    /**
     * Les COLLISIONS typées du renommage de ressource proposé :
     * identifiant invalide (le nom devient un champ Java), nom déjà pris
     * du MÊME type (le référentiel est la source de vérité).
     *
     * @param chemin le chemin logique du document Java.
     * @param source la source Java complète.
     * @param offset le caret.
     * @param nouveauNom le nouveau nom proposé.
     * @return les collisions (vide = renommage sûr).
     */
    public fun conflits(
        chemin: String,
        source: String,
        offset: Int,
        nouveauNom: String,
    ): List<ConflitRenommage> {
        val cible = cible(chemin, source, offset) ?: return emptyList()
        if (cible.refus != null) return emptyList() // le refus n'est pas une collision
        return conflitsDeCible(cible, nouveauNom)
    }

    // ------------------------------------------------------------------ //
    // Outils
    // ------------------------------------------------------------------ //

    /** Vrai ssi le type R désigne une ressource FICHIER (le nom vit dans le nom de fichier). */
    private fun estRessourceFichier(type: String, nom: String): Boolean {
        // Les ids et attrs vivent DANS les XML (`@+id/…`, `<attr>`) — jamais
        // un fichier ; les types values non plus (dossier `values*`).
        if (type == "id" || type == "attr" || type in TYPES_VALEURS) return false
        val ressource = referentiel.ressource(type, nom) ?: return true
        val dossier = ressource.fichierPrincipal.parent?.fileName?.toString() ?: return true
        return !dossier.startsWith("values")
    }

    /** Les types R de VALEUR (déclarés dans un XML values, pas dans un nom de fichier). */
    private companion object {
        private val TYPES_VALEURS: Set<String> = setOf(
            "string", "color", "dimen", "bool", "integer", "array", "plurals", "fraction", "style",
        )
        private val MOTS_CLES: Set<String> = setOf(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
            "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
            "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
            "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
            "volatile", "while", "true", "false", "null",
        )
    }

    /** Vrai ssi le nom est un identifiant Java valide (mot-clé exclu). */
    private fun estIdentifiantValide(nom: String): Boolean =
        nom.isNotEmpty() && nom[0].isJavaIdentifierStart() && nom.all { it.isJavaIdentifierPart() } && nom !in MOTS_CLES

    /** Le fichier disque d'un chemin logique (null si purement en mémoire). */
    private fun fichierCourant(chemin: String): Path? {
        runCatching {
            val direct = Path.of(chemin)
            if (Files.isRegularFile(direct)) return direct
        }
        for (racine in racines) {
            val candidat = racine.resolve(chemin)
            if (Files.isRegularFile(candidat)) return candidat
        }
        return null
    }

    /** Tous les XML des dossiers res/ (triés). */
    private fun fichiersXml(): List<Path> {
        val fichiers = ArrayList<Path>()
        for (dossier in dossiersRes) {
            if (!Files.isDirectory(dossier)) continue
            try {
                Files.walk(dossier).use { flux ->
                    flux.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".xml") }
                        .forEach { fichiers.add(it) }
                }
            } catch (_: Exception) {
                // Dossier illisible : écarté (tolérance).
            }
        }
        return fichiers.sorted()
    }

    /**
     * Les occurrences du nom dans UN fichier XML : la DÉCLARATION
     * (`name="<nom>"` des values, `<item type>`), les RÉFÉRENCES
     * `@<type>/<nom>`, `@+<type>/<nom>`, `?<type>/<nom>` et les
     * expressions `R.<type>.<nom>` du data binding — sur le texte BRUT
     * des valeurs (les entités décodées décaleraient les plages).
     */
    private fun occurrencesXml(fichier: Path, texte: String, type: String, nom: String): List<Plage> {
        val plages = ArrayList<Plage>()
        val dossierParent = fichier.parent ?: return plages
        val typeDossier = dossierParent.fileName.toString().substringBefore('-')
        val estValues = typeDossier == "values"
        val lecteur = LecteurXmlTolerant(texte)
        while (true) {
            val evenement = lecteur.suivant() ?: break
            if (evenement !is LecteurXmlTolerant.EvenementXml.Debut) continue

            // La DÉCLARATION : l'élément typé des values porte name="<nom>".
            if (estValues) {
                val typeDeclare = typeDeclareDe(evenement.nom, evenement.attributs)
                if (typeDeclare == type && evenement.attributs["name"] == nom) {
                    val position = evenement.positionsValeurs["name"]
                    if (position != null && position + nom.length <= texte.length) {
                        plages.add(Plage(position, position + nom.length))
                    }
                }
            }

            // Les RÉFÉRENCES dans chaque valeur d'attribut (texte brut —
            // de l'ouverture à la fermeture du guillemet, miroir du lecteur).
            for ((cle, position) in evenement.positionsValeurs) {
                if (cle == "xmlns") continue
                val fin = finValeurBrute(texte, position) ?: continue
                if (fin <= position) continue
                plages.addAll(occurrencesDansValeur(texte, position, fin, type, nom))
            }
        }
        return plages.sortedBy { it.debutOffset }
    }

    /**
     * Le type R DÉCLARÉ par un élément des values (`<string>` → `string`,
     * `<item type="id">` → `id`…) — null si l'élément ne déclare pas
     * une ressource values (commun au balayage et au caret XML).
     */
    private fun typeDeclareDe(nomElement: String, attributs: Map<String, String>): String? = when (nomElement) {
        "string-array", "integer-array" -> "array"
        "string", "color", "dimen", "bool", "integer", "array", "plurals", "fraction", "style" -> nomElement
        "item" -> if (attributs["type"] in setOf("id", "string", "color", "dimen", "bool", "integer")) {
            attributs["type"]
        } else {
            null
        }
        else -> null
    }

    /** La fin (exclusive) de la valeur BRUTE ouverte en [position] (le guillemet fermant — miroir du lecteur). */
    private fun finValeurBrute(texte: String, position: Int): Int? {
        val guillemet = texte.getOrNull(position - 1) ?: return null
        if (guillemet != '"' && guillemet != '\'') return null
        val fin = texte.indexOf(guillemet, position)
        return if (fin < 0) null else fin
    }

    /**
     * Une référence de ressource localisée dans une valeur BRUTE (interne).
     *
     * @property plage la plage du SEGMENT `<nom>` (offsets absolus).
     * @property type le type R lu (`string`, `id`…).
     * @property nom le nom lu.
     */
    private data class ReferenceValeur(
        val plage: Plage,
        val type: String,
        val nom: String,
    )

    /**
     * Les occurrences du nom dans la valeur BRUTE [debut, fin) : les
     * références `@+?(pkg:)?type/nom`, `?type/nom` (thème) et les
     * expressions `R.type.nom` (data binding). Le paquet explicite doit
     * être CELUI du module (`@ns:string/foo`) ; sans paquet, le module.
     */
    private fun occurrencesDansValeur(
        texte: String,
        debut: Int,
        fin: Int,
        type: String,
        nom: String,
    ): List<Plage> = referencesDansValeur(texte, debut, fin)
        .filter { it.type == type && it.nom == nom }
        .map { it.plage }

    /**
     * TOUTES les références de la valeur BRUTE [debut, fin) — le scan
     * GÉNÉRAL (le caret XML s'y localise, le renommage y filtre) :
     * références `@+?(pkg:)?type/nom`, `?type/nom` (thème) et expressions
     * `R.type.nom` (data binding).
     */
    private fun referencesDansValeur(texte: String, debut: Int, fin: Int): List<ReferenceValeur> {
        val references = ArrayList<ReferenceValeur>()
        var i = debut
        while (i < fin) {
            val c = texte[i]
            if (c == '@' || c == '?') {
                var j = i + 1
                if (c == '@' && texte.getOrNull(j) == '+') j++
                // Le PAQUET explicite se termine par ':' AVANT le '/' du
                // type (`@ns:string/foo`) ; un '/' d'abord (`@string/foo`)
                // signifie SANS paquet — le scan qui précède a déjà traversé
                // le type.
                var debutType = j
                var paquet = ""
                var k = j
                while (k < fin && texte[k] != ':' && texte[k] != '/') k++
                if (k < fin && texte[k] == ':') {
                    paquet = texte.substring(j, k)
                    debutType = k + 1
                }
                // Le TYPE se termine au '/', le NOM suit.
                var m = debutType
                while (m < fin && texte[m] != '/') m++
                if (m < fin && texte[m] == '/') {
                    val typeLu = texte.substring(debutType, m)
                    val debutNom = m + 1
                    var finNom = debutNom
                    while (finNom < fin && texte[finNom].isJavaIdentifierPart()) finNom++
                    val nomLu = texte.substring(debutNom, finNom)
                    if (nomLu.isNotEmpty() && (paquet.isEmpty() || paquet == namespace)) {
                        references.add(ReferenceValeur(Plage(debutNom, debutNom + nomLu.length), typeLu, nomLu))
                    }
                }
                // AVANCE D'UN SEUL caractère : un '@' de data binding
                // (`@{…R.id.x…}`) n'est PAS une référence — le scan doit
                // continuer DEDANS (mesuré : le saut à la fin de la valeur
                // volait l'expression R.* qui suit).
                i++
                continue
            }
            // Les expressions R.<type>.<nom> (data binding `@{…}`) — le
            // préfixe EXACT `R.<ident>.<ident>` (les courses
            // d'identifiants sont MAXIMALES : les bornes sont celles de
            // l'ancien balayage V-D3, miroir du comportement mesuré).
            if (c == 'R' && estBorneIdentifier(texte, i - 1) && texte.getOrNull(i + 1) == '.') {
                var k = i + 2
                while (k < fin && texte[k].isJavaIdentifierPart()) k++
                val typeLu = texte.substring(i + 2, k)
                if (typeLu.isNotEmpty() && k < fin && texte[k] == '.') {
                    var m = k + 1
                    while (m < fin && texte[m].isJavaIdentifierPart()) m++
                    val nomLu = texte.substring(k + 1, m)
                    if (nomLu.isNotEmpty()) {
                        references.add(ReferenceValeur(Plage(k + 1, k + 1 + nomLu.length), typeLu, nomLu))
                        i = m
                        continue
                    }
                }
            }
            i++
        }
        return references
    }

    /** Vrai ssi l'offset est une BORNE d'identifiant (début/fin de fichier ou non-ident). */
    private fun estBorneIdentifier(texte: String, offset: Int): Boolean {
        if (offset < 0 || offset >= texte.length) return true
        val c = texte[offset]
        return !c.isJavaIdentifierPart()
    }

    // ------------------------------------------------------------------ //
    // Collecteurs Java
    // ------------------------------------------------------------------ //

    /** Le type R d'un FQN déclarant (`ns.R$string` binaire, `ns.R.string` source). */
    private fun typeRDe(fqcn: String): String? {
        val prefixe = "$namespace.R"
        return when {
            fqcn.startsWith("$prefixe$") && fqcn.length > prefixe.length + 1 -> fqcn.substringAfterLast('$')
            fqcn.startsWith("$prefixe.") && fqcn.length > prefixe.length + 1 -> fqcn.removePrefix("$prefixe.")
            else -> null
        }
    }

    /** Le FQN (joint) du type déclarant d'un binding de champ — forme BINAIRE d'abord (constantPoolName — `ns.R$string`), forme source en repli. */
    private fun fqnDe(b: FieldBinding): String? = runCatching {
        b.declaringClass?.constantPoolName()?.let { nom -> String(nom).replace('/', '.') }
            ?: b.declaringClass?.compoundName?.joinToString(".") { String(it) }
    }.getOrNull()

    /**
     * Le collecteur de la cible : le segment de référence qualifiée qui
     * porte le caret ET un binding de champ d'une classe `R$<type>`.
     */
    private inner class CollecteurCibleRessource(
        private val source: String,
        private val offset: Int,
        private val namespace: String,
    ) : LectureAst.VisiteurResilient() {
        var cible: Triple<String, String, Plage>? = null

        override fun visiteReferenceQualifiee(node: QualifiedNameReference, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            if (cible != null) return true
            val positions = runCatching { node.sourcePositions }.getOrNull() ?: return true
            val autres = runCatching { node.otherBindings }.getOrNull()
            for (indice in positions.indices) {
                // Liaison unique : `binding` porte le DERNIER segment — le
                // segment 0 (`R`) n'est PAS le champ.
                if (indice == 0 && autres == null && positions.size > 1) continue
                val b = if (indice == 0) {
                    runCatching { node.binding }.getOrNull()
                } else {
                    autres?.getOrNull(indice - 1)
                }
                if (b !is FieldBinding) continue
                val type = typeRDe(fqnDe(b) ?: continue) ?: continue
                val p = positions[indice]
                val debut = (p ushr 32).toInt()
                val fin = p.toInt()
                if (offset < debut || offset > fin) continue
                cible = Triple(type, String(b.name), Plage(debut, fin + 1))
                return true
            }
            // Forme « liaison unique » (mesuré V-B2) : pour
            // `R.<type>.<nom>`, ecj lie le DERNIER segment dans `binding`
            // et laisse `otherBindings` NULL — le caret sur le dernier
            // jeton désigne quand même le champ.
            if (autres == null && positions.size > 1) {
                val b = runCatching { node.binding }.getOrNull()
                if (b is FieldBinding) {
                    val type = typeRDe(fqnDe(b) ?: return true)
                    if (type != null) {
                        val p = positions.last()
                        val debut = (p ushr 32).toInt()
                        val fin = p.toInt()
                        if (offset in debut..fin) {
                            cible = Triple(type, String(b.name), Plage(debut, fin + 1))
                        }
                    }
                }
            }
            return true
        }
    }

    /**
     * Le collecteur des occurrences Java : chaque segment de référence
     * qualifiée dont le binding est le champ `R$<type>.<nom>` (le type R
     * départage les homonymes — jamais `R.string.x` confondu avec
     * `R.layout.x`).
     */
    private inner class CollecteurOccurrencesR(
        private val source: String,
        private val namespace: String,
        private val type: String,
        private val nom: String,
    ) : LectureAst.VisiteurResilient() {
        val plages = ArrayList<Plage>()

        override fun visiteReferenceQualifiee(node: QualifiedNameReference, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
            val positions = runCatching { node.sourcePositions }.getOrNull() ?: return true
            val autres = runCatching { node.otherBindings }.getOrNull()
            for (indice in positions.indices) {
                // Liaison unique : `binding` porte le DERNIER segment — le
                // segment 0 (`R`) n'est PAS le champ.
                if (indice == 0 && autres == null && positions.size > 1) continue
                val b = if (indice == 0) {
                    runCatching { node.binding }.getOrNull()
                } else {
                    autres?.getOrNull(indice - 1)
                }
                if (b !is FieldBinding) continue
                val fqn = fqnDe(b) ?: continue
                if (typeRDe(fqn) != type || String(b.name) != nom) continue
                val p = positions[indice]
                val debut = (p ushr 32).toInt()
                val fin = p.toInt()
                if (debut in 0..fin && fin < source.length) {
                    plages.add(Plage(debut, fin + 1))
                }
            }
            // Forme « liaison unique » (mesuré V-B2) : le DERNIER segment
            // porte le champ dans `binding`, `otherBindings` est NULL.
            if (autres == null && positions.size > 1) {
                val b = runCatching { node.binding }.getOrNull()
                if (b is FieldBinding) {
                    val fqn = fqnDe(b)
                    if (fqn != null && typeRDe(fqn) == type && String(b.name) == nom) {
                        val p = positions.last()
                        val debut = (p ushr 32).toInt()
                        val fin = p.toInt()
                        if (debut in 0..fin && fin < source.length) {
                            plages.add(Plage(debut, fin + 1))
                        }
                    }
                }
            }
            return true
        }
    }
}
