// CodeIDE — tests de la fraîcheur inter-fichiers par empreinte STAT
// (module :kotlin, vague V-A — GK-038, miroir du schéma Java ADR 0025).
// Code NEUF jjoblab (clean-room — comportement : une modification EXTERNE
// d'un fichier du scan (hors éditeur) est DÉTECTÉE au consult suivant par
// sonde STAT throttlée, la passe delta-only la reprend, le consult d'après
// voit l'état frais — jamais un diagnostic périmé durable).
package jo.codeide.lsp.kotlin.session

import java.nio.file.Files
import java.nio.file.Path
import jo.codeide.lsp.kotlin.api.ClasspathKotlin
import jo.codeide.lsp.kotlin.api.EntreeClasspathKotlin
import jo.codeide.lsp.kotlin.api.KindEntreeKotlin
import jo.codeide.lsp.kotlin.api.SeveriteKotlin
import jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

/**
 * La vague V-A(c) — la FRAÎCHEUR INTER-FICHIERS (GK-038) : le document B
 * ouvert voit les changements EXTERNES du fichier A (frère du scan) après
 * la sonde STAT + la passe delta-only — le miroir Kotlin du schéma
 * d'empreintes de l'ADR 0025 Java.
 */
class FraicheurInterFichiersKotlinTest {

    @TempDir
    lateinit var racine: Path

    /** Le jar kotlin-stdlib du classpath de test (jar physique). */
    private fun jarStdlib(): java.io.File =
        java.io.File(
            kotlin.jvm.internal.Intrinsics::class.java.protectionDomain
                .codeSource.location.toURI(),
        )

    /** Session branchée sur le stdlib + les racines données (sondage borné). */
    private fun session(racines: List<Path>): SessionLangageKotlinDocuments {
        val moteur = ServiceMoteurLangageKotlin.unique as MoteurLangageKotlinNoyau
        val classpath = ClasspathKotlin(
            entrees = listOf(
                EntreeClasspathKotlin(KindEntreeKotlin.JAR, jarStdlib().path, "fraicheur:stdlib"),
            ),
            racinesSources = racines.map { it.toString() },
        )
        moteur.definirClasspath(classpath)
        val session = moteur.creerSession() as SessionLangageKotlinDocuments
        session.definirClasspath(classpath)
        session.rafraichirSources()
        return session
    }

    /**
     * L'édition EXTERNE de A (hors éditeur — pas de didChange) est vue par
     * les diagnostics de B : la sonde STAT détecte le disque qui bouge, la
     * passe delta-only reprend A, le consult suivant rend B À JOUR (le
     * symbole disparu de A devient non résolu dans B).
     */
    @Test
    fun `edition-externe-de-A---diagnostics-de-B-frais-au-consult-suivant`() {
        val a = racine.resolve("A.kt")
        val b = racine.resolve("B.kt")
        Files.writeString(a, "package p\n\nfun partage(): Int = 1\n")
        Files.writeString(b, "package p\n\nfun consommateur(): Int = partage()\n")
        val session = session(listOf(racine))
        try {
            val uriB = b.toUri().toString()
            session.ouvrir(uriB, Files.readString(b))
            val avant = session.diagnostics(uriB)
            assertTrue(
                avant.none { it.severite == SeveriteKotlin.ERREUR },
                "B sain tant que A porte partage() (diags: ${avant.map { it.code.code }})",
            )

            // Édition EXTERNE : A perd partage() — aucun didChange, aucun
            // événement hôte : le disque seul a bougé.
            Thread.sleep(50)
            Files.writeString(a, "package p\n\nfun remplace(): Int = 2\n")

            // Le premier consult déclenche la sonde (throttle écoulé) et
            // amorce la passe ; on REJOINT l'état frais par sondage borné
            // (la re-publication est un bloc — miroir du cycle hôte réel).
            val limite = System.currentTimeMillis() + 30_000
            var apres = session.diagnostics(uriB)
            while (System.currentTimeMillis() < limite &&
                apres.none { it.severite == SeveriteKotlin.ERREUR }
            ) {
                Thread.sleep(100)
                apres = session.diagnostics(uriB)
            }
            assertTrue(
                apres.any { it.severite == SeveriteKotlin.ERREUR },
                "B voit la disparition de partage() une fois la passe delta-only publiée " +
                    "(diags: ${apres.map { it.code.code }})",
            )
        } finally {
            session.close()
        }
    }

    /**
     * Le scanneur lui-même expose la sonde : un disque intact rend `false`,
     * un mtime qui bouge rend `true` (le contrat de l'empreinte STAT).
     */
    @Test
    fun `sonde-stat---intacte-puis-perimee-au-changement`() {
        val a = racine.resolve("A.kt")
        Files.writeString(a, "package p\n\nfun un(): Int = 1\n")
        val session = session(listOf(racine))
        try {
            assertTrue(!scanneurPerime(session), "disque intact : la sonde rend faux")
            Thread.sleep(50)
            Files.writeString(a, "package p\n\nfun deux(): Int = 2\n")
            assertTrue(scanneurPerime(session), "mtime bougé : la sonde rend vrai")
        } finally {
            session.close()
        }
    }

    /** Accès de test au scanneur de la session (sonde STAT). */
    private fun scanneurPerime(session: SessionLangageKotlinDocuments): Boolean {
        val champ = SessionLangageKotlinDocuments::class.java.getDeclaredField("scanneur")
        champ.isAccessible = true
        val scanneur = champ.get(session) as ScanProjetParesseux
        return scanneur.perimeAuStat()
    }
}
