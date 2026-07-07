import SwiftUI

/// Splash → onboarding (first run) → main app.
/// Browsing is open to everyone; logging in is asked for lazily (via the
/// `requestAuth` environment hook) when a gated action is tapped.
struct RootView: View {
    @Environment(SessionStore.self) private var session
    @Environment(FarmsStore.self) private var farms
    @Environment(FavoritesStore.self) private var favorites

    @AppStorage("didFinishOnboarding") private var didFinishOnboarding = false
    @State private var splashDone = false

    var body: some View {
        ZStack {
            Color.cream.ignoresSafeArea()

            if !splashDone || !session.isBootstrapped {
                SplashView { splashDone = true }
                    .transition(.opacity)
            } else if session.isAuthenticated || didFinishOnboarding {
                MainView()
                    .transition(.opacity)
            } else {
                OnboardingView { didFinishOnboarding = true }
                    .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.45), value: splashDone)
        .animation(.easeInOut(duration: 0.45), value: session.isAuthenticated)
        .task {
            await session.bootstrap()
        }
        .task {
            await farms.loadIfNeeded()
        }
        .onChange(of: session.session?.user.id, initial: true) { _, userId in
            if let userId {
                Task { await favorites.load(userId: userId) }
            } else {
                favorites.clear()
            }
        }
    }
}
