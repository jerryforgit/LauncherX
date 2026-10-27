# Contributing to LauncherX

Thanks for your interest in LauncherX. This is an experimental fork of Lawnchair 16; bug
reports, code, translations, and design feedback are all welcome.

## Before opening an issue

- Search existing issues first.
- Make sure the problem is present in the latest LauncherX build, not only in an old build.
- Check whether the same problem exists in upstream Lawnchair. If it is an upstream bug,
  reporting it to Lawnchair as well may be useful, but LauncherX-specific changes belong here.
- Include your device model, Android version, LauncherX version, and exact reproduction steps.
- Attach a logcat if the app crashes or misbehaves. Avoid including personal data such as
  account names, phone numbers, or private file paths.

## Pull requests

1. Keep the change focused. One logical change per pull request.
2. Follow the existing code style and package structure.
3. Run `./gradlew spotlessCheck` before submitting.
4. Build and test the affected variant:
   `./gradlew assembleLawnWithQuickstepGithubDebug`.
5. Describe what changed, why it changed, and how you tested it.
6. Do not commit `local.properties`, `keystore.properties`, `*.jks`, APKs, AABs, or build
   outputs.
7. Retain upstream copyright and license headers. New files should use the Apache-2.0 header
   used by the rest of the project unless they deliberately follow another compatible license.

## Source layout

- `lawnchair/src/com/jerry/launcherx/` — LauncherX/Lawnchair-specific code
- `lawnchair/res/` — LauncherX resources and settings
- `src/` — Launcher3 sources
- `quickstep/` — recents/Quickstep integration
- `platform_frameworks_libs_systemui/` — Git submodule

The app-level package is `com.jerry.launcherx`. `app.lawnchair.icons`,
`com.android.launcher3`, and `app.lawnchair.lawnfeed` are intentionally kept: the first is shared
with the icon submodule, the second is the AOSP namespace, and the third is an external feed app.
Do not rename those in ordinary PRs; see `PACKAGE_RENAME.md`.

## Translations

Some translations are inherited from Lawnchair and still use the Lawnchair name. A
translation cleanup is welcome, but keep changes separate from functional pull requests.

## Licensing

By contributing, you agree that your contribution is licensed under the Apache License 2.0,
unless a file explicitly states another license. See [LICENSE.txt](LICENSE.txt) and
[NOTICE](NOTICE).
