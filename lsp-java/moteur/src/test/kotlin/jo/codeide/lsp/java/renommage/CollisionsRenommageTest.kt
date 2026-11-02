package jo.codeide.lsp.java.renommage

import jo.codeide.lsp.java.MoteurJava
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * Corpus doré des COLLISIONS typées (étage N5 — critère de sortie
 * « 0 conflit manqué (oracle) », ADR 0014 §tests : « scénarios dorés :
 * renommage champ/méthode/classe, homonymes, overrides, imports
 * statiques, @Override cassé »).
 *
 * Couvert :
 *  - le RAPPEL des occurrences : la requête INDEXÉE égale l'ORACLE
 *    (balayage complet, même correspondance par clé) ;
 *  - NOM_PRIS (champ vers champ, méthode vers champ — tout genre
 *    confondu) ;
 *  - MASQUAGE (champ vers locale, locale vers champ englobant) et son
 *    anti-faux-positif (une locale d'une AUTRE méthode ne compte pas) ;
 *  - IDENTIFIANT_INVALIDE (mot-clé, identifiant illégal) ;
 *  - OVERRIDE_NON_SUIVI (défaut : l'override garde l'ancien nom, son
 *    @Override devient faux) et l'opt-in explicite (les déclarations
 *    outrépassées suivent, plus de collision) ;
 *  - l'import STATIQUE nominatif retouché (réparation) ;
 *  - l'APERÇU : preview = résultat.
 */
class CollisionsRenommageTest {

    private companion object {

        lateinit var MOTEUR: MoteurJava
        lateinit var PROJET: Path

        val SOURCE_COLLISION = """
            package demo;

            public class Collision {
                int gauche;
                int droite;

                public int calculer() {
                    int local = gauche + 1;
                    gauche = local;
                    return droite;
                }

                public void autre() {
                    int voisin = 2;
                }
            }
        """.trimIndent()

        val SOURCE_BASE = """
            package demo;

            public class Base {
                public void marcher() {
                }
            }
        """.trimIndent()

        val SOURCE_IMPL = """
            package demo;

            public class Impl extends Base {
                @Override
                public void marcher() {
                }
            }
        """.trimIndent()

        val SOURCE_APPELANT = """
            package demo;

            public class Appelant {
                public void go(Impl impl, Base base) {
                    impl.marcher();
                    base.marcher();
                }
            }
        """.trimIndent()

        val SOURCE_OUTIL = """
            package demo;

            public class Outil {
                public static int PLAFOND = 100;
            }
        """.trimIndent()

        val SOURCE_CONSOMMATEUR = """
            package demo;

            import static demo.Outil.PLAFOND;

            public class Consommateur {
                public int borne() {
                    return PLAFOND;
                }
            }
        """.trimIndent()

        fun texte(nom: String): String = PROJET.resolve("demo").resolve(nom).toFile().readText()

        fun offsetDe(source: String, mot: String, occurrence: Int = 0): Int {
            var position = -1
            repeat(occurrence + 1) { position = source.indexOf(mot, position + 1) }
            return position
        }

        @BeforeAll
        @JvmStatic
        fun avantTout() {
            PROJET = Files.createTempDirectory("renommage-conflits")
            val dossier = Files.createDirectories(PROJET.resolve("demo"))
            dossier.resolve("Collision.java").writeText(SOURCE_COLLISION)
            dossier.resolve("Base.java").writeText(SOURCE_BASE)
            dossier.resolve("Impl.java").writeText(SOURCE_IMPL)
            dossier.resolve("Appelant.java").writeText(SOURCE_APPELANT)
            dossier.resolve("Outil.java").writeText(SOURCE_OUTIL)
            dossier.resolve("Consommateur.java").writeText(SOURCE_CONSOMMATEUR)
            MOTEUR = MoteurJava(racines = listOf(PROJET), jars = emptyList(), compliance = "17")
            assumeTrue(MOTEUR.isLikelyUsable(), "image jrt indisponible — renommage ignoré")
        }

        @AfterAll
        @JvmStatic
        fun apresTout() {
            MOTEUR.dispose()
        }
    }

    /** rappelEgalOracle — la requête indexée égale l'oracle (champ, méthode, type). */
    @Test
    fun rappelEgalOracle() {
        // Champ.
        val source = texte("Collision.java")
        var caret = offsetDe(source, "int gauche;") + "int ".length
        assertEquals(
            MOTEUR.renommageProjetParBalayageComplet("demo/Collision.java", source, caret),
            MOTEUR.renommageProjet("demo/Collision.java", source, caret),
            "rappel = oracle (champ)",
        )
        // Méthode (override + appelants croix-fichiers).
        val sourceBase = texte("Base.java")
        caret = offsetDe(sourceBase, "public void marcher()") + "public void ".length
        assertEquals(
            MOTEUR.renommageProjetParBalayageComplet("demo/Base.java", sourceBase, caret),
            MOTEUR.renommageProjet("demo/Base.java", sourceBase, caret),
            "rappel = oracle (méthode)",
        )
        // Type.
        caret = offsetDe(sourceBase, "class Base {") + "class ".length
        assertEquals(
            MOTEUR.renommageProjetParBalayageComplet("demo/Base.java", sourceBase, caret),
            MOTEUR.renommageProjet("demo/Base.java", sourceBase, caret),
            "rappel = oracle (type)",
        )
    }

    /** champVersChampExistant — NOM_PRIS (genre confondu : le nouveau nom est déjà un membre). */
    @Test
    fun champVersChampExistant() {
        val source = texte("Collision.java")
        val caret = offsetDe(source, "int gauche;") + "int ".length
        val conflits = MOTEUR.conflitsRenommage("demo/Collision.java", source, caret, "droite")
        assertTrue(
            conflits.any { it.genre == GenreConflit.NOM_PRIS },
            "« droite » est déjà un champ de demo.Collision : ${conflits.map { it.genre }}",
        )
    }

    /** methodeVersChampExistant — NOM_PRIS croix-genre (une méthode ne prend pas le nom d'un champ). */
    @Test
    fun methodeVersChampExistant() {
        val source = texte("Collision.java")
        val caret = offsetDe(source, "public int calculer()") + "public int ".length
        val conflits = MOTEUR.conflitsRenommage("demo/Collision.java", source, caret, "droite")
        assertTrue(
            conflits.any { it.genre == GenreConflit.NOM_PRIS },
            "une méthode nommée « droite » colliderait avec le champ : ${conflits.map { it.genre }}",
        )
    }

    /** champVersLocaleDeMethode — MASQUAGE (le champ renommé serait masqué par une locale). */
    @Test
    fun champVersLocaleDeMethode() {
        val source = texte("Collision.java")
        val caret = offsetDe(source, "int gauche;") + "int ".length
        val conflits = MOTEUR.conflitsRenommage("demo/Collision.java", source, caret, "local")
        assertTrue(
            conflits.any { it.genre == GenreConflit.MASQUAGE },
            "le champ « gauche » renommé « local » serait masqué par la locale : ${conflits.map { it.genre }}",
        )
    }

    /** localeVersChampEnglobant — MASQUAGE (la locale renommée masquerait le champ). */
    @Test
    fun localeVersChampEnglobant() {
        val source = texte("Collision.java")
        val caret = offsetDe(source, "int local =") + "int ".length
        val conflits = MOTEUR.conflitsRenommage("demo/Collision.java", source, caret, "gauche")
        assertTrue(
            conflits.any { it.genre == GenreConflit.MASQUAGE },
            "la locale « local » renommée « gauche » masquerait le champ : ${conflits.map { it.genre }}",
        )
    }

    /** localeVersLocaleDuneAutreMethode — PAS de faux positif (périmètre de la méthode hôte). */
    @Test
    fun localeVersLocaleDuneAutreMethode() {
        val source = texte("Collision.java")
        val caret = offsetDe(source, "int local =") + "int ".length
        val conflits = MOTEUR.conflitsRenommage("demo/Collision.java", source, caret, "voisin")
        assertTrue(
            conflits.none { it.genre == GenreConflit.NOM_PRIS || it.genre == GenreConflit.MASQUAGE },
            "une locale d'une AUTRE méthode ne compte pas (anti R1) : ${conflits.map { it.genre }}",
        )
    }

    /** nouveauNomMotCle — IDENTIFIANT_INVALIDE (mot-clé). */
    @Test
    fun nouveauNomMotCle() {
        val source = texte("Collision.java")
        val caret = offsetDe(source, "int gauche;") + "int ".length
        val conflits = MOTEUR.conflitsRenommage("demo/Collision.java", source, caret, "class")
        assertEquals(1, conflits.size, "une seule collision, immédiate")
        assertEquals(GenreConflit.IDENTIFIANT_INVALIDE, conflits.first().genre)
    }

    /** nouveauNomInvalide — IDENTIFIANT_INVALIDE (identifiant illégal). */
    @Test
    fun nouveauNomInvalide() {
        val source = texte("Collision.java")
        val caret = offsetDe(source, "int gauche;") + "int ".length
        val conflits = MOTEUR.conflitsRenommage("demo/Collision.java", source, caret, "9solde")
        assertEquals(1, conflits.size, "une seule collision, immédiate")
        assertEquals(GenreConflit.IDENTIFIANT_INVALIDE, conflits.first().genre)
    }

    /** renommagePropreZeroConflit — aucune collision sur un renommage sain. */
    @Test
    fun renommagePropreZeroConflit() {
        val source = texte("Collision.java")
        val caret = offsetDe(source, "int gauche;") + "int ".length
        val conflits = MOTEUR.conflitsRenommage("demo/Collision.java", source, caret, "centre")
        assertTrue(conflits.isEmpty(), "renommage propre — zéro collision : ${conflits}")
    }

    /** overrideNonSuivi — défaut : la déclaration outrépassée garde l'ancien nom (collision typée). */
    @Test
    fun overrideNonSuivi() {
        val source = texte("Base.java")
        val caret = offsetDe(source, "public void marcher()") + "public void ".length
        val conflits = MOTEUR.conflitsRenommage("demo/Base.java", source, caret, "courir")
        val nonSuivis = conflits.filter { it.genre == GenreConflit.OVERRIDE_NON_SUIVI }
        assertEquals(1, nonSuivis.size, "Impl.marcher est outrépassée et non suivie : ${conflits.map { it.genre }}")
        val conflit = nonSuivis.first()
        assertTrue(conflit.chemin!!.endsWith("demo/Impl.java"), "la collision vit dans le fichier du sous-type")
        val texteImpl = texte("Impl.java")
        assertEquals("marcher", texteImpl.substring(conflit.plage!!.debutOffset, conflit.plage.finOffset), "plage du sélecteur outrépassé")
    }

    /** overrideSuiviOptIn — l'opt-in explicite : les déclarations outrépassées suivent, plus de collision. */
    @Test
    fun overrideSuiviOptIn() {
        val source = texte("Base.java")
        val caret = offsetDe(source, "public void marcher()") + "public void ".length
        val conflits = MOTEUR.conflitsRenommage("demo/Base.java", source, caret, "courir", suivreOverrides = true)
        assertTrue(
            conflits.none { it.genre == GenreConflit.OVERRIDE_NON_SUIVI },
            "opt-in armé : plus de collision d'override : ${conflits.map { it.genre }}",
        )
        val parFichier = MOTEUR.renommageProjet("demo/Base.java", source, caret, suivreOverrides = true)
        val occurrencesImpl = parFichier.filterKeys { it.endsWith("demo/Impl.java") }.values.flatten()
        assertEquals(1, occurrencesImpl.size, "la déclaration outrépassée EST retouchée")
        val texteImpl = texte("Impl.java")
        assertEquals(
            "marcher",
            texteImpl.substring(occurrencesImpl.first().plage.debutOffset, occurrencesImpl.first().plage.finOffset),
        )
    }

    /** importStatiqueRetouche — le segment d'un import statique nominatif est retouché (réparation). */
    @Test
    fun importStatiqueRetouche() {
        val source = texte("Outil.java")
        val caret = offsetDe(source, "public static int PLAFOND") + "public static int ".length
        val parFichier = MOTEUR.renommageProjet("demo/Outil.java", source, caret)
        val occurrencesConsommateur = parFichier.filterKeys { it.endsWith("demo/Consommateur.java") }.values.flatten()
        assertEquals(2, occurrencesConsommateur.size, "le segment d'import + l'usage : ${occurrencesConsommateur.size}")
        val texteConsommateur = texte("Consommateur.java")
        val surImport = occurrencesConsommateur.any {
            val ligne = texteConsommateur.substringBefore('\n').let { _ ->
                var debut = 0
                for (i in 0 until it.plage.debutOffset) {
                    if (texteConsommateur[i] == '\n') debut = i + 1
                }
                var fin = texteConsommateur.indexOf('\n', it.plage.debutOffset)
                if (fin < 0) fin = texteConsommateur.length
                texteConsommateur.substring(debut, fin)
            }
            ligne.trim().startsWith("import")
        }
        assertTrue(surImport, "l'import statique nominatif est retouché")
    }

    /** previewEgalResultat — l'aperçu EST le résultat de l'application des éditions. */
    @Test
    fun previewEgalResultat() {
        val source = texte("Collision.java")
        val caret = offsetDe(source, "int gauche;") + "int ".length
        val occurrences = MOTEUR.renommageProjet("demo/Collision.java", source, caret)
            .values.flatten()
            .sortedBy { it.plage.debutOffset }
        val editions = MOTEUR.editionsRenommage("centre", occurrences)
        val apercu = MOTEUR.apercuRenommage(source, editions)
        assertEquals(source.replace("gauche", "centre"), apercu, "preview = résultat")
        assertTrue("centre" in apercu && "gauche" !in apercu, "le renommage est appliqué dans l'aperçu")
    }
}
