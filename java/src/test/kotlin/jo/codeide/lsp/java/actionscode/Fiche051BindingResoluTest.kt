package jo.codeide.lsp.java.actionscode

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import jo.codeide.lsp.java.MoteurJava
import jo.codeide.lsp.java.diagnostics.Severite
import jo.codeide.lsp.java.projetandroid.ModeleAndroidDerive

/**
 * Fiche 051 (AMENDÉE par la décision D6 — vague V-B3) : les classes Data
 * Binding sont RÉSOLUES avant tout build par la fabrique light (ADR 0021)
 * — le déclassement `DeclassementDatabinding` est RETIRÉ (son rôle
 * disparaît : plus d'erreur à déclasser).
 *
 * Gate environnementale (motif Fiche004) : la plateforme android.jar est
 * EXIGÉE — un binding réel référence indirectement android.view.* /
 * android.widget.* (super, champs de vues, inflate) : sans plateforme,
 * l'erreur « indirectly referenced » est le comportement documenté de la
 * fiche 028, PAS un défaut du binding light.
 */
class Fiche051BindingResoluTest {

    private companion object {
        val MINI: Path = Path.of("src/test/resources/projetandroid/mini/app/src/main/java")

        val ANDROID_JAR: Path? =
            sequenceOf(
                System.getenv("BANC_ANDROID_LIBS")?.let { Path.of(it) },
                Path.of("../bancs/projet-android-libs"),
                Path.of("../../bancs/projet-android-libs"),
            ).firstOrNull { p -> p != null && Files.isRegularFile(p.resolve("android-34-ext12.jar")) }
                ?.resolve("android-34-ext12.jar")
    }

    /** Le moteur mini avec dataBinding ACTIVÉ + plateforme réelle. */
    private fun moteurDataBinding(): MoteurJava {
        val contrat = ModeleAndroidDerive.deriver(listOf(MINI))!!.copy(dataBinding = true)
        return MoteurJava(listOf(MINI), listOf(ANDROID_JAR!!), null, "17", null, null, contrat)
    }

    @Test
    fun `l import databinding et l usage complet du binding resolvent avant tout build`() {
        assumeTrue(ANDROID_JAR != null, "BANC_ANDROID_LIBS absent — plateforme requise (fiche 028)")
        val moteur = moteurDataBinding()

        // Repro EXACT de la fiche 051 d'origine : l'import `.databinding`
        // sur un projet SANS build — désormais RÉSOLU par la fabrique.
        val source = """
            package com.exemple.mini;

            import com.exemple.mini.databinding.EcranDonneesBinding;

            public class Ecran {
                private EcranDonneesBinding binding;

                void afficher(android.view.LayoutInflater gonfle) {
                    binding = EcranDonneesBinding.inflate(gonfle);
                    binding.textTitre.setText("x");
                    binding.liaison.titre = "x";
                }
            }
        """.trimIndent()
        val erreurs = moteur.diagnose("/com/exemple/mini/Ecran.java", source)
            .filter { it.severite == Severite.ERREUR }
        assertTrue(
            erreurs.isEmpty(),
            "import + inflate + champs de vues + variable : tout résout avant build — erreurs réelles : $erreurs",
        )
    }

    @Test
    fun `une faute de frappe de binding reste une erreur integrale - jamais de downgrade`() {
        assumeTrue(ANDROID_JAR != null, "BANC_ANDROID_LIBS absent — plateforme requise (fiche 028)")
        val moteur = moteurDataBinding()
        val source = """
            package com.exemple.mini;

            public class EcranFautif {
                Object o = com.exemple.mini.databinding.EcranFantomeBinding.INEXISTANT;
            }
        """.trimIndent()
        val erreurs = moteur.diagnose("/com/exemple/mini/EcranFautif.java", source)
            .filter { it.severite == Severite.ERREUR }
        assertTrue(
            erreurs.any { erreur -> erreur.message.contains("EcranFantomeBinding") },
            "la faute de frappe reste une ERROR (l'ancien déclassement en HINT est retiré — D6)",
        )
    }

    @Test
    fun `post-build la source generee reelle prime sur le binding light`() {
        val dossier = Files.createTempDirectory("binding-anti-doublon")
        try {
            val racine = Files.createDirectories(dossier.resolve("com/exemple/mini/databinding"))
            Files.writeString(
                racine.resolve("EcranDonneesBinding.java"),
                """
                package com.exemple.mini.databinding;
                public abstract class EcranDonneesBinding {
                    public static EcranDonneesBinding inflate(android.view.LayoutInflater i) { return null; }
                }
                """.trimIndent(),
            )
            val contrat = ModeleAndroidDerive.deriver(listOf(MINI))!!.copy(dataBinding = true)
            val moteur = MoteurJava(listOf(dossier), emptyList(), null, "17", null, null, contrat)
            val localisation = moteur.typeIndex().find("com.exemple.mini.databinding.EcranDonneesBinding")
            assertTrue(
                localisation is jo.codeide.lsp.java.classpath.EnvironnementNoms.Localisation.Source,
                "le binding généré du build passé gagne sur le light",
            )
        } finally {
            dossier.toFile().deleteRecursively()
        }
    }
}
