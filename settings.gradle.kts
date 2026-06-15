pluginManagement {
    repositories {
        maven("https://cache-redirector.jetbrains.com/repo1.maven.org/maven2")
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "lsp4ij"
