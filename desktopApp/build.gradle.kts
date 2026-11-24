import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose)
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(project(":ui"))
    implementation(compose.desktop.currentOs)
    implementation(libs.coroutines.swing)
    implementation(libs.litertlm.jvm)
    implementation(libs.jna.platform)
    testImplementation(kotlin("test"))
}

tasks.test { useJUnitPlatform() }

compose.desktop {
    application {
        mainClass = "br.offgr1d.companion.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "2B Companion"
            packageVersion = "1.0.0" // o MSI não aceita 0.x
            modules("java.net.http", "jdk.unsupported")
        }
    }
}
