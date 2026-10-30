// CodeIDE — typage BORNÉ étendu (module :kotlin, étage 2 vague 2b — fiche
// 046 : familles sémantiques K4). Code NEUF jjoblab (clean-room — l'ancien
// `resolve/` est en lecture interdite R9.1 ; contrat : ADR 0107 « périmètre
// assumé, preuve ou silence » + ADR 0223 « sélection par types complets » +
// fiche 046 : smart casts POSITIONNELS, lambdas par ATTENTE, littéraux).
package jo.codeide.lsp.kotlin.resolve

import jo.codeide.lsp.kotlin.parse.NoeudArbre
import jo.codeide.lsp.kotlin.parse.kt.KtFichier
import org.jetbrains.kotlin.kmp.lexer.KtTokens
import org.jetbrains.kotlin.kmp.parser.KtNodeTypes

/**
 * Le type d'une VALEUR du document borné à ce qui se DÉCIDE (ADR 0107) : un
 * type du PROJET (modèle de symboles) ou un type de BIBLIOTHÈQUE connu par
 * son FQCN (classpath, mappé Kotlin) — le reste n'est pas typé (`null`,
 * sélecteur muet, D3).
 */
internal sealed class TypeValeur {

    /** Un type du PROJET (classe, interface, enum, objet — modèle 2a). */
    internal class Projet(internal val type: TypeProjet) : TypeValeur()

    /** Un type de BIBLIOTHÈQUE (`kotlin.String`, `android.widget.TextView`). */
    internal class Jvm(internal val fqcn: String) : TypeValeur()
}

/**
 * Le typage borné ÉTENDU de la vague 2b : l'expression typée par ses LIENS
 * (types écrits, initialiseurs — miroir de [MarcheTypee]), par ses
 * LITTÉRAUX, par les SMART CASTS positionnels (branche THEN d'un `if`,
 * garde `!is` à sortie précoce, opérande droit d'un `&&`, entrée d'un `when`
 * sans sujet — l'ombrage par une locale coupe, `||` annule) et par
 * l'ATTENTE des lambdas (paramètre explicite, `it`, SAM projet, type de
 * fonction écrit).
 *
 * Conservateur par construction (D3) : ce qui ne se décide pas rend `null`.
 */
internal object TypageBorne {

    // ------------------------------------------------------------------
    // Le typage d'une expression.
    // ------------------------------------------------------------------

    /**
     * Le type borné d'une EXPRESSION du document (fiche 046 ; vague 2c —
     * la PILE DES THIS type `this`, les membres IMPLICITES hérités
     * `layoutInflater`/`compte`, les références de classe IMPORTÉES
     * `Peinture`, les RETOURS de membres de bibliothèque et les objets
     * ANONYMES).
     *
     * @param noeud l'expression à typer ;
     * @param univers l'univers des preuves (modèle + imports + index) ;
     * @param liaison la liaison nom → type écrit des portées ;
     * @param pile la pile des this de la position (vague 2c — vide au
     * niveau du document) ;
     * @return le type ; `null` si l'expression n'est pas typable à cet
     * étage (le sélecteur reste muet, D3).
     */
    internal fun typeDeLexpression(
        noeud: NoeudArbre,
        univers: UniversPreuves,
        liaison: MarcheTypee.Liaison,
        pile: PileThis = PileThis.VIDE,
    ): TypeValeur? {
        return when (noeud.genre) {
            KtNodeTypes.REFERENCE_EXPRESSION -> {
                val nom = noeud.texteCanonique
                if (nom.isEmpty()) return null
                // `it` implicite : l'attente à paramètre unique le type
                // (une locale nommée `it` prime — garde documentaire).
                if (nom == "it" && !univers.possedeNomDeclare("it")) {
                    attenteLambdaAuPoint(univers.arbre, noeud.debut, univers)
                        ?.takeIf { it.size == 1 }
                        ?.let { return univers.resoudreTypeEcrit(it.single()) }
                }
                // Un PARAMÈTRE DE LAMBDA explicite : l'attente de la lambda
                // le type à son INDEX (fiche 046 — `repeter(2) { s -> … }`).
                attenteLambdaAuPoint(univers.arbre, noeud.debut, univers)?.let { attente ->
                    indiceParametreLambda(univers.arbre, noeud.debut, nom)?.let { indice ->
                        attente.getOrNull(indice)?.let { return univers.resoudreTypeEcrit(it) }
                    }
                }
                // Le SMART CAST positionnel prime sur le type écrit (fiche
                // 046 : le cast raffine le sujet en position).
                raffinementAuPoint(univers.arbre, noeud.debut, nom)?.let { raffinement ->
                    raffinement.typeNom?.let { ecrit ->
                        univers.resoudreTypeEcrit(ecrit)?.let { return it }
                    }
                    if (raffinement.nonNul) {
                        // Non-nullifié : le type écrit DÉBALLÉ (sans `?`).
                        liaison(nom)?.let { ecrit ->
                            univers.resoudreTypeEcrit(ecrit)?.let { return it }
                        }
                    }
                }
                // Le type ÉCRIT de la liaison (paramètre, locale, membre) —
                // la consultation POSITIONNELLE rend l'OMBRE interne (la
                // locale postérieure gagne, fiche 046).
                liaison(nom, noeud.debut)?.let { ecrit ->
                    univers.resoudreTypeEcrit(ecrit)?.let { return it }
                }
                // Un type IMBRIQUÉ d'un SUPERTYPE en nom simple (vague 2c
                // — fiche 028 : `Resultats` nu type `Base$Resultats`).
                pile.membre(nom)?.takeIf { it.genre == GenreMembreHerite.TYPE_IMBRIQUE }
                    ?.typeImbrique?.let { return it }
                // Un MEMBRE HÉRITÉ du récepteur implicite (vague 2c —
                // fiches 028/040 : `compte` type `Int`, `layoutInflater`
                // type `LayoutInflater` par le descripteur du lien).
                pile.membre(nom)?.let { membre ->
                    membre.typeImbrique?.let { return it }
                    membre.typeEcrit?.let { ecrit ->
                        univers.resoudreTypeEcrit(ecrit)?.let { return it }
                    }
                    membre.lienJvm?.let { lien ->
                        univers.vueJvm?.typeDuMembre(lien, nom)?.let { return TypeValeur.Jvm(it) }
                    }
                }
                // Un type du PROJET en position d'expression (objet,
                // accès statique : `Calculatrice.somme(…)`).
                univers.typeProjetEcrit(nom)?.let { return TypeValeur.Projet(it) }
                // Une classe IMPORTÉE ou par DÉFAUT (vague 2c — fiche 028 :
                // `Peinture` type `exo.peint.Peinture`, le récepteur de
                // `Peinture.Capitaine.ROND` et de `Peinture().apply { }`).
                univers.typeImporteOuDefaut(nom)?.let { return it }
                null
            }
            // `this` : le type de la couche la plus PROFONDE de la pile
            // (vague 2c — fiche 040 : `this.` dans une Activity propose
            // les membres hérités de la chaîne).
            KtNodeTypes.THIS_EXPRESSION -> pile.typeInterne
            // Un OBJET ANONYME : le type de son premier supertype RÉSOLU
            // (vague 2c — fiche 028 : `object : Autre() { … }` type Autre).
            KtNodeTypes.OBJECT_LITERAL -> {
                val declaration = noeud.enfants.firstOrNull { it.genre == KtNodeTypes.OBJECT_DECLARATION }
                val supertype = ModeleProjet.supertypesEcrits(declaration ?: noeud).firstOrNull()
                supertype?.let { univers.supertypeResolu(it) }
            }
            KtNodeTypes.CALL_EXPRESSION -> {
                val nom = noeud.enfants
                    .firstOrNull { it.genre == KtNodeTypes.REFERENCE_EXPRESSION }
                    ?.texteCanonique.orEmpty()
                if (nom.isEmpty()) return null
                // Un constructeur du projet : le type construit.
                univers.typeProjetEcrit(nom)?.let { return TypeValeur.Projet(it) }
                // Un constructeur d'une classe IMPORTÉE ou par DÉFAUT
                // (vague 2c — `Peinture()` type la bibliothèque).
                univers.typeImporteOuDefaut(nom)?.let { return it }
                // Le RETOUR écrit d'une fonction du projet.
                univers.modele.appelables(nom, univers.paquet)
                    .firstNotNullOfOrNull { it.typeRetourNomme }
                    ?.let { return univers.resoudreTypeEcrit(it) }
                null
            }
            KtNodeTypes.DOT_QUALIFIED_EXPRESSION, KtNodeTypes.SAFE_ACCESS_EXPRESSION -> {
                val recepteur = noeud.enfants.firstOrNull() ?: return null
                val selecteur = noeud.enfants.lastOrNull() ?: return null
                val typeRecepteur = typeDeLexpression(recepteur, univers, liaison, pile) ?: return null
                val nomMembre = when (selecteur.genre) {
                    KtNodeTypes.REFERENCE_EXPRESSION -> selecteur.texteCanonique
                    KtNodeTypes.CALL_EXPRESSION ->
                        selecteur.enfants.firstOrNull { it.genre == KtNodeTypes.REFERENCE_EXPRESSION }
                            ?.texteCanonique.orEmpty()
                    else -> ""
                }
                if (nomMembre.isEmpty()) return null
                when (typeRecepteur) {
                    is TypeValeur.Projet -> {
                        // Une IMBRIQUÉE du modèle d'abord (vague 2c —
                        // `Aquarium.Bac` type l'imbriquée).
                        univers.modele.imbriquesDu(typeRecepteur.type.fqcn)
                            .firstOrNull { it.nomSimple == nomMembre }
                            ?.let { return TypeValeur.Projet(it) }
                        // Un membre DÉCLARÉ (le socle 2a), puis un membre
                        // HÉRITÉ de la clôture (vague 2c — `this.layoutInflater`
                        // type LayoutInflater par le descripteur du lien).
                        univers.resoudreTypeEcrit(typeRecepteur.type.membre(nomMembre)?.typeRetourNomme)
                            ?: univers.clotureDe(typeRecepteur.type)?.membre(nomMembre)?.let { membre ->
                                membre.typeImbrique
                                    ?: membre.typeEcrit?.let { ecrit -> univers.resoudreTypeEcrit(ecrit) }
                                    ?: membre.lienJvm?.let { lien ->
                                        univers.vueJvm?.typeDuMembre(lien, nomMembre)
                                            ?.let { TypeValeur.Jvm(it) }
                                    }
                            }
                    }
                    is TypeValeur.Jvm -> {
                        // Le RETOUR/CHAMP d'un membre de bibliothèque (vague
                        // 2c — `layoutInflater.inflate(…)` : le getter type
                        // LayoutInflater, sa méthode type View) puis
                        // l'IMBRIQUÉE `$` (`Peinture.Capitaine` type
                        // `Peinture$Capitaine`).
                        val mape = univers.mapeJvm(typeRecepteur.fqcn)
                        univers.vueJvm?.typeDuMembre(mape, nomMembre)?.let { return TypeValeur.Jvm(it) }
                        univers.vueJvm?.possedeImbrique(mape, nomMembre)
                            ?.takeIf { it }
                            ?.let { return TypeValeur.Jvm("${typeRecepteur.fqcn}\$$nomMembre") }
                        null
                    }
                }
            }
            // Les LITTÉRAUX : la famille décide (chaîne, entier, flottant,
            // booléen, caractère — fiche 046 : le littréla type son récepteur).
            KtNodeTypes.STRING_TEMPLATE -> univers.resoudreTypeEcrit("String")
            KtNodeTypes.INTEGER_CONSTANT -> {
                val texte = noeud.texteCanonique
                univers.resoudreTypeEcrit(if (texte.endsWith("L") || texte.endsWith("l")) "Long" else "Int")
            }
            KtNodeTypes.FLOAT_CONSTANT -> {
                val texte = noeud.texteCanonique
                univers.resoudreTypeEcrit(if (texte.endsWith("f") || texte.endsWith("F")) "Float" else "Double")
            }
            KtNodeTypes.BOOLEAN_CONSTANT -> univers.resoudreTypeEcrit("Boolean")
            KtNodeTypes.CHARACTER_CONSTANT -> univers.resoudreTypeEcrit("Char")
            else -> null
        }
    }

    /**
     * La FAMILLE d'un littéral (`String`, `Int`, `Long`, `Double`, `Float`,
     * `Boolean`, `Char`) — la matière de `kt.typeMismatch` (fiche 046) et
     * du typage des initialiseurs littéraux.
     *
     * @param noeud le nœud candidat ;
     * @return la famille ; `null` si le nœud n'est pas un littéral connu.
     */
    internal fun familleLitteral(noeud: NoeudArbre): String? {
        return when (noeud.genre) {
            KtNodeTypes.STRING_TEMPLATE -> "String"
            KtNodeTypes.INTEGER_CONSTANT -> {
                val texte = noeud.texteCanonique
                if (texte.endsWith("L") || texte.endsWith("l")) "Long" else "Int"
            }
            KtNodeTypes.FLOAT_CONSTANT -> {
                val texte = noeud.texteCanonique
                if (texte.endsWith("f") || texte.endsWith("F")) "Float" else "Double"
            }
            KtNodeTypes.BOOLEAN_CONSTANT -> "Boolean"
            KtNodeTypes.CHARACTER_CONSTANT -> "Char"
            else -> null
        }
    }

    // ------------------------------------------------------------------
    // Les smart casts positionnels (fiche 046).
    // ------------------------------------------------------------------

    /**
     * Un raffinement positionnel : un SMART CAST (le nom est vu comme le
     * type casté) ou une NON-NULLITÉ (le nom est vu non nul).
     *
     * @property typeNom le nom ÉCRIT du type de cast (`TextView`), `null`
     * pour une pure non-nullité ;
     * @property nonNul le nom est-il raffiné NON NULL (garde `!= null`).
     */
    internal class Raffinement(internal val typeNom: String?, internal val nonNul: Boolean)

    /**
     * Le raffinement du nom à la POSITION donnée (smart cast positionnel —
     * fiche 046) : la marche des ANCÊTRES couvrant la position applique —
     * de l'extérieur vers l'intérieur, le raffinement le plus PROFOND
     * gagne :
     *
     * - la branche THEN d'un `if (nom is T)` voit `nom` comme `T` ;
     * - l'opérande DROIT d'un `nom is T && …` voit `nom` comme `T` ;
     * - le CORPS d'une entrée `nom is T ->` d'un `when` sans sujet voit
     *   `nom` comme `T` ;
     * - après un `if (nom !is T) return/throw` (garde à sortie précoce),
     *   `nom` est vu comme `T` — de même après `if (nom == null) …` ;
     * - la branche THEN d'un `if (nom != null)` voit `nom` non nul ;
     * - une LOCALE du même nom déclarée APRÈS la condition et AVANT la
     *   position OMBRE le sujet (le cast ne fuit pas — fiche 046) ;
     * - un `||` dans la condition ANNULE tout raffinement (bornologie
     *   conservatrice, ADR 0107 : jamais un cast hors preuve).
     *
     * @param arbre la racine du document ;
     * @param position l'offset de la référence raffinée ;
     * @param nom le nom du sujet ;
     * @return le raffinement applicable ; `null` si aucun.
     */
    internal fun raffinementAuPoint(arbre: KtFichier, position: Int, nom: String): Raffinement? {
        var courant: Raffinement? = null
        for (ancetre in chaineAncetres(arbre.noeud, position)) {
            when (ancetre.genre) {
                KtNodeTypes.IF -> {
                    val alors = ancetre.enfants.firstOrNull { it.genre == KtNodeTypes.THEN }
                    if (alors != null && couvre(alors, position)) {
                        val carte = analyserConjonctions(
                            ancetre.enfants.firstOrNull { it.genre == KtNodeTypes.CONDITION },
                        )
                        appliquerSiNonOmbre(carte, nom, alors, position)?.let { courant = it }
                    }
                }
                KtNodeTypes.BINARY_EXPRESSION -> {
                    // L'opérande DROIT d'un && voit les casts de la chaîne
                    // GAUCHE (fiche 046) ; un || annule tout.
                    if (porteOperation(ancetre, KtTokens.ANDAND)) {
                        val enfants = ancetre.enfants
                        val droite = enfants.lastOrNull()
                        if (droite != null && couvre(droite, position)) {
                            analyserConjonctions(enfants.firstOrNull())?.get(nom)?.let { courant = it }
                        }
                    } else if (porteOperation(ancetre, KtTokens.OROR)) {
                        courant = null
                    }
                }
                KtNodeTypes.WHEN -> {
                    val entree = ancetre.enfants.firstOrNull {
                        it.genre == KtNodeTypes.WHEN_ENTRY && couvre(it, position)
                    } ?: continue
                    // Le CORPS de l'entrée (après les conditions) : les
                    // conditions `nom is T` castent le corps.
                    val corps = entree.enfants.lastOrNull { !estConditionQuand(it) } ?: continue
                    if (!couvre(corps, position)) continue
                    for (condition in entree.enfants) {
                        if (!estConditionQuand(condition) || condition.debut > corps.debut) continue
                        analyserConjonctions(condition)?.get(nom)?.let { courant = it }
                    }
                }
                KtNodeTypes.BLOCK -> {
                    // Les instructions PRÉCÉDENTES du bloc : les gardes à
                    // sortie précoce castent la suite, une locale du même
                    // nom OMBRE (le raffinement ne fuit pas).
                    val porteur = ancetre.enfants.firstOrNull { couvre(it, position) } ?: continue
                    for (frere in ancetre.enfants) {
                        if (frere === porteur) break
                        if (frere.genre == KtNodeTypes.PROPERTY) {
                            if (nomDeDeclaration(frere) == nom) courant = null
                            continue
                        }
                        if (frere.genre != KtNodeTypes.IF) continue
                        val carte = analyserGarde(frere) ?: continue
                        carte[nom]?.let { courant = it }
                    }
                }
                else -> Unit
            }
        }
        return courant
    }

    /**
     * Applique la carte d'un THEN en vérifiant l'OMBAGE : une locale du
     * même nom déclarée dans la branche APRÈS la condition et AVANT la
     * position coupe le cast (fiche 046 : le cast ne fuit pas).
     */
    private fun appliquerSiNonOmbre(
        carte: Map<String, Raffinement>?,
        nom: String,
        alors: NoeudArbre,
        position: Int,
    ): Raffinement? {
        val raffinement = carte?.get(nom) ?: return null
        if (localeOmbre(alors, nom, position)) return null
        return raffinement
    }

    /** Une locale du nom déclarée dans le sous-arbre AVANT la position ? */
    private fun localeOmbre(racine: NoeudArbre, nom: String, position: Int): Boolean {
        if (racine.genre == KtNodeTypes.PROPERTY) {
            return nomDeDeclaration(racine) == nom && racine.fin <= position
        }
        return racine.enfants.any { localeOmbre(it, nom, position) }
    }

    /**
     * La carte des raffinements des CONJONCTIONS d'une condition (`&&`
     * uniquement — un `||` ANNULE tout, bornologie ADR 0107) : les feuilles
     * `nom is T` castent, `nom != null` non-nullifie.
     *
     * @param condition le nœud condition (ou conjoint gauche) ;
     * @return la carte nom → raffinement ; `null` si un `||` s'y trouve.
     */
    private fun analyserConjonctions(condition: NoeudArbre?): Map<String, Raffinement>? {
        if (condition == null) return null
        // Une condition ENVELOPPE son expression (CONDITION autour de
        // l'IS_EXPRESSION ou du binaire) : le déballage rend l'analyse
        // uniforme — de même pour une condition d'entrée de `when`.
        val noeud = when (condition.genre) {
            KtNodeTypes.CONDITION,
            KtNodeTypes.WHEN_CONDITION_EXPRESSION,
            KtNodeTypes.WHEN_CONDITION_IS_PATTERN,
            -> condition.enfants.firstOrNull() ?: return emptyMap()
            else -> condition
        }
        if (noeud.genre == KtNodeTypes.BINARY_EXPRESSION) {
            if (porteOperation(noeud, KtTokens.OROR)) return null
            if (porteOperation(noeud, KtTokens.ANDAND)) {
                val gauche = analyserConjonctions(noeud.enfants.firstOrNull()) ?: return null
                val droite = analyserConjonctions(noeud.enfants.lastOrNull()) ?: return null
                return gauche + droite
            }
            // `nom != null` : non-nullification en conjonction.
            if (porteOperation(noeud, KtTokens.EXCLEQ)) {
                raffinementNullite(noeud)?.let { return mapOf(it) }
            }
            return emptyMap()
        }
        if (noeud.genre == KtNodeTypes.IS_EXPRESSION) {
            val cast = raffinementCast(noeud, positif = true) ?: return emptyMap()
            return mapOf(cast)
        }
        return emptyMap()
    }

    /**
     * La carte d'une GARDE à sortie précoce (`if (nom !is T) return`) : le
     * négatif caste la SUITE du bloc — la brèche doit être TERMINALE
     * (`return`/`throw` en dernière instruction).
     *
     * @param si le nœud IF candidat ;
     * @return la carte ; `null` si la forme n'est pas une garde terminale.
     */
    private fun analyserGarde(si: NoeudArbre): Map<String, Raffinement>? {
        val alors = si.enfants.firstOrNull { it.genre == KtNodeTypes.THEN } ?: return null
        if (!estTerminal(alors)) return null
        val enveloppe = si.enfants.firstOrNull { it.genre == KtNodeTypes.CONDITION } ?: return null
        val condition = enveloppe.enfants.firstOrNull() ?: return null
        if (condition.genre == KtNodeTypes.IS_EXPRESSION) {
            val cast = raffinementCast(condition, positif = false) ?: return null
            return mapOf(cast)
        }
        if (condition.genre == KtNodeTypes.BINARY_EXPRESSION &&
            porteOperation(condition, KtTokens.EQEQ)
        ) {
            raffinementNullite(condition)?.let { return mapOf(it) }
        }
        return null
    }

    /** La branche est-elle TERMINALE (return/throw en dernière position) ? */
    private fun estTerminal(alors: NoeudArbre): Boolean {
        return when (alors.genre) {
            KtNodeTypes.RETURN, KtNodeTypes.THROW -> true
            KtNodeTypes.THEN -> alors.enfants.singleOrNull()?.let { estTerminal(it) } ?: false
            KtNodeTypes.BLOCK -> {
                val derniere = alors.enfants.lastOrNull() ?: return false
                derniere.genre == KtNodeTypes.RETURN || derniere.genre == KtNodeTypes.THROW
            }
            else -> false
        }
    }

    /**
     * Le raffinement d'un IS_EXPRESSION : `nom is T` (positif) caste en
     * position de conjonction ; `nom !is T` (négatif) caste en position de
     * GARDE (la suite du bloc).
     *
     * @return la paire nom → raffinement ; `null` si la forme ne s'y prête
     * pas (sujet non nominal, type illisible, polarité inadaptée).
     */
    private fun raffinementCast(est: NoeudArbre, positif: Boolean): Pair<String, Raffinement>? {
        val sujet = est.enfants.firstOrNull { it.genre == KtNodeTypes.REFERENCE_EXPRESSION }
            ?: return null
        val negatif = est.enfants.any {
            it.genre == KtNodeTypes.OPERATION_REFERENCE && it.jetonsPropres.any { j ->
                j.genre == KtTokens.NOT_IS
            }
        }
        if (negatif == positif) return null
        val nom = sujet.texteCanonique
        if (nom.isEmpty()) return null
        val type = est.enfants.firstOrNull { it.genre == KtNodeTypes.TYPE_REFERENCE }
            ?.let(::nomTypeEcrit) ?: return null
        return nom to Raffinement(typeNom = type, nonNul = false)
    }

    /** Le raffinement `nom != null` (paire nom → non-nul) ou `nom == null` en garde. */
    private fun raffinementNullite(binaire: NoeudArbre): Pair<String, Raffinement>? {
        val gauche = binaire.enfants.firstOrNull { it.genre == KtNodeTypes.REFERENCE_EXPRESSION }
            ?: return null
        if (binaire.enfants.none { it.genre == KtNodeTypes.NULL }) return null
        val nom = gauche.texteCanonique
        if (nom.isEmpty()) return null
        return nom to Raffinement(typeNom = null, nonNul = true)
    }

    // ------------------------------------------------------------------
    // L'attente des lambdas (fiche 046).
    // ------------------------------------------------------------------

    /**
     * Les types ATTENDUS des paramètres de la lambda contenant la position
     * (fiche 046) : le paramètre explicite et `it` s'y typent.
     *
     * Les attentes DÉMONTRABLES : le paramètre correspondant d'une fonction
     * DU PROJET appelée avec la lambda en position (`repeter(2) { s -> … }`
     * — la fonction déclare `action: (String) -> Unit`), la MÉTHODE UNIQUE
     * d'une interface du projet en conversion SAM (`Ecrivain { l -> … }`)
     * et le TYPE DE FONCTION ÉCRIT d'une propriété initialisée par la
     * lambda (`val cb: (String) -> Unit = { s -> … }`). Sans attente
     * démontrable : `null` (aucun membre — sous-rapport D3).
     *
     * @param arbre la racine du document ;
     * @param position l'offset DANS le corps de la lambda ;
     * @param univers l'univers des preuves ;
     * @return les types écrits attendus, PAR INDEX de paramètre ; `null`
     * sans attente démontrable.
     */
    internal fun attenteLambdaAuPoint(
        arbre: KtFichier,
        position: Int,
        univers: UniversPreuves,
    ): List<String>? {
        // La lambda englobante la plus profonde.
        val litteral = chaineAncetres(arbre.noeud, position)
            .lastOrNull { it.genre == KtNodeTypes.FUNCTION_LITERAL } ?: return null

        // La chaîne lambda ← LAMBDA_EXPRESSION ← (LAMBDA_ARGUMENT | PROPERTY…).
        val expression = chaineAncetres(arbre.noeud, litteral.debut)
            .lastOrNull { it.genre == KtNodeTypes.LAMBDA_EXPRESSION } ?: return null
        val chaine = chaineAncetres(arbre.noeud, expression.debut)
        val parent = chaine.getOrNull(chaine.indexOf(expression) - 1) ?: return null

        return when (parent.genre) {
            KtNodeTypes.LAMBDA_ARGUMENT -> {
                // Le parent du LAMBDA_ARGUMENT est le CALL porteur.
                val appel = chaineAncetres(arbre.noeud, parent.debut)
                    .getOrNull(chaineAncetres(arbre.noeud, parent.debut).indexOf(parent) - 1)
                if (appel?.genre == KtNodeTypes.CALL_EXPRESSION) {
                    attenteDeLappel(appel, univers)
                } else {
                    null
                }
            }
            KtNodeTypes.VALUE_ARGUMENT -> attenteDeLargument(parent, univers)
            KtNodeTypes.PROPERTY -> attenteDePropriete(parent)
            else -> null
        }
    }

    /**
     * L'INDEX du paramètre NOMMÉ de la lambda englobante (`s` dans
     * `{ s -> … }`) — l'attente type le paramètre à son index ; `null` si
     * la position n'est pas dans une lambda paramétrée par ce nom.
     */
    private fun indiceParametreLambda(arbre: KtFichier, position: Int, nom: String): Int? {
        val litteral = chaineAncetres(arbre.noeud, position)
            .lastOrNull { it.genre == KtNodeTypes.FUNCTION_LITERAL } ?: return null
        val liste = litteral.enfants
            .firstOrNull { it.genre == KtNodeTypes.VALUE_PARAMETER_LIST } ?: return null
        val indices = liste.enfants
            .filter { it.genre == KtNodeTypes.VALUE_PARAMETER }
            .mapIndexed { indice, parametre -> indice to nomDeDeclaration(parametre) }
        return indices.firstOrNull { it.second == nom }?.first
    }

    /** L'attente d'une lambda en ARGUMENT PARENTHÉSÉ (`f(2, { s -> … })`). */
    private fun attenteDeLargument(
        argument: NoeudArbre,
        univers: UniversPreuves,
    ): List<String>? {
        val liste = chaineAncetres(univers.arbre.noeud, argument.debut)
            .lastOrNull { it.genre == KtNodeTypes.VALUE_ARGUMENT_LIST } ?: return null
        val appel = chaineAncetres(univers.arbre.noeud, liste.debut)
            .lastOrNull { it.genre == KtNodeTypes.CALL_EXPRESSION } ?: return null
        val indice = liste.enfants.indexOf(argument)
        return attenteDeLappel(appel, univers, indice)
    }

    /**
     * L'attente d'une lambda EN POSITION D'APPEL : le paramètre du même
     * INDEX d'une fonction du PROJET, ou la méthode unique d'une interface
     * du projet (conversion SAM).
     *
     * @param appel le CALL_EXPRESSION porteur ;
     * @param univers l'univers des preuves ;
     * @param indiceLambda l'index de la lambda parmi les arguments (défaut
     * `-1` : trailing lambda — l'index suit les arguments parenthésés).
     */
    private fun attenteDeLappel(
        appel: NoeudArbre,
        univers: UniversPreuves,
        indiceLambda: Int = -1,
    ): List<String>? {
        val nom = appel.enfants
            .firstOrNull { it.genre == KtNodeTypes.REFERENCE_EXPRESSION }
            ?.texteCanonique.orEmpty()
        if (nom.isEmpty()) return null
        val indice = if (indiceLambda >= 0) {
            indiceLambda
        } else {
            appel.enfants
                .firstOrNull { it.genre == KtNodeTypes.VALUE_ARGUMENT_LIST }
                ?.enfants?.size ?: 0
        }

        // Une FONCTION du projet : le paramètre du même index porte
        // l'attente (son type fonctionnel écrit).
        for (candidat in univers.modele.appelables(nom, univers.paquet)) {
            val parametre = candidat.parametres.getOrNull(indice) ?: continue
            parametre.typeFonction?.let { return it.parametres }
        }

        // Une INTERFACE (ou classe) du projet à méthode unique : la
        // conversion SAM type la lambda par les paramètres de la méthode.
        val type = univers.typeProjetEcrit(nom) ?: return null
        if (type.genre != GenreTypeProjet.INTERFACE && type.genre != GenreTypeProjet.CLASSE) return null
        val fonctions = type.membres.filter { it.genre == GenreMembreProjet.FONCTION }
        if (fonctions.size != 1) return null
        return fonctions.single().parametres.mapNotNull { it.typeEcrit }
    }

    /** L'attente d'une propriété au TYPE DE FONCTION ÉCRIT. */
    private fun attenteDePropriete(propriete: NoeudArbre): List<String>? {
        return propriete.enfants
            .firstOrNull { it.genre == KtNodeTypes.TYPE_REFERENCE }
            ?.let(::typeFonctionEcritDe)
            ?.parametres
    }

    // ------------------------------------------------------------------
    // Aides structurelles.
    // ------------------------------------------------------------------

    /** Le nœud porte-t-il cette opération (OPERATION_REFERENCE) ? */
    private fun porteOperation(
        noeud: NoeudArbre,
        operation: com.intellij.platform.syntax.SyntaxElementType,
    ): Boolean {
        return noeud.enfants.any {
            it.genre == KtNodeTypes.OPERATION_REFERENCE && it.jetonsPropres.any { j -> j.genre == operation }
        }
    }

    /** La plage du nœud couvre-t-elle la position ? */
    private fun couvre(noeud: NoeudArbre, position: Int): Boolean =
        position >= noeud.debut && position <= noeud.fin

    /** Le nœud est-il une condition d'entrée de `when` ? */
    private fun estConditionQuand(noeud: NoeudArbre): Boolean =
        noeud.genre == KtNodeTypes.WHEN_CONDITION_EXPRESSION ||
            noeud.genre == KtNodeTypes.WHEN_CONDITION_IS_PATTERN

    /** Le nom d'un nœud déclaration (premier IDENTIFIER propre). */
    private fun nomDeDeclaration(noeud: NoeudArbre): String? =
        noeud.jetonsPropres.firstOrNull { it.genre == KtTokens.IDENTIFIER }?.texte

    /** Le nom simple ÉCRIT d'une référence de type (nullable déballée). */
    private fun nomTypeEcrit(reference: NoeudArbre): String? {
        var porteur: NoeudArbre =
            reference.enfants.filter { it.genre == KtNodeTypes.NULLABLE_TYPE }.firstOrNull() ?: reference
        if (porteur.genre == KtNodeTypes.NULLABLE_TYPE) {
            porteur = porteur.enfants.filter { it.genre == KtNodeTypes.USER_TYPE }.firstOrNull() ?: return null
        }
        val utilisateur = porteur.enfants.filter { it.genre == KtNodeTypes.USER_TYPE }.firstOrNull()
            ?: porteur.takeIf { it.genre == KtNodeTypes.USER_TYPE }
            ?: return null
        return utilisateur.texteCanonique.substringBefore('<').ifEmpty { null }
    }

    /**
     * La chaîne des ANCÊTRES couvrant la position (de la racine vers le
     * plus profond — chaque nœud CONTIENT la position).
     */
    private fun chaineAncetres(racine: NoeudArbre, position: Int): List<NoeudArbre> {
        val chaine = mutableListOf<NoeudArbre>()
        var courant: NoeudArbre? = racine
        while (courant != null && couvre(courant, position)) {
            chaine.add(courant)
            courant = courant.enfants.firstOrNull { couvre(it, position) }
        }
        return chaine
    }
}
