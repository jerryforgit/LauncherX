package com.jerry.launcherx.search

import android.content.SearchRecentSuggestionsProvider
import com.android.launcher3.BuildConfig

class LauncherXRecentSuggestionProvider : SearchRecentSuggestionsProvider() {
    companion object {
        const val AUTHORITY = BuildConfig.APPLICATION_ID + ".search.LauncherXRecentSuggestionProvider"
        const val MODE = DATABASE_MODE_QUERIES
    }

    init {
        setupSuggestions(AUTHORITY, MODE)
    }
}
