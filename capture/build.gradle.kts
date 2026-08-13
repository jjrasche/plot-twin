plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    `java-test-fixtures`
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":worldstate"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(project(":geometry"))
    testImplementation(libs.kotlin.test)
    testImplementation(libs.sqlite.jdbc)
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}
