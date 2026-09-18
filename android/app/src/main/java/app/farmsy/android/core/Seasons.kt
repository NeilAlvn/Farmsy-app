package app.farmsy.android.core

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import java.util.Calendar

// The seasonal calendar, as GET /api/seasons serves it — the Android twin of
// iOS Seasons.swift.
//
// Served, not bundled: the table is hand-reviewed on the web and corrected
// there. The whole year comes down once and the month is picked here, so a
// month boundary needs no fetch. Until the endpoint is deployed the load
// fails quietly and the seasonal sections simply do not render.

@Serializable
data class SeasonalItem(
    val slug: String,
    val nl: String,
    val en: String,
    val months: List<Int> = emptyList(),
    val peak: List<Int> = emptyList(),
    val category: String = "",
    /// Slug of the bundled tile photograph. Older servers omit it; most seasonal
    /// slugs are their own file, the aliased ones (aardbei → strawberry) show
    /// the emoji until the server sends the mapping.
    val image: String? = null,
    val storage: String? = null,
    val note: String? = null,
    /// Every word that means this, for matching a farm's produce text.
    val terms: List<String> = emptyList(),
    /// Available this month and not all year round.
    val now: Boolean = false,
) {
    fun label(language: String): String = if (language == "nl") nl else en

    val imageSlug: String get() = image ?: slug

    val emoji: String
        get() = when (category) {
            "dairy" -> "🥛"; "eggs" -> "🥚"; "meat" -> "🥩"; "honey" -> "🍯"
            "cheese" -> "🧀"; "fish" -> "🐟"
            else -> "🌱"
        }

    fun isPeak(month: Int): Boolean = month in peak
}

/// Copy in the four app languages, picked by the app's language.
@Serializable
data class LocalizedText(val nl: String = "", val en: String = "", val fr: String = "", val de: String = "") {
    fun text(language: String): String = when (language) {
        "nl" -> nl; "fr" -> fr; "de" -> de
        else -> en
    }
}

/// What to make with what is in season: one card, tied to the products it
/// needs so "put it on my list" is one tap.
@Serializable
data class SeasonIdea(
    val slug: String,
    val month: Int,
    val title: LocalizedText,
    val body: LocalizedText,
    /// Shopping ids or seasonal slugs.
    val ingredients: List<String> = emptyList(),
    val image: String = "",
)

@Serializable
private data class SeasonsPayload(
    val month: Int,
    val items: List<SeasonalItem> = emptyList(),
    val ideas: List<SeasonIdea>? = null,
)

object Seasons {
    private val _month = MutableStateFlow(Calendar.getInstance().get(Calendar.MONTH) + 1)
    val month: StateFlow<Int> = _month.asStateFlow()

    private val _items = MutableStateFlow<List<SeasonalItem>>(emptyList())
    val items: StateFlow<List<SeasonalItem>> = _items.asStateFlow()

    private val _ideas = MutableStateFlow<List<SeasonIdea>>(emptyList())
    val ideas: StateFlow<List<SeasonIdea>> = _ideas.asStateFlow()

    private var loaded = false

    /// Everything in season in a month, peak first. Unlike `thisMonth` this
    /// keeps the year-round staples (eggs, cheese) out, so a month page is
    /// about what changed.
    fun items(month: Int, items: List<SeasonalItem> = _items.value): List<SeasonalItem> =
        items.filter { month in it.months && it.months.size < 12 }
            .sortedWith(compareByDescending<SeasonalItem> { it.isPeak(month) }.thenBy { it.slug })

    fun ideas(month: Int, ideas: List<SeasonIdea> = _ideas.value): List<SeasonIdea> = ideas.filter { it.month == month }

    /// What is news this month: at peak first, then the rest that is in season.
    fun thisMonth(items: List<SeasonalItem> = _items.value, month: Int = _month.value): List<SeasonalItem> =
        items.filter { it.now }.sortedWith(compareByDescending<SeasonalItem> { it.isPeak(month) }.thenBy { it.slug })

    suspend fun loadIfNeeded() {
        if (loaded) return
        val payload = runCatching {
            val resp = httpClient.get("${Backend.WEB_API}/seasons")
            if (resp.status.value != 200) null
            else lenientJson.decodeFromString<SeasonsPayload>(resp.bodyAsText())
        }.getOrNull() ?: return
        _items.value = payload.items
        _ideas.value = payload.ideas ?: emptyList()
        _month.value = payload.month
        loaded = true
    }
}
