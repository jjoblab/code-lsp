package jo.codeide.lsp.java.completion

import jo.codeide.lsp.java.MoteurJava
import jo.codeide.lsp.java.interne.ItemCompletion
import jo.codeide.lsp.java.interne.NatureItem
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Fiche 020 — BindingCompletionHoverTest, PARTIE COMPLÉTION (8
 * comportements sur 15) : les complétions pilotées par bindings sur une
 * unité isolée — membres sur instance, membres statiques sur qualifier de
 * type, locales et membres englobants en complétion de nom, filtrage
 * strict par préfixe, robustesse au code cassé.
 *
 * La partie hover/définition de la fiche vit à l'étage N3 (PLAN-REECRITURE
 * §4 : « 020 mixte ») ; le présent harnais verrouille le versant
 * complétion du contrat.
 *
 * Fixture du cahier : `demo.Main` avec champ `counter`, champ `name`,
 * méthodes `greet(String)`, `getCounter()` ; moteur construit avec racines
 * sources et classpath de jars VIDES, conformité « 17 » — la plateforme
 * (java.lang…) vient de l'image jrt du JVM de test.
 *
 * Anomalie de nommage du domaine consignée au cahier (fichier
 * `EcjHoverDefinitionCompletionTest.java` mais classe
 * `JdtHoverDefinitionCompletionTest`) : la réécriture NOMME la classe
 * d'après le CONTRAT (bindings complétion), sans préfixe Ecj/Jdt —
 * convention de nommage du dépôt (AGENTS.md).
 */
class Fiche020BindingCompletionTest {

    private companion object {

        /**
         * Moteur partagé par la suite (jrt paresseux, racines/jars vides —
         * la plateforme vient du runtime, conformité « 17 »).
         */
        val MOTEUR: MoteurJava by lazy { MoteurJava(racines = emptyList(), jars = emptyList()) }

        /** Le squelette de `demo.Main` du cahier (champs + méthodes). */
        val SQUELETTE = """
            package demo;

            public class Main {
                private int counter = 0;
                private String name = "x";

                public String greet(String who) {
                    return "hello " + who;
                }

                public int getCounter() {
                    BLOC
                    return 0;
                }
            }
        """.trimIndent()

        /**
         * Complète au caret marqué par « ¦ » dans le bloc de `getCounter`
         * (fin de bloc si absent) — la convention de toute la suite.
         */
        fun completerA(bloc: String): jo.codeide.lsp.java.completion.ReponseCompletion {
            val marque = bloc.indexOf('¦')
            val ancre = bloc.replace("¦", "")
            val texte = SQUELETTE.replace("BLOC", ancre)
            // L'ancre est unique dans le squelette : l'offset = position du
            // bloc inséré + position du marqueur DANS le bloc.
            val offset = texte.indexOf(ancre) + if (marque >= 0) marque else ancre.length
            return MOTEUR.completer("/demo/Main.java", texte, offset)
        }

        /** Les libellés rendus (diagnostic des messages d'échec). */
        fun List<ItemCompletion>.libelles(): List<String> = map { it.libelle }
    }

    @org.junit.jupiter.api.Test
    fun memberCompletionOnStringVariable() {
        val reponse = completerA("String s = \"abc\";\n            return s.le¦")
        assertNotNull(reponse, "la réponse n'est jamais nulle (fiche 035)")
        assertTrue(
            reponse.items.any { it.libelle == "length" },
            "« length » proposé sur une locale String — items=${reponse.items.libelles().take(8)}",
        )
    }

    @org.junit.jupiter.api.Test
    fun memberCompletionIncludesInheritedMembers() {
        val reponse = completerA("String s = \"abc\";\n            return s.¦")
        val libelles = reponse.items.libelles()
        assertTrue("isEmpty" in libelles, "« isEmpty » (String) présent — items=${libelles.take(8)}")
        assertTrue("charAt" in libelles, "« charAt » (String) présent")
        assertTrue(
            "equals" in libelles,
            "« equals » (redéfinition String de Object) présent — la hiérarchie complète est balayée",
        )
        // PREUVE RÉELLE du balayage d'héritage (constat du banc de qualité
        // N2) : `notify` et `wait` ne sont redéfinis NULLE PART dans la
        // hiérarchie de String — ils ne remontent que si la chaîne va
        // jusqu'à Object (availableMethods d'ecj ne rend que les déclarées).
        assertTrue("notify" in libelles, "« notify » (Object, jamais redéfini) présent — la chaîne va jusqu'à Object")
        assertTrue("wait" in libelles, "« wait » (Object, jamais redéfini) présent")
    }

    @org.junit.jupiter.api.Test
    fun memberCompletionOnOwnField() {
        val reponse = completerA("return name.le¦")
        assertTrue(
            reponse.items.any { it.libelle == "length" },
            "« length » proposé sur le champ String propre — items=${reponse.items.libelles().take(8)}",
        )
    }

    @org.junit.jupiter.api.Test
    fun memberCompletionStaticQualifier() {
        val reponse = completerA("return String.va¦")
        val libelles = reponse.items.libelles()
        assertTrue(
            libelles.any { it == "valueOf" },
            "« valueOf » (membre STATIQUE) proposé sur le qualifier de type — items=${libelles.take(8)}",
        )
        assertTrue(
            "charAt" !in libelles,
            "« charAt » (méthode d'INSTANCE) JAMAIS proposé sur un qualifier de type",
        )
    }

    @org.junit.jupiter.api.Test
    fun nameCompletionProposesLocals() {
        val reponse = completerA("int total = 42;\n            return tot¦")
        assertTrue(
            reponse.items.any { it.libelle == "total" },
            "la locale « total » est proposée — items=${reponse.items.libelles().take(8)}",
        )
    }

    @org.junit.jupiter.api.Test
    fun nameCompletionProposesEnclosingMembers() {
        val reponse = completerA("return cou¦")
        assertTrue(
            reponse.items.any { it.libelle == "counter" },
            "le champ « counter » de la classe englobante est proposé — items=${reponse.items.libelles().take(8)}",
        )
    }

    @org.junit.jupiter.api.Test
    fun memberCompletionFiltersByPrefix() {
        val reponse = completerA("String s = \"abc\";\n            return s.is¦")
        assertTrue(
            reponse.items.isNotEmpty(),
            "des items sont rendus pour le préfixe « is » — items=${reponse.items.libelles().take(8)}",
        )
        assertTrue(
            reponse.items.all { it.libelle.startsWith("is") },
            "TOUS les items commencent par « is » (filtrage strict par préfixe) — items=${reponse.items.libelles().take(8)}",
        )
    }

    @org.junit.jupiter.api.Test
    fun completionOnBrokenCodeStillWorks() {
        val reponse = completerA("int broken = undefinedThing;\n            String s = \"abc\";\n            return s.le¦")
        assertTrue(
            reponse.items.any { it.libelle == "length" },
            "la récupération ecj résout malgré tout le contexte : « length » est proposé — items=${reponse.items.libelles().take(8)}",
        )
    }

    @org.junit.jupiter.api.Test
    fun memberCompletionNatureEstChampOuMethode() {
        // Le détail du contrat de nature : les membres d'instance rendus par
        // la sonde sont typés CHAMP/MÉTHODE (alimentation du kind LSP
        // Method/Field, fiche 050).
        val reponse = completerA("String s = \"abc\";\n            return s.¦")
        assertTrue(
            reponse.items.any { it.nature == NatureItem.METHODE },
            "au moins un item METHODE — items=${reponse.items.take(5).map { it.libelle to it.nature }}",
        )
    }

    @org.junit.jupiter.api.Test
    fun gelSurvitAuRetrecissementDeListe() {
        // Régression découverte PAR la fiche 020 : sur une même clé de gel
        // (document + genre + récepteur), l'affinement du préfixe rétrécit
        // la liste SOUS la taille de la tête gelée — le comblement des
        // trous devait lever IndexOutOfBounds, avalée par le contrat
        // « jamais null » (fiche 035) en liste VIDE silencieuse. La même
        // séquence (large puis fin) doit rendre les deux fois des items.
        val reponseLarge = completerA("String s = \"abc\";\n            return s.¦")
        assertTrue(reponseLarge.items.size > 8, "la liste large gèle plus de 8 têtes (pré-condition du cas)")
        val reponseFine = completerA("String s = \"abc\";\n            return s.le¦")
        assertTrue(
            reponseFine.items.any { it.libelle == "length" },
            "après affinement, « length » survit au gel rétréci — items=${reponseFine.items.libelles().take(8)}",
        )
    }
}
