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
        MapScreen(onOpenFarm: { openFarm($0, source: .mapPin) }, focusPin: flyTarget)
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
                openFarm(pin, source: .whatsNew)
            })
            .presentationDetents([.fraction(0.55), .fraction(0.92)])
            .presentationBackgroundInteraction(.enabled(upThrough: .fraction(0.55)))
            .presentationDragIndicator(.visible)
                .presentationCornerRadius(28)
        }
        .sheet(item: $accountRoute) { route in
            Group {
                switch route {
                case .saved:    SavedScreen { openFarm($0, source: .saved) }
                case .settings: SettingsSheet()
                }
            }
            .presentationDetents([.fraction(0.55), .fraction(0.92)])
            .presentationBackgroundInteraction(.enabled(upThrough: .fraction(0.55)))
            .presentationDragIndicator(.visible)
                .presentationCornerRadius(28)
        }
        .sheet(isPresented: $showTrips) {
            TripsView(onOpenFarm: { openFarm($0, source: .trips) }, selectedOsmId: selectedPin?.osmId, detent: $tripDetent)
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
    private func openFarm(_ pin: FarmPin, source: AnalyticsValue.Source) {
        farmDetent = .fraction(0.55)   // always open at half
        flyTarget = pin                // fly the map to the farm, wherever it was opened from
        selectedPin = pin
        // Fired here, where the pin is set, not in a view body that runs more than
        // once. `source` is what tells whether the map or the feed sells.
        Observability.capture(.farmOpened, [AnalyticsProp.osmId: pin.osmId,
                                            AnalyticsProp.source: source.rawValue])
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

/// The survey's floating entry button + arrow + sheet, as one modifier so MainView's
/// body stays small. The button is shown for EVERY role — signed-out, signed-in and
/// admin alike (Neil: "the survey appears whatever the role"). It changes what it
/// opens: the seven questions while unanswered, the feedback box once answered. A down
/// arrow bounces above it while unanswered. It also auto-opens once on cold launch
/// after the map settles — once ever per signed-in account, once a day for signed-out.
/// (Admin data integrity is kept server-side: `POST /api/survey/respond` rejects an
/// admin submit with `is_admin`, so showing the UI to staff pollutes nothing.)
private struct SurveyEntry: ViewModifier {
    @Binding var isPresented: Bool
    @Environment(SessionStore.self) private var session

    /// The gate result (nil until loaded). Drives only `answered` now: answered → the
    /// button opens feedback and the arrow is gone; not-answered → questions + arrow.
    /// Role no longer hides anything on the client.
    @State private var gate: SurveyGate?
    @State private var mode: SurveyView.Mode = .questions
    /// Cold-launch auto-open is attempted exactly once (this modifier appears once at
    /// app start; resume does not recreate it, so a plain `.task` never re-fires).
    @State private var didAutoOpen = false

    /// The arrow points only while there is an unanswered survey to point at — role
    /// no longer matters, only `answered`.
    private var showArrow: Bool {
        guard let g = gate else { return false }
        return !g.answered
    }

    func body(content: Content) -> some View {
        content
            .overlay(alignment: .bottomTrailing) {
                ZStack(alignment: .bottom) {
                    if showArrow {
                        SurveyArrow()
                            .offset(y: -52)   // sit above the 44pt button
                            .accessibilityHidden(true)
                    }
                    Button {
                        Haptics.tap()
                        mode = (gate?.answered == true) ? .feedback : .questions
                        isPresented = true
                    } label: {
                        Image(systemName: "text.bubble.fill")
                            .font(.system(size: 17, weight: .semibold))
                            .foregroundStyle(Color.farmGreenMap)
                            .frame(width: 44, height: 44)
                            .background(Color.white.opacity(0.94), in: Circle())
                            .overlay(Circle().stroke(.white.opacity(0.6), lineWidth: 1))
                            .shadow(color: .black.opacity(0.22), radius: 10, y: 3)
                    }
                    .buttonStyle(.plain)
                }
                .padding(.trailing, 14)
                // 120 clears the bottom pill (spans ~safe-bottom+6 to +67) by ~50pt.
                // The overlay is inset by the safe area, so it clears the home
                // indicator; button + pill ignore Dynamic Type, so the gap holds.
                .padding(.bottom, 120)
            }
            .sheet(isPresented: $isPresented) {
                SurveyView(mode: mode)
                    .presentationDetents([.fraction(0.92)])
                    .presentationDragIndicator(.visible)
                    .presentationCornerRadius(28)
                    // Re-check once the sheet closes (they may have just answered), so
                    // the arrow disappears and the button switches to feedback mode.
                    .onDisappear { Task { gate = await SurveyAPI.gate(accessToken: session.session?.accessToken) } }
            }
            // Refresh the gate on sign-in / sign-out (token change) for the arrow + mode.
            .task(id: session.session?.accessToken) {
                gate = await SurveyAPI.gate(accessToken: session.session?.accessToken)
            }
            // Cold-launch auto-open — runs once when the map first appears (not on
            // resume). The 1.4s delay lets the map settle (and the session bootstrap)
            // before asking, so it reads as a question rather than part of the loading.
            // Fires for any role; only `answered` and the frequency cap stop it.
            .task {
                guard !didAutoOpen else { return }
                didAutoOpen = true
                try? await Task.sleep(for: .seconds(1.4))
                let g = await SurveyAPI.gate(accessToken: session.session?.accessToken)
                gate = g
                guard !g.answered else { return }
                let account = session.isAuthenticated ? session.email : nil
                guard SurveyAutoOpen.canAutoOpen(account: account) else { return }
                SurveyAutoOpen.recordAutoOpen(account: account)
                mode = .questions
                isPresented = true
            }
    }
}

/// Whether the survey may auto-open, and the record that it did. Signed in it opens
/// once ever per account; signed out, once per calendar day (a phone cold-launches
/// often, so an uncapped "every visit" would harass). Persisted in UserDefaults —
/// per-device is acceptable for signed-out (there is no identity to key on), and the
/// signed-in per-account key survives across devices only as far as the account's
/// server-side `answered` state does, which is the real stop.
enum SurveyAutoOpen {
    private static let store = UserDefaults.standard

    static func canAutoOpen(account: String?) -> Bool {
        if let a = account, !a.isEmpty {
            return !store.bool(forKey: "survey_autoopen_acct_\(a)")
        }
        return store.string(forKey: "survey_autoopen_day") != today()
    }

    static func recordAutoOpen(account: String?) {
        if let a = account, !a.isEmpty {
            store.set(true, forKey: "survey_autoopen_acct_\(a)")
        } else {
            store.set(today(), forKey: "survey_autoopen_day")
        }
    }

    private static func today() -> String {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        f.locale = Locale(identifier: "en_US_POSIX")
        return f.string(from: Date())
    }
}

/// A regular dark-green down arrow above the survey button, pointing at it — shown
/// only while the survey is unanswered. Per Neil's brief (a standard arrow, not a
/// hand-drawn Path): SF `arrow.down`, `farmGreen` (dark brand green, distinct from the
/// lighter on-map `farmGreenMap`). Bounces ~9px / ~0.9s eased; hidden from VoiceOver;
/// holds still under Reduce Motion. Matches the Android `SurveyArrow`.
private struct SurveyArrow: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var drop = false

    var body: some View {
        Image(systemName: "arrow.down")
            .font(.system(size: 22, weight: .semibold))
            .foregroundStyle(Color.farmGreen)
            .offset(y: drop ? 9 : 0)   // ~9px fall-and-settle
            .animation(reduceMotion ? nil
                       : .easeInOut(duration: 0.9).repeatForever(autoreverses: true), value: drop)
            .onAppear { if !reduceMotion { drop = true } }
    }
}
