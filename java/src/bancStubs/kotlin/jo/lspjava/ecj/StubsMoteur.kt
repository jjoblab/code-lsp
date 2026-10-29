// STUBS de la façade publique du moteur vendored — étage T (banc de parité
// croisé ADR 0016).
//
// RÔLE EXACT : ces classes servent UNIQUEMENT à la COMPILATION du pilote
// oracle (`java/src/piloteOracle/`). Elles reproduisent les SIGNATURES
// publiques documentées de `jo.lspjava.ecj`/`jo.lspjava.api` (façade
// consommable par tout projet Java — ADR 0002 du dépôt jjoblab/code-lsp,
// même surface que celle qu'utilisent les tests du vendored, source du
// cahier). AUCUNE ligne d'implémentation n'est reprise (clean-room,
// AGENTS.md — le vendored s'utilise en oracle boîte noire : on l'EXÉCUTE,
// on ne le copie pas ; registre docs/PROVENANCE-REPRISE.md §6).
//
// SÉCURITÉ DE CLASSEMENT : le source set `bancStubs` n'est JAMAIS sur un
// classpath d'EXÉCUTION. Au runtime, le pilote reçoit le JAR RÉEL du
// vendored (`.oracle/java-1.16.0.jar`) en tête de classpath : les classes
// réelles chargent, les stubs restent inertes. Si un jar oracle absent ou
// obsolète laissait un stub charger, tout appel déclenche l'alarme
// explicite ci-dessous — l'échec est bruyant, jamais silencieux.
//
// COMPATIBILITÉ BINAIRE : les signatures ci-dessous doivent produire des
// DESCRIPTEURS JVM strictement identiques à ceux du vendored (types
// effacés, ordre des paramètres, nullabilité sans effet). Toute retouche
// de la façade vendored = retouche des stubs + re-pin du commit oracle.
package jo.lspjava.ecj

/**
 * Stub du moteur compilateur vendored — constructeur documenté
 * `(List<File> racines, List<File> classpath, String compliance)`.
 *
 * @param racines les racines sources du module (répertoires existants)
 * @param classpath les jars du classpath (vide = jrt du runtime seul)
 * @param compliance le niveau de conformité source (ex. « 17 »)
 */
public class EcjCompilerEngine(
    racines: List<java.io.File>?,
    classpath: List<java.io.File>?,
    compliance: String?,
) {
    init {
        alarmeStub()
    }
}

/**
 * Stub du moteur de complétion vendored — constructeur documenté
 * `(EcjCompilerEngine moteur)` et méthode `complete(String, String, Int)`.
 */
public class EcjCompletionEngine(moteur: EcjCompilerEngine?) {
    init {
        alarmeStub()
    }

    /**
     * Stub de `complete(chemin, source, offset)` — retourne `null` quand le
     * contexte n'est pas résoluble (contrat du vendored), une liste sinon.
     */
    public fun complete(chemin: String?, source: String?, offset: Int): List<jo.lspjava.api.CompletionItem>? {
        alarmeStub()
    }
}

/** Alarme chargée : un stub a atteint l'exécution — le jar oracle manque. */
private fun alarmeStub(): Nothing = throw IllegalStateException(
    "STUB CHARGÉ — la classe jo.lspjava du banc de parité est un stub de compilation : " +
        "le jar réel du vendored (.oracle/java-1.16.0.jar, scripts/preparer-oracle.sh) " +
        "est absent du classpath d'exécution ou n'a pas fourni cette classe (dérive de façade ?).",
)
