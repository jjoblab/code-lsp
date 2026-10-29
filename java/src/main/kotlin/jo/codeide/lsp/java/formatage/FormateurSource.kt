package jo.codeide.lsp.java.formatage

import jo.codeide.lsp.java.outils.EditionTexte
import jo.codeide.lsp.java.outils.Jeton
import jo.codeide.lsp.java.outils.LexeurJava
import jo.codeide.lsp.java.outils.NatureJeton
import jo.codeide.lsp.java.outils.appliquerEditions

/**
 * Formateur Java clean-room (étage N7 — cahier fiches 016 et 017).
 *
 * Le formateur Eclipse historique (`DefaultCodeFormatter` de JDT) n'existe
 * pas dans le jar ecj patché (l'artefact `org.eclipse.jdt:ecj` n'embarque
 * PAS `org.eclipse.jdt.core.formatter.*`) : cette réécriture Kotlin est
 * donc une fonction PURE du texte — lexage maison, ré-émission par règles
 * d'espacement et d'indentation, repli de ligne aux virgules — exécutable
 * tel quel sur Android/ART (fiche 017, note de réécriture : « la
 * réécriture Kotlin devra offrir un équivalent exécutable sur Android/ART »).
 *
 * Le cahier ne fixe PAS le texte intégral de sortie (assertions par
 * sous-chaînes) ; les invariants verrouillés ici sont :
 *  - espacement : opérateurs binaires et `=` cernés d'espaces, signature
 *    `type nom(params)` espacée, `{` précédée d'un espace ;
 *  - indentation : profondeur d'accolades de BLOC × unité du style
 *    (4 Eclipse, 2 Google) — les initialiseurs de tableau ne comptent pas ;
 *  - repli : une ligne dépassant la largeur se replie aux virgules de la
 *    profondeur la moins imbriquée, continuation indentée ;
 *  - commentaires : un commentaire/javadoc MONOLIGNE est ré-espacé
 *    (comportement « F_INCLUDE_COMMENTS » — les espaces internes sont
 *    resserrés) ; les multi-lignes restent verbatim (contenu sacré) ;
 *  - sûreté : source imparsable (littéral inachevé, accolades/parenthèses
 *    déséquilibrées) → null, JAMAIS un texte mutilé ; source vide → vide ;
 *  - idempotence : formater deux fois égale formater une fois (les règles
 *    sont des fonctions déterministes de l'adjacence des jetons).
 *
 * Éditions de plage (fiche 016) : l'unité d'émission est le GROUPE — une
 * instruction, un fragment de ligne, une ligne de continuation préservée
 * ou une ligne vide. Chaque groupe connaît sa plage dans la source
 * ORIGINALE ; les éditions naissent des groupes modifiés ENTIÈREMENT
 * contenus dans la plage demandée, en ordre croissant, disjoints — le
 * protocole sûr du cahier (le client applique en ordre DÉCROISSANT).
 *
 * @param niveau le niveau Java déclaré (contractuel — « 17 » dans le
 *   cahier ; le lexage reconnaît toujours les blocs-texte et les records).
 * @param style le style d'espacement et de repli.
 */
public class FormateurSource(
    private val niveau: String,
    private val style: StyleFormattage,
) {

    /** Genre d'une accolade ouvrante (seuls les BLOCs portent l'indentation). */
    private enum class GenreAccolade { BLOC, TABLEAU }

    /** Écart à émettre entre deux jetons d'une même ligne. */
    private enum class Ecart { AUCUN, ESPACE }

    /** Nature de coupure de ligne décidée avant un jeton. */
    private enum class Coupe { APRES, FERMANTE, CONTINUATION }

    /**
     * Formate la source entière.
     *
     * @param source le texte Java.
     * @return le texte formaté, ou null si la source est imparsable
     *   (littéral inachevé ou accolades/parenthèses/crochets déséquilibrés).
     */
    public fun format(source: String): String? {
        val emission = emission(source) ?: return null
        return emission.texteFormate()
    }

    /**
     * Formate la PLAGE demandée (fiche 016) : l'unité entière est parsée
     * pour le contexte, mais seules les lignes contenues dans la plage sont
     * réécrites — le résultat est, par construction, l'application des
     * éditions de [editionsRange] (fiche 016 : « formatRange ≡ éditions
     * appliquées »).
     *
     * @param source le texte Java complet.
     * @param debut le début de la plage (offset).
     * @param longueur la longueur de la plage (0 → aucune édition, source
     *   rendue telle quelle).
     * @return le texte résultat, ou null si imparsable.
     */
    public fun formatRange(source: String, debut: Int, longueur: Int): String? {
        val editions = editionsRange(source, debut, longueur) ?: return null
        return appliquerEditions(source, editions)
    }

    /**
     * Éditions de formatage de la PLAGE (fiche 016) : chaque édition est
     * ENTIÈREMENT contenue dans [debut, debut + longueur], la liste voyage
     * en ordre CROISSANT d'offset et les plages sont disjointes.
     *
     * Distinction d'API (invariant du cahier) : liste VIDE (jamais null)
     * pour une plage vide — no-op explicite ; null pour une source
     * imparsable — signal d'échec.
     *
     * @param source le texte Java complet.
     * @param debut le début de la plage.
     * @param longueur la longueur de la plage.
     * @return les éditions ordonnées, une liste vide pour une plage vide,
     *   ou null si imparsable.
     */
    public fun editionsRange(source: String, debut: Int, longueur: Int): List<EditionTexte>? {
        val emission = emission(source) ?: return null
        if (longueur <= 0) return emptyList()
        val fin = debut + longueur
        return emission.groupes
            .asSequence()
            .filter { groupe ->
                groupe.debutSpan >= debut && groupe.finSpan <= fin &&
                    groupe.texteRendu() != source.substring(groupe.debutSpan, groupe.finSpan)
            }
            .map { groupe ->
                EditionTexte(groupe.debutSpan, groupe.finSpan - groupe.debutSpan, groupe.texteRendu())
            }
            .toList()
    }

    // ------------------------------------------------------------------ //
    // Émission : jetons → groupes
    // ------------------------------------------------------------------ //

    /**
     * Lexe puis émet la source. Retourne null si un littéral est inachevé
     * ou si les accolades/parenthèses/crochets sont déséquilibrés (garde
     * imparsable du cahier — `class {{{` ne produit jamais de texte).
     */
    private fun emission(source: String): Emission? {
        val jetons = LexeurJava.lexe(source) ?: return null
        var accolades = 0
        var parenthese = 0
        var crochets = 0
        for (jeton in jetons) {
            when (jeton.texte) {
                "{" -> accolades++
                "}" -> accolades--
                "(" -> parenthese++
                ")" -> parenthese--
                "[" -> crochets++
                "]" -> crochets--
            }
            if (accolades < 0 || parenthese < 0 || crochets < 0) return null
        }
        if (accolades != 0 || parenthese != 0 || crochets != 0) return null
        val emission = Emission(source, style, jetons)
        for ((indice, jeton) in jetons.withIndex()) emission.jeton(jeton, indice)
        emission.terminer()
        return emission
    }

    /**
     * Machine d'émission : transforme le flux de jetons en GROUPES — une
     * instruction, un fragment de ligne, une ligne de continuation préservée
     * ou une ligne vide. Chaque groupe porte sa plage dans la source
     * originale et son texte rendu (repli compris).
     */
    private class Emission(
        private val source: String,
        private val style: StyleFormattage,
        private val jetons: List<Jeton> = emptyList(),
    ) {

        /** Débuts de lignes (offset du premier caractère de chaque ligne). */
        private val debutsLignes: IntArray = run {
            val list = ArrayList<Int>()
            list.add(0)
            for (i in source.indices) if (source[i] == '\n') list.add(i + 1)
            list.toIntArray()
        }

        /** Groupes émis, dans l'ordre du document. */
        val groupes = ArrayList<Groupe>()

        /** Groupe en cours de construction. */
        private var courant: Groupe? = null

        /** Dernier jeton émis. */
        private var precedent: Jeton? = null

        /** Pile des genres d'accolades ouvertes. */
        private val pileAccolades = ArrayDeque<GenreAccolade>()

        /** Profondeur de parenthèses. */
        private var profondeurParen = 0

        /** Profondeur de crochets. */
        private var profondeurCrochets = 0

        /** Arguments de type actifs (`List<String>` — profondeur de chevrons). */
        private var argsType = 0

        /** Drapeau « étiquette case/default en cours » (jusqu'au deux-points). */
        private var etiquetteCase = false

        /** Le dernier jeton émis était-il un opérateur unaire préfixe ? */
        private var dernierUnaire = false

        /** Le jeton précédent a-t-il fermé une accolade de BLOC ? */
        private var fermanteBlocPrecedente = false

        /** État de l'annotation en cours (coupure après `@X`/`@X(...)`). */
        private var etatAnnotation = 0

        /** Profondeur de parenthèses à l'ouverture des arguments d'annotation. */
        private var profondeurAnnotation = 0

        /** Blocs ouverts DANS des parenthèses (corps de lambda en arguments). */
        private var blocsSousParen = 0

        /** Un groupe émis : plage source + texte rendu. */
        inner class Groupe(
            /** Vrai ssi le groupe débute une ligne fraîche (le saut amont est préservé dans la source). */
            val ligneFraiche: Boolean,
            /** Indentation (calculée, ou préservée pour une continuation). */
            val indentation: String,
        ) {
            /** Contenu rendu (sans l'indentation). */
            val contenu = StringBuilder()

            /** Début de la plage dans la source originale. */
            var debutSpan = 0

            /** Fin (exclusive) de la plage dans la source originale. */
            var finSpan = 0

            /** Premier jeton du groupe (null pour une ligne vide). */
            var premierJeton: Jeton? = null

            /** Dernier jeton du groupe (null pour une ligne vide). */
            var dernierJeton: Jeton? = null

            /** Points de repli : position dans contenu (après la virgule) × profondeur. */
            val pointsDeRepli = ArrayList<Pair<Int, Int>>()

            /** Texte rendu complet : saut éventuel + indentation + contenu (repli appliqué). */
            fun texteRendu(): String {
                val base = if (ligneFraiche) "" else "\n"
                return base + indentation + replier()
            }

            /** Applique le repli de ligne si le contenu dépasse la largeur du style. */
            private fun replier(): String {
                val texte = contenu.toString()
                if (texte.length + indentation.length <= style.largeurLigne || pointsDeRepli.isEmpty()) {
                    return texte
                }
                val profondeurMin = pointsDeRepli.minOf { it.second }
                val coupures = pointsDeRepli.asSequence()
                    .filter { it.second == profondeurMin }
                    .map { it.first }
                    .toList()
                val continuation = " ".repeat(style.indentationContinuation)
                val lignes = ArrayList<String>()
                var debut = 0
                var prefixe = ""
                var marge = indentation.length
                while (true) {
                    // Coupure la plus lointaine depuis « debut » qui tient dans la largeur.
                    var retenue = -1
                    for (coupure in coupures) {
                        if (coupure <= debut) continue
                        if (marge + (coupure - debut) <= style.largeurLigne) retenue = coupure else break
                    }
                    val finLigne = if (retenue > debut) retenue else texte.length
                    lignes.add(prefixe + texte.substring(debut, finLigne))
                    if (finLigne >= texte.length) break
                    debut = if (texte[finLigne] == ' ') finLigne + 1 else finLigne
                    prefixe = continuation
                    marge = continuation.length
                }
                return lignes.joinToString("\n")
            }
        }

        // ---------------------------------------------------------------- //
        // Point d'entrée
        // ---------------------------------------------------------------- //

        /** Traite le jeton d'indice [indice] : coupure, écart, contenu, état. */
        fun jeton(jeton: Jeton, indice: Int) {
            val precedent = this.precedent
            val blancs = jeton.blancAvant
            val aSaut = '\n' in blancs

            if (precedent == null) {
                commencerGroupe(jeton, fraiche = true, dedente = false)
            } else {
                when (coupureForcee(precedent, jeton, aSaut, indice)) {
                    Coupe.FERMANTE -> {
                        viderCourant()
                        pousserLignesVides(blancs, jeton)
                        commencerGroupe(jeton, fraiche = aSaut, dedente = true)
                    }

                    Coupe.APRES -> {
                        viderCourant()
                        pousserLignesVides(blancs, jeton)
                        commencerGroupe(jeton, fraiche = aSaut, dedente = false)
                    }

                    Coupe.CONTINUATION -> {
                        viderCourant()
                        val indentationPreservee = blancs.substringAfterLast('\n')
                        commencerGroupe(jeton, fraiche = true, dedente = false, indentation = indentationPreservee)
                    }

                    null -> {
                        if (ecartEntre(precedent, jeton, indice) == Ecart.ESPACE) courant!!.contenu.append(' ')
                    }
                }
            }

            val groupe = courant!!
            if (groupe.premierJeton == null) {
                groupe.premierJeton = jeton
                groupe.debutSpan = if (groupe.ligneFraiche) debutLigne(jeton.debut) else precedent?.fin ?: jeton.debut
            }
            groupe.contenu.append(texteNormalise(jeton))
            groupe.dernierJeton = jeton
            if (jeton.texte == ",") {
                groupe.pointsDeRepli.add(
                    groupe.contenu.length to (profondeurParen + profondeurCrochets + profondeurTableaux()),
                )
            }
            this.precedent = jeton
            mettreAJourEtat(jeton, precedent, indice)
        }

        /** Mise à jour de l'état structurel APRÈS l'émission du jeton. */
        private fun mettreAJourEtat(jeton: Jeton, precedent: Jeton?, indice: Int) {
            val texte = jeton.texte
            fermanteBlocPrecedente = false
            when {
                texte == "(" -> {
                    if (etatAnnotation == ANNON_IDENT) {
                        etatAnnotation = ANNON_PARENS
                        profondeurAnnotation = profondeurParen
                    }
                    profondeurParen++
                    dernierUnaire = false
                }

                texte == ")" -> {
                    profondeurParen--
                    if (etatAnnotation == ANNON_PARENS && profondeurParen == profondeurAnnotation) {
                        etatAnnotation = ANNON_TERMINEE
                    }
                    dernierUnaire = false
                }

                texte == "[" -> {
                    profondeurCrochets++
                    dernierUnaire = false
                }

                texte == "]" -> {
                    profondeurCrochets--
                    dernierUnaire = false
                }

                texte == "{" -> {
                    val genre = genreAccolade(precedent)
                    pileAccolades.addLast(genre)
                    if (genre == GenreAccolade.BLOC && profondeurParen > 0) blocsSousParen++
                    dernierUnaire = false
                }

                texte == "}" -> {
                    val genre = pileAccolades.removeLastOrNull() ?: GenreAccolade.BLOC
                    if (genre == GenreAccolade.BLOC) {
                        fermanteBlocPrecedente = true
                        if (blocsSousParen > 0 && profondeurParen > 0) blocsSousParen--
                    }
                    dernierUnaire = false
                }

                texte == "<" -> {
                    if (argsType == 0 && precedent != null && precedent.nature == NatureJeton.IDENTIFIANT &&
                        argumentsType(indice)
                    ) {
                        argsType = 1
                    } else if (argsType > 0) {
                        argsType++
                    }
                    dernierUnaire = false
                }

                texte == ">" -> {
                    if (argsType > 0) argsType--
                    dernierUnaire = false
                }

                jeton.nature == NatureJeton.MOT_CLE && (texte == "case" || texte == "default") -> {
                    etiquetteCase = true
                    dernierUnaire = false
                }

                texte == ":" -> {
                    etiquetteCase = false
                    dernierUnaire = false
                }

                texte == "@" -> {
                    etatAnnotation = ANNON_AROBASE
                    dernierUnaire = false
                }

                etatAnnotation == ANNON_AROBASE -> {
                    etatAnnotation = if (jeton.nature == NatureJeton.IDENTIFIANT) ANNON_IDENT else ANNON_NONE
                    dernierUnaire = false
                }

                texte == "!" || texte == "~" -> dernierUnaire = true

                texte == "-" || texte == "+" || texte == "++" || texte == "--" -> {
                    dernierUnaire = estUnaire(precedent)
                }

                else -> dernierUnaire = false
            }

            // L'état d'annotation est consommé par la décision de coupure du
            // jeton suivant (une annotation sans arguments se termine sur son
            // identifiant ; une avec arguments, sur sa fermeture).
            if (etatAnnotation == ANNON_TERMINEE) {
                etatAnnotation = ANNON_NONE
            } else if (etatAnnotation == ANNON_IDENT && texte != "(" && texte != "@") {
                etatAnnotation = ANNON_NONE
            }
        }

        /** Genre d'une accolade ouvrante selon le jeton précédent. */
        private fun genreAccolade(precedent: Jeton?): GenreAccolade {
            val texte = precedent?.texte ?: return GenreAccolade.BLOC
            if (texte == "=" || texte == "," || texte == "[" || texte == "]") return GenreAccolade.TABLEAU
            if (texte == "{" && pileAccolades.lastOrNull() == GenreAccolade.TABLEAU) return GenreAccolade.TABLEAU
            return GenreAccolade.BLOC
        }

        /** Nombre d'accolades d'initialiseur de tableau ouvertes. */
        private fun profondeurTableaux(): Int = pileAccolades.count { it == GenreAccolade.TABLEAU }

        /** Décision de coupure de ligne AVANT le jeton courant. */
        private fun coupureForcee(precedent: Jeton, jeton: Jeton, aSaut: Boolean, indice: Int): Coupe? {
            val texte = jeton.texte
            val textePrecedent = precedent.texte

            // Un commentaire qui reste sur la ligne de son énoncé n'y coupe pas.
            val commentaireCollant = !aSaut && (
                jeton.nature == NatureJeton.COMMENTAIRE_LIGNE ||
                    jeton.nature == NatureJeton.COMMENTAIRE_BLOC ||
                    jeton.nature == NatureJeton.JAVADOC
                )

            // Accolade fermante de BLOC : sa ligne dé-décalle AVANT l'énoncé.
            if (!commentaireCollant && texte == "}" && pileAccolades.lastOrNull() == GenreAccolade.BLOC &&
                textePrecedent != "{"
            ) {
                return Coupe.FERMANTE
            }

            // Corps de bloc : coupure après `{` de BLOC (sauf bloc vide).
            if (!commentaireCollant && textePrecedent == "{" && pileAccolades.lastOrNull() == GenreAccolade.BLOC &&
                texte != "}"
            ) {
                return Coupe.APRES
            }

            // Fin d'instruction : coupure après `;` hors en-tête de for,
            // sauf mots qui restent collés à l'énoncé (else/catch/…).
            if (textePrecedent == ";" && (profondeurParen == 0 || blocsSousParen > 0) &&
                texte != "}" && texte !in APRES_ACCOLADE_FERMANTE
            ) {
                return Coupe.APRES
            }

            // Un bloc fermé suivi d'un nouvel énoncé ouvre une ligne.
            if (fermanteBlocPrecedente &&
                texte != ";" && texte != ")" && texte != "," && texte != "]" && texte != "}" &&
                texte != "." && texte != "::" && texte !in APRES_ACCOLADE_FERMANTE &&
                jeton.nature != NatureJeton.OPERATEUR
            ) {
                return Coupe.APRES
            }

            // Un commentaire de ligne consomme le reste de sa ligne.
            if (precedent.nature == NatureJeton.COMMENTAIRE_LIGNE) return Coupe.APRES

            // Un commentaire de bloc maître de sa ligne reste seul sur elle.
            if ((precedent.nature == NatureJeton.COMMENTAIRE_BLOC || precedent.nature == NatureJeton.JAVADOC) && aSaut) {
                return Coupe.APRES
            }

            // Annotation suivie d'un modificateur / mot-clé de déclaration.
            if ((etatAnnotation == ANNON_TERMINEE || etatAnnotation == ANNON_IDENT) &&
                (jeton.nature == NatureJeton.MOT_CLE && texte in MOTS_TETE_DECLARATION ||
                    jeton.nature == NatureJeton.IDENTIFIANT && texte == "record")
            ) {
                return Coupe.APRES
            }

            // Ligne de continuation préservée : saut au cœur d'un groupement
            // ouvert (arguments, dimensions, initialiseur) hors bloc sous
            // parenthèses (les corps de lambda gardent leurs coupures d'énoncé).
            if (aSaut && blocsSousParen == 0 &&
                (profondeurParen > 0 || profondeurCrochets > 0 || profondeurTableaux() > 0)
            ) {
                return Coupe.CONTINUATION
            }

            return null
        }

        /** Ouvre un groupe pour [jeton]. */
        private fun commencerGroupe(
            jeton: Jeton,
            fraiche: Boolean,
            dedente: Boolean,
            indentation: String? = null,
        ) {
            val niveau = if (dedente) pileAccolades.size - 1 else pileAccolades.size
            val calcul = " ".repeat(niveau.coerceAtLeast(0) * style.indentation)
            courant = Groupe(fraiche, indentation ?: calcul)
        }

        /** Émet les lignes vides du blanc, avant le groupe du jeton courant. */
        private fun pousserLignesVides(blancs: String, jeton: Jeton) {
            val sauts = blancs.count { it == '\n' }
            if (sauts <= 1) return
            val debutGap = jeton.debut - blancs.length
            val indices = ArrayList<Int>(sauts)
            for (i in blancs.indices) if (blancs[i] == '\n') indices.add(i)
            // Les lignes vides vivent entre deux sauts consécutifs du blanc :
            // leur contenu (espaces compris) est restitué VERBATIM.
            for (j in 0 until indices.size - 1) {
                val vide = Groupe(true, "")
                vide.debutSpan = debutGap + indices[j] + 1
                vide.finSpan = debutGap + indices[j + 1]
                vide.contenu.append(blancs.substring(indices[j] + 1, indices[j + 1]))
                groupes.add(vide)
            }
        }

        /** Termine l'émission : ferme le groupe courant et calcule les bornes aval. */
        fun terminer() {
            viderCourant()
            for (i in groupes.indices) {
                val groupe = groupes[i]
                groupe.finSpan = when {
                    groupe.dernierJeton == null -> groupe.finSpan
                    i + 1 < groupes.size && !groupes[i + 1].ligneFraiche -> groupe.dernierJeton!!.fin
                    else -> finLigne(groupe.dernierJeton!!.fin)
                }
            }
        }

        /** Ferme le groupe courant dans la liste. */
        private fun viderCourant() {
            val groupe = courant ?: return
            groupes.add(groupe)
            courant = null
        }

        /** Début de la ligne contenant [offset]. */
        private fun debutLigne(offset: Int): Int {
            var bas = 0
            var haut = debutsLignes.size - 1
            while (bas < haut) {
                val milieu = (bas + haut + 1) / 2
                if (debutsLignes[milieu] <= offset) bas = milieu else haut = milieu - 1
            }
            return debutsLignes[bas]
        }

        /** Fin (hors saut de ligne) de la ligne contenant [offset]. */
        private fun finLigne(offset: Int): Int {
            val indice = source.indexOf('\n', offset)
            return if (indice < 0) source.length else indice
        }

        /** Texte rendu d'un jeton : commentaires MONOLIGNES ré-espacés. */
        private fun texteNormalise(jeton: Jeton): String {
            if (jeton.nature != NatureJeton.COMMENTAIRE_BLOC && jeton.nature != NatureJeton.JAVADOC) return jeton.texte
            if ('\n' in jeton.texte) return jeton.texte
            val ouverture = if (jeton.nature == NatureJeton.JAVADOC) "/**" else "/*"
            val interieur = jeton.texte.substring(ouverture.length, jeton.texte.length - 2)
            if (interieur.isBlank()) return jeton.texte
            val resserre = interieur.replace(Regex("[ \t]+"), " ").trim()
            return ouverture + " " + resserre + " */"
        }

        /** Regard en avant : le `<` d'indice [indice] ouvre-t-il des arguments de type ? */
        private fun argumentsType(indice: Int): Boolean {
            var profondeur = 1
            var i = indice + 1
            while (i < jetons.size) {
                val j = jetons[i]
                when (j.nature) {
                    NatureJeton.OPERATEUR -> when (j.texte) {
                        "<" -> profondeur++
                        ">" -> {
                            profondeur--
                            if (profondeur == 0) {
                                val suivant = jetons.getOrNull(i + 1) ?: return false
                                return when {
                                    suivant.nature == NatureJeton.IDENTIFIANT -> true
                                    suivant.texte == "(" || suivant.texte == "::" ||
                                        suivant.texte == "." || suivant.texte == "[" -> true
                                    else -> false
                                }
                            }
                        }

                        ".", ",", "[", "]", "::", "?", "..." -> {}

                        else -> return false
                    }

                    NatureJeton.IDENTIFIANT -> {}

                    NatureJeton.MOT_CLE -> if (j.texte != "extends" && j.texte != "super") return false

                    else -> return false
                }
                i++
            }
            return false
        }

        /** Espacement entre deux jetons d'une même ligne. */
        private fun ecartEntre(precedent: Jeton, jeton: Jeton, indice: Int): Ecart {
            val a = precedent.texte
            val b = jeton.texte
            val natureA = precedent.nature
            val natureB = jeton.nature

            // Colleurs systématiques.
            if (b == "." || a == "." || b == "::" || a == "::" || b == "...") return Ecart.AUCUN
            if (b == ";" || b == "," || b == ")" || b == "]") return Ecart.AUCUN
            if (b == "[") return Ecart.AUCUN
            if (a == "[") return Ecart.AUCUN
            if (a == "(") return Ecart.AUCUN
            if (a == ",") return Ecart.ESPACE
            if (a == ";") return Ecart.ESPACE
            if (b == "}") return Ecart.AUCUN
            if (a == "{") return Ecart.AUCUN
            if (a == "." && b == "*") return Ecart.AUCUN
            if (a == "->" || b == "->") return Ecart.ESPACE
            if (a == "@") return Ecart.AUCUN
            if (b == "@") return if (a == "(" || a == "[") Ecart.AUCUN else Ecart.ESPACE
            if (a == "!") return Ecart.AUCUN
            if (b == "!" || b == "~") return Ecart.ESPACE

            // Deux-points : resserré devant une étiquette case/default.
            if (b == ":" && etiquetteCase) return Ecart.AUCUN

            // Accolade ouvrante : espace devant (bloc ou initialiseur en tête de ligne).
            if (b == "{") return when (a) {
                "(", "[", "{" -> Ecart.AUCUN
                else -> Ecart.ESPACE
            }

            // Ouvrante de parenthèse : collée aux identifiants et fermetures.
            if (b == "(") {
                return when {
                    natureA == NatureJeton.IDENTIFIANT -> Ecart.AUCUN
                    natureA == NatureJeton.MOT_CLE && (a == "this" || a == "super") -> Ecart.AUCUN
                    natureA == NatureJeton.MOT_CLE -> Ecart.ESPACE
                    a == ")" || a == "]" -> Ecart.AUCUN
                    natureA == NatureJeton.OPERATEUR && dernierUnaire -> Ecart.AUCUN
                    natureA == NatureJeton.OPERATEUR && a == ">" -> Ecart.AUCUN
                    else -> Ecart.ESPACE
                }
            }

            // Arguments de type : chevrons resserrés.
            if (argsType > 0) {
                if (b == "<") return Ecart.AUCUN
                if (b == ">") return Ecart.AUCUN
                if (a == "<" || a == ">") return Ecart.AUCUN
            }
            if (b == "<" && natureA == NatureJeton.IDENTIFIANT && argumentsType(indice)) {
                return Ecart.AUCUN
            }

            // Mots-clés : un espace suit, sauf devant les colleurs.
            if (natureA == NatureJeton.MOT_CLE) {
                return if (b == "." || b == "::" || b == ";" || b == "," || b == ")" || b == "]" || b == "[") {
                    Ecart.AUCUN
                } else {
                    Ecart.ESPACE
                }
            }
            if (natureB == NatureJeton.MOT_CLE) {
                return if (a == "(" || a == "[" || (natureA == NatureJeton.OPERATEUR && dernierUnaire)) {
                    Ecart.AUCUN
                } else {
                    Ecart.ESPACE
                }
            }

            // Identifiants et littéraux.
            if (natureA == NatureJeton.IDENTIFIANT && natureB == NatureJeton.IDENTIFIANT) return Ecart.ESPACE

            // Opérateurs.
            if (natureA == NatureJeton.OPERATEUR || natureB == NatureJeton.OPERATEUR) {
                if (natureA == NatureJeton.OPERATEUR && natureB == NatureJeton.OPERATEUR) {
                    if (a == ">" && b == ">") return Ecart.AUCUN
                    if (a == "<" && b == "<") return Ecart.AUCUN
                }
                if (natureA == NatureJeton.OPERATEUR && dernierUnaire) return Ecart.AUCUN
                if (natureB == NatureJeton.OPERATEUR && (b == "++" || b == "--" || b == "~")) return Ecart.AUCUN
                return Ecart.ESPACE
            }

            return Ecart.ESPACE
        }

        /** Un opérateur unaire se reconnaît à son prédécesseur. */
        private fun estUnaire(precedent: Jeton?): Boolean {
            if (precedent == null) return true
            return when (precedent.nature) {
                NatureJeton.OPERATEUR -> precedent.texte != ")" && precedent.texte != "]"
                NatureJeton.MOT_CLE -> precedent.texte != "true" && precedent.texte != "false" && precedent.texte != "null"
                NatureJeton.IDENTIFIANT, NatureJeton.NOMBRE, NatureJeton.CHAINE,
                NatureJeton.BLOC_TEXTE, NatureJeton.CARACTERE,
                -> false

                else -> true
            }
        }

        /** Texte formaté complet : tête verbatim + groupes + queue verbatim. */
        fun texteFormate(): String {
            val premier = groupes.firstOrNull() ?: return source
            val sb = StringBuilder()
            sb.append(source.substring(0, premier.debutSpan))
            for ((index, groupe) in groupes.withIndex()) {
                if (index > 0 && groupe.ligneFraiche) sb.append('\n')
                sb.append(groupe.texteRendu())
            }
            val dernier = groupes.last()
            sb.append(source.substring(dernier.finSpan.coerceAtMost(source.length)))
            return sb.toString()
        }

        private companion object {
            /** Modificateurs et mots-clés de tête de déclaration (coupure après annotation). */
            private val MOTS_TETE_DECLARATION: Set<String> = setOf(
                "public", "private", "protected", "static", "final", "abstract", "synchronized",
                "native", "strictfp", "transient", "volatile", "class", "interface", "enum",
            )

            /** Mots-clés qui restent sur la ligne d'une accolade fermante. */
            private val APRES_ACCOLADE_FERMANTE: Set<String> = setOf("else", "catch", "finally", "while")

            const val ANNON_NONE = 0
            const val ANNON_AROBASE = 1
            const val ANNON_IDENT = 2
            const val ANNON_PARENS = 3
            const val ANNON_TERMINEE = 4
        }
    }
}
