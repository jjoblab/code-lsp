// CodeIDE — scan DÉDIÉ post-build des racines GÉNÉRÉES (module :kotlin,
// vague V-E2 — ADR 0127 Option 1 : levée de l'exclusion H2, miroir Java
// 0021 R1 « généré réel > synthétique »). Code NEUF jjoblab (clean-room —
// le scan des SOURCES (ScanProjetParesseux, ADR 0212) EXCLUT `build/` :
// les racines générées vivent dans un scan SÉPARÉ, la priorité est la
// règle — une classe générée RÉELLE (KSP/KAPT : Room `*_Impl`, Hilt
// `Hilt_*`, Safe Args, Moshi) efface le groupe synthétique homonyme).
package jo.codeide.lsp.kotlin.session

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import jo.codeide.lsp.kotlin.parse.ParseurKotlin
import jo.codeide.lsp.kotlin.parse.kt.KtFichier
import org.jetbrains.kotlin.kmp.lexer.KtTokens
import org.jetbrains.kotlin.kmp.parser.KtNodeTypes

/**
 * Une déclaration GÉNÉRÉE lue d'une racine `build/generated` : la matière
 * du scan post-build (V-E2) — les types et leurs membres, pour la
 * complétion, la navigation et le typage sur le code généré.
 *
 * @property nom le nom de la déclaration ;
 * @property uri l'uri du fichier généré ;
 * @property genre le genre (CLASS/OBJECT/ENUM…) ;
 * @property membres les membres déclarés (fonctions, propriétés) ;
 * @property supertypesEcrits la clause ÉCRITE (`: Comparable<…>` tronqué
 * au premier segment générique) ;
 * @property ligne la ligne (0-based) du NOM dans le fichier généré
 * (navigation vers le généré).
 */
internal class DeclarationGeneree internal constructor(
    internal val nom: String,
    internal val uri: String,
    internal val genre: String,
    internal val membres: List<MembreGenere>,
    internal val supertypesEcrits: List<String>,
    internal val ligne: Int,
)

/**
 * Un membre généré (fonction ou propriété d'une classe générée).
 *
 * @property nom le nom du membre ;
 * @property genre le genre (`fun`/`val`/`var`) ;
 * @property typeRetour le type de retour ÉCRIT (`null` si absent) ;
 * @property parametres les noms des paramètres (arité, 0129) ;
 * @property estAbstrait le membre est abstrait (sans corps) ;
 */
internal class MembreGenere internal constructor(
    internal val nom: String,
    internal val genre: String,
    internal val typeRetour: String?,
    internal val parametres: List<String>,
    internal val estAbstrait: Boolean,
)

/**
 * Le scan des racines GÉNÉRÉES `build/generated` (V-E2 — ADR 0127 : la
 * levée de H2, miroir Java 0021 R1). Les racines se DÉTECTENT par
 * convention AGP : sous chaque racine source du projet, le sous-arbre
 * `build/generated` (récursif) (KSP : `ksp/`, KAPT : `kapt/`, AGP : `generated/`)
 * porte les sources générées — LISIBLES SANS SYNC (le disque est la
 * source de vérité, miroir 0130).
 *
 * La PRIORITÉ est la règle (0021 R1) : une classe générée RÉELLE du scan
 * PRIME le groupe synthétique homonyme (la coupure
 * `ClassesSynthetiques`) et les conventions de nom muettes
 * (`estNomGenere`) : le nom `Hilt_MonApp`/`MonDao_Impl` RÉSOUT parce que
 * sa SOURCE existe — la convention ne rend plus MUET un nom dont la
 * source générée est LUE.
 *
 * Tolérance D3 : une racine illisible, un fichier non parsable sont
 * IGNORÉS (partiel honnête) ; l'absence de build (avant la première
 * génération) rend un snapshot VIDE — l'atout « avant build » du
 * synthétique meurt alors avec honnêteté (le synthétique n'est PAS
 * effacé sans preuve réelle).
 */
internal class ScanGenerees internal constructor() {

    /** Les déclarations par nom (la première gagne — ordre de marche trié). */
    @Volatile
    private var publiees: Map<String, DeclarationGeneree> = emptyMap()

    /** Les racines couvertes par le snapshot publié. */
    @Volatile
    private var racinesPubliees: Set<String> = emptySet()

    /** Le cache par fichier (mtime) — un fichier inchangé n'est jamais re-parsé. */
    private val cacheFichiers = ConcurrentHashMap<String, Pair<Long, List<DeclarationGeneree>>>()

    /** Le parseur (sans état). */
    private val parseur = ParseurKotlin()

    /**
     * RE-PASSE les racines générées des racines sources données (détectées
     * par convention AGP : `build/generated` (récursif) sous chaque racine).
     *
     * @param racinesSources les racines sources du projet (le module et
     * ses dépendances — les mêmes que le scan des sources) ;
     * @return les déclarations générées (par nom, la première gagne).
     */
    internal fun repasser(racinesSources: List<String>): Map<String, DeclarationGeneree> {
        val racinesGenerees = racinesSources.flatMap(::racinesGenereesDe).sorted()
        val carte = LinkedHashMap<String, DeclarationGeneree>()
        for (racine in racinesGenerees) {
            val dossier =
                try {
                    Paths.get(racine)
                } catch (_: Exception) {
                    continue
                }
            if (!Files.isDirectory(dossier)) continue
            val fichiers =
                try {
                    Files.walk(dossier).use { flux ->
                        flux.filter { f ->
                            Files.isRegularFile(f) && f.fileName.toString().endsWith(".kt")
                        }.sorted().toList()
                    }
                } catch (_: Exception) {
                    continue
                }
            for (fichier in fichiers) {
                val cle = fichier.toString()
                val mtime =
                    try {
                        Files.getLastModifiedTime(fichier).toMillis()
                    } catch (_: Exception) {
                        continue
                    }
                val enCache = cacheFichiers[cle]
                val declarations =
                    if (enCache != null && enCache.first == mtime) {
                        enCache.second
                    } else {
                        val lues = lireDeclarations(fichier)
                        cacheFichiers[cle] = Pair(mtime, lues)
                        lues
                    }
                for (declaration in declarations) {
                    if (declaration.nom !in carte) carte[declaration.nom] = declaration
                }
            }
        }
        // Purge : le cache ne garde que les vivants de la marche.
        cacheFichiers.keys.retainAll(racinesGenerees.toSet())
        publiees = carte
        racinesPubliees = racinesGenerees.toSet()
        return carte
    }

    /**
     * Les déclarations générées publiées (sans re-passe — l'état servi).
     *
     * @return la carte nom → déclaration (VIDE avant la première passe).
     */
    internal fun publiées(): Map<String, DeclarationGeneree> = publiees

    /**
     * Les racines GÉNÉRÉES d'une racine source (convention AGP) : les
     * sous-arbres `build/generated` de la racine — recherche BORNÉE en
     * profondeur (la racine elle-même, puis ses sous-répertoires jusqu'au
     * `build` — jamais un parcours complet du disque) : `<racine>/build`,
     * `<racine>/build/generated`, et les `build` des parents jusqu'à la
     * racine du projet (convention Gradle multi-module).
     *
     * @param racineSource la racine source (module) ;
     * @return les répertoires générés LISIBLES (KSP `ksp/`, KAPT `kapt/`,
     * AGP `generated/` — le répertoire `build/generated` entier).
     */
    private fun racinesGenereesDe(racineSource: String): List<String> {
        val racines = mutableListOf<String>()
        var courant: Path? =
            try {
                Paths.get(racineSource)
            } catch (_: Exception) {
                return emptyList()
            }
        // La racine elle-même puis ses parents (le build Gradle est au
        // module OU à la racine du projet — convention multi-module).
        var compteur = 0
        while (courant != null && compteur < 8) {
            val genere = courant.resolve("build").resolve("generated")
            if (Files.isDirectory(genere)) {
                racines.add(genere.toString())
            }
            courant = courant.parent
            compteur++
        }
        return racines
    }

    /**
     * Lit les déclarations de premier niveau d'un fichier GÉNÉRÉ
     * (tolérant : un fichier non parsable rend une liste VIDE).
     */
    private fun lireDeclarations(fichier: Path): List<DeclarationGeneree> {
        val texte =
            try {
                Files.readString(fichier)
            } catch (_: Exception) {
                return emptyList()
            }
        val arbre: KtFichier =
            try {
                parseur.arbre(texte, fichier.toString().endsWith(".kts"))
            } catch (_: Exception) {
                return emptyList()
            }
        val uri = fichier.toUri().toString()
        val declarations = mutableListOf<DeclarationGeneree>()
        for (enfant in arbre.noeud.enfants) {
            when (enfant.genre) {
                KtNodeTypes.CLASS, KtNodeTypes.OBJECT_DECLARATION -> {
                    val nom = enfant.jetonsPropres
                        .firstOrNull { it.genre == KtTokens.IDENTIFIER }?.texte
                        ?: continue
                    declarations.add(
                        DeclarationGeneree(
                            nom = nom,
                            uri = uri,
                            genre = enfant.genre.toString(),
                            membres = lireMembres(enfant),
                            supertypesEcrits = lireSupertypes(enfant),
                            ligne = compterLignes(arbre.source, enfant.debut),
                        ),
                    )
                }
                KtNodeTypes.FUN -> {
                    // Une fonction de premier niveau générée (les factories
                    // KSP en produisent).
                    val nom = enfant.jetonsPropres
                        .firstOrNull { it.genre == KtTokens.IDENTIFIER }?.texte
                        ?: continue
                    declarations.add(
                        DeclarationGeneree(
                            nom = nom,
                            uri = uri,
                            genre = "FUN",
                            membres = emptyList(),
                            supertypesEcrits = emptyList(),
                            ligne = compterLignes(arbre.source, enfant.debut),
                        ),
                    )
                }
                else -> Unit
            }
        }
        return declarations
    }

    /** Les membres déclarés d'une classe générée (fonctions, propriétés). */
    private fun lireMembres(classe: jo.codeide.lsp.kotlin.parse.NoeudArbre): List<MembreGenere> {
        val membres = mutableListOf<MembreGenere>()
        val corps = classe.enfants.firstOrNull { it.genre == KtNodeTypes.CLASS_BODY } ?: return membres
        for (enfant in corps.enfants) {
            when (enfant.genre) {
                KtNodeTypes.FUN -> {
                    val nom = enfant.jetonsPropres
                        .firstOrNull { it.genre == KtTokens.IDENTIFIER }?.texte ?: continue
                    membres.add(
                        MembreGenere(
                            nom = nom,
                            genre = "fun",
                            typeRetour = enfant.jetonsPropres
                                .firstOrNull { it.genre == KtTokens.IDENTIFIER }?.texte,
                            parametres = enfant.enfants
                                .filter { it.genre == KtNodeTypes.VALUE_PARAMETER_LIST }
                                .flatMap { liste -> liste.enfants }
                                .mapNotNull { param -> param.jetonsPropres.firstOrNull { it.genre == KtTokens.IDENTIFIER }?.texte },
                            estAbstrait = enfant.enfants.none { it.genre == KtNodeTypes.BLOCK },
                        ),
                    )
                }
                KtNodeTypes.PROPERTY -> {
                    val nom = enfant.jetonsPropres
                        .firstOrNull { it.genre == KtTokens.IDENTIFIER }?.texte ?: continue
                    membres.add(
                        MembreGenere(
                            nom = nom,
                            genre = if (enfant.jetonsPropres.any { it.genre == KtTokens.VAL_KEYWORD }) "val" else "var",
                            typeRetour = null,
                            parametres = emptyList(),
                            estAbstrait = enfant.jetonsPropres.none { it.genre == KtTokens.EQ },
                        ),
                    )
                }
                else -> Unit
            }
        }
        return membres
    }

    /** La clause de supertypes ÉCRITE d'une classe générée (premiers segments). */
    private fun lireSupertypes(classe: jo.codeide.lsp.kotlin.parse.NoeudArbre): List<String> {
        val clause = classe.enfants
            .firstOrNull { it.genre == KtNodeTypes.SUPER_TYPE_LIST } ?: return emptyList()
        return clause.enfants.mapNotNull { entrant ->
            entrant.jetonsPropres
                .filter { it.genre == KtTokens.IDENTIFIER }
                .joinToString(".") { it.texte }
                .ifEmpty { null }
        }
    }

    /** Le nombre de lignes (0-based) avant l'offset. */
    private fun compterLignes(texte: CharSequence, offset: Int): Int {
        var lignes = 0
        for (indice in 0 until offset.coerceAtMost(texte.length)) {
            if (texte[indice] == '\n') lignes++
        }
        return lignes
    }
}
