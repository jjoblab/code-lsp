// Module :index — index disque CIDX (reprise contrôlée prévue en vague 1,
// après inscription au manifeste docs/PROVENANCE-REPRISE.md — R9.2).
// Étude D7 : CIDX est le socle conservé (préfixe binaire, budget octets,
// corruption par segment — supérieur au design IntelliJ sur les axes mobiles).
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
