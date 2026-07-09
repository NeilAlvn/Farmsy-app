package app.farmsy.android.core

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders

sealed class FarmDetailException : Exception() {
    class Locked : FarmDetailException()   // 401/403 — no active subscription
    class NotFound : FarmDetailException()
    class Other : FarmDetailException()
}

/// Full farm detail comes only from the farmsy.app API, which verifies the
/// caller's subscription server-side. The public pins RPC never contains
/// these fields, so there is nothing to bypass on-device.
object FarmDetailApi {
    suspend fun fetch(osmId: String, accessToken: String): FarmDetail {
        val resp = httpClient.get("${Backend.WEB_API}/farm/$osmId") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        return when (resp.status.value) {
            200 -> lenientJson.decodeFromString<FarmDetail>(resp.bodyAsText())
            401, 403 -> throw FarmDetailException.Locked()
            404 -> throw FarmDetailException.NotFound()
            else -> throw FarmDetailException.Other()
        }
    }
}
