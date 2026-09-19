package app.farmsy.android.core

import android.content.Context
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The product profiles, as GET /api/products?lang= serves them: the year bar,
// how to choose, keep and preserve, five ideas, grandmother's tips, pairings.
// Researched with sources and read by a person before they ship; the app only
// shows them. One language per fetch, the app's. The Android twin of iOS
// Products.swift.

enum class MonthState { NONE, AVAILABLE, PEAK }

@Serializable
data class ProductProfile(
    val slug: String,
    val image: String = "",
    val shopping: String? = null,
    val seasonal: String? = null,
    val name: String = "",
    /// Twelve of `none | available | peak`, January first.
    val months: List<String> = emptyList(),
    @SerialName("region_note") val regionNote: String = "",
    @SerialName("greenhouse_note") val greenhouseNote: String = "",
    val choose: List<String> = emptyList(),
    val store: Store = Store(),
    val preserve: List<Preserve> = emptyList(),
    val ideas: List<Idea> = emptyList(),
    val tips: List<String> = emptyList(),
    val pairs: List<String> = emptyList(),
    @SerialName("fun_fact") val funFact: String = "",
) {
    @Serializable
    data class Store(@SerialName("where") val place: String = "", val how: String = "", val days: Int = 0)

    @Serializable
    data class Preserve(val method: String = "", val how: String = "")

    @Serializable
    data class Idea(
        val title: String = "",
        val body: String = "",
        /// Shopping ids or seasonal slugs.
        val ingredients: List<String> = emptyList(),
        val image: String = "",
    )

    fun state(month: Int): MonthState = when (months.getOrNull(month - 1)) {
        "peak" -> MonthState.PEAK
        "available" -> MonthState.AVAILABLE
        else -> MonthState.NONE
    }
}

@Serializable
private data class ProductsPayload(val products: List<ProductProfile> = emptyList())

object Products {
    private val _all = MutableStateFlow<List<ProductProfile>>(emptyList())
    val all: StateFlow<List<ProductProfile>> = _all.asStateFlow()

    /// True once a fetch has finished, failed or not: a failed fetch still ends
    /// the skeleton, and the sheet says there is no profile yet.
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private var loadedLang: String? = null
    private val lock = Mutex()

    /// The profile for a shopping id or a seasonal slug, whichever the caller has.
    fun profile(slug: String): ProductProfile? =
        _all.value.firstOrNull { it.slug == slug || it.shopping == slug || it.seasonal == slug }

    /// The app's language, narrowed to the four the endpoint serves.
    fun lang(context: Context): String =
        ShoppingItems.language(context).takeIf { it in listOf("nl", "en", "fr", "de") } ?: "en"

    fun decode(text: String): List<ProductProfile> = lenientJson.decodeFromString<ProductsPayload>(text).products

    /// Fetched once per language; a language switch (the activity recreates,
    /// the process does not) fetches again.
    suspend fun loadIfNeeded(lang: String) {
        if (loadedLang == lang) return
        lock.withLock {
            if (loadedLang == lang) return
            val decoded = runCatching {
                val resp = httpClient.get("${Backend.WEB_API}/products?lang=$lang")
                if (resp.status.value != 200) null else decode(resp.bodyAsText())
            }.getOrNull()
            _all.value = decoded ?: emptyList()
            _loaded.value = true
            loadedLang = lang
        }
    }
}
