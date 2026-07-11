import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.shadow)
    jacoco
}

group = "io.github.dravengarden"
version = providers.gradleProperty("version").orElse("0.1.0-SNAPSHOT").get()

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // Treat warnings as errors and require explicit API on public surface.
        allWarningsAsErrors = true
        // Compile on JDK 21 while remaining loadable in Java 17+ JDBC clients.
        jvmTarget = JvmTarget.JVM_17
    }
    explicitApi()
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "0.40".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

tasks.shadowJar {
    // A single self-contained JAR to drop into DataGrip's driver settings.
    archiveClassifier.set("")
    mergeServiceFiles() // keep META-INF/services/java.sql.Driver
    manifest {
        attributes(
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version,
            "Automatic-Module-Name" to "io.github.dravengarden.d1.jdbc",
        )
    }
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

dependencyLocking {
    lockAllConfigurations()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
