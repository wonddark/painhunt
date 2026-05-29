# PainHunt Desktop (Linux / RPM) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a Linux desktop build of PainHunt as a native RPM, reusing the shared KMP domain/data/presentation layer, at full feature parity with Android.

**Architecture:** Add a `jvm("desktop")` target to `:shared`, create a new `:desktopApp` Compose Desktop module that reuses shared ViewModels/repositories and re-implements the view layer for desktop (Compose Multiplatform reuses `androidx.compose.*` packages, so screens port with import/dependency swaps). Config is baked at build time from `local.properties` into a generated `AppConfig.kt`. RPM is produced via Compose Desktop `jpackage`.

**Tech Stack:** Kotlin Multiplatform (Kotlin 2.3.20), Compose Multiplatform (`org.jetbrains.compose`), JetBrains multiplatform `navigation-compose`, `lifecycle-viewmodel-compose`, Ktor CIO engine, Supabase Postgrest KT, `jpackage`/`rpmbuild`.

**Working directory:** all paths are relative to `painhunt-android/` unless noted. The repo root is one level up (contains `docs/`, `supabase/`, `painhunt-scraper/`).

---

## Pre-flight: environment facts

- Repo root: `/home/oz/Projects/Personal/painhunt`
- Gradle project root: `/home/oz/Projects/Personal/painhunt/painhunt-android`
- Run gradle from the project root, e.g. `cd painhunt-android && ./gradlew <task>`.
- `local.properties` already holds `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `SCRAPER_BASE_URL`, `MODELS_LIST` (consumed by Android `BuildConfig`).
- RPM packaging requires `jpackage` (in the JDK, JDK 17+) and `rpmbuild` (`rpm-build` package) on the build host. Verify before Task 9.

## File structure

**Modify:**
- `shared/src/commonMain/kotlin/com/painhunt/presentation/FeedViewModel.kt` — drop Android coupling.
- `androidApp/src/main/kotlin/com/painhunt/ui/feed/FeedScreen.kt` — read bytes before calling `uploadFile`.
- `shared/build.gradle.kts` — add desktop target + `desktopMain`.
- `gradle/libs.versions.toml` — add Compose MP + desktop libs.
- `settings.gradle.kts` — include `:desktopApp`.

**Create (desktopApp):**
- `desktopApp/build.gradle.kts` — Compose Desktop + RPM config + AppConfig generation task.
- `desktopApp/src/main/kotlin/com/painhunt/desktop/Main.kt` — `main()`, window, repo wiring.
- `desktopApp/src/main/kotlin/com/painhunt/desktop/App.kt` — nav graph (port of `MainActivity`).
- `desktopApp/src/main/kotlin/com/painhunt/desktop/theme/Theme.kt` — static Material3 theme.
- `desktopApp/src/main/kotlin/com/painhunt/desktop/platform/Connectivity.kt` — JVM connectivity.
- `desktopApp/src/main/kotlin/com/painhunt/desktop/platform/Platform.kt` — `openUrl` + `pickJsonFileBytes`.
- `desktopApp/src/main/kotlin/com/painhunt/ui/...` — ported screens (7).
- Generated (by Gradle task): `com/painhunt/desktop/AppConfig.kt`.

---

## Task 1: Decouple FeedViewModel from Android

`FeedViewModel.uploadFile(Context, Uri)` uses `android.content.Context`/`android.net.Uri` inside `commonMain`. This compiles only while Android is the sole target. Change it to accept raw bytes; move file reading to the UI layer of each platform.

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/painhunt/presentation/FeedViewModel.kt`
- Modify: `androidApp/src/main/kotlin/com/painhunt/ui/feed/FeedScreen.kt`

- [ ] **Step 1: Remove Android imports and change `uploadFile` signature**

In `FeedViewModel.kt`, delete these three imports:
```kotlin
import android.content.Context
import android.net.Uri
import java.io.IOException
```

Replace the entire `uploadFile` function with:
```kotlin
    fun uploadFile(bytes: ByteArray) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true, scrapeResult = null) }
            try {
                val scraperBaseUrl = settingsRepository.get().scraperBaseUrl
                val response = httpClient.post("$scraperBaseUrl/scrape/upload") {
                    contentType(ContentType.Application.Json)
                    setBody(bytes)
                }
                if (!response.status.isSuccess()) {
                    throw IllegalStateException("Server error ${response.status.value}: ${response.bodyAsText()}")
                }
                val body = response.bodyAsText()
                _uiState.update { it.copy(isUploading = false, scrapeResult = body) }
                loadIdeas()
            } catch (e: Exception) {
                _uiState.update { it.copy(isUploading = false, error = "Upload failed: ${e.message}") }
            }
        }
    }
```
(`IllegalStateException` is multiplatform; replaces the removed `java.io.IOException`.)

- [ ] **Step 2: Update the Android caller to read bytes first**

In `FeedScreen.kt`, the launcher callback currently calls `viewModel.uploadFile(context, uri)`. Replace the `fileLauncher` block (lines ~40-42) with:
```kotlin
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val bytes = context.contentResolver.openInputStream(uri)?.readBytes()
            if (bytes != null) viewModel.uploadFile(bytes)
        }
    }
```
Leave the `android.net.Uri`, `rememberLauncherForActivityResult`, `LocalContext` imports — still used. `context` is still needed for `contentResolver`.

- [ ] **Step 3: Verify Android still compiles**

Run: `cd painhunt-android && ./gradlew :androidApp:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add painhunt-android/shared/src/commonMain/kotlin/com/painhunt/presentation/FeedViewModel.kt \
        painhunt-android/androidApp/src/main/kotlin/com/painhunt/ui/feed/FeedScreen.kt
git commit -m "refactor(shared): decouple FeedViewModel.uploadFile from Android Context/Uri"
```

---

## Task 2: Add desktop JVM target to shared

**Files:**
- Modify: `shared/build.gradle.kts`
- Modify: `gradle/libs.versions.toml`

- [ ] **Step 1: Add Ktor CIO library to the version catalog**

In `gradle/libs.versions.toml`, under `[libraries]` (near the other ktor lines) add:
```toml
ktor-client-cio = { group = "io.ktor", name = "ktor-client-cio", version.ref = "ktor" }
```

- [ ] **Step 2: Add the desktop target and source set**

In `shared/build.gradle.kts`, inside the `kotlin { }` block, after the `androidTarget { ... }` block add:
```kotlin
    jvm("desktop") {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
                }
            }
        }
    }
```

In the same file, inside `sourceSets { }`, after the `androidMain.dependencies { ... }` block add:
```kotlin
        val desktopMain by getting
        desktopMain.dependencies {
            implementation(libs.ktor.client.cio)
            implementation(libs.coroutines.core)
        }
```

- [ ] **Step 3: Verify the shared module compiles for desktop**

Run: `cd painhunt-android && ./gradlew :shared:compileKotlinDesktop`
Expected: BUILD SUCCESSFUL. If it fails on `androidx.lifecycle` resolution for the desktop target, confirm `lifecycle-viewmodel` 2.10.0 publishes a JVM artifact; if not, bump `lifecycle` in the catalog to the latest 2.10.x that does and re-run.

- [ ] **Step 4: Verify Android target still compiles (no regression)**

Run: `cd painhunt-android && ./gradlew :shared:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add painhunt-android/shared/build.gradle.kts painhunt-android/gradle/libs.versions.toml
git commit -m "feat(shared): add jvm(desktop) target with Ktor CIO engine"
```

---

## Task 3: Add Compose Multiplatform + desktop libraries to the catalog

**Files:**
- Modify: `gradle/libs.versions.toml`

- [ ] **Step 1: Add versions**

Under `[versions]` add (versions to confirm against the Compose Multiplatform ↔ Kotlin compatibility table for Kotlin 2.3.20 in Step 3):
```toml
composeMultiplatform = "1.10.0"
navigationCompose = "2.9.0-beta01"
lifecycleViewmodelCompose = "2.10.0"
```

- [ ] **Step 2: Add plugin + libraries**

Under `[plugins]` add:
```toml
jetbrains-compose = { id = "org.jetbrains.compose", version.ref = "composeMultiplatform" }
```
Under `[libraries]` add:
```toml
navigation-compose-mp = { group = "org.jetbrains.androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
lifecycle-viewmodel-compose-mp = { group = "org.jetbrains.androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycleViewmodelCompose" }
```
(JetBrains `org.jetbrains.androidx.*` artifacts are the multiplatform navigation/lifecycle-compose surfaces. If desktop dependency resolution later reports duplicate `androidx.lifecycle` classes against the shared module's Google `androidx.lifecycle:lifecycle-viewmodel`, switch `lifecycle-viewmodel-compose-mp` to the Google coordinate `androidx.lifecycle:lifecycle-viewmodel-compose` at the same `lifecycle` version.)

- [ ] **Step 3: Confirm versions are compatible with Kotlin 2.3.20**

Use the Context7 MCP (`resolve-library-id` → `query-docs` for "compose-multiplatform compatibility kotlin 2.3.20"), or the JetBrains compatibility page, to confirm the chosen `composeMultiplatform`, `navigationCompose`, and `lifecycleViewmodelCompose` versions support Kotlin 2.3.20. Adjust the three version numbers in Step 1 if needed. This task creates no compiled output yet, so verification happens in Task 4 Step 4.

- [ ] **Step 4: Commit**

```bash
git add painhunt-android/gradle/libs.versions.toml
git commit -m "build: add Compose Multiplatform and desktop libs to version catalog"
```

---

## Task 4: Create desktopApp module skeleton + RPM packaging config

**Files:**
- Modify: `settings.gradle.kts`
- Create: `desktopApp/build.gradle.kts`
- Create: `desktopApp/src/main/kotlin/com/painhunt/desktop/Main.kt` (temporary stub)

- [ ] **Step 1: Register the module**

In `settings.gradle.kts`, after `include(":androidApp")` add:
```kotlin
include(":desktopApp")
```

- [ ] **Step 2: Create `desktopApp/build.gradle.kts`**

```kotlin
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
            implementation(compose.components.resources)

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
```
Note: `kotlin { jvm() }` with `compose.desktop` is the standard Compose Desktop layout; source set is `jvmMain`, source dir `desktopApp/src/jvmMain/kotlin`. Use that path for all desktop sources below. (The File-structure section's `src/main/kotlin` was indicative; the real path is `src/jvmMain/kotlin`.)

- [ ] **Step 3: Create a temporary `Main.kt` stub**

Path: `desktopApp/src/jvmMain/kotlin/com/painhunt/desktop/Main.kt`
```kotlin
package com.painhunt.desktop

import androidx.compose.material3.Text
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "PainHunt") {
        Text("PainHunt desktop — bootstrapping")
    }
}
```

- [ ] **Step 4: Verify it compiles and runs**

Run: `cd painhunt-android && ./gradlew :desktopApp:compileKotlinJvm`
Expected: BUILD SUCCESSFUL. If Compose plugin/version errors appear, fix versions from Task 3 Step 1 and re-run.

Optional smoke (opens a window): `./gradlew :desktopApp:run`

- [ ] **Step 5: Commit**

```bash
git add painhunt-android/settings.gradle.kts painhunt-android/desktopApp/build.gradle.kts \
        painhunt-android/desktopApp/src/jvmMain/kotlin/com/painhunt/desktop/Main.kt
git commit -m "feat(desktop): scaffold desktopApp Compose Desktop module with RPM config"
```

---

## Task 5: Generate AppConfig.kt from local.properties

Desktop analog of Android `BuildConfig`. A Gradle task reads `local.properties` (at the Gradle project root) and writes `AppConfig.kt` into generated sources.

**Files:**
- Modify: `desktopApp/build.gradle.kts`

- [ ] **Step 1: Add the generation task and wire it into the source set**

In `desktopApp/build.gradle.kts`, after the `kotlin { ... }` block add:
```kotlin
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
```

- [ ] **Step 2: Generate and inspect**

Run: `cd painhunt-android && ./gradlew :desktopApp:generateAppConfig`
Expected: BUILD SUCCESSFUL, and the file exists:
Run: `cat painhunt-android/desktopApp/build/generated/appconfig/com/painhunt/desktop/AppConfig.kt`
Expected: an `object AppConfig` with the four `const val`s populated from `local.properties`.

- [ ] **Step 3: Verify compilation picks it up**

Run: `cd painhunt-android && ./gradlew :desktopApp:compileKotlinJvm`
Expected: BUILD SUCCESSFUL (generation runs automatically as a source dependency).

- [ ] **Step 4: Commit**

```bash
git add painhunt-android/desktopApp/build.gradle.kts
git commit -m "feat(desktop): generate AppConfig.kt from local.properties at build time"
```

---

## Task 6: Desktop platform helpers (theme, connectivity, url/file)

**Files:**
- Create: `desktopApp/src/jvmMain/kotlin/com/painhunt/desktop/theme/Theme.kt`
- Create: `desktopApp/src/jvmMain/kotlin/com/painhunt/desktop/platform/Connectivity.kt`
- Create: `desktopApp/src/jvmMain/kotlin/com/painhunt/desktop/platform/Platform.kt`

- [ ] **Step 1: Static Material3 theme**

`theme/Theme.kt`:
```kotlin
package com.painhunt.desktop.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

@Composable
fun PainHuntTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme, content = content)
}
```

- [ ] **Step 2: JVM connectivity**

`platform/Connectivity.kt` — mirrors Android's `rememberIsOnline(): Pair<Boolean, () -> Unit>` API so the ported nav code is unchanged:
```kotlin
package com.painhunt.desktop.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

private fun probeOnline(): Boolean = runCatching {
    Socket().use { it.connect(InetSocketAddress("1.1.1.1", 53), 1500) }
    true
}.getOrDefault(false)

@Composable
fun rememberIsOnline(): Pair<Boolean, () -> Unit> {
    var isOnline by remember { mutableStateOf(true) }
    var ticker by remember { mutableStateOf(0) }
    LaunchedEffect(ticker) {
        isOnline = withContext(Dispatchers.IO) { probeOnline() }
        while (true) {
            delay(15_000)
            isOnline = withContext(Dispatchers.IO) { probeOnline() }
        }
    }
    return Pair(isOnline) { ticker++ }
}
```

- [ ] **Step 3: URL opener + JSON file picker**

`platform/Platform.kt`:
```kotlin
package com.painhunt.desktop.platform

import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.net.URI

fun openUrl(url: String) {
    runCatching {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url))
        }
    }
}

/** Opens a native file chooser filtered to .json and returns the file bytes, or null if cancelled. */
fun pickJsonFileBytes(): ByteArray? {
    val dialog = FileDialog(null as Frame?, "Import JSON", FileDialog.LOAD).apply {
        setFilenameFilter { _, name -> name.endsWith(".json", ignoreCase = true) }
        isVisible = true
    }
    val dir = dialog.directory ?: return null
    val name = dialog.file ?: return null
    return File(dir, name).readBytes()
}
```

- [ ] **Step 4: Verify compilation**

Run: `cd painhunt-android && ./gradlew :desktopApp:compileKotlinJvm`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add painhunt-android/desktopApp/src/jvmMain/kotlin/com/painhunt/desktop/theme \
        painhunt-android/desktopApp/src/jvmMain/kotlin/com/painhunt/desktop/platform
git commit -m "feat(desktop): add static theme, JVM connectivity, url/file helpers"
```

---

## Task 7: Port the screen composables to desktop

Copy each screen from `androidApp` into `desktopApp` under the same `com.painhunt.ui.*` package, then apply the listed edits to remove Android-only APIs. Compose Multiplatform reuses `androidx.compose.*` packages, so most imports are unchanged.

**Files (copy then edit):**
- Create: `desktopApp/src/jvmMain/kotlin/com/painhunt/ui/feed/FeedScreen.kt`, `IdeaCard.kt`
- Create: `desktopApp/src/jvmMain/kotlin/com/painhunt/ui/detail/IdeaDetailScreen.kt`
- Create: `desktopApp/src/jvmMain/kotlin/com/painhunt/ui/implementing/ImplementingListScreen.kt`, `ImplementingDetailScreen.kt`
- Create: `desktopApp/src/jvmMain/kotlin/com/painhunt/ui/settings/SettingsScreen.kt`
- Create: `desktopApp/src/jvmMain/kotlin/com/painhunt/ui/sources/SourcesScreen.kt`
- Create: `desktopApp/src/jvmMain/kotlin/com/painhunt/ui/offline/OfflineScreen.kt`

- [ ] **Step 1: Copy the screen files verbatim**

```bash
cd painhunt-android
SRC=androidApp/src/main/kotlin/com/painhunt/ui
DST=desktopApp/src/jvmMain/kotlin/com/painhunt/ui
mkdir -p $DST/feed $DST/detail $DST/implementing $DST/settings $DST/sources $DST/offline
cp $SRC/feed/FeedScreen.kt $SRC/feed/IdeaCard.kt $DST/feed/
cp $SRC/detail/IdeaDetailScreen.kt $DST/detail/
cp $SRC/implementing/ImplementingListScreen.kt $SRC/implementing/ImplementingDetailScreen.kt $DST/implementing/
cp $SRC/settings/SettingsScreen.kt $DST/settings/
cp $SRC/sources/SourcesScreen.kt $DST/sources/
cp $SRC/offline/OfflineScreen.kt $DST/offline/
```

- [ ] **Step 2: Fix `feed/FeedScreen.kt` (file picker)**

Delete imports:
```kotlin
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
```
Add import:
```kotlin
import com.painhunt.desktop.platform.pickJsonFileBytes
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
```
Delete the two lines:
```kotlin
    val context = LocalContext.current
```
and the whole `val fileLauncher = rememberLauncherForActivityResult(...) { ... }` block.
Add after the `menuExpanded` state line:
```kotlin
    val scope = rememberCoroutineScope()
```
Replace the import IconButton `onClick = { fileLauncher.launch("application/json") }` with:
```kotlin
                        onClick = {
                            scope.launch {
                                val bytes = withContext(Dispatchers.IO) { pickJsonFileBytes() }
                                if (bytes != null) viewModel.uploadFile(bytes)
                            }
                        },
```

- [ ] **Step 3: Fix `detail/IdeaDetailScreen.kt` (open URL)**

Delete imports:
```kotlin
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
```
Add import:
```kotlin
import com.painhunt.desktop.platform.openUrl
```
Delete the line `val context = LocalContext.current`.
Replace `context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(idea.url)))` with `openUrl(idea.url)`.

- [ ] **Step 4: Fix `implementing/ImplementingDetailScreen.kt` (open URL)**

Delete imports:
```kotlin
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
```
Add import:
```kotlin
import com.painhunt.desktop.platform.openUrl
```
Delete the line `val context = LocalContext.current`.
Replace `onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(idea.url))) }` with `onClick = { openUrl(idea.url) }`.

- [ ] **Step 5: Fix `settings/SettingsScreen.kt` (models list)**

Replace import `import com.painhunt.app.BuildConfig` with `import com.painhunt.desktop.AppConfig`.
Replace `private val OLLAMA_MODELS = BuildConfig.MODELS_LIST.split(",")` with `private val OLLAMA_MODELS = AppConfig.MODELS_LIST.split(",")`.

- [ ] **Step 6: Sanity-check the remaining files for stray Android imports**

Run:
```bash
cd painhunt-android
grep -rn "^import android\.\|com.painhunt.app\|LocalContext\|androidx.activity" desktopApp/src/jvmMain/kotlin/com/painhunt/ui/
```
Expected: no output. If `IdeaCard.kt`, `SourcesScreen.kt`, `ImplementingListScreen.kt`, or `OfflineScreen.kt` surface any, remove them the same way (these were read as pure-Compose and should need no changes).

- [ ] **Step 7: Verify compilation**

Run: `cd painhunt-android && ./gradlew :desktopApp:compileKotlinJvm`
Expected: BUILD SUCCESSFUL. Resolve any remaining unresolved references (typically a leftover Android import) before continuing.

- [ ] **Step 8: Commit**

```bash
git add painhunt-android/desktopApp/src/jvmMain/kotlin/com/painhunt/ui
git commit -m "feat(desktop): port all screens to Compose Desktop"
```

---

## Task 8: Desktop App nav graph + Main entry point

Port `MainActivity`'s nav graph into `App.kt`, and wire repos + window in `Main.kt`.

**Files:**
- Create: `desktopApp/src/jvmMain/kotlin/com/painhunt/desktop/App.kt`
- Modify: `desktopApp/src/jvmMain/kotlin/com/painhunt/desktop/Main.kt` (replace stub)

- [ ] **Step 1: Create `App.kt`**

This mirrors `MainActivity.onCreate`'s `setContent { ... }` body. Repos are passed in (created in `Main.kt`).
```kotlin
package com.painhunt.desktop

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.painhunt.data.BookmarksRepository
import com.painhunt.data.ChatRepository
import com.painhunt.data.IdeasRepository
import com.painhunt.data.ImplementationsRepository
import com.painhunt.data.SettingsRepository
import com.painhunt.data.SourcesRepository
import com.painhunt.desktop.platform.rememberIsOnline
import com.painhunt.desktop.theme.PainHuntTheme
import com.painhunt.presentation.FeedViewModel
import com.painhunt.presentation.IdeaChatViewModel
import com.painhunt.presentation.IdeaDetailViewModel
import com.painhunt.presentation.ImplementingDetailViewModel
import com.painhunt.presentation.ImplementingListViewModel
import com.painhunt.presentation.SettingsViewModel
import com.painhunt.presentation.SourcesViewModel
import com.painhunt.ui.detail.IdeaDetailScreen
import com.painhunt.ui.feed.FeedScreen
import com.painhunt.ui.implementing.ImplementingDetailScreen
import com.painhunt.ui.implementing.ImplementingListScreen
import com.painhunt.ui.offline.OfflineScreen
import com.painhunt.ui.settings.SettingsScreen
import com.painhunt.ui.sources.SourcesScreen
import kotlinx.serialization.Serializable

@Serializable object FeedRoute
@Serializable object SourcesRoute
@Serializable object SettingsRoute
@Serializable data class DetailRoute(val ideaId: String)
@Serializable object ImplementingRoute
@Serializable data class ImplementingDetailRoute(val implementationId: String)

class AppRepositories(
    val ideas: IdeasRepository,
    val bookmarks: BookmarksRepository,
    val chat: ChatRepository,
    val sources: SourcesRepository,
    val settings: SettingsRepository,
    val implementations: ImplementationsRepository,
)

@Composable
fun App(repos: AppRepositories) {
    PainHuntTheme {
        val (isOnline, retryConnection) = rememberIsOnline()
        if (!isOnline) {
            OfflineScreen(onRetry = retryConnection)
            return@PainHuntTheme
        }

        val navController = rememberNavController()
        val backStack by navController.currentBackStackEntryAsState()

        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = backStack?.destination?.hasRoute(FeedRoute::class) == true,
                        onClick = { navController.navigate(FeedRoute) { launchSingleTop = true } },
                        icon = { Icon(Icons.Default.Home, null) },
                        label = { Text("Feed") },
                    )
                    NavigationBarItem(
                        selected = backStack?.destination?.hasRoute(ImplementingRoute::class) == true,
                        onClick = { navController.navigate(ImplementingRoute) { launchSingleTop = true } },
                        icon = { Icon(Icons.Default.Build, null) },
                        label = { Text("Implementing") },
                    )
                }
            }
        ) { innerPadding ->
            NavHost(
                navController,
                startDestination = FeedRoute,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable<FeedRoute> {
                    val vm = viewModel { FeedViewModel(repos.ideas, repos.settings) }
                    FeedScreen(
                        viewModel = vm,
                        onIdeaClick = { ideaId -> navController.navigate(DetailRoute(ideaId)) },
                        onSources = { navController.navigate(SourcesRoute) { launchSingleTop = true } },
                        onSettings = { navController.navigate(SettingsRoute) { launchSingleTop = true } },
                    )
                }
                composable<DetailRoute> { entry ->
                    val route = entry.toRoute<DetailRoute>()
                    val vm = viewModel { IdeaDetailViewModel(repos.ideas, repos.bookmarks, repos.settings, repos.implementations) }
                    val chatVm = viewModel { IdeaChatViewModel(repos.chat, repos.settings) }
                    IdeaDetailScreen(
                        ideaId = route.ideaId,
                        viewModel = vm,
                        chatViewModel = chatVm,
                        onBack = { navController.popBackStack() },
                        onNavigateToImplementation = { implId -> navController.navigate(ImplementingDetailRoute(implId)) },
                    )
                }
                composable<SourcesRoute> {
                    val vm = viewModel { SourcesViewModel(repos.sources) }
                    SourcesScreen(vm, onBack = { navController.popBackStack() })
                }
                composable<SettingsRoute> {
                    val vm = viewModel { SettingsViewModel(repos.settings) }
                    SettingsScreen(vm, onBack = { navController.popBackStack() })
                }
                composable<ImplementingRoute> {
                    val vm = viewModel { ImplementingListViewModel(repos.implementations) }
                    ImplementingListScreen(vm) { id -> navController.navigate(ImplementingDetailRoute(id)) }
                }
                composable<ImplementingDetailRoute> { entry ->
                    val route = entry.toRoute<ImplementingDetailRoute>()
                    val vm = viewModel { ImplementingDetailViewModel(repos.implementations, repos.ideas) }
                    ImplementingDetailScreen(route.implementationId, vm) { navController.popBackStack() }
                }
            }
        }
    }
}
```
Note: if Task 3 Step 2's fallback was used (Google `androidx.lifecycle:lifecycle-viewmodel-compose`), the `import androidx.lifecycle.viewmodel.compose.viewModel` line is still correct. The JetBrains nav artifact exposes the same `androidx.navigation.*` import paths used above.

- [ ] **Step 2: Replace `Main.kt` with real wiring**

```kotlin
package com.painhunt.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.painhunt.data.BookmarksRepository
import com.painhunt.data.ChatRepository
import com.painhunt.data.IdeasRepository
import com.painhunt.data.ImplementationsRepository
import com.painhunt.data.SettingsRepository
import com.painhunt.data.SourcesRepository
import com.painhunt.data.SupabaseClientProvider

fun main() = application {
    val supabase = SupabaseClientProvider.create(AppConfig.SUPABASE_URL, AppConfig.SUPABASE_ANON_KEY)
    val repos = AppRepositories(
        ideas = IdeasRepository(supabase),
        bookmarks = BookmarksRepository(supabase),
        chat = ChatRepository(supabase),
        sources = SourcesRepository(supabase),
        settings = SettingsRepository(supabase),
        implementations = ImplementationsRepository(supabase),
    )
    Window(
        onCloseRequest = ::exitApplication,
        title = "PainHunt",
        state = rememberWindowState(width = 1000.dp, height = 720.dp),
    ) {
        App(repos)
    }
}
```

- [ ] **Step 3: Verify compilation**

Run: `cd painhunt-android && ./gradlew :desktopApp:compileKotlinJvm`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Smoke-run the app**

Run: `cd painhunt-android && ./gradlew :desktopApp:run`
Expected: a window opens titled "PainHunt" showing the Feed (or the Offline screen if no network). Manually click into an idea, open the AI chat tab, and trigger Sources/Settings from the Feed menu to confirm navigation and data loading work. Close the window to end.

- [ ] **Step 5: Commit**

```bash
git add painhunt-android/desktopApp/src/jvmMain/kotlin/com/painhunt/desktop/App.kt \
        painhunt-android/desktopApp/src/jvmMain/kotlin/com/painhunt/desktop/Main.kt
git commit -m "feat(desktop): wire nav graph and Main entry point with Supabase repos"
```

---

## Task 9: Build and verify the RPM

**Files:** none (build only).

- [ ] **Step 1: Confirm host tooling**

Run: `which jpackage && which rpmbuild`
Expected: both resolve. If `rpmbuild` is missing, install it (`sudo zypper install rpm-build` on this openSUSE host) and re-run.

- [ ] **Step 2: Package the RPM**

Run: `cd painhunt-android && ./gradlew :desktopApp:packageRpm`
Expected: BUILD SUCCESSFUL. Artifact lands under `desktopApp/build/compose/binaries/main/rpm/`.

- [ ] **Step 3: Inspect the artifact**

Run:
```bash
cd painhunt-android
ls -lh desktopApp/build/compose/binaries/main/rpm/
rpm -qpi desktopApp/build/compose/binaries/main/rpm/painhunt-*.rpm
rpm -qpl desktopApp/build/compose/binaries/main/rpm/painhunt-*.rpm | head -20
```
Expected: a `painhunt-1.0.0-1.x86_64.rpm` (or similar) with package metadata and a bundled JRE + launcher under `/opt/painhunt`.

- [ ] **Step 4: (Optional, requires sudo) install and launch**

```bash
sudo rpm -i painhunt-android/desktopApp/build/compose/binaries/main/rpm/painhunt-*.rpm
/opt/painhunt/bin/PainHunt   # exact launcher path per rpm -qpl output
```
Expected: the installed app launches and behaves like the `:run` smoke test. Uninstall with `sudo rpm -e painhunt` when done.

- [ ] **Step 5: Update docs and commit**

Append a "Desktop (Linux)" build section to the repo `README` (or create `painhunt-android/desktopApp/README.md`) documenting: `./gradlew :desktopApp:run` to run, `./gradlew :desktopApp:packageRpm` to build the RPM, and the `jpackage`/`rpmbuild` host requirements.
```bash
git add -A
git commit -m "docs(desktop): document desktop run and RPM packaging"
```

---

## Self-review notes

- **Spec coverage:** module layout (Task 4), shared jvm target + CIO (Task 2), compile-time AppConfig from local.properties (Task 5), navigation-compose MP + lifecycle-viewmodel-compose (Tasks 3/8), all 7 screens (Task 7), RPM via jpackage (Tasks 4/9), Offline + connectivity (Task 6). The spec's "open verification items" are addressed by the version-confirmation step (Task 3 Step 3) and compile gates after each task.
- **Added beyond spec:** Task 1 (FeedViewModel decoupling) — a hard prerequisite the design missed; without it the desktop target cannot compile.
- **Testing approach:** The codebase has no test infrastructure and uses concrete (non-interfaced) repositories that take a live `SupabaseClient`, so ViewModel unit tests would require an interface refactor outside this plan's scope. Verification is therefore by compile gates, `:desktopApp:run` smoke, and RPM install — matching the existing project's conventions.
- **Type consistency:** `uploadFile(bytes: ByteArray)`, `AppConfig.{SUPABASE_URL,SUPABASE_ANON_KEY,SCRAPER_BASE_URL,MODELS_LIST}`, `AppRepositories(ideas,bookmarks,chat,sources,settings,implementations)`, `rememberIsOnline(): Pair<Boolean, () -> Unit>`, `openUrl(String)`, `pickJsonFileBytes(): ByteArray?` are used consistently across tasks.
