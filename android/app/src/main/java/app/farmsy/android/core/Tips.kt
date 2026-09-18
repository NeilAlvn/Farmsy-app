package app.farmsy.android.core

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

/// Grootmoeders tips, as GET /api/tips serves them: one card each on the
/// Discover tab, about a product, with its photograph and a list action.
/// The Android twin of iOS Tips.swift.
@Serializable
data class Tip(
    val slug: String,
    val kicker: LocalizedText,
    val title: LocalizedText,
    val body: LocalizedText,
    val ingredient: String? = null,
    val image: String = "",
)

@Serializable
private data class TipsPayload(val tips: List<Tip> = emptyList())

object Tips {
    private val _tips = MutableStateFlow<List<Tip>>(emptyList())
    val tips: StateFlow<List<Tip>> = _tips.asStateFlow()

    suspend fun loadIfNeeded() {
        if (_tips.value.isNotEmpty()) return
        val payload = runCatching {
            val resp = httpClient.get("${Backend.WEB_API}/tips")
            if (resp.status.value != 200) null
            else lenientJson.decodeFromString<TipsPayload>(resp.bodyAsText())
        }.getOrNull() ?: return
        _tips.value = payload.tips
    }
}
