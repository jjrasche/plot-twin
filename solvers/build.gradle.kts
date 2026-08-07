plugins {
    alias(libs.plugins.kotlinJvm)
    `java-test-fixtures`
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":worldstate"))
    implementation(project(":geometry"))
    implementation(libs.solar.positioning)
    testImplementation(libs.kotlin.test)
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}
