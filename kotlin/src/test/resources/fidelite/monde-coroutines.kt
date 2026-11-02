package mondes.coroutines

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collect

class ModeleEcran(etat: SharedFlow<Int>) {
    val flux: Flow<Int> = etat
}

@Suppress("UNUSED_EXPRESSION")
suspend fun demo(flux: Flow<Int>, modele: ModeleEcran) {
    flux.collect { valeur -> valeur + 1 }
    modele.flux.collect { valeur -> valeur + 1 }
}
