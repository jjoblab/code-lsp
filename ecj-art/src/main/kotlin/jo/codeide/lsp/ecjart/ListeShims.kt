package jo.codeide.lsp.ecjart

/**
 * Modèle de la liste de shims versionnée (ADR 0018) — le point de vérité
 * unique des transformations appliquées par [PatcheurJar] au jar ecj.
 *
 * La liste est un fichier texte versionné dans les ressources du module
 * (`liste-shims-<version-ecj>.txt`) : chaque directive y est lisible,
 * diffable en revue, et la CI confronte le scan du jar entrant à son contenu
 * (tout motif sensible non couvert par une directive = échec de build).
 *
 * Deux formes de directives existent :
 *  - [DirectiveRemap] : remappe toutes les références d'un type amont vers un
 *    type shim (entrées Class, descripteurs, signatures, frames) ;
 *  - [DirectiveRedirection] : remplace une invocation de méthode virtuelle par
 *    un appel statique à un shim (le récepteur devient premier argument —
 *    forme de pile identique, frames inchangées).
 */
public sealed interface DirectiveShim {

    /** Représentation canonique (une ligne de la liste versionnée). */
    public fun enLigne(): String
}

/**
 * Remappe toutes les références du type [classeAmont] vers [classeShim].
 *
 * @param classeAmont nom interne JVM (slashs) du type absent d'ART,
 *   p. ex. `java/lang/Runtime$Version`.
 * @param classeShim nom interne JVM du type de remplacement embarqué dans le
 *   jar patché, p. ex. `jo/codeide/lsp/ecjart/compat/VersionJava`.
 */
public data class DirectiveRemap(
    public val classeAmont: String,
    public val classeShim: String,
) : DirectiveShim {
    init {
        require(classeAmont.isNotEmpty() && !classeAmont.endsWith("/") && !classeAmont.startsWith("[")) {
            "classe amont mal formée : « $classeAmont » (nom interne JVM attendu, p. ex. java/lang/Runtime\$Version)"
        }
        require(classeShim.isNotEmpty() && !classeShim.endsWith("/")) { "classe shim mal formée : « $classeShim »" }
    }

    override public fun enLigne(): String = "remap-classe $classeAmont $classeShim"
}

/**
 * Redirige une invocation virtuelle vers un appel statique de shim.
 *
 * @param proprietaire nom interne JVM de la classe déclarant la méthode amont
 *   (p. ex. `java/io/InputStream`).
 * @param methode nom simple de la méthode amont (p. ex. `readAllBytes`).
 * @param descripteur descripteur JVM exact de la méthode amont
 *   (p. ex. `()[B`) — seules les invocations au descripteur EXACT sont
 *   redirigées.
 * @param shimProprietaire nom interne JVM de la classe shim cible.
 * @param shimMethode nom de la méthode statique shim.
 * @param shimDescripteur descripteur JVM de la méthode shim — il doit être
 *   la forme « récepteur en premier argument » du descripteur amont
 *   (vérifié par [PatcheurJar] avant transformation).
 */
public data class DirectiveRedirection(
    public val proprietaire: String,
    public val methode: String,
    public val descripteur: String,
    public val shimProprietaire: String,
    public val shimMethode: String,
    public val shimDescripteur: String,
) : DirectiveShim {
    override public fun enLigne(): String =
        "rediriger-methode $proprietaire $methode $descripteur " +
            "$shimProprietaire $shimMethode $shimDescripteur"
}

/**
 * Liste de shims chargée depuis le fichier versionné.
 *
 * @param versionEcj version d'ecj ciblée (informationnelle — p. ex. « 3.40.0 »).
 * @param directives directives dans l'ordre du fichier.
 */
public class ListeShims(
    public val versionEcj: String,
    public val directives: List<DirectiveShim>,
) {
    /** Directives de remap de type uniquement. */
    public fun remaps(): List<DirectiveRemap> = directives.filterIsInstance<DirectiveRemap>()

    /** Directives de redirection d'invocation uniquement. */
    public fun redirections(): List<DirectiveRedirection> =
        directives.filterIsInstance<DirectiveRedirection>()

    public companion object {

        /**
         * Charge une liste depuis un fichier versionné.
         *
         * @param fichier chemin du fichier `liste-shims-*.txt`.
         * @param versionEcj version d'ecj attendue (reprise du nom de fichier
         *   par l'appelant ; conservée comme métadonnée du rapport).
         * @return la liste compilée, directives dans l'ordre du fichier.
         * @throws IllegalArgumentException si une directive est mal formée,
         *   inconnue, dupliquée, ou si la liste est vide.
         */
        @JvmStatic
        public fun charger(fichier: java.nio.file.Path, versionEcj: String): ListeShims {
            val lignes = java.nio.file.Files.readAllLines(fichier, Charsets.UTF_8)
            return compiler(lignes, versionEcj)
        }

        /**
         * Compile des lignes texte en liste (séparée de [charger] pour les tests).
         *
         * @throws IllegalArgumentException ligne inconnue, arité incorrecte ou
         *   directive dupliquée.
         */
        @JvmStatic
        public fun compiler(lignes: List<String>, versionEcj: String): ListeShims {
            val directives = mutableListOf<DirectiveShim>()
            val vues = mutableSetOf<String>()
            for ((numero, brute) in lignes.withIndex()) {
                val ligne = brute.trim()
                if (ligne.isEmpty() || ligne.startsWith("#")) continue
                val jetons = ligne.split(' ', '\t').filter { it.isNotEmpty() }
                val directive = when (jetons.first()) {
                    "remap-classe" -> {
                        require(jetons.size == 3) {
                            "ligne ${numero + 1} : remap-classe exige 2 arguments (classe amont, classe shim)"
                        }
                        DirectiveRemap(jetons[1], jetons[2])
                    }
                    "rediriger-methode" -> {
                        require(jetons.size == 7) {
                            "ligne ${numero + 1} : rediriger-methode exige 6 arguments " +
                                "(propriétaire, méthode, descripteur, shim ×3)"
                        }
                        DirectiveRedirection(jetons[1], jetons[2], jetons[3], jetons[4], jetons[5], jetons[6])
                    }
                    else -> throw IllegalArgumentException(
                        "ligne ${numero + 1} : directive inconnue « ${jetons.first()} » " +
                            "(attendues : remap-classe, rediriger-methode)"
                    )
                }
                val vue = directive.enLigne()
                require(vue !in vues) { "ligne ${numero + 1} : directive dupliquée « $vue »" }
                vues.add(vue)
                directives.add(directive)
            }
            require(directives.isNotEmpty()) { "liste de shims vide — aucune directive active" }
            return ListeShims(versionEcj, directives)
        }
    }
}
