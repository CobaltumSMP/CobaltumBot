pluginManagement {
    plugins {
        // Update this in libs.version.toml when you change it here
        kotlin("jvm") version "1.5.30"
        kotlin("plugin.serialization") version "1.5.30"

        // Update this in libs.version.toml when you change it here
        id("io.gitlab.arturbosch.detekt") version "1.17.1"

        id("com.github.jakemarsden.git-hooks") version "0.0.1"
        id("com.github.johnrengelman.shadow") version "5.2.0"
    }
}

rootProject.name = "CobaltumBot"

enableFeaturePreview("VERSION_CATALOGS")

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("libs.versions.toml"))
        }
    }
}
