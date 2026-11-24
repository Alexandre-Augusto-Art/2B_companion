// Núcleo sem Android nem Windows: tudo aqui roda igual nos dois.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    api(libs.coroutines.core)
    api(libs.serialization.json)
    // Mesma API nos dois sistemas; cada app traz o binário do seu (litertlm-android ou litertlm-jvm).
    compileOnly(libs.litertlm.jvm)
    testImplementation(kotlin("test"))
    testImplementation(libs.coroutines.test)
}

tasks.test { useJUnitPlatform() }
