// CodeIDE — banc de cohérence P5 : cycle quick-fix (module :kotlin, vague
// 0). Code NEUF jjoblab (banc de MESURE).
package jo.codeide.lsp.kotlin.coherence

import java.nio.file.Files
import jo.codeide.lsp.kotlin.api.GenreActionKotlin
import jo.codeide.lsp.kotlin.api.SeveriteKotlin
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * P5 — Cycle quick-fix (banc de cohérence, vague 0 — miroir de la P5 Java).
 *
 * ## Ce que la propriété mesure
 *
 * Pour chaque action rapide proposée SUR un diagnostic : appliquer le plan
 * (les éditions textuelles du plan, en ordre inverse du document) doit
 * (1) FAIRE DISPARAÎTRE le diagnostic cible et (2) n'introduire AUCUNE
 * nouvelle erreur — le document corrigé reste sain. C'est le contrat des
 * familles livrées (fiches 016/020 du cahier : chaque famille re-dérive
 * SES diagnostics avec les MÊMES prédicats que la passe).
 *
 * ## Documents sonde
 *
 * - **accès non sûr** : `val t: String? = "x"` puis `t.length` →
 *   `kt.unsafeNullable` + les trois correctifs (`?.`, `!!`, `if != null`) ;
 * - **membres manquants** : une classe qui n'implémente pas l'interface
 *   → le correctif de stubs (rapport seul : le périmètre des membres
 *   attendus dépend de la clôture d'héritage).
 *
 * ## R5 — rapport détaillé, assertion sur la famille certaine
 *
 * La famille « accès non sûr » est certaine (3 correctifs documentés) :
 * l'assertion exige qu'AU MOINS UN plan appliqué élimine l'erreur sans en
 * introduire. Le reste est imprimé (chaque plan, son effet).
 */
class P5CycleQuickFixTest {

    /** Applique des éditions (offsets du texte courant) et rend le texte résultat. */
    private fun appliquer(texte: String, editions: List<Triple<Int, Int, String>>): String {
        val builder = StringBuilder(texte)
        for ((debut, fin, remplacement) in editions.sortedByDescending { it.first }) {
            builder.replace(debut, fin, remplacement)
        }
        return builder.toString()
    }

    /** Compte les ERREURS d'un document ouvert dans la session. */
    private fun erreurs(session: jo.codeide.lsp.kotlin.session.SessionLangageKotlinDocuments, uri: String, texte: String): List<String> =
        session.diagnostics(uri).filter { it.severite == SeveriteKotlin.ERREUR }.map { "${it.code.code}:${it.message}" }

    @Test
    fun `appliquer-chaque-quick-fix-fait-disparaître-le-diagnostic-sans-nouvelle-erreur`() {
        val racine = Files.createTempDirectory("p5-quickfix")
        val jars = SocleCoherence.assurerPlancherStdlib(SocleCoherence.jarsAutoCorpus())
        val session = SocleCoherence.monterSession(listOf(racine), jars)

        // Document sonde 1 — accès non sûr (famille CERTAINE, assertion).
        val docSur = racine.resolve("acces-non-sur.kt")
        val texteSur = "package p5\n\nfun zzMain() {\n    val t: String? = \"x\"\n    t.length\n}\n"
        Files.writeString(docSur, texteSur)
        val uriSur = docSur.toUri().toString()
        session.rafraichirSources()
        session.ouvrir(uriSur, texteSur)
        val cible = session.diagnostics(uriSur).firstOrNull { it.code.code == "kt.unsafeNullable" }
        assertTrue(cible != null, "le document sonde doit porter un kt.unsafeNullable (t.length sur String?)")
        val avant = erreurs(session, uriSur, texteSur)
        assertTrue(avant.isNotEmpty(), "l'état initial porte l'erreur cible")
        var auMoinsUnCycleVert = false
        for (plan in session.actions(uriSur, cible.plage.debut).filter { it.genre == GenreActionKotlin.RAPIDE }) {
            val editions = plan.editions.flatMap { fichier -> fichier.editions.map { Triple(it.plage.debut, it.plage.fin, it.texte) } }
            if (editions.isEmpty()) {
                println("P5 [${plan.titre}] : plan sans édition (ignoré)")
                continue
            }
            val corrige = appliquer(texteSur, editions)
            session.modifier(uriSur, corrige)
            val apres = erreurs(session, uriSur, corrige)
            session.modifier(uriSur, texteSur)
            val cibleDisparue = apres.none { it.startsWith("kt.unsafeNullable") }
            println("P5 [${plan.titre}]: cibleDisparue=$cibleDisparue, erreursAvant=${avant.size}, erreursApres=${apres.size} $apres")
            if (cibleDisparue && apres.isEmpty()) auMoinsUnCycleVert = true
        }
        session.fermer(uriSur)
        assertTrue(
            auMoinsUnCycleVert,
            "P5 : au moins un correctif rapide appliqué doit éliminer kt.unsafeNullable sans aucune nouvelle erreur",
        )

        // Document sonde 2 — membres manquants (RAPPORT SEUL).
        val docMembres = racine.resolve("membres-manquants.kt")
        val texteMembres = "package p5\n\ninterface ZzService {\n    fun zzCalculer(): Int\n}\n\nclass ZzImpl : ZzService\n"
        Files.writeString(docMembres, texteMembres)
        val uriMembres = docMembres.toUri().toString()
        session.rafraichirSources()
        session.ouvrir(uriMembres, texteMembres)
        val plansMembres = session.actions(uriMembres, texteMembres.indexOf("ZzImpl") + 2)
        println("P5 membres-manquants: ${plansMembres.size} plan(s) — ${plansMembres.joinToString { it.titre }}")
        for (plan in plansMembres.filter { it.genre == GenreActionKotlin.RAPIDE }) {
            val editions = plan.editions.flatMap { f -> f.editions.map { Triple(it.plage.debut, it.plage.fin, it.texte) } }
            if (editions.isEmpty()) continue
            val corrige = appliquer(texteMembres, editions)
            session.modifier(uriMembres, corrige)
            println("P5 membres [${plan.titre}]: erreursApres=${erreurs(session, uriMembres, corrige).size}")
            session.modifier(uriMembres, texteMembres)
        }
        session.fermer(uriMembres)
        session.close()
    }
}
