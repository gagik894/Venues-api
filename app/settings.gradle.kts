// This file allows the 'app' module to be built standalone while still being part of the multi-project build.
// It's required for IntelliJ IDEA to properly recognize the module.

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
    }
    
    // Include the parent project's version catalog
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "app"

