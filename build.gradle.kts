plugins {
    kotlin("jvm") version "2.1.20"
}

group = "com.ax-h"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("jakarta.jms:jakarta.jms-api:2.0.3")
    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.0")

    testImplementation(kotlin("test"))
    testImplementation("io.strikt:strikt-core:0.35.1")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}