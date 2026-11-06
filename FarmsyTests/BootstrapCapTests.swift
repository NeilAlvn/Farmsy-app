import Testing
@testable import Farmsy

/// The splash is held until `SessionStore.isBootstrapped`. Restoring a signed-in
/// session is two network calls with no deadline of their own, so the cap is
/// the only thing that keeps a slow connection from pinning the splash.
@MainActor
struct BootstrapCapTests {

    @Test("the cap releases the splash without the session restore finishing")
    func capReleasesSplash() async throws {
        let store = SessionStore()
        // No restore runs here at all — only the cap can flip the flag.
        store.releaseSplash(after: .milliseconds(50))
        #expect(store.isBootstrapped == false)

        try await Task.sleep(for: .milliseconds(400))
        #expect(store.isBootstrapped)
    }
}
