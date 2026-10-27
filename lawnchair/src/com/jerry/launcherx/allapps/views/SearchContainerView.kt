package com.jerry.launcherx.allapps.views

import android.content.Context
import android.util.AttributeSet
import com.jerry.launcherx.search.LauncherXSearchUiDelegate
import com.android.launcher3.allapps.LauncherAllAppsContainerView

class SearchContainerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LauncherAllAppsContainerView(context, attrs, defStyleAttr) {

    override fun createSearchUiDelegate() = LauncherXSearchUiDelegate(this)
}
