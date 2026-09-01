package app.farmsy.android.core

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/// The "why-farm" survey — a 1:1 port of iOS `SurveyAPI`/`SurveyView`. Questions come
/// from the server (localized, cached an hour) so a reworded question changes on every
/// phone at once without an app update. Three question kinds only: `one`
/// (single-select), `many` (multi-select, optionally capped by `max`), `text`
/// (free text, always optional).
enum class SurveyKind { one, many, text }

@Serializable
data class SurveyOption(val id: String, val label: String)

@Serializable
data class SurveyQuestion(
    val id: String,
    val kind: SurveyKind,
    /// Cap on a `many` question (question 4 is 2); null = unlimited.
    val max: Int? = null,
    /// The two open (`text`) questions are optional; the five choice ones are not.
    val optional: Boolean = false,
    val text: String,
    val options: List<SurveyOption> = emptyList(),
)

@Serializable
data class SurveyDefinition(
    val surveyKey: String,
    val locale: String,
    val questions: List<SurveyQuestion>,
)

/// `GET /api/survey/respond` — has this person answered? Bearer optional. The screen
/// (and the entry button) show ONLY when the person has not answered and is not an
/// admin (an answer from staff is >0.5% of the data, indistinguishable later).
@Serializable
data class SurveyGate(
    val known: Boolean = false,
    val answered: Boolean = false,
    val isAdmin: Boolean = false,
) {
    val shouldShow: Boolean get() = !answered && !isAdmin
}

/// A 400 from the submit route: `incomplete`, `unknown_person`, `is_admin`,
/// `rate_limited`, `failed`.
class SurveyRefused(val reason: String) : Exception()

object SurveyApi {

    /// The `source` the app sends that the web does not — `android` here (iOS sends
    /// `ios`), so Aviah can tell whether phone users answer differently.
    private const val SOURCE = "android"

    /// The survey's locales overlap the app's four (es exists server-side but the app
    /// ships no Spanish), so clamp to what the app actually renders.
    fun clampLocale(code: String): String =
        if (code in listOf("nl", "fr", "de")) code else "en"

    suspend fun questions(locale: String): SurveyDefinition {
        val resp = httpClient.get("${Backend.WEB_API}/survey/questions") {
            parameter("locale", locale)
        }
        if (resp.status.value != 200) throw IllegalStateException("survey questions ${resp.status.value}")
        return lenientJson.decodeFromString<SurveyDefinition>(resp.bodyAsText())
    }

    /// Best-effort gate: any failure is treated as "unknown, not answered, not admin"
    /// so a network blip does not wrongly hide the survey from a real person.
    suspend fun gate(accessToken: String?): SurveyGate = runCatching {
        val resp = httpClient.get("${Backend.WEB_API}/survey/respond") {
            accessToken?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        }
        if (resp.status.value != 200) return SurveyGate()
        lenientJson.decodeFromString<SurveyGate>(resp.bodyAsText())
    }.getOrDefault(SurveyGate())

    /// One answer: `{ options: [...] }` for one/many, `{ text: … }` for text.
    sealed interface Answer {
        data class Options(val ids: List<String>) : Answer
        data class Text(val value: String) : Answer
    }

    /// `POST /api/survey/respond`. name/email only travel when signed out (server has
    /// them otherwise). 200 → stored; 400 → `{ ok:false, error }` thrown as SurveyRefused.
    suspend fun submit(
        locale: String,
        answers: Map<String, Answer>,
        name: String?,
        email: String?,
        accessToken: String?,
    ) {
        val body = buildJsonObject {
            put("locale", JsonPrimitive(locale))
            put("source", JsonPrimitive(SOURCE))
            name?.takeIf { it.isNotEmpty() }?.let { put("name", JsonPrimitive(it)) }
            email?.takeIf { it.isNotEmpty() }?.let { put("email", JsonPrimitive(it)) }
            putJsonObject("answers") {
                answers.forEach { (qid, ans) ->
                    putJsonObject(qid) {
                        when (ans) {
                            is Answer.Options -> putJsonArray("options") { ans.ids.forEach { add(JsonPrimitive(it)) } }
                            is Answer.Text -> put("text", JsonPrimitive(ans.value))
                        }
                    }
                }
            }
        }
        val resp = httpClient.post("${Backend.WEB_API}/survey/respond") {
            contentType(ContentType.Application.Json)
            accessToken?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            setBody(body)
        }
        if (resp.status.value == 200) return
        val reason = runCatching {
            lenientJson.decodeFromString<Refusal>(resp.bodyAsText()).error
        }.getOrNull() ?: "failed"
        throw SurveyRefused(reason)
    }

    @Serializable
    private data class Refusal(val ok: Boolean = false, val error: String = "failed")

    /// The post-answer feedback box → `POST /api/contact` with `topic: "feedback"`.
    /// The contact route requires name+email+message and has NO `subject` field, so the
    /// box's subject rides on the first line of the message.
    suspend fun sendFeedback(
        subject: String,
        message: String,
        name: String,
        email: String,
        accessToken: String?,
    ) {
        val combined = if (subject.trim().isEmpty()) message else "$subject\n\n$message"
        val body = buildJsonObject {
            put("name", JsonPrimitive(name))
            put("email", JsonPrimitive(email))
            put("topic", JsonPrimitive("feedback"))
            put("message", JsonPrimitive(combined))
            put("source", JsonPrimitive(SOURCE))
        }
        val resp = httpClient.post("${Backend.WEB_API}/contact") {
            contentType(ContentType.Application.Json)
            accessToken?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            setBody(body)
        }
        if (resp.status.value != 200) throw IllegalStateException("contact ${resp.status.value}")
    }
}
