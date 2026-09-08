package app.farmsy.android.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/// JVM unit tests for the opening-hours parser — the Android half of the regression
/// guard iOS keeps in OpeningHoursTests.swift. Android had no unit-test target at all,
/// which is exactly how the en-dash TIME-range bug shipped: #13 fixed the day dash, the
/// time dash was left on the ASCII hyphen, and nothing on this side could catch it.
///
/// FarmFilters is pure Kotlin (java.util.Calendar/TimeZone + Regex, no Android
/// framework), so these run on the JVM with no emulator.
class FarmFiltersTest {

    /// An Amsterdam Calendar pinned to a given hour. `Mo-Su` hours cover every weekday,
    /// so the date does not matter here — only the clock does.
    private fun amsterdamAt(hour: Int, minute: Int = 0): Calendar =
        Calendar.getInstance(TimeZone.getTimeZone("Europe/Amsterdam")).apply {
            set(2026, Calendar.JUNE, 1, hour, minute, 0)
        }

    // The bug this file exists for. An en-dash time range must be a real window, not
    // "a day named with no times = open all day". Before the fix isOpenNow — the PAID
    // filter — answered yes at 03:00, sending a member to a gate shut since the night.
    @Test fun endashTimeRange_isARealWindow_notOpenAllDay() {
        val hours = "Mo-Su 09:00–17:00" // en dash between the two times
        assertFalse("must be closed at 03:00", FarmFilters.isOpenNow(hours, amsterdamAt(3)))
        assertTrue("must be open at 12:00", FarmFilters.isOpenNow(hours, amsterdamAt(12)))
    }

    @Test fun emdashTimeRange_parsesToo() {
        val hours = "Mo-Su 09:00—17:00" // em dash
        assertFalse(FarmFilters.isOpenNow(hours, amsterdamAt(3)))
        assertTrue(FarmFilters.isOpenNow(hours, amsterdamAt(12)))
    }

    // The fix widened the separator class, it did not replace it — a hyphen still works.
    @Test fun hyphenTimeRange_stillWorks() {
        val hours = "Mo-Su 09:00-17:00"
        assertFalse(FarmFilters.isOpenNow(hours, amsterdamAt(3)))
        assertTrue(FarmFilters.isOpenNow(hours, amsterdamAt(12)))
    }

    // Dutch day names and en-dash DAY ranges parse (day-level, no clock needed).
    @Test fun dutchDayNamesAndRanges_parse() {
        assertTrue(FarmFilters.isOpenOnDay("maandag: 09:00–17:00", 0))    // Monday
        assertTrue(FarmFilters.isOpenOnDay("zaterdag 09:00–13:00", 5))    // Saturday
        assertTrue(FarmFilters.isOpenOnDay("ma–vr 09:00–17:00", 2))  // Wed within ma–vr
        assertFalse(FarmFilters.isOpenOnDay("ma–vr 09:00–17:00", 6)) // not Sunday
    }

    // A day the record calls shut, in the words our data uses, is shut — not open.
    @Test fun closedInAnyLanguage_isNotOpen() {
        assertFalse(FarmFilters.isOpenOnDay("Mo-Su 09:00-17:00; zondag gesloten", 6))
        assertFalse(FarmFilters.isOpenOnDay("Mo-Su 09:00-17:00; Su off", 6))
    }
}
