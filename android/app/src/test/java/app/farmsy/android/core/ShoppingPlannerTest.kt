package app.farmsy.android.core

import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/// JVM unit tests for the shopping list — the Android mirror of
/// FarmsyTests/ShoppingPlannerTests.swift.
///
/// The MATCHING rules are pinned hardest, because both traps were live on the
/// website before they were fixed there: a short term inside a longer word
/// (`ui` in `fruit`), and a search vocabulary used for a list (`lamb` also
/// meaning beef and pork).
///
/// The PLAN never throws when it is wrong — a bad plan is a plausible-looking
/// list of the wrong farms. So: cover what you can, say what you could not,
/// never wander.
class ShoppingPlannerTest {

    // Utrecht-ish. 0.01 degrees of latitude is about 1.1 km.
    private val origin = LatLng(52.09, 5.12)
    private fun at(dLat: Double, dLng: Double = 0.0) = LatLng(origin.latitude + dLat, origin.longitude + dLng)

    /// Items as GET /api/shopping/items serves them — terms already narrowed
    /// and pluralised by the web, which is the whole reason they are served.
    private fun item(id: String, terms: List<String>) = ShoppingItem(id, id, id, terms)

    private val eggs = item("eggs", listOf("eggs", "egg", "eieren", "ei"))
    private val cheese = item("cheese", listOf("cheese", "kaas", "boerenkaas"))
    private val milk = item("milk", listOf("milk", "melk"))
    private val onions = item("onions", listOf("onions", "onion", "ui", "uien"))
    private val strawberry = item("strawberry", listOf("strawberry", "strawberries", "aardbei", "aardbeien"))

    /// The narrowed one. `expandQuery('lamb')` would also return beef and pork.
    private val lamb = item("lamb", listOf("lamb", "lamsvlees"))

    private fun plan(
        wanted: List<ShoppingItem>,
        farms: List<ShoppingPlanner.Candidate>,
        maxStops: Int = 5,
        radiusKm: Double = 25.0,
    ) = ShoppingPlanner.plan(wanted, farms, origin, maxStops, radiusKm)

    // ── The matching rules ──────────────────────────────────────────────────

    @Test
    fun `the list rule needs a whole word, for every term`() {
        assertFalse(ProductMatch.covers("fruit, tuinplanten", listOf("ui")))
        assertTrue(ProductMatch.covers("ui, aardappelen", listOf("ui")))
        // Long terms too: the served terms already carry their plurals.
        assertFalse(ProductMatch.covers("boerenkaas", listOf("kaas")))
        assertTrue(ProductMatch.covers("boerenkaas", listOf("boerenkaas")))
    }

    @Test
    fun `the search rule still finds a word inside a compound`() {
        assertTrue(ProductMatch.matches("boerenkaas en geitenkaas", listOf("kaas")))
        assertFalse(ProductMatch.matches("fruit, tuinplanten", listOf("ui")))
        assertFalse(ProductMatch.matches("prijslijst", listOf("ijs")))
    }

    @Test
    fun `accents and hyphens fold on both sides`() {
        assertTrue(ProductMatch.covers("Légumes de saison", listOf("legumes")))
        assertTrue(ProductMatch.covers("pommes-de-terre", listOf("pommes de terre")))
        assertTrue(ProductMatch.covers("hard-cheese", listOf("cheese")))
    }

    @Test
    fun `no terms covers nothing`() {
        assertFalse(ProductMatch.covers("eggs", emptyList()))
        assertFalse(ProductMatch.covers("", listOf("eggs")))
    }

    @Test
    fun `a narrowed item does not claim a farm selling its cousins`() {
        val p = plan(listOf(lamb), listOf(
            ShoppingPlanner.Candidate("beef-farm", at(0.01), "beef, pork, chicken"),
        ))
        assertTrue(p.isEmpty)
        assertEquals(listOf("lamb"), p.missing)
    }

    // ── One stop is better than two ─────────────────────────────────────────

    @Test
    fun `a farm that sells everything is the only stop`() {
        val p = plan(listOf(eggs, cheese), listOf(
            ShoppingPlanner.Candidate("a", at(0.02), "eggs, cheese, milk"),
            ShoppingPlanner.Candidate("b", at(0.03), "eggs"),
        ))
        assertEquals(listOf("a"), p.picks.map { it.osmId })
        assertEquals(listOf("eggs", "cheese"), p.picks.first().covers)
        assertTrue(p.missing.isEmpty())
    }

    @Test
    fun `two farms when one cannot cover the list, nearest useful first`() {
        val p = plan(listOf(eggs, cheese), listOf(
            ShoppingPlanner.Candidate("eggs-close", at(0.01), "eggs"),
            ShoppingPlanner.Candidate("cheese-far", at(0.05), "cheese"),
        ))
        assertEquals(listOf("eggs-close", "cheese-far"), p.picks.map { it.osmId })
        assertEquals(listOf("eggs"), p.picks.first().covers)
    }

    // ── Say what you could not find ─────────────────────────────────────────

    @Test
    fun `an item no farm sells is reported, not silently dropped`() {
        val p = plan(listOf(eggs, strawberry), listOf(
            ShoppingPlanner.Candidate("a", at(0.01), "eggs"),
        ))
        assertEquals(1, p.picks.size)
        assertEquals(listOf("strawberry"), p.missing)
    }

    @Test
    fun `a farm that never said what it sells is never a stop`() {
        val p = plan(listOf(eggs), listOf(
            ShoppingPlanner.Candidate("silent", at(0.001), ""),
            ShoppingPlanner.Candidate("says-so", at(0.05), "eggs"),
        ))
        assertEquals(listOf("says-so"), p.picks.map { it.osmId })
    }

    // ── Never wander ────────────────────────────────────────────────────────

    @Test
    fun `a farm beyond the radius is not offered, however well it fits`() {
        val p = plan(listOf(eggs), listOf(
            ShoppingPlanner.Candidate("far", at(0.5), "eggs, cheese, milk"),
        ))
        assertTrue(p.isEmpty)
        assertEquals(listOf("eggs"), p.missing)
    }

    @Test
    fun `the trip stops at maxStops even with more to buy`() {
        val p = plan(listOf(eggs, cheese, milk), listOf(
            ShoppingPlanner.Candidate("a", at(0.01), "eggs"),
            ShoppingPlanner.Candidate("b", at(0.02), "cheese"),
            ShoppingPlanner.Candidate("c", at(0.03), "milk"),
        ), maxStops = 2)
        assertEquals(2, p.picks.size)
        assertEquals(listOf("milk"), p.missing)
    }

    // ── The served terms are the point ──────────────────────────────────────

    @Test
    fun `Dutch finds English data`() {
        val p = plan(listOf(strawberry), listOf(
            ShoppingPlanner.Candidate("a", at(0.01), "strawberry, asparagus"),
        ))
        assertEquals(listOf("a"), p.picks.map { it.osmId })
    }

    @Test
    fun `a short term does not drag in half the map`() {
        val p = plan(listOf(onions), listOf(
            ShoppingPlanner.Candidate("fruit-farm", at(0.01), "fruit, tuinplanten"),
            ShoppingPlanner.Candidate("onion-farm", at(0.04), "ui, aardappelen"),
        ))
        assertEquals(listOf("onion-farm"), p.picks.map { it.osmId })
    }

    // ── Same list, same trip ────────────────────────────────────────────────

    @Test
    fun `two identical farms plan the same way every time`() {
        val farms = listOf(
            ShoppingPlanner.Candidate("zzz", at(0.01), "eggs"),
            ShoppingPlanner.Candidate("aaa", at(0.01), "eggs"),
        )
        assertEquals(plan(listOf(eggs), farms), plan(listOf(eggs), farms.reversed()))
        assertEquals(listOf("aaa"), plan(listOf(eggs), farms).picks.map { it.osmId })
    }

    @Test
    fun `an empty list plans nothing rather than the whole map`() {
        val p = plan(emptyList(), listOf(
            ShoppingPlanner.Candidate("a", at(0.01), "eggs"),
        ))
        assertTrue(p.isEmpty)
        assertTrue(p.missing.isEmpty())
    }
}
