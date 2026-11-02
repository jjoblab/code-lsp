package jo.codeide.lsp.java.navigation

import jo.codeide.lsp.java.classpath.EnvironnementNoms
import jo.codeide.lsp.java.diagnostics.Plage
import jo.codeide.lsp.java.hierarchie.CibleTypeHierarchie
import jo.codeide.lsp.java.interne.LectureAst
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration
import org.eclipse.jdt.internal.compiler.ast.FieldDeclaration
import org.eclipse.jdt.internal.compiler.ast.MethodDeclaration
import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration
import org.eclipse.jdt.internal.compiler.lookup.FieldBinding
import org.eclipse.jdt.internal.compiler.lookup.LocalVariableBinding
import org.eclipse.jdt.internal.compiler.lookup.MethodBinding
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * Routeur de navigation (étage N4 — cahier fiches 049/060/062/005) : le
 * panneau GO TO du moteur.
 *
 * Discipline des TROIS CHEMINS (fiche 049) — l'ordre est verrouillé et
 * chaque requête rend SON régime, sans fuite :
 *
 *  1. **intra-fichier** : la déclaration visée vit dans l'unité courante
 *     (registre des déclarations de l'arbre — la locale par lien direct
 *     de binding, le champ/la méthode/le type par registre) ;
 *  2. **projet** : le type DÉCLARANT se localise par l'annuaire de noms
 *     (sonde disque des racines), le fichier est lu et résolu, la cible
 *     est la plage du JETON nom dans SA source — le document interrogé
 *     peut ne pas être ouvert : la cible vit sur le DISQUE (l'overlay
 *     tampons-ouverts est porté par l'appelant : l'unité courante est
 *     résolue sur la source DONNÉE, jamais relue au disque) ;
 *  3. **binaire** : le type déclarant vit hors des sources (jar, jrt,
 *     stub intrinsèque) — l'URI `jdt://decompiled/<chemin>.java` et une
 *     plage VIDE `0:0–0:0` : le câble GO TO binaire (fiche 062 : le
 *     client appelle ensuite `decompiledSource(fqcn)`).
 *
 * Une cible PROJET n'atterrit JAMAIS dans le stub décompilé ; une cible
 * BINAIRE n'atterrit jamais dans les sources (fiche 049).
 *
 * Les plages rendues pour l'unité COURANTE sont en coordonnées de la
 * source de l'arbre interrogé — l'appelant (moteur) les remappe vers la
 * source ORIGINALE quand la résolution passait par une source RÉDUITE
 * (SourceFocuser, fiche 062).
 *
 * @param resoudre résout une unité (chemin logique, texte) en arbre
 *   bindé — la même résolution que le reste du moteur.
 * @param localiser localise un FQN (annuaire de noms du moteur).
 * @param sousTypesDeFqn les sous-types DIRECTS d'un FQN (balayage des
 *   racines — le service de hiérarchies N7).
 * @param cibleChampSynthetique résout la cible XML d'un champ d'une classe
 *   light (V-B2 — `R.layout.x` → le fichier déclarant + la plage du jeton) ;
 *   null = aucun champ synthétique sur ce moteur.
 */
public class ServiceNavigation(
    private val resoudre: (String, String) -> CompilationUnitDeclaration?,
    private val localiser: (String) -> EnvironnementNoms.Localisation?,
    private val sousTypesDeFqn: (String) -> List<CibleTypeHierarchie>,
    private val cibleChampSynthetique: ((fqcnDeclarant: String, champ: String) -> jo.codeide.lsp.java.classpath.TypesSynthetiques.CibleChampSynthetique?)? = null,
) {

    // ------------------------------------------------------------------ //
    // GO TO definition
    // ------------------------------------------------------------------ //

    /**
     * Définition de la référence au caret (fiche 049) : la cible de GO TO
     * — exactement UNE cible par requête (1 cible exactement — le binding
     * départage les homonymes par l'arité).
     *
     * @param chemin le chemin logique du document interrogé.
     * @param arbre l'arbre RÉSOLU du document (source de l'arbre : [sourceArbre]).
     * @param sourceArbre le texte dans le système de l'arbre.
     * @param position la position interrogée (dans le système de l'arbre).
     * @return la liste des cibles (vide honnête — jamais d'exception, la
     *   fiche 049 exige « résultat NON NULL avec une liste VIDE »).
     */
    public fun definition(
        chemin: String,
        arbre: CompilationUnitDeclaration?,
        sourceArbre: String,
        position: Int,
    ): List<CibleNavigation> {
        val symbole = symboleEn(arbre, sourceArbre, position) ?: return emptyList()
        return when (symbole.nature) {
            NatureSymbole.LOCALE -> {
                val locale = symbole.bindingEcj as? LocalVariableBinding ?: return emptyList()
                cibleLocale(chemin, locale)
            }
            NatureSymbole.METHODE -> cibleMethode(chemin, arbre, sourceArbre, symbole)
            NatureSymbole.CHAMP -> cibleChamp(chemin, arbre, sourceArbre, symbole)
            NatureSymbole.TYPE -> cibleType(chemin, arbre, sourceArbre, symbole.fqnTypeDuSymbole ?: return emptyList())
        }
    }

    // ------------------------------------------------------------------ //
    // GO TO typeDefinition
    // ------------------------------------------------------------------ //

    /**
     * Définition du TYPE du symbole au caret (fiche 062) : la variable
     * locale vise la déclaration de SON type ; la référence de type se
     * vise elle-même ; le type BINAIRE rend l'URI synthétique
     * `jdt://decompiled/<chemin>.java` à plage VIDE ; le type de base
     * (`int`) rend une liste VIDE — « un type de base n'a pas de
     * déclaration ».
     *
     * @param chemin le chemin logique du document interrogé.
     * @param arbre l'arbre résolu du document.
     * @param sourceArbre le texte dans le système de l'arbre.
     * @param position la position interrogée.
     * @return les cibles (0 ou 1 — exactement 1 pour un type résolvable).
     */
    public fun typeDefinition(
        chemin: String,
        arbre: CompilationUnitDeclaration?,
        sourceArbre: String,
        position: Int,
    ): List<CibleNavigation> {
        val symbole = symboleEn(arbre, sourceArbre, position) ?: return emptyList()
        val fqnType = symbole.fqnTypeDuSymbole ?: return emptyList()
        return cibleType(chemin, arbre, sourceArbre, fqnType)
    }

    // ------------------------------------------------------------------ //
    // GO TO implementation
    // ------------------------------------------------------------------ //

    /**
     * Implémentations du type au caret (fiche 060) : les héritiers et
     * implémenteurs DIRECTS du type en contexte — balayage inter-fichiers
     * des racines sources (délégué au service de hiérarchies N7), y
     * compris une sous-classe IMBRIQUÉE du même fichier.
     *
     * Convention de contexte (fiche 060) : un caret SANS référence de type
     * (champ, corps vide) retombe sur la classe ENGLOBANTE.
     *
     * @param chemin le chemin logique du document interrogé.
     * @param arbre l'arbre résolu du document.
     * @param sourceArbre le texte dans le système de l'arbre.
     * @param position la position interrogée.
     * @return les cibles (une par héritier direct, sur le NOM de classe).
     */
    public fun implementation(
        chemin: String,
        arbre: CompilationUnitDeclaration?,
        sourceArbre: String,
        position: Int,
    ): List<CibleNavigation> {
        if (arbre == null) return emptyList()
        val typeEnContexte = typeAuCaretOuEnglobant(arbre, sourceArbre, position) ?: return emptyList()
        val fqn = fqnCompletDe(typeEnContexte) ?: return emptyList()
        return sousTypesDeFqn(fqn).map { sousType ->
            CibleNavigation(
                genre = GenreCible.SOURCE_PROJET,
                chemin = sousType.chemin,
                uri = "file://${sousType.chemin}",
                plage = sousType.plageNom,
                nomSimple = sousType.nomSimple,
            )
        }
    }

    // ------------------------------------------------------------------ //
    // GO TO superDefinition
    // ------------------------------------------------------------------ //

    /**
     * Super au caret (fiches 049/060) : le membre OUTREPASSÉ dans le
     * supertype (libellé « nom  ·  ClasseDéclarante »), sinon les
     * supertypes DIRECTS du type en contexte (libellé « Nom  ·  paquet »)
     * — un supertype BINAIRE ne rend AUCUNE cible source (règle D3).
     *
     * @param chemin le chemin logique du document interrogé.
     * @param arbre l'arbre résolu du document.
     * @param sourceArbre le texte dans le système de l'arbre.
     * @param position la position interrogée.
     * @return les cibles internes (chemin + plage + libellé picker).
     */
    public fun superDefinition(
        chemin: String,
        arbre: CompilationUnitDeclaration?,
        sourceArbre: String,
        position: Int,
    ): List<CibleSuper> {
        if (arbre == null) return emptyList()
        // 1. Une méthode au caret (déclaration englobante ou envoi) : la
        //    méthode OUTREPASSÉE au-dessus du déclarant.
        val methode = methodeAuCaret(arbre, position)
        if (methode != null) {
            val cible = superMethodeDe(methode)
            if (cible != null) return listOf(cible)
        }
        // 2. Retombée : les supertypes DIRECTS du type en contexte.
        val typeEnContexte = typeAuCaretOuEnglobant(arbre, sourceArbre, position) ?: return emptyList()
        return supertypesDirectsDe(typeEnContexte)
    }

    // ------------------------------------------------------------------ //
    // Sonde fqcnAt (fiche 005 — câble du repli binaire)
    // ------------------------------------------------------------------ //

    /**
     * FQN au caret (fiche 005 : `fqcnAt`) : la sonde du fallback « GO TO
     * definition / typeDefinition sur cible binaire » côté serveur LSP.
     *
     * @param chemin le chemin logique du document interrogé.
     * @param arbre l'arbre résolu du document.
     * @param sourceArbre le texte dans le système de l'arbre.
     * @param position la position interrogée.
     * @param cibleType `targetType` du cahier : faux → la classe
     *   DÉCLARANTE du symbole (definition — pour une référence de type,
     *   la classe déclarante EST le type lui-même) ; vrai → le TYPE du
     *   symbole (typeDefinition — le type déclaré d'une variable).
     * @return le FQN, ou null (caret sans binding, type de base).
     */
    public fun fqcnAt(
        chemin: String,
        arbre: CompilationUnitDeclaration?,
        sourceArbre: String,
        position: Int,
        cibleType: Boolean,
    ): String? {
        val symbole = symboleEn(arbre, sourceArbre, position) ?: return null
        return if (cibleType) {
            symbole.fqnTypeDuSymbole
        } else {
            symbole.fqnProprietaire ?: symbole.fqnTypeDuSymbole
        }
    }

    // ------------------------------------------------------------------ //
    // Routage des cibles — les trois chemins
    // ------------------------------------------------------------------ //

    /** Cible d'une LOCALE : lien direct du binding vers sa déclaration. */
    private fun cibleLocale(chemin: String, locale: LocalVariableBinding): List<CibleNavigation> {
        val declaration = try { locale.declaration } catch (_: Exception) { null } ?: return emptyList()
        val nom = try { String(declaration.name) } catch (_: Exception) { null } ?: return emptyList()
        return listOf(
            CibleNavigation(
                genre = GenreCible.SOURCE_COURANTE,
                chemin = Path.of(chemin),
                uri = "file://$chemin",
                plage = Plage(declaration.sourceStart, declaration.sourceStart + nom.length),
                nomSimple = nom,
            ),
        )
    }

    /** Cible d'une MÉTHODE : registre intra-unité, puis projet, puis binaire. */
    private fun cibleMethode(
        chemin: String,
        arbre: CompilationUnitDeclaration?,
        sourceArbre: String,
        symbole: SymboleAuCaret,
    ): List<CibleNavigation> {
        val binding = symbole.bindingEcj as? MethodBinding ?: return emptyList()
        val selecteur = try { String(binding.selector) } catch (_: Exception) { null } ?: return emptyList()
        val arite = try { binding.parameters?.size ?: 0 } catch (_: Exception) { 0 }

        // 1. Intra-unité : la déclaration vit dans l'arbre courant.
        if (arbre != null) {
            val registre = RegistreUnite()
            LectureAst.traverserResilient(arbre, registre)
            val declaration = registre.methodes[binding]
                ?: binding.original()?.let { registre.methodes[it] }
            if (declaration != null) {
                val plage = plageNomMethode(declaration, sourceArbre) ?: return emptyList()
                return listOf(
                    CibleNavigation(GenreCible.SOURCE_COURANTE, Path.of(chemin), "file://$chemin", plage, selecteur),
                )
            }
        }

        // 2. Projet / 3. binaire : par le type déclarant TOP-NIVEAU.
        val cle = symbole.cleUsages as? CleUsages.Methode ?: return emptyList()
        return cibleDepuisFqnProprietaire(chemin, cle.fqnProprietaire, selecteur) { fichier, texte, arbreFichier ->
            val declaration = trouverMethode(arbreFichier, selecteur, arite) ?: return@cibleDepuisFqnProprietaire null
            plageNomMethode(declaration, texte)
        }
    }

    /** Cible d'un CHAMP : registre intra-unité, puis projet, puis binaire. */
    private fun cibleChamp(
        chemin: String,
        arbre: CompilationUnitDeclaration?,
        sourceArbre: String,
        symbole: SymboleAuCaret,
    ): List<CibleNavigation> {
        val binding = symbole.bindingEcj as? FieldBinding ?: return emptyList()
        val nom = try { String(binding.name) } catch (_: Exception) { null } ?: return emptyList()

        // 1. Intra-unité.
        if (arbre != null) {
            val registre = RegistreUnite()
            LectureAst.traverserResilient(arbre, registre)
            val declaration = registre.champs[binding]
            if (declaration != null) {
                return listOf(
                    CibleNavigation(
                        GenreCible.SOURCE_COURANTE, Path.of(chemin), "file://$chemin",
                        Plage(declaration.sourceStart, declaration.sourceStart + nom.length), nom,
                    ),
                )
            }
        }

        // 1b. Champ d'une classe LIGHT (V-B2) : `R.layout.x` vise le XML
        // déclarant — la cible EXACTE du référentiel, avant tout repli
        // projet/binaire (le déclarant synthétique n'a pas de source).
        // Le FQCN déclarant est pris en forme BINAIRE (constantPoolName —
        // `ns.R$layout`) : la clé d'usages remonte au type TOP (l'imbriquée
        // y serait perdue).
        val cle = symbole.cleUsages as? CleUsages.Champ
        cibleChampSynthetique?.let { resoudre ->
            val fqnDeclarant = runCatching {
                binding.declaringClass?.constantPoolName()?.let { nom -> String(nom).replace('/', '.') }
            }.getOrNull() ?: cle?.fqnProprietaire
            if (fqnDeclarant != null) {
                val cible = runCatching { resoudre(fqnDeclarant, nom) }.getOrNull()
                if (cible != null) {
                    return listOf(
                        CibleNavigation(
                            genre = GenreCible.SOURCE_PROJET,
                            chemin = cible.fichier,
                            uri = "file://${cible.fichier}",
                            plage = Plage(cible.offset, cible.offset + cible.longueur),
                            nomSimple = nom,
                        ),
                    )
                }
            }
        }

        // 2. Projet / 3. binaire.
        if (cle == null) return emptyList()
        return cibleDepuisFqnProprietaire(chemin, cle.fqnProprietaire, nom) { fichier, texte, arbreFichier ->
            val declaration = trouverChamp(arbreFichier, nom) ?: return@cibleDepuisFqnProprietaire null
            Plage(declaration.sourceStart, declaration.sourceStart + nom.length)
        }
    }

    /** Cible d'un TYPE (definition sur référence, typeDefinition) : registre, projet, binaire. */
    private fun cibleType(
        chemin: String,
        arbre: CompilationUnitDeclaration?,
        sourceArbre: String,
        fqnType: String,
    ): List<CibleNavigation> {
        // Le nom composé d'un type membre porte un « $ » (forme binaire
        // ecj) : le nom SIMPLE est le dernier segment APRÈS le « $ ».
        val nomSimple = fqnType.substringAfterLast('.').substringAfterLast('$')
        val fqnTop = fqnTypeAvantImbriquee(fqnType)

        // 1. Intra-unité : le type (même IMBRIQUÉ) est déclaré dans l'arbre.
        if (arbre != null) {
            val registre = RegistreUnite()
            LectureAst.traverserResilient(arbre, registre)
            for ((binding, declaration) in registre.types) {
                if (fqnCompletDe(binding) == fqnType) {
                    val plage = plageNomType(declaration, sourceArbre) ?: return emptyList()
                    return listOf(
                        CibleNavigation(GenreCible.SOURCE_COURANTE, Path.of(chemin), "file://$chemin", plage, nomSimple),
                    )
                }
            }
        }

        // 2. Projet : fichier du type TOP-NIVEAU, déclaration cherchée au
        //    nom simple (la traversée couvre les types imbriqués).
        return cibleDepuisFqnProprietaire(chemin, fqnTop, nomSimple) { fichier, texte, arbreFichier ->
            val declaration = trouverType(arbreFichier, nomSimple) ?: return@cibleDepuisFqnProprietaire null
            plageNomType(declaration, texte)
        }
    }

    /**
     * Chemins 2 et 3 du routeur : localise le FQN du type porteur — une
     * SOURCE rend la cible projet (plage fournie par le chercheur), un
     * BINAIRE rend l'URI `jdt://decompiled/` à plage VIDE.
     */
    private inline fun cibleDepuisFqnProprietaire(
        chemin: String,
        fqnProprietaire: String,
        nomSimple: String,
        chercherPlage: (fichier: Path, texte: String, arbre: CompilationUnitDeclaration?) -> Plage?,
    ): List<CibleNavigation> {
        val localisation = runCatching { localiser(fqnProprietaire) }.getOrNull()
        return when (localisation) {
            is EnvironnementNoms.Localisation.Source -> {
                val fichier = localisation.fichier
                val texte = try {
                    fichier.readText()
                } catch (_: Exception) {
                    return emptyList()
                }
                val arbre = runCatching { resoudre(fichier.toString(), texte) }.getOrNull()
                val plage = chercherPlage(fichier, texte, arbre) ?: return emptyList()
                listOf(
                    CibleNavigation(GenreCible.SOURCE_PROJET, fichier, "file://$fichier", plage, nomSimple),
                )
            }
            EnvironnementNoms.Localisation.Jar,
            EnvironnementNoms.Localisation.Jrt,
            EnvironnementNoms.Localisation.Stub,
            -> listOf(
                CibleNavigation(
                    genre = GenreCible.BINAIRE_DECOMPILE,
                    chemin = null,
                    uri = uriDecompilee(fqnProprietaire),
                    plage = Plage(0, 0),
                    nomSimple = nomSimple,
                ),
            )
            else -> emptyList()
        }
    }

    /** L'URI synthétique d'un binaire décompilé (fiche 062 : forme exacte). */
    private fun uriDecompilee(fqcn: String): String = "jdt://decompiled/${fqcn.replace('.', '/')}.java"

    // ------------------------------------------------------------------ //
    // Super — méthode outrépassée et supertypes directs
    // ------------------------------------------------------------------ //

    /** La méthode au caret : l'envoi le plus profond, ou la déclaration englobante (bornes COMPLÈTES). */
    private fun methodeAuCaret(arbre: CompilationUnitDeclaration, position: Int): MethodBinding? {
        val collecteur = object : LectureAst.VisiteurResilient() {
            var envoi: MethodBinding? = null
            private var tailleEnvoi = Int.MAX_VALUE
            var declaration: MethodBinding? = null
            private var tailleDeclaration = Int.MAX_VALUE

            override fun visiteEnvoi(node: org.eclipse.jdt.internal.compiler.ast.MessageSend, scope: org.eclipse.jdt.internal.compiler.lookup.BlockScope?): Boolean {
                if (position in node.sourceStart..node.sourceEnd && node.sourceEnd - node.sourceStart < tailleEnvoi) {
                    envoi = try { node.binding } catch (_: Exception) { null }
                    tailleEnvoi = node.sourceEnd - node.sourceStart
                }
                return true
            }

            override fun visiteMethode(node: MethodDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
                // Bornes COMPLÈTES : les « sourceStart/End » d'une déclaration
                // ne couvrent que la tête sans modificateurs (convention ecj) —
                // le caret « dans la méthode » (fiche 049) doit la trouver.
                val debut = try { node.declarationSourceStart } catch (_: Exception) { node.sourceStart }
                val fin = try { node.declarationSourceEnd } catch (_: Exception) { node.sourceEnd }
                if (position in debut..fin && fin - debut < tailleDeclaration) {
                    declaration = try { node.binding } catch (_: Exception) { null }
                    tailleDeclaration = fin - debut
                }
                return true
            }
        }
        LectureAst.traverserResilient(arbre, collecteur)
        return collecteur.envoi ?: collecteur.declaration
    }

    /**
     * La méthode OUTREPASSÉE au-dessus du déclarant de [methode] : remonte
     * la chaîne des supertypes (superclasse d'abord, interfaces ensuite),
     * la première DÉCLARATION SOURCE du même sélecteur et de la même arité
     * gagne — un maillon BINAIRE est franchi sans cible (règle D3).
     */
    private fun superMethodeDe(methode: MethodBinding): CibleSuper? {
        val selecteur = try { String(methode.selector) } catch (_: Exception) { null } ?: return null
        val arite = try { methode.parameters?.size ?: 0 } catch (_: Exception) { 0 }
        val declarant = try { methode.declaringClass } catch (_: Exception) { null } ?: return null
        var courant: ReferenceBinding? = declarant
        val dejaVus = HashSet<String>()
        while (courant != null) {
            val fqnCourant = fqnCompletDe(courant) ?: break
            if (!dejaVus.add(fqnCourant)) break
            val supertypes = ArrayList<ReferenceBinding>()
            runCatching {
                courant.superclass()?.let { supertypes.add(it) }
                (courant.superInterfaces() ?: emptyArray()).forEach { supertypes.add(it) }
            }
            for (superType in supertypes) {
                val fqnSuper = fqnCompletDe(superType) ?: continue
                val localisation = runCatching { localiser(fqnTypeAvantImbriquee(fqnSuper)) }.getOrNull()
                val fichier = (localisation as? EnvironnementNoms.Localisation.Source)?.fichier ?: continue
                val texte = try {
                    fichier.readText()
                } catch (_: Exception) {
                    continue
                }
                val arbre = runCatching { resoudre(fichier.toString(), texte) }.getOrNull() ?: continue
                val declaration = trouverMethode(arbre, selecteur, arite) ?: continue
                val plage = plageNomMethode(declaration, texte) ?: continue
                val conteneur = fqnSuper.substringAfterLast('.').substringAfterLast('$')
                return CibleSuper(selecteur, conteneur, fichier, plage)
            }
            courant = supertypes.firstOrNull()
        }
        return null
    }

    /** Les supertypes DIRECTS SOURCE du type en contexte (binaire écarté). */
    private fun supertypesDirectsDe(type: ReferenceBinding): List<CibleSuper> {
        val resultat = ArrayList<CibleSuper>()
        val supertypes = ArrayList<ReferenceBinding>()
        runCatching {
            type.superclass()?.let { supertypes.add(it) }
            (type.superInterfaces() ?: emptyArray()).forEach { supertypes.add(it) }
        }
        for (superType in supertypes) {
            val fqnSuper = fqnCompletDe(superType) ?: continue
            val localisation = runCatching { localiser(fqnTypeAvantImbriquee(fqnSuper)) }.getOrNull()
            val fichier = (localisation as? EnvironnementNoms.Localisation.Source)?.fichier ?: continue
            val texte = try {
                fichier.readText()
            } catch (_: Exception) {
                continue
            }
            val arbre = runCatching { resoudre(fichier.toString(), texte) }.getOrNull() ?: continue
            val nomSimple = fqnSuper.substringAfterLast('.').substringAfterLast('$')
            val declaration = trouverType(arbre, nomSimple) ?: continue
            val plage = plageNomType(declaration, texte) ?: continue
            val paquet = fqnSuper.replace('$', '.').substringBeforeLast('.', "")
            resultat.add(CibleSuper(nomSimple, paquet, fichier, plage))
        }
        return resultat
    }

    // ------------------------------------------------------------------ //
    // Type en contexte (fiche 060 : référence au caret, sinon englobant)
    // ------------------------------------------------------------------ //

    /** Le type au caret : la référence visée, sinon la classe ENGLOBANTE. */
    private fun typeAuCaretOuEnglobant(
        arbre: CompilationUnitDeclaration,
        sourceArbre: String,
        position: Int,
    ): ReferenceBinding? {
        val symbole = symboleEn(arbre, sourceArbre, position)
        if (symbole?.nature == NatureSymbole.TYPE) {
            (symbole.bindingEcj as? ReferenceBinding)?.let { return it }
        }
        // Retombée : la déclaration de type la plus PROFONDE contenant le
        // caret — sur ses bornes COMPLÈTES (les « sourceStart/End » d'une
        // déclaration de type ne couvrent que le NOM, convention ecj).
        val collecteur = object : LectureAst.VisiteurResilient() {
            var englobante: TypeDeclaration? = null
            private var taille = Int.MAX_VALUE

            override fun visiteType(node: TypeDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
                val debut = try { node.declarationSourceStart } catch (_: Exception) { node.sourceStart }
                val fin = try { node.declarationSourceEnd } catch (_: Exception) { node.sourceEnd }
                if (position in debut..fin && fin - debut < taille) {
                    englobante = node
                    taille = fin - debut
                }
                return true
            }
        }
        LectureAst.traverserResilient(arbre, collecteur)
        return try {
            collecteur.englobante?.binding as? ReferenceBinding
        } catch (_: Exception) {
            null
        }
    }

    // ------------------------------------------------------------------ //
    // Recherches de déclarations dans une unité résolue
    // ------------------------------------------------------------------ //

    /** Le symbole au caret (collecteur segment-précis). */
    private fun symboleEn(
        arbre: CompilationUnitDeclaration?,
        sourceArbre: String,
        position: Int,
    ): SymboleAuCaret? {
        if (arbre == null) return null
        val collecteur = CollecteurSymbole(sourceArbre, position)
        LectureAst.traverserResilient(arbre, collecteur)
        return collecteur.symbole()
    }

    /** Déclaration de méthode par sélecteur + arité (traversée imbriqués comprise). */
    private fun trouverMethode(
        arbre: CompilationUnitDeclaration?,
        selecteur: String,
        arite: Int,
    ): MethodDeclaration? {
        if (arbre == null) return null
        val collecteur = object : LectureAst.VisiteurResilient() {
            var trouvee: MethodDeclaration? = null
            override fun visiteMethode(node: MethodDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
                // NB : ecj rend « arguments » NULL pour une méthode sans
                // paramètre (jamais un tableau vide) — l'arité 0 doit passer.
                if (trouvee == null && String(node.selector) == selecteur && (node.arguments?.size ?: 0) == arite) {
                    trouvee = node
                }
                return true
            }
        }
        LectureAst.traverserResilient(arbre, collecteur)
        return collecteur.trouvee
    }

    /** Déclaration de champ par nom (traversée imbriqués comprise). */
    private fun trouverChamp(arbre: CompilationUnitDeclaration?, nom: String): FieldDeclaration? {
        if (arbre == null) return null
        val collecteur = object : LectureAst.VisiteurResilient() {
            var trouve: FieldDeclaration? = null
            override fun visiteChamp(node: FieldDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.MethodScope?): Boolean {
                if (trouve == null && String(node.name) == nom) trouve = node
                return true
            }
        }
        LectureAst.traverserResilient(arbre, collecteur)
        return collecteur.trouve
    }

    /** Déclaration de type par nom simple (traversée imbriqués comprise). */
    private fun trouverType(arbre: CompilationUnitDeclaration?, nomSimple: String): TypeDeclaration? {
        if (arbre == null) return null
        val collecteur = object : LectureAst.VisiteurResilient() {
            var trouve: TypeDeclaration? = null
            override fun visiteType(node: TypeDeclaration, scope: org.eclipse.jdt.internal.compiler.lookup.ClassScope?): Boolean {
                if (trouve == null && String(node.name) == nomSimple) trouve = node
                return true
            }
        }
        LectureAst.traverserResilient(arbre, collecteur)
        return collecteur.trouve
    }

    // ------------------------------------------------------------------ //
    // Plages des JETONS déclarés (balayage de tête — convention N3/N7)
    // ------------------------------------------------------------------ //

    /** Plage du sélecteur d'une déclaration de méthode. */
    private fun plageNomMethode(declaration: MethodDeclaration, texte: String): Plage? {
        val selecteur = String(declaration.selector)
        return plageJeton(declaration.sourceStart, declaration.sourceStart + 120, texte, selecteur)
    }

    /** Plage du nom déclaré d'un type (les modificateurs précèdent le nom). */
    private fun plageNomType(declaration: TypeDeclaration, texte: String): Plage? {
        val nom = String(declaration.name)
        return plageJeton(declaration.sourceStart, declaration.sourceStart + 200, texte, nom)
    }

    /** Cherche [mot] aux bornes de mots dans la tête [debut, limite) du texte. */
    private fun plageJeton(debut: Int, limite: Int, texte: String, mot: String): Plage? {
        val borne = limite.coerceAtMost(texte.length)
        if (debut >= borne) return null
        val tete = texte.substring(debut, borne)
        var i = 0
        while (i < tete.length) {
            if (tete[i].isJavaIdentifierStart() && tete.regionMatches(i, mot, 0, mot.length)) {
                val apres = i + mot.length
                val borneAvant = i == 0 || !tete[i - 1].isJavaIdentifierPart()
                val borneApres = apres >= tete.length || !tete[apres].isJavaIdentifierPart()
                if (borneAvant && borneApres) return Plage(debut + i, debut + apres)
            }
            i++
        }
        return null
    }

    // ------------------------------------------------------------------ //
    // Extraction de FQN (défensive)
    // ------------------------------------------------------------------ //

    /** FQN complet d'un binding de type (imbriqués compris). */
    private fun fqnCompletDe(type: ReferenceBinding?): String? = try {
        type?.compoundName?.joinToString(".") { String(it) }
    } catch (_: Exception) {
        null
    }

    /** Le FQN tronqué au type TOP-NIVEAU (`demo.Outer$Inner` → `demo.Outer`). */
    private fun fqnTypeAvantImbriquee(fqn: String): String {
        // Un segment n'est imbriqué que si le fichier du FQN tronqué existe
        // — on essaie depuis la droite (le paquet seul n'est jamais un type).
        // Le « $ » des types membres est normalisé en point au préalable.
        val segments = fqn.replace('$', '.').split('.')
        for (coupe in segments.size - 1 downTo 2) {
            val candidat = segments.subList(0, coupe).joinToString(".")
            val localisation = runCatching { localiser(candidat) }.getOrNull()
            if (localisation is EnvironnementNoms.Localisation.Source) return candidat
        }
        return fqn
    }
}
