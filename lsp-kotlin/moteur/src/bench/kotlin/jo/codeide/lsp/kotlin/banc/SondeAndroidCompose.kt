// Sonde manuelle : que rend le moteur sur les positions défaillantes ?
// (script de diagnostic, pas un test du cahier)
package jo.codeide.lsp.kotlin.banc

import jo.codeide.lsp.kotlin.api.ClasspathKotlin
import jo.codeide.lsp.kotlin.api.EntreeClasspathKotlin
import jo.codeide.lsp.kotlin.api.KindEntreeKotlin
import jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin
import jo.codeide.lsp.kotlin.session.MoteurLangageKotlinNoyau
import jo.codeide.lsp.kotlin.session.SessionLangageKotlinDocuments
import java.nio.file.Files
import java.nio.file.Path

fun main() {
    val projet = Path.of("/home/z/my-project/projet-test-android")
    val jars = Files.list(projet.resolve("classpath-jars")).use { f ->
        f.filter { it.fileName.toString().endsWith(".jar") }.sorted().toList()
    }
    println("jars: ${jars.size}")
    val classpath = ClasspathKotlin(
        entrees = jars.mapIndexed { i, j -> EntreeClasspathKotlin(KindEntreeKotlin.JAR, j.toString(), "sonde:$i:${j.fileName}") },
        racinesSources = listOf(projet.resolve("app/src/main/java").toString()),
    )
    val moteur = ServiceMoteurLangageKotlin.unique as MoteurLangageKotlinNoyau
    moteur.definirClasspath(classpath)
    val session = moteur.creerSession() as SessionLangageKotlinDocuments
    session.definirClasspath(classpath)

    // Attente index
    val limite = System.currentTimeMillis() + 300_000
    while (System.currentTimeMillis() < limite) {
        if (session.indexSiPret() != null) break
        Thread.sleep(50)
    }
    println("index prêt: ${session.indexSiPret() != null}")

    // ── Sonde 1 : it. dans un lambda filter (data class Note)
    val source = """
        package demo

        data class Note(val id: Long, val titre: String, val contenu: String)

        fun test(notes: List<Note>) {
            notes.filter { it.ti }
        }
    """.trimIndent() + "\n"
    session.ouvrir("file:///sonde1.kt", source)
    val off1 = source.indexOf("it.ti") + 3 // après le point
    val rep1 = session.completionArrangee("file:///sonde1.kt", off1)
    println("SONDE it. → ${rep1.propositions.size} items : ${rep1.propositions.take(8).map { it.libelle }}")

    // ── Sonde 2 : même chose avec lambda param nommé (n -> n.ti)
    val source2 = """
        package demo

        data class Note(val id: Long, val titre: String)

        fun test(notes: List<Note>) {
            notes.filter { n -> n.ti }
        }
    """.trimIndent() + "\n"
    session.ouvrir("file:///sonde2.kt", source2)
    val off2 = source2.indexOf("n.ti") + 2
    val rep2 = session.completionArrangee("file:///sonde2.kt", off2)
    println("SONDE n. (param lambda) → ${rep2.propositions.size} items : ${rep2.propositions.take(8).map { it.libelle }}")

    // ── Sonde 3 : import androidx.compose.material3. (après le point final)
    val source3 = "package demo\n\nimport androidx.compose.material3.Te\n\nfun x() {}\n"
    session.ouvrir("file:///sonde3.kt", source3)
    val off3 = source3.indexOf("material3.Te") + "material3.".length
    val rep3 = session.completion("file:///sonde3.kt", off3)
    val avecText = rep3.any { it.libelle == "Text" }
    val avecButton = rep3.any { it.libelle == "Button" }
    println("SONDE import material3 (caret après point, préfixe Te) → ${rep3.size} items ; contient Text: $avecText ; contient Button: $avecButton ; extraits=${rep3.take(8).map { it.libelle }}")
    // Sonde 3b : caret APRÈS le "Te" complet (le vrai point d'insertion).
    val off3b = source3.indexOf("material3.Te") + "material3.Te".length
    val rep3b = session.completion("file:///sonde3.kt", off3b)
    val avecTextB = rep3b.any { it.libelle == "Text" }
    println("SONDE import material3.Te (caret après Te) → ${rep3b.size} items ; contient Text: $avecTextB ; extraits=${rep3b.take(8).map { it.libelle }}")
    // Sonde 3c : l'index porte-t-il Text de material3 ?
    val guichetSonde = session.indexSiPret()
    val textIdx = guichetSonde?.vue?.topParNom("Text").orEmpty()
    println("SONDE index topParNom(Text) → ${textIdx.size} appelables ; paquets=${textIdx.map { it.paquet }.distinct().take(6)}")
    val dansPaquet = guichetSonde?.vue?.topDansPaquet("", "androidx.compose.material3", 400).orEmpty()
    val textDansPaquet = dansPaquet.any { it.nom == "Text" }
    println("SONDE index topDansPaquet(material3, préfixe vide) → ${dansPaquet.size} appelables ; contient Text: $textDansPaquet")

    // ── Sonde 4 : signature help Text(
    val source4 = """
        package demo

        import androidx.compose.material3.Text

        @Composable
        fun e() {
            Text(text = "x" )
        }
    """.trimIndent() + "\n"
    session.ouvrir("file:///sonde4.kt", source4)
    val off4 = source4.indexOf("Text(") + 5
    val aide = session.signature("file:///sonde4.kt", off4)
    println("SONDE signature Text( → ${aide?.signatures?.size ?: 0} signatures ; première = ${aide?.signatures?.firstOrNull()?.etiquette}")

    // ── Sonde 5 : modifier.padding — définition
    val source5 = """
        package demo

        import androidx.compose.ui.Modifier
        import androidx.compose.foundation.layout.padding

        fun e(m: Modifier): Modifier = m.padding(8)
    """.trimIndent() + "\n"
    session.ouvrir("file:///sonde5.kt", source5)
    val off5 = source5.indexOf("m.padding")
    val defs = session.definition("file:///sonde5.kt", off5 + 2)
    println("SONDE definition padding → ${defs.size} locs : ${defs.take(2).map { it.uri }}")

    // ── Sonde 6 : position EXACTE du test LSP dans le vrai fichier Note.kt
    val noteReel = Files.readString(projet.resolve("app/src/main/java/com/demo/noteapp/Note.kt"))
    session.ouvrir("file:///note-reel.kt", noteReel)
    val offIt = noteReel.indexOf("it.titre.contains")
    val repIt = session.completionArrangee("file:///note-reel.kt", offIt + 3)
    println("SONDE it.titre (vrai fichier, caret sur t) → ${repIt.propositions.size} items, genre=${repIt.genre} ; extraits=${repIt.propositions.take(6).map { it.libelle }}")
    // Variante : récepteur List<Note> explicite (comme la sonde 1)
    val srcListe = "package demo\n\ndata class Note(val titre: String)\n\nclass R {\n    val notes: List<Note> = listOf()\n    fun c(): List<Note> = notes.filter { it.ti }\n}\n"
    session.ouvrir("file:///sonde-liste.kt", srcListe)
    val offL = srcListe.indexOf("it.ti") + 3
    val repL = session.completionArrangee("file:///sonde-liste.kt", offL)
    println("SONDE List filter it. (classe membre) → ${repL.propositions.size} items ; extraits=${repL.propositions.take(5).map { it.libelle }}")
    // Variante : Map.values.filter { it. }
    val srcVals = "package demo\n\ndata class Note(val titre: String)\n\nclass R {\n    val notes = mutableMapOf<Long, Note>()\n    fun c(): List<Note> = notes.values.filter { it.ti }\n}\n"
    session.ouvrir("file:///sonde-vals.kt", srcVals)
    val offV = srcVals.indexOf("it.ti") + 3
    val repV = session.completionArrangee("file:///sonde-vals.kt", offV)
    println("SONDE Map.values filter it. → ${repV.propositions.size} items ; extraits=${repV.propositions.take(5).map { it.libelle }}")
    // Variante : top-level fun avec Map.values (comme le vrai fichier)
    val srcTop = "package demo\n\ndata class Note(val titre: String)\nval notes = mutableMapOf<Long, Note>()\nfun c(): List<Note> = notes.values.filter { it.ti }\n"
    session.ouvrir("file:///sonde-top.kt", srcTop)
    val offT = srcTop.indexOf("it.ti") + 3
    val repT = session.completionArrangee("file:///sonde-top.kt", offT)
    println("SONDE top-level Map.values filter it. → ${repT.propositions.size} items ; extraits=${repT.propositions.take(5).map { it.libelle }}")

    session.close()
    println("FIN SONDE")
}
