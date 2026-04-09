plugins {
    `java-library`
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.springBoot) apply false
    alias(libs.plugins.springDependencyManagement)
}

group = "app.venues"
version = "0.0.1-SNAPSHOT"

dependencies {
    api("jakarta.validation:jakarta.validation-api")
    api("org.springframework.data:spring-data-commons")
}

dependencyManagement {
    imports {
        mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
    }
}

kotlin {
    jvmToolchain(providers.gradleProperty("app.jvm.version").get().toInt())
}
