# Modern Desktop Launcher - Integration Guide

## Overview
This module adds modern desktop features to Lawnchair, including:
- Large Folders (2x2, 3x3, 4x4 grid inline display)
- Adaptive Dock (Phone/Tablet/Desktop modes)
- Smart Stack Widgets
- Universal Search
- Desktop Profiles (Office/Entertainment/Minimal)
- Freeform Window Management
- Multi-finger Gesture Support
- Keyboard/Mouse Input Adaptation
- Layout Backup & Lock

## File Structure
```
lawnchair/src/com/jerry/launcherx/modern/
├── ModernDesktopManager.kt          # Main entry point
├── TabletDeviceProfileHelper.kt     # Tablet DPI/grid config
├── folder/
│   ├── LargeFolderView.kt           # Inline folder rendering
│   ├── LargeFolderGrid.kt           # Size → grid → capacity formula
│   ├── LargeFolderBinder.kt         # Binds FolderInfo to the inline grid
│   └── SmartFolderManager.kt        # Auto-categorize apps
├── dock/
│   ├── AdaptiveDockView.kt          # Phone/Tablet/Desktop dock
│   └── DockLayoutManager.kt         # Dock mode persistence
├── widget/
│   ├── SmartStackView.kt            # Swipeable widget stack
│   └── SmartStackManager.kt         # Stack persistence
├── search/
│   ├── UniversalSearchEngine.kt     # Multi-source search
│   └── UniversalSearchOverlay.kt    # Search UI overlay
├── profile/
│   ├── DesktopProfileManager.kt     # Layout profile management
│   └── LayoutLockManager.kt         # Lock/unlock layout
├── window/
│   └── FreeformWindowManager.kt     # Freeform/split screen
├── gesture/
│   └── MultiTouchGestureView.kt     # 3/4 finger gestures
├── input/
│   └── DesktopInputAdapter.kt       # Mouse/keyboard support
└── backup/
    └── LayoutBackupManager.kt       # Snapshot & restore

lawnchair/res/layout/
├── view_adaptive_dock.xml
├── view_smart_stack.xml
└── overlay_universal_search.xml
```

## Integration Steps

### Step 1: Modify InvariantDeviceProfile
In `src/com/android/launcher3/InvariantDeviceProfile.java`, add tablet-aware grid options:

```kotlin
// Add to getPredefinedGridOptions()
fun getTabletGridOptions(context: Context): List<GridOption> {
    val helper = TabletDeviceProfileHelper(context)
    // Generate grid options for different screen sizes
}
```

### Step 2: Modify Hotseat
In `src/com/android/launcher3/Hotseat.java`, add the adaptive dock:

```java
// In Hotseat's init or setup method:
if (ModernDesktopManager.getInstance(context).detectDeviceMode(...) == DockMode.TABLET) {
    // Use AdaptiveDockView layout
    inflate(context, R.layout.view_adaptive_dock, this);
}
```

### Step 3: Modify Folder
In `src/com/android/launcher3/folder/Folder.java`, add large folder rendering:

```kotlin
// Replace Folder open animation with inline rendering
fun openLargeFolder() {
    val largeFolderView = LargeFolderView(context)
    largeFolderView.setItems(folderItems)
    largeFolderView.onItemClicked = { item, _ ->
        launchApp(item)
    }
    // Replace existing folder dialog
}
```

### Step 4: Add Universal Search
In `Launcher.java`, add the search overlay:

```kotlin
// In onCreate or onResume:
val searchOverlay = UniversalSearchOverlay(this)
// Add to root layout, respond to swipe-down gesture
```

### Step 5: Add Gesture Support
In `LauncherRootView.java` or the main layout, add:

```kotlin
val gestureView = MultiTouchGestureView(this)
gestureView.setGestureCallback(
    ModernDesktopManager.getInstance(this).getGestureCallback()
)
// Add to root layout behind other views
```

### Step 6: Add Preferences
In `com/jerry/launcherx/preferences/PreferenceManager.kt`, add:

```kotlin
// Add new preference entries for:
// - Large folder size per folder
// - Dock mode (Phone/Tablet/Desktop)
// - Desktop profile selection
// - Layout lock toggle
// - Smart stack rotation interval
// - Search sources configuration
```

## Build Configuration
Add to `build.gradle` if needed:

```gradle
android {
    defaultConfig {
        minSdk 26  // Required for split screen APIs
    }
}
```

## Permissions
The following permissions may be needed in `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />
<uses-permission android:name="android.permission.READ_CONTACTS" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
```

## Testing Checklist
- [ ] Large folder renders correctly at 2x2, 3x3, 4x4
- [ ] Folder items can be reordered via drag
- [ ] Dock switches mode on tablet/phone
- [ ] Smart stack rotates and allows swiping
- [ ] Universal search finds apps, contacts, files
- [ ] Desktop profiles switch grid layout
- [ ] Split screen launches correctly
- [ ] Multi-finger gestures trigger actions
- [ ] Mouse hover and right-click work
- [ ] Layout backup/restore works
- [ ] Layout lock prevents edits
