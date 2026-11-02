package jo.codeide.lsp.java

import jo.codeide.lsp.java.inspections.BaseApiAndroid
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Vague V-C2 (suite) — SEGMENT COMPACT CIDX de la base API (ADR 0024 :
 * compilation binaire `owner+name+desc → since`, re-compilable
 * BIT-À-BIT, empreinte versionnée — critère de sortie de l'ADR).
 *
 * La vraie base (5,5 Mo, R6.4 jamais committée) vit en CI/appareil via
 * `lsp-java/scripts/preparer-base-api.sh` ; ce banc éprouve le FORMAT sur une base
 * synthétique : aller-retour des requêtes, déterminisme des octets,
 * EMPREINTES consignées, chemin rapide du chargeur.
 */
class SegmentBaseApiTest {

    @AfterEach
    fun nettoyer() {
        BaseApiAndroid.reinitialiser()
    }

    /** Une base synthétique couvrant classes, méthodes, champs, inlined. */
    private fun poserBaseSynthetique(): Path {
        val base = Files.createTempDirectory("vc2-segment")
        val xml = base.resolve("api-versions.xml")
        xml.writeText(
            """
            <api version="3">
              <class name="android/widget/TextView" since="1">
                <method name="setText(CharSequence)" since="1"/>
                <method name="setTextAlignment(int)" since="17"/>
              </class>
              <class name="android/view/View" since="1">
                <field name="TEXT_ALIGNMENT_CENTER" since="17" inlined="26"/>
              </class>
            </api>
            """.trimIndent(),
        )
        return xml
    }

    @Test
    fun `le segment fait l'aller-retour des niveaux de la base`() {
        val xml = poserBaseSynthetique()
        val segment = xml.resolveSibling("api-versions.xml.seg")
        val octets = BaseApiAndroid.compilerSegment(xml, segment)
        assertTrue(octets > 0 && Files.size(segment) == octets, "la taille rendue est la taille réelle")

        // Chargement par le segment DIRECTEMENT (propriété → .seg).
        BaseApiAndroid.reinitialiser()
        System.setProperty("jo.codeide.lsp.java.lint.baseapi", segment.toString())
        assertEquals(17, BaseApiAndroid.sinceMethode("android/widget/TextView", "setTextAlignment", listOf("int")))
        assertEquals(1, BaseApiAndroid.sinceMethode("android/widget/TextView", "setText", listOf("CharSequence")))
        assertEquals(1, BaseApiAndroid.sinceClasse("android/widget/TextView"))
        assertEquals(26, BaseApiAndroid.niveauChamp("android/view/View", "TEXT_ALIGNMENT_CENTER")?.inlined)
        assertEquals(null, BaseApiAndroid.sinceMethode("android/widget/TextView", "setText", listOf("Inconnu")))
    }

    @Test
    fun `le segment voisin du xml prend le chemin rapide du chargeur`() {
        val xml = poserBaseSynthetique()
        BaseApiAndroid.compilerSegment(xml, xml.resolveSibling("api-versions.xml.seg"))

        // Propriété pointant le XML : le .seg VOISIN doit être préféré —
        // preuve par la requête servie (et par une source xml
        // INTENTIONNELLEMENT différente : si le xml était lu, le
        // setTextAlignment(24) du pot de miel serait servi à la place).
        xml.writeText(
            """
            <api version="3">
              <class name="android/widget/TextView" since="1">
                <method name="setTextAlignment(int)" since="24"/>
              </class>
            </api>
            """.trimIndent(),
        )
        BaseApiAndroid.reinitialiser()
        System.setProperty("jo.codeide.lsp.java.lint.baseapi", xml.toString())
        assertEquals(
            17,
            BaseApiAndroid.sinceMethode("android/widget/TextView", "setTextAlignment", listOf("int")),
            "le segment voisin prime sur le xml (pot de miel à 24 non servi)",
        )
    }

    @Test
    fun `la recompilation est bit a bit (meme xml, memes octets)`() {
        val xml = poserBaseSynthetique()
        val premiere = xml.resolveSibling("a.seg")
        val seconde = xml.resolveSibling("b.seg")
        BaseApiAndroid.compilerSegment(xml, premiere)
        BaseApiAndroid.compilerSegment(xml, seconde)
        val octetsA = Files.readAllBytes(premiere)
        val octetsB = Files.readAllBytes(seconde)
        assertTrue(octetsA.contentEquals(octetsB), "deux compilations du même xml → octets identiques")
        assertEquals(BaseApiAndroid.empreinte(premiere), BaseApiAndroid.empreinte(seconde), "empreintes SHA-256 égales")

        // Et une source DIFFÉRENTE change l'empreinte (la signature vit).
        val autre = xml.resolveSibling("autre.xml")
        autre.writeText(
            """
            <api version="3">
              <class name="android/widget/TextView" since="2"/>
            </api>
            """.trimIndent(),
        )
        val troisieme = xml.resolveSibling("c.seg")
        BaseApiAndroid.compilerSegment(autre, troisieme)
        assertNotEquals(BaseApiAndroid.empreinte(premiere), BaseApiAndroid.empreinte(troisieme))
    }

    @Test
    fun `un segment corrompu retombe honnetement sur le xml`() {
        val xml = poserBaseSynthetique()
        val corrompu = xml.resolveSibling("api-versions.xml.seg")
        corrompu.writeText("ceci n'est pas un segment")
        BaseApiAndroid.reinitialiser()
        System.setProperty("jo.codeide.lsp.java.lint.baseapi", xml.toString())
        assertEquals(
            17,
            BaseApiAndroid.sinceMethode("android/widget/TextView", "setTextAlignment", listOf("int")),
            "segment illisible → repli sur le xml (jamais un silence fabriqué)",
        )
    }
}
