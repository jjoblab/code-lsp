package jo.codeide.lsp.java.projetandroid

import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong
import jo.codeide.lsp.java.classpath.TypesSynthetiques

/**
 * Fabrique et ANNEXE des classes light Android d'un module (vague V-B —
 * ADR 0021 option 1, décision D2) : `R` et ses classes internes par type,
 * `BuildConfig` — les classes `*Binding` (V-B3) s'y raccorderont par le
 * même contrat [TypesSynthetiques].
 *
 * **Règles AGP reproduites** (§B9 de la référence, vérifiées sur les cas
 * dorés du banc) :
 *  - `<ns>.R` final, constructeur privé ; classes internes `public static
 *    final` par type (`R$layout`, `R$id`…), constructeurs privés ;
 *  - champs `public static final int` — NON finaux si `nonFinalResIds`
 *    (valeur = constante émise, ordre STABLE par nom pour l'idempotence) ;
 *  - styleables : TROIS familles (`X`, `X_attr`, indices) ;
 *  - R non transitif (AGP 8) : seules les ressources DU module — le
 *    référentiel ne scanne que SES dossiers `res/` ;
 *  - `BuildConfig` n'existe QUE si `buildFeatures.buildConfig` (AGP 8 :
 *    désactivé par défaut) : `DEBUG`, `APPLICATION_ID`, `BUILD_TYPE`,
 *    `FLAVOR`, `VERSION_CODE`, `VERSION_NAME` + champs `buildConfigField`.
 *
 * **Anti-doublon (R3)** : la règle vit dans l'ORDRE DE REPLI de
 * l'environnement (sources d'abord, la source générée réelle PRIME) et
 * dans l'annuaire (enregistrement après les sources) — cette fabrique ne
 * masque jamais un build passé.
 *
 * **Fraîcheur** : [generation] est incrémentée à chaque MAJ ciblée du
 * référentiel (didChange XML) — le mémo de diagnose du moteur l'enregistre
 * comme dépendance virtuelle : un layout édité invalide les diagnostics
 * même si AUCUN `.java` n'a changé (P8 étendu aux ressources).
 *
 * @param contrat le contrat Android du module (namespace, drapeaux).
 * @param referentiel le référentiel de ressources (source de vérité des noms).
 */
public class ClassesLegereAndroid(
    public val contrat: ContratAndroidMoteur,
    public val referentiel: ReferentielRessources,
) : TypesSynthetiques {

    /** Compteur de fraîcheur (incrémenté par [rafraichirFichierXml]). */
    private val compteur = AtomicLong(0)

    /** Octets déjà fabriqués, par FQCN (fabrication paresseuse, une fois). */
    private val octetsParFqcn = HashMap<String, ByteArray>()

    /** Les types énumérés (annuaire), paresseux. */
    @Volatile
    private var catalogue: Map<String, List<String>>? = null

    // ------------------------------------------------------------------ //
    // Contract TypesSynthetiques
    // ------------------------------------------------------------------ //

    /**
     * Les octets du fichier de classe d'un type light — `R`, `R$<type>`,
     * `BuildConfig` ; null pour tout autre FQCN.
     */
    override fun octetsPour(fqcn: String): ByteArray? {
        octetsParFqcn[fqcn]?.let { return it }
        val fabrique = when {
            fqcn == "${contrat.namespace}.R" -> fabriquerR()
            fqcn.startsWith("${contrat.namespace}.R$") -> fabriquerImbriqueeR(fqcn.substringAfterLast('$'))
            fqcn == "${contrat.namespace}.BuildConfig" && contrat.buildConfig -> fabriquerBuildConfig()
            else -> null
        } ?: return null
        octetsParFqcn[fqcn] = fabrique
        return fabrique
    }

    /** Les types light par paquet (annuaire de complétion). */
    override fun typesParPaquet(): Map<String, List<String>> {
        catalogue?.let { return it }
        val resultat = HashMap<String, MutableList<String>>()
        if (contrat.namespace.isNotEmpty()) {
            // Les imbriquées R$* ne sont JAMAIS proposées comme types de
            // premier niveau (règle `$` du filtre, fiches 032/036) — seuls
            // R et BuildConfig peuplent le paquet du namespace.
            resultat[contrat.namespace] = mutableListOf<String>().apply {
                add("R")
                if (contrat.buildConfig) add("BuildConfig")
            }
        }
        catalogue = resultat
        return resultat
    }

    /** Les paquets porteurs de types light (`ns`, `ns.databinding`). */
    override fun estPaquet(nomPaquet: String): Boolean = nomPaquet == contrat.namespace

    /**
     * La cible de NAVIGATION d'un champ light : `R.layout.x` → le XML du
     * layout, `R.id.x` → la déclaration `@+id/x`, `R.string.x` → le
     * `<string name="x">` (référence §B8 : la navigation ressource est
     * EXACTE, jamais approximative).
     */
    override fun cibleChamp(
        fqcnDeclarant: String,
        champ: String,
    ): Pair<Path, Int>? {
        if (!fqcnDeclarant.startsWith("${contrat.namespace}.R$")) return null
        val type = fqcnDeclarant.substringAfterLast('$')
        if (type == "styleable" && champ.contains('_')) {
            val styleable = champ.substringBefore('_')
            val attribut = champ.substringAfter('_')
            return referentiel.cibleStyleable(styleable, attribut)
        }
        return referentiel.cible(type, champ)
    }

    // ------------------------------------------------------------------ //
    // Fraîcheur (P8 étendu aux ressources)
    // ------------------------------------------------------------------ //

    /** La génération courante du référentiel (sonde de fraîcheur du mémo). */
    public fun generation(): Long = compteur.get()

    /**
     * MAJ ciblée : un fichier XML de ressource a changé — le référentiel
     * le re-scanne, la génération est incrémentée (les octets light déjà
     * fabriqués sont OUBLIÉS : la prochaine résolution re-fabrique).
     *
     * @param fichier le fichier XML modifié.
     */
    public fun rafraichirFichierXml(fichier: Path) {
        referentiel.rafraichirFichier(fichier)
        compteur.incrementAndGet()
        octetsParFqcn.clear()
        catalogue = null
    }

    // ------------------------------------------------------------------ //
    // Fabrication
    // ------------------------------------------------------------------ //

    /** Fabrique `<ns>.R` (coquille finale, constructeur privé, imbriquées
     *  déclarées par l'attribut InnerClasses — sans lui ecj refuse `R.layout`). */
    private fun fabriquerR(): ByteArray {
        val ecrivain = EcrivainClasseLegere.pour("${contrat.namespace}.R")
        ecrivain.finale()
        ecrivain.constructeurPrive()
        for (type in referentiel.typesConnus()) {
            ecrivain.imbriquee(type, "${contrat.namespace}.R")
        }
        return ecrivain.octets()
    }

    /** Fabrique une classe interne `R$<type>` (champs int par nom). */
    private fun fabriquerImbriqueeR(type: String): ByteArray {
        val ecrivain = EcrivainClasseLegere.pour("${contrat.namespace}.R$$type")
        ecrivain.finale()
        ecrivain.constructeurPrive()
        // L'imbriquée porte AUSSI son entrée InnerClasses (convention javac).
        ecrivain.imbriquee(type, "${contrat.namespace}.R")
        var rang = 0
        for (nom in referentiel.noms(type)) {
            ecrivain.champ(
                nom = nom,
                descripteur = "I",
                statique = true,
                finalChamp = !contrat.nonFinalResIds,
                constante = baseDeType(type) + rang,
            )
            rang++
        }
        return ecrivain.octets()
    }

    /** Fabrique `<ns>.BuildConfig` (uniquement si activé — AGP 8). */
    private fun fabriquerBuildConfig(): ByteArray {
        val ecrivain = EcrivainClasseLegere.pour("${contrat.namespace}.BuildConfig")
        ecrivain.finale()
        ecrivain.constructeurPrive()
        ecrivain.champ("DEBUG", "Z", constante = true)
        ecrivain.champ("APPLICATION_ID", "Ljava/lang/String;", constante = contrat.applicationId ?: contrat.namespace)
        ecrivain.champ("BUILD_TYPE", "Ljava/lang/String;", constante = "debug")
        ecrivain.champ("FLAVOR", "Ljava/lang/String;", constante = "")
        ecrivain.champ("VERSION_CODE", "I", constante = 1)
        ecrivain.champ("VERSION_NAME", "Ljava/lang/String;", constante = "1.0")
        for ((nom, declaration) in contrat.buildConfigFields) {
            val morceaux = declaration.trim().split(Regex("\\s+"), 2)
            if (morceaux.size != 2) continue
            val type = morceaux[0]
            val valeur = morceaux[1].trim().removeSurrounding("\"")
            // Types numériques illisibles ÉCARTÉS honnêtement (une constante
            // fausse serait un faux positif — règle conservatrice ADR 0022).
            val constante: Any? = when (type) {
                "int" -> valeur.toIntOrNull()
                "long" -> valeur.toLongOrNull()
                "boolean" -> valeur.toBooleanStrictOrNull()
                "String" -> valeur
                else -> null
            }
            if (constante == null) continue
            ecrivain.champ(
                nom = nom,
                descripteur = EcrivainClasseLegere.descripteurDe(type),
                constante = constante,
            )
        }
        return ecrivain.octets()
    }

    /** Base déterministe des valeurs de R par type (ordre stable par nom). */
    private fun baseDeType(type: String): Int = 0x7f0000 + (BASES_PAR_TYPE[type] ?: 0x00) * 0x100

    /** Les préfixes de type usuels d'aapt (stabilité des valeurs émises). */
    private companion object {
        private val BASES_PAR_TYPE: Map<String, Int> =
            mapOf(
                "anim" to 0x01, "animator" to 0x02, "array" to 0x03, "attr" to 0x04,
                "bool" to 0x05, "color" to 0x06, "dimen" to 0x07, "drawable" to 0x08,
                "font" to 0x09, "fraction" to 0x0a, "id" to 0x0b, "integer" to 0x0c,
                "interpolator" to 0x0d, "layout" to 0x0e, "menu" to 0x0f, "mipmap" to 0x10,
                "navigation" to 0x11, "plurals" to 0x12, "raw" to 0x13, "string" to 0x14,
                "style" to 0x15, "styleable" to 0x16, "transition" to 0x17, "xml" to 0x18,
            )
    }
}
