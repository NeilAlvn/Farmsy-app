package app.farmsy.android.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/// Android twin of FarmsyTests/FarmLinkTests.swift, plus the Play Install
/// Referrer string (spec A4). Pure: takes the path and the referrer as strings,
/// no android.net.Uri, so it runs on the JVM.
class FarmLinksTest {
    private val hoeve = FarmPathRef("ede", "de-hoeve")

    @Test
    fun `a farm page in any language`() {
        assertEquals(hoeve, FarmLinks.parsePath("/boerderijwinkels/ede/de-hoeve"))
        assertEquals(hoeve, FarmLinks.parsePath("/en/boerderijwinkels/ede/de-hoeve"))
        assertEquals(hoeve, FarmLinks.parsePath("/de/boerderijwinkels/ede/de-hoeve/"))
    }

    @Test
    fun `anything else is not a farm link`() {
        assertNull(FarmLinks.parsePath("/join"))
        assertNull(FarmLinks.parsePath("/boerderijwinkels/ede"))
        assertNull(FarmLinks.parsePath("/boerderijwinkels/ede/de-hoeve/extra"))
        assertNull(FarmLinks.parsePath("/nl/boerderijwinkels/ede/de-hoeve"))
        assertNull(FarmLinks.parsePath("/boerderijwinkels/Ede/de-hoeve"))
        assertNull(FarmLinks.parsePath("/boerderijwinkels/ede/de--hoeve"))
        assertNull(FarmLinks.parsePath(null))
    }

    @Test
    fun `segment is anchored at the true end and capped at 80`() {
        assertNull(FarmLinks.parsePath("/boerderijwinkels/ede/de-hoeve\n"))
        assertNull(FarmLinks.parsePath("/boerderijwinkels/ede/" + "a".repeat(81)))
        assertEquals(FarmPathRef("ede", "a".repeat(80)), FarmLinks.parsePath("/boerderijwinkels/ede/" + "a".repeat(80)))
    }

    @Test
    fun `referrer from a web button carries the farm, decoded once`() {
        val r = FarmLinks.parseReferrer(
            "utm_source=web&utm_medium=farm_page&utm_campaign=app_funnel&farm=node%2F123",
        )
        assertEquals(InstallReferrer(campaign = "app_funnel", medium = "farm_page", farm = "node/123"), r)
    }

    @Test
    fun `organic install, empty or broken referrer has no farm`() {
        assertEquals(
            InstallReferrer(campaign = null, medium = "organic", farm = null),
            FarmLinks.parseReferrer("utm_source=google-play&utm_medium=organic"),
        )
        assertEquals(InstallReferrer(null, null, null), FarmLinks.parseReferrer(null))
        assertEquals(InstallReferrer(null, null, null), FarmLinks.parseReferrer("farm=%E0%A4%A&&="))
    }
}
