package app.farmsy.android.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/// Mirror of FarmsyTests/BasketSeedTests.swift.
class BasketSeedTest {
    @Test fun addsOnlyMissingInPickOrder() {
        assertEquals(listOf("eggs", "milk"), BasketSeed.toAdd(listOf("eggs", "cheese", "milk"), listOf("cheese")))
        assertTrue(BasketSeed.toAdd(emptyList(), listOf("cheese")).isEmpty())
        assertEquals(listOf("eggs"), BasketSeed.toAdd(listOf("eggs", "eggs"), emptyList()))
    }

    @Test fun fallbackIsTwelveRealIds() {
        assertEquals(
            listOf("eggs", "cheese", "milk", "potatoes", "vegetables", "fruits", "meat", "honey", "bread", "strawberry", "apples", "butter"),
            BasketSeed.fallback.map { it.id },
        )
        BasketSeed.fallback.forEach { assertTrue(it.nl.isNotEmpty() && it.en.isNotEmpty()) }
    }
}
