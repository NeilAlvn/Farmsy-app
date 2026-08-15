import SwiftUI

/// The map is the app. There is no feed, list or discovery tab — everything is a
/// layer over the map (MOBILE-SPEC-MAP §0). The farm card is a bottom sheet at
/// three heights with the map usable behind it; Saved and Settings live behind an
/// account button in the header; posts and featured farms live in the What's New
/// sheet, opened from a floating button on the map.
struct MainView: View {
    @Environment(SessionStore.self) private var session

    /// The open farm. A bound value (not a `.sheet(item:)`) so tapping another pin
    /// swaps the card's contents in place rather than dismissing and re-presenting.
    @State private var selectedPin: FarmPin?
    @State private var showAuth = false
    @State private var accountRoute: AccountRoute?
    @State private var showWhatsNew = false
    @State private var showTrips = false
    /// The card opens at half and can be dragged to peek or full.
    @State private var farmDetent: PresentationDetent = .fraction(0.55)
    /// A pin the map should fly to (set when opening from the What's New sheet).
    @State private var flyTarget: FarmPin?

    enum AccountRoute: Identifiable {
        case saved, settings
        var id: Int { hashValue }
    }

    var body: some View {
        MapScreen(onOpenFarm: { openFarm($0) }, focusPin: flyTarget)
        .background(Color.cream.ignoresSafeArea())
        .ignoresSafeArea(.keyboard)
        .tint(.farmGreen)
        .environment(\.requestAuth, { showAuth = true })
        .overlay(alignment: .bottom) {
            bottomPanel
                .padding(.horizontal, 14)
                .padding(.bottom, 6)
        }
        // The farm card — three resting heights, opening at half, and the map
        // stays interactive behind it up through half.
        .sheet(isPresented: Binding(
            get: { selectedPin != nil },
            set: { if !$0 { selectedPin = nil } }
        )) {
            if let pin = selectedPin {
                FarmDetailView(pin: pin)
                    .id(pin.osmId)   // swap contents when another pin is tapped
                    .presentationDetents([.height(180), .fraction(0.55), .large], selection: $farmDetent)
                    .presentationBackgroundInteraction(.enabled(upThrough: .fraction(0.55)))
                    .presentationContentInteraction(.scrolls)
                    .presentationDragIndicator(.visible)
            }
        }
        .sheet(isPresented: $showWhatsNew) {
            WhatsNewSheet(onOpenFarm: { pin in
                flyTarget = pin          // fly the map straight to it
                showWhatsNew = false
                openFarm(pin)
            })
            .presentationDetents([.fraction(0.55), .fraction(0.92)])
            .presentationBackgroundInteraction(.enabled(upThrough: .fraction(0.55)))
            .presentationDragIndicator(.visible)
        }
        .sheet(item: $accountRoute) { route in
            switch route {
            case .saved:    SavedScreen { openFarm($0) }
            case .settings: SettingsSheet()
            }
        }
        .sheet(isPresented: $showTrips) { TripsPlaceholderView() }
        .sheet(isPresented: $showAuth) { AuthView() }
    }

    /// Farm cards open for everyone, signed out included (MOBILE-SPEC-MAP §0) —
    /// the card shows the free content and locks the paid fields inside. Actions
    /// that need an account (save, subscribe) prompt for one from within the card.
    private func openFarm(_ pin: FarmPin) {
        farmDetent = .fraction(0.55)   // always open at half
        selectedPin = pin
    }

    // MARK: - Bottom floating panel

    /// A floating pill over the map, the width of the search bar: Discover (the
    /// What's New sheet), Save, Trips and Settings.
    private var bottomPanel: some View {
        HStack(spacing: 4) {
            panelItem(icon: "newspaper", label: String(localized: "Discover")) { showWhatsNew = true }
            panelItem(icon: "heart", label: String(localized: "Saved")) { requireAuth { accountRoute = .saved } }
            panelItem(icon: "map", label: String(localized: "Trips")) { requireAuth { showTrips = true } }
            panelItem(icon: "gearshape", label: String(localized: "Settings")) { requireAuth { accountRoute = .settings } }
        }
        .padding(6)
        .background(.white, in: Capsule())
        .shadow(color: .black.opacity(0.14), radius: 12, y: 3)
    }

    private func panelItem(icon: String, label: String, action: @escaping () -> Void) -> some View {
        Button {
            Haptics.tap()
            action()
        } label: {
            VStack(spacing: 3) {
                Image(systemName: icon).font(.system(size: 17, weight: .semibold))
                Text(label).font(.geist(10, .semibold))
            }
            .foregroundStyle(Color.farmGreenMap)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 8)
        }
        .buttonStyle(.plain)
    }

    private func requireAuth(_ action: @escaping () -> Void) {
        if session.isAuthenticated { action() } else { showAuth = true }
    }
}

/// Placeholder until the Trips spec lands from Aviah — trip planning is a Pro
/// feature and the web owns the route logic (POST /api/route).
struct TripsPlaceholderView: View {
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        VStack(spacing: 14) {
            Image(systemName: "map")
                .font(.system(size: 34))
                .foregroundStyle(Color.farmGreenMap)
            Text("Trips are coming soon")
                .font(.geist(18, .bold))
                .foregroundStyle(Color.ink)
            Text("Plan a route between farms — a Farmsy Pro feature.")
                .font(.geist(14))
                .foregroundStyle(Color.inkMuted)
                .multilineTextAlignment(.center)
            Button("Close") { dismiss() }
                .font(.geist(15, .semibold))
                .foregroundStyle(Color.farmGreen)
                .padding(.top, 4)
        }
        .padding(30)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.cream.ignoresSafeArea())
    }
}
