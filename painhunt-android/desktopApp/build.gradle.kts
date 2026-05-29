import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm()
    sourceSets {
        val jvmMain by getting
        jvmMain.dependencies {
            implementation(project(":shared"))

            implementation(compose.desktop.currentOs)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)

            implementation(libs.navigation.compose.mp)
            implementation(libs.lifecycle.viewmodel.compose.mp)

            implementation(libs.coroutines.core)
            implementation(libs.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.supabase.postgrest)
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.painhunt.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Rpm)
            packageName = "painhunt"
            packageVersion = "1.0.0"
            description = "PainHunt desktop"
            vendor = "PainHunt"
            linux {
                menuGroup = "Development"
                appCategory = "Development"
            }
        }
    }
}

val generateAppConfig by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/appconfig")
    outputs.dir(outputDir)
    val props = Properties().apply {
        rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }
    val supabaseUrl = props.getProperty("SUPABASE_URL", "")
    val supabaseAnonKey = props.getProperty("SUPABASE_ANON_KEY", "")
    val scraperBaseUrl = props.getProperty("SCRAPER_BASE_URL", "http://localhost:3000")
    val modelsList = props.getProperty("MODELS_LIST", "gtp-oss:20b,gemma4:31b-cloud")
    inputs.property("supabaseUrl", supabaseUrl)
    inputs.property("supabaseAnonKey", supabaseAnonKey)
    inputs.property("scraperBaseUrl", scraperBaseUrl)
    inputs.property("modelsList", modelsList)
    doLast {
        val dir = outputDir.get().asFile.resolve("com/painhunt/desktop")
        dir.mkdirs()
        dir.resolve("AppConfig.kt").writeText(
            """
            package com.painhunt.desktop

            object AppConfig {
                const val SUPABASE_URL = "$supabaseUrl"
                const val SUPABASE_ANON_KEY = "$supabaseAnonKey"
                const val SCRAPER_BASE_URL = "$scraperBaseUrl"
                const val MODELS_LIST = "$modelsList"
            }
            """.trimIndent() + "\n"
        )
    }
}

kotlin.sourceSets.named("jvmMain") {
    kotlin.srcDir(generateAppConfig)
}
