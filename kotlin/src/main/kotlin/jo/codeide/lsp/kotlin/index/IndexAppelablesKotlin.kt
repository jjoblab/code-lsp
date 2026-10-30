// CodeIDE — index des appelables Kotlin (module :kotlin, étage 1 vague
// 1d). Code NEUF jjoblab écrit pour code-lsp (contrat : fiches 008/034 du
// cahier docs/cahier-specifications-kotlin/ ; ADR 0108/0211/0214 anciens
// dont les CONTRATS sont repris : segments par empreinte, persistance
// sans jar d'origine, auto-guérison, cache mémoire, garde de couverture,
// annulation coopérative). INTERNE au module (D6).
package jo.codeide.lsp.kotlin.index

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import jo.codeide.lsp.kotlin.api.EntreeClasspathKotlin
import jo.codeide.lsp.kotlin.classpath.DecodeurMetadonnees

/**
 * L'index des APPELABLES Kotlin du classpath (K3) — partagé par toutes les
 * sessions d'un moteur.
 *
 * ## Segments par empreinte (contrat disque)
 *
 * Chaque entrée de classpath décode UNE fois, puis persiste en SEGMENT
 * `<dossierIndex>/kotlin-appelables/<sha256>.kidx` : le nom dérive de
 * l'EMPREINTE posée par l'hôte (préfixe d'espace de clés propre au module
 * — les segments d'une autre ère du moteur, sous un autre préfixe, ne
 * sont jamais atteints). Le format porte MAGIE + VERSION + empreinte
 * recoupée : un segment corrompu, renommé ou d'une autre version du
 * format est REJETÉ puis re-décodé depuis le jar (auto-guérison —
 * l'index est un CACHE, jamais une autorité). Un segment écrit est relu
 * SANS le jar d'origine : la persistance est le cœur du contrat.
 *
 * ## Trois leviers de coût (ADR 0211 ancien, repris)
 *
 * 1. **Garde d'empreintes** : `definir` aux MÊMES empreintes dans le MÊME
 *    ordre est un NO-OP CHAUD (la liste ordonnée qui a construit le
 *    snapshot courant est retenue — l'ordre fait la préséance des
 *    candidats, il fait partie de l'identité).
 * 2. **Cache mémoire par empreinte** : un segment lu une fois ne retourne
 *    JAMAIS au disque ; un ÉCHEC n'y entre JAMAIS (une reprise ciblée le
 *    retente toujours) ; BORNÉ (vidange au plafond — une sync qui enchaîne
 *    des classpaths massivement différents paie au plus une relecture).
 * 3. **Garde de couverture** : [couvre] dit si le snapshot courant couvre
 *    une liste d'entrées (liste EXACTE ordonnée) — le guichet des
 *    requêtes qui prouvent par l'absence s'en sert pour se taire.
 *
 * ## Écart et vide ne sont pas la même chose
 *
 * Une entrée ABSENTE du disque est ÉCARTÉE (elle n'entre pas dans le
 * snapshot — écart honnête) ; un jar JAVA PUR sans module Kotlin rend au
 * contraire une liste VIDE légitime (la porte Kotlin absente, zéro classe
 * décodée). Tolérance D3 partout : jamais d'exception vers l'appelant.
 *
 * ## Concurrence
 *
 * Snapshot IMMUBLE publié volatil : les requêtes ([VueAppelables]) lisent
 * l'état cohérent d'une définition, les redéfinitions remplacent TOUT ;
 * le cache mémoire est une ConcurrentHashMap (lectures sans verrou).
 */
@Suppress("TooManyFunctions")
internal class IndexAppelablesKotlin : VueAppelables {

    /** Le snapshot courant (vide avant la première définition). */
    @Volatile
    private var snapshot: Snapshot = Snapshot.VIDE

    /**
     * Empreintes (ORDONNÉES) qui ont construit le snapshot courant — la
     * garde du no-op chaud et la clé de [couvre].
     */
    @Volatile
    private var empreintesSnapshot: List<String> = emptyList()

    /**
     * Cache MÉMOIRE par empreinte — un segment lu une fois ne retourne
     * plus au disque ; les échecs n'y entrent JAMAIS.
     */
    private val cacheSegments = ConcurrentHashMap<String, List<AppelableKotlin>>()

    /** L'issue d'une entrée du passage — miroir de l'état public du travail. */
    internal enum class IssueSegment {

        /** Servie SANS reconstruction (cache mémoire ou segment disque). */
        REPRIS,

        /** Décodage à froid + écriture du segment. */
        TERMINE,

        /** Illisible ou absente : écartée honnêtement (jamais d'exception). */
        ECARTE,
    }

    /**
     * L'observation d'un passage — UNE méthode pour toute la vie d'une
     * entrée : `issue == null` = l'entrée devient ACTIVE (avant traitement
     * — un décodage à froid peut durer des secondes, l'écran montre
     * l'entrée en cours), sinon l'entrée aboutit à [issue] ; le retour
     * `false` ANNULE le passage (l'entrée courante reste ACQUISE, les
     * suivantes pendent).
     */
    internal fun interface ObservationPassage {

        /**
         * Le tick d'une entrée.
         *
         * @param entree l'entrée traitée ;
         * @param issue l'issue (`null` = l'entrée devient active) ;
         * @return `true` pour continuer le passage ; `false` pour
         * l'ANNULER après cette entrée (coopératif).
         */
        fun surUnite(
            entree: EntreeClasspathKotlin,
            issue: IssueSegment?,
        ): Boolean
    }

    /** Le résultat d'une définition. */
    internal enum class ResultatDefin {

        /** Snapshot reconstruit (au moins une empreinte lue). */
        RECONSTRUIT,

        /** No-op chaud : le snapshot couvrait déjà ces empreintes. */
        DEJA_COUVERT,

        /** Passage PARTIEL : annulation coopérative en cours de route. */
        PARTIEL_ANNULE,
    }

    /**
     * (Re)définit l'index depuis les entrées de classpath — chaque
     * empreinte est servie depuis le CACHE MÉMOIRE, sinon son segment
     * disque, sinon décodée à froid puis persistée. Les entrées absentes
     * du disque sont ÉCARTÉES (jamais d'exception — la vue hôte a déjà
     * filtré l'existence). Un retour d'observation `false` annule : les
     * entrées suivantes pendent, le snapshot partiel est la vérité.
     *
     * @param entrees les entrées (jars/AAR) dans l'ordre du classpath ;
     * @param dossierIndex répertoire de l'index partagé, `null` pour la
     *        mémoire seule ;
     * @param observation observateur du passage (`null` = silence) ;
     * @return le résultat du passage.
     */
    @Suppress("LoopWithTooManyJumpStatements", "CyclomaticComplexMethod")
    internal fun definir(
        entrees: List<EntreeClasspathKotlin>,
        dossierIndex: File?,
        observation: ObservationPassage? = null,
    ): ResultatDefin {
        val empreintes = entrees.map { it.empreinte }
        if (empreintes == empreintesSnapshot) return ResultatDefin.DEJA_COUVERT
        var annule = false
        val parNom = HashMap<String, MutableList<AppelableKotlin>>()
        val extensions = HashMap<String, MutableList<AppelableKotlin>>()
        val aliasParNom = HashMap<String, MutableList<AppelableKotlin>>()
        val chargees = mutableListOf<String>()
        for (entree in entrees) {
            if (annule) break // l'entrée reste PENDANTE (non traitée)
            observation?.surUnite(entree, null)
            val lecture = charger(entree, dossierIndex)
            val issue =
                when {
                    lecture.appelables == null -> IssueSegment.ECARTE
                    lecture.reprise -> IssueSegment.REPRIS
                    else -> IssueSegment.TERMINE
                }
            if (observation?.surUnite(entree, issue) == false) {
                annule = true
            }
            val appelables = lecture.appelables ?: continue
            for (appelable in appelables) {
                // Les TYPEALIAS vivent dans une carte DÉDIÉE : parNom et
                // extensions (complétion, survol, signatures) restent des
                // APPELABLES purs.
                if (appelable.genre == GenreAppelable.ALIAS) {
                    aliasParNom.getOrPut(appelable.nom) { mutableListOf() }.add(appelable)
                    continue
                }
                parNom.getOrPut(appelable.nom) { mutableListOf() }.add(appelable)
                appelable.recepteur?.let { recepteur ->
                    extensions.getOrPut(recepteur) { mutableListOf() }.add(appelable)
                }
            }
            chargees.add(entree.empreinte)
        }
        snapshot =
            Snapshot(
                parNom = parNom,
                nomsTries = parNom.keys.sorted(),
                extensions = extensions,
                aliasParNom = aliasParNom,
            )
        empreintesSnapshot = chargees
        return if (annule) ResultatDefin.PARTIEL_ANNULE else ResultatDefin.RECONSTRUIT
    }

    /** Le snapshot courant couvre-t-il EXACTEMENT ces entrées (ordre compris) ? */
    internal fun couvre(entrees: List<EntreeClasspathKotlin>): Boolean =
        entrees.map { it.empreinte } == empreintesSnapshot

    // ------------------------------------------------------------------
    // Requêtes (VueAppelables — lectures sans verrou sur l'immuable).
    // ------------------------------------------------------------------

    /** Appelables de premier niveau portant EXACTEMENT ce nom. */
    override fun topParNom(nom: String): List<AppelableKotlin> = snapshot.parNom[nom] ?: emptyList()

    /**
     * Noms de premier niveau portant un PRÉFIXE — recherche binaire
     * (premier nom >= prefixe) puis marche bornée par le préfixe ET la limite.
     */
    override fun nomsTopParPrefixe(
        prefixe: String,
        limite: Int,
    ): List<String> {
        val noms = snapshot.nomsTries
        var bas = 0
        var haut = noms.size
        while (bas < haut) {
            val milieu = (bas + haut) ushr 1
            if (noms[milieu] < prefixe) bas = milieu + 1 else haut = milieu
        }
        val rendus = mutableListOf<String>()
        while (bas < noms.size && rendus.size < limite) {
            if (!noms[bas].startsWith(prefixe)) break
            rendus.add(noms[bas])
            bas++
        }
        return rendus
    }

    /** Extensions dont le récepteur EST ce FQCN. */
    override fun extensionsSur(recepteur: String): List<AppelableKotlin> =
        snapshot.extensions[recepteur] ?: emptyList()

    /** TYPEALIAS publics de premier niveau portant EXACTEMENT ce nom. */
    override fun aliasTopParNom(nom: String): List<AppelableKotlin> =
        snapshot.aliasParNom[nom] ?: emptyList()

    /** Le FQCN complet désigne-t-il un TYPEALIAS public du classpath ? */
    override fun estTypeAliasConnu(fqcn: String): Boolean {
        val nom = fqcn.substringAfterLast('.')
        if (nom.isEmpty() || nom == fqcn) return false
        return aliasTopParNom(nom).any { "${it.paquet}.${it.nom}" == fqcn }
    }

    /**
     * Le nom de segment d'une empreinte (inspection/test — le fichier vit
     * sous `<dossierIndex>/kotlin-appelables/`).
     *
     * @param empreinte l'empreinte d'identité posée par l'hôte ;
     * @return le nom de fichier du segment.
     */
    internal fun nomSegment(empreinte: String): String = sha256Hex(PREFIXE_CLE + empreinte) + SUFFIXE_SEGMENT

    // ------------------------------------------------------------------
    // Segments (cache mémoire → disque → décodage à froid).
    // ------------------------------------------------------------------

    /** Une lecture d'entrée : les appelables (null = écart) + la provenance. */
    private class Lecture(
        val appelables: List<AppelableKotlin>?,
        val reprise: Boolean,
    )

    /**
     * Charge une entrée — cache mémoire D'ABORD, sinon segment disque
     * valide, sinon DÉCODAGE à froid. Un échec n'est jamais mis en cache.
     */
    private fun charger(
        entree: EntreeClasspathKotlin,
        dossierIndex: File?,
    ): Lecture {
        cacheSegments[entree.empreinte]?.let { enCache ->
            return Lecture(enCache, reprise = true)
        }
        val (appelables, repriseDisque) = chargerSegment(entree, dossierIndex)
        if (appelables == null) return Lecture(null, reprise = false)
        retenirEnCache(entree.empreinte, appelables)
        return Lecture(appelables, reprise = repriseDisque)
    }

    /** Retient une lecture en cache mémoire — borné (vidange au plafond). */
    private fun retenirEnCache(
        empreinte: String,
        appelables: List<AppelableKotlin>,
    ) {
        if (cacheSegments.size >= PLAFOND_CACHE_MEMOIRE) cacheSegments.clear()
        cacheSegments[empreinte] = appelables
    }

    /**
     * Segment valide si présent (REPRISE — jamais redécodé) ; sinon
     * l'entrée doit EXISTER pour être décodée — un jar ABSENT est un écart
     * honnête, un jar JAVA PUR sans métadonnées Kotlin est une liste VIDE
     * légitime. Le décodage à froid écrit son segment ATOMIC
     * (tmp + rename — jamais de demi-segment visible).
     */
    @Suppress("ReturnCount")
    private fun chargerSegment(
        entree: EntreeClasspathKotlin,
        dossierIndex: File?,
    ): Pair<List<AppelableKotlin>?, Boolean> {
        if (dossierIndex == null) {
            return decoderSiPresent(entree) to false
        }
        val fichier = File(File(dossierIndex, DOSSIER_SEGMENTS), nomSegment(entree.empreinte))
        // Le SEGMENT d'abord : il survit au jar DISPARU (l'index est un
        // CACHE, jamais une autorité — le contrat de persistance).
        if (fichier.isFile) {
            lireSegment(fichier, entree.empreinte)?.let { return it to true }
        }
        val appelables = decoderSiPresent(entree) ?: return null to false
        ecrireSegment(fichier, entree.empreinte, appelables)
        return appelables to false
    }

    /**
     * Décodage à froid — `null` si l'entrée est ABSENTE ou illisible
     * (écart honnête) ; une liste ÉVENTUELLEMENT VIDE sinon.
     *
     * SwallowedException ciblé : `null` EST le contrat pour une entrée
     * illisible — l'index est partiel honnête (D3).
     */
    @Suppress("SwallowedException")
    private fun decoderSiPresent(entree: EntreeClasspathKotlin): List<AppelableKotlin>? {
        if (!File(entree.chemin).isFile) return null
        return try {
            DecodeurMetadonnees.decoder(File(entree.chemin), entree.kind)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Lit et VALIDE un segment (magie, version, empreinte recoupée — un
     * fichier renommé ou réécrit sous un mauvais nom est détecté, pas
     * cru). Toute incohérence rend `null` (re-décodage — auto-guérison).
     *
     * SwallowedException ciblé : corrompu → null — l'exception EST la
     * détection, le rejet du segment est le contrat (D3).
     */
    @Suppress("SwallowedException", "ReturnCount")
    private fun lireSegment(
        fichier: File,
        empreinte: String,
    ): List<AppelableKotlin>? =
        try {
            DataInputStream(fichier.inputStream().buffered()).use { flux ->
                if (flux.readInt() != MAGIE) return null
                if (flux.readInt() != VERSION_FORMAT) return null
                if (flux.readUTF() != empreinte) return null
                val nombre = flux.readInt()
                if (nombre < 0) return null
                List(nombre) { lireAppelable(flux) }
            }
        } catch (_: Throwable) {
            null
        }

    /**
     * Écriture atomique (tmp + rename) — un échec d'écriture dégrade en
     * mémoire seule : le disque est un CACHE, jamais une autorité.
     *
     * SwallowedException ciblé : la dégradation silencieuse EST le contrat.
     */
    @Suppress("SwallowedException")
    private fun ecrireSegment(
        fichier: File,
        empreinte: String,
        appelables: List<AppelableKotlin>,
    ) {
        try {
            fichier.parentFile.mkdirs()
            val tampon = ByteArrayOutputStream()
            DataOutputStream(tampon).use { flux ->
                flux.writeInt(MAGIE)
                flux.writeInt(VERSION_FORMAT)
                flux.writeUTF(empreinte)
                flux.writeInt(appelables.size)
                appelables.forEach { ecrireAppelable(flux, it) }
            }
            val temporaire = File(fichier.parentFile, fichier.name + SUFFIXE_TEMPORAIRE)
            java.io.FileOutputStream(temporaire).use { sortie ->
                sortie.write(tampon.toByteArray())
            }
            if (!temporaire.renameTo(fichier)) {
                temporaire.delete()
            }
        } catch (_: Throwable) {
            // Mémoire seule : honnête, jamais d'exception vers l'appelant.
        }
    }

    /** Un appelable en binaire compact (les booléens portent les présences). */
    private fun ecrireAppelable(
        flux: DataOutputStream,
        appelable: AppelableKotlin,
    ) {
        flux.writeByte(appelable.genre.ordinal)
        flux.writeUTF(appelable.nom)
        flux.writeUTF(appelable.paquet)
        if (appelable.recepteur != null) {
            flux.writeBoolean(true)
            flux.writeUTF(appelable.recepteur)
        } else {
            flux.writeBoolean(false)
        }
        if (appelable.typeRetour != null) {
            flux.writeBoolean(true)
            flux.writeUTF(appelable.typeRetour)
        } else {
            flux.writeBoolean(false)
        }
        flux.writeInt(appelable.parametres.size)
        appelable.parametres.forEach { parametre ->
            if (parametre.nom != null) {
                flux.writeBoolean(true)
                flux.writeUTF(parametre.nom)
            } else {
                flux.writeBoolean(false)
            }
            if (parametre.type != null) {
                flux.writeBoolean(true)
                flux.writeUTF(parametre.type)
            } else {
                flux.writeBoolean(false)
            }
        }
        flux.writeUTF(appelable.conteneur)
    }

    /** Relecture symétrique (toute incohérence lève → segment rejeté). */
    private fun lireAppelable(flux: DataInputStream): AppelableKotlin {
        val genre = GenreAppelable.entries[flux.readByte().toInt()]
        val nom = flux.readUTF()
        val paquet = flux.readUTF()
        val recepteur = if (flux.readBoolean()) flux.readUTF() else null
        val typeRetour = if (flux.readBoolean()) flux.readUTF() else null
        val parametres =
            List(flux.readInt()) {
                ParametreAppelable(
                    nom = if (flux.readBoolean()) flux.readUTF() else null,
                    type = if (flux.readBoolean()) flux.readUTF() else null,
                )
            }
        val conteneur = flux.readUTF()
        return AppelableKotlin(genre, nom, paquet, recepteur, typeRetour, parametres, conteneur)
    }

    /** Le snapshot immuable publié par [definir] (vide avant définition). */
    private class Snapshot(
        val parNom: Map<String, List<AppelableKotlin>>,
        val nomsTries: List<String>,
        val extensions: Map<String, List<AppelableKotlin>>,
        val aliasParNom: Map<String, List<AppelableKotlin>>,
    ) {
        companion object {
            val VIDE = Snapshot(emptyMap(), emptyList(), emptyMap(), emptyMap())
        }
    }

    private companion object {
        /** Magie des segments (0x4B434944 = « KCID »). */
        internal const val MAGIE: Int = 0x4B434944

        /**
         * Version du format — v1 du module (alias portés d'origine).
         * Toute incompatibilité de lecture incrémente : les segments
         * périmés sont rejetés puis re-décodés (auto-guérison). L'espace
         * de clés ([PREFIXE_CLE]) isole les noms de segment de toute
         * autre ère du moteur.
         */
        internal const val VERSION_FORMAT: Int = 1

        /** Sous-répertoire dédié de l'index partagé. */
        internal const val DOSSIER_SEGMENTS: String = "kotlin-appelables"

        /** Suffixe des segments. */
        internal const val SUFFIXE_SEGMENT: String = ".kidx"

        /** Suffixe d'écriture atomique. */
        internal const val SUFFIXE_TEMPORAIRE: String = ".tmp"

        /** Préfixe de dérivation (espace de clés propre au module). */
        internal const val PREFIXE_CLE: String = "codeide-lsp-kotlin|appelables|"

        /** Plafond du cache mémoire par empreinte (vidange au-delà). */
        internal const val PLAFOND_CACHE_MEMOIRE: Int = 256

        /** sha256 hexadécimal (autonome — entité indépendante D6). */
        internal fun sha256Hex(valeur: String): String =
            MessageDigest
                .getInstance("SHA-256")
                .digest(valeur.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
