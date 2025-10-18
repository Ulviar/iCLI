import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
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
    jvmToolchain(25)

    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_24)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(kotlin("test"))
}

tasks.test {
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

tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    reports.maybeCreate("html").required.set(true)
    reports.maybeCreate("xml").required.set(false)
}
