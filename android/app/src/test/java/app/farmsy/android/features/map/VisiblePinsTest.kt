package app.farmsy.android.features.map

import app.farmsy.android.core.FarmPin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/// Zoomed out, two farms on the same screen cell draw as one dot at the first
/// farm's own coordinate; zoomed in, both draw. Mirrors iOS visiblePins.
class VisiblePinsTest {
    private fun pin(id: String, lat: Double, lng: Double) = FarmPin(id = id, osmId = id, name = id, lat = lat, lng = lng)

    private val pins = listOf(
        pin("a", 52.0000, 5.0000),
        pin("b", 52.0001, 5.0001),   // same cell as a at province zoom
        pin("c", 52.5000, 5.5000),
        pin("far", 60.0, 20.0),      // outside the viewport
    )

    @Test
    fun `province zoom thins to one pin per cell and culls off-screen`() {
        val out = visiblePins(pins, 52.0, 5.0, 3.4, 3.4)
        assertEquals(listOf("a", "c"), out.map { it.osmId })
    }

    @Test
    fun `neighbourhood zoom draws every farm in view`() {
        val out = visiblePins(pins, 52.0, 5.0, 0.05, 0.05)
        assertEquals(listOf("a", "b"), out.map { it.osmId })
    }

    /// 8,400 farms is the real dataset. A fixed 56-cell grid has a fixed cell
    /// *count* (~5,000) however far out you are, so at country zoom it handed
    /// 2,000-3,000 Marker composables to the map and froze it on Android.
    private val dense: List<FarmPin> = buildList {
        var i = 0
        for (row in 0 until 120) for (col in 0 until 70) {
            add(pin("p${i++}", 50.80 + row * 0.02, 3.40 + col * 0.05))
        }
    }

    @Test
    fun `country zoom stays under the marker ceiling`() {
        val out = visiblePins(dense, 52.0, 5.0, 3.4, 3.4)
        assertTrue("drew ${out.size} pins, ceiling is $MAX_PINS", out.size <= MAX_PINS)
        // Still a map, not an empty one.
        assertTrue("drew only ${out.size} pins", out.size > 50)
    }

    @Test
    fun `thinning coarsens instead of truncating, so dots stay spread out`() {
        val out = visiblePins(dense, 52.0, 5.0, 3.4, 3.4)
        // The pin list is not in screen order, so take(MAX_PINS) would bunch the
        // dots into whichever corner it starts in and leave the rest blank.
        val latRange = out.maxOf { it.lat } - out.minOf { it.lat }
        val lngRange = out.maxOf { it.lng } - out.minOf { it.lng }
        assertTrue("dots only span $latRange of latitude", latRange > 1.5)
        assertTrue("dots only span $lngRange of longitude", lngRange > 2.0)
    }
}
