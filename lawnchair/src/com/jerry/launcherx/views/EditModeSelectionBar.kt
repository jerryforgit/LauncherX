package com.jerry.launcherx.views

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.widget.LinearLayout
import android.widget.TextView
import com.android.app.animation.Interpolators
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherSettings
import com.android.launcher3.R
import com.android.launcher3.folder.Folder
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.WorkspaceItemInfo

/**
 * Bottom action bar shown while the launcher is in
 * [com.android.launcher3.LauncherState.EDIT_MODE] and at least one item is selected.
 *
 * It replaces [EditModeActionBar] for the duration of a multi-select session and offers the batch
 * operations (grouping into a folder and removing) plus a way to cancel the selection.
 */
class EditModeSelectionBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val countView: TextView
    private val newFolderView: View
    private val newFolderLabel: TextView

    private var selection: EditModeSelection? = null
    private val selectionListener = EditModeSelection.Listener { updateSelectionState() }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        LayoutInflater.from(context).inflate(R.layout.edit_mode_selection_bar, this, true)

        countView = findViewById(R.id.edit_mode_selection_count)
        newFolderView = findViewById(R.id.edit_mode_selection_new_folder)
        newFolderLabel = findViewById(R.id.edit_mode_selection_new_folder_label)

        newFolderView.setOnClickListener { v ->
            Launcher.getLauncher(v.context).createFolderFromSelection()
        }
        findViewById<View>(R.id.edit_mode_selection_remove).setOnClickListener { v ->
            val launcher = Launcher.getLauncher(v.context)
            val items = launcher.editModeSelection.items()
            launcher.editModeSelection.clear()
            launcher.dropTargetHandler.removeItems(items)
        }
        findViewById<View>(R.id.edit_mode_selection_cancel).setOnClickListener { v ->
            Launcher.getLauncher(v.context).editModeSelection.clear()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val launcher = Launcher.getLauncher(context)
        selection = launcher.editModeSelection
        selection?.addListener(selectionListener)
        updateSelectionState()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        selection?.removeListener(selectionListener)
        selection = null
    }

    private fun updateSelectionState() {
        val items = selection?.items() ?: emptyList()
        countView.text = context.getString(R.string.edit_mode_selection_count, items.size)
        // Grouping only makes sense for top-level desktop items: an app that already lives in a
        // folder (e.g. a cell of a large folder) cannot be moved into another one.
        val desktopItems = items.filter {
            it.container == LauncherSettings.Favorites.CONTAINER_DESKTOP
        }
        // "Group" is meaningful for two loose apps (create a folder), or any selection that
        // contains at least one folder plus something else (add apps / merge folders).
        val folders = desktopItems.count { it is FolderInfo }
        val apps = desktopItems.count { it is WorkspaceItemInfo && Folder.willAccept(it) }
        val canGroup = if (folders == 0) apps >= 2 else folders + apps >= 2
        newFolderView.visibility = if (canGroup) VISIBLE else INVISIBLE
        newFolderLabel.setText(
            when {
                folders == 0 -> R.string.edit_mode_selection_new_folder
                folders >= 2 -> R.string.edit_mode_selection_merge_folders
                else -> R.string.edit_mode_selection_add_to_folder
            },
        )
    }

    fun showForEditMode(launcher: Launcher, duration: Long) {
        animate().cancel()
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

    fun hideForEditMode(duration: Long) {
        animate().cancel()
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
