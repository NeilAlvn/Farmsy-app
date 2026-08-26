package app.farmsy.android.core

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray
import java.net.URLEncoder

sealed class FarmDetailException : Exception() {
    class Locked : FarmDetailException()   // 401/403 — no active subscription
    class NotFound : FarmDetailException()
    class Other : FarmDetailException()
}

/// Full farm detail comes only from the farmsy.app API, which verifies the
/// caller's subscription server-side. The public pins RPC never contains
/// these fields, so there is nothing to bypass on-device.
object FarmDetailApi {

    /// `/api/farm/[osmId]` is a single dynamic segment, but ~35% of osm_ids
    /// contain a slash (`node/123`, `amsterdam_urban/…`). A raw slash is read as a
    /// path separator and breaks the route — the API then answers empty/404, which
    /// reads as "descriptions don't load". Percent-encode the id (slashes → %2F),
    /// exactly like iOS FarmDetailAPI.farmURL. URLEncoder form-encodes (slash →
    /// %2F); fix its `+`-for-space back to %20 so the segment stays valid.
    private fun encodeOsmId(osmId: String): String =
        URLEncoder.encode(osmId, "UTF-8").replace("+", "%20")

    suspend fun fetch(osmId: String, accessToken: String): FarmDetail {
        val resp = httpClient.get("${Backend.WEB_API}/farm/${encodeOsmId(osmId)}") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        return when (resp.status.value) {
            200 -> lenientJson.decodeFromString<FarmDetail>(resp.bodyAsText())
            401, 403 -> throw FarmDetailException.Locked()
            404 -> throw FarmDetailException.NotFound()
            else -> throw FarmDetailException.Other()
        }
    }

    /// The opening of a farm's description, for someone without a membership.
    /// Public on purpose — a farm that has written about itself gets to say its
    /// first sentence to every visitor, which is the reason to unlock the rest.
    /// Best-effort: any failure (or empty text) yields null and the card simply
    /// shows no teaser. Mirrors iOS FarmDetailAPI.teaser.
    suspend fun teaser(osmId: String): FarmTeaser? {
        return runCatching {
            val resp = httpClient.get("${Backend.WEB_API}/farm/${encodeOsmId(osmId)}/teaser")
            if (resp.status.value != 200) return null
            val t = lenientJson.decodeFromString<FarmTeaser>(resp.bodyAsText())
            t.takeIf { it.text.isNotEmpty() }
        }.getOrNull()
    }

    @Serializable
    private data class TeasersResponse(val teasers: Map<String, FarmTeaser> = emptyMap())

    /// Teasers for many farms in one request — for rows/shelves, so a list doesn't
    /// spend a round trip per tile. POST (osm_ids go in the JSON body, sidestepping
    /// the `%2F` slash problem); the server caps the batch at 60. Every id sent
    /// comes back as a key; absent-or-empty both mean "nothing to show", so drop
    /// blanks and return only the non-empty ones (id → text). Mirrors iOS.
    suspend fun teasers(osmIds: List<String>): Map<String, String> {
        if (osmIds.isEmpty()) return emptyMap()
        return runCatching {
            val body = buildJsonObject {
                putJsonArray("osmIds") { osmIds.take(60).forEach { add(it) } }
            }.toString()
            val resp = httpClient.post("${Backend.WEB_API}/farms/teasers") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            if (resp.status.value != 200) return emptyMap()
            lenientJson.decodeFromString<TeasersResponse>(resp.bodyAsText())
                .teasers
                .mapNotNull { (id, t) -> t.text.takeIf { it.isNotEmpty() }?.let { id to it } }
                .toMap()
        }.getOrDefault(emptyMap())
    }
}
