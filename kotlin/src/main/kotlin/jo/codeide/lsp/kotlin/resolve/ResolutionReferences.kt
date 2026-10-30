// CodeIDE — résolution de références du socle (module :kotlin, étage 2 vague
// 2a). Code NEUF jjoblab (clean-room — contrat : fiches 017/022/006/042 du
// cahier docs/cahier-specifications-kotlin/ + ADR 0106 (hiérarchie de
// portées : locales → membres → englobantes → paquet → imports exact/joker/
// alias → défauts kotlin.*/java.lang.*) + ADR 0223 (sélection par types
// complets : jamais de résolution devinée). L'ancien `resolve/` est en
// lecture interdite R9.1 — cette implémentation est écrite cahier en main.
package jo.codeide.lsp.kotlin.resolve

import jo.codeide.lsp.kotlin.index.VueAppelables
import jo.codeide.lsp.kotlin.parse.NoeudArbre
import jo.codeide.lsp.kotlin.parse.kt.KtDirectiveImport
import jo.codeide.lsp.kotlin.parse.kt.KtFichier

/**
 * L'univers des preuves d'une requête de diagnostics sémantiques : le
 * document courant, le modèle du projet (scan + document), l'état de
 * l'index des appelables (guichet de la fiche 042) et le régime du
 * document.
 *
 * ## Régimes (la preuve ou le silence — D3)
 *
 * - **Guichet muet** ([index] `null` — classpath non posé ou passe non
 *   publiée) : AUCUNE absence n'est prouvable, `kt.unresolved` se tait
 *   (fiche 046 : « sans classpath, jamais signalé ») ;
 * - **Scan PARTIEL** : l'univers projet n'est pas complet — les preuves
 *   d'absence se taisent (miroir du correctif H1 du rapport faux positifs,
 *   fiche 036 à venir) ;
 * - **Joker non énumérable** : un import joker d'un package que le modèle
 *   ne connaît pas OUVRIRAIT l'univers — les noms nus non résolus se
 *   taisent (un `import java.util.*` sans vue JVM ne permet pas de prouver
 *   qu'un nom n'y vit pas).
 *
 * @property arbre la racine du document diagnostiqué ;
 * @property paquet le package ÉCRIT du document (`""` si aucun) ;
 * @property modele le modèle du projet (document courant PRIMÉ sur son
 * doublon périmé du scan) ;
 * @property index la vue des appelables du classpath si le guichet couvre
 * le classpath posé ; `null` sinon (régime dégradé — silence) ;
 * @property etatScan l'état du scan des racines ;
 * @property script le document est-il un script `.kts` (les récepteurs
 * implicites du DSL y sont inconnus : noms nus muets, ADR 0106) ;
 * @property vueJvm la vue JVM des MEMBRES du classpath (vague 2b — ADR
 * 0223) si le guichet couvre le classpath posé ; `null` sinon (les
 * sélecteurs sur récepteurs de bibliothèque restent muets).
 */
internal class UniversPreuves(
    internal val arbre: KtFichier,
    internal val paquet: String,
    internal val modele: ModeleProjet,
    internal val index: VueAppelables?,
    internal val etatScan: EtatScan,
    internal val script: Boolean,
    internal val vueJvm: VueJvmMembres? = null,
) {

    /** Les imports du document (calculés une fois). */
    private val imports: List<KtDirectiveImport> = arbre.imports()

    /** Le cache des CLÔTURES d'héritage par FQCN projet (vague 2c). */
    private val clotures = HashMap<String, ClotureHeritage?>()

    /**
     * La CLÔTURE D'HÉRITAGE d'un type du projet (vague 2c — fiches
     * 044/028/040) : mémoïsée par FQCN ; `null` quand la chaîne ne se
     * ferme pas (univers ouvert, D3).
     *
     * @param type le type du projet à clore ;
     * @return la clôture (membres déclarés + hérités de la chaîne mixte
     * projet/classpath) ; `null` si ouverte.
     */
    internal fun clotureDe(type: TypeProjet): ClotureHeritage? {
        synchronized(clotures) {
            if (clotures.containsKey(type.fqcn)) return clotures[type.fqcn]
            val cloture = Clotures.clotureDuType(this, type)
            clotures[type.fqcn] = cloture
            return cloture
        }
    }

    /**
     * Le FQCN JVM d'un type Kotlin écrit (les MAPPÉS — `kotlin.String` se
     * lit `java.lang.String` dans les entrées ; identité sinon).
     */
    internal fun mapeJvm(fqcn: String): String = MAPPES_JVM[fqcn] ?: fqcn

    /**
     * Résout un SUPERTYPE ÉCRIT (vague 2c — la clôture mixte) : le modèle
     * du projet D'ABORD (même package, puis nom globalement unique), puis
     * les imports EXACTS (le FQCN écrit — un type du projet y prime, sinon
     * le lien classpath), puis le catalogue KP8. AUCUNE résolution devinée
     * par nom simple du classpath (ADR 0223) : un supertype introuvable
     * par ces voies rend `null` — la chaîne est OUVERTE (muet, D3).
     *
     * @param nomEcrit le nom écrit dans la clause (`Base`, `Activity`) ;
     * @return le maillon résolu (projet ou bibliothèque) ; `null` sinon.
     */
    internal fun supertypeResolu(nomEcrit: String): TypeValeur? {
        // 1. Le modèle du projet (package courant d'abord, unique ensuite).
        modele.typeParNomSimple(nomEcrit, paquet)?.let { return TypeValeur.Projet(it) }

        // 2. Les imports EXACTS : le nom effectif (alias compris).
        for (directive in imports) {
            if (directive.estJoker()) continue
            if (directive.nomEffectifImport() != nomEcrit) continue
            val chemin = directive.nomQualifie() ?: continue
            modele.type(chemin)?.let { return TypeValeur.Projet(it) }
            return TypeValeur.Jvm(chemin)
        }

        // 3. Le catalogue KP8 (les types bien connus des paquets par défaut).
        NOMS_FQCN_DEFAUT[nomEcrit]?.let { return TypeValeur.Jvm(it) }
        return null
    }

    /**
     * Le verdict d'une résolution de nom.
     */
    internal enum class Verdict {
        /** Le nom résout (une preuve de présence existe). */
        RESOLU,

        /** La preuve d'absence est IMPOSSIBLE (univers ouvert — silence D3). */
        MUET,

        /** L'absence est PROUVÉE : le nom est introuvable partout (émission). */
        SIGNALABLE,
    }

    /**
     * Interroge si l'univers permet des preuves d'absence de NOMS NUS.
     *
     * @return `true` si les conditions sont réunies (guichet portant, scan
     * non partiel, aucun joker non énumérable).
     */
    internal val nomsNusProuvables: Boolean
        get() = index != null && etatScan != EtatScan.PARTIEL && !aUnJokerNonEnumerable

    /** Le document porte-t-il un import joker d'un package non énumérable ? */
    private val aUnJokerNonEnumerable: Boolean by lazy {
        imports.any { directive ->
            directive.estJoker() && directive.nomQualifie()?.let { !estPackageProjet(it) } ?: false
        }
    }

    /**
     * Résout un NOM NU du document (appel, référence d'expression).
     *
     * L'ordre de la hiérarchie de portées (ADR 0106) : déclarés du document
     * (la garde de la fiche 017 : un nom déclaré n'importe où n'est JAMAIS
     * signalé) → types du projet (un nom de classe/objet en position
     * d'expression) → imports exacts (nom effectif, alias compris) →
     * package courant (types + appelables projet) → paquets par défaut
     * (catalogue de types KP8 + appelables de l'index).
     *
     * La vague 2c y insère la PILE DES THIS (fiches 044/028/040) : les
     * MEMBRES HÉRITÉS des couches englobantes (clôture mixte
     * projet/classpath — la voie 2-ter d'ADR 0216) précèdent les imports,
     * miroir de la portée des membres de la spécification.
     *
     * @param nom le nom nu cherché ;
     * @param nomsDeclares les noms déclarés du document (garde fiche 017) ;
     * @param liaison la liaison nom → type ÉCRIT des portées locales
     * (paramètres, locales, membres — rend `null` sans liaison) ;
     * @param pile la pile des this de la position (vague 2c — vide au
     * niveau du document).
     * @return le verdict (RESOLU / MUET / SIGNALABLE).
     */
    internal fun resoudreNomNu(
        nom: String,
        nomsDeclares: Set<String>,
        liaison: MarcheTypee.Liaison,
        pile: PileThis = PileThis.VIDE,
    ): Verdict {
        // 1. La garde du document : un nom DÉCLARÉ n'importe où n'est jamais
        //    signalé (fiche 017 — déclaration en cours de frappe comprise).
        if (nom in nomsDeclares) return Verdict.RESOLU

        // 2. Les portées locales typées (paramètres, locales, membres) : la
        //    liaison rend le type écrit — sa simple existence résout.
        if (liaison(nom) != null) return Verdict.RESOLU

        // 2-ter. Les MEMBRES HÉRITÉS des couches englobantes (vague 2c —
        //    ADR 0216 : `setContentView(…)` nu dans une classe qui étend
        //    `Activity` résout par la clôture de la chaîne).
        if (pile.contientMembre(nom)) return Verdict.RESOLU

        // 3. Un type du projet en position d'expression (objet, accès
        //    statique : `Calculatrice.somme(…)`).
        if (modele.typeParNomSimple(nom, paquet) != null) return Verdict.RESOLU

        // 4. Les imports exacts : le nom effectif (alias compris) résout.
        if (imports.any { it.nomEffectif() == nom }) return Verdict.RESOLU

        // 5. Le package courant : appelables de premier niveau du projet.
        if (modele.appelables(nom, paquet).isNotEmpty()) return Verdict.RESOLU

        // 6. Les appelables du CLASSPATH visibles : package courant et
        //    paquets par DÉFAUT (voie 6 — ADR 0203 : la preuve exige la
        //    visibilité, la seule présence dans l'index ne suffit pas).
        if (index != null) {
            if (index.topParNom(nom).any { it.paquet == paquet || it.paquet in PAQUETS_DEFAUT }) {
                return Verdict.RESOLU
            }
        }

        // 6-bis. Les INTRINSÈQUES RÉPUTÉES du module par défaut (vague 2d
        //    — fiche 023 : `arrayOf` et la famille des constructeurs de
        //    tableaux vivent dans la RESSOURCE `kotlin.kotlin_builtins`,
        //    AUCUNE facade `*Kt` ne les porte — le décodeur @Metadata ne
        //    les verra JAMAIS, par conception D4) : le plancher documenté
        //    des appelables, miroir du catalogue KP8 pour les types.
        if (nom in BUILTINS_REPUTES_MUETS) return Verdict.RESOLU

        // 6-ter. Les CONVENTIONS DE SOURCES GÉNÉRÉES (vague 2e — fiche
        //    035 : la marche du scan EXCLUT `build` — ADR 0212 —, les
        //    classes KSP/Hilt (`Hilt_` préfixé), Room (`_Impl` suffixé)
        //    et DataBinding (`BindingImpl` suffixé) y vivent : leurs
        //    sources sont invisibles PAR CONCEPTION, leur absence n'est
        //    JAMAIS prouvable).
        if (estNomGenere(nom)) return Verdict.MUET

        // 7. Le catalogue KP8 : les types des paquets par défaut (positions
        //    de types ET d'expressions — un accès statique y est légal).
        if (nom in CATALOGUE_TYPES_DEFAUT) return Verdict.RESOLU

        // L'univers est-il seulement prouvable ?
        return if (nomsNusProuvables && !script) Verdict.SIGNALABLE else Verdict.MUET
    }

    /**
     * Résout un TYPE ÉCRIT du document (position de type, constructeur).
     *
     * @param nom le nom simple du type (`Boite`, `Int`) ;
     * @return le type du PROJET portant ce nom ; `null` si le nom résout
     * ailleurs (classpath, défauts) — la distinction importe pour la
     * preuve d'absence des sélecteurs (seuls les récepteurs PROJET sont
     * prouvables à cet étage).
     */
    internal fun typeProjetEcrit(nom: String): TypeProjet? {
        return modele.typeParNomSimple(nom, paquet)
    }

    /**
     * Résout un TYPE ÉCRIT du document en type VALEUR (vague 2b — fiche
     * 046) : le modèle du projet D'ABORD (un type du projet prime), puis
     * les imports EXACTS (le FQCN importé — un doublon du projet y prime
     * aussi), puis le catalogue KP8 (les types bien connus des paquets par
     * défaut, avec leur FQCN Kotlin).
     *
     * @param nomEcrit le nom ÉCRIT (`String`, `String?`, `Boite`,
     * `List<Int>` — nullabilité et arguments sont DÉPOUILLÉS) ;
     * @return le type ; `null` si le nom ne désigne ni un type du projet,
     * ni un import, ni un type par défaut connu (récepteur non typable,
     * sélecteur muet — D3).
     */
    internal fun resoudreTypeEcrit(nomEcrit: String?): TypeValeur? {
        if (nomEcrit == null) return null
        val nom = nomEcrit.trim()
            .removeSuffix("?")
            .substringBefore('<')
            .trim()
        if (nom.isEmpty()) return null

        // Un nom QUALIFIÉ (round-trip de la liaison typée — `kotlin.String`
        // rendu par l'initialisateur d'une propriété ; type écrit QUALIFIÉ
        // `Aquarium.Bac` — vague 2c, fiche 029) : FQCN direct du modèle,
        // puis le CHEMIN de segments (projet imbriqué, import + `$`
        // classpath, classe de packages), puis le FQCN brut en repli muet.
        if ('.' in nom) {
            modele.type(nom)?.let { return TypeValeur.Projet(it) }
            val segments = nom.split('.')
            if (segments.size > 1 && segments.all { it.isNotEmpty() }) {
                cheminClasse(segments)?.let { chemin ->
                    modele.type(chemin)?.let { return TypeValeur.Projet(it) }
                    return TypeValeur.Jvm(chemin)
                }
            }
            return TypeValeur.Jvm(nom)
        }

        // Le PROJET d'abord (même nom simple, même package ou unique).
        modele.typeParNomSimple(nom, paquet)?.let { return TypeValeur.Projet(it) }

        // Les imports EXACTS : le FQCN écrit — un type DU PROJET sous ce
        // FQCN y prime (l'import d'un type projet est légal).
        for (directive in imports) {
            if (directive.estJoker()) continue
            val chemin = directive.nomQualifie() ?: continue
            if (chemin.substringAfterLast('.') != nom && directive.alias() != nom) continue
            modele.type(chemin)?.let { return TypeValeur.Projet(it) }
            return TypeValeur.Jvm(chemin)
        }

        // Le catalogue KP8 : les types bien connus des paquets par défaut.
        NOMS_FQCN_DEFAUT[nom]?.let { return TypeValeur.Jvm(it) }
        return null
    }

    /**
     * Le document déclare-t-il ce nom n'importe où (garde documentaire —
     * la forme paresseuse de la fiche 017, consultée par le typage de
     * l'implicite `it`) ?
     */
    internal fun possedeNomDeclare(nom: String): Boolean {
        val declares = nomsDeclaresDuDocument
        return nom in declares
    }

    /** Les noms DÉCLARÉS du document (paresseux — garde de l'implicit `it`). */
    private val nomsDeclaresDuDocument: Set<String> by lazy {
        val noms = mutableSetOf<String>()
        fun collecter(noeud: jo.codeide.lsp.kotlin.parse.NoeudArbre) {
            if (noeud.genre in GENRES_DECLARATION) {
                noeud.jetonsPropres
                    .firstOrNull { it.genre == org.jetbrains.kotlin.kmp.lexer.KtTokens.IDENTIFIER }
                    ?.texte?.let { noms.add(it) }
            }
            noeud.enfants.forEach(::collecter)
        }
        collecter(arbre.noeud)
        noms
    }

    /**
     * Résout un nom de TYPE (position `USER_TYPE` ou appel de constructeur) :
     * le verdict dit si l'absence est prouvable. La vague 2c y insère les
     * TYPES IMBRIQUÉS des SUPERTYPES par nom simple (fiche 028 : hériter
     * met les imbriqués en portée — `Resultats` nu de `Base$Resultats`).
     *
     * @param nom le nom simple écrit ;
     * @param pile la pile des this de la position (vague 2c) ;
     * @return RESOLU (projet/import/défaut/alias/hérité), MUET (univers
     * ouvert) ou SIGNALABLE (type introuvable partout).
     */
    internal fun resoudreNomType(nom: String, pile: PileThis = PileThis.VIDE): Verdict {
        // Le projet (document primé, package courant, nom globalement unique).
        if (modele.typeParNomSimple(nom, paquet) != null) return Verdict.RESOLU

        // Les imports exacts : le nom effectif (alias compris) résout.
        if (imports.any { it.nomEffectif() == nom }) return Verdict.RESOLU

        // Les IMBRIQUÉS des couches englobantes (vague 2c — fiche 028 :
        // la clôture porte les imbriquées des supertypes).
        if (pile.contientMembre(nom)) return Verdict.RESOLU

        // Le catalogue KP8 (types des paquets par défaut).
        if (nom in CATALOGUE_TYPES_DEFAUT) return Verdict.RESOLU

        // Les TYPEALIAS du classpath visibles : paquets par défaut et
        // package courant (kotlin.collections.ArrayList et compagnie).
        if (index != null) {
            if (index.aliasTopParNom(nom).any { it.paquet == paquet || it.paquet in PAQUETS_DEFAUT }) {
                return Verdict.RESOLU
            }
        }

        // Les CONVENTIONS DE SOURCES GÉNÉRÉES (vague 2e — fiche 035) : un
        // type de `build/generated` est invisible du scan par conception —
        // son absence n'est jamais prouvable.
        if (estNomGenere(nom)) return Verdict.MUET

        return if (nomsNusProuvables) Verdict.SIGNALABLE else Verdict.MUET
    }

    /**
     * Vérifie un SÉLECTEUR sur un récepteur TYPAÉ (ADR 0223 : la sélection
     * par types complets — le jeu de membres complet décide) — du PROJET
     * (vague 2a) ou de BIBLIOTHÈQUE (vague 2b : la vue JVM des membres du
     * classpath, chaîne de supertypes comprise).
     *
     * Le jeu complet d'un type du PROJET sans héritage écrit : les membres
     * DÉCLARÉS (paramètres `val`/`var` du constructeur compris), les
     * SYNTHÉTISÉS des data classes (`copy`, `componentN`), les IMPLICITES
     * de Any/Object et les EXTENSIONS connues (projet par récepteur écrit,
     * classpath par FQCN — plus les extensions génériques `<T> T.…` et
     * celles de `Any`, qui atteignent tout récepteur : leur présence dans
     * l'index MUET la preuve, fiche 026).
     *
     * Le jeu complet d'un type de BIBLIOTHÈQUE : les membres DECLARÉS de
     * toute la chaîne de supertypes lue des ENTRÉES (vue JVM — un maillon
     * manquant rend la preuve muette), les PROPRIÉTÉS DÉRIVÉES des
     * accesseurs (ADR 0219) et les implicites ; les types MAPPÉS à membres
     * numériques (kotlin.Int…) restent MUETS (leurs membres attendus ne
     * vivent pas dans le bytecode de la classe encadrante — sous-rapport
     * assumé, jamais un faux positif).
     *
     * @param recepteur le type du récepteur (projet ou classpath) ;
     * @param nom le nom du sélecteur ;
     * @return RESOLU (membre/extension connu), MUET (univers ouvert —
     * héritage écrit, enum, extensions non résolues, chaîne illisible) ou
     * SIGNALABLE (ABSENCE PROUVÉE — émission).
     */
    internal fun verifierSelecteur(recepteur: TypeValeur, nom: String): Verdict {
        if (recepteur is TypeValeur.Projet) {
            return verifierSelecteurProjet(recepteur.type, nom)
        }
        val fqcn = (recepteur as TypeValeur.Jvm).fqcn

        // Les types mappés NUMÉRIQUES : leurs membres Kotlin (plus, inv…)
        // ne vivent pas dans le bytecode des classes encadrantes — muets.
        if (fqcn in MAPPES_NON_FIABLES) return Verdict.MUET

        // Scan partiel ou extensions non résolues : univers ouvert (D3).
        if (etatScan == EtatScan.PARTIEL) return Verdict.MUET
        if (modele.extensionsNonResolues) return Verdict.MUET

        // La vue JVM : le jeu complet de la chaîne (déclarés + dérivés +
        // implicites) — guichet muet ou maillon manquant = silence ; les
        // IMBRIQUÉES `$` du récepteur résolvent aussi (vague 2c — fiche
        // 028 : `Peinture.Capitaine.ROND` traverse `Peinture$Capitaine`).
        val jeu = vueJvm?.jeuComplet(MAPPES_JVM[fqcn] ?: fqcn) ?: return Verdict.MUET
        if (jeu.contient(nom)) return Verdict.RESOLU
        if (vueJvm.possedeImbrique(MAPPES_JVM[fqcn] ?: fqcn, nom)) return Verdict.RESOLU

        // Les implicites de Any/Object (le socle commun de tout récepteur).
        if (nom in MEMBRES_IMPLICITS) return Verdict.RESOLU

        // Le COMPAGNON du classpath (vague 2d — fiche 024 : `Regex.escape`
        // vit sur `kotlin.text.Regex$Companion`) : l'entrée compagnon
        // PRÉSENTE et LISIBLE rend ses noms (la classe est close, l'absence
        // y reste prouvable) ; PRÉSENTE mais ILLISIBLE = univers ouvert.
        val compagnon = vueJvm?.compagnonDe(fqcn)
        if (compagnon != null) {
            if (compagnon.illisible) return Verdict.MUET
            if (nom in compagnon.noms) return Verdict.RESOLU
        }

        return verdictExtensions(fqcn, nom)
    }

    /** La suite des EXTENSIONS du verdict classpath (récepteur FQCN Kotlin). */
    private fun verdictExtensions(fqcn: String, nom: String): Verdict {
        if (index == null) return Verdict.MUET
        // Les extensions sur le récepteur EXACT puis sur toute sa CHAÎNE de
        // supertypes (vague 2d — fiche 033 : `collect` sur `SharedFlow` vit
        // sur le maillon DISTANCIÉ `Flow`) — les maillons lus du bytecode
        // portent des noms JVM, les clés de l'index des noms KOTLIN : le
        // miroir inverse des types MAPPÉS est consulté aussi.
        if (recepteursExtension(fqcn).any { recepteur ->
                index.extensionsSur(recepteur).any { it.nom == nom }
            }
        ) {
            return Verdict.RESOLU
        }
        // Les extensions GÉNÉRIQUES (`<T> T.…`) atteignent TOUT récepteur —
        // leur présence MUET la preuve (fiche 026 : let/also/apply/run…).
        if (index.topParNom(nom).any { it.recepteur == null && it.genre == jo.codeide.lsp.kotlin.index.GenreAppelable.FONCTION }) {
            return Verdict.MUET
        }
        // Les extensions de `kotlin.Any` atteignent tout récepteur.
        if (index.extensionsSur("kotlin.Any").any { it.nom == nom }) return Verdict.MUET
        return Verdict.SIGNALABLE
    }

    /**
     * Les RÉCEPTEURS d'extension à consulter pour un FQCN (vague 2d —
     * fiche 033) : le récepteur EXACT, puis chaque MAILLON de sa chaîne
     * de supertypes lue des entrées (nom JVM + miroir KOTLIN des types
     * mappés) ; liste vide si la chaîne ne se lit pas (le jeu complet a
     * déjà fait foi — appelée après lui, jamais seule).
     */
    private fun recepteursExtension(fqcn: String): List<String> {
        val recepteurs = LinkedHashSet<String>()
        recepteurs.add(fqcn)
        for (maillon in vueJvm?.chainesDe(MAPPES_JVM[fqcn] ?: fqcn).orEmpty()) {
            recepteurs.add(maillon)
            MAPPES_JVM_INVERSE[maillon]?.let { recepteurs.add(it) }
        }
        return recepteurs.toList()
    }

    /**
     * Vérifie un SÉLECTEUR sur un récepteur typé du PROJET (vague 2a —
     * ADR 0223 ; vague 2c — la CLÔTURE d'héritage : un récepteur à clause
     * écrite dont la chaîne SE FERME porte un jeu de membres complet —
     * déclarés + hérités de la chaîne mixte projet/classpath — l'absence
     * s'y PROUVE, fiches 044/028 ; une chaîne OUVERTE reste muette, D3).
     *
     * @param recepteur le type du projet du récepteur ;
     * @param nom le nom du sélecteur ;
     * @return RESOLU (membre/extension connu), MUET (univers ouvert) ou
     * SIGNALABLE (ABSENCE PROUVÉE — émission).
     */
    internal fun verifierSelecteurProjet(recepteur: TypeProjet, nom: String): Verdict {
        // Scan partiel : le modèle du projet n'est pas complet.
        if (etatScan == EtatScan.PARTIEL) return Verdict.MUET

        // Une extension à récepteur NON résolu (alias, qualifié) pourrait
        // viser CE récepteur : l'univers est ouvert.
        if (modele.extensionsNonResolues) return Verdict.MUET

        // Le jeu de membres : DÉCLARÉS seuls sans clause écrite (le socle
        // 2a — absence-fiable), CLÔTURE complète avec une clause écrite
        // dont la chaîne se ferme (vague 2c) — MUET si la chaîne est
        // ouverte (enum, supertype introuvable, maillon illisible).
        val cloture: ClotureHeritage? =
            if (recepteur.absenceFiable) {
                ClotureHeritage(
                    recepteur.membres.associate { m -> m.nom to MembreHerite(m.nom, genreHerite(m.genre), m.typeRetourNomme, null, null, recepteur.fqcn) },
                )
            } else {
                clotureDe(recepteur)
            }
        if (cloture == null) return Verdict.MUET
        if (cloture.contient(nom)) return Verdict.RESOLU

        // Les implicites de Any/Object (le socle commun de tout récepteur).
        if (nom in MEMBRES_IMPLICITS) return Verdict.RESOLU

        // Les membres du COMPAGNON du type (vague 2d — fiche 024 :
        // `Boite.creer()` appelle le membre du compagnon par le NOM de la
        // classe) — portée STATIQUE du conteneur, énumérable : l'absence
        // d'un autre nom reste PROUVABLE (garde D3 de la fiche).
        if (recepteur.membresCompagnon.any { it.nom == nom }) return Verdict.RESOLU

        // Les extensions du PROJET sur ce récepteur (résolues par FQCN à
        // l'assemblage du modèle — deuxième passe).
        if (modele.extensionsSur(recepteur.fqcn).any { it.nom == nom }) return Verdict.RESOLU

        // Les extensions du CLASSPATH sur ce FQCN (l'index porte).
        if (index != null) {
            if (index.extensionsSur(recepteur.fqcn).any { it.nom == nom }) return Verdict.RESOLU

            // Les extensions GÉNÉRIQUES (`<T> T.…`) sont indexées SANS
            // récepteur de classe : elles atteignent TOUT récepteur — leur
            // présence MUET la preuve (fiche 026 : let/also/apply/run…).
            if (index.topParNom(nom).any { it.recepteur == null && it.genre == jo.codeide.lsp.kotlin.index.GenreAppelable.FONCTION }) {
                return Verdict.MUET
            }

            // Les extensions de `kotlin.Any` atteignent tout récepteur.
            if (index.extensionsSur("kotlin.Any").any { it.nom == nom }) return Verdict.MUET
        }

        // L'index ne porte pas (guichet muet) : les extensions classpath
        // sont inconnues — l'absence n'est pas prouvable.
        if (index == null) return Verdict.MUET

        return Verdict.SIGNALABLE
    }

    /**
     * Le type du projet d'un NOM lié par les portées locales (paramètre,
     * locale, membre) : la liaison donne le nom ÉCRIT du type, résolu en
     * type projet (classpath → `null`, récepteur non typable à cet étage).
     *
     * @param typeNomme le nom ÉCRIT du type (`Boite`, `String`) ;
     * @return le type du projet ; `null` si le nom n'en désigne aucun.
     */
    internal fun typeProjetDuNomEcrit(typeNomme: String?): TypeProjet? {
        if (typeNomme == null) return null
        return modele.typeParNomSimple(typeNomme, paquet)
    }

    /**
     * Les propositions de COMPLÉTION MEMBRE d'un récepteur TYPÉ (fiche
     * 022 — `copy` proposé après `p.` ; fiche 046 — récepteurs de
     * bibliothèque, smart casts et attentes de lambda en 2b ; vague 2c —
     * fiches 044/040 : les membres HÉRITÉS de la clôture s'offrent aussi,
     * `this.` dans une Activity propose `layoutInflater`) : membres
     * déclarés et synthétisés (ou jeu JVM complet — chaîne de supertypes,
     * propriétés dérivées) + implicites + extensions connues, en ordre de
     * rencontre.
     *
     * @param recepteur le type du récepteur (projet ou classpath) ;
     * @return les libellés proposés (noms nus) ; liste vide si rien.
     */
    internal fun propositionsMembres(recepteur: TypeValeur): List<String> {
        val propositions = LinkedHashSet<String>()
        if (recepteur is TypeValeur.Projet) {
            // La clôture d'abord : les DÉCLARÉS puis les HÉRITÉS de la
            // chaîne mixte (vague 2c) — une chaîne ouverte retombe sur les
            // déclarés seuls (honnête, jamais faux).
            val cloture = if (recepteur.type.absenceFiable) null else clotureDe(recepteur.type)
            if (cloture != null) {
                propositions.addAll(cloture.membres.keys)
            } else {
                for (membre in recepteur.type.membres) {
                    propositions.add(membre.nom)
                }
            }
            if (recepteur.type.genre == GenreTypeProjet.ENUM) {
                propositions.addAll(recepteur.type.entreesEnum)
            }
            propositions.addAll(MEMBRES_IMPLICITS)
            // Les membres du COMPAGNON (vague 2d — fiche 024 : `Boite.`
            // propose `creer` — la portée STATIQUE du conteneur).
            for (membre in recepteur.type.membresCompagnon) {
                propositions.add(membre.nom)
            }
            for (extension in modele.extensionsSur(recepteur.type.fqcn)) {
                propositions.add(extension.nom)
            }
            if (index != null) {
                for (extension in index.extensionsSur(recepteur.type.fqcn)) {
                    propositions.add(extension.nom)
                }
            }
            return propositions.toList()
        }
        val fqcn = (recepteur as TypeValeur.Jvm).fqcn
        // Le jeu JVM complet de la chaîne (déclarés + dérivés + implicites)
        // — une chaîne illisible rend la complétion VIDE d'entrées (les
        // EXTENSIONS de l'index se proposent encore, D3) ; les IMBRIQUÉES
        // `$` s'offrent aussi (vague 2c).
        vueJvm?.jeuComplet(MAPPES_JVM[fqcn] ?: fqcn)?.let { propositions.addAll(it.noms) }
        vueJvm?.let { propositions.addAll(it.imbriquesDe(MAPPES_JVM[fqcn] ?: fqcn)) }
        propositions.addAll(MEMBRES_IMPLICITS)
        // Les membres du COMPAGNON du classpath (vague 2d — fiche 024 :
        // `Regex.` propose `escape`) — l'illisible ne propose RIEN (D3).
        vueJvm?.compagnonDe(fqcn)?.takeIf { !it.illisible }?.let { propositions.addAll(it.noms) }
        if (index != null) {
            for (extension in index.extensionsSur(fqcn)) {
                propositions.add(extension.nom)
            }
        }
        return propositions.toList()
    }

    /**
     * Résout un CHEMIN DE SEGMENTS en classe (vague 2c — fiches 044 E4/
     * E4bis, 028/029 : les types QUALIFIÉS et les FQN d'expression) :
     * (a) le chemin complet comme FQCN ABSOLU du projet
     * (`com.ex.app.Aquarium.Bac` importé tel quel) ; (b) le premier
     * segment par le MODÈLE (package courant, nom unique) puis les
     * suivants comme IMBRIQUÉES du modèle (`Aquarium.Bac` — FQCN pointé) ;
     * (c) le premier segment par IMPORT exact puis les suivants comme
     * IMBRIQUÉES `$` du classpath (`Conteneur.Fabrique` →
     * `exo.conteneur.Conteneur$Fabrique`) ; (d) le chemin complet comme
     * classe CLASSPATH (`android.os.Bundle` — les segments amont y sont
     * des PACKAGES, jamais vérifiés).
     *
     * @param segments les segments écrites (`[android, os, Bundle]`) ;
     * @return le FQCN RÉSOLU (forme interne : pointée pour le projet, `$`
     * pour l'imbriquée classpath) ; `null` si le chemin ne résout pas.
     */
    internal fun cheminClasse(segments: List<String>): String? {
        if (segments.isEmpty()) return null

        // (a) Le chemin ABSOLU du projet.
        val absolu = segments.joinToString(".")
        modele.type(absolu)?.let { return it.fqcn }

        // (b) Le premier segment dans le MODÈLE, les suivants en imbriquées
        // du modèle (FQCN pointé — `Aquarium.Bac.Couloir` imbriquée de
        // niveau 3, fiche 029 : l'extraction RÉCURSIVE bornée s'y lit).
        modele.typeParNomSimple(segments.first(), paquet)?.let { racine ->
            var actuel = racine
            for (i in 1 until segments.size) {
                actuel = modele.imbriquesDu(actuel.fqcn)
                    .firstOrNull { it.nomSimple == segments[i] } ?: return null
            }
            return actuel.fqcn
        }

        // (c) Le premier segment par IMPORT exact, les suivants en
        // IMBRIQUÉES du modèle (projet) ou `$` du classpath.
        for (directive in imports) {
            if (directive.estJoker()) continue
            val chemin = directive.nomQualifie() ?: continue
            if (directive.nomEffectifImport() != segments.first()) continue

            // Racine PROJET : les marches se suivent dans le modèle.
            modele.type(chemin)?.let { racine ->
                var actuel = racine
                for (i in 1 until segments.size) {
                    actuel = modele.imbriquesDu(actuel.fqcn)
                        .firstOrNull { it.nomSimple == segments[i] } ?: return null
                }
                return actuel.fqcn
            }

            // Racine CLASSPATH : la tête doit exister, chaque marche `$`
            // se vérifie (vue JVM — une classe inconnue rend le chemin
            // non résolu).
            val vue = vueJvm ?: return null
            var parent = mapeJvm(chemin)
            if (!vue.possedeClasse(parent)) return null
            for (i in 1 until segments.size) {
                if (!vue.possedeImbrique(parent, segments[i])) return null
                parent = "$parent\$${segments[i]}"
            }
            return parent
        }

        // (d) Le chemin complet comme classe CLASSPATH (les amont sont des
        // packages — `android.os.Bundle`, fiche 044 E4).
        val vueJvmLocale = vueJvm ?: return null
        if (vueJvmLocale.possedeClasse(absolu)) return absolu

        // (d-bis) Le chemin complet comme TYPEALIAS PUBLIC du classpath
        // (vague 2d — fiche 037 : `kotlinx.datetime.DayOfWeek` — AUCUN
        // miroir bytecode, la facade `*Kt` est sa seule existence) : le
        // dernier segment reste MUET en type qualifié.
        if (index?.estTypeAliasConnu(absolu) == true) return absolu
        return null
    }

    /**
     * Le type d'un NOM DE TYPE en position d'EXPRESSION (vague 2c — le
     * typage des références de classe : `Peinture` importé type
     * `exo.peint.Peinture`, `String` type `kotlin.String`) : le modèle
     * d'abord, puis les imports EXACTS, puis le catalogue KP8.
     *
     * @param nom le nom écrit (`Peinture`, `String`) ;
     * @return le type ; `null` si le nom n'y désigne aucune classe.
     */
    internal fun typeImporteOuDefaut(nom: String): TypeValeur? {
        modele.typeParNomSimple(nom, paquet)?.let { return TypeValeur.Projet(it) }
        for (directive in imports) {
            if (directive.estJoker()) continue
            if (directive.nomEffectifImport() != nom) continue
            val chemin = directive.nomQualifie() ?: continue
            modele.type(chemin)?.let { return TypeValeur.Projet(it) }
            return TypeValeur.Jvm(chemin)
        }
        NOMS_FQCN_DEFAUT[nom]?.let { return TypeValeur.Jvm(it) }
        return null
    }

    /** Le genre de clôture d'un genre de membre projet. */
    private fun genreHerite(genre: GenreMembreProjet): GenreMembreHerite = when (genre) {
        GenreMembreProjet.FONCTION -> GenreMembreHerite.FONCTION
        GenreMembreProjet.PROPRIETE -> GenreMembreHerite.PROPRIETE
        GenreMembreProjet.TYPE_IMBRIQUE -> GenreMembreHerite.TYPE_IMBRIQUE
    }

    /** Le package importé est-il un package du PROJET (énumérable) ? */
    private fun estPackageProjet(nomPackage: String): Boolean {
        return nomPackage in modele.paquetsConnus()
    }

    internal companion object {
        /**
         * Les paquets IMPORTÉS PAR DÉFAUT (spécification Kotlin — les
         * déclarations y sont en portée sans import écrit).
         */
        internal val PAQUETS_DEFAUT: Set<String> = setOf(
            "kotlin",
            "kotlin.annotation",
            "kotlin.collections",
            "kotlin.comparisons",
            "kotlin.io",
            "kotlin.ranges",
            "kotlin.sequences",
            "kotlin.text",
            "kotlin.jvm",
            "java.lang",
        )

        /**
         * Le catalogue KP8 : les TYPES bien connus des paquets par défaut —
         * conservateur par construction (un type absent du catalogue et du
         * classpath est SIGNALÉ : la garde KP8 de la fiche 046 l'exige).
         * Les APPELABLES (`println`, `emptyList`…) n'y vivent PAS : ils
         * relèvent de l'index du classpath réel (voie 6, ADR 0203).
         */
        internal val CATALOGUE_TYPES_DEFAUT: Set<String> = setOf(
            // kotlin — basiques et exceptions.
            "Any", "Nothing", "Unit", "Int", "Long", "Short", "Byte", "Double", "Float",
            "Char", "Boolean", "String", "Array", "Number", "CharSequence", "Enum",
            "Comparable", "Iterable", "Throwable", "Exception", "Error", "RuntimeException",
            "IllegalArgumentException", "IllegalStateException", "UnsupportedOperationException",
            "IndexOutOfBoundsException", "NullPointerException", "ClassCastException",
            "ArithmeticException", "NoSuchElementException", "ConcurrentModificationException",
            "AssertionError", "NumberFormatException",
            // kotlin.collections.
            "Collection", "List", "MutableList", "Set", "MutableSet", "Map", "MutableMap",
            "ArrayList", "HashMap", "HashSet", "LinkedHashMap", "LinkedHashSet",
            "MutableCollection", "MutableIterable", "Map.Entry",
            // kotlin.ranges.
            "IntRange", "LongRange", "CharRange", "IntProgression", "LongProgression",
            "CharProgression",
            // kotlin.text.
            "StringBuilder", "Regex", "MatchResult", "MatchGroup",
            // kotlin.sequences / kotlin.annotation / kotlin.jvm.
            "Sequence", "Annotation", "Deprecated",
            // java.lang (interop).
            "Object", "Thread", "Runnable", "Class", "System", "Math", "Void",
            "CloneNotSupportedException", "InterruptedException", "Cloneable",
        )

        /**
         * Les membres IMPLICITES de Any/Object (le socle commun de tout
         * récepteur — conservateur : les membres protégés compris, un
         * sélecteur les visant n'est JAMAIS prouvable absent).
         */
        internal val MEMBRES_IMPLICITS: Set<String> = setOf(
            "equals", "hashCode", "toString", "getClass", "wait", "notify", "notifyAll",
            "clone", "finalize",
        )

        /**
         * Les genres de nœuds qui DÉCLARENT un nom (la garde documentaire
         * de la fiche 017 — partagée avec la passe sémantique).
         */
        internal val GENRES_DECLARATION: Set<com.intellij.platform.syntax.SyntaxElementType> = setOf(
            org.jetbrains.kotlin.kmp.parser.KtNodeTypes.FUN,
            org.jetbrains.kotlin.kmp.parser.KtNodeTypes.CLASS,
            org.jetbrains.kotlin.kmp.parser.KtNodeTypes.OBJECT_DECLARATION,
            org.jetbrains.kotlin.kmp.parser.KtNodeTypes.PROPERTY,
            org.jetbrains.kotlin.kmp.parser.KtNodeTypes.TYPEALIAS,
            org.jetbrains.kotlin.kmp.parser.KtNodeTypes.ENUM_ENTRY,
            org.jetbrains.kotlin.kmp.parser.KtNodeTypes.VALUE_PARAMETER,
            org.jetbrains.kotlin.kmp.parser.KtNodeTypes.TYPE_PARAMETER,
            org.jetbrains.kotlin.kmp.parser.KtNodeTypes.DESTRUCTURING_DECLARATION_ENTRY,
        )

        /**
         * Les FQCN des types bien connus des paquets par défaut (le miroir
         * TYPÉ du catalogue KP8 — vague 2b : la résolution d'un type ÉCRIT
         * en type de valeur ; `kotlin.String` est le nom KOTLIN, les
         * membres se lisent du MAPPÉ JVM [MAPPES_JVM]).
         */
        internal val NOMS_FQCN_DEFAUT: Map<String, String> = mapOf(
            "Any" to "kotlin.Any", "Nothing" to "kotlin.Nothing", "Unit" to "kotlin.Unit",
            "Int" to "kotlin.Int", "Long" to "kotlin.Long", "Short" to "kotlin.Short",
            "Byte" to "kotlin.Byte", "Double" to "kotlin.Double", "Float" to "kotlin.Float",
            "Char" to "kotlin.Char", "Boolean" to "kotlin.Boolean", "String" to "kotlin.String",
            "Array" to "kotlin.Array", "Number" to "kotlin.Number",
            "CharSequence" to "kotlin.CharSequence", "Enum" to "kotlin.Enum",
            "Comparable" to "kotlin.Comparable", "Iterable" to "kotlin.Iterable",
            "Throwable" to "kotlin.Throwable", "Exception" to "kotlin.Exception",
            "Error" to "kotlin.Error", "RuntimeException" to "kotlin.RuntimeException",
            "IllegalArgumentException" to "kotlin.IllegalArgumentException",
            "IllegalStateException" to "kotlin.IllegalStateException",
            "UnsupportedOperationException" to "kotlin.UnsupportedOperationException",
            "IndexOutOfBoundsException" to "kotlin.IndexOutOfBoundsException",
            "NullPointerException" to "kotlin.NullPointerException",
            "ClassCastException" to "kotlin.ClassCastException",
            "ArithmeticException" to "kotlin.ArithmeticException",
            "NoSuchElementException" to "kotlin.NoSuchElementException",
            "ConcurrentModificationException" to "kotlin.ConcurrentModificationException",
            "AssertionError" to "kotlin.AssertionError",
            "NumberFormatException" to "kotlin.NumberFormatException",
            "Collection" to "kotlin.collections.Collection", "List" to "kotlin.collections.List",
            "MutableList" to "kotlin.collections.MutableList", "Set" to "kotlin.collections.Set",
            "MutableSet" to "kotlin.collections.MutableSet", "Map" to "kotlin.collections.Map",
            "MutableMap" to "kotlin.collections.MutableMap",
            "ArrayList" to "kotlin.collections.ArrayList",
            "HashMap" to "kotlin.collections.HashMap", "HashSet" to "kotlin.collections.HashSet",
            "LinkedHashMap" to "kotlin.collections.LinkedHashMap",
            "LinkedHashSet" to "kotlin.collections.LinkedHashSet",
            "MutableCollection" to "kotlin.collections.MutableCollection",
            "MutableIterable" to "kotlin.collections.MutableIterable",
            "StringBuilder" to "kotlin.text.StringBuilder", "Regex" to "kotlin.text.Regex",
            "Sequence" to "kotlin.sequences.Sequence",
            "Object" to "java.lang.Object", "Thread" to "java.lang.Thread",
            "Runnable" to "java.lang.Runnable", "Class" to "java.lang.Class",
            "System" to "java.lang.System", "Math" to "java.lang.Math", "Void" to "java.lang.Void",
        )

        /**
         * Les types KOTLIN MAPPÉS du bytecode JVM (les classes encadrantes
         * — kotlin.String se lit en java.lang.String, etc.) : la vue JVM
         * y consulte les membres DÉCLARÉS.
         */
        internal val MAPPES_JVM: Map<String, String> = mapOf(
            "kotlin.String" to "java.lang.String",
            "kotlin.Char" to "java.lang.Character",
            "kotlin.Any" to "java.lang.Object",
            "kotlin.Int" to "java.lang.Integer",
            "kotlin.Long" to "java.lang.Long",
            "kotlin.Short" to "java.lang.Short",
            "kotlin.Byte" to "java.lang.Byte",
            "kotlin.Double" to "java.lang.Double",
            "kotlin.Float" to "java.lang.Float",
            "kotlin.Boolean" to "java.lang.Boolean",
        )

        /**
         * Le plancher des INTRINSÈQUES RÉPUTÉES (vague 2d — fiche 023) :
         * les constructeurs de tableaux du module par défaut, déclarés
         * dans la RESSOURCE `kotlin/kotlin.kotlin_builtins` (aucune facade
         * `*Kt` ne les porte, le décodeur @Metadata ne les indexe jamais —
         * constaté sur le stdlib 2.2.10 : `arrayOf`/`intArrayOf` absents de
         * l'index, `listOf`/`println` présents). Silence conservateur
         * DOCUMENTÉ, miroir du catalogue KP8 pour les appelables — la
         * garde D3 de la fiche 023 l'exige (un type hors catalogue et hors
         * classpath reste SIGNALÉ, l'inverse n'ouvre RIEN).
         */
        internal val BUILTINS_REPUTES_MUETS: Set<String> = setOf(
            "arrayOf", "arrayOfNulls", "emptyArray",
            "booleanArrayOf", "byteArrayOf", "charArrayOf", "doubleArrayOf",
            "floatArrayOf", "intArrayOf", "longArrayOf", "shortArrayOf",
        )

        /**
         * Les CONVENTIONS DE SOURCES GÉNÉRÉES (vague 2e — fiche 035) : les
         * préfixes/suffixes des classes générées de KSP/Hilt (`Hilt_`),
         * Room (`_Impl`) et DataBinding (`BindingImpl`) — leurs sources
         * vivent dans `build/generated`, EXCLU de la marche du scan
         * (ADR 0212) : leur absence n'est jamais prouvable. La liste est
         * CLOSE et documentée (tout autre nom reste soumis à la preuve).
         */
        private val PREFIXES_GENERES: List<String> = listOf("Hilt_")

        /** Les suffixes des classes générées (Room `_Impl`, DataBinding `BindingImpl`). */
        private val SUFFIXES_GENERES: List<String> = listOf("_Impl", "BindingImpl")

        /** Le nom porte-t-il une CONVENTION DE SOURCE GÉNÉRÉE (fiche 035) ? */
        internal fun estNomGenere(nom: String): Boolean =
            PREFIXES_GENERES.any { nom.startsWith(it) } || SUFFIXES_GENERES.any { nom.endsWith(it) }

        /**
         * Le MIROIR INVERSE des types mappés (vague 2d — `java.lang.String`
         * → `kotlin.String`) : les maillons de chaîne lus du bytecode
         * portent des noms JVM, les clés d'extension de l'index des noms
         * KOTLIN — la consultation des extensions sur un maillon passe par
         * les DEUX.
         */
        internal val MAPPES_JVM_INVERSE: Map<String, String> =
            MAPPES_JVM.entries.associate { (kotlin, jvm) -> jvm to kotlin }

        /**
         * Les types mappés NUMÉRIQUES et booléens : leurs membres KOTLIN
         * (plus, inv, not…) ne vivent PAS dans le bytecode des classes
         * encadrantes — l'absence n'y est JAMAIS prouvable (sous-rapport
         * assumé, vague 2b — jamais un faux positif).
         */
        internal val MAPPES_NON_FIABLES: Set<String> = setOf(
            "kotlin.Int", "kotlin.Long", "kotlin.Short", "kotlin.Byte",
            "kotlin.Double", "kotlin.Float", "kotlin.Char", "kotlin.Boolean",
        )
    }
}

/**
 * Le nom EFFECTIF d'une directive d'import (l'alias s'il est écrit, sinon le
 * dernier segment du chemin) — l'extension conservatrice de la façade,
 * miroir du calcul des imports inutilisés de la session (fiche 049).
 */
private fun KtDirectiveImport.nomEffectif(): String? {
    alias()?.let { return it }
    val chemin = nomQualifie() ?: return null
    val segment = chemin.substringAfterLast('.')
    return segment.ifEmpty { null }
}
