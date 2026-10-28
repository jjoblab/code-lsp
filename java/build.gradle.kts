// Module :java — moteur Java LSP de CodeIDE (RÉÉCRITURE INTÉGRALE en Kotlin, ADR 0001).
// Vague 0 : squelette vide. Les sources naîtront en vague 1+ à partir du
// cahier de spécifications (docs/cahier-specifications/) — jamais du vendored.
// Règles : Kotlin 100 %, KDoc français détaillé (AGENTS.md).
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
