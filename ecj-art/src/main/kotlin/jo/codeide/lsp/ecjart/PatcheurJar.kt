package jo.codeide.lsp.ecjart

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.Remapper
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Patcheur build-time du jar ecj pour la compatibilité ART — réécriture
 * intégrale vague 1c (ADR 0001 §2, ADR 0005, ADR 0018).
 *
 * CLEAN-ROOM (R9.3 voie ①) : l'ancien patcheur de codeide-lsp n'a pas été
 * lu ; les transformations sont dictées par la liste de shims versionnée
 * ([ListeShims]) et implémentées avec l'API publique ASM 9.7.
 *
 * Le patchage est CIBLÉ ET MINIMAL : le scanner analyse les constant pools
 * du jar entrant, seules les classes qui référencent un motif de la liste
 * sont réécrites (toutes les autres entrées — classes, ressources,
 * manifeste — sont recopiées octet pour octet), et le jar sortant est
 * re-scanné avant d'être déclaré bon : tout motif bloquant résiduel fait
 * échouer l'opération.
 *
 * Sortie produite par [patcher] :
 *  - classes ecj patchées (remap de types, redirections d'invocations) ;
 *  - shims embarqués (paquet `jo.codeide.lsp.ecjart.compat`, depuis
 *    [racineClassesShims]) ;
 *  - `META-INF/ECJ-ART-MODIFICATIONS.txt` : registre des modifications
 *    (obligation EPL-2.0 d'informer les destinataires d'un code modifié) ;
 *  - signatures amont (entrées `META-INF` d'extension `.SF`, `.RSA`,
 *    `.DSA`) RETIRÉES : un jar modifié ne peut pas garder des signatures
 *    devenues invalides (elles provoqueraient des `SecurityException` au
 *    chargement).
 *
 * @param jarEntree jar ecj à patcher (p. ex. `ecj-3.40.0.jar` de Maven Central).
 * @param racineClassesShims racine du répertoire de classes compilées
 *   contenant le paquet `jo/codeide/lsp/ecjart/compat` (les shims à embarquer).
 * @param jarSortie chemin du jar patché à écrire (créé, avec ses parents).
 * @param liste liste de shims versionnée pilotant les transformations.
 */
public class PatcheurJar(
    private val jarEntree: Path,
    private val racineClassesShims: Path,
    private val jarSortie: Path,
    private val liste: ListeShims,
) {

    /** Paquet (avec slashs) des shims embarqués dans le jar patché. */
    private val paquetCompat: String = "jo/codeide/lsp/ecjart/compat"

    /**
     * Applique le patchage et écrit le jar sorti.
     *
     * @return le rapport (classes patchées, remplacements, shims embarqués,
     *   signatures retirées).
     * @throws IllegalArgumentException si la liste est incohérente avec
     *   elle-même (descripteur shim mal formé, méthode shim déclarée
     *   manquante ou non statique) ou si le jar ENTRANT contient un motif
     *   bloquant NON couvert par la liste (garde ADR 0018 : la liste doit
     *   être révisée, pas contournée).
     * @throws IllegalStateException si le re-scan du jar SORTANT révèle un
     *   motif bloquant résiduel (transformation incomplète).
     */
    public fun patcher(): RapportPatch {
        val shims = chargerShims()
        val scanneur = ScanneurArt()

        // --- ciblage : quelles classes toucher, d'après le scan du jar entrant ---
        val scanEntree = scanneur.scanner(jarEntree)
        val classesARemapper = mutableMapOf<String, DirectiveRemap>()
        for (directive in liste.remaps()) {
            for (chemin in scanEntree.classesMentionnant(directive.classeAmont)) {
                classesARemapper[chemin] = directive
            }
        }
        val classesARediriger = mutableMapOf<String, MutableList<DirectiveRedirection>>()
        for (directive in liste.redirections()) {
            for (chemin in scanEntree.classesInvoquant(
                    directive.proprietaire, directive.methode, directive.descripteur,
                )) {
                classesARediriger.getOrPut(chemin) { mutableListOf() }.add(directive)
            }
        }
        val cibles = classesARemapper.keys + classesARediriger.keys

        // --- garde ADR 0018 : tout motif bloquant du jar entrant doit être couvert ---
        val clefsCouvertes = clefsCouvertesParLaListe()
        val motifsEntrants = scanEntree.motifs()
        val motifsNonCouverts = motifsEntrants.filter { it.cle !in clefsCouvertes && it.cle !in SURVEILLES }
        require(motifsNonCouverts.isEmpty()) {
            "motifs ART non couverts par la liste de shims (réviser la liste, ADR 0018) : " +
                motifsNonCouverts.joinToString("; ") { "${it.cle} (${it.occurrences}x) — ${it.verdict}" }
        }

        // --- vérification des shims embarqués (échec rapide avant transformation) ---
        for (directive in liste.redirections()) {
            verifierDescripteurRedirection(directive)
            verifierMethodeShim(shims, directive)
        }
        for (directive in liste.remaps()) {
            require(shims.containsKey(directive.classeShim)) {
                "shim de remap absent des classes embarquées : ${directive.classeShim}"
            }
        }

        // --- écriture du jar sortant ---
        Files.createDirectories(jarSortie.toAbsolutePath().parent)
        val evenements = mutableListOf<EvenementPatch>()
        val signaturesRetirees = mutableListOf<String>()
        ZipOutputStream(Files.newOutputStream(jarSortie)).use { sortie ->
            ZipFile(jarEntree.toFile()).use { archive ->
                for (entree in archive.entries()) {
                    val nom = entree.name
                    if (estFichierSignature(nom)) {
                        signaturesRetirees.add(nom)
                        continue
                    }
                    val octets = archive.getInputStream(entree).readBytes()
                    if (nom.endsWith(".class") && nom in cibles) {
                        val patche = transformerClasse(
                            octets, classesARemapper[nom],
                            classesARediriger[nom] ?: emptyList(), nom, evenements,
                        )
                        ecrire(sortie, nom, patche)
                    } else {
                        ecrire(sortie, nom, octets)
                    }
                }
            }
            // Shims embarqués ( après les entrées ecj, ordre stable ).
            for ((nomInterne, octets) in shims.toSortedMap()) {
                ecrire(sortie, "$nomInterne.class", octets)
            }
            // Registre des modifications (EPL-2.0).
            ecrire(sortie, "META-INF/ECJ-ART-MODIFICATIONS.txt", redigerRegistre(evenements, signaturesRetirees))
        }

        // --- re-scan du jar sortant : AUCUN motif bloquant résiduel toléré ---
        val scanSortie = scanneur.scanner(jarSortie)
        val residuels = scanSortie.motifs().filter { it.cle !in SURVEILLES }
        check(residuels.isEmpty()) {
            "motifs sensibles RÉSIDUELS dans le jar patché (transformation incomplète) : " +
                residuels.joinToString("; ") { "${it.cle} (${it.occurrences}x)" }
        }

        return RapportPatch(
            jarEntree = jarEntree,
            jarSortie = jarSortie,
            liste = liste,
            classesPatchees = evenements.groupBy { it.cheminClasse }
                .map { (chemin, evts) ->
                    ClassePatchee(
                        chemin,
                        evts.count { it is EvenementRemap } > 0,
                        evts.count { it is EvenementRedirection },
                    )
                }.sortedBy { it.chemin },
            shimsEmbarques = shims.keys.sorted(),
            signaturesRetirees = signaturesRetirees.sorted(),
            motifsEntrants = motifsEntrants,
        )
    }

    // ------------------------------------------------------------------
    // Transformation d'une classe (chaîne ASM : lecteur → remap → redirection → rédacteur)
    // ------------------------------------------------------------------

    /**
     * Transforme les octets d'une classe ciblée.
     *
     * La chaîne applique d'abord le remap de types (`ClassRemapper` —
     * entrées Class, descripteurs, signatures et frames), puis la
     * redirection d'invocations : la substitution préserve exactement la
     * forme de pile (récepteur de l'invocation virtuelle → premier argument
     * de l'appel statique), les frames n'ont donc pas à être recalculées.
     */
    private fun transformerClasse(
        octets: ByteArray,
        remap: DirectiveRemap?,
        redirections: List<DirectiveRedirection>,
        cheminClasse: String,
        evenements: MutableList<EvenementPatch>,
    ): ByteArray {
        val lecteur = ClassReader(octets)
        // RÉDACTEUR INDÉPENDANT (sans liaison au lecteur) — choix délibéré :
        // ClassWriter(lecteur, …) active l'optimisation « mostly add » d'ASM,
        // qui copie le pool de constantes amont « tel quel » ; les entrées
        // remplacées (Class Runtime$Version, Methodref readAllBytes…) y
        // survivraient alors comme entrées MORTES. Or le re-scan du jar
        // sortant compte les ENTRÉES de pool (ScanneurArt), pas leurs usages :
        // ces cadavres seraient signalés comme motifs résiduels. Un rédacteur
        // non lié reconstruit le pool depuis les seuls événements visités —
        // seules les références réelles subsistent.
        val redacteur = ClassWriter(0)

        val regles: Map<Triple<String, String, String>, DirectiveRedirection> =
            redirections.associateBy { Triple(it.proprietaire, it.methode, it.descripteur) }

        val visiteurRedirection = VisiteurRedirection(redacteur, regles, cheminClasse, evenements)
        val premier: ClassVisitor = if (remap != null) {
            evenements.add(EvenementRemap(cheminClasse, remap))
            ClassRemapper(
                visiteurRedirection,
                object : Remapper() {
                    override fun map(nomInterne: String): String =
                        if (nomInterne == remap.classeAmont) remap.classeShim else nomInterne
                },
            )
        } else {
            visiteurRedirection
        }
        lecteur.accept(premier, 0)
        return redacteur.toByteArray()
    }

    /**
     * Visiteur de redirection : remplace les invocations VIRTUELLES ciblées
     * par des appels STATIQUES aux shims (récepteur → premier argument).
     */
    private class VisiteurRedirection(
        suivant: ClassVisitor,
        private val regles: Map<Triple<String, String, String>, DirectiveRedirection>,
        private val cheminClasse: String,
        private val evenements: MutableList<EvenementPatch>,
    ) : ClassVisitor(Opcodes.ASM9, suivant) {

        override fun visitMethod(
            acces: Int,
            nom: String?,
            descripteur: String?,
            signature: String?,
            exceptions: Array<out String>?,
        ): MethodVisitor? {
            val suivantMethode = super.visitMethod(acces, nom, descripteur, signature, exceptions)
                ?: return null
            return object : MethodVisitor(Opcodes.ASM9, suivantMethode) {
                override fun visitMethodInsn(
                    opcode: Int,
                    proprietaire: String?,
                    nomMethode: String?,
                    descripteurMethode: String?,
                    estInterface: Boolean,
                ) {
                    val clef = Triple(proprietaire ?: "", nomMethode ?: "", descripteurMethode ?: "")
                    val regle = regles[clef]
                    if (regle != null && opcode == Opcodes.INVOKEVIRTUAL) {
                        evenements.add(EvenementRedirection(cheminClasse, nom ?: "?", regle))
                        super.visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            regle.shimProprietaire,
                            regle.shimMethode,
                            regle.shimDescripteur,
                            false,
                        )
                    } else {
                        super.visitMethodInsn(opcode, proprietaire, nomMethode, descripteurMethode, estInterface)
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Shims : chargement, vérifications
    // ------------------------------------------------------------------

    /**
     * Charge les classes du paquet compat depuis [racineClassesShims].
     *
     * @return nom interne (sans `.class`) → octets de la classe compilée.
     * @throws IllegalArgumentException si aucune classe shim n'est trouvée.
     */
    private fun chargerShims(): Map<String, ByteArray> {
        val dossier = racineClassesShims.resolve(paquetCompat)
        require(Files.isDirectory(dossier)) {
            "répertoire des shims introuvable : $dossier (racine : $racineClassesShims)"
        }
        val shims = mutableMapOf<String, ByteArray>()
        Files.walk(dossier).use { flux ->
            flux.filter { it.toString().endsWith(".class") }.sorted().forEach { fichier ->
                val nomInterne = paquetCompat + "/" + dossier.relativize(fichier).toString()
                    .removeSuffix(".class")
                shims[nomInterne] = Files.readAllBytes(fichier)
            }
        }
        require(shims.isNotEmpty()) { "aucune classe shim sous $dossier" }
        return shims
    }

    /**
     * Vérifie la cohérence d'une directive de redirection : le descripteur
     * shim doit être la forme « récepteur en premier argument » du
     * descripteur amont (sinon la pile ne correspond plus).
     */
    private fun verifierDescripteurRedirection(directive: DirectiveRedirection) {
        require(directive.descripteur.startsWith("(")) {
            "descripteur amont invalide : « ${directive.descripteur} »"
        }
        val attendu = "(L${directive.proprietaire};${directive.descripteur.substring(1)}"
        require(directive.shimDescripteur == attendu) {
            "descripteur shim incohérent pour ${directive.proprietaire}.${directive.methode} : " +
                "attendu « $attendu », trouvé « ${directive.shimDescripteur} »"
        }
    }

    /**
     * Vérifie (par lecture ASM, sans exécution) que la méthode shim visée
     * par une directive existe, est statique et porte exactement le
     * descripteur déclaré — échec rapide avant toute transformation.
     */
    private fun verifierMethodeShim(shims: Map<String, ByteArray>, directive: DirectiveRedirection) {
        val octets = shims[directive.shimProprietaire]
            ?: throw IllegalArgumentException(
                "classe shim absente des classes embarquées : ${directive.shimProprietaire}",
            )
        var trouvee = false
        ClassReader(octets).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(
                acces: Int,
                nom: String?,
                descripteur: String?,
                signature: String?,
                exceptions: Array<out String>?,
            ): MethodVisitor? {
                if (nom == directive.shimMethode && descripteur == directive.shimDescripteur) {
                    require(acces and Opcodes.ACC_STATIC != 0) {
                        "la méthode shim ${directive.shimProprietaire}.${nom} doit être STATIQUE"
                    }
                    trouvee = true
                }
                return null // seuls les en-têtes de méthodes intéressent ici
            }
        }, ClassReader.SKIP_CODE)
        require(trouvee) {
            "méthode shim introuvable : ${directive.shimProprietaire}" +
                ".${directive.shimMethode}${directive.shimDescripteur}"
        }
    }

    // ------------------------------------------------------------------
    // Registre des modifications (EPL-2.0) et utilitaires d'écriture
    // ------------------------------------------------------------------

    /** Rédige `META-INF/ECJ-ART-MODIFICATIONS.txt`. */
    private fun redigerRegistre(
        evenements: List<EvenementPatch>,
        signaturesRetirees: List<String>,
    ): ByteArray {
        val texte = buildString {
            appendLine("Jar ecj MODIFIÉ par le patcheur ecj-art du dépôt jjoblab/code-lsp (Apache-2.0).")
            appendLine("Artefact amont : $jarEntree (org.eclipse.jdt:ecj ${liste.versionEcj}, EPL-2.0 — Eclipse JDT).")
            appendLine("Conformément à l'EPL-2.0, ce code est modifié : les transformations sont décrites ci-dessous ;")
            appendLine("les sources amont non modifiées sont disponibles sur Maven Central (org.eclipse.jdt:ecj).")
            appendLine()
            appendLine("Liste de shims versionnée : ecj ${liste.versionEcj} (ADR 0018).")
            appendLine("Transformations appliquées :")
            for (e in evenements) {
                when (e) {
                    is EvenementRemap -> appendLine(
                        "  - ${e.cheminClasse} : remap de type ${e.directive.classeAmont} -> ${e.directive.classeShim}",
                    )
                    is EvenementRedirection -> appendLine(
                        "  - ${e.cheminClasse} (${e.methode}) : invocation " +
                            "${e.directive.proprietaire}.${e.directive.methode}${e.directive.descripteur} " +
                            "-> appel statique ${e.directive.shimProprietaire}.${e.directive.shimMethode}",
                    )
                }
            }
            if (signaturesRetirees.isNotEmpty()) {
                appendLine("Signatures amont retirées (invalidées par modification) :")
                for (s in signaturesRetirees) appendLine("  - $s")
            }
            appendLine("Shims embarqués (code neuf code-lsp, Apache-2.0) : paquet jo.codeide.lsp.ecjart.compat.")
        }
        return texte.toByteArray(Charsets.UTF_8)
    }

    /** Écrit une entrée (horodatage fixé à 0 — sortie déterministe). */
    private fun ecrire(sortie: ZipOutputStream, nom: String, octets: ByteArray) {
        val entree = ZipEntry(nom)
        entree.time = 0
        sortie.putNextEntry(entree)
        sortie.write(octets)
        sortie.closeEntry()
    }

    /** Reconnaît une entrée de signature de jar (retrait obligatoire après modification). */
    private fun estFichierSignature(nom: String): Boolean {
        if (!nom.startsWith("META-INF/") || nom.endsWith("/")) return false
        return when (nom.substringAfterLast('.', "").lowercase()) {
            "sf", "rsa", "dsa" -> true
            else -> false
        }
    }

    /** Motif bloquant au sens du re-scan : ni couvert par la liste, ni simplement surveillé. */
    internal fun clefsCouvertesParLaListe(): Set<String> = buildSet {
        for (d in liste.remaps()) add(d.classeAmont)
        for (d in liste.redirections()) add("${d.proprietaire}.${d.methode}")
    }
}

/** Motifs tolérés à l'identique (présents sur ART ou usage à vérifier) — ADR 0018. */
internal val SURVEILLES: Set<String> = setOf(
    "java/lang/invoke/MethodHandles", // présent sur ART (API 26+), usages Lookup à vérifier
    "java/util/ServiceLoader",        // présent sur ART, packaging META-INF/services à vérifier
    "java/nio/MappedByteBuffer",      // présent mais map() limité sur ART
    "com/sun",                        // partiellement absent — aucun usage constaté dans ecj
)

/** Événement de patch consigné au rapport et au registre EPL. */
internal sealed interface EvenementPatch {
    val cheminClasse: String
}

/** Remap de type appliqué à une classe. */
internal data class EvenementRemap(
    override val cheminClasse: String,
    val directive: DirectiveRemap,
) : EvenementPatch

/** Redirection d'invocation appliquée dans une méthode d'une classe. */
internal data class EvenementRedirection(
    override val cheminClasse: String,
    val methode: String,
    val directive: DirectiveRedirection,
) : EvenementPatch

/**
 * Classe patchée, telle que rapportée par [PatcheurJar.patcher].
 *
 * @param chemin entrée du jar (chemin interne avec `.class`).
 * @param remappee la classe a subi un remap de type.
 * @param redirections nombre d'invocations redirigées vers un shim.
 */
public data class ClassePatchee(
    public val chemin: String,
    public val remappee: Boolean,
    public val redirections: Int,
)

/**
 * Rapport d'exécution du patcheur.
 *
 * @param jarEntree jar amont patché.
 * @param jarSortie jar patché écrit (re-scanné : aucun motif bloquant résiduel).
 * @param liste liste de shims appliquée.
 * @param classesPatchees classes effectivement réécrites (toutes les autres
 *   entrées sont recopiées octet pour octet).
 * @param shimsEmbarques noms internes des classes shim embarquées.
 * @param signaturesRetirees entrées de signature amont retirées.
 * @param motifsEntrants motifs sensibles constatés au scan du jar entrant
 *   (tous couverts par la liste ou surveillés — sinon l'opération échoue).
 */
public class RapportPatch(
    public val jarEntree: Path,
    public val jarSortie: Path,
    public val liste: ListeShims,
    public val classesPatchees: List<ClassePatchee>,
    public val shimsEmbarques: List<String>,
    public val signaturesRetirees: List<String>,
    public val motifsEntrants: List<MotifSensible>,
)

/**
 * Point d'entrée du patcheur (tâche Gradle `patcherEcj`).
 *
 * La racine des classes shims n'est PAS un argument : elle est localisée via
 * le code source de cette classe — les shims du paquet `compat` partagent le
 * même répertoire de sortie compilé que le patcheur (classpath de la tâche
 * Gradle), ce qui élimine tout raccordement de chemin à maintenir.
 *
 * Usage : `PatcheurJarKt <jar-entrant> <jar-sortant> <liste-shims>`
 */
public fun main(args: Array<String>) {
    if (args.size != 3) {
        System.err.println(
            "usage : PatcheurJarKt <jar-entrant> <jar-sortant> <liste-shims>",
        )
        kotlin.system.exitProcess(2)
    }
    val jarEntree = Path.of(args[0])
    val jarSortie = Path.of(args[1])
    val fichierListe = Path.of(args[2])
    val versionEcj = fichierListe.fileName.toString()
        .removePrefix("liste-shims-").removeSuffix(".txt")

    val racineShims = Path.of(
        PatcheurJar::class.java.protectionDomain.codeSource.location.toURI(),
    )

    val liste = ListeShims.charger(fichierListe, versionEcj)
    val rapport = PatcheurJar(jarEntree, racineShims, jarSortie, liste).patcher()

    println("ecj-art : ${rapport.jarSortie} écrit (${Files.size(jarSortie) / 1024} Ko)")
    println("  classes patchées    : ${rapport.classesPatchees.size}")
    for (c in rapport.classesPatchees) {
        println("    - ${c.chemin} ${if (c.remappee) "[remap]" else ""}${if (c.redirections > 0) "[${c.redirections} redirection(s)]" else ""}")
    }
    println("  shims embarqués     : ${rapport.shimsEmbarques.size}")
    println("  signatures retirées : ${rapport.signaturesRetirees.joinToString()}")
    println("  motifs entrants     : ${rapport.motifsEntrants.joinToString { "${it.cle} (${it.occurrences}x)" }}")
    println("  re-scan sortie      : AUCUN motif bloquant résiduel")
}
