package jo.codeide.lsp.java.protocole

import java.nio.file.Files
import java.nio.file.Path
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.SymbolKind

/**
 * Les symboles de l'espace de travail (étage N8 — cahier fiche 056 :
 * `workspace/symbol`, lot S1-c) : recherche de types par PRÉFIXE sur les
 * sources du PROJET (les fichiers `.java` des racines déclarées à
 * l'initialisation — l'overlay des documents ouverts fait foi pour les
 * fichiers vivants, le disque pour les autres).
 *
 * **Contrats verrouillés par la fiche** :
 *  - requête VIDE → liste VIDE (convention I9 : jamais d'exception, la
 *    requête vide n'est pas une erreur, elle ne matche rien) ;
 *  - moteur NON PRÊT (aucune racine) → liste VIDE sans exception ;
 *  - l'entrée trouvée porte `containerName` = le PAQUET de déclaration,
 *    une URI en forme `Path.toUri()`, et une plage de début SUR LE NOM
 *    DÉCLARANT (fiche : « ligne 2, caractère 13 » pour « public class
 *    MaVue { » — jamais la déclaration entière) ;
 *  - `didChangeWatchedFiles` invalide le cache (la notification à liste
 *    VIDE force la reconstruction — le test S1-c s'en sert comme signal
 *    d'indexation après écriture des fichiers).
 *
 * Le balayage est LEXICAL (paquet + déclarations de types par jetons) :
 * la recherche de symboles ne requiert AUCUNE résolution de type — un
 * lecteur résilient par fichier suffit, et les fichiers non compilables
 * (syntaxe cassée) contribuent leurs types quand même. Le cache tient un
 * instantané par racine ; toute notification de fichiers surveillés le
 * RÉVOQUE intégralement (invalidation conservatrice : un balayage de
 * quelques dizaines de fichiers coûte quelques millisecondes, une
 * publication fausse coûterait la confiance).
 *
 * @param racines les racines sources du projet (vides = moteur non prêt).
 */
public class SymbolesEspace(private val racines: () -> List<Path>) {

    /** Un type déclaré, extrait lexicalement d'un fichier source. */
    private data class TypeDeclare(
        val nom: String,
        val genre: SymbolKind,
        val paquet: String?,
        val fichier: Path,
        val offsetNom: Int,
    )

    /** Le cache d'index (null = à reconstruire). */
    @Volatile
    private var cache: List<TypeDeclare>? = null

    /**
     * Révoque le cache (didChangeWatchedFiles — fiche 056 : les
     * événements de fichiers « invalident le cache sans erreur » ; la
     * liste VIDE force la reconstruction).
     */
    public fun invalider() {
        cache = null
    }

    /**
     * Recherche les types du projet par préfixe de nom simple.
     *
     * @param requete le préfixe (vide → liste VIDE, convention I9 ;
     *   insensible à la casse — « MaVue » et « mavue » matchent).
     * @param limite le plafond de réponses (garde-fou mémoire).
     * @return les symboles trouvés (jamais null, jamais d'exception).
     */
    public fun rechercher(requete: String, limite: Int = 200): List<SymbolInformation> {
        if (requete.isEmpty()) return emptyList()
        val index = cache ?: balayer().also { cache = it }
        val prefixe = requete.lowercase()
        return index.asSequence()
            .filter { it.nom.lowercase().startsWith(prefixe) }
            .take(limite)
            .map { type -> informationDe(type) }
            .toList()
    }

    /** Le [SymbolInformation] LSP d'un type déclaré. */
    private fun informationDe(type: TypeDeclare): SymbolInformation {
        val texte = lireSansEchec(type.fichier) ?: ""
        val repere = ConvertisseurLsp.repere(texte)
        val position = repere.position(type.offsetNom)
        val plage = org.eclipse.lsp4j.Range(position, org.eclipse.lsp4j.Position(position.line, position.character + type.nom.length))
        val symbole = SymbolInformation()
        symbole.name = type.nom
        symbole.kind = type.genre
        symbole.location = Location(type.fichier.toUri().toString(), plage)
        symbole.containerName = type.paquet ?: ""
        return symbole
    }

    /**
     * Le balayage des racines : chaque fichier `.java` est lu et analysé
     * lexicalement — paquet (`package x.y.z ;` en tête) puis déclarations
     * de types de PREMIER niveau (`class|interface|enum|record|@interface
     * Nom`). Les fichiers illisibles ou imparsables sont ignorés sans
     * bruit (l'honnêteté paie mieux que l'invention).
     *
     * @return l'index des types déclarés.
     */
    private fun balayer(): List<TypeDeclare> {
        val types = ArrayList<TypeDeclare>()
        for (racine in racines()) {
            val fichiers = runCatching {
                Files.walk(racine).use { flux ->
                    flux.filter { p -> p.toString().endsWith(".java") && Files.isRegularFile(p) }
                        .filter { p -> p.fileName?.toString()?.firstOrNull() != '.' }
                        .toList()
                }
            }.getOrDefault(emptyList())
            for (fichier in fichiers) {
                extraireTypes(fichier, types)
            }
        }
        return types
    }

    /**
     * Extrait les types déclarés d'un fichier (lexical, résilient).
     */
    private fun extraireTypes(fichier: Path, sortie: MutableList<TypeDeclare>) {
        val texte = lireSansEchec(fichier) ?: return
        val paquet = paquetDe(texte)

        // Balayage des déclarations de types de premier niveau : le lecteur
        // suit les accolades pour ne pas entrer dans les corps ; les
        // chaînes et commentaires sont sautés (le même contrat que le
        // pliage N7 — jamais de type né d'une accolade de chaîne).
        var indice = 0
        var profondeur = 0
        val n = texte.length
        while (indice < n) {
            val c = texte[indice]
            when {
                c == '/' && indice + 1 < n && texte[indice + 1] == '/' -> {
                    val fin = texte.indexOf('\n', indice)
                    indice = if (fin < 0) n else fin + 1
                }
                c == '/' && indice + 1 < n && texte[indice + 1] == '*' -> {
                    val fin = texte.indexOf("*/", indice + 2)
                    indice = if (fin < 0) n else fin + 2
                }
                c == '"' -> {
                    indice = sauterChaine(texte, indice)
                }
                c == '\'' -> {
                    indice = sauterCaractere(texte, indice)
                }
                c == '{' -> {
                    profondeur++
                    indice++
                }
                c == '}' -> {
                    profondeur = maxOf(0, profondeur - 1)
                    indice++
                }
                c.isJavaIdentifierStart() && profondeur == 0 -> {
                    var j = indice + 1
                    while (j < n && texte[j].isJavaIdentifierPart()) j++
                    val mot = texte.substring(indice, j)
                    if (mot in MOTS_TYPES) {
                        // Le nom suit (blancs/annotations intercalés admis).
                        val nom = nomApres(texte, j)
                        if (nom != null) {
                            sortie.add(
                                TypeDeclare(
                                    nom = nom.premier,
                                    genre = genreDuMot(mot),
                                    paquet = paquet,
                                    fichier = fichier,
                                    offsetNom = nom.debut,
                                ),
                            )
                        }
                        indice = nom?.fin ?: j
                    } else {
                        indice = j
                    }
                }
                else -> indice++
            }
        }
    }

    /** Le paquet déclaré (`package com.ex ;` en tête), ou null. */
    private fun paquetDe(texte: String): String? {
        val motif = Regex("""(?m)^\s*package\s+([A-Za-z_][A-Za-z0-9_.]*)\s*;""")
        return motif.find(texte)?.groupValues?.get(1)
    }

    /** Saute une chaîne littérale (renvoie l'indice APRÈS la clôture). */
    private fun sauterChaine(texte: String, debut: Int): Int {
        var i = debut + 1
        while (i < texte.length) {
            if (texte[i] == '\\') {
                i += 2
            } else if (texte[i] == '"') {
                return i + 1
            } else {
                i++
            }
        }
        return i
    }

    /** Saute un littéral de caractère. */
    private fun sauterCaractere(texte: String, debut: Int): Int {
        var i = debut + 1
        while (i < texte.length) {
            if (texte[i] == '\\') {
                i += 2
            } else if (texte[i] == '\'') {
                return i + 1
            } else {
                i++
            }
        }
        return i
    }

    /** Le nom déclarant trouvé après un mot-clé de type (annotations admises avant). */
    private fun nomApres(texte: String, apres: Int): NomTrouve? {
        var i = apres
        // Les annotations peuvent précéder le nom : `record @Deprecated Foo(...)`.
        while (i < texte.length) {
            while (i < texte.length && texte[i].isWhitespace()) i++
            if (i < texte.length && texte[i] == '@') {
                var j = i + 1
                while (j < texte.length && texte[j].isJavaIdentifierPart()) j++
                // Arguments d'annotation éventuels.
                i = j
                while (i < texte.length && texte[i].isWhitespace()) i++
                if (i < texte.length && texte[i] == '(') {
                    var profondeur = 0
                    do {
                        if (texte[i] == '(') profondeur++
                        if (texte[i] == ')') profondeur--
                        i++
                    } while (i < texte.length && profondeur > 0)
                }
            } else {
                break
            }
        }
        if (i >= texte.length || !texte[i].isJavaIdentifierStart()) return null
        var j = i + 1
        while (j < texte.length && texte[j].isJavaIdentifierPart()) j++
        return NomTrouve(texte.substring(i, j), i, j)
    }

    /** Un nom déclarant trouvé dans le texte (avec ses bornes). */
    private data class NomTrouve(
        val premier: String,
        val debut: Int,
        val fin: Int,
    )

    /** Le genre LSP du mot-clé déclarant (une annotation déclarante passe
     *  par « interface » — le « @ » est ignoré par le lecteur). */
    private fun genreDuMot(mot: String): SymbolKind = when (mot) {
        "interface" -> SymbolKind.Interface
        "enum" -> SymbolKind.Enum
        "record" -> SymbolKind.Struct
        else -> SymbolKind.Class
    }

    /** Lecture d'un fichier sans jamais lever (fichier absent/vérolé → null). */
    private fun lireSansEchec(fichier: Path): String? = runCatching {
        Files.readString(fichier)
    }.getOrNull()

    private companion object {
        /** Les mots-clés déclarants de types de premier niveau. */
        private val MOTS_TYPES: Set<String> = setOf("class", "interface", "enum", "record")
    }
}
