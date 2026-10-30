// CodeIDE — modèle de symboles du PROJET pour la résolution (module :kotlin,
// étage 2 vague 2a — socle de résolution). Code NEUF jjoblab (clean-room —
// contrat : fiches 017/022/006 du cahier docs/cahier-specifications-kotlin/
// + ADR 0105/0106 ; les zones NOYAU anciennes resolve/ et diagnostics/ sont
// en lecture interdite R9.1 — ce modèle est écrit cahier en main).
package jo.codeide.lsp.kotlin.resolve

import jo.codeide.lsp.kotlin.parse.NoeudArbre
import jo.codeide.lsp.kotlin.parse.kt.KtFichier
import org.jetbrains.kotlin.kmp.lexer.KtTokens
import org.jetbrains.kotlin.kmp.parser.KtNodeTypes

/**
 * Le genre d'un TYPE du projet (Kotlin ou Java — la source Java nourrit le
 * même modèle, fiche 022 : « le volet Java couvre les POJO »).
 */
internal enum class GenreTypeProjet {

    /** `class` Kotlin. */
    CLASSE,

    /** `interface` Kotlin. */
    INTERFACE,

    /** `enum class` Kotlin — les membres implicites n'y sont jamais prouvables absents. */
    ENUM,

    /** `object` Kotlin nommé (compagnon compris). */
    OBJET,

    /** Classe JAVA du projet (POJO, bac…). */
    JAVA,
}

/**
 * Un TYPE du projet : classe, interface, enum, objet Kotlin ou classe Java —
 * la matière des preuves d'absence sur récepteur PROJET (fiche 022 : un
 * sélecteur dont le récepteur est un type DU PROJET sans héritage écrit
 * porte un jeu de membres COMPLET — déclarés + implicites de Any/Object).
 *
 * Le modèle est CONSERVATEUR par construction : ce qui n'est pas lisible se
 * lit vide ou absent (jamais d'exception, D3).
 *
 * @property uri l'identifiant du fichier porteur (`null` = document
 * COURANT — le modèle du document ouvert, toujours frais) ;
 * @property fqcn le nom pleinement qualifié (`com.ex.app.Boite`) ;
 * @property nomSimple le nom écrit (`Boite`) ;
 * @property genre le genre du type ;
 * @property supertypesEcrits les noms de types ÉCRITS dans la clause
 * (simples, tels qu'écrits — `Base` pour `: Base()`) : une clause non vide
 * rend TOUS les membres hérités invérifiables (jamais prouvable absent,
 * fiche 022) ;
 * @property membres les membres NOMMÉS déclarés (fonctions, propriétés,
 * paramètres de constructeur `val`/`var` compris — `var contenu: String`
 * déclare le membre `contenu`) ;
 * @property estData la classe porte le modificateur `data` (membres
 * synthétisés `copy`/`componentN` — fiche 022) ;
 * @property entreesEnum les entrées d'enum déclarées (`ROUGE`, `VERT`) ;
 * @property parametresPrimaire les paramètres du constructeur PRIMAIRE
 * (val/var ou non — le témoin d'arité d'un appel de constructeur les
 * compte TOUS, fiche 046) ; liste vide sans constructeur primaire.
 */
internal class TypeProjet(
    internal val uri: String?,
    internal val fqcn: String,
    internal val nomSimple: String,
    internal val genre: GenreTypeProjet,
    internal val supertypesEcrits: List<String>,
    internal val membres: List<MembreProjet>,
    internal val estData: Boolean,
    internal val entreesEnum: List<String>,
    internal val parametresPrimaire: List<ParametreProjet> = emptyList(),
) {

    /**
     * Interroge si le type est ABSENCE-FIABLE : un type sans clause de
     * supertype écrite et qui n'est pas un enum porte un jeu de membres
     * COMPLET (déclarés + synthétisés + implicites) — l'absence d'un
     * sélecteur s'y PROUVE (fiche 022).
     *
     * @return `true` si le jeu de membres est complet ; `false` si un
     * héritage écrit ou le régime enum ouvre l'univers (muet, D3).
     */
    internal val absenceFiable: Boolean
        get() = supertypesEcrits.isEmpty() && genre != GenreTypeProjet.ENUM

    /** Les noms de membres déclarés et synthétisés (avec doublons éventuels). */
    internal val nomsMembres: List<String>
        get() = membres.map { it.nom }

    /**
     * Le membre portant ce nom (le PREMIER en ordre de déclaration).
     *
     * @param nom le nom cherché ;
     * @return le membre ; `null` si aucun membre déclaré ne le porte.
     */
    internal fun membre(nom: String): MembreProjet? = membres.firstOrNull { it.nom == nom }
}

/**
 * Le genre d'un membre déclaré d'un type du projet.
 */
internal enum class GenreMembreProjet {

    /** `fun` membre (les accesseurs synthétisés Java compris). */
    FONCTION,

    /** `val`/`var` membre (paramètre de constructeur `val`/`var` compris). */
    PROPRIETE,

    /**
     * Un TYPE IMBRIQUÉ déclaré dans le corps du conteneur (vague 2c —
     * fiche 029 : l'imbriquée de niveau 2 est extraite comme DÉCLARATION
     * MEMBRE du conteneur, `Aquarium.Bac` résout par le sélecteur `Bac`
     * sur le récepteur typé `Aquarium`).
     */
    TYPE_IMBRIQUE,
}

/**
 * Un membre NOMMÉ déclaré d'un type du projet.
 *
 * @property nom le nom appelé (`ouvrir`, `contenu`, `getTitre`) ;
 * @property genre le genre du membre ;
 * @property typeRetourNomme le nom ÉCRIT du type de retour ou de la
 * propriété (`Int`, `String`, `Boite`), `null` si absent/illisible — le
 * typage des initialiseurs s'en sert (fiche 022 : `val q = p.copy(x = 1)`).
 * @property parametres les paramètres DÉCLARÉS d'une méthode (attente
 * SAM : une interface à méthode unique type la lambda en position,
 * fiche 046) ; liste vide pour les propriétés.
 */
internal class MembreProjet(
    internal val nom: String,
    internal val genre: GenreMembreProjet,
    internal val typeRetourNomme: String?,
    internal val parametres: List<ParametreProjet> = emptyList(),
)

/**
 * Un paramètre DÉCLARÉ d'un appelable du projet (fonction, méthode,
 * constructeur primaire) — la matière des témoins d'ARITÉ (fiche 046 :
 * `kt.argumentCount`) et de l'ATTENTE des lambdas (paramètre explicite, `it`
 * — fiche 046 : la fonction projet type sa lambda par son paramètre).
 *
 * @property nom le nom du paramètre (`null` dans un type fonctionnel
 * écrit — `(String) -> Unit` ne nomme pas) ;
 * @property typeEcrit le nom ÉCRIT simple du type (`Int`, `String`),
 * `null` si absent/illisible ;
 * @property typeFonction le type FONCTIONNEL ÉCRIT du paramètre
 * (`(String) -> Unit` — paramètres + retour) quand il en porte un, `null`
 * sinon : c'est lui qui type la lambda ATTENDUE en position ;
 * @property aDefaut le paramètre porte une VALEUR PAR DÉFAUT (`b: Int = 2`)
 * — un défaut ÉTEINT le témoin d'arité (fiche 046) ;
 * @property estVararg le paramètre est un `vararg` — toute arité ≥ la
 * place libre est légale (le témoin se tait).
 */
internal class ParametreProjet(
    internal val nom: String?,
    internal val typeEcrit: String?,
    internal val typeFonction: TypeFonctionEcrit?,
    internal val aDefaut: Boolean,
    internal val estVararg: Boolean,
)

/**
 * Un type FONCTIONNEL ÉCRIT (`(String, Int) -> Unit`) — lu de la production
 * `FUNCTION_TYPE` d'une référence de type, il porte les types des PARAMÈTRES
 * et le RETOUR : l'ATTENTE qui type la lambda en position (fiche 046).
 *
 * @property parametres les noms ÉCRITS simples des types de paramètres
 * (`String`, `Int`) ; liste vide pour `() -> R` ;
 * @property retour le nom ÉCRIT simple du type de retour (`Unit`).
 */
internal class TypeFonctionEcrit(
    internal val parametres: List<String>,
    internal val retour: String,
)

/**
 * Un appelable de PREMIER NIVEAU du projet : fonction, propriété ou
 * EXTENSION déclarée au niveau supérieur d'un fichier Kotlin des racines
 * (fiche 022 : `fun Boite.saluer()` — l'extension projet rend le sélecteur
 * muet sur tout récepteur du type).
 *
 * @property nom le nom appelé ;
 * @property recepteurNomme le nom ÉCRIT du récepteur pour une EXTENSION
 * (`Boite`), `null` pour un appelable simple ;
 * @property typeRetourNomme le nom ÉCRIT du type de retour, `null` sinon ;
 * @property paquetSource le package du fichier déclarant (la résolution du
 * récepteur écrit y préfère les types du même package) ;
 * @property uri l'identifiant du fichier déclarant (`""` = document
 * COURANT — le témoin d'arité s'y vérifie PROPRE, fiche 017) ;
 * @property parametres les paramètres DÉCLARÉS (témoins d'arité, attente
 * des lambdas) ; liste vide pour les propriétés.
 */
internal class AppelableProjet(
    internal val nom: String,
    internal val recepteurNomme: String?,
    internal val typeRetourNomme: String?,
    internal val paquetSource: String,
    internal val uri: String,
    internal val parametres: List<ParametreProjet>,
)

/**
 * La contribution d'UN fichier au modèle du projet (Kotlin ou Java).
 *
 * @property uri l'identifiant du fichier (`""` = document courant) ;
 * @property paquet le package déclaré (`""` si aucun) ;
 * @property types les types déclarés (imbriqués compris — nom simple) ;
 * @property appelables les appelables de premier niveau ;
 * @property extensions les extensions de premier niveau.
 */
internal class ContributionFichier(
    internal val uri: String,
    internal val paquet: String,
    internal val types: List<TypeProjet>,
    internal val appelables: List<AppelableProjet>,
    internal val extensions: List<AppelableProjet>,
)

/**
 * Le modèle de symboles du projet : les types et appelables du SCAN des
 * racines sources (fichiers EXTERNES Kotlin et Java) et du document
 * COURANT, assemblés en tables de consultation pour la résolution (étage 2
 * — ADR 0105/0106).
 *
 * La clé des types est le FQCN (package + nom simple) ; la consultation par
 * nom simple préfère le package du DOCUMENT (le même package que le code en
 * cours d'édition), puis accepte un nom GLOBALEMENT unique (un nom porté
 * par plusieurs packages sans import est inconnu — conservateur).
 *
 * La construction est TOUJOURS tolérante : un fichier illisible ou un arbre
 * hostile rend une contribution VIDE (D3 — jamais d'exception).
 */
internal class ModeleProjet private constructor(
    private val types: Map<String, TypeProjet>,
    private val parNomSimple: Map<String, List<TypeProjet>>,
    private val appelables: Map<String, List<AppelableProjet>>,
    private val extensions: Map<String, List<AppelableProjet>>,
    private val paquetsConnus: Set<String>,
    private val imbriques: Map<String, List<TypeProjet>>,
    internal val extensionsNonResolues: Boolean,
) {

    /**
     * Les packages du projet (les packages énumérables — un joker sur l'un
     * d'eux laisse la preuve d'absence possible).
     */
    internal fun paquetsConnus(): Set<String> = paquetsConnus

    /**
     * Rend le type du projet portant ce FQCN exact.
     *
     * @param fqcn le nom pleinement qualifié (`com.ex.app.Boite`) ;
     * @return le type ; `null` si le modèle n'en connaît aucun.
     */
    internal fun type(fqcn: String): TypeProjet? = types[fqcn]

    /**
     * Les types IMBRIQUÉS déclarés dans le corps du type portant ce FQCN
     * (vague 2c — fiche 029 : la consultation imbriquée du modèle, miroir
     * des classes `$` du classpath).
     *
     * @param fqcnParent le FQCN pointé du conteneur ;
     * @return les imbriquées DIRECTES du conteneur ; liste vide si aucune.
     */
    internal fun imbriquesDu(fqcnParent: String): List<TypeProjet> = imbriques[fqcnParent].orEmpty()

    /**
     * Rend le type du projet portant ce nom SIMPLE : le package préféré
     * d'abord (celui du document en cours d'édition), puis un nom
     * GLOBALEMENT unique à défaut.
     *
     * @param nomSimple le nom écrit (`Boite`) ;
     * @param paquetPrefere le package du document (`""` si aucun) ;
     * @return le type ; `null` si absent, ou porté par PLUSIEURS packages
     * sans être dans le package préféré (ambigu — conservateur).
     */
    internal fun typeParNomSimple(nomSimple: String, paquetPrefere: String): TypeProjet? {
        val candidats = parNomSimple[nomSimple] ?: return null
        candidats.firstOrNull { extrairePaquet(it.fqcn) == paquetPrefere }?.let { return it }
        return if (candidats.size == 1) candidats.single() else null
    }

    /**
     * Rend les appelables de premier niveau portant EXACTEMENT ce nom dans
     * ce package.
     *
     * @param nom le nom appelé ;
     * @param paquet le package porteur ;
     * @return les appelables du nom en ce package ; liste vide si aucun.
     */
    internal fun appelables(nom: String, paquet: String): List<AppelableProjet> {
        return appelables[cle(nom, paquet)].orEmpty()
    }

    /**
     * Rend les extensions du projet déclarées sur ce récepteur FQCN.
     *
     * @param recepteurFqcn le nom pleinement qualifié du récepteur ;
     * @return les extensions sur ce récepteur ; liste vide si aucune.
     */
    internal fun extensionsSur(recepteurFqcn: String): List<AppelableProjet> {
        return extensions[recepteurFqcn].orEmpty()
    }

    /** La clé d'un appelable (nom + package). */
    private fun cle(nom: String, paquet: String): String = "$paquet\u0000$nom"

    /** Le package d'un FQCN (tout avant le dernier point, `""` à la racine). */
    private fun extrairePaquet(fqcn: String): String {
        val point = fqcn.lastIndexOf('.')
        return if (point <= 0) "" else fqcn.substring(0, point)
    }

    /** Le bâtisseur du modèle (contributions assemblées dans l'ordre de la marche). */
    internal class Batisseur {
        private val types = LinkedHashMap<String, TypeProjet>()
        private val parNomSimple = LinkedHashMap<String, MutableList<TypeProjet>>()
        private val appelables = LinkedHashMap<String, MutableList<AppelableProjet>>()
        private val extensionsBrutes = mutableListOf<AppelableProjet>()

        /**
         * Ajoute une contribution de fichier : les types entrent par FQCN
         * (le PREMIER gagne en ordre de marche — miroir du scan), les
         * appelables s'accumulent (les surcharges relèvent de la
         * résolution), les extensions attendent la résolution de leur
         * récepteur ([figer] — deuxième passe).
         *
         * @param contribution la contribution du fichier.
         */
        internal fun ajouter(contribution: ContributionFichier) {
            for (t in contribution.types) {
                if (t.fqcn !in types) {
                    types[t.fqcn] = t
                    parNomSimple.getOrPut(t.nomSimple) { mutableListOf() }.add(t)
                }
            }
            for (a in contribution.appelables) {
                appelables.getOrPut(cle(a.nom, contribution.paquet)) { mutableListOf() }.add(a)
            }
            extensionsBrutes.addAll(contribution.extensions)
        }

        /**
         * Fige le modèle immuable : la DEUXIÈME passe résout le récepteur
         * ÉCRIT de chaque extension (le package de son fichier d'abord,
         * puis un nom globalement unique) — une extension dont le
         * récepteur ne résout à AUCUN type du projet (alias, qualifié,
         * classpath) marque le modèle [ModeleProjet.extensionsNonResolues]
         * : elle pourrait viser N'IMPORTE QUEL récepteur, l'univers est
         * ouvert (garde de sonorité D3).
         */
        internal fun figer(): ModeleProjet {
            val extensions = LinkedHashMap<String, MutableList<AppelableProjet>>()
            var nonResolues = false
            for (extension in extensionsBrutes) {
                val recepteur = extension.recepteurNomme
                if (recepteur == null) {
                    nonResolues = true
                    continue
                }
                val cibles = parNomSimple[recepteur].orEmpty()
                val cible = cibles.firstOrNull { extrairePaquet(it.fqcn) == extension.paquetSource }
                    ?: if (cibles.size == 1) cibles.single() else null
                if (cible == null) {
                    nonResolues = true
                    continue
                }
                extensions.getOrPut(cible.fqcn) { mutableListOf() }.add(extension)
            }
            val paquets = types.keys.map(::extrairePaquet).toSet()
            // L'index des IMBRIQUÉES par conteneur (vague 2c — fiche 029) :
            // chaque type dont le PRÉFIXE pointé est lui-même un type connu
            // est une imbriquée directe de ce conteneur.
            val indexImbriques = LinkedHashMap<String, MutableList<TypeProjet>>()
            for ((fqcn, type) in types) {
                val point = fqcn.lastIndexOf('.')
                if (point <= 0) continue
                val parent = fqcn.substring(0, point)
                if (parent in types) {
                    indexImbriques.getOrPut(parent) { mutableListOf() }.add(type)
                }
            }
            return ModeleProjet(
                types = types,
                parNomSimple = parNomSimple,
                appelables = appelables,
                extensions = extensions,
                paquetsConnus = paquets,
                imbriques = indexImbriques,
                extensionsNonResolues = nonResolues,
            )
        }

        /** Le package d'un FQCN (tout avant le dernier point, `""` à la racine). */
        private fun extrairePaquet(fqcn: String): String {
            val point = fqcn.lastIndexOf('.')
            return if (point <= 0) "" else fqcn.substring(0, point)
        }

        private fun cle(nom: String, paquet: String): String = "$paquet\u0000$nom"
    }

    internal companion object {

        /** Le modèle VIDE (aucune contribution — projet sans sources). */
        internal val VIDE: ModeleProjet =
            ModeleProjet(emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptySet(), emptyMap(), false)

        /**
         * Extrait la contribution d'un fichier KOTLIN parsé (le scan des
         * racines ou le document COURANT — ce dernier porte `uri = ""` pour
         * primer sur son doublon périmé du scan).
         *
         * La collecte se fait au NIVEAU DES NŒUDS (classes, objets, fonctions,
         * propriétés — les déclarations typées de la façade ne couvrent pas
         * les objets nommés à cet étage, la navigation par genre reste la
         * voie complète).
         *
         * @param arbre la racine de la façade `Kt*` du fichier ;
         * @param uri l'identifiant du fichier (`""` = document courant) ;
         * @return la contribution (vide si le fichier ne déclare rien).
         */
        internal fun contributionKotlin(arbre: KtFichier, uri: String): ContributionFichier {
            val paquet = arbre.directivePackage()?.nomQualifie().orEmpty()
            val types = mutableListOf<TypeProjet>()
            val appelables = mutableListOf<AppelableProjet>()
            val extensions = mutableListOf<AppelableProjet>()
            for (enfant in arbre.noeud.enfants) {
                collecterDeclaration(enfant, paquet, chemin = emptyList(), uri, types, appelables, extensions)
            }
            return ContributionFichier(uri, paquet, types, appelables, extensions)
        }

        /**
         * Collecte une déclaration (récursif pour les membres et types
         * imbriqués) — le [chemin] porte les noms des types ENGLOBANTS
         * (vide au niveau supérieur) : une imbriquée vit au FQCN pointé
         * `paquet.Conteneur.Imbriquee` (vague 2c — fiche 029).
         */
        private fun collecterDeclaration(
            noeud: NoeudArbre,
            paquet: String,
            chemin: List<String>,
            uri: String,
            types: MutableList<TypeProjet>,
            appelables: MutableList<AppelableProjet>,
            extensions: MutableList<AppelableProjet>,
        ) {
            when (noeud.genre) {
                KtNodeTypes.CLASS, KtNodeTypes.OBJECT_DECLARATION -> {
                    val nom = nomDeDeclaration(noeud) ?: return
                    types.add(typeKotlin(noeud, nom, paquet, chemin, uri))
                    val corps = noeud.enfants.firstOrNull { it.genre == KtNodeTypes.CLASS_BODY } ?: return
                    for (membre in corps.enfants) {
                        collecterDeclaration(membre, paquet, chemin + nom, uri, types, appelables, extensions)
                    }
                }
                KtNodeTypes.FUN -> {
                    val nom = nomDeDeclaration(noeud) ?: return
                    val recepteur = recepteurEcrit(noeud)
                    val appelable = AppelableProjet(
                        nom, recepteur, typeRetourEcrit(noeud), paquet, uri,
                        parametresDeclares(noeud),
                    )
                    if (recepteur == null) appelables.add(appelable) else extensions.add(appelable)
                }
                KtNodeTypes.PROPERTY -> {
                    val nom = nomDeDeclaration(noeud) ?: return
                    val recepteur = recepteurEcrit(noeud)
                    val appelable = AppelableProjet(nom, recepteur, typeEcritPropriete(noeud), paquet, uri, emptyList())
                    if (recepteur == null) appelables.add(appelable) else extensions.add(appelable)
                }
                else -> Unit
            }
        }

        /**
         * Construit le [TypeProjet] d'une déclaration de type Kotlin (nœud) —
         * les types IMBRIQUÉS du corps entrent AUSSI comme membres
         * TYPE_IMBRIQUE du conteneur (vague 2c — fiche 029 : `Aquarium.Bac`
         * résout par le sélecteur `Bac` sur le récepteur `Aquarium`).
         */
        private fun typeKotlin(
            noeud: NoeudArbre,
            nom: String,
            paquet: String,
            chemin: List<String>,
            uri: String,
        ): TypeProjet {
            val genre = when {
                noeud.genre == KtNodeTypes.OBJECT_DECLARATION -> GenreTypeProjet.OBJET
                porteurModificateur(noeud, KtTokens.ENUM_MODIFIER) -> GenreTypeProjet.ENUM
                noeud.jetonsPropres.any { it.genre == KtTokens.INTERFACE_KEYWORD } -> GenreTypeProjet.INTERFACE
                else -> GenreTypeProjet.CLASSE
            }
            val membres = mutableListOf<MembreProjet>()
            var nbProprietesCtor = 0

            // Paramètres de constructeur primaire `val`/`var` : des membres.
            val primaire = noeud.enfantsDe(KtNodeTypes.PRIMARY_CONSTRUCTOR).firstOrNull()
            val listeParams = primaire?.enfantsDe(KtNodeTypes.VALUE_PARAMETER_LIST)?.firstOrNull()
                ?: noeud.enfantsDe(KtNodeTypes.VALUE_PARAMETER_LIST).firstOrNull()
            val parametresPrimaire = mutableListOf<ParametreProjet>()
            if (listeParams != null) {
                for (parametre in listeParams.enfantsDe(KtNodeTypes.VALUE_PARAMETER)) {
                    val nomParam = nomDeDeclaration(parametre) ?: continue
                    val estMembre = parametre.jetonsPropres.any {
                        it.genre == KtTokens.VAL_KEYWORD || it.genre == KtTokens.VAR_KEYWORD
                    }
                    parametresPrimaire.add(parametreProjet(parametre, nomParam))
                    if (estMembre) {
                        membres.add(MembreProjet(nomParam, GenreMembreProjet.PROPRIETE, typeEcritParametre(parametre)))
                        nbProprietesCtor++
                    }
                }
            }

            val corps = noeud.enfants.firstOrNull { it.genre == KtNodeTypes.CLASS_BODY }
            if (corps != null) {
                for (membre in corps.enfants) {
                    when (membre.genre) {
                        KtNodeTypes.FUN -> {
                            val n = nomDeDeclaration(membre) ?: continue
                            membres.add(
                                MembreProjet(
                                    n, GenreMembreProjet.FONCTION, typeRetourEcrit(membre),
                                    parametresDeclares(membre),
                                ),
                            )
                        }
                        KtNodeTypes.PROPERTY -> {
                            val n = nomDeDeclaration(membre) ?: continue
                            membres.add(MembreProjet(n, GenreMembreProjet.PROPRIETE, typeEcritPropriete(membre)))
                        }
                        KtNodeTypes.CLASS, KtNodeTypes.OBJECT_DECLARATION -> {
                            // Un type IMBRIQUÉ du corps est un MEMBRE du
                            // conteneur (vague 2c — fiche 029) : le
                            // sélecteur `Bac` sur un récepteur `Aquarium`
                            // résout, et le typage rend l'imbriquée.
                            val n = nomDeDeclaration(membre) ?: continue
                            membres.add(MembreProjet(n, GenreMembreProjet.TYPE_IMBRIQUE, null))
                        }
                        else -> Unit
                    }
                }
            }

            val estData = porteurModificateur(noeud, KtTokens.DATA_MODIFIER)

            val entrees = if (genre == GenreTypeProjet.ENUM && corps != null) {
                corps.enfants
                    .filter { it.genre == KtNodeTypes.ENUM_ENTRY }
                    .mapNotNull { nomDeDeclaration(it) }
            } else {
                emptyList()
            }

            var type = TypeProjet(
                uri = uri,
                fqcn = (if (paquet.isEmpty()) chemin else listOf(paquet) + chemin).let {
                    if (it.isEmpty()) nom else it.joinToString(".") + "." + nom
                },
                nomSimple = nom,
                genre = genre,
                supertypesEcrits = supertypesEcrits(noeud),
                membres = membres,
                estData = estData,
                entreesEnum = entrees,
                parametresPrimaire = parametresPrimaire,
            )
            if (estData && nbProprietesCtor > 0) {
                type = type.avecSynthesesData(nbProprietesCtor)
            }
            return type
        }

        /** Le nœud porte-t-il ce modificateur doux (MODIFIER_LIST comprise) ? */
        private fun porteurModificateur(noeud: NoeudArbre, modificateur: com.intellij.platform.syntax.SyntaxElementType): Boolean {
            return noeud.enfants
                .filter { it.genre == KtNodeTypes.MODIFIER_LIST }
                .any { liste -> liste.enfants.any { it.genre == modificateur } }
        }

        /** Les enfants directs d'un nœud interne d'un genre exact (aide). */
        private fun NoeudArbre.enfantsDe(genre: com.intellij.platform.syntax.SyntaxElementType): List<NoeudArbre> {
            return enfants.filter { it.genre == genre }
        }

        /**
         * Les noms de types ÉCRITS dans la clause de supertypes (`Base` pour
         * `: Base()`) — INTERNE : la passe sémantique s'en sert aussi pour
         * les OBJETS ANONYMES (vague 2c — la pile des this, fiche 028).
         */
        internal fun supertypesEcrits(noeud: NoeudArbre): List<String> {
            val clause = noeud.enfantsDe(KtNodeTypes.SUPER_TYPE_LIST).firstOrNull()
                ?: return emptyList()
            val noms = mutableListOf<String>()
            for (entree in clause.enfants) {
                if (entree.genre != KtNodeTypes.SUPER_TYPE_ENTRY &&
                    entree.genre != KtNodeTypes.SUPER_TYPE_CALL_ENTRY
                ) {
                    continue
                }
                val reference = entree.enfantsDe(KtNodeTypes.CONSTRUCTOR_CALLEE).firstOrNull()
                    ?.enfantsDe(KtNodeTypes.TYPE_REFERENCE)?.firstOrNull()
                    ?: entree.enfantsDe(KtNodeTypes.TYPE_REFERENCE).firstOrNull()
                val utilisateur = reference?.enfantsDe(KtNodeTypes.USER_TYPE)?.firstOrNull()
                if (utilisateur != null && utilisateur.enfants.all { it.genre != KtNodeTypes.USER_TYPE }) {
                    val nom = utilisateur.texteCanonique.substringBefore('<')
                    if (nom.isNotEmpty()) noms.add(nom)
                }
            }
            return noms
        }

        /** Le récepteur ÉCRIT d'une fonction/propriété d'extension (`Boite`), `null` sinon. */
        private fun recepteurEcrit(noeud: NoeudArbre): String? {
            val enfants = noeud.enfants
            val listeParams = enfants.indexOfFirst { it.genre == KtNodeTypes.VALUE_PARAMETER_LIST }
            if (listeParams <= 0) return null
            for (i in 0 until listeParams) {
                if (enfants[i].genre != KtNodeTypes.TYPE_REFERENCE) continue
                val utilisateur = enfants[i].enfantsDe(KtNodeTypes.USER_TYPE).firstOrNull() ?: continue
                if (utilisateur.enfants.any { it.genre == KtNodeTypes.USER_TYPE }) continue
                val nom = utilisateur.texteCanonique.substringBefore('<')
                if (nom.isNotEmpty()) return nom
            }
            return null
        }

        /** Le type de retour ÉCRIT d'une fonction (`null` si absent). */
        private fun typeRetourEcrit(noeud: NoeudArbre): String? {
            val enfants = noeud.enfants
            val listeParams = enfants.indexOfFirst { it.genre == KtNodeTypes.VALUE_PARAMETER_LIST }
            for (i in (listeParams + 1) until enfants.size) {
                if (enfants[i].genre != KtNodeTypes.TYPE_REFERENCE) continue
                return typeEcritDeReference(enfants[i])
            }
            return null
        }

        /** Le type ÉCRIT d'une propriété (`null` si absent). */
        private fun typeEcritPropriete(noeud: NoeudArbre): String? {
            for (enfant in noeud.enfants) {
                if (enfant.genre == KtNodeTypes.TYPE_REFERENCE) return typeEcritDeReference(enfant)
            }
            return null
        }

        /** Le type ÉCRIT d'un paramètre (`null` si absent). */
        private fun typeEcritParametre(noeud: NoeudArbre): String? {
            for (enfant in noeud.enfants) {
                if (enfant.genre == KtNodeTypes.TYPE_REFERENCE) return typeEcritDeReference(enfant)
            }
            return null
        }

        /**
         * Les paramètres DÉCLARÉS d'une fonction (témoins d'arité et attente
         * des lambdas — fiche 046) : la liste de paramètres de valeur du
         * nœud `FUN`, chacun avec son type écrit, son type fonctionnel le
         * cas échéant, son défaut et son `vararg`.
         */
        private fun parametresDeclares(noeud: NoeudArbre): List<ParametreProjet> {
            val liste = noeud.enfantsDe(KtNodeTypes.VALUE_PARAMETER_LIST).firstOrNull()
                ?: return emptyList()
            return liste.enfantsDe(KtNodeTypes.VALUE_PARAMETER)
                .mapNotNull { parametre ->
                    val nom = nomDeDeclaration(parametre) ?: return@mapNotNull null
                    parametreProjet(parametre, nom)
                }
        }

        /**
 * Construit le [ParametreProjet] d'un nœud `VALUE_PARAMETER` : type
 * écrit simple, type FONCTIONNEL le cas échéant, défaut (`=` écrit) et
 * `vararg` (modificateur doux).
 */
        private fun parametreProjet(parametre: NoeudArbre, nom: String): ParametreProjet {
            var typeEcrit: String? = null
            var typeFonction: TypeFonctionEcrit? = null
            for (enfant in parametre.enfants) {
                if (enfant.genre != KtNodeTypes.TYPE_REFERENCE) continue
                typeFonction = typeFonctionEcritDe(enfant)
                typeEcrit = typeEcritDeReference(enfant)
                break
            }
            val aDefaut = parametre.enfants.any {
                it.genre != KtNodeTypes.TYPE_REFERENCE && it.genre != KtNodeTypes.MODIFIER_LIST
            }
            val estVararg = parametre.enfants
                .filter { it.genre == KtNodeTypes.MODIFIER_LIST }
                .any { liste -> liste.enfants.any { it.genre == KtTokens.VARARG_MODIFIER } }
            return ParametreProjet(nom, typeEcrit, typeFonction, aDefaut, estVararg)
        }

        /**
         * Le type FONCTIONNEL écrit d'une référence de type est lu par la
         * fonction de HAUT NIVEAU [typeFonctionEcritDe] (partagée avec
         * l'attente des lambdas, vague 2b).
         */

        /**
 * Le nom simple du type d'une référence de type (sans arguments) — la
 * référence NULLABLE (`String?`) est DÉBALLÉE vers son type utilisateur
 * (la nullabilité relève du typage, vague 2b : le nom écrit reste
 * `String`) ; un type FONCTIONNEL ne rend PAS de nom simple (`null`).
 */
        private fun typeEcritDeReference(reference: NoeudArbre): String? {
            // Le porteur de l'utilisateur : la référence elle-même, ou son
            // déballage NULLABLE_TYPE (`String?` → USER_TYPE `String`).
            var porteur: NoeudArbre =
                reference.enfantsDe(KtNodeTypes.NULLABLE_TYPE).firstOrNull() ?: reference
            if (porteur.genre == KtNodeTypes.NULLABLE_TYPE) {
                porteur = porteur.enfantsDe(KtNodeTypes.USER_TYPE).firstOrNull() ?: return null
            }
            val utilisateur = porteur.enfantsDe(KtNodeTypes.USER_TYPE).firstOrNull()
                ?: porteur.takeIf { it.genre == KtNodeTypes.USER_TYPE }
                ?: return null
            val nom = utilisateur.texteCanonique.substringBefore('<')
            return nom.ifEmpty { null }
        }

        /** Le nom d'un nœud déclaration (premier IDENTIFIER propre). */
        private fun nomDeDeclaration(noeud: NoeudArbre): String? {
            return noeud.jetonsPropres.firstOrNull { it.genre == KtTokens.IDENTIFIER }?.texte
        }
    }
}

/**
 * Le type FONCTIONNEL écrit d'une référence de type (`(String) -> Unit`)
 * — paramètres + retour, lus des productions `FUNCTION_TYPE` ; `null`
 * si la référence n'en porte pas (partagé : modèle des paramètres et
 * attente des lambdas, vague 2b).
 */
internal fun typeFonctionEcritDe(reference: NoeudArbre): TypeFonctionEcrit? {
    val fonctionnelle = reference.enfants
        .filter { it.genre == KtNodeTypes.FUNCTION_TYPE }
        .firstOrNull() ?: return null
    val parametres = mutableListOf<String>()
    val liste = fonctionnelle.enfants
        .filter { it.genre == KtNodeTypes.VALUE_PARAMETER_LIST }
        .firstOrNull()
    if (liste != null) {
        for (parametre in liste.enfants.filter { it.genre == KtNodeTypes.VALUE_PARAMETER }) {
            val type = parametre.enfants
                .firstOrNull { it.genre == KtNodeTypes.TYPE_REFERENCE }
                ?.let { referenceTypeEcritSimple(it) }
            if (type != null) parametres.add(type)
        }
    }
    val retour = fonctionnelle.enfants
        .lastOrNull { it.genre == KtNodeTypes.TYPE_REFERENCE }
        ?.let { referenceTypeEcritSimple(it) }
        ?: return null
    return TypeFonctionEcrit(parametres, retour)
}

/** Le nom simple ÉCRIT d'une référence de type (nullable déballée — aide). */
private fun referenceTypeEcritSimple(reference: NoeudArbre): String? {
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

/** Ajoute les membres synthétisés d'une data class (copy, componentN — fiche 022). */
private fun TypeProjet.avecSynthesesData(nbProprietes: Int): TypeProjet {
    val synthetises = mutableListOf<MembreProjet>()
    synthetises.add(MembreProjet("copy", GenreMembreProjet.FONCTION, nomSimple))
    for (rang in 1..nbProprietes) {
        synthetises.add(MembreProjet("component$rang", GenreMembreProjet.FONCTION, null))
    }
    return TypeProjet(
        uri = uri,
        fqcn = fqcn,
        nomSimple = nomSimple,
        genre = genre,
        supertypesEcrits = supertypesEcrits,
        membres = membres + synthetises,
        estData = estData,
        entreesEnum = entreesEnum,
        parametresPrimaire = parametresPrimaire,
    )
}
