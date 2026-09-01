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
    @State private var showSurvey = false
    @State private var tripDetent: PresentationDetent = .fraction(0.92)
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
        // Survey entry point (button + sheet). Extracted into one modifier so the
        // already-large body stays within the type-checker's budget. Aviah's spec
        // delegates placement on a phone explicitly ("the button placement is
        // yours"); a small round button at bottom-trailing, above the panel, mirrors
        // the web's bottom-right corner without colliding with the map controls. The
        // server gate hides it after answering. (Auto-open timing is a deliberate
        // deferral — see PORT_NOTES.)
        .modifier(SurveyEntry(isPresented: $showSurvey))
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
                .presentationCornerRadius(28)
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
                .presentationCornerRadius(28)
        }
        .sheet(item: $accountRoute) { route in
            Group {
                switch route {
                case .saved:    SavedScreen { openFarm($0) }
                case .settings: SettingsSheet()
                }
            }
            .presentationDetents([.fraction(0.55), .fraction(0.92)])
            .presentationBackgroundInteraction(.enabled(upThrough: .fraction(0.55)))
            .presentationDragIndicator(.visible)
                .presentationCornerRadius(28)
        }
        .sheet(isPresented: $showTrips) {
            TripsView(onOpenFarm: { openFarm($0) }, selectedOsmId: selectedPin?.osmId, detent: $tripDetent)
                .presentationDetents([.fraction(0.5), .fraction(0.92)], selection: $tripDetent)
                .presentationBackgroundInteraction(.enabled(upThrough: .fraction(0.5)))
                .presentationDragIndicator(.visible)
                .presentationCornerRadius(28)
        }
        .sheet(isPresented: $showAuth) { AuthView() }
    }

    /// Farm cards open for everyone, signed out included (MOBILE-SPEC-MAP §0) —
    /// the card shows the free content and locks the paid fields inside. Actions
    /// that need an account (save, subscribe) prompt for one from within the card.
    private func openFarm(_ pin: FarmPin) {
        farmDetent = .fraction(0.55)   // always open at half
        flyTarget = pin                // fly the map to the farm, wherever it was opened from
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

/// The survey's floating entry button + its sheet, as one modifier so MainView's
/// body stays small enough to type-check quickly. The button is a small round
/// control at bottom-trailing (above the panel); the sheet presents the survey at
/// 0.92, matching the other secondary surfaces.
private struct SurveyEntry: ViewModifier {
    @Binding var isPresented: Bool

    func body(content: Content) -> some View {
        content
            .overlay(alignment: .bottomTrailing) {
                Button { Haptics.tap(); isPresented = true } label: {
                    Image(systemName: "text.bubble.fill")
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundStyle(Color.farmGreenMap)
                        .frame(width: 44, height: 44)
                        .background(Color.white.opacity(0.94), in: Circle())
                        .overlay(Circle().stroke(.white.opacity(0.6), lineWidth: 1))
                        .shadow(color: .black.opacity(0.22), radius: 10, y: 3)
                }
                .buttonStyle(.plain)
                .padding(.trailing, 14)
                // Raised from 66: at 66 the button's bottom edge met the bottom pill's
                // top (the pill spans ~safe-bottom+6 to +67), so it crowded the pill
                // ("too low" on device). 120 clears the pill by ~50pt. The overlay is
                // inset by the safe area (the pill at bottom 6 already clears the home
                // indicator), so this sits well above it; both the 44pt button and the
                // fixed-size-font pill ignore Dynamic Type, so the gap holds at large
                // text too.
                .padding(.bottom, 120)
            }
            .sheet(isPresented: $isPresented) {
                SurveyView()
                    .presentationDetents([.fraction(0.92)])
                    .presentationDragIndicator(.visible)
                    .presentationCornerRadius(28)
            }
    }
}
