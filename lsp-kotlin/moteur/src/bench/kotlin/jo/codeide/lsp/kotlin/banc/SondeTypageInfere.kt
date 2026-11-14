// Sonde : que contient l'index pour les fabriques stdlib (mutableMapOf,
// listOf…) — typeRetour et paramètres rendus par les métadonnées ?
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
        entrees = jars.mapIndexed { i, j -> EntreeClasspathKotlin(KindEntreeKotlin.JAR, j.toString(), "sonde-inf:$i:${j.fileName}") },
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
    for (nom in listOf("mutableMapOf", "mutableListOf", "listOf", "mapOf", "setOf", "mutableSetOf", "buildList", "emptyList")) {
        val appelables = vue.topParNom(nom)
        println("$nom → ${appelables.size} entrées")
        for (a in appelables.take(4)) {
            println("   paquet=${a.paquet} retour=${a.typeRetour} params=${a.parametres.map { "${it.nom}:${it.type}" }} recepteur=${a.recepteur}")
        }
    }
    // ── Le CHANTIER : typage inféré sans type écrit ────────────────────
    // cas 1 : mutableMapOf inféré → values.filter { it. }
    val s1 = "package demo\n\ndata class Note(val titre: String)\n\nval notes = mutableMapOf<Long, Note>()\n\nfun c(): List<Note> = notes.values.filter { it.ti }\n"
    session.ouvrir("file:///inf1.kt", s1)
    val r1 = session.completionArrangee("file:///inf1.kt", s1.indexOf("it.ti") + 3)
    val titrePresent = r1.propositions.any { it.libelle == "titre" }
    println("INFÉRÉ mutableMapOf values.filter it. → ${r1.propositions.size} items ; titre présent: $titrePresent")
    // cas 2 : mutableListOf inféré → filter
    val s2 = "package demo\n\ndata class Note(val titre: String)\n\nval notes = mutableListOf<Note>()\n\nfun c(): List<Note> = notes.filter { it.ti }\n"
    session.ouvrir("file:///inf2.kt", s2)
    val r2 = session.completionArrangee("file:///inf2.kt", s2.indexOf("it.ti") + 3)
    println("INFÉRÉ mutableListOf filter it. → ${r2.propositions.size} items")
    // cas 3 : listOf inféré
    val s3 = "package demo\n\ndata class Note(val titre: String)\n\nval notes = listOf<Note>()\n\nfun c(): List<Note> = notes.filter { it.ti }\n"
    session.ouvrir("file:///inf3.kt", s3)
    val r3 = session.completionArrangee("file:///inf3.kt", s3.indexOf("it.ti") + 3)
    println("INFÉRÉ listOf filter it. → ${r3.propositions.size} items")
    session.close()
    println("FIN")
}
