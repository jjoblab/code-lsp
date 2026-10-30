// Build racine de code-lsp (vague 0 — squelette ; vague 1b — outillage repris).
// Les versions des plugins Kotlin sont déclarées ICI uniquement ; les modules
// les appliquent sans version (plugins { kotlin("jvm") }).
//
// Kotlin 2.2.10 (vague 1b) : aligné sur la chaîne d'origine des modules repris
// de codeide-lsp — kotlinx-serialization 1.9.0 exige un lecteur de métadonnées
// Kotlin 2.2 (1.10+ exigerait Kotlin 2.3) ; 2.1.20 ne sait pas la lire.
plugins {
    kotlin("jvm") version "2.2.10" apply false
    kotlin("plugin.serialization") version "2.2.10" apply false
}

// Version du dépôt (ADR 0002 : réécriture intégrale — nouvelles bases).
// Étage N8 : la ressource `version.properties` embarquée dans le serveur
// (fiche 059, I7) est GÉNÉRÉE depuis cette valeur — serverInfo.version du
// protocole et version du dépôt restent synchronisées par construction.
// 1.0.0 : BASCULE §8.5 SIGNÉE le 30/10/2026 (gabarit
// bancs/GABARIT-BASCULE-APPAREIL.md — checklist 6/6, passe appareil moto g06
// 18/18 / 0 saut, deltas arbitrés 103/62/0).
// 1.1.0 : INTÉGRATION CODEIDE (docs/INTEGRATION-CODEIDE.md §3.3, option B
// arbitrée) — constructeur VIVANT ServeurLangageJava(environnement) :
// routeur multi-modules AU PROTOCOLE (résolution par requête, replaceAll
// à chaud, jamais de re-initialize, invariant ADR 0211) ; contrat de
// sérialisation superDefinition ALIGNÉ sur le vendord {path, start, end,
// label} (miroir cel-lsp — aucune adaptation client requise) ; fiche 066.
// 1.1.2 : incident ART du 30/10 au soir (CodeIDE 0.204.1, moto g06) —
// AccesJrt.ouvrirDepuisJarExterne capture Throwable, PAS seulement
// Exception : sur ART, le fournisseur jrt du lib/jrt-fs.jar du JDK
// externe lève ServiceConfigurationError (un Error) à l'instanciation
// (internes JDK absents d'ART — jdk.internal.jimage) ; l'ancienne garde
// laissait l'Error fuir, la construction paresseuse d'EnvironnementNoms
// explosait à la PREMIÈRE requête et le serveur répondait « Internal
// error. » à TOUT (complétion, surlignage, inlay, diagnostics muets).
// Désormais repli silencieux vers le régime instantané documenté (null),
// la plateforme restant couverte par les entrées réelles du classpath
// (android.jar du modèle). §10 d'INTEGRATION-CODEIDE.
allprojects {
    version = "1.1.2"
}
