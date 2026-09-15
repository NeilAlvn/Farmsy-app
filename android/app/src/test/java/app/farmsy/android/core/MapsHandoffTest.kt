package app.farmsy.android.core

import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/// R8: the drive the user planned is the drive the maps app opens.
///
/// The bug this exists for shipped with R1 and is silent. A drive planned
/// Utrecht to Groningen opened as a drive ending at the last farm, because the
/// hand-off predated trips having a destination of their own. Nothing errored
/// and nothing looked wrong until you were in the car.
///
/// The second silent one is Google's waypoint cap. Past nine, dir/?api=1 drops
/// the surplus without a word, so a ten-stop drive opens missing stops.
///
/// These mirror `MapsHandoffTests.swift` and `mapsHandoff.test.ts` case for
/// case. A case that changes here changes there, in the same commit.
class MapsHandoffTest {

    private val utrecht = LatLng(52.0907, 5.1214)
    private val groningen = LatLng(53.2194, 6.5665)
    private val farmA = LatLng(52.5, 5.5)
    private val farmB = LatLng(52.8, 6.0)

    /// The query string, as a map. Deliberately hand-rolled: android.net.Uri is
    /// not available in a plain JVM unit test, which is the same reason
    /// MapsHandoff does not use it either.
    private fun params(url: String): Map<String, String> =
        url.substringAfter('?', "").split("&").filter { it.contains('=') }
            .associate { it.substringBefore('=') to it.substringAfter('=') }

    // ── The bug R1 left behind ──────────────────────────────────────────────

    @Test
    fun `a drive with a destination ends there, not at the last farm`() {
        val p = params(MapsHandoff.googleMapsUrl(
            MapsHandoff.Plan(origin = utrecht, stops = listOf(farmA, farmB), destination = groningen)
        ))
        assertEquals("52.0907,5.1214", p["origin"])
        assertEquals("53.2194,6.5665", p["destination"])
        // Both farms survive as stops on the way — neither is consumed as the end.
        assertEquals("52.5,5.5%7C52.8,6.0", p["waypoints"])
    }

    @Test
    fun `with no destination the last farm becomes one, as it did before R1`() {
        val p = params(MapsHandoff.googleMapsUrl(
            MapsHandoff.Plan(origin = utrecht, stops = listOf(farmA, farmB))
        ))
        assertEquals("52.8,6.0", p["destination"])
        assertEquals("52.5,5.5", p["waypoints"])
    }

    // ── Google's waypoint cap ───────────────────────────────────────────────

    @Test
    fun `a drive longer than the cap keeps every stop`() {
        val many = (0 until MapsHandoff.API_WAYPOINT_LIMIT + 3).map { LatLng(52.0 + it / 100.0, 5.0) }
        val url = MapsHandoff.googleMapsUrl(
            MapsHandoff.Plan(origin = utrecht, stops = many, destination = groningen)
        )
        assertFalse("past the cap the path form is used", url.contains("api=1"))
        many.forEach { assertTrue("stop ${it.latitude} kept", url.contains("${it.latitude},${it.longitude}")) }
        assertTrue("destination kept", url.contains("53.2194,6.5665"))
    }

    @Test
    fun `exactly at the cap still uses the documented form`() {
        val many = (0 until MapsHandoff.API_WAYPOINT_LIMIT).map { LatLng(52.0 + it / 100.0, 5.0) }
        val url = MapsHandoff.googleMapsUrl(
            MapsHandoff.Plan(origin = utrecht, stops = many, destination = groningen)
        )
        assertTrue(url.contains("api=1"))
        assertEquals(MapsHandoff.API_WAYPOINT_LIMIT, params(url)["waypoints"]!!.split("%7C").size)
    }

    // ── Not enough to route ─────────────────────────────────────────────────

    @Test
    fun `one place and no start is a search, not a route from nowhere`() {
        val url = MapsHandoff.googleMapsUrl(MapsHandoff.Plan(stops = listOf(farmA)))
        assertTrue(url.startsWith("https://www.google.com/maps/search/"))
        assertEquals("52.5,5.5", params(url)["query"])
    }

    @Test
    fun `nothing planned hands over nothing`() {
        assertEquals("", MapsHandoff.googleMapsUrl(MapsHandoff.Plan()))
        assertEquals("", MapsHandoff.googleMapsUrl(MapsHandoff.Plan(origin = utrecht)))
    }

    @Test
    fun `a destination and no stops is still a drive`() {
        val p = params(MapsHandoff.googleMapsUrl(
            MapsHandoff.Plan(origin = utrecht, destination = groningen)
        ))
        assertEquals("52.0907,5.1214", p["origin"])
        assertEquals("53.2194,6.5665", p["destination"])
        assertEquals(null, p["waypoints"])
    }

    @Test
    fun `no start means the first farm is the start, and is not also a stop`() {
        val p = params(MapsHandoff.googleMapsUrl(
            MapsHandoff.Plan(stops = listOf(farmA, farmB), destination = groningen)
        ))
        assertEquals("52.5,5.5", p["origin"])
        assertEquals("52.8,6.0", p["waypoints"])
        assertEquals("53.2194,6.5665", p["destination"])
    }

    // ── The URL itself ──────────────────────────────────────────────────────

    @Test
    fun `the waypoint separator is encoded, and the coordinates are not`() {
        // An unencoded pipe is a different parse in some clients and loses every
        // stop after the first. Encoding the whole string instead would also
        // encode the commas and minus signs — valid, but a different URL than
        // the web and iOS send for the same drive.
        val url = MapsHandoff.googleMapsUrl(
            MapsHandoff.Plan(origin = utrecht, stops = listOf(farmA, farmB), destination = groningen)
        )
        assertTrue("pipe is encoded", url.contains("%7C"))
        assertFalse("and never raw", url.contains("|"))
        assertFalse("commas stay commas", url.contains("%2C"))
    }

    @Test
    fun `a travel mode is passed through, and its absence is not`() {
        val withMode = MapsHandoff.googleMapsUrl(
            MapsHandoff.Plan(origin = utrecht, destination = groningen, travelMode = "bicycling")
        )
        assertEquals("bicycling", params(withMode)["travelmode"])

        // The web has no mode picker, so leaving it out must produce the URL the
        // web produces rather than a default nobody chose.
        val without = MapsHandoff.googleMapsUrl(
            MapsHandoff.Plan(origin = utrecht, destination = groningen)
        )
        assertFalse(without.contains("travelmode"))
    }

    @Test
    fun `southern and western coordinates survive`() {
        // Minus signs must not be encoded away. Farmsy is NL and BE only today,
        // but the formatter is not, and a silently mangled minus is the kind of
        // thing that is found by a user and not by a test.
        val url = MapsHandoff.googleMapsUrl(
            MapsHandoff.Plan(origin = LatLng(-33.9249, 18.4241), destination = LatLng(-34.0, -18.5))
        )
        assertEquals("-33.9249,18.4241", params(url)["origin"])
        assertEquals("-34.0,-18.5", params(url)["destination"])
    }
}
