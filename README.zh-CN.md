# LauncherX

**LauncherX** 是一个非官方的 Android 开源桌面，基于
[Lawnchair 16](https://github.com/LawnchairLauncher/lawnchair)，而 Lawnchair 又基于 Android
的 [Launcher3](https://source.android.com/docs/core/architecture/modular-system/launcher3)。

它保留 Lawnchair 的 Pixel 风格基础，并加入一些我自己维护的定制功能：液态玻璃 Dock、iPhone
式 Dock 槽位和补位动画、大文件夹滑动分页、启动台小组件等。

> **非官方分支。** LauncherX 与 Lawnchair 项目、Google 没有任何隶属或背书关系。
> LauncherX 特有的问题请在本仓库反馈，不要提交到 Lawnchair 上游。

[English](README.md)

---

## 当前状态

- 基础版本：Lawnchair 16 / Android 16 Launcher3
- 当前版本：`16.Dev.(<commit>)`
- 开发版，可能有粗糙之处和不兼容变更
- Target SDK 37 · Min SDK 26（Android 8.0+）
- 已在 Android 8.1（API 27，小米 5X）和 Android 14/15/16 平板实测

LauncherX 使用独立的包名，可以和 Lawnchair 同时安装：

| 构建 | 包名 |
| --- | --- |
| GitHub 版本 | `com.jerry.launcherx` |
| 调试版 | `com.jerry.launcherx.debug` |
| Nightly 变体 | `com.jerry.launcherx.nightly` |
| Play 变体 | `com.jerry.launcherx.play` |

## 功能

在 Lawnchair 16 基础上，LauncherX 目前包含：

- **液态玻璃 Dock**：跟随明暗主题和壁纸的半透明胶囊，可单独调节模糊画质。
- **Dock 宽度模式**：`自动`（手机全宽、平板内容宽度）、`全宽`、`内容宽度`。
- **iPhone 式 Dock 槽位**：移出一个图标后，剩余图标压到最前面，并带 220ms 的
  `translationX/Y` 补位动画。
- **拖入占位**：把 app 拖到 Dock 上时，手指最近的槽位会先空出来；松手插入，后面的图标让位。
- **大文件夹滑动分页**：横向/纵向、循环、圆点指示器，以及单个文件夹的菜单覆盖。
- **启动台小组件**：1×1 位图 tile，跟随当前图标形状和图标大小。
- **Dock 小组件**：有空位时可以放 1×1 小组件。
- **Dock 调参**：应用间距、内边距、图标数量（最多 20）、Dock 行数、Dock 页数。
- **Android 8.x 兼容**：小格子上会限制图标大小并修正标签间距；Android 12 以下没有模糊，
  应用抽屉改用不透明背景。
- **LauncherX 图标**：新的自适应图标和单色图标。
- **细节修复**：文件夹弹窗时 Dock 胶囊变暗、QSB 关闭时的 Dock 高度、弹窗高清毛玻璃等。

部分功能仍在开发中，详见[已知限制](#已知限制)。

## 编译

### 环境要求

- Linux、macOS，或 Windows + WSL2
- JDK 21
- Android SDK **API 37**、build-tools **37.0.0**
- Git

### 命令行

```bash
git clone --recurse-submodules <你的仓库地址> LauncherX
cd LauncherX

# 指定 Android SDK（也可以直接用 Android Studio 打开）
echo "sdk.dir=$HOME/Android/Sdk" > local.properties

./gradlew assembleLawnWithQuickstepGithubDebug
```

调试 APK 输出在：

```text
build/outputs/apk/lawnWithQuickstepGithub/debug/
```

安装：

```bash
adb install -r build/outputs/apk/lawnWithQuickstepGithub/debug/*.apk
```

Release：

```bash
./gradlew assembleLawnWithQuickstepGithubRelease
```

Release 签名从仓库根目录的 `keystore.properties` 读取（该文件已被 Git 忽略）。
如果没有这个文件，会回退到 debug 签名。

### Android Studio

把仓库根目录作为 Gradle 项目打开，同步后选择
`lawnWithQuickstepGithub` / `debug` 变体。

## 仓库结构

这是一个 Launcher3/Lawnchair 衍生的 monorepo。LauncherX 的主要代码在：

- `lawnchair/src/com/jerry/launcherx/` — Lawnchair/LauncherX 相关的 Kotlin/Java 代码
- `lawnchair/res/` — LauncherX 资源和设置界面
- `src/` — Launcher3 源码
- `quickstep/` — Quickstep / 最近任务集成
- `platform_frameworks_libs_systemui/` — 与 Lawnchair 共用的 Git 子模块

app 层的 Java/Kotlin 包名现在已经是 `com.jerry.launcherx`。有三个名字为了兼容性保留：

- `app.lawnchair.icons` — 图标 drawable/共享图标工具，同时被
  `platform_frameworks_libs_systemui` 子模块使用
- `com.android.launcher3` — AOSP 命名空间，`R`、`BuildConfig`、AIDL 和资源都依赖它
- `app.lawnchair.lawnfeed` — 可选的外部喂源 app，不属于 LauncherX

Android 安装包 ID 和用户看到的名称是 LauncherX。完整改名记录和例外见
[PACKAGE_RENAME.md](PACKAGE_RENAME.md)。

## 已知限制

- 项目仍在开发中，不是所有上游功能都在所有设备上验证过。
- 少量内部命名空间仍沿用上游名字（`app.lawnchair.icons`、`com.android.launcher3`）；
  安装包 ID 是 `com.jerry.launcherx`。
- 部分第三方翻译里仍然写着“Lawnchair”，需要后续统一更新。
- Dock 最多 20 个槽位；30 个槽位在默认图标大小下会重叠，需要缩小图标或自动分页。
- About 链接、Nightly 更新检查、Live Information 等少量在线服务仍指向 Lawnchair 的基础设施，
  后续会替换成 LauncherX 自己的地址。
- 部分 OEM ROM（例如 ZUI）的手势导航最近任务属于系统限制。

## 贡献

欢迎提交 Issue 和 PR，请先阅读 [CONTRIBUTING.md](CONTRIBUTING.md)。

- Bug 和功能请求请走 GitHub Issues。
- PR 尽量小而集中，并写清楚测试步骤。
- 不要提交签名密钥、`keystore.properties`、`local.properties` 或编译产物。
- 提交 PR 前运行 `./gradlew spotlessCheck`。

## 许可证与署名

除非文件另有说明，LauncherX 使用 **Apache License 2.0**。
详见 [LICENSE.txt](LICENSE.txt) 和 [NOTICE](NOTICE)。

项目包含以下来源的代码和资源：

- [Android Launcher3](https://source.android.com/docs/core/architecture/modular-system/launcher3) —
  Apache License 2.0
- [Lawnchair](https://github.com/LawnchairLauncher/lawnchair) — Apache License 2.0；其中部分文件
  带有自己的 GPL-3.0 头（例如 Flowerpot parser）
- [platform_frameworks_libs_systemui](https://github.com/LawnchairLauncher/platform_frameworks_libs_systemui)

上游版权声明和许可证头都保留。重新分发 LauncherX 时请保留 NOTICE 和许可证文件，并遵守每个文件
对应的许可证。

## 致谢

感谢 Lawnchair 维护者和贡献者、AOSP Launcher3 团队，以及所有在上游报告问题、贡献翻译和代码的人。
