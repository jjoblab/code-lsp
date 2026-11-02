package mondes.compose

import exo.theme.MaterialTheme
import exo.layout.Arrangement

@Suppress("UNUSED_PARAMETER")
fun Ecran(contenu: (Int) -> Unit) {
    val typographie = MaterialTheme.typography
    val couleur = MaterialTheme.colorant
    val espacement = Arrangement.spacedBy(8)
    contenu(espacement + typographie.hashCode() + couleur.hashCode())
}
