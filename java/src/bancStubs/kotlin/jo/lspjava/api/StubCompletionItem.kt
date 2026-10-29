// STUB de l'item de complétion de la façade publique vendored — étage T
// (banc de parité croisé ADR 0016). Voir l'en-tête de
// `jo/lspjava/ecj/StubsMoteur.kt` pour le rôle exact (compilation seule,
// alarme au chargement, descripteurs JVM identiques au vendored).
package jo.lspjava.api

/**
 * Stub de l'item de complétion vendored — accès en lecture seule au
 * libellé, à la nature et à la pertinence (la surface que sérialise le
 * pilote du banc de parité).
 */
public class CompletionItem {
    init {
        alarmeStub()
    }

    /** Stub de la nature d'item (enum imbriquée du même nom binaire). */
    public enum class Kind

    /** Libellé affichable (ce que compare le banc, à l'ordre près). */
    public fun label(): String {
        alarmeStub()
    }

    /** Nature de l'item (sérialisée par son nom d'enum au runtime réel). */
    public fun kind(): Kind {
        alarmeStub()
    }

    /** Pertinence interne du vendored (informatif, non comparé). */
    public fun relevance(): Int {
        alarmeStub()
    }
}

/** Alarme chargée : un stub a atteint l'exécution — le jar oracle manque. */
private fun alarmeStub(): Nothing = throw IllegalStateException(
    "STUB CHARGÉ — la classe jo.lspjava du banc de parité est un stub de compilation : " +
        "le jar réel du vendored (.oracle/java-1.16.0.jar, scripts/preparer-oracle.sh) " +
        "est absent du classpath d'exécution ou n'a pas fourni cette classe (dérive de façade ?).",
)
