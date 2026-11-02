package jo.codeide.lsp.java

import jo.codeide.lsp.java.classpath.CachePresenceSource
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Fiche 006 — CachePresenceSourceTest : cache négatif de présence source
 * (v1.9.1). La fenêtre de masquage du négatif (TTL 30 s) est CONTRACTUELLE ;
 * l'invalidation par événement Created rend la classe visible immédiatement.
 *
 * Le comportement du cache est INDÉPENDANT d'android.jar (note du cahier) :
 * le test tourne partout.
 */
class Fiche006CachePresenceSourceTest {

    private fun preparerRacine(): Path {
        val racine = Files.createTempDirectory("fiche006")
        val paquet = racine.resolve("com/example/c")
        Files.createDirectories(paquet)
        return racine
    }

    @Test
    fun classeCreeeApresNegatifDevientVisibleApresInvalidation() {
        val racine = preparerRacine()
        val cache = CachePresenceSource(listOf(racine), ttlMillis = 30_000)

        // (1) find sur une classe absente → négatif mémorisé.
        assertNull(cache.fichierPour("com.example.c.Nouvelle"), "classe absente : null")

        // (2) écriture de Nouvelle.java SUR DISQUE (nouvelle classe hors éditeur).
        racine.resolve("com/example/c").resolve("Nouvelle.java").writeText(
            "package com.example.c; public class Nouvelle { }\n",
        )

        // (3) find SANS invalidation : le négatif TTL MASQUE encore la création
        // (fenêtre de masquage documentée et verrouillée par le cahier).
        assertNull(cache.fichierPour("com.example.c.Nouvelle"), "fenêtre de masquage : null tant que le négatif vit")

        // (4) invalidation par événement Created → la classe résout immédiatement.
        cache.invalider()
        assertNotNull(cache.fichierPour("com.example.c.Nouvelle"), "après invalidation : la classe résout")
    }

    @Test
    fun sourceExistanteResteToujoursLisible() {
        val racine = preparerRacine()
        racine.resolve("com/example/c").resolve("Stable.java").writeText(
            "package com.example.c; public class Stable { }\n",
        )
        val cache = CachePresenceSource(listOf(racine), ttlMillis = 30_000)
        assertNotNull(cache.fichierPour("com.example.c.Stable"), "lecture 1 : non-null")
        assertNotNull(cache.fichierPour("com.example.c.Stable"), "lecture 2 : non-null")
        assertNotNull(cache.fichierPour("com.example.c.Stable"), "lecture 3 : non-null (les positifs ne sont jamais cachés)")
        assertEquals(0, cache.taille(), "aucun négatif mémorisé pour les sources existantes")
    }
}
