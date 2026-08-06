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
    implementation(project(":eyes"))
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
