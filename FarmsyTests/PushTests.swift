import Testing
import UserNotifications
@testable import Farmsy

/// The Profile notifications row shows one of three states. This is the pure
/// mapping from the system's answer to that state — no permission dialog, no
/// UIApplication, so it is a plain synchronous test.
struct NotificationRowStateTests {

    @Test("not yet asked shows Turn on")
    func notDeterminedShowsTurnOn() {
        #expect(PushRegistrar.notificationRowState(.notDetermined) == .turnOn)
    }

    @Test("denied shows Open Settings")
    func deniedShowsOpenSettings() {
        #expect(PushRegistrar.notificationRowState(.denied) == .openSettings)
    }

    @Test("authorized, provisional and ephemeral all show On")
    func grantedShowsOn() {
        #expect(PushRegistrar.notificationRowState(.authorized) == .on)
        #expect(PushRegistrar.notificationRowState(.provisional) == .on)
        #expect(PushRegistrar.notificationRowState(.ephemeral) == .on)
    }
}
