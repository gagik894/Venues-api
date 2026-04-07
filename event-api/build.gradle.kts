plugins {
    `java-library`
    alias(libs.plugins.kotlinJvm)
}

group = "app.venues"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    api(project(":shared"))
    implementation(kotlin("stdlib"))
}

kotlin {
    jvmToolchain(providers.gradleProperty("app.jvm.version").get().toInt())
}
