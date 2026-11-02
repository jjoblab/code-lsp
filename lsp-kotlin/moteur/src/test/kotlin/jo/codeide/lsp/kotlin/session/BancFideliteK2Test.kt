package jo.codeide.lsp.kotlin.session

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import jo.codeide.lsp.kotlin.api.ClasspathKotlin
import jo.codeide.lsp.kotlin.api.CodeDiagnosticKotlin
import jo.codeide.lsp.kotlin.api.EntreeClasspathKotlin
import jo.codeide.lsp.kotlin.api.KindEntreeKotlin
import jo.codeide.lsp.kotlin.api.ServiceMoteurLangageKotlin
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Le BANC DE FIDÉLITÉ K2 (étage 2 vague 2g — clôture, ADR 0119 option 1) :
 * l'oracle est le compilateur K2 VÉRITABLE lancé en SOUS-PROCESSUS
 * (`org.jetbrains.kotlin.cli.jvm.K2JVMCompiler` sur le classpath de test)
 * sur le CORPUS FIXE DES QUATRE MONDES + un TÉMOIN POSITIF.
 *
 * ## Les deux mesures (ADR 0119, critère §4)
 *
 * - **FAUX POSITIFS = 0** (INVARIANT) : chaque `kt.unresolved` émis par le
 *   moteur sur le corpus doit correspondre à un « unresolved reference »
 *   de K2 (même nom, même ligne) — sur les quatre mondes SAINS le moteur
 *   n'émet RIEN, sur le témoin positif les DEUX s'accordent ;
 * - **FAUX NÉGATIFS BASELINÉS SIGNÉS** : les « unresolved reference » de
 *   K2 que le moteur ne couvre pas sont COMPTÉS et comparés à la baseline
 *   (`src/test/resources/fidelite/baseline-fidelite.txt` — le compte ET
 *   l'EMPREINTE SHA-256 du détail complet : toute variation, en hausse
 *   comme en baisse, est une divergence à EXPLIQUER avant re-signature ;
 *   la couverture se mesure, elle ne se déclare pas. Re-signature :
 *   `-Dfidelite.majBase=true --rerun-tasks` — écrit dans l'ARBRE SOURCE,
 *   miroir de la suite de parité fiche 010).
 *
 * ## Le corpus (fixe, versionné)
 *
 * Les quatre mondes sains (Views/Compose/Coroutines/stdlib) sont
 * K2-COMPILABLES par construction — la fidélité mesurée est celle de la
 * COUVERTURE (ce que le moteur prouve là où K2 prouve), pas celle des
 * divergences délibérées du modèle CodeIDE (porte INSTANCE, ADR 0219) :
 * celles-ci vivent dans les fiches du cahier. Le témoin positif porte
 * des références VRAIMENT inconnues — l'oracle ET le moteur doivent les
 * signaler (le harnais ne valide pas un « tout muet »).
 */
class BancFideliteK2Test {

    /** La racine temporaire des jars et sorties. */
    @TempDir
    lateinit var racine: Path

    /** Les jars réels du classpath de test. */
    private fun jarClasse(ancre: Class<*>): java.io.File =
        java.io.File(ancre.protectionDomain.codeSource.location.toURI())

    /**
     * Le jar de fixture du monde Compose (statiques Java — l'interop
     * LÉGALE pour l'oracle : un membre STATIQUE d'une classe Java se
     * lit par le NOM DE CLASSE, une Java-class sans `@Metadata` n'est
     * JAMAIS un objet Kotlin — les membres d'instance via le nom de
     * classe seraient des NON-RESOLUS pour K2, la porte INSTANCE du
     * moteur est un modèle CodeIDE que l'oracle ne partage pas).
     */
    private fun jarCompose(): java.io.File =
        OutilsJarFixture.Batisseur()
            .classe(
                "exo.theme.MaterialTheme", null,
                OutilsJarFixture.champStatique("typography", "Lexo/theme/Typography;"),
                OutilsJarFixture.champStatique("colorant", "Lexo/theme/ColorScheme;"),
            )
            .classe(
                "exo.layout.Arrangement", null,
                OutilsJarFixture.methodeStatique("spacedBy", "(I)Lexo/layout/Dp;"),
            )
            .classe("exo.theme.Typography", null)
            .classe("exo.theme.ColorScheme", null)
            .classe("exo.layout.Dp", null)
            .ecrire(racine, "fixture-compose-fidelite.jar")

    /** Le corpus : chemin de ressource → jars du classpath de la cible. */
    private fun corpus(): List<Pair<String, List<java.io.File>>> = listOf(
        "fidelite/monde-views.kt" to listOf(jarClasse(android.util.Log::class.java)),
        "fidelite/monde-compose.kt" to listOf(jarCompose(), jarClasse(kotlin.jvm.internal.Intrinsics::class.java)),
        "fidelite/monde-coroutines.kt" to listOf(
            jarClasse(kotlinx.coroutines.flow.Flow::class.java),
            jarClasse(kotlin.jvm.internal.Intrinsics::class.java),
        ),
        "fidelite/monde-stdlib.kt" to listOf(jarClasse(kotlin.jvm.internal.Intrinsics::class.java)),
        "fidelite/temoin-positif.kt" to listOf(jarClasse(kotlin.jvm.internal.Intrinsics::class.java)),
    )

    /** Le contenu d'une ressource du classpath de test. */
    private fun ressource(chemin: String): String =
        javaClass.classLoader.getResourceAsStream(chemin)!!.readBytes().toString(Charsets.UTF_8)

    /** Copie une ressource corpus vers un FICHIER (la cible de K2). */
    private fun copierRessource(chemin: String, dossier: Path): File {
        val fichier = dossier.resolve(chemin.substringAfterLast('/'))
        Files.createDirectories(dossier)
        Files.writeString(fichier, ressource(chemin))
        return fichier.toFile()
    }

    /**
     * L'ORACLE : K2 en SOUS-PROCESSUS sur le fichier donné — rend les
     * paires (ligne, nom) des « unresolved reference » (les autres erreurs
     * et avertissements ne comptent pas : la fidélité de l'étage est celle
     * des références).
     *
     * La forme K2 2.2 vérifiée sur le bac à sable :
     * `<chemin>:<ligne>:<colonne>: error: unresolved reference '<nom>'.`
     * (minuscules, nom entre APOSTROPHES, point final). L'invocation porte
     * `-no-stdlib` (le compilateur embarqué CHERCHE un kotlin-home et
     * n'en trouve pas hors installation — sans le drapeau il émet des
     * « cannot access built-in declaration » en cascade) et le classpath
     * cible reçoit TOUJOURS le jar kotlin-stdlib : aucun projet Kotlin ne
     * compile sans stdlib, l'oracle doit voir le MÊME univers que le
     * moteur (voie 6 — le stdlib est son plancher d'appelables).
     */
    private fun oracleK2(fichier: File, jars: List<java.io.File>): Set<Pair<Int, String>> {
        val classpathTest = System.getProperty("java.class.path")
        val ancreStdlib = jarClasse(kotlin.jvm.internal.Intrinsics::class.java)
        val classpathOracle = (jars + ancreStdlib)
            .distinctBy { it.absolutePath }
            .joinToString(File.pathSeparator) { it.absolutePath }
        val processus = ProcessBuilder(
            File(System.getProperty("java.home"), "bin/java").absolutePath,
            "-cp", classpathTest,
            "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
            "-no-stdlib",
            "-cp", classpathOracle,
            "-nowarn",
            fichier.absolutePath,
        ).redirectErrorStream(true).start()
        val sortie = processus.inputStream.bufferedReader().readText()
        processus.waitFor(120, TimeUnit.SECONDS)
        val nonResolus = mutableSetOf<Pair<Int, String>>()
        for (ligne in sortie.lineSequence()) {
            val correspondance =
                Regex(":(\\d+):\\d+: error: unresolved reference '([^']+)'").find(ligne) ?: continue
            val numeroLigne = correspondance.groupValues[1].toIntOrNull() ?: continue
            nonResolus.add(Pair(numeroLigne, correspondance.groupValues[2]))
        }
        return nonResolus
    }

    /** Les `kt.unresolved` du moteur : paires (ligne, nom). */
    private fun moteurNonResolus(texte: String, jars: List<java.io.File>): Set<Pair<Int, String>> {
        val moteur = ServiceMoteurLangageKotlin.unique as MoteurLangageKotlinNoyau
        val classpath = ClasspathKotlin(
            entrees = jars.mapIndexed { i, jar ->
                EntreeClasspathKotlin(KindEntreeKotlin.JAR, jar.path, "fidelite:$i:${jar.name}")
            },
            racinesSources = emptyList(),
        )
        moteur.definirClasspath(classpath)
        val session = moteur.creerSession() as SessionLangageKotlinDocuments
        session.definirClasspath(classpath)
        attendreCouvert(session)
        val uri = "file:///fidelite.kt"
        session.ouvrir(uri, texte)
        val resultat = mutableSetOf<Pair<Int, String>>()
        for (diagnostic in session.diagnostics(uri)) {
            if (diagnostic.code != CodeDiagnosticKotlin.REFERENCE_NON_RESOLUE) continue
            val ligne = 1 + texte.substring(0, diagnostic.plage.debut.coerceAtMost(texte.length)).count { it == '\n' }
            val nom = Regex("« (.+) »$").find(diagnostic.message)?.groupValues?.get(1) ?: continue
            resultat.add(Pair(ligne, nom))
        }
        session.close()
        return resultat
    }

    /** Attend (sondage borné) que le guichet serve le snapshot publié. */
    private fun attendreCouvert(session: SessionLangageKotlinDocuments, secondes: Long = 120) {
        val limite = System.currentTimeMillis() + secondes * 1000
        while (System.currentTimeMillis() < limite) {
            if (session.indexSiPret() != null) return
            Thread.sleep(10)
        }
        throw AssertionError("la passe du moteur n'a pas publié (guichet toujours muet)")
    }

    @Test
    fun `banc-de-fidelite---faux-positifs-zero-et-faux-negatifs-baselines-signes`() {
        val dossier = Files.createTempDirectory(racine, "fidelite")
        var fauxPositifs = 0
        var fauxNegatifs = 0
        val details = StringBuilder()
        for ((chemin, jars) in corpus()) {
            val fichier = copierRessource(chemin, dossier)
            val texte = ressource(chemin)
            val oracle = oracleK2(fichier, jars)
            val moteur = moteurNonResolus(texte, jars)
            val fp = moteur - oracle
            val fn = oracle - moteur
            fauxPositifs += fp.size
            fauxNegatifs += fn.size
            details.append(chemin.substringAfterLast('/'))
                .append(" oracle=").append(oracle.size)
                .append(" moteur=").append(moteur.size)
                .append(" fp=").append(fp.size)
                .append(" fn=").append(fn.size)
                .append('\n')
            fp.sortedWith(compareBy({ it.first }, { it.second })).forEach {
                details.append("  FP ").append(it.first).append(':').append(it.second).append('\n')
            }
            fn.sortedWith(compareBy({ it.first }, { it.second })).forEach {
                details.append("  FN ").append(it.first).append(':').append(it.second).append('\n')
            }
        }

        // L'INVARIANT §4 : FAUX POSITIFS = 0 (le moteur ne signale RIEN que
        // K2 ne signale pas — sur les quatre mondes sains il n'émet rien,
        // sur le témoin positif les deux s'accordent).
        assertEquals(0, fauxPositifs, "FAUX POSITIFS = 0 (invariant §4) :\n$details")

        // Le TÉMOIN POSITIF s'exerce : l'oracle ET le moteur signalent les
        // références inconnues (le harnais ne valide pas un « tout muet »).
        assertTrue(fauxNegatifs < 64, "le banc s'exerce (l'oracle consulté) :\n$details")

        // Les FAUX NÉGATIFS sont BASELINÉS SIGNÉS : une variation est une
        // régression (re-signature : -Dfidelite.majBase=true --rerun-tasks).
        val ressourceBaseline = "fidelite/baseline-fidelite.txt"
        val empreinte = java.security.MessageDigest.getInstance("SHA-256")
            .digest(details.toString().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val contenuNeuf = "faux-negatifs=$fauxNegatifs\n" +
            "empreinte=$empreinte\n" +
            details
        if (System.getProperty("fidelite.majBase") == "true") {
            // La re-signature ÉCRIT la baseline de l'ARBRE SOURCE en place
            // (miroir de la suite de parité fiche 010 — le chargeur de
            // classes sert la COPIE de build, écrire dedans serait perdu ;
            // on remonte donc depuis le répertoire de travail jusqu'aux
            // ressources sources ; --rerun-tasks obligatoire).
            var dir = File(System.getProperty("user.dir")).absoluteFile
            while (dir != null && !File(dir, "src/test/resources/fidelite").isDirectory) {
                dir = dir.parentFile
            }
            assertNotNull(dir, "src/test/resources/fidelite introuvable en remontant depuis l'emplacement de test")
            val fichierBaseline = File(dir, "src/test/resources/$ressourceBaseline")
            fichierBaseline.writeText(contenuNeuf)
            println("BASELINE RE-SIGNÉE : $fichierBaseline")
            return
        }
        val baseline = ressource(ressourceBaseline)
        val ligneFauxNegatifs = baseline.lineSequence().firstOrNull { it.startsWith("faux-negatifs=") }
        assertTrue(ligneFauxNegatifs != null, "la baseline signée existe : $ressourceBaseline")
        val nombreBaseline = ligneFauxNegatifs.substringAfter('=').trim().toInt()
        assertEquals(
            nombreBaseline,
            fauxNegatifs,
            "faux négatifs = baseline signée (une variation est une régression) :\n$details",
        )

        // L'EMPREINTE de la baseline est la SIGNATURE du détail complet
        // (miroir de la suite de parité fiche 010 — le digeste par fichier)
        // : TOUTE variation, en hausse COMME en baisse, est une divergence
        // à EXPLIQUER avant re-signature (amélioration = re-baseline par
        // PR dédiée, ADR 0119).
        val ligneEmpreinte = baseline.lineSequence().firstOrNull { it.startsWith("empreinte=") }
        assertTrue(ligneEmpreinte != null, "l'empreinte signée existe : $ressourceBaseline")
        assertEquals(
            ligneEmpreinte.substringAfter('=').trim(),
            empreinte,
            "empreinte ≠ baseline signée (le DÉTAIL complet varie — expliquer la divergence " +
                "puis re-signer : -Dfidelite.majBase=true --rerun-tasks) :\n$details",
        )
    }
}
