plugins {
    alias(libs.plugins.kotlinJvm)
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":worldstate"))
    api(project(":solvers"))
    testImplementation(libs.kotlin.test)
    testImplementation(testFixtures(project(":solvers")))
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}
