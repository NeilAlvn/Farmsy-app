package app.farmsy.android.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/// GET /api/products decodes as the sheet reads it: twelve month states,
/// the snake_case notes, `where` as the keeping place, and the tips array.
class ProductsTest {

    private val payload = """
        {"lang":"nl","products":[{"slug":"aardappel-nieuw","image":"aardappel-nieuw","shopping":null,
        "seasonal":"aardappel-nieuw","name":"Nieuwe aardappelen",
        "months":["none","none","none","none","none","peak","peak","available","none","none","none","none"],
        "region_note":"Zeeuwse klei","greenhouse_note":"",
        "choose":["Dunne schil"],"store":{"where":"Koel en donker","how":"Niet wassen","days":14},
        "preserve":[{"method":"invriezen","how":"Eerst koken"}],
        "ideas":[{"title":"Krieltjes","body":"Met boter","ingredients":["aardappel-nieuw","butter"],"image":"recipe-aardappel-nieuw-1"}],
        "tips":["Schil niet","Kook met schil"],"pairs":["butter","fish"],"fun_fact":"Uit Zeeland"}]}
    """.trimIndent()

    @Test
    fun `months, snake_case fields and the tips array decode`() {
        val p = Products.decode(payload).single()
        assertEquals(MonthState.NONE, p.state(1))
        assertEquals(MonthState.PEAK, p.state(6))
        assertEquals(MonthState.AVAILABLE, p.state(8))
        assertEquals(MonthState.NONE, p.state(13))   // out of range reads as none, not a crash
        assertEquals("Zeeuwse klei", p.regionNote)
        assertEquals("", p.greenhouseNote)
        assertEquals("Uit Zeeland", p.funFact)
        assertNull(p.shopping)
        assertEquals("aardappel-nieuw", p.seasonal)
        assertEquals("Koel en donker", p.store.place)
        assertEquals(14, p.store.days)
        assertEquals(listOf("Schil niet", "Kook met schil"), p.tips)
        assertEquals(listOf("aardappel-nieuw", "butter"), p.ideas.single().ingredients)
        assertEquals("invriezen", p.preserve.single().method)
    }
}
