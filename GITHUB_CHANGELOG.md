# LauncherX 16.Dev

Initial LauncherX fork of Lawnchair 16.

Highlights in this build:

- Liquid-glass dock with light/dark tinting and quality settings.
- Dock width modes: Auto, Full, and Content.
- iPhone-style dock slots: removing an app compacts the remaining icons, with a
  translation-based shift animation.
- Drag-in placeholder for dock icons.
- Large-folder swiper with horizontal/vertical paging and page dots.
- Launchpad 1×1 widget and dock widget support.
- Dock icon count up to 20, with app spacing and inner-padding controls.
- New LauncherX adaptive/monochrome launcher icon.
- Android 8.x fixes: label padding/icon sizing on small cells, and an opaque app-drawer
  background before Android 12 (no blur available).
- Remove LeakCanary from debug builds, so the separate “Leaks” launcher entry is gone.
- Source package renamed from `app.lawnchair` to `com.jerry.launcherx`
  (`app.lawnchair.icons`, `com.android.launcher3`, and the external
  `app.lawnchair.lawnfeed` are intentionally unchanged).
- Class/object brand renamed from `Lawnchair*` to `LauncherX*`
  (e.g. `LauncherXLauncher`, `LauncherXApp`, `LauncherXTheme`).

LauncherX is an unofficial fork and installs under `com.jerry.launcherx`
(`com.jerry.launcherx.debug` for debug builds). It is not affiliated with Lawnchair or Google.

Upstream: https://github.com/LawnchairLauncher/lawnchair
License: Apache-2.0 (see LICENSE.txt and NOTICE).
