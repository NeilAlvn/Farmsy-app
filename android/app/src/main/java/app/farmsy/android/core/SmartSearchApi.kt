package app.farmsy.android.core

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/// Natural-language search parsing, via the web's `POST /api/search/smart`.
/// Mirrors iOS SmartSearchAPI.swift. Reads a sentence and returns a structured
/// intent applied to the pins already held; the only network call is the parse.
/// Fails to empty (timeout/bad response = every field empty) → caller falls back
/// to the plain keyword search.
@Serializable
data class SmartSearchIntent(
    val products: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val locationTypes: List<String> = emptyList(),
    val methods: List<String> = emptyList(),
    val openNow: Boolean = false,
    val automaat: Boolean = false,
    val zelfpluk: Boolean = false,
    val verified: Boolean = false,
    val place: String? = null,
    val center: Center? = null,
    val nearMe: Boolean = false,
    val radiusKm: Double? = null,
    val summary: String? = null,
    /// Ranking weights, supplied server-side so every client ranks identically
    /// with no coordinates on the wire; null → SearchRanking.DEFAULT.
    val ranking: SearchRanking? = null,
) {
    @Serializable
    data class Center(val lat: Double, val lng: Double)

    val isEmpty: Boolean
        get() = products.isEmpty() && categories.isEmpty() &&
            locationTypes.isEmpty() && methods.isEmpty() &&
            !openNow && !automaat && !zelfpluk && !verified &&
            !nearMe && center == null && place.isNullOrEmpty()
}

/// Result-ranking weights (v1 defaults mirror the iOS fallback).
@Serializable
data class SearchRanking(
    val version: Int = 1,
    val distanceZeroKm: Double = 100.0,
    val distanceWeight: Double = 100.0,
    val openToday: Double = 20.0,
    val verified: Double = 15.0,
    val hasPhoto: Double = 10.0,
    val ratingFactor: Double = 2.0,
    val reviewEach: Double = 0.25,
    val reviewCap: Double = 20.0,
) {
    companion object { val DEFAULT = SearchRanking() }
}

object SmartSearchApi {
    suspend fun parse(query: String): SmartSearchIntent? {
        val q = query.trim()
        if (q.isEmpty()) return null
        return try {
            val body = buildJsonObject { put("query", q) }.toString()
            val resp = httpClient.post("${Backend.WEB_API}/search/smart") {
                header(HttpHeaders.ContentType, "application/json")
                setBody(body)
            }
            if (resp.status.value != 200) return null
            lenientJson.decodeFromString<SmartSearchIntent>(resp.bodyAsText())
        } catch (e: Exception) {
            null
        }
    }
}
