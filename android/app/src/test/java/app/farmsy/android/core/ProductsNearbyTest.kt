package app.farmsy.android.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/// Home's "Available near you": counts per list item within the radius, nearest
/// distance, most farms first, and nothing for a product nobody nearby sells.
/// Mirrors FarmsyTests/ProductsNearbyTests.swift.
class ProductsNearbyTest {

    private val originLat = 52.09
    private val originLng = 5.12

    private fun pin(id: String, dLat: Double) =
        FarmPin(id = id, osmId = id, name = id, lat = originLat + dLat, lng = originLng)

    private val eggs = ShoppingItem("eggs", "Eieren", "Eggs", listOf("eggs", "eieren"))
    private val cheese = ShoppingItem("cheese", "Kaas", "Cheese", listOf("cheese", "kaas"))
    private val fish = ShoppingItem("fish", "Vis", "Fish", listOf("fish", "vis"))

    @Test
    fun `counts, nearest distance, order, and the radius cut`() {
        // 0.01° lat ≈ 1.1 km. Three farms inside 15 km, one at ~33 km.
        val pins = listOf(pin("a", 0.02), pin("b", 0.05), pin("c", 0.09), pin("far", 0.30))
        val produce = mapOf("a" to "eieren, kaas", "b" to "kaas", "c" to "kaas, vis", "far" to "vis, eieren")
        val out = FarmsStore.productsNearby(listOf(eggs, cheese, fish), pins, produce, originLat, originLng, 15.0)
        assertEquals(listOf("cheese", "eggs", "fish"), out.map { it.item.id })
        assertEquals(3, out[0].count)
        assertEquals(1, out[1].count)   // the far farm's eggs are outside the radius
        assertTrue(Math.abs(out[1].nearestKm - 2.2) < 0.2)
        assertEquals(1, out[2].count)
    }

    @Test
    fun `a product nobody nearby sells is absent, not zero`() {
        val out = FarmsStore.productsNearby(listOf(fish), listOf(pin("a", 0.01)), mapOf("a" to "kaas"), originLat, originLng, 15.0)
        assertTrue(out.isEmpty())
    }
}
