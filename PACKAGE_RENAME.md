# Package and class rename — completed

## Result

The app-level source package and `Lawnchair*` class identifiers have been renamed:

| Before | After |
| --- | --- |
| `app.lawnchair` | `com.jerry.launcherx` |
| `app.lawnchair.LawnchairApp` | `com.jerry.launcherx.LauncherXApp` |
| `app.lawnchair.LawnchairLauncher` | `com.jerry.launcherx.LauncherXLauncher` |
| `app.lawnchair.*` (most subpackages) | `com.jerry.launcherx.*` |
| `Lawnchair*` classes/objects/theme | `LauncherX*` (e.g. `LawnchairTheme` → `LauncherXTheme`) |

Class files containing the brand were renamed as well (for example `LawnchairLauncher.kt` →
`LauncherXLauncher.kt`, `LawnchairWidgetHolder.kt` → `LauncherXWidgetHolder.kt`). The generated
protobuf outer class is now `LauncherXProto` (`launcher_x_version` field, same field number).
Upstream copyright headers and translated declined forms of “Lawnchair” are intentionally left
untouched.

Moved source roots:

- `lawnchair/src/com/jerry/launcherx/`
- `lawnchair/aidl/com/jerry/launcherx/`
- `compatLib/*/src/main/java/com/jerry/launcherx/`
- `baseline-profile/src/main/java/com/jerry/launcherx/`
- `systemUI/shared/src/com/jerry/launcherx/`
- `tests/unit/com/jerry/launcherx/`
- `tests/multivalentTests/src/com/jerry/launcherx/`

Updated namespaces/class references in:

- `build.gradle` (`launcher_component`)
- `AndroidManifest.xml`, `AndroidManifest-common.xml`, `lawnchair/AndroidManifest.xml`,
  `quickstep/AndroidManifest*.xml`
- `lawnchair/res/values/config.xml`
- custom-view class names in XML
- Quickstep, AOSP `src/` imports, AIDL, proguard rules, tests, docs

## Intentionally kept

These are **not** LauncherX application code and must keep their current names:

| Name | Reason |
| --- | --- |
| `app.lawnchair.icons` | Shared with `platform_frameworks_libs_systemui` (submodule) icon drawables/helpers |
| `com.android.launcher3` | AOSP namespace used by `R`, `BuildConfig`, AIDL and resources |
| `app.lawnchair.lawnfeed` | Optional external feed app; not part of LauncherX |
| `platform_frameworks_libs_systemui` | Git submodule; not modified |

`lawnchair/src/app/lawnchair/icons/` therefore remains the main app's icon package alongside
the submodule's `app.lawnchair.icons` classes.

## Application ID is unchanged

- Release/GitHub: `com.jerry.launcherx`
- Debug: `com.jerry.launcherx.debug`
- Nightly: `com.jerry.launcherx.nightly`
- Play: `com.jerry.launcherx.play`

Because the application ID did not change, existing app data (prefs/database) is preserved on
update.

## Component name change

The launcher activity class changed from:

```text
app.lawnchair.LawnchairLauncher
```

to:

```text
com.jerry.launcherx.LauncherXLauncher
```

When updating an existing installation, Android may drop LauncherX as the default HOME because
the old component no longer exists. Re-select LauncherX as the default home app, or run:

```bash
adb shell cmd package set-home-activity \
  com.jerry.launcherx.debug/com.jerry.launcherx.LauncherXLauncher
```

Existing widgets/shortcuts created by the old debug build may also need to be re-added, because
their provider component names changed.

## Verification

- `assembleLawnWithQuickstepGithubDebug` — successful
- `assembleLawnWithQuickstepGithubRelease` — successful
- `assembleLawnWithQuickstepPlayDebug` and `assembleLawnWithQuickstepNightlyRelease` — successful
- `testLawnWithQuickstepGithubDebugUnitTest` — successful
- Debug APK: `com.jerry.launcherx.debug`, label `LauncherX (Debug)`, activity
  `com.jerry.launcherx.LauncherXLauncher`
- Release APK: `com.jerry.launcherx`, label `LauncherX`, activity
  `com.jerry.launcherx.LauncherXLauncher`
- Installed and launched on Xiaomi Mi 5X / Android 8.1 (API 27), set as default HOME

## If `app.lawnchair.icons` should also be renamed later

Fork the `platform_frameworks_libs_systemui` submodule, rename its
`app.lawnchair.icons` package and the related imports, then move
`lawnchair/src/app/lawnchair/icons` to `lawnchair/src/com/jerry/launcherx/icons`. Do not rename
`app.lawnchair.lawnfeed`.
