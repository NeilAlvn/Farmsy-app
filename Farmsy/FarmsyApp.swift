//
//  FarmsyApp.swift
//  Farmsy
//
//  Created by Neil Alvin Medallon on 6/9/26.
//

import SwiftUI

@main
struct FarmsyApp: App {
    @Environment(\.scenePhase) private var scenePhase
    @State private var session: SessionStore
    @State private var farms = FarmsStore()
    @State private var favorites = FavoritesStore()
    @State private var locationManager = LocationManager()
    @State private var purchases = PurchaseStore()

    init() {
        #if DEBUG
        if CommandLine.arguments.contains("--reset-onboarding") {
            UserDefaults.standard.removeObject(forKey: "didFinishOnboarding")
            UserDefaults.standard.removeObject(forKey: "pendingRefCode")
        }
        #endif
        Observability.start()
        PurchaseStore.configure()
        // Warm the store prices now so the paywall has them in hand when a
        // locked farm is opened, instead of paying the round-trip on screen.

        let store = SessionStore()
        #if DEBUG
        store.isDemoSession = CommandLine.arguments.contains("--demo-session")
        #endif
        _session = State(initialValue: store)
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(session)
                .environment(farms)
                .environment(favorites)
                .environment(locationManager)
                .environment(purchases)
                .tint(.farmGreen)
                .preferredColorScheme(.light)
                .onOpenURL { captureReferral($0) }
                .onContinueUserActivity(NSUserActivityTypeBrowsingWeb) { activity in
                    activity.webpageURL.map(captureReferral)
                }
                // Re-check the subscription whenever the app returns to the
                // foreground. Buying, cancelling and managing a plan all happen
                // outside the app — in the App Store — and the profile only changes
                // once the webhook lands a few seconds later. Refreshing only on
                // login and screen-open meant coming back from the store showed the
                // state from before you left. onChange(scenePhase → .active) is the
                // one hook that catches every one of those round-trips.
                .onChange(of: scenePhase) { _, phase in
                    if phase == .active {
                        Task { await session.refreshProfile() }
                    }
                }
        }
    }

    /// Pull `?ref=CODE` out of a farmsy.app/join universal link and hold it until
    /// signup, mirroring the web's 7-day `farmsy_ref` cookie. Without this a user
    /// who taps a referral link and installs the app arrives with no code at all,
    /// and the referrer is never credited — it fails silently, which is the worst
    /// way for it to fail.
    private func captureReferral(_ url: URL) {
        guard url.path.hasPrefix("/join"),
              let code = URLComponents(url: url, resolvingAgainstBaseURL: false)?
                  .queryItems?.first(where: { $0.name == "ref" })?.value?
                  .trimmingCharacters(in: .whitespaces).uppercased(),
              // Same shape the web validates before setting its cookie.
              code.range(of: "^[A-Z0-9]{6,12}$", options: .regularExpression) != nil
        else { return }

        UserDefaults.standard.set(code, forKey: "pendingRefCode")
        UserDefaults.standard.set(Date.now, forKey: "pendingRefCodeAt")
    }
}
