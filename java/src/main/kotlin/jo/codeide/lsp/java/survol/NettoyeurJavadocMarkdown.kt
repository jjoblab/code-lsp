package jo.codeide.lsp.java.survol

/**
 * Nettoyeur javadoc → markdown (étage N3 — cahier fiche 021) : convertit le
 * texte BRUT d'un commentaire javadoc (de l'ouverture « slash-étoile-étoile »
 * à la fermeture « étoile-slash ») en documentation markdown affichable par
 * le survol riche.
 *
 * Sémantique de conversion verrouillée par la fiche :
 *  - les marqueurs d'ouverture et de fermeture du commentaire disparaissent,
 *    ainsi que les astérisques de marge (` * ` en tête de ligne) ;
 *  - les balises INLINE deviennent du markdown : `{@code X}` et
 *    `{@link X}` → code span `` `X` `` (le `#` d'un lien devient un `.`),
 *    `{@linkplain X}` → `` `X` ``, `{@literal X}` → `X` verbatim ;
 *  - `<p>` (et `<p/>`) devient un saut de PARAGRAPHE markdown (ligne
 *    vide), `<br>` et `<br/>` un saut de ligne simple ;
 *  - les balises de BLOC (`@param`, `@return`…) sont conservées VERBATIM —
 *    le nettoyeur n'interprète pas la sémantique des blocs ;
 *  - `null` en entrée (pas de javadoc) ou un commentaire vide donnent `null`
 *    en sortie — la documentation NULLE est distincte d'une documentation
 *    vide (contrat explicite de la fiche 021).
 *
 * L'ordre des passes est structurel : retrait des marges LIGNE PAR LIGNE
 * d'abord (l'astérisque de marge est un fait de mise en page), puis
 * conversion des balises inline (qui n'introduisent aucun saut de ligne),
 * puis conversion des sauts `<p>`/`<br>` (qui en introduisent), puis
 * épuration des lignes vides de tête et de queue (les sauts de paragraphe
 * INTERNES sont conservés).
 */
public object NettoyeurJavadocMarkdown {

    /**
     * Nettoie un commentaire javadoc brut.
     *
     * @param javadoc le texte du commentaire, ou null si la déclaration
     *   n'en porte pas.
     * @return la documentation markdown, ou null quand il n'y a rien à
     *   dire (entrée nulle, blancs, ou commentaire vide) — jamais une
     *   chaîne vide.
     */
    public fun nettoyer(javadoc: String?): String? {
        if (javadoc == null) return null
        val texte = javadoc.trim()
        if (texte.isEmpty()) return null
        if (!texte.startsWith("/*" + "*") || !texte.endsWith("*" + "/")) return null
        // Le cœur : sans les marqueurs d'ouverture (3 caractères) ni de
        // fermeture (2 caractères). Un commentaire vide donne un cœur vide.
        val coeur = texte.substring(3, texte.length - 2)
        val lignes = coeur.split('\n').map { retirerMarge(it) }
        val converties = lignes
            .map { convertirBalisesInline(it) }
            .map { convertirSauts(it) }
        // Épuration : lignes vides de tête et de queue supprimées, sauts de
        // paragraphe internes conservés ; blancs de fin de ligne retirés.
        val utiles = converties
            .map { it.trimEnd() }
            .dropWhile { it.isEmpty() }
            .dropLastWhile { it.isEmpty() }
        if (utiles.isEmpty()) return null
        val resultat = utiles.joinToString("\n").trim()
        return resultat.ifEmpty { null }
    }

    /**
     * Retire l'astérisque de marge d'une ligne de javadoc : blancs de tête,
     * puis `*`, puis AU PLUS un espace (la convention ` * texte` garde le
     * texte tel quel, ` *texte` aussi).
     */
    private fun retirerMarge(ligne: String): String {
        var i = 0
        while (i < ligne.length && (ligne[i] == ' ' || ligne[i] == '\t')) i++
        if (i < ligne.length && ligne[i] == '*') {
            i++
            if (i < ligne.length && ligne[i] == ' ') i++
        }
        return if (i == 0) ligne else ligne.substring(i)
    }

    /**
     * Convertit les balises inline en markdown : `{@code X}` → `` `X` ``,
     * `{@link Foo#bar()}` → `` `Foo.bar()` `` (le dièse devient un point),
     * `{@linkplain X}` → `` `X` ``, `{@literal X}` → `X` verbatim.
     */
    private fun convertirBalisesInline(ligne: String): String {
        var texte = ligne
        // {@literal …} d'abord : il rend son contenu VERBATIM (pas de code
        // span) et ne doit pas être rattrapé par les règles suivantes.
        texte = remplacerBalise(texte, "literal") { contenu -> contenu }
        texte = remplacerBalise(texte, "linkplain") { contenu -> "`${contenu.trim()}`" }
        texte = remplacerBalise(texte, "code") { contenu -> "`${contenu.trim()}`" }
        texte = remplacerBalise(texte, "link") { contenu -> "`${contenu.trim().replace('#', '.')}`" }
        return texte
    }

    /**
     * Remplace toutes les occurrences de la balise `{@<nom> …}` par le
     * résultat de la transformation appliquée à son contenu.
     */
    private fun remplacerBalise(texte: String, nom: String, transformation: (String) -> String): String {
        val ouverture = "{@$nom"
        val sortie = StringBuilder()
        var i = 0
        while (i < texte.length) {
            val debut = texte.indexOf(ouverture, i)
            if (debut < 0) {
                sortie.append(texte.substring(i))
                break
            }
            val fin = texte.indexOf('}', debut)
            if (fin < 0) {
                sortie.append(texte.substring(i))
                break
            }
            sortie.append(texte.substring(i, debut))
            val contenu = texte.substring(debut + ouverture.length, fin)
            // Le contenu commence par au moins un blanc («{@code X}») — la
            // transformation retrime elle-même ; un contenu sans blanc
            // («{@code}») est une balise dégénérée, rendue telle quelle.
            if (contenu.isBlank()) {
                sortie.append(texte.substring(debut, fin + 1))
            } else {
                sortie.append(transformation(contenu))
            }
            i = fin + 1
        }
        return sortie.toString()
    }

    /**
     * Convertit les balises de saut : `<p>`/`<p/>` → ligne vide (saut de
     * paragraphe markdown), `<br>`/`<br/>` → saut de ligne simple.
     */
    private fun convertirSauts(ligne: String): String = ligne
        .replace("<p>", "\n\n")
        .replace("<p/>", "\n\n")
        .replace("<br>", "\n")
        .replace("<br/>", "\n")
}
