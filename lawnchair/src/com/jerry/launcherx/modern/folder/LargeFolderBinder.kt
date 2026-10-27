package com.jerry.launcherx.modern.folder

import android.content.Context
import android.view.View
import com.jerry.launcherx.modern.ModernDesktopManager
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.BubbleTextView
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherState
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.folder.FolderIcon
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.ItemInfoWithIcon
import com.android.launcher3.touch.ItemLongClickListener

object LargeFolderBinder {

    const val PREFS_NAME = "modern_folder_prefs"
    private const val KEY_LARGE_FOLDERS = "large_folders_enabled"
    const val KEY_SHOW_NAME = "large_folder_show_name"
    const val KEY_BG_OPACITY = "large_folder_bg_opacity"
    const val KEY_ICON_SCALE = "large_folder_icon_scale"
    const val KEY_BG_PADDING = "large_folder_bg_padding"
    const val KEY_BG_INSET = "large_folder_bg_inset"
    const val KEY_BG_COLOR = "large_folder_bg_color"
    const val KEY_FROSTED_GLASS = "large_folder_frosted_glass"
    const val KEY_LIQUID_GLASS = "large_folder_liquid_glass"
    const val KEY_GLASS_BLUR = "large_folder_glass_blur"
    const val KEY_ICON_GAP = "large_folder_icon_gap"
    const val KEY_ALIGN_H = "large_folder_align_h"
    const val KEY_ALIGN_V = "large_folder_align_v"
    const val KEY_MAX_SPAN = "large_folder_max_span"
    const val KEY_SWIPER_MODE = "large_folder_swiper_mode"

    const val SWIPER_OFF = "off"
    const val SWIPER_HORIZONTAL = "horizontal"
    const val SWIPER_VERTICAL = "vertical"

    private const val KEY_SWIPER_FOLDER_PREFIX = "large_folder_swiper_mode_folder_"

    const val DEFAULT_ICON_SCALE = 0.9f

    @JvmStatic
    fun isEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_LARGE_FOLDERS, true)
    }

    @JvmStatic
    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_LARGE_FOLDERS, enabled)
            .apply()
    }

    /** Whether the folder title is shown under an inline large folder (default on). */
    @JvmStatic
    fun isShowName(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SHOW_NAME, true)
    }

    @JvmStatic
    fun setShowName(context: Context, show: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SHOW_NAME, show)
            .apply()
    }

    /**
     * Largest workspace span allowed on either axis (default 3, settings allow 2..4).
     * The internal grid is derived from the span per the long-side `2n` rule, so
     * limiting the span bounds the capacity directly.
     */
    @JvmStatic
    fun getMaxSpan(context: Context): Int {
        return LargeFolderGrid.clampMaxSpan(
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_MAX_SPAN, LargeFolderGrid.DEFAULT_MAX_SPAN),
        )
    }

    /** Global swiper mode chosen in Settings → Large folder → Layout. */
    @JvmStatic
    fun getGlobalSwiperMode(context: Context): SwiperMode = SwiperMode.fromPref(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SWIPER_MODE, SWIPER_OFF),
    )

    @JvmStatic
    fun setGlobalSwiperMode(context: Context, mode: SwiperMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SWIPER_MODE, SwiperMode.toPref(mode))
            .apply()
    }

    /**
     * Effective swiper mode for one folder: an explicit per-folder override wins over the
     * global setting, so the long-press menu can change a single card without touching the rest.
     */
    @JvmStatic
    fun getSwiperMode(context: Context, folderId: Int): SwiperMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val override = if (folderId != ItemInfo.NO_ID) {
            prefs.getString(KEY_SWIPER_FOLDER_PREFIX + folderId, null)
        } else {
            null
        }
        return SwiperMode.fromPref(override ?: prefs.getString(KEY_SWIPER_MODE, SWIPER_OFF))
    }

    @JvmStatic
    fun setSwiperMode(context: Context, folderId: Int, mode: SwiperMode) {
        if (folderId == ItemInfo.NO_ID) return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SWIPER_FOLDER_PREFIX + folderId, SwiperMode.toPref(mode))
            .apply()
    }

    @JvmStatic
    fun clearSwiperModeOverride(context: Context, folderId: Int) {
        if (folderId == ItemInfo.NO_ID) return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_SWIPER_FOLDER_PREFIX + folderId)
            .apply()
    }

    @JvmStatic
    fun hasSwiperModeOverride(context: Context, folderId: Int): Boolean {
        if (folderId == ItemInfo.NO_ID) return false
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .contains(KEY_SWIPER_FOLDER_PREFIX + folderId)
    }

    @Volatile
    private var lastInlineLaunchItemId: Int = ItemInfo.NO_ID

    /**
     * Remembers the launched item when the launch came from an inline large-folder
     * cell (called from Launcher.startActivitySafely). The app-close animation uses
     * this to return the icon to its cell even when the same app is also on the
     * desktop or dock — launch cookies are not delivered back on gesture-nav close.
     */
    @JvmStatic
    fun setLastInlineLaunch(view: View?, item: ItemInfo?) {
        lastInlineLaunchItemId =
            if (item != null && isInsideInlineGrid(view)) item.id else ItemInfo.NO_ID
    }

    @JvmStatic
    fun getLastInlineLaunchItemId(): Int = lastInlineLaunchItemId

    /** Whether the given view sits inside a large folder's inline grid on the workspace. */
    @JvmStatic
    fun isInsideInlineGrid(view: View?): Boolean {
        var v: View? = view
        while (v != null) {
            if (v is LargeFolderView) return true
            v = v.parent as? View
        }
        return false
    }

    @JvmStatic
    fun bindInline(
        gridView: LargeFolderView,
        folderIcon: View,
        info: FolderInfo,
        spanX: Int,
        spanY: Int,
        itemClickListener: View.OnClickListener,
    ) {
        val context = gridView.context
        val modern = ModernDesktopManager.getInstance(context)
        val items = info.getContents().orEmpty()

        val folderItems = items.map { item ->
            val withIcon = item as? ItemInfoWithIcon
            val packageName = withIcon?.targetPackage
                ?: item.intent?.component?.packageName
                ?: item.intent?.`package`
                ?: ""
            LargeFolderView.FolderItem(
                id = item.id.toLong(),
                packageName = packageName,
                label = item.title?.toString() ?: packageName,
                icon = withIcon?.newIcon(context),
                category = modern.smartFolderManager.detectCategory(item),
                itemInfo = withIcon,
            )
        }

        gridView.setInlineMode(true)
        gridView.showItemLabels = false
        // Grid comes from the workspace span and screen orientation (long-side 2n
        // rule) and must be settled before the children are built so no transient
        // default grid is ever laid out.
        gridView.setSwiperMode(getSwiperMode(context, info.id))
        gridView.setSpan(spanX, spanY)
        gridView.setItems(folderItems)

        gridView.onItemClicked = { _, index ->
            if (index in items.indices) {
                val launcher = Launcher.getLauncher(context)
                // The actual icon view so launch animations (e.g. fake zoom) start from the icon
                // position, not the whole grid — and so edit mode knows which cell was tapped.
                val iconView = gridView.getChildForItemIndex(index) ?: gridView
                if (launcher.isInState(LauncherState.EDIT_MODE) && iconView is BubbleTextView) {
                    // Edit mode: the tapped app is the selection target, not the folder. The
                    // folder itself stays selectable through the card's empty area.
                    itemClickListener.onClick(iconView)
                } else if (launcher.isInState(LauncherState.EDIT_MODE)) {
                    itemClickListener.onClick(folderIcon)
                } else {
                    gridView.tag = items[index]
                    itemClickListener.onClick(iconView)
                }
            }
        }
        gridView.onOverflowClicked = {
            itemClickListener.onClick(folderIcon)
        }
        gridView.onItemLongClicked = { view, _, _ ->
            // Mirror the opened-folder behavior: long-press shows the app's own menu
            // (shortcuts + app info) via the pre-drag condition registered inside
            // Workspace.beginDragShared; moving past the drag threshold then lifts the
            // icon out of the folder (Folder.startDrag wires the drag lifecycle so the
            // item is removed from / restored to the folder content around the drag).
            val bubble = view as? BubbleTextView
            val folder = (folderIcon as? FolderIcon)?.folder
            if (bubble != null && folder != null) {
                folder.startDrag(bubble, DragOptions())
            }
            true
        }
        gridView.onOverflowLongClicked = { folderIcon.performLongClick() }
        gridView.onEmptyAreaLongPress = { folderIcon.performLongClick() }
        gridView.onEmptyAreaDragStart = {
            // The same gesture continues right after the long-press showed the handles:
            // dismiss the handles and start dragging the folder on the workspace directly.
            val launcher = Launcher.getLauncher(context)
            AbstractFloatingView.closeAllOpenViews(launcher)
            ItemLongClickListener.beginDrag(folderIcon, launcher, info, DragOptions())
        }
    }
}
