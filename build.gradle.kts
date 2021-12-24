import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application

    kotlin("jvm")
    kotlin("plugin.serialization")

    id("com.github.jakemarsden.git-hooks")
    id("com.github.johnrengelman.shadow")
    id("io.gitlab.arturbosch.detekt")
}

group = "cobaltumsmp.discordbot"
version = "1.0-SNAPSHOT"

repositories {
    // You can remove this if you're not testing locally-installed KordEx builds
    mavenLocal()

    maven {
        name = "Kotlin Discord"
        url = uri("https://maven.kotlindiscord.com/repository/maven-public/")
    }
    maven {
        name = "Quilt Releases"
        url = uri("https://maven.quiltmc.org/repository/release/")
    }
}

dependencies {
    detektPlugins(libs.detekt)

    implementation(libs.kord.extensions)
    implementation(libs.kotlin.stdlib)

    // Logging dependencies
    implementation(libs.groovy)
    implementation(libs.logback)
    implementation(libs.logging)

    implementation(libs.ktor.http)
    implementation(libs.ktor.core)

    implementation(libs.launchermeta.parser)

    // Database management dependencies
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)

    implementation(libs.postgresql)
}

application {
    // This is deprecated, but the Shadow plugin requires it
    mainClassName = "cobaltumsmp.discordbot.AppKt"
}

gitHooks {
    setHooks(
        mapOf("pre-commit" to "detekt")
    )
}

tasks.withType<KotlinCompile> {
    // Current LTS version of Java
    kotlinOptions.jvmTarget = "11"

    kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "cobaltumsmp.discordbot.AppKt"
        )
    }
}

java {
    // Current LTS version of Java
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

detekt {
    buildUponDefaultConfig = true
    config = rootProject.files("detekt.yml")
}
