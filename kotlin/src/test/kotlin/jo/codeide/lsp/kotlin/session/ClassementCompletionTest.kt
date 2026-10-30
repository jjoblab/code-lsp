package jo.codeide.lsp.kotlin.session

import java.nio.file.Files
import java.nio.file.Path
import jo.codeide.lsp.kotlin.api.ArrangementListeKotlin
import jo.codeide.lsp.kotlin.api.ChaineClassementKotlin
import jo.codeide.lsp.kotlin.api.CompteurUsageKotlin
import jo.codeide.lsp.kotlin.api.ContexteClassementKotlin
import jo.codeide.lsp.kotlin.api.CorrespondanceGradueeKotlin
import jo.codeide.lsp.kotlin.api.GenreContexteKotlin
import jo.codeide.lsp.kotlin.api.NaturePropositionKotlin
import jo.codeide.lsp.kotlin.api.PeseurAlphabetiqueKotlin
import jo.codeide.lsp.kotlin.api.PropositionKotlin
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Les contrats de la CHAÎNE DE CLASSEMENT (étage 3 vague 3c — ADR 0113,
 * miroir de l'ADR 0012 Java) : grades de correspondance, priorités de
 * nature par contexte, liftShorter, départage alphabétique déterministe,
 * GEL des têtes (contrat « zéro saut de liste »), trim honnête (plafond +
 * total réel), compteur d'usage local (cycle de vie), injection de la
 * chaîne naïve (le comparatif du banc de qualité).
 *
 * Ces tests ne sont PAS une fiche du cahier : ils verrouillent le
 * MÉCANISME livré en 3c (les fiches 043/045 verrouillent la parité).
 */
@Suppress("LargeClass")
class ClassementCompletionTest {

    /** La racine temporaire du compteur d'usage. */
    @TempDir
    lateinit var racine: Path

    private fun proposition(libelle: String, nature: NaturePropositionKotlin = NaturePropositionKotlin.FONCTION) =
        PropositionKotlin(libelle = libelle, nature = nature)

    // ── Correspondance graduée ─────────────────────────────────────────

    @Test
    fun `grade---exact,-prefixe,-insensible,-hump,-substring,-hors`() {
        assertEquals(
            jo.codeide.lsp.kotlin.api.GradeCorrespondanceKotlin.EXACT,
            CorrespondanceGradueeKotlin.grade("compte", "compte"),
            "la frappe complète est EXACT",
        )
        assertEquals(
            jo.codeide.lsp.kotlin.api.GradeCorrespondanceKotlin.PREFIXE,
            CorrespondanceGradueeKotlin.grade("compter", "compte"),
            "le début sensible à la casse est PREFIXE",
        )
        assertEquals(
            jo.codeide.lsp.kotlin.api.GradeCorrespondanceKotlin.PREFIXE_INSENSIBLE,
            CorrespondanceGradueeKotlin.grade("Compte", "compte"),
            "le début sans la casse est PREFIXE_INSENSIBLE",
        )
        assertEquals(
            jo.codeide.lsp.kotlin.api.GradeCorrespondanceKotlin.HUMP,
            CorrespondanceGradueeKotlin.grade("TextView", "TV"),
            "les bossages épousés sont HUMP",
        )
        assertEquals(
            jo.codeide.lsp.kotlin.api.GradeCorrespondanceKotlin.SUBSTRING,
            CorrespondanceGradueeKotlin.grade("requestLayout", "questL"),
            "l'intérieur est SUBSTRING",
        )
        assertEquals(
            jo.codeide.lsp.kotlin.api.GradeCorrespondanceKotlin.HORS,
            CorrespondanceGradueeKotlin.grade("append", "requ"),
            "aucune correspondance est HORS",
        )
        assertEquals(
            jo.codeide.lsp.kotlin.api.GradeCorrespondanceKotlin.PREFIXE,
            CorrespondanceGradueeKotlin.grade("nimporte", ""),
            "le préfixe vide est neutre (PREFIXE)",
        )
    }

    // ── La chaîne standard ─────────────────────────────────────────────

    @Test
    fun `chaine-standard---correspondance-dominante-puis-nature-puis-longueur`() {
        val arrangement = ArrangementListeKotlin(ChaineClassementKotlin())
        val candidates = listOf(
            proposition("companion", NaturePropositionKotlin.MOT_CLE),
            proposition("compteRendu", NaturePropositionKotlin.VARIABLE),
            proposition("compte", NaturePropositionKotlin.VARIABLE),
            proposition("compterTotal", NaturePropositionKotlin.FONCTION),
        )
        val arrangementListe = arrangement.arranger(
            cleContexte = "cle",
            candidates = candidates,
            contexte = ContexteClassementKotlin(GenreContexteKotlin.NOM, prefixe = "compte"),
        )
        assertEquals(
            listOf("compte", "compteRendu", "compterTotal", "companion"),
            arrangementListe.propositions.map { it.libelle },
            "EXACT d'abord, puis nature (variable avant mot-clé), puis longueur (liftShorter)",
        )
    }

    @Test
    fun `chaine-standard---nature-dependante-du-contexte-membre`() {
        val arrangement = ArrangementListeKotlin(ChaineClassementKotlin())
        val candidates = listOf(
            proposition("append", NaturePropositionKotlin.FONCTION),
            proposition("text", NaturePropositionKotlin.PROPRIETE),
        )
        val liste = arrangement.arranger(
            cleContexte = "cle",
            candidates = candidates,
            contexte = ContexteClassementKotlin(GenreContexteKotlin.MEMBRE, prefixe = ""),
        )
        assertEquals(
            listOf("text", "append"),
            liste.propositions.map { it.libelle },
            "en contexte membre, la propriété précède la fonction",
        )
    }

    @Test
    fun `chaine-standard---nature-dependante-du-contexte-import`() {
        val arrangement = ArrangementListeKotlin(ChaineClassementKotlin())
        val candidates = listOf(
            proposition("TextView", NaturePropositionKotlin.CLASSE),
            proposition("widget", NaturePropositionKotlin.PAQUET),
        )
        val liste = arrangement.arranger(
            cleContexte = "cle",
            candidates = candidates,
            contexte = ContexteClassementKotlin(GenreContexteKotlin.IMPORT, prefixe = ""),
        )
        assertEquals(
            listOf("widget", "TextView"),
            liste.propositions.map { it.libelle },
            "en contexte d'import, le paquet domine le type (miroir fiche 008 Java)",
        )
    }

    @Test
    fun `chaine-standard---departage-alphabetique-deterministe`() {
        val arrangement = ArrangementListeKotlin(ChaineClassementKotlin())
        val candidates = listOf(
            proposition("abc", NaturePropositionKotlin.MOT_CLE),
            proposition("xyz", NaturePropositionKotlin.MOT_CLE),
            proposition("mno", NaturePropositionKotlin.MOT_CLE),
        )
        val liste = arrangement.arranger(
            cleContexte = "cle",
            candidates = candidates,
            contexte = ContexteClassementKotlin(GenreContexteKotlin.NOM, prefixe = ""),
        )
        assertEquals(
            listOf("abc", "mno", "xyz"),
            liste.propositions.map { it.libelle },
            "les égaux se départagent alphabétiquement (ordre total déterministe)",
        )
    }

    // ── Le gel (contrat « zéro saut de liste ») ────────────────────────

    @Test
    fun `gel---une-tete-gelee-ne-descend-jamais-quand-le-prefixe-saffine`() {
        val arrangement = ArrangementListeKotlin(ChaineClassementKotlin())
        val candidates = listOf(
            proposition("compte"),
            proposition("compteRendu"),
            proposition("compter"),
            proposition("compteTotal"),
            proposition("compteAnnexe"),
            proposition("compterTotal"),
            proposition("compteActif"),
            proposition("compteBase"),
            proposition("compteCle"),
            proposition("compteDelta"),
        )
        // Frappe 1 : "compte" — la tête se gèle.
        val premiere = arrangement.arranger(
            cleContexte = "session|NOM|42",
            candidates = candidates,
            contexte = ContexteClassementKotlin(GenreContexteKotlin.NOM, prefixe = "compte"),
        )
        // Frappe 2 : "compter" — le préfixe s'affine, les items disparaissent
        // (filtre simulé : le client retire les non-correspondants).
        val deuxieme = arrangement.arranger(
            cleContexte = "session|NOM|42",
            candidates = candidates.filter { it.libelle.startsWith("compter") },
            contexte = ContexteClassementKotlin(GenreContexteKotlin.NOM, prefixe = "compter"),
        )
        val tetePremiere = premiere.propositions.take(ArrangementListeKotlin.GEL_MAX).map { it.libelle }
        val teteDeuxieme = deuxieme.propositions.map { it.libelle }
        for ((rang, libelle) in tetePremiere.withIndex()) {
            if (libelle !in teteDeuxieme) continue // ne survit pas au filtre : légal
            val nouveauRang = teteDeuxieme.indexOf(libelle)
            assertTrue(
                nouveauRang <= rang,
                "« $libelle » rang $rang → $nouveauRang : une tête gelée ne peut que MONTER (disparition des items au-dessus), jamais descendre",
            )
        }
    }

    @Test
    fun `gel---un-nouveau-venu-ne-deplace-pas-une-tete-gelee`() {
        val arrangement = ArrangementListeKotlin(ChaineClassementKotlin())
        // Frappe 1 : une seule candidate.
        val premiere = arrangement.arranger(
            cleContexte = "cle",
            candidates = listOf(proposition("compte")),
            contexte = ContexteClassementKotlin(GenreContexteKotlin.NOM, prefixe = "c"),
        )
        assertEquals(listOf("compte"), premiere.propositions.map { it.libelle })
        // Frappe 2 : trois candidates dont deux nouvelles qui BATTRAIENT
        // "compte" à l'ordre de la chaîne (courtes, alphabétiquement
        // avant) — le gel maintient "compte" à SON rang (premier).
        val deuxieme = arrangement.arranger(
            cleContexte = "cle",
            candidates = listOf(proposition("compte"), proposition("cb"), proposition("ca")),
            contexte = ContexteClassementKotlin(GenreContexteKotlin.NOM, prefixe = "c"),
        )
        assertEquals(
            "compte",
            deuxieme.propositions.first().libelle,
            "le gel fige la tête déjà affichée : le nouveau venu ne la déplace pas (ordre brut : ${deuxieme.propositions.map { it.libelle }})",
        )
    }

    @Test
    fun `gel---cles-distinctes,-gels-independants`() {
        val arrangement = ArrangementListeKotlin(ChaineClassementKotlin())
        arrangement.arranger(
            cleContexte = "doc-a|NOM|10",
            candidates = listOf(proposition("alpha")),
            contexte = ContexteClassementKotlin(GenreContexteKotlin.NOM, prefixe = "a"),
        )
        val autre = arrangement.arranger(
            cleContexte = "doc-b|NOM|10",
            candidates = listOf(proposition("alpha"), proposition("beta")),
            contexte = ContexteClassementKotlin(GenreContexteKotlin.NOM, prefixe = ""),
        )
        assertEquals(
            listOf("beta", "alpha"),
            autre.propositions.map { it.libelle },
            "sans gel sous cette clé, l'ordre de la chaîne s'applique (liftShorter : beta 4 lettres < alpha 5)",
        )
    }

    // ── Le trim honnête ────────────────────────────────────────────────

    @Test
    fun `trim---plafond-et-total-reel-honnêtes`() {
        val arrangement = ArrangementListeKotlin(ChaineClassementKotlin())
        val candidates = (1..200).map { proposition("item%03d".format(it)) }
        val liste = arrangement.arranger(
            cleContexte = "cle",
            candidates = candidates,
            contexte = ContexteClassementKotlin(GenreContexteKotlin.NOM, prefixe = ""),
            limite = ArrangementListeKotlin.LIMITE_AFFICHAGE,
        )
        assertEquals(ArrangementListeKotlin.LIMITE_AFFICHAGE, liste.propositions.size, "la liste est plafonnée")
        assertEquals(200, liste.totalDisponible, "le total réel voyage (le plafond ne ment pas)")
        assertTrue(liste.tronquee, "la troncature est signalée")
    }

    @Test
    fun `trim-membre---la-chaine-du-recepteur-est-servie-entiere`() {
        val arrangement = ArrangementListeKotlin(ChaineClassementKotlin())
        val candidates = (1..300).map { proposition("membre%03d".format(it)) }
        val liste = arrangement.arranger(
            cleContexte = "cle",
            candidates = candidates,
            contexte = ContexteClassementKotlin(GenreContexteKotlin.MEMBRE, prefixe = ""),
            limite = ArrangementListeKotlin.LIMITE_MEMBRE,
        )
        assertEquals(300, liste.propositions.size, "un espace membre borné (≤ 512) se sert ENTIER (frontière RPC)")
        assertEquals(300, liste.totalDisponible)
        assertTrue(!liste.tronquee)
    }

    // ── Le compteur d'usage (opt-in) ───────────────────────────────────

    @Test
    fun `compteur-usage---increment,-compte-,-purge-(cycle-de-vie)`() {
        val fichier = racine.resolve("etat/usage.properties")
        val compteur = CompteurUsageKotlin(fichier)
        assertEquals(0, compteur.compter("compte"), "inconnu : compte nul")
        compteur.incrementer("compte")
        compteur.incrementer("compte")
        compteur.incrementer("text")
        assertEquals(2, compteur.compter("compte"))
        assertEquals(1, compteur.compter("text"))
        // Le fichier d'état est écrit (persistance locale).
        assertTrue(Files.isRegularFile(fichier), "l'état persiste localement")
        // Une NOUVELLE instance relit le même fichier.
        val relue = CompteurUsageKotlin(fichier)
        assertEquals(2, relue.compter("compte"), "l'état se recharge")
        // Purge : comptes vidés, fichier supprimé.
        compteur.purger()
        assertEquals(0, compteur.compter("compte"))
        assertTrue(!Files.exists(fichier), "la purge supprime le fichier d'état")
    }

    // ── La chaîne naïve (le comparatif du banc) ────────────────────────

    @Test
    fun `chaine-naive---alphabetique-seule-(le-statu-quo-sans-design)`() {
        val naive = ChaineClassementKotlin(listOf(PeseurAlphabetiqueKotlin()))
        val arrangement = ArrangementListeKotlin(naive)
        val candidates = listOf(
            proposition("zeta", NaturePropositionKotlin.VARIABLE),
            proposition("alpha", NaturePropositionKotlin.MOT_CLE),
            proposition("milieu", NaturePropositionKotlin.FONCTION),
        )
        val liste = arrangement.arranger(
            cleContexte = "cle",
            candidates = candidates,
            contexte = ContexteClassementKotlin(GenreContexteKotlin.NOM, prefixe = ""),
        )
        assertEquals(
            listOf("alpha", "milieu", "zeta"),
            liste.propositions.map { it.libelle },
            "la chaîne naïve trie alphabétiquement SEULE (la baseline du banc)",
        )
    }
}
