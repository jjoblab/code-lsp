// Module :model — modèle de projet LSP (snapshots immuables).
// Reprise contrôlée prévue en vague 1, CONDITIONNÉE à l'action propriétaire
// P1 (ajout d'une LICENSE au dépôt jjoblab/codeide-tooling — ADR 0001/0017).
plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

dependencies {
    testImplementation(kotlin("test"))
}
