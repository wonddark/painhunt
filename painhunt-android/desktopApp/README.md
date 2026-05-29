# PainHunt Desktop (Linux)

Compose Multiplatform desktop build of PainHunt. Reuses the `:shared` KMP module
(domain, repositories, ViewModels); the view layer is desktop-specific Compose.

## Run

```bash
./gradlew :desktopApp:run
```

Opens the app window (Feed by default; Offline screen if no network).

## Configuration

Supabase URL/key, scraper URL, and model list are baked at build time from
`local.properties` at the Gradle project root (same file Android's `BuildConfig`
uses). The `generateAppConfig` Gradle task writes them into a generated
`com.painhunt.desktop.AppConfig` object. Keys recognized:

- `SUPABASE_URL`
- `SUPABASE_ANON_KEY`
- `SCRAPER_BASE_URL` (default `http://localhost:3000`)
- `MODELS_LIST` (default `gtp-oss:20b,gemma4:31b-cloud`)

## Package a native RPM

```bash
./gradlew :desktopApp:packageRpm
# -> desktopApp/build/compose/binaries/main/rpm/painhunt-<version>-1.x86_64.rpm
```

The RPM bundles its own JRE and installs to `/opt/painhunt` with launcher
`/opt/painhunt/bin/painhunt` and a `.desktop` menu entry.

### Host requirements

- `rpmbuild` (openSUSE: `sudo zypper install rpm-build`).
- A JDK 17+ **that includes `jpackage`**. The packaging task uses the JDK that
  runs Gradle. JetBrains Runtime (Android Studio's bundled JBR) and some split
  distro JDK packages do **not** ship `jpackage`. If `packageRpm` fails with a
  missing-`jpackage` error, run Gradle under a full JDK, e.g.:

  ```bash
  JAVA_HOME=/path/to/jdk-with-jpackage ./gradlew :desktopApp:packageRpm
  ```

  Verify a candidate JDK with `"$JAVA_HOME/bin/jpackage" --version`.

### Install / uninstall (optional)

```bash
sudo rpm -i desktopApp/build/compose/binaries/main/rpm/painhunt-*.rpm
/opt/painhunt/bin/painhunt
sudo rpm -e painhunt   # uninstall
```
