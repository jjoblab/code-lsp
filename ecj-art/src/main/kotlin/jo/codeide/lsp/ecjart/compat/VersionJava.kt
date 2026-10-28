package jo.codeide.lsp.ecjart.compat

/**
 * Shim ART de `java.lang.Runtime.Version` (JEP 322) — type absent d'ART,
 * référencé par le compilateur ecj 3.40.0 dans `Parser` (diagnostic
 * « version trop récente » : `Runtime.Version.parse(a).compareTo(
 * Runtime.Version.parse(b))`).
 *
 * Le patcheur remappe toutes les références de `java/lang/Runtime$Version`
 * vers ce type (directive `remap-classe` de la liste de shims versionnée) :
 * les appels amont `parse(String)` et `compareTo(Version)` retrouvent ici des
 * membres de MÊMES NOMS et descripteurs remappés — la résolution à l'exécution
 * est donc exacte, sans adaptation supplémentaire des sites d'appel.
 *
 * Sémantique implémentée depuis la spécification publique (JEP 322 + javadoc
 * JDK de `Runtime.Version`), calée sur les comportements constatés de l'oracle
 * JVM (sondes du 28/10/2026 — voir `VersionJavaTest`, qui rejoue ces cas en
 * différentiel contre la vraie `Runtime.Version`) :
 *  - grammaire STRICTE : `$VNUM(-$PRE)?(\+$BUILD)?(-$OPT)?` et forme
 *    `$VNUM+-OPT` — zéros initiaux interdits, composante FINALE non nulle,
 *    `$PRE` = identifiant ASCII alphanumérique unique, `$BUILD` sans zéro
 *    initial sauf « 0 », `$OPT` = `[-a-zA-Z0-9.]+` non vide ;
 *  - ordre de comparaison par précédence : numéros de version (point à point,
 *    suite courte complétée par zéros — équivalent à « élément manquant
 *    inférieur » pour toute entrée valide), puis pré-version (absente >
 *    présente ; identifiants numériques comparés en nombre, numériques
 *    inférieurs aux non numériques, sinon lexicographique), puis numéro de
 *    build (absent < présent, sinon numérique), puis information optionnelle
 *    (absente < présente, sinon lexicographique).
 *
 * @param vnum composantes du numéro de version (au moins une, aucune
 *   composante finale nulle — garanti par [parse]).
 * @param pre identifiant de pré-version (`ea`, `internal`…) ou `null`.
 * @param build numéro de build ou `null`.
 * @param opt information optionnelle de build ou `null` (comparée, jamais
 *   déterminante seule).
 * @param source chaîne d'origine, restituée par [toString].
 */
public class VersionJava private constructor(
    private val vnum: List<Int>,
    private val pre: String?,
    private val build: Int?,
    private val opt: String?,
    private val source: String,
) : Comparable<VersionJava> {

    /**
     * Compare cette version à [autre] selon l'ordre du JEP 322 (voir KDoc de
     * la classe). Le signe du résultat suffit aux usages d'ecj (Parser).
     *
     * @param autre version à comparer (non nulle).
     * @return négatif si cette version est antérieure, positif si postérieure,
     *   zéro si équivalente pour l'ordre défini.
     */
    override fun compareTo(autre: VersionJava): Int {
        // 1. Numéros de version, point à point, suites complétées par zéros.
        val taille = maxOf(vnum.size, autre.vnum.size)
        for (k in 0 until taille) {
            val a = vnum.getOrNull(k) ?: 0
            val b = autre.vnum.getOrNull(k) ?: 0
            if (a != b) return a.compareTo(b)
        }
        // 2. Pré-version : absente > présente ; numérique < non numérique.
        val cmpPre = comparerPre(pre, autre.pre)
        if (cmpPre != 0) return cmpPre
        // 3. Build : absent < présent ; comparaison numérique.
        val aB = build
        val bB = autre.build
        if (aB != null || bB != null) {
            if (aB == null) return -1
            if (bB == null) return 1
            if (aB != bB) return aB.compareTo(bB)
        }
        // 4. Optionnel : absent < présent ; comparaison lexicographique.
        val aO = opt
        val bO = autre.opt
        if (aO != null || bO != null) {
            if (aO == null) return -1
            if (bO == null) return 1
            if (aO != bO) return aO.compareTo(bO)
        }
        return 0
    }

    /** Représentation textuelle : la chaîne d'origine (comportement JDK). */
    override fun toString(): String = source

    /**
     * Égalité structurelle (vnum, pré-version, build, optionnel).
     * `java.lang.Runtime.Version` n'utilise pas l'information optionnelle
     * dans son égalité — divergence documentée, sans effet pour ecj
     * (n'appelle jamais `equals` sur ce type).
     */
    override fun equals(autre: Any?): Boolean =
        autre is VersionJava &&
            autre.vnum == vnum &&
            autre.pre == pre &&
            autre.build == build &&
            autre.opt == opt

    override fun hashCode(): Int =
        (vnum.hashCode() * 31 + (pre?.hashCode() ?: 0)) * 31 +
            ((build ?: 0) * 31 + (opt?.hashCode() ?: 0))

    public companion object {

        /**
         * Analyse une chaîne de version selon la grammaire stricte du JEP 322.
         *
         * Les chaînes alimentées par ecj (niveaux de conformité de
         * `CompilerOptions` : « 1.1 » … « 1.8 », « 9 » … « 23 ») sont toutes
         * des formes `$VNUM` simples — couvertes sans réserve.
         *
         * @param chaine chaîne à analyser (non nulle).
         * @return la version analysée.
         * @throws IllegalArgumentException si la chaîne viole la grammaire
         *   (zéro initial, composante finale nulle, pré-version à points ou
         *   tirets, build à zéros initiaux, information optionnelle vide,
         *   reste non consommé…).
         */
        @JvmStatic
        public fun parse(chaine: String): VersionJava {
            if (chaine.isEmpty()) throw IllegalArgumentException("chaîne de version vide")
            var i = 0
            val n = chaine.length

            fun lireChiffres(): String {
                val debut = i
                while (i < n && chaine[i] in '0'..'9') i++
                return chaine.substring(debut, i)
            }

            fun estAlphanumeriqueAscii(c: Char): Boolean =
                c in '0'..'9' || c in 'a'..'z' || c in 'A'..'Z'

            fun estCaractereOpt(c: Char): Boolean =
                estAlphanumeriqueAscii(c) || c == '-' || c == '.'

            fun refus(): IllegalArgumentException =
                IllegalArgumentException("chaîne de version invalide : « $chaine »")

            // --- $VNUM : composantes séparées par des points ---
            val composantes = mutableListOf<Int>()
            while (true) {
                val brute = lireChiffres()
                if (brute.isEmpty()) throw refus()
                if (brute.length > 1 && brute[0] == '0') throw refus() // zéro initial
                val valeur = brute.toIntOrNull() ?: throw refus() // composante démesurée
                composantes.add(valeur)
                if (i < n && chaine[i] == '.') {
                    i++
                    continue
                }
                break
            }
            // Composante « 0 » interdite en tête et en queue (JEP 322 :
            // ni « 0 » seul, ni zéro traînant — les zéros intermédiaires restent légaux).
            if (composantes.first() == 0 || composantes.last() == 0) throw refus()

            // --- suffixes : $PRE, $BUILD, $OPT ---
            var pre: String? = null
            var build: Int? = null
            var opt: String? = null

            fun lireOpt() {
                val debut = i
                while (i < n && estCaractereOpt(chaine[i])) i++
                if (i == debut) throw refus() // information optionnelle vide
                opt = chaine.substring(debut, i)
            }

            fun lireBuildPuisOpt() {
                val brute = lireChiffres()
                if (brute.isEmpty()) throw refus() // build non numérique
                if (brute.length > 1 && brute[0] == '0') throw refus() // zéro initial
                build = brute.toIntOrNull() ?: throw refus()
                if (i < n && chaine[i] == '-') {
                    i++
                    lireOpt()
                }
            }

            if (i < n) {
                when (chaine[i]) {
                    '-' -> {
                        i++
                        val debut = i
                        while (i < n && estAlphanumeriqueAscii(chaine[i])) i++
                        if (i == debut) throw refus() // pré-version vide
                        pre = chaine.substring(debut, i)
                        if (i < n) {
                            when (chaine[i]) {
                                '+' -> { i++; lireBuildPuisOpt() }
                                '-' -> { i++; lireOpt() }
                                else -> throw refus() // pré-version suivie d'un caractère interdit
                            }
                        }
                    }
                    '+' -> {
                        i++
                        if (i < n && chaine[i] == '-') {
                            i++
                            lireOpt() // forme « $VNUM+-OPT » (sans build)
                        } else {
                            lireBuildPuisOpt()
                        }
                    }
                    else -> throw refus() // caractère inattendu après $VNUM
                }
            }
            if (i != n) throw refus() // reste non consommé

            return VersionJava(composantes, pre, build, opt, chaine)
        }

        /**
         * Compare deux pré-versions selon le JEP 322 : absente > présente ;
         * deux identifiants numériques se comparent en nombre ; un numérique
         * est inférieur à un non numérique ; sinon comparaison lexicographique.
         */
        private fun comparerPre(a: String?, b: String?): Int {
            if (a == b) return 0
            if (a == null) return 1
            if (b == null) return -1
            val aNumerique = a.all { it in '0'..'9' }
            val bNumerique = b.all { it in '0'..'9' }
            return when {
                aNumerique && bNumerique -> a.toLong().compareTo(b.toLong())
                aNumerique -> -1
                bNumerique -> 1
                else -> a.compareTo(b)
            }
        }
    }
}
