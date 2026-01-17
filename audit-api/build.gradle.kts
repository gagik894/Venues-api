plugins {
    `java-library`
    kotlin("jvm")
    alias(libs.plugins.springBoot) apply false
    alias(libs.plugins.springDependencyManagement)
}

group = "app.venues"
version = "0.0.1-SNAPSHOT"

dependencies {
    // No dependencies - pure Kotlin DTOs and interfaces
}

dependencyManagement {
    imports {
        mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
    }
}
