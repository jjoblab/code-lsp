// CodeIDE — aide à la signature d'un document Kotlin (module :kotlin, étage
// 6 vague 6d). Code NEUF jjoblab (contrat : fiches 051 et 018 du cahier
// docs/cahier-specifications-kotlin/ — l'aide à la signature S1 : l'appel
// englobant le plus profond, les candidates projet ET classpath, l'arité
// compatible, le paramètre actif, la KDoc qui accompagne).
package jo.codeide.lsp.kotlin.session

import jo.codeide.lsp.kotlin.api.AideSignatureKotlin
import jo.codeide.lsp.kotlin.api.SignatureKotlin
import jo.codeide.lsp.kotlin.documentation.DocumentationKotlin
import jo.codeide.lsp.kotlin.parse.NoeudArbre
import jo.codeide.lsp.kotlin.parse.kt.KtClasse
import jo.codeide.lsp.kotlin.parse.kt.KtDeclaration
import jo.codeide.lsp.kotlin.parse.kt.KtFonction
import jo.codeide.lsp.kotlin.parse.kt.KtObjet
import jo.codeide.lsp.kotlin.resolve.AppelableProjet
import jo.codeide.lsp.kotlin.resolve.DescripteursJvm
import jo.codeide.lsp.kotlin.resolve.Couches
import jo.codeide.lsp.kotlin.resolve.MarcheTypee
import jo.codeide.lsp.kotlin.resolve.MembreChaineJvm
import jo.codeide.lsp.kotlin.resolve.TypeValeur
import jo.codeide.lsp.kotlin.resolve.TypageBorne
import jo.codeide.lsp.kotlin.resolve.UniversPreuves
import org.jetbrains.kotlin.kmp.lexer.KtTokens
import org.jetbrains.kotlin.kmp.parser.KtNodeTypes

/**
 * Calcul de l'AIDE À LA SIGNATURE (pur — consulte les documents ouverts et
 * le snapshot du scan, appelé sous verrou de lecture).
 *
 * ## L'appel englobant (fiche 051)
 *
 * Le caret désigne une place dans une liste d'arguments : la chaîne des
 * appels ANCESTRES se parcourt du PLUS PROFOND au plus externe — le
 * premier qui porte des candidates gagne (l'appel niché prime). Un caret
 * HORS liste (avant la parenthèse, après l'appel FERMÉ) ne désigne rien :
 * silence (D3).
 *
 * ## Le paramètre actif (fiche 051)
 *
 * Zéro après la parenthèse ouvrante, +1 après chaque virgule (le compte
 * des arguments ÉCRITS avant le caret) ; un lambda FINAL en position
 * compte pour l'argument SUIVANT (le caret dans le littéral désigne la
 * place après les arguments de la liste).
 *
 * ## Les candidates (fiches 051/018)
 *
 * Le MEMBRE VIF de la classe englobante d'abord (le membre ombrage), puis
 * les FONCTIONS du projet (surcharges comprises — la signature ACTIVE est
 * l'arité compatible : assez de paramètres pour servir la place du caret,
 * les défauts élargissant l'arité légale), le CONSTRUCTEUR PRIMAIRE, puis
 * le CLASSPATH (appelables de premier niveau par l'index réel). Un appel
 * QUALIFIÉ (`v.setText(…)`) résout par le type du RÉCEPTEUR : la méthode
 * JVM de la vue (le bytecode ne nomme pas ses paramètres — types seuls).
 *
 * ## La KDoc des candidates (fiche 018)
 *
 * La candidate du document COURANT porte la KDoc de son arbre VIF ; une
 * candidate d'un fichier tiers OUVERT porte celle de l'arbre VIVANT (le
 * disque périmé n'est jamais consulté) ; une candidate d'un fichier FERMÉ
 * porte celle du PORT (lecture bornée au scan) ; la candidate du
 * CLASSPATH reste MUETTE (le bytecode ne porte pas de documentation, D3).
 */
internal object SignatureDocuments {

    /**
     * Rend l'aide à la signature au point demandé.
     *
     * @param cible le document ouvert visé ;
     * @param autres les autres documents ouverts (arbres VIVANTS) ;
     * @param scan le snapshot du scan publié (`null` sans rafraîchissement) ;
     * @param deplacement l'offset du caret ;
     * @param univers l'univers des preuves (modèle + index + vue JVM) ;
     * @return l'aide ; `null` hors appel ou sans candidate (silence D3).
     */
    internal fun signature(
        cible: DocumentOuvert,
        autres: List<Pair<String, DocumentOuvert>>,
        scan: ScanSources?,
        deplacement: Int,
        univers: UniversPreuves?,
    ): AideSignatureKotlin? {
        // La chaîne des ancêtres du caret, du plus profond à la racine :
        // les APPELS s'y lisent dans l'ordre de priorité (le niché gagne).
        val chaine = chaineAncetres(cible.arbre.noeud, deplacement).asReversed()
        for (noeud in chaine) {
            if (noeud.genre != KtNodeTypes.CALL_EXPRESSION) continue
            val actif = parametreActif(noeud, deplacement) ?: continue
            val candidates = candidates(cible, autres, scan, noeud, univers) ?: continue
            if (candidates.isEmpty()) continue
            val active = candidates.indexOfFirst { it.parametres.size > actif }.takeIf { it >= 0 } ?: 0
            return AideSignatureKotlin(
                signatures = candidates,
                signatureActive = active,
                parametreActif = actif,
            )
        }
        return null
    }

    // ------------------------------------------------------------------
    // La place du caret dans la liste d'arguments.
    // ------------------------------------------------------------------

    /**
     * Le paramètre ACTIF de l'appel au point demandé.
     *
     * @param appel le nœud `CALL_EXPRESSION` ;
     * @param deplacement l'offset du caret ;
     * @return l'index du paramètre actif ; `null` si le caret est HORS de
     * la liste (avant la parenthèse, après l'appel fermé).
     */
    private fun parametreActif(appel: NoeudArbre, deplacement: Int): Int? {
        val liste = appel.enfants.firstOrNull { it.genre == KtNodeTypes.VALUE_ARGUMENT_LIST } ?: return null
        val callee = appel.enfants.firstOrNull { it.genre == KtNodeTypes.REFERENCE_EXPRESSION }
        val finCallee = callee?.fin ?: appel.debut
        val lambda = appel.enfants.firstOrNull { it.genre == KtNodeTypes.LAMBDA_ARGUMENT }
        // La liste est OUVERTE en (finCallee, fin) — le caret sur la
        // parenthèse fermante désigne ENCORE la dernière place, le caret
        // APRÈS l'appel fermé ne désigne plus rien (fiche 051 : silence).
        val dansListe = deplacement > finCallee && deplacement < liste.fin
        val dansLambda = lambda != null && deplacement >= lambda.debut && deplacement < lambda.fin
        if (!dansListe && !dansLambda) return null
        val arguments = liste.enfants.filter { it.genre == KtNodeTypes.VALUE_ARGUMENT }
        return if (dansLambda && !dansListe) {
            // Le lambda FINAL compte pour l'argument SUIVANT : la place
            // active est celle d'après les arguments de la liste.
            arguments.size
        } else {
            arguments.count { it.fin <= deplacement }
        }
    }

    // ------------------------------------------------------------------
    // Les candidates de l'appel.
    // ------------------------------------------------------------------

    /**
     * Les candidates de l'appel : QUALIFIÉ (le sélecteur d'un récepteur
     * typé — la méthode JVM ou le membre projet du récepteur) ou NU (le
     * membre vif de la classe englobante, la fonction du projet, le
     * constructeur primaire, l'appelable de premier niveau du classpath).
     */
    private fun candidates(
        cible: DocumentOuvert,
        autres: List<Pair<String, DocumentOuvert>>,
        scan: ScanSources?,
        appel: NoeudArbre,
        univers: UniversPreuves?,
    ): List<SignatureKotlin>? {
        val nom = appel.enfants
            .firstOrNull { it.genre == KtNodeTypes.REFERENCE_EXPRESSION }
            ?.texteCanonique.orEmpty()
        if (nom.isEmpty()) return null
        if (univers == null) return null

        // Un appel QUALIFIÉ : le sélecteur d'une expression qualifiée — le
        // RÉCEPTEUR marche (son type porte la candidate).
        val qualifie = qualifieeCouvrante(cible.arbre.noeud, appel)
        if (qualifie != null) {
            candidatsQualifies(cible, appel, nom, qualifie, univers)?.let { return it }
        }

        // Le MEMBRE VIF de la classe englobante (le membre ombrage le
        // premier niveau — `ranger()` dans sa classe).
        membresEnglobants(cible, univers, appel, nom)?.let { return it }

        // Les FONCTIONS du projet (surcharges comprises).
        val fonctions = univers.modele.appelables(nom, univers.paquet)
        if (fonctions.isNotEmpty()) {
            return fonctions.map { appelable ->
                SignatureKotlin(
                    etiquette = etiquetteProjet(nom, appelable.parametres.map(::renduParametre), appelable.typeRetourNomme),
                    parametres = appelable.parametres.map(::renduParametre),
                    documentation = kdocCandidate(cible, autres, scan, appelable.nom, appelable.uri, appelable.estFonction),
                )
            }
        }

        // Le CONSTRUCTEUR PRIMAIRE d'une classe du projet.
        val type = univers.typeProjetEcrit(nom)
        if (type != null && type.parametresPrimaire.isNotEmpty()) {
            val parametres = type.parametresPrimaire.map(::renduParametre)
            return listOf(
                SignatureKotlin(
                    etiquette = etiquetteProjet(nom, parametres, null),
                    parametres = parametres,
                    documentation = null,
                ),
            )
        }
        if (type != null) {
            val parametres = type.parametresPrimaire.map(::renduParametre)
            return listOf(
                SignatureKotlin(etiquette = etiquetteProjet(nom, parametres, null), parametres = parametres, documentation = null),
            )
        }

        // Les APPELABLES de premier niveau du CLASSPATH (index réel — le
        // stdlib `listOf` ; la documentation reste MUETTE, bytecode D3).
        val top = univers.index?.topParNom(nom)
            ?.filter { it.paquet == univers.paquet || it.paquet in UniversPreuves.PAQUETS_DEFAUT }
            .orEmpty()
        if (top.isNotEmpty()) {
            return top.map { appelable ->
                val parametres = appelable.parametres.map { parametre ->
                    when {
                        parametre.nom != null && parametre.type != null -> "${parametre.nom}: ${parametre.type}"
                        parametre.type != null -> parametre.type
                        else -> parametre.nom.orEmpty()
                    }
                }
                SignatureKotlin(
                    etiquette = etiquetteProjet(nom, parametres, appelable.typeRetour),
                    parametres = parametres,
                    documentation = null,
                )
            }
        }
        return null
    }

    /**
     * Les candidates d'un appel QUALIFIÉ : le type du RÉCEPTEUR porte la
     * candidate — projet (la clôture d'héritage) ou bibliothèque (la vue
     * JVM, descripteurs seuls).
     */
    private fun candidatsQualifies(
        cible: DocumentOuvert,
        appel: NoeudArbre,
        nom: String,
        qualifie: NoeudArbre,
        univers: UniversPreuves,
    ): List<SignatureKotlin>? {
        val recepteur = qualifie.enfants.firstOrNull() ?: return null
        if (recepteur.fin > appel.debut) return null
        val region = regionContenant(cible.arbre.noeud, appel.debut)
        val liaison = MarcheTypee.Liaison(arbre = cible.arbre, univers = univers, region = region)
        val pile = Couches.aPosition(cible.arbre, univers, liaison, appel.debut)
        val type = TypageBorne.typeDeLexpression(recepteur, univers, liaison, pile) ?: return null
        return when (type) {
            is TypeValeur.Projet -> {
                // Le membre DÉCLARÉ du type du projet (les paramètres y
                // sont portés) ; un membre seulement HÉRITÉ (clôture sans
                // paramètres lisibles) reste muet — jamais une signature
                // inventée (D3).
                val membre = type.type.membre(nom) ?: return null
                if (membre.genre != jo.codeide.lsp.kotlin.resolve.GenreMembreProjet.FONCTION) return null
                val parametres = membre.parametres.map(::renduParametre)
                listOf(
                    SignatureKotlin(
                        etiquette = etiquetteProjet(nom, parametres, membre.typeRetourNomme),
                        parametres = parametres,
                        documentation = kdocDuDocument(cible, nom),
                    ),
                )
            }
            is TypeValeur.Jvm -> {
                val vue = univers.vueJvm ?: return null
                val chaine = vue.membreChaine(univers.mapeJvm(type.fqcn), nom) ?: return null
                // Le rendu EXACT depuis les descripteurs BRUTS du
                // PROPRIÉTAIRE DÉCLARANT : le `V` du void s'y rend
                // `Unit` (fiche 018 : `setText(CharSequence): Unit`) —
                // le dépouillement standard muet le void, la signature
                // ne se tait jamais sur lui.
                val methodes = vue.membresDeclares(chaine.proprietaire)
                    ?.membres
                    ?.filter { !it.estChamp && it.nom == nom }
                    .orEmpty()
                if (methodes.isNotEmpty()) {
                    return methodes.map { methode ->
                        val parametres = methode.parametres().orEmpty().map(::nomSimple)
                        val retour = retourJvm(methode.descripteur)
                        SignatureKotlin(
                            etiquette = "$nom(${parametres.joinToString(", ")})" +
                                (retour?.let { ": $it" } ?: ""),
                            parametres = parametres,
                            documentation = null,
                        )
                    }
                }
                listOf(renduJvm(chaine, nom))
            }
        }
    }

    /**
     * Le RETOUR d'un descripteur de méthode : `V` (void) se rend `Unit`
     * (fiche 018), un type d'objet son nom simple, un TABLEAU reste muet
     * (D3 — jamais un type inventé).
     */
    private fun retourJvm(descripteur: String): String? {
        val fermeture = descripteur.indexOf(')')
        if (fermeture < 0) return null
        return when (val reste = descripteur.substring(fermeture + 1)) {
            "V" -> "Unit"
            "" -> null
            else -> DescripteursJvm.typeNu(reste)?.let(::nomSimple)
        }
    }

    /** Le rendu d'une méthode JVM : types SEULS (le bytecode ne nomme pas). */
    private fun renduJvm(chaine: MembreChaineJvm, nom: String): SignatureKotlin {
        val premiere = chaine.signatures.firstOrNull() ?: return SignatureKotlin(nom, emptyList(), null)
        val parametres = premiere.parametres.orEmpty().map(::nomSimple)
        val retour = premiere.retour?.let(::nomSimple)
        val etiquette = "$nom(${parametres.joinToString(", ")})" + (retour?.let { ": $it" } ?: "")
        return SignatureKotlin(etiquette = etiquette, parametres = parametres, documentation = null)
    }

    /**
     * Les MEMBRES VIFS de la classe englobante de l'appel : la classe (ou
     * l'objet) la plus proche dans la chaîne des ancêtres porte le membre
     * du nom — le membre ombrage le premier niveau.
     */
    private fun membresEnglobants(
        cible: DocumentOuvert,
        univers: UniversPreuves,
        appel: NoeudArbre,
        nom: String,
    ): List<SignatureKotlin>? {
        val englobante = chaineAncetres(cible.arbre.noeud, appel.debut)
            .lastOrNull { it.genre == KtNodeTypes.CLASS || it.genre == KtNodeTypes.OBJECT_DECLARATION }
            ?: return null
        val nomClasse = englobante.jetonsPropres.firstOrNull { it.genre == KtTokens.IDENTIFIER }?.texte ?: return null
        val type = univers.typeProjetEcrit(nomClasse) ?: return null
        val membres = type.membres.filter { it.nom == nom && it.genre == jo.codeide.lsp.kotlin.resolve.GenreMembreProjet.FONCTION }
        if (membres.isEmpty()) return null
        return membres.map { membre ->
            val parametres = membre.parametres.map(::renduParametre)
            SignatureKotlin(
                etiquette = etiquetteProjet(nom, parametres, membre.typeRetourNomme),
                parametres = parametres,
                documentation = kdocMembreEnglobant(cible, nom),
            )
        }
    }

    // ------------------------------------------------------------------
    // Les rendus.
    // ------------------------------------------------------------------

    /** L'étiquette d'une candidate du projet : `nom(params): Retour?`. */
    private fun etiquetteProjet(nom: String, parametres: List<String>, retour: String?): String =
        "$nom(${parametres.joinToString(", ")})" + (retour?.let { ": $it" } ?: "")

    /** Le rendu d'un paramètre du projet : `nom: Type` (le type écrit seul sinon). */
    private fun renduParametre(parametre: jo.codeide.lsp.kotlin.resolve.ParametreProjet): String = when {
        parametre.nom != null && parametre.typeEcrit != null -> "${parametre.nom}: ${parametre.typeEcrit}"
        parametre.typeEcrit != null -> parametre.typeEcrit
        parametre.nom != null -> parametre.nom
        else -> ""
    }

    /** Le nom SIMPLE d'un FQCN (`java.lang.CharSequence` → `CharSequence`). */
    private fun nomSimple(fqcn: String): String =
        fqcn.substringAfterLast('.').substringAfterLast('$')

    // ------------------------------------------------------------------
    // La KDoc des candidates (fiche 018).
    // ------------------------------------------------------------------

    /**
     * La KDoc d'une candidate du projet : le document COURANT par son
     * arbre VIF, un fichier tiers OUVERT par son arbre VIVANT (le disque
     * périmé n'est jamais consulté), un fichier FERMÉ par le PORT (la KDoc
     * du scan).
     */
    private fun kdocCandidate(
        cible: DocumentOuvert,
        autres: List<Pair<String, DocumentOuvert>>,
        scan: ScanSources?,
        nom: String,
        uri: String,
        estFonction: Boolean,
    ): String? {
        if (!estFonction) return null
        // Le document COURANT (l'URI vide de sa contribution).
        if (uri.isEmpty()) return kdocDuDocument(cible, nom)
        // Un fichier tiers OUVERT : l'arbre VIVANT évince le disque
        // périmé (fiche 018 — la KDoc vient de l'arbre vif).
        val tiers = autres.firstOrNull { (uriDocument, _) -> uriDocument == uri }
        if (tiers != null) return kdocDuDocument(tiers.second, nom)
        // Un fichier FERMÉ : la KDoc du PORT (lecture bornée au scan).
        return scan?.declaration(nom)?.documentation
    }

    /** La KDoc d'une FONCTION NOMMÉE d'un arbre VIVANT (la plus proche). */
    private fun kdocDuDocument(document: DocumentOuvert, nom: String): String? {
        val declaration = SurvolDocuments.declarationsRecursives(document.arbre)
            .filterIsInstance<KtFonction>()
            .firstOrNull { it.nom() == nom }
            ?: return null
        return DocumentationKotlin.kdocDe(declaration, document.arbre.noeud.commentaires)
    }

    /**
     * La KDoc du MEMBRE de la classe englobante : la déclaration VIVE de
     * la fonction nommée dans le document courant (la classe la plus
     * proche porte le membre — la KDoc vive de la fiche 018).
     */
    private fun kdocMembreEnglobant(cible: DocumentOuvert, nom: String): String? =
        kdocDuDocument(cible, nom)

    // ------------------------------------------------------------------
    // La navigation.
    // ------------------------------------------------------------------

    /**
     * La chaîne des ancêtres du point (de la racine au plus profond).
     */
    private fun chaineAncetres(racine: NoeudArbre, position: Int): List<NoeudArbre> {
        val chaine = mutableListOf<NoeudArbre>()
        var courant: NoeudArbre = racine
        chaine.add(courant)
        while (position >= courant.debut && position < courant.fin) {
            val enfant = courant.enfants.firstOrNull {
                it.debut <= position && position < it.fin
            } ?: break
            chaine.add(enfant)
            courant = enfant
        }
        return chaine
    }

    /**
     * L'expression QUALIFIÉE couvrante d'un appel (le sélecteur en
     * DERNIER enfant d'un `DOT_QUALIFIED_EXPRESSION`/
     * `SAFE_ACCESS_EXPRESSION`).
     */
    private fun qualifieeCouvrante(racine: NoeudArbre, appel: NoeudArbre): NoeudArbre? {
        val chaine = chaineAncetres(racine, appel.debut)
        val index = chaine.indexOf(appel)
        if (index <= 0) return null
        val parent = chaine[index - 1]
        if (parent.genre != KtNodeTypes.DOT_QUALIFIED_EXPRESSION &&
            parent.genre != KtNodeTypes.SAFE_ACCESS_EXPRESSION
        ) {
            return null
        }
        if (parent.enfants.lastOrNull() != appel) return null
        return parent
    }

    /**
     * La RÉGION de portée contenant le point (miroir de la marche typée —
     * la fonction englobante dont les locales lient).
     */
    private fun regionContenant(racine: NoeudArbre, position: Int): NoeudArbre? =
        chaineAncetres(racine, position).lastOrNull { it.genre == KtNodeTypes.FUN }
}
