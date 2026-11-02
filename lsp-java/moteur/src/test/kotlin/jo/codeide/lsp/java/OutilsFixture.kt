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

    /**
     * Localise le `android.jar` du SDK local (gates environnementales des
     * fiches 033/036/043/044/050/053 — hypothèses VISIBLES du cahier).
     *
     * Candidats, dans l'ordre :
     *  1. la variable d'environnement `ANDROID_JAR` (chemin direct —
     *     requalification CI : le jar y est téléchargé sans SDK complet) ;
     *  2. `ANDROID_HOME/platforms/android-{34,35}/android.jar` ;
     * 3. les chemins machine historiques du bac à sable.
     *
     * @return le chemin du jar, ou null (la classe appelante IGNORE ses
     *   tests par `assumeTrue` — la gate dort, message visible).
     */
    fun androidJar(): Path? {
        System.getenv("ANDROID_JAR")?.let { env ->
            val p = Path.of(env)
            if (Files.isRegularFile(p)) return p
        }
        val home = System.getenv("ANDROID_HOME") ?: "/home/z/android-sdk"
        val candidats = listOf(
            Path.of(home, "platforms/android-34/android.jar"),
            Path.of(home, "platforms/android-35/android.jar"),
            Path.of("/home/z/android-sdk/platforms/android-34/android.jar"),
            Path.of("/home/z/android-sdk/platforms/android-35/android.jar"),
            Path.of("/home/z/tools/android-sdk/platforms/android-34/android.jar"),
            Path.of("/home/z/tools/android-sdk/platforms/android-35/android.jar"),
        )
        return candidats.firstOrNull { Files.isRegularFile(it) }
    }

    /** La source R.java du cahier (fiche 037) : classe finale, classes internes statiques vides. */
    private val SOURCE_R = """
        package com.example.monip;

        public final class R {
            public static final class style {
            }

            public static final class id {
            }

            public static final class string {
            }
        }
    """.trimIndent()

    /**
     * Fabrique du R.jar « réaliste » (aapt2-like — fiche 037, partagée par
     * la fiche 036) : la source `R.java` embarquée est compilée par ecj EN
     * MÉMOIRE puis jarée — exactement 4 entrées (R, R$style, R$id,
     * R$string).
     *
     * @param dossier le dossier de travail (le jar y est écrit).
     * @return le chemin du jar `r-bench.jar`.
     */
    fun fabriquerRJar(dossier: Path): Path {
        val classes = compilerEnMemoire(
            dossier,
            mapOf("/com/example/monip/R.java" to SOURCE_R),
        )
        check(classes.size == 4) { "4 classes compilées attendues : ${classes.keys}" }
        val jar = dossier.resolve("r-bench.jar")
        ecrireJar(
            jar,
            linkedMapOf(
                "com/example/monip/R.class" to classes.getValue("com/example/monip/R.class"),
                "com/example/monip/R\$style.class" to classes.getValue("com/example/monip/R\$style.class"),
                "com/example/monip/R\$id.class" to classes.getValue("com/example/monip/R\$id.class"),
                "com/example/monip/R\$string.class" to classes.getValue("com/example/monip/R\$string.class"),
            ),
        )
        return jar
    }
}
