package com.jerry.launcherx.util

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import com.jerry.launcherx.LauncherXApp
import com.jerry.launcherx.LauncherXLauncher
import com.jerry.launcherx.preferences2.PreferenceManager2
import com.jerry.launcherx.preferences2.firstCached
import com.jerry.launcherx.views.overlay.FullScreenOverlayMode
import com.android.launcher3.BubbleTextView

/**
 * App launch animation helper for devices where LauncherX is not the recents
 * component (no CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS). In that case the
 * opening overlay modes cannot be applied via remote animations; instead a
 * self-drawn "fake zoom" icon mirror is shown in the launcher window while the
 * system animates the app window with a scale-up from the same bounds.
 */
object AppLaunchAnimation {

    /** Total icon zoom animation duration. */
    const val DURATION_MS = 600L

    /** Delay before the icon zoom starts (click feedback window). */
    const val START_DELAY_MS = 100L

    /** App window animation starts at this fraction of the icon animation. */
    const val APP_ANIM_START_FRACTION = 0.5f

    /** Whether the system lets us control the app window transition. */
    @JvmStatic
    fun isRemoteAppTransitionSupported(): Boolean = LauncherXApp.isRecentsEnabled

    /** Fake zoom is only used when remote transitions are unavailable and the user chose SUCK_IN. */
    @JvmStatic
    fun shouldFakeZoom(launcher: LauncherXLauncher): Boolean {
        if (isRemoteAppTransitionSupported()) return false
        val mode = PreferenceManager2.getInstance(launcher).openingAppOverlay.firstCached()
        return mode == FullScreenOverlayMode.SUCK_IN
    }

    @JvmStatic
    fun iconDrawableOf(v: View): Drawable? = when (v) {
        is BubbleTextView -> v.icon
        is ImageView -> v.drawable
        else -> null
    }

    /**
     * Mirrors [icon] at the on-screen bounds of [sourceView] inside the launcher's
     * drag layer and scales it up toward the screen center, fading out near the end.
     * Runs independently of (and alongside) the system window transition.
     */
    @JvmStatic
    fun playFakeZoom(launcher: LauncherXLauncher, sourceView: View, icon: Drawable) {
        val dragLayer = launcher.dragLayer ?: return
        if (sourceView.width <= 0 || sourceView.height <= 0) return

        val screenLoc = IntArray(2)
        sourceView.getLocationOnScreen(screenLoc)
        // Also get global visible rect for comparison
        val globalRect = android.graphics.Rect()
        sourceView.getGlobalVisibleRect(globalRect)

        // dragLayer's own location on screen — needed to convert screen coords
        // into the dragLayer's local coordinate space (where mirror.x/y live).
        val dlLoc = IntArray(2)
        dragLayer.getLocationOnScreen(dlLoc)

        // Use getIconBounds() for the on-view position of the icon graphic.
        // BubbleTextView draws the icon as a compound drawable; icon.bounds
        // does NOT reflect the view-relative offset — getIconBounds() does.
        val iconBoundsRect = android.graphics.Rect()
        if (sourceView is BubbleTextView) {
            sourceView.getIconBounds(iconBoundsRect)
        } else {
            iconBoundsRect.set(0, 0, sourceView.width, sourceView.height)
        }
        // Fall back to drawable bounds / view size if getIconBounds returned empty.
        if (iconBoundsRect.width() <= 0 || iconBoundsRect.height() <= 0) {
            val db = icon.bounds
            if (db.width() > 0 && db.height() > 0) {
                iconBoundsRect.set(db)
            } else {
                iconBoundsRect.set(0, 0, sourceView.width, sourceView.height)
            }
        }
        val size = maxOf(iconBoundsRect.width(), iconBoundsRect.height()).coerceAtLeast(1)
        val viewCenterX = screenLoc[0] + iconBoundsRect.exactCenterX()
        val viewCenterY = screenLoc[1] + iconBoundsRect.exactCenterY()

        android.util.Log.d("FakeZoom", "src=${sourceView.javaClass.simpleName} " +
            "screenLoc=${screenLoc[0]},${screenLoc[1]} " +
            "view=${sourceView.width}x${sourceView.height} " +
            "iconBoundsRect=${iconBoundsRect} " +
            "drawableBounds=${icon.bounds} " +
            "center=($viewCenterX,$viewCenterY) size=$size " +
            "dlLoc=${dlLoc[0]},${dlLoc[1]}")

        val mirror = ImageView(launcher).apply {
            setImageDrawable(icon.constantState?.newDrawable()?.mutate() ?: icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        dragLayer.addView(mirror, FrameLayout.LayoutParams(size, size))

        // Convert screen-space center to dragLayer-local space.
        // The ImageView is added at (0,0) in the dragLayer (FrameLayout default),
        // so translationX/Y = desired local position (left/top stay 0).
        val localCenterX = viewCenterX - dlLoc[0]
        val localCenterY = viewCenterY - dlLoc[1]
        val initTX = localCenterX - size / 2f
        val initTY = localCenterY - size / 2f

        // Target: dragLayer center in its own local space.
        val targetScale = maxOf(
            dragLayer.width.toFloat() / size,
            dragLayer.height.toFloat() / size,
        ) * 1.15f
        val targetTX = dragLayer.width / 2f - size / 2f
        val targetTY = dragLayer.height / 2f - size / 2f

        // Set the starting position via translationX/Y (left/top remain 0).
        mirror.translationX = initTX
        mirror.translationY = initTY

        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = DURATION_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { a ->
                val t = a.animatedValue as Float
                mirror.scaleX = 1f + (targetScale - 1f) * t
                mirror.scaleY = 1f + (targetScale - 1f) * t
                mirror.translationX = initTX + (targetTX - initTX) * t
                mirror.translationY = initTY + (targetTY - initTY) * t
                // Gradual fade: icon becomes more transparent as it scales up
                mirror.alpha = 1f - t * 0.9f
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    (mirror.parent as? ViewGroup)?.removeView(mirror)
                }
            })
        }
        // Delay start by 100ms so the click feedback is visible first
        mirror.postDelayed({ anim.start() }, START_DELAY_MS)
    }
}
