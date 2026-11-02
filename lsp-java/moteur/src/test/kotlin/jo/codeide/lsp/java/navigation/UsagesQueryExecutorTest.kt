package jo.codeide.lsp.java.navigation

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
 * QueryExecutor des usages (étage N4 — ADR 0014, design intégré) :
 * `references`/`documentHighlight` EXHAUSTIFS par clé de binding sur
 * l'index de mots des sources — le critère de sortie de l'étage :
 *
 *  - **rappel usages = oracle** : la requête INDEXÉE rend EXACTEMENT le
 *    même ensemble que l'oracle (balayage complet + même correspondance) ;
 *  - **`Files.walk` retiré du chemin chaud** : l'index de mots se
 *    construit UNE fois (compteur observable), jamais par requête ;
 *  - l'overlay tampons-ouverts prime sur le disque (le document interrogé
 *    est analysé sur la source DONNÉE) ; l'invalidation par événement
 *    Created découvre les fichiers nouveaux.
 */
class UsagesQueryExecutorTest {

    private companion object {

        lateinit var MOTEUR: MoteurJava
        lateinit var PROJET: Path
        lateinit var DOSSIER: Path

        val SOURCE_CALC = """
            package demo;

            public class Calc {
                int memoire;

                public int ajouter(int a, int b) {
                    return a + b;
                }

                public int doubler(int n) {
                    return ajouter(n, n);
                }
            }
        """.trimIndent()

        val SOURCE_VUE = """
            package demo;

            public class Vue {
                public int rendu(Calc c) {
                    int total = c.ajouter(1, 2);
                    c.memoire = total;
                    total += 3;
                    total++;
                    ++total;
                    c.memoire += 1;
                    int relu = c.memoire;
                    return c.doubler(total);
                }
            }
        """.trimIndent()

        val SOURCE_AUTRE = """
            package demo;

            public class Autre {
                public int appel() {
                    Calc c = new Calc();
                    return c.ajouter(3, 4);
                }

                public int ajouter(int x) {
                    return x + 1;
                }
            }
        """.trimIndent()

        fun texte(nom: String): String = DOSSIER.resolve(nom).toFile().readText()

        fun offsetDe(source: String, mot: String, occurrence: Int = 0): Int {
            var position = -1
            repeat(occurrence + 1) { position = source.indexOf(mot, position + 1) }
            return position
        }

        @BeforeAll
        @JvmStatic
        fun avantTout() {
            PROJET = Files.createTempDirectory("usages-qe")
            DOSSIER = Files.createDirectories(PROJET.resolve("demo"))
            DOSSIER.resolve("Calc.java").writeText(SOURCE_CALC)
            DOSSIER.resolve("Vue.java").writeText(SOURCE_VUE)
            DOSSIER.resolve("Autre.java").writeText(SOURCE_AUTRE)
            MOTEUR = MoteurJava(racines = listOf(PROJET), jars = emptyList(), compliance = "17")
            assumeTrue(MOTEUR.isLikelyUsable(), "image jrt indisponible — usages ignorés")
        }

        @AfterAll
        @JvmStatic
        fun apresTout() {
            MOTEUR.dispose()
        }
    }

    /** references_methode_rappelEgalOracle — la requête indexée égale l'oracle (rappel 100 %). */
    @Test
    fun referencesMethodeRappelEgalOracle() {
        val source = texte("Vue.java")
        val caret = offsetDe(source, "c.ajouter(1, 2)") + 2
        val indexee = MOTEUR.references("demo/Vue.java", source, caret)
        val oracle = MOTEUR.referencesParBalayageComplet("demo/Vue.java", source, caret)
        assertTrue(indexee.isNotEmpty(), "des usages sont trouvés")
        assertTrue(oracle.isNotEmpty(), "l'oracle trouve des usages")
        assertEquals(oracle.toSet(), indexee.toSet(), "rappel usages = oracle (méthode) :\nindexée=$indexee\noracle=$oracle")
    }

    /** references_champ_rappelEgalOracle — un champ, écritures et lectures comprises. */
    @Test
    fun referencesChampRappelEgalOracle() {
        val source = texte("Vue.java")
        val caret = offsetDe(source, "c.memoire = total") + 2
        val indexee = MOTEUR.references("demo/Vue.java", source, caret)
        val oracle = MOTEUR.referencesParBalayageComplet("demo/Vue.java", source, caret)
        assertTrue(indexee.size >= 3, "déclaration (Calc) + écriture + lecture : ${indexee.size}")
        assertEquals(oracle.toSet(), indexee.toSet(), "rappel usages = oracle (champ)")
    }

    /** references_type_rappelEgalOracle — un type (références de type et allocations). */
    @Test
    fun referencesTypeRappelEgalOracle() {
        val source = texte("Vue.java")
        val caret = offsetDe(source, "Calc c")
        val indexee = MOTEUR.references("demo/Vue.java", source, caret)
        val oracle = MOTEUR.referencesParBalayageComplet("demo/Vue.java", source, caret)
        assertTrue(indexee.size >= 4, "déclaration + références de type + allocation : ${indexee.size}")
        assertEquals(oracle.toSet(), indexee.toSet(), "rappel usages = oracle (type)")
    }

    /** references_homonymes_departages — la clé (propriétaire + arité) départage les homonymes. */
    @Test
    fun referencesHomonymesDepartages() {
        val source = texte("Autre.java")
        // L'invocation de Calc.ajouter(int, int) — l'HOMONYME Autre.ajouter(int)
        // (même sélecteur, propriétaire et arité différents) ne doit PAS fuiter.
        val caret = offsetDe(source, "c.ajouter(3, 4)") + 2
        val usages = MOTEUR.references("demo/Autre.java", source, caret)
        val fichiers = usages.map { it.chemin }.distinct()
        assertTrue(fichiers.any { it.endsWith("demo/Calc.java") }, "le déclarant Calc est couvert")
        val occurrencesAutre = usages.count { it.chemin.endsWith("demo/Autre.java") }
        assertEquals(1, occurrencesAutre, "seule L'INVOCATION de Autre.java (ni l'homonyme déclaré, ni sa déclaration)")
        assertEquals(true, usages.any { it.estDeclaration }, "la DÉCLARATION de Calc.ajouter est comprise")
    }

    /** filesWalk_retire_du_chemin_chaud — l'index se construit UNE fois pour N requêtes. */
    @Test
    fun filesWalkRetireDuCheminChaud() {
        val source = texte("Vue.java")
        // Premier appel : la construction froide (balayage + lexage uniques).
        MOTEUR.references("demo/Vue.java", source, offsetDe(source, "c.ajouter(1, 2)") + 2)
        val apresPremiere = MOTEUR.constructionsIndexMots()
        // N requêtes suivantes : plus AUCUN balayage d'arborescence.
        repeat(8) {
            MOTEUR.references("demo/Vue.java", source, offsetDe(source, "c.memoire = total") + 2)
            MOTEUR.references("demo/Calc.java", texte("Calc.java"), offsetDe(texte("Calc.java"), "ajouter(n, n)"))
        }
        assertEquals(apresPremiere, MOTEUR.constructionsIndexMots(), "le compteur de constructions ne bouge plus (Files.walk hors chemin chaud)")
        assertEquals(1, MOTEUR.constructionsIndexMots(), "une seule construction froide depuis la naissance")
    }

    /** documentHighlight_variable — lectures et écritures de la locale. */
    @Test
    fun documentHighlightVariableLecturesEtEcritures() {
        val source = texte("Vue.java")
        val caret = offsetDe(source, "int total =") + "int ".length
        val surbrillances = MOTEUR.surbrillances("demo/Vue.java", source, caret)
        assertTrue(surbrillances.size >= 3, "déclaration + écriture (c.memoire = total) + lecture (doubler(total)) : ${surbrillances.size}")
        val genres = surbrillances.map { it.genre }
        assertTrue(GenreSurbrillance.ECRITURE in genres, "la déclaration est une ÉCRITURE")
        assertTrue(GenreSurbrillance.LECTURE in genres, "les lectures sont des LECTURES")
        // Les plages désignent le JETON « total ».
        for (s in surbrillances) {
            assertEquals("total", source.substring(s.plage.debutOffset, s.plage.finOffset), "plage du jeton total")
        }
    }

    /** Le genre de(s) l'occurrence(s) portée(s) par la LIGNE d'un extrait anchored. */
    private fun genreDeLaLigne(source: String, surbrillances: List<Surbrillance>, ancre: String): GenreSurbrillance {
        val indice = source.indexOf(ancre)
        assertTrue(indice >= 0, "ancre absente de la fixture : $ancre")
        val ligne = source.substring(0, indice).count { it == '\n' }
        val genres = surbrillances
            .filter { source.substring(0, it.plage.debutOffset).count { c -> c == '\n' } == ligne }
            .map { it.genre }
            .distinct()
        assertEquals(1, genres.size, "un seul genre sur la ligne de « $ancre » — trouvé : $genres")
        return genres.single()
    }

    /**
     * documentHighlight_affectations_composées — `+=`, `x++` et `++x`
     * ÉCRIVENT leur cible (vague V-E, G-026 — ADR 0028 (b)). La
     * hiérarchie ecj (Assignment ← CompoundAssignment ← Postfix/Prefix)
     * route ces nœuds hors du pont visit(Assignment) : sans ponts
     * dédiés, une composée était typée LECTURE.
     */
    @Test
    fun documentHighlightAffectationsComposeesEtIncrements() {
        val source = texte("Vue.java")
        val caret = offsetDe(source, "int total =") + "int ".length
        val surbrillances = MOTEUR.surbrillances("demo/Vue.java", source, caret)

        assertEquals(GenreSurbrillance.ECRITURE, genreDeLaLigne(source, surbrillances, "int total ="), "la déclaration est une ÉCRITURE")
        assertEquals(GenreSurbrillance.ECRITURE, genreDeLaLigne(source, surbrillances, "total += 3"), "l'affectation composée += ÉCRIT total")
        assertEquals(GenreSurbrillance.ECRITURE, genreDeLaLigne(source, surbrillances, "total++"), "le postfixe x++ ÉCRIT total")
        assertEquals(GenreSurbrillance.ECRITURE, genreDeLaLigne(source, surbrillances, "++total"), "le préfixe ++x ÉCRIT total")
        assertEquals(GenreSurbrillance.LECTURE, genreDeLaLigne(source, surbrillances, "c.memoire = total"), "l'opérande de DROITE est une LECTURE")
        assertEquals(GenreSurbrillance.LECTURE, genreDeLaLigne(source, surbrillances, "doubler(total)"), "l'argument d'appel est une LECTURE")
    }

    /** documentHighlight_champ_composé — le segment écrit d'un champ qualifié (c.memoire += 1) est une ÉCRITURE. */
    @Test
    fun documentHighlightChampAffectationComposee() {
        val source = texte("Vue.java")
        val caret = offsetDe(source, "c.memoire += 1") + 2 // sur « memoire »
        val surbrillances = MOTEUR.surbrillances("demo/Vue.java", source, caret)
        assertEquals(3, surbrillances.size, "3 occurrences du champ dans le document SEUL (écriture simple, composée, lecture) : ${surbrillances.size}")
        assertEquals(GenreSurbrillance.ECRITURE, genreDeLaLigne(source, surbrillances, "c.memoire = total"), "l'écriture simple du champ")
        assertEquals(GenreSurbrillance.ECRITURE, genreDeLaLigne(source, surbrillances, "c.memoire += 1"), "l'affectation composée du champ ÉCRIT le segment")
        assertEquals(GenreSurbrillance.LECTURE, genreDeLaLigne(source, surbrillances, "int relu = c.memoire"), "la lecture du champ")
        // Les plages désignent le JETON « memoire » (le SEGMENT, pas « c.memoire »).
        for (s in surbrillances) {
            assertEquals("memoire", source.substring(s.plage.debutOffset, s.plage.finOffset), "plage du jeton memoire")
        }
    }

    /** documentHighlight_methode — occurrences textuelles dans le document seul. */
    @Test
    fun documentHighlightMethode() {
        val source = texte("Calc.java")
        val caret = offsetDe(source, "ajouter(n, n)")
        val surbrillances = MOTEUR.surbrillances("demo/Calc.java", source, caret)
        assertEquals(2, surbrillances.size, "la déclaration + l'invocation de doubler, dans le document SEUL")
        assertTrue(surbrillances.all { it.genre == GenreSurbrillance.TEXTE }, "méthode → TEXTE")
    }

    /** references_inclureDeclaration_false — le paramètre LSP filtre les déclarations. */
    @Test
    fun referencesInclureDeclarationFalse() {
        val source = texte("Vue.java")
        val caret = offsetDe(source, "c.ajouter(1, 2)") + 2
        val avec = MOTEUR.references("demo/Vue.java", source, caret, inclureDeclaration = true)
        val sans = MOTEUR.references("demo/Vue.java", source, caret, inclureDeclaration = false)
        assertEquals(avec.size - 1, sans.size, "seule la DÉCLARATION est retranchée")
        assertTrue(avec.any { it.estDeclaration } && sans.none { it.estDeclaration }, "le drapeau est reporté")
    }

    /** overlay_tampons_prime — le document interrogé est analysé sur la source DONNÉE. */
    @Test
    fun overlayTamponsPrime() {
        val source = texte("Vue.java")
        // Le tampon OUVERT ajoute un appel SANS toucher au disque.
        val tampon = source.replace(
            "return c.doubler(total);",
            "int encore = c.ajouter(9, 9);\n                    return c.doubler(total);",
        )
        val caret = offsetDe(tampon, "c.ajouter(1, 2)") + 2
        val usages = MOTEUR.references("demo/Vue.java", tampon, caret)
        // Le tampon PRIME : les DEUX invocations de Vue — (1,2) existante et
        // (9,9) AJOUTÉE au tampon — vivent dans le document courant.
        val occurrences = usages.count { it.chemin.endsWith("demo/Vue.java") }
        assertEquals(2, occurrences, "l'appel AJOUTÉ au tampon est trouvé (overlay > disque)")

        // Le tampon RETIRE un appel : l'usage disparaît — l'occurrence rendue
        // est celle du TAMPON (9,9), jamais celle du disque (1,2) restée en
        // dessous (l'overlay des AUTRES documents est porté par la couche
        // LSP de N8 — le moteur ne connaît que le document interrogé).
        val tamponRetire = tampon.replace("int total = c.ajouter(1, 2);\n        ", "")
        val caretRetire = offsetDe(tamponRetire, "c.ajouter(9, 9)") + 2
        val usagesRetire = MOTEUR.references("demo/Vue.java", tamponRetire, caretRetire)
        val occurrencesVue = usagesRetire.filter { it.chemin.endsWith("demo/Vue.java") }
        assertEquals(1, occurrencesVue.size, "l'appel retiré du tampon disparaît (le disque ne fuit pas)")
        assertEquals(
            offsetDe(tamponRetire, "c.ajouter(9, 9)") + 2,
            occurrencesVue.single().plage.debutOffset,
            "l'occurrence est celle du TAMPON (9,9), pas celle du disque (1,2)",
        )
    }

    /** invalidation_fichier_cree — l'événement Created découvre les fichiers nouveaux. */
    @Test
    fun invalidationFichierCree() {
        val source = texte("Autre.java")
        val caret = offsetDe(source, "c.ajouter(3, 4)") + 2
        val avant = MOTEUR.references("demo/Autre.java", source, caret).size

        // Un NOUVEAU fichier (postérieur à la construction de l'index) appelle ajouter.
        val nouveau = """
            package demo;

            public class Nouveau {
                public int frais() {
                    Calc c = new Calc();
                    return c.ajouter(5, 6);
                }
            }
        """.trimIndent()
        DOSSIER.resolve("Nouveau.java").writeText(nouveau)

        // Sans invalidation : le fichier nouveau n'est PAS candidat (le
        // branchement didSave/watcher arrive en N8 — comportement documenté).
        val sansInvalidation = MOTEUR.references("demo/Autre.java", source, caret).size
        assertEquals(avant, sansInvalidation, "le fichier créé n'est pas découvert avant l'invalidation")

        // Avec invalidation (événement Created — fiche 006) : découvert.
        MOTEUR.invalidateSourcePresence()
        val apres = MOTEUR.references("demo/Autre.java", source, caret)
        assertEquals(avant + 1, apres.size, "l'invalidation découvre le fichier nouveau (rappel rétabli)")
    }
}
