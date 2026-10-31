package jo.codeide.lsp.java.projetandroid

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import jo.codeide.lsp.java.MoteurJava
import jo.codeide.lsp.java.navigation.GenreCible

/**
 * Navigation RESSOURCE de la vague V-B2 (ADR 0028 §navigation + ADR 0020
 * référentiel) : `R.layout.x` / `R.id.x` / `R.string.x` / les champs de
 * styleable → le fichier XML déclarant, plage EXACTE du nom.
 *
 * Critère de sortie de la vague : « navigation `R.id.x` → layout : 100 %
 * cibles exactes » — chaque cas ci-dessous vérifie le FICHIER visé ET que
 * la plage couvre EXACTEMENT le nom de la ressource dans le XML.
 */
class NavigationRessourcesTest {

    private companion object {
        val MINI: Path = Path.of("src/test/resources/projetandroid/mini/app/src/main/java")
    }

    /** Le moteur mini (contrat dérivé, classes light actives). */
    private fun moteurMini(): MoteurJava {
        val contrat = ModeleAndroidDerive.deriver(listOf(MINI))!!
        return MoteurJava(listOf(MINI), emptyList(), null, "17", null, null, contrat)
    }

    @Test
    fun `r point layout vise le xml du layout a la plage exacte`() {
        val moteur = moteurMini()
        val source = """
            package com.exemple.mini;

            public class Nav {
                int naviguer() {
                    return R.layout.ecran_principal;
                }
            }
        """.trimIndent()
        val offset = source.indexOf("ecran_principal")

        val cibles = moteur.definition("/com/exemple/mini/Nav.java", source, offset)

        assertEquals(1, cibles.size, "exactement une cible")
        val cible = cibles.single()
        assertEquals(GenreCible.SOURCE_PROJET, cible.genre, "le XML est une cible PROJET (pas un stub)")
        assertTrue(cible.chemin!!.endsWith("res/layout/ecran_principal.xml"), "le dossier principal d'abord : ${cible.chemin}")
        // Ressource FICHIER : le nom n'est pas dans le XML — plage VIDE,
        // le client ouvre le layout (comportement de la référence).
        assertEquals(0, cible.plage.debutOffset)
        assertEquals(0, cible.plage.finOffset)
    }

    @Test
    fun `r point id vise la declaration at plus id dans le layout principal`() {
        val moteur = moteurMini()
        val source = """
            package com.exemple.mini;

            public class NavId {
                int viser() {
                    return R.id.text_titre;
                }
            }
        """.trimIndent()
        val cibles = moteur.definition("/com/exemple/mini/NavId.java", source, source.indexOf("text_titre"))

        assertEquals(1, cibles.size)
        val cible = cibles.single()
        assertTrue(cible.chemin!!.endsWith("res/layout/ecran_principal.xml"), "l'id du layout principal — pas de layout-land")
        val texte = Files.readString(cible.chemin)
        val extraite = texte.substring(cible.plage.debutOffset, cible.plage.finOffset)
        assertEquals("text_titre", extraite)
        // La cible vit DANS la valeur @+id/text_titre (pas ailleurs).
        assertTrue(extraite.isNotEmpty() && texte.regionMatches(cible.plage.debutOffset - 5, "@+id/", 0, 5), "la plage désigne le nom dans @+id/")
    }

    @Test
    fun `r point string et styleable visent le values xml a la plage exacte`() {
        val moteur = moteurMini()
        val source = """
            package com.exemple.mini;

            public class NavValeurs {
                int valeurs(int code) {
                    if (code == 1) return R.string.app_nom;
                    return R.styleable.Exemple_libelle;
                }
            }
        """.trimIndent()

        val cibleString = moteur.definition("/com/exemple/mini/NavValeurs.java", source, source.indexOf("app_nom")).single()
        assertTrue(cibleString.chemin!!.endsWith("values/ressources.xml"))
        assertEquals("app_nom", Files.readString(cibleString.chemin).substring(cibleString.plage.debutOffset, cibleString.plage.finOffset))

        val cibleStyleable = moteur.definition("/com/exemple/mini/NavValeurs.java", source, source.indexOf("Exemple_libelle")).single()
        assertTrue(cibleStyleable.chemin!!.endsWith("values/ressources.xml"))
        assertEquals(
            "libelle",
            Files.readString(cibleStyleable.chemin).substring(cibleStyleable.plage.debutOffset, cibleStyleable.plage.finOffset),
            "le champ X_attr vise l'attribut <attr name=\"attr\">",
        )
    }

    @Test
    fun `le type r lui-meme est un type binaire synthetique - pas une source`() {
        val moteur = moteurMini()
        // Le R light vit hors des sources : la sonde fiche 005 le dit
        // (le client proposera l'aperçu décompilé de l'API light).
        assertTrue(
            moteur.typeIndex().find("com.exemple.mini.R") is jo.codeide.lsp.java.classpath.EnvironnementNoms.Localisation.Synthetique,
        )
        // Et la définition du TYPE R (pas d'un champ) : cible BINAIRE
        // décompilée — jamais une cible source inventée.
        val source = """
            package com.exemple.mini;

            public class NavType {
                Class<?> type() {
                    return R.class;
                }
            }
        """.trimIndent()
        val cibles = moteur.definition("/com/exemple/mini/NavType.java", source, source.indexOf("R.class"))
        assertTrue(
            cibles.isEmpty() || cibles.all { it.genre == GenreCible.BINAIRE_DECOMPILE },
            "le type light n'a pas de fausse cible source : $cibles",
        )
    }
}
