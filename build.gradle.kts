plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.shadow)
}

group = "io.github.dravengarden"
version = "0.1.0-SNAPSHOT"

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // Treat warnings as errors and require explicit API on public surface.
        allWarningsAsErrors = true
    }
    explicitApi()
}

dependencies {
    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    // A single self-contained JAR to drop into DataGrip's driver settings.
    archiveClassifier.set("")
    mergeServiceFiles() // keep META-INF/services/java.sql.Driver
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
