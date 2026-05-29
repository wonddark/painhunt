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
