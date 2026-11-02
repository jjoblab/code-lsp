package mondes.views

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.TextView

class MonActivite : Activity() {
    private lateinit var titre: TextView

    override fun onCreate(etat: Bundle?) {
        super.onCreate(etat)
        setContentView(titre)
    }

    fun rafraichir(vue: View) {
        vue.invalidate()
        titre.text = "Bonjour"
    }
}
