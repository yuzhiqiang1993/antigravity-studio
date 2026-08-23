plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            // Coroutines & Serialization
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)

            // Koin DI
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            // Settings
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.noarg)

            // Ktor Server & Client
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.cio)
            implementation(libs.ktor.server.cors)
            implementation(libs.ktor.server.content.negotiation)
            implementation(libs.ktor.server.sse)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

// ---------------------------------------------------------------------------
// 利用 Gradle 原生 Task 生成 BuildInfo (类似 Android BuildConfig，零外部插件依赖)
// ---------------------------------------------------------------------------
abstract class GenerateBuildConfigTask : DefaultTask() {
    @get:Input
    abstract val debugMode: Property<Boolean>

    @get:Input
    abstract val buildType: Property<String>

    @get:Input
    abstract val versionName: Property<String>

    @get:Input
    abstract val versionCode: Property<Int>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val out = outputDir.get().asFile
        val packageDir = File(out, "com/yuzhiqiang/antigravity")
        packageDir.mkdirs()
        val isDebug = debugMode.get()
        File(packageDir, "BuildInfo.kt").writeText(
            """
            package com.yuzhiqiang.antigravity

            /**
             * 由 Gradle 原生任务自动生成的构建环境信息（对标 Android BuildConfig）
             */
            object BuildInfo {
                const val DEBUG: Boolean = $isDebug
                const val BUILD_TYPE: String = "${buildType.get()}"
                const val VERSION_NAME: String = "${versionName.get()}"
                const val VERSION_CODE: Int = ${versionCode.get()}
                val IS_RELEASE: Boolean = !$isDebug
            }
            """.trimIndent() + "\n"
        )
    }
}

val isReleaseTask = gradle.startParameter.taskNames.any { task ->
    task.contains("package", ignoreCase = true) ||
    task.contains("createDistributable", ignoreCase = true) ||
    task.contains("release", ignoreCase = true)
}
val explicitBuildType = project.findProperty("buildType")?.toString()
val effectiveBuildType = explicitBuildType ?: if (isReleaseTask) "release" else "debug"
val isDebugBuild = effectiveBuildType.equals("debug", ignoreCase = true)

val generateBuildConfig = tasks.register<GenerateBuildConfigTask>("generateBuildConfig") {
    debugMode.set(isDebugBuild)
    buildType.set(effectiveBuildType)
    versionName.set("1.0.0")
    versionCode.set(100)
    outputDir.set(layout.buildDirectory.dir("generated/source/buildConfig/commonMain/kotlin"))
}

kotlin.sourceSets.named("commonMain") {
    kotlin.srcDir(generateBuildConfig.map { it.outputDir.get().asFile })
}

