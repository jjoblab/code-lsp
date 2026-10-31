// CodeIDE — occurrences PROUVÉES (module :kotlin, étage 4 vague 4a). Code
// NEUF jjoblab (clean-room — contrat : fiche 048 du cahier
// docs/cahier-specifications-kotlin/ : le contrat S2 « references /
// documentHighlight / rename borné » du plan de stabilisation LSP §3.2,
// multi-fichiers par la PREUVE DE LIAISON — jamais de faux usage, jamais
// de faux renommage). Le germe de la vague 2e (occurrences périmètre
// document, sans preuve) demeure : la version prouvée vit à côté.
package jo.codeide.lsp.kotlin.session

import com.intellij.platform.syntax.SyntaxElementType
import java.nio.file.Files
import java.nio.file.Paths
import jo.codeide.lsp.kotlin.api.EmplacementKotlin
import jo.codeide.lsp.kotlin.api.GenreSurlignageKotlin
import jo.codeide.lsp.kotlin.api.PlageRenommableKotlin
import jo.codeide.lsp.kotlin.api.SurlignageDocumentKotlin
import jo.codeide.lsp.kotlin.index.VueAppelables
import jo.codeide.lsp.kotlin.parse.JetonBrut
import jo.codeide.lsp.kotlin.parse.NoeudArbre
import jo.codeide.lsp.kotlin.parse.ParseurKotlin
import jo.codeide.lsp.kotlin.parse.kt.KtDirectiveImport
import jo.codeide.lsp.kotlin.parse.kt.KtFichier
import jo.codeide.lsp.kotlin.parse.kt.PlageTexte
import jo.codeide.lsp.kotlin.resolve.ContributionFichier
import jo.codeide.lsp.kotlin.resolve.Couches
import jo.codeide.lsp.kotlin.resolve.EtatScan
import jo.codeide.lsp.kotlin.resolve.MarcheTypee
import jo.codeide.lsp.kotlin.resolve.ModeleProjet
import jo.codeide.lsp.kotlin.resolve.TypeValeur
import jo.codeide.lsp.kotlin.resolve.TypageBorne
import jo.codeide.lsp.kotlin.resolve.UniversPreuves
import jo.codeide.lsp.kotlin.resolve.VueJvmMembres
import org.jetbrains.kotlin.kmp.lexer.KtTokens
import org.jetbrains.kotlin.kmp.parser.KtNodeTypes

/**
 * Le moteur des OCCURRENCES PROUVÉES (étage 4 — fiche 048) : la CIBLE sous
 * le point, puis chaque usage LIÉ — multi-fichiers (document courant,
 * documents ouverts, fichiers FERMÉS du scan re-parsés paresseusement à la
 * demande).
 *
 * ## La cible sous le point
 *
 * - le caret sur le NOM D'UNE DÉCLARATION : la déclaration visée (locale,
 * paramètre, membre — propriété de constructeur comprise —, appelable
 * top-level, type) ;
 * - le caret sur un USAGE NU : la cascade de portées — locale/paramètre
 * lié positionnellement (l'OMBRE la plus récente avant l'usage gagne),
 * membre nu par la PILE des this (le `proprietaireFqcn` du membre de la
 * couche prouve le déclarant), déclaration top-level du MÊME FICHIER
 * d'abord (ADR 0204 : la garde d'homonymie ne confond plus la cible avec
 * un homonyme du paquet), puis type du modèle (paquet préféré, nom
 * unique), import exact, appelable du paquet courant ;
 * - le caret sur un SÉLECTEUR QUALIFIÉ (`b.ouvrir()`) : le typage BORNÉ
 * du récepteur puis la CLÔTURE du type — le `proprietaireFqcn` du membre
 * doit désigner le déclarant de la cible ;
 * - le caret sur le MOT-CLÉ `fun`/`class` d'une déclaration : la
 *   DÉCLARATION ENGLOBANTE (ADR 0218, miroir du repli Android Studio) ;
 *   le curseur dans le CORPS ne vise RIEN (le corps n'est pas l'en-tête).
 *
 * ## La preuve de liaison d'un usage (le cœur conservateur)
 *
 * Un usage du nom dans un fichier F n'entre dans la liste QUE s'il y
 * RÉSOUT vers la cible : par IMPORT EXACT (le segment du chemin importé
 * est une référence à éditer ; un alias rend l'usage sous son ALIAS), par
 * MÊME PAQUET sans homonyme top-level local (l'homonyme local de F PRIME
 * — la copie fantôme du scan d'un document ouvert est naturellement écartée
 * par cette garde), par MÊME FICHIER déclarant, ou — pour les membres —
 * par le typage du récepteur / la pile des this. Une locale ombrageante,
 * un homonyme d'un autre paquet sans import, un sélecteur sans récepteur
 * démontrable restent HORS liste (D3 — jamais de faux usage).
 *
 * Le re-parse paresseux des fichiers FERMÉS est la décision d'architecture
 * de l'étage : le scan ne garde NI texte NI arbre (budget mémoire
 * appareil), la requête de navigation est PONCTUELLE (pas une frappe) —
 * chaque fichier candidat est lu (pré-filtre par contenu) puis parsé à la
 * demande, le coût est documenté au journal §5.
 */
internal object OccurrencesProuvees {

    // ------------------------------------------------------------------
    // Les genres de cible (la portée de la preuve de liaison).
    // ------------------------------------------------------------------

    /**
     * Le genre d'une cible d'occurrences — il décide de la PORTÉE de la
     * preuve de liaison (le fichier déclarant seul pour une locale, le
     * paquet et les imports pour un appelable, la clôture pour un membre).
     */
    internal enum class GenreCible {

        /** Une propriété LOCALE d'un corps de fonction (fichier déclarant). */
        LOCALE,

        /** Un paramètre de fonction (confiné au corps). */
        PARAMETRE,

        /** Un membre de type (fonction, propriété, propriété de constructeur). */
        MEMBRE,

        /** Une fonction ou propriété de premier niveau (paquet + imports). */
        APPELABLE_TOP,

        /** Un type déclaré : classe, interface, objet, enum, typealias. */
        TYPE,
    }

    /**
     * La CIBLE d'une requête d'occurrences : le symbole désigné sous le
     * point, avec tout ce que la preuve de liaison doit savoir de lui.
     *
     * @property nom le nom ÉCRIT de la cible ;
     * @property genre le genre (la portée de la preuve) ;
     * @property uriDeclarante l'URI du fichier portant la DÉCLARATION ;
     * @property plageNom la plage du NOM déclaré dans le fichier déclarant ;
     * @property paquet le paquet du fichier déclarant (`""` à la racine) ;
     * @property fqcnDeclarant pour un [GenreCible.MEMBRE] : le FQCN pointé
     * du type portant le membre (miroir de `MembreHerite.proprietaireFqcn`).
     */
    internal class Cible(
        internal val nom: String,
        internal val genre: GenreCible,
        internal val uriDeclarante: String,
        internal val plageNom: PlageTexte,
        internal val paquet: String,
        internal val fqcnDeclarant: String? = null,
    )

    // ------------------------------------------------------------------
    // Le montage : la matière par fichier (assemblé par la session, hors
    // verrou — les instantanés sont immuables).
    // ------------------------------------------------------------------

    /**
     * Le montage d'une requête d'occurrences : les documents OUVERTS (leurs
     * arbres vifs), les fichiers du SCAN non ouverts (re-parsés à la
     * demande), les contributions au modèle de symboles (ouvertes vives
     * PRIMANT leurs doublons scannés) et le guichet de classpath.
     *
     * @property documents les documents ouverts (URI → document, ordre
     * d'ouverture préservé par la liste des URI) ;
     * @property ordreDocuments les URI des documents ouverts, en ordre
     * d'ouverture ;
     * @property urisScan les URI des fichiers Kotlin `.kt`/`.kts` du scan
     * NON OUVERTS (les `.java` et les groupes synthétiques n'entrent pas :
     * le moteur Kotlin ne renomme pas le Java et les synthétiques n'ont pas
     * de source à éditer) ;
     * @property contributions les contributions au modèle (toutes — le
     * modèle global sert chaque fichier) ;
     * @property index la vue des appelables du classpath (`null` = guichet
     * muet) ;
     * @property vueJvm la vue JVM des membres (`null` = pas d'annuaire) ;
     * @property etatScan l'état du scan au consult.
     */
    internal class Montage(
        private val documents: Map<String, DocumentOuvert>,
        private val ordreDocuments: List<String>,
        private val urisScan: List<String>,
        private val contributions: List<ContributionFichier>,
        private val index: VueAppelables?,
        private val vueJvm: VueJvmMembres?,
        private val etatScan: EtatScan,
    ) {

        /** Le parseur des fichiers fermés (sans état). */
        private val parseur = ParseurKotlin()

        /** Le cache paresseux des arbres des fichiers fermés (null = illisible). */
        private val arbresFermes = HashMap<String, KtFichier?>()

        /** Le cache des textes des fichiers fermés (null = illisible). */
        private val textesFermes = HashMap<String, String?>()

        /** Le cache des univers par URI (un univers par fichier consulté). */
        private val universParUri = HashMap<String, UniversPreuves?>()

        /** Le cache des liaisons par (URI, début de région). */
        private val liaisonsParRegion = HashMap<Pair<String, Int>, MarcheTypee.Liaison>()

        /**
         * Le MODÈLE GLOBAL des symboles (figé UNE fois — chaque univers le
         * partage ; seuls l'arbre et le paquet varient par fichier).
         */
        internal val modele: ModeleProjet by lazy {
            val batisseur = ModeleProjet.Batisseur()
            for (contribution in contributions) batisseur.ajouter(contribution)
            batisseur.figer()
        }

        /**
         * Rend l'ARBRE d'un fichier : l'arbre VIF du document ouvert (le
         * doublon scanné d'un document ouvert n'est JAMAIS servi), sinon le
         * re-parse paresseux du fichier du scan.
         *
         * @param uri l'URI du fichier ;
         * @return l'arbre ; `null` si le fichier est fermé ET illisible.
         */
        internal fun arbreDe(uri: String): KtFichier? {
            documents[uri]?.let { return it.arbre }
            if (uri !in arbresFermes) {
                val texte = texteDe(uri)
                arbresFermes[uri] = texte?.let { parseur.arbre(it, uri.endsWith(".kts")) }
            }
            return arbresFermes[uri]
        }

        /**
         * Rend le TEXTE d'un fichier : le texte vif du document ouvert,
         * sinon la lecture disque (mise en cache pour la requête).
         *
         * @param uri l'URI du fichier ;
         * @return le texte complet ; `null` si illisible (silence honnête).
         */
        internal fun texteDe(uri: String): String? {
            documents[uri]?.let { return it.texte }
            if (uri !in textesFermes) {
                textesFermes[uri] = lireFichier(uri)
            }
            return textesFermes[uri]
        }

        /**
         * Rend l'UNIVERS DES PREUVES d'un fichier (paresseux, mémoïsé) :
         * le modèle GLOBAL partagé, l'arbre et le paquet DU FICHIER — la
         * matière du typage des récepteurs et des piles de CE fichier.
         *
         * @param uri l'URI du fichier ;
         * @return l'univers ; `null` si l'arbre est indisponible.
         */
        internal fun universDe(uri: String): UniversPreuves? {
            if (universParUri.containsKey(uri)) return universParUri[uri]
            val arbre = arbreDe(uri)
            val univers = arbre?.let {
                UniversPreuves(
                    arbre = it,
                    paquet = it.directivePackage()?.nomQualifie().orEmpty(),
                    modele = modele,
                    index = index,
                    etatScan = etatScan,
                    script = uri.endsWith(".kts"),
                    vueJvm = vueJvm,
                )
            }
            universParUri[uri] = univers
            return univers
        }

        /**
         * Rend la LIAISON typée d'une position d'un fichier (paresseuse,
         * mémoïsée par région porteuse — miroir du montage de la complétion).
         *
         * @param uri l'URI du fichier ;
         * @param position l'offset de l'usage à lier ;
         * @return la liaison ; `null` sans univers.
         */
        internal fun liaisonA(uri: String, position: Int): MarcheTypee.Liaison? {
            val univers = universDe(uri) ?: return null
            val arbre = arbreDe(uri) ?: return null
            val cle = uri to (regionContenant(arbre.noeud, position)?.debut ?: -1)
            return liaisonsParRegion.getOrPut(cle) {
                MarcheTypee.Liaison(
                    arbre = arbre,
                    univers = univers,
                    region = regionContenant(arbre.noeud, position),
                )
            }
        }

        /**
         * Les FICHIERS CANDIDATS d'une cible, en ordre de visite : le
         * fichier DÉCLARANT d'abord (ses usages se lient par lui), le
         * document courant, les autres documents ouverts (ordre d'ouverture),
         * puis les fichiers du scan non ouverts (ordre de marche). Le
         * pré-filtre par contenu (le nom ou son alias potentiel doit vivre
         * dans le texte) épargne le re-parse des fichiers étrangers — le
         * déclarant est TOUJOURS candidat.
         *
         * @param cible la cible cherchée ;
         * @param uriCourante l'URI du document consulté ;
         * @return les URI candidates, pré-filtrées.
         */
        internal fun fichiersCandidats(cible: Cible, uriCourante: String): List<String> {
            val ordre = LinkedHashSet<String>()
            ordre.add(cible.uriDeclarante)
            ordre.add(uriCourante)
            for (uri in ordreDocuments) ordre.add(uri)
            for (uri in urisScan) ordre.add(uri)
            return ordre.filter { uri ->
                uri == cible.uriDeclarante || texteDe(uri)?.let { it.contains(cible.nom) } == true
            }
        }

        /** La lecture disque tolérante d'un fichier du scan (null si inaccessible). */
        private fun lireFichier(uri: String): String? {
            return try {
                String(Files.readAllBytes(Paths.get(java.net.URI(uri))), Charsets.UTF_8)
            } catch (_: Exception) {
                null
            }
        }
    }

    // ------------------------------------------------------------------
    // Les entrées (appelées par la session, HORS verrou — le re-parse et
    // la lecture disque ne tiennent jamais le verrou, ADR 0212).
    // ------------------------------------------------------------------

    /**
     * Rend les RÉFÉRENCES PROUVÉES du symbole sous le point (fiche 048).
     *
     * @param document le document ouvert consulté ;
     * @param uri l'URI du document consulté ;
     * @param deplacement l'offset du caret ;
     * @param inclureDeclaration `true` pour la déclaration en tête ;
     * @param montage la matière de la preuve ;
     * @return les emplacements des usages liés (déclaration en tête si
     * demandée) ; liste vide sans cible prouvable.
     */
    internal fun references(
        document: DocumentOuvert,
        uri: String,
        deplacement: Int,
        inclureDeclaration: Boolean,
        montage: Montage,
    ): List<EmplacementKotlin> {
        val cible = cibleSousPoint(document, uri, deplacement, montage) ?: return emptyList()
        val resultat = mutableListOf<EmplacementKotlin>()
        if (inclureDeclaration) {
            resultat.add(EmplacementKotlin(uri = cible.uriDeclarante, plage = cible.plageNom))
        }
        for (uriFichier in montage.fichiersCandidats(cible, uri)) {
            resultat.addAll(usagesDans(uriFichier, cible, montage))
        }
        return resultat
    }

    /**
     * Rend les SURLIGNAGES de document au point (fiche 048 :
     * documentHighlight — périmètre le document SEUL).
     *
     * @param document le document ouvert consulté ;
     * @param uri l'URI du document consulté ;
     * @param deplacement l'offset du caret ;
     * @param montage la matière de la preuve ;
     * @return les surlignages (déclaration ECRITURE en tête si le document
     * la porte, usages LECTURE en ordre du document) ; liste vide sans cible.
     */
    internal fun surlignages(
        document: DocumentOuvert,
        uri: String,
        deplacement: Int,
        montage: Montage,
    ): List<SurlignageDocumentKotlin> {
        val cible = cibleSousPoint(document, uri, deplacement, montage) ?: return emptyList()
        val resultat = mutableListOf<SurlignageDocumentKotlin>()
        if (cible.uriDeclarante == uri) {
            resultat.add(SurlignageDocumentKotlin(plage = cible.plageNom, genre = GenreSurlignageKotlin.ECRITURE))
        }
        for (emplacement in usagesDans(uri, cible, montage)) {
            resultat.add(SurlignageDocumentKotlin(plage = emplacement.plage, genre = GenreSurlignageKotlin.LECTURE))
        }
        return resultat
    }

    /**
     * Rend la PLAGE RENOMMABLE sous le point (fiche 048 : prepareRename —
     * la plage de l'IDENTIFIANT sous le caret, pas celle de la déclaration :
     * le caret peut vivre sur un usage).
     *
     * @param document le document ouvert consulté ;
     * @param deplacement l'offset du caret ;
     * @param montage la matière de la preuve ;
     * @return la plage et son placeholder ; `null` hors identifiant ou sans
     * cible prouvable (jamais deviné).
     */
    internal fun plageRenommable(
        document: DocumentOuvert,
        deplacement: Int,
        montage: Montage,
    ): PlageRenommableKotlin? {
        val jeton = jetonIdentifierA(document.arbre.noeud, deplacement) ?: return null
        if (cibleSousPoint(document, "", deplacement, montage, jeton) == null) return null
        return PlageRenommableKotlin(
            plage = PlageTexte(jeton.debut, jeton.fin),
            placeholder = jeton.texte,
        )
    }

    /**
     * Rend les occurrences à ÉDITER du RENOMMAGE BORNÉ (fiche 048) : la
     * déclaration en tête puis chaque usage LIÉ — multi-fichiers, segments
     * d'import compris.
     *
     * @param document le document ouvert consulté ;
     * @param uri l'URI du document consulté ;
     * @param deplacement l'offset du caret ;
     * @param montage la matière de la preuve ;
     * @return les emplacements à éditer ; liste VIDE sans cible prouvée.
     */
    internal fun renommage(
        document: DocumentOuvert,
        uri: String,
        deplacement: Int,
        montage: Montage,
    ): List<EmplacementKotlin> {
        return references(document, uri, deplacement, inclureDeclaration = true, montage = montage)
    }

    // ------------------------------------------------------------------
    // La cible sous le point.
    // ------------------------------------------------------------------

    /**
     * Résout la CIBLE sous le point : le site de déclaration désigné,
     * l'usage résolu par la cascade de portées, le sélecteur qualifié par
     * le typage du récepteur, ou le repli mot-clé ADR 0218.
     *
     * @param document le document consulté ;
     * @param uri l'URI du document (le repli mot-clé en a besoin pour la
     * cible du site — `""` quand seul l'identifiant intéresse l'appelant) ;
     * @param deplacement l'offset du caret ;
     * @param montage la matière de la preuve ;
     * @param identifiantConnu le jeton identifiant déjà calculé (réutilisé
     * par [plageRenommable] — évite la double marche) ;
     * @return la cible ; `null` sans cible prouvable (D3).
     */
    private fun cibleSousPoint(
        document: DocumentOuvert,
        uri: String,
        deplacement: Int,
        montage: Montage,
        identifiantConnu: JetonBrut? = null,
    ): Cible? {
        val arbre = document.arbre
        val paquet = arbre.directivePackage()?.nomQualifie().orEmpty()
        val sites = sitesDeclaration(arbre)

        // 1. L'IDENTIFIANT sous le caret : le site de déclaration désigné
        // d'abord, puis la cascade de portées de l'usage.
        val identifiant = identifiantConnu ?: jetonIdentifierA(arbre.noeud, deplacement)
        if (identifiant != null) {
            val auSite = sites
                .filter { it.plageNom.debut == identifiant.debut && it.plageNom.fin == identifiant.fin }
                .minByOrNull { it.noeud.fin - it.noeud.debut }
            if (auSite != null) return cibleDuSite(auSite, uri, paquet)
            return cibleDeLUsage(arbre, uri, paquet, sites, identifiant, montage)
        }

        // 2. Le repli MOT-CLÉ (ADR 0218) : la déclaration englobante dont
        // l'EN-TÊTE couvre le caret — le mot-clé de déclaration vit dans
        // l'en-tête, jamais dans le corps.
        val jetonMotCle = jetonMotCleDeclarationA(arbre.noeud, deplacement) ?: return null
        val englobante = sites
            .filter { site ->
                site.noeud.debut <= jetonMotCle.debut && jetonMotCle.fin <= site.noeud.fin &&
                    jetonMotCle.debut < debutDuCorps(site.noeud)
            }
            .minByOrNull { it.noeud.fin - it.noeud.debut }
            ?: return null
        return cibleDuSite(englobante, uri, paquet)
    }

    /**
     * La cible d'un USAGE NU (la cascade de portées — chaque étape prouve
     * ou passe la main, jamais ne devine).
     */
    private fun cibleDeLUsage(
        arbre: KtFichier,
        uri: String,
        paquet: String,
        sites: List<Site>,
        identifiant: JetonBrut,
        montage: Montage,
    ): Cible? {
        val nom = identifiant.texte

        // 1. La LOCALE / le PARAMÈTRE lié positionnellement (l'ombre la plus
        // récente avant l'usage gagne) — l'ombrage est la preuve.
        val liee = declarationLocaleLiee(arbre, identifiant.debut, nom)
        if (liee != null) return cibleDuSite(liee, uri, paquet)

        // 2. Le MEMBRE NU de la pile des this (le proprietaireFqcn prouve
        // le déclarant — propriété de constructeur comprise, ADR 0206).
        val univers = montage.universDe(uri)
        if (univers != null) {
            val liaison = montage.liaisonA(uri, identifiant.debut)
            if (liaison != null) {
                val membre = Couches.aPosition(arbre, univers, liaison, identifiant.debut).membre(nom)
                if (membre != null) {
                    return cibleMembre(nom, membre.proprietaireFqcn, montage)
                }
            }
        }

        // 3. La déclaration TOP-LEVEL du MÊME FICHIER (ADR 0204 : la garde
        // d'homonymie ne confond plus la cible avec un homonyme du paquet).
        val topDuFichier = sites.firstOrNull { it.conteneur == Conteneur.TOP && it.nom == nom }
        if (topDuFichier != null) return cibleDuSite(topDuFichier, uri, paquet)

        // 4. Le TYPE du modèle (paquet préféré, nom globalement unique).
        val type = montage.modele.typeParNomSimple(nom, paquet)
        if (type != null) {
            return cibleType(type.nomSimple, type.fqcn, type.uri, montage)
        }

        // 5. L'import EXACT : un type du PROJET sous le chemin importé (un
        // import de bibliothèque ne se renomme pas ici — silence, D3).
        for (directive in arbre.imports()) {
            if (directive.estJoker()) continue
            val chemin = directive.nomQualifie() ?: continue
            if (chemin.substringAfterLast('.') != nom && directive.alias() != nom) continue
            monteTypeDuChemin(chemin, montage)?.let { return it }
        }

        // 6. L'APPELABLE de premier niveau du PAQUET courant.
        val appelable = montage.modele.appelables(nom, paquet).firstOrNull()
        if (appelable != null) {
            return cibleAppele(nom, appelable.paquetSource, appelable.uri, montage)
        }
        return null
    }

    /** La cible d'un site de déclaration (le genre porte la portée de preuve). */
    private fun cibleDuSite(site: Site, uri: String, paquet: String): Cible? {
        val fqcnEnglobant = (listOf(paquet) + site.chemin).filter { it.isNotEmpty() }.joinToString(".")
        return when {
            site.estType -> Cible(
                nom = site.nom,
                genre = GenreCible.TYPE,
                uriDeclarante = uri,
                plageNom = site.plageNom,
                paquet = paquet,
                fqcnDeclarant = if (fqcnEnglobant.isEmpty()) site.nom else "$fqcnEnglobant.${site.nom}",
            )
            site.conteneur == Conteneur.PRIMAIRE && site.avecValVar -> Cible(
                // Une PROPRIÉTÉ DE CONSTRUCTEUR : le déclarant est la
                // CLASSE PORTEUSE (le chemin des englobants, SANS le nom
                // du membre — miroir de MembreHerite.proprietaireFqcn).
                nom = site.nom,
                genre = GenreCible.MEMBRE,
                uriDeclarante = uri,
                plageNom = site.plageNom,
                paquet = paquet,
                fqcnDeclarant = fqcnEnglobant.takeIf { it.isNotEmpty() },
            )
            site.conteneur == Conteneur.CORPS_CLASSE -> Cible(
                // Un MEMBRE du corps : le déclarant est la classe porteuse.
                nom = site.nom,
                genre = GenreCible.MEMBRE,
                uriDeclarante = uri,
                plageNom = site.plageNom,
                paquet = paquet,
                fqcnDeclarant = fqcnEnglobant.takeIf { it.isNotEmpty() },
            )
            site.conteneur == Conteneur.PRIMAIRE -> Cible(
                // Paramètre NU du constructeur primaire : la portée de
                // construction (la liaison ne le lie pas — usages perdus,
                // jamais faux ; pas couvert par la fiche, D3 assumé).
                nom = site.nom,
                genre = GenreCible.PARAMETRE,
                uriDeclarante = uri,
                plageNom = site.plageNom,
                paquet = paquet,
            )
            site.conteneur == Conteneur.BLOC -> Cible(
                // Une déclaration dans un bloc de fonction (locale — une
                // fonction locale y vit pareil : preuve positionnelle).
                nom = site.nom,
                genre = GenreCible.LOCALE,
                uriDeclarante = uri,
                plageNom = site.plageNom,
                paquet = paquet,
            )
            site.conteneur == Conteneur.PARAMETRES -> Cible(
                nom = site.nom,
                genre = GenreCible.PARAMETRE,
                uriDeclarante = uri,
                plageNom = site.plageNom,
                paquet = paquet,
            )
            else -> Cible(
                nom = site.nom,
                genre = GenreCible.APPELABLE_TOP,
                uriDeclarante = uri,
                plageNom = site.plageNom,
                paquet = paquet,
            )
        }
    }

    /**
     * La cible d'un MEMBRE prouvé par son FQCN déclarant : la déclaration
     * se CHERCHE dans le fichier porteur du type (l'arbre vif du document
     * ouvert, sinon le re-parse du fichier du scan) — sans plage trouvée,
     * la cible n'existe pas (un renommage sans déclaration serait un
     * renommage partiel : 0 conflit manqué exige mieux, D3).
     */
    private fun cibleMembre(nom: String, fqcnDeclarant: String, montage: Montage): Cible? {
        val type = montage.modele.type(fqcnDeclarant) ?: return null
        val uriDeclarante = type.uri ?: return null
        val arbre = montage.arbreDe(uriDeclarante) ?: return null
        val paquet = arbre.directivePackage()?.nomQualifie().orEmpty()
        val plageNom = siteMembreDe(arbre, paquet, fqcnDeclarant, nom) ?: return null
        return Cible(
            nom = nom,
            genre = GenreCible.MEMBRE,
            uriDeclarante = uriDeclarante,
            plageNom = plageNom,
            paquet = paquet,
            fqcnDeclarant = fqcnDeclarant,
        )
    }

    /** La cible d'un TYPE par nom simple + FQCN + URI déclarante. */
    private fun cibleType(nom: String, fqcn: String, uri: String?, montage: Montage): Cible? {
        if (uri == null) return null
        val arbre = montage.arbreDe(uri) ?: return null
        val paquet = arbre.directivePackage()?.nomQualifie().orEmpty()
        val plageNom = siteTypeDe(arbre, fqcn) ?: return null
        return Cible(
            nom = nom,
            genre = GenreCible.TYPE,
            uriDeclarante = uri,
            plageNom = plageNom,
            paquet = paquet,
            fqcnDeclarant = fqcn,
        )
    }

    /** La cible d'un APPELABLE top-level (paquet source + URI déclarante). */
    private fun cibleAppele(nom: String, paquet: String, uri: String, montage: Montage): Cible? {
        val arbre = montage.arbreDe(uri) ?: return null
        val site = sitesDeclaration(arbre)
            .firstOrNull { it.conteneur == Conteneur.TOP && it.nom == nom } ?: return null
        return Cible(
            nom = nom,
            genre = GenreCible.APPELABLE_TOP,
            uriDeclarante = uri,
            plageNom = site.plageNom,
            paquet = paquet,
        )
    }

    /** La cible du type du modèle porté par un chemin importé (étape 5). */
    private fun monteTypeDuChemin(chemin: String, montage: Montage): Cible? {
        val type = montage.modele.type(chemin) ?: return null
        return cibleType(type.nomSimple, type.fqcn, type.uri, montage)
    }

    // ------------------------------------------------------------------
    // Les usages liés d'un fichier.
    // ------------------------------------------------------------------

    /**
     * Rend les USAGES LIÉS de la cible dans UN fichier (la preuve de
     * liaison par genre de cible).
     *
     * @param uriFichier l'URI du fichier candidat ;
     * @param cible la cible cherchée ;
     * @param montage la matière de la preuve ;
     * @return les emplacements des usages liés, en ordre du document.
     */
    private fun usagesDans(uriFichier: String, cible: Cible, montage: Montage): List<EmplacementKotlin> {
        val arbre = montage.arbreDe(uriFichier) ?: return emptyList()
        val estDeclarant = uriFichier == cible.uriDeclarante
        val paquetF = arbre.directivePackage()?.nomQualifie().orEmpty()

        // L'import EXACT de la cible dans ce fichier (alias compris) — le
        // segment du chemin importé est une référence à éditer.
        val cheminCible = when (cible.genre) {
            GenreCible.TYPE -> cible.fqcnDeclarant ?: cible.nom
            GenreCible.APPELABLE_TOP -> if (cible.paquet.isEmpty()) cible.nom else "${cible.paquet}.${cible.nom}"
            else -> null
        }
        val importExact = cheminCible?.let { chemin ->
            arbre.imports().firstOrNull { directive ->
                !directive.estJoker() && directive.nomQualifie() == chemin
            }
        }
        val alias = importExact?.alias()

        // Les noms cherchés : le nom de la cible, et l'ALIAS sous lequel le
        // fichier l'importe (les usages d'alias sont des usages liés).
        val nomsCherches = mutableSetOf(cible.nom)
        if (alias != null) nomsCherches.add(alias)

        val usages = collecterUsages(arbre, nomsCherches)
        val resultat = mutableListOf<EmplacementKotlin>()

        // La DIRECTIVE d'import exacte : le segment du chemin (le nom) est
        // une référence à éditer — miroir IntelliJ (le renommage liste
        // aussi l'import à renommer).
        if (importExact != null) {
            segmentImporte(importExact, cible.nom)?.let { plage ->
                resultat.add(EmplacementKotlin(uri = uriFichier, plage = plage))
            }
        }

        when (cible.genre) {
            GenreCible.LOCALE, GenreCible.PARAMETRE -> {
                if (!estDeclarant) return resultat
                for (usage in usages) {
                    if (usage.estSelecteur || usage.dansType) continue
                    val liee = declarationLocaleLiee(arbre, usage.debut, cible.nom)
                    if (liee != null && liee.plageNom == cible.plageNom) {
                        resultat.add(emplacementDe(uriFichier, usage))
                    }
                }
            }
            GenreCible.MEMBRE -> {
                val univers = montage.universDe(uriFichier)
                if (univers == null) return resultat
                // La garde d'HOMONYMIE DÉCLARANTE : un fichier qui DÉCLARE
                // lui-même ce membre (même fqcn déclarant) lie ses usages à
                // SA déclaration — la COPIE FANTÔME du scan d'un document
                // ouvert (URI distincte, même contenu) est écartée ainsi,
                // miroir de la garde top-level des appelables et des types.
                if (!estDeclarant &&
                    siteMembreDe(arbre, paquetF, cible.fqcnDeclarant.orEmpty(), cible.nom) != null
                ) {
                    return resultat
                }
                for (usage in usages) {
                    if (usage.estSelecteur) {
                        // Le sélecteur QUALIFIÉ : le typage du récepteur puis
                        // la clôture — le proprietaireFqcn désigne le déclarant.
                        val qualifie = usage.qualifie ?: continue
                        val recepteur = qualifie.enfants.firstOrNull() ?: continue
                        val liaison = montage.liaisonA(uriFichier, recepteur.debut) ?: continue
                        val type = TypageBorne.typeDeLexpression(recepteur, univers, liaison)
                        if (type is TypeValeur.Projet) {
                            val membre = univers.clotureDe(type.type)?.membre(cible.nom)
                            if (membre != null && membre.proprietaireFqcn == cible.fqcnDeclarant) {
                                resultat.add(emplacementDe(uriFichier, usage))
                            }
                        }
                    } else {
                        // L'usage NU : la PILE des this à la position — le
                        // proprietaireFqcn du membre de la couche.
                        val liaison = montage.liaisonA(uriFichier, usage.debut) ?: continue
                        val membre = Couches.aPosition(arbre, univers, liaison, usage.debut)
                            .membre(usage.texte)
                        if (membre != null && membre.proprietaireFqcn == cible.fqcnDeclarant) {
                            resultat.add(emplacementDe(uriFichier, usage))
                        }
                    }
                }
            }
            GenreCible.APPELABLE_TOP -> {
                val visible = estDeclarant ||
                    importExact != null ||
                    visibleParPaquet(arbre, cible, montage, chercherTypes = false)
                if (visible) {
                    for (usage in usages) {
                        if (usage.estSelecteur || usage.dansType) continue
                        if (alias != null && usage.texte == cible.nom && !estDeclarant) continue
                        if (alias == null && usage.texte != cible.nom) continue
                        resultat.add(emplacementDe(uriFichier, usage))
                    }
                }
            }
            GenreCible.TYPE -> {
                val visible = estDeclarant ||
                    importExact != null ||
                    visibleParPaquet(arbre, cible, montage, chercherTypes = true)
                if (visible) {
                    for (usage in usages) {
                        if (usage.estSelecteur) continue
                        if (alias != null && usage.texte == cible.nom && !estDeclarant) continue
                        if (alias == null && usage.texte != cible.nom) continue
                        resultat.add(emplacementDe(uriFichier, usage))
                    }
                }
            }
        }
        return resultat.sortedBy { it.plage.debut }
    }

    /**
     * Le nom est-il VISIBLE PAR PAQUET dans le fichier : même paquet que
     * la cible SANS homonyme top-level local de la même famille, ou JOKER
     * porteur unique (l'ambiguïté de jokers rend le silence — deux jokers
     * porteurs du même nom ne prouvent rien, D3).
     *
     * @param arbre l'arbre du fichier de l'usage ;
     * @param cible la cible cherchée ;
     * @param montage la matière (le modèle) ;
     * @param chercherTypes `true` pour la famille des TYPES, `false` pour
     * celle des APPELABLES (les familles ne se masquent pas entre elles).
     */
    private fun visibleParPaquet(arbre: KtFichier, cible: Cible, montage: Montage, chercherTypes: Boolean): Boolean {
        val paquetF = arbre.directivePackage()?.nomQualifie().orEmpty()
        val homonymeLocal = homonymeTopLevel(arbre, cible.nom, chercherTypes)
        if (paquetF == cible.paquet && !homonymeLocal) return true

        // Les JOKERS porteurs : les jokers dont le paquet rend le nom visible
        // (un appelable ou un type du modèle y vit) — exactement UN porteur
        // qui est le paquet de la cible prouve, sinon silence.
        val jokers = arbre.imports().filter { it.estJoker() }.mapNotNull { it.nomQualifie() }
        if (jokers.isEmpty()) return false
        val porteurs = jokers.filter { paquetJ ->
            if (chercherTypes) {
                montage.modele.typeParNomSimple(cible.nom, paquetJ) != null
            } else {
                montage.modele.appelables(cible.nom, paquetJ).isNotEmpty()
            }
        }
        return porteurs.size == 1 && porteurs.single() == cible.paquet && !homonymeLocal
    }

    /**
     * Le fichier porte-t-il un HOMONYME top-level de la même famille (la
     * déclaration locale du fichier PRIME — l'usage désigne ELLE).
     */
    private fun homonymeTopLevel(arbre: KtFichier, nom: String, chercherTypes: Boolean): Boolean {
        for (site in sitesDeclaration(arbre)) {
            if (site.conteneur != Conteneur.TOP || site.nom != nom) continue
            if (chercherTypes == site.estType) return true
        }
        return false
    }

    /**
     * La PLAGE du SEGMENT importé d'une directive : le dernier segment du
     * chemin (`outils.triple` → la plage de `triple`) — la référence à
     * éditer au renommage, jamais l'alias (miroir IntelliJ).
     */
    private fun segmentImporte(directive: KtDirectiveImport, nom: String): PlageTexte? {
        val chemin = directive.premierEnfantDeGenre(KtNodeTypes.DOT_QUALIFIED_EXPRESSION)
            ?: directive.premierEnfantDeGenre(KtNodeTypes.REFERENCE_EXPRESSION)
            ?: return null
        val segments = collecterUsagesArbre(chemin, setOf(nom))
        return segments.lastOrNull()?.let { PlageTexte(it.debut, it.fin) }
    }

    /** L'emplacement d'un usage candidat. */
    private fun emplacementDe(uri: String, usage: Usage): EmplacementKotlin =
        EmplacementKotlin(uri = uri, plage = PlageTexte(usage.debut, usage.fin))

    // ------------------------------------------------------------------
    // Les marches de l'arbre.
    // ------------------------------------------------------------------

    /** Le conteneur d'un site (la portée de sa preuve). */
    private enum class Conteneur {
        /** Premier niveau du fichier (ou du bloc d'un script). */
        TOP,

        /** Corps de classe (membre). */
        CORPS_CLASSE,

        /** Bloc de fonction (locale). */
        BLOC,

        /** Liste de paramètres d'une fonction. */
        PARAMETRES,

        /** Constructeur primaire (paramètre, propriété de constructeur). */
        PRIMAIRE,
    }

    /**
     * Un SITE de déclaration : toute déclaration nommée du document, avec
     * son conteneur, son chemin de types englobants et sa PORTÉE (le bloc
     * d'une locale, la fonction d'un paramètre — la preuve positionnelle
     * s'y joue).
     */
    private class Site(
        internal val noeud: NoeudArbre,
        internal val genreNoeud: SyntaxElementType,
        internal val nom: String,
        internal val plageNom: PlageTexte,
        internal val conteneur: Conteneur,
        internal val chemin: List<String>,
        internal val avecValVar: Boolean,
        internal val portee: NoeudArbre?,
    ) {

        /** Le site déclare-t-il un TYPE (classe, objet, enum, typealias). */
        internal val estType: Boolean =
            genreNoeud == KtNodeTypes.CLASS ||
                genreNoeud == KtNodeTypes.OBJECT_DECLARATION ||
                genreNoeud == KtNodeTypes.TYPEALIAS
    }

    /**
     * Collecte TOUS les sites de déclaration d'un document (marche
     * récursive — premier niveau, membres, imbriqués, locales, paramètres).
     *
     * La PORTÉE suit la marche : le BLOC d'une locale (elle y vit jusqu'à
     * sa fin), la FUN d'un paramètre (il vit dans tout son corps) — les
     * paramètres de LAMBDA n'ont pas de FUN porteuse (portée `null` : la
     * preuve positionnelle ne les lie pas, D3 assumé).
     *
     * @param arbre la racine du document ;
     * @return les sites en ordre du document.
     */
    private fun sitesDeclaration(arbre: KtFichier): List<Site> {
        val resultat = mutableListOf<Site>()
        val racine = arbre.noeud
        val porteurs: List<NoeudArbre> =
            racine.enfants.firstOrNull { it.genre == KtNodeTypes.SCRIPT }
                ?.enfants?.firstOrNull { it.genre == KtNodeTypes.BLOCK }?.enfants
                ?: racine.enfants

        fun nomDe(noeud: NoeudArbre): String? =
            noeud.jetonsPropres.firstOrNull { it.genre == KtTokens.IDENTIFIER }?.texte

        fun marcher(
            noeud: NoeudArbre,
            conteneur: Conteneur,
            chemin: List<String>,
            dansPrimaire: Boolean,
            portee: NoeudArbre?,
        ) {
            val estDeclaration = noeud.genre in FabriqueGenres.DECLARATIONS
            val estParametre = noeud.genre == KtNodeTypes.VALUE_PARAMETER
            if (estDeclaration || estParametre) {
                val nom = nomDe(noeud)
                if (nom != null) {
                    val plage = noeud.jetonsPropres
                        .firstOrNull { it.genre == KtTokens.IDENTIFIER }
                        ?.let { PlageTexte(it.debut, it.fin) }
                    if (plage != null) {
                        val conteneurSite = when {
                            estParametre && dansPrimaire -> Conteneur.PRIMAIRE
                            estParametre -> Conteneur.PARAMETRES
                            else -> conteneur
                        }
                        resultat.add(
                            Site(
                                noeud = noeud,
                                genreNoeud = noeud.genre,
                                nom = nom,
                                plageNom = plage,
                                conteneur = conteneurSite,
                                chemin = chemin,
                                avecValVar = noeud.jetonsPropres.any {
                                    it.genre == KtTokens.VAL_KEYWORD || it.genre == KtTokens.VAR_KEYWORD
                                },
                                portee = portee,
                            ),
                        )
                    }
                }
                // Un type déclaré ouvre un CHEMIN pour ses membres — le
                // constructeur PRIMAIRE (explicite ou liste directe) y
                // entre, le corps de classe aussi. Un type TRONQUÉ sans
                // nom n'ouvre pas de chemin (ses membres restent au
                // chemin courant — jamais faux).
                if (estDeclaration &&
                    (noeud.genre == KtNodeTypes.CLASS || noeud.genre == KtNodeTypes.OBJECT_DECLARATION)
                ) {
                    val cheminDesMembres = if (nom != null) chemin + nom else chemin
                    for (enfant in noeud.enfants) {
                        val primaire = enfant.genre == KtNodeTypes.PRIMARY_CONSTRUCTOR ||
                            enfant.genre == KtNodeTypes.VALUE_PARAMETER_LIST
                        marcher(
                            enfant,
                            if (enfant.genre == KtNodeTypes.CLASS_BODY) Conteneur.CORPS_CLASSE else conteneur,
                            cheminDesMembres,
                            primaire,
                            portee,
                        )
                    }
                    return
                }
            }
            for (enfant in noeud.enfants) {
                val conteneurEnfant = when (enfant.genre) {
                    KtNodeTypes.CLASS_BODY -> Conteneur.CORPS_CLASSE
                    // Un BLOC rencontré en cours de marche est TOUJOURS un
                    // CORPS (de fonction, de lambda, de contrôle) — le bloc
                    // RACINE d'un script vit à part : ses declarations sont
                    // exposées au niveau TOP par les porteurs (jamais par
                    // ici, la marche n'y descend pas).
                    KtNodeTypes.BLOCK -> Conteneur.BLOC
                    else -> conteneur
                }
                val porteeEnfant = when {
                    // Les paramètres directs d'une FUN vivent dans TOUT son
                    // corps — la FUN porteuse est leur portée.
                    noeud.genre == KtNodeTypes.FUN &&
                        enfant.genre == KtNodeTypes.VALUE_PARAMETER_LIST -> noeud
                    // Les déclarations d'un BLOC y vivent jusqu'à sa fin.
                    enfant.genre == KtNodeTypes.BLOCK && conteneurEnfant == Conteneur.BLOC -> enfant
                    else -> portee
                }
                marcher(
                    enfant,
                    conteneurEnfant,
                    chemin,
                    dansPrimaire || enfant.genre == KtNodeTypes.PRIMARY_CONSTRUCTOR,
                    porteeEnfant,
                )
            }
        }
        for (porteur in porteurs) {
            if (porteur.genre == KtNodeTypes.PACKAGE_DIRECTIVE || porteur.genre == KtNodeTypes.IMPORT_LIST) continue
            marcher(porteur, Conteneur.TOP, emptyList(), false, null)
        }
        return resultat
    }

    /**
     * Le SITE du membre d'un type (recherche par FQCN déclarant + nom) —
     * le corps de classe ET le constructeur primaire (`val`/`var`).
     */
    private fun siteMembreDe(arbre: KtFichier, paquet: String, fqcnDeclarant: String, nom: String): PlageTexte? {
        for (site in sitesDeclaration(arbre)) {
            if (site.nom != nom) continue
            if (site.conteneur != Conteneur.CORPS_CLASSE && site.conteneur != Conteneur.PRIMAIRE) continue
            if (site.conteneur == Conteneur.PRIMAIRE && !site.avecValVar) continue
            val fqcnSite = (listOf(paquet) + site.chemin).filter { it.isNotEmpty() }.joinToString(".")
            if (fqcnSite == fqcnDeclarant) return site.plageNom
        }
        return null
    }

    /** Le SITE du type déclaré par FQCN (la plage de son nom). */
    private fun siteTypeDe(arbre: KtFichier, fqcn: String): PlageTexte? {
        val paquet = arbre.directivePackage()?.nomQualifie().orEmpty()
        for (site in sitesDeclaration(arbre)) {
            if (!site.estType || site.conteneur != Conteneur.TOP && site.conteneur != Conteneur.CORPS_CLASSE) continue
            val fqcnSite = (listOf(paquet) + site.chemin + site.nom)
                .filter { it.isNotEmpty() }
                .joinToString(".")
            if (fqcnSite == fqcn) return site.plageNom
        }
        return null
    }

    /**
     * La LOCALE ou le PARAMÈTRE lié à une position (la résolution
     * positionnelle — miroir de `MarcheTypee.Liaison` : le site doit ÊTRE
     * EN PORTÉE à la position — sa FUN pour un paramètre, son BLOC pour
     * une locale — et déclaré AVANT elle ; le plus RÉCEMMENT DÉCLARÉ
     * gagne, l'ombre interne comprise. Les paramètres NUS du constructeur
     * primaire ne lient pas (ils relèvent du membre).
     *
     * @param arbre la racine du document ;
     * @param position l'offset de l'usage ;
     * @param nom le nom lié cherché ;
     * @return le site de la déclaration liée ; `null` si aucune portée locale
     * ne porte le nom à cette position.
     */
    private fun declarationLocaleLiee(arbre: KtFichier, position: Int, nom: String): Site? {
        return sitesDeclaration(arbre)
            .filter { site ->
                (site.conteneur == Conteneur.PARAMETRES || site.conteneur == Conteneur.BLOC) &&
                    site.nom == nom &&
                    site.noeud.fin <= position &&
                    site.portee != null &&
                    site.portee.debut <= position && position <= site.portee.fin
            }
            .maxByOrNull { it.noeud.fin }
    }

    /** Un usage candidat collecté par la marche des références. */
    private class Usage(
        internal val debut: Int,
        internal val fin: Int,
        internal val texte: String,
        internal val estSelecteur: Boolean,
        internal val qualifie: NoeudArbre?,
        internal val dansType: Boolean,
    )

    /**
     * Collecte les USAGES CANDIDATS d'un document : les références des noms
     * cherchés (les directives d'import et de package EXCLUES — le segment
     * d'import se prouve à part), avec leurs drapeaux de preuve.
     *
     * @param arbre la racine du document ;
     * @param noms les noms cherchés (le nom de la cible et son alias) ;
     * @return les usages en ordre du document.
     */
    private fun collecterUsages(arbre: KtFichier, noms: Set<String>): List<Usage> {
        val resultat = mutableListOf<Usage>()
        marcherUsages(arbre.noeud, noms, dansType = false, resultat)
        return resultat
    }

    /** La marche des références d'un sous-arbre (segment d'import compris). */
    private fun collecterUsagesArbre(racine: NoeudArbre, noms: Set<String>): List<Usage> {
        val resultat = mutableListOf<Usage>()
        marcherUsages(racine, noms, dansType = false, resultat)
        return resultat
    }

    /**
     * La marche des usages : une REFERENCE_EXPRESSION du nom cherché est
     * candidate ; le DERNIER segment d'un qualifié (`b.ouvrir()`) porte le
     * drapeau sélecteur (sa preuve passe par le typage du récepteur), les
     * références sous TYPE_REFERENCE portent le drapeau de type.
     */
    private fun marcherUsages(
        noeud: NoeudArbre,
        noms: Set<String>,
        dansType: Boolean,
        resultat: MutableList<Usage>,
    ) {
        when (noeud.genre) {
            KtNodeTypes.PACKAGE_DIRECTIVE, KtNodeTypes.IMPORT_LIST, KtNodeTypes.IMPORT_DIRECTIVE -> return
            KtNodeTypes.TYPE_REFERENCE -> {
                for (enfant in noeud.enfants) marcherUsages(enfant, noms, true, resultat)
                return
            }
            KtNodeTypes.DOT_QUALIFIED_EXPRESSION, KtNodeTypes.SAFE_ACCESS_EXPRESSION -> {
                val recepteur = noeud.enfants.firstOrNull()
                val selecteur = noeud.enfants.lastOrNull()
                if (recepteur != null && selecteur != null && recepteur !== selecteur) {
                    marcherUsages(recepteur, noms, dansType, resultat)
                    poserSelecteur(selecteur, noeud, noms, dansType, resultat)
                    // Les arguments du sélecteur appelé se marchent aussi.
                    if (selecteur.genre == KtNodeTypes.CALL_EXPRESSION) {
                        for (enfant in selecteur.enfants) {
                            if (enfant.genre == KtNodeTypes.REFERENCE_EXPRESSION) continue
                            marcherUsages(enfant, noms, dansType, resultat)
                        }
                    }
                    return
                }
            }
            else -> Unit
        }
        if (noeud.genre == KtNodeTypes.REFERENCE_EXPRESSION) {
            val texte = noeud.texteCanonique
            if (texte in noms) {
                resultat.add(
                    Usage(
                        debut = noeud.debut,
                        fin = noeud.fin,
                        texte = texte,
                        estSelecteur = false,
                        qualifie = null,
                        dansType = dansType,
                    ),
                )
            }
            return
        }
        for (enfant in noeud.enfants) marcherUsages(enfant, noms, dansType, resultat)
    }

    /** Pose le drapeau SÉLECTEUR sur la référence du dernier segment. */
    private fun poserSelecteur(
        selecteur: NoeudArbre,
        qualifie: NoeudArbre,
        noms: Set<String>,
        dansType: Boolean,
        resultat: MutableList<Usage>,
    ) {
        when (selecteur.genre) {
            KtNodeTypes.REFERENCE_EXPRESSION -> {
                val texte = selecteur.texteCanonique
                if (texte in noms) {
                    resultat.add(
                        Usage(
                            debut = selecteur.debut,
                            fin = selecteur.fin,
                            texte = texte,
                            estSelecteur = true,
                            qualifie = qualifie,
                            dansType = dansType,
                        ),
                    )
                }
            }
            KtNodeTypes.CALL_EXPRESSION -> {
                val callee = selecteur.enfants.firstOrNull { it.genre == KtNodeTypes.REFERENCE_EXPRESSION }
                if (callee != null) {
                    val texte = callee.texteCanonique
                    if (texte in noms) {
                        resultat.add(
                            Usage(
                                debut = callee.debut,
                                fin = callee.fin,
                                texte = texte,
                                estSelecteur = true,
                                qualifie = qualifie,
                                dansType = dansType,
                            ),
                        )
                    }
                }
            }
            else -> Unit
        }
    }

    // ------------------------------------------------------------------
    // Les jetons sous le caret.
    // ------------------------------------------------------------------

    /** Les genres de nœuds qui sont des déclarations nommées (miroir FabriqueKt). */
    private object FabriqueGenres {
        internal val DECLARATIONS: Set<SyntaxElementType> = setOf(
            KtNodeTypes.CLASS,
            KtNodeTypes.OBJECT_DECLARATION,
            KtNodeTypes.FUN,
            KtNodeTypes.PROPERTY,
            KtNodeTypes.TYPEALIAS,
            KtNodeTypes.ENUM_ENTRY,
        )
    }

    /** Le jeton IDENTIFIER qui contient l'offset du caret (ou `null`). */
    private fun jetonIdentifierA(racine: NoeudArbre, deplacement: Int): JetonBrut? {
        var trouve: JetonBrut? = null
        fun marcher(noeud: NoeudArbre) {
            for (jeton in noeud.jetonsPropres) {
                if (jeton.genre == KtTokens.IDENTIFIER && jeton.debut <= deplacement && deplacement < jeton.fin) {
                    trouve = jeton
                }
            }
            noeud.enfants.forEach(::marcher)
        }
        marcher(racine)
        return trouve
    }

    /**
     * Le jeton MOT-CLÉ DE DÉCLARATION sous le caret (ADR 0218) : `fun`,
     * `class`, `interface`, `object`, `val`, `var`, `typealias` — le
     * vocabulaire des en-têtes de déclaration.
     */
    private fun jetonMotCleDeclarationA(racine: NoeudArbre, deplacement: Int): JetonBrut? {
        val motsCles = setOf(
            KtTokens.FUN_MODIFIER,
            KtTokens.CLASS_KEYWORD,
            KtTokens.INTERFACE_KEYWORD,
            KtTokens.OBJECT_KEYWORD,
            KtTokens.VAL_KEYWORD,
            KtTokens.VAR_KEYWORD,
            KtTokens.TYPE_ALIAS_KEYWORD,
        )
        var trouve: JetonBrut? = null
        fun marcher(noeud: NoeudArbre) {
            for (jeton in noeud.jetonsPropres) {
                if (jeton.genre in motsCles && jeton.debut <= deplacement && deplacement < jeton.fin) {
                    trouve = jeton
                }
            }
            noeud.enfants.forEach(::marcher)
        }
        marcher(racine)
        return trouve
    }

    /**
     * Le DÉBUT DU CORPS d'une déclaration (BLOCK d'une fonction, CLASS_BODY
     * d'un type) — l'EN-TÊTE vit avant ; une déclaration sans corps est
     * toute en-tête (corps d'expression compris).
     */
    private fun debutDuCorps(noeud: NoeudArbre): Int {
        val corps = noeud.enfants.firstOrNull {
            it.genre == KtNodeTypes.BLOCK || it.genre == KtNodeTypes.CLASS_BODY
        }
        return corps?.debut ?: noeud.fin
    }

    /**
     * La RÉGION contenant un point : la plus profonde déclaration de
     * fonction ou de propriété (miroir de la complétion — la liaison y vit).
     */
    private fun regionContenant(racine: NoeudArbre, deplacement: Int): NoeudArbre? {
        var trouve: NoeudArbre? = null
        fun marcher(noeud: NoeudArbre) {
            if (deplacement < noeud.debut || deplacement > noeud.fin) return
            if (noeud.genre == KtNodeTypes.FUN || noeud.genre == KtNodeTypes.PROPERTY) {
                trouve = noeud
            }
            noeud.enfants.forEach(::marcher)
        }
        marcher(racine)
        return trouve
    }
}
