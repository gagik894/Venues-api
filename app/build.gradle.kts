plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSpring)
    alias(libs.plugins.springBoot)
    alias(libs.plugins.springDependencyManagement)
}

group = "app.venues"
version = "0.0.1-SNAPSHOT"
description = "app"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Feature modules - app needs implementations for Spring Boot component scanning
    // The app module is the "outer layer" that wires everything together
    implementation(project(":user"))
    implementation(project(":venue"))
    implementation(project(":seating"))
    implementation(project(":event"))
    implementation(project(":booking"))
    implementation(project(":platform"))
    implementation(project(":finance"))
    implementation(project(":organization"))
    implementation(project(":staff"))
    implementation(project(":ticket"))
    implementation(project(":media"))
    implementation(project(":audit"))

    // Shared module dependency (includes common, security, web config, etc.)
    implementation(project(":shared"))

    // WebClient for ISR notifier
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation(libs.caffeine)

    // Database driver (runtime only)
    runtimeOnly("org.postgresql:postgresql")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

