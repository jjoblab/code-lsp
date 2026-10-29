package jo.codeide.lsp.java.navigation

import jo.codeide.lsp.java.MoteurJava
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * Fiche 049 — AuditNavigationJavaTest : audit du panneau GO TO —
 * caractérisation à travers le ROUTEUR de navigation du moteur :
 * `definition` et `typeDefinition` CROSS-FICHIERS (chemin intra-fichier →
 * PROJET → binaire décompilé), `superDefinition` (garde de parité),
 * discipline des trois chemins et vide honnête sur document inconnu.
 *
 * Une cible PROJET n'atterrit JAMAIS dans le stub décompilé
 * `jdt://decompiled/…` ; une cible BINAIRE n'atterrit jamais dans les
 * sources. Corpus du cahier : `demo.Base` (champ `compteur`, `run()`,
 * `doubler(int)`, statique `creer()`), `demo.ImplA extends Base` (champ
 * `tag`, `@Override run()`), `demo.Consommateur` (usages croisés),
 * `demo.Utilitaire` + import statique `triple`, `demo.PorteurUn` /
 * `demo.PorteurDeux` (méthodes homonymes `run()`/`run(int)`),
 * `demo.AppeleurDeuxRun`, usages binaires `HashMap`.
 */
class Fiche049AuditNavigationTest {

    private companion object {

        lateinit var MOTEUR: MoteurJava
        lateinit var PROJET: Path
        lateinit var DOSSIER: Path

        val SOURCE_BASE = """
            package demo;

            public class Base {
                int compteur;

                public void run() {
                }

                public int doubler(int n) {
                    return n * 2;
                }

                public static Base creer() {
                    return new Base();
                }
            }
        """.trimIndent()

        val SOURCE_IMPL_A = """
            package demo;

            public class ImplA extends Base {
                String tag = "a";

                @Override
                public void run() {
                }
            }
        """.trimIndent()

        val SOURCE_CONSOMMATEUR = """
            package demo;

            import java.util.HashMap;

            public class Consommateur {
                // un commentaire de repos

                public void use(Base b, ImplA a) {
                    b.run();
                    b.doubler(21);
                    int valeur = a.tag.length();
                    Base fait = Base.creer();
                    int locale = 1;
                    int suivant = locale + 1;
                    HashMap<String, String> carte = new HashMap<>();
                    carte.put("a", "b");
                    int taille = carte.size();
                }

                public String texte() {
                    return "x";
                }
            }
        """.trimIndent()

        val SOURCE_UTILITAIRE = """
            package demo;

            public class Utilitaire {
                public static int triple(int n) {
                    return n * 3;
                }
            }
        """.trimIndent()

        val SOURCE_STATIQUE = """
            package demo;

            import static demo.Utilitaire.triple;

            public class Statique {
                public int appel() {
                    return triple(2);
                }
            }
        """.trimIndent()

        val SOURCE_PORTEUR_UN = """
            package demo;

            public class PorteurUn {
                public void run() {
                }
            }
        """.trimIndent()

        val SOURCE_PORTEUR_DEUX = """
            package demo;

            public class PorteurDeux {
                public void run(int n) {
                }
            }
        """.trimIndent()

        val SOURCE_APPELEUR = """
            package demo;

            public class AppeleurDeuxRun {
                public void appeler() {
                    PorteurUn un = new PorteurUn();
                    PorteurDeux deux = new PorteurDeux();
                    un.run();
                    deux.run(5);
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
            PROJET = Files.createTempDirectory("fiche049")
            DOSSIER = Files.createDirectories(PROJET.resolve("demo"))
            DOSSIER.resolve("Base.java").writeText(SOURCE_BASE)
            DOSSIER.resolve("ImplA.java").writeText(SOURCE_IMPL_A)
            DOSSIER.resolve("Consommateur.java").writeText(SOURCE_CONSOMMATEUR)
            DOSSIER.resolve("Utilitaire.java").writeText(SOURCE_UTILITAIRE)
            DOSSIER.resolve("Statique.java").writeText(SOURCE_STATIQUE)
            DOSSIER.resolve("PorteurUn.java").writeText(SOURCE_PORTEUR_UN)
            DOSSIER.resolve("PorteurDeux.java").writeText(SOURCE_PORTEUR_DEUX)
            DOSSIER.resolve("AppeleurDeuxRun.java").writeText(SOURCE_APPELEUR)
            MOTEUR = MoteurJava(racines = listOf(PROJET), jars = emptyList(), compliance = "17")
            assumeTrue(MOTEUR.isLikelyUsable(), "image jrt indisponible — audit ignorée")
        }

        @AfterAll
        @JvmStatic
        fun apresTout() {
            MOTEUR.dispose()
        }
    }

    /** definition_methodeInterFichiers_viseLaClasseDeclarante — méthode projet cross-fichiers. */
    @Test
    fun definitionMethodeInterFichiersViseLaClasseDeclarante() {
        val source = texte("Consommateur.java")
        // Caret sur le NOM « run » de « b.run() » (la position est CONTRACTUELLE).
        val caret = offsetDe(source, "b.run()") + 2
        val cibles = MOTEUR.definition("demo/Consommateur.java", source, caret)
        assertEquals(1, cibles.size, "1 cible exactement")
        val cible = cibles.first()
        assertEquals(GenreCible.SOURCE_PROJET, cible.genre, "la cible vit dans les sources du projet")
        assertTrue(cible.chemin!!.endsWith("demo/Base.java"), "la cible est dans Base.java (fichier NON ouvert) : ${cible.chemin}")
        val texteBase = cible.chemin.toFile().readText()
        val attendu = offsetDe(texteBase, "void run()") + "void ".length
        assertEquals(attendu, cible.plage.debutOffset, "la plage démarre sur le NOM run de « void run() »")
        assertEquals("run", texteBase.substring(cible.plage.debutOffset, cible.plage.finOffset))
    }

    /** definition_methodeHeritee_viseLeFichierDuDeclarant — méthode héritée : le DÉCLARANT porte la cible. */
    @Test
    fun definitionMethodeHeriteeViseLeFichierDuDeclarant() {
        val source = texte("Consommateur.java")
        // Caret sur le NOM « doubler » de « b.doubler(21) ».
        val caret = offsetDe(source, "b.doubler(21)") + 2
        val cibles = MOTEUR.definition("demo/Consommateur.java", source, caret)
        assertEquals(1, cibles.size, "1 cible")
        val cible = cibles.first()
        assertTrue(cible.chemin!!.endsWith("demo/Base.java"), "le déclarant Base porte la cible")
        assertEquals("doubler", cible.nomSimple, "la plage vise le nom doubler")
    }

    /** definition_champInterFichiers_viseSaDeclaration — champ cross-fichiers (caret sur le SEGMENT). */
    @Test
    fun definitionChampInterFichiersViseSaDeclaration() {
        val source = texte("Consommateur.java")
        // Caret SUR le segment « tag » de « a.tag » (le récepteur donnerait
        // le paramètre, le point le dernier chaînon).
        val caret = offsetDe(source, "a.tag") + 2
        val cibles = MOTEUR.definition("demo/Consommateur.java", source, caret)
        assertEquals(1, cibles.size, "1 cible")
        val cible = cibles.first()
        assertTrue(cible.chemin!!.endsWith("demo/ImplA.java"), "la déclaration du champ vit dans ImplA.java")
        val texteImpl = cible.chemin.toFile().readText()
        assertEquals("tag", texteImpl.substring(cible.plage.debutOffset, cible.plage.finOffset), "la plage vise le nom du champ tag")
    }

    /** definition_referenceDeType_viseLaClasse — référence de type. */
    @Test
    fun definitionReferenceDeTypeViseLaClasse() {
        val source = texte("Consommateur.java")
        // Caret sur le NOM « Base » de la signature (après « use( »).
        val caret = offsetDe(source, "use(Base b") + 4
        val cibles = MOTEUR.definition("demo/Consommateur.java", source, caret)
        assertEquals(1, cibles.size, "1 cible")
        val cible = cibles.first()
        assertTrue(cible.chemin!!.endsWith("demo/Base.java"), "la cible est la classe Base")
        val texteBase = cible.chemin.toFile().readText()
        val attendu = offsetDe(texteBase, "class Base") + "class ".length
        assertEquals(attendu, cible.plage.debutOffset, "la plage est sur le NOM de la CLASSE (après « class »)")
    }

    /** definition_constructeurProjet_viseLeNomDeLaClasse — invocation statique projet. */
    @Test
    fun definitionInvocationStatiqueViseLeNomDeLaMethode() {
        val source = texte("Consommateur.java")
        val caret = offsetDe(source, "Base.creer()") + "Base.".length
        val cibles = MOTEUR.definition("demo/Consommateur.java", source, caret)
        assertEquals(1, cibles.size, "1 cible")
        val cible = cibles.first()
        assertTrue(cible.chemin!!.endsWith("demo/Base.java"), "la factory statique vit dans Base.java")
        val texteBase = cible.chemin.toFile().readText()
        assertEquals("creer", texteBase.substring(cible.plage.debutOffset, cible.plage.finOffset), "la plage vise le nom creer")
    }

    /** definition_importStatique_viseLaMethodeImportee — import statique. */
    @Test
    fun definitionImportStatiqueViseLaMethodeImportee() {
        val source = texte("Statique.java")
        val caret = offsetDe(source, "triple(2)")
        val cibles = MOTEUR.definition("demo/Statique.java", source, caret)
        assertEquals(1, cibles.size, "1 cible")
        val cible = cibles.first()
        assertTrue(cible.chemin!!.endsWith("demo/Utilitaire.java"), "le binding résout via l'import statique")
        val texteUtil = cible.chemin.toFile().readText()
        assertEquals("triple", texteUtil.substring(cible.plage.debutOffset, cible.plage.finOffset), "la plage vise le nom triple")
    }

    /** definition_homonymes_deuxRun_differentParBindings — homonymes départagés par le binding (arité). */
    @Test
    fun definitionHomonymesDeuxRunDifferentParBindings() {
        val source = texte("AppeleurDeuxRun.java")
        // Invocation de PorteurUn.run() — sans argument (caret sur « run »).
        val caretUn = offsetDe(source, "un.run()") + 3
        val ciblesUn = MOTEUR.definition("demo/AppeleurDeuxRun.java", source, caretUn)
        assertEquals(1, ciblesUn.size, "1 cible par requête (première)")
        assertTrue(ciblesUn.first().chemin!!.endsWith("demo/PorteurUn.java"), "l'invocation sans argument vise PorteurUn")

        // Invocation de PorteurDeux.run(int) — avec argument (caret sur « run »).
        val caretDeux = offsetDe(source, "deux.run(5)") + 5
        val ciblesDeux = MOTEUR.definition("demo/AppeleurDeuxRun.java", source, caretDeux)
        assertEquals(1, ciblesDeux.size, "1 cible par requête (seconde)")
        assertTrue(ciblesDeux.first().chemin!!.endsWith("demo/PorteurDeux.java"), "l'invocation avec argument vise PorteurDeux")
    }

    /** definition_locale_viseLeFichierCourant — variable locale. */
    @Test
    fun definitionLocaleViseLeFichierCourant() {
        val source = texte("Consommateur.java")
        val caret = offsetDe(source, "locale + 1")
        val cibles = MOTEUR.definition("demo/Consommateur.java", source, caret)
        assertEquals(1, cibles.size, "1 cible")
        val cible = cibles.first()
        assertEquals(GenreCible.SOURCE_COURANTE, cible.genre, "la locale vit dans le FICHIER COURANT")
        assertEquals("locale", source.substring(cible.plage.debutOffset, cible.plage.finOffset), "la plage vise le nom de la locale")
    }

    /** definition_cibleBinaire_resteDecompilee — cible binaire. */
    @Test
    fun definitionCibleBinaireResteDecompilee() {
        val source = texte("Consommateur.java")
        // Caret sur le NOM « put » de « carte.put( » (récepteur binaire).
        val caret = offsetDe(source, "carte.put(") + 6
        val cibles = MOTEUR.definition("demo/Consommateur.java", source, caret)
        assertEquals(1, cibles.size, "1 cible")
        val cible = cibles.first()
        assertEquals(GenreCible.BINAIRE_DECOMPILE, cible.genre, "le fallback décompilé sert, jamais le chemin projet")
        assertTrue(cible.uri.startsWith("jdt://decompiled/"), "URI décompilée : ${cible.uri}")
        assertEquals(0, cible.plage.debutOffset, "plage vide (le client ouvre le tampon décompilé)")
        assertEquals(0, cible.plage.finOffset, "plage vide")
    }

    /** typeDefinition_variableProjet_viseLaClasse — type d'une variable projet. */
    @Test
    fun typeDefinitionVariableProjetViseLaClasse() {
        val source = texte("Consommateur.java")
        // Caret SUR le paramètre « a » (de type ImplA) dans « a.tag ».
        val caret = offsetDe(source, "a.tag")
        val cibles = MOTEUR.typeDefinition("demo/Consommateur.java", source, caret)
        assertEquals(1, cibles.size, "1 cible")
        val cible = cibles.first()
        assertTrue(cible.chemin!!.endsWith("demo/ImplA.java"), "le TYPE de la variable (ImplA), pas le champ")
        val texteImpl = cible.chemin.toFile().readText()
        assertEquals("ImplA", texteImpl.substring(cible.plage.debutOffset, cible.plage.finOffset), "la plage vise le nom de la classe ImplA")
    }

    /** typeDefinition_typeBinaire_resteDecompile — type binaire. */
    @Test
    fun typeDefinitionTypeBinaireResteDecompile() {
        val source = texte("Consommateur.java")
        // Caret sur « String » (type de retour binaire java.lang).
        val caret = offsetDe(source, "public String texte()") + "public ".length
        val cibles = MOTEUR.typeDefinition("demo/Consommateur.java", source, caret)
        assertEquals(1, cibles.size, "1 cible")
        val cible = cibles.first()
        assertTrue(cible.uri.startsWith("jdt://decompiled/java/lang/String"), "URI décompilée du type binaire : ${cible.uri}")
    }

    /** super_surOverride_viseLaMethodeDuSupertype — super sur override. */
    @Test
    fun superSurOverrideViseLaMethodeDuSupertype() {
        val source = texte("ImplA.java")
        val caret = offsetDe(source, "public void run()")
        val cibles = MOTEUR.superDefinition("demo/ImplA.java", source, caret)
        assertEquals(1, cibles.size, "1 cible")
        assertEquals("run  ·  Base", cibles.first().libelle, "libellé picker exact « run  ·  Base »")
        assertTrue(cibles.first().chemin!!.endsWith("demo/Base.java"), "la méthode du supertype vit dans Base.java")
    }

    /** super_surChampDeClasseSansSuperProjet_vide — super sur champ : retombée supertypes directs. */
    @Test
    fun superSurChampDeClasseRetombeSurLeSupertypeDirect() {
        val source = texte("ImplA.java")
        val caret = offsetDe(source, "tag = \"a\"")
        val cibles = MOTEUR.superDefinition("demo/ImplA.java", source, caret)
        assertEquals(1, cibles.size, "1 cible")
        assertEquals("Base  ·  demo", cibles.first().libelle, "libellé picker exact « Base  ·  demo »")
        assertTrue(cibles.first().chemin!!.endsWith("demo/Base.java"), "le supertype DIRECT de ImplA est Base")
    }

    /** ordreDesChemins_projetAvantBinaire_binaireQuandPasProjet — discipline des chemins (même flux). */
    @Test
    fun ordreDesCheminsProjetAvantBinaire() {
        val source = texte("Consommateur.java")
        // Une requête PROJET : b.run() → Base.java (caret sur « run »).
        val ciblesProjet = MOTEUR.definition("demo/Consommateur.java", source, offsetDe(source, "b.run()") + 2)
        assertEquals(1, ciblesProjet.size)
        assertEquals(GenreCible.SOURCE_PROJET, ciblesProjet.first().genre, "b.run() → le régime projet")

        // Puis une requête BINAIRE : .size() sur un HashMap fraîchement alloué.
        val ciblesBinaire = MOTEUR.definition("demo/Consommateur.java", source, offsetDe(source, "carte.size()") + "carte.".length)
        assertEquals(1, ciblesBinaire.size)
        assertEquals(GenreCible.BINAIRE_DECOMPILE, ciblesBinaire.first().genre, ".size() → le régime binaire")
        assertTrue(ciblesBinaire.first().uri.startsWith("jdt://decompiled/"), "aucune fuite entre les chemins")
    }

    /** definition_documentInconnu_ouCurseurHorsSymbole_vide — vide honnête. */
    @Test
    fun definitionDocumentInconnuOuCurseurHorsSymboleVide() {
        // Document JAMAIS ouvert, position 0:0 — liste NON NULL et VIDE.
        val cibles = MOTEUR.definition("introuvable.java", "", 0)
        assertNotNull(cibles, "le résultat n'est jamais null")
        assertTrue(cibles.isEmpty(), "liste VIDE — aucune exception, jamais de cible fantôme")

        // Curseur hors symbole (dans un commentaire) : vide honnête.
        val source = texte("Consommateur.java")
        val caret = offsetDe(source, "un commentaire de repos") + 3
        assertTrue(MOTEUR.definition("demo/Consommateur.java", source, caret).isEmpty(), "caret hors symbole → vide")
    }
}
