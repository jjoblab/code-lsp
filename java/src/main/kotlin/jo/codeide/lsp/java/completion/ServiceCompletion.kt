package jo.codeide.lsp.java.completion

import jo.codeide.lsp.java.classpath.EnvironnementNoms
import jo.codeide.lsp.java.classpath.GenreType
import jo.codeide.lsp.java.interne.ItemCompletion
import jo.codeide.lsp.java.interne.LectureAst
import jo.codeide.lsp.java.interne.NatureItem
import jo.codeide.lsp.java.interne.PontEcj
import jo.codeide.lsp.java.interne.UnitesSource
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration
import org.eclipse.jdt.internal.compiler.lookup.FieldBinding
import org.eclipse.jdt.internal.compiler.lookup.MethodBinding
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding
import org.eclipse.jdt.internal.compiler.lookup.TypeBinding

/**
 * Réponse de complétion (étage N2) : les items arrangés AVEC la publicité
 * honnête du total et le genre diagnostiqué (trace/banc de qualité).
 *
 * @property items les items ordonnés, plafonnés à l'affichage.
 * @property totalDisponible le nombre RÉEL de candidats correspondants.
 * @property genre le genre de contexte diagnostiqué au caret.
 */
public class ReponseCompletion(
    public val items: List<ItemCompletion>,
    public val totalDisponible: Int,
    public val genre: GenreContexte,
) {
    /** Vrai ssi la liste rendue a été raccourcie par le plafond. */
    public val tronquee: Boolean
        get() = totalDisponible > items.size
}

/**
 * Service de complétion & classement (étage N2 — PLAN-REECRITURE §4) :
 * le pipeline complet de la « capacité phare ».
 *
 * 1. **analyse** — sonde à chaud sur l'arbre original du cache (membre,
 *    référence de paquet), sinon classification par source marquée
 *    ([AnalyseurContexte]) ;
 * 2. **collecte** — les candidats du genre diagnostiqué : membres du
 *    récepteur, types visibles + traversée du classpath par préfixe
 *    (auto-import), règles d'or du filtre sous-paquetage (fiche 044),
 *    stubs d'override (fiche 007), noms dérivés du type, constantes
 *    d'enum, mots-clés en position de nom (fiche 008) ;
 * 3. **filtrage gradué** — [CorrespondanceGraduee] : le préfixe domine
 *    (bonus −200), la sous-chaîne n'est admise qu'à partir de 3 caractères ;
 * 4. **arrangement** — [ArrangementListe] : chaîne de peseurs (D8) + gel
 *    des têtes + plafond honnête de 120 items.
 *
 * Le service ne rend JAMAIS null et ne lève jamais : un contexte non
 * compris rend une liste VIDE honnête (fiche 035 — le contrat « jamais
 * null » qui corrigeait le « got 0 items » par NPE avalée).
 *
 * @param pontFournisseur le pont ecj du moteur (résolution marquée).
 * @param environnementFournisseur l'environnement de noms (annuaire).
 * @param arbreOriginal fournit l'arbre résolu de la source ORIGINALE
 *   (cache de parse) pour la sonde à chaud — null si indisponible.
 * @param chainePersonnalisee chaîne de classement de remplacement (BANC
 *   de qualité ADR 0016 : la baseline « naïve » est une chaîne
 *   alphabétique seule — null = chaîne standard D8 de l'étage).
 */
public class ServiceCompletion(
    private val pontFournisseur: () -> PontEcj,
    private val environnementFournisseur: () -> EnvironnementNoms,
    private val arbreOriginal: (String, String) -> CompilationUnitDeclaration?,
    private val chainePersonnalisee: ChaineClassement? = null,
) {

    /** Chaîne de classement (statistique désactivée par défaut ; banc : remplaçable). */
    private val chaine: ChaineClassement = chainePersonnalisee ?: ChaineClassement()

    /** Arrangement (gel + trim honnête) — un par moteur. */
    private val arrangement: ArrangementListe = ArrangementListe(chaine)

    /** Analyseur de contexte (paresseux — lié au pont et à l'environnement). */
    private val analyseur: AnalyseurContexte by lazy {
        AnalyseurContexte(pontFournisseur(), environnementFournisseur())
    }

    /**
     * Complète au caret.
     *
     * @param chemin chemin logique du document.
     * @param source le texte Java complet (null/vide : liste vide).
     * @param offset la position du caret.
     * @return la réponse (items + total + genre) — jamais nulle.
     */
    public fun completer(chemin: String, source: String?, offset: Int): ReponseCompletion {
        if (source.isNullOrEmpty()) return ReponseCompletion(emptyList(), 0, GenreContexte.INCONNU)
        return try {
            val contexte = analyserContexte(chemin, source, offset)
            val candidats = collecter(contexte, chemin, source, offset)
            val filtres = filtrer(candidats, contexte.prefixe)
            val cle = cleDeGel(chemin, contexte)
            val reponse = arrangement.arranger(cle, filtres, ContexteClassement(contexte.genre, contexte.prefixe))
            // Gabarits (étage N7, fiche 029) : injection POST-arrangement —
            // la chaîne D8 et le gel des têtes restent intacts ; la clé
            // exacte domine, la clé partielle reste discrète.
            val items = jo.codeide.lsp.java.gabarits.ServiceGabarits.enrichir(reponse.items, contexte, source, offset)
            ReponseCompletion(items, reponse.totalDisponible, contexte.genre)
        } catch (_: Exception) {
            // Aucune levée ne franchit la frontière du service (fiche 035) :
            // un échec interne rend une liste vide honnête.
            ReponseCompletion(emptyList(), 0, GenreContexte.INCONNU)
        }
    }

    /** Analyse du contexte : sonde à chaud d'abord, classification marquée ensuite. */
    private fun analyserContexte(chemin: String, source: String, offset: Int): ContexteCompletion {
        val arbre = arbreOriginal(chemin, source)
        val sonde = analyseur.sonde(arbre, source, offset)
        if (sonde != null) return sonde
        return analyseur.analyser(chemin, source, offset)
    }

    /** Clé de gel : document + genre + ancre (le récepteur ou le paquet tapé). */
    private fun cleDeGel(chemin: String, contexte: ContexteCompletion): String = buildString {
        append(chemin)
        append('|')
        append(contexte.genre.name)
        append('|')
        append(contexte.recepteur ?: contexte.paquetTape ?: "")
    }

    // ------------------------------------------------------------------ //
    // Collecte par genre
    // ------------------------------------------------------------------ //

    /** Collecte les candidats bruts du contexte. */
    private fun collecter(contexte: ContexteCompletion, chemin: String, source: String, offset: Int): List<ItemCompletion> {
        return when (contexte.genre) {
            GenreContexte.MEMBRE -> membresDe(contexte.typeRecepteur, statique = false)
            GenreContexte.MEMBRE_STATIQUE -> membresStatiques(contexte)
            GenreContexte.TYPE -> types(contexte)
            GenreContexte.NOM -> noms(contexte, chemin, source, offset)
            GenreContexte.IMPORT -> sousPaquetages(contexte)
            GenreContexte.REFERENCE_PAQUET -> sousPaquetages(contexte)
            GenreContexte.CORPS_CLASSE -> corpsDeClasse(contexte)
            GenreContexte.NOM_DECLARATION -> nomsDeDeclaration(contexte)
            GenreContexte.CASE_LABEL -> constantesDEnum(contexte.typeAttendu)
            GenreContexte.VALEUR_ANNOTATION -> constantesDEnum(contexte.typeAttendu)
            GenreContexte.INCONNU -> emptyList()
        }
    }

    /** Membres d'INSTANCE du récepteur (fiche 020 : la hiérarchie complète est balayée). */
    private fun membresDe(type: TypeBinding?, statique: Boolean): List<ItemCompletion> {
        val reference = type as? ReferenceBinding ?: return emptyList()
        val items = mutableListOf<ItemCompletion>()
        try {
            // La hiérarchie COMPLÈTE est balayée (fiche 020) : les méthodes
            // et champs hérités — jusqu'à Object — sont proposés. ecj ne le
            // fait PAS tout seul (`availableMethods` ne rend que les
            // DÉCLARÉES du type — constat du banc de qualité N2 : aucun
            // stub `toString()` sur un identifiant nu, `notify`/`wait`
            // absents des membres d'un String) ; la redéfinition la plus
            // DÉRIVÉE gagne (la collecte va du type vers les ancêtres et
            // le dédoublonnage garde la première occurrence).
            for (typeCourant in hierarchie(reference)) {
                for (champ in champsOuHerites(typeCourant)) {
                    if (statique && !champ.isStatic()) continue
                    if (!statique && champ.isStatic()) continue
                    items.add(
                        ItemCompletion(
                            libelle = String(champ.name),
                            nature = NatureItem.CHAMP,
                            detail = LectureAst.lisible(champ.type),
                        ),
                    )
                }
                for (methode in methodesOuHeritees(typeCourant)) {
                    if (methode.isConstructor()) continue
                    if (statique && !methode.isStatic()) continue
                    if (!statique && methode.isStatic()) continue
                    items.add(
                        ItemCompletion(
                            libelle = String(methode.selector),
                            nature = NatureItem.METHODE,
                            detail = signature(methode),
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            // binding incomplet : ce qui a été collecté suffit
        }
        return items.distinctBy { it.libelle to it.nature }
    }

    /**
     * Hiérarchie complète d'un type : lui-même, ses superclasses (jusqu'à
     * Object) et ses interfaces (directes et héritées) — l'ordre va du plus
     * DÉRIVÉ au plus ancien (la première occurrence d'un nom lors du
     * dédoublonnage est la redéfinition la plus dérivée).
     *
     * Les types d'ecj sont protégés contre les cycles par une garde
     * d'identité (le recovery peut fabriquer des hiérarchies bouclées —
     * jamais de boucle infinie).
     */
    private fun hierarchie(base: ReferenceBinding): List<ReferenceBinding> {
        val resultat = mutableListOf<ReferenceBinding>()
        val vus = java.util.IdentityHashMap<ReferenceBinding, Boolean>()
        fun visiter(type: ReferenceBinding?) {
            if (type == null || vus.containsKey(type)) return
            vus[type] = true
            resultat.add(type)
            try {
                visiter(type.superclass())
                for (interface_ in type.superInterfaces() ?: emptyArray()) {
                    visiter(interface_)
                }
            } catch (_: Exception) {
                // hiérarchie non connectée : le type seul suffit
            }
        }
        visiter(base)
        return resultat
    }

    /** Champs DÉCLARÉS d'un type (défensif — liaison incomplète tolérée). */
    private fun champsOuHerites(type: ReferenceBinding): Array<org.eclipse.jdt.internal.compiler.lookup.FieldBinding> =
        try {
            type.availableFields() ?: emptyArray()
        } catch (_: Exception) {
            emptyArray()
        }

    /** Méthodes DÉCLARÉES d'un type (défensif — liaison incomplète tolérée). */
    private fun methodesOuHeritees(type: ReferenceBinding): Array<MethodBinding> =
        try {
            type.availableMethods() ?: emptyArray()
        } catch (_: Exception) {
            emptyArray()
        }

    /** Membres STATIQUES du qualifier de type (fiche 020 : jamais une méthode d'instance). */
    private fun membresStatiques(contexte: ContexteCompletion): List<ItemCompletion> {
        val fqcn = contexte.recepteurFqcn ?: return emptyList()
        val binding = bindingDe(fqcn) ?: return emptyList()
        return membresDe(binding, statique = true)
    }

    /** Signature lisible d'une méthode (détail de l'item). */
    private fun signature(methode: MethodBinding): String = buildString {
        append(LectureAst.lisible(methode.returnType) ?: "?")
        append(' ')
        append(String(methode.selector))
        append('(')
        append((methode.parameters ?: emptyArray()).joinToString(", ") { LectureAst.lisible(it) ?: "?" })
        append(')')
    }

    /** Types proposés en position de TYPE (fiche 007/008/035). */
    private fun types(contexte: ContexteCompletion): List<ItemCompletion> {
        val items = mutableListOf<ItemCompletion>()
        val visibles = typesVisibles(contexte)
        for ((nomSimple, fqcn) in visibles) {
            items.add(itemType(nomSimple, fqcn, autoImport = null))
        }
        if (contexte.prefixe.isNotEmpty()) {
            val annuaire = environnementFournisseur().annuaire
            for (entree in annuaire.typesParPrefixe(contexte.prefixe, LIMITE_COLLECTE)) {
                if (visibles.any { it.first == entree.nomSimple }) continue
                items.add(itemType(entree.nomSimple, entree.fqcn, autoImport = entree.fqcn))
            }
        }
        return filtrerGrammaticalement(items, contexte.filtre)
    }

    /** Item de type (kind par genre binaire lu à la demande, marqueur annotation). */
    private fun itemType(nomSimple: String, fqcn: String, autoImport: String?): ItemCompletion {
        val genre = try {
            environnementFournisseur().annuaire.genreDe(fqcn)
        } catch (_: Exception) {
            null
        }
        val nature = when (genre) {
            GenreType.INTERFACE -> NatureItem.INTERFACE
            GenreType.ANNOTATION -> NatureItem.ANNOTATION
            else -> NatureItem.CLASSE
        }
        return ItemCompletion(
            libelle = nomSimple,
            nature = nature,
            detail = fqcn,
            autoImportFqn = autoImport,
        )
    }

    /** Types VISIBLES sans import : paquet courant, imports (exacts et `*`), java.lang. */
    private fun typesVisibles(contexte: ContexteCompletion): List<Pair<String, String>> {
        val annuaire = environnementFournisseur().annuaire
        val resultats = mutableMapOf<String, String>()
        contexte.paquetCourant?.let { paquet ->
            for (entree in annuaire.typesDirects(paquet)) {
                resultats[entree.nomSimple] = entree.fqcn
            }
        }
        for (fqcnImport in contexte.importsFqcn) {
            if (fqcnImport.endsWith(".*")) {
                val paquet = fqcnImport.removeSuffix(".*")
                for (entree in annuaire.typesDirects(paquet)) {
                    resultats[entree.nomSimple] = entree.fqcn
                }
            } else {
                val nomSimple = fqcnImport.substringAfterLast('.')
                resultats[nomSimple] = fqcnImport
            }
        }
        for (entree in annuaire.typesDirects("java.lang")) {
            resultats[entree.nomSimple] = entree.fqcn
        }
        return resultats.map { (nom, fqcn) -> nom to fqcn }
    }

    /** Filtrage grammatical des types (fiche 007 : la position filtre le légal). */
    private fun filtrerGrammaticalement(items: List<ItemCompletion>, filtre: FiltreGrammatical?): List<ItemCompletion> {
        if (filtre == null) return items
        val annuaire = environnementFournisseur().annuaire
        return items.filter { item ->
            val fqcn = item.autoImportFqn ?: item.detail ?: return@filter true
            val genre = try {
                annuaire.genreDe(fqcn)
            } catch (_: Exception) {
                null
            } ?: return@filter true
            val throwable = when (filtre) {
                FiltreGrammatical.THROWABLES_SEULES ->
                    try {
                        annuaire.estThrowable(fqcn)
                    } catch (_: Exception) {
                        false
                    }

                else -> false
            }
            when (filtre) {
                FiltreGrammatical.CLASSES_SEULES -> genre == GenreType.CLASSE || genre == GenreType.CLASSE_ABSTRAITE
                FiltreGrammatical.INTERFACES_SEULES -> genre == GenreType.INTERFACE
                FiltreGrammatical.THROWABLES_SEULES -> throwable
                FiltreGrammatical.INSTANCIABLES_SEULES -> genre == GenreType.CLASSE
                FiltreGrammatical.ANNOTATIONS_SEULES -> genre == GenreType.ANNOTATION
                null -> true
            }
        }
    }

    /** Complétion de NOM : inventaire du fichier + types + mots-clés (fiche 008). */
    private fun noms(contexte: ContexteCompletion, chemin: String, source: String, offset: Int): List<ItemCompletion> {
        val items = mutableListOf<ItemCompletion>()
        items.addAll(types(contexte.copy(genre = GenreContexte.TYPE, filtre = null)))
        // Inventaire du fichier : champs, méthodes, locales, paramètres (le
        // marqueur a résolu l'unité — l'inventaire se lit sur l'original).
        items.addAll(inventaireOriginal(chemin, source, offset))
        for (mot in LectureAst.MOTS_CLES) {
            items.add(ItemCompletion(mot, NatureItem.MOT_CLE))
        }
        return items
    }

    /** Inventaire du fichier courant (complétion instantanée N1 réutilisée sur l'arbre original). */
    private fun inventaireOriginal(chemin: String, source: String, offset: Int): List<ItemCompletion> {
        val arbre = arbreOriginal(chemin, source) ?: return emptyList()
        return LectureAst.inventaireFichier(arbre, offset)
    }

    /**
     * Règles d'or du filtre sous-paquetage (fiche 044) : segments ENFANTS
     * DIRECTS dédoublonnés (kind PAQUET, insertion du SEGMENT simple, détail
     * FQN) + types DIRECTS du paquet (jamais d'auto-import — règle 4).
     */
    private fun sousPaquetages(contexte: ContexteCompletion): List<ItemCompletion> {
        val paquet = contexte.paquetTape ?: ""
        val annuaire = environnementFournisseur().annuaire
        val items = mutableListOf<ItemCompletion>()
        for (segment in annuaire.segmentsEnfants(paquet)) {
            val fqn = if (paquet.isEmpty()) segment else "$paquet.$segment"
            items.add(
                ItemCompletion(
                    libelle = segment,
                    nature = NatureItem.PAQUET,
                    texteInsertion = segment,
                    detail = fqn,
                ),
            )
        }
        if (paquet.isNotEmpty()) {
            for (entree in annuaire.typesDirects(paquet)) {
                items.add(itemType(entree.nomSimple, entree.fqcn, autoImport = null))
            }
        }
        return items
    }

    /** Stubs d'override en position de membre de classe (fiche 007). */
    private fun corpsDeClasse(contexte: ContexteCompletion): List<ItemCompletion> {
        val items = mutableListOf<ItemCompletion>()
        val englobant = contexte.typeEnglobant
        if (englobant != null) {
            val vus = mutableSetOf<String>()
            try {
                // Stubs d'override de TOUTE la hiérarchie (fiche 007) : le
                // point même du stub est de redéfinir une méthode HÉRITÉE —
                // toString() sur `toStr|` ne vit dans aucune méthode
                // déclarée du type (constat du banc de qualité N2). La
                // redéfinition la plus dérivée est proposée (première
                // occurrence du sélecteur dans le balayage).
                for (typeCourant in hierarchie(englobant)) {
                    for (methode in methodesOuHeritees(typeCourant)) {
                        if (methode.isConstructor()) continue
                        if (methode.isStatic()) continue
                        if (methode.isPrivate()) continue
                        if (methode.isFinal()) continue
                        val nom = String(methode.selector)
                        if (nom in vus) continue
                        vus.add(nom)
                        items.add(stubOverride(methode))
                    }
                }
            } catch (_: Exception) {
                // hiérarchie incomplète : les stubs déjà collectés suffisent
            }
        }
        // Les types restent proposés en corps de classe (déclarations imbriquées).
        items.addAll(types(contexte.copy(genre = GenreContexte.TYPE, filtre = null)))
        return items
    }

    /** Construit le snippet de stub d'override (fiche 007 : `$0` final obligatoire). */
    private fun stubOverride(methode: MethodBinding): ItemCompletion {
        val nom = String(methode.selector)
        val retour = LectureAst.lisible(methode.returnType) ?: "void"
        val parametres = (methode.parameters ?: emptyArray()).joinToString(", ") { LectureAst.lisible(it) ?: "?" }
        val estInterface = try {
            methode.declaringClass?.isInterface() == true
        } catch (_: Exception) {
            false
        }
        val corps = if (estInterface) {
            "throw new UnsupportedOperationException();"
        } else {
            "super.$nom();"
        }
        val insertion = buildString {
            append("@Override\n")
            append("public ")
            append(if (retour == "void") "void" else retour)
            append(' ')
            append(nom)
            append('(')
            append(parametres)
            append(") {\n    ")
            append(corps)
            append("\n    $0\n}")
        }
        return ItemCompletion(
            libelle = "$nom($parametres)",
            nature = NatureItem.EXTRAIT,
            texteInsertion = insertion,
            detail = signature(methode),
            extrait = true,
        )
    }

    /** Noms dérivés du type déclarant (fiche 007 : lowerFirst complet + suffixe court). */
    private fun nomsDeDeclaration(contexte: ContexteCompletion): List<ItemCompletion> {
        val type = contexte.typeAttendu ?: return emptyList()
        val lisible = LectureAst.lisible(type) ?: return emptyList()
        val nomSimple = lisible.substringAfterLast('.').substringAfterLast('$')
        if (nomSimple.isEmpty() || !nomSimple[0].isJavaIdentifierStart()) return emptyList()
        val complet = nomSimple.replaceFirstChar { it.lowercaseChar() }
        val mots = couperCamel(nomSimple)
        val court = mots.last().replaceFirstChar { it.lowercaseChar() }
        val suggestions = linkedSetOf(complet)
        if (mots.size > 1) suggestions.add(court)
        return suggestions.map { ItemCompletion(it, NatureItem.VARIABLE) }
    }

    /** Découpe camel-case (`HttpClient` → `Http` + `Client`). */
    private fun couperCamel(nom: String): List<String> {
        val mots = mutableListOf<String>()
        val courant = StringBuilder()
        for (caractere in nom) {
            if (courant.isNotEmpty() && caractere.isUpperCase()) {
                mots.add(courant.toString())
                courant.setLength(0)
            }
            courant.append(caractere)
        }
        if (courant.isNotEmpty()) mots.add(courant.toString())
        return mots
    }

    /** Constantes d'un enum (fiche 007 : labels de switch, valeurs d'annotation — kind ENUM). */
    private fun constantesDEnum(type: TypeBinding?): List<ItemCompletion> {
        val reference = type as? ReferenceBinding ?: return emptyList()
        val items = mutableListOf<ItemCompletion>()
        try {
            for (champ in reference.fields() ?: emptyArray()) {
                val nom = String(champ.name)
                if (nom.startsWith("$")) continue
                // Une constante d'enum a le TYPE de l'enum lui-même (les
                // tableaux synthétiques $VALUES sont écartés par le test type).
                if (champ.type != reference) continue
                items.add(ItemCompletion(nom, NatureItem.CONSTANTE_ENUM))
            }
        } catch (_: Exception) {
            // enum binaire fautif : repli par lecture du fichier de classe
            val fqcn = LectureAst.lisible(reference)
            if (fqcn != null) {
                for (nom in environnementFournisseur().annuaire.constantesEnum(fqcn)) {
                    items.add(ItemCompletion(nom, NatureItem.CONSTANTE_ENUM))
                }
            }
        }
        if (items.isEmpty()) {
            val fqcn = LectureAst.lisible(reference)
            if (fqcn != null) {
                for (nom in environnementFournisseur().annuaire.constantesEnum(fqcn)) {
                    items.add(ItemCompletion(nom, NatureItem.CONSTANTE_ENUM))
                }
            }
        }
        return items
    }

    // ------------------------------------------------------------------ //
    // Filtrage gradué et divers
    // ------------------------------------------------------------------ //

    /** Filtre les candidats par correspondance graduée au préfixe (le grade voyage sur l'item). */
    private fun filtrer(candidats: List<ItemCompletion>, prefixe: String): List<ItemCompletion> =
        candidats.mapNotNull { item ->
            val grade = CorrespondanceGraduee.grade(prefixe, item.libelle)
            if (grade == GradeCorrespondance.AUCUN) return@mapNotNull null
            item.copy(correspondance = grade)
        }

    /** Résout le binding d'un FQCN (passe dédiée — qualifiers statiques). */
    private fun bindingDe(fqcn: String): ReferenceBinding? {
        return try {
            val source = "class __Sonde__ { void m() { $fqcn x = null; } }"
            val reponse = pontFournisseur().resoudre(
                UnitesSource.EnMemoire("/__sonde__.java", source),
                genererCode = false,
            )
            var resultat: ReferenceBinding? = null
            val collecteur = object : LectureAst.VisiteurResilient() {
                override fun visiteLocale(node: org.eclipse.jdt.internal.compiler.ast.LocalDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
                    resultat = node.type?.resolvedType as? ReferenceBinding
                    return true
                }
            }
            reponse.arbre?.traverse(collecteur, reponse.arbre?.scope)
            resultat
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        /** Borne de la traversée du classpath par préfixe (le plafond d'affichage est en aval). */
        private const val LIMITE_COLLECTE: Int = 800
    }
}
