package jo.codeide.lsp.host

import jo.codeide.lsp.model.protocole.ClasspathEntry
import jo.codeide.lsp.model.protocole.ClasspathKind
import jo.codeide.lsp.model.protocole.ClasspathModule
import jo.codeide.lsp.model.protocole.ClasspathResult
import jo.codeide.tooling.protocol.ClasspathEntry as ClasspathEntryTooling
import jo.codeide.tooling.protocol.ClasspathKind as ClasspathKindTooling
import jo.codeide.tooling.protocol.ClasspathModule as ClasspathModuleTooling
import jo.codeide.tooling.protocol.ClasspathResult as ClasspathResultTooling

/**
 * Adaptation du protocole tooling réel vers le contrat intégré du socle
 * :model (MIGRATION du 13/11/2026 depuis codeide-lsp).
 *
 * ## Pourquoi cet adaptateur existe
 *
 * La SURFACE PUBLIQUE de l'hôte expose le type du protocole tooling
 * ([ClasspathResultTooling] — artefact com.github.jjoblab.codeide-tooling:
 * protocol) : c'est celui que CodeIDE construit côté client et passe aux
 * sessions — le contrat CodeIDE est INCHANGÉ par la migration. Mais le socle
 * :model de code-lsp consomme son PROPRE contrat intégré
 * (jo.codeide.lsp.model.protocole — réécriture locale de code-lsp motivée
 * par l'action propriétaire P1 : le dépôt du protocole ne comporte pas de
 * fichier LICENSE, cf. socle/model/.../protocole/ProtocoleClasspath.kt).
 *
 * L'adaptation est STRUCTURELLE, champ pour champ — les deux contrats sont
 * alignés sur la notation filaire du protocole v8. Seul écart : le champ
 * `android` du module ([ClasspathModule.android]) n'existe pas dans le
 * protocole v8 — il reste `null` à l'arrivée, exactement comme le faisait
 * l'ancien dépôt (le modèle y prévoit la donnée, le tooling ne l'envoie
 * pas encore).
 *
 * Quand l'action P1 sera résolue (LICENSE posée dans codeide-tooling), le
 * socle pourra retourner à l'artefact officiel et cet adaptateur
 * disparaîtra — le commentaire du contrat intégré le prévoit explicitement.
 */
internal fun ClasspathResultTooling.versContratModele(): ClasspathResult =
    ClasspathResult(
        id = id,
        protocolVersion = protocolVersion,
        projectDir = projectDir,
        modules = modules.map(ClasspathModuleTooling::versContratModele),
    )

/** Adaptation d'un module (champ pour champ, écart `android` à `null`). */
private fun ClasspathModuleTooling.versContratModele(): ClasspathModule =
    ClasspathModule(
        name = name,
        sourceDirs = sourceDirs,
        sourceDirsTest = sourceDirsTest,
        entries = entries.map(ClasspathEntryTooling::versContratModele),
        nbJars = nbJars,
        nbAars = nbAars,
        nbSources = nbSources,
        varianteAndroid = varianteAndroid,
        nbDependancesProjet = nbDependancesProjet,
        fichiersGeneres = fichiersGeneres,
        androidJar = androidJar,
        ignore = ignore,
        raisonIgnore = raisonIgnore,
        avertissements = avertissements,
        sourceCompatibility = sourceCompatibility,
        targetCompatibility = targetCompatibility,
        android = null,
    )

/** Adaptation d'une entrée (chemin, sorte, portée, sources, aar d'origine). */
private fun ClasspathEntryTooling.versContratModele(): ClasspathEntry =
    ClasspathEntry(
        path = path,
        kind = kind.versContratModele(),
        scope = scope,
        sources = sources,
        aar = aar,
    )

/** Adaptation de la sorte — `when` EXHAUSTIF sans `else` : les deux
 *  énumérations sont fermées sur les QUATRE valeurs du protocole v8. */
private fun ClasspathKindTooling.versContratModele(): ClasspathKind =
    when (this) {
        ClasspathKindTooling.JAR -> ClasspathKind.JAR
        ClasspathKindTooling.AAR -> ClasspathKind.AAR
        ClasspathKindTooling.DOSSIER -> ClasspathKind.DOSSIER
        ClasspathKindTooling.MODULE -> ClasspathKind.MODULE
    }
