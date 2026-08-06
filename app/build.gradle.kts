plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

repositories {
    mavenLocal()
    mavenCentral()
    google()
}

dependencies {
    implementation(project(":render"))
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

compose.desktop {
    application {
        mainClass = "plottwin.app.WalkableToyPlotAppKt"
    }
}
