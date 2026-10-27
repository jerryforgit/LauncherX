# GitHub upload checklist

This file is a practical checklist for publishing LauncherX. It is not part of the app.

## 1. What has already been prepared

- Gradle project name: `LauncherX`
- App label: `LauncherX` / `LauncherX (Debug)`
- Application IDs:
  - `com.jerry.launcherx` (GitHub/release flavor)
  - `com.jerry.launcherx.debug` (debug)
  - `com.jerry.launcherx.nightly`
  - `com.jerry.launcherx.play`
- App-level source package is now `com.jerry.launcherx`. `app.lawnchair.icons`,
  `com.android.launcher3`, and `app.lawnchair.lawnfeed` are intentionally kept (shared submodule
  icon helpers, AOSP namespace, and an external feed app respectively). See
  `PACKAGE_RENAME.md`.
- `Lawnchair*` app classes/objects/themes are renamed to `LauncherX*`; upstream copyright headers
  and translated declined forms are preserved.
- `README.md`, `README.zh-CN.md`, `NOTICE`, `CONTRIBUTING.md`, `SECURITY.md`
- Upstream funding link and upstream-specific issue templates were replaced.
- `.gitignore` covers signing files and build outputs.
- Release builds no longer force `android:debuggable="true"`.
- Debug builds no longer bundle LeakCanary (the “Leaks” launcher entry is gone).
- New LauncherX adaptive/monochrome launcher icon.
- Android 8.x fallbacks: icon/label sizing and opaque app-drawer background below Android 12.

## 2. Before a later push

The initial push has been completed. Use this section when preparing future pushes.

Run these from the repository root:

```bash
git status --short
git diff --check
git submodule status
```

Check for accidental secrets or local files:

```bash
git status --short | grep -E 'local.properties|keystore.properties|\.jks|\.apk|\.aab' || true
git grep -nE 'AIza[0-9A-Za-z_-]{35}|ghp_[0-9A-Za-z]{36}|BEGIN (RSA|EC|OPENSSH) PRIVATE KEY' || true
```

`local.properties` is already ignored. Do not force-add it.

Build the exact variant that will be published:

```bash
./gradlew assembleLawnWithQuickstepGithubDebug
```

Optionally verify the APK identity (adjust the path and build-tools version):

```bash
$ANDROID_HOME/build-tools/37.0.0/aapt dump badging \
  build/outputs/apk/lawnWithQuickstepGithub/debug/*.apk | head
```

Expected package line:

```text
package: name='com.jerry.launcherx.debug' ...
```

`./gradlew spotlessCheck` currently reports formatting drift in pre-existing custom files.
The CI `check-style` step is therefore temporarily `continue-on-error: true`. Run
`./gradlew spotlessApply`, review the formatting-only diff, then remove that flag before
enforcing style checks.

## 3. Create the repository and push

Create an **empty** repository on GitHub named `LauncherX` (no README, no license, no
`.gitignore`), then:

```bash
# The existing remote points at Lawnchair. Keep it for upstream merges:
git remote rename origin upstream

git remote add origin git@github.com:jerryforgit/LauncherX.git
# or: git remote add origin https://github.com/jerryforgit/LauncherX.git

git add -A
git commit -m "LauncherX: fork of Lawnchair 16 with liquid-glass dock and dock slot animation"
git branch -M main
git push -u origin main
```

If an `origin` already points at your new repository, use `git remote set-url origin ...`
instead. Pushing `main` triggers the CI build because `main` is included in the workflow
branch list; the current local branch is `16-dev`, and `git branch -M main` renames it.

## 4. Repository settings

Recommended:

- Description: `Unofficial Launcher3/Lawnchair fork with a liquid-glass dock and iPhone-style dock slots.`
- Topics: `android`, `launcher`, `lawnchair`, `launcher3`, `pixel-launcher`, `customization`
- Enable Issues.
- Disable Discussions unless you plan to moderate them.
- Enable private vulnerability reporting (Security tab).
- Set the default branch to `main`.
- Add a LICENSE badge if GitHub does not detect it automatically (the project is Apache-2.0,
  with some GPL-3.0 files called out in NOTICE).

## 5. GitHub Actions

The checked-in workflows are inherited from Lawnchair:

- `ci.yml` builds debug/release variants and runs `spotlessCheck`. The build jobs work on a
  fork; upstream-only jobs are guarded by `github.repository_owner == 'LawnchairLauncher'`.
- `release_update.yml`, `build_release_apk.yml`, Crowdin sync, Telegram notifications and
  nightly publishing are upstream-specific or require secrets. They will not run on your fork
  unless you change their conditions.
- If you want GitHub release signing later, add these repository secrets:
  `KEYSTORE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, and change the signing-step
  condition away from the upstream owner guard.
- Never commit `keystore.properties` or the keystore itself.

## 6. Known upstream references to replace after the repo URL exists

These are intentionally left for the URL pass, because they need the final GitHub owner/name:

- `lawnchair/src/com/jerry/launcherx/ui/preferences/about/About.kt`
- `lawnchair/src/com/jerry/launcherx/ui/preferences/about/AboutViewModel.kt`
- `lawnchair/src/com/jerry/launcherx/ui/preferences/about/ChangesDialog.kt`
- `lawnchair/src/com/jerry/launcherx/ui/preferences/about/GithubService.kt` (nightly update repo)
- `lawnchair/res/values/config.xml` (`config_default_live_information_endpoint`)
- README links/badges, once the repository exists
- Remaining translated strings that still say “Lawnchair”

Until those are changed, the About screen and nightly updater may point at Lawnchair
infrastructure. Do not publish a “nightly updater” build until that is fixed.

## 7. History choice

The current Git history descends from Lawnchair/Launcher3. Keeping it preserves attribution and
makes upstream merges easier. If you prefer a single “initial commit”, you can create an orphan
branch, but you must keep the upstream license/NOTICE files and attribution. Keeping the
history is recommended.

The existing pack is roughly 926 MiB and the working tree (excluding `.git` and `build`) is
roughly 563 MiB. That is below GitHub's hard limits but makes the first push slow. For a
smaller public repository, an orphan initial commit is an option; do not use `git filter-repo`
on the only copy without a backup.

## 8. After a push

- Add CI badges and screenshot(s) if desired.
- Configure signing secrets.
- Create a first release from the `com.jerry.launcherx` APK.
- Mention in the release notes that this is an unofficial fork.
