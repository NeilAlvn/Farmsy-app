package app.farmsy.android.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/// The search filter matches against a prebuilt lowercased haystack instead of
/// calling `lowercase()` on three fields per pin. That is only a safe swap if it
/// matches *exactly* the same farms as the old per-field code, so this pins the
/// equivalence rather than the speed.
///
/// The old predicate, kept here as the oracle:
///   name.lowercase().contains(q) || city?.lowercase()?.contains(q) || postalCode...
class SearchHaystackTest {

    private fun haystack(name: String, city: String?, postalCode: String?): String =
        buildString {
            append(name.lowercase())
            city?.takeIf { it.isNotEmpty() }?.let { append('\n').append(it.lowercase()) }
            postalCode?.takeIf { it.isNotEmpty() }?.let { append('\n').append(it.lowercase()) }
        }

    private fun oldMatch(name: String, city: String?, postalCode: String?, q: String): Boolean =
        name.lowercase().contains(q) ||
            (city?.lowercase()?.contains(q) ?: false) ||
            (postalCode?.lowercase()?.contains(q) ?: false)

    private fun newMatch(name: String, city: String?, postalCode: String?, q: String): Boolean =
        haystack(name, city, postalCode).contains(q)

    private val farms = listOf(
        Triple("De Groene Weide", "Amsterdam", "1012 AB"),
        Triple("Hoeve Zonnehof", "Utrecht", "3511 LN"),
        Triple("'t Kleine Erf", null, "9711 AA"),
        Triple("BOERDERIJ DE HORST", "Zwolle", null),
        Triple("Fruitbedrijf", "", ""),
        Triple("Ferme du Pré", "Liège", "4000"),
    )

    private val queries = listOf(
        "de", "groene", "amsterdam", "1012", "utrecht", "horst", "zwolle",
        "9711", "ferme", "liège", "4000", "fruit", "ui", "z", "", "xyz",
        "boerderij", "'t kleine", "1012 ab", "pré",
    )

    @Test
    fun `haystack matches exactly the same farms as the per-field predicate`() {
        for ((name, city, pc) in farms) {
            for (q in queries) {
                assertEquals(
                    "query='$q' farm='$name'",
                    oldMatch(name, city, pc, q),
                    newMatch(name, city, pc, q),
                )
            }
        }
    }

    /// The fields are joined with a newline so a query cannot span two of them.
    /// Without the separator "amsterdam1012" would match a farm in Amsterdam with
    /// postcode 1012 — a farm the old code would never have returned.
    @Test
    fun `a query cannot match across a field boundary`() {
        val hay = haystack("De Groene Weide", "Amsterdam", "1012 AB")
        assertFalse(hay.contains("amsterdam1012"))
        assertFalse(oldMatch("De Groene Weide", "Amsterdam", "1012 AB", "amsterdam1012"))
        // Each field still matches on its own.
        assertTrue(hay.contains("amsterdam"))
        assertTrue(hay.contains("1012"))
    }

    /// A farm with no entry in the map falls back to the live fields, so a pin that
    /// arrived after the cache was built can never be silently hidden from search.
    @Test
    fun `missing haystack falls back instead of dropping the farm`() {
        val hays = emptyMap<String, String>()
        val q = "amsterdam"
        val matched = hays["osm-1"]?.contains(q)
            ?: oldMatch("De Groene Weide", "Amsterdam", "1012 AB", q)
        assertTrue(matched)
    }
}
