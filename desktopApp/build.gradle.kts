import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)
    implementation(libs.compose.components.uiToolingPreview)

    // Ktor Server & Client for Desktop Engine
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.sse)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.serialization.kotlinx.json)
}

compose.desktop {
    application {
        mainClass = "com.yuzhiqiang.antigravity.studio.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Pkg, TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Antigravity Studio"
            packageVersion = "2.0.0"
            description = "All-in-one desktop hub and productivity studio for Antigravity AI tools"
            vendor = "yuzhiqiang"

            macOS {
                bundleID = "com.yuzhiqiang.antigravity.studio"
            }
            windows {
                menuGroup = "Antigravity Studio"
            }
        }
    }
}