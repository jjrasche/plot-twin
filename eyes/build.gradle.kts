plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

repositories {
    mavenLocal()
    maven("https://jjrasche.github.io/factoredui/")
    mavenCentral()
    google()
}

dependencies {
    api(project(":render"))
    implementation(testFixtures(project(":solvers")))
    implementation(compose.desktop.currentOs)
    testImplementation(libs.kotlin.test)
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}
