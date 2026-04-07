plugins {
    `java-library`
    alias(libs.plugins.kotlinJvm)
}

group = "app.venues"
version = "0.0.1-SNAPSHOT"
description = "platform-api"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(providers.gradleProperty("app.jvm.version").get().toInt())
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Pure port interfaces - no implementation dependencies
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

