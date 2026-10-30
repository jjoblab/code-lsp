package mondes.stdlib

import kotlin.text.Regex as Motif

class Boite(val contenu: String) {
    companion object {
        const val CLE = "boite"

        fun creer(): Boite = Boite("neuve")
    }
}

@Suppress("UNUSED_VARIABLE")
fun demo() {
    val a = arrayOf(1, 2, 3)
    val b = intArrayOf(1, 2)
    val c = listOf(a.size, b.size)
    val d = emptyArray<String>()
    println(c.joinToString() + d.size)
    val m = Motif("a.b")
    val e = m.matches("a.b")
    val f = Boite.creer()
    val g = f.contenu.length + Boite.CLE.length
    val h = kotlin.io.println("qualifié")
    val i = lazy { e }
    val j = g + i.value.toString().length + h.toString().length
    val k = j.toString()
}
