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
 * implicites du DSL y sont inconnus : noms nus muets, ADR 0106).
 */
internal class UniversPreuves(
    internal val arbre: KtFichier,
    internal val paquet: String,
    internal val modele: ModeleProjet,
    internal val index: VueAppelables?,
    internal val etatScan: EtatScan,
    internal val script: Boolean,
) {

    /** Les imports du document (calculés une fois). */
    private val imports: List<KtDirectiveImport> = arbre.imports()

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
     * @param nom le nom nu cherché ;
     * @param nomsDeclares les noms déclarés du document (garde fiche 017) ;
     * @param liaison la liaison nom → type ÉCRIT des portées locales
     * (paramètres, locales, membres — rend `null` sans liaison) ;
     * @return le verdict (RESOLU / MUET / SIGNALABLE).
     */
    internal fun resoudreNomNu(
        nom: String,
        nomsDeclares: Set<String>,
        liaison: MarcheTypee.Liaison,
    ): Verdict {
        // 1. La garde du document : un nom DÉCLARÉ n'importe où n'est jamais
        //    signalé (fiche 017 — déclaration en cours de frappe comprise).
        if (nom in nomsDeclares) return Verdict.RESOLU

        // 2. Les portées locales typées (paramètres, locales, membres) : la
        //    liaison rend le type écrit — sa simple existence résout.
        if (liaison(nom) != null) return Verdict.RESOLU

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
     * Résout un nom de TYPE (position `USER_TYPE` ou appel de constructeur) :
     * le verdict dit si l'absence est prouvable.
     *
     * @param nom le nom simple écrit ;
     * @return RESOLU (projet/import/défaut/alias), MUET (univers ouvert) ou
     * SIGNALABLE (type introuvable partout — garde KP8 de la fiche 046).
     */
    internal fun resoudreNomType(nom: String): Verdict {
        // Le projet (document primé, package courant, nom globalement unique).
        if (modele.typeParNomSimple(nom, paquet) != null) return Verdict.RESOLU

        // Les imports exacts : le nom effectif (alias compris) résout.
        if (imports.any { it.nomEffectif() == nom }) return Verdict.RESOLU

        // Le catalogue KP8 (types des paquets par défaut).
        if (nom in CATALOGUE_TYPES_DEFAUT) return Verdict.RESOLU

        // Les TYPEALIAS du classpath visibles : paquets par défaut et
        // package courant (kotlin.collections.ArrayList et compagnie).
        if (index != null) {
            if (index.aliasTopParNom(nom).any { it.paquet == paquet || it.paquet in PAQUETS_DEFAUT }) {
                return Verdict.RESOLU
            }
        }

        return if (nomsNusProuvables) Verdict.SIGNALABLE else Verdict.MUET
    }

    /**
     * Vérifie un SÉLECTEUR sur un récepteur typé du PROJET (ADR 0223 : la
     * sélection par types complets — le jeu de membres complet décide).
     *
     * Le jeu complet d'un type sans héritage écrit : les membres DÉCLARÉS
     * (paramètres `val`/`var` du constructeur compris), les SYNTHÉTISÉS des
     * data classes (`copy`, `componentN`), les IMPLICITES de Any/Object et
     * les EXTENSIONS connues (projet par récepteur écrit, classpath par
     * FQCN — plus les extensions génériques `<T> T.…` et celles de `Any`,
     * qui atteignent tout récepteur : leur présence dans l'index MUET la
     * preuve, fiche 026).
     *
     * @param recepteur le type du projet du récepteur ;
     * @param nom le nom du sélecteur ;
     * @return RESOLU (membre/extension connu), MUET (univers ouvert —
     * héritage écrit, enum, extensions non résolues) ou SIGNALABLE
     * (ABSENCE PROUVÉE — émission).
     */
    internal fun verifierSelecteur(recepteur: TypeProjet, nom: String): Verdict {
        // Héritage écrit ou enum : l'univers des membres est ouvert (D3).
        if (!recepteur.absenceFiable) return Verdict.MUET

        // Scan partiel : le modèle du projet n'est pas complet.
        if (etatScan == EtatScan.PARTIEL) return Verdict.MUET

        // Une extension à récepteur NON résolu (alias, qualifié) pourrait
        // viser CE récepteur : l'univers est ouvert.
        if (modele.extensionsNonResolues) return Verdict.MUET

        // Les membres déclarés et synthétisés.
        if (recepteur.membre(nom) != null) return Verdict.RESOLU

        // Les implicites de Any/Object (le socle commun de tout récepteur).
        if (nom in MEMBRES_IMPLICITS) return Verdict.RESOLU

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
     * Les propositions de COMPLÉTION MEMBRE d'un récepteur typé du projet
     * (germe de l'étage 2 requis par la fiche 022 : `copy` proposé après
     * `p.`) : membres déclarés et synthétisés + implicites + extensions du
     * projet sur le récepteur, en ordre de déclaration.
     *
     * @param recepteur le type du projet du récepteur ;
     * @return les libellés proposés (noms nus) ; liste vide si rien.
     */
    internal fun propositionsMembres(recepteur: TypeProjet): List<String> {
        val propositions = LinkedHashSet<String>()
        for (membre in recepteur.membres) {
            propositions.add(membre.nom)
        }
        if (recepteur.genre == GenreTypeProjet.ENUM) {
            propositions.addAll(recepteur.entreesEnum)
        }
        propositions.addAll(MEMBRES_IMPLICITS)
        for (extension in modele.extensionsSur(recepteur.fqcn)) {
            propositions.add(extension.nom)
        }
        if (index != null) {
            for (extension in index.extensionsSur(recepteur.fqcn)) {
                propositions.add(extension.nom)
            }
        }
        return propositions.toList()
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
