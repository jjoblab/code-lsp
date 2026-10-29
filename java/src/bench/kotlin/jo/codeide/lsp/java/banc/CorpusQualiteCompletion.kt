package jo.codeide.lsp.java.banc

import jo.codeide.lsp.java.completion.GenreContexte

/**
 * Corpus FIXE VERSIONNÉ du banc de qualité de complétion (étage N2 —
 * ADR 0016 : corpus maison déterministe, enrichissement par PR uniquement).
 *
 * Chaque position porte :
 *  - `source` : la source Java COMPLÈTE, le caret marqué par « ¦ » (la
 *    convention de la suite de tests) ;
 *  - `genreAttendu` : le genre de contexte diagnostiqué (contrôle de
 *    santé : un verdict qui change d'un run à l'autre est un bug, pas une
 *    mesure) ;
 *  - `attendus` : les libellés qui DOIVENT figurer dans la liste rendue
 *    (recall) ;
 *  - `gagnant` : le libellé désigné PREMIER (top-1 / MRR) — l'étiquette
 *    encode le CLASSEMENT désiré, pas seulement la présence.
 *
 * Sources des étiquettes (traçabilité, pas invention) :
 *  - fiches 007/008/035/044 : verrous de comportement du cahier (pires
 *    cas de recovery, préfixe dominant, sous-paquets dominants en
 *    contexte import) ;
 *  - design D8 (ADR 0012 « en naissance », ChaineClassement) : nature
 *    prioritaire par contexte (locale avant type en NOM, champ avant
 *    méthode en MEMBRE) et liftShorter.
 *
 * La baseline « naïve » (chaîne alphabétique seule — le statu quo sans
 * design) doit PERDRE les trois positions de divergence (d1/d2/d3) : c'est
 * la preuve de valeur exigée à la sortie d'étage (+5 points top-1/MRR).
 *
 * Plateforme : jrt du runtime SEUL (pas d'android.jar) — le banc tourne
 * partout, CI comprise ; les comportements android.jar sont couverts par
 * les tests de fiches (036/043/044/050/053).
 */
object CorpusQualiteCompletion {

    /** Une position de complétion étiquetée du corpus. */
    class Position(
        val nom: String,
        val source: String,
        val genreAttendu: GenreContexte,
        val attendus: List<String>,
        val gagnant: String,
    )

    /** La source Main posée sur disque (sans bloc — le corpus marque les positions dans des variantes). */
    val SOURCE_MAIN = """
        package demo;

        import java.util.List;

        public class Main {
            private int counter = 0;
            private String name = "x";

            public String greet(String who) {
                return "hello " + who;
            }

            public int abc() {
                return 1;
            }

            public int getCounter() {
                int abd = 42;
                return 0;
            }
        }
    """.trimIndent()

    /** La source Widget posée sur disque (champ zCount, méthode zAdd — divergence d3). */
    val SOURCE_WIDGET = """
        package demo;

        public class Widget {
            public int zCount = 3;

            public int zAdd() {
                return zCount + 1;
            }
        }
    """.trimIndent()

    /** Le squelette Main : champs, méthodes, locale `abd` dans getCounter. */
    private fun mainAvec(bloc: String): String = """
        package demo;

        import java.util.List;

        public class Main {
            private int counter = 0;
            private String name = "x";

            public String greet(String who) {
                return "hello " + who;
            }

            public int abc() {
                return 1;
            }

            public int getCounter() {
                int abd = 42;
                $bloc
                return 0;
            }
        }
    """.trimIndent()

    /** Le squelette import : paquet + tête d'import + classe vide. */
    private fun squeletteImport(tete: String): String = """
        package demo;

        $tete

        public class Main {
        }
    """.trimIndent()

    /** Le squelette membre de classe : la position vit en CORPS DE CLASSE. */
    private fun classeAvec(membre: String): String = """
        package demo;

        import java.util.List;

        public class Main {
            private int counter = 0;
            private String name = "x";

            $membre

            public String greet(String who) {
                return "hello " + who;
            }

            public int getCounter() {
                return 0;
            }
        }
    """.trimIndent()

    /**
     * Les positions du corpus (v1 — 18 positions, 3 de divergence).
     *
     * Divergences désignées (la chaîne naïve les perd) :
     *  - d1 `import java.util.f` : le sous-paquet `function` DOMINE le type
     *    `Formatter` (fiche 044 — paquets avant types en import ; naïf
     *    rend « Formatter » en tête, la casse ASCII favorise les types) ;
     *  - d2 `return ab` : la LOCALE `abd` domine la méthode `abc` et les
     *    types `Abstract*` en NOM (D8 PeseurNature ; naïf rend
     *    « Abstract… » en tête) ;
     *  - d3 `return w.z` : le CHAMP `zCount` domine la méthode `zAdd` en
     *    MEMBRE (D8 PeseurNature ; naïf rend « zAdd » en tête).
     */
    val positions: List<Position> = listOf(
        // --- MEMBRE (instance, java.lang.String via jrt — fiches 020) ---
        Position("membre-longueur", mainAvec("String s = \"a\";\n                return s.le¦"), GenreContexte.MEMBRE, listOf("length"), "length"),
        Position("membre-isEmpty", mainAvec("String s = \"a\";\n                return s.isEmp¦"), GenreContexte.MEMBRE, listOf("isEmpty"), "isEmpty"),
        Position("membre-toString", mainAvec("String s = \"a\";\n                return s.toStr¦"), GenreContexte.MEMBRE, listOf("toString"), "toString"),
        Position("membre-champ-propre", mainAvec("return name.le¦"), GenreContexte.MEMBRE, listOf("length"), "length"),
        Position("membre-hierarchie", mainAvec("String s = \"a\";\n                return s.eq¦"), GenreContexte.MEMBRE, listOf("equals"), "equals"),
        // --- MEMBRE_STATIQUE (fiche 020) ---
        Position("statique-valueOf", mainAvec("return String.va¦"), GenreContexte.MEMBRE_STATIQUE, listOf("valueOf"), "valueOf"),
        // --- MEMBRE sur Widget (divergence d3) ---
        Position("membre-z-champ", mainAvec("Widget w = new Widget();\n                return w.z¦"), GenreContexte.MEMBRE, listOf("zCount", "zAdd"), "zCount"),
        // --- TYPE (recovery et préfixe — fiches 008/035/050 ; position de CORPS DE CLASSE) ---
        Position("type-String", classeAvec("private Stri¦"), GenreContexte.TYPE, listOf("String"), "String"),
        Position("type-ArrayList", classeAvec("private ArrayLis¦"), GenreContexte.TYPE, listOf("ArrayList"), "ArrayList"),
        Position("type-Integer", classeAvec("private Inte¦"), GenreContexte.TYPE, listOf("Integer"), "Integer"),
        Position("type-Override", classeAvec("@Overrid¦"), GenreContexte.TYPE, listOf("Override"), "Override"),
        // --- NOM (locale + méthode + types Abstract* ; divergence d2) ---
        Position("nom-locale-domine", mainAvec("return ab¦"), GenreContexte.NOM, listOf("abd", "abc"), "abd"),
        Position("nom-champ", mainAvec("return cou¦"), GenreContexte.NOM, listOf("counter"), "counter"),
        // --- IMPORT (règles d'or — fiche 044). Verdict attendu :
        // REFERENCE_PAQUET — la SONDE capture l'import non terminé comme
        // référence de paquet AVANT la classification marquée (routage
        // IDENTIQUE : les deux genres servent sousPaquetages).
        Position("import-java-u", squeletteImport("import java.u¦"), GenreContexte.REFERENCE_PAQUET, listOf("util"), "util"),
        // --- IMPORT (divergence d1 : paquet function vs type Formatter) — sonde : REFERENCE_PAQUET. ---
        Position("import-paquet-domine", squeletteImport("import java.util.f¦"), GenreContexte.REFERENCE_PAQUET, listOf("function", "Formatter"), "function"),
        // --- REFERENCE_PAQUET (hors import — fiche 044 rule 9) ---
        Position("refpaquet-List", mainAvec("Object o = java.util.Li¦"), GenreContexte.REFERENCE_PAQUET, listOf("List"), "List"),
        // --- CORPS_CLASSE (stubs d'override — fiche 007 ; libellé AVEC parenthèses ; position de CORPS DE CLASSE) ---
        Position("corps-toString", classeAvec("toStr¦"), GenreContexte.CORPS_CLASSE, listOf("toString()"), "toString()"),
        // --- CASE_LABEL (constantes d'enum — fiche 007) ---
        Position(
            "case-ROUGE",
            """
            package demo;

            public class Main {
                enum Couleur { ROUGE, VERT }

                void m(Couleur c) {
                    switch (c) {
                        case RO¦
                    }
                }
            }
            """.trimIndent(),
            GenreContexte.CASE_LABEL,
            listOf("ROUGE"),
            "ROUGE",
        ),
    )

    /**
     * La succession de frappe pour le contrat « zéro saut de liste » (gel
     * D8) : même document, même genre, même récepteur — le préfixe
     * s'affine, les têtes gelées ne sautent JAMIS.
     */
    val successionFrappe: List<String> = listOf(
        mainAvec("String s = \"a\";\n                return s.¦"),
        mainAvec("String s = \"a\";\n                return s.is¦"),
        mainAvec("String s = \"a\";\n                return s.isE¦"),
        mainAvec("String s = \"a\";\n                return s.isEm¦"),
    )
}
