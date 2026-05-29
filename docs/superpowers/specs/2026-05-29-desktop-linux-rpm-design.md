# PainHunt Desktop (Linux / native RPM) — Design

Date: 2026-05-29
Status: Approved pending user review

## Goal

Ship a Linux desktop build of PainHunt, distributed as a native RPM package,
reusing the existing KMP `shared` module (domain, repositories, ViewModels).
Full feature parity with the Android app at first release.

## Premise correction

`painhunt-android` is only partially KMP today:

- `shared/` is a KMP module but targets **Android only** (`androidTarget`). It
  holds domain models, repositories (Supabase Postgrest + Ktor), and ViewModels
  (`androidx.lifecycle`, multiplatform-capable).
- `androidApp/` is a **pure Android** app. Its UI is **Jetpack Compose**
  (`androidx.compose` + Activity + `androidx.navigation.compose`), not Compose
  Multiplatform. UI is therefore Android-only.

Conclusion: domain/data/presentation is share-ready; UI is not shared.

## Decisions

- **UI strategy:** Separate desktop UI, shared logic only. Android UI untouched;
  desktop gets its own Compose Desktop view layer reusing shared ViewModels/repos.
- **Config delivery:** Compile-time from `local.properties` (mirrors Android).
- **Scope:** Full parity — all 7 screens at first release.
- **Ktor JVM engine:** CIO (pure-Kotlin, no extra native deps).
- **Desktop navigation:** JetBrains `navigation-compose` (multiplatform port of
  androidx Navigation; same type-safe `@Serializable` route API).

## Architecture / module layout

```
painhunt-android/
  shared/        # + jvm("desktop") target, + desktopMain source set
  androidApp/    # unchanged
  desktopApp/    # NEW — Compose Desktop UI, depends on :shared
```

- `shared` remains single source of domain + repositories + ViewModels.
- `desktopApp`: `kotlin("jvm")` + `org.jetbrains.compose` + compose-compiler
  plugin (`org.jetbrains.kotlin.plugin.compose`), depends on `:shared`.
- `settings.gradle.kts` adds `include(":desktopApp")`.

## Shared module changes

- Add `jvm("desktop")` target to `shared/build.gradle.kts`.
- New `desktopMain` source set with JVM Ktor engine dependency: **CIO**
  (`io.ktor:ktor-client-cio`). Resolves the engine for the bare `HttpClient()`
  in `IdeaChatViewModel` on desktop.
- `commonMain` deps (supabase-postgrest, ktor-client-core, coroutines-core,
  kotlinx-datetime, serialization-json, lifecycle-viewmodel) already KMP — JVM
  variants resolve automatically.
- Verify `androidx.lifecycle:lifecycle-viewmodel:2.10.0` resolves its JVM
  artifact for the desktop target (it is multiplatform; expected to work).

## desktopApp UI

Reuse logic; rewrite the view layer for desktop. Compose Multiplatform shares
`androidx.compose.*` package names, so most screen composables port with
import/dependency swaps rather than full rewrites.

Per-screen Android→desktop substitutions:

| Android-only thing | Desktop replacement |
|---|---|
| `androidx.navigation.compose` | `org.jetbrains.androidx.navigation:navigation-compose` (same `NavHost`/`composable<Route>`/`toRoute` API; nav graph ports near-verbatim) |
| `viewModel { }` (androidx) | JetBrains `lifecycle-viewmodel-compose` `viewModel { }` (same call) |
| `R.string` / `strings.xml` | inline strings or a `Strings` object in desktopApp |
| `PainHuntTheme` dynamic color | static Material3 color scheme for desktop |
| `ConnectivityState` (Android `ConnectivityManager`) | JVM connectivity check (reachability probe) |
| `MainActivity` | `main()` → `application { Window { App() } }`, same nav graph |

Screens ported: Feed, Idea Detail + AI chat, Sources, Settings, Implementing
list, Implementing detail, Offline.

Routes reused from Android (`@Serializable` objects/data classes): FeedRoute,
SourcesRoute, SettingsRoute, DetailRoute(ideaId), ImplementingRoute,
ImplementingDetailRoute(implementationId).

Bottom navigation (Feed / Implementing) replicated; desktop window with
appropriate min size.

## Config (compile-time from local.properties)

- Gradle task in `desktopApp` reads `local.properties` (same file Android uses)
  and generates `AppConfig.kt` into generated sources — desktop analog of
  Android `BuildConfig`.
- Generated object fields: `SUPABASE_URL`, `SUPABASE_ANON_KEY`,
  `SCRAPER_BASE_URL` (default `http://localhost:3000`), `MODELS_LIST`.
- `main()` calls `SupabaseClientProvider.create(AppConfig.SUPABASE_URL,
  AppConfig.SUPABASE_ANON_KEY)` and wires the same repos as `MainActivity`
  (Ideas, Bookmarks, Chat, Sources, Settings, Implementations).

## RPM packaging

Compose Desktop `nativeDistributions` via `jpackage`:

```kotlin
compose.desktop {
  application {
    mainClass = "com.painhunt.desktop.MainKt"
    nativeDistributions {
      targetFormats(TargetFormat.Rpm)
      packageName = "painhunt"
      packageVersion = "1.0.0"
      linux {
        // menu group, app category, icon
      }
    }
  }
}
```

- Build: `./gradlew :desktopApp:packageRpm` → `.rpm` under desktopApp build output.
- Host requirements: JDK with `jpackage` + `rpmbuild` available. Document this.

## Error handling

- Reuse existing ViewModel try/catch → `error` field in UI state (unchanged).
- Offline: desktop `ConnectivityState` drives `OfflineScreen` like Android.
- Missing `local.properties` values: generated `AppConfig` falls back to empty
  strings / documented defaults; app surfaces Supabase failures via existing
  error states.

## Testing

- Existing shared ViewModel unit tests run against the desktop JVM target too.
- `./gradlew :desktopApp:run` for manual smoke.
- `packageRpm` then local `rpm -i` (or `rpm -qpl`) to verify the artifact.

## Out of scope (first release)

- Auto-update mechanism.
- Other Linux formats (deb, AppImage) — RPM only for now.
- Sharing the UI layer between Android and desktop (kept separate by decision).
- Windows/macOS desktop packaging.

## Open verification items (resolve during build)

- Compose Multiplatform plugin version compatible with Kotlin `2.3.20`.
- Confirm `lifecycle-viewmodel` 2.10.0 JVM artifact resolves on desktop target.
- Confirm `material-icons-extended` availability under Compose Multiplatform.
