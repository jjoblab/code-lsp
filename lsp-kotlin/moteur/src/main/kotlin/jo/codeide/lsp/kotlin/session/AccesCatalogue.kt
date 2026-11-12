// CodeIDE — accès au CATALOGUE Gradle depuis le document .kts (module
// :kotlin, vague V-G — ADR 0130 : lecture disque SANS sync) : la
// complétion des SEGMENTS suivants d'un accès `libs.|`, la navigation
// vers l'entrée du catalogue (G5) et le diagnostic de clé inconnue
// (G6, preuve d'absence par parse). Code NEUF jjoblab (clean-room).
package jo.codeide.lsp.kotlin.session

import java.nio.file.Path
import jo.codeide.lsp.kotlin.api.DiagnosticKotlin
import jo.codeide.lsp.kotlin.api.EmplacementKotlin
import jo.codeide.lsp.kotlin.api.SeveriteKotlin
import jo.codeide.lsp.kotlin.api.CodeDiagnosticKotlin
import jo.codeide.lsp.kotlin.parse.NoeudArbre
import jo.codeide.lsp.kotlin.parse.kt.KtFichier
import jo.codeide.lsp.kotlin.parse.kt.PlageTexte
import org.jetbrains.kotlin.kmp.lexer.KtTokens
import org.jetbrains.kotlin.kmp.parser.KtNodeTypes

/**
 * Les accès `libs.…` du document `.kts` servis par le catalogue de
 * versions lu du DISQUE (ADR 0130) :
 *
 * - [segmentsAuPoint] (G4) : les SEGMENTS suivants d'un accès
 *   `libs.<préfixe>|` — complétion par groupes, alias exacts du TOML ;
 * - [definitionCatalogue] (G5) : la navigation vers la LIGNE de l'entrée
 *   (`version.ref` → l'entrée `[versions]` pointée) ;
 * - [diagnostics] (G6) : la clé INCONNUE du catalogue — preuve
 *   d'absence par parse (chaque segment d'un accès RESOLU n'existe pas
 *   comme ENTRÉE : seul l'ACCÈS COMPLET se prouve absent), jamais un
 *   diagnostic de version ou de conflit (indémontrables sans sync, G7).
 *
 * Le répertoire du catalogue : le PARENT du document `.kts` (module)
 * puis SA racine (`gradle/libs.versions.toml` de la racine du projet) ;
 * un catalogue absent rend TOUT muet (silence honnête, G4 bornes).
 */
internal object AccesCatalogue {

    /**
     * Les SEGMENTS suivants d'un accès `libs.<préfixe>` au point (G4).
     *
     * @param arbre l'arbre du document `.kts` ;
     * @param repertoireModule le répertoire du document (le catalogue s'y
     * cherche, puis à la racine remontée) ;
     * @param deplacement l'offset du caret (dans le préfixe après `libs.`) ;
     * @return les segments suivants (propositions de complétion) ; VIDE
     * sans catalogue ou hors d'un accès `libs.`.
     */
    internal fun segmentsAuPoint(
        arbre: KtFichier,
        repertoireModule: Path?,
        deplacement: Int,
    ): List<String> {
        val prefixe = prefixeAuPoint(arbre, deplacement) ?: return emptyList()
        val entrees = catalogueDe(repertoireModule)
        if (entrees.isEmpty()) return emptyList()
        return CatalogueGradle.segmentsSuivants(entrees, prefixe)
    }

    /**
     * La navigation vers l'entrée du catalogue (G5) : la définition d'un
     * accès `libs.…` complet saute à la LIGNE de l'entrée dans le TOML ;
     * une entrée de bibliothèque avec `version.ref` saute à l'entrée
     * `[versions]` pointée.
     *
     * @param arbre l'arbre du document `.kts` ;
     * @param repertoireModule le répertoire du document ;
     * @param cheminCatalogue le chemin du catalogue trouvé (rendu) ;
     * @param deplacement l'offset du caret (dans l'accès) ;
     * @return l'emplacement (uri + plage de la ligne d'entrée) ; `null`
     * sans accès complet résolu.
     */
    internal fun definitionCatalogue(
        arbre: KtFichier,
        repertoireModule: Path?,
        cheminCatalogue: java.util.concurrent.atomic.AtomicReference<String>?,
        deplacement: Int,
    ): EmplacementKotlin? {
        val acces = accesCouvrant(arbre, deplacement) ?: return null
        val entrees = catalogueDe(repertoireModule)
        val entree = CatalogueGradle.entreeExacte(entrees, acces) ?: return null
        val fichier = fichierCatalogue(repertoireModule) ?: return null
        // La ligne : l'entrée, OU l'entrée [versions] référencée (G5).
        val ligne = entree.versionRef?.let { CatalogueGradle.ligneVersion(entrees, it) }
            ?: entree.ligne
        cheminCatalogue?.set(fichier.toUri().toString())
        return EmplacementKotlin(
            uri = fichier.toUri().toString(),
            plage = PlageTexte(ligne, ligne),
        )
    }

    /**
     * Les diagnostics de clés INCONNUES du catalogue (G6) : chaque accès
     * `libs.…` COMPLET (au moins deux segments, le dernier frappé) qui ne
     * désigne AUCUNE entrée du catalogue — la preuve d'absence exige le
     * catalogue PARSÉ et l'accès hors préfixe en cours de frappe (un
     * accès PARTIEL n'est jamais signalé — le caret y vit).
     *
     * @param arbre l'arbre du document `.kts` ;
     * @param repertoireModule le répertoire du document ;
     * @param deplacement l'offset du caret (les accès COUVRANT le caret
     * sont en frappe : muets) ;
     * @return les diagnostics `kt.cleCatalogueInconnue` ; VIDE sans
     * catalogue (le silence n'est jamais une preuve, G4).
     */
    internal fun diagnostics(
        arbre: KtFichier,
        repertoireModule: Path?,
        deplacement: Int,
    ): List<DiagnosticKotlin> {
        val entrees = catalogueDe(repertoireModule)
        if (entrees.isEmpty()) return emptyList()
        val resultat = mutableListOf<DiagnosticKotlin>()
        for (acces in accesComplets(arbre)) {
            // L'accès en FRAPPE (le caret dedans) reste muet.
            if (deplacement >= acces.second.debut && deplacement <= acces.second.fin) continue
            if (CatalogueGradle.entreeExacte(entrees, acces.first) != null) continue
            resultat.add(
                DiagnosticKotlin(
                    code = CodeDiagnosticKotlin.CLE_CATALOGUE_INCONNUE,
                    severite = SeveriteKotlin.AVERTISSEMENT,
                    plage = acces.second,
                    message = "La clé « ${acces.first.removePrefix("libs.")} » est inconnue du catalogue de versions",
                    tags = emptyList(),
                    informationsLiees = emptyList(),
                ),
            )
        }
        return resultat
    }

    // ------------------------------------------------------------------
    // Aides de preuve.
    // ------------------------------------------------------------------

    /** Le préfixe frappé au point : `androidx.` dans `libs.androidx.|`. */
    private fun prefixeAuPoint(arbre: KtFichier, deplacement: Int): String? {
        val (texte, _) = accesAuPoint(arbre, deplacement) ?: return null
        return texte
    }

    /**
     * L'accès `libs.…` au point : le préfixe frappé après `libs.` et la
     * plage de l'accès COMPLET (le texte canonique du sélecteur
     * qualifié débutant par `libs.`).
     */
    private fun accesAuPoint(arbre: KtFichier, deplacement: Int): Pair<String, PlageTexte>? {
        var trouve: Pair<String, PlageTexte>? = null
        fun marcher(noeud: NoeudArbre) {
            if (deplacement < noeud.debut || deplacement > noeud.fin) return
            if (noeud.genre == KtNodeTypes.DOT_QUALIFIED_EXPRESSION &&
                noeud.texteCanonique.startsWith("libs.")
            ) {
                // Le PRÉFIXE : le texte après `libs.` jusqu'au caret (la
                // frappe en cours se coupe au caret).
                val texte = arbre.source
                val coupe = texte.substring(noeud.debut + "libs.".length, deplacement.coerceAtMost(noeud.fin))
                if (coupe.all { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' }) {
                    trouve = Pair(coupe, PlageTexte(noeud.debut, noeud.fin))
                }
            }
            noeud.enfants.forEach(::marcher)
        }
        marcher(arbre.noeud)
        return trouve
    }

    /** L'accès `libs.…` COMPLET couvrant le point (le plus EXTÉRIEUR du nid). */
    private fun accesCouvrant(arbre: KtFichier, deplacement: Int): String? {
        var trouve: String? = null
        fun marcher(noeud: NoeudArbre) {
            if (deplacement < noeud.debut || deplacement > noeud.fin) return
            if (noeud.genre == KtNodeTypes.DOT_QUALIFIED_EXPRESSION &&
                noeud.texteCanonique.startsWith("libs.") &&
                noeud.texteCanonique.removePrefix("libs.").contains('.')
            ) {
                // Le PLUS EXTÉRIEUR gagne (le premier trouvé en descente).
                if (trouve == null) trouve = noeud.texteCanonique
            }
            noeud.enfants.forEach(::marcher)
        }
        marcher(arbre.noeud)
        return trouve
    }

    /**
     * Les accès `libs.…` COMPLETS du document (texte + plage) : l'accès
     * le plus EXTÉRIEUR d'un nid (l'expression `libs.a.b.c` se parse en
     * DOT_QUALIFIED imbriqués — `libs.a.b` vit DANS `libs.a.b.c` : seul
     * le PORTANT EXTERNE est l'accès ; les PARTIELS internes ne sont pas
     * des accès du document).
     */
    private fun accesComplets(arbre: KtFichier): List<Pair<String, PlageTexte>> {
        val acces = mutableListOf<Pair<String, PlageTexte>>()
        fun marcher(noeud: NoeudArbre, dansAcces: Boolean) {
            if (noeud.genre == KtNodeTypes.DOT_QUALIFIED_EXPRESSION &&
                noeud.texteCanonique.startsWith("libs.") &&
                !dansAcces
            ) {
                val suffixe = noeud.texteCanonique.removePrefix("libs.")
                if (suffixe.isNotEmpty()) {
                    acces.add(Pair(noeud.texteCanonique, PlageTexte(noeud.debut, noeud.fin)))
                    noeud.enfants.forEach { marcher(it, true) }
                    return
                }
            }
            noeud.enfants.forEach { marcher(it, dansAcces) }
        }
        marcher(arbre.noeud, false)
        return acces
    }

    /** Les entrées du catalogue du répertoire (module puis racine remontée). */
    private fun catalogueDe(repertoireModule: Path?): List<EntreeCatalogue> {
        if (repertoireModule == null) return emptyList()
        // Le catalogue du module d'abord, puis la racine du PROJET Gradle
        // (le parent qui porte `settings.gradle.kts` — la convention).
        val direct = CatalogueGradle.lire(repertoireModule)
        if (direct.isNotEmpty()) return direct
        var dir: Path? = repertoireModule
        var compteur = 0
        while (dir != null && compteur < 8) {
            dir = dir.parent
            compteur++
            if (dir == null) break
            val essai = CatalogueGradle.lire(dir)
            if (essai.isNotEmpty()) return essai
        }
        return emptyList()
    }

    /** Le fichier catalogue du répertoire (module puis racine remontée). */
    private fun fichierCatalogue(repertoireModule: Path?): Path? {
        if (repertoireModule == null) return null
        val gradle = repertoireModule.resolve("gradle").resolve("libs.versions.toml")
        val racine = repertoireModule.resolve("libs.versions.toml")
        if (java.nio.file.Files.isRegularFile(gradle)) return gradle
        if (java.nio.file.Files.isRegularFile(racine)) return racine
        var dir: Path? = repertoireModule
        var compteur = 0
        while (dir != null && compteur < 8) {
            dir = dir.parent
            compteur++
            if (dir == null) break
            val g = dir.resolve("gradle").resolve("libs.versions.toml")
            if (java.nio.file.Files.isRegularFile(g)) return g
        }
        return null
    }
}
