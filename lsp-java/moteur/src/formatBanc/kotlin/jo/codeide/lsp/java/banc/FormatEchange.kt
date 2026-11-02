// Format d'échange du banc de parité croisé (étage T, ADR 0016) —
// sérialisation minimale MAISON du corpus et des résultats entre
// l'orchestrateur (JVM du moteur neuf) et le pilote oracle (sous-processus
// du vendored).
//
// POURQUOI un format maison : le pilote oracle doit rester SANS dépendance
// (aucune bibliothèque — le sous-processus ne porte que le jar vendored,
// kotlin-stdlib et lsp4j/gson du vendored) ; et le dépôt ne dépend pas de
// gson. La grammaire est donc volontairement RESTREINTE au deux formes
// réellement échangées, documentées ci-dessous, et couvertes par des tests
// aller-retour (`FormatEchangeTest`).
//
// Grammaire (JSON restreint, UTF-8, sorties ASCII-pures par échappement) :
//  corpus   := { "positions": [ pos* ] , "succession": [ pos* ] }
//  resultats:= { "resultats": [ res* ] }
//  pos      := { "nom": CHAINE , "source": CHAINE }
//  res      := { "nom": CHAINE , "libelles": [ CHAINE* ] }
// Les clés superflues sont REJETÉES (dérive de format = échec bruyant).
package jo.codeide.lsp.java.banc

/** Une position brute sérialisée vers le pilote (source AVEC marqueur « ¦ »). */
public data class PositionBrute(
    /** Le nom court de la position (identifiant stable du corpus versionné). */
    public val nom: String,
    /** La source Java complète, caret marqué par « ¦ » (convention du cahier). */
    public val source: String,
)

/** Le corpus brut sérialisé vers le pilote oracle. */
public data class CorpusBrut(
    /** Les positions étiquetées du corpus (rejouées par les deux moteurs). */
    public val positions: List<PositionBrute>,
    /** La succession de frappe du contrat « zéro saut » (même marqueur). */
    public val succession: List<PositionBrute>,
)

/** Le résultat d'un moteur sur une position — libellés ORDONNÉS (l'ordre est l'information de parité). */
public data class ResultatPosition(
    /** Le nom de la position rejouée (correspondance avec [PositionBrute.nom]). */
    public val nom: String,
    /** Les libellés dans l'ordre exact rendu par le moteur (null moteur = liste vide). */
    public val libelles: List<String>,
)

/**
 * Lecteur/rédacteur du format d'échange du banc de parité.
 *
 * Chaque fonction est REFUSIVE : toute entrée qui ne respecte pas la
 * grammaire documentée lève [IllegalArgumentException] avec la position
 * approximative de l'erreur — un fichier corrompu ne passe jamais inaperçu.
 */
public object FormatEchange {

    /** Sérialise le corpus (forme compacte à une entrée par ligne, diffable). */
    public fun ecrireCorpus(corpus: CorpusBrut): String {
        val sb = StringBuilder()
        sb.append("{\n  \"positions\": [\n")
        ecrirePositions(sb, corpus.positions, "    ")
        sb.append("  ],\n  \"succession\": [\n")
        ecrirePositions(sb, corpus.succession, "    ")
        sb.append("  ]\n}\n")
        return sb.toString()
    }

    /** Sérialise les résultats (forme compacte, une entrée par ligne). */
    public fun ecrireResultats(resultats: List<ResultatPosition>): String {
        val sb = StringBuilder()
        sb.append("{\n  \"resultats\": [\n")
        resultats.forEachIndexed { i, r ->
            sb.append("    { \"nom\": ")
            ecrireChaine(sb, r.nom)
            sb.append(", \"libelles\": [")
            r.libelles.forEachIndexed { j, libelle ->
                if (j > 0) sb.append(", ")
                ecrireChaine(sb, libelle)
            }
            sb.append("] }")
            if (i < resultats.size - 1) sb.append(",")
            sb.append('\n')
        }
        sb.append("  ]\n}\n")
        return sb.toString()
    }

    /** Lit un corpus sérialisé — @throws IllegalArgumentException grammaire invalide. */
    public fun lireCorpus(texte: String): CorpusBrut {
        val l = Lecteur(texte)
        l.attendre('{')
        var positions: List<PositionBrute>? = null
        var succession: List<PositionBrute>? = null
        while (l.courant() != '}') {
            when (val cle = l.lireCle()) {
                "positions" -> positions = l.lireListePositions()
                "succession" -> succession = l.lireListePositions()
                else -> l.erreur("clé inconnue « $cle » (positions/succession attendues)")
            }
            l.attendreSeparateur('}')
        }
        l.attendre('}')
        l.fin()
        return CorpusBrut(
            positions ?: l.erreur("clé « positions » absente"),
            succession ?: l.erreur("clé « succession » absente"),
        )
    }

    /** Lit des résultats sérialisés — @throws IllegalArgumentException grammaire invalide. */
    public fun lireResultats(texte: String): List<ResultatPosition> {
        val l = Lecteur(texte)
        l.attendre('{')
        var resultats: List<ResultatPosition>? = null
        while (l.courant() != '}') {
            when (val cle = l.lireCle()) {
                "resultats" -> resultats = lireListeResultats(l)
                else -> l.erreur("clé inconnue « $cle » (resultats attendu)")
            }
            l.attendreSeparateur('}')
        }
        l.attendre('}')
        l.fin()
        return resultats ?: l.erreur("clé « resultats » absente")
    }

    /** Écrit une liste de positions sous forme d'objets une-ligne. */
    private fun ecrirePositions(sb: StringBuilder, positions: List<PositionBrute>, marge: String) {
        positions.forEachIndexed { i, p ->
            sb.append(marge).append("{ \"nom\": ")
            ecrireChaine(sb, p.nom)
            sb.append(", \"source\": ")
            ecrireChaine(sb, p.source)
            sb.append(" }")
            if (i < positions.size - 1) sb.append(',')
            sb.append('\n')
        }
    }

    /** Échappe une chaîne JSON (sortie ASCII pure : tout non-ASCII en \\uXXXX). */
    private fun ecrireChaine(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> {
                    if (c.code < 0x20 || c.code > 0x7E) {
                        sb.append("\\u").append("%04x".format(c.code))
                    } else {
                        sb.append(c)
                    }
                }
            }
        }
        sb.append('"')
    }

    /** Lit une liste de positions (« [ {...}, ... ] »). */
    private fun Lecteur.lireListePositions(): List<PositionBrute> {
        attendre('[')
        val positions = mutableListOf<PositionBrute>()
        while (courant() != ']') {
            attendre('{')
            var nom: String? = null
            var source: String? = null
            while (courant() != '}') {
                when (val cle = lireCle()) {
                    "nom" -> nom = lireChaine()
                    "source" -> source = lireChaine()
                    else -> erreur("clé inconnue « $cle » dans une position (nom/source)")
                }
                attendreSeparateur('}')
            }
            attendre('}')
            positions.add(
                PositionBrute(
                    nom ?: erreur("clé « nom » absente dans une position"),
                    source ?: erreur("clé « source » absente dans une position"),
                ),
            )
            attendreSeparateur(']')
        }
        attendre(']')
        return positions
    }

    /** Lit une liste de résultats (« [ {...}, ... ] »). */
    private fun lireListeResultats(l: Lecteur): List<ResultatPosition> {
        l.attendre('[')
        val resultats = mutableListOf<ResultatPosition>()
        while (l.courant() != ']') {
            l.attendre('{')
            var nom: String? = null
            var libelles: List<String>? = null
            while (l.courant() != '}') {
                when (val cle = l.lireCle()) {
                    "nom" -> nom = l.lireChaine()
                    "libelles" -> libelles = l.lireListeChaines()
                    else -> l.erreur("clé inconnue « $cle » dans un résultat (nom/libelles)")
                }
                l.attendreSeparateur('}')
            }
            l.attendre('}')
            resultats.add(
                ResultatPosition(
                    nom ?: l.erreur("clé « nom » absente dans un résultat"),
                    libelles ?: l.erreur("clé « libelles » absente dans un résultat"),
                ),
            )
            l.attendreSeparateur(']')
        }
        l.attendre(']')
        return resultats
    }

    /** Lit une liste de chaînes (« [ "a", "b" ] »). */
    private fun Lecteur.lireListeChaines(): List<String> {
        attendre('[')
        val chaines = mutableListOf<String>()
        while (courant() != ']') {
            chaines.add(lireChaine())
            attendreSeparateur(']')
        }
        attendre(']')
        return chaines
    }

    /**
     * Analyseur récursif descendant minimal (private) — un curseur, aucun
     * état global, messages d'erreur avec position.
     */
    private class Lecteur(private val texte: String) {
        private var i = 0

        fun courant(): Char {
            sauterBlancs()
            if (i >= texte.length) erreur("fin de texte inattendue")
            return texte[i]
        }

        fun attendre(c: Char) {
            sauterBlancs()
            if (i >= texte.length || texte[i] != c) {
                erreur("« $c » attendu")
            }
            i++
        }

        fun attendreSeparateur(fermeture: Char) {
            sauterBlancs()
            if (i >= texte.length) erreur("fin de texte inattendue (séparateur)")
            when (texte[i]) {
                ',' -> i++
                fermeture -> {}
                else -> erreur("« , » ou « $fermeture » attendu")
            }
        }

        fun fin() {
            sauterBlancs()
            if (i != texte.length) erreur("trailing content après la racine")
        }

        fun lireCle(): String {
            sauterBlancs()
            if (i >= texte.length || texte[i] != '"') erreur("clé (chaîne) attendue")
            val cle = lireChaine()
            sauterBlancs()
            if (i >= texte.length || texte[i] != ':') erreur("« : » attendu après la clé")
            i++
            return cle
        }

        fun lireChaine(): String {
            sauterBlancs()
            if (i >= texte.length || texte[i] != '"') erreur("chaîne attendue")
            i++
            val sb = StringBuilder()
            while (true) {
                if (i >= texte.length) erreur("chaîne non fermée")
                when (val c = texte[i]) {
                    '"' -> {
                        i++
                        return sb.toString()
                    }
                    '\\' -> {
                        i++
                        if (i >= texte.length) erreur("échappement non fermé")
                        when (val e = texte[i]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'u' -> {
                                if (i + 4 >= texte.length) erreur("échappement \\u tronqué")
                                val hex = texte.substring(i + 1, i + 5)
                                val code = hex.toIntOrNull(16) ?: erreur("\\uXXXX invalide : « $hex »")
                                sb.append(code.toChar())
                                i += 4
                            }
                            else -> erreur("échappement inconnu « \\$e »")
                        }
                        i++
                    }
                    else -> {
                        sb.append(c)
                        i++
                    }
                }
            }
        }

        fun erreur(message: String): Nothing =
            throw IllegalArgumentException("FormatEchange — $message (position ~$i)")

        private fun sauterBlancs() {
            while (i < texte.length && (texte[i] == ' ' || texte[i] == '\n' || texte[i] == '\r' || texte[i] == '\t')) i++
        }
    }
}
