package com.jerry.launcherx.allapps.views

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import com.jerry.launcherx.search.adapter.SearchTargetCompat
import com.jerry.launcherx.ui.preferences.PreferenceActivity
import com.jerry.launcherx.ui.preferences.destinations.SearchRoute
import com.jerry.launcherx.ui.preferences.navigation.Search
import com.android.launcher3.R

class SearchResultSearchSettings(context: Context, attrs: AttributeSet?) :
    LinearLayout(context, attrs),
    SearchResultView {

    private lateinit var iconButton: ImageButton

    override fun onFinishInflate() {
        super.onFinishInflate()
        iconButton = ViewCompat.requireViewById(this, R.id.search_settings)
        iconButton.setOnClickListener {
            context.startActivity(PreferenceActivity.createIntent(context, Search(SearchRoute.DRAWER_SEARCH)))
        }
    }

    override val isQuickLaunch: Boolean = false
    override fun launch(): Boolean = false

    override fun bind(
        target: SearchTargetCompat,
        shortcuts: List<SearchTargetCompat>,
    ) {
        // no-op
    }
}
