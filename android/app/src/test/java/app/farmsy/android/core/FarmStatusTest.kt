package app.farmsy.android.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/// What visitors said about a farm, turned into one honest sentence — the
/// Android mirror of FarmsyTests/FarmStatusTests.swift and the web's
/// src/lib/statusReports.test.ts.
///
/// Nothing here throws when it is wrong. A summary that leads with "people got
/// in" for a farm three people found shut sends somebody on a wasted drive, and
/// it looks exactly like a working feature while it does so.
class FarmStatusTest {

    private val now: Instant = Instant.ofEpochSecond(1_780_000_000)

    private fun report(status: ReportStatus, daysAgo: Double) = FarmReport(
        status = status.wire,
        createdAt = now.minusSeconds((daysAgo * 86_400).toLong()).toString(),
    )

    private fun summarise(reports: List<FarmReport>) = FarmStatus.summarise(reports, now)

    // ── The asymmetric warning ──────────────────────────────────────────────

    @Test
    fun `one closed against one open leads with the warning`() {
        // The cost of the two errors is not symmetrical: a wasted drive is worse
        // than a second phone call.
        val s = summarise(listOf(report(ReportStatus.OPEN, 1.0), report(ReportStatus.CLOSED, 2.0)))
        assertEquals(StatusLead.TROUBLE, s.lead)
    }

    @Test
    fun `one closed against four open is mixed, not a warning`() {
        val s = summarise(listOf(
            report(ReportStatus.OPEN, 1.0), report(ReportStatus.OPEN, 2.0),
            report(ReportStatus.OPEN, 3.0), report(ReportStatus.OPEN, 4.0),
            report(ReportStatus.CLOSED, 5.0),
        ))
        assertEquals(StatusLead.MIXED, s.lead)
    }

    @Test
    fun `one closed against two open still warns, half is not outnumbered`() {
        val s = summarise(listOf(
            report(ReportStatus.OPEN, 1.0), report(ReportStatus.OPEN, 2.0),
            report(ReportStatus.CLOSED, 3.0),
        ))
        assertEquals(StatusLead.TROUBLE, s.lead)
    }

    @Test
    fun `only open reports lead with open`() {
        val s = summarise(listOf(report(ReportStatus.OPEN, 1.0), report(ReportStatus.OPEN, 9.0)))
        assertEquals(StatusLead.OPEN, s.lead)
        assertEquals(2, s.open)
        assertEquals(0, s.trouble)
    }

    @Test
    fun `sold out counts as trouble, not as open`() {
        val s = summarise(listOf(report(ReportStatus.SOLD_OUT, 1.0)))
        assertEquals(StatusLead.TROUBLE, s.lead)
        assertEquals(1, s.soldOut)
        assertEquals(1, s.trouble)
    }

    // ── No reports is not a verdict ─────────────────────────────────────────

    @Test
    fun `nothing said leads with nothing at all`() {
        val s = summarise(emptyList())
        assertEquals(StatusLead.NONE, s.lead)
        assertEquals(0, s.total)
        assertNull(s.daysAgo)
    }

    @Test
    fun `a farm shut last March says nothing about this Saturday`() {
        val s = summarise(listOf(report(ReportStatus.CLOSED, 60.0)))
        assertEquals(StatusLead.NONE, s.lead)
        assertEquals(0, s.total)
    }

    @Test
    fun `the cutoff is twenty-one days`() {
        assertEquals(1, summarise(listOf(report(ReportStatus.OPEN, 20.9))).total)
        assertEquals(0, summarise(listOf(report(ReportStatus.OPEN, 21.1))).total)
    }

    // ── A wrong clock is not a fact ─────────────────────────────────────────

    @Test
    fun `a report from the future is ignored`() {
        val s = summarise(listOf(report(ReportStatus.CLOSED, -3.0)))
        assertEquals(0, s.total)
        assertEquals(StatusLead.NONE, s.lead)
    }

    // ── How long ago ────────────────────────────────────────────────────────

    @Test
    fun `daysAgo is the most recent report shown`() {
        val s = summarise(listOf(report(ReportStatus.OPEN, 9.0), report(ReportStatus.OPEN, 2.0)))
        assertEquals(2, s.daysAgo)
    }

    // ── One vote per person per day ─────────────────────────────────────────

    @Test
    fun `today's own report is what the buttons show`() {
        val mine = listOf(report(ReportStatus.CLOSED, 0.0))
        assertEquals(ReportStatus.CLOSED, FarmStatus.myReportToday(mine, now))
    }

    @Test
    fun `yesterday's own report does not light today's button`() {
        // A farm can be shut on Monday and open on Saturday, and both are true.
        val mine = listOf(report(ReportStatus.CLOSED, 2.0))
        assertNull(FarmStatus.myReportToday(mine, now))
    }

    @Test
    fun `the day is an Amsterdam day, not the device's`() {
        // 22:30 UTC on 1 June is already 2 June in Amsterdam (CEST, UTC+2).
        val lateEvening = Instant.parse("2026-06-01T22:30:00Z")
        assertEquals("2026-06-02", FarmStatus.amsterdamDay(lateEvening))
    }

    // ── Timestamps as Postgres sends them ───────────────────────────────────

    @Test
    fun `timestamps parse with or without fractional seconds`() {
        assertNotNull(FarmStatus.parseTimestamp("2026-06-01T10:00:00Z"))
        assertNotNull(FarmStatus.parseTimestamp("2026-06-01T10:00:00.123456+00:00"))
        assertNull(FarmStatus.parseTimestamp("not a date"))
    }
}

/// The Plus half: minutes, how many agree today, and what they found. Mirror
/// of FarmsyTests FreshnessTests.
class FreshnessTest {

    private val now: Instant = Instant.ofEpochSecond(1_800_000_000)

    private fun at(minutesAgo: Long, status: ReportStatus, products: List<String> = emptyList()) = FarmReport(
        status = status.wire,
        createdAt = now.minusSeconds(minutesAgo * 60).toString(),
        products = products,
    )

    @Test
    fun `newest report leads, only same-status reports in the last day count as confirmations`() {
        val f = FarmStatus.freshness(listOf(
            at(18, ReportStatus.OPEN, listOf("eggs")),
            at(240, ReportStatus.OPEN, listOf("cheese", "eggs")),
            at(300, ReportStatus.CLOSED),
            at(60 * 30, ReportStatus.OPEN, listOf("milk")),   // 30 h ago: too old
        ), now)
        assertEquals(ReportStatus.OPEN, f?.status)
        assertEquals(18, f?.minutesAgo)
        assertEquals(2, f?.confirmations)
        assertEquals(listOf("eggs", "cheese"), f?.products)
    }

    @Test
    fun `a day-old report is history, not confirmation`() {
        assertNull(FarmStatus.freshness(listOf(at(60 * 25, ReportStatus.OPEN)), now))
        assertNull(FarmStatus.freshness(emptyList(), now))
    }
}
