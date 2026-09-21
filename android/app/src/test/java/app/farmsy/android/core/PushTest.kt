package app.farmsy.android.core

import org.junit.Assert.assertEquals
import org.junit.Test

/// The Profile notifications row shows one of three states — the Android
/// mirror of FarmsyTests/PushTests.swift. `PushRegistrar.notificationRowState`
/// is pure: no `ContextCompat`, no `Activity`, so it's a plain JVM test.
class PushTest {

    @Test
    fun `granted and enabled shows On`() {
        assertEquals(
            NotificationRowState.ON,
            PushRegistrar.notificationRowState(granted = true, enabled = true, canAsk = true),
        )
    }

    @Test
    fun `granted but the channel is muted still needs a trip to Settings`() {
        assertEquals(
            NotificationRowState.OPEN_SETTINGS,
            PushRegistrar.notificationRowState(granted = true, enabled = false, canAsk = true),
        )
    }

    @Test
    fun `not granted and the system can still ask shows Turn on`() {
        assertEquals(
            NotificationRowState.TURN_ON,
            PushRegistrar.notificationRowState(granted = false, enabled = false, canAsk = true),
        )
    }

    @Test
    fun `not granted and the system will no longer ask shows Open Settings`() {
        assertEquals(
            NotificationRowState.OPEN_SETTINGS,
            PushRegistrar.notificationRowState(granted = false, enabled = false, canAsk = false),
        )
    }
}
