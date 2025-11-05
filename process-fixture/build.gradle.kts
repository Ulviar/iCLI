import com.github.spotbugs.snom.SpotBugsTask

plugins {
    application
    java
    kotlin("jvm") version "2.2.20"
    id("com.diffplug.spotless") version "8.0.0"
    id("com.github.spotbugs") version "6.4.3"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

kotlin {
    jvmToolchain(24)
}

application {
    mainClass.set("com.github.ulviar.icli.fixture.ProcessFixture")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains:annotations:26.0.2-1")
    implementation("com.github.spotbugs:spotbugs-annotations:4.9.7")

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(kotlin("test"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

spotless {
    java {
        palantirJavaFormat("2.80.0")
        target("src/**/*.java")
    }
    kotlin {
        ktlint()
        target("src/**/*.kt")
    }
}

spotbugs {
    toolVersion.set("4.9.7")
}

tasks.withType<SpotBugsTask>().configureEach {
    reports.maybeCreate("html").required.set(true)
    reports.maybeCreate("xml").required.set(false)
}

// The fixture is a CLI utility; disable SpotBugs for tests to keep the build lightweight.
tasks.named<SpotBugsTask>("spotbugsTest").configure {
    enabled = false
}
