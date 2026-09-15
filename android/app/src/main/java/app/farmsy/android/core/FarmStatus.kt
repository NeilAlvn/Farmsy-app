package app.farmsy.android.core

import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// "Was it open when you got there?" — one tap, from the farm card.
// Android twin of iOS FarmStatus.swift, port of src/lib/statusReports.ts.
//
// WHY THIS CARRIES MORE THAN IT LOOKS
// Google Places was dropped, which took away permanently-closed detection.
// Track V was paused, which took away ringing every farm in a province. This is
// the only thing in phase one that says a listing has gone stale, and it costs
// a visitor one tap on a screen they were already looking at.
//
// WHAT IT IS NOT
// Not the farmer speaking. There are no farmers on the platform yet, so every
// report here is another visitor's, and the copy has to keep saying so.

/// What a visitor found. Deliberately no "I don't know": somebody unsure should
/// say nothing, and the absence of reports is already how we say we do not know.
enum class ReportStatus(val wire: String) {
    OPEN("open"),
    CLOSED("closed"),
    SOLD_OUT("sold_out");

    companion object {
        fun fromWire(s: String?): ReportStatus? = entries.firstOrNull { it.wire == s }
    }
}

@Serializable
data class FarmReport(
    val status: String = "open",
    @SerialName("created_at") val createdAt: String = "",
) {
    val reportStatus: ReportStatus? get() = ReportStatus.fromWire(status)
    val instant: Instant? get() = FarmStatus.parseTimestamp(createdAt)
}

@Serializable
data class FarmStatusLoaded(
    val reports: List<FarmReport> = emptyList(),
    /// This caller's own reports, so the buttons can show their own state.
    val mine: List<FarmReport> = emptyList(),
)

/// What the panel should lead with.
enum class StatusLead {
    /// Say nothing at all. No reports is not a verdict.
    NONE,
    /// Recent visitors got in.
    OPEN,
    /// Recent visitors found it shut or sold out. Worth a warning even when
    /// others got in, because the cost of the two errors is not symmetrical: a
    /// wasted drive is worse than a second phone call.
    TROUBLE,
    /// Enough of both that neither leads.
    MIXED,
}

data class StatusSummary(
    val open: Int = 0,
    val closed: Int = 0,
    val soldOut: Int = 0,
    val total: Int = 0,
    /// Days since the most recent report shown, or null when there are none.
    val daysAgo: Int? = null,
    val lead: StatusLead = StatusLead.NONE,
) {
    val trouble: Int get() = closed + soldOut
}

object FarmStatus {

    /// Reports older than this are kept but not shown. A farm that was shut last
    /// March tells you nothing about this Saturday.
    const val RECENT_DAYS = 21L

    private val AMSTERDAM: ZoneId = ZoneId.of("Europe/Amsterdam")

    /// Summarise the reports for one farm. `now` is injectable so a test does
    /// not depend on the clock.
    ///
    /// Counts and recency, never a percentage: "50% found it open" is a lie with
    /// a decimal point in it, because at this volume there is no sample.
    fun summarise(reports: List<FarmReport>, now: Instant = Instant.now()): StatusSummary {
        val cutoff = now.minus(RECENT_DAYS, ChronoUnit.DAYS)
        val tomorrow = now.plus(1, ChronoUnit.DAYS)

        var open = 0; var closed = 0; var soldOut = 0
        var newest: Instant? = null

        for (r in reports) {
            val at = r.instant ?: continue
            if (at.isBefore(cutoff)) continue
            // A report dated in the future is a clock problem, not a fact.
            if (at.isAfter(tomorrow)) continue

            when (r.reportStatus) {
                ReportStatus.OPEN -> open++
                ReportStatus.CLOSED -> closed++
                ReportStatus.SOLD_OUT -> soldOut++
                null -> continue
            }
            if (newest == null || at.isAfter(newest)) newest = at
        }

        val total = open + closed + soldOut
        val trouble = closed + soldOut

        val lead = when {
            total == 0 -> StatusLead.NONE
            // Trouble leads unless it is clearly outnumbered. One "closed"
            // against four "open" is probably somebody arriving after hours; one
            // against one is not something to wave away.
            trouble > 0 && trouble * 2 >= open -> StatusLead.TROUBLE
            trouble > 0 -> StatusLead.MIXED
            else -> StatusLead.OPEN
        }

        return StatusSummary(
            open = open, closed = closed, soldOut = soldOut, total = total,
            daysAgo = newest?.let { ChronoUnit.DAYS.between(it, now).toInt() },
            lead = lead,
        )
    }

    /// Whether this person already reported today, and what they said.
    ///
    /// The table allows one row per person per farm per Amsterdam day, so the
    /// buttons show their own state rather than offering to vote again.
    fun myReportToday(mine: List<FarmReport>, now: Instant = Instant.now()): ReportStatus? {
        val today = amsterdamDay(now)
        for (r in mine) {
            val at = r.instant ?: continue
            if (amsterdamDay(at) == today) return r.reportStatus
        }
        return null
    }

    /// `YYYY-MM-DD` in Amsterdam, matching the generated column in migration 065.
    /// The farms are in NL and BE; a visitor's own timezone is not the question.
    fun amsterdamDay(at: Instant): String =
        DateTimeFormatter.ISO_LOCAL_DATE.format(at.atZone(AMSTERDAM).toLocalDate())

    /// Postgres timestamptz varies in fractional-second precision; normalise
    /// before parsing. Mirrors Profile.parsePostgresDate.
    fun parseTimestamp(raw: String): Instant? {
        if (raw.isEmpty()) return null
        val noFraction = raw.replace(Regex("\\.\\d+"), "")
        return runCatching { Instant.parse(noFraction) }.getOrNull()
            ?: runCatching { java.time.OffsetDateTime.parse(noFraction).toInstant() }.getOrNull()
            ?: runCatching { Instant.parse(noFraction + "Z") }.getOrNull()
    }
}

// ── API ──────────────────────────────────────────────────────────────────────

/// GET / POST / DELETE /api/farm/{osmId}/status.
///
/// The website calls the server actions directly; the app cannot, so the web
/// wraps them in this route. One place decides who may write, and there is no
/// second copy of the one-per-day rule to drift.
object FarmStatusApi {

    private fun url(osmId: String) =
        "${Backend.WEB_API}/farm/${java.net.URLEncoder.encode(osmId, "UTF-8")}/status"

    /// Reading is public: no token, no account. Failure returns null rather than
    /// throwing — on this panel "we could not look" and "nobody has said" look
    /// the same and read the same.
    suspend fun load(osmId: String, token: String?): FarmStatusLoaded? = runCatching {
        val resp = httpClient.get(url(osmId)) {
            if (token != null) header(HttpHeaders.Authorization, "Bearer $token")
        }
        if (resp.status.value != 200) null
        else lenientJson.decodeFromString<FarmStatusLoaded>(resp.bodyAsText())
    }.getOrNull()

    /// Report what you found. Sending the same status twice is a correction, not
    /// a second vote — the caller clears instead.
    suspend fun report(osmId: String, status: ReportStatus, token: String): Boolean = runCatching {
        httpClient.post(url(osmId)) {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"status":"${status.wire}"}""")
        }.status.value == 200
    }.getOrDefault(false)

    /// Take back today's report.
    suspend fun clear(osmId: String, token: String): Boolean = runCatching {
        httpClient.delete(url(osmId)) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.status.value == 200
    }.getOrDefault(false)
}
