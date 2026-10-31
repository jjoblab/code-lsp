package jo.codeide.lsp.java.completion

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import jo.codeide.lsp.java.classpath.EnvironnementNoms
import jo.codeide.lsp.java.classpath.GenreType
import jo.codeide.lsp.java.concurrence.EpoqueRetiree
import jo.codeide.lsp.java.IndexNoms
import jo.codeide.lsp.java.interne.ItemCompletion
import jo.codeide.lsp.java.interne.MARQUEUR_INACCESSIBLE
import jo.codeide.lsp.java.interne.MARQUEUR_TYPEDEF
import jo.codeide.lsp.java.interne.LectureAst
import jo.codeide.lsp.java.interne.NatureItem
import jo.codeide.lsp.java.interne.PontEcj
import jo.codeide.lsp.java.interne.UnitesSource
import jo.codeide.lsp.java.projetandroid.ReferentielRessources
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
 * @param estCoupe point de contrôle d'annulation fine (V-F1 — ADR 0029
 *   phase 1) : consulté aux bornes des boucles de COLLECTE (hiérarchies de
 *   membres, traversée de l'annuaire par préfixe) ; vrai ⇒ la collecte est
 *   coupée par [EpoqueRetiree] — le moteur vient d'être retiré par un swap
 *   de double-tampon et le résultat ne serait de toute façon pas livré.
 *   Défaut `{ false }` : jamais coupé (services construits hors moteur).
 * @param ressourcesFournisseur le référentiel de RESSOURCES Android (V-C3 —
 *   ADR 0026 (f) : littéraux `R.*` en position `@…Res` ; null = pas de
 *   contrat Android, le genre LITTÉRAL_RESSOURCE rend une liste VIDE
 *   honnête).
 * @param indexFournisseur l'index de noms PARTAGÉ (V-C3 — ADR 0026 (d) :
 *   3e invocation, types du PROJET entier avec import automatique ; null =
 *   moteur sans index, le 3e appel n'élargit pas — honnête).
 * @param compteur le compteur d'usage local OPT-IN (V-C3 — ADR 0026 (g) :
 *   fenêtre glissante, peseur D9 de la chaîne standard ; null = statistiques
 *   désactivées, défaut — vie privée).
 */
public class ServiceCompletion(
    private val pontFournisseur: () -> PontEcj,
    private val environnementFournisseur: () -> EnvironnementNoms,
    private val arbreOriginal: (String, String) -> CompilationUnitDeclaration?,
    private val chainePersonnalisee: ChaineClassement? = null,
    private val estCoupe: () -> Boolean = { false },
    private val ressourcesFournisseur: () -> ReferentielRessources? = { null },
    private val indexFournisseur: () -> IndexNoms? = { null },
    private val compteur: CompteurUsage? = null,
) {

    /** Chaîne de classement (statistique désactivée par défaut ; banc : remplaçable). */
    private val chaine: ChaineClassement = chainePersonnalisee ?: ChaineClassement(compteur)

    /** Arrangement (gel + trim honnête) — un par moteur. */
    private val arrangement: ArrangementListe = ArrangementListe(chaine)

    /** Réinitialise le gel des têtes (banc ADR 0016 — scénarios indépendants). */
    internal fun reinitialiserGel() {
        arrangement.reinitialiser()
    }

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
            // V-C3 (fiche C-08 D4) : compteur d'invocation par (document,
            // ancre, PRÉFIXE) — une frappe qui change le préfixe ROUVRE un
            // contexte (retombe à 1) ; la même position ré-invoquée élargit
            // (2e : inaccessibles marqués + statiques importables ; 3e : projet).
            val invocation = noterInvocation(cleInvocation(chemin, contexte))
            val candidats = collecter(contexte, chemin, source, offset, invocation)
            var filtres = filtrer(candidats, contexte.prefixe)
            // V-C3 (ADR 0026 (b)) : CHAÎNE — seconde passe quand AUCUN
            // candidat direct type-compatible n'a survécu au préfixe : les
            // méthodes du récepteur retournant le type attendu en deux
            // sauts (`w.builder().text()`), budget borné.
            if (contexte.genre == GenreContexte.MEMBRE &&
                contexte.unionAttendus.isNotEmpty() &&
                filtres.none { it.compatibilite == 0 }
            ) {
                filtres = filtres + filtrer(chaineDe(contexte), contexte.prefixe)
            }
            val cle = cleDeGel(chemin, contexte)
            val reponse = arrangement.arranger(cle, filtres, ContexteClassement(contexte.genre, contexte.prefixe))
            // Gabarits (étage N7, fiche 029) : injection POST-arrangement —
            // la chaîne D8 et le gel des têtes restent intacts ; la clé
            // exacte domine, la clé partielle reste discrète.
            val items = jo.codeide.lsp.java.gabarits.ServiceGabarits.enrichir(reponse.items, contexte, source, offset)
            // Auto-import (retours appareil du 31/10/2026, banc S06) : le
            // FQCN porté par l'item devient une ÉDITION D'INSERTION —
            // additionalTextEdits côté LSP, le geste Android Studio.
            val avecImports = items.map { ajouterEditionImport(it, contexte, source) }
            ReponseCompletion(avecImports, reponse.totalDisponible, contexte.genre)
        } catch (e: EpoqueRetiree) {
            // V-F1 (annulation fine) : la coupure d'époque TRAVERSE la
            // frontière du service — elle n'est pas un échec interne mais un
            // signal de rejeu pour le protocole d'époque de l'appelant ;
            // l'avaler ici reviendrait à livrer une liste VIDE (pire qu'une
            // réponse périmée) sur un moteur retiré.
            throw e
        } catch (_: Exception) {
            // Aucune autre levée ne franchit la frontière du service (fiche
            // 035) : un échec interne rend une liste vide honnête.
            ReponseCompletion(emptyList(), 0, GenreContexte.INCONNU)
        }
    }

    /**
     * Compteur d'invocations de complétion (V-C3 — fiche C-08 D4) : la clé
     * est (document, genre, ancre, préfixe) — mémoire BORNÉE (les 64
     * dernières ancres, LRU d'accès).
     */
    private val invocations = LinkedHashMap<String, Int>(16, 0.75f, true)

    /**
     * Verrou du périmètre ÉTAT de complétion (V-F2 — ADR 0010 schéma B :
     * verrou NOMMÉ, jamais de moniteur anonyme) : garde les compteurs
     * d'invocation et les MÉMOS (membres binaires, bindings) — chemins
     * SANS ecj, ils ne prennent jamais le verrou de compilation. Feuille
     * relative au périmètre compilation : jamais l'inverse.
     */
    private val verrouEtat = java.util.concurrent.locks.ReentrantLock()

    /** Note et rend le NUMÉRO d'invocation courant de la clé (1, 2, 3…). */
    private fun noterInvocation(cle: String): Int = verrouEtat.withLock {
        val numero = (invocations[cle] ?: 0) + 1
        invocations[cle] = numero
        while (invocations.size > MAX_ANCRES_INVOCATION) {
            val iterateur = invocations.entries.iterator()
            iterateur.next()
            iterateur.remove()
        }
        numero
    }

    /** La clé d'invocation : document + genre + ancre + PRÉFIXE (fiche C-08 D4). */
    private fun cleInvocation(chemin: String, contexte: ContexteCompletion): String = buildString {
        append(chemin)
        append('|')
        append(contexte.genre.name)
        append('|')
        append(contexte.recepteur ?: contexte.paquetTape ?: "")
        append('|')
        append(contexte.prefixe)
    }

    /**
     * Point de contrôle d'annulation fine (V-F1 — ADR 0029 phase 1) : coupe
     * la collecte si l'époque du moteur a été retirée. Posé aux bornes des
     * boucles LONGUES (coût du contrôle : une lecture volatile) — les tris
     * bornés et les collections déjà en mémoire ne sont pas ponctués.
     */
    private fun pointDeControle() {
        if (estCoupe()) throw EpoqueRetiree()
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
    private fun collecter(
        contexte: ContexteCompletion,
        chemin: String,
        source: String,
        offset: Int,
        invocation: Int = 1,
    ): List<ItemCompletion> {
        return when (contexte.genre) {
            GenreContexte.MEMBRE -> membresDe(contexte.typeRecepteur, statique = false, contexte, invocation)
            GenreContexte.MEMBRE_STATIQUE -> membresStatiques(contexte, invocation)
            GenreContexte.TYPE -> types(contexte)
            GenreContexte.NOM -> noms(contexte, chemin, source, offset, invocation)
            GenreContexte.IMPORT -> sousPaquetages(contexte)
            GenreContexte.REFERENCE_PAQUET -> sousPaquetages(contexte)
            GenreContexte.CORPS_CLASSE -> corpsDeClasse(contexte)
            GenreContexte.NOM_DECLARATION -> nomsDeDeclaration(contexte)
            GenreContexte.CASE_LABEL -> constantesDEnum(contexte.typeAttendu) + constantesTypedef(contexte)
            GenreContexte.VALEUR_ANNOTATION -> constantesDEnum(contexte.typeAttendu) + constantesTypedef(contexte)
            GenreContexte.TOP_NIVEAU -> motsClesTopNiveau() + stubsDeDeclaration(contexte)
            GenreContexte.LITTERAL_RESSOURCE -> ressourcesDe(contexte)
            GenreContexte.INCONNU -> emptyList()
        }
    }

    /**
     * Un membre binaire brut du MÉMO : l'item + son type d'expression + son
     * niveau d'accès (l'épreuve d'accessibilité dépend du CONTEXTE d'appel,
     * elle ne se mémoïse pas).
     */
    private class MembreBrut(
        val item: ItemCompletion,
        val typeExpression: TypeBinding?,
        val niveau: NiveauAcces,
        val niveauDeclarent: ReferenceBinding?,
    )

    /** Niveau d'accès d'un membre (JLS 6.6 — épreuve par le contexte d'appel). */
    private enum class NiveauAcces { PUBLIC, DEFAUT, PROTEGE, PRIVE }

    /**
     * MÉMO des membres de types BINAIRES (V-C3 — reste consigné du PV
     * 1.6.0) : l'énumération paie le lazy-parsing du fichier de classe À
     * CHAQUE appel (mesuré 15–26 ms par appel pour 68 membres de
     * `java.lang.String`, 62–68 ms à froid) — les membres d'un type binaire
     * ne changent JAMAIS dans la vie d'un moteur, ils se mémoïsent par
     * IDENTITÉ de binding. Les types SOURCES ne s'y mémoïsent pas (leurs
     * membres bougent à l'édition).
     */
    private val memoMembres = java.util.IdentityHashMap<ReferenceBinding, HashMap<Int, List<MembreBrut>>>()

    /** Mémo des résolutions de bindings par FQCN (binaires seulement). */
    private val memoBindings = HashMap<String, ReferenceBinding?>()

    /**
     * Un membre STATIQUE IMPORTABLE brut du mémo : l'item + son type
     * d'expression (la compatibilité au type attendu se tamponne PAR APPEL).
     */
    private class StatiqueBrut(val item: ItemCompletion, val typeExpression: TypeBinding?)

    /**
     * Mémo des membres statiques par FQCN PORTEUR (2e appel, fiche C-08 D2) :
     * le panneau est borné mais l'énumération ne doit pas se REJOUER à
     * chaque invocation — un banc de charge qui ré-invoque la même ancre
     * des dizaines de fois (mesuré : régression du p95 CONCURRENT de la
     * diagnose voisine) doit payer UNE énumération, pas une par appel.
     * clé par porteur, vide = non résolvable (négatif mémoïsé).
     */
    private val memoStatiques = HashMap<String, List<StatiqueBrut>>()

    /**
     * Membres du récepteur (fiche 020 : la hiérarchie complète est balayée) —
     * depuis V-C3 : compatibilité au TYPE ATTENDU tamponnée (fiche C-03),
     * accessibilité des membres éprouvée (fiche C-08 D1 : 1er appel =
     * accessibles seuls ; 2e = les inaccessibles s'ajoutent MARQUÉS), et
     * mémoïsation des types binaires.
     */
    private fun membresDe(
        type: TypeBinding?,
        statique: Boolean,
        contexte: ContexteCompletion? = null,
        invocation: Int = 1,
    ): List<ItemCompletion> {
        val reference = type as? ReferenceBinding ?: return emptyList()
        val attendus = contexte?.unionAttendus ?: emptyList()
        val bruts = membresBruts(reference, statique)
        return bruts.mapNotNull { brut ->
            val accessible = estAccessible(brut.niveau, brut.niveauDeclarent, contexte)
            if (!accessible && invocation < 2) return@mapNotNull null
            brut.item.copy(
                compatibilite = TypeAttendu.compatibilite(brut.typeExpression, attendus),
                degrade = !accessible,
                marqueur = if (!accessible) MARQUEUR_INACCESSIBLE else brut.item.marqueur,
            )
        }
    }

    /** Les membres bruts (mémoïsés pour les types BINAIRES). */
    private fun membresBruts(reference: ReferenceBinding, statique: Boolean): List<MembreBrut> {
        if (estBinaire(reference)) {
            verrouEtat.withLock {
                memoMembres[reference]?.get(cleMemo(statique))?.let { return it }
            }
        }
        val bruts = collecterMembresBruts(reference, statique)
        if (estBinaire(reference)) {
            verrouEtat.withLock {
                if (memoMembres.size >= MAX_TYPES_MEMO) memoMembres.clear() // borne : tout vider, un cache
                memoMembres.getOrPut(reference) { HashMap() }[cleMemo(statique)] = bruts
            }
        }
        return bruts
    }

    /** Clé de mémo : statique × (1er ou 2e appel — le 2e inclut tout). */
    private fun cleMemo(statique: Boolean): Int = if (statique) 1 else 0

    /** Vrai ssi le binding vient d'un fichier de CLASSE (jrt/jar). */
    private fun estBinaire(reference: ReferenceBinding): Boolean =
        reference is org.eclipse.jdt.internal.compiler.lookup.BinaryTypeBinding

    /** La collecte brute : hiérarchie complète, niveaux d'accès consignés. */
    private fun collecterMembresBruts(reference: ReferenceBinding, statique: Boolean): List<MembreBrut> {
        val items = mutableListOf<MembreBrut>()
        try {
            // La hiérarchie COMPLÈTE est balayée (fiche 020) : les méthodes
            // et champs hérités — jusqu'à Object — sont proposés. ecj ne le
            // fait PAS tout seul (`availableMethods` ne rend que les
            // DÉCLARÉES du type — constat du banc de qualité N2 : aucun
            // stub `toString()` sur un identifiant nu, `notify`/`wait`
            // absents des membres d'un String) ; la redéfinition la plus
            // DÉRIVÉE gagne (la collecte va du type vers les ancêtres et
            // le dédoublonnage garde la première occurrence).
            for (typeCourant in TypeAttendu.hierarchieComplete(reference)) {
                pointDeControle() // V-F1 : coupure fine par ancêtre balayé
                for (champ in champsOuHerites(typeCourant)) {
                    if (statique && !champ.isStatic()) continue
                    if (!statique && champ.isStatic()) continue
                    items.add(
                        MembreBrut(
                            item = ItemCompletion(
                                libelle = String(champ.name),
                                nature = NatureItem.CHAMP,
                                detail = LectureAst.lisible(champ.type),
                            ),
                            typeExpression = try {
                                champ.type
                            } catch (_: Exception) {
                                null
                            },
                            niveau = niveauAcces(champ.isPrivate(), champ.isProtected(), champ.isPublic()),
                            niveauDeclarent = try {
                                champ.declaringClass
                            } catch (_: Exception) {
                                null
                            },
                        ),
                    )
                }
                for (methode in methodesOuHeritees(typeCourant)) {
                    if (methode.isConstructor()) continue
                    if (statique && !methode.isStatic()) continue
                    if (!statique && methode.isStatic()) continue
                    items.add(
                        MembreBrut(
                            item = ItemCompletion(
                                libelle = String(methode.selector),
                                nature = NatureItem.METHODE,
                                detail = signature(methode),
                            ),
                            typeExpression = try {
                                methode.returnType
                            } catch (_: Exception) {
                                null
                            },
                            niveau = niveauAcces(methode.isPrivate(), methode.isProtected(), methode.isPublic()),
                            niveauDeclarent = try {
                                methode.declaringClass
                            } catch (_: Exception) {
                                null
                            },
                        ),
                    )
                }
            }
        } catch (e: EpoqueRetiree) {
            throw e // V-F1 : la coupure n'est pas un binding incomplet
        } catch (_: Exception) {
            // binding incomplet : ce qui a été collecté suffit
        }
        return items.distinctBy { it.item.libelle to it.item.nature }
    }

    /** Le niveau d'accès d'un membre par ses drapeaux ecj (défaut = aucun des trois). */
    private fun niveauAcces(prive: Boolean, protege: Boolean, publique: Boolean): NiveauAcces = when {
        prive -> NiveauAcces.PRIVE
        protege -> NiveauAcces.PROTEGE
        publique -> NiveauAcces.PUBLIC
        else -> NiveauAcces.DEFAUT
    }

    /**
     * L'épreuve d'accessibilité (JLS 6.6, fiche C-08 D1) : public = partout ;
     * défaut = même paquet ; protégé = même paquet OU sous-type ; privé =
     * depuis le type déclarant (imbriqués compris). Sans type englobant
     * (sonde muette), seul le PUBLIC passe — honnête.
     */
    private fun estAccessible(niveau: NiveauAcces, declarante: ReferenceBinding?, contexte: ContexteCompletion?): Boolean {
        if (niveau == NiveauAcces.PUBLIC || declarante == null) return true
        val paquet = contexte?.paquetCourant
        val memePaquet = paquet != null && try {
            String(declarante.qualifiedPackageName()) == paquet
        } catch (_: Exception) {
            false
        }
        if (niveau == NiveauAcces.DEFAUT) return memePaquet
        val englobant = contexte?.typeEnglobant
        if (englobant == null) return false
        if (niveau == NiveauAcces.PRIVE) return estDansLeDeclarant(declarante, englobant)
        // protégé : même paquet OU sous-type
        return memePaquet || estSousType(englobant, declarante)
    }

    /** Vrai ssi le type courant vit DANS le déclarant (imbriqué — accès privé). */
    private fun estDansLeDeclarant(declarante: ReferenceBinding, courant: ReferenceBinding): Boolean {
        var curseur: ReferenceBinding? = courant
        var garde = 0
        while (curseur != null && garde++ < 16) {
            if (curseur === declarante) return true
            curseur = try {
                curseur.enclosingType()
            } catch (_: Exception) {
                null
            }
        }
        return false
    }

    /** Vrai ssi le candidat EST un sous-type (ou égal) au déclarant. */
    private fun estSousType(courant: ReferenceBinding, declarante: ReferenceBinding): Boolean =
        TypeAttendu.hierarchieComplete(courant).any { it === declarante }

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
    private fun membresStatiques(contexte: ContexteCompletion, invocation: Int = 1): List<ItemCompletion> {
        val fqcn = contexte.recepteurFqcn ?: return emptyList()
        // Forme POINTÉE pour la sonde source : les imbriqués voyagent en
        // `$` côté FQCN (`com.example.monip.R$layout`) mais la source Java
        // de la sonde exige les points (`com.example.monip.R.layout`).
        val binding = bindingDeMemo(fqcn.replace('$', '.')) ?: return emptyList()
        return membresDe(binding, statique = true, contexte, invocation)
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
                pointDeControle() // V-F1 : coupure fine pendant la traversée du classpath
                if (visibles.any { it.first == entree.nomSimple }) continue
                // Accessibilité (P6/G-004, vague V-A) : jamais de type non
                // public d'un AUTRE paquet proposé avec auto-import insoluble.
                if (!annuaire.estProposablePourAutoImport(entree, contexte.paquetCourant)) continue
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
            // Même paquet : tout type direct est accessible (règle Java),
            // package-privé compris — aucun filtre d'accessibilité ici.
            for (entree in annuaire.typesDirects(paquet)) {
                pointDeControle() // V-F1 : coupure fine
                resultats[entree.nomSimple] = entree.fqcn
            }
        }
        for (fqcnImport in contexte.importsFqcn) {
            if (fqcnImport.endsWith(".*")) {
                val paquet = fqcnImport.removeSuffix(".*")
                // Balayage `import x.*` : seuls les types ACCESSIBLES sont
                // proposés (P6/G-004 — un package-privé d'un autre paquet
                // n'est pas joignable par l'import étoilé).
                for (entree in annuaire.typesDirects(paquet)) {
                    if (!annuaire.estProposablePourAutoImport(entree, contexte.paquetCourant)) continue
                    resultats[entree.nomSimple] = entree.fqcn
                }
            } else {
                // Import EXPLICIT du fichier : le nom est proposé tel quel
                // (miroir de l'intention écrite — l'accessibilité de
                // l'import écrit relève du diagnostic, pas de la complétion).
                val nomSimple = fqcnImport.substringAfterLast('.')
                resultats[nomSimple] = fqcnImport
            }
        }
        // java.lang est visible SANS import : seuls les types PUBLICS sont
        // réellement joignables depuis un paquet étranger (P6/G-004).
        for (entree in annuaire.typesDirects("java.lang")) {
            pointDeControle() // V-F1 : coupure fine
            if (!annuaire.estProposablePourAutoImport(entree, contexte.paquetCourant)) continue
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

    /**
     * Complétion de NOM (fiche 008) : inventaire du fichier + types +
     * mots-clés — depuis V-C3, tamponnés de leur compatibilité au type
     * attendu (fiche C-03 S1/S2) et ÉLARGIS aux invocations successives
     * (fiche C-08 : 2e appel = imports STATIQUES ; 3e = types du PROJET
     * entier par l'index partagé, import automatique).
     */
    private fun noms(
        contexte: ContexteCompletion,
        chemin: String,
        source: String,
        offset: Int,
        invocation: Int = 1,
    ): List<ItemCompletion> {
        val items = mutableListOf<ItemCompletion>()
        items.addAll(types(contexte.copy(genre = GenreContexte.TYPE, filtre = null)))
        // Inventaire du fichier : champs, méthodes, locales, paramètres (le
        // marqueur a résolu l'unité — l'inventaire se lit sur l'original).
        // V-C3 : chaque item construit sur un binding vivant est tamponné de
        // sa compatibilité au type attendu (S1 : la locale `String nom`
        // remonte, la locale `int age` descend — JAMAIS cachée).
        items.addAll(
            inventaireOriginal(chemin, source, offset) { type ->
                TypeAttendu.compatibilite(type, contexte.unionAttendus)
            },
        )
        for (mot in LectureAst.MOTS_CLES) {
            items.add(ItemCompletion(mot, NatureItem.MOT_CLE))
        }
        // V-C3 (fiche C-04 T1) : les constantes du TYPEDEF visé voyagent en
        // tête — même non importées (import statique à l'insertion).
        items.addAll(constantesTypedef(contexte))
        // V-C3 (fiche C-08 D2) : SECOND appel — membres STATIQUES importables
        // des types de l'unité (imports explicites, paquet courant, panneau
        // java.lang), avec édition d'import statique (insertion compilable).
        if (invocation >= 2) {
            items.addAll(importsStatiques(contexte))
        }
        // V-C3 (fiche C-08 D3) : TROISIÈME appel — types du PROJET entier via
        // l'index partagé, import automatique (sans index : honnête, rien).
        if (invocation >= 3) {
            items.addAll(typesDuProjet(contexte))
        }
        return items
    }

    /** Inventaire du fichier courant (complétion instantanée N1 réutilisée sur l'arbre original). */
    private fun inventaireOriginal(
        chemin: String,
        source: String,
        offset: Int,
        evaluateur: (TypeBinding?) -> Int = { _ -> 1 },
    ): List<ItemCompletion> {
        val arbre = arbreOriginal(chemin, source) ?: return emptyList()
        return LectureAst.inventaireFichier(arbre, offset, evaluateur)
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
            pointDeControle() // V-F1 : coupure fine
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
                pointDeControle() // V-F1 : coupure fine
                items.add(itemType(entree.nomSimple, entree.fqcn, autoImport = null))
            }
        }
        return items
    }

    /** Stubs d'override en position de membre de classe (fiche 007). */
    private fun corpsDeClasse(contexte: ContexteCompletion): List<ItemCompletion> {
        val items = mutableListOf<ItemCompletion>()
        items.addAll(stubsDeDeclaration(contexte))
        // Les types restent proposés en corps de classe (déclarations imbriquées).
        items.addAll(types(contexte.copy(genre = GenreContexte.TYPE, filtre = null)))
        return items
    }

    /**
     * Les STUBS d'override de TOUTE la hiérarchie du type englobant (fiche
     * 007) : le point même du stub est de redéfinir une méthode HÉRITÉE —
     * `toString()` sur `toStr|` ne vit dans aucune méthode déclarée du type
     * (constat du banc de qualité N2). La redéfinition la plus dérivée est
     * proposée (première occurrence du sélecteur dans le balayage).
     *
     * Partagé par CORPS_CLASSE et — depuis V-C3 (décision D7, M1/G-024) —
     * par la TÊTE de déclaration : les mots-clés `extends`/`implements` y
     * sont FUSIONNÉS aux stubs de la hiérarchie du type en cours.
     */
    private fun stubsDeDeclaration(contexte: ContexteCompletion): List<ItemCompletion> {
        val items = mutableListOf<ItemCompletion>()
        val englobant = contexte.typeEnglobant ?: return items
        val vus = mutableSetOf<String>()
        try {
            for (typeCourant in TypeAttendu.hierarchieComplete(englobant)) {
                pointDeControle() // V-F1 : coupure fine par ancêtre balayé
                for (methode in methodesOuHeritees(typeCourant)) {
                    if (methode.isConstructor()) continue
                    if (methode.isStatic()) continue
                    if (methode.isPrivate()) continue
                    if (methode.isFinal()) continue
                    // Le MARQUEUR de splice parse comme une méthode
                    // déclarée du type englobant quand le préfixe est
                    // vide en corps de classe — jamais un candidat.
                    if (String(methode.selector) == AnalyseurContexte.MARQUEUR) continue
                    val nom = String(methode.selector)
                    if (nom in vus) continue
                    vus.add(nom)
                    items.add(stubOverride(methode))
                }
            }
        } catch (e: EpoqueRetiree) {
            throw e // V-F1 : la coupure n'est pas une hiérarchie incomplète
        } catch (_: Exception) {
            // hiérarchie incomplète : les stubs déjà collectés suffisent
        }
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

    /**
     * Mots-clés de déclaration en position de TOP LEVEL (v1.1.6 — l'écart
     * consigné INTEGRATION-CODEIDE §13.4) : la frappe du MOT-CLÉ lui-même
     * (`i|`, `im|`, `import|` avant l'espace) propose les mots-clés légaux
     * hors de tout corps de type, exactement le popup d'Android Studio —
     * les positions spécialisées (clause `extends`, annotation, import
     * APRÈS l'espace) restent capturées par la classification marquée et
     * n'atteignent JAMAIS cette collecte.
     */
    private fun motsClesTopNiveau(): List<ItemCompletion> =
        MOTS_CLES_TOP_NIVEAU.map { ItemCompletion(it, NatureItem.MOT_CLE) }

    // ------------------------------------------------------------------ //
    // V-C3 — typedef, ressources, chaîne, second/troisième appel
    // ------------------------------------------------------------------ //

    /**
     * Les CONSTANTES du typedef visé (fiche C-04 T1/T2/T3) : proposées en
     * TÊTE (marqueur `typedef`, compatibilité dominante) — l'insertion est le
     * nom SIMPLE, l'import STATIQUE du type déclarant voyage en édition
     * additionnelle quand le nom n'y est pas visible (P6 : insertion
     * compilable).
     */
    private fun constantesTypedef(contexte: ContexteCompletion): List<ItemCompletion> {
        val typedef = contexte.typedef ?: return emptyList()
        if (typedef.constantes.isEmpty()) return emptyList()
        return typedef.constantes.map { constante ->
            val qualifiee = constante.insertion != constante.nom
            ItemCompletion(
                libelle = constante.nom,
                nature = NatureItem.CHAMP,
                texteInsertion = constante.insertion,
                detail = if (typedef.typeDeclarent.isNotEmpty()) "${typedef.typeDeclarent}.${constante.nom}" else constante.nom,
                marqueur = MARQUEUR_TYPEDEF,
                compatibilite = 0,
                importStatiqueFqn = if (qualifiee) {
                    null // forme QUALIFIÉE : compilable telle quelle (P6), aucun import
                } else {
                    importStatiqueTypedef(typedef.typeDeclarent, constante.nom, contexte)
                },
            )
        }
    }

    /**
     * L'import statique d'une constante typedef : exigé dès que le type
     * déclarant n'est PAS le type englobant du caret (un champ statique
     * d'une AUTRE classe ne se voit pas sans import, même paquet — JLS 6.5).
     */
    private fun importStatiqueTypedef(typeDeclarent: String, constante: String, contexte: ContexteCompletion): String? {
        if (typeDeclarent.isEmpty()) return null
        val englobant = try {
            contexte.typeEnglobant?.readableName()?.let { String(it) }
        } catch (_: Exception) {
            null
        }
        if (englobant != null && englobant == typeDeclarent) return null
        return "$typeDeclarent.$constante"
    }

    /**
     * Les NOMS de ressources du type attendu (ADR 0026 (f), B11) : depuis le
     * RÉFÉRENTIEL du module (ADR 0020 — jamais d'invention) ; le détail
     * `R.<type>.<nom>` porte la forme qualifiée, le client insère le nom.
     */
    private fun ressourcesDe(contexte: ContexteCompletion): List<ItemCompletion> {
        val type = contexte.typeRessource ?: return emptyList()
        val referentiel = try {
            ressourcesFournisseur()
        } catch (_: Exception) {
            null
        } ?: return emptyList()
        return try {
            referentiel.noms(type).map { nom ->
                ItemCompletion(
                    libelle = nom,
                    nature = NatureItem.CHAMP,
                    detail = "R.$type.$nom",
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * La CHAÎNE de complétion (ADR 0026 (b), fiche C-03 note) : les méthodes
     * du récepteur retournant le type attendu en DEUX sauts — `m1().m2()` où
     * m2 retourne un type compatible. Budgets bornés (premier niveau, items).
     */
    private fun chaineDe(contexte: ContexteCompletion): List<ItemCompletion> {
        val reference = contexte.typeRecepteur as? ReferenceBinding ?: return emptyList()
        val attendus = contexte.unionAttendus
        val items = mutableListOf<ItemCompletion>()
        val vus = HashSet<String>()
        var premierNiveau = 0
        try {
            for (typeCourant in TypeAttendu.hierarchieComplete(reference)) {
                pointDeControle()
                for (m1 in TypeAttendu.methodesDeclarees(typeCourant)) {
                    if (items.size >= BUDGET_CHAINES) return items
                    if (m1.isConstructor() || m1.isStatic() || m1.isPrivate()) continue
                    if (++premierNiveau > BUDGET_CHAINES_PREMIER_NIVEAU) return items
                    val retour1 = try {
                        m1.returnType
                    } catch (_: Exception) {
                        null
                    } ?: continue
                    if (TypeAttendu.compatibilite(retour1, attendus) == 0) continue // compatible direct : pas une chaîne
                    if (retour1 is TypeBinding && "void" == String(retour1.readableName())) continue
                    val intermediaire = retour1 as? ReferenceBinding ?: continue
                    for (typeMilieu in TypeAttendu.hierarchieComplete(intermediaire)) {
                        for (m2 in TypeAttendu.methodesDeclarees(typeMilieu)) {
                            if (m2.isConstructor() || m2.isStatic() || m2.isPrivate()) continue
                            val retour2 = try {
                                m2.returnType
                            } catch (_: Exception) {
                                null
                            } ?: continue
                            if (TypeAttendu.compatibilite(retour2, attendus) != 0) continue
                            val libelle = "${String(m1.selector)}().${String(m2.selector)}()"
                            if (!vus.add(libelle)) continue
                            items.add(
                                ItemCompletion(
                                    libelle = libelle,
                                    nature = NatureItem.METHODE,
                                    texteInsertion = libelle,
                                    detail = "${String(m1.selector)}() → ${LectureAst.lisible(retour1) ?: "?"}, ${String(m2.selector)}() → attendu",
                                    compatibilite = 0,
                                ),
                            )
                            if (items.size >= BUDGET_CHAINES) return items
                        }
                    }
                }
            }
        } catch (e: EpoqueRetiree) {
            throw e
        } catch (_: Exception) {
            // bindings incomplets : les chaînes déjà collectées suffisent
        }
        return items
    }

    /**
     * Les membres STATIQUES IMPORTABLES du second appel (fiche C-08 D2) :
     * énumérés depuis les types EXPLICITEMENT importés par l'unité, les types
     * du PAQUET COURANT et le PANNEAU java.lang des porteurs statiques —
     * l'énumération binaire est mémoïsée (le reste consigné du PV 1.6.0).
     * Chaque item porte `importStatiqueFqn` : l'édition d'insertion
     * `import static …;` garantit une insertion COMPILABLE (P6).
     */
    private fun importsStatiques(contexte: ContexteCompletion): List<ItemCompletion> {
        if (contexte.prefixe.isEmpty()) return emptyList() // sans préfixe : bruit, honnête
        val items = mutableListOf<ItemCompletion>()
        val porteurs = mutableListOf<String>()
        contexte.importsFqcn.asSequence().filter { !it.endsWith(".*") }.take(MAX_TYPES_STATIQUES).forEach { porteurs.add(it) }
        PANNEAU_JAVA_LANG.forEach { porteurs.add(it) }
        try {
            contexte.paquetCourant?.let { paquet ->
                for (entree in environnementFournisseur().annuaire.typesDirects(paquet).take(4)) {
                    porteurs.add(entree.fqcn)
                }
            }
        } catch (_: Exception) {
            // annuaire indisponible : le panneau suffit
        }
        for (fqcn in porteurs.distinct().take(2 * MAX_TYPES_STATIQUES)) {
            pointDeControle()
            for (brut in statiquesBruts(fqcn)) {
                items.add(
                    brut.item.copy(
                        compatibilite = TypeAttendu.compatibilite(brut.typeExpression, contexte.unionAttendus),
                    ),
                )
            }
        }
        return items
    }

    /**
     * Les membres STATIQUES PUBLICS d'un porteur — MÉMOÏSÉS par FQCN (voir
     * [memoStatiques]) : l'énumération ne se paie qu'une fois par porteur
     * et par moteur, la compatibilité au type attendu reste PAR APPEL.
     */
    private fun statiquesBruts(fqcn: String): List<StatiqueBrut> {
        verrouEtat.withLock {
            memoStatiques[fqcn]?.let { return it }
        }
        val bruts = mutableListOf<StatiqueBrut>()
        val binding = try {
            bindingDeMemo(fqcn)
        } catch (_: Exception) {
            null
        }
        if (binding != null) {
            try {
                for (champ in champsOuHerites(binding)) {
                    if (!champ.isStatic() || !champ.isPublic()) continue
                    bruts.add(
                        StatiqueBrut(
                            item = ItemCompletion(
                                libelle = String(champ.name),
                                nature = NatureItem.CHAMP,
                                detail = fqcn,
                                importStatiqueFqn = "$fqcn.${String(champ.name)}",
                            ),
                            typeExpression = try {
                                champ.type
                            } catch (_: Exception) {
                                null
                            },
                        ),
                    )
                }
                for (methode in methodesOuHeritees(binding)) {
                    if (methode.isConstructor() || !methode.isStatic() || !methode.isPublic()) continue
                    bruts.add(
                        StatiqueBrut(
                            item = ItemCompletion(
                                libelle = String(methode.selector),
                                nature = NatureItem.METHODE,
                                detail = signature(methode),
                                importStatiqueFqn = "$fqcn.${String(methode.selector)}",
                            ),
                            typeExpression = try {
                                methode.returnType
                            } catch (_: Exception) {
                                null
                            },
                        ),
                    )
                }
            } catch (_: Exception) {
                // binding fautif : ce qui a été collecté suffit
            }
        }
        verrouEtat.withLock {
            if (memoStatiques.size >= MAX_TYPES_MEMO) memoStatiques.clear()
            memoStatiques[fqcn] = bruts
        }
        return bruts
    }

    /**
     * Les types du PROJET ENTIER au TROISIÈME appel (fiche C-08 D3) : par
     * l'index de noms PARTAGÉ (CIDX), avec import automatique — en SUS des
     * types du classpath déjà proposés au premier appel (dédoublonnés).
     */
    private fun typesDuProjet(contexte: ContexteCompletion): List<ItemCompletion> {
        if (contexte.prefixe.isEmpty()) return emptyList()
        val index = try {
            indexFournisseur()
        } catch (_: Exception) {
            null
        } ?: return emptyList()
        val dejaProposees = try {
            environnementFournisseur().annuaire.typesParPrefixe(contexte.prefixe, LIMITE_COLLECTE)
                .map { it.fqcn }.toHashSet()
        } catch (_: Exception) {
            hashSetOf()
        }
        val items = mutableListOf<ItemCompletion>()
        try {
            for (nom in index.nomsParPrefixe(contexte.prefixe, LIMITE_COLLECTE)) {
                pointDeControle()
                for (fqcn in index.fqnsForSimpleName(nom)) {
                    if (fqcn in dejaProposees) continue
                    if (fqcn.contains('$')) continue // imbriqué : jamais importé directement
                    items.add(itemType(nom, fqcn, autoImport = fqcn))
                }
            }
        } catch (_: Exception) {
            // index indisponible : honnête, rien
        }
        return items
    }

    /**
     * Résolution de binding par FQCN (passe dédiée) — MÉMOÏSÉE pour les
     * types BINAIRES (le reste consigné du PV 1.6.0 : 15–26 ms par appel).
     */
    private fun bindingDeMemo(fqcn: String): ReferenceBinding? {
        verrouEtat.withLock {
            if (memoBindings.containsKey(fqcn)) return memoBindings[fqcn]
        }
        val binding = bindingDe(fqcn)
        if (binding != null && estBinaire(binding)) {
            verrouEtat.withLock {
                if (memoBindings.size >= MAX_TYPES_MEMO) memoBindings.clear()
                memoBindings[fqcn] = binding
            }
        }
        return binding
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
    // Auto-import des types proposés
    // ------------------------------------------------------------------ //

    /**
     * L'édition d'INSERTION d'import d'un item d'auto-import (banc S06) :
     * un item portant [ItemCompletion.autoImportFqn] reçoit une édition
     * `import <fqn>;\n` posée au POINT D'INSERTION des imports de l'unité —
     * le client LSP l'applique comme `additionalTextEdits` au moment de la
     * SÉLECTION (jamais avant), le geste exact d'Android Studio.
     *
     * Depuis V-C3, un item portant [ItemCompletion.importStatiqueFqn] reçoit
     * de même une édition `import static <fqn>;\n` (imports statiques du
     * SECOND appel, fiche C-08 D2 ; constantes typedef non visibles, fiche
     * C-04 T1) — l'insertion reste COMPILABLE (P6).
     *
     * Aucune édition quand l'import est INUTILE : déjà présent (exact ou
     * `.*`), type du PAQUET COURANT, ou `java.lang` — les mêmes règles de
     * visibilité que [typesVisibles].
     *
     * @param item l'item candidate.
     * @param contexte le contexte (imports + paquet courant de l'unité).
     * @param source la source du document.
     * @return l'item complété de son édition, ou l'item intact.
     */
    private fun ajouterEditionImport(
        item: ItemCompletion,
        contexte: ContexteCompletion,
        source: String,
    ): ItemCompletion {
        val statique = item.importStatiqueFqn
        val fqn = item.autoImportFqn ?: statique ?: return item
        if (fqn.contains('$')) return item // imbriqué : jamais importé directement
        if (statique != null) {
            // Import STATIQUE : inutile si déjà présent (exact ou `.*` du porteur).
            for (importe in contexte.importsStatiquesFqcn) {
                if (importe == statique) return item
                if (importe.endsWith(".*") && statique.startsWith(importe.removeSuffix(".*") + ".")) return item
            }
            val insertion = pointInsertionImport(source)
            val edition = jo.codeide.lsp.java.outils.EditionTexte(
                offset = insertion,
                longueur = 0,
                texte = "import static $statique;\n",
            )
            return item.copy(editionsAdditionnelles = item.editionsAdditionnelles + edition)
        }
        val paquetType = fqn.substringBeforeLast('.')
        if (paquetType == contexte.paquetCourant) return item
        if (paquetType == "java.lang") return item
        for (importe in contexte.importsFqcn) {
            if (importe == fqn) return item
            if (importe.endsWith(".*") && fqn.startsWith(importe.removeSuffix(".*") + ".")) return item
        }
        val insertion = pointInsertionImport(source)
        val edition = jo.codeide.lsp.java.outils.EditionTexte(
            offset = insertion,
            longueur = 0,
            texte = "import $fqn;\n",
        )
        return item.copy(editionsAdditionnelles = item.editionsAdditionnelles + edition)
    }

    /**
     * Le point d'insertion d'un import dans l'unité : après le DERNIER
     * import existant, sinon après la déclaration de paquet, sinon au début
     * (balayage ligne à ligne — les lignes `import` incomplètes de la frappe
     * compte aussi : l'insertion reste un import valide à sa suite).
     *
     * @param source la source du document.
     * @return l'offset d'insertion (toujours dans les bornes).
     */
    private fun pointInsertionImport(source: String): Int {
        var position = 0
        var point = 0
        for (ligne in source.lineSequence()) {
            val amorce = ligne.trimStart()
            if (amorce.startsWith("package ") || amorce.startsWith("import ") || amorce.startsWith("import\t")) {
                if (amorce.startsWith("package ") && !amorce.contains(';')) {
                    position += ligne.length + 1
                    continue
                }
                point = position + ligne.length + 1
            }
            position += ligne.length + 1
        }
        return point.coerceAtMost(source.length)
    }

    // ------------------------------------------------------------------ //
    // Filtrage gradué et divers
    // ------------------------------------------------------------------ //

    /** Filtre les candidats par correspondance graduée au préfixe (le grade voyage sur l'item).
     *
     * Le MARQUEUR de splice ([AnalyseurContexte.MARQUEUR]) n'est JAMAIS un
     * candidat légitime : quand le marqueur parse comme une DÉCLARATION
     * (corps de classe, préfixe vide — banc S13a du 31/10/2026), il entrait
     * dans l'inventaire des membres déclarés et fuitait dans la liste rendue
     * (`__codeide_completion__()` visible par l'utilisateur). Il est écarté
     * ICI, au dernier rempart avant l'arrangement — quelle que soit sa
     * source de collecte. */
    private fun filtrer(candidats: List<ItemCompletion>, prefixe: String): List<ItemCompletion> =
        candidats.asSequence()
            .filter { it.libelle != AnalyseurContexte.MARQUEUR }
            .mapNotNull { item ->
                val grade = CorrespondanceGraduee.grade(prefixe, item.libelle)
                if (grade == GradeCorrespondance.AUCUN) return@mapNotNull null
                item.copy(correspondance = grade)
            }
            .toList()

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

        /** Ancres d'invocation retenues (fiche C-08 D4 — mémoire bornée). */
        private const val MAX_ANCRES_INVOCATION: Int = 64

        /** Types BINAIRES mémoïsés (membres + bindings — reste consigné PV 1.6.0). */
        private const val MAX_TYPES_MEMO: Int = 128

        /** Budget d'items de la CHAÎNE (ADR 0026 (b)). */
        private const val BUDGET_CHAINES: Int = 12

        /** Budget de méthodes du PREMIER NIVEAU de la chaîne. */
        private const val BUDGET_CHAINES_PREMIER_NIVEAU: Int = 40

        /** Types porteurs énumérés pour les imports STATIQUES (fiche C-08 D2). */
        private const val MAX_TYPES_STATIQUES: Int = 6

        /**
         * PANNEAU java.lang des porteurs statiques (fiche C-08 D2 — l'exemple
         * de la fiche est `Math.asin`) : les types VISIBLES SANS IMPORT dont
         * les membres statiques sont les plus couramment importés
         * statiquement. Documenté (USAGE §7.21) : le panneau est borné par
         * construction (latence du 2e appel), l'énumération est mémoïsée.
         */
        private val PANNEAU_JAVA_LANG: List<String> = listOf(
            "java.lang.Math",
            "java.lang.System",
            "java.lang.Objects",
            "java.lang.Integer",
            "java.lang.Long",
            "java.lang.Double",
            "java.lang.Boolean",
            "java.lang.Character",
            "java.lang.Short",
            "java.lang.Byte",
            "java.lang.Float",
            "java.lang.StrictMath",
        )

        /**
         * Mots-clés légaux en position de TOP LEVEL (hors de tout corps de
         * type) — déclarations de types, modificateurs de déclaration de
         * type ET mots-clés de tête de déclaration (`extends`,
         * `implements`, `permits` : la tête d'une déclaration vit à
         * profondeur d'accolades zéro — `class Foo e|` doit proposer
         * `extends`). `non-sealed` n'est pas un identifiant simple : absent.
         * Les modificateurs de MEMBRES (transient, volatile, synchronized,
         * native) y sont illégaux : absents.
         */
        private val MOTS_CLES_TOP_NIVEAU: List<String> = listOf(
            "package", "import", "public", "private", "protected",
            "static", "final", "abstract", "strictfp", "sealed",
            "class", "interface", "enum", "record",
            "extends", "implements", "permits",
        )
    }
}
