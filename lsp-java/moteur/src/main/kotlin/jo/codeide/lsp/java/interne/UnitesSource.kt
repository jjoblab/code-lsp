package jo.codeide.lsp.java.interne

import org.eclipse.jdt.internal.compiler.env.ICompilationUnit
import java.nio.file.Files
import java.nio.file.Path

/**
 * Unités de compilation ecj (étage N1) : deux concrétions de
 * `ICompilationUnit`, la frontière d'entrée du pont ecj.
 */
public object UnitesSource {

    /**
     * Unité en mémoire : le contenu VIT dans l'objet (sources de l'éditeur,
     * sources masquées du diagnose rapide, sources réduites du focuser).
     *
     * @property chemin nom de fichier logique (p. ex. `/demo/Main.java`) —
     *   sert de clé aux caches et aux diagnostics.
     * @property source le texte Java complet.
     */
    public class EnMemoire(
        public val chemin: String,
        private val source: String,
    ) : ICompilationUnit {
        private val contenu: CharArray = source.toCharArray()

        override fun getContents(): CharArray = contenu
        override fun getFileName(): CharArray = chemin.toCharArray()
        override fun getMainTypeName(): CharArray {
            val base = chemin.substringAfterLast('/')
            return base.removeSuffix(".java").toCharArray()
        }

        override fun getPackageName(): Array<CharArray>? = null
        override fun ignoreOptionalProblems(): Boolean = false
    }

    /**
     * Unité sur disque : le contenu est lu paresseusement à la première
     * demande (le moteur répond `findType` pour des centaines de FQCN par
     * passe — l'ouverture ne doit lire que ce qu'ecj demande réellement).
     *
     * @property fichier chemin du `.java` sur disque (racine source + paquet).
     */
    public class SurDisque(
        public val fichier: Path,
    ) : ICompilationUnit {
        private var contenu: CharArray? = null

        override fun getContents(): CharArray =
            contenu ?: Files.readString(fichier, Charsets.UTF_8).toCharArray().also { contenu = it }

        override fun getFileName(): CharArray = fichier.toString().toCharArray()

        override fun getMainTypeName(): CharArray {
            val base = fichier.fileName.toString()
            return base.removeSuffix(".java").toCharArray()
        }

        override fun getPackageName(): Array<CharArray>? = null
        override fun ignoreOptionalProblems(): Boolean = false
    }
}
