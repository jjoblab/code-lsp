// CodeIDE — vue JVM des MEMBRES du classpath (module :kotlin, étage 2 vague
// 2b — fiche 046 : sélecteurs sur récepteurs CLASSPATH typés, ADR 0223 ;
// vague 2c — fiches 044/028/032/040 : les DESCRIPTEURS sont lus (typage et
// survol des membres hérités), la dérivation ADR 0219 devient CONSCIENTE
// des descripteurs (un getter à paramètre ne synthétise RIEN), les classes
// IMBRIQUÉES `$` se consultent et la chaîne porte ses PROPRIÉTAIRES).
// Code NEUF jjoblab (clean-room — lecteur maison du format class-file JLS
// §4, miroir de la discipline de LecteurClassBrut R5 SANS la reprendre :
// celui-ci lit les CHAMPS et MÉTHODES déclarés AVEC leurs descripteurs,
// l'arbitrage Q-K1 « lecteur maison vs :index » est tranché À MINIMA pour
// la vague — aucun lien inter-modules, aucune dépendance neuve, le budget
// dex E-K8 est épargné.
package jo.codeide.lsp.kotlin.resolve

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.util.zip.ZipFile
import jo.codeide.lsp.kotlin.api.EntreeClasspathKotlin
import jo.codeide.lsp.kotlin.api.KindEntreeKotlin

/**
 * La vue JVM des membres du CLASSPATH : ce que les preuves ADR 0223
 * consultent quand le récepteur est un type DE BIBLIOTHÈQUE
 * (`android.widget.TextView`, `kotlin.String`…) — le jeu de membres COMPLET
 * (déclarés + chaîne de supertypes + propriétés dérivées des accesseurs +
 * implicites de Any/Object) d'un FQCN, lu des ENTRÉES posées.
 *
 * ## La discipline « pilotée par les entrées » (miroir de l'index, 1d)
 *
 * La vue ne lit QUE les jars/aars des entrées du classpath posé : un type
 * absent des entrées (le `java.lang.String` d'un android.jar de stub, le
 * `kotlin.String` mappé d'un stdlib seul) rend un jeu `null` — le récepteur
 * reste TYPABLE mais jamais ABSENCE-FIABLE (sélecteur muet, complétion
 * membre vide d'entrées : les EXTENSIONS de l'index se proposent encore).
 * La vue de l'HÔTE n'est jamais consultée (D6 : le moteur est
 * autonome) ; la ressource du RUNTIME n'est jamais lue (déterminisme
 * bac à sable = CI = appareil).
 *
 * ## Lecture paresseuse et tolérante
 *
 * Tout est PARESSEUX (rien n'est lu à la construction — le snapshot se
 * publie à vil coût) et TOUT est tolérant (D3) : une entrée absente, un
 * zip corrompu, un class-file hostile rendent `null`/vide, jamais
 * d'exception vers l'appelant. Les lectures sont SYNCHRONISÉES sur la vue
 * (les requêtes de session sont parallèles — ADR 0117).
 *
 * @property entrees les entrées du classpath couvert (ordre de préséance).
 */
internal class VueJvmMembres(private val entrees: List<EntreeClasspathKotlin>) {

    /** Les zip ouverts par entrée (paresseux — l'ordre des entrées fait foi). */
    private val zips = arrayOfNulls<ZipFile>(entrees.size)

    /** Le cache des membres DÉCLARÉS par FQCN (`null` = classe introuvable). */
    private val cacheDeclares = HashMap<String, MembresDeclaresJvm?>()

    /** Le cache des jeux COMPLETS par FQCN (`null` = chaîne non lisible). */
    private val cacheJeux = HashMap<String, JeuMembresJvm?>()

    /** Le cache des infos de chaîne par (FQCN, nom) — vague 2c. */
    private val cacheMembresChaine = HashMap<Pair<String, String>, MembreChaineJvm?>()

    /** Le cache des imbriquées par FQCN parent (vague 2c — fiche 028/029). */
    private val cacheImbriques = HashMap<String, List<String>>()

    /** Le cache des COMPAGNONS par FQCN (vague 2d — fiche 024). */
    private val cacheCompagnons = HashMap<String, CompagnonJvm?>()

    /** Le cache des CHAÎNES de supertypes par FQCN (vague 2d — fiche 033). */
    private val cacheChaines = HashMap<String, List<String>?>()

    /** Le cache des jeux DÉTAILLÉS par FQCN (étage 3 vague 3a — fiche 045). */
    private val cacheJeuxDetailles = HashMap<String, List<MembreDetailleJvm>?>()

    /**
     * L'ANNUAIRE paresseux des types des entrées (étage 3 vague 3a —
     * fiche 045 : complétion de nom avec auto-import et complétion
     * d'import) : `null` tant qu'aucune requête ne l'exige, puis UNE
     * marche des entrées construit les trois tables — noms SIMPLES triés
     * (recherche par préfixe), noms simples → FQCN (les collisions sont
     * LÉGALES : plusieurs paquets portent un `Builder`), paquets CONNUS
     * (tout préfixe de paquet d'une classe, intermédiaires compris).
     */
    private var annuaire: AnnuaireTypesJvm? = null

    /**
     * L'annuaire des types — construit à la PREMIÈRE requête (une seule
     * marche des entrées : les noms d'entrées du zip central seuls, aucun
     * class-file n'est ouvert — la complétion de nom reste une requête
     * légère), puis servi du champ.
     *
     * @return l'annuaire immuable (jamais `null` après construction).
     */
    private fun annuaireTypes(): AnnuaireTypesJvm {
        annuaire?.let { return it }
        val parNomSimple = HashMap<String, MutableList<String>>()
        val paquets = HashSet<String>()
        for (i in entrees.indices) {
            val zip = zipDe(i) ?: continue
            val entreesZip = try {
                zip.entries()
            } catch (_: Exception) {
                continue
            }
            for (entree in entreesZip) {
                val nom = entree.name
                if (!nom.endsWith(".class")) continue
                if (nom.endsWith("/module-info.class") ||
                    nom.endsWith("/package-info.class")
                ) {
                    continue
                }
                val base = nom.substringAfterLast('/')
                if ('\$' in base) continue
                val fqcn = nom.removeSuffix(".class").replace('/', '.')
                parNomSimple.getOrPut(base.removeSuffix(".class")) { mutableListOf() }.add(fqcn)
                // Tout PRÉFIXE de paquet est un paquet CONNU (les
                // intermédiaires `android` de `android.widget.TextView`
                // n'ont aucune classe propre — ils existent pourtant).
                var paquet = fqcn
                while (true) {
                    paquet = paquet.substringBeforeLast('.', "")
                    if (paquet.isEmpty()) break
                    if (!paquets.add(paquet)) break
                }
            }
        }
        val construit = AnnuaireTypesJvm(
            nomsTries = parNomSimple.keys.sorted(),
            parNomSimple = parNomSimple,
            paquets = paquets,
        )
        annuaire = construit
        return construit
    }

    /**
     * Les FQCN des classes des entrées dont le nom SIMPLE porte ce
     * PRÉFIXE (étage 3 — fiche 045 : « types du classpath avec
     * auto-import ») : marche binaire bornée sur les noms triés, chaque
     * nom rend TOUS ses FQCN (les collisions se proposent toutes —
     * l'hôte arbitre par import).
     *
     * @param prefixe le préfixe sensible à la casse (`TextV`) ;
     * @param limite le nombre maximum de noms simples parcourus ;
     * @return les FQCN distincts, triés (déterminisme) ; liste vide sans
     * classe correspondante ou entrée lisible.
     */
    internal fun fqcnParPrefixe(prefixe: String, limite: Int): List<String> {
        if (prefixe.isEmpty() || limite <= 0) return emptyList()
        synchronized(this) {
            val annuaireTypes = annuaireTypes()
            val trouves = sortedSetOf<String>()
            var indice = annuaireTypes.nomsTries.binarySearch { nom ->
                nom.compareTo(prefixe).takeIf { it != 0 } ?: 1
            }
            if (indice < 0) indice = -indice - 1
            var visites = 0
            while (indice < annuaireTypes.nomsTries.size && visites < limite) {
                val nom = annuaireTypes.nomsTries[indice]
                if (!nom.startsWith(prefixe)) break
                annuaireTypes.parNomSimple[nom]?.let { trouves.addAll(it) }
                indice++
                visites++
            }
            return trouves.toList()
        }
    }

    /**
     * Les SOUS-PAQUETS DIRECTS d'un paquet des entrées (étage 3 — fiche
     * 045 : « complétion import : sous-paquets du paquet tapé ») : les
     * enfants immédiats qui PORTENT des classes ou en portent plus bas
     * (`android` → `widget`, `view`…).
     *
     * @param parent le paquet parent (`android`) ;
     * @return les sous-paquets directs, TRIÉS (déterminisme) ; liste vide
     * si le paquet n'est pas connu.
     */
    internal fun sousPaquetsDirects(parent: String): List<String> {
        if (parent.isEmpty()) return emptyList()
        synchronized(this) {
            val annuaireTypes = annuaireTypes()
            if (parent !in annuaireTypes.paquets) return emptyList()
            val cible = "$parent."
            val enfants = sortedSetOf<String>()
            for (paquet in annuaireTypes.paquets) {
                if (!paquet.startsWith(cible)) continue
                enfants.add(cible + paquet.removePrefix(cible).substringBefore('.'))
            }
            return enfants.toList()
        }
    }

    /**
     * Les FQCN des classes directement posées dans ce PAQUET (étage 3 —
     * complétion d'import : les types directs du paquet tapé, sous les
     * sous-paquets dominants).
     *
     * @param paquet le paquet (`android.widget`) ;
     * @return les FQCN triés ; liste vide si le paquet n'est pas connu.
     */
    internal fun typesDansPaquet(paquet: String): List<String> {
        if (paquet.isEmpty()) return emptyList()
        synchronized(this) {
            val annuaireTypes = annuaireTypes()
            if (paquet !in annuaireTypes.paquets) return emptyList()
            val cible = "$paquet."
            val trouves = sortedSetOf<String>()
            for ((_, fqcns) in annuaireTypes.parNomSimple) {
                for (fqcn in fqcns) {
                    if (fqcn.startsWith(cible) && fqcn.removePrefix(cible).indexOf('.') == -1) {
                        trouves.add(fqcn)
                    }
                }
            }
            return trouves.toList()
        }
    }

    /**
     * Les membres DÉCLARÉS d'un FQCN des entrées (sans héritage).
     *
     * @param fqcn le nom pleinement qualifié (`android.widget.TextView`) ;
     * @return les membres déclarés ; `null` si la classe n'est dans AUCUNE
     * entrée (ou est illisible — la preuve d'absence se tait, D3).
     */
    internal fun membresDeclares(fqcn: String): MembresDeclaresJvm? {
        synchronized(this) {
            if (cacheDeclares.containsKey(fqcn)) return cacheDeclares[fqcn]
            val lus = lireMembres(fqcn)
            cacheDeclares[fqcn] = lus
            return lus
        }
    }

    /**
     * La classe existe-t-elle dans les ENTRÉES (vague 2c — fiche 044 E4 :
     * un type QUALIFIÉ existant `android.os.Bundle` rend ses segments
     * muets ; la résolution des chemins FQN s'appuie sur cette porte).
     *
     * @param fqcn le nom pleinement qualifié ;
     * @return `true` si une entrée porte un class-file lisible à ce nom.
     */
    internal fun possedeClasse(fqcn: String): Boolean = membresDeclares(fqcn) != null

    /**
     * Le jeu de membres COMPLET d'un FQCN : les DÉCLARÉS de toute la chaîne
     * de supertypes (superclasse et interfaces, en profondeur bornée et
     * gardée contre les cycles), les PROPRIÉTÉS DÉRIVÉES des accesseurs
     * Java (`getText` → `text`, ADR 0219) et les IMPLICITES de
     * Any/Object — la matière des preuves ADR 0223 et de la complétion
     * membre.
     *
     * `java.lang.Object` est un TERMINAL CONNU (ses membres publics et
     * protégés sont le socle commun — jamais requis lisible) ; un maillon
     * manquant des entrées rend le jeu `null` (chaîne non prouvable).
     *
     * @param fqcn le nom pleinement qualifié du récepteur ;
     * @return le jeu complet ; `null` si la chaîne n'est pas lisible.
     */
    internal fun jeuComplet(fqcn: String): JeuMembresJvm? {
        synchronized(this) {
            if (cacheJeux.containsKey(fqcn)) return cacheJeux[fqcn]
            val jeu = assemblerJeu(fqcn)
            cacheJeux[fqcn] = jeu
            return jeu
        }
    }

    /**
     * Le jeu de membres DÉTAILLÉ d'un FQCN (étage 3 vague 3a — fiche 045 :
     * « champ et méthode le long de la chaîne de supertypes ») : la MÊME
     * marche que [jeuComplet], mais chaque nom emporte son GENRE (fonction
     * ou propriété) et son TYPE de présentation (le type nu du champ, le
     * type de la propriété dérivée — nom simple, `CharSequence`) — le
     * plus dérivé gagne par nom, miroir de [chercherMembreChaine].
     *
     * @param fqcn le nom pleinement qualifié du récepteur ;
     * @return le jeu détaillé ; `null` si la chaîne n'est pas lisible.
     */
    internal fun jeuDetaille(fqcn: String): List<MembreDetailleJvm>? {
        synchronized(this) {
            if (cacheJeuxDetailles.containsKey(fqcn)) return cacheJeuxDetailles[fqcn]
            val jeu = assemblerJeuDetaille(fqcn)
            cacheJeuxDetailles[fqcn] = jeu
            return jeu
        }
    }

    /**
     * L'information COMPLÈTE d'un membre sur la chaîne d'un FQCN (vague
     * 2c — fiches 040/028 : le SURVOL rend `val layoutInflater:
     * LayoutInflater` AVEC son propriétaire déclarant, le TYPAGE lit le
     * type de retour du descripteur) : le PREMIER maillon de la chaîne
     * (le plus dérivé) qui déclare le nom — membre OU propriété dérivée
     * ADR 0219 — porte la réponse.
     *
     * @param fqcn le FQCN du récepteur ;
     * @param nom le nom du membre cherché ;
     * @return le membre de chaîne (propriétaire + signatures + dérivée) ;
     * `null` si la chaîne ne se lit pas ou qu'aucun maillon ne le porte.
     */
    internal fun membreChaine(fqcn: String, nom: String): MembreChaineJvm? {
        synchronized(this) {
            val cle = Pair(fqcn, nom)
            if (cacheMembresChaine.containsKey(cle)) return cacheMembresChaine[cle]
            val membre = chercherMembreChaine(fqcn, nom)
            cacheMembresChaine[cle] = membre
            return membre
        }
    }

    /**
     * Le TYPE d'un membre de la chaîne (vague 2c — le typage des sélecteurs
     * sur récepteurs de bibliothèque : `layoutInflater.inflate(…)` type le
     * récepteur `LayoutInflater` par le RETOUR du descripteur du getter).
     * Les surcharges au RETOUR divergent rendent `null` (sous-rapport D3 —
     * le sélecteur suivant reste muet, jamais faux).
     *
     * @param fqcn le FQCN du récepteur ;
     * @param nom le nom du membre ;
     * @return le FQCN Kotlin du type (retour de méthode, type de champ,
     * type de la propriété dérivée) ; `null` si absent ou divergent.
     */
    internal fun typeDuMembre(fqcn: String, nom: String): String? {
        val membre = membreChaine(fqcn, nom) ?: return null
        membre.proprieteDerivee?.let { return it.typeFqcn }
        val retours = membre.signatures.mapNotNull { it.retour }.toSet()
        if (retours.size != 1) return null
        return retours.single()
    }

    /**
     * Les noms SIMPLES des classes IMBRIQUÉES d'un FQCN des entrées
     * (vague 2c — fiche 028 : `Peinture.Capitaine.ROND` traverse
     * `Peinture$Capitaine` ; les imbriquées des SUPERTYPES entrent à la
     * clôture — `Resultats` nu hérité de `Base$Resultats`).
     *
     * @param fqcnParent le FQCN du conteneur ;
     * @return les noms simples des imbriquées DIRECTES (sans `$` interne),
     * triés (déterminisme) ; liste vide si aucune.
     */
    internal fun imbriquesDe(fqcnParent: String): List<String> {
        synchronized(this) {
            cacheImbriques[fqcnParent]?.let { return it }
            val prefixe = fqcnParent.replace('.', '/') + "$"
            val trouves = sortedSetOf<String>()
            for (i in entrees.indices) {
                val zip = zipDe(i) ?: continue
                val entreesZip = try {
                    zip.entries()
                } catch (_: Exception) {
                    continue
                }
                for (entree in entreesZip) {
                    val nom = entree.name
                    if (!nom.startsWith(prefixe) || !nom.endsWith(".class")) continue
                    // Le nom simple vit APRÈS LE DERNIER `$` (l'entrée est
                    // déjà préfixée `conteneur$` — les imbriquées profondes
                    // `A$B$C` y rendent leur feuille C, sur-approximation
                    // sûre pour la clôture).
                    val simple = nom.removeSuffix(".class").substringAfterLast('$')
                    if (simple.isEmpty() || '\$' in simple) continue
                    trouves.add(simple)
                }
            }
            val liste = trouves.toList()
            cacheImbriques[fqcnParent] = liste
            return liste
        }
    }

    /**
     * Le conteneur porte-t-il une IMBRIQUÉE de ce nom simple (vague 2c —
     * la preuve de présence du sélecteur `Capitaine` sur `Peinture`) :
     * consultation DIRECTE de l'entrée `conteneur$Nom.class`.
     *
     * @param fqcnParent le FQCN du conteneur ;
     * @param nomSimple le nom simple cherché ;
     * @return `true` si une entrée porte la classe imbriquée.
     */
    internal fun possedeImbrique(fqcnParent: String, nomSimple: String): Boolean {
        if (nomSimple.isEmpty() || '\$' in nomSimple) return false
        synchronized(this) {
            return entreeExiste(fqcnParent.replace('.', '/') + "\$" + nomSimple + ".class")
        }
    }

    /**
     * Le COMPAGNON d'une classe du classpath (vague 2d — fiche 024 : le
     * compilateur Kotlin émet `fqcn$Companion`, les membres du compagnon
     * y vivent — `Regex.escape` est une méthode d'INSTANCE de
     * `kotlin.text.Regex$Companion`, les `const` des champs STATIQUES —
     * le lecteur ne distingue pas, il lit tout).
     *
     * Les trois états (jamais de faux positif, D3) : l'entrée ABSENTE
     * rend `null` (la classe n'a pas de compagnon — l'absence des
     * sélecteurs se prouve sur le seul jeu d'instance) ; l'entrée
     * PRÉSENTE et LISIBLE rend les NOMS (membres + propriétés dérivées
     * — la classe du compagnon est close et énumérable, l'absence y
     * reste prouvable) ; l'entrée PRÉSENTE mais ILLISIBLE rend
     * [CompagnonJvm.illisible] (la preuve se tait).
     *
     * @param fqcn le FQCN de la classe porteuse ;
     * @return le compagnon ; `null` si aucune entrée ne porte
     * `fqcn$Companion.class`.
     */
    internal fun compagnonDe(fqcn: String): CompagnonJvm? {
        synchronized(this) {
            if (cacheCompagnons.containsKey(fqcn)) return cacheCompagnons[fqcn]
            val nomCompanion = fqcn + "\$Companion"
            if (!entreeExiste(nomCompanion.replace('.', '/') + ".class")) {
                cacheCompagnons[fqcn] = null
                return null
            }
            val declares = lireMembres(nomCompanion)
            val compagnon = if (declares == null) {
                CompagnonJvm(emptySet(), illisible = true)
            } else {
                val noms = LinkedHashSet<String>()
                for (membre in declares.membres) noms.add(membre.nom)
                for (derivee in declares.proprietesDerivees) noms.add(derivee.nom)
                CompagnonJvm(noms, illisible = false)
            }
            cacheCompagnons[fqcn] = compagnon
            return compagnon
        }
    }

    /**
     * La chaîne des FQCN de SUPERTYPES d'une classe des entrées (vague 2d
     * — fiche 033 : l'extension `collect` vit sur `Flow`, le récepteur
     * `SharedFlow` l'atteint par son MAILLON DISTANCIÉ) : la fermeture
     * transitive superclass + interfaces, cycle-interrompue et bornée —
     * les TERMINAUX CONNUS (`java.lang.Object`, `kotlin.Any`,
     * `java.lang.Enum`) n'y entrent PAS (leurs membres sont le socle
     * commun, jamais des clés d'extensions).
     *
     * @param fqcn le FQCN de départ ;
     * @return les FQCN des maillons (le récepteur EXCLU) ; `null` si un
     * maillon manque des entrées (chaîne non lisible — miroir de
     * [jeuComplet]).
     */
    internal fun chainesDe(fqcn: String): List<String>? {
        synchronized(this) {
            if (cacheChaines.containsKey(fqcn)) return cacheChaines[fqcn]
            val maillons = LinkedHashSet<String>()
            val visites = HashSet<String>()
            val pile = ArrayDeque<String>()
            pile.addLast(fqcn)
            var profondeur = 0
            var lisible = true
            while (pile.isNotEmpty()) {
                if (++profondeur > PROFONDEUR_MAX) {
                    lisible = false
                    break
                }
                val courant = pile.removeLast()
                if (!visites.add(courant)) continue
                if (courant == "java.lang.Object" || courant == "kotlin.Any" ||
                    courant == "java.lang.Enum"
                ) {
                    continue
                }
                val declares = lireMembres(courant)
                if (declares == null) {
                    lisible = false
                    break
                }
                if (courant != fqcn) maillons.add(courant)
                declares.superFqcn?.let { pile.addLast(it) }
                pile.addAll(declares.interfaces)
            }
            val resultat = if (lisible) maillons.toList() else null
            cacheChaines[fqcn] = resultat
            return resultat
        }
    }

    /**
     * Les FQCN des classes des entrées portant EXACTEMENT ce nom simple
     * (quick-fix d'import `importsCandidats`, fiche 046) : les entrées sont
     * parcourues en ordre de préséance, les classes INTERNES (`$`) et les
     * fichiers spéciaux (`module-info`, `package-info`) sont ÉCARTÉS.
     *
     * @param nomSimple le nom cherché (`TextView`) ;
     * @return les FQCN distincts, TRIÉS (déterminisme) ; liste vide si
     * aucune entrée ne porte ce nom.
     */
    internal fun fqcnParNomSimple(nomSimple: String): List<String> {
        synchronized(this) {
            val trouves = sortedSetOf<String>()
            for (i in entrees.indices) {
                val zip = zipDe(i) ?: continue
                val prefixe = "$nomSimple.class"
                val entreesZip = try {
                    zip.entries()
                } catch (_: Exception) {
                    continue
                }
                for (entree in entreesZip) {
                    val nom = entree.name
                    if (!nom.endsWith(".class")) continue
                    if (nom.endsWith("/module-info.class") ||
                        nom.endsWith("/package-info.class")
                    ) {
                        continue
                    }
                    val base = nom.substringAfterLast('/')
                    if (base != prefixe) continue
                    if ('\$' in base) continue
                    trouves.add(nom.removeSuffix(".class").replace('/', '.'))
                }
            }
            return trouves.toList()
        }
    }

    // ------------------------------------------------------------------
    // Assemblage du jeu complet (la chaîne de supertypes).
    // ------------------------------------------------------------------

    /**
     * Assemble le jeu complet par marche en profondeur bornée de la chaîne
     * des supertypes (cycle-interrompue ; `java.lang.Object` terminal).
     *
     * @param fqcn le FQCN de départ ;
     * @return le jeu ; `null` si un maillon manque des entrées.
     */
    private fun assemblerJeu(fqcn: String): JeuMembresJvm? {
        val noms = LinkedHashSet<String>()
        val visites = HashSet<String>()
        val pile = ArrayDeque<String>()
        pile.addLast(fqcn)
        var profondeur = 0
        while (pile.isNotEmpty()) {
            if (++profondeur > PROFONDEUR_MAX) return null
            val courant = pile.removeLast()
            if (!visites.add(courant)) continue
            if (courant == "java.lang.Object" || courant == "kotlin.Any") {
                noms.addAll(IMPLICITS_ANY)
                continue
            }
            if (courant == "java.lang.Enum") {
                // TERMINAL CONNU (vague 2c — fiche 032) : la superclasse
                // BINAIRE de tout enum Kotlin vit dans le JDK, jamais dans
                // les entrées — ses membres y sont le socle commun des
                // récepteurs d'enum (sur-approximation conservatrice : le
                // silence prime, jamais un faux positif).
                noms.addAll(IMPLICITS_ENUM)
                continue
            }
            val declares = lireMembres(courant) ?: return null
            for (membre in declares.membres) noms.add(membre.nom)
            for (derivee in declares.proprietesDerivees) noms.add(derivee.nom)
            declares.superFqcn?.let { pile.addLast(it) }
            pile.addAll(declares.interfaces)
        }
        noms.addAll(IMPLICITS_ANY)
        return JeuMembresJvm(noms)
    }

    /**
     * Assemble le jeu DÉTAILLÉ par la même marche bornée que
     * [assemblerJeu] (chaîne de supertypes, cycle-interrompue, terminaux
     * connus) — chaque nom emporte genre et type de présentation, le
     * PREMIER maillon déclarant gagne (le plus dérivé).
     *
     * @param fqcn le FQCN de départ ;
     * @return le jeu détaillé ; `null` si un maillon manque des entrées.
     */
    private fun assemblerJeuDetaille(fqcn: String): List<MembreDetailleJvm>? {
        val membres = LinkedHashMap<String, MembreDetailleJvm>()
        fun ajouter(detaille: MembreDetailleJvm) {
            if (detaille.nom !in membres) membres[detaille.nom] = detaille
        }
        val visites = HashSet<String>()
        val pile = ArrayDeque<String>()
        pile.addLast(fqcn)
        var profondeur = 0
        while (pile.isNotEmpty()) {
            if (++profondeur > PROFONDEUR_MAX) return null
            val courant = pile.removeLast()
            if (!visites.add(courant)) continue
            if (courant == "java.lang.Object" || courant == "kotlin.Any") {
                IMPLICITS_ANY.forEach { ajouter(MembreDetailleJvm(it, GenreMembreHerite.FONCTION, null)) }
                continue
            }
            if (courant == "java.lang.Enum") {
                IMPLICITS_ENUM.forEach { ajouter(MembreDetailleJvm(it, GenreMembreHerite.PROPRIETE, null)) }
                continue
            }
            val declares = lireMembres(courant) ?: return null
            for (membre in declares.membres) {
                ajouter(
                    MembreDetailleJvm(
                        nom = membre.nom,
                        genre = if (membre.estChamp) GenreMembreHerite.PROPRIETE else GenreMembreHerite.FONCTION,
                        typeDetail = membre.retour()?.substringAfterLast('.'),
                    ),
                )
            }
            for (derivee in declares.proprietesDerivees) {
                ajouter(
                    MembreDetailleJvm(
                        nom = derivee.nom,
                        genre = GenreMembreHerite.PROPRIETE,
                        typeDetail = derivee.typeFqcn?.substringAfterLast('.'),
                    ),
                )
            }
            declares.superFqcn?.let { pile.addLast(it) }
            pile.addAll(declares.interfaces)
        }
        IMPLICITS_ANY.forEach { ajouter(MembreDetailleJvm(it, GenreMembreHerite.FONCTION, null)) }
        return membres.values.toList()
    }

    /**
     * Cherche un membre PAR NOM sur la chaîne (vague 2c) : le premier
     * maillon qui DÉCLARE le nom (membre brut ou propriété dérivée)
     * — le plus dérivé gagne, miroir de la sémantique de surcharge.
     */
    private fun chercherMembreChaine(fqcn: String, nom: String): MembreChaineJvm? {
        val visites = HashSet<String>()
        val pile = ArrayDeque<String>()
        pile.addLast(fqcn)
        var profondeur = 0
        while (pile.isNotEmpty()) {
            if (++profondeur > PROFONDEUR_MAX) return null
            val courant = pile.removeLast()
            if (!visites.add(courant)) continue
            // Les TERMINAUX CONNUS ne portent RIEN de lisible dans les
            // entrées — la marche CONTINUE sur le reste de la pile (étage
            // 3, fiche 045 : le récepteur TextView porte des INTERFACES
            // dont la superclasse binaire EST Object — l'exploration
            // d'Object AVANT la superclasse View ne doit pas éteindre la
            // recherche d'un membre hérité ; miroir de [assemblerJeu]).
            if (courant == "java.lang.Object" || courant == "kotlin.Any") continue
            if (courant == "java.lang.Enum") continue
            val declares = lireMembres(courant) ?: return null
            val signatures = declares.membres.filter { it.nom == nom }
                .map { SignatureJvm(it.nom, it.estChamp, it.parametres(), it.retour()) }
            val derivee = declares.proprietesDerivees.firstOrNull { it.nom == nom }
            if (signatures.isNotEmpty() || derivee != null) {
                return MembreChaineJvm(courant, signatures, derivee)
            }
            declares.superFqcn?.let { pile.addLast(it) }
            pile.addAll(declares.interfaces)
        }
        return null
    }

    // ------------------------------------------------------------------
    // Lecture des entrées (le lecteur maison des membres).
    // ------------------------------------------------------------------

    /**
     * Lit les membres déclarés d'un FQCN dans la PREMIÈRE entrée qui le
     * porte (ordre de préséance du classpath).
     *
     * @param fqcn le nom pleinement qualifié ;
     * @return les membres déclarés avec la chaîne écrite ; `null` si
     * introuvable ou illisible (D3).
     */
    private fun lireMembres(fqcn: String): MembresDeclaresJvm? {
        val chemin = fqcn.replace('.', '/') + ".class"
        for (i in entrees.indices) {
            val zip = zipDe(i) ?: continue
            val octets = try {
                val entree = zip.getEntry(chemin) ?: continue
                zip.getInputStream(entree).use { it.readBytes() }
            } catch (_: Exception) {
                continue
            }
            return LecteurMembresClasse.lire(octets)
        }
        return null
    }

    /** Une entrée de zip existe-t-elle dans l'une des entrées posées ? */
    private fun entreeExiste(chemin: String): Boolean {
        for (i in entrees.indices) {
            val zip = zipDe(i) ?: continue
            try {
                if (zip.getEntry(chemin) != null) return true
            } catch (_: Exception) {
                continue
            }
        }
        return false
    }

    /**
     * Le zip ouvert d'une entrée (paresseux, CACHÉ — la vue vit tant que le
     * snapshot la sert ; l'abandon du snapshot rend le zip unreachable et le
     * nettoyeur du JDK le referme ; une entrée JAR est lue telle quelle, une
     * AAR est lue dans son `classes.jar` imbriqué).
     *
     * @param index l'ordre de l'entrée dans le classpath ;
     * @return le zip ouvert ; `null` si l'entrée est absente/corrompue.
     */
    private fun zipDe(index: Int): ZipFile? {
        zips[index]?.let { return it }
        val entree = entrees[index]
        if (entree.kind != KindEntreeKotlin.JAR && entree.kind != KindEntreeKotlin.AAR) return null
        val fichier = File(entree.chemin)
        if (!fichier.isFile) return null
        val zip = try {
            if (entree.kind == KindEntreeKotlin.JAR) {
                ZipFile(fichier)
            } else {
                // AAR : les classes vivent dans classes.jar — un zip IMBRIQUÉ
                // est lu en mémoire (miroir minimal du décodage 1d).
                val externe = ZipFile(fichier)
                val octets = try {
                    val entreeInterne = externe.getEntry("classes.jar") ?: run {
                        externe.close()
                        return null
                    }
                    externe.getInputStream(entreeInterne).use { it.readBytes() }
                } finally {
                    externe.close()
                }
                ZipFile(
                    File.createTempFile("vuejvm-aar", ".jar").apply {
                        deleteOnExit()
                        writeBytes(octets)
                    },
                )
            }
        } catch (_: Exception) {
            return null
        }
        zips[index] = zip
        return zip
    }

    /** Le companion des constantes et implicites. */
    private companion object {
        /** La borne de profondeur de la chaîne de supertypes. */
        private const val PROFONDEUR_MAX: Int = 24

        /**
         * Les membres IMPLICITES de `java.lang.Object`/`kotlin.Any` — le
         * socle commun de tout récepteur (miroir de
         * `UniversPreuves.MEMBRES_IMPLICITS`, conservateur : les membres
         * protégés compris).
         */
        private val IMPLICITS_ANY: List<String> = listOf(
            "equals", "hashCode", "toString", "getClass", "wait", "notify",
            "notifyAll", "clone", "finalize",
        )

        /**
         * Les membres IMPLICITES de `java.lang.Enum` (vague 2c — le socle
         * commun des récepteurs d'enum : `name`, `ordinal`, `compareTo`…
         * sur-approximation conservatrice).
         */
        private val IMPLICITS_ENUM: List<String> = IMPLICITS_ANY + listOf(
            "name", "ordinal", "compareTo", "describeConstable", "getDeclaringClass",
        )
    }
}

/**
 * Les membres DÉCLARÉS d'une classe du classpath (sans héritage) —
 * vague 2c : les DESCRIPTEURS sont portés (typage, survol, dérivation
 * consciente des signatures).
 *
 * @property membres les champs et méthodes NON synthétiques NON pont (les
 * constructeurs `<init>`/`<clinit>` exclus) AVEC leurs descripteurs ;
 * @property proprietesDerivees les propriétés dérivées des accesseurs Java
 * (ADR 0219, consciente des descripteurs depuis la vague 2c : un getter à
 * PARAMÈTRE ne synthétise RIEN — `getDrawable(I)` n'est pas `drawable` ;
 * `getText()` → `text` VAL, `setText(X)` → `text` VAR) ;
 * @property superFqcn la superclasse ÉCRITE (`null` = racine) ;
 * @property interfaces les interfaces directes ÉCRITES.
 */
internal class MembresDeclaresJvm(
    internal val membres: List<MembreJvm>,
    internal val proprietesDerivees: List<ProprieteDeriveeJvm>,
    internal val superFqcn: String?,
    internal val interfaces: List<String>,
    /** Les DRAPEAUX D'ACCÈS de la CLASSE (étage 6 — fiche 018 : le genre `public class/interface/enum` de la fiche structurelle, ACC_INTERFACE 0x0200, ACC_ENUM 0x4000, ACC_FINAL 0x0010). */
    internal val accesClasse: Int = 0,
)

/**
 * Un membre JVM lu du class-file (vague 2c — le descripteur compris).
 *
 * @property nom le nom appelé ;
 * @property descripteur le descripteur de champ/méthode (`(I)Ljava/lang/String;`) ;
 * @property estChamp `true` pour un champ, `false` pour une méthode.
 */
internal class MembreJvm(
    internal val nom: String,
    internal val descripteur: String,
    internal val estChamp: Boolean,
    /** Le membre est ABSTAIT au bytecode (ACC_ABSTRACT — vague 2f : les membres à implémenter d'un supertype classpath). */
    internal val estAbstrait: Boolean = false,
    /** Les DRAPEAUX D'ACCÈS du membre (étage 6 — fiche 018 : public/privé/statique/final de la fiche structurelle). */
    internal val acces: Int = 0,
) {
    /** Les types FQCN Kotlin des PARAMÈTRES (`null` si tableau/illisible). */
    internal fun parametres(): List<String>? = DescripteursJvm.parametres(descripteur)

    /**
     * Le type FQCN Kotlin du RETOUR ou du CHAMP (`null` si tableau/
     * illisible) — vague 2d : un descripteur de CHAMP n'a PAS la forme
     * `(params)retour`, c'est le TYPE NU du champ (`Lexo/theme/Typography;`)
     * — le dépouillement s'y adapte (le survol d'un membre d'objet
     * `MaterialTheme.typography` rend son type, fiche 033).
     */
    internal fun retour(): String? =
        if (estChamp) {
            DescripteursJvm.typeNu(descripteur)
        } else {
            DescripteursJvm.retour(descripteur)
        }
}

/**
 * Une propriété DÉRIVÉE d'un accesseur Java (ADR 0219 — vague 2c : le type
 * et le genre VAL/VAR suivent le descripteur).
 *
 * @property nom le nom dérivé (`text`, `isEnabled`) ;
 * @property typeFqcn le FQCN Kotlin du type (retour du getter, paramètre du
 * setter) ;
 * @property estVar `true` si un SETTER (ou le setter seul) la porte —
 * l'affectation nue y résout.
 */
internal class ProprieteDeriveeJvm(
    internal val nom: String,
    internal val typeFqcn: String?,
    internal val estVar: Boolean,
)

/**
 * La signature dépouillée d'un membre JVM (vague 2c — le rendu du survol
 * `fun getString(Int): String`).
 *
 * @property nom le nom appelé ;
 * @property estChamp `true` pour un champ ;
 * @property parametres les types FQCN Kotlin des paramètres (méthodes) ;
 * @property retour le type FQCN Kotlin du retour ou du champ.
 */
internal class SignatureJvm(
    internal val nom: String,
    internal val estChamp: Boolean,
    internal val parametres: List<String>?,
    internal val retour: String?,
)

/**
 * L'information complète d'un membre TROUVÉ sur une chaîne de supertypes
 * (vague 2c — le survol et le typage des membres hérités).
 *
 * @property proprietaire le FQCN du maillon DÉCLARANT (le plus dérivé) ;
 * @property signatures les signatures portées au nom (surcharges) ;
 * @property proprieteDerivee la dérivation ADR 0219 le cas échéant.
 */
internal class MembreChaineJvm(
    internal val proprietaire: String,
    internal val signatures: List<SignatureJvm>,
    internal val proprieteDerivee: ProprieteDeriveeJvm?,
)

/**
 * Le COMPAGNON d'une classe du classpath (vague 2d — fiche 024) : les
 * NOMS appelables du compagnon `fqcn$Companion`, ou le témoin ILLISIBLE
 * qui muet la preuve (D3).
 *
 * @property noms les noms des membres + propriétés dérivées du compagnon ;
 * @property illisible l'entrée compagnon existe mais ne se parse PAS — la
 * preuve d'absence se tait (univers ouvert).
 */
internal class CompagnonJvm(
    internal val noms: Set<String>,
    internal val illisible: Boolean,
)

/**
 * Un membre du jeu DÉTAILLÉ (étage 3 vague 3a — fiche 045) : le nom, son
 * genre et son type de présentation (nom SIMPLE — `CharSequence`), la
 * matière des propositions de complétion membre riches.
 *
 * @property nom le nom appelé ;
 * @property genre fonction ou propriété ;
 * @property typeDetail le type de présentation (nom simple), `null` sans
 * type démontrable (une fonction sans détail de signature).
 */
internal class MembreDetailleJvm(
    internal val nom: String,
    internal val genre: GenreMembreHerite,
    internal val typeDetail: String?,
)

/**
 * Le jeu de membres COMPLET d'un récepteur classpath (la matière des
 * preuves ADR 0223 — présence et absence) : déclarés de toute la chaîne,
 * propriétés dérivées et implicites de Any/Object.
 *
 * @property noms les noms appelables (dédupliqués, ordre de rencontre).
 */
internal class JeuMembresJvm(internal val noms: Set<String>) {

    /**
     * Le nom est-il un membre du jeu ?
     *
     * @param nom le nom du sélecteur ;
     * @return `true` si présent (la référence RÉSOUT).
     */
    internal fun contient(nom: String): Boolean = nom in noms
}

/**
 * L'ANNUAIRE des types des entrées du classpath (étage 3 vague 3a —
 * fiche 045) : la structure servie par la marche paresseuse de la vue —
 * noms simples TRIÉS (recherche par préfixe en binaire), noms simples →
 * FQCN (les collisions se proposent toutes), paquets CONNUS
 * (intermédiaires compris).
 *
 * @property nomsTries les noms SIMPLES des classes, triés ;
 * @property parNomSimple les FQCN par nom simple ;
 * @property paquets les paquets portant (ou précédant) des classes.
 */
internal class AnnuaireTypesJvm internal constructor(
    internal val nomsTries: List<String>,
    internal val parNomSimple: Map<String, List<String>>,
    internal val paquets: Set<String>,
)

/**
 * Le dépouillement des DESCRIPTEURS de la JVMS §4.3 (vague 2c) : les types
 * de paramètres et de retour en FQCN KOTLIN (`I` → `kotlin.Int`,
 * `Ljava/lang/String;` → `java.lang.String`, `V` → `kotlin.Unit`) ; les
 * TABLEAUX (`[I`) rendent `null` (conservateur — le membre reste muet,
 * jamais faux).
 */
internal object DescripteursJvm {

    /** Les types FQCN Kotlin des paramètres du descripteur (`null` si tableau). */
    internal fun parametres(descripteur: String): List<String>? {
        val fermeture = descripteur.indexOf(')')
        if (fermeture <= 0 || !descripteur.startsWith("(")) return null
        return lire(descripteur, 1, fermeture)?.first
    }

    /** Le type FQCN Kotlin du retour du descripteur (`null` si tableau). */
    internal fun retour(descripteur: String): String? {
        val fermeture = descripteur.indexOf(')')
        if (fermeture < 0 || !descripteur.startsWith("(")) return null
        return lire(descripteur, fermeture + 1, descripteur.length)?.first?.singleOrNull()
    }

    /**
     * Le type FQCN Kotlin d'un descripteur de CHAMP NU (vague 2d — le type
     * DU champ, pas un retour : `Lexo/theme/Typography;` →
     * `exo.theme.Typography`) ; `null` si tableau/illisible (conservateur).
     */
    internal fun typeNu(descripteur: String): String? {
        if (descripteur.startsWith("(")) return null
        return lire(descripteur, 0, descripteur.length)?.first?.singleOrNull()
    }

    /** La lecture d'une tranche de descripteur (types lus, position finale). */
    private fun lire(descripteur: String, debut: Int, fin: Int): Pair<List<String>, Int>? {
        val types = mutableListOf<String>()
        var i = debut
        while (i < fin) {
            when (val c = descripteur[i]) {
                'L' -> {
                    val pointVirgule = descripteur.indexOf(';', i)
                    if (pointVirgule < 0 || pointVirgule >= fin) return null
                    types.add(descripteur.substring(i + 1, pointVirgule).replace('/', '.'))
                    i = pointVirgule + 1
                }
                '[', 'V' -> return null // tableau : conservateur (D3).
                else -> {
                    val fqcn = PRIMITIFS[c] ?: return null
                    types.add(fqcn)
                    i++
                }
            }
        }
        return Pair(types, i)
    }

    /** Les primitifs JVM → FQCN Kotlin. */
    private val PRIMITIFS: Map<Char, String> = mapOf(
        'Z' to "kotlin.Boolean", 'B' to "kotlin.Byte", 'C' to "kotlin.Char",
        'S' to "kotlin.Short", 'I' to "kotlin.Int", 'J' to "kotlin.Long",
        'F' to "kotlin.Float", 'D' to "kotlin.Double",
    )
}

/**
 * Le lecteur maison des MEMBRES d'un class-file (JLS §4 : pool de
 * constantes, `fields_count`/`methods_count`, en-têtes u2×3 par membre) —
 * la même discipline octet par octet que `LecteurClassBrut` (R5), le
 * périmètre des membres ET DESCRIPTEURS EN PLUS ; les attributs sont sautés
 * balancés. Tolérant (D3) : tout écart rend `null`, jamais d'exception.
 */
private object LecteurMembresClasse {

    /** Magie d'un fichier de classe JVM. */
    private const val MAGIE_CLASSE: Int = 0xCAFEBABE.toInt()

    /** Accès SYNTHETIC (les membres générés ne sont pas appelables par nom). */
    private const val ACC_SYNTHETIC: Int = 0x1000

    /** Accès BRIDGE (les ponts génériques ne sont pas appelables par nom). */
    private const val ACC_BRIDGE: Int = 0x0040

    /** Accès ABSTRACT (vague 2f — les membres à implémenter d'un supertype). */
    private const val ACC_ABSTRACT_METHODE: Int = 0x0400

    /**
     * Lit les membres déclarés d'un class-file.
     *
     * @param octets les octets du `.class` ;
     * @return les membres + la chaîne écrite ; `null` si illisible (D3 —
     * le consommateur sous-rapporte, jamais d'exception).
     */
    internal fun lire(octets: ByteArray): MembresDeclaresJvm? {
        return try {
            lireOuNull(octets)
        } catch (_: Exception) {
            null
        }
    }

    /** Le corps de lecture (toute exception devient `null` par [lire]). */
    private fun lireOuNull(octets: ByteArray): MembresDeclaresJvm? {
        val flux = DataInputStream(ByteArrayInputStream(octets))
        if (flux.readInt() != MAGIE_CLASSE) return null
        flux.readInt() // version mineure + majeure.
        val compte = flux.readUnsignedShort()
        val utf8 = arrayOfNulls<String>(compte)
        // L'indirection des constantes CLASS (tag 7 : index → name_index)
        // — sans elle, la superclasse et les interfaces restent muettes.
        val classes = IntArray(compte)
        var i = 1
        while (i < compte) {
            val tag = flux.readUnsignedByte()
            when (tag) {
                1 -> {
                    val longueur = flux.readUnsignedShort()
                    val texte = ByteArray(longueur)
                    flux.readFully(texte)
                    utf8[i] = texte.toString(Charsets.UTF_8)
                }
                3, 4 -> flux.skipBytes(4)
                5, 6 -> {
                    flux.skipBytes(8)
                    i++ // les longs/doubles occupent DEUX index du pool.
                }
                7 -> classes[i] = flux.readUnsignedShort()
                8, 16, 19, 20 -> flux.skipBytes(2)
                9, 10, 11, 12, 17, 18 -> flux.skipBytes(4)
                15 -> flux.skipBytes(3)
                else -> return null
            }
            i++
        }
        fun nomClasse(index: Int): String? {
            if (index <= 0 || index >= compte) return null
            return utf8[classes[index]]?.replace('/', '.')
        }

        val accesClasse = flux.readUnsignedShort() // access_flags (fiche 018 : le genre).
        flux.skipBytes(2) // this_class.
        val superIndex = flux.readUnsignedShort()
        val superFqcn = if (superIndex == 0) null else nomClasse(superIndex)
        val nbInterfaces = flux.readUnsignedShort()
        val interfaces = mutableListOf<String>()
        repeat(nbInterfaces) {
            nomClasse(flux.readUnsignedShort())?.let { interfaces.add(it) }
        }

        val membres = mutableListOf<MembreJvm>()
        for (estChamp in listOf(true, false)) {
            val nombre = flux.readUnsignedShort()
            repeat(nombre) {
                val acces = flux.readUnsignedShort()
                val nom = utf8[flux.readUnsignedShort()] ?: return null
                val descripteur = utf8[flux.readUnsignedShort()] ?: return null
                val nbAttributs = flux.readUnsignedShort()
                repeat(nbAttributs) {
                    flux.skipBytes(2)
                    val longueur = flux.readInt()
                    flux.skipBytes(longueur)
                }
                if (nom == "<init>" || nom == "<clinit>") return@repeat
                if (acces and (ACC_SYNTHETIC or ACC_BRIDGE) != 0) return@repeat
                membres.add(MembreJvm(nom, descripteur, estChamp, acces and ACC_ABSTRACT_METHODE != 0, acces))
            }
        }

        // La dérivation ADR 0219 CONSCIENTE des descripteurs (vague 2c) :
        // `getText()` (aucun paramètre, retour non void) → `text` VAL ;
        // `isEnabled()` → `isEnabled` VAL ; `setText(X)` (un paramètre,
        // retour void) → `text` VAR ; un getter À PARAMÈTRE ne synthétise
        // RIEN (`getDrawable(I)` n'est pas `drawable` — fiche 040).
        val derivees = LinkedHashMap<String, ProprieteDeriveeJvm>()
        for (membre in membres) {
            if (membre.estChamp) continue
            val parametres = membre.parametres() ?: continue
            val retour = membre.retour()
            val nom = membre.nom
            if (nom.length > 3 && nom.startsWith("get") && parametres.isEmpty() && retour != null && retour != "kotlin.Unit") {
                val propriete = nom.substring(3).replaceFirstChar { it.lowercaseChar() }
                inscrireDerivee(derivees, propriete, retour, estVar = false)
            } else if (nom.length > 2 && nom.startsWith("is") && parametres.isEmpty() && retour != null && retour != "kotlin.Unit") {
                inscrireDerivee(derivees, nom, retour, estVar = false)
            } else if (nom.length > 3 && nom.startsWith("set") && parametres.size == 1) {
                val propriete = nom.substring(3).replaceFirstChar { it.lowercaseChar() }
                inscrireDerivee(derivees, propriete, parametres.single(), estVar = true)
            }
        }
        return MembresDeclaresJvm(membres, derivees.values.toList(), superFqcn, interfaces, accesClasse)
    }

    /** Inscrire une dérivation (getter+setter → VAR, getter seul → VAL). */
    private fun inscrireDerivee(
        derivees: LinkedHashMap<String, ProprieteDeriveeJvm>,
        nom: String,
        typeFqcn: String,
        estVar: Boolean,
    ) {
        if (nom.isEmpty()) return
        val courante = derivees[nom]
        if (courante == null) {
            derivees[nom] = ProprieteDeriveeJvm(nom, typeFqcn, estVar)
        } else if (estVar) {
            derivees[nom] = ProprieteDeriveeJvm(nom, courante.typeFqcn ?: typeFqcn, true)
        }
    }
}
