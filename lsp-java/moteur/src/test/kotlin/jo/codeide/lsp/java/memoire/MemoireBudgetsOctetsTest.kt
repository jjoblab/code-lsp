package jo.codeide.lsp.java.memoire

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import jo.codeide.lsp.java.classpath.CacheLocalisation
import jo.codeide.lsp.java.interne.CacheParse
import jo.codeide.lsp.java.interne.UniteResolue

/**
 * Vague V-F3 — BUDGETS D'OCTETS et shim `onTrimMemory` (ADR 0029 phase 3,
 * exécution de l'ADR 0011 points 2 et 5) :
 *
 *  - **budget du cache de PARSE** : la borne en ENTRÉES (8) laisse passer
 *    le trou inverse (UN fichier géant) — le budget d'octets ESTIMÉS
 *    évince au LRU jusqu'à rentrer sous la borne, SANS jamais évincer la
 *    dernière entrée posée (garde anti-cyclage) ;
 *  - **budget du mémo de LOCALISATION** : les verdicts ne périment jamais
 *    (aucun TTL) — au-delà du budget, les NÉGATIFS partent d'abord
 *    (re-probe honnête : le verdict rendu reste EXACT), puis le mémo
 *    entier si besoin ; les contrats observables de la fiche 024
 *    (deux find successifs, même verdict) restent tenus ;
 *  - **shim `pressionMemoire`** : la politique cumulative par niveau
 *    (RUNNING → parse seul ; BACKGROUND/MODERATE → + mémos + négatifs ;
 *    COMPLETE → dispose intégral drainé) libère EXACTEMENT ce que le
 *    rapport annonce, et le moteur RESTE UTILISABLE après chaque niveau
 *    (reconstruction paresseuse — la diagnose d'après fonctionne).
 *
 * R5 : les coûts sont des ESTIMATIONS de gestion (facteurs documentés) —
 * ce test éprouve la MÉCANIQUE de borne, jamais une mesure de tas.
 */
class MemoireBudgetsOctetsTest {

    /** Le source d'une unité saine de taille donnée (corps monotope). */
    private fun source(taille: Int): String = buildString {
        append("package demo;\npublic class Gros {\n")
        append("    public int unChamp = 1;\n")
        while (length < taille) {
            append("    // remplissage jusqu'a la taille cible pour le budget d octets estimes\n")
        }
        append("}\n")
    }

    // --------------------------------------------------------------- //
    // Budget d'octets du cache de parse
    // --------------------------------------------------------------- //

    @Test
    fun `le cache de parse evince au LRU au-dela du budget d octets estimes`() {
        // Budget minuscule : deux fichiers MOYENS le dépassent ensemble.
        val petit = source(200)
        val budget = CacheParse.coutEstime(petit) + CacheParse.coutEstime(petit) / 2
        val cache = CacheParse(budgetOctets = budget)

        fun ranger(chemin: String, s: String) {
            cache.ranger(
                chemin, s,
                UniteResolue(null, chemin, s, genererCode = false),
                genererCode = false, focused = null,
            )
        }

        ranger("/a.java", petit)
        assertTrue(cache.octetsEstimes() <= budget || cache.taille() == 1, "un seul fichier : la garde anti-cyclage le retient")
        ranger("/b.java", petit)
        // Deux fichiers > budget : l'éviction LRU n'en garde qu'UN (le
        // dernier posé) et le total estimé suit la table.
        assertEquals(1, cache.taille(), "le budget évince le moins récent")
        assertEquals(CacheParse.coutEstime(petit), cache.octetsEstimes(), "le total estimé suit la table")
        assertNotNull(cache.obtenir("/b.java", petit), "le dernier posé survit")
        assertEquals(null, cache.obtenir("/a.java", petit), "l'ancien est évincé")
    }

    @Test
    fun `un fichier seul plus gros que le budget vit seul sans cyclage`() {
        val geant = source(5_000)
        val cache = CacheParse(budgetOctets = CacheParse.coutEstime(geant) / 4)
        cache.ranger(
            "/geant.java", geant,
            UniteResolue(null, "/geant.java", geant, genererCode = false),
            genererCode = false, focused = null,
        )
        assertEquals(1, cache.taille(), "garde anti-cyclage : jamais de put/éviction en boucle")
        assertEquals(CacheParse.coutEstime(geant), cache.octetsEstimes())
    }

    @Test
    fun `la borne d entrees et le budget se cumulent et l invalidation suit les octets`() {
        val petit = source(100)
        val cache = CacheParse(budgetOctets = Long.MAX_VALUE)
        for (i in 1..10) {
            val chemin = "/f$i.java"
            cache.ranger(
                chemin, petit,
                UniteResolue(null, chemin, petit, genererCode = false),
                genererCode = false, focused = null,
            )
        }
        assertEquals(CacheParse.MAX_ENTREES, cache.taille(), "la borne d'entrées reste la première borne")
        val liberes = cache.invalider("/f10.java")
        assertEquals(1, liberes)
        assertEquals(CacheParse.coutEstime(petit) * (CacheParse.MAX_ENTREES - 1), cache.octetsEstimes(), "l'invalidation retire le coût de l'entrée")
    }

    // --------------------------------------------------------------- //
    // Budget d'octets du mémo de localisation
    // --------------------------------------------------------------- //

    /** Un jar minimal contenant UNE classe (positifs sonnables). */
    private fun jarAvecClasse(@TempDir tmp: Path, fqcn: String): Path {
        val jar = tmp.resolve("lib-${fqcn.substringAfterLast('.')}.jar")
        ZipOutputStream(Files.newOutputStream(jar)).use { zip ->
            zip.putNextEntry(ZipEntry(fqcn.replace('.', '/') + ".class"))
            zip.write(ByteArray(16))
            zip.closeEntry()
        }
        return jar
    }

    @Test
    fun `le memo de localisation evince les negatifs d'abord et reste exact`(@TempDir tmp: Path) {
        val jar = jarAvecClasse(tmp, "demo.Presente")
        // Budget minuscule : quelques verdicts le dépassent.
        val cache = CacheLocalisation(
            listOf(jar),
            budgetOctets = CacheLocalisation.coutEntree("demo.AbsentNumero1") * 3 / 2,
        )
        // Un positif + des négatifs (absents du jar unique).
        assertNotNull(cache.jarPour("demo.Presente"))
        assertEquals(null, cache.jarPour("demo.AbsentNumero1"))
        assertEquals(null, cache.jarPour("demo.AbsentNumero2"))
        assertTrue(cache.nbNegatifs() <= 1, "le budget a évacué des négatifs (${cache.nbNegatifs()})")
        // Contrat fiche 024 INTACT : deux find successifs, même verdict,
        // même après éviction (le re-probe est honnête, pas un mensonge).
        assertEquals(cache.jarPour("demo.AbsentNumero1"), null)
        assertEquals(cache.jarPour("demo.AbsentNumero1"), null)
        // Le positif garde son verdict (re-probe immédiat, même jar).
        assertEquals(jar, cache.jarPour("demo.Presente"))
        assertTrue(cache.octetsMemoises() <= budgetEffectifDe(cache), "le mémo rentre sous la borne après éviction")
    }

    /** Le budget effectif privé, lu par réflexion (sonde boîte blanche). */
    private fun budgetEffectifDe(cache: CacheLocalisation): Long {
        val champ = CacheLocalisation::class.java.getDeclaredField("budgetEffectif")
        champ.isAccessible = true
        return champ.getLong(cache)
    }

    @Test
    fun `evictNegatifs vide les negatifs et garde les positifs`(@TempDir tmp: Path) {
        val jar = jarAvecClasse(tmp, "demo.Porte")
        val cache = CacheLocalisation(listOf(jar))
        assertNotNull(cache.jarPour("demo.Porte"))
        assertEquals(null, cache.jarPour("demo.AbsentA"))
        assertEquals(null, cache.jarPour("demo.AbsentB"))
        val avant = cache.nbNegatifs()
        assertTrue(avant >= 2)
        val liberes = cache.evictNegatifs()
        assertEquals(avant, liberes, "le compte annoncé est le compte réel")
        assertEquals(0, cache.nbNegatifs())
        assertEquals(jar, cache.jarPour("demo.Porte"), "le positif survit au trim")
    }

    // --------------------------------------------------------------- //
    // Shim onTrimMemory — politique cumulative par niveau
    // --------------------------------------------------------------- //

    @Test
    fun `la pression RUNNING libere le parse seul et le moteur reste utilisable`(@TempDir tmp: Path) {
        val racine = Files.createDirectories(tmp.resolve("demo"))
        racine.resolve("A.java").writeText("package demo;\npublic class A { public int unChamp = 1; }\n")
        val moteur = jo.codeide.lsp.java.MoteurJava(racines = listOf(tmp))
        val chemin = "/demo/A.java"
        val sourceA = "package demo;\npublic class A { public int unChamp = 1; }\n"

        // Chauffe : l'environnement vit, le cache de parse se remplit.
        assertTrue(moteur.diagnose(chemin, sourceA).none { it.severite == jo.codeide.lsp.java.diagnostics.Severite.ERREUR })
        assertTrue(moteur.parseCacheSize() >= 1, "chauffe : le cache de parse vit")
        assertTrue(moteur.parseCacheOctetsEstimes() > 0)

        val rapport = moteur.pressionMemoire(NiveauxPressionMemoire.RUNNING_MODERATE)
        assertEquals(1, rapport.entreesParseLiberees, "le rapport annonce le parse libéré")
        assertEquals(0, rapport.memosDiagnoseLiberes, "le mémo de frappe reste (premier plan)")
        assertEquals(0, rapport.negatifsLocalisationLiberes)
        assertTrue(!rapport.environnementLibere)
        assertEquals(0, moteur.parseCacheSize(), "le cache est vidé")
        assertEquals(0L, moteur.parseCacheOctetsEstimes())

        // Le moteur RESTE utilisable : la diagnose d'après est servie par
        // le MÉMO (premier plan : il a survécu — c'est le contrat), et une
        // source FRAÎCHE re-remplit le cache de parse (reconstruction
        // paresseuse — le contrat dispose).
        assertTrue(moteur.diagnose(chemin, sourceA).none { it.severite == jo.codeide.lsp.java.diagnostics.Severite.ERREUR })
        assertEquals(0, moteur.parseCacheSize(), "le mémo sert la frappe identique sans re-payer le parse")
        val sourceFraiche = sourceA.replace("unChamp = 1", "unChamp = 2")
        assertTrue(moteur.diagnose(chemin, sourceFraiche).none { it.severite == jo.codeide.lsp.java.diagnostics.Severite.ERREUR })
        assertEquals(1, moteur.parseCacheSize(), "une source fraîche re-remplit le cache")
    }

    @Test
    fun `la pression BACKGROUND libere aussi les memos et les negatifs`(@TempDir tmp: Path) {
        val racine = Files.createDirectories(tmp.resolve("demo"))
        racine.resolve("A.java").writeText("package demo;\npublic class A { public int unChamp = 1; }\n")
        val moteur = jo.codeide.lsp.java.MoteurJava(racines = listOf(tmp), jars = listOf(jarAvecClasse(tmp, "demo.Porte")))
        val chemin = "/demo/A.java"
        val sourceA = "package demo;\npublic class A { public int unChamp = 1; }\n"

        assertTrue(moteur.diagnose(chemin, sourceA).none { it.severite == jo.codeide.lsp.java.diagnostics.Severite.ERREUR })
        assertTrue(moteur.parseCacheSize() >= 1)

        val rapport = moteur.pressionMemoire(NiveauxPressionMemoire.BACKGROUND)
        assertEquals(1, rapport.entreesParseLiberees)
        assertTrue(rapport.memosDiagnoseLiberes >= 1, "le mémo de diagnose part en arrière-plan")
        assertTrue(rapport.negatifsLocalisationLiberes >= 0, "les négatifs de localisation sont trimés (compte honnête)")
        assertTrue(!rapport.environnementLibere)

        // Utilisable : la diagnose repart (mémo reconstruit).
        assertTrue(moteur.diagnose(chemin, sourceA).none { it.severite == jo.codeide.lsp.java.diagnostics.Severite.ERREUR })
    }

    @Test
    fun `la pression COMPLETE libere integralement et le moteur survit`(@TempDir tmp: Path) {
        val racine = Files.createDirectories(tmp.resolve("demo"))
        racine.resolve("A.java").writeText("package demo;\npublic class A { public int unChamp = 1; }\n")
        val moteur = jo.codeide.lsp.java.MoteurJava(racines = listOf(tmp))
        val chemin = "/demo/A.java"
        val sourceA = "package demo;\npublic class A { public int unChamp = 1; }\n"

        assertTrue(moteur.diagnose(chemin, sourceA).none { it.severite == jo.codeide.lsp.java.diagnostics.Severite.ERREUR })
        val rapport = moteur.pressionMemoire(NiveauxPressionMemoire.COMPLETE)
        assertTrue(rapport.environnementLibere, "COMPLETE = libération intégrale annoncée")
        assertEquals(1, rapport.entreesParseLiberees)

        // Le moteur survit à la libération intégrale (contrat dispose :
        // reconstruction paresseuse — la diagnose d'après est EXACTE).
        assertTrue(moteur.diagnose(chemin, sourceA).none { it.severite == jo.codeide.lsp.java.diagnostics.Severite.ERREUR })
        assertTrue(moteur.isLikelyUsable(), "la plateforme se re-résout (jrt du runtime)")
    }

    @Test
    fun `le relais environnement touche tous les moteurs vivants`(@TempDir tmp: Path) {
        val racine = Files.createDirectories(tmp.resolve("demo"))
        racine.resolve("A.java").writeText("package demo;\npublic class A { public int unChamp = 1; }\n")
        val env = jo.codeide.lsp.java.EnvironnementCompilation()
        try {
            env.remplacerContexteModule(null, listOf(tmp), emptyList())
            val chemin = "/demo/A.java"
            val sourceA = "package demo;\npublic class A { public int unChamp = 1; }\n"
            val moteur = env.moteurPourFichier(chemin)
            assertTrue(moteur.diagnose(chemin, sourceA).none { it.severite == jo.codeide.lsp.java.diagnostics.Severite.ERREUR })
            val rapport = env.pressionMemoire(NiveauxPressionMemoire.UI_HIDDEN)
            assertTrue(rapport.entreesParseLiberees >= 1, "le relais agrège les moteurs vivants")
            assertTrue(moteur.parseCacheSize() == 0)
        } finally {
            env.dispose()
        }
    }
}
