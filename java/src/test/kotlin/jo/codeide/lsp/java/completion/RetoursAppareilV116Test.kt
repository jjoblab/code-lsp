package jo.codeide.lsp.java.completion

import jo.codeide.lsp.java.MoteurJava
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Retours appareil du 31/10 au soir (v1.1.6) — « le moteur n'est pas
 * intelligent : dépendamment où l'utilisateur tape, il doit savoir où il
 * est ; s'il veut utiliser l'une des méthodes de SES classes, il doit les
 * voir dans les suggestions de complétion ET dans l'aide de signature ».
 *
 * Ce harnais verrouille les trois correctifs de la vague :
 *
 *  1. **mots-clés en position de top level** (écart consigné
 *     INTEGRATION-CODEIDE §13.4) : `i|`, `im|` AVANT l'espace rendaient 0
 *     item là où Android Studio propose `import`/`interface` — le genre
 *     TOP_NIVEAU et ses gardes textuelles (profondeur d'accolades zéro,
 *     tête de déclaration, littéraux/commentaires) ;
 *  2. **aide de signature niveau 4 sur méthode du PROJET** : la virgule
 *     traînante `repeter("monip", ` rendait RIEN — la fermeture de la
 *     liste d'arguments s'insère désormais AVANT l'accolade du bloc
 *     englobant (l'ancienne queue plaçait le «)» après les «}») ;
 *  3. **latence de la résolution marquée** : la réduction SourceFocuser
 *     (corps étrangers effacés) — couverte au banc (S17/large fichier),
 *     ce harnais verrouille la NON-régression du classement.
 */
class RetoursAppareilV116Test {

    private companion object {

        /** Racine de sources du projet simulé (Greeter + MainActivity). */
        val RACINE: Path by lazy {
            val racine = Files.createTempDirectory("retours-v116")
            val greeter = racine.resolve("com/exemple/monip/Greeter.java")
            Files.createDirectories(greeter.parent)
            greeter.writeText(
                """
                package com.exemple.monip;

                public class Greeter {
                    private String politesse = "Bonjour";

                    public String saluer(String nom) {
                        return politesse + " " + nom + " !";
                    }

                    public String repeter(String nom, int fois) {
                        return nom.repeat(fois);
                    }
                }
                """.trimIndent(),
            )
            val principal = racine.resolve("com/ex/exemple/MainActivity.java")
            Files.createDirectories(principal.parent)
            principal.writeText(
                """
                package com.ex.exemple;

                import com.exemple.monip.Greeter;

                public class MainActivity {
                    private Greeter liaison = new Greeter();

                    public void onCreate() {
                        String texte = liaison.saluer("monip");
                    }
                }
                """.trimIndent(),
            )
            racine
        }

        /** Moteur sur le projet simulé (plateforme du jrt du JVM de test). */
        val MOTEUR: MoteurJava by lazy { MoteurJava(racines = listOf(RACINE), jars = emptyList()) }

        /** Le squelette d'unité pour les complétions de top level. */
        val UNITE = """
            package com.ex.exemple;

            import com.exemple.monip.Greeter;

            ANCRE

            public class MainActivity {
                public void onCreate() {
                }
            }
        """.trimIndent()

        /** Complète `ancre` (contenant le marqueur ¦) en position de top level. */
        fun completerTopNiveau(ancre: String): jo.codeide.lsp.java.completion.ReponseCompletion {
            val marque = ancre.indexOf('¦')
            val texte = UNITE.replace("ANCRE", ancre.replace("¦", ""))
            val offset = texte.indexOf(ancre.replace("¦", "")) + marque
            return MOTEUR.completer("/com/ex/exemple/MainActivity.java", texte, offset)
        }

        /** L'aide de signature sur l'unité de travail à l'ancre donnée. */
        fun signatureSur(source: String, offset: Int) =
            MOTEUR.signatureHelp("/com/ex/exemple/MainActivity.java", source, offset)
    }

    // ------------------------------------------------------------------ //
    // 1. Mots-clés en position de top level (écart §13.4)
    // ------------------------------------------------------------------ //

    @org.junit.jupiter.api.Test
    fun motCleImportAvantLEspace() {
        val reponse = completerTopNiveau("im¦")
        assertEquals(GenreContexte.TOP_NIVEAU, reponse.genre, "la position `im|` est classée TOP_NIVEAU")
        val libelles = reponse.items.map { it.libelle }
        assertTrue("import" in libelles, "`im|` propose « import » — reçu : $libelles")
        assertTrue(
            libelles.none { it == "class" || it == "enum" },
            "les mots-clés hors préfixe ne paraissent pas — reçu : $libelles",
        )
    }

    @org.junit.jupiter.api.Test
    fun motCleInterfaceEtImplementsDepuisI() {
        val reponse = completerTopNiveau("i¦")
        val libelles = reponse.items.map { it.libelle }
        assertTrue("interface" in libelles, "`i|` propose « interface » — reçu : $libelles")
        assertTrue("implements" in libelles, "`i|` propose « implements » — reçu : $libelles")
        assertTrue("import" in libelles, "`i|` propose « import » — reçu : $libelles")
    }

    @org.junit.jupiter.api.Test
    fun motClePackageDepuisP() {
        val reponse = completerTopNiveau("p¦")
        val libelles = reponse.items.map { it.libelle }
        assertTrue("package" in libelles, "`p|` propose « package » — reçu : $libelles")
        assertTrue("public" in libelles, "`p|` propose « public » — reçu : $libelles")
    }

    @org.junit.jupiter.api.Test
    fun motCleExtendsDansLaTeteDeDeclaration() {
        // La tête de déclaration vit à profondeur zéro. AVANT V-C3, la
        // classification marquée y capturait le sélecteur de MÉTHODE en
        // cours (variante «() {}» — fiche 007 : CORPS_CLASSE) et le repli
        // TOP_NIVEAU ne s'y appliquait PAS : le mot-clé `extends` était
        // MUET pendant sa frappe (l'ÉCART documenté docs/ECARTS-SERVEUR.md
        // = G-024/M1). AMENDÉ PAR LA VAGUE V-C3 (décision D7) : la tête de
        // déclaration rend TOP_NIVEAU — les MOTS-CLÉS de clause proposés
        // PENDANT leur frappe, FUSIONNÉS aux stubs d'override de la
        // hiérarchie du type en cours de déclaration (le comportement
        // Android Studio). Borne documentée : une tête LOCALE (classe dans
        // un corps de méthode) reste un artefact de recovery d'ecj.
        val squelette = """
            package demo;

            public class Principal e¦
        """.trimIndent()
        val marque = squelette.indexOf('¦')
        val texte = squelette.replace("¦", "")
        val reponse = MOTEUR.completer("/demo/Principal.java", texte, marque)
        assertEquals(GenreContexte.TOP_NIVEAU, reponse.genre, "la tête de déclaration rend TOP_NIVEAU (D7/M1 — mots-clés de clause)")
        val libelles = reponse.items.map { it.libelle }
        assertTrue("extends" in libelles, "le mot-clé extends est proposé pendant sa frappe — reçu : ${libelles.take(6)}")
        assertTrue(
            libelles.any { it.startsWith("equals") },
            "un stub d'override accompagne (fusion D7) — reçu : ${libelles.take(6)}",
        )
    }

    @org.junit.jupiter.api.Test
    fun pasDeMotsClesTopNiveauDansUnCorpsDeMethode() {
        // À profondeur ≥ 1, la complétion de nom reste le popup NOM complet
        // (locales + membres + mots-clés + types) — PAS le genre TOP_NIVEAU.
        val squelette = """
            package demo;

            public class Principal {
                public void run() {
                    int titre = 1;
                    ti¦
                }
            }
        """.trimIndent()
        val marque = squelette.indexOf('¦')
        val texte = squelette.replace("¦", "")
        val reponse = MOTEUR.completer("/demo/Principal.java", texte, marque)
        assertTrue(
            reponse.genre != GenreContexte.TOP_NIVEAU,
            "un corps de méthode n'est jamais classé TOP_NIVEAU — genre=${reponse.genre}",
        )
        assertTrue(
            reponse.items.any { it.libelle == "titre" },
            "la locale en tête du popup NOM (retour v1.1.5 tenu) — reçu : ${reponse.items.map { it.libelle }.take(6)}",
        )
    }

    @org.junit.jupiter.api.Test
    fun pasDeMotsClesTopNiveauApresLeMotClass() {
        // `class Fo|` : la position porte un NOM de type — jamais de
        // mots-clés de déclaration (garde TETES_DECLARATION).
        val squelette = """
            package demo;

            public class Fo¦
        """.trimIndent()
        val marque = squelette.indexOf('¦')
        val texte = squelette.replace("¦", "")
        val reponse = MOTEUR.completer("/demo/Principal.java", texte, marque)
        assertTrue(
            reponse.genre != GenreContexte.TOP_NIVEAU,
            "`class Fo|` n'est pas une position de mot-clé — genre=${reponse.genre}",
        )
    }

    @org.junit.jupiter.api.Test
    fun pasDeMotsClesTopNiveauDansUnCommentaire() {
        // Le caret dans un commentaire de top level : jamais de complétion
        // de mots-clés (la garde franchit les littéraux/commentaires).
        val squelette = """
            package demo;

            // note im¦

            public class Principal {
            }
        """.trimIndent()
        val marque = squelette.indexOf('¦')
        val texte = squelette.replace("¦", "")
        val reponse = MOTEUR.completer("/demo/Principal.java", texte, marque)
        assertTrue(
            reponse.items.none { it.libelle == "import" },
            "un commentaire ne propose pas « import » — reçu : ${reponse.items.map { it.libelle }}",
        )
    }

    // ------------------------------------------------------------------ //
    // 2. Aide de signature niveau 4 sur méthode du PROJET
    // ------------------------------------------------------------------ //

    @org.junit.jupiter.api.Test
    fun signatureMethodeProjetVirguleTrainante() {
        // LA repro du retour : « je veux voir les méthodes de mes classes
        // dans l'aide signature » — deux arguments, le second en cours.
        val source = """
            package com.ex.exemple;

            import com.exemple.monip.Greeter;

            public class MainActivity {
                private Greeter liaison = new Greeter();

                public void onCreate() {
                    String texte = liaison.repeter("monip", ${'¦'}
                }
            }
        """.trimIndent()
        val marque = source.indexOf('¦')
        val texte = source.replace("¦", "")
        val aide = signatureSur(texte, marque)
        assertNotNull(aide, "la virgule traînante rend une aide (niveau 4)")
        assertTrue(
            aide.signatures.any { it.libelle.contains("repeter") && it.libelle.contains("int") },
            "la signature PROJET repeter(String, int) est servie — reçu : ${aide.signatures.map { it.libelle }}",
        )
        assertEquals(1, aide.parametreActif, "le paramètre ACTIF est le second (fois)")
    }

    @org.junit.jupiter.api.Test
    fun signatureMethodeProjetParametresNommesDepuisLaSource() {
        // Les noms de paramètres viennent de la SOURCE du projet (pas des
        // binaires) — `nom`/`fois` doivent paraître dans le libellé.
        val source = """
            package com.ex.exemple;

            import com.exemple.monip.Greeter;

            public class MainActivity {
                private Greeter liaison = new Greeter();

                public void onCreate() {
                    String texte = liaison.repeter("monip", 2¦
                }
            }
        """.trimIndent()
        val marque = source.indexOf('¦')
        val texte = source.replace("¦", "")
        val aide = signatureSur(texte, marque)
        assertNotNull(aide, "l'appel au second argument rend une aide")
        val libelle = aide.signatures.first().libelle
        assertTrue(
            " nom" in libelle && " fois" in libelle,
            "les noms de paramètres de la SOURCE paraissent — reçu : $libelle",
        )
    }

    @org.junit.jupiter.api.Test
    fun signatureMethodeProjetChaineOuverte() {
        // Famille A : chaîne non refermée au premier argument.
        val source = """
            package com.ex.exemple;

            import com.exemple.monip.Greeter;

            public class MainActivity {
                private Greeter liaison = new Greeter();

                public void onCreate() {
                    String texte = liaison.saluer("monip¦
                }
            }
        """.trimIndent()
        val marque = source.indexOf('¦')
        val texte = source.replace("¦", "")
        val aide = signatureSur(texte, marque)
        assertNotNull(aide, "la chaîne ouverte rend une aide (famille A)")
        assertTrue(
            aide.signatures.any { it.libelle.contains("saluer") },
            "la signature PROJET saluer(String) est servie — reçu : ${aide.signatures.map { it.libelle }}",
        )
        assertEquals(0, aide.parametreActif, "le paramètre actif est le premier (nom)")
    }

    @org.junit.jupiter.api.Test
    fun signatureHorsAppelToujoursNulle() {
        // La garde absolue de la fiche 023 tient toujours : hors appel,
        // RIEN n'est inventé — même en top level.
        val source = """
            package demo;

            public class Principal {
                public void run() {
                    int x = 5;¦
                }
            }
        """.trimIndent()
        val marque = source.indexOf('¦')
        val texte = source.replace("¦", "")
        assertNull(signatureSur(texte, marque), "hors appel : null, jamais rien d'inventé")
    }
}
