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

dependencies {
    implementation(project(":"))

    implementation("org.jetbrains:annotations:26.0.2-1")
    implementation("org.apache.commons:commons-exec:1.5.0")
    implementation("org.zeroturnaround:zt-exec:1.12")
    implementation("com.zaxxer:nuprocess:3.0.0")
    implementation("org.jline:jline:3.30.6")

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(kotlin("test"))
    testImplementation(project(":process-fixture"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
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
