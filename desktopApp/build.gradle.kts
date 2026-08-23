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
    implementation(libs.kotlinx.datetime)
    implementation(libs.compose.components.uiToolingPreview)
    implementation(libs.koin.core)

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
    implementation("org.slf4j:slf4j-nop:2.0.16")
}

compose.desktop {
    application {
        mainClass = "com.yuzhiqiang.antigravity.studio.MainKt"

        jvmArgs += listOf(
            "-Dapple.awt.application.appearance=system",
            "-Dskiko.vsync=true",
            "-Xmx1024m",
            "-Xms256m",
            "-XX:+UseG1GC"
        )

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Pkg, TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Antigravity Studio"
            packageVersion = "1.0.0"
            description = "All-in-one desktop hub and productivity studio for Antigravity AI tools"
            vendor = "yuzhiqiang"

            macOS {
                bundleID = "com.yuzhiqiang.antigravity.studio"
                iconFile.set(project.file("src/main/resources/icon.icns"))
            }
            windows {
                menuGroup = "Antigravity Studio"
                iconFile.set(project.file("src/main/resources/icon.ico"))
            }
        }
    }
}
