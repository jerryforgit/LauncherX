package com.jerry.launcherx.views

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.jerry.launcherx.ui.popup.LauncherOptionEntry
import com.android.app.animation.Interpolators
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherState
import com.android.launcher3.R
import com.android.launcher3.views.OptionsPopupView

/**
 * Bottom action bar shown while the launcher is in [LauncherState.EDIT_MODE].
 *
 * The entries are built from the same option list as the long-press popup (see
 * [com.android.launcher3.views.OptionsPopupView.getOptionEntries]), so anything enabled in the
 * popup settings — the system-settings entry for example — shows up here as well. Only the
 * "edit home screen" entry is dropped because the launcher is already editing, and the
 * "set as default page" entry takes over the page strip's former home button.
 *
 * [R.id.edit_mode_action_tidy] and [R.id.edit_mode_action_done] are bar-only actions and are
 * always appended after the shared entries.
 */
class EditModeActionBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val itemsContainer: LinearLayout

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        LayoutInflater.from(context).inflate(R.layout.edit_mode_action_bar, this, true)

        itemsContainer = findViewById(R.id.edit_mode_action_items)
        findViewById<View>(R.id.edit_mode_action_tidy).setOnClickListener { v ->
            Launcher.getLauncher(v.context).tidyUpWorkspace()
        }
        findViewById<View>(R.id.edit_mode_action_done).setOnClickListener { v ->
            Launcher.getLauncher(v.context).stateManager.goToState(LauncherState.NORMAL)
        }
    }

    fun showForEditMode(launcher: Launcher, duration: Long) {
        cancelVisibilityAnimation()
        bindOptions(launcher)
        if (duration <= 0) {
            applyVisibleState()
            return
        }
        visibility = VISIBLE
        alpha = 0f
        translationY = slideOffset()
        animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(duration)
            .setInterpolator(Interpolators.DECELERATE_2)
            .start()
    }

    /**
     * Rebuilds the shared entries. Runs on every show so a preference change (which can only
     * happen outside edit mode) and the current page are always reflected.
     */
    private fun bindOptions(launcher: Launcher) {
        itemsContainer.removeAllViews()
        // Setting a default page is only meaningful once there is more than one page group to
        // choose from; with a single page the entry would be a no-op.
        val hasMultiplePages = launcher.workspace.reorderablePageGroupStarts.size() > 1
        for (entry in OptionsPopupView.getOptionEntries(launcher)) {
            if (entry.identifier == "edit_mode") {
                continue
            }
            if (entry.identifier == "default_page" && !hasMultiplePages) {
                continue
            }
            itemsContainer.addView(createOptionView(entry))
        }
    }

    private fun createOptionView(entry: LauncherOptionEntry): View {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.edit_mode_action_bar_item, itemsContainer, false)
        val icon = view.findViewById<ImageView>(R.id.edit_mode_action_item_icon)
        val label = view.findViewById<TextView>(R.id.edit_mode_action_item_label)
        icon.setImageDrawable(entry.item.icon)
        icon.setColorFilter(Color.WHITE)
        label.text = entry.item.label
        view.contentDescription = entry.item.label
        view.setOnClickListener { v -> entry.item.clickListener.onLongClick(v) }
        return view
    }

    fun hideForEditMode(duration: Long) {
        cancelVisibilityAnimation()
        if (duration <= 0) {
            applyHiddenState()
            return
        }
        animate()
            .alpha(0f)
            .translationY(slideOffset())
            .setDuration(duration)
            .setInterpolator(Interpolators.ACCELERATE)
            .withEndAction { applyHiddenState() }
            .start()
    }

    private fun cancelVisibilityAnimation() {
        animate().cancel()
    }

    private fun slideOffset(): Float {
        if (height > 0) {
            return height.toFloat()
        }
        return resources.getDimension(R.dimen.edit_mode_page_strip_height)
    }

    private fun applyVisibleState() {
        visibility = VISIBLE
        alpha = 1f
        translationY = 0f
    }

    private fun applyHiddenState() {
        visibility = GONE
        alpha = 1f
        translationY = 0f
    }
}
