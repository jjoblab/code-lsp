package jo.codeide.lsp.java.classpath

/**
 * Stubs embarqués des intrinsèques du compilateur (étage N1, cahier fiche 042).
 *
 * Contexte de production reproduit par le contrat : sur un appareil sans
 * image jrt, un `.class` binaire du classpath peut référencer
 * `java.lang.invoke.StringConcatFactory` (chaque concaténation compilée par
 * javac ≥ 9 laisse un `CONSTANT_MethodHandle` vers `makeConcatWithConstants`)
 * — la classe n'existe dans AUCUNE plateforme Android (vérifié API 31 et 36 :
 * D8 désugare toujours les concaténations invokedynamic). Sans filet, ecj
 * publie le faux positif « The type java.lang.invoke.StringConcatFactory
 * cannot be resolved. It is indirectly referenced from required .class
 * files », qui dégrade toute la hiérarchie du type édité.
 *
 * Correctif verrouillé par le cahier : une liste **fermée** de trois
 * intrinsèques, servis en dernier repli — APRÈS les racines sources, les
 * jars du classpath et l'image jrt, et uniquement pour ces FQCN. Le stub ne
 * masque JAMAIS un android.jar qui contiendrait la classe ni un jdkHome
 * chargé : c'est un filet structurel, pas un remplacement de plateforme.
 *
 * Les octets sont produits par [FabriqueClasseBinaire] (version 52, magic
 * `CA FE BA BE`) — jamais chargés dans la JVM, uniquement consommés par le
 * lecteur de classes d'ecj.
 */
public object StubsIntrinseques {

    /**
     * La liste fermée des intrinsèques stubées.
     *
     * L'appartenance est un contrat observable (fiche 042) : `estIntrinseque`
     * est FAUX pour les types de plateforme (`java.lang.String`,
     * `java.util.List`) et VRAI pour exactement ces trois FQCN.
     */
    private val FQCNS_INTRINSEQUES: Set<String> = setOf(
        "java.lang.invoke.StringConcatFactory",
        "java.lang.runtime.ObjectMethods",
        "java.lang.runtime.SwitchBootstraps",
    )

    /** Octets mis en cache paresseusement (trois classes de ~200 octets). */
    private val cache = HashMap<String, ByteArray>()

    /**
     * Indique si un FQCN appartient à la liste fermée des intrinsèques.
     *
     * @param fqcn nom entièrement qualifié (points).
     * @return vrai ssi [octets] fournira un stub pour ce FQCN.
     */
    @JvmStatic
    public fun estIntrinseque(fqcn: String): Boolean = fqcn in FQCNS_INTRINSEQUES

    /**
     * Rend les octets du stub d'un intrinsèque.
     *
     * @param fqcn nom entièrement qualifié (points).
     * @return les octets du fichier de classe (magic `CA FE BA BE`, version
     *   majeure 52) ; **null pour tout autre FQCN** — jamais de stub d'un
     *   type de plateforme (contrat fiche 042 : `octets("java.lang.String")`
     *   rend null).
     */
    @JvmStatic
    public fun octets(fqcn: String): ByteArray? {
        if (!estIntrinseque(fqcn)) return null
        return cache.getOrPut(fqcn) {
            FabriqueClasseBinaire.octets(fqcn, constructeurPublic = false)
        }
    }
}
