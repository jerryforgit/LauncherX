package com.jerry.launcherx.search.algorithms

import android.content.Context
import com.jerry.launcherx.preferences.PreferenceManager
import com.jerry.launcherx.preferences2.PreferenceManager2
import com.jerry.launcherx.preferences2.firstCached
import com.jerry.launcherx.search.adapter.SearchLinksTarget
import com.jerry.launcherx.search.adapter.SearchTargetCompat
import com.jerry.launcherx.search.adapter.SearchTargetFactory
import com.jerry.launcherx.search.algorithms.engine.ActionsSectionBuilder
import com.jerry.launcherx.search.algorithms.engine.AppsAndShortcutsSectionBuilder
import com.jerry.launcherx.search.algorithms.engine.CalculationSectionBuilder
import com.jerry.launcherx.search.algorithms.engine.ContactsSectionBuilder
import com.jerry.launcherx.search.algorithms.engine.EmptyStateSectionBuilder
import com.jerry.launcherx.search.algorithms.engine.FilesSectionBuilder
import com.jerry.launcherx.search.algorithms.engine.HistorySectionBuilder
import com.jerry.launcherx.search.algorithms.engine.SearchProvider
import com.jerry.launcherx.search.algorithms.engine.SearchResult
import com.jerry.launcherx.search.algorithms.engine.SearchSettingsSectionBuilder
import com.jerry.launcherx.search.algorithms.engine.SectionBuilder
import com.jerry.launcherx.search.algorithms.engine.SettingsSectionBuilder
import com.jerry.launcherx.search.algorithms.engine.WebSuggestionsSectionBuilder
import com.jerry.launcherx.search.algorithms.engine.provider.CalculatorSearchProvider
import com.jerry.launcherx.search.algorithms.engine.provider.ContactsSearchProvider
import com.jerry.launcherx.search.algorithms.engine.provider.FileSearchProvider
import com.jerry.launcherx.search.algorithms.engine.provider.HistorySearchProvider
import com.jerry.launcherx.search.algorithms.engine.provider.SettingsSearchProvider
import com.jerry.launcherx.search.algorithms.engine.provider.ShortcutSearchProvider
import com.jerry.launcherx.search.algorithms.engine.provider.apps.AppSearchProvider
import com.jerry.launcherx.search.algorithms.engine.provider.web.CustomWebSearchProvider
import com.jerry.launcherx.search.algorithms.engine.provider.web.WebSuggestionProvider
import com.android.launcher3.LauncherAppState
import com.android.launcher3.R
import com.android.launcher3.allapps.BaseAllAppsAdapter
import com.android.launcher3.search.SearchCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LauncherXLocalSearchAlgorithm(context: Context) : LauncherXSearchAlgorithm(context) {

    private val appState = LauncherAppState.getInstance(context)

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentJob: Job? = null

    private val appSearchProvider = AppSearchProvider
    private val shortcutSearchProvider = ShortcutSearchProvider
    private val historySearchProvider = HistorySearchProvider

    private val searchProviders: List<SearchProvider> = listOf(
        SettingsSearchProvider,
        FileSearchProvider,
        ContactsSearchProvider,
        WebSuggestionProvider,
    )

    override fun doSearch(query: String, callback: SearchCallback<BaseAllAppsAdapter.AdapterItem>) {
        appState.model.enqueueModelUpdateTask { _, _, apps ->
            val appResults = appSearchProvider.search(context, query, apps)
            val shortcutResults = shortcutSearchProvider.search(context, appResults)

            currentJob?.cancel()
            currentJob = coroutineScope.launch {
                val nonAppProvidersFlow = combine(
                    searchProviders.map { it.search(context, query) },
                ) { resultsArray ->
                    resultsArray.toList().flatten()
                }

                nonAppProvidersFlow.collect { nonAppResults ->
                    val calcResult = CalculatorSearchProvider.search(context, query)
                        .firstOrNull()

                    val allResults = appResults + shortcutResults + (calcResult ?: emptyList()) + nonAppResults + generateActionResults(query)

                    val searchTargets = translateToSearchTargets(allResults)
                    setFirstItemQuickLaunch(searchTargets)
                    val adapterItems = transformSearchResults(searchTargets)
                    withContext(Dispatchers.Main) {
                        callback.onSearchResult(query, ArrayList(adapterItems))
                    }
                }
            }
        }
    }

    override fun doZeroStateSearch(callback: SearchCallback<BaseAllAppsAdapter.AdapterItem>) {
        currentJob?.cancel()

        val prefs = PreferenceManager.getInstance(context)
        val historyEnabled = prefs.searchResulRecentSuggestion.get()

        if (!historyEnabled) {
            callback.clearSearchResult()
        } else {
            currentJob = coroutineScope.launch {
                val prefs2 = PreferenceManager2.getInstance(context)
                val maxHistory = prefs2.maxRecentResultCount.firstCached()

                val historyResults = historySearchProvider.getRecentKeywords(context, maxHistory)

                val resultsToTranslate = if (historyResults.isNotEmpty()) {
                    historyResults + listOf(SearchResult.Action.SearchSettings)
                } else {
                    listOf(
                        SearchResult.Action.EmptyState(
                            titleRes = R.string.search_empty_state_title,
                            subtitleRes = R.string.search_empty_state_no_history_subtitle,
                        ),
                        SearchResult.Action.SearchSettings,
                    )
                }

                val searchTargets = translateToSearchTargets(resultsToTranslate)
                val adapterItems = transformSearchResults(searchTargets)
                withContext(Dispatchers.Main) {
                    callback.onSearchResult("", ArrayList(adapterItems))
                }
            }
        }
    }

    override fun cancel(interruptActiveRequests: Boolean) {
        currentJob?.cancel()
    }

    private fun generateActionResults(query: String): List<SearchResult.Action> {
        val actions = mutableListOf<SearchResult.Action>()
        val prefs = PreferenceManager.getInstance(context)
        val prefs2 = PreferenceManager2.getInstance(context)

        if (prefs.searchResultStartPageSuggestion.get()) {
            val provider = prefs2.webSuggestionProvider.firstCached()
            val webProvider = provider.configure(context)

            val providerName = if (webProvider is CustomWebSearchProvider) {
                webProvider.getDisplayName()
            } else {
                context.getString(webProvider.label)
            }

            actions.add(
                SearchResult.Action.WebSearch(
                    query = query,
                    providerName = providerName,
                    searchUrl = webProvider.getSearchUrl(query),
                    providerIconRes = webProvider.iconRes,
                    tintIcon = webProvider is CustomWebSearchProvider,
                ),
            )
        }

        if (SearchLinksTarget.resolveMarketSearchActivity(context) != null) {
            actions.add(SearchResult.Action.MarketSearch(query = query))
        }

        actions.add(SearchResult.Action.SearchSettings)

        return actions
    }

    private val sectionBuilders: List<SectionBuilder> = listOf(
        AppsAndShortcutsSectionBuilder,
        CalculationSectionBuilder,
        WebSuggestionsSectionBuilder,
        ContactsSectionBuilder,
        FilesSectionBuilder,
        SettingsSectionBuilder,
        HistorySectionBuilder,
        ActionsSectionBuilder,
        EmptyStateSectionBuilder,
        SearchSettingsSectionBuilder,
    )

    private fun translateToSearchTargets(
        results: List<SearchResult>,
    ): List<SearchTargetCompat> {
        val factory = SearchTargetFactory(context)

        // The new function is just a flatMap. It's declarative and beautiful.
        return sectionBuilders.flatMap { builder ->
            builder.build(context, factory, results)
        }
    }
}
