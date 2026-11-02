// CodeIDE — corpus FIXE VERSIONNÉ du banc de qualité de complétion Kotlin
// (module :kotlin, étage 3 vague 3e — critère de sortie, ADR 0119 option 1 :
// miroir du corpus Java `:java:benchQualite`, ADR 0016/0113 — corpus maison
// déterministe, enrichissement par PR uniquement).
//
// Chaque position porte :
//  - `uri` : le document de la position — CHAQUE POSITION VIT DANS SON
//    PROPRE DOCUMENT (la clé de gel document + genre + ancre ne peut JAMAIS
//    être partagée entre positions de mesure : le gel d'une frappe ne doit
//    pas contaminer la mesure d'une autre — la succession de frappe, elle,
//    partage SA clé délibérément) ;
//  - `source` : la source Kotlin COMPLÈTE, le caret marqué par « ¦ » (la
//    convention de la suite de tests) ;
//  - `genreAttendu` : le genre de contexte diagnostiqué (contrôle de
//    santé : un verdict qui change d'un run à l'autre est un bug, pas une
//    mesure) ;
//  - `attendus` : les libellés qui DOIVENT figurer dans la liste rendue
//    (recall) ;
//  - `gagnant` : le libellé désigné PREMIER (top-1 / MRR) — l'étiquette
//    encode le CLASSEMENT désiré, pas seulement la présence.
//
// Sources des étiquettes (traçabilité, pas invention) :
//  - fiches 022/047/045 (complétion membre, régime nom, pipeline — germe
//    et contrats K3/K5), fiche 043 (contextuelle v1 : arguments nommés,
//    constantes d'enum, attributs d'annotation), fiches 038/039 (récepteurs
//    de portée, ViewBinding), fiche 008 Java en miroir (sous-paquets
//    dominants en contexte import) ;
//  - design livré en 3c (ADR 0113, miroir ADR 0012 Java) : correspondance
//    graduée dominante, NATURE prioritaire par contexte (locale avant tout
//    en NOM, paquets avant types en IMPORT, propriétés avant fonctions en
//    MEMBRE, mots-clés derniers), liftShorter, départage alphabétique.
//
// La baseline « naïve » (chaîne alphabétique seule — le statu quo sans
// design) doit PERDRE les positions de divergence (★) : c'est la preuve de
// valeur exigée à la sortie d'étage (+5 points top-1/MRR, ADR 0113).
//
// TROIS DIFFÉRENCES STRUCTURELLES avec le corpus Java, conséquences du
// design Kotlin (documentées, pas contournées) :
//  1. Le régime MEMBRE ne FILTRE PAS par préfixe côté serveur (frontière
//     RPC ADR 0118 : le client re-filtre à chaque frappe) — la chaîne
//     naïve y rend l'espace ENTIER en ordre alphabétique : le gagnant
//     descend au rang de sa position alphabétique (`byteInputStream` devant
//     `trim` à 106 rangs d'écart, `clone` devant `zCount`). Les positions
//     membre sont donc TOUTES perdues par la naïve : c'est la MESURE de la
//     valeur du classement livré, pas un artefact ;
//  2. Les collectes NOM/IMPORT/contextuelles FILTRENT par préfixe SENSIBLE
//     À LA CASSE : à préfixe `annotation`, le type `Annotation` n'est pas
//     collecté — la divergence paquet-avant-type ne s'isolait donc pas par
//     un préfixe commun (elle s'éprouve à préfixe VIDE, où tout le niveau
//     est collecté) ;
//  3. Les ANCRES de gel sont tenues DISTINCTES entre positions PAR URI
//     (voir plus haut) — le banc le vérifie mécaniquement (passe de
//     stabilité sous gel, miroir du contrôle de déterminisme des genres).
//
// Plateforme : kotlin-stdlib du RUNTIME seule (localisée par ancre de
// classe — la chaîne d'outils l'épingle à 2.2.10), PAS d'android.jar : le
// banc tourne partout, CI comprise ; les comportements android.jar sont
// couverts par les tests de fiches (040/044/045/047). Le projet corpus
// (Widget, Outil) vit sur DISQUE dans les racines sources — résolu par le
// scan, comme une vraie frappe.
package jo.codeide.lsp.kotlin.banc

import jo.codeide.lsp.kotlin.api.GenreContexteKotlin

/**
 * Le corpus FIXE VERSIONNÉ du banc de qualité de complétion Kotlin
 * (étage 3 — ADR 0119 option 1).
 */
object CorpusQualiteCompletion {

    /** Une position de complétion étiquetée du corpus. */
    class Position(
        val nom: String,
        val uri: String,
        val source: String,
        val genreAttendu: GenreContexteKotlin,
        val attendus: List<String>,
        val gagnant: String,
    )

    /**
     * La source Widget posée sur DISQUE (racines sources du classpath) :
     * la classe de base `Outil` (membre hérité `identifiant`) et `Widget`
     * (propriété `zCount`, fonction `zAdd` — divergence de nature en
     * membre, miroir de la d3 Java).
     */
    val SOURCE_WIDGET = """
        package demo

        open class Outil {
            val identifiant: Int = 7
        }

        class Widget : Outil() {
            val zCount: Int = 3

            fun zAdd(): Int {
                return zCount + 1
            }
        }
    """.trimIndent()

    /**
     * Le squelette principal : classe `Main` (propriétés `compteur`/`nom`,
     * fonctions `saluer`/`creer`/`abc`) et la fonction DONNÉE — chaque
     * position vit dans SON document (clé de gel étanche par URI).
     */
    private fun principal(fonction: String): String = """
        package demo

        class Main {
            private var compteur: Int = 0
            private val nom: String = "x"

            fun saluer(qui: String): String {
                return "bonjour " + qui
            }

            fun creer(nom: String, age: Int): Int {
                return 0
            }

            fun abc(): Int {
                return 1
            }

            $fonction
        }
    """.trimIndent()

    /** Le squelette import : paquet, tête d'import, classe vide. */
    private fun squeletteImport(tete: String): String = """
        package demo

        $tete

        class Main
    """.trimIndent()

    /** Le squelette enum : la position vit dans la zone de condition d'un `when` à sujet enum typé. */
    private fun squeletteEnum(corps: String): String = """
        package demo

        enum class Couleur { ROUGE, VERT }

        class Main {
            fun choisir(c: Couleur): Int {
                when (c) {
                    $corps
                }
                return 0
            }
        }
    """.trimIndent()

    /** L'URI d'une position (une URI par position — clés de gel étanches). */
    private fun uri(nom: String): String = "file:///demo/corpus-$nom.kt"

    /**
     * Les positions du corpus (v1 — 16 positions).
     *
     * Divergences désignées (★ — la chaîne naïve les perd par le MÉCANISME
     * isolé, la collecte étant bornée par préfixe) :
     *  - ★ `import-paquet-domine` : à préfixe VIDE tout le niveau `kotlin`
     *    est collecté (les BUILTINS `Any`/`Annotation`/`String` n'existent
     *    pas comme ENTRÉES de classe du jar — ils vivent dans les
     *    `.kotlin_builtins` : les types directs servis sont les ~96 entrées
     *    `*.class`) ; les SOUS-PAQUETS dominent les types (nature, fiche
     *    008 Java en miroir) et `io` (2 lettres) gagne par liftShorter ;
     *    naïf rend « ArrayIntrinsicsKt » en tête et enterre les paquets
     *    (`io` au rang 112) ;
     *  - ★ `nom-locale-domine` : la LOCALE `abd` domine la fonction `abc`
     *    en NOM (nature locales-avant-tout) ; naïf rend « abc » en tête
     *    (a-b-c < a-b-d) ;
     *  - ★ `nom-propriete-implicite` : la PROPRIÉTÉ implicite `compteur`
     *    domine le mot-clé `companion` et les `componentN` en NOM (nature
     *    propriété-avant-fonction, mots-clés derniers) ; naïf rend
     *    « companion » en tête ;
     *  - ★ `import-lift-shorter` : `IntRange` (8) domine `IntProgression`
     *    (14) à correspondance et nature égales (liftShorter) ; naïf rend
     *    « IntProgression » en tête ;
     *  - ★ `constantes-enum-vides` : à préfixe VIDE, `VERT` (4) domine
     *    `ROUGE` (5) (liftShorter sur contexte exclusif) ; naïf rend
     *    « ROUGE » en tête.
     * Les positions MEMBRE sont toutes perdues par la naïve pour la raison
     * structurelle documentée en tête de fichier (espace non filtré,
     * frontière RPC).
     */
    val positions: List<Position> = listOf(
        // --- MEMBRE (extensions stdlib réelles décodées du jar — fiche 045) ---
        Position(
            "membre-extension-stdlib",
            uri("membre-extension-stdlib"),
            principal(
                """
                fun membreExtension() {
                    val s = "a"
                    s.tri¦
                }
                """.trimIndent(),
            ),
            GenreContexteKotlin.MEMBRE,
            listOf("trim"),
            "trim",
        ),
        // --- MEMBRE (accès sûr `?.` — ancre SAFE_ACCESS, fiche 022) ---
        Position(
            "membre-access-sur",
            uri("membre-access-sur"),
            principal(
                """
                fun membreAccessSur() {
                    val t: String? = null
                    t?.tri¦
                }
                """.trimIndent(),
            ),
            GenreContexteKotlin.MEMBRE,
            listOf("trim"),
            "trim",
        ),
        // --- MEMBRE (membre hérité du PROJET par le scan — chaîne Widget→Outil, fiche 044) ---
        Position(
            "membre-heritage-projet",
            uri("membre-heritage-projet"),
            principal(
                """
                fun membreHeritage() {
                    val v = Widget()
                    v.ide¦
                }
                """.trimIndent(),
            ),
            GenreContexteKotlin.MEMBRE,
            listOf("identifiant"),
            "identifiant",
        ),
        // --- MEMBRE (fonction propre du projet) ---
        Position(
            "membre-fonction-propre",
            uri("membre-fonction-propre"),
            principal(
                """
                fun membreFonction() {
                    val x = Widget()
                    x.zAd¦
                }
                """.trimIndent(),
            ),
            GenreContexteKotlin.MEMBRE,
            listOf("zAdd"),
            "zAdd",
        ),
        // --- MEMBRE (divergence de NATURE : propriété avant fonction — miroir d3 Java) ---
        Position(
            "membre-z-champ",
            uri("membre-z-champ"),
            principal(
                """
                fun membreChamp() {
                    val w = Widget()
                    w.z¦
                }
                """.trimIndent(),
            ),
            GenreContexteKotlin.MEMBRE,
            listOf("zCount", "zAdd"),
            "zCount",
        ),
        // --- NOM (divergence ★ : locale avant fonction) ---
        Position(
            "nom-locale-domine",
            uri("nom-locale-domine"),
            principal(
                """
                fun compterNoms(): Int {
                    val abd = 42
                    return ab¦
                }
                """.trimIndent(),
            ),
            GenreContexteKotlin.NOM,
            listOf("abd", "abc"),
            "abd",
        ),
        // --- NOM (divergence ★ : propriété implicite avant mot-clé et componentN) ---
        Position(
            "nom-propriete-implicite",
            uri("nom-propriete-implicite"),
            principal(
                """
                fun lireEtat(): Int {
                    com¦
                }
                """.trimIndent(),
            ),
            GenreContexteKotlin.NOM,
            listOf("compteur", "companion"),
            "compteur",
        ),
        // --- NOM (type du classpath par préfixe, import auto-éditable — fiche 045) ---
        Position(
            "nom-type-classpath",
            uri("nom-type-classpath"),
            principal(
                """
                fun typesBaties(): StringBuilder? {
                    val b: StringBui¦
                    return null
                }
                """.trimIndent(),
            ),
            GenreContexteKotlin.NOM,
            listOf("StringBuilder"),
            "StringBuilder",
        ),
        // --- NOM (mot-clé du langage — le témoin du repli honnête, fiche 043) ---
        Position(
            "nom-mot-cle",
            uri("nom-mot-cle"),
            principal(
                """
                fun motCleTémoin(): Int {
                    retu¦
                }
                """.trimIndent(),
            ),
            GenreContexteKotlin.NOM,
            listOf("return"),
            "return",
        ),
        // --- IMPORT (sous-paquet direct du paquet tapé — fiche 008 Java en miroir) ---
        Position(
            "import-sous-paquet",
            uri("import-sous-paquet"),
            squeletteImport("import kotlin.coll¦"),
            GenreContexteKotlin.IMPORT,
            listOf("collections"),
            "collections",
        ),
        // --- IMPORT (divergence ★ : à préfixe vide, le paquet `io` domine les types) ---
        Position(
            "import-paquet-domine",
            uri("import-paquet-domine"),
            squeletteImport("import kotlin.¦"),
            GenreContexteKotlin.IMPORT,
            listOf("io", "annotation"),
            "io",
        ),
        // --- IMPORT (divergence ★ : liftShorter, IntRange avant IntProgression) ---
        Position(
            "import-lift-shorter",
            uri("import-lift-shorter"),
            squeletteImport("import kotlin.ranges.I¦"),
            GenreContexteKotlin.IMPORT,
            listOf("IntRange", "IntProgression"),
            "IntRange",
        ),
        // --- ARGUMENTS_NOMMES (appel projet résolu, préfixe vide — fiche 043) ---
        Position(
            "arguments-nommes",
            uri("arguments-nommes"),
            principal(
                """
                fun demoAppel(): Int {
                    creer(¦
                    return 0
                }
                """.trimIndent(),
            ),
            GenreContexteKotlin.ARGUMENTS_NOMMES,
            listOf("age =", "nom ="),
            "age =",
        ),
        // --- CONSTANTES_ENUM (sujet enum projet typé, préfixe — fiche 043) ---
        Position(
            "constantes-enum-prefixe",
            uri("constantes-enum-prefixe"),
            squeletteEnum("RO¦"),
            GenreContexteKotlin.CONSTANTES_ENUM,
            listOf("ROUGE"),
            "ROUGE",
        ),
        // --- CONSTANTES_ENUM (divergence ★ : préfixe vide, liftShorter VERT avant ROUGE) ---
        Position(
            "constantes-enum-vides",
            uri("constantes-enum-vides"),
            squeletteEnum("¦"),
            GenreContexteKotlin.CONSTANTES_ENUM,
            listOf("ROUGE", "VERT"),
            "VERT",
        ),
        // --- ATTRIBUTS_ANNOTATION (annotation stdlib résolue par import — fiche 043) ---
        Position(
            "attributs-annotation",
            uri("attributs-annotation"),
            """
            package demo

            import kotlin.Deprecated

            @Deprecated(¦

            class Main
            """.trimIndent(),
            GenreContexteKotlin.ATTRIBUTS_ANNOTATION,
            listOf("level =", "message =", "replaceWith ="),
            "level =",
        ),
    )

    /** L'URI de la succession de frappe (les QUATRE frappes partagent la clé — c'est le contrat). */
    val URI_SUCCESSION: String = "file:///demo/corpus-succession-gel.kt"

    /**
     * La succession de frappe pour le contrat « zéro saut de liste » (gel
     * 3c) : même document, même genre, même récepteur — le préfixe
     * s'affine, les têtes gelées ne sautent JAMAIS (une tête qui SURVIT à
     * l'affinement garde SON rang exact ; l'espace membre n'est PAS filtré
     * côté serveur, les têtes survivent donc toutes).
     */
    val successionFrappe: List<String> = listOf(
        principal(
            """
            fun membreChamp() {
                val w = Widget()
                w.¦
            }
            """.trimIndent(),
        ),
        principal(
            """
            fun membreChamp() {
                val w = Widget()
                w.z¦
            }
            """.trimIndent(),
        ),
        principal(
            """
            fun membreChamp() {
                val w = Widget()
                w.zC¦
            }
            """.trimIndent(),
        ),
        principal(
            """
            fun membreChamp() {
                val w = Widget()
                w.zCo¦
            }
            """.trimIndent(),
        ),
    )
}
