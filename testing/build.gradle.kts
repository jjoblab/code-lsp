// Module :testing — suite d'intégration moteur ↔ index (scénarios de parité,
// ADR 0016). Reprise contrôlée prévue en vague 1 avec le couple moteur↔index.
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
