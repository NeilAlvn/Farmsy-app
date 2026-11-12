package app.farmsy.android.core

import android.content.Intent
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URLDecoder

/// A farm page address: /boerderijwinkels/<place>/<name>.
data class FarmPathRef(val place: String, val name: String)

/// What the Play Store says brought this install (spec A4). `farm` is an osm_id.
data class InstallReferrer(val campaign: String?, val medium: String?, val farm: String?)

/// Farm page links (F3) and the Play Install Referrer. Mirrors iOS FarmLink.swift;
/// the same segment rule as the web's isSegment.
object FarmLinks {
    val HOSTS = setOf("farmsy.app", "www.farmsy.app")

    /// Dutch has no prefix; /nl/... is a redirect on the site and never claimed.
    private val PREFIXED_LOCALES = setOf("en", "fr", "de", "es")
    private val SEGMENT = Regex("[a-z0-9]+(-[a-z0-9]+)*") // matches() = whole string, so no trailing-newline slack

    private fun isSegment(s: String) = s.length <= 80 && SEGMENT.matches(s)

    fun parsePath(path: String?): FarmPathRef? {
        var parts = path.orEmpty().split('/').filter { it.isNotEmpty() }
        if (parts.isNotEmpty() && parts[0] in PREFIXED_LOCALES) parts = parts.drop(1)
        if (parts.size != 3 || parts[0] != "boerderijwinkels" || !isSegment(parts[1]) || !isSegment(parts[2])) return null
        return FarmPathRef(parts[1], parts[2])
    }

    /// `utm_source=web&utm_medium=farm_page&utm_campaign=app_funnel&farm=node%2F123`.
    /// Play hands it over already decoded once; each value is decoded once more.
    /// A value that will not decode, or is blank or absurdly long, counts as absent.
    fun parseReferrer(raw: String?): InstallReferrer {
        val params = raw.orEmpty().split('&').mapNotNull { pair ->
            val i = pair.indexOf('=')
            if (i <= 0) null
            else pair.substring(0, i) to runCatching { URLDecoder.decode(pair.substring(i + 1), "UTF-8") }.getOrNull()
        }.toMap()
        fun value(key: String) = params[key]?.trim()?.takeIf { it.isNotEmpty() && it.length <= 200 }
        return InstallReferrer(campaign = value("utm_campaign"), medium = value("utm_medium"), farm = value("farm"))
    }

    /// A farm a link or the install referrer asked for. MainScreen opens it once
    /// its pin is loaded and clears it — the same hand-off as a push tap.
    data class Pending(val osmId: String, val askFollow: Boolean)
    private val _pending = MutableStateFlow<Pending?>(null)
    val pending: StateFlow<Pending?> = _pending.asStateFlow()
    fun consume() { _pending.value = null }

    /// The lookup in flight; a newer link cancels it so the latest tap always wins.
    private var lookup: Job? = null

    /// A VIEW intent for a farm page: ask the web which farm it is, then hand it
    /// to MainScreen. Anything else (a /join referral, a push tap) is ignored here.
    fun deliver(intent: Intent?, scope: CoroutineScope) {
        val uri = intent?.data ?: return
        if (uri.scheme != "https" || uri.host !in HOSTS) return
        val ref = parsePath(uri.path) ?: return
        val source = uri.getQueryParameter("utm_source")?.takeIf { it.isNotBlank() } ?: "link"
        lookup?.cancel()
        lookup = scope.launch {
            val osmId = osmIdForPath(ref) ?: return@launch
            Observability.capture(
                AnalyticsEvent.APP_OPENED_FROM_LINK,
                mapOf(AnalyticsProp.FARM to osmId, AnalyticsProp.SOURCE to source),
            )
            _pending.value = Pending(osmId, askFollow = false)
        }
    }

    @Serializable
    private data class ByPath(@SerialName("osm_id") val osmId: String)

    /// null on anything but a 200: offline, unknown farm, rate-limited. The app then
    /// stays where it is — opening the page in a browser would loop back into the app.
    private suspend fun osmIdForPath(ref: FarmPathRef): String? = try {
        withTimeout(10_000) {
            val resp = httpClient.get("${Backend.WEB_API}/farm/by-path") {
                parameter("place", ref.place)
                parameter("name", ref.name)
            }
            if (resp.status.value == 200) lenientJson.decodeFromString<ByPath>(resp.bodyAsText()).osmId else null
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        if (e is kotlinx.coroutines.TimeoutCancellationException) null else throw e
    } catch (e: Exception) {
        null
    }
}
