package com.jerry.launcherx.views

import android.animation.ValueAnimator
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import com.android.launcher3.BubbleTextView
import com.android.launcher3.CellLayout
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.folder.Folder
import com.android.launcher3.folder.FolderIcon
import com.android.launcher3.folder.FolderPagedView
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Drives the "jiggle" rotation of home-screen icons while the launcher is in
 * [com.android.launcher3.LauncherState.EDIT_MODE], mirroring the editing affordance of modern OEM
 * launchers (HyperOS / HarmonyOS / iOS).
 *
 * A single [ValueAnimator] feeds one shared wave, with a per-icon phase offset so icons wobble out
 * of sync without each needing its own animator. Each icon also pivots around the bottom of its
 * icon drawable so it appears to rock on its base, and the wave is shaped so the motion snaps
 * through upright and lingers at the extremes instead of swaying smoothly like a pendulum.
 *
 * Folder icons join in as a single unit; the tiles inside an inline (large) folder jiggle
 * individually, matching the contents of an opened floating folder.
 */
class EditModeIconAnimator(private val launcher: Launcher) {

    private class IconState(
        val view: View,
        val phase: Float,
        val amplitude: Float,
        val originalPivotX: Float,
        val originalPivotY: Float,
    )

    private var animator: ValueAnimator? = null
    private val icons = ArrayList<IconState>(24)
    private var lastOpenFolder: Folder? = null

    fun start() {
        stop()
        collectIcons()
        if (icons.isEmpty()) {
            return
        }
        animator =
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = WAVE_DURATION_MS
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { applyRotation(it.animatedFraction) }
                start()
            }
    }

    fun stop() {
        animator?.cancel()
        animator = null
        resetIcons()
        lastOpenFolder = null
    }

    /**
     * Forces every workspace icon to redraw, so edit-mode adornments such as the remove badge
     * appear or disappear immediately.
     */
    fun invalidateIcons() {
        forEachIcon { it.invalidate() }
    }

    private fun resetIcons() {
        for (state in icons) {
            state.view.rotation = 0f
            state.view.pivotX = state.originalPivotX
            state.view.pivotY = state.originalPivotY
        }
        icons.clear()
    }

    private fun applyRotation(fraction: Float) {
        // Folder contents only jiggle while a folder is open, so rebuild the icon list whenever the
        // open folder changes (opening, closing or switching folders).
        val openFolder = Folder.getOpen(launcher)
        if (openFolder !== lastOpenFolder) {
            resetIcons()
            collectIcons()
            lastOpenFolder = openFolder
        }
        val wave = fraction * TWO_PI
        for (state in icons) {
            val view = state.view
            if (view.parent == null) {
                continue
            }
            view.rotation = state.amplitude * wobble(wave + state.phase)
        }
    }

    /**
     * Shapes a sine wave so the icon moves quickly through the neutral (upright) pose and lingers
     * near the extremes, giving the "mechanical" feel of OEM edit modes rather than a smooth sway.
     */
    private fun wobble(angle: Float): Float {
        val s = sin(angle.toDouble()).toFloat()
        val a = abs(s)
        val shaped = 2f * a - a * a
        return if (s < 0f) -shaped else shaped
    }

    private fun collectIcons() {
        forEachIcon { icon ->
            val originalPivotX = icon.pivotX
            val originalPivotY = icon.pivotY
            val bounds = Rect()
            if (icon is BubbleTextView) {
                icon.getIconBounds(bounds)
            }
            val hasBounds = bounds.width() > 0 && bounds.height() > 0
            icon.pivotX = if (hasBounds) bounds.exactCenterX() else icon.width / 2f
            icon.pivotY = if (hasBounds) bounds.bottom.toFloat() else icon.height * 0.72f
            val seed = ((icon.hashCode() and 0x7fffffff) % 1000) / 1000f
            icons.add(
                IconState(
                    view = icon,
                    phase = seed * TWO_PI,
                    amplitude = BASE_AMPLITUDE * (0.85f + 0.3f * seed),
                    originalPivotX = originalPivotX,
                    originalPivotY = originalPivotY,
                ),
            )
        }
    }

    private fun forEachIcon(action: (View) -> Unit) {
        val workspace = launcher.workspace ?: return
        for (page in 0 until workspace.pageCount) {
            val layout = workspace.getPageAt(page) as? CellLayout ?: continue
            layout.shortcutsAndWidgets?.let { forEachIcon(it, action) }
        }
        launcher.hotseat?.shortcutsAndWidgets?.let { forEachIcon(it, action) }
        val folderContent = Folder.getOpen(launcher)
            ?.findViewById<FolderPagedView>(R.id.folder_content) ?: return
        for (page in 0 until folderContent.pageCount) {
            val layout = folderContent.getPageAt(page) ?: continue
            layout.shortcutsAndWidgets?.let { forEachIcon(it, action) }
        }
    }

    private fun forEachIcon(container: ViewGroup, action: (View) -> Unit) {
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            when {
                // A legacy folder icon behaves like a single icon; an inline (large) folder
                // exposes its tiles so each one jiggles and can be removed on its own.
                child is FolderIcon -> {
                    if (child.isInlineActive()) {
                        forEachIcon(child, action)
                    } else if (child.parent != null) {
                        action(child)
                    }
                }

                child is BubbleTextView -> if (child.parent != null) action(child)
                child is ViewGroup -> forEachIcon(child, action)
            }
        }
    }

    companion object {
        private const val WAVE_DURATION_MS = 620L
        private const val BASE_AMPLITUDE = 4.0f
        private const val TWO_PI = (2 * PI).toFloat()
    }
}
