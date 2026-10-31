package jo.codeide.lsp.java.projetandroid

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import jo.codeide.lsp.java.classpath.TypesSynthetiques
import kotlin.concurrent.withLock

/**
 * Déclarations Kotlin publiques exposées au Java (vague V-B3 — ADR 0022,
 * décision D3) : un scanner LEXICAL TOLÉRANT des `.kt` des racines du
 * module — PAS un front-end Kotlin (instruction du 30/10/2026 : le moteur
 * Kotlin ne migre pas ; seul l'index partagé est dans le champ).
 *
 * **Règle conservatrice** (R1 de l'ADR — « ne déclarer que ce qui est
 * certain ») : un scanner trompé serait un FAUX POSITIF (pire que
 * l'absence de déclaration). Ne sont donc exposés que :
 *  - les types de PREMIER NIVEAU (`class`/`interface`/`object`/`enum
 *    class`/`data class`, colonne 0) non `private`/`internal` — coquilles
 *    vides publiques (l'API suffit : hover/complétion/navigation) ;
 *  - les fonctions top-level dont TOUS les types (paramètres + retour)
 *    sont CERTAINS : primitifs Kotlin, `String`/`Any`/collections
 *    standards, ou FQCN CONTENANT UN POINT — sinon la fonction est
 *    ÉCARTÉE (sous-estimation honnête, jamais un type inventé) ;
 *  - les propriétés top-level (`val`/`var`) au même tarif — accesseurs
 *    statiques sur la facade.
 *
 * Les fonctions/propriétés vivent sur la FACADE du fichier
 * (`Utils.kt` → `paquet.UtilsKt` ; `@file:JvmName("Nom")` prime).
 *
 * **Honnêteté** : la décompilation d'une classe/facade Kotlin rend l'API
 * seule (aperçu), jamais de corps ; la NAVIGATION mène au `.kt` (texte,
 * offset exact de la déclaration).
 *
 * @param racines racines sources du module (les `.kt` y sont scannés).
 */
public class DeclarationsKotlin(
    private val racines: List<Path>,
) : TypesSynthetiques {

    /** Une déclaration exposée (type, fonction ou propriété). */
    private class Declaration(
        val paquet: String,
        val nomSimple: String,
        val genre: Genre,
        val fichier: Path,
        val offsetNom: Int,
        /** Pour les fonctions : (nom, descripteur). */
        val methodes: MutableList<Pair<String, String>> = mutableListOf(),
    )

    private enum class Genre { TYPE, FACADE }

    /** Les déclarations par FQCN (scan paresseux, une fois). */
    @Volatile
    private var scanne: Map<String, Declaration>? = null

    /** Les octets fabriqués par FQCN. */
    private val octetsParFqcn = ConcurrentHashMap<String, ByteArray>()

    /**
     * Verrou du scan lexical (V-F2 — périmètre FEUILLE : construction
     * paresseuse une fois, jamais imbriquée vers un verrou de module).
     */
    private val verrouScan = ReentrantLock()

    // ------------------------------------------------------------------ //
    // Contract TypesSynthetiques
    // ------------------------------------------------------------------ //

    override fun octetsPour(fqcn: String): ByteArray? {
        octetsParFqcn[fqcn]?.let { return it }
        val declaration = scanner()[fqcn] ?: return null
        val ecrivain = EcrivainClasseLegere.pour(fqcn)
        ecrivain.finale()
        if (declaration.genre == Genre.FACADE) {
            // Facade : jamais instanciable (constructeur privé).
            ecrivain.constructeurPrive()
            for ((nom, descripteur) in declaration.methodes) {
                ecrivain.methode(nom, descripteur, statique = true)
            }
        } else {
            // TYPE : constructeur public SANS ARGUMENT — un binaire sans
            // constructeur déclaré n'en expose AUCUN pour ecj (les vraies
            // signatures Kotlin ne sont pas extraites, ADR 0022).
            ecrivain.methode("<init>", "()V", statique = false, constructeur = true)
        }
        // TYPE : constructeur public SANS ARGUMENT explicite — un type
        // binaire SANS constructeur déclaré n'en expose AUCUN pour ecj
        // (les signatures réelles des constructeurs Kotlin ne sont pas
        // extraites par le scanner lexical — documenté ADR 0022 : repli
        // honnête, jamais un ctor inventé avec de faux paramètres).
        val octets = ecrivain.octets()
        octetsParFqcn[fqcn] = octets
        return octets
    }

    override fun typesParPaquet(): Map<String, List<String>> {
        val resultat = HashMap<String, MutableList<String>>()
        for (declaration in scanner().values) {
            resultat.getOrPut(declaration.paquet) { mutableListOf() }.add(declaration.nomSimple)
        }
        return resultat
    }

    override fun estPaquet(nomPaquet: String): Boolean =
        scanner().keys.any { fqcn -> fqcn == nomPaquet || fqcn.startsWith("$nomPaquet.") }

    override fun cibleChamp(
        fqcnDeclarant: String,
        champ: String,
    ): TypesSynthetiques.CibleChampSynthetique? {
        // Une fonction top-level n'est pas un champ — la navigation des
        // méthodes passe par la facade (binaire) ; les PROPRIÉTÉS top-level
        // exposent un accesseur get*() : le GO TO vise le `.kt`.
        val declaration = scanner()[fqcnDeclarant] ?: return null
        val methode = declaration.methodes.firstOrNull { it.first == champ || it.first == "get${champ.replaceFirstChar { c -> c.uppercase() }}" }
            ?: return null
        return TypesSynthetiques.CibleChampSynthetique(declaration.fichier, declaration.offsetNom, champ.length)
    }

    // ------------------------------------------------------------------ //
    // Scan lexical
    // ------------------------------------------------------------------ //

    /** Le scan complet (une fois, sous verrou de scan). */
    private fun scanner(): Map<String, Declaration> {
        scanne?.let { return it }
        verrouScan.withLock {
            scanne?.let { return it }
            val resultat = HashMap<String, Declaration>()
            for (racine in racines) {
                if (!Files.isDirectory(racine)) continue
                try {
                    Files.walk(racine).use { flux ->
                        flux.filter { fichier -> fichier.fileName.toString().endsWith(".kt") }
                            .sorted()
                            .forEach { fichier -> scannerFichier(fichier, resultat) }
                    }
                } catch (_: Exception) {
                    // racine illisible : écartée (tolérance)
                }
            }
            scanne = resultat
            return resultat
        }
    }

    /** Le scan d'UN fichier .kt. */
    private fun scannerFichier(
        fichier: Path,
        resultat: MutableMap<String, Declaration>,
    ) {
        val texte =
            try {
                Files.readString(fichier, Charsets.UTF_8)
            } catch (_: Exception) {
                return
            }
        val lignes = texte.split('\n')
        val paquet = lignes.firstNotNullOfOrNull { ligne ->
            MOTIF_PACKAGE.find(ligne)?.groupValues?.get(1)
        } ?: ""

        // @file:JvmName("Nom") — la facade porte CE nom.
        val nomFacade = lignes.firstNotNullOfOrNull { ligne ->
            MOTIF_JVM_NAME.find(ligne)?.groupValues?.get(1)
        } ?: (fichier.fileName.toString().removeSuffix(".kt") + "Kt")

        var declarationFacade: Declaration? = null
        for (ligne in lignes) {
            // Seules les déclarations de PREMIER NIVEAU (colonne 0)
            // intéressent le Java — le contenu des classes est vu par le
            // bytecode (post-build) ou rien (règle conservatrice).
            if (ligne.isEmpty() || ligne[0] != 'f' && ligne[0] != 'c' && ligne[0] != 'i' &&
                ligne[0] != 'o' && ligne[0] != 'e' && ligne[0] != 'd' && ligne[0] != 'a' &&
                ligne[0] != 's' && ligne[0] != 'v' && ligne[0] != '@' && ligne[0] != 'p'
            ) {
                continue
            }
            if (ligne.startsWith("package") || ligne.startsWith("import") || ligne.startsWith("@file:")) continue
            if (ligne.startsWith("private ") || ligne.startsWith("internal ")) continue

            // Types de premier niveau.
            MOTIF_TYPE.find(ligne)?.let { correspondance ->
                val nomType = correspondance.groupValues[2]
                if (nomType.isValidJavaIdentifier()) {
                    val offset = texte.indexOfSegment(lignes, ligne, nomType)
                    resultat["$paquet.$nomType"] = Declaration(paquet, nomType, Genre.TYPE, fichier, offset)
                }
                continue
            }

            // Fonction top-level : facade + méthode statique.
            MOTIF_FONCTION.find(ligne)?.let { correspondance ->
                val nomFonction = correspondance.groupValues[1]
                if (!nomFonction.isValidJavaIdentifier()) return@let
                val signature = signatureDe(ligne) ?: return@let
                if (declarationFacade == null) {
                    declarationFacade = Declaration(paquet, nomFacade, Genre.FACADE, fichier, texte.indexOfSegment(lignes, ligne, nomFonction))
                }
                declarationFacade!!.methodes.add(nomFonction to signature)
            }

            // Propriété top-level : accesseur statique (get, et set pour var).
            MOTIF_PROPRIETE.find(ligne)?.let { correspondance ->
                val modifiable = correspondance.groupValues[1] == "var"
                val nomPropriete = correspondance.groupValues[2]
                if (!nomPropriete.isValidJavaIdentifier()) return@let
                val type = typeCertainDe(correspondance.groupValues[3]) ?: return@let
                if (declarationFacade == null) {
                    declarationFacade = Declaration(paquet, nomFacade, Genre.FACADE, fichier, texte.indexOfSegment(lignes, ligne, nomPropriete))
                }
                val accesseur = nomPropriete.replaceFirstChar { it.uppercase() }
                declarationFacade!!.methodes.add("get$accesseur" to "()$type")
                if (modifiable) {
                    declarationFacade!!.methodes.add("set$accesseur" to "($type)V")
                }
            }
        }
        declarationFacade?.let { facade ->
            if (facade.methodes.isNotEmpty()) {
                resultat["${facade.paquet}.${facade.nomSimple}"] = facade
            }
        }
    }

    /**
     * Le descripteur JVM d'une déclaration `fun nom(params): Ret` — null si
     * un seul type est INCERTAIN (règle conservatrice : la fonction est
     * écartée, jamais un type inventé).
     */
    private fun signatureDe(ligne: String): String? {
        val ouverture = ligne.indexOf('(')
        if (ouverture < 0) return null
        val fermeture = ligne.indexOf(')', ouverture)
        if (fermeture < 0) return null
        val parametres = ligne.substring(ouverture + 1, fermeture).trim()
        val descripteurs = mutableListOf<String>()
        if (parametres.isNotEmpty()) {
            for (parametre in decouper(parametres)) {
                // `nom: Type = défaut` → garder `nom: Type`.
                val declaration = parametre.substringBefore('=').trim()
                val deuxPoints = declaration.lastIndexOf(':')
                if (deuxPoints < 0) return null
                descripteurs.add(typeCertainDe(declaration.substring(deuxPoints + 1).trim()) ?: return null)
            }
        }
        val apres = ligne.substring(fermeture + 1).trim()
        val retour =
            if (apres.startsWith(":")) {
                typeCertainDe(apres.removePrefix(":").substringBefore('{').trim()) ?: return null
            } else {
                "V" // Unit implicite
            }
        return "(" + descripteurs.joinToString("") + ")" + retour
    }

    /** Découpe les paramètres au NIVEAU SUPÉRIEUR (parenthèses/<> imbriquées). */
    private fun decouper(parametres: String): List<String> {
        val morceaux = mutableListOf<String>()
        val courant = StringBuilder()
        var profondeur = 0
        for (c in parametres) {
            when (c) {
                '(', '<', '[' -> {
                    profondeur++
                    courant.append(c)
                }
                ')', '>' -> {
                    profondeur--
                    courant.append(c)
                }
                ',' -> if (profondeur == 0) {
                    morceaux.add(courant.toString())
                    courant.setLength(0)
                } else {
                    courant.append(c)
                }
                else -> courant.append(c)
            }
        }
        if (courant.isNotEmpty()) morceaux.add(courant.toString())
        return morceaux
    }

    /**
     * Le descripteur d'un type CERTAIN — null si le type ne peut pas être
     * déduit sans contexte (identifiant nu sans point : import inconnu).
     */
    private fun typeCertainDe(type: String?): String? {
        if (type.isNullOrEmpty()) return null
        val nu = type.substringBefore('<').removeSuffix("?").trim()
        return when (nu) {
            "Int" -> "I"
            "Long" -> "J"
            "Short" -> "S"
            "Byte" -> "B"
            "Char" -> "C"
            "Boolean" -> "Z"
            "Float" -> "F"
            "Double" -> "D"
            "Unit" -> "V"
            "String", "CharSequence" -> "Ljava/lang/String;"
            "Any" -> "Ljava/lang/Object;"
            "List", "MutableList", "Collection", "Iterable" -> "Ljava/util/List;"
            "Set", "MutableSet" -> "Ljava/util/Set;"
            "Map", "MutableMap" -> "Ljava/util/Map;"
            "IntArray" -> "[I"
            "LongArray" -> "[J"
            "ByteArray" -> "[B"
            "BooleanArray" -> "[Z"
            else -> if (nu.contains('.') && nu.split('.').all { it.isValidJavaIdentifier() }) {
                "L" + nu + ";"
            } else {
                null // identifiant nu : import inconnu — ÉCARTÉ (conservateur)
            }
        }
    }

    private companion object {
        private val MOTIF_PACKAGE = Regex("""^\s*package\s+([\w.]+)""")
        private val MOTIF_JVM_NAME = Regex("""@file:JvmName\("([^"]+)"""")
        private val MOTIF_TYPE = Regex(
            """^(?:public\s+)?(?:abstract\s+|sealed\s+|open\s+|data\s+|value\s+)*(class|interface|object|enum class)\s+([A-Za-z_]\w*)""",
        )
        private val MOTIF_FONCTION = Regex("""^(?:@\w+(?:\([^)]*\))?\s*)*(?:public\s+|inline\s+|suspend\s+)*fun\s+(?:<[^>]+>\s*)?([A-Za-z_]\w*)\s*\(""")
        private val MOTIF_PROPRIETE = Regex("""^(?:@\w+\s*)*(?:public\s+)*(val|var)\s+([A-Za-z_]\w*)\s*(?::\s*([^.=\s][^={]*))?""")

        /** Un identifiant Java/Kotlin valide. */
        private fun String.isValidJavaIdentifier(): Boolean =
            isNotEmpty() && get(0).isJavaIdentifierStart() && all { it.isJavaIdentifierPart() }
    }
}

/** L'offset d'un segment de ligne dans le TEXTE complet (première occurrence). */
private fun String.indexOfSegment(
    lignes: List<String>,
    ligne: String,
    segment: String,
): Int {
    var cumul = 0
    for (l in lignes) {
        if (l === ligne || l == ligne) {
            val position = l.indexOf(segment)
            if (position >= 0) return cumul + position
        }
        cumul += l.length + 1
    }
    return 0
}
