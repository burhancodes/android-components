/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.feature.awesomebar.provider

import mozilla.components.concept.awesomebar.AwesomeBar
import mozilla.components.concept.storage.HistoryStorage
import mozilla.components.concept.storage.SearchResult
import mozilla.components.feature.session.SessionUseCases
import java.util.UUID

private const val HISTORY_SUGGESTION_LIMIT = 20

/**
 * A [AwesomeBar.SuggestionProvider] implementation that provides suggestions based on the browsing
 * history stored in the [HistoryStorage].
 */
class HistoryStorageSuggestionProvider(
    private val historyStorage: HistoryStorage,
    private val loadUrlUseCase: SessionUseCases.LoadUrlUseCase
) : AwesomeBar.SuggestionProvider {

    override val id: String = UUID.randomUUID().toString()

    override suspend fun onInputChanged(text: String): List<AwesomeBar.Suggestion> {
        if (text.isEmpty()) {
            return emptyList()
        }
        return historyStorage.getSuggestions(text, HISTORY_SUGGESTION_LIMIT).into()
    }

    override val shouldClearSuggestions: Boolean
        // We do not want the suggestion of this provider to disappear and re-appear when text changes.
        get() = false

    private fun Iterable<SearchResult>.into(): List<AwesomeBar.Suggestion> {
        return this.map {
            AwesomeBar.Suggestion(
                provider = this@HistoryStorageSuggestionProvider,
                id = it.id,
                title = it.title,
                description = it.url,
                score = it.score,
                onSuggestionClicked = { loadUrlUseCase.invoke(it.url) }
            )
        }
    }
}
