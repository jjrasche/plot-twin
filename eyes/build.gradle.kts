plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

repositories {
    maven("https://jjrasche.github.io/factoredui/")
    mavenCentral()
    google()
}

dependencies {
    api(project(":render"))
    api(project(":capture"))
    implementation(testFixtures(project(":solvers")))
    testImplementation(testFixtures(project(":capture")))
    implementation(compose.desktop.currentOs)
    testImplementation(libs.kotlin.test)
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}
