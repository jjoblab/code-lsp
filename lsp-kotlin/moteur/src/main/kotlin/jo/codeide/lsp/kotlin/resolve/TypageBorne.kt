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
 *
 * V-B (ADR 0123) — le type de bibliothèque porte les ARGUMENTS DE TYPE
 * ÉCRITS (`List<Client>` → `Jvm("kotlin.collections.List", ["Client"])`)
 * : la matière de la substitution des paramètres de type (l'attente des
 * lambdas d'extension — `clients.map { it.nom }` type `it` par
 * l'ÉLÉMENT). Les arguments restent des TEXTES ÉCRITS (jamais une
 * inférence) — frontière versionnée [SPECIFICATION-TYPES.md](../SPECIFICATION-TYPES.md).
 */
internal sealed class TypeValeur {

    /** Un type du PROJET (classe, interface, enum, objet — modèle 2a). */
    internal class Projet(internal val type: TypeProjet) : TypeValeur()

    /**
     * Un type de BIBLIOTHÈQUE (`kotlin.String`, `android.widget.TextView`)
     * — avec ses ARGUMENTS DE TYPE écrits le cas échéant (V-B).
     */
    internal class Jvm(
        internal val fqcn: String,
        internal val arguments: List<String> = emptyList(),
    ) : TypeValeur()
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
                    attenteLambdaAuPoint(univers.arbre, noeud.debut, univers, liaison, pile)
                        ?.takeIf { it.size == 1 }
                        ?.let { return univers.resoudreTypeEcrit(it.single()) }
                }
                // Un PARAMÈTRE DE LAMBDA explicite : l'attente de la lambda
                // le type à son INDEX (fiche 046 — `repeter(2) { s -> … }`).
                attenteLambdaAuPoint(univers.arbre, noeud.debut, univers, liaison, pile)?.let { attente ->
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
                // V-A — une callee QUALIFIÉE (`ModeleProjet.Batisseur()`) :
                // le CHEMIN résout l'imbriquée — les enfants RÉFÉRENCE du
                // CALL sont ceux des ARGUMENTS (la callee vit dans un DOT
                // parent).
                val calleeQualifiee = noeud.enfants.firstOrNull {
                    it.genre == KtNodeTypes.DOT_QUALIFIED_EXPRESSION
                }
                if (calleeQualifiee != null) {
                    univers.resoudreTypeEcrit(calleeQualifiee.texteCanonique)?.let { return it }
                }
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
     * fiche 046 ; étendu en vague V-A) : la marche des ANCÊTRES couvrant la
     * position applique — de l'extérieur vers l'intérieur, le raffinement
     * le plus PROFOND gagne :
     *
     * - la branche THEN d'un `if (nom is T)` voit `nom` comme `T` ;
     * - l'opérande DROIT d'un `nom is T && …` voit `nom` comme `T` ;
     * - le CORPS d'une entrée `nom is T ->` d'un `when` sans sujet voit
     *   `nom` comme `T` ;
     * - le CORPS d'une entrée `is T ->` d'un `when` À SUJET `nom` caste le
     *   sujet (V-A — le sujet implicite du motif) ; un sujet `nom?.x`
     *   rend `nom` non nul dans les corps d'entrées ;
     * - le CORPS d'un `while (nom != null && …)` voit `nom` non nul
     *   (V-A — la condition domine chaque itération ; une réaffectation
     *   intermédiaire à `null` reste un sous-rapport assumé) ;
     * - l'opérande DROIT d'un `nom == null || …` voit `nom` NON NUL (V-A —
     *   la disjonction ne s'exécute à droite que si le nom n'est pas
     *   nul) ;
     * - un `X?.f(…) ?: return/throw/break/continue` précédent rend `X` non
     *   nul pour la suite du bloc, et `val v = … ?: terminal` rend `v`
     *   non nul (V-A — l'elvis à sortie précoce) ; une AFFECTATION
     *   précédente `nom = …` rend l'accès muet (affectation certaine
     *   avant usage — V-A) ;
     * - la LAMBDA d'un `X?.f { … }` voit `X` non nul (V-A — l'accès sûr
     *   ne traverse pas : la lambda ne s'exécute que récepteur non nul) ;
     * - après un `if (nom !is T) return/throw` (garde à sortie précoce),
     *   `nom` est vu comme `T` — de même après `if (nom == null) …` ;
     * - la branche THEN d'un `if (nom != null)` voit `nom` non nul ; un
     *   `if (t is T)` où `val t = nom?.f(…)` précède rend `nom` non nul
     *   dans le THEN (V-A — la non-nullité du résultat d'un accès sûr
     *   entraîne celle de sa base) ;
     * - une LOCALE du même nom déclarée APRÈS la condition et AVANT la
     *   position OMBRE le sujet (le cast ne fuit pas — fiche 046) ;
     * - un `||` dans une conjonction de garde ANNULE tout raffinement
     *   (bornologie conservatrice, ADR 0107 : jamais un cast hors
     *   preuve).
     *
     * @param arbre la racine du document ;
     * @param position l'offset de la référence raffinée ;
     * @param nom le nom du sujet ;
     * @return le raffinement applicable ; `null` si aucun.
     */
    internal fun raffinementAuPoint(arbre: KtFichier, position: Int, nom: String): Raffinement? {
        // V-A — deux CANAUX de raffinement : `courant` (les raffinements de
        // CONDITION — then/when/&&/droite-de-|| — ANNULABLES par un `||`
        // englobant, bornologie fiche 046) et `garde` (les raffinements
        // STICKY — gardes à sortie précoce, elvis terminaux, affectations,
        // accès sûr, corps de while — qui TIENNENT quoi qu'il arrive dans
        // la suite de l'expression : un `||` dans une lambda n'invalide
        // jamais une garde déjà franchie).
        var courant: Raffinement? = null
        var garde: Raffinement? = null
        // Les propriétés `val t = X?.f(…)` déjà traversées (V-A) : la
        // non-nullité de `t` (garde ou cast) entraîne celle de `X`.
        val baseSureParPropriete = mutableMapOf<String, String>()
        for (ancetre in chaineAncetres(arbre.noeud, position)) {
            when (ancetre.genre) {
                KtNodeTypes.IF -> {
                    val alors = ancetre.enfants.firstOrNull { it.genre == KtNodeTypes.THEN }
                    if (alors != null && couvre(alors, position)) {
                        val carte = analyserConjonctions(
                            ancetre.enfants.firstOrNull { it.genre == KtNodeTypes.CONDITION },
                        )
                        appliquerSiNonOmbre(carte, nom, alors, position)?.let { courant = it }
                        // V-A : un cast/garde sur `t` où `val t = nom?.f(…)`
                        // précède rend la BASE `nom` non nulle dans le THEN.
                        if (courant == null) {
                            for ((propriete, base) in baseSureParPropriete) {
                                if (base != nom) continue
                                if (carte == null || carte[propriete] == null) continue
                                appliquerSiNonOmbre(carte, propriete, alors, position) ?: continue
                                garde = Raffinement(typeNom = null, nonNul = true)
                            }
                        }
                    }
                    // V-B — la branche ELSE d'une IF-EXPRESSION `if (nom ==
                    // null) a else …nom…` voit le nom NON NUL (la
                    // négation de la condition) : garde STICKY.
                    val sinon = ancetre.enfants.firstOrNull { it.genre == KtNodeTypes.ELSE }
                    if (sinon != null && couvre(sinon, position) && garde == null) {
                        val condition = ancetre.enfants
                            .firstOrNull { it.genre == KtNodeTypes.CONDITION }
                            ?.enfants?.firstOrNull()
                        if (condition != null && condition.genre == KtNodeTypes.BINARY_EXPRESSION &&
                            porteOperation(condition, KtTokens.EQEQ)
                        ) {
                            val sujets = condition.enfants.filter { it.genre == KtNodeTypes.REFERENCE_EXPRESSION }
                            val nuls = condition.enfants.filter { it.genre == KtNodeTypes.NULL }
                            if (sujets.size == 1 && nuls.isNotEmpty() &&
                                sujets.single().texteCanonique == nom
                            ) {
                                garde = Raffinement(typeNom = null, nonNul = true)
                            }
                        }
                    }
                }
                KtNodeTypes.BINARY_EXPRESSION -> {
                    // L'opérande DROIT d'un && voit les casts de la chaîne
                    // GAUCHE (fiche 046) ; l'opérande DROIT d'un || dont la
                    // GAUCHE est `nom == null` voit `nom` NON NUL (V-A) ;
                    // tout autre || ANNULE les raffinements de condition.
                    if (porteOperation(ancetre, KtTokens.ANDAND)) {
                        val enfants = ancetre.enfants
                        val droite = enfants.lastOrNull()
                        if (droite != null && couvre(droite, position)) {
                            analyserConjonctions(enfants.firstOrNull())?.get(nom)?.let { courant = it }
                        }
                    } else if (porteOperation(ancetre, KtTokens.OROR)) {
                        val enfants = ancetre.enfants
                        val droite = enfants.lastOrNull()
                        if (droite != null && couvre(droite, position)) {
                            nulliteGaucheDeDisjonction(enfants.firstOrNull(), nom)?.let { courant = it }
                        } else {
                            courant = null
                        }
                    }
                }
                KtNodeTypes.WHILE -> {
                    // V-A : la CONDITION d'un while (gardes `!= null` et
                    // casts) domine le CORPS — chaque itération la revérifie
                    // (raffinement STICKY : un `||` dans le corps ne
                    // l'invalide pas).
                    val condition = ancetre.enfants.firstOrNull { it.genre == KtNodeTypes.CONDITION }
                        ?: continue
                    val porteur = ancetre.enfants.firstOrNull { couvre(it, position) } ?: continue
                    if (porteur.debut <= condition.debut) continue
                    analyserConjonctions(condition)?.get(nom)?.let { garde = it }
                }
                KtNodeTypes.WHEN -> {
                    val entree = ancetre.enfants.firstOrNull {
                        it.genre == KtNodeTypes.WHEN_ENTRY && couvre(it, position)
                    } ?: continue
                    // Le CORPS de l'entrée (après les conditions) : les
                    // conditions `nom is T` castent le corps.
                    val corps = entree.enfants.lastOrNull { !estConditionQuand(it) } ?: continue
                    if (!couvre(corps, position)) continue
                    val sujet = sujetDuQuand(ancetre)
                    for (condition in entree.enfants) {
                        if (!estConditionQuand(condition) || condition.debut > corps.debut) continue
                        analyserConjonctions(condition)?.get(nom)?.let { courant = it }
                        // V-A — `when` À SUJET : le motif `is T` caste le
                        // SUJET implicite dans le corps de l'entrée.
                        if (sujet?.genre == KtNodeTypes.REFERENCE_EXPRESSION &&
                            sujet.texteCanonique == nom
                        ) {
                            raffinementMotifSujet(condition)?.let { courant = it }
                        }
                    }
                    // V-A — sujet `nom?.x` : la comparaison d'entrée ne
                    // s'exécute que sujet NON NUL, donc `nom` non nul dans
                    // les corps.
                    val base = sujet?.let(::baseReferenceSure)
                    if (base == nom) {
                        garde = Raffinement(typeNom = null, nonNul = true)
                    }
                }
                KtNodeTypes.SAFE_ACCESS_EXPRESSION -> {
                    // V-A — la LAMBDA (ou le sélecteur) d'un accès sûr ne
                    // s'exécute que si la BASE est non nulle (STICKY : un
                    // `||` dans la lambda ne ramène pas la base à nulle).
                    val enfants = ancetre.enfants
                    val selecteur = enfants.getOrNull(1)
                    if (selecteur != null && position >= selecteur.debut) {
                        if (baseReferenceSure(enfants.firstOrNull()) == nom) {
                            garde = Raffinement(typeNom = null, nonNul = true)
                        }
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
                            val init = initialisateurDePropriete(frere)
                            // V-A — `val t = X?.f(…)…` : la base d'un accès
                            // sûr devient la base SÛRE de la propriété.
                            baseReferenceSure(init)?.let { base ->
                                nomDeDeclaration(frere)?.let { propriete ->
                                    baseSureParPropriete[propriete] = base
                                    // V-A — elvis TERMINAL : la base de
                                    // l'accès sûr est NON NULLE pour la
                                    // suite (`X?.f() ?: return`).
                                    if (elvisTerminal(init) && base == nom) {
                                        garde = Raffinement(typeNom = null, nonNul = true)
                                    }
                                }
                            }
                            if (nomDeDeclaration(frere) != nom) continue
                            // V-A — `val v = … ?: terminal` : `v` NON NUL
                            // pour la suite (l'elvis à sortie précoce).
                            if (elvisTerminal(init)) {
                                garde = Raffinement(typeNom = null, nonNul = true)
                            } else {
                                courant = null
                                garde = null
                            }
                            continue
                        }
                        // V-A — une AFFECTATION précédente au nom : la
                        // valeur est certainement assignée avant l'accès
                        // (le flot d'assignation est hors preuve — muet).
                        if (frere.genre == KtNodeTypes.BINARY_EXPRESSION &&
                            cibleAffectation(frere) == nom
                        ) {
                            garde = Raffinement(typeNom = null, nonNul = true)
                            continue
                        }
                        // V-A — `requireNotNull(nom)`/`checkNotNull(nom)` en
                        // instruction : la suite voit le nom NON NUL.
                        if (frere.genre == KtNodeTypes.CALL_EXPRESSION &&
                            gardeRequireNotNull(frere, nom)
                        ) {
                            garde = Raffinement(typeNom = null, nonNul = true)
                            continue
                        }
                        if (frere.genre != KtNodeTypes.IF) continue
                        val carte = analyserGarde(frere) ?: continue
                        carte[nom]?.let { garde = it }
                    }
                }
                else -> Unit
            }
        }
        return courant ?: garde
    }

    /** Le SUJET d'un `when` (premier enfant hors entrées) ; `null` sans sujet. */
    private fun sujetDuQuand(quand: NoeudArbre): NoeudArbre? =
        quand.enfants.firstOrNull { it.genre != KtNodeTypes.WHEN_ENTRY }

    /**
     * Le raffinement d'un MOTIF d'entrée de `when` À SUJET (V-A) : `is T`
     * caste le sujet implicite — le motif ne porte PAS la référence du
     * sujet (elle est implicite dans la grammaire).
     */
    private fun raffinementMotifSujet(condition: NoeudArbre): Raffinement? {
        val motif = when (condition.genre) {
            KtNodeTypes.WHEN_CONDITION_IS_PATTERN,
            KtNodeTypes.WHEN_CONDITION_EXPRESSION,
            -> condition.enfants.firstOrNull() ?: return null
            else -> condition
        }
        if (motif.genre != KtNodeTypes.IS_EXPRESSION) return null
        val negatif = motif.enfants.any {
            it.genre == KtNodeTypes.OPERATION_REFERENCE && it.jetonsPropres.any { j ->
                j.genre == KtTokens.NOT_IS
            }
        }
        if (negatif) return null
        val type = motif.enfants.firstOrNull { it.genre == KtNodeTypes.TYPE_REFERENCE }
            ?.let(::nomTypeEcrit) ?: return null
        return Raffinement(typeNom = type, nonNul = false)
    }

    /**
     * La BASE REFERENCE d'une chaîne d'accès sûr (V-A) : le récepteur le
     * plus profond d'une chaîne `a?.b?.c` — `null` si la base n'est pas un
     * nom simple. L'opérande GAUCHE d'un elvis (`a?.f() ?: …`) se déroule
     * d'abord (la base sûre vit à gauche de l'elvis).
     */
    private fun baseReferenceSure(noeud: NoeudArbre?): String? {
        var courant = noeud ?: return null
        if (courant.genre == KtNodeTypes.BINARY_EXPRESSION && porteOperation(courant, KtTokens.ELVIS)) {
            courant = courant.enfants.firstOrNull() ?: return null
        }
        while (courant.genre == KtNodeTypes.SAFE_ACCESS_EXPRESSION) {
            courant = courant.enfants.firstOrNull() ?: return null
        }
        if (courant.genre != KtNodeTypes.REFERENCE_EXPRESSION) return null
        return courant.texteCanonique.ifEmpty { null }
    }

    /**
     * L'opérande DROIT d'un `||` voit la NON-NULLITÉ du nom si la GAUCHE
     * (ou un DISJONCT de sa chaîne `a == null || b || c`) est `nom == null`
     * (V-A — la disjonction ne s'exécute à droite que si le nom n'est pas
     * nul) ; sinon la disjonction n'apporte rien (conservateur).
     */
    private fun nulliteGaucheDeDisjonction(gauche: NoeudArbre?, nom: String): Raffinement? {
        if (gauche == null) return null
        // V-A — la chaîne de disjonction se déroule en FEUILLES (un `||`
        // imbriqué à gauche ou la comparaison DIRECTE `nom == null`).
        val disjoncts = if (gauche.genre == KtNodeTypes.BINARY_EXPRESSION &&
            porteOperation(gauche, KtTokens.OROR)
        ) {
            disjonctsDeDisjonction(gauche) ?: return null
        } else {
            listOf(gauche)
        }
        for (disjonct in disjoncts) {
            if (disjonct.genre != KtNodeTypes.BINARY_EXPRESSION) continue
            if (!porteOperation(disjonct, KtTokens.EQEQ)) continue
            val sujets = disjonct.enfants.filter { it.genre == KtNodeTypes.REFERENCE_EXPRESSION }
            val nuls = disjonct.enfants.filter { it.genre == KtNodeTypes.NULL }
            if (sujets.size != 1 || nuls.isEmpty()) continue
            if (sujets.single().texteCanonique == nom) return Raffinement(typeNom = null, nonNul = true)
        }
        return null
    }

    /** Les DISJONCTS d'une chaîne `||` (V-A) ; `null` si ce n'en est pas une. */
    private fun disjonctsDeDisjonction(noeud: NoeudArbre?): List<NoeudArbre>? {
        if (noeud == null) return null
        if (noeud.genre != KtNodeTypes.BINARY_EXPRESSION) return null
        if (!porteOperation(noeud, KtTokens.OROR)) return null
        val disjoncts = mutableListOf<NoeudArbre>()
        fun etendre(courant: NoeudArbre) {
            if (courant.genre == KtNodeTypes.BINARY_EXPRESSION && porteOperation(courant, KtTokens.OROR)) {
                courant.enfants.firstOrNull()?.let(::etendre)
                courant.enfants.lastOrNull()?.let(::etendre)
            } else {
                disjoncts.add(courant)
            }
        }
        etendre(noeud)
        return disjoncts
    }

    /** L'INITIALISATEUR d'une PROPERTY (dernier enfant hors type/modificateurs). */
    private fun initialisateurDePropriete(propriete: NoeudArbre): NoeudArbre? {
        return propriete.enfants.lastOrNull {
            it.genre != KtNodeTypes.TYPE_REFERENCE &&
                it.genre != KtNodeTypes.MODIFIER_LIST &&
                it.genre != KtNodeTypes.PROPERTY_DELEGATE &&
                it.genre != KtNodeTypes.PROPERTY_ACCESSOR
        }
    }

    /**
     * L'expression porte-t-elle un ELVIS à sortie précoce au SOMMET (V-A :
     * `… ?: return/throw/break/continue`) — la valeur en est NON NULLE
     * pour la suite du bloc.
     */
    private fun elvisTerminal(expression: NoeudArbre?): Boolean {
        if (expression == null || expression.genre != KtNodeTypes.BINARY_EXPRESSION) return false
        if (!porteOperation(expression, KtTokens.ELVIS)) return false
        val droite = expression.enfants.lastOrNull() ?: return false
        return when (droite.genre) {
            KtNodeTypes.RETURN, KtNodeTypes.THROW -> true
            KtNodeTypes.CONTINUE, KtNodeTypes.BREAK -> true
            else -> false
        }
    }

    /** La CIBLE d'une affectation `=` (V-A) ; `null` si ce n'en est pas une. */
    private fun cibleAffectation(binaire: NoeudArbre): String? {
        val operation = binaire.enfants.indexOfFirst { it.genre == KtNodeTypes.OPERATION_REFERENCE }
        if (operation <= 0) return null
        if (!porteJetonOperation(binaire.enfants[operation], KtTokens.EQ)) return null
        val cible = binaire.enfants[operation - 1]
        if (cible.genre != KtNodeTypes.REFERENCE_EXPRESSION) return null
        return cible.texteCanonique.ifEmpty { null }
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
     * (`return`/`throw` en dernière instruction). V-A : la garde à
     * DISJONCTION (`if (nom == null || autre) return`) caste AUSSI la
     * suite — chaque disjonction franchie implique sa négation.
     *
     * @param si le nœud IF candidat ;
     * @return la carte ; `null` si la forme n'est pas une garde terminale.
     */
    private fun analyserGarde(si: NoeudArbre): Map<String, Raffinement>? {
        val alors = si.enfants.firstOrNull { it.genre == KtNodeTypes.THEN } ?: return null
        if (!estTerminal(alors)) return null
        val enveloppe = si.enfants.firstOrNull { it.genre == KtNodeTypes.CONDITION } ?: return null
        val condition = enveloppe.enfants.firstOrNull() ?: return null
        // V-A — une DISJONCTION de gardes : chaque disjonct `nom == null`
        // (ou `nom !is T`) caste la SUITE (la garde n'a passé que si TOUTES
        // les disjonctions étaient FAUSSES).
        if (condition.genre == KtNodeTypes.BINARY_EXPRESSION &&
            porteOperation(condition, KtTokens.OROR)
        ) {
            val carte = mutableMapOf<String, Raffinement>()
            if (!collecterGardesDisjonction(condition, carte)) return null
            if (carte.isEmpty()) return null
            return carte
        }
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

    /**
     * V-A — collecte les gardes d'une DISJONCTION terminale : chaque
     * disjonct analysable (`nom == null`, `nom !is T`) caste la suite ; un
     * disjonct non analysable n'apporte RIEN (conservateur : aucune
     * contre-preuve).
     *
     * @return `false` si un disjonct casse la forme (l'analyse s'arrête).
     */
    private fun collecterGardesDisjonction(disjonction: NoeudArbre, carte: MutableMap<String, Raffinement>): Boolean {
        if (disjonction.genre == KtNodeTypes.BINARY_EXPRESSION &&
            porteOperation(disjonction, KtTokens.OROR)
        ) {
            val gauche = disjonction.enfants.firstOrNull() ?: return false
            val droite = disjonction.enfants.lastOrNull() ?: return false
            return collecterGardesDisjonction(gauche, carte) &&
                collecterGardesDisjonction(droite, carte)
        }
        if (disjonction.genre == KtNodeTypes.IS_EXPRESSION) {
            raffinementCast(disjonction, positif = false)?.let { carte.putIfAbsent(it.first, it.second) }
            return true
        }
        if (disjonction.genre == KtNodeTypes.BINARY_EXPRESSION &&
            porteOperation(disjonction, KtTokens.EQEQ)
        ) {
            raffinementNullite(disjonction)?.let { carte.putIfAbsent(it.first, it.second) }
            return true
        }
        // Un disjonct sans intérêt (autre comparaison, appel…) : la garde
        // reste VALIDE pour les autres disjoncts (aucune contre-preuve).
        return true
    }

    /**
     * V-A — l'instruction est-elle un `requireNotNull(nom)`/
     * `checkNotNull(nom)` (garde d'invariant : la suite voit le nom NON
     * NUL) ?
     */
    private fun gardeRequireNotNull(appel: NoeudArbre, nom: String): Boolean {
        val callee = appel.enfants
            .firstOrNull { it.genre == KtNodeTypes.REFERENCE_EXPRESSION }
            ?.texteCanonique ?: return false
        if (callee != "requireNotNull" && callee != "checkNotNull") return false
        val arguments = appel.enfants
            .firstOrNull { it.genre == KtNodeTypes.VALUE_ARGUMENT_LIST }
            ?.enfants?.filter { it.genre == KtNodeTypes.VALUE_ARGUMENT }.orEmpty()
        if (arguments.size != 1) return false
        return arguments.single().enfants
            .firstOrNull { it.genre == KtNodeTypes.REFERENCE_EXPRESSION }
            ?.texteCanonique == nom
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
        liaison: MarcheTypee.Liaison? = null,
        pile: PileThis = PileThis.VIDE,
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
                        ?: attenteSelecteurClasspath(appel, univers, liaison, pile)
                        ?: attenteExtensionClasspath(appel, univers, liaison, pile)
                } else {
                    null
                }
            }
            KtNodeTypes.VALUE_ARGUMENT ->
                attenteDeLargument(parent, univers) ?: attenteExtensionClasspathArgument(parent, univers, liaison, pile)
            KtNodeTypes.PROPERTY -> attenteDePropriete(parent)
            else -> null
        }
    }

    /**
     * L'attente d'une lambda passée à un SÉLECTEUR sur récepteur CLASSPATH
     * typé (vague 2f — fiche 047 : `vue.setOnClickListener { v -> … }` —
     * le paramètre du membre est une interface SAM de bibliothèque, sa
     * méthode UNIQUE type la lambda). La chaîne remonte au DOT porteur,
     * le récepteur se type, le membre se lit dans la vue JVM.
     */
    private fun attenteSelecteurClasspath(
        appel: NoeudArbre,
        univers: UniversPreuves,
        liaison: MarcheTypee.Liaison?,
        pile: PileThis,
    ): List<String>? {
        val nom = appel.enfants
            .firstOrNull { it.genre == KtNodeTypes.REFERENCE_EXPRESSION }
            ?.texteCanonique.orEmpty()
        if (nom.isEmpty()) return null
        val chaine = chaineAncetres(univers.arbre.noeud, appel.debut)
        val porteur = chaine.getOrNull(chaine.indexOf(appel) - 1) ?: return null
        if (porteur.genre != KtNodeTypes.DOT_QUALIFIED_EXPRESSION &&
            porteur.genre != KtNodeTypes.SAFE_ACCESS_EXPRESSION
        ) {
            return null
        }
        val recepteur = porteur.enfants.firstOrNull() ?: return null
        val liaisonEffective = liaison
            ?: MarcheTypee.Liaison(arbre = univers.arbre, univers = univers, region = null)
        val typeRecepteur = typeDeLexpression(recepteur, univers, liaisonEffective, pile) ?: return null
        if (typeRecepteur !is TypeValeur.Jvm) return null
        val vue = univers.vueJvm ?: return null
        val membre = vue.membreChaine(univers.mapeJvm(typeRecepteur.fqcn), nom) ?: return null
        // Le paramètre d'index de la lambda : l'argument ORDINAIRE compte
        // (le trailing lambda est le DERNIER paramètre du membre).
        val signatures = membre.signatures.filter { !it.estChamp }
        for (signature in signatures) {
            val parametres = signature.parametres ?: continue
            val sam = parametres.lastOrNull() ?: continue
            // L'interface SAM de bibliothèque : sa méthode UNIQUE type la
            // lambda (les paramètres de CETTE méthode).
            val declares = vue.membresDeclares(sam) ?: continue
            val methodes = declares.membres.filter { !it.estChamp }
            if (methodes.size != 1) continue
            val attente = methodes.single().parametres() ?: continue
            return attente.map { it.substringAfterLast('.') }
        }
        return null
    }

    /**
     * V-B — l'attente d'une lambda passée à une EXTENSION du CLASSPATH
     * (ADR 0123 : la substitution des paramètres de type) : le récepteur
     * TYPAÉ portant des ARGUMENTS DE TYPE écrits (`List<Client>`) prête
     * ses arguments au paramètre FONCTIONNEL de l'extension
     * (`Iterable<T>.map(transform: (T) -> R)` — T ← Client) : `it` se
     * type par l'ÉLÉMENT, ses sélecteurs se prouvent.
     *
     * La substitution est POSITIONNELLE et BORNÉE : seuls les paramètres
     * de type à UNE lettre majuscule du type fonctionnel se substituent
     * (le N-ième ← le N-ième argument du récepteur — `Map<K, V>.forEach
     * {(K, V) -> …}` substitue K et V) ; tout autre texte reste tel quel
     * (jamais une inférence — SPECIFICATION-TYPES.md).
     *
     * @param appel le CALL porteur (callee sans récepteur propre) ;
     * @param univers l'univers des preuves ;
     * @param liaison la liaison de la région ;
     * @param pile la pile des this ;
     * @return les types écrits attendus des paramètres de la lambda ;
     * `null` sans extension candidate démontrable.
     */
    private fun attenteExtensionClasspath(
        appel: NoeudArbre,
        univers: UniversPreuves,
        liaison: MarcheTypee.Liaison?,
        pile: PileThis,
    ): List<String>? {
        val typeRecepteur = recepteurTypeDeLappel(appel, univers, liaison, pile) ?: return null
        if (typeRecepteur !is TypeValeur.Jvm) return null
        if (typeRecepteur.arguments.isEmpty()) return null
        val nom = appel.enfants
            .firstOrNull { it.genre == KtNodeTypes.REFERENCE_EXPRESSION }
            ?.texteCanonique.orEmpty()
        if (nom.isEmpty()) return null
        val extensions = univers.extensionsApplicables(typeRecepteur.fqcn, nom)
        for (extension in extensions) {
            attenteDuParametreFonctionnel(extension.parametres.lastOrNull())?.let { parametres ->
                val substitues = substituerParametresDeType(parametres, typeRecepteur.arguments)
                if (substitues != parametres) return substitues
            }
        }
        return null
    }

    /**
     * V-B — l'attente d'une lambda en ARGUMENT PARENTHÉSÉ d'une extension
     * du classpath (`clients.map({ it.nom })`) : le porteur qualifié se
     * remonte depuis l'argument, miroir de [attenteExtensionClasspath].
     */
    private fun attenteExtensionClasspathArgument(
        argument: NoeudArbre,
        univers: UniversPreuves,
        liaison: MarcheTypee.Liaison?,
        pile: PileThis,
    ): List<String>? {
        val liste = chaineAncetres(univers.arbre.noeud, argument.debut)
            .lastOrNull { it.genre == KtNodeTypes.VALUE_ARGUMENT_LIST } ?: return null
        val appel = chaineAncetres(univers.arbre.noeud, liste.debut)
            .lastOrNull { it.genre == KtNodeTypes.CALL_EXPRESSION } ?: return null
        return attenteExtensionClasspath(appel, univers, liaison, pile)
    }

    /**
     * V-B — le RÉCEPTEUR typé d'un appel qualifié (le porteur DOT/SAFE de
     * l'appel) ; `null` pour un appel nu ou un récepteur non typable.
     */
    private fun recepteurTypeDeLappel(
        appel: NoeudArbre,
        univers: UniversPreuves,
        liaison: MarcheTypee.Liaison?,
        pile: PileThis,
    ): TypeValeur? {
        val chaine = chaineAncetres(univers.arbre.noeud, appel.debut)
        val porteur = chaine.getOrNull(chaine.indexOf(appel) - 1) ?: return null
        if (porteur.genre != KtNodeTypes.DOT_QUALIFIED_EXPRESSION &&
            porteur.genre != KtNodeTypes.SAFE_ACCESS_EXPRESSION
        ) {
            return null
        }
        val recepteur = porteur.enfants.firstOrNull() ?: return null
        val liaisonEffective = liaison
            ?: MarcheTypee.Liaison(arbre = univers.arbre, univers = univers, region = null)
        return typeDeLexpression(recepteur, univers, liaisonEffective, pile)
    }

    /**
     * V-B — les paramètres du TYPE FONCTIONNEL écrit d'un paramètre
     * d'appelable. Deux formes rendues :
     *
     * - la forme DÉCODEE du classpath — `Function1<T, R>` (le miroir JVM
     *   du type fonctionnel : les N premiers arguments sont les
     *   PARAMÈTRES, le dernier est le RETOUR — `Function2<K, V, Unit>`
     *   rend `["K", "V"]`) ;
     * - la forme ÉCRITE du projet — `(T) -> R`, `(K, V) -> Unit`.
     *
     * `null` si le type n'est pas fonctionnel ou illisible.
     */
    private fun attenteDuParametreFonctionnel(parametre: jo.codeide.lsp.kotlin.index.ParametreAppelable?): List<String>? {
        val type = parametre?.type ?: return null
        if (type.startsWith("Function") && type.contains('<') && type.endsWith('>')) {
            val arguments = type.substringAfter('<').removeSuffix(">").split(',').map { it.trim() }
            if (arguments.size < 2) return null
            return arguments.dropLast(1).filter { it.isNotEmpty() }.ifEmpty { null }
        }
        if (!type.contains("->")) return null
        val parametres = type.substringBefore("->")
            .removePrefix("(")
            .removeSuffix(")")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (parametres.isEmpty()) return null
        return parametres
    }

    /**
     * V-B — la SUBSTITUTION positionnelle : chaque paramètre de type à
     * UNE lettre majuscule (le N-ième en ordre) ← le N-ième argument
     * écrit du récepteur (`(T) -> R` + `List<Client>` → `["Client"]`).
     * Les autres textes restent tels quels.
     */
    private fun substituerParametresDeType(parametres: List<String>, arguments: List<String>): List<String> {
        var rang = 0
        return parametres.map { parametre ->
            if (parametre.length == 1 && parametre[0] in 'A'..'Z') {
                val substitue = arguments.getOrNull(rang)
                rang++
                substitue ?: parametre
            } else {
                parametre
            }
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
     * Le RÉCEPTEUR ATTENDU de la lambda englobante (étage 3 vague 3d —
     * fiche 038 : le paramètre `content: RowScope.() -> Unit` de Compose
     * `Row { … }` prête RowScope comme this IMPLICITE du corps — le
     * candidat PROJET porte le récepteur ÉCRIT du type fonctionnel, le
     * candidat CLASSPATH le porte par la forme RENDUE `Function1<R, U>`
     * du descripteur amont — `buildList { add(…) }` prête MutableList).
     *
     * @param arbre la racine du document ;
     * @param position l'offset DANS le corps de la lambda ;
     * @param univers l'univers des preuves ;
     * @return le type du récepteur attendu ; `null` sans attente
     * démontrable (lambda orpheline, appel inconnu, type sans récepteur).
     */
    internal fun recepteurAttenduDeLambda(
        arbre: KtFichier,
        position: Int,
        univers: UniversPreuves,
    ): TypeValeur? {
        val litteral = chaineAncetres(arbre.noeud, position)
            .lastOrNull { it.genre == KtNodeTypes.FUNCTION_LITERAL } ?: return null
        val expression = chaineAncetres(arbre.noeud, litteral.debut)
            .lastOrNull { it.genre == KtNodeTypes.LAMBDA_EXPRESSION } ?: return null
        val chaine = chaineAncetres(arbre.noeud, expression.debut)
        val parent = chaine.getOrNull(chaine.indexOf(expression) - 1) ?: return null
        return when (parent.genre) {
            KtNodeTypes.LAMBDA_ARGUMENT -> {
                val chaineParent = chaineAncetres(arbre.noeud, parent.debut)
                val appel = chaineParent.getOrNull(chaineParent.indexOf(parent) - 1)
                if (appel?.genre == KtNodeTypes.CALL_EXPRESSION) {
                    recepteurDeLappel(appel, univers)
                } else {
                    null
                }
            }
            KtNodeTypes.VALUE_ARGUMENT -> {
                val liste = chaineAncetres(arbre.noeud, parent.debut)
                    .lastOrNull { it.genre == KtNodeTypes.VALUE_ARGUMENT_LIST } ?: return null
                val appel = chaineAncetres(arbre.noeud, liste.debut)
                    .lastOrNull { it.genre == KtNodeTypes.CALL_EXPRESSION } ?: return null
                recepteurDeLappel(appel, univers, liste.enfants.indexOf(parent))
            }
            else -> null
        }
    }

    /**
     * Le récepteur attendu du PARAMÈTRE de lambda d'un appel (l'index du
     * paramètre compte les arguments ORDINAIRES — miroir de
     * [attenteDeLappel]) : le candidat PROJET d'abord (récepteur ÉCRIT du
     * type fonctionnel), puis le candidat CLASSPATH visible (forme RENDUE
     * `Function1<R, U>`).
     */
    private fun recepteurDeLappel(
        appel: NoeudArbre,
        univers: UniversPreuves,
        indiceLambda: Int = -1,
    ): TypeValeur? {
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
        // Le candidat PROJET : le récepteur ÉCRIT du type fonctionnel du
        // paramètre du même index. Le nom écrit vit dans le FICHIER
        // DÉCLARANT : sa résolution tente le document courant (types du
        // projet, imports, défauts) PUIS le nom simple UNIQUE des entrées
        // du classpath (le récepteur d'un scope classpath — `Ecran(bloc:
        // PorteeEtrangere.() -> Unit)` déclaré dans un autre fichier,
        // dont l'import n'est PAS dans le document en édition).
        for (candidat in univers.modele.appelables(nom, univers.paquet)) {
            val parametre = candidat.parametres.getOrNull(indice) ?: continue
            val recepteur = parametre.typeFonction?.recepteur ?: continue
            resoudreRecepteurEcrit(univers, recepteur)?.let { return it }
        }
        // Le candidat CLASSPATH visible : la forme RENDUE Function1<R, U>
        // du paramètre du même index (package courant et paquets par
        // défaut — la preuve de visibilité, miroir de la voie 6).
        if (univers.index != null) {
            for (appelable in univers.index.topParNom(nom)) {
                if (appelable.paquet != univers.paquet &&
                    appelable.paquet !in UniversPreuves.PAQUETS_DEFAUT
                ) {
                    continue
                }
                val parametre = appelable.parametres.getOrNull(indice) ?: continue
                val rendu = parametre.type ?: continue
                val recepteur = premierArgumentFunction1(rendu) ?: continue
                return univers.resoudreTypeEcrit(recepteur)
            }
        }
        return null
    }

    /**
     * Le PREMIER argument de type d'une forme RENDUE `Function1<R, U>` (le
     * récepteur d'un type fonctionnel à récepteur, encodé Function1 par le
     * compilateur) : les génériques imbriqués sont dépouillés
     * (`MutableList<E>` → `MutableList`).
     */
    private fun premierArgumentFunction1(rendu: String): String? {
        if (!rendu.startsWith("Function1<") || !rendu.endsWith(">")) return null
        val corps = rendu.removePrefix("Function1<").removeSuffix(">")
        var profondeur = 0
        for ((indice, caractere) in corps.withIndex()) {
            when (caractere) {
                '<' -> profondeur++
                '>' -> profondeur--
                ',' -> if (profondeur == 0) {
                    return corps.substring(0, indice).substringBefore('<').trim().ifEmpty { null }
                }
            }
        }
        return corps.substringBefore('<').trim().ifEmpty { null }
    }

    /**
     * Résout le nom ÉCRIT du récepteur d'un type fonctionnel déclaré dans
     * un AUTRE fichier (étage 3 vague 3d) : le document courant d'abord
     * (types du projet, imports, défauts), puis le nom simple UNIQUE des
     * ENTRÉES du classpath — le récepteur d'un scope classpath (`Ecran(bloc:
     * PorteeEtrangere.() -> Unit)`) s'y désigne sans équivoque.
     */
    private fun resoudreRecepteurEcrit(univers: UniversPreuves, recepteur: String): TypeValeur? {
        univers.resoudreTypeEcrit(recepteur)?.let { return it }
        val fqcns = univers.vueJvm?.fqcnParNomSimple(recepteur) ?: return null
        return fqcns.singleOrNull()?.let { TypeValeur.Jvm(it) }
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
            it.genre == KtNodeTypes.OPERATION_REFERENCE && porteJetonOperation(it, operation)
        }
    }

    /**
     * V-A — une OPERATION_REFERENCE porte-t-elle ce jeton ? Le jeton peut
     * être PROPRE (jetonsPropres — `||`, `==`) ou EMBOÎTÉ dans un nœud
     * enfant (l'elvis `?:` de la grammaire vendorée enveloppe son jeton).
     */
    private fun porteJetonOperation(
        operation: NoeudArbre,
        jeton: com.intellij.platform.syntax.SyntaxElementType,
    ): Boolean {
        if (operation.jetonsPropres.any { it.genre == jeton }) return true
        return operation.enfants.any { it.genre == jeton }
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
