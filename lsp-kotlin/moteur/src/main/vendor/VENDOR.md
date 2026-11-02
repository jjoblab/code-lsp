# Vendoring — la grammaire Kotlin multiplateforme du compilateur (module :kotlin)

> **Obtention R9.2** : ces fichiers sont obtenus **depuis l'amont JetBrains**
> (jamais depuis l'ancien dépôt `codeide-lsp`), à une version épinglée :
>
>     amont :   https://github.com/JetBrains/kotlin
>     chemin :  compiler/multiplatform-parsing/common/src
>     tag :     v2.3.0 (commit f95cb2f7693a15966fd8fd9b1d78d440545e7b66)
>     licence : Apache-2.0 (LICENSE.txt + COPYRIGHT.txt dans ce répertoire,
>               copiés depuis `license/` du dépôt amont au même tag)
>     vérification d'exception : `license/README.md` du tag v2.3.0 ne liste
>               AUCUN fichier sous `compiler/multiplatform-parsing` — Apache-2.0
>               pur (R6.1 : revérifié à la ré-obtention du 2026-10-30, vague 0)
>
> Copie **octet pour octet** (`git archive` au tag) : 23 fichiers Kotlin,
> 9 585 lignes — identité vérifiée par `diff -r` contre le vendoring du
> spike (`spike-kotlin/src/main/vendor`, même procédure même tag). Un
> re-sync est un **remplacement**, jamais une fusion : il se fait par
> `scripts/vendor-grammaire-kotlin.sh` (miroir de la procédure de l'ADR
> 0121 — remplacement intégral + suite de parité + digeste comme gate).
> La version v2.3.0 est délibérée : première version publiée contenant le
> module, compilée par le compilateur bootstrap 2.2.x — la même chaîne
> d'outils que ce dépôt (Kotlin Gradle plugin 2.2.10) compile ces sources
> telles quelles [mesuré, spike §5.1].
>
> **Rôle dans le module** : grammaire de production du moteur Kotlin LSP
> (voie C, ADR 0100 accepté ; stratégie (i), ADR 0103 accepté — vendoring
> amont épinglé, façade `Kt*` mince conservatrice, ADR 0104). Inscription au
> registre : `docs/PROVENANCE-REPRISE-KOTLIN.md` §4. La CI vérifie ce
> répertoire via `scripts/verifier-provenance.sh` (périmètre `kotlin/`).

## Dépendances amont (artefacts binaires, pas vendorés)

- `org.jetbrains:syntax-api:0.3.340` (variante `-jvm`, dépôt
  intellij-dependencies via cache-redirector) — l'arbre de construction
  syntaxique contre lequel la grammaire est écrite ; épinglée par le tag
  amont v2.3.0. Apache-2.0 (sources) — conditions des **binaires** à
  confirmer par le propriétaire (alerte P-K2 de l'étude, miroir AL1 Java :
  aucun `META-INF/LICENSE` dans les jars) avant toute publication du pack.
- `org.jetbrains:annotations:26.0.2` (Maven Central, Apache-2.0).

## Exemptions de qualité applicables au vendoring

Miroir de l'ADR 0115 de l'ancien dépôt (consigné dans l'ADR 0104 du
présent dépôt) : `explicitApi()` ne s'applique pas aux sources vendorées
(elles vivent dans une compilation séparée du module — voir
`kotlin/build.gradle.kts`) ; la couverture n'est pas exigée sur
`org.jetbrains.kotlin.kmp.*` : la grammaire est attestée par la suite de
parité du présent module (fiches 010–012 du cahier : corpus du dépôt sans
marqueur d'erreur, digeste structurel, tolérance aux fautes).
