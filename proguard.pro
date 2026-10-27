# The rules from AOSP are located in proguard.flags file, we can just maintain LauncherX related rules here.

# Optimization options.
-allowaccessmodification
-dontusemixedcaseclassnames
-allowaccessmodification
-keepattributes InnerClasses, *Annotation*, Signature, SourceFile, LineNumberTable

# Remove some Kotlin overhead
-processkotlinnullchecks remove

# Common rules.
-keep class android.window.** { *; }
-keep class android.view.** { *; }

-keepclassmembers class * implements android.os.Parcelable {
  public static final ** CREATOR;
}

# LauncherX specific rules.
-keep,allowshrinking,allowoptimization class com.jerry.launcherx.LauncherXProto$* { *; }
-keep,allowshrinking,allowoptimization class com.jerry.launcherx.LauncherXApp { *; }
-keep,allowshrinking,allowoptimization class com.jerry.launcherx.LauncherXLauncher { *; }
-keep,allowshrinking,allowoptimization class com.jerry.launcherx.compatlib.** { *; }

# Keep Compose stability fields for preference classes
-keepclassmembers class com.jerry.launcherx.preferences.** {
    public static final int $stable;
}
-keepclassmembers class com.jerry.launcherx.preferences2.** {
    public static final int $stable;
}
# Keep preference inner classes entirely (Compose $stable issue)
-keep class com.jerry.launcherx.preferences.BasePreferenceManager$* { *; }
-keep class com.jerry.launcherx.preferences.BasePreferenceManager { *; }

-keep,allowshrinking,allowoptimization class com.google.protobuf.Timestamp { *; }
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }

# We intentionally remove it to replace Smartspacer's widget popup with our own Launcher3 popup
-dontwarn com.skydoves.balloon.*

# This shouldn't concern us much
-dontwarn androidx.window.extensions.**
-dontwarn androidx.window.sidecar.**
