package app.farmsy.android.features.map

import app.farmsy.android.core.FarmPin
import org.junit.Assert.assertEquals
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
}
