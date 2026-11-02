package jo.codeide.lsp.java

import jo.codeide.lsp.java.actionscode.ModCommande
import jo.codeide.lsp.java.diagnostics.Diagnostic
import jo.codeide.lsp.java.diagnostics.Plage
import jo.codeide.lsp.java.diagnostics.Severite
import jo.codeide.lsp.java.outils.EditionTexte
import jo.codeide.lsp.java.outils.appliquerEditions
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * COMPLÉMENT DE CATALOGUE — les RESTES DES FAMILLES UNITAIRES (ADR 0032,
 * reste consigné du PV V-D3 : « ajout/réordonnancement de paramètres,
 * rename de ressource DEPUIS un document XML » + le solde V-G (e) :
 * codeDescription.href) :
 *
 *  - **AJOUT de paramètres** : le quickfix ID-FIRST sur l'appel fautif
 *    (ParameterMismatch 67108979 / UndefinedConstructor 134217858 — IDs
 *    MESURÉS) étend la signature des types RÉSOLUS des arguments
 *    excédentaires, chaque autre appelant recevant la valeur PAR DÉFAUT ;
 *  - **RÉORDONNANCEMENT** : le paramètre au caret s'échange avec son
 *    voisin (signature + arguments de chaque appelant — la liaison
 *    expression↔paramètre est préservée) ;
 *  - **codeDescription.href** : le lien de documentation officiel des
 *    contrôles LINT (page vérifiée — aucune ancre par ID n'existe).
 *
 * Critères (esprit P5) : chaque WorkspaceEdit doré appliqué puis
 * RE-DIAGNOSTIQUÉ fichier par fichier sur un témoin NEUF — 0 ERREUR ;
 * refus honnêtes motivés (oracle) ; actions DIFFÉRÉES (clé + positions).
 *
 * Marqueurs : `§` le caret simple.
 */
class ComplementCatalogueTest {

    /** Le moteur sur une racine temporaire : Main.java + fichiers annexes. */
    private fun moteurAvec(source: String, vararg autres: Pair<String, String>): Pair<MoteurJava, String> {
        val racine = Files.createTempDirectory("cat-complement")
        Files.createDirectories(racine.resolve("demo"))
        val chemin = "demo/Main.java"
        racine.resolve("demo").resolve("Main.java").writeText(source)
        for ((nom, texte) in autres) {
            racine.resolve("demo").resolve(nom).writeText(texte)
        }
        return MoteurJava(racines = listOf(racine)) to chemin
    }

    /** Les sources par fichier RELATIF (demo/X.java) d'une racine. */
    private fun sourcesDe(vararg fichiers: Pair<String, String>): Map<String, String> = fichiers.toMap()

    /** Applique une map d'éditions MULTI-FICHIERS (clé : chemin disque). */
    private fun appliquerProjet(sources: Map<String, String>, editions: Map<String, List<EditionTexte>>): Map<String, String> {
        val resultat = HashMap(sources)
        for ((fichier, liste) in editions) {
            val relatif = fichier.substringAfter("/demo/")
            val ancien = resultat[relatif] ?: continue
            resultat[relatif] = appliquerEditions(ancien, liste)
        }
        return resultat
    }

    /** Les ERREURS de CHAQUE fichier, re-diagnostiqués sur un témoin NEUF (esprit P5). */
    private fun erreursDe(sources: Map<String, String>): Map<String, Int> {
        val racine = Files.createTempDirectory("cat-verifie")
        Files.createDirectories(racine.resolve("demo"))
        for ((nom, texte) in sources) {
            racine.resolve("demo").resolve(nom).writeText(texte)
        }
        val temoin = MoteurJava(racines = listOf(racine))
        return sources.mapValues { (nom, texte) ->
            temoin.diagnose("demo/$nom", texte).count { it.severite == Severite.ERREUR }
        }
    }

    /** Les arguments sérialisés d'une commande différée. */
    private fun argumentsDe(action: jo.codeide.lsp.java.actionscode.ActionCode): List<String> =
        (action.commande as? ModCommande.Differee)?.arguments ?: emptyList()

    /** Le quickfix d'ajout de paramètres servi sur les diagnostics du document. */
    private fun actionAjout(moteur: MoteurJava, chemin: String, source: String): jo.codeide.lsp.java.actionscode.ActionCode? {
        val diagnostics = moteur.diagnose(chemin, source)
        return moteur.actionsCode(chemin, source, diagnostics)
            .firstOrNull { (it.commande as? ModCommande.Differee)?.cle == jo.codeide.lsp.java.actionscode.CorrecteurAjoutParametres.CLE_AJOUTER_PARAMETRES }
    }

    // ------------------------------------------------------------------ //
    // Ajout de paramètres — la MÉTHODE
    // ------------------------------------------------------------------ //

    @Test
    fun `ajouter des parametres dore la signature et sert les defauts aux appelants (multi-fichiers)`() {
        val source = """
            package demo;
            public class Main {
                int calcul(int a) { return a * 2; }
                int appel() { return calcul(3, "x", 4L); }
            }
        """.trimIndent()
        val client = """
            package demo;
            public class Client {
                int appelDistant(Main m) { return m.calcul(4); }
            }
        """.trimIndent()
        val (moteur, chemin) = moteurAvec(source, "Client.java" to client)
        val action = actionAjout(moteur, chemin, source)
        assertNotNull(action, "le quickfix d'ajout est servi sur l'appel fautif")
        assertEquals("Ajouter les 2 paramètres manquants à « calcul »", action!!.titre)

        val verdict = moteur.ajoutParametresSure(chemin, source, argumentsDe(action).first().toInt())
        assertNotNull(verdict, "l'oracle sert le verdict")
        assertNull(verdict!!.motifRefus, "aucun refus : ${verdict.motifRefus}")
        assertEquals(2, verdict.ajoutes, "deux paramètres ajoutés (String, long)")
        assertEquals(1, verdict.appelants, "l'appelant distant de l'ancienne arité")

        val applique = appliquerProjet(sourcesDe("Main.java" to source, "Client.java" to client), verdict.editions!!)
        assertEquals(
            "int calcul(int a, String texte, long nombre) { return a * 2; }",
            Regex("int calcul\\([^)]*\\) \\{ return a \\* 2; \\}").find(applique["Main.java"]!!)!!.value,
            "la signature est étendue des types résolus",
        )
        assertTrue(applique["Main.java"]!!.contains("calcul(3, \"x\", 4L)"), "le site fautif ne se retouche pas (il porte déjà les futurs paramètres)")
        assertTrue(applique["Client.java"]!!.contains("m.calcul(4, null, 0)"), "l'appelant reçoit les valeurs par défaut")

        val erreurs = erreursDe(applique)
        assertTrue(erreurs.values.all { it == 0 }, "re-diagnose 0 erreur partout : $erreurs")
    }

    @Test
    fun `ajouter un parametre raccourcit le type et pose l import dans le fichier declarant`() {
        val source = """
            package demo;
            public class Main {
                int total(int a) { return a; }
                int appel() { return total(3, new java.math.BigDecimal("1")); }
            }
        """.trimIndent()
        val (moteur, chemin) = moteurAvec(source)
        val action = actionAjout(moteur, chemin, source)
        assertNotNull(action, "le quickfix est servi (1 paramètre manquant)")
        assertEquals("Ajouter le paramètre manquant à « total »", action!!.titre)

        val verdict = moteur.ajoutParametresSure(chemin, source, argumentsDe(action).first().toInt())
        assertNull(verdict!!.motifRefus)
        val mainApplique = appliquerEditions(source, verdict.editions!![verdict.editions.keys.first()]!!)
        assertTrue(mainApplique.contains("import java.math.BigDecimal;"), "l'import du type raccourci est posé dans le fichier déclarant")
        assertTrue(mainApplique.contains("int total(int a, BigDecimal bigDecimal)"), "le FQN est raccourci vers le nom simple : ${Regex("int total\\([^)]*\\)").find(mainApplique)!!.value}")
        assertTrue(mainApplique.contains("total(3, new java.math.BigDecimal(\"1\"))"), "le site fautif ne se retouche pas")

        val erreurs = erreursDe(mapOf("Main.java" to mainApplique))
        assertEquals(0, erreurs["Main.java"], "re-diagnose 0 erreur")
    }

    @Test
    fun `l action n est pas servie sans argument excedentaire ni sur un null literral`() {
        val source = """
            package demo;
            public class Main {
                int calcul(int a) { return a * 2; }
                int mismatch() { return calcul("x"); }
                int nul() { return calcul(3, null); }
            }
        """.trimIndent()
        val (moteur, chemin) = moteurAvec(source)
        // mismatch pur (aucun argument excédentaire) et null littéral (type
        // inconnu) : la liste ne sert RIEN.
        assertNull(actionAjout(moteur, chemin, source), "aucune action d'ajout sur ces profils")
        // L'oracle confirme : le premier site fautif est un mismatch pur.
        val diagnostics = moteur.diagnose(chemin, source).filter { it.code == "67108979" }
        assertTrue(diagnostics.isNotEmpty(), "les deux appels fautifs émettent ParameterMismatch")
        val verdict = moteur.ajoutParametresSure(chemin, source, diagnostics.first().plage.debutOffset)
        assertNotNull(verdict)
        assertEquals("mismatch", verdict!!.motifRefus, "pur mismatch de types : pas un ajout")
    }

    // ------------------------------------------------------------------ //
    // Ajout de paramètres — le CONSTRUCTEUR
    // ------------------------------------------------------------------ //

    @Test
    fun `etendre le constructeur dore la signature et sert les defauts aux autres new`() {
        val source = """
            package demo;
            public class Main {
                private int a;
                public Main(int a) { this.a = a; }
                void ctor() { new Main(1, "y"); }
            }
        """.trimIndent()
        val client = """
            package demo;
            public class Client {
                Client() { new Main(2); }
            }
        """.trimIndent()
        val (moteur, chemin) = moteurAvec(source, "Client.java" to client)
        val action = actionAjout(moteur, chemin, source)
        assertNotNull(action, "le quickfix d'extension est servi sur le new fautif")
        assertEquals("Étendre le constructeur « Main »", action!!.titre)

        val verdict = moteur.ajoutParametresSure(chemin, source, argumentsDe(action).first().toInt())
        assertNull(verdict!!.motifRefus, "aucun refus : ${verdict.motifRefus}")
        assertEquals(1, verdict.ajoutes)

        val applique = appliquerProjet(sourcesDe("Main.java" to source, "Client.java" to client), verdict.editions!!)
        assertTrue(applique["Main.java"]!!.contains("public Main(int a, String texte)"), "le constructeur est étendu : ${Regex("public Main\\([^)]*\\)").find(applique["Main.java"]!!)!!.value}")
        assertTrue(applique["Main.java"]!!.contains("new Main(1, \"y\")"), "le site fautif ne se retouche pas")
        assertTrue(applique["Client.java"]!!.contains("new Main(2, null)"), "l'autre site new reçoit la valeur par défaut")

        val erreurs = erreursDe(applique)
        assertTrue(erreurs.values.all { it == 0 }, "re-diagnose 0 erreur partout : $erreurs")
    }

    // ------------------------------------------------------------------ //
    // Ajout de paramètres — refus honnêtes (oracle)
    // ------------------------------------------------------------------ //

    @Test
    fun `les refus honnetes de l ajout portent leur motif (oracle)`() {
        // SURCHARGE : deux méthodes du même sélecteur — étendre le candidat
        // le plus proche serait le choix d'ecj, pas celui de l'utilisateur.
        val surcharge = """
            package demo;
            public class Main {
                int calcul(int a) { return a; }
                int calcul(String s) { return s.length(); }
                int appel() { return calcul(3, "x"); }
            }
        """.trimIndent()
        moteurAvec(surcharge).let { (moteur, chemin) ->
            val action = actionAjout(moteur, chemin, surcharge)
            assertNotNull(action, "la liste sert (gardes locales passées)")
            val verdict = moteur.ajoutParametresSure(chemin, surcharge, argumentsDe(action!!).first().toInt())
            assertEquals("surcharge", verdict!!.motifRefus)
        }

        // VARARGS : le rang d'un argument étalé ne se sait pas à l'appel.
        val varargs = """
            package demo;
            public class Main {
                int somme(int... b) { return b.length; }
                int appel() { return somme(1, "x"); }
            }
        """.trimIndent()
        moteurAvec(varargs).let { (moteur, chemin) ->
            val action = actionAjout(moteur, chemin, varargs)
            assertNotNull(action)
            val verdict = moteur.ajoutParametresSure(chemin, varargs, argumentsDe(action!!).first().toInt())
            assertEquals("varargs", verdict!!.motifRefus)
        }

        // HÉRARCHIE : la méthode outrepasse — la classe entière devrait suivre.
        val hierarchie = """
            package demo;
            public class Main {
                int appel(Sub s) { return s.calcul(3, "x"); }
            }
        """.trimIndent()
        val base = """
            package demo;
            public class Base {
                int calcul(int a) { return a; }
            }
        """.trimIndent()
        val sub = """
            package demo;
            public class Sub extends Base {
                @Override
                int calcul(int a) { return a * 2; }
            }
        """.trimIndent()
        moteurAvec(hierarchie, "Base.java" to base, "Sub.java" to sub).let { (moteur, chemin) ->
            val action = actionAjout(moteur, chemin, hierarchie)
            assertNotNull(action)
            val verdict = moteur.ajoutParametresSure(chemin, hierarchie, argumentsDe(action!!).first().toInt())
            assertEquals("hierarchie", verdict!!.motifRefus)
        }

        // BINAIRE : la méthode visée vit dans le JDK — rien à étendre.
        val binaire = """
            package demo;
            public class Main {
                int appel() { return Math.max(1, 2, 3); }
            }
        """.trimIndent()
        moteurAvec(binaire).let { (moteur, chemin) ->
            val action = actionAjout(moteur, chemin, binaire)
            assertNotNull(action, "la liste sert (arithmétique des arguments seule)")
            val verdict = moteur.ajoutParametresSure(chemin, binaire, argumentsDe(action!!).first().toInt())
            assertEquals("binaire", verdict!!.motifRefus)
        }

        // SANS CONSTRUCTEUR : le constructeur par défaut est synthétique.
        val sansCtor = """
            package demo;
            public class Main {
                int a;
                void ctor() { new Main(1, "y"); }
            }
        """.trimIndent()
        moteurAvec(sansCtor).let { (moteur, chemin) ->
            val action = actionAjout(moteur, chemin, sansCtor)
            assertNotNull(action)
            val verdict = moteur.ajoutParametresSure(chemin, sansCtor, argumentsDe(action!!).first().toInt())
            assertEquals("sansConstructeur", verdict!!.motifRefus)
        }
    }

    // ------------------------------------------------------------------ //
    // Réordonnancement
    // ------------------------------------------------------------------ //

    @Test
    fun `deplacer un parametre echange les fragments et les arguments des appelants (multi-fichiers)`() {
        val source = """
            package demo;
            public class Main {
                String formater(int nombre, String §texte) { return texte + nombre; }
                String appel() { return formater(3, "x"); }
            }
        """.trimIndent()
        val client = """
            package demo;
            public class Client {
                String appelDistant(Main m) { return m.formater(4, "y"); }
            }
        """.trimIndent()
        val propre = source.replace("§", "")
        val position = source.indexOf("§")
        val (moteur, chemin) = moteurAvec(propre, "Client.java" to client)

        // La liste sert GAUCHE seulement (le paramètre est TERMINAL — les
        // paramètres SONT utilisés : ils se déplacent avec leurs lectures).
        val actions = moteur.intentions(chemin, propre, position, position)
            .filter { it.kind == "refactor.rewrite.signature" }
        val gauche = actions.firstOrNull { it.titre.contains("vers la gauche") }
        val droite = actions.firstOrNull { it.titre.contains("vers la droite") }
        assertNotNull(gauche, "le déplacement vers la gauche est servi")
        assertNull(droite, "le paramètre terminal ne se déplace pas vers la droite")
        assertEquals("Déplacer le paramètre « texte » vers la gauche", gauche!!.titre)

        val verdict = moteur.deplacementParametreSure(chemin, propre, position, -1)
        assertNotNull(verdict)
        assertNull(verdict!!.motifRefus, "aucun refus : ${verdict.motifRefus}")

        val applique = appliquerProjet(sourcesDe("Main.java" to propre, "Client.java" to client), verdict.editions!!)
        assertEquals(
            "String formater(String texte, int nombre) { return texte + nombre; }",
            Regex("String formater\\([^)]*\\) \\{ return texte \\+ nombre; \\}").find(applique["Main.java"]!!)!!.value,
            "les deux fragments s'échangent (virgule et blancs intermédiaires intacts)",
        )
        assertTrue(applique["Main.java"]!!.contains("formater(\"x\", 3)"), "l'appel local échange ses arguments")
        assertTrue(applique["Client.java"]!!.contains("m.formater(\"y\", 4)"), "l'appelant distant échange ses arguments")

        val erreurs = erreursDe(applique)
        assertTrue(erreurs.values.all { it == 0 }, "la liaison expression↔paramètre est préservée (0 erreur) : $erreurs")
    }

    @Test
    fun `deplacer vers la droite le premier parametre (dore et re-diagnostique)`() {
        val source = """
            package demo;
            public class Main {
                String formater(int §nombre, String texte) { return texte + nombre; }
                String appel() { return formater(3, "x"); }
            }
        """.trimIndent()
        val propre = source.replace("§", "")
        val position = source.indexOf("§")
        val (moteur, chemin) = moteurAvec(propre)

        val actions = moteur.intentions(chemin, propre, position, position).filter { it.kind == "refactor.rewrite.signature" }
        assertNull(actions.firstOrNull { it.titre.contains("vers la gauche") }, "le premier paramètre n'a pas de voisin gauche")
        assertNotNull(actions.firstOrNull { it.titre.contains("vers la droite") }, "le déplacement vers la droite est servi")

        val verdict = moteur.deplacementParametreSure(chemin, propre, position, +1)
        assertNull(verdict!!.motifRefus)
        val applique = appliquerProjet(sourcesDe("Main.java" to propre), verdict.editions!!)
        assertEquals("String formater(String texte, int nombre) { return texte + nombre; }", Regex("String formater\\([^)]*\\) \\{ return texte \\+ nombre; \\}").find(applique["Main.java"]!!)!!.value)
        assertTrue(applique["Main.java"]!!.contains("formater(\"x\", 3)"), "l'appel échange ses arguments")
        assertEquals(0, erreursDe(applique)["Main.java"])
    }

    @Test
    fun `les refus honnetes du deplacement portent leur motif (oracle)`() {
        // VARARGS : l'échange toucherait le paramètre étalé (toujours dernier).
        val varargs = """
            package demo;
            public class Main {
                int somme(int §a, int... b) { return a + b.length; }
            }
        """.trimIndent()
        val propreVarargs = varargs.replace("§", "")
        moteurAvec(propreVarargs).let { (moteur, chemin) ->
            val actions = moteur.intentions(chemin, propreVarargs, varargs.indexOf("§"), varargs.indexOf("§"))
                .filter { it.kind == "refactor.rewrite.signature" }
            assertTrue(actions.none { it.titre.contains("Déplacer") }, "aucun déplacement servi à la frontière varargs : ${actions.map { it.titre }}")
            val verdict = moteur.deplacementParametreSure(chemin, propreVarargs, varargs.indexOf("§"), +1)
            assertEquals("varargs", verdict!!.motifRefus)
        }

        // SURCHARGE : l'échange d'arguments aux sites d'appel pourrait
        // déplacer la résolution vers l'autre surcharge.
        val surcharge = """
            package demo;
            public class Main {
                void m(int a, String §b) { }
                void m(String a, int b) { }
                void appel() { m(1, "x"); }
            }
        """.trimIndent()
        val propreSurcharge = surcharge.replace("§", "")
        moteurAvec(propreSurcharge).let { (moteur, chemin) ->
            val position = surcharge.indexOf("§")
            val actions = moteur.intentions(chemin, propreSurcharge, position, position).filter { it.kind == "refactor.rewrite.signature" }
            assertNotNull(actions.firstOrNull { it.titre.contains("Déplacer") }, "la liste sert (gardes locales passées)")
            val verdict = moteur.deplacementParametreSure(chemin, propreSurcharge, position, -1)
            assertEquals("surcharge", verdict!!.motifRefus)
        }

        // RÉFÉRENCE DE MÉTHODE : `this::m` fixe la forme.
        val avecRef = """
            package demo;
            public class Main {
                String m(int §a, String b) { return b + a; }
                java.util.function.BiFunction<Integer, String, String> ref = this::m;
            }
        """.trimIndent()
        moteurAvec(avecRef.replace("§", "")).let { (moteur, chemin) ->
            val verdict = moteur.deplacementParametreSure(chemin, avecRef.replace("§", ""), avecRef.indexOf("§"), +1)
            assertEquals("referenceMethode", verdict!!.motifRefus, "un X::m visant la méthode fixe sa forme")
        }
    }

    // ------------------------------------------------------------------ //
    // codeDescription.href (G-023 — solde V-G (e))
    // ------------------------------------------------------------------ //

    @Test
    fun `le lien de documentation est servi pour la provenance lint seulement (verifie, sans ancre par id)`() {
        val repere = jo.codeide.lsp.java.protocole.ConvertisseurLsp.repere("int x = 0;")
        val lint = Diagnostic(Severite.AVERTISSEMENT, "Recycle", Plage(0, 3), code = "Recycle", source = "lint")
        val ecj = Diagnostic(Severite.ERREUR, "erreur", Plage(0, 3), code = "16777218")
        val inspection = Diagnostic(Severite.AVERTISSEMENT, "cast inutile", Plage(0, 3), source = "inspection")

        val hrefLint = jo.codeide.lsp.java.protocole.ConvertisseurLsp.diagnostic(lint, repere).lsp.codeDescription
        assertNotNull(hrefLint, "un contrôle lint porte son lien de documentation")
        assertEquals("https://developer.android.com/studio/write/lint", hrefLint!!.href, "la page officielle des contrôles lint (vérifiée : aucune ancre par ID)")

        assertNull(jo.codeide.lsp.java.protocole.ConvertisseurLsp.diagnostic(ecj, repere).lsp.codeDescription, "un diagnostic ecj n'a pas de href (aucune page officielle stable par erreur)")
        assertNull(jo.codeide.lsp.java.protocole.ConvertisseurLsp.diagnostic(inspection, repere).lsp.codeDescription, "une inspection n'a pas de href (documenté)")
    }
}
