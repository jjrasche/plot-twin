plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
}

repositories {
    maven("https://jjrasche.github.io/factoredui/")
    mavenCentral()
    google()
}

dependencies {
    api(project(":solvers"))
    api(libs.factoredui.kotlin.compose)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlin.test)
    testImplementation(testFixtures(project(":solvers")))
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}
