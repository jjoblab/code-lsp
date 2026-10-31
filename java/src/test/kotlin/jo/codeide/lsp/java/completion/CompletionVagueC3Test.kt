package jo.codeide.lsp.java.completion

import jo.codeide.lsp.java.MoteurJava
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * BANC V-C3 (ADR 0026 — G-012/G-017/G-018) : complétion SMART (type attendu
 * par bindings), TYPEDEF `@IntDef`/`@StringDef`, LITTÉRAUX `R.*`, SECOND
 * appel (membres inaccessibles + imports statiques + projet), CHAÎNE,
 * STATEMENT completion, STATISTIQUES locales opt-in à fenêtre glissante et
 * MÉMOÏSATION des membres binaires.
 *
 * Cas dorés dérivés des fiches de comportement C-03 (S1–S5), C-04 (T1–T3),
 * C-08 (D1–D4) — la vérité terrain qualitative de l'ADR 0030 ; corpus jrt
 * seul (déterministe au bit près), la fixture Android des littéraux `R.*`
 * est le projet mini de V-B.
 */
class CompletionVagueC3Test {

    private companion object {
        val dossier: Path = Files.createTempDirectory("vc3")

        val SOURCE_INT_DEF = """
            package demo.autre;

            public @interface IntDef {
                int[] value();
            }
        """.trimIndent()

        val SOURCE_STRING_DEF = """
            package demo.autre;

            public @interface StringDef {
                String[] value();
            }
        """.trimIndent()

        val SOURCE_AUTRE = """
            package demo.autre;

            public class Autre {
                public static final int MODE_ON = 1;
                public static final int MODE_OFF = 2;
                public static final int OTHER = 3;
                public static final String KEY_X = "x";
                private int secret = 7;
                public int publicQui = 1;

                public void setMode(@IntDef({MODE_ON, MODE_OFF}) int mode) {
                }
            }
        """.trimIndent()

        val SOURCE_WIDGET2 = """
            package demo.outil;

            public class Widget2 {
                public Widget2 self() {
                    return this;
                }

                public int count() {
                    return 1;
                }

                public Builder builder() {
                    return new Builder();
                }

                public static class Builder {
                    public String text() {
                        return "x";
                    }

                    public int numero() {
                        return 1;
                    }
                }
            }
        """.trimIndent()

        val SOURCE_MAIN = """
            package demo;

            import demo.autre.Autre;
            import demo.outil.Widget2;

            public class Main {
                public static final String KEY_A = "a";
                public static final String KEY_B = "b";
                @demo.autre.StringDef({KEY_A, KEY_B})
                private String key = "k";
                private int compteur = 0;

                public void setKey(String k) {
                    this.key = k;
                }

                public CharSequence construire() {
                    return "x";
                }

                public int melanger(String a, Integer b, int c) {
                    return 0;
                }

                public int melanger(CharSequence x) {
                    return 0;
                }

                public int session() {
                    String nom = "x";
                    int age = 1;
                    Object o = null;
                    String s = ¦
                    return 0;
                }
            }
        """.trimIndent()
    }

    /** Le moteur du banc : sources réelles sur disque (comme une vraie frappe). */
    private fun moteur(): MoteurJava {
        val racine = dossier.resolve("src")
        Files.createDirectories(racine.resolve("demo/autre"))
        Files.createDirectories(racine.resolve("demo/outil"))
        Files.writeString(racine.resolve("demo/Main.java"), SOURCE_MAIN.replace("¦", ""))
        Files.writeString(racine.resolve("demo/autre/IntDef.java"), SOURCE_INT_DEF)
        Files.writeString(racine.resolve("demo/autre/StringDef.java"), SOURCE_STRING_DEF)
        Files.writeString(racine.resolve("demo/autre/Autre.java"), SOURCE_AUTRE)
        Files.writeString(racine.resolve("demo/outil/Widget2.java"), SOURCE_WIDGET2)
        return MoteurJava(racines = listOf(racine))
    }

    /** Complète `SOURCE_MAIN` avec une frappe injectée à la position de session(). */
    private fun completer(moteur: MoteurJava, frappe: String): ReponseCompletion {
        val source = SOURCE_MAIN.replace("String s = ¦", frappe)
        val marque = source.indexOf('¦')
        val texte = source.replace("¦", "")
        return moteur.completer("/demo/Main.java", texte, marque)
    }

    // ------------------------------------------------------------------ //
    // Fiche C-03 — smart : filtrage GRADUÉ par type attendu (jamais caché)
    // ------------------------------------------------------------------ //

    @Test
    fun `s1 affectation place la locale compatible en tete`() {
        val moteur = moteur()
        val reponse = completer(moteur, "String cible = ¦;")
        val labels = reponse.items.map { it.libelle }
        assertEquals(GenreContexte.NOM, reponse.genre)
        assertTrue(labels.isNotEmpty(), "la complétion rend une liste")
        // S1 : `nom` (String) en TÊTE — devant les types (inconnus) et les
        // incompatibles (le plafond honnête de 120 coupe la QUEUE, le total
        // exact voyage : jamais de cachage SILENCIEUX, S5).
        assertEquals("nom", labels.firstOrNull(), "nom (String compatible) en tête — trouvé : ${labels.take(8)}")
        assertEquals(0, reponse.items.first { it.libelle == "nom" }.compatibilite)
        assertTrue(reponse.totalDisponible > reponse.items.size, "le total réel est annoncé (${reponse.totalDisponible})")
        // le groupe compatible (les String du fichier) domine les types java.lang
        assertTrue(labels.indexOf("nom") < labels.indexOf("Byte"), "nom avant les types inconnus")
        assertTrue(labels.indexOf("key") < labels.indexOf("Byte"), "key (champ String) avant les types inconnus")
    }

    @Test
    fun `s1 borne les membres de object retournant string dominent`() {
        val moteur = moteur()
        val reponse = completer(moteur, "String cible = o.¦;")
        val labels = reponse.items.map { it.libelle }
        assertEquals(GenreContexte.MEMBRE, reponse.genre)
        assertEquals("toString", labels.firstOrNull(), "toString (retour String) en tête — trouvé : ${labels.take(6)}")
        // S5 : les membres incompatibles restent visibles (en bas)
        assertContains(labels, "hashCode")
    }

    @Test
    fun `s2 argument place la locale du type du parametre en tete`() {
        val moteur = moteur()
        val reponse = completer(moteur, "melanger(no¦, null, 0)")
        val labels = reponse.items.map { it.libelle }
        assertTrue(labels.indexOf("nom") >= 0, "nom proposé — trouvé : ${labels.take(8)}")
        // l'union des surcharges (String, CharSequence) : `nom` (String) compatible
        assertTrue(
            reponse.items.first { it.libelle == "nom" }.compatibilite == 0,
            "nom est type-compatible à l'union des surcharges",
        )
    }

    @Test
    fun `s2 union des surcharges accepte chaque type compatible`() {
        val moteur = moteur()
        // melanger(String, Integer, int) et melanger(CharSequence) : à l'indice 0,
        // l'union est {String, CharSequence} — `nom` (String) convient aux DEUX.
        val reponse = completer(moteur, "melanger(no¦, null, 0)")
        val labels = reponse.items.map { it.libelle }
        val nom = reponse.items.first { it.libelle == "nom" }
        assertEquals(0, nom.compatibilite, "nom compatible à l'UNION des surcharges")
        // S5 : les candidats inconnus (types Node*) restent visibles, DERRIÈRE
        assertTrue(labels.indexOf("nom") < labels.indexOf("Node"), "nom avant les types inconnus — trouvé : ${labels.take(8)}")
    }

    @Test
    fun `s3 return place l expression du type de retour en tete`() {
        val moteur = moteur()
        // la frappe vit dans session() (rend int) : c'est la locale INT qui
        // est type-compatible — le sous-typage S3 est couvert par la locale
        // `nom` dans le banc (affectation CharSequence ci-dessus).
        val reponse = completer(moteur, "return ¦;")
        val labels = reponse.items.map { it.libelle }
        assertEquals("age", labels.firstOrNull(), "age (int, type du retour) en tête — trouvé : ${labels.take(8)}")
        assertEquals(0, reponse.items.first { it.libelle == "age" }.compatibilite)
        assertTrue(labels.indexOf("age") < labels.indexOf("Byte"), "age avant les types inconnus")
    }

    @Test
    fun `s4 ternaire herite l attente de l affectation englobante`() {
        val moteur = moteur()
        val reponse = completer(moteur, "CharSequence cible = age > 0 ? ¦ : \"x\";")
        val labels = reponse.items.map { it.libelle }
        assertEquals("nom", labels.firstOrNull(), "nom en tête (ternaire transparent — l'attente est celle de l'affectation) — trouvé : ${labels.take(8)}")
        assertEquals(0, reponse.items.first { it.libelle == "nom" }.compatibilite)
    }

    @Test
    fun `s4 lambda attend le retour de la methode fonctionnelle`() {
        val moteur = moteur()
        val reponse = completer(
            moteur,
            "java.util.function.Supplier<String> sup = () -> ¦;",
        )
        val labels = reponse.items.map { it.libelle }
        assertEquals("nom", labels.firstOrNull(), "nom en tête (Supplier<String>.get rend String) — trouvé : ${labels.take(8)}")
        assertEquals(0, reponse.items.first { it.libelle == "nom" }.compatibilite)
    }

    // ------------------------------------------------------------------ //
    // Fiche C-04 — typedef @IntDef / @StringDef
    // ------------------------------------------------------------------ //

    @Test
    fun `t1 argument typedef propose les constantes autorisees en tete`() {
        val moteur = moteur()
        val reponse = completer(moteur, "Autre autre = new Autre(); autre.setMode(MODE_O¦);")
        val labels = reponse.items.map { it.libelle }
        assertTrue(
            labels.indexOf("MODE_ON") >= 0 && labels.indexOf("MODE_OFF") >= 0,
            "les constantes du typedef sont proposées — trouvé : ${labels.take(8)}",
        )
        val premiere = reponse.items.first { it.libelle == "MODE_ON" }
        assertEquals("typedef", premiere.marqueur, "la constante porte le marqueur typedef")
        assertTrue(
            premiere.editionsAdditionnelles.any { it.texte.contains("import static demo.autre.Autre.MODE_ON;") },
            "l'import statique voyage en édition additionnelle (T1 : proposée même non importée)",
        )
    }

    @Test
    fun `t2 champ typedef propose les constantes en tete`() {
        val moteur = moteur()
        val reponse = completer(moteur, "key = KEY_¦;")
        val labels = reponse.items.map { it.libelle }
        assertTrue(
            labels.indexOf("KEY_A") >= 0 && labels.indexOf("KEY_B") >= 0,
            "les constantes du @StringDef du champ sont proposées — trouvé : ${labels.take(8)}",
        )
        val premiere = reponse.items.first { it.libelle == "KEY_A" }
        assertEquals("typedef", premiere.marqueur)
        // champ du FICHIER COURANT : aucun import nécessaire
        assertTrue(premiere.editionsAdditionnelles.isEmpty(), "même classe : pas d'import statique")
    }

    @Test
    fun `t3 label de switch sur typedef propose les constantes`() {
        val moteur = moteur()
        val source = SOURCE_MAIN.replace(
            "String s = ¦",
            """
            @demo.autre.IntDef({Autre.MODE_ON, Autre.MODE_OFF}) int mode = Autre.MODE_ON;
            switch (mode) {
                case MO¦
            }
            """.trimIndent(),
        )
        val marque = source.indexOf('¦')
        val texte = source.replace("¦", "")
        val reponse = moteur.completer("/demo/Main.java", texte, marque)
        val labels = reponse.items.map { it.libelle }
        assertEquals(GenreContexte.CASE_LABEL, reponse.genre)
        assertTrue(
            labels.containsAll(listOf("MODE_ON", "MODE_OFF")),
            "les constantes du typedef du sélecteur sont proposées — trouvé : ${labels}",
        )
    }

    // ------------------------------------------------------------------ //
    // Fiche C-08 — second appel : inaccessibles, statiques importables, projet
    // ------------------------------------------------------------------ //

    @Test
    fun `d1 second appel ajoute les membres inaccessibles marques et derriere`() {
        val moteur = moteur()
        val source = SOURCE_MAIN.replace("String s = ¦", "Autre autre = new Autre(); autre.¦;")
        val marque = source.indexOf('¦')
        val texte = source.replace("¦", "")
        // PREMIER appel : les accessibles seulement (secret est privé à Autre)
        val premier = moteur.completer("/demo/Main.java", texte, marque)
        val labelsPremier = premier.items.map { it.libelle }
        assertEquals(GenreContexte.MEMBRE, premier.genre)
        assertFalse(labelsPremier.contains("secret"), "le membre privé est absent du PREMIER appel — trouvé : ${labelsPremier.take(10)}")
        assertTrue(labelsPremier.contains("publicQui"), "un membre public est proposé — trouvé : ${labelsPremier.take(10)}")
        // SECOND appel à la MÊME position : l'inaccessible s'ajoute, MARQUÉ, DERRIÈRE
        val second = moteur.completer("/demo/Main.java", texte, marque)
        val labelsSecond = second.items.map { it.libelle }
        assertContains(labelsSecond, "secret", "le membre privé apparaît au SECOND appel (marqué)")
        val itemSecret = second.items.first { it.libelle == "secret" }
        assertEquals("inaccessible", itemSecret.marqueur, "l'inaccessible porte la marque (D1)")
        assertTrue(itemSecret.degrade, "l'inaccessible est dégradé (derrière les accessibles)")
        assertTrue(
            labelsSecond.indexOf("secret") > labelsSecond.indexOf("publicQui"),
            "l'inaccessible passe DERRIÈRE les accessibles",
        )
    }

    @Test
    fun `d2 second appel propose les imports statiques avec edition`() {
        val moteur = moteur()
        // `asse|` en expression : Math.asin est un membre STATIQUE importable
        val premier = completer(moteur, "double d = asi¦;")
        assertFalse(
            premier.items.any { it.libelle == "asin" },
            "PREMIER appel : pas d'import statique — trouvé : ${premier.items.map { it.libelle }.take(8)}",
        )
        val second = completer(moteur, "double d = asi¦;")
        val asin = second.items.firstOrNull { it.libelle == "asin" }
        assertNotNull(asin, "SECOND appel : Math.asin proposé — trouvé : ${second.items.map { it.libelle }.take(12)}")
        assertEquals("java.lang.Math.asin", asin.importStatiqueFqn)
        assertTrue(
            asin.editionsAdditionnelles.any { it.texte == "import static java.lang.Math.asin;\n" },
            "l'édition d'import statique voyage (insertion compilable, P6)",
        )
    }

    @Test
    fun `d3 troisieme appel elargit aux types du projet par l index`() {
        val index = IndexFactice()
        val racine = dossier.resolve("src")
        val moteur = MoteurJava(
            racines = listOf(racine),
            jars = emptyList(),
            jdkHome = null,
            compliance = "17",
            niveauDemande = null,
            indexNoms = index,
            projetAndroid = null,
        )
        val premier = completer(moteur, "Object z = Zez¦;")
        assertFalse(premier.items.any { it.libelle == "Zezture" }, "PREMIER appel : le type hors classpath n'est pas proposé")
        completer(moteur, "Object z = Zez¦;") // 2e
        val troisieme = completer(moteur, "Object z = Zez¦;")
        val zezture = troisieme.items.firstOrNull { it.libelle == "Zezture" }
        assertNotNull(zezture, "TROISIÈME appel : le type du PROJET (index) est proposé — trouvé : ${troisieme.items.map { it.libelle }.take(10)}")
        assertEquals("demo.ailleurs.Zezture", zezture.autoImportFqn)
        assertTrue(
            zezture.editionsAdditionnelles.any { it.texte == "import demo.ailleurs.Zezture;\n" },
            "l'import automatique voyage (D3)",
        )
    }

    @Test
    fun `d4 le changement de prefixe rouvre le contexte`() {
        val moteur = moteur()
        completer(moteur, "double d = asi¦;") // 1re
        val second = completer(moteur, "double d = asi¦;") // 2e : asin présent
        assertTrue(second.items.any { it.libelle == "asin" }, "2e appel à la même ancre : import statique présent")
        val autrePrefixe = completer(moteur, "double d = as¦") // préfixe changé : 1re
        assertFalse(
            autrePrefixe.items.any { it.libelle == "asin" },
            "le compteur RETOMBE à 1 au changement de préfixe (nouveau contexte, D4)",
        )
        val secondBis = completer(moteur, "double d = as¦") // 2e à la nouvelle ancre
        assertTrue(secondBis.items.any { it.libelle == "asin" }, "2e appel à la nouvelle ancre : import statique présent")
    }

    // ------------------------------------------------------------------ //
    // ADR 0026 (b) — chaîne : second saut retournant le type attendu
    // ------------------------------------------------------------------ //

    @Test
    fun `chaine propose les methodes retournant le type attendu en deux sauts`() {
        val moteur = moteur()
        val reponse = completer(moteur, "String r = new Widget2().b¦;")
        val labels = reponse.items.map { it.libelle }
        assertEquals(
            "builder().text()",
            labels.firstOrNull(),
            "la chaîne builder().text() (retour String en deux sauts) domine — trouvé : ${labels.take(6)}",
        )
        // le membre direct `builder` (retour Builder, incompatible) reste présent (S5)
        assertContains(labels, "builder")
    }

    // ------------------------------------------------------------------ //
    // Décision D7 (M1/G-024) — mots-clés de tête de déclaration, fusion stubs
    // ------------------------------------------------------------------ //

    @Test
    fun `d7 les mots cles de tete de declaration sont proposes pendant leur frappe`() {
        val moteur = moteur()
        // la frappe vit en TÊTE d'une déclaration TOP LEVEL fraîche (le cas
        // M1/G-024 — une tête LOCALE dans un corps de méthode est un artefact
        // de recovery d'ecj : borne documentée USAGE §7.21)
        val complet = SOURCE_MAIN.replace("String s = ¦", "int fin = 0;").replace("¦", "") +
            "\nclass Tete e¦\n"
        val marque = complet.indexOf('¦')
        val texte = complet.replace("¦", "")
        val reponse = moteur.completer("/demo/Main.java", texte, marque)
        val labels = reponse.items.map { it.libelle }
        assertContains(labels, "extends", "le mot-clé extends est proposé pendant sa frappe (D7/M1)")
        // la FUSION : les stubs de la hiérarchie du type en cours restent là
        assertTrue(
            labels.any { it.startsWith("equals(") },
            "les stubs de la hiérarchie accompagnent les mots-clés (fusion D7) — trouvé : ${labels.take(10)}",
        )
        // la frappe « im| » propose implements (même position, autre préfixe)
        val completIm = SOURCE_MAIN.replace("String s = ¦", "int fin = 0;").replace("¦", "") +
            "\nclass Tete im¦\n"
        val reponseIm = moteur.completer(
            "/demo/Main.java",
            completIm.replace("¦", ""),
            completIm.indexOf('¦'),
        )
        assertContains(reponseIm.items.map { it.libelle }, "implements", "le mot-clé implements est proposé pendant sa frappe")
    }

    @Test
    fun `d7 la clause apres implements reste une position de type`() {
        val moteur = moteur()
        val complet = SOURCE_MAIN.replace("String s = ¦", "int fin = 0;").replace("¦", "") +
            "\nclass Tete implements Ru¦\n"
        val marque = complet.indexOf('¦')
        val texte = complet.replace("¦", "")
        val reponse = moteur.completer("/demo/Main.java", texte, marque)
        assertEquals(GenreContexte.TYPE, reponse.genre, "la clause après implements reste une position de TYPE")
        assertContains(reponse.items.map { it.libelle }, "Runnable")
    }

    // ------------------------------------------------------------------ //
    // ADR 0026 (c) — statement completion (geste Ctrl+Shift+Enter)
    // ------------------------------------------------------------------ //

    @Test
    fun `statement completion referme l appel et termine l instruction`() {
        val moteur = moteur()
        val edition = moteur.completerInstruction(
            "/demo/Main.java",
            "class A { void m() { repeter(\"monip\",  } }",
            "class A { void m() { repeter(\"monip\", ".length,
        )
        assertNotNull(edition, "une édition est rendue")
        assertEquals("0);", edition.texte, "virgule traînante : argument de secours + fermeture + `;`")
        assertEquals("class A { void m() { repeter(\"monip\", ".length, edition.offset)
        assertEquals(edition.offset + edition.texte.length, edition.caret)
    }

    @Test
    fun `statement completion referme le litteral ouvert`() {
        val moteur = moteur()
        val prefixe = "class A { void m() { String s = \"hel"
        val edition = moteur.completerInstruction("/demo/Main.java", "$prefixe } }", prefixe.length)
        assertNotNull(edition)
        assertEquals("\";", edition.texte, "littéral refermé puis instruction terminée")
    }

    @Test
    fun `statement completion ouvre le bloc d un mot cle de controle`() {
        val moteur = moteur()
        val prefixe = "class A { void m() { if (a > b"
        val edition = moteur.completerInstruction("/demo/Main.java", "$prefixe ) } }", prefixe.length)
        assertNotNull(edition)
        assertEquals(") {", edition.texte, "liste refermée puis bloc ouvert")
    }

    @Test
    fun `statement completion termine l instruction nue et se tait quand complete`() {
        val moteur = moteur()
        val nue = moteur.completerInstruction("/demo/Main.java", "class A { void m() { return", "class A { void m() { return".length)
        assertNotNull(nue, "une instruction nue est terminée")
        assertEquals(";", nue.texte)
        val complete = moteur.completerInstruction(
            "/demo/Main.java",
            "class A { void m() { int x = 1; } }",
            "class A { void m() { int x = 1;".length,
        )
        assertNull(complete, "une instruction déjà terminée ne rend RIEN (jamais d'édition vide)")
    }

    // ------------------------------------------------------------------ //
    // ADR 0026 (g) — statistiques locales opt-in, fenêtre glissante
    // ------------------------------------------------------------------ //

    @Test
    fun `compteur d usage ne compte que la fenetre glissante`() {
        val fichier = Files.createTempDirectory("stats").resolve("usages.txt")
        var maintenant = 1_000_000L
        val compteur = CompteurUsage(fichier, fenetreMs = 1_000, horloge = { maintenant })
        compteur.incrementer("length")
        compteur.incrementer("length")
        assertEquals(2, compteur.compter("length"), "les usages récents comptent")
        maintenant += 2_000
        assertEquals(0, compteur.compter("length"), "les usages hors fenêtre s'éteignent")
        compteur.incrementer("length")
        assertEquals(1, compteur.compter("length"), "le nouvel usage compte")
        // Persistance : un compteur neuf sur le même fichier voit l'usage récent
        val recharge = CompteurUsage(fichier, fenetreMs = 1_000, horloge = { maintenant })
        assertEquals(1, recharge.compter("length"), "l'état persiste (fichier local opt-in)")
        compteur.purger()
        assertEquals(0, compteur.compter("length"), "la purge vide tout")
    }

    // ------------------------------------------------------------------ //
    // Mémoïsation des membres binaires (reste consigné du PV 1.6.0)
    // ------------------------------------------------------------------ //

    @Test
    fun `memo membres binaires deux appels meme verdict`() {
        val moteur = moteur()
        val source = SOURCE_MAIN.replace("String s = ¦", "String v = \"a\"; int n = v.compare¦;")
        val marque = source.indexOf('¦')
        val texte = source.replace("¦", "")
        val premier = moteur.completer("/demo/Main.java", texte, marque)
        assertEquals(GenreContexte.MEMBRE, premier.genre)
        // Membres de java.lang.String (BINAIRE jrt) : le second appel passe par
        // le MÉMO — même verdict, mêmes libellés (l'ordre déterministe est gelé).
        val second = moteur.completer("/demo/Main.java", texte, marque)
        assertEquals(premier.items.map { it.libelle }, second.items.map { it.libelle })
        assertContains(premier.items.map { it.libelle }, "compareTo")
        // et l'épreuve d'accessibilité SURVIT au mémo (les niveaux y sont consignés)
        val sourceAutre = SOURCE_MAIN.replace("String s = ¦", "Autre a = new Autre(); a.pub¦;")
        val marqueAutre = sourceAutre.indexOf('¦')
        moteur.completer("/demo/Main.java", sourceAutre.replace("¦", ""), marqueAutre)
        val deuxieme = moteur.completer("/demo/Main.java", sourceAutre.replace("¦", ""), marqueAutre)
        assertContains(deuxieme.items.map { it.libelle }, "publicQui")
        assertFalse(deuxieme.items.map { it.libelle }.contains("secret"), "le privé reste absent du premier appel APRES mémoïsation d'un autre type")
    }

    // ------------------------------------------------------------------ //
    // Mémoïsation des membres binaires — TRANSCRIPT de latence (R5 : la
    // mesure voyage dans la sortie du test, aucune valeur fabriquée)
    // ------------------------------------------------------------------ //

    @Test
    fun `memo membres binaires transcript de latence`() {
        val moteur = moteur()
        val source = SOURCE_MAIN.replace("String s = ¦", "String v = \"a\"; int n = v.compare¦;")
        val marque = source.indexOf('¦')
        val texte = source.replace("¦", "")
        // chauffer l'ENVIRONNEMENT (autre préfixe, autre ancre) — le MÉMO des
        // membres de java.lang.String reste froid pour l'ancre mesure.
        moteur.completer("/demo/Main.java", texte.replace("v.compare", "v.length"), texte.replace("v.compare", "v.length").indexOf("v.length") + 8)
        val echantillons = mutableListOf<Long>()
        repeat(12) { tour ->
            val s = if (tour % 2 == 0) texte else texte.replace("v.compare", "v.compareTo")
            val o = if (tour % 2 == 0) marque else s.indexOf("v.compareTo") + "v.compareTo".length
            val debut = System.nanoTime()
            moteur.completer("/demo/Main.java", s, o)
            if (tour >= 2) echantillons.add((System.nanoTime() - debut) / 1_000_000)
        }
        val triees = echantillons.sorted()
        println(
            "MEMO membres binaires (java.lang.String, mémo chaud) : p50=%.2f ms, max=%.2f ms (%d échantillons)".format(
                triees[triees.size / 2].toDouble(),
                triees.last().toDouble(),
                triees.size,
            ),
        )
    }

    // ------------------------------------------------------------------ //
    // ADR 0026 (f) — littéraux R.* (fixture mini de V-B)
    // ------------------------------------------------------------------ //

    @Test
    fun `litteral de chaine en position StringRes propose les noms de ressources`() {
        val mini = Path.of("src/test/resources/projetandroid/mini/app/src/main/java")
        val contrat = jo.codeide.lsp.java.projetandroid.ModeleAndroidDerive.deriver(listOf(mini))!!
        val moteur = MoteurJava(listOf(mini), emptyList(), null, "17", null, null, contrat)
        val source = """
            package com.exemple.mini;

            public class Nav {
                @interface StringRes {}
                static void titre(@StringRes int res) {}
                void m() {
                    titre("sal¦");
                }
            }
        """.trimIndent()
        val marque = source.indexOf('¦')
        val texte = source.replace("¦", "")
        val reponse = moteur.completer("/com/exemple/mini/Nav.java", texte, marque)
        assertEquals(GenreContexte.LITTERAL_RESSOURCE, reponse.genre, "le littéral en position @StringRes est reconnu")
        assertEquals(listOf("salut"), reponse.items.map { it.libelle }, "le nom de ressource du référentiel est proposé (R.string.salut)")
        assertEquals("R.string.salut", reponse.items.first().detail)
    }

    @Test
    fun `litteral non annote reste muet (comportement d avant V-C3)`() {
        val moteur = moteur()
        val reponse = completer(moteur, "String v = \"sal¦")
        assertEquals(GenreContexte.INCONNU, reponse.genre, "un littéral sans annotation reste INCONNU (liste vide honnête)")
        assertTrue(reponse.items.isEmpty())
    }
}

/** L'index factice du 3e appel (D3) : UN type de projet hors classpath. */
private class IndexFactice : jo.codeide.lsp.java.IndexNoms {
    override fun estPret(): Boolean = true
    override fun ajouterObservateurPret(observateur: () -> Unit) = observateur()
    override fun jarDe(fqcn: String): String? = null
    override fun nomsParPrefixe(prefixe: String, limite: Int): List<String> =
        if ("Zezture".startsWith(prefixe)) listOf("Zezture") else emptyList()
    override fun fqnsForSimpleName(nom: String): List<String> =
        if (nom == "Zezture") listOf("demo.ailleurs.Zezture") else emptyList()
    override fun estPaquet(paquet: String): Boolean = paquet == "demo.ailleurs"
    override fun sousPaquetsDirects(paquet: String): List<String> = emptyList()
    override fun typesDansPaquet(paquet: String): List<String> =
        if (paquet == "demo.ailleurs") listOf("demo.ailleurs.Zezture") else emptyList()
    override fun membresStatiques(fqcn: String): List<String> = emptyList()
}
