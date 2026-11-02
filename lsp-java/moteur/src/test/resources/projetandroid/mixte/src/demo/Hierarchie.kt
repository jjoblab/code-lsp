package demo

/**
 * Hiérarchies Kotlin vues du Java (régression corpus V-A2 — AnySoftKeyboard :
 * `PackBroadcastReceiverBase` est une `open class` que le Java étend).
 *
 * - `open class BaseOuverte` : extensible depuis Java — NE DOIT PAS être
 *   synthétisée finale, sinon « cannot subclass the final class ».
 * - `class FermeeParDefaut` : finale par défaut en Kotlin — l'héritage Java
 *   DOIT être refusé (fidélité Kotlin, pas un faux négatif).
 * - `abstract class BaseAbstraite` : jamais finale.
 * - `object SingletonLie` : naturellement final (non extensible).
 */
open class BaseOuverte {
    open fun accueil(): String = "base"
}

class FermeeParDefaut {
    fun ferme(): Int = 1
}

abstract class BaseAbstraite {
    abstract fun calcul(): Int
}
