package com.jerry.launcherx.hotseat

import android.view.View
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.jerry.launcherx.preferences2.PreferenceManager2
import com.android.launcher3.R
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Keeps the dock in sync with the actual stored "search bar widget" mode.
 *
 * [PreferenceManager2.getCachedPreferences] is a snapshot that may still hold the previous
 * value right after a settings change triggers an activity restart, so reading it in
 * [com.android.launcher3.Hotseat]'s constructor can leave the QSB visible even though the
 * user turned it off (or vice versa). Collecting the preference flows emits the current
 * stored value as soon as the collector starts, which corrects the dock.
 */
fun View.observeQsbDisabled(onChanged: java.util.function.Consumer<Boolean>) {
    val owner = findViewTreeLifecycleOwner() ?: return
    val prefs = PreferenceManager2.getInstance(context)
    owner.lifecycleScope.launch {
        combine(prefs.isHotseatEnabled.get(), prefs.hotseatMode.get()) { enabled, mode ->
            !enabled || mode.layoutResourceId == R.layout.empty_view
        }
            .distinctUntilChanged()
            .collect { onChanged.accept(it) }
    }
}
