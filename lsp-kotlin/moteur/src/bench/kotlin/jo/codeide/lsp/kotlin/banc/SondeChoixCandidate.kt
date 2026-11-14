// Sonde du choix de candidate (chantier B) : que contient l'index pour
// map (récepteurs), et que type le caret ?
package jo.codeide.lsp.kotlin.banc

import java.nio.file.Files
import java.nio.file.Path
import jo.codeide.lsp.kotlin.api.ClasspathKotlin
import jo.codeide.lsp.kotlin.api.EntreeClasspathKotlin
import jo.codeide.lsp.kotlin.api.KindEntreeKotlin
import jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin
import jo.codeide.lsp.kotlin.session.MoteurLangageKotlinNoyau
import jo.codeide.lsp.kotlin.session.SessionLangageKotlinDocuments

fun main() {
    val projet = Path.of("/home/z/my-project/projet-test-android")
    val jars = Files.list(projet.resolve("classpath-jars")).use { f ->
        f.filter { it.fileName.toString().endsWith(".jar") }.sorted().toList()
    }
    val classpath = ClasspathKotlin(
        entrees = jars.mapIndexed { i, j -> EntreeClasspathKotlin(KindEntreeKotlin.JAR, j.toString(), "sc:$i") },
        racinesSources = listOf(projet.resolve("app/src/main/java").toString()),
    )
    val moteur = ServiceMoteurLangageKotlin.unique as MoteurLangageKotlinNoyau
    moteur.definirClasspath(classpath)
    val session = moteur.creerSession() as SessionLangageKotlinDocuments
    session.definirClasspath(classpath)
    val limite = System.currentTimeMillis() + 300_000
    while (System.currentTimeMillis() < limite) {
        if (session.indexSiPret() != null) break
        Thread.sleep(50)
    }
    val vue = session.indexSiPret()!!.vue
    println("=== récepteurs de map :")
    for (a in vue.topParNom("map")) {
        println("  ${a.paquet} recepteur=${a.recepteur}")
    }
    println("=== récepteurs de padding (material3/ui) :")
    for (a in vue.topParNom("padding")) {
        println("  ${a.paquet} recepteur=${a.recepteur}")
    }
    session.close()
    println("FIN")
}
