package app.farmsy.android.core

import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

sealed class SubmissionException : Exception() {
    class NotSignedIn : SubmissionException()
    class MembersOnly : SubmissionException()
    class Server(val serverMessage: String?) : SubmissionException()
}

/// Farm submissions and ownership claims go through the farmsy.app API —
/// the same service-role write path the website uses. Mirrors SubmissionAPI.swift.
object SubmissionApi {

    data class FarmSubmission(
        var name: String = "",
        var city: String = "",
        var description: String = "",
        var farmType: List<String> = emptyList(),
        var address: String = "",
        var postalCode: String = "",
        var country: String = "Netherlands",
        var phone: String = "",
        var website: String = "",
        var email: String = "",
        var openingHours: String = "",
        var lat: Double? = null,
        var lng: Double? = null,
        var imageData: List<ByteArray> = emptyList(),
    )

    suspend fun submitFarm(s: FarmSubmission, accessToken: String) {
        val resp = httpClient.post("${Backend.WEB_API}/farms/submit") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        fun field(name: String, value: String) {
                            if (value.isNotEmpty()) append(name, value)
                        }
                        field("name", s.name)
                        field("city", s.city)
                        field("description", s.description)
                        field("farm_type", s.farmType.joinToString(","))
                        field("address", s.address)
                        field("postal_code", s.postalCode)
                        field("country", s.country)
                        field("phone", s.phone)
                        field("website", s.website)
                        field("email", s.email)
                        field("opening_hours", s.openingHours)
                        s.lat?.let { field("lat", it.toString()) }
                        s.lng?.let { field("lng", it.toString()) }
                        s.imageData.take(5).forEachIndexed { i, bytes ->
                            append(
                                "images", bytes,
                                Headers.build {
                                    append(HttpHeaders.ContentType, "image/jpeg")
                                    append(
                                        HttpHeaders.ContentDisposition,
                                        "filename=\"photo-$i.jpg\""
                                    )
                                }
                            )
                        }
                    }
                )
            )
        }
        check(resp.status.value, resp.bodyAsText())
    }

    @Serializable
    data class FarmClaim(
        val farmOsmId: String,
        val farmName: String,
        val fullName: String,
        val email: String,
        val phone: String,
        val verificationMethod: String, // "email" | "kvk"
        val kvkNumber: String? = null,
        val message: String? = null,
    )

    suspend fun submitClaim(claim: FarmClaim, accessToken: String) {
        val resp = httpClient.post("${Backend.WEB_API}/farms/claim") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody(lenientJson.encodeToString(FarmClaim.serializer(), claim))
        }
        check(resp.status.value, resp.bodyAsText())
    }

    @Serializable
    private data class ErrorBody(
        val error: String? = null,
        /// Stable machine-readable reason from the server. Prefer this over the
        /// status code, and never over the message copy — that changes.
        val code: String? = null,
    )

    private fun check(status: Int, body: String) {
        if (status == 200 || status == 201) return

        val parsed = runCatching { lenientJson.decodeFromString<ErrorBody>(body) }.getOrNull()
        when (parsed?.code) {
            "unauthenticated" -> throw SubmissionException.NotSignedIn()
            "no_subscription" -> throw SubmissionException.MembersOnly()
            "invalid", "failed" -> throw SubmissionException.Server(parsed.error)
            else -> when (status) {
                // Older deploys don't send `code` — fall back to the status.
                401 -> throw SubmissionException.NotSignedIn()
                403 -> throw SubmissionException.MembersOnly()
                else -> throw SubmissionException.Server(parsed?.error)
            }
        }
    }
}
