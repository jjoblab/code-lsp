package jo.codeide.lsp.java

import jo.codeide.lsp.java.classpath.EnvironnementNoms
import jo.codeide.lsp.java.interne.PontEcj
import jo.codeide.lsp.java.interne.UnitesSource
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import kotlin.io.path.writeText
import java.util.zip.ZipOutputStream

/**
 * Outils de fixture des tests N1 : compilation de classes par ecj EN
 * MÉMOIRE (remplace le javac système du cahier — aucune dépendance à un
 * JDK outillé, la suite tourne partout) et fabrication de jars sélectifs.
 */
object OutilsFixture {

    /**
     * Compile des sources Java vers des octets de classe, en mémoire.
     *
     * Le nom d'entrée est le nom INTERNE de la classe (lu dans le fichier
     * produit : `this_class` du format de classe), pas le nom de fichier
     * source — une source peut produire plusieurs classes (imbriquées).
     *
     * @param racine racine source (les sources s'y résolvent entre elles).
     * @param sources sources à compiler (chemin logique → texte).
     * @return les octets par entrée de classe (nom interne → octets).
     */
    fun compilerEnMemoire(racine: Path, sources: Map<String, String>): Map<String, ByteArray> {
        // Les sources sont D'ABORD posées sur disque sous la racine : la
        // résolution inter-sources de ecj passe par l'environnement de noms,
        // qui sonde le DISQUE (sans ça, `B extends A` se compilerait avec
        // java.lang.Object en superclasse — hiérarchie faussée).
        for ((chemin, texte) in sources) {
            val fichier = racine.resolve(chemin.removePrefix("/"))
            Files.createDirectories(fichier.parent)
            fichier.writeText(texte)
        }
        val environnement = EnvironnementNoms(listOf(racine), emptyList(), null)
        val pont = PontEcj(environnement, "17")
        val octets = LinkedHashMap<String, ByteArray>()
        for ((chemin, texte) in sources) {
            val reponse = pont.resoudre(UnitesSource.EnMemoire(chemin, texte), genererCode = true)
            for (resultat in reponse.resultats) {
                for (fichierClasse in resultat.classFiles ?: emptyArray()) {
                    val contenu = fichierClasse.getBytes()
                    val nomInterne = try {
                        String(
                            org.eclipse.jdt.internal.compiler.classfmt.ClassFileReader
                                .read(contenu, "fixture.class").name,
                        )
                    } catch (_: Exception) {
                        // Lecture défensive : entrée écartée.
                        continue
                    }
                    octets["$nomInterne.class"] = contenu
                }
            }
        }
        environnement.dispose()
        return octets
    }

    /** Écrit un jar des entrées données (nom d'entrée → octets). */
    fun ecrireJar(jar: Path, entrees: Map<String, ByteArray>) {
        Files.createDirectories(jar.parent)
        ZipOutputStream(Files.newOutputStream(jar)).use { sortie ->
            for ((nom, octets) in entrees) {
                sortie.putNextEntry(ZipEntry(nom))
                sortie.write(octets)
                sortie.closeEntry()
            }
        }
    }
}
