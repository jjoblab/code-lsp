package spikekotlin

import java.io.File

/**
 * Localisation des jars de mesure (transmise par la tâche `test` du module
 * via la propriété système `spike.jars` — résolue depuis la racine du
 * dépôt, surchargeable au lancement Gradle).
 */
object CheminJars {
    /** Le répertoire des jars, ou null s'il n'est pas configuré. */
    val dossier: File? =
        System.getProperty("spike.jars")?.let(::File)?.takeIf { it.isDirectory }

    /** Le jar [nom] s'il existe, sinon null (le banc l'assumera honnêtement). */
    fun jar(nom: String): File? = dossier?.resolve(nom)?.takeIf { it.isFile }
}
