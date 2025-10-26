import com.github.spotbugs.snom.SpotBugsTask

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
    jvmToolchain(24)
}

repositories {
    mavenCentral()
}

sourceSets {
    val integrationTest by creating {
        java.setSrcDirs(emptyList<String>())
        java.srcDir("src/integrationTest/kotlin")
        resources.srcDir("src/integrationTest/resources")
        compileClasspath += sourceSets["main"].output
        compileClasspath += configurations["testRuntimeClasspath"]
        runtimeClasspath += compileClasspath
        runtimeClasspath += output
    }
}

dependencies {
    implementation("org.jetbrains:annotations:26.0.2-1")
    implementation("com.github.spotbugs:spotbugs-annotations:4.9.7")

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(kotlin("test"))

    add("integrationTestImplementation", platform("org.junit:junit-bom:6.0.0"))
    add("integrationTestImplementation", "org.junit.jupiter:junit-jupiter")
    add("integrationTestImplementation", kotlin("test"))
}

configurations.named("integrationTestImplementation") {
    extendsFrom(configurations["testImplementation"])
}
configurations.named("integrationTestRuntimeOnly") {
    extendsFrom(configurations["testRuntimeOnly"])
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

tasks.withType<SpotBugsTask>().configureEach {
    reports.maybeCreate("html").required.set(true)
    reports.maybeCreate("xml").required.set(false)
}

tasks.named<SpotBugsTask>("spotbugsTest").configure {
    enabled = false
}

val integrationTest = tasks.register<Test>("integrationTest") {
    description = "Runs integration tests that exercise real system commands."
    group = "verification"
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    shouldRunAfter(tasks.test)
    useJUnitPlatform()
}
