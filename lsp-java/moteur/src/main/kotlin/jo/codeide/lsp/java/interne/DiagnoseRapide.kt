package jo.codeide.lsp.java.interne

import jo.codeide.lsp.java.actionscode.FamilleProbleme
import jo.codeide.lsp.java.outils.PlageCorps
import jo.codeide.lsp.java.outils.ScannerCorps
import java.nio.file.Files
import java.nio.file.Path

/**
 * Mécanique du diagnose rapide par corps de méthode (étage N1, cahier
 * fiche 014 — P6 v1.5.0, « skip the file parse entirely when a keystroke
 * stays inside one body », adapté ecj).
 *
 * Principe : quand une frappe reste CONFINÉE dans un seul corps de méthode,
 * seul CE corps est re-diagnostiqué — les autres corps sont remplacés dans
 * la source soumise à ecj par un **synthétique isomorphe** (même longueur :
 * ` throw new RuntimeException();` + bourrage d'espaces), et les problèmes
 * des corps masqués sont PORTÉS tels quels du diagnostic précédent (mêmes
 * offsets — l'isomorphie garantit l'alignement).
 *
 * La preuve de confinement casse → repli OBLIGATOIRE sur diagnose complet :
 *  - accolades déséquilibrées (le scanner rend null) ;
 *  - le texte HORS corps a changé (signature, initialiseur, import) ;
 *  - plus d'un corps a changé (région traversante) ;
 *  - aucun corps n'a changé mais la source diffère (memo géré en amont).
 *
 * Le synthétique `throw new RuntimeException();` est valide pour un corps de
 * méthode quel que soit le type de retour (pas de faux « must return ») et
 * ne référence rien du projet (pas de faux « unused ») — les pièges du
 * corpus du cahier (méthode non-void, import utilisé seulement par un corps
 * masqué) sont tenus par construction.
 *
 * **Re-validation du report hors-corps (vague V-A, ADR 0025 — P1)** : le
 * portage des signaux ANALYTIQUES hors corps (import/membre privé inutilisé)
 * est GABARITÉ par les règles du TÉMOIN NEUF, mesurées sur ecj 3.40 (sonde
 * du 31/10/2026, banc P1) : un diagnose complet d'une source portant une
 * ERREUR ne publie JAMAIS l'import inutilisé ; une source portant une erreur
 * de SYNTAXE ne publie jamais le membre privé inutilisé (une erreur de simple
 * résolution ne le supprime pas). Le portage suit exactement ces règles : la
 * divergence rapide/complet ne peut plus naître du portage (P1 = 0).
 */
public object DiagnoseRapide {

    /** Texte du synthétique (interne du corps, sans les accolades). */
    internal const val SYNTHETIQUE: String = " throw new RuntimeException();"

    /**
     * État rapide d'un document : dernière source diagnostiquée, son
     * résultat publié, ses plages de corps, son arbre focalisé et les
     * **dépendances disque** qui garantissent la fraîcheur du mémo (P8).
     *
     * @property source la dernière source passée par diagnose (garde de
     *   contenu pour l'arbre focalisé).
     * @property diagnostics le résultat publié pour cette source.
     * @property corps les plages de corps détectées dans cette source.
     * @property arbreFocalise l'arbre de la source MASQUÉE (servi pour les
     *   features dans le corps hôte) ; null après un diagnose complet.
     * @property corpsHote le corps hôte de l'arbre focalisé (garde d'offset).
     * @property dependances les fichiers SOURCES du module lus sur disque
     *   pendant la passe (chemin → empreinte STAT : mtime+taille) — le mémo
     *   n'est servi que s'ils sont TOUS inchangés ([dependancesInchangees]) : une
     *   édition de l'API d'un fichier voisin invalide le résultat périmé
     *   (écart G-003/H4, ADR 0025 — le re-diagnostic est PULL côté hôte).
     * @property analytiquesHorsCorps les signaux analytiques hors corps
     *   (import/membre privé inutilisé) du dernier diagnose COMPLET,
     *   TRANSLATÉS aux positions de la présente source — la source de
     *   portage PERSISTANTE : la publication d'une passe rapide peut les
     *   TAIRE (gabarit du témoin, frappe syntaxiquement fausse) sans les
     *   perdre — la passe suivante les retrouve intacts dès que l'unité
     *   redevient publiable (sinon la révocation temporaire deviendrait
     *   définitive : mesuré P1 édition 27, vague V-A).
     */
    public class Etat(
        public val source: String,
        public val diagnostics: List<jo.codeide.lsp.java.diagnostics.Diagnostic>,
        public val corps: List<PlageCorps>,
        public val arbreFocalise: UniteResolue?,
        public val corpsHote: PlageCorps?,
        public val dependances: Map<Path, String> = emptyMap(),
        public val analytiquesHorsCorps: List<jo.codeide.lsp.java.diagnostics.Diagnostic> = emptyList(),
    ) {
        /**
         * Vrai ssi AUCUNE dépendance disque n'a changé depuis la passe —
         * la garde de fraîcheur du mémo (P8) : empreinte courante == empreinte
         * enregistrée pour CHAQUE fichier (un fichier disparu ou devenu
         * illisible vaut changement si sa lecture avait réussi à la passe).
         */
        public fun dependancesInchangees(): Boolean = dependances.all { (fichier, empreinte) ->
            empreinteFichier(fichier) == empreinte
        }
    }

    /**
     * Tente d'établir la preuve de confinement entre l'ancienne source et
     * la nouvelle.
     *
     * La preuve compare les SEGMENTS hors corps (le texte entre les corps,
     * signatures comprises) : un corps qui grandit décale les positions
     * aval — les contenus restent identiques, la comparaison doit donc être
     * en contenu et non en longueur totale.
     *
     * @param ancienne l'état rapide du précédent diagnose (null = aucun).
     * @param nouvelle la nouvelle source.
     * @return la plage du CORPS HÔTE (le seul corps dont l'intérieur a
     *   changé) si la preuve est établie ; null sinon (repli complet).
     */
    public fun preuveDeConfinement(ancienne: Etat?, nouvelle: String): PlageCorps? {
        if (ancienne == null) return null
        val corpsNouveau = ScannerCorps.corpsDeMethodes(nouvelle) ?: return null
        if (ancienne.corps.size != corpsNouveau.size) return null
        // Le texte HORS corps doit être identique, segment par segment.
        if (segmentsHorsCorps(ancienne.source, ancienne.corps) != segmentsHorsCorps(nouvelle, corpsNouveau)) {
            return null
        }
        // Exactement UN corps dont l'intérieur diffère.
        var hote: PlageCorps? = null
        for ((index, plage) in corpsNouveau.withIndex()) {
            val anciennePlage = ancienne.corps[index]
            if (anciennePlage.longueur != plage.longueur ||
                ancienne.source.substring(anciennePlage.debutInterieur, anciennePlage.finInterieur) !=
                nouvelle.substring(plage.debutInterieur, plage.finInterieur)
            ) {
                if (hote != null) return null // deux corps touchés : structurel
                hote = plage
            }
        }
        return hote
    }

    /** Segments de texte hors corps (entre les corps, avant le premier, après le dernier). */
    private fun segmentsHorsCorps(source: String, corps: List<PlageCorps>): List<String> {
        val segments = mutableListOf<String>()
        var curseur = 0
        for (plage in corps) {
            if (plage.debutInterieur > curseur) {
                segments.add(source.substring(curseur, plage.debutInterieur))
            }
            if (plage.finInterieur > curseur) {
                curseur = plage.finInterieur
            }
        }
        segments.add(source.substring(curseur.coerceAtMost(source.length)))
        return segments
    }

    /**
     * Construit la source MASQUÉE pour le diagnose rapide : le corps hôte
     * intact, les autres corps remplacés par le synthétique isomorphe.
     *
     * Les corps trop courts pour contenir le synthétique, et les corps
     * vides, restent VERBATIM (contrat du cahier : « corps vide et corps
     * trop court laissés verbatim »).
     *
     * @param source la nouvelle source complète.
     * @param corps les plages de corps de la nouvelle source.
     * @param hote le corps hôte (intact).
     * @return la source masquée, de MÊME LONGUEUR que l'original.
     */
    public fun sourceMasquee(source: String, corps: List<PlageCorps>, hote: PlageCorps): String {
        val resultat = source.toCharArray()
        var frontierePrecedente = -1
        for (plage in corps) {
            // Égalité STRUCTURELLE (==), jamais l'identité (===) : l'hôte rendu par
            // [preuveDeConfinement] est une instance de SON scan interne, tandis que
            // cette liste vient du re-scan de diagnostiquerRapide — DEUX scans de la
            // même source produisent des instances DISTINCTES. Avec ===, l'hôte
            // n'était JAMAIS reconnu : il était masqué par le synthétique comme les
            // autres corps, la frappe de l'utilisateur était EFFACÉE du texte
            // diagnostiqué (0 diagnostic pour une erreur fraîche — v1.1.0, mesuré
            // sur JVM ET appareil : « l'éditeur n'affiche rien quand je tape »).
            // PlageCorps est une data class ; les plages d'une même liste étant
            // disjointes, l'égalité structurelle ne peut confondre deux corps.
            if (plage == hote) continue
            if (plage.debutInterieur < frontierePrecedente) continue // imbriqué : déjà couvert
            if (plage.longueur >= SYNTHETIQUE.length) {
                for (k in 0 until SYNTHETIQUE.length) {
                    resultat[plage.debutInterieur + k] = SYNTHETIQUE[k]
                }
                for (k in plage.debutInterieur + SYNTHETIQUE.length until plage.finInterieur) {
                    resultat[k] = ' '
                }
            }
            frontierePrecedente = plage.finInterieur
        }
        return String(resultat)
    }

    /**
     * Sépare les diagnostics PORTÉS (dans les corps masqués) des
     * diagnostics de structure (hors corps).
     *
     * Les corps masqués ont un intérieur IDENTIQUE entre l'ancienne et la
     * nouvelle source, mais leurs positions ABSOLUES peuvent être décalées
     * (le corps hôte a grandi ou rétréci) : les diagnostics portés sont
     * TRANSLATÉS du décalage de leur corps, de sorte que leurs offsets
     * correspondent EXACTEMENT à ceux du témoin (diagnose complet de la
     * nouvelle source).
     *
     * @param diagnostics le résultat précédent (offsets = ancienne source).
     * @param corpsAnciens les plages de corps de l'ancienne source.
     * @param corpsNouveaux les plages de corps de la nouvelle source (même
     *   cardinalité, index alignés).
     * @param indexHote l'index du corps hôte (exclu du portage).
     * @return les diagnostics portés et translatés ; le reste est recalculé
     *   frais sur la source masquée.
     */
    public fun portables(
        diagnostics: List<jo.codeide.lsp.java.diagnostics.Diagnostic>,
        corpsAnciens: List<PlageCorps>,
        corpsNouveaux: List<PlageCorps>,
        indexHote: Int,
    ): List<jo.codeide.lsp.java.diagnostics.Diagnostic> {
        val resultats = mutableListOf<jo.codeide.lsp.java.diagnostics.Diagnostic>()
        for (diagnostic in diagnostics) {
            val index = corpsAnciens.indices.firstOrNull { index ->
                index != indexHote &&
                    // Seuls les corps EFFECTIVEMENT masqués portent : les corps
                    // trop courts (laissés verbatim) et vides sont recalculés
                    // frais par la résolution de la source masquée — les porter
                    // les DUPLIQUERAIT.
                    corpsNouveaux[index].longueur >= SYNTHETIQUE.length &&
                    corpsAnciens[index].debutInterieur <= diagnostic.plage.debutOffset &&
                    diagnostic.plage.finOffset <= corpsAnciens[index].finInterieur
            } ?: continue // hors corps (ou verbatim) : recalculé frais
            val ancien = corpsAnciens[index]
            val nouveau = corpsNouveaux[index]
            val decalage = nouveau.debutInterieur - ancien.debutInterieur
            resultats.add(
                diagnostic.copy(
                    plage = jo.codeide.lsp.java.diagnostics.Plage(
                        diagnostic.plage.debutOffset + decalage,
                        diagnostic.plage.finOffset + decalage,
                    ),
                ),
            )
        }
        return resultats
    }

    /** Le résultat du portage hors-corps : le PUBLIÉ (gabarité) et le PORTÉ (traduit, pour l'état suivant). */
    public class PortageHorsCorps(
        /** Les signaux publiés par CETTE passe (gabarit du témoin appliqué). */
        public val publies: List<jo.codeide.lsp.java.diagnostics.Diagnostic>,
        /** Les signaux TRANSLATÉS non filtrés — la source de portage de la passe suivante. */
        public val portes: List<jo.codeide.lsp.java.diagnostics.Diagnostic>,
    )

    /**
     * Les diagnostics ANALYTIQUES hors corps, PORTÉS du dernier diagnostic
     * complet (retours appareil du 30/10/2026 : « le fichier des imports
     * non utilisés, variables non utilisées et autres, il ne les affiche
     * pas » — ces signaux DISPARAISSAIENT à la première frappe dans un
     * corps, la passe rapide ne pouvant pas les recalculer).
     *
     * **Pourquoi porter et non recalculer** : l'analyse d'usage d'un
     * import ou d'un membre privé traverse TOUT le fichier — le masquage
     * des corps non hôtes casserait cette analyse (fiche 014, piège
     * « import utilisé seulement par un corps masqué » : un recalcul
     * fabriquerait des faux « unused »). Le signal du dernier diagnose
     * COMPLET (corps entiers visibles) reste donc la seule source honnête
     * pendant la frappe.
     *
     * **Re-validation par gabarit du témoin (vague V-A, ADR 0025 — règles
     * MESURÉES sur ecj 3.40 le 31/10/2026)** : le portage ne doit porter un
     * signal QUE si un diagnose COMPLET de la même source le publierait —
     * sinon la divergence P1 (équivalence rapide/complet) naît du portage
     * lui-même. Règles mesurées : l'IMPORT inutilisé n'est publié par le
     * témoin que si l'unité est SANS AUCUNE erreur (une erreur de simple
     * résolution suffit à le taire) ; le MEMBRE PRIVÉ inutilisé n'est
     * publié que si l'unité est sans erreur de SYNTAXE. La locale
     * inutilisée n'est pas concernée : périmètre par méthode, recalculée
     * fraîche sur le corps hôte et portée sur les masqués.
     *
     * **Translation des positions** : la preuve de confinement garantit
     * que le texte HORS corps est identique, segment par segment — seule
     * la LONGUEUR du corps hôte varie. Un diagnostic entièrement AVANT le
     * corps hôte garde ses offsets ; un diagnostic entièrement APRÈS est
     * translaté du delta de fin du corps hôte ; un diagnostic à cheval sur
     * la frontière du corps hôte est ABANDONNÉ (sans position fiable).
     *
     * **Fraîcheur bornée (écart assumé, même doctrine que [portables])** :
     * une frappe DANS le corps hôte qui ajoute le PREMIER usage d'un
     * import/membre jusque-là inutilisé ne révoque pas immédiatement le
     * signal porté — le prochain diagnose complet (caret hors de tout
     * corps, sauvegarde, ou changement de corps édité) le révoque. C'est
     * le même compromis que le portage des corps masqués : jamais de
     * fabrication, une révocation différée bornée par la frappe suivante.
     *
     * @param diagnostics les signaux analytiques hors corps du dernier état
     *   (offsets = ancienne source) — la source PERSISTANTE, jamais le
     *   résultat publié (une publication tue ne doit pas détruire le signal).
     * @param corpsAnciens les plages de corps de l'ancienne source.
     * @param corpsNouveaux les plages de corps de la nouvelle source (même
     *   cardinalité, index alignés).
     * @param indexHote l'index du corps hôte.
     * @param aUneErreur vrai ssi la nouvelle passe (frais sur la source
     *   masquée + corps masqués portés) porte AU MOINS une erreur — le
     *   témoin ne publie jamais l'import inutilisé sur une unité fautive.
     * @param aUneErreurSyntaxe vrai ssi elle porte au moins une erreur de
     *   SYNTAXE ([estErreurSyntaxe]) — le témoin ne publie jamais le membre
     *   privé inutilisé sur une unité syntaxiquement fautive.
     * @return le portage : les signaux PUBLIÉS (gabarités) et les signaux
     *   PORTÉS traduits (source de la passe suivante).
     */
    public fun portablesHorsCorps(
        diagnostics: List<jo.codeide.lsp.java.diagnostics.Diagnostic>,
        corpsAnciens: List<PlageCorps>,
        corpsNouveaux: List<PlageCorps>,
        indexHote: Int,
        aUneErreur: Boolean,
        aUneErreurSyntaxe: Boolean,
    ): PortageHorsCorps {
        if (indexHote < 0 || indexHote >= corpsAnciens.size) {
            return PortageHorsCorps(emptyList(), emptyList())
        }
        val hoteAncien = corpsAnciens[indexHote]
        val hoteNouveau = corpsNouveaux[indexHote]
        val decalageApresHote = hoteNouveau.finInterieur - hoteAncien.finInterieur
        val publies = mutableListOf<jo.codeide.lsp.java.diagnostics.Diagnostic>()
        val portes = mutableListOf<jo.codeide.lsp.java.diagnostics.Diagnostic>()
        for (diagnostic in diagnostics) {
            // Familles ANALYTIQUES seules : celles que la table rapide ne
            // produit PAS (import inutilisé, membre privé inutilisé) — les
            // recaler ici ne peut JAMAIS dupliquer un diagnostic frais.
            val famille = FamilleProbleme.fromCode(diagnostic.code)
            if (famille != FamilleProbleme.UNUSED_IMPORT && famille != FamilleProbleme.UNUSED_DECLARATION) continue
            // HORS corps uniquement : un diagnostic DANS un corps est déjà
            // servi (frais sur l'hôte, porté par [portables] sur les masqués).
            val dansUnCorps = corpsAnciens.any { plage ->
                plage.debutInterieur <= diagnostic.plage.debutOffset &&
                    diagnostic.plage.finOffset <= plage.finInterieur
            }
            if (dansUnCorps) continue
            // Position relative au corps hôte : avant → tel quel ; après →
            // translaté du delta de fin ; à cheval → abandonné (perte de
            // position, le signal ne survit pas à cette passe).
            val plage = when {
                diagnostic.plage.finOffset <= hoteAncien.debutInterieur ->
                    diagnostic.plage
                diagnostic.plage.debutOffset >= hoteAncien.finInterieur ->
                    jo.codeide.lsp.java.diagnostics.Plage(
                        diagnostic.plage.debutOffset + decalageApresHote,
                        diagnostic.plage.finOffset + decalageApresHote,
                    )
                else -> continue
            }
            val traduit = diagnostic.copy(plage = plage)
            portes.add(traduit)
            // GABARIT DU TÉMOIN (re-validation, vague V-A — P1/G-002) : la
            // PUBLICATION seule est gabaritée — le signal TRADUIT survit dans
            // l'état (une frappe cassée ne détruit jamais le portage).
            if (famille == FamilleProbleme.UNUSED_IMPORT && aUneErreur) continue
            if (famille == FamilleProbleme.UNUSED_DECLARATION && aUneErreurSyntaxe) continue
            publies.add(traduit)
        }
        return PortageHorsCorps(publies, portes)
    }

    /**
     * Vrai ssi le diagnostic est une ERREUR DE SYNTAXE au sens ecj — le
     * masque de catégorie [org.eclipse.jdt.core.compiler.IProblem.Syntax]
     * (mesuré à `0x40000000` sur le jar 3.40.0-art ; les IDs vivants portent
     * aussi `Internal`, p. ex. `ParsingErrorInsertToComplete` = `0x600000F0`).
     * Le code moteur est l'ID nu en chaîne ([RegimeInstantane.convertir]) ;
     * toute forme non numérique n'est pas classée syntaxe (garde prudente).
     *
     * @param diagnostic le diagnostic à classifier.
     * @return vrai ssi l'ID porte le masque Syntax.
     */
    public fun estErreurSyntaxe(diagnostic: jo.codeide.lsp.java.diagnostics.Diagnostic): Boolean {
        val id = diagnostic.code?.removePrefix("ecj:")?.toLongOrNull() ?: return false
        return (id and SYNTAXE) != 0L
    }

    /**
     * L'empreinte STAT d'un fichier (mtime milliseconde + taille) — la clé de
     * fraîcheur des dépendances du mémo (P8) ; null si introuvable ou
     * illisible (vaut « changé » dès que la lecture avait réussi à la passe
     * qui a enregistré l'empreinte).
     *
     * **Pourquoi un stat et non un hachage de contenu** : l'empreinte est
     * prise à CHAQUE passe (enregistrement) et vérifiée à chaque mémo — un
     * SHA-256 du contenu y coûterait des millisecondes par frappe (mesuré :
     * diagnose rapide p50 1,5 → 5 ms, banc N1, corrigé V-A). Le stat coûte
     * des microsecondes. **Résidu documenté** : une réécriture de MÊME taille
     * dans la MÊME milliseconde que l'enregistrement échapperait à la garde
     * (granularité fs) — fenêtre bornée par la durée d'une passe, hors
     * pratique réelle ; la republication hôte (overlay des documents ouverts,
     * ADR 0025 étape 4) ferme ce résidu.
     *
     * @param fichier le fichier à empreinter.
     * @return l'empreinte "mtime:taille", ou null si le fichier n'est pas lisible.
     */
    public fun empreinteFichier(fichier: Path): String? = try {
        val attributs = Files.readAttributes(
            fichier,
            java.nio.file.attribute.BasicFileAttributes::class.java,
        )
        "${attributs.lastModifiedTime().toMillis()}:${attributs.size()}"
    } catch (_: Exception) {
        null
    }

    /** Masque de catégorie Syntax d'IProblem (0x40000000, mesuré jar 3.40.0). */
    private const val SYNTAXE: Long =
        org.eclipse.jdt.core.compiler.IProblem.Syntax.toLong()
}
