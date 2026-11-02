# Vendoring — la grammaire Kotlin multiplateforme du compilateur (spike-kotlin)

> **Obtention R9.2** : ces fichiers sont obtenus **depuis l'amont JetBrains**
> (jamais depuis l'ancien dépôt `codeide-lsp`), à une version épinglée :
>
>     amont :   https://github.com/JetBrains/kotlin
>     chemin :  compiler/multiplatform-parsing/common/src
>     tag :     v2.3.0 (commit f95cb2f7693a15966fd8fd9b1d78d440545e7b66)
>     licence : Apache-2.0 (LICENSE.txt + COPYRIGHT.txt dans ce répertoire,
>               copiés depuis `license/` du dépôt amont au même tag)
>     vérification d'exception : `license/README.md` du tag v2.3.0 ne liste
>     AUCUN fichier sous `compiler/multiplatform-parsing` — Apache-2.0 pur
>     (R6.1 : vérifié le 2026-10-30)
>
> Copie **octet pour octet** (git archive au tag) : 23 fichiers Kotlin,
> 9 585 lignes. Un re-sync est un **remplacement**, jamais une fusion.
> La version v2.3.0 est délibérée : première version publiée contenant le
> module, compilée par le compilateur bootstrap 2.2.x — la même chaîne
> d'outils que ce dépôt (Kotlin Gradle plugin 2.2.10) compile ces sources
> telles quelles.
>
> **Rôle dans le spike** : ce vendoring ne sert QUE la variante S2/S3 du
> module de mesure (Phase 2 du prompt de mission) — le parseur officiel
> seul, avec un modèle de symboles neuf minimal. Ce n'est **pas** la
> grammaire d'un moteur de production (toute reprise serait conditionnée
> à l'ADR 0100 et inscrite au manifeste
> `docs/PROVENANCE-REPRISE-KOTLIN.md`).

## Dépendances amont (artefacts binaires, pas vendorées)

- `org.jetbrains:syntax-api:0.3.340` (variante `-jvm`) — l'arbre de
  construction syntaxique contre lequel la grammaire est écrite ;
  dépôt `intellij-dependencies` (accès cache-redirector), Apache-2.0
  (sources) — conditions des **binaires** à consigner (alerte P-K2 de
  l'étude).
- `org.jetbrains:annotations:26.0.2` (Maven Central, Apache-2.0).

Les jars sont téléchargés par `scripts/telecharger-jars.sh` dans un
répertoire **hors dépôt** et référencés en dépendances de fichiers —
jamais committés (R6.4).

## Exemptions de qualité applicables au vendoring

Miroir de l'ADR 0115 de l'ancien dépôt : `explicitApi()` et
`allWarningsAsErrors` ne s'appliquent pas aux sources vendorées ; la
couverture n'est pas exigée sur `org.jetbrains.kotlin.kmp.*` (la grammaire
est attestée par la suite de parité de l'ancien dépôt et par les bancs S2
du présent spike — mesures de latence et de tolérance).
