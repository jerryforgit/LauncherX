package com.jerry.launcherx.views

import com.android.launcher3.model.data.ItemInfo

/**
 * Tracks the workspace items that are currently selected in the home-screen edit mode.
 *
 * The launcher owns a single instance (see [com.android.launcher3.Launcher.getEditModeSelection])
 * so that the icon views, the click handler and the selection action bar can all observe the same
 * state. Selection is only meaningful while the launcher is in
 * [com.android.launcher3.LauncherState.EDIT_MODE] and is cleared when leaving that state.
 */
class EditModeSelection {

    fun interface Listener {
        fun onSelectionChanged()
    }

    private val selected = LinkedHashMap<Long, ItemInfo>()
    private val listeners = mutableListOf<Listener>()

    val size: Int
        get() = selected.size

    val isActive: Boolean
        get() = selected.isNotEmpty()

    fun addListener(listener: Listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    fun isSelected(item: ItemInfo): Boolean = selected.containsKey(itemKey(item))

    /** Adds the item to the selection if absent, otherwise removes it. Returns whether it is now selected. */
    fun toggle(item: ItemInfo): Boolean {
        val key = itemKey(item)
        val nowSelected = if (selected.containsKey(key)) {
            selected.remove(key)
            false
        } else {
            selected[key] = item
            true
        }
        notifyListeners()
        return nowSelected
    }

    /** Removes the item from the selection without toggling. Returns whether it was selected. */
    fun remove(item: ItemInfo): Boolean {
        if (selected.remove(itemKey(item)) == null) {
            return false
        }
        notifyListeners()
        return true
    }

    fun clear() {
        if (selected.isEmpty()) {
            return
        }
        selected.clear()
        notifyListeners()
    }

    fun items(): List<ItemInfo> = selected.values.toList()

    private fun notifyListeners() {
        listeners.toList().forEach { it.onSelectionChanged() }
    }

    private fun itemKey(item: ItemInfo): Long {
        return if (item.id != ItemInfo.NO_ID) item.id.toLong() else System.identityHashCode(item).toLong()
    }
}
