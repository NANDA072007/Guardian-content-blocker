package com.guardian.app.walls.wall2.monitor

import android.view.accessibility.AccessibilityNodeInfo
import com.guardian.app.walls.wall2.adapter.SearchEngineAdapter
import com.guardian.app.walls.wall2.adapter.GoogleSearchAdapter
import com.guardian.app.walls.wall2.event.*
import dagger.hilt.android.scopes.ServiceScoped
import javax.inject.Inject

@ServiceScoped
class SearchMonitor @Inject constructor() {

    private val substringBlockedWords = setOf(
        "porn", "sex", "xxx", "nude", "nsfw", "hentai",
        "pornhub", "xvideos", "xnxx", "xhamster"
    )

    private val adapters: List<SearchEngineAdapter> = listOf(
        GoogleSearchAdapter()
    )

    fun process(rootNode: AccessibilityNodeInfo): ProtectionEvent? {
        for (adapter in adapters) {
            if (!adapter.isSearchResultPage(rootNode)) continue

            val query = adapter.getSearchQuery(rootNode) ?: continue
            val lowerquery = query.lowercase()

            for (word in substringBlockedWords) {
                if (lowerquery.contains(word)) {
                    return SearchEvent(
                        sessionId = java.util.UUID.randomUUID().toString(),
                        metadata = SearchMetadata(
                            query = query,
                            engine = adapter.getEngineName()
                        )
                    )
                }
            }
        }
        return null
    }
}
