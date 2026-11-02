package jo.codeide.lsp.java.renommage

import jo.codeide.lsp.java.diagnostics.Diagnostic
import jo.codeide.lsp.java.diagnostics.Plage
import jo.codeide.lsp.java.interne.LectureAst
import jo.codeide.lsp.java.navigation.CleUsages
import jo.codeide.lsp.java.navigation.CollecteurSymbole
import jo.codeide.lsp.java.navigation.IndexMotsSources
import jo.codeide.lsp.java.navigation.NatureSymbole
import jo.codeide.lsp.java.outils.EditionTexte
import jo.codeide.lsp.java.outils.appliquerEditions
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration
import org.eclipse.jdt.internal.compiler.ast.ImportReference
import org.eclipse.jdt.internal.compiler.lookup.Binding
import org.eclipse.jdt.internal.compiler.lookup.CompilationUnitScope
import org.eclipse.jdt.internal.compiler.lookup.ImportBinding
import org.eclipse.jdt.internal.compiler.lookup.LocalVariableBinding
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * Le moteur de renommage par PHASES (étage N5 — cahier fiches 026 et
 * 061, design intégré ADR 0014 : « protocole de phases du rename —
 * prepare / usage / conflicts / exec »).
 *
 * Les phases, chacune observable et testable isolément :
 *
 *  1. **PREPARE** — [cibleA] : identification du symbole au caret par
 *     BINDINGS (la clé structurelle « method:paquet.Classe.nom( » /
 *     « field:paquet.Classe.nom » / « type:paquet.Nom » isole les
 *     homonymes) ; le constructeur vise le TYPE ; un segment d'import
 *     est une cible comme une autre.
 *  2. **USAGES** — [renommageProjet] : la collecte EXHAUSTIVE des
 *     occurrences à retoucher (déclaration + usages simples et
 *     qualifiés + IMPORTS + constructeurs pour un type), multi-fichiers
 *     par l'index de mots (le chemin chaud ne traverse JAMAIS
 *     l'arborescence — l'oracle [renommageProjetParBalayageComplet]
 *     vérifie le rappel = 100 %) ; les LOCALES court-circuitent le
 *     balayage (file-local).
 *  3. **CONFLITS** — [conflits] : la liste TYPÉE de collisions
 *     (identifiant invalide, nom pris, masquage, override non suivi,
 *     référence brisée par re-résolution de l'aperçu) — le renommage
 *     « sûr » de l'ADR 0014 : détecter AVANT écriture, décider côté
 *     client (le moteur ne décide JAMAIS à la place de l'utilisateur).
 *  4. **EXÉCUTION** — [editions] puis [apercu] : les éditions exactes
 *     (une par occurrence, le jeton remplacé par le nouveau nom) et
 *     l'APERÇU — preview = résultat (le modèle serveur-calcule /
 *     client-applique est conservé : le moteur n'écrit JAMAIS sur le
 *     disque, l'undo appartient au client).
 *
 * Le renommage des OVERRIDES est opt-in explicite ([suivreOverrides]
 * = false par défaut — comportement volontaire documenté ADR 0014) :
 * par défaut, les déclarations outrépassées dans les sous-types
 * gardent l'ancien nom et sont SIGNALÉES comme collisions typées.
 *
 * @param racines les racines sources du projet.
 * @param indexMots l'index de mots des sources (candidats, fraîcheur).
 * @param resoudre résout une unité (chemin logique, texte) en arbre bindé.
 * @param diagnostiquer diagnostique une unité (chemin logique, texte) —
 *   l'oracle de re-résolution de la phase conflits.
 * @param localiser localise le fichier source d'un FQN (annuaire de
 *   noms) — sert la phase conflits à analyser le type DÉCLARANT même
 *   quand il vit dans un autre fichier.
 */
public class MoteurRenommage(
    private val racines: List<Path>,
    private val indexMots: IndexMotsSources,
    private val resoudre: (String, String) -> CompilationUnitDeclaration?,
    private val diagnostiquer: (String, String) -> List<Diagnostic>,
    private val localiser: (String) -> Path?,
) {

    // ------------------------------------------------------------------ //
    // Phase 1 — PREPARE : la cible au caret
    // ------------------------------------------------------------------ //

    /**
     * La cible de renommage au caret (fiche 026) : le symbole identifié
     * par bindings — nom actuel, genre, clé structurelle, périmètre.
     *
     * Cas particuliers (fiche 026) : le nom d'un CONSTRUCTEUR vise le
     * TYPE ; un segment d'IMPORT vise le type (ou le membre importé
     * statiquement) ; une LOCALE est file-local.
     *
     * @param chemin le chemin logique du document interrogé.
     * @param source le texte Java complet (tampon ouvert).
     * @param offset la position du caret.
     * @return la cible, ou null (caret hors identifiant renommable).
     */
    public fun cibleA(chemin: String, source: String, offset: Int): CibleRenommage? {
        val arbre = runCatching { resoudre(chemin, source) }.getOrNull() ?: return null
        val collecteur = CollecteurCibleRenommage(source, offset, liaisonsDImports(arbre))
        LectureAst.traverserResilient(arbre, collecteur)
        val symbole = collecteur.symbole() ?: return null
        return decrireCible(symbole, source)
    }

    /** Traduit le symbole interne en cible publique (clé structurelle fiche 026). */
    private fun decrireCible(symbole: jo.codeide.lsp.java.navigation.SymboleAuCaret, source: String): CibleRenommage? {
        val nom = textePlage(source, symbole.jeton) ?: return null
        val (genre, cle) = when (symbole.nature) {
            NatureSymbole.LOCALE -> GenreCibleRenommage.LOCALE to null
            NatureSymbole.CHAMP -> {
                val c = symbole.cleUsages as? CleUsages.Champ ?: return null
                GenreCibleRenommage.CHAMP to "field:${c.fqnProprietaire}.${c.nom}"
            }
            NatureSymbole.METHODE -> {
                val m = symbole.cleUsages as? CleUsages.Methode ?: return null
                GenreCibleRenommage.METHODE to "method:${m.fqnProprietaire}.${m.selecteur}("
            }
            NatureSymbole.TYPE -> {
                val t = symbole.cleUsages as? CleUsages.Type ?: return null
                GenreCibleRenommage.TYPE to "type:${t.fqn}"
            }
        }
        return CibleRenommage(
            nomActuel = nom,
            genre = genre,
            cleStructurelle = cle,
            fileLocal = genre == GenreCibleRenommage.LOCALE,
            jeton = symbole.jeton,
        )
    }

    // ------------------------------------------------------------------ //
    // Phase 2 — USAGES : les occurrences à retoucher
    // ------------------------------------------------------------------ //

    /**
     * Les occurrences du renommage, par fichier (fiche 026 :
     * `renameProject`) — EXHAUSTIF sur les racines : la déclaration, les
     * usages simples et qualifiés, et pour un TYPE les imports et
     * constructeurs compris. Les fichiers sans occurrence sont ABSENTS
     * de la sortie ; les homonymes d'une autre classe ne sont jamais
     * retouchés (clé de binding).
     *
     * Une LOCALE court-circuite le balayage projet : la map contient
     * alors UNE seule entrée (le fichier courant).
     *
     * @param chemin le chemin logique du document interrogé.
     * @param source le texte du document (tampon ouvert — prime sur le disque).
     * @param offset la position du caret.
     * @param suivreOverrides suivre les déclarations outrépassées dans
     *   les sous-types (opt-in explicite, faux par défaut — ADR 0014).
     * @return la map triée fichier → occurrences (triées par offset).
     */
    public fun renommageProjet(
        chemin: String,
        source: String,
        offset: Int,
        suivreOverrides: Boolean = false,
    ): Map<String, List<OccurrenceRenommage>> {
        val arbre = runCatching { resoudre(chemin, source) }.getOrNull() ?: return emptyMap()
        val symbole = symboleA(arbre, source, offset) ?: return emptyMap()
        return occurrences(chemin, source, arbre, symbole, suivreOverrides, balayageIndexe = true)
    }

    /**
     * L'ORACLE du banc : les occurrences par BALAYAGE COMPLET des
     * racines (toutes les sources résolues, même correspondance par
     * clé) — le rappel de [renommageProjet] est mesuré CONTRE cette
     * map. Ce chemin traverse exprès l'arborescence : il n'est JAMAIS
     * sur le chemin chaud.
     *
     * @param chemin le chemin logique du document interrogé.
     * @param source le texte du document (tampon ouvert).
     * @param offset la position du caret.
     * @return la map exhaustive (même forme que [renommageProjet]).
     */
    public fun renommageProjetParBalayageComplet(
        chemin: String,
        source: String,
        offset: Int,
    ): Map<String, List<OccurrenceRenommage>> {
        val arbre = runCatching { resoudre(chemin, source) }.getOrNull() ?: return emptyMap()
        val symbole = symboleA(arbre, source, offset) ?: return emptyMap()
        return occurrences(chemin, source, arbre, symbole, suivreOverrides = false, balayageIndexe = false)
    }

    /** Le symbole au caret d'un arbre déjà résolu. */
    private fun symboleA(
        arbre: CompilationUnitDeclaration,
        source: String,
        offset: Int,
    ): jo.codeide.lsp.java.navigation.SymboleAuCaret? {
        val collecteur = CollecteurCibleRenommage(source, offset, liaisonsDImports(arbre))
        LectureAst.traverserResilient(arbre, collecteur)
        return collecteur.symbole()
    }

    /**
     * La collecte commune des occurrences : le document courant (source
     * DONNÉE — l'overlay tampons-ouverts prime sur le disque) puis les
     * autres fichiers — candidats de l'index de mots (chemin chaud) ou
     * toutes les sources (oracle).
     */
    private fun occurrences(
        chemin: String,
        source: String,
        arbre: CompilationUnitDeclaration,
        symbole: jo.codeide.lsp.java.navigation.SymboleAuCaret,
        suivreOverrides: Boolean,
        balayageIndexe: Boolean,
    ): Map<String, List<OccurrenceRenommage>> {
        val cheminCourant = fichierCourant(chemin)?.toString() ?: chemin
        val resultat = HashMap<String, MutableList<OccurrenceRenommage>>()

        // LOCALE : périmètre de l'unité courante (identité de binding),
        // court-circuit du balayage projet (fiche 026).
        if (symbole.nature == NatureSymbole.LOCALE) {
            val locale = symbole.bindingEcj as? LocalVariableBinding ?: return emptyMap()
            val collecteur = CollecteurOccurrencesRenommage(source, null, locale)
            LectureAst.traverserResilient(arbre, collecteur)
            val liste = collecteur.occurrences.map { OccurrenceRenommage(it.plage, it.estDeclaration) }
            if (liste.isNotEmpty()) resultat[cheminCourant] = liste.toMutableList()
            return trier(resultat)
        }

        val cle = symbole.cleUsages ?: return emptyMap()
        val mot = motDe(cle)

        // Document courant : la source DONNÉE (overlay).
        run {
            val collecteur = CollecteurOccurrencesRenommage(source, cle, null, suivreOverrides, liaisonsDImports(arbre))
            LectureAst.traverserResilient(arbre, collecteur)
            val liste = collecteur.occurrences.map { OccurrenceRenommage(it.plage, it.estDeclaration) }
            if (liste.isNotEmpty()) resultat[cheminCourant] = liste.toMutableList()
        }

        // Autres fichiers : candidats de l'index (chemin chaud) ou
        // toutes les sources (oracle), texte disque.
        val disqueCourant = fichierCourant(chemin)
        val fichiers = if (balayageIndexe) indexMots.fichiersPour(mot) else balayerSources()
        for (fichier in fichiers) {
            if (disqueCourant != null && fichier == disqueCourant) continue // déjà servi (overlay)
            val texte = try {
                fichier.readText()
            } catch (_: Exception) {
                continue
            }
            val arbreFichier = runCatching { resoudre(fichier.toString(), texte) }.getOrNull() ?: continue
            val collecteur = CollecteurOccurrencesRenommage(texte, cle, null, suivreOverrides, liaisonsDImports(arbreFichier))
            LectureAst.traverserResilient(arbreFichier, collecteur)
            val liste = collecteur.occurrences.map { OccurrenceRenommage(it.plage, it.estDeclaration) }
            if (liste.isNotEmpty()) resultat[fichier.toString()] = liste.toMutableList()
        }
        return trier(resultat)
    }

    /** Trie la sortie : fichiers croissants, occurrences par offset croissant. */
    private fun trier(brut: Map<String, List<OccurrenceRenommage>>): Map<String, List<OccurrenceRenommage>> {
        val triee = java.util.TreeMap<String, List<OccurrenceRenommage>>()
        for ((fichier, liste) in brut) {
            triee[fichier] = liste.sortedBy { it.plage.debutOffset }
        }
        return triee
    }

    // ------------------------------------------------------------------ //
    // Phase 3 — CONFLITS : les collisions typées
    // ------------------------------------------------------------------ //

    /**
     * Les collisions typées du renommage proposé (ADR 0014 : « liste
     * typée de collisions ») — détectées AVANT écriture :
     *
     *  - [GenreConflit.IDENTIFIANT_INVALIDE] : mot-clé ou identifiant
     *    Java illégal (garde statique) ;
     *  - [GenreConflit.NOM_PRIS] : le nouveau nom est déjà porté par un
     *    membre du type déclarant (tout genre confondu), par une locale
     *    du périmètre (locale) ou par un type de la même unité (type) ;
     *  - [GenreConflit.MASQUAGE] : le renommé masquerait (ou serait
     *    masqué par) une entité visible du périmètre — Java l'autorise,
     *    le moteur le signale ;
     *  - [GenreConflit.OVERRIDE_NON_SUIVI] : une méthode outrépassée
     *    dans un sous-type garde l'ancien nom (opt-in non armé) — son
     *    `@Override` devient faux ;
     *  - [GenreConflit.REFERENCE_BRISE] / [GenreConflit.CASSURE] :
     *    diagnostics NOUVEAUX sur l'aperçu du fichier courant (re-
     *    résolution) quand le renommage est confiné à ce fichier.
     *
     * La re-résolution multi-fichiers complète attend l'overlay
     * tampons-ouverts de la couche LSP (étage N8) : les conflits
     * croix-fichiers sont couverts par l'analyse statique ci-dessus
     * (noms du déclarant, overrides) et l'exhaustivité des occurrences
     * (l'oracle du banc).
     *
     * @param chemin le chemin logique du document interrogé.
     * @param source le texte du document (tampon ouvert).
     * @param offset la position du caret.
     * @param nouveauNom le nom proposé.
     * @param suivreOverrides suivre les overrides (opt-in — supprime la
     *   collision OVERRIDE_NON_SUIVI en retouchant les déclarations).
     * @return les collisions typées (vide = renommage sûr).
     */
    public fun conflits(
        chemin: String,
        source: String,
        offset: Int,
        nouveauNom: String,
        suivreOverrides: Boolean = false,
    ): List<ConflitRenommage> {
        // Garde statique : identifiant Java valide, hors mot-clé.
        if (!estIdentifiantValide(nouveauNom)) {
            return listOf(
                ConflitRenommage(
                    GenreConflit.IDENTIFIANT_INVALIDE,
                    "« $nouveauNom » n'est pas un identifiant Java valide (mot-clé compris)",
                ),
            )
        }

        val arbre = runCatching { resoudre(chemin, source) }.getOrNull() ?: return emptyList()
        val collecteurCible = CollecteurCibleRenommage(source, offset, liaisonsDImports(arbre))
        LectureAst.traverserResilient(arbre, collecteurCible)
        val symbole = collecteurCible.symbole() ?: return emptyList()
        val cible = decrireCible(symbole, source) ?: return emptyList()

        // Renommer au même nom : aucune collision (éditions identités).
        if (cible.nomActuel == nouveauNom) return emptyList()

        // Phase USAGES réutilisée : les occurrences à retoucher.
        val parFichier = occurrences(chemin, source, arbre, symbole, suivreOverrides, balayageIndexe = true)
        val cheminCourant = fichierCourant(chemin)?.toString() ?: chemin
        val conflits = ArrayList<ConflitRenommage>()

        // --- périmètre déclarant : noms pris, masquages, overrides ---
        conflits.addAll(conflitsDePerimetre(chemin, source, arbre, symbole, cible, nouveauNom, cheminCourant, parFichier, suivreOverrides))

        // --- re-résolution de l'aperçu (renommage confiné au fichier courant) ---
        val confine = parFichier.keys.all { it == cheminCourant }
        if (confine && parFichier.isNotEmpty()) {
            conflits.addAll(conflitsDeReResolution(chemin, source, cible.nomActuel, nouveauNom, parFichier[cheminCourant] ?: emptyList(), conflits))
        }
        return conflits
    }

    /**
     * Les collisions du périmètre déclarant (analyse statique par
     * bindings) : noms déjà pris, masquages légaux mais surprenants,
     * overrides non suivis — le corpus doré « 0 conflit manqué ».
     */
    private fun conflitsDePerimetre(
        chemin: String,
        source: String,
        arbre: CompilationUnitDeclaration,
        symbole: jo.codeide.lsp.java.navigation.SymboleAuCaret,
        cible: CibleRenommage,
        nouveauNom: String,
        cheminCourant: String,
        parFichier: Map<String, List<OccurrenceRenommage>>,
        suivreOverrides: Boolean,
    ): List<ConflitRenommage> {
        val conflits = ArrayList<ConflitRenommage>()

        // L'unité déclarante : l'arbre courant s'il porte la déclaration
        // du type déclarant, sinon le fichier localisé par l'annuaire.
        val fqnDeclarant = symbole.fqnProprietaire
        var uniteDeclarante = arbre
        var cheminDeclarant = cheminCourant
        var sourceDeclarante = source
        if (symbole.nature != NatureSymbole.LOCALE && fqnDeclarant != null && !declareType(arbre, fqnDeclarant)) {
            val fichier = runCatching { localiser(fqnDeclarant) }.getOrNull()
            if (fichier != null) {
                val texte = try {
                    fichier.readText()
                } catch (_: Exception) {
                    null
                }
                if (texte != null) {
                    val arbreDecl = runCatching { resoudre(fichier.toString(), texte) }.getOrNull()
                    if (arbreDecl != null) {
                        uniteDeclarante = arbreDecl
                        cheminDeclarant = fichier.toString()
                        sourceDeclarante = texte
                    }
                }
            }
        }

        val analyse = AnalyseurPerimetres(sourceDeclarante, symbole)
        LectureAst.traverserResilient(uniteDeclarante, analyse)

        when (symbole.nature) {
            NatureSymbole.LOCALE -> {
                // Noms pris dans la méthode hôte ; champs masqués.
                val hote = analyse.methodeHote ?: return conflits
                for ((nom, plage) in analyse.localesDeLaMethode(hote)) {
                    if (nom == nouveauNom) {
                        conflits += ConflitRenommage(
                            GenreConflit.NOM_PRIS,
                            "« $nouveauNom » est déjà une locale du périmètre",
                            cheminDeclarant, plage,
                        )
                    }
                }
                for ((nom, plage) in analyse.membresDuTypeEnglobant(hote)) {
                    if (nom == nouveauNom) {
                        conflits += ConflitRenommage(
                            GenreConflit.MASQUAGE,
                            "la locale renommée « $nouveauNom » masquerait le champ du type englobant",
                            cheminDeclarant, plage,
                        )
                    }
                }
            }
            NatureSymbole.CHAMP -> {
                val champ = symbole.cleUsages as? CleUsages.Champ
                if (champ != null) {
                    for ((nom, plage) in analyse.membresDeLaClasse(champ.fqnProprietaire)) {
                        if (nom == nouveauNom) {
                            conflits += ConflitRenommage(
                                GenreConflit.NOM_PRIS,
                                "« $nouveauNom » est déjà porté par un membre de ${champ.fqnProprietaire}",
                                cheminDeclarant, plage,
                            )
                        }
                    }
                    for ((nom, plage) in analyse.localesDesMethodesDe(champ.fqnProprietaire)) {
                        if (nom == nouveauNom) {
                            conflits += ConflitRenommage(
                                GenreConflit.MASQUAGE,
                                "le champ renommé « $nouveauNom » serait masqué par une locale",
                                cheminDeclarant, plage,
                            )
                        }
                    }
                }
            }
            NatureSymbole.METHODE -> {
                val methode = symbole.cleUsages as? CleUsages.Methode
                if (methode != null) {
                    for ((nom, plage) in analyse.membresDeLaClasse(methode.fqnProprietaire)) {
                        if (nom == nouveauNom) {
                            conflits += ConflitRenommage(
                                GenreConflit.NOM_PRIS,
                                "« $nouveauNom » est déjà porté par un membre de ${methode.fqnProprietaire}",
                                cheminDeclarant, plage,
                            )
                        }
                    }
                }
            }
            NatureSymbole.TYPE -> {
                for ((nom, plage) in analyse.typesDeLUnite()) {
                    if (nom == nouveauNom) {
                        conflits += ConflitRenommage(
                            GenreConflit.NOM_PRIS,
                            "« $nouveauNom » est déjà un type de l'unité",
                            cheminDeclarant, plage,
                        )
                    }
                }
            }
        }

        // Overrides non suivis (opt-in désarmé) : les déclarations
        // outrépassées des sous-types gardent l'ancien nom.
        if (symbole.nature == NatureSymbole.METHODE && !suivreOverrides) {
            val methode = symbole.cleUsages as? CleUsages.Methode
            if (methode != null) {
                conflits += conflitsOverridesNonSuivis(chemin, source, cheminCourant, methode)
            }
        }
        return conflits
    }

    /**
     * Les déclarations outrépassées dans les sous-types (même sélecteur,
     * même arité, type déclarant sous-type de la base) : elles gardent
     * l'ancien nom quand l'opt-in n'est pas armé.
     */
    private fun conflitsOverridesNonSuivis(
        chemin: String,
        source: String,
        cheminCourant: String,
        methode: CleUsages.Methode,
    ): List<ConflitRenommage> {
        val conflits = ArrayList<ConflitRenommage>()
        val disqueCourant = fichierCourant(chemin)

        fun visiter(cheminFichier: String, texteFichier: String) {
            val arbreFichier = runCatching { resoudre(cheminFichier, texteFichier) }.getOrNull() ?: return
            val chasseur = ChasseurOverrides(methode.selecteur, methode.nbParametres, methode.fqnProprietaire, texteFichier)
            LectureAst.traverserResilient(arbreFichier, chasseur)
            for (trouveau in chasseur.trouvailles) {
                conflits += ConflitRenommage(
                    GenreConflit.OVERRIDE_NON_SUIVI,
                    "« ${methode.selecteur} » est outrépassée dans un sous-type et ne sera PAS suivie — son @Override devient faux (opt-in explicite requis)",
                    cheminFichier, trouveau,
                )
            }
        }

        visiter(cheminCourant, source)
        for (fichier in indexMots.fichiersPour(methode.selecteur)) {
            if (disqueCourant != null && fichier == disqueCourant) continue
            val texte = try {
                fichier.readText()
            } catch (_: Exception) {
                continue
            }
            visiter(fichier.toString(), texte)
        }
        return conflits
    }

    /**
     * La re-résolution de l'APERÇU (famille « prototype re-résolu » de
     * l'étude D10) : les diagnostics NOUVEAUX après renommage du fichier
     * courant — référence brisée, cassure résiduelle.
     */
    private fun conflitsDeReResolution(
        chemin: String,
        source: String,
        ancienNom: String,
        nouveauNom: String,
        occurrences: List<OccurrenceRenommage>,
        dejaConnus: List<ConflitRenommage>,
    ): List<ConflitRenommage> {
        val editionsCourant = editions(nouveauNom, occurrences)
        val apercuCourant = apercu(source, editionsCourant)
        val avant = runCatching { diagnostiquer(chemin, source) }.getOrDefault(emptyList())
        val apres = runCatching { diagnostiquer(chemin, apercuCourant) }.getOrDefault(emptyList())
        val empreintesAvant = avant.map { empreinte(it) }.toSet()
        val conflits = ArrayList<ConflitRenommage>()
        for (diagnostic in apres) {
            if (diagnostic.severite != jo.codeide.lsp.java.diagnostics.Severite.ERREUR) continue
            if (empreinte(diagnostic) in empreintesAvant) continue
            val plage = diagnostic.plage
            // Doublon d'un conflit statique déjà rendu (même plage) : le
            // genre statique, plus précis, prime.
            if (dejaConnus.any { it.plage == plage }) continue
            val message = diagnostic.message.lowercase()
            val genre = when {
                "duplicate" in message -> GenreConflit.NOM_PRIS
                "cannot be resolved" in message || "ambiguous" in message -> GenreConflit.REFERENCE_BRISE
                else -> GenreConflit.CASSURE
            }
            val libelle = when (genre) {
                GenreConflit.NOM_PRIS -> "« $nouveauNom » duplique une déclaration après renommage de « $ancienNom »"
                GenreConflit.REFERENCE_BRISE -> "une référence ne résout plus après renommage de « $ancienNom » en « $nouveauNom »"
                else -> "cassure après renommage de « $ancienNom » en « $nouveauNom » : ${diagnostic.message}"
            }
            conflits += ConflitRenommage(genre, libelle, chemin, plage)
        }
        return conflits
    }

    /** L'empreinte d'un diagnostic (parité avant/après). */
    private fun empreinte(diagnostic: Diagnostic): Triple<String, String, Int> =
        Triple(diagnostic.message, diagnostic.code ?: "", diagnostic.plage.debutOffset)

    /** Vrai ssi l'unité déclare le type portant ce FQN complet. */
    private fun declareType(arbre: CompilationUnitDeclaration, fqn: String): Boolean {
        val verificateur = object : LectureAst.VisiteurResilient() {
            var trouve = false
            override fun visiteType(node: org.eclipse.jdt.internal.compiler.ast.TypeDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
                val b = try { node.binding } catch (_: Exception) { null }
                if (b is org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding && fqnCompletDuType(b) == fqn) trouve = true
                return true
            }
        }
        LectureAst.traverserResilient(arbre, verificateur)
        return verificateur.trouve
    }

    /** Vrai ssi le nom est un identifiant Java valide, hors mot-clé. */
    private fun estIdentifiantValide(nom: String): Boolean =
        nom.isNotEmpty() &&
            Character.isJavaIdentifierStart(nom[0]) &&
            nom.drop(1).all { Character.isJavaIdentifierPart(it) } &&
            nom !in MOTS_CLES

    /** Les mots-clés de la grammaire Java (garde IDENTIFIANT_INVALIDE). */
    private companion object {
        val MOTS_CLES: Set<String> = java.util.Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
            "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
            "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
            "interface", "long", "native", "new", "package", "private", "protected", "public",
            "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
            "throw", "throws", "transient", "try", "void", "volatile", "while",
            "true", "false", "null",
        )
    }

    // ------------------------------------------------------------------ //
    // Phase 4 — EXÉCUTION : éditions et aperçu
    // ------------------------------------------------------------------ //

    /**
     * Les éditions d'un renommage (une par occurrence) : le JETON est
     * remplacé EXACTEMENT par le nouveau nom — plages disjointes en
     * offsets croissants (contrat [appliquerEditions]).
     *
     * @param nouveauNom le nouveau nom.
     * @param occurrences les occurrences retouchées (un seul fichier).
     * @return les éditions triées par offset croissant.
     */
    public fun editions(nouveauNom: String, occurrences: List<OccurrenceRenommage>): List<EditionTexte> =
        occurrences
            .map { EditionTexte(it.plage.debutOffset, it.plage.finOffset - it.plage.debutOffset, nouveauNom) }
            .sortedBy { it.offset }

    /**
     * L'APERÇU du renommage (preview = résultat, ADR 0014) : la source
     * APRÈS application des éditions — le moteur n'écrit jamais sur le
     * disque, l'aperçu est la preuve que la prévisualisation et le
     * résultat coïncident.
     *
     * @param source la source originale du fichier.
     * @param editions les éditions du renommage pour CE fichier.
     * @return la source previewée.
     */
    public fun apercu(source: String, editions: List<EditionTexte>): String = appliquerEditions(source, editions)

    // ------------------------------------------------------------------ //
    // Outils internes
    // ------------------------------------------------------------------ //

    /** Le mot-indexable d'une clé (sélecteur, nom de champ, nom simple). */
    private fun motDe(cle: CleUsages): String = when (cle) {
        is CleUsages.Methode -> cle.selecteur
        is CleUsages.Champ -> cle.nom
        is CleUsages.Type -> cle.fqn.substringAfterLast('.').substringAfterLast('$')
    }

    /** Le texte exact d'une plage (null si hors bornes). */
    private fun textePlage(source: String, plage: Plage): String? = try {
        if (plage.debutOffset >= 0 && plage.finOffset <= source.length && plage.debutOffset < plage.finOffset) {
            source.substring(plage.debutOffset, plage.finOffset)
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }

    /** Le fichier disque d'un chemin logique (null si purement en mémoire). */
    internal fun fichierCourant(chemin: String): Path? {
        runCatching {
            val direct = Path.of(chemin)
            if (Files.isRegularFile(direct)) return direct
        }
        for (racine in racines) {
            val candidat = racine.resolve(chemin)
            if (Files.isRegularFile(candidat)) return candidat
        }
        return null
    }

    /** L'énumération complète des sources (balayage de l'ORACLE seulement). */
    private fun balayerSources(): List<Path> {
        val fichiers = ArrayList<Path>()
        for (racine in racines) {
            if (!Files.isDirectory(racine)) continue
            try {
                Files.walk(racine).use { flux ->
                    flux.filter { it.fileName?.toString()?.endsWith(".java") == true }.forEach { fichiers.add(it) }
                }
            } catch (_: Exception) {
                // Racine illisible écartée.
            }
        }
        return fichiers.sorted()
    }

    /**
     * Les liaisons import ↔ ImportBinding d'une unité résolue : la
     * fenêtre des segments d'import sur leurs bindings (type importé,
     * membre importé statiquement) — null si le scope est muet.
     */
    internal fun liaisonsDImports(arbre: CompilationUnitDeclaration): Map<ImportReference, ImportBinding> {
        val liaisons = HashMap<ImportReference, ImportBinding>()
        runCatching {
            val scope: CompilationUnitScope? = arbre.scope
            val imports: Array<ImportBinding>? = scope?.imports
            if (imports != null) {
                for (liaison in imports) {
                    if (liaison != null && liaison.reference != null) {
                        liaisons[liaison.reference] = liaison
                    }
                }
            }
        }
        return liaisons
    }
}
