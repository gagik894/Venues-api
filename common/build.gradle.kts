plugins {
    `java-library`
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinPluginSerialization)
    alias(libs.plugins.springBoot) apply false
    alias(libs.plugins.springDependencyManagement)
}

group = "app.venues"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
    }
}


dependencies {
    // Kotlin Standard Library
    api("org.jetbrains.kotlin:kotlin-stdlib")
    api("org.jetbrains.kotlin:kotlin-reflect")

    // Kotlin Serialization
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Kotlin DateTime
    api("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")

    // Logging (SLF4J API only - no implementation)
    api("org.slf4j:slf4j-api:2.0.16")

    // Validation annotations (JSR 380)
    api("jakarta.validation:jakarta.validation-api:3.1.0")

    api("jakarta.persistence:jakarta.persistence-api:3.1.0")
    api("org.springframework.boot:spring-boot-starter-data-jpa")

    api("org.springframework.data:spring-data-commons")

    implementation("com.github.f4b6a3:uuid-creator:5.3.3")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.13.13")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}