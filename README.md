# LauncherX

**LauncherX** is an unofficial, open-source home screen for Android, forked from
[Lawnchair 16](https://github.com/LawnchairLauncher/lawnchair), which itself is based on
Android's [Launcher3](https://source.android.com/docs/core/architecture/modular-system/launcher3).

Repository: [github.com/jerryforgit/LauncherX](https://github.com/jerryforgit/LauncherX)

Chinese name: **X 桌面**

It keeps Lawnchair's Pixel-style foundation and adds a set of focused, experimental
customizations: a liquid-glass dock, iPhone-style dock slots with sliding reordering, a
large-folder swiper, a Launchpad widget, and more.

> **Unofficial fork.** LauncherX is not affiliated with, endorsed by, or supported by the
> Lawnchair project or Google. Please report LauncherX-specific issues here, not upstream.

[中文说明](README.zh-CN.md)

Technical notes: [Liquid glass implementation](docs/LIQUID_GLASS.md)

---

## Status

- Base: Lawnchair 16 / Launcher3 from Android 16
- Current version: `16.Dev.(<commit>)`
- Development build: expect rough edges and breaking changes
- Target SDK: 37 · Min SDK: 26 (Android 8.0+)
- Tested on Android 8.1 (API 27, Xiaomi Mi 5X) and Android 14/15/16 tablets

LauncherX installs as a separate app from Lawnchair, so both can coexist:

| Build | Application ID |
| --- | --- |
| GitHub release | `com.jerry.launcherx` |
| Debug | `com.jerry.launcherx.debug` |
| Nightly flavor | `com.jerry.launcherx.nightly` |
| Play flavor | `com.jerry.launcherx.play` |

## Features

LauncherX-specific work on top of Lawnchair 16:

- **Liquid-glass dock**: a translucent capsule that adapts to light/dark themes and to the
  wallpaper, with separate blur-quality settings.
- **Dock width modes**: `Auto` (full width on phones, content width on tablets), `Full`
  (capsule spans the dock bar), and `Content` (capsule wraps the icons).
- **iPhone-style dock slots**: removing an icon compacts the remaining icons to the start of
  the row, with a 220 ms `translationX/Y` slide animation.
- **Drag-in placeholder**: dragging an app over the dock opens a gap at the nearest slot;
  dropping inserts it and shifts the following icons.
- **Large-folder swiper**: horizontal or vertical pages, wrap-around, page dots, and
  per-folder menu overrides.
- **Launchpad widget**: a 1×1 bitmap tile that follows the current icon shape and icon size.
- **Dock widgets**: 1×1 widgets can be placed in the dock when a slot is free.
- **Dock tuning**: app spacing, inner padding, icon count (up to 20), dock rows, and pages.
- **Android 8.x fallbacks**: the workspace caps icon size / fixes label padding on small cells, and the app drawer uses an opaque panel before Android 12 (where blur is unavailable).
- **LauncherX icon**: new 3D X adaptive/monochrome launcher icon (light blue/white).
- **Chinese name**: the app is shown as **X 桌面** in Simplified/Traditional Chinese.
- **Polished details**: dimmed dock capsule behind folder popups, QSB-aware dock height,
  and high-resolution frosted backgrounds for popups.

Some features are still work in progress. See [Known limitations](#known-limitations).

## Build

### Requirements

- Linux, macOS, or Windows with WSL2
- JDK 21
- Android SDK with **API 37** and build-tools **37.0.0**
- Git

### From the command line

```bash
git clone --recurse-submodules https://github.com/jerryforgit/LauncherX.git LauncherX
cd LauncherX

# Point Gradle at your Android SDK (or open the project in Android Studio)
echo "sdk.dir=$HOME/Android/Sdk" > local.properties

./gradlew assembleLawnWithQuickstepGithubDebug
```

The debug APK is written to:

```text
build/outputs/apk/lawnWithQuickstepGithub/debug/
```

Install it with:

```bash
adb install -r build/outputs/apk/lawnWithQuickstepGithub/debug/*.apk
```

For a release build:

```bash
./gradlew assembleLawnWithQuickstepGithubRelease
```

Release signing is read from `keystore.properties` at the repository root (ignored by Git).
If the file is absent, the debug signing key is used.

### Android Studio

Open the repository root as a Gradle project, let the SDK sync, and select the
`lawnWithQuickstepGithub` / `debug` variant.

## Repository layout

This is a Launcher3/Lawnchair-derived monorepo. Most LauncherX code lives in:

- `lawnchair/src/com/jerry/launcherx/` — Lawnchair/LauncherX-specific Kotlin and Java code
- `lawnchair/res/` — LauncherX resources and preferences
- `src/` — Launcher3 sources
- `quickstep/` — Quickstep / recents integration
- `platform_frameworks_libs_systemui/` — Git submodule shared with Lawnchair

The app-level Java/Kotlin package is now `com.jerry.launcherx`. Three names are intentionally
kept for compatibility:

- `app.lawnchair.icons` — icon drawables/shared icon helpers also used by the
  `platform_frameworks_libs_systemui` submodule
- `com.android.launcher3` — the AOSP namespace used by `R`, `BuildConfig`, AIDL and resources
- `app.lawnchair.lawnfeed` — an optional external feed app, not part of LauncherX

The Android application ID and the user-visible name are LauncherX. See
[PACKAGE_RENAME.md](PACKAGE_RENAME.md) for the completed rename and the remaining exceptions.

## Known limitations

- The fork is under active development; not every upstream feature is tested on every device.
- A few internal namespaces still use upstream names (`app.lawnchair.icons`,
  `com.android.launcher3`); the installed app ID is `com.jerry.launcherx`.
- Some user-visible strings in third-party translations still say “Lawnchair” until the
  translations are refreshed.
- The dock supports up to 20 slots. A single row with 30 slots would overlap at the default
  icon size; high-density docks need smaller icons or automatic paging.
- Some upstream online services (About links, nightly update checks, live information) still
  point at Lawnchair infrastructure. These will be replaced with LauncherX endpoints.
- Gesture-navigation recents on some OEM ROMs (e.g. ZUI) is a platform limitation.

## Contributing

Contributions are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md) first.

- Use GitHub Issues for bug reports and feature requests.
- Keep pull requests focused and include a test plan.
- Do not commit signing keys, `keystore.properties`, `local.properties`, or build outputs.
- Run `./gradlew spotlessCheck` before opening a pull request.

## License and attribution

LauncherX is distributed under the **Apache License 2.0**, unless a file states otherwise.
See [LICENSE.txt](LICENSE.txt) and [NOTICE](NOTICE).

This project contains code and resources from:

- [Android Launcher3](https://source.android.com/docs/core/architecture/modular-system/launcher3) —
  Apache License 2.0
- [Lawnchair](https://github.com/LawnchairLauncher/lawnchair) — Apache License 2.0, with
  some files carrying their own GPL-3.0 headers (for example the Flowerpot parser)
- [platform_frameworks_libs_systemui](https://github.com/LawnchairLauncher/platform_frameworks_libs_systemui)

Upstream copyright notices and license headers are retained. If you redistribute LauncherX,
keep the NOTICE and license files, and comply with the license of every file you use.

## Credits

Thanks to the Lawnchair maintainers and contributors, the AOSP Launcher3 team, and everyone
who has reported bugs or contributed translations and code upstream.
